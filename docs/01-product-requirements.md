# 01. Product Requirements (PRD)

> Status: Accepted (v3) · Last updated: 2026-09-20 · Related: DEC-01, DEC-05, DEC-06, DEC-16, DEC-18, DEC-27, DEC-28, DEC-29, DEC-30, DEC-31, ADR-039, ADR-040, `02-user-scenarios-and-ux.md`, `04-domain-model-and-db.md`, `06-learning-engine-rules.md`, `11-development-roadmap.md`, `12-acceptance-criteria.md`
>
> 이 문서는 **무엇을, 왜, 어느 단계(S0~S7)에** 만드는지 정의한다. 알고리즘 수치는 `06-learning-engine-rules.md`, enum·상태 전이는 `04-domain-model-and-db.md`, 요청·응답 DTO는 `05-api-spec.md`, 화면은 `02-user-scenarios-and-ux.md`가 기준이다. 여기서 ID(FR, NFR)를 정의하고 다른 문서는 이 ID로 참조한다.
>
> **v3 (2026-09-18)** — DevPilot은 **학습 도구**다. 핵심 루프를 "읽는다 → 만든다 → 설명한다 → 반복한다 → 증거가 된다"로 다시 세웠고(§1.2, §5), 러버덕(FR-25)·사이드 프로젝트(FR-26)·코드 읽기(FR-27)를 더했다. 문서에 고정 날짜를 두지 않는다 — 목표일은 사용자가 설정에 등록하는 값이고, S0~S7은 기간이 없는 구현 단계다(§7).

---

## 1. 제품 개요 · 문제 정의

### 1.1 한 줄 정의

DevPilot은 **사용자가 스스로 정한 목표일까지 실무에 쓰이는 개발 실력을 올리도록, 검증된 오픈소스를 읽고 → 같은 패턴을 사이드 프로젝트에 만들고 → AI에게 설명(러버덕)하고 → 막힌 곳을 반복하게 하는 개인용 개발 학습 코치**다. 남은 시간·현재 실력·학습 기억·프로젝트 경험을 계속 다시 계산해 매일 가장 가치 높은 학습 1개를 안내한다.

| 항목 | 내용 |
|---|---|
| 제품명 | DevPilot |
| 성격 | **학습 도구.** DevPilot 자체는 사용자의 사이드 프로젝트가 아니다. 학습의 결과물은 사용자의 사이드 프로젝트(기본: 주문 시스템, FR-26)이고, 그것이 실력이 늘었다는 근거가 된다 |
| 무엇을 공부하나 | 실무 시스템이 공통으로 갖는 것 — 회원가입·로그인·상품 CRUD·주문·취소·트랜잭션·조회 성능. 학습 순서는 과목이 아니라 **주문 시스템을 만드는 순서**다(계획 템플릿 9개 milestone, FR-04) |
| 공부 방식의 중심 | **AI와의 러버덕 코딩**(FR-25). AI는 답을 주지 않고 되묻는다 |
| 학습 목표 | **무엇을, 언제까지** — 학습 트랙(`targetRole`)과 **목표일**(`targetCompletionDate`) 하나다. 사용자가 설정의 "학습 목표"에 직접 등록한다(FR-03). 목표일로 남은 시간을 역산해 촉박하면 필수 위주로, 여유 있으면 깊이 있게 안내한다(FR-05) |
| 형태 | 웹 우선 Flutter PWA + Spring Boot backend + AI 코칭 (Android 앱은 Later, DEC-18) |
| 학습 트랙 | Java 백엔드 (`TargetRole.JAVA_BACKEND`만 지원. 다른 트랙은 값 추가 + 콘텐츠 작업, C-7) |
| 사용자 | 소유자 1명 + 초대 최대 2명 (DEC-01) |
| UI 언어 | 한국어 (식별자·기술 용어는 영어 원문) |

### 1.2 문제 정의

사용자는 스스로 정한 학습 목표와 공부 의지가 있지만 다음 문제를 겪는다.

| # | 문제 | DevPilot의 대응 (FR) |
|---|---|---|
| P-1 | 퇴근 후 시간이 부족하고 피로해서 **학습 시작 자체**가 어렵다 | Today 1개 과제·15분 모드(FR-07), 복귀 모드(FR-21), 캘린더 구독(FR-20) |
| P-2 | 무엇을 먼저 공부해야 할지 매일 결정하기 어렵다 | 결정적 planner와 이유 표시(FR-07) |
| P-3 | 학습 내용을 빠르게 잊는다. 같은 기술을 몰아서 복습하면 그 자리에서만 쉽게 느껴진다 | spaced review + 교차 학습(FR-11, RV-INTERLEAVE) |
| P-4 | AI에게 코드를 맡기면서 원리 이해와 독립 해결력이 약해질 수 있다 | **러버덕 — AI가 답 대신 되묻는다**(FR-25), self-explain 선행, Hint Ladder(FR-09, FR-10) |
| P-5 | 자원 관리, 예외, null, 보안, 로그, 트랜잭션, 성능 문제를 스스로 인지하기 어렵다 | self-review 선행 Project Coach, thinking pattern(FR-12, FR-13) |
| P-6 | 현재 실력이 공부하려는 로드맵·기술 목록과 얼마나 맞는지 판단하기 어렵다. 자기평가("Java 3점")는 부정확하다 | 온보딩 짧은 진단(FR-02, FR-15), 증거 기반 skill state(FR-06), 로드맵 비교(FR-19) |
| P-7 | 스스로 정한 목표일이 있어 모든 기술을 완벽히 공부할 수 없다. 반대로 여유가 있을 때는 무엇을 더 깊이 할지 모른다 | 기한 역산 양방향 — 촉박하면 필수 위주 축소, 여유 있으면 깊이 확장 제안(FR-05) |
| P-8 | 공부한 내용이 내 말로 설명할 수 있는 형태로 남지 않아, 무엇을 왜 그렇게 했는지 나중에 되짚기 어렵다 | 사이드 프로젝트 결과물(FR-26), 러버덕으로 설명해 본 기록(FR-25), 학습 기록 정리 (STAR) export(FR-18) |
| P-9 | 교재 예제는 너무 간단해서 **실무 코드가 어떻게 생겼는지** 모른다 | 검증된 오픈소스의 파일 1개·줄 범위 1개 읽기(FR-27) |
| P-10 | AI가 거들어 풀린 것을 "이해했다"로 착각한다 | 며칠 뒤 **AI 없이 혼자 다시 만드는 재현 과제**(FR-28) |
| P-11 | 프로젝트에서 왜 그렇게 정했고 무엇이 터졌는지 며칠 뒤면 흐려진다 | 프로젝트 **결정·장애 기록**(FR-29) |
| P-12 | 수준이 다른 사람과 같은 계획을 쓰면 한쪽에는 너무 어렵거나 너무 쉽다 | 학습자마다 **학습 트랙**을 고른다(FR-03) |
| P-10 | 개념을 배우고 연습문제를 풀어도 **직접 만들어 보지 않아** 쓸 줄 모르고, **남에게 설명해 보지 않아** 어디를 모르는지 모른다 | 사이드 프로젝트 과제(FR-26), 러버덕(FR-25) |

v2까지의 학습 루프는 **개념 → 연습문제 → 복습**이었다. 이 루프에는 "만든다"와 "설명한다"가 비어 있었다(P-9, P-10). v3는 루프를 **읽는다 → 만든다 → 설명한다 → 반복한다 → 증거가 된다**로 다시 세운다(§5).

### 1.3 제품 목표 문장

사용자가 등록한 목표일과 현재 실력을 바탕으로 학습 계획을 재편하고, 매일 가장 가치 높은 학습을 제시하며, 검증된 오픈소스 읽기·사이드 프로젝트 구현·러버덕 설명·반복 회상을 통해 **AI 도움 없이 문제를 인지하고 해결하고 설명하는 능력**을 높인다.

---

## 2. 목표 · 비목표

### 2.1 목표

| ID | 목표 | 확인 방법 |
|---|---|---|
| G-1 | 사용자가 매일 "무엇을 할지" 고민하지 않고 30초 안에 학습을 시작한다 | Today 생성 → 시작까지 화면 전환 2회 이하 (`02` §4.2) |
| G-2 | M1 완료(S3 완료 = 실사용 시작) 후 실제 학습에 매일 사용한다 | S3 완료 시 배포 환경에서 AC-02, AC-05, AC-10, AC-11, AC-26, AC-28 통과 |
| G-3 | 학습 결과가 자기평가가 아니라 **증거**로 skill state에 반영된다. 시작점도 자기평가보다 진단이 우선이다 | AC-09, AC-11, `06` §7 test vectors |
| G-4 | 도움(hint) 사용량이 줄고 스스로 발견한 위험이 늘어나는 추세를 사용자가 확인한다 | §6 지표 |
| G-5 | 사용자가 등록한 목표일로 역산한 위험을 숫자 근거와 함께 보고, 촉박하면 무엇을 미룰지, 여유 있으면 무엇을 더 깊이 할지 고른다 | AC-03, AC-30 |
| G-6 | 사용자가 등록한 목표일 전에 직접 해결한 문제를 STAR 형식 학습 기록으로 정리해 Markdown으로 내보낸다 | AC-21 |
| G-7 | AI 비용을 월 USD 3 이하로 통제하고, AI가 없어도 핵심 학습(계획·Today·복습·기록)은 계속된다 | AC-12, AC-13 |
| G-8 | 매주 검증된 오픈소스를 읽고, 같은 패턴을 사이드 프로젝트에 만들고, 그것을 AI에게 설명한다. 설명하다 막힌 곳은 복습 카드가 된다 | AC-26, AC-27, AC-28, §6 러버덕 지표 |

### 2.2 비목표

| # | 비목표 | 근거 |
|---|---|---|
| NG-1 | IDE 자체 제작, IDE 플러그인 (Later) | 원칙 9 |
| NG-2 | Copilot 대체, 자동 기능 개발 agent | 원칙 3 |
| NG-3 | 상용 LMS, 기업용 source code ingestion platform | 개인용 제품 |
| NG-4 | 실시간 pair programming | 범위 밖 |
| NG-5 | 첫 버전에서 Kafka/Kubernetes/MSA 학습 플랫폼 구현 | 범위 밖 |
| NG-6 | **달성 확률, 적합도 점수, 퍼센트 매칭 제공** | 근거 없는 수치가 결정을 왜곡함. 로드맵 비교는 `READY/STRETCH/LATER`만 (FR-19) |
| NG-7 | **MVP에서 사용자 코드 실행**(compiler/test runner). `VERIFIED`는 curated source만 가능 | `06` §10. code runner는 Later |
| NG-8 | **외부 문서 자동 수집·크롤링, URL 본문 fetch** (로드맵 비교의 `sourceUrl`, 사이드 프로젝트 `repoUrl`, 큐레이션 저장소 포함) | 서버는 외부 URL을 가져오지 않는다. 로드맵·기술 목록은 사용자가 본문을 붙여넣고(FR-19), 오픈소스 코드는 사용자가 로컬에 clone해 IDE로 읽는다(FR-26, FR-27) |
| NG-9 | 팀·다중 사용자 협업, 사용자 간 데이터 공유 | DEC-01 |
| NG-10 | 공개 가입, 불특정 다수 서비스 | allowlist (FR-01) |
| NG-11 | 연속 학습일(streak), 순위, 배지 수집 같은 게임화 | 원칙 "guilt UI 금지" (`02` §1) |
| NG-12 | 이메일·Web Push 알림 (MVP) | 캘린더 구독으로 대체 (FR-20) |
| NG-13 | 네이티브 Android/iOS 앱 (MVP) | DEC-18 |
| NG-14 | 나이·성별 등 개인 속성을 계산에 사용 | §3.3 |
| NG-15 | **사이드 프로젝트를 대신 만들어 주기, 사이드 프로젝트 코드의 저장·빌드·호스팅** | DevPilot은 학습 도구다. 결과물은 사용자의 사이드 프로젝트이고 DevPilot은 그 이름·설명·저장소 주소만 기록한다(FR-26). DevPilot 구현 자체는 학습 과제도 증거도 아니다 |
| NG-16 | **AI가 정답·해설·수정 코드를 먼저 주는 튜터** | 러버덕에서 AI는 되묻기만 한다(FR-25, 원칙 3). 답에 가까운 도움은 사용자가 요청한 만큼만 Hint Ladder로 준다(FR-10) |

---

## 3. 사용자 · 페르소나

### 3.1 사용자 수 전제 (DEC-01)

- 사용자는 **소유자 1명 + 초대 최대 2명**이다. backend allowlist(`devpilot.security.allowed-emails`/`allowed-subjects`)로만 접근을 허용한다.
- 사용자 간 데이터 공유, 조회, 비교 기능은 없다. 모든 데이터는 `user_id`로 격리한다(I-15).
- **두 사람이 같은 주제의 사이드 프로젝트를 공부해도 계정은 완전히 분리된다.** 서로 다른 학습 트랙(FR-03)을 고를 수 있고, 계획·기록·증거·프로젝트 기록은 공유되지 않으며 상대의 진행 상황을 보는 기능도 없다(`07` §4.3).
- `UserRole.ADMIN`은 운영 작업용이다. MVP에 ADMIN 전용 화면은 없다.

### 3.2 Persona — 목표일을 정해 스스로 공부하는 개발자

| 항목 | 내용 |
|---|---|
| 배경 | 스스로 방향을 정해 공부하는 개발자. Java를 다뤄 본 적은 있지만 현대 Java 백엔드(Spring Boot·JPA·테스트·DB 설계)를 실무 수준으로 다뤄 본 증거는 부족하다. 아는 영역과 모르는 영역이 섞여 있다 |
| 목표 | 설정에 등록한 목표일(예: 2027-04-01)까지 Java 백엔드 학습 트랙의 필수 역량을 실무 수준으로 끌어올린다 |
| 제약 | 주당 학습 시간이 한정되어 있다(평일은 짧게, 주말은 길게). 매일 무엇을 할지 고르는 데 시간을 쓰고 싶지 않다 |
| 주 사용 패턴 | 평일 저녁 30~60분(데스크톱: 코드 읽기·러버덕), 짬 시간 5분 복습(모바일 PWA), 주말 사이드 프로젝트(주문 시스템) 구현·코드 리뷰 |
| 제품에 기대하는 것 | 매일 할 일 1개(FR-07), 이미 아는 기초를 반복하지 않기(온보딩 진단 FR-02·FR-15), 목표일까지 남은 시간으로 무엇을 미루고 무엇을 더할지 결정(FR-05), 막혔을 때 답이 아니라 되묻는 질문과 단계적 힌트(FR-25, FR-10), 실무 코드를 읽고 설명하는 힘(FR-27, FR-25), **AI 없이 혼자 다시 만들 수 있는지 확인**(FR-28), 기초 개념의 반복 회상(FR-11), 따라 만들 수 있는 정석 프로젝트와 그 과정의 기록(FR-26, FR-29), 실무 문제 인지력의 증거(FR-12, FR-18) |

> 같은 사이드 프로젝트 주제를 **개발을 막 시작한 사람**이 함께 공부할 수 있다. 그때는 입문 학습 트랙(`JAVA_BACKEND_STARTER`, FR-03)을 고른다 — 필수 skill이 적고 목표 레벨과 과제 난이도가 낮다. 계정은 서로 완전히 분리된다(§3.1).

### 3.3 속성 사용 규칙

- **나이는 수집하지 않고, skill 계산·planner·로드맵 비교 어디에도 쓰지 않는다.**
- 현재 실력은 자기 신고 프로필이 아니라 짧은 진단(FR-02, FR-15)과 증거 레벨(FR-06)로 잰다. 학습 목표는 학습 트랙과 목표일뿐이다(FR-03).
- 이메일은 allowlist 비교에만 쓰고 DB에 저장하지 않는다(`app_user`에 email 컬럼 없음).

---

## 4. 핵심 원칙

원문 10개 원칙을 유지하고, v2에서 **강제 지점**을 붙였다. 원칙이 충돌하면 번호가 작은 원칙이 우선한다.

| # | 원칙 | 의미 | 강제 지점 |
|---|---|---|---|
| 1 | **Deadline first** | 사용자가 등록한 목표일이 학습 완성도보다 우선한다. 촉박하면 중요한 것 위주로, 여유 있으면 깊이 있게 | budget/risk(`06` §3~4), risk ≥ HIGH면 LATER 제외·MUST 가중(`06` §5.2, §5.5), 양방향 replan 제안(`06` §4.4 축소·확장, FR-05) |
| 2 | **One important thing today** | 하루 화면은 핵심 과업 하나를 중심으로 구성한다 | 활성 main task 1개(I-04), Today 첫 화면(`02` §1) |
| 3 | **Do not replace thinking** | AI는 답을 대신 쓰지 않고 사용자가 먼저 생각하게 한다. 러버덕이 이 원칙의 가장 직접적인 구현이다 | 러버덕 질문만(RD-1, `NoAnswerGuard` — FR-25), self-explanation 선행(HL-2), coach self-review 선행(FR-12), finding에 수정 코드 없음(AC-06), 코드 노출 가드(HL-8) |
| 4 | **Evidence over self-rating** | 실력은 자기평가보다 풀이·설명·코드·재현 가능한 결과로 갱신한다 | 온보딩 진단 우선(FR-02), 자기평가 상한 3·planning level 분리(`06` §7.5), 레벨 변경은 규칙 엔진만(I-12) |
| 5 | **Memory is database** | 장기 사용자 상태는 LLM 대화가 아니라 DB에 저장한다 | AI 대화 미저장(`03` §11) — 러버덕 턴은 AI 맥락이 아니라 학습 기록으로 마스킹본만 저장(RD-6), 모든 판단은 `learning_event` payload 기반(`04` §6) |
| 6 | **Verified when possible** | 도구·검수 근거를 AI 판단보다 우선하고, 근거 수준을 숨기지 않는다 | `VerificationGuard`(`06` §10), DB CHECK(I-07, I-08), 배지 상시 표시(FR-14) |
| 7 | **Adaptive repetition** | 틀리거나 도움을 많이 쓴 항목·설명하다 막힌 곳은 더 짧은 간격으로, 섞어서 다시 낸다 | 최종 등급 조정(`06` §6.1), 복습 카드 자동 생성(`06` §8.3), 러버덕 gaps → 복습 카드(FR-25), 교차 학습(`06` §6.5 RV-INTERLEAVE) |
| 8 | **Learning from real code** | 검증된 오픈소스를 읽고, 같은 패턴을 내 사이드 프로젝트에 만들고, 그 코드와 diff에서 학습 포인트를 찾는다 | 코드 읽기 READ_CODE(FR-27), PROJECT_TASK(FR-26, `06` §5.3), Project Coach(FR-12) |
| 9 | **No IDE reinvention** | IDE를 대체하지 않는다. 붙여넣기로 연결한다 | NG-1, NG-7 |
| 10 | **Privacy by default** | 회사 코드·비밀정보는 보내지 않고, 개인 코드도 전송 전 최소화·마스킹한다 | 동의 체크·secret masking·private key 차단(I-14), 원문 30일 보존(DEC-16), export/삭제(FR-23) |

---

## 5. 제품 루프

**핵심 루프 (v3)** — 제품의 뼈대다. 각 milestone 안에서 이 순서로 돈다(읽기 → 개념 → 구현 → 러버덕 → 복습).

```text
 읽는다        검증된 오픈소스에서 "실무는 이렇게 생겼다"를 본다        코드 읽기 READ_CODE (FR-27)
   ↓
 만든다        같은 패턴을 내 사이드 프로젝트(주문 시스템)에 적용한다    PROJECT_TASK (FR-26), 문제 풀이 (FR-09)
   ↓
 설명한다      러버덕 — AI가 되묻고, 막히는 지점이 드러난다              러버덕 (FR-25) ★ 중심 기능
   ↓
 반복한다      막힌 지점이 복습 카드가 된다 (간격 반복 + 교차 학습)       복습 (FR-11)
   ↓
 증거가 된다   내 말로 설명할 수 있는 형태로 정리된다                      skill state (FR-06), Evidence (FR-18)
   ↓
 혼자 해낸다   며칠 뒤 같은 것을 AI 없이 처음부터 다시 만든다             재현 과제 REDO (FR-28)
```

- "혼자 해낸다"가 마지막에 붙는 이유: 앞의 다섯 단계는 모두 **도움을 받는 상태**에서 일어난다. 실력의 증거는 도움이 없을 때 남는다. 재현 과제는 그래서 며칠을 기다렸다가, AI를 잠근 채 같은 것을 다시 만들게 한다(FR-28).
- "만든다"에서 내린 결정과 겪은 장애는 그 자리에서 **프로젝트 기록**으로 남긴다(FR-29). 나중에 "설명한다"와 "증거가 된다"의 재료가 된다.

- 학습 순서는 과목이 아니라 **주문 시스템을 만드는 순서**다: 기반 다지기 → 회원과 인증 → 상품과 CRUD → 주문 생성 → 취소와 환불 → 조회 성능(1~6 MUST) → 구조 정리 → 배포와 운영 → 설명과 정리(7~9 SHOULD). 기한이 촉박하면 7~9부터 미루거나 줄인다(FR-04, FR-05).
- 이 루프를 **얼마나 깊이** 돌릴지는 사용자가 등록한 목표일로 역산한다(FR-05): 촉박하면 필수(MUST) 위주로 줄이는 안, 여유 있으면 미룬 항목 복원·목표 상향 안을 제안한다.
- 인증/로그인, 동시성·재고 차감, 대용량 조회·인덱스처럼 큐레이션 저장소가 다루지 않는 영역은 "읽는다"를 건너뛰고 직접 구현과 문제로 채운다(FR-27).

**시스템 루프** — 위 루프가 어떤 데이터로 이어지는지 보여 준다.

```text
 Learning Goal · 학습 트랙 + 목표일 (사용자가 설정에 등록, FR-03)
        │
        ▼
 Plan version · Skill target (FR-04) ◀──────────────────────┐
        │                                                    │
        ▼                                                    │
 Study budget · Deadline risk (FR-05)                        │
        │                                                    │
        ▼                                                    │
 Today: main task 1개 + 복습 (FR-07)                         │
        │                                                    │
        ├──▶ 코드 읽기 READ_CODE (FR-27) ───┐                │
        ├──▶ 프로젝트 과제 PROJECT_TASK (FR-26)               │
        ├──▶ Training (FR-09)               │                │
        │      self-explain → Hint Ladder (FR-10) ◀─ RD-3 ─┐ │
        │      → 제출 → 비동기 평가         ▼               │ │
        │                        러버덕 (FR-25) ────────────┘ │
        │                  설명 → AI 질문 → … → 정리          │
        ├──▶ Review (FR-11, 교차 학습) ◀── gaps → 복습 카드   │
        └──▶ Project Coach (FR-12)                           │
               self-review → 분석 → 응답 → hint → close      │
                                         │                   │
                                         ▼                   │
                              Learning Event (append-only, `04` §6)
                                         │                   │
              ┌──────────────────────────┼───────────────────┤
              ▼                          ▼                   │
   Review schedule (FR-11)   Skill state 갱신 (FR-06)        │
   Thinking pattern (FR-13)  Evidence 후보 (FR-18)           │
              │                          │                   │
              └────────────┬─────────────┘                   │
                           ▼                                 │
            Weekly review · Dashboard (FR-16, FR-17)         │
                           │                                 │
                           └── risk 변화 → Replan (축소·확장) ┘
```

| 단계 | 사용자가 하는 일 | 시스템이 하는 일 | AI 필요 |
|---|---|---|---|
| 목표·계획 | 학습 트랙·목표일·시간 입력, 짧은 진단(또는 자기평가), 사이드 프로젝트 등록 | 템플릿 plan(9개 milestone), seed 카드 배정, 진단 문제 제안 | 진단 답안 평가만 |
| Today | 가능 시간·컨디션 선택 | planner 점수, 이유 템플릿 | 없음 |
| 코드 읽기 | 로컬에 clone한 저장소에서 파일 1개·줄 범위를 IDE로 읽는다 | reading 선택(결정적), 읽는 이유·질문·볼 지점 제시. 코드 본문은 다루지 않는다 | 없음 (완료 조건인 설명은 러버덕) |
| 프로젝트 과제 | 같은 패턴을 사이드 프로젝트에 구현하고 이유를 적는다 | 프로젝트 이름을 넣은 과제 제안 | 없음 |
| 러버덕 | 자기 말로 설명하고, 되묻는 질문에 답한다 | 턴마다 질문 1개(가드), 종료 시 정리 1회 → gaps 복습 카드, EXPLANATION 증거 | 필요 (`RUBBER_DUCK`, `RUBBER_DUCK_SUMMARY`) |
| 복습 | 기억해서 답하고 자기평가 | 최종 등급 조정, 다음 due, 교차 학습 출제 순서 | 선택(REVIEW_EVALUATE) |
| Training | 먼저 설명 → 풀이 제출 | 평가, outcome, 복습 카드 | 평가·4단계 이상 hint |
| Coach | 먼저 우려점 작성 → 질문에 응답 | finding 질문, discoveredBy 판정 | 분석·피드백·hint |
| 기록 | — | learning event → skill state, thinking pattern | 없음 |
| 조정 | 주간 회고, replan 결정 | 지표, risk, 축소(defer·목표 낮춤) 또는 확장(복원·목표 올림) 제안 | 없음 |

---

## 6. 성공 지표

- 계산식은 `06-learning-engine-rules.md` §12가 유일한 기준이다. 결과는 정수(bp, milli)다. (예외: 아래 러버덕 지표는 `06` §12에 아직 없어 이 표에 계산 출처를 적었다.)
- 측정 기간: **실사용 시작(M1 완료 = S3 완료) 다음 plan-day부터 8주.** 주간 값은 plan-day 기준 ISO week(월요일 시작) 단위이고, 시작 plan-day가 월요일이 아니면 그다음 월요일부터 ISO week 8개를 센다(부분 주는 판정에서 뺀다). 이 8주의 첫 주를 **1주차**라고 부른다. 주간 값은 `weekly_review.metrics_json`(`04` §5.7)에서 읽는다.
- M2 단계(S4~S7)의 기능이 만드는 지표는 **그 단계가 완료된 다음 주부터** 센다. 단계에는 날짜가 없으므로 8주 창 안에 들어오지 않을 수 있고, 그때는 추적만 한다.
- 지표는 사용자별로 본다. 사용자 간 비교·합산 화면은 만들지 않는다.
- 목표 미달은 제품 개선 신호다. 사용자에게 "목표 미달" 문구로 보여주지 않는다(`02` §9).

| 지표 | 정의 (`06` §12) | 측정 시작 | 초기 목표 (8주) |
|---|---|---|---|
| 주간 완료 세션 수 | `completedSessions` (주간) | 1주차 | **주 4회 이상**을 8주 중 6주 이상 |
| 주간 학습 시간 | `studyMinutes` (주간) | 1주차 | 참고 지표. 완료율 `completionRateBp`(`06` §3.3, 28일) **≥ 6000** |
| Recall 성공률 | `recallSuccessRateBp` | 1주차 | 3주차부터 주간 **≥ 7000** |
| 독립 해결률 | `independentSolveRateBp` | 1주차 (Training은 S3 = M1) | 8주차 28일 값 **≥ 4000** (분모 5 이상일 때 판정) |
| 평균 hint 단계 | `averageHintLevelMilli` | 1주차 | **하락 추세**: 7~8주차 평균 < 3~4주차 평균, 8주차 값 **≤ 2000** |
| 주간 완료 러버덕 세션 수 | 계산 출처: `rubber_duck_session`(`04`) 중 `status = COMPLETED`이고 `completed_at`의 plan-day(`06` §2)가 그 ISO week에 속하는 행 수. `COMPLETED`는 턴이 1개 이상일 때만 생기고(턴 0개 종료는 `ABANDONED`, `05` §9.8), 정리 AI가 실패한 세션(`summary_json = null`)도 센다 — 대화 자체가 학습이므로. `RUBBER_DUCK_COMPLETED` 이벤트 수는 쓰지 않는다(skill이 없는 세션은 이벤트가 없다, RD-7). `06` §12·`04` §5.7에 `completedRubberDuckSessions`로 추가해야 한다 | 1주차 | **주 3회 이상**을 8주 중 6주 이상 |
| 스스로 발견한 위험 | `selfFoundRiskCount` | S4(Project Coach) 완료 다음 주 | 2주마다 **1개 이상** |
| Deadline risk | `riskLevel`, `ratioBp` (주 마지막 snapshot) | 1주차 (budget·risk는 S2부터 계산) | 매주 **MEDIUM 이하** 또는 replan으로 MEDIUM 이하 복귀 |
| 승인된 evidence | `acceptedEvidenceCount` | S6 완료 다음 주 | 사용자가 등록한 목표일(`learning_goal.target_completion_date`)까지 누적 **3개 이상** (8주 범위 밖일 수 있음, 추적만) |
| 약한 thinking 축 | `weakThinkingAxes` | S4 완료 다음 주 | 목표 없음. 상위 3개 축이 Today/Coach 맥락에 반영되는지 확인 |
| 로드맵 항목 준비 비율 | `requirementCoverageBp` | S7 완료 다음 주 | 목표 없음. **사용자 화면에 퍼센트로 표시하지 않는다**(NG-6). 내부 추적만 |

제품 건강 확인(지표 외):
- Today를 생성한 plan-day 수 ≥ 주 5일 (`daily_plan` 수)
- AI 월 비용 ≤ USD 3 (NFR-01)

---

## 7. 범위 (단계별)

S0~S7은 **구현 순서를 나타내는 단계 ID**다. 기간·날짜·일수가 없고, 단계는 exit criteria(`11-development-roadmap.md` §3)를 통과하면 끝난다. 단계는 두 묶음이다.

- **M1 — 쓸 수 있는 최소 (S0 · S1 · S2 · S3)**: 온보딩(학습 트랙 선택·진단·사이드 프로젝트 등록) · 학습 계획 · 기한 역산 · 오늘 할 일 · 코드 읽기 · 러버덕 · 프로젝트 결정·장애 기록 · 복습(교차 학습) · 레벨 갱신 · 로그인 · 배포. **S3 완료 = M1 완료 = 실사용 시작(매일 쓰기 시작).** S2가 끝나면 AI 없이 Today·Review만 먼저 써도 된다(선택).
- **M2 — M1을 쓰면서 필요한 순서로 (S4 · S5 · S6 · S7)**: Coach 코드 리뷰, 주간 리뷰·Dashboard 완성, 캘린더, AI 문제 자동 생성, 증거·export, 로드맵 비교. 단계 순서는 잠정이다.
- 목표일은 사용자가 설정에 등록하는 값(`learning_goal`)이고 단계 진행과 무관하다. 문서에 목표일·실사용 시작 날짜를 상수로 쓰지 않는다.

| 묶음 | 단계 | 목표 | FR (해당 단계에 완성 또는 시작하는 부분) | 주요 AC |
|---|---|---|---|---|
| M1 | S0 | Bootstrap, walking skeleton, 배포, spike | FR-01(로그인 왕복, `GET /me` 401→200) | — |
| M1 | S1 | Identity · Onboarding · Goal · Plan · **사이드 프로젝트** · seed v0 | FR-01(allowlist, JIT), FR-02(목표·시간·자기평가·사이드 프로젝트 등록), FR-03, FR-04(9개 milestone 템플릿, milestone 편집, 최소 replan 저장), FR-06(조회, 자기평가 반영), FR-24, **FR-26(등록·조회·수정·삭제)** | AC-01, AC-11(온보딩·`sideProject`), AC-18, AC-24, AC-27 |
| M1 | S2 | Today · Session · Review(AI 없음, **교차 학습**) · **Budget·Risk·Replan 제안(축소·확장)** · Dashboard 최소 · PWA | **FR-05**, FR-07, FR-08, FR-11(due, 답변, 스케줄, 수동 카드, 교차 학습), FR-16(최소), FR-21, FR-26(PROJECT_TASK 연결) | AC-02, **AC-03**, AC-05, AC-10, AC-17, AC-27(PROJECT_TASK), **AC-29**, **AC-30** |
| M1 | S3 | AI Platform · Training · Skill updater · **온보딩 진단** · **코드 읽기** · **러버덕** · **학습 트랙 2종** · **프로젝트 기록** — 완료 = 실사용 시작 | FR-02(진단 모드, 트랙 선택), FR-03(학습 트랙 2종), FR-06(규칙 갱신, 이력), FR-09(seed challenge 풀이·평가), FR-10(challenge), FR-11(evaluate, variant, 러버덕 gaps 카드), FR-14(guard 기반), FR-15, FR-22, **FR-25**, **FR-27**, **FR-29** | AC-04, AC-09, AC-11(진단), AC-12, AC-13, AC-16, AC-23, **AC-26**, **AC-28**, **AC-32**, **AC-33** |
| M2 | S4 | Project Coach · evals v1 · **재현 과제** | FR-10(coach finding), FR-12(사이드 프로젝트 연결 포함), FR-13(기록), FR-14(finding 배지), **FR-28** | AC-06, AC-07, AC-14, AC-19, **AC-31**, AC-12·AC-23 재검증 |
| M2 | S5 | Weekly review · Dashboard 완성 · **캘린더 구독** · **AI 문제 자동 생성** | FR-09(AI 문제 생성), FR-13(추세), FR-16(완성), FR-17(`projectNoteCount`·`independentRedoCount` 포함), FR-20 | AC-21(weekly) |
| M2 | S6 | Evidence · Export · 계정 삭제 · 백업 · 하드닝 | FR-18(프로젝트 기록 초안 포함), FR-23 | AC-08 재검증, AC-15, AC-20, AC-21(evidence), AC-33(초안 연결) |
| M2 | S7 | 로드맵 비교 | FR-19 | AC-22 |
| — | Later | 확장 | Android 앱, FSRS, code runner, IDE plugin, 데모 모드, 공휴일 반영, 다크 모드, 이메일·Push 알림, **세 번째 이후 학습 트랙**(다른 스택이면 skill tree부터 새로 쓴다, `19` §10.4), 학습 트랙 변경 | — |

v3에서 옮긴 것(`11` §3, `13` §2): Budget·Risk·Replan 제안은 S5 → **S2**(목표일 역산은 MUST이고 risk는 planner 입력 `DEADLINE_RISK_MUST`이므로 Today와 같은 단계다 — 이제 S2부터 `deadline_risk`가 계산된다), 캘린더 구독·AI 문제 자동 생성은 S2·S3 → **S5**(M1에 필요 없다), 진단 제안은 S3 안에서 필수(P0)로 올렸다.

규칙:
- 각 단계 끝에 **배포된 환경에서** 해당 AC를 확인한다.
- 단계에 아직 없는 기능의 화면 진입점은 숨긴다(`02` §2.5 feature flag). "준비 중" 화면을 만들지 않는다.
- NFR은 S0부터 적용한다. 측정 방식은 §9에 있다.

---

## 8. 기능 요구사항

표기:
- **관련 API**는 base `/api/v1` 생략. 인증이 필요한 모든 `POST`는 `Idempotency-Key` 필수(`03` §5.4, 예외: `POST /plans/{planId}/replan/preview`).
- **관련 화면**의 SCR ID는 `02-user-scenarios-and-ux.md` §3에서 정의한다.
- **Sprint** 칸의 값은 §7의 단계 ID(S0~S7)다. 기간이 없다.
- **우선순위**: MUST = 해당 단계 완료 조건, SHOULD = 다음 단계로 넘길 수 있음.
- FR-25~29는 **범위 밖** 줄을 둔다. 규칙 ID는 러버덕 RD-1~RD-7·코드 읽기 RC-1~RC-4·프로젝트 기록 PN-1~PN-4(`06` §9.5), 사이드 프로젝트 SP-1~SP-3(FR-26), 재현 과제 RE-1~RE-8(`06` §5.10)이다. 사이드 프로젝트 규칙 SP-n은 스파이크 SP-1~SP-5(`11` §4)와 다른 것이다.
- 입력 상한은 `05-api-spec.md`가 서버 기준이며, 클라이언트는 `02` §3.2 표로 같은 값을 미리 검사한다.

### FR-01 인증·접근 제어 (devtoken, allowlist, JIT 사용자 생성)

**설명** — 로그인하면 backend가 JWT를 검증한 뒤 allowlist에 있는 사용자만 첫 요청 시 `app_user`로 생성한다. 로그인 방식은 `devpilot.security.auth-mode`가 정한다: **`devtoken`(기본, 운영 포함)** 은 이메일을 입력하면 backend가 서명한 토큰을 발급하고(tailnet 전용, ADR-033), `supabase`(Later)는 GitHub OAuth를 쓴다(DEC-08).

**사용자 스토리** — As a 소유자, I want 내가 초대한 사람만 로그인해 쓰게 하고 싶다, so that 외부인이 AI 비용을 쓰거나 데이터에 접근하지 못한다.

**규칙**
- 지금 로그인 수단은 `devtoken`(allowlist 이메일 입력 → backend 서명 토큰, tailnet 안에서만 닿는다)이다. 공개 전환 전에 공개 인증(GitHub OAuth, `BL-SEC-18`)으로 바꾼다(`11` §3.10). 비밀번호·이메일 매직링크 로그인은 제공하지 않는다.
- backend는 JWT를 JWKS로 검증한다(서명, `iss`, `aud=authenticated`, `exp`/`nbf`, clock skew 60초 — `03` §4.2). 실패는 `401 AUTHENTICATION_REQUIRED`이며 세부 사유를 응답에 넣지 않는다.
- 사용자 프로비저닝은 `03` §4.3 순서를 따른다: `sub`로 조회 → 없으면 allowlist(이메일 소문자·trim 비교 또는 `sub`) 확인 → `INSERT … ON CONFLICT DO NOTHING` → 재조회.
- allowlist에 없으면 `403 USER_NOT_ALLOWED`이고 `app_user`를 만들지 않는다(AC-18). 이미 생성된 사용자도 allowlist에서 빠지면 다음 요청부터 403이다.
- 생성 시 기본값: `displayName` = 이메일 `@` 앞부분(없으면 `사용자`), `timezone=Asia/Seoul`, `dayStartHour=4`, `weekdayStudyMinutes=45`, `weekendStudyMinutes=240`, `role=USER`.
- `status=DELETION_REQUESTED` 사용자는 `GET /me`, `DELETE /me`만 호출할 수 있고 그 외 API는 `403 FORBIDDEN`이다(`03` §4.1, `05-api-spec.md` §1.4.2).
- 다른 사용자의 리소스와 존재하지 않는 리소스는 똑같이 404다(`03` §4.4, I-15).
- 인증된 `/api/v1/**` 요청은 사용자당 분당 120회로 제한한다(`RateLimitFilter`, 초과 시 `429 RATE_LIMITED` + `Retry-After`, `05-api-spec.md` §1.4.3).
- 로그아웃은 클라이언트가 저장한 토큰을 버리는 것이다. backend에 로그아웃 API는 없다.
- 사용자는 최대 3명(DEC-01)이다. allowlist 변경은 환경변수 수정과 재기동으로 한다.

**관련 API** — `GET /me`, `POST /api/v1/dev/token`(devtoken 모드). Supabase 모드의 OAuth redirect·token refresh는 backend API가 아니다
**관련 화면** — SCR-LOGIN, SCR-AUTH-CALLBACK, SCR-NOT-ALLOWED
**AC** — AC-08, AC-18 · **Sprint** — S0(로그인 왕복), S1(allowlist, JIT) · **우선순위** — MUST

### FR-02 온보딩 (목표, 시간, 짧은 진단 우선·자기평가 대체, 사이드 프로젝트 등록, 계획 템플릿, seed 카드 배정)

**설명** — 최초 로그인 사용자가 5단계, 5분 안팎으로 목표(학습 트랙·목표일)·시간·현재 수준 확인 방식·사이드 프로젝트를 정하면 한 번의 요청으로 학습 목표, 활성 plan v1, skill 시작 상태, 사이드 프로젝트, seed 복습 카드를 만든다. 현재 수준은 **짧은 진단이 기본**이다 — 실력을 키우려는 사람에게 자기평가("Java 3점")는 정확하기 어렵기 때문이다(원칙 4). 진단은 새 기능이 아니라 FR-15 진단 challenge를 온보딩 앞으로 당긴 것이다.

**사용자 스토리** — As a 처음 온 사용자, I want 몇 가지만 답하고 짧은 문제 몇 개로 지금 수준을 확인한 뒤 바로 계획과 오늘 할 일을 받고 싶다, so that 첫날부터 설정에 시간을 쓰지 않고 내 수준에 맞는 학습을 시작한다.

**규칙**
- 단계: ① 목표 ② 시간 ③ 현재 수준(짧은 진단 또는 자기평가) ④ 사이드 프로젝트 → 제출 → ⑤ 생성된 계획 확인(+ 진단 시작). ①~④는 클라이언트에만 보관하고, ④에서 `POST /onboarding` 1회로 제출한다. 계획 미리보기 API는 없다.
- ③ **짧은 진단(기본)**: `runDiagnostic = true`, `selfAssessments = []`. 제출 후 서버가 카테고리당 1문제, **최대 5문제**를 제안한다(`suggestedDiagnostics`, `DiagnosticSuggestionService` — `05-api-spec.md` §4.2). 문제는 ⑤ 이후 SCR-DIAGNOSTICS에서 FR-09 흐름으로 푼다. 진단 결과가 `user_skill_state`의 시작점이다(`06` §7.4 `DIAG_PASSED`/`DIAG_FAILED`). 하나도 풀지 않아도 온보딩은 완료이고, 그때는 모든 skill이 4축 0에서 시작한다.
- ③ **진단 건너뛰기 = 자기평가로 대체**: `runDiagnostic = false`, 카테고리 13개 자기평가를 보낸다(v2 방식). 자기평가 3 이상인 카테고리는 이후 FR-15 진단이 제안된다.
- ④ **사이드 프로젝트**(FR-26 SP-1): 기본 이름 "주문 시스템"을 **클라이언트가 채워 보낸다**(서버는 기본값을 만들지 않는다). 사용자는 이름만 바꿀 수 있고, 설명·저장소 주소·스택은 나중에 SCR-PROJECTS에서 고친다. 건너뛸 수 있다(`sideProject = null`) — 건너뛰면 planner가 `PROJECT_TASK`를 제안하지 않는다는 것을 화면에서 알린다.
- 입력 항목:

| 단계 | 필드 | 필수 | 제약 |
|---|---|---|---|
| ① | `displayName` | Y | 1~100자, 기본값 = `GET /me`의 `displayName` |
| ① | `learningGoal.targetRole` | Y | `JAVA_BACKEND` 고정 (화면 "학습 트랙") |
| ① | `learningGoal.targetCompletionDate` | Y | 목표일. 오늘(plan-day) 다음 날 ~ 3년 이내 (화면 빠른 선택: 3개월 후·6개월 후·1년 후·직접 선택) |
| ② | `weekdayStudyMinutes`, `weekendStudyMinutes` | Y | 0~720, 기본 45 / 240 |
| ② | `dayStartHour` | Y | 0~6, 기본 4 |
| ② | `timezone` | Y | IANA ID, 기본 브라우저 값(없으면 `Asia/Seoul`) |
| ③ | `runDiagnostic` | Y | `true` = 짧은 진단(기본, 진단 기능이 켜진 S3 이후 빌드), `false` = 자기평가 |
| ③ | `selfAssessments[]` (`category`, `level`) | `runDiagnostic = false`일 때 1개 이상 | `runDiagnostic = true`면 `[]`(아니면 `MUTUALLY_EXCLUSIVE`). 자기평가 모드에서 클라이언트는 `SkillCategory` 13개를 모두 보낸다(기본 0). 화면 선택지는 0~4(`UNKNOWN`~`PRACTICAL`), 서버 허용은 ≤ 13개·category 중복 금지·level 0~5 |
| ③ | `learningGoal.focusSkillCodes` | N | 최대 10개, 중복 없음, 활성 catalog code |
| ④ | `sideProject` (`name`, `description`, `repoUrl`, `stack`) | N | `null` = 건너뛰기. `name` 1~100자(화면 기본 "주문 시스템"), `description` ≤ 1000자, `repoUrl` ≤ 500자 http/https URL(**저장만, 서버는 fetch하지 않는다**), `stack` ≤ 300자. 온보딩 화면은 `name`과 고정 기본 `description`만 보낸다 |
| — | `useTemplate` | Y | 클라이언트는 항상 `true` |

- 처리는 **한 트랜잭션**이다: `app_user` 갱신(`onboarding_completed_at`) → `learning_goal` → `user_skill_state` 시작 상태(`SelfAssessmentPropagation` — 자기평가 모드면 카테고리 값을 role target이 있는 활성 non-root skill마다 `self_assessed_level`에 전파, 진단 모드면 `self_assessed_level`을 모두 `null`로 둔다. 두 모드 모두 `self_assessment_active=true`, root skill 제외, `05-api-spec.md` §4.1 5단계) → plan 템플릿(9개 milestone, 주문 시스템을 만드는 순서)으로 활성 plan v1과 milestone 생성, `role_skill_target` 전체를 `plan_skill_target`으로 복사(`06` §11.3) → seed 카드를 사용자 `review_item`으로 복사하고 하루 5장씩 due 분산(`06` §6.3, `04` §9) → 오늘 날짜 `plan_progress_snapshot` upsert → `sideProject`가 있으면 `side_project` INSERT(`ACTIVE`, 이름·설명·스택은 마스킹 후 저장). 커밋 후 `suggestedDiagnostics`를 계산한다. 처리 순서의 기준은 `05-api-spec.md` §4.1이다.
- 자기평가는 planning level에만 반영되고 상한은 3이다(`06` §7.5). evidence level은 0에서 시작한다.
- 응답은 `201`이고 `user`, `learningGoal`, `activePlan`(최신 risk 포함), `sideProject`(건너뛰었으면 `null`), `assignedSeedCardCount`, `suggestedDiagnostics`(자기평가 모드이거나 진단 기능 이전 빌드면 `[]`)를 포함한다.
- 이미 완료한 사용자가 다시 제출하면 `409 ONBOARDING_ALREADY_COMPLETED`다.
- 온보딩 전에 허용되는 API: `GET /me`, `PATCH /me`, `POST /onboarding`, `GET /skills/tree`, `GET /me/export`, `DELETE /me`. 그 외 사용자 데이터 API는 `409 ONBOARDING_REQUIRED`다(AC-11).
- 나이, 성별, 소속은 묻지 않는다.

**관련 API** — `POST /onboarding`, `GET /skills/tree?role=JAVA_BACKEND`, `GET /me`, `GET /diagnostics/suggestions`
**관련 화면** — SCR-ONBOARDING (1~5단계), SCR-DIAGNOSTICS
**AC** — AC-11, AC-27 · **Sprint** — S1(목표·시간·자기평가·사이드 프로젝트), S3(진단 모드 — 진단 문제 풀이가 Training·AI 평가에 의존) · **우선순위** — MUST

### FR-03 학습 목표 관리

**설명** — 학습 목표는 **무엇을(학습 트랙), 언제까지(목표일)** 두 가지다. 학습 트랙은 온보딩 1단계에서 둘 중 하나를 고른다 — **Java 백엔드**(`JAVA_BACKEND`)와, 개발을 막 시작한 사람을 위한 **Java 백엔드 입문**(`JAVA_BACKEND_STARTER`). 같은 skill 카탈로그를 쓰되 트랙마다 필수(MUST) skill 수와 목표 레벨, 과제 난이도 기본값이 다르다. 사용자는 학습 트랙, 목표일, 집중 skill을 조회·수정한다. **목표일은 사용자가 직접 등록하는 날짜 하나**이고(온보딩 ①, 설정의 "학습 목표"), DevPilot은 이 날짜로 남은 시간을 역산한다(FR-05). 계획의 마지막 milestone "설명과 정리"(만든 것을 설명으로 정리하고 CS 기초를 채우는 정리 단계)는 목표일 바로 앞에 놓인다(FR-04, `19` §5). 제품·문서에 목표일을 상수로 두지 않는다.

**사용자 스토리** — As a 목표일을 정해 공부하는 사용자, I want 사정이 바뀌면 목표 날짜를 고치고 싶다, so that 계획과 위험 계산이 현실을 따라간다.

**규칙**
- 사용자당 학습 목표는 1개다(I-01). `GET /learning-goal`에서 없으면 `404 LEARNING_GOAL_NOT_FOUND`(온보딩 완료 사용자에게는 발생하지 않는다).
- `PUT /learning-goal`은 전체 교체이며 `version`이 필수다. 불일치면 `409 CONCURRENT_MODIFICATION`.
- 검증: `targetRole`은 `TargetRole` 값(`JAVA_BACKEND` | `JAVA_BACKEND_STARTER`), `targetCompletionDate`는 오늘(plan-day)+1일 ~ +3년(`DATE_OUT_OF_RANGE`), `focusSkillCodes` 최대 10개·중복 없음·고른 트랙에 role target이 있는 활성 catalog code.
- **학습 트랙은 온보딩에서 정하고 이후 바꾸지 않는다.** `PUT /learning-goal`에 다른 트랙을 보내면 `400 VALIDATION_FAILED`(`VALUE_NOT_ALLOWED`)다 — 트랙을 바꾸면 role target·계획 템플릿·skill state 집합이 통째로 달라져 계획과 증거를 이을 수 없다(`19` §10.4).
- 트랙이 정하는 것: role target 파일(필수 skill과 목표 레벨), 계획 템플릿(milestone 구성), planner 난이도 상한과 코드 읽기 진입 문턱(`devpilot.tracks.<트랙>`, `06` §5.3), 진단 제안 범위(FR-15). 그 밖의 규칙(점수, 복습 간격, 레벨 갱신, 기한 역산)은 트랙과 무관하게 같다.
- 두 사용자가 서로 다른 트랙으로 같은 사이드 프로젝트 주제를 공부해도 계정은 완전히 분리된다 — 데이터 공유도, 서로의 진행 상황을 보는 기능도 없다(`07` §4.3).
- `PUT`은 기존 목표만 교체한다. 목표가 없으면 `404 LEARNING_GOAL_NOT_FOUND`이고 새로 만들지 않는다(생성은 온보딩).
- 날짜가 바뀌면 goal은 즉시 저장하고 활성 plan의 `replan_recommended=true`로 표시한다. **자동으로 replan하지 않는다**(`06` §11.1).
- budget horizon은 `targetCompletionDate`다(`06` §3.1). 계획 템플릿 배치의 창도 오늘 ~ 목표일 하나다(`19` §5).
- focus skill은 planner의 `projectNeed` factor다(`06` §5.4).

**관련 API** — `GET /learning-goal`, `PUT /learning-goal`, `POST /onboarding`(`learningGoal.targetRole`), `GET /skills/tree?role=`
**관련 화면** — SCR-LEARNING-GOAL, SCR-SETTINGS("학습 목표" 행), SCR-PLAN, SCR-ONBOARDING(1단계 트랙 선택)
**AC** — AC-01, AC-32 · **Sprint** — S1 (학습 트랙 2종은 S3) · **우선순위** — MUST

### FR-04 학습 계획·milestone·계획 버전

**설명** — 활성 plan은 milestone(기간, priority, 관련 skill)과 계획별 skill 목표(`plan_skill_target`)를 가진다. 구조를 바꾸면 새 plan version을 만들고 이전 version은 조회만 가능하다. 기본 템플릿은 과목 순서가 아니라 **주문 시스템을 만드는 순서**의 9개 milestone이다: ① 기반 다지기 ② 회원과 인증 ③ 상품과 CRUD ④ 주문 생성 ⑤ 취소와 환불 ⑥ 조회 성능(①~⑥ MUST) ⑦ 구조 정리 ⑧ 배포와 운영 ⑨ 설명과 정리(⑦~⑨ SHOULD — 기한이 촉박하면 여기부터 미루거나 줄인다. 자동 제안은 milestone이 아니라 skill 목표 단위다, FR-05). milestone 안에서는 읽기 → 개념 → 구현 → 러버덕 → 복습 순서로 돈다(`content/plan-templates/java-backend.yaml`, `19`).

**사용자 스토리** — As a 사용자, I want 월별 milestone을 보고 진행 상태를 바꾸고, 구조를 바꿀 때는 이전 계획을 남기고 싶다, so that 계획이 어떻게 바뀌어 왔는지 설명할 수 있다.

**규칙**
- 사용자당 `ACTIVE` plan은 1개다(I-02). `SUPERSEDED` plan은 읽기 전용이다. 수정 시도는 `409 PLAN_NOT_ACTIVE`.
- 변경 분류(`06` §11.1):
  - in-place `PATCH`: milestone `status`, `description`, `sortOrder` (+ `version`)
  - 새 version(`POST /plans/{planId}/replan`): milestone 추가·삭제, 날짜·priority·skill 구성 변경, skill target defer/축소
- replan 저장 순서는 `06` §11.2다(기존 plan `SUPERSEDED` → **flush** → 새 plan INSERT). 동시 replan 2건 중 1건만 성공한다(AC-24).
- milestone 제약: 최대 24개, `title` 1~200자, `description` ≤ 2000자, 날짜 오늘−1년 ~ 오늘+3년, `startDate ≤ endDate`, `skillCodes` 최대 30개·활성 catalog code, `sortOrder` 0~10000.
- replan commit의 `reason`은 **필수** 1~1000자(`learning_plan.change_reason`). preview에서는 선택이다.
- milestone ID는 새 version에서 새 UUID다. 과거 `daily_plan`, `learning_task`는 이전 plan/milestone ID를 유지한다.
- `POST /plans`는 활성 plan이 없을 때만 템플릿으로 생성한다. 있으면 `409 ACTIVE_PLAN_EXISTS`.
- version 이력은 `GET /plans?cursor=`로 최신순 조회한다.
- S1에는 replan을 **미리보기 없이** 저장만 한다(milestone 구조 편집용, `acceptedDeferrals`·`acceptedTargetReductions`·`restoredDeferrals`·`acceptedTargetRaises`는 빈 배열). S2에서 미리보기·축소/확장 제안·defer 복원·목표 상향을 붙인다(FR-05).

**관련 API** — `GET /plans/active`, `GET /plans?cursor=`, `GET /plans/{planId}`, `POST /plans`, `PATCH /plans/{planId}/milestones/{milestoneId}`, `POST /plans/{planId}/replan`
**관련 화면** — SCR-PLAN, SCR-REPLAN, SCR-PLAN-HISTORY, SCR-PLAN-VERSION, SCR-ONBOARDING(5단계 계획 확인)
**AC** — AC-01, AC-24 · **Sprint** — S1 · **우선순위** — MUST

### FR-05 기한 역산 양방향 (budget, risk, 축소·defer 제안, 확장 제안)

**설명** — 사용자가 등록한 목표일(FR-03)까지 남은 가능 시간(study budget)과 MUST 목표에 필요한 시간을 비교해 deadline risk를 계산한다. 계획을 바꿀 때 **촉박하면 중요한 것 위주로 줄이는 안**(SHOULD defer, MUST 목표 축소)을, **여유가 있으면 깊이를 더하는 안**(미뤄 둔 SHOULD/LATER 복원, MUST 목표 +1)을 **제안**한다. 두 방향은 배타적이다. 적용은 사용자가 고른다. 이 역산은 MUST다 — risk는 planner 입력(`DEADLINE_RISK_MUST`)이라 Today와 같은 단계(S2)에서 동작한다.

**사용자 스토리** — As a 목표일을 정해 둔 사용자, I want 지금 속도로 그날까지 무엇이 가능한지, 빠듯하면 무엇을 미뤄야 하고 여유가 있으면 무엇을 더 깊이 할 수 있는지 알고 싶다, so that 모든 것을 하려다 전부 놓치지도, 남는 시간을 흘려보내지도 않는다.

**규칙**
- budget: nominal → 28일 완료율 → effective (`06` §3). 완료율 기록이 14일 미만이면 7000bp. horizon은 목표일이다(`06` §3.1).
- required minutes와 risk 4단계(`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`)는 `06` §4.2~4.3. `deferred=true`인 target과 `LATER`는 제외한다.
- 제안 규칙은 `06` §4.4다. 방향은 **한쪽뿐**이다 — 한 응답에 축소·defer 제안과 확장 제안이 동시에 들어 있지 않다.

| risk | `ratioBp` | 제안 | 응답 필드 |
|---|---|---|---|
| ≥ HIGH | — | **축소**: MUST 목표 1단계 낮춤(3 미만으로는 제안하지 않음) → SHOULD defer | `mustTargetReductionSuggestions[]`, `deferSuggestions[]` |
| LOW | ≤ 7000 (여유 30% 이상) | **확장**: 미뤄 둔 SHOULD/LATER 복원(`RESTORE_DEFERRED`) → MUST 목표 +1(`RAISE_TARGET`, 상한 5). 확장 후 비율이 9000bp를 넘지 않는 동안만 | `expansionSuggestions[]` |
| MEDIUM, LOW이면서 7001~8000, 또는 `ratioBp = null` | — | 없음 | 세 목록 모두 `[]` |

- 제안은 저장하지 않고 **자동 적용하지 않는다.**
- `POST /plans/{planId}/replan/preview`는 편집안 기준 budget/risk, `deferSuggestions`, `mustTargetReductionSuggestions`, `expansionSuggestions`, `riskAfterSuggestions`를 반환하고 아무것도 저장하지 않는다(IK 선택). `riskAfterSuggestions`는 MUST만으로 계산하므로 확장 제안 중 목표 상향만 반영된다(복원은 risk 계산에 들어가지 않는다).
- preview 요청도 `acceptedDeferrals`, `acceptedTargetReductions`, `restoredDeferrals`, `acceptedTargetRaises`를 적용한 상태로 계산한다(`05-api-spec.md` §7.7). 클라이언트는 제안 선택을 바꾸면 preview를 다시 호출해 선택 반영 후 risk를 보여준다.
- commit(`POST /plans/{planId}/replan`)은 사용자가 체크한 것만 적용한다(`06` §11.2 7단계): `acceptedDeferrals`(defer), `acceptedTargetReductions`(목표 낮춤 — `newTarget` 0~5, 현재 target보다 낮아야 함, 아니면 `TARGET_NOT_REDUCED`), `restoredDeferrals`(복원 — 확장 제안 `RESTORE_DEFERRED`를 받아들이거나 미뤄 둔 기술을 직접 다시 넣을 때, `deferred=false`, `adjustment=USER_EDITED`), `acceptedTargetRaises`(목표 올림 — 확장 제안 `RAISE_TARGET`, 현재 target < `newTarget` ≤ 5, 아니면 `TARGET_NOT_RAISED`, `adjustment=USER_EDITED`). "3 미만으로 낮추지 않는다"는 제안 생성 규칙이다(`06` §4.4).
- 같은 skill을 defer와 복원에 함께 넣거나, 같은 skill·축을 낮춤과 올림에 함께 넣거나, defer한 skill의 목표를 올리면 `400 VALIDATION_FAILED`(`MUTUALLY_EXCLUSIVE`)다.
- `ProgressSnapshotJob`이 매일 사용자 `dayStartHour`에 snapshot을 upsert한다(`03` §6). replan commit 직후(`06` §11.2 9단계)와 온보딩 직후(`05-api-spec.md` §4.1)에도 upsert한다.
- risk는 Today(`daily_plan.deadline_risk`), Plan, Dashboard에 같은 배지로 표시한다. `ratioBp`는 "필요 시간 ÷ 가능 시간"으로 표시한다(달성 확률이 아니다).
- risk ≥ HIGH면 planner가 MUST를 가중하고 LATER를 후보에서 뺀다(`06` §5.2, §5.5).

**관련 API** — `GET /plans/active/budget`, `POST /plans/{planId}/replan/preview`, `POST /plans/{planId}/replan`, `GET /plans/active`
**관련 화면** — SCR-PLAN, SCR-REPLAN, SCR-DASHBOARD, SCR-TODAY
**AC** — AC-03, AC-30 · **Sprint** — S2 · **우선순위** — MUST

### FR-06 Skill state (4축, 증거 기반 갱신, 변경 이력)

**설명** — skill마다 KNOWLEDGE, IMPLEMENTATION, EXPLANATION, DEBUGGING 4축 레벨(0~5)을 가진다. 레벨은 learning event에 대한 결정적 규칙으로만 바뀌고, 모든 변경은 근거와 함께 이력으로 남는다.

**사용자 스토리** — As a 사용자, I want 내 실력이 왜 이 레벨인지 근거를 보고 싶다, so that 자기평가가 아니라 실제로 한 일로 성장을 확인한다.

**규칙**
- 레벨 의미: `SkillLevel` 0 `UNKNOWN`, 1 `SEEN`, 2 `GUIDED`, 3 `INDEPENDENT_BASIC`, 4 `PRACTICAL`, 5 `TRANSFERABLE`.
- 세 값을 구분해 저장·표시한다: **evidence level**(규칙 결과), **self-assessed level**(온보딩), **planning level**(`06` §7.5, planner·budget 입력).
- 갱신은 `LearningEventRecorded` 동기 처리, 최근 60일 무효화되지 않은 이벤트 payload만 사용, 축당 한 번에 1단계, 24시간 cooldown이다(`06` §7.1~7.3).
- 진단 통과는 예외 규칙이다(`06` §7.4, FR-15). 온보딩에서 진단을 고르면 진단 결과가 시작점이다(FR-02).
- 러버덕 정리에서 막힌 곳 없이 3턴 이상 설명한 세션은 EXPLANATION 축 증거다(RD-5, FR-25).
- 모든 변경은 `skill_state_change(axis, from, to, rule_code, evidence_event_ids)`를 남긴다(I-12). 이력은 최신순 cursor로 조회한다.
- **사용자가 레벨을 직접 수정하는 API는 없다.** 이의가 있으면 학습 활동으로 증거를 만든다.
- AI는 레벨이나 점수를 출력하지 않는다. AI 결과는 rubric `met` 판정까지이고 계산은 서버가 한다(`06` §8).
- 목표 레벨은 활성 plan의 `plan_skill_target`이다.

**관련 API** — `GET /skills/tree?role=JAVA_BACKEND`, `GET /skills/me`, `GET /skills/{skillId}/history?cursor=`
**관련 화면** — SCR-SKILL-TREE, SCR-SKILL-DETAIL, SCR-DASHBOARD
**AC** — AC-09 · **Sprint** — S1(조회·자기평가), S3(규칙 갱신·이력) · **우선순위** — MUST

### FR-07 Today 계획 (planner, 재생성, task 상태)

**설명** — 사용자가 오늘 가능 시간과 컨디션을 고르면 결정적 planner가 main task 1개와 복습 task(있으면)를 만들고 이유를 1~3개 보여준다.

**사용자 스토리** — As a 퇴근한 사용자, I want 시간과 컨디션만 고르면 오늘 할 가장 중요한 일 하나를 받고 싶다, so that 무엇을 할지 고민하는 데 에너지를 쓰지 않는다.

**규칙**
- 입력: `availableMinutes` 5~720, `energyLevel` `LOW|NORMAL|HIGH`, `force`(기본 false).
- "오늘"은 plan-day다(`06` §2). plan-day당 `daily_plan` 1개(I-03), 활성 main task(PLANNED/IN_PROGRESS) 1개(I-04).
- 후보·task 제안·factor·modifier·동점 처리는 `06` §5.2~5.5, 시간 배분은 `06` §5.6이다. AI를 쓰지 않는다.
- task 제안 순서(`06` §5.3): **REDO**(재현 창 안일 때, FR-28) → CHALLENGE → **READ_CODE**(AI 사용 가능 + 해당 skill planning KNOWLEDGE ≥ 트랙 문턱(RC-3) + 선택 가능한 reading이 있을 때, FR-27) → READING → **PROJECT_TASK**(projectNeed + 컨디션 LOW 아님 + `ACTIVE` 사이드 프로젝트가 있을 때만, SP-1·SP-3) → EXPLAIN. 난이도 상한과 코드 읽기 문턱은 학습 트랙이 정한다(FR-03).
- `PROJECT_TASK` 제목·설명에는 사이드 프로젝트 이름과 skill 이름이 들어간다(SP-2, 예: "주문 시스템에 Spring Transaction 적용하기"). task에 `side_project_id`, `READ_CODE` task에 `reading_key`를 생성 시점에 고정한다(`05-api-spec.md` §8.1 `sideProjectId`, `readingKey`).
- `READ_CODE`가 선택되면 이유 `READ_REAL_CODE`("{저장소 이름}에서 같은 문제를 어떻게 풀었는지 먼저 봅니다")가 붙는다(`06` §5.8).
- due 복습이 있고 배분된 `reviewMinutes > 0`이면 `REVIEW` task 1개(`is_main=false`, `sort_order=0`)를 만든다. 가능 시간이 짧아 `reviewMinutes = 0`이면(예: 5분, `06` §5.6) 만들지 않고 `reviewTask = null`이다. 일일 복습 상한은 20장(복귀 모드 10장).
- 이유는 `ReasonTemplates` 규칙으로 **최소 1개, 최대 3개**다(`06` §5.8, AC-02). 문구는 템플릿 그대로다.
- `score_breakdown`은 저장하지만 MVP 화면에는 표시하지 않는다.
- 재생성은 `06` §5.9 표를 따른다: main `IN_PROGRESS`에서 `force=false`면 `409 TODAY_ALREADY_STARTED`, main `COMPLETED`에서 `force=false`면 `409 TODAY_ALREADY_COMPLETED`.
- task 상태 전이는 `04` §4.1이다. 표에 없는 전이는 `409 INVALID_STATE_TRANSITION`.
- 오늘 plan이 없으면 `GET /today`는 `404 TODAY_NOT_GENERATED`이고 클라이언트는 입력 화면을 보여준다.
- 활성 plan이 없으면 `POST /today/generate`는 `404 PLAN_NOT_FOUND`다.
- 후보 skill이 하나도 없으면(모든 목표 달성 등) main task를 만들지 않는다. due 복습이 있으면 REVIEW task만 만들고 `mainTask`는 `null`이다(`06` §5.2). 화면은 계획 조정을 안내한다.
- `aiStatus`가 `DISABLED`·`BALANCE_EXHAUSTED`이면 planner는 `CHALLENGE`(평가가 AI에 의존)와 `READ_CODE`(완료 조건인 러버덕이 AI에 의존) task를 제안하지 않는다(`06` §5.3).
- `READ_CODE` task의 `IN_PROGRESS → COMPLETED`는 그 task를 대상으로 한 러버덕 세션이 `COMPLETED`여야 한다(RC-1, FR-27). 아니면 `409 INVALID_STATE_TRANSITION`.
- `REDO` task의 `IN_PROGRESS → COMPLETED`에는 `redoWithoutAi`(AI 도움 없이 끝냈는지) 답이 필요하다(RE-6, FR-28). 없으면 `400 VALIDATION_FAILED`.
- task 상태와 학습 세션(FR-08)은 별개 리소스다. 클라이언트가 둘 다 갱신한다(`02` §4.2).

**관련 API** — `POST /today/generate`, `GET /today`, `PATCH /today/tasks/{taskId}`
**관련 화면** — SCR-TODAY
**AC** — AC-02, AC-17, AC-27(PROJECT_TASK), AC-28(READ_CODE) · **Sprint** — S2 (READ_CODE 분기는 S3) · **우선순위** — MUST

### FR-08 학습 세션 기록

**설명** — 사용자가 과제를 시작·완료·중단한 시각과 실제 학습 시간, 짧은 회고를 기록한다. 완료율(budget)과 지표의 원천이다.

**사용자 스토리** — As a 사용자, I want 실제로 공부한 시간을 간단히 남기고 싶다, so that 계획이 내 실제 속도에 맞춰진다.

**규칙**
- 사용자당 `IN_PROGRESS` 세션은 1개다(I-05). 새 세션을 시작하면 서버가 기존 세션을 `ABANDONED`로 닫는다. main task "시작" 시 클라이언트는 진행 중 세션의 `learningTaskId`가 이 task와 다르면(세션이 없는 경우 포함) 새 세션을 시작한다(서버가 기존 세션을 닫는다, `02` §4.2). 복습 세션은 진행 중 세션이 있으면 새로 시작하지 않는다(`02` §4.3).
- 시작: `learningTaskId`(선택). `plan_date`는 시작 시각의 plan-day다.
- 완료: `actualMinutes` 0~720이면서 `⌈경과 분 × 1.5⌉` 이하(필수, `05-api-spec.md` §9.2), `selfReflection`(선택, ≤ 5000자). 완료한 세션은 수정하지 않는다.
- 이벤트: `SESSION_STARTED`, `SESSION_COMPLETED`(`04` §6). task의 skill이 있으면 `skill_id`를 채운다.
- 완료율은 COMPLETED 세션의 `actual_minutes`만 합산한다(`06` §3.3).
- 목록은 `from`/`to`(plan date)와 cursor로 조회한다.

**관련 API** — `POST /learning-sessions`, `POST /learning-sessions/{sessionId}/complete`, `POST /learning-sessions/{sessionId}/abandon`, `GET /learning-sessions?from=&to=&cursor=`
**관련 화면** — SCR-TODAY(완료 시트), SCR-REVIEW-SESSION, SCR-TRAINING-ATTEMPT, SCR-DASHBOARD
**AC** — AC-02 · **Sprint** — S2 · **우선순위** — MUST

### FR-09 Training challenge (생성, attempt, 제출, 비동기 평가)

**설명** — skill별 난이도 문제(seed 또는 AI 생성)를 풀면서 먼저 접근을 설명하고, 답안·코드를 제출하면 AI가 rubric 충족 여부를 비동기로 판정하고 서버가 coverage와 outcome을 계산한다.

**사용자 스토리** — As a 사용자, I want 실무형 문제를 스스로 먼저 풀어 보고 무엇이 빠졌는지 확인하고 싶다, so that 문법 암기가 아니라 적용·판단 능력이 는다.

**규칙**
- 난이도 1~5 의미: L1 기본 사용, L2 작은 변형, L3 여러 개념 조합, L4 실무형 시나리오, L5 트레이드오프·디버깅·설계.
- 목록(`GET /challenges`)은 `VALIDATED`이고 seed 공용이거나 본인 소유인 문제만 반환한다. `purpose`로 `PRACTICE`/`DIAGNOSTIC`을 거른다.
- 생성(`POST /challenges/generate`: `skillId`(UUID), `difficulty` 1~5, `targetMinutes` 5~180)은 `202`, `CHALLENGE_GENERATE` 비동기다. 실패·거절 시 재시도 API 없이 새로 요청한다. 결과는 `ChallengeValidationService`의 결정적 검증(I-09, rubric weight 합 10000 — I-10)을 통과하면 `VALIDATED`, 아니면 `REJECTED`다. 1~3단계 hint를 함께 생성해 저장한다.
- attempt 시작은 `VALIDATED` challenge에서만 가능하다(`04` §4.2). 같은 challenge에 `STARTED`/`SUBMITTED` attempt가 있으면 `409 INVALID_STATE_TRANSITION`이고, 클라이언트는 `GET /challenges/{challengeId}`의 `activeAttemptId`(STARTED/SUBMITTED/EVALUATED 최신 1개)로 기존 attempt를 연다. `activeAttemptId`가 null이거나 그 attempt가 `EVALUATED`일 때만 새로 시작할 수 있다.
- **제출 전에 self-explanation(텍스트 1~5000자) 또는 명시적 건너뛰기**가 있어야 한다. 없으면 `409 SELF_EXPLANATION_REQUIRED`. 한 번 기록하면 바꾸지 않는다. 이벤트 `SELF_EXPLANATION_SUBMITTED`/`SKIPPED`. self-explanation은 한 번 쓰고 채점받는 **일방향** 기록이다. 같은 문제를 AI와 **대화**로 설명하려면 러버덕(FR-25, `targetType = CHALLENGE`)을 쓴다.
- 제출: `answerText`(≤ 5000자)와 `code`(UTF-8 20000 bytes 이하) 중 하나 이상, `language`(`CodeLanguage`, code가 있으면 필수). `202`를 반환하고 `CHALLENGE_EVALUATE`를 비동기로 실행한다.
- attempt당 제출은 최대 5회(`devpilot.training.max-submissions-per-attempt`). 초과는 `409 SUBMISSION_LIMIT_REACHED`. 평가 중(PENDING/RUNNING) 제출이 있으면 새 제출은 `409 EVALUATION_IN_PROGRESS`, 최신 제출이 평가 실패(`FAILED`)면 `409 INVALID_STATE_TRANSITION`이다(재평가만 가능).
- 평가 실패(`FAILED`)한 제출만 `POST …/submissions/{submissionNo}/retry`로 재평가한다. 그 외는 `409 AI_TASK_NOT_RETRYABLE`.
- coverage, evaluatedOutcome, attempt outcome은 서버가 계산한다(`06` §8.1~8.2). AI는 rubric `met`, `evidenceQuote`, `misconceptions`, `followUpQuestion`만 낸다(`04` §5.3).
- `GET /challenges/{challengeId}`는 본인 attempt 평가 완료 전에는 `rubric`, `expectedConcepts`를 제외한다.
- 평가 완료 후 outcome이 `FAILED`/`PARTIAL`이거나 `SOLVED_WITH_HINTS`이면서 maxHintLevel ≥ `PSEUDOCODE`면 skill마다 review item을 upsert한다(`06` §8.3). due는 다음 plan-day(`06` §6.3). attempt 응답의 `reviewScheduled`로 화면에 알린다.
- 포기(`POST …/abandon`)는 `04` §4.2 규칙을 따른다.
- **MVP는 코드를 실행하지 않는다**(NG-7). 평가는 AI rubric 판정뿐이다.
- AI 불가·차단이면 문제 보기, self-explanation, 사전 생성 hint(1~3단계)는 동작하고 제출·재평가·AI 생성은 `503 AI_UNAVAILABLE`/`429 AI_*`로 거절된다(AC-12). **자기 채점 같은 대체 평가 경로는 없다.** 클라이언트는 `aiStatus=DISABLED`면 제출 버튼을 미리 막는다.

**관련 API** — `GET /challenges?skillId=&purpose=&cursor=`, `POST /challenges/generate`, `GET /challenges/{challengeId}`, `POST /challenges/{challengeId}/attempts`, `GET /challenge-attempts/{attemptId}`, `POST /challenge-attempts/{attemptId}/self-explanation`, `POST /challenge-attempts/{attemptId}/submissions`, `POST /challenge-attempts/{attemptId}/submissions/{submissionNo}/retry`, `POST /challenge-attempts/{attemptId}/abandon`
**관련 화면** — SCR-TRAINING-LIST, SCR-CHALLENGE-DETAIL, SCR-TRAINING-ATTEMPT, SCR-RUBBER-DUCK(결과 후 설명)
**AC** — AC-04 · **Sprint** — S3(seed challenge 목록·풀이·제출·평가), S5(AI 문제 자동 생성 `POST /challenges/generate`) · **우선순위** — MUST

### FR-10 Hint Ladder

**설명** — 사용자가 요청할 때만 도움을 7단계(`SELF_EXPLAIN` 0 ~ `FULL_EXAMPLE` 6)로 늘린다. 공개한 최대 단계는 outcome, 복습 등급, skill 규칙에 반영된다.

**사용자 스토리** — As a 막힌 사용자, I want 답 전체가 아니라 다음 한 걸음만 힌트로 받고 싶다, so that 최대한 스스로 해결한 경험을 남긴다.

**규칙**
- 서버 정책은 `06` §9.1 HL-1~HL-8이 전부다. 요약:
  - HL-1 이미 공개한 단계 이하 요청은 AI 호출 없이 저장된 내용 반환
  - HL-2 challenge는 self-explanation(또는 skip), coach finding은 응답 또는 `skipSelfExplanation=true`가 선행 → 없으면 `409 SELF_EXPLANATION_REQUIRED`
  - HL-3 여러 단계를 한 번에 올릴 수 있고 건너뛴 단계는 기록
  - HL-4 `PSEUDOCODE` 이상은 `acknowledgeEvidenceImpact=true` 필요 → 없으면 `409 HINT_CONFIRMATION_REQUIRED`
  - HL-5 `FULL_EXAMPLE`은 challenge 제출 1회 이상 또는 `giveUp=true` → 아니면 `409 FULL_EXAMPLE_NOT_ALLOWED`
  - HL-6 challenge 1~3단계는 사전 생성 내용, 4단계 이상과 coach finding 전 단계는 `HINT_GENERATE`(동기, 20초)
  - HL-7 공개마다 `hint_disclosure` + `HINT_DISCLOSED` + `max_hint_level` 갱신
  - HL-8 `DIRECTION` 이하 AI hint에 코드가 있으면 가드가 거절
- **클라이언트는 다음 단계(현재 max + 1) 버튼만 활성화한다.** 서버는 건너뛰기를 허용하지만 UI는 제공하지 않는다(결정 비용 감소, `02` §6.2).
- `PSEUDOCODE` 이상은 확인 대화상자에서 영향("힌트로 해결로 기록, 복습 카드 생성 가능")을 보여준 뒤 요청한다.
- 영향: challenge outcome은 maxHintLevel ≤ `QUESTION_ONLY`일 때만 `SOLVED_INDEPENDENTLY`(`06` §8.2). 복습 등급 상한(`06` §6.1).
- 복습(review item)의 hint는 AI 없이 클라이언트가 처리한다: rubric 첫 항목 공개 = `CONCEPT_HINT`, 답하기 전 정답 보기 = `FULL_EXAMPLE`(`06` §6.1). 별도 API가 없다.
- 공개된 hint 내용은 attempt/finding 조회 응답에 포함되어 다시 볼 수 있다.
- **러버덕과의 연결(RD-3)**: 러버덕에서 2턴 연속 "모르겠다"류로 답하면 `suggestHint=true`가 되고, 대상이 challenge attempt면 그 attempt의 Hint Ladder 다음 단계로 넘어간다(HL-3·HL-4 그대로, `HINT_DISCLOSED` 정상 기록 — 러버덕을 거쳤다고 힌트가 증거 계산에서 빠지지 않는다). 러버덕 턴이 1개 이상이면 HL-2의 자기설명으로 본다(`06` §9.5). 러버덕 전용 hint API는 없다.

**관련 API** — `POST /challenge-attempts/{attemptId}/hints`, `POST /coach/reviews/{reviewId}/findings/{findingId}/hints`, `GET /challenge-attempts/{attemptId}`, `GET /coach/reviews/{reviewId}`
**관련 화면** — SCR-TRAINING-ATTEMPT, SCR-COACH-DETAIL, SCR-REVIEW-SESSION, SCR-RUBBER-DUCK
**AC** — AC-16 · **Sprint** — S3(challenge), S4(coach finding) · **우선순위** — MUST

### FR-11 Memory review (due, 답변, 스케줄, 수동 카드, variant, 교차 학습)

**설명** — seed·수동·challenge·coach·러버덕에서 온 복습 카드를 plan-day마다 상한 안에서 **섞어서** 출제하고, 답변·자기평가·hint·(선택) AI 채점으로 최종 등급과 다음 due를 정한다. 러버덕에서 설명하다 막힌 곳(gaps)이 복습 카드가 되어 핵심 루프의 "반복한다"를 맡는다.

**사용자 스토리** — As a 출퇴근 중인 사용자, I want 휴대폰으로 5분 동안 카드 몇 장을 떠올려 답하고 싶다, so that 배운 내용을 잊기 전에 다시 꺼낸다.

**규칙**
- due 선택·정렬·상한은 `06` §6.5다. 상한 20장, 복귀 모드 10장.
- **교차 학습(RV-INTERLEAVE, `06` §6.5)**: 정렬하고 상한으로 자른 **뒤에** 출제 순서만 재배치한다 — 같은 skill 카드가 3장 연속이 되는 자리에서 뒤쪽의 가장 가까운 다른 skill 카드와 자리를 바꾼다. 카드 집합은 바뀌지 않고, 무작위를 쓰지 않는다(같은 입력 → 같은 순서). 전부 같은 skill이면 그대로 둔다. `GET /reviews/due`의 목록 순서가 이 결과다(AC-29).
- 러버덕 정리의 gaps마다 복습 카드를 만든다(`source_type = RUBBER_DUCK`, `review_type = EXPLAIN`, 문항 = gap의 복습 질문, 첫 due 다음 plan-day). 같은 `concept_key`의 활성 카드가 있으면 새로 만들지 않고 due를 당긴다(FR-25, `06` §6.3).
- `GET /reviews/due`(`limit` 생략 = cap) 응답은 `cap`, `comebackMode`, `totalDueCount`와 카드별 `prompt`, `expectedAnswer`, `rubric`, `wasVariant`, `overdueDays`를 준다. **클라이언트가 답 확인 전까지 숨긴다.** reveal API는 없다.
- `variant_status=READY`면 variant 문항이 출제된다(`wasVariant=true`).
- 답변(`POST /reviews/{reviewItemId}/answer`, 동기): `answerText`(선택, ≤ 5000자. 비어 있으면 클라이언트는 `evaluate=false`로 보낸다 — 서버는 공백 답변 + `evaluate=true`를 AI 호출 없이 `NOT_EVALUATED`로 처리), `selfRating`(`AGAIN|HARD|GOOD|EASY`), `hintLevel`(`SELF_EXPLAIN|CONCEPT_HINT|FULL_EXAMPLE` 중 하나), `responseSeconds`(0~86400), `wasVariant`(due 응답 값 그대로), `evaluate`(S3부터, 기본 false).
- 답변 응답: `finalRating`, `adjustedBy`, `evaluatedOutcome`, `rubricResults`(평가 성공 시), `intervalBefore`/`intervalAfter`, `nextDueDate`, `evaluationSkippedReason`, `status`, `leechDetected`.
- 아직 due가 아니거나 `ACTIVE`가 아닌 카드에 답하면 `409 INVALID_STATE_TRANSITION`, `wasVariant`가 현재 variant 상태와 다르면 `409 CONCURRENT_MODIFICATION`이다(`05-api-spec.md` §11.3).
- 최종 등급은 `06` §6.1, 간격·due는 `06` §6.2(1~60일, horizon cap). 응답의 `adjustedBy`를 화면에 사유로 보여준다.
- `evaluate=true`면 `REVIEW_EVALUATE`(동기, 20초, 재시도 0)를 호출한다. **실패해도 답변은 `NOT_EVALUATED`로 저장**하고 `evaluationSkippedReason`을 준다(`03` §5.3). `answerText`가 비어 있으면 evaluate를 보내지 않는다.
- leech: 연속 실패 4회면 `SUSPENDED` + `LEECH_DETECTED`. 연속 실패 2회 이상이고 AI 사용 가능하면 variant를 비동기 생성한다(`06` §6.4).
- 수동 카드(`POST /review-items`): `skillCode`(필수), `conceptKey`(필수, `^[A-Z0-9_.:-]{3,150}$` — 클라이언트가 `MANUAL:{대문자 UUID v4}`로 만든다), `reviewType`(화면은 `RECALL|EXPLAIN|BUG_SPOT`만 제공), `prompt`(1~2000자), `expectedAnswer`(1~3000자), `rubric`(1~6개, 각 1~500자 — 첫 항목이 복습 힌트). 새로 만들면 `201`, 첫 due는 다음 plan-day(`06` §6.3). `CHOICE`는 보기 필드가 없어 수동 생성 화면에서 제외한다.
- 사용자·개념당 카드 1개(I-06). 같은 `concept_key`가 이미 있으면 새로 만들지 않고 기존 카드를 활성화해 `200`으로 반환한다(문항은 덮어쓰지 않음, `06` §6.3).
- 카드 관리(`PATCH /review-items/{reviewItemId}`): `status`(`ACTIVE`/`SUSPENDED`/`ARCHIVED`, 전이 `04` §4.5), `prompt`(1~2000자), `expectedAnswer`(1~3000자), `version`. rubric은 바꾸지 않고, `ARCHIVED` 카드의 문항은 수정할 수 없다.
- 모든 답변은 `review_answer`에 append-only로 남고 `REVIEW_ANSWERED` 이벤트를 발행한다.

**관련 API** — `GET /reviews/due?limit=`, `POST /reviews/{reviewItemId}/answer`, `GET /review-items?skillId=&status=&cursor=`, `POST /review-items`, `PATCH /review-items/{reviewItemId}`
**관련 화면** — SCR-REVIEW-HOME, SCR-REVIEW-SESSION, SCR-REVIEW-ITEMS, SCR-REVIEW-ITEM-EDIT, SCR-TODAY
**AC** — AC-05, AC-10, AC-29 · **Sprint** — S2(due, 답변, 스케줄, 수동 카드, 교차 학습), S3(evaluate, variant, 러버덕 gaps 카드) · **우선순위** — MUST

### FR-12 Project Coach (self-review, 분석, 응답 피드백, hint, close)

**설명** — 사용자가 개인 프로젝트(주로 사이드 프로젝트, FR-26) 코드·diff·로그를 붙여넣고 **먼저 스스로 우려점을 적은 뒤** 분석을 요청한다. AI는 수정 코드 대신 finding별 학습 질문을 낸다. 사용자는 질문에 답하고, 필요하면 hint를 받고, 각 finding을 수정 완료/해당 없음으로 정리한 뒤 리뷰를 닫는다.

**사용자 스토리** — As a 사이드 프로젝트를 만드는 사용자, I want 내 코드의 위험을 AI가 알려주기 전에 내가 먼저 찾아보고 질문으로 확인받고 싶다, so that 다음 코드에서는 스스로 문제를 인지한다.

**규칙**
- 입력: `contentType`(`CODE|DIFF|LOG`), `language`(`CodeLanguage`, 선택), `content`(≤ 30000 bytes UTF-8, ≤ 1000줄 — `03` §9), `confidentialConsent=true`(필수), `userSelfReview`(선택, ≤ 5000자), `sideProjectId`(선택 — 어느 사이드 프로젝트 코드인지. 본인 프로젝트가 아니면 `400 VALIDATION_FAILED`, code `REFERENCE_NOT_FOUND`. 프로젝트가 삭제되면 `null`이 되고 리뷰는 남는다), `context`(필수: `projectType` 필수, `topic` ≤ 100자, `skillCodes` ≤ 10, `fileName` ≤ 200자 — `04` §5.5, `05-api-spec.md` §12.1). `selfReviewAxes`는 분석 시 서버가 채운다.
- 순서(`03` §5.3): 크기·동의 검증(400/413) → **secret masking**(`content`와 `userSelfReview` 모두. private key 블록이면 `422 SECRET_DETECTED_BLOCKED`, 저장·AI 호출 없음) → 예산 확인(429) → 마스킹본 저장(`PENDING`) → `202 { id, status, maskedSecretCount }` → 비동기 `COACH_REVIEW`.
- self-review는 분석 **요청과 함께** 저장한다. 분석 결과를 본 뒤에는 self-review를 수정할 수 없다(H-04). 비어 있으면 어떤 finding도 `MENTIONED_UNPROMPTED`가 될 수 없다는 점을 제출 전에 알린다.
- finding은 최대 7개(`devpilot.coach.max-findings`)이고 `type`(`BUG|RISK|LEARNING_POINT`), `category`(`ThinkingAxis`), `summary`, `learningQuestion`, 위치(줄 범위), `verificationStatus`, `confidence`, `sourceType`, `sourceReference`, `mentionedByUser`를 가진다. **수정 코드 필드는 없다**(AC-06).
- 클라이언트는 `GET /coach/reviews/{reviewId}`를 2초 간격, 최대 3분 polling한다(`03` §5.3). `RUNNING`이 10분 넘게 멈추면 서버가 `FAILED(INTERRUPTED)`로 정리한다.
- 실패한 리뷰만 `POST …/retry`(202)로 다시 분석한다(`retryable=true`). `failureCode=CONFIDENTIAL_SUSPECTED`이거나 원문이 삭제된 리뷰는 재시도할 수 없고 새 요청을 안내한다.
- finding 응답(`POST …/responses`, 텍스트 1~5000자, 마스킹 적용·private key면 422)은 응답을 먼저 저장한 뒤 동기 `COACH_RESPONSE_FEEDBACK`을 호출해 `aiFeedback`, `userIdentifiedIssue`, `followUpQuestion`을 저장·반환한다(`CoachFindingView.followUpQuestion`, `feedbackAiMeta`로 언제든 다시 조회). **AI 실패·차단 시 응답만 저장**하고 `feedbackSkippedReason`을 반환한다(HTTP 오류 없음). 한 번 `userIdentifiedIssue=true`가 되면 유지된다. finding 상태는 `USER_RESPONDED`(재응답은 최신으로 덮어씀, `04` §4.3).
- hint는 FR-10(coach finding 전 단계 AI). 원문이 삭제된 뒤에는 새 단계를 생성할 수 없다(이미 공개한 단계는 볼 수 있다).
- `PATCH …/findings/{findingId}`로 `RESOLVED`(수정 완료) 또는 `DISMISSED`(해당 없음 = 오탐 신호)로 바꾼다. 되돌리지 않는다.
- `POST …/complete`로 리뷰를 닫는다. 이때 finding마다 `discoveredBy`를 확정하고(`06` §9.3) `COACH_FINDING_CLOSED`, thinking pattern observation, `COACH_REVIEW_COMPLETED`를 기록한다. OPEN finding이 남아 있어도 닫을 수 있다. `findingType ∈ {BUG, RISK}`, skill 있음, `discoveredBy ∈ {MISSED, FOUND_AFTER_HINT}`인 finding은 복습 카드가 된다(`06` §9.4, due 다음 plan-day). 응답은 `CoachReviewCompleteResponse{review, createdReviewItemCount, updatedReviewItemCount}`다.
- 닫힌 리뷰의 finding 응답·hint·상태 변경은 `409 REVIEW_ALREADY_CLOSED`다.
- 원문(`content`)은 30일 후 자동 purge(DEC-16, `04` §8), `DELETE /coach/reviews/{reviewId}/content`로 즉시 삭제한다(분석 중에는 불가). finding은 남는다.
- AI 불가·예산 초과면 분석 요청을 받지 않는다(`503 AI_UNAVAILABLE` / `429`). 기존 리뷰 조회·상태 변경·close는 동작한다.

**관련 API** — `POST /coach/reviews`, `GET /coach/reviews/{reviewId}`, `GET /coach/reviews?cursor=`, `POST /coach/reviews/{reviewId}/retry`, `POST /coach/reviews/{reviewId}/findings/{findingId}/responses`, `POST /coach/reviews/{reviewId}/findings/{findingId}/hints`, `PATCH /coach/reviews/{reviewId}/findings/{findingId}`, `POST /coach/reviews/{reviewId}/complete`, `DELETE /coach/reviews/{reviewId}/content`, `GET /side-projects`(프로젝트 선택)
**관련 화면** — SCR-COACH-LIST, SCR-COACH-NEW, SCR-COACH-DETAIL
**AC** — AC-06, AC-14, AC-19 · **Sprint** — S4 · **우선순위** — MUST

### FR-13 Thinking pattern 추적

**설명** — coach 리뷰에서 사용자가 각 관점(10개 `ThinkingAxis`)을 스스로 언급했는지, 질문·hint 후에 찾았는지, 놓쳤는지를 누적해 약한 축을 보여주고 코칭 맥락에 쓴다.

**사용자 스토리** — As a 사용자, I want 내가 자주 놓치는 관점(예: 자원 수명, 동시성)을 알고 싶다, so that 코드를 볼 때 그 관점을 의식적으로 확인한다.

**규칙**
- 축: `CORRECTNESS`, `NULL_BOUNDARY`, `RESOURCE_LIFECYCLE`, `EXCEPTION_STRATEGY`, `SECURITY`, `PERFORMANCE`, `CONCURRENCY`, `OBSERVABILITY`, `MAINTAINABILITY`, `TRANSACTION_DATA_CONSISTENCY` (10개).
- observation 기록 시점: 리뷰 close 시 finding마다 `discoveredBy`(`06` §9.3), 분석 완료 시 `incorrectClaims[]`마다 `INCORRECT_CLAIM`.
- MVP의 observation source는 `COACH_REVIEW`뿐이다. `CHALLENGE_ATTEMPT` source는 기록 규칙이 정의될 때까지 쓰지 않는다.
- `mentionedByUser`는 AI 분류다. 화면에 "AI 분류" 표시와 함께 보여준다. MVP에 이의 제기 API는 없다.
- 약한 축 `weakThinkingAxes`는 `06` §12(MISSED 비율 상위 3개, observation 3개 이상인 축만).
- 추세: `GET /thinking-patterns/trend?weeks=8`(주별·축별 observation 수).
- 사용처: coach 분석 컨텍스트(`03` §5.3), `D_DOWN_REPEATED_MISS` 하락 규칙(`06` §7.3), Dashboard·Weekly.

**관련 API** — `GET /thinking-patterns/trend?weeks=8`, `GET /dashboard`, `GET /weekly-reviews/{weekStartDate}`
**관련 화면** — SCR-COACH-DETAIL, SCR-DASHBOARD, SCR-WEEKLY-DETAIL
**AC** — AC-19 · **Sprint** — S4(기록), S5(추세·표시) · **우선순위** — MUST

### FR-14 Verification 표시와 가드

**설명** — AI가 낸 지적마다 근거 수준(`VerificationStatus`)과 확신도(`Confidence`)를 저장·표시하고, 서버가 근거 없는 높은 등급을 강등한다.

**사용자 스토리** — As a 사용자, I want AI 지적이 공식 문서 근거인지 AI 판단인지 구분해서 보고 싶다, so that AI 말을 그대로 믿지 않고 확인할 부분을 안다.

**규칙**
- 등급: `VERIFIED`(서버 도구 결과 또는 curated source), `SUPPORTED`(allowlist 공식 문서·보안 가이드), `AI_JUDGMENT`, `UNCERTAIN`. 정의와 강등 순서는 `06` §10.
- MVP에는 서버 도구 실행이 없으므로 `VERIFIED`는 `content/curated-sources.yaml`에 등록된 근거일 때만 가능하다.
- **서버는 `sourceReference` URL을 fetch하지 않는다.** 호스트 문자열만 allowlist와 비교한다.
- DB CHECK로 이중 방어한다(I-07, I-08).
- 화면: finding 카드에 verification 배지와 confidence 배지를 **항상** 텍스트로 표시한다(색만으로 구분 금지, NFR-08). `sourceType`이 `OFFICIAL_DOC`/`SECURITY_GUIDE`이고 https URL이면 외부 링크로 연다. 그 외 `sourceReference`는 일반 텍스트다.
- 가드 동작은 `ai_call_log.guard_actions`에 남는다(`04` §5.6). 사용자 화면에는 표시하지 않는다.

**관련 API** — `GET /coach/reviews/{reviewId}`
**관련 화면** — SCR-COACH-DETAIL
**AC** — AC-07 · **Sprint** — S3(guard), S4(finding 표시) · **우선순위** — MUST

### FR-15 진단 challenge

**설명** — 짧은 진단 문제를 카테고리당 1개 제안한다. v3부터 온보딩 3단계의 기본 경로다(FR-02 진단 모드): 진단 모드면 카테고리마다(최대 5개), 자기평가 모드면 자기평가 3 이상인 카테고리마다 제안한다. 통과하면 evidence level을 올려 이미 아는 사용자가 기초 과제만 반복하지 않게 한다.

**사용자 스토리** — As a 기초 일부를 이미 아는 사용자, I want 이미 아는 영역은 짧은 문제로 증명하고 넘어가고 싶다, so that planner가 내 수준에 맞는 과제를 준다.

**규칙**
- `GET /diagnostics/suggestions`: `purpose=DIAGNOSTIC`인 seed `VALIDATED` challenge를 카테고리당 1개, **최대 5개** 제안한다(선택 규칙 `05-api-spec.md` §4.2). 대상 카테고리 — 진단 모드(모든 `self_assessed_level`이 `null`): 자기평가가 활성인 행이 있는 카테고리 전부 / 자기평가 모드: 자기평가가 활성이고 값이 3 이상인 카테고리. 진단 attempt를 한 번이라도 시작한 카테고리(상태 무관)는 제외한다.
- 진단은 **건너뛸 수 있다**. 건너뛰기는 서버에 저장하지 않고 클라이언트가 기기에 기억한다(`02` §3.5 SCR-DIAGNOSTICS).
- 풀이 흐름은 FR-09와 같다(self-explanation → 제출 → 비동기 평가).
- 판정(`06` §7.4): `evaluatedOutcome=CORRECT`이고 maxHintLevel ≤ `QUESTION_ONLY`면 `DIAGNOSTIC_PASSED` → KNOWLEDGE, IMPLEMENTATION = `max(현재, min(claimedLevel, 3))` (cooldown·1단계 제한 없음). 그 외 평가 완료는 `DIAGNOSTIC_FAILED` → 레벨 유지, `self_assessment_active=false`.
- `claimedLevel`은 평가 시점 해당 skill의 `self_assessed_level`이다.
- 진단 challenge는 planner의 task 제안에 쓰지 않는다(`06` §5.3은 `PRACTICE`만).

**관련 API** — `GET /diagnostics/suggestions`, `POST /challenges/{challengeId}/attempts`, FR-09 attempt API
**관련 화면** — SCR-ONBOARDING(5단계 "진단 시작"), SCR-TODAY(제안 카드), SCR-DIAGNOSTICS, SCR-TRAINING-ATTEMPT
**AC** — AC-11 · **Sprint** — S3 · **우선순위** — MUST (v3: 온보딩 시작점이 되어 SHOULD에서 올림)

### FR-16 Dashboard

**설명** — 오늘 상태, 이번 주 학습, 계획 위험, skill·thinking 약점을 한 화면에 읽기 전용으로 모은다.

**사용자 스토리** — As a 사용자, I want 이번 주에 얼마나 했고 기한 대비 어디쯤인지 한눈에 보고 싶다, so that 다음 주 계획을 조정할지 판단한다.

**규칙**
- `GET /dashboard` 하나로 집계한다(읽기 전용, AI 없음, p95 < 1000ms).
- S2 최소 구성(`05-api-spec.md` §13.1): 오늘 요약(main task 제목·유형·상태·예상 시간, 복습 task 상태), 오늘 due 복습 수, 이번 ISO week(월요일~오늘) 완료 세션 수·학습 시간, `aiStatus`, `replanRecommended`.
- S5 완성 구성: 위 항목 + 최근 snapshot 8개의 risk 추세, milestone 타임라인과 오늘 위치·horizon, 카테고리별 평균 planning 레벨 대비 평균 목표 레벨, 최근 28 plan-day `weakThinkingAxes`.
- 학습 방식 지표(`independentSolveRateBp`, `averageHintLevelMilli`, `recallSuccessRateBp`, `selfFoundRiskCount`)는 Dashboard가 아니라 weekly review(FR-17)에서 보여준다.
- **연속 학습일(streak), 학습하지 않은 날 수, 사용자 간 비교를 표시하지 않는다**(NG-11).
- 지표 값이 null(분모 0)이면 "기록이 더 필요해요"로 표시한다.

**관련 API** — `GET /dashboard`
**관련 화면** — SCR-DASHBOARD
**AC** — AC-02 · **Sprint** — S2(최소), S5(완성) · **우선순위** — MUST

### FR-17 Weekly review·지표

**설명** — 매주 월요일 plan-day 시작 시 지난주 지표를 스냅샷으로 만들고, 사용자는 짧은 회고를 남긴다.

**사용자 스토리** — As a 사용자, I want 한 주를 숫자와 내 말로 돌아보고 싶다, so that 다음 주에 무엇을 바꿀지 정한다.

**규칙**
- `WeeklyReviewJob`이 사용자 로컬 월요일 `dayStartHour`에 지난 ISO week(월~일, plan-day 기준)의 지표를 계산해 `weekly_review`를 만든다(`03` §6). 이미 있으면 다시 만들지 않는다.
- 지표 필드는 `04` §5.7, 계산식은 `06` §12다. 생성 후 `metrics_json`은 바뀌지 않는다.
- 회고(`PUT /weekly-reviews/{weekStartDate}/reflection`): `reflection` 0~5000자, `version` 필수.
- 목록은 최신 주부터 cursor. 첫 주는 온보딩 다음 월요일에 생긴다.
- AI를 쓰지 않는다. 회고 문구 제안도 하지 않는다.
- thinking pattern 추세(최근 8주)를 같은 화면에 보여준다.

**관련 API** — `GET /weekly-reviews?cursor=`, `GET /weekly-reviews/{weekStartDate}`, `PUT /weekly-reviews/{weekStartDate}/reflection`, `GET /thinking-patterns/trend?weeks=8`
**관련 화면** — SCR-WEEKLY-LIST, SCR-WEEKLY-DETAIL, SCR-DASHBOARD
**AC** — AC-21 · **Sprint** — S5 · **우선순위** — SHOULD

### FR-18 Evidence (후보, AI 초안, 승인, 학습 기록 정리 (STAR) export)

**설명** — 실제로 해결한 학습·코드 기록을 **학습 기록**(evidence)으로 정리한다. 형식은 STAR(상황-과제-행동-결과) 회고다. 사용자가 직접 쓰거나 learning event에서 AI 초안을 받고, 수정 후 승인한 것만 export한다.

**사용자 스토리** — As a 배운 것을 오래 남기고 싶은 사용자, I want 공부하며 해결한 문제를 STAR 형식으로 정리해 두고 싶다, so that 무엇을 왜 그렇게 했는지 나중에 구체적인 사례로 되짚고 설명할 수 있다.

**규칙**
- 상태: `CANDIDATE → ACCEPTED | REJECTED`, `REJECTED → CANDIDATE`(복원). `ACCEPTED`는 되돌리지 않고 수정만 허용한다(`04` §4.7). 복원은 `PATCH /evidence/{evidenceId}`의 `status=CANDIDATE`다.
- 수동 작성(`POST /evidence`): `title` 1~200자, `problem`/`analysis`/`action`/`result` 각 0~3000자, `skillCode`(선택), `referenceLinks` 0~10개(https, 각 ≤ 500자), `explanationTopics` 0~10개(각 1~200자).
- AI 초안(`POST /evidence/drafts`, `sourceLearningEventId`): `202`, `EVIDENCE_DRAFT` 비동기. 결과는 `ai_draft_json`에 원본으로 두고 편집 필드에 복사한다. 사용자가 편집해도 AI 원본은 바뀌지 않는다(`04` §5.8).
- 초안 source는 본인의 무효화되지 않은 learning event면 된다(`05-api-spec.md` §14.5). 화면은 skill 레벨 변경 이력의 근거 이벤트 중 `CHALLENGE_EVALUATED`, `COACH_FINDING_CLOSED`, `COACH_REVIEW_COMPLETED`에만 초안 버튼을 둔다(`02` §3.10 SCR-SKILL-DETAIL). 문제 풀이·코드 리뷰 결과 화면의 "증거로 남기기"는 `AttemptView.evidenceSourceEventId` / `CoachReviewView.evidenceSourceEventId`가 있으면 그 값으로 초안을 요청하고, null이거나 AI 불가일 때만 제목·기술을 채운 수동 작성으로 연결한다. 초안 생성 실패는 재시도 API 없이 새로 요청한다.
- 편집은 `PATCH /evidence/{evidenceId}`(+`version`). 초안 생성 중에는 편집·승인·거절이 `409 INVALID_STATE_TRANSITION`이다.
- 승인(`POST …/accept`)은 `title`과 `problem`, `action`, `result`가 비어 있지 않아야 한다. 승인 시 `EVIDENCE_ACCEPTED` 이벤트 → skill `evidence_count`, `I4_PRODUCTION_LIKE` 규칙 입력(`06` §7.2).
- export(`GET /evidence/export?format=markdown`)는 `ACCEPTED`만 STAR Markdown(제목, 상황·문제, 분석, 행동, 결과, 관련 skill, 링크, 설명 주제)으로 반환한다.
- 외부 서비스로 자동 게시하지 않는다.

**관련 API** — `GET /evidence?status=&cursor=`, `GET /evidence/{evidenceId}`, `POST /evidence`, `POST /evidence/drafts`, `PATCH /evidence/{evidenceId}`, `POST /evidence/{evidenceId}/accept`, `POST /evidence/{evidenceId}/reject`, `GET /evidence/export?format=markdown`
**관련 화면** — SCR-EVIDENCE-LIST, SCR-EVIDENCE-DETAIL, SCR-TRAINING-ATTEMPT(초안 진입), SCR-COACH-DETAIL(초안 진입)
**AC** — AC-21 · **Sprint** — S6 · **우선순위** — SHOULD

### FR-19 로드맵 비교 (Roadmap compare — 공개 로드맵·기술 목록 붙여넣기 분석, 확률·점수 없음)

**설명** — 사용자가 공개된 학습 로드맵이나 기술 목록을 붙여넣으면 AI가 항목을 추출하고, 서버가 catalog skill과 현재 skill state·accepted evidence로 항목마다 `READY`(준비됨) / `STRETCH`(도전) / `LATER`(나중에)로 분류한다. 식별자는 `radar` 모듈·`requirement_doc`/`requirement_item`·`REQUIREMENT_EXTRACT`·`/requirement-docs`다.

**사용자 스토리** — As a 공개 로드맵을 참고해 공부하는 사용자, I want 로드맵 항목 중 무엇이 준비됐고 무엇을 보완해야 하는지 알고 싶다, so that 남은 시간에 공부할 것을 고른다.

**규칙**
- 입력: `title` 1~200자(필수), `sourceUrl` ≤ 2000자(http/https URL, 선택), `sourceText`(필수, UTF-8 20000 bytes 이하 — 초과 시 `413 CONTENT_TOO_LARGE`).
- **서버는 `sourceUrl`을 fetch하지 않는다.** 기록용 링크일 뿐이다(NG-8, AC-22). 외부 사이트 자동 수집·크롤링은 없다.
- `POST /requirement-docs`는 `202`, `REQUIREMENT_EXTRACT` 비동기다. 항목마다 `rawText`, `requirementType`(`REQUIRED|PREFERRED`), catalog에 있는 `skillCode`(없으면 null)를 저장한다.
- `fitCategory`(`READY|STRETCH|LATER`)와 연결 evidence는 **서버의 `RequirementFitClassifier`가 evidence level로 계산**한다(`06` §13). AI가 정하지 않는다. skill이 매칭되지 않은 항목은 `fitCategory=null`("분류 불가")이다. 분류는 분석 시점에 고정된다.
- **응답과 화면 어디에도 달성 확률, 점수, 퍼센트, 순위가 없다**(NG-6). 화면은 분류별 개수와 목록만 보여준다.
- 붙여넣은 원문(`sourceText`)은 180일 후 purge(`04` §8). 분석 결과(항목·분류)는 남는다. `DELETE /requirement-docs/{requirementDocId}`로 전체 삭제한다.

**관련 API** — `POST /requirement-docs`, `GET /requirement-docs/{requirementDocId}`, `GET /requirement-docs?cursor=`, `DELETE /requirement-docs/{requirementDocId}`
**관련 화면** — SCR-REQUIREMENTS-LIST, SCR-REQUIREMENT-NEW, SCR-REQUIREMENT-DETAIL
**AC** — AC-22 · **Sprint** — S7 · **우선순위** — SHOULD

### FR-20 캘린더 구독 (ICS)

**설명** — 사용자의 캘린더 앱이 구독할 비밀 URL을 발급해 매일 학습 일정을 캘린더에 표시한다. 푸시 인프라 없이 캘린더 알림으로 학습 시작을 돕는다.

**사용자 스토리** — As a 퇴근 후 공부를 자주 잊는 사용자, I want 내 캘린더에 오늘의 학습이 떠 있기를 원한다, so that 캘린더 알림을 보고 앱을 연다.

**규칙**
- `POST /me/calendar-token`은 256bit 랜덤 토큰을 새로 만들고 `SHA-256` 해시만 저장한다. 이전 토큰은 즉시 무효다. **구독 URL(`feedUrl`)은 이 응답에서만 한 번 준다.** 같은 `Idempotency-Key`의 재생 응답은 `feedUrl=null`이므로(`03` §5.4) 화면은 새 링크를 다시 만들라고 안내한다.
- 구독 여부는 `GET /me`의 `calendarSubscribed`로 표시한다.
- `GET /calendar/{token}.ics`는 Bearer 인증 없이 토큰 해시로 조회한다. 일치하지 않으면 404. 로그에서 토큰 경로는 마스킹한다(`03` §5.5). 토큰당 시간당 60회를 넘으면 `429 RATE_LIMITED`다.
- 피드 내용(`05-api-spec.md` §3.6): 오늘 plan-day부터 7 plan-day의 **종일(all-day) 일정** 1개씩.
  - 오늘, main task 있음: `오늘의 핵심: {title} ({estimatedMinutes}분)`
  - 오늘, 계획·main task 없음: `DevPilot 오늘 계획 만들기`
  - 내일 ~ 6일 후: `DevPilot 학습`
- 피드에 자기 회고, 답변, 코드, 점수, risk를 넣지 않는다.
- 알림 시각은 사용자가 캘린더 앱에서 정한다(DevPilot에 알림 시각 설정은 없다).
- 토큰 폐기만 하는 API는 없다. 재발급으로 이전 링크를 끊는다. 계정 삭제 요청 시 토큰은 즉시 지워진다.

**관련 API** — `POST /me/calendar-token`, `GET /calendar/{token}.ics`
**관련 화면** — SCR-SETTINGS(캘린더 구독 섹션)
**AC** — — (API 테스트로 검증) · **Sprint** — S5 (v3: M1에 필요 없어 S2에서 옮김) · **우선순위** — SHOULD

### FR-21 복귀 모드

**설명** — 며칠 쉬고 돌아온 사용자에게 밀린 양을 들이밀지 않고, 복습 상한을 줄이고 쉬운 과제로 다시 시작하게 한다.

**사용자 스토리** — As a 바빠서 며칠 못 한 사용자, I want 부담 없이 다시 시작하고 싶다, so that 죄책감 때문에 앱을 피하지 않는다.

**규칙**
- 판정: `comebackMode = (최근 3 plan-day에 COMPLETED 세션 없음) && (그 이전에 COMPLETED 세션 ≥ 1)` (`06` §5.5, `ComebackModePolicy`). 첫 사용자는 복귀 모드가 아니다.
- 효과: 복습 상한 10장(`06` §5.6), task 난이도 ≤ 2(`06` §5.3), `COMEBACK_HARD_TASK` modifier(`06` §5.5), 이유 `COMEBACK_EASY_START`(`06` §5.8).
- `daily_plan.comeback_mode`에 저장하고 `GET /today`에 포함한다.
- 화면: Today 상단에 복귀 안내 배너 1줄(`02` §4.14). **쉰 날 수, 밀린 카드 수, "연속 기록이 끊겼어요" 같은 문구를 보여주지 않는다.**
- 복귀 모드는 사용자가 끄거나 켤 수 없다. 완료 세션이 생기면 다음 plan-day부터 자연스럽게 해제된다.

**관련 API** — `POST /today/generate`, `GET /today`
**관련 화면** — SCR-TODAY
**AC** — AC-02 · **Sprint** — S2 · **우선순위** — SHOULD

### FR-22 AI 사용량·예산 표시와 차단

**설명** — AI 호출 수·비용을 기록하고 한도에 가까워지면 경고, 초과하면 AI 기능만 막는다. 사용자는 현재 AI 상태와 사용량을 볼 수 있다.

**사용자 스토리** — As a 비용을 직접 내는 소유자, I want AI 비용이 예산을 넘지 않게 자동으로 막히길 원한다, so that 예상하지 못한 청구가 생기지 않는다.

**규칙**
- 한도(DEC-06, `03` §9): 서비스 전체 월 예산 USD 3, 사용자당 일일 호출 60회, 사용자당 동시 실행 2개.
- `AiBudgetGuard`는 AI 호출 전에 확인한다. 초과 시 공급자를 호출하지 않고 `ai_call_log.status=BUDGET_BLOCKED`를 남긴다.
  - 월 예산 초과 → `429 AI_MONTHLY_BUDGET_EXCEEDED`
  - 일일 호출 초과 → `429 AI_DAILY_LIMIT_EXCEEDED`
  - 동시 실행 초과 → `429 AI_CONCURRENCY_LIMIT`
- 비동기 작업의 예산 확인은 요청 시점(저장 전)에 한다(`03` §5.3). 확인 후 실행 시점에 초과되면 작업은 `FAILED(AI_BUDGET_EXCEEDED)`다.
- `GET /me`의 `aiStatus`: provider가 `disabled`이거나 월 비용 ≥ 예산이면 `DISABLED`, 공급자 선불 잔액이 소진되면 `BALANCE_EXHAUSTED`(ADR-035), 월 비용 ≥ 80%(`budget-warning-ratio`)면 `BUDGET_WARNING`, 그 외 `ENABLED`. 클라이언트는 `BALANCE_EXHAUSTED`를 `DISABLED`와 같게 다루고 이유 문구만 다르게 보여 준다. 일일 호출 한도 도달은 `aiStatus`에 반영하지 않는다. `aiUsage` = `todayCalls`, `dailyCallLimit`, `monthCostUsd`(서비스 전체, 문자열 소수 2자리), `monthlyBudgetUsd`(`05-api-spec.md` §1.9.1).
- 월 경계는 Asia/Seoul 기준 달력 월(NFR-01), 일일 경계는 사용자 plan-day다. 429 응답에는 `Retry-After`가 있다. 비용 계산은 `17-ai-integration.md`, 단가는 설정값(`03` §9)을 따른다.
- **AI 차단은 비-AI 기능에 영향을 주지 않는다**(NFR-03, AC-12): Today, 복습(자기평가), 계획, skill 조회, 이력, 수동 카드, 수동 evidence.
- 클라이언트는 `DISABLED`일 때 AI 진입 버튼을 비활성화하고 이유를 보여준다. `BUDGET_WARNING`은 AI 진입점과 설정에 경고를 표시한다(`02` §6.5).
- 공급자에 콘솔 하드 캡이 없으므로 **선불 잔액을 소액으로 유지**하는 것이 외부 상한이다. 앱 예산(기본 USD 3)이 먼저 차단하고, 잔액이 임계값 미만이면 `aiStatus = BALANCE_EXHAUSTED`로 AI를 멈춘다(ADR-035, NFR-01).

**관련 API** — `GET /me` (`aiStatus`, `aiUsage`), 모든 AI 유발 API의 429/503 응답
**관련 화면** — SCR-SETTINGS, AI 진입 화면 전체(SCR-RUBBER-DUCK, SCR-READ-CODE, SCR-TRAINING-LIST, SCR-TRAINING-ATTEMPT, SCR-COACH-NEW, SCR-COACH-DETAIL, SCR-REVIEW-SESSION, SCR-EVIDENCE-DETAIL, SCR-REQUIREMENT-NEW)
**AC** — AC-13 · **Sprint** — S3 · **우선순위** — MUST

### FR-23 데이터 export·계정 삭제

**설명** — 사용자는 자기 데이터를 JSON으로 내려받고, 최근 재로그인을 확인한 뒤 계정과 모든 학습 데이터를 삭제한다.

**사용자 스토리** — As a 사용자, I want 내 기록을 가져가거나 완전히 지울 수 있기를 원한다, so that 내 데이터를 내가 통제한다.

**규칙**
- `GET /me/export`: 사용자 소유 데이터 전체를 JSON 1개로 반환한다 — 프로필, 학습 목표, 모든 plan version(milestone, skill target, snapshot), skill state·변경 이력, 세션, learning event, daily plan·task, 소유 challenge·attempt·submission, hint disclosure, review item·answer, coach review(purge 전 content 포함)·finding·observation, evidence, weekly review, 로드맵 비교의 요구사항 문서(purge 전 원문 포함)·요구사항 항목, 사이드 프로젝트, 러버덕 세션·턴(마스킹본).
- export 제외: `ai_call_log`, `idempotency_record`, `calendar_token_hash`, seed catalog, 다른 사용자 데이터. 감사 이벤트 `DATA_EXPORTED`를 남긴다. 형식·정렬은 `05-api-spec.md` §3.3이다.
- `DELETE /me`: 최근 인증 시각 `authTime` = JWT `amr[].timestamp` 최댓값(`amr`이 없을 때만 `iat`)이 **5분 이내**여야 한다(`devpilot.security.account-deletion-max-token-age`, `03` §4.2, `05-api-spec.md` §3.4). 아니면 `403 RECENT_LOGIN_REQUIRED`. 클라이언트 카운트다운도 같은 기준을 쓴다.
- 성공 시 `202`. `status=DELETION_REQUESTED`가 되고 캘린더 토큰이 지워진다. 이후 `GET /me`, `DELETE /me` 외 API는 `403 FORBIDDEN`이다. `AccountDeletionJob`(5분 주기)이 `app_user`를 삭제해 cascade로 지우고 `ai_call_log.user_id`는 null이 된다(`04` §8, `03` §6).
- 운영자는 삭제된 계정을 **allowlist에서도 제거**한다. 남겨 두면 다시 로그인할 때 새 사용자가 생긴다(`05-api-spec.md` §3.4). Supabase 모드(Later)에서는 Auth 사용자도 대시보드에서 수동 삭제한다(DEC-11). 화면에서 이 사실을 알린다.
- 백업 속 데이터는 백업 보관 기간(일간 14개, 월간 6개) 만료 시 사라진다(`04` §8). 화면에서 알린다.
- 삭제 요청은 취소할 수 없다.

**관련 API** — `GET /me/export`, `DELETE /me`
**관련 화면** — SCR-SETTINGS, SCR-ACCOUNT-DELETE
**AC** — AC-15 · **Sprint** — S6 · **우선순위** — MUST

### FR-24 사용자 설정 (timezone, 하루 시작 시각, 학습 시간)

**설명** — 사용자는 표시 이름, timezone, 하루 시작 시각, 평일·주말 학습 가능 시간을 바꾼다. 이 값은 plan-day 계산, budget, Today 기본값에 쓰인다.

**사용자 스토리** — As a 자정 넘어 공부하는 사용자, I want 새벽 공부가 전날 기록으로 남기를 원한다, so that 하루 계획과 기록이 내 생활 리듬과 맞는다.

**규칙**
- `PATCH /me`: `displayName` 1~100자, `timezone` IANA ID(서버가 `ZoneId`로 검증), `dayStartHour` 0~6, `weekdayStudyMinutes`/`weekendStudyMinutes` 0~720, `version` 필수. null 필드는 변경하지 않는다.
- plan-day 계산은 `06` §2. 변경은 **다음 요청부터 즉시** 적용된다. 이미 저장된 `daily_plan`, 세션, 이벤트의 `plan_date`는 다시 계산하지 않는다.
- timezone·dayStartHour를 바꾸면 "오늘"이 앞뒤로 이동할 수 있다. 화면에서 변경 전에 이 점을 알린다.
- 학습 시간 변경은 다음 budget 계산(snapshot, preview)부터 반영된다.
- 클라이언트는 날짜·시각을 **기기 timezone이 아니라 `GET /me`의 `timezone`으로** 표시하고, 오늘 plan-day는 `GET /me`의 `today`를 쓴다(NFR-09).
- 이메일, GitHub 계정 정보는 DevPilot에서 수정하지 않는다.

**관련 API** — `GET /me`, `PATCH /me`
**관련 화면** — SCR-SETTINGS, SCR-ONBOARDING(2단계)
**AC** — AC-17 · **Sprint** — S1 · **우선순위** — MUST

### FR-25 러버덕 (AI가 되묻는 설명 대화, 막힌 곳 → 복습 카드) ★ 중심 기능

**설명** — 사용자가 방금 읽은 코드·방금 만든 것·복습 카드·개념·프로젝트 작업을 **자기 말로 설명**하면, AI는 **답을 주지 않고 질문 하나로 되묻는다.** 이 대화를 몇 턴 반복하면 설명이 막히는 지점이 드러나고, 종료할 때 한 번 정리해서 막힌 곳(gaps)은 복습 카드로, 제대로 설명한 것(confirmed)은 학습 기록으로 남긴다. DevPilot 공부 방식의 중심이고 핵심 루프의 "설명한다" 단계다(§5). 원칙 3(Do not replace thinking)의 가장 직접적인 구현이다. 기존 self-explanation(FR-09)은 한 번 쓰고 제출하면 채점받는 **일방향**이었고, 러버덕은 **대화**다.

**사용자 스토리** — As a 개념을 "안다고 느끼는" 사용자, I want 내 설명의 빈틈을 누군가 계속 되물어 주기를 원한다, so that 모르는 곳을 스스로 발견하고 그곳을 다시 공부한다.

**규칙**
- 흐름: 대상을 정해 시작(AI 호출 없음) → 설명 제출 → AI 질문 1개(`RUBBER_DUCK`, 동기 20s, 재시도 0) → 답 제출 → 반복(기본 최대 5턴, `devpilot.rubberduck.max-turns`) → 사용자가 끝내거나 턴 상한에 닿으면 정리 1회(`RUBBER_DUCK_SUMMARY`, 동기 30s) → gaps → 복습 카드, confirmed → 학습 이벤트.
- 대상(`RubberDuckTargetType`): `CODE_READING`(READ_CODE task, FR-27), `CHALLENGE`(challenge attempt), `REVIEW_ITEM`(복습 카드), `CONCEPT`(`conceptKey`, 예: skill code), `PROJECT_WORK`(사이드 프로젝트, FR-26). 본인 것이 아니거나 없으면 시작할 수 없다(`400 VALIDATION_FAILED` `REFERENCE_NOT_FOUND`). 대상이 나중에 지워져도 세션은 남는다.
- 규칙 RD-1~RD-7(`06` §9.5):
  - **RD-1** AI는 질문만 한다. 정답·수정 코드·"맞습니다/틀렸습니다"를 말하지 않는다 — `CodeLeakGuard`와 `NoAnswerGuard`(NA-1 물음표로 끝남, NA-2 정답 단정 표현 금지, NA-3 코드 금지, NA-4 정리의 정답 서술 gap 제거, `17` §6.8)가 막는다. 위반이면 그 턴은 저장하지 않고 `502 AI_OUTPUT_INVALID`다.
  - **RD-2** 질문은 사용자 설명의 빈틈이나 틀린 전제를 겨냥한다. "더 설명해 보세요" 같은 일반 질문은 금지다.
  - **RD-3** 2턴 연속 "모르겠다"류로 답하면 `suggestHint=true` — 대상이 challenge면 그 attempt의 Hint Ladder로 넘어가고(FR-10, `HINT_DISCLOSED` 정상 기록), 그 외 대상은 정리로 안내한다. 무한 좌절을 막는다.
  - **RD-4** 턴 상한에 닿거나 사용자가 끝내면 정리를 **세션당 한 번만** 한다. 턴이 0개면 AI를 부르지 않고 `ABANDONED`로 끝낸다.
  - **RD-5** 정리에서 gaps가 0개이고 턴이 3 이상이면 EXPLANATION 축 증거다(`RUBBER_DUCK_COMPLETED`, `06` §7.2).
  - **RD-6** 설명은 저장·AI 전송 전에 `SecretMasker`를 통과한다. private key가 있으면 `422 SECRET_DETECTED_BLOCKED`(저장·AI 호출 없음). 설명 한 번은 최대 2000자(`devpilot.rubberduck.max-explanation-chars`, 넘으면 `413`).
  - **RD-7** 세션 하나는 skill 하나를 주 대상으로 한다(생략하면 대상에서 유도). skill이 없으면 학습 이벤트를 남기지 않는다(레벨에 영향 없음).
- 사용자당 진행 중(`IN_PROGRESS`) 세션은 1개다. 새로 시작하면 이전 세션은 `ABANDONED`가 된다. 24시간 방치된 세션은 `StaleRubberDuckJob`이 `ABANDONED`로 닫는다(정리·카드 없음). 사용자가 중단하면(`abandon`) AI를 부르지 않고 대화 기록만 남는다.
- 정리 결과: `gaps[]`(최대 3 — 무엇을 몰랐나, 왜 중요한가, 복습 질문), `confirmed[]`(최대 5), 한 줄 총평. gap마다 복습 카드를 만들고(`review_type = EXPLAIN`, 첫 due 다음 plan-day), 같은 개념의 활성 카드가 있으면 due만 당긴다(FR-11). 응답의 `createdReviewItemCount`는 새로 만든 카드 수다.
- 학습 이벤트는 턴마다 남기지 않는다. 정리에 성공하고 skill이 있을 때만 `RUBBER_DUCK_COMPLETED`(payload `{ sessionId, turns, gapCount, targetType }`, `04` §6)를 남긴다.
- **AI 불가 시**(`aiStatus` `DISABLED`·`BALANCE_EXHAUSTED`): 시작 버튼을 막고 이유를 보여준다. 턴 제출 실패(차단·오류·timeout·가드 위반)는 턴을 저장하지 않고 HTTP 오류를 준다 — 사용자의 설명은 화면이 유지한다. 정리 실패는 **오류가 아니다**: 세션은 `COMPLETED`, `summarySkippedReason`을 주고, 대화 기록은 남고, 복습 카드·이벤트는 만들지 않는다. 정리 재시도는 없다. 지난 세션 조회는 언제나 된다(`17` §3.10).
- `READ_CODE` task의 완료 조건은 그 task를 대상으로 한 `COMPLETED` 세션 1개다(RC-1, FR-27). 정리 AI가 실패해도 `COMPLETED`이므로 조건을 만족한다.
- 세션 목록 API는 없다. 진행 중 세션은 시작 응답과 클라이언트 로컬 상태로 이어서 연다(`05-api-spec.md` §9.10).
- 비용: 5턴 + 정리 1회 ≈ USD 0.008(피크 단가 ×2 기준 추정). 앱 월 예산(FR-22) 안에서 돈다.

**관련 API** — `POST /rubber-duck`, `POST /rubber-duck/{sessionId}/turns`, `POST /rubber-duck/{sessionId}/complete`, `POST /rubber-duck/{sessionId}/abandon`, `GET /rubber-duck/{sessionId}`
**관련 화면** — SCR-RUBBER-DUCK (진입: SCR-READ-CODE, SCR-TODAY, SCR-TRAINING-ATTEMPT, SCR-REVIEW-SESSION, SCR-REVIEW-ITEMS, SCR-SKILL-DETAIL, SCR-PROJECTS)
**관련 규칙** — RD-1~RD-7(`06` §9.5), `NoAnswerGuard` NA-1~NA-4(`17` §6.8), 출력 스키마 `17` §4.10·§4.11
**AC** — AC-26 · **Sprint** — S3 · **우선순위** — MUST
**범위 밖** — AI가 정답·해설·모범 답안·수정 코드를 주는 것(답에 가까운 도움은 Hint Ladder FR-10), 러버덕 전용 hint API, AI가 레벨·점수·outcome을 정하는 것, 음성 입력, 세션 목록·검색, 정리 재시도, 끝난 세션을 다시 열어 이어 하기, 사용자 간 세션 공유.

### FR-26 사이드 프로젝트 (등록·조회·수정·삭제, PROJECT_TASK·Coach 대상)

**설명** — 배운 것을 적용할 **사용자의 프로젝트**를 등록·조회·수정·삭제한다. 기본은 실무 시스템의 정석인 **주문 시스템**(회원가입·로그인 → 상품 CRUD → 주문 생성 → 취소·환불 → 조회 성능, 계획 템플릿 milestone 순서와 같다)이다. 프로젝트는 PROJECT_TASK(오늘 할 구현 과제), Coach 리뷰(FR-12), 러버덕 `PROJECT_WORK`(FR-25)의 대상이 되고, 그 결과물이 실력이 늘었다는 근거가 된다. DevPilot은 학습 도구이지 이 프로젝트 자체가 아니다(NG-15).

**사용자 스토리** — As a 실무 역량을 키우려는 사용자, I want 배운 패턴을 내 주문 시스템에 바로 적용하는 과제를 받고 싶다, so that 공부가 직접 만들고 내 말로 설명할 수 있는 결과물로 쌓인다.

**규칙**
- 규칙 SP-1~SP-3(스파이크 SP-n과 다른 ID다):
  - **SP-1** 온보딩 마지막 입력 단계(④)에서 프로젝트를 하나 만든다. 기본 이름 "주문 시스템"을 클라이언트가 채워 보내고, 온보딩에서는 이름만 바꿀 수 있다. 건너뛸 수 있지만 건너뛰면(또는 `ACTIVE` 프로젝트가 하나도 없으면) planner가 `PROJECT_TASK`를 제안하지 않는다. 나중에 SCR-PROJECTS에서 만들 수 있다.
  - **SP-2** `PROJECT_TASK` 문구는 프로젝트 이름과 skill 이름으로 구체화한다 — 제목 `{프로젝트 이름}에 {skill 이름} 적용하기`, 설명 "{프로젝트}에서 이 개념을 적용할 지점을 찾아 구현하고 이유를 적어 보세요."(`06` §5.3). task의 `side_project_id`는 생성 시점에 고정한다.
  - **SP-3** `ACTIVE` 프로젝트는 여러 개일 수 있지만 planner는 `updated_at`이 가장 최근인 `ACTIVE` 하나만 쓴다. `PAUSED`·`DONE`은 고르지 않는다.
- 필드: `name` 1~100자(필수), `description` ≤ 1000자, `repoUrl` ≤ 500자(http/https URL), `stack` ≤ 300자. **`repoUrl`은 저장만 한다 — 서버는 이 URL을 요청하지 않는다**(`07` §5.5). 저장소 내용이 필요하면 사용자가 로컬에서 본다. `name`·`description`·`stack`은 저장 전 `SecretMasker`를 통과한다(private key면 `422`). 빈 문자열은 `null`로 저장한다.
- 상태 `SideProjectStatus`: `ACTIVE`·`PAUSED`·`DONE`, 서로 자유롭게 바꿀 수 있다. 수정(`PATCH`)은 `version`이 필요하다(다르면 `409 CONCURRENT_MODIFICATION`). 값이 실제로 바뀔 때만 `updated_at`이 갱신된다(SP-3 입력).
- 삭제: 프로젝트 행을 지운다. 이 프로젝트를 가리키던 task·코드 리뷰는 **남고 연결만 끊긴다**(`side_project_id = null`). 지난 러버덕 세션도 남고 대상 제목이 비어 보인다. 되돌릴 수 없다.
- 소유권: 다른 사용자의 프로젝트는 없는 것과 같이 `404`.
- 목록은 `updatedAt` 최신순 cursor 페이지, `status`로 거를 수 있다.

**관련 API** — `POST /side-projects`, `GET /side-projects?status=&cursor=`, `GET /side-projects/{sideProjectId}`, `PATCH /side-projects/{sideProjectId}`, `DELETE /side-projects/{sideProjectId}`, `POST /onboarding`(`sideProject`)
**관련 화면** — SCR-PROJECTS, SCR-ONBOARDING(4단계), SCR-TODAY(PROJECT_TASK 카드), SCR-COACH-NEW(프로젝트 선택), SCR-RUBBER-DUCK(`PROJECT_WORK`)
**관련 규칙** — SP-1~SP-3(이 절), `06` §5.3 PROJECT_TASK 분기
**AC** — AC-27 · **Sprint** — S1(등록·조회·수정·삭제, 온보딩 생성·건너뛰기), S2(PROJECT_TASK 연결) · **우선순위** — MUST
**범위 밖** — 저장소 내용 가져오기·clone·빌드·배포·코드 호스팅, 커밋·PR 자동 연동, 프로젝트 진척률·완성도 계산, 여러 `ACTIVE` 프로젝트를 planner가 동시에 쓰기, 팀 프로젝트·공유, DevPilot 구현을 학습 과제나 증거로 쓰기.

### FR-27 코드 읽기 (`READ_CODE`, 큐레이션 저장소, 서버는 코드를 가져오지 않음)

**설명** — 교재 예제는 너무 간단하다. 검증된 오픈소스의 **파일 하나·줄 범위 하나**를 사용자가 **로컬에 clone해 IDE로** 읽고, 주어진 질문에 러버덕으로 설명한다. DevPilot은 "무엇을, 왜 읽는지"와 "읽고 설명하기"만 맡는다 — **서버는 코드를 가져오지 않고, 화면에 코드 본문을 보여주지 않는다.** 큐레이션 저장소는 3개다: Spring Modulith 예제(모듈 경계·이벤트), Spring PetClinic(계층 구조·테스트 작성법), Spring RESTBucks(주문·결제 상태 전이, 라이선스 명시 없음 → 읽기만).

**사용자 스토리** — As a 교재 예제만 봐 온 사용자, I want 검증된 실무 코드에서 같은 문제를 어떻게 풀었는지 읽고 싶다, so that 내 주문 시스템에 같은 패턴을 근거를 갖고 적용한다.

**규칙**
- 콘텐츠는 `content/curated-repos.yaml`이다 — 저장소(`key`, 이름, URL, 하위 경로, 라이선스, 스택, `why`, `cloneHint`, `pinnedCommit`)와 reading(`key`, 저장소, 파일 경로, 줄 범위, `skillCodes`, 예상 시간, `question`, `lookFor`). 형식·검증은 `19` §3.8·§4.1, 줄 번호는 `pinnedCommit` 기준이고 갱신은 콘텐츠 작업이다(`19` §8.4).
- 과제 유형 `READ_CODE`. planner는 CHALLENGE 다음 순위로 제안한다(`06` §5.3): AI 사용 가능 + 해당 skill planning KNOWLEDGE ≥ 학습 트랙의 문턱(`devpilot.tracks.<트랙>.read-code-min-knowledge`, 기본 트랙 1 / 입문 트랙 2) + 그 skill의 reading 중 완료하지 않았고 최근 14 plan-day 안에 제안하지 않은 것이 있을 때, `reading.key` 오름차순 첫 번째. 예상 시간 = reading의 `estimatedMinutes`, 난이도 2. 이유 `READ_REAL_CODE`.
- 규칙 RC-1~RC-4(`06` §9.5):
  - **RC-1** `READ_CODE` 과제의 완료 조건은 **러버덕 세션 1개 완료**(`targetType = CODE_READING`, `targetId` = 그 task)다. 읽었다고 체크만 하는 것은 완료가 아니다 — 없으면 `PATCH … COMPLETED`가 `409 INVALID_STATE_TRANSITION`. 건너뛰기·미루기에는 조건이 없다.
  - **RC-2** 한 과제는 파일 1개·범위 1개다.
  - **RC-3** planning KNOWLEDGE가 학습 트랙의 문턱 이상일 때만 제안한다(FR-03). 아무것도 모르는 상태에서 코드를 읽으면 좌절한다 — 그때는 READING이 나온다.
  - **RC-4** 저장소가 로컬에 없으면 화면이 `cloneHint`(그 커밋으로 checkout하는 명령)를 먼저 보여준다. **서버는 저장소를 fetch하지 않는다.**
- `GET /readings/{readingKey}`는 저장소 정보·파일 경로·줄 범위·질문·볼 지점·관련 skill만 준다. **코드 본문은 없다.** 사용자 소유 리소스가 아닌 공용 콘텐츠이고(인증은 필요), AI를 호출하지 않는다(비용 0).
- 라이선스가 명시되지 않은 저장소(`UNSPECIFIED`, 현재 restbucks)는 **읽기만** 한다. 화면은 "코드 복사 금지"를 함께 보여주고, 콘텐츠·문서에 그 코드를 옮겨 적지 않는다(`19` §3.8).
- AI가 불가하면 planner가 `READ_CODE`를 제안하지 않는다. 이미 만들어진 `READ_CODE` 과제의 읽기 안내는 동작하지만 완료 조건인 러버덕은 막힌다(`17` §3.10).
- 세 저장소가 다루지 않는 영역(인증/로그인, 동시성·재고 차감, 대용량 조회·인덱스 튜닝)은 읽기 없이 직접 구현(PROJECT_TASK)과 문제(FR-09)로 채운다.
- **읽기 평가(선택)**: 과제를 완료할 때(`PATCH /today/tasks/{taskId}` `status = COMPLETED`) 이 읽기가 어땠는지 하나를 고를 수 있다 — 도움 됐어요(`HELPFUL`) / 어려웠어요(`TOO_HARD`) / 지루했어요(`BORING`). 고르지 않아도 된다. 과제에 저장만 하고(`learning_task.reading_feedback`, `readingFeedback`), 레벨·planner·budget 규칙의 입력이 아니다(`06` §5.3). 사람이 하는 소스 점검의 입력이다.
- **소스 점검**(`19` §8.5): 저장소·읽기 단위는 사람이 주기적으로 다시 본다 — 첫 점검은 S3 구현 시작 직전, 이후 단계 회고마다 또는 사용자가 요청할 때. 입력은 빈틈 목록(낮은 레벨·막히는 복습·읽을 단위가 없는 skill), 읽기 평가, 저장소의 라이선스·유지 상태다. 추가·교체·은퇴는 사용자와 정한 뒤 `content/curated-repos.yaml`에 반영하고(`pinnedCommit` 고정, 경로·줄 재확인, 검증기, `catalogVersion` +1), 은퇴한 단위도 지난 과제를 위해 계속 조회된다. 자동으로 도는 것은 없고 서버는 저장소를 가져오지 않는다.

**관련 API** — `GET /today`(`MainTaskView.readingKey`), `GET /readings/{readingKey}`, `PATCH /today/tasks/{taskId}`(완료 + 선택 `readingFeedback`), `POST /rubber-duck`(`targetType = CODE_READING`)
**관련 화면** — SCR-TODAY(READ_CODE 카드), SCR-READ-CODE, SCR-RUBBER-DUCK
**관련 규칙** — RC-1~RC-4(`06` §9.5), `06` §5.3 READ_CODE 분기·reading 선택, `06` §5.8 `READ_REAL_CODE`
**AC** — AC-28 · **Sprint** — S3 · **우선순위** — MUST
**범위 밖** — 서버가 저장소를 clone·fetch하거나 코드 본문을 저장·표시·검색하는 것, 사용자가 임의 저장소·reading을 등록하는 것(큐레이션만), 여러 파일을 묶은 과제, 코드 실행(NG-7), 저장소 최신 커밋 자동 추적(`pinnedCommit` 갱신은 콘텐츠 작업), 평가로 저장소·단위를 자동 교체하는 것(소스 점검은 사람이 한다), IDE 플러그인(NG-1).

### FR-28 재현 과제 (`REDO`, AI 없이 혼자 다시 만들기)

**설명** — AI가 옆에서 거들 때 풀린 것은 "이해했다"처럼 느껴진다. 실제로 할 수 있는지는 **며칠 뒤 혼자 처음부터 다시 만들 때** 드러난다. 문제나 프로젝트 과제를 마치고 며칠 지나면 Today가 **재현 과제**를 제안한다 — 같은 것을, 처음부터, **AI 없이**. 그 과제가 열려 있는 동안에는 그 대상의 힌트와 러버덕이 잠긴다. 마칠 때 사용자는 질문 하나에 답한다: "AI 도움 없이 끝냈나요?" **성공한 재현만 독립 구현 증거로 센다.** 실패는 벌이 아니라 복습 카드가 되고, 며칠 뒤 다시 제안될 수 있다.

**사용자 스토리** — As a AI와 함께 문제를 푼 사용자, I want 며칠 뒤 같은 것을 혼자 다시 만들어 보고 싶다, so that 내가 할 수 있는 것과 AI가 해 준 것을 구분할 수 있다.

**규칙**
- 규칙 RE-1~RE-8(`06` §5.10). 요약:
  - **RE-1·RE-2 언제** — 원본은 `COMPLETED`인 `CHALLENGE`·`PROJECT_TASK`다. 원본을 마친(또는 지난 재현을 마친) 날로부터 **3~7일**(`devpilot.planner.redo.min-days-after`·`max-days-after`, 양 끝 포함) 사이의 plan-day에만 제안한다. 창을 놓치면 그 기회는 사라진다.
  - **RE-3 몇 번** — 한 원본에 열려 있는 재현은 하나뿐이다. 성공하면 끝이고, 실패·건너뛰기는 시도로 세어 `max-attempts`(기본 2)까지다.
  - **RE-4 크기** — 예상 시간은 원본 그대로다. 오늘 예산에 안 들어가면 오늘은 제안하지 않는다.
  - **RE-5 잠금** — 재현 과제가 `PLANNED`·`IN_PROGRESS`인 동안 그 대상(원본의 challenge 또는 사이드 프로젝트)의 힌트 공개와 러버덕 시작은 `409 AI_ASSIST_LOCKED_FOR_REDO`다. 화면은 **이유를 보여 준다**. 다른 대상의 AI 기능과 복습·계획·기록은 그대로다.
  - **RE-6 답** — 완료하려면 `redoWithoutAi`(예/아니오)가 필요하다. 답 없이 완료할 수 없다.
  - **RE-7 실패** — `false`면 레벨 증거가 되지 않고 그 skill의 복습 카드가 생긴다(다음 plan-day due).
  - **RE-8 증거** — `true`만 `I3_SOLVED_INDEPENDENT`의 독립 구현 증거로 센다(`06` §7.2). AI 상태와 무관하게 동작한다 — 재현 과제는 AI를 부르지 않는다.
- Today 안에서의 자리: 같은 skill에서는 다른 제안보다 앞서지만(§5.3 0번), skill 사이 경쟁은 점수로 한다(modifier `REDO_DUE` ×1.30).

**관련 API** — `GET /today`·`POST /today/generate`(`MainTaskView.redoSourceTaskId`·`redoSourceTaskType`·`redoWithoutAi`), `PATCH /today/tasks/{taskId}`(완료 + 필수 `redoWithoutAi`), `POST /challenge-attempts/{attemptId}/hints`·`POST /rubber-duck`(409 잠금)
**관련 화면** — SCR-TODAY(REDO 카드·완료 시트의 질문), SCR-TRAINING-ATTEMPT·SCR-RUBBER-DUCK(잠금 안내)
**관련 규칙** — RE-1~RE-8(`06` §5.10), HL-9(`06` §9.1), `06` §5.3 0번·§5.5 `REDO_DUE`·§5.8 `REDO_WITHOUT_AI`·§7.2 독립 구현 증거
**AC** — AC-31 · **Sprint** — S4 · **우선순위** — MUST
**범위 밖** — 제출물 비교·유사도 판정(무엇을 만들었는지 서버가 보지 않는다), "AI 없이 했는지"를 서버가 감시하거나 추정하는 것(사용자의 답을 그대로 믿는다), 재현 과제의 자동 채점, `READ_CODE`·`REVIEW`·`EXPLAIN` 과제의 재현, 잠금 우회 요청.

### FR-29 사이드 프로젝트 결정·장애 기록

**설명** — 사이드 프로젝트는 실력을 증명하는 자리인데, **무엇을 왜 골랐고 무엇이 어떻게 깨졌는지**는 며칠만 지나도 흐려진다. 프로젝트마다 두 가지 기록을 남긴다 — **결정 기록**(무엇을 골랐나 · 어떤 선택지가 있었나 · 왜)과 **장애 기록**(무엇이 잘못됐나 · 어떻게 찾았나 · 무엇으로 고쳤나 · 무엇으로 다시 막나). 텍스트와 날짜, 선택 skill 하나뿐이고 파일 첨부는 없다. 이 기록이 나중에 학습 기록 초안(FR-18)과 주간 리뷰(FR-17)의 재료가 된다.

**사용자 스토리** — As a 주문 시스템을 만드는 사용자, I want 그때 왜 그렇게 정했고 무엇이 터졌는지 바로 적어 두고 싶다, so that 나중에 그 과정을 내 말로 설명할 수 있다.

**규칙**
- 규칙 PN-1~PN-4(`06` §9.5). 유형은 `DECISION`·`INCIDENT` 둘이고, 유형마다 채우는 항목이 정해져 있다(I-22). **유형은 생성 후 바꿀 수 없다** — 바꾸려면 지우고 다시 만든다.
- 필드: `title` 1~200자(필수), `occurredOn`(필수, 오늘 이하), `skillCode`(선택), 본문 항목 각 1~4000자. 모든 텍스트는 저장 전 `SecretMasker`를 통과한다(private key면 `422`).
- 기록은 **학습 이벤트를 만들지 않고 skill 레벨을 바꾸지 않는다**(PN-3, 자기 신고 텍스트다). 붙인 skill은 표시와 나중 초안 입력으로만 쓴다.
- 목록은 `occurredOn` 최신순 cursor 페이지이고 유형으로 거를 수 있다. 다른 사용자의 프로젝트·기록은 없는 것과 같이 `404`.
- 프로젝트를 지우면 그 프로젝트의 기록도 함께 사라진다. 삭제 확인 화면이 이를 알린다.
- 이어지는 곳: `POST /evidence/drafts`의 `sourceProjectNoteId`(S6, FR-18)와 주간 리뷰 지표 `projectNoteCount`(S5, FR-17).

**관련 API** — `POST /side-projects/{sideProjectId}/notes`, `GET …/notes`, `GET …/notes/{noteId}`, `PATCH …/notes/{noteId}`, `DELETE …/notes/{noteId}`, `POST /evidence/drafts`(`sourceProjectNoteId`)
**관련 화면** — SCR-PROJECT-DETAIL, SCR-PROJECT-NOTE-EDIT, SCR-PROJECTS(기록 수 표시)
**관련 규칙** — PN-1~PN-4(`06` §9.5), `06` §12 `projectNoteCount`
**AC** — AC-33 · **Sprint** — S3 (증거 초안 연결 S6, 지표 S5) · **우선순위** — MUST
**범위 밖** — 파일·이미지 업로드, 커밋·이슈 자동 연동, 기록으로 skill 레벨을 올리는 것, AI가 기록을 대신 쓰거나 검증하는 것, 기록 공유·공개.

---

## 9. 비기능 요구사항

| ID | 요구사항 | 측정 가능한 기준 | 측정·강제 방법 | AC |
|---|---|---|---|---|
| NFR-01 | **비용**: 추가 인프라 비용 0, AI 월 USD 3 이하 | ① 서버·Tailscale은 이미 쓰던 것이라 추가 비용이 없고 도메인은 쓰지 않는다(ADR-031) ② 운영 AI 비용은 앱의 서비스 전체 월 예산 **USD 3**(DEC-06, Asia/Seoul 기준 달력 월)로 제한한다: `ai_call_log.cost_micro_usd` 월 합계 ≤ 예산 ③ 80% 도달 시 `BUDGET_WARNING`, 100%면 AI 차단 ④ 외부 상한은 **선불 잔액**뿐이다(콘솔 하드 캡 없음) — 잔액 < `min-balance-usd`이면 `BALANCE_EXHAUSTED`(ADR-035) | `AiBudgetGuard`, `AiBalanceCheckJob`, 일일 요약 로그(`03` §8) | AC-13 |
| NFR-02 | **성능** | ① AI를 호출하지 않는 API의 서버 p95 < 1000ms (사용자 1명 기준 데이터: 이벤트 5,000행, 복습 카드 500장에서 k6 50 req) ② 동기 AI(`HINT_GENERATE`, `REVIEW_EVALUATE`, `COACH_RESPONSE_FEEDBACK`, `RUBBER_DUCK`) 서버 타임아웃 20s, `RUBBER_DUCK_SUMMARY` 30s ③ 비동기 AI 작업 요청~완료 ≤ 180s(operation 타임아웃) ④ 캐시된 재방문 시 Today 데이터 표시 ≤ 3s (Chrome Android, Lighthouse "Fast 4G" 프로필) | Micrometer 지표, k6 스크립트(S2), Lighthouse(S2) | — |
| NFR-03 | **가용성**: 단일 인스턴스 best effort, AI 장애 시 핵심 기능 유지 | ① SLA 없음. 외부 uptime monitor 5분 간격, 월 가용성 ≥ 99%를 추적 목표로 둔다 ② AI provider `disabled`/5xx에서 Today 생성, 복습 답변, 계획 조회·수정, 이력 조회가 모두 2xx ③ RPO ≤ 24h(일간 백업), RTO ≤ 4h(VM 재구축) | provider=disabled 통합 테스트, `10-deployment-and-operations.md` runbook | AC-12 |
| NFR-04 | **보안**: OWASP Top 10 대응, 사용자 격리 | ① 모든 사용자 소유 리소스 API에서 타 사용자 토큰 → 404 (endpoint 목록 기반 파라미터화 테스트 100% 통과) ② allowlist 밖 사용자 403, row 미생성 ③ 오류 응답에 stack trace·SQL·클래스명 0건 ④ 요청 body > 64KB는 413 ⑤ 서비스는 tailnet 안에서만 접근 가능하고 공개 인터넷에 노출하지 않는다(ADR-031). Supabase 도입 시 Data API로 `devpilot` 스키마 조회 불가(DEC-15) ⑥ 저장소 secret·서버 주소·이메일 0건(gitleaks — DEC-04) ⑦ 인증 POST에 `Idempotency-Key` 없으면 400 | Security 테스트, CI 스캔, `07-security-and-privacy.md` | AC-08, AC-18, AC-20, AC-23 |
| NFR-05 | **개인정보**: 최소 수집, 보존기간, export/삭제, 마스킹 | ① 수집 항목에 나이·성별·이메일 원문 저장 없음 ② coach content 30일, 로드맵 비교 원문 180일, ai_call_log 180일, idempotency 24h 후 삭제(`04` §8) ③ 저장·AI 전송 전 secret masking, private key 차단(I-14) ④ export에 타 사용자 데이터 0건, 삭제 후 사용자 row 0건 ⑤ 로그에 코드·프롬프트·응답 원문·이메일·Authorization 0건 | purge job 테스트, AC-14/15 테스트, 로그 필드 검사 | AC-14, AC-15 |
| NFR-06 | **관측성**: structured log, traceId, 감사 이벤트 | ① prod 로그 100% JSON, 모든 요청 로그에 `traceId`, 인증 요청에 `userRef` ② 모든 응답에 `X-Trace-Id` 헤더 ③ 감사 이벤트(`07-security-and-privacy.md` §6.3) 누락 0건 ④ 스케줄 job마다 시작·건수·소요시간 INFO 1줄, 실패 시 `JOB_FAILED` WARN ⑤ 화면 오류 상세에 `traceId` 표시 | `03` §8, 통합 테스트, `02` §3.3 | — |
| NFR-07 | **유지보수성**: 자동 품질 게이트, 문서·코드 일치, 결정적 규칙 | ① PR마다 format, Checkstyle, PMD, SpotBugs, ArchUnit, 테스트, `flutter analyze`, `flutter test` 통과 전 merge 불가 ② `06`의 test vector 100% `@ParameterizedTest`로 통과 ③ 규칙 계산에 부동소수점 0건(N-1) ④ enum 이름은 `04` §3과 1:1 ⑤ 가중치·임계값 하드코딩 0건(`03` §9) ⑥ API·DB·규칙 변경 PR은 관련 문서를 같은 PR에서 갱신 | CI, ArchUnit, 코드 리뷰 체크리스트 | — |
| NFR-08 | **접근성·반응형**: 360px~, 터치 44px, 색 외 표시 | ① 폭 360px에서 가로 스크롤 없음(모든 SCR) ② 터치 대상 ≥ 44×44 CSS px ③ 텍스트 대비 ≥ 4.5:1, 아이콘·경계 ≥ 3:1 (WCAG 2.1 AA) ④ 상태·등급·배지는 텍스트 라벨 포함(색만으로 구분 0건) ⑤ 데스크톱 복습은 키보드만으로 완료 가능 ⑥ 글자 크기 200%에서 핵심 버튼 가려짐 없음 ⑦ 한국어 IME 조합 입력·붙여넣기 정상(스파이크 SP-1, `11` §4) | widget test, 수동 점검 체크리스트(`02` §7) | AC-10 |
| NFR-09 | **시간**: plan-day, UTC 저장 | ① instant는 UTC `timestamptz`, 사용자 날짜는 plan-day `date` ② 서버 시간 계산은 주입된 `Clock`만 사용(`LocalDate.now()` 직접 호출 0건, ArchUnit) ③ `06` §2 test vector 통과 ④ 클라이언트 표시는 `GET /me.timezone` 기준 | 단위·통합 테스트 | AC-17 |
| NFR-10 | **이식성**: linux/amd64 이미지, 설정 기반 AI 모델 | ① backend 컨테이너 이미지 `linux/amd64` 빌드·기동 성공(자체 서버 x86_64, ADR-031) ② AI 모델 ID·provider·단가·prompt 버전은 설정값(`devpilot.ai.*`)만 바꿔 교체(DEC-05) ③ 도메인 코드에서 공급자 HTTP 타입 import 0건(`integration.ai.deepseek` 제외, ArchUnit ARCH-03) ④ Flutter 코드는 web 전용 API를 `platform/` 어댑터에만 둔다(Android 확장 대비) | CI 이미지 빌드, ArchUnit | — |

---

## 10. 제약 · 가정

### 10.1 제약

| # | 제약 | 영향 |
|---|---|---|
| C-1 | **단일 backend 인스턴스**, 메시지 큐·캐시 서버 없음(`03` §1) | 스케줄 job은 분산 락 없이 동작. 비동기 AI는 in-process executor(core 2, queue 20) |
| C-2 | **사용자 ≤ 3명**(DEC-01) | 집계는 실시간 쿼리로 충분. 캐시 없음 |
| C-3 | **한국어 UI만** 제공. 식별자·기술 용어·enum은 영어 원문 | l10n 구조는 두되 `ko` 하나만 둔다(`02` §3.1) |
| C-4 | **자체 서버**: 공용 Linux 서버(2 vCPU, 가용 약 4.7GB)와 공용 PostgreSQL 16을 다른 서비스와 나눠 쓴다. 관리형 백업이 없고 tailnet 밖에서 접근할 수 없다 | 보존기간 정책(`04` §8), 자체 백업(일 1회 + 주 1회 로컬 pull), JVM 메모리 상한, 데이터 크기 관리 |
| C-5 | **AI 예산**: 월 USD 3, 일일 60회/사용자, 동시 2개(DEC-06), 모델 `deepseek-flash`(DEC-05), 외부 상한은 선불 잔액 | 비동기 우선, 사전 생성 hint, AI 불가 시 대체 동작 |
| C-6 | 웹 우선 PWA(DEC-18). 네이티브 앱·Push 알림 없음 | 알림은 캘린더 구독(FR-20) |
| C-7 | 학습 트랙 `JAVA_BACKEND` 1개, skill catalog·템플릿·큐레이션 저장소는 저장소 YAML(`04` §9, `19`) | 트랙 추가는 구조 변경 없이 `target_role` 값 추가 migration + `role_skill_target` seed + skill catalog 보강(콘텐츠 작업, `19`) |
| C-8 | 로그인은 지금 `devtoken`(allowlist 이메일, tailnet 전용), 공개 전환 시 GitHub OAuth(DEC-08, `BL-SEC-18`) | 공개 전환 후에는 GitHub 계정 필요 |
| C-9 | 지원 브라우저: Chrome·Edge 최신 2개 버전(데스크톱·Android), Safari 17 이상(iOS·macOS). 2026-09-17 기준, 구현 시 스파이크 SP-1(`11` §4)에서 확인 | 그 외 브라우저는 동작을 보장하지 않는다 |
| C-10 | 코드 실행 환경 없음(NG-7) | challenge 평가는 AI rubric 판정, `VERIFIED`는 curated source만 |
| C-11 | **서버는 외부 코드를 가져오지 않는다** — 사이드 프로젝트 `repoUrl`, 큐레이션 저장소 URL 모두 저장·표시만 한다(`07` §5.5) | 코드 읽기는 사용자가 로컬에 clone해 IDE로 한다(FR-27). 화면에 코드 본문이 없다 |

### 10.2 가정

| # | 가정 | 틀리면 |
|---|---|---|
| A-1 | seed 콘텐츠(skill 60~90개, 복습 카드, challenge, 진단 challenge, plan 템플릿 9개 milestone, 큐레이션 저장소 3개·reading)가 S1~S3에 준비된다 | planner·budget 품질 저하 → content 작업을 단계 최우선으로 올린다 |
| A-2 | 사용자는 하루 최소 5분은 앱을 연다 | 캘린더 구독·복귀 모드 효과를 S5 weekly 지표로 재평가 |
| A-3 | `06`의 가중치·임계값은 초기값이며 4주 사용 후 조정한다 | 설정값만 변경하고 test vector를 갱신한다 |
| A-4 | 사용자가 붙여넣는 코드는 개인 프로젝트 코드다(동의 체크) | `confidentialSuspected` 판정 시 분석하지 않는다 |
| A-5 | `deepseek-flash` 단가·파라미터는 2026-09-18에 실제 호출로 확인한 값이며(`17` §2), 응답 필드 일부는 구현 시 재확인한다 | 설정 갱신, `17` 먼저 수정 |
| A-6 | DevPilot 구현은 AI 코딩 에이전트에 위임하고, 사용자는 리뷰와 결정을 맡는다. DevPilot 구현은 사용자의 학습 과제가 아니다(학습 결과물은 사이드 프로젝트, NG-15) | S1 완료 시 속도를 보고 M1(S2~S3) 범위를 조정한다 |
| A-7 | 사용자는 큐레이션 저장소를 로컬에 clone해 IDE로 읽을 수 있는 개발 환경(git, IDE)이 있다 | 코드 읽기 안내(`cloneHint`)를 보강한다. 읽기 과제를 건너뛰어도 planner는 다른 과제로 이어 간다 |

---

## 11. 용어

용어 정의(plan-day, horizon, nominal/effective budget, planning level, evidence level, final rating, leech, assistance level, independent solve, self-review, discovered by 등)는 `15-glossary.md`가 기준이다. 이 문서에서 처음 나오는 용어도 그 문서의 정의를 따른다.

v3에서 새로 쓰는 말의 요약이다(정의는 `15`가 기준이다):

| 용어 | 뜻 |
|---|---|
| 핵심 루프 | 읽는다 → 만든다 → 설명한다 → 반복한다 → 증거가 된다 (§5) |
| 러버덕 (rubber duck) | 사용자가 설명하고 AI가 답 없이 되묻는 대화 세션. 종료 시 정리 1회 (FR-25) |
| 코드 읽기 (reading, `READ_CODE`) | 큐레이션 저장소의 파일 1개·줄 범위 1개를 로컬에서 읽고 러버덕으로 설명하는 과제 (FR-27) |
| 사이드 프로젝트 | 배운 것을 적용하는 사용자의 프로젝트. 기본은 주문 시스템 (FR-26) |
| 단계 (S0~S7) | 구현 순서를 나타내는 ID. 기간·날짜가 없다 (§7) |
| M1 / M2 | M1 = S0~S3(쓸 수 있는 최소), M2 = S4~S7 (§7) |
| 실사용 시작 | M1 완료(= S3 완료). 성공 지표 8주의 기준점 (§6) |
| 확장 제안 | 여유가 있을 때 미뤄 둔 항목 복원·MUST 목표 +1을 제안하는 것. 축소 제안과 배타 (FR-05) |
| 학습 목표 · 목표일 | 학습 목표 = 학습 트랙 + 목표일(날짜 하나). budget horizon이자 계획 배치의 끝 (FR-03) |
| 소스 점검 | `READ_CODE` 저장소·읽기 단위를 사람이 주기적으로 다시 보는 절차 (FR-27, `19` §8.5) |
| 학습 트랙 (`TargetRole`) | 무엇을 공부하는지. `JAVA_BACKEND`(Java 백엔드)와 `JAVA_BACKEND_STARTER`(Java 백엔드 입문) 둘. 필수 skill·목표 레벨·과제 난이도 기본값이 다르다 (FR-03) |
| 재현 과제 (`REDO`) | 며칠 전에 마친 과제를 **AI 없이** 처음부터 다시 만드는 과제. 성공만 독립 구현 증거가 된다 (FR-28, `06` §5.10) |
| 독립 구현 증거 | AI가 거들지 않은 상태에서 만들어 낸 기록. 독립 해결한 challenge 평가와 성공한 재현 과제 (`06` §7.2) |
| 프로젝트 기록 | 사이드 프로젝트의 결정 기록(무엇을·선택지·왜)과 장애 기록(증상·발견·수정·예방) (FR-29) |
