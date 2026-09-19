@AGENTS.md

# Claude Code 메모

- 공통 규칙은 위에서 import한 `AGENTS.md`다. 이 파일에는 Claude Code에만 해당하는 내용만 둔다.
- 개발 PC는 Windows 11이다. 셸 스크립트, Dockerfile, YAML은 LF를 유지한다(`.gitattributes`).
- Testcontainers 테스트 전에 Docker 연결을 확인한다(`docker info`). 로컬 Docker Desktop은 선택이고, 기본은 서버 Docker를 SSH 터널로 쓴다: `pwsh infra/scripts/dev-docker-tunnel.ps1` (`docs/18-project-setup-and-local-dev.md` §3.1).
- Flutter 명령은 `fvm flutter …`로 실행한다.
- 작업을 시작할 때 BL ID·관련 AC·변경 예정 파일을 먼저 요약하고, 끝나면 `AGENTS.md` §6 형식으로 보고한다.
