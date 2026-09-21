# 04. Domain Model & Database

> Status: Accepted (v2) · Last updated: 2026-09-20 · Related: ADR-001, ADR-014, ADR-038, ADR-039, ADR-040, ADR-041, ADR-042, `database/schema.sql`, `06-learning-engine-rules.md`
>
> 컬럼 수준의 기준은 `database/schema.sql`이다. 이 문서는 **aggregate와 소유 관계, enum 레지스트리, 상태 전이, JSON 컬럼 스키마, 이벤트 payload, 불변식, migration 계획**을 정의한다.

---

## 1. 공통 규칙

| 항목 | 규칙 |
|---|---|
| DB | PostgreSQL 16 (개발·운영 모두 자체 서버의 공용 인스턴스, Testcontainers `postgres:16`), 스키마 `devpilot` 단일 사용. PG 17 전용 문법을 쓰지 않는다 |
| 테이블/컬럼 이름 | snake_case, 단수형 (`review_item`) |
| Entity 이름 | PascalCase 단수형 (`ReviewItem`), 모듈의 `domain` 패키지 |
| PK | `UUID`. 애플리케이션이 생성한다(`UUID.randomUUID()`). `@Id`만 쓰고 DB 생성 전략은 쓰지 않는다. `Persistable#isNew`를 구현하거나 `@Version`으로 new 여부를 판단해 불필요한 SELECT를 피한다 |
| 시각 | `Instant` ↔ `timestamptz`(UTC). 사용자 날짜는 `LocalDate` ↔ `date` (plan-day 기준, `06` §2) |
| 동시성 | 수정 가능한 aggregate root는 `@Version Long version` |
| 계획 버전 번호 | `learning_plan.plan_version` (JPA `@Version`과 이름을 구분) |
| enum 매핑 | `@Enumerated(EnumType.STRING)`, DB는 varchar + CHECK. **CHECK를 두지 않는 예외 둘**: (1) `failure_code`(`AsyncFailureCode`)는 Sprint마다 값이 늘어 migration이 계속 따라와야 하므로 애플리케이션에서만 검증한다 (2) enum **배열** 컬럼(`learning_task.reason_codes`, `review_answer.adjusted_by`, `coach_review.self_review_axes`)도 같은 이유로 애플리케이션 검증이다. 둘 다 §3 레지스트리 값만 쓰는 것은 동일하고, 위반은 테스트로 잡는다 |
| 비율 | 정수 basis point(`*_bp`, 0~10000). 부동소수점 비교를 쓰지 않는다 |
| JSON 컬럼 | `jsonb` ↔ Java record (Hibernate JSON 매핑). 각 컬럼의 스키마는 §5 |
| 배열 컬럼 | `varchar[]`/`uuid[]` ↔ `List<String>`/`List<UUID>` (Hibernate array 매핑) |
| 삭제 | 사용자 데이터는 `app_user` 삭제 시 cascade. catalog(`skill`)는 비활성화만 |
| 감사 시각 | `created_at`, `updated_at`, `status_updated_at`은 애플리케이션이 설정 (JPA Auditing / 명시적 set) |

---

## 2. Aggregate와 소유 관계

| 모듈 | Aggregate root | 포함 엔티티 / 테이블 | 소유자 경로 | 수정 방식 |
|---|---|---|---|---|
| user | `AppUser` | `app_user` | 자기 자신 | 수정 |
| goal | `LearningGoal` | `learning_goal`, `learning_goal_focus_skill` | `user_id` (1:1) | 수정 |
| skill | `Skill` (catalog) | `skill`, `skill_prerequisite`, `role_skill_target` | 전역 | seed만 |
| skill | `UserSkillState` | `user_skill_state` | `user_id` | 규칙 엔진만 수정 |
| skill | `SkillStateChange` | `skill_state_change` | `user_id` | append-only |
| learning | `LearningSession` | `learning_session` | `user_id` | 수정 |
| learning | `LearningEvent` | `learning_event` | `user_id` | append-only (무효화만) |
| learning | `HintDisclosure` | `hint_disclosure` | `user_id` | append-only |
| rubberduck | `RubberDuckSession` | `rubber_duck_session`, `rubber_duck_turn` | `user_id` | 세션은 수정, 턴은 append-only |
| plan | `LearningPlan` | `learning_plan`, `plan_milestone`, `milestone_skill`, `plan_skill_target` | `user_id` | 구조 변경 = 새 version (`06` §11) |
| plan | `PlanProgressSnapshot` | `plan_progress_snapshot` | `user_id` | job이 upsert |
| project | `SideProject` | `side_project` | `user_id` | 수정 |
| project | `SideProjectNote` | `side_project_note` | `user_id` (프로젝트 경로 `side_project_id`) | 수정 |
| today | `DailyPlan` | `daily_plan`, `learning_task` | `user_id` | 수정 |
| today | `UserDailyTip` | `user_daily_tip` | `user_id` | 표시할 때 INSERT, 그 뒤 `feedback`만 수정 (§4.11) |
| training | `Challenge` | `challenge`, `challenge_skill` | `owner_user_id` (null = seed 공용) | AI·수동 생성분은 생성 후 불변(status만 변경). seed는 §9의 갱신 규칙 |
| training | `ChallengeAttempt` | `challenge_attempt`, `challenge_submission` | `user_id` | 수정 |
| review | `ReviewItem` | `review_item` | `user_id` | 수정 |
| review | `ReviewAnswer` | `review_answer` | `user_id` | append-only |
| coach | `CoachReview` | `coach_review`, `coach_finding` | `user_id` | 수정 |
| coach | `ThinkingPatternObservation` | `thinking_pattern_observation` | `user_id` | append-only |
| evidence | `EvidenceCandidate` | `evidence_candidate` | `user_id` | 수정 |
| evidence | `WeeklyReview` | `weekly_review` | `user_id` | job 생성, reflection만 수정 |
| radar | `RequirementDoc` | `requirement_doc`, `requirement_item` | `user_id` | 분석 후 불변, 삭제 가능 |
| integration.ai | `AiCallLog` | `ai_call_log` | `user_id` (삭제 시 null) | append-only |
| common | `IdempotencyRecord` | `idempotency_record` | `user_id` | 만료 삭제 |

규칙:
- 다른 aggregate는 **ID로만 참조**한다. JPA 연관관계(`@ManyToOne`)는 같은 aggregate 안에서만 쓴다. 예: `PlanMilestone → LearningPlan`은 허용, `LearningTask → Challenge`는 `UUID challengeId`
- 컬렉션 연관관계는 기본 LAZY. 목록 조회는 필요한 컬럼만 담는 projection(record)을 쓴다(N+1 방지)

---

## 3. Enum 레지스트리

**이 표가 유일한 기준이다.** Java enum, DB CHECK, Dart enum, OpenAPI 스키마가 모두 이 이름을 쓴다. 순서가 의미를 갖는 enum은 ordinal을 명시한다(Java에서는 선언 순서로 보장하고, 필요하면 `rank()` 메서드를 둔다).

| Enum (Java 위치) | 값 | 사용처 |
|---|---|---|
| `TargetRole` (goal.domain) | `JAVA_BACKEND`("Java 백엔드"), `JAVA_BACKEND_STARTER`("Java 백엔드 입문"), `INTEGRATION_ENGINEER`("연동·구축 엔지니어") | learning_goal, role_skill_target — 학습 트랙. 트랙마다 role target 파일 1개·계획 템플릿 1개가 있고, 트랙별 기본값은 `devpilot.tracks.<트랙>`이다(`03` §9, `06` §5.3) |
| `UserRole` (common.security) | `USER`, `ADMIN` | app_user |
| `UserStatus` (user.domain) | `ACTIVE`, `DELETION_REQUESTED` | app_user |
| `AiProvider` 값 (설정 문자열, Java enum 아님) | `deepseek`, `anthropic`(대안), `fake`, `disabled` — **소문자**. `devpilot.ai.provider` 설정값이 그대로 `ai_call_log.provider`에 들어가므로 이 표의 다른 enum과 달리 소문자다 | ai_call_log |
| `AiStatus` (integration.ai.api) | `ENABLED`, `BUDGET_WARNING`, `BALANCE_EXHAUSTED`(공급자 선불 잔액 소진·402, `17` §8.7), `DISABLED` | `GET /me` 응답 (저장 안 함). `BALANCE_EXHAUSTED`는 클라이언트가 `DISABLED`와 같게 다루되 이유 문구만 다르다 |
| `EnergyLevel` (today.domain) | `LOW`, `NORMAL`, `HIGH` | daily_plan |
| `HintLevel` (learning.domain) | `SELF_EXPLAIN`(0), `QUESTION_ONLY`(1), `CONCEPT_HINT`(2), `DIRECTION`(3), `PSEUDOCODE`(4), `PARTIAL_CODE`(5), `FULL_EXAMPLE`(6) | attempt, finding, review_answer, hint_disclosure |
| `HintTargetType` (learning.domain) | `CHALLENGE_ATTEMPT`, `COACH_FINDING` | hint_disclosure |
| `HintContentOrigin` (learning.domain) | `SEED`, `PREGENERATED`, `AI_GENERATED` | hint_disclosure |
| `VerificationStatus` (coach.domain) | `VERIFIED`, `SUPPORTED`, `AI_JUDGMENT`, `UNCERTAIN` | coach_finding — 규칙 `06` §10 |
| `Confidence` (coach.domain) | `HIGH`, `MEDIUM`, `LOW` | coach_finding |
| `VerificationSourceType` (coach.domain) | `COMPILER`, `TEST_RESULT`, `STATIC_ANALYSIS`, `CURATED_SOURCE`, `OFFICIAL_DOC`, `SECURITY_GUIDE`, `AI_REASONING` | coach_finding.source_type |
| `FindingType` (coach.domain) | `BUG`, `RISK`, `LEARNING_POINT` | coach_finding |
| `FindingStatus` (coach.domain) | `OPEN`, `USER_RESPONDED`, `RESOLVED`, `DISMISSED` | coach_finding |
| `DiscoveredBy` (coach.domain) | `MENTIONED_UNPROMPTED`, `FOUND_AFTER_HINT`, `MISSED` | coach_finding |
| `ThinkingAxis` (learning.domain) | `CORRECTNESS`, `NULL_BOUNDARY`, `RESOURCE_LIFECYCLE`, `EXCEPTION_STRATEGY`, `SECURITY`, `PERFORMANCE`, `CONCURRENCY`, `OBSERVABILITY`, `MAINTAINABILITY`, `TRANSACTION_DATA_CONSISTENCY` | finding.category, observation.axis, self_review_axes |
| `ThinkingObservation` (coach.domain) | `MENTIONED_UNPROMPTED`, `FOUND_AFTER_HINT`, `MISSED`, `INCORRECT_CLAIM` | thinking_pattern_observation |
| `ObservationSourceType` (coach.domain) | `COACH_REVIEW`, `CHALLENGE_ATTEMPT` | thinking_pattern_observation |
| `CoachContentType` (coach.domain) | `CODE`, `DIFF`, `LOG` | coach_review |
| `CoachProjectType` (coach.domain) | `SPRING_BOOT`, `JAVA_LIBRARY`, `ANDROID`, `FLUTTER`, `OTHER` | coach_review.context_json.projectType |
| `CodeLanguage` (learning.domain) | `JAVA`, `KOTLIN`, `SQL`, `DART`, `YAML`, `PROPERTIES`, `XML`, `SHELL`, `OTHER` | coach_review, challenge_submission |
| `ReviewRating` (review.domain) | `AGAIN`(0), `HARD`(1), `GOOD`(2), `EASY`(3) | review_answer, review_item.last_result |
| `RatingAdjustment` (review.domain) | `EVALUATED_INCORRECT`, `EVALUATED_PARTIAL`, `HINT_CAP_AGAIN`, `HINT_CAP_HARD`, `HINT_CAP_GOOD` | review_answer.adjusted_by |
| `ReviewType` (review.domain) | `RECALL`, `BUG_SPOT`, `EXPLAIN`, `CHOICE` | review_item |
| `ReviewItemSourceType` (review.domain) | `SEED_CARD`, `MANUAL`, `CHALLENGE_ATTEMPT`, `COACH_FINDING`, `EVIDENCE`, `RUBBER_DUCK`, `REDO_TASK`, `TERM`, `TIP` | review_item.source_type — `RUBBER_DUCK`은 러버덕 정리의 gap(`05` §9.8), `REDO_TASK`는 AI 없이 다시 만들지 못한 재현 과제(`06` §5.10 RE-7), `TERM`은 용어 사전에서 만든 복습 카드(`05` §20.7), `TIP`은 오늘의 팁에서 "새로 알았어요"를 고른 카드(`06` §5.12 TIP-5) |
| `ReviewItemStatus` (review.domain) | `ACTIVE`, `SUSPENDED`, `ARCHIVED` | review_item — due 여부는 `due_at < planDayStart(today + 1)`로 계산 (`06` §6.5) |
| `VariantStatus` (review.domain) | `NONE`, `PENDING`, `RUNNING`, `READY`, `FAILED` | review_item.variant_status |
| `EvaluatedOutcome` (learning.domain) | `CORRECT`, `PARTIAL`, `INCORRECT`, `NOT_EVALUATED` | submission, attempt, review_answer |
| `AttemptOutcome` (training.domain) | `SOLVED_INDEPENDENTLY`, `SOLVED_WITH_HINTS`, `PARTIAL`, `FAILED`, `ABANDONED` | challenge_attempt |
| `AttemptStatus` (training.domain) | `STARTED`, `SUBMITTED`, `EVALUATED`, `ABANDONED` | challenge_attempt |
| `ChallengeStatus` (training.domain) | `DRAFT`, `VALIDATED`, `REJECTED`, `RETIRED` | challenge |
| `ChallengePurpose` (training.domain) | `PRACTICE`, `DIAGNOSTIC` | challenge |
| `ContentOrigin` (common) | `SEED`, `MANUAL`, `AI_GENERATED` | challenge, review_item |
| `RubricAxis` (training.domain) | `IMPLEMENTATION`, `EXPLANATION`, `DEBUGGING` | rubric_json 항목 |
| `AsyncJobStatus` (common.async) | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` | coach_review, challenge.generation_status, submission.evaluation_status, requirement_doc |
| `EvidenceGenerationStatus` (evidence.domain) | `NONE`, `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` | evidence_candidate.generation_status |
| `AsyncFailureCode` (common.async) | `AI_UNAVAILABLE`, `AI_TIMEOUT`, `AI_REFUSED`, `AI_OUTPUT_INVALID`, `AI_BUDGET_EXCEEDED`, `AI_RATE_LIMITED`, `CONFIDENTIAL_SUSPECTED`, `INTERRUPTED`, `INTERNAL_ERROR` | `failure_code` 컬럼 전체 |
| `RiskLevel` (plan.domain) | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` | daily_plan, snapshot |
| `Priority` (plan.domain) | `MUST`, `SHOULD`, `LATER` | milestone, role/plan skill target |
| `PlanStatus` (plan.domain) | `ACTIVE`, `SUPERSEDED`, `ARCHIVED` | learning_plan |
| `MilestoneStatus` (plan.domain) | `PLANNED`, `IN_PROGRESS`, `DONE`, `DEFERRED`, `DROPPED` | plan_milestone |
| `TargetAdjustment` (plan.domain) | `ROLE_DEFAULT`, `DEFERRED`, `TARGET_REDUCED`, `USER_EDITED` | plan_skill_target.adjustment. 확장 제안을 받아들인 목표 상향도 `USER_EDITED`다(`06` §11.2) |
| `ExpansionKind` (plan.domain) | `RESTORE_DEFERRED`, `RAISE_TARGET` | replan preview 응답 `expansionSuggestions[].kind` (저장 안 함, `06` §4.4 6단계) |
| `TaskType` (today.domain) | `RECALL`, `REVIEW`, `CHALLENGE`, `PROJECT_TASK`, `COACH_REVIEW`, `READING`, `READ_CODE`, `EXPLAIN`, `REDO` | learning_task — `READ_CODE`는 큐레이션 저장소 읽기(`06` §5, RC-1~4), `REDO`는 며칠 뒤 **AI 없이 혼자 다시 만드는 재현 과제**(`06` §5.10, RE-1~RE-8) |
| `TaskStatus` (today.domain) | `PLANNED`, `IN_PROGRESS`, `COMPLETED`, `SKIPPED`, `DEFERRED` | learning_task |
| `ReadingFeedback` (today.domain) | `HELPFUL`, `TOO_HARD`, `BORING` | learning_task.reading_feedback — `READ_CODE` 완료 때 사용자가 고르는 읽기 평가(선택, `05` §8.4). 규칙 입력이 아니다(`06` §5.3). 소스 점검(`19` §8.5)의 입력 |
| `TipLevel` (learning.domain) | `BASIC`("기본기"), `PRACTICAL`("실무") | 저장하지 않는다. 오늘의 팁 콘텐츠(`content/tips/*.yaml`의 `level`)와 용어 사전(`content/terms/*.yaml`의 `level`)이 같은 값을 쓰고, `GET /tips`·`GET /terms` 응답과 필터에 나온다(`05` §20) |
| `TipSeries` (learning.domain) | `ERROR_READING`, `RESOURCE`, `LOGGING`, `HTTP_INTEGRATION`, `DATABASE`, `OPERATIONS`, `CONVENTION` | 저장하지 않는다. 팁 묶음(`content/tips/*.yaml`의 `series`). `GET /tips`의 필터·응답(`05` §20.4) |
| `TipFeedback` (learning.domain) | `KNEW_IT`("알고 있었어요"), `LEARNED`("새로 알았어요"), `WILL_TRY`("직접 해 볼래요") | user_daily_tip.feedback — 팁을 읽은 뒤 고른 값(`05` §20.3) |
| `ReasonCode` (today.domain) | §5.1 | learning_task.reason_codes |
| `SessionStatus` (learning.domain) | `IN_PROGRESS`, `COMPLETED`, `ABANDONED` | learning_session |
| `RubberDuckTargetType` (rubberduck.domain) | `CODE_READING`, `CHALLENGE`, `REVIEW_ITEM`, `CONCEPT`, `PROJECT_WORK` | rubber_duck_session.target_type — `CONCEPT`이면 `target_id` 대신 `concept_key`, `PROJECT_WORK`이면 `target_id`가 `side_project.id` |
| `RubberDuckStatus` (rubberduck.domain) | `IN_PROGRESS`, `COMPLETED`, `ABANDONED` | rubber_duck_session.status |
| `SideProjectStatus` (project.domain) | `ACTIVE`, `PAUSED`, `DONE` | side_project.status |
| `SideProjectKind` (project.domain) | `SIDE`("사이드 프로젝트", 기본값), `PAST_WORK`("지난 경험 기록") | side_project.kind — 상태가 아니라 분류다(§4.9, I-23). `PAST_WORK` 프로젝트는 planner의 `PROJECT_TASK` 대상에서 빠진다(`06` SP-3) |
| `SideProjectNoteType` (project.domain) | `DECISION`("결정 기록"), `INCIDENT`("장애 기록") | side_project_note.note_type — 값마다 채우는 본문 컬럼이 다르다(I-22, `05` §19.8) |
| `SkillAxis` (skill.domain) | `KNOWLEDGE`, `IMPLEMENTATION`, `EXPLANATION`, `DEBUGGING` | skill_state_change |
| `SkillLevel` (skill.domain) | `UNKNOWN`(0), `SEEN`(1), `GUIDED`(2), `INDEPENDENT_BASIC`(3), `PRACTICAL`(4), `TRANSFERABLE`(5) | 표시용 (DB는 smallint) |
| `SkillCategory` (skill.domain) | `JAVA`, `SPRING`, `DATABASE`, `WEB_HTTP`, `NETWORK`, `CS`, `ALGORITHM`, `TESTING`, `DEVOPS`, `SECURITY`, `INTEGRATION`("연동"), `PRACTICAL_ENGINEERING`, `SYSTEM_DESIGN`, `EXPLANATION` (14개) | skill |
| `LearningStage` (skill.domain) | `BUILD`(0), `READ_CONCEPT`(1), `READ_CODE`(2), `EXPLAIN`(3), `REVIEW`(4), `REDO`(5) — **만들기가 먼저다.** 이 선언 순서가 화면의 6칸 순서다 | 저장하지 않는다. `GET /skills/{skillId}`의 `learningStages[]`를 만들 때 기존 기록(과제 완료, 러버덕 세션, 복습 답변, 재현 과제)에서 파생 계산한다(ADR-042, `06` §5.11) |
| `LearningEventType` (learning.domain) | §6 | learning_event |
| `EventSourceType` (learning.domain) | `LEARNING_SESSION`, `CHALLENGE_ATTEMPT`, `CHALLENGE_SUBMISSION`, `REVIEW_ITEM`, `COACH_REVIEW`, `COACH_FINDING`, `EVIDENCE`, `LEARNING_PLAN`, `RUBBER_DUCK_SESSION`, `LEARNING_TASK` | learning_event.source_type — `LEARNING_TASK`는 재현 과제 완료(`REDO_COMPLETED`, §6) |
| `EvidenceStatus` (evidence.domain) | `CANDIDATE`, `ACCEPTED`, `REJECTED` | evidence_candidate |
| `RequirementType` (radar.domain) | `REQUIRED`, `PREFERRED` | requirement_item |
| `RequirementFitCategory` (radar.domain) | `READY`, `STRETCH`, `LATER` | requirement_item |
| `AiOperation` (integration.ai.api) | `COACH_REVIEW`, `COACH_RESPONSE_FEEDBACK`, `CHALLENGE_GENERATE`, `CHALLENGE_EVALUATE`, `HINT_GENERATE`, `REVIEW_VARIANT`, `REVIEW_EVALUATE`, `EVIDENCE_DRAFT`, `REQUIREMENT_EXTRACT`, `RUBBER_DUCK`, `RUBBER_DUCK_SUMMARY` | ai_call_log — 러버덕은 턴마다 `RUBBER_DUCK`(prompt `rubber.duck`), 종료 정리 1회는 `RUBBER_DUCK_SUMMARY`(prompt `rubber.duck.summary`). 설정이 달라 나눈다 (`03` §9, `17`) |
| `AiCallStatus` (integration.ai.api) | `SUCCESS`, `INVALID_OUTPUT`, `REFUSED`, `TIMEOUT`, `RATE_LIMITED`, `PROVIDER_ERROR`, `BUDGET_BLOCKED` | ai_call_log |

---

## 4. 상태 전이

전이표에 없는 전이를 요청하면 `409 INVALID_STATE_TRANSITION`을 반환한다. 전이 로직은 entity 메서드(예: `task.start()`)에 두고, 허용되지 않으면 도메인 예외를 던진다.

### 4.1 LearningTask (`TaskStatus`)

| From → To | 트리거 | 부수효과 |
|---|---|---|
| `PLANNED → IN_PROGRESS` | `PATCH /today/tasks/{id}` status=IN_PROGRESS, 또는 `POST /learning-sessions`가 이 task를 참조할 때 자동 전이(`05` §9.1) | 재생성이 PLANNED task를 삭제해도 진행 중 세션의 task가 사라지지 않게 |
| `PLANNED → SKIPPED` | PATCH | — |
| `IN_PROGRESS → COMPLETED` | PATCH (`READ_CODE`는 RC-1 조건 + 선택 `readingFeedback`, `REDO`는 필수 `redoWithoutAi`, `EXPLAIN`·`READ_CODE`는 선택 `explainedToPerson`·`explainedNote`, `05` §8.4) | `completed_at`, `READ_CODE`이고 평가가 있으면 `reading_feedback`, `EXPLAIN`·`READ_CODE`이고 값이 있으면 `explained_to_person`·`explained_note`(마스킹본, I-24), `REDO`면 `redo_without_ai` + `REDO_COMPLETED` 이벤트(§6). `redo_without_ai = false`면 복습 카드 upsert (`06` §5.10 RE-7) |
| `IN_PROGRESS → DEFERRED` | PATCH, 또는 `POST /today/generate force=true` | 다음날 planner 이어하기 보너스 대상 |
| `PLANNED → (삭제)` | `POST /today/generate` 재생성 | 같은 daily_plan의 PLANNED task 모두 삭제 후 flush → 새 task INSERT |
| `SKIPPED → PLANNED` | PATCH (되돌리기) | 같은 날 활성 main이 없을 때만 |

### 4.2 ChallengeAttempt (`AttemptStatus`) · Submission

| From → To | 트리거 | 조건 |
|---|---|---|
| (new) → `STARTED` | `POST /challenges/{id}/attempts` | challenge `VALIDATED` |
| `STARTED → SUBMITTED` | `POST .../submissions` | self-explanation 기록 존재(제출 또는 skip) |
| `SUBMITTED → EVALUATED` | 평가 완료 (submission `COMPLETED`) | outcome 계산 (`06` §8) |
| `EVALUATED → SUBMITTED` | 재제출 | `submission_count < 5` |
| `STARTED/SUBMITTED/EVALUATED → ABANDONED` | `POST .../abandon` | outcome=ABANDONED (EVALUATED에서 포기하면 마지막 outcome 유지, status만 ABANDONED) |

Submission `evaluation_status`: `PENDING → RUNNING → COMPLETED | FAILED`. FAILED면 attempt는 `SUBMITTED`에 머물고, 사용자는 같은 제출을 재평가 요청(`POST .../submissions/{no}/retry`)할 수 있다.

### 4.3 CoachReview / CoachFinding

CoachReview `status`: `PENDING → RUNNING → COMPLETED | FAILED`. `COMPLETED` 후 `POST /complete`로 `closed_at`이 설정되면 finding을 더 수정할 수 없다.

| Finding From → To | 트리거 |
|---|---|
| `OPEN → USER_RESPONDED` | 응답 제출 |
| `USER_RESPONDED → USER_RESPONDED` | 추가 응답 (최신 응답으로 덮어쓴다. 별도 학습 이벤트는 없다 — §6) |
| `OPEN/USER_RESPONDED → RESOLVED` | 사용자가 수정 완료 표시 |
| `OPEN/USER_RESPONDED → DISMISSED` | 사용자가 "해당 없음" 표시 (오탐 신호) |
| 모든 상태 → (고정) | review `closed_at` 설정 시 `discovered_by` 확정 (`06` §9.3) |

### 4.4 LearningPlan (`PlanStatus`)

| From → To | 트리거 |
|---|---|
| (new) → `ACTIVE` | 온보딩, `POST /plans` (활성 plan 없을 때) |
| `ACTIVE → SUPERSEDED` | `POST /plans/{id}/replan` — **새 plan INSERT 전에 flush** |
| `ACTIVE → ARCHIVED` | 학습 목표 삭제·재설정 시 (Later) |

### 4.5 ReviewItem (`ReviewItemStatus`, `VariantStatus`)

| From → To | 트리거 |
|---|---|
| `ACTIVE → SUSPENDED` | 사용자, 또는 leech 규칙 (`06` §6.4) |
| `SUSPENDED → ACTIVE` | 사용자. `due_at = 다음 plan-day 시작`, 연속 실패 0 |
| `ACTIVE/SUSPENDED → ARCHIVED` | 사용자 |
| variant `NONE → PENDING → RUNNING → READY \| FAILED` | 연속 실패 ≥ 2일 때 답변 처리 후 비동기 생성 |
| variant `READY → NONE` | variant로 출제·답변 완료 시 |
| variant `FAILED → NONE` | 다음 답변 처리 시 (원문항 출제) |

### 4.6 PlanMilestone (`MilestoneStatus`)

진행 기록 성격이므로 **모든 상태 간 전이를 허용**한다(같은 상태로의 변경은 no-op). 부수효과는 없다. milestone 상태는 planner·budget 계산에 쓰지 않는다(계산은 날짜와 `plan_skill_target` 기준).

### 4.7 EvidenceCandidate · AppUser

- Evidence `status`: `CANDIDATE → ACCEPTED | REJECTED`, `REJECTED → CANDIDATE`(복원). `ACCEPTED`는 되돌리지 않고, 수정만 허용한다.
- Evidence `generation_status`: `NONE → PENDING → RUNNING → COMPLETED | FAILED`
- AppUser `status`: `ACTIVE → DELETION_REQUESTED` → (AccountDeletionJob이 row 삭제)

### 4.8 RubberDuckSession (`RubberDuckStatus`)

| From → To | 트리거 | 부수효과 |
|---|---|---|
| (new) → `IN_PROGRESS` | `POST /rubber-duck` | `started_at`. 사용자당 `IN_PROGRESS`는 1개다(I-18) — 이전 `IN_PROGRESS` 세션을 `ABANDONED`로 바꾸고 flush한 뒤 INSERT한다 |
| `IN_PROGRESS → COMPLETED` | `POST /rubber-duck/{id}/complete` — 턴 상한 도달 또는 사용자 종료 (`06` RD-4) | `RUBBER_DUCK_SUMMARY` 호출 1회 → `summary_json`, `completed_at`. `gaps[]` → 복습 카드, `skill_id`가 있으면 `RUBBER_DUCK_COMPLETED` 이벤트(정리 AI가 실패하면 없음). 레벨 증거가 되는 것은 `gapCount = 0`·`turns ≥ 3`인 것뿐 (`06` RD-5·RD-7, §7.2) |
| `IN_PROGRESS → ABANDONED` | `POST /rubber-duck/{id}/abandon`, 새 세션 시작(I-18), `StaleRubberDuckJob`(24시간), 턴 0개로 `complete` | 정리·이벤트·복습 카드 없음 |

- `COMPLETED`·`ABANDONED`에서 나가는 전이는 없다. 정리를 다시 만들지 않는다(RD-4).
- 턴 추가(`POST /rubber-duck/{id}/turns`)는 `IN_PROGRESS`에서만 허용한다. 다른 상태면 `409 INVALID_STATE_TRANSITION`.
- 방치 정리: `StaleRubberDuckJob`(`03` §6)이 `started_at`에서 `devpilot.rubberduck.stale-after`(24h)가 지난 `IN_PROGRESS` 세션을 `ABANDONED`로 바꾼다 (인덱스 §11).
- `rubber_duck_turn.learner_stuck`은 그 턴의 설명이 RD-3 "모르겠다"인지 서버 규칙으로 판정한 값이다(`06` §9.5). AI 출력이 아니다.

### 4.9 SideProject (`SideProjectStatus`)

| From → To | 트리거 |
|---|---|
| (new) → `ACTIVE` | 온보딩 마지막 단계 또는 `POST /side-projects` (`06` SP-1) |
| `ACTIVE → PAUSED` | `PATCH /side-projects/{id}` |
| `PAUSED → ACTIVE` | `PATCH /side-projects/{id}` |
| `ACTIVE/PAUSED → DONE` | `PATCH /side-projects/{id}` |
| `DONE → ACTIVE/PAUSED` | `PATCH /side-projects/{id}` (끝낸 프로젝트를 다시 이어 간다) |

세 상태 사이의 모든 전이를 허용하고 같은 상태로의 변경은 no-op이다(`05` §19.5). `ACTIVE`는 사용자당 여러 개일 수 있고, planner는 가장 최근 `updated_at`인 `ACTIVE` **`kind = SIDE`** 하나만 쓴다(`06` SP-3).

`kind`(`SideProjectKind`, §3)는 상태가 아니라 분류다. 전이표가 없고 `POST /side-projects`에서 정하며 `PATCH /side-projects/{id}`로 바꿀 수 있다(`05` §19.2·§19.5). `PAST_WORK`로 바꿔도 이미 만들어진 `PROJECT_TASK`는 남고, 다음 생성부터 planner가 그 프로젝트를 고르지 않는다(I-23).

### 4.10 SideProjectNote

상태 컬럼이 없다. 생성·수정·삭제만 있고 전이표가 없다(`05` §19.8~§19.12). `note_type`은 **생성 시 고정**이고 `PATCH`로 바꿀 수 없다 — 유형이 바뀌면 본문 컬럼 조합도 바뀌어 I-22를 지킬 수 없다. 유형을 잘못 골랐으면 지우고 다시 만든다. 프로젝트가 삭제되면 그 프로젝트의 노트도 함께 삭제된다(`on delete cascade`, §8).

### 4.11 UserDailyTip

상태 컬럼이 없다. `GET /tips/today`가 그날 보여 준 팁을 한 행으로 INSERT하고(`shown_on` = 그 plan-day, `feedback = null`), `POST /tips/{tipKey}/feedback`이 `feedback`을 **한 번** 기록한다(`05` §20.2·§20.3). 이미 값이 있으면 덮어쓰지 않는다 — 읽은 직후의 판단을 그대로 남긴다. 행은 사용자당 팁당 1개다(I-26). 삭제는 계정 삭제 cascade뿐이다(§8).

---

## 5. JSON 컬럼 스키마

모든 JSON 컬럼은 Java record로 매핑하고 저장 전에 Bean Validation을 거친다. 필드 이름은 lowerCamelCase다.

### 5.1 `learning_task.score_breakdown` / `reason_codes`

```json
{
  "plannerVersion": "RULE_V1",
  "factors": { "practicalImportance": 900000, "skillGap": 600000, "reviewUrgency": 0,
               "milestoneUrgency": 500000, "projectNeed": 0, "prerequisiteReadiness": 1000000 },
  "baseScore": 520000,
  "modifiers": [ { "code": "RISK_HIGH_MUST", "multiplierBp": 12000 } ],
  "finalScore": 624000,
  "rank": 1,
  "reasonParams": { "milestoneTitle": "Spring/JPA 핵심", "planningImplementation": 1, "targetImplementation": 4, "overdueDays": 2, "repoName": null, "redoDaysAfter": null }
}
```
- `reasonParams`는 reason 문구 템플릿(`06-learning-engine-rules.md` §5.8)의 변수 값이다. 응답을 만들 때 이 값으로 문구를 채운다. 값이 없는 변수는 null이다.
- 점수와 factor는 **micro 단위 정수**(1.0 = 1,000,000)다.
- `ReasonCode`: `MILESTONE_CORE`, `MILESTONE_NEXT`, `HIGH_PRACTICAL_IMPORTANCE`, `LARGE_SKILL_GAP`, `REVIEW_OVERDUE`, `RECENT_RECALL_FAILURE`, `PROJECT_FOCUS`, `READ_REAL_CODE`, `REDO_WITHOUT_AI`, `CONTINUE_YESTERDAY`, `DEADLINE_RISK_MUST`, `LOW_ENERGY_LIGHT_TASK`, `COMEBACK_EASY_START`
- `modifiers[].code`: `RISK_HIGH_MUST`, `RISK_HIGH_SHOULD`, `LOW_ENERGY_DEEP_TASK`, `HIGH_ENERGY_HARD_TASK`, `CONTINUATION`, `FATIGUE_TWO_DAYS`, `FATIGUE_ONE_DAY`, `MONOTONY_THREE_DAYS`, `MONOTONY_FIVE_DAYS`, `COMEBACK_HARD_TASK`, `REDO_DUE` (`06` §5.5)
- `rank`: 그날 후보 순위. main은 1, 추가 과제는 2·3·4다 (`06` §5.6)

### 5.2 `challenge.*_json`

```json
// expected_concepts_json
["exception translation", "cause 보존", "checked vs unchecked"]

// rubric_json — weight 합은 정확히 10000
[
  { "id": "R1", "criterion": "원인 예외(cause)를 보존한다", "weightBp": 4000, "axis": "IMPLEMENTATION" },
  { "id": "R2", "criterion": "복구 가능한 계층에서만 처리하는 이유를 설명한다", "weightBp": 4000, "axis": "EXPLANATION" },
  { "id": "R3", "criterion": "null 반환 대신 의미 있는 결과/예외를 사용한다", "weightBp": 2000, "axis": "IMPLEMENTATION" }
]

// constraints_json
["외부 라이브러리 없이 JDK만 사용", "메서드 시그니처는 유지"]

// common_mistakes_json
["catch 블록에서 원인 예외를 버림", "RuntimeException으로 감싸기만 하고 메시지가 없음"]

// transfer_targets_json — skill code 목록
["SPRING.EXCEPTION_HANDLING"]

// hints_json — 1~3단계만 (4단계 이상은 요청 시 AI 생성)
{ "QUESTION_ONLY": "...", "CONCEPT_HINT": "...", "DIRECTION": "..." }
```

### 5.3 `challenge_submission.evaluation_json`

```json
{
  "rubric": [ { "id": "R1", "met": true, "evidenceQuote": "throw new ConfigLoadException(path, e)" },
              { "id": "R2", "met": false, "evidenceQuote": null } ],
  "misconceptions": ["checked 예외는 항상 잡아야 한다고 설명함"],
  "followUpQuestion": "이 예외를 호출자가 복구할 수 없다면 어떤 타입이 더 적절할까요?"
}
```
coverage 계산은 서버가 한다(`06` §8.1).

### 5.4 `review_item.rubric_json` / `variant_rubric_json`

```json
[ { "id": "R1", "criterion": "프록시 기반 AOP 언급" },
  { "id": "R2", "criterion": "내부 호출은 프록시를 우회한다는 점" } ]
```
복습 rubric은 가중치 없이 균등 배분한다(항목 수로 나눈 bp, 나머지는 첫 항목에 더함).

### 5.5 `coach_review.context_json`

```json
{ "projectType": "SPRING_BOOT", "topic": "HTTP_CLIENT", "skillCodes": ["JAVA.EXCEPTION"], "fileName": "ConfigLoader.java" }
```
- `projectType`: `SPRING_BOOT`, `JAVA_LIBRARY`, `ANDROID`, `FLUTTER`, `OTHER`
- `fileName`: 표시용. 경로 구분자(`/`, `\`)는 제거하고 저장한다.

### 5.6 `ai_call_log.guard_actions`

```json
[ { "guard": "VERIFICATION", "action": "DOWNGRADED_VERIFIED", "detail": "finding[2] VERIFIED→AI_JUDGMENT" },
  { "guard": "CODE_LEAK", "action": "RETRIED", "detail": "finding[0].summary code block" } ]
```

### 5.7 `weekly_review.metrics_json`

```json
{
  "weekStartDate": "2026-10-05",
  "completedSessions": 5, "studyMinutes": 310,
  "independentSolveRateBp": 5000, "averageHintLevelMilli": 1800, "recallSuccessRateBp": 7200,
  "selfFoundRiskCount": 2, "acceptedEvidenceCount": 1, "completedRubberDuckSessions": 4,
  "projectNoteCount": 3, "independentRedoCount": 1,
  "riskLevel": "MEDIUM", "ratioBp": 9400,
  "weakThinkingAxes": ["RESOURCE_LIFECYCLE", "CONCURRENCY"]
}
```

### 5.8 `evidence_candidate.ai_draft_json` / `explanation_topics`

```json
// ai_draft_json — AI 원본. 사용자가 편집해도 바뀌지 않는다
{ "title": "...", "problem": "...", "analysis": "...", "action": "...", "result": "...",
  "explanationTopics": ["try-with-resources와 소유권", "예외 변환 기준"], "promptVersion": "v1" }

// explanation_topics — 사용자 확정본
["try-with-resources와 소유권"]
```

### 5.9 `rubber_duck_session.summary_json`

```json
{
  "gaps": [
    { "conceptKey": "SPRING.TRANSACTION.PROPAGATION",
      "whatWasMissed": "전파 속성이 다른 두 메서드가 같은 트랜잭션을 쓰는지 설명하지 못함",
      "whyItMatters": "주문 생성과 재고 차감이 한 트랜잭션인지를 결정한다",
      "reviewQuestion": "주문 생성 메서드가 재고 차감 메서드를 부를 때 트랜잭션이 몇 개 생기는지 설명해 보세요.",
      "reviewItemId": "c92a…" }
  ],
  "confirmed": ["프록시 기반 AOP라서 내부 호출에는 전파가 적용되지 않는다는 점"],
  "overallNote": "계층의 목적은 잡았고, 트랜잭션 경계가 다음 차례다.",
  "rawGapCount": 1,
  "promptVersion": "rubber.duck.summary@v1"
}
```
- `RUBBER_DUCK_SUMMARY` 출력(`17` §4.11)을 가드 적용 후 그대로 저장하고, 서버가 두 값을 더한다: gap마다 `reviewItemId`(만든 카드 또는 due를 당긴 기존 카드, 카드를 만들 수 없었으면 null — `05` §9.8), 그리고 `rawGapCount`(가드가 gap을 제거하기 **전**의 수, RD-5 판정에 쓴다). 세션이 `COMPLETED`가 된 뒤에는 바뀌지 않는다.
- `gaps[].conceptKey`는 복습 카드의 `review_item.concept_key`가 된다(`06` §6).
- `rawGapCount = 0`이고 `turn_count >= 3`이면 EXPLANATION 증거로 인정한다(`06` RD-5).
- 정리 텍스트는 사용자 설명에서 나온 것이므로 저장 전 `SecretMasker`를 통과한 `rubber_duck_turn.user_text`만 입력으로 쓴다(RD-6).

---

## 6. Learning Event 카탈로그

- `learning_event`는 append-only다. 여러 skill이 관련된 사건은 **skill마다 1행**을 기록한다. `dedupe_key`에는 skill id를 포함한다.
- 모든 payload에는 공통 필드 `planDate`가 없다. 테이블 컬럼 `plan_date`를 쓴다.
- skill updater(`06` §7)는 **이 payload만으로 판단**할 수 있어야 한다. 규칙에 새 조건이 필요하면 payload와 이 표를 함께 확장한다(`payload_version` 증가).

| event_type | source_type / source_id | skill_id | payload (payload_version=1) | dedupe_key |
|---|---|---|---|---|
| `SESSION_STARTED` | LEARNING_SESSION | task의 skill (없으면 null) | `{ taskId? }` | `SESSION_STARTED:{sessionId}` |
| `SESSION_COMPLETED` | LEARNING_SESSION | 〃 | `{ taskId?, actualMinutes }` | `SESSION_COMPLETED:{sessionId}` |
| `SELF_EXPLANATION_SUBMITTED` | CHALLENGE_ATTEMPT | challenge skill별 | `{ attemptId, length }` | `SELF_EXPLANATION:{attemptId}:{skillId}` |
| `SELF_EXPLANATION_SKIPPED` | CHALLENGE_ATTEMPT / COACH_FINDING | challenge skill별 / finding skill | `{ attemptId? , findingId? }` | `SELF_EXPLANATION:{attemptId 또는 findingId}:{skillId}` |
| `HINT_DISCLOSED` | CHALLENGE_ATTEMPT / COACH_FINDING | 대상 skill | `{ targetType, targetId, hintLevel, previousMaxHintLevel, skippedLevels[] }` | `HINT:{targetId}:{hintLevel}:{skillId}` |
| `CHALLENGE_STARTED` | CHALLENGE_ATTEMPT | challenge skill별 | `{ attemptId, challengeId, difficulty, purpose }` | `CHALLENGE_STARTED:{attemptId}:{skillId}` |
| `CHALLENGE_SUBMITTED` | CHALLENGE_SUBMISSION | 〃 | `{ attemptId, challengeId, submissionNo }` | `CHALLENGE_SUBMITTED:{submissionId}:{skillId}` |
| `CHALLENGE_EVALUATED` | CHALLENGE_SUBMISSION | 〃 | `{ attemptId, challengeId, submissionNo, difficulty, purpose, isTransfer, evaluatedOutcome, outcome, rubricCoverageBp, explanationCoverageBp?, maxHintLevel, transferTargetSkillCodes[] }` | `CHALLENGE_EVALUATED:{submissionId}:{skillId}` |
| `REVIEW_ANSWERED` | REVIEW_ITEM | item skill | `{ reviewItemId, reviewAnswerId, conceptKey, reviewType, wasVariant, selfRating, evaluatedOutcome, rubricCoverageBp?, hintLevel, finalRating, intervalBefore, intervalAfter }` | `REVIEW_ANSWERED:{reviewAnswerId}` |
| `LEECH_DETECTED` | REVIEW_ITEM | item skill | `{ reviewItemId, consecutiveFailures }` | `LEECH:{reviewItemId}:{reviewAnswerId}` |
| `RUBBER_DUCK_COMPLETED` | RUBBER_DUCK_SESSION | session skill (`rubber_duck_session.skill_id`, null이면 기록 생략 — `06` RD-7) | `{ sessionId, turns, gapCount, targetType, hintDisclosed }` — `gapCount`는 가드 적용 **전** gap 수(`summary_json.rawGapCount`, RD-5), `hintDisclosed`는 세션 시작 이후 같은 대상에 `HINT_DISCLOSED`가 있었는지(`06` §7.2 독립 판정) | `RUBBER_DUCK:{sessionId}:{skillId}` |
| `COACH_REVIEW_COMPLETED` | COACH_REVIEW | 관련 skill별 (`context.skillCodes` + finding skill) | `{ coachReviewId, findingCount, selfReviewAxes[] }` | `COACH_COMPLETED:{coachReviewId}:{skillId}` |
| `COACH_FINDING_CLOSED` | COACH_FINDING | finding.skill_id (없으면 기록 생략) | `{ coachReviewId, findingId, findingType, axis, confidence, discoveredBy, maxHintLevel, finalStatus }` | `FINDING_CLOSED:{findingId}` |
| `DIAGNOSTIC_PASSED` / `DIAGNOSTIC_FAILED` | CHALLENGE_ATTEMPT | challenge skill별 | `{ attemptId, challengeId, claimedLevel, rubricCoverageBp }` | `DIAGNOSTIC:{attemptId}:{skillId}` |
| `REDO_COMPLETED` | LEARNING_TASK (`learning_task.id`) | task의 skill (null이면 기록 생략) | `{ taskId, sourceTaskId, sourceTaskType, withoutAi, difficulty }` — `sourceTaskType`은 `CHALLENGE` 또는 `PROJECT_TASK`, `difficulty`는 원본 과제의 difficulty(`06` §5.3), `withoutAi`는 완료 때 사용자가 답한 값 | `REDO:{taskId}:{skillId}` |
| `TIP_VIEWED` | (없음 — `source_type`·`source_id` 모두 null) | 팁 `skillCodes`의 첫 활성 skill (없으면 null) | `{ tipKey, series, level }` — 팁을 보여 준 시점에 1회(`05` §20.2). `feedback`은 담지 않는다(고르지 않을 수 있다 — 값은 `user_daily_tip`에 있다) | `TIP_VIEWED:{tipKey}` |
| `TERM_CARD_CREATED` | REVIEW_ITEM (정방향 카드 `review_item.id`) | 용어 `skillCodes`의 첫 활성 skill | `{ termKey, conceptKey, cardCount }` — `conceptKey`는 정방향 카드의 것(`TERM:{termKey}`), `cardCount`는 이번에 만든 카드 수(`05` §20.7) | `TERM_CARD:{termKey}:{skillId}` |
| `UNIT_SOLVED` | (없음 — `source_type`·`source_id` 모두 null) | 노트의 skill | `{ lessonKey, unitKey, helpLevel, selfChecksMet }` — `helpLevel`은 `NONE`·`HINT`·`DUCK`·`ANSWER`(앱이 센 값, `05` §21.7), `selfChecksMet`은 모범 답안과 견준 개수(견주지 않고 넘어갔으면 없다). 단위를 마칠 때 **한 번에** 기록한다 — 이벤트를 나중에 고치지 않는다 | (없음 — 같은 단위를 여러 번 풀 수 있다) |
| `EVIDENCE_ACCEPTED` | EVIDENCE | evidence skill | `{ evidenceId }` | `EVIDENCE_ACCEPTED:{evidenceId}` |
| `PLAN_REPLANNED` | LEARNING_PLAN | null | `{ fromPlanId, toPlanId, fromVersion, toVersion, deferredSkillCodes[], reducedSkillCodes[] }` | `REPLANNED:{toPlanId}` |

- `UNIT_SOLVED`는 **복습 일정의 입력**이고 레벨의 증거가 아니다(`01` 원칙 4). 단위 문제는 서버가 채점하지 않으므로(`05` §21.6) 맞았는지를 모르고, `helpLevel`과 `selfChecksMet`만 남는다. skill updater의 입력에서 제외한다(`06` §7.1).
- `TIP_VIEWED`·`TERM_CARD_CREATED`는 **기록용**이다. `skill_id`가 있어도 skill updater의 입력에서 제외한다(`06` §7.1) — 팁을 받은 것과 용어 카드를 만든 것은 무엇을 할 수 있게 됐다는 증거가 아니다.
- 무효화: `invalidated_at`이 설정된 이벤트는 모든 규칙 계산과 지표에서 제외한다. MVP에는 무효화 API가 없고 ADMIN 운영 작업으로만 설정한다.
- `COACH_REVIEW_COMPLETED`와 `COACH_FINDING_CLOSED`는 `POST /coach/reviews/{id}/complete` 처리 트랜잭션에서 기록한다. `discoveredBy`는 그 시점에 확정한다(`06-learning-engine-rules.md` §9.3).

---

## 7. 불변식 · 제약 요약

| # | 불변식 | 강제 위치 |
|---|---|---|
| I-01 | 사용자당 학습 목표 1개 | `learning_goal.user_id unique` |
| I-02 | 사용자당 ACTIVE plan 1개 | partial unique index + `ReplanService` 순서 (SUPERSEDED flush 후 INSERT) |
| I-03 | plan-day당 daily_plan 1개 | `unique(user_id, plan_date)` |
| I-04 | daily_plan당 활성 main task(PLANNED/IN_PROGRESS) 1개. 남는 시간을 채우는 추가 과제(`06` §5.6)는 `is_main = false`다 | partial unique index + `TodayPlanService` |
| I-05 | 사용자당 IN_PROGRESS 세션 1개 | partial unique index. 새 세션 시작 시 기존 세션 ABANDONED 처리 후 flush |
| I-06 | 사용자·개념당 review item 1개 | `unique(user_id, concept_key)` — 같은 개념이면 기존 항목 갱신. conceptKey 패턴 `^[A-Z0-9_.:-]{3,150}$`은 seed·수동 카드에만 적용하고, 시스템 생성 키(`CHALLENGE:{uuid}`, `COACH:{skillCode}:{category}`)는 예외 |
| I-07 | VERIFIED는 도구·검수 근거만 | CHECK + `VerificationGuard` |
| I-08 | 도구 source_type은 VERIFIED/SUPPORTED만 | CHECK + guard |
| I-09 | VALIDATED challenge는 prompt/rubric/expectedConcepts 필수 | CHECK + `ChallengeValidationService` |
| I-10 | rubric weight 합 10000 | `ChallengeValidationService` |
| I-11 | 동일 대상·단계 hint 1회 저장 | `unique(target_type, target_id, hint_level)` |
| I-12 | skill 레벨 변경은 반드시 `skill_state_change` 동반 | `SkillStateUpdater` 단일 경로 (ArchUnit: `UserSkillState` 레벨 setter는 skill.application에서만 호출) |
| I-13 | learning_event 수정 금지 (무효화 컬럼 제외) | entity setter 미제공 |
| I-14 | coach content는 마스킹 후 저장 | `CoachReviewService` 순서 + 테스트 (AC-14) |
| I-15 | 타 사용자 리소스 접근 불가 | 모든 조회 `userId` 조건 + 권한 격리 테스트 |
| I-16 | 러버덕 세션 안에서 `turn_no`는 유일하다 (1부터 1씩 증가) | `rubber_duck_turn` `unique (session_id, turn_no)` + `RubberDuckService` (턴 상한은 `devpilot.rubberduck.max-turns`) |
| I-17 | `READ_CODE` 과제에는 `reading_key`가 **반드시** 있고, `READING` 과제에는 **있을 수도 없을 수도** 있으며(자료가 없는 skill이면 null), 그 밖의 과제에는 **없다** | CHECK `learning_task_reading_key_type`(V10에서 다시 만든다, §10.1 13번) + `TaskProposalPolicy`(`06` §5.3). 값은 `content/curated-repos.yaml`의 코드 읽기 `key`(`READ.*`) 또는 `content/concept-readings.yaml`의 개념 읽기 `key`(`DOC.*`)이고 FK가 없다 — 콘텐츠에서 은퇴해도 과제 행은 남는다(`19` §8.2). 두 파일은 한 key namespace를 쓴다(`19` §3.13, CV-120) |
| I-18 | 사용자당 `IN_PROGRESS` 러버덕 세션은 1개다 | partial unique index `uq_rubber_duck_session_one_in_progress` + `RubberDuckService`(시작 시 이전 세션 `ABANDONED` → flush → INSERT, 동시 시작 위반은 409 `CONCURRENT_MODIFICATION`, `05` §9.6). I-05(학습 세션)와 같은 방식 |
| I-19 | `reading_feedback`은 `READ_CODE` 과제에만 있을 수 있고(nullable), 값은 `ReadingFeedback`이다 | CHECK `learning_task_reading_feedback_type`(`reading_feedback is null or task_type = 'READ_CODE'`) + 값 CHECK + `TodayPlanService`(`status = COMPLETED` PATCH에서만 받는다, 그 외 400 `VALUE_NOT_ALLOWED`, `05` §8.4) |
| I-20 | `redo_source_task_id`는 `REDO` 과제에만 있고, `REDO` 과제에는 반드시 있다 | CHECK `learning_task_redo_source_type`(`(redo_source_task_id is not null) = (task_type = 'REDO')`) + `TaskProposalPolicy`(`06` §5.10 RE-3). 자기 참조 FK는 `on delete set null`이 아니라 **cascade 없음** — 원본 행은 사용자 삭제 때만 사라진다 |
| I-21 | `redo_without_ai`는 `REDO` 과제에만 있을 수 있고(nullable), `COMPLETED` 상태의 `REDO` 과제에는 반드시 있다 | CHECK `learning_task_redo_without_ai_type`(`redo_without_ai is null or task_type = 'REDO'`) + CHECK `learning_task_redo_answer_required`(`not (task_type = 'REDO' and status = 'COMPLETED' and redo_without_ai is null)`) + `TodayPlanService`(완료 PATCH에 `redoWithoutAi` 필수, 없으면 400 `VALUE_REQUIRED`, `05` §8.4) |
| I-22 | `side_project_note`의 본문 컬럼은 `note_type`과 일치한다 — `DECISION`이면 `decision_*` 셋이 모두 있고 `incident_*` 넷이 모두 null, `INCIDENT`이면 반대 | CHECK `side_project_note_body_by_type` + `SideProjectNoteService`(어긋나면 400 `VALUE_REQUIRED`/`VALUE_NOT_ALLOWED`, `05` §19.9) |
| I-23 | `side_project.kind`는 `SIDE` 또는 `PAST_WORK`다(기본 `SIDE`). `PAST_WORK` 프로젝트는 `PROJECT_TASK` 후보에서 빠진다 | CHECK `side_project_kind_check` + `TaskProposalPolicy`(`06` SP-3). 분류이므로 상태 전이표가 없다(§4.9) |
| I-24 | `explained_to_person`·`explained_note`는 `EXPLAIN`·`READ_CODE` 과제에만 있을 수 있다(둘 다 nullable) | CHECK `learning_task_explained_by_type`(`(explained_to_person is null and explained_note is null) or task_type in ('EXPLAIN','READ_CODE')`) + `TodayPlanService`(`status = COMPLETED` PATCH에서만 받는다, 그 외 400 `VALUE_NOT_ALLOWED`, `05` §8.4). `explained_note`는 마스킹본이다(`05` §1.11) |
| I-25 | `challenge.time_limit_minutes`는 null이거나 1~120이고, `challenge_attempt.elapsed_seconds`는 null이거나 0 이상이다 | CHECK `challenge_time_limit_minutes_check`, CHECK `challenge_attempt_elapsed_seconds_check` + `05` §10.9. 시간 제한은 문제(콘텐츠)의 성질이고 경과 시간은 시도의 기록이다 — 둘 다 규칙 입력이 아니다 |
| I-26 | 사용자·팁당 `user_daily_tip` 1행이고, 같은 팁을 두 번 제안하지 않는다 | `user_daily_tip_unique unique (user_id, tip_key)` + 선택 규칙의 제외 조건(`06` §5.12 TIP-2). `feedback` 값은 CHECK(`KNEW_IT`/`LEARNED`/`WILL_TRY`)이고 한 번만 기록한다(§4.11) |

- `rubber_duck_turn`에는 `user_id`가 없다. 소유자 검증은 `coach_finding`과 같이 부모(`rubber_duck_session.user_id`)로 한다(I-15).

---

## 8. 데이터 수명주기

| 데이터 | 보관 | 삭제 방식 |
|---|---|---|
| `coach_review.content` | 30일 (`devpilot.privacy.coach-content-retention-days`) | `CoachContentPurgeJob`: `content=null`, `content_purged_at` 설정. `DELETE /coach/reviews/{id}/content`로 즉시 삭제 가능 |
| `requirement_doc.source_text` | 180일 | `RetentionCleanupJob`: null + `source_text_purged_at` |
| `ai_call_log` | 180일 | 삭제. 계정 삭제 시 `user_id=null` |
| `idempotency_record` | 24시간 | 만료분 삭제 |
| 학습 기록 (event, answer, state change, finding, evidence) | 계정 유지 기간 | 계정 삭제 시 cascade |
| `user_daily_tip` (오늘의 팁 표시·선택 기록) | 계정 유지 기간 | 계정 삭제 시 cascade. 팁 본문은 콘텐츠라 저장하지 않는다 — `tip_key`만 남는다(ADR-041) |
| 러버덕 (`rubber_duck_session`, `rubber_duck_turn` — `user_text`는 마스킹본만, 원문 없음) | 계정 유지 기간 | 계정 삭제 시 cascade. `IN_PROGRESS`로 24시간 방치된 세션은 `StaleRubberDuckJob`이 `ABANDONED`로 바꾼다(삭제하지 않음) |
| `side_project` | 사용자가 삭제할 때까지 | `DELETE /side-projects/{id}` → 행 삭제, `learning_task`·`coach_review`의 `side_project_id`는 null. 계정 삭제 시 cascade |
| `side_project_note` (결정·장애 기록, 마스킹본) | 사용자가 삭제할 때까지 | `DELETE …/notes/{id}` → 행 삭제. 프로젝트 삭제와 계정 삭제 시 cascade(프로젝트가 사라지면 그 프로젝트의 기록도 사라진다 — `05` §19.6 삭제 안내에 적는다) |
| catalog (`skill`, `role_skill_target`, seed challenge) | 영구 | 비활성화 (`active=false`, `RETIRED`) |
| 백업 | 서버 7일 + 로컬 PC 8주(주 1회 pull) | `10-deployment-and-operations.md` §8 |

---

## 9. Seed 적재

- 원본: 저장소 `content/`(`19-content-spec.md`). 빌드 시 Gradle `processResources`가 `build/resources/main/content/`로 복사한다(소스 트리 `backend/src/main/resources/content/`는 만들지 않는다, `18-project-setup-and-local-dev.md`).
- `ContentSeeder`(ApplicationRunner, `devpilot.content.seed-on-startup=true`)
  1. `ContentValidator`로 전체 검증한다. 실패하면 **기동 실패**.
  2. `catalog.yaml`의 `catalogVersion`이 DB 버전(`max(skill.catalog_version)`, 행이 없으면 0)보다 크거나 같을 때만 upsert한다(키: `skill.code`, `(target_role, skill_code)`, `challenge.seed_key`). 작으면 seed를 건너뛰고 WARN 로그를 남긴다.
     - seed challenge는 **구조 필드**(skills, difficulty, purpose, isTransfer, rubric의 id·weightBp·axis, expectedConcepts)가 DB와 다르면 기동 실패다. 구조를 바꾸려면 기존 seedKey를 retire하고 새 seedKey로 추가한다. 텍스트·보조 필드(title, scenario, prompt, constraints, commonMistakes, hints, criterion 문구, estimatedMinutes, transferTargets)는 갱신한다.
  3. YAML에서 사라진 skill은 `active=false`, challenge는 `RETIRED`로 둔다. 삭제하지 않는다.
  4. (S2부터, `review_item`은 V4) seed review card는 공용 테이블이 없다. 카드 정의는 `review.application.SeedCardRegistry`(메모리, `PlanTemplateRegistry`와 같은 방식으로 `content`가 기동 시 등록)에 두고, **사용자 온보딩 시 해당 사용자의 `review_item`으로 복사**한다(`SeedCardAssignmentService.assignForNewUser`, `source_type=SEED_CARD`, `concept_key` 동일). 새 카드가 추가되면 `ContentSeeder`가 기동 끝에 `SeedCardAssignmentService.backfillAll()`을 호출해 기존 사용자에게도 추가한다(이미 있는 concept_key는 건너뜀).
  5. challenge upsert는 `devpilot.content.seed-challenges`(S1·S2 빌드 기본 `false`, S3부터 `true`)일 때만 실행한다. `false`면 challenge YAML은 검증만 한다.
- S1에 온보딩한 사용자는 seed 카드가 없다(`review_item` 테이블이 S2의 V4에서 생김). S2 배포 후 기동 시 step 4의 backfill로 배정된다. risk snapshot은 S2부터 만든다(budget·risk 규칙이 S2에 들어온다, `11` §3).
- seed 트랜잭션은 한 번에 처리한다. 사용자 수가 적고 데이터가 수천 행 이하이므로 문제없다.

---

## 10. Migration 계획

`backend/src/main/resources/db/migration/`

| Migration | Sprint | 내용 |
|---|---|---|
| `V1__baseline.sql` | S0 | `create schema if not exists devpilot`(Flyway `create-schemas=true`와 공존), Supabase hardening DO 블록(순수 PostgreSQL에서는 no-op — 현재 운영 DB) |
| `V2__user_goal_skill.sql` | S1 | `app_user`, `learning_goal`(학습 트랙 `target_role` + 목표일 `target_completion_date` — 날짜는 하나, ADR-039), `skill`, `skill_prerequisite`, `role_skill_target`, `learning_goal_focus_skill`, `user_skill_state`, `skill_state_change`, `idempotency_record` |
| `V3__plan.sql` | S1 | `learning_plan`, `plan_milestone`, `milestone_skill`, `plan_skill_target`, `plan_progress_snapshot` |
| `V4__learning_today_review.sql` | S2 | `learning_session`, `learning_event`, `ai_call_log`(challenge·review FK 대상), `daily_plan`, `challenge`(seed 참조용), `learning_task`, FK 추가, `review_item`, `review_answer` |
| `V5__training.sql` | S3 | `hint_disclosure`, `challenge_skill`, `challenge_attempt`, `challenge_submission` |
| `V6__coach.sql` | S4 | `coach_review`, `coach_finding`, `thinking_pattern_observation` |
| `V7__evidence_weekly.sql` | S5–S6 | `evidence_candidate`, `weekly_review` |
| `V8__requirement_radar.sql` | S7 | `requirement_doc`, `requirement_item` |
| `V9__rubberduck_project.sql` | S1(`side_project`) · S3(러버덕, 읽기 평가) | `side_project`, `rubber_duck_session`, `rubber_duck_turn`, `learning_task.side_project_id`·`reading_key`(+ CHECK `learning_task_reading_key_type`)·`reading_feedback`(`varchar(20)` null, 값 CHECK `HELPFUL`/`TOO_HARD`/`BORING` + CHECK `learning_task_reading_feedback_type`)·`coach_review.side_project_id` 추가 |
| `V10__track_notes_redo.sql` | S3(학습 트랙 3종, 프로젝트 기록, 경험 기록 분류, 오늘의 팁, 용어 카드, 설명 기록, 문제 시간 제한, 개념 읽기) · S4(재현 과제) | 아래 §10.1 |
| `V11__lesson_events.sql` | 개념 노트(학습 단위) | `learning_event.event_type` CHECK에 `UNIT_SOLVED`를 더해 다시 만든다(`learning_event_event_type_check`). **이것 하나뿐이다** — 개념 노트 본문은 콘텐츠이고 DB 테이블이 없다(`19` §3.14), 사용자 답도 저장하지 않는다(`05` §21.6) |

### 10.1 `V10__track_notes_redo.sql` (내용)

`V1`~`V9`는 고치지 않는다(ADR-038). `V10`은 **`alter`만** 쓴다.

| # | 내용 |
|---|---|
| 1 | `learning_goal.target_role`·`role_skill_target.target_role`의 값 CHECK를 `('JAVA_BACKEND','JAVA_BACKEND_STARTER','INTEGRATION_ENGINEER')`로 다시 만든다: `alter table … drop constraint if exists learning_goal_target_role_check` → `add constraint learning_goal_target_role_check check (…)` (같은 방식으로 `role_skill_target_target_role_check`). 이름은 PostgreSQL이 V2의 인라인 CHECK에 붙인 자동 이름이고, `V10`이 **명시적으로 같은 이름을 다시 붙인다** |
| 2 | `learning_task.task_type` CHECK를 `('RECALL','REVIEW','CHALLENGE','PROJECT_TASK','COACH_REVIEW','READING','READ_CODE','EXPLAIN','REDO')`로 다시 만든다(`learning_task_task_type_check`) |
| 3 | `learning_task`에 `redo_source_task_id uuid references learning_task(id)`(cascade 없음)와 `redo_without_ai boolean`을 추가하고 CHECK 셋을 붙인다: `learning_task_redo_source_type`, `learning_task_redo_without_ai_type`, `learning_task_redo_answer_required`(I-20·I-21) |
| 4 | 부분 인덱스 `idx_learning_task_redo_candidate`(§11) |
| 5 | `learning_event.event_type` CHECK에 `REDO_COMPLETED`, `TIP_VIEWED`, `TERM_CARD_CREATED`를, `source_type` CHECK에 `LEARNING_TASK`를 더해 다시 만든다(`learning_event_event_type_check`, `learning_event_source_type_check`) |
| 6 | `review_item.source_type` CHECK에 `REDO_TASK`·`TERM`·`TIP`을 더해 다시 만든다(`review_item_source_type_check`) |
| 7 | `side_project_note` 테이블 생성 + CHECK `side_project_note_body_by_type`(I-22) + 인덱스 `idx_side_project_note_project`(§11) |
| 8 | `skill.category` CHECK에 `INTEGRATION`을 더해 다시 만든다(`skill_category_check`). 값 순서는 §3과 같다 |
| 9 | `side_project`에 `kind varchar(20) not null default 'SIDE'` 추가 + CHECK `side_project_kind_check (kind in ('SIDE','PAST_WORK'))`(I-23) |
| 10 | `learning_task`에 `explained_to_person boolean`과 `explained_note varchar(500)` 추가 + CHECK `learning_task_explained_by_type`: `EXPLAIN`·`READ_CODE` 과제에만 값이 있을 수 있다(I-24) |
| 11 | `challenge`에 `time_limit_minutes int` 추가(null 허용, 있으면 1~120 — CHECK `challenge_time_limit_minutes_check`) + `challenge_attempt`에 `elapsed_seconds int` 추가(null 허용, 0 이상 — CHECK `challenge_attempt_elapsed_seconds_check`)(I-25) |
| 12 | `user_daily_tip` 테이블 생성(아래 SQL) + 인덱스 `idx_user_daily_tip_user_shown(user_id, shown_on desc)`(§11, I-26) |
| 13 | `learning_task`의 CHECK `learning_task_reading_key_type`을 **다시 만든다**: `alter table learning_task drop constraint if exists learning_task_reading_key_type` → `add constraint learning_task_reading_key_type check (...)`(아래 SQL). `READING` 과제도 `reading_key`를 가질 수 있게 한다 — 개념 읽기(`19` §3.13, `06` §5.3)를 코드 읽기와 같은 칸에 담는다(I-17). `learning_task_reading_feedback_type`은 **그대로 둔다**(읽기 평가는 `READ_CODE` 전용이다) |

```sql
create table side_project_note (
    id                  uuid primary key default gen_random_uuid(),
    user_id             uuid not null references app_user(id) on delete cascade,
    side_project_id     uuid not null references side_project(id) on delete cascade,
    note_type           varchar(20) not null check (note_type in ('DECISION','INCIDENT')),
    title               varchar(200) not null,
    occurred_on         date not null,
    skill_id            uuid references skill(id),          -- 선택. skill이 비활성화돼도 기록은 남는다
    decision_choice     text,                                -- DECISION: 무엇을 골랐나
    decision_options    text,                                -- DECISION: 어떤 선택지가 있었나
    decision_rationale  text,                                -- DECISION: 왜 그것을 골랐나
    incident_symptom    text,                                -- INCIDENT: 무엇이 잘못됐나
    incident_detection  text,                                -- INCIDENT: 어떻게 찾았나
    incident_fix        text,                                -- INCIDENT: 무엇으로 고쳤나
    incident_prevention text,                                -- INCIDENT: 무엇으로 다시 막나
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    version             bigint not null default 0,
    constraint side_project_note_body_by_type check (
        (note_type = 'DECISION'
         and decision_choice is not null and decision_options is not null and decision_rationale is not null
         and incident_symptom is null and incident_detection is null
         and incident_fix is null and incident_prevention is null)
     or (note_type = 'INCIDENT'
         and incident_symptom is not null and incident_detection is not null
         and incident_fix is not null and incident_prevention is not null
         and decision_choice is null and decision_options is null and decision_rationale is null))
);
```

```sql
-- §10.1 13번. V9의 CHECK는 (reading_key is not null) = (task_type = 'READ_CODE') 였다
alter table learning_task drop constraint if exists learning_task_reading_key_type;
alter table learning_task add constraint learning_task_reading_key_type check (
       (task_type = 'READ_CODE' and reading_key is not null)                    -- 코드 읽기: 반드시 있다
    or (task_type = 'READING')                                                  -- 개념 읽기: 있을 수도 없을 수도
    or (task_type not in ('READ_CODE', 'READING') and reading_key is null));    -- 그 밖: 없다
```

```sql
create table user_daily_tip (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references app_user(id) on delete cascade,
    tip_key     varchar(120) not null,               -- content/tips 의 key. FK 없음(코드 읽기 reading_key 선례)
    shown_on    date not null,                        -- plan-day
    feedback    varchar(20) check (feedback in ('KNEW_IT','LEARNED','WILL_TRY')),
    created_at  timestamptz not null default now(),
    constraint user_daily_tip_unique unique (user_id, tip_key)
);
```

규칙:
- **적용된 migration 파일은 수정하지 않는다.** 변경은 새 `V{n}`으로 한다. 이 규칙은 **첫 배포 시점부터** 발효한다 — `V1`~`V9`는 아직 어떤 환경에도 적용된 적이 없으므로(백엔드 코드가 없다) v3 설계로 늘어난 enum 값(`learning_event.event_type`·`source_type`, `learning_task.task_type`, `ai_call_log.operation`)은 `V9`의 `alter ... drop/add constraint`가 아니라 `V4`를 직접 고쳐 반영했다. **`V10`부터는 이 방식을 쓰지 않는다** — `V1`~`V9`는 ADR-038에 따라 확정됐으므로 `V10`은 `alter table … drop constraint if exists <이름>` → `add constraint <같은 이름> check (…)`으로 enum CHECK를 다시 만든다(§10.1). 인라인 CHECK의 자동 이름은 PostgreSQL이 `<table>_<column>_check`로 정하므로 그 이름을 그대로 쓰고, 다시 만들 때 **명시적으로 같은 이름을 붙인다**(다음 migration이 또 찾을 수 있게).
- Sprint 열은 그 테이블을 **처음 쓰는 단계**다(`11-development-roadmap.md` §3의 `S0`~`S7`). migration 파일은 V1~V9가 이미 저장소에 있고 Flyway는 버전 순서대로 **전부** 적용한다 — 단계별로 파일을 나눠 넣지 않는다. 그래서 `V9`가 `V6`의 `coach_review`를 고쳐도 순서 문제가 없다.
- **서버 개발 DB와 첫 배포 전 migration 수정**: 2026-09-18 확인 결과 서버 `devpilot` DB에는 SP-2 때의 스키마가 남아 있지 않았고, 첫 `bootRun`이 V1~V9를 적용했다(초기화 불필요). 첫 배포 전에 V1~V9를 다시 고치면 개발 DB의 `flyway_schema_history` checksum과 어긋나 기동이 실패하므로, 그때는 개발 DB의 `devpilot` schema를 지우고 다시 적용한다(실데이터 없음, `18` §5.3 "DB 초기화").
- 각 migration은 `schema.sql`의 해당 부분과 같아야 한다. `schema.sql`은 전체 migration 적용 결과의 스냅샷이므로 **migration 파일을 실제로 추가할 때 함께 갱신한다** — 설계만 적어 둔 단계에서 스냅샷을 앞서 고치면 `SchemaSnapshotConsistencyTest`가 실패한다(`09` §8.1).
- 파괴적 변경(컬럼 삭제, 타입 축소)은 백업 확인 후 2단계로 한다: (1) 코드가 쓰지 않게 배포 (2) 다음 migration에서 삭제.
- 통합 테스트: 빈 DB → 전체 migration → `ddl-auto=validate` 기동 성공.

---

## 11. 인덱스 근거

| 인덱스 | 쿼리 |
|---|---|
| `idx_review_item_due (user_id, status, due_at)` | `GET /reviews/due`: `where user_id=? and status='ACTIVE' and due_at <= ? order by due_at` |
| `idx_learning_event_user_skill_time` | skill updater: 사용자·skill의 최근 60일 이벤트 |
| `idx_learning_event_user_time` | 이력 목록, 지표 계산 |
| `idx_learning_session_user_time` | 세션 목록, completion rate(28일) |
| `idx_challenge_attempt_user_time` | attempt 목록, 지표 |
| `idx_coach_review_user_time` | coach 이력 |
| `idx_coach_review_status`, `idx_challenge_submission_status` | `OrphanAsyncTaskJob` |
| `idx_coach_review_retention` | purge job |
| `idx_snapshot_user_date` | dashboard risk 추세 |
| `idx_thinking_obs_user_axis_time` | thinking pattern 추세, D 하락 규칙 |
| `idx_rubber_duck_session_user_time (user_id, started_at desc)` | 러버덕 세션 목록·최근 세션: `where user_id=? order by started_at desc` |
| `idx_rubber_duck_session_in_progress (started_at) where status='IN_PROGRESS'` | `StaleRubberDuckJob` (§4.8). 부분 인덱스라 완료된 세션은 담지 않는다 |
| `uq_rubber_duck_session_one_in_progress (user_id) where status='IN_PROGRESS'` | I-18(사용자당 진행 중 1개) 강제 + 진행 중 세션 조회(`where user_id=? and status='IN_PROGRESS'`) |
| `rubber_duck_turn` `unique (session_id, turn_no)` | 세션의 턴을 순서대로 조회(`where session_id=? order by turn_no`). unique 제약의 인덱스가 정렬을 겸하므로 별도 인덱스를 두지 않는다 |
| `idx_side_project_user_status (user_id, status, updated_at desc)` | planner: `where user_id=? and status='ACTIVE' order by updated_at desc limit 1` (`06` SP-3), 프로젝트 목록 |
| `idx_side_project_note_project (side_project_id, occurred_on desc, id desc)` | 프로젝트 기록 목록·cursor 정렬: `where side_project_id=? [and note_type=?] order by occurred_on desc, id desc` (`05` §19.10) |
| `idx_user_daily_tip_user_shown (user_id, shown_on desc)` | 오늘의 팁: 그 plan-day에 이미 보여 준 팁이 있는지(`where user_id=? order by shown_on desc limit 1`)와 최근 표시 이력. "이미 본 팁" 제외는 `user_daily_tip_unique (user_id, tip_key)`가 맡는다(`05` §20.2) |
| `idx_learning_task_redo_candidate (user_id, task_type, status, completed_at desc) where task_type in ('CHALLENGE','PROJECT_TASK','REDO')` | 재현 과제 후보 조회(`06` §5.10 RE-2 1단계 두 쿼리): 최근 완료한 CHALLENGE·PROJECT_TASK, 그리고 그 사용자의 모든 `REDO` task(열려 있는 것·지난 시도). 부분 인덱스라 `READING`·`EXPLAIN` 등은 담지 않는다. RE-5의 잠금 판정(`RedoLockService`)도 같은 인덱스를 쓴다 |
| FK 인덱스 (`idx_*_plan`, `_review`, `_doc` 등) | 부모 삭제 cascade, 자식 조회 |

`learning_task.redo_source_task_id`에는 별도 인덱스를 두지 않는다 — 조회는 항상 `idx_learning_task_redo_candidate`로 사용자·기간을 먼저 좁힌 뒤 메모리에서 짝짓는다(RE-2 창이 최대 며칠이라 행이 적다).

`learning_task.side_project_id`·`reading_key`와 `coach_review.side_project_id`에는 인덱스를 두지 않는다 — `learning_task.challenge_id`·`milestone_id`와 같은 기준이다(프로젝트 삭제가 드물고, 프로젝트별 과제·리뷰 목록 조회가 없다). 조회가 생기면 그때 인덱스를 추가한다.
