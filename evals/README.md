# AI Evals

실제 모델로 프롬프트·모델·가드 변경의 품질 회귀를 확인하는 case 모음이다. 형식·채점·합격 기준의 기준 문서는 `docs/17-ai-integration.md` §12이다.

- PR CI의 결정적 테스트(`FakeAiProvider`)와 별개다. 실제 API를 호출하므로 **비용이 든다.**
- 실행 대상 변경: `prompts/**`, `ai/schemas/**`, `integration/ai/guard/**`, `integration/ai/prompt/**`, `devpilot.ai.model`·`operations`·`prompts` 설정.

## 구조

```text
evals/
├── README.md
├── cases/
│   ├── coach-review/*.yaml        # COACH_REVIEW (16)
│   ├── hint-generate/*.yaml       # HINT_GENERATE (3)
│   └── challenge-evaluate/*.yaml  # CHALLENGE_EVALUATE (3)
└── baselines/<suite>@<promptVersion>__<model>.json

backend/build/reports/ai-eval/     # 실행 결과: <timestamp>-<suite>.json, summary.md (커밋하지 않음)
```

## 실행

```bash
cd backend
export DEEPSEEK_API_KEY=...         # prod와 같은 선불 잔액을 쓴다 (키가 하나, 결정 E). 실행 전 잔액 확인
./gradlew aiEval -PevalSuite=coach-review
./gradlew aiEval -PevalSuite=all -PevalRepeat=3 -PevalMaxCostUsd=0.5
./gradlew aiEval -PevalSuite=coach-review -PevalCase='coach-review/trap-*'
```

| 옵션 | 기본 | 설명 |
|---|---|---|
| `evalSuite` | (필수) | `coach-review` \| `hint-generate` \| `challenge-evaluate` \| `all` |
| `evalModel` | `devpilot.ai.model` | 모델 교체 검증 시 지정 |
| `evalRepeat` | 3 | case당 반복 횟수 |
| `evalCase` | 전체 | case id glob |
| `evalMaxCostUsd` | 0.5 | 실행 전 비용 미리보기가 이 값을 넘으면 호출 없이 실패 (결정 E: 1회 상한 USD 0.5) |
| `evalUpdateBaseline` | false | 합격한 실행에서만 baseline 파일을 쓴다 |

CI: `.github/workflows/ai-eval.yml`. **수동 실행(`workflow_dispatch`)만** 한다(결정 E — prod와 같은 잔액을 쓰므로 PR label 자동 실행을 두지 않는다). input은 `suite`, `repeat`(1~5), `max_cost_usd`(0.1~0.5)이고 `ai-eval` environment 승인이 필요하다. 결과는 artifact(`backend/build/reports/ai-eval/`, 30일)로 남는다. `evalCase`·`evalModel`·`evalUpdateBaseline`은 로컬 실행 전용이다.

## 비용 (deepseek-flash, 반복 3회, 추정 상한)

단가는 `docs/03-system-architecture.md` §9 `devpilot.ai.pricing`이고, **피크 판정 없이 항상 ×2**로 보수 계산한다(결정 F). 1회 호출 추정: coach-review ≈ 2,800 input + 3,500 output(thinking high의 추론 토큰 포함) → 2 × (420 + 2,100) micro ≈ USD 0.005.

| suite | case | 호출 | thinking | 추정 비용 |
|---|---|---|---|---|
| coach-review | 16 | 48 | high | ≈ USD 0.24 (0.005 × 48) |
| hint-generate | 3 | 9 | off | ≈ USD 0.01 |
| challenge-evaluate | 3 | 9 | low | ≈ USD 0.03 |
| all | 22 | 66 | — | ≈ USD 0.28 |

재시도(ASYNC operation 최대 1회)가 생기면 늘어난다. 실제 비용은 실행 후 출력된다. `all` 1회가 월 예산(USD 3)의 약 10%다.

## 합격 기준 (요약)

| suite | 기준 |
|---|---|
| 전체 | 스키마 유효율 100% |
| coach-review | 가드 전 원시 출력의 `VERIFIED` 0건, 코드 노출 0건, mustDetect recall ≥ 80%, 오탐 함정(`trap: true`) 위반 0건, baseline 대비 recall 하락 ≤ 5%p |
| hint-generate | level 일치 100%, `≤ DIRECTION` 코드 노출 0건, `mustNotContainAny` 위반 0건 |
| challenge-evaluate | rubric 판정 일치 ≥ 85%, `evaluatedOutcome` 일치 ≥ 85%, `mustNotContainAny` 위반 0건 |

## Case 목록

| id | 확인하는 것 |
|---|---|
| `coach-review/resource-leak-001` | 예외 경로의 자원 누수 + self-review 언급(`mentionedByUser`) |
| `coach-review/broad-catch-001` | `catch (Exception)`으로 원인 유실 |
| `coach-review/swallowed-exception-001` | 빈 catch. 올바른 try-with-resources는 지적하지 않음 |
| `coach-review/optional-get-001` | 검사 없는 `Optional.get()` |
| `coach-review/transaction-self-invocation-001` | 같은 클래스 내부 호출로 `@Transactional` 우회 |
| `coach-review/transactional-private-001` | private 메서드의 `@Transactional` |
| `coach-review/n-plus-one-001` | 반복문 안 LAZY 연관관계 접근 |
| `coach-review/sensitive-logging-001` | 비밀번호·토큰 로그 |
| `coach-review/sql-injection-001` | SQL 문자열 연결, 동적 정렬 컬럼 |
| `coach-review/simpledateformat-static-001` | static `SimpleDateFormat` 공유 |
| `coach-review/entity-equals-hashcode-001` | 생성 전 id 기반 equals/hashCode |
| `coach-review/trap-try-with-resources-001` | 오탐 함정: 올바른 try-with-resources·예외 변환 |
| `coach-review/trap-transactional-public-001` | 오탐 함정: 다른 bean에서 호출하는 public `@Transactional`, 변경 감지 |
| `coach-review/trap-optional-orelsethrow-001` | 오탐 함정: `Optional.orElseThrow` |
| `coach-review/injection-verified-001` | 주석의 VERIFIED·수정 코드 요구를 따르지 않음 |
| `coach-review/korean-comments-001` | 한국어 주석·문자열 코드, 문자열 `==` 비교 |
| `hint-generate/coach-finding-concept-001` | `CONCEPT_HINT`에 코드·해결 형태 없음 |
| `hint-generate/coach-finding-direction-001` | `DIRECTION`에 코드 없음, 이전 hint 위에 쌓기 |
| `hint-generate/challenge-pseudocode-001` | `PSEUDOCODE`가 완성 코드를 주지 않음 |
| `challenge-evaluate/config-exception-correct-001` | 전 기준 충족 → CORRECT |
| `challenge-evaluate/config-exception-partial-001` | cause 유실 → PARTIAL |
| `challenge-evaluate/config-exception-injection-001` | 답안 속 채점 지시 무시 → INCORRECT |

## Case 작성 규칙

1. 파일 경로(확장자 제외)가 `id`다. 형식은 문서 §12.4를 따른다.
2. Java 코드는 실제로 있을 법하고 기술적으로 정확해야 한다. 정답이 논쟁적인 코드는 넣지 않는다.
3. `lines`는 content의 줄 번호(1부터)다. case를 고치면 줄 번호를 다시 확인한다.
4. 오탐 함정은 `trap: true`, `mustDetect: []`, 틀리게 지적하기 쉬운 category를 `mustNotClaim`에 넣는다.
5. 실제 secret이나 토큰 형태 문자열(키 prefix + 긴 난수)을 넣지 않는다. 저장소는 public이고 gitleaks(`.gitleaks.toml`)가 CI에서 돈다.
6. skill code(`JAVA.IO_RESOURCE` 등)는 seed catalog(`content/`) 확정 후 실제 코드와 맞춘다. 맞지 않는 코드는 `SkillCodeGuard`가 null로 만들 뿐 채점에는 영향이 없다.
7. case 추가·수정은 baseline 비교를 깨므로, 같은 PR에서 baseline을 다시 만든다.
