# 15. Glossary

> Status: Accepted (v2) · Last updated: 2026-09-18 (v3: 러버덕 · 코드 읽기 · 사이드 프로젝트 · 교차 학습 · 확장 제안 · 단계) · Related: `04-domain-model-and-db.md` §3, `05-api-spec.md`, `06-learning-engine-rules.md`, `08-coding-conventions.md`, `11-development-roadmap.md`, ADR-010
>
> 용어 정의와 **한국어 → 영어 식별자 명명 사전**이다. 코드·DB·API·Dart의 이름은 §6을 따른다. 사전에 없는 도메인 용어가 필요하면 구현 전에 이 문서에 먼저 추가한다(`16-definition-of-ready-done.md` §1).

---

## 1. 제품 용어

| 용어 | 정의 |
|---|---|
| Learning goal (학습 목표) | 학습 트랙(`JAVA_BACKEND`)과 학습 목표일, 선택 항목인 중간 점검일(MUST 항목을 끝낼 날). 사용자당 1개 |
| Checkpoint date (중간 점검일) | 학습 목표일보다 앞서 핵심(MUST) 항목을 끝내기로 정한 날(`checkpointDate`, 선택). 있으면 budget horizon이 되고, 그 뒤는 정리 단계다 |
| Consolidation phase (정리 단계) | 중간 점검일부터 학습 목표일까지의 구간(계획 템플릿 milestone phase `CONSOLIDATION`, `19` §5). 만든 것을 설명으로 정리하고 CS 기초의 빈틈을 채운다(milestone "설명과 정리", `EXPLAIN_AND_CONSOLIDATE`) |
| Learning plan (학습 계획) | 목표까지의 milestone과 skill 목표 묶음. 구조가 바뀌면 새 plan version이 생긴다 |
| Plan version (계획 버전) | 불변 계획 스냅샷 번호(`planVersion`). 사용자당 `ACTIVE` 1개, 이전 버전은 `SUPERSEDED` |
| Milestone | 기간(시작·종료일)과 priority, skill 목록을 가진 계획 단위 |
| Daily plan (일일 계획) | plan-day 하나의 학습 묶음. main task 1개 + 복습 task 0~1개 |
| Main task (핵심 과제) | 그날 가장 가치 높은 단일 과제. 진행 가능한(`PLANNED`/`IN_PROGRESS`) main은 하루 1개(WIP = 1) |
| Learning session (학습 세션) | 실제 학습 시작~완료 기록과 실제 시간·회고. 완료율 계산의 원천 |
| Learning event (학습 이벤트) | 풀이·hint·복습·리뷰·증거 승인 등 skill 상태 판단에 쓰는 append-only 사건 |
| Review item (복습 항목) | 스케줄(due, interval)을 가진 복습 카드. 사용자·개념당 1개 |
| Review answer (복습 응답) | 복습 1회 답변 기록(자기평가, 최종 등급, 간격 전후). append-only |
| Challenge | rubric과 단계별 hint를 가진 연습·진단 문제 |
| Attempt (시도) | 사용자가 challenge 하나를 푸는 과정. 제출(submission)을 최대 5회 가진다 |
| Project Coach | 사용자의 실제 코드·diff·로그를 self-review 후 AI 질문형 리뷰로 학습하는 기능 |
| Finding | Coach 분석 결과 항목. 유형(`BUG`/`RISK`/`LEARNING_POINT`), 사고 축, 학습 질문, 검증 상태를 가진다 |
| Self-review (자기 리뷰) | Coach 분석 요청 전에 사용자가 적는 우려점. thinking pattern 판정 기준 |
| Thinking pattern (사고 패턴) | 코드에서 사용자가 스스로 고려하는 관점(10개 축)의 누적 관찰 |
| Evidence (증거) | 사용자가 직접 해결하고 설명한 학습·문제 해결 사실. 후보 → 사용자 승인(`ACCEPTED`). 승인한 증거는 STAR(상황-과제-행동-결과) 형식의 **학습 기록 정리**(Markdown export)로 내보낼 수 있다 |
| Weekly review (주간 리뷰) | 지난 plan-day 주의 지표 스냅샷과 사용자 회고 |
| Requirement Radar (요구 역량 비교) | 기술 요구사항 목록(팀 기술 스택 문서, 프로젝트 명세, 학습 로드맵 등)을 붙여넣어 요구사항별 준비 상태(READY/STRETCH/LATER)를 보는 기능. 점수·확률 없음, 서버는 URL을 가져오지 않는다 |
| Comeback mode (복귀 모드) | 최근 3 plan-day 학습이 없다가 돌아온 날 부담을 줄이는 planner 보정 |
| Calendar feed (캘린더 구독) | 토큰 URL로 제공하는 ICS 일정. 학습 시작 알림을 캘린더 앱에 맡긴다 |
| Onboarding (온보딩) | 목표·시간·시작 수준(짧은 진단 또는 카테고리 자기평가 13개, `runDiagnostic`)·계획 템플릿·사이드 프로젝트(`sideProject`, 건너뛰기 가능)를 한 번에 저장하는 첫 설정 |
| Diagnostic challenge (진단 문제) | 시작 수준을 실제 풀이로 정하는 문제. 온보딩에서 진단을 고르면 카테고리당 1문제(최대 5), 자기평가를 고르면 자기평가 3 이상 카테고리를 확인한다(`05` §4.2) |
| Rubber duck (러버덕) | 학습자가 설명하고 **AI는 답을 주지 않고 질문 1개만 되묻는** 대화(`RubberDuckSession`). 세션은 대상 1개·skill 1개를 다루고, 종료할 때 한 번 정리한다. 막힌 지점(gap)은 복습 카드가 된다(`06` §9.5 RD-1~RD-7) |
| Turn (턴) | 러버덕에서 학습자 설명 1개 + AI 질문 1개의 한 왕복(`RubberDuckTurn`). 세션당 최대 5턴(`devpilot.rubberduck.max-turns`) |
| Summary (러버덕 정리) | 세션 종료 시 AI가 한 번 만드는 `gaps[]`·`confirmed[]`·`overallNote`(`RUBBER_DUCK_SUMMARY`). 실패해도 세션은 `COMPLETED`이고 대화는 남는다 |
| Gap (막힌 지점) | 정리에서 학습자가 설명하지 못한 개념 1개(`conceptKey`, `whatWasMissed`, `whyItMatters`, `reviewQuestion`). gap마다 복습 카드를 만든다 |
| Code reading (코드 읽기) | 검증된 오픈소스의 **파일 1개·줄 범위 1개**를 읽고 러버덕으로 설명하는 과제(`TaskType.READ_CODE`). 서버는 코드를 가져오지 않고 사용자가 로컬에 clone해 읽는다. 완료 조건은 러버덕 세션 1개 완료(RC-1) |
| Reading (읽기 범위) | 코드 읽기 과제 하나가 가리키는 콘텐츠 항목: 저장소·경로·줄 범위·질문·볼 지점(`content/curated-repos.yaml`의 `readings[]`, key `READ.<REPO>.<TOPIC>.NNN`) |
| Curated repo (큐레이션 저장소) | 코드 읽기 대상으로 검증해 등록한 오픈소스 저장소(`repos[]`). 근거 ID인 curated source(§3)와 다르다 |
| Pinned commit (기준 커밋) | reading의 줄 번호가 맞는 저장소 커밋 SHA(`pinnedCommit`, 40자 hex). 저장소가 바뀌어도 줄 번호가 틀어지지 않게 `cloneHint`가 이 커밋을 체크아웃한다(`19` §8.4) |
| Side project (사이드 프로젝트) | 학습한 것을 적용해 **직접 만드는** 사용자 프로젝트(기본 예: "주문 시스템", `SideProject`). `PROJECT_TASK` 과제와 코치 리뷰·러버덕(`PROJECT_WORK`)의 대상이다. DevPilot 자체는 사이드 프로젝트가 아니다 |

## 2. 학습 엔진 용어

| 용어 | 정의 |
|---|---|
| Plan-day | 사용자 timezone과 하루 시작 시각 기준의 "하루". 모든 "오늘"은 plan-day다(`06` §2) |
| Day start hour (하루 시작 시각) | plan-day가 바뀌는 로컬 시각(0~6, 기본 4) |
| Horizon | budget 계산의 마감일. 미래의 중간 점검일, 없으면 학습 목표일 |
| Nominal budget | horizon까지 설정된 평일·주말 학습 가능 시간의 합(분) |
| Completion rate (완료율) | 최근 28 plan-day의 실제 학습 시간 / 가능 시간(bp, 3000~10000, 기록 부족 시 7000) |
| Effective budget | nominal × completion rate. 실제로 쓸 수 있을 것으로 보는 시간 |
| Required minutes | skill 목표와 planning level 차이를 축 비용·level step 시간으로 환산한 필요 시간 |
| Deadline risk | required MUST / effective 비율로 판정한 위험(`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`) |
| Replan suggestion | 기한 역산 제안(`ReplanSuggestionPolicy`). risk가 HIGH 이상이면 **축소**(MUST 목표 축소·SHOULD defer), risk LOW이고 `ratioBp ≤ 7000`(여유 30% 이상)이면 **확장**. 두 방향은 배타이고 자동 적용하지 않는다(`06` §4.4) |
| Expansion suggestion (확장 제안) | 여유가 있을 때 깊이를 더하는 제안(`expansionSuggestions[]`, `ExpansionKind`): 미뤘던 SHOULD/LATER 복원(`RESTORE_DEFERRED`)과 MUST 목표 레벨 +1(`RAISE_TARGET`). 받아들이면 replan의 `restoredDeferrals`·`acceptedTargetRaises`로 들어간다 |
| Interleaving (교차 학습) | 같은 skill 복습 카드가 3장 연속 나오지 않게 출제 순서만 바꾸는 결정적 재배치(`RV-INTERLEAVE`, `06` §6.5). 무엇을 낼지(정렬·cap)는 바꾸지 않는다 |
| Don't-know turn ("모르겠다" 턴) | 러버덕에서 학습자 답이 "모르겠다"류인 턴. **서버가 문구·길이 규칙으로 판정한다**(AI 판정 아님). 2턴 연속이면 `CHALLENGE` 대상은 Hint Ladder로 넘길 수 있다(RD-3, `learner_stuck`, `suggestHint`) |
| Defer (미루기) | plan skill target을 계산·후보에서 제외(`deferred = true`) |
| Priority | `MUST`(중간 점검일 전 필수) / `SHOULD`(가능하면) / `LATER`(나중에) |
| Practical importance (실무 중요도) | 학습 트랙에서 skill의 실무 중요도(0~1, planner에서는 micro) |
| Factor · Modifier | planner 점수의 6개 요소와 조건부 곱셈 보정 |
| Reason code | main task가 선택된 이유 코드. 템플릿 문구로 1~3개 표시 |
| Evidence level (증거 레벨) | 학습 이벤트 규칙으로만 바뀌는 축별 레벨 |
| Self-assessed level (자기평가 레벨) | 온보딩 카테고리 자기평가(0~5)를 skill에 전파한 값 |
| Planning level (계획용 레벨) | planner·budget 계산용 레벨. 자기평가가 유효하면 `max(evidence, min(self, 3))`, 아니면 evidence |
| Cooldown | 같은 축의 레벨 변경 후 24시간 동안 다시 바꾸지 않는 규칙 |
| Final rating (최종 등급) | 자기평가를 평가 결과·hint 사용으로 낮춘 복습 등급 |
| Interval (간격) | 다음 due까지 plan-day 수(1~60) |
| Due | 복습 예정 시각(`due_at`). 오늘 due = `due_at < planDayStart(today + 1)` |
| Leech | 연속 4회 실패한 복습 항목. 자동 일시중지 |
| Variant (변형 문항) | 연속 2회 실패한 항목에 AI가 만든 같은 개념의 다른 문항 |
| Recall | 답을 보기 전에 기억에서 꺼내는 행위 |
| Transfer problem (전이 문제) | 같은 원리를 다른 상황에 적용하는 문제. 레벨 5 판정 근거 |
| Rubric coverage | 충족한 rubric 항목 가중치 합(bp) |
| Independent solve (독립 해결) | 최대 hint가 `QUESTION_ONLY` 이하이면서 정답인 해결 |
| Hint Ladder | `SELF_EXPLAIN(0)`부터 `FULL_EXAMPLE(6)`까지 도움을 한 단계씩 늘리는 정책(HL-1~HL-8) |
| Max hint level (최대 hint 단계) | 한 attempt·finding에서 공개된 가장 높은 hint 단계 |
| Discovered by | coach finding을 사용자가 스스로 언급했는지(`MENTIONED_UNPROMPTED`), 질문 후 찾았는지(`FOUND_AFTER_HINT`), 놓쳤는지(`MISSED`) |

## 3. AI · 검증 용어

| 용어 | 정의 |
|---|---|
| AI operation | AI 호출 종류 11개(`AiOperation`). 동기 5개(러버덕 `RUBBER_DUCK`·`RUBBER_DUCK_SUMMARY` 포함), 비동기 6개 |
| AI gateway | 도메인 모듈이 AI를 호출하는 유일한 진입점. 예산 → 호출 → 가드 → 기록 |
| Fake provider | fixture 응답을 돌려주는 테스트·로컬용 provider |
| Output guard (출력 가드) | AI 출력을 서버에서 검사·강등·거절하는 규칙 7종 |
| No-answer guard | 러버덕 출력이 정답 단정·코드를 담지 않았는지 문자열로 검사하는 가드(`NoAnswerGuard`, NA-1~NA-4, `17` §6.8). 서술형 정답은 못 잡으므로 eval이 보완한다 |
| Verification status | finding 근거의 신뢰 수준 `VERIFIED` / `SUPPORTED` / `AI_JUDGMENT` / `UNCERTAIN` |
| `VERIFIED` | 서버가 실행한 도구 결과 또는 등록된 curated 근거가 있는 주장. AI 판단만으로는 불가 |
| `SUPPORTED` | allowlist 호스트의 공식 문서·보안 가이드가 지지하는 주장 |
| `AI_JUDGMENT` | 설계·가독성 등 AI 판단. 단일 정답이 아님 |
| `UNCERTAIN` | 버전·환경·맥락 부족으로 판단 보류 |
| Confidence | AI가 표시한 확신도 `HIGH`/`MEDIUM`/`LOW`. 검증 상태와 별개 |
| Curated source | `content/curated-sources.yaml`에 등록되어 사람이 검수한 근거 ID. 코드 읽기 대상인 curated repo(§1)와 다르다 |
| Source reference (근거 참조) | finding 근거의 URL 또는 curated ID. "evidence"와 다른 개념 |
| Secret masking | 저장·전송 전 비밀값을 `[REDACTED:…]`로 바꾸는 처리. private key는 차단 |
| Budget guard | 월 예산(서비스 전체)·일일 호출 수·동시 실행 수를 호출 전에 확인하는 가드 |
| Refusal | 모델이 요청 처리를 거절한 응답(`stop_reason = refusal`) |
| Eval | 실제 모델로 prompt·모델·가드 품질을 측정하는 case 세트. PR마다 돌리지 않는다 |
| Prompt injection | 사용자 입력 안의 지시문으로 모델 동작을 바꾸려는 시도 |

## 4. Skill 레벨

4개 축(KNOWLEDGE, IMPLEMENTATION, EXPLANATION, DEBUGGING)마다 따로 판정한다. 상승·하락 규칙은 `06` §7.

| 값 | 이름 | 의미 |
|---|---|---|
| 0 | `UNKNOWN` | 모름 |
| 1 | `SEEN` | 본 적 있음, 관련 활동 기록 있음 |
| 2 | `GUIDED` | 도움(hint)을 받아 설명·구현·발견 가능 |
| 3 | `INDEPENDENT_BASIC` | 기본 문제를 독립적으로 해결·설명 |
| 4 | `PRACTICAL` | 실무 수준 문제·긴 간격 기억·반복된 스스로 발견 |
| 5 | `TRANSFERABLE` | 다른 상황으로 원리를 전이하고 trade-off를 설명 |

## 5. 기술 용어

| 용어 | 정의 |
|---|---|
| Modular monolith | 하나의 배포 단위 안에서 모듈 경계·의존 방향을 강제하는 구조(ADR-002) |
| Aggregate root | 함께 일관성을 지키는 entity 묶음의 진입점. 다른 aggregate는 ID로만 참조 |
| Walking skeleton | 모든 계층을 관통하는 최소 기능을 먼저 배포하는 방식 |
| Spike | 기술 불확실성을 timebox 안에 확인하는 실험(`11` §4의 SP-1~SP-5). 사이드 프로젝트 규칙 ID SP-1~SP-3(`05` §4.1·§19, `06` §5.3에서 참조)과 접두사가 같으므로 문서에서는 "spike SP-2", "사이드 프로젝트 규칙 SP-2"처럼 구분해 쓴다 |
| 단계 (S0~S7) | **구현 순서를 나타내는 단계 ID.** 기간·날짜·일수가 없고 exit criteria를 통과하면 끝난다(`11` §3). BL의 `Sprint` 열 값(`S0`~`S7`, `Later`, `공개 배포 전`)도 이 단계 ID다. "Sprint"라는 열 이름은 남아 있지만 2주 주기를 뜻하지 않는다 |
| M1 | 쓸 수 있는 최소 = S0·S1·S2·S3. **S3 완료 = 실사용 시작**(매일 쓰기 시작). S2가 끝나면 AI 없이 Today·Review만 먼저 쓸 수도 있다(선택) |
| M2 | S4·S5·S6·S7. M1을 쓰면서 필요한 순서로 진행한다(단계 순서는 잠정) |
| 실사용 시작 | M1 완료(= S3 완료) 시점. 날짜로 쓰지 않는다. 학습 목표일·중간 점검일은 사용자가 설정에 등록하는 값(`learning_goal.target_completion_date`·`checkpoint_date`)이다 |
| Test vector | 규칙 입력과 기대 출력 표. `@ParameterizedTest`로 그대로 옮긴다 |
| bp (basis point) | 비율 정수 표현. 1.0 = 10,000 |
| micro | 점수·비용 정수 표현. 1.0 = 1,000,000 (`costMicroUsd`) |
| milli | 1/1000 정수 표현(`averageHintLevelMilli`) |
| Optimistic lock | `version` 비교로 동시 수정 충돌을 409로 알리는 방식 |
| Idempotency key | 같은 사용자 행동의 재시도를 1회 처리로 묶는 요청 헤더 |
| Problem Details | RFC 9457 오류 응답 형식 |
| Cursor pagination | `(sortKey, id)`를 인코딩한 불투명 문자열로 다음 페이지를 조회하는 방식 |
| JWKS | JWT 서명 검증용 공개키 목록 endpoint |
| Allowlist | 서비스를 쓸 수 있는 이메일·subject 목록 |
| devtoken | 운영을 포함한 기본 인증 모드. backend가 EC P-256 키로 서명·검증하는 JWT를 `POST /api/v1/dev/token`으로 발급한다(`03` §4.2). tailnet 전용 배포를 전제로 한다 |
| tailnet | Tailscale로 연결된 사설 네트워크. DevPilot은 여기에서만 접근된다 |
| Session pooler | (Supabase 도입 시) Supavisor의 session mode 연결(포트 5432) |
| Data API | (Supabase 도입 시) publishable key로 제공되는 REST/GraphQL 자동 API. DevPilot은 비활성 |
| BALANCE_EXHAUSTED | AI 공급자의 선불 잔액이 소진된 상태(`AiStatus`). 예산 초과(`DISABLED`)와 구분해 표시한다 |
| PWA | 홈 화면에 설치해 앱처럼 쓰는 웹 앱 |
| Plan-day job | 사용자 로컬 시각이 `dayStartHour`일 때만 사용자별로 처리하는 매시 실행 job |
| Orphan task | 서버 재시작 등으로 `RUNNING`에 멈춘 비동기 작업 |
| Purge | 보존기간이 지난 원문을 null로 바꾸고 purge 시각을 남기는 처리 |

---

## 6. 도메인 명명 사전

규칙:
- Java class·enum은 PascalCase, 필드·JSON은 lowerCamelCase, DB는 snake_case 단수형, enum 값은 UPPER_SNAKE. Dart 모델 필드는 JSON 이름과 같다.
- 표의 이름을 줄이거나 동의어로 바꾸지 않는다(`ReviewItem`을 `Card`, `Flashcard`로 부르지 않는다).
- 한 개념에 이름 하나. 표의 "주의"에 적힌 혼동을 피한다.

| 한국어 용어 | Java (class / field) | DB (table / column) | API (path / JSON) | 주의 |
|---|---|---|---|---|
| 사용자 | `AppUser` | `app_user` | `/me` | `User`는 Spring Security 타입과 충돌하므로 쓰지 않는다 |
| 외부 인증 ID | `externalAuthId` | `external_auth_id` | (노출 안 함) | JWT `sub` |
| 현재 사용자 | `CurrentUser` | — | — | 컨트롤러 파라미터 |
| 허용 목록 | `allowedEmails`, `allowedSubjects` | — | — | 설정 `devpilot.security.allowed-*` |
| 개발 경험 프로필 | `ExperienceProfile experienceProfile` | `experience_profile` | `experienceProfile` | 값 `WORKING_DEVELOPER`(현업 개발자) · `DEVELOPER_STARTER`(개발 입문) · `OTHER`(기타) |
| 온보딩 완료 | `onboardingCompletedAt` | `onboarding_completed_at` | `onboardingCompleted`(boolean) | |
| 시간대 | `timezone` (`ZoneId zoneId`) | `timezone` | `timezone` | IANA ID 문자열 |
| 하루 시작 시각 | `dayStartHour` | `day_start_hour` | `dayStartHour` | `dayStartTime`, `resetHour` 금지 |
| 평일 학습 시간 | `weekdayStudyMinutes` | `weekday_study_minutes` | `weekdayStudyMinutes` | 분 단위는 항상 `…Minutes` |
| 주말 학습 시간 | `weekendStudyMinutes` | `weekend_study_minutes` | `weekendStudyMinutes` | |
| 오늘(plan-day 날짜) | `planDate`, `today` | `plan_date` | `planDate`, `today` | 달력 날짜 의미의 `date` 단독 사용 금지 |
| plan-day 시작 시각 | `planDayStart` | — | — | |
| 학습 목표 | `LearningGoal`, 모듈 `com.devpilot.goal` | `learning_goal` | `/learning-goal`, `learningGoal` | |
| 학습 트랙 | `TargetRole targetRole` | `target_role` | `targetRole` | |
| 중간 점검일 | `checkpointDate` | `checkpoint_date` | `checkpointDate` | |
| 학습 목표일(학습 완료 목표일) | `targetCompletionDate` | `target_completion_date` | `targetCompletionDate` | `deadline` 단독 사용 금지 |
| 집중 skill | `LearningGoalFocusSkill` | `learning_goal_focus_skill` | `focusSkillCodes` | |
| 학습 계획 | `LearningPlan` | `learning_plan` | `/plans`, `plan` | 일일 계획(`DailyPlan`)과 구분 |
| 계획 버전 | `planVersion` | `plan_version` | `planVersion` | JPA `version`과 다른 필드 |
| 낙관적 락 버전 | `version` | `version` | `version` | |
| 대체된 계획 | `PlanStatus.SUPERSEDED`, `supersedesPlanId` | `supersedes_plan_id`, `superseded_at` | `supersedesPlanId` | |
| 재계획 | `ReplanService` | — | `/replan`, `/replan/preview` | `reschedule` 금지 |
| 재계획 권장 | `replanRecommended` | `replan_recommended` | `replanRecommended` | |
| 변경 사유 | `changeReason` | `change_reason` | `reason`(요청) | |
| 마일스톤 | `PlanMilestone` | `plan_milestone` | `milestones[]` | |
| 계획별 skill 목표 | `PlanSkillTarget` | `plan_skill_target` | `skillTargets[]` | |
| 역할 기본 목표 | `RoleSkillTarget` | `role_skill_target` | `roleTargets` | |
| 우선순위 | `Priority priority` | `priority` | `priority` | `importance`와 구분 |
| 실무 중요도 | `practicalImportance` | `practical_importance` | `practicalImportanceBp` | API는 bp 정수 |
| 미루기 | `deferred`, `TargetAdjustment.DEFERRED` | `deferred` | `acceptedDeferrals`, `restoredDeferrals` | `postponed`, `skip` 금지 |
| 목표 축소 | `TargetAdjustment.TARGET_REDUCED` | `adjustment` | `acceptedTargetReductions` | |
| 확장 제안 | `ExpansionKind`(`RESTORE_DEFERRED`, `RAISE_TARGET`), `ExpansionSuggestionView` | — (저장 안 함) | `expansionSuggestions[]`, `kind` | 축소 제안(`mustTargetReductionSuggestions`, `deferSuggestions`)과 한 응답에 함께 비어 있지 않은 경우가 없다 |
| 목표 상향 | `TargetRaiseInput`, `ExpansionKind.RAISE_TARGET` | `adjustment = USER_EDITED` | `acceptedTargetRaises`, 오류 `TARGET_NOT_RAISED` | `upgrade`, `boost` 금지. 목표 축소의 반대말로만 쓴다 |
| 진행 스냅샷 | `PlanProgressSnapshot` | `plan_progress_snapshot` | `latestSnapshot` | |
| 학습 예산 | `StudyBudgetCalculator` | — | `/plans/active/budget` | `timeBudget` 금지 |
| 명목 예산 | `nominalBudgetMinutes` | `nominal_budget_minutes` | `nominalBudgetMinutes` | |
| 완료율 | `completionRateBp` | `completion_rate_bp` | `completionRateBp` | |
| 유효 예산 | `effectiveBudgetMinutes` | `effective_budget_minutes` | `effectiveBudgetMinutes` | |
| 필요 시간(MUST/SHOULD) | `requiredMustMinutes`, `requiredShouldMinutes` | `required_must_minutes` | `requiredMustMinutes` | |
| 마감 위험 | `RiskLevel riskLevel` | `risk_level`, `deadline_risk` | `riskLevel`, `deadlineRisk` | |
| 위험 비율 | `ratioBp` | `ratio_bp` | `ratioBp` | `ratio`(double) 금지 |
| 마감 기준일 | `horizonDate` | `horizon_date` | `horizonDate` | |
| 일일 계획 | `DailyPlan` | `daily_plan` | `/today` | |
| 가능 시간 | `availableMinutes` | `available_minutes` | `availableMinutes` | |
| 컨디션 | `EnergyLevel energyLevel` | `energy_level` | `energyLevel` | `condition`, `mood` 금지 |
| 학습 과제 | `LearningTask` | `learning_task` | `/today/tasks`, `tasks[]` | `Todo`, `Job` 금지 |
| 핵심 과제 | `isMain` (`boolean main`) | `is_main` | `mainTask` | |
| 과제 유형 | `TaskType taskType` | `task_type` | `taskType` | |
| 코드 읽기 과제 | `TaskType.READ_CODE` | `task_type = 'READ_CODE'` | `taskType` | 개념 읽기 과제 `READING`과 다르다. 러버덕 대상 값은 `CODE_READING`이다(아래) — 두 이름을 섞지 않는다 |
| 읽기 범위 (reading) | `CuratedReadingView`, `CuratedReadingRegistry`, `readingKey` | `reading_key` (FK 없음, CHECK `learning_task_reading_key_type`) | `/readings/{readingKey}`, `readingKey` | key 형식 `READ.<REPO>.<TOPIC>.NNN`. `Lesson`, `Snippet`, `CodeSample` 금지 |
| 큐레이션 저장소 | `CuratedRepoView` (`repo.key`) | — (`content/curated-repos.yaml`의 `repos[]`) | `repo` | 근거 ID인 curated source(`curated-sources.yaml`)와 구분 |
| 기준 커밋 · clone 안내 | `pinnedCommit`, `cloneHint` | — | `pinnedCommit`, `cloneHint` | `pinnedCommit`은 40자 소문자 hex SHA. `commitHash`, `revision` 금지 |
| 사이드 프로젝트 | `SideProject`, `SideProjectService`, `SideProjectQueryService`, 모듈 `com.devpilot.project` | `side_project`, `side_project_id` | `/side-projects`, `sideProjectId`, `sideProject`(온보딩) | `Project` 단독 클래스, `sideproject`·`Sideproject`(한 단어), `PersonalProject` 금지. 코치의 `CoachProjectType`·과제 `PROJECT_TASK`와 구분 |
| 사이드 프로젝트 상태 | `SideProjectStatus` | `status` | `status` | `ACTIVE` / `PAUSED` / `DONE` |
| 저장소 URL | `repoUrl` | `repo_url` | `repoUrl` | 저장만 한다. 서버는 fetch하지 않는다(`07` §5.5) |
| 추천 사유 | `ReasonCode`, `reasonCodes` | `reason_codes` | `reasons[]` | |
| 점수 내역 | `ScoreBreakdown scoreBreakdown` | `score_breakdown` | (MVP 비노출) | |
| 복귀 모드 | `comebackMode`, `ComebackModePolicy` | `comeback_mode` | `comebackMode` | `returnMode` 금지 |
| 재생성 횟수 | `generationCount` | `generation_count` | `generationCount` | |
| 학습 세션 | `LearningSession` | `learning_session` | `/learning-sessions` | `StudySession` 금지 |
| 실제 학습 시간 | `actualMinutes` | `actual_minutes` | `actualMinutes` | |
| 세션 회고 | `selfReflection` | `self_reflection` | `selfReflection` | 주간 회고(`reflection`)와 구분 |
| 러버덕 세션 | `RubberDuckSession`, `RubberDuckService`, `RubberDuckController`, `RubberDuckPolicy` | `rubber_duck_session` | `/rubber-duck`, `sessionId` | 식별자는 두 단어(`RubberDuck`, `rubber_duck`, `rubber-duck`, prompt id `rubber.duck`). 한 단어 `rubberduck`은 설정 prefix `devpilot.rubberduck.*`와 migration 파일 `V9__rubberduck_project.sql`에만 쓴다. 학습 세션(`LearningSession`)과 구분 — "세션" 단독으로 쓰지 않는다 |
| 러버덕 대상 | `RubberDuckTargetType targetType`, `targetId` | `target_type`, `target_id`(FK 없음), `concept_key` | `targetType`, `targetId`, `conceptKey`, `targetTitle` | 코드 읽기 대상 값은 `CODE_READING`, 사이드 프로젝트 대상은 `PROJECT_WORK` |
| 러버덕 상태 | `RubberDuckStatus` | `status` | `status` | 값은 `SessionStatus`와 같지만 다른 enum이다 |
| 턴 | `RubberDuckTurn`, `turnNo`, `turnCount` | `rubber_duck_turn`, `turn_no`, `turn_count` | `/turns`, `turnNo`, `turnCount`, `maxTurns`, `remainingTurns` | `round`, `step`, `message` 금지 |
| 러버덕 설명 | `explanation`(요청) → `userText` | `user_text` (마스킹본) | `explanation`, `userText` | 자기설명(`selfExplanation`)과 구분 |
| 되묻는 질문 | `question` | `ai_question` | `question` | AI의 "답"이라고 부르지 않는다 |
| 러버덕 정리 | `RubberDuckSummaryOutput`, `summary` | `summary_json` | `summary`, `summarySkippedReason` | 코치 리뷰·피드백과 혼동되므로 `review`, `feedback` 금지 |
| 막힌 지점 | `RubberDuckGap`, `gaps`, `whatWasMissed`, `whyItMatters`, `reviewQuestion` | (`summary_json` 안) | `gaps[]`, `gapCount`(이벤트 payload) | 필드 이름은 `17` §4.11 출력 스키마가 기준이다. `weakness`, `mistake` 금지 |
| 제대로 설명한 것 | `confirmed` | (`summary_json` 안) | `confirmed[]` | |
| 막힘 신호 | `learnerStuck`(턴 — 서버 판정, `RubberDuckPolicy`), `suggestHint`(세션·응답) | `learner_stuck` | `learnerStuck`, `suggestHint` | RD-3. `stuck` 단독, `giveUp` 금지 |
| AI가 겨냥한 빈틈 | `targetsGap` | 저장 안 함 | 비노출 | 다음 턴 prompt에도 넣지 않는다 |
| 러버덕 완료 이벤트 | `LearningEventType.RUBBER_DUCK_COMPLETED`, `EventSourceType.RUBBER_DUCK_SESSION` | `event_type`, `source_type` | — | payload `{ sessionId, turns, gapCount, targetType, hintDisclosed }` |
| 교차 학습 | `DueReviewSelector`의 재배치 단계 (`RV-INTERLEAVE`) | — | — | `shuffle`, `randomize` 금지 — 무작위가 아닌 결정적 재배치다 |
| 학습 이벤트 | `LearningEvent`, `LearningEventType` | `learning_event` | (export만) | `ActivityLog` 금지 |
| 중복 방지 키 | `dedupeKey` | `dedupe_key` | — | 멱등 키(`idempotencyKey`)와 구분 |
| 이벤트 무효화 | `invalidatedAt` | `invalidated_at` | — | 삭제 대신 사용 |
| skill | `Skill`, `skillId`, `skillCode` | `skill`, `code` | `skillId`, `skillCode` | code 형식 `CATEGORY.NAME` |
| 선수 skill | `SkillPrerequisite` | `skill_prerequisite` | `prerequisites` | |
| skill 카테고리 | `SkillCategory category` | `category` | `category` | 13개 |
| skill 상태 | `UserSkillState` | `user_skill_state` | `/skills/me` | `SkillScore` 금지 |
| skill 축 | `SkillAxis axis` | `axis` | `axis` | 사고 축(`ThinkingAxis`)과 구분 |
| 증거 레벨 | `knowledgeLevel` 등 4축 | `knowledge_level` 등 | `evidenceLevel` | |
| 계획용 레벨 | `PlanningLevelPolicy`, `planningLevel` | — | `planningLevel` | 저장하지 않음 |
| 자기평가 | `SelfAssessmentPropagation`, `selfAssessedLevel` | `self_assessed_level` | `selfAssessments[]`, `selfAssessedLevel` | `selfRating`(복습 자기평가)과 구분 |
| 자기평가 유효 여부 | `selfAssessmentActive` | `self_assessment_active` | `selfAssessmentActive` | |
| 레벨 변경 이력 | `SkillStateChange` | `skill_state_change` | `/skills/{skillId}/history` | |
| 규칙 코드 | `ruleCode` | `rule_code` | `ruleCode` | |
| 복습 항목 | `ReviewItem` | `review_item` | `/review-items`, `reviewItem` | `Card`, `Flashcard` 금지 |
| 복습 응답 | `ReviewAnswer` | `review_answer` | `/reviews/{reviewItemId}/answer` | |
| 개념 키 | `conceptKey` | `concept_key` | `conceptKey` | |
| 복습 유형 | `ReviewType reviewType` | `review_type` | `reviewType` | |
| 복습 자기평가 | `ReviewRating selfRating` | `self_rating` | `selfRating` | |
| 최종 등급 | `finalRating` | `final_rating` | `finalRating` | |
| 등급 조정 사유 | `RatingAdjustment`, `adjustedBy` | `adjusted_by` | `adjustedBy` | |
| 간격 | `intervalDays` | `interval_days` | `intervalBefore`, `intervalAfter` | |
| 복습 예정 시각 | `dueAt` | `due_at` | `dueAt` | |
| 연속 실패 | `consecutiveFailures` | `consecutive_failures` | `consecutiveFailures` | |
| 변형 문항 | `variantStatus`, `variantPrompt` | `variant_status`, `variant_prompt` | `wasVariant` | |
| 챌린지 | `Challenge` | `challenge` | `/challenges` | `Quiz`, `Problem` 금지 |
| 문제 목적 | `ChallengePurpose purpose` | `purpose` | `purpose` | |
| 시도 | `ChallengeAttempt` | `challenge_attempt` | `/challenge-attempts`, `attemptId` | |
| 제출 | `ChallengeSubmission`, `submissionNo` | `challenge_submission`, `submission_no` | `submissions[]`, `submissionNo` | |
| 자기설명 | `selfExplanation`, `selfExplanationSkipped` | `self_explanation` | `/self-explanation` | 자기 리뷰(`userSelfReview`)와 구분 |
| 채점 기준 | `rubric`, `RubricAxis` | `rubric_json` | `rubric` | |
| 기준 충족률 | `rubricCoverageBp`, `explanationCoverageBp` | `rubric_coverage_bp` | `rubricCoverageBp` | |
| 평가 결과 | `EvaluatedOutcome evaluatedOutcome` | `evaluated_outcome` | `evaluatedOutcome` | |
| 시도 결과 | `AttemptOutcome outcome` | `outcome` | `outcome` | |
| 전이 문제 | `isTransfer`, `transferTargets` | `is_transfer`, `transfer_targets_json` | `isTransfer` | |
| hint 단계 | `HintLevel hintLevel` | `hint_level` | `hintLevel`, `requestedLevel` | `assistLevel`, `hintStep` 금지 |
| 최대 hint 단계 | `maxHintLevel` | `max_hint_level` | `maxHintLevel` | |
| hint 공개 기록 | `HintDisclosure` | `hint_disclosure` | `/hints` | |
| 증거 영향 확인 | `acknowledgeEvidenceImpact` | — | `acknowledgeEvidenceImpact` | |
| 코치 리뷰 | `CoachReview` | `coach_review` | `/coach/reviews`, `reviewId` | 복습(`ReviewItem`)과 구분: 항상 `coach` 접두 |
| 발견 사항 | `CoachFinding`, `FindingType` | `coach_finding` | `findings[]`, `findingId` | `Issue`, `Comment` 금지 |
| 자기 리뷰 | `userSelfReview`, `selfReviewAxes` | `user_self_review`, `self_review_axes` | `userSelfReview` | |
| 사용자 응답(finding) | `userResponse` | `user_response` | `/responses`, `text` | |
| AI 피드백 | `aiFeedback`, `userIdentifiedIssue` | `ai_feedback`, `user_identified_issue` | `aiFeedback` | |
| 발견 방식 | `DiscoveredBy discoveredBy` | `discovered_by` | `discoveredBy` | |
| 사고 축 | `ThinkingAxis category / axis` | `category`, `axis` | `category` | |
| 사고 패턴 관찰 | `ThinkingPatternObservation` | `thinking_pattern_observation` | `/thinking-patterns/trend` | |
| 검증 상태 | `VerificationStatus verificationStatus` | `verification_status` | `verificationStatus` | |
| 확신도 | `Confidence confidence` | `confidence` | `confidence` | |
| 근거 유형 | `VerificationSourceType sourceType` | `source_type` | `sourceType` | |
| 근거 참조 (finding) | `sourceReference` | `source_reference` | `sourceReference` | **evidence라고 부르지 않는다** |
| 기밀 동의 | `confidentialConsent` | `confidential_consent` | `confidentialConsent` | |
| 가린 비밀값 수 | `maskedSecretCount` | `masked_secret_count` | `maskedSecretCount` | |
| 원문 보존 기한 | `contentRetentionUntil`, `contentPurgedAt` | `content_retention_until` | `contentPurgedAt` | |
| 증거 | `EvidenceCandidate` | `evidence_candidate` | `/evidence` | 레벨 근거 이벤트(`evidenceEventIds`)와 구분 |
| 근거 이벤트 ID | `evidenceEventIds` | `evidence_event_ids` | `evidenceEvents` | skill 변경의 근거 learning event |
| 평가 인용 | `evidenceQuote` | (`evaluation_json` 내부) | `evidenceQuote` | 사용자 답변에서 인용한 문장 |
| AI 초안 | `aiDraft` | `ai_draft_json` | `aiDraft` | 사용자 확정본과 분리 |
| 설명 주제 | `explanationTopics` | `explanation_topics` | `explanationTopics` | 이 사례로 스스로 설명해 볼 주제 |
| 주간 리뷰 | `WeeklyReview`, `weekStartDate` | `weekly_review`, `week_start_date` | `/weekly-reviews/{weekStartDate}` | |
| 주간 회고 | `reflection` | `reflection` | `/reflection` | |
| 지표 | `MetricsCalculator`, `metrics` | `metrics_json` | `metrics` | |
| 요구사항 문서 | `RequirementDoc`, 모듈 `com.devpilot.radar` | `requirement_doc`, `source_text` | `/requirement-docs`, `requirementDocId`, `sourceText` | 붙여넣은 요구사항 목록 원문 1건. 추출된 항목(`RequirementItem`)과 구분 |
| 요구사항 항목 | `RequirementItem`, `RequirementType` | `requirement_item` | `requirements[]` | |
| 준비 상태 분류 | `RequirementFitCategory fitCategory`, `RequirementFitClassifier` | `fit_category` | `fitCategory` | `score`, `matchRate` 금지 |
| AI 호출 기록 | `AiCallLog` | `ai_call_log` | (비노출) | |
| AI 작업 종류 | `AiOperation operation` | `operation` | — | |
| AI 상태·사용량 | `AiStatus aiStatus`, `aiUsage` | — | `aiStatus`, `aiUsage` | |
| 비용 | `costMicroUsd` | `cost_micro_usd` | `monthCostUsd`(문자열) | |
| 비동기 작업 상태 | `AsyncJobStatus` | `status`, `generation_status`, `evaluation_status`, `analysis_status` | 같은 이름 | |
| 실패 코드 | `AsyncFailureCode failureCode` | `failure_code` | `failureCode` | HTTP 오류 `code`와 구분 |
| 멱등 키 | `idempotencyKey`, `IdempotencyService` | `idempotency_record` | 헤더 `Idempotency-Key` | |
| 추적 ID | `traceId` | — | `traceId`, 헤더 `X-Trace-Id` | |
| 오류 코드 | `ErrorCode` | — | `code` | |
| 캘린더 토큰 | `CalendarTokenService`, `calendarTokenHash` | `calendar_token_hash` | `/me/calendar-token` | 평문 토큰은 저장·로그 금지 |
| 계정 삭제 요청 | `UserStatus.DELETION_REQUESTED`, `deletionRequestedAt` | `deletion_requested_at` | `DELETE /me` | |
| seed 콘텐츠 | `ContentSeeder`, `catalogVersion`, `seedKey` | `catalog_version`, `seed_key` | — | |

---

## 7. 금지 식별자 예시

| 금지 | 이유 | 대신 |
|---|---|---|
| `복습항목`, `계획` 등 한글 식별자 | ADR-010 | 사전의 영어 이름 |
| `sawon`, `hoesa`, `gyoyuk`, `bokseup`, `gyehoek`, `mokpyo` | 로마자 한국어(Checkstyle 경고 사전) | `employee`, `company`, `education`, `review`, `plan`, `goal` |
| `data`, `info`, `tmp`, `obj`, `result1`, `list2` | 의미 없음 | 도메인 이름 (`dueReviewItems`, `budgetResult`) |
| `Card`, `Flashcard`, `Quiz` | 사전 이름과 다른 동의어 | `ReviewItem`, `Challenge` |
| `Review` 단독 클래스 | 복습과 코치 리뷰 혼동 | `ReviewItem` / `CoachReview` / `WeeklyReview` |
| `Plan` 단독 클래스, `plan` 필드가 daily plan을 가리킴 | 학습 계획과 일일 계획 혼동 | `LearningPlan` / `DailyPlan` |
| `version`으로 계획 버전 표현 | JPA `@Version`과 충돌 | `planVersion` |
| `date`, `day` 단독 필드 | 달력 날짜·plan-day 혼동 | `planDate`, `weekStartDate`, `snapshotDate` |
| `score`, `ratio`, `rate`를 `double`로 | ADR-020 | `…Bp`, `…Micro`, `…Milli` 정수 |
| `evidence`로 finding 근거 표현 | 증거(`EvidenceCandidate`)와 혼동 | `sourceReference`, `sourceType` |
| `level` 단독 필드 | 축·hint·자기평가 혼동 | `knowledgeLevel`, `hintLevel`, `selfAssessedLevel` |
| `assistLevel`, `hintStep`, `helpLevel` | 사전 이름과 다름 | `hintLevel`, `maxHintLevel` |
| `isDeleted`, `deleted` 플래그 | 삭제 정책은 cascade·purge·비활성 | `active`, `contentPurgedAt`, `invalidatedAt` |
| `userId`를 요청 body·query로 받는 필드 | 사용자 식별은 JWT만 | `CurrentUser` |
| `Manager`, `Helper`, `Util` 접미사 클래스 | 책임 불명확 | `…Service`, `…Policy`, `…Calculator` |
| `Rubberduck…`, `rubberduck` (Java·DB·API·Dart 식별자), `Duck` 단독 | 사전 이름과 다름 | `RubberDuck…`, `rubber_duck_…`, `/rubber-duck` (예외: 모듈·패키지 `rubberduck`(`com.devpilot.rubberduck`, Java 패키지 규칙), 설정 `devpilot.rubberduck.*`, migration `V9__rubberduck_project.sql`) |
| `sideproject`, `Sideproject`, `Project` 단독 클래스 | Project Coach·`CoachProjectType`·`PROJECT_TASK`와 혼동 | `SideProject`, `side_project`, `/side-projects` |
| 과제 유형에 `CODE_READING`, 러버덕 대상에 `READ_CODE` | 두 enum 값 혼동 | `TaskType.READ_CODE` / `RubberDuckTargetType.CODE_READING` |
| 복습 순서에 `shuffle`, `Random` | RV-INTERLEAVE는 결정적이어야 한다(`06` §6.5) | `DueReviewSelector`의 재배치 |
| `LocalDate.now()`, `Instant.now()` 무인자 | ADR-017 | 주입된 `Clock` |
