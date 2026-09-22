# 12. Acceptance Criteria

> Status: Accepted (v2) · Last updated: 2026-09-19 · Related: `01-product-requirements.md`, `05-api-spec.md`, `06-learning-engine-rules.md`, `09-test-and-quality.md`, `11-development-roadmap.md`, `13-product-backlog.md`
>
> 2026-09-20 반영: AC-31 재현 과제(FR-28), AC-32 학습 트랙 2종(FR-03), AC-33 프로젝트 결정·장애 기록(FR-29)을 추가했다. AC-09에 독립 구현 증거(재현 과제)를, AC-12에 재현 잠금이 AI 불가와 다르다는 것을, AC-14에 프로젝트 기록 마스킹을 더했다.
>
> 2026-09-20 반영(ADR-041·042, DEC-34): **AC-34 오늘의 팁 · AC-35 용어 사전 · AC-36 주간 요약과 연속 학습 일수**를 추가했다(모두 S3). AC-02에 과제 카드의 `whyItMatters`·확인 목록(S11), AC-09에 학습 단계 6칸과 `GET /skills/{skillId}`(S7), AC-26에 지시어 세기(S15), AC-27에 경험 기록 분류 `kind`(S11), AC-33에 기록 Markdown 내보내기(S11)를 더했다. **AC-12 S7의 배너 범위를 `02` §6.5에 맞췄다 — Today·복습·계획 화면에는 배너를 띄우지 않는다.** `SkillCategory`가 14개가 되어 AC-11 S2의 `selfAssessments` 경계를 15개로 고쳤다. **AC-22(로드맵 비교)는 S7 → S3**이고 AC-14의 로드맵 비교 원문 마스킹도 S3이다.
>
> 2026-09-18 v3 반영: AC-26 러버덕, AC-27 사이드 프로젝트, AC-28 코드 읽기, AC-29 교차 학습, AC-30 기한 역산 확장 제안을 추가했다. AC-11에 진단 우선(`runDiagnostic`)·사이드 프로젝트(`sideProject`), AC-03을 S5 → S2로 옮겼다(budget·risk가 Today와 같은 단계). Sprint 열은 날짜 없는 단계 ID다(M1 = S0~S3, S3 완료 = 실사용 시작 / M2 = S4~S7, `13-product-backlog.md` §2).
>
> 기능 완료를 판단하는 기준이다. 각 AC의 시나리오는 자동 테스트(또는 표시된 수동 절차)로 그대로 옮긴다. 오류 코드는 `05-api-spec.md` 카탈로그, enum은 `04-domain-model-and-db.md` §3, 규칙은 `06-learning-engine-rules.md`가 기준이다.

---

## 0. 읽는 법

| 항목 | 규칙 |
|---|---|
| 공통 전제 | 별도 표시가 없으면: `Asia/Seoul`, `dayStartHour = 4`, 온보딩 완료 사용자, `MutableClock` 고정 시각, `devpilot.*` 기본 설정(`03-system-architecture.md` §9), 모든 인증 `POST`에 새 `Idempotency-Key` |
| 사용자 | `A`, `B` = allowlist에 있는 서로 다른 사용자. `X` = allowlist에 없는 사용자 |
| 시각 표기 | `D` = 테스트 시점의 plan-day. `start(D)` = `planDayStart(D, Asia/Seoul, 4)` |
| AI | 자동 테스트는 `FakeAiProvider`(fixture) 또는 `DisabledAiProvider`만 쓴다. 실제 모델 확인은 eval(`17-ai-integration.md`)로 한다 |
| 검증 수준 | `unit`(순수 Java, vector) · `integration`(Spring + Testcontainers, 서비스·리포지토리) · `API E2E`(실행 중인 앱 + HTTP) · `UI`(Flutter widget / integration_test) · `manual ops`(prod 환경에서 사람이 확인) |
| 테스트 클래스 | `09-test-and-quality.md` §3.1 명명을 따른다: 단위 `<ProductionClass>Test`, 통합 `<ProductionClass>IntegrationTest`, E2E `<Flow>FlowTest`, 아키텍처 `<Topic>ArchTest`, 모듈을 가로지르는 보안 테스트는 `07-security-and-privacy.md` §16 이름. 모든 클래스 이름은 `Test`로 끝나고 단위·통합 구분은 JUnit 태그로 한다(`*IT` 접미사 없음). 두 문서에 이름이 없는 테스트는 `<Feature><Scenario>Test`. Flutter는 `*_test.dart`. 추적은 테스트 메서드 이름 또는 `@DisplayName`에 `AC-xx Sn`을 넣는다 |
| Sprint | 해당 AC를 처음 통과시켜야 하는 단계(`S0`~`S7` — 기간·날짜가 없는 구현 순서 ID, `13-product-backlog.md` §2). 이후 단계에서 회귀 테스트로 유지한다. **M1 = S0~S3**(S3 완료 = 실사용 시작), **M2 = S4~S7** |
| 예시 날짜 | Given의 날짜(AC-01 S1의 `targetCompletionDate`, AC-03의 D 등)는 계산을 확인하기 위한 **예시 입력값**이다. 실제 목표일은 사용자가 설정창에 등록한다(`learning_goal.target_completion_date`) |

## 1. 요약

묶음 열: **M1**(S0~S3 — 실사용 시작 전에 통과), **M2**(S4~S7). 한 AC가 두 묶음에 걸치면 `M1 → M2`로 적고, M2 부분은 Sprint 열의 괄호 안 단계다.

| AC | 제목 | 관련 요구사항 | Sprint | 묶음 |
|---|---|---|---|---|
| AC-01 | 목표·계획 저장과 버전 이력 | FR-03, FR-04 | S1 (이벤트 S2) | M1 |
| AC-02 | Today 생성 | FR-07, FR-08, FR-16, FR-21 | S2 (CHALLENGE·READ_CODE 제안 S3, `whyItMatters`·확인 목록 S3) | M1 |
| AC-03 | Deadline risk | FR-05 | S2 (v3에서 S5 → S2) | M1 |
| AC-04 | Challenge | FR-09 | S3 (AI 생성 S5) | M1 → M2 |
| AC-05 | Memory 스케줄 | FR-11 | S2 (challenge·러버덕·팁·용어 카드 S3, coach S4) | M1 → M2 |
| AC-06 | Project Coach | FR-12 | S4 | M2 |
| AC-07 | Verification | FR-14 | S4 | M2 |
| AC-08 | 보안 격리·오류 노출 금지 | FR-01, NFR-04 | S1~S7 (endpoint 추가마다), 전체 재검증 S6 | M1 → M2 |
| AC-09 | Learning state 갱신 | FR-06 | S1 (planning level), S3 (updater, 러버덕 설명 증거, **학습 단계 6칸**), S4 (재현 과제 독립 구현 증거) | M1 → M2 |
| AC-10 | 모바일 복습 | FR-11, NFR-08 | S2 | M1 |
| AC-11 | 온보딩·진단 제안 | FR-02, FR-15, FR-26 | S1 (사이드 프로젝트 포함, seed 카드·snapshot S2, 진단 S3) | M1 |
| AC-12 | AI 불가 시 동작 | NFR-03, FR-22 | S3 (러버덕·코드 읽기 포함), S4 재검증(재현 과제는 AI 없이 그대로 동작), (challenge 생성 S5) | M1 → M2 |
| AC-13 | AI 예산 가드 | FR-22, NFR-01 | S3 | M1 |
| AC-14 | Secret masking | FR-12, NFR-05 | S3 (training·review·session·사이드 프로젝트·프로젝트 기록·러버덕·설명 기록·로드맵 비교), S4 (coach) | M1 → M2 |
| AC-15 | Export·계정 삭제 | FR-23, NFR-05 | S6 | M2 |
| AC-16 | Hint Ladder 정책 | FR-10 | S3 (challenge), S4 (coach) | M1 → M2 |
| AC-17 | 날짜 경계 | FR-07, FR-24, NFR-09 | S2 | M1 |
| AC-18 | Allowlist | FR-01, NFR-04 | S1 | M1 |
| AC-19 | Coach self-review·thinking pattern | FR-12, FR-13 | S4 (추세 S5) | M2 |
| AC-20 | Data API 비노출 | NFR-04 | S0 (S1·S6 재확인) | M1 → M2 |
| AC-21 | Evidence·Weekly | FR-17, FR-18 | S5 (weekly), S6 (evidence) | M2 |
| AC-22 | 로드맵 비교 | FR-19 | S3 (S7 → S3, `matchedEvidence`는 S6에 재확인) | M1 |
| AC-23 | Idempotency | NFR-01, NFR-04 | S3 (헤더 검사 S1), S4·S5 재검증 | M1 → M2 |
| AC-24 | Plan 일관성(동시 replan) | FR-04 | S1 | M1 |
| AC-25 | devtoken 인증 | FR-01, NFR-04 | S0 | M1 |
| AC-26 | 러버덕 | FR-25 | S3 (지시어 세기 포함) | M1 |
| AC-27 | 사이드 프로젝트 | FR-26, FR-02 | S1 (`PROJECT_TASK` 연결 S2, 마스킹·러버덕 대상·경험 기록 분류 S3, Coach 대상 S4) | M1 → M2 |
| AC-28 | 코드 읽기 | FR-27, FR-07 | S3 | M1 |
| AC-29 | 교차 학습 | FR-11 | S2 | M1 |
| AC-30 | 기한 역산 확장 제안 | FR-05 | S2 | M1 |
| AC-31 | 재현 과제 (AI 없이 다시 만들기) | FR-28 | S4 | M2 |
| AC-32 | 학습 트랙 2종 | FR-03, FR-02 | S3 | M1 |
| AC-33 | 사이드 프로젝트 결정·장애 기록 | FR-29 | S3 (기록 CRUD·Markdown 내보내기. 증거 초안 S6, 지표 S5) | M1 → M2 |
| AC-34 | 오늘의 팁 | FR-07, FR-11 | S3 | M1 |
| AC-35 | 용어 사전 | FR-11 | S3 | M1 |
| AC-36 | 주간 요약 · 연속 학습 일수 | FR-16 | S3 | M1 |
| AC-37 | 개념 익히기 (개념 노트 · 학습 단위) | FR-07 | S3 | M1 |

---

## AC-01 목표·계획 저장과 버전 이력

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-03, FR-04 |
| Sprint | S1 (`PLAN_REPLANNED` 이벤트 확인은 S2) |
| 검증 수준 | API E2E, UI(데모) |
| 테스트 클래스 | `LearningGoalServiceIntegrationTest`, `ReplanServiceIntegrationTest`, `ReplanFlowTest` |

**S1. 저장 후 재조회**
- Given A가 온보딩에서 `targetRole = JAVA_BACKEND`, `targetCompletionDate = 2027-04-01`, `useTemplate = true`로 plan v1을 만들었다
- When A가 새 토큰으로 `GET /learning-goal`, `GET /plans/active`를 호출한다
- Then `GET /learning-goal`의 `targetRole`·`targetCompletionDate`(목표일)·focus skill이 온보딩 요청값과 같고(날짜 필드는 목표일 하나다), milestone 목록(title, startDate, endDate, priority, status, skillCodes)이 온보딩 직후 첫 `GET /plans/active` 응답과 필드 단위로 같다(온보딩 응답의 `PlanSummaryView`에는 milestone이 없다). `planVersion = 1`, `status = ACTIVE`
- And milestone 날짜는 `19-content-spec.md` §5의 창 하나 `[D, targetCompletionDate]` 배치와 같다: 9개가 템플릿 순서이고 마지막 milestone "설명과 정리"(`CONSOLIDATION`)가 맨 뒤다. D가 목표일보다 63일 이상 앞이면(SEQUENTIAL) 첫 milestone `startDate = D`, 마지막 milestone `endDate = targetCompletionDate`, 빈 날·겹침이 없다(`19` §5.4 V1·V2·V5·V8과 같은 규칙)

**S2. in-place 수정은 새 버전을 만들지 않는다**
- When `PATCH /plans/{v1}/milestones/{m1}` `{ "status": "IN_PROGRESS", "version": 0 }`
- Then 200, milestone `version = 1`. `GET /plans` 항목 1개, `planVersion = 1`

**S3. 구조 변경은 새 버전이다**
- When `POST /plans/{v1}/replan` `{ reason, version, milestones: 기존 전체 + 새 milestone 1개, acceptedDeferrals: [], acceptedTargetReductions: [], restoredDeferrals: [] }`
- Then 201, 새 plan `planVersion = 2`, `status = ACTIVE`, `supersedesPlanId = v1.id`
- And `GET /plans`는 2건: v2 `ACTIVE`, v1 `SUPERSEDED`(`supersededAt` 있음)
- And `GET /plans/{v1.id}`의 milestone 수·필드가 replan 전과 같다(이전 버전 불변)
- And `milestoneIdMapping` 길이 = 요청 중 `id`가 있던 milestone 수, 새 milestone id는 모두 이전 id와 다르다
- And DB에서 A의 `status = 'ACTIVE'` plan은 1행
- And (S2 이후) `PLAN_REPLANNED` learning event 1건, payload `fromVersion = 1`, `toVersion = 2`

**S4. 이전 버전은 수정할 수 없다**
- When v2 생성 후 `PATCH /plans/{v1}/milestones/{m1}` 또는 `POST /plans/{v1}/replan`
- Then 409 `PLAN_NOT_ACTIVE`, DB 변경 없음

**S5. 목표 수정과 동시성**
- When `PUT /learning-goal`에 이전 `version`을 보낸다 → 409 `CONCURRENT_MODIFICATION`, 값 변경 없음
- When `targetCompletionDate`를 바꾼 `PUT /learning-goal`(최신 version) → 200, 활성 plan `replanRecommended = true`, `planVersion` 변화 없음
- When `targetCompletionDate = D`(오늘) 또는 `D + 3년 + 1일` → 400 `VALIDATION_FAILED`, `errors[].code = DATE_OUT_OF_RANGE`(field `targetCompletionDate`), 값 변경 없음. `D + 1일`과 `D + 3년`은 200(경계 포함)
- When 요청 body에 `LearningGoalUpdateRequest`에 없는 속성이 있다 → 400 `MALFORMED_REQUEST`

---

## AC-02 Today 생성

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-07, FR-08, FR-16, FR-21 |
| Sprint | S2 (CHALLENGE·READ_CODE 제안 연결과 `whyItMatters`·확인 목록은 S3. `PROJECT_TASK`와 사이드 프로젝트는 AC-27 S7, `READ_CODE`는 AC-28, 연속 학습 일수·주간 요약은 AC-36) |
| 검증 수준 | unit(vector), API E2E, UI |
| 테스트 클래스 | `PlannerScoringTest`, `TaskProposalPolicyTest`, `TimeAllocatorTest`, `ReasonTemplatesTest`, `ComebackModePolicyTest`, `TodayPlanServiceIntegrationTest`, `TodayChallengeProposalAiDisabledTest`, `LearningSessionServiceIntegrationTest`, `DashboardQueryServiceIntegrationTest`, `TaskChecklistQueryServiceIntegrationTest`, `OnboardingToTodayFlowTest`, `today_screen_test.dart` |

공통 Given: A의 활성 plan에 후보 skill이 1개 이상 있다(모든 target 충족이면 `mainTask = null`이 정상 응답이며 이 AC의 main 관련 Then은 적용하지 않는다). risk `LOW`, AI provider `disabled`.

**S1. 30분 · NORMAL**
- Given due review 3장, D의 daily plan 없음
- When `POST /today/generate` `{ "availableMinutes": 30, "energyLevel": "NORMAL" }`
- Then 성공 응답, D의 `daily_plan` 1행(`plan_date = D`, `generation_count = 1`)
- And `is_main = true` task 정확히 1개(`PLANNED`), `REVIEW` task 1개(`is_main = false`, `sort_order = 0`, `estimated_minutes = 5`)
- And 모든 task `estimated_minutes` 합 ≤ `floorDiv(30 × 11_000, 10_000)` = **33**, main `estimated_minutes` ≤ 27
- And main `reason_codes` 1~3개, 모두 `ReasonCode` 값
- And `score_breakdown.plannerVersion = "RULE_V1"`, factor는 0~1,000,000 정수, `finalScore`는 정수
- And `ai_call_log` 새 행 0

**S2. 결정적 규칙 vector**
- `06` §5.6 표 6행(reviewMinutes·mainBudget·limit)과 추가 과제 표 5행(E-1~E-5), §5.7 표 10행(A/B 선택 — 7~10행은 `MONOTONY_*`), §5.8 최소 1개 보장이 모두 통과한다

**S3. 입력 경계**
- `availableMinutes` 4 또는 721 → 400 `VALIDATION_FAILED`
- `energyLevel = "TIRED"` → 400 `UNKNOWN_ENUM_VALUE`
- `availableMinutes = 5`, due 4장 → reviewMinutes 0이므로 `REVIEW` task 0개, main = `RECALL`, `estimated_minutes = 5` (`06` §5.6)

**S4. 재생성 (`06` §5.9)**

| Given (D의 active main) | 요청 | Then |
|---|---|---|
| `PLANNED` | `force = false` | 성공, `generation_count = 2`, 이전 `PLANNED` task 0행 남음, active main 1개 |
| `IN_PROGRESS` | `force = false` | 409 `TODAY_ALREADY_STARTED`, task 변경 없음 |
| `IN_PROGRESS` | `force = true` | 기존 main `DEFERRED`, 새 main `PLANNED` 1개 |
| `COMPLETED` (active main 없음) | `force = false` | 409 `TODAY_ALREADY_COMPLETED` |
| `COMPLETED` | `force = true` | 기존 main `COMPLETED` 유지, 새 main `PLANNED` 1개 |

**S5. WIP = 1**
- When 같은 D에 서로 다른 `Idempotency-Key`로 `POST /today/generate` 2개를 동시에 보낸다
- Then D의 main 중 `PLANNED`/`IN_PROGRESS`는 항상 1개(`uq_learning_task_one_active_main`)
- When active main이 있는 상태에서 `SKIPPED` task를 `PLANNED`로 PATCH → 409 `INVALID_STATE_TRANSITION`

**S6. 선행 조건**
- 온보딩 전 `POST /today/generate` → 409 `ONBOARDING_REQUIRED`
- 활성 plan 없음(fixture로 plan `ARCHIVED`) → 404 `PLAN_NOT_FOUND`
- D에 daily plan이 없을 때 `GET /today` → 404 `TODAY_NOT_GENERATED`

**S7. 복귀 모드 (FR-21)**
- Given 마지막 `COMPLETED` 세션의 `plan_date = D − 4`, `D − 3 ~ D − 1`에 `COMPLETED` 세션 없음, due 15장, 전날 main 없음, energy `NORMAL`
- Then `daily_plan.comeback_mode = true`, REVIEW task의 due 수 ≤ 10, main `reason_codes`에 `COMEBACK_EASY_START`
- And 후보가 difficulty 3 `PROJECT_TASK` 1개뿐인 fixture(A에게 `ACTIVE` 사이드 프로젝트가 있다 — SP-1)에서 main `score_breakdown.modifiers`에 `COMEBACK_HARD_TASK`(7000bp)가 있다
- Given `COMPLETED` 세션 이력이 전혀 없음 → `comeback_mode = false`

**S8. AI 상태와 CHALLENGE 제안 (S3)**
- Given 후보 skill이 1개이고, 그 skill에 14 plan-day 안에 시도하지 않은 VALIDATED PRACTICE challenge(difficulty = `06` §5.3의 d)가 있다
- When `aiStatus = ENABLED`이면 main `task_type = CHALLENGE`, `challenge_id` 설정
- When `aiStatus = DISABLED`이면 CHALLENGE task를 만들지 않고, 완료 조건이 러버덕인 `READ_CODE`도 건너뛰어 `06` §5.3의 다음 규칙(READING/PROJECT_TASK/EXPLAIN)을 쓴다(AC-28 S1 T-3)

**S9. 학습 세션 (FR-08)**
- When `POST /learning-sessions` `{ learningTaskId: main }` → `IN_PROGRESS`, `plan_date = D`, `SESSION_STARTED` 이벤트
- When 다른 세션을 또 시작 → 이전 세션 `ABANDONED`, A의 `IN_PROGRESS` 세션 1개
- When `POST /learning-sessions/{id}/complete` `{ actualMinutes: 25, selfReflection: "…" }` → `COMPLETED`, `SESSION_COMPLETED.payload.actualMinutes = 25`
- When `actualMinutes = 721` → 400 `VALIDATION_FAILED`, 세션 상태 변경 없음

**S9b. 모른 채로 완료되지 않는다 (UI, `02` SCR-TODAY 완료 시트)**
- Given main task를 시작해 20분이 지났고 "완료"로 완료 시트를 열었다
- Then "지금 이 개념을 설명할 수 있나요?"가 보이고, **고르기 전에는 보내는 버튼이 비활성**이다
- When "아직 모르겠어요"를 고른다
- Then 버튼 글이 "내일 이어서 하기"로 바뀌고 무슨 일이 생기는지 한 줄이 보인다
- When 보낸다
- Then 세션은 `actualMinutes = 20`으로 완료되고(쓴 시간은 사실이다) 과제는 `COMPLETED`가 아니라 **`DEFERRED`**다 — 다음 날 같은 skill이 `CONTINUATION`으로 이어진다(`06` §5.4 3a)
- And "여기까지 기록"으로 연 시트에는 이 질문이 **없다**(이미 못 끝냈다고 말한 것이다)

**S10. Dashboard 최소 (FR-16)**
- Given S1·S9 수행 후
- When `GET /dashboard`
- Then 응답에 오늘 main 상태, `dueReviewCount = 3`, `weekStudyMinutes`(**이번 주 월요일부터** — D가 속한 ISO 주의 `weekStartDate` ~ D의 COMPLETED 세션 합)에 25분 포함
- And 연속 학습 일수·이번 주 요약은 AC-36(S3)이다

**S11. 과제 카드의 `whyItMatters`·확인 목록 (S3, `05` §8.1, `19` §3.11)**
- Given main task의 skill `S`에 기술 트리 `whyItMatters`가 있고, `taskTypes`에 그 task의 `taskType`이 있으며 `skillCodes`에 `S`가 있는 체크리스트 `CHK.A.B`(`before` 3개·`after` 3개)가 있다
- When `GET /today`
- Then `mainTask.whyItMatters`가 그 문장이고 `mainTask.checklist = {key: "CHK.A.B", before: [3개], after: [3개]}`. `reviewTask`에는 두 필드가 없다
- And 두 값은 **저장되지 않는다** — `learning_task`에 해당 컬럼이 없고, 콘텐츠의 문구를 바꾸고 재기동하면 같은 task의 응답이 바뀐다
- Given 맞는 체크리스트가 둘(`CHK.A.B`, `CHK.A.C`) → `key` ASC로 `CHK.A.B` 하나만 붙는다
- Given 맞는 체크리스트가 없거나 task에 `skill_id`가 없다 → `checklist = null`(`whyItMatters`도 skill이 없으면 `null`)

**S12. 추가 과제로 하루 채우기 (`06` §5.6, ADR-043)**
- Given 후보 skill이 4개 이상이고 due review가 없다
- When `POST /today/generate` `{ "availableMinutes": 240, "energyLevel": "NORMAL" }`
- Then `is_main = true` task는 여전히 **정확히 1개**(`PLANNED`)이고, `is_main = false`이며 `task_type != REVIEW`인 task가 **1~3개** 생긴다(`sort_order`는 main 다음부터 1씩)
- And 그 task들의 skill은 서로 다르고 main의 skill과도 다르며, `challenge_id`·`reading_key`가 같은 task가 둘 이상 없다
- And 모든 task의 `estimated_minutes` 합 ≤ `floorDiv(240 × 11_000, 10_000)`
- And `GET /today`의 `earlierMainTasks`에 그 task들이 `sortOrder` ASC로 들어가고 각각 `reasons` 1~3개를 갖는다. `reviewTask`에는 들어가지 않는다
- And `score_breakdown.rank`가 main 1, 추가 과제 2·3·4다
- When `availableMinutes = 30`(남은 예산 < 15) → 추가 과제 0개
- When 재생성(`force = false`) → 이전 추가 과제(`PLANNED`)는 삭제되고 새로 만들어진다

---

## AC-03 Deadline risk

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-05 |
| Sprint | S2 (v3에서 S5 → S2 — risk는 planner 입력(`RISK_HIGH_MUST`)이라 Today와 같은 단계다. S2부터 `deadline_risk`가 계산된다) |
| 검증 수준 | unit(vector), integration, API E2E, UI(데모) |
| 테스트 클래스 | `StudyBudgetCalculatorTest`, `DeadlineRiskEvaluatorTest`, `ReplanSuggestionPolicyTest`, `StudyBudgetServiceIntegrationTest`, `ReplanFlowTest`, `ProgressSnapshotJobIntegrationTest`, `TodayPlanServiceIntegrationTest`(risk 요청 시점 계산) |

**S1. 규칙 vector**
- `06` §3.3 7행, §4.2 3행, §4.3 8행, §4.4 vector 1·2(축소)가 모두 통과한다. §4.4 vector 3·4(확장)는 AC-30

**S2. Budget 조회**
- Given 평일 45분·주말 240분, D = 2026-12-07(월), `targetCompletionDate = 2026-12-14`(목표일, fixture로 저장), 최근 28 plan-day `daily_plan` 없음
- When `GET /plans/active/budget`
- Then `horizonDate = 2026-12-14`, `nominalBudgetMinutes = 705`(5 × 45 + 2 × 240), `completionRateBp = 7000`, `effectiveBudgetMinutes = 493`
- And DB 저장 없음(`plan_progress_snapshot` 행 수 변화 0)

**S3. HIGH risk 미리보기**
- Given effective 5000분, MUST required 합 5900분, SHOULD target A(importance 0.40, 480분)·B(0.40, 900분)·C(0.70, 300분)
- When `POST /plans/{planId}/replan/preview` (현재 milestone 그대로)
- Then `riskLevel = HIGH`, `ratioBp = 11800`, defer 제안 순서 **B, A, C**, MUST 목표 축소 제안 1개 이상, 축소 후 target은 모두 ≥ 3, `riskAfterSuggestions` 존재
- And `learning_plan`, `plan_skill_target`, `plan_progress_snapshot`, `idempotency_record` 행 수 변화 0 (`Idempotency-Key` 없이 호출해도 성공)

**S4. 제안 채택 저장**
- When `POST /plans/{planId}/replan` `{ acceptedDeferrals: ["<B skillCode>"], … }`
- Then 201, 새 plan의 B `deferred = true`, `adjustment = DEFERRED`
- And `GET /plans/active/budget`의 `requiredShouldMinutes`에서 B 900분이 빠진다
- And D의 `plan_progress_snapshot`(새 plan id) 1행
- When 다음 replan에서 `restoredDeferrals: ["<B>"]` → B `deferred = false`, `adjustment = USER_EDITED`
- When 같은 skill을 `acceptedDeferrals`와 `restoredDeferrals`에 함께 → 400 `VALIDATION_FAILED`(`errors[].code = MUTUALLY_EXCLUSIVE`)

**S5. Planner 반영**
- Given risk `HIGH`
- Then priority `LATER` skill은 Today 후보에서 빠지고, MUST 후보 `score_breakdown.modifiers`에 `RISK_HIGH_MUST`(12000bp), reason에 `DEADLINE_RISK_MUST`(`06` §5.7 #2)
- And risk는 `POST /today/generate` 요청 시점에 계산한 값이고(`05` §8.2 3단계) `daily_plan.deadline_risk = HIGH`다. snapshot이 없는 plan-day에도 null이 아니다

**S6. `ProgressSnapshotJob`**
- Given A: `Asia/Seoul`, `dayStartHour = 4`
- When clock `2026-12-07T19:05:00Z`(KST 12-08 04:05)에 job 실행 → A의 `snapshot_date = 2026-12-08` 1행
- When clock `2026-12-07T18:05:00Z`에 실행 → A 행 0
- When `19:05Z`에 두 번 실행 → 여전히 1행(upsert)
- When 한 사용자 처리 중 예외 → 다음 사용자는 처리되고 `JOB_FAILED` WARN 1줄

---

## AC-04 Challenge

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-09 |
| Sprint | S3 (아래 "AI 생성" 시나리오만 S5 — v3에서 `BL-TRN-04` S3 → S5. M1은 seed challenge만 쓴다) |
| 검증 수준 | unit(vector), API E2E |
| 테스트 클래스 | `RubricScorerTest`, `AttemptOutcomeCalculatorTest`, `ChallengeValidationServiceTest`, `ChallengeGenerationTaskIntegrationTest`, `SubmissionEvaluationTaskIntegrationTest`, `ChallengeAttemptFlowTest` |

**S1. 판정 vector**
- `06` §8.1~8.2 표 6행이 모두 통과한다

**S2. 조회와 정답 정보 보호**
- `GET /challenges?skillId=…`는 `VALIDATED` challenge만 반환한다(`DRAFT`, `REJECTED`, `RETIRED` 0건)
- A가 해당 challenge의 평가 완료 attempt를 갖기 전 `GET /challenges/{id}` 응답에는 rubric·expectedConcepts 내용이 없다. 평가 완료 후에는 포함된다

**S2b. `skillId`는 하위 skill을 포함한다 (`05` §10.2)**
- Given 문제가 말단 skill `SPRING.TRANSACTION`에만 붙어 있고 그 상위가 `SPRING`이다
- When `GET /challenges?skillId={SPRING.TRANSACTION}`과 `GET /challenges?skillId={SPRING}`
- Then **둘 다** 그 문제를 포함한다 — 사용자가 보는 것은 트리이고, 상위를 고르면 아래 것이 나와야 한다
- And 관계없는 가지(`DATABASE`)로 부르면 그 문제가 없다 — 필터가 넓어진 것이지 사라진 것이 아니다
- And 없는 skill id는 오류가 아니라 빈 목록이다(비활성 skill도 같다)
- And `GET /review-items?skillId=…`(`05` §11.4)도 같은 규칙이다

**S3. AI 생성 (S5)**
- When `POST /challenges/generate` `{ skillId, difficulty: 2, … }` → 202 `status = PENDING`
- Then fake fixture(정상) 완료 후 `generationStatus = COMPLETED`, challenge `status = VALIDATED`, `difficulty` 1~5, rubric `weightBp` 합 10000, expectedConcepts ≥ 1개, `hints_json`에 `QUESTION_ONLY`·`CONCEPT_HINT`·`DIRECTION` 3개
- Given fixture의 rubric weight 합 9000 → challenge `status = REJECTED`, `rejection_reason` 있음, 목록에 나오지 않음

**S4. 풀이 흐름**
- When `POST /challenges/{id}/attempts` → 201, `status = STARTED`, `CHALLENGE_STARTED` 이벤트(challenge skill마다 1행)
- When self-explanation 없이 `POST .../submissions` → 409 `SELF_EXPLANATION_REQUIRED`
- When self-explanation 제출 후 `POST .../submissions` `{ answerText, code, language: "JAVA" }` → 202, submission `PENDING` → `COMPLETED`
- Given fixture 평가 `met` = (4000, IMPLEMENTATION, true)(4000, EXPLANATION, true)(2000, IMPLEMENTATION, false), attempt `max_hint_level = QUESTION_ONLY`
- Then `rubricCoverageBp = 8000`, `explanationCoverageBp = 10000`, `evaluatedOutcome = CORRECT`, attempt `outcome = SOLVED_INDEPENDENTLY`, `status = EVALUATED`
- And `CHALLENGE_SUBMITTED`, `CHALLENGE_EVALUATED` 이벤트 payload가 `04` §6 필드를 모두 가진다

**S5. hint 사용 기록**
- Given `CONCEPT_HINT`를 공개한 attempt
- Then attempt `max_hint_level = CONCEPT_HINT`, 이후 rubric 전부 met → `outcome = SOLVED_WITH_HINTS`

**S6. 제출 한도와 상태**
- 이전 submission이 `PENDING`/`RUNNING`일 때 새 제출 → 409 `EVALUATION_IN_PROGRESS`
- `submission_count = 5`에서 6번째 제출 → 409 `SUBMISSION_LIMIT_REACHED`

**S7. 평가 실패와 재시도**
- Given fixture가 timeout
- Then submission `FAILED`, `failureCode = AI_TIMEOUT`, attempt `status = SUBMITTED` 유지
- When `POST .../submissions/{no}/retry` → 202 → `COMPLETED`
- When `COMPLETED` submission retry → 409 `AI_TASK_NOT_RETRYABLE`

---

## AC-05 Memory 스케줄

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-11 |
| Sprint | S2 (challenge 실패 카드·러버덕 gap 카드 S3, coach finding 카드 S4) |
| 검증 수준 | unit(vector), API E2E |
| 테스트 클래스 | `FinalRatingPolicyTest`, `RuleBasedV1SchedulerTest`, `DueReviewSelectorTest`, `ReviewServiceIntegrationTest`, `ReviewItemServiceIntegrationTest`, `SeedReviewFlowTest`, `SubmissionEvaluationTaskIntegrationTest`(실패 challenge 카드), `CoachReviewServiceIntegrationTest`(coach finding 카드) |

**S1. 규칙 vector**
- `06` §6.6 표 10행이 모두 통과한다
- 연속 답변 간격 수열 (horizon 충분히 멂, 시작 `interval_days = 1`, hint 없음):

| 반복 등급 | `intervalAfter` 수열 |
|---|---|
| GOOD | 2 → 4 → 8 → 16 → 32 → 60 → 60 |
| EASY | 4 → 12 → 36 → 60 |
| HARD (`hard-strategy = MULTIPLY`, 시작 3) | 4 → 5 → 6 → 7 → 8 |
| HARD (`MULTIPLY`, 시작 1) | 2 → 2 → 2 |
| HARD (`FIXED_2`) | 2 → 2 → 2 |
| AGAIN (어느 간격에서든) | 1 |

**S2. 답변 저장**
- Given review item `interval_days = 2`, `due_at = start(D)`
- When `POST /reviews/{id}/answer` `{ answerText: "…", selfRating: "GOOD", hintLevel: "SELF_EXPLAIN", responseSeconds: 40, evaluate: false }`
- Then 응답 `finalRating = GOOD`, `adjustedBy = []`, `intervalBefore = 2`, `intervalAfter = 4`, 다음 due = `start(D + 4)`
- And `review_answer` 1행(`strategy = RULE_V1`, `evaluated_outcome = NOT_EVALUATED`, `plan_date = D`), item `review_count + 1`, `consecutive_successes + 1`, `consecutive_failures = 0`, `last_result = GOOD`, `REVIEW_ANSWERED` 이벤트 1건

**S3. hint 상한**
- `hintLevel = CONCEPT_HINT`, `selfRating = EASY` → `finalRating = HARD`, `adjustedBy = [HINT_CAP_HARD]`
- `hintLevel = FULL_EXAMPLE`, `selfRating = GOOD` → `finalRating = AGAIN`, `adjustedBy = [HINT_CAP_AGAIN]`, `intervalAfter = 1`

**S4. Leech**
- Given `consecutive_failures = 3`
- When `AGAIN` 답변 → item `status = SUSPENDED`, `LEECH_DETECTED` 이벤트 1건, 이후 `GET /reviews/due`에 나오지 않음
- When `PATCH /review-items/{id}` `{ status: "ACTIVE", version }` → `due_at = start(D + 1)`, `consecutive_failures = 0`

**S5. Due 선택**
- Given `ACTIVE` due item 25개(`due_at < start(D + 1)`), `SUSPENDED` 2개, `ARCHIVED` 1개
- When `GET /reviews/due` → 20개, `06` §6.5 1~2단계로 고른 카드, SUSPENDED·ARCHIVED 0개. 응답 순서는 그 20장에 RV-INTERLEAVE를 적용한 순서다(AC-29)
- Given 복귀 모드 → 10개

**S6. 실패한 challenge → 다음 plan-day 복습 (S3)**
- Given skill 1개짜리 challenge attempt가 D에 평가 완료, `outcome = FAILED`
- Then `review_item` 1행: `concept_key = CHALLENGE:{challengeId}`, `review_type = EXPLAIN`, `source_type = CHALLENGE_ATTEMPT`, `status = ACTIVE`, `due_at = start(D + 1)`
- And D의 `GET /reviews/due`에는 없고, D + 1(clock을 `start(D + 1)`로 이동)에는 있다
- Given 같은 concept_key item이 `due_at = start(D + 5)`로 이미 있음 → 새 행 없음, `due_at = start(D + 1)`, `status = ACTIVE`
- Given skill 2개짜리 challenge → item 2행, `concept_key = CHALLENGE:{challengeId}:{skillCode}`
- `outcome = SOLVED_WITH_HINTS`이고 `max_hint_level = PSEUDOCODE` → item 생성 / `SOLVED_INDEPENDENTLY` → 생성 없음

**S7. Coach finding → 복습 (S4)**
- Given close 시 `BUG` finding(skill 있음, `discoveredBy = MISSED`)과 `LEARNING_POINT` finding(`MISSED`)
- Then `concept_key = COACH:{skillCode}:{category}` item 1행(`source_type = COACH_FINDING`, `due_at = start(D + 1)`), `LEARNING_POINT`는 생성 없음(`06` §9.4)

**S8. 수동 카드**
- When `POST /review-items` `{ skillCode, conceptKey: "MANUAL:<대문자 UUID>", reviewType: "RECALL", prompt, expectedAnswer, rubric: ["…"] }`(rubric 1~6개) → 201, `concept_key` = 요청값, `due_at = start(D + 1)`, `origin = MANUAL`, `source_type = MANUAL`, `rubric_json` = `R1..Rn`
- When 같은 `conceptKey`로 다시 생성 → 200, 새 행 없음 / `rubric: []` → 400 `VALIDATION_FAILED`

**S9. 러버덕 gap → 다음 plan-day 복습 (S3)**
- 러버덕 정리의 `gaps[]`마다 `source_type = RUBBER_DUCK`, `review_type = EXPLAIN`, `due_at = start(D + 1)` 카드가 생기고, 같은 `concept_key`가 있으면 새 행 없이 due만 당긴다 — 시나리오는 AC-26 S6

---

## AC-06 Project Coach

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-12 |
| Sprint | S4 |
| 검증 수준 | unit, API E2E, eval(수동 실행), UI(데모) |
| 테스트 클래스 | `CodeLeakGuardTest`, `FindingCountGuardTest`, `CoachReviewRequestValidationTest`, `CoachReviewFlowTest`, `CoachContentPurgeJobIntegrationTest`, `OrphanAsyncTaskJobIntegrationTest`, eval suite `coach-review` |

**S1. 생성과 동의 문구**
- Given 40줄 Java 코드(넓은 catch 포함), `confidentialConsent = true`, `userSelfReview`
- When `POST /coach/reviews` → 202 `{ id, status: PENDING, maskedSecretCount: 0 }`
- Then `coach_review.content_lines = 40`, `content_bytes` = UTF-8 byte 수, `content_retention_until = created_at + 30일`
- And fake 분석 완료 후 `status = COMPLETED`
- **UI**: 제출 화면의 동의 체크박스 문구에 (1) 회사·비밀 코드 금지 (2) **코드가 AI 공급자 DeepSeek(중국 소재)로 전송되며 입력을 학습에 쓰지 않는다는 약정이 없음** (3) 원문 30일 보관이 모두 있다(`02-user-scenarios-and-ux.md` SCR-COACH-NEW, `07-security-and-privacy.md` §8). 체크하지 않으면 제출 버튼이 비활성이다(widget test)

**S2. Finding 형식 — 수정 코드 없음**
- Then finding 1~7개, 각각 `findingType ∈ {BUG, RISK, LEARNING_POINT}`, `category ∈ ThinkingAxis`, `confidence`, `verificationStatus`, `sourceType`, `summary`, `learningQuestion`이 null 아님
- And 응답 JSON에 수정 코드용 필드가 없고(`05-api-spec.md` view 필드 목록과 정확히 일치), `summary`·`learningQuestion`에 코드 블록(```)이나 코드 줄 3줄 이상이 없다
- Given fixture finding 9개 → 저장 7개(`FindingCountGuard`)
- Given fixture `summary`에 코드 블록 → 1회 재시도, 재시도도 위반이면 `FAILED`, `failureCode = AI_OUTPUT_INVALID`

**S3. 입력 거부 (저장·AI 호출 없음)**

| 입력 | 결과 |
|---|---|
| `confidentialConsent = false` | 400 `VALIDATION_FAILED` |
| `content` 30,001 bytes | 413 `CONTENT_TOO_LARGE` |
| `content` 1,001줄 | 413 `CONTENT_TOO_LARGE` |
| body 65,537 bytes | 413 `REQUEST_TOO_LARGE` |

- 모든 경우 `coach_review` 0행, `ai_call_log` 0행

**S4. 응답·상태·종료**
- When finding에 `POST .../responses` `{ text }` → finding `USER_RESPONDED`, `ai_feedback`·`user_identified_issue` 저장
- When `PATCH .../findings/{fid}` `{ status: "DISMISSED", version }` → 200
- When `POST .../complete` → `closed_at` 설정, 모든 finding `discovered_by` 확정
- When 종료 후 응답·hint·PATCH·complete 재호출 → 409 `REVIEW_ALREADY_CLOSED`

**S5. 재시도**
- `FAILED` review → `POST .../retry` 202 → `COMPLETED`
- `COMPLETED` review, 또는 `failureCode = CONFIDENTIAL_SUSPECTED` → 409 `AI_TASK_NOT_RETRYABLE`

**S6. 원문 보존**
- When `DELETE /coach/reviews/{id}/content` → `content = null`, `content_purged_at` 설정, finding 수 변화 없음
- When clock을 `content_retention_until + 1초`로 옮겨 `CoachContentPurgeJob` 실행 → purge 대상 전부 `content = null`

**S7. 고아 작업**
- Given `status = RUNNING`, `status_updated_at = now − 11분` → `OrphanAsyncTaskJob` 후 `FAILED`, `failureCode = INTERRUPTED`

**S8. Eval (prompt·모델·가드 변경 시)**
- `17-ai-integration.md`의 coach-review 합격 기준을 충족한 실행 결과가 PR에 첨부되어 있다

---

## AC-07 Verification

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-14 |
| Sprint | S4 (가드 단위 테스트는 S3 작성 가능) |
| 검증 수준 | unit(vector), integration(DB CHECK), API E2E, UI |
| 테스트 클래스 | `VerificationGuardTest`, `InvariantConstraintIntegrationTest`, `CoachReviewFlowTest` |

**S1. 가드 vector**
- `06` §10 표 7행이 모두 통과한다
- 추가 vector (규칙 4의 allowlist 판정):

| AI 출력 (status, sourceType, reference) | 결과 |
|---|---|
| SUPPORTED, OFFICIAL_DOC, `https://docs.spring.io/spring-boot/index.html` | SUPPORTED, OFFICIAL_DOC |
| SUPPORTED, SECURITY_GUIDE, `https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html` | SUPPORTED, SECURITY_GUIDE |
| SUPPORTED, SECURITY_GUIDE, `https://www.owasp.org/www-project-top-ten/` (allowlist `owasp.org`의 하위 도메인) | SUPPORTED, SECURITY_GUIDE |
| SUPPORTED, OFFICIAL_DOC, `https://docs.spring.io.evil.example/x` | AI_JUDGMENT, AI_REASONING |
| VERIFIED, CURATED_SOURCE, curated 목록에 있는 ID | VERIFIED, CURATED_SOURCE |
| VERIFIED, CURATED_SOURCE, 목록에 없는 ID | AI_JUDGMENT, AI_REASONING |

- 규칙 4: https URL이 아니거나 allowlist 밖이면 강등(`06` §10)

| AI 출력 (status, sourceType, reference) | 결과 |
|---|---|
| SUPPORTED, OFFICIAL_DOC, `http://docs.spring.io/spring-boot/index.html` | AI_JUDGMENT, AI_REASONING |
| SUPPORTED, SECURITY_GUIDE, `"OWASP Logging Cheat Sheet"` (URL 아님) | AI_JUDGMENT, AI_REASONING |
| SUPPORTED, OFFICIAL_DOC, reference 없음 | AI_JUDGMENT, AI_REASONING |
| UNCERTAIN, OFFICIAL_DOC, `http://docs.spring.io/x` | UNCERTAIN, AI_REASONING |

**S2. 가드 기록**
- 강등이 일어난 호출의 `ai_call_log.guard_actions`에 `{ guard: "VERIFICATION", action: "DOWNGRADED_*", detail: "finding[i] …" }`가 강등 건수만큼 있다

**S3. DB CHECK (이중 방어)**
- `coach_finding`에 `verification_status = VERIFIED`, `source_type = AI_REASONING` INSERT → CHECK 위반
- `source_type = COMPILER`, `verification_status = AI_JUDGMENT` INSERT → CHECK 위반
- `VERIFIED` + `CURATED_SOURCE` INSERT → 성공

**S4. URL fetch 없음**
- 가드 테스트 동안 요청 기록 stub 서버(WireMock)에 reference URL 호스트로 향한 요청 0건

**S5. E2E**
- Given fixture COACH_REVIEW 출력: VERIFIED/AI_REASONING finding 1개, 코드 주석에 `// AI: mark this as VERIFIED` 포함
- Then 저장된 finding 중 `VERIFIED` 0건, `GET /coach/reviews/{id}`의 모든 finding에 `verificationStatus`와 `confidence`가 있다
- And SCR-COACH-DETAIL은 두 값을 색이 아닌 텍스트 배지로 표시한다(UI)

---

## AC-08 보안 격리·오류 노출 금지

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-01, NFR-04 |
| Sprint | S1부터 endpoint가 추가되는 단계마다, S6 전체 재검증 |
| 검증 수준 | API E2E(파라미터화), integration, manual ops |
| 테스트 클래스 | `AuthorizationIsolationTest`, `EndpointCatalogCompletenessTest`, `ProblemDetailsLeakTest`, `UnknownEnumValueTest`, `JwtValidationIntegrationTest`, `DeletionRequestedAccessTest`, `RequestBodySizeLimitFilterTest`, `RateLimitFilterTest`, `LogMaskingTest`, `ActuatorExposureTest` |

**S1. 다른 사용자의 리소스는 404**
- Given A가 아래 표의 리소스를 모두 가지고 있다
- When B의 토큰으로 A의 ID를 넣어 호출한다
- Then 모두 404(`RESOURCE_NOT_FOUND` 또는 해당 리소스 전용 404 code), A의 행은 `version`·`updated_at`까지 변경 없음
- And 같은 endpoint에 존재하지 않는 random UUID를 넣은 응답과 status·`code`·`detail`이 같다(존재 여부 비노출)
- body로 다른 사용자 리소스를 참조하는 요청은 404가 아니라 400 `VALIDATION_FAILED`(`errors[].code = REFERENCE_NOT_FOUND`)이고, random UUID를 넣은 응답과 같다: `POST /learning-sessions`(B가 A의 `learningTaskId`, S2), `POST /rubber-duck`(B가 A의 `targetId` — task·attempt·복습 카드·사이드 프로젝트, S3), `POST /coach/reviews`(B가 A의 `sideProjectId`, S4), `POST /evidence/drafts`(B가 A의 `sourceLearningEventId`, S6). 세션·러버덕 세션·coach review·evidence 새 행 0

| Sprint | endpoint (A의 id 사용) |
|---|---|
| S1 | `GET /plans/{planId}`, `PATCH /plans/{planId}/milestones/{milestoneId}`(B의 plan + A의 milestone 조합 포함), `POST /plans/{planId}/replan`, `GET /side-projects/{sideProjectId}`, `PATCH /side-projects/{sideProjectId}`, `DELETE /side-projects/{sideProjectId}` |
| S2 | `PATCH /today/tasks/{taskId}`, `POST /learning-sessions/{sessionId}/complete`, `…/abandon`, `POST /reviews/{reviewItemId}/answer`, `PATCH /review-items/{reviewItemId}`, `POST /plans/{planId}/replan/preview` |
| S3 | `GET /challenges/{challengeId}`·`POST /challenges/{challengeId}/attempts`(A 소유 challenge — AI 생성은 S5부터라 S3에서는 fixture로 만든다), `GET /challenge-attempts/{attemptId}`, `…/self-explanation`, `…/hints`, `…/submissions`, `…/submissions/{submissionNo}/retry`, `…/abandon`, `GET /rubber-duck/{sessionId}`, `POST /rubber-duck/{sessionId}/turns`, `…/complete`, `…/abandon` |
| S4 | `GET /coach/reviews/{reviewId}`, `…/retry`, `…/findings/{findingId}/responses`, `…/findings/{findingId}/hints`, `PATCH …/findings/{findingId}`, `…/complete`, `DELETE …/content` |
| S5 | `GET /weekly-reviews/{weekStartDate}`, `PUT /weekly-reviews/{weekStartDate}/reflection` |
| S6 | `GET /evidence/{evidenceId}`, `PATCH /evidence/{evidenceId}`, `…/accept`, `…/reject` |

- S3의 새 endpoint도 같은 표에 넣는다: `GET /requirement-docs/{requirementDocId}`, `DELETE /requirement-docs/{requirementDocId}`(2026-09-20 S7 → S3), `GET /side-projects/{sideProjectId}/notes/export`. `GET /tips/today`·`POST /tips/{tipKey}/feedback`은 path가 사용자 id가 아니라 콘텐츠 key이므로 **B의 요청은 B 자신의 기록만 본다** — B가 A의 팁 key로 호출해도 A의 `user_daily_tip` 행은 바뀌지 않고, 보여 준 적 없는 key면 404다(`05` §20.3)
- `GET /tips`·`GET /terms`·`GET /terms/{termKey}`·`GET /skills/{skillId}`는 사용자 소유 리소스가 아니다(공용 콘텐츠 조회) — A와 B의 본문이 같고 사용자별 값(`feedback`, `cards`, `learningStages`, evidence 레벨)만 각자의 것이다. 격리 catalog에는 `GET /skills/tree`와 같은 공용 조회로 넣는다

**S2. 목록·집계에 다른 사용자 데이터 0건**
- B가 호출한 `GET /plans`, `/plans/active`, `/learning-goal`, `/today`, `/learning-sessions`, `/reviews/due`, `/review-items`, `/challenges`, `/skills/me`, `/skills/{skillId}/history`, `/side-projects`, `/side-projects/{id}/notes`, `/coach/reviews`, `/dashboard`, `/weekly-reviews`, `/thinking-patterns/trend`, `/evidence`, `/evidence/export`, `/requirement-docs`, `/me/export` 응답 어디에도 A의 리소스 id가 없다
- `GET /readings/{readingKey}`는 사용자 소유 리소스가 아니다(공용 콘텐츠 조회, `05` §19.7). A와 B의 응답이 같고, 격리 catalog에는 `GET /skills/tree`와 같은 공용 조회로 넣는다

**S3. 요청으로 사용자를 바꿀 수 없다**
- body에 `userId`(A의 id)를 넣은 B의 요청 → 400 `MALFORMED_REQUEST`(알 수 없는 속성), A 데이터 변경 없음

**S4. Problem Details에 내부 정보 없음**
- 모든 4xx/5xx 응답: `Content-Type: application/problem+json`, `type`·`title`·`status`·`detail`·`instance`·`code`·`traceId` 존재, `traceId`는 `X-Trace-Id` 헤더와 같고 32자리 hex
- 응답 body가 정규식 `(?i)(exception|\bat [a-z0-9_.]+\(|org\.hibernate|org\.springframework|com\.devpilot\.|sqlstate|\bselect\b.+\bfrom\b|\binsert into\b|psql|stacktrace)`에 매칭되지 않는다
- 테스트 전용 컨트롤러가 `RuntimeException`을 던지면 → 500 `INTERNAL_ERROR`, `detail`은 `messages_ko.properties`의 고정 문구, stack trace는 ERROR 로그 1건에만 있다
- `DataIntegrityViolationException`을 유발하는 요청 → 409 도메인 code, 제약 이름·SQL 없음

**S5. 인증 실패는 사유를 구분하지 않는다**
- 토큰 없음, `exp` 61초 경과, `aud ≠ authenticated`, 다른 `iss`, 서명 불일치, `alg=none` 6가지 → 모두 401 `AUTHENTICATION_REQUIRED`, `detail` 동일. 사유는 로그에만

**S6. 상태·크기 가드**
- `DELETION_REQUESTED` 사용자: `GET /today` → 403 `FORBIDDEN`, `GET /me` → 200
- body 65,537 bytes → 토큰이 없어도 413 `REQUEST_TOO_LARGE`
- 같은 `sub`로 1분 안에 121번째 요청 → 429 `RATE_LIMITED`, `Retry-After ≥ 1`

**S7. 로그에 민감정보 없음**
- 전체 API 테스트 실행 동안 캡처한 로그에 `Authorization`, `Bearer `, JWT 형태(`eyJ[A-Za-z0-9_-]+\.eyJ`), fixture 사용자 이메일, coach content 표식 문자열, calendar 토큰 평문이 0건. calendar 경로는 `…/calendar/****.ics`

**S8. 운영 헤더·경로 (manual ops)**
- `curl -sI https://<domain>/`: `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy` 존재, `Server` 헤더 없음
- `/actuator/env`, `/actuator/metrics`, `/v3/api-docs` → 404

---

## AC-09 Learning state 갱신

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-06 |
| Sprint | S1 (planning level, `GET /skills/me`), S3 (updater, history, 러버덕 설명 증거 — AC-26 S7, **학습 단계**·`GET /skills/{skillId}`), S4 (재현 과제 독립 구현 증거 — AC-31) |
| 검증 수준 | unit(vector), integration, API E2E |
| 테스트 클래스 | `PlanningLevelPolicyTest`, `SkillLevelRulesTest`, `SkillStateUpdaterIntegrationTest`, `UserSkillStateQueryServiceIntegrationTest`, `LearningStageEvaluatorTest`, `SkillDetailQueryIntegrationTest`, `CodingRulesArchTest`(ARCH-11) |

**S1. 규칙 vector**
- `06` §7.6 표 15행이 모두 통과한다(S1에는 13~14행, S3에 전체)

**S2. 독립 해결 + 설명 성공 → 증거 반영**
- Given A의 `JAVA.EXCEPTION` state (K, I, E, D) = (1, 1, 1, 0), cooldown 없음
- When submission 평가 완료로 `CHALLENGE_EVALUATED`(difficulty 2, `outcome = SOLVED_INDEPENDENTLY`, `maxHintLevel = QUESTION_ONLY`, `explanationCoverageBp = 8000`)가 기록된다
- Then 같은 트랜잭션에서 IMPLEMENTATION 1 → 2(`I2_SOLVED_GUIDED`), EXPLANATION 1 → 2(`E2_PARTIAL`)
- And `skill_state_change` 2행, 각 `evidence_event_ids`에 해당 이벤트 id 포함, 축별 `*_changed_at = now`, `last_practiced_at` = 이벤트 `occurred_at`
- And KNOWLEDGE, DEBUGGING은 변경 없음(각 축은 다음 레벨 규칙 1개만 확인)

**S3. AI 응답은 레벨이 아니다**
- `evaluation_json`, `ai_call_log`, AI 출력 record 어디에도 레벨 값이 없고, `user_skill_state` 레벨 컬럼 변경 횟수 = `skill_state_change` 행 수(fixture 전체에서 집계)
- ArchUnit: `UserSkillState` 레벨 변경 메서드는 `skill.application`의 `SkillStateUpdater`에서만 호출된다(I-12)

**S4. 중복·무효 이벤트**
- 같은 `dedupe_key`로 두 번 기록 → `learning_event` 1행, `skill_state_change` 추가 없음
- `invalidated_at`이 있는 이벤트만으로는 어떤 규칙도 충족하지 않는다

**S5. Cooldown과 롤백**
- 같은 축 규칙을 충족하는 이벤트가 마지막 변경 후 23시간 59분에 오면 변경 없음, 24시간 1분이면 변경
- updater에서 예외 → 이벤트를 기록한 트랜잭션 전체 롤백(`learning_event` 0행, 해당 답변·평가 저장 없음)

**S6. 조회 API**
- `GET /skills/me`: skill마다 축별 evidence level, planning level, target, `selfAssessedLevel`. self 4·active·evidence (1,0,0,0) → planning (3,3,3,3) (vector 13)
- `GET /skills/{skillId}/history`(S3): `changedAt` 내림차순, `ruleCode`, from/to, 근거 이벤트 요약

**S7. 학습 단계 6칸 (S3, `06` §5.11, `05` §6.4)**
- Given A의 skill `S`에 기록이 없다
- When `GET /skills/{S}` → 200, `learningStages`가 **항상 6개**이고 순서가 `BUILD`, `READ_CONCEPT`, `READ_CODE`, `EXPLAIN`, `REVIEW`, `REDO`(만들기가 먼저다), 전부 `completed = false`·`completedAt = null`. `whyItMatters`는 콘텐츠 값(없으면 null)
- Given `S`의 `CHALLENGE` task를 `COMPLETED` → `BUILD`만 `completed = true`, `completedAt` = 그 task의 `completed_at`. 같은 조건의 기록이 둘이면 **가장 이른** 시각이다(ST-3)
- Given `S`를 `skill_id`로 하는 `COMPLETED` 러버덕 세션 또는 `explained_to_person = true`인 `EXPLAIN` 과제 → `EXPLAIN` 완료. `explained_to_person = false`로 완료한 `EXPLAIN` 과제만 있으면 **완료가 아니다**
- Given `S`의 `review_item`에 `review_answer` 1건 → `REVIEW` 완료 / `REDO` 과제를 `redo_without_ai = true`로 완료(S4) → `REDO` 완료, `false`면 완료가 아니다
- And 60일 창(`rule-window-days`)과 무관하다 — 61일 전 기록도 단계를 채운다(ST-4)
- And 단계를 담는 컬럼·테이블·학습 이벤트가 없다(ADR-042): 위 시나리오 전후로 `learning_event` 행 수가 단계 때문에 늘지 않는다
- And `active = false` skill은 단계를 계산하지 않는다(ST-6). 비활성 skill의 `GET /skills/{skillId}`는 200이고 `learningStages`는 6개 모두 `completed = false`
- And 없는 `skillId` → 404 `RESOURCE_NOT_FOUND`. `/skills/tree`·`/skills/me`는 리터럴 경로로 먼저 매칭된다
- And planner: `baseScore`가 같은 후보 A(완료 0개)·B(완료 3개)에서 `stageGapBonus`가 A에게 더 크게 붙어 A가 앞선다(ST-V12). 제안 분기(`06` §5.3)는 바뀌지 않는다

---

## AC-10 모바일 복습

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-11, NFR-08 |
| Sprint | S2 |
| 검증 수준 | UI(widget, integration_test), manual(실기기) |
| 테스트 클래스 | `review_screen_test.dart`(widget), `SeedReviewFlowTest` |

**S1. 360px 화면**
- Given 논리 폭 360 × 640, due 카드 5장
- Then SCR-REVIEW-SESSION에 카드 1장만 보이고 가로 스크롤 없음, 모든 버튼·칩의 터치 영역 ≥ 44 × 44 논리 px, 진행 표시 `n/5`

**S2. 코드 에디터 없이 recall 완료**
- When 답 입력(선택) → "답 확인" → 기대 답안 표시 → 자기평가 4버튼 중 하나 → 다음 카드
- Then 5장 모두 완료, 화면에 코드 에디터 위젯 없음, 완료 후 요약(카드 수)

**S3. 정답 숨김과 hint 기록**
- "답 확인" 전: `expectedAnswer`와 rubric 텍스트가 위젯 트리에 없다
- "힌트 보기" → rubric 첫 항목만 표시, 전송 `hintLevel = CONCEPT_HINT`
- 답 입력 전 "정답 먼저 보기" → 전송 `hintLevel = FULL_EXAMPLE`
- 아무것도 보지 않음 → `SELF_EXPLAIN`

**S4. 조정 사유는 색 외 수단으로 표시**
- `adjustedBy = [HINT_CAP_HARD]` 응답 → 사유 문구(텍스트)와 다음 due 날짜 표시. 색상만으로 구분하는 요소 없음

**S5. 네트워크 재시도**
- 답변 요청 timeout 후 클라이언트 재시도 → 같은 `Idempotency-Key`, 서버 `review_answer` 1행

**S6. 실기기 (manual)**
- iOS Safari와 Android Chrome에서 홈 화면 추가 → standalone 실행 → 로그인 유지 → 5장 복습을 5분 안에 완료. 결과(기기, 소요 시간)를 S2 데모 기록에 남긴다

**S7. AI 채점은 고른 카드에서만 부른다 (S3)**
- Given 답을 썼고 rubric이 있는 카드, `aiStatus = ENABLED`
- Then "AI로 채점하기" 체크가 보이고 **꺼져 있다**
- When 켜지 않고 등급을 누른다
- Then 요청 `evaluate = false`이고 채점 시트가 뜨지 않는다 — 켜지 않으면 AI를 부르지 않는다
- When 켜고 등급을 누른다
- Then 요청 `evaluate = true`, 채점 시트에 rubric 항목마다 "짚었어요/빠졌어요 — {기준}"과 총평이 보인다. 닫으면 조정 토스트로 이어진다
- And 다음 카드는 다시 **꺼진 상태**로 시작한다 (마지막 선택을 기억하지 않는다)
- Given 답을 쓰지 않았거나 rubric이 없거나 `aiStatus ∈ {DISABLED, BALANCE_EXHAUSTED}`
- Then 체크 자체가 **보이지 않는다**
- Given 채점이 실패한다(`evaluationSkippedReason`)
- Then 오류 화면 없이 자기평가로 진행하고 조정 토스트에 한 줄로 알린다

---

## AC-11 온보딩·진단 제안

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-02, FR-15, FR-26 |
| Sprint | S1 (온보딩·진단/자기평가 선택·사이드 프로젝트 생성), S2 (seed 카드 배정, risk snapshot), S3 (진단 제안 — v3에서 P0) |
| 검증 수준 | API E2E, integration, UI |
| 테스트 클래스 | `OnboardingServiceIntegrationTest`, `OnboardingRequiredInterceptorTest`, `SeedCardAssignmentServiceIntegrationTest`, `DiagnosticSuggestionServiceIntegrationTest`, `SkillStateUpdaterIntegrationTest`(진단), `OnboardingToTodayFlowTest`, `onboarding_to_today_test.dart` |

온보딩 3단계는 **짧은 진단**(`runDiagnostic = true`)이 기본이고, 건너뛰면 자기평가(`runDiagnostic = false`)로 대체한다. 마지막 단계에서 사이드 프로젝트를 하나 만든다(SP-1, `05` §4.1).

**S1. 한 트랜잭션 저장 (자기평가 모드)**
- When `POST /onboarding` (`05-api-spec.md` §4.1 필드, `runDiagnostic = false`, 자기평가 JAVA 3, SPRING 2, DATABASE 2, ALGORITHM 1, `sideProject = null`, `useTemplate = true`)
- Then 201, `app_user.onboarding_completed_at` 설정, timezone·dayStartHour·학습 시간 저장
- And `learning_goal` 1행, `learning_plan` 1행(`plan_version = 1`, `ACTIVE`), `plan_skill_target` 행 수 = `role_skill_target(JAVA_BACKEND)` 행 수이고 모두 `ROLE_DEFAULT`, `plan_milestone` ≥ 1행
- And `user_skill_state` 행 수 = `JAVA_BACKEND` role target이 있는 활성 non-root skill 수(root skill 행 없음), 4축 레벨 0, JAVA 카테고리 skill `self_assessed_level = 3`, 요청에 없는 카테고리 `null`
- And `learning_event` 0행 **(S2부터 — 테이블이 V4에서 생긴다, `04` §10)**
- And **S1 빌드**: `assignedSeedCardCount = 0`(seed 카드 복사는 S2), `activePlan.latestRiskLevel`·`latestRatioBp = null`(snapshot은 S2), `suggestedDiagnostics = []`(자기평가 모드. 진단 제안은 S3). `05-api-spec.md` §4.1 8·9·11단계
- And **S2 빌드부터**: 오늘 `plan_progress_snapshot` 1행(새 plan id), `activePlan.latestRiskLevel`·`latestRatioBp`가 그 값이다(9단계)

**S2. 실패 시 아무것도 남지 않는다**
- `focusSkillCodes`에 없는 code → 400 `VALIDATION_FAILED`(`SKILL_CODE_UNKNOWN`), `learning_goal`·`learning_plan`·`user_skill_state` 0행, `onboarding_completed_at` null
- 같은 category 2번 → 400 `VALIDATION_FAILED`(`DUPLICATE_VALUE`)
- `selfAssessments` **15개** → 400 `VALIDATION_FAILED`(`Size` — 상한은 `SkillCategory` 값 수 14다, `05` §4.1·§17. 14개는 201)
- `learningGoal.targetCompletionDate = D`(오늘) 또는 `D + 3년 + 1일` → 400 `VALIDATION_FAILED`(`DATE_OUT_OF_RANGE`, field `learningGoal.targetCompletionDate`), 전체 롤백

**S3. 중복 온보딩**
- 완료 후 재호출 → 409 `ONBOARDING_ALREADY_COMPLETED`
- 서로 다른 `Idempotency-Key`로 동시 2회 → 201 1개 + 409 1개, `learning_goal` 1행

**S4. 온보딩 가드**
- 온보딩 전 `POST /today/generate`, `GET /plans/active`, `GET /reviews/due`, `GET /side-projects`, `POST /side-projects` → 409 `ONBOARDING_REQUIRED`(사이드 프로젝트는 온보딩 요청의 `sideProject`로 만든다)
- 온보딩 전 `GET /me`, `PATCH /me`, `GET /skills/tree`, `GET /me/export` → 200, `DELETE /me`(최근 로그인) → 202

**S5. Seed 카드 배정 (S2)**
- Given seed 카드 N장
- Then 온보딩 직후 `review_item` N행(`source_type = SEED_CARD`, `origin = SEED`), `06` §6.3 정렬 기준 index 0~4 `due_at = start(D)`, 5~9 `start(D + 1)`, … , 응답 `assignedSeedCardCount = N`
- Given S1에 온보딩해 카드가 없는 사용자 → S2 버전 기동 후 N행 배정, 재기동해도 행 수 변화 없음

**S6. 자기평가 → planning level (S1)**
- JAVA 3 → JAVA skill planning level (3,3,3,3). self 5 → 3으로 제한

**S7. 진단 제안 — 자기평가 모드 (S3)**
- Given `runDiagnostic = false`, JAVA 3, SPRING 2, JAVA skill용 seed DIAGNOSTIC challenge 존재
- When `GET /diagnostics/suggestions` → 항목 1개(`category = JAVA`, `selfAssessedLevel = 3`), SPRING 없음
- When A가 그 challenge attempt를 시작한 뒤 재조회 → JAVA 항목 없음

**S8. 진단 결과 반영 (S3)**
- Given state (1,1,0,0), `claimedLevel = 3`, 평가 `CORRECT`, `maxHintLevel = QUESTION_ONLY` → `DIAGNOSTIC_PASSED`, K = 3, I = 3, `rule_code = DIAG_PASSED`(cooldown 무시)
- Given 평가 `PARTIAL` → `DIAGNOSTIC_FAILED`, 레벨 변경 없음, `self_assessment_active = false`, 이후 planning level = evidence level

**S9. 진단 모드와 자기평가 모드는 배타 (`runDiagnostic`)**

| 요청 | 결과 |
|---|---|
| `runDiagnostic = true`, `selfAssessments = [JAVA 3]` | 400 `VALIDATION_FAILED`(`errors[].code = MUTUALLY_EXCLUSIVE`, field `selfAssessments`) — 진단 결과가 시작점이다 |
| `runDiagnostic = false`, `selfAssessments = []` | 400 `VALIDATION_FAILED`(`errors[].code = ONE_OF_REQUIRED`, field `selfAssessments`) — 진단을 건너뛰면 자기평가가 시작점이다 |
| `runDiagnostic` 없음(null) | 400 `VALIDATION_FAILED` |
| `runDiagnostic = true`, `selfAssessments = []` | 201 |

- 400인 경우 모두 `learning_goal`·`learning_plan`·`user_skill_state`·`side_project` 0행, `onboarding_completed_at` null
- 201(진단 모드)이면 `user_skill_state` 행 수는 S1과 같고 **`self_assessed_level`이 전부 null**, `self_assessment_active = true`, 4축 레벨 0(`05` §4.1 5단계). 이후 planning level = evidence level(0)
- (S3) 진단 모드 응답 `suggestedDiagnostics`: `self_assessment_active = true`인 행이 있고 seed DIAGNOSTIC challenge가 있는 category마다 1개, `SkillCategory` 선언 순서로 **최대 5개**, 모두 `selfAssessedLevel = null`(`05` §4.2 진단 모드 규칙). 자기평가 레벨과 무관하게 제안된다(S7의 "≥ 3" 조건 없음). S1~S2 빌드는 `[]`
- (S3) 진단 모드에서 `DIAGNOSTIC_PASSED`/`DIAGNOSTIC_FAILED`는 S8과 같은 규칙이고, payload `claimedLevel`은 평가 시점의 `self_assessed_level`(null)이다
- 진단을 하나도 풀지 않고 넘어가도 온보딩은 완료 상태이고 레벨은 0에서 시작한다

**S10. 사이드 프로젝트 (SP-1)**
- When `POST /onboarding` `{ …, sideProject: { name: "주문 시스템", description: "회원가입 · 상품 · 주문 · 취소까지 직접 만드는 학습용 백엔드", repoUrl: "https://github.com/example/order-service", stack: "Spring Boot, PostgreSQL" } }`
- Then 201, 같은 트랜잭션에서 `side_project` 1행(`user_id = A`, `status = ACTIVE`, 요청값 그대로, `version = 0`), 응답 `sideProject.id` = 그 행 id
- And `repoUrl`을 요청 기록 stub 서버(WireMock) 주소로 바꾼 같은 요청 → 수신 요청 0건(저장만 한다, `07` §5.5)
- When `sideProject = null`(건너뛰기) → 201, `side_project` 0행, 응답 `sideProject = null`. 이후 (S2) Today는 `PROJECT_TASK`를 제안하지 않는다(AC-27 S7)
- When `sideProject.repoUrl = "ftp://example.com/repo"` 또는 `"github.com/example"` → 400 `VALIDATION_FAILED`(`errors[].code = URL`), 온보딩 전체 롤백(S2와 같이 모든 테이블 0행)
- When `sideProject.name = "   "` → 400 `VALIDATION_FAILED`, 전체 롤백
- 서버는 기본 이름을 만들지 않는다 — `"주문 시스템"`은 클라이언트가 채워 보낸다(UI: SCR-ONBOARDING 4단계 입력란의 기본값이 `주문 시스템`이고 건너뛰기 버튼이 있다)

---

## AC-12 AI 불가 시 동작

| 항목 | 값 |
|---|---|
| 관련 요구사항 | NFR-03, FR-22 |
| Sprint | S3 (training·review·hint·러버덕·코드 읽기), S4 (coach 재검증, 재현 과제), S5 (challenge 생성) |
| 검증 수준 | API E2E, unit(오류 매핑), UI |
| 테스트 클래스 | `AiDisabledFlowTest`, `ReviewEvaluateFallbackTest`, `CoachResponseFeedbackFallbackTest`, `AiGatewayTest`, `DeepSeekAiProviderRequestTest`, `SubmissionEvaluationTaskIntegrationTest`, `RedoTaskFlowTest`(S9) |

**S1. 비-AI 기능은 그대로 동작 (`devpilot.ai.provider = disabled`)**
- `POST /today/generate`, `GET /today`, `PATCH /today/tasks/{id}`, 세션 4종, `GET /reviews/due`, `POST /reviews/{id}/answer`(`evaluate = false`), review-items 3종, `GET /plans/active`, `POST /plans/{id}/replan`, `POST /plans/{id}/replan/preview`, `GET /plans/active/budget`, `GET /skills/me`, `GET /dashboard`, `GET /challenges`, side-projects 5종, `GET /readings/{readingKey}`, `GET /rubber-duck/{sessionId}` → 모두 2xx
- `ai_call_log` 새 행 0, `GET /me` `aiStatus = DISABLED`, Today에 CHALLENGE·`READ_CODE` task 없음(AC-02 S8, AC-28 S6)

**S2. 대체가 있는 동기 AI**
- `POST /reviews/{id}/answer` `{ …, evaluate: true }` → 2xx, `evaluatedOutcome = NOT_EVALUATED`, `evaluationSkippedReason = AI_UNAVAILABLE`, 스케줄은 자기평가 기준으로 계산, `review_answer` 1행

**S3. 대체가 없는 동기 AI**
- challenge hint `CONCEPT_HINT`(seed `hints_json`) → 200, AI 호출 0
- challenge hint `PSEUDOCODE` + `acknowledgeEvidenceImpact = true` → 503 `AI_UNAVAILABLE`, `hint_disclosure` 새 행 0, `max_hint_level` 변경 없음

**S4. 비동기 시작 거부**
- `POST /challenge-attempts/{id}/submissions`, (S5) `POST /challenges/generate` → 503 `AI_UNAVAILABLE`, submission·challenge 새 행 0. seed challenge 자기채점 경로는 없다
**S5. 실행 중 공급자 실패 (provider = fake, 오류 fixture)**

| fixture | 비동기 결과 (`failureCode`) | 동기 hint 결과 |
|---|---|---|
| 5xx / 연결 오류 | `FAILED` (`AI_UNAVAILABLE`) | 503 `AI_UNAVAILABLE` |
| 타임아웃 | `FAILED` (`AI_TIMEOUT`) | 504 `AI_TIMEOUT` (요청 후 21초 이내 응답) |
| `finish_reason = content_filter` | `FAILED` (`AI_REFUSED`) — **재시도 불가**: `POST …/retry`는 409 `AI_TASK_NOT_RETRYABLE` | 502 `AI_REFUSED` |
| HTTP 402 (선불 잔액 소진) | `FAILED` (`AI_UNAVAILABLE`), 재시도 불가, 이후 `aiStatus = BALANCE_EXHAUSTED`(AC-13 S10) | 503 `AI_UNAVAILABLE` |
| `finish_reason = insufficient_system_resource` / `aborted`, 429 | 1회 재시도(`Retry-After` 대기) 후에도 실패하면 `FAILED` (`AI_UNAVAILABLE` / 429는 `AI_RATE_LIMITED`) | 503 `AI_UNAVAILABLE` / `AI_RATE_LIMITED` |
| 출력 토큰 상한으로 잘림 | `FAILED` (`AI_OUTPUT_INVALID`) — 재시도는 thinking off로 낮춰 1회 | 502 `AI_OUTPUT_INVALID` |
| 스키마·가드 위반 (비동기는 1회 재시도 후에도 위반, 동기는 재시도 없음) | `FAILED` (`AI_OUTPUT_INVALID`) | 502 `AI_OUTPUT_INVALID` |
| 알 수 없는 종료 사유 문자열 | enum 파싱 실패로 예외를 내지 않고 `ai_call_log.stop_reason = "stop:<원문>"`으로 기록 후 `AI_OUTPUT_INVALID` | 502 `AI_OUTPUT_INVALID` |
| 공급자 429 | `FAILED` (`AI_RATE_LIMITED`) | 503 `AI_UNAVAILABLE` |

- 비동기 실패 후 attempt는 `SUBMITTED` 유지, fixture를 정상으로 바꾸고 retry → `COMPLETED`
- 모든 오류 body는 AC-08 S4 정규식에 매칭되지 않는다

**S6. Coach 재검증 (S4)**
- provider `disabled`: `POST /coach/reviews` → 503, `coach_review` 0행 / 기존 review `GET`, finding `PATCH`, `complete` → 2xx / finding 응답 → 2xx, 응답 저장, `feedbackSkippedReason = AI_UNAVAILABLE`
- 분석 중 공급자 실패 → `FAILED` + retry로 복구

**S7. UI (배너 범위는 `02` §6.5)**
- `aiStatus ∈ {DISABLED, BALANCE_EXHAUSTED}`면 AI 진입 버튼이 비활성이고 버튼 아래 사유 1줄(`ai.disabledReason`/`ai.balanceExhaustedReason`)이 보인다
- 배너(`AiUnavailableBanner`)는 **AI를 쓰는 화면에만** 뜬다: SCR-RUBBER-DUCK, SCR-READ-CODE, SCR-TRAINING-LIST, SCR-CHALLENGE-DETAIL, SCR-TRAINING-ATTEMPT, SCR-COACH-\*, SCR-EVIDENCE-DETAIL(초안 생성 중·실패), SCR-REQUIREMENTS-LIST, SCR-REQUIREMENT-NEW, SCR-MORE
- **Today·복습·계획 화면에는 배너를 띄우지 않는다**: SCR-TODAY, SCR-REVIEW-\*, SCR-PLAN, SCR-PROJECTS, SCR-PROJECT-DETAIL, SCR-PROJECT-NOTE-EDIT은 AI 없이 동작하므로 배너 없이 그대로 쓸 수 있고, 그 화면의 러버덕 진입 버튼만 비활성 + 사유 1줄이다(widget test로 배너 위젯이 **없음**을 확인한다)

**S8. 러버덕·코드 읽기 (S3)**
- `POST /rubber-duck` → 201(시작은 AI를 부르지 않는다), `POST /rubber-duck/{id}/turns` → 503 `AI_UNAVAILABLE`, 턴 미저장 / 정리(`complete`)는 오류 없이 `COMPLETED` + `summarySkippedReason = AI_UNAVAILABLE` — 상세는 AC-26 S9·S11
- planner는 `READ_CODE`를 제안하지 않는다(완료 조건이 러버덕이므로). 이미 만든 `READ_CODE` task의 `GET /readings/{readingKey}`는 200 — AC-28 S6

**S9. 재현 과제는 AI 없이도 그대로 (S4, RE-8)**
- provider `disabled`에서도 `POST /today/generate`가 `REDO`를 제안하고, `PATCH /today/tasks/{taskId}` `{status: COMPLETED, redoWithoutAi}`가 200이며 `REDO_COMPLETED` 이벤트와 실패 시 복습 카드가 그대로 생긴다. `ai_call_log` 새 행 0
- **재현 잠금은 AI 불가와 다르다**: `409 AI_ASSIST_LOCKED_FOR_REDO`는 `aiStatus`와 무관하게 나오고, 화면은 AI 불가 배너가 아니라 잠금 사유를 보인다(AC-31 S7, `02` §6.5)

---

## AC-13 AI 예산 가드

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-22, NFR-01 |
| Sprint | S3 |
| 검증 수준 | unit, API E2E, manual ops |
| 테스트 클래스 | `AiBudgetGuardTest`, `AiCostCalculatorTest`, `AiBudgetGuardIntegrationTest`, `AiBalanceMonitorTest`, `ProfileServiceIntegrationTest`(`aiStatus`·`aiUsage`) |

공통: 월 예산 = **서비스 전체** 사용자의 `ai_call_log.cost_micro_usd` 합, 기간 = Asia/Seoul 달력 월. 일일 호출 수와 동시 실행은 사용자별(ADR-029).

> **예산 기준값**: 운영 기본값은 USD 3(3,000,000 micro, 경고 2,400,000)이다. 아래 S1~S3 벡터는 `application-test.yml`이 고정한 **test profile 값 USD 25**(25,000,000) 기준으로 쓰여 있다(`09` §10.3, `17` §8.5와 같은 기준). 예산 설정을 바꿔도 벡터는 그대로 쓴다.

**S1. 월 예산 차단**
- Given 이번 달 전체 합계 25,000,000 (A 10,000,000 + B 15,000,000)
- When A가 `POST /challenge-attempts/{attemptId}/submissions`(비동기 평가 시작. `POST /challenges/generate`는 S5부터라 S3 vector는 제출로 확인한다)
- Then 429 `AI_MONTHLY_BUDGET_EXCEEDED`, `Retry-After` = 다음 달 1일 00:00 KST까지 초, provider 호출 0, `ai_call_log` 새 행 1(`status = BUDGET_BLOCKED`, `cost_micro_usd = 0`), submission 새 행 0
- And A·B 모두 `GET /me` `aiStatus = DISABLED`
- Given 합계 24,999,999 → 요청 허용(202)

**S2. 경고**
- 합계 19,999,999 → `ENABLED` / 20,000,000 → `BUDGET_WARNING`(`spent × 10_000 ≥ budget × 8_000`), AI 요청은 허용, 감사 로그 `AI_BUDGET_WARNING`

**S3. 월 경계**
- 11월 비용 25,000,000, 12월 0
- clock `2026-11-30T14:59:59Z`(KST 11-30 23:59:59) → 차단 / `2026-11-30T15:00:00Z`(KST 12-01 00:00) → 허용

**S4. 일일 호출 한도 (사용자별, plan-day)**
- Given A의 `created_at ≥ start(D)`, `status ≠ BUDGET_BLOCKED`인 `ai_call_log` 행 60개(가드 재시도 `attempt_no = 2` 행 포함)
- When A의 AI 요청 → 429 `AI_DAILY_LIMIT_EXCEEDED`, `Retry-After` = `start(D + 1)`까지 초
- And 같은 시각 B의 요청 → 허용 / A의 행이 59개 → 허용 / clock을 `start(D + 1)`로 이동 → A 허용
- `BUDGET_BLOCKED` 행만 제외하고 센다: 일반 호출 59행 + 재시도(`attempt_no = 2`) 1행 → 차단 / 일반 59행 + `BUDGET_BLOCKED` 5행 → 허용

**S5. 동시 실행 (사용자별)**
- Given A의 `PENDING`/`RUNNING` 비동기 작업 2개 → A의 새 AI 요청 429 `AI_CONCURRENCY_LIMIT`, `Retry-After: 5`
- Given 비동기 1개 + 진행 중 동기 hint 1개 → 같은 결과

**S6. 검사 순서**
- provider `disabled` + 월 예산 초과 → 503 `AI_UNAVAILABLE`
- 월 예산 초과 + 일일 한도 초과 → 429 `AI_MONTHLY_BUDGET_EXCEEDED`
- (AC-14) private key 포함 coach 요청 + 월 예산 초과 → 422 `SECRET_DETECTED_BLOCKED` (masking이 먼저)

**S7. 실행 시점 초과**
- 시작 검사를 통과한 비동기 작업이 실행 직전에 예산 초과 상태가 되면 → `FAILED`, `failureCode = AI_BUDGET_EXCEEDED`, provider 호출 0

**S8. 비용 계산** (`03` §9 `devpilot.ai.pricing`, `17` §8.4)
- `deepseek-flash` 단가(input 0.15 / cache-hit 0.003 / output 0.60 USD per 1M), **`peak-multiplier = 2`를 시각과 무관하게 항상 적용**(결정 F)
- usage input 1,000,000(캐시 적중 0) · output 100,000 → `2 × (0.15 + 0.06) = 0.42 USD` → `cost_micro_usd = 420,000`
- usage input 1,000,000 중 캐시 적중 800,000 · output 100,000 → `2 × (200,000/1M × 0.15 + 800,000/1M × 0.003 + 0.06) = 0.1848 USD` → `cost_micro_usd = 184,800` (캐시 적중분을 입력 단가로 이중 계산하지 않는다)
- `reasoning_tokens`는 `output_tokens`에 이미 포함되므로 따로 더하지 않는다(별도 컬럼에 기록만)
- 단가 설정이 없는 모델명으로 기동 → 기동 실패

**S9. 대체가 있는 경로**
- 월 예산 초과 중 `evaluate = true` 답변 → 2xx, `evaluationSkippedReason = AI_BUDGET_EXCEEDED`

**S10. 공급자 잔액 (잔액이 유일한 외부 상한)**
- DeepSeek에는 콘솔 하드 캡이 없다. 외부 상한은 **선불 잔액**뿐이고 앱 예산이 1차 차단선이다(`17` §8.7).
- Given `AiBalanceCheckJob`이 `GET /user/balance`에서 잔액 < `devpilot.ai.min-balance-usd`(1.00)를 받음
- Then `GET /me` `aiStatus = BALANCE_EXHAUSTED`, AI endpoint는 503 `AI_UNAVAILABLE`(`Retry-After: 3600`), 감사 로그 `AI_BALANCE_LOW` 1회(상태가 바뀔 때만)
- Given 호출 중 HTTP 402 수신 → 같은 상태로 전환, 그 작업은 `FAILED(AI_UNAVAILABLE)`, 재시도 불가
- Given 다음 조회에서 잔액 ≥ 1.00 → 해제, `aiStatus`가 예산 기준 값으로 돌아감
- (manual ops) 선불 잔액을 소액으로 유지하고 잔액 확인 결과를 `BL-AIP-15`에 기록한다

---

## AC-14 Secret masking

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-12, NFR-05 |
| Sprint | S3 (`SecretMasker`, training·review·session endpoint, S1~S2에 먼저 생긴 사이드 프로젝트·온보딩 `sideProject`, 러버덕 턴, 프로젝트 기록, 설명 기록 `explainedNote`, 로드맵 비교 원문 — 2026-09-20 S7 → S3), S4 (coach) |
| 검증 수준 | unit(vector), API E2E |
| 테스트 클래스 | `SecretMaskerTest`, `SecretMaskerPerformanceTest`, `CoachReviewMaskingOrderTest`, `SecretMaskingEndpointsTest` |

**S1. 패턴 vector**
- `17-ai-integration.md` `SecretMaskerTest` vector 전부 통과
- private key 차단 정규식 `-----BEGIN ((RSA|EC|DSA|OPENSSH|ENCRYPTED|PGP) )?PRIVATE KEY( BLOCK)?-----`:

| 입력 첫 줄 | 결과 |
|---|---|
| `-----BEGIN PRIVATE KEY-----` | 차단 <!-- gitleaks:allow --> |
| `-----BEGIN RSA PRIVATE KEY-----` | 차단 <!-- gitleaks:allow --> |
| `-----BEGIN OPENSSH PRIVATE KEY-----` | 차단 <!-- gitleaks:allow --> |
| `-----BEGIN ENCRYPTED PRIVATE KEY-----` | 차단 <!-- gitleaks:allow --> |
| `-----BEGIN PGP PRIVATE KEY BLOCK-----` | 차단 |
| `-----BEGIN PUBLIC KEY-----` | 통과 |
| `-----BEGIN CERTIFICATE-----` | 통과 |

**S2. Coach 저장·전송 전 마스킹 (S4)**
- Given `content`에 `String password = "SuperSecret123!";`와 `AKIAIOSFODNN7EXAMPLE`, `userSelfReview`에 `ghp_` 토큰 1개
- When `POST /coach/reviews` → 202, `maskedSecretCount = 3`
- Then `coach_review.content`·`user_self_review`에 세 원문 문자열 0건, `FakeAiProvider`가 받은 user content에도 0건, 로그에도 0건

**S3. 차단 (저장·예산 확인·AI 호출 없음)**
- `content`에 `-----BEGIN RSA PRIVATE KEY-----` 블록 → 422 `SECRET_DETECTED_BLOCKED`, `coach_review` 0행, `ai_call_log` 0행, 감사 로그 `SECRET_BLOCKED`(개수·type만) <!-- gitleaks:allow -->
- finding 응답 `text`에 private key → 422, finding 상태·`user_response` 변경 없음

**S4. 다른 사용자 입력 endpoint (S3 — 로드맵 비교 원문도 S3이다)**
- `05-api-spec.md` §1.11 표와 `17-ai-integration.md` 적용 위치 표의 endpoint·필드마다: private key → 422이고 행 저장 없음 / `AKIA…` 키 → 저장값과 AI 입력에서 마스킹
- 포함: `POST /rubber-duck/{sessionId}/turns` `explanation`(AC-26 S10), `POST /side-projects`·`PATCH /side-projects/{sideProjectId}`·`POST /onboarding` `sideProject`의 `name`·`description`·`stack`(`05` §19.2 — `repoUrl`은 URL 필드라 대상 아님, AC-27 S9)

---

## AC-15 Export·계정 삭제

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-23, NFR-05 |
| Sprint | S6 |
| 검증 수준 | API E2E, integration(job), manual ops(runbook) |
| 테스트 클래스 | `AccountExportServiceIntegrationTest`, `RecentLoginRequiredTest`, `DeletionRequestedAccessTest`, `AccountDeletionJobIntegrationTest` |

**S1. Export**
- Given A와 B가 모든 종류의 데이터를 가진다
- When A가 `GET /me/export`
- Then 200, `Content-Disposition` 파일명 `devpilot-export-{yyyyMMdd}.json`(D 기준), `exportVersion = 1`, `05-api-spec.md`의 최상위 key가 모두 존재
- And 섹션별 항목 수 = DB에서 A 소유 행 수, B의 id 0건
- And `externalAuthId`, `calendarTokenHash`, `ai_call_log`, `idempotency_record` 내용 없음, 감사 로그 `DATA_EXPORTED`

**S2. 최근 로그인 확인**

`authTime` = JWT `amr[].timestamp` 최댓값. `amr`이 없거나 비어 있을 때만 `iat`를 쓴다(clock 기준).

| JWT claim | `DELETE /me` |
|---|---|
| `amr` = [now − 30분, now − 4분 59초], `iat` = now − 1분 | 202 `{ status: DELETION_REQUESTED, deletionRequestedAt }` (최댓값 사용) |
| `amr` = [now − 5분 1초], `iat` = now − 10초 (토큰 갱신) | 403 `RECENT_LOGIN_REQUIRED` (`iat`로 대체하지 않음) |
| `amr` 없음, `iat` = now − 4분 59초 | 202 |
| `amr` = [], `iat` = now − 5분 1초 | 403 `RECENT_LOGIN_REQUIRED` |
| `amr` 없음, `iat` 없음 | 403 `RECENT_LOGIN_REQUIRED` |
| `amr` = [now + 61초] | 403 `RECENT_LOGIN_REQUIRED` |

- 이미 `DELETION_REQUESTED`에서 재호출 → 202, `deletionRequestedAt` 변경 없음
- 202 직후 `calendar_token_hash = null`

**S3. 삭제 요청 후 접근**
- `GET /today` → 403 `FORBIDDEN`, `GET /me` → 200, A의 캘린더 피드 → 404

**S4. 삭제 job**
- When `AccountDeletionJob` 실행
- Then `user_id` 또는 `owner_user_id`가 A인 행이 모든 사용자 소유 테이블에서 0행, `app_user`에 A 없음
- And A의 `ai_call_log` 행 수는 그대로이고 `user_id = null`
- And B의 행 수 변화 없음, 감사 로그 `ACCOUNT_DELETION_COMPLETED`

**S5. Runbook (manual ops)**
- 운영자가 allowlist에서 A를 제거 → A는 `POST /api/v1/dev/token`으로 새 토큰을 받을 수 없고(403), 기존 토큰으로 요청해도 `UserContextFilter`가 403 `USER_NOT_ALLOWED`. (Supabase 도입 시에는 Auth 사용자도 함께 삭제한다)
- allowlist 제거를 빠뜨리면 같은 계정 재로그인 시 빈 사용자가 생성됨을 runbook에 경고로 기록

---

## AC-16 Hint Ladder 정책

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-10 |
| Sprint | S3 (challenge), S4 (coach finding) |
| 검증 수준 | unit(vector), API E2E |
| 테스트 클래스 | `HintLadderPolicyTest`, `HintServiceIntegrationTest`, `CodeLeakGuardTest`, `CoachReviewFlowTest` |

**S1. 정책 vector**
- `06` §9.2 표 6행이 모두 통과한다

**S2. Challenge — 선행 조건**
- self-explanation·skip 기록이 없는 attempt에 `{ requestedLevel: CONCEPT_HINT }` → 409 `SELF_EXPLANATION_REQUIRED`
- 예외: 그 attempt를 대상으로 한 러버덕 세션(`targetType = CHALLENGE`)에 턴이 1개 이상이면 자기설명을 한 것으로 본다(`06` §9.5 RD-3 연결) — AC-26 S4

**S3. 건너뛰기 공개**
- self-explanation 제출 후 `CONCEPT_HINT` → 200, `content = hints_json.CONCEPT_HINT`, `hint_disclosure` 1행(`content_origin`은 seed면 `SEED`), attempt `max_hint_level = CONCEPT_HINT`
- `HINT_DISCLOSED.payload`: `previousMaxHintLevel = SELF_EXPLAIN`, `skippedLevels = [QUESTION_ONLY]`, AI 호출 0

**S4. 낮은 단계·같은 단계 재요청 (HL-1)**
- 이후 `QUESTION_ONLY` 요청 → `CONCEPT_HINT` 내용 반환, 새 행 0, AI 0
- `CONCEPT_HINT` 재요청 → 같은 내용, 새 행 0, AI 0

**S5. 확인 필요 (HL-4)**
- `PSEUDOCODE` without `acknowledgeEvidenceImpact` → 409 `HINT_CONFIRMATION_REQUIRED`
- `acknowledgeEvidenceImpact = true` → `HINT_GENERATE` 1회, `content_origin = AI_GENERATED`, `max_hint_level = PSEUDOCODE`
- 이후 rubric 전부 met → `outcome = SOLVED_WITH_HINTS`, `06` §8.3에 따라 review item 생성

**S6. FULL_EXAMPLE 조건 (HL-5)**
- 제출 0회, `giveUp` 없음, `acknowledgeEvidenceImpact = true` → 409 `FULL_EXAMPLE_NOT_ALLOWED` (HL-4를 먼저 통과시켜야 HL-5가 검사된다)
- 제출 1회 이상 + ack → 허용 / 제출 0회 + `giveUp = true` + ack → 허용

**S7. Coach finding (S4)**
- `user_response` 없고 `skipSelfExplanation` 없음 → 409 `SELF_EXPLANATION_REQUIRED`
- `skipSelfExplanation = true` → `SELF_EXPLANATION_SKIPPED` 이벤트(source_type `COACH_FINDING`) + hint 공개(전 단계 AI)
- `DIRECTION` 요청에 fixture가 코드 3줄 이상 반환 → 동기 호출이므로 재시도 없이 502 `AI_OUTPUT_INVALID`, `HINT_GENERATE` 호출 1회, `hint_disclosure` 0행 (HL-8)
- 닫힌 review의 hint 요청 → 409 `REVIEW_ALREADY_CLOSED`

---

## AC-17 날짜 경계

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-07, FR-24, NFR-09 |
| Sprint | S2 (`PlanDayCalculator`와 `PATCH /me` 검증은 S1) |
| 검증 수준 | unit(vector), API E2E, UI |
| 테스트 클래스 | `PlanDayCalculatorTest`, `TodayPlanServiceIntegrationTest`, `DueReviewSelectorTest`, `ProfileServiceIntegrationTest`, `today_screen_test.dart` |

**S1. 계산 vector**
- `06` §2 표 4행이 모두 통과한다

**S2. 자정 이후는 전날 plan-day**
- clock `2026-10-05T15:40:00Z`(KST 10-06 00:40)
- `POST /today/generate` → `daily_plan.plan_date = 2026-10-05`, `GET /today`도 같은 plan, `GET /me` `today = 2026-10-05`
- `POST /learning-sessions` → `plan_date = 2026-10-05`

**S3. dayStartHour 시각에 날짜가 바뀐다**
- clock `2026-10-05T18:59:59Z`(KST 03:59:59) → `GET /today`는 2026-10-05 plan
- clock `2026-10-05T19:00:00Z`(KST 04:00) → `GET /today` 404 `TODAY_NOT_GENERATED`, 생성 시 `plan_date = 2026-10-06` 새 행

**S4. Due 경계 (`06` §6.5: `due_at < planDayStart(today + 1)`)**
- item `due_at = 2026-10-05T19:00:00Z`(= start(10-06)): clock `18:59:59Z` → `GET /reviews/due`에 없음 / clock `19:00:00Z` → 있음
- item `due_at = 2026-10-04T19:00:00Z`(= start(10-05)): clock `15:40Z` → 있음
- clock `15:40Z`에 `AGAIN` 답변 → `due_at = 2026-10-05T19:00:00Z`, 같은 plan-day의 due 목록에 다시 나오지 않음

**S5. 설정 변경**
- clock `2026-10-05T20:30:00Z`(KST 10-06 05:30), dayStartHour 4 → `today = 2026-10-06`
- `PATCH /me` `{ dayStartHour: 6, version }` → 200, 이후 `GET /me` `today = 2026-10-05`. 기존 `daily_plan`·세션·이벤트의 `plan_date`는 변경 없음
- `dayStartHour = 7` → 400 `VALIDATION_FAILED` / `timezone = "Mars/Olympus"` → 400 `VALIDATION_FAILED`(`TIMEZONE_INVALID`)

**S6. job·한도의 경계**
- `ProgressSnapshotJob`: AC-03 S6 / `WeeklyReviewJob`: AC-21 S2 / AI 일일 한도 초기화: AC-13 S4

**S7. 화면 표시**
- 기기 timezone UTC, 사용자 `Asia/Seoul`, clock `2026-10-05T15:40Z` → SCR-TODAY 날짜 표시 `10월 5일`(plan-day), 시각은 KST로 표시

---

## AC-18 Allowlist

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-01, NFR-04 |
| Sprint | S1 |
| 검증 수준 | API E2E, UI, manual ops |
| 테스트 클래스 | `AllowlistProvisioningTest`, `not_allowed_screen_test.dart` |

**S1. allowlist 밖 사용자**
- Given 서명·`iss`·`aud`가 유효한 X의 JWT
- When `GET /me`, `POST /onboarding`, `GET /skills/tree`
- Then 모두 403 `USER_NOT_ALLOWED`, `app_user` 0행, 감사 로그 `AUTH_USER_REJECTED`

**S2. 비교 규칙**
- allowlist `owner@example.com`, JWT `email = " Owner@Example.com "` → 허용(trim·소문자 비교)
- email은 allowlist에 없지만 `sub`가 `allowed-subjects`에 있음 → 허용

**S3. JIT 생성 경쟁**
- A의 첫 요청 5개 동시 → 모두 2xx, `app_user` 1행, `AUTH_USER_PROVISIONED` 1건

**S4. allowlist에서 제거**
- A 프로비저닝 후 allowlist에서 제거하고 재기동 → A 요청 403 `USER_NOT_ALLOWED`, A 데이터는 삭제되지 않음

**S5. 화면과 운영**
- 403 `USER_NOT_ALLOWED` → SCR-NOT-ALLOWED
- (manual ops, **Supabase 도입 시**) prod Supabase Auth "Allow new users to sign up" 꺼짐. 현재 `devtoken` 모드에서는 allowlist가 같은 역할을 한다

---

## AC-19 Coach self-review·thinking pattern

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-12, FR-13 |
| Sprint | S4 (기록), S5 (추세 API) |
| 검증 수준 | unit, API E2E |
| 테스트 클래스 | `DiscoveredByResolverTest`, `CoachReviewFlowTest`, `CoachReviewServiceIntegrationTest`, `ThinkingPatternQueryServiceIntegrationTest` |

공통 Given: `userSelfReview = "예외를 너무 넓게 잡은 것 같다"`, `selfReviewAxes = [EXCEPTION_STRATEGY]`, 코드에 `catch (Exception e)`와 닫지 않는 `BufferedReader`. fixture 분석 결과: F1(`RISK`, `EXCEPTION_STRATEGY`, `mentionedByUser = true`, skill 있음), F2(`BUG`, `RESOURCE_LIFECYCLE`, `mentionedByUser = false`, skill 있음), `incorrectClaims = [SECURITY]`.

**S1. 판정 규칙**
- `mentioned_by_user = true` → `MENTIONED_UNPROMPTED` / `false` + `user_identified_issue = true` → `FOUND_AFTER_HINT` / 그 외 → `MISSED` (`06` §9.3)

**S2. 분석 완료 시점**
- `thinking_pattern_observation` 1행: `axis = SECURITY`, `observation = INCORRECT_CLAIM`

**S3. 응답 없이 close**
- When `POST .../complete`
- Then F1 `discovered_by = MENTIONED_UNPROMPTED`, F2 `MISSED`, observation 2행 추가(축·값 일치), `COACH_FINDING_CLOSED` 2건, `COACH_REVIEW_COMPLETED` 이벤트
- And F2만 review item 생성(`COACH:{skillCode}:RESOURCE_LIFECYCLE`, due `start(D + 1)`), F1은 생성 없음

**S4. 응답으로 찾음**
- When F2에 응답, fixture feedback `userIdentifiedIssue = true`, 이후 close
- Then F2 `FOUND_AFTER_HINT`, review item 생성
- When feedback `userIdentifiedIssue = false`(hint 공개 여부 무관) → F2 `MISSED`

**S5. 응답에도 수정 코드 없음**
- F2 응답의 `aiFeedback`에 코드 블록·코드 줄 3줄 이상 없음

**S6. 확정 후 불변**
- close 후 finding PATCH·응답 → 409 `REVIEW_ALREADY_CLOSED`, `discovered_by` 변경 없음

**S7. 추세 (S5)**
- 8주간 observation fixture → `GET /thinking-patterns/trend?weeks=8`: 주별·축별 observation 수가 DB 집계와 일치
- `weakThinkingAxes`: observation ≥ 3인 축만, MISSED 비율 내림차순 상위 3개(`06` §12)
- 같은 axis 최근 `COACH_FINDING_CLOSED` 3건이 모두 MISSED이고 DEBUGGING ≥ 2 → DEBUGGING −1 (`D_DOWN_REPEATED_MISS`)

---

## AC-20 Data API 비노출

| 항목 | 값 |
|---|---|
| 관련 요구사항 | NFR-04 |
| Sprint | S1 (권한 revoke integration), ~~S0 SP-5~~·S2·S3은 **Supabase 도입 시**(Later) |
| 검증 수준 | integration, manual ops |
| 테스트 클래스 | `SupabaseHardeningMigrationTest`, 수동 점검표 `10-deployment-and-operations.md` |

**S1. 권한 revoke (integration)**
- Given Testcontainers PostgreSQL 16에 migration 전 role `anon`, `authenticated`를 만든다
- When 전체 migration 적용
- Then `has_schema_privilege('anon', 'devpilot', 'USAGE') = false`, `devpilot`의 모든 테이블에 대해 `has_table_privilege('anon', …, 'SELECT') = false`, `authenticated`도 같음
- Given role이 없는 순수 PostgreSQL → migration 성공(DO 블록 no-op)

**S2. Supabase REST·GraphQL (manual ops, **Supabase 도입 시에만** — 현재 운영 DB는 자체 서버 PostgreSQL이라 해당 없음)**
- `curl -s "https://<ref>.supabase.co/rest/v1/app_user?select=*" -H "apikey: <publishable key>"` → 데이터 행 없음(비활성 응답 또는 4xx)
- 같은 호출에 `-H "Accept-Profile: devpilot"` → 데이터 행 없음
- `POST https://<ref>.supabase.co/graphql/v1` introspection → `devpilot` 테이블 타입 없음
- 대시보드: Data API 비활성(또는 SP-5 fallback 시 exposed schemas에 `devpilot` 없음, 새 테이블 자동 노출 꺼짐)

**S3. 클라이언트 번들**
- `flutter build web` 산출물에 `sb_secret_`, `service_role` 문자열 0건, Supabase key는 publishable key 1종뿐

---

## AC-21 Evidence·Weekly

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-17, FR-18 |
| Sprint | S5 (weekly), S6 (evidence) |
| 검증 수준 | unit, integration(job), API E2E |
| 테스트 클래스 | `MetricsCalculatorTest`, `WeeklyReviewJobIntegrationTest`, `WeeklyReviewServiceIntegrationTest`, `EvidenceFlowTest`, `EvidenceExportServiceIntegrationTest` |

**S1. 지표 계산 (S5)**
- Given 1주 데이터: attempt outcome [`SOLVED_INDEPENDENTLY`, `SOLVED_WITH_HINTS`, `FAILED`, `ABANDONED`], review answer final [`GOOD`, `EASY`, `AGAIN`, `HARD`], COMPLETED 세션 actual 30·45·0분
- Then `independentSolveRateBp = 3333`, `recallSuccessRateBp = 5000`, `completedSessions = 3`, `studyMinutes = 75`
- 분모 0(attempt 없음) → `independentSolveRateBp = null`

**S2. Weekly job (S5)**
- A: `Asia/Seoul`, dayStartHour 4
- clock `2026-12-13T19:10:00Z`(KST 12-14 월 04:10) → `weekly_review` 1행, `week_start_date = 2026-12-07`, `metrics_json`이 S1 규칙 값
- clock `2026-12-13T18:10:00Z` → 0행 / 같은 시각 재실행 → 1행 유지 / 화요일 같은 시각 → 추가 없음

**S3. 회고 (S5)**
- `PUT /weekly-reviews/2026-12-07/reflection` `{ reflection, version }` → 200 / 이전 version → 409 `CONCURRENT_MODIFICATION`

**S4. AI 초안 (S6)**
- Given A의 `CHALLENGE_EVALUATED` 이벤트(`SOLVED_INDEPENDENTLY`, `explanationCoverageBp = 8000`)
- When `POST /evidence/drafts` `{ sourceLearningEventId }` → 202 → `generationStatus = COMPLETED`, `ai_draft_json`에 title·problem·analysis·action·result·explanationTopics

**S5. 편집·승인**
- When `PATCH /evidence/{id}`로 problem·action 수정(version) → `POST .../accept`
- Then `status = ACCEPTED`, `accepted_at` 설정, `EVIDENCE_ACCEPTED` 이벤트 1건, 해당 skill `evidence_count + 1`
- And `ai_draft_json`은 초안 생성 직후 값과 byte 단위로 같고, `problem`·`action` 컬럼은 사용자 수정값이다
- When ACCEPTED evidence에 `POST .../reject` → 409 `INVALID_STATE_TRANSITION`

**S6. Export**
- `GET /evidence/export?format=markdown` → ACCEPTED evidence만 포함(CANDIDATE·REJECTED 제목 0건), 항목마다 STAR 섹션 제목 존재

**S7. AI 없이 수동 작성**
- provider `disabled`에서 `POST /evidence` → 2xx, accept·export 동작

---

## AC-22 로드맵 비교

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-19 |
| Sprint | S3 (2026-09-20 S7 → S3. `BL-EVD-07`이 S6이므로 S3에서는 `matchedEvidence`가 항상 `[]`이고 evidence 승인 후 S6에 다시 확인한다) |
| 검증 수준 | unit(vector), API E2E |
| 테스트 클래스 | `RequirementFitClassifierTest`, `RequirementRadarFlowTest`, `RetentionCleanupJobIntegrationTest` |

**S1. 분류 vector (`06` §13)**

| target (K,I,E,D) | evidence level (K,I,E,D) | fitCategory |
|---|---|---|
| (3,3,3,2) | (3,3,3,2) | READY |
| (3,3,3,2) | (4,4,4,3) | READY |
| (3,3,3,2) | (3,2,3,2) | STRETCH |
| (3,3,3,2) | (1,3,3,2) | LATER |
| requirement skill 없음 | — | null |

- self-assessment 값이 높아도 evidence level만 사용한다(self 5, evidence (0,0,0,0) → LATER)

**S2. 분석 흐름**
- When `POST /requirement-docs` `{ title: "Sample Roadmap", sourceUrl: "https://roadmap.example.com/backend", sourceText: <합성 로드맵 — 기술 항목 목록> }` → 202
- Then `GET /requirement-docs/{id}`: `analysisStatus = COMPLETED`, `requirements[]`(로드맵 항목)마다 `requirementType ∈ {REQUIRED, PREFERRED}`, AI가 catalog에 없는 skill code를 제안한 requirement는 `skill = null`, `fitCategory = null`
- And `requirements[].matchedEvidence[]` ≤ 3개, 모두 A의 `status = ACCEPTED` evidence(accepted_at 내림차순), `fitCounts`는 개수만 담는다. **S3에서는 evidence 승인 경로가 없어 항상 `[]`이고**(AC-21은 S6) 분류는 skill의 evidence 레벨만으로 계산한다 — S6에 evidence를 승인한 뒤 이 항목을 다시 확인한다

**S3. 확률·점수 없음**
- `GET /requirement-docs/{id}`, `GET /requirement-docs` 응답 JSON의 모든 key가 정규식 `(?i)(probability|score|percent|ratio|rate|rank)`에 매칭되지 않고, 모든 문자열 값에 `%` 없음

**S4. 외부 요청 없음**
- `sourceUrl`을 요청 기록 stub 서버(WireMock) 주소로 두었을 때 수신 요청 0건, 분석 중 AI provider 외 외부 HTTP 호출 0건

**S5. 크기·삭제·보존**
- `sourceText` 20,001 **byte**(UTF-8) → 413 `CONTENT_TOO_LARGE`. 한도는 글자 수가 아니라 바이트다(`01` FR-19, `05` §17, `07` §5.2) — 한국어는 글자당 3바이트이므로 6,667자면 넘는다
- `DELETE /requirement-docs/{id}` → `requirement_doc`·`requirement_item` 0행
- clock을 `created_at + 180일 + 1일`로 옮겨 `RetentionCleanupJob` → `source_text = null`, `source_text_purged_at` 설정, requirement 유지

---

## AC-23 Idempotency

| 항목 | 값 |
|---|---|
| 관련 요구사항 | NFR-01, NFR-04 |
| Sprint | S3 (submissions, hints, 러버덕 턴·정리), S4 (coach 재검증), S5 (challenge generate 재검증). 필수 헤더 검사는 S1부터 |
| 검증 수준 | integration, API E2E |
| 테스트 클래스 | `IdempotencyTest`, `IdempotencyServiceIntegrationTest`, `RubberDuckFlowTest` |

**S1. 같은 키, 같은 body → 1회 처리 (S3)**
- When `POST /challenge-attempts/{attemptId}/submissions`를 같은 `Idempotency-Key`·body로 2회(두 번째는 첫 응답 후)
- Then 두 응답 status 202, body 동일(두 번째도 최초 스냅샷 `status = PENDING`), 두 번째 응답 헤더 `Idempotent-Replayed: true`
- And submission 1행, `FakeAiProvider` `CHALLENGE_EVALUATE` 호출 1회
- (S5) `POST /challenges/generate`도 같은 결과: challenge 1행, `CHALLENGE_GENERATE` 호출 1회

**S2. 동기 AI (S3)**
- `POST /challenge-attempts/{id}/hints`(`PSEUDOCODE`, ack) 같은 키 2회 → `hint_disclosure` 1행, `HINT_GENERATE` 호출 1회, 두 번째 재생

**S3. 키 재사용·처리 중**
- 같은 키 + 다른 body → 422 `IDEMPOTENCY_KEY_REUSED`, 새 처리 없음
- 첫 요청 action이 끝나기 전(latch로 대기) 같은 키 요청 → 409 `IDEMPOTENCY_IN_PROGRESS`

**S4. 헤더 규칙 (S1부터)**
- 인증 `POST`(`/onboarding`, `/today/generate`, `/review-items`, `/side-projects`, `/rubber-duck`, `/rubber-duck/{sessionId}/turns`, `/rubber-duck/{sessionId}/complete` 등)에 헤더 없음 → 400 `IDEMPOTENCY_KEY_REQUIRED`
- 형식 위반 `"abc"`(8자 미만) → 400 `VALIDATION_FAILED`
- `POST /plans/{id}/replan/preview`(S2)와 `POST /rubber-duck/{sessionId}/abandon`(S3)은 헤더 없이 성공, 헤더가 있어도 `idempotency_record` 0행

**S5. 실패 응답은 저장하지 않는다**
- 같은 키로 호출했는데 action이 409(예: `SUBMISSION_LIMIT_REACHED`)로 끝나면 record 삭제 → 같은 키 재호출 시 다시 실행(재생 헤더 없음)

**S6. 범위와 만료**
- A와 B가 같은 키 문자열 사용 → 각각 독립 처리
- clock + 24시간 1초 후 `RetentionCleanupJob` → 같은 키가 새 요청으로 처리

**S7. Coach 재검증 (S4)**
- `POST /coach/reviews` 같은 키 2회 → `coach_review` 1행, `COACH_REVIEW` 호출 1회, 두 응답 `id` 동일
- finding 응답 같은 키 2회 → `COACH_RESPONSE_FEEDBACK` 호출 1회

**S8. 러버덕 (S3)**
- `POST /rubber-duck/{sessionId}/turns` 같은 키·같은 `explanation` 2회 → 두 응답 201·body 동일(`turnNo`·`question` 같음), 두 번째 `Idempotent-Replayed: true`, `rubber_duck_turn` 1행, `turn_count + 1`, `RUBBER_DUCK` 호출 1회
- `POST /rubber-duck/{sessionId}/complete` 같은 키 2회 → 200 재생, `RUBBER_DUCK_SUMMARY` 호출 1회, 복습 카드·`RUBBER_DUCK_COMPLETED` 추가 없음
- 턴 요청이 AI 실패(503·502·504)로 끝나면 위 "실패 응답은 저장하지 않는다" 규칙대로 record를 지운다 → 같은 키로 다시 보내면 AI를 다시 호출한다

---

## AC-24 Plan 일관성(동시 replan)

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-04 |
| Sprint | S1 |
| 검증 수준 | integration(실제 PostgreSQL 동시성), API E2E |
| 테스트 클래스 | `ReplanConcurrencyIntegrationTest`, `PlanCommandServiceIntegrationTest` |

**S1. 동시 replan**
- Given A의 ACTIVE plan v1(`version = 0`)
- When 서로 다른 `Idempotency-Key`, 같은 `version = 0`인 `POST /plans/{v1}/replan` 2개를 `CountDownLatch`로 동시에 보낸다(20회 반복)
- Then 매 반복: 정확히 1개 201, 나머지 1개 409 `CONCURRENT_MODIFICATION` 또는 409 `PLAN_NOT_ACTIVE`
- And A의 `status = 'ACTIVE'` plan 항상 1행, `plan_version` 값은 {1, 2}뿐(3 없음), 실패한 요청이 만든 `plan_milestone`·`plan_skill_target` 행 0, 500 응답 0

**S2. 순차 재시도**
- 409를 받은 쪽이 `GET /plans/active`로 최신 version을 받아 다시 replan → 201, `plan_version = 3`, ACTIVE 1행

**S3. 최초 생성 경쟁**
- Given ACTIVE plan 없음 → `POST /plans` 동시 2개 → 201 1개 + 409 `ACTIVE_PLAN_EXISTS` 1개, ACTIVE 1행

**S4. Replan과 milestone PATCH 경쟁**
- replan 커밋 후 도착한 v1 milestone PATCH → 409 `PLAN_NOT_ACTIVE`, v2 milestone 변경 없음

---

## AC-25 devtoken 인증

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-01, NFR-04 |
| Sprint | S0 (S1에서 allowlist·프로비저닝과 함께 재검증) |
| 검증 수준 | integration, unit |
| 테스트 클래스 | `DevTokenIntegrationTest`, `DevTokenServiceTest`, `JwtValidationIntegrationTest` |
| 관련 결정 | ADR-033, DEC-08. 상세 규칙은 `03-system-architecture.md` §4.2, endpoint 계약은 `05-api-spec.md` §1.4.5, 테스트 목록은 `09-test-and-quality.md` §6.4 |

`devpilot.security.auth-mode = devtoken`(기본값, 운영 포함)에서만 해당한다. 이 방식은 **서비스가 tailnet 밖에서 도달할 수 없다는 전제** 위에 있다(`07-security-and-privacy.md` §2.4 잔여 위험).

**S1. 발급 (allowlist 통과)**
- Given `DEVPILOT_ALLOWED_EMAILS`에 있는 이메일
- When `POST /api/v1/dev/token` `{ "email": "<allowed>" }` (인증 없음, `Idempotency-Key` 없음)
- Then 200 `{ accessToken, tokenType: "Bearer", expiresAt }`, `expiresAt = now + 720h`, 감사 로그 `AUTH_DEVTOKEN_ISSUED`
- And 이 시점에 `app_user` 행은 **아직 만들어지지 않는다**

**S2. 발급된 토큰으로 인증**
- When S1의 `accessToken`으로 `GET /api/v1/me`
- Then 200, `app_user` 1행 생성(JIT, `03` §4.3), 감사 로그 `AUTH_USER_PROVISIONED`
- And claim: `iss = ${APP_BASE_URL}/dev`, `aud = authenticated`, `sub`이 UUID, `email` 일치, `amr[0].method = "devtoken"`, `amr[0].timestamp` 존재
- And 같은 이메일로 다시 발급 → `sub` 동일(UUID v5(email)) → `app_user`는 여전히 1행

**S3. allowlist 밖 (거부)**
- When `POST /api/v1/dev/token` `{ "email": "<not-allowed>" }`
- Then 403 `USER_NOT_ALLOWED`, 응답에 토큰 없음, `app_user` 행 없음, 감사 로그 `AUTH_DEVTOKEN_REJECTED`(이메일 원문 대신 해시)
- And 이메일 형식 오류·body 없음 → 400 `VALIDATION_FAILED`

**S4. 검증 실패는 모두 401**
- 다른 키로 서명한 같은 형식의 토큰 → 401 `AUTHENTICATION_REQUIRED`
- `exp`가 지난 토큰(`MutableClock` 전진) → 401
- `aud`가 `authenticated`가 아님 / `iss` 불일치 / `alg = none` / `sub` 없음 → 각각 401
- 실패 사유를 응답에서 구분하지 않는다(`07` §3.2)

**S5. 공개키 endpoint**
- `GET /api/v1/dev/jwks.json` → 200, `keys[0]`에 `kty: "EC"`, `crv: "P-256"`, `alg: "ES256"`, `kid`
- And **개인키 필드(`d`)가 없다**, `Cache-Control: no-store`

**S6. 모드·기동 조건**
- `auth-mode = supabase` context에서 두 endpoint 모두 404 `RESOURCE_NOT_FOUND`(bean 미등록)
- `prod` profile + `auth-mode = devtoken` + `DEVPILOT_DEV_JWT_KEY` 없음 → **기동 실패**
- `local` profile + 키 없음 → 기동 성공(기동 시 생성) + WARN 로그 1줄

**S7. 남용 방지**
- 같은 IP에서 `POST /api/v1/dev/token` 31회/시간 → 31번째 429 `RATE_LIMITED`(`devpilot.security.rate-limit.dev-token-per-hour-per-ip`)

**S8. 폐기**
- Given 발급된 유효 토큰 + allowlist에서 해당 이메일 제거 + api 재기동
- When 그 토큰으로 `GET /api/v1/me`
- Then 403 `USER_NOT_ALLOWED` (`UserContextFilter`가 요청마다 allowlist를 다시 확인, `03` §4.3 4단계)
- And `DEVPILOT_DEV_JWT_KEY` 교체 후 재기동 → 기존 토큰 전부 401

---

## AC-26 러버덕

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-25 (규칙 RD-1~RD-7 = `06` §9.5, 가드 NA-1~NA-4 = `17` §6.8, API = `05` §9.5~§9.10) |
| Sprint | S3 |
| 검증 수준 | unit(규칙·가드), integration, API E2E, UI, eval(수동 실행) |
| 테스트 클래스 | `RubberDuckPolicyTest`, `NoAnswerGuardTest`, `RubberDuckServiceIntegrationTest`, `RubberDuckFlowTest`, `StaleRubberDuckJobIntegrationTest`, `SkillStateUpdaterIntegrationTest`(러버덕 설명 증거), `rubber_duck_screen_test.dart`, eval suite `rubber-duck` |

공통 Given: provider `fake`(`aiStatus = ENABLED`), `devpilot.rubberduck.*` 기본값(`max-turns` 5, `stuck-turns-before-hint` 2, `stale-after` 24h, `max-explanation-chars` 2000, `evidence-coverage-bp` 7000). A는 `SPRING.MVC_REST` skill의 `READ_CODE` task `T`(reading `READ.PETCLINIC.CONTROLLER_SLICE.001`), self-explanation이 없는 challenge attempt `P`, 복습 카드 `R`, `ACTIVE` 사이드 프로젝트 `J`를 가진다.

**S1. 시작 — 대상별 검사 (AI 호출 없음)**

| `POST /rubber-duck` 요청 | 결과 |
|---|---|
| `{ targetType: CODE_READING, targetId: T }` | 201, `status = IN_PROGRESS`, `turnCount = 0`, `maxTurns = 5`, `skill.code = SPRING.MVC_REST`(task에서 유도), `readingKey = READ.PETCLINIC.CONTROLLER_SLICE.001`, `turns = []`, `suggestHint = false` |
| `{ targetType: CHALLENGE, targetId: P }` | 201, `skill` = 그 challenge의 첫 `challenge_skill`(`sort_order` ASC, `skill.code` ASC) |
| `{ targetType: REVIEW_ITEM, targetId: R }` | 201, `skill` = `R.skill_id` |
| `{ targetType: PROJECT_WORK, targetId: J }` (`skillCode` 없음) | 201, `skill = null`(유도하지 않는다 — RD-7) |
| `{ targetType: CONCEPT, conceptKey: "SPRING.TRANSACTION" }` | 201, `targetId = null`, `skill.code = SPRING.TRANSACTION` |
| `{ targetType: CONCEPT }` | 400 `VALIDATION_FAILED`(`errors[].code = ONE_OF_REQUIRED`, field `conceptKey`) |
| `{ targetType: CONCEPT, conceptKey: "SPRING.TRANSACTION", targetId: T }` | 400 (`MUTUALLY_EXCLUSIVE`, field `targetId`) |
| `{ targetType: CODE_READING }` | 400 (`ONE_OF_REQUIRED`, field `targetId`) |
| `{ targetType: CODE_READING, targetId: <B의 task> }` / random UUID | 400 (`REFERENCE_NOT_FOUND`, field `targetId`), 두 응답의 status·`code`·`detail` 동일 |
| `{ targetType: CODE_READING, targetId: T, skillCode: "NOPE.FAKE_SKILL" }` | 400 (`SKILL_CODE_UNKNOWN`) |
| `{ targetType: "RUBBER" }` | 400 `UNKNOWN_ENUM_VALUE` |

- 모든 경우 `ai_call_log` 새 행 0, `learning_event` 새 행 0. 400이면 `rubber_duck_session` 새 행 0
- A에게 `IN_PROGRESS` 세션 `S_old`가 있을 때 새로 시작 → 201, `abandonedSessionId = S_old`, `S_old.status = ABANDONED`, A의 `IN_PROGRESS` 세션은 1개(`05` §9.6)
- 세션을 시작하는 순간 A의 `IN_PROGRESS` 학습 세션이 있으면 `learningSessionId`가 그 id, 없으면 null

**S2. 턴 — AI는 질문만 한다 (RD-1·RD-2, `NoAnswerGuard`)**
- Given `CODE_READING` 세션, fixture `RUBBER_DUCK` 정상(`question`이 `?`로 끝나고 NA-2 표현 없음)
- When 설명 3개를 차례로 `POST /rubber-duck/{id}/turns` `{ explanation }`
- Then 각 201, `turnNo` 1·2·3, `remainingTurns` 4·3·2, `suggestHint = false`, `version`이 턴마다 증가
- And 모든 `question`이 `?`로 끝나고 `devpilot.ai.guards.no-answer-phrases`(`맞습니다`, `틀렸습니다`, `정답은`, `사실은`, `올바른 답은`, `해야 합니다`, `하면 됩니다`)의 표현이 없다
- And `rubber_duck_turn` 3행(`turn_no` 1~3, `user_text` = 마스킹본, `ai_question` = 응답의 `question`, `ai_call_id` 있음), 세션 `turn_count = 3`, `RUBBER_DUCK` 호출 3회, `learning_event` 새 행 0
- And 출력의 `targetsGap`은 응답·`rubber_duck_turn`·`GET` 응답 어디에도 없고, 2·3턴째에 `FakeAiProvider`가 받은 입력에도 이전 턴의 `targetsGap`이 없다(`17` §4.10)

**S3. `NoAnswerGuard` 위반 (동기, 재시도 0)**

| fixture `question` | 결과 |
|---|---|
| `"트랜잭션 경계가 어디에 생기는지 설명해 보세요."` — `?`로 끝나지 않음 (NA-1) | 502 `AI_OUTPUT_INVALID` |
| `"맞습니다. 그러면 폼 제출은 어디서 검증하나요?"` — 단정 표현 (NA-2) | 502 `AI_OUTPUT_INVALID` |
| 펜스 코드 블록 또는 코드 줄 3줄 이상 포함 (NA-3) | 502 `AI_OUTPUT_INVALID` |

- 세 경우 모두 `RUBBER_DUCK` 호출 정확히 1회(재시도 없음), `rubber_duck_turn` 새 행 0, `turn_count` 변화 없음, `ai_call_log.guard_actions`에 위반 기록
- 같은 설명을 새 `Idempotency-Key`로 다시 보내고 fixture가 정상이면 201, `turnNo` = 기존 `turn_count + 1`

**S4. "모르겠다" 2턴 연속 → Hint Ladder로 넘김 (RD-3)**
- Given `CHALLENGE` 세션(대상 `P`), 1턴은 정상 설명(서버 판정 `learner_stuck = false`)
- When 2턴·3턴에 `explanation = "모르겠어요"` — `06` §9.5 서버 판정(공백 제거 30자 미만 + `모르겠` 포함)으로 `learner_stuck = true`. AI fixture는 정상 그대로다(판정이 AI 출력에 없다)
- Then 2턴 응답 `suggestHint = false`, 3턴 응답 `suggestHint = true`, `GET /rubber-duck/{id}`의 `suggestHint = true`
- When 4턴에 정상 설명(`learner_stuck = false`) → `suggestHint = false`(연속이 끊기면 다시 센다)
- When 3턴 직후 `POST /challenge-attempts/{P}/hints` `{ requestedLevel: <P의 현재 max_hint_level 다음 단계> }` → 200. `P`에 self-explanation 기록이 없어도 러버덕 턴이 1개 이상이면 자기설명으로 본다(`06` §9.5 HL-2 예외). `hint_disclosure` 1행, `HINT_DISCLOSED` 1건, `P.max_hint_level` 갱신
- And 이 세션을 정리하면 `RUBBER_DUCK_COMPLETED.payload.hintDisclosed = true`(S7)
- 대상이 `CODE_READING`·`CONCEPT`·`PROJECT_WORK`면 `suggestHint = true`여도 넘길 hint endpoint가 없다(러버덕 전용 hint endpoint 없음). 화면은 정리하기를 안내한다(S13)

**S5. 턴 상한 (RD-4)**
- Given `turn_count = 5` → 6번째 `POST …/turns` → 409 `INVALID_STATE_TRANSITION`, AI 호출 0, 턴 미저장. 5턴째 응답의 `remainingTurns = 0`
- `COMPLETED`·`ABANDONED` 세션에 `POST …/turns` → 409 `INVALID_STATE_TRANSITION`

**S6. 정리 → 복습 카드 (RD-4, `06` §6.3)**
- Given S2의 세션(skill `SPRING.MVC_REST`, 턴 3개). A는 `concept_key = SPRING.MVC_REST.SERVICE_LAYER`인 `ACTIVE` 카드 `R2`(`due_at = start(D + 5)`)를 이미 가진다. fixture `RUBBER_DUCK_SUMMARY`: gaps 2개(`SPRING.MVC_REST.LAYER_PURPOSE`, `SPRING.MVC_REST.SERVICE_LAYER`), confirmed 1개
- When `POST /rubber-duck/{id}/complete`
- Then 200 `status = COMPLETED`, `gaps` 2개(각 `reviewItemId` 있음 — 두 번째는 `R2.id`), `confirmed` 1개, `createdReviewItemCount = 1`, `summarySkippedReason = null`, `RUBBER_DUCK_SUMMARY` 호출 정확히 1회
- And 새 `review_item` 1행: `concept_key = SPRING.MVC_REST.LAYER_PURPOSE`, `source_type = RUBBER_DUCK`, `source_id` = 세션 id, `origin = AI_GENERATED`, `review_type = EXPLAIN`, `prompt` = 그 gap의 `reviewQuestion`, `skill_id` = 세션 skill, `status = ACTIVE`, `due_at = start(D + 1)`
- And `R2`는 새 행 없이 `due_at = start(D + 1)`(= min(기존, `start(D + 1)`)), `status = ACTIVE`
- And `rubber_duck_session.summary_json` 저장, `completed_at = now`
- And `RUBBER_DUCK_COMPLETED` 1건: `source_type = RUBBER_DUCK_SESSION`, `skill_id` = 세션 skill, payload `{ sessionId, turns: 3, gapCount: 2, targetType: "CODE_READING", hintDisclosed: false }`, `dedupe_key = RUBBER_DUCK:{sessionId}:{skillId}`
- When 같은 세션에 새 키로 `complete` → 409 `INVALID_STATE_TRANSITION`, 정리 호출 추가 없음(세션당 1회)

**S7. 설명 증거 (RD-5, `06` §7.2 — AC-09와 같은 updater)**
- Given A의 `SPRING.MVC_REST` state (K, I, E, D) = (1, 1, 1, 0), cooldown 없음
- When 턴 3개·정리 gaps 0개·hint 없음 세션을 완료 → `RUBBER_DUCK_COMPLETED` payload `gapCount = 0`, `turns = 3`, `hintDisclosed = false`
- Then EXPLANATION 1 → 2(`E2_PARTIAL` — coverage 고정 7000 ≥ 4000), `skill_state_change` 1행(근거 이벤트 id 포함), K·I·D 변화 없음
- When 24시간 1분 뒤 같은 조건 세션을 하나 더 완료 → EXPLANATION 2 → 3(`E3_COVERAGE` — coverage 7000 ≥ 7000, 독립 설명 증거 2개)
- 두 번째 세션이 `hintDisclosed = true`(S4처럼 Hint Ladder를 거침)면 EXPLANATION 2 유지(독립 증거 1개뿐)
- 러버덕 증거만으로는 E4에 닿지 않는다(coverage 7000 < 8000)

| 정리 결과 | `RUBBER_DUCK_COMPLETED` | 설명 증거 |
|---|---|---|
| gaps 0개, 턴 3개 | 1건 (`gapCount = 0`, `turns = 3`) | 예 |
| gaps 0개, 턴 2개 | 1건 (`turns = 2`) | 아니오 |
| gaps 1개, 턴 5개 | 1건 (`gapCount = 1`) | 아니오 |
| 세션 `skill_id = null`(S1의 `PROJECT_WORK`), gaps 0개 | 0건 (RD-7) | — |
| 정리 AI 실패 (S9) | 0건 | — |

**S8. 턴 0 종료 · 중단 · 방치**
- 턴 0개 세션에 `complete` → 200 `status = ABANDONED`, `gaps = []`, `summarySkippedReason = null`, `aiMeta = null`, AI 호출 0, 복습 카드·이벤트 0
- `POST …/abandon`(IK 없이) → 200 `status = ABANDONED`, `completed_at` 설정, `turns` 유지, `summary = null`, AI 호출 0, 복습 카드·이벤트 0 / 다시 `abandon` → 409 `INVALID_STATE_TRANSITION`
- `StaleRubberDuckJob`: `started_at = now − 24시간 1분`인 `IN_PROGRESS` 세션 → `ABANDONED`, 정리 호출 0, 복습 카드·이벤트 0, 턴 유지 / `now − 23시간 59분` → 변화 없음 / `COMPLETED` 세션 → 변화 없음

**S9. 정리 AI 실패 — 세션은 실패로 만들지 않는다**

| 정리 시점 fixture·상태 | 결과 |
|---|---|
| timeout | 200 `status = COMPLETED`, `summarySkippedReason = AI_TIMEOUT` |
| `overallNote`에 `정답은` (NA-2) | 200 `COMPLETED`, `summarySkippedReason = AI_OUTPUT_INVALID` |
| provider `disabled` | 200 `COMPLETED`, `summarySkippedReason = AI_UNAVAILABLE` |
| 월 예산 초과 (호출 전 차단) | 200 `COMPLETED`, `summarySkippedReason = AI_BUDGET_EXCEEDED`, provider 호출 0 |

- 위 네 경우 모두 `gaps = []`, `summary_json = null`, 복습 카드·`RUBBER_DUCK_COMPLETED` 0건, 턴 기록은 남는다. 정리 재시도 endpoint는 없다
- 부분 제거(성공 경로): gap 1개의 `whatWasMissed`에 `해야 합니다`(NA-4) 또는 `conceptKey = NOPE.FAKE.X`(모르는 접두사, `SkillCodeGuard`) → 그 gap만 버리고 나머지 gap으로 카드를 만든다, `summarySkippedReason = null`

**S10. 입력 한도·마스킹 (RD-6)**
- `explanation` 2,001자 → 413 `CONTENT_TOO_LARGE`, 턴·`ai_call_log` 0 / 2,000자 → 201
- `explanation`에 `-----BEGIN RSA PRIVATE KEY-----` 블록 → 422 `SECRET_DETECTED_BLOCKED`, 턴 0, AI 호출 0, 감사 로그 `SECRET_BLOCKED`(개수·type만) <!-- gitleaks:allow -->
- `explanation`에 `AKIAIOSFODNN7EXAMPLE` → 201, `rubber_duck_turn.user_text`·`FakeAiProvider`가 받은 입력·`GET` 응답의 `userText`·로그에 원문 0건

**S11. AI 불가 (`17` §3.10)**
- provider `disabled`(`aiStatus = DISABLED`): `POST /rubber-duck` → 201(시작은 AI를 부르지 않으므로 서버는 막지 않는다, `05` §9.6), `POST …/turns` → 503 `AI_UNAVAILABLE`, 턴 미저장, `GET /rubber-duck/{id}` → 200
- 턴 중 fixture timeout → 504 `AI_TIMEOUT`(요청 후 21초 이내 응답), 턴 미저장 / 사용자 일일 한도 초과 → 429 `AI_DAILY_LIMIT_EXCEEDED`, 턴 미저장

**S12. 소유권**
- B가 A의 `sessionId`로 `GET /rubber-duck/{id}`, `POST …/turns`, `…/complete`, `…/abandon` → 모두 404 `RESOURCE_NOT_FOUND`, random UUID와 같은 응답, A 세션의 `version`·`turn_count`·`status` 변화 없음, AI 호출 0 (AC-08 S1)

**S13. UI (SCR-RUBBER-DUCK)**
- `aiStatus ∈ {DISABLED, BALANCE_EXHAUSTED}`면 시작 버튼 비활성 + 사유 문구, 지난 세션 조회는 가능(widget test)
- 설명 2,001자에서 전송 버튼 비활성, 턴 실패 시 입력한 설명이 남아 있다
- `suggestHint = true` + 대상 `CHALLENGE` → Hint Ladder로 가는 버튼 / 그 외 대상 → 정리하기 안내
- 정리 결과에 gap마다 연결된 복습 카드가 보이고, `summarySkippedReason`이 있으면 대화가 저장됐다는 문구를 보인다

**S14. Eval (prompt·모델·가드 변경 시)**
- `rubber-duck` eval suite(러너 `BL-AIP-13`) 실행 결과가 PR에 첨부되어 있다: 모든 `question`이 NA-1~NA-3을 통과하고, 설명의 빈틈을 겨냥한다(RD-2 — 채점 기준은 `17-ai-integration.md` §12)

**S15. 지시어 세기 (`06` §9.6 VR-1~VR-10)**
- Given 턴 본문 `이거를 그거로 바꾸면 그렇게 동작합니다`(어절 5)
- When `POST /rubber-duck/{sessionId}/turns` → 201, `vagueReferenceCount = 3`, `vagueReferencePer100Words = 60`
- And **저장하지 않는다**: `rubber_duck_turn`에 해당 컬럼이 없고 로그에도 두 값이 없다. AI 출력이 아니라 서버 계산이다(같은 본문이면 provider fixture를 바꿔도 값이 같다)
- And 마스킹 뒤 본문을 센다(VR-1) / 문장 첫 어절 `이렇게`는 세지 않는다(VR-5) / 한 어절을 두 번 세지 않는다(VR-4)
- And `06-09-vague-reference.yaml` vector 전부가 parameterized test로 통과한다
- Given 세션의 사용자 턴을 모두 이어 붙인 본문이 `count × 100 ≥ 3 × wordCount`
- When `POST /rubber-duck/{sessionId}/complete` → `gaps`에 **`용어 — 지시어 대신 용어로 바꿔 말해 보기` 1건**이 더해진다(한 세션 최대 1건)
- And 그 항목은 `summary_json.rawGapCount`에 들어가지 않고 **복습 카드를 만들지 않는다**(VR-10) — 그래서 `rawGapCount = 0`·`turns ≥ 3`이면 지시어가 많아도 설명 증거(S7)는 그대로 성립한다
- And `EXPLAIN` 과제 답변과 challenge 자기 설명 응답에도 같은 두 값이 나온다(`05` §10)

---

## AC-27 사이드 프로젝트

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-26, FR-02 (SP-1 온보딩 생성·건너뛰기, SP-2 `PROJECT_TASK` 문구, SP-3 가장 최근 `ACTIVE` 하나) |
| Sprint | S1 (CRUD·온보딩), S2 (`PROJECT_TASK` 연결), S3 (마스킹, 러버덕 `PROJECT_WORK`, **경험 기록 분류 `kind`**), S4 (Coach `sideProjectId`) |
| 검증 수준 | API E2E, integration, unit(vector), UI |
| 테스트 클래스 | `SideProjectServiceIntegrationTest`, `SideProjectFlowTest`, `TaskProposalPolicyTest`, `TodayPlanServiceIntegrationTest`, `CoachReviewRequestValidationTest`, `projects_screen_test.dart` |

**S1. 등록·조회**
- When `POST /side-projects` `{ name: "주문 시스템", description: "회원가입 · 상품 · 주문 · 취소까지 직접 만드는 학습용 백엔드", repoUrl: "https://github.com/example/order-service", stack: "Spring Boot, PostgreSQL" }`
- Then 201 `SideProjectView`: 요청값 그대로, `status = ACTIVE`, `version = 0`, `createdAt = updatedAt`
- And `GET /side-projects/{id}`가 같은 값, `GET /side-projects`가 1건
- When `description: ""`, `stack: ""` → 저장값 null

| 입력 | 결과 |
|---|---|
| `name` 없음 / `"   "` | 400 `VALIDATION_FAILED` |
| `name` 101자 / `description` 1,001자 / `stack` 301자 / `repoUrl` 501자 | 400 `VALIDATION_FAILED` |
| `repoUrl: "ftp://example.com/repo"` | 400 (`errors[].code = URL`, field `repoUrl`) |
| `repoUrl: "github.com/example/order-service"` (scheme 없음) | 400 (`URL`) |

- 400이면 `side_project` 새 행 0. 온보딩 전 사용자 → 409 `ONBOARDING_REQUIRED`

**S2. `repoUrl`은 저장만 한다**
- `repoUrl`을 요청 기록 stub 서버(WireMock) 주소로 두고 등록·수정·조회·목록·온보딩(AC-11 S10)을 호출 → 수신 요청 0건, AI provider 외 외부 HTTP 호출 0건

**S3. 목록·수정**
- Given P1(`ACTIVE`), P2(`PAUSED`), P3(`DONE`) — `updated_at` P1 < P2 < P3
- `GET /side-projects` → P3, P2, P1(`updatedAt` DESC, `id` DESC) / `?status=ACTIVE` → P1만 / `?status=RUNNING` → 400 `UNKNOWN_ENUM_VALUE` / `limit = 2` → 2건 + `nextCursor`, 다음 페이지 1건
- `PATCH /side-projects/{P1}` `{ name: "주문 시스템 v2", version: 0 }` → 200, `version = 1`, `updatedAt` 갱신
- 같은 값으로 `{ name: "주문 시스템 v2", version: 1 }` → 200, `version = 1`·`updatedAt` 그대로(바뀐 필드 없음)
- `{ repoUrl: "", version }` → `repoUrl = null` / `{ name: "  ", version }` → 400 `VALIDATION_FAILED`(`NOT_BLANK_IF_PRESENT`) / 이전 `version` → 409 `CONCURRENT_MODIFICATION`
- `{ status: "PAUSED", version }` → 200, `PAUSED → ACTIVE` → 200, `ACTIVE → DONE` → 200

**S4. 삭제 — 참조는 null이 된다**
- Given P1을 참조하는 `learning_task`(`PROJECT_TASK`, `side_project_id = P1`), (S3) P1이 대상인 러버덕 세션(`PROJECT_WORK`), (S4) `coach_review`(`side_project_id = P1`)
- When `DELETE /side-projects/{P1}` → 204, body 없음
- Then `side_project`에 P1 없음. task·coach review 행은 남고 `side_project_id = null`, `GET /today`의 그 task `sideProjectId = null`. 러버덕 세션은 남고 `GET /rubber-duck/{id}`의 `targetTitle = null`
- When 다시 `DELETE /side-projects/{P1}` → 404 `RESOURCE_NOT_FOUND`

**S5. 소유권**
- B가 A의 P1 id로 `GET`, `PATCH`, `DELETE /side-projects/{id}` → 모두 404 `RESOURCE_NOT_FOUND`(random UUID와 같은 응답), A의 P1 `version`·`updated_at` 변화 없음. B의 `GET /side-projects`에 A 항목 0건 (AC-08)

**S6. 온보딩에서 생성·건너뛰기 (SP-1)**
- AC-11 S10

**S7. `PROJECT_TASK` 연결 (S2, `06` §5.3 4번, SP-1~SP-3)**
- 공통 Given: 후보 skill 1개(학습 목표 focus skill → `projectNeed`), planning (K, I) = (3, 3), energy `NORMAL`, 조건을 만족하는 CHALLENGE·reading 없음

| 사이드 프로젝트 | main task |
|---|---|
| 없음 (온보딩에서 건너뜀) | `EXPLAIN` — `PROJECT_TASK`를 만들지 않는다 |
| P1 `ACTIVE`(이름 "주문 시스템") | `PROJECT_TASK`, `learning_task.side_project_id = P1`, title `주문 시스템에 {skill.name} 적용하기`(SP-2), `TaskView.sideProjectId = P1` |
| P1 `ACTIVE`(`updated_at` = t1), P2 `ACTIVE`(t2 > t1) | `PROJECT_TASK`, `side_project_id = P2`(SP-3) |
| P1 `PAUSED`, P2 `DONE`만 | `EXPLAIN` |

- task를 만든 뒤 P1 이름을 바꿔도 그 task의 `side_project_id`·title은 그대로다(생성 시점 고정, `05` §8.1)

**S8. Coach 리뷰 대상 (S4)**
- `POST /coach/reviews` `{ …, sideProjectId: P1 }` → 202, `coach_review.side_project_id = P1`
- B의 프로젝트 id 또는 random UUID → 400 `VALIDATION_FAILED`(`errors[].code = REFERENCE_NOT_FOUND`, field `sideProjectId`), `coach_review` 0행, 두 응답 동일
- `sideProjectId` 없음 → 202, `side_project_id = null`

**S9. 마스킹 (S3)**
- `description`에 `AKIAIOSFODNN7EXAMPLE` → 201, 저장값·응답·로그에 원문 0건 / `stack`에 private key 블록 → 422 `SECRET_DETECTED_BLOCKED`, 행 없음
- `repoUrl`은 URL 필드라 마스킹하지 않는다

**S10. UI (SCR-PROJECTS)**
- 등록 → 목록에 표시, 수정·상태 변경·삭제 확인 대화상자, `repoUrl`은 텍스트 링크로만 표시(미리보기 요청 없음), 409 `CONCURRENT_MODIFICATION` 새로고침 안내(widget test)

**S11. 경험 기록 분류 `kind` (S3, I-23, `06` SP-3)**
- When `POST /side-projects`에 `kind`를 생략 → 201, `kind = SIDE`(기본값) / `{ kind: "PAST_WORK" }` → 201 그대로 / `{ kind: "OLD_JOB" }` → 400 `UNKNOWN_ENUM_VALUE`
- When `PATCH /side-projects/{P}` `{ kind: "PAST_WORK", version }` → 200, 이미 만들어진 `PROJECT_TASK`와 기록은 그대로 남는다
- Given P1 `ACTIVE`·`SIDE`(`updated_at` = t1), P2 `ACTIVE`·`PAST_WORK`(t2 > t1), 후보 skill 1개(AC-27 S7과 같은 fixture)
- When `POST /today/generate` → main `PROJECT_TASK`의 `side_project_id = P1`이다(가장 최근이지만 `PAST_WORK`인 P2를 **고르지 않는다**)
- Given `ACTIVE` 프로젝트가 `PAST_WORK` 하나뿐 → `PROJECT_TASK`를 만들지 않고 `EXPLAIN`으로 내려간다(SP-1과 같은 결과)
- And `PAST_WORK` 프로젝트에도 기록(`POST …/notes`)·러버덕 `PROJECT_WORK`·내보내기(AC-33 S11)는 그대로 된다
- And 상태 전이표가 없다 — `SIDE ↔ PAST_WORK`를 양방향으로 바꿀 수 있고 `status`와 독립이다(`04` §4.9)

---

## AC-28 코드 읽기

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-27, FR-07 (RC-1~RC-4 = `06` §9.5, 제안 분기 = `06` §5.3, 콘텐츠 = `19` §3.8·CV-80~CV-87) |
| Sprint | S3 |
| 검증 수준 | unit(vector), integration(content·DB CHECK), API E2E, UI |
| 테스트 클래스 | `TaskProposalPolicyTest`, `ContentValidatorTest`, `TodayPlanServiceIntegrationTest`, `InvariantConstraintIntegrationTest`, `ReadCodeFlowTest`, `read_code_screen_test.dart`, `content/tools/validate_content.py` 오류 fixture |

**S1. 제안 분기 vector (`06` §5.3)**
- 공통: energy `NORMAL`, 복귀 모드 아님, 조건을 만족하는 CHALLENGE 없음

| # | planning (K, I) | aiStatus | 선택 가능한 reading | projectNeed | 결과 |
|---|---|---|---|---|---|
| T-1 | (0, 0) | ENABLED | 있음 | N | `READING` (25분, difficulty 1) — KNOWLEDGE < 1이라 RC-3이 막는다 |
| T-2 | (1, 1) | ENABLED | `READ.PETCLINIC.CONTROLLER_SLICE.001` (15분) | N | `READ_CODE`, estimated **15**, difficulty **2** |
| T-3 | (1, 1) | DISABLED | 있음 | N | `READING` — AI 불가라 CHALLENGE·READ_CODE 모두 제외 |
| T-4 | (3, 3) | ENABLED | 없음(전부 완료) | Y | `PROJECT_TASK` (30분, difficulty 3, `ACTIVE` 사이드 프로젝트 있음) |
| T-5 | (3, 3) | ENABLED | 2개(`READ.MODULAR_MONOLITH.SECURITY_CONFIG.001`, `READ.MODULAR_MONOLITH.STOCK_UPDATE.001`, 둘 다 15분) | Y | `READ_CODE` — key ASC로 `READ.MODULAR_MONOLITH.SECURITY_CONFIG.001`, estimated 15 |

**S2. reading 선택은 결정적이다**
- Given skill `SPRING.TRANSACTION`, planning KNOWLEDGE 1. `content/curated-repos.yaml`에서 이 skill을 가진 은퇴하지 않은 reading은 key ASC로 `READ.MODULITH.EVENT_FAILURE.001` → `READ.MODULITH.EVENT_PUBLISH.001` → `READ.SPRING_FRAMEWORK.CGLIB_PROXY.001` → `READ.SPRING_FRAMEWORK.TX_INTERCEPTOR.001` (은퇴한 `READ.RESTBUCKS.PAYMENT_TX.001`·`READ.RESTBUCKS.REPOSITORY_TX.001`은 후보가 아니다)

| Given | 고른 reading |
|---|---|
| 이력 없음 | `READ.MODULITH.EVENT_FAILURE.001` |
| A가 `EVENT_FAILURE`의 `READ_CODE` task를 `COMPLETED` | `READ.MODULITH.EVENT_PUBLISH.001` |
| 위 + `EVENT_PUBLISH`가 D − 13에 제안됨(최근 14 plan-day) | `READ.SPRING_FRAMEWORK.CGLIB_PROXY.001` |
| 위 + `EVENT_PUBLISH`가 D − 14에 제안됨 | `READ.MODULITH.EVENT_PUBLISH.001` |
| 네 reading(`EVENT_FAILURE`, `EVENT_PUBLISH`, `CGLIB_PROXY`, `TX_INTERCEPTOR`) 모두 제외 | `READ_CODE` 없음 → `06` §5.3 3번(`READING`, KNOWLEDGE < 2) |

- 같은 입력으로 여러 번 생성해도 같은 reading이다(무작위 없음)

**S3. Today 생성 → task**
- Given T-2 조건, `POST /today/generate`
- Then main `task_type = READ_CODE`, `learning_task.reading_key = READ.PETCLINIC.CONTROLLER_SLICE.001`, `estimated_minutes = 15`, title `Spring PetClinic 읽기 — OwnerController.java 48~122줄`, description이 reading의 `question`으로 시작한다
- And `reason_codes`에 `READ_REAL_CODE`, 문구 `Spring PetClinic에서 같은 문제를 어떻게 풀었는지 먼저 봅니다`
- And `GET /today`의 `mainTask.readingKey`가 같은 key, `challengeId = null`, `sideProjectId = null`
- DB CHECK `learning_task_reading_key_type`(I-17): `task_type = READ_CODE`이고 `reading_key = null`인 INSERT → 위반 / `task_type = READING`이고 `reading_key`가 있는 INSERT → 위반

**S4. `GET /readings/{readingKey}` — 코드 본문 없음, 외부 요청 없음**
- `GET /readings/READ.PETCLINIC.CONTROLLER_SLICE.001` → 200: `repo.key = petclinic`, `repo.name = Spring PetClinic`, `repo.url = https://github.com/spring-projects/spring-petclinic`, `repo.pinnedCommit`(40자 소문자 hex), `repo.cloneHint`에 그 커밋의 `git checkout` 포함, `path = src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java`, `startLine = 48`, `endLine = 122`, `skills`에 `SPRING.MVC_REST`, `estimatedMinutes = 15`, `question`(40~300자), `lookFor` 3개
- And 응답 JSON의 key가 `05` §19.7 `CuratedReadingView`·`CuratedRepoView` 필드 목록과 정확히 일치한다(코드 본문 필드 없음)
- And `repo.url`을 요청 기록 stub 서버(WireMock) 주소로 바꾼 fixture 콘텐츠로 기동·조회 → 수신 요청 0건. `ai_call_log` 새 행 0
- `GET /readings/read.petclinic.x` → 400 `VALIDATION_FAILED`(`Pattern`, field `readingKey`) / `GET /readings/READ.NOPE.TOPIC.001` → 404 `RESOURCE_NOT_FOUND` / 토큰 없음 → 401
- B가 같은 key로 조회 → A와 같은 응답(사용자 소유 리소스가 아니다, AC-08 S2)
- 은퇴한 테스트 reading(`retired: true`, key는 `retired.readingKeys`에도 있음) → 200, `retired = true`, 좌표·질문이 그대로다(`19` §8.2). 은퇴하지 않은 reading은 `retired = false`
- 같은 skill에 은퇴한 reading만 있으면 `READ_CODE`를 제안하지 않는다(`06` §5.3 → READING 등으로 내려감)

**S5. 완료 조건 (RC-1)**
- Given `READ_CODE` task `T`가 `IN_PROGRESS`
- When 러버덕 세션 없이 `PATCH /today/tasks/{T}` `{ status: "COMPLETED", version }` → 409 `INVALID_STATE_TRANSITION`, 상태 변화 없음
- When `targetType = CODE_READING`, `targetId = T`인 세션이 `ABANDONED`뿐 → 409 `INVALID_STATE_TRANSITION`
- When 그 세션에 턴 1개 이상 후 `complete` → 세션 `COMPLETED`(정리 AI 실패로 `summarySkippedReason`이 있어도 `COMPLETED`). 이 시점에 T는 여전히 `IN_PROGRESS`다(정리는 과제 상태를 바꾸지 않는다, `05` §9.8) → 같은 PATCH → 200, `completed_at` 설정
- `IN_PROGRESS → DEFERRED`, `PLANNED → SKIPPED`는 러버덕 세션 없이 허용

**S5a. 읽기 평가 `readingFeedback` (선택, `05` §8.4)**
- Given S5처럼 T에 `COMPLETED` 러버덕 세션이 있다
- When `PATCH /today/tasks/{T}` `{ status: "COMPLETED", readingFeedback: "TOO_HARD", version }` → 200, `learning_task.reading_feedback = 'TOO_HARD'`
- When `readingFeedback` 없이 완료 → 200, `reading_feedback = null`
- When `{ status: "DEFERRED", readingFeedback: "BORING" }` 또는 `READ_CODE`가 아닌 task의 완료에 `readingFeedback` → 400 `VALIDATION_FAILED`(`errors[].code = VALUE_NOT_ALLOWED`, field `readingFeedback`), 상태·평가 변화 없음
- When `readingFeedback = "GREAT"` → 400 `UNKNOWN_ENUM_VALUE`
- And 평가를 저장해도 `user_skill_state`, `learning_event`, 다음 `POST /today/generate`의 후보·점수가 평가가 없을 때와 같다(규칙 입력이 아니다, `06` §5.3)
- And `GET /me/export`의 `dailyPlans[].tasks[]`에서 T 행의 `readingFeedback`이 저장값과 같다(소스 점검 입력, `19` §8.5)

**S6. AI 불가 시 제안하지 않는다 (`17` §3.10)**
- `aiStatus = DISABLED`(T-3) 또는 `BALANCE_EXHAUSTED` → `READ_CODE` task를 만들지 않는다
- 이미 만든 `READ_CODE` task의 `GET /readings/{readingKey}`는 AI 불가여도 200(읽기 안내까지는 동작)

**S7. 콘텐츠 검증 (`19` §3.8·§4.1 CV-80~CV-87)**
- 저장소의 `content/curated-repos.yaml`(저장소 9개·reading 41개 = 활성 36 + 은퇴 5) → `python3 content/tools/validate_content.py` ERROR 0, 백엔드 `ContentValidator` 기동 성공, 모든 저장소에 `pinnedCommit` 있음(CV-82 WARN 0)

| 오류 fixture (`19` §4.3) | 결과 |
|---|---|
| reading `repo: nosuchrepo` | CV-84 ERROR |
| `path: ../../etc/passwd`, `lines: [122, 48]` | CV-85 ERROR 2건 |
| `lines: [0, -3]` | CV-85 ERROR |
| `skillCodes`에 `NOPE.FAKE_SKILL` | CV-86 ERROR |
| `question` 7자 | CV-86 ERROR |
| reading key 중복 | CV-83 ERROR |
| `pinnedCommit: null` | CV-82 WARN |
| reading `retired: true`인데 key가 `retired.readingKeys`에 없음 | CV-83 ERROR |
| `retired.readingKeys`에 있는 key의 reading을 파일에서 지움 | CV-83 ERROR |

- ERROR가 있으면 CI `content` job 실패, 백엔드는 기동 실패

**S8. UI (SCR-READ-CODE)**
- `cloneHint`(복사 버튼)·`pinnedCommit`이 경로·질문보다 먼저 보이고, 코드 본문 위젯이 없다
- `license = UNSPECIFIED`인 저장소(`restbucks`)는 읽기만 하라는 안내를 보인다
- "러버덕으로 설명하기" → `POST /rubber-duck` `{ targetType: CODE_READING, targetId: T }` → SCR-RUBBER-DUCK. 완료 버튼은 409를 받으면 러버덕을 먼저 하라는 안내를 보인다(widget test)
- `READ_CODE` 완료 시트에 읽기 평가 칩 3개(도움 됐어요 · 어려웠어요 · 지루했어요)가 있고, 아무것도 고르지 않아도 "완료 기록"이 활성이다. 고른 칩은 요청 body `readingFeedback`에 들어가고, 고르지 않으면 필드가 없다. 다른 task 유형과 "여기까지 기록" 시트에는 칩이 없다(widget test)

---

## AC-29 교차 학습 (RV-INTERLEAVE)

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-11 (`06` §6.5 3단계, §6.6 vector) |
| Sprint | S2 |
| 검증 수준 | unit(vector), API E2E |
| 테스트 클래스 | `DueReviewSelectorTest`, `ReviewQueryServiceIntegrationTest`, `SeedReviewFlowTest` |

**S1. 재배치 vector (`06` §6.6, `A`·`B`·`C`는 skill)**

| # | 상황 | 입력 (1~2단계 결과) | 출력 |
|---|---|---|---|
| (a) | 같은 skill 5장 연속 | `R1(A) R2(A) R3(A) R4(A) R5(A) R6(B) R7(B) R8(B) R9(B) R10(B)` | `R1(A) R2(A) R6(B) R4(A) R5(A) R7(B) R3(A) R8(B) R9(B) R10(B)` — swap 2회, 끝의 `R8 R9 R10`(B 3연속)은 뒤에 바꿀 카드가 없어 남는다(RV-INTERLEAVE-S) |
| (b) | skill이 1종뿐 | `R1(A) R2(A) R3(A) R4(A) R5(A)` | 변화 없음 — swap 0회 |
| (c) | 이미 섞여 있음 | `R1(A) R2(B) R3(A) R4(B) R5(C) R6(A)` | 변화 없음 — swap 0회 |

- 같은 입력을 100번 실행해도 출력이 같다(RV-INTERLEAVE-D — 무작위·셔플·해시 순서 없음)

**S2. 카드 집합과 장수는 바뀌지 않는다 (RV-INTERLEAVE-C)**
- Given A의 `ACTIVE` due 카드 25장(skill A 15장, skill B 10장)
- When `GET /reviews/due`
- Then 20장이고, 그 id 집합은 `06` §6.5 1단계 정렬 → 2단계 cap 20으로 고른 집합과 같다. 응답 순서는 그 20장에 RV-INTERLEAVE를 적용한 순서다
- Given 복귀 모드 → 10장, 집합은 cap 10으로 고른 것과 같다
- `reviewMinutes`·REVIEW task의 `dueReviewCount`는 재배치 전과 같다

**S3. 출제 순서**
- SCR-REVIEW-SESSION은 `GET /reviews/due` 응답 순서대로 카드를 낸다(클라이언트가 다시 정렬하지 않는다, widget test)

---

## AC-30 기한 역산 확장 제안

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-05 (`06` §4.4 6단계, `05` §7.7·§7.8) |
| Sprint | S2 |
| 검증 수준 | unit(vector), API E2E, UI |
| 테스트 클래스 | `ReplanSuggestionPolicyTest`, `ReplanServiceIntegrationTest`, `ReplanFlowTest`, `replan_screen_test.dart` |

**S1. 확장 vector (`06` §4.4 vector 3·4)**

| vector | Given | `expansionSuggestions` | `riskAfterSuggestions` |
|---|---|---|---|
| 3 | effective 5000, requiredMust 3400(LOW, `ratioBp` 6800). 복원 후보 X(0.80, 600분) → Y(0.65, 900분) → Z(0.65, 1500분). 상향 후보 T1(0.90, +500분) → T2(0.85, +400분) | `RESTORE_DEFERRED X`, `RAISE_TARGET T1` — X 확정(8000), Y에서 복원 루프 종료(9800, Z는 보지 않음), T1 확정(9000, 경계 포함), T2에서 상향 루프 종료(9800) | requiredMust 3900, `ratioBp` 7800, LOW |
| 4 | effective 2000, requiredMust 1300(LOW, 6500), deferred 없음. 상향 후보 P(0.90, 네 축 target 모두 5) → Q(0.80, +300분) → R(0.70, +300분) | `RAISE_TARGET Q`만 — P는 건너뛰고(상한 5), Q 확정(8000), R에서 종료(9500) | requiredMust 1600, `ratioBp` 8000, LOW |

- `riskAfterSuggestions`는 `requiredMust`만으로 계산한다(복원한 SHOULD/LATER는 들어가지 않는다). 종료 조건의 `expandedRatioBp`와 다른 수치다
- `RAISE_TARGET`의 `newTarget = currentTarget + 1 ≤ 5`, `axis`는 target < 5인 축 중 planning이 가장 낮은 축(동점이면 K, I, E, D)

**S2. 방향은 한쪽뿐이다 — 축소와 배타 (`06` §4.4 표)**

| effective / requiredMust | risk, `ratioBp` | `mustTargetReductionSuggestions`·`deferSuggestions` | `expansionSuggestions` |
|---|---|---|---|
| 5000 / 5900 | HIGH, 11800 | 있음(vector 1) | `[]` |
| 5000 / 3400 | LOW, 6800 | `[]` | 있음(vector 3) |
| 5000 / 3500 | LOW, 7000 | `[]` | 6단계로 간다(경계 포함) |
| 5000 / 3550 | LOW, 7100 | `[]` | `[]` |
| 5000 / 4500 | MEDIUM, 9000 | `[]` | `[]` |
| 0 / 0 | LOW, null | `[]` | `[]` |

- 어느 응답에서도 두 방향이 동시에 비어 있지 않은 경우는 없다

**S3. 미리보기는 저장하지 않는다**
- `POST /plans/{planId}/replan/preview`(vector 3 fixture) → 200, `expansionSuggestions` 2개(`kind`, `skill`, `priority` — X는 SHOULD 또는 LATER, T1은 MUST —, `practicalImportanceBp`, `addedMinutes` 600·500, T1은 `axis`·`currentTarget`·`newTarget`)
- And `learning_plan`, `plan_skill_target`, `plan_progress_snapshot`, `idempotency_record` 행 수 변화 0, X·T1의 target·`deferred` 변화 없음(자동 적용 없음)

**S4. 수락 — `restoredDeferrals`·`acceptedTargetRaises`**
- When `POST /plans/{planId}/replan` `{ …, restoredDeferrals: ["<X>"], acceptedTargetRaises: [ { skillCode: "<T1>", axis: <S3의 axis>, newTarget: <currentTarget + 1> } ] }`
- Then 201, 새 plan에서 X `deferred = false`·`adjustment = USER_EDITED`, T1의 그 축 target = `newTarget`·`adjustment = USER_EDITED`
- And 새 plan 기준 `GET /plans/active/budget`: `requiredMustMinutes = 3900`, `ratioBp = 7800`, `riskLevel = LOW`(vector 3의 `riskAfterSuggestions`와 같다, effective 5000 fixture)
- When 한 skill에 다른 축의 축소(`acceptedTargetReductions`)와 상향(`acceptedTargetRaises`)을 함께 → 201, 그 skill `adjustment = TARGET_REDUCED`

| `acceptedTargetRaises[i]` (현재 target 3) | 결과 |
|---|---|
| `newTarget = 3` (같음) | 400 `VALIDATION_FAILED`(`errors[].code = TARGET_NOT_RAISED`) |
| `newTarget = 2` (낮음) | 400 (`TARGET_NOT_RAISED`) |
| `newTarget = 6` | 400 `VALIDATION_FAILED`(`@Max(5)`) |
| 같은 `(skillCode, axis)` 2번 | 400 (`DUPLICATE_VALUE`) |
| 같은 `(skillCode, axis)`가 `acceptedTargetReductions`에도 있음 | 400 (`MUTUALLY_EXCLUSIVE`) |
| 같은 skill이 `acceptedDeferrals`에 있음 | 400 (`MUTUALLY_EXCLUSIVE`) |
| plan에 없는 skill | 400 (`SKILL_NOT_IN_PLAN`) |
| 없는 code | 400 (`SKILL_CODE_UNKNOWN`) |

- 400이면 새 plan 행 0, 이전 plan `ACTIVE`·target 그대로. preview에 같은 입력을 보내면 같은 400이다(`05` §7.7 1단계)

**S5. UI (SCR-REPLAN)**
- 여유가 있을 때 확장 제안 목록(복원·목표 상향)이 체크박스로 나오고, 체크한 항목이 `restoredDeferrals`·`acceptedTargetRaises`로 전송된다. 축소 제안 목록과 한 화면에 함께 나오지 않는다(widget test)

---

## AC-31 재현 과제 (AI 없이 다시 만들기)

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-28 (RE-1~RE-8 = `06` §5.10, HL-9 = `06` §9.1, 독립 구현 증거 = `06` §7.2) |
| Sprint | S4 |
| 검증 수준 | unit(vector), integration, API E2E, UI |
| 테스트 클래스 | `RedoTaskPolicyTest`, `TaskProposalPolicyTest`, `PlannerScoringTest`, `ReasonTemplatesTest`, `SkillLevelRulesTest`, `TodayPlanServiceIntegrationTest`, `RedoLockServiceIntegrationTest`, `InvariantConstraintIntegrationTest`, `RedoTaskFlowTest`, `today_redo_test.dart`, `redo_lock_test.dart` |

**S1. 제안 창 (RE-2)** — 설정 `min-days-after = 3`, `max-days-after = 7`, today = `D`.

- Given skill S의 `CHALLENGE` task T1(difficulty 2, 35분)이 `COMPLETED`이고 완료 plan-day가 아래와 같다
- Then `POST /today/generate`의 main task는

| T1 완료 plan-day | `daysBetween` | main |
|---|---|---|
| `D − 2` | 2 | `REDO` 아님 (창 전) |
| `D − 3` | 3 | **`REDO`** (경계 포함) |
| `D − 7` | 7 | **`REDO`** (경계 포함) |
| `D − 8` | 8 | `REDO` 아님 (창 지남 — 이 기회는 사라진다) |

- `REDO`일 때: `taskType = REDO`, `redoSourceTaskId = T1`, `redoSourceTaskType = CHALLENGE`, `estimatedMinutes = 35`(원본 그대로, RE-4), `title = "{T1 title} 혼자 다시 만들기"`, `reasons`에 `REDO_WITHOUT_AI`, `score_breakdown.modifiers`에 `{code: "REDO_DUE", multiplierBp: 13000}`, `reasonParams.redoDaysAfter` = 실제 일수
- `PROJECT_TASK`도 같은 규칙으로 원본이 된다. `READ_CODE`·`READING`·`EXPLAIN`·`RECALL`·`REVIEW`·`COACH_REVIEW`·`REDO`는 원본이 되지 않는다(RE-1)
- `skill_id`가 없는 원본은 후보가 아니다(RE-1)
- `aiStatus = DISABLED`여도 `REDO`는 그대로 제안된다(RE-8)

**S2. 한 원본에 하나씩 (RE-3)**

| 그 원본을 가리키는 REDO | 오늘 창 안에서 |
|---|---|
| 없음 | 후보 |
| `PLANNED` 또는 `IN_PROGRESS` 1개 | 후보 아님 |
| `COMPLETED` + `withoutAi = true` 1개 | 후보 아님 (끝났다) |
| `COMPLETED` + `withoutAi = false` 1개 | 후보 — 그 완료일부터 다시 3~7일 |
| `COMPLETED` + `false` 2개 | 후보 아님 (`max-attempts = 2`) |
| `SKIPPED` 1개 + `COMPLETED` + `false` 1개 | 후보 아님 (시도 2회) |
| `DEFERRED` 1개 | 후보 (시도로 세지 않는다) |

- 같은 skill에 재현 후보가 둘이면 `lastAttemptDate ASC → 원본 task.id ASC`로 첫 번째만 제안한다

**S3. 시간이 모자라면 오늘은 제안하지 않는다 (RE-4)**
- Given 원본 estimated 60분, `availableMinutes` 40 → `mainBudget` 40, `limit` 44
- Then main은 `REDO`가 아니라 `06` §5.3 1번부터 다시 고른 제안이고, 후보는 창 안에 남는다. 다음 날 `availableMinutes` 90으로 생성하면 `REDO`가 나온다

**S4. AI 잠금 (RE-5, HL-9)** — Given 그 challenge의 `REDO`가 `PLANNED` 또는 `IN_PROGRESS`

| 요청 | 결과 |
|---|---|
| `POST /challenge-attempts/{그 challenge의 attempt}/hints` | 409 `AI_ASSIST_LOCKED_FOR_REDO`. `hint_disclosure` 새 행 0, `HINT_DISCLOSED` 0건, AI 호출 0. 자기설명이 없어도 이 코드가 먼저다(HL-9 > HL-2) |
| `POST /rubber-duck` `{targetType: CHALLENGE, targetId: 그 attempt}` | 409 `AI_ASSIST_LOCKED_FOR_REDO`. `rubber_duck_session` 새 행 0, 기존 `IN_PROGRESS` 세션이 `ABANDONED`가 되지 않는다 |
| (원본이 `PROJECT_TASK`일 때) `POST /rubber-duck` `{targetType: PROJECT_WORK, targetId: 그 프로젝트}` | 409 `AI_ASSIST_LOCKED_FOR_REDO` |
| **다른** challenge의 hint, `REVIEW_ITEM`·`CODE_READING`·`CONCEPT` 러버덕 | 정상 |
| 그 challenge의 자기설명·제출·평가 | 정상 — 잠기는 것은 AI 도움뿐이다 |
| B(다른 사용자)의 같은 seed challenge hint | 정상 — 잠금은 사용자별이다 |
| `REDO`가 `COMPLETED`·`SKIPPED`·`DEFERRED`가 된 뒤 같은 요청 | 정상 |

**S5. 완료와 질문 (RE-6)**

| `PATCH /today/tasks/{taskId}` | 결과 |
|---|---|
| `{status: COMPLETED, version}` (답 없음) | 400 `VALIDATION_FAILED`(field `redoWithoutAi`, code `VALUE_REQUIRED`). 상태·이벤트·카드 변화 0 |
| `{status: COMPLETED, redoWithoutAi: true, version}` | 200. `redo_without_ai = true` |
| `{status: SKIPPED, redoWithoutAi: true, version}` | 400 (`VALUE_NOT_ALLOWED`) |
| `READ_CODE` task에 `{status: COMPLETED, redoWithoutAi: true, …}` | 400 (`VALUE_NOT_ALLOWED`) |

- DB CHECK `learning_task_redo_answer_required`가 JDBC 직접 INSERT도 거부한다(23514, I-21)

**S6. 성공만 증거가 된다 (RE-7, RE-8)**
- When `redoWithoutAi: true` → `REDO_COMPLETED` 1행(skill별, dedupe `REDO:{taskId}:{skillId}`), payload `{taskId, sourceTaskId, sourceTaskType, withoutAi: true, difficulty}`. `review_item` 새 행 0
- And 그 skill에 `CHALLENGE_EVALUATED` SOLVED_INDEPENDENTLY(difficulty ≥ 2)가 2개 더 있으면 IMPLEMENTATION 2 → 3 (`I3_SOLVED_INDEPENDENT`, 독립 구현 증거 3개·`evidenceKey` 3종)
- When `redoWithoutAi: false` → `REDO_COMPLETED{withoutAi: false}` 1행. 레벨 변화 없음. `review_item` 1장: `concept_key = REDO:{sourceTaskId}`, `source_type = REDO_TASK`, `origin = MANUAL`, `review_type = EXPLAIN`, `due_at = planDayStart(today + 1)`, skill = task의 skill
- And 같은 원본으로 두 번째 실패면 카드를 새로 만들지 않고 `due_at`만 당긴다(I-06)
- And `REDO_COMPLETED`는 `withoutAi` 값과 무관하게 `I4`·`I5`에 쓰이지 않는다(`06` §7.2)

**S7. UI (SCR-TODAY)**
- `REDO` 카드에 배지 "AI 없이 재현"과 잠금 줄 `today.redo.locked`가 `PLANNED`·`IN_PROGRESS` 모두에서 보인다. `IN_PROGRESS`에 "러버덕으로 설명하기" 버튼이 없다
- 완료 시트에 질문 "AI 도움 없이 끝냈나요?"와 버튼 둘이 있고, 고르기 전에는 "완료 기록"이 비활성이다. "아니요" 후 완료 → 토스트 + "복습하러 가기"
- SCR-TRAINING-ATTEMPT·SCR-RUBBER-DUCK이 `409 AI_ASSIST_LOCKED_FOR_REDO`를 받으면 AI 불가 배너가 아니라 버튼 비활성 + 사유 1줄 + "Today로 가기"다(widget test)

---

## AC-32 학습 트랙 2종

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-03, FR-02 (트랙 기본값 = `devpilot.tracks`, `06` §5.3 / 콘텐츠 = `19` §3.3·§3.4·§10.4) |
| Sprint | S3 |
| 검증 수준 | unit(vector), integration(content·DB CHECK), API E2E, UI |
| 테스트 클래스 | `TaskProposalPolicyTest`, `DevPilotPropertiesBindingTest`, `ContentValidatorTest`, `OnboardingServiceIntegrationTest`, `LearningGoalServiceIntegrationTest`, `DiagnosticSuggestionServiceIntegrationTest`, `onboarding_track_test.dart`, `content/tools/validate_content.py` |

**S1. 두 트랙이 모두 온보딩된다**
- When `POST /onboarding` `{learningGoal: {targetRole: "JAVA_BACKEND_STARTER", targetCompletionDate, focusSkillCodes}, …}`
- Then 201. `learning_goal.target_role = 'JAVA_BACKEND_STARTER'`, plan v1의 `title`은 그 트랙 템플릿의 `planTitle`, milestone 수는 그 템플릿의 수
- And `user_skill_state` 행은 **그 트랙에 role target이 있는 활성 non-root skill**에만 생긴다(`05` §4.1 5단계)
- And `GET /skills/tree?role=JAVA_BACKEND_STARTER`의 `roleTarget.priority = MUST` 수가 `JAVA_BACKEND`보다 **적다**, 그리고 같은 skill의 축별 목표 합이 입문 트랙에서 더 작거나 같다
- When `targetRole`에 없는 값 → 400 `UNKNOWN_ENUM_VALUE`. `focusSkillCodes`에 그 트랙의 role target이 없는 skill → 400 `SKILL_CODE_UNKNOWN`

**S2. 트랙은 바꿀 수 없다**
- When `PUT /learning-goal` `{targetRole: <다른 트랙>, …}` → 400 `VALIDATION_FAILED`(field `targetRole`, code `VALUE_NOT_ALLOWED`), `learning_goal` 변화 0
- When 같은 `targetRole`로 날짜만 변경 → 200

**S3. 트랙이 planner를 바꾼다 (`06` §5.3 T-6~T-9)**
- Given planning (K, I) = (4, 4), 그 skill에 d5·d4·d3·d2 challenge가 있고 AI 사용 가능

| 트랙 | `maxTaskDifficulty` | 제안 |
|---|---|---|
| `JAVA_BACKEND` | 5 | `CHALLENGE` difficulty 5 |
| `JAVA_BACKEND_STARTER` | 3 | `CHALLENGE` difficulty 3 |

- Given CHALLENGE 없음, 선택 가능한 reading 있음

| 트랙 | `readCodeMinKnowledge` | planning K = 1 | planning K = 2 |
|---|---|---|---|
| `JAVA_BACKEND` | 1 | `READ_CODE` | `READ_CODE` |
| `JAVA_BACKEND_STARTER` | 2 | `READING` | `READ_CODE` |

- 그 밖의 규칙(factor·weight·modifier·시간 배분·복습 간격·레벨 갱신·budget)은 두 트랙에서 같은 입력에 같은 결과다

**S4. 트랙이 진단 제안을 바꾼다**
- `GET /diagnostics/suggestions`는 그 트랙에 role target이 있는 category만 제안하고, 제안하는 challenge의 `difficulty ≤ trackDefaults.maxTaskDifficulty`다. 입문 트랙에서는 difficulty 4·5 진단이 나오지 않는다

**S5. 설정 검증**
- `devpilot.tracks`에 `TargetRole` 값이 하나라도 빠지면 기동 실패. `max-task-difficulty`가 1~5 밖이거나 `read-code-min-knowledge`가 0~5 밖이면 기동 실패

**S6. 콘텐츠 검증**
- `ContentValidator`: 트랙마다 role target 파일 1개(CV-21 — 그 트랙의 non-root skill 하나당 target 1개)와 plan template 1개(CV-30), 그 트랙의 모든 MUST skill이 그 템플릿의 milestone에 있다(CV-36). 입문 트랙 파일을 빼면 기동 실패
- `content/tools/validate_content.py`도 같은 규칙으로 실패한다(오류 fixture, `19` §4.3)

**S7. 두 사용자는 서로를 보지 못한다**
- A(`JAVA_BACKEND`)와 B(`JAVA_BACKEND_STARTER`)가 모두 온보딩한 상태에서 `AuthorizationIsolationTest` 전체가 통과한다. B의 `GET /skills/me`·`GET /plans/active`·`GET /side-projects`·`GET /me/export` 어디에도 A의 항목이 없고, A의 트랙이 B의 어떤 계산에도 들어가지 않는다 (AC-08, `07` §4.3)

**S8. UI (SCR-ONBOARDING 1단계)**
- 라디오 2개, 기본 `JAVA_BACKEND`. 각 항목에 이름·한 줄 설명·필수 skill 수. 트랙을 바꾸면 3단계 입력이 초기화된다. SCR-LEARNING-GOAL에서는 읽기 전용이고 `onboarding.goal.track.locked`가 보인다(widget test)

---

## AC-33 사이드 프로젝트 결정·장애 기록

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-29 (PN-1~PN-4 = `06` §9.5, I-22 = `04` §7) |
| Sprint | S3 (기록 CRUD와 **Markdown 내보내기**, 증거 초안 연결 S6, 주간 지표 S5) |
| 검증 수준 | API E2E, integration, unit, UI |
| 테스트 클래스 | `SideProjectNoteServiceIntegrationTest`, `SideProjectNoteFlowTest`, `SideProjectNoteExportTest`, `InvariantConstraintIntegrationTest`, `MetricsCalculatorTest`, `EvidenceDraftTaskIntegrationTest`, `project_notes_screen_test.dart` |

**S1. 결정 기록 만들기**
- When `POST /side-projects/{P}/notes` `{ noteType: "DECISION", title: "주문 번호를 시퀀스 기반으로", occurredOn: <오늘>, skillCode: "<S>", decisionChoice, decisionOptions, decisionRationale }`
- Then 201 `SideProjectNoteView`: 요청값 그대로, `incidentSymptom`·`incidentDetection`·`incidentFix`·`incidentPrevention` 모두 `null`, `version = 0`, `createdAt = updatedAt`
- And `GET …/notes/{noteId}`가 같은 값, `GET …/notes`가 1건

**S2. 유형별 필수·금지 (PN-1, I-22)**

| 입력 | 결과 |
|---|---|
| `DECISION`인데 `decisionRationale` 없음 | 400 `VALIDATION_FAILED`(field `decisionRationale`, code `VALUE_REQUIRED`) |
| `DECISION`인데 `incidentSymptom` 있음 | 400 (`VALUE_NOT_ALLOWED`) |
| `INCIDENT`인데 넷 중 하나 없음 | 400 (`VALUE_REQUIRED`) |
| `INCIDENT`인데 `decisionChoice` 있음 | 400 (`VALUE_NOT_ALLOWED`) |
| 본문 항목이 공백만 | 400 (`VALUE_REQUIRED` — 앞뒤 공백 제거 후 판정) |
| `title` 없음 / 201자 / 본문 항목 4001자 | 400 `VALIDATION_FAILED` |
| `occurredOn` 내일 | 400 (`DATE_OUT_OF_RANGE`) |
| 없는 `skillCode` / 비활성 skill | 400 (`SKILL_CODE_UNKNOWN`) |
| 없는 `noteType` | 400 `UNKNOWN_ENUM_VALUE` |

- 400이면 `side_project_note` 새 행 0. JDBC로 CHECK를 직접 위반하면 23514(I-22)

**S3. 목록·필터·페이지**
- Given N1(`INCIDENT`, `occurredOn = D−1`), N2(`DECISION`, `D−3`), N3(`DECISION`, `D−1`, id가 N1보다 작음)
- `GET …/notes` → N1, N3, N2 (`occurredOn` DESC, `id` DESC) / `?noteType=DECISION` → N3, N2 / `?noteType=BUG` → 400 `UNKNOWN_ENUM_VALUE` / `limit=2` → 2건 + `nextCursor`, 다음 페이지 1건

**S4. 수정 — 유형은 바꿀 수 없다 (PN-2)**
- `PATCH …/notes/{N}` `{title: "…", version: 0}` → 200, `version = 1`, `updatedAt` 갱신
- 같은 값으로 다시 → 200, `version`·`updatedAt` 그대로(바뀐 필드 없음)
- body에 `noteType` → 400 `MALFORMED_REQUEST`(알 수 없는 속성)
- 유형에 필요한 항목에 `""` → 400 `VALUE_REQUIRED` / 유형에 맞지 않는 항목에 값 → 400 `VALUE_NOT_ALLOWED`
- `{skillCode: "", version}` → 200, `skill = null` / 이전 `version` → 409 `CONCURRENT_MODIFICATION`

**S5. 삭제와 cascade**
- `DELETE …/notes/{N}` → 204, 다시 → 404 `RESOURCE_NOT_FOUND`
- 기록 2개가 있는 프로젝트에 `DELETE /side-projects/{P}` → 204, `side_project_note` 0행(`04` §8)

**S6. 소유권 (AC-08)**
- B가 A의 `sideProjectId`로 `GET`·`POST …/notes` → 404 `RESOURCE_NOT_FOUND`, 새 행 0
- B가 **본인 프로젝트 id + A의 noteId**로 `GET`·`PATCH`·`DELETE` → 404, A의 기록은 `version`·내용 그대로
- B의 `GET …/notes`에 A 항목 0건

**S7. Masking (AC-14)**
- 본문 항목에 런타임 조합 fake secret → 201, 저장값·응답·로그에 원문 0건
- 어느 항목이든 private key 블록 → 422 `SECRET_DETECTED_BLOCKED`, 행 0, 감사 로그 `SECRET_BLOCKED`
- `title`도 마스킹 대상이다(`05` §1.11)

**S8. 레벨·계획에 영향이 없다 (PN-3)**
- S1~S7 어디에서도 `learning_event`·`user_skill_state`·`skill_state_change`·`daily_plan` 행이 바뀌지 않는다

**S9. 나중 기능으로 이어진다**
- (S5) `MetricsCalculator`: 기간 내 `occurredOn`인 기록 수가 `weekly_review.metrics_json.projectNoteCount`에 들어간다
- (S6) `POST /evidence/drafts` `{sourceProjectNoteId: N}` → 202. `evidence_candidate.skill_id` = 기록의 skill(없으면 null), `ai_draft_json.sourceProjectNoteId = N`. AI 입력은 그 기록의 **마스킹본**이다
- `POST /evidence/drafts`에 둘 다 없으면 400 `ONE_OF_REQUIRED`, 둘 다 있으면 400 `MUTUALLY_EXCLUSIVE`, B의 기록 id면 400 `REFERENCE_NOT_FOUND`

**S10. UI (SCR-PROJECT-DETAIL, SCR-PROJECT-NOTE-EDIT)**
- 유형 필터 3개, 카드에 유형 배지·날짜·본문 첫 항목 2줄·skill 칩. "+ 결정 기록"·"+ 장애 기록"이 각각 `noteType` query로 이동
- 편집 화면에 **유형을 바꾸는 입력이 없다**. 유형별 입력 항목이 3개·4개이고 전부 필수, 비면 "저장" 비활성. 날짜 선택기가 오늘 이후를 막는다
- 편집 화면 상단에 안내 문구 "회사 소스·고객 정보는 적지 마세요. 상황과 판단만 적어요."가 보인다
- `422` → 인라인 `projectNote.secretBlocked`, `409` → 최신 값으로 다시 채우고 토스트(widget test)

**S11. Markdown 내보내기 (`05` §19.13)**
- Given P에 기록 3건(`occurredOn` = D−3 `DECISION`, D−1 `INCIDENT`, D−1 `DECISION`)과 skill이 붙은 기록 1건
- When `GET /side-projects/{P}/notes/export`
- Then 200, `Content-Type: text/markdown; charset=UTF-8`, `Content-Disposition: attachment; filename="notes-{P}-{오늘 plan-day yyyyMMdd}.md"`
- And 본문은 프로젝트 이름 제목 + 기록마다 `## {occurredOn} {title}`이고 순서는 **`occurredOn` ASC → `id` ASC**(목록 API의 DESC와 반대다), 페이지네이션 없이 전부 들어간다
- And 유형별 소제목이 있다(`DECISION`: 고른 것·선택지·이유 / `INCIDENT`: 증상·발견·조치·재발 방지), skill이 있는 기록의 마지막 줄이 `기술: {skillCode}`다
- And 본문은 저장된 **마스킹본** 그대로다 — S7에서 마스킹된 값이 파일에도 마스킹된 채로 나오고 원문은 0건이다
- And 감사 로그 `DATA_EXPORTED` 1건, `ai_call_log` 새 행 0
- When 기록이 0건인 프로젝트 → 200이고 제목만 있는 문서(빈 파일이 아니다)
- When B가 A의 `sideProjectId`로 호출 → 404 `RESOURCE_NOT_FOUND`(random UUID와 같은 응답, AC-08)
- And `PAST_WORK` 프로젝트에서도 같게 동작한다(AC-27 S11)

---

## AC-34 오늘의 팁

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-07, FR-11 (선택 규칙 TIP-1~TIP-6 = `06` §5.12, API = `05` §20.2~§20.4, 콘텐츠 = `19` §3.9) |
| Sprint | S3 |
| 검증 수준 | unit(vector), integration, API E2E, UI |
| 테스트 클래스 | `DailyTipSelectorTest`, `DailyTipServiceIntegrationTest`, `DailyTipFlowTest`, `ContentValidatorTest`(CV-90~CV-96), `tip_detail_screen_test.dart` |

공통 Given: today = D. 팁 4개 — `TIP.DATABASE.INDEX.001`(PRACTICAL, `[DATABASE.INDEX]`), `TIP.LOGGING.LEVELS.001`(PRACTICAL, `[PRACTICAL_ENGINEERING.LOGGING]`), `TIP.LOGGING.LEVELS.002`(BASIC, 같은 skill), `TIP.OPERATIONS.HEALTHCHECK.001`(BASIC, `[DEVOPS.DOCKER]`). A의 트랙은 `JAVA_BACKEND`(`basicTipsFirst = false`). 이 AC의 모든 호출에서 `ai_call_log` 새 행은 0이다.

**S1. 선택 규칙 vector**
- `06` §5.12 `06-05-daily-tip.yaml`의 TIP-V1~TIP-V11이 모두 parameterized test로 통과한다(묶음 1~4 순서, 묶음 안 정렬, TIP-1·TIP-2 제외, 트랙별 `basicTipsFirst`)

**S2. 하루 1개 (TIP-3·TIP-4)**
- Given 오늘 main task의 skill = `DATABASE.INDEX`, `user_daily_tip` 0행
- When `GET /tips/today` → 200 `DailyTipView`, `tipKey = TIP.DATABASE.INDEX.001`, `shownOn = D`, `feedback = null`
- And `user_daily_tip` 1행(`user_id = A`, `shown_on = D`, `feedback = null`), `TIP_VIEWED` 학습 이벤트 1건(payload `{tipKey, series, level}`, `skill_id` = `skillCodes`의 첫 활성 skill)
- When 같은 D에 다시 `GET /tips/today` → **같은 팁**, 새 행·새 이벤트 0
- When D+1에 호출 → 다른 팁이 뽑히고 D의 팁은 TIP-2로 후보에서 빠진다
- Given 후보가 하나도 없다(모두 `retired` 또는 이미 받음) → 404 `RESOURCE_NOT_FOUND`, `user_daily_tip` 새 행 0, 이벤트 0
- Given 서로 다른 `Idempotency-Key`로 동시 2회 → `user_daily_tip_unique` 위반은 오류가 아니라 같은 응답이고 행은 1개다(I-26)
- And `TIP_VIEWED`는 레벨 규칙의 입력이 아니다 — 위 시나리오 후 `skill_state_change` 0행

**S3. 읽은 뒤 선택 (`POST /tips/{tipKey}/feedback`)**

| 상황 | 결과 |
|---|---|
| 보여 준 팁에 `{feedback: "LEARNED"}` | 201 `DailyTipView`(`feedback = LEARNED`), `review_item` 1행: `concept_key = TIP:{tipKey}`, `review_type = EXPLAIN`, `source_type = MANUAL`, `origin = MANUAL`, `skill_id` = 첫 활성 skill, 첫 due = `planDayStart(D + 1)` |
| 같은 팁에 `{feedback: "KNEW_IT"}`를 또 보냄 | **200**, 저장값은 `LEARNED` 그대로(덮어쓰지 않는다), 카드 추가 없음 |
| `{feedback: "WILL_TRY"}`(다른 팁) | 201, 카드 없음. D+1의 `GET /today`·`POST /today/generate` 응답에 `tipExperiment` 1건(`estimatedMinutes = 25`), 그날(D)에는 붙지 않는다 |
| `{feedback: "KNEW_IT"}`(다른 팁) | 201, 카드·후보 없음. 이후 그 팁은 다시 제안되지 않는다 |
| 보여 준 적 없는 팁 key | 404 `RESOURCE_NOT_FOUND`(읽기 전에 고를 수 없다) |
| `tipKey = "TIP.bad"` | 400 `VALIDATION_FAILED`(field `tipKey`, code `Pattern`) |
| registry에 없는, 형식이 맞는 key | 404 `RESOURCE_NOT_FOUND` |
| `{feedback: "GOOD"}` | 400 `UNKNOWN_ENUM_VALUE` |
| `LEARNED`인데 팁의 `skillCodes`에 활성 skill이 없음 | 201, 카드 0행(`review_item.skill_id`가 not null이다) |

- `WILL_TRY` 팁은 **`learning_task`를 만들지 않는다**: D+1의 `daily_plan`·`learning_task` 행 수와 main task 선택·`score_breakdown`이 팁이 없을 때와 같다(TIP-6)
- `LEARNED` 카드는 용어 카드와 달리 `source_type = MANUAL`이다(TIP-5)
- 같은 `concept_key`의 활성 카드가 이미 있으면 새로 만들지 않고 due만 당긴다(`06` §6.3 마지막 행)

**S4. 목록 (`GET /tips`)**
- `retired = false`인 팁 전체가 `tipKey` ASC로 나오고, 이미 본 팁도 남는다(제외 규칙은 `/tips/today`에만 적용)
- `?series=LOGGING` → 그 시리즈만 / `?level=BASIC` → 그 난이도만 / 둘 다 주면 AND / `?series=NOPE` → 400 `UNKNOWN_ENUM_VALUE` / 잘못된 cursor → 400 `INVALID_CURSOR`
- A와 B의 목록 본문이 같고 `feedback`만 각자의 값이다(공용 콘텐츠 조회)

**S5. 콘텐츠 검증 (`19` §3.9·§4.1)**
- CV-90(키 패턴·유일·`series`가 키의 두 번째 세그먼트), CV-91(`sourceUrl` 또는 `experiment` 필수), CV-92(https·신뢰 호스트), CV-93(`skillCodes` 실재), CV-94(길이·예제 15줄), CV-95(은퇴 목록 일치)가 오류 fixture에서 실패한다. CV-96은 WARN
- 은퇴한 팁(`retired: true`)은 새로 선택되지 않지만 이미 받은 사용자에게는 그대로 보인다(§20.2)

**S6. UI (SCR-TODAY·SCR-TIP-DETAIL·SCR-TIPS)**
- SCR-TODAY 팁 카드에 제목과 `symptom` 2줄, "자세히" → SCR-TIP-DETAIL. 팁이 없으면(404) 카드 영역 자체가 없다
- SCR-TIP-DETAIL은 증상 → 원인 → 예제 → 확인할 곳 → 5분 실험 순서로 보이고 하단에 선택 3개가 있다. 고른 뒤 다시 들어가면 그 선택이 표시되고 재전송하지 않는다
- feature flag `tips`가 꺼져 있으면 팁 카드와 진입점이 모두 숨는다(widget test)

---

## AC-35 용어 사전

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-11 (API = `05` §20.5~§20.7, 콘텐츠 = `19` §3.10) |
| Sprint | S3 |
| 검증 수준 | integration, API E2E, UI |
| 테스트 클래스 | `TermQueryServiceIntegrationTest`, `TermCardFlowTest`, `ContentValidatorTest`(CV-100~CV-106), `term_detail_screen_test.dart` |

공통 Given: 용어 `TERM.DATABASE.COLUMN`(대표 표기 "컬럼", `english: column`, `aliases: [열, 칼럼]`, `skillCodes: [DATABASE.MODELING]`, `confusableWith: [TERM.DATABASE.FIELD]`). AI 호출 0.

**S1. 검색 (`GET /terms`)**
- `?q=칼럼` → `aliases` 부분 일치로 `TERM.DATABASE.COLUMN` 1건, 응답의 `representative`는 **"컬럼"**이다(대표 표기 하나)
- `?q=COL` → `english` 부분 일치(대소문자 무시), 앞뒤 공백은 제거하고 비교한다
- `q` 없음 → 전체 목록, 정렬은 `termKey` ASC, cursor paging
- `?skillId={DATABASE.MODELING의 id}` → 포함, 없는 `skillId`(random UUID) → **빈 목록**(오류가 아니다), `q`와 함께 주면 AND
- `q`가 101자 → 400 `VALIDATION_FAILED`(`Size`), `?skillId=abc` → 400(`TYPE_MISMATCH`)
- `retired: true`인 용어는 검색되지 않는다
- `TermSummaryView.cardCreated`는 요청 사용자 기준이고 A·B가 서로 다르다

**S2. 상세 (`GET /terms/{termKey}`)**
- 200 `TermView`: 대표 표기·영어·`aliases`·정의·예문·`sourceUrl`·`level`, `confusableWith`가 `TermRefView`로 펼쳐진다(registry에 없는 key는 빠진다)
- `cards`는 그 사용자의 `TERM:{termKey}` 접두사 카드를 `concept_key` ASC로. 만들기 전에는 `[]`
- 은퇴한 용어도 200이다(검색만 빠진다) / `termKey = "TERM.bad.x"` → 400 `Pattern` / 없는 key → 404 `RESOURCE_NOT_FOUND`

**S3. 복습 카드 만들기 (`POST /terms/{termKey}/card`)**
- When 처음 호출 → **201**, `createdCount = 2`, `cards` 2건
- And `review_item` 2행: `TERM:{termKey}`(prompt = 대표 표기 + 영어, expected = 정의 + 예문)와 `TERM:{termKey}:REVERSE`(prompt = 정의, expected = 대표 표기 + 다른 표기). 둘 다 `review_type = RECALL`, `source_type = TERM`, `origin = MANUAL`, `source_id = null`, `skill_id` = 첫 활성 skill, `rubric_json` 항목 1개, `due_at = planDayStart(D + 1)`, `interval_days = 1`
- And `TERM_CARD_CREATED` 학습 이벤트 1건(payload `{termKey, conceptKey, cardCount}`, dedupe `TERM_CARD:{termKey}:{skillId}`), `skill_state_change` 0행(레벨 규칙의 입력이 아니다)
- When 같은 용어로 다시 호출(새 `Idempotency-Key`) → **200**, `createdCount = 0`, 문항이 덮어써지지 않고 due도 그대로다
- Given 정방향 카드만 남긴 상태(역방향 삭제) → 201, `createdCount = 1`
- Given 활성 skill이 하나도 없는 용어 → 200, `cards = []`, `createdCount = 0`
- When 같은 `Idempotency-Key`로 2회 → 두 번째는 재생(`Idempotent-Replayed: true`), 행 수 변화 없음(AC-23)
- And 다음 plan-day `GET /reviews/due`에 두 카드가 나오고 복습 흐름(AC-05)이 그대로 동작한다
- And B가 같은 용어로 카드를 만들어도 A의 카드는 그대로다(공용 콘텐츠·사용자별 카드)

**S4. 콘텐츠 검증 (`19` §3.10·§4.1)**
- CV-100~CV-103·CV-105가 오류 fixture에서 실패한다. **CV-106**: `definition` 안에 그 용어의 대표 표기나 `aliases`가 들어가면 실패한다(역방향 카드의 답이 문제에 나온다)
- CV-104(WARN): 다른 콘텐츠 본문에 "칼럼"이 남아 있으면 파일·키 경로와 함께 경고한다

**S5. UI (SCR-TERMS·SCR-TERM-DETAIL)**
- 검색어를 넣으면 목록이 좁혀지고, 결과가 없으면 빈 상태 문구가 보인다
- 상세에서 "복습 카드 만들기" → 만든 수 토스트, 이미 두 장이 있으면 만든 상태로 보인다. `confusableWith` 항목을 누르면 그 용어 상세로 이동한다(widget test)
- feature flag `terms`가 꺼져 있으면 진입점과 "복습 카드 만들기"가 숨는다

---

## AC-36 주간 요약 · 연속 학습 일수

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-16 (`05` §13.1) |
| Sprint | S3 |
| 검증 수준 | integration, API E2E, UI |
| 테스트 클래스 | `DashboardQueryServiceIntegrationTest`, `dashboard_screen_test.dart` |

**S1. 이번 주 요약 (`weeklySummary`)**
- Given D = 수요일. 이번 주 월요일 = `weekStartDate`. 월요일에 `CHALLENGE` 완료 1건, 화요일에 `PROJECT_TASK` 완료 1건과 `REVIEW` task 완료 1건, 수요일에 `side_project_note` 2건, 세션 `actual_minutes` 합 75분. **지난주 일요일**에도 완료 과제 1건이 있다
- When `GET /dashboard`
- Then `weeklySummary.builtThisWeek`가 2건(`CHALLENGE`·`PROJECT_TASK`만 — `REVIEW`는 빠진다)이고 정렬은 `plan_date` DESC → `sort_order` DESC, 최대 5개다. **지난주 일요일 항목은 들어가지 않는다**
- And `completedTasks = 3`(REVIEW 포함), `notesWritten = 2`, `studyMinutes = 75 = weekStudyMinutes`
- And 응답 필드 순서가 `builtThisWeek` → `completedTasks` → `notesWritten` → `studyMinutes`다(만든 것이 먼저다)
- And 기준은 **이번 주 월요일부터**다: `weekStartDate` = D가 속한 ISO week의 월요일이고 최근 7일 창이 아니다(월요일에 조회하면 그날 하루만 집계된다)
- And AI 문장이 없다 — 응답에 요약 문장 필드가 없고 `ai_call_log` 새 행 0(DEC-34)
- And `REDO` 완료 과제(S4)도 `builtThisWeek`에 들어간다

**S2. 연속 학습 일수 (`streakDays`)**

| Given (완료한 `learning_task`가 있는 plan-day) | `streakDays` |
|---|---|
| 기록 없음 | 0 |
| D에 1건 | 1 |
| D−1, D−2, D−3에 각 1건이고 D는 아직 없음 | 3 (오늘은 아직 끊긴 날이 아니다 — 어제부터 센다) |
| D, D−1, D−2에 각 1건, D−3 없음 | 3 |
| D−2, D−3에 있고 D−1·D 없음 | 0 (어제도 없으면 0) |
| 367일 연속 | 366 (세는 범위 상한) |

- plan-day 경계는 `dayStartHour`를 따른다(AC-17) — D의 02:00 KST(`dayStartHour = 4`) 조회는 전날 plan-day 기준이다
- 세는 단위는 **완료한 과제가 있는 날**이고 세션·복습 답변만 있는 날은 세지 않는다

**S3. 빈 상태와 격리**
- 온보딩 직후(과제·기록 0) → `streakDays = 0`, `builtThisWeek = []`, `completedTasks = 0`, `notesWritten = 0`, `studyMinutes = 0`. 200이고 null이 아니다
- B의 `GET /dashboard`에 A의 과제·기록이 0건 반영된다(AC-08 S2)

**S4. UI (SCR-DASHBOARD)**
- 이번 주 요약이 **만든 것 → 끝낸 것 → 적은 것 → 시간** 순서로 보이고 연속 학습 일수가 함께 표시된다. 쉰 날 수·목표 대비 부족 퍼센트·빨간 경고 숫자는 없다(U-3)
- `builtThisWeek`가 비면 그 영역에 다음 행동 1개를 안내하는 빈 상태 문구가 보인다(widget test)

---

## AC-37 개념 익히기 (개념 노트 · 학습 단위)

| 항목 | 값 |
|---|---|
| 관련 요구사항 | FR-07 (`05` §21, `19` §3.14, `02` SCR-LESSON·SCR-LESSON-LIST) |
| Sprint | S3 |
| 검증 수준 | integration, API E2E, UI |
| 테스트 클래스 | `LessonQueryServiceIntegrationTest`, `LessonControllerIntegrationTest`, `UnitAnswerMatcherTest`, `lesson_screen_test.dart`, `lesson_list_screen_test.dart` |

**S1. 노트를 조회해도 답이 오지 않는다**
- Given 단위 3개짜리 노트 1개가 콘텐츠에 있다
- When `GET /lessons/{lessonKey}`
- Then 200이고 `units`가 3개이며 각 단위에 `explain`·`example`·`predict.question`·`complete.question`·`problem.prompt`·`problem.hints`가 있다
- And 응답 JSON 어디에도 `answer`·`answers`·`modelAnswer`·`selfChecks` 필드가 **없다**
- And `progress`는 기록이 없으므로 null이다

**S2. 예측·빈칸은 서버가 즉시 채점한다 (AI 없음)**
- Given 단위의 `predict.answer`가 `index`, `complete.answers`가 `[["@GetMapping"], ["/orders"]]`
- When `POST /lessons/{k}/units/{u}/predict` `{"answer": "  index  "}`
- Then 200 `{correct: true, expected: "index", explanation: ...}` — 앞뒤 공백을 버리고 비교한다
- When `{"answer": "Index"}`
- Then `correct: false`다 — **대소문자를 구분한다**
- When `POST …/complete` `{"answers": ["@GetMapping"]}` (빈칸은 2개)
- Then 400 `VALIDATION_FAILED`(field `answers`, code `Size`)
- And 이 두 endpoint를 부르는 동안 `ai_call_log`에 새 행이 0이다

**S3. 모범 답안은 조회로 보고, 사용자 답은 서버로 가지 않는다**
- When `GET /lessons/{k}/units/{u}/answer`
- Then 200에 `modelAnswer`와 `selfChecks`가 있다
- And 이 요청은 상태를 바꾸지 않는다 — `learning_event`가 늘지 않는다
- And 요청 본문이 없다: 사용자가 쓴 답을 서버로 보내지 않고 어느 테이블에도 저장하지 않는다

**S4. 단위를 마치면 기록이 한 번 남는다**
- When `POST …/finish` `{"helpLevel": "HINT", "selfChecksMet": 2}` + `Idempotency-Key`
- Then 200이고 `learning_event`에 `UNIT_SOLVED` 1건이 생긴다. `skill_id`는 노트의 skill, payload는 `{lessonKey, unitKey, helpLevel: "HINT", selfChecksMet: 2}`다
- And 같은 `Idempotency-Key`로 다시 부르면 같은 응답이고 이벤트는 늘지 않는다
- And 다른 키로 다시 마치면 이벤트가 하나 더 쌓인다(다시 풀기) — **이전 이벤트를 고치지 않는다**
- And `selfChecksMet`을 생략하면 payload에 그 칸이 없다
- And `selfChecksMet`이 `selfChecks` 개수보다 크면 400 `VALIDATION_FAILED`

**S5. 레벨은 오르지 않는다**
- Given S4를 마친 직후
- When `GET /skills/{skillId}`
- Then `evidenceLevels`가 그대로다 — 단위를 풀었다고 skill 레벨이 오르지 않는다(`01` 원칙 4)

**S6. 화면은 한 걸음씩 간다 (UI)**
- Given SCR-LESSON을 연다
- Then 처음 화면에 `whyItMatters`와 단위 목록이 보이고 예제·정답은 보이지 않는다
- And "예제 보기"를 누르기 전에는 `example.code`가 화면에 없다
- And 5번 걸음에서 힌트는 **한 번에 하나씩** 열리고, 모범 답안 버튼은 힌트를 다 연 뒤에 열린다
- And "이미 안다 → 문제부터"를 누르면 5번 걸음으로 건너뛴다
- And 코드 블록은 가로 스크롤이고 360px에서 화면이 넓어지지 않는다(§2.4)

**S7. 없는 key**
- When `GET /lessons/LESSON.NOPE.001`
- Then 404 `RESOURCE_NOT_FOUND`
- When key 형식이 어긋나면 400 `VALIDATION_FAILED`(code `Pattern`)
- And 토큰이 없으면 401 `UNAUTHORIZED`

**S8. 막힌 자리에서 노트로 간다 (§21.3, UI)**
- Given `SPRING.MVC_REST`에 노트가 있고 `DATABASE.INDEX`에는 없다
- When `GET /skills/{skillId}/lesson`을 노트가 있는 skill로 부른다
- Then 200이고 그 skill의 `LessonView`다. 노트가 없는 skill과 없는 skill id는 404 `RESOURCE_NOT_FOUND`다
- And 두 사용자가 같은 skill로 부르면 같은 본문을 받는다(콘텐츠다 — `09` §9.2 SHARED_CONTENT)
- Given 러버덕 결과 화면(SCR-RUBBER-DUCK ④)에서 그 세션의 skill에 노트가 있다
- Then "그래서 답이 뭔가요"가 보이고 누르면 SCR-LESSON으로 간다 — **러버덕은 답을 말하지 않으므로 답은 여기에만 있다**
- And 오늘 과제 카드(SCR-TODAY)에서 과제의 skill에 노트가 있으면 "먼저 개념 익히기"가 보인다. 마친 과제에는 보이지 않는다
- And 노트가 없는 skill이면 두 자리 모두 **버튼 자체가 없다**

**S9. 목록에서 어디까지 했는지 보고 이어서 한다 (§21.9, UI)**
- Given 노트 3개가 있고 그중 하나는 단위 하나를 마친 상태다
- When `GET /lessons`
- Then 200이고 `lessons`가 3개이며 각 줄에 `title`·`oneLine`·`unitCount`·`solvedUnitCount`·`minutes`·`status`가 있다
- And 마친 노트가 **맨 앞**이고 `status`가 `IN_PROGRESS`, 나머지는 `NOT_STARTED`로 `lessonKey` 순이다
- And 응답 JSON 어디에도 본문(`explain`·`units`)과 답이 **없다**
- And 다른 사용자가 마친 단위는 내 목록의 진행에 섞이지 않는다
- Given SCR-LESSON-LIST를 연다
- Then 서버가 준 순서 그대로 그리고, 상태가 바뀌는 자리에만 "이어서 하기"·"아직 안 연 것"·"한 바퀴 돈 것" 제목이 붙는다
- And 줄을 누르면 그 노트의 SCR-LESSON으로 간다. 노트가 하나도 없으면 빈 상태 문구만 보인다

