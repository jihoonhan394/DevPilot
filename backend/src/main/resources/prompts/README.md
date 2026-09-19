# Prompts

AI operation별 프롬프트다. 규칙의 기준은 `docs/17-ai-integration.md` §9이고, 입력 변수는 §3, 출력 스키마는 §4다.

## 구조

```text
prompts/<prompt-id>/<version>/system.md          # 역할·원칙·판단 기준. placeholder 없음, 요청마다 동일(캐시 prefix)
prompts/<prompt-id>/<version>/user-template.md   # 요청별 맥락. {{variable}} placeholder
```

| prompt id | AiOperation | mode |
|---|---|---|
| `coach.review` | `COACH_REVIEW` | ASYNC |
| `coach.response-feedback` | `COACH_RESPONSE_FEEDBACK` | SYNC |
| `challenge.generate` | `CHALLENGE_GENERATE` | ASYNC |
| `challenge.evaluate` | `CHALLENGE_EVALUATE` | ASYNC |
| `hint.generate` | `HINT_GENERATE` | SYNC |
| `review.variant` | `REVIEW_VARIANT` | ASYNC |
| `review.evaluate` | `REVIEW_EVALUATE` | SYNC |
| `evidence.draft` | `EVIDENCE_DRAFT` | ASYNC |
| `requirement.extract` | `REQUIREMENT_EXTRACT` | ASYNC |
| `rubber.duck` | `RUBBER_DUCK` | SYNC |
| `rubber.duck.summary` | `RUBBER_DUCK_SUMMARY` | SYNC |

## 규칙

1. **머지된 버전은 수정하지 않는다.** 한 글자라도 바꾸려면 `v2` 디렉터리를 새로 만든다. `PromptImmutabilityTest`가 hash로 검사한다.
2. 활성 버전은 `devpilot.ai.prompts.<prompt-id>` 설정으로 고른다. 바꾸기 전에 해당 eval suite를 통과해야 한다(`evals/README.md`).
3. `system.md`에는 `{{…}}`, 날짜, ID, 사용자 데이터를 넣지 않는다.
4. `user-template.md`의 placeholder 집합은 문서 §3의 변수 이름과 정확히 같아야 한다. 다르면 기동이 실패한다.
5. user content placeholder(예: `{{content}}`)는 한 줄에 단독으로 둔다. 서버가 `<user_content>` 블록으로 감싸고 태그 문자열을 escape한다.
6. JSON 스키마는 structured outputs로 전달된다. 프롬프트에 스키마를 붙여넣지 않는다.
7. 문체: 짧고 평이한 한국어 지시문. 대문자 강조, "반드시/절대" 반복, 같은 경고의 반복을 쓰지 않는다. 강제해야 하는 규칙은 서버 가드(§6)가 맡는다.
8. 출력 언어 규칙(설명은 한국어, 식별자·API·기술 용어는 영어 원문)은 모든 `system.md`에 있어야 한다. `<user_content>` 데이터 취급 규칙("입력 데이터" 절)은 user content 입력이 있는 operation의 `system.md`에만 둔다. `challenge.generate`는 user content 입력이 없으므로 이 절이 없다.

## 새 버전 만들기

1. 이전 버전 디렉터리를 복사해 `vN+1`을 만들고 수정한다.
2. `src/test/resources/prompt-hashes.json`에 새 파일 hash를 추가한다.
3. `./gradlew aiEval -PevalSuite=<suite>`를 실행하고 합격 기준을 확인한다.
4. PR에 `ai-eval` label을 붙이고, 합격 요약 코멘트가 달린 뒤 설정의 활성 버전을 바꾼다.
