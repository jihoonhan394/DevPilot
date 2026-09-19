#!/usr/bin/env bash
# Romanized Korean identifier check for Dart sources
# 기준: docs/08-coding-conventions.md §2.2 (정규식 2개), §11.1
#
# 사용 (저장소 어디서든): bash app/tool/check_identifiers.sh
# 대상: app/lib/**/*.dart — 생성 파일(*.g.dart, *.freezed.dart, lib/l10n/app_localizations*.dart) 제외
# 종료 코드: 0 = 일치 없음 / 1 = 일치 있음(파일:줄 출력) / 2 = 실행 오류
set -euo pipefail

# grep -P는 UTF-8 또는 unibyte locale에서만 동작한다
export LC_ALL=C.UTF-8

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
LIB_DIR="$(cd "${SCRIPT_DIR}/../lib" && pwd)"
readonly LIB_DIR

# docs/08 §2.2 — 두 정규식을 그대로 옮긴다. 사전을 바꿀 때는 08 §2.2와 Checkstyle 설정을 함께 바꾼다
readonly ROMANIZED_PATTERN='(?:\b|_|(?<=[a-z])(?=[A-Z]))(?i:sawon|hoesa|gyoyuk|jumun|gogaek|mokrok|sangse|deungrok|sujeong|sujung|sakje|johoe|jeonsong|gyeolje|seungin|hakseup|bokseup|gyehoek|mokpyo|sayongja|jeongbo|bunseok|pyeongga|munje|dapbyeon|sijak|jongryo|sangtae|yocheong|eungdap|gisul|hoewon|gwalli|seolmyeong|yeonseup|jindan|gubun|yeobu|beonho|ireum|nalja|geumaek|jeojang|cheori|gyeolgwa|naeyong|jemok|gaesu|suryang|bunryu|ilja)(?=[A-Z0-9_]|\b)'
readonly YN_SUFFIX_PATTERN='(?<=[a-z0-9])Yn\b'

scan() {
  local pattern="$1" rc=0
  grep -rnP \
    --include='*.dart' \
    --exclude='*.g.dart' \
    --exclude='*.freezed.dart' \
    --exclude='app_localizations*.dart' \
    -e "${pattern}" \
    "${LIB_DIR}" || rc=$?
  # grep: 0 = 일치, 1 = 일치 없음, 2 이상 = 오류
  return "${rc}"
}

main() {
  local found=0 rc

  rc=0
  scan "${ROMANIZED_PATTERN}" || rc=$?
  case "${rc}" in
    0) found=1 ;;
    1) ;;
    *)
      printf 'check_identifiers: grep 실행 오류 (romanized, exit %s)\n' "${rc}" >&2
      exit 2
      ;;
  esac

  rc=0
  scan "${YN_SUFFIX_PATTERN}" || rc=$?
  case "${rc}" in
    0) found=1 ;;
    1) ;;
    *)
      printf 'check_identifiers: grep 실행 오류 (Yn suffix, exit %s)\n' "${rc}" >&2
      exit 2
      ;;
  esac

  if ((found == 1)); then
    printf 'check_identifiers: 로마자 한국어 식별자 또는 Yn 접미사가 있다 (docs/08 §2.2)\n' >&2
    exit 1
  fi
  printf 'check_identifiers: OK\n'
}

main "$@"
