# 06. Learning Engine Rules

> Status: Accepted (v3) · Last updated: 2026-09-19 · Related: ADR-008, ADR-009, ADR-017, ADR-018, ADR-039, `04-domain-model-and-db.md`, `19-content-spec.md`
>
> Planner, study budget, deadline risk(축소·확장 양방향), 복습 스케줄(교차 학습 포함), skill 레벨 갱신, attempt 판정, Hint Ladder, 러버덕·코드 읽기, verification guard, 계획 버전, 지표의 **결정적 규칙**이다. 이 문서의 모든 규칙은 AI 없이 동작한다 — 단, 러버덕(§9.5)과 `READ_CODE` 제안(§5.3)은 AI가 있어야 하므로 `aiStatus`가 불가하면 제안하지 않는다.
>
> - 숫자(가중치, 임계값, 간격)는 **초기 기본값**이다. 코드에 하드코딩하지 않고 `devpilot.*` 설정으로 둔다(`03-system-architecture.md` §9). 4주 사용 후 조정한다.
> - 규칙을 구현하는 클래스는 Spring/JPA에 의존하지 않는 순수 Java다. 이름은 `03` §3.2를 따른다.
> - **Test vectors는 그대로 `@ParameterizedTest` 케이스로 옮긴다.** 모든 vector를 통과해야 완료다.

---

## 1. 수치 규약 (결정성)

| 규약 | 내용 |
|---|---|
| N-1 | **부동소수점(`double`, `float`)을 규칙 계산에 쓰지 않는다.** 정수(`long`)만 쓴다 |
| N-2 | 비율은 basis point(bp): `1.0 = 10_000` |
| N-3 | 점수와 factor는 micro: `1.0 = 1_000_000` |
| N-4 | 나눗셈은 명시한 함수만 쓴다: `floorDiv(a, b)`, `ceilDiv(a, b)`, `roundHalfUpDiv(a, b) = floorDiv(2a + b, 2b)` (a, b ≥ 0) |
| N-5 | 곱셈 modifier 적용: `score = floorDiv(score × multiplierBp, 10_000)` |
| N-6 | 설정의 소수 값(예: `0.25`)은 기동 시 bp/micro 정수로 변환한다. 변환 결과가 정수가 아니면 기동 실패 |
| N-7 | 오버플로 방지를 위해 중간값은 `long`, 곱셈은 `Math.multiplyExact` |

---

## 2. Plan-day (날짜 경계)

```text
planDate(now, zone, dayStartHour)  = now.atZone(zone).toLocalDateTime().minusHours(dayStartHour).toLocalDate()   # 벽시계 기준
planDayStart(date, zone, dayStartHour) = date.atTime(dayStartHour, 0).atZone(zone).toInstant()
```

- 모든 "오늘"은 plan-day다. 서버는 주입된 `Clock`만 사용한다.
- **벽시계 기준**이다: 현지 시각이 `dayStartHour`시 이전이면 전날이다. DST가 있는 timezone에서도 이 정의를 따른다(instant 기준 `ZonedDateTime.minusHours`를 쓰지 않는다). `planDayStart`에서 현지 시각이 DST로 건너뛰어 존재하지 않으면 `atZone`의 기본 규칙(뒤로 밀림)을 따른다.
- `daysBetween(a, b)` = `ChronoUnit.DAYS.between(a, b)` (LocalDate 기준)

**Test vectors** (`Asia/Seoul`, dayStartHour=4)

| now (KST) | planDate | planDayStart(planDate) (UTC) |
|---|---|---|
| 2026-10-05 23:30 | 2026-10-05 | 2026-10-04T19:00:00Z |
| 2026-10-06 00:40 | 2026-10-05 | 2026-10-04T19:00:00Z |
| 2026-10-06 03:59 | 2026-10-05 | 2026-10-04T19:00:00Z |
| 2026-10-06 04:00 | 2026-10-06 | 2026-10-05T19:00:00Z |

DST vector (`America/New_York`, dayStartHour=4, 2026-03-08 02:00 EST→03:00 EDT 전환일): now = 2026-03-08T08:30:00Z (현지 04:30 EDT) → planDate **2026-03-08** (instant 기준으로 계산하면 03-07이 되므로 구분된다)

---

## 3. Study Budget (`StudyBudgetCalculator`)

### 3.1 Horizon

```text
horizonDate = targetCompletionDate        # 학습 목표의 목표일 (learning_goal.target_completion_date)
```

학습 목표의 날짜는 목표일 하나다(ADR-039). 계획 템플릿 배치의 창 끝도 같은 날이다(`19` §5.2).

### 3.2 Nominal budget

```text
nominalMinutes = Σ_{d = today .. horizonDate − 1} (d is SAT/SUN ? weekendStudyMinutes : weekdayStudyMinutes)
horizonDate ≤ today → nominalMinutes = 0
```
오늘도 포함한다. 공휴일은 반영하지 않는다(Later).

### 3.3 Completion rate

```text
window = plan-day [today − 28, today − 1]
days   = window 안에서 daily_plan이 존재하는 plan-day 수
Σavail = 그 날들의 daily_plan.available_minutes 합
Σactual = window 안 COMPLETED learning_session.actual_minutes 합 (plan_date 기준)

if days < 14 or Σavail == 0 → completionRateBp = 7_000
else completionRateBp = clamp(floorDiv(Σactual × 10_000, Σavail), 3_000, 10_000)

effectiveMinutes = floorDiv(nominalMinutes × completionRateBp, 10_000)
```

**Test vectors**

| 조건 | 결과 |
|---|---|
| 평일 45 / 주말 240, today=2026-10-05(월), horizon=2026-10-12, 기록 없음 | nominal 705, rate 7000, effective **493** |
| nominal 90, 기록 없음 | effective **63** |
| days 20, Σavail 1500, Σactual 600 | rate **4000** |
| days 20, Σavail 1500, Σactual 1650 | rate **10000** (clamp) |
| days 20, Σavail 1500, Σactual 300 | rate **3000** (clamp, 원값 2000) |
| days 13, Σavail 1000, Σactual 1000 | rate **7000** (기록 부족) |
| horizon = today | nominal 0, effective 0 |

---

## 4. Required Minutes · Deadline Risk (`DeadlineRiskEvaluator`)

### 4.1 대상

- 활성 plan의 `plan_skill_target` 중 `deferred=false`이고 skill이 `active=true`인 항목
- milestone의 단계(`PREPARATION`/`CONSOLIDATION`)와 무관하게 모든 MUST 항목을 horizon(목표일)까지의 필요 시간으로 계산한다. "설명과 정리" milestone에 들어 있는 MUST skill도 목표일까지 끝내야 하는 목표이기 때문이다.
- `requiredMust`: priority=MUST 합계, `requiredShould`: priority=SHOULD 합계. LATER는 계산하지 않는다.

### 4.2 Skill별 필요 시간

```text
AXIS_COST_BP   = { KNOWLEDGE: 5_000, IMPLEMENTATION: 10_000, EXPLANATION: 4_000, DEBUGGING: 8_000 }
REVIEW_OVERHEAD_BP = 11_500

weightedGap = Σ_axis max(0, target_axis − planningLevel_axis) × AXIS_COST_BP[axis]
requiredMinutes(skill) = ceilDiv(weightedGap × minutesPerLevelStep × REVIEW_OVERHEAD_BP, 100_000_000)
```
`planningLevel`은 §7.5를 따른다.

**Test vectors**

| target (K,I,E,D) | planning (K,I,E,D) | step | weightedGap | required |
|---|---|---|---|---|
| (4,4,4,3) | (2,1,3,3) | 120 | 2×5000+3×10000+1×4000 = 44,000 | ceil(607.2) = **608** |
| (3,3,3,2) | (3,3,3,2) | 150 | 0 | **0** |
| (4,4,3,3) | (0,0,0,0) | 90 | 4×5000+4×10000+3×4000+3×8000 = 96,000 | ceil(993.6) = **994** |

### 4.3 Risk level

```text
if requiredMust == 0          → LOW
else if effective == 0        → CRITICAL
else if requiredMust × 10_000 ≤ effective × 8_000   → LOW
else if requiredMust × 10_000 ≤ effective × 10_000  → MEDIUM
else if requiredMust × 10_000 ≤ effective × 12_500  → HIGH
else                                                → CRITICAL

ratioBp (저장·표시용) = effective == 0 ? null : floorDiv(requiredMust × 10_000, effective)
```

**Test vectors**

| requiredMust | effective | risk | ratioBp |
|---|---|---|---|
| 400 | 500 | LOW | 8000 |
| 450 | 500 | MEDIUM | 9000 |
| 500 | 500 | MEDIUM | 10000 |
| 600 | 500 | HIGH | 12000 |
| 625 | 500 | HIGH | 12500 |
| 700 | 500 | CRITICAL | 14000 |
| 10 | 0 | CRITICAL | null |
| 0 | 0 | LOW | null |

### 4.4 Replan 제안 (`ReplanSuggestionPolicy`, 자동 적용 금지)

입력은 사용자가 편집 중인 milestone 목록과 현재 `plan_skill_target`이다. 출력은 제안 목록이며 저장하지 않는다.

**방향 분기 (축소와 확장은 상호 배타)** — 기한 역산은 한쪽으로만 간다. 한 응답에 축소·defer 제안과 확장 제안이 **동시에 비어 있지 않은 경우는 없다.**

| risk (§4.3) | `ratioBp` | 만드는 제안 | 응답 필드 |
|---|---|---|---|
| ≥ HIGH | `!= null` | **축소** (3·4단계) | `mustTargetReductionSuggestions[]`, `deferSuggestions[]` |
| LOW | ≤ 7_000 (여유 30% 이상) | **확장** (6단계) | `expansionSuggestions[]` |
| MEDIUM, 또는 LOW이면서 `ratioBp` 7_001~8_000, 또는 `ratioBp = null` | — | **없음** | 세 배열 모두 `[]` |

`ratioBp = null`은 `effective == 0`(오늘이 horizon이거나 예산이 0)이라는 뜻이다. 나눌 수가 없으므로 확장도 축소도 제안하지 않는다. **이 판정이 먼저다** — risk가 CRITICAL이어도 `ratioBp = null`이면 세 목록 모두 `[]`다(줄일 기간 자체가 없다).

1. 편집안 기준으로 risk를 계산한다(§4.3, MUST 기준).
2. risk ≤ MEDIUM이면 축소·defer 제안(3·4단계)을 하지 않는다. risk == LOW이고 `ratioBp != null && ratioBp ≤ 7_000`이면 **6단계(확장)로 간다.**
3. **MUST 목표 축소 제안 (먼저)**: `requiredMust > effective`인 동안 MUST 항목을 `(practicalImportance ASC, skill.code ASC)` 순서로 돌면서, 각 skill에서 target과 planning의 차이가 가장 큰 축(동점이면 K, I, E, D 순)의 target을 1 낮추는 안을 하나씩 추가한다. 축소 후 `requiredMust'`가 `≤ effective`가 되거나, 모든 MUST 항목을 한 번씩 검토하면 멈춘다. 축소 후 target은 3 미만으로 내리지 않는다(이미 3 이하인 축은 건너뛴다).
4. **SHOULD defer 제안 (그다음)**: 3단계 적용 후의 `requiredMust'`를 기준으로, SHOULD 항목을 `(practicalImportance ASC, requiredMinutes DESC, skill.code ASC)` 순서로 하나씩 defer 후보에 넣는다. `requiredMust' + 남은 requiredShould ≤ effective`가 되면 멈춘다. (SHOULD를 모두 빼도 조건을 만족하지 못하면 모두 제안)
5. 응답의 risk는 **§4.3의 계산값** 그대로다. `riskAfterSuggestions`는 3단계 축소를 모두 적용한 편집안의 재계산값이다(SHOULD는 risk 계산에 들어가지 않으므로 4단계는 영향이 없다).

> 순서를 MUST 축소 → SHOULD defer로 두는 이유: risk ≥ HIGH는 `requiredMust > effective`를 뜻하므로, SHOULD defer를 먼저 돌리면 종료 조건을 만족할 수 없어 항상 SHOULD 전부가 제안된다.

6. **확장 제안 (여유가 있을 때. 3·4단계와 배타)** — 부족할 때 깎기만 하고 남을 때 아무것도 하지 않으면 기한 역산이 절반만 도는 셈이다. risk LOW이고 `ratioBp ≤ 7_000`일 때 **깊이를 더하는** 안을 만든다.

   ```text
   expandedRequired = requiredMust(6b·6c에서 확정한 상향을 반영) + Σ(복원 확정한 항목의 requiredMinutes)
   expandedRatioBp  = floorDiv(expandedRequired × 10_000, effective)
   종료 조건        = expandedRatioBp ≤ 9_000 을 유지하는 동안만 확정한다

   6a. 복원 후보: 활성 plan의 `plan_skill_target` 중 deferred = true 이고 priority ∈ {SHOULD, LATER}인 항목
       정렬: practicalImportance DESC → skill.code ASC
   6b. 앞에서부터 하나씩: 그 항목을 복원했다고 가정하고 expandedRatioBp를 다시 계산한다
       ≤ 9_000  → 확정 (kind = RESTORE_DEFERRED), 다음 항목으로
       > 9_000  → 복원 루프를 멈춘다               # 건너뛰지 않고 멈춘다 (결정적)
   6c. 복원 루프가 끝나면(소진했든 멈췄든) 목표 상향 후보를 본다:
       priority = MUST 이고 deferred = false 이고 어느 축이든 planningLevel < target 인 skill
       정렬: practicalImportance DESC → skill.code ASC
       skill 하나당 축 하나만, 최대 1회 상향한다
       올릴 축: target < 5 인 축 중 planningLevel이 가장 낮은 축 (동점이면 K, I, E, D 순)
                올릴 수 있는 축이 없으면(모든 축 target = 5) 그 skill은 건너뛴다
       상향 후 expandedRatioBp ≤ 9_000 → 확정 (kind = RAISE_TARGET), 아니면 상향 루프를 멈춘다
   6d. 레벨 상한은 5다. 같은 skill·축을 두 번 제안하지 않는다
   ```

   - 응답 필드 `expansionSuggestions[]`. 형식은 `deferSuggestions`·`mustTargetReductionSuggestions`와 같고 `kind`가 `RESTORE_DEFERRED` / `RAISE_TARGET`이다.
   - **자동 적용하지 않는다.** 제안만 하고 저장하지 않는다(3·4단계와 동일). 사용자가 받아들이면 `POST /plans/{id}/replan`의 `restoredDeferrals`(RESTORE_DEFERRED)·`acceptedTargetRaises`(RAISE_TARGET)로 들어간다(§11.2 7단계).
   - `riskAfterSuggestions`는 §4.3의 정의 그대로 **`requiredMust`만으로** 계산한다. 6b의 복원(SHOULD/LATER)은 risk 계산에 들어가지 않으므로 `riskAfterSuggestions`에 영향이 없고, 6c의 목표 상향만 반영된다. 반면 6b·6c의 종료 조건에 쓰는 `expandedRatioBp`는 복원분까지 더한 값이다 — **둘은 다른 수치이므로 섞지 않는다.**

**Test vector 1**: effective 5000, MUST required 5900(risk HIGH). MUST 축소 2건으로 `requiredMust'` = 4900. SHOULD = [A(importance 0.40, 480분), B(0.40, 900분), C(0.70, 300분)] → 4900 + 1680 > 5000 → defer 제안 순서 **B, A, C** (C까지 빼야 4900 ≤ 5000). `riskAfterSuggestions` = ratio 9800bp → MEDIUM.
**Test vector 2**: 같은 조건에서 SHOULD = [A(0.40, 80분), B(0.40, 120분)] → 4900 + 200 > 5000 → B defer → 4900 + 80 ≤ 5000 → 멈춤. 제안은 **B만**. `expansionSuggestions`는 `[]`(risk HIGH이므로 6단계에 가지 않는다).

**Test vector 3 (확장 — 복원 + 목표 상향)**: effective **5000**, requiredMust **3400**.
- risk: 3400 × 10_000 = 34,000,000 ≤ 5000 × 8_000 = 40,000,000 → **LOW**. `ratioBp` = floorDiv(34,000,000, 5000) = **6800** ≤ 7000 → 6단계로 간다.
- 6a 복원 후보(정렬 후): `X`(importance 0.80, requiredMinutes 600) → `Y`(0.65, 900) → `Z`(0.65, 1500, code가 Y보다 뒤).
- 6b `X`: expandedRequired = 3400 + 600 = 4000 → expandedRatioBp = floorDiv(40,000,000, 5000) = **8000** ≤ 9000 → **확정**.
  `Y`: 4000 + 900 = 4900 → floorDiv(49,000,000, 5000) = **9800** > 9000 → **복원 루프 종료**(`Z`는 보지 않는다).
- 6c 목표 상향 후보: `T1`(0.90, 올릴 축 +1 비용 500분) → `T2`(0.85, 400분).
  `T1`: expandedRequired = 4000 + 500 = 4500 → floorDiv(45,000,000, 5000) = **9000** ≤ 9000(경계 포함) → **확정**.
  `T2`: 4500 + 400 = 4900 → **9800** > 9000 → 상향 루프 종료.
- 제안: **`RESTORE_DEFERRED X`, `RAISE_TARGET T1`(해당 축 +1)** 2건.
- `riskAfterSuggestions`: requiredMust' = 3400 + 500 = **3900** → 39,000,000 ≤ 40,000,000 → **LOW**, `ratioBp` **7800**.

**Test vector 4 (확장 — 복원 대상 없음, 상한 5 건너뛰기)**: effective **2000**, requiredMust **1300**, deferred 항목 **없음**.
- risk: 13,000,000 ≤ 2000 × 8_000 = 16,000,000 → **LOW**. `ratioBp` = floorDiv(13,000,000, 2000) = **6500** ≤ 7000 → 6단계로 간다.
- 6b: 복원 후보가 없어 아무것도 확정하지 않는다.
- 6c 후보(정렬 후): `P`(0.90) → `Q`(0.80, 상향 비용 300분) → `R`(0.70, 300분).
  `P`: 네 축 target이 모두 5라 올릴 축이 없다 → **건너뜀**(루프를 멈추지 않는다).
  `Q`: expandedRequired = 1300 + 300 = 1600 → floorDiv(16,000,000, 2000) = **8000** ≤ 9000 → **확정**.
  `R`: 1600 + 300 = 1900 → floorDiv(19,000,000, 2000) = **9500** > 9000 → 상향 루프 종료.
- 제안: **`RAISE_TARGET Q`만** 1건. (`P` 건너뛰기와 `R` 종료는 다른 동작이다 — 상한 5는 건너뛰고, 예산 초과는 멈춘다.)
- `riskAfterSuggestions`: requiredMust' = **1600** → 16,000,000 ≤ 16,000,000 → **LOW**, `ratioBp` **8000**.

---

## 5. Daily Planner (`PlannerScoring`, `TimeAllocator`, `ReasonTemplates`)

### 5.1 입력

`today`, `availableMinutes`(5~720), `energyLevel`, 활성 plan(milestones, skill targets), planning level, due review 목록, 최근 2 plan-day의 main task, 최근 3 plan-day 세션 기록, risk level.

### 5.2 후보 skill

합집합(중복 제거):
1. 오늘이 기간에 포함된 milestone의 `milestone_skill`
2. 시작일이 오늘 이후인 가장 가까운 milestone의 `milestone_skill`
3. due review가 있는 skill
4. `plan_skill_target`에서 priority MUST/SHOULD인 skill

제외:
- `deferred=true`
- `skill.active=false` (retire된 skill)
- risk ≥ HIGH이면 priority LATER
- 모든 축에서 `planningLevel ≥ target`이고 due review가 없는 skill
- `prerequisiteReadiness < 500_000`인 skill → 대신 **준비되지 않은 prerequisite 중 planning IMPLEMENTATION이 가장 낮은 skill**(동점이면 code ASC)을 후보에 추가

후보가 하나도 없으면(모든 목표 달성 등) main task를 만들지 않는다. due review가 있으면 REVIEW task만 만들고, 응답의 `mainTask`는 `null`이다.

### 5.3 Task 제안 (`TaskProposalPolicy`)

점수를 매기기 전에 후보 skill마다 main task 제안 1개를 만든다.

```text
d = clamp(planning IMPLEMENTATION + 1, 1, 5)
if comebackMode: d = min(d, 2)

1. `aiStatus ∉ {DISABLED, BALANCE_EXHAUSTED}`이고(평가가 AI에 의존하므로), 해당 skill에 VALIDATED challenge(PRACTICE, seed 또는 본인 소유)가 있고,
   최근 14 plan-day 안에 attempt하지 않았고 **해결(outcome ∈ {SOLVED_INDEPENDENTLY, SOLVED_WITH_HINTS})한 attempt가 없는** 문제가 difficulty d, 없으면 d−1(≥1) 순서로 있으면
   → CHALLENGE (estimated = challenge.estimated_minutes, difficulty = 찾은 문제의 difficulty)
   같은 difficulty에 여러 개면 seed_key ASC(null은 뒤), 그다음 ID ASC 첫 번째
2. else if `aiStatus ∉ {DISABLED, BALANCE_EXHAUSTED}`이고(RC-1의 완료 조건이 러버덕이므로),
   **planning KNOWLEDGE ≥ 1**이고(RC-3), 해당 skill의 선택 가능한 reading이 있으면
   → READ_CODE (estimated = reading.estimatedMinutes, difficulty 2)
3. else if planning KNOWLEDGE < 2   → READING  (estimated 25, difficulty 1)
4. else if projectNeed && energy != LOW && ACTIVE 사이드 프로젝트가 있으면(SP-1) → PROJECT_TASK (estimated 30, difficulty 3)
5. else                               → EXPLAIN  (estimated 15, difficulty 2)
```

**READ_CODE의 자리 (2번)와 그 이유** — CHALLENGE는 I축·K축 상승 규칙(§7.2)의 증거를 직접 만들므로 1번 자리를 유지한다. READ_CODE를 READING보다 **앞**에 두는 것은 §12의 핵심 루프(읽는다 → 만든다 → 설명한다)를 따르기 위해서다: 무엇인지 조금이라도 아는 상태(KNOWLEDGE ≥ 1)라면 요약 글을 한 번 더 읽는 것보다 검증된 실제 코드를 읽는 편이 낫다. 아무것도 모르는 상태(KNOWLEDGE = 0)에서 코드를 읽으면 좌절하므로 RC-3이 이를 막고, 그때는 3번 READING으로 내려간다. 결과적으로 READING은 **KNOWLEDGE = 0**이거나 해당 skill에 reading 콘텐츠가 없을 때만 나온다.

**reading 선택 (결정적)**

```text
후보: content/curated-repos.yaml의 readings 중
      - retired가 true가 아니고 (은퇴한 단위는 조회만 된다, 19 §8.2)
      - skillCodes에 해당 skill code가 들어 있고
      - 그 사용자가 COMPLETED한 READ_CODE task의 reading key가 아니고
      - 최근 14 plan-day 안에 제안된 적이 없는 것
정렬: reading.key ASC              # repo key는 reading.key 안에 들어 있다
선택: 첫 번째. 후보가 비면 READ_CODE를 제안하지 않고 3번으로 내려간다
```

`READ_CODE` 완료 때 사용자가 고르는 평가(`learning_task.reading_feedback` — `HELPFUL`/`TOO_HARD`/`BORING`, 선택, `05` §8.4)는 **이 선택 규칙을 포함해 어떤 레벨·planner·budget 규칙의 입력도 아니다.** 저장만 하고, 사람이 하는 소스 점검(`19` §8.5)에서만 읽는다.

`estimatedMinutes`는 **콘텐츠의 `reading.estimatedMinutes`를 그대로 쓴다**(서버가 다시 계산하지 않는다). difficulty는 2로 고정한다 — 범위가 고정되어 있어 §5.5의 `LOW_ENERGY_DEEP_TASK`(difficulty ≥ 4)에도, `HIGH_ENERGY_HARD_TASK`(difficulty ≥ 3)에도 걸리지 않는다.

task 제목 템플릿:

| TaskType | title | description |
|---|---|---|
| CHALLENGE | `{challenge.title}` | challenge scenario 요약 (앞 200자) |
| READ_CODE | `{repo.name} 읽기 — {path의 파일명} {lines[0]}~{lines[1]}줄` | `reading.question` + 줄바꿈 + "읽고 나서 러버덕으로 설명하면 완료입니다." (저장소가 로컬에 없으면 화면이 `cloneHint`를 먼저 보여준다 — RC-4) |
| READING | `{skill.name} 핵심 개념 정리` | `skill.description` + "공식 문서를 읽고 핵심 3가지를 스스로 적어 보세요." |
| PROJECT_TASK | `{사이드 프로젝트 이름}에 {skill.name} 적용하기` | "{프로젝트}에서 이 개념을 적용할 지점을 찾아 구현하고 이유를 적어 보세요." (SP-2. 등록된 `ACTIVE` 프로젝트가 없으면 PROJECT_TASK를 제안하지 않는다 — SP-1·SP-3) |
| EXPLAIN | `{skill.name} 내 말로 설명하기` | `skill.description` + "5문장 이내로 설명하고 예시를 하나 드세요." |
| RECALL | `{skill.name} 5분 떠올리기` | "자료를 보지 않고 기억나는 내용을 적어 보세요." |
| REVIEW | `복습 {n}장` | — |

**Test vectors (제안 분기)** — 공통: energy NORMAL, comebackMode 아님, 해당 skill에 조건을 만족하는 CHALLENGE 없음.

| # | planning (K,I) | aiStatus | 선택 가능한 reading | projectNeed | 결과 |
|---|---|---|---|---|---|
| T-1 | (0,0) | ENABLED | 있음 | N | **READING** (25, d1) — RC-3이 READ_CODE를 막는다 |
| T-2 | (1,1) | ENABLED | `READ.PETCLINIC.CONTROLLER_SLICE.001` (15분) | N | **READ_CODE**, estimated **15**, difficulty **2** |
| T-3 | (1,1) | DISABLED | 있음 | N | **READING** (25, d1) — AI 불가라 CHALLENGE·READ_CODE 모두 제외, KNOWLEDGE < 2 |
| T-4 | (3,3) | ENABLED | 없음(전부 완료) | Y | **PROJECT_TASK** (30, d3) |
| T-5 | (3,3) | ENABLED | 2개(`READ.RESTBUCKS.AGGREGATE.001`, `READ.RESTBUCKS.STATE_TRANSITION.001`) | Y | **READ_CODE** — key ASC로 `READ.RESTBUCKS.AGGREGATE.001`, estimated **15**, difficulty 2 |

### 5.4 Factor (micro, 0 ~ 1_000_000)

| factor | 계산 |
|---|---|
| `practicalImportance` | `plan_skill_target.practical_importance × 1_000_000` (target 없으면 `300_000`) |
| `skillGap` | `Σtarget == 0 ? 0 : floorDiv(Σ_axis max(0, target − planning) × 1_000_000, Σ_axis target)` |
| `reviewUrgency` | due 없음 → 0 / 있음 → `min(1_000_000, 300_000 + 100_000 × maxOverdueDays)`, `overdueDays = daysBetween(planDate(due_at), today)` (≥0) |
| `milestoneUrgency` | 현재 milestone skill: `length = daysBetween(start, end) + 1`, `daysLeft = daysBetween(today, end)`, `max(200_000, 1_000_000 − floorDiv(daysLeft × 1_000_000, length))` / 다음 milestone skill: `100_000` / 그 외 0 (여러 milestone이면 최댓값) |
| `projectNeed` | 학습 목표의 focus skill → `1_000_000`, 아니면 0 |
| `prerequisiteReadiness` | prerequisite 없음 → `1_000_000` / 있음 → `floorDiv(count(planning IMPLEMENTATION ≥ 2) × 1_000_000, count)` |

```text
WEIGHT_BP = { practicalImportance: 2500, skillGap: 2000, reviewUrgency: 2000,
              milestoneUrgency: 1500, projectNeed: 1000, prerequisiteReadiness: 1000 }
baseScore = floorDiv(Σ factor × WEIGHT_BP, 10_000)
```

### 5.5 Modifier (이 순서로 적용)

| 순서 | 조건 | multiplierBp | modifier code |
|---|---|---|---|
| 1 | risk ≥ HIGH, priority=MUST | 12_000 | `RISK_HIGH_MUST` |
| 1 | risk ≥ HIGH, priority=SHOULD | 8_000 | `RISK_HIGH_SHOULD` |
| 2 | energy=LOW, 제안 estimated > 30 또는 difficulty ≥ 4 | 7_000 | `LOW_ENERGY_DEEP_TASK` |
| 2 | energy=HIGH, difficulty ≥ 3 | 11_000 | `HIGH_ENERGY_HARD_TASK` |
| 3a | 어제 main task가 같은 skill이고 상태가 IN_PROGRESS 또는 DEFERRED | 11_500 | `CONTINUATION` (3b 적용 안 함) |
| 3b | 어제와 그제 main task가 모두 같은 skill (상태 무관) | 6_000 | `FATIGUE_TWO_DAYS` |
| 3b | 어제 main task만 같은 skill | 8_000 | `FATIGUE_ONE_DAY` |
| 4 | comebackMode, difficulty ≥ 3 | 7_000 | `COMEBACK_HARD_TASK` |

`comebackMode = (최근 3 plan-day(today−3 ~ today−1)에 COMPLETED 세션 없음) && (그 이전에 COMPLETED 세션이 1개 이상 있음)`

**동점 처리:** `finalScore DESC → practicalImportance DESC → last_practiced_at ASC (null 먼저) → skill.code ASC`

### 5.6 시간 배분

```text
cap = comebackMode ? 10 : 20                        # devpilot.review.max-per-day / comeback-max-per-day
dueCount = min(due review 수, cap)
reviewMinutes = dueCount == 0 ? 0
              : min( ceilDiv(dueCount × 3, 2), max(5, floorDiv(available × 2_500, 10_000)), available − 5 )
reviewMinutes = max(0, reviewMinutes)
mainBudget = available − reviewMinutes              # 항상 ≥ 5
limit = floorDiv(mainBudget × 11_000, 10_000)       # 초과 허용 10%

선택된 후보의 제안 estimated > limit 이면:
  CHALLENGE → 같은 skill에서 difficulty를 1씩 낮춰 estimated ≤ limit인 challenge 탐색
  READ_CODE → 줄 범위가 고정이라 줄일 수 없다(RC-2). 같은 skill의 다음 reading을 key ASC로
              이어 보며 estimated ≤ limit인 것을 찾는다
  없으면 EXPLAIN(15)이 limit 이하면 EXPLAIN, 아니면 RECALL(estimated = min(10, mainBudget))
mainBudget < 10 이면 제안과 무관하게 RECALL(estimated = mainBudget)
```

- 점수 계산 전에 시간 조정을 하지 않는다. 먼저 1위 후보를 고른 뒤 그 후보의 task만 조정한다.
- `reviewMinutes ≥ 1`이면 REVIEW task 1개를 만든다(`is_main=false`, `estimated=reviewMinutes`, `sort_order=0`). `reviewMinutes = 0`이면(예: available 5) due가 있어도 REVIEW task를 만들지 않는다(`learning_task.estimated_minutes`는 1 이상). main은 `sort_order=1`.

**Test vectors**

| available | due | comeback | reviewMinutes | mainBudget | limit |
|---|---|---|---|---|---|
| 30 | 3 | N | min(5, 7, 25) = **5** | 25 | 27 |
| 45 | 0 | N | **0** | 45 | 49 |
| 15 | 12 | N | min(18, 5, 10) = **5** | 10 | 11 |
| 5 | 4 | N | min(6, 5, 0) = **0** | 5 | 5 → RECALL 5 |
| 120 | 40 | N | dueCount 20 → min(30, 30, 115) = **30** | 90 | 99 |
| 60 | 40 | Y | dueCount 10 → min(15, 15, 55) = **15** | 45 | 49 |

### 5.7 Planner 점수 Test vectors

공통: risk LOW, energy NORMAL, today=2026-10-10.
- **A**: MUST, importance 0.90, skillGap 600_000, due 없음, 현재 milestone(2026-10-01~2026-10-20 → daysLeft 10, length 20 → milestoneUrgency 500_000), focus 아님, prerequisite 없음, 제안 EXPLAIN(15, d2)
- **B**: SHOULD, importance 0.50, skillGap 800_000, 2일 overdue review(reviewUrgency 500_000), milestone 밖, prerequisite 없음, 제안 CHALLENGE(20, d2)

| # | 조건 | A | B | 선택 |
|---|---|---|---|---|
| 1 | 기본 | 520_000 | 485_000 | **A** |
| 2 | risk HIGH | 624_000 | 388_000 | **A** |
| 3 | A가 어제·그제 main (둘 다 COMPLETED) | 312_000 | 485_000 | **B** |
| 4 | A가 어제 main, IN_PROGRESS | 598_000 | 485_000 | **A** |
| 5 | A가 어제 main만 COMPLETED | 416_000 | 485_000 | **B** |
| 6 | energy LOW, B 제안 CHALLENGE(40분, d2) | 520_000 | 339_500 | **A** |

### 5.8 Reason (`ReasonTemplates`)

1. `factor × WEIGHT_BP` 기여도가 큰 순서로 아래 조건을 만족하는 reason을 최대 3개 고른다.
2. modifier·task reason(`DEADLINE_RISK_MUST`, `CONTINUE_YESTERDAY`, `LOW_ENERGY_LIGHT_TASK`, `COMEBACK_EASY_START`, `READ_REAL_CODE`)은 해당 조건이 맞으면 추가한다. 전체는 최대 3개이고, 이쪽을 우선한다.
3. 조건을 만족하는 것이 하나도 없으면 기여도 1위 factor의 code를 조건 없이 넣는다(**최소 1개 보장**, AC-02).

| ReasonCode | 조건 | 문구 |
|---|---|---|
| `MILESTONE_CORE` | 현재 milestone skill | `{milestoneTitle} milestone 핵심 항목` |
| `MILESTONE_NEXT` | 다음 milestone skill | `다음 milestone({milestoneTitle}) 준비` |
| `HIGH_PRACTICAL_IMPORTANCE` | practicalImportance ≥ 700_000 | `실무에서 중요도가 높은 기술` |
| `LARGE_SKILL_GAP` | skillGap ≥ 400_000 | `목표 수준과 차이가 큼 (구현 {planningImplementation}/{targetImplementation})` |
| `REVIEW_OVERDUE` | overdue ≥ 1일 | `복습이 {overdueDays}일 밀림` |
| `RECENT_RECALL_FAILURE` | 최근 7 plan-day 안에 해당 skill review AGAIN | `최근 복습에서 기억이 흔들림` |
| `PROJECT_FOCUS` | projectNeed | `현재 프로젝트에 필요` |
| `READ_REAL_CODE` | 선택된 task가 READ_CODE | `{repo.name}에서 같은 문제를 어떻게 풀었는지 먼저 봅니다` |
| `DEADLINE_RISK_MUST` | RISK_HIGH_MUST 적용 | `마감 위험이 높아 필수 항목 우선` |
| `CONTINUE_YESTERDAY` | CONTINUATION 적용 | `어제 하던 과제 이어하기` |
| `LOW_ENERGY_LIGHT_TASK` | energy=LOW이고 선택 task estimated ≤ 30 | `컨디션에 맞춘 가벼운 과제` |
| `COMEBACK_EASY_START` | comebackMode | `다시 시작하기 좋은 쉬운 과제` |

변수 값은 선택 시점에 `score_breakdown.reasonParams`에 저장한다(`04-domain-model-and-db.md` §5.1). `overdueDays`는 상한 없이 실제 값을 저장한다.

### 5.9 재생성 (`POST /today/generate`)

| 오늘 active main 상태 | force=false | force=true |
|---|---|---|
| daily_plan 없음 | 새로 생성 | 새로 생성 |
| main `PLANNED` 또는 `SKIPPED` (active main 없음 포함) | 같은 daily_plan의 PLANNED task 삭제 → **flush** → 새 task INSERT, `generation_count+1`, 입력값 갱신 | 동일 |
| main `IN_PROGRESS` | `409 TODAY_ALREADY_STARTED` | 기존 main `DEFERRED` → PLANNED 상태 REVIEW task 삭제 → flush → 새 main·REVIEW task INSERT (REVIEW task가 IN_PROGRESS/COMPLETED면 유지하고 새로 만들지 않음) |
| main `COMPLETED` (active main 없음) | `409 TODAY_ALREADY_COMPLETED` | 추가 main INSERT (COMPLETED 유지) |

행 판정 순서(한 daily_plan에 main task가 여러 개일 수 있으므로): `IN_PROGRESS` main이 하나라도 있으면 3행 → 없고 `COMPLETED` main이 하나라도 있으면 4행 → 그 외(PLANNED·SKIPPED·DEFERRED만 있거나 main 없음)는 2행.

---

## 6. Review Scheduling (`FinalRatingPolicy`, `RuleBasedV1Scheduler`, `DueReviewSelector`)

### 6.0 무엇을 근거로 이렇게 정했나

아래 규칙들은 임의로 정한 것이 아니라 **반복 검증된 학습 연구 결과**에 맞춰 놓은 것이다. 어느 결과가 어느 규칙·절에 들어가 있는지 여기에 적는다.

| 학습 원리 | 이 문서에서 구현된 곳 | 어떤 형태로 |
|---|---|---|
| **간격 효과 (spacing)** | §6.2 간격 계산, §6.3 신규 항목 첫 due | 맞히면 간격을 2~3배로 늘려 다음 인출을 미룬다. 온보딩 카드도 하루 5장씩 나눠 첫 due를 분산한다 |
| **인출 연습 (retrieval practice / testing effect)** | §6.1 최종 등급, §9.1 HL-1·HL-2·HL-4, §5.3 RECALL·EXPLAIN task | 자료를 다시 보는 대신 먼저 떠올려 답하게 한다. 답을 보기 전에 힌트를 받으면 `HINT_CAP_*`으로 등급 상한을 걸어 "혼자 떠올린 것"으로 세지 않는다 |
| **교차 학습 (interleaving)** | §6.5 `RV-INTERLEAVE`, §5.5 `FATIGUE_ONE_DAY`·`FATIGUE_TWO_DAYS` | 같은 skill 카드가 3장 연속 나오지 않도록 출제 순서를 재배치한다. 같은 skill이 이틀 연속 main task였으면 점수를 낮춘다 |
| **생성 효과 (generation)** | §5.3 EXPLAIN·PROJECT_TASK·READ_CODE 제안, §9.5 RD-1 | 정답을 받기 전에 스스로 답·설명·구현을 만들어 보게 한다. 러버덕에서 AI는 답을 주지 않고 되묻기만 한다 |
| **정교화 (elaboration)** | §7.2 EXPLANATION 축 규칙(E2~E5), §8.1 `explanationCoverageBp`, §9.5 RD-2 | "왜 그런가"를 어디까지 설명했는지를 coverage로 재서 레벨 증거로 쓴다. 되묻는 질문은 설명의 빈틈과 틀린 전제를 겨냥한다 |
| **적정 난이도 (desirable difficulty)** | §5.3 `d = planning IMPLEMENTATION + 1`, §5.5 `LOW_ENERGY_DEEP_TASK`·`HIGH_ENERGY_HARD_TASK`·`COMEBACK_HARD_TASK`, §6.4 leech | 지금 수준보다 한 단계 위를 낸다. 다만 컨디션이 낮거나 오래 쉬고 돌아온 날은 난이도를 낮추고, 4회 연속 실패한 카드는 중단해서 좌절을 막는다 |

읽는 법:

- 연구 결과가 지지하는 것은 **방향**(간격을 늘린다, 힌트를 본 인출은 덜 세어 준다, 같은 것을 몰아 내지 않는다)이지 **구체적인 수치**가 아니다. `HARD ×1.2`, `EASY ×3`, `cap 20` 같은 값은 §1의 정수 규약을 지키는 **초기 기본값**이고 `devpilot.*` 설정으로 둔다. 4주 사용 후 실제 기록으로 조정한다.
- 근거가 약하거나 반복 실험에서 지지되지 않은 주장(학습 유형론 — 시각형/청각형에 맞춰 자료 형식을 바꾸면 더 잘 배운다는 주장 등)은 **규칙에 넣지 않는다.** 제안 분기(§5.3)나 복습 순서(§6.5)가 사용자의 "학습 유형"을 입력으로 받지 않는 것은 이 때문이다.
- 규칙이 원리와 어긋나게 바뀌면 이 표의 해당 행도 같이 고친다.

### 6.1 최종 등급

순서: `AGAIN(0) < HARD(1) < GOOD(2) < EASY(3)`

```text
final = selfRating; adjustedBy = []
if evaluatedOutcome == INCORRECT          → final = AGAIN;          adjustedBy += EVALUATED_INCORRECT
if evaluatedOutcome == PARTIAL && final > HARD → final = HARD;      adjustedBy += EVALUATED_PARTIAL
if hintLevel ≥ PSEUDOCODE && final > AGAIN   → final = AGAIN;       adjustedBy += HINT_CAP_AGAIN
if hintLevel ∈ {CONCEPT_HINT, DIRECTION} && final > HARD → final = HARD; adjustedBy += HINT_CAP_HARD
if hintLevel == QUESTION_ONLY && final > GOOD → final = GOOD;       adjustedBy += HINT_CAP_GOOD
```
실제로 등급을 낮춘 규칙만 `adjustedBy`에 넣는다.

복습 화면의 hint 기준(클라이언트가 `hintLevel`을 보고):
- 아무것도 보지 않고 답함 → `SELF_EXPLAIN`
- "힌트 보기"(rubric 첫 항목 공개) → `CONCEPT_HINT`
- 답을 쓰기 전에 정답 보기 → `FULL_EXAMPLE`

### 6.2 간격

```text
prev = review_item.interval_days
AGAIN → 1                                   successes = 0, failures += 1
HARD  → max(2, roundHalfUpDiv(prev × 12, 10))  successes = 0          (hard-strategy=FIXED_2이면 2)
GOOD  → max(2, prev × 2)                      successes += 1, failures = 0
EASY  → max(4, prev × 3)                      successes += 1, failures = 0

interval = clamp(interval, 1, 60)
if horizonDate > answeredPlanDate:
    interval = min(interval, max(1, daysBetween(answeredPlanDate, horizonDate) − 1))
due_at = planDayStart(answeredPlanDate + interval)
review_count += 1, last_result = final, last_reviewed_at = now
```

### 6.3 신규 항목의 첫 due

| 생성 경로 | due |
|---|---|
| 온보딩 seed 카드 복사 | priority(MUST→SHOULD→LATER), practicalImportance DESC, conceptKey ASC로 정렬 후 `index`번째 카드 → `planDayStart(today + floorDiv(index, 5))` (하루 5장씩 분산) |
| 신규 seed 카드 (기존 사용자) | 기존 사용자의 마지막 due 이후부터 같은 방식으로 분산 |
| challenge 실패 / coach finding / 수동 생성 | `planDayStart(today + 1)` |
| 같은 `concept_key`가 이미 있음 | 새로 만들지 않는다. `status=ACTIVE`, `due_at = min(기존 due_at, planDayStart(today + 1))` |

### 6.4 Leech · Variant (답변 처리 직후)

```text
if failures ≥ 4 (suspend-after-failures):
    status = SUSPENDED; LEECH_DETECTED 이벤트
    (planner: 최근 1 plan-day 안에 해당 skill의 LEECH_DETECTED 이벤트가 있으면 §5.4 reviewUrgency = 1_000_000 — 복습 대신 학습 task 유도)
else if failures ≥ 2 and variant_status ∈ {NONE, FAILED} and AI 사용 가능:   # Later (아래)
    variant_status = PENDING → 커밋 후 REVIEW_VARIANT 비동기 생성
if 답변이 variant 출제분이었으면: variant 필드 비우고 variant_status = NONE
```
due 목록에서는 `variant_status=READY`이면 variant 문항을 출제한다(`wasVariant=true`).

`REVIEW_VARIANT` 생성(둘째 분기)은 **Later**다(2026-09-18 범위 축소). 그 전까지 `variant_status`는 항상 `NONE`, `wasVariant=false`이며 컬럼·필드는 그대로 둔다.

### 6.5 오늘의 due 선택과 교차 학습 (`RV-INTERLEAVE`)

```text
1. 대상: status=ACTIVE, skill.active=true, due_at < planDayStart(today + 1)   # 오늘 plan-day가 끝나기 전까지 due (내일 시작 시각은 제외)
   정렬: overdueDays DESC → priority(MUST, SHOULD, LATER, 없음) → consecutive_failures DESC → due_at ASC → id ASC
2. 상한: cap (§5.6)까지 앞에서부터 자른다
3. 재배치 (RV-INTERLEAVE): 아래 알고리즘으로 순서만 바꾼다. 카드 집합은 바뀌지 않는다
4. 결과 순서가 출제 순서다
```

**왜 재배치하나** — 같은 skill 카드가 몰려 나오면 그 자리에서는 쉽게 느껴지지만 오래 가지 않는다(§6.0 교차 학습). 정렬 규칙(overdueDays DESC 등)은 **무엇을 낼지**를 정하는 데는 맞지만 같은 skill을 뭉치게 만든다. 그래서 무엇을 낼지 정한 뒤(1~2단계)에 **순서만** 흔든다.

```text
RV-INTERLEAVE(list)                      # list = 1~2단계 결과, 0-based, 무작위를 쓰지 않는다
  for i = 2 .. len(list) − 1:            # 앞에서 뒤로 한 번만 훑는다
      if list[i].skillId == list[i−1].skillId and list[i−1].skillId == list[i−2].skillId:
          # 직전 2장과 같은 skill → 같은 skill 3연속
          j = (j > i 이면서 list[j].skillId != list[i−1].skillId 인 가장 작은 j)
          if j 가 있으면: swap(list[i], list[j])
          else:            그대로 둔다              # 뒤에 다른 skill이 없음 (예: 전부 같은 skill)
  return list
```

| ID | 규칙 |
|---|---|
| RV-INTERLEAVE | 같은 skill 카드가 **3장 연속**이 되는 자리에서, 그 자리보다 뒤에 있는 카드 중 **직전 카드와 skill이 다른 가장 가까운 카드**와 자리를 바꾼다. 바꿀 대상이 없으면 그대로 둔다 |
| RV-INTERLEAVE-D | **결정적이어야 한다.** 무작위·셔플·해시 순서를 쓰지 않는다. 같은 입력에는 항상 같은 출력이 나온다 |
| RV-INTERLEAVE-S | 전진 1회 통과(single forward pass)만 한다. swap 후 같은 `i`를 다시 검사하지 않고 `i+1`로 간다. 따라서 목록 **끝부분에는 3연속이 남을 수 있다**(뒤에 바꿀 카드가 없으므로). 이는 정상이다 |
| RV-INTERLEAVE-C | cap을 **적용한 뒤**에 재배치한다. 재배치가 어떤 카드를 넣거나 빼지 않는다 — 몇 장을 내는지는 §5.6이 정한다 |

`wasVariant` 판정(§6.4)과 `reviewMinutes` 계산(§5.6)은 재배치와 무관하다. 재배치는 화면에 내보내는 순서만 정한다.

### 6.6 Test vectors

**RV-INTERLEAVE** (§6.5 3단계. 입력은 1~2단계를 마친 목록이고, `cap`은 충분히 크다고 가정한다)

| # | 상황 | 입력 (카드(skill) 순서 — `A`·`B`·`C`는 skill) | 출력 |
|---|---|---|---|
| (a) | 같은 skill 5장이 연속 | `R1(A) R2(A) R3(A) R4(A) R5(A) R6(B) R7(B) R8(B) R9(B) R10(B)` | **`R1(A) R2(A) R6(B) R4(A) R5(A) R7(B) R3(A) R8(B) R9(B) R10(B)`** |
| (b) | skill이 1종뿐 | `R1(A) R2(A) R3(A) R4(A) R5(A)` | **변화 없음** — `R1 R2 R3 R4 R5` |
| (c) | 이미 섞여 있음 | `R1(A) R2(B) R3(A) R4(B) R5(C) R6(A)` | **변화 없음** — `R1 R2 R3 R4 R5 R6` |

계산 과정:

- **(a)** `i=2`: `R3,R2,R1`이 모두 A → 3연속. `j>2`이면서 skill ≠ A인 가장 작은 `j` = 5(`R6`, B) → `swap(2,5)` → `R1 R2 R6 R4 R5 R3 R7 R8 R9 R10`.
  `i=3`(`R4`, 직전 `R6`이 B) 통과. `i=4`(`R5`,`R4`는 S1이지만 `R6`이 B) 통과.
  `i=5`: `R3,R5,R4`가 모두 A → 3연속. `j` = 6(`R7`, B) → `swap(5,6)` → `R1 R2 R6 R4 R5 R7 R3 R8 R9 R10`.
  `i=6,7,8` 통과. `i=9`: `R10,R9,R8`이 모두 B → 3연속이지만 `j>9`가 없다 → 그대로(RV-INTERLEAVE-S).
  결과 skill 나열: `A A B A A B A B B B` — 앞의 5연속이 끊겼고, 끝의 3연속은 바꿀 대상이 없어 남는다.
- **(b)** `i=2,3,4` 모두 3연속이지만 skill이 1종이라 `j`가 없다 → 세 번 모두 그대로. **swap 0회**.
- **(c)** `i=2`(`R3` A, 직전 `R2` B), `i=3`(`R4` B, 직전 `R3` A), `i=4`(`R5` C, 직전 `R4` B), `i=5`(`R6` A, 직전 `R5` C) — 3연속 조건이 한 번도 성립하지 않는다. **swap 0회**.

**간격·등급** (horizon은 충분히 멀다고 가정. 마지막 행만 예외)

| prev | selfRating | evaluated | hint | final | adjustedBy | interval |
|---|---|---|---|---|---|---|
| 1 | GOOD | NOT_EVALUATED | SELF_EXPLAIN | GOOD | [] | 2 |
| 2 | GOOD | CORRECT | SELF_EXPLAIN | GOOD | [] | 4 |
| 4 | EASY | CORRECT | QUESTION_ONLY | GOOD | [HINT_CAP_GOOD] | 8 |
| 8 | EASY | PARTIAL | SELF_EXPLAIN | HARD | [EVALUATED_PARTIAL] | 10 |
| 1 | HARD | NOT_EVALUATED | SELF_EXPLAIN | HARD | [] | 2 |
| 30 | GOOD | CORRECT | PSEUDOCODE | AGAIN | [HINT_CAP_AGAIN] | 1 |
| 5 | GOOD | INCORRECT | SELF_EXPLAIN | AGAIN | [EVALUATED_INCORRECT] | 1 |
| 3 | HARD | CORRECT | DIRECTION | HARD | [] | 4 |
| 40 | EASY | CORRECT | SELF_EXPLAIN | EASY | [] | 60 (max) |
| 10 | GOOD | CORRECT | SELF_EXPLAIN | GOOD | [] | horizon까지 5일 → **4** |

---

## 7. Skill State Updater (`SkillStateUpdater`, `SkillLevelRules`)

### 7.1 실행

- 트리거: `LearningEventRecorded`(동기, 같은 트랜잭션). 이벤트의 `skill_id`가 null이면 무시한다.
- 입력: 해당 사용자·skill의 최근 60일(`rule-window-days`) **무효화되지 않은** 이벤트와 **payload만** 사용한다(`04` §6). 다른 테이블을 조회하지 않는다.
- 처리 순서:
  1. `DIAGNOSTIC_*` 이벤트면 §7.4를 적용하고 끝낸다.
  2. 축마다 **하락 규칙**(§7.3)을 먼저 확인한다. 적용되면 그 축의 상승 규칙은 건너뛴다.
  3. 축마다 **다음 레벨(현재+1) 상승 규칙 하나만** 확인한다.
  4. cooldown: 해당 축의 `*_changed_at`이 24시간 이내면 변경하지 않는다(하락, 상승 모두).
  5. 변경마다 `skill_state_change(axis, from, to, rule_code, evidence_event_ids)`를 기록한다. `evidence_event_ids`는 조건을 만족시킨 이벤트 ID다(최대 10개, 최신순).
- `last_practiced_at` = 이벤트 `occurred_at`(최댓값). `evidence_count` = 해당 skill의 `EVIDENCE_ACCEPTED` 수
- 해당 사용자·skill의 `user_skill_state` 행이 없으면(온보딩은 role target이 있는 skill에만 행을 만든다) 레벨 (0,0,0,0), `self_assessed_level=null`로 행을 만든 뒤 규칙을 적용한다.

"독립" = `hintLevel` 또는 `maxHintLevel` ≤ `QUESTION_ONLY`
"해결" = outcome ∈ {`SOLVED_INDEPENDENTLY`, `SOLVED_WITH_HINTS`}

### 7.2 상승 규칙 (현재 레벨 → 다음 레벨)

| rule_code | 축 | → | 조건 (최근 60일, 해당 skill) |
|---|---|---|---|
| `K1_ANY_EVENT` | KNOWLEDGE | 1 | 이벤트 1개 이상 |
| `K2_RECALL_GUIDED` | KNOWLEDGE | 2 | `REVIEW_ANSWERED` finalRating ≥ HARD 이고 hintLevel ≤ CONCEPT_HINT, 2개 이상 (힌트를 보고 떠올린 회상도 인정. §6.1에서 CONCEPT_HINT는 GOOD을 HARD로 깎으므로 GOOD 조건을 두면 K3와 같아진다) |
| `K3_RECALL_INDEPENDENT` | KNOWLEDGE | 3 | `REVIEW_ANSWERED` finalRating ≥ GOOD, 독립, **3개 이상**, 서로 다른 plan_date 2일 이상, 그중 가장 최근 것의 intervalBefore ≥ 4 |
| `K4_RECALL_LONG` | KNOWLEDGE | 4 | `REVIEW_ANSWERED` finalRating ≥ GOOD, 독립, intervalBefore ≥ 14, 2개 이상 |
| `K5_TRANSFER` | KNOWLEDGE | 5 | `CHALLENGE_EVALUATED` difficulty = 5, outcome = SOLVED_INDEPENDENTLY, 1개 이상 |
| `I1_ATTEMPTED` | IMPLEMENTATION | 1 | `CHALLENGE_SUBMITTED` 1개 이상 |
| `I2_SOLVED_GUIDED` | IMPLEMENTATION | 2 | `CHALLENGE_EVALUATED` 해결, maxHintLevel ≤ PARTIAL_CODE, 1개 이상 |
| `I3_SOLVED_INDEPENDENT` | IMPLEMENTATION | 3 | `CHALLENGE_EVALUATED` SOLVED_INDEPENDENTLY, difficulty ≥ 2, 3개 이상, 서로 다른 challengeId 2개 이상 |
| `I4_PRODUCTION_LIKE` | IMPLEMENTATION | 4 | `CHALLENGE_EVALUATED` 해결, difficulty ≥ 4, maxHintLevel ≤ CONCEPT_HINT, 1개 이상 **그리고** `EVIDENCE_ACCEPTED` 1개 이상 |
| `I5_TRANSFER` | IMPLEMENTATION | 5 | `CHALLENGE_EVALUATED` SOLVED_INDEPENDENTLY, difficulty = 5, 서로 다른 challengeId 2개 이상, 그중 1개 이상 isTransfer = true |
| `E1_ANY_EXPLANATION` | EXPLANATION | 1 | `SELF_EXPLANATION_SUBMITTED` 1개 이상, 또는 `REVIEW_ANSWERED` reviewType = EXPLAIN 1개 이상, 또는 `RUBBER_DUCK_COMPLETED` 1개 이상(gap 수와 무관 — 설명을 시도한 것 자체가 E1이다) |
| `E2_PARTIAL` | EXPLANATION | 2 | coverage ≥ 4000인 설명 증거 1개 이상 |
| `E3_COVERAGE` | EXPLANATION | 3 | coverage ≥ 7000, 독립, 설명 증거 2개 이상 |
| `E4_TRANSFER_QUESTION` | EXPLANATION | 4 | coverage ≥ 8000 설명 증거 1개 이상 **그리고** `REVIEW_ANSWERED` wasVariant = true, evaluatedOutcome = CORRECT 1개 이상 |
| `E5_TRADEOFF` | EXPLANATION | 5 | `CHALLENGE_EVALUATED` difficulty = 5, explanationCoverageBp ≥ 8000, 1개 이상 |
| `D1_ANY_REVIEW` | DEBUGGING | 1 | `COACH_REVIEW_COMPLETED` 또는 `COACH_FINDING_CLOSED` 1개 이상 |
| `D2_FOUND_WITH_HINT` | DEBUGGING | 2 | `COACH_FINDING_CLOSED` discoveredBy ∈ {FOUND_AFTER_HINT, MENTIONED_UNPROMPTED}, maxHintLevel ≤ DIRECTION, 1개 이상 |
| `D3_FOUND_UNPROMPTED` | DEBUGGING | 3 | `COACH_FINDING_CLOSED` discoveredBy = MENTIONED_UNPROMPTED, findingType ∈ {BUG, RISK}, 2개 이상 |
| `D4_REPEATED_UNPROMPTED` | DEBUGGING | 4 | D3 조건 이벤트 3개 이상, 서로 다른 coachReviewId 2개 이상, 그중 confidence = HIGH 1개 이상 |
| `D5_TRANSFER` | DEBUGGING | 5 | D3 조건 이벤트의 axis 종류가 3개 이상 |

**도달 가능 상한(Sprint별, 규칙이 아니라 사실):** S3까지는 D = 0(coach는 S4), I ≤ 3(I4는 `EVIDENCE_ACCEPTED`가 필요 → S6), E ≤ 3(E4는 variant 답변이 필요 → REVIEW_VARIANT가 Later이므로 그 전까지 불가), K·I·E의 5는 difficulty 5 challenge가 필요(seed는 최대 L4, AI 생성 L5는 S5부터(`BL-TRN-04`)). UI는 정체된 레벨 옆에 다음 레벨의 조건을 보여준다(`02-user-scenarios-and-ux.md` SCR-SKILL-DETAIL).

**설명 증거와 coverage:**
- `CHALLENGE_EVALUATED`의 `explanationCoverageBp` (null이면 제외)
- `REVIEW_ANSWERED` 중 reviewType = EXPLAIN이고 `rubricCoverageBp`가 있는 것
- `RUBBER_DUCK_COMPLETED` 중 `gapCount = 0`(가드 적용 전 수)이고 `turns ≥ 3`인 것(RD-5). coverage는 **고정값** `devpilot.rubberduck.evidence-coverage-bp`(기본 7000)로 본다. AI가 매긴 점수가 아니라 "3턴 이상 되물어도 빈틈이 드러나지 않았다"는 서버 판정을 증거로 쓰는 것이므로 E2·E3에는 쓰이고 E4(≥ 8000)에는 닿지 않는다 — E4는 전이 증거(variant)가 따로 필요하다. **독립**은 payload `hintDisclosed = false`일 때만이다(세션 시작 이후 같은 대상에 `HINT_DISCLOSED`가 있었으면 true — RD-3으로 Hint Ladder를 거친 경우)

### 7.3 하락 규칙

| rule_code | 조건 | 결과 |
|---|---|---|
| `K_DOWN_RECALL_FAIL` | KNOWLEDGE ≥ 3 이고, 해당 skill의 가장 최근 `REVIEW_ANSWERED` 2개가 모두 finalRating = AGAIN이며 둘 다 최근 14 plan-day 안 | KNOWLEDGE −1 (하한 2) |
| `I_DOWN_TRANSFER_FAIL` | IMPLEMENTATION ≥ 3 이고, 방금 기록된 이벤트가 `CHALLENGE_EVALUATED` isTransfer = true, outcome = FAILED | IMPLEMENTATION −1 (하한 2) |
| `D_DOWN_REPEATED_MISS` | DEBUGGING ≥ 2 이고, 같은 axis의 가장 최근 `COACH_FINDING_CLOSED` 3개가 모두 discoveredBy = MISSED | DEBUGGING −1 (하한 1) |

하락 규칙이 적용되면 `self_assessment_active = false`로 바꾼다.

### 7.4 진단 (예외 규칙)

- `DIAGNOSTIC_PASSED`: 해당 skill의 KNOWLEDGE, IMPLEMENTATION을 `max(현재, min(claimedLevel, 3))`으로 설정한다. rule_code `DIAG_PASSED`. **1단계 제한과 cooldown을 적용하지 않는다.**
  - `claimedLevel` = 그 skill의 `self_assessed_level`. **null이면**(온보딩 진단 모드 `runDiagnostic = true`, 또는 그 category를 자기평가하지 않음) 진단 challenge의 `difficulty`를 쓴다. 예: 진단 모드에서 difficulty 2 진단을 통과 → K·I = `max(현재, min(2, 3))` = 2.
- `DIAGNOSTIC_FAILED`: 레벨은 바꾸지 않고 `self_assessment_active = false`
- 판정: challenge purpose = DIAGNOSTIC, evaluatedOutcome = CORRECT, maxHintLevel ≤ QUESTION_ONLY → PASSED. 그 외 평가 완료 → FAILED
- 진단 challenge 제안 규칙(선택 순서)은 `05-api-spec.md` §4.2가 기준이다. challenge에 skill이 여러 개면, 그 category에 속한 challenge skill 중 `(priority MUST→SHOULD→LATER→없음, practicalImportance DESC, skill.code ASC)`로 가장 앞선 skill의 값을 그 challenge의 정렬 키로 쓴다. `claimedLevel`은 평가 시점 해당 skill의 `self_assessed_level`이다.

### 7.5 Planning level (`PlanningLevelPolicy`)

```text
selfCap = min(self_assessed_level ?? 0, 3)
planningLevel_axis = self_assessment_active ? max(evidenceLevel_axis, selfCap) : evidenceLevel_axis
```
- 증거 레벨이 자기평가를 넘어서면 증거 레벨을 쓴다. 이벤트가 생겼다고 해서 자기평가 값이 사라지지 않는다.
- 부정적 증거(하락 규칙, 진단 실패)가 나오면 자기평가를 더 이상 쓰지 않는다.

### 7.6 Test vectors

| # | 현재 (K,I,E,D) | 60일 이벤트 요약 | 결과 |
|---|---|---|---|
| 1 | (0,0,0,0) | REVIEW_ANSWERED GOOD 1개 | K→1 (`K1_ANY_EVENT`) |
| 2 | (1,0,0,0) | REVIEW_ANSWERED HARD(hint CONCEPT_HINT, selfRating GOOD → §6.1 HINT_CAP_HARD)×2, 마지막 K 변경 30시간 전 | K→2 (`K2_RECALL_GUIDED`) |
| 3 | (1,0,0,0) | 위와 같음, 마지막 K 변경 10시간 전 | 변경 없음 (cooldown) |
| 4 | (2,0,0,0) | 독립 GOOD×3, plan_date 모두 같은 날 | 변경 없음 (서로 다른 날 2일 조건) |
| 5 | (2,0,0,0) | 독립 GOOD×3 (10/1, 10/3, 10/7), 10/7 것 intervalBefore 4 | K→3 |
| 6 | (0,0,0,0) | CHALLENGE_EVALUATED SOLVED_WITH_HINTS(maxHint PSEUDOCODE) | K→1, I 변경 없음 (I1은 CHALLENGE_SUBMITTED 필요) |
| 7 | (1,1,0,0) | CHALLENGE_EVALUATED SOLVED_WITH_HINTS(maxHint PSEUDOCODE) | I→2 |
| 8 | (1,2,0,0) | SOLVED_INDEPENDENTLY d2 ×3, challengeId 모두 같음 | 변경 없음 |
| 9 | (3,3,2,2) | REVIEW_ANSWERED AGAIN (10/10), AGAIN (10/12) — 최근 2개 | K→2 (`K_DOWN_RECALL_FAIL`), self_assessment_active=false |
| 10 | (1,1,0,0) | SELF_EXPLANATION_SUBMITTED | E→1 |
| 11 | (1,1,1,0) | CHALLENGE_EVALUATED outcome PARTIAL, explanationCoverageBp 4500 | E→2 (I는 해결이 아니므로 변경 없음) |
| 12 | (1,1,0,1) | COACH_FINDING_CLOSED MENTIONED_UNPROMPTED RISK ×2 | D→2 (한 단계만) |
| 13 | self 4, active, evidence (1,0,0,0) | — | planning = (3,3,3,3) |
| 14 | self 4, active=false, evidence (1,0,0,0) | — | planning = (1,0,0,0) |
| 15 | (1,1,0,0), claimed 3 | DIAGNOSTIC_PASSED | K→3, I→3 (`DIAG_PASSED`, cooldown 무시) |

---

## 8. Attempt 판정 (`RubricScorer`, `AttemptOutcomeCalculator`)

### 8.1 Coverage → evaluatedOutcome

```text
rubricCoverageBp      = Σ weightBp (met = true)                               # 전체 weight 합 = 10_000
explanationCoverageBp = Σ_EXPLANATION weight == 0 ? null
                      : floorDiv(Σ weightBp(met, axis = EXPLANATION) × 10_000, Σ weightBp(axis = EXPLANATION))

evaluatedOutcome = rubricCoverageBp ≥ 8_000 → CORRECT
                 | rubricCoverageBp ≥ 4_000 → PARTIAL
                 | else                     → INCORRECT
```
복습 rubric(가중치 없음): `weightBp = floorDiv(10_000, n)`이고, 나머지 `10_000 − weightBp × n`은 첫 항목에 더한다.

### 8.2 Attempt outcome

```text
latest = 가장 최근 COMPLETED submission
if attempt.status == ABANDONED and latest == null → ABANDONED
if latest.evaluatedOutcome == CORRECT and attempt.maxHintLevel ≤ QUESTION_ONLY → SOLVED_INDEPENDENTLY
if latest.evaluatedOutcome == CORRECT                                          → SOLVED_WITH_HINTS
if latest.evaluatedOutcome == PARTIAL                                          → PARTIAL
else                                                                           → FAILED
```

### 8.3 복습 항목 생성

평가 완료 후 `outcome ∈ {FAILED, PARTIAL}`이거나 (`SOLVED_WITH_HINTS`이고 maxHintLevel ≥ PSEUDOCODE)이면 challenge의 **각 skill마다** review item을 upsert한다(§6.3).

| 필드 | 값 |
|---|---|
| concept_key | `CHALLENGE:{challengeId}` (skill이 여러 개면 `CHALLENGE:{challengeId}:{skillCode}`) |
| review_type | `EXPLAIN` |
| prompt | evaluation `followUpQuestion`이 있으면 그 문장, 없으면 `"{title}" 문제의 핵심을 설명하세요: {expectedConcepts를 ", "로 연결}` |
| expected_answer | rubric criterion들을 `- ` 목록으로 연결 |
| rubric_json | rubric criterion(id, criterion) |
| origin / source_type | `AI_GENERATED` 또는 `SEED`(seed challenge) / `CHALLENGE_ATTEMPT` |

**Test vectors**

| rubric (weightBp, axis, met) | coverage | expl. coverage | outcome (maxHint) |
|---|---|---|---|
| (4000,I,T) (4000,E,T) (2000,I,F) | 8000 | 10000 | CORRECT → SOLVED_INDEPENDENTLY (QUESTION_ONLY) |
| (4000,I,T) (4000,E,F) (2000,I,T) | 6000 | 0 | PARTIAL → PARTIAL |
| (5000,I,F) (5000,I,T) | 5000 | null | PARTIAL |
| (3000,I,T) (3000,E,F) (4000,E,F) | 3000 | 0 | INCORRECT → FAILED |
| 전부 met | 10000 | — | CORRECT → SOLVED_WITH_HINTS (CONCEPT_HINT) |
| 복습 rubric 3개, met T,F,T | weight 3334/3333/3333 → 6667 | — | PARTIAL |

---

## 9. Hint Ladder (`HintLadderPolicy`, `HintService`)

### 9.1 정책

| ID | 규칙 |
|---|---|
| HL-1 | 순서는 `HintLevel` ordinal. 요청 단계 ≤ 현재 max이면 AI를 호출하지 않는다. **요청 단계 이하에서 저장된 가장 높은 단계의 내용**을 반환하고, 그런 저장 내용이 없으면(건너뛴 단계) **저장된 가장 낮은 단계의 내용**을 반환한다(`05-api-spec.md` §10.8 4단계) |
| HL-2 | 선행 조건: challenge는 `self_explanation` 또는 `self_explanation_skipped`, coach finding은 `user_response` 존재 또는 요청의 `skipSelfExplanation=true`(이벤트 기록). 없으면 `409 SELF_EXPLANATION_REQUIRED` |
| HL-3 | 여러 단계를 한 번에 올릴 수 있다. 요청 단계만 생성·저장하고, 건너뛴 단계는 `HINT_DISCLOSED.skippedLevels`에 기록한다 |
| HL-4 | `PSEUDOCODE` 이상 요청에 `acknowledgeEvidenceImpact=true`가 없으면 `409 HINT_CONFIRMATION_REQUIRED` |
| HL-5 | `FULL_EXAMPLE`은 제출 1회 이상(challenge) 또는 `giveUp=true`일 때만 허용. 아니면 `409 FULL_EXAMPLE_NOT_ALLOWED` |
| HL-6 | 내용 출처: challenge 1~3단계는 `challenge.hints_json`(SEED/PREGENERATED), 4단계 이상과 coach finding 전 단계는 `HINT_GENERATE`(AI) |
| HL-7 | 공개할 때마다 `hint_disclosure` 행 + `HINT_DISCLOSED` 이벤트 + 대상의 `max_hint_level` 갱신 |
| HL-8 | AI 생성 hint 중 `DIRECTION` 이하 단계에 코드 블록이나 코드 줄 3줄 이상이 있으면 가드가 거절한다(`17-ai-integration.md` §6) |

### 9.2 Test vectors

| 현재 max | 요청 | 조건 | 결과 |
|---|---|---|---|
| SELF_EXPLAIN | CONCEPT_HINT | self-explanation 없음 | 409 SELF_EXPLANATION_REQUIRED |
| SELF_EXPLAIN | CONCEPT_HINT | 제출됨 | CONCEPT_HINT 공개, skippedLevels=[QUESTION_ONLY] |
| CONCEPT_HINT | QUESTION_ONLY | — | 저장된 ≤ QUESTION_ONLY 없음 → CONCEPT_HINT 내용 반환 (AI 호출 0) |
| CONCEPT_HINT | PSEUDOCODE | ack 없음 | 409 HINT_CONFIRMATION_REQUIRED |
| CONCEPT_HINT | PSEUDOCODE | ack=true | AI 생성, max=PSEUDOCODE |
| DIRECTION | FULL_EXAMPLE | ack=true, 제출 0회, giveUp 없음 | 409 FULL_EXAMPLE_NOT_ALLOWED (HL-4 확인을 먼저 검사하므로 ack가 없으면 HINT_CONFIRMATION_REQUIRED) |

### 9.3 Coach finding의 `discovered_by` 확정 (review close 시)

```text
if finding.mentioned_by_user                     → MENTIONED_UNPROMPTED
else if finding.user_identified_issue == true    → FOUND_AFTER_HINT
else                                             → MISSED
```
- finding마다 `thinking_pattern_observation(axis = category, observation = discoveredBy)`와 `COACH_FINDING_CLOSED` 이벤트를 기록한다.
- 분석 결과의 `incorrectClaims[]`(axis)는 분석 완료 시점에 `INCORRECT_CLAIM` observation으로 기록한다.
- `user_identified_issue`는 `COACH_RESPONSE_FEEDBACK` 결과다. hint를 보지 않았더라도 응답으로 문제를 짚었으면 `FOUND_AFTER_HINT`로 본다. 질문 자체가 힌트이기 때문이다.
- HL-2에서 `skipSelfExplanation=true`로 hint를 요청하면 `SELF_EXPLANATION_SKIPPED` 이벤트(source_type `COACH_FINDING`)를 기록한다.

### 9.4 Coach finding → 복습 항목 (review close 시)

`findingType ∈ {BUG, RISK}`, `skill_id` 있음, `discoveredBy ∈ {MISSED, FOUND_AFTER_HINT}`인 finding마다 review item을 upsert한다(§6.3 규칙).

| 필드 | 값 |
|---|---|
| concept_key | `COACH:{skillCode}:{category}` |
| review_type | `EXPLAIN` |
| prompt | finding `learning_question` |
| expected_answer | finding `summary` (+ `ai_feedback`이 있으면 줄바꿈 후 추가) |
| rubric_json | `[{"id": "R1", "criterion": summary}]` |
| origin / source_type / source_id | `AI_GENERATED` / `COACH_FINDING` / finding id |
| due | 다음 plan-day 시작. 이미 있으면 `min(기존 due, 다음 plan-day 시작)` |

코드 원문은 30일 후 삭제되므로 복습 문항에 코드를 넣지 않는다.

### 9.5 러버덕 (`RubberDuckPolicy`)

사용자가 설명하고 **AI는 답을 주지 않고 되묻는다.** 막히는 지점이 드러나면 그게 복습 대상이 된다. 기존 자기설명은 한 번 쓰고 채점받는 일방향이었고, 러버덕은 대화다. 이 절이 §9에 있는 것은 **RD-3이 §9.1 Hint Ladder로 넘기는 유일한 출구**이기 때문이다(아래 "RD-3과 Hint Ladder의 연결").

흐름: 대상 선택 → 사용자가 설명 → **AI가 질문 1개** → 사용자가 답 → 반복(기본 최대 5턴, `devpilot.rubberduck.max-turns`) → 종료 시 정리(`gaps[]`, `confirmed[]`) → `gaps` → 복습 카드, `confirmed` → 학습 이벤트.

| ID | 규칙 |
|---|---|
| RD-1 | AI는 **질문만** 한다. 정답·수정 코드·"맞습니다/틀렸습니다"를 말하지 않는다. 출력 가드 `CodeLeakGuard`(`17-ai-integration.md` §6)를 그대로 적용한다 |
| RD-2 | 질문은 **사용자 설명의 빈틈이나 틀린 전제를 겨냥**한다. 일반적인 질문("더 설명해 보세요")은 금지 |
| RD-3 | 사용자가 "모르겠다"류로 **2턴 연속** 답하면 Hint Ladder로 넘긴다(무한 좌절 방지). 이때 `HINT_DISCLOSED` 이벤트가 정상 기록된다 |
| RD-4 | 턴 상한에 도달하거나 사용자가 종료하면 정리를 **한 번만** 한다. 정리 후의 세션은 `COMPLETED`이고 턴을 더 받지 않는다 |
| RD-5 | 정리 AI가 낸 `gaps`가 **가드 적용 전 기준 0개이고**(`rawGapCount = 0` — 가드가 gap을 지워서 0이 된 경우는 인정하지 않는다) **턴이 3 이상**이면 EXPLANATION 증거로 인정한다(`RUBBER_DUCK_COMPLETED`). coverage는 고정값 7000이고 독립은 `hintDisclosed = false`일 때다 — §7.2 "설명 증거와 coverage" |
| RD-6 | 사용자 입력은 저장·AI 전송 전에 `SecretMasker`를 통과한다(`07-security-and-privacy.md` §8.2). **마스킹 전 원문은 어디에도 저장하지 않는다.** 마스킹본(`rubber_duck_turn.user_text`)은 학습 기록으로 계정 유지 기간 보관한다(`04` §8) |
| RD-7 | 한 세션은 skill 하나를 주 대상으로 한다(`skill_id`). 없으면 **학습 이벤트를 남기지 않는다**(§7.1이 `skill_id`가 null인 이벤트를 무시하므로, 레벨에도 영향이 없다) |

**RD-3의 "모르겠다" 판정 (결정적, AI에 맡기지 않는다)**

```text
사용자 턴 텍스트에서 공백을 제거한 길이 < dontKnowMaxChars(기본 30)  이고
`devpilot.rubberduck.dont-know-phrases` 중 하나를 대소문자 무시 부분 문자열로 포함하면 "모르겠다"로 본다
기본 목록: ["모르겠", "모름", "잘 모르", "생각 안", "idk", "no idea", "don't know", "dont know"]
연속 2턴이 모두 "모르겠다"이면 RD-3이 발동한다 (사이에 다른 턴이 끼면 카운터는 0으로 돌아간다)
```

판정을 AI에 맡기면 결정적 규칙이 아니게 되므로 서버가 위 규칙으로 판정한다. 문구 목록과 길이 임계값은 §1의 다른 수치와 마찬가지로 설정값이고, 4주 사용 후 조정한다.

**RD-3과 Hint Ladder(§9.1)의 연결**

| 항목 | 이어지는 방식 |
|---|---|
| 넘길 수 있는 대상 | `target_type = CHALLENGE`일 때만 §9.1로 넘긴다(Hint Ladder와 `max_hint_level`을 가진 대상은 `challenge_attempt`뿐이다 — 복습 카드의 힌트는 §6.1 `HINT_CAP_*`의 카드 힌트이고 Hint Ladder가 아니다). `REVIEW_ITEM`·`CODE_READING`·`CONCEPT`·`PROJECT_WORK`는 넘기지 않고, RD-4대로 정리하고 세션을 끝낸다(그 `gaps`가 §6.3 "수동 생성" 경로로 복습 카드가 된다) |
| 진입 단계 | 대상의 현재 `max_hint_level`의 **다음 단계**를 요청한 것으로 본다. 건너뛴 단계 처리는 HL-3 그대로(`HINT_DISCLOSED.skippedLevels`) |
| HL-2 선행 조건 | 러버덕 턴이 **1개 이상이면 자기설명을 한 것으로 본다.** 러버덕 턴 자체가 자기설명이므로 `409 SELF_EXPLANATION_REQUIRED`를 내지 않는다. 턴이 0개인 세션은 RD-3이 성립할 수 없으므로 이 예외가 필요한 경우는 없다 |
| HL-4 확인 | `PSEUDOCODE` 이상으로 넘어가야 하면 HL-4가 그대로 적용된다 — `acknowledgeEvidenceImpact` 없이 자동으로 올리지 않는다. 확인이 필요하면 `409 HINT_CONFIRMATION_REQUIRED`를 내고 화면이 사용자에게 묻는다 |
| 기록 | HL-7 그대로 `hint_disclosure` 행 + `HINT_DISCLOSED` 이벤트 + 대상의 `max_hint_level` 갱신. 따라서 §6.1의 `HINT_CAP_*`과 §7.2의 "독립" 판정(`maxHintLevel ≤ QUESTION_ONLY`)에 **그대로 반영된다** — 러버덕을 거쳤다고 해서 힌트가 증거 계산에서 빠지지 않는다 |
| 이후 | Hint 공개 후 러버덕 세션은 계속할 수 있다. 턴 상한(RD-4)은 그대로다 |

**AI가 불가할 때** — `aiStatus ∈ {DISABLED, BALANCE_EXHAUSTED}`이면 화면이 러버덕 시작 버튼을 막고, §5.3이 `READ_CODE`를 제안하지 않는다(RC-1의 완료 조건이 러버덕이므로). 서버의 시작 요청(`05` §9.6)은 AI를 부르지 않으므로 막지 않지만, 턴 제출은 `05` §1.9.4대로 실패한다. 계획·복습·기록은 그대로 동작한다.

**코드 읽기(`READ_CODE`)와의 관계**

| ID | 규칙 |
|---|---|
| RC-1 | `READ_CODE` 과제의 완료 조건은 **러버덕 세션 1개 완료**다. 읽었다고 체크만 하는 것은 완료가 아니다 |
| RC-2 | 한 과제는 파일 1개·범위 1개다. 여러 파일을 묶지 않는다 |
| RC-3 | planner는 해당 skill의 planning **KNOWLEDGE가 1 이상**일 때만 `READ_CODE`를 제안한다(§5.3 2번). 아무것도 모르는 상태에서 코드를 읽으면 좌절한다 |
| RC-4 | 저장소가 로컬에 없으면 화면이 `cloneHint`를 먼저 보여준다. **서버는 저장소를 fetch하지 않는다**(`07-security-and-privacy.md` §5.5) — 사용자가 직접 clone해서 IDE로 읽는다 |

읽을 대상은 `content/curated-repos.yaml`이 정한다(형식·검증은 `19-content-spec.md` §3.8·§4.1, 줄 번호 관리는 §8.4).

**사이드 프로젝트 규칙 (SP-1~SP-3)** — 배운 것을 적용할 대상이다(`project` 모듈). 이 ID는 스파이크 ID(`11-development-roadmap.md` §4의 SP-1~SP-5)와 다른 체계다. 스파이크를 가리킬 때는 "스파이크 SP-n"으로 쓴다.

| ID | 규칙 |
|---|---|
| SP-1 | 온보딩 마지막 단계에서 사이드 프로젝트를 **하나 만든다.** 기본 이름("주문 시스템")은 클라이언트가 채워 보내고 바꿀 수 있다. 건너뛸 수 있으며, `ACTIVE` 프로젝트가 하나도 없으면 §5.3이 `PROJECT_TASK`를 제안하지 않는다 |
| SP-2 | `PROJECT_TASK` 제안 문구는 프로젝트 이름과 skill 이름을 넣어 구체화한다(§5.8 표 `PROJECT_TASK` 행) |
| SP-3 | `ACTIVE` 프로젝트는 여러 개일 수 있지만 planner는 `updated_at`이 가장 최근인 `ACTIVE` 하나만 쓴다. 그 id를 생성 시점에 `learning_task.side_project_id`에 고정한다(`05` §8.1) |

---

## 10. Verification Guard (`VerificationGuard`)

| 최종 status | 허용 source_type | 부여 방법 |
|---|---|---|
| `VERIFIED` | `COMPILER`, `TEST_RESULT`, `CURATED_SOURCE` | **서버가 실행한 도구 결과**(MVP에는 없음) 또는 `content/curated-sources.yaml`에 등록된 근거 ID |
| `SUPPORTED` | `STATIC_ANALYSIS`, `OFFICIAL_DOC`, `SECURITY_GUIDE` | STATIC_ANALYSIS는 서버 도구 실행 결과만(MVP에는 없음). OFFICIAL_DOC, SECURITY_GUIDE는 `sourceReference` URL의 호스트가 allowlist에 있을 때 |
| `AI_JUDGMENT` | `AI_REASONING` | AI 판단 |
| `UNCERTAIN` | `OFFICIAL_DOC`, `SECURITY_GUIDE`, `CURATED_SOURCE`, `AI_REASONING` | 맥락 부족. 도구 결과 계열(`COMPILER`, `TEST_RESULT`, `STATIC_ANALYSIS`)과는 조합할 수 없다 — `schema.sql`의 CHECK와 `04` §7 I-08이 거부한다 |

AI 출력 파싱 직후 finding마다 순서대로 적용한다.

```text
1. source_type ∈ {COMPILER, TEST_RESULT, STATIC_ANALYSIS} (AI가 주장했지만 서버 도구 실행 기록이 없음)
     → source_type = AI_REASONING, status = (status == UNCERTAIN ? UNCERTAIN : AI_JUDGMENT), action DOWNGRADED_TOOL_CLAIM
2. source_type == CURATED_SOURCE이고 sourceReference가 curated ID 목록에 없음
     → source_type = AI_REASONING, status = AI_JUDGMENT, action DOWNGRADED_UNKNOWN_CURATED
3. status == VERIFIED이고 source_type != CURATED_SOURCE
     → status = AI_JUDGMENT (source_type이 OFFICIAL_DOC/SECURITY_GUIDE이고 규칙 4를 통과하면 SUPPORTED), action DOWNGRADED_VERIFIED
4. source_type ∈ {OFFICIAL_DOC, SECURITY_GUIDE}
     → sourceReference가 https URL이 아니거나(null, 문자열 설명, http 포함) 호스트가 allowlist(정확 일치 또는 하위 도메인)가 아니면
        source_type = AI_REASONING, status = (status == UNCERTAIN ? UNCERTAIN : AI_JUDGMENT), action DOWNGRADED_UNTRUSTED_SOURCE
5. status == SUPPORTED이고 source_type == AI_REASONING → status = AI_JUDGMENT, action DOWNGRADED_SUPPORTED_WITHOUT_SOURCE
```

- **서버는 URL을 fetch하지 않는다.** 호스트 문자열만 검사한다.
- allowlist (`devpilot.ai.trusted-source-hosts`): `docs.spring.io`, `spring.io`, `docs.oracle.com`, `openjdk.org`, `www.postgresql.org`, `owasp.org`, `cheatsheetseries.owasp.org`, `www.kisa.or.kr`, `supabase.com`, `dart.dev`, `docs.flutter.dev`, `api.flutter.dev`, `pmd.github.io`, `spotbugs.readthedocs.io`, `checkstyle.org`, `junit.org`, `hibernate.org`, `docs.jboss.org`, `developer.mozilla.org`, `www.rfc-editor.org`
- DB CHECK로 이중 방어한다(`schema.sql` coach_finding).

**Test vectors**

| AI 출력 (status, sourceType, reference) | 결과 |
|---|---|
| VERIFIED, AI_REASONING, — | AI_JUDGMENT, AI_REASONING |
| SUPPORTED, STATIC_ANALYSIS, "PMD CloseResource" | AI_JUDGMENT, AI_REASONING |
| SUPPORTED, OFFICIAL_DOC, `https://pmd.github.io/pmd/pmd_rules_java_errorprone.html#closeresource` | SUPPORTED, OFFICIAL_DOC |
| VERIFIED, OFFICIAL_DOC, `https://docs.spring.io/...` | SUPPORTED, OFFICIAL_DOC |
| SUPPORTED, OFFICIAL_DOC, `https://blog.example.com/...` | AI_JUDGMENT, AI_REASONING |
| UNCERTAIN, COMPILER, — | UNCERTAIN, AI_REASONING |
| SUPPORTED, AI_REASONING, — | AI_JUDGMENT, AI_REASONING |

---

## 11. Plan Versioning (`PlanCommandService`, `ReplanService`)

### 11.1 변경 분류

| 변경 | 처리 |
|---|---|
| milestone `status`, `description`, `sortOrder` | in-place PATCH (`version` 필수) |
| milestone 추가·삭제, 날짜·priority·skill 구성 변경, skill target defer/축소 | **새 plan version** (`POST /plans/{id}/replan`) |
| 목표일 변경 | 학습 목표 즉시 저장 + 활성 plan `replan_recommended = true` |

### 11.2 Replan 절차 (한 트랜잭션)

```text
1. 대상 plan 조회 (userId 조건, status = ACTIVE 아니면 409 PLAN_NOT_ACTIVE, version 불일치면 409 CONCURRENT_MODIFICATION)
2. 요청 검증: milestone 날짜 범위, skillCodes 존재, 최대 24개
3. old.status = SUPERSEDED, old.superseded_at = now
4. entityManager.flush()          ← partial unique index 때문에 반드시 새 plan INSERT 전에
5. new plan: plan_version = old.plan_version + 1, status = ACTIVE, supersedes_plan_id = old.id,
             title = old.title, change_reason = request.reason, replan_recommended = false
6. new milestones: 요청 목록으로 생성 (id가 있어도 새 UUID. 이전 id는 응답의 mapping으로 제공)
7. new plan_skill_target: old 목록 복사 후 적용
     - acceptedDeferrals의 skill → deferred = true, adjustment = DEFERRED
     - acceptedTargetReductions → 해당 축 target 변경, adjustment = TARGET_REDUCED
     - restoredDeferrals의 skill → deferred = false, adjustment = USER_EDITED (같은 skill이 acceptedDeferrals에도 있으면 400 VALIDATION_FAILED)
     - acceptedTargetRaises → 해당 축 target을 newTarget으로 (현재 < newTarget ≤ 5), adjustment = USER_EDITED.
       §4.4 6단계 확장 제안(RAISE_TARGET)을 받아들이는 경로다. 같은 (skill, 축)이 축소에도 있으면 400 MUTUALLY_EXCLUSIVE
     - `role_skill_target`에는 있는데 이전 plan에 없는 skill(새 seed로 추가된 skill)은 role 기본값으로 추가한다(adjustment = ROLE_DEFAULT)
     - priority 변경과 **확장 제안 밖의** target 상향은 MVP 범위 밖이다(Later). 필요하면 content YAML의 role target을 바꾸고 새 plan을 만든다
8. PLAN_REPLANNED 이벤트
9. 새 plan 기준 risk 계산 → plan_progress_snapshot(today) upsert
```

- 과거 `daily_plan`, `learning_task`는 이전 plan/milestone ID를 그대로 참조한다.
### 11.3 최초 plan 생성 (온보딩, `POST /plans`)

- `role_skill_target` 전체를 `plan_skill_target`으로 복사한다(adjustment = ROLE_DEFAULT).
- milestone 날짜는 `19-content-spec.md`의 **계획 템플릿 배치 알고리즘**으로 계산한다. `useTemplate=false`이면 milestone 없이 plan만 만든다.
- plan title: 템플릿의 `planTitle`(예: `Java 백엔드 성장 계획`). `useTemplate=false`이면 같은 targetRole 템플릿의 `planTitle`을 제목으로만 쓴다

---

## 12. 지표 (`MetricsCalculator`)

기간: 기본 최근 28 plan-day. 주간: ISO week(월요일 시작, 사용자 plan-day 기준). 결과는 정수다.

| 지표 | 계산 | 단위 |
|---|---|---|
| `completedSessions` | COMPLETED learning_session 수 | 개 |
| `studyMinutes` | COMPLETED 세션 actual_minutes 합 | 분 |
| `independentSolveRateBp` | `floorDiv(SOLVED_INDEPENDENTLY 수 × 10_000, outcome ∉ {null, ABANDONED}인 attempt 수)`, 분모 0이면 null | bp |
| `averageHintLevelMilli` | `floorDiv(Σ ordinal(maxHintLevel) × 1000, n)` — 대상: 평가 완료 attempt, close된 finding, review_answer | 1/1000 |
| `recallSuccessRateBp` | `floorDiv(finalRating ∈ {GOOD, EASY} 수 × 10_000, review_answer 수)` | bp |
| `selfFoundRiskCount` | `COACH_FINDING_CLOSED` 중 discoveredBy = MENTIONED_UNPROMPTED, findingType ∈ {BUG, RISK} | 개 |
| `acceptedEvidenceCount` | status = ACCEPTED evidence 수 (기간 내 accepted_at) | 개 |
| `completedRubberDuckSessions` | status = COMPLETED `rubber_duck_session` 수 (기간 내 `completed_at`의 plan-day). skill 유무와 무관하다 — 이벤트가 아니라 세션 행으로 센다(RD-7) | 개 |
| `riskLevel`, `ratioBp` | 기간 마지막 snapshot | — |
| `weakThinkingAxes` | axis별 `MISSED` 비율(`MISSED / 전체 observation`) 상위 3개, observation 3개 이상인 axis만 | 목록 |
| `requirementCoverageBp` | 최근 분석한 요구사항 문서(로드맵 비교에 붙여넣은 로드맵·기술 목록) 5개의 REQUIRED 항목 중 fit = READY 비율 | bp |

---

## 13. 로드맵 항목 분류 (`RequirementFitClassifier`)

로드맵 비교(FR-19)에서 요구사항 문서의 requirement item(로드맵·기술 목록에서 뽑은 항목)마다 적용한다. 자기평가가 아니라 **증거 레벨**(evidence level)로 판단한다.

```text
if requirement.skill_id == null → fitCategory = null (UI: "분류 불가")
target_axis = 활성 plan의 plan_skill_target(해당 skill) 목표
            ?? role_skill_target 목표
            ?? (KNOWLEDGE 3, IMPLEMENTATION 3, EXPLANATION 3, DEBUGGING 2)
gap = max_axis( target_axis − evidenceLevel_axis )
fitCategory = gap ≤ 0 → READY
            | gap == 1 → STRETCH
            | gap ≥ 2 → LATER
matchedEvidenceIds = 해당 skill의 ACCEPTED evidence id (accepted_at DESC, 최대 3개)
```

- deferred 여부와 무관하게 target을 쓴다.
- 분류는 분석 완료 시점에 저장하고, 이후 레벨이 바뀌어도 자동으로 다시 계산하지 않는다(재분석은 새 요구사항 문서로 요청한다).

**Test vectors**

| target (K,I,E,D) | evidence (K,I,E,D) | gap | fit |
|---|---|---|---|
| (3,3,3,2) | (3,3,3,2) | 0 | READY |
| (4,4,4,3) | (4,3,4,3) | 1 | STRETCH |
| (4,4,4,3) | (2,4,4,3) | 2 | LATER |
| (3,3,3,2) | (5,5,5,5) | −2 → ≤0 | READY |
