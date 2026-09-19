# AGENTS.md — DevPilot AI 개발 에이전트 규칙

이 파일은 DevPilot 저장소에서 작업하는 모든 AI 코딩 에이전트의 최상위 규칙이다. 문서(`docs/`)가 source of truth이며, 이 파일은 그 문서를 읽고 지키는 방법을 정한다.

## 0. 기준 버전 (기억에 의존하지 않는다)

| 영역 | 기준 | 확인 위치 |
|---|---|---|
| Java | 25 LTS (Eclipse Temurin) — ADR-005에 따라 21일 수 있음 | `backend/build.gradle.kts` toolchain |
| Spring Boot | 4.1.x | `backend/gradle/libs.versions.toml` |
| PostgreSQL | **16** (개발·운영 모두 자체 서버의 공용 인스턴스, Testcontainers 동일). PG 17 전용 문법 금지 | `docs/04-domain-model-and-db.md` |
| Flutter | `app/.fvmrc` 고정 버전 (`fvm flutter …`) | `app/.fvmrc` |
| AI | **DeepSeek `deepseek-flash`** (Spring `RestClient`로 `POST /responses`, SDK 없음) | `docs/17-ai-integration.md` |
| 인증 | **`devtoken` 모드**(운영 포함, tailnet 전용). Supabase Auth는 Later | `docs/03-system-architecture.md` §4.2 |
| 호스팅 | 자체 Linux 서버(x86_64) + Tailscale HTTPS. 공개 도메인·OCI 없음 | `docs/10-deployment-and-operations.md` |

- Spring Boot 4 / Spring Framework 7 / Hibernate 7 / Jackson 3 기준으로 작성한다. **Boot 3 시절 설정, 의존성 좌표, API를 추측으로 쓰지 않는다.** 확실하지 않으면 공식 문서나 실제 의존성 jar를 확인하고 컴파일로 검증한다.
- DeepSeek API는 Java SDK가 없다. 요청·응답 형식은 `docs/17-ai-integration.md` §2·§5를 그대로 쓰고, **"(구현 시 실제 응답으로 확인)"이 붙은 항목은 실제 호출로 확인한 뒤 문서를 먼저 고친다.**

## 1. 작업 시작 전

1. 작업 지시에 적힌 BL ID와 관련 문서를 읽는다. 최소: `docs/03-system-architecture.md`(모듈·클래스 이름), 작업 영역의 `docs/04`(데이터), `docs/05`(API), `docs/06`(규칙), `docs/12`(AC).
2. 작업 범위, 관련 AC, 변경할 파일 목록을 먼저 요약한다.
3. 문서끼리 모순되거나 문서에 없는 결정이 필요하면 **구현을 멈추고** 질문한다. 변경이 필요하면 `docs/14-adrs.md`에 Proposed ADR 초안을 함께 제시한다.

## 2. 절대 금지

- 한글 식별자, 한국어 발음 로마자 식별자(`sawon`, `hoesa`, `gyoyuk` 등), 의미 없는 이름(`a`, `tmp`, `data1`). 짧은 loop index는 예외
- `docs/03` §2.2 모듈 의존 규칙 위반. 다른 모듈의 entity/repository 직접 사용
- Controller에서 Repository 호출, Controller/Widget에 비즈니스 로직
- `catch (Exception e)` / `catch (Throwable t)` — 허용 위치는 `docs/03` §7의 세 곳뿐. 빈 catch, 원인 예외 유실
- **`@Transactional` 안에서 `AiGateway`/`AiProvider` 호출**
- **무인자 `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `OffsetDateTime.now()`, `ZonedDateTime.now()`** — 주입된 `Clock` 사용
- **학습 규칙 계산에 `double`/`float` 사용** — bp/micro 정수 (`docs/06` §1)
- 응답·일반 로그에 stack trace, 토큰, API key, 비밀번호, Authorization 헤더, 요청 body, 사용자 코드, AI 프롬프트·응답 원문 출력
- `.env`, 실제 credential, `DEEPSEEK_API_KEY`, 서버 IP·호스트명·계정·이메일 커밋 (**저장소는 public**. 자리표시자 `<server>`, `<tailnet-host>`, `<your-email>`를 쓰고 실제 값은 저장소 밖 `DevPilot-ops/`에 둔다)
- SQL 문자열 연결, allowlist 없는 동적 정렬 컬럼
- AI 출력을 서버 가드 없이 저장. `VERIFIED`를 AI 판단으로 부여. AI가 skill 레벨·점수·outcome을 직접 결정
- 서버에서 사용자가 준 URL fetch, 외부 사이트 크롤링
- 적용된 Flyway migration 수정, `ddl-auto`를 `validate` 외 값으로 변경
- 문서에 없는 인프라(Kafka, Redis, Kubernetes, MQ, 추가 DB) 도입
- 테스트를 통과시키려고 테스트 삭제, assertion 약화, `@Disabled` 추가

## 3. 구현 규칙 요약

| 영역 | 규칙 | 상세 |
|---|---|---|
| 이름 | 클래스·패키지 이름은 `docs/03` §3에 적힌 이름을 그대로 쓴다. 용어는 `docs/15` 명명 사전 | 03, 15 |
| 계층 | presentation → application → domain / infrastructure. 도메인 규칙 클래스는 순수 Java | 03 §2.3 |
| 데이터 | enum은 `docs/04` §3 레지스트리 값만. 상태 전이는 `docs/04` §4 표만 허용 | 04 |
| 규칙 | planner, budget, review, skill updater, hint, guard는 `docs/06`을 그대로 구현하고 test vector를 모두 통과 | 06 |
| API | 경로·DTO·오류 코드는 `docs/05`. 오류는 RFC 9457 ProblemDetail + `code`. 모든 인증 POST는 Idempotency-Key | 05 |
| 트랜잭션 | T-1~T-6. AI는 트랜잭션 밖. 비동기 AI는 AFTER_COMMIT 이벤트 + `aiTaskExecutor` | 03 §5 |
| AI | `AiGateway`만 사용. prompt는 버전 불변. 출력 스키마·가드는 `docs/17` | 17 |
| 보안 | allowlist, 소유권(타인 리소스 404), masking → 예산 → 저장 순서 | 07 |
| 코드 스타일 | `./gradlew spotlessApply`(AOSP), `dart format`. Checkstyle/PMD/SpotBugs/ArchUnit 통과 | 08 |
| 설정 | 가중치·임계값·단가·한도는 `devpilot.*` 설정 (`docs/03` §9) | 03 §9 |

## 4. 테스트 없는 완료 금지

- 규칙 클래스: `docs/06`의 test vector 전부를 parameterized test로
- application service: 성공·실패·상태 전이
- repository/migration: Testcontainers `postgres:16`, 전체 migration 적용 + validate (로컬 Docker가 없으면 `infra/scripts/dev-docker-tunnel.ps1`로 서버 Docker를 쓴다)
- API: validation, ProblemDetails 형식, **다른 사용자 토큰 → 404**, 토큰 없음 → 401, allowlist 밖 → 403
- AI: `FakeAiProvider` fixture. 실제 API 호출은 `evalTest` source set(`./gradlew aiEval`)에서만
- ArchUnit 규칙 통과
- 버그 수정은 재현 테스트를 먼저 추가

상세: `docs/09-test-and-quality.md`

## 5. 변경 단위와 문서 동기화

- 한 작업 = 하나의 BL 항목(또는 밀접한 2~3개). 범위 밖 발견 사항은 보고에만 적는다.
- API 변경 → `docs/05` + OpenAPI 스냅샷(`docs/api/openapi.yaml`). DB 변경 → 새 migration + `database/schema.sql` 스냅샷. 규칙 변경 → `docs/06` + vector. 정책 변경 → ADR.
- prompt/모델/가드 변경 → eval 실행 필요를 보고한다.

## 6. 완료 보고 형식

```text
BL: (처리한 BL ID)
Changed: (주요 변경 파일과 요지)
Tests: (실행 명령과 결과 — 예: ./gradlew check → BUILD SUCCESSFUL, 312 tests)
AC: (확인한 AC ID)
Security impact:
Docs updated:
Decisions needed:
Known limitations / Next:
```
