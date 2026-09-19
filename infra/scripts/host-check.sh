#!/usr/bin/env bash
# DevPilot host check: 디스크, 컨테이너 상태·재시작, health, tailscale serve·인증서, 백업 최신성 -> healthchecks.io (선택)
# 기준: docs/10-deployment-and-operations.md §11.3 (S6 timer 자동화; 그 전에는 수동 실행) (2026-09-18)
#
# 실행 (서버, deploy 사용자 또는 root): /opt/devpilot/host-check.sh
#   S6: systemd devpilot-host-check.timer (매시) — 유닛은 S6에 추가한다
# 입력: /opt/devpilot/ops.env 의 HEALTHCHECK_HOST_URL (선택, healthchecks.io check devpilot-host). 비어 있으면 출력만 한다
# 검사 항목 (문제가 있으면 한 줄씩 출력, 종료 코드 1)
#   - 루트 디스크 사용률 < 80%
#   - devpilot-api-1, devpilot-caddy-1 running, 지난 실행 이후 재시작 없음 (state/<name>.restarts)
#   - http://127.0.0.1:18080/actuator/health "status":"UP"
#   - tailscale Running, `tailscale serve status` 에 127.0.0.1:18080 (읽을 수 없으면 WARN)
#   - tailnet 주소 443 인증서 만료 ≥ 14일 (127.0.0.1:443 은 tailscale serve가 듣지 않으므로 tailnet IP로 확인)
#   - state/last_backup_ok 가 26시간 이내
# 메모리는 정보로만 출력한다 (공용 서버 — 사용률 규칙 없음)
set -euo pipefail

readonly DEVPILOT_HOME="${DEVPILOT_HOME:-/opt/devpilot}"
readonly STATE_DIR="${DEVPILOT_HOME}/state"
readonly HEALTH_URL="http://127.0.0.1:18080/actuator/health"
readonly DISK_MAX_PERCENT=80
readonly CERT_MIN_DAYS=14
readonly BACKUP_MAX_AGE_HOURS=26

declare -a PROBLEMS=()
declare -a WARNINGS=()

log() { printf '%s [host-check] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

read_env_value() {
  local key="$1" file="$2"
  grep -E "^${key}=" "${file}" | tail -n 1 | cut -d '=' -f 2- || true
}

check_disk() {
  local disk
  disk="$(df --output=pcent / | tail -n 1 | tr -dc '0-9')"
  ((disk < DISK_MAX_PERCENT)) || PROBLEMS+=("disk ${disk}% (>= ${DISK_MAX_PERCENT}%)")
  log "disk / ${disk}%, memory available $(free -m | awk '/^Mem:/ {print $7}')MB"
}

check_containers() {
  local container state status restarts counter_file previous
  mkdir -p "${STATE_DIR}"
  for container in devpilot-api-1 devpilot-caddy-1; do
    state="$(docker inspect -f '{{.State.Status}} {{.RestartCount}}' "${container}" 2>/dev/null || echo 'missing 0')"
    status="${state% *}"
    restarts="${state#* }"
    [[ "${status}" == "running" ]] || PROBLEMS+=("${container} ${status}")
    counter_file="${STATE_DIR}/${container}.restarts"
    previous="$(cat "${counter_file}" 2>/dev/null || echo 0)"
    ((restarts <= previous)) || PROBLEMS+=("${container} restarted $((restarts - previous))x since last check")
    printf '%s\n' "${restarts}" >"${counter_file}" 2>/dev/null || WARNINGS+=("cannot write ${counter_file}")
  done
}

check_health() {
  local body
  body="$(curl -fsS -m 10 "${HEALTH_URL}" 2>/dev/null || true)"
  [[ "${body}" == *'"status":"UP"'* ]] || PROBLEMS+=("health not UP (${HEALTH_URL})")
}

check_tailscale() {
  local ts_state serve_status ts_ip ts_host end_date days
  ts_state="$(tailscale status --json 2>/dev/null | sed -n 's/.*"BackendState": *"\([^"]*\)".*/\1/p' | head -n 1)"
  [[ "${ts_state}" == "Running" ]] || PROBLEMS+=("tailscale ${ts_state:-unknown}")

  serve_status="$(tailscale serve status 2>/dev/null || true)"
  if [[ -z "${serve_status}" ]]; then
    WARNINGS+=("tailscale serve status 를 읽을 수 없다 (root 또는 operator 권한 필요)")
  elif [[ "${serve_status}" != *"127.0.0.1:18080"* ]]; then
    PROBLEMS+=("tailscale serve 가 127.0.0.1:18080 으로 설정되어 있지 않다")
  fi

  ts_ip="$(tailscale ip -4 2>/dev/null || true)"
  ts_host="$(tailscale status --self --json 2>/dev/null | sed -n 's/.*"DNSName": *"\([^"]*\)\.".*/\1/p' | head -n 1)"
  if [[ -z "${ts_ip}" || -z "${ts_host}" ]]; then
    WARNINGS+=("tailnet 주소를 읽을 수 없어 인증서 검사를 건너뛴다")
    return 0
  fi
  end_date="$(openssl s_client -servername "${ts_host}" -connect "${ts_ip}:443" </dev/null 2>/dev/null \
    | openssl x509 -noout -enddate 2>/dev/null | cut -d '=' -f 2 || true)"
  if [[ -n "${end_date}" ]]; then
    days=$((($(date -d "${end_date}" +%s) - $(date +%s)) / 86400))
    ((days >= CERT_MIN_DAYS)) || PROBLEMS+=("tls cert expires in ${days}d")
    log "tls cert ${ts_host} expires in ${days}d"
  else
    PROBLEMS+=("tls cert unreadable at ${ts_ip}:443 (tailscale serve 미설정 또는 첫 요청 전)")
  fi
}

check_backup() {
  local age_hours
  if [[ -f "${STATE_DIR}/last_backup_ok" ]]; then
    age_hours=$((($(date +%s) - $(stat -c %Y "${STATE_DIR}/last_backup_ok")) / 3600))
    ((age_hours <= BACKUP_MAX_AGE_HOURS)) || PROBLEMS+=("last backup ${age_hours}h ago")
  else
    WARNINGS+=("last_backup_ok 없음 (아직 백업이 한 번도 성공하지 않았다)")
  fi
}

report() {
  local url=""
  if [[ -r "${DEVPILOT_HOME}/ops.env" ]]; then
    url="$(read_env_value HEALTHCHECK_HOST_URL "${DEVPILOT_HOME}/ops.env")"
  fi
  if ((${#WARNINGS[@]} > 0)); then
    printf '  WARN: %s\n' "${WARNINGS[@]}"
  fi
  if ((${#PROBLEMS[@]} == 0)); then
    log "OK"
    [[ -z "${url}" ]] || curl -fsS -m 10 --retry 3 -o /dev/null "${url}" || log "WARN: healthchecks ping 실패"
    return 0
  fi
  log "문제 ${#PROBLEMS[@]}건:"
  printf '  - %s\n' "${PROBLEMS[@]}"
  [[ -z "${url}" ]] || curl -fsS -m 10 --retry 3 -o /dev/null \
    --data-binary "$(printf '%s\n' "${PROBLEMS[@]}")" "${url}/fail" || true
  return 1
}

main() {
  check_disk
  check_containers
  check_health
  check_tailscale
  check_backup
  report
}

main "$@"
