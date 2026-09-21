# 05. REST API Specification

> Status: Accepted (v2) · Last updated: 2026-09-20 · Related: FR-01~FR-29, ADR-039, ADR-040, DEC-28, DEC-29, DEC-30, DEC-31, AC-08, AC-12, AC-13, AC-23, AC-24, `03-system-architecture.md`, `04-domain-model-and-db.md`, `06-learning-engine-rules.md`, `database/schema.sql`
>
> 이 문서는 모든 REST endpoint의 **경로, 인증, 헤더, 요청/응답 record, 검증, 오류 코드, 동작 규칙**을 정의한다. 컨트롤러와 request/response record는 이 문서의 이름과 annotation을 그대로 쓴다. 알고리즘은 다시 쓰지 않고 `04`, `06`의 절을 참조한다.
>
> 최종 계약 산출물은 코드에서 생성한 OpenAPI 스냅샷(`docs/api/openapi.yaml`, §18)이다. 이 문서와 스냅샷이 다르면 둘 중 틀린 쪽을 같은 PR에서 고친다.

---

## 0. 읽는 법

| 표기 | 뜻 |
|---|---|
| `record ...` 코드 블록 | **규범(normative)**. 필드 이름, 타입, 순서, Jakarta Bean Validation annotation을 그대로 구현한다 |
| `IK` | `Idempotency-Key` 헤더 필수 (§1.7) |
| `429 AI_*` | `AI_DAILY_LIMIT_EXCEEDED`, `AI_MONTHLY_BUDGET_EXCEEDED`, `AI_CONCURRENCY_LIMIT` 세 가지 (§1.9.3) |
| record 주석 `null 가능` / nullable 래퍼 타입(`Integer`, `Boolean`) | 값이 `null`일 수 있음. 응답에서는 필드를 생략하지 않고 `null`로 내려준다 |
| "도메인 검사" | Bean Validation이 아니라 application service가 `BusinessValidationException`으로 던지는 400 `VALIDATION_FAILED` (§1.2.3의 field error code 사용) |
| 오류 행 | 그 endpoint가 **추가로** 반환할 수 있는 코드. 모든 인증 endpoint 공통 오류(§1.4.3)는 반복하지 않는다 |
| operationId | OpenAPI operationId (§18) |

Java 패키지: request/response record는 각 모듈의 `presentation` 패키지에 둔다(`03-system-architecture.md` §2.3). 공통 record(§2)는 `common.web`에 둔다.

---

## 1. 공통 규약

### 1.1 기본

| 항목 | 규칙 |
|---|---|
| Base path | `/api/v1` (예외: `GET /actuator/health`) |
| Content-Type | 요청·응답 `application/json` (UTF-8). 오류는 `application/problem+json`. 예외: ICS(`text/calendar`), Markdown export(`text/markdown`) |
| JSON 이름 | lowerCamelCase. 약어도 camelCase (`sourceUrl`, `aiMeta`) |
| 알 수 없는 속성 | **거부**한다. `spring.jackson.deserialization.fail-on-unknown-properties=true` → 400 `MALFORMED_REQUEST` |
| 기본형 필드 | `fail-on-null-for-primitives=true`에서는 record의 **primitive 필드를 생략하거나 `null`로 보내면 400** `MALFORMED_REQUEST`다(Java 기본값을 넣지 않는다). 그래서 **생략을 허용하는 값은 래퍼 타입**(`Integer`, `Boolean`)으로 받는다: 없어도 되는 flag(`force`, `wasVariant`, `evaluate`)는 생략하거나 `null`이면 `false`이고, 반드시 있어야 하는 값(`availableMinutes`, `responseSeconds`)에는 `@NotNull`을 붙여 생략하면 400 `VALIDATION_FAILED`(code `NotNull`)다. primitive로 남아 있는 필드(`skipped`, `acknowledgeEvidenceImpact`, `giveUp`)는 클라이언트가 **항상 보낸다** |
| null 표현 | 응답은 null 필드를 생략하지 않는다(`spring.jackson.default-property-inclusion=always`). 목록 필드는 비어 있으면 `[]`이고 `null`이 아니다 |
| PATCH 의미 | PATCH 요청의 nullable 필드가 `null`(또는 생략)이면 "변경하지 않음"이다. 값을 지우는 방법은 endpoint별로 적는다 |
| ID | UUID 문자열 (소문자, 하이픈 포함). path의 UUID 파싱 실패는 400 `VALIDATION_FAILED` (field = path 변수 이름, code `TYPE_MISMATCH`) |
| Instant | ISO-8601 UTC, `Z` 접미사 (`2026-10-05T19:00:00Z`). 소수 초는 0~6자리이며 클라이언트는 모두 파싱한다. `spring.jackson.serialization.write-dates-as-timestamps=false` |
| 날짜 | `yyyy-MM-dd` (`LocalDate`). 사용자 날짜는 모두 **plan-day**다(`06-learning-engine-rules.md` §2). 서버의 "오늘"은 `PlanDayCalculator.planDate(now, user.zone, user.dayStartHour)` |
| 시간 길이 | 분 단위 정수 (`estimatedMinutes`, `actualMinutes`). 초 단위는 `responseSeconds`만 |
| 비율·점수 | 비율은 bp 정수(`*Bp`, 0~10000), 평균 레벨은 milli 정수(`*Milli`). 부동소수점 JSON 숫자를 쓰지 않는다(`06-learning-engine-rules.md` §1) |
| 금액 | USD 문자열, 소수 2자리 고정 (`"12.34"`). 내부 계산은 micro USD 정수, 표시 반올림은 HALF_UP |
| Skill 레벨 | 정수 0~5 (`SkillLevel` ordinal, `04-domain-model-and-db.md` §3) |
| Enum | `04-domain-model-and-db.md` §3 이름 그대로, 대소문자 구분. **서버는 모르는 값을 400 `UNKNOWN_ENUM_VALUE`로 거절**한다(요청 body와 query 모두). `accept-case-insensitive-enums=false`, `read-unknown-enum-values-as-null=false`. **클라이언트는 모르는 값을 `unknown`으로 받아들인다**(Dart enum에 `unknown` fallback) |
| 사용자 식별 | JWT `sub`만 쓴다. body/path/query의 `userId`는 받지 않는다 |
| 소유권 | 타 사용자 리소스와 없는 리소스는 똑같이 404다(`03-system-architecture.md` §4.4, I-15) |
| 캐시 | 인증 API 응답은 `Cache-Control: no-store` (Spring Security 기본 헤더 유지) |
| CORS | 운영은 same-origin(`03-system-architecture.md` §1). `local` profile만 허용 origin 설정 |

### 1.2 오류 응답 (RFC 9457 Problem Details)

#### 1.2.1 형식

모든 오류는 `application/problem+json`이고 Spring `ProblemDetail`에 확장 속성 `code`, `traceId`, `errors`를 더한다(`03-system-architecture.md` §3.1 `GlobalExceptionHandler`).

```json
{
  "type": "urn:devpilot:problem:validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "입력값을 확인해 주세요.",
  "instance": "/api/v1/onboarding",
  "code": "VALIDATION_FAILED",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [
    { "field": "learningGoal.targetCompletionDate", "code": "DATE_OUT_OF_RANGE", "message": "허용 범위를 벗어난 날짜입니다." },
    { "field": "selfAssessments[2].level", "code": "Max", "message": "5 이하여야 합니다." }
  ]
}
```

| 필드 | 타입 | 규칙 |
|---|---|---|
| `type` | string (URI) | `urn:devpilot:problem:` + `code`를 소문자 kebab-case로 바꾼 값. 예: `PLAN_NOT_FOUND` → `urn:devpilot:problem:plan-not-found`. 도메인과 무관한 URN을 써서 계약이 배포 주소에 의존하지 않게 한다 |
| `title` | string | 코드별 고정 영어 문구(`ErrorCode.title`, §1.3) |
| `status` | int | HTTP status와 같다 |
| `detail` | string | 사용자에게 보여줄 한국어 문장. `messages_ko.properties`의 `error.<CODE>` 값(§1.3). 내부 메시지, SQL, 클래스명, stack trace를 넣지 않는다 |
| `instance` | string | 요청 경로(query string 제외). 캘린더 경로는 `/api/v1/calendar/****.ics`로 마스킹 |
| `code` | string | §1.3의 코드. **클라이언트 분기는 `code`로만** 한다 |
| `traceId` | string | 32 hex. 응답 헤더 `X-Trace-Id`와 같은 값 |
| `errors` | array | `FieldError` 목록. 해당 없으면 `[]` |

`FieldError`:

| 필드 | 규칙 |
|---|---|
| `field` | JSON 경로. 점과 index 표기: `learningGoal.focusSkillCodes[2]`, `milestones[0].endDate`. query/path 파라미터는 파라미터 이름(`limit`, `planId`), 헤더는 헤더 이름(`Idempotency-Key`) |
| `code` | Bean Validation annotation 단순 이름(`NotNull`, `Size`, …) 또는 §1.2.3 도메인 코드 |
| `message` | 한국어. `messages_ko.properties`의 `validation.<code>` 값에 annotation 속성(`{min}`, `{max}`)을 치환한 문장 |

#### 1.2.2 추가 응답 헤더

| 상황 | 헤더 |
|---|---|
| 모든 응답 | `X-Trace-Id` |
| 401 | `WWW-Authenticate: Bearer` |
| 429 | `Retry-After: <초>` (§1.4.3 4단계, §1.9.3, §3.6) |

#### 1.2.3 Field error code

Bean Validation code: `NotNull`, `NotBlank`, `NotEmpty`, `Size`, `Min`, `Max`, `Pattern`, `AssertTrue`, `UniqueElements`(Hibernate Validator), `URL`(Hibernate Validator).

도메인 code (`BusinessValidationException`):

| code | 뜻 | 사용처 |
|---|---|---|
| `TYPE_MISMATCH` | path/query 값 타입 변환 실패 (enum 제외 — enum은 `UNKNOWN_ENUM_VALUE`) | 모든 path UUID, date query |
| `TIMEZONE_INVALID` | IANA region ID가 아님 (`ZoneId.getAvailableZoneIds()`에 없음. `+09:00` 같은 offset ID 거부) | onboarding, PATCH /me |
| `SKILL_CODE_UNKNOWN` | 활성 skill catalog에 없는 code | skillCode(s)를 받는 모든 요청 |
| `DATE_ORDER_INVALID` | 시작일 > 종료일 | milestone, session 조회 |
| `DATE_OUT_OF_RANGE` | 허용 날짜 범위 밖 (§17) | 날짜 입력 전체 |
| `DATE_RANGE_TOO_LONG` | 조회 기간 초과 | `GET /learning-sessions` |
| `NOT_MONDAY` | 주 시작일이 월요일이 아님 | weekly review |
| `DUPLICATE_VALUE` | 목록 안 중복 (예: 같은 category 두 번, 같은 skillCode·axis 두 번) | onboarding, replan |
| `ONE_OF_REQUIRED` | 둘 중 하나 이상 필요 | submission(answerText/code), self-explanation(text/skipped) |
| `MUTUALLY_EXCLUSIVE` | 동시에 줄 수 없음 | self-explanation(text와 skipped=true) |
| `LANGUAGE_REQUIRED` | `code`가 있으면 `language` 필수 | submission |
| `VALUE_NOT_ALLOWED` | 값은 유효하지만 이 endpoint에서 허용하지 않음 | hint level, task/finding status, evidence draft의 원천 이벤트 종류, 유형에 맞지 않는 프로젝트 기록 항목(§19.8), `READ_CODE`가 아닌 task의 `readingFeedback`, `REDO`가 아닌 task의 `redoWithoutAi`, `EXPLAIN`·`READ_CODE`가 아닌 task의 `explainedToPerson`·`explainedNote` |
| `VALUE_REQUIRED` | 이 상황에서 반드시 있어야 하는 값이 없음 | `REDO` 과제 완료의 `redoWithoutAi`(§8.4), 유형에 필요한 프로젝트 기록 항목(§19.8) |
| `REQUIRED_FOR_ACCEPT` | accept에 필요한 필드가 비어 있음 | evidence accept |
| `ACTUAL_MINUTES_EXCEEDS_ELAPSED` | 실제 경과 시간 대비 과다 (§9.2) | session complete |
| `MILESTONE_NOT_IN_PLAN` | milestone `id`가 대상 plan 소속이 아님 | replan |
| `SKILL_NOT_IN_PLAN` | plan_skill_target에 없는 skill | replan |
| `TARGET_NOT_REDUCED` | `newTarget`이 현재 target 이상 | replan |
| `TARGET_NOT_RAISED` | `newTarget`이 현재 target 이하이거나 5 초과 | replan (`acceptedTargetRaises`) |
| `REFERENCE_NOT_FOUND` | body가 참조한 리소스가 없음 (사용자 소유 리소스는 타 사용자 소유 포함, catalog skill은 비활성 포함) | session(learningTaskId), evidence draft(sourceLearningEventId), challenge generate(skillId) |
| `NOT_BLANK_IF_PRESENT` | 값이 있으면 공백만으로 이루어질 수 없음 | PATCH 문자열 필드 |

### 1.3 오류 코드 카탈로그

`common.error.ErrorCode` enum과 1:1이다. `detail` 열은 `messages_ko.properties`의 `error.<CODE>` 값 그대로다. 비동기 작업 실패는 HTTP 오류가 아니라 리소스의 `failureCode`(`AsyncFailureCode`)로 표현한다(§1.8).

| HTTP | code | title | detail (`error.<CODE>`) | 발생 |
|---|---|---|---|---|
| 400 | `VALIDATION_FAILED` | Validation failed | 입력값을 확인해 주세요. | body/query/path 검증, 도메인 검사, `Idempotency-Key` 형식, `limit` 범위 |
| 400 | `UNKNOWN_ENUM_VALUE` | Unknown enum value | 지원하지 않는 값이 포함되어 있습니다. | enum 필드·query를 받는 모든 endpoint |
| 400 | `MALFORMED_REQUEST` | Malformed request | 요청 형식이 올바르지 않습니다. | JSON 파싱 실패, 알 수 없는 속성, body 타입 불일치, primitive null |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | Idempotency key required | Idempotency-Key 헤더가 필요합니다. | 리소스를 만들거나 AI를 호출하는 인증 `POST` (§1.7 IK endpoint) |
| 400 | `INVALID_CURSOR` | Invalid cursor | 목록 위치 정보가 올바르지 않습니다. 처음부터 다시 조회해 주세요. | `cursor`를 받는 모든 목록 |
| 401 | `AUTHENTICATION_REQUIRED` | Authentication required | 로그인이 필요합니다. | 모든 인증 endpoint (토큰 없음·만료·서명/iss/aud 오류. 세부 사유는 로그에만) |
| 403 | `USER_NOT_ALLOWED` | User not allowed | 이 서비스를 사용할 수 있는 계정이 아닙니다. | 모든 인증 endpoint (allowlist 거부 `03-system-architecture.md` §4.3, 익명 토큰 `is_anonymous = true` `03-system-architecture.md` §4.1) |
| 403 | `FORBIDDEN` | Forbidden | 이 작업을 수행할 권한이 없습니다. | 삭제 요청 상태 사용자의 요청(§1.4.2). MVP에 ADMIN 전용 endpoint는 없다(§1.4.2) |
| 403 | `RECENT_LOGIN_REQUIRED` | Recent login required | 보안을 위해 다시 로그인한 뒤 5분 안에 시도해 주세요. | `DELETE /me` |
| 404 | `RESOURCE_NOT_FOUND` | Resource not found | 요청한 항목을 찾을 수 없습니다. | id로 조회하는 모든 endpoint, 없는 경로, 캘린더 토큰 불일치 |
| 404 | `LEARNING_GOAL_NOT_FOUND` | Learning goal not found | 학습 목표가 없습니다. 먼저 목표를 설정해 주세요. | `GET/PUT /learning-goal`, `POST /plans`, replan preview, `GET /plans/active/budget` |
| 404 | `PLAN_NOT_FOUND` | Plan not found | 학습 계획을 찾을 수 없습니다. | `/plans/*`, `POST /today/generate` |
| 404 | `TODAY_NOT_GENERATED` | Today not generated | 오늘 계획이 아직 없습니다. | `GET /today` |
| 409 | `ONBOARDING_REQUIRED` | Onboarding required | 먼저 온보딩을 완료해 주세요. | 온보딩 전 호출 (§1.4.4) |
| 409 | `ONBOARDING_ALREADY_COMPLETED` | Onboarding already completed | 온보딩을 이미 완료했습니다. | `POST /onboarding` |
| 409 | `ACTIVE_PLAN_EXISTS` | Active plan exists | 이미 진행 중인 학습 계획이 있습니다. | `POST /plans` |
| 409 | `PLAN_NOT_ACTIVE` | Plan not active | 현재 계획이 아니어서 변경할 수 없습니다. 최신 계획을 다시 불러와 주세요. | milestone PATCH, replan preview/commit |
| 409 | `TODAY_ALREADY_STARTED` | Today already started | 오늘 과제를 이미 시작했습니다. 새로 만들려면 다시 만들기를 선택해 주세요. | `POST /today/generate` |
| 409 | `TODAY_ALREADY_COMPLETED` | Today already completed | 오늘 핵심 과제를 이미 완료했습니다. 추가 과제가 필요하면 다시 만들기를 선택해 주세요. | `POST /today/generate` |
| 409 | `SELF_EXPLANATION_REQUIRED` | Self-explanation required | 먼저 내 생각을 적거나 건너뛰기를 선택해 주세요. | challenge hint·submission, coach finding hint (HL-2) |
| 409 | `HINT_CONFIRMATION_REQUIRED` | Hint confirmation required | 이 단계의 힌트를 보면 학습 증거 판정에 반영됩니다. 확인한 뒤 다시 요청해 주세요. | hint endpoint (HL-4) |
| 409 | `FULL_EXAMPLE_NOT_ALLOWED` | Full example not allowed | 전체 예시는 한 번 이상 제출했거나 포기를 선택한 뒤에 볼 수 있습니다. | hint endpoint (HL-5) |
| 409 | `SUBMISSION_LIMIT_REACHED` | Submission limit reached | 이 시도의 제출 횟수를 모두 사용했습니다. | `POST .../submissions` |
| 409 | `EVALUATION_IN_PROGRESS` | Evaluation in progress | 이전 제출을 평가하는 중입니다. 평가가 끝난 뒤 다시 시도해 주세요. | `POST .../submissions`, attempt abandon |
| 409 | `AI_TASK_NOT_RETRYABLE` | AI task not retryable | 이 작업은 다시 시도할 수 없는 상태입니다. | submission retry, coach retry |
| 409 | `REVIEW_ALREADY_CLOSED` | Review already closed | 이미 완료한 코드 리뷰입니다. | coach finding 응답·hint·PATCH, coach complete |
| 409 | `AI_ASSIST_LOCKED_FOR_REDO` | AI assist locked for redo | 지금은 이 과제를 AI 없이 혼자 다시 만드는 중이에요. 재현 과제를 마치면 다시 쓸 수 있어요. | challenge hint(§10.8), 러버덕 시작(§9.6) — 열려 있는 재현 과제의 대상일 때 (`06` §5.10 RE-5, §9.1 HL-9) |
| 409 | `INVALID_STATE_TRANSITION` | Invalid state transition | 현재 상태에서는 이 작업을 할 수 없습니다. | 상태를 바꾸는 모든 endpoint (`04-domain-model-and-db.md` §4) |
| 409 | `CONCURRENT_MODIFICATION` | Concurrent modification | 다른 곳에서 먼저 변경되었습니다. 최신 내용을 불러온 뒤 다시 시도해 주세요. | `version`을 받는 요청, 동기 AI tx2 충돌, 동시 replan (AC-24) |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | Idempotency in progress | 같은 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요. | IK를 받는 모든 `POST` |
| 413 | `CONTENT_TOO_LARGE` | Content too large | 입력 내용이 너무 깁니다. 허용 크기 안으로 줄여 주세요. | coach content, submission code, 로드맵 비교 원문, 러버덕 설명 (§17) |
| 413 | `REQUEST_TOO_LARGE` | Request too large | 요청 크기가 허용 범위를 넘었습니다. | 모든 요청 (body > 64KB) |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Idempotency key reused | 같은 요청 키가 다른 요청에 사용되었습니다. 새로 시도해 주세요. | IK를 받는 모든 `POST` |
| 422 | `SECRET_DETECTED_BLOCKED` | Secret detected | 개인 키(private key)가 포함되어 있어 저장하지 않았습니다. 키를 지운 뒤 다시 시도해 주세요. | 자유 텍스트를 저장하는 endpoint 전체 (§1.11) |
| 429 | `AI_DAILY_LIMIT_EXCEEDED` | AI daily limit exceeded | 오늘 AI 사용 한도를 모두 사용했습니다. 복습과 계획은 계속 사용할 수 있습니다. | AI를 쓰는 endpoint (§1.9.4) |
| 429 | `AI_MONTHLY_BUDGET_EXCEEDED` | AI monthly budget exceeded | 이번 달 AI 예산을 모두 사용했습니다. 복습과 계획은 계속 사용할 수 있습니다. | 〃 |
| 429 | `AI_CONCURRENCY_LIMIT` | AI concurrency limit | 진행 중인 AI 작업이 있습니다. 끝난 뒤 다시 시도해 주세요. | 〃 |
| 429 | `RATE_LIMITED` | Rate limited | 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요. | 인증된 `/api/v1/**` 전체(`RateLimitFilter`, 사용자당 120 req/min, §1.4.3), `GET /calendar/{token}.ics`(§3.6) |
| 502 | `AI_OUTPUT_INVALID` | AI output invalid | AI 응답을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요. | 동기 AI (hint) |
| 502 | `AI_REFUSED` | AI refused | AI가 이 요청을 처리하지 않았습니다. 내용을 바꿔 다시 시도해 주세요. | 동기 AI (hint) |
| 503 | `AI_UNAVAILABLE` | AI unavailable | AI 기능을 잠시 사용할 수 없습니다. 복습과 계획은 그대로 사용할 수 있습니다. | 동기 AI (hint), 비동기 작업 시작 (`provider=disabled`) |
| 504 | `AI_TIMEOUT` | AI timeout | AI 응답이 늦어 요청을 끝내지 못했습니다. 잠시 후 다시 시도해 주세요. | 동기 AI (hint) |
| 500 | `INTERNAL_ERROR` | Internal error | 일시적인 오류가 발생했습니다. 문제가 계속되면 traceId와 함께 알려 주세요. | 모든 endpoint (최후 방어선) |

예외 → 코드 변환은 `03-system-architecture.md` §7을 따른다. 추가 규칙:

| 발생 | 변환 |
|---|---|
| `MissingRequestHeaderException` (헤더 `Idempotency-Key`) | 400 `IDEMPOTENCY_KEY_REQUIRED` |
| `MethodArgumentTypeMismatchException` (대상이 enum) | 400 `UNKNOWN_ENUM_VALUE` |
| `MethodArgumentTypeMismatchException` (그 외) | 400 `VALIDATION_FAILED`, field error `TYPE_MISMATCH` |
| `HandlerMethodValidationException` | 400 `VALIDATION_FAILED` + `errors[]` |
| `HttpMediaTypeNotSupportedException` | 400 `MALFORMED_REQUEST` |
| `HttpRequestMethodNotSupportedException` | 404 `RESOURCE_NOT_FOUND` (지원하지 않는 메서드를 노출하지 않음) |
| `DataIntegrityViolationException` | endpoint별 409 코드 (`03-system-architecture.md` §5.1 T-6). 매핑은 각 endpoint 절에 적는다 |

### 1.4 인증 · 사용자 상태 · 온보딩 가드

#### 1.4.1 인증

| 항목 | 규칙 |
|---|---|
| 방식 | `Authorization: Bearer <access token>` — `devtoken` 모드(기본)는 `POST /api/v1/dev/token`(§1.4.5)이 발급한 JWT, `supabase` 모드(Later)는 Supabase access token (`03-system-architecture.md` §4.2) |
| 인증 없음 | `GET /actuator/health`, `GET /api/v1/calendar/{token}.ics`(토큰 해시 인증, §3.6). `local` profile의 `/v3/api-docs/**`, `/swagger-ui/**` |
| 사용자 프로비저닝 | 첫 요청에서 JIT 생성, allowlist 확인 (`03-system-architecture.md` §4.3). 거부 시 403 `USER_NOT_ALLOWED` |
| 익명 토큰 | JWT `is_anonymous = true`이면 403 `USER_NOT_ALLOWED` (`03-system-architecture.md` §4.1, §4.2) |
| 역할 | 이 문서의 endpoint는 모두 `USER`로 호출 가능하다. ADMIN 전용 API는 없다 |

#### 1.4.2 삭제 요청 상태

`app_user.status = DELETION_REQUESTED`인 사용자는 `GET /me`와 `DELETE /me`(재요청, 202 재응답)만 호출할 수 있다. 그 외 요청은 403 `FORBIDDEN`이다(`03-system-architecture.md` §4.1 3-3).

#### 1.4.3 요청 검사 순서 (모든 endpoint 공통)

먼저 실패한 단계의 오류 하나만 반환한다.

| # | 단계 | 오류 |
|---|---|---|
| 1 | `TraceIdFilter` — 요청 `X-Trace-Id`가 `^[0-9a-f]{32}$`이면 사용, 아니면 새로 생성 | — |
| 2 | `RequestBodySizeLimitFilter` | 413 `REQUEST_TOO_LARGE` |
| 3 | JWT 인증 (`BearerTokenAuthenticationFilter`) | 401 `AUTHENTICATION_REQUIRED` |
| 4 | `RateLimitFilter` — JWT `sub`별 token bucket(용량 120, 초당 2개 보충 = 120 req/min, `devpilot.security.rate-limit.requests-per-minute`). `Retry-After` = 다음 토큰까지 초(올림, 최소 1). 대상: 인증된 `/api/v1/**` 전체(캘린더 피드·health 제외) | 429 `RATE_LIMITED` |
| 5 | `UserContextFilter` — allowlist, 익명 토큰, 삭제 요청 상태 | 403 `USER_NOT_ALLOWED`, 403 `FORBIDDEN` |
| 6 | 온보딩 가드 (§1.4.4) | 409 `ONBOARDING_REQUIRED` |
| 7 | `Idempotency-Key` 존재 (IK endpoint) | 400 `IDEMPOTENCY_KEY_REQUIRED` |
| 8 | path/query/body 바인딩 | 400 `MALFORMED_REQUEST`, `UNKNOWN_ENUM_VALUE`, `VALIDATION_FAILED(TYPE_MISMATCH)` |
| 9 | Bean Validation (`@Valid`) | 400 `VALIDATION_FAILED` |
| 10 | 컨트롤러 크기 검사 (§17의 413 항목) | 413 `CONTENT_TOO_LARGE` |
| 11 | `IdempotencyService` — 키 형식 → record 확인 (§1.7) | 400 `VALIDATION_FAILED`, 422 `IDEMPOTENCY_KEY_REUSED`, 409 `IDEMPOTENCY_IN_PROGRESS`, 또는 저장된 응답 재생 |
| 12 | Application service — 리소스 조회 → 상태 검사 → 도메인 검사 → (secret masking → AI 예산) → 저장 → (AI) | 404, 409, 400, 422, 429, 503 … endpoint 절의 순서가 있으면 그것을 따른다 |

컨트롤러 메서드 파라미터 순서는 `CurrentUser`, `@RequestHeader("Idempotency-Key") String idempotencyKey`, path/query 파라미터, `@Valid @RequestBody` 순서로 고정한다(7이 8·9보다 먼저 실행되도록).

필터 위치는 `03-system-architecture.md` §4.1을 따른다: `RateLimitFilter`는 Spring Security chain에서 `BearerTokenAuthenticationFilter` 다음, `UserContextFilter` 앞이다. 한도 세부(버킷 맵 상한, 정리 주기)는 `07-security-and-privacy.md` §12.3이다.

#### 1.4.4 온보딩 가드

`app_user.onboarding_completed_at IS NULL`인 사용자가 아래 **예외 목록 밖**의 인증 endpoint를 호출하면 409 `ONBOARDING_REQUIRED`다.

| 온보딩 전 허용 endpoint | 이유 |
|---|---|
| `GET /me` | 온보딩 여부 확인 |
| `PATCH /me` | 설정 |
| `POST /onboarding` | 온보딩 자체 |
| `GET /skills/tree` | 온보딩 화면의 catalog 표시 |
| `GET /me/export`, `DELETE /me` | 개인정보 권리(NFR-05)는 온보딩과 무관하게 보장 |

- 구현: `UserContextFilter` 뒤, 컨트롤러 인자 해석 전에 실행되는 Spring MVC `HandlerInterceptor`(`common.web.OnboardingRequiredInterceptor`). 예외 목록은 `(method, path pattern)` 상수로 둔다.
- 판정 값은 `CurrentUser.onboardingCompleted`다(`03-system-architecture.md` §3.1). `UserContextFilter`가 요청마다 `app_user`에서 읽어 채운다.

#### 1.4.5 개발 토큰 endpoint (`auth-mode = devtoken`)

`devpilot.security.auth-mode = devtoken`(기본값, 운영 포함 — 2026-09-18 결정 A, `03-system-architecture.md` §4.2)일 때만 bean이 등록되는 endpoint다. `supabase` 모드에서는 존재하지 않아 404 `RESOURCE_NOT_FOUND`다. 둘 다 인증·IK·온보딩 가드 밖이며, `RateLimitFilter`가 **IP당 30회/시간**(`devpilot.security.rate-limit.dev-token-per-hour-per-ip`)으로 제한한다. tailnet 밖에서 닿을 수 없다는 배포 전제(`07-security-and-privacy.md` §3) 위에서만 허용된다.

##### `POST /api/v1/dev/token` — 개발 토큰 발급

| 항목 | 값 |
|---|---|
| operationId | `devIssueToken` |
| 인증 / IK | 없음 / — |
| 요청 | `DevTokenRequest` |
| 응답 | 200 `DevTokenResponse` |
| 오류 | 400 `VALIDATION_FAILED`, 403 `USER_NOT_ALLOWED`, 429 `RATE_LIMITED` |
| Sprint · 요구사항 | S0 · BL-SEC-16, AC-15 |

```java
public record DevTokenRequest(@NotBlank @Email @Size(max = 254) String email) {}
public record DevTokenResponse(String accessToken, String tokenType, Instant expiresAt) {}   // tokenType = "Bearer"
```

처리:
1. `email`을 소문자·trim한다. `devpilot.security.allowed-emails`에 없으면 403 `USER_NOT_ALLOWED` + 감사 로그 `AUTH_DEVTOKEN_REJECTED`(`userRef` 대신 email SHA-256 앞 12 hex).
2. `DevTokenService`가 EC P-256 키로 JWT를 서명한다. claims: `iss = ${APP_BASE_URL}/dev`, `aud = authenticated`, `sub = UUID v5(namespace DNS, email)`, `email`, `amr = [{"method":"devtoken","timestamp":<iat>}]`, `iat`, `exp = iat + token-ttl`(720h).
3. 감사 로그 `AUTH_DEVTOKEN_ISSUED`. 사용자 행은 만들지 않는다 — 첫 인증 요청에서 `03-system-architecture.md` §4.3 JIT 프로비저닝이 만든다.

```json
{ "email": "<your-email>" }
```
```json
{ "accessToken": "eyJhbGciOiJFUzI1NiIs…", "tokenType": "Bearer", "expiresAt": "2026-10-18T09:00:00Z" }
```

##### `GET /api/v1/dev/jwks.json` — 검증용 공개키

| 항목 | 값 |
|---|---|
| operationId | `devJwks` |
| 인증 / IK | 없음 / — |
| 응답 | 200 JWKS `{"keys":[{"kty":"EC","crv":"P-256","kid":"…","x":"…","y":"…","alg":"ES256","use":"sig"}]}`, `Cache-Control: no-store` |
| Sprint | S0 |

backend 자신은 이 endpoint를 호출하지 않는다(메모리의 공개키로 검증). 외부 도구·디버그용이다.

### 1.5 Cursor pagination

목록 endpoint는 모두 cursor 방식이다(offset 없음).

| 항목 | 규칙 |
|---|---|
| query | `limit` (기본 20, 1~100, 범위 밖이면 400 `VALIDATION_FAILED` — clamp하지 않음), `cursor` (선택) |
| 응답 | `CursorPage<T>` = `{ "items": [...], "nextCursor": "..." \| null }` (§2.1). 전체 개수는 주지 않는다 |
| 마지막 페이지 | `nextCursor = null` |
| 정렬 | endpoint마다 고정 `(sortKey, id)`. 클라이언트가 정렬을 고르지 않는다 |
| cursor 내용 | `CursorCodec`(`03-system-architecture.md` §3.1)이 JSON `{"v":1,"k":"<sortKey 문자열>","id":"<uuid>"}`를 base64url(패딩 없음)로 인코딩. `k`는 Instant면 ISO-8601, 날짜면 `yyyy-MM-dd`, 정수면 10진 문자열. (filter scope 해시 `f`는 2026-09-18 범위 축소로 제거 — 필터를 바꾸면 클라이언트가 cursor를 버린다) |
| 불투명성 | 클라이언트는 cursor를 해석·생성하지 않는다 |
| 콘텐츠 목록 | 팁·용어 목록(§20)은 DB 행이 아니라 콘텐츠라 UUID id가 없다. 이때 cursor의 `id`는 빈 문자열이고 비교는 `k`(= 콘텐츠 `key`, 유일하다)만으로 한다. 그 밖의 규칙은 같다 |
| 오류 | base64url/JSON 디코딩 실패, `v ≠ 1`, `k` 타입 불일치 → 400 `INVALID_CURSOR` |
| 다음 페이지 조건 | sortKey DESC 목록: `(sortKey, id) < (k, id)` / ASC 목록: `(sortKey, id) > (k, id)`. 서버는 `limit + 1`개를 읽어 다음 페이지 존재를 판단한다 |

### 1.6 Optimistic locking

| 항목 | 규칙 |
|---|---|
| 응답 | 수정 가능한 리소스의 응답에는 JPA `@Version` 값 `version`(int64 정수)을 넣는다 |
| 요청 | 모든 `PATCH`, `PUT` body는 `@NotNull Long version`을 받는다. replan은 대상 plan의 `version`을 body로 받는다 |
| 비교 | 서비스가 조회한 entity의 version과 요청 version이 다르면 409 `CONCURRENT_MODIFICATION` (flush 시 `ObjectOptimisticLockingFailureException`도 같은 코드) |
| 성공 응답 | 증가한 새 `version`을 포함한 리소스 전체 |
| POST | IK로 중복을 막고 `version`을 받지 않는다(`03-system-architecture.md` §5.4). 동기 AI endpoint는 tx1과 tx2 사이 version 변경 시 409 `CONCURRENT_MODIFICATION` (예외: §12.5 coach 응답 피드백은 409 없이 피드백만 버린다) |
| 계획 버전 번호 | `planVersion`(= `learning_plan.plan_version`)은 `version`과 다른 필드다 |

### 1.7 Idempotency (AC-23)

| 항목 | 규칙 |
|---|---|
| 대상 | 인증이 필요한 `POST` 중 **리소스를 만들거나 AI를 호출하는 것**(각 endpoint 표의 "인증 / IK" 열이 `IK`인 것). 예외(IK 열이 `—`): `POST /plans/{planId}/replan/preview`(계산만), `POST /learning-sessions/{id}/abandon`, `POST /challenge-attempts/{id}/abandon`, `POST /rubber-duck/{id}/abandon`(상태 전이가 자연히 멱등 — 두 번째 호출은 409 또는 같은 결과), `POST /lessons/{k}/units/{u}/predict`·`POST /lessons/{k}/units/{u}/complete`(§21.4·§21.5 — 채점만 하고 아무것도 만들지 않는다. 같은 답은 항상 같은 결과다), `POST /api/v1/dev/token`(인증 없음). 예외 endpoint는 헤더를 받아도 무시하고 record를 만들지 않는다 |
| 헤더 | `Idempotency-Key: <key>`. 형식 `^[A-Za-z0-9_-]{8,100}$`. 없으면 400 `IDEMPOTENCY_KEY_REQUIRED`, 형식 오류면 400 `VALIDATION_FAILED`(field `Idempotency-Key`, code `Pattern`) |
| 클라이언트 | 사용자 행동 1회마다 UUID v4를 새로 만든다. 네트워크 재시도에는 같은 키를 쓴다 |
| 범위 | `(user_id, key)` 단위(`idempotency_record` PK). 다른 사용자의 같은 키는 무관 |
| request hash | `SHA-256(method + " " + path + "\n" + canonical JSON body)`. canonical = 키 사전순 정렬, 공백 제거. body가 없거나 비어 있으면 빈 문자열. path는 query 제외 |
| 처리 | `03-system-architecture.md` §5.4. 같은 키 + 다른 hash → 422 `IDEMPOTENCY_KEY_REUSED` / 처리 완료 → 저장된 status·body 재생 / 처리 중 → 409 `IDEMPOTENCY_IN_PROGRESS` |
| 재생 응답 | 저장된 HTTP status와 JSON body를 그대로 반환하고 헤더 `Idempotent-Replayed: true`를 붙인다. 재생 body는 **최초 처리 시점의 스냅샷**이다(예: 202 body의 `status`는 계속 `PENDING`). 최신 상태는 GET으로 확인한다. 예외: secret을 담은 응답은 원문을 저장하지 않는다 — `POST /me/calendar-token`의 record는 status 200, `feedUrl: null`로 저장되어 재생 응답도 그렇다(`03-system-architecture.md` §5.4). 세부는 이 문서 §3.5 |
| 저장 대상 | 2xx 응답만 저장한다. action이 예외(4xx/5xx)로 끝나면 record를 삭제하므로 같은 키로 다시 시도할 수 있다 |
| 헤더 저장 | 응답 헤더는 저장하지 않는다. 그래서 IK endpoint는 `Location` 등 재생이 필요한 헤더를 쓰지 않는다 |
| TTL | 24시간(`devpilot.privacy.idempotency-ttl`). 만료 후 같은 키는 새 요청으로 처리한다 |
| 409 처리 중 대응 | 클라이언트는 1초 후 같은 키로 최대 5회 재시도한다 |

### 1.8 비동기 리소스

`CHALLENGE_GENERATE`, `CHALLENGE_EVALUATE`, `COACH_REVIEW`, `EVIDENCE_DRAFT`, `REQUIREMENT_EXTRACT`는 비동기다(`03-system-architecture.md` §5.3). `REVIEW_VARIANT`도 비동기지만 전용 endpoint가 없다(§11.2, **Later** — 2026-09-18 범위 축소).

| 항목 | 규칙 |
|---|---|
| 시작 응답 | `202 Accepted` + `AsyncStatusView` (§2.3). `status = PENDING`, `failureCode = null`, `pollPath` = 폴링할 GET 경로 |
| 시작 전 검사 순서 | 검증(400/413) → (coach: secret masking 422) → AI 가능 여부·예산(503/429, §1.9) → 저장(PENDING) → 커밋 후 비동기 실행 |
| 폴링 | 클라이언트는 `pollPath`를 **2초 간격, 최대 3분** 조회한다. 3분이 지나면 폴링을 멈추고 "처리 중" 표시와 수동 새로고침을 제공한다 |
| 상태 | `AsyncJobStatus`: `PENDING → RUNNING → COMPLETED \| FAILED` |
| 실패 코드 | `failureCode`: `AsyncFailureCode` (`04-domain-model-and-db.md` §3). `FAILED`일 때만 값이 있다 |
| 고아 작업 | `PENDING` 또는 `RUNNING`이 10분 넘게 갱신되지 않으면 `OrphanAsyncTaskJob`이 `FAILED(INTERRUPTED)`로 바꾼다(`03-system-architecture.md` §5.3) |
| 실행 시점 예산 초과 | 시작 시 통과했지만 실행 직전 예산을 넘으면 `FAILED(AI_BUDGET_EXCEEDED)`. 실행 중 공급자 잔액 소진(402)이면 `FAILED(AI_UNAVAILABLE)` + `aiStatus = BALANCE_EXHAUSTED` |
| 재시도 endpoint | `POST /challenge-attempts/{attemptId}/submissions/{submissionNo}/retry`, `POST /coach/reviews/{reviewId}/retry`. `FAILED`만 허용, 그 외 409 `AI_TASK_NOT_RETRYABLE`. 재시도도 IK 필수이고 §1.9 예산 검사를 다시 한다. 응답은 202 `AsyncStatusView` |
| 재시도 불가 실패 | `failureCode ∈ {CONFIDENTIAL_SUSPECTED, AI_REFUSED}` (같은 입력이면 같은 결과), 원문이 purge된 coach review → 409 `AI_TASK_NOT_RETRYABLE` |
| 재시도 없는 작업 | challenge 생성, evidence 초안, 로드맵 비교 분석은 새로 요청한다 |

리소스별 상태 필드 이름:

| 리소스 | 상태 필드 | GET |
|---|---|---|
| Challenge 생성 | `generationStatus` | `GET /challenges/{challengeId}` |
| Submission 평가 | `submissions[].evaluationStatus` | `GET /challenge-attempts/{attemptId}` |
| Coach review 분석 | `status` | `GET /coach/reviews/{reviewId}` |
| Evidence 초안 | `generationStatus` (`EvidenceGenerationStatus`: 요청한 적 없으면 `NONE`) | `GET /evidence/{evidenceId}` |
| 로드맵 비교 분석 | `analysisStatus` | `GET /requirement-docs/{requirementDocId}` |

모든 비동기 리소스 view는 상태 필드 옆에 `failureCode`, `statusUpdatedAt`을 함께 둔다. 상태 타입은 `AsyncJobStatus`이고, evidence만 `NONE`을 포함하는 `EvidenceGenerationStatus`다(`04-domain-model-and-db.md` §3). 202 응답의 `AsyncStatusView.status`는 모든 리소스에서 `PENDING`이다.

### 1.9 AI 공통

#### 1.9.1 AI 상태와 사용량 (`GET /me`)

| 필드 | 계산 |
|---|---|
| `aiUsage.todayCalls` | 요청 사용자의 `ai_call_log` 중 `status <> 'BUDGET_BLOCKED'`이고 `created_at >= planDayStart(오늘)`인 행 수 (가드 재시도 `attempt_no=2`도 1행으로 센다) |
| `aiUsage.dailyCallLimit` | `devpilot.ai.daily-call-limit-per-user` |
| `aiUsage.monthCostUsd` | 전체 사용자 `ai_call_log.cost_micro_usd` 합계 중 `created_at`이 **`devpilot.time.default-zone`(Asia/Seoul) 기준 이번 달력 월**(1일 00:00 ~ 다음 달 1일 00:00)에 속한 것. 월 예산은 서비스 전체 한도이므로 경계도 모든 사용자에게 같다(DEC-06, `17-ai-integration.md` §8.2). 계정 삭제로 `user_id`가 null이 된 행도 포함한다. 문자열, 소수 2자리, HALF_UP |
| `aiUsage.monthlyBudgetUsd` | `devpilot.ai.monthly-budget-usd`, 문자열 소수 2자리 |
| `aiStatus` | `provider = disabled` → `DISABLED` / 월 비용(micro) ≥ 예산(micro) → `DISABLED` / `AiBalanceMonitor`가 잔액 소진 상태(402 수신 또는 잔액 < `min-balance-usd`, `17-ai-integration.md` §8.7) → `BALANCE_EXHAUSTED` / 월 비용 × 10000 ≥ 예산 × `budget-warning-ratio`(bp) → `BUDGET_WARNING` / 그 외 `ENABLED`. 일일 한도 도달은 `aiStatus`에 반영하지 않는다(`todayCalls`로 표시) |

`AiBudgetGuard`의 차단 판정도 같은 계산을 쓴다(월 비용 경계, 일일 호출 수). 검사 알고리즘은 `17-ai-integration.md` §8.2, `aiStatus` test vector는 `17-ai-integration.md` §8.5다.

#### 1.9.2 동시 실행 수

`AI_CONCURRENCY_LIMIT` 판정: 요청 사용자의 (진행 중인 동기 AI 호출 수 + `PENDING`/`RUNNING` 상태 비동기 작업 수) ≥ `devpilot.ai.max-concurrent-per-user`(2).

#### 1.9.3 AI 차단 오류 (호출 전)

| 조건 (이 순서로 검사) | 오류 | `Retry-After` |
|---|---|---|
| `provider = disabled` | 503 `AI_UNAVAILABLE` | 없음 |
| 공급자 잔액 소진 (`aiStatus = BALANCE_EXHAUSTED`) | 503 `AI_UNAVAILABLE` (`detail`: "AI 잔액이 소진되었습니다") | `3600` (다음 잔액 확인 주기) |
| 월 예산 도달 | 429 `AI_MONTHLY_BUDGET_EXCEEDED` | `devpilot.time.default-zone`(Asia/Seoul) 기준 다음 달 1일 00:00까지 초 (모든 사용자 동일) |
| 일일 호출 한도 도달 | 429 `AI_DAILY_LIMIT_EXCEEDED` | 다음 plan-day 시작까지 초 |
| 동시 실행 한도 | 429 `AI_CONCURRENCY_LIMIT` | `5` |

차단 시 `AiGateway`/`AiBudgetGuard`가 `BUDGET_BLOCKED` 로그를 별도 트랜잭션으로 남긴다(`03-system-architecture.md` §5.3).

#### 1.9.4 AI를 쓰는 endpoint의 실패 처리

| 유형 | endpoint | 차단(§1.9.3) | 호출 실패 |
|---|---|---|---|
| 동기, 대체 없음 | `POST /challenge-attempts/{id}/hints` (AI 생성 단계, §10.8), `POST /coach/reviews/{id}/findings/{fid}/hints`, `POST /rubber-duck/{id}/turns` (§9.7) | HTTP 오류 | 502 `AI_OUTPUT_INVALID` / 502 `AI_REFUSED` / 503 `AI_UNAVAILABLE`(공급자 오류·rate limit 포함) / 504 `AI_TIMEOUT`. 아무것도 저장하지 않음 |
| 동기, 대체 있음 | `POST /reviews/{id}/answer` (`evaluate=true`), `POST /coach/reviews/{id}/findings/{fid}/responses`, `POST /rubber-duck/{id}/complete` (§9.8) | **오류 없음** — skip reason으로 표시 | **오류 없음** — 답변/응답은 저장하고 skip reason 반환 |
| 비동기 시작 | challenge generate, submissions, submission retry, coach create/retry, evidence drafts, requirement docs | HTTP 오류, 저장 안 함 | 리소스 `FAILED` + `failureCode` |

skip reason(`evaluationSkippedReason`, `feedbackSkippedReason`, `summarySkippedReason`)의 타입은 `AsyncFailureCode`다.

러버덕의 두 동기 operation은 실패 처리가 서로 다르다(`17-ai-integration.md` §3.10). 턴(`RUBBER_DUCK`)은 **대체가 없다** — 턴을 저장하지 않고 `turn_count`도 늘지 않으며 클라이언트가 설명 원문을 유지한다. 종료 정리(`RUBBER_DUCK_SUMMARY`)는 **대체가 있다** — 세션은 `COMPLETED`가 되고 `summary`는 `null`, `summarySkippedReason`에 사유를 담으며 복습 카드와 학습 이벤트를 만들지 않는다. 대화 기록 자체가 학습이므로 세션을 실패로 만들지 않는다.

가드 위반 재시도(`attempt_no = 2`)는 **비동기 operation에만** 한다. 동기 operation(`HINT_GENERATE`, `REVIEW_EVALUATE`, `COACH_RESPONSE_FEEDBACK`, `RUBBER_DUCK`, `RUBBER_DUCK_SUMMARY`)은 가드 위반 시 재시도 없이 hint와 러버덕 턴은 502 `AI_OUTPUT_INVALID`, 나머지 셋은 skip reason `AI_OUTPUT_INVALID`로 처리한다(`03-system-architecture.md` §5.3). 원인별 표면 매핑의 기준표는 `17-ai-integration.md` §5.4다.

| 원인 | skip reason / `failureCode` |
|---|---|
| provider disabled, 공급자 5xx·연결 오류 | `AI_UNAVAILABLE` |
| 월 예산, 일일 한도 | `AI_BUDGET_EXCEEDED` |
| 동시 실행 한도, 공급자 429 | `AI_RATE_LIMITED` |
| 타임아웃 | `AI_TIMEOUT` |
| 공급자 콘텐츠 필터(DeepSeek `finish_reason = content_filter`, `17-ai-integration.md` §5.3) | `AI_REFUSED` (같은 입력 재시도 불가 → 비동기 리소스의 `/retry`는 409 `AI_TASK_NOT_RETRYABLE`) |
| 공급자 선불 잔액 소진(호출 중 HTTP 402) | `AI_UNAVAILABLE` (이후 요청은 §1.9.3에서 503으로 차단, `AiBalanceCheckJob`이 해제) |
| 스키마·가드 위반 (비동기는 가드 재시도 1회 후, 동기는 재시도 없이 즉시) | `AI_OUTPUT_INVALID` |
| 서버 재시작으로 중단 | `INTERRUPTED` (비동기만) |
| AI가 회사/비밀 코드로 판단 | `CONFIDENTIAL_SUSPECTED` (coach만) |
| 그 외 예외 | `INTERNAL_ERROR` |

#### 1.9.5 `aiMeta`

AI 결과를 보여주는 응답에는 `aiMeta`(§2.4)를 넣는다. AI 결과가 없으면 `null`이다.

| 필드 | 값 |
|---|---|
| `model` | `ai_call_log.model` (예: `deepseek-flash`, fake provider면 `fake`) |
| `promptVersion` | `"{prompt_id}@{prompt_version}"` (예: `coach.review@v1`) |
| `guardActions` | `ai_call_log.guard_actions` 배열(`04-domain-model-and-db.md` §5.6) |

- 저장된 결과는 `ai_call_id`로 `ai_call_log`를 읽어 만든다. `ai_call_log`가 보존기간(180일) 경과로 삭제되어 `ai_call_id = null`이면 `aiMeta = null`이다.
- 사용처와 참조 컬럼: challenge(AI 생성, `challenge.ai_call_id`), submission 평가(`challenge_submission.ai_call_id`), hint(AI 생성, `hint_disclosure.ai_call_id`), review answer 평가(`review_answer.ai_call_id`), coach review 분석(`coach_review.ai_call_id`), coach 응답 피드백(`coach_finding.feedback_ai_call_id`), evidence 초안(`evidence_candidate.ai_call_id`), 요구사항 분석(`requirement_doc.ai_call_id`).

### 1.10 크기 제한과 헤더 요약

| 항목 | 규칙 |
|---|---|
| 요청 body 상한 | 64KB (`devpilot.security.max-request-body-bytes = 65536`). `Content-Length` 또는 실제 읽은 바이트 기준. 초과 시 413 `REQUEST_TOO_LARGE` (인증보다 먼저) |
| 필드별 상한 | §17. JSON escape로 body가 커질 수 있으므로 클라이언트는 필드 상한과 body 상한을 모두 검사한다 |

| 헤더 | 방향 | 사용 |
|---|---|---|
| `Authorization` | 요청 | 인증 endpoint 전체 |
| `Idempotency-Key` | 요청 | 인증 `POST` 전체 (§1.7) |
| `X-Trace-Id` | 요청(선택)·응답(항상) | §1.4.3 #1 |
| `Idempotent-Replayed: true` | 응답 | 재생 응답 |
| `Retry-After` | 응답 | 429 |
| `Content-Disposition: attachment; filename="..."` | 응답 | `GET /me/export`, `GET /evidence/export`, `GET /side-projects/{id}/notes/export`(§19.13) |

### 1.11 Secret masking

사용자 자유 텍스트는 **저장하거나 AI에 보내기 전에** `SecretMasker`로 마스킹한다(`17-ai-integration.md` §7.1). 적용 endpoint와 필드:

| endpoint | 필드 |
|---|---|
| `POST /coach/reviews` | `content`, `userSelfReview` |
| `POST /coach/reviews/{reviewId}/findings/{findingId}/responses` | `text` |
| `POST /challenge-attempts/{attemptId}/self-explanation` | `text` |
| `POST /challenge-attempts/{attemptId}/submissions` | `answerText`, `code` |
| `POST /reviews/{reviewItemId}/answer` | `answerText` |
| `POST /learning-sessions/{sessionId}/complete` | `selfReflection` |
| `PATCH /today/tasks/{taskId}` | `explainedNote` (§8.4, I-24) |
| `POST /rubber-duck/{sessionId}/turns` | `explanation` (RD-6, 원문은 남기지 않는다) |
| `POST /requirement-docs` | `sourceText` |
| `POST /review-items` | `prompt`, `expectedAnswer`, `rubric[]` 항목 |
| `PATCH /review-items/{reviewItemId}` | `prompt`, `expectedAnswer` |
| `POST /evidence`, `PATCH /evidence/{evidenceId}` | `title`, `problem`, `analysis`, `action`, `result`, `explanationTopics[]` 항목 |
| `PUT /weekly-reviews/{weekStartDate}/reflection` | `reflection` |
| `POST /plans/{planId}/replan`, `POST /plans/{planId}/replan/preview` | `reason`, `milestones[].title`, `milestones[].description` |
| `PATCH /plans/{planId}/milestones/{milestoneId}` | `description` |
| `POST /side-projects`, `PATCH /side-projects/{sideProjectId}` | `name`, `description`, `stack` (`repoUrl`은 URL 검증만, §19.2) |
| `POST /side-projects/{sideProjectId}/notes`, `PATCH …/notes/{noteId}` | `title`, `decisionChoice`, `decisionOptions`, `decisionRationale`, `incidentSymptom`, `incidentDetection`, `incidentFix`, `incidentPrevention` (PN-4, §19.9) |
| `POST /onboarding` | `sideProject.name`, `sideProject.description`, `sideProject.stack` |

- 이 표가 "자유 텍스트를 저장하는 endpoint 전체"(§1.3 `SECRET_DETECTED_BLOCKED`)다. `displayName`, code·enum·URL·날짜 필드는 대상이 아니다. preview는 저장하지 않지만 commit과 같은 입력이므로 같은 결과(422)를 미리 돌려준다.
- 순서: Bean Validation(400) → 크기 검사(413, 원문 기준) → `IdempotencyService`(IK endpoint만) → **마스킹** → 리소스 조회·상태 검사(404/409) → (AI 차단 검사) → 저장 → (AI). 마스킹은 application service의 첫 단계다.
- 차단 패턴(private key 블록, `17-ai-integration.md` §7.2)이 한 필드라도 있으면 422 `SECRET_DETECTED_BLOCKED`. 아무것도 저장하지 않고 AI를 호출하지 않는다. 감사 로그 `SECRET_BLOCKED`(개수·type만).
- 차단이 아니면 마스킹본을 저장하고, 이후 응답·조회·AI 입력에는 마스킹본만 쓴다. 치환 문자열은 `17-ai-integration.md` §7.2를 따른다.
- 마스킹 개수를 저장·응답하는 곳은 coach review(`maskedSecretCount`)뿐이다.

---

## 2. 공통 DTO

패키지 `com.devpilot.common.web` (AI 관련 값은 `integration.ai.api` 타입을 presentation에서 변환). 모든 record는 불변이고 목록 필드는 `List.copyOf`로 방어 복사한다.

### 2.1 `CursorPage<T>`

```java
public record CursorPage<T>(
        List<T> items,          // 비어 있으면 []
        String nextCursor) {}   // 마지막 페이지면 null
```

목록 query 파라미터는 컨트롤러에서 다음처럼 받는다(클래스에 `@Validated`).

```java
@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
@RequestParam(required = false) @Size(max = 512) String cursor
```

### 2.2 Skill 참조 · 축별 레벨

```java
public record SkillRef(
        UUID id,
        String code,            // 예: "SPRING.TRANSACTION"
        String name,
        SkillCategory category) {}

public record AxisLevels(       // SkillAxis 순서. 각 0~5
        int knowledge,
        int implementation,
        int explanation,
        int debugging) {}
```

### 2.3 `AsyncStatusView` (202 응답 body)

모든 비동기 시작·재시도 endpoint의 202 body다(§1.8).

```java
public record AsyncStatusView(
        UUID id,                        // 폴링 대상 리소스 id: challengeId | attemptId | coachReviewId | evidenceId | requirementDocId
        Integer submissionNo,           // submission 평가·재시도만 값, 그 외 null
        AsyncJobStatus status,          // 202에서는 항상 PENDING
        AsyncFailureCode failureCode,   // 202에서는 항상 null
        Instant statusUpdatedAt,
        String pollPath,                // base path 포함, host 제외. 예: "/api/v1/coach/reviews/0b6f…"
        Integer maskedSecretCount) {}   // coach review 생성·재시도만 값, 그 외 null
```

```json
{
  "id": "0b6f8d0e-6c7a-4a57-9a3f-2d7f0f0d9c11",
  "submissionNo": null,
  "status": "PENDING",
  "failureCode": null,
  "statusUpdatedAt": "2026-11-20T11:02:13.412Z",
  "pollPath": "/api/v1/coach/reviews/0b6f8d0e-6c7a-4a57-9a3f-2d7f0f0d9c11",
  "maskedSecretCount": 2
}
```

### 2.4 `AiMeta`

```java
public record AiMeta(
        String model,
        String promptVersion,                 // "{promptId}@{version}"
        List<GuardActionView> guardActions) {}

public record GuardActionView(
        String guard,    // ai_call_log.guard_actions[].guard  (04 §5.6)
        String action,   // ai_call_log.guard_actions[].action
        String detail) {}
```

### 2.5 Problem Details 확장

```java
// GlobalExceptionHandler가 ProblemDetail.setProperty("errors", ...)로 넣는 항목
public record FieldErrorView(String field, String code, String message) {}

// properties: "code" -> ErrorCode.name(), "traceId" -> MDC traceId, "errors" -> List<FieldErrorView> (없으면 빈 목록)
// type = URI.create("urn:devpilot:problem:" + code.name().toLowerCase(Locale.ROOT).replace('_', '-'))
// title = ErrorCode.title(), detail = messageSource.getMessage("error." + code.name(), args, Locale.KOREAN)
```

### 2.6 Hint (challenge·coach 공용)

```java
public record HintRequest(
        @NotNull HintLevel requestedLevel,       // SELF_EXPLAIN이면 VALUE_NOT_ALLOWED
        boolean acknowledgeEvidenceImpact,       // HL-4
        boolean giveUp,                          // HL-5
        Boolean skipSelfExplanation) {}          // coach finding 전용 (HL-2). challenge에서 true면 VALUE_NOT_ALLOWED

public record HintView(
        HintLevel level,                   // 실제로 반환한 내용의 단계 (HL-1이면 요청 단계보다 낮을 수 있음)
        String content,
        HintContentOrigin contentOrigin,
        HintLevel maxHintLevel,            // 처리 후 대상의 max_hint_level
        List<HintLevel> skippedLevels,     // 이번 요청으로 건너뛴 단계 (HL-3). 저장된 내용 반환(HL-1)이면 []
        AiMeta aiMeta) {}                  // contentOrigin = AI_GENERATED일 때만, 그 외 null

public record DisclosedHintView(
        HintLevel level,
        String content,
        HintContentOrigin contentOrigin,
        Instant disclosedAt) {}
```

### 2.7 사용하는 enum

요청·응답의 enum 타입은 모두 `04-domain-model-and-db.md` §3 레지스트리의 것이다. `CoachProjectType`(`coach.domain`)의 값은 `04-domain-model-and-db.md` §5.5 `projectType` 목록과 같다(§12.1).

---

## 3. User 모듈

Controller: `MeController`(`GET/PATCH /me`, `/me/calendar-token`), `AccountController`(`DELETE /me`), `CalendarFeedController`(`/calendar/{token}.ics`), `account` 모듈의 `AccountExportController`(`GET /me/export`) — `03-system-architecture.md` §3.2.

### 3.1 `GET /me` — 내 프로필

| 항목 | 값 |
|---|---|
| operationId | `userGetMe` |
| 인증 / IK | Bearer / — |
| 온보딩 전 | 허용. `DELETION_REQUESTED` 사용자도 허용 |
| 응답 | 200 `MeResponse` |
| 오류 | 공통만 |
| Sprint · 요구사항 | S1 (aiStatus/aiUsage 계산은 S3. 그 전에는 `aiStatus = DISABLED`(AI 기능이 없으므로 Today가 CHALLENGE를 제안하지 않게), 사용량 0, 예산은 설정값. `AiStatus`·`AiUsageSnapshot` record만 S1에 만든다 — `13` BL-AIP-01) · FR-01, FR-22, FR-24 |

```java
public record MeResponse(
        UUID id,
        String displayName,
        UserRole role,
        UserStatus status,
        String timezone,                     // IANA ID
        int dayStartHour,                    // 0~6
        int weekdayStudyMinutes,             // 0~720
        int weekendStudyMinutes,             // 0~720
        boolean onboardingCompleted,         // onboarding_completed_at != null
        Instant onboardingCompletedAt,
        LocalDate today,                     // 서버 기준 현재 plan-day
        boolean calendarSubscribed,          // calendar_token_hash != null
        Instant deletionRequestedAt,
        AiStatus aiStatus,
        AiUsageView aiUsage,
        Instant createdAt,
        long version) {}

public record AiUsageView(
        int todayCalls,
        int dailyCallLimit,
        String monthCostUsd,        // "12.34"
        String monthlyBudgetUsd) {} // "25.00"
```

```json
{
  "id": "5a1d7c1e-3f4b-4f39-9a0b-6e9f4c2b8d10",
  "displayName": "MT",
  "role": "USER",
  "status": "ACTIVE",
  "timezone": "Asia/Seoul",
  "dayStartHour": 4,
  "weekdayStudyMinutes": 45,
  "weekendStudyMinutes": 240,
  "onboardingCompleted": true,
  "onboardingCompletedAt": "2026-09-30T12:10:44Z",
  "today": "2026-11-20",
  "calendarSubscribed": false,
  "deletionRequestedAt": null,
  "aiStatus": "BUDGET_WARNING",
  "aiUsage": { "todayCalls": 7, "dailyCallLimit": 60, "monthCostUsd": "20.41", "monthlyBudgetUsd": "25.00" },
  "createdAt": "2026-09-30T12:02:01Z",
  "version": 3
}
```

- `aiStatus`, `aiUsage` 계산: §1.9.1.
- 이메일은 저장하지 않으므로 응답에 없다.

### 3.2 `PATCH /me` — 설정 변경

| 항목 | 값 |
|---|---|
| operationId | `userUpdateMe` |
| 인증 / IK | Bearer / — |
| 온보딩 전 | 허용 |
| 요청 | `UpdateMeRequest` |
| 응답 | 200 `MeResponse` |
| 오류 | 400 `VALIDATION_FAILED`(`TIMEZONE_INVALID`, `NOT_BLANK_IF_PRESENT`), 409 `CONCURRENT_MODIFICATION` |
| Sprint · 요구사항 | S1 · FR-24, AC-17 |

```java
public record UpdateMeRequest(
        @Size(min = 1, max = 100) String displayName,   // trim 후 저장. 공백만이면 NOT_BLANK_IF_PRESENT
        @Size(max = 50) String timezone,                // IANA region ID (TIMEZONE_INVALID)
        @Min(0) @Max(6) Integer dayStartHour,
        @Min(0) @Max(720) Integer weekdayStudyMinutes,
        @Min(0) @Max(720) Integer weekendStudyMinutes,
        @NotNull Long version) {}
```

- `null` 필드는 변경하지 않는다. 모든 필드가 `null`이어도 version이 맞으면 200(변경 없음, version 유지).
- `UpdateMeRequest`에 없는 속성을 보내면 알 수 없는 속성 → 400 `MALFORMED_REQUEST`.
- `timezone`, `dayStartHour` 변경은 다음 요청부터 plan-day 계산에 적용된다. 이미 저장된 `plan_date` 값(daily_plan, learning_session, learning_event, review_answer)은 바꾸지 않는다(AC-17).
- 학습 시간 변경은 다음 budget 계산(`GET /plans/active/budget`, `ProgressSnapshotJob`)부터 반영된다(`06-learning-engine-rules.md` §3.2).

### 3.3 `GET /me/export` — 내 데이터 내보내기

| 항목 | 값 |
|---|---|
| operationId | `userExportMe` |
| 인증 / IK | Bearer / — |
| 온보딩 전 | 허용 |
| 응답 | 200 `application/json`, `Content-Disposition: attachment; filename="devpilot-export-{yyyyMMdd}.json"` (날짜 = 오늘 plan-day) |
| 오류 | 공통만 |
| Sprint · 요구사항 | S6 · FR-23, AC-15 |

동작:
- 한 개의 read-only 트랜잭션에서 만든다(시점 일관성). 페이지 없이 전체를 반환한다.
- 감사 로그 `DATA_EXPORTED`를 남긴다.

최상위 구조 (`exportVersion = 1`):

| key | 타입 | 내용 (정렬) |
|---|---|---|
| `exportVersion` | int | `1` |
| `exportedAt` | Instant | 생성 시각 |
| `user` | object | `app_user` (`externalAuthId`, `calendarTokenHash` 제외, `calendarSubscribed` 추가) |
| `learningGoal` | object \| null | `learning_goal` + `focusSkillCodes[]` |
| `plans[]` | array | `learning_plan` (`planVersion` ASC). 항목마다 `milestones[]`(+`skillCodes[]`), `skillTargets[]`, `snapshots[]`(`snapshotDate` ASC) |
| `skillStates[]` | array | `user_skill_state` (`skillCode` ASC) |
| `skillStateChanges[]` | array | `skill_state_change` (`changedAt` ASC) |
| `dailyPlans[]` | array | `daily_plan` + `tasks[]`(`learning_task`, `sortOrder` ASC — `READ_CODE` 행은 `readingKey`·`readingFeedback` 포함, `READING` 행은 자료가 있으면 `readingKey` 포함(소스 점검 입력 `19` §8.5), `REDO` 행은 `redoSourceTaskId`·`redoWithoutAi` 포함, `EXPLAIN`·`READ_CODE` 행은 `explainedToPerson`·`explainedNote` 포함) (`planDate` ASC) |
| `learningSessions[]` | array | `learning_session` (`startedAt` ASC) |
| `learningEvents[]` | array | `learning_event` (`occurredAt` ASC). 무효화된 이벤트도 `invalidatedAt`과 함께 포함 |
| `hintDisclosures[]` | array | `hint_disclosure` (`disclosedAt` ASC) |
| `reviewItems[]` | array | `review_item` (`createdAt` ASC) |
| `reviewAnswers[]` | array | `review_answer` (`answeredAt` ASC) |
| `challenges[]` | array | 사용자 소유(`owner_user_id` = 본인) `challenge` + `skillCodes[]` (`createdAt` ASC). seed challenge 제외 |
| `challengeAttempts[]` | array | `challenge_attempt` + `submissions[]`(`submissionNo` ASC) (`startedAt` ASC) |
| `coachReviews[]` | array | `coach_review` + `findings[]`(`sortOrder` ASC) (`createdAt` ASC). purge된 review는 `content: null` |
| `thinkingPatternObservations[]` | array | `thinking_pattern_observation` (`observedAt` ASC) |
| `evidence[]` | array | `evidence_candidate` (`createdAt` ASC) |
| `weeklyReviews[]` | array | `weekly_review` (`weekStartDate` ASC) |
| `requirementDocs[]` | array | `requirement_doc` + `requirements[]`(`sortOrder` ASC) (`createdAt` ASC). purge된 문서는 `sourceText: null` |
| `sideProjects[]` | array | `side_project` + `notes[]`(`side_project_note`, `occurredOn` ASC·`id` ASC — 모든 텍스트는 마스킹본, §19.8) (`createdAt` ASC) |
| `rubberDuckSessions[]` | array | `rubber_duck_session` + `turns[]`(`rubber_duck_turn`, `turnNo` ASC — `userText`는 마스킹본) (`startedAt` ASC) |
| `dailyTips[]` | array | `user_daily_tip` (`shownOn` ASC, `tipKey` ASC). 팁 본문은 콘텐츠라 `tipKey`·`shownOn`·`feedback`만 있다(§20, ADR-041) |

행 변환 규칙:
- 컬럼 이름 snake_case → lowerCamelCase. 값 형식은 §1.1(Instant, date, 정수).
- 제외 컬럼: `user_id`, `owner_user_id`, `version`, `ai_call_id`, `request_fingerprint`, `content_sha256`.
- `skill_id`가 있는 행에는 `skillCode`를 함께 넣는다. `practical_importance`는 `practicalImportanceBp`(× 10000 정수)로 넣는다.
- `jsonb` 컬럼은 JSON 그대로 중첩한다(`04-domain-model-and-db.md` §5). 배열 컬럼은 JSON 배열.
- `ai_call_log`, `idempotency_record`는 포함하지 않는다.

```json
{
  "exportVersion": 1,
  "exportedAt": "2026-12-28T13:00:00Z",
  "user": { "id": "5a1d…", "displayName": "MT", "role": "USER", "status": "ACTIVE", "timezone": "Asia/Seoul",
            "dayStartHour": 4, "weekdayStudyMinutes": 45, "weekendStudyMinutes": 240,
            "onboardingCompletedAt": "2026-09-30T12:10:44Z", "calendarSubscribed": true,
            "deletionRequestedAt": null, "createdAt": "2026-09-30T12:02:01Z", "updatedAt": "2026-10-02T01:00:00Z" },
  "learningGoal": { "id": "…", "targetRole": "JAVA_BACKEND",
                    "targetCompletionDate": "2027-04-01", "focusSkillCodes": ["SPRING.TRANSACTION"],
                    "createdAt": "2026-09-30T12:10:44Z", "updatedAt": "2026-09-30T12:10:44Z" },
  "plans": [ { "id": "…", "planVersion": 1, "status": "SUPERSEDED", "milestones": [], "skillTargets": [], "snapshots": [] } ],
  "skillStates": [], "skillStateChanges": [], "dailyPlans": [], "learningSessions": [], "learningEvents": [],
  "hintDisclosures": [], "reviewItems": [], "reviewAnswers": [], "challenges": [], "challengeAttempts": [],
  "coachReviews": [ { "id": "…", "status": "COMPLETED", "content": null, "contentPurgedAt": "2026-12-20T18:20:00Z", "findings": [] } ],
  "thinkingPatternObservations": [], "evidence": [], "weeklyReviews": [], "requirementDocs": []
}
```

### 3.4 `DELETE /me` — 계정 삭제 요청

| 항목 | 값 |
|---|---|
| operationId | `userDeleteMe` |
| 인증 / IK | Bearer / — |
| 온보딩 전 | 허용 |
| 요청 | body 없음 |
| 응답 | 202 `AccountDeletionResponse` |
| 오류 | 403 `RECENT_LOGIN_REQUIRED` |
| Sprint · 요구사항 | S1 (BL-SEC-14. 실제 삭제 job은 BL-SEC-17 Later) · FR-23, AC-15 |

```java
public record AccountDeletionResponse(UserStatus status, Instant deletionRequestedAt) {}
```

동작:
1. 이미 `DELETION_REQUESTED`이면 상태를 바꾸지 않고 기존 값으로 202를 반환한다(재인증 검사 생략).
2. 최근 로그인 검사(`03-system-architecture.md` §4.2): `authTime` = JWT `amr` 배열 원소의 `timestamp`(epoch 초) 중 최댓값. `amr`이 없거나 비어 있으면 `iat`로 대체한다(SP-5에서 확정). `authTime`이 없거나 `now − authTime > devpilot.security.account-deletion-max-token-age`(5분)이거나 `authTime > now + 60초`이면 403 `RECENT_LOGIN_REQUIRED`. 시각은 주입된 `Clock` 기준이다. 클라이언트는 이 코드를 받으면 재로그인(`signInWithOAuth`) 후 다시 호출한다.
3. 한 트랜잭션: `status = DELETION_REQUESTED`, `deletion_requested_at = now`, `calendar_token_hash = null`(피드 즉시 중단). 감사 로그 `ACCOUNT_DELETION_REQUESTED`.
4. `AccountDeletionJob`(5분 주기)이 `app_user` 행을 삭제한다. 사용자 데이터는 cascade로 지워지고 `ai_call_log.user_id`는 null이 된다(`03-system-architecture.md` §6, `04-domain-model-and-db.md` §8).
5. 이후 요청은 `GET /me`, `DELETE /me`를 제외하고 403 `FORBIDDEN`이다(§1.4.2).
6. `devtoken` 모드에서는 allowlist에서 이메일을 빼면 다시 발급받을 수 없다. `supabase` 모드(Later)의 Auth 사용자는 운영자가 수동 삭제한다(DEC-11). 클라이언트는 202를 받으면 즉시 로그아웃한다. 행이 삭제된 뒤 allowlist에 남은 계정으로 요청하면 빈 사용자가 JIT 생성되므로(`03-system-architecture.md` §4.3) 운영자는 allowlist에서도 제거한다.

### 3.5 `POST /me/calendar-token` — 캘린더 구독 토큰 발급·재발급

| 항목 | 값 |
|---|---|
| operationId | `userIssueCalendarToken` |
| 인증 / IK | Bearer / IK |
| 요청 | body 없음 |
| 응답 | 201 `CalendarTokenResponse` / 같은 `Idempotency-Key` 재생 시 200 `CalendarTokenResponse`(`feedUrl = null`) |
| 오류 | 공통만 |
| Sprint · 요구사항 | S3 (2026-09-18 범위 축소: S2에서 제외) · FR-20 |

```java
public record CalendarTokenResponse(
        String feedUrl,     // 최초 응답에서만 값이 있다. idempotency 재생 응답에서는 null. 다시 조회하는 API는 없다
        Instant issuedAt) {}
```

```json
{ "feedUrl": "https://<tailnet-host>/api/v1/calendar/q3v2Qm7l0cQ1bJ8pW4n6sXy0aZ9kLr5tUe2hGd3fIoM.ics",
  "issuedAt": "2026-10-12T11:00:00Z" }
```

- 토큰: `SecureRandom` 32바이트 → base64url 패딩 없음(43자). DB에는 `SHA-256(token)` 소문자 hex만 `app_user.calendar_token_hash`에 저장한다.
- 재발급하면 이전 토큰은 즉시 무효다(해시 덮어쓰기).
- `feedUrl` = `devpilot.web.app-base-url`(`APP_BASE_URL`) + `/api/v1/calendar/{token}.ics`. 요청의 `Host`, `X-Forwarded-*` 헤더로 만들지 않는다(`07-security-and-privacy.md` §4.6).
- Idempotency: `idempotency_record`에는 status 200과 `feedUrl: null`로 바꾼 body를 저장한다(`03-system-architecture.md` §5.4). 같은 키로 다시 보내면 200, `Idempotent-Replayed: true`, `feedUrl: null`이 돌아온다. 이때 서버는 토큰을 새로 만들지 않으므로 클라이언트는 **새 키로** 다시 발급한다. 토큰 평문은 DB 어디에도, 로그에도 남지 않는다.
- 감사 이벤트 `CALENDAR_TOKEN_ROTATED`(`07-security-and-privacy.md` §6.3).

### 3.6 `GET /api/v1/calendar/{token}.ics` — 캘린더 피드

| 항목 | 값 |
|---|---|
| operationId | `userGetCalendarFeed` |
| 인증 / IK | **Bearer 없음** — 토큰 해시 조회(`03-system-architecture.md` §5.5) / — |
| path | `token`: `^[A-Za-z0-9_-]{43}$`. 형식이 다르면 404 |
| 응답 | 200 `text/calendar; charset=utf-8`, `Cache-Control: private, max-age=900` |
| 오류 | 404 `RESOURCE_NOT_FOUND`(토큰 불일치·형식 오류·삭제 요청 사용자), 429 `RATE_LIMITED`(토큰별·IP별 한도). 오류 body는 problem+json |
| Sprint · 요구사항 | S3 (2026-09-18 범위 축소: S2에서 제외) · FR-20 |

처리 순서:
1. Rate limit: 토큰 해시별 **UTC 정시 기준 1시간 고정 창, 60회**. 61번째 요청부터 429 `RATE_LIMITED`, `Retry-After` = 다음 정시까지 초. 카운터는 메모리에 둔다(단일 인스턴스 전제, `03-system-architecture.md` §6). `devpilot.security.rate-limit.calendar-feed-per-hour`.
2. 무효 토큰 한도: 404로 끝나는 요청(형식 오류 포함)은 클라이언트 IP(`X-Forwarded-For` 첫 값, Caddy만 신뢰 proxy)별로 같은 방식의 1시간 창에서 센다. 이미 30회(`devpilot.security.rate-limit.calendar-invalid-token-per-hour-per-ip`)에 도달한 IP의 요청은 조회 전에 429 `RATE_LIMITED`. IP 값은 로그에 남기지 않는다(`07-security-and-privacy.md` §12.2).
3. `SHA-256(token)`으로 `app_user` 조회. 없으면 404.
4. 해당 사용자의 `today = planDate(now, zone, dayStartHour)`로 본문을 만든다.
5. 로그와 `instance`에는 경로를 `/api/v1/calendar/****.ics`로 마스킹한다.

본문 규칙 (RFC 5545):

| 항목 | 규칙 |
|---|---|
| 줄바꿈 | CRLF. 75 octet을 넘는 줄은 folding(UTF-8 문자 경계에서 자르고 다음 줄은 공백 1개로 시작) |
| escape | TEXT 값의 `\` → `\\`, `;` → `\;`, `,` → `\,`, 줄바꿈 → `\n` |
| VCALENDAR 속성 | `VERSION:2.0`, `PRODID:-//DevPilot//Calendar Feed 1.0//KO`, `CALSCALE:GREGORIAN`, `METHOD:PUBLISH`, `X-WR-CALNAME:DevPilot`, `X-WR-TIMEZONE:{user.timezone}`, `REFRESH-INTERVAL;VALUE=DURATION:PT1H`, `X-PUBLISHED-TTL:PT1H` |
| VEVENT 수 | plan-day `today` ~ `today + 6` 날마다 1개 (7개), 날짜 ASC |
| `UID` | `{planDate}@devpilot` (예: `2026-10-12@devpilot`). 같은 날짜는 항상 같은 UID |
| `DTSTAMP` | 생성 시각 UTC (`20261012T110000Z`) |
| `DTSTART;VALUE=DATE` / `DTEND;VALUE=DATE` | `planDate` / `planDate + 1` (`yyyyMMdd`) — 종일 일정 |
| `TRANSP` | `TRANSPARENT` |
| `SUMMARY` (today, main task 있음) | `오늘의 핵심: {mainTask.title} ({mainTask.estimatedMinutes}분)`. main task: 오늘 `daily_plan`의 `is_main` task 중 `PLANNED`/`IN_PROGRESS`인 것, 없으면 `sort_order`가 가장 큰 것 |
| `SUMMARY` (today, daily_plan 또는 main task 없음) | `DevPilot 오늘 계획 만들기` |
| `SUMMARY` (today+1 ~ today+6) | `DevPilot 학습` |
| 넣지 않는 것 | `DESCRIPTION`, `LOCATION`, `VALARM`, 세션 기록, reflection, 복습 내용, 개인 메모 |

```text
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//DevPilot//Calendar Feed 1.0//KO
CALSCALE:GREGORIAN
METHOD:PUBLISH
X-WR-CALNAME:DevPilot
X-WR-TIMEZONE:Asia/Seoul
REFRESH-INTERVAL;VALUE=DURATION:PT1H
X-PUBLISHED-TTL:PT1H
BEGIN:VEVENT
UID:2026-10-12@devpilot
DTSTAMP:20261012T110000Z
DTSTART;VALUE=DATE:20261012
DTEND;VALUE=DATE:20261013
SUMMARY:오늘의 핵심: Spring Transaction 내 말로 설명하기 (15분)
TRANSP:TRANSPARENT
END:VEVENT
BEGIN:VEVENT
UID:2026-10-13@devpilot
DTSTAMP:20261012T110000Z
DTSTART;VALUE=DATE:20261013
DTEND;VALUE=DATE:20261014
SUMMARY:DevPilot 학습
TRANSP:TRANSPARENT
END:VEVENT
END:VCALENDAR
```

(나머지 VEVENT 5개는 생략)

---

## 4. Onboarding 모듈

Controller: `OnboardingController`. Service: `OnboardingService`, `DiagnosticSuggestionService`, 도메인 `SelfAssessmentPropagation` (`03-system-architecture.md` §3.2).

온보딩 3단계는 **짧은 진단**이다(자기평가 입력이 아니다). 진단을 건너뛰면 기존 자기평가 입력으로 대체한다. 마지막 단계에서 **사이드 프로젝트를 하나 만든다**(SP-1, §19). 둘 다 같은 `POST /onboarding` 요청에 담는다.

### 4.1 `POST /onboarding` — 온보딩 일괄 처리

| 항목 | 값 |
|---|---|
| operationId | `onboardingComplete` |
| 인증 / IK | Bearer / IK |
| 온보딩 전 | 허용 (이 endpoint 자체) |
| 요청 | `OnboardingRequest` |
| 응답 | 201 `OnboardingResponse` |
| 오류 | 400 `VALIDATION_FAILED`(`TIMEZONE_INVALID`, `DATE_OUT_OF_RANGE`, `SKILL_CODE_UNKNOWN`, `DUPLICATE_VALUE`, `ONE_OF_REQUIRED`, `MUTUALLY_EXCLUSIVE`, `URL`), 409 `ONBOARDING_ALREADY_COMPLETED`, 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S1 (8단계 seed 카드 배정과 9단계 snapshot은 S2부터 — S1 빌드는 `assignedSeedCardCount = 0`, `latestRiskLevel`·`latestRatioBp = null`. 11단계 진단 제안은 S3부터 — 그 전 빌드는 `[]`. 모두 M1 안이다) · FR-02, FR-03, FR-04, FR-24, AC-11, SP-1 |

```java
public record OnboardingRequest(
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(max = 50) String timezone,                  // IANA region ID
        @NotNull @Min(0) @Max(6) Integer dayStartHour,
        @NotNull @Min(0) @Max(720) Integer weekdayStudyMinutes,
        @NotNull @Min(0) @Max(720) Integer weekendStudyMinutes,
        @NotNull @Valid LearningGoalInput learningGoal,             // 1단계: 무엇을(학습 트랙)·언제까지(목표일)
        @NotNull Boolean runDiagnostic,                             // 3단계: true = 짧은 진단, false = 자기평가 입력으로 대체
        @NotNull @Size(max = 14) List<@NotNull @Valid SelfAssessmentInput> selfAssessments,  // SkillCategory 값 수. runDiagnostic = true면 []
        @Valid SideProjectInput sideProject,                        // null = 건너뛰기 (SP-1)
        @NotNull Boolean useTemplate) {}

public record LearningGoalInput(
        @NotNull TargetRole targetRole,              // 학습 트랙: JAVA_BACKEND | JAVA_BACKEND_STARTER (04 §3)
        @NotNull LocalDate targetCompletionDate,     // 목표일 (날짜 하나)
        @NotNull @Size(max = 10) @UniqueElements List<@NotBlank @Size(max = 100) String> focusSkillCodes) {}

public record SelfAssessmentInput(
        @NotNull SkillCategory category,
        @NotNull @Min(0) @Max(5) Integer level) {}   // SkillLevel ordinal

public record SideProjectInput(                      // onboarding.presentation. 필드는 §19.2 생성 요청과 같다
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @Size(max = 500) String repoUrl,             // 저장만 한다. 서버는 fetch하지 않는다 (07-security-and-privacy.md §5.5)
        @Size(max = 300) String stack) {}

public record OnboardingResponse(
        MeResponse user,
        LearningGoalView learningGoal,                      // §5.1
        PlanSummaryView activePlan,                         // §7.1
        SideProjectView sideProject,                        // §19.1. 건너뛰었으면 null
        int assignedSeedCardCount,
        List<DiagnosticSuggestionView> suggestedDiagnostics) {}  // §4.2. runDiagnostic = false면 []
```

```json
{
  "displayName": "MT",
  "timezone": "Asia/Seoul",
  "dayStartHour": 4,
  "weekdayStudyMinutes": 45,
  "weekendStudyMinutes": 240,
  "learningGoal": {
    "targetRole": "JAVA_BACKEND",
    "targetCompletionDate": "2027-04-01",
    "focusSkillCodes": ["SPRING.TRANSACTION", "DATABASE.JPA_MAPPING"]
  },
  "runDiagnostic": true,
  "selfAssessments": [],
  "sideProject": {
    "name": "주문 시스템",
    "description": "회원가입 · 상품 · 주문 · 취소까지 직접 만드는 학습용 백엔드",
    "repoUrl": "https://github.com/example/order-service",
    "stack": "Spring Boot, PostgreSQL"
  },
  "useTemplate": true
}
```

(`user`는 `MeResponse` 전체. 아래 예시는 일부 필드만 표시)

```json
{
  "user": { "id": "5a1d…", "displayName": "MT", "onboardingCompleted": true, "today": "2026-09-30" },
  "learningGoal": { "id": "c0a1…", "targetRole": "JAVA_BACKEND",
                    "targetCompletionDate": "2027-04-01",
                    "focusSkills": [ { "id": "…", "code": "SPRING.TRANSACTION", "name": "Spring Transaction", "category": "SPRING" } ],
                    "replanRecommended": false, "createdAt": "2026-09-30T12:10:44Z", "updatedAt": "2026-09-30T12:10:44Z", "version": 0 },
  "activePlan": { "id": "9e2b…", "planVersion": 1, "status": "ACTIVE", "title": "Java 백엔드 성장 계획", "changeReason": null,
                  "milestoneCount": 9, "latestRiskLevel": "MEDIUM", "latestRatioBp": 9400,
                  "createdAt": "2026-09-30T12:10:44Z", "supersededAt": null },
  "sideProject": { "id": "3f7c…", "name": "주문 시스템", "description": "회원가입 · 상품 · 주문 · 취소까지 직접 만드는 학습용 백엔드",
                   "repoUrl": "https://github.com/example/order-service", "stack": "Spring Boot, PostgreSQL", "status": "ACTIVE",
                   "createdAt": "2026-09-30T12:10:44Z", "updatedAt": "2026-09-30T12:10:44Z", "version": 0 },
  "assignedSeedCardCount": 184,
  "suggestedDiagnostics": [
    { "category": "JAVA", "selfAssessedLevel": 3,
      "skill": { "id": "…", "code": "JAVA.EXCEPTION", "name": "Exception", "category": "JAVA" },
      "challengeId": "7d3c…", "title": "설정 로더의 예외 처리 진단", "difficulty": 3, "estimatedMinutes": 10 }
  ]
}
```

도메인 검사 (오늘 = 요청의 `timezone`, `dayStartHour`로 계산한 plan-day):

| 필드 | 규칙 | 실패 code |
|---|---|---|
| `timezone` | IANA region ID | `TIMEZONE_INVALID` |
| `learningGoal.targetCompletionDate` | 목표일. 오늘 + 1일(내일) ~ 오늘 + 3년 | `DATE_OUT_OF_RANGE` |
| `learningGoal.targetRole` | `TargetRole` 값. 온보딩 1단계에서 사용자가 고른다. 이 값이 role target·계획 템플릿·planner 트랙 기본값(`devpilot.tracks`)을 모두 정한다 | `UNKNOWN_ENUM_VALUE` |
| `learningGoal.focusSkillCodes[i]` | 활성 skill code. 고른 학습 트랙에 role target이 있는 skill이어야 한다 | `SKILL_CODE_UNKNOWN` |
| `selfAssessments[i].category` | 목록 안에서 유일 | `DUPLICATE_VALUE` |
| `selfAssessments` (`runDiagnostic = false`) | 1개 이상 — 진단을 건너뛰면 자기평가가 시작점이다 | `ONE_OF_REQUIRED` |
| `selfAssessments` (`runDiagnostic = true`) | 비어 있어야 한다 — 진단 결과가 시작점이다 | `MUTUALLY_EXCLUSIVE` |
| `sideProject.repoUrl` | `java.net.URI` 파싱 성공, scheme `http`/`https`, host 존재 (`07-security-and-privacy.md` §5.5) | `URL` |

처리 (한 트랜잭션, 이 순서):
1. `app_user`를 version 조건으로 조회한다. `onboarding_completed_at`이 있으면 409 `ONBOARDING_ALREADY_COMPLETED`. 동시 요청의 `learning_goal.user_id` unique 위반도 같은 코드로 변환한다.
2. 도메인 검사(위 표).
3. `app_user` 갱신: 요청의 프로필 필드, `onboarding_completed_at = now`.
4. `learning_goal` + `learning_goal_focus_skill` INSERT.
5. `user_skill_state` (`SelfAssessmentPropagation`, `19-content-spec.md` §9): 학습 목표의 학습 트랙(`targetRole`)에 role target이 있는 **활성 non-root skill**마다 행을 만든다. 레벨 4축은 0, `self_assessment_active = true`. root skill에는 행을 만들지 않는다. planning level 규칙은 `06-learning-engine-rules.md` §7.5.
   - `runDiagnostic = false`(자기평가 모드): `self_assessed_level` = 요청에 그 skill의 category가 있으면 그 `level`, 없으면 `null`.
   - `runDiagnostic = true`(진단 모드): `self_assessed_level`은 **전부 `null`**이다. 시작점은 진단 결과가 정한다(`06-learning-engine-rules.md` §7.4 `DIAG_PASSED`/`DIAG_FAILED`). 진단을 하나도 풀지 않으면 4축 0에서 시작한다.
6. `learning_plan` INSERT (`06-learning-engine-rules.md` §11.3): `plan_version = 1`, `status = ACTIVE`, `learning_goal_id`, `title` = 템플릿 `planTitle`(`06-learning-engine-rules.md` §11.3, 예: `Java 백엔드 성장 계획`), `supersedes_plan_id = null`, `change_reason = null`. `role_skill_target(target_role)` 전체를 `plan_skill_target`으로 복사한다(`adjustment = ROLE_DEFAULT`).
7. `useTemplate = true`면 JAVA_BACKEND plan 템플릿의 milestone을 `06-learning-engine-rules.md` §11.3이 가리키는 계획 템플릿 배치 알고리즘(`19-content-spec.md` §5)으로 날짜를 정해 `plan_milestone`(`status = PLANNED`, `sort_order` = 템플릿 순서)·`milestone_skill`로 만든다. `false`면 milestone 없이 plan만 만든다(사용자가 Plan 화면에서 replan으로 추가).
8. (S2부터) seed 복습 카드를 사용자 `review_item`으로 복사한다(`SeedCardAssignmentService.assignForNewUser`, `source_type = SEED_CARD`, `origin = SEED`). 첫 due 분산은 `06-learning-engine-rules.md` §6.3 첫 행. target이 없는 skill의 카드는 정렬에서 LATER 뒤에 둔다(`06-learning-engine-rules.md` §6.5 "없음"과 같은 위치). `assignedSeedCardCount` = 복사한 행 수. S1 빌드에는 이 단계가 없고 `assignedSeedCardCount = 0`이다(S2 배포 후 backfill, `04-domain-model-and-db.md` §9).
9. (S2부터) 오늘 날짜로 `plan_progress_snapshot`을 upsert한다(`06-learning-engine-rules.md` §3, §4.1~§4.3). `activePlan.latestRiskLevel`, `latestRatioBp`는 이 값이다. S1 빌드에는 이 단계가 없고 두 값은 `null`이다.
10. `sideProject`가 있으면 `side_project` INSERT(`status = ACTIVE`, `user_id` = 요청 사용자). `name`·`description`·`stack`은 마스킹 후 저장한다(§1.11). `null`이면 만들지 않는다 — 이때 planner는 `PROJECT_TASK`를 제안하지 않는다(SP-1, `06-learning-engine-rules.md` §5.3). 기본 이름("주문 시스템")은 **클라이언트가 채워 보낸다**(`02-user-scenarios-and-ux.md` SCR-ONBOARDING). 서버는 기본값을 만들지 않는다.
11. 커밋 후 `suggestedDiagnostics`를 §4.2 규칙으로 계산한다. `runDiagnostic = false`면 `[]`다.

- 이 요청은 learning event를 만들지 않는다.
- 진단은 이 요청 안에서 채점되지 않는다. 클라이언트는 `suggestedDiagnostics`의 `challengeId`로 `POST /challenges/{challengeId}/attempts`(§10.5)를 이어서 호출한다. 진단을 하나도 풀지 않고 넘어가도 온보딩은 완료 상태다.
- 나이, 성별, 소속 같은 입력은 받지 않는다(알 수 없는 속성 → 400 `MALFORMED_REQUEST`). 현재 실력은 진단(§4.2)이나 자기평가로 시작점을 잡는다.

### 4.2 `GET /diagnostics/suggestions` — 진단 challenge 제안

| 항목 | 값 |
|---|---|
| operationId | `onboardingGetDiagnosticSuggestions` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `DiagnosticSuggestionsResponse` |
| 오류 | 공통만 |
| Sprint · 요구사항 | S3 · FR-15, AC-11 |

```java
public record DiagnosticSuggestionsResponse(List<DiagnosticSuggestionView> items) {}

public record DiagnosticSuggestionView(
        SkillCategory category,
        Integer selfAssessedLevel, // 자기평가 모드면 그 category의 값(3~5), 진단 모드면 null
        SkillRef skill,            // 진단 대상 skill
        UUID challengeId,
        String title,
        int difficulty,
        Integer estimatedMinutes,
        @Nullable UUID activeAttemptId) {} // STARTED attempt가 있으면 그 id (이어서 풀기). 없으면 null
```

선택 규칙 (`DiagnosticSuggestionService`, 결정적):
1. 대상 category — `SkillCategory` 선언 순서로 처리하고, 결과는 **최대 5개**로 자른다(category당 1문제, 설계 §8).
   - **진단 모드**(`user_skill_state` 중 `self_assessed_level`이 `null`이 아닌 행이 하나도 없음 = 온보딩에서 `runDiagnostic = true`): `self_assessment_active = true`인 행이 있는 category 전부.
   - **자기평가 모드**: `self_assessment_active = true`인 행의 `self_assessed_level` 최댓값이 3 이상인 category.
2. 그 category에서 **진단을 실제로 받은 경우** category 전체를 제외한다 — 그 category의 `purpose = DIAGNOSTIC` challenge에 `status ∈ {SUBMITTED, EVALUATED}`인 attempt가 있을 때다. 진단은 category당 1회 제안한다.
   - `STARTED`(시작만 함)는 **제외하지 않고 이어서 풀도록 그대로 제안**한다. 응답의 `activeAttemptId`가 그 attempt다.
   - `ABANDONED`(제출 없이 그만둠)도 **제외하지 않는다.** 진단을 받지 않았기 때문이다.
   - 왜 이렇게 바꿨나: 이전 규칙은 상태와 무관하게 제외해서, 진단을 열었다가 나오기만 해도 그 category를 **영영 진단받지 못했다.** 그러면 그 category의 모든 skill이 0에서 시작하고 계획에서 뒤로 밀린다(2026-09-21 실사용에서 확인).
3. 후보 challenge: `status = VALIDATED`, `purpose = DIAGNOSTIC`, `owner_user_id IS NULL`, `challenge_skill` 중 하나 이상이 그 category에 속하고 해당 skill의 `self_assessment_active = true`.
4. 정렬: 대상 skill의 활성 plan `plan_skill_target.priority`(MUST → SHOULD → LATER → target 없음) → `practical_importance` DESC → `challenge.seed_key` ASC. 첫 번째 1개를 고른다. challenge가 여러 skill에 걸치면 이 정렬에서 가장 앞선 skill을 `skill`로 쓴다.
5. 후보가 없는 category는 결과에서 뺀다.

- 제안은 저장하지 않는다. 사용자는 `POST /challenges/{challengeId}/attempts`(§10.5)로 시작하고, 평가 결과는 `DIAGNOSTIC_PASSED`/`DIAGNOSTIC_FAILED` 규칙(`06-learning-engine-rules.md` §7.4)을 따른다. 이벤트 payload `claimedLevel`은 평가 시점 해당 skill의 `self_assessed_level`이다.

---

## 5. Goal 모듈

Controller: `LearningGoalController`.

### 5.1 `GET /learning-goal` — 학습 목표 조회

| 항목 | 값 |
|---|---|
| operationId | `goalGetGoal` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `LearningGoalView` |
| 오류 | 404 `LEARNING_GOAL_NOT_FOUND` |
| Sprint · 요구사항 | S1 · FR-03, AC-01 |

```java
public record LearningGoalView(
        UUID id,
        TargetRole targetRole,                // 학습 트랙
        LocalDate targetCompletionDate,       // 목표일. budget horizon (06 §3.1)이자 템플릿 배치 창의 끝 (19 §5)
        List<SkillRef> focusSkills,           // code ASC
        boolean replanRecommended,            // 활성 plan의 replan_recommended (활성 plan 없으면 false)
        Instant createdAt,
        Instant updatedAt,
        long version) {}
```

### 5.2 `PUT /learning-goal` — 학습 목표 수정

| 항목 | 값 |
|---|---|
| operationId | `goalUpdateGoal` |
| 인증 / IK | Bearer / — |
| 요청 | `LearningGoalUpdateRequest` |
| 응답 | 200 `LearningGoalView` |
| 오류 | 400 `VALIDATION_FAILED`(`DATE_OUT_OF_RANGE`, `SKILL_CODE_UNKNOWN`, `VALUE_NOT_ALLOWED`), 400 `UNKNOWN_ENUM_VALUE`, 404 `LEARNING_GOAL_NOT_FOUND`, 409 `CONCURRENT_MODIFICATION` |
| Sprint · 요구사항 | S1 · FR-03, AC-01 |

```java
public record LearningGoalUpdateRequest(
        @NotNull TargetRole targetRole,
        @NotNull LocalDate targetCompletionDate,
        @NotNull @Size(max = 10) @UniqueElements List<@NotBlank @Size(max = 100) String> focusSkillCodes,
        @NotNull Long version) {}
```

- 전체 교체(PUT)다. 목표가 없으면 만들지 않고 404다(생성은 온보딩).
- **`targetRole`(학습 트랙)은 바꿀 수 없다.** 저장된 값과 다르면 400 `VALIDATION_FAILED`(field `targetRole`, code `VALUE_NOT_ALLOWED`). 트랙을 바꾸면 role target·계획 템플릿·skill state가 모두 다른 집합이 되어 계획과 증거를 잇지 못한다 — 트랙 변경은 MVP 범위 밖이다(`19` §10.4). 요청에는 현재 값을 그대로 담는다.
- 날짜·skill 검사는 §4.1 도메인 검사와 같다(목표일은 내일 ~ 오늘+3년).
- `targetCompletionDate`가 바뀌면 같은 트랜잭션에서 활성 plan의 `replan_recommended = true` (`06-learning-engine-rules.md` §11.1). plan 구조는 바꾸지 않는다. 다음 budget 계산부터 새 horizon을 쓴다(`06-learning-engine-rules.md` §3.1).
- `focusSkillCodes`는 집합 전체를 교체한다. 다음 Today 생성의 `projectNeed` factor에 반영된다(`06-learning-engine-rules.md` §5.4).

---

## 6. Skill 모듈

Controller: `SkillController`.

### 6.1 `GET /skills/tree` — skill catalog와 역할 목표

| 항목 | 값 |
|---|---|
| operationId | `skillGetTree` |
| 인증 / IK | Bearer / — |
| 온보딩 전 | 허용 |
| query | `role`: `TargetRole`, 선택. 기본값은 **사용자의 학습 목표 트랙**이고, 목표가 아직 없으면(온보딩 전) `JAVA_BACKEND` |
| 응답 | 200 `SkillTreeResponse` |
| 오류 | 400 `UNKNOWN_ENUM_VALUE` |
| Sprint · 요구사항 | S1 · FR-06 |

```java
public record SkillTreeResponse(
        TargetRole role,
        int catalogVersion,             // 활성 skill의 catalog_version 최댓값
        List<SkillNodeView> skills) {}  // 평면 목록. 트리는 parentCode로 클라이언트가 구성

public record SkillNodeView(
        UUID id,
        String code,
        String name,
        SkillCategory category,
        String parentCode,              // 최상위면 null
        String description,
        int minutesPerLevelStep,
        int sortOrder,
        List<String> prerequisiteCodes, // code ASC
        RoleTargetView roleTarget) {}   // 해당 role 목표가 없으면 null

public record RoleTargetView(
        Priority priority,
        int practicalImportanceBp,         // role_skill_target.practical_importance × 10000
        AxisLevels targets) {}
```

- `active = true`인 skill만 반환한다. 정렬: `SkillCategory` 선언 순서 → `sortOrder` ASC → `code` ASC.
- skill 카탈로그는 트랙과 무관하게 하나다. **트랙이 바꾸는 것은 `roleTarget`뿐이다** — 같은 skill이 트랙에 따라 다른 priority·중요도·목표 레벨을 갖고, 그 트랙에 role target이 없으면 `roleTarget = null`이다(`19` §3.3).

### 6.2 `GET /skills/me` — 내 skill state

| 항목 | 값 |
|---|---|
| operationId | `skillGetMyStates` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `UserSkillStatesResponse` |
| 오류 | 공통만 |
| Sprint · 요구사항 | S1 · FR-06, AC-09 |

```java
public record UserSkillStatesResponse(List<UserSkillStateView> items) {}

public record UserSkillStateView(
        SkillRef skill,
        AxisLevels evidenceLevels,        // user_skill_state *_level
        AxisLevels planningLevels,        // 06 §7.5
        SkillTargetView target,           // 활성 plan의 plan_skill_target. 없으면 null
        Integer selfAssessedLevel,        // null 가능
        boolean selfAssessmentActive,
        int evidenceCount,
        Instant lastPracticedAt,          // null 가능
        Instant updatedAt) {}

public record SkillTargetView(
        Priority priority,
        int practicalImportanceBp,
        AxisLevels targets,
        boolean deferred,
        TargetAdjustment adjustment) {}
```

- 활성 skill 전체를 반환한다. `user_skill_state` 행이 없는 skill(온보딩 뒤 catalog에 추가된 skill)은 레벨 0, `selfAssessedLevel = null`, `selfAssessmentActive = true`, `evidenceCount = 0`, `updatedAt = null`로 채운다. 조회 시 행을 만들지 않는다.
- 정렬은 §6.1과 같다. 레벨을 수동으로 바꾸는 API는 없다(I-12).

### 6.3 `GET /skills/{skillId}/history` — skill 레벨 변경 이력

| 항목 | 값 |
|---|---|
| operationId | `skillGetHistory` |
| 인증 / IK | Bearer / — |
| path | `skillId`: UUID (catalog skill id, 비활성 포함) |
| query | `limit`, `cursor` (§1.5) |
| 정렬 | `changedAt` DESC, `id` DESC |
| 응답 | 200 `CursorPage<SkillStateChangeView>` |
| 오류 | 400 `INVALID_CURSOR`, 404 `RESOURCE_NOT_FOUND`(없는 skill) |
| Sprint · 요구사항 | S3 · FR-06, AC-09 |

```java
public record SkillStateChangeView(
        UUID id,
        SkillAxis axis,
        int fromLevel,
        int toLevel,
        String ruleCode,                          // 06 §7.2~§7.4의 rule_code
        List<EvidenceEventView> evidenceEvents,   // evidence_event_ids 순서 유지(최신순, 최대 10)
        Instant changedAt) {}

public record EvidenceEventView(
        UUID id,
        LearningEventType eventType,
        LocalDate planDate,
        Instant occurredAt,
        boolean invalidated) {}                   // invalidated_at != null
```

- 본인 이력만 반환한다. 변경이 없으면 `items: []`.

### 6.4 `GET /skills/{skillId}` — skill 상세 (학습 단계 포함)

| 항목 | 값 |
|---|---|
| operationId | `skillGet` |
| 인증 / IK | Bearer / — |
| path | `skillId`: UUID (catalog skill id, 비활성 포함 — §6.3과 같은 기준) |
| 응답 | 200 `SkillDetailView` |
| 오류 | 404 `RESOURCE_NOT_FOUND`(없는 skill) |
| Sprint · 요구사항 | S3 · FR-06, BL-STG-01~02, AC-09 |

```java
public record SkillDetailView(
        SkillRef skill,
        String parentCode,                          // 최상위면 null
        String description,                         // null 가능
        String whyItMatters,                        // 이 기술을 왜 하는지 한 줄. 콘텐츠 값, 없으면 null
        int minutesPerLevelStep,
        List<String> prerequisiteCodes,             // code ASC
        AxisLevels evidenceLevels,                  // user_skill_state *_level. 행이 없으면 전부 0
        AxisLevels planningLevels,                  // 06 §7.5
        SkillTargetView target,                     // 활성 plan의 plan_skill_target. 없으면 null (§6.2)
        List<LearningStageView> learningStages) {}  // LearningStage 선언 순서 6칸, 항상 6개

public record LearningStageView(
        LearningStage stage,
        boolean completed,
        Instant completedAt) {}                     // 그 단계를 채운 가장 이른 기록의 시각. completed = false면 null
```

- `learningStages`는 **저장하지 않는다.** 요청 시점에 이미 있는 기록(과제 완료, 러버덕 세션, 복습 답변, 재현 과제)에서 결정적으로 계산한다(ADR-042, `06` §5.11). 단계 이름과 순서는 `04-domain-model-and-db.md` §3 `LearningStage`다.
- `whyItMatters`는 DB 컬럼이 아니라 기술 트리 콘텐츠(`content/skill-tree*.yaml`)의 값이고, 기동 시 적재한 registry에서 읽는다(`19-content-spec.md`). 값이 없는 skill은 `null`이다.
- 비활성 skill(`active = false`)도 조회된다. 지난 과제·복습 카드가 가리키는 skill을 계속 보여 주기 위해서다(§19.7 은퇴한 reading과 같은 기준).
- `/skills/tree`(§6.1)와 `/skills/me`(§6.2)는 리터럴 경로이며 `/skills/{skillId}`보다 우선한다(§7의 `/plans/active`와 같은 방식).

---

## 7. Plan 모듈

Controller: `PlanController`. Service: `PlanQueryService`, `PlanCommandService`, `ReplanService`, `StudyBudgetService`.

공통 경로 규칙: `/plans/active`, `/plans/active/budget`은 리터럴 경로이며 `/plans/{planId}`보다 우선한다. `planId`가 본인 plan이 아니면 404 `PLAN_NOT_FOUND`, `milestoneId`가 그 plan의 milestone이 아니면 404 `RESOURCE_NOT_FOUND`다.

### 7.1 공통 view record

```java
public record PlanView(
        UUID id,
        int planVersion,
        PlanStatus status,
        String title,
        UUID supersedesPlanId,                    // null 가능
        String changeReason,                      // null 가능
        boolean replanRecommended,
        List<MilestoneView> milestones,           // sortOrder ASC → startDate ASC → id ASC
        List<PlanSkillTargetView> skillTargets,   // priority(MUST,SHOULD,LATER) → practicalImportanceBp DESC → skill.code ASC
        SnapshotView latestSnapshot,              // 이 plan의 snapshot_date 최댓값 행. 없으면 null
        Instant createdAt,
        Instant supersededAt,
        long version) {}

public record MilestoneView(
        UUID id,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        Priority priority,
        MilestoneStatus status,
        int sortOrder,
        List<String> skillCodes,                  // code ASC
        Instant updatedAt,
        long version) {}

public record PlanSkillTargetView(
        SkillRef skill,
        Priority priority,
        int practicalImportanceBp,
        AxisLevels targets,
        boolean deferred,
        TargetAdjustment adjustment) {}

public record SnapshotView(
        LocalDate snapshotDate,
        LocalDate horizonDate,
        int nominalBudgetMinutes,
        int completionRateBp,
        int effectiveBudgetMinutes,
        int requiredMustMinutes,
        int requiredShouldMinutes,
        Integer ratioBp,                          // effective = 0이면 null
        RiskLevel riskLevel,
        Instant generatedAt) {}

public record PlanSummaryView(
        UUID id,
        int planVersion,
        PlanStatus status,
        String title,
        String changeReason,
        int milestoneCount,
        RiskLevel latestRiskLevel,                // 최신 snapshot. 없으면 null
        Integer latestRatioBp,
        Instant createdAt,
        Instant supersededAt) {}

public record BudgetView(
        UUID planId,
        LocalDate today,
        LocalDate horizonDate,
        int nominalBudgetMinutes,
        int completionRateBp,
        int effectiveBudgetMinutes,
        int requiredMustMinutes,
        int requiredShouldMinutes,
        Integer ratioBp,
        RiskLevel riskLevel) {}
```

### 7.2 `GET /plans/active` — 활성 plan

| 항목 | 값 |
|---|---|
| operationId | `planGetActive` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `PlanView` |
| 오류 | 404 `PLAN_NOT_FOUND`(활성 plan 없음) |
| Sprint · 요구사항 | S1 · FR-04, AC-01 |

```json
{
  "id": "9e2b1c7a-0f0e-4d7b-8e59-0c3e1f6b2a01",
  "planVersion": 2,
  "status": "ACTIVE",
  "title": "Java 백엔드 성장 계획",
  "supersedesPlanId": "4b7d…",
  "changeReason": "야근으로 주문 생성 milestone 2주 지연",
  "replanRecommended": false,
  "milestones": [
    { "id": "a1…", "title": "기반 다지기", "description": null, "startDate": "2026-09-30", "endDate": "2026-10-25",
      "priority": "MUST", "status": "IN_PROGRESS", "sortOrder": 0,
      "skillCodes": ["SPRING.MVC_REST", "TESTING.JUNIT"], "updatedAt": "2026-10-20T11:00:00Z", "version": 1 },
    { "id": "a2…", "title": "주문 생성", "description": "트랜잭션 경계와 재고 차감 중심", "startDate": "2026-10-26",
      "endDate": "2026-12-06", "priority": "MUST", "status": "PLANNED", "sortOrder": 1,
      "skillCodes": ["SPRING.TRANSACTION", "DATABASE.TRANSACTION.LOCKING"], "updatedAt": "2026-10-20T11:00:00Z", "version": 0 }
  ],
  "skillTargets": [
    { "skill": { "id": "…", "code": "SPRING.TRANSACTION", "name": "Spring Transaction", "category": "SPRING" },
      "priority": "MUST", "practicalImportanceBp": 9000,
      "targets": { "knowledge": 4, "implementation": 4, "explanation": 4, "debugging": 3 },
      "deferred": false, "adjustment": "ROLE_DEFAULT" },
    { "skill": { "id": "…", "code": "SYSTEM_DESIGN.CACHE", "name": "Cache", "category": "SYSTEM_DESIGN" },
      "priority": "SHOULD", "practicalImportanceBp": 4000,
      "targets": { "knowledge": 3, "implementation": 2, "explanation": 3, "debugging": 2 },
      "deferred": true, "adjustment": "DEFERRED" }
  ],
  "latestSnapshot": { "snapshotDate": "2026-10-20", "horizonDate": "2027-04-01", "nominalBudgetMinutes": 16305,
                      "completionRateBp": 7000, "effectiveBudgetMinutes": 11413, "requiredMustMinutes": 10730,
                      "requiredShouldMinutes": 1810, "ratioBp": 9401, "riskLevel": "MEDIUM",
                      "generatedAt": "2026-10-19T19:05:02Z" },
  "createdAt": "2026-10-20T11:00:00Z",
  "supersededAt": null,
  "version": 0
}
```

### 7.3 `GET /plans` — plan 버전 이력

| 항목 | 값 |
|---|---|
| operationId | `planList` |
| 인증 / IK | Bearer / — |
| query | `limit`, `cursor` |
| 정렬 | `planVersion` DESC, `id` DESC |
| 응답 | 200 `CursorPage<PlanSummaryView>` |
| 오류 | 400 `INVALID_CURSOR` |
| Sprint · 요구사항 | S1 · FR-04, AC-01 |

### 7.4 `GET /plans/{planId}` — 특정 버전 조회

| 항목 | 값 |
|---|---|
| operationId | `planGet` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `PlanView` (SUPERSEDED, ARCHIVED 포함) |
| 오류 | 404 `PLAN_NOT_FOUND` |
| Sprint · 요구사항 | S1 · FR-04, AC-01 |

### 7.5 `POST /plans` — 템플릿으로 plan 생성

| 항목 | 값 |
|---|---|
| operationId | `planCreate` |
| 인증 / IK | Bearer / IK |
| 요청 | body 없음 |
| 응답 | 201 `PlanView` |
| 오류 | 404 `LEARNING_GOAL_NOT_FOUND`, 409 `ACTIVE_PLAN_EXISTS` |
| Sprint · 요구사항 | **Later** (2026-09-18 범위 축소) · FR-04 |

- 활성 plan이 있으면 409 `ACTIVE_PLAN_EXISTS`. partial unique index(`uq_learning_plan_one_active`) 위반도 같은 코드로 변환한다(T-6).
- 생성 내용은 §4.1 처리 6·7·9와 같다(`useTemplate = true`, 규칙은 `06-learning-engine-rules.md` §11.3). `plan_version` = 사용자의 기존 최대 `plan_version` + 1 (없으면 1), `supersedes_plan_id = null`. seed 카드 배정은 하지 않는다.
- 온보딩이 항상 plan을 만들고 `ACTIVE → ARCHIVED` 전이가 Later이므로(`04-domain-model-and-db.md` §4.4) MVP에서는 호출될 수 없다. ARCHIVED 전이와 함께 구현한다. OpenAPI 스냅샷에도 그때 추가한다.

### 7.6 `PATCH /plans/{planId}/milestones/{milestoneId}` — milestone in-place 수정

| 항목 | 값 |
|---|---|
| operationId | `planUpdateMilestone` |
| 인증 / IK | Bearer / — |
| 요청 | `MilestonePatchRequest` |
| 응답 | 200 `MilestoneView` |
| 오류 | 404 `PLAN_NOT_FOUND`, 404 `RESOURCE_NOT_FOUND`, 409 `PLAN_NOT_ACTIVE`, 409 `CONCURRENT_MODIFICATION`, 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S1 · FR-04 |

```java
public record MilestonePatchRequest(
        MilestoneStatus status,
        @Size(max = 2000) String description,   // "" 이면 null로 지움
        @Min(0) @Max(10000) Integer sortOrder,
        @NotNull Long version) {}               // milestone의 version
```

- 허용 필드는 이 셋뿐이다(`06-learning-engine-rules.md` §11.1). `title`, 날짜, `priority`, `skillCodes`를 보내면 알 수 없는 속성으로 400 `MALFORMED_REQUEST`다. 이런 변경은 replan(§7.8)으로 한다.
- 검사 순서: plan 조회(404) → plan `ACTIVE`(409 `PLAN_NOT_ACTIVE`) → milestone 조회(404) → version(409).
- `MilestoneStatus`는 다섯 값 사이 모든 전이를 허용하고, 같은 상태로의 변경은 no-op이다(`04-domain-model-and-db.md` §4.6). 부수효과는 없다.
- plan의 `version`, `planVersion`은 바뀌지 않는다.

### 7.7 `POST /plans/{planId}/replan/preview` — replan 미리보기

| 항목 | 값 |
|---|---|
| operationId | `planPreviewReplan` |
| 인증 / IK | Bearer / **선택** — 보내도 무시하고 record를 만들지 않는다(§1.7) |
| 요청 | `ReplanRequest` (`@Validated(Default.class)` — `reason` 필수 아님) |
| 응답 | 200 `ReplanPreviewResponse` |
| 오류 | 400 `VALIDATION_FAILED`(§7.8 도메인 검사와 같음), 404 `PLAN_NOT_FOUND`, 409 `PLAN_NOT_ACTIVE`, 409 `CONCURRENT_MODIFICATION`, 404 `LEARNING_GOAL_NOT_FOUND`, 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S2 · FR-05, AC-03, AC-30 |

```java
public record ReplanPreviewResponse(
        UUID planId,
        LocalDate today,
        LocalDate horizonDate,
        int nominalBudgetMinutes,
        int completionRateBp,
        int effectiveBudgetMinutes,
        int requiredMustMinutes,
        int requiredShouldMinutes,
        Integer ratioBp,
        RiskLevel riskLevel,
        List<DeferSuggestionView> deferSuggestions,                       // 제안 순서 그대로
        List<TargetReductionSuggestionView> mustTargetReductionSuggestions,
        List<ExpansionSuggestionView> expansionSuggestions,               // 여유가 있을 때만 (06 §4.4 6단계). 위 두 목록과 동시에 비어 있지 않은 경우는 없다
        RiskEstimateView riskAfterSuggestions) {}

public record DeferSuggestionView(
        SkillRef skill,
        Priority priority,              // 항상 SHOULD
        int practicalImportanceBp,
        int requiredMinutes) {}         // 이 skill을 defer하면 줄어드는 requiredShould

public record TargetReductionSuggestionView(
        SkillRef skill,
        SkillAxis axis,
        int currentTarget,
        int newTarget,                  // currentTarget − 1, 3 이상
        int planningLevel,
        int savedMinutes) {}            // 앞선 제안을 적용한 상태 기준으로 줄어드는 requiredMust

public record ExpansionSuggestionView(
        ExpansionKind kind,             // RESTORE_DEFERRED | RAISE_TARGET (06 §4.4 6단계)
        SkillRef skill,
        Priority priority,              // RESTORE_DEFERRED면 SHOULD 또는 LATER, RAISE_TARGET이면 MUST
        int practicalImportanceBp,
        SkillAxis axis,                 // RAISE_TARGET일 때만, 그 외 null
        Integer currentTarget,          // RAISE_TARGET일 때만
        Integer newTarget,              // RAISE_TARGET일 때만. currentTarget + 1, 5 이하
        int addedMinutes) {}            // 앞선 제안을 적용한 상태 기준으로 늘어나는 required (복원이면 requiredMinutes, 상향이면 그 축 +1 비용)

public record RiskEstimateView(
        int requiredMustMinutes,
        int requiredShouldMinutes,
        Integer ratioBp,
        RiskLevel riskLevel) {}
```

계산 (저장하지 않음):
1. 검사 순서와 도메인 검사는 §7.8과 같다(단 `reason` 필수 검사 제외).
2. 현재 plan의 `plan_skill_target`을 메모리로 복사하고 요청의 `acceptedDeferrals`, `acceptedTargetReductions`, `restoredDeferrals`, `acceptedTargetRaises`를 적용한다(§7.8 처리의 `plan_skill_target` 적용 규칙과 같다).
3. budget: `06-learning-engine-rules.md` §3 (학습 목표의 horizon, 사용자 학습 시간, completion rate).
4. required minutes·risk: `06-learning-engine-rules.md` §4.1~§4.3 (planning level은 `06-learning-engine-rules.md` §7.5).
5. 제안: `06-learning-engine-rules.md` §4.4. 방향은 한쪽뿐이다 — risk ≥ HIGH면 축소·defer, risk LOW이고 `ratioBp ≤ 7000`이면 확장(`expansionSuggestions`), 그 사이(또는 `ratioBp = null`)면 세 목록 모두 `[]`. 이미 `deferred = true`인 항목은 defer 후보에서 빼고, 확장의 복원 후보는 `deferred = true`인 SHOULD/LATER 항목이다. 제안이 없으면 `riskAfterSuggestions`는 현재 값과 같다.
6. `riskAfterSuggestions` = 모든 제안(축소·defer 또는 목표 상향)을 적용한 뒤 `06-learning-engine-rules.md` §4.2~§4.3으로 다시 계산한 값. 복원(SHOULD/LATER)은 risk 계산에 들어가지 않는다(§4.4 6단계).

- milestone 편집은 budget·required 계산에 영향을 주지 않는다(`06-learning-engine-rules.md` §3~§4는 plan_skill_target만 사용). preview가 milestone을 검증하는 이유는 commit 실패를 미리 알리기 위해서다.

```json
{
  "reason": "야근으로 주문 생성 milestone 2주 지연",
  "version": 3,
  "milestones": [
    { "id": "a1…", "title": "기반 다지기", "description": null, "startDate": "2026-09-30", "endDate": "2026-10-25",
      "priority": "MUST", "status": "DONE", "sortOrder": 0, "skillCodes": ["SPRING.MVC_REST", "TESTING.JUNIT"] },
    { "id": "a2…", "title": "주문 생성", "description": null, "startDate": "2026-10-26", "endDate": "2026-12-20",
      "priority": "MUST", "status": "IN_PROGRESS", "sortOrder": 1, "skillCodes": ["SPRING.TRANSACTION", "DATABASE.TRANSACTION.LOCKING"] },
    { "id": null, "title": "설명과 정리", "description": null, "startDate": "2026-12-21", "endDate": "2027-01-04",
      "priority": "SHOULD", "status": "PLANNED", "sortOrder": 2, "skillCodes": ["EXPLANATION.PROJECT_STORY"] }
  ],
  "acceptedDeferrals": ["SYSTEM_DESIGN.CACHE"],
  "acceptedTargetReductions": [ { "skillCode": "DEVOPS.DOCKER", "axis": "DEBUGGING", "newTarget": 3 } ],
  "restoredDeferrals": ["DEVOPS.CI_GITHUB_ACTIONS"],
  "acceptedTargetRaises": []
}
```

```json
{
  "planId": "9e2b…",
  "today": "2026-12-08",
  "horizonDate": "2027-01-05",
  "nominalBudgetMinutes": 2820,
  "completionRateBp": 6800,
  "effectiveBudgetMinutes": 1917,
  "requiredMustMinutes": 2240,
  "requiredShouldMinutes": 900,
  "ratioBp": 11684,
  "riskLevel": "HIGH",
  "deferSuggestions": [
    { "skill": { "id": "…", "code": "DEVOPS.KUBERNETES_BASICS", "name": "Kubernetes 기초", "category": "DEVOPS" },
      "priority": "SHOULD", "practicalImportanceBp": 3000, "requiredMinutes": 480 },
    { "skill": { "id": "…", "code": "SYSTEM_DESIGN.MESSAGE_QUEUE", "name": "메시지 큐 개념", "category": "SYSTEM_DESIGN" },
      "priority": "SHOULD", "practicalImportanceBp": 3000, "requiredMinutes": 300 },
    { "skill": { "id": "…", "code": "ALGORITHM.GRAPH", "name": "그래프 탐색", "category": "ALGORITHM" },
      "priority": "SHOULD", "practicalImportanceBp": 5000, "requiredMinutes": 120 }
  ],
  "mustTargetReductionSuggestions": [
    { "skill": { "id": "…", "code": "DATABASE.EXECUTION_PLAN", "name": "실행계획 읽기", "category": "DATABASE" },
      "axis": "IMPLEMENTATION", "currentTarget": 4, "newTarget": 3, "planningLevel": 1, "savedMinutes": 172 },
    { "skill": { "id": "…", "code": "WEB_HTTP.CACHING", "name": "HTTP 캐싱", "category": "WEB_HTTP" },
      "axis": "IMPLEMENTATION", "currentTarget": 4, "newTarget": 3, "planningLevel": 1, "savedMinutes": 172 }
  ],
  "expansionSuggestions": [],
  "riskAfterSuggestions": { "requiredMustMinutes": 1896, "requiredShouldMinutes": 0, "ratioBp": 9890, "riskLevel": "MEDIUM" }
}
```

예시 수치 근거 (사용자 평일 45분·주말 240분, 이 예시의 목표일 2027-01-05 = `horizonDate`):
- nominal: plan-day 2026-12-08(화) ~ 2027-01-04, 28일 = 평일 20 × 45 + 주말 8 × 240 = **2820** (`06-learning-engine-rules.md` §3.2). effective = `floorDiv(2820 × 6800, 10000)` = **1917** (`06-learning-engine-rules.md` §3.3).
- `ratioBp = floorDiv(2240 × 10000, 1917)` = **11684** → `2240 × 10000 ≤ 1917 × 12500`이므로 HIGH (`06-learning-engine-rules.md` §4.3).
- 축소 먼저 (`06-learning-engine-rules.md` §4.4 3단계, `practicalImportance ASC` — 앞 skill 0.60, 뒤 skill 0.65): `DATABASE.EXECUTION_PLAN` step 150, target (4,4,3,3), planning (2,1,2,2) → weightedGap 52,000 → required 897, IMPLEMENTATION 4→3 후 42,000 → 725, 절감 172. `WEB_HTTP.CACHING` step 150, target (4,4,4,3), planning (2,1,2,2) → 56,000 → 966, 축소 후 46,000 → 794, 절감 172. 두 번째 제안 후 `requiredMust'` = 2240 − 344 = 1896 ≤ 1917이므로 멈춘다.
- defer 그다음 (§4.4 4단계): `requiredMust'(1896) + 남은 SHOULD(900, 요청에서 defer한 `SYSTEM_DESIGN.CACHE` 제외) = 2796 > 1917`이므로 `(practicalImportance ASC, requiredMinutes DESC, code ASC)` 순서로 넣는다: `KUBERNETES_BASICS`(480) → 2316, `MESSAGE_QUEUE`(300) → 2016, `GRAPH`(120) → 1896 ≤ 1917 → 멈춤. 결과적으로 셋 다 제안. 응답의 `deferSuggestions` 순서가 이 순서다.
- `riskAfterSuggestions`: requiredMust 1896, requiredShould 0, `floorDiv(1896 × 10000, 1917)` = 9890 → MEDIUM.

### 7.8 `POST /plans/{planId}/replan` — 새 plan 버전 확정

| 항목 | 값 |
|---|---|
| operationId | `planReplan` |
| 인증 / IK | Bearer / IK |
| 요청 | `ReplanRequest` (`@Validated({Default.class, ReplanRequest.Commit.class})`) |
| 응답 | 201 `ReplanCommitResponse` |
| 오류 | 400 `VALIDATION_FAILED`(`DATE_ORDER_INVALID`, `DATE_OUT_OF_RANGE`, `MILESTONE_NOT_IN_PLAN`, `DUPLICATE_VALUE`, `MUTUALLY_EXCLUSIVE`, `SKILL_CODE_UNKNOWN`, `SKILL_NOT_IN_PLAN`, `TARGET_NOT_REDUCED`, `TARGET_NOT_RAISED`, `VALUE_NOT_ALLOWED`), 404 `PLAN_NOT_FOUND`, 409 `PLAN_NOT_ACTIVE`, 409 `CONCURRENT_MODIFICATION`, 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S1 최소 구현(milestone 편집·reason. `acceptedDeferrals`, `acceptedTargetReductions`, `restoredDeferrals`, `acceptedTargetRaises`가 비어 있지 않으면 `VALUE_NOT_ALLOWED`) → S2 완성 · FR-04, FR-05, AC-01, AC-03, AC-24 |

```java
public record ReplanRequest(
        @NotBlank(groups = ReplanRequest.Commit.class) @Size(max = 1000) String reason,
        @NotNull Long version,                                               // 대상 plan의 version (planVersion 아님)
        @NotNull @Size(max = 24) List<@NotNull @Valid MilestoneInput> milestones,
        @NotNull @Size(max = 100) @UniqueElements List<@NotBlank @Size(max = 100) String> acceptedDeferrals,
        @NotNull @Size(max = 400) List<@NotNull @Valid TargetReductionInput> acceptedTargetReductions,
        @NotNull @Size(max = 100) @UniqueElements List<@NotBlank @Size(max = 100) String> restoredDeferrals,
        @NotNull @Size(max = 400) List<@NotNull @Valid TargetRaiseInput> acceptedTargetRaises) {   // 확장 제안 RAISE_TARGET을 받아들일 때

    public interface Commit {}
}

public record MilestoneInput(
        UUID id,                                   // 기존 milestone 유지 시. 새 milestone은 null
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull Priority priority,
        @NotNull MilestoneStatus status,
        @NotNull @Min(0) @Max(10000) Integer sortOrder,
        @NotNull @Size(max = 30) @UniqueElements List<@NotBlank @Size(max = 100) String> skillCodes) {}

public record TargetReductionInput(
        @NotBlank @Size(max = 100) String skillCode,
        @NotNull SkillAxis axis,
        @NotNull @Min(0) @Max(5) Integer newTarget) {}

public record TargetRaiseInput(
        @NotBlank @Size(max = 100) String skillCode,
        @NotNull SkillAxis axis,
        @NotNull @Min(0) @Max(5) Integer newTarget) {}

public record ReplanCommitResponse(
        PlanView plan,                                     // 새 ACTIVE plan
        List<MilestoneIdMappingView> milestoneIdMapping) {} // 요청에 id가 있던 milestone만, 요청 순서

public record MilestoneIdMappingView(UUID previousId, UUID newId) {}
```

검사 순서:
1. plan 조회(본인, 404 `PLAN_NOT_FOUND`) → `status = ACTIVE`(409 `PLAN_NOT_ACTIVE`) → `version` 일치(409 `CONCURRENT_MODIFICATION`) — `06-learning-engine-rules.md` §11.2 1단계.
2. 도메인 검사:

| 대상 | 규칙 | code |
|---|---|---|
| `milestones[i]` | `startDate ≤ endDate` | `DATE_ORDER_INVALID` |
| `milestones[i].startDate/endDate` | 오늘 − 1년 ~ 오늘 + 3년 | `DATE_OUT_OF_RANGE` |
| `milestones[i].id` | null이 아니면 대상 plan의 milestone | `MILESTONE_NOT_IN_PLAN` |
| `milestones[*].id` | null 아닌 값끼리 유일 | `DUPLICATE_VALUE` |
| `milestones[i].skillCodes[j]` | 활성 skill | `SKILL_CODE_UNKNOWN` |
| `acceptedDeferrals[i]` | 활성 skill / 대상 plan의 `plan_skill_target`에 있음 | `SKILL_CODE_UNKNOWN` / `SKILL_NOT_IN_PLAN` |
| `acceptedTargetReductions[i]` | skill 존재·plan 소속, `(skillCode, axis)` 유일, `newTarget` < 현재 그 축 target | `SKILL_CODE_UNKNOWN` / `SKILL_NOT_IN_PLAN` / `DUPLICATE_VALUE` / `TARGET_NOT_REDUCED` |
| `restoredDeferrals[i]` | 활성 skill / 대상 plan의 `plan_skill_target`에 있음 / `acceptedDeferrals`에 같은 code가 없음 | `SKILL_CODE_UNKNOWN` / `SKILL_NOT_IN_PLAN` / `MUTUALLY_EXCLUSIVE` |
| `acceptedTargetRaises[i]` | skill 존재·plan 소속, `(skillCode, axis)` 유일, 같은 `(skillCode, axis)`가 `acceptedTargetReductions`에 없음, 같은 skill이 `acceptedDeferrals`에 없음, 현재 그 축 target < `newTarget` ≤ 5 | `SKILL_CODE_UNKNOWN` / `SKILL_NOT_IN_PLAN` / `DUPLICATE_VALUE` / `MUTUALLY_EXCLUSIVE` / `MUTUALLY_EXCLUSIVE` / `TARGET_NOT_RAISED` |

처리: `06-learning-engine-rules.md` §11.2 3~9단계를 한 트랜잭션으로 수행한다.
- 이전 plan `SUPERSEDED` + **flush** 후 새 plan INSERT(I-02). 동시 replan으로 `uq_learning_plan_one_active` 또는 `unique(user_id, plan_version)` 위반이 나면 409 `CONCURRENT_MODIFICATION`(AC-24).
- 새 milestone은 모두 새 UUID를 받는다. `milestoneIdMapping`으로 이전 id를 알려준다.
- `plan_skill_target`은 이전 plan에서 복사한 뒤 적용한다: `acceptedDeferrals` → `deferred = true`, `adjustment = DEFERRED` / `acceptedTargetReductions` → 해당 축 target = `newTarget`, `adjustment = TARGET_REDUCED` (같은 skill이 defer와 축소를 모두 받으면 `DEFERRED`) / `restoredDeferrals` → `deferred = false`, `adjustment = USER_EDITED` / `acceptedTargetRaises` → 해당 축 target = `newTarget`, `adjustment = USER_EDITED`(축소와 함께 받은 skill은 `TARGET_REDUCED`) (`06-learning-engine-rules.md` §11.2 7단계). 복원과 축소를 함께 받은 skill은 `TARGET_REDUCED`. 이전 plan에서 `deferred = false`인 skill을 복원 목록에 넣으면 변경 없음(no-op). 복원하지 않은 defer 항목은 그대로 복사된다.
- `PLAN_REPLANNED` learning event(`04-domain-model-and-db.md` §6), (S2부터) 오늘 snapshot upsert.
- 과거 `daily_plan`, `learning_task`는 이전 plan·milestone id를 그대로 참조한다.

### 7.9 `GET /plans/active/budget` — 현재 budget·risk

| 항목 | 값 |
|---|---|
| operationId | `planGetActiveBudget` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `BudgetView` |
| 오류 | 404 `PLAN_NOT_FOUND`, 404 `LEARNING_GOAL_NOT_FOUND` |
| Sprint · 요구사항 | S2 · FR-05, AC-03, AC-30 |

- 요청 시점에 `06-learning-engine-rules.md` §3, §4.1~§4.3으로 계산한다. 저장하지 않는다(저장은 `ProgressSnapshotJob`, replan, 온보딩). 제안은 포함하지 않는다(preview 사용).

---

## 8. Today 모듈

Controller: `TodayController`. Service: `TodayPlanService`, `TodayQueryService`. Today 기능은 AI를 호출하지 않는다(AC-12).

### 8.1 공통 view record

```java
public record TodayView(
        UUID dailyPlanId,
        LocalDate planDate,
        int availableMinutes,
        EnergyLevel energyLevel,
        RiskLevel deadlineRisk,                 // null 가능
        boolean comebackMode,
        int generationCount,
        Instant generatedAt,
        MainTaskView mainTask,                  // 규칙 아래. 후보가 없으면 null
        ReviewTaskView reviewTask,              // REVIEW task가 없으면 null (due가 없거나 reviewMinutes = 0)
        List<MainTaskView> earlierMainTasks,    // mainTask를 뺀 같은 날의 다른 학습 과제, sortOrder ASC
                                                // (재생성이 남긴 지난 main + 06 §5.6 추가 과제. REVIEW는 빠진다)
        TipExperimentView tipExperiment) {}     // WILL_TRY로 표시한 팁의 실험 후보 1건. 없으면 null (06 §5.12 TIP-6)

public record MainTaskView(
        UUID id,
        TaskType taskType,
        UUID skillId,                  // null 가능. 화면이 §21.3으로 그 개념의 노트를 찾는 열쇠다
        String skillCode,              // null 가능
        String skillName,              // null 가능
        UUID milestoneId,              // null 가능
        UUID challengeId,              // taskType = CHALLENGE일 때만
        UUID sideProjectId,            // taskType = PROJECT_TASK일 때만 (learning_task.side_project_id, SP-2, §19)
        String readingKey,             // taskType = READ_CODE(항상) 또는 READING(자료가 있을 때만). 그 외 null
                                       // (learning_task.reading_key, §19.7 — kind로 CODE/CONCEPT를 구분한다)
        UUID redoSourceTaskId,         // taskType = REDO일 때만. 다시 만드는 원본 task (06 §5.10 RE-1, I-20)
        TaskType redoSourceTaskType,   // taskType = REDO일 때만. CHALLENGE | PROJECT_TASK (화면 문구·되돌아갈 곳)
        Boolean redoWithoutAi,         // taskType = REDO이고 COMPLETED일 때만. 그 외 null (RE-6)
        String title,
        String description,            // null 가능
        String whyItMatters,           // 이 기술을 왜 하는지 한 줄 (§6.4와 같은 콘텐츠 값). skill이 없거나 값이 없으면 null
        ChecklistView checklist,       // 시작 전·끝내기 전 확인 목록. 맞는 목록이 없으면 null
        int estimatedMinutes,
        TaskStatus status,
        List<ReasonView> reasons,      // 1~3개 (06 §5.8)
        Boolean explainedToPerson,     // taskType = EXPLAIN | READ_CODE이고 값이 있을 때만. 그 외 null (I-24)
        String explainedNote,          // 〃 (마스킹본, ≤ 500자)
        Instant completedAt,
        long version) {}

public record ReasonView(
        ReasonCode code,
        String text) {}

public record ChecklistView(
        String key,                    // content/checklists 의 key (예: "CHK.API.DESIGN")
        List<String> before,           // 시작 전 확인 목록 3~5개
        List<String> after) {}         // 끝내기 전 확인 목록 3~5개

public record TipExperimentView(
        String tipKey,                 // 05 §20의 팁. 자세한 내용은 GET /tips/today 또는 GET /tips에서 본다
        String title,
        String experiment,             // 5분 안에 재현하는 방법. 팁에 없으면 null
        int estimatedMinutes) {}       // devpilot.tips.experiment-minutes (기본 25)

public record ReviewTaskView(
        UUID id,
        int estimatedMinutes,
        int dueReviewCount,            // 조회 시점의 남은 due 수 (06 §6.5 대상, cap 적용)
        TaskStatus status,
        long version) {}

public record TaskStatusView(          // PATCH 응답
        UUID id,
        UUID dailyPlanId,
        LocalDate planDate,
        boolean main,
        TaskType taskType,
        TaskStatus status,
        Instant completedAt,
        long version) {}
```

`mainTask` 선택: 그 daily_plan의 `is_main` task 중 `PLANNED`/`IN_PROGRESS`인 것(I-04로 최대 1개). 없으면 `sort_order`가 가장 큰 main task. main task가 하나도 없으면 `null`.

`earlierMainTasks` 선택: 그 daily_plan의 task 중 `taskType != REVIEW`이고 `mainTask`가 아닌 것을 **모두** `sort_order` ASC로 싣는다. 두 종류가 들어간다 — (a) 재생성이 남긴 지난 main(`COMPLETED`·`DEFERRED`), (b) 남는 시간을 채우는 **추가 과제**(`06` §5.6, `is_main = false`, `sort_order`는 main 다음). 추가 과제를 main으로 저장할 수 없는 이유는 daily plan당 활성 main이 1개이기 때문이다(I-04). 화면은 상태로 둘을 구분한다 — `PLANNED`면 아직 할 일, `COMPLETED`/`DEFERRED`면 지난 기록이다.

`reasons[].text` 생성: 저장된 `reason_codes` 순서대로 `ReasonTemplates`(`06-learning-engine-rules.md` §5.8 문구)를 **응답 생성 시** 적용한다. 변수 값은 생성 시점에 저장한 `score_breakdown.reasonParams`(`04-domain-model-and-db.md` §5.1)에서 읽는다. 조회 시점의 plan·skill state로 다시 계산하지 않는다.

| 템플릿 변수 | `reasonParams` 필드 | 사용하는 ReasonCode |
|---|---|---|
| `{milestoneTitle}` | `milestoneTitle` (string) | `MILESTONE_CORE`, `MILESTONE_NEXT` |
| `{planningImplementation}` | `planningImplementation` (int 0~5) | `LARGE_SKILL_GAP` |
| `{targetImplementation}` | `targetImplementation` (int 0~5) | `LARGE_SKILL_GAP` |
| `{overdueDays}` | `overdueDays` (int, 상한 없음) | `REVIEW_OVERDUE` |
| `{repo.name}` | `repoName` (string) | `READ_REAL_CODE` |
| `{redoDaysAfter}` | `redoDaysAfter` (int ≥ 1) | `REDO_WITHOUT_AI` |

- 필요한 변수가 `null`이거나 `reasonParams`가 없으면(이전 버전 데이터) 그 reason은 코드만 두고 `text`는 변수 부분을 뺀 고정 문구로 만든다: `milestone 핵심 항목`, `다음 milestone 준비`, `목표 수준과 차이가 큼`, `복습이 밀림`.

`redoSourceTaskId` 저장 규칙: `REDO` 과제는 생성 시점에 재현 후보의 원본 task id를 `learning_task.redo_source_task_id`에 고정한다(I-20, `06` §5.10 RE-3). `redoSourceTaskType`은 응답을 만들 때 그 원본 행에서 읽는다(저장하지 않는다). 원본 task 행은 사용자 데이터가 지워질 때만 사라지므로 조회에 실패하지 않는다.

`sideProjectId`·`readingKey` 저장 규칙: `PROJECT_TASK`는 생성 시점의 `ACTIVE` 사이드 프로젝트 중 `updated_at`이 가장 최근인 것 하나를 `learning_task.side_project_id`에 저장한다(SP-3, `06-learning-engine-rules.md` §5.3). `ACTIVE` 프로젝트가 없으면 `PROJECT_TASK`를 제안하지 않는다(SP-1). `READ_CODE`는 고른 코드 읽기의 key를 `learning_task.reading_key`에 **반드시** 저장한다(`CuratedReadingRegistry`, `19-content-spec.md` §3.8). `READING`은 고른 **개념 읽기**의 key를 같은 칸에 저장하고, 그 skill에 후보가 없으면 `null`로 둔다(`ConceptReadingRegistry`, `19-content-spec.md` §3.13 — 자료 없이 제안되는 지금까지의 `READING`과 같다). 두 값은 **생성 시점에 고정**되고 조회 때 다시 계산하지 않는다(`reasons`와 같은 원칙). 그래서 콘텐츠에서 자료가 은퇴해도 지난 과제가 가리키는 자료는 바뀌지 않는다. 프로젝트가 삭제되면 `side_project_id`는 `null`이 되고(§19.6) task는 그대로 남는다.

`tipExperiment` 규칙: `WILL_TRY`로 표시한 팁(`user_daily_tip.feedback = WILL_TRY`) 중 `shown_on` DESC → `tip_key` ASC로 **하나만** 붙인다. 표시한 그날은 붙이지 않고 다음 plan-day부터 보인다. **`learning_task`를 만들지 않고 planner에도 들어가지 않는다** — 점수·제안·시간 배분과 무관한 표시용 후보다(`06` §5.12 TIP-6). 없으면 `null`이다.

`whyItMatters`·`checklist` 규칙: 둘 다 **저장하지 않고 응답을 만들 때 콘텐츠에서 읽는다**(`reasons[].text`와 같은 방식이지만 값은 콘텐츠 최신본이다). `whyItMatters`는 task의 `skill_id`에 해당하는 기술 트리 값이다(§6.4). `checklist`는 `content/checklists/*.yaml` 중 `taskTypes`에 그 task의 `taskType`이 있고 `skillCodes`가 task의 skill과 겹치는 항목이며, 둘 이상이면 `key` ASC 첫 번째다. 맞는 항목이 없거나 task에 skill이 없으면 `null`이다(`19-content-spec.md`). 두 값은 `mainTask`와 `earlierMainTasks[]`에만 있다 — `reviewTask`는 skill이 없다.

`learning_task.milestone_id` 저장 규칙: 선택된 skill이 오늘을 포함하는 milestone에 있으면 그 milestone(여러 개면 `end_date` ASC, `id` ASC 첫 번째), 아니면 다음 milestone(`06-learning-engine-rules.md` §5.2의 2번), 둘 다 아니면 null.

### 8.2 `POST /today/generate` — 오늘 계획 생성·재생성

| 항목 | 값 |
|---|---|
| operationId | `todayGenerate` |
| 인증 / IK | Bearer / IK |
| 요청 | `TodayGenerateRequest` |
| 응답 | 200 `TodayView` (생성·재생성 모두 200) |
| 오류 | 404 `PLAN_NOT_FOUND`(활성 plan 없음), 409 `TODAY_ALREADY_STARTED`, 409 `TODAY_ALREADY_COMPLETED`, 409 `CONCURRENT_MODIFICATION` |
| Sprint · 요구사항 | S2 · FR-07, FR-21, AC-02, AC-17 |

```java
public record TodayGenerateRequest(
        @NotNull @Min(5) @Max(720) Integer availableMinutes,   // 생략하면 400 VALIDATION_FAILED (§1.1)
        @NotNull EnergyLevel energyLevel,
        Boolean force) {}                                      // 생략하거나 null이면 false
```

```json
{ "availableMinutes": 45, "energyLevel": "NORMAL", "force": false }
```

```json
{
  "dailyPlanId": "d7f1…",
  "planDate": "2026-10-12",
  "availableMinutes": 45,
  "energyLevel": "NORMAL",
  "deadlineRisk": "LOW",
  "comebackMode": false,
  "generationCount": 1,
  "generatedAt": "2026-10-12T11:00:03Z",
  "mainTask": {
    "id": "t1…",
    "taskType": "EXPLAIN",
    "skillCode": "SPRING.TRANSACTION",
    "skillName": "Spring Transaction",
    "milestoneId": "a2…",
    "challengeId": null,
    "title": "Spring Transaction 내 말로 설명하기",
    "description": "5문장 이내로 설명하고 예시를 하나 드세요.",
    "whyItMatters": "트랜잭션 경계를 모르면 어디서 데이터가 어긋나는지 설명할 수 없다.",
    "checklist": null,
    "estimatedMinutes": 15,
    "status": "PLANNED",
    "reasons": [
      { "code": "MILESTONE_CORE", "text": "Spring Boot/JPA milestone 핵심 항목" },
      { "code": "HIGH_PRACTICAL_IMPORTANCE", "text": "실무에서 중요도가 높은 기술" },
      { "code": "LARGE_SKILL_GAP", "text": "목표 수준과 차이가 큼 (구현 1/4)" }
    ],
    "explainedToPerson": null,
    "explainedNote": null,
    "completedAt": null,
    "version": 0
  },
  "reviewTask": { "id": "t0…", "estimatedMinutes": 5, "dueReviewCount": 3, "status": "PLANNED", "version": 0 },
  "earlierMainTasks": [],
  "tipExperiment": { "tipKey": "TIP.LOGGING.LEVELS.001", "title": "로그 레벨은 언제 무엇을 쓰나",
                     "experiment": "레벨을 한 단계 올리고 같은 요청을 한 번 보내 어떤 줄이 사라지는지 본다.",
                     "estimatedMinutes": 25 }
}
```

처리 (한 트랜잭션):
1. 오늘 plan-day 계산(`06-learning-engine-rules.md` §2). 활성 plan이 없으면 404 `PLAN_NOT_FOUND`.
2. 기존 daily_plan과 main task 상태에 따라 `06-learning-engine-rules.md` §5.9 표를 적용한다(409 두 가지 포함). 삭제·`DEFERRED` 전환 뒤에는 새 task INSERT 전에 flush한다.
3. 입력 계산: `aiStatus`(§1.9.1 — `DISABLED`/`BALANCE_EXHAUSTED`이면 `TaskProposalPolicy`가 `CHALLENGE` 제안을 건너뛴다, `06-learning-engine-rules.md` §5.3), risk(`06-learning-engine-rules.md` §3~§4.3, 요청 시점 계산 → `deadline_risk`. budget·risk 규칙은 Today와 같은 S2에 들어온다 — 사용자가 등록한 목표일이 첫 Today부터 우선순위에 반영된다), comebackMode(`06-learning-engine-rules.md` §5.5), 후보·제안·점수·modifier·reason(`06-learning-engine-rules.md` §5.2~§5.5, §5.8), 시간 배분(`06-learning-engine-rules.md` §5.6), due review(`06-learning-engine-rules.md` §6.5).
4. `daily_plan` INSERT 또는 갱신: `available_minutes`, `energy_level`, `deadline_risk`, `comeback_mode`, `learning_plan_id`, `generated_at = now`, 재생성이면 `generation_count + 1`.
5. main task INSERT: `is_main = true`, `sort_order` = 그 daily_plan에 남은 task의 최대 `sort_order` + 1 (처음이면 1), `reason_codes`, `score_breakdown`(`reasonParams` 포함, `04-domain-model-and-db.md` §5.1). 후보 skill이 하나도 없으면 main task를 만들지 않고 `mainTask = null`이다(`06-learning-engine-rules.md` §5.2). 이때 6단계 조건을 만족하면 REVIEW task만 만든다.
5-1. 추가 task INSERT(`06-learning-engine-rules.md` §5.6 "추가 과제"): main 다음 순위 후보로 최대 `devpilot.planner.max-extra-tasks`개. `is_main = false`(활성 main은 1개여야 한다 — I-04), `sort_order` = main의 `sort_order` + 1, +2, …, `score_breakdown.rank` = 2·3·4. main을 만들지 않았으면 추가 task도 만들지 않는다.
6. REVIEW task: `reviewMinutes ≥ 1`이고(`06-learning-engine-rules.md` §5.6, `learning_task.estimated_minutes` CHECK ≥ 1), 그 daily_plan에 `PLANNED`가 아닌 REVIEW task가 없을 때만 INSERT(`is_main = false`, `sort_order = 0`, `estimated = reviewMinutes`). `reviewMinutes = 0`이면(예: `availableMinutes = 5`) due review가 있어도 만들지 않고 `reviewTask = null`이다. 기존 `PLANNED` REVIEW task는 2단계에서 삭제된 상태다.
7. 동시 요청으로 `unique(user_id, plan_date)` 또는 `uq_learning_task_one_active_main` 위반이 나면 409 `CONCURRENT_MODIFICATION`.

### 8.3 `GET /today` — 오늘 계획 조회

| 항목 | 값 |
|---|---|
| operationId | `todayGet` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `TodayView` |
| 오류 | 404 `TODAY_NOT_GENERATED` (클라이언트는 생성 입력 화면을 보여준다) |
| Sprint · 요구사항 | S2 · FR-07, AC-02, AC-17 |

- 조회 시점의 plan-day로 daily_plan을 찾는다. 날짜 경계(`dayStartHour`)가 지나면 전날 plan은 반환하지 않는다.

### 8.4 `PATCH /today/tasks/{taskId}` — task 상태 변경

| 항목 | 값 |
|---|---|
| operationId | `todayUpdateTaskStatus` |
| 인증 / IK | Bearer / — |
| 요청 | `TaskStatusPatchRequest` |
| 응답 | 200 `TaskStatusView` |
| 오류 | 400 `VALIDATION_FAILED`(`VALUE_NOT_ALLOWED` — field `readingFeedback`·`redoWithoutAi`·`explainedToPerson`·`explainedNote`, `VALUE_REQUIRED` — field `redoWithoutAi`), 400 `UNKNOWN_ENUM_VALUE`, 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`, 409 `CONCURRENT_MODIFICATION`, 422 `SECRET_DETECTED_BLOCKED`(`explainedNote`) |
| Sprint · 요구사항 | S2 (`readingFeedback`·`explainedToPerson`은 S3, `redoWithoutAi`는 S4) · FR-07, FR-27, FR-28, AC-02, AC-28, AC-31 |

```java
public record TaskStatusPatchRequest(
        @NotNull TaskStatus status,
        ReadingFeedback readingFeedback,      // 선택. READ_CODE 완료 때만: HELPFUL | TOO_HARD | BORING (04 §3)
        Boolean redoWithoutAi,                // REDO 완료 때 필수. 그 밖에는 금지 (06 §5.10 RE-6)
        Boolean explainedToPerson,            // 선택. EXPLAIN | READ_CODE 완료 때만 (I-24)
        @Size(max = 500) String explainedNote, // 선택. explainedToPerson = true일 때만
        @NotNull Long version) {}
```

```json
{ "status": "COMPLETED", "readingFeedback": "HELPFUL", "redoWithoutAi": null,
  "explainedToPerson": true, "explainedNote": "팀 동료에게 5분 동안 설명했고 전파 속성에서 막혔다.", "version": 1 }
```

- 허용 전이는 `04-domain-model-and-db.md` §4.1 표의 PATCH 행뿐이다: `PLANNED → IN_PROGRESS`, `PLANNED → SKIPPED`, `IN_PROGRESS → COMPLETED`(`completed_at = now`), `IN_PROGRESS → DEFERRED`, `SKIPPED → PLANNED`. 같은 상태로의 변경을 포함해 그 외는 409 `INVALID_STATE_TRANSITION`.
- `SKIPPED → PLANNED`는 같은 daily_plan에 `PLANNED`/`IN_PROGRESS` main task가 없을 때만 허용한다(main task에만 해당). 동시 요청의 partial unique index 위반도 409 `INVALID_STATE_TRANSITION`.
- 오늘이 아닌 plan-day의 task도 같은 규칙으로 변경할 수 있다.
- REVIEW task(`is_main = false`)도 같은 전이표를 쓴다.
- **`READ_CODE` task의 `IN_PROGRESS → COMPLETED`**는 그 task를 대상으로 하는 `COMPLETED` 러버덕 세션(`targetType = CODE_READING`, `targetId = taskId`)이 **1개 이상** 있어야 한다(RC-1, `06-learning-engine-rules.md` §5.3). 없으면 409 `INVALID_STATE_TRANSITION`. 읽었다는 체크만으로는 완료가 아니다. `SKIPPED`·`DEFERRED`에는 이 조건이 없다. 러버덕 정리(§9.8)는 과제 상태를 바꾸지 않으므로 `READ_CODE` 과제도 이 PATCH로 완료한다.
- **`readingFeedback`(읽기 평가, 선택)**: `READ_CODE` 과제를 `COMPLETED`로 바꾸는 요청에서만 받는다. 값이 있으면 같은 트랜잭션에서 `learning_task.reading_feedback`에 저장한다(`04` §3 `ReadingFeedback`, I-19). 생략하거나 `null`이면 저장하지 않는다(`null` 그대로). 그 밖의 요청(`status ≠ COMPLETED`, 또는 `READ_CODE`가 아닌 task)에 값이 있으면 400 `VALIDATION_FAILED`(field `readingFeedback`, code `VALUE_NOT_ALLOWED`)이고 아무것도 바꾸지 않는다. 검사 순서: 형식(enum) → 소유권(404) → 전이·RC-1(409) → `readingFeedback` 허용 여부(400). 평가는 learning event를 만들지 않고 레벨·planner·budget 규칙의 입력이 아니다(`06` §5.3). 사람이 하는 소스 점검(`19` §8.5)에서 export로 읽는다. 응답 `TaskStatusView`에는 넣지 않는다.
- **`redoWithoutAi`(재현 결과, S4)**: `REDO` 과제를 `COMPLETED`로 바꾸는 요청에서는 **필수**다(RE-6, I-21). 없거나 `null`이면 400 `VALIDATION_FAILED`(field `redoWithoutAi`, code `VALUE_REQUIRED`)이고 아무것도 바꾸지 않는다. 그 밖의 요청(`status ≠ COMPLETED`, 또는 `REDO`가 아닌 task)에 값이 있으면 400 `VALIDATION_FAILED`(code `VALUE_NOT_ALLOWED`)다. 저장은 같은 트랜잭션에서 `learning_task.redo_without_ai`에 하고, 이어서 `REDO_COMPLETED` 이벤트를 task의 skill로 기록한다(`04` §6). `redoWithoutAi = false`면 같은 트랜잭션에서 복습 카드를 upsert한다(RE-7 — `concept_key = REDO:{sourceTaskId}`, `source_type = REDO_TASK`, due = 다음 plan-day 시작). 이 경로는 AI를 부르지 않는다. 검사 순서: 형식 → 소유권(404) → 전이·RC-1(409) → `readingFeedback`·`redoWithoutAi` 허용·필수 여부(400) → 저장·이벤트.
- **`explainedToPerson`·`explainedNote`(설명 기록, S3)**: `EXPLAIN` 또는 `READ_CODE` 과제를 `COMPLETED`로 바꾸는 요청에서만 받는다(둘 다 선택). 값이 있으면 같은 트랜잭션에서 `learning_task.explained_to_person`·`explained_note`에 저장한다(I-24). 그 밖의 요청(`status ≠ COMPLETED`, 또는 두 유형이 아닌 task)에 값이 있으면 400 `VALIDATION_FAILED`(code `VALUE_NOT_ALLOWED`)이고 아무것도 바꾸지 않는다. `explainedNote`만 있고 `explainedToPerson`이 `true`가 아니면 같은 400이다 — 메모는 "다른 사람에게 설명했어요"에 붙는 한 줄이다(`02` 러버덕·설명 완료 화면). `explainedNote`는 저장 전에 마스킹한다(§1.11, 차단 시 422). 검사 순서는 `readingFeedback`·`redoWithoutAi`와 같은 단계(400)다. 응답 `TaskStatusView`에는 넣지 않는다 — 값은 `GET /today`의 `MainTaskView`(§8.1)에서 본다. `EXPLAIN` 과제를 `explainedToPerson = true`로 완료하면 그 skill의 `EXPLAIN` 학습 단계가 채워진다(§6.4, `06` §5.11). 학습 이벤트는 만들지 않는다.
- `REDO` 과제를 `SKIPPED`·`DEFERRED`로 바꿀 때는 `redoWithoutAi`를 받지 않는다. `SKIPPED`는 RE-3의 **시도 1회**로 센다(`DEFERRED`는 아직 오늘 안 한 것이므로 세지 않는다 — `06` §5.10).
- 이 요청은 learning session을 만들지 않는다. 클라이언트는 `IN_PROGRESS`로 바꾼 뒤 `POST /learning-sessions`(§9.1)를 호출한다.

---

## 9. Learning 모듈 (세션) · 러버덕 (`rubberduck` 모듈)

Controller: `LearningSessionController`(`/learning-sessions*`, `learning` 모듈), `RubberDuckController`(`/rubber-duck*`, `rubberduck` 모듈). Service: `LearningSessionService`, `LearningEventRecorder`, `RubberDuckService`, 도메인 `RubberDuckPolicy`(`03-system-architecture.md` §2.2·§3.2 — 러버덕은 여러 모듈의 대상을 읽으므로 순환을 피해 독립 모듈이다).

```java
public record SessionView(
        UUID id,
        UUID learningTaskId,       // null 가능
        LocalDate planDate,        // 시작 시점 plan-day
        Instant startedAt,
        Instant completedAt,       // COMPLETED일 때만
        Integer actualMinutes,     // COMPLETED일 때만
        String selfReflection,
        SessionStatus status,
        long version) {}
```

### 9.1 `POST /learning-sessions` — 세션 시작

| 항목 | 값 |
|---|---|
| operationId | `learningStartSession` |
| 인증 / IK | Bearer / IK |
| 요청 | `SessionStartRequest` (body 생략 가능 = 모든 필드 null) |
| 응답 | 201 `SessionStartResponse` |
| 오류 | 400 `VALIDATION_FAILED`(`REFERENCE_NOT_FOUND`), 409 `CONCURRENT_MODIFICATION` |
| Sprint · 요구사항 | S2 · FR-08, AC-02 |

```java
public record SessionStartRequest(UUID learningTaskId) {}

public record SessionStartResponse(
        SessionView session,
        UUID abandonedSessionId) {}   // 이번 요청으로 ABANDONED 처리한 이전 세션. 없으면 null
```

처리 (한 트랜잭션):
1. `learningTaskId`가 있으면 본인 task인지 확인한다. 아니면 `REFERENCE_NOT_FOUND`(field `learningTaskId`).
2. 사용자의 `IN_PROGRESS` 세션이 있으면 `ABANDONED`로 바꾸고 flush한다(I-05).
3. `learning_session` INSERT: `plan_date` = 오늘 plan-day, `status = IN_PROGRESS`.
4. `SESSION_STARTED` 이벤트(`04-domain-model-and-db.md` §6): `skill_id` = task의 skill(없으면 null), `session_id` = 새 세션.
5. partial unique index 위반(동시 시작)은 409 `CONCURRENT_MODIFICATION`.
6. `learningTaskId`의 task가 `PLANNED`이면 같은 트랜잭션에서 `IN_PROGRESS`로 바꾼다(`04-domain-model-and-db.md` §4.1). 재생성이 PLANNED task를 삭제해 진행 중 세션의 task가 사라지는 것을 막는다. 그 외 상태(`IN_PROGRESS`, `COMPLETED` 등)는 바꾸지 않는다.

- 응답 `TaskView`가 없으므로 클라이언트는 필요하면 `GET /today`로 task 상태를 다시 읽는다.

### 9.2 `POST /learning-sessions/{sessionId}/complete` — 세션 완료

| 항목 | 값 |
|---|---|
| operationId | `learningCompleteSession` |
| 인증 / IK | Bearer / IK |
| 요청 | `SessionCompleteRequest` |
| 응답 | 200 `SessionView` |
| 오류 | 400 `VALIDATION_FAILED`(`ACTUAL_MINUTES_EXCEEDS_ELAPSED`), 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`, 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S2 · FR-08, AC-02 |

```java
public record SessionCompleteRequest(
        @NotNull @Min(0) @Max(720) Integer actualMinutes,
        @Size(max = 5000) String selfReflection) {}
```

```json
{ "actualMinutes": 38, "selfReflection": "Transaction 경계를 아직 헷갈린다." }
```

- `selfReflection`은 먼저 마스킹한다(§1.11, 차단 시 422).
- 세션이 `IN_PROGRESS`가 아니면 409 `INVALID_STATE_TRANSITION`.
- 상한: `elapsedSeconds = max(0, now − startedAt)`, `limit = ceilDiv(elapsedSeconds × 3, 120)` (= ⌈경과 분 × 1.5⌉, `06-learning-engine-rules.md` §1 N-4). `actualMinutes > limit`이면 400 `VALIDATION_FAILED`, field `actualMinutes`, code `ACTUAL_MINUTES_EXCEEDS_ELAPSED`.
- 저장: `status = COMPLETED`, `completed_at = now`, `actual_minutes`, `self_reflection`(마스킹본, 빈 문자열은 null). `plan_date`는 시작 시점 값을 유지한다.
- `SESSION_COMPLETED` 이벤트(`{ taskId?, actualMinutes }`). task 상태는 바꾸지 않는다.

### 9.3 `POST /learning-sessions/{sessionId}/abandon` — 세션 중단

| 항목 | 값 |
|---|---|
| operationId | `learningAbandonSession` |
| 인증 / IK | Bearer / — (상태 전이가 멱등, §1.7) |
| 요청 | body 없음 |
| 응답 | 200 `SessionView` |
| 오류 | 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`(IN_PROGRESS 아님) |
| Sprint · 요구사항 | S2 · FR-08 |

- `status = ABANDONED`. `completed_at`, `actual_minutes`는 null로 둔다. 이벤트는 기록하지 않는다(`04-domain-model-and-db.md` §6에 해당 이벤트 없음).

### 9.4 `GET /learning-sessions` — 세션 이력

| 항목 | 값 |
|---|---|
| operationId | `learningListSessions` |
| 인증 / IK | Bearer / — |
| query | `from`, `to`: `LocalDate`, 선택, `plan_date` 기준 양 끝 포함 / `limit`, `cursor` |
| 정렬 | `startedAt` DESC, `id` DESC |
| 응답 | 200 `CursorPage<SessionView>` |
| 오류 | 400 `VALIDATION_FAILED`(`TYPE_MISMATCH`, `DATE_ORDER_INVALID`, `DATE_RANGE_TOO_LONG`), 400 `INVALID_CURSOR` |
| Sprint · 요구사항 | S2 · FR-08 |

- `from`과 `to`가 모두 있으면 `from ≤ to`, 기간(`daysBetween(from, to) + 1`)은 366일 이하.

### 9.5 러버덕 공통

러버덕은 학습자가 설명하고 **AI는 답을 주지 않고 질문만 되묻는** 대화다(RD-1~RD-7 = `06-learning-engine-rules.md` §9.5, AI 계약은 `17-ai-integration.md` §3.11·§3.12·§4.10·§4.11·§6.8). 세션 하나는 대상 1개와 skill 1개를 다루고, 종료할 때 정리를 **한 번만** 한다.

Service: `RubberDuckService`. 두 AI operation을 쓴다 — 턴마다 `RUBBER_DUCK`(동기), 종료 시 1회 `RUBBER_DUCK_SUMMARY`(동기).

```java
public record RubberDuckTurnView(
        int turnNo,
        String userText,                       // 마스킹본 (RD-6)
        String question,                       // 그 턴의 AI 질문
        boolean learnerStuck,                  // 이 턴의 설명이 "모르겠다"인지 — 서버 결정적 판정(06 §9.5 RD-3, AI 출력 아님)
        Instant createdAt,
        AiMeta aiMeta) {}                      // 그 턴의 RUBBER_DUCK 호출. ai_call_log가 지워졌으면 null

public record RubberDuckGapView(
        String conceptKey,
        String whatWasMissed,
        String whyItMatters,
        String reviewQuestion,
        UUID reviewItemId) {}                  // 이 gap에 연결된 복습 카드. 기존 카드의 due를 당긴 경우 그 카드 id

public record RubberDuckSummaryView(
        List<RubberDuckGapView> gaps,          // 0~3 (17 §4.11)
        List<String> confirmed,                // 0~5
        String overallNote) {}

public record RubberDuckSessionView(
        UUID id,
        RubberDuckTargetType targetType,
        UUID targetId,                         // CONCEPT이면 null
        String conceptKey,                     // CONCEPT일 때만
        String readingKey,                     // targetType = CODE_READING일 때만 (§19.7)
        String targetTitle,                    // 대상을 사람이 알아볼 문구. 대상이 지워졌으면 null
        SkillRef skill,                        // null 가능 (RD-7 — 이벤트를 남기지 않는 세션)
        RubberDuckStatus status,
        int turnCount,
        int maxTurns,                          // devpilot.rubberduck.max-turns (기본 5)
        boolean suggestHint,                   // RD-3: 마지막 2턴의 learnerStuck이 모두 true
        List<RubberDuckTurnView> turns,        // turnNo ASC. 조회 응답에만 채운다
        RubberDuckSummaryView summary,         // COMPLETED이고 정리에 성공했을 때만, 그 외 null
        AsyncFailureCode summarySkippedReason, // 정리 AI가 실패했을 때만 (§1.9.4), 그 외 null
        UUID learningSessionId,                // 시작 시점의 IN_PROGRESS 학습 세션. 없으면 null
        Instant startedAt,
        Instant completedAt,                   // COMPLETED · ABANDONED일 때만
        long version) {}
```

공통 규칙:

| 항목 | 규칙 |
|---|---|
| 소유권 | `sessionId`가 본인 것이 아니면 **없는 것과 똑같이** 404 `RESOURCE_NOT_FOUND`(§1.1) |
| 상태 | `RubberDuckStatus`: `IN_PROGRESS → COMPLETED \| ABANDONED`. `COMPLETED`·`ABANDONED`에서 나가는 전이는 없다 |
| 동시 세션 | 사용자당 `IN_PROGRESS` 세션은 **최대 1개**다. 새로 시작하면 이전 세션을 `ABANDONED`로 바꾼다(§9.6, `POST /learning-sessions`의 I-05와 같은 방식) |
| 방치된 세션 | `IN_PROGRESS`인 채 `devpilot.rubberduck.stale-after`(24h)가 지나면 `StaleRubberDuckJob`이 `ABANDONED`로 바꾼다. 정리 AI를 부르지 않고 복습 카드도 만들지 않는다 |
| 턴 상한 | `turn_count ≥ devpilot.rubberduck.max-turns`이면 더 낼 수 없다(RD-4). 클라이언트는 §9.8 `complete`로 유도한다 |
| 마스킹 | 설명은 저장·AI 전송 전에 `SecretMasker`를 통과한다(RD-6, §1.11). 원문은 어디에도 남기지 않는다 |
| AI 없이 | `aiStatus`가 `DISABLED`·`BALANCE_EXHAUSTED`이면 클라이언트는 시작 버튼을 막는다(`17-ai-integration.md` §3.10). 이미 있는 세션의 **조회**는 언제나 된다 |
| 이벤트 | 턴에서는 학습 이벤트를 남기지 않는다. `RUBBER_DUCK_COMPLETED`는 §9.8에서만 남는다 |

대상(`targetType`)별 계약:

| targetType | `targetId`가 가리키는 것 | 소유·존재 검사 | `skillCode` 생략 시 유도 | `targetTitle` / `17` §3.11 `targetSummary` |
|---|---|---|---|---|
| `CODE_READING` | `learning_task.id` (`task_type = READ_CODE`) | 본인 task | task의 `skill_id` | task의 `reading_key`로 찾은 reading의 저장소·경로·줄 범위·`question` (§19.7) |
| `CHALLENGE` | `challenge_attempt.id` | 본인 attempt | 그 challenge의 `challenge_skill` 첫 행(`sort_order` ASC, `skill.code` ASC) | challenge `title` + `prompt` |
| `REVIEW_ITEM` | `review_item.id` | 본인 카드 | `review_item.skill_id` | `review_item.prompt` |
| `CONCEPT` | (없음 — `conceptKey`를 쓴다) | — | `conceptKey`의 접두사와 같은 활성 `skill.code`. 없으면 null | `conceptKey` |
| `PROJECT_WORK` | `side_project.id` | 본인 프로젝트 | 유도하지 않는다(없으면 null) | `side_project.name` + `description` |

- 대상이 지워졌으면(예: 프로젝트 삭제) 세션은 남고 `targetTitle`이 `null`이 된다. `rubber_duck_session.target_id`에는 FK를 두지 않는다(`04-domain-model-and-db.md` §3).

### 9.6 `POST /rubber-duck` — 세션 시작

| 항목 | 값 |
|---|---|
| operationId | `learningStartRubberDuck` |
| 인증 / IK | Bearer / IK |
| 요청 | `RubberDuckStartRequest` |
| 응답 | 201 `RubberDuckStartResponse` |
| 오류 | 400 `VALIDATION_FAILED`(`ONE_OF_REQUIRED`, `MUTUALLY_EXCLUSIVE`, `REFERENCE_NOT_FOUND`, `SKILL_CODE_UNKNOWN`, `Pattern`), 400 `UNKNOWN_ENUM_VALUE`, 409 `AI_ASSIST_LOCKED_FOR_REDO`(S4, RE-5), 409 `CONCURRENT_MODIFICATION` |
| Sprint · 요구사항 | S3 (재현 잠금 S4) · FR-25, FR-28, AC-26, AC-31, RD-7 |

```java
public record RubberDuckStartRequest(
        @NotNull RubberDuckTargetType targetType,
        UUID targetId,                                     // CONCEPT이 아니면 필수
        @Size(max = 120) @Pattern(regexp = "^[A-Z][A-Z0-9_]*(\\.[A-Z][A-Z0-9_]*)+$") String conceptKey,  // CONCEPT이면 필수
        @Size(max = 100) String skillCode) {}              // 생략하면 대상에서 유도한다 (§9.5 표, RD-7)

public record RubberDuckStartResponse(
        RubberDuckSessionView session,                     // turns = []
        UUID abandonedSessionId) {}                        // 이번 요청으로 ABANDONED 처리한 이전 세션. 없으면 null
```

```json
{ "targetType": "CODE_READING", "targetId": "e41b7f0c-9d53-4a11-8c2e-7a1f0b9d3c44", "conceptKey": null, "skillCode": "SPRING.MVC_REST" }
```

처리 (한 트랜잭션):
1. 도메인 검사. `targetType = CONCEPT`이면 `conceptKey`가 있어야 하고(`ONE_OF_REQUIRED`, field `conceptKey`) `targetId`는 `null`이어야 한다(`MUTUALLY_EXCLUSIVE`, field `targetId`). 그 외 `targetType`은 반대로 `targetId` 필수·`conceptKey` 금지.
2. 대상 조회(§9.5 표). 본인 것이 아니거나 없으면 400 `VALIDATION_FAILED`, field `targetId`, code `REFERENCE_NOT_FOUND` — 타인 소유와 존재하지 않음을 구분하지 않는다(§1.2.3).
3. **재현 잠금(S4, RE-5)**: `learning.application.RedoLockProvider`(구현은 `today`)로 확인한다. `targetType = CHALLENGE`이면 그 attempt의 `challenge_id`, `targetType = PROJECT_WORK`이면 그 `side_project_id`에 대해 `PLANNED`·`IN_PROGRESS`인 `REDO` 과제가 있으면 409 `AI_ASSIST_LOCKED_FOR_REDO`. 세션을 만들지 않고 이전 `IN_PROGRESS` 세션도 건드리지 않는다. `CODE_READING`·`REVIEW_ITEM`·`CONCEPT`은 잠그지 않는다.
4. `skillCode`가 있으면 활성 skill 검사(`SKILL_CODE_UNKNOWN`), 없으면 §9.5 표대로 유도한다. 유도에 실패하면 `skill_id`를 `null`로 둔다(그 세션은 학습 이벤트를 남기지 않는다 — RD-7).
5. 사용자의 다른 `IN_PROGRESS` 러버덕 세션이 있으면 `ABANDONED`로 바꾸고 flush한다. 그 id가 `abandonedSessionId`다.
6. `rubber_duck_session` INSERT: `status = IN_PROGRESS`, `turn_count = 0`, `started_at = now`, `learning_session_id` = 지금 `IN_PROGRESS`인 학습 세션(없으면 null).
7. partial unique index 위반(동시 시작)은 409 `CONCURRENT_MODIFICATION`.

- **AI를 호출하지 않는다.** 그래서 §1.9.3 예산·차단 검사도 하지 않는다. 첫 검사는 §9.7에서 한다.
- 학습 이벤트를 남기지 않는다.

### 9.7 `POST /rubber-duck/{sessionId}/turns` — 설명 제출 → AI 질문

| 항목 | 값 |
|---|---|
| operationId | `learningSubmitRubberDuckTurn` |
| 인증 / IK | Bearer / IK |
| 요청 | `RubberDuckTurnRequest` |
| 응답 | 201 `RubberDuckTurnResponse` |
| 오류 | 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`, 409 `CONCURRENT_MODIFICATION`, 413 `CONTENT_TOO_LARGE`, 422 `SECRET_DETECTED_BLOCKED`, 429 `AI_*`, 502 `AI_OUTPUT_INVALID`, 502 `AI_REFUSED`, 503 `AI_UNAVAILABLE`, 504 `AI_TIMEOUT` |
| Sprint · 요구사항 | S3 · FR-25, AC-26, RD-1, RD-2, RD-3, RD-6 |

```java
public record RubberDuckTurnRequest(
        @NotBlank String explanation) {}       // 상한은 컨트롤러가 검사한다 → 413 (아래 2번)

public record RubberDuckTurnResponse(
        int turnNo,                            // 이번에 저장한 턴 번호 (1부터)
        String question,                       // AI의 되묻는 질문 (17 §4.10 question)
        boolean suggestHint,                   // RD-3
        int remainingTurns,                    // max-turns − turnNo
        int vagueReferenceCount,               // 이번 설명의 지시어 수 (06 §9.6). 저장하지 않는다
        int vagueReferencePer100Words,         // 100어절당 비율, 정수(내림). 어절이 0이면 0
        AiMeta aiMeta,
        long version) {}                       // 갱신된 세션 version
```

```json
{ "explanation": "OwnerController가 Repository를 직접 주입받는 이유는 이 컨트롤러가 조회만 하고 규칙이 없기 때문입니다." }
```

```json
{ "turnNo": 1, "question": "조회만 한다고 하셨는데, 폼 제출을 처리하는 메서드는 어디에 규칙을 두고 있나요?",
  "suggestHint": false, "remainingTurns": 4,
  "vagueReferenceCount": 2, "vagueReferencePer100Words": 5,
  "aiMeta": { "model": "deepseek-flash", "promptVersion": "rubber.duck@v1", "guardActions": [] }, "version": 1 }
```

처리 순서 (§1.11 순서 그대로):
1. Bean Validation(400).
2. 크기: `explanation`의 길이(UTF-16 code unit) > `devpilot.rubberduck.max-explanation-chars`(2000)이면 413 `CONTENT_TOO_LARGE` (§17). 컨트롤러에서 검사하고 아무것도 저장하지 않는다.
3. `IdempotencyService`(§1.7). 같은 키 재시도는 저장된 201 body를 재생한다 — AI를 다시 부르지 않는다.
4. `SecretMasker`를 `explanation`에 적용한다(§1.11, RD-6). 차단 패턴(private key 블록)이 있으면 422 `SECRET_DETECTED_BLOCKED` — 턴을 저장하지 않고 AI를 호출하지 않는다. 감사 로그 `SECRET_BLOCKED`. 마스킹 개수는 저장·응답하지 않는다.
5. tx1 — 세션 조회. 본인 것이 아니면 404 `RESOURCE_NOT_FOUND`. `status ≠ IN_PROGRESS`면 409 `INVALID_STATE_TRANSITION`. `turn_count ≥ max-turns`면 409 `INVALID_STATE_TRANSITION`(RD-4 — 더 낼 수 없다. 클라이언트는 §9.8 `complete`를 부른다).
6. AI 차단 검사(§1.9.3) → 429 / 503. 저장하지 않는다.
7. `RUBBER_DUCK` 호출(`17-ai-integration.md` §3.11, SYNC · timeout 20s · 재시도 0). 가드는 CodeLeak · Language · `NoAnswerGuard`(`17-ai-integration.md` §6.8)다. 실패·가드 위반은 §1.9.4 "동기, 대체 없음" — **턴을 저장하지 않고 `turn_count`도 늘지 않는다.** 학습자의 설명은 클라이언트가 유지한다.
8. tx2 — 세션 `version`이 tx1과 다르면 409 `CONCURRENT_MODIFICATION`(§1.6). `rubber_duck_turn` INSERT(`turn_no = turn_count + 1`, `user_text` = 마스킹본, `ai_question` = `question`, `learner_stuck` = `RubberDuckPolicy.isDontKnow(설명 원문을 마스킹한 값)` — `06` §9.5 RD-3 규칙(문구 목록 + 길이), AI 출력이 아니다, `ai_call_id`), `turn_count += 1`.
9. `suggestHint` = 이번 턴을 포함한 **마지막 `devpilot.rubberduck.stuck-turns-before-hint`(2)턴의 `learner_stuck`이 모두 true**(RD-3). `turn_count`가 그보다 적으면 항상 `false`.

- **지시어 세기**: `vagueReferenceCount`·`vagueReferencePer100Words`는 **마스킹본 설명에서 응답을 만들 때 세고 저장하지 않는다**(`06` §9.6 — 세는 표현 목록과 어절 기준은 그 절이 기준이다). AI 출력이 아니고 서버의 결정적 계산이며, 규칙 입력이나 레벨 판정에 쓰지 않는다. 7단계가 실패해 턴을 저장하지 않으면 이 응답 자체가 없다. 비율이 `devpilot.rubberduck.vague-reference-warn-per-100`(기본 3) 이상이면 종료 정리(§9.8)의 빈틈에 "용어" 항목 1건이 더해진다.
- `17-ai-integration.md` §4.10의 `targetsGap`은 응답에도 DB에도 넣지 않는다. 다음 턴 프롬프트에도 들어가지 않는다.
- `suggestHint = true`일 때 클라이언트 행동: 대상이 `CHALLENGE`면 그 attempt의 Hint Ladder(`POST /challenge-attempts/{attemptId}/hints`, §10.8)로 넘어간다 — `HINT_DISCLOSED` 이벤트는 그 경로에서 정상 기록된다. 그 외 대상은 hint endpoint가 없으므로 §9.8 `complete`로 마무리하도록 안내한다. **러버덕 전용 hint endpoint는 두지 않는다.**
- 학습 이벤트를 남기지 않는다.

### 9.8 `POST /rubber-duck/{sessionId}/complete` — 종료 · 정리

| 항목 | 값 |
|---|---|
| operationId | `learningCompleteRubberDuck` |
| 인증 / IK | Bearer / IK |
| 요청 | body 없음 |
| 응답 | 200 `RubberDuckCompleteResponse` |
| 오류 | 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`, 409 `CONCURRENT_MODIFICATION` |
| Sprint · 요구사항 | S3 · FR-25, AC-26, RD-4, RD-5 |

```java
public record RubberDuckCompleteResponse(
        UUID sessionId,
        RubberDuckStatus status,               // 턴이 1개 이상이면 COMPLETED, 0개면 ABANDONED
        List<RubberDuckGapView> gaps,          // 정리 실패·턴 0개면 []
        List<String> confirmed,                // 〃
        String overallNote,                    // 〃 null
        int createdReviewItemCount,            // 이번 요청으로 새로 만든 복습 카드 수 (due만 당긴 것은 세지 않는다)
        AsyncFailureCode summarySkippedReason, // 정리 AI가 실패했을 때만, 그 외 null
        AiMeta aiMeta,                         // RUBBER_DUCK_SUMMARY 호출. 호출하지 않았으면 null
        long version) {}
```

```json
{
  "sessionId": "8b1e…", "status": "COMPLETED",
  "gaps": [ { "conceptKey": "SPRING.TRANSACTION", "whatWasMissed": "읽기 전용 조회에도 트랜잭션 경계가 필요한 이유를 설명하지 못했다.",
              "whyItMatters": "경계를 모르면 지연 로딩과 커넥션 반환 시점을 예측할 수 없다.",
              "reviewQuestion": "조회만 하는 메서드에 트랜잭션을 여는 이유를 설명해 보세요.",
              "reviewItemId": "c92a…" } ],
  "confirmed": ["컨트롤러에 규칙이 없다는 점을 정확히 짚었다"],
  "overallNote": "계층의 목적은 잡았고, 트랜잭션 경계가 다음 차례다.",
  "createdReviewItemCount": 1, "summarySkippedReason": null,
  "aiMeta": { "model": "deepseek-flash", "promptVersion": "rubber.duck.summary@v1", "guardActions": [] }, "version": 4
}
```

처리:
1. `IdempotencyService`(§1.7). 재생 시 AI를 다시 부르지 않는다.
2. tx1 — 세션 조회(본인 아니면 404). `status ≠ IN_PROGRESS`면 409 `INVALID_STATE_TRANSITION`(정리는 세션당 1회다, RD-4).
3. **`turn_count = 0`이면 AI를 호출하지 않는다.** `status = ABANDONED`, `completed_at = now`로 끝낸다. 복습 카드·학습 이벤트 없음, `summary = null`, `summarySkippedReason = null`, `aiMeta = null`. 200이다(오류가 아니다).
4. AI 차단 검사(§1.9.3). 차단이면 **오류를 내지 않고** 6번의 실패 경로로 간다(§1.9.4 "동기, 대체 있음").
5. `RUBBER_DUCK_SUMMARY` 호출(`17-ai-integration.md` §3.12, SYNC · timeout 30s · 재시도 0). 가드는 CodeLeak · SkillCode · Language · `NoAnswerGuard`다.
6. tx2 — 세션 `version`이 tx1과 다르면 409 `CONCURRENT_MODIFICATION`.
   - **성공**: `summary_json` 저장, `status = COMPLETED`, `completed_at = now`. `gaps[]`마다 복습 카드를 만든다 — `review_item`(`source_type = RUBBER_DUCK`, `source_id` = 세션 id, `origin = AI_GENERATED`, `skill_id` = 세션 skill — **세션 skill이 null이면** `conceptKey`와 `.` 경계로 접두사가 가장 길게 일치하는 활성 `skill.code`, 그것도 없으면 **그 gap은 카드를 만들지 않고** `reviewItemId = null`, `concept_key` = gap의 `conceptKey`, `review_type = EXPLAIN`, `prompt` = `reviewQuestion`, `expected_answer` = 두 줄 목록(`- {whatWasMissed}`, `- {whyItMatters}`)(답이 아니라 답이 다뤄야 할 것 — 러버덕은 답을 만들지 않는다, NA-4), `rubric_json` = `[{"id": "G1", "criterion": whatWasMissed}]`), 첫 due는 `06-learning-engine-rules.md` §6.3의 "challenge 실패 / coach finding / 수동 생성" 행을 따른다. **같은 `concept_key`의 활성 카드가 이미 있으면 새로 만들지 않고 due를 당긴다**(같은 절). `createdReviewItemCount`는 새로 만든 수만 센다.
   - `summary_json`에는 가드가 gap을 제거하기 전의 수 `rawGapCount`를 함께 저장한다(`04-domain-model-and-db.md` §5.9). `skill_id`가 있으면 `RUBBER_DUCK_COMPLETED` 이벤트를 남긴다(`04-domain-model-and-db.md` §6, payload `{ sessionId, turns, gapCount, targetType, hintDisclosed }` — `gapCount` = `rawGapCount`). `skill_id`가 `null`이면 남기지 않는다(RD-7). `rawGapCount`가 0이고 `turn_count ≥ 3`이면 EXPLANATION 축 증거로 쓰인다(RD-5 — coverage 고정 7000, `hintDisclosed = false`일 때 독립, `06-learning-engine-rules.md` §7.2).
   - **실패**(§1.9.4의 사유 전부 — 차단·공급자 오류·timeout·가드 위반): `status = COMPLETED`, `summary_json = null`, `summarySkippedReason` = `AsyncFailureCode`. 복습 카드도 학습 이벤트도 만들지 않는다. **대화 기록은 남는다** — 대화 자체가 학습이므로 세션을 실패로 만들지 않는다.
7. `READ_CODE` task의 완료 조건은 이 endpoint가 `COMPLETED`를 만든 세션 1개다(RC-1, §8.4). `targetType = CODE_READING`인 세션이 `COMPLETED`가 되면(정리 AI 실패로 `summarySkippedReason`이 있어도) 그 조건이 충족된다. **이 endpoint는 대상 `learning_task`의 상태를 바꾸지 않는다** — 과제 완료는 클라이언트가 Today 완료 기록에서 `PATCH /today/tasks/{taskId}` `{status: COMPLETED}`(선택 `readingFeedback`)로 한다(§8.4, `02` SCR-TODAY 완료 시트).

- 재시도 endpoint는 없다. 정리를 놓친 세션은 그대로 둔다(대화는 남아 있다).

### 9.9 `POST /rubber-duck/{sessionId}/abandon` — 중단

| 항목 | 값 |
|---|---|
| operationId | `learningAbandonRubberDuck` |
| 인증 / IK | Bearer / — (상태 전이가 자연히 멱등, §1.7) |
| 요청 | body 없음 |
| 응답 | 200 `RubberDuckSessionView` (`turns` 포함) |
| 오류 | 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`(IN_PROGRESS 아님) |
| Sprint · 요구사항 | S3 · FR-25, AC-26 |

- `status = ABANDONED`, `completed_at = now`. `summary_json`은 `null`로 둔다.
- **AI를 호출하지 않는다.** 복습 카드도 학습 이벤트도 만들지 않는다. 턴 기록은 남는다.
- IK를 받아도 무시하고 idempotency record를 만들지 않는다(§1.7).

### 9.10 `GET /rubber-duck/{sessionId}` — 세션 조회

| 항목 | 값 |
|---|---|
| operationId | `learningGetRubberDuck` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `RubberDuckSessionView` |
| 오류 | 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S3 · FR-25, AC-26 |

- `turns`는 `turn_no` ASC 전체다. 페이징하지 않는다(최대 `max-turns`개).
- `userText`는 마스킹본이다. 원문은 저장하지 않으므로 복원할 수 없다.
- AI가 불가한 상태에서도 조회는 된다(`17-ai-integration.md` §3.10).
- 목록 endpoint는 두지 않는다. 진행 중 세션은 시작 응답(§9.6)과 클라이언트 로컬 상태로 추적하고, 지난 세션은 `GET /learning-sessions`(§9.4)와 복습 카드로 이어진다.
- **`summarySkippedReason`은 이 응답에서 항상 `null`이다.** 실패 사유는 저장하지 않으므로(`04` §5.9 `summary_json`은 실패 시 null) §9.8 응답에만 있다. 조회에서는 `status = COMPLETED`인데 `summary`가 없으면 정리를 못 한 세션이다.

---

## 10. Training 모듈

Controller: `ChallengeController`(`/challenges*`), `ChallengeAttemptController`(`/challenge-attempts*`). Service: `ChallengeGenerationService`, `ChallengeValidationService`, `AttemptService`, `SubmissionService`, `ChallengeQueryService`, `HintService`(learning).

접근 규칙: challenge는 `owner_user_id IS NULL`(seed 공용) 또는 `owner_user_id = 본인`일 때만 보인다. 그 외는 404 `RESOURCE_NOT_FOUND`. attempt는 본인 것만 보인다.

### 10.1 공통 view record

```java
public record ChallengeSummaryView(
        UUID id,
        String title,
        int difficulty,
        Integer estimatedMinutes,
        ChallengePurpose purpose,
        ContentOrigin origin,
        boolean isTransfer,
        List<SkillRef> skills,                // code ASC
        LastAttemptView lastAttempt,          // 본인의 가장 최근 attempt. 없으면 null
        Instant createdAt) {}

public record LastAttemptView(UUID attemptId, AttemptStatus status, AttemptOutcome outcome, Instant startedAt) {}

public record ChallengeView(
        UUID id,
        ContentOrigin origin,
        ChallengeStatus status,
        AsyncJobStatus generationStatus,
        AsyncFailureCode failureCode,
        Instant statusUpdatedAt,
        ChallengePurpose purpose,
        boolean isTransfer,
        String title,                         // 아래 "본문 공개" 규칙
        int difficulty,
        Integer estimatedMinutes,
        Integer timeLimitMinutes,             // 시간 제한이 있는 문제만 1~120, 없으면 null (challenge.time_limit_minutes, I-25)
        String scenario,
        String prompt,
        List<String> constraints,
        List<SkillRef> skills,
        List<String> transferTargetSkillCodes,
        boolean answerRevealed,               // 아래 "정답 정보 공개" 규칙
        List<String> expectedConcepts,        // answerRevealed=false면 null
        List<RubricItemView> rubric,          // answerRevealed=false면 null
        List<String> commonMistakes,          // answerRevealed=false면 null
        AiMeta aiMeta,                        // origin = AI_GENERATED일 때
        UUID activeAttemptId,                 // 요청 사용자의 STARTED/SUBMITTED/EVALUATED attempt id (ABANDONED 제외, 최신 1개). 없으면 null
        Instant createdAt) {}

public record RubricItemView(String id, String criterion, int weightBp, RubricAxis axis) {}

public record AttemptView(
        UUID id,
        UUID challengeId,
        String challengeTitle,
        int difficulty,
        ChallengePurpose purpose,
        AttemptStatus status,
        String selfExplanation,
        boolean selfExplanationSkipped,
        Integer vagueReferenceCount,               // selfExplanation이 있을 때만. 응답 시 계산하고 저장하지 않는다 (§9.7과 같은 규칙)
        Integer vagueReferencePer100Words,         // 〃
        Integer elapsedSeconds,                    // 마지막 제출이 보낸 경과 시간. 없으면 null (challenge_attempt.elapsed_seconds)
        int submissionCount,
        int maxSubmissions,                        // devpilot.training.max-submissions-per-attempt
        HintLevel maxHintLevel,
        List<DisclosedHintView> hints,             // level ordinal ASC
        EvaluatedOutcome evaluatedOutcome,         // 최신 COMPLETED submission 기준, 없으면 null
        AttemptOutcome outcome,
        Integer rubricCoverageBp,
        Integer explanationCoverageBp,
        List<SubmissionView> submissions,          // submissionNo ASC
        List<ScheduledReviewView> reviewScheduled, // 06 §8.3으로 생성·갱신된 review item. 없으면 []
        UUID evidenceSourceEventId,                // 최신 CHALLENGE_EVALUATED learning_event id (skill이 여러 개면 첫 skill 행). POST /evidence/drafts의 sourceLearningEventId로 사용. 없으면 null
        Instant startedAt,
        Instant completedAt,
        long version) {}

public record SubmissionView(
        int submissionNo,
        String answerText,
        String code,
        CodeLanguage language,
        AsyncJobStatus evaluationStatus,
        AsyncFailureCode failureCode,
        Instant statusUpdatedAt,
        boolean retryable,                         // §10.10 조건을 만족하면 true
        Instant submittedAt,
        Instant evaluatedAt,
        EvaluationView evaluation,                 // evaluationStatus = COMPLETED일 때만
        AiMeta aiMeta) {}

public record EvaluationView(
        EvaluatedOutcome evaluatedOutcome,
        int rubricCoverageBp,
        Integer explanationCoverageBp,             // EXPLANATION 축 rubric이 없으면 null
        List<RubricResultView> rubric,             // challenge rubric 순서
        List<String> misconceptions,
        String followUpQuestion) {}                // null 가능

public record RubricResultView(
        String id, String criterion, RubricAxis axis, int weightBp, boolean met, String evidenceQuote) {}

public record ScheduledReviewView(UUID reviewItemId, String skillCode, LocalDate dueDate) {}
```

**본문 공개**: `title`, `scenario`, `prompt`, `constraints`, `estimatedMinutes`는 `status ∈ {VALIDATED, RETIRED}`일 때만 값을 채우고 그 외(`DRAFT`, `REJECTED`)는 null이다. `hints_json`은 어떤 view에도 넣지 않는다(hint endpoint로만 공개, HL-6).

**시간 제한**: `timeLimitMinutes`는 콘텐츠(seed challenge)의 값이고 서버는 이 시간을 강제하지 않는다 — 클라이언트가 경과 시간을 보여 주고(`02` 문제 풀이 화면), 사용자가 제출할 때 `elapsedSeconds`를 함께 보낸다(§10.9). 시간 초과라는 상태는 없고 outcome 계산(`06` §8.2)에도 쓰지 않는다.

**정답 정보 공개**: `answerRevealed = true` ⇔ 요청 사용자가 이 challenge에 대해 `evaluated_outcome IS NOT NULL`인 attempt를 하나 이상 가짐(한 번이라도 평가 완료). 이후 재제출·포기와 무관하게 계속 공개한다.

`reviewScheduled` 계산: attempt `outcome`이 `06-learning-engine-rules.md` §8.3 생성 조건을 만족하면 challenge skill마다 같은 절의 `concept_key`로 `review_item`을 찾아 `{reviewItemId, skillCode, dueDate = planDate(due_at)}`를 넣는다.

### 10.2 `GET /challenges` — 풀 수 있는 challenge 목록

| 항목 | 값 |
|---|---|
| operationId | `trainingListChallenges` |
| 인증 / IK | Bearer / — |
| query | `skillId`: UUID, 선택(`challenge_skill` 필터) / `purpose`: `ChallengePurpose`, 선택(없으면 둘 다) / `limit`, `cursor` |
| 대상 | `status = VALIDATED`, 접근 규칙 통과 |
| 정렬 | `createdAt` DESC, `id` DESC |
| 응답 | 200 `CursorPage<ChallengeSummaryView>` |
| 오류 | 400 `UNKNOWN_ENUM_VALUE`, 400 `VALIDATION_FAILED`(`TYPE_MISMATCH`), 400 `INVALID_CURSOR` |
| Sprint · 요구사항 | S3 · FR-09 |

- 없는 `skillId`는 오류가 아니라 빈 목록이다.

### 10.3 `POST /challenges/generate` — AI challenge 생성 요청

| 항목 | 값 |
|---|---|
| operationId | `trainingGenerateChallenge` |
| 인증 / IK | Bearer / IK |
| 요청 | `ChallengeGenerateRequest` |
| 응답 | 202 `AsyncStatusView` (`id` = challengeId, `pollPath` = `/api/v1/challenges/{challengeId}`) |
| 오류 | 400 `VALIDATION_FAILED`(`REFERENCE_NOT_FOUND`), 429 `AI_*`, 503 `AI_UNAVAILABLE` |
| Sprint · 요구사항 | S3 · FR-09, AC-04, AC-12, AC-13, AC-23 |

```java
public record ChallengeGenerateRequest(
        @NotNull UUID skillId,                         // 활성 catalog skill (17 §3.3)
        @NotNull @Min(1) @Max(5) Integer difficulty,
        @NotNull @Min(5) @Max(180) Integer targetMinutes) {}
```

처리:
1. `skillId`가 활성 skill이 아니면 `REFERENCE_NOT_FOUND`(field `skillId`) → AI 차단 검사(§1.9.3). 생성 challenge의 purpose는 항상 `PRACTICE`다(DIAGNOSTIC은 seed만, `17-ai-integration.md` §3.3).
2. 트랜잭션: `challenge` INSERT(`owner_user_id` = 본인, `origin = AI_GENERATED`, `status = DRAFT`, `generation_status = PENDING`, `purpose = PRACTICE`, `difficulty`, `estimated_minutes = targetMinutes`) + `challenge_skill`.
3. 커밋 후 `ChallengeGenerationTask`: `CHALLENGE_GENERATE` → `ChallengeValidationService`(I-09, I-10) → `status = VALIDATED | REJECTED`, `generation_status = COMPLETED`. AI 실패는 `generation_status = FAILED` + `failureCode`(§1.9.4). 재시도 endpoint는 없다.

### 10.4 `GET /challenges/{challengeId}` — challenge 조회·생성 폴링

| 항목 | 값 |
|---|---|
| operationId | `trainingGetChallenge` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `ChallengeView` |
| 오류 | 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S3 · FR-09, AC-04 |

- 생성 폴링 중에는 `generationStatus`를 본다. `COMPLETED`인데 `status = REJECTED`이면 클라이언트는 "문제를 만들지 못했어요"를 보여주고 새로 요청하게 한다(거절 사유는 노출하지 않는다).

### 10.5 `POST /challenges/{challengeId}/attempts` — attempt 시작

| 항목 | 값 |
|---|---|
| operationId | `trainingStartAttempt` |
| 인증 / IK | Bearer / IK |
| 요청 | body 없음 |
| 응답 | 201 `AttemptView` |
| 오류 | 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION` |
| Sprint · 요구사항 | S3 · FR-09, FR-15, AC-04 |

- challenge `status ≠ VALIDATED`이면 409 `INVALID_STATE_TRANSITION`(`04-domain-model-and-db.md` §4.2).
- 같은 challenge에 본인의 활성 attempt(`STARTED`/`SUBMITTED`/`EVALUATED`, 즉 `ChallengeView.activeAttemptId`가 null이 아님)가 있으면 409 `INVALID_STATE_TRANSITION`. 클라이언트는 시작 전에 `GET /challenges/{challengeId}`의 `activeAttemptId`를 확인하고, 값이 있으면 `GET /challenge-attempts/{activeAttemptId}`로 이어서 연다(409를 받은 경우도 같다). `ABANDONED` attempt만 있으면 새 attempt를 허용한다. 평가 완료 attempt를 버리고 새로 풀려면 먼저 `POST /challenge-attempts/{attemptId}/abandon`(§10.11)을 호출한다.
- 저장: `status = STARTED`, `max_hint_level = SELF_EXPLAIN`. `CHALLENGE_STARTED` 이벤트를 challenge skill마다 기록한다.

### 10.6 `GET /challenge-attempts/{attemptId}` — attempt 상세·평가 폴링

| 항목 | 값 |
|---|---|
| operationId | `trainingGetAttempt` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `AttemptView` |
| 오류 | 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S3 · FR-09, AC-04 |

```json
{
  "id": "e5a0…",
  "challengeId": "7d3c…",
  "challengeTitle": "설정 로더의 예외 처리",
  "difficulty": 2,
  "purpose": "PRACTICE",
  "status": "EVALUATED",
  "selfExplanation": "IOException을 삼키지 말고 도메인 예외로 바꿔 던져야 할 것 같다.",
  "selfExplanationSkipped": false,
  "submissionCount": 1,
  "maxSubmissions": 5,
  "maxHintLevel": "CONCEPT_HINT",
  "hints": [ { "level": "CONCEPT_HINT", "content": "예외 변환(exception translation)과 원인 보존을 떠올려 보세요.",
               "contentOrigin": "SEED", "disclosedAt": "2026-11-02T12:04:10Z" } ],
  "evaluatedOutcome": "PARTIAL",
  "outcome": "PARTIAL",
  "rubricCoverageBp": 6000,
  "explanationCoverageBp": 0,
  "submissions": [
    { "submissionNo": 1, "answerText": "원인 예외를 보존하도록 바꿨다.", "code": "try { … } catch (IOException e) { throw new ConfigLoadException(path, e); }",
      "language": "JAVA", "evaluationStatus": "COMPLETED", "failureCode": null, "statusUpdatedAt": "2026-11-02T12:10:31Z",
      "retryable": false, "submittedAt": "2026-11-02T12:09:40Z", "evaluatedAt": "2026-11-02T12:10:31Z",
      "evaluation": {
        "evaluatedOutcome": "PARTIAL", "rubricCoverageBp": 6000, "explanationCoverageBp": 0,
        "rubric": [
          { "id": "R1", "criterion": "원인 예외(cause)를 보존한다", "axis": "IMPLEMENTATION", "weightBp": 4000, "met": true, "evidenceQuote": "throw new ConfigLoadException(path, e)" },
          { "id": "R2", "criterion": "복구 가능한 계층에서만 처리하는 이유를 설명한다", "axis": "EXPLANATION", "weightBp": 4000, "met": false, "evidenceQuote": null },
          { "id": "R3", "criterion": "null 반환 대신 의미 있는 결과/예외를 사용한다", "axis": "IMPLEMENTATION", "weightBp": 2000, "met": true, "evidenceQuote": "throw new" }
        ],
        "misconceptions": [],
        "followUpQuestion": "이 예외를 호출자가 복구할 수 없다면 어떤 타입이 더 적절할까요?" },
      "aiMeta": { "model": "deepseek-flash", "promptVersion": "challenge.evaluate@v1", "guardActions": [] } }
  ],
  "reviewScheduled": [ { "reviewItemId": "r9…", "skillCode": "JAVA.EXCEPTION", "dueDate": "2026-11-03" } ],
  "evidenceSourceEventId": "4c1e…",
  "startedAt": "2026-11-02T12:01:00Z",
  "completedAt": "2026-11-02T12:10:31Z",
  "version": 4
}
```

- 평가 폴링은 최신 submission의 `evaluationStatus`를 본다(§1.8).

### 10.7 `POST /challenge-attempts/{attemptId}/self-explanation` — 자기 설명 제출·건너뛰기

| 항목 | 값 |
|---|---|
| operationId | `trainingSubmitSelfExplanation` |
| 인증 / IK | Bearer / IK |
| 요청 | `SelfExplanationRequest` |
| 응답 | 200 `AttemptView` |
| 오류 | 400 `VALIDATION_FAILED`(`ONE_OF_REQUIRED`, `MUTUALLY_EXCLUSIVE`), 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`, 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S3 · FR-09, FR-10, AC-16 |

```java
public record SelfExplanationRequest(
        @Size(max = 5000) String text,
        boolean skipped) {}
```

- `text`(공백 아닌 값)와 `skipped = true` 중 **정확히 하나**. 둘 다 없으면 `ONE_OF_REQUIRED`(field `text`), 둘 다 있으면 `MUTUALLY_EXCLUSIVE`(field `skipped`).
- `text`는 attempt 조회 전에 마스킹한다(§1.11, 차단 시 422). 저장하는 값은 마스킹본이다.
- attempt `status = STARTED`이고 아직 기록이 없을 때만 허용한다(`self_explanation IS NULL AND self_explanation_skipped = false`). 그 외 409 `INVALID_STATE_TRANSITION`. 한 번 기록하면 바꾸지 않는다.
- 이벤트: `SELF_EXPLANATION_SUBMITTED`(`length` = text의 code point 수) 또는 `SELF_EXPLANATION_SKIPPED`, challenge skill마다(`04-domain-model-and-db.md` §6).

### 10.8 `POST /challenge-attempts/{attemptId}/hints` — Hint Ladder

| 항목 | 값 |
|---|---|
| operationId | `trainingRequestHint` |
| 인증 / IK | Bearer / IK |
| 요청 | `HintRequest` (§2.6) |
| 응답 | 200 `HintView` |
| 오류 | 400 `VALIDATION_FAILED`(`VALUE_NOT_ALLOWED`), 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`, 409 `AI_ASSIST_LOCKED_FOR_REDO`(S4, HL-9), 409 `SELF_EXPLANATION_REQUIRED`(HL-2), 409 `HINT_CONFIRMATION_REQUIRED`(HL-4), 409 `FULL_EXAMPLE_NOT_ALLOWED`(HL-5), 409 `CONCURRENT_MODIFICATION`, 429 `AI_*`, 502 `AI_OUTPUT_INVALID`(HL-8 포함), 502 `AI_REFUSED`, 503 `AI_UNAVAILABLE`, 504 `AI_TIMEOUT` |
| Sprint · 요구사항 | S3 (재현 잠금 S4) · FR-10, FR-28, AC-16, AC-12, AC-23, AC-31 |

```json
{ "requestedLevel": "PSEUDOCODE", "acknowledgeEvidenceImpact": true, "giveUp": false, "skipSelfExplanation": null }
```

```json
{
  "level": "PSEUDOCODE",
  "content": "1. 파일을 여는 부분을 try-with-resources로 감싼다\n2. IOException을 잡으면 경로를 담은 도메인 예외를 만든다\n3. …",
  "contentOrigin": "AI_GENERATED",
  "maxHintLevel": "PSEUDOCODE",
  "skippedLevels": ["DIRECTION"],
  "aiMeta": { "model": "deepseek-flash", "promptVersion": "hint.generate@v1", "guardActions": [] }
}
```

검사·처리 순서 (`06-learning-engine-rules.md` §9.1, test vector `06-learning-engine-rules.md` §9.2):
1. `requestedLevel = SELF_EXPLAIN` 또는 `skipSelfExplanation = true` → 400 `VALIDATION_FAILED`(`VALUE_NOT_ALLOWED`).
2. attempt 조회(404) → `status = ABANDONED`이면 409 `INVALID_STATE_TRANSITION`.
3. **HL-9 재현 잠금(S4)**: `learning.application.RedoLockProvider.challengeLocked(userId, attempt.challengeId)`가 참이면 409 `AI_ASSIST_LOCKED_FOR_REDO`(`06` §9.1 HL-9). HL-2보다 **먼저** 검사한다 — 자기설명을 요구하기 전에 막는다. `hint_disclosure`·`HINT_DISCLOSED`·AI 호출 없음.
4. HL-2: 자기 설명 기록(`self_explanation` 또는 `self_explanation_skipped`)이 없으면 409 `SELF_EXPLANATION_REQUIRED`. **예외**: 이 attempt를 대상(`targetType = CHALLENGE`)으로 한 러버덕 세션에 턴이 1개 이상 있으면 자기 설명을 한 것으로 본다(`06-learning-engine-rules.md` §9.5 RD-3 연결).
5. HL-1: `requestedLevel ≤ max_hint_level`이면 `hint_disclosure` 중 요청 단계 이하에서 가장 높은 단계의 내용을 반환한다. 요청 단계 이하 저장 내용이 없으면 저장된 가장 낮은 단계의 내용을 반환한다. AI 호출·저장·이벤트 없음, `skippedLevels = []`.
6. HL-4: `requestedLevel ≥ PSEUDOCODE`이고 `acknowledgeEvidenceImpact = false` → 409 `HINT_CONFIRMATION_REQUIRED`.
7. HL-5: `requestedLevel = FULL_EXAMPLE`이고 `submission_count = 0`이고 `giveUp = false` → 409 `FULL_EXAMPLE_NOT_ALLOWED`.
8. HL-6 내용 출처: 요청 단계가 `QUESTION_ONLY`/`CONCEPT_HINT`/`DIRECTION`이고 `challenge.hints_json`에 그 키가 있으면 그 내용(`contentOrigin` = seed challenge면 `SEED`, 그 외 `PREGENERATED`). 아니면 AI: §1.9.3 차단 검사 → tx1 종료 → `HINT_GENERATE`(트랜잭션 밖, 20초) → 출력 가드. 동기 operation이므로 가드 위반(HL-8 포함)은 재시도 없이 502 `AI_OUTPUT_INVALID`(`03-system-architecture.md` §5.3).
9. 저장 트랜잭션: attempt `version`이 2단계 조회 값과 다르면 409 `CONCURRENT_MODIFICATION`(`03-system-architecture.md` §5.3). `hint_disclosure` INSERT(I-11 unique 위반도 409 `CONCURRENT_MODIFICATION`), `max_hint_level = requestedLevel`, `HINT_DISCLOSED` 이벤트를 challenge skill마다 기록(HL-3, HL-7). `skippedLevels` = 이전 max와 요청 단계 사이(양 끝 제외)의 단계.

### 10.9 `POST /challenge-attempts/{attemptId}/submissions` — 답안 제출 (비동기 평가)

| 항목 | 값 |
|---|---|
| operationId | `trainingSubmitAnswer` |
| 인증 / IK | Bearer / IK |
| 요청 | `SubmissionRequest` |
| 응답 | 202 `AsyncStatusView` (`id` = attemptId, `submissionNo`, `pollPath` = `/api/v1/challenge-attempts/{attemptId}`) |
| 오류 | 400 `VALIDATION_FAILED`(`ONE_OF_REQUIRED`, `LANGUAGE_REQUIRED`), 413 `CONTENT_TOO_LARGE`, 422 `SECRET_DETECTED_BLOCKED`, 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`, 409 `SUBMISSION_LIMIT_REACHED`, 409 `EVALUATION_IN_PROGRESS`, 409 `SELF_EXPLANATION_REQUIRED`, 429 `AI_*`, 503 `AI_UNAVAILABLE` |
| Sprint · 요구사항 | S3 · FR-09, AC-04, AC-23 |

```java
public record SubmissionRequest(
        @Size(max = 5000) String answerText,
        String code,                    // UTF-8 20,000 byte 이하 (초과 시 413 CONTENT_TOO_LARGE, 컨트롤러 검사)
        CodeLanguage language,
        @Min(0) @Max(86400) Integer elapsedSeconds) {}   // 선택. 문제를 푸는 데 걸린 시간 (§10.1 시간 제한)
```

```json
{ "answerText": "원인 예외를 보존하고, 호출자가 복구할 수 없으니 unchecked 예외로 바꿨다.",
  "code": "public Config load(Path path) {\n  try { … } catch (IOException e) {\n    throw new ConfigLoadException(path, e);\n  }\n}",
  "language": "JAVA", "elapsedSeconds": 1520 }
```

검사 순서:
1. Bean Validation(400) → `code` 크기 413 `CONTENT_TOO_LARGE`(컨트롤러, 원문 기준).
2. `IdempotencyService`(§1.7) → application service 진입: 형태 검사(`answerText`·`code`가 모두 null이거나 공백이면 `ONE_OF_REQUIRED`(field `answerText`), `code`가 공백이 아닌데 `language = null`이면 `LANGUAGE_REQUIRED`) → **`answerText`·`code` 마스킹**(§1.11, 차단 시 422 — 리소스 조회·상태 검사보다 먼저다) → attempt 조회(404) → `status = ABANDONED`이면 409 `INVALID_STATE_TRANSITION`.
3. `submission_count ≥ devpilot.training.max-submissions-per-attempt`(5) → 409 `SUBMISSION_LIMIT_REACHED`.
4. 최신 submission이 `PENDING`/`RUNNING` → 409 `EVALUATION_IN_PROGRESS`.
5. 최신 submission이 `FAILED` → 409 `INVALID_STATE_TRANSITION` (재평가는 §10.10. `SUBMITTED → SUBMITTED`는 전이표에 없다, `04-domain-model-and-db.md` §4.2).
6. 자기 설명 기록 없음 → 409 `SELF_EXPLANATION_REQUIRED` (`04-domain-model-and-db.md` §4.2 `STARTED → SUBMITTED` 조건).
7. AI 차단 검사(§1.9.3).
8. 트랜잭션: `challenge_submission` INSERT(마스킹본 `answer_text`/`code`, `submission_no = submission_count + 1`, `evaluation_status = PENDING`), attempt `status = SUBMITTED`, `submission_count + 1`, `elapsedSeconds`가 있으면 `challenge_attempt.elapsed_seconds`에 저장(재제출이면 덮어쓴다 — 마지막 제출 기준, I-25), `CHALLENGE_SUBMITTED` 이벤트(challenge skill마다). `elapsedSeconds`는 학습 이벤트 payload에 넣지 않고 규칙 입력도 아니다.
9. 커밋 후 `SubmissionEvaluationTask`: `CHALLENGE_EVALUATE` → rubric `met` 저장(`04-domain-model-and-db.md` §5.3) → coverage·evaluatedOutcome(`06-learning-engine-rules.md` §8.1, 서버 계산) → attempt `EVALUATED`, outcome(`06-learning-engine-rules.md` §8.2) → `CHALLENGE_EVALUATED` 이벤트 → purpose `DIAGNOSTIC`이면 `DIAGNOSTIC_PASSED/FAILED`(`06-learning-engine-rules.md` §7.4) → review item(`06-learning-engine-rules.md` §8.3). 실패 시 submission `FAILED` + `failureCode`, attempt는 `SUBMITTED` 유지.

### 10.10 `POST /challenge-attempts/{attemptId}/submissions/{submissionNo}/retry` — 평가 재시도

| 항목 | 값 |
|---|---|
| operationId | `trainingRetryEvaluation` |
| 인증 / IK | Bearer / IK |
| path | `submissionNo`: int ≥ 1 |
| 요청 | body 없음 |
| 응답 | 202 `AsyncStatusView` (§10.9와 같은 형태) |
| 오류 | 404 `RESOURCE_NOT_FOUND`, 409 `AI_TASK_NOT_RETRYABLE`, 429 `AI_*`, 503 `AI_UNAVAILABLE` |
| Sprint · 요구사항 | S3 · FR-09, AC-12 |

- 재시도 가능 조건(= `SubmissionView.retryable`): submission `evaluationStatus = FAILED`, 그 submission이 attempt의 최신 submission, attempt `status ≠ ABANDONED`. 하나라도 아니면 409 `AI_TASK_NOT_RETRYABLE`.
- AI 차단 검사 → `evaluation_status = PENDING`, `failure_code = null`, `status_updated_at = now` → 커밋 후 §10.9 9단계와 같다. `submission_count`는 늘지 않는다.

### 10.11 `POST /challenge-attempts/{attemptId}/abandon` — 포기

| 항목 | 값 |
|---|---|
| operationId | `trainingAbandonAttempt` |
| 인증 / IK | Bearer / — (상태 전이가 멱등, §1.7) |
| 요청 | body 없음 |
| 응답 | 200 `AttemptView` |
| 오류 | 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`(이미 ABANDONED), 409 `EVALUATION_IN_PROGRESS`(최신 submission PENDING/RUNNING) |
| Sprint · 요구사항 | S3 · FR-09 |

- `STARTED/SUBMITTED/EVALUATED → ABANDONED`, `completed_at = now`. outcome은 `04-domain-model-and-db.md` §4.2와 `06-learning-engine-rules.md` §8.2를 따른다: `COMPLETED` submission이 없으면 `ABANDONED`, 있으면 마지막 outcome 유지.
- 이벤트는 기록하지 않는다.

---

## 11. Review 모듈 (Memory)

Controller: `ReviewController`(`/reviews*`), `ReviewItemController`(`/review-items*`). Service: `ReviewService`, `ReviewItemService`, `ReviewQueryService`, 도메인 `FinalRatingPolicy`, `RuleBasedV1Scheduler`, `DueReviewSelector`.

복습 hint는 AI 없이 클라이언트가 처리한다. due 응답에 `expectedAnswer`, `rubric`이 들어 있고 클라이언트가 숨긴다. "힌트 보기"는 rubric 첫 항목 공개(`CONCEPT_HINT`), "정답 먼저 보기"는 `FULL_EXAMPLE`이다(`06-learning-engine-rules.md` §6.1). reveal API는 없다.

### 11.1 공통 view record

```java
public record ReviewRubricItemView(String id, String criterion) {}

public record ReviewItemView(
        UUID id,
        SkillRef skill,
        ContentOrigin origin,
        ReviewItemSourceType sourceType,
        UUID sourceId,                         // null 가능
        String conceptKey,
        ReviewType reviewType,
        String prompt,
        String expectedAnswer,
        List<ReviewRubricItemView> rubric,
        Instant dueAt,
        LocalDate dueDate,                     // planDate(dueAt)
        int intervalDays,
        int consecutiveSuccesses,
        int consecutiveFailures,
        int reviewCount,
        ReviewRating lastResult,               // null 가능
        Instant lastReviewedAt,
        VariantStatus variantStatus,
        ReviewItemStatus status,
        Instant createdAt,
        long version) {}
```

### 11.2 `GET /reviews/due` — 오늘 복습 카드

| 항목 | 값 |
|---|---|
| operationId | `reviewGetDue` |
| 인증 / IK | Bearer / — |
| query | `limit`: int, 선택, 1~100. 실제 반환 수 = `min(limit, cap)`. 생략하면 `cap` |
| 응답 | 200 `DueReviewsResponse` |
| 오류 | 400 `VALIDATION_FAILED` |
| Sprint · 요구사항 | S2 · FR-11, FR-21, AC-05, AC-10 |

```java
public record DueReviewsResponse(
        LocalDate planDate,
        int cap,                         // comebackMode ? comeback-max-per-day(10) : max-per-day(20)
        boolean comebackMode,            // 06 §5.5
        int totalDueCount,               // cap 적용 전 due 수
        List<DueReviewItemView> items) {}

public record DueReviewItemView(
        UUID reviewItemId,
        String skillCode,
        String skillName,
        ReviewType reviewType,
        boolean wasVariant,              // variant_status = READY → variant 문항 출제 (REVIEW_VARIANT는 Later, 그 전까지 항상 false)
        String prompt,                   // wasVariant면 variant_prompt
        String expectedAnswer,           // wasVariant면 variant_expected_answer
        List<ReviewRubricItemView> rubric, // wasVariant면 variant_rubric_json
        LocalDate dueDate,               // planDate(due_at)
        int overdueDays) {}              // max(0, daysBetween(dueDate, today))
```

```json
{
  "planDate": "2026-10-12",
  "cap": 20,
  "comebackMode": false,
  "totalDueCount": 3,
  "items": [
    { "reviewItemId": "r1…", "skillCode": "SPRING.TRANSACTION", "skillName": "Spring Transaction", "reviewType": "RECALL",
      "wasVariant": false,
      "prompt": "같은 클래스 안에서 @Transactional 메서드를 this로 호출하면 트랜잭션이 적용되지 않을 수 있다. 이유는?",
      "expectedAnswer": "Spring의 선언적 트랜잭션은 프록시 기반이다. 내부 호출은 프록시를 거치지 않아 advice가 적용되지 않는다.",
      "rubric": [ { "id": "R1", "criterion": "프록시 기반 AOP 언급" },
                  { "id": "R2", "criterion": "내부 호출은 프록시를 우회한다는 점" },
                  { "id": "R3", "criterion": "해결 방법 1개 이상 (빈 분리 등)" } ],
      "dueDate": "2026-10-10", "overdueDays": 2 }
  ]
}
```

- 대상·정렬·상한: `06-learning-engine-rules.md` §6.5. 대상 경계는 `due_at < planDayStart(today + 1)`이다(내일 plan-day 시작 시각에 due인 항목은 제외).
- 조회는 아무것도 저장하지 않는다(variant 출제 여부도 답변 시점에 확인, §11.3).

### 11.3 `POST /reviews/{reviewItemId}/answer` — 복습 답변

| 항목 | 값 |
|---|---|
| operationId | `reviewAnswer` |
| 인증 / IK | Bearer / IK |
| 요청 | `ReviewAnswerRequest` |
| 응답 | 200 `ReviewAnswerResponse` |
| 오류 | 400 `VALIDATION_FAILED`(`VALUE_NOT_ALLOWED`), 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`, 409 `CONCURRENT_MODIFICATION`, 422 `SECRET_DETECTED_BLOCKED`. **AI 오류(429/502/503/504)는 반환하지 않는다** |
| Sprint · 요구사항 | S2 (`evaluate=true` 처리는 S3. S2에서는 `evaluationSkippedReason = AI_UNAVAILABLE`) · FR-11, AC-05, AC-09, AC-10, AC-12 |

```java
public record ReviewAnswerRequest(
        @Size(max = 5000) String answerText,
        @NotNull ReviewRating selfRating,
        @NotNull HintLevel hintLevel,                         // SELF_EXPLAIN | CONCEPT_HINT | FULL_EXAMPLE 만 허용
        @NotNull @Min(0) @Max(86400) Integer responseSeconds,
        Boolean wasVariant,                                   // due 응답의 wasVariant 그대로. 생략하거나 null이면 false (§1.1)
        Boolean evaluate) {}                                  // 생략하거나 null이면 false

public record ReviewAnswerResponse(
        UUID reviewAnswerId,
        ReviewRating finalRating,
        List<RatingAdjustment> adjustedBy,           // 실제로 등급을 낮춘 규칙만 (06 §6.1)
        EvaluatedOutcome evaluatedOutcome,
        Integer rubricCoverageBp,                    // 평가 성공 시만
        List<ReviewRubricResultView> rubricResults,  // 평가 성공 시만, 저장하지 않음. 그 외 null
        String evaluationFeedback,                   // REVIEW_EVALUATE 출력의 feedback 문장. 평가 성공 시만, 저장하지 않음. 그 외 null
        int intervalBefore,
        int intervalAfter,
        LocalDate nextDueDate,                       // planDate(새 due_at)
        AsyncFailureCode evaluationSkippedReason,    // AI 평가 대상인데 평가하지 못한 경우만 (§1.9.4). 대상이 아니면 null
        ReviewItemStatus status,                     // 처리 후 item 상태
        boolean leechDetected,                       // 이번 답변으로 SUSPENDED 되었으면 true
        AiMeta aiMeta) {}

public record ReviewRubricResultView(String id, String criterion, boolean met) {}
```

```json
{ "answerText": "프록시를 거치지 않아서 트랜잭션 advice가 안 걸린다.", "selfRating": "GOOD",
  "hintLevel": "CONCEPT_HINT", "responseSeconds": 74, "wasVariant": false, "evaluate": true }
```

```json
{
  "reviewAnswerId": "ra1…",
  "finalRating": "HARD",
  "adjustedBy": ["EVALUATED_PARTIAL"],
  "evaluatedOutcome": "PARTIAL",
  "rubricCoverageBp": 6667,
  "rubricResults": [ { "id": "R1", "criterion": "프록시 기반 AOP 언급", "met": true },
                     { "id": "R2", "criterion": "내부 호출은 프록시를 우회한다는 점", "met": true },
                     { "id": "R3", "criterion": "해결 방법 1개 이상 (빈 분리 등)", "met": false } ],
  "evaluationFeedback": "프록시 우회는 정확해요. 내부 호출 문제를 피하는 방법도 하나 적어 보세요.",
  "intervalBefore": 2,
  "intervalAfter": 2,
  "nextDueDate": "2026-10-14",
  "evaluationSkippedReason": null,
  "status": "ACTIVE",
  "leechDetected": false,
  "aiMeta": { "model": "deepseek-flash", "promptVersion": "review.evaluate@v1", "guardActions": [] }
}
```

검사·처리 순서 (`03-system-architecture.md` §5.2, §5.3):
1. Bean Validation → `hintLevel ∉ {SELF_EXPLAIN, CONCEPT_HINT, FULL_EXAMPLE}`이면 `VALUE_NOT_ALLOWED` → idempotency 후 `answerText` 마스킹(§1.11, 차단 시 422).
2. tx1(read-only): item 조회(404) → `status ≠ ACTIVE`이면 409 `INVALID_STATE_TRANSITION` → `due_at ≥ planDayStart(today + 1)`(아직 due 아님, `06-learning-engine-rules.md` §6.5의 경계)이면 409 `INVALID_STATE_TRANSITION` → `wasVariant ≠ (variant_status = READY)`이면 409 `CONCURRENT_MODIFICATION`. item `version`과 출제 문항(prompt/expectedAnswer/rubric)을 기억한다.
3. AI 평가 대상 = `evaluate = true` **그리고** `answerText`가 공백이 아님 **그리고** 출제 rubric이 1개 이상(`17-ai-integration.md` §3.7). 대상이 아니면 AI를 호출하지 않고 `evaluatedOutcome = NOT_EVALUATED`, `evaluationSkippedReason = null`, `aiMeta = null`이다. 대상이면: §1.9.3 차단 시 평가 생략(`evaluationSkippedReason`, §1.9.4). 아니면 트랜잭션 밖에서 `REVIEW_EVALUATE`(20초, 재시도 0, 가드 위반도 재시도 없음). 성공 시 rubric `met` → `rubricCoverageBp`·`evaluatedOutcome`(`06-learning-engine-rules.md` §8.1, 복습 rubric 균등 배분). 실패 시 `NOT_EVALUATED` + skip reason.
4. 평가 성공 시 `evaluationFeedback`(출력의 `feedback`)과 `rubricResults`는 응답에만 넣고 저장하지 않는다(`17-ai-integration.md` §3.7). skip reason 매핑은 §1.9.4다(동시 실행 한도·공급자 429 → `AI_RATE_LIMITED`, 월·일일 한도 → `AI_BUDGET_EXCEEDED`).
5. tx2: item `version`이 2단계와 다르면 409 `CONCURRENT_MODIFICATION`. `FinalRatingPolicy`(`06-learning-engine-rules.md` §6.1) → `RuleBasedV1Scheduler`(`06-learning-engine-rules.md` §6.2, horizon은 `06-learning-engine-rules.md` §3.1) → `review_answer` INSERT(`plan_date` = 오늘, `presented_prompt`, `was_variant`, `ai_call_id`) → item 갱신 → `REVIEW_ANSWERED` 이벤트(skill 갱신 동기 실행) → leech·variant 처리(`06-learning-engine-rules.md` §6.4, `LEECH_DETECTED` 이벤트, variant 요청 시 커밋 후 `REVIEW_VARIANT` 비동기).

### 11.4 `GET /review-items` — 복습 카드 관리 목록

| 항목 | 값 |
|---|---|
| operationId | `reviewListItems` |
| 인증 / IK | Bearer / — |
| query | `skillId`: UUID, 선택 / `status`: `ReviewItemStatus`, 선택 / `limit`, `cursor` |
| 정렬 | `dueAt` ASC, `id` ASC |
| 응답 | 200 `CursorPage<ReviewItemView>` |
| 오류 | 400 `UNKNOWN_ENUM_VALUE`, 400 `VALIDATION_FAILED`(`TYPE_MISMATCH`), 400 `INVALID_CURSOR` |
| Sprint · 요구사항 | S3 (2026-09-18 범위 축소: 수동 카드 관리는 S2에서 제외) · FR-11 |

### 11.5 `POST /review-items` — 수동 카드 생성

| 항목 | 값 |
|---|---|
| operationId | `reviewCreateItem` |
| 인증 / IK | Bearer / IK |
| 요청 | `ReviewItemCreateRequest` |
| 응답 | 201 `ReviewItemView` (새로 생성) / 200 `ReviewItemView` (같은 `conceptKey`가 이미 있음) |
| 오류 | 400 `VALIDATION_FAILED`(`SKILL_CODE_UNKNOWN`), 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S3 (2026-09-18 범위 축소: 수동 카드 관리는 S2에서 제외) · FR-11 |

```java
public record ReviewItemCreateRequest(
        @NotBlank @Size(max = 100) String skillCode,
        @NotNull @Pattern(regexp = "^[A-Z0-9_.:-]{3,150}$") String conceptKey,
        @NotNull ReviewType reviewType,
        @NotBlank @Size(max = 2000) String prompt,
        @NotBlank @Size(max = 3000) String expectedAnswer,
        @NotNull @Size(min = 1, max = 6) List<@NotBlank @Size(max = 500) String> rubric) {}
```

- 새 항목: `origin = MANUAL`, `source_type = MANUAL`, `source_id = null`, `interval_days = 1`, `due_at = planDayStart(today + 1)`(`06-learning-engine-rules.md` §6.3), `rubric_json` = 입력 순서대로 `{ "id": "R1".."Rn", "criterion" }`.
- `(user_id, concept_key)`가 이미 있으면(I-06) 새로 만들지 않고 `06-learning-engine-rules.md` §6.3 마지막 행만 적용한다(`status = ACTIVE`, `due_at = min(기존, planDayStart(today + 1))`). 문항 내용은 덮어쓰지 않고 200으로 기존 항목을 반환한다.
- rubric은 최소 1개다. 복습 화면의 `CONCEPT_HINT`가 rubric 첫 항목을 공개하기 때문이다.

### 11.6 `PATCH /review-items/{reviewItemId}` — 카드 상태·문항 수정

| 항목 | 값 |
|---|---|
| operationId | `reviewUpdateItem` |
| 인증 / IK | Bearer / — |
| 요청 | `ReviewItemPatchRequest` |
| 응답 | 200 `ReviewItemView` |
| 오류 | 400 `VALIDATION_FAILED`(`NOT_BLANK_IF_PRESENT`), 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`, 409 `CONCURRENT_MODIFICATION`, 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S3 (2026-09-18 범위 축소: 수동 카드 관리는 S2에서 제외) · FR-11 |

```java
public record ReviewItemPatchRequest(
        ReviewItemStatus status,
        @Size(min = 1, max = 2000) String prompt,
        @Size(min = 1, max = 3000) String expectedAnswer,
        @NotNull Long version) {}
```

- `status` 전이는 `04-domain-model-and-db.md` §4.5의 사용자 전이만: `ACTIVE → SUSPENDED`, `SUSPENDED → ACTIVE`(`due_at = planDayStart(today + 1)`, `consecutive_failures = 0`), `ACTIVE/SUSPENDED → ARCHIVED`. 현재와 같은 값이면 상태 변경 없음. 그 외 409 `INVALID_STATE_TRANSITION`.
- `ARCHIVED` 항목의 `prompt`/`expectedAnswer` 수정은 409 `INVALID_STATE_TRANSITION`.
- `prompt` 또는 `expectedAnswer`를 바꿀 때 `variant_status ∈ {PENDING, RUNNING}`이면 409 `INVALID_STATE_TRANSITION`. `READY`/`FAILED`이면 variant 필드를 비우고 `variant_status = NONE`으로 둔다(바뀐 문항과 맞지 않는 variant를 출제하지 않기 위해).
- rubric은 이 API로 바꾸지 않는다.

---

## 12. Coach 모듈 (Project Coach)

Controller: `CoachReviewController`. Service: `CoachReviewService`, `CoachFindingService`, `ThinkingPatternRecorder`, `HintService`(learning). 흐름: 코드 + self-review 제출 → 비동기 분석 → finding(요약·질문만, 수정 코드 없음) → finding별 응답/hint → complete(close).

공통 경로 규칙: `reviewId`가 본인 것이 아니면 404 `RESOURCE_NOT_FOUND`, `findingId`가 그 review의 finding이 아니면 404 `RESOURCE_NOT_FOUND`.

### 12.1 `POST /coach/reviews` — 코드 리뷰 요청

| 항목 | 값 |
|---|---|
| operationId | `coachCreateReview` |
| 인증 / IK | Bearer / IK |
| 요청 | `CoachReviewCreateRequest` |
| 응답 | 202 `AsyncStatusView` (`id` = reviewId, `maskedSecretCount`, `pollPath` = `/api/v1/coach/reviews/{reviewId}`) |
| 오류 | 400 `VALIDATION_FAILED`(`AssertTrue`, `SKILL_CODE_UNKNOWN`, `REFERENCE_NOT_FOUND`), 400 `UNKNOWN_ENUM_VALUE`, 413 `CONTENT_TOO_LARGE`, 422 `SECRET_DETECTED_BLOCKED`, 429 `AI_*`, 503 `AI_UNAVAILABLE` |
| Sprint · 요구사항 | S4 · FR-12, FR-14, AC-06, AC-14, AC-19, AC-23 |

```java
public record CoachReviewCreateRequest(
        UUID sideProjectId,                              // null 가능. 이 리뷰가 어느 사이드 프로젝트 코드인지 (§19)
        @NotNull CoachContentType contentType,
        CodeLanguage language,
        @NotBlank String content,                        // UTF-8 30,000 byte, 1,000줄 이하 (컨트롤러 검사 → 413)
        @NotNull @AssertTrue Boolean confidentialConsent, // "회사·비밀 코드가 아니며, 코드가 AI 공급자 DeepSeek(중국 소재)로 전송되고 학습 미사용 약정이 없음에 동의" (02 SCR-COACH-NEW 문구)
        @Size(max = 5000) String userSelfReview,
        @NotNull @Valid CoachContextInput context) {}

public record CoachContextInput(
        @NotNull CoachProjectType projectType,           // SPRING_BOOT | JAVA_LIBRARY | ANDROID | FLUTTER | OTHER (04 §5.5)
        @Size(max = 100) String topic,
        @NotNull @Size(max = 10) @UniqueElements List<@NotBlank @Size(max = 100) String> skillCodes,
        @Size(max = 200) String fileName) {}
```

`CoachProjectType`은 `coach.domain`에 두는 enum이며 값은 `04-domain-model-and-db.md` §5.5 `projectType` 목록과 같다.

```json
{
  "sideProjectId": "3f7c8a02-1c3e-4b7f-9a55-6d2e4b1c0f77",
  "contentType": "CODE",
  "language": "JAVA",
  "content": "public class ConfigLoader {\n  public Config load(String path) {\n    try {\n      FileInputStream in = new FileInputStream(path);\n      …",
  "confidentialConsent": true,
  "userSelfReview": "파일 스트림을 닫는지 모르겠고, 예외를 너무 넓게 잡은 것 같다.",
  "context": { "projectType": "SPRING_BOOT", "topic": "HTTP_CLIENT", "skillCodes": ["JAVA.EXCEPTION"], "fileName": "ConfigLoader.java" }
}
```

검사·처리 순서 (`03-system-architecture.md` §5.3, I-14):
1. Bean Validation(400). `confidentialConsent`가 `true`가 아니면 400 `VALIDATION_FAILED`(code `AssertTrue`). 동의 문구(회사 코드 금지 + DeepSeek 데이터 정책)는 `02-user-scenarios-and-ux.md` SCR-COACH-NEW와 `07-security-and-privacy.md` §8이 정한다.
2. 크기(413 `CONTENT_TOO_LARGE`): `content`의 UTF-8 byte 수 > `devpilot.coach.max-content-bytes`(30000) 또는 줄 수 > `devpilot.coach.max-content-lines`(1000). 줄 수 = `\n` 개수 + (마지막 문자가 `\n`이 아니면 1).
3. `IdempotencyService`(§1.7).
4. `SecretMasker`를 `content`와 `userSelfReview`에 적용한다(§1.11). 차단 패턴(private key 블록)이 있으면 422 `SECRET_DETECTED_BLOCKED` — 아무것도 저장하지 않고 AI를 호출하지 않는다. 감사 로그 `SECRET_BLOCKED`.
5. `context.skillCodes[i]` 활성 skill 검사(`SKILL_CODE_UNKNOWN`). `sideProjectId`가 있으면 본인 `side_project`인지 확인한다 — 아니거나 없으면 400 `VALIDATION_FAILED`(field `sideProjectId`, code `REFERENCE_NOT_FOUND`).
6. AI 차단 검사(§1.9.3) → 429/503.
7. 트랜잭션: `coach_review` INSERT — `side_project_id`(있으면), `status = PENDING`, `content` = 마스킹본, `content_sha256` = 마스킹본 SHA-256 hex, `content_bytes`/`content_lines` = 마스킹본 기준, `masked_secret_count` = content와 self-review 마스킹 수 합, `user_self_review` = 마스킹본, `confidential_consent = true`, `context_json`(`fileName`의 `/`, `\` 제거, `04-domain-model-and-db.md` §5.5), `content_retention_until = now + devpilot.privacy.coach-content-retention-days`.
8. 커밋 후 `CoachAnalysisTask`(`03-system-architecture.md` §5.3): `COACH_REVIEW` → 출력 가드(verification `06-learning-engine-rules.md` §10, finding 최대 `devpilot.coach.max-findings`) → finding INSERT, `self_review_axes`, `mentioned_by_user`, `INCORRECT_CLAIM` observation(`06-learning-engine-rules.md` §9.3) → `status = COMPLETED`, `completed_at`. AI가 회사/비밀 코드로 판단하면 `FAILED(CONFIDENTIAL_SUSPECTED)`.

### 12.2 `GET /coach/reviews/{reviewId}` — 리뷰 조회·분석 폴링

| 항목 | 값 |
|---|---|
| operationId | `coachGetReview` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `CoachReviewView` |
| 오류 | 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S4 · FR-12, FR-14, AC-06, AC-07 |

```java
public record CoachReviewView(
        UUID id,
        UUID sideProjectId,                   // null 가능 (§12.1). 프로젝트 삭제 후에는 null
        AsyncJobStatus status,
        AsyncFailureCode failureCode,
        Instant statusUpdatedAt,
        boolean retryable,                    // §12.4 조건
        CoachContentType contentType,
        CodeLanguage language,
        String content,                       // 마스킹본. purge 후 null
        boolean contentPurged,
        Instant contentPurgedAt,
        Instant contentRetentionUntil,
        int contentBytes,
        int contentLines,
        int maskedSecretCount,
        String userSelfReview,
        List<ThinkingAxis> selfReviewAxes,
        CoachContextView context,
        List<CoachFindingView> findings,      // status = COMPLETED 전에는 []. sortOrder ASC
        Instant closedAt,
        Instant completedAt,                  // 분석 완료 시각
        Instant createdAt,
        AiMeta aiMeta,                        // 분석 호출 (COACH_REVIEW)
        UUID evidenceSourceEventId,           // close 후 COACH_REVIEW_COMPLETED learning_event id (첫 skill 행). POST /evidence/drafts에 사용. close 전·skill 없음이면 null
        long version) {}

public record CoachContextView(
        CoachProjectType projectType, String topic, List<String> skillCodes, String fileName) {}

public record CoachFindingView(
        UUID id,
        FindingType findingType,
        ThinkingAxis category,
        String skillCode,                     // null 가능
        String summary,
        String learningQuestion,
        LineRangeView location,               // location_start_line null이면 null
        VerificationStatus verificationStatus,
        Confidence confidence,
        VerificationSourceType sourceType,
        String sourceReference,               // null 가능. 서버는 fetch하지 않음
        boolean mentionedByUser,
        String userResponse,
        String aiFeedback,
        String followUpQuestion,              // coach_finding.ai_follow_up_question (≤ 300자), null 가능
        Boolean userIdentifiedIssue,
        AiMeta feedbackAiMeta,                // feedback_ai_call_id 기준. 피드백이 없으면 null
        DiscoveredBy discoveredBy,            // closedAt 전에는 null
        HintLevel maxHintLevel,
        List<DisclosedHintView> hints,        // level ordinal ASC
        FindingStatus status,
        long version) {}

public record LineRangeView(int startLine, int endLine) {}   // endLine null이면 startLine
```

```json
{
  "id": "0b6f…", "status": "COMPLETED", "failureCode": null, "statusUpdatedAt": "2026-11-20T11:03:40Z", "retryable": false,
  "contentType": "CODE", "language": "JAVA", "content": "public class ConfigLoader { … }", "contentPurged": false,
  "contentPurgedAt": null, "contentRetentionUntil": "2026-12-20T11:02:13Z", "contentBytes": 2311, "contentLines": 64,
  "maskedSecretCount": 0, "userSelfReview": "파일 스트림을 닫는지 모르겠고, 예외를 너무 넓게 잡은 것 같다.",
  "selfReviewAxes": ["RESOURCE_LIFECYCLE", "EXCEPTION_STRATEGY"],
  "context": { "projectType": "SPRING_BOOT", "topic": "HTTP_CLIENT", "skillCodes": ["JAVA.EXCEPTION"], "fileName": "ConfigLoader.java" },
  "findings": [
    { "id": "f1…", "findingType": "RISK", "category": "RESOURCE_LIFECYCLE", "skillCode": "JAVA.EXCEPTION",
      "summary": "예외가 발생하면 FileInputStream이 닫히지 않을 수 있습니다.",
      "learningQuestion": "try 블록 안에서 예외가 나면 이 스트림은 언제 닫힐까요?",
      "location": { "startLine": 4, "endLine": 9 },
      "verificationStatus": "SUPPORTED", "confidence": "HIGH", "sourceType": "OFFICIAL_DOC",
      "sourceReference": "https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html",
      "mentionedByUser": true, "userResponse": null, "aiFeedback": null, "followUpQuestion": null,
      "userIdentifiedIssue": null, "feedbackAiMeta": null, "discoveredBy": null, "maxHintLevel": "SELF_EXPLAIN", "hints": [], "status": "OPEN", "version": 0 }
  ],
  "closedAt": null, "completedAt": "2026-11-20T11:03:40Z", "createdAt": "2026-11-20T11:02:13Z",
  "aiMeta": { "model": "deepseek-flash", "promptVersion": "coach.review@v1",
              "guardActions": [ { "guard": "VERIFICATION", "action": "DOWNGRADED_TOOL_CLAIM", "detail": "finding[2] STATIC_ANALYSIS→AI_REASONING" } ] },
  "evidenceSourceEventId": null,
  "version": 2
}
```

### 12.3 `GET /coach/reviews` — 리뷰 이력

| 항목 | 값 |
|---|---|
| operationId | `coachListReviews` |
| 인증 / IK | Bearer / — |
| query | `limit`, `cursor` |
| 정렬 | `createdAt` DESC, `id` DESC |
| 응답 | 200 `CursorPage<CoachReviewSummaryView>` |
| 오류 | 400 `INVALID_CURSOR` |
| Sprint · 요구사항 | S4 · FR-12 |

```java
public record CoachReviewSummaryView(
        UUID id,
        AsyncJobStatus status,
        AsyncFailureCode failureCode,
        CoachContentType contentType,
        CodeLanguage language,
        CoachProjectType projectType,
        String fileName,
        int findingCount,
        int maskedSecretCount,
        boolean contentPurged,
        Instant closedAt,
        Instant createdAt) {}
```

### 12.4 `POST /coach/reviews/{reviewId}/retry` — 분석 재시도

| 항목 | 값 |
|---|---|
| operationId | `coachRetryReview` |
| 인증 / IK | Bearer / IK |
| 요청 | body 없음 |
| 응답 | 202 `AsyncStatusView` (§12.1과 같은 형태) |
| 오류 | 404 `RESOURCE_NOT_FOUND`, 409 `AI_TASK_NOT_RETRYABLE`, 429 `AI_*`, 503 `AI_UNAVAILABLE` |
| Sprint · 요구사항 | S4 · FR-12, AC-12 |

- 재시도 가능 조건(= `retryable`): `status = FAILED`, `failureCode ≠ CONFIDENTIAL_SUSPECTED`, `content`가 purge되지 않음. 아니면 409 `AI_TASK_NOT_RETRYABLE`.
- AI 차단 검사 → `status = PENDING`, `failure_code = null` → 커밋 후 §12.1 8단계. secret masking은 이미 저장된 마스킹본을 쓰므로 다시 하지 않는다.

### 12.5 `POST /coach/reviews/{reviewId}/findings/{findingId}/responses` — finding 응답

| 항목 | 값 |
|---|---|
| operationId | `coachRespondToFinding` |
| 인증 / IK | Bearer / IK |
| 요청 | `FindingResponseRequest` |
| 응답 | 200 `FindingResponseResult` |
| 오류 | 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`, 409 `REVIEW_ALREADY_CLOSED`, 409 `CONCURRENT_MODIFICATION`(tx1 저장 충돌만. tx2 충돌은 오류가 아님), 422 `SECRET_DETECTED_BLOCKED`. **AI 오류(429/502/503/504)는 반환하지 않는다** |
| Sprint · 요구사항 | S4 · FR-12, FR-13, AC-06, AC-19, AC-12 |

```java
public record FindingResponseRequest(
        @NotBlank @Size(max = 5000) String text) {}

public record FindingResponseResult(
        UUID findingId,
        FindingStatus status,                      // USER_RESPONDED
        String userResponse,                       // 저장된 마스킹본
        String aiFeedback,                         // 피드백을 저장한 경우만
        Boolean userIdentifiedIssue,               // 저장된 값 (피드백 실패 시 이전 값)
        String followUpQuestion,                   // 피드백을 저장한 경우만. ai_follow_up_question 값
        AsyncFailureCode feedbackSkippedReason,    // 피드백을 못 한 경우 (§1.9.4)
        AiMeta aiMeta,                             // feedback_ai_call_id로 만든다. 피드백을 저장하지 않았으면 null
        long version) {}
```

```json
{ "findingId": "f1…", "status": "USER_RESPONDED",
  "userResponse": "예외가 나면 close가 호출되지 않는다. try-with-resources로 감싸야 한다.",
  "aiFeedback": "스트림이 닫히지 않는 경로를 정확히 짚었습니다.", "userIdentifiedIssue": true,
  "followUpQuestion": "close()에서도 예외가 나면 원래 예외는 어떻게 될까요?", "feedbackSkippedReason": null,
  "aiMeta": { "model": "deepseek-flash", "promptVersion": "coach.response-feedback@v1", "guardActions": [] }, "version": 3 }
```

검사·처리 순서:
1. `SecretMasker`로 `text` 마스킹(§1.11). 차단 패턴이면 422 `SECRET_DETECTED_BLOCKED`(저장 안 함).
2. review·finding 조회(404) → review `status ≠ COMPLETED`이면 409 `INVALID_STATE_TRANSITION` → `closed_at` 있음 409 `REVIEW_ALREADY_CLOSED` → finding `status ∈ {RESOLVED, DISMISSED}`이면 409 `INVALID_STATE_TRANSITION`.
3. tx1: `user_response` = 마스킹본, `status = USER_RESPONDED`(`04-domain-model-and-db.md` §4.3), 이전 응답의 피드백(`ai_feedback`, `ai_follow_up_question`, `feedback_ai_call_id`)은 null로 비운다, `user_identified_issue`는 유지. 커밋(여기까지는 AI 결과와 무관하게 확정, `03-system-architecture.md` §5.3 `COACH_RESPONSE_FEEDBACK` 변형).
4. AI 차단 검사(§1.9.3) — 차단이면 `feedbackSkippedReason`을 채우고 5단계를 건너뛴다. 아니면 트랜잭션 밖에서 `COACH_RESPONSE_FEEDBACK`(20초, 재시도 0, 가드 위반도 재시도 없이 skip reason `AI_OUTPUT_INVALID`). 실패하면 skip reason.
5. tx2(피드백 성공 시): finding을 다시 읽어 `user_response`가 3단계에서 저장한 값과 같고 review `closed_at`이 여전히 null이면 `ai_feedback`, `ai_follow_up_question`(≤ 300자), `feedback_ai_call_id` 저장, `user_identified_issue = (기존 값 == true) || 결과 값`(한 번 짚은 문제는 유지, `06-learning-engine-rules.md` §9.3). 그 사이 새 응답이 저장됐거나 review가 close되었으면 **피드백을 저장하지도 응답에 넣지도 않고 409도 반환하지 않는다**(`03-system-architecture.md` §5.3). 이때 응답은 200이고 `aiFeedback`, `followUpQuestion`, `aiMeta`는 null, `feedbackSkippedReason`도 null, 나머지 필드는 조회 시점의 finding 값이다.

- 응답의 `followUpQuestion`, `aiMeta`는 저장된 값으로 만든다. §12.2 `CoachFindingView.followUpQuestion`, `feedbackAiMeta`로 다시 조회할 수 있다.

### 12.6 `POST /coach/reviews/{reviewId}/findings/{findingId}/hints` — finding Hint Ladder

| 항목 | 값 |
|---|---|
| operationId | `coachRequestFindingHint` |
| 인증 / IK | Bearer / IK |
| 요청 | `HintRequest` (§2.6) |
| 응답 | 200 `HintView` |
| 오류 | 400 `VALIDATION_FAILED`(`VALUE_NOT_ALLOWED`), 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`, 409 `REVIEW_ALREADY_CLOSED`, 409 `SELF_EXPLANATION_REQUIRED`, 409 `HINT_CONFIRMATION_REQUIRED`, 409 `FULL_EXAMPLE_NOT_ALLOWED`, 409 `CONCURRENT_MODIFICATION`, 429 `AI_*`, 502 `AI_OUTPUT_INVALID`, 502 `AI_REFUSED`, 503 `AI_UNAVAILABLE`, 504 `AI_TIMEOUT` |
| Sprint · 요구사항 | S4 · FR-10, FR-12, AC-16 |

검사·처리 순서는 §10.8과 같고 다음만 다르다(`06-learning-engine-rules.md` §9.1):
- 상태: review `status ≠ COMPLETED` → 409 `INVALID_STATE_TRANSITION`, `closed_at` 있음 → 409 `REVIEW_ALREADY_CLOSED`.
- HL-2: `user_response IS NOT NULL` 또는 `skipSelfExplanation = true`가 아니면 409 `SELF_EXPLANATION_REQUIRED`. `user_response IS NULL`이고 `skipSelfExplanation = true`로 통과하면 저장 트랜잭션에서 `SELF_EXPLANATION_SKIPPED` 이벤트(`source_type = COACH_FINDING`, `source_id` = findingId, `skill_id` = finding skill, payload `{ findingId }`, dedupe `SELF_EXPLANATION:{findingId}:{skillId}`)를 기록한다(`04-domain-model-and-db.md` §6, `06-learning-engine-rules.md` §9.3). hint 요청이 오류로 끝나면 이벤트도 남지 않는다.
- HL-5: `FULL_EXAMPLE`은 `giveUp = true`일 때만 허용(제출 개념 없음).
- HL-6: 모든 단계가 `HINT_GENERATE`(AI, 동기 — 가드 위반은 재시도 없이 502 `AI_OUTPUT_INVALID`)다. AI 호출이 필요한데 `content`가 purge되었으면 409 `INVALID_STATE_TRANSITION`(이미 공개된 단계의 HL-1 반환은 허용).
- 저장: `hint_disclosure(target_type = COACH_FINDING)`, finding `max_hint_level`, `HINT_DISCLOSED` 이벤트(`skill_id` = finding의 skill, null 가능). version 비교 대상은 finding이다.

### 12.7 `PATCH /coach/reviews/{reviewId}/findings/{findingId}` — 해결·해당 없음 표시

| 항목 | 값 |
|---|---|
| operationId | `coachUpdateFinding` |
| 인증 / IK | Bearer / — |
| 요청 | `FindingPatchRequest` |
| 응답 | 200 `CoachFindingView` |
| 오류 | 400 `VALIDATION_FAILED`(`VALUE_NOT_ALLOWED`), 404 `RESOURCE_NOT_FOUND`, 409 `REVIEW_ALREADY_CLOSED`, 409 `INVALID_STATE_TRANSITION`, 409 `CONCURRENT_MODIFICATION` |
| Sprint · 요구사항 | S4 · FR-12 |

```java
public record FindingPatchRequest(
        @NotNull FindingStatus status,        // RESOLVED | DISMISSED 만 허용
        @NotNull Long version) {}
```

- `status ∉ {RESOLVED, DISMISSED}` → 400 `VALIDATION_FAILED`(`VALUE_NOT_ALLOWED`). review `closed_at` 있음 → 409 `REVIEW_ALREADY_CLOSED`. finding이 `OPEN`/`USER_RESPONDED`가 아니면 409 `INVALID_STATE_TRANSITION`(`04-domain-model-and-db.md` §4.3).

### 12.8 `POST /coach/reviews/{reviewId}/complete` — 리뷰 완료(close)

| 항목 | 값 |
|---|---|
| operationId | `coachCompleteReview` |
| 인증 / IK | Bearer / IK |
| 요청 | body 없음 |
| 응답 | 200 `CoachReviewCompleteResponse` |
| 오류 | 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`(status ≠ COMPLETED), 409 `REVIEW_ALREADY_CLOSED` |
| Sprint · 요구사항 | S4 · FR-12, FR-13, AC-06, AC-19 |

처리 (한 트랜잭션):
1. `closed_at = now`.
2. finding마다 `discovered_by` 확정(`06-learning-engine-rules.md` §9.3), `thinking_pattern_observation(axis = category, observation = discoveredBy)`, `COACH_FINDING_CLOSED` 이벤트(finding skill이 없으면 생략, `04-domain-model-and-db.md` §6).
3. `COACH_REVIEW_COMPLETED` 이벤트를 관련 skill(`context.skillCodes` ∪ finding skill)마다 기록한다(`04-domain-model-and-db.md` §6).
4. 복습 항목 upsert(`06-learning-engine-rules.md` §9.4): `findingType ∈ {BUG, RISK}`, skill 있음, `discoveredBy ∈ {MISSED, FOUND_AFTER_HINT}`인 finding마다 `concept_key = COACH:{skillCode}:{category}`로 upsert한다(`06-learning-engine-rules.md` §6.3 규칙). 새로 만든 행 수는 `createdReviewItemCount`, 이미 있던 행을 갱신한 수는 `updatedReviewItemCount`다.
5. 이후 finding 응답·hint·PATCH는 409 `REVIEW_ALREADY_CLOSED`.

```java
public record CoachReviewCompleteResponse(
        CoachReviewView review,            // closedAt, findings[].discoveredBy가 채워진 상태
        int createdReviewItemCount,
        int updatedReviewItemCount) {}
```

### 12.9 `DELETE /coach/reviews/{reviewId}/content` — 원문 즉시 삭제

| 항목 | 값 |
|---|---|
| operationId | `coachPurgeContent` |
| 인증 / IK | Bearer / — |
| 응답 | 204 No Content |
| 오류 | 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`(status `PENDING`/`RUNNING`) |
| Sprint · 요구사항 | S4 · FR-12, NFR-05 |

- `content = null`, `content_purged_at = now`(`04-domain-model-and-db.md` §8). finding, self-review, 크기·해시 메타데이터는 유지한다.
- 이미 purge된 review는 변경 없이 204.
- purge 후에는 재시도(§12.4)와 AI hint 생성(§12.6)이 불가능하다.

---

## 13. Dashboard 모듈

Controller: `DashboardController`. Service: `DashboardQueryService`(읽기 전용).

### 13.1 `GET /dashboard` — 홈 집계

| 항목 | 값 |
|---|---|
| operationId | `dashboardGet` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `DashboardView` |
| 오류 | 공통만 |
| Sprint · 요구사항 | S2 최소(`today`, `todaySummary`, `dueReviewCount`, `weekStartDate`, `weekStudyMinutes`, `weekCompletedSessions`, `aiStatus`, `replanRecommended`) → S3(`streakDays`, `weeklySummary`) → S5 완성(`risk`, `milestoneTimeline`, `skillCategories`, `weakThinkingAxes`. S5 전에는 `null`/`[]`) · FR-16, AC-02 |

```java
public record DashboardView(
        LocalDate today,
        TodaySummaryView todaySummary,
        int dueReviewCount,
        int streakDays,                                 // 연속 학습 일수 (아래 계산)
        WeeklySummaryView weeklySummary,                // 이번 주 요약 — 만든 것이 먼저다
        LocalDate weekStartDate,
        int weekStudyMinutes,
        int weekCompletedSessions,
        RiskSummaryView risk,                           // 활성 plan 또는 snapshot이 없으면 null
        MilestoneTimelineView milestoneTimeline,        // 활성 plan이 없으면 null
        List<SkillCategorySummaryView> skillCategories,
        List<ThinkingAxis> weakThinkingAxes,
        AiStatus aiStatus,
        boolean replanRecommended) {}

public record TodaySummaryView(
        boolean generated,                // 오늘 daily_plan 존재
        UUID mainTaskId,                  // §8.1 mainTask 선택 규칙. 없으면 null
        String mainTaskTitle,
        TaskType mainTaskType,
        TaskStatus mainTaskStatus,
        Integer mainTaskEstimatedMinutes,
        TaskStatus reviewTaskStatus) {}   // REVIEW task 없으면 null

public record WeeklySummaryView(                    // 필드 순서가 화면 순서다: 만든 것 → 끝낸 것 → 적은 것 → 시간
        List<BuiltItemView> builtThisWeek,          // 이번 주에 완료한 CHALLENGE · PROJECT_TASK · REDO 과제, 최대 5개
        int completedTasks,
        int notesWritten,
        int studyMinutes) {}                        // = weekStudyMinutes (이번 주 월요일부터)

public record BuiltItemView(
        UUID taskId,
        TaskType taskType,
        String title,
        LocalDate planDate) {}

public record RiskSummaryView(
        RiskLevel currentRiskLevel,
        Integer currentRatioBp,
        LocalDate currentSnapshotDate,
        List<RiskPointView> trend) {}     // 최근 8개, snapshotDate ASC. 마지막 원소 = current

public record RiskPointView(LocalDate snapshotDate, RiskLevel riskLevel, Integer ratioBp) {}

public record MilestoneTimelineView(
        UUID planId,
        int planVersion,
        LocalDate todayMarker,            // = today
        LocalDate horizonDate,            // 06 §3.1
        List<TimelineMilestoneView> milestones) {}   // startDate ASC → sortOrder ASC

public record TimelineMilestoneView(
        UUID id, String title, LocalDate startDate, LocalDate endDate,
        Priority priority, MilestoneStatus status,
        boolean current) {}               // startDate ≤ today ≤ endDate

public record SkillCategorySummaryView(
        SkillCategory category,
        int skillCount,
        int avgPlanningLevelMilli,
        int avgTargetLevelMilli) {}
```

계산 (오늘 = 요청 시점 plan-day):

| 필드 | 계산 |
|---|---|
| `dueReviewCount` | `06-learning-engine-rules.md` §6.5 대상 수에 cap 적용 |
| `weekStartDate` | 오늘이 속한 ISO week의 월요일 |
| `weekStudyMinutes`, `weekCompletedSessions` | `plan_date ∈ [weekStartDate, today]`인 `COMPLETED` 세션의 `actual_minutes` 합, 개수 (`06-learning-engine-rules.md` §12 정의). 주간 학습 시간의 기준은 **이번 주 월요일부터**다 |
| `streakDays` | 완료한 `learning_task`가 1건 이상인 plan-day를 오늘부터 거꾸로 세어 **끊기지 않고 이어진 날 수**. 오늘 아직 완료가 없으면 어제부터 센다(오늘은 아직 끊긴 날이 아니다). 어제도 없으면 0. 세는 범위는 최근 366 plan-day까지다 |
| `weeklySummary` | `builtThisWeek`: `plan_date ∈ [weekStartDate, today]`이고 `status = COMPLETED`인 `CHALLENGE`·`PROJECT_TASK`·`REDO` task를 `plan_date` DESC, `sort_order` DESC로 최대 5개. `completedTasks`: 같은 기간의 `COMPLETED` task 수(REVIEW task 포함). `notesWritten`: 같은 기간에 만든 `side_project_note` 수(`created_at`의 plan-day 기준) — dashboard는 `project`를 직접 의존하지 않고 `evidence`의 지표 경로로 읽는다(`03-system-architecture.md` §2.2, `06-learning-engine-rules.md` §12 `projectNoteCount`). `studyMinutes`: `weekStudyMinutes`와 같은 값 |
| `risk.trend` | 사용자의 `plan_progress_snapshot`에서 서로 다른 `snapshot_date` 최근 8개. 같은 날짜에 여러 행(replan)이 있으면 `generated_at`이 가장 늦은 행 |
| `skillCategories` | 활성 plan의 `plan_skill_target` 중 `deferred = false`인 skill을 category별로 묶는다. `n` = skill 수. `avgPlanningLevelMilli = floorDiv(Σ_skill Σ_axis planning × 1000, n × 4)`, `avgTargetLevelMilli = floorDiv(Σ_skill Σ_axis target × 1000, n × 4)` (planning은 `06-learning-engine-rules.md` §7.5). `n ≥ 1`인 category만, `SkillCategory` 선언 순서 |
| `weakThinkingAxes` | `06-learning-engine-rules.md` §12 `weakThinkingAxes`, 기간 최근 28 plan-day |
| `aiStatus` | §1.9.1 |
| `replanRecommended` | 활성 plan의 `replan_recommended` (없으면 false) |

---

## 14. Evidence 모듈 (Evidence · Weekly review · Thinking trend)

Controller: `EvidenceController`(`/evidence*`), `WeeklyReviewController`(`/weekly-reviews*`, `/thinking-patterns/trend`). Service: `EvidenceService`, `EvidenceExportService`, `WeeklyReviewService`, 도메인 `MetricsCalculator`.

### 14.1 공통 view record

```java
public record EvidenceView(
        UUID id,
        SkillRef skill,                      // null 가능
        UUID sourceLearningEventId,          // null 가능
        EvidenceStatus status,
        EvidenceGenerationStatus generationStatus,   // NONE | PENDING | RUNNING | COMPLETED | FAILED
        AsyncFailureCode failureCode,
        Instant statusUpdatedAt,
        EvidenceDraftView aiDraft,           // ai_draft_json. 없으면 null. 사용자 편집과 무관한 AI 원본
        String title,
        String problem,
        String analysis,
        String action,
        String result,
        List<String> referenceLinks,
        List<String> explanationTopics,
        Instant acceptedAt,
        Instant createdAt,
        Instant updatedAt,
        AiMeta aiMeta,                       // ai_call_id 기준. 초안이 없으면 null
        long version) {}

public record EvidenceDraftView(
        String title, String problem, String analysis, String action, String result,
        List<String> explanationTopics, String promptVersion) {}   // 04 §5.8
```

`aiMeta`는 `evidence_candidate.ai_call_id`로 만든다(§1.9.5). `aiDraft.promptVersion`은 초안 JSON 안의 값이다.

### 14.2 `GET /evidence` — 목록

| 항목 | 값 |
|---|---|
| operationId | `evidenceList` |
| 인증 / IK | Bearer / — |
| query | `status`: `EvidenceStatus`, 선택 / `limit`, `cursor` |
| 정렬 | `createdAt` DESC, `id` DESC |
| 응답 | 200 `CursorPage<EvidenceView>` |
| 오류 | 400 `UNKNOWN_ENUM_VALUE`, 400 `INVALID_CURSOR` |
| Sprint · 요구사항 | S6 · FR-18, AC-21 |

### 14.3 `GET /evidence/{evidenceId}` — 상세·초안 폴링

| 항목 | 값 |
|---|---|
| operationId | `evidenceGet` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `EvidenceView` |
| 오류 | 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S6 · FR-18 |

경로 규칙: `/evidence/export`는 리터럴 경로로 `/evidence/{evidenceId}`보다 우선한다.

### 14.4 `POST /evidence` — 수동 작성

| 항목 | 값 |
|---|---|
| operationId | `evidenceCreate` |
| 인증 / IK | Bearer / IK |
| 요청 | `EvidenceCreateRequest` |
| 응답 | 201 `EvidenceView` (`status = CANDIDATE`, `generationStatus = NONE`) |
| 오류 | 400 `VALIDATION_FAILED`(`SKILL_CODE_UNKNOWN`, `URL`), 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S6 · FR-18 |

```java
public record EvidenceCreateRequest(
        @Size(max = 100) String skillCode,                                  // 선택
        @NotBlank @Size(max = 200) String title,
        @Size(max = 3000) String problem,
        @Size(max = 3000) String analysis,
        @Size(max = 3000) String action,
        @Size(max = 3000) String result,
        @NotNull @Size(max = 10) List<@NotBlank @Size(max = 500) @URL(protocol = "https") String> referenceLinks,
        @NotNull @Size(max = 10) List<@NotBlank @Size(max = 200) String> explanationTopics) {}
```

- 빈 문자열 텍스트 필드는 null로 저장한다. `referenceLinks`는 서버가 fetch하지 않는다.

### 14.5 `POST /evidence/drafts` — AI 초안 요청

| 항목 | 값 |
|---|---|
| operationId | `evidenceRequestDraft` |
| 인증 / IK | Bearer / IK |
| 요청 | `EvidenceDraftRequest` |
| 응답 | 202 `AsyncStatusView` (`id` = evidenceId, `pollPath` = `/api/v1/evidence/{evidenceId}`) |
| 오류 | 400 `VALIDATION_FAILED`(`REFERENCE_NOT_FOUND`, `VALUE_NOT_ALLOWED`, `ONE_OF_REQUIRED`, `MUTUALLY_EXCLUSIVE`), 429 `AI_*`, 503 `AI_UNAVAILABLE` |
| Sprint · 요구사항 | S6 · FR-18, FR-29, AC-21, AC-33 |

```java
public record EvidenceDraftRequest(
        UUID sourceLearningEventId,      // 둘 중 정확히 하나
        UUID sourceProjectNoteId) {}     // 사이드 프로젝트 기록 (§19.8). 장애 기록이 STAR와 잘 맞는다
```

처리:
1. 둘 다 없으면 `ONE_OF_REQUIRED`(field `sourceLearningEventId`), 둘 다 있으면 `MUTUALLY_EXCLUSIVE`(field `sourceProjectNoteId`).
1a. `sourceLearningEventId`: 이벤트가 본인 것이고 `invalidated_at IS NULL`이어야 한다. 아니면 `REFERENCE_NOT_FOUND`(field `sourceLearningEventId`). `event_type ∉ {CHALLENGE_EVALUATED, COACH_FINDING_CLOSED, COACH_REVIEW_COMPLETED, SESSION_COMPLETED, REDO_COMPLETED}`이면 `VALUE_NOT_ALLOWED`(같은 field, `17-ai-integration.md` §3.8).
1b. `sourceProjectNoteId`: 본인 기록이어야 한다. 아니면 `REFERENCE_NOT_FOUND`(field `sourceProjectNoteId`). 유형 제한은 없다 — 결정 기록도 초안 입력이 된다. AI 입력은 그 기록의 **마스킹본 텍스트 항목과 제목·날짜**이고, `skill_id`가 있으면 evidence의 skill이 된다(없으면 `skill_id = null`인 후보를 만들고 사용자가 편집에서 고른다).
2. AI 차단 검사(§1.9.3).
3. 트랜잭션: `evidence_candidate` INSERT(`skill_id` = 이벤트 또는 기록의 skill, `source_learning_event_id`(기록 경로면 null), `status = CANDIDATE`, `generation_status = PENDING`). 기록 경로의 출처는 `ai_draft_json.sourceProjectNoteId`로 남긴다 — `evidence_candidate`에 컬럼을 더하지 않는다.
4. 커밋 후 `EvidenceDraftTask`: `EVIDENCE_DRAFT` → `ai_draft_json`, `ai_call_id` 저장(`04-domain-model-and-db.md` §5.8) → 초안의 `title/problem/analysis/action/result/explanationTopics`를 편집 필드에 복사(생성 중에는 PATCH가 막혀 있으므로 편집 필드는 비어 있다) → `generation_status = COMPLETED`. 실패 시 `FAILED` + `failureCode`. 재시도 endpoint는 없다.

### 14.6 `PATCH /evidence/{evidenceId}` — 편집·복원

| 항목 | 값 |
|---|---|
| operationId | `evidenceUpdate` |
| 인증 / IK | Bearer / — |
| 요청 | `EvidencePatchRequest` |
| 응답 | 200 `EvidenceView` |
| 오류 | 400 `VALIDATION_FAILED`(`NOT_BLANK_IF_PRESENT`, `VALUE_NOT_ALLOWED`, `URL`), 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION`, 409 `CONCURRENT_MODIFICATION`, 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S6 · FR-18 |

```java
public record EvidencePatchRequest(
        @Size(max = 200) String title,
        @Size(max = 3000) String problem,
        @Size(max = 3000) String analysis,
        @Size(max = 3000) String action,
        @Size(max = 3000) String result,
        @Size(max = 10) List<@NotBlank @Size(max = 500) @URL(protocol = "https") String> referenceLinks,
        @Size(max = 10) List<@NotBlank @Size(max = 200) String> explanationTopics,
        EvidenceStatus status,               // CANDIDATE만 허용 (REJECTED → CANDIDATE 복원)
        @NotNull Long version) {}
```

- 텍스트: `null` = 변경 없음, `""` = 지움(`title`은 지울 수 없음 → `NOT_BLANK_IF_PRESENT`). 목록: `null` = 변경 없음, `[]` = 지움, 값 = 전체 교체.
- `generationStatus ∈ {PENDING, RUNNING}`이면 409 `INVALID_STATE_TRANSITION`.
- `status`: `CANDIDATE` 외 값은 400 `VALIDATION_FAILED`(`VALUE_NOT_ALLOWED`)(accept/reject는 전용 endpoint). 현재 `REJECTED`면 `CANDIDATE`로 복원, `CANDIDATE`면 변경 없음, `ACCEPTED`면 409 `INVALID_STATE_TRANSITION`(`04-domain-model-and-db.md` §4.7).
- `ACCEPTED` evidence도 텍스트·목록 수정은 허용한다.

### 14.7 `POST /evidence/{evidenceId}/accept` · `POST /evidence/{evidenceId}/reject`

| 항목 | 값 |
|---|---|
| operationId | `evidenceAccept` / `evidenceReject` |
| 인증 / IK | Bearer / IK |
| 요청 | body 없음 |
| 응답 | 200 `EvidenceView` |
| 오류 | 400 `VALIDATION_FAILED`(`REQUIRED_FOR_ACCEPT`, accept만), 404 `RESOURCE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION` |
| Sprint · 요구사항 | S6 · FR-18, AC-21 |

- 공통: `status = CANDIDATE`가 아니거나 `generationStatus ∈ {PENDING, RUNNING}`이면 409 `INVALID_STATE_TRANSITION`.
- accept: `title`, `problem`, `action`, `result`가 모두 공백이 아니어야 한다(STAR export 필수 항목). 비어 있는 필드마다 `REQUIRED_FOR_ACCEPT` field error. 성공 시 `status = ACCEPTED`, `accepted_at = now`, `EVIDENCE_ACCEPTED` 이벤트(`skill_id` = evidence skill, null 가능, `04-domain-model-and-db.md` §6).
- reject: `status = REJECTED`. 이벤트 없음.

### 14.8 `GET /evidence/export` — STAR Markdown

| 항목 | 값 |
|---|---|
| operationId | `evidenceExport` |
| 인증 / IK | Bearer / — |
| query | `format`: 필수, 값 `markdown`만 허용(그 외 400 `VALIDATION_FAILED` `VALUE_NOT_ALLOWED`) |
| 응답 | 200 `text/markdown; charset=utf-8`, `Content-Disposition: attachment; filename="devpilot-evidence-{yyyyMMdd}.md"` |
| 오류 | 400 `VALIDATION_FAILED` |
| Sprint · 요구사항 | S6 · FR-18, AC-21 |

- 대상: `status = ACCEPTED`, `accepted_at` ASC. 사용자 텍스트는 그대로 넣는다(escape 없음). 값이 없는 절(`###`)과 목록 줄은 생략한다. 대상이 없으면 제목 줄과 `승인된 evidence가 없습니다.` 한 줄만 쓴다. 줄바꿈 LF.

```markdown
# DevPilot Evidence

> Exported: 2026-12-28 (Asia/Seoul) · 2 items

## 1. ConfigLoader 자원 누수 수정

- Skill: Exception (`JAVA.EXCEPTION`)
- Accepted: 2026-12-21

### Situation / Task
설정 파일 로더가 예외 발생 시 스트림을 닫지 않았다.

### Analysis
…

### Action
…

### Result
…

### Explanation topics
- try-with-resources와 소유권

### Links
- https://github.com/example/devpilot/pull/42
```

### 14.9 Weekly review

```java
public record WeeklyReviewView(
        LocalDate weekStartDate,
        LocalDate weekEndDate,              // weekStartDate + 6
        WeeklyMetricsView metrics,
        String reflection,                  // null 가능
        Instant createdAt,
        Instant updatedAt,
        long version) {}

public record WeeklyMetricsView(            // 04 §5.7 metrics_json, 06 §12
        LocalDate weekStartDate,
        int completedSessions,
        int studyMinutes,
        Integer independentSolveRateBp,     // 분모 0이면 null
        Integer averageHintLevelMilli,      // 대상 0개면 null
        Integer recallSuccessRateBp,        // review_answer 0개면 null
        int selfFoundRiskCount,
        int acceptedEvidenceCount,
        RiskLevel riskLevel,                // snapshot 없으면 null
        Integer ratioBp,
        List<ThinkingAxis> weakThinkingAxes) {}

public record WeeklyReflectionRequest(
        @Size(max = 5000) String reflection,   // null 또는 "" = 지움
        @NotNull Long version) {}
```

| Method Path | operationId | 응답 | 오류 | Sprint |
|---|---|---|---|---|
| `GET /weekly-reviews` (`limit`, `cursor`; 정렬 `weekStartDate` DESC, `id` DESC) | `evidenceListWeeklyReviews` | 200 `CursorPage<WeeklyReviewView>` | 400 `INVALID_CURSOR` | S5 |
| `GET /weekly-reviews/{weekStartDate}` | `evidenceGetWeeklyReview` | 200 `WeeklyReviewView` | 400 `VALIDATION_FAILED`(`TYPE_MISMATCH`, `NOT_MONDAY`), 404 `RESOURCE_NOT_FOUND` | S5 |
| `PUT /weekly-reviews/{weekStartDate}/reflection` (IK 없음) | `evidenceUpdateWeeklyReflection` | 200 `WeeklyReviewView` | 400 `VALIDATION_FAILED`(`NOT_MONDAY`), 404 `RESOURCE_NOT_FOUND`, 409 `CONCURRENT_MODIFICATION`, 422 `SECRET_DETECTED_BLOCKED` | S5 |

- 요구사항: FR-17, AC-21. `weekly_review` 행은 `WeeklyReviewJob`만 만든다(`03-system-architecture.md` §6). 행이 없는 주는 404다. PUT은 `reflection`만 바꾸고 `metrics`는 바꾸지 않는다.

### 14.10 `GET /thinking-patterns/trend` — thinking 축 추세

| 항목 | 값 |
|---|---|
| operationId | `evidenceGetThinkingTrend` |
| 인증 / IK | Bearer / — |
| query | `weeks`: int, 선택, 기본 8, 1~26 |
| 응답 | 200 `ThinkingTrendResponse` |
| 오류 | 400 `VALIDATION_FAILED` |
| Sprint · 요구사항 | S5 · FR-13, AC-19 |

```java
public record ThinkingTrendResponse(
        List<LocalDate> weekStartDates,               // ASC. 마지막 = 이번 주(진행 중)
        List<ThinkingAxisTrendView> axes,             // ThinkingAxis 선언 순서, 10개 모두
        List<ThinkingAxis> weakThinkingAxes) {}       // 06 §12 규칙, 기간 = 반환한 주 전체

public record ThinkingAxisTrendView(
        ThinkingAxis axis,
        List<ThinkingWeekPointView> points) {}        // weekStartDates와 같은 순서·개수

public record ThinkingWeekPointView(
        LocalDate weekStartDate,
        int total,
        int mentionedUnprompted,
        int foundAfterHint,
        int missed,
        int incorrectClaim,
        Integer mentionedUnpromptedRateBp) {}         // total = 0이면 null, 아니면 floorDiv(mentionedUnprompted × 10000, total)
```

- 데이터: `thinking_pattern_observation`. 관측의 주 = `planDate(observed_at)`이 속한 ISO week(월요일 시작, 사용자 timezone·dayStartHour).
- 기간: 이번 주 월요일 − (weeks − 1)주 ~ 오늘.

---

## 15. Radar 모듈 (로드맵 비교)

Controller: `RequirementRadarController`. Service: `RequirementAnalysisService`, 도메인 `RequirementFitClassifier`.

사용자가 붙여넣은 공개 학습 로드맵이나 기술 목록에서 항목(`requirement_item`)을 뽑고, 항목마다 사용자의 현재 레벨 기준 준비 상태를 분류한다. 붙여넣은 문서 1건이 `requirement_doc`이다.

원칙: 달성 확률, 점수, 퍼센트 적합도 필드를 **두지 않는다**. 항목별 `READY/STRETCH/LATER` 분류와 개수만 반환한다(FR-19). 서버는 `sourceUrl`을 fetch하지 않고 외부 사이트를 수집하지 않는다.

### 15.1 공통 view record

```java
public record RequirementDocView(
        UUID id,
        String title,
        String sourceUrl,                    // 저장만. 표시용 링크
        String sourceText,                   // purge 후 null
        boolean sourceTextPurged,
        AsyncJobStatus analysisStatus,
        AsyncFailureCode failureCode,
        Instant statusUpdatedAt,
        List<RequirementItemView> requirements,   // analysisStatus = COMPLETED 전에는 []. sortOrder ASC
        RequirementFitCountsView fitCounts,       // COMPLETED 전에는 null
        Instant createdAt,
        Instant analyzedAt,
        AiMeta aiMeta) {}

public record RequirementItemView(
        UUID id,
        String rawText,
        RequirementType requirementType,
        SkillRef skill,                      // 매칭 skill 없으면 null
        RequirementFitCategory fitCategory,  // 06 §13. skill 없으면 null
        List<MatchedEvidenceView> matchedEvidence) {}   // 06 §13: 해당 skill의 ACCEPTED evidence, accepted_at DESC, 최대 3개

public record MatchedEvidenceView(UUID id, String title, EvidenceStatus status) {}

public record RequirementFitCountsView(      // 항목 개수. 비율·점수 아님
        int requiredReady, int requiredStretch, int requiredLater, int requiredUnmatched,
        int preferredReady, int preferredStretch, int preferredLater, int preferredUnmatched) {}

public record RequirementDocSummaryView(
        UUID id, String title,
        AsyncJobStatus analysisStatus, AsyncFailureCode failureCode,
        int requirementCount, Instant createdAt, Instant analyzedAt) {}
```

### 15.2 `POST /requirement-docs` — 로드맵 비교 요청

| 항목 | 값 |
|---|---|
| operationId | `radarCreate` |
| 인증 / IK | Bearer / IK |
| 요청 | `RequirementDocCreateRequest` |
| 응답 | 202 `AsyncStatusView` (`id` = requirementDocId, `pollPath` = `/api/v1/requirement-docs/{requirementDocId}`) |
| 오류 | 400 `VALIDATION_FAILED`(`URL`), 413 `CONTENT_TOO_LARGE`, 422 `SECRET_DETECTED_BLOCKED`, 429 `AI_*`, 503 `AI_UNAVAILABLE` |
| Sprint · 요구사항 | S7 · FR-19, AC-22 |

```java
public record RequirementDocCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) @URL String sourceUrl,     // http/https만. 선택
        @NotBlank String sourceText) {}              // UTF-8 20,000 byte 이하 (컨트롤러 검사 → 413)
```

처리:
1. Bean Validation → `sourceText` 크기(413) → idempotency → `sourceText` 마스킹(§1.11, 차단 시 422) → AI 차단 검사(§1.9.3).
2. 트랜잭션: `requirement_doc` INSERT(마스킹본 `source_text`, `analysis_status = PENDING`).
3. 커밋 후 `RequirementAnalysisTask`: `REQUIREMENT_EXTRACT` → `requirement_item` INSERT(skill code 가드) → `RequirementFitClassifier`로 `fit_category`, `matched_evidence_ids` 계산(`06-learning-engine-rules.md` §13 — 증거 레벨 기준, 분석 시점에 고정) → `analysis_status = COMPLETED`, `analyzed_at`. 실패 시 `FAILED` + `failureCode`. 재시도 endpoint는 없다.
- `source_text`는 180일 후 purge된다(`04-domain-model-and-db.md` §8).

### 15.3 조회·삭제

| Method Path | operationId | 응답 | 오류 | Sprint |
|---|---|---|---|---|
| `GET /requirement-docs/{requirementDocId}` | `radarGet` | 200 `RequirementDocView` | 404 `RESOURCE_NOT_FOUND` | S7 |
| `GET /requirement-docs` (`limit`, `cursor`; 정렬 `createdAt` DESC, `id` DESC) | `radarList` | 200 `CursorPage<RequirementDocSummaryView>` | 400 `INVALID_CURSOR` | S7 |
| `DELETE /requirement-docs/{requirementDocId}` | `radarDelete` | 204 | 404 `RESOURCE_NOT_FOUND` | S7 |

- DELETE는 `requirement_doc`과 `requirement_item`(cascade)를 삭제한다. 분석 중에 삭제하면 `RequirementAnalysisTask`는 저장 시점에 행이 없음을 확인하고 결과를 버린다(오류 기록 없음). 연결된 evidence는 삭제하지 않는다.
- 요구사항: FR-19, AC-22.

---

> 사이드 프로젝트(`project` 모듈)와 코드 읽기 콘텐츠 조회는 **§19**에 있다. §16~§18은 다른 문서가 절 번호로 참조하는 부록이라 번호를 밀지 않았다. 읽는 순서는 §15 → §19 → §16이다.

---

## 16. Ops

### 16.1 `GET /actuator/health`

| 항목 | 값 |
|---|---|
| operationId | (OpenAPI 문서에서 제외) |
| 인증 | 없음 |
| 응답 | 200 `{"status":"UP"}` / 503 `{"status":"DOWN"}` (`management.endpoint.health.show-details=never`) |
| Sprint | S0 |

- `management.endpoints.web.exposure.include=health`. liveness/readiness group은 compose healthcheck용이며 Caddy에서 외부로 노출하지 않는다.
- `local` profile에서만 `/v3/api-docs/**`, `/swagger-ui/**`를 연다(`03-system-architecture.md` §4.1).

---

## 17. 검증 상한 요약

| 대상 | 상한 | 초과 시 | DB 근거 |
|---|---|---|---|
| 요청 body 전체 | 64KB | 413 `REQUEST_TOO_LARGE` | `devpilot.security.max-request-body-bytes` |
| `displayName` | 1~100자 | 400 | `app_user.display_name varchar(100)` |
| `timezone` | IANA region ID, 50자 | 400 | `varchar(50)` |
| `dayStartHour` | 0~6 | 400 | CHECK |
| `weekdayStudyMinutes`, `weekendStudyMinutes` | 0~720 | 400 | CHECK |
| `targetCompletionDate` (목표일) | 오늘+1일 ~ 오늘+3년 | 400 | — |
| milestone 날짜 | 오늘−1년 ~ 오늘+3년, start ≤ end | 400 | CHECK |
| `focusSkillCodes` | ≤ 10, 유일 | 400 | — |
| `selfAssessments` | ≤ 14(= `SkillCategory` 값 수), category 유일, level 0~5 | 400 | CHECK |
| skill code 문자열 | ≤ 100자 | 400 | `skill.code varchar(100)` |
| replan `reason` | ≤ 1000자 (commit은 필수) | 400 | `change_reason varchar(1000)` |
| replan `milestones` | ≤ 24개 | 400 | — |
| milestone `title` / `description` | 1~200자 / ≤ 2000자 | 400 | `varchar(200)` / `varchar(2000)` |
| milestone `skillCodes` | ≤ 30, 유일 | 400 | — |
| milestone `sortOrder` | 0~10000 | 400 | — |
| `acceptedDeferrals` / `acceptedTargetReductions` / `restoredDeferrals` / `acceptedTargetRaises` | ≤ 100 / ≤ 400 / ≤ 100 / ≤ 400 (deferral과 restore, 축소와 상향은 겹칠 수 없음) | 400 | — |
| `availableMinutes` | 5~720 | 400 | CHECK 5~720 |
| `actualMinutes` | 0~720, ≤ ⌈경과 분 × 1.5⌉ | 400 | CHECK |
| `selfReflection` | ≤ 5000자 | 400 | text |
| 러버덕 `explanation` | ≤ 2000자 (`devpilot.rubberduck.max-explanation-chars`) | 413 `CONTENT_TOO_LARGE` | `rubber_duck_turn.user_text` text |
| 러버덕 `conceptKey` | `^[A-Z][A-Z0-9_]*(\.[A-Z][A-Z0-9_]*)+$`, ≤ 120자 | 400 | `rubber_duck_session.concept_key varchar(120)` |
| 러버덕 턴 수 | ≤ `devpilot.rubberduck.max-turns`(5) | 409 `INVALID_STATE_TRANSITION` | `turn_count smallint` |
| 사이드 프로젝트 `name` | 1~100자 | 400 | `side_project.name varchar(100)` |
| 사이드 프로젝트 `description` | ≤ 1000자 | 400 | `varchar(1000)` |
| 사이드 프로젝트 `repoUrl` | ≤ 500자, http(s) URL (저장만, fetch 금지) | 400 | `varchar(500)` |
| 사이드 프로젝트 `stack` | ≤ 300자 | 400 | `varchar(300)` |
| 프로젝트 기록 `title` | 1~200자 | 400 | `side_project_note.title varchar(200)` |
| 프로젝트 기록 `occurredOn` | 오늘(plan-day) 이하 | 400 `DATE_OUT_OF_RANGE` | `date` |
| 프로젝트 기록 본문 항목 (`decision*`·`incident*`) | 각 1~4000자, 유형에 맞는 항목만 | 400 | text + CHECK `side_project_note_body_by_type` |
| `redoWithoutAi` | `REDO` 완료 요청에서만·그때는 필수 | 400 | `learning_task.redo_without_ai boolean` CHECK |
| reading `key` (path) | `^[A-Z0-9][A-Z0-9_.]{2,149}$` | 400 | `learning_task.reading_key varchar(150)` |
| `readingFeedback` | `HELPFUL`·`TOO_HARD`·`BORING`, `READ_CODE` 완료 요청에서만 | 400 | `learning_task.reading_feedback varchar(20)` CHECK |
| `explainedToPerson` / `explainedNote` | `EXPLAIN`·`READ_CODE` 완료 요청에서만, 메모는 ≤ 500자이고 `explainedToPerson = true`일 때만 | 400 | `learning_task.explained_to_person boolean`, `explained_note varchar(500)` + CHECK `learning_task_explained_by_type` |
| challenge `elapsedSeconds` (제출) | 0~86400, 선택 | 400 | `challenge_attempt.elapsed_seconds int` CHECK ≥ 0 |
| challenge `timeLimitMinutes` (응답 전용, 콘텐츠 값) | 1~120 또는 없음 | — | `challenge.time_limit_minutes int` CHECK |
| `tipKey` / `termKey` (path) | `^TIP\.[A-Z][A-Z0-9_]*\.[A-Z][A-Z0-9_]*\.[0-9]{3}$` / `^TERM\.[A-Z][A-Z0-9_]*\.[A-Z][A-Z0-9_]*$` (≤ 120자) | 400 | `user_daily_tip.tip_key varchar(120)`, `review_item.concept_key varchar(150)`(`TERM:{termKey}`) |
| 용어 검색 `q` | ≤ 100자 | 400 | — |
| 세션 조회 기간 | ≤ 366일 | 400 | — |
| challenge `difficulty` / `targetMinutes` | 1~5 / 5~180 | 400 | CHECK |
| self-explanation `text` | ≤ 5000자 | 400 | text |
| submission `answerText` | ≤ 5000자 | 400 | text |
| submission `code` | ≤ 20,000 byte (UTF-8) | 413 `CONTENT_TOO_LARGE` | text |
| review `answerText` | ≤ 5000자 | 400 | text |
| `responseSeconds` | 0~86400 | 400 | CHECK |
| 수동 카드 `conceptKey` | `^[A-Z0-9_.:-]{3,150}$` | 400 | `varchar(150)` |
| 수동 카드 `prompt` / `expectedAnswer` | 1~2000자 / 1~3000자 | 400 | text |
| 수동 카드 `rubric` | 1~6개, 항목 ≤ 500자 | 400 | jsonb |
| coach `content` | ≤ 30,000 byte (UTF-8), ≤ 1000줄 | 413 `CONTENT_TOO_LARGE` | `devpilot.coach.*` |
| coach `userSelfReview` | ≤ 5000자 | 400 | `varchar(5000)` |
| coach `context.topic` / `fileName` / `skillCodes` | ≤ 100자 / ≤ 200자 / ≤ 10 | 400 | jsonb |
| coach 응답 `text` | 1~5000자 | 400 | `user_response varchar(5000)` |
| evidence `title` | 1~200자 | 400 | `varchar(200)` |
| evidence `problem`/`analysis`/`action`/`result` | ≤ 3000자 | 400 | `varchar(3000)` |
| evidence `referenceLinks` | ≤ 10개, https URL, ≤ 500자 | 400 | `varchar(500)[]` |
| evidence `explanationTopics` | ≤ 10개, ≤ 200자 | 400 | jsonb |
| weekly `reflection` | ≤ 5000자 | 400 | `varchar(5000)` |
| thinking trend `weeks` | 1~26 | 400 | — |
| 로드맵 비교 `title` / `sourceUrl` | 1~200자 / ≤ 2000자 http(s) URL | 400 | `varchar` |
| 로드맵 비교 `sourceText` | ≤ 20,000 byte (UTF-8) | 413 `CONTENT_TOO_LARGE` | text |
| `limit` | 1~100 (기본 20) | 400 | — |
| `cursor` | ≤ 512자 | 400 | — |
| `Idempotency-Key` | `^[A-Za-z0-9_-]{8,100}$` | 400 | `varchar(100)` |

- "자"는 Java `String.length()`(UTF-16 code unit) 기준이다(`@Size`의 동작). byte 상한은 `getBytes(UTF_8).length`로 센다.
- 모든 문자열 입력은 앞뒤 공백을 제거하지 않고 저장한다. 예외: `displayName`은 trim 후 저장한다.

---

## 18. OpenAPI

| 항목 | 규칙 |
|---|---|
| 생성 | `springdoc-openapi` (Spring Boot 4.1 호환 버전은 구현 시 확인). 코드 annotation에서 생성한다 |
| 노출 | `local` profile만 `/v3/api-docs`, `/swagger-ui` (§16.1) |
| 스냅샷 | `docs/api/openapi.yaml`. `test` profile 통합 테스트가 `/v3/api-docs.yaml`을 받아 스냅샷과 비교한다. 다르면 CI 실패. API를 바꾸는 PR은 스냅샷 갱신을 함께 커밋한다 |
| 클라이언트 모델 | Flutter는 스냅샷을 기준으로 freezed 모델을 수기 작성한다(DEC-12) |
| operationId | `<module><Action>` lowerCamelCase. 각 endpoint 절의 값을 `@Operation(operationId = ...)`로 고정한다 |
| tag | 모듈 이름: `user`, `onboarding`, `goal`, `skill`, `plan`, `today`, `learning`, `rubber-duck`(`rubberduck` 모듈의 `/rubber-duck*`, §9.5~§9.10), `project`(§19.1~§19.6), `training`, `review`, `coach`, `dashboard`, `evidence`, `radar`, `dev`(`devIssueToken`, `devJwks` — `auth-mode=devtoken`일 때만 스키마에 나타난다). `GET /readings/{key}`(§19.7)는 `today` tag를, `/tips*`(§20.2~§20.4)는 `today`, `/terms*`(§20.5~§20.7)는 `review` tag를 쓴다 |
| security scheme | `bearerAuth` (HTTP bearer, JWT). 전역 적용, `userGetCalendarFeed`만 `security: []` |
| 공통 component | header parameter `IdempotencyKey`(IK endpoint에 `required: true`, preview에는 `required: false`), schema `ProblemDetail`(§1.2 확장 필드 포함), `FieldError`, `AsyncStatusView`, `AiMeta`, `SkillRef`, `AxisLevels`, `CursorPage_<T>` |
| 오류 응답 | 모든 operation에 `default` 응답 `application/problem+json` → `ProblemDetail`. 각 endpoint 표의 오류 status를 명시적으로 나열한다 |
| enum | `04-domain-model-and-db.md` §3 값 목록을 schema `enum`으로 생성한다(Java enum에서 자동) |
| nullable | nullable 필드는 schema에 `nullable`(OpenAPI 3.1이면 `type: [..., "null"]`)로 표시한다. 필수 여부: 응답 필드는 모두 `required`(null 포함 항상 존재) |
| 예시 | 이 문서의 JSON 예시를 `@ExampleObject`로 넣지 않는다(스냅샷 크기 관리). 예시의 기준은 이 문서다 |


---

## 19. Project 모듈 (사이드 프로젝트 · 결정·장애 기록) · 코드 읽기 콘텐츠

Controller: `SideProjectController`(`/side-projects*`, `project` 모듈), `SideProjectNoteController`(`/side-projects/{id}/notes*`, `project` 모듈 — §19.8~§19.12), `ReadingController`(`/readings*`, `today` 모듈 — `CuratedReadingRegistry`를 읽는다, `03-system-architecture.md` §2.2). Service: `SideProjectService`, `SideProjectQueryService`(`today`·`coach`가 사용), `SideProjectNoteService`, `SideProjectNoteQueryService`(`evidence`가 사용).

사이드 프로젝트는 **학습이 적용될 대상**이다. 온보딩 마지막 단계에서 하나 만든다(SP-1, §4.1). 없으면 planner가 `PROJECT_TASK`를 제안하지 않는다.

`repo_url`은 **저장만 한다.** 서버는 이 URL을 요청하지 않는다(`07-security-and-privacy.md` §5.5 URL 필드 규칙, `requirement_doc.source_url`·`coach_finding.source_reference`와 같은 취급). 저장소 내용이 필요하면 사용자가 로컬에서 본다.

### 19.1 공통 view record

```java
public record SideProjectView(
        UUID id,
        String name,
        String description,          // null 가능
        String repoUrl,              // null 가능. 서버는 fetch하지 않는다
        String stack,                // null 가능
        SideProjectStatus status,    // ACTIVE | PAUSED | DONE (04 §3)
        SideProjectKind kind,        // SIDE | PAST_WORK (04 §3, I-23)
        Instant createdAt,
        Instant updatedAt,
        long version) {}
```

- 소유권: `sideProjectId`가 본인 것이 아니면 404 `RESOURCE_NOT_FOUND`(§1.1).
- `updated_at`은 `PATCH`로 값이 실제로 바뀔 때만 갱신한다. planner의 SP-3(가장 최근 `ACTIVE`·`kind = SIDE` 하나)이 이 값을 쓴다.
- `kind`는 분류다(`04-domain-model-and-db.md` §4.9). `SIDE`는 지금 만들고 있는 사이드 프로젝트, `PAST_WORK`는 예전에 한 일을 적어 두는 **경험 기록용** 프로젝트다. `PAST_WORK`에는 planner가 `PROJECT_TASK`를 제안하지 않고(SP-3, I-23), 기록(§19.8)·러버덕 `PROJECT_WORK` 대상·내보내기(§19.13)는 두 종류 모두에서 쓸 수 있다.

### 19.2 `POST /side-projects` — 프로젝트 등록

| 항목 | 값 |
|---|---|
| operationId | `projectCreate` |
| 인증 / IK | Bearer / IK |
| 요청 | `SideProjectCreateRequest` |
| 응답 | 201 `SideProjectView` |
| 오류 | 400 `VALIDATION_FAILED`(`URL`), 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S1 · FR-26, AC-27, SP-1 |

```java
public record SideProjectCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @Size(max = 500) String repoUrl,
        @Size(max = 300) String stack,
        SideProjectKind kind) {}          // 생략하거나 null이면 SIDE
```

```json
{ "name": "주문 시스템", "description": "회원가입 · 상품 · 주문 · 취소까지 직접 만드는 학습용 백엔드",
  "repoUrl": "https://github.com/example/order-service", "stack": "Spring Boot, PostgreSQL", "kind": "SIDE" }
```

- `repoUrl` 검사: `java.net.URI` 파싱 성공, scheme `http`/`https`, host 존재(`07-security-and-privacy.md` §5.5). 실패하면 400 `VALIDATION_FAILED`, field `repoUrl`, code `URL`.
- `name`·`description`·`stack`은 마스킹 후 저장한다(§1.11). `repoUrl`은 URL 필드라 마스킹 대상이 아니다.
- `status = ACTIVE`로 만든다. 사용자는 `ACTIVE`를 여러 개 가질 수 있다(SP-3). `kind`를 생략하면 `SIDE`다 — 온보딩이 만드는 첫 프로젝트도 `SIDE`다(§4.1, SP-1).
- 빈 문자열 `description`·`repoUrl`·`stack`은 `null`로 저장한다.

### 19.3 `GET /side-projects` — 목록

| 항목 | 값 |
|---|---|
| operationId | `projectList` |
| 인증 / IK | Bearer / — |
| query | `status`: `SideProjectStatus`, 선택(생략하면 전부) / `limit`, `cursor` (§1.5) |
| 정렬 | `updatedAt` DESC, `id` DESC |
| 응답 | 200 `CursorPage<SideProjectView>` |
| 오류 | 400 `UNKNOWN_ENUM_VALUE`, 400 `INVALID_CURSOR` |
| Sprint · 요구사항 | S1 · FR-26, AC-27 |

### 19.4 `GET /side-projects/{sideProjectId}` — 조회

| 항목 | 값 |
|---|---|
| operationId | `projectGet` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `SideProjectView` |
| 오류 | 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S1 · FR-26, AC-27 |

### 19.5 `PATCH /side-projects/{sideProjectId}` — 수정

| 항목 | 값 |
|---|---|
| operationId | `projectUpdate` |
| 인증 / IK | Bearer / — |
| 요청 | `SideProjectPatchRequest` |
| 응답 | 200 `SideProjectView` |
| 오류 | 400 `VALIDATION_FAILED`(`NOT_BLANK_IF_PRESENT`, `URL`), 400 `UNKNOWN_ENUM_VALUE`, 404 `RESOURCE_NOT_FOUND`, 409 `CONCURRENT_MODIFICATION`, 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S1 · FR-26, AC-27, SP-3 |

```java
public record SideProjectPatchRequest(
        @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @Size(max = 500) String repoUrl,
        @Size(max = 300) String stack,
        SideProjectStatus status,
        SideProjectKind kind,
        @NotNull Long version) {}
```

- PATCH 의미는 §1.1과 같다. `null`(또는 생략)은 "변경하지 않음"이다.
- 값을 지우려면 `description`·`repoUrl`·`stack`에 **빈 문자열**을 보낸다 → `null`로 저장한다. `name`은 지울 수 없다(공백만이면 400 `NOT_BLANK_IF_PRESENT`).
- `version`이 다르면 409 `CONCURRENT_MODIFICATION`(§1.6).
- 상태 전이에는 제약이 없다(`ACTIVE ↔ PAUSED ↔ DONE` 모두 허용). `DONE`으로 바꿔도 이미 만들어진 `PROJECT_TASK`는 그대로 남는다. planner는 다음 생성부터 그 프로젝트를 고르지 않는다(SP-3).
- `kind`도 같은 방식으로 바꿀 수 있다(`SIDE ↔ PAST_WORK`). `PAST_WORK`로 바꾸면 다음 생성부터 planner가 그 프로젝트를 `PROJECT_TASK` 대상으로 고르지 않고, 이미 있는 과제·기록은 그대로 남는다(I-23, `04-domain-model-and-db.md` §4.9).
- 실제로 바뀐 필드가 하나도 없으면 `updated_at`과 `version`을 그대로 둔다.

### 19.6 `DELETE /side-projects/{sideProjectId}` — 삭제

| 항목 | 값 |
|---|---|
| operationId | `projectDelete` |
| 인증 / IK | Bearer / — |
| 응답 | 204 (body 없음) |
| 오류 | 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S1 · FR-26, AC-27 |

- 행을 지운다. `learning_task.side_project_id`와 `coach_review.side_project_id`는 `on delete set null`이라 task·리뷰는 남고 참조만 끊긴다(`04-domain-model-and-db.md` §5).
- **그 프로젝트의 결정·장애 기록(§19.8)은 함께 삭제된다**(`on delete cascade`). 삭제 확인 화면에 이 사실을 알린다(`02` SCR-PROJECTS).
- `rubber_duck_session.target_id`에는 FK가 없으므로 지난 러버덕 세션은 그대로 남는다. 조회 시 `targetTitle`이 `null`이 된다(§9.5).
- 이미 삭제된 id로 다시 부르면 404다(멱등 204를 주지 않는다 — 소유권과 존재를 구분하지 않는 §1.1 규칙과 같다).

### 19.7 `GET /readings/{readingKey}` — 읽기 자료 조회 (코드 읽기 · 개념 읽기)

| 항목 | 값 |
|---|---|
| operationId | `todayGetReading` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `ReadingView` |
| 오류 | 400 `VALIDATION_FAILED`(`Pattern`), 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S3 · FR-27, AC-28, RC-1~RC-4 |

**두 종류를 한 endpoint가 돌려준다.** `learning_task.reading_key`가 `READ_CODE`의 **코드 읽기**(`curated-repos.yaml`, `19` §3.8)와 `READING`의 **개념 읽기**(`concept-readings.yaml`, `19` §3.13)를 한 칸에 담기 때문이다. key 하나가 어느 쪽인지는 `kind`로 알려 준다 — 클라이언트가 `READ.`·`DOC.` 접두사로 추측하지 않는다.

```java
public enum ReadingKind { CODE, CONCEPT }

public record ReadingView(
        String key,                  // 예: "READ.PETCLINIC.CONTROLLER_SLICE.001", "DOC.GIT.BRANCHING.001"
        ReadingKind kind,            // CODE = 저장소 코드 읽기, CONCEPT = 공식 문서 개념 읽기
        List<SkillRef> skills,       // skillCodes를 활성 skill로 해석한 것. 없는 code는 뺀다
        Integer estimatedMinutes,    // null 가능
        boolean retired,             // 은퇴한 단위(19 §8.2). true여도 조회는 된다
        CodeReadingView code,        // kind = CODE일 때만. 그 밖에는 null
        ConceptReadingView concept) {}  // kind = CONCEPT일 때만. 그 밖에는 null

public record CodeReadingView(
        CuratedRepoView repo,
        String path,                 // repo.subPath 기준 상대 경로 (19 §3.8). 로컬 파일은 <clone 위치>/<subPath>/<path>
        int startLine,
        int endLine,                 // startLine ≤ endLine
        String question,             // 읽고 답할 질문 (러버덕 대상이 된다)
        List<String> lookFor) {}     // 볼 지점 목록

public record ConceptReadingView(
        String title,                // 문서 제목 그대로. 카드의 "자료" 줄
        String url,                  // https. 새 탭으로 연다. 서버는 요청하지 않는다
        String publisher,            // 예: "PostgreSQL Global Development Group"
        String versionScope,         // 예: "PostgreSQL 16"
        String whyRead,              // 이 skill에서 무엇을 할 수 있게 되는지 (40~400자)
        List<String> checkPoints,    // 읽고 스스로 답할 것 정확히 3개 (06 §5.3 "핵심 3가지")
        LocalDate verifiedAt) {}     // 사람이 이 링크를 열어 확인한 날 (19 §3.13)

public record CuratedRepoView(
        String key,                  // 예: "petclinic"
        String name,
        String url,                  // 저장소 URL. 서버는 요청하지 않는다
        String subPath,              // 저장소 안의 하위 경로. 없으면 ""
        String license,              // 명시가 없으면 "UNSPECIFIED" (19 §3.8) — 그 저장소는 읽기만, 코드 인용 금지
        String licenseNote,          // null 가능. 예: "라이선스 명시 없음 — 읽기만"
        String stack,
        String why,                  // 이 저장소를 읽는 이유
        String cloneHint,            // 로컬로 가져오는 명령 한 줄 (RC-4)
        String pinnedCommit) {}      // 줄 번호의 기준 커밋 SHA. null 가능

```

`kind = CODE` (코드 읽기):

```json
{
  "key": "READ.PETCLINIC.CONTROLLER_SLICE.001",
  "kind": "CODE",
  "skills": [ { "id": "…", "code": "SPRING.MVC_REST", "name": "Spring MVC REST", "category": "SPRING" } ],
  "estimatedMinutes": 15,
  "retired": false,
  "code": {
    "repo": { "key": "petclinic", "name": "Spring PetClinic", "url": "https://github.com/spring-projects/spring-petclinic",
              "subPath": "", "license": "Apache-2.0", "stack": "Spring Boot 4.1, Java 17, Spring Data JPA",
              "why": "Spring 공식 샘플. 계층 구조와 테스트 작성법의 정석.",
              "cloneHint": "git clone https://github.com/spring-projects/spring-petclinic.git && cd spring-petclinic && git checkout 818c4136ea971c21674525f9053de0d9c7ad8cfe",
              "licenseNote": null,
              "pinnedCommit": "818c4136ea971c21674525f9053de0d9c7ad8cfe" },
    "path": "src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java",
    "startLine": 48, "endLine": 122,
    "question": "이 컨트롤러는 Repository를 직접 주입받고 Service 계층이 없습니다. 이렇게 두어도 괜찮은 경우와 곤란해지는 경우를 나눠서 설명해 보세요.",
    "lookFor": ["계층을 나누는 목적", "트랜잭션 경계가 어디에 생기는가", "지금 내 프로젝트는 어느 쪽에 가까운가"]
  },
  "concept": null
}
```

`kind = CONCEPT` (개념 읽기):

```json
{
  "key": "DOC.GIT.BRANCHING.001",
  "kind": "CONCEPT",
  "skills": [ { "id": "…", "code": "DEVOPS.GIT", "name": "Git 협업", "category": "DEVOPS" } ],
  "estimatedMinutes": 25,
  "retired": false,
  "code": null,
  "concept": {
    "title": "Pro Git — 3.2 Git Branching, Basic Branching and Merging",
    "url": "https://git-scm.com/book/en/v2/Git-Branching-Basic-Branching-and-Merging",
    "publisher": "Git",
    "versionScope": "Pro Git 2nd Edition (버전 없음, 2026-09-21 기준 내용)",
    "whyRead": "브랜치를 복사본이 아니라 커밋을 가리키는 이름으로 이해하게 된다. …",
    "checkPoints": [
      "fast-forward merge와 그렇지 않은 merge가 갈리는 조건을 적어 보세요",
      "충돌 표시의 위쪽과 아래쪽이 각각 어느 브랜치의 내용인지 적어 보세요",
      "작업 도중에 급한 수정을 끼워 넣어야 할 때 어떤 순서로 브랜치를 옮길지 적어 보세요"
    ],
    "verifiedAt": "2026-09-21"
  }
}
```

| 항목 | 규칙 |
|---|---|
| 출처 | `content/curated-repos.yaml`을 적재한 `CuratedReadingRegistry`와 `content/concept-readings.yaml`을 적재한 `ConceptReadingRegistry`(`03-system-architecture.md` §2.2·§3.2, `19-content-spec.md` §3.8·§3.13). 둘 다 기동 시 메모리 등록이고 DB 조회가 아니며 사용자별 데이터도 아니다 |
| 조회 순서 | key로 `CuratedReadingRegistry` → 없으면 `ConceptReadingRegistry`. 둘 다 없으면 404. **두 registry에 같은 key가 있으면 기동 실패**다(CV-120이 콘텐츠 단계에서 막는다) |
| 인증 | 필요하다. 하지만 **사용자 소유 리소스가 아니다** — 모든 사용자가 같은 내용을 본다. 권한 격리 catalog에는 `SCOPED_COLLECTION`이 아니라 공용 조회로 넣는다(`09-test-and-quality.md` §9.2 `GET /skills/tree`와 같은 취급) |
| `readingKey` 형식 | `^[A-Z0-9][A-Z0-9_.]{2,149}$`(두 종류를 모두 받는다). 어긋나면 400 `VALIDATION_FAILED`(field `readingKey`, code `Pattern`). 형식이 맞아도 registry에 없으면 404 `RESOURCE_NOT_FOUND`. **은퇴한 단위(`retired: true`)는 registry에 남아 있으므로 200**이다 — 지난 과제·러버덕 세션이 가리키는 단위를 계속 보여 준다(`19` §8.2) |
| **코드 본문·문서 본문** | **반환하지 않는다.** `CODE`는 파일 경로와 줄 범위만, `CONCEPT`은 제목과 링크만 준다. 서버는 `repo.url`도 `concept.url`도 fetch하지 않는다(`07-security-and-privacy.md` §5.5). 사용자가 `cloneHint`로 clone해 IDE로 읽거나 링크를 새 탭으로 연다 |
| 줄 번호 | `pinnedCommit` 기준이다. 저장소가 바뀌면 줄이 밀리므로 클라이언트는 `pinnedCommit`을 함께 보여준다(`19-content-spec.md`) |
| `verifiedAt` | 사람이 그 링크를 마지막으로 열어 본 날이다. 클라이언트는 그대로 보여 주기만 한다 — 오래됐다고 경고를 만들지 않는다(판단은 소스 점검에서 사람이 한다, `19` §8.5) |
| 쓰임 | `TaskView.readingKey`(§8.1)가 이 key를 쓴다. `READ_CODE`(`kind = CODE`)는 러버덕 `targetType = CODE_READING`(§9.5)의 대상이기도 하고 완료 조건이 러버덕 세션 1개다(RC-1, §8.4). `READING`(`kind = CONCEPT`)에는 러버덕 대상도 완료 조건도 없다 |
| AI | 이 endpoint는 AI를 호출하지 않는다. 비용 0이다 |

**기존 응답과의 차이** — S3에 구현된 `CuratedReadingView`는 `repo`·`path`·`startLine`·`endLine`·`question`·`lookFor`를 최상위에 두었다. 위 `ReadingView`는 그 여섯을 `code` 안으로 옮기고 `kind`를 더한다. 구현할 때 backend record와 app의 `reading_models.dart`, OpenAPI 스냅샷(`docs/api/openapi.yaml`)을 **같은 변경에서** 고친다.

### 19.8 프로젝트 기록 공통 (`side_project_note`)

Controller: `SideProjectNoteController`(`project` 모듈). Service: `SideProjectNoteService`, `SideProjectNoteQueryService`.

사이드 프로젝트에서 **무엇을 왜 골랐는지**(결정 기록)와 **무엇이 어떻게 깨졌고 어떻게 고쳤는지**(장애 기록)를 그때그때 남긴다(FR-29, PN-1~PN-4 = `06` §9.5). 파일 업로드는 없고 텍스트와 날짜, 선택 skill 하나뿐이다. AI를 호출하지 않는다.

```java
public record SideProjectNoteView(
        UUID id,
        UUID sideProjectId,
        SideProjectNoteType noteType,   // DECISION | INCIDENT (04 §3). 생성 후 바뀌지 않는다
        String title,
        LocalDate occurredOn,
        SkillRef skill,                 // null 가능 (§2.2 공통 skill 참조)
        String decisionChoice,          // DECISION일 때만, 그 외 null
        String decisionOptions,         // 〃
        String decisionRationale,       // 〃
        String incidentSymptom,         // INCIDENT일 때만, 그 외 null
        String incidentDetection,       // 〃
        String incidentFix,             // 〃
        String incidentPrevention,      // 〃
        Instant createdAt,
        Instant updatedAt,
        long version) {}
```

| 항목 | 규칙 |
|---|---|
| 소유권 | 경로의 `sideProjectId`가 본인 프로젝트가 아니면 404 `RESOURCE_NOT_FOUND`. 노트 조회·수정·삭제는 `(id, sideProjectId, userId)` 세 조건을 모두 쓴다 — 다른 프로젝트의 노트 id를 넣어도 404다 |
| 유형별 필수 | `DECISION`이면 `decisionChoice`·`decisionOptions`·`decisionRationale`이 모두 필요하고 `incident*`는 금지다. `INCIDENT`이면 반대다(I-22, PN-1). 어긋나면 400 `VALIDATION_FAILED`(빠진 필드는 `VALUE_REQUIRED`, 유형에 맞지 않는 필드는 `VALUE_NOT_ALLOWED`) |
| 상한 | `title` 1~200자, 본문 각 항목 1~4000자(§17). 앞뒤 공백을 제거한 뒤 검사한다 |
| `occurredOn` | 필수. 오늘(plan-day)보다 미래면 400 `VALIDATION_FAILED`(code `DATE_OUT_OF_RANGE`). 과거는 제한이 없다 |
| `skillCode` | 선택. 활성 skill code가 아니면 400 `VALIDATION_FAILED`(field `skillCode`, code `SKILL_CODE_UNKNOWN`). 기록은 학습 이벤트를 만들지 않고 skill 레벨을 바꾸지 않는다(PN-3) |
| masking | 모든 텍스트 항목이 저장 전에 `SecretMasker`를 통과한다(§1.11, PN-4). private key면 422 `SECRET_DETECTED_BLOCKED`이고 아무것도 저장하지 않는다 |
| 삭제 | 프로젝트를 지우면 그 프로젝트의 기록도 함께 사라진다(`on delete cascade`, `04` §8). §19.6의 삭제 확인 문구에 이 사실을 넣는다 |

### 19.9 `POST /side-projects/{sideProjectId}/notes` — 기록 추가

| 항목 | 값 |
|---|---|
| operationId | `projectCreateNote` |
| 인증 / IK | Bearer / IK |
| 요청 | `SideProjectNoteCreateRequest` |
| 응답 | 201 `SideProjectNoteView` |
| 오류 | 400 `VALIDATION_FAILED`(`VALUE_REQUIRED`, `VALUE_NOT_ALLOWED`, `DATE_OUT_OF_RANGE`, `SKILL_CODE_UNKNOWN`), 400 `UNKNOWN_ENUM_VALUE`, 404 `RESOURCE_NOT_FOUND`, 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S3 · FR-29, AC-33 |

```java
public record SideProjectNoteCreateRequest(
        @NotNull SideProjectNoteType noteType,
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDate occurredOn,
        @Size(max = 100) String skillCode,          // 선택
        @Size(max = 4000) String decisionChoice,
        @Size(max = 4000) String decisionOptions,
        @Size(max = 4000) String decisionRationale,
        @Size(max = 4000) String incidentSymptom,
        @Size(max = 4000) String incidentDetection,
        @Size(max = 4000) String incidentFix,
        @Size(max = 4000) String incidentPrevention) {}
```

```json
{
  "noteType": "DECISION",
  "title": "주문 번호를 UUID 대신 시퀀스 기반으로",
  "occurredOn": "2026-10-11",
  "skillCode": "DATABASE.INDEX",
  "decisionChoice": "yyyyMMdd + 일련번호 형식의 주문 번호를 쓰기로 했다.",
  "decisionOptions": "① UUID v4 ② UUID v7 ③ 날짜 + 시퀀스. UUID v4는 인덱스가 흩어지고, v7은 라이브러리가 필요했다.",
  "decisionRationale": "주문 목록을 날짜 범위로 조회하는 일이 가장 잦아서 클러스터링이 잘 되는 쪽을 골랐다. 대신 번호로 주문량이 드러나는 것은 감수한다.",
  "incidentSymptom": null, "incidentDetection": null, "incidentFix": null, "incidentPrevention": null
}
```

처리: Bean Validation(400) → 마스킹(422) → 프로젝트 조회 `(sideProjectId, userId)`(404) → 유형별 필수·금지 검사(400) → `occurredOn` 범위(400) → `skillCode` 조회(400) → INSERT. 빈 문자열은 앞뒤 공백 제거 후 `null`로 본다(유형에 필요한 항목이면 `VALUE_REQUIRED`).

### 19.10 `GET /side-projects/{sideProjectId}/notes` — 목록

| 항목 | 값 |
|---|---|
| operationId | `projectListNotes` |
| 인증 / IK | Bearer / — |
| query | `noteType`: `SideProjectNoteType`, 선택(생략하면 전부) / `limit`, `cursor` (§1.5) |
| 정렬 | `occurredOn` DESC, `id` DESC (`idx_side_project_note_project`) |
| 응답 | 200 `CursorPage<SideProjectNoteView>` |
| 오류 | 400 `UNKNOWN_ENUM_VALUE`, 400 `INVALID_CURSOR`, 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S3 · FR-29, AC-33 |

### 19.11 `GET /side-projects/{sideProjectId}/notes/{noteId}` · `PATCH …` — 조회·수정

| 항목 | 값 |
|---|---|
| operationId | `projectGetNote` / `projectUpdateNote` |
| 인증 / IK | Bearer / — |
| 요청 (PATCH) | `SideProjectNotePatchRequest` |
| 응답 | 200 `SideProjectNoteView` |
| 오류 | 400 `VALIDATION_FAILED`(`NOT_BLANK_IF_PRESENT`, `VALUE_REQUIRED`, `VALUE_NOT_ALLOWED`, `DATE_OUT_OF_RANGE`, `SKILL_CODE_UNKNOWN`), 404 `RESOURCE_NOT_FOUND`, 409 `CONCURRENT_MODIFICATION`, 422 `SECRET_DETECTED_BLOCKED` |
| Sprint · 요구사항 | S3 · FR-29, AC-33 |

```java
public record SideProjectNotePatchRequest(
        @Size(max = 200) String title,
        LocalDate occurredOn,
        @Size(max = 100) String skillCode,          // 빈 문자열이면 연결 해제
        @Size(max = 4000) String decisionChoice,
        @Size(max = 4000) String decisionOptions,
        @Size(max = 4000) String decisionRationale,
        @Size(max = 4000) String incidentSymptom,
        @Size(max = 4000) String incidentDetection,
        @Size(max = 4000) String incidentFix,
        @Size(max = 4000) String incidentPrevention,
        @NotNull Long version) {}
```

- `noteType`은 요청 record에 **없다.** 유형을 바꾸려면 지우고 다시 만든다(`04` §4.10, PN-2). body에 `noteType`을 넣으면 알 수 없는 속성이라 400 `MALFORMED_REQUEST`다(§1.1).
- PATCH 의미는 §1.1과 같다. `null`(또는 생략)은 "변경하지 않음"이다. 유형에 필요한 항목을 빈 문자열로 지우려 하면 400 `VALUE_REQUIRED`이고, 유형에 맞지 않는 항목을 채우면 400 `VALUE_NOT_ALLOWED`다(I-22는 어떤 경로로도 깨지지 않는다).
- `skillCode`에 빈 문자열을 보내면 `skill_id = null`이 된다.
- 실제로 바뀐 필드가 하나도 없으면 `updated_at`·`version`을 그대로 둔다(§19.5와 같은 규칙).

### 19.12 `DELETE /side-projects/{sideProjectId}/notes/{noteId}` — 삭제

| 항목 | 값 |
|---|---|
| operationId | `projectDeleteNote` |
| 인증 / IK | Bearer / — |
| 응답 | 204 (body 없음) |
| 오류 | 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S3 · FR-29, AC-33 |

- 행을 지운다. 이미 지운 id로 다시 부르면 404다(§19.6과 같은 규칙).

### 19.13 `GET /side-projects/{sideProjectId}/notes/export` — 기록 Markdown 내려받기

| 항목 | 값 |
|---|---|
| operationId | `projectExportNotes` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `text/markdown; charset=UTF-8`, `Content-Disposition: attachment; filename="notes-{sideProjectId}-{yyyyMMdd}.md"` (날짜 = 오늘 plan-day) |
| 오류 | 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S3 · FR-29, AC-33 |

- 경로는 `/side-projects/{sideProjectId}/notes` 아래의 리터럴이며 `{noteId}`(§19.11)보다 우선한다 — `export`는 UUID가 아니므로 충돌하지 않는다.
- 대상: 그 프로젝트의 모든 기록. 정렬은 `occurredOn` ASC, `id` ASC이고 페이징하지 않는다. 기록이 없어도 200이고 제목만 있는 문서를 돌려준다.
- 한 개의 read-only 트랜잭션에서 만든다(§3.3과 같은 기준). 감사 로그 `DATA_EXPORTED`를 남긴다. AI를 호출하지 않는다.
- 모든 텍스트는 저장된 **마스킹본**이다(§19.8). 서버는 내보내기에서 추가 가공을 하지 않는다.
- 문서 구조: 프로젝트 이름 제목 → 기록마다 `## {occurredOn} {title}` → 유형별 소제목(`DECISION`: 고른 것 / 선택지 / 이유, `INCIDENT`: 증상 / 발견 / 조치 / 재발 방지) → skill이 있으면 마지막 줄에 `기술: {skillCode}`.

```markdown
# 주문 시스템 — 결정·장애 기록

내보낸 날짜: 2026-11-02 · 기록 12건

## 2026-10-11 주문 번호를 UUID 대신 시퀀스 기반으로

- 유형: 결정 기록
- 고른 것: yyyyMMdd + 일련번호 형식의 주문 번호를 쓰기로 했다.
- 선택지: ① UUID v4 ② UUID v7 ③ 날짜 + 시퀀스. …
- 이유: 주문 목록을 날짜 범위로 조회하는 일이 가장 잦아서 …
- 기술: DATABASE.INDEX
```

---

## 20. 오늘의 팁 · 용어 사전 (콘텐츠)

Controller: `TipController`(`/tips*`, `today` 모듈 — 팁 registry를 읽고 `user_daily_tip`을 쓴다), `TermController`(`/terms*`, `review` 모듈 — 용어 registry를 읽고 복습 카드를 만든다). 두 registry는 `CuratedReadingRegistry`와 같은 방식으로 `content` 모듈이 기동 시 등록한다(`03-system-architecture.md` §2.2, `04-domain-model-and-db.md` §9).

팁·용어는 **콘텐츠다**(ADR-041). 본문은 DB에 없고 `content/tips/*.yaml`·`content/terms/*.yaml`에 있다(`19-content-spec.md`). DB에 남는 사용자별 상태는 `user_daily_tip`(어떤 팁을 언제 보여 줬고 무엇을 골랐는지)과 용어에서 만든 `review_item`뿐이다. **이 절의 endpoint는 AI를 호출하지 않는다.**

키 형식: `tipKey`는 `^TIP\.[A-Z][A-Z0-9_]*\.[A-Z][A-Z0-9_]*\.[0-9]{3}$`, `termKey`는 `^TERM\.[A-Z][A-Z0-9_]*\.[A-Z][A-Z0-9_]*$`(각 ≤ 120자, §17 — `19-content-spec.md` §3.9·§3.10과 같은 패턴이다). 어긋나면 400 `VALIDATION_FAILED`(field `tipKey`/`termKey`, code `Pattern`). 형식이 맞아도 registry에 없으면 404 `RESOURCE_NOT_FOUND`. 콘텐츠 형식과 검증기 규칙은 `19-content-spec.md`다.

### 20.1 공통 view record

```java
public record DailyTipView(
        String tipKey,
        TipSeries series,
        TipLevel level,
        String title,
        String symptom,              // 실제로 보게 되는 로그·오류 한 토막
        String cause,                // 왜 그런가
        String example,              // null 가능
        String whereToLook,          // 어디를 보면 되는가
        String experiment,           // 5분 안에 재현하는 방법. null 가능
        String sourceUrl,            // 공식 문서. null 가능 (experiment가 있으면 없을 수 있다)
        List<SkillRef> skills,       // skillCodes를 활성 skill로 해석한 것. 없는 code는 뺀다 (§19.7과 같은 규칙)
        int estimatedMinutes,
        LocalDate shownOn,           // user_daily_tip.shown_on
        TipFeedback feedback) {}     // 아직 고르지 않았으면 null

public record TipSummaryView(
        String tipKey,
        TipSeries series,
        TipLevel level,
        String title,
        String symptom,
        int estimatedMinutes,
        TipFeedback feedback) {}     // 이 사용자가 이미 고른 값. 없으면 null

public record TermView(
        String termKey,
        String representative,              // 대표 표기 하나 (저장소 전체가 이 표기를 쓴다, 19)
        String english,
        List<String> aliases,               // "이렇게도 부른다"
        String definition,                  // 한 문장
        String example,                     // 실무 예문 한 줄
        List<TermRefView> confusableWith,   // 헷갈리는 짝. 상세에서만 채운다
        List<SkillRef> skills,
        TipLevel level,                     // 팁과 같은 난이도 enum (04 §3)
        String sourceUrl,
        List<CreatedCardView> cards) {}     // 이 용어로 이미 만든 복습 카드. 없으면 []

public record TermRefView(String termKey, String representative, String english) {}

public record TermSummaryView(
        String termKey,
        String representative,
        String english,
        String definition,
        TipLevel level,
        boolean cardCreated) {}             // 이 용어의 복습 카드를 이미 만들었으면 true

public record CreatedCardView(
        UUID reviewItemId,
        String conceptKey,
        ReviewType reviewType,
        LocalDate dueDate) {}               // planDate(due_at)
```

### 20.2 `GET /tips/today` — 오늘의 팁

| 항목 | 값 |
|---|---|
| operationId | `todayGetDailyTip` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `DailyTipView` |
| 오류 | 404 `RESOURCE_NOT_FOUND`(더 보여 줄 팁이 없음) |
| Sprint · 요구사항 | S3 · BL-TIP-01~05 |

처리 (한 트랜잭션, AI 없음):
1. 오늘 plan-day를 계산한다(`06-learning-engine-rules.md` §2).
2. `user_daily_tip`에 `shown_on = 오늘`인 행이 있으면 그 팁을 그대로 반환한다(`feedback` 포함). **하루 1개**이므로 같은 날 다시 불러도 같은 팁이다.
3. 없으면 `06` §5.12의 선택 규칙으로 후보를 고른다(결정적, 제외 조건 포함). 후보가 하나도 없으면 404 `RESOURCE_NOT_FOUND`이고 아무것도 저장하지 않는다.
4. 고른 팁을 `user_daily_tip`에 INSERT한다(`tip_key`, `shown_on = 오늘`, `feedback = null`). 이 조회는 **표시 기록을 남기는 쓰기**다 — 그래야 하루 동안 같은 팁이 유지되고 같은 팁을 두 번 제안하지 않는다(I-26).
5. `TIP_VIEWED` 학습 이벤트를 남긴다(`04-domain-model-and-db.md` §6, 팁 `skillCodes`의 첫 활성 skill. 활성 skill이 없으면 `skill_id = null`).
6. 동시 요청으로 `user_daily_tip_unique`를 위반하면 그 행을 다시 읽어 같은 응답을 돌려준다(오류가 아니다).

- 팁 본문은 registry 최신본이다. 콘텐츠에서 은퇴한 팁(`retired: true`)이라도 이미 보여 준 팁이면 그대로 보여 준다(§19.7 은퇴한 reading과 같은 기준).

### 20.3 `POST /tips/{tipKey}/feedback` — 읽은 뒤 선택

| 항목 | 값 |
|---|---|
| operationId | `todaySubmitTipFeedback` |
| 인증 / IK | Bearer / IK |
| 요청 | `TipFeedbackRequest` |
| 응답 | 201 `DailyTipView` (처음 기록) / 200 `DailyTipView` (이미 기록돼 있음) |
| 오류 | 400 `VALIDATION_FAILED`(`Pattern`), 400 `UNKNOWN_ENUM_VALUE`, 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S3 · BL-TIP-01~05 |

```java
public record TipFeedbackRequest(
        @NotNull TipFeedback feedback) {}   // KNEW_IT | LEARNED | WILL_TRY (04 §3)
```

```json
{ "feedback": "LEARNED" }
```

처리 (한 트랜잭션, AI 없음):
1. `tipKey` 형식(400) → registry 조회(404).
2. 그 사용자의 `user_daily_tip` 행을 찾는다. **아직 보여 준 적 없는 팁이면 404** `RESOURCE_NOT_FOUND`다(읽기 전에 고를 수 없다).
3. `feedback`이 이미 있으면 덮어쓰지 않고 200으로 현재 값을 돌려준다(`04-domain-model-and-db.md` §4.11, §11.5와 같은 규칙). 없으면 저장하고 201이다.
4. 값에 따른 처리:
   - `LEARNED` — 같은 트랜잭션에서 복습 카드를 upsert한다. `concept_key = TIP:{tipKey}`, `source_type = TIP`, `origin = MANUAL`, `skill_id` = 팁 `skillCodes`의 첫 활성 skill. 문항 구성과 첫 due는 `06` §5.12 TIP-5를 따른다(기존 개념 카드 규칙을 그대로 쓴다 — 같은 `concept_key`의 활성 카드가 있으면 새로 만들지 않고 due만 당긴다, `06-learning-engine-rules.md` §6.3 마지막 행). 활성 skill이 하나도 없으면 카드를 만들지 않는다(`review_item.skill_id`는 not null).
   - `WILL_TRY` — 저장만 한다. 다음 plan-day부터 Today 응답에 25분짜리 실험 후보로 붙는다(`06` §5.12 TIP-6, §8.1 `tipExperiment`).
   - `KNEW_IT` — 저장만 한다. 이 팁은 다시 제안하지 않는다(I-26).
5. 학습 이벤트는 여기서 만들지 않는다 — `TIP_VIEWED`는 §20.2에서 이미 남겼다.

### 20.4 `GET /tips` — 팁 목록

| 항목 | 값 |
|---|---|
| operationId | `todayListTips` |
| 인증 / IK | Bearer / — |
| query | `series`: `TipSeries`, 선택 / `level`: `TipLevel`, 선택 / `limit`, `cursor` (§1.5 콘텐츠 목록) |
| 대상 | `retired = false`인 팁 전체 (사용자별 필터 없음) |
| 정렬 | `tipKey` ASC |
| 응답 | 200 `CursorPage<TipSummaryView>` |
| 오류 | 400 `UNKNOWN_ENUM_VALUE`, 400 `INVALID_CURSOR` |
| Sprint · 요구사항 | S3 · BL-TIP-01~05 |

- 콘텐츠 조회이므로 사용자 소유 리소스가 아니다 — 모든 사용자가 같은 목록을 본다(§19.7과 같은 취급). `feedback`만 요청 사용자의 값이다.
- 이미 본 팁도 목록에 남는다. 제외 규칙은 오늘의 팁 선택(§20.2)에만 적용한다.

### 20.4a `GET /tips/{tipKey}` — 팁 1건

| 항목 | 값 |
|---|---|
| operationId | `todayGetTip` |
| 인증 / IK | Bearer / — |
| path | `tipKey`: `^TIP\.[A-Z0-9_]+\.[A-Z0-9_]+\.[0-9]{3}$` |
| 응답 | 200 `DailyTipView` (§20.1 — 본문 전체와 요청 사용자의 `feedback`) |
| 오류 | 400 `VALIDATION_FAILED`(`Pattern`), 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S3 · BL-TIP-01~05 |

- **§20.4 목록에서 지난 팁을 여는 경로다.** 목록(`TipSummaryView`)에는 제목·증상 요약만 있어 본문(`cause`·`example`·`whereToLook`·`experiment`)을 받을 수 없다.
- 콘텐츠 조회라 **오늘의 팁이 아니어도 200**이다. 은퇴한 팁(`retired = true`)도 조회된다 — 이미 본 팁의 본문을 다시 열 수 있어야 하기 때문이다(`19` §8.2와 같은 취급). 제안에서만 빠진다(§20.2).
- `feedback`은 `user_daily_tip`에 행이 있으면 그 값, 없으면 `null`이다. **이 endpoint는 `user_daily_tip` 행을 만들지 않는다**(하루 1개 규칙은 §20.2만 쓴다, `06` §5.12 TIP-3).

### 20.5 `GET /terms` — 용어 검색

| 항목 | 값 |
|---|---|
| operationId | `reviewListTerms` |
| 인증 / IK | Bearer / — |
| query | `q`: 검색어, 선택(≤ 100자) / `skillId`: UUID, 선택 / `limit`, `cursor` (§1.5 콘텐츠 목록) |
| 정렬 | `termKey` ASC |
| 응답 | 200 `CursorPage<TermSummaryView>` |
| 오류 | 400 `VALIDATION_FAILED`(`TYPE_MISMATCH`, `Size`), 400 `INVALID_CURSOR` |
| Sprint · 요구사항 | S3 · BL-TRM-01~04 |

- 대상은 `retired = false`인 용어다. 은퇴한 용어는 검색되지 않지만 `GET /terms/{termKey}`로는 조회된다(`19-content-spec.md` §8.2, §19.7의 은퇴한 reading과 같은 기준).
- `q`는 `representative`·`english`·`aliases[]`에 대한 **부분 일치**이고 대소문자를 구분하지 않는다. 앞뒤 공백은 지우고 비교한다.
- `skillId`는 용어의 `skillCodes`가 그 skill을 담고 있는지로 거른다. 없는 `skillId`는 오류가 아니라 빈 목록이다(§10.2와 같은 규칙).
- 두 필터를 모두 주면 AND다. 둘 다 없으면 전체 목록이다.

### 20.6 `GET /terms/{termKey}` — 용어 1건

| 항목 | 값 |
|---|---|
| operationId | `reviewGetTerm` |
| 인증 / IK | Bearer / — |
| 응답 | 200 `TermView` |
| 오류 | 400 `VALIDATION_FAILED`(`Pattern`), 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S3 · BL-TRM-01~04 |

- `confusableWith`는 key 목록을 registry에서 펼쳐 `TermRefView`로 돌려준다. registry에 없는 key는 뺀다(콘텐츠 검증이 막지만 방어한다, `19-content-spec.md`).
- `cards`는 이 사용자가 이 용어로 만든 `review_item`이다(`concept_key`가 `TERM:{termKey}`로 시작하는 것, `concept_key` ASC). 없으면 `[]`.

### 20.7 `POST /terms/{termKey}/card` — 용어 복습 카드 만들기

| 항목 | 값 |
|---|---|
| operationId | `reviewCreateTermCard` |
| 인증 / IK | Bearer / IK |
| 요청 | body 없음 |
| 응답 | 201 `TermCardResponse` (이번에 만든 카드가 있음) / 200 `TermCardResponse` (이미 있어 새로 만들지 않음) |
| 오류 | 400 `VALIDATION_FAILED`(`Pattern`), 404 `RESOURCE_NOT_FOUND` |
| Sprint · 요구사항 | S3 · BL-TRM-01~04, FR-11 |

```java
public record TermCardResponse(
        String termKey,
        List<CreatedCardView> cards,   // 이 용어의 카드 전체 (이번에 만든 것 + 이미 있던 것), conceptKey ASC
        int createdCount) {}           // 이번 요청으로 새로 만든 카드 수
```

- **양방향 2장**을 만든다. `review_type = RECALL`, `source_type = TERM`, `origin = MANUAL`, `source_id = null`, `skill_id` = 용어 `skillCodes`의 첫 활성 skill.
  - 정방향 `concept_key = TERM:{termKey}` — 대표 표기를 보고 뜻을 말한다.
  - 역방향 `concept_key = TERM:{termKey}:REVERSE` — 뜻을 보고 대표 표기를 말한다. `(user_id, concept_key)`가 유일해야 하므로(I-06) 키를 나눈다.
- 문항은 콘텐츠에서 만든다: 정방향 `prompt` = 대표 표기(+ `english`), `expected_answer` = `definition` + `example`. 역방향은 `prompt` = `definition`, `expected_answer` = 대표 표기(+ `aliases`). `rubric_json`은 항목 1개(`{"id":"R1","criterion":"대표 표기와 뜻을 짝지어 말한다"}`)다 — 복습 화면의 `CONCEPT_HINT`가 rubric 첫 항목을 쓰기 때문이다(§11.5).
- 첫 `due_at`은 `planDayStart(today + 1)`, `interval_days = 1`이다(`06-learning-engine-rules.md` §6.3 "수동 생성" 행).
- 이미 있는 `concept_key`는 새로 만들지 않고 그대로 둔다(문항을 덮어쓰지 않는다, §11.5). 두 장 모두 있으면 `createdCount = 0`이고 200이다.
- 활성 skill이 하나도 없는 용어(은퇴한 skill만 가리키는 경우)는 카드를 만들지 않고 200 + `cards: []`, `createdCount = 0`을 돌려준다.
- 새로 만든 카드가 있으면 `TERM_CARD_CREATED` 학습 이벤트를 1건 남긴다(`04-domain-model-and-db.md` §6).
- 자유 텍스트 입력이 없으므로 마스킹 대상이 아니다(§1.11).

## 21. Lesson 모듈 (개념 노트 · 학습 단위)

Controller: `LessonController`(`/lessons*`, `today` 모듈). 본문은 DB에 없고 `content/lessons/*.yaml`에 있다(`19-content-spec.md` §3.14). `LessonRegistry`는 `content` 모듈이 기동 시 등록한다(`CuratedReadingRegistry`와 같은 방식).

**이 절의 endpoint는 AI를 호출하지 않는다.** 예측·빈칸은 서버가 문자열로 채점하고, 백지 문제는 채점하지 않는다 — 모범 답안과 확인 목록을 돌려주고 사용자가 스스로 견준다(`01` 원칙 9 *No IDE reinvention*: 서버는 사용자 코드를 실행하지 않는다).

키 형식: `lessonKey`는 `^LESSON\.[A-Z][A-Z0-9_]*(\.[A-Z][A-Z0-9_]*)*\.[0-9]{3}$`, `unitKey`는 `^<lessonKey>\.U[0-9]{1,2}$`(각 ≤ 140자, §17). 어긋나면 400 `VALIDATION_FAILED`(code `Pattern`). 형식이 맞아도 registry에 없으면 404 `RESOURCE_NOT_FOUND`. 은퇴한 노트(`retired: true`)도 조회는 된다 — 기록이 그 key를 가리키기 때문이다(`19` §8.2).

### 21.1 공통 view record

```java
public record LessonView(
        String lessonKey,
        String skillId,              // UUID. registry가 skillCode로 찾아 채운다
        String skillCode,
        String skillName,
        String title,
        String whyItMatters,
        String oneLine,
        List<LessonUnitView> units,
        List<String> commonMistakes,
        String inProject,
        List<LessonSourceView> sources,
        List<LessonSourceView> readMore,
        boolean retired) {}

public record LessonUnitView(
        String unitKey,
        String title,
        int minutes,
        boolean core,
        String explain,             // 마크다운
        LessonExampleView example,
        LessonQuestionView predict,  // answer·explanation 없음
        LessonQuestionView complete, // answers·explanation 없음
        LessonProblemView problem,   // modelAnswer·selfChecks 없음
        List<String> prerequisiteUnits,
        UnitProgressView progress) {} // 그 사용자의 진행. 기록이 없으면 null

public record LessonExampleView(String language, String code, String output, String note) {}

/** 문항. 정답은 채점 응답에만 들어간다. */
public record LessonQuestionView(String question, String code, List<String> choices, int blanks) {}

/** 백지 문제. hints는 단계별로 받는다(앱이 하나씩 연다) — 모범 답안은 제출해야 온다. */
public record LessonProblemView(
        String prompt, List<String> deliverables, String starterCode, List<String> hints) {}

public record LessonSourceView(String title, String url, String versionScope) {}

public record UnitProgressView(
        boolean solved, HelpLevel helpLevel, Instant solvedAt, Integer selfChecksMet) {}
```

`HelpLevel`(`today.domain`): `NONE` < `HINT` < `DUCK` < `ANSWER`. 첫 학습에서 **예제를 다시 보는 것은 도움으로 치지 않는다**(방금 본 것이다). 다시 풀기에서는 노트를 여는 것도 도움이다 — 그 값은 복습 쪽에서 정한다(`06` §6).

### 21.2 `GET /api/v1/lessons/{lessonKey}`

노트 하나와 단위 전부. **정답을 담지 않는다** — `predict.answer`, `complete.answers`, `problem.modelAnswer`, `problem.selfChecks`는 채점·제출 응답에만 들어간다. 그래서 이 응답을 캐시해도 답이 새지 않는다.

- 200 `LessonView`. 다른 사용자의 자원이 아니므로 소유권 검사는 없다(콘텐츠다). 진행(`progress`)만 호출한 사용자 것이다.
- 404 `RESOURCE_NOT_FOUND` — registry에 없는 key

### 21.3 `GET /api/v1/skills/{skillId}/lesson`

그 skill의 노트. 화면에서 skill → 노트로 바로 가는 길이다.

- 200 `LessonView`, 404 `RESOURCE_NOT_FOUND`(그 skill에 노트가 없다)

### 21.4 `POST /api/v1/lessons/{lessonKey}/units/{unitKey}/predict`

출력 예측 채점. `Idempotency-Key` 불필요 — 상태를 바꾸지 않는다(§2.6의 예외 목록에 넣는다).

```json
{ "answer": "index" }
```

- `answer` 1~200자 필수
- 200 `{ "correct": true, "expected": "index", "explanation": "..." }`
- 채점 규칙(`19` §3.14): 앞뒤 공백을 버리고 연속 공백을 하나로 줄인 뒤 **대소문자를 구분해** 비교한다. `choices`가 있으면 고른 값과 비교한다
- 404 `RESOURCE_NOT_FOUND`

### 21.5 `POST /api/v1/lessons/{lessonKey}/units/{unitKey}/complete`

빈칸 채우기 채점. 상태를 바꾸지 않는다.

```json
{ "answers": ["@GetMapping", "/orders"] }
```

- `answers`는 그 단위의 빈칸 수와 같아야 한다. 다르면 400 `VALIDATION_FAILED`(field `answers`, code `Size`)
- 200 `{ "correct": false, "results": [true, false], "expected": ["@GetMapping", "/orders"], "explanation": "..." }`

### 21.6 `GET /api/v1/lessons/{lessonKey}/units/{unitKey}/answer`

모범 답안과 확인 목록. **백지 문제를 낸 다음에 보는 것**이고 서버는 채점하지 않는다 — 사용자가 자기 IDE에서 돌려 본 답을 이것과 스스로 견준다(`01` 원칙 9).

- 200 `{ "modelAnswer": "...", "selfChecks": ["...", "..."] }`
- 상태를 바꾸지 않는다. 기록은 §21.7에서 한다
- **사용자가 쓴 답은 서버로 보내지 않는다.** 보낼 이유가 없다(채점하지 않는다) — 저장하지 않을 것을 받지도 않는다(`03` §11)
- 404 `RESOURCE_NOT_FOUND`

### 21.7 `POST /api/v1/lessons/{lessonKey}/units/{unitKey}/finish`

단위 한 바퀴를 마쳤다. `Idempotency-Key` 필수(§2.6).

```json
{ "helpLevel": "HINT", "selfChecksMet": 2 }
```

- `helpLevel` 필수. **앱이 센 값이다** — 무엇을 열었는지 서버가 추적하지 않는다. 이 값은 복습 일정에만 쓰고 레벨에는 쓰지 않으므로(`01` 원칙 4) 자기 보고로 충분하다
- `selfChecksMet` 선택. 0 ~ 그 단위의 `selfChecks` 개수. 견주지 않고 넘어갔으면 생략한다. 범위를 벗어나면 400 `VALIDATION_FAILED`
- 200 `{ "unitKey": "...", "helpLevel": "HINT", "selfChecksMet": 2, "recordedAt": "..." }`
- 기록: `learning_event` `UNIT_SOLVED` 1건(`04` §6). `skill_id`는 노트의 skill
- 같은 단위를 다시 마치면 이벤트가 하나 더 쌓인다(다시 풀기). dedupe 하지 않는다

### 21.8 오류 코드

| 상황 | 상태 | code |
|---|---|---|
| key 형식 위반 | 400 | `VALIDATION_FAILED` |
| registry에 없음 | 404 | `RESOURCE_NOT_FOUND` |
| `answers`·`met` 개수 불일치 | 400 | `VALIDATION_FAILED` |
| 토큰 없음 | 401 | `UNAUTHORIZED` |

---

