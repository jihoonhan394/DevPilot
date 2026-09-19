# 16. Definition of Ready / Done

> Status: Accepted (v2) · Last updated: 2026-09-18 · Related: `11-development-roadmap.md` §7, `12-acceptance-criteria.md`, `13-product-backlog.md`, `08-coding-conventions.md`, `09-test-and-quality.md`
>
> BL 작업을 시작해도 되는 조건(DoR)과 끝났다고 말할 수 있는 조건(DoD)의 **유일한 기준**이다. 다른 문서의 체크리스트는 이 문서를 참조한다. 사람과 에이전트에 똑같이 적용한다.

---

## 1. Definition of Ready (BL 착수 조건)

모든 항목이 "예"여야 에이전트에게 작업을 지시하거나 직접 착수한다. 하나라도 "아니오"면 먼저 문서를 보강하거나 결정을 기록한다.

| # | 항목 | 확인 방법 |
|---|---|---|
| R-1 | `13-product-backlog.md`에 BL ID, 우선순위, Sprint, 선행 BL이 있다 | 백로그 행 |
| R-2 | 선행 BL(Depends on)이 모두 main에 merge되어 있다. Spike가 선행이면 결과 ADR이 `Accepted`다 | PR·ADR 상태 |
| R-3 | 연결된 AC가 `12-acceptance-criteria.md`에 있고, 이번 BL이 통과시킬 시나리오(`AC-xx Sn`)를 특정했다 | 작업 지시의 `[완료 조건]` |
| R-4 | 사용자 가치 또는 기술 목적을 1문장으로 말할 수 있다 | 작업 지시의 `[목표]` |
| R-5 | API 변경이면 `05-api-spec.md`에 endpoint, request/response record, 검증, 오류 코드가 있다 | 해당 절 |
| R-6 | DB 변경이면 `database/schema.sql`과 `04-domain-model-and-db.md` §10 migration 계획에 테이블·컬럼·제약이 있다 | schema diff |
| R-7 | 규칙 구현이면 `06-learning-engine-rules.md`에 식과 test vector가 있다. 없으면 "(DoR 미충족)" 표시 후 문서 PR 먼저 | 해당 절 |
| R-8 | 새 이름이 모두 `03-system-architecture.md` §3(클래스)와 `15-glossary.md` §6(도메인 용어)에 있다 | 이름 대조 |
| R-9 | 보안 영향(인증·인가·입력 검증·로그·secret·외부 전송)을 식별했고, 필요한 통제가 `07-security-and-privacy.md`에 있다 | 작업 지시의 `[제약]` |
| R-10 | AI 기능이면 결정적 부분과 AI 부분이 나뉘어 있고, operation·입력·출력 스키마·가드·실패 동작이 `17-ai-integration.md`에 있다 | 해당 절 |
| R-11 | 외부 서비스·새 라이브러리가 필요하면 결정(ADR 또는 DEC)이 있고, 실패 시 동작이 정의되어 있다 | ADR |
| R-12 | 화면 작업이면 SCR ID, 상태(loading/empty/error/AI unavailable), 문구가 `02-user-scenarios-and-ux.md`에 있다 | 해당 화면 |
| R-13 | 규칙 클래스(`06`의 test vector가 있는 것)를 만드는 작업이면, vector를 옮긴 `@ParameterizedTest`가 같은 PR에 있거나 먼저 merge되었다 | PR |
| R-14 | 작업 크기: 에이전트 지시 1회에 BL 최대 3개, 변경 파일 예상 30개 이하. 넘으면 BL을 나눈다 | 계획 |

---

## 2. Definition of Done (BL 완료 조건)

### 2.1 체크리스트

**코드·테스트**
- [ ] 연결된 AC 시나리오가 자동 테스트로 구현되어 통과한다. 테스트 이름 또는 `@DisplayName`에 `AC-xx Sn`이 있다(수동 시나리오는 체크 결과를 PR에 첨부)
- [ ] 규칙 구현이면 `06-learning-engine-rules.md`의 test vector가 `@ParameterizedTest`로 **전부** 통과한다. vector를 수정·삭제하지 않았다
- [ ] 사용자 소유 리소스 endpoint를 추가했으면 사용자 격리 테스트 목록(AC-08 S1)에 넣었다
- [ ] 상태 전이를 추가했으면 허용·불허 전이(409 `INVALID_STATE_TRANSITION`) 테스트가 있다
- [ ] 시간이 관여하면 `MutableClock`으로 plan-day 경계를 테스트했다. `Thread.sleep` 없음
- [ ] AI를 쓰면 `FakeAiProvider` fixture로 성공·실패(unavailable, timeout, refusal, invalid output)·예산 차단 경로를 테스트했다. 실제 API를 호출하는 테스트가 PR CI에 없다
- [ ] 테스트를 통과시키려고 assertion을 약화하거나 테스트를 삭제·`@Disabled`하지 않았다

**품질 게이트**
- [ ] `./gradlew spotlessCheck check` 통과 (Checkstyle, PMD, SpotBugs, JaCoCo gate, ArchUnit 포함)
- [ ] ArchUnit 규칙(`08-coding-conventions.md`의 ARCH-*)을 우회하는 예외·suppress를 추가하지 않았다. 추가가 필요하면 ADR
- [ ] Flutter 변경이면 `dart format` 변경 없음, `flutter analyze` 0건, `flutter test` 통과
- [ ] 새 의존성이 없다. 있으면 ADR/DEC와 라이선스·유지보수 상태 확인 기록이 PR에 있다

**DB·API·콘텐츠**
- [ ] DB 변경은 새 Flyway migration으로만 했다. **적용된 migration 파일은 수정하지 않았다**
- [ ] migration 추가 시 `database/schema.sql`을 같은 PR에서 갱신했고, 빈 DB → 전체 migration → `ddl-auto=validate` 테스트가 통과한다
- [ ] 파괴적 변경(컬럼 삭제·타입 축소)은 2단계 배포 절차를 따랐다
- [ ] API 변경 시 `docs/api/openapi.yaml` 스냅샷을 갱신했고(CI diff 통과), `05-api-spec.md`와 Dart 모델을 같은 PR에서 맞췄다
- [ ] enum 값을 추가했으면 `04-domain-model-and-db.md` §3, DB CHECK, Java, Dart, OpenAPI가 모두 같다
- [ ] seed 콘텐츠 변경은 `ContentValidator`와 CI content job을 통과했고 `catalog_version`을 올렸다

**보안·개인정보**
- [ ] 보안 영향 검토 결과를 PR의 `Security impact`에 적었다(없으면 "없음"과 이유)
- [ ] 조회 조건에 항상 `userId`가 있다. 타 사용자 리소스는 404다
- [ ] 오류 응답에 stack trace·SQL·클래스명·내부 메시지가 없다(`detail`은 `messages_ko.properties` 문구)
- [ ] 로그에 Authorization 헤더, 토큰, 이메일 원문, 요청·응답 body, 사용자 코드, AI 프롬프트·응답 원문, secret이 없다. 사용자 식별은 `userRef`만
- [ ] 사용자 자유 입력을 저장·전송하는 경로는 `SecretMasker`를 거친다(`17-ai-integration.md` 적용 위치)
- [ ] 저장소에 실제 secret이 없다(gitleaks 통과, `.env.example`은 키 이름만)

**AI**
- [ ] prompt·모델·가드·출력 스키마를 바꿨으면 해당 eval suite를 실행했고 합격 기준 충족 결과 요약을 PR에 첨부했다(`ai-eval` label)
- [ ] prompt 파일은 새 버전 디렉터리로 추가했고 기존 버전을 수정하지 않았다
- [ ] AI 호출이 트랜잭션 밖에 있다(ArchUnit T-2 통과)

**문서**
- [ ] 구현이 문서와 다르면 문서를 먼저 고쳤다(같은 PR 또는 선행 PR). 코드를 문서에 맞추지 않은 채 남긴 차이가 없다
- [ ] 결정이 생겼으면 ADR(`Proposed`)을 추가했다. 설정값·운영 절차가 바뀌었으면 `03-system-architecture.md` §9 또는 `10-deployment-and-operations.md`·runbook을 갱신했다
- [ ] 사용자에게 보이는 한국어 문구(화면, 오류 `detail`, 템플릿 reason)를 사용자가 검토했다(`02-user-scenarios-and-ux.md` 문구 톤)

**배포·확인** (S0 이후 모든 BL)
- [ ] main merge 후 다음 `v*` 배포에서 prod health `UP`
- [ ] 이 BL의 AC 시나리오 중 prod에서 확인 가능한 것(화면 흐름, 헤더, 권한)을 prod URL에서 확인했다. 확인 결과는 Sprint 데모 기록에 남긴다
- [ ] 운영 영향(새 env 변수, job, 외부 설정)이 있으면 서버 `/opt/devpilot/api.env`와 `.env.example`에 키를 추가했고 `10-deployment-and-operations.md` §6 매트릭스를 갱신했다

### 2.2 완료 보고 형식

에이전트와 사용자 모두 PR 본문에 이 형식으로 적는다.

```text
Changed:          변경 파일과 요약 (BL-ID별)
Tests:            추가·수정한 테스트 클래스, 대응 AC 시나리오, 실행 결과 (통과 수 / 전체)
Security impact:  인증·인가·입력 검증·로그·secret·외부 전송 영향. 없으면 "없음 — 이유"
Docs updated:     갱신한 문서·절, OpenAPI 스냅샷·schema.sql·migration 변경 여부, ADR 번호
Decisions needed: 사용자 결정이 필요한 항목 (선택지, 권장안, 영향)
Known limitations: 남은 제약, 의도적으로 뺀 범위, 후속 BL 후보
```

---

## 3. PR 체크리스트

PR 템플릿(`.github/pull_request_template.md`)에 그대로 넣는다.

```markdown
## BL / AC
- BL: BL-XXX-nn
- AC: AC-xx S1, S2 …

## 체크
- [ ] 제목이 Conventional Commits 형식 (`feat(today): …`, `test(review): …`, `docs: …`)
- [ ] PR 하나에 BL 최대 3개, 서로 관련된 변경만 포함
- [ ] DoD §2.1 전 항목 확인 (해당 없음은 "N/A — 이유")
- [ ] CI 필수 check green (`security`, `backend`, `app`, `content`)
- [ ] 새 endpoint를 사용자 격리 테스트 목록에 추가 / 해당 없음
- [ ] migration 추가 시 schema.sql 갱신 / 해당 없음
- [ ] OpenAPI 스냅샷·Dart 모델 동기화 / 해당 없음
- [ ] prompt·모델·가드 변경 시 eval 결과 첨부 / 해당 없음

## 완료 보고
Changed:
Tests:
Security impact:
Docs updated:
Decisions needed:
Known limitations:
```

리뷰어(사용자) 확인 항목:
- [ ] 이름이 명명 사전과 같다
- [ ] 트랜잭션 경계, N+1, 락 범위를 설명할 수 있다
- [ ] 테스트가 실제로 실패할 수 있는 assertion을 가진다(항상 통과하는 테스트 아님)
- [ ] 범위 밖 변경이 없다

---

## 4. Sprint Definition of Done

단계(Sprint 열의 `S0`~`S7`)를 끝낼 때 확인한다. 단계에는 기간·날짜가 없고 exit criteria(`11-development-roadmap.md` §3)를 통과하면 끝난다(`13-product-backlog.md` §2). M1(S0~S3)의 마지막 단계인 S3 완료가 실사용 시작이다.

| # | 항목 | 증거 |
|---|---|---|
| S-1 | Sprint의 모든 P0 BL이 DoD를 충족해 merge되었다. 미완료 P1은 이월 여부를 `13-product-backlog.md` §1 규칙으로 결정했다 | 백로그 상태, 이월 기록 |
| S-2 | Sprint exit criteria의 AC가 CI에서 통과한다 | CI 실행 링크 |
| S-3 | `v*` tag를 prod에 배포했고 health `UP`, 자동 롤백 경로가 동작한다 | 배포 job 요약 |
| S-4 | Sprint 데모 체크리스트(`11-development-roadmap.md` §3)를 **prod URL**에서 모두 통과했다. 실패 항목은 원인과 후속 BL을 기록했다 | 데모 기록(날짜, 기기, 결과) |
| S-5 | 새로 생긴 운영 작업·장애 대응이 runbook에 있다. 기존 runbook은 이번 변경으로 틀린 곳이 없다 | runbook diff |
| S-6 | Spike·결정 결과가 ADR에 반영되었다. `Proposed` ADR이 남아 있으면 다음 단계에 착수하기 전에 결정한다 | ADR 목록 |
| S-7 | 리스크 레지스터(`20-decisions-and-risks.md` §4)의 조기 신호를 점검하고 상태를 갱신했다 | 갱신 커밋 |
| S-8 | 회고 3줄(잘된 점, 문제, 다음 Sprint 바꿀 것)과 사용자 실제 투입 시간을 기록했다. S1·S3 종료 시 재추정(`11-development-roadmap.md` §2.2)을 했다 | 회고 기록 |
| S-9 | 백업: S2 이후 일일 자동 백업이 그 단계를 진행하는 동안 실패 없이 실행되었고(실패 시 조치 기록), S6 이후에는 월 1회 복구 리허설 기록이 있다 | 백업 로그, 리허설 기록표 |
| S-10 | AI 비용(S3 이후): 이번 Sprint 비용 합계와 월 예산 대비 비율을 기록했다 | `GET /me` aiUsage 또는 `ai_call_log` 집계 |
