#!/usr/bin/env bash
# DevPilot health ping: 로컬 health -> healthchecks.io (dead-man switch)
# 기준: docs/10-deployment-and-operations.md §11.1, §11.2 (2026-09-18)
#
# 실행: systemd devpilot-healthping.timer (5분), User=deploy. 수동: /opt/devpilot/healthping.sh
# 입력: /opt/devpilot/ops.env 의 HEALTHCHECKS_PING_URL (healthchecks.io check devpilot-health, Period 5분 / Grace 10분)
#       비어 있으면 ping 없이 0으로 끝난다 (첫 배포 전·URL 미설정 상태에서 timer가 실패 로그를 남기지 않게)
# 검사: http://127.0.0.1:18080/actuator/health (caddy → api). 200 + "status":"UP" 이면 ping, 아니면 ${URL}/fail
# 종료 코드: 0 = UP (또는 URL 없음) / 1 = health 실패
set -euo pipefail

readonly DEVPILOT_HOME="${DEVPILOT_HOME:-/opt/devpilot}"
readonly HEALTH_URL="http://127.0.0.1:18080/actuator/health"

log() { printf '%s [healthping] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

read_env_value() {
  local key="$1" file="$2"
  grep -E "^${key}=" "${file}" | tail -n 1 | cut -d '=' -f 2- || true
}

main() {
  local url="" body
  if [[ -r "${DEVPILOT_HOME}/ops.env" ]]; then
    url="$(read_env_value HEALTHCHECKS_PING_URL "${DEVPILOT_HOME}/ops.env")"
  fi
  if [[ -z "${url}" ]]; then
    log "HEALTHCHECKS_PING_URL 이 비어 있어 ping 을 보내지 않는다 (${DEVPILOT_HOME}/ops.env)"
    exit 0
  fi

  body="$(curl -fsS -m 10 "${HEALTH_URL}" 2>/dev/null || true)"
  if [[ "${body}" == *'"status":"UP"'* ]]; then
    curl -fsS -m 10 --retry 3 -o /dev/null "${url}" || log "WARN: healthchecks ping 실패"
    exit 0
  fi

  log "health not UP: ${body:-no response}"
  curl -fsS -m 10 --retry 3 -o /dev/null --data-binary "health not UP $(date -u +%FT%TZ)" "${url}/fail" \
    || log "WARN: healthchecks /fail ping 실패"
  exit 1
}

main "$@"
