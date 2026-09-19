#!/usr/bin/env bash
# DevPilot deploy (서버, deploy 사용자): web.zip 다운로드 -> /opt/devpilot/web 교체 -> GHCR pull -> compose up -d
#                                       -> health 60초 대기 -> 실패 시 이전 tag 자동 롤백 (결정 D)
# 기준: docs/10-deployment-and-operations.md §9, §13.1 (deploy-and-rollback) (2026-09-18)
#
# 사용 (로컬 PC에서: ssh <user>@<server> → sudo -u deploy /opt/devpilot/deploy.sh v0.x.y):
#   deploy.sh <tag> [--backup-first] [--no-pull]
#
#   <tag>           v0.<sprint>.<patch> (예: v0.2.3). tag는 불변이다. release.yml이 만든
#                   GitHub Release 자산 web.zip 과 GHCR 이미지 ghcr.io/<owner>/devpilot-api:<tag> 가 있어야 한다
#   --backup-first  배포 전에 backup.sh를 실행하고, 실패하면 아무것도 바꾸지 않고 중단한다
#                   (migration이 포함된 릴리스에 권장. 자동 감지는 없다 — 서버에 git 저장소가 없다)
#   --no-pull       이미지를 pull하지 않는다 (이미 받은 tag 재활성화 = 수동 롤백)
#
# 입력
#   /opt/devpilot/ops.env        GITHUB_OWNER (web.zip URL과 이미지 이름). 값은 저장소 밖에만 둔다
#   /opt/devpilot/api.env        compose env_file. 존재와 권한(600 deploy)만 확인한다
#   /opt/devpilot/compose.prod.yml, Caddyfile   prepare-server.sh가 복사 (갱신도 prepare-server.sh 재실행)
# 기록
#   /opt/devpilot/.env           compose 치환 변수 TAG, API_IMAGE (compose가 프로젝트 디렉터리에서 자동으로 읽는다)
#   /opt/devpilot/.last_good_tag health를 통과한 마지막 tag (롤백 대상)
#   /opt/devpilot/state/web-<tag>.zip   최근 두 tag의 web.zip 사본 (네트워크 없이 롤백)
#
# 결과 출력: 마지막 줄에 DEPLOY_RESULT=<success|rolled_back|rollback_failed|failed_no_previous>
# 종료 코드: 0 성공 / 1 사전 단계 실패(변경 없음) / 2 사용법 오류 / 10 실패 후 롤백 성공
#            11 롤백 실패(서버가 중간 상태 — 수동 복구가 필요하다)
#            12 배포 실패, 되돌릴 이전 tag가 없어 롤백을 시도하지 않았다
set -euo pipefail

readonly DEVPILOT_HOME="${DEVPILOT_HOME:-/opt/devpilot}"
readonly HEALTH_URL="http://127.0.0.1:18080/actuator/health"
readonly HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-60}"
readonly HEALTH_INTERVAL_SECONDS=3
readonly GITHUB_REPO="DevPilot"
readonly COMPOSE_FILE="${DEVPILOT_HOME}/compose.prod.yml"
readonly COMPOSE_ENV_FILE="${DEVPILOT_HOME}/.env"
readonly STATE_DIR="${DEVPILOT_HOME}/state"
readonly WEB_DIR="${DEVPILOT_HOME}/web"
readonly LAST_GOOD_TAG_FILE="${DEVPILOT_HOME}/.last_good_tag"
readonly TAG_PATTERN='^v0\.[0-9]+\.[0-9]+$'

TAG=""
BACKUP_FIRST="false"
NO_PULL="false"
GITHUB_OWNER=""
API_IMAGE=""

log() { printf '%s [deploy] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() {
  log "ERROR: $*"
  exit 1
}

usage() {
  printf 'usage: %s <tag> [--backup-first] [--no-pull]\n' "$(basename "$0")" >&2
  exit 2
}

parse_args() {
  [[ $# -ge 1 ]] || usage
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --backup-first)
        BACKUP_FIRST="true"
        ;;
      --no-pull)
        NO_PULL="true"
        ;;
      -h | --help)
        usage
        ;;
      -*)
        usage
        ;;
      *)
        [[ -z "${TAG}" ]] || usage
        TAG="$1"
        ;;
    esac
    shift
  done
  [[ -n "${TAG}" ]] || usage
  [[ "${TAG}" =~ ${TAG_PATTERN} ]] || {
    log "tag 형식 오류: ${TAG} (v0.<sprint>.<patch>)"
    usage
  }
}

# KEY=VALUE 파일에서 값 하나를 읽는다 (source 하지 않는다)
read_env_value() {
  local key="$1" file="$2"
  grep -E "^${key}=" "${file}" | tail -n 1 | cut -d '=' -f 2- || true
}

compose_cmd() {
  docker compose -f "${COMPOSE_FILE}" "$@"
}

acquire_lock() {
  exec 9>"${STATE_DIR}/deploy.lock"
  flock -n 9 || die "다른 배포가 진행 중이다"
}

# web.zip: state/web-<tag>.zip 사본이 있으면 그것을, 없으면 GitHub Release에서 받는다
fetch_web_zip() {
  local tag="$1" zip="${STATE_DIR}/web-${tag}.zip" url
  if [[ -s "${zip}" ]]; then
    log "[${tag}] web.zip 사본 사용: ${zip}"
    return 0
  fi
  url="https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}/releases/download/${tag}/web.zip"
  log "[${tag}] web.zip 다운로드: ${url}"
  curl -fsSL --retry 3 --max-time 180 -o "${zip}.part" "${url}" || {
    rm -f "${zip}.part"
    log "[${tag}] web.zip 다운로드 실패 (release.yml 성공 여부, GITHUB_OWNER 확인)"
    return 1
  }
  mv -f "${zip}.part" "${zip}" || return 1
}

# /opt/devpilot/web 의 내용을 교체한다. 디렉터리 자체(inode)는 유지한다 — caddy가 bind mount로 보고 있어
# 디렉터리를 rename하면 컨테이너에 반영되지 않는다. 잠깐 파일이 없는 순간이 있지만 사용자 3명 이하라 허용한다.
install_web() {
  local tag="$1" zip="${STATE_DIR}/web-${tag}.zip" tmp
  fetch_web_zip "${tag}" || return 1
  tmp="$(mktemp -d "${STATE_DIR}/web.XXXXXX")" || return 1
  if ! unzip -q "${zip}" -d "${tmp}" || [[ ! -f "${tmp}/index.html" ]]; then
    rm -rf -- "${tmp}"
    rm -f "${zip}"
    log "[${tag}] web.zip 이 비정상이다 (index.html 없음). 사본을 지웠다"
    return 1
  fi
  # 이 함수는 activate()를 거쳐 if 조건에서 호출되므로 본문 전체에서 errexit가 꺼져 있다.
  # 실패를 놓치면 배포가 성공으로 보고되므로 단계마다 명시적으로 반환한다.
  chmod -R u+rwX,go+rX,go-w "${tmp}" || {
    rm -rf -- "${tmp}"
    log "[${tag}] 압축 해제한 web 파일의 권한을 바꾸지 못했다"
    return 1
  }
  find "${WEB_DIR}" -mindepth 1 -delete || {
    rm -rf -- "${tmp}"
    log "[${tag}] ${WEB_DIR} 를 비우지 못했다 (기존 파일 유지)"
    return 1
  }
  cp -a "${tmp}/." "${WEB_DIR}/" || {
    rm -rf -- "${tmp}"
    log "[${tag}] web 정적 파일 복사 실패. ${WEB_DIR} 가 비어 있다 — 같은 tag로 다시 실행한다"
    return 1
  }
  rm -rf -- "${tmp}"
  log "[${tag}] web 정적 파일 교체 완료"
}

# install_web 과 같은 이유로(errexit 꺼짐) 단계마다 명시적으로 반환한다
write_compose_env() {
  local tag="$1"
  printf 'TAG=%s\nAPI_IMAGE=%s\n' "${tag}" "${API_IMAGE}" >"${COMPOSE_ENV_FILE}.tmp" || return 1
  mv -f "${COMPOSE_ENV_FILE}.tmp" "${COMPOSE_ENV_FILE}" || return 1
}

# 호스트에서 caddy(127.0.0.1:18080) → api:8080 경로로 확인한다 (tailscale serve는 거치지 않는다)
wait_api_healthy() {
  local tag="$1" body deadline
  deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  while ((SECONDS < deadline)); do
    body="$(curl -fsS --max-time 5 "${HEALTH_URL}" 2>/dev/null || true)"
    if [[ "${body}" == *'"status":"UP"'* ]]; then
      log "[${tag}] health UP"
      return 0
    fi
    sleep "${HEALTH_INTERVAL_SECONDS}"
  done
  log "[${tag}] ${HEALTH_TIMEOUT_SECONDS}초 안에 health UP을 받지 못했다"
  return 1
}

# 함수가 if 조건에서 호출되면 errexit가 꺼지므로 모든 단계에서 실패를 명시적으로 반환한다
activate() {
  local tag="$1" pull="$2"

  if [[ "${pull}" == "true" ]]; then
    log "[${tag}] image pull ${API_IMAGE}:${tag}"
    docker pull --quiet "${API_IMAGE}:${tag}" >/dev/null || return 1
  elif ! docker image inspect "${API_IMAGE}:${tag}" >/dev/null 2>&1; then
    log "[${tag}] 이미지가 없어 pull 한다 (--no-pull 무시)"
    docker pull --quiet "${API_IMAGE}:${tag}" >/dev/null || return 1
  fi

  install_web "${tag}" || return 1
  write_compose_env "${tag}" || return 1

  log "[${tag}] compose up -d"
  compose_cmd up -d --remove-orphans || return 1

  wait_api_healthy "${tag}" || return 1
  return 0
}

mark_good() {
  local tag="$1"
  printf '%s\n' "${tag}" >"${LAST_GOOD_TAG_FILE}.tmp"
  mv -f "${LAST_GOOD_TAG_FILE}.tmp" "${LAST_GOOD_TAG_FILE}"
}

# 이 저장소의 이미지·web.zip 사본만 정리한다 (공용 Docker: docker image prune 은 쓰지 않는다)
prune_old() {
  local keep_a="$1" keep_b="$2" image_tag zip name
  while IFS= read -r image_tag; do
    [[ -n "${image_tag}" && "${image_tag}" != "<none>" ]] || continue
    [[ "${image_tag}" == "${keep_a}" || "${image_tag}" == "${keep_b}" ]] && continue
    docker image rm "${API_IMAGE}:${image_tag}" >/dev/null 2>&1 || true
  done < <(docker image ls "${API_IMAGE}" --format '{{.Tag}}')
  for zip in "${STATE_DIR}"/web-v*.zip; do
    [[ -f "${zip}" ]] || continue
    name="$(basename "${zip}" .zip)"
    name="${name#web-}"
    [[ "${name}" == "${keep_a}" || "${name}" == "${keep_b}" ]] && continue
    rm -f "${zip}"
  done
}

main() {
  parse_args "$@"
  [[ "$(id -un)" == "deploy" ]] || die "deploy 사용자로 실행한다 (sudo -u deploy $0 ${TAG})"
  [[ -f "${COMPOSE_FILE}" && -f "${DEVPILOT_HOME}/Caddyfile" ]] \
    || die "${COMPOSE_FILE}, Caddyfile 이 필요하다 (prepare-server.sh)"
  [[ -f "${DEVPILOT_HOME}/ops.env" && -f "${DEVPILOT_HOME}/api.env" ]] \
    || die "${DEVPILOT_HOME}/ops.env, api.env 가 필요하다"
  local api_env_mode
  api_env_mode="$(stat -c '%a %U' "${DEVPILOT_HOME}/api.env")"
  [[ "${api_env_mode}" == "600 deploy" ]] \
    || die "api.env 권한이 '600 deploy'가 아니다 (${api_env_mode}). docs/07 §7.3"
  GITHUB_OWNER="$(read_env_value GITHUB_OWNER "${DEVPILOT_HOME}/ops.env")"
  [[ "${GITHUB_OWNER}" =~ ^[A-Za-z0-9-]+$ ]] || die "ops.env 의 GITHUB_OWNER 가 비어 있거나 형식이 틀렸다"
  API_IMAGE="ghcr.io/${GITHUB_OWNER,,}/devpilot-api"
  mkdir -p "${STATE_DIR}"
  acquire_lock

  local previous_tag=""
  if [[ -f "${LAST_GOOD_TAG_FILE}" ]]; then
    previous_tag="$(tr -d '[:space:]' <"${LAST_GOOD_TAG_FILE}")"
  fi
  log "deploy ${TAG} (previous=${previous_tag:-none}, backup_first=${BACKUP_FIRST}, no_pull=${NO_PULL})"

  if [[ "${BACKUP_FIRST}" == "true" ]]; then
    log "배포 전 백업 실행"
    "${DEVPILOT_HOME}/backup.sh" || die "배포 전 백업 실패. 배포를 중단한다 (변경 없음)"
  fi

  local pull="true"
  [[ "${NO_PULL}" == "true" ]] && pull="false"

  if activate "${TAG}" "${pull}"; then
    mark_good "${TAG}"
    prune_old "${TAG}" "${previous_tag}"
    log "배포 완료 ${TAG}. 로컬 PC(tailnet)에서 smoke-headers.sh https://<tailnet-host> 로 헤더를 확인한다"
    printf 'DEPLOY_RESULT=success\n'
    exit 0
  fi

  log "배포 실패: ${TAG}. api 로그 마지막 200줄:"
  compose_cmd logs --no-color --tail 200 api || true

  if [[ -z "${previous_tag}" || "${previous_tag}" == "${TAG}" ]]; then
    log "롤백 대상 tag가 없다 (롤백을 시도하지 않았다)"
    printf 'DEPLOY_RESULT=failed_no_previous\n'
    exit 12
  fi

  log "자동 롤백: ${previous_tag} (DB migration은 되돌리지 않는다)"
  if activate "${previous_tag}" "false"; then
    printf 'DEPLOY_RESULT=rolled_back\n'
    exit 10
  fi

  log "롤백 실패. docs/10-deployment-and-operations.md §13.1 수동 절차를 따른다"
  printf 'DEPLOY_RESULT=rollback_failed\n'
  exit 11
}

main "$@"
