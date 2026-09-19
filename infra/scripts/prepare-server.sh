#!/usr/bin/env bash
# DevPilot server preparation — 기존 자체 서버 (Rocky Linux 9 x86_64, 공용 Docker·PostgreSQL). root, 1회, 멱등
# 기준: docs/10-deployment-and-operations.md §2.1, §2.2, §5.1 (2026-09-18)
#
# 사용 (서버에 root로, 저장소를 임시로 clone한 상태에서):
#   git clone --depth 1 https://github.com/<owner>/DevPilot.git /tmp/devpilot
#   bash /tmp/devpilot/infra/scripts/prepare-server.sh [--operator <user>]
#   rm -rf /tmp/devpilot
#
#   --operator <user>   운영자(SSH 로그인) 계정을 deploy 그룹에 추가한다
#                       (pull-backup.ps1이 /opt/devpilot/backups 를 scp로 읽기 위해)
#
# 하는 일
#   1) 전제 조건 확인: x86_64, docker info, tailscale Running, 127.0.0.1:18080 비어 있음(또는 DevPilot caddy), 가용 메모리 ≥ 2GB
#      하나라도 실패하면 아무것도 만들지 않고 종료한다
#   2) deploy 사용자 (docker 그룹, 비밀번호 잠금, 로그인 셸 bash)
#   3) /opt/devpilot/{backups,web,state} + compose.prod.yml·Caddyfile·deploy.sh·backup.sh·healthping.sh·host-check.sh 복사
#      api.env·ops.env 템플릿 (0600, 이미 있으면 덮어쓰지 않음)
#   4) systemd devpilot-backup / devpilot-healthping service·timer 설치, daemon-reload, timer enable
#   5) 남은 수동 작업 안내 (tailscale serve, env 값, 첫 배포)
#
# 건드리지 않는 것 (공용 서버, docs/10 §2.1): /etc/docker/daemon.json, podman, sshd_config, firewalld, SELinux,
#   dnf-automatic, 패키지 설치, tailscale up, 다른 DB·컨테이너, 호스트 포트 80/443/8080
# 멱등성: 여러 번 실행해도 결과가 같다. 재실행하면 compose/Caddyfile/스크립트/유닛을 최신으로 갱신하고 env 파일은 유지한다.
set -euo pipefail

readonly DEVPILOT_HOME="/opt/devpilot"
readonly DEPLOY_USER="deploy"
readonly CADDY_PORT="18080"
readonly MIN_AVAILABLE_MB=2048

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly INFRA_DIR

OPERATOR_USER=""

log() { printf '[prepare-server] %s\n' "$*"; }
die() {
  printf '[prepare-server] ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  sed -n '2,25p' "${BASH_SOURCE[0]}"
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --operator)
        [[ $# -ge 2 ]] || die "--operator 에 사용자 이름이 필요하다"
        OPERATOR_USER="$2"
        shift 2
        ;;
      -h | --help)
        usage
        exit 0
        ;;
      *)
        die "알 수 없는 인자: $1"
        ;;
    esac
  done
}

# 새 내용(임시 파일)이 대상과 다를 때만 설치한다. 바뀌었으면 0, 같으면 1을 반환한다.
install_if_changed() {
  local src="$1" dest="$2" mode="$3" owner="${4:-root:root}"
  if [[ -f "${dest}" ]] && cmp -s "${src}" "${dest}"; then
    rm -f "${src}"
    return 1
  fi
  install -m "${mode}" -o "${owner%%:*}" -g "${owner##*:}" "${src}" "${dest}"
  rm -f "${src}"
  return 0
}

check_prerequisites() {
  [[ ${EUID} -eq 0 ]] || die "root 권한으로 실행한다 (su - 또는 sudo bash $0)"

  local arch
  arch="$(uname -m)"
  # release.yml이 linux/amd64 + linux/arm64 멀티아치 이미지를 만든다 (docs/10 §1.4, §7.2).
  # x86_64 = 현재 자체 서버, aarch64 = 공개 전환 후보인 OCI Always Free Ampere A1
  [[ "${arch}" == "x86_64" || "${arch}" == "aarch64" ]] \
    || die "지원하지 않는 아키텍처다 (uname -m = ${arch}). 이미지는 linux/amd64 와 linux/arm64 만 만든다"

  command -v docker >/dev/null || die "docker 명령이 없다"
  docker info >/dev/null 2>&1 || die "docker daemon에 접근할 수 없다 (docker info 실패)"
  docker compose version >/dev/null 2>&1 || die "docker compose plugin이 없다"

  command -v tailscale >/dev/null || die "tailscale 명령이 없다"
  local ts_state
  ts_state="$(tailscale status --json 2>/dev/null | sed -n 's/.*"BackendState": *"\([^"]*\)".*/\1/p' | head -n 1)"
  [[ "${ts_state}" == "Running" ]] || die "tailscale이 Running 상태가 아니다 (${ts_state:-unknown}). tailscale up은 이 스크립트가 하지 않는다"

  # 18080은 DevPilot caddy만 쓴다. 다른 프로세스가 듣고 있으면 중단한다 (재실행 시 우리 컨테이너면 통과)
  if ss -Hltn "sport = :${CADDY_PORT}" 2>/dev/null | grep -q .; then
    if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'devpilot-caddy-1'; then
      die "127.0.0.1:${CADDY_PORT} 를 다른 프로세스가 사용 중이다 (ss -ltnp | grep ${CADDY_PORT})"
    fi
  fi

  local available_mb
  available_mb="$(free -m | awk '/^Mem:/ {print $7}')"
  [[ -n "${available_mb}" ]] || die "free -m 결과를 읽지 못했다"
  ((available_mb >= MIN_AVAILABLE_MB)) \
    || die "가용 메모리 ${available_mb}MB < ${MIN_AVAILABLE_MB}MB. api 1.5g + caddy 128m 을 둘 수 없다"

  for cmd in curl unzip flock; do
    command -v "${cmd}" >/dev/null || die "${cmd} 가 없다 (deploy.sh가 사용). 서버 소유자 방식으로 설치한다"
  done

  [[ -f "${INFRA_DIR}/compose.prod.yml" && -f "${INFRA_DIR}/Caddyfile" && -d "${INFRA_DIR}/systemd" ]] \
    || die "infra 디렉터리를 찾지 못했다: ${INFRA_DIR}"

  if [[ -n "${OPERATOR_USER}" ]]; then
    id -u "${OPERATOR_USER}" >/dev/null 2>&1 || die "--operator 사용자가 없다: ${OPERATOR_USER}"
  fi
  log "전제 조건 확인 완료 (x86_64, docker, tailscale Running, :${CADDY_PORT} 사용 가능, 가용 ${available_mb}MB)"
}

create_deploy_user() {
  getent group docker >/dev/null || die "docker 그룹이 없다. 공용 Docker의 소켓 접근 방식을 서버 소유자와 확인한다"
  if ! id -u "${DEPLOY_USER}" >/dev/null 2>&1; then
    log "사용자 생성: ${DEPLOY_USER}"
    useradd --create-home --shell /bin/bash "${DEPLOY_USER}"
  fi
  passwd -l "${DEPLOY_USER}" >/dev/null
  # docker 그룹은 사실상 root 권한이다. deploy 사용자는 tailnet SSH 뒤의 운영자만 sudo -u deploy 로 쓴다
  usermod -aG docker "${DEPLOY_USER}"
  if [[ -n "${OPERATOR_USER}" ]]; then
    usermod -aG "${DEPLOY_USER}" "${OPERATOR_USER}"
    log "운영자 ${OPERATOR_USER} 를 ${DEPLOY_USER} 그룹에 추가 (backups 읽기). 재로그인 후 적용"
  fi
}

create_dirs() {
  # /opt/devpilot 자체는 750 (docs/07 §7.3). bind mount는 마운트 지점부터 권한을 검사하므로
  # 컨테이너가 읽는 web/ 와 Caddyfile 만 other 읽기를 준다 (caddy 컨테이너는 CAP_DAC_OVERRIDE 없음)
  install -d -m 750 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${DEVPILOT_HOME}"
  install -d -m 755 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${DEVPILOT_HOME}/web"
  install -d -m 750 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${DEVPILOT_HOME}/state"
  # backups: setgid → 파일이 deploy 그룹 소유가 되어 --operator 사용자가 읽을 수 있다 (backup.sh는 umask 027)
  install -d -m 2750 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${DEVPILOT_HOME}/backups"
  if [[ ! -f "${DEVPILOT_HOME}/web/index.html" ]]; then
    # 첫 배포 전에도 caddy가 기동하도록 자리표시자를 둔다 (deploy.sh가 web.zip으로 교체)
    printf '<!doctype html><title>DevPilot</title><p>not deployed yet</p>\n' >"${DEVPILOT_HOME}/web/index.html"
    chown "${DEPLOY_USER}:${DEPLOY_USER}" "${DEVPILOT_HOME}/web/index.html"
    chmod 644 "${DEVPILOT_HOME}/web/index.html"
  fi
}

install_files() {
  local name tmp changed=()
  for name in compose.prod.yml Caddyfile; do
    tmp="$(mktemp)"
    cp "${INFRA_DIR}/${name}" "${tmp}"
    if install_if_changed "${tmp}" "${DEVPILOT_HOME}/${name}" 644 "${DEPLOY_USER}:${DEPLOY_USER}"; then
      changed+=("${name}")
    fi
  done
  for name in deploy.sh backup.sh healthping.sh host-check.sh; do
    tmp="$(mktemp)"
    cp "${INFRA_DIR}/scripts/${name}" "${tmp}"
    if install_if_changed "${tmp}" "${DEVPILOT_HOME}/${name}" 750 "${DEPLOY_USER}:${DEPLOY_USER}"; then
      changed+=("${name}")
    fi
  done
  if ((${#changed[@]} > 0)); then
    log "갱신: ${changed[*]}"
    if [[ " ${changed[*]} " == *" Caddyfile "* ]] \
      && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'devpilot-caddy-1'; then
      log "Caddyfile 변경 → deploy 사용자로 'docker compose -f ${DEVPILOT_HOME}/compose.prod.yml restart caddy' 실행이 필요하다"
    fi
  else
    log "compose/Caddyfile/스크립트 변경 없음"
  fi
}

create_env_file_if_missing() {
  local path="$1" mode="$2"
  if [[ -e "${path}" ]]; then
    cat >/dev/null
    log "유지: ${path}"
    return 0
  fi
  install -m "${mode}" -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" /dev/null "${path}"
  cat >"${path}"
  log "생성: ${path} (값을 채워야 한다)"
}

create_env_templates() {
  create_env_file_if_missing "${DEVPILOT_HOME}/api.env" 600 <<'ENV'
# DevPilot API runtime environment (docs/10-deployment-and-operations.md §6). KEY=VALUE 한 줄씩, 따옴표·공백 없이.
# 실제 값은 저장소 밖 DevPilot-ops/ 와 password manager 에만 둔다.
DEVPILOT_AUTH_MODE=devtoken
# 필수: EC P-256 private key PEM 을 \n 으로 이어 한 줄로 (앱이 되돌린다 — 구현 시 확인)
#   openssl ecparam -genkey -name prime256v1 -noout | awk 'BEGIN{ORS="\\n"} {print}'
DEVPILOT_DEV_JWT_KEY=
# 소유자 + 초대 사용자 (쉼표, 소문자)
DEVPILOT_ALLOWED_EMAILS=
DEVPILOT_ALLOWED_SUBJECTS=
# openssl rand -hex 32
DEVPILOT_LOG_HASH_KEY=
# 공용 PostgreSQL 16 (호스트 5432). compose extra_hosts 로 host.docker.internal 이 호스트를 가리킨다
DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/devpilot
DATABASE_USERNAME=devpilot
DATABASE_PASSWORD=
# DeepSeek (prod·eval 공용 잔액, 결정 E). S3 전에는 provider=disabled
DEEPSEEK_API_KEY=
DEVPILOT_AI_PROVIDER=disabled
DEVPILOT_AI_MODEL=deepseek-flash
DEVPILOT_AI_MONTHLY_BUDGET_USD=3
CORS_ALLOWED_ORIGINS=
# 필수: https://<tailnet-host> (tailscale serve 주소). 캘린더 피드 URL·devtoken issuer
APP_BASE_URL=
ENV

  create_env_file_if_missing "${DEVPILOT_HOME}/ops.env" 600 <<'ENV'
# DevPilot ops (docs/10-deployment-and-operations.md §9, §11.2). deploy.sh·healthping.sh·backup.sh·host-check.sh 가 읽는다
# GitHub 소유자 (소문자): web.zip URL https://github.com/<owner>/DevPilot/releases/download/<tag>/web.zip,
# 이미지 ghcr.io/<owner>/devpilot-api
GITHUB_OWNER=
# healthchecks.io ping URL (알림을 무력화할 수 있으므로 0600). 비우면 ping을 보내지 않는다
HEALTHCHECKS_PING_URL=
HEALTHCHECK_BACKUP_URL=
HEALTHCHECK_HOST_URL=
ENV
}

install_systemd_units() {
  local unit changed="false" tmp
  for unit in devpilot-backup.service devpilot-backup.timer devpilot-healthping.service devpilot-healthping.timer; do
    [[ -f "${INFRA_DIR}/systemd/${unit}" ]] || die "systemd 유닛 파일이 없다: ${INFRA_DIR}/systemd/${unit}"
    tmp="$(mktemp)"
    cp "${INFRA_DIR}/systemd/${unit}" "${tmp}"
    if install_if_changed "${tmp}" "/etc/systemd/system/${unit}" 644; then
      changed="true"
    fi
  done
  if [[ "${changed}" == "true" ]]; then
    log "systemd 유닛 갱신 → daemon-reload"
    systemctl daemon-reload
  fi

  # healthping은 URL이 비어 있으면 스스로 건너뛰므로 바로 켠다
  systemctl enable --now devpilot-healthping.timer >/dev/null
  log "devpilot-healthping.timer 활성 (5분). ops.env 의 HEALTHCHECKS_PING_URL 이 비어 있으면 ping 없이 종료한다"

  if grep -Eq '^DATABASE_PASSWORD=.+' "${DEVPILOT_HOME}/api.env"; then
    systemctl enable --now devpilot-backup.timer >/dev/null
    log "devpilot-backup.timer 활성 (매일 19:00 UTC)"
  else
    log "api.env 의 DATABASE_PASSWORD 가 비어 있어 devpilot-backup.timer 를 켜지 않았다. 값을 채운 뒤:"
    log "  systemctl enable --now devpilot-backup.timer && systemctl start devpilot-backup.service"
  fi
}

print_next_steps() {
  log "완료. 남은 수동 작업:"
  log "  1) ${DEVPILOT_HOME}/api.env, ops.env 값 채우기 (권한 600 deploy 유지, 내용은 password manager에 보관)"
  log "  2) HTTPS (1회, root): tailscale serve --bg --https=443 http://127.0.0.1:${CADDY_PORT}   → tailscale serve status"
  log "  3) GHCR 패키지 ghcr.io/<owner>/devpilot-api 를 Public으로 (아니면 deploy 사용자로 docker login ghcr.io)"
  log "  4) 첫 배포: sudo -u ${DEPLOY_USER} ${DEVPILOT_HOME}/deploy.sh v0.x.y   → DEPLOY_RESULT=success"
  log "  5) 로컬 PC(tailnet): bash infra/scripts/smoke-headers.sh https://<tailnet-host>"
  log "  6) 백업 timer 활성 확인: systemctl list-timers 'devpilot-*'"
}

main() {
  parse_args "$@"
  check_prerequisites
  create_deploy_user
  create_dirs
  install_files
  create_env_templates
  install_systemd_units
  print_next_steps
}

main "$@"
