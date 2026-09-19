#!/usr/bin/env bash
# DevPilot deploy smoke: production response headers
# 기준: docs/07-security-and-privacy.md §9.3 (헤더 값), §16 ST-27 / docs/10-deployment-and-operations.md §4.2, §9.2 (2026-09-18)
#
# 사용: smoke-headers.sh [base-url]
#   base-url   기본 http://127.0.0.1:18080 (서버 안에서: caddy 직접). 로컬 PC(tailnet, Git Bash)에서는
#              https://<tailnet-host> 로 tailscale serve 경로까지 확인한다. 값은 인자로만 주고 파일에 쓰지 않는다
# 환경변수: SMOKE_WAIT_SECONDS (기본 60) — 응답을 기다리는 최대 시간 (첫 HTTPS 요청의 인증서 발급 포함)
#
# 검사
#   GET /            200, 공통 보안 헤더, Cache-Control: no-cache, Server 헤더 없음
#   GET /api/v1/me   401 (토큰 없음), 공통 보안 헤더, Cache-Control에 no-store, Server 헤더 없음
# 종료 코드: 0 = 모두 일치 / 1 = 불일치 / 2 = 사용법 오류·응답 없음
set -euo pipefail

readonly WAIT_SECONDS="${SMOKE_WAIT_SECONDS:-60}"
readonly DEFAULT_BASE_URL="http://127.0.0.1:18080"
readonly EXPECTED_HSTS='max-age=31536000; includeSubDomains'
readonly EXPECTED_PERMISSIONS='camera=(), microphone=(), geolocation=(), payment=(), usb=()'

BASE_URL=""
declare -a FAILURES=()
WORK_DIR=""

log() { printf '[smoke] %s\n' "$*"; }

usage() {
  printf 'usage: %s [base-url]   (기본 %s)\n' "$(basename "$0")" "${DEFAULT_BASE_URL}" >&2
  exit 2
}

cleanup() {
  if [[ -n "${WORK_DIR}" && -d "${WORK_DIR}" ]]; then
    rm -rf -- "${WORK_DIR}"
  fi
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -h | --help)
        usage
        ;;
      -*)
        usage
        ;;
      *)
        [[ -z "${BASE_URL}" ]] || usage
        BASE_URL="$1"
        ;;
    esac
    shift
  done
  BASE_URL="${BASE_URL:-${DEFAULT_BASE_URL}}"
  BASE_URL="${BASE_URL%/}"
  [[ "${BASE_URL}" =~ ^https?://[A-Za-z0-9.:-]+$ ]] || usage
}

# URL의 응답 헤더를 파일에 저장하고 status code를 출력한다
fetch() {
  local path="$1" out="$2"
  curl -sS --max-time 10 --proto '=http,https' \
    -o /dev/null -D "${out}" -w '%{http_code}' "${BASE_URL}${path}"
}

# 헤더 파일에서 값 하나를 읽는다 (이름 대소문자 무시, 마지막 값)
header_value() {
  local file="$1" name="$2"
  awk -v name="${name}" '
    {
      line = $0
      sub(/\r$/, "", line)
      i = index(line, ":")
      if (i > 0 && tolower(substr(line, 1, i - 1)) == name) {
        value = substr(line, i + 1)
        sub(/^[ \t]+/, "", value)
        sub(/[ \t]+$/, "", value)
        last = value
        found = 1
      }
    }
    END { if (found) print last }
  ' "${file}"
}

has_header() {
  local file="$1" name="$2"
  awk -v name="${name}" '
    { i = index($0, ":"); if (i > 0 && tolower(substr($0, 1, i - 1)) == name) found = 1 }
    END { exit found ? 0 : 1 }
  ' "${file}"
}

expect_equals() {
  local label="$1" file="$2" name="$3" expected="$4" actual
  actual="$(header_value "${file}" "${name}")"
  if [[ "${actual}" != "${expected}" ]]; then
    FAILURES+=("${label}: ${name} = '${actual}' (expected '${expected}')")
  fi
}

expect_contains() {
  local label="$1" file="$2" name="$3" expected="$4" actual
  actual="$(header_value "${file}" "${name}")"
  if [[ "${actual}" != *"${expected}"* ]]; then
    FAILURES+=("${label}: ${name} = '${actual}' (must contain '${expected}')")
  fi
}

check_common() {
  local label="$1" file="$2" csp
  # HSTS는 Caddy가 항상 붙인다 (tailscale serve가 HTTPS로 전달). http://127.0.0.1:18080 에서도 값이 있어야 한다
  expect_equals "${label}" "${file}" strict-transport-security "${EXPECTED_HSTS}"
  expect_equals "${label}" "${file}" x-content-type-options "nosniff"
  expect_equals "${label}" "${file}" x-frame-options "DENY"
  expect_equals "${label}" "${file}" referrer-policy "no-referrer"
  expect_equals "${label}" "${file}" permissions-policy "${EXPECTED_PERMISSIONS}"
  expect_equals "${label}" "${file}" cross-origin-opener-policy "same-origin"
  expect_equals "${label}" "${file}" cross-origin-resource-policy "same-origin"

  csp="$(header_value "${file}" content-security-policy)"
  if [[ -z "${csp}" ]]; then
    csp="$(header_value "${file}" content-security-policy-report-only)"
  fi
  if [[ "${csp}" != *"default-src 'self'"* || "${csp}" != *"frame-ancestors 'none'"* ]]; then
    FAILURES+=("${label}: CSP 헤더 없음 또는 default-src/frame-ancestors 누락")
  fi

  if has_header "${file}" server; then
    FAILURES+=("${label}: Server 헤더가 제거되지 않았다")
  fi
}

wait_for_response() {
  local deadline=$((SECONDS + WAIT_SECONDS)) status
  while ((SECONDS < deadline)); do
    status="$(curl -s --max-time 5 --proto '=http,https' -o /dev/null -w '%{http_code}' "${BASE_URL}/" || true)"
    if [[ "${status}" =~ ^[2-4][0-9][0-9]$ ]]; then
      return 0
    fi
    sleep 3
  done
  return 1
}

main() {
  parse_args "$@"
  WORK_DIR="$(mktemp -d)"
  trap cleanup EXIT

  wait_for_response || {
    log "${BASE_URL}/ 가 ${WAIT_SECONDS}초 안에 응답하지 않았다"
    exit 2
  }

  local root_headers="${WORK_DIR}/root.txt" api_headers="${WORK_DIR}/api.txt" status

  status="$(fetch / "${root_headers}")"
  [[ "${status}" == "200" ]] || FAILURES+=("GET /: status ${status} (expected 200)")
  check_common "GET /" "${root_headers}"
  expect_equals "GET /" "${root_headers}" cache-control "no-cache"

  status="$(fetch /api/v1/me "${api_headers}")"
  [[ "${status}" == "401" ]] || FAILURES+=("GET /api/v1/me: status ${status} (expected 401)")
  check_common "GET /api/v1/me" "${api_headers}"
  expect_contains "GET /api/v1/me" "${api_headers}" cache-control "no-store"

  if [[ ${#FAILURES[@]} -gt 0 ]]; then
    log "헤더 불일치 ${#FAILURES[@]}건:"
    printf '  - %s\n' "${FAILURES[@]}"
    exit 1
  fi
  log "OK: ${BASE_URL} 헤더가 docs/07 §9.3과 일치한다"
}

main "$@"
