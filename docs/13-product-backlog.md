# 13. Product Backlog

> Status: Accepted (v2) · Last updated: 2026-09-20 · Related: `11-development-roadmap.md`, `12-acceptance-criteria.md`, `16-definition-of-ready-done.md`, `03-system-architecture.md`, `06-learning-engine-rules.md`
>
> 2026-09-18 반영: 자체 서버 + Tailscale HTTPS, 서버 PostgreSQL 16, `devtoken` 운영 인증(BL-SEC-16), DeepSeek(BL-AIP-03·11·15·17), 수동 배포·`pg_dump` 백업(BL-OPS-07·11), git 전략(BL-OPS-21). Anthropic·도메인 항목은 `~~취소~~` 또는 Later로 옮겼다. Tailscale 전용·`devtoken`은 **임시 수단**이고 최종은 공개 배포다 — 공개 인증(`BL-SEC-18`)은 그 필수 선행이고 OCI Always Free(arm64)는 배포지 후보다(`11` §3.10). "(검토 제안, 미결정)" 표시 항목은 착수 전에 사용자 결정이 필요하다.
>
> 2026-09-20 반영(ADR-040, DEC-29~31): 새 BL — 재현 과제 `BL-TDY-17`·`BL-TDY-18`·`BL-TRN-17`·`BL-RDK-05`·`BL-SKL-08`·`BL-CLI-37`(S4), 학습 트랙 2종 `BL-GOL-18`·`BL-CNT-17`·`BL-CLI-36`·`BL-TRN-16`(S3), 사이드 프로젝트 결정·장애 기록 `BL-PRJ-02`·`BL-CLI-35`(S3)·`BL-EVD-09`(S6), 공통 migration `BL-FND-27`(`V10`, S3). §5·§6 색인을 함께 갱신했다.
>
> 2026-09-18 v3 반영(단계 재배치): `S0`~`S7`은 날짜 없는 **구현 단계 ID**다(M1 = S0~S3, S3 완료 = 실사용 시작 / M2 = S4~S7, §2). 새 Epic `PRJ`(사이드 프로젝트)·`RDK`(러버덕)와 BL-PRJ-01, BL-RDK-01~04, BL-CLI-32~34, BL-MEM-12(교차 학습), BL-GOL-17(확장 제안), BL-CNT-15(curated repos), BL-TDY-16(`READ_CODE`)을 추가했다. budget·risk·replan(BL-GOL-08~14, BL-CLI-25) S5 → S2와 그 선행 BL-FND-25 S3 → S2, 캘린더(BL-TDY-13, BL-CLI-18)·AI 문제 생성(BL-TRN-04) S3 → S5, CSP 강제(BL-SEC-08) S3 → S4, Evals v1(BL-AIP-13) S4 → S3, 진단(BL-TRN-13, BL-CLI-23, BL-CNT-08) P1 → P0.
>
> 모든 구현 작업의 단위 목록이다. 에이전트 작업 지시(`11-development-roadmap.md` §7)는 이 문서의 `BL-*` ID 하나 또는 같은 단계의 인접 ID 묶음 단위로 한다. 요구사항 ID(`FR-*`, `NFR-*`)의 상세는 `01-product-requirements.md`, 화면 ID(`SCR-*`)는 `02-user-scenarios-and-ux.md`를 따른다.

---

## 1. 우선순위 정의

| Priority | 정의 | 범위를 줄여야 할 때 |
|---|---|---|
| **P0** | 해당 단계 exit criteria(AC)를 통과하는 데 필수이거나, 이월하면 다음 단계 작업이 막힌다 | 이월 금지. 단계 목표를 줄여서라도 끝낸다 |
| **P1** | MVP 범위이고 AC에 연결되지만, RISK-01 stop-loss(`11-development-roadmap.md` §6)가 발동하면 Later로 옮길 수 있다 | 다음 단계로 1회 이월 가능. 2회 이월 시 `20-decisions-and-risks.md`에 결정 기록 |
| **P2** | 품질·편의 향상. AC에 직접 영향이 없다 | 그 단계의 P0·P1이 끝난 뒤에만 착수 |
| **P3** | Later. M1·M2 범위 밖 | 착수하지 않는다 |

## 2. 작성 규칙

- ID: `BL-<EPIC>-<nn>`. 한번 부여한 ID는 재사용하지 않는다. 항목을 삭제하면 행을 지우지 않고 제목 앞에 `~~취소~~`를 붙인다.
- **굵은 클래스 이름**은 `03-system-architecture.md` §3.2의 도메인 규칙 클래스다. 규칙과 test vector는 `06-learning-engine-rules.md`가 기준이다.
- 구현은 **전부 에이전트가 한다.** 사용자는 결정(ADR 승인)·PR 리뷰·계정/서버 설정만 한다(`11-development-roadmap.md` §5).
- "(DoR 미충족)" 표시 항목은 규칙 정의가 canonical 문서에 없다. 착수 전에 문서를 먼저 보강한다(`16-definition-of-ready-done.md` §1).
- Depends on에는 선행 BL만 적는다. Spike 결과가 선행 조건이면 해당 Spike BL을 적는다.
- **단계 (Sprint 열)**: `S0`~`S7`은 **구현 순서를 나타내는 단계 ID**다. 기간·날짜·일수가 없고, 단계는 exit criteria(`11-development-roadmap.md` §3, `12-acceptance-criteria.md` §1)를 통과하면 끝난다.
  - **M1 = S0 · S1 · S2 · S3** — 쓸 수 있는 최소. **S3 완료 = 실사용 시작**(매일 쓰기 시작). S2가 끝나면 AI 없이 Today·Review만 먼저 써도 된다(선택).
  - **M2 = S4 · S5 · S6 · S7** — M1을 쓰면서 필요한 순서로 진행한다. 단계 순서는 잠정이다.
  - **Later** — M1·M2 밖. `공개 배포 전`은 단계가 아니라 공개 전환 게이트(`11-development-roadmap.md` §3.10)에 걸린 항목이다.
  - 목표일은 문서의 상수가 아니라 사용자가 설정창에 등록하는 값(`learning_goal.target_completion_date`)이다. 설명 안의 날짜는 이미 일어난 일의 기록(예: "2026-09-18 취소")뿐이다.

## 3. Epic 목록

| Epic | 이름 | 범위 |
|---|---|---|
| `FND` | Foundation | 백엔드 골격, 공통 모듈(`common`), migration, 프로필 API, 스케줄 공통 |
| `SEC` | Security · Privacy | 인증, allowlist, 격리, 헤더, 스캔, export·계정 삭제 |
| `OPS` | Deploy · Ops | 서버 준비, Caddy, CI/CD(git 전략), Tailscale HTTPS, 수동 배포, 백업, runbook, 모니터링 |
| `CLI` | Flutter Client | 앱 셸, API 클라이언트, 모든 화면, PWA |
| `GOL` | Goal · Plan | 학습 목표(**학습 트랙** 2종), 계획·버전, 온보딩, budget·risk·replan(축소·확장 제안) |
| `SKL` | Skill | skill catalog, skill state, 학습 이벤트, 레벨 갱신 |
| `TDY` | Today | planner, daily plan, 학습 세션, 코드 읽기 과제(`READ_CODE`)·`GET /readings/{readingKey}`, **재현 과제(`REDO`)와 AI 잠금 판정**, dashboard, 캘린더 |
| `MEM` | Memory Review | 복습 항목, 스케줄, due·교차 학습(RV-INTERLEAVE), 평가, variant |
| `TRN` | Training | challenge, attempt, self-explanation, Hint Ladder, 제출·평가, 진단 |
| `COA` | Project Coach | 코드 리뷰, finding, 응답 피드백, thinking pattern, purge |
| `EVD` | Evidence · Weekly | 지표, 주간 리뷰, thinking 추세, evidence, export |
| `REQ` | 로드맵 비교 (Roadmap compare) | 붙여넣은 공개 로드맵·기술 목록의 항목 추출과 분류(READY/STRETCH/LATER). 식별자는 `radar` 모듈·`requirement_doc`/`requirement_item` |
| `AIP` | AI Platform | `integration.ai` 전체, 비동기 AI 공통, evals |
| `CNT` | Content | seed YAML, curated repos, 검증기, seeder, prompt 파일 |
| `PRJ` | Side Project | `project` 모듈: 사이드 프로젝트 등록·조회·수정·삭제, **결정·장애 기록**(`side_project_note`). 학습이 적용될 대상(`PROJECT_TASK`·Coach·러버덕 `PROJECT_WORK`) |
| `RDK` | Rubber Duck | `rubberduck` 모듈(`03` §2.1 — 여러 모듈의 대상을 읽으므로 순환을 피한 독립 모듈)의 러버덕: 세션·턴·정리, gaps → 복습 카드, `RUBBER_DUCK_COMPLETED`, AI 계약(`RUBBER_DUCK`·`RUBBER_DUCK_SUMMARY`, `NoAnswerGuard`), 방치 세션 정리 |

---

## 4. Backlog

### 4.1 FND — Foundation

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-FND-01 | SP-4 툴체인 호환 spike | seed 빌드 파일로 Gradle 9.7.1 + Boot 4.1.1 + Temurin 25.0.4에서 compile(-Werror)·Spotless·Checkstyle·PMD·SpotBugs·bootJar는 확인됨(09-17). 서버 Docker 터널 Testcontainers 실행과 RestClient + DeepSeek `/responses` smoke는 확인됨(09-18). 남은 확인: ArchUnit·springdoc 동작, 도구별 위반 검출. Java SDK·WireMock 의존성 없음. 절차는 `11-development-roadmap.md` §4 SP-4 | P0 | S0 | — | — | NFR-07, NFR-10 |
| BL-FND-02 | Monorepo 구조·루트 파일 | `backend/ app/ content/ evals/ infra/ docs/` 구조, `CLAUDE.md`(`@AGENTS.md`), `AGENTS.md`, `.gitattributes`(sh·gradlew·yml LF), `.editorconfig`, `.gitignore`, `.env.example`(키 이름만) | P0 | S0 | — | — | NFR-07 |
| BL-FND-03 | Backend bootstrap | Initializr 좌표 그대로 사용, Gradle Kotlin DSL + `libs.versions.toml`, toolchain은 SP-4 결과. `application.yml` 기본값(open-in-view=false, ddl-auto=validate, Flyway schema `devpilot`, actuator health만 노출), `GET /actuator/health` | P0 | S0 | BL-FND-01, BL-FND-02 | — | NFR-03 |
| BL-FND-04 | 품질 게이트 | Spotless(google-java-format AOSP, 100열), Checkstyle(명명 규칙 전용, ASCII 정규식), PMD 7, SpotBugs, JaCoCo(`09-test-and-quality.md` §15가 열거한 패키지 목록 기준 gate — 와일드카드 금지, 수치도 `09` §15) → `./gradlew check` 하나로 실행 | P0 | S0 | BL-FND-03 | — | NFR-07 |
| BL-FND-05 | ArchUnit 기본 규칙 | `08-coding-conventions.md`의 ARCH-* 규칙: 계층 의존, 필드 주입 금지, 무인자 `now()` 금지, `System.out` 금지, `@RestController` 위치, 모듈 의존표(`03` §2.2). 모듈이 추가될 때마다 규칙 대상 확장 | P0 | S0 | BL-FND-03 | — | NFR-07 |
| BL-FND-06 | Flyway V1 + migration 통합 테스트 | `V1__baseline.sql`(schema 생성 + 조건부 하드닝 DO 블록 — 순수 PostgreSQL에서는 no-op, Supabase 도입 시 revoke). Testcontainers `postgres:16`(서버 개발 DB와 같은 major)에서 빈 DB → 전체 migration → `ddl-auto=validate` 기동 테스트 | P0 | S0 | BL-FND-03, BL-OPS-01 | AC-20 | NFR-07 |
| BL-FND-07 | TraceId·요청 크기·Clock | `TraceIdFilter`(32 hex, MDC, 응답 헤더), `RequestBodySizeLimitFilter`(64KB 초과 413 `REQUEST_TOO_LARGE`), `ClockConfig`(UTC) + 테스트용 `MutableClock` | P0 | S0 | BL-FND-03 | AC-08 | NFR-06 |
| BL-FND-08 | 오류 처리 아키텍처 | `ErrorCode`(오류 코드 카탈로그 1:1), `DevPilotException` 계열, `GlobalExceptionHandler`(`03` §7 매핑, `code`·`traceId`·`errors`), `messages_ko.properties` | P0 | S1 | BL-FND-07 | AC-08 | NFR-04 |
| BL-FND-09 | 설정 바인딩 | `DevPilotProperties` 중첩 record + `@Validated`. 가중치 합·threshold 순서 검증, 소수 설정의 bp/micro 변환(N-6) 실패 시 기동 실패. 모듈 착수 시 해당 절을 추가 | P0 | S1 | BL-FND-03 | — | NFR-07 |
| BL-FND-10 | `PlanDayCalculator` | `planDate`, `planDayStart` 구현 + `06` §2 test vector 전부 | P0 | S1 | BL-FND-07 | AC-17 | FR-24, NFR-09 |
| BL-FND-11 | JPA 기반 | `BaseTimeEntity`, JPA Auditing, 애플리케이션 생성 UUID + `Persistable#isNew`, Hibernate jsonb·배열 매핑 검증 테스트 | P0 | S1 | BL-FND-06 | — | NFR-07 |
| BL-FND-12 | Cursor pagination | `CursorCodec`(`(sortKey, id)` ↔ base64url), `CursorPage<T>`, 잘못된 cursor 400 `INVALID_CURSOR` | P0 | S1 | BL-FND-08 | — | NFR-02 |
| BL-FND-13 | Idempotency | `IdempotencyService`(`03` §5.4) + 인증 `POST` 헤더 필수 검사(400 `IDEMPOTENCY_KEY_REQUIRED`, replan preview만 선택), canonical JSON hash, `Idempotent-Replayed` 헤더, 예외 시 record 삭제 | P0 | S1 | BL-FND-08, BL-FND-14 | AC-23 | NFR-04 |
| BL-FND-14 | Flyway V2·V3 | `V2__user_goal_skill.sql`, `V3__plan.sql`. 내용은 `database/schema.sql` 해당 부분과 동일 | P0 | S1 | BL-FND-06 | AC-01 | FR-03, FR-04 |
| BL-FND-15 | Flyway V4 | `V4__learning_today_review.sql` | P0 | S2 | BL-FND-14 | AC-02, AC-05 | FR-07, FR-11 |
| BL-FND-16 | Flyway V5 | `V5__training.sql` | P0 | S3 | BL-FND-15 | AC-04 | FR-09 |
| BL-FND-17 | Flyway V6 | `V6__coach.sql` | P1 | S4 | BL-FND-16 | AC-06 | FR-12 |
| BL-FND-18 | Flyway V7 | `V7__evidence_weekly.sql` | P1 | S5 | BL-FND-17 | AC-21 | FR-17, FR-18 |
| BL-FND-19 | Flyway V8 | `V8__requirement_radar.sql` | P1 | S7 | BL-FND-18 | AC-22 | FR-19 |
| BL-FND-20 | 구조화 로그·감사 로그 | prod JSON structured logging, `AuditLogger`(감사 이벤트 목록은 `07-security-and-privacy.md`), `userRef` = HMAC 앞 12 hex, `DEVPILOT_LOG_HASH_KEY` 없으면 prod 기동 실패 | P0 | S1 | BL-FND-07 | AC-08 | NFR-06 |
| BL-FND-21 | 프로필 API | `MeController`, `ProfileService`. `GET /me`(profile, onboardingCompleted, `aiStatus`, `aiUsage` — S3 전에는 provider `disabled`이므로 `aiStatus=DISABLED`, `aiUsage`는 호출 0·월 비용 0·예산값. record는 BL-AIP-16), `PATCH /me`(displayName, timezone IANA 검증, dayStartHour 0~6, weekday/weekendStudyMinutes 0~720, version) | P0 | S1 | BL-SEC-04, BL-FND-10, BL-AIP-16 | AC-17 | FR-24 |
| BL-FND-22 | OpenAPI 스냅샷 | springdoc(Swagger UI는 local만), `docs/api/openapi.yaml` 생성 task, `./gradlew openApiCheck`로 스냅샷 diff 발생 시 CI 실패. `ci.yml` `backend` job이 S0부터 실행하므로 S0 | P0 | S0 | BL-FND-03, BL-OPS-06 | — | NFR-07 |
| BL-FND-23 | 비동기 실행기·AI 작업 공통 | `AsyncConfig`(`aiTaskExecutor` core 2 / max 2 / queue 20), 비동기 AI task 템플릿(`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, `PENDING → RUNNING → COMPLETED/FAILED`, `AsyncFailureCode` 매핑, 최상위 catch 1곳), `common.job.OrphanAsyncTaskSweeper` port 정의 + `OrphanAsyncTaskJob`(`common.job`, 기동 1회 + 10분, `RUNNING` 10분 초과 → `FAILED(INTERRUPTED)`). job은 `List<OrphanAsyncTaskSweeper>`만 주입받고 도메인 모듈을 직접 의존하지 않는다(`03` §2.2 규칙 4, ARCH-01). 구현은 coach·training·review(Later)·evidence·radar가 각자 한다 | P0 | S3 | BL-FND-25, BL-FND-16 | AC-12 | NFR-03 |
| BL-FND-24 | `RetentionCleanupJob` | `common.job.RetentionCleanupTarget` port 정의 + `RetentionCleanupJob`(`common.job`): 만료 `idempotency_record` 삭제(자기 소유) + 주입받은 target 전부 호출 + 전날 AI 호출·비용·오류·job 실패 수 일일 요약 INFO 1줄(`dailySummary`). `ai_call_log` 180일 삭제는 `integration.ai.log.AiCallLogRetentionService`가 구현한다 — `common`은 도메인 모듈을 의존하지 않는다(`03` §2.2 규칙 4, ARCH-01) | P1 | S3 | BL-FND-25, BL-AIP-07 | — | NFR-05, NFR-06 |
| BL-FND-25 | 스케줄 job 공통 규약 | `@EnableScheduling`, test profile 스케줄러 비활성, job 시작·건수·소요시간 INFO 1줄, 실패 `JOB_FAILED` WARN, 사용자 단위 트랜잭션 분리 템플릿. v3에서 S3 → S2(S2로 당긴 `BL-GOL-14` `ProgressSnapshotJob`의 선행) | P0 | S2 | BL-FND-20 | — | NFR-06 |
| BL-FND-27 | Flyway V10 | `V10__track_notes_redo.sql`(`04` §10.1): ① `learning_goal_target_role_check`·`role_skill_target_target_role_check` 재생성(`JAVA_BACKEND_STARTER` 추가) ② `learning_task_task_type_check` 재생성(`REDO` 추가) ③ `learning_task.redo_source_task_id`·`redo_without_ai` + CHECK 3종(I-20·I-21) ④ 부분 인덱스 `idx_learning_task_redo_candidate` ⑤ `learning_event_event_type_check`(`REDO_COMPLETED`)·`learning_event_source_type_check`(`LEARNING_TASK`) 재생성 ⑥ `review_item_source_type_check`(`REDO_TASK`) 재생성 ⑦ `side_project_note` 생성 + CHECK `side_project_note_body_by_type`(I-22) + `idx_side_project_note_project`. **V1~V9는 고치지 않는다**(ADR-038). `database/schema.sql` 스냅샷 갱신 + `SchemaSnapshotConsistencyTest` | P0 | S3 | BL-FND-16 | AC-31, AC-32, AC-33 | FR-03, FR-28, FR-29 |
| BL-FND-26 | Job 지표 | `devpilot.jobs.runs{job,result}` Micrometer 지표 (외부 비노출). AI 지표는 BL-AIP-07 | P2 | S3 | BL-FND-25 | — | NFR-06 |

### 4.2 SEC — Security · Privacy

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-SEC-01 | ~~취소~~ SP-5 Supabase Auth 단독 운용 spike | 2026-09-18 취소(결정 A: 운영 인증도 devtoken). 내용은 BL-SEC-18(Later)이 흡수 | — | ~~S0~~ | — | — | — |
| BL-SEC-02 | ~~취소~~ Supabase 하드닝 | 2026-09-18 취소. 내용(GitHub OAuth provider, 가입 비활성, Data API 비활성, publishable key)은 BL-SEC-18(Later)이 흡수 | — | ~~S0~~ | — | — | — |
| BL-SEC-03 | JWT resource server | `SecurityConfig`: `devpilot.security.auth-mode`로 `JwtDecoder` 선택 — `devtoken`(기본·운영)은 앱이 가진 EC P-256 공개키로 `NimbusJwtDecoder.withJwkSource`(JWKS HTTP 조회 없음), `supabase`(Later)는 JWKS·`jws-algorithms`; issuer·`aud=authenticated`, clock skew 60초, STATELESS, CSRF 비활성, CORS local만, 인증 제외 경로(health, `calendar/*.ics`, devtoken 모드의 `/dev/**`, local api-docs), 401 `AUTHENTICATION_REQUIRED` ProblemDetail. `spring.security.oauth2.resourceserver.jwt.*`는 supabase 모드에서만 쓰고 `${SUPABASE_URL:}` 기본값을 비워 기동이 막히지 않게 한다 | P0 | S0 | BL-FND-03 | AC-08, AC-25 | FR-01 |
| BL-SEC-04 | 사용자 프로비저닝·allowlist | `UserProvisioningService`(`03` §4.3: ON CONFLICT, allowed-emails/subjects, 요청마다 재확인, `AUTH_USER_PROVISIONED`/`AUTH_USER_REJECTED`), `UserContextFilter`, `CurrentUser`, `CurrentUserArgumentResolver`, `DELETION_REQUESTED` 사용자 403 `FORBIDDEN` | P0 | S1 | BL-SEC-03, BL-FND-14, BL-FND-20 | AC-18 | FR-01 |
| BL-SEC-05 | 사용자 격리 테스트 하네스 | 사용자 소유 endpoint 목록 기반 파라미터화 테스트: 타 사용자 ID → 404, 목록 응답에 타 사용자 항목 0건, 상태 변경 없음. 단계마다 새 endpoint를 목록에 추가(S1 `/side-projects*`, S3 `/rubber-duck*`·`/side-projects/{id}/notes*` 포함) | P0 | S1 | BL-SEC-04 | AC-08 | NFR-04 |
| BL-SEC-06 | 로그 민감정보 차단 | Authorization 헤더·요청/응답 body·사용자 코드·AI 프롬프트·이메일 미기록 검증 테스트(로그 캡처), calendar 토큰 경로 `…/calendar/****.ics` 마스킹 | P0 | S1 | BL-FND-20 | AC-08 | NFR-05, NFR-06 |
| BL-SEC-07 | Caddy 보안 헤더 | HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options DENY`, `Permissions-Policy`, `-Server`, `/actuator/*`(health 제외) 404, CSP는 Report-Only로 시작 | P0 | S0 | BL-OPS-05 | AC-08 | NFR-04 |
| BL-SEC-08 | CSP 강제 전환 | 실사용 시작(M1 완료 = S3 완료) 후 1주 동안 Report-Only 위반 0건 확인 → 헤더를 `Content-Security-Policy`로 전환. 절차는 `07-security-and-privacy.md`. v3에서 S3 → S4(실사용 시작이 S3 완료로 옮겨졌다) | P1 | S4 | BL-SEC-07, BL-CLI-01 | AC-08 | NFR-04 |
| BL-SEC-09 | Secret scanning · S0 보안 도구 | GitHub secret scanning + push protection 활성(DEC-04), CI gitleaks job(DeepSeek 키 규칙 `sk-` + hex 32자 포함), third-party action commit SHA 고정 + `actions-pin-check`, Dependabot(gradle, pub, github-actions; 주 1회), `.env`·`config/*.json`(example 제외)·`DevPilot-ops/` ignore | P0 | S0 | BL-FND-02 | — | NFR-04 |
| BL-SEC-10 | 의존성·이미지 스캔 | Trivy fs(CI `security` job)·Trivy image(`release.yml`, 수정 가능한 CRITICAL/HIGH 실패), CodeQL(Java), Dockerfile base image digest 고정. S0 도구는 BL-SEC-09까지만 | P1 | S2 | BL-OPS-06, BL-OPS-07 | — | NFR-04 |
| BL-SEC-11 | Rate limit | `RateLimitFilter` + `TokenBucketRateLimiter`: JWT `sub`당 120 req/min, calendar 유효 토큰 60회/시간, 무효 토큰 IP당 30회/시간, 초과 429 `RATE_LIMITED` + `Retry-After` (`07-security-and-privacy.md`) | P1 | S2 | BL-SEC-04 | AC-08 | NFR-04 |
| BL-SEC-12 | 운영 계정 보안 | GitHub, DeepSeek 플랫폼, Tailscale, healthchecks.io 계정 2FA/passkey(지원하는 곳), 서버 SSH 키 로그인만, DeepSeek API 키 1개(prod·eval 공용 잔액, 결정 E)·선불 소액 유지, 접속 정보는 저장소 밖 `DevPilot-ops/`에만 (수동 체크리스트) | P0 | S0 | — | — | NFR-04 |
| BL-SEC-13 | 데이터 export | `account` 모듈 `AccountExportService` + `GET /me/export`: 사용자 소유 aggregate 전체 JSON, 타 사용자 데이터 0건, `calendar_token_hash`·`idempotency_record` 제외, `DATA_EXPORTED` 감사 로그. v3 테이블은 `sideProjects[]`(+`notes[]`, 마스킹본), `rubberDuckSessions[]`(+`turns[]`, 마스킹본)로 내보내고 `dailyPlans[].tasks[]`에 `redoSourceTaskId`·`redoWithoutAi`를 넣는다(`05` §3.3) | P1 | S6 | BL-FND-21 | AC-15 | FR-23, NFR-05 |
| BL-SEC-14 | 계정 삭제 요청 | `AccountDeletionService` + `DELETE /me`(`amr[].timestamp` 최댓값 — devtoken은 발급 시각 — 5분 초과 403 `RECENT_LOGIN_REQUIRED`, `DELETION_REQUESTED`, 캘린더 토큰 null, 202, `ACCOUNT_DELETION_REQUESTED` 감사 로그). 이후 `GET /me`·`DELETE /me` 외 403(BL-SEC-04). 실제 삭제 job은 BL-SEC-17(Later) | P1 | S1 | BL-SEC-04 | AC-15 | FR-23, NFR-05 |
| BL-SEC-15 | 하드닝 점검 | `07-security-and-privacy.md` 체크리스트 전체 점검, OWASP Top 10 대응표 갱신, AC-08·AC-18·AC-25 전체 재검증(AC-20은 Supabase 도입 시) | P1 | S6 | BL-SEC-05, BL-SEC-08 | AC-08, AC-18, AC-25 | NFR-04 |
| BL-SEC-16 | devtoken 모드 백엔드 | `DevTokenController` + `DevTokenService`(`auth-mode=devtoken`일 때만 bean): EC P-256 키(`DEVPILOT_DEV_JWT_KEY` PEM, 비면 기동 시 생성 → 재기동 시 기존 토큰 무효, prod는 필수), `POST /api/v1/dev/token {email}` → 200 `{accessToken, expiresAt}`(permitAll, allowlist 검사, 불허 403 `USER_NOT_ALLOWED`, IK 불필요), `GET /api/v1/dev/jwks.json`(permitAll). JWT `iss=${APP_BASE_URL}/dev`, `aud=authenticated`, `sub=uuid v5(email)`, `email`, `amr=[{method:"devtoken",timestamp}]`, ttl 720h. `JwtDecoder`는 같은 키의 공개키로 직접 구성. test profile은 `TestJwksServer` 대신 이 키를 공유(`09` §6.2). **비공개 네트워크 배포 전용** — tailnet 밖 노출 없음이 전제이고(`07` §3), 공개 노출 전에 `auth-mode=supabase`로 전환한다(`BL-SEC-18`, `03` §4.2). prod + devtoken이면 기동 시 WARN 1줄 | P0 | S0 | BL-SEC-03 | AC-25, AC-18 | FR-01, NFR-04 |
| BL-SEC-17 | 계정 삭제 job | `AccountDeletionJob`(5분 주기, `DELETION_REQUESTED` 사용자 cascade 삭제, `ai_call_log.user_id=null`, `ACCOUNT_DELETION_COMPLETED` 감사 로그). 그 전까지는 `account-deletion` runbook(allowlist 제거 + 수동 SQL) | P3 | Later | BL-SEC-14, BL-FND-25 | AC-15 | FR-23, NFR-05 |
| BL-SEC-18 | 공개 인증 도입 | **공개 노출의 필수 선행 조건.** `devtoken`의 취약점은 JWT가 아니라 "이메일만 입력하면 발급"이라는 **신원 확인 부재**이므로 발급 직전 단계만 바꾼다. **후보 (a) GitHub OAuth 직접(권장)**: backend가 `spring-boot-starter-oauth2-client`로 GitHub 로그인을 처리 → 이메일 확인 → allowlist 검사 → **현재의 EC 키 JWT 발급·검증 경로를 그대로 재사용**. 외부 인증 서비스 의존 없음, `DevTokenService`의 서명·검증 코드 재사용, Flutter는 로그인 버튼만 교체. **후보 (b) Supabase Auth**: `auth-mode=supabase` 활성 — Supabase 프로젝트·GitHub OAuth provider·가입 비활성·Data API 비활성(DEC-15)·publishable key만 클라이언트 사용, `SUPABASE_URL`·`SUPABASE_JWT_ALGORITHM`, JWKS 검증 경로, Flutter `AUTH_MODE=supabase`, `account-deletion` runbook에 Auth 사용자 수동 삭제, AC-20 S2·S3, 구 SP-5를 spike로 재실행. BL-SEC-01·02 흡수. 착수 시점은 Sprint가 아니라 공개 전환 게이트가 정한다(`11` §3.10) | P0 | 공개 배포 전 | BL-SEC-03, BL-SEC-16 | AC-20 | FR-01, NFR-04 |

### 4.3 OPS — Deploy · Ops

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-OPS-01 | SP-2 서버 PG16 + 원격 Docker Testcontainers | 서버 PostgreSQL 16(`devpilot` DB/role, TLS 없음)에 전체 migration 적용, `dev-docker-tunnel.ps1`(SSH 소켓 포워딩, `DOCKER_HOST`·`TESTCONTAINERS_HOST_OVERRIDE`)로 Testcontainers `postgres:16` 실행 — **2026-09-18 검증 완료**. 남은 것: Hikari 동시 20요청 확인, DO 블록 no-op 확인(BL-FND-06). 절차는 `11-development-roadmap.md` §4 SP-2, `18` §3 | P0 | S0 | — | — | NFR-03 |
| BL-OPS-02 | SP-3 amd64 이미지 → GHCR → 서버 pull + Tailscale HTTPS | `ubuntu-24.04` runner에서 linux/amd64 이미지 빌드 → GHCR → 서버 `deploy.sh` pull → Caddy `127.0.0.1:18080`(TLS 없음) → `tailscale serve --bg --https=443` → tailnet 안 유효 인증서 접속, 자동 롤백, 메모리(`-Xmx1g`, `mem_limit 1.5g`) 확인. 절차는 `11-development-roadmap.md` §4 SP-3 | P0 | S0 | BL-OPS-04 | — | NFR-10 |
| BL-OPS-03 | ~~취소~~ 도메인·DNS | 2026-09-18 취소(DEC-07: 도메인 미사용, Tailscale MagicDNS 호스트명 `https://<tailnet-host>` 사용) | — | ~~S0~~ | — | — | — |
| BL-OPS-04 | 서버 준비 `prepare-server.sh` | 기존 자체 서버(Rocky 9.8 x86_64, 2 vCPU/8GB 공용, Docker 29.6.1, Tailscale 1.98.8)에서 root 1회 멱등 실행: 전제 조건 확인(docker, tailscale, 18080 비어 있음) → `deploy` 사용자(+docker 그룹) → `/opt/devpilot/{backups,web}` + `api.env`·`ops.env` 템플릿(0600) → systemd 유닛(`devpilot-backup`, `devpilot-healthping`) 설치·enable → 안내 출력. OS 설정(daemon.json, podman, sshd, firewalld, dnf-automatic, `tailscale up`)은 건드리지 않는다(공용 서버). 구 `bootstrap-vm.sh` 삭제. 사용자 직접 작업: tailnet MagicDNS + HTTPS Certificates 활성화, `tailscale serve` 1회 | P0 | S0 | — | — | NFR-03 |
| BL-OPS-05 | 이미지·compose·Caddy | `Dockerfile`(temurin 25 JRE, 현재 빌드는 amd64 — 공개 전환 시 arm64 추가, `11` §3.10; 비root), `compose.prod.yml`(api: publish 없음, `JAVA_TOOL_OPTIONS=-Xmx1g`, `mem_limit 1.5g`, read_only, no-new-privileges; caddy: `127.0.0.1:18080:80`만 publish, `mem_limit 128m`; ACME·80/443·443/udp·certs 볼륨·`DOMAIN`·`SUPABASE_HOST` 없음), `Caddyfile`(TLS 없음, 보안 헤더, 정적 파일 `/srv/web` no-cache·SPA rewrite, `/api/*`·`/actuator/health` → `api:8080`), `/opt/devpilot/api.env` 권한 600 | P0 | S0 | BL-OPS-04, BL-FND-03 | — | NFR-10 |
| BL-OPS-06 | CI `ci.yml` | job 4개 = branch protection 필수 check(`main`·`developer` 둘 다) `security`(actions SHA 고정 검사, gitleaks — Trivy fs는 S2 BL-SEC-10), `backend`(`spotlessCheck check`, `openApiCheck`), `app`(format, analyze, `app/tool/check_identifiers.sh`, test, `build web`), `content`(`python3 content/tools/validate_content.py`). 트리거: `main`·`developer` push + 두 브랜치 대상 PR | P0 | S0 | BL-FND-04, BL-CLI-02, BL-OPS-21 | — | NFR-07 |
| BL-OPS-07 | CD `release.yml` + 수동 `deploy.sh` | 태그 `v0.<sprint>.<patch>` push → `ubuntu-24.04` runner → Flutter web 빌드(릴리스 아티팩트 zip) → 백엔드 이미지 linux/amd64(현재 서버) → GHCR `ghcr.io/<owner>/devpilot-api:<tag>`(Trivy image는 S2). 공개 전환 시 `linux/amd64,linux/arm64` 멀티아치로 바꾼다(OCI Always Free는 Ampere arm64, `11` §3.10). 배포는 로컬에서 `ssh <server>` → `sudo -u deploy /opt/devpilot/deploy.sh v0.x.y`(web zip → `/opt/devpilot/web`, GHCR pull, `docker compose -f /opt/devpilot/compose.prod.yml up -d`, `curl http://127.0.0.1:18080/actuator/health` 60초, 실패 시 이전 태그 롤백) → `infra/scripts/smoke-headers.sh`(서버 안 `127.0.0.1:18080` 기준). 구 `deploy.yml` 삭제. Actions → tailnet 자동 배포는 BL-OPS-22(Later) | P0 | S0 | BL-OPS-05 | AC-08 | NFR-10 |
| BL-OPS-08 | ~~취소~~ Tailscale 배포 경로 | 2026-09-18 취소(결정 D: CI OAuth client·`tag:ci` ACL·Tailscale SSH는 Later BL-OPS-22). 운영자 접속은 이미 Tailscale, 공개 SSH 없음 | — | ~~S0~~ | — | — | — |
| BL-OPS-09 | 로컬 개발 환경 | `local` profile은 `DATABASE_URL/USERNAME/PASSWORD` 필수(기본값 없음, 서버 PG16을 Tailscale 경유로 사용), `infra/scripts/dev-docker-tunnel.ps1`(서버 Docker 소켓 포워딩 — Docker Desktop 불필요), `infra/compose.dev.yml`(오프라인 대체, `postgres:16`, 포트 55432), `.env` import, Flutter `config/local.example.json`(`AUTH_MODE=dev`). 절차는 `18-project-setup-and-local-dev.md` | P0 | S0 | BL-FND-03 | — | NFR-07 |
| BL-OPS-10 | Runbook: 배포·롤백 | 태그 → `release.yml` 확인 → `deploy.sh` 실행, 수동 롤백 명령(`deploy.sh <이전 태그>`), migration 포함 배포 전 `backup.sh` 1회 실행 (`10-deployment-and-operations.md`) | P0 | S0 | BL-OPS-07 | — | NFR-03 |
| BL-OPS-11 | 백업 자동화 | `infra/scripts/backup.sh`(`pg_dump -Fc` → `/opt/devpilot/backups/devpilot-<UTC ts>.dump`, 7일 보관), systemd `devpilot-backup.timer` 매일 19:00 UTC, 로컬 PC `infra/scripts/pull-backup.ps1` 주 1회 scp pull → `%USERPROFILE%\DevPilot-backups\`(8주 보관), `db-restore` 절차(`pg_restore --clean --if-exists --no-owner -d devpilot`)로 로컬 복구 1회 확인. 오브젝트 스토리지·rclone·age 없음(tailnet 내부). 실사용 시작(M1 완료 = S3 완료) 전에 활성 | P0 | S2 | BL-OPS-01, BL-OPS-04 | — | NFR-03, NFR-05 |
| BL-OPS-12 | Runbook: 서버·DB 복구 | `server-restore`(서버 재설치 시 `prepare-server.sh` → `tailscale serve` → `api.env` 복원 → `deploy.sh` → `db-restore`), `db-restore`(로컬 사본 또는 서버 `backups/`에서 `pg_restore`) | P0 | S2 | BL-OPS-11 | — | NFR-03 |
| BL-OPS-13 | 헬스 모니터(healthchecks.io) | `infra/scripts/healthping.sh` + `devpilot-healthping.timer`(5분): `http://127.0.0.1:18080/actuator/health` 200이면 `HEALTHCHECKS_PING_URL`(`/opt/devpilot/ops.env`) GET, 실패면 `${HEALTHCHECKS_PING_URL}/fail`. healthchecks.io 누락·실패 알림 이메일. 외부 uptime 서비스 없음(tailnet 전용이라 외부에서 닿지 않음) | P1 | S2 | BL-OPS-04 | — | NFR-03 |
| BL-OPS-14 | ~~취소~~ 무료 플랜 운영 점검 | 2026-09-18 취소(Supabase·OCI 미사용). 공용 서버 자원 점검은 BL-OPS-16 `host-check.sh` | — | ~~S2~~ | — | — | — |
| BL-OPS-15 | Runbook: DeepSeek 키 유출 | `incident-api-key-leak`: platform.deepseek.com에서 키 삭제 → 새 키 발급 → `api.env`·GitHub `ai-eval` secret 교체 → `ai_call_log` 대비 콘솔 사용량 확인(잔액이 소액이라 피해 상한) → git 이력 조치 판단. prod AI 활성화 전 완료 | P0 | S3 | BL-AIP-15 | AC-13 | NFR-04 |
| BL-OPS-16 | 백업·호스트 운영 점검 | healthchecks.io 알림 실제 수신 확인, 서버 `backups/` 7일·로컬 8주 보관 개수 확인, `pull-backup.ps1` 주 1회 실행 기록. 호스트 점검: `infra/scripts/host-check.sh`(디스크·컨테이너 상태·`tailscale serve status`; 메모리 <20% 규칙·127.0.0.1:443 검사 없음) (`10-deployment-and-operations.md` §11) | P1 | S6 | BL-OPS-11, BL-OPS-13 | — | NFR-03, NFR-05 |
| BL-OPS-17 | 복구 리허설 | 로컬 PC 사본을 `compose.dev.yml` PostgreSQL 16에 `pg_restore` → 테이블별 행 수·최신 `learning_event.occurred_at` 비교 → 기록표. 이후 월 1회 | P0 | S6 | BL-OPS-11 | — | NFR-03 |
| BL-OPS-18 | Runbook: 키 교체·계정 삭제 | `secret-rotation`(DB 비밀번호, DeepSeek key, devtoken 서명 키 `DEVPILOT_DEV_JWT_KEY` — 교체 시 전체 재로그인, `DEVPILOT_LOG_HASH_KEY`), `account-deletion`(allowlist 제거 + 재기동 + 수동 SQL 삭제; Supabase 도입 시 Auth 사용자 수동 삭제 추가), `db-restore` 리허설 결과 반영 | P1 | S6 | BL-SEC-14, BL-OPS-17 | AC-15 | NFR-05 |
| BL-OPS-19 | 성능 스모크 | prod에서 non-AI 주요 API(`GET /today`, `GET /reviews/due`, `GET /plans/active`, `GET /dashboard`) 각 50회 → p95 < 1000ms 확인 | P2 | S2 | BL-TDY-08, BL-MEM-05 | — | NFR-02 |
| BL-OPS-20 | 데모 모드 | `demo` profile: 가상 사용자·4주 샘플 데이터, `FakeAiProvider`, 쓰기 요청 차단 | P3 | Later | — | — | — |
| BL-OPS-21 | git 전략·developer 보호·CI 트리거 | 브랜치 `feature/BL-<EPIC>-<nn>-<슬러그>`·`fix/`·`chore/`·`docs/` → `developer`(squash merge) → `main`(PR + merge commit), 태그 `v0.<sprint>.<patch>`는 `main`에서만, Conventional Commits(영문), PR 제목에 BL ID. GitHub branch protection: `main`·`developer` 둘 다 PR 필수 + 필수 check 4개 + 직접 push 금지. `.github/pull_request_template.md`(`16` §3), `ci.yml` 트리거 두 브랜치 | P0 | S0 | BL-FND-02 | — | NFR-07 |
| BL-OPS-22 | Actions → tailnet 자동 배포 | Tailscale OAuth client + ACL 태그 + 노드 태깅 + Tailscale SSH로 `release.yml`이 `deploy.sh`를 직접 실행. 수동 배포가 월 4회를 넘거나 실수가 생기면 착수 | P3 | Later | BL-OPS-07 | — | NFR-10 |

### 4.4 CLI — Flutter Client

화면 ID·라우트·상태 UI·문구는 `02-user-scenarios-and-ux.md`가 기준이다.

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-CLI-01 | SP-1 Flutter Web 한글 입력 spike | 한글 IME 조합·커서·붙여넣기, 코드 붙여넣기, 폰트 번들, 첫 로드 시간 확인. 절차는 `11-development-roadmap.md` §4 SP-1 | P0 | S0 | — | AC-10 | NFR-08 |
| BL-CLI-02 | Flutter bootstrap | FVM 버전 고정, `flutter_riverpod`, `go_router`, `dio`, `freezed`/`json_serializable`, ARB l10n(ko), 한글 폰트 번들, `--dart-define=AUTH_MODE=dev\|supabase`(기본 `dev`; `supabase_flutter`는 BL-SEC-18 — 공개 전환 게이트), lint 경고 0건 | P0 | S0 | BL-CLI-01 | — | NFR-08 |
| BL-CLI-03 | Walking skeleton 화면 · dev 로그인 | SCR-LOGIN dev 모드(이메일 입력 1칸 + 로그인 버튼 → `POST /api/v1/dev/token` → 토큰을 메모리 + `sessionStorage`에 보관 → `GET /me`; 403 → SCR-NOT-ALLOWED; 초대 시 1회 DeepSeek 안내 문구), `AuthInterceptor`(Bearer, 401이면 토큰 삭제 → `/login?reason=expired`), SCR-NOT-FOUND, 로그인 후 `GET /api/v1/me` 결과 표시, widget test 1개(`login_screen_test.dart`). SCR-AUTH-CALLBACK·GitHub 버튼은 supabase 모드(Later) | P0 | S0 | BL-CLI-02, BL-SEC-16 | AC-25 | FR-01 |
| BL-CLI-04 | 앱 셸 | go_router redirect 규칙(세션·allowlist·온보딩), SCR-NOT-ALLOWED, 반응형 내비(모바일 하단 탭 / 데스크톱 rail), 테마 토큰, 360px 폭 대응 | P0 | S1 | BL-CLI-03 | AC-10, AC-18 | NFR-08 |
| BL-CLI-05 | API 클라이언트 | dio interceptor: Bearer, `Idempotency-Key`(사용자 행동당 UUID v4, 네트워크 재시도는 같은 키), `X-Trace-Id`, Problem Details `code` 분기, 알 수 없는 enum → `unknown`. OpenAPI 스냅샷 기준 수기 freezed 모델(DEC-12) | P0 | S1 | BL-CLI-03, BL-FND-22 | AC-23 | NFR-07 |
| BL-CLI-06 | 공통 상태 UI | loading skeleton, empty(다음 행동 1개), error(`detail` + 문제 신고 영역의 traceId), 409 `CONCURRENT_MODIFICATION` 새로고침 안내 | P0 | S1 | BL-CLI-05 | AC-08 | NFR-08 |
| BL-CLI-07 | 온보딩 화면 | SCR-ONBOARDING(`02-user-scenarios-and-ux.md`): 목표 → 시간·하루 시작 시각 → **3단계 진단/자기평가 선택**(짧은 진단 = 카테고리당 1문제·최대 5문제, `runDiagnostic = true`·`selfAssessments = []` / 진단 건너뛰기 = 카테고리 자기평가 13개 중 1개 이상) → **4단계 사이드 프로젝트 등록**(기본 이름 "주문 시스템"을 클라이언트가 채우고 이름만 바꿀 수 있다, 건너뛰기 → `sideProject = null` — SP-1) → 계획 확인. 진단 풀이는 `suggestedDiagnostics`로 SCR-DIAGNOSTICS(BL-CLI-23, S3)에 이어진다 — S1~S2 빌드는 `suggestedDiagnostics = []` | P0 | S1 | BL-CLI-04, BL-GOL-06 | AC-11, AC-27 | FR-02, FR-24, FR-26 |
| BL-CLI-08 | Plan 화면 | SCR-PLAN(타임라인, milestone status/description/순서 PATCH), SCR-LEARNING-GOAL, SCR-PLAN-HISTORY, SCR-PLAN-VERSION | P0 | S1 | BL-GOL-01, BL-GOL-02, BL-GOL-04 | AC-01 | FR-03, FR-04 |
| BL-CLI-09 | Replan 화면 (저장) | SCR-REPLAN: milestone 추가·삭제·날짜·skill 편집 → 새 버전 저장, milestone id mapping 반영 | P0 | S1 | BL-GOL-05 | AC-01, AC-24 | FR-04 |
| BL-CLI-10 | Skill 화면 | SCR-SKILL-TREE(카테고리 트리), SCR-SKILL-DETAIL(축별 evidence·planning 레벨 막대 + 목표선). 이력은 BL-CLI-22 | P1 | S1 | BL-SKL-01, BL-SKL-02 | AC-09 | FR-06 |
| BL-CLI-11 | Settings 기본 | SCR-SETTINGS: 표시 이름, timezone, 하루 시작 시각(변경 영향 안내), 평일·주말 학습 시간 | P0 | S1 | BL-FND-21 | AC-17 | FR-24 |
| BL-CLI-12 | Today 화면 | SCR-TODAY: 시간 칩·컨디션 → 생성, main task 1개 + reasons + REVIEW task, 시작·완료(세션 `actualMinutes`, 회고)·건너뛰기·미루기, 재생성 409 처리, 복귀 모드 문구 | P0 | S2 | BL-TDY-07, BL-TDY-08, BL-TDY-09, BL-TDY-10 | AC-02, AC-17 | FR-07, FR-08, FR-21 |
| BL-CLI-13 | Review 화면 | SCR-REVIEW-HOME, SCR-REVIEW-SESSION: 카드 1장씩, 답 입력, "힌트 보기"(rubric 첫 항목, `CONCEPT_HINT`)·"정답 먼저 보기"(`FULL_EXAMPLE`), 답 확인 → 자기평가 4버튼, `adjustedBy`·다음 due 표시 | P0 | S2 | BL-MEM-05, BL-MEM-06 | AC-05, AC-10 | FR-11, NFR-08 |
| BL-CLI-14 | 복습 카드 관리 화면 | SCR-REVIEW-ITEMS(skill·status 필터), SCR-REVIEW-ITEM-EDIT(수동 생성·수정·일시중지·보관). 2026-09-18 S2 → S3(수동 카드 CRUD 범위 축소) | P1 | S3 | BL-MEM-07 | AC-05 | FR-11 |
| BL-CLI-15 | Dashboard 최소 화면 | SCR-DASHBOARD: 오늘 상태, due 수, 최근 7 plan-day 학습 시간, 현재 milestone | P0 | S2 | BL-TDY-11 | AC-02 | FR-16 |
| BL-CLI-16 | More 화면 | SCR-MORE(모바일 폭 전용 진입 목록, 폭 ≥ 600이면 `/today` redirect) | P0 | S2 | BL-CLI-04 | AC-10 | NFR-08 |
| BL-CLI-17 | PWA | manifest, 아이콘, 홈 화면 추가 안내, 배포 후 새 버전 반영 확인(no-cache) | P0 | S2 | BL-OPS-05 | AC-10 | NFR-08 |
| BL-CLI-18 | 캘린더 구독 설정 | SCR-SETTINGS 캘린더 구독 URL 발급·재발급(평문 URL은 발급 응답에서만 표시). 2026-09-18 S2 → S3, v3에서 S3 → S5(M2) | P1 | S5 | BL-TDY-13 | — | FR-20 |
| BL-CLI-19 | UI 통합 테스트 | Flutter `integration_test`: 온보딩 → Today 생성 1개 흐름 | P1 | S2 | BL-CLI-07, BL-CLI-12 | AC-02, AC-11 | NFR-07 |
| BL-CLI-20 | AI 공통 UI | AI unavailable 배너, `aiStatus`·`aiUsage` 표시(SCR-SETTINGS 포함), `DISABLED`·`BALANCE_EXHAUSTED`("AI 잔액 소진" 배지·사유 문구) 시 AI 진입 버튼 비활성, `BUDGET_WARNING` 안내, 비동기 진행 표시(2초 polling, 최대 3분). enum 미지 값은 `unknown` | P0 | S3 | BL-AIP-10, BL-AIP-16 | AC-12, AC-13 | FR-22, NFR-03 |
| BL-CLI-21 | Training 화면 | SCR-TRAINING-LIST(목록·생성 요청 polling), SCR-CHALLENGE-DETAIL, SCR-TRAINING-ATTEMPT(self-explanation/건너뛰기, Hint Ladder 다음 단계 버튼, `PSEUDOCODE` 이상 확인 대화상자, 제출·평가 polling·retry, rubric 결과) | P0 | S3 | BL-TRN-02, BL-TRN-05, BL-TRN-08, BL-TRN-09 | AC-04, AC-16 | FR-09, FR-10 |
| BL-CLI-22 | Skill 이력 | SCR-SKILL-DETAIL 레벨 변경 이력(rule_code, 근거 이벤트 요약) | P1 | S3 | BL-SKL-06 | AC-09 | FR-06 |
| BL-CLI-23 | 진단 제안 화면 | SCR-DIAGNOSTICS(건너뛰기 가능 카드, 시작 시 attempt 생성). 온보딩 3단계에서 진단을 고른 사용자의 시작점이다(진단 결과가 `user_skill_state`의 시작점, `06` §7.4) — v3에서 P1 → P0 | P0 | S3 | BL-TRN-13 | AC-11 | FR-02, FR-15 |
| BL-CLI-24 | Coach 화면 | SCR-COACH-LIST, SCR-COACH-NEW(붙여넣기, 동의 체크 + DeepSeek 동의 문구 "코드는 AI 공급자 DeepSeek(중국 소재)로 전송되며, 입력을 학습에 쓰지 않는다는 약정이 없습니다. 회사 코드를 붙여넣지 마세요.", self-review, `maskedSecretCount`), SCR-COACH-DETAIL(polling·retry — `AI_REFUSED`는 retry 불가 안내, finding 배지: type·category·verification·confidence 텍스트, 응답·피드백, hint, 해결/해당 없음, 완료, 원문 삭제) | P1 | S4 | BL-COA-02, BL-COA-04, BL-COA-06, BL-COA-09, BL-COA-10 | AC-06, AC-07, AC-14, AC-19 | FR-12, FR-14 |
| BL-CLI-25 | Replan 미리보기 | SCR-REPLAN: 미리보기(risk, `ratioBp`, defer·축소 제안 체크, 여유가 있으면 확장 제안 — 복원 `RESTORE_DEFERRED` → `restoredDeferrals`·목표 상향 `RAISE_TARGET` → `acceptedTargetRaises` 체크, `riskAfterSuggestions`) → 새 버전 저장, SCR-PLAN budget·risk 표시. 축소와 확장은 한 응답에 함께 나오지 않는다(`06` §4.4). v3에서 S5 → S2 | P0 | S2 | BL-GOL-11, BL-GOL-12, BL-GOL-13, BL-GOL-17 | AC-03, AC-30 | FR-05 |
| BL-CLI-26 | Dashboard 완성·Weekly | SCR-DASHBOARD(risk 추세, skill 카테고리 요약, 약한 thinking 축 Top 3), SCR-WEEKLY-LIST, SCR-WEEKLY-DETAIL(지표, 회고 작성) | P1 | S5 | BL-TDY-12, BL-EVD-03, BL-EVD-04 | AC-19, AC-21 | FR-13, FR-16, FR-17 |
| BL-CLI-27 | Evidence 화면 | SCR-EVIDENCE-LIST, SCR-EVIDENCE-DETAIL(수동 작성, AI 초안 polling, 편집, accept/reject, Markdown export 다운로드) | P1 | S6 | BL-EVD-05, BL-EVD-06, BL-EVD-07, BL-EVD-08 | AC-21 | FR-18 |
| BL-CLI-28 | Export·계정 삭제 화면 | SCR-SETTINGS export 다운로드, SCR-ACCOUNT-DELETE(403 `RECENT_LOGIN_REQUIRED` → 재로그인(dev 모드: `/api/v1/dev/token` 재발급으로 새 `amr` timestamp) → 자동 재요청, 202 후 로그아웃) | P1 | S6 | BL-SEC-13, BL-SEC-14 | AC-15 | FR-23 |
| BL-CLI-29 | 로드맵 비교 화면 | SCR-REQUIREMENTS-LIST, SCR-REQUIREMENT-NEW, SCR-REQUIREMENT-DETAIL(분류별 개수·목록만, 확률·점수·퍼센트 없음) | P1 | S7 | BL-REQ-02, BL-REQ-03 | AC-22 | FR-19 |
| BL-CLI-35 | 프로젝트 기록 화면 | SCR-PROJECT-DETAIL(`/projects/:sideProjectId`)과 SCR-PROJECT-NOTE-EDIT(`…/notes/new?noteType=`·`…/notes/:noteId`) — `02` §3.16: 프로젝트 요약 + 기록 목록(유형 필터 3개, `occurredOn` DESC 서버 정렬, 스크롤 페이지네이션), 카드(유형 배지·날짜·본문 첫 항목 2줄·skill 칩), `⋮` 수정·삭제, "+ 결정 기록"·"+ 장애 기록". 편집 화면은 **유형 입력이 없고**(PN-2) 유형별 항목 3개·4개가 전부 필수, `occurredOn` 날짜 선택기는 오늘 이하, skill 선택은 선택 사항, `422` 인라인·`409` 재조회. SCR-PROJECTS 카드 탭 → 상세, 삭제 확인 문구에 "기록도 함께 지워져요", SCR-TODAY `PROJECT_TASK` 카드에 "이 프로젝트 기록" 버튼 | P1 | S3 | BL-PRJ-02, BL-CLI-32, BL-CLI-05, BL-CLI-06 | AC-33 | FR-29 |
| BL-CLI-36 | 온보딩 학습 트랙 선택 | SCR-ONBOARDING 1단계에 라디오 2개(`JAVA_BACKEND` 기본 / `JAVA_BACKEND_STARTER`), 각 항목에 이름·한 줄 설명·필수 skill 수(`GET /skills/tree?role=`의 MUST 수, 실패하면 숫자 생략), `onboarding.goal.track.locked` 안내, 트랙을 바꾸면 3단계 입력 초기화 + 토스트. 요청 body `learningGoal.targetRole`. SCR-LEARNING-GOAL은 트랙을 **읽기 전용**으로 보이고 변경 입력을 두지 않는다. ARB 키는 `02` §3.4 | P1 | S3 | BL-GOL-18, BL-CLI-07 | AC-32, AC-11 | FR-03, FR-02 |
| BL-CLI-37 | 재현 과제 화면 | SCR-TODAY의 `REDO` 카드(`02` §3.5): 배지 "AI 없이 재현", 잠금 줄 `today.redo.locked`(`PLANNED`·`IN_PROGRESS` 모두), 보조 "원본 과제 보기", `IN_PROGRESS`에 러버덕 버튼 없음. 완료 시트의 필수 질문 2버튼(`today.redo.question`, 고르기 전 "완료 기록" 비활성) → `PATCH …` `{status: COMPLETED, redoWithoutAi}`, "아니요" 후 토스트 + "복습하러 가기". `409 AI_ASSIST_LOCKED_FOR_REDO` 처리(`02` §5.1): SCR-TRAINING-ATTEMPT 힌트 버튼 전 단계·SCR-RUBBER-DUCK 시작 버튼 비활성 + `today.redo.lockedElsewhere` 1줄 + "Today로 가기"(AI 불가 배너와 구분) | P1 | S4 | BL-TDY-17, BL-TDY-18, BL-CLI-12, BL-CLI-21, BL-CLI-33 | AC-31 | FR-28 |
| BL-CLI-30 | Android 앱 | Android 빌드·서명·배포 (DEC-18) | P3 | Later | — | — | — |
| BL-CLI-31 | 세션 완료 화면 즉시 진전 표시 | (검토 제안, 미결정) 세션 완료 직후 오늘 학습 시간·연속 일수·이번 주 완료 세션 수를 완료 시트에 바로 표시. 사용자 결정 후 착수 | P3 | Later | BL-CLI-12 | — | FR-08, FR-16 |
| BL-CLI-32 | 사이드 프로젝트 화면 | SCR-PROJECTS(목록·등록·수정 한 화면, `02-user-scenarios-and-ux.md`): `GET /side-projects?status=` 목록(`updatedAt` DESC, 상태 필터), 등록(name 필수, description·repoUrl·stack 선택, `URL` 오류 표시), 수정(상태 `ACTIVE`·`PAUSED`·`DONE` 포함, version, 409 `CONCURRENT_MODIFICATION` 새로고침 안내), 삭제 확인(과제·리뷰 기록은 남고 연결만 끊긴다). `repoUrl`은 텍스트 링크로만 보여 준다(서버는 fetch하지 않는다). `ACTIVE`가 여럿이면 planner는 가장 최근 수정한 하나를 쓴다는 안내(SP-3) | P1 | S1 | BL-PRJ-01, BL-CLI-04, BL-CLI-05, BL-CLI-06 | AC-27 | FR-26 |
| BL-CLI-33 | 러버덕 화면 | SCR-RUBBER-DUCK(`02-user-scenarios-and-ux.md`): 대상 요약(`targetTitle`)·skill → 설명 입력(2000자 카운터, 초과 시 전송 불가) → `POST /rubber-duck/{sessionId}/turns`(동기 최대 20초 대기 표시, 사용자 행동당 `Idempotency-Key` 1개, 실패 시 입력 유지·같은 키로 재시도) → AI 질문, 남은 턴(`remainingTurns`). `suggestHint = true`면 대상이 `CHALLENGE`일 때 그 attempt의 Hint Ladder로 넘어가는 버튼, 그 외 대상은 정리 안내. 턴 상한이면 입력을 닫고 정리(`complete`) → gaps(연결된 복습 카드)·confirmed·overallNote, `summarySkippedReason`이면 "대화는 저장됨" 안내, 중단(`abandon`). `aiStatus ∈ {DISABLED, BALANCE_EXHAUSTED}`면 시작 버튼 비활성 + 사유(BL-CLI-20), 지난 세션 조회는 가능. 진입: Today task·SCR-READ-CODE·복습 카드·challenge attempt·SCR-PROJECTS | P0 | S3 | BL-RDK-02, BL-CLI-05, BL-CLI-20 | AC-26 | FR-25 |
| BL-CLI-34 | 코드 읽기 화면 | SCR-READ-CODE(`02-user-scenarios-and-ux.md`): `GET /readings/{readingKey}`로 저장소 이름·`why`·라이선스(`UNSPECIFIED`면 읽기만·코드 복사 금지 안내), **`cloneHint`(복사 버튼)·`pinnedCommit`을 먼저** 보여 준다(RC-4 — 로컬에 없으면 clone부터), 파일 경로·줄 범위(`startLine`~`endLine`), `question`, `lookFor`, 예상 시간 → "러버덕으로 설명하기"(`POST /rubber-duck` `targetType = CODE_READING`, `targetId` = task id) → SCR-RUBBER-DUCK. **코드 본문은 보여 주지 않는다**(서버가 가져오지 않는다). task 완료는 `COMPLETED` 러버덕 세션이 있을 때만 성공한다(RC-1, 409 `INVALID_STATE_TRANSITION` 안내) | P0 | S3 | BL-TDY-16, BL-CLI-33, BL-CLI-12 | AC-28 | FR-27 |

### 4.5 GOL — Goal · Plan

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-GOL-01 | 학습 목표 API | `LearningGoal`, `LearningGoalFocusSkill`, `LearningGoalService`, `LearningGoalQueryService`. `GET /learning-goal`(없으면 404 `LEARNING_GOAL_NOT_FOUND`), `PUT /learning-goal`(version, 날짜 검증, 날짜 변경 시 활성 plan `replan_recommended = true` — application event로 전달) | P0 | S1 | BL-FND-14, BL-SKL-01 | AC-01 | FR-03 |
| BL-GOL-02 | Plan 조회 API | `PlanQueryService`. `GET /plans/active`(milestones + skillTargets + latest snapshot, 없으면 404 `PLAN_NOT_FOUND`), `GET /plans?cursor=`, `GET /plans/{planId}`(SUPERSEDED 포함) | P0 | S1 | BL-FND-14, BL-FND-12 | AC-01 | FR-04 |
| BL-GOL-03 | Plan 템플릿 생성 서비스 | `PlanCommandService.createFromTemplate`(온보딩 내부 호출 전용): 활성 plan 없을 때 템플릿으로 v1 생성(milestone 날짜 배치는 **`PlanTemplatePlacement`** — `19` §5 알고리즘·§5.4 test vector), 있으면 `ACTIVE_PLAN_EXISTS`. `role_skill_target` → `plan_skill_target` 복사(`ROLE_DEFAULT`). 공개 endpoint `POST /plans`는 MVP에서 호출 경로가 없어 Later(BL-GOL-16) | P0 | S1 | BL-GOL-02, BL-CNT-05 | AC-01 | FR-04 |
| BL-GOL-04 | Milestone in-place 수정 | `PATCH /plans/{planId}/milestones/{milestoneId}`: status/description/sortOrder/version만. 비활성 plan 409 `PLAN_NOT_ACTIVE` (`06` §11.1) | P0 | S1 | BL-GOL-02 | AC-01, AC-24 | FR-04 |
| BL-GOL-05 | Replan 최소판 | `ReplanService` + `POST /plans/{planId}/replan`: milestone 구조 변경, 기존 plan `SUPERSEDED` + **flush** 후 새 version INSERT, id mapping, `plan_skill_target` 복사. `acceptedDeferrals`/`acceptedTargetReductions`/`restoredDeferrals`/`acceptedTargetRaises`가 비어 있지 않으면 400 `VALIDATION_FAILED`(`VALUE_NOT_ALLOWED`, `05` §7.8). `PLAN_REPLANNED` 이벤트는 BL-GOL-07, snapshot은 BL-GOL-13 | P0 | S1 | BL-GOL-04 | AC-01, AC-24 | FR-04 |
| BL-GOL-06 | 온보딩 API | `OnboardingService` + `POST /onboarding`(201, 한 트랜잭션, `05` §4.1 1~11단계): 프로필·timezone·dayStartHour·학습 시간, 학습 목표, `runDiagnostic`(true = 진단 모드: `selfAssessments`가 비어 있지 않으면 `MUTUALLY_EXCLUSIVE`, `self_assessed_level` 전부 null / false = 자기평가 모드: 비어 있으면 `ONE_OF_REQUIRED`), **`SelfAssessmentPropagation`**(카테고리 최대 13개 → 활성 skill 전체 `user_skill_state`), plan v1(템플릿, BL-GOL-03), `sideProject`(있으면 `side_project` `ACTIVE` INSERT — `SideProjectService`, `repoUrl` `URL` 검사·fetch 없음, 기본 이름은 클라이언트가 채운다 / null이면 만들지 않고 planner가 `PROJECT_TASK`를 제안하지 않는다 — SP-1), 응답 `sideProject`, 409 `ONBOARDING_ALREADY_COMPLETED`, `OnboardingRequiredInterceptor`(409 `ONBOARDING_REQUIRED`). **S1은 8·9·11단계 생략**: 응답 `assignedSeedCardCount = 0`, `latestRiskLevel = null`, `suggestedDiagnostics = []`. seed 카드 복사는 BL-MEM-08(S2, 기동 backfill), snapshot upsert는 BL-GOL-13(S2), 진단 제안은 BL-TRN-13(S3)에서 연결 | P0 | S1 | BL-GOL-01, BL-GOL-03, BL-SKL-02, BL-CNT-05, BL-PRJ-01 | AC-11, AC-27 | FR-02, FR-26 |
| BL-GOL-07 | `PLAN_REPLANNED` 이벤트 연결 | V4 적용 후 replan에서 `LearningEventRecorder`로 `PLAN_REPLANNED` 기록 + 감사 로그 | P1 | S2 | BL-SKL-03, BL-GOL-05 | AC-01 | FR-04 |
| BL-GOL-08 | **`StudyBudgetCalculator`** | horizon, nominal, completion rate, effective (`06` §3, test vector 전부). v3에서 S5 → S2 — 사용자가 등록한 목표일의 역산이 첫 Today부터 우선순위에 반영된다 | P0 | S2 | BL-FND-09, BL-FND-10 | AC-03 | FR-05 |
| BL-GOL-09 | **`DeadlineRiskEvaluator`** | skill별 required minutes, risk level, `ratioBp` (`06` §4.1~4.3, test vector 전부). risk는 planner 입력(`RISK_HIGH_MUST`·`DEADLINE_RISK_MUST`)이라 Today와 같은 단계다. v3에서 S5 → S2 | P0 | S2 | BL-SKL-02 | AC-03 | FR-05 |
| BL-GOL-10 | **`ReplanSuggestionPolicy`** | 방향 분기(`06` §4.4 표 — risk ≥ HIGH면 축소, LOW이고 `ratioBp ≤ 7000`이면 확장, 그 외·`ratioBp = null`이면 제안 없음)와 축소 쪽: MUST 목표 축소를 먼저 `requiredMust ≤ effective`까지 → 그다음 SHOULD defer, `riskAfterSuggestions` (`06` §4.4 1~5단계, test vector 1·2). 확장 쪽(6단계)은 BL-GOL-17. v3에서 S5 → S2 | P0 | S2 | BL-GOL-09 | AC-03 | FR-05 |
| BL-GOL-11 | Budget API | `StudyBudgetService` + `GET /plans/active/budget`. completion 입력은 port `plan.application.StudyHistoryProvider`(plan 정의, `today` 모듈 구현: plan-day별 `available_minutes`와 COMPLETED 세션 `actual_minutes` 합)로 받는다. Today 생성(BL-TDY-07)도 같은 계산으로 요청 시점 risk를 구한다. v3에서 S5 → S2 | P0 | S2 | BL-GOL-08, BL-GOL-09, BL-TDY-10 | AC-03 | FR-05 |
| BL-GOL-12 | Replan preview API | `POST /plans/{planId}/replan/preview`: 저장 없음, IK 무시, risk + 제안 + `riskAfterSuggestions` (`05` §7.7). `expansionSuggestions`는 BL-GOL-17. v3에서 S5 → S2 | P0 | S2 | BL-GOL-10, BL-GOL-11 | AC-03 | FR-05 |
| BL-GOL-13 | Replan 완성 | `acceptedDeferrals`(`DEFERRED`), `acceptedTargetReductions`(`TARGET_REDUCED`), `restoredDeferrals`(defer 해제) 적용, 새 plan 기준 오늘 snapshot upsert. 온보딩 처리의 snapshot upsert(`05` §4.1 9단계)도 연결. `acceptedTargetRaises`는 BL-GOL-17. v3에서 S5 → S2 | P0 | S2 | BL-GOL-12 | AC-03, AC-24 | FR-05 |
| BL-GOL-14 | `ProgressSnapshotJob` | 매시 5분, 로컬 시각이 `dayStartHour`인 사용자만 budget/risk 계산 → `plan_progress_snapshot` upsert, 사용자 단위 트랜잭션. v3에서 S5 → S2 — S2부터 risk가 계산된다. Today의 `deadline_risk`는 생성 요청 시점에 계산하고(`05` §8.2), snapshot은 온보딩·replan 응답의 `latestRiskLevel`과 Dashboard 추세(BL-TDY-12)가 쓴다 | P0 | S2 | BL-GOL-11, BL-FND-25 | AC-03, AC-17 | FR-05 |
| BL-GOL-15 | 공휴일 반영 budget | nominal budget 계산에 공휴일 달력 반영 | P3 | Later | — | — | FR-05 |
| BL-GOL-16 | `POST /plans` endpoint | 활성 plan 없을 때 템플릿 plan 생성 공개 API(201 / 409 `ACTIVE_PLAN_EXISTS`, AC-24 S3). MVP에는 온보딩 외 호출 경로가 없어 Later(2026-09-18) | P3 | Later | BL-GOL-03 | AC-24 | FR-04 |
| BL-GOL-17 | 확장 제안 | **`ReplanSuggestionPolicy`** 6단계(`06` §4.4 6a~6d): risk LOW이고 `ratioBp ≤ 7000`일 때만. 복원 후보(`deferred = true`인 SHOULD/LATER, `practicalImportance DESC → code ASC`) → 목표 상향 후보(MUST, `deferred = false`, planning < target인 skill, skill당 축 하나 +1 — target < 5인 축 중 planning이 가장 낮은 축, 모든 축이 5면 건너뛰고 예산을 넘으면 멈춘다), 종료 조건 `expandedRatioBp ≤ 9000`, `riskAfterSuggestions`는 `requiredMust`만(복원분 제외). `06` §4.4 test vector 3·4. 축소·defer와 **배타**(한 응답에 함께 나오지 않는다, `ratioBp = null`이면 세 목록 모두 `[]`). preview 응답 `expansionSuggestions[]`(`ExpansionSuggestionView`, `ExpansionKind` `RESTORE_DEFERRED`·`RAISE_TARGET` — `04` §3, `05` §7.7). replan 확정 `acceptedTargetRaises`(`TargetRaiseInput`, `05` §7.8): skill 존재·plan 소속, `(skillCode, axis)` 유일, 같은 `(skillCode, axis)`의 `acceptedTargetReductions`·같은 skill의 `acceptedDeferrals`와 `MUTUALLY_EXCLUSIVE`, 현재 target < `newTarget` ≤ 5가 아니면 `TARGET_NOT_RAISED` → 적용 시 `adjustment = USER_EDITED`(축소와 함께 받은 skill은 `TARGET_REDUCED`, `06` §11.2 7단계). 자동 적용하지 않는다 | P0 | S2 | BL-GOL-10, BL-GOL-12, BL-GOL-13 | AC-30, AC-03 | FR-05 |
| BL-GOL-18 | 학습 트랙 2종 | `TargetRole`에 `JAVA_BACKEND_STARTER` 추가(`04` §3, migration은 BL-FND-27). `common.config.TrackDefaults` record + `devpilot.tracks.<트랙>`(`max-task-difficulty`, `read-code-min-knowledge`, `03` §9) 바인딩과 **`TargetRole` 값마다 항목이 있는지 기동 시 검사**(`goal.application`, 없으면 기동 실패). `POST /onboarding`의 `learningGoal.targetRole`로 role target·계획 템플릿·`user_skill_state` 대상 skill이 갈린다(`05` §4.1 5단계). `PUT /learning-goal`은 트랙 변경을 400 `VALUE_NOT_ALLOWED`로 막는다(`05` §5.2). `GET /skills/tree`의 `role` 기본값을 사용자의 트랙으로(`05` §6.1). `TaskProposalPolicy`가 `trackDefaults`를 입력으로 받는다(`06` §5.1·§5.3, vector T-6~T-9) — 난이도 상한과 RC-3 문턱만 바뀌고 다른 규칙은 그대로다 | P1 | S3 | BL-GOL-06, BL-FND-27, BL-CNT-17, BL-TDY-03 | AC-32, AC-01 | FR-03, FR-02 |

### 4.6 SKL — Skill

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-SKL-01 | Skill catalog API | `Skill`, `SkillPrerequisite`, `RoleSkillTarget` 매핑, `SkillCatalogQueryService`, `GET /skills/tree?role=JAVA_BACKEND` | P0 | S1 | BL-FND-14, BL-CNT-02 | AC-11 | FR-06 |
| BL-SKL-02 | Skill state API | `UserSkillState`, **`PlanningLevelPolicy`**(`06` §7.5, vector 13~14), `UserSkillStateQueryService`, `GET /skills/me`. | P0 | S1 | BL-SKL-01 | AC-09 | FR-06 |
| BL-SKL-03 | 학습 이벤트 기록 | `LearningEvent`, `LearningEventType`, payload record(`04` §6), `LearningEventRecorder`(dedupe_key 중복 시 기존 반환, `plan_date`), `LearningEventRecorded` 발행 | P0 | S2 | BL-FND-15 | AC-09 | FR-06 |
| BL-SKL-04 | **`SkillLevelRules`** | 상승·하락·진단 규칙(`06` §7.1~7.4, vector 1~12, 15). 입력은 60일 이벤트 payload만. 설명 증거에 `RUBBER_DUCK_COMPLETED`(`gapCount = 0`·`turns ≥ 3`, coverage 고정 `devpilot.rubberduck.evidence-coverage-bp` 7000 → E2·E3에만, 독립 = payload `hintDisclosed = false`, `06` §7.2)를 포함한다 — 이벤트 기록은 BL-RDK-02 | P0 | S3 | BL-SKL-03 | AC-09, AC-26 | FR-06 |
| BL-SKL-05 | `SkillStateUpdater` | 동기 `@EventListener`, 무효화 이벤트 제외, 하락 우선, 축당 1단계, 24시간 cooldown, `skill_state_change`, `evidence_count`, `last_practiced_at`, `self_assessment_active`. ArchUnit: 레벨 setter는 이 경로만(I-12). | P0 | S3 | BL-SKL-04 | AC-09 | FR-06 |
| BL-SKL-06 | Skill 이력 API | `GET /skills/{skillId}/history?cursor=` (`skill_state_change`, rule_code, 근거 이벤트 요약) | P1 | S3 | BL-SKL-05 | AC-09 | FR-06 |
| BL-SKL-08 | 독립 구현 증거에 재현 포함 | **`SkillLevelRules`**의 `I3_SOLVED_INDEPENDENT`를 "독립 구현 증거"(`06` §7.2 표) 기준으로 바꾼다: `CHALLENGE_EVALUATED` SOLVED_INDEPENDENTLY(`evidenceKey = CHALLENGE:{challengeId}`) **또는** `REDO_COMPLETED` `withoutAi = true`(`evidenceKey = REDO:{sourceTaskId}`), difficulty ≥ 2, 3개 이상, `evidenceKey` 2종 이상. `withoutAi = false`는 어떤 상승 규칙에도 쓰지 않는다. `I4`·`I5`는 challenge 전용 조건(`maxHintLevel`·`isTransfer`)이라 그대로 둔다. vector `06` §7.6 #16~17과 `09` §5.4 RD-V1~RD-V4 | P1 | S4 | BL-SKL-04, BL-TDY-18 | AC-31, AC-09 | FR-06, FR-28 |
| BL-SKL-07 | 하루 1회 skill 재평가 | (검토 제안, 미결정) `SkillStateUpdater`는 새 이벤트가 올 때만 재평가하므로 시간 경과로만 성립하는 하락 규칙(`06` §7.3)이 늦게 적용된다. `ProgressSnapshotJob`(plan-day 시작 시)에서 사용자 skill 전체를 1회 재평가하는 방식을 검토. 사용자 결정 후 착수 | P2 | S5 | BL-SKL-05, BL-GOL-14 | AC-09 | FR-06 |

### 4.7 TDY — Today

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-TDY-01 | Daily plan 도메인 | `DailyPlan`, `LearningTask` 매핑, 상태 전이 메서드(`04` §4.1, 위반 409 `INVALID_STATE_TRANSITION`), WIP unique index 위반 변환 | P0 | S2 | BL-FND-15 | AC-02 | FR-07 |
| BL-TDY-02 | **`PlannerScoring`** | factor, weight, modifier, 동점 처리(`06` §5.4~5.5, §5.7 vector 전부). | P0 | S2 | BL-FND-09 | AC-02 | FR-07 |
| BL-TDY-03 | **`TaskProposalPolicy`** | 후보 skill별 main task 제안과 제목 템플릿(`06` §5.3). S2에는 난이도 상한 5·RC-3 문턱 1을 상수로 두고, `BL-GOL-18`(S3)이 `trackDefaults`로 바꾼다. `PROJECT_TASK`는 `ACTIVE` 사이드 프로젝트가 있을 때만 제안하고(SP-1), `updated_at`이 가장 최근인 `ACTIVE` 하나(SP-3, `SideProjectQueryService`)를 생성 시점에 `learning_task.side_project_id`로 고정, 제목 `{프로젝트 이름}에 {skill.name} 적용하기`(SP-2), `TaskView.sideProjectId`(`05` §8.1). `READ_CODE` 분기(2번, RC-3)는 BL-TDY-16(S3) — S2 빌드는 reading 후보를 빈 목록으로 둔다(CHALLENGE의 BL-TDY-14와 같은 방식) | P0 | S2 | BL-FND-09, BL-PRJ-01 | AC-02, AC-27 | FR-07, FR-26 |
| BL-TDY-04 | **`TimeAllocator`** | reviewMinutes, mainBudget, limit, 초과 시 조정(`06` §5.6 vector 전부). | P0 | S2 | BL-FND-09 | AC-02 | FR-07 |
| BL-TDY-05 | **`ReasonTemplates`** | reason 1~3개, modifier·task reason 우선, 최소 1개 보장(`06` §5.8). `READ_REAL_CODE`(`{repo.name}`, `READ_CODE` task — BL-TDY-16) 포함. | P0 | S2 | BL-TDY-02 | AC-02 | FR-07 |
| BL-TDY-06 | **`ComebackModePolicy`** | 복귀 모드 판정(`06` §5.5 정의), cap 10, difficulty ≤ 2. | P0 | S2 | BL-FND-10 | AC-02 | FR-21 |
| BL-TDY-07 | Today 생성 API | `TodayPlanService` + `POST /today/generate`: availableMinutes 5~720, 후보(`06` §5.2), risk 요청 시점 계산(`StudyBudgetService` — budget·risk가 Today와 같은 S2에 들어온다, `05` §8.2 3단계), 재생성 표(`06` §5.9, PLANNED 삭제 후 flush), `generation_count`, 활성 plan 없음 404 `PLAN_NOT_FOUND`. S2에는 challenge 후보(BL-TDY-14)와 reading 후보(BL-TDY-16)를 빈 목록으로 전달 | P0 | S2 | BL-TDY-01, BL-TDY-02, BL-TDY-03, BL-TDY-04, BL-TDY-05, BL-TDY-06, BL-MEM-04, BL-TDY-10, BL-GOL-11 | AC-02, AC-03, AC-17 | FR-05, FR-07, FR-21 |
| BL-TDY-08 | Today 조회 API | `TodayQueryService` + `GET /today`(없으면 404 `TODAY_NOT_GENERATED`) | P0 | S2 | BL-TDY-01 | AC-02, AC-17 | FR-07 |
| BL-TDY-09 | Task 상태 API | `PATCH /today/tasks/{taskId}`(status, version, SKIPPED→PLANNED는 활성 main 없을 때만) | P0 | S2 | BL-TDY-01 | AC-02 | FR-07 |
| BL-TDY-10 | 학습 세션 API | `LearningSession`, `LearningSessionService`: `POST /learning-sessions`(기존 IN_PROGRESS → ABANDONED 후 flush, I-05), `POST .../complete`(actualMinutes 0~720, selfReflection), `POST .../abandon`, `GET /learning-sessions?from=&to=&cursor=`, `SESSION_*` 이벤트 | P0 | S2 | BL-SKL-03 | AC-02 | FR-08 |
| BL-TDY-11 | Dashboard 최소 API | `DashboardQueryService` + `GET /dashboard`: 오늘 상태, due 수, 최근 7 plan-day 학습 시간, 현재 milestone | P0 | S2 | BL-TDY-08, BL-MEM-05 | AC-02 | FR-16 |
| BL-TDY-12 | Dashboard 완성 API | risk 추세(snapshot), skill 카테고리 요약, 약한 thinking 축 Top 3 | P1 | S5 | BL-TDY-11, BL-GOL-14, BL-COA-09 | AC-02 | FR-16 |
| BL-TDY-13 | 캘린더 피드 | `CalendarTokenService` + `POST /me/calendar-token`(32바이트 토큰, SHA-256 hash 저장, 재발급 시 이전 폐기), `CalendarFeedController` + `IcsFeedWriter` + `GET /calendar/{token}.ics`(Bearer 없음, 불일치 404, 제목·예상 시간만). 2026-09-18 S2 → S3, v3에서 S3 → S5(M2) | P1 | S5 | BL-TDY-08, BL-SEC-06 | AC-08 | FR-20 |
| BL-TDY-14 | Today ↔ challenge 연결 | `ChallengeQueryService`를 `TaskProposalPolicy` 입력으로 연결(VALIDATED PRACTICE, 14 plan-day 내 attempt 제외, SOLVED_* outcome이 있는 challenge 제외). `aiStatus ∈ {DISABLED, BALANCE_EXHAUSTED}`이면 CHALLENGE 제안을 만들지 않는다(AI 평가 없이 풀 수 없음) | P0 | S3 | BL-TRN-02, BL-TDY-07, BL-AIP-10 | AC-02, AC-12 | FR-07 |
| BL-TDY-15 | plan-day 시작 시 Today 자동 생성 | (검토 제안, 미결정) `dayStartHour`에 전날 설정(시간·컨디션 기본값)으로 daily plan을 미리 만들어 첫 진입 시 "생성" 단계를 없애는 방식. 재생성 규칙·`generation_count`와의 상호작용 결정 필요. 사용자 결정 후 착수 | P3 | Later | BL-TDY-07, BL-GOL-14 | — | FR-07 |
| BL-TDY-16 | `READ_CODE` 과제 | **`TaskProposalPolicy`** 2번 분기(`06` §5.3): AI 상태가 `DISABLED`가 아니고(완료 조건이 러버덕이므로) planning KNOWLEDGE ≥ 1(RC-3 — `BL-GOL-18`이 이 상수를 `trackDefaults.readCodeMinKnowledge`로 바꾼다)이고 선택 가능한 reading(skill code 포함, 사용자가 완료한 `READ_CODE`의 reading 제외, 최근 14 plan-day 안에 제안된 reading 제외, `key` ASC 첫 번째)이 있으면 `READ_CODE`(estimated = `reading.estimatedMinutes`, difficulty 2), 제목·설명 템플릿, `06` §5.3 제안 분기 vector T-1~T-5. `learning_task.reading_key`(생성 시점 고정, CHECK `learning_task_reading_key_type` — I-17), `TaskView.readingKey`(`05` §8.1). `ReadingController` + `GET /readings/{readingKey}`(`05` §19.7: 형식 위반 400 `Pattern`, 없는 key 404 `RESOURCE_NOT_FOUND`, **코드 본문 없음**, 서버 외부 요청·AI 호출 없음, 사용자 소유가 아닌 공용 조회). RC-1 완료 조건: `PATCH /today/tasks/{taskId}` `IN_PROGRESS → COMPLETED`는 그 task가 대상인 `COMPLETED` 러버덕 세션이 있어야 하고 없으면 409 `INVALID_STATE_TRANSITION`(`05` §8.4) | P0 | S3 | BL-TDY-03, BL-TDY-07, BL-TDY-09, BL-CNT-15, BL-RDK-02, BL-AIP-10 | AC-28, AC-02 | FR-27, FR-07 |
| BL-TDY-17 | 재현 과제 제안 | **`RedoTaskPolicy`**(`today.domain`, `06` §5.10 RE-1~RE-4): 후보 조회 절차 5단계, 창 `devpilot.planner.redo.min-days-after`·`max-days-after`(3·7, 양 끝 포함)·`max-attempts`(2), `lastAttemptDate` 계산, 정렬 `lastAttemptDate ASC → 원본 task.id ASC`, skill당 1개. **`TaskProposalPolicy`** 0번 분기(`REDO`, estimated·difficulty는 원본 그대로), **`PlannerScoring`** modifier 5번 `REDO_DUE`(13_000bp, `03` §9 `planner.modifiers.redo-due`), **`ReasonTemplates`** `REDO_WITHOUT_AI`(`reasonParams.redoDaysAfter`), **`TimeAllocator`**에서 `limit` 초과 시 `REDO`를 버리고 1번부터 재선택(RE-4), §5.2 후보 skill 5번(목표 달성 skill 예외). `learning_task.redo_source_task_id` 저장(I-20), `MainTaskView.redoSourceTaskId`·`redoSourceTaskType`·`redoWithoutAi`(`05` §8.1). vector `06` §5.10 RE-V1~RE-V11(`06-05-redo-candidate.yaml`) | P1 | S4 | BL-FND-27, BL-TDY-03, BL-TDY-07, BL-TRN-05 | AC-31, AC-02 | FR-28, FR-07 |
| BL-TDY-18 | 재현 완료·AI 잠금 | 완료 경로(`05` §8.4, RE-6~RE-8): `PATCH /today/tasks/{taskId}`의 `redoWithoutAi`(REDO 완료 때 필수 `VALUE_REQUIRED`, 그 밖에는 `VALUE_NOT_ALLOWED`), `learning_task.redo_without_ai`(I-21), `LearningEventType.REDO_COMPLETED`·`EventSourceType.LEARNING_TASK`와 payload record `{taskId, sourceTaskId, sourceTaskType, withoutAi, difficulty}`(`04` §6, dedupe `REDO:{taskId}:{skillId}`), `withoutAi = false`면 복습 카드 upsert(RE-7 — `concept_key = REDO:{sourceTaskId}`, `source_type = REDO_TASK`, `origin = MANUAL`, `review_type = EXPLAIN`, 첫 due `06` §6.3). `today.application.RedoLockService`(implements `learning.application.RedoLockProvider` — `challengeLocked`·`sideProjectLocked`, `PLANNED`·`IN_PROGRESS`만, `idx_learning_task_redo_candidate` 사용). `ErrorCode.AI_ASSIST_LOCKED_FOR_REDO`(409, `05` §1.3)와 `messages_ko.properties`. 격리 목록 변경 없음(기존 `PATCH /today/tasks/{taskId}`) | P1 | S4 | BL-TDY-17, BL-MEM-07, BL-SKL-03 | AC-31, AC-05 | FR-28 |

### 4.8 MEM — Memory Review

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-MEM-01 | Review 도메인 | `ReviewItem`, `ReviewAnswer` 매핑, 상태 전이(`04` §4.5), `unique(user_id, concept_key)` 위반 변환 | P0 | S2 | BL-FND-15 | AC-05 | FR-11 |
| BL-MEM-02 | **`FinalRatingPolicy`** | 자기평가 → 최종 등급, `adjustedBy`(`06` §6.1). | P0 | S2 | BL-FND-09 | AC-05 | FR-11 |
| BL-MEM-03 | **`RuleBasedV1Scheduler`** | `ReviewSchedulingStrategy` 구현: 간격, clamp 1~60, horizon cap, `hard-strategy`(MULTIPLY/FIXED_2), due 계산(`06` §6.2, §6.6 vector 전부). | P0 | S2 | BL-FND-09, BL-FND-10 | AC-05 | FR-11 |
| BL-MEM-04 | **`DueReviewSelector`** | 오늘 due 대상·정렬·상한(20 / 복귀 모드 10)(`06` §6.5 1~2단계). 3단계 재배치(RV-INTERLEAVE)는 BL-MEM-12. | P0 | S2 | BL-FND-10 | AC-05, AC-17 | FR-11 |
| BL-MEM-05 | Due 조회 API | `ReviewQueryService`(다른 모듈 공개) + `GET /reviews/due?limit=`: prompt + expectedAnswer + rubric. variant 출제(`variant_status=READY`)는 BL-MEM-10(Later)과 함께 | P0 | S2 | BL-MEM-01, BL-MEM-04 | AC-05, AC-10 | FR-11 |
| BL-MEM-06 | 복습 답변 API | `ReviewService.answer` + `POST /reviews/{reviewItemId}/answer`(evaluate=false 경로): `review_answer` INSERT, 스케줄, leech(`06` §6.4 — 최근 1 plan-day 안 `LEECH_DETECTED` 이벤트가 있으면 planner reviewUrgency 최댓값), `REVIEW_ANSWERED`/`LEECH_DETECTED` 이벤트 | P0 | S2 | BL-MEM-02, BL-MEM-03, BL-MEM-05, BL-SKL-03 | AC-05, AC-10 | FR-11 |
| BL-MEM-07 | 복습 카드 관리 API | `ReviewItemService`: `GET /review-items?skillId=&status=&cursor=`, `POST /review-items`(수동, `concept_key = MANUAL:{uuid}`, 첫 due 다음 plan-day), `PATCH /review-items/{reviewItemId}`(status/prompt/expectedAnswer/version, SUSPENDED→ACTIVE due 재설정). 2026-09-18 S2 → S3(수동 카드 CRUD 범위 축소). S2의 leech 자동 SUSPENDED는 BL-MEM-06에서 처리하고 재활성화는 S3 | P0 | S3 | BL-MEM-01 | AC-05 | FR-11 |
| BL-MEM-08 | Seed 카드 배정 | `SeedCardAssignmentService`(`review.application.SeedCardRegistry`에 content가 등록): 온보딩 시 seed 카드 → `review_item` 복사(`06` §6.3 첫 행, 하루 5장 분산), 기동 시 `ContentSeeder`가 `SeedCardAssignmentService.backfillAll()`을 호출해 기존 사용자에게 신규 카드 추가. S1에 온보딩한 사용자는 S2 첫 배포 기동 시 배정 | P0 | S2 | BL-MEM-01, BL-CNT-06, BL-GOL-06 | AC-11 | FR-02, FR-11 |
| BL-MEM-09 | 복습 AI 평가 | `POST /reviews/{reviewItemId}/answer` `evaluate=true`: `REVIEW_EVALUATE` 동기(20초, 재시도 0, thinking off), 복습 rubric 균등 배분 coverage(`06` §8.1, `RubricScorer`는 `learning.domain`), 실패·차단 시 `NOT_EVALUATED` + `evaluationSkippedReason` | P1 | S3 | BL-MEM-06, BL-AIP-07 | AC-05, AC-12 | FR-11 |
| BL-MEM-10 | `ReviewVariantTask` | 연속 실패 ≥ 2이고 AI 사용 가능하면 커밋 후 `REVIEW_VARIANT` 비동기 생성, `variant_status` 전이(`04` §4.5), `GET /reviews/due` variant 출제, `AiPendingJobCounter` 구현(review). 2026-09-18 Later(`REVIEW_VARIANT` operation 보류; `03` §9 설정에는 남겨 둔다) | P3 | Later | BL-MEM-06, BL-FND-23, BL-AIP-07, BL-AIP-10 | AC-05 | FR-11 |
| BL-MEM-11 | FSRS 전략 | `ReviewSchedulingStrategy` 두 번째 구현. 전환 조건은 ADR-018 | P3 | Later | — | — | FR-11 |
| BL-MEM-12 | `RV-INTERLEAVE` 교차 학습 | **`DueReviewSelector`** 3단계 재배치(`06` §6.5): 1단계 정렬 → 2단계 cap → **재배치** — 같은 skill 카드가 3장 연속이 되는 자리에서 그 뒤쪽 카드 중 직전 카드와 skill이 다른 가장 가까운 카드와 swap, 바꿀 대상이 없으면 그대로(RV-INTERLEAVE), 무작위·셔플 없음(RV-INTERLEAVE-D), 전진 1회 통과(RV-INTERLEAVE-S), 카드 집합·장수 불변(RV-INTERLEAVE-C). `06` §6.6 RV-INTERLEAVE vector (a)·(b)·(c) parameterized test. `GET /reviews/due`(BL-MEM-05)와 Today REVIEW task의 출제 순서가 이 결과다 | P0 | S2 | BL-MEM-04 | AC-29, AC-05 | FR-11 |

### 4.9 TRN — Training

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-TRN-01 | Training 도메인 | `Challenge`, `ChallengeSkill`, `ChallengeAttempt`, `ChallengeSubmission` 매핑, 상태 전이(`04` §4.2) | P0 | S3 | BL-FND-16 | AC-04 | FR-09 |
| BL-TRN-02 | Challenge 조회 API | `ChallengeQueryService`(today 공개) + `GET /challenges?skillId=&purpose=&cursor=`(VALIDATED, seed + 본인 소유), `GET /challenges/{challengeId}`(본인 attempt 평가 완료 전 rubric·expectedConcepts 제외) | P0 | S3 | BL-TRN-01, BL-CNT-07 | AC-04, AC-08 | FR-09 |
| BL-TRN-03 | `ChallengeValidationService` | VALIDATED 조건(I-09), rubric weight 합 10000(I-10), difficulty 1~5, hints 1~3단계, skill code 존재 → `VALIDATED`/`REJECTED` + `rejection_reason` | P0 | S3 | BL-TRN-01 | AC-04 | FR-09 |
| BL-TRN-04 | Challenge 생성 | `ChallengeGenerationService` + `POST /challenges/generate`(202) + `ChallengeGenerationTask`(`CHALLENGE_GENERATE`). `AiPendingJobCounter` 구현(training: 생성 중 challenge의 PENDING/RUNNING 수). v3에서 S3 → S5(M2 — M1은 seed challenge만 쓴다) | P1 | S5 | BL-TRN-03, BL-FND-23, BL-AIP-07, BL-CNT-10, BL-AIP-10 | AC-04, AC-12, AC-23 | FR-09 |
| BL-TRN-05 | Attempt API | `AttemptService`: `POST /challenges/{challengeId}/attempts`(201, VALIDATED만), `GET /challenge-attempts/{attemptId}`, `POST .../abandon`, `CHALLENGE_STARTED` 이벤트 | P0 | S3 | BL-TRN-02, BL-SKL-03 | AC-04 | FR-09 |
| BL-TRN-06 | Self-explanation API | `POST /challenge-attempts/{attemptId}/self-explanation`(text 또는 skipped), `SELF_EXPLANATION_*` 이벤트 | P0 | S3 | BL-TRN-05 | AC-16 | FR-10 |
| BL-TRN-07 | **`HintLadderPolicy`** | HL-1~HL-8 판정(`06` §9.1~9.2 vector 전부) | P0 | S3 | BL-FND-09 | AC-16 | FR-10 |
| BL-TRN-08 | Challenge hint API | `HintService` + `POST /challenge-attempts/{attemptId}/hints`: 1~3단계 `hints_json`, 4단계 이상 `HINT_GENERATE` 동기, `hint_disclosure`, `HINT_DISCLOSED`, `max_hint_level` | P0 | S3 | BL-TRN-06, BL-TRN-07, BL-AIP-07 | AC-12, AC-16, AC-23 | FR-10 |
| BL-TRN-09 | 제출·평가 | `SubmissionService` + `POST .../submissions`(202, `SUBMISSION_LIMIT_REACHED`, `EVALUATION_IN_PROGRESS`, `SELF_EXPLANATION_REQUIRED`) + `SubmissionEvaluationTask`(`CHALLENGE_EVALUATE`), 코드 masking 후 저장. `AiPendingJobCounter` 구현(training: 평가 중 submission의 PENDING/RUNNING 수) | P0 | S3 | BL-TRN-06, BL-TRN-11, BL-AIP-09, BL-FND-23, BL-AIP-07, BL-CNT-10, BL-AIP-10 | AC-04, AC-14 | FR-09 |
| BL-TRN-10 | 평가 재시도 API | `POST .../submissions/{submissionNo}/retry`(202, FAILED만, 그 외 409 `AI_TASK_NOT_RETRYABLE`) | P0 | S3 | BL-TRN-09 | AC-04, AC-12 | FR-09 |
| BL-TRN-11 | **`RubricScorer`**, **`AttemptOutcomeCalculator`** | coverage, evaluatedOutcome, attempt outcome(`06` §8.1~8.2 vector 전부), `CHALLENGE_SUBMITTED`/`CHALLENGE_EVALUATED` payload | P0 | S3 | BL-FND-09 | AC-04, AC-09 | FR-09 |
| BL-TRN-12 | 실패 attempt → 복습 항목 | 평가 후 `06` §8.3 조건이면 challenge skill마다 review item upsert(due `planDayStart(today + 1)`) | P0 | S3 | BL-TRN-09, BL-MEM-07 | AC-05 | FR-09, FR-11 |
| BL-TRN-13 | 진단 제안 | `DiagnosticSuggestionService` + `GET /diagnostics/suggestions`(`05` §4.2: 진단 모드 — `runDiagnostic = true`로 `self_assessed_level`이 모두 null — 면 `self_assessment_active` category 전부, 자기평가 모드면 자기평가 최댓값 ≥ 3인 category, category당 1문제·최대 5개, DIAGNOSTIC attempt가 이미 있는 category 제외), 온보딩 응답 `suggestedDiagnostics` 연결(`05` §4.1 11단계), `DIAGNOSTIC_PASSED`/`DIAGNOSTIC_FAILED` 판정(`06` §7.4). 진단이 온보딩의 시작점이 되어 v3에서 P1 → P0 | P0 | S3 | BL-TRN-11, BL-CNT-08 | AC-11 | FR-02, FR-15 |
| BL-TRN-16 | 트랙별 진단 제안 | `DiagnosticSuggestionService`가 학습 목표의 트랙을 쓴다: 그 트랙에 role target이 있는 category만 제안하고, 고르는 challenge의 `difficulty ≤ devpilot.tracks.<트랙>.max-task-difficulty`. 그 밖의 선택 순서(`05` §4.2)는 그대로다. 입문 트랙에서 difficulty 4·5 진단이 나오지 않는 것을 테스트한다 | P1 | S3 | BL-TRN-13, BL-GOL-18 | AC-32, AC-11 | FR-15, FR-02 |
| BL-TRN-17 | 재현 잠금 (hint HL-9) | `HintService`가 `learning.application.RedoLockProvider`(구현 `today`, BL-TDY-18)를 주입받아 **HL-2보다 먼저** 검사한다: 그 attempt의 `challenge_id`가 열려 있는 `REDO` 과제의 원본이면 409 `AI_ASSIST_LOCKED_FOR_REDO`, `hint_disclosure`·`HINT_DISCLOSED`·AI 호출 없음(`06` §9.1 HL-9, `05` §10.8 3단계). vector `06` §9.2 3행. coach finding hint(`BL-COA-07`)는 잠그지 않는다(재현 대상이 아니다) | P1 | S4 | BL-TRN-08, BL-TDY-18 | AC-31, AC-16 | FR-28, FR-10 |
| BL-TRN-14 | 코드 실행 runner | 샌드박스 컴파일·테스트 실행 → `COMPILER`/`TEST_RESULT` 근거 | P3 | Later | — | — | FR-14 |
| BL-TRN-15 | 자기설명 선택화 · EXPLAIN 카드 상한 | (검토 제안, 미결정) 실사용 4주 후 self-explanation 필수(HL-2)가 부담이면 난이도 ≤ 2 challenge에서 선택으로 바꾸고, 하루 EXPLAIN 복습 카드 수에 상한을 두는 방식을 검토. `06` §9.1·§6.5 개정이 선행 | P3 | Later | BL-TRN-06, BL-CNT-14 | — | FR-10, FR-11 |

### 4.10 AIP — AI Platform

`17-ai-integration.md`가 기준이다. 이 표의 ID와 범위는 `17-ai-integration.md`의 구현 체크리스트와 같다.

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-AIP-01 | AI port·Fake·Disabled | `AiProvider`, `AiRequest<T>`, `AiResult<T>`, `AiOperation`, `AiCallStatus`(402 → `PROVIDER_ERROR` 재시도 불가, `content_filter` → `REFUSED`, `insufficient_system_resource`·`aborted`·429·5xx·timeout → 재시도 가능 `PROVIDER_ERROR`, 출력 상한 잘림 → `INVALID_OUTPUT`, 미지 값 → `stop:<raw>` 기록), `FakeAiProvider`(operation별 fixture, `finishReason: completed\|max_output_tokens\|content_filter\|insufficient_system_resource\|aborted`, `simulate: INSUFFICIENT_BALANCE\|CONTENT_FILTER`, usage `cacheHitTokens`·`reasoningTokens`), `DisabledAiProvider`(항상 `AI_UNAVAILABLE`). `AiStatus`·`AiUsageSnapshot` record는 BL-AIP-16(S1) | P0 | S3 | BL-FND-09, BL-AIP-16 | AC-12 | NFR-03, NFR-10 |
| BL-AIP-02 | AI 설정 바인딩 | `DevPilotProperties.ai`(provider, model, deepseek{base-url, api-key, store, retry-after-default}, monthly-budget-usd 기본 3, budget-warning-ratio, min-balance-usd, balance-check-cron, operations{mode, thinking, reasoning-effort, max-tokens, timeout, max-retries, input-token-budget}, pricing{peak-multiplier, models}, guard 임계값) 정수 변환(N-6), 모델 단가·operation 누락 시 기동 실패 (`03` §9) | P0 | S3 | BL-AIP-01 | AC-13 | NFR-07, NFR-10 |
| BL-AIP-03 | `DeepSeekAiProvider` (RestClient) | `integration.ai.deepseek.DeepSeekAiProvider`: Spring `RestClient`로 `POST /responses` + `text.format {type: json_schema, name, schema}`, `instructions`=system.md, `input`, `store:false`, `max_output_tokens`, `thinking {type: enabled\|disabled}`, `reasoning_effort low\|high\|max`; 요청에 `user`/`user_id` 금지(캐시 분리 방지); 응답 `output[]`의 `type=message` `output_text`만 순서대로 추출; op별 read timeout 고정 + 시도 사이 deadline 검사; `Retry-After`(없으면 `retry-after-default` 2s, deadline 상한) 대기(ARCH-07 예외 패키지); `HttpClientErrorException`(400/401/402/422/429)·`HttpServerErrorException`·`ResourceAccessException` → `AiCallStatus`; 잘림 재시도 시 thinking off; `/responses` 정확한 필드(`status: incomplete`, `incomplete_details.reason`, `usage.input_tokens_details.cached_tokens`, `usage.output_tokens_details.reasoning_tokens`)는 구현 시 실제 응답으로 확인. 테스트 `DeepSeekAiProviderRequestTest`(`MockRestServiceServer`). 잔액 조회 `checkBalance()`(`GET /user/balance`)는 BL-AIP-17 | P0 | S3 | BL-AIP-02, BL-FND-01 | AC-12 | NFR-10 |
| BL-AIP-04 | ~~취소~~ Refusal 처리 | 2026-09-18 취소(Anthropic 전용 서버 측 refusal fallback·iteration 비용). `content_filter` → `REFUSED` 매핑은 BL-AIP-01·03, coach `AI_REFUSED` 재시도 불가는 BL-COA-05 | — | ~~S3~~ | — | — | — |
| BL-AIP-05 | `PromptRegistry` | `classpath:prompts/<id>/<version>/` 로딩, 활성 버전, `PromptRenderer`(줄 번호, user content escape, 토큰 추정, 절삭 mode), prompt 파일 불변 테스트 | P0 | S3 | BL-AIP-02 | — | NFR-07 |
| BL-AIP-06 | 출력 스키마 | `OutputSchemaRegistry`, operation 출력 record 9종(러버덕 2종은 BL-RDK-03 — 합계 11종), 전용 `ObjectMapper`, 스키마-record 일치 테스트 | P0 | S3 | BL-AIP-01 | AC-04, AC-06 | NFR-07 |
| BL-AIP-07 | `AiGateway` | 예산 → provider → stop_reason → 스키마·가드 → (비동기 operation만) 1회 재시도 → `ai_call_log` 기록(`AiCallLog`, 별도 트랜잭션 writer, 원문 미저장, `guard_actions`), `devpilot.ai.*` Micrometer 지표 | P0 | S3 | BL-AIP-03, BL-AIP-05, BL-AIP-06, BL-AIP-10, BL-FND-16 | AC-12, AC-13 | NFR-06, NFR-07 |
| BL-AIP-08 | 출력 가드 | `OutputGuardChain`: `CodeLeakGuard`(HL-8), `EnumGuard`, `SkillCodeGuard`, `LanguageGuard` (S3), `VerificationGuard`(`06` §10 vector 전부, URL fetch 없음), `FindingCountGuard` (S4 coach와 함께). 러버덕 전용 `NoAnswerGuard`(`17` §6.8)는 BL-RDK-03 | P0 | S3 | BL-AIP-06 | AC-06, AC-07, AC-16 | FR-10, FR-12, FR-14 |
| BL-AIP-09 | `SecretMasker` | 패턴·test vector(`17-ai-integration.md`), private key 정규식 차단, `MaskingResult`, 적용 endpoint 연결(`05` §1.11·§19.2 — S1~S2에 먼저 생긴 자유 텍스트 endpoint(`/side-projects`, 온보딩 `sideProject`, 세션 `selfReflection` 등), 러버덕 턴 `explanation`, 프로젝트 기록(`/side-projects/{id}/notes*`의 `title`·본문 항목 전부, BL-PRJ-02) 포함, coach endpoint는 S4), `SECRET_BLOCKED` 감사 로그 | P0 | S3 | BL-AIP-01 | AC-14 | NFR-05 |
| BL-AIP-10 | `AiBudgetGuard` | 사용자당 일일 호출 60회(plan-day 기준), **서비스 전체** 월 비용 USD 3(`monthly-budget-usd`, Asia/Seoul 달력 월, 경고 80%), 사용자당 동시 실행 2(동기 진행 중 + 비동기 PENDING/RUNNING, permit 수명), 잔액 소진 플래그(`AiBalanceMonitor`, BL-AIP-17) 확인, `BUDGET_BLOCKED` 기록, `GET /me` `aiStatus`·`aiUsage` (ADR-029). port `common.time.UserTimeSettingsProvider`(user 모듈 구현)로 요청 밖 timezone·dayStartHour 조회, port `integration.ai.api.AiPendingJobCounter`(coach·training·evidence·radar 구현; review는 BL-MEM-10 Later) 합산으로 비동기 진행 수 계산. 테스트 벡터는 test profile 예산 25 기준(`17`·`09`) | P0 | S3 | BL-AIP-02, BL-FND-21, BL-AIP-16 | AC-13 | FR-22, NFR-01 |
| BL-AIP-11 | `AiCostCalculator` | `pricing.models.<model> {input, cache-hit, output}`(USD/1M) × `peak-multiplier` 2(결정 F: 시각 규칙 없이 항상): `cost_micro = 2 × [(inputTokens − cachedTokens) × input + cachedTokens × cacheHit + outputTokens × output]`(정수 연산, 추론 토큰은 출력에 포함), `reasoning_tokens`·`effort(off\|low\|high\|max)` 기록. `deepseek-v4-pro` cache-hit 단가 미확인 → 입력 단가로 보수 계산 | P0 | S3 | BL-AIP-02 | AC-13 | NFR-01 |
| BL-AIP-12 | AI ArchUnit 규칙 | 트랜잭션 안 `AiGateway` 호출 금지(T-2), DeepSeek HTTP DTO·`RestClient`는 `integration.ai.deepseek`만(ARCH-07 `Thread.sleep` 예외도 이 패키지만), 도메인 모듈은 `AiGateway`와 `integration.ai.api`(`AiStatus`, `AiUsageSnapshot`, `AiBudgetDecision`, `AiConcurrencyReservation`)만 사용 | P0 | S3 | BL-AIP-07, BL-FND-05 | AC-12 | NFR-07 |
| BL-AIP-13 | Evals v1 | `evals/` 구조, `evalTest` source set·`aiEval` task, 채점·baseline·실행 전 비용 미리보기(`-PevalMaxCostUsd` 기본 0.5), `ai-eval.yml`(`workflow_dispatch`만, environment `ai-eval`, secret `DEEPSEEK_API_KEY`, prod와 같은 잔액 — 결정 E). S3 seed case: `hint-generate`·`challenge-evaluate`·`rubber-duck`(BL-RDK-03). coach case(`coach-review` 등)는 S4의 BL-COA-03·BL-CNT-11에서 추가한다. v3에서 S4 → S3 — 중심 기능인 러버덕 prompt 품질을 M1 안에서 eval로 확인한다 | P1 | S3 | BL-AIP-07, BL-CNT-10 | AC-26, AC-06, AC-07 | FR-25, FR-12, NFR-07 |
| BL-AIP-14 | 실측 반영 | `ai_call_log` 2주 집계로 토큰·추론 토큰·캐시 적중 추정 계수, 비용 추정표(USD 3/월 근거), operation별 thinking·reasoning-effort 조정 여부를 문서에 반영 | P1 | S5 | BL-AIP-07 | AC-13 | NFR-01 |
| BL-AIP-15 | AI 운영 게이트 (DeepSeek) | DeepSeek는 콘솔 지출 한도가 없다 → 선불 잔액을 소액(USD 10 안팎)으로 유지 + `AiBalanceCheckJob`(BL-AIP-17) + 앱 예산 가드가 상한. 공급자 정책 확인 결과를 ADR-013에 기록(중국 소재, 학습 미사용 약정 없음, zero-retention 없음 → 학습용 프로젝트로 진행), Coach 제출 화면·초대 시 1회 안내 동의 문구(BL-CLI-24·03) 반영 확인, API 키 1개(prod·eval 공용). 완료 전 prod `DEVPILOT_AI_PROVIDER=deepseek` 금지 | P0 | S3 | BL-AIP-17 | AC-13, AC-14 | NFR-01, NFR-05 |
| BL-AIP-16 | `AiStatus`·`AiUsageSnapshot` record | `integration.ai.api`의 `AiStatus` enum(`ENABLED`, `BUDGET_WARNING`, `DISABLED`, `BALANCE_EXHAUSTED`)과 `AiUsageSnapshot` record(`todayCalls`, `dailyCallLimit`, `monthCostUsd`, `monthlyBudgetUsd`)만 S1에 먼저 만들어 `GET /me`(BL-FND-21)가 쓴다. S1~S2는 provider `disabled` 고정값(`DISABLED`, 호출 0). BL-AIP-01에서 분리(2026-09-18) | P0 | S1 | BL-FND-09 | AC-12 | NFR-03 |
| BL-AIP-17 | `AiBalanceCheckJob` · 잔액 모니터 | `AiProvider.checkBalance()`(`DeepSeekAiProvider`가 `GET /user/balance` 조회) + `integration.ai.budget.AiBalanceMonitor`(메모리 플래그) + `AiBalanceCheckJob`(매시 15분, `balance-check-cron`, `provider=deepseek`일 때만): 잔액 < `min-balance-usd`(1.00) 또는 402 수신 → `BALANCE_EXHAUSTED`(다음 정상 확인까지 `DISABLED`처럼 동작, 이유 표시), 회복 시 해제, 소진 전이 시 감사 `AI_BALANCE_LOW` + ERROR, 해제 시 INFO 로그(`17` §8.7), 조회 실패는 상태 유지 (`03` §6, `17` §8) | P0 | S3 | BL-AIP-03, BL-FND-25, BL-AIP-16 | AC-12, AC-13 | NFR-01, NFR-03 |

### 4.11 COA — Project Coach

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-COA-01 | Coach 도메인 | `CoachReview`, `CoachFinding`, `ThinkingPatternObservation` 매핑, 상태 전이(`04` §4.3) | P1 | S4 | BL-FND-17 | AC-06 | FR-12 |
| BL-COA-02 | Coach 생성 API | `CoachReviewService` + `POST /coach/reviews`(202): `sideProjectId`(선택 — 본인 `side_project`가 아니거나 없으면 400 `REFERENCE_NOT_FOUND`, `coach_review.side_project_id`, `05` §12.1), 동의·크기(30000 bytes, 1000줄) → masking(422) → 예산(429/503) → 마스킹본 저장(`PENDING`, retention 30일) → `CoachReviewRequested`. `AiPendingJobCounter` 구현(coach: 분석 중 review의 PENDING/RUNNING 수) | P1 | S4 | BL-COA-01, BL-AIP-09, BL-FND-23, BL-AIP-07, BL-AIP-10, BL-PRJ-01 | AC-06, AC-14, AC-23, AC-27 | FR-12, FR-26 |
| BL-COA-03 | `CoachAnalysisTask` | `COACH_REVIEW` 비동기, skill state·약한 thinking 축 컨텍스트, findings INSERT, `INCORRECT_CLAIM` observation. eval case `coach-review`(BL-AIP-13 러너) | P1 | S4 | BL-COA-02, BL-CNT-11, BL-AIP-08 | AC-06, AC-07, AC-19 | FR-12, FR-14 |
| BL-COA-04 | Coach 조회 API | `GET /coach/reviews/{reviewId}`(polling, 수정 코드 필드 없음), `GET /coach/reviews?cursor=` | P1 | S4 | BL-COA-01 | AC-06, AC-08 | FR-12 |
| BL-COA-05 | Coach 재시도 API | `POST /coach/reviews/{reviewId}/retry`(202, FAILED만, 원문 purge·`CONFIDENTIAL_SUSPECTED`·`AI_REFUSED`(content_filter)면 409 `AI_TASK_NOT_RETRYABLE`) | P1 | S4 | BL-COA-03 | AC-06, AC-12 | FR-12 |
| BL-COA-06 | Finding 응답 API | `CoachFindingService` + `POST .../findings/{findingId}/responses`: `COACH_RESPONSE_FEEDBACK` 동기, `user_identified_issue`, 실패·차단 시 응답만 저장 + `feedbackSkippedReason` | P1 | S4 | BL-COA-01, BL-AIP-07 | AC-12, AC-19 | FR-12 |
| BL-COA-07 | Finding hint API | `POST .../findings/{findingId}/hints`: `HintService` 재사용, 전 단계 `HINT_GENERATE`, HL-2(응답 또는 `skipSelfExplanation`) | P1 | S4 | BL-TRN-08, BL-COA-01 | AC-16 | FR-10 |
| BL-COA-08 | Finding 상태 API | `PATCH .../findings/{findingId}`(RESOLVED/DISMISSED, version, 닫힌 리뷰 409 `REVIEW_ALREADY_CLOSED`) | P1 | S4 | BL-COA-01 | AC-06 | FR-12 |
| BL-COA-09 | Review close | **`DiscoveredByResolver`**(`06` §9.3) + `ThinkingPatternRecorder` + `POST .../complete`: `closed_at`, `discovered_by` 확정, observation, `COACH_FINDING_CLOSED`, `COACH_REVIEW_COMPLETED`. skill이 있는 BUG/RISK finding 중 `discoveredBy ∈ {MISSED, FOUND_AFTER_HINT}`는 review item upsert(`COACH:{skillCode}:{category}`, due 다음 plan-day) | P1 | S4 | BL-COA-06, BL-SKL-03, BL-MEM-07 | AC-05, AC-09, AC-19 | FR-11, FR-12, FR-13 |
| BL-COA-10 | 원문 삭제·purge | `DELETE /coach/reviews/{reviewId}/content`(즉시), `CoachContentPurgeJob`(UTC 18:20, `content_retention_until < now`) | P1 | S4 | BL-COA-01, BL-FND-25 | AC-14, AC-15 | NFR-05 |
| BL-COA-11 | IDE plugin | JetBrains plugin: 선택 코드·diff → coach review 요청 (ADR-004) | P3 | Later | — | — | FR-12 |

### 4.12 EVD — Evidence · Weekly

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-EVD-01 | **`MetricsCalculator`** | `06` §12 지표 전부(정수). `requirementCoverageBp`는 port `evidence.application.RequirementCoverageProvider`를 `ObjectProvider`로 받고, 구현(BL-REQ-04)이 없는 S7 전에는 null. `projectNoteCount`(기간 내 `occurredOn`인 `side_project_note` 수, `SideProjectNoteQueryService`)와 `independentRedoCount`(기간 내 완료한 `REDO` 중 `redo_without_ai = true` 수)를 포함하고 `weekly_review.metrics_json`에 넣는다(`04` §5.7). S4 전에는 `independentRedoCount = 0` | P1 | S5 | BL-FND-18 | AC-21 | FR-17 |
| BL-EVD-02 | `WeeklyReviewJob` | `WeeklyReviewService` + 매시 10분, 로컬 월요일 `dayStartHour` 사용자만 지난주 `metrics_json` 생성, `unique(user_id, week_start_date)`로 중복 없음 | P1 | S5 | BL-EVD-01, BL-FND-25 | AC-17, AC-21 | FR-17 |
| BL-EVD-03 | Weekly API | `WeeklyReviewController`: `GET /weekly-reviews?cursor=`, `GET /weekly-reviews/{weekStartDate}`, `PUT /weekly-reviews/{weekStartDate}/reflection`(version) | P1 | S5 | BL-EVD-02 | AC-21 | FR-17 |
| BL-EVD-04 | Thinking 추세 API | `GET /thinking-patterns/trend?weeks=8` (주별·축별 observation 수) | P1 | S5 | BL-COA-09 | AC-19 | FR-13 |
| BL-EVD-05 | Evidence 조회·수동 작성 API | `EvidenceService`: `GET /evidence?status=&cursor=`, `GET /evidence/{evidenceId}`, `POST /evidence` | P1 | S6 | BL-FND-18 | AC-21 | FR-18 |
| BL-EVD-06 | Evidence AI 초안 | `POST /evidence/drafts`(202, `sourceLearningEventId`) + `EvidenceDraftTask`(`EVIDENCE_DRAFT`, `ai_draft_json` 원본 보존). `AiPendingJobCounter` 구현(evidence: 생성 중 초안의 PENDING/RUNNING 수) | P1 | S6 | BL-EVD-05, BL-FND-23, BL-AIP-07, BL-CNT-12, BL-AIP-10 | AC-21, AC-23 | FR-18 |
| BL-EVD-07 | Evidence 편집·승인 API | `PATCH /evidence/{evidenceId}`(version), `POST .../accept`(`EVIDENCE_ACCEPTED` → `evidence_count`), `POST .../reject`, REJECTED → CANDIDATE 복원, ACCEPTED 되돌림 409 | P1 | S6 | BL-EVD-05, BL-SKL-05 | AC-09, AC-21 | FR-18 |
| BL-EVD-09 | 프로젝트 기록 → 증거 초안 | `POST /evidence/drafts`를 `sourceLearningEventId` **또는** `sourceProjectNoteId` 중 하나로 받는다(`05` §14.5: 둘 다 없으면 `ONE_OF_REQUIRED`, 둘 다면 `MUTUALLY_EXCLUSIVE`, 타인 기록이면 `REFERENCE_NOT_FOUND`). 기록 경로는 `SideProjectNoteQueryService`로 조회해 **마스킹본 텍스트·제목·날짜**를 `EVIDENCE_DRAFT` 입력으로 쓰고, 기록의 `skill_id`가 evidence skill이 된다(없으면 null). 출처는 `ai_draft_json.sourceProjectNoteId`로 남기고 `evidence_candidate`에 컬럼을 더하지 않는다. `REDO_COMPLETED`를 허용 `event_type` 목록에 추가한다. `evidence → project` 의존 추가(`03` §2.2). `17` §3.8 prompt 변수 표 갱신 | P1 | S6 | BL-EVD-06, BL-PRJ-02 | AC-33, AC-21 | FR-29, FR-18 |
| BL-EVD-08 | 학습 기록(STAR) export | `EvidenceExportService` + `GET /evidence/export?format=markdown`(ACCEPTED만) | P1 | S6 | BL-EVD-07 | AC-21 | FR-18 |

### 4.13 REQ — 로드맵 비교 (Roadmap compare)

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-REQ-01 | **`RequirementFitClassifier`** | requirement skill의 축별 `gap = max(target − evidence level)` → `READY`(≤ 0), `STRETCH`(= 1), `LATER`(≥ 2), skill 없음 → null. `matchedEvidenceIds`. 확률·점수 없음 (`06-learning-engine-rules.md`) | P1 | S7 | BL-SKL-02, BL-EVD-07 | AC-22 | FR-19 |
| BL-REQ-02 | 로드맵 비교 요청 | `RequirementAnalysisService` + `POST /requirement-docs`(202, `sourceUrl` fetch 금지) + `RequirementAnalysisTask`(`REQUIREMENT_EXTRACT`, `SkillCodeGuard`). `AiPendingJobCounter` 구현(radar: 분석 중 요구사항 문서의 PENDING/RUNNING 수) | P1 | S7 | BL-REQ-01, BL-FND-19, BL-FND-23, BL-AIP-07, BL-CNT-13, BL-AIP-10 | AC-22, AC-23 | FR-19 |
| BL-REQ-03 | 로드맵 비교 조회·삭제 API | `RequirementRadarController`: `GET /requirement-docs/{requirementDocId}`, `GET /requirement-docs?cursor=`, `DELETE /requirement-docs/{requirementDocId}` | P1 | S7 | BL-FND-19 | AC-08, AC-22 | FR-19 |
| BL-REQ-04 | 원문 purge·지표 | `radar.application.RequirementDocPurgeService`(`common.job.RetentionCleanupTarget` 구현)로 `source_text` 180일 purge, `requirementCoverageBp` 지표. `RequirementCoverageProvider` 구현(최근 분석한 요구사항 문서 5개의 REQUIRED 수와 READY 수) | P2 | S7 | BL-FND-24, BL-EVD-01, BL-REQ-02 | AC-22 | NFR-05 |
| BL-REQ-05 | 공개 로드맵 가져오기 | 공개 학습 로드맵을 약관 검토 후 공개 API로만 가져온다 (자동 크롤링 금지 유지) | P3 | Later | — | — | FR-19 |

### 4.14 CNT — Content

형식·검증 규칙·작성 가이드는 `19-content-spec.md`가 기준이다.

**S1 직렬 사슬 감시 권고**: `BL-CNT-01 → CNT-02·CNT-03 → CNT-05·SKL-01 → SKL-02 → GOL-06 → CLI-07`은 병렬화할 수 없는 7단계 사슬이라 S1의 임계 경로다(`BL-PRJ-01`도 `GOL-06`의 선행이지만 이 사슬과 병렬로 진행할 수 있다). S1의 다른 P0 BL보다 `BL-CNT-03` merge가 늦어지면 `11-development-roadmap.md` §2.2의 재추정 조치를 미리 적용한다. CNT-03(skill tree v0)은 사용자 검수가 필요하므로 S0 중에 초안을 시작하는 것이 좋다.

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-CNT-01 | Content 검증기 | `content/` 구조, `YamlContentReader`, `ContentValidator`(기동 시: 스키마, code 유일, parent 존재, prerequisite 순환 금지, role target 참조, conceptKey 유일, rubric weight 합 10000, catalog_version), CI content job은 `content/tools/validate_content.py`로 같은 규칙 검사 | P0 | S1 | BL-FND-14 | AC-11 | FR-06 |
| BL-CNT-02 | `ContentSeeder` | ApplicationRunner, 검증 실패 시 기동 실패, `catalog_version` 비교 upsert, 사라진 skill `active=false`·challenge `RETIRED`, 단일 트랜잭션(`04` §9) | P0 | S1 | BL-CNT-01 | AC-11 | FR-06 |
| BL-CNT-03 | Skill tree v0 | `content/skill-tree/java-backend.yaml`: skill 60~90개, 13 카테고리, prerequisite, `minutesPerLevelStep` | P0 | S1 | BL-CNT-01 | AC-11 | FR-06 |
| BL-CNT-04 | Role target | JAVA_BACKEND priority·practicalImportance·축별 목표 레벨. DEC-14: ALGORITHM SHOULD, EXPLANATION MUST | P0 | S1 | BL-CNT-03 | AC-03, AC-11 | FR-05, FR-06 |
| BL-CNT-05 | Plan template | `content/plan-templates/java-backend.yaml`: JAVA_BACKEND milestone 9개를 **주문 시스템을 만드는 순서**로 재작성 — 1 기반 다지기 · 2 회원과 인증 · 3 상품과 CRUD · 4 주문 생성 · 5 취소와 환불 · 6 조회 성능(MUST), 7 구조 정리 · 8 배포와 운영 · 9 설명과 정리(SHOULD — 기한이 촉박하면 7~9부터 잘린다). milestone 안 순서는 읽기 → 개념 → 구현 → 러버덕 → 복습, `skillCodes`는 기존 카탈로그에서만 고른다. 배치는 창 하나 오늘 ~ 목표일(`19` §5, `PlanTemplatePlacement`), milestone skill | P0 | S1 | BL-CNT-03 | AC-01, AC-11 | FR-02, FR-04 |
| BL-CNT-06 | Seed 복습 카드 | 50장 이상(JAVA·SPRING·DATABASE 중심), conceptKey·reviewType·rubric | P0 | S2 | BL-CNT-03 | AC-05, AC-10 | FR-11 |
| BL-CNT-07 | Seed practice challenge | 20개(difficulty 1~4), hints 1~3단계, rubric weightBp 합 10000, expectedConcepts, transferTargets | P0 | S3 | BL-CNT-03 | AC-04, AC-12 | FR-09 |
| BL-CNT-08 | Seed diagnostic challenge | `content/challenges/diagnostic.yaml`: `purpose=DIAGNOSTIC`, 카테고리별 1개 이상, 5~10분 분량. 온보딩 진단(카테고리당 1문제·최대 5문제)의 문제 은행이라 v3에서 P1 → P0 | P0 | S3 | BL-CNT-07 | AC-11 | FR-02, FR-15 |
| BL-CNT-09 | Curated sources | `content/curated-sources.yaml`(CURATED_SOURCE 근거 ID). S3는 빈 목록 허용 | P1 | S3 | BL-CNT-01 | AC-07 | FR-14 |
| BL-CNT-10 | Prompt v1 (S3) | `challenge.generate`, `challenge.evaluate`, `hint.generate`, `review.evaluate`(`review.variant`는 BL-MEM-10 Later와 함께). system.md는 `/responses`의 `instructions`로 전달되며 JSON 스키마는 `text.format`으로 붙는다 | P0 | S3 | BL-AIP-05 | AC-04, AC-16 | FR-09, FR-10, FR-11 |
| BL-CNT-11 | Prompt v1 (S4) | `coach.review`, `coach.response-feedback` + eval case `coach-review`(BL-AIP-13 러너) | P1 | S4 | BL-AIP-05, BL-AIP-13 | AC-06, AC-19 | FR-12 |
| BL-CNT-12 | Prompt v1 (S6) | `evidence.draft` | P1 | S6 | BL-AIP-05 | AC-21 | FR-18 |
| BL-CNT-13 | Prompt v1 (S7) | `requirement.extract` | P1 | S7 | BL-AIP-05 | AC-22 | FR-19 |
| BL-CNT-14 | 1차 튜닝 | 실사용 4주 데이터로 planner 가중치·budget 계수·seed 목표·카드 품질 조정. 변경값과 근거를 ADR 또는 변경 이력에 기록 | P2 | S5 | BL-GOL-14 | AC-02, AC-03 | FR-05, FR-07 |
| BL-CNT-15 | Curated repos | `content/curated-repos.yaml`(`19` §3.8): 저장소 3개(`modulith`, `petclinic`, `restbucks` — `license: UNSPECIFIED`는 경로·줄 번호·질문만 적고 코드를 옮겨 적지 않는다)·reading 13개, `repos[].pinnedCommit` 40자 SHA + 그 커밋을 체크아웃하는 `cloneHint`(줄 번호 관리 `19` §8.4 — 줄 범위는 체크아웃한 사본에서 눈으로 확인, 추측 금지), `catalogVersion` 3. 검증 CV-80~CV-87(`ContentValidator` + `content/tools/validate_content.py`, 오류 fixture `19` §4.3). `CuratedReadingRegistry`(메모리, DB 테이블 없음 — `ContentSeeder`가 기동 시 등록, `SeedCardRegistry`와 같은 방식, `03` §3.2) | P0 | S3 | BL-CNT-01, BL-CNT-02, BL-CNT-03 | AC-28 | FR-27 |
| BL-CNT-17 | 입문 트랙 콘텐츠 | `content/role-targets/java-backend-starter.yaml`(`targetRole: JAVA_BACKEND_STARTER`, **기존 non-root skill 75개 전부**에 target 1개씩 — CV-21. MUST는 14개 안팎으로 줄이고 나머지는 SHOULD·LATER, 목표 레벨은 같은 skill의 기본 트랙 값 이하, `importance`는 그 트랙 기준으로 다시 매긴다)와 `content/plan-templates/java-backend-starter.yaml`(`templateKey: JAVA_BACKEND_STARTER_DEFAULT`, `planTitle` "Java 백엔드 입문 계획", milestone 6개 — PREPARATION 5 + CONSOLIDATION 1, `weightBp` 합 10000, 그 트랙의 MUST skill 전부 포함 — CV-33·CV-35·CV-36). `catalog.yaml`의 `files.roleTargets`·`files.planTemplates`에 추가하고 `catalogVersion` +1. **skill tree·복습 카드·challenge·curated repo는 공유한다**(같은 Java 백엔드 카탈로그) — `19` §3.3·§3.4·§10.4·§12. `content/tools/validate_content.py`와 `ContentValidator` 실패 fixture(트랙 파일 누락, MUST skill이 milestone에 없음) | P1 | S3 | BL-CNT-03, BL-CNT-04, BL-CNT-01 | AC-32, AC-01 | FR-03, FR-06 |
| BL-CNT-16 | READ_CODE 평가 수집과 소스 점검 절차 | **읽기 평가**: `ReadingFeedback`(`HELPFUL`·`TOO_HARD`·`BORING`, `04` §3), `learning_task.reading_feedback`(nullable `varchar(20)`, 값 CHECK + `learning_task_reading_feedback_type` — I-19, V9 + `database/schema.sql`), `PATCH /today/tasks/{taskId}`의 선택 필드 `readingFeedback`(`READ_CODE`를 `COMPLETED`로 바꿀 때만, 그 외 400 `VALUE_NOT_ALLOWED` — `05` §8.4), SCR-TODAY 완료 시트의 평가 칩 3개(선택, `02`), export `dailyPlans[].tasks[].readingFeedback`. 어떤 레벨·planner·budget 규칙의 입력도 아니다(`06` §5.3). **은퇴 단위 조회**: `curated-repos.yaml` `readings[].retired`(`19` §3.8·§8.2), CV-83·CV-87 은퇴 규칙(`ContentValidator` + `content/tools/validate_content.py`), `CuratedReadingRegistry`가 은퇴 reading도 보관하고 `GET /readings/{key}`가 `retired`를 돌려준다(`05` §19.7), `TaskProposalPolicy` 후보에서 은퇴 reading 제외, SCR-READ-CODE 은퇴 안내. **소스 점검 절차**(`19` §8.5): 사람이 하는 수동 점검 — 첫 점검은 S3 구현 시작 전(이 BL의 우선순위와 무관, `16` R-15), 이후 단계 회고마다·사용자 요청 시. 자동 실행·서버의 저장소 조회 없음 | P2 | S3 | BL-TDY-16, BL-CNT-15, BL-CLI-34 | AC-28 | FR-27 |

### 4.15 PRJ — Side Project

`05-api-spec.md` §19.1~§19.12가 기준이다. 사이드 프로젝트(기본 "주문 시스템")는 학습이 적용될 대상이고 DevPilot 자체와 별개다 — DevPilot 구현은 `PROJECT_TASK`·evidence 대상이 아니다.

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-PRJ-01 | Side project 도메인·API | `project` 모듈(`03` §2.1·§3.2): `SideProject` 매핑, `SideProjectStatus`(`ACTIVE`, `PAUSED`, `DONE` — `04` §3), `SideProjectRepository`, `SideProjectService`, `SideProjectQueryService`(`today`·`coach`·`onboarding`이 사용 — SP-3 "`updated_at`이 가장 최근인 `ACTIVE` 하나", `idx_side_project_user_status`), `SideProjectController`: `POST /side-projects`(201, IK), `GET /side-projects?status=&cursor=`(`updatedAt` DESC), `GET /side-projects/{sideProjectId}`, `PATCH /side-projects/{sideProjectId}`(version, 빈 문자열 → null, 공백 name 400 `NOT_BLANK_IF_PRESENT`, 값이 실제로 바뀔 때만 `updated_at`·`version`), `DELETE /side-projects/{sideProjectId}`(204, `learning_task.side_project_id`·`coach_review.side_project_id`는 `on delete set null`, 재삭제 404) — `05` §19.1~§19.6. `repoUrl`은 **저장만** 한다(URI 파싱·http/https·host 검사 → 400 `URL`, 서버 fetch 없음 — `07` §5.5). 타인 id 404 `RESOURCE_NOT_FOUND`, 온보딩 전 409 `ONBOARDING_REQUIRED`. `V9__rubberduck_project.sql` 적용 확인(`04` §10 — `side_project`는 S1부터, 러버덕 테이블은 S3에 처음 쓴다). 격리 목록(BL-SEC-05)에 추가. `name`·`description`·`stack` 마스킹은 BL-AIP-09(S3)가 연결한다. 상태 전이는 세 상태 사이 모두 허용한다(`04` §4.9, `05` §19.5) | P0 | S1 | BL-SEC-04, BL-FND-11, BL-FND-12, BL-FND-13 | AC-27, AC-08 | FR-26 |
| BL-PRJ-02 | 프로젝트 결정·장애 기록 | `project` 모듈: `SideProjectNote` 매핑(`side_project_note`, V10 — BL-FND-27), `SideProjectNoteType`(`DECISION`, `INCIDENT` — `04` §3), `SideProjectNoteRepository`, `SideProjectNoteService`, `SideProjectNoteQueryService`(`evidence`가 사용), `SideProjectNoteController`: `POST /side-projects/{id}/notes`(201, IK), `GET …/notes?noteType=&cursor=`(`occurredOn` DESC·`id` DESC), `GET …/notes/{noteId}`, `PATCH …/notes/{noteId}`(version, `noteType` 필드 없음 — PN-2), `DELETE …/notes/{noteId}`(204) — `05` §19.8~§19.12. 유형별 필수·금지 검사(I-22, 400 `VALUE_REQUIRED`/`VALUE_NOT_ALLOWED`), `occurredOn` 오늘 이하(`DATE_OUT_OF_RANGE`), 선택 `skillCode`(`SKILL_CODE_UNKNOWN`), 모든 텍스트 `SecretMasker`(PN-4, `05` §1.11 — BL-AIP-09가 연결), 부모+자식 소유 동시 확인(타인 404, `07` §4.3). `project → skill` 의존 추가(`03` §2.2). 격리 목록(BL-SEC-05)에 5개 추가. export `sideProjects[].notes[]`(BL-SEC-13). 기록은 학습 이벤트·레벨에 영향이 없다(PN-3) | P1 | S3 | BL-PRJ-01, BL-FND-27, BL-FND-12, BL-FND-13, BL-AIP-09, BL-SKL-01 | AC-33, AC-08, AC-14 | FR-29 |

### 4.16 RDK — Rubber Duck

`06-learning-engine-rules.md` §9.5(RD-1~RD-7), `05-api-spec.md` §9.5~§9.10, `17-ai-integration.md` §3.10~§3.12·§4.10·§4.11·§6.8이 기준이다. 모듈은 `learning`이다(`03` §2.1). 사용자가 설명하고 AI는 답을 주지 않고 되묻기만 한다.

| ID | 제목 | 설명 | P | Sprint | Depends on | AC | FR/NFR |
|---|---|---|---|---|---|---|---|
| BL-RDK-01 | 러버덕 도메인 | `rubberduck` 모듈(`03` §2.2·§3.2): `RubberDuckSession`·`RubberDuckTurn` 매핑(`rubber_duck_session`, `rubber_duck_turn`, V9), `RubberDuckTargetType`(`CODE_READING`, `CHALLENGE`, `REVIEW_ITEM`, `CONCEPT`, `PROJECT_WORK`), `RubberDuckStatus`(`IN_PROGRESS → COMPLETED \| ABANDONED`, 나가는 전이 없음 — `04` §4.8), I-16 `unique (session_id, turn_no)`. **`RubberDuckPolicy`**(`06` §9.5 RD-1~RD-7): 턴 상한(`devpilot.rubberduck.max-turns` 5, RD-4), RD-3 "모르겠다" 연속 `stuck-turns-before-hint`(2)턴 판정, RD-5 증거 조건(가드 전 `rawGapCount = 0`·`turns ≥ 3`, coverage 고정 `evidence-coverage-bp` 7000, 독립 = `hintDisclosed = false` — `06` §7.2), RD-7(skill 없으면 이벤트 없음). `devpilot.rubberduck.*` 설정 바인딩(`03` §9). `LearningEventType.RUBBER_DUCK_COMPLETED`·`EventSourceType.RUBBER_DUCK_SESSION`과 payload record `{ sessionId, turns, gapCount, targetType, hintDisclosed }`(`04` §6). RD-3 "모르겠다" 판정은 서버 결정적 규칙이다(`06` §9.5, `devpilot.rubberduck.dont-know-phrases`·`dont-know-max-chars` 30) — 결과를 `rubber_duck_turn.learner_stuck`에 저장하고 AI 출력에 의존하지 않는다. I-18 사용자당 `IN_PROGRESS` 1개(`uq_rubber_duck_session_one_in_progress`) | P0 | S3 | BL-FND-09, BL-FND-11, BL-SKL-03, BL-PRJ-01 | AC-26 | FR-25 |
| BL-RDK-02 | 러버덕 API | `RubberDuckService` + `RubberDuckController`(`05` §9.5~§9.10): `POST /rubber-duck`(201, IK, AI 호출·예산 검사 없음 — targetType별 계약 표: `CONCEPT`이면 `conceptKey` 필수(`ONE_OF_REQUIRED`)·`targetId` 금지(`MUTUALLY_EXCLUSIVE`), 그 외는 반대 / 대상이 타인 소유·없음 → 400 `REFERENCE_NOT_FOUND` / `skillCode` 유도 / 이전 `IN_PROGRESS` → `ABANDONED` + `abandonedSessionId`), `POST /rubber-duck/{sessionId}/turns`(201, IK: 2000자 초과 413 `CONTENT_TOO_LARGE` → `SecretMasker`(private key 422 `SECRET_DETECTED_BLOCKED`) → tx1(비 `IN_PROGRESS`·턴 상한 409 `INVALID_STATE_TRANSITION`) → AI 차단 검사(429/503) → `AiGateway` `RUBBER_DUCK` → tx2(`rubber_duck_turn` INSERT, `turn_count + 1`), AI 실패·가드 위반 시 턴 미저장, `suggestHint`(RD-3)), `POST .../complete`(200: 턴 0 → AI 없이 `ABANDONED` / `RUBBER_DUCK_SUMMARY` 1회 → `COMPLETED`, `gaps[]` → `review_item`(`source_type = RUBBER_DUCK`, `origin = AI_GENERATED`, `review_type = EXPLAIN`, 첫 due `06` §6.3, 같은 `concept_key`면 due만 당김), `skill_id`가 있으면 `RUBBER_DUCK_COMPLETED`(dedupe `RUBBER_DUCK:{sessionId}:{skillId}`) / 정리 차단·실패 → `COMPLETED` + `summarySkippedReason`, 카드·이벤트 없음), `POST .../abandon`(200, IK 무시, AI 없음), `GET /rubber-duck/{sessionId}`(turns 전체, AI 불가여도 조회). 타인 세션 404 `RESOURCE_NOT_FOUND`, 격리 목록(BL-SEC-05)에 추가. 대상 조회(`learning_task`·`challenge_attempt`·`review_item`·`side_project`)는 `rubberduck` 모듈이 `today`·`training`·`review`·`project`에 직접 의존해 한다(`03` §2.2). gap 카드 필드(skill 유도 — 세션 skill이 없으면 `conceptKey` 접두사, 그래도 없으면 카드 없음 / `expected_answer` 두 줄 목록 / `rubric_json` 1개)는 `05` §9.8. `CODE_READING` 세션이 `COMPLETED`가 되면 대상 `READ_CODE` 과제의 RC-1 조건이 충족된다 — 과제 상태는 바꾸지 않고, 완료는 `PATCH /today/tasks/{taskId}`가 한다(BL-TDY-16, `05` §9.8 7번). `summary_json.rawGapCount` 저장 | P0 | S3 | BL-RDK-01, BL-RDK-03, BL-AIP-07, BL-AIP-09, BL-AIP-10, BL-MEM-07, BL-SKL-03, BL-TDY-01, BL-TDY-10, BL-TRN-05 | AC-26, AC-08, AC-14, AC-23 | FR-25 |
| BL-RDK-03 | 러버덕 AI 계약 | prompt `rubber.duck/v1`·`rubber.duck.summary/v1`(`17` §3.11·§3.12 변수·절삭 표), operation 설정 `RUBBER_DUCK`(SYNC, thinking off, max-tokens 1500, timeout 20s, 재시도 0, input-token-budget 6000)·`RUBBER_DUCK_SUMMARY`(SYNC, thinking on·effort low, 4000, 30s, 0, 8000)(`03` §9), 출력 record `RubberDuckTurnOutput`(`question` 10~200자, `targetsGap`은 저장·응답·다음 프롬프트에 넣지 않음)·`RubberDuckSummaryOutput`(`gaps` ≤ 3, `confirmed` ≤ 5, `overallNote`)과 스키마 파일(`17` §4.10·§4.11, `OutputSchemaRegistry` 11종), **`NoAnswerGuard`**(`17` §6.8: NA-1 `question`이 `?`로 끝남, NA-2 정답 단정 표현 `devpilot.ai.guards.no-answer-phrases`, NA-3 코드 검출, NA-4 summary gap 제거 — 필드별 규칙 하나)를 `OutputGuardChain`(`CODE_LEAK` 다음, `LANGUAGE` 앞)에 추가하고 `GuardName.NO_ANSWER`, gap `conceptKey` 접두사 `SkillCodeGuard`(알 수 없으면 그 gap만 버림), `FakeAiProvider` fixture(정상·NA-1~NA-4 위반·timeout·gaps 0개/2개), eval case `rubber-duck`·`rubber-duck-summary`(`17` §12.3~§12.5, 러너 BL-AIP-13). prompt·가드를 바꾸면 eval 실행 | P0 | S3 | BL-AIP-05, BL-AIP-06, BL-AIP-08, BL-AIP-13 | AC-26 | FR-25, NFR-07 |
| BL-RDK-05 | 재현 잠금 (러버덕 시작) | `RubberDuckService.start`가 `learning.application.RedoLockProvider`(구현 `today`, BL-TDY-18)로 확인한다(`05` §9.6 3단계): `targetType = CHALLENGE`면 그 attempt의 `challenge_id`, `PROJECT_WORK`면 그 `side_project_id`에 열려 있는 `REDO` 과제가 있으면 409 `AI_ASSIST_LOCKED_FOR_REDO` — 세션을 만들지 않고 이전 `IN_PROGRESS` 세션도 건드리지 않는다. `CODE_READING`·`REVIEW_ITEM`·`CONCEPT`은 잠그지 않는다. 이미 시작한 세션의 턴 제출은 막지 않는다(잠금은 시작에만 걸린다). `rubberduck → learning` 의존은 이미 있다(`03` §2.2) | P1 | S4 | BL-RDK-02, BL-TDY-18 | AC-31, AC-26 | FR-28, FR-25 |
| BL-RDK-04 | `StaleRubberDuckJob` | `rubberduck` 모듈 스케줄 job: `IN_PROGRESS`이고 `started_at < now − devpilot.rubberduck.stale-after`(24h)인 세션 → `ABANDONED`, `completed_at = now`(`idx_rubber_duck_session_in_progress`). 정리 AI 호출·복습 카드·학습 이벤트 없음, 턴 기록은 남는다(`05` §9.5). BL-FND-25 공통 규약(INFO 1줄, `JOB_FAILED` WARN, 사용자 단위 트랜잭션). 스케줄 `0 25 * * * *`(매시 25분, `03` §6) | P1 | S3 | BL-RDK-01, BL-FND-25 | AC-26 | FR-25 |

---

## 5. Sprint 요약

단계는 §2의 묶음을 따른다: **M1 = S0~S3**(S3 완료 = 실사용 시작), **M2 = S4~S7**(단계 순서는 잠정), Later는 M1·M2 밖이다.

### 5.1 Epic × Sprint 항목 수

`~~취소~~` 표시 6개(`BL-SEC-01`, `BL-SEC-02`, `BL-OPS-03`, `BL-OPS-08`, `BL-OPS-14`, `BL-AIP-04`)는 P·Sprint가 비어 있어 표에서 빠진다. `BL-SEC-18`(공개 인증 도입, P0)은 Sprint가 아니라 **공개 배포 게이트**에 걸려 있어 Sprint 열에 넣지 않는다(`11` §3.10).

§4 본문 행 243개 − 취소 6개 − 게이트 1개(`BL-SEC-18`) = **표 합계 236개**(M1 169개 · M2 53개 · Later 14개).

| Epic | S0 | S1 | S2 | S3 | **M1 계** | S4 | S5 | S6 | S7 | **M2 계** | Later | 합계 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| FND | 8 | 9 | 2 | 5 | 24 | 1 | 1 | — | 1 | 3 | — | 27 |
| SEC | 5 | 4 | 2 | — | 11 | 1 | — | 2 | — | 3 | 1 | 15 |
| OPS | 9 | — | 4 | 1 | 14 | — | — | 3 | — | 3 | 2 | 19 |
| CLI | 3 | 9 | 7 | 9 | 28 | 2 | 2 | 2 | 1 | 7 | 2 | 37 |
| GOL | — | 6 | 9 | 1 | 16 | — | — | — | — | — | 2 | 18 |
| SKL | — | 2 | 1 | 3 | 6 | 1 | 1 | — | — | 2 | — | 8 |
| TDY | — | — | 11 | 2 | 13 | 2 | 2 | — | — | 4 | 1 | 18 |
| MEM | — | — | 8 | 2 | 10 | — | — | — | — | — | 2 | 12 |
| TRN | — | — | — | 13 | 13 | 1 | 1 | — | — | 2 | 2 | 17 |
| AIP | — | 1 | — | 14 | 15 | — | 1 | — | — | 1 | — | 16 |
| COA | — | — | — | — | — | 10 | — | — | — | 10 | 1 | 11 |
| EVD | — | — | — | — | — | — | 4 | 5 | — | 9 | — | 9 |
| REQ | — | — | — | — | — | — | — | — | 4 | 4 | 1 | 5 |
| CNT | — | 5 | 1 | 7 | 13 | 1 | 1 | 1 | 1 | 4 | — | 17 |
| PRJ | — | 1 | — | 1 | 2 | — | — | — | — | — | — | 2 |
| RDK | — | — | — | 4 | 4 | 1 | — | — | — | 1 | — | 5 |
| **합계** | **25** | **37** | **45** | **62** | **169** | **20** | **13** | **13** | **7** | **53** | **14** | **236** |

### 5.2 Sprint × Priority 항목 수

| 단계 | P0 | P1 | P2 | P3 | 합계 |
|---|---:|---:|---:|---:|---:|
| S0 | 25 | — | — | — | 25 |
| S1 | 34 | 3 | — | — | 37 |
| S2 | 39 | 5 | 1 | — | 45 |
| S3 | 46 | 14 | 2 | — | 62 |
| **M1 계 (S0~S3)** | **144** | **22** | **3** | — | **169** |
| S4 | — | 20 | — | — | 20 |
| S5 | — | 11 | 2 | — | 13 |
| S6 | 1 | 12 | — | — | 13 |
| S7 | — | 6 | 1 | — | 7 |
| **M2 계 (S4~S7)** | **1** | **49** | **3** | — | **53** |
| Later | — | — | — | 14 | 14 |
| **합계** | **145** | **71** | **6** | **14** | **236** |

- S3가 가장 무겁다(62개). S3 완료가 실사용 시작이므로 S3의 P0 46개는 이월하지 않는다. S3의 P1·P2 **16개**(P1 14 + P2 2) 중 `BL-AIP-13`(Evals v1)은 P0 `BL-RDK-03`의 선행이라 S3에 남긴다. **학습 트랙 6개**(`BL-FND-27`은 P0라 제외 — `BL-GOL-18`, `BL-CNT-17`, `BL-CLI-36`, `BL-TRN-16`)와 **프로젝트 기록 2개**(`BL-PRJ-02`, `BL-CLI-35`)는 P1이지만 이월하면 실사용 시작의 의미가 줄어든다(두 번째 학습자가 시작하지 못하고, 프로젝트 초기 결정·장애가 기록되지 않는다) — **가장 마지막에** 이월을 검토한다. 이월 순서는 `BL-FND-26` → `BL-CNT-16` → `BL-CLI-22` → `BL-SKL-06` → `BL-CNT-09` → `BL-MEM-09` → `BL-CLI-14` → `BL-FND-24` → `BL-RDK-04` → `BL-TRN-16` → `BL-CLI-35` → `BL-PRJ-02` → `BL-CLI-36` → `BL-CNT-17` → `BL-GOL-18`다. `BL-CNT-16`을 이월해도 첫 소스 점검(`19` §8.5)은 S3 시작 전에 한다(`16` R-15) — 이월되는 것은 읽기 평가·은퇴 단위 조회 구현이다. `BL-MEM-10`은 REVIEW_VARIANT 축소로 P3·Later라 대상이 아니다.
- S2가 끝나면 AI 없이 Today·Review·budget·risk·replan(축소·확장 제안)을 먼저 쓸 수 있다(선택, §2).
- M2(S4~S7)의 단계 순서는 잠정이다. 순서를 바꾸면 이 표를 다시 센다.
- 항목 수는 작업량이 아니다. 작업량 추정과 재추정 규칙은 `11-development-roadmap.md` §2.

---

## 6. 커버리지 색인

설계 문서의 구성 요소가 모두 백로그에 있는지 확인하는 색인이다. 새 endpoint·job·규칙 클래스를 추가하면 이 표도 갱신한다.

### 6.1 API endpoint (resource별)

| Resource | Endpoint | BL |
|---|---|---|
| health | `GET /actuator/health` | BL-FND-03 |
| me | `GET /me`, `PATCH /me` | BL-FND-21 (aiStatus·aiUsage: BL-AIP-10) |
| me | `GET /me/export` | BL-SEC-13 |
| me | `DELETE /me` | BL-SEC-14 |
| calendar | `POST /me/calendar-token`, `GET /calendar/{token}.ics` | BL-TDY-13 |
| onboarding | `POST /onboarding` | BL-GOL-06 (`sideProject` BL-PRJ-01, seed 카드 BL-MEM-08, snapshot BL-GOL-13, `suggestedDiagnostics` BL-TRN-13, `learningGoal.targetRole` 선택 BL-GOL-18) |
| diagnostics | `GET /diagnostics/suggestions` | BL-TRN-13 (트랙별 범위 BL-TRN-16) |
| learning-goal | `GET /learning-goal`, `PUT /learning-goal` | BL-GOL-01 (트랙 변경 차단 BL-GOL-18) |
| skills | `GET /skills/tree` | BL-SKL-01 (`role` 기본값·트랙별 roleTarget BL-GOL-18) |
| skills | `GET /skills/me` | BL-SKL-02 |
| skills | `GET /skills/{skillId}/history` | BL-SKL-06 |
| plans | `GET /plans/active`, `GET /plans`, `GET /plans/{planId}` | BL-GOL-02 |
| plans | `POST /plans` | BL-GOL-16 (Later, 생성 서비스는 BL-GOL-03) |
| plans | `PATCH /plans/{planId}/milestones/{milestoneId}` | BL-GOL-04 |
| plans | `POST /plans/{planId}/replan/preview` | BL-GOL-12 (`expansionSuggestions` BL-GOL-17) |
| plans | `POST /plans/{planId}/replan` | BL-GOL-05 (S1), BL-GOL-07 (S2), BL-GOL-13 (S2), BL-GOL-17 (S2, `acceptedTargetRaises`) |
| plans | `GET /plans/active/budget` | BL-GOL-11 |
| today | `POST /today/generate` | BL-TDY-07, BL-TDY-14, BL-TDY-16, BL-TDY-17 (재현 과제 제안), BL-GOL-18 (트랙 기본값) |
| today | `GET /today` | BL-TDY-08 |
| today | `PATCH /today/tasks/{taskId}` | BL-TDY-09 (`READ_CODE` 완료 조건 RC-1 BL-TDY-16, 읽기 평가 `readingFeedback` BL-CNT-16, 재현 결과 `redoWithoutAi` BL-TDY-18) |
| learning-sessions | `POST /learning-sessions`, `POST .../complete`, `POST .../abandon`, `GET /learning-sessions` | BL-TDY-10 |
| rubber-duck | `POST /rubber-duck`, `POST /rubber-duck/{sessionId}/turns`, `POST /rubber-duck/{sessionId}/complete`, `POST /rubber-duck/{sessionId}/abandon`, `GET /rubber-duck/{sessionId}` | BL-RDK-02 (AI 계약 BL-RDK-03, 재현 잠금 BL-RDK-05) |
| challenges | `GET /challenges`, `GET /challenges/{challengeId}` | BL-TRN-02 |
| challenges | `POST /challenges/generate` | BL-TRN-04 |
| challenges | `POST /challenges/{challengeId}/attempts` | BL-TRN-05 |
| challenge-attempts | `GET /challenge-attempts/{attemptId}`, `POST .../abandon` | BL-TRN-05 |
| challenge-attempts | `POST .../self-explanation` | BL-TRN-06 |
| challenge-attempts | `POST .../hints` | BL-TRN-08 (재현 잠금 HL-9 BL-TRN-17) |
| challenge-attempts | `POST .../submissions` | BL-TRN-09 |
| challenge-attempts | `POST .../submissions/{submissionNo}/retry` | BL-TRN-10 |
| reviews | `GET /reviews/due` | BL-MEM-05 (출제 순서 RV-INTERLEAVE BL-MEM-12) |
| reviews | `POST /reviews/{reviewItemId}/answer` | BL-MEM-06 (evaluate: BL-MEM-09) |
| review-items | `GET /review-items`, `POST /review-items`, `PATCH /review-items/{reviewItemId}` | BL-MEM-07 |
| coach/reviews | `POST /coach/reviews` | BL-COA-02 |
| coach/reviews | `GET /coach/reviews/{reviewId}`, `GET /coach/reviews` | BL-COA-04 |
| coach/reviews | `POST .../retry` | BL-COA-05 |
| coach/reviews | `POST .../findings/{findingId}/responses` | BL-COA-06 |
| coach/reviews | `POST .../findings/{findingId}/hints` | BL-COA-07 |
| coach/reviews | `PATCH .../findings/{findingId}` | BL-COA-08 |
| coach/reviews | `POST .../complete` | BL-COA-09 |
| coach/reviews | `DELETE .../content` | BL-COA-10 |
| dashboard | `GET /dashboard` | BL-TDY-11 (S2), BL-TDY-12 (S5) |
| evidence | `GET /evidence`, `GET /evidence/{evidenceId}`, `POST /evidence` | BL-EVD-05 |
| evidence | `POST /evidence/drafts` | BL-EVD-06 (`sourceProjectNoteId` BL-EVD-09) |
| evidence | `PATCH /evidence/{evidenceId}`, `POST .../accept`, `POST .../reject` | BL-EVD-07 |
| evidence | `GET /evidence/export` | BL-EVD-08 |
| weekly-reviews | `GET /weekly-reviews`, `GET /weekly-reviews/{weekStartDate}`, `PUT .../reflection` | BL-EVD-03 |
| thinking-patterns | `GET /thinking-patterns/trend` | BL-EVD-04 |
| requirement-docs | `POST /requirement-docs` | BL-REQ-02 |
| requirement-docs | `GET /requirement-docs/{requirementDocId}`, `GET /requirement-docs`, `DELETE /requirement-docs/{requirementDocId}` | BL-REQ-03 |
| side-projects | `POST /side-projects`, `GET /side-projects`, `GET /side-projects/{sideProjectId}`, `PATCH /side-projects/{sideProjectId}`, `DELETE /side-projects/{sideProjectId}` | BL-PRJ-01 |
| side-projects | `POST /side-projects/{sideProjectId}/notes`, `GET …/notes`, `GET …/notes/{noteId}`, `PATCH …/notes/{noteId}`, `DELETE …/notes/{noteId}` | BL-PRJ-02 |
| readings | `GET /readings/{readingKey}` | BL-TDY-16 (은퇴 단위 `retired` BL-CNT-16) |

### 6.2 스케줄 job · 비동기 task (`03-system-architecture.md` §5.3, §6)

| 구성 요소 | 종류 | BL |
|---|---|---|
| `ProgressSnapshotJob` | job | BL-GOL-14 |
| `WeeklyReviewJob` | job | BL-EVD-02 |
| `CoachContentPurgeJob` | job | BL-COA-10 |
| `RetentionCleanupJob` (+ port `RetentionCleanupTarget`) | job | BL-FND-24, BL-REQ-04 |
| `AccountDeletionJob` | job | BL-SEC-17 (Later, 삭제 요청은 BL-SEC-14) |
| `OrphanAsyncTaskJob` (+ port `OrphanAsyncTaskSweeper`) | job | BL-FND-23 |
| `StaleRubberDuckJob` | job | BL-RDK-04 |
| `CoachAnalysisTask` | async | BL-COA-03 |
| `ChallengeGenerationTask` | async | BL-TRN-04 |
| `SubmissionEvaluationTask` | async | BL-TRN-09 |
| `ReviewVariantTask` | async | BL-MEM-10 |
| `EvidenceDraftTask` | async | BL-EVD-06 |
| `RequirementAnalysisTask` | async | BL-REQ-02 |

### 6.3 도메인 규칙 클래스 (`03-system-architecture.md` §3.2 굵은 이름)

| 클래스 | `06` 절 | BL | 구현 주체 |
|---|---|---|---|
| `PlanningLevelPolicy` | §7.5 | BL-SKL-02 | 에이전트 |
| `SkillLevelRules` | §7.1~7.4 | BL-SKL-04 (독립 구현 증거 BL-SKL-08) | 에이전트 |
| `StudyBudgetCalculator` | §3 | BL-GOL-08 | 에이전트 |
| `DeadlineRiskEvaluator` | §4.1~4.3 | BL-GOL-09 | 에이전트 |
| `ReplanSuggestionPolicy` | §4.4 (1~5단계 축소, 6단계 확장) | BL-GOL-10, BL-GOL-17 | 에이전트 |
| `PlanTemplatePlacement` | §11.3 → `19-content-spec.md` §5 | BL-GOL-03 | 에이전트 |
| `PlannerScoring` | §5.4~5.5, §5.7 | BL-TDY-02 | 에이전트 |
| `TaskProposalPolicy` | §5.3 | BL-TDY-03 (`READ_CODE` 분기 BL-TDY-16, `REDO` 분기·트랙 기본값 BL-TDY-17·BL-GOL-18) | 에이전트 |
| `RedoTaskPolicy` | §5.10 (RE-1~RE-8) | BL-TDY-17 (완료·잠금 BL-TDY-18) | 에이전트 |
| `TimeAllocator` | §5.6 | BL-TDY-04 | 에이전트 |
| `ReasonTemplates` | §5.8 | BL-TDY-05 | 에이전트 |
| `ComebackModePolicy` | §5.5 | BL-TDY-06 | 에이전트 |
| `FinalRatingPolicy` | §6.1 | BL-MEM-02 | 에이전트 |
| `RuleBasedV1Scheduler` | §6.2, §6.6 | BL-MEM-03 | 에이전트 |
| `DueReviewSelector` | §6.5 (RV-INTERLEAVE §6.5·§6.6) | BL-MEM-04, BL-MEM-12 | 에이전트 |
| `HintLadderPolicy` | §9.1~9.2 | BL-TRN-07 (HL-9 BL-TRN-17) | 에이전트 |
| `RubberDuckPolicy` | §9.5 (RD-1~RD-7) | BL-RDK-01 | 에이전트 |
| `RubricScorer`, `AttemptOutcomeCalculator` | §8.1~8.2 | BL-TRN-11 | 에이전트 |
| `DiscoveredByResolver` | §9.3 | BL-COA-09 | 에이전트 |
| `MetricsCalculator` | §12 | BL-EVD-01 (`projectNoteCount`·`independentRedoCount` 포함) | 에이전트 |
| `RequirementFitClassifier` | §13 | BL-REQ-01 | 에이전트 |
| `SelfAssessmentPropagation` (굵게 아님) | — (`05-api-spec.md`) | BL-GOL-06 | 에이전트 |

### 6.4 AI platform (`03-system-architecture.md` §3.3)

| 구성 요소 | BL |
|---|---|
| `AiProvider`, `AiRequest`, `AiResult`, `AiOperation`, `AiCallStatus`, `FakeAiProvider`, `DisabledAiProvider` | BL-AIP-01 |
| `DevPilotProperties.ai` | BL-AIP-02 |
| `DeepSeekAiProvider`(잔액 조회 `checkBalance` 포함) | BL-AIP-03 |
| `PromptRegistry` | BL-AIP-05 |
| `OutputSchemaRegistry`, `integration.ai.api.output` record 9종, 전용 `ObjectMapper` | BL-AIP-06 |
| `AiOperation.RUBBER_DUCK`·`RUBBER_DUCK_SUMMARY`(operation 설정, prompt `rubber.duck`·`rubber.duck.summary`), 출력 record `RubberDuckTurnOutput`·`RubberDuckSummaryOutput`(합계 11종) | BL-RDK-03 |
| `AiGateway`, `AiCallLog`, `AiCallLogRepository`, `AiCallLogWriter`(REQUIRES_NEW) | BL-AIP-07 |
| `OutputGuardChain` (6종) | BL-AIP-08 |
| `NoAnswerGuard` (`OutputGuardChain`에 추가, 러버덕 전용 NA-1~NA-4) | BL-RDK-03 |
| `SecretMasker` | BL-AIP-09 |
| `AiBudgetGuard` | BL-AIP-10 |
| `AiBalanceMonitor`, `AiBalanceCheckJob` (`integration.ai.budget`) | BL-AIP-17 |
| `AiCallLogRetentionService` (`common.job.RetentionCleanupTarget` 구현) | BL-FND-24 |
| `AiCostCalculator` | BL-AIP-11 |
| AI ArchUnit 규칙 | BL-AIP-12 |
| evals (`rubber-duck` case BL-RDK-03, `coach-review` case BL-COA-03·BL-CNT-11) | BL-AIP-13 |
