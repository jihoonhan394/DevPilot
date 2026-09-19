# 03. System Architecture

> Status: Accepted (v2) · Last updated: 2026-09-18 · Related: ADR-002, ADR-005, ADR-011, ADR-012, ADR-014, ADR-019
>
> 이 문서는 **모듈 경계, 패키지와 핵심 클래스 이름, 요청 처리 순서, 트랜잭션/비동기 규칙, 설정값 전체 목록**을 정의한다. 여기 적힌 이름은 구현에서 그대로 쓴다. 이름을 바꿀 때는 이 문서도 함께 수정한다.

---

## 1. 시스템 컨텍스트

```text
┌───────────────────────────┐
│ Flutter Web (PWA)         │  현재 배포 기준(임시): Tailscale 앱이 설치된 기기에서만 접근
│ (Android: Later)          │
└─────────────┬─────────────┘
              │ HTTPS  https://<tailnet-host>  /api/v1/*  Bearer JWT
              ▼
┌──────────────────────────────────────────────────────────────────┐
│ 자체 서버 (Rocky Linux 9, x86_64, 공용 Docker 호스트)               │
│  tailscaled: `tailscale serve --https=443 → 127.0.0.1:18080`     │
│  ┌──────────────────┐        ┌────────────────────────────────┐  │
│  │ Caddy            │──/api─▶│ devpilot-api (Spring Boot 4.1) │  │
│  │ 127.0.0.1:18080  │        │  - JWT 검증 (devtoken 공개키    │  │
│  │ 정적 웹 제공      │        │    또는 Supabase JWKS, Later)   │  │
│  └──────────────────┘        │  - 도메인 규칙 (결정적)          │  │
│                              │  - AI 호출 (트랜잭션 밖, 비동기)  │  │
│                              └───────┬───────────────┬────────┘  │
│                                      │ JDBC          │            │
│                              ┌───────▼────────────┐  │            │
│                              │ PostgreSQL 16      │  │            │
│                              │ (공용 인스턴스,      │  │            │
│                              │  DB/role devpilot) │  │            │
│                              └────────────────────┘  │            │
└──────────────────────────────────────────────────────┼────────────┘
                                                       │ HTTPS
                                                       ▼
                                             ┌──────────────────┐
                                             │ DeepSeek API     │
                                             │ deepseek-flash   │
                                             └──────────────────┘
```

| 결정 | 내용 |
|---|---|
| 배포 단위 | 단일 Spring Boot 애플리케이션(modular monolith), 단일 인스턴스 |
| 노출 | **현재 배포 기준(임시)**: tailnet 전용. TLS는 `tailscale serve`가 종단하고 Caddy는 `127.0.0.1:18080`에만 바인딩(`10` §2.3). 공개 도메인·ACME 없음. 공개 배포(자체 서버 웹 공개 또는 OCI Always Free)는 별도 게이트이고 선행 조건은 **실제 신원 확인 인증**(GitHub OAuth 직접 또는 Supabase Auth) + ACME TLS다(`11` §3.10, `13` BL-SEC-18) |
| 웹 제공 | Caddy가 Flutter web 정적 파일과 `/api`를 **같은 origin**으로 제공 → 운영 CORS 불필요 |
| 인증 | `devpilot.security.auth-mode=devtoken`(현재 기본, 운영 포함): backend가 EC P-256 키로 서명한 JWT를 `POST /api/v1/dev/token`으로 발급하고 같은 키로 검증(§4.2). **비공개 네트워크 배포 전용**이다 — tailnet 밖에서 접근할 수 없다는 전제 하나로만 안전하다. 공개 노출 전에 `supabase` 모드(Supabase Auth JWT를 JWKS로 검증)로 전환한다 |
| DB 접근 | backend만 JDBC로 접근(서버의 공용 PostgreSQL 16, `devpilot` DB/role, TLS 없음 — 현재는 같은 호스트이거나 Tailscale 구간 암호화). 클라이언트의 DB 직접 접근 없음 |
| AI | backend만 DeepSeek을 호출(Spring `RestClient`, `POST /responses`). 클라이언트에는 API key 없음. 다른 공급자는 `AiProvider` 구현 추가로 교체 |
| 메시지 큐 · 캐시 서버 | 사용하지 않음 (ADR-002, ADR-012) |

---

## 2. 모듈 구조

### 2.1 모듈 목록과 책임

Base package: `com.devpilot`

| 모듈 (package) | 책임 | 소유 테이블 |
|---|---|---|
| `common` | 오류 처리, 보안 설정, 웹 필터, 시간, 설정 바인딩, JPA 기반 클래스, idempotency, 비동기 실행기 | `idempotency_record` |
| `user` | 사용자 식별·프로비저닝, 프로필, 계정 삭제, 캘린더 피드 (export는 `account`) | `app_user` |
| `goal` | 학습 목표(학습 완료 목표일·중간 점검일), 집중 skill | `learning_goal`, `learning_goal_focus_skill` |
| `skill` | skill catalog 조회, 사용자 skill state, **skill 레벨 갱신 규칙** | `skill`, `skill_prerequisite`, `role_skill_target`, `user_skill_state`, `skill_state_change` |
| `learning` | 학습 세션, **학습 이벤트 기록**, **Hint Ladder** | `learning_session`, `learning_event`, `hint_disclosure` |
| `plan` | 계획·milestone·버전, 계획별 skill 목표, **study budget, deadline risk**, replan, 진행 스냅샷 | `learning_plan`, `plan_milestone`, `milestone_skill`, `plan_skill_target`, `plan_progress_snapshot` |
| `review` | 복습 항목·응답, **복습 스케줄 규칙** | `review_item`, `review_answer` |
| `today` | 일일 계획, **planner scoring**, task 상태 | `daily_plan`, `learning_task` |
| `training` | challenge 생성·검증·조회, attempt, 제출·평가, outcome 계산, 진단 challenge | `challenge`, `challenge_skill`, `challenge_attempt`, `challenge_submission` |
| `coach` | 코드 리뷰 요청, 분석, finding 응답, thinking pattern 기록, 원문 purge | `coach_review`, `coach_finding`, `thinking_pattern_observation` |
| `evidence` | evidence 후보·승인·export, 주간 리뷰 | `evidence_candidate`, `weekly_review` |
| `radar` | 요구 역량 비교: 요구사항 문서 분석, 요구사항 항목 매칭 | `requirement_doc`, `requirement_item` |
| `onboarding` | 온보딩 일괄 처리 (user, goal, plan, skill 조합) | (없음) |
| `dashboard` | 읽기 전용 집계 | (없음) |
| `account` | 사용자 데이터 export (전 모듈 읽기 집계) | (없음) |
| `project` | 사이드 프로젝트 등록·조회 (학습이 적용될 대상) | `side_project` |
| `rubberduck` | **러버덕 대화**(설명 → AI 질문), 정리, gap → 복습 카드. 여러 모듈의 대상을 읽으므로 독립 모듈로 둔다(순환 방지) | `rubber_duck_session`, `rubber_duck_turn` |
| `content` | seed YAML 적재·검증 | (seed 대상 테이블에 upsert) |
| `integration.ai` | AI port, 공급자 구현, 프롬프트 로딩, 출력 가드, 비용 로그, 예산 가드, secret masking | `ai_call_log` |

### 2.2 모듈 의존 규칙

화살표는 "의존한다(호출 가능)"를 뜻한다. **표에 없는 의존은 금지**이며 ArchUnit으로 검사한다(`09-test-and-quality.md` §7).

| 모듈 | 의존 가능 대상 |
|---|---|
| `common` | (없음) |
| `integration.ai` | `common` |
| `user` | `common`, `integration.ai` (`AiStatus`·사용량 조회) |
| `learning` | `common`, `integration.ai` |
| `skill` | `common`, `learning` |
| `goal` | `common`, `skill` |
| `plan` | `common`, `goal`, `skill` |
| `review` | `common`, `skill`, `learning`, `goal` (horizon), `plan` (skill priority), `integration.ai` |
| `training` | `common`, `skill`, `learning`, `review`, `integration.ai` |
| `today` | `common`, `plan`, `skill`, `review`, `learning`, `training`, `goal` (focus skill → `projectNeed`, `06` §5.4), `project` (PROJECT_TASK 대상), `integration.ai` (`AiStatus`). `CuratedReadingRegistry`는 today 모듈이 갖고 content 모듈이 기동 시 등록한다(`SeedCardRegistry`와 같은 방식) |
| `coach` | `common`, `skill`, `learning`, `review`, `project`(리뷰 대상 프로젝트), `integration.ai` |
| `evidence` | `common`, `skill`, `learning`, `coach`, `training`, `review`, `plan` (지표 입력), `integration.ai` |
| `radar` | `common`, `skill`, `evidence`, `goal` (target role), `plan` (활성 plan의 `plan_skill_target`, `06` §13), `integration.ai` |
| `onboarding` | `common`, `user`, `goal`, `plan`, `skill`, `review` (seed 카드 배정), `training`, `project` (첫 사이드 프로젝트 생성) |
| `dashboard` | `common`, `user`, `plan`, `skill`, `review`, `today`, `learning`, `coach`, `evidence` (`MetricsCalculator` — 약한 thinking 축), `integration.ai` |
| `project` | `common` |
| `rubberduck` | `common`, `integration.ai`, `skill`(대상 skill), `learning`(학습 세션 id, `RUBBER_DUCK_COMPLETED` 기록, `hintDisclosed` 조회), `review`(gap → 복습 카드, `REVIEW_ITEM` 대상), `training`(`CHALLENGE` 대상), `today`(`CODE_READING` 대상 task·RC-1 완료, reading 조회), `project`(`PROJECT_WORK` 대상). **다른 모듈은 rubberduck에 의존하지 않는다**(account 모듈의 export 집계만 예외) |
| `content` | `common`, `skill`, `plan`, `review`, `training`, `today` (`CuratedReadingRegistry` 등록) |
| `account` | `common`, `user`, `goal`, `plan`, `skill`, `learning`, `review`, `today`, `training`, `coach`, `evidence`, `radar`, `project`, `rubberduck` (데이터 export 전용 집계) |

이 표는 순환이 없음을 확인했다(위상 정렬, 의존 대상이 앞: common → integration.ai → user → learning → skill → goal → plan → review → training → project → today → coach → evidence → radar → onboarding → dashboard → rubberduck → content → account). 여러 모듈이 같이 쓰는 순수 규칙(`ComebackModePolicy`: today·review, `RubricScorer`: training·review)은 둘 다 의존할 수 있는 `learning.domain`에 둔다(§3.2).

**역방향 입력은 port(interface)로 받는다.** 의존 방향상 직접 조회할 수 없는 규칙 입력은 입력이 필요한 모듈이 `application`에 interface를 정의하고, 데이터를 가진 모듈이 구현한다.

| Port (정의 모듈) | 구현 모듈 | 제공 데이터 |
|---|---|---|
| `common.time.UserTimeSettingsProvider` | `user` | 사용자 timezone, dayStartHour |
| `integration.ai.api.AiPendingJobCounter` | `coach`, `training`, `review`, `evidence`, `radar` | 사용자의 PENDING/RUNNING 비동기 AI 작업 수 |
| `plan.application.StudyHistoryProvider` | `today` | plan-day별 `available_minutes`(daily_plan)와 COMPLETED 세션 `actual_minutes` 합계 (`06` §3.3 completion rate) |
| `evidence.application.RequirementCoverageProvider` | `radar` | 최근 분석한 요구사항 문서 5개의 REQUIRED 요구사항 수와 READY 수 (`06` §12 `requirementCoverageBp`) |
| `common.job.RetentionCleanupTarget` | `integration.ai`(`log.AiCallLogRetentionService`), `radar`(`application.RequirementDocPurgeService`) | 보존 기간이 지난 소유 데이터의 삭제·purge 실행과 건수. `dailySummary(day)`(기본 구현 빈 map)로 일일 요약 항목도 같이 제공한다 (§6, §8) |
| `common.job.OrphanAsyncTaskSweeper` | `coach`, `training`, `review`(Later), `evidence`, `radar` | 지정 시각보다 오래 갱신되지 않은 `PENDING`/`RUNNING` 작업을 `FAILED(INTERRUPTED)`로 전이하고 건수를 돌려준다 (§5.3, §6) |

규칙:
1. 다른 모듈을 쓸 때는 그 모듈의 **`application` 패키지에 있는 public 서비스와 DTO**, 그리고 **`domain` 패키지의 enum과 불변 record(이벤트 포함, entity 제외)**만 쓴다. 다른 모듈의 entity와 `infrastructure` repository는 직접 쓰지 않는다.
2. 역방향 알림이 필요하면 **Spring application event**를 쓴다. 예: `learning`이 `LearningEventRecorded`를 발행하고 `skill`이 구독한다. `learning`은 `skill`을 모른다.
3. `common.security`는 사용자 조회가 필요하므로 `common`에 interface `AuthenticatedUserResolver`를 정의하고 `user`가 구현한다. 의존 방향은 `user → common`이다.
4. `common`의 스케줄 job(`RetentionCleanupJob`, `OrphanAsyncTaskJob`, §6)도 같은 방식이다. job 본체는 `common.job`에 두고, 실제 삭제·purge·전이는 위 두 port의 구현을 **`List<T>`로 주입받아** 호출한다. `common`은 도메인 모듈을 알지 못하고(표의 `common` 행은 "(없음)" 그대로), 구현이 하나도 없는 Sprint에서는 job이 자기 소유 데이터(`idempotency_record`)만 정리한다.

### 2.3 모듈 내부 패키지

```text
com.devpilot.<module>
├── presentation     # @RestController, request/response record, 요청 검증
├── application      # use case 서비스(@Service), 트랜잭션 경계, 소유권 확인, 다른 모듈에 공개하는 query 서비스
├── domain           # entity(@Entity 허용), 값 객체, 도메인 규칙(순수 Java), enum
└── infrastructure   # Spring Data repository, 외부 연동 어댑터, 스케줄 job
```

- JPA entity는 `domain`에 둔다. 매핑 annotation만 허용하고, Spring bean 주입이나 repository 호출은 금지한다.
- **도메인 규칙 클래스는 Spring과 JPA에 의존하지 않는 순수 Java**로 만든다. 예: `PlannerScoring`, `ReviewScheduler`, `SkillLevelRules`. 입력은 불변 record, 출력도 record다. 이 클래스들이 test vector로 검증된다.
- 빈 패키지는 만들지 않는다.

---

## 3. 핵심 클래스 (이름 고정)

### 3.1 common

| 클래스 | 역할 |
|---|---|
| `common.config.DevPilotProperties` | `devpilot.*` 설정 루트 (`@ConfigurationProperties`, record) — §9 |
| `common.time.ClockConfig` | `Clock` bean (UTC). 테스트에서 `MutableClock`으로 교체 |
| `common.time.PlanDayCalculator` | `planDate(Instant, ZoneId, dayStartHour)`, `planDayStart(LocalDate, ZoneId, dayStartHour)` |
| `common.time.UserTimeSettingsProvider` | interface: 사용자 id → `(ZoneId, dayStartHour)`. `user` 모듈이 구현. 요청 밖(비동기 task, job, 예산 가드)에서 사용 |
| `common.error.ErrorCode` | enum: HTTP status, title, 메시지 key (`05-api-spec.md` §1.3 카탈로그와 1:1) |
| `common.error.DevPilotException` | 추상 예외: `ErrorCode`, `Map<String,Object> args`, `List<FieldError> errors` |
| `common.error.{NotFoundException, ConflictException, BusinessValidationException, ForbiddenException, PayloadTooLargeException, TooManyRequestsException}` | 구체 예외 |
| `common.error.ProblemDetailFactory` / `ProblemResponseWriter` | Problem Details 본문 생성(`ErrorCode` + `messages_ko.properties` + `traceId`) / MVC 밖(필터·Security entry point)에서 같은 본문을 쓰는 writer (`05` §1.2) |
| `common.error.GlobalExceptionHandler` | `@RestControllerAdvice`. ProblemDetail + `code`, `traceId`, `errors` 생성. 예상하지 못한 예외는 `INTERNAL_ERROR`로 변환하고 ERROR 로그 1회 |
| `common.web.TraceIdFilter` | `X-Trace-Id` 수신 또는 생성(32 hex) → MDC `traceId` → 응답 헤더 |
| `common.web.RequestBodySizeLimitFilter` | `Content-Length` 또는 실제 스트림 기준 64KB 초과 시 413 |
| `common.web.CursorCodec` | `(sortKey, id)` ↔ base64url cursor |
| `common.web.CursorPage<T>` | `record CursorPage<T>(List<T> items, String nextCursor)` |
| `common.security.SecurityConfig` | filter chain, CORS(local), 인증 예외 → ProblemDetail. JWT decoder는 auth-mode가 고른다(devtoken → `DevTokenConfig`, supabase → Spring Boot 자동 설정) |
| `common.security.DevTokenConfig` / `DevTokenSigningKey` | devtoken 모드 전용(§4.2): 서명 키(PEM 또는 기동 시 생성, prod는 필수) → `JwtEncoder`·`JwtDecoder`(`NimbusJwtDecoder.withJwkSource`, 메모리의 공개 JWK). prod + devtoken WARN 1줄 |
| `common.security.AllowedUserPolicy` | allowlist 판정(이메일 소문자·trim, `allowed-subjects`). devtoken 발급과 §4.3 프로비저닝이 같이 쓴다 |
| `common.security.AuthenticatedUserResolver` | interface: `ResolvedUser resolve(Jwt jwt)` — `user` 모듈이 구현 |
| `common.security.UserContextFilter` | JWT 인증 후 실행. resolver 호출 → `CurrentUser`를 SecurityContext에 저장, 거부 시 403 |
| `common.security.UserRole` | enum `USER`, `ADMIN` (보안 공통 타입이므로 common에 둔다) |
| `common.security.CurrentUser` | `record CurrentUser(UUID userId, UUID externalAuthId, UserRole role, ZoneId zoneId, int dayStartHour, boolean onboardingCompleted, boolean deletionRequested)` |
| `common.web.OnboardingRequiredInterceptor` | MVC `HandlerInterceptor`. 온보딩 전 사용자의 요청을 409 `ONBOARDING_REQUIRED`로 막는다. 예외 목록은 §4.1 |
| `common.web.RateLimitFilter` / `TokenBucketRateLimiter` | 사용자당 요청 수 제한 (`07-security-and-privacy.md` §12.3) |
| `common.math.FixedPointMath` | `floorDiv`, `ceilDiv`, `roundHalfUpDiv`, bp·micro 변환 (`06-learning-engine-rules.md` §1) |
| `common.logging.AuditLogger` / `AuditEvent` / `UserRefCalculator` | 감사 이벤트 로그(로거 `com.devpilot.audit`), 이벤트 이름 enum(`07` §6.3), `userRef` HMAC 계산 |
| `common.config.OpenApiConfig` | OpenAPI 공통 규약(`05` §18): `bearerAuth`, default `ProblemDetail` 응답, 응답 필드 required, 서버 주소 고정 |
| `common.web.validation.*` | `05-api-spec.md` 검증 규칙(UTF-8 바이트 상한, 줄 수, IANA timezone, URL, 날짜 범위)을 구현하는 공용 제약 annotation. 서비스 계층 검사로 구현해도 되며, 응답 오류 코드는 05를 따른다 |
| `common.security.CurrentUserArgumentResolver` | 컨트롤러 파라미터 `CurrentUser currentUser` 주입 |
| `common.idempotency.IdempotencyService` | `<T> IdempotentResult<T> execute(userId, key, method, path, requestHash, Supplier<ResponseEntity<T>>)` — §5.4 |
| `common.async.AsyncConfig` | `aiTaskExecutor` (core 2, max 2, queue 20) |
| `common.job.RetentionCleanupTarget` | interface: `String name()`, `int purgeExpired(Instant now)`, `default Map<String,Object> dailySummary(LocalDate day)`. 데이터를 소유한 모듈이 구현 (§2.2) |
| `common.job.OrphanAsyncTaskSweeper` | interface: `String name()`, `int markInterrupted(Instant staleBefore)`. 비동기 작업을 소유한 모듈이 구현 (§2.2) |
| `common.job.RetentionCleanupJob` | `idempotency_record` 만료 삭제(자기 소유) + 주입받은 `RetentionCleanupTarget` 전부 호출 + 일일 요약 INFO 1줄 (§6, §8) |
| `common.job.OrphanAsyncTaskJob` | 주입받은 `OrphanAsyncTaskSweeper` 전부를 `now − orphan-timeout`으로 호출 (§5.3, §6) |
| `common.jpa.BaseTimeEntity` | `createdAt`, `updatedAt` (JPA Auditing), `@Version version`은 필요한 entity에만 |

### 3.2 도메인 모듈 (주요 클래스)

| 모듈 | presentation | application | domain (규칙은 굵게) | infrastructure |
|---|---|---|---|---|
| `user` | `MeController`, `AccountController`, `CalendarFeedController`, `DevTokenController`(`auth-mode=devtoken`일 때만 bean) | `UserProvisioningService`(implements `AuthenticatedUserResolver`, `UserTimeSettingsProvider`), `ProfileService`, `AccountDeletionService`, `CalendarTokenService`, `DevTokenService`(allowlist 검사 → JWT 서명, §4.2) | `AppUser`, `UserStatus`, `UserRole` | `AppUserRepository`, `AccountDeletionJob`, `IcsFeedWriter` |
| `goal` | `LearningGoalController` | `LearningGoalService`, `LearningGoalQueryService` | `LearningGoal`, `LearningGoalFocusSkill`, `TargetRole` | `LearningGoalRepository` |
| `skill` | `SkillController` | `SkillCatalogQueryService`, `UserSkillStateQueryService`, `SkillStateUpdater`(이벤트 구독) | `Skill`, `RoleSkillTarget`, `UserSkillState`, `SkillStateChange`, **`SkillLevelRules`**, **`PlanningLevelPolicy`** | repositories |
| `learning` | `LearningSessionController` | `LearningSessionService`, `LearningEventRecorder`, `LearningEventQueryService`(사용자·skill의 최근 N일 이벤트 — `skill`이 사용), `HintService` | `LearningSession`, `LearningEvent`, `LearningEventType`, `HintDisclosure`, `HintLevel`, **`HintLadderPolicy`**, **`ComebackModePolicy`**(세션 이력만 입력, today·review가 사용), **`RubricScorer`**(rubric coverage, training·review가 사용), event record `LearningEventRecorded` | repositories |
| `plan` | `PlanController` | `PlanQueryService`, `PlanCommandService`, `ReplanService`, `StudyBudgetService`, `PlanTemplateRegistry`(메모리 보관, `content`가 기동 시 등록) | `LearningPlan`, `PlanMilestone`, `PlanSkillTarget`, `PlanProgressSnapshot`, `PlanTemplate`(record), **`StudyBudgetCalculator`**, **`DeadlineRiskEvaluator`**, **`ReplanSuggestionPolicy`**, **`PlanTemplatePlacement`** | repositories, `ProgressSnapshotJob` |
| `review` | `ReviewController`, `ReviewItemController` | `ReviewService`, `ReviewItemService`(Later: implements `OrphanAsyncTaskSweeper` — review variant), `ReviewQueryService`(다른 모듈 공개), `SeedCardAssignmentService`(`assignForNewUser`, `backfillAll`), `SeedCardRegistry`(메모리 보관, `content`가 기동 시 등록) | `ReviewItem`, `ReviewAnswer`, `SeedCard`(record), **`FinalRatingPolicy`**, `ReviewSchedulingStrategy`, **`RuleBasedV1Scheduler`**, **`DueReviewSelector`** | repositories, `ReviewVariantTask`(async, Later) |
| `today` | `TodayController`, `ReadingController`(`GET /readings/{key}`) | `TodayPlanService`, `TodayQueryService`, `ReadingQueryService`, `CuratedReadingRegistry`(메모리 보관, `content`가 기동 시 등록) | `DailyPlan`, `LearningTask`, **`PlannerScoring`**, **`TaskProposalPolicy`**, **`TimeAllocator`**, **`ReasonTemplates`** (`ComebackModePolicy`는 `learning.domain`) | repositories |
| `training` | `ChallengeController`, `ChallengeAttemptController` | `ChallengeGenerationService`, `ChallengeValidationService`, `AttemptService`, `SubmissionService`, `ChallengeQueryService`(today가 사용), `TrainingAsyncTaskSweeper`(implements `OrphanAsyncTaskSweeper` — challenge 생성·submission 평가) | `Challenge`, `ChallengeAttempt`, `ChallengeSubmission`, **`AttemptOutcomeCalculator`** (`RubricScorer`는 `learning.domain`) | repositories, `ChallengeGenerationTask`, `SubmissionEvaluationTask` (async) |
| `coach` | `CoachReviewController` | `CoachReviewService`(implements `OrphanAsyncTaskSweeper`), `CoachFindingService`, `ThinkingPatternRecorder` | `CoachReview`, `CoachFinding`, `ThinkingPatternObservation`, **`DiscoveredByResolver`** | repositories, `CoachAnalysisTask`(async), `CoachContentPurgeJob` |
| `evidence` | `EvidenceController`, `WeeklyReviewController` | `EvidenceService`(implements `OrphanAsyncTaskSweeper`), `EvidenceExportService`, `WeeklyReviewService` | `EvidenceCandidate`, `WeeklyReview`, **`MetricsCalculator`** | repositories, `WeeklyReviewJob`, `EvidenceDraftTask` |
| `radar` | `RequirementRadarController` | `RequirementAnalysisService`(implements `OrphanAsyncTaskSweeper`), `RequirementDocPurgeService`(implements `RetentionCleanupTarget` — `source_text` 180일 purge) | `RequirementDoc`, `RequirementItem`, **`RequirementFitClassifier`** | repositories, `RequirementAnalysisTask` |
| `onboarding` | `OnboardingController` | `OnboardingService`, `DiagnosticSuggestionService` | `SelfAssessmentPropagation` | — |
| `dashboard` | `DashboardController` | `DashboardQueryService` | — | — |
| `account` | `AccountExportController` (`GET /me/export`) | `AccountExportService` | — | — |
| `project` | `SideProjectController` | `SideProjectService`, `SideProjectQueryService`(today·coach·rubberduck이 사용) | `SideProject`, `SideProjectStatus` | `SideProjectRepository` |
| `rubberduck` | `RubberDuckController` | `RubberDuckService`(턴·정리·gap 카드·이벤트, `05` §9.5~§9.10) | `RubberDuckSession`, `RubberDuckTurn`, `RubberDuckTargetType`, `RubberDuckStatus`, **`RubberDuckPolicy`**(RD-1~RD-7 — 턴 상한, RD-3 "모르겠다" 판정, RD-5 증거 조건, `06` §9.5) | repositories, `StaleRubberDuckJob` |
| `content` | — | `ContentSeeder`(ApplicationRunner: 검증 → upsert → `PlanTemplateRegistry`·`SeedCardRegistry`·`CuratedReadingRegistry` 등록 → `SeedCardAssignmentService.backfillAll()`), `ContentValidator` | seed record 타입 | `YamlContentReader` |

### 3.3 integration.ai

| 클래스 | 역할 |
|---|---|
| `integration.ai.api.AiProvider` | port: `<T> AiResult<T> call(AiRequest<T> request)` |
| `integration.ai.api.AiRequest<T>` | `operation`, `promptId`, `variables`, `userContent`, `outputType(Class<T>)`, `userId`, `guardContext` |
| `integration.ai.api.GuardContext` | 가드 검사에 필요한 호출 측 정보: `contentLineCount`, `expectedRubricIds`, `requestedHintLevel`, `knownSkillCodes` (`integration.ai`는 skill catalog를 조회하지 않으므로 호출 모듈이 채운다) |
| `integration.ai.api.AiPendingJobCounter` | port: 사용자의 PENDING/RUNNING 비동기 AI 작업 수. `coach`, `training`, `review`, `evidence`, `radar`가 각각 구현하고 `AiBudgetGuard`가 합산 |
| `integration.ai.api.AiResult<T>` | `status(AiCallStatus)`, `value(T)`, `aiCallId`, `model`, `promptVersion`, `guardActions` |
| `integration.ai.api.AiOperation`, `AiCallStatus`, `AiStatus` | enum (`04-domain-model-and-db.md` §3) |
| `integration.ai.api.AiUsageSnapshot`, `AiBudgetDecision`, `AiConcurrencyReservation` | 도메인 모듈에 노출되는 불변 record: 사용량 요약(`GET /me`), 예산 판정, 동시 실행 예약(`17` §8) |
| `integration.ai.api.output` (패키지) | operation 출력 record 11종(`CoachReviewOutput` 등 9종 + 러버덕 `RubberDuckTurnOutput`·`RubberDuckSummaryOutput`, `17` §4). 도메인 모듈은 이 record만 받는다 |
| `integration.ai.deepseek.DeepSeekAiProvider` | Spring `RestClient`로 `POST /responses` 호출(`instructions`, `input`, `text.format json_schema`, `max_output_tokens`, `thinking`, `reasoning_effort`, `store:false`), 응답 `output[]`에서 `type=message`의 `output_text` 추출, 종료 상태·HTTP 오류 → `AiCallStatus` 매핑, `Retry-After` 대기(`17` §5) |
| `integration.ai.deepseek.DeepSeekBalanceClient` | `GET /user/balance` 조회 (`AiBalanceCheckJob`, 402 처리) |
| `integration.ai.fake.FakeAiProvider` | fixture 기반 응답 (`local`, `test`, `demo`). `@ConditionalOnProperty(devpilot.ai.provider=fake)` |
| `integration.ai.disabled.DisabledAiProvider` | 항상 `AI_UNAVAILABLE` |
| `integration.ai.prompt.PromptRegistry` | `classpath:prompts/<id>/<version>/` 로딩, 활성 버전 선택, 템플릿 치환 |
| `integration.ai.schema.OutputSchemaRegistry` | `classpath:ai/schemas/<OPERATION>.schema.json`(규범) 로딩 → wire 스키마 생성(`$schema`·`$id` 제거), operation → 출력 record 매핑, 전용 `ObjectMapper` 제공 (`17` §4.0). `integration.ai` 안에서만 쓴다 |
| `integration.ai.guard.OutputGuardChain` | `VerificationGuard`, `CodeLeakGuard`, `FindingCountGuard`, `EnumGuard`, `SkillCodeGuard`, `LanguageGuard`, `NoAnswerGuard`(러버덕 2개 operation 전용, `17` §6.8) |
| `integration.ai.budget.AiBudgetGuard` | 일일 호출 수, 월 비용, 동시 실행 수, 잔액 소진 플래그 확인 |
| `integration.ai.budget.AiCostCalculator` | usage × 모델 단가 × `peak-multiplier` → micro USD (`17` §8.4) |
| `integration.ai.budget.AiBalanceMonitor` / `AiBalanceCheckJob` | 잔액 상태(메모리 플래그): 402 수신 또는 잔액 < `min-balance-usd`이면 `BALANCE_EXHAUSTED`, 다음 정상 조회에서 해제. 감사 이벤트 `AI_BALANCE_LOW` (`17` §8.7) |
| `integration.ai.log.AiCallLog` / `AiCallLogRepository` | 호출 기록 |
| `integration.ai.log.AiCallLogWriter` | `ai_call_log` 쓰기 전용 (`write`, `writeBlocked`). 쓰기 메서드는 `REQUIRES_NEW`라서 호출 모듈 트랜잭션과 섞이지 않는다 (`17` §5.5, `08` TX-1·TX-4·ARCH-21) |
| `integration.ai.log.AiCallLogRetentionService` | `ai_call_log` 보존 기간(`privacy.ai-call-log-retention-days`) 초과 행 삭제. `common.job.RetentionCleanupJob`이 port로 호출한다 (§6) |
| `integration.ai.masking.SecretMasker` | 패턴 기반 마스킹 → `MaskingResult(maskedText, maskedCount, blocked)` |
| `integration.ai.AiGateway` | **도메인 모듈이 쓰는 유일한 진입점**: 예산·잔액 확인(차단 시 `BUDGET_BLOCKED` 로그) → provider 호출 → 종료 상태 처리 → 출력 가드 → (위반 시 1회 재시도) → `ai_call_log` 기록. secret masking은 입력을 **저장하기 전에** 호출 모듈이 먼저 수행한다 |

도메인 모듈은 `AiGateway`, `integration.ai.api`의 타입, 그리고 입력 저장 전에 필요한 `SecretMasker`/`MaskingResult`, `AiBudgetGuard`(사전 확인)만 쓴다. DeepSeek HTTP 형식(요청·응답 DTO, `RestClient`)은 `integration.ai.deepseek` 밖으로 나가지 않는다. `AiGateway`는 활성 트랜잭션 안에서 호출되면 `IllegalStateException`을 던진다(ArchUnit 규칙과 이중 방어).

---

## 4. 요청 처리 흐름

### 4.1 필터 순서

```text
1. TraceIdFilter                     (Ordered.HIGHEST_PRECEDENCE)
2. RequestBodySizeLimitFilter        (HIGHEST_PRECEDENCE + 10)
3. Spring Security FilterChain
   3-1. CorsFilter                   (local profile만 허용 origin 설정)
   3-2. BearerTokenAuthenticationFilter  → 실패 시 401 AUTHENTICATION_REQUIRED
   3-3. RateLimitFilter              → 사용자(sub)당 120 req/min 초과 시 429 RATE_LIMITED
   3-4. UserContextFilter            → allowlist 거부·익명 토큰(is_anonymous=true) 403 USER_NOT_ALLOWED
                                     → status=DELETION_REQUESTED 이면 GET /me, DELETE /me 외 403 FORBIDDEN
4. DispatcherServlet
   4-1. OnboardingRequiredInterceptor → 온보딩 전이면 409 ONBOARDING_REQUIRED (예외 목록 아래)
   4-2. Controller → Application Service
```

- 온보딩 전에도 허용: `GET /me`, `PATCH /me`, `POST /onboarding`, `GET /skills/tree`, `GET /me/export`, `DELETE /me`, 캘린더 피드
- `DELETE /me`를 다시 호출하면 202를 다시 반환한다(멱등).

- 인증 제외 경로: `GET /actuator/health`, `GET /api/v1/calendar/*.ics`(토큰 인증, §5.5). `auth-mode=devtoken`일 때 `POST /api/v1/dev/token`, `GET /api/v1/dev/jwks.json`(`05` §1.4.5). `local` profile에서만 `/v3/api-docs/**`, `/swagger-ui/**`
- CSRF: 쿠키 인증을 쓰지 않으므로 비활성화. 쿠키 기반 인증을 추가하면 다시 검토한다(`07-security-and-privacy.md` §9).
- 세션: `SessionCreationPolicy.STATELESS`

### 4.2 JWT 검증

`devpilot.security.auth-mode`로 `JwtDecoder` bean을 고른다. 검증 이후(§4.3 프로비저닝, allowlist, `CurrentUser`)는 두 모드가 같다.

**`devtoken` (기본, 운영 포함 — 2026-09-18 결정 A)**

```text
키:      DEVPILOT_DEV_JWT_KEY(EC P-256 PKCS#8 PEM). 비어 있으면 기동 시 생성하고 WARN 1줄(재기동 시 기존 토큰 무효).
         prod profile에서는 비어 있으면 기동 실패.
발급:    POST /api/v1/dev/token {email}  →  DevTokenService
         email(소문자 trim)이 allowlist에 없으면 403 USER_NOT_ALLOWED
         claims: iss=${APP_BASE_URL}/dev, aud=authenticated, sub=UUID v5(email), email,
                 amr=[{method:"devtoken", timestamp}], iat, exp=iat+token-ttl(720h)
검증:    NimbusJwtDecoder.withJwkSource(메모리의 공개 JWK) + issuer·audience validator (JWKS HTTP 조회 없음)
공개키:  GET /api/v1/dev/jwks.json (디버그·외부 도구용, 인증 불필요)
```

**`supabase` (Later)**

```yaml
spring.security.oauth2.resourceserver.jwt:
  jwk-set-uri: ${SUPABASE_URL:}/auth/v1/.well-known/jwks.json     # 기본값을 비워 SUPABASE_URL 없이도
  issuer-uri: ${SUPABASE_URL:}/auth/v1                            # 기동이 막히지 않게 한다 (13 BL-SEC-03)
  audiences: authenticated
  jws-algorithms: ${SUPABASE_JWT_ALGORITHM:ES256}
```

- 검증 항목(공통): 서명, `iss`, `aud=authenticated`, `exp`/`nbf`(clock skew 60초)
- 사용 claim: `sub`(UUID) → `external_auth_id`, `email`(allowlist 비교), `is_anonymous`(true면 거부), `amr[].timestamp`(계정 삭제 전 최근 로그인 확인: 가장 최근 인증 시각이 5분 이내. devtoken은 발급 시각. `amr`이 없으면 `iat`로 대체)
- `supabase` 모드에서 JWKS는 Spring decoder가 캐시한다. 키 회전 시 새 `kid`가 들어오면 다시 조회한다.
- **`devtoken`은 임시 수단이다.** 안전성의 근거는 "tailnet 밖에서 `POST /api/v1/dev/token`에 닿을 수 없다" 하나뿐이다(`07` §3). 공개 URL이 되면 allowlist 이메일만 알면 누구나 로그인한다. **공개 노출 전에 **실제 신원 확인이 있는 인증**으로 바꾼다(`13` BL-SEC-18, `11` §3.10). devtoken의 문제는 JWT 자체가 아니라 "이메일만 입력하면 발급"이라는 신원 확인 부재이므로, 발급 직전 단계만 바꾸면 된다 — 후보 (a) backend가 GitHub OAuth를 직접 처리하고 현재의 JWT 발급·검증 경로를 그대로 재사용(외부 서비스 의존 없음, 권장) (b) Supabase Auth(`auth-mode=supabase`). 이 방식은 비공개 네트워크 배포에서만 쓴다.**
- **기동 시 경고**: `prod` profile + `auth-mode = devtoken`이면 기동은 하되 `DevTokenConfig`가 WARN 1줄을 남긴다 — "이 인스턴스는 tailnet 등 비공개 네트워크에서만 노출해야 한다. 공개 배포 전 `DEVPILOT_AUTH_MODE=supabase`로 전환". 기동을 막지는 않는다(현재 운영 모드이므로). 확인은 `09` §6.4.

### 4.3 사용자 프로비저닝 (`UserProvisioningService.resolve`)

```text
1. externalAuthId = UUID(jwt.sub)
2. appUser = findByExternalAuthId(externalAuthId)
3. 없으면:
   a. email(소문자, trim)이 allowlist(devpilot.security.allowed-emails) 또는 sub이 allowed-subjects에 없으면 → USER_NOT_ALLOWED
   b. INSERT ... ON CONFLICT (external_auth_id) DO NOTHING  (displayName = email @ 앞부분, timezone/dayStartHour 기본값)
   c. 다시 조회
   d. 감사 로그 AUTH_USER_PROVISIONED
4. 있으면: allowlist를 다시 확인한다(allowlist에서 제거된 사용자 차단). 거부 시 AUTH_USER_REJECTED 로그
5. CurrentUser 반환
```

- 조회 결과를 요청마다 DB에서 읽는다. 사용자 수가 적어 캐시는 두지 않는다.

### 4.4 Controller → Service 규칙

- Controller는 `@Valid` request record 검증, `CurrentUser` 전달, 응답 매핑만 한다.
- Application service의 모든 public 메서드는 첫 인자로 `CurrentUser` 또는 `UUID userId`를 받는다. **조회 조건에 항상 `userId`를 포함**한다. 예: `findByIdAndUserId`
- 타인 리소스와 존재하지 않는 리소스는 똑같이 `NotFoundException`(404)으로 처리한다.

---

## 5. 트랜잭션 · 이벤트 · 비동기

### 5.1 트랜잭션 규칙

| 규칙 | 내용 |
|---|---|
| T-1 | 트랜잭션 경계는 application service 메서드. 조회는 `@Transactional(readOnly = true)` |
| T-2 | **AI 호출(`AiGateway`)은 트랜잭션 밖에서만** 한다. ArchUnit으로 검사 |
| T-3 | 한 use case 안에서 여러 모듈 서비스를 호출할 때는 호출하는 쪽 트랜잭션에 참여한다(기본 전파 REQUIRED) |
| T-4 | `open-in-view=false`. 응답 DTO는 트랜잭션 안에서 만든다 |
| T-5 | 동시 수정 대상 entity는 `@Version`. 충돌 시 `ObjectOptimisticLockingFailureException` → `CONCURRENT_MODIFICATION` 409 |
| T-6 | 사용자당 활성 plan 1개, 하루 main task 1개 같은 불변식은 **DB unique index + 애플리케이션 검사** 둘 다 둔다. unique 위반은 `DataIntegrityViolationException` → 도메인별 409 코드로 변환 |

### 5.2 학습 이벤트 → skill 갱신 (동기, 같은 트랜잭션)

```text
ReviewService.answer(...)                     @Transactional
  ├─ RuleBasedV1Scheduler.schedule(...)        (순수 계산)
  ├─ reviewItem 갱신, reviewAnswer 저장
  └─ LearningEventRecorder.record(REVIEW_ANSWERED, ...)
        ├─ learning_event INSERT (dedupe_key 있으면 중복 시 기존 이벤트 반환)
        └─ publisher.publishEvent(new LearningEventRecorded(eventId, userId, skillId, type))
              └─ SkillStateUpdater.on(LearningEventRecorded)   @EventListener (동기)
                    ├─ 해당 skill의 60일 이벤트 조회
                    ├─ SkillLevelRules.evaluate(...)             (순수 계산)
                    └─ user_skill_state 갱신 + skill_state_change INSERT
```

- 이벤트 처리 중 예외가 나면 전체 트랜잭션을 롤백한다(응답 실패). skill 갱신이 조용히 누락되는 것보다 낫다.
- 이 경로에서는 AI를 호출하지 않는다.

### 5.3 AI 비동기 작업 (coach review 예시)

```text
[HTTP 요청 스레드]
CoachReviewController.create()          (@Valid: 크기·줄 수·동의 검증 → 400/413)
  └─ IdempotencyService.execute(...)
       └─ CoachReviewService.create()
            ├─ SecretMasker.mask(content)                → blocked면 422 (저장·AI 호출 없음)
            ├─ AiBudgetGuard.check(userId, COACH_REVIEW) → 초과면 BUDGET_BLOCKED 로그(별도 트랜잭션) 후 429
            ├─ @Transactional: coach_review INSERT (status=PENDING, content=마스킹본)
            └─ publishEvent(CoachReviewRequested(reviewId))
  └─ 202 Accepted { id, status: PENDING, maskedSecretCount }

[aiTaskExecutor 스레드]  @TransactionalEventListener(phase = AFTER_COMMIT) + @Async("aiTaskExecutor")
CoachAnalysisTask.on(CoachReviewRequested)
  ├─ tx: status=RUNNING, 컨텍스트 조회 (skill state, 약한 thinking 축)
  ├─ (트랜잭션 없음) AiGateway.call(COACH_REVIEW ...)      ← 최대 180s
  └─ tx: 성공 → findings INSERT, status=COMPLETED
         실패 → status=FAILED, failure_code
```

- 클라이언트는 `GET /api/v1/coach/reviews/{id}`를 2초 간격으로 polling한다(최대 3분).
- 서버 재시작 등으로 `RUNNING` 또는 `PENDING` 상태가 `orphan-timeout`(10분) 넘게 `status_updated_at`(review variant는 `variant_status_updated_at`)이 갱신되지 않으면 `OrphanAsyncTaskJob`이 `FAILED(INTERRUPTED)`로 정리한다. 메모리 큐가 재시작으로 사라진 PENDING 작업이 동시 실행 수에 계속 잡히는 것을 막기 위해서다.
- executor 큐가 가득 차 작업 제출이 거절되면(`TaskRejectedException`) 그 리소스를 즉시 `FAILED(INTERNAL_ERROR)`로 바꾸고 WARN 로그를 남긴다. 사용자는 retry endpoint로 다시 요청한다.
- 같은 패턴을 쓰는 작업: `ChallengeGenerationTask`, `SubmissionEvaluationTask`, `ReviewVariantTask`, `EvidenceDraftTask`, `RequirementAnalysisTask`
- **재시도는 `AiGateway`가 직접 한다.** `RestClient`에는 자동 재시도가 없고 provider 안에서도 재시도하지 않는다. 그래서 공급자 호출마다 `ai_call_log` 행이 하나씩 남는다. 재시도 사이에는 `Retry-After`(없으면 `retry-after-default`) 만큼 기다린다(`17` §5.2). operation 설정의 `max-retries`는 네트워크 오류와 가드 위반 재시도를 합한 횟수이고, `timeout`은 재시도를 포함한 전체 마감 시간이다. 동기 operation은 `max-retries: 0`이므로 가드 위반 시 바로 `AI_OUTPUT_INVALID`로 처리한다(응답 20초 보장).
- **동기 AI 호출**은 `HINT_GENERATE`, `REVIEW_EVALUATE`, `COACH_RESPONSE_FEEDBACK` 세 가지뿐이다. 서버 타임아웃은 20초 이하, 재시도는 0회다. 순서는 `tx1(조회/검증) → AiGateway → tx2(저장)`이다. 두 트랜잭션 사이에 상태가 바뀌었으면(`version` 불일치) 결과를 저장하지 않고 409를 반환한다.
- `REVIEW_EVALUATE`가 실패하면 답변은 `NOT_EVALUATED`로 정상 저장하고, 응답에 `evaluationSkippedReason`을 넣는다(AI 장애가 복습 흐름을 막지 않게).
- **`COACH_RESPONSE_FEEDBACK` 변형**: 사용자 응답을 먼저 저장하고(tx1: masking → `user_response`, finding status `USER_RESPONDED`) → AI 호출 → tx2에서 `ai_feedback`, `ai_follow_up_question`, `user_identified_issue`, `feedback_ai_call_id`를 저장한다. AI가 실패해도 응답은 남고 `feedbackSkippedReason`을 반환한다. tx2 시점에 사용자 응답이 바뀌었거나 review가 close되었으면 피드백을 저장하지 않고 응답에도 넣지 않는다(409를 반환하지 않음).
- 동기 skip reason 매핑: 동시 실행 한도(`AI_CONCURRENCY_LIMIT`)와 공급자 429는 `AI_RATE_LIMITED`, 예산·일일 한도는 `AI_BUDGET_EXCEEDED`. AI를 호출할 필요가 없는 경우(평가할 rubric 없음 등) skip reason은 `null`이다.

### 5.4 Idempotency

```text
IdempotencyService.execute(userId, key, method, path, requestHash, action):
  1. INSERT idempotency_record(user_id, key, method, path, request_hash, expires_at=now+24h)
     ON CONFLICT DO NOTHING  (별도 짧은 트랜잭션)
  2. 삽입됨 → action 실행 → 응답 status/body를 record에 저장 → 응답 반환
            → action이 예외로 끝나면 record 삭제 (재시도 가능)
  3. 이미 존재:
     a. request_hash 다름                 → 422 IDEMPOTENCY_KEY_REUSED
     b. response_status 있음              → 저장된 응답 재생 (헤더 Idempotent-Replayed: true)
     c. response_status 없음 (처리 중)     → 409 IDEMPOTENCY_IN_PROGRESS
```

- `requestHash = SHA-256(method + " " + path + "\n" + canonical JSON body)` (canonical: 키 정렬, 공백 제거)
- **인증이 필요한 모든 `POST` 요청은 `Idempotency-Key` 헤더가 필수**다. 없으면 400 `IDEMPOTENCY_KEY_REQUIRED`. `PUT`/`PATCH`/`DELETE`는 `version`으로 동시성을 제어하므로 키를 받지 않는다.
- 클라이언트는 사용자 행동 1회(버튼 클릭 1회)마다 UUID 키를 새로 만들고, 네트워크 재시도에는 같은 키를 재사용한다.
- **secret을 담은 응답은 원문을 저장하지 않는다.** `POST /me/calendar-token`은 `response_body`의 `feedUrl`을 `null`로 바꿔 저장하므로 재생 응답에는 토큰이 없다.

### 5.5 캘린더 피드 인증

- `GET /api/v1/calendar/{token}.ics`는 Bearer 인증을 쓰지 않는다. `SHA-256(token)`으로 `app_user.calendar_token_hash`를 조회한다.
- 일치하지 않으면 404. 토큰 경로는 로그에 `…/calendar/****.ics`로 마스킹한다.

---

## 6. 스케줄 작업

| Job (클래스) | 모듈 | 스케줄 (UTC cron) | 처리 |
|---|---|---|---|
| `ProgressSnapshotJob` | plan | `0 5 * * * *` (매시 5분) | 로컬 시각이 `dayStartHour`인 사용자만: budget/risk 계산 → `plan_progress_snapshot` upsert |
| `WeeklyReviewJob` | evidence | `0 10 * * * *` (매시 10분) | 로컬 시각이 월요일 `dayStartHour`인 사용자만: 지난주 지표 → `weekly_review` |
| `CoachContentPurgeJob` | coach | `0 20 18 * * *` (KST 03:20) | `content_retention_until < now` → `content=null`, `content_purged_at=now` |
| `RetentionCleanupJob` | common | `0 30 18 * * *` | `idempotency_record` 만료 삭제(직접) + `RetentionCleanupTarget` 구현 호출: `ai_call_log` 180일 초과 삭제(`integration.ai`), `requirement_doc.source_text` 180일 purge(`radar`). 대상 모듈을 직접 의존하지 않는다 (§2.2) |
| `AccountDeletionJob` | user | `0 */5 * * * *` | `DELETION_REQUESTED` 사용자 삭제 (cascade) + 감사 로그 |
| `OrphanAsyncTaskJob` | common | 기동 시 1회 + `0 */10 * * * *` | `OrphanAsyncTaskSweeper` 구현 호출: 10분 넘게 갱신되지 않은 `PENDING`/`RUNNING` 작업(coach review, challenge 생성, submission 평가, review variant, evidence 초안, 요구사항 분석) → `FAILED(INTERRUPTED)`. 대상 모듈을 직접 의존하지 않는다 (§2.2) |
| `AiBalanceCheckJob` | integration.ai | `0 15 * * * *` (매시 15분, `balance-check-cron`) | `provider=deepseek`일 때만. `GET /user/balance` → 잔액 < `min-balance-usd`이면 `AiBalanceMonitor`를 `BALANCE_EXHAUSTED`로, 이상이면 해제. 상태가 바뀔 때 감사 이벤트 `AI_BALANCE_LOW`/`AI_BALANCE_RESTORED` + WARN/INFO. 조회 실패는 상태를 바꾸지 않는다 (S3) |
| `StaleRubberDuckJob` | rubberduck | `0 25 * * * *` (매시 25분) | `status = IN_PROGRESS`이고 `started_at < now − devpilot.rubberduck.stale-after`(24h)인 러버덕 세션 → `ABANDONED`, `completed_at = now`. 정리 AI를 부르지 않고 복습 카드·학습 이벤트도 만들지 않는다(`05` §9.5). 부분 인덱스 `idx_rubber_duck_session_in_progress`를 쓴다 |

- `@EnableScheduling`을 쓰고 단일 인스턴스를 전제로 한다. 인스턴스를 늘리면 분산 락을 도입한다(현재 범위 밖).
- 모든 job은 시작, 처리 건수, 소요시간을 INFO 1줄로 남기고, 실패하면 `JOB_FAILED` WARN을 남긴다.
- job 안의 사용자별 처리는 사용자 단위 트랜잭션으로 나눈다. 한 사용자가 실패해도 다음 사용자를 계속 처리한다.

---

## 7. 오류 처리 아키텍처

| 발생 | 변환 |
|---|---|
| `DevPilotException` 계열 | `ErrorCode`의 status/title + 메시지(`messages_ko.properties`의 `error.<CODE>`) |
| `MethodArgumentNotValidException`, `ConstraintViolationException` | 400 `VALIDATION_FAILED` + `errors[]`(field, code, message) |
| `HttpMessageNotReadableException` 중 enum 변환 실패 | 400 `UNKNOWN_ENUM_VALUE` |
| 그 외 `HttpMessageNotReadableException` (파싱 실패, 알 수 없는 속성, primitive null) | 400 `MALFORMED_REQUEST` |
| `MethodArgumentTypeMismatchException` (path/query) | enum이면 400 `UNKNOWN_ENUM_VALUE`, 그 외 400 `VALIDATION_FAILED`(field code `TYPE_MISMATCH`) |
| `MissingRequestHeaderException` (`Idempotency-Key`) | 400 `IDEMPOTENCY_KEY_REQUIRED` |
| `AuthenticationException` (EntryPoint) | 401 `AUTHENTICATION_REQUIRED` |
| `AccessDeniedException` | 403 `FORBIDDEN` |
| `ObjectOptimisticLockingFailureException` | 409 `CONCURRENT_MODIFICATION` |
| `NoResourceFoundException` | 404 `RESOURCE_NOT_FOUND` |
| 기타 `Exception` (최후 방어선 1곳) | 500 `INTERNAL_ERROR`, ERROR 로그(stack trace는 로그에만) |

- `catch (Exception e)`는 다음 **세 곳에서만** 허용한다: (1) `GlobalExceptionHandler`의 최후 방어선 (2) 비동기 `*Task` 최상위(작업을 FAILED로 표시) (3) 스케줄 `*Job`의 사용자 단위 루프(한 사용자 실패가 다른 사용자 처리를 막지 않게, `JOB_FAILED` 로그). 모두 이유를 주석으로 남긴다.
- 응답 `detail`에는 사용자용 한국어 문장만 넣는다. 내부 메시지는 로그에만 남긴다.
- Jackson: `spring.jackson.deserialization.fail-on-unknown-properties=true`, `fail-on-null-for-primitives=true` (요청 형식을 엄격하게 검사)

---

## 8. 관측성

| 항목 | 구현 |
|---|---|
| 로그 포맷 | `prod`: Spring Boot structured logging (JSON) / `local`: 기본 콘솔 |
| 공통 필드 | `traceId`(MDC), `userRef`(= HMAC-SHA256(userId, `DEVPILOT_LOG_HASH_KEY`) 앞 12 hex) |
| 감사 이벤트 | `AuditLogger.log(AuditEvent, Map<String,Object>)` → INFO, 필드 `event`. 목록은 `07-security-and-privacy.md` §6.3 |
| 금지 | Authorization 헤더, request/response body, 사용자 코드, AI 프롬프트·응답 원문, 이메일 원문 |
| 지표 | Micrometer: `devpilot.ai.calls`, `devpilot.ai.latency`, `devpilot.ai.cost.usd`, `devpilot.jobs.runs` (외부 비노출) |
| 일일 요약 | `RetentionCleanupJob` 끝에 전날 AI 호출 수, 비용, 오류 수, job 실패 수를 INFO 1줄로 기록. AI 항목은 `RetentionCleanupTarget.dailySummary`(`integration.ai` 구현)가 넘긴 값을 그대로 쓴다 (§2.2) |
| Health | `/actuator/health` (liveness, readiness 포함, 상세 비노출) |

---

## 9. 설정값 전체 목록 (`devpilot.*`)

`application.yml` 기본값이다. 운영 값은 환경변수로 덮어쓴다. **가중치, 임계값, 단가를 코드에 하드코딩하지 않는다.**

```yaml
devpilot:
  security:
    auth-mode: ${DEVPILOT_AUTH_MODE:devtoken}           # devtoken | supabase (§4.2, 결정 A)
    devtoken:
      issuer: ${APP_BASE_URL}/dev
      private-key-pem: ${DEVPILOT_DEV_JWT_KEY:}         # 비면 기동 시 EC P-256 생성(재기동 시 기존 토큰 무효). prod는 필수
      token-ttl: 720h
    allowed-emails: ${DEVPILOT_ALLOWED_EMAILS:}          # 쉼표 구분, 소문자 비교
    allowed-subjects: ${DEVPILOT_ALLOWED_SUBJECTS:}      # 선택: 외부 인증 user UUID
    max-request-body-bytes: 65536
    account-deletion-max-token-age: 5m
    log-hash-key: ${DEVPILOT_LOG_HASH_KEY:}              # prod 필수
    rate-limit:
      requests-per-minute: 120
      calendar-feed-per-hour: 60
      calendar-invalid-token-per-hour-per-ip: 30
      dev-token-per-hour-per-ip: 30                      # POST /api/v1/dev/token (05 §1.4.5)
  web:
    app-base-url: ${APP_BASE_URL}                        # 캘린더 피드 URL 생성 (prod 필수)
    cors-allowed-origins: ${CORS_ALLOWED_ORIGINS:}       # local만 사용
  time:
    default-zone: Asia/Seoul
    default-day-start-hour: 4
  planner:
    weights: { practical-importance: 0.25, skill-gap: 0.20, review-urgency: 0.20, milestone-urgency: 0.15, project-need: 0.10, prerequisite-readiness: 0.10 }
    modifiers:
      risk-high-must: 1.20
      risk-high-should: 0.80
      low-energy-deep-task: 0.70
      high-energy-hard-task: 1.10
      fatigue-one-day: 0.80
      fatigue-two-days: 0.60
      continuation-bonus: 1.15
      comeback-hard-task: 0.70             # 06 §5.5 COMEBACK_HARD_TASK (7_000bp)
    low-energy-long-task-minutes: 30
    min-prerequisite-readiness: 0.5
    overrun-tolerance: 1.10
    min-main-task-minutes: 10
    min-available-minutes: 5
    challenge-repeat-exclusion-days: 14
    comeback-inactive-days: 3
    review-minutes-per-card: 1.5
    review-max-share: 0.25
    default-practical-importance: 0.30
    milestone-urgency-floor: 0.20          # 06 §5.4 진행 중 milestone 하한 (200_000 micro)
    next-milestone-urgency: 0.10           # 06 §5.4 다음 milestone 고정값
    review-urgency-base: 0.30              # 06 §5.4 due review 기저
    review-urgency-per-overdue-day: 0.10   # 06 §5.4 연체 일당 가산
    leech-review-urgency: 1.00             # 06 §6.4 LEECH_DETECTED 다음 plan-day
    reason-high-threshold: 0.70            # 06 §5.8 HIGH_* reason 임계값
    reason-medium-threshold: 0.40          # 06 §5.8 *_GAP reason 임계값
  budget:
    completion-window-days: 28
    completion-min-history-days: 14
    completion-default-rate: 0.70
    completion-min-rate: 0.30
    axis-cost: { knowledge: 0.5, implementation: 1.0, explanation: 0.4, debugging: 0.8 }
    review-overhead: 1.15
    risk-thresholds: { low-max: 0.80, medium-max: 1.00, high-max: 1.25 }
    # 소수 설정값은 기동 시 bp(×10000)/micro(×1000000) 정수로 변환. 정수가 아니면 기동 실패 (06 §1 N-6)
  review:
    max-per-day: 20
    comeback-max-per-day: 10
    min-interval-days: 1
    max-interval-days: 60
    hard-strategy: MULTIPLY        # MULTIPLY(×1.2) | FIXED_2
    hard-multiplier: 1.2
    good-multiplier: 2.0
    easy-multiplier: 3.0
    good-min-days: 2
    easy-min-days: 4
    variant-after-failures: 2
    suspend-after-failures: 4
  rubberduck:
    max-turns: 5                            # 06 §9.5 RD-4
    stuck-turns-before-hint: 2             # RD-3
    dont-know-max-chars: 30                # RD-3 판정: 공백 제거 길이 < 30 이고
    dont-know-phrases: ["모르겠", "모름", "잘 모르", "생각 안", "idk", "no idea", "don't know", "dont know"]   # 06 §9.5
    stale-after: 24h                       # StaleRubberDuckJob (§6)
    max-explanation-chars: 2000
    evidence-coverage-bp: 7000             # 06 §7.2 러버덕 설명 증거의 고정 coverage (E2·E3용, E4 미만)
  skill:
    rule-window-days: 60
    axis-change-cooldown: 24h
    self-assessment-cap: 3
    diagnostic-max-level: 3                # 06 §7.4 min(claimedLevel, 3)
  radar:
    default-target: { knowledge: 3, implementation: 3, explanation: 3, debugging: 2 }   # 06 §13 target 없을 때
  training:
    max-submissions-per-attempt: 5
  coach:
    max-content-bytes: 30000
    max-content-lines: 1000
    max-findings: 7
  privacy:
    coach-content-retention-days: 30
    source-text-retention-days: 180
    ai-call-log-retention-days: 180
    idempotency-ttl: 24h
  content:
    seed-on-startup: true
    seed-challenges: true                                # S1·S2 빌드는 false (challenge upsert는 S3 training 모듈부터, 04 §9)
    location: classpath:content/
  ai:
    provider: ${DEVPILOT_AI_PROVIDER:deepseek}           # deepseek | fake | disabled
    model: ${DEVPILOT_AI_MODEL:deepseek-flash}
    deepseek: { base-url: https://api.deepseek.com, api-key: "${DEEPSEEK_API_KEY:}", store: false, retry-after-default: 2s }
    monthly-budget-usd: ${DEVPILOT_AI_MONTHLY_BUDGET_USD:3}
    budget-warning-ratio: 0.80
    min-balance-usd: 1.00                                # 선불 잔액이 이 값 미만이면 BALANCE_EXHAUSTED (17 §8.7)
    balance-check-cron: "0 15 * * * *"
    daily-call-limit-per-user: 60
    max-concurrent-per-user: 2
    async: { core-pool-size: 2, max-pool-size: 2, queue-capacity: 20, orphan-timeout: 10m }
    operations:                                          # 17-ai-integration.md §2. thinking=false면 reasoning-effort 무시, effort 컬럼 'off'
      COACH_REVIEW:            { mode: ASYNC, thinking: true,  reasoning-effort: high, max-tokens: 32000, timeout: 180s, max-retries: 1, input-token-budget: 14000 }
      COACH_RESPONSE_FEEDBACK: { mode: SYNC,  thinking: false, max-tokens: 2000,  timeout: 20s,  max-retries: 0, input-token-budget: 6000 }
      CHALLENGE_GENERATE:      { mode: ASYNC, thinking: true,  reasoning-effort: high, max-tokens: 32000, timeout: 180s, max-retries: 1, input-token-budget: 4000 }
      CHALLENGE_EVALUATE:      { mode: ASYNC, thinking: true,  reasoning-effort: low,  max-tokens: 16000, timeout: 120s, max-retries: 1, input-token-budget: 8000 }
      HINT_GENERATE:           { mode: SYNC,  thinking: false, max-tokens: 3000,  timeout: 20s,  max-retries: 0, input-token-budget: 6000 }
      REVIEW_VARIANT:          { mode: ASYNC, thinking: false, max-tokens: 4000,  timeout: 60s,  max-retries: 1, input-token-budget: 3000 }   # Later
      REVIEW_EVALUATE:         { mode: SYNC,  thinking: false, max-tokens: 2000,  timeout: 20s,  max-retries: 0, input-token-budget: 3000 }
      EVIDENCE_DRAFT:          { mode: ASYNC, thinking: true,  reasoning-effort: low,  max-tokens: 16000, timeout: 120s, max-retries: 1, input-token-budget: 6000 }
      REQUIREMENT_EXTRACT:     { mode: ASYNC, thinking: false, max-tokens: 8000,  timeout: 120s, max-retries: 1, input-token-budget: 8000 }
      RUBBER_DUCK:             { mode: SYNC,  thinking: false, max-tokens: 1500,  timeout: 20s,  max-retries: 0, input-token-budget: 6000 }
      RUBBER_DUCK_SUMMARY:     { mode: SYNC,  thinking: true,  reasoning-effort: low, max-tokens: 4000, timeout: 30s, max-retries: 0, input-token-budget: 8000 }
    trusted-source-hosts: [docs.spring.io, spring.io, docs.oracle.com, openjdk.org, www.postgresql.org, owasp.org, cheatsheetseries.owasp.org, www.kisa.or.kr, supabase.com, dart.dev, docs.flutter.dev, api.flutter.dev, pmd.github.io, spotbugs.readthedocs.io, checkstyle.org, junit.org, hibernate.org, docs.jboss.org, developer.mozilla.org, www.rfc-editor.org]
    curated-sources-location: classpath:content/curated-sources.yaml
    pricing:                                             # USD per 1M tokens, 2026-09-18 확인 (17-ai-integration.md §8.4)
      peak-multiplier: 2                                 # 결정 F: 피크 시간대 판정 없이 항상 곱한다 (보수 계산)
      models:
        deepseek-flash:  { input: 0.15, cache-hit: 0.003, output: 0.60 }
        deepseek-v4-pro: { input: 0.66, cache-hit: 0.66,  output: 1.98 }   # cache-hit 단가 미확인 → 입력 단가로 보수 계산
    guards:
      language-min-letters: 30
      language-hangul-weight: 3
      language-min-ratio-bp: 4000
      no-answer-phrases: ["맞습니다", "틀렸습니다", "정답은", "사실은", "올바른 답은", "해야 합니다", "하면 됩니다", "를 쓰세요", "을 쓰세요"]   # NoAnswerGuard (17 §6.8)
    prompts:                                             # 활성 prompt 버전
      coach.review: v1
      coach.response-feedback: v1
      challenge.generate: v1
      challenge.evaluate: v1
      hint.generate: v1
      review.variant: v1
      review.evaluate: v1
      evidence.draft: v1
      requirement.extract: v1
      rubber.duck: v1
      rubber.duck.summary: v1
```

- 설정 바인딩은 `DevPilotProperties`(중첩 record)와 Bean Validation(`@Validated`)을 쓴다. 가중치 합이 1.0이 아니거나 threshold 순서가 틀리면 **기동 실패**로 처리한다.

---

## 10. Profile

| Profile | DB | AI | 인증 | 기타 |
|---|---|---|---|---|
| `local` | 서버 PostgreSQL 16 (`DATABASE_URL` 필수, 값은 저장소 밖 `DevPilot-ops/`). 오프라인 대체: `infra/compose.dev.yml`(포트 55432) | `fake` (실호출 확인 시 `.env`에서 `deepseek`) | `devtoken` (키 기동 시 생성) | CORS `http://localhost:5173`, Swagger UI 활성 |
| `test` | Testcontainers `postgres:16` (로컬 Docker 또는 서버 Docker 터널, `18` §3) | `fake` | 테스트 JWKS(로컬 EC 키, `TestJwksConfig`가 직접 등록) 또는 `jwt()` | 스케줄러 비활성: `@EnableScheduling`을 두는 `common.config.SchedulingConfig`에 `@Profile("!test")` |
| `prod` | 서버 PostgreSQL 16 (같은 호스트, TLS 없음) | `deepseek` | `devtoken` (`DEVPILOT_DEV_JWT_KEY` 필수) — Supabase는 Later | JSON 로그, Swagger 비활성 |
| `demo` | Docker PostgreSQL (demo seed) | `fake` | 데모 전용 로그인 없이 고정 사용자 | 쓰기 요청 차단 (Later) |

---

## 11. Source of Truth

| 데이터 | Source of truth | 비고 |
|---|---|---|
| 사용자 인증 정보 | `devtoken` 모드: allowlist 이메일(설정) + backend 서명 키. `supabase` 모드(Later): Supabase Auth | backend는 `sub`만 참조 |
| 프로필, 목표, 계획, skill state, 이벤트, 복습 | PostgreSQL `devpilot` 스키마 | |
| skill catalog, 목표 레벨, 계획 템플릿, seed 카드·문제 | 저장소 `content/*.yaml` → DB로 seed | DB는 사본. 수정은 YAML에서 |
| prompt | 저장소 `backend/src/main/resources/prompts/` | 버전 불변 |
| AI 대화 | 저장하지 않음 | 필요한 결과만 구조화해서 저장 |
| 설정 | `application.yml` + 환경변수 | |
