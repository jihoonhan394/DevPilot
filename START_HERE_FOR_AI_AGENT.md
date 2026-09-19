# AI Agent 시작 지시 — S0 (Bootstrap + Walking Skeleton)

아래 문서를 읽고 **S0만** 구현하라(단계 S0~S7은 날짜 없는 구현 순서다 — `docs/11` §3). S0 범위 밖 기능(온보딩, 계획, 규칙 엔진, AI 연동)은 구현하지 않는다.

## 필독 (순서대로)

1. `AGENTS.md`
2. `docs/18-project-setup-and-local-dev.md` — 전체 (특히 §3.1 서버 Docker 터널, §5 최초 준비)
3. `docs/03-system-architecture.md` — §1, §2, §3.1, §4(특히 §4.2 devtoken 인증), §7, §9, §10
4. `docs/10-deployment-and-operations.md` — §2 서버 준비, §4 Tailscale HTTPS, §5 컨테이너, §7 CI/CD
5. `docs/05-api-spec.md` — §1.4.5 개발 토큰 endpoint (S0 구현 대상)
6. `docs/07-security-and-privacy.md` — 인증, 웹 보안, secret 관리 절
7. `docs/08-coding-conventions.md` — 자동 강제 도구 절
8. `docs/09-test-and-quality.md` — JWT 테스트, DB/migration 테스트 절
9. `docs/11-development-roadmap.md` — S0와 Spike
10. `docs/14-adrs.md` — ADR-005, ADR-010, ADR-014, ADR-016, ADR-019

## 작업 방식

- 시작 전에 S0 범위, 완료 조건, 커밋 단위 계획을 요약해서 보고한 뒤 진행한다.
- 저장소 루트에는 이미 `repo-seed/`의 파일이 복사되어 있다(AGENTS.md, CLAUDE.md, .gitattributes, .editorconfig, .gitignore, .gitleaks.toml, .env.example, backend·app·infra·content·evals·.github 초기 파일). **이 파일들을 기준으로 삼고, 내용을 바꿔야 하면 이유를 보고한다.**
- Spring Boot 4.1 의존성 좌표와 버전은 `backend/gradle/libs.versions.toml`을 따른다(2026-09-18 확인값). 새 patch 버전으로 올릴 때는 공식 저장소에서 확인하고 보고한다.
- 브랜치: `feature/BL-<EPIC>-<nn>-<슬러그>` → `developer`(squash) → `main`. 커밋은 Conventional Commits(영문).
- **실제 secret을 파일에 쓰지 않는다.** 저장소는 public이다. 서버 IP·호스트명·계정·이메일도 쓰지 않고 `<server>`, `<tailnet-host>`, `<your-email>` 자리표시자를 쓴다(실제 값은 저장소 밖 `DevPilot-ops/`).
- 로컬 테스트용 Docker는 서버 Docker를 SSH 소켓 포워딩으로 쓴다(`infra/scripts/dev-docker-tunnel.ps1`). Docker Desktop은 선택이다.

## S0 범위

| # | 항목 | BL |
|---|---|---|
| 1 | monorepo 구조 확정: `backend/`, `app/`, `infra/`, `content/`, `evals/`, `docs/`, `database/`, `.github/` | BL-FND |
| 2 | backend: Gradle 설정(toolchain, version catalog, Spotless AOSP, Checkstyle, PMD, SpotBugs, JaCoCo, `openApiCheck`), `./gradlew check` 통과 | BL-FND, BL-FND-22 |
| 3 | backend: `common` 기반 — `ClockConfig`, `TraceIdFilter`, `RequestBodySizeLimitFilter`, `ErrorCode`(S0 사용분), `GlobalExceptionHandler`(ProblemDetail), `SecurityConfig`(JWT resource server, stateless, health 공개) | BL-FND |
| 4 | backend: Flyway 적용, Testcontainers(`postgres:16`) migration 통합 테스트. `db/migration`에는 V1~V9가 이미 있고 Flyway는 **전부** 적용한다(`docs/04` §10) — S0은 entity를 만들지 않으므로 `ddl-auto=validate`에 걸리는 것이 없다. 테스트는 빈 DB → 전체 migration → `database/schema.sql`과 같은지 확인 | BL-FND |
| 5 | backend: **devtoken 인증 모드**(`docs/03` §4.2, `docs/05` §1.4.5) — `POST /api/v1/dev/token`(allowlist 검사 → EC P-256 서명 JWT), `GET /api/v1/dev/jwks.json`, 앱이 가진 공개키로 검증하는 `JwtDecoder`. `DEVPILOT_DEV_JWT_KEY`가 비면 기동 시 생성(prod는 필수) | BL-SEC-16 |
| 6 | backend: 임시 `GET /api/v1/me` — JWT `sub`, `email`을 반환 (사용자 테이블·allowlist 프로비저닝은 S1). 테스트: 토큰 없음 401 `AUTHENTICATION_REQUIRED`, 발급 토큰 200, 잘못된 audience·만료·서명 401 | BL-FND |
| 7 | backend: ArchUnit 테스트 — 필드 주입 금지, 무인자 `now()` 금지, `System.out` 금지, controller는 presentation 패키지 | BL-FND |
| 8 | app: FVM 고정 Flutter, Riverpod·go_router·dio, 한글 폰트 번들, **`AUTH_MODE=dev` 로그인 화면(이메일 입력 → `/dev/token` → 토큰 저장)** → `/api/v1/me` 결과 표시, widget test 1개, `fvm flutter analyze` 0건 | BL-CLI-03 |
| 9 | infra: 서버 준비(`prepare-server.sh`), `tailscale serve --bg --https=443 http://127.0.0.1:18080`, `compose.prod.yml`·`Caddyfile`·`Dockerfile`·`deploy.sh` 동작 확인 | BL-OPS |
| 10 | CI: `ci.yml`의 필수 체크 `security`(gitleaks·action pin 검사), `backend`, `app`, `content`가 `main`·`developer` PR에서 green, `dependabot.yml`, 모든 action을 commit SHA로 고정 | BL-OPS, BL-SEC |
| 11 | 배포: `release.yml`이 태그 `v0.*`에서 **linux/amd64** 이미지를 GHCR에 push, 로컬에서 `ssh <server> "sudo -u deploy /opt/devpilot/deploy.sh v0.0.1"`로 배포 확인 | BL-OPS |
| 12 | Spike 실행 및 결과 기록 (자동화할 수 없는 수동 확인은 체크리스트로 남김) | BL-FND |

## 범위 밖

- `app_user` 등 도메인 테이블의 entity·repository(테이블은 migration으로 생긴다), 온보딩, 계획, Today, 복습, AI 연동
- Supabase(인증·DB) — Later. `supabase` auth-mode 코드 경로는 만들지 않는다
- Trivy·CodeQL·image digest 고정 — S2 P1
- 문서에 없는 라이브러리 추가

## 완료 조건

- [ ] `./gradlew check` 성공 (unit + Testcontainers + ArchUnit + 정적 분석 + `openApiCheck`)
- [ ] `fvm flutter analyze` 0건, `fvm flutter test` 성공, `fvm flutter build web --release` 성공
- [ ] `main`·`developer` PR에서 필수 체크 `security`·`backend`·`app`·`content` green, action SHA 고정 완료
- [ ] 로컬: Flutter web에서 이메일 로그인 → `/api/v1/me` 응답 표시
- [ ] 운영: `https://<tailnet-host>`에서 같은 흐름 동작, `/actuator/health` 200 (사람이 수행할 tailnet HTTPS 활성화·서버 준비는 명령과 체크리스트로 보고)
- [ ] Spike 결과와 ADR 갱신안 보고 (특히 Java 25 toolchain 실패 시 Java 21 전환 여부)
- [ ] 저장소에 실제 secret·서버 주소·이메일 없음

## 보고

S0를 마치면 다음 단계로 넘어가지 말고 `AGENTS.md` §6 형식으로 보고하라. 사람이 직접 해야 하는 작업(tailnet HTTPS 활성화, healthchecks.io 계정, 서버 `api.env` 작성, GitHub branch protection·secret)은 따로 목록으로 정리하라.
