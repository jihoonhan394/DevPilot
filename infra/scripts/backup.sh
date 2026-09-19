#!/usr/bin/env bash
# DevPilot daily backup: pg_dump -Fc -> /opt/devpilot/backups/devpilot-<UTC ts>.dump, 7일 보관 (결정 C)
# 기준: docs/10-deployment-and-operations.md §8, §13.2 (db-restore) (2026-09-18)
#
# 실행: systemd devpilot-backup.timer (매일 19:00 UTC = KST 04:00), deploy.sh --backup-first, 또는 수동
#   sudo systemctl start devpilot-backup.service && journalctl -u devpilot-backup.service -n 30
#
# 입력 (source 하지 않고 필요한 키만 읽는다)
#   /opt/devpilot/api.env     DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD
#   /opt/devpilot/ops.env     HEALTHCHECK_BACKUP_URL (선택: healthchecks.io check devpilot-backup — /start, 성공, /fail)
#
# 원칙
#   - 호스트에 pg_dump가 없으므로 postgres:16 컨테이너(--network host)로 실행한다. DATABASE_URL의 host.docker.internal은
#     호스트 네트워크에서 127.0.0.1 로 바꾼다
#   - 암호화·오브젝트 스토리지 없음 (tailnet 내부, 로컬 디스크). 2차 사본은 로컬 PC의 pull-backup.ps1 (주 1회, 8주 보관)
#   - 파일은 640 (deploy 그룹의 운영자가 scp로 읽는다), .part 로 쓰고 완료 후 rename (원자적)
#   - 복구: pg_restore --clean --if-exists --no-owner -d devpilot <file>  (docs/10 §13.2)
# 보관: 7일 (mtime). 상태: /opt/devpilot/state/last_backup_ok (host-check.sh가 최신성 확인)
set -Eeuo pipefail
umask 027

readonly DEVPILOT_HOME="${DEVPILOT_HOME:-/opt/devpilot}"
readonly API_ENV="${DEVPILOT_HOME}/api.env"
readonly OPS_ENV="${DEVPILOT_HOME}/ops.env"
readonly BACKUP_DIR="${DEVPILOT_HOME}/backups"
readonly STATE_DIR="${DEVPILOT_HOME}/state"
readonly PG_IMAGE="postgres:16"
readonly RETENTION_DAYS=7
readonly MIN_BACKUP_BYTES=1024
readonly JDBC_PATTERN='^jdbc:postgresql://([^:/?]+):([0-9]+)/([^?]+)(\?(.*))?$'

HEALTHCHECK_URL=""
OUT_PART=""

log() { printf '%s [backup] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() {
  log "ERROR: $*"
  exit 1
}

read_env_value() {
  local key="$1" file="$2"
  grep -E "^${key}=" "${file}" | tail -n 1 | cut -d '=' -f 2- || true
}

require_value() {
  local name="$1" value="$2"
  [[ -n "${value}" ]] || die "${name} 값이 비어 있다 (${API_ENV})"
}

ping_healthcheck() {
  local suffix="$1"
  [[ -n "${HEALTHCHECK_URL}" ]] || return 0
  curl -fsS -m 10 --retry 3 -o /dev/null "${HEALTHCHECK_URL}${suffix}" \
    || log "WARN: healthcheck ping 실패 (${suffix:-success})"
}

on_exit() {
  local rc=$?
  if [[ -n "${OUT_PART}" && -f "${OUT_PART}" ]]; then
    rm -f -- "${OUT_PART}"
  fi
  if ((rc != 0)); then
    log "백업 실패 (exit ${rc})"
    ping_healthcheck "/fail"
  fi
}

main() {
  [[ -r "${API_ENV}" ]] || die "${API_ENV} 를 읽을 수 없다"
  [[ -d "${BACKUP_DIR}" && -w "${BACKUP_DIR}" ]] || die "${BACKUP_DIR} 에 쓸 수 없다 (prepare-server.sh)"
  mkdir -p "${STATE_DIR}"
  command -v docker >/dev/null || die "docker 가 없다"

  if [[ -r "${OPS_ENV}" ]]; then
    HEALTHCHECK_URL="$(read_env_value HEALTHCHECK_BACKUP_URL "${OPS_ENV}")"
  fi
  trap on_exit EXIT
  ping_healthcheck "/start"

  local jdbc_url db_user db_password
  jdbc_url="$(read_env_value DATABASE_URL "${API_ENV}")"
  db_user="$(read_env_value DATABASE_USERNAME "${API_ENV}")"
  db_password="$(read_env_value DATABASE_PASSWORD "${API_ENV}")"
  require_value DATABASE_URL "${jdbc_url}"
  require_value DATABASE_USERNAME "${db_user}"
  require_value DATABASE_PASSWORD "${db_password}"

  [[ "${jdbc_url}" =~ ${JDBC_PATTERN} ]] \
    || die "DATABASE_URL 형식 오류 (jdbc:postgresql://host:port/db?...)"
  local db_host="${BASH_REMATCH[1]}"
  local db_port="${BASH_REMATCH[2]}"
  local db_name="${BASH_REMATCH[3]}"
  # compose 컨테이너용 별칭은 호스트 네트워크에서 loopback 이다
  if [[ "${db_host}" == "host.docker.internal" ]]; then
    db_host="127.0.0.1"
  fi

  local timestamp file_name out size
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  file_name="devpilot-${timestamp}.dump"
  out="${BACKUP_DIR}/${file_name}"
  OUT_PART="${out}.part"

  log "pg_dump 시작 host=${db_host} port=${db_port} db=${db_name} -> ${file_name}"
  PGPASSWORD="${db_password}" \
    docker run --rm --network host -e PGPASSWORD "${PG_IMAGE}" \
    pg_dump \
    --host="${db_host}" \
    --port="${db_port}" \
    --username="${db_user}" \
    --dbname="${db_name}" \
    --format=custom \
    --no-password \
    >"${OUT_PART}"

  size="$(stat -c %s "${OUT_PART}")"
  ((size >= MIN_BACKUP_BYTES)) || die "백업 파일이 너무 작다 (${size} bytes)"
  chmod 640 "${OUT_PART}"
  mv -f "${OUT_PART}" "${out}"
  OUT_PART=""
  log "dump 생성 ${file_name} (${size} bytes)"

  # 새 백업이 성공한 뒤에만 보관 기간이 지난 파일을 지운다
  find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'devpilot-*.dump' -mtime +"${RETENTION_DAYS}" -print -delete \
    | sed 's/^/삭제(보관 기간 경과): /' || true

  printf '%s %s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${file_name}" "${size}" \
    >"${STATE_DIR}/last_backup_ok"
  ping_healthcheck ""
  log "백업 완료"
}

main "$@"
