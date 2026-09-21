# 02. User Scenarios & UX

> Status: Accepted (v3) · Last updated: 2026-09-20 · Related: DEC-10, DEC-12, DEC-18, DEC-27, DEC-28, ADR-041, ADR-042, `01-product-requirements.md`, `05-api-spec.md`, `06-learning-engine-rules.md`, `17-ai-integration.md`, `19-content-spec.md`
>
> 이 문서는 Flutter 클라이언트의 **화면 ID(SCR), 라우트, 화면별 레이아웃·데이터·상태·검증·문구, 핵심 흐름, 오류 코드별 UI 처리, 공통 컴포넌트, 접근성, PWA, 테마 토큰**을 정의한다. 기능 규칙은 `01`의 FR, 요청·응답 필드는 `05-api-spec.md`가 기준이다. API는 base `/api/v1`을 생략하고 `METHOD path`로 적는다.
>
> **v3 (2026-09-18)** — 핵심 루프 "읽는다 → 만든다 → 설명한다 → 반복한다"(`01` §5)에 맞춰 SCR-RUBBER-DUCK·SCR-READ-CODE·SCR-PROJECTS를 추가하고(§3.16), 온보딩을 5단계(짧은 진단·사이드 프로젝트)로 바꾸고, 기한 역산 확장 제안을 SCR-REPLAN에 넣었다. 핵심 루프 하루 시나리오는 §4.16이다.
>
> 온보딩 1단계에서 **학습 트랙**을 고르고(FR-03), SCR-TODAY에 **재현 과제** 카드와 완료 질문이 있으며(FR-28, §4.18), 사이드 프로젝트의 **결정·장애 기록**은 SCR-PROJECT-DETAIL·SCR-PROJECT-NOTE-EDIT에서 남긴다(FR-29, §4.19).
>
> **2026-09-20** — 짧게 읽는 것(오늘의 팁)과 말을 고르는 것(용어 사전)을 넣었다. 새 화면은 **SCR-TIPS · SCR-TIP-DETAIL · SCR-TERMS · SCR-TERM-DETAIL**(§3.17)이고, SCR-TODAY(팁 카드·`whyItMatters`·확인 목록)·SCR-SKILL-DETAIL(학습 단계 6칸)·SCR-DASHBOARD(이번 주에 만든 것·연속 학습 일수)·SCR-PROJECT-DETAIL(프로젝트 종류·Markdown 내려받기)을 넓혔다. 팁·용어·확인 목록 본문은 DB가 아니라 **콘텐츠**이고(ADR-041, `19-content-spec.md` §3.9~§3.11) 학습 단계는 저장하지 않고 기존 기록에서 계산한다(ADR-042, `06` §5.11).
>
> **2026-09-21** — **개념 읽기**(`READING` 과제)가 무엇을 읽어야 하는지 알려 준다. SCR-TODAY의 `READING` 카드에 자료 제목·출처·"자료 열기"(새 탭)·"읽고 답할 3가지"를 붙였다(§3.5). **새 화면은 없다** — 자료 블록은 Today 카드 안에 있고 `GET /readings/{readingKey}`가 코드 읽기(`kind = CODE`)와 개념 읽기(`kind = CONCEPT`) 두 종류를 돌려준다(`05` §19.7). 자료 본문은 DB가 아니라 콘텐츠다(`19` §3.13). 자료가 없는 skill의 `READING`은 전과 똑같이 보인다.
>
> 클라이언트 스택: Flutter web(PWA), Riverpod, go_router, dio, freezed(DEC-10, DEC-12). 인증은 `AUTH_MODE` dart-define으로 고른다 — `dev`(기본: `POST /api/v1/dev/token`) / `supabase`(Later: `supabase_flutter`).

---

## 1. UX 원칙

| # | 원칙 | 화면 규칙 |
|---|---|---|
| U-1 | **오늘 중요한 것 하나** | 로그인 후 첫 화면은 항상 SCR-TODAY다. 차트가 아니라 main task 1개가 화면 중심이다. 한 화면의 primary 버튼(filled)은 1개다 |
| U-2 | **낮은 결정 비용** | 선택지는 칩·세그먼트로 3~7개만 준다. 모든 입력에 기본값을 채운다(Today 시간 = 설정값, 컨디션 = `NORMAL`). 설정·온보딩은 나중에 고칠 수 있음을 알린다 |
| U-3 | **죄책감 UI 금지** | 쉰 날 수, "놓친" 과제 수, 빨간 경고 숫자, 연속 기록이 끊겼다는 표시를 하지 않는다. 누적 지표("이번 주에 만든 것", "이번 주 학습 시간")만 보여준다. **연속 학습 일수는 SCR-DASHBOARD 한 곳에서만, 1일 이상일 때만, 목표·불꽃·재촉 문구 없이 사실 한 줄로** 보인다(§3.11). 단, deadline risk는 숨기지 않고 사실과 다음 행동으로 보여준다 |
| U-4 | **근거와 AI 확신도를 숨기지 않는다** | Today 이유(1~3개), 복습 등급 조정 사유(`adjustedBy`), skill 레벨 변경 근거(rule), finding의 verification·confidence 배지를 항상 텍스트로 보여준다. AI가 만든 내용에는 "AI" 표시를 붙인다 |
| U-5 | **BUG와 더 나은 선택을 구분** | `BUG`는 "실제 오류 가능성"으로 분명히 말한다. `RISK`는 "지금은 동작하지만 문제가 될 수 있음", `LEARNING_POINT`는 "틀린 것이 아니라 더 나은 선택"으로 표현하고 오류 색을 쓰지 않는다 |
| U-6 | **생각 먼저, 도움은 한 단계씩** | 답·힌트·분석 전에 self-explanation/self-review 입력을 먼저 보여준다. 힌트는 다음 단계 버튼만 활성화한다 |
| U-7 | **긴 AI 응답은 접는다** | 요약 1~2줄 + "자세히" 펼침. 코드·원문은 기본 접힘 |
| U-8 | **기다림을 막지 않는다** | 비동기 AI 작업 중에도 다른 화면으로 이동할 수 있고 결과는 저장된다고 알린다 |
| U-9 | **읽고 만든 뒤에는 설명하게 한다** | 코드 읽기·프로젝트 과제·문제 결과·복습 끝에 "설명하기(러버덕)" 진입점을 둔다. 러버덕 화면에서 AI 말풍선은 질문만 담고, 정답·채점 표시(✓/✗, "맞아요")를 두지 않는다(RD-1) |

---

## 2. 정보 구조 · 네비게이션

### 2.1 Breakpoint

폭은 `MediaQuery.sizeOf(context).width`(논리 픽셀) 기준이다.

| 구간 | 폭 | 내비게이션 | 콘텐츠 폭 |
|---|---|---|---|
| Mobile | < 600 | 하단 `NavigationBar` 4개: **Today · Review · Plan · More** | 전체 폭, 좌우 여백 16 |
| Tablet | 600 ~ 1023 | 좌측 `NavigationRail`(아이콘 + 아래 라벨, 폭 80), 데스크톱과 같은 목적지 | 최대 720, 가운데 정렬 |
| Desktop | ≥ 1024 | 좌측 `NavigationRail` extended(아이콘 + 오른쪽 라벨, 폭 220) | 최대 960. 2단 레이아웃을 쓰는 화면은 §3에 명시 |

- 최소 지원 폭은 360이다. 360~599는 같은 레이아웃이다.
- 화면 방향 전환이나 창 크기 변경 시 입력 중인 내용은 유지한다(상태는 Riverpod provider에 둔다).
- **하단 탭은 v3에서도 4개(Today · Review · Plan · More)를 유지한다.** 러버덕은 중심 기능이지만 탭으로 두지 않는다 — ① 러버덕 세션은 항상 대상(읽은 코드, 문제, 복습 카드, 개념, 프로젝트 작업)이 있어야 시작되므로 그 대상 화면에서 여는 편이 결정 비용이 낮고(U-2), ② 세션 목록 API가 없어(`05` §9.10) 탭 첫 화면이 빈 화면이 되며, ③ Today가 `READ_CODE`·`PROJECT_TASK`로 러버덕을 매일 끌어오고 진행 중 세션은 Today에서 이어 열 수 있고(§3.5), ④ 360px에서 탭 5개는 라벨이 좁아진다. 사이드 프로젝트는 자주 여는 화면이 아니므로 More(모바일)·rail(태블릿·데스크톱)에 둔다.

### 2.2 목적지

| 목적지 | 아이콘(Material Symbols) | 라우트 | Mobile | Tablet·Desktop | Sprint |
|---|---|---|---|---|---|
| Today | `today` | `/today` | 탭 | rail | S2 (S1은 SCR-PLAN으로 대체) |
| Review | `style` | `/review` | 탭 | rail | S2 |
| Training | `fitness_center` | `/training` | More | rail | S3 |
| Coach | `rate_review` | `/coach` | More | rail | S4 |
| Plan | `timeline` | `/plan` | 탭 | rail | S1 |
| Projects | `code_blocks` | `/projects` | More | rail | S1 |
| Skill | `account_tree` | `/skills` | More | rail | S1 |
| Evidence | `workspace_premium` | `/evidence` | More | rail | S6 |
| Radar | `radar` | `/radar` | More | rail | S7 |
| Settings | `settings` | `/settings` | More | rail (하단 고정) | S1 |
| More | `menu` | `/more` | 탭 | 없음 | S2 |

- Dashboard(`/dashboard`)와 Weekly(`/weekly`)는 목적지가 아니다. SCR-TODAY 헤더의 "진행 현황" 링크, SCR-MORE, SCR-PLAN에서 진입한다.
- 러버덕(`/rubber-duck/*`)은 목적지가 아니다. 진입점: SCR-READ-CODE "읽었으면 설명하기", SCR-TODAY(PROJECT_TASK·EXPLAIN 카드, 진행 중 세션 이어 하기), SCR-TRAINING-ATTEMPT 결과, SCR-REVIEW-SESSION 끝 화면, SCR-REVIEW-ITEMS 메뉴, SCR-SKILL-DETAIL "개념 설명하기", SCR-PROJECTS·SCR-PROJECT-DETAIL "이 프로젝트 작업 설명하기", SCR-PROJECT-DETAIL 기록 카드 `⋮` "러버덕으로 설명하기". 러버덕 화면은 하단 탭·rail을 숨기는 집중 화면이다(SCR-REVIEW-SESSION과 같음).
- 사이드 프로젝트 상세(`/projects/:sideProjectId`)와 기록 편집(`…/notes/*`)도 목적지가 아니다. 진입점은 SCR-PROJECTS 카드와 SCR-TODAY `PROJECT_TASK` 카드의 "이 프로젝트 기록"이다.
- 팁(`/tips*`)과 용어(`/terms*`)도 목적지가 아니다. 진입점: 팁은 SCR-TODAY 오늘의 팁 카드 "자세히"와 SCR-MORE "오늘의 팁", 용어는 SCR-REVIEW-HOME "용어 찾기"와 SCR-MORE "용어 사전", SCR-TIP-DETAIL·SCR-TERM-DETAIL 안의 용어 링크다. 하단 탭·rail을 그대로 두는 보통 화면이다(러버덕 같은 집중 화면이 아니다).
- 현재 라우트가 목적지 하위(예: `/training/attempts/…`, `/today/read/…`)면 해당 목적지를 선택 상태로 표시한다. `/dashboard`, `/weekly/*`, `/tips*`는 Mobile에서 More, 그 외에서는 Today를 선택 상태로 표시한다. `/terms*`는 Review를 선택 상태로 표시한다(복습 카드를 만드는 화면이다).
- 하위 화면은 앱 바 뒤로가기(`←`)를 둔다. 목적지 화면에는 뒤로가기가 없다.
- 브라우저 뒤로가기는 go_router 히스토리를 따른다. 작성 중인 입력이 있으면 §6.8 이탈 확인을 띄운다.

### 2.3 라우트 표 (go_router)

- `인증` = 저장된 access token 필요. `온보딩` = `GET /me.onboardingCompleted=true` 필요.
- 경로 파라미터는 UUID(주간 리뷰만 `yyyy-MM-dd`, 코드 읽기만 reading key `^[A-Z0-9][A-Z0-9_.]{2,149}$`, 팁만 tip key `^TIP\.[A-Z][A-Z0-9_]*\.[A-Z][A-Z0-9_]*\.[0-9]{3}$`, 용어만 term key `^TERM\.[A-Z][A-Z0-9_]*\.[A-Z][A-Z0-9_]*$` — 각 ≤ 120자, `05` §20). 형식이 틀리면 SCR-NOT-FOUND.
- `/`는 `/today`로 redirect한다. S1(Today 출시 전)에는 `/plan`으로 redirect한다.

| 경로 | SCR ID | 인증 | 온보딩 | Sprint |
|---|---|---|---|---|
| `/login` | SCR-LOGIN | N | N | S0 |
| `/auth/callback` | SCR-AUTH-CALLBACK | N | N | S0 |
| `/not-allowed` | SCR-NOT-ALLOWED | N (세션만 있음) | N | S1 |
| `/onboarding/goal` | SCR-ONBOARDING (1 목표) | Y | 미완료만 | S1 |
| `/onboarding/time` | SCR-ONBOARDING (2 시간) | Y | 미완료만 | S1 |
| `/onboarding/level` | SCR-ONBOARDING (3 현재 수준 — 짧은 진단 / 자기평가) | Y | 미완료만 | S1 (진단 선택지 S3) |
| `/onboarding/project` | SCR-ONBOARDING (4 사이드 프로젝트) | Y | 미완료만 | S1 |
| `/onboarding/plan` | SCR-ONBOARDING (5 계획 확인) | Y | 완료 직후 | S1 |
| `/today` | SCR-TODAY | Y | Y | S2 |
| `/today/diagnostics` | SCR-DIAGNOSTICS | Y | Y | S3 |
| `/today/read/:readingKey` | SCR-READ-CODE | Y | Y | S3 |
| `/lessons/:lessonKey` | SCR-LESSON | Y | Y | S3 |
| `/tips` | SCR-TIPS | Y | Y | S3 |
| `/tips/:tipKey` | SCR-TIP-DETAIL | Y | Y | S3 |
| `/terms` | SCR-TERMS | Y | Y | S3 |
| `/terms/:termKey` | SCR-TERM-DETAIL | Y | Y | S3 |
| `/rubber-duck/new` | SCR-RUBBER-DUCK (시작 전) | Y | Y | S3 |
| `/rubber-duck/:sessionId` | SCR-RUBBER-DUCK | Y | Y | S3 |
| `/projects` | SCR-PROJECTS | Y | Y | S1 |
| `/projects/:sideProjectId` | SCR-PROJECT-DETAIL | Y | Y | S3 |
| `/projects/:sideProjectId/notes/new` | SCR-PROJECT-NOTE-EDIT (생성) | Y | Y | S3 |
| `/projects/:sideProjectId/notes/:noteId` | SCR-PROJECT-NOTE-EDIT (수정) | Y | Y | S3 |
| `/dashboard` | SCR-DASHBOARD | Y | Y | S2 (최소) / S5 (완성) |
| `/review` | SCR-REVIEW-HOME | Y | Y | S2 |
| `/review/session` | SCR-REVIEW-SESSION | Y | Y | S2 |
| `/review/items` | SCR-REVIEW-ITEMS | Y | Y | S2 |
| `/review/items/new` | SCR-REVIEW-ITEM-EDIT (생성) | Y | Y | S2 |
| `/review/items/:reviewItemId` | SCR-REVIEW-ITEM-EDIT (수정) | Y | Y | S2 |
| `/training` | SCR-TRAINING-LIST | Y | Y | S3 |
| `/training/challenges/:challengeId` | SCR-CHALLENGE-DETAIL | Y | Y | S3 |
| `/training/attempts/:attemptId` | SCR-TRAINING-ATTEMPT | Y | Y | S3 |
| `/coach` | SCR-COACH-LIST | Y | Y | S4 |
| `/coach/new` | SCR-COACH-NEW | Y | Y | S4 |
| `/coach/:reviewId` | SCR-COACH-DETAIL | Y | Y | S4 |
| `/plan` | SCR-PLAN | Y | Y | S1 |
| `/plan/replan` | SCR-REPLAN | Y | Y | S1 (저장만) / S2 (미리보기·축소/확장 제안) |
| `/plan/goal` | SCR-LEARNING-GOAL | Y | Y | S1 |
| `/plan/versions` | SCR-PLAN-HISTORY | Y | Y | S1 |
| `/plan/versions/:planId` | SCR-PLAN-VERSION | Y | Y | S1 |
| `/skills` | SCR-SKILL-TREE | Y | Y | S1 |
| `/skills/:skillId` | SCR-SKILL-DETAIL | Y | Y | S1 (이력 S3) |
| `/weekly` | SCR-WEEKLY-LIST | Y | Y | S5 |
| `/weekly/:weekStartDate` | SCR-WEEKLY-DETAIL | Y | Y | S5 |
| `/evidence` | SCR-EVIDENCE-LIST | Y | Y | S6 |
| `/evidence/new` | SCR-EVIDENCE-DETAIL (작성) | Y | Y | S6 |
| `/evidence/:evidenceId` | SCR-EVIDENCE-DETAIL | Y | Y | S6 |
| `/radar` | SCR-REQUIREMENTS-LIST | Y | Y | S7 |
| `/radar/new` | SCR-REQUIREMENT-NEW | Y | Y | S7 |
| `/radar/:requirementDocId` | SCR-REQUIREMENT-DETAIL | Y | Y | S7 |
| `/settings` | SCR-SETTINGS | Y | N | S1 (AI 사용량 S3, 캘린더 S5, export·삭제 S6) |
| `/settings/delete-account` | SCR-ACCOUNT-DELETE | Y | N | S6 |
| `/more` | SCR-MORE | Y | Y | S2 (폭 ≥ 600이면 `/today`로 redirect) |
| 그 외 | SCR-NOT-FOUND | N | N | S0 |

쿼리 파라미터:

| 경로 | 파라미터 | 용도 |
|---|---|---|
| `/login` | `from` (URL 인코딩된 경로), `reason` (`expired`, `deleted`) | 로그인 후 복귀, 안내 문구 |
| `/today` | `complete=<taskId>` | 완료 시트를 바로 연다 (Training·Review에서 복귀) |
| `/projects/:sideProjectId/notes/new` | `noteType` (필수, `DECISION` \| `INCIDENT`) | 어느 유형을 만들지. 유형은 생성 후 바꿀 수 없다(PN-2) |
| `/review/session` | `taskId` | Today REVIEW task에서 진입 |
| `/review/items` | `skillId`, `status` | 필터 초기값 |
| `/training` | `skillId` | 필터 초기값 |
| `/training/challenges/:challengeId` | `taskId` | Today CHALLENGE task에서 진입 |
| `/today/read/:readingKey` | `taskId` (필수) | Today READ_CODE task. 러버덕 대상(`CODE_READING`의 `targetId`)이자 완료 대상. 없으면 읽기 안내만 보이고 "설명하기"는 비활성 |
| `/tips` | `series` (`TipSeries`), `level` (`TipLevel`) | 목록 필터 초기값 |
| `/tips/:tipKey` | `from=today` | SCR-TODAY 팁 카드에서 왔을 때. 뒤로가기가 없으면 `/today`로 돌아간다 |
| `/terms` | `q` (검색어), `skillId` | 검색어·기술 필터 초기값 |
| `/rubber-duck/new` | `targetType` (필수), `targetId` 또는 `conceptKey`, `skillCode`(선택), `taskId`(선택) | 시작 전 화면. `05` §9.6 `RubberDuckStartRequest`와 같은 값. `taskId`는 Today task에서 왔을 때 끝나고 돌아갈 곳(`/today?complete=`) |
| `/rubber-duck/:sessionId` | `taskId` (선택) | 같음 |
| `/coach/new` | `taskId` | Today COACH_REVIEW task에서 진입 |
| `/plan/replan` | `from=goal` | 목표 변경 후 진입 안내 |
| `/settings/delete-account` | `reauth=1` | 재로그인 직후 |

### 2.4 본문 표기와 복사 (마크다운, 코드, 선택)

콘텐츠와 AI 출력은 **마크다운**으로 쓰여 있다(문제의 상황·지시·제약, 힌트, 평가 근거, 복습 카드의 문항·정답, 개념 읽기, 러버덕 말풍선, 과제 설명). 본문은 `MarkdownText`로 그린다. `Text`로 그대로 찍으면 ```` ``` ````과 `#`이 글자로 보인다.

| 규칙 | 내용 |
|---|---|
| 코드 블록 | 고정폭 글꼴, 옅은 배경 카드, **가로 스크롤**(줄바꿈하지 않는다 — 360px에서도 화면이 넓어지지 않는다), 왼쪽 위에 언어, 오른쪽 위에 복사 버튼(`code.copyButton`) |
| 인라인 코드 | 고정폭 + 옅은 배경 |
| 그림 | **불러오지 않는다.** 앱도 서버도 콘텐츠가 가리키는 것을 fetch하지 않으므로(`07` §4.2) `![alt](url)`은 alt 글자로 보인다 |
| raw HTML | 파서에서 끈다. `<script>`는 글자 그대로 남는다. 같은 위젯이 AI 출력과 사용자가 쓴 글도 그린다 |
| 링크 | 새 탭으로 열기만 한다(http·https). 앱이 그 페이지를 직접 불러오지 않는다 |

**선택과 복사** — 화면 본문은 `ScreenBody` 하나가 **선택 범위 하나**다. 본문의 어떤 글자든 드래그해 복사할 수 있고, 한 번의 드래그가 화면 전체를 가로지른다. 지켜야 할 것 둘:

- 안에 `SelectionArea`를 **또 두지 않는다.** 선택 범위가 쪼개져 드래그가 중간에 끊긴다.
- 화면이 **보일 때만** 선택 범위를 만든다(`TickerMode.of(context)`). Navigator가 뒤에 살려 둔 화면은 레이아웃되지 않는데, 그 안의 선택 범위가 위치로 정렬하려 하면 크기가 없는 render box를 읽어 터진다.
- 진행 표시기(`CircularProgressIndicator` 등)는 `SelectionContainer.disabled`로 선택에서 뺀다. 고를 글자가 없고, 회전 변환 때문에 같은 문제를 일으킨다.
- 입력칸은 자기 선택을 그대로 쓴다. **붙여넣기를 막는 곳은 없다** — 재현 과제(`REDO`)도 예외가 아니다.

---

### 2.5 Redirect 규칙과 feature flag

go_router `redirect`는 아래 순서로 평가하고 처음 해당하는 규칙에서 멈춘다.

| 순서 | 조건 | 이동 |
|---|---|---|
| 1 | 인증 필요 경로 + 저장된 토큰 없음 | `/login?from=<현재 경로>` |
| 2 | 세션 있음 + `/login` | `from` 또는 `/today` |
| 3 | 세션 있음 + `meProvider` 로딩 중 | 현재 위치 유지, 전체 화면 로딩(SCR-AUTH-CALLBACK과 같은 표시) |
| 4 | `meProvider` 오류 `USER_NOT_ALLOWED` | `/not-allowed` |
| 5 | `meProvider.status == DELETION_REQUESTED` (삭제 요청 계정은 `GET /me`만 200, 그 외 API는 403 `FORBIDDEN`) | 토큰 삭제(`supabase` 모드면 `signOut`) → `/login?reason=deleted` |
| 6 | `onboardingCompleted=false` + 온보딩 필요 경로 | `/onboarding/goal` |
| 7 | `onboardingCompleted=true` + `/onboarding/goal|time|level|project` | `/today` |
| 8 | 경로의 feature flag가 꺼짐 | `/today` (S1은 `/plan`) |

- `meProvider`는 `GET /me` 결과다. 앱 시작, 로그인 직후, 브라우저 탭이 다시 보일 때(`visibilitychange`), AI 관련 429/503 응답 직후 다시 읽는다.
- feature flag는 `lib/app/feature_flags.dart`의 `const bool` 값이며 빌드 시 `--dart-define=DEVPILOT_FEATURES=today,review,…`로 켠다. 꺼진 기능의 목적지·진입 버튼·라우트는 모두 숨긴다. 플래그 이름과 켜지는 단계: `projects`(S1), `today`·`review`·`replan_preview`(S2), `training`·`diagnostics`·`ai_usage`·`review_evaluate`·`read_code`·`rubber_duck`·`tips`·`terms`(S3), `coach`(S4), `dashboard_full`·`weekly`·`calendar`·`challenge_generate`(S5), `evidence`·`account`(S6), `radar`(S7). `diagnostics`가 꺼져 있으면 온보딩 3단계는 자기평가만 보인다. `rubber_duck`이 꺼져 있으면 모든 러버덕 진입 버튼을 숨긴다. `challenge_generate`는 SCR-TRAINING-LIST "AI로 문제 만들기"만 켠다. `tips`가 꺼져 있으면 SCR-TODAY 오늘의 팁 카드와 팁 진입점을, `terms`가 꺼져 있으면 용어 진입점과 SCR-TERM-DETAIL "복습 카드 만들기"를 숨긴다.

---

## 3. 화면 명세

### 3.1 표기 · 공통 규칙

**화면 명세 항목**: 목적 · 진입 경로 · 레이아웃(모바일 기준, 폭 360) · 컴포넌트 · 데이터 · 상태 · 사용자 행동과 검증 · 주요 문구. 상태 칸에 "공통"이라고 쓴 것은 §3.3 기본 동작을 그대로 쓴다는 뜻이다.

- **Sprint** 칸의 S0~S7은 구현 단계 ID다(`01` §7). 기간·날짜가 없다. M1 = S0~S3(S3 완료 = 실사용 시작), M2 = S4~S7.
- 와이어프레임 안의 날짜·이름·숫자는 모두 **예시**다. 목표일은 사용자가 등록한 값이 들어간다.

**문구 key와 l10n**
- 파일: `lib/l10n/app_ko.arb` 하나(한국어만, `01` §10.1 C-3). `flutter gen-l10n`을 쓴다.
- 이 문서의 key는 점 표기(`today.generate.button`)다. ARB key는 점을 없애고 lowerCamelCase로 바꾼다: `today.generate.button` → `todayGenerateButton`, `error.AI_UNAVAILABLE` → `errorAiUnavailable`.
- enum 라벨 key: `enum.<EnumName>.<VALUE>` (예: `enum.FindingType.BUG` → `enumFindingTypeBug`). 모르는 enum 값(`unknown`)은 `enum.unknown` = "알 수 없음"으로 표시한다.
- 문구 안의 변수는 ARB placeholder(`{minutes}`)로 둔다.

**표시 형식**

| 값 | 형식 | 예 |
|---|---|---|
| plan date | `M월 d일 (E)` | 10월 13일 (화) |
| instant | `/me.timezone` 기준 `M월 d일 a h:mm` | 10월 13일 오후 9:05 |
| 분 | < 60: `{m}분`, ≥ 60: `{h}시간 {m}분` (m=0이면 생략) | 1시간 30분 |
| bp | `floorDiv(bp, 100)` + `%` | 7250 → 72% |
| milli hint | `floorDiv(milli, 100) / 10` 소수 1자리 + `단계` | 1850 → 1.8단계 |
| null 지표 | `—` + 보조 문구 `common.metric.notEnough` | — 기록이 더 필요해요 |
| 난이도 | `L{difficulty}` + 라벨 | L2 작은 변형 |

**enum 라벨 (화면 공통)**

| Enum | 값 → 라벨 |
|---|---|
| `EnergyLevel` | LOW 낮음 · NORMAL 보통 · HIGH 좋음 |
| `TaskType` | RECALL 떠올리기 · REVIEW 복습 · CHALLENGE 문제 풀이 · PROJECT_TASK 프로젝트 과제 · COACH_REVIEW 코드 리뷰 · READING 개념 읽기 · READ_CODE 코드 읽기 · EXPLAIN 설명하기 · REDO AI 없이 재현 |
| `RubberDuckTargetType` | CODE_READING 읽은 코드 · CHALLENGE 문제 풀이 · REVIEW_ITEM 복습 카드 · CONCEPT 개념 · PROJECT_WORK 프로젝트 작업 |
| `SideProjectNoteType` | DECISION 결정 기록 · INCIDENT 장애 기록 |
| `SideProjectKind` | SIDE 지금 만드는 것 · PAST_WORK 지난 경험 |
| `RubberDuckStatus` | IN_PROGRESS 설명 중 · COMPLETED 정리함 · ABANDONED 그만둠 |
| `SideProjectStatus` | ACTIVE 진행 중 · PAUSED 잠시 멈춤 · DONE 완료 |
| `ExpansionKind` | RESTORE_DEFERRED 다시 포함 · RAISE_TARGET 목표 올리기 |
| `TaskStatus` | PLANNED 예정 · IN_PROGRESS 진행 중 · COMPLETED 완료 · SKIPPED 건너뜀 · DEFERRED 미룸 |
| `RiskLevel` | LOW 여유 · MEDIUM 보통 · HIGH 빠듯함 · CRITICAL 매우 빠듯함 |
| `Priority` | MUST 필수 · SHOULD 권장 · LATER 나중 |
| `MilestoneStatus` | PLANNED 예정 · IN_PROGRESS 진행 중 · DONE 완료 · DEFERRED 미룸 · DROPPED 제외 |
| `ReviewRating` | AGAIN 다시 · HARD 어려움 · GOOD 알맞음 · EASY 쉬움 |
| `ReviewType` | RECALL 떠올리기 · BUG_SPOT 버그 찾기 · EXPLAIN 설명 · CHOICE 선택 |
| `ReviewItemStatus` | ACTIVE 사용 중 · SUSPENDED 일시중지 · ARCHIVED 보관 |
| `HintLevel` | SELF_EXPLAIN 스스로 · QUESTION_ONLY 질문 · CONCEPT_HINT 개념 힌트 · DIRECTION 방향 · PSEUDOCODE 의사코드 · PARTIAL_CODE 부분 코드 · FULL_EXAMPLE 전체 예시 |
| `EvaluatedOutcome` | CORRECT 충족 · PARTIAL 일부 충족 · INCORRECT 보완 필요 · NOT_EVALUATED 채점 안 함 |
| `AttemptOutcome` | SOLVED_INDEPENDENTLY 스스로 해결 · SOLVED_WITH_HINTS 힌트로 해결 · PARTIAL 일부 해결 · FAILED 다시 도전 · ABANDONED 그만둠 |
| `FindingType` | BUG 버그 · RISK 위험 · LEARNING_POINT 더 나은 선택 |
| `FindingStatus` | OPEN 확인 전 · USER_RESPONDED 답변함 · RESOLVED 수정 완료 · DISMISSED 해당 없음 |
| `DiscoveredBy` | MENTIONED_UNPROMPTED 스스로 언급 · FOUND_AFTER_HINT 질문·힌트 후 발견 · MISSED 놓침 |
| `VerificationStatus` | VERIFIED 검증됨 · SUPPORTED 문서 근거 · AI_JUDGMENT AI 판단 · UNCERTAIN 불확실 |
| `Confidence` | HIGH 확신 높음 · MEDIUM 확신 보통 · LOW 확신 낮음 |
| `ThinkingAxis` | CORRECTNESS 정확성 · NULL_BOUNDARY null·경계 · RESOURCE_LIFECYCLE 자원 수명 · EXCEPTION_STRATEGY 예외 처리 · SECURITY 보안 · PERFORMANCE 성능 · CONCURRENCY 동시성 · OBSERVABILITY 관측성 · MAINTAINABILITY 유지보수성 · TRANSACTION_DATA_CONSISTENCY 트랜잭션·데이터 일관성 |
| `SkillAxis` | KNOWLEDGE 지식 · IMPLEMENTATION 구현 · EXPLANATION 설명 · DEBUGGING 문제 인지 |
| `SkillLevel` | 0 모름 · 1 본 적 있음 · 2 도움받아 가능 · 3 혼자 기본 가능 · 4 실무 적용 · 5 응용·전이 |
| `SkillCategory` | JAVA Java · SPRING Spring · DATABASE 데이터베이스·JPA · WEB_HTTP 웹·HTTP · NETWORK 네트워크 · CS CS 기초 · ALGORITHM 알고리즘·문제 풀이 · TESTING 테스트 · DEVOPS DevOps·배포 · SECURITY 보안 · INTEGRATION 연동 · PRACTICAL_ENGINEERING 실무 엔지니어링 · SYSTEM_DESIGN 시스템 설계 · EXPLANATION 기술 설명 |
| `TargetRole` (학습 트랙) | JAVA_BACKEND Java 백엔드 · JAVA_BACKEND_STARTER Java 백엔드 입문 · INTEGRATION_ENGINEER 연동·구축 엔지니어 |
| `LearningStage` | BUILD 만들기 · READ_CONCEPT 개념 읽기 · READ_CODE 코드 읽기 · EXPLAIN 설명하기 · REVIEW 복습 · REDO AI 없이 재현 |
| `TipSeries` | ERROR_READING 오류 읽기 · RESOURCE 자원 · LOGGING 로그 · HTTP_INTEGRATION HTTP 연동 · DATABASE 데이터베이스 · OPERATIONS 운영 · CONVENTION 관례 |
| `TipLevel` | BASIC 기본 · PRACTICAL 실무 |
| `TipFeedback` | KNEW_IT 알고 있었어요 · LEARNED 새로 알았어요 · WILL_TRY 직접 해 볼래요 |
| `ReadingFeedback` | HELPFUL 도움 됐어요 · TOO_HARD 어려웠어요 · BORING 지루했어요 |
| `CoachContentType` | CODE 코드 · DIFF Diff · LOG 로그 |
| `EvidenceStatus` | CANDIDATE 후보 · ACCEPTED 승인 · REJECTED 거절 |
| `RequirementType` | REQUIRED 필수 · PREFERRED 권장 |
| `RequirementFitCategory` | READY 준비됨 · STRETCH 도전 · LATER 나중에 · (null) 분류 불가 |
| `AsyncJobStatus` | PENDING 대기 중 · RUNNING 진행 중 · COMPLETED 완료 · FAILED 실패 |
| 난이도 1~5 | L1 기본 사용 · L2 작은 변형 · L3 개념 조합 · L4 실무형 · L5 트레이드오프·설계 |

### 3.2 입력 검증 (클라이언트 = 서버 상한)

클라이언트는 전송 전에 아래 규칙으로 검사하고, 위반 시 버튼을 비활성화하거나 필드 아래에 오류를 표시한다. 서버 `400 VALIDATION_FAILED`의 `errors[]`는 같은 필드 아래에 서버 `message`로 표시한다. 글자 수는 서버 `@Size`와 같은 UTF-16 code unit(Dart `String.length`) 기준이고, byte 상한은 `utf8.encode(text).length`로 센다(`05-api-spec.md` §17). 요청 body 전체 상한은 64KB다(`03` §3.1).

| 필드 | 화면 | 규칙 | 오류 key |
|---|---|---|---|
| `displayName` | SCR-ONBOARDING, SCR-SETTINGS | 앞뒤 공백 제거 후 1~100자 | `validation.required`, `validation.maxLength` |
| `timezone` | SCR-ONBOARDING, SCR-SETTINGS | 목록에서 선택(IANA, `timezone` 패키지 목록) | — |
| `dayStartHour` | SCR-ONBOARDING, SCR-SETTINGS | 0~6 선택 | — |
| `weekdayStudyMinutes`, `weekendStudyMinutes` | SCR-ONBOARDING, SCR-SETTINGS | 정수 0~720 | `validation.range` |
| `availableMinutes` | SCR-TODAY | 정수 5~720 | `validation.range` |
| `targetCompletionDate` (목표일) | SCR-ONBOARDING, SCR-LEARNING-GOAL | 필수, 내일(plan-day + 1) ≤ 값 ≤ 오늘+3년. 날짜 선택기도 이 범위만 연다 | `validation.dateFuture`, `validation.dateTooFar` |
| `focusSkillCodes` | SCR-ONBOARDING, SCR-LEARNING-GOAL | 0~10개 | `validation.maxItems` |
| 카테고리 자기평가 | SCR-ONBOARDING | 자기평가를 고른 경우에만. `SkillCategory` 값 수(14개) 모두 0~4 (기본 0). 짧은 진단을 고르면 보내지 않는다(`[]`) | — |
| 사이드 프로젝트 `name` | SCR-ONBOARDING, SCR-PROJECTS | 앞뒤 공백 제거 후 1~100자 (온보딩 기본값 "주문 시스템") | `validation.required`, `validation.maxLength` |
| 사이드 프로젝트 `description` | SCR-PROJECTS | 0~1000자 | `validation.maxLength` |
| 사이드 프로젝트 `repoUrl` | SCR-PROJECTS | 선택, `http://` 또는 `https://`, ≤ 500자 | `validation.url`, `validation.maxLength` |
| 사이드 프로젝트 `stack` | SCR-PROJECTS | 0~300자 | `validation.maxLength` |
| 프로젝트 기록 `title` | SCR-PROJECT-NOTE-EDIT | 앞뒤 공백 제거 후 1~200자 | `validation.required`, `validation.maxLength` |
| 프로젝트 기록 `occurredOn` | SCR-PROJECT-NOTE-EDIT | 필수, 오늘(plan-day) 이하. 날짜 선택기도 이 범위만 연다 | `validation.required`, `validation.datePast` |
| 프로젝트 기록 본문 항목 | SCR-PROJECT-NOTE-EDIT | 고른 유형의 항목 **전부** 필수, 각 1~4000자. 유형에 맞지 않는 항목은 화면에 없다 | `validation.required`, `validation.maxLength` |
| 프로젝트 기록 `skillCode` | SCR-PROJECT-NOTE-EDIT | 선택. skill 검색 목록에서 고른다 | — |
| `redoWithoutAi` | SCR-TODAY 완료 시트 | `REDO` 과제를 완료할 때 **필수**, 예/아니오 | `validation.required` |
| `explainedNote` | SCR-TODAY 완료 시트 | `EXPLAIN`·`READ_CODE` 과제를 완료할 때만. 0~500자이고 `explainedToPerson = true`일 때만 보낸다 | `validation.maxLength` |
| 용어 검색어 `q` | SCR-TERMS | 0~100자. 앞뒤 공백은 지우고 보낸다 | `validation.maxLength` |
| 러버덕 `explanation` | SCR-RUBBER-DUCK | 공백 아닌 문자 1개 이상, ≤ 2000자(`devpilot.rubberduck.max-explanation-chars`), private key 정규식 불일치 | `validation.required`, `validation.maxLength`, `rubberDuck.validation.privateKey` |
| milestone `title` | SCR-REPLAN | 1~200자 | `validation.required`, `validation.maxLength` |
| milestone `description` | SCR-PLAN, SCR-REPLAN | 0~2000자 | `validation.maxLength` |
| milestone 기간 | SCR-REPLAN | 오늘−1년 ~ 오늘+3년, `startDate ≤ endDate` | `validation.dateRange` |
| milestone 수 | SCR-REPLAN | 1~24개 | `validation.milestoneCount` |
| milestone `skillCodes` | SCR-REPLAN | 0~30개 | `validation.maxItems` |
| replan `reason` | SCR-REPLAN | 저장 시 필수 1~1000자 (미리보기는 선택) | `validation.required`, `validation.maxLength` |
| `actualMinutes` | SCR-TODAY 완료 시트 | 정수 0 ~ min(720, ⌈세션 경과 분 × 1.5⌉) | `validation.range` |
| `selfReflection` | SCR-TODAY 완료 시트 | 0~5000자 | `validation.maxLength` |
| 복습 `answerText` | SCR-REVIEW-SESSION | 0~5000자 | `validation.maxLength` |
| 수동 카드 `prompt` | SCR-REVIEW-ITEM-EDIT | 1~2000자 | `validation.required`, `validation.maxLength` |
| 수동 카드 `expectedAnswer` | 같음 | 1~3000자 | 같음 |
| 수동 카드 `rubric` | 같음 | 1~6개, 각 1~500자 | `validation.required`, `validation.maxItems`, `validation.maxLength` |
| self-explanation `text` | SCR-TRAINING-ATTEMPT | 1~5000자 (건너뛰기는 별도 버튼) | `validation.required`, `validation.maxLength` |
| 제출 `answerText` / `code` | SCR-TRAINING-ATTEMPT | 둘 중 하나 이상. answerText 0~5000자, code UTF-8 20000 bytes 이하, code가 있으면 `language` 필수 | `training.submit.validation.empty`, `validation.maxLength`, `training.submit.validation.language` |
| challenge 생성 `difficulty` / `targetMinutes` | SCR-TRAINING-LIST | 1~5 / 5~180 (칩으로만 입력) | — |
| coach `content` | SCR-COACH-NEW | 공백 아닌 문자 1개 이상, UTF-8 30000 bytes 이하, 1000줄 이하(줄 수 = `\n` 개수 + 마지막 문자가 `\n`이 아니면 1), private key 정규식 불일치 | `coach.new.validation.tooLarge`, `coach.new.validation.tooManyLines`, `coach.new.validation.privateKey` |
| coach `userSelfReview` | SCR-COACH-NEW | 0~5000자, private key 정규식 불일치 | `validation.maxLength` |
| coach `fileName` / `topic` | SCR-COACH-NEW | 0~200자 / 0~100자. `fileName`의 `/` `\`는 입력 시 제거 | `validation.maxLength` |
| coach `context.projectType` | SCR-COACH-NEW | 필수 선택 | — |
| coach `context.skillCodes` | SCR-COACH-NEW | 0~10개 | `validation.maxItems` |
| coach `confidentialConsent` | SCR-COACH-NEW | 반드시 체크 | `coach.new.validation.consent` |
| finding 응답 `text` | SCR-COACH-DETAIL | 1~5000자, private key 정규식 불일치 | `validation.required`, `validation.maxLength`, `coach.new.validation.privateKey` |
| evidence `title` | SCR-EVIDENCE-DETAIL | 1~200자 | `validation.required`, `validation.maxLength` |
| evidence `problem`·`analysis`·`action`·`result` | 같음 | 각 0~3000자. 승인 시 `problem`, `action`, `result` 필수 | `validation.maxLength`, `evidence.accept.validation.incomplete` |
| evidence `referenceLinks` | 같음 | 0~10개, `https://`로 시작, 각 ≤ 500자 | `validation.httpsUrl` |
| evidence `explanationTopics` | 같음 | 0~10개, 각 1~200자 | `validation.maxItems` |
| weekly `reflection` | SCR-WEEKLY-DETAIL | 0~5000자 | `validation.maxLength` |
| 로드맵 비교 `title` | SCR-REQUIREMENT-NEW | 1~200자 | `validation.required` |
| 로드맵 비교 `sourceUrl` | SCR-REQUIREMENT-NEW | 선택, `http://` 또는 `https://`, ≤ 2000자 | `validation.url` |
| 로드맵 비교 `sourceText` | SCR-REQUIREMENT-NEW | 공백 아닌 문자 1개 이상, UTF-8 20000 bytes 이하 | `radar.new.validation.tooLarge` |
| 계정 삭제 확인 문구 | SCR-ACCOUNT-DELETE | 정확히 `삭제합니다` | — (버튼 비활성) |

private key 정규식(서버와 동일): `-----BEGIN ((RSA|EC|DSA|OPENSSH|ENCRYPTED|PGP) )?PRIVATE KEY( BLOCK)?-----`

### 3.3 공통 상태

모든 화면은 아래 기본 동작을 쓴다. 화면별 차이는 각 SCR의 상태 칸에 적는다.

| 상태 | 판정 | 표현 | 문구 key |
|---|---|---|---|
| Loading (첫 로드) | provider `AsyncLoading`, 이전 데이터 없음 | 화면 레이아웃과 같은 모양의 skeleton(회색 블록, 1.2초 shimmer). **스피너 단독 전체 화면 금지** | — |
| Loading (재조회) | 이전 데이터 있음 | 기존 데이터 유지 + 앱 바 아래 2px `LinearProgressIndicator` | — |
| Empty | 목록 0건 | `EmptyState`(§6.4): 아이콘 + 설명 1줄 + 다음 행동 버튼 1개 | 화면별 |
| Error (조회) | 조회 실패 | 전체 영역 `ErrorView`: 코드별 문구(§5) + "다시 시도" + 접힌 "문제 신고 정보"(code, traceId, 시각, 복사 버튼) | `common.error.retry`, `common.error.reportInfo` |
| Error (행동) | 저장·전송 실패 | §5 표의 처리(토스트/인라인/대화상자). 입력 내용은 유지 | §5 |
| AI unavailable | `/me.aiStatus ∈ {DISABLED, BALANCE_EXHAUSTED}` 또는 `503 AI_UNAVAILABLE` 수신 | AI 진입 화면 상단 `AiUnavailableBanner`(§6.5). AI 버튼 비활성 + 버튼 아래 사유 1줄. 비-AI 기능은 그대로 | `ai.unavailable.banner` / `ai.balanceExhausted.banner` |
| Budget warning | `/me.aiStatus=BUDGET_WARNING` | AI 버튼 아래 `BudgetWarningNote`(§6.5). 동작은 막지 않음 | `ai.budgetWarning.note` |
| Async pending | 비동기 리소스 `PENDING`/`RUNNING` | `AsyncStatusIndicator`(§6.3). 2초 간격 polling, 최대 3분(90회). 3분 초과 시 polling을 멈추고 "나중에 다시 확인" 상태 + "다시 확인" 버튼(3분 창을 다시 시작). 화면을 벗어나면 polling 중지, 다시 들어오면 재시작 | `async.pending`, `async.leaveOk`, `async.checkLater`, `async.checkAgain` |
| Offline | 브라우저 `navigator.onLine=false` 또는 dio `connectionError` | 앱 바 아래 `OfflineBanner`. **오프라인 쓰기는 하지 않는다**: 저장·전송 버튼 비활성. 이미 메모리에 있는 데이터는 읽기 전용으로 표시. 첫 로드부터 오프라인이면 전체 영역 오프라인 상태 + "다시 시도". 복귀 시 배너 제거 후 현재 화면 조회를 다시 한다 | `offline.banner`, `offline.fullPage` |
| 세션 만료 | `401 AUTHENTICATION_REQUIRED` | `supabase` 모드: `refreshSession()` 1회 후 원요청 재시도. `dev` 모드: 갱신이 없으므로 바로 로그아웃 흐름. 실패하면 토큰 삭제 → `/login?reason=expired&from=<경로>` | `login.expired` |

공통 문구:

| key | 문구 |
|---|---|
| `common.error.retry` | 다시 시도 |
| `common.error.reportInfo` | 문제 신고 정보 |
| `common.error.copied` | 복사했어요 |
| `common.metric.notEnough` | 기록이 더 필요해요 |
| `async.pending` | AI가 작업하고 있어요. 보통 1~2분 걸려요. |
| `async.leaveOk` | 다른 화면으로 이동해도 결과는 저장돼요. |
| `async.checkLater` | 생각보다 오래 걸리고 있어요. 나중에 다시 확인해 주세요. |
| `async.checkAgain` | 다시 확인 |
| `offline.banner` | 오프라인이에요. 연결되면 저장할 수 있어요. |
| `offline.fullPage` | 인터넷에 연결되어 있지 않아요. |
| `validation.required` | 입력해 주세요 |
| `validation.maxLength` | {max}자 이하로 입력해 주세요 |
| `validation.range` | {min}~{max} 사이로 입력해 주세요 |
| `validation.maxItems` | 최대 {max}개까지 고를 수 있어요 |
| `validation.dateFuture` | 오늘 이후 날짜를 골라 주세요 |
| `validation.dateTooFar` | 3년 이내 날짜를 골라 주세요 |
| `validation.dateRange` | 시작일이 종료일보다 늦어요 |
| `validation.milestoneCount` | milestone은 1~24개여야 해요 |
| `validation.httpsUrl` | https:// 로 시작하는 주소를 입력해 주세요 |
| `validation.url` | http:// 또는 https:// 로 시작하는 주소를 입력해 주세요 |

### 3.4 인증 · 온보딩

#### SCR-LOGIN

- **목적**: 로그인한다. **진입**: 세션 없음(redirect), 로그아웃, 계정 삭제 후. **Sprint**: S0.
- **모드**: `AppConfig.authMode`(dart-define `AUTH_MODE`)가 화면을 고른다 — **`dev`(기본, 운영 포함)** 은 이메일 입력, `supabase`(Later)는 GitHub 버튼(ADR-033).
- **레이아웃 (`AUTH_MODE=dev`)**

```text
┌────────────────────────────────┐
│                                │
│            DevPilot            │
│                                │
│   목표한 날까지, 오늘 할        │
│   가장 중요한 공부 하나.        │
│                                │
│ ┌────────────────────────────┐ │
│ │ 이메일                      │ │
│ │ [                        ]  │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │
│ │          로그인             │ │
│ └────────────────────────────┘ │
│   초대받은 계정만 쓸 수 있어요.  │
│                                │
│ ┌────────────────────────────┐ │  reason=expired|deleted 일 때만
│ │ ⓘ 로그인이 만료됐어요.       │ │
│ │   다시 로그인해 주세요.      │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

- **레이아웃 (`AUTH_MODE=supabase`, Later)**: 이메일 입력 자리에 `FilledButton`(GitHub 아이콘) 하나.
- **컴포넌트**: 로고 텍스트, 설명, `TextField`(email, `autofillHints: [email]`, `keyboardType: emailAddress`) 또는 GitHub `FilledButton`, `FilledButton`(로그인), `InfoNote`.
- **데이터**
  - `dev`: "로그인" → `POST /api/v1/dev/token {email}` → 200이면 `accessToken`을 세션 저장소에 넣고 `GET /me`로 이동(SCR-AUTH-CALLBACK과 같은 분기). 403 `USER_NOT_ALLOWED` → `/not-allowed`.
  - `supabase`: `supabase.auth.signInWithOAuth(OAuthProvider.github, redirectTo: '<origin>/auth/callback?from=<from>')`.
- **상태**: Loading — 버튼 안 진행 표시, 다시 탭 불가. Error — 인라인 `login.error`(429는 `login.tooMany`). Offline — 공통 배너 + 버튼 비활성.
- **행동·검증**: `dev` 모드에서 이메일이 비었거나 형식이 아니면 버튼 비활성(제출 전 검증, 서버 400을 만들지 않는다).
- **문구**

| key | 문구 |
|---|---|
| `login.tagline` | 목표한 날까지, 오늘 할 가장 중요한 공부 하나. |
| `login.email.label` | 이메일 |
| `login.submit` | 로그인 |
| `login.github.button` | GitHub로 로그인 (`AUTH_MODE=supabase`) |
| `login.inviteOnly` | 초대받은 계정만 쓸 수 있어요. |
| `login.expired` | 로그인이 만료됐어요. 다시 로그인해 주세요. |
| `login.deleted` | 계정 삭제 요청을 받았어요. 몇 분 안에 데이터가 지워져요. |
| `login.error` | 로그인하지 못했어요. 다시 시도해 주세요. |
| `login.tooMany` | 시도가 많아요. 잠시 후 다시 해 주세요. |

> 이 로그인 방식은 서비스가 **tailnet 안에서만 접근된다**는 전제 위에 있다(ADR-033, `07` §3). 공개 노출 시에는 `AUTH_MODE=supabase`로 바꾼다.

#### SCR-AUTH-CALLBACK

- **목적**: OAuth redirect를 받아 세션을 확정하고 다음 화면으로 보낸다. **진입**: GitHub 인증 후 redirect. **Sprint**: S0.
- **레이아웃**: 가운데 로고 + `CircularProgressIndicator` + `auth.callback.loading`. (전체 화면 스피너를 허용하는 유일한 화면)
- **데이터**: (`supabase` 모드 전용) `supabase_flutter`가 URL의 세션을 저장 → `GET /me`. `dev` 모드에서는 이 화면을 거치지 않고 SCR-LOGIN이 바로 `GET /me`로 분기한다.
- **분기**: 200 + `onboardingCompleted=false` → `/onboarding/goal` / 200 + true → `from` 또는 `/today` / `403 USER_NOT_ALLOWED` → `/not-allowed` / 그 외 오류 → `ErrorView`(다시 시도 = `GET /me` 재호출, "로그아웃" 보조 버튼).
- **문구**: `auth.callback.loading` = "로그인하는 중이에요…"

#### SCR-NOT-ALLOWED

- **목적**: allowlist에 없는 계정에 사유와 다음 행동을 알린다. **진입**: `403 USER_NOT_ALLOWED`. **Sprint**: S1.
- **레이아웃**: 아이콘(`lock`) + 제목 + 설명 + `FilledButton` "다른 계정으로 로그인"(signOut → `/login`).
- **데이터**: 없음. **상태**: 없음.
- **문구**

| key | 문구 |
|---|---|
| `notAllowed.title` | 초대된 계정이 아니에요 |
| `notAllowed.body` | DevPilot은 초대받은 사람만 사용할 수 있어요. 초대받은 GitHub 계정으로 다시 로그인해 주세요. |
| `notAllowed.switchAccount` | 다른 계정으로 로그인 |

#### SCR-ONBOARDING

- **목적**: 5단계로 목표(무엇을·언제까지 — 학습 트랙과 목표일)·시간·현재 수준 확인 방식·사이드 프로젝트를 받고 계획 v1을 만든다(FR-02, FR-26 SP-1). 현재 수준은 **짧은 진단이 기본**이고, 건너뛰면 자기평가로 대체한다. **진입**: `onboardingCompleted=false` redirect. **Sprint**: S1 (3단계 "짧은 진단" 선택지는 `diagnostics` flag가 켜지는 S3).
- **공통 레이아웃**: 상단 진행 막대 + `n / 5`, 제목, 본문(스크롤), 하단 고정 버튼 영역(좌 "이전" `TextButton`, 우 "다음" `FilledButton`). 1단계에는 "이전"이 없다. 5단계에는 "이전"이 없다(이미 저장됨). 데스크톱은 폭 560 카드 가운데 정렬.
- **입력 보관**: ①~④ 입력은 `onboardingDraftProvider`에 두고, 새로고침 대비로 `localStorage` key `devpilot.onboarding.draft.<externalAuthId>`에 저장한다(읽기·쓰기 모두 try/catch, 실패해도 동작). 제출 성공 시 삭제한다.

**1단계 — 목표** (`/onboarding/goal`)

```text
┌────────────────────────────────┐
│ ━━━━━───────────────────── 1/5 │
│ 무엇을, 언제까지 공부할지 정해요  │
│                                │
│ 표시 이름 *                      │
│ [MT                          ]  │
│                                │
│ 학습 트랙 *                     │
│ ┌────────────────────────────┐ │
│ │ (●) Java 백엔드              │ │
│ │     Spring Boot·JPA·DB를 실무 │ │
│ │     수준으로 (필수 34개)      │ │
│ ├────────────────────────────┤ │
│ │ ( ) Java 백엔드 입문         │ │
│ │     개발을 막 시작했다면 여기 │ │
│ │     부터 (필수 14개, 쉬운 과제)│ │
│ ├────────────────────────────┤ │
│ │ ( ) 연동·구축 엔지니어        │ │
│ │     외부 시스템 연동과 배포·  │ │
│ │     운영 위주 (필수 22개)     │ │
│ └────────────────────────────┘ │
│ 트랙은 나중에 바꿀 수 없어요.     │
│                                │
│ 목표일 *                         │
│ [3개월 후] [6개월 후] [1년 후]    │
│ [직접 선택]                      │
│ 2027년 4월 1일 (목)             │
│ 목표일까지 남은 시간으로 무엇을   │
│ 먼저 할지 정해요.                │
│ ────────────────────────────── │
│                      [  다음  ] │
└────────────────────────────────┘
```

- 필드는 셋이다: 표시 이름(1~100자, 기본값 `GET /me.displayName`), **학습 트랙**(필수, 라디오 3개), 목표일(`targetCompletionDate`, 필수). 학습 목표는 무엇을(학습 트랙)·언제까지(목표일) 두 가지뿐이다(`01` FR-03).
- **학습 트랙 선택**: `JAVA_BACKEND`("Java 백엔드", 기본 선택), `JAVA_BACKEND_STARTER`("Java 백엔드 입문"), `INTEGRATION_ENGINEER`("연동·구축 엔지니어" — 기본기 위에 외부 시스템 연동과 배포·운영을 더 다루는 구성이다. 입문 트랙과 달리 낮은 수준판이 아니라 **다른 구성**이다, `19` §3.3). 순서는 `TargetRole` 선언 순서다. 각 항목은 이름 + 한 줄 설명 + 필수 skill 수를 보인다. 필수 skill 수는 `GET /skills/tree?role={트랙}`의 `roleTarget.priority = MUST` 개수로 채우고, 조회에 실패하면 설명만 보인다(숫자는 생략). 아래에 `onboarding.goal.track.locked`("트랙은 나중에 바꿀 수 없어요.")를 둔다 — 서버도 `PUT /learning-goal`에서 변경을 막는다(`05` §5.2).
- 트랙을 바꾸면 3단계(자기평가·진단)의 카테고리 목록과 5단계 계획 미리보기가 달라진다. 그래서 트랙 변경 시 3단계의 입력값을 지우고 다시 받는다(사용자에게 `onboarding.goal.track.reset` 토스트).
- 기본값: 목표일 없음(필수). 날짜는 사용자가 고른 값이다(와이어프레임의 날짜는 예시).
- 빠른 선택 칩: "3개월 후" = 오늘(plan-day) + 3개월, "6개월 후" = + 6개월, "1년 후" = + 1년, "직접 선택" = 날짜 선택기(범위 내일 ~ 오늘+3년). 칩을 누르면 계산한 날짜를 칩 아래에 보이고, 선택한 칩은 선택 상태로 둔다. 날짜를 직접 고르면 "직접 선택"이 선택 상태가 된다.
- 목표일 아래에 `onboarding.goal.completion.help`를 둔다. 이 날짜는 나중에 설정 > 학습 목표(SCR-LEARNING-GOAL)에서 바꿀 수 있다.
- 검증: 목표일은 내일(plan-day + 1)부터 오늘+3년까지(§3.2, 서버 `DATE_OUT_OF_RANGE`).
- "다음" 활성 조건: `displayName` 유효 + `targetCompletionDate` 유효(§3.2).

**2단계 — 시간** (`/onboarding/time`)

```text
┌────────────────────────────────┐
│ ━━━━━━━━━━──────────────── 2/5 │
│ 공부할 수 있는 시간은?           │
│                                │
│ 평일 하루                       │
│ [0][15][30][45✓][60][90][120]   │
│ [직접 입력]                     │
│ 주말 하루                       │
│ [0][30][60][120][180][240✓]     │
│ [직접 입력]                     │
│ 일주일에 약 11시간 45분          │
│                                │
│ 하루 시작 시각                   │
│ [ 새벽 4시            ▾ ]       │
│ 새벽 4시 전에 한 공부는 전날      │
│ 기록으로 남아요.                 │
│                                │
│ 시간대   Asia/Seoul   [변경]     │
│ ────────────────────────────── │
│ [이전]                [  다음  ] │
└────────────────────────────────┘
```

- 기본값: 평일 45, 주말 240, 시작 시각 4, 시간대 = `Intl.DateTimeFormat().resolvedOptions().timeZone`(실패 시 `Asia/Seoul`).
- "직접 입력"은 숫자 입력 대화상자(0~720). 주간 합계 = 평일×5 + 주말×2.
- 하루 시작 시각 선택지: 자정(0시)~새벽 6시.

**3단계 — 현재 수준: 짧은 진단 또는 자기평가** (`/onboarding/level`)

```text
┌────────────────────────────────┐
│ ━━━━━━━━━━━━━━━─────────── 3/5 │
│ 지금 수준을 확인해요             │
│                                │
│ ┌────────────────────────────┐ │
│ │ (●) 짧은 진단으로 시작 [권장] │ │
│ │ 분야마다 1문제, 최대 5문제예요.│ │
│ │ 계획을 만든 뒤 바로 풀어요.    │ │
│ │ 스스로 고르는 것보다 정확해요. │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │
│ │ ( ) 진단 건너뛰고 직접 고르기 │ │
│ │ 분야별 수준을 직접 골라요.     │ │
│ └────────────────────────────┘ │
│                                │
│ ▸ 지금 프로젝트에 필요한 기술     │
│   (선택, 최대 10개)              │
│ ────────────────────────────── │
│ [이전]                [  다음  ] │
└────────────────────────────────┘
```

"진단 건너뛰고 직접 고르기"를 고르면 카드 아래에 자기평가 칩이 펼쳐진다(v2와 같은 입력):

```text
│ (●) 진단 건너뛰고 직접 고르기     │
│ 대략이면 충분해요. 실제 레벨은   │
│ 학습 기록으로 정해져요.          │
│ Java                           │
│ [모름✓][본 적 있음][도움받아 가능]│
│ [혼자 가능][실무 적용]           │
│ ────────────────────────────── │
│ … 13개 카테고리                 │
```

- 기본 선택 = 짧은 진단(`runDiagnostic=true`, `selfAssessments=[]`). 자기평가를 고르면 `runDiagnostic=false`, `selfAssessments` 13개(`05` §4.1: 진단 모드에서 자기평가를 보내면 `MUTUALLY_EXCLUSIVE`, 자기평가 모드에서 비우면 `ONE_OF_REQUIRED`).
- `diagnostics` flag가 꺼진 빌드(S1~S2)는 두 카드 없이 자기평가 칩만 보여주고(제목 `onboarding.level.titleSelf`) `runDiagnostic=false`로 보낸다. 이 사용자는 S3 이후 자기평가 3 이상인 분야에 한해 Today에서 진단을 제안받는다(FR-15).
- 진단 문제는 이 단계에서 풀지 않는다. 서버가 제출 응답의 `suggestedDiagnostics`(카테고리당 1문제, 최대 5개, `05` §4.2)로 문제를 정하므로 5단계 이후 SCR-DIAGNOSTICS에서 푼다.
- 자기평가: 카테고리 순서는 §3.1 `SkillCategory` 표 순서. 선택지는 `ChoiceChip` 5개(0~4), 기본 0. 3 이상을 고른 카테고리가 있으면 칩 아래에 `onboarding.level.diagnosticNote`를 보여준다(S3 이후).
- "프로젝트에 필요한 기술" 펼침: `GET /skills/tree?role=JAVA_BACKEND`의 skill을 카테고리별 검색 목록으로 보여주고 최대 10개 선택.
- "다음"은 항상 활성이다(두 방식 모두 기본값이 있다). 여기서는 아직 제출하지 않는다.

**4단계 — 사이드 프로젝트** (`/onboarding/project`)

```text
┌────────────────────────────────┐
│ ━━━━━━━━━━━━━━━━━━━━────── 4/5 │
│ 무엇을 만들면서 배울까요?        │
│ 배운 것을 바로 적용할 내 프로젝트 │
│ 예요.                            │
│                                │
│ 프로젝트 이름 *                  │
│ [주문 시스템                  ]  │
│                                │
│ ┌────────────────────────────┐ │
│ │ 실무 시스템이 공통으로 갖는 것을│ │
│ │ 만드는 순서로 배워요.          │ │
│ │ 기반 → 회원·로그인 → 상품 CRUD │ │
│ │ → 주문 → 취소·환불 → 조회 성능 │ │
│ └────────────────────────────┘ │
│ 저장소 주소와 스택은 나중에       │
│ '사이드 프로젝트'에서 적을 수 있어요.│
│ ────────────────────────────── │
│ [이전]      건너뛰기 [계획 만들기]│
└────────────────────────────────┘
```

- 이름 기본값 "주문 시스템"은 **클라이언트가 채운다**(서버는 기본값을 만들지 않는다, `05` §4.1 10단계). 온보딩에서는 이름만 바꿀 수 있다(SP-1). `description`은 고정 문구 `onboarding.project.defaultDescription`을 보내고 `repoUrl`, `stack`은 보내지 않는다(`null`). 나머지는 SCR-PROJECTS에서 고친다.
- 안내 카드(`ReferenceProjectCard`)는 계획 템플릿 milestone 순서를 요약한 고정 문구다. 사용자가 이름을 바꿔도 그대로 둔다.
- "건너뛰기" → 대화상자 `onboarding.project.skip.*`(건너뛰면 Today가 프로젝트 과제를 제안하지 않는다는 사실 + 나중에 만들 수 있음) → "건너뛰고 계획 만들기"를 누르면 `sideProject: null`로 바로 제출한다.
- "계획 만들기" → `POST /onboarding` `{displayName, timezone, dayStartHour, weekdayStudyMinutes, weekendStudyMinutes, learningGoal: {targetRole, targetCompletionDate, focusSkillCodes}, runDiagnostic, selfAssessments, sideProject: {name, description} 또는 null, useTemplate: true}`(IK, 버튼 로딩, 이전·뒤로가기 비활성). 성공 → `meProvider` 갱신 → `/onboarding/plan`.
- "계획 만들기" 활성 조건: 이름 1~100자(§3.2).

**5단계 — 계획 확인** (`/onboarding/plan`)

```text
┌────────────────────────────────┐
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━ 5/5 │
│ 계획을 만들었어요                │
│ Java 백엔드 성장 계획 · v1 [보통] │
│                                │
│ ● 9월 30일 – 10월 20일          │
│ │ 기반 다지기          [필수]   │
│ ○ 10월 21일 – 11월 10일         │
│ │ 회원과 인증          [필수]   │
│ ○ …  상품과 CRUD · 주문 생성 ·   │
│ │    취소와 환불 · 조회 성능 [필수]│
│ ○ …  구조 정리 · 배포와 운영 ·   │
│      설명과 정리        [권장]   │
│                                │
│ 사이드 프로젝트: 주문 시스템       │
│ 복습 카드는 하루 5장씩 나눠       │
│ 두었어요.                        │
│ ┌────────────────────────────┐ │  짧은 진단을 골랐을 때
│ │ 짧은 진단 5문제가 준비됐어요.  │ │
│ │ 풀지 않은 분야는 처음부터       │ │
│ │ 시작해요. 나중에 풀어도 돼요.   │ │
│ └────────────────────────────┘ │
│ 계획은 Plan에서 언제든 바꿀 수    │
│ 있어요.                          │
│ ────────────────────────────── │
│ [나중에 하고 시작]   [진단 시작]  │
│ 계획 자세히                       │
└────────────────────────────────┘
```

- 제목은 서버 `PlanView.title`을 그대로 표시한다(와이어프레임의 제목·날짜는 예시). milestone은 9개이고 템플릿 순서대로 보인다.
- 데이터: milestone 목록은 `GET /plans/active`(응답의 `activePlan`은 요약이라 milestone이 없다). 제목 옆 risk 배지 = `activePlan.latestRiskLevel`(새로고침 후에는 `latestSnapshot.riskLevel`). S2 이전 빌드는 `null`이라 배지를 숨긴다. 카드 문구에 `assignedSeedCardCount`를 쓴다. S2 배포 전(S1)에 온보딩한 사용자는 0일 수 있고(seed 카드는 S2 배포 때 backfill), 0이면 카드 문구를 숨긴다. `sideProject`가 있으면 `onboarding.plan.project`, `null`이면 `onboarding.plan.noProject`. `suggestedDiagnostics`는 SCR-DIAGNOSTICS·SCR-TODAY 진단 카드 초기값으로 보관한다.
- 버튼(primary는 하나, U-1):
  - 짧은 진단을 골랐고 `suggestedDiagnostics`가 비어 있지 않음: primary "진단 시작"(→ `/today/diagnostics`), 보조 "나중에 하고 시작"(→ `/today`), 링크 "계획 자세히"(→ `/plan`).
  - 짧은 진단을 골랐는데 제안이 비어 있음(진단 문제 콘텐츠가 없는 분야뿐): 진단 카드 자리에 `onboarding.plan.diagnosticNone`, primary "그대로 시작".
  - 자기평가를 골랐음: primary "그대로 시작"(→ `/today`, S1은 `/plan`), 보조 "계획 자세히".

- **컴포넌트**: `StepProgress`, `RadioListTile`, `DateQuickChips`, `LevelModeCards`(진단/자기평가), `ChoiceChip`, `SkillMultiPicker`, `ProjectNameField`, `ReferenceProjectCard`, `MilestoneTimelineList`(§3.9와 공유), `DiagnosticReadyCard`.
- **상태**: 1·2·4단계 — 로컬만, 로딩 없음. 3단계 — skill 목록 로딩 시 펼침 영역 skeleton, 실패 시 펼침 영역 안 인라인 오류(다음 단계로 갈 수 있다). 제출 중(4단계) — 버튼 로딩. 제출 오류 — §5(`VALIDATION_FAILED`는 오류 필드가 있는 단계로 이동해 필드 오류 표시: `sideProject.*` → 4단계, `selfAssessments` → 3단계, `learningGoal.*` → 1단계. `422 SECRET_DETECTED_BLOCKED`는 4단계 이름 필드 아래 인라인, `ONBOARDING_ALREADY_COMPLETED`는 `meProvider` 갱신 후 `/today`). Offline — 공통(제출 버튼 비활성). AI — 온보딩 자체는 AI를 쓰지 않는다. 진단 답안 평가는 AI가 필요하므로 AI 불가이면 5단계 진단 카드에 `diagnostics.aiNote`를 함께 보여준다.
- **문구**

| key | 문구 |
|---|---|
| `onboarding.progress` | {step} / 5 |
| `onboarding.next` | 다음 |
| `onboarding.back` | 이전 |
| `onboarding.goal.title` | 무엇을, 언제까지 공부할지 정해요 |
| `onboarding.goal.displayName` | 표시 이름 |
| `onboarding.goal.role` | 학습 트랙 |
| `onboarding.goal.track.javaBackend` | Java 백엔드 |
| `onboarding.goal.track.javaBackend.desc` | Spring Boot · JPA · DB를 실무 수준으로 |
| `onboarding.goal.track.javaBackendStarter` | Java 백엔드 입문 |
| `onboarding.goal.track.javaBackendStarter.desc` | 개발을 막 시작했다면 여기부터. 필수 항목이 적고 과제가 쉬워요 |
| `onboarding.goal.track.mustCount` | 필수 {count}개 |
| `onboarding.goal.track.locked` | 트랙은 나중에 바꿀 수 없어요. |
| `onboarding.goal.track.reset` | 트랙을 바꿔서 수준 입력을 다시 받아요. |
| `onboarding.goal.completion` | 목표일 |
| `onboarding.goal.completion.help` | 목표일까지 남은 시간으로 무엇을 먼저 할지 정해요. |
| `onboarding.goal.quick.3m` | 3개월 후 |
| `onboarding.goal.quick.6m` | 6개월 후 |
| `onboarding.goal.quick.1y` | 1년 후 |
| `onboarding.goal.quick.custom` | 직접 선택 |
| `onboarding.time.title` | 공부할 수 있는 시간은? |
| `onboarding.time.weekday` | 평일 하루 |
| `onboarding.time.weekend` | 주말 하루 |
| `onboarding.time.weeklyTotal` | 일주일에 약 {duration} |
| `onboarding.time.dayStart` | 하루 시작 시각 |
| `onboarding.time.dayStart.help` | {hour} 전에 한 공부는 전날 기록으로 남아요. |
| `onboarding.time.timezone` | 시간대 |
| `onboarding.level.title` | 지금 수준을 확인해요 |
| `onboarding.level.titleSelf` | 지금 수준을 골라 주세요 |
| `onboarding.level.diagnostic` | 짧은 진단으로 시작 |
| `onboarding.level.diagnostic.badge` | 권장 |
| `onboarding.level.diagnostic.desc` | 분야마다 1문제, 최대 5문제예요. 계획을 만든 뒤 바로 풀어요. 스스로 고르는 것보다 정확해요. |
| `onboarding.level.self` | 진단 건너뛰고 직접 고르기 |
| `onboarding.level.self.desc` | 분야별 수준을 직접 골라요. |
| `onboarding.level.help` | 대략이면 충분해요. 실제 레벨은 학습 기록으로 정해져요. |
| `onboarding.level.focus` | 지금 프로젝트에 필요한 기술 (선택, 최대 10개) |
| `onboarding.level.diagnosticNote` | '혼자 가능' 이상인 분야는 나중에 짧은 확인 문제로 건너뛸 수 있어요. |
| `onboarding.project.title` | 무엇을 만들면서 배울까요? |
| `onboarding.project.subtitle` | 배운 것을 바로 적용할 내 프로젝트예요. |
| `onboarding.project.name` | 프로젝트 이름 |
| `onboarding.project.defaultName` | 주문 시스템 |
| `onboarding.project.defaultDescription` | 회원가입 · 상품 · 주문 · 취소까지 직접 만드는 학습용 백엔드 |
| `onboarding.project.reference` | 실무 시스템이 공통으로 갖는 것을 만드는 순서로 배워요. 기반 → 회원·로그인 → 상품 CRUD → 주문 → 취소·환불 → 조회 성능 |
| `onboarding.project.later` | 저장소 주소와 스택은 나중에 '사이드 프로젝트'에서 적을 수 있어요. |
| `onboarding.project.skip` | 건너뛰기 |
| `onboarding.project.skip.title` | 프로젝트 없이 시작할까요? |
| `onboarding.project.skip.body` | 건너뛰면 Today가 프로젝트 과제를 제안하지 않아요. 나중에 '사이드 프로젝트'에서 만들 수 있어요. |
| `onboarding.project.skip.confirm` | 건너뛰고 계획 만들기 |
| `onboarding.project.skip.cancel` | 프로젝트 만들기 |
| `onboarding.submit` | 계획 만들기 |
| `onboarding.plan.title` | 계획을 만들었어요 |
| `onboarding.plan.cards` | 복습 카드 {count}장을 하루 5장씩 나눠 두었어요. |
| `onboarding.plan.project` | 사이드 프로젝트: {name} |
| `onboarding.plan.noProject` | 사이드 프로젝트 없이 시작해요. 프로젝트 과제는 제안되지 않아요. |
| `onboarding.plan.diagnostic` | 짧은 진단 {count}문제가 준비됐어요. 풀지 않은 분야는 처음부터 시작해요. 나중에 풀어도 돼요. |
| `onboarding.plan.diagnosticNone` | 지금 풀 수 있는 진단 문제가 없어요. 모든 분야를 처음부터 시작해요. |
| `onboarding.plan.diagnosticStart` | 진단 시작 |
| `onboarding.plan.startWithoutDiagnostic` | 나중에 하고 시작 |
| `onboarding.plan.editLater` | 계획은 Plan에서 언제든 바꿀 수 있어요. |
| `onboarding.plan.start` | 그대로 시작 |
| `onboarding.plan.detail` | 계획 자세히 |

### 3.5 Today · 진단

#### SCR-TODAY

- **목적**: 오늘 가능 시간·컨디션을 받아 main task 1개를 보여주고 시작·완료를 기록한다(FR-07, FR-08, FR-21). 핵심 루프의 출발점이다 — `READ_CODE`는 SCR-READ-CODE로, `PROJECT_TASK`는 사이드 프로젝트 구현과 러버덕으로 이어진다(FR-26, FR-27). `READING`은 **카드 안에서** 읽을 자료를 보여주고 새 탭으로 연다(별도 화면 없음). **진입**: 로그인 후 기본 화면, 하단 탭 Today, Training·Review·러버덕 종료 후 복귀. **Sprint**: S2 (READ_CODE 카드·READING 자료 블록·러버덕 진입 S3).
- **레이아웃 (생성 전 — `404 TODAY_NOT_GENERATED`)**

```text
┌────────────────────────────────┐
│ 10월 13일 (화)      진행 현황 > │
│                                │
│ 오늘 얼마나 할 수 있나요?        │
│ [15분][30분][45분✓][60분]       │
│ [90분][120분][직접 입력]         │
│                                │
│ 컨디션                          │
│ ┌──────┬──────┬──────┐         │
│ │ 낮음 │ 보통✓│ 좋음 │         │
│ └──────┴──────┴──────┘         │
│                                │
│ ┌────────────────────────────┐ │
│ │      오늘 계획 만들기        │ │
│ └────────────────────────────┘ │
│                                │
│ ┌ 실력 확인 (선택) ──────────┐ │  S3, 제안이 있을 때 (진단 모드면 본문이 today.diagnostic.bodyDiagnosticMode)
│ │ Java 확인 문제 · 약 10분     │ │
│ │ 통과하면 기초 과제를 건너뛰어요│ │
│ │ [풀기]  [건너뛰기]  [모두 보기]│ │
│ └────────────────────────────┘ │
├────────────────────────────────┤
│  Today   Review   Plan   More  │
└────────────────────────────────┘
```

- **레이아웃 (생성 후 — main `PLANNED`)**

```text
┌────────────────────────────────┐
│ 10월 13일 (화)      진행 현황 > │
│ ┌────────────────────────────┐ │  comebackMode=true일 때만
│ │ 다시 시작해도 괜찮아요.      │ │
│ │ 오늘은 가볍게 시작해요.      │ │
│ └────────────────────────────┘ │
│ 마감 위험 [보통]   45분 · 보통 [변경]│
│                                │
│ 오늘의 핵심                     │
│ ┌────────────────────────────┐ │
│ │ [문제 풀이]  Spring Transaction│
│ │ 트랜잭션 전파 수정하기        │ │
│ │ 약 35분 · L2 작은 변형        │ │
│ │ 왜 중요한가 — 경계를 모르면   │ │  whyItMatters (있을 때만 1줄)
│ │ 어디서 데이터가 어긋나는지    │ │
│ │ 설명할 수 없다.               │ │
│ │                              │ │
│ │ 왜 오늘?                      │ │
│ │ • Spring/JPA milestone 핵심 항목│
│ │ • 최근 복습에서 기억이 흔들림  │ │
│ │ • 현재 프로젝트에 필요         │ │
│ │ ▸ 시작 전 확인 3가지           │ │  checklist.before (접힘)
│ │ ┌──────────────────────────┐ │ │
│ │ │          시작            │ │ │
│ │ └──────────────────────────┘ │ │
│ │         오늘은 건너뛰기       │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │  REVIEW task가 있을 때
│ │ 복습 6장 · 약 9분     [복습] │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │  오늘의 팁 (S3, tips flag)
│ │ 오늘의 팁 · 약 3분            │ │
│ │ 로그 레벨은 언제 무엇을 쓰나  │ │  title
│ │ 장애가 났는데 로그에는 INFO   │ │  symptom 2줄
│ │ 한 줄만 남아 있고…            │ │
│ │                    자세히 >  │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │  tipExperiment가 있을 때
│ │ 직접 해 보기 · 약 25분         │ │
│ │ 로그 레벨은 언제 무엇을 쓰나  │ │
│ │ 레벨을 한 단계 올리고 같은…   │ │
│ │                    자세히 >  │ │
│ └────────────────────────────┘ │
├────────────────────────────────┤
│  Today   Review   Plan   More  │
└────────────────────────────────┘
```

- **`whyItMatters` 줄** (S3): main 카드의 예상 시간 아래 한 줄이다. `MainTaskView.whyItMatters`(`05` §8.1)를 그대로 쓴다 — 서버가 응답을 만들 때 기술 트리 콘텐츠에서 읽은 값이므로 클라이언트가 조합하거나 다듬지 않는다. 라벨은 `today.whyItMatters`이고 최대 2줄까지 보인 뒤 말줄임한다. `null`(값이 없는 skill, skill 없는 과제)이면 줄 전체를 숨긴다. **"왜 오늘?"과 다르다** — "왜 오늘?"은 오늘 이 과제를 고른 이유(`reasons`)이고, 이 줄은 **그 기술을 왜 하는지**다. 두 영역을 나란히 두고 각각 제목을 붙인다(A-3).
- **확인 목록 (S3)**: `MainTaskView.checklist`(`05` §8.1, 콘텐츠 `19` §3.11)가 있을 때만 보인다.
  - **시작 전**: `PLANNED` 카드에서 "시작" 위에 접힘 줄 `today.checklist.before`. 펼치면 `before[]` 3~5개를 글머리표로 보여준다. `IN_PROGRESS`에서도 같은 자리에 접힌 채로 남는다(하던 중에 다시 볼 수 있다).
  - **끝내기 전**: 완료 시트 맨 위에 `today.checklist.after`와 `after[]`.
  - **체크박스가 아니라 읽는 목록이다.** 개별 항목의 체크 상태를 서버에 보내지도, 기기에 저장하지도 않는다 — 확인 목록은 저장하지 않는 콘텐츠다(`05` §8.1). 완료를 막지도 않는다.
  - `checklist = null`이면 두 영역을 모두 숨긴다. `earlierMainTasks` 접힘 목록에는 보이지 않는다.
- **오늘의 팁 카드 (S3, `tips` flag)**: 복습 줄 아래에 한 장 둔다. 제목(`title`) + 증상(`symptom`) **2줄 말줄임** + "자세히"(→ `/tips/{tipKey}?from=today`)이고, 머리줄은 `today.tip.title` + `약 {estimatedMinutes}`이다. 카드 전체가 하나의 터치 대상이고(A-1) "자세히"는 그 안의 보조 표시다. 이미 고른 값이 있으면(`feedback ≠ null`) 머리줄 오른쪽에 그 `TipFeedback` 라벨을 텍스트로 붙인다. 하루에 한 개이고 같은 날 다시 열어도 같은 팁이다(`06` §5.12 TIP-3). **계획을 만들기 전(생성 전 레이아웃)에는 보이지 않는다** — 팁 선택이 오늘 main task의 기술을 먼저 보기 때문이다(`06` §5.12 묶음 1).
- **"직접 해 보기" 줄 (S3)**: `TodayView.tipExperiment`가 있으면 팁 카드 아래에 한 줄 둔다(`today.tipExperiment.title` + `약 {estimatedMinutes}` + 팁 제목 + `experiment` 1줄 말줄임 + "자세히" → 같은 팁 상세). 전에 "직접 해 볼래요"를 고른 팁이 다음 날부터 올라오는 자리다(`06` §5.12 TIP-6). **과제가 아니다** — 시작·완료 버튼이 없고 계획·시간 배분에도 들어가지 않는다. `null`이면 숨긴다.
- **main 카드 — `READ_CODE`** (예: reading `READ.PETCLINIC.CONTROLLER_SLICE.001`)

```text
│ ┌────────────────────────────┐ │
│ │ [코드 읽기] Spring MVC REST API│
│ │ Spring PetClinic 읽기 —       │ │  title (06 §5.3 템플릿)
│ │ OwnerController.java 48~122줄 │ │
│ │ 약 15분                        │ │
│ │ 이 컨트롤러는 Repository를 직접 │ │  description 2줄 + 펼치기
│ │ 주입받고 Service 계층이 없…  ▾ │ │  (question + 완료 조건 문장)
│ │ 코드는 내 컴퓨터에서 읽어요.    │ │  today.readCode.local
│ │                              │ │
│ │ 왜 오늘?                      │ │
│ │ • Spring PetClinic에서 같은    │ │  READ_REAL_CODE
│ │   문제를 어떻게 풀었는지 먼저   │ │
│ │   봅니다                      │ │
│ │ • 기반 다지기 milestone 핵심 항목│
│ │ ┌──────────────────────────┐ │ │
│ │ │          시작            │ │ │  → SCR-READ-CODE
│ │ └──────────────────────────┘ │ │
│ │         오늘은 건너뛰기       │ │
│ └────────────────────────────┘ │
```

  - 제목·예상 시간·설명은 `MainTaskView`의 `title`, `estimatedMinutes`, `description`(서버가 `06` §5.3 템플릿으로 만든 값)을 그대로 쓴다. 파일·줄 범위를 클라이언트가 다시 조합하지 않는다.
  - description은 reading의 `question` + "읽고 나서 러버덕으로 설명하면 완료입니다."(`06` §5.3)이다. 카드에서는 2줄로 접고 펼쳐 볼 수 있다. 그 아래 보조 줄 `today.readCode.local`로 코드는 사용자 컴퓨터에서 읽는다는 것을 알린다(서버는 코드를 가져오지 않는다).

- **main 카드 — `READING`** (개념 읽기, S3. `06` §5.3, 콘텐츠 `19` §3.13)

```text
│ ┌────────────────────────────┐ │
│ │ [개념 읽기]  Git 협업          │ │
│ │ Git 협업 개념 읽기 —          │ │  title (06 §5.3 템플릿)
│ │ Pro Git — 3.2 Git Branching, │ │
│ │ Basic Branching and Merging  │ │
│ │ 약 25분                        │ │
│ │ 브랜치를 복사본이 아니라 커밋을 │ │  description 2줄 + 펼치기
│ │ 가리키는 이름으로 이해하게…  ▾ │ │  (whyRead + 완료 안내)
│ │                              │ │
│ │ 자료                          │ │  today.reading.material
│ │ Pro Git — 3.2 Git Branching, │ │  concept.title
│ │ Basic Branching and Merging  │ │
│ │ Git · Pro Git 2nd Edition     │ │  publisher · versionScope
│ │ ┌──────────────────────────┐ │ │
│ │ │      자료 열기  ↗         │ │ │  → concept.url (새 탭)
│ │ └──────────────────────────┘ │ │
│ │ ▸ 읽고 답할 3가지             │ │  today.reading.checkPoints (접힘)
│ │                              │ │
│ │ 왜 오늘?                      │ │
│ │ • 기반 다지기 milestone 핵심 항목│
│ │ ┌──────────────────────────┐ │ │
│ │ │          시작            │ │ │
│ │ └──────────────────────────┘ │ │
│ │         오늘은 건너뛰기       │ │
│ └────────────────────────────┘ │
```

  - **이 카드가 푸는 문제**: 전에는 `READING` 카드에 제목과 "공식 문서를 읽고 핵심 3가지를 스스로 적어 보세요"만 있어서 **무엇을 읽어야 하는지 알 수 없었다.** 자료 블록이 그 자리를 채운다.
  - **데이터**: `MainTaskView.readingKey`가 있으면 `GET /readings/{readingKey}`(`05` §19.7)로 `kind = CONCEPT` 응답을 받아 `concept`을 쓴다. `READ_CODE`가 같은 endpoint를 쓰는 것과 같은 방식이고 **별도 화면을 만들지 않는다** — 자료 블록은 Today 카드 안에 있다. 조회에 실패하면 자료 블록만 숨기고 과제는 그대로 보인다(제목·설명은 이미 `MainTaskView`에 있다).
  - **자료 블록**: 머리줄 `today.reading.material` + `concept.title`(최대 3줄) + 보조 줄 `{concept.publisher} · {concept.versionScope}`. 제목은 문서 원문 제목이라 번역하거나 줄이지 않는다.
  - **"자료 열기"**(`today.reading.open`): `concept.url`을 **새 탭·외부 브라우저**로 연다(앱 안 WebView로 감싸지 않는다 — 공식 문서는 그 사이트에서 읽는 것이 맞고, 서버도 앱도 그 URL을 대신 가져오지 않는다). 카드 안의 secondary 버튼이며 한 화면 primary 1개 규칙(U-1)을 지킨다 — primary는 "시작"이다.
  - **"읽고 답할 3가지"**(`today.reading.checkPoints`): `concept.checkPoints` 3개를 글머리표로 보여 주는 접힘 줄이다. **체크박스가 아니라 읽는 목록**이고 서버에 답을 보내지 않는다(확인 목록과 같은 취급). `IN_PROGRESS`에서도 접힌 채로 남는다.
  - **`readingKey`가 `null`일 때**(그 skill에 개념 읽기가 없다): 자료 블록과 "자료 열기", "읽고 답할 3가지"를 **모두 숨긴다.** 카드는 전과 똑같이 제목 + `skill.description` + "공식 문서를 읽고 핵심 3가지를 스스로 적어 보세요"만 보인다(회귀 없음).
  - **완료 조건이 없다.** `READ_CODE`와 달리 러버덕 세션 없이도 "완료"를 누를 수 있다. `IN_PROGRESS`에서 보조 버튼 "러버덕으로 설명하기"는 그대로 둔다(권장이지 조건이 아니다).
  - `verifiedAt`은 카드에 보이지 않는다. 링크가 언제 확인됐는지는 콘텐츠 점검용 값이고 사용자 판단에 쓰이지 않는다(`19` §8.5).

- **main 카드 — `REDO`** (재현 과제, S4. FR-28, `06` §5.10)

```text
│ ┌────────────────────────────┐ │
│ │ [AI 없이 재현] Spring Transaction│
│ │ 트랜잭션 전파 수정하기        │ │  "{원본 제목} 혼자 다시 만들기"
│ │ 혼자 다시 만들기              │ │
│ │ 약 35분 · AI 도움 없이         │ │
│ │ 4일 전에 한 과제입니다. 이번에는│ │
│ │ AI 도움 없이 처음부터 혼자 다시 │ │
│ │ 만들어 보세요. 막히면 기록해   │ │
│ │ 두고, 끝나고 혼자 해냈는지     │ │
│ │ 답해 주세요.                  │ │
│ │ 🔒 이 과제를 하는 동안 이 문제의│ │  today.redo.locked
│ │ 힌트와 러버덕은 잠겨 있어요.   │ │
│ │                              │ │
│ │ 왜 오늘?                      │ │
│ │ • 4일 전에 한 것을 AI 없이 혼자 │ │  REDO_WITHOUT_AI
│ │   다시 만들어 확인합니다       │ │
│ │ ┌──────────────────────────┐ │ │
│ │ │          시작            │ │ │
│ │ └──────────────────────────┘ │ │
│ │       원본 과제 보기          │ │
│ │       오늘은 건너뛰기         │ │
│ └────────────────────────────┘ │
```

  - 제목·설명·예상 시간은 `MainTaskView`를 그대로 쓴다(`06` §5.3 템플릿). 배지는 `taskType` 라벨 `task.type.REDO`("AI 없이 재현")다.
  - **잠금 줄 `today.redo.locked`는 카드에 항상 보인다**(`PLANNED`·`IN_PROGRESS`). 왜 잠겼는지를 사용자가 다른 화면에서 처음 알게 되지 않도록 여기서 먼저 알린다(RE-5).
  - 보조 "원본 과제 보기": `redoSourceTaskType`이 `CHALLENGE`면 `/training/challenges/{원본 challengeId}`(원본 task의 `challengeId`는 `GET /today`의 `earlierMainTasks`나 과거 기록에 없을 수 있으므로, 없으면 이 버튼을 숨긴다), `PROJECT_TASK`면 `/projects/{sideProjectId}`. 원본을 **다시 풀라는 뜻이 아니라** 무엇을 만들었는지 확인하는 용도다.
  - `IN_PROGRESS`에서는 보조 버튼에 "러버덕으로 설명하기"를 **두지 않는다**(잠겨 있다). primary는 "완료"다.

- **main 카드 — `PROJECT_TASK`** (SP-2: 제목에 사이드 프로젝트 이름)

```text
│ ┌────────────────────────────┐ │
│ │ [프로젝트 과제] Spring Transaction│
│ │ 주문 시스템에 Spring          │ │
│ │ Transaction 적용하기          │ │
│ │ 약 30분                        │ │
│ │ 주문 시스템에서 이 개념을 적용할│ │
│ │ 지점을 찾아 구현하고 이유를     │ │
│ │ 적어 보세요.                  │ │
│ │ 왜 오늘?                      │ │
│ │ • 주문 생성 milestone 핵심 항목 │ │
│ │ • 현재 프로젝트에 필요          │ │
│ │ [ 시작 ]                      │ │
│ └────────────────────────────┘ │
```

  - `IN_PROGRESS`가 되면 보조 버튼 "러버덕으로 설명하기"(`today.explainWithDuck`)가 생긴다 → `/rubber-duck/new?targetType=PROJECT_WORK&targetId={sideProjectId}&skillCode={skillCode}&taskId={taskId}`. 완료 조건이 아니라 권장이다. `sideProjectId`가 `null`(프로젝트 삭제)이면 이 버튼을 숨긴다.
  - 보조 버튼 "이 프로젝트 기록"(`today.projectNotes`) → `/projects/{sideProjectId}`(SCR-PROJECT-DETAIL, S3). 구현하면서 내린 결정과 겪은 장애를 그 자리에서 남기게 한다(FR-29). `sideProjectId`가 `null`이면 숨긴다.
  - `EXPLAIN`·`READING` task도 `IN_PROGRESS`에서 같은 보조 버튼을 두고 `targetType=CONCEPT&conceptKey={skillCode}&skillCode={skillCode}`로 연다.

- **main task 상태별 카드 하단**

| main 상태 | 표시 | primary | 보조 |
|---|---|---|---|
| `PLANNED` | 이유 목록 (REDO면 잠금 줄 `today.redo.locked` 포함) | "시작" | "오늘은 건너뛰기", REDO면 "원본 과제 보기"도 |
| `IN_PROGRESS` | "진행 중 · {n}분째"(세션 `startedAt` 기준 1분마다 갱신) | "완료". **`READ_CODE`는 이 task를 대상으로 한 러버덕이 `COMPLETED`로 확인될 때만 "완료"**, 아니면 primary는 "코드 읽기로 돌아가기"(RC-1) | "여기까지 기록", CHALLENGE면 "문제로 돌아가기", COACH_REVIEW면 "리뷰로 돌아가기", PROJECT_TASK·EXPLAIN·READING이면 "러버덕으로 설명하기", **REDO면 러버덕 버튼을 두지 않는다**(RE-5) |
| `COMPLETED` | 체크 아이콘 + "오늘의 핵심을 마쳤어요" + 기록한 시간 | 없음 | "하나 더 하기" |
| `SKIPPED` (active main 없음) | "오늘 과제를 건너뛰었어요" | "다시 만들기" | "되돌리기" (같은 날 active main이 없을 때만) |
| `DEFERRED` (active main 없음) | "내일 이어서 할 수 있어요" | "다시 만들기" | — |
| `mainTask = null` (후보 없음, `06` §5.2) | `today.noCandidate`. 복습 줄은 그대로 | "계획 조정"(→ `/plan/replan`) | "진행 현황" |

- **완료 시트** (`showModalBottomSheet`, 데스크톱은 대화상자)

```text
┌────────────────────────────────┐
│ 수고했어요                      │
│ 끝내기 전 확인                   │  checklist.after 가 있을 때
│ • 오류 응답 형식을 맞췄나        │
│ • 같은 요청을 두 번 보내도 결과가 │
│   같은가                        │
│ • 목록 응답에 페이지 기준을 넣었나│
│ 실제로 공부한 시간               │
│   [ − ]     35 분     [ + ]     │
│ 한 줄 회고 (선택)                │
│ ┌────────────────────────────┐ │
│ │ 오늘 배운 것, 막힌 것…        │ │
│ └────────────────────────────┘ │
│ 지금 이 개념을 설명할 수 있나요?  │  "완료"로 열었을 때
│ [네, 설명할 수 있어요] [아직 모르겠어요]│
│ ┌────────────────────────────┐ │
│ │         완료 기록            │ │  "아직"이면 "내일 이어서 하기"
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

  - 시간 기본값 = 세션 시작부터 경과 분(반올림, 최소 1). 최댓값 = `min(720, ⌈경과 분 × 1.5⌉)`(`05-api-spec.md` §9.2). `−`/`+`는 5분 단위.
  - "여기까지 기록"도 같은 시트를 제목 `today.partial.title`로 연다. 0분으로 기록하면 세션을 abandon한다.
  - **`READ_CODE` 읽기 평가 (S3)**: 완료할 task가 `READ_CODE`이면 "완료 기록" 시트의 회고 아래에 `today.readCode.feedback.title`과 칩 3개(`ReadingFeedback` 라벨: 도움 됐어요 · 어려웠어요 · 지루했어요)를 둔다. **선택 사항이다** — 아무것도 고르지 않아도 되고, 고른 칩을 다시 누르면 선택이 풀린다. 고른 값은 완료 `PATCH /today/tasks/{taskId}` `{status: COMPLETED, readingFeedback, version}`에 넣는다(`05` §8.4). 고르지 않으면 `readingFeedback`을 보내지 않는다. "여기까지 기록"(`DEFERRED`) 시트와 다른 task 유형에는 칩이 없다. 평가는 계획·레벨에 영향이 없고 저장소·단위를 고르는 소스 점검(`19` §8.5)에만 쓰인다.

```text
│ 이 코드 읽기는 어땠나요? (선택)   │
│ [도움 됐어요] [어려웠어요] [지루했어요]│
```

  - **`REDO` 재현 결과 (S4)**: 완료할 task가 `REDO`이면 "완료 기록" 시트의 회고 **위**에 질문 하나를 둔다 — `today.redo.question`("AI 도움 없이 끝냈나요?")과 버튼 둘(`today.redo.answer.yes` "네, 혼자 했어요" / `today.redo.answer.no` "아니요, 도움을 받았어요"). **필수다** — 고르지 않으면 "완료 기록"이 비활성이다(`05` §8.4 `VALUE_REQUIRED`). 고른 값은 `PATCH /today/tasks/{taskId}` `{status: COMPLETED, redoWithoutAi, version}`에 넣는다.
  - 질문 아래에 `today.redo.answer.help`("솔직하게 고르는 게 도움이 돼요. '아니요'를 고르면 벌점이 아니라 복습 카드가 생겨요.")를 둔다. "아니요"로 완료하면 결과 토스트 `today.redo.reviewCreated`를 보이고 "복습하러 가기"(→ `/review`)를 함께 준다.
  - "여기까지 기록"(`DEFERRED`) 시트에는 이 질문이 없다(아직 해 보지 않은 것이다). `SKIPPED`도 마찬가지다.
  - **알게 되었나 (S3)**: "완료"로 연 시트에는 유형과 무관하게 질문 하나를 더 둔다 — `today.complete.understand.title`("지금 이 개념을 설명할 수 있나요?")과 칩 둘(`…understand.yes` "네, 설명할 수 있어요" / `…understand.no` "아직 모르겠어요"). **필수다** — 고르기 전에는 보내는 버튼이 비활성이다. 기본값을 "네"로 두면 모른 채로 완료되는 일이 그대로 남는다.
    - **시간을 썼다는 것과 알게 되었다는 것은 다르다.** 지금까지 완료는 "시간을 썼다"만 뜻했고, 답을 모르는 채 완료하면 그 개념은 다시 나오지 않았다.
    - "아직 모르겠어요"를 고르면 **세션은 실제 시간 그대로 기록**하고(쓴 시간은 사실이다) 과제는 `COMPLETED`가 아니라 **`DEFERRED`**가 된다. 다음 날 같은 skill이 `CONTINUATION`으로 이어진다(`06` §5.4 3a). 보내는 버튼은 `today.complete.submit.notYet`("내일 이어서 하기")으로 바뀌고, 칩 아래에 무슨 일이 생기는지 한 줄(`today.complete.notYet.note`)을 둔다.
    - `DEFERRED`로 가므로 `READ_CODE` 읽기 평가와 `REDO` 재현 결과는 함께 보내지 않는다(둘 다 `COMPLETED` 전용이다).
    - **복습 카드는 아직 만들지 않는다** — 자동 등록은 R-3(1·7·30일 사다리)과 함께 정한다.

```text
│ AI 도움 없이 끝냈나요? *          │
│ [네, 혼자 했어요][아니요, 도움 받았어요]│
│ 솔직하게 고르는 게 도움이 돼요.    │
```

  - **설명 기록 (S3)**: 완료할 task가 `EXPLAIN` 또는 `READ_CODE`이면 회고 아래에 체크 `today.explainedToPerson`("다른 사람에게 설명했어요")과, 켰을 때만 펼쳐지는 한 줄 메모(`today.explainedNote`, 0~500자, §3.2)를 둔다. **선택 사항이다** — 켜지 않으면 두 값을 모두 보내지 않는다. 켠 채로 메모를 비워도 된다(`explainedToPerson`만 보낸다). 고른 값은 `PATCH /today/tasks/{taskId}` `{status: COMPLETED, explainedToPerson, explainedNote, version}`에 넣는다(`05` §8.4). `explainedToPerson`을 켜지 않고 메모만 보내면 서버가 400을 주므로 클라이언트가 먼저 막는다.
  - 체크 아래에 `today.explainedToPerson.help`를 둔다 — `EXPLAIN` 과제를 이렇게 완료하면 그 기술의 **설명하기 학습 단계**가 채워진다(`06` §5.11, SCR-SKILL-DETAIL). 사람에게 말한 적이 없으면 켜지 않는 것이 맞고, 러버덕으로 설명한 것도 같은 단계를 채우므로 둘 중 하나면 된다.
  - 메모는 저장 전에 마스킹한다(`projectNote.maskingNote`와 같은 안내 `today.explainedNote.maskingNote`). `422 SECRET_DETECTED_BLOCKED`면 메모 아래 인라인 `projects.secretBlocked`.
  - "여기까지 기록"(`DEFERRED`) 시트와 다른 task 유형에는 이 영역이 없다.

```text
│ [ ] 다른 사람에게 설명했어요       │  EXPLAIN · READ_CODE
│ ┌────────────────────────────┐ │  켰을 때만
│ │ 누구에게, 어디서 막혔는지     │ │
│ └────────────────────────────┘ │
│ 비밀값은 저장 전에 가려요.  0 / 500│
```

- **진행 중 러버덕 줄** (`ActiveRubberDuckTile`, S3): 기기에 저장한 진행 중 세션(`localStorage` `devpilot.rubberduck.active.<externalAuthId>` = `{sessionId, taskId}`, try/catch)이 있고 `GET /rubber-duck/{sessionId}`가 `IN_PROGRESS`이면 복습 줄 위에 `today.duck.continue` + "이어서 설명하기"(→ `/rubber-duck/{sessionId}`)를 한 줄로 보인다. `IN_PROGRESS`가 아니거나 404면 저장값을 지우고 숨긴다. 세션 목록 API가 없어서 다른 기기에서 시작한 세션은 보이지 않는다(`05` §9.10).
- **`READ_CODE` 완료 확인**: 완료 조건(RC-1)은 서버가 검사한다 — 그 task를 대상으로 한 `COMPLETED` 러버덕 세션이 있어야 `PATCH /today/tasks/{taskId}` `{status: COMPLETED}`가 성공한다(`05` §8.4). 러버덕 정리는 과제 상태를 바꾸지 않는다(`05` §9.8). 클라이언트는 러버덕 정리 화면의 "Today로 돌아가 완료하기"(`/today?complete={taskId}`)로 돌아오면 완료 시트(읽기 평가 칩 포함)를 연다. 그 밖의 진입에서는 기기에 저장한 기록(`devpilot.rubberduck.task.<taskId>`)의 세션이 `COMPLETED`이면 "완료"를, 아니면 "코드 읽기로 돌아가기"와 "설명하기"를 보인다. 러버덕 없이 완료를 누르면 서버가 `409 INVALID_STATE_TRANSITION`을 주고 토스트 `today.readCode.needDuck`.
- **컴포넌트**: `MinutesChips`, `EnergySegmented`, `MainTaskCard`(유형별 변형: `READ_CODE`·`READING`·`PROJECT_TASK`·`REDO` 위 와이어프레임), `ConceptReadingSection`(`READING` 자료 블록 + "자료 열기" + 읽고 답할 3가지), `WhyItMattersLine`, `ChecklistSection`(before 접힘 / after 목록), `ReasonList`, `RiskBadge`(§6.1), `ComebackBanner`, `ActiveRubberDuckTile`, `ReviewTaskTile`, `DailyTipCard`, `TipExperimentTile`, `DiagnosticSuggestionCard`, `CompleteSessionSheet`(`ExplainedToPersonField` 포함), `RegenerateSheet`(시간·컨디션 재입력).
- **데이터**

| 시점 | API |
|---|---|
| 진입, 탭 재선택, 화면 복귀(visibility) | `GET /today` (404 `TODAY_NOT_GENERATED` → 생성 전 레이아웃) |
| 생성 전 레이아웃 표시 시 (S3) | `GET /diagnostics/suggestions` (실패해도 카드만 숨김) |
| 진입 시, 기기에 진행 중 러버덕·READ_CODE 완료 기록이 있을 때 (S3) | `GET /rubber-duck/{sessionId}` (실패하면 해당 줄·버튼만 기본 상태) |
| 진입·화면 복귀, 계획이 있을 때 (S3, `tips` flag) | `GET /tips/today` (404 `RESOURCE_NOT_FOUND` = 더 보여 줄 팁이 없음 → 카드만 숨김. 그 밖의 실패도 카드만 숨기고 화면 오류로 만들지 않는다) |
| 진입 시, "시작" 직전 | `GET /learning-sessions?from={planDate}&to={planDate}` → `IN_PROGRESS` 세션(있으면 1개)과 그 `learningTaskId` 확인 |
| "오늘 계획 만들기" / 재생성 | `POST /today/generate` `{availableMinutes, energyLevel, force}` |
| "시작" | `PATCH /today/tasks/{taskId}` `{status: IN_PROGRESS, version}` → 진행 중 세션이 없거나 그 `learningTaskId`가 이 task가 아니면 `POST /learning-sessions` `{learningTaskId}`(서버가 기존 세션을 ABANDONED로 닫음) |
| "오늘은 건너뛰기" | `PATCH /today/tasks/{taskId}` `{status: SKIPPED, version}` |
| "되돌리기" | `PATCH /today/tasks/{taskId}` `{status: PLANNED, version}` |
| "완료 기록" | `POST /learning-sessions/{sessionId}/complete` `{actualMinutes, selfReflection}` → `PATCH /today/tasks/{taskId}` `{status: COMPLETED, version}` (`READ_CODE`이고 평가를 골랐으면 `readingFeedback`, `EXPLAIN`·`READ_CODE`이고 체크를 켰으면 `explainedToPerson`·`explainedNote`, `REDO`면 `redoWithoutAi` 추가) |
| "여기까지 기록" (> 0분) | `POST /learning-sessions/{sessionId}/complete` → `PATCH … {status: DEFERRED}` |
| "여기까지 기록" (0분) | `POST /learning-sessions/{sessionId}/abandon` → `PATCH … {status: DEFERRED}` |

- **상태**
  - Loading: 날짜 줄 + main 카드 skeleton 1개 + 복습 줄 skeleton. 팁 카드는 별도 skeleton 1장(main과 따로 로드한다).
  - Empty: 해당 없음(생성 전 레이아웃이 empty 역할). 오늘의 팁이 없으면(404) 카드 자리를 비우고 `EmptyState`를 두지 않는다 — 팁은 보조 정보라 빈 상태를 만들지 않는다.
  - Error: 조회는 공통. `404 PLAN_NOT_FOUND`(생성 시) → 전체 영역 `today.noPlan` + "계획 만들기" 버튼(`POST /plans` → 성공 시 재생성). 팁 조회 실패는 카드만 숨긴다(재시도 버튼도 두지 않는다).
  - AI unavailable·budget: 표시하지 않는다(Today는 AI를 쓰지 않음 — `GET /today`도 `GET /tips/today`도 AI를 호출하지 않는다, `05` §8·§20).
  - Async: 해당 없음.
  - Offline: 공통. 메모리의 today 데이터는 보이지만 모든 버튼 비활성.
- **행동·검증**
  - 시간 기본값: 오늘이 토·일이면 `weekendStudyMinutes`, 아니면 `weekdayStudyMinutes`(`/me`). 0이면 30. 칩에 없는 값이면 "직접 입력" 칩에 값을 표시한다.
  - "직접 입력": 숫자 대화상자 5~720.
  - 날짜 헤더는 `GET /today.planDate`, 생성 전에는 `GET /me.today`를 쓴다.
  - `earlierMainTasks`(같은 날의 다른 학습 과제 — 먼저 끝냈거나 미룬 main과 §5.6의 추가 과제)가 있으면 main 카드 아래 `today.earlier` 접힘 목록(제목·상태 라벨·예상 시간)으로 보여준다. **기본은 접힘이고 개수 배지·진행률·퍼센트를 붙이지 않는다** — 오늘 시간이 남을 때 이어서 할 수 있는 것이지 할당량이 아니다(U-3). `PLANNED`인 항목은 눌러 바로 시작할 수 있다.
  - `aiStatus`가 `DISABLED`/`BALANCE_EXHAUSTED`면 서버가 CHALLENGE task를 제안하지 않는다(`06` §5.3). 이미 만들어진 CHALLENGE task는 시작할 수 있지만 문제 화면에서 제출이 막힌다.
  - main `PLANNED`에서 재생성: 확인 없이 `force=false`.
  - main `IN_PROGRESS`에서 "변경"(재생성): 대화상자 `today.regenerate.startedDialog` → 확인 시 `force=true`.
  - main `COMPLETED`에서 "하나 더 하기": `RegenerateSheet` → `force=true`로 바로 요청(확인 대화상자 없음, 기존 완료는 유지됨을 시트에 표시).
  - 서버가 `409 TODAY_ALREADY_STARTED`/`TODAY_ALREADY_COMPLETED`를 주면(다른 기기에서 상태가 바뀐 경우) 같은 대화상자를 띄우고 확인 시 `force=true`.
  - "시작" 후 이동: `CHALLENGE` → `/training/challenges/{challengeId}?taskId=`, `COACH_REVIEW` → `/coach/new?taskId=`, **`READ_CODE` → `/today/read/{readingKey}?taskId=`**, `REVIEW` main은 생기지 않는다. 그 외(`READING`, `EXPLAIN`, `PROJECT_TASK`, `RECALL`)는 Today에 머물며 description을 펼쳐 보여준다.
  - `READ_CODE`는 `aiStatus`가 `DISABLED`/`BALANCE_EXHAUSTED`이면 새로 제안되지 않는다(완료 조건인 러버덕이 AI에 의존, `06` §5.3). 이미 만들어진 `READ_CODE` task는 시작·읽기 안내까지 되고, 러버덕 버튼은 SCR-READ-CODE에서 이유와 함께 막힌다. Today에는 AI 배너를 두지 않는다(§6.5).
  - `deadlineRisk`는 S2부터 값이 있다(기한 역산이 S2로 옮겨짐). `null`이면 배지를 숨긴다.
  - 복습 줄 "복습" → `/review/session?taskId={reviewTaskId}`.
  - `reviewTask = null`(due 없음, 또는 가능 시간이 짧아 `reviewMinutes = 0` — 예: 5분)이면 복습 줄을 숨긴다. due 카드가 있으면 Review 탭에서는 그대로 복습할 수 있다.
  - `PATCH`가 `409 CONCURRENT_MODIFICATION`이면 `GET /today`로 새 `version`을 받아 같은 요청을 1회 재시도한다. 상태가 이미 목표 상태면 성공으로 처리한다.
  - `?complete=<taskId>`로 진입하면 해당 task가 `IN_PROGRESS`일 때 완료 시트를 바로 연다.
  - 오늘의 팁 카드는 **읽기만 한다** — 여기서는 "알고 있었어요 / 새로 알았어요 / 직접 해 볼래요"를 고를 수 없고 SCR-TIP-DETAIL에서 고른다. 상세에서 고르고 돌아오면 `GET /tips/today`를 다시 읽어 라벨을 갱신한다.
  - 확인 목록·`whyItMatters`는 화면에서 편집할 수 없다. 콘텐츠가 바뀌면 다음 조회부터 새 문구가 보인다(`05` §8.1).
  - 스크린 리더: main 카드 전체를 하나의 semantics 그룹으로 읽는다("문제 풀이, 트랜잭션 전파 수정하기, 약 35분, 왜 중요한가 있음, 이유 3개"). 확인 목록은 펼쳤을 때 "시작 전 확인, 3개 항목"으로 읽고 각 항목을 목록으로 읽는다. 팁 카드는 "오늘의 팁, {제목}, 약 {n}분"으로 읽는다.
- **문구**

| key | 문구 |
|---|---|
| `today.dashboardLink` | 진행 현황 |
| `today.minutes.question` | 오늘 얼마나 할 수 있나요? |
| `today.minutes.custom` | 직접 입력 |
| `today.energy.label` | 컨디션 |
| `today.generate.button` | 오늘 계획 만들기 |
| `today.inputSummary` | {minutes} · {energy} |
| `today.change` | 변경 |
| `today.risk.label` | 마감 위험 |
| `today.main.title` | 오늘의 핵심 |
| `today.main.estimated` | 약 {minutes} |
| `today.whyItMatters` | 왜 중요한가 |
| `today.reasons.title` | 왜 오늘? |
| `today.checklist.before` | 시작 전 확인 {count}가지 |
| `today.checklist.after` | 끝내기 전 확인 |
| `today.tip.title` | 오늘의 팁 |
| `today.tip.more` | 자세히 |
| `today.tipExperiment.title` | 직접 해 보기 |
| `today.start.button` | 시작 |
| `today.skip.button` | 오늘은 건너뛰기 |
| `today.skip.done` | 건너뛰었어요 |
| `today.undo` | 되돌리기 |
| `today.inProgress` | 진행 중 · {minutes}째 |
| `today.complete.button` | 완료 |
| `today.partial.button` | 여기까지 기록 |
| `today.backToChallenge` | 문제로 돌아가기 |
| `today.backToCoach` | 리뷰로 돌아가기 |
| `today.completed` | 오늘의 핵심을 마쳤어요 |
| `today.completed.minutes` | {minutes} 공부했어요 |
| `today.oneMore` | 하나 더 하기 |
| `today.oneMore.note` | 마친 과제 기록은 그대로 남아요. |
| `today.skipped` | 오늘 과제를 건너뛰었어요 |
| `today.deferred` | 내일 이어서 할 수 있어요 |
| `today.regenerate` | 다시 만들기 |
| `today.regenerate.startedDialog.title` | 진행 중인 과제가 있어요 |
| `today.regenerate.startedDialog.body` | 지금 과제는 '미룸'으로 두고 새 과제를 만들까요? 미룬 과제는 다음에 이어서 추천될 수 있어요. |
| `today.regenerate.startedDialog.confirm` | 새로 만들기 |
| `today.review.tile` | 복습 {count}장 · 약 {minutes} |
| `today.review.button` | 복습 |
| `today.comeback.banner` | 다시 시작해도 괜찮아요. 오늘은 가볍게 시작해요. |
| `today.completeSheet.title` | 수고했어요 |
| `today.partial.title` | 여기까지 기록할게요 |
| `today.completeSheet.minutes` | 실제로 공부한 시간 |
| `today.completeSheet.reflection` | 한 줄 회고 (선택) |
| `today.completeSheet.reflection.hint` | 오늘 배운 것, 막힌 것… |
| `today.completeSheet.submit` | 완료 기록 |
| `today.redo.locked` | 이 과제를 하는 동안 이 문제의 힌트와 러버덕은 잠겨 있어요. |
| `today.redo.viewSource` | 원본 과제 보기 |
| `today.redo.question` | AI 도움 없이 끝냈나요? |
| `today.redo.answer.yes` | 네, 혼자 했어요 |
| `today.redo.answer.no` | 아니요, 도움을 받았어요 |
| `today.redo.answer.help` | 솔직하게 고르는 게 도움이 돼요. '아니요'를 고르면 벌점이 아니라 복습 카드가 생겨요. |
| `today.redo.reviewCreated` | 막힌 부분을 복습 카드로 만들었어요. |
| `today.projectNotes` | 이 프로젝트 기록 |
| `today.redo.lockedElsewhere` | 지금은 이 과제를 AI 없이 혼자 다시 만드는 중이에요. Today의 재현 과제를 마치면 다시 쓸 수 있어요. |
| `today.noPlan` | 활성 계획이 없어요. 계획을 먼저 만들어 주세요. |
| `today.noPlan.button` | 계획 만들기 |
| `today.noCandidate` | 목표를 모두 달성했어요. 계획을 조정해 새 목표를 잡아 보세요. |
| `today.noCandidate.button` | 계획 조정 |
| `today.earlier` | 오늘 앞서 한 과제 {count}개 |
| `today.diagnostic.title` | 실력 확인 (선택) |
| `today.diagnostic.body` | 통과하면 이미 아는 기초 과제를 건너뛰어요. |
| `today.diagnostic.solve` | 풀기 |
| `today.diagnostic.skip` | 건너뛰기 |
| `today.diagnostic.all` | 모두 보기 |
| `today.diagnostic.bodyDiagnosticMode` | 아직 풀지 않은 진단이 {count}문제 있어요. 풀면 그 분야의 시작 수준이 정해져요. |
| `today.readCode.local` | 코드는 내 컴퓨터에서 읽어요. |
| `today.readCode.back` | 코드 읽기로 돌아가기 |
| `today.readCode.needDuck` | 러버덕으로 설명을 마쳐야 완료할 수 있어요. |
| `today.readCode.feedback.title` | 이 코드 읽기는 어땠나요? (선택) |
| `today.reading.material` | 자료 |
| `today.reading.open` | 자료 열기 |
| `today.reading.openHint` | 새 탭에서 열려요. |
| `today.reading.checkPoints` | 읽고 답할 3가지 |
| `enum.ReadingFeedback.HELPFUL` | 도움 됐어요 |
| `enum.ReadingFeedback.TOO_HARD` | 어려웠어요 |
| `enum.ReadingFeedback.BORING` | 지루했어요 |
| `today.explainWithDuck` | 러버덕으로 설명하기 |
| `today.duck.continue` | 설명하던 러버덕이 있어요. |
| `today.duck.continueButton` | 이어서 설명하기 |
| `today.explainedToPerson` | 다른 사람에게 설명했어요 |
| `today.explainedToPerson.help` | 사람에게 말로 설명했으면 골라 주세요. 러버덕으로 설명한 것도 같은 단계로 쳐요. |
| `today.explainedNote` | 한 줄 메모 (선택) |
| `today.explainedNote.hint` | 누구에게 설명했는지, 어디서 막혔는지 |
| `today.explainedNote.maskingNote` | 비밀값은 저장 전에 가려요. |
| `enum.TipFeedback.KNEW_IT` | 알고 있었어요 |
| `enum.TipFeedback.LEARNED` | 새로 알았어요 |
| `enum.TipFeedback.WILL_TRY` | 직접 해 볼래요 |

#### SCR-DIAGNOSTICS

- **목적**: 진단 challenge 제안 전체를 보고 풀거나 건너뛴다(FR-15, FR-02 진단 모드). 온보딩에서 짧은 진단을 고른 사용자에게는 **시작 수준을 정하는 화면**이다. **진입**: SCR-ONBOARDING 5단계 "진단 시작", SCR-TODAY 진단 카드 "모두 보기". **Sprint**: S3.
- **레이아웃**: 설명 1줄(진단 모드면 `diagnostics.helpDiagnosticMode`, 자기평가 모드면 `diagnostics.help`) + 카테고리별 카드 목록(카테고리 라벨, `selfAssessedLevel` 라벨 — 진단 모드는 `null`이라 이 줄을 숨긴다, `skill.name`, challenge `title`, `L{difficulty}`, `estimatedMinutes`, "풀기", "건너뛰기"). 최대 5개(`05` §4.2). 건너뛴 항목은 하단 "건너뛴 항목" 접힘 영역에 "다시 보기" 버튼과 함께 둔다.
- **모드 판정**: 모든 제안의 `selfAssessedLevel`이 `null`이면 진단 모드다(`05` §4.2 `DiagnosticSuggestionView`).
- **데이터**: 진입 시 `GET /diagnostics/suggestions`. "풀기" → `POST /challenges/{challengeId}/attempts` → `/training/attempts/{attemptId}`.
- **건너뛰기 저장**: 서버 API가 없다. `localStorage` key `devpilot.diagnostics.skipped.<externalAuthId>`에 challengeId 목록을 저장한다(try/catch). SCR-TODAY 카드는 건너뛰지 않은 첫 제안 1개만 보여주고, 모두 건너뛰면 카드를 숨긴다.
- **상태**: Loading — 카드 skeleton 2개. Empty — `diagnostics.empty` + "Today로" 버튼. Error — 공통. AI unavailable — 풀기는 가능하지만 카드 안에 `diagnostics.aiNote`(제출 평가에 AI가 필요함). Offline — 공통.
- **문구**

| key | 문구 |
|---|---|
| `diagnostics.title` | 실력 확인 문제 |
| `diagnostics.help` | '혼자 가능' 이상으로 고른 분야를 짧은 문제로 확인해요. 통과하면 기초 과제를 건너뛰어요. 풀지 않아도 괜찮아요. |
| `diagnostics.helpDiagnosticMode` | 분야마다 짧은 문제 1개로 지금 수준을 확인해요. 풀지 않은 분야는 처음부터 시작해요. |
| `diagnostics.claimed` | 내가 고른 수준: {level} |
| `diagnostics.solve` | 풀기 |
| `diagnostics.skip` | 건너뛰기 |
| `diagnostics.skipped` | 건너뛴 항목 |
| `diagnostics.restore` | 다시 보기 |
| `diagnostics.empty` | 지금 확인할 문제가 없어요. |
| `diagnostics.aiNote` | 제출한 답은 AI가 평가해요. 지금은 AI를 쓸 수 없어 제출이 막혀 있어요. |

### 3.6 Review

#### SCR-REVIEW-HOME

- **목적**: 오늘 복습할 카드 수를 보여주고 복습을 시작한다. 카드 관리로 이동한다(FR-11). **진입**: 하단 탭·rail Review. **Sprint**: S2.
- **레이아웃**

```text
┌────────────────────────────────┐
│ 복습                            │
│                                │
│ ┌────────────────────────────┐ │
│ │ 오늘 복습할 카드              │ │
│ │ 6장 · 약 9분                  │ │
│ │ Spring 3 · Java 2 · DB 1     │ │
│ │ ┌──────────────────────────┐ │ │
│ │ │        복습 시작          │ │ │
│ │ └──────────────────────────┘ │ │
│ └────────────────────────────┘ │
│                                │
│ 카드 관리                    >  │
│ 카드 직접 추가               >  │
│ 용어 찾기                    >  │  S3, terms flag
├────────────────────────────────┤
│  Today   Review   Plan   More  │
└────────────────────────────────┘
```

- **컴포넌트**: `DueSummaryCard`(카드 수 = `items.length`, 예상 분 = `ceilDiv(n × 3, 2)`, 기술별 개수. `totalDueCount > cap`이어도 남은 수는 표시하지 않는다(U-3)), `ListTile` 3개(맨 아래 "용어 찾기"는 `terms` flag가 켜진 S3부터, → `/terms`).
- **데이터**: 진입·탭 재선택 시 `GET /reviews/due`(limit 생략 = 서버 cap). 결과 목록은 `dueReviewsProvider`에 두고 SCR-REVIEW-SESSION이 그대로 쓴다.
- **상태**: Loading — 요약 카드 skeleton. Empty — `review.home.empty` + "카드 직접 추가". Error·Offline — 공통. AI — 해당 없음.
- **문구**

| key | 문구 |
|---|---|
| `review.home.title` | 복습 |
| `review.home.due` | 오늘 복습할 카드 |
| `review.home.dueCount` | {count}장 · 약 {minutes} |
| `review.home.start` | 복습 시작 |
| `review.home.manage` | 카드 관리 |
| `review.home.add` | 카드 직접 추가 |
| `review.home.terms` | 용어 찾기 |
| `review.home.empty` | 오늘 복습할 카드가 없어요. 학습을 하면 복습 카드가 자동으로 생겨요. |

#### SCR-REVIEW-SESSION

- **목적**: 카드를 한 장씩 떠올려 답하고, 답을 확인한 뒤 자기평가로 다음 복습일을 정한다(FR-11, AC-10). 모바일 한 손 사용이 기준이다. **진입**: SCR-REVIEW-HOME "복습 시작", SCR-TODAY 복습 줄(`?taskId=`). **Sprint**: S2 (AI 채점 S3).
- **레이아웃 ① 답하기**

```text
┌────────────────────────────────┐
│ ✕                        2 / 6 │
│ ━━━━━━━━━━──────────────────── │
│ Spring Transaction · 떠올리기    │
│                                │
│ 같은 클래스 안에서 @Transactional │
│ 메서드를 this로 호출하면 트랜잭션 │
│ 이 적용되지 않을 수 있다. 이유는? │
│                                │
│ 내 답 (선택)                     │
│ ┌────────────────────────────┐ │
│ │ 머릿속으로 떠올려도 괜찮아요.  │ │
│ │                              │ │
│ └────────────────────────────┘ │
│                                │
│ 힌트 보기                        │
│ 모르겠어요, 정답 볼게요           │
│ ┌────────────────────────────┐ │
│ │          답 확인             │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

  - "힌트 보기"를 누르면 그 자리에 `rubric[0].criterion`을 보여주고 링크는 사라진다(`hintLevel=CONCEPT_HINT`). rubric이 비어 있으면 링크를 숨긴다.
  - `wasVariant=true`면 카드 상단에 `변형 문제` 칩을 붙인다.

- **레이아웃 ② 정답 확인 → 자기평가**

```text
┌────────────────────────────────┐
│ ✕                        2 / 6 │
│ ━━━━━━━━━━──────────────────── │
│ 같은 클래스 안에서 @Transactional │
│ 메서드를 this로 호출하면…         │
│ ─────────────────────────────  │
│ 내 답                            │
│ 프록시를 안 거쳐서…               │
│ ─────────────────────────────  │
│ 정답                             │
│ Spring의 선언적 트랜잭션은 프록시 │
│ 기반이다. 내부 호출은 프록시를…   │
│ 핵심 포인트                       │
│ • 프록시 기반 AOP 언급            │
│ • 내부 호출은 프록시를 우회        │
│ • 해결 방법 1개 이상              │
│                                │
│ [ ] AI로 채점하기 (S3)            │
│ 얼마나 잘 떠올렸나요?             │
│ ┌──────┬──────┬──────┬──────┐  │
│ │ 다시 │어려움│알맞음│ 쉬움 │  │
│ │  1   │  2   │  3   │  4   │  │
│ └──────┴──────┴──────┴──────┘  │
└────────────────────────────────┘
```

  - 평가 버튼 4개는 한 줄, 각 높이 56 이상. 버튼 아래 작은 숫자는 데스크톱에서만 보인다(단축키 안내).
  - "AI로 채점하기" 스위치는 `answerText`가 비어 있지 않고, flag `review_evaluate`가 켜져 있고, **`aiAvailable`**(= `aiStatus ∉ {DISABLED, BALANCE_EXHAUSTED}`, §6.4)일 때만 보인다. 기본값은 마지막 선택(`localStorage` `devpilot.review.evaluate`, 없으면 꺼짐).
  - 평가 버튼을 누르면 버튼 영역이 진행 표시로 바뀐다. 채점이 켜져 있으면 `review.session.evaluating`(최대 20초)을 보여준다.

- **레이아웃 ③ 조정 토스트 (다음 카드 위에 4초)**

```text
┌────────────────────────────────┐
│ ✕                        3 / 6 │
│ …다음 카드…                     │
│                                │
│ ┌────────────────────────────┐ │
│ │ '알맞음' → '다시'로 조정했어요 │ │
│ │ 정답을 먼저 봤어요.            │ │
│ │ 다음 복습: 10월 14일 (수)      │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

  - `evaluate=true`로 평가에 성공하면(`rubricResults` 있음) 자동으로 넘어가지 않고 정답 아래에 핵심 포인트별 충족 ✓/✗, 최종 등급, 조정 사유, 다음 복습일을 보여주고 "다음 카드" 버튼을 둔다.
  - `leechDetected=true`면 토스트에 `review.adjust.leech`를 추가한다.
  - `adjustedBy`가 비어 있으면 `다음 복습: {date}`만 짧게(2초) 보여준다.
  - `evaluatedOutcome != NOT_EVALUATED`면 첫 줄 앞에 `AI 채점: {라벨}`을 붙인다. `evaluationSkippedReason`이 있으면 `review.adjust.evaluationSkipped`를 추가한다.

- **레이아웃 ④ 끝**: `{n}장 복습했어요` + 최종 등급별 개수(다시/어려움/알맞음/쉬움, 텍스트 라벨) + primary "완료 기록"(이 화면이 세션을 시작했을 때) 또는 "Today로".
  - (S3, `rubber_duck` flag) 최종 등급이 `AGAIN`·`HARD`인 카드가 있으면 그 아래에 `review.summary.explainTitle` 목록(출제 순서로 최대 3장, 문항 1줄 말줄임)을 두고 행마다 "설명해 보기"(보조 `OutlinedButton`) → `/rubber-duck/new?targetType=REVIEW_ITEM&targetId={reviewItemId}`. 헷갈린 카드를 말로 풀어 보게 하는 진입점이다(U-9). AI 불가이면 목록을 숨긴다.
  - 카드 순서는 서버가 교차 학습(RV-INTERLEAVE, `06` §6.5)으로 정한 `GET /reviews/due` 순서 그대로다. 클라이언트가 다시 정렬하지 않는다.

- **컴포넌트**: `ReviewProgressHeader`, `ReviewCardView`, `AnswerField`, `HintRevealLink`, `RevealedAnswer`, `RatingButtonRow`, `EvaluateSwitch`, `AdjustmentToast`, `ReviewSummary`, `CompleteSessionSheet`(Today와 공유).
- **데이터**

| 시점 | API |
|---|---|
| 진입 (`dueReviewsProvider`가 비었을 때만) | `GET /reviews/due` — 세션 동안 목록을 고정한다 |
| 첫 평가 버튼 탭, 진행 중 세션 없음 | (`taskId` 있고 PLANNED면) `PATCH /today/tasks/{taskId}` `{status: IN_PROGRESS, version}` → `POST /learning-sessions` `{learningTaskId: taskId 또는 생략}` |
| 평가 버튼 탭 | `POST /reviews/{reviewItemId}/answer` `{answerText, selfRating, hintLevel, responseSeconds, wasVariant, evaluate}` |
| 끝 화면 "완료 기록" | `POST /learning-sessions/{sessionId}/complete` → (`taskId`) `PATCH /today/tasks/{taskId}` `{status: COMPLETED}` |

  - `hintLevel`: 아무것도 보지 않음 `SELF_EXPLAIN`, "힌트 보기" `CONCEPT_HINT`, "모르겠어요, 정답 볼게요" `FULL_EXAMPLE`(`06` §6.1). "답 확인"을 먼저 누른 뒤에는 hint 단계가 바뀌지 않는다.
  - `responseSeconds`: 카드 표시 시각부터 평가 버튼 탭까지 초(최대 86400).
  - 이미 진행 중 세션(Today main 등)이 있으면 새 세션을 시작하지 않는다(I-05 때문에 기존 세션이 닫히는 것을 막는다).
- **상태**
  - Loading: 카드 skeleton 1장.
  - Empty: 진입 시 0장이면 `review.home.empty`와 같은 문구 + "Today로".
  - Error (답변 저장 실패): 카드와 선택을 유지하고 토스트 + "다시 시도". 다음 카드로 넘어가지 않는다.
  - `409 INVALID_STATE_TRANSITION`(아직 due가 아니거나 다른 곳에서 일시중지·보관) 또는 `409 CONCURRENT_MODIFICATION`(변형 문제 상태가 바뀜): 토스트 `review.session.cardChanged` 후 이 카드를 건너뛴다. 세션 끝에 `dueReviewsProvider`를 무효화한다.
  - AI unavailable: 채점 스위치를 숨긴다. 평가 실패·차단은 HTTP 오류가 아니라 `evaluationSkippedReason`으로 온다(`05-api-spec.md` §1.9.4). 정상 흐름으로 진행한다.
  - Budget warning: 채점 스위치 아래 `ai.budgetWarning.note`.
  - Offline: 공통. 평가 버튼 비활성, 카드는 계속 볼 수 있다.
- **행동·검증**
  - `answerText` ≤ 5000자.
  - "✕" 또는 뒤로가기: 답한 카드가 있고 이 화면이 세션을 시작했으면 `CompleteSessionSheet`(제목 `today.partial.title`)를 연다. 답한 카드가 없으면 바로 나간다.
  - 데스크톱 단축키(텍스트 필드 포커스가 없을 때): `Space` 답 확인, `H` 힌트 보기, `1`~`4` 평가(답 확인 후), `E` 채점 스위치. 텍스트 필드 안에서는 `Ctrl/Cmd+Enter` = 답 확인, `Esc` = 포커스 해제.
  - 답 확인 후 포커스는 "정답" 제목으로 이동하고 스크린 리더가 정답을 읽는다.
- **문구**

| key | 문구 |
|---|---|
| `review.session.progress` | {current} / {total} |
| `review.session.variant` | 변형 문제 |
| `review.session.answer.label` | 내 답 (선택) |
| `review.session.answer.hint` | 머릿속으로 떠올려도 괜찮아요. |
| `review.session.hint` | 힌트 보기 |
| `review.session.showAnswerFirst` | 모르겠어요, 정답 볼게요 |
| `review.session.reveal` | 답 확인 |
| `review.session.expected` | 정답 |
| `review.session.rubric` | 핵심 포인트 |
| `review.session.evaluate` | AI로 채점하기 |
| `review.session.ratePrompt` | 얼마나 잘 떠올렸나요? |
| `review.session.evaluating` | AI가 채점하고 있어요 (최대 20초) |
| `review.adjust.changed` | '{from}' → '{to}'로 조정했어요 |
| `review.adjust.EVALUATED_INCORRECT` | AI 채점에서 핵심이 빠졌어요. |
| `review.adjust.EVALUATED_PARTIAL` | AI 채점에서 일부만 충족했어요. |
| `review.adjust.HINT_CAP_AGAIN` | 정답을 먼저 봤어요. |
| `review.adjust.HINT_CAP_HARD` | 힌트를 봤어요. |
| `review.adjust.HINT_CAP_GOOD` | 질문 힌트를 봤어요. |
| `review.adjust.aiOutcome` | AI 채점: {outcome} |
| `review.adjust.evaluationSkipped` | AI 채점은 건너뛰고 자기평가로 저장했어요. |
| `review.adjust.nextDue` | 다음 복습: {date} |
| `review.summary.title` | {count}장 복습했어요 |
| `review.summary.complete` | 완료 기록 |
| `review.summary.toToday` | Today로 |
| `review.saveFailed` | 답을 저장하지 못했어요. |
| `review.adjust.leech` | 여러 번 헷갈린 카드라 잠시 쉬게 했어요. 관련 학습 과제로 먼저 다뤄요. |
| `review.session.cardChanged` | 이 카드는 상태가 바뀌어 건너뛰었어요. |
| `review.session.next` | 다음 카드 |
| `review.summary.explainTitle` | 헷갈린 카드, 말로 설명해 볼까요? |
| `review.summary.explain` | 설명해 보기 |

#### SCR-REVIEW-ITEMS

- **목적**: 복습 카드를 찾아 일시중지·다시 사용·보관·수정한다(FR-11). **진입**: SCR-REVIEW-HOME "카드 관리", SCR-SKILL-DETAIL "복습 카드". **Sprint**: S2.
- **레이아웃**: 상단 상태 `SegmentedButton`(사용 중·일시중지·보관, 기본 사용 중) + skill 필터 칩(탭 → `SkillPicker`) + "추가" 아이콘 버튼. 목록 행: 문항 2줄 말줄임, `skill 이름 · 유형 라벨 · 출처 라벨`, `다음 복습 {date}` 또는 상태, 마지막 결과 라벨. 행 오른쪽 `⋮` 메뉴.
- **출처 라벨**: `SEED_CARD` 기본 카드 · `MANUAL` 직접 만듦 · `CHALLENGE_ATTEMPT` 문제 풀이 · `COACH_FINDING` 코드 리뷰 · `EVIDENCE` 증거 · `RUBBER_DUCK` 러버덕(설명하다 막힌 곳) · `REDO_TASK` 재현 과제(혼자 다시 만들 때 막힌 곳) · `TERM` 용어 사전(S3) · `TIP` 오늘의 팁(S3 — "새로 알았어요"를 고른 팁).
- **데이터**: 진입·필터 변경 시 `GET /review-items?skillId=&status=&cursor=`. 목록 끝 80% 스크롤 시 `nextCursor`로 다음 페이지. `⋮` 메뉴 → `PATCH /review-items/{reviewItemId}` `{status, version}`.
- **메뉴 항목**: ACTIVE → "일시중지", "보관", "수정", "러버덕으로 설명하기" / SUSPENDED → "다시 사용", "보관", "수정", "러버덕으로 설명하기" / ARCHIVED → 메뉴 없음(보관은 되돌리지 않는다, `04` §4.5). "러버덕으로 설명하기"(S3, `rubber_duck` flag, AI 가능할 때만) → `/rubber-duck/new?targetType=REVIEW_ITEM&targetId={reviewItemId}`.
- **상태**: Loading — 행 skeleton 6개. Empty — 필터별 `review.items.empty.{status}` + "카드 직접 추가"(ACTIVE일 때만). Error·Offline — 공통. 페이지 추가 로드 실패 — 목록 끝에 "다시 불러오기" 행.
- **행동·검증**: "보관"은 확인 대화상자(`review.items.archive.confirm`). "다시 사용"은 즉시 실행 후 토스트 `review.items.reactivated`(다음 plan-day부터 출제). `409 CONCURRENT_MODIFICATION`이면 목록을 다시 불러오고 토스트.
- **문구**

| key | 문구 |
|---|---|
| `review.items.title` | 복습 카드 |
| `review.items.nextDue` | 다음 복습 {date} |
| `review.items.suspend` | 일시중지 |
| `review.items.reactivate` | 다시 사용 |
| `review.items.archive` | 보관 |
| `review.items.edit` | 수정 |
| `review.items.explain` | 러버덕으로 설명하기 |
| `review.items.archive.confirm` | 보관한 카드는 다시 출제되지 않고 되돌릴 수 없어요. 보관할까요? |
| `review.items.reactivated` | 내일부터 다시 출제돼요. |
| `review.items.empty.ACTIVE` | 사용 중인 카드가 없어요. |
| `review.items.empty.SUSPENDED` | 일시중지한 카드가 없어요. 여러 번 헷갈린 카드는 자동으로 이곳에 모여요. |
| `review.items.empty.ARCHIVED` | 보관한 카드가 없어요. |

#### SCR-REVIEW-ITEM-EDIT

- **목적**: 복습 카드를 직접 만들거나 문항·정답을 고친다(FR-11). **진입**: 생성 `/review/items/new`, 수정 `/review/items/:reviewItemId`(SCR-REVIEW-ITEMS 메뉴). **Sprint**: S2.
- **레이아웃**

```text
┌────────────────────────────────┐
│ ← 카드 추가                저장 │
│ 기술 *                          │
│ [ Spring Transaction        ▾ ] │
│ 유형                            │
│ [떠올리기✓][설명][버그 찾기]     │
│ 질문 *                          │
│ ┌────────────────────────────┐ │
│ │                              │ │
│ └────────────────────────────┘ │
│                       0 / 2000 │
│ 정답 *                          │
│ ┌────────────────────────────┐ │
│ │                              │ │
│ └────────────────────────────┘ │
│                       0 / 3000 │
│ 핵심 포인트 * (1~6개)            │
│ 1 [프록시 기반 AOP 언급      ] ✕ │
│ + 포인트 추가                    │
│ 첫 포인트는 복습할 때 힌트로      │
│ 보여요.                          │
│ ┌────────────────────────────┐ │
│ │            저장              │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

- **수정 모드**: 기술·유형·핵심 포인트는 읽기 전용으로 표시한다(`PATCH`가 `prompt`, `expectedAnswer`만 받는다). 제목 `review.edit.titleEdit`.
- **데이터**: 기술 목록 `GET /skills/tree?role=JAVA_BACKEND`(캐시). 생성 `POST /review-items` `{skillCode, conceptKey, reviewType, prompt, expectedAnswer, rubric}` — `conceptKey`는 화면을 열 때 `MANUAL:` + 대문자 UUID v4로 만들어 재시도에도 유지한다. `201`은 생성, `200`은 같은 개념 카드가 이미 있어 그 카드를 다시 활성화한 것이다(토스트 `review.edit.exists`). 수정 `PATCH /review-items/{reviewItemId}` `{prompt, expectedAnswer, version}`.
- **수정 대상 로드**: 단건 조회 API가 없으므로 SCR-REVIEW-ITEMS가 go_router `extra`로 항목을 넘긴다. `extra`가 없으면(새로고침·직접 URL) `/review/items`로 이동하고 토스트 `review.edit.reopen`.
- **상태**: Loading — 기술 드롭다운만 skeleton. Error — 저장 실패 시 입력 유지 + §5. Offline — 저장 비활성. AI — 해당 없음.
- **행동·검증**: §3.2(질문 1~2000, 정답 1~3000, 포인트 1~6개 각 1~500). "저장" 활성 조건 = 필수 입력 충족 + 변경 있음. 저장 성공 → 뒤로 + 토스트 `review.edit.created`(생성) / `review.edit.saved`(수정). 입력 중 이탈 → §6.8.
- **문구**

| key | 문구 |
|---|---|
| `review.edit.titleNew` | 카드 추가 |
| `review.edit.titleEdit` | 카드 수정 |
| `review.edit.skill` | 기술 |
| `review.edit.type` | 유형 |
| `review.edit.prompt` | 질문 |
| `review.edit.expected` | 정답 |
| `review.edit.rubric` | 핵심 포인트 (1~6개) |
| `review.edit.rubric.add` | 포인트 추가 |
| `review.edit.rubric.help` | 첫 포인트는 복습할 때 힌트로 보여요. |
| `review.edit.save` | 저장 |
| `review.edit.created` | 카드를 추가했어요. 내일부터 복습에 나와요. |
| `review.edit.saved` | 저장했어요. |
| `review.edit.exists` | 같은 개념의 카드가 이미 있어 그 카드를 다시 복습에 넣었어요. |
| `review.edit.reopen` | 카드 목록에서 다시 선택해 주세요. |

### 3.7 Training

#### SCR-TRAINING-LIST

- **목적**: 풀 수 있는 연습 문제를 찾고, 필요하면 AI로 새 문제를 만든다(FR-09). **진입**: rail Training, More > 문제 풀이, SCR-SKILL-DETAIL "문제 풀기"(`?skillId=`). **Sprint**: S3 (AI 문제 만들기 S5 — `challenge_generate` flag가 꺼진 빌드는 "AI로 문제 만들기" 버튼·시트를 숨기고, Empty 상태 버튼은 "Today로"만 둔다).
- **레이아웃**

```text
┌────────────────────────────────┐
│ 문제 풀이                        │
│ [기술: 전체 ▾]                   │
│ ┌────────────────────────────┐ │
│ │ 설정 로더 예외 처리 개선       │ │
│ │ L2 작은 변형 · 약 15분         │ │
│ │ Java Exception · 기본 문제     │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │
│ │ 트랜잭션 전파 수정하기  [AI]   │ │
│ │ L3 개념 조합 · 약 25분         │ │
│ └────────────────────────────┘ │
│ …                               │
│ ┌────────────────────────────┐ │
│ │      AI로 문제 만들기         │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

- **문제 만들기 시트**: 기술 선택(필수, 기본 = 필터 skill), 난이도 L1~L5 칩(기본 L2), 풀이 시간 칩 15·20·30·45·60분(기본 20), 안내 `training.generate.cost`. "만들기" → `POST /challenges/generate` `{skillId, difficulty, targetMinutes}` → `202 AsyncStatusView` → `/training/challenges/{id}`.
- **컴포넌트**: `SkillFilterChip`, `ChallengeTile`(제목, 난이도 라벨, 예상 시간, skill 이름, `AI` 배지 = `origin=AI_GENERATED`, `lastAttempt.status ∈ {STARTED, SUBMITTED}`면 `풀이 중` 라벨, 그 외 `lastAttempt.outcome` 라벨 — 표시용), `GenerateChallengeSheet`, `AiUnavailableBanner`, `BudgetWarningNote`.
- **데이터**: 진입·필터 변경 `GET /challenges?skillId=&purpose=PRACTICE&cursor=`, 스크롤 페이지네이션. 행 탭 → `/training/challenges/{challengeId}`.
- **상태**: Loading — 타일 skeleton 4개. Empty — `training.list.empty` + "AI로 문제 만들기"(AI 가능할 때) 또는 "Today로". Error·Offline — 공통. AI unavailable — 상단 배너, "AI로 문제 만들기" 비활성 + 사유. 문제 풀기는 가능(단 제출은 막힘, SCR-TRAINING-ATTEMPT). Budget warning — 버튼 아래 경고.
- **문구**

| key | 문구 |
|---|---|
| `training.list.title` | 문제 풀이 |
| `training.list.filter.skill` | 기술: {name} |
| `training.list.filter.all` | 전체 |
| `training.list.aiBadge` | AI |
| `training.list.empty` | 이 기술로 풀 수 있는 문제가 아직 없어요. |
| `training.generate.button` | AI로 문제 만들기 |
| `training.generate.title` | 새 문제 만들기 |
| `training.generate.difficulty` | 난이도 |
| `training.generate.cost` | AI 호출 1회를 사용해요. 만드는 데 1~3분 걸려요. |
| `training.generate.submit` | 만들기 |
| `training.generate.minutes` | 풀이 시간 |
| `training.list.inProgress` | 풀이 중 |

#### SCR-CHALLENGE-DETAIL

- **목적**: 문제 내용을 확인하고 풀이를 시작한다. AI 생성 중인 문제의 진행 상태를 보여준다(FR-09). **진입**: SCR-TRAINING-LIST, SCR-TODAY CHALLENGE task(`?taskId=`), 문제 만들기 직후. **Sprint**: S3.
- **레이아웃**: 앱 바(제목) → 메타 줄(`L{n} 라벨 · 약 {m}분 · skill 이름들`, `AI` 배지, **`timeLimitMinutes`가 있으면 `training.detail.timeLimit` 칩**) → "상황"(scenario) → "문제"(prompt) → "제약"(constraints 목록) → 하단 고정 primary "풀기 시작". 이어 풀 attempt가 있으면 primary "이어서 풀기", 보조 "새로 풀기".
- **시간 제한 (S3)**: `ChallengeView.timeLimitMinutes`(`05` §10.1)가 있으면 메타 줄에 "시간 제한 {n}분"을 보이고 하단 primary 위에 `training.detail.timeLimit.note`를 한 줄 둔다 — **서버가 시간을 강제하지 않고** 화면이 경과 시간을 보여 줄 뿐이며, 넘겨도 실패가 아니다. `null`이면 칩과 안내를 모두 숨긴다.
- **데이터**: 진입 `GET /challenges/{challengeId}`. `generationStatus ∈ {PENDING, RUNNING}`이면 §3.3 Async polling(같은 API). "풀기 시작" → `POST /challenges/{challengeId}/attempts` → `201 AttemptView` → `/training/attempts/{id}?taskId=`(있으면 유지). 버튼은 `ChallengeView.activeAttemptId`로 정한다: null → "풀기 시작"만. 값이 있으면 `GET /challenge-attempts/{activeAttemptId}`로 상태를 읽어 `STARTED`/`SUBMITTED` → "이어서 풀기"만(→ `/training/attempts/{activeAttemptId}`), `EVALUATED` → "결과 보기"(보조) + "새로 풀기"(primary). "풀기 시작"이 `409 INVALID_STATE_TRANSITION`이면 challenge를 다시 조회해 `activeAttemptId`로 이동한다.
- **상태**
  - Loading: 제목·본문 skeleton.
  - Async pending: 본문 대신 `AsyncStatusIndicator` + `training.detail.generating`.
  - 생성 `FAILED`: `failureCode` 문구(§5.2) + "다시 만들기"(`skills[0].id`, `difficulty`, `targetMinutes` = 20으로 `POST /challenges/generate`).
  - `status=REJECTED`: `training.detail.rejected` + "다시 만들기".
  - `status=RETIRED`: 내용은 보여주고 "풀기 시작" 비활성 + `training.detail.retired`.
  - Error·Offline: 공통. AI unavailable: 풀기는 가능, 상단에 `training.detail.aiSubmitNote`.
- **문구**

| key | 문구 |
|---|---|
| `training.detail.timeLimit` | 시간 제한 {minutes}분 |
| `training.detail.timeLimit.note` | 시간을 재 드려요. 넘겨도 괜찮고 기록에 불이익은 없어요. |
| `training.detail.scenario` | 상황 |
| `training.detail.prompt` | 문제 |
| `training.detail.constraints` | 제약 |
| `training.detail.start` | 풀기 시작 |
| `training.detail.continue` | 이어서 풀기 |
| `training.detail.new` | 새로 풀기 |
| `training.detail.viewResult` | 결과 보기 |
| `training.detail.generating` | 문제를 만들고 있어요. |
| `training.detail.rejected` | 문제를 만들지 못했어요. 다시 만들어 주세요. |
| `training.detail.regenerate` | 다시 만들기 |
| `training.detail.retired` | 더 이상 제공하지 않는 문제예요. |
| `training.detail.aiSubmitNote` | 지금은 AI를 쓸 수 없어 답 제출·평가가 막혀 있어요. 설명 작성과 1~3단계 힌트는 쓸 수 있어요. |

#### SCR-TRAINING-ATTEMPT

- **목적**: 먼저 접근을 설명하고, 필요한 만큼만 힌트를 받고, 답을 제출해 평가를 받는다(FR-09, FR-10, FR-15). **진입**: SCR-CHALLENGE-DETAIL, SCR-DIAGNOSTICS. **Sprint**: S3.
- **라우트 쿼리**: `?taskId=` (Today CHALLENGE task에서 온 경우).
- **레이아웃 구성**: 모바일은 한 열에 섹션 순서 `문제(접힘 가능) → ① 먼저 설명 → ② 힌트 → ③ 답 제출 → 제출 결과`. 데스크톱(≥ 1024)은 왼쪽 열(40%) 문제·제약 고정 스크롤, 오른쪽 열(60%) ①~결과.

- **레이아웃 ① 먼저 설명 (self-explanation 전)**

```text
┌────────────────────────────────┐
│ ← 설정 로더 예외 처리 개선    ⋮ │
│ L2 작은 변형 · 약 15분           │
│ 시간 제한 20분 · 지난 시간 06:12 │  timeLimitMinutes 가 있을 때
│ ▾ 문제                           │
│ 파일에서 설정값을 읽는 유틸리티가 │
│ 모든 예외를 삼키고 null을 반환…   │
│ 제약: JDK만 사용, 시그니처 유지   │
│ ─────────────────────────────  │
│ ① 먼저 어떻게 풀지 설명해 주세요  │
│ 어떤 예외를 어디서 처리할지,      │
│ 왜 그렇게 할지 적어 보세요.       │
│ ┌────────────────────────────┐ │
│ │                              │ │
│ │                              │ │
│ └────────────────────────────┘ │
│                       0 / 5000 │
│ 건너뛰기            [설명 저장]  │
│ ─────────────────────────────  │
│ ② 힌트      설명 후에 열려요      │
│ ③ 답 제출   설명 후에 열려요      │
└────────────────────────────────┘
```

- **레이아웃 ② 힌트 사다리 + 제출 입력**

```text
┌────────────────────────────────┐
│ ① 내 설명 ✓                  ▸ │
│ ─────────────────────────────  │
│ ② 힌트 (필요할 때만)             │
│ ┌────────────────────────────┐ │
│ │ ✓ 1 질문                      │ │
│ │   호출하는 쪽은 실패했을 때    │ │
│ │   무엇을 알고 싶을까요?        │ │
│ │ ┌──────────────────────────┐ │ │
│ │ │   2 개념 힌트 보기         │ │ │ ← 다음 단계만 활성
│ │ └──────────────────────────┘ │ │
│ │   3 방향                 🔒  │ │
│ │   4 의사코드 ⚠           🔒  │ │
│ │   5 부분 코드 ⚠          🔒  │ │
│ │   6 전체 예시 ⚠          🔒  │ │
│ │ 지금까지: 질문 힌트           │ │
│ │ → 맞히면 '스스로 해결'로 인정 │ │
│ └────────────────────────────┘ │
│ ─────────────────────────────  │
│ ③ 답 제출 (0/5회 사용)           │
│ 설명 답안                         │
│ ┌────────────────────────────┐ │
│ │                              │ │
│ └────────────────────────────┘ │
│ 코드 (선택)        언어 [Java ▾] │
│ ┌────────────────────────────┐ │
│ │ monospace                    │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │
│ │            제출              │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

- **레이아웃 ③ 확인 대화상자 (`PSEUDOCODE` 이상)**

```text
┌──────────────────────────────┐
│ 의사코드 힌트를 볼까요?         │
│                              │
│ 이 힌트를 보면                 │
│ • 이번 풀이는 정답이어도        │
│   '힌트로 해결'로 기록돼요      │
│ • 이 문제가 복습 카드로 다시     │
│   나와요                        │
│ • 스스로 해결한 증거로는         │
│   인정되지 않아요                │
│                              │
│          [취소]  [힌트 보기]    │
└──────────────────────────────┘
```

  - `FULL_EXAMPLE`이고 제출이 0회면 제목 `training.hint.giveUp.title`, 확인 버튼 `training.hint.giveUp.confirm`으로 바꾸고 `giveUp=true`를 함께 보낸다.

- **경과 시간 (S3)** — challenge의 `timeLimitMinutes`(`05` §10.1)가 있을 때만
  - 헤더 메타 줄 아래에 `training.attempt.timeLimit`(시간 제한)과 `training.attempt.elapsed`(지난 시간 `mm:ss`)를 한 줄로 보인다. `timeLimitMinutes`가 `null`이면 **줄 전체를 숨긴다** — 시간 제한이 없는 문제에 시계를 붙이지 않는다.
  - 기준 시각은 `AttemptView.startedAt`이고 1초마다 갱신한다. 새로고침하거나 다른 화면에 다녀와도 `startedAt`으로 다시 계산하므로 값이 이어진다(기기에 따로 저장하지 않는다).
  - **제한을 넘겨도 막지 않는다.** 넘으면 숫자만 `color.warning`으로 바꾸고 아래에 `training.attempt.overTime`을 한 줄 둔다. 제출을 막거나 경고 대화상자를 띄우지 않고, "시간 초과"라는 상태도 만들지 않는다(`05` §10.1 — 서버는 시간을 강제하지 않고 outcome 계산에도 쓰지 않는다).
  - 제출할 때 지금까지의 경과 초를 `elapsedSeconds`(0~86400, 내림)로 함께 보낸다. 다시 제출하면 서버가 마지막 값으로 덮어쓴다(`05` §10.9). 타이머를 못 구한 경우(값이 없음)에는 `elapsedSeconds`를 보내지 않는다.
  - attempt가 `SUBMITTED`·`EVALUATED`·`ABANDONED`이면 타이머를 멈추고, `AttemptView.elapsedSeconds`가 있으면 그 값을 `training.attempt.tookTime`으로 한 줄 보인다.
  - `MediaQuery.disableAnimations`에서도 1초 갱신은 유지한다(애니메이션이 아니라 값이다). 스크린 리더는 이 줄을 live region으로 읽지 **않는다** — 매초 읽히면 방해가 된다(A-6).

- **지시어 안내 (S3)**: self-explanation을 저장한 뒤 `AttemptView.vagueReferenceCount`가 1 이상이면 "① 내 설명" 접힘 영역 안, 저장한 설명 아래에 보조 한 줄 `rubberDuck.vague.count`를 둔다(SCR-RUBBER-DUCK과 같은 문구·같은 규칙, `06` §9.6). 오류 색을 쓰지 않고 저장·제출을 막지 않는다. `selfExplanation`이 없거나 건너뛰었으면 값이 `null`이라 줄을 숨긴다.

- **레이아웃 ④ 평가 대기 (polling)**

```text
┌────────────────────────────────┐
│ ③ 제출 1 · 평가 중               │
│ ┌────────────────────────────┐ │
│ │ ◌  AI가 답을 평가하고 있어요.  │ │
│ │    보통 1분 안팎 걸려요.       │ │
│ │    다른 화면으로 이동해도       │ │
│ │    결과는 저장돼요.             │ │
│ └────────────────────────────┘ │
│ (3분 초과)                       │
│ ┌────────────────────────────┐ │
│ │ 생각보다 오래 걸리고 있어요.   │ │
│ │ 나중에 다시 확인해 주세요.     │ │
│ │              [다시 확인]      │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

- **레이아웃 ⑤ 결과**

```text
┌────────────────────────────────┐
│ 제출 1 결과                       │
│ [일부 충족]   풀이: [일부 해결]    │
│ 핵심 기준 3개 중 2개 충족          │
│ ✓ 충족   원인 예외(cause)를 보존   │
│   "throw new ConfigLoad…(path,e)"│
│ ✗ 미충족 복구 가능한 계층에서만     │
│          처리하는 이유 설명        │
│ ✓ 충족   null 대신 의미 있는 예외  │
│ 짚어 볼 오해                       │
│ • checked 예외는 항상 잡아야 한다고 │
│   설명함                           │
│ 더 생각해 볼 질문                   │
│ 이 예외를 호출자가 복구할 수 없다면 │
│ 어떤 타입이 더 적절할까요?          │
│ ⓘ 이 문제는 내일 복습 카드로 나와요 │
│ ┌────────────────────────────┐ │
│ │   수정해서 다시 제출 (2/5)     │ │
│ └────────────────────────────┘ │
│ Today로 돌아가 완료하기            │  taskId 있을 때
│ 증거로 남기기                      │  S6, 해결 outcome일 때
│ 러버덕으로 설명하기                  │  S3, rubber_duck flag
│ ▸ 이전 제출 (0)                   │
└────────────────────────────────┘
```

- **컴포넌트**: `ChallengeHeader`, `CollapsibleProblem`, `SelfExplanationForm`, `HintLadder`(§6.2), `HintConfirmDialog`, `SubmissionForm`(`CodeField`: monospace, 탭 입력 = 공백 4칸, 줄 번호 없음), `AsyncStatusIndicator`, `EvaluationResultCard`(`OutcomeBadge`, `RubricList`), `SubmissionHistory`, `AttemptMenu`(그만두기).
- **데이터**

| 시점 | API |
|---|---|
| 진입 | `GET /challenge-attempts/{attemptId}` → `GET /challenges/{challengeId}` |
| 설명 저장 / 건너뛰기 | `POST /challenge-attempts/{attemptId}/self-explanation` `{text}` / `{skipped: true}` |
| 힌트 단계 버튼 | `POST /challenge-attempts/{attemptId}/hints` `{requestedLevel, acknowledgeEvidenceImpact?, giveUp?}` (동기) |
| 제출 | `POST /challenge-attempts/{attemptId}/submissions` `{answerText, code, language, elapsedSeconds}` → `202` (`elapsedSeconds`는 시간 제한이 있는 문제에서만 보낸다) |
| 평가 대기 | `GET /challenge-attempts/{attemptId}` 2초 polling, 최신 submission `evaluationStatus ∈ {COMPLETED, FAILED}`이면 중지 |
| 평가 완료 직후 | `GET /challenges/{challengeId}` 재조회(rubric·expectedConcepts가 이제 포함됨) |
| 평가 실패 "다시 평가" | `POST /challenge-attempts/{attemptId}/submissions/{submissionNo}/retry` → polling 재시작 |
| 그만두기 | `POST /challenge-attempts/{attemptId}/abandon` |
| 증거로 남기기 (S6) | `AttemptView.evidenceSourceEventId`가 있고 AI 사용 가능 → `POST /evidence/drafts` `{sourceLearningEventId: evidenceSourceEventId}` (IK) → `/evidence/{id}`. null이거나 AI 불가 → `/evidence/new`(`extra`: 제목 = `challengeTitle`, 기술 = challenge `skills[0].code`) |

- **상태**
  - Loading: 문제·섹션 skeleton.
  - self-explanation 전: ②③ 섹션은 잠금 표시(`training.locked`).
  - 힌트 요청 중: 해당 단계 행에 진행 표시. 1~3단계는 즉시, 4단계 이상은 `training.hint.generating`(최대 20초). 요청 중 다른 힌트·제출 버튼 비활성.
  - Async pending: 레이아웃 ④. 화면을 벗어났다 돌아오면 attempt 조회로 상태를 복원하고 polling을 다시 시작한다.
  - 평가 `FAILED`: 결과 자리에 `failureCode` 문구 + "다시 평가"(`retryable=true`일 때). 최신 제출이 `FAILED`인 동안 새 제출 영역은 잠근다(서버 409). `AI_BUDGET_EXCEEDED`면 "다시 평가" 비활성 + 사유.
  - attempt `ABANDONED`: 읽기 전용, 상단 `training.attempt.abandoned`.
  - AI unavailable: 제출 버튼 비활성 + `training.detail.aiSubmitNote`. 4~6단계 힌트 비활성, 1~3단계 사용 가능.
  - **재현 잠금 (S4)**: 이 challenge의 재현 과제가 열려 있으면(`409 AI_ASSIST_LOCKED_FOR_REDO`, RE-5·HL-9) 힌트 버튼 **전 단계**와 "러버덕으로 설명하기"를 비활성으로 두고 그 아래 `today.redo.lockedElsewhere` 1줄 + "Today로 가기"를 보인다(§5.1). 자기설명·제출·평가는 그대로 된다 — 잠기는 것은 AI 도움뿐이다. 잠금 여부는 미리 알 수 없으므로 첫 409를 받은 뒤 이 상태로 바꾼다.
  - Budget warning: 제출 버튼·4단계 이상 힌트 아래 `ai.budgetWarning.note`.
  - Offline: 공통. 입력은 계속 가능, 전송 버튼 비활성.
- **행동·검증**
  - 설명 1~5000자. "건너뛰기"는 확인 대화상자 `training.selfExplain.skip.confirm`. 저장한 설명은 고칠 수 없으므로 저장 버튼 위에 `training.selfExplain.final`을 표시한다.
  - 힌트: 활성 버튼은 `maxHintLevel + 1` 단계 하나뿐이다. 이미 공개한 단계는 내용을 펼쳐 볼 수 있다. `PSEUDOCODE`·`PARTIAL_CODE`·`FULL_EXAMPLE`은 확인 대화상자를 거친다. 힌트 아래 요약 줄은 현재 max 기준 결과 영향을 보여준다: `SELF_EXPLAIN`·`QUESTION_ONLY` → `training.hint.impact.independent`, `CONCEPT_HINT`·`DIRECTION` → `training.hint.impact.withHints`, `PSEUDOCODE` 이상 → `training.hint.impact.review`.
  - 제출: §3.2(답안·코드 중 하나 이상, code가 있으면 언어 필수). 제출 횟수 5회 사용 시 버튼 비활성 + `training.submit.limit`.
  - 입력 보관: 답안·코드는 `localStorage` `devpilot.attempt.draft.<attemptId>`에 1초 debounce로 저장(try/catch), 제출 성공 시 삭제.
  - 결과의 복습 안내(ⓘ)는 `AttemptView.reviewScheduled`가 비어 있지 않을 때 그 `dueDate`로 표시한다(`06` §8.3).
  - "Today로 돌아가 완료하기" → `/today?complete={taskId}`.
  - "러버덕으로 설명하기"(결과가 나온 뒤, AI 가능할 때) → `/rubber-duck/new?targetType=CHALLENGE&targetId={attemptId}&taskId={taskId}`. 제출한 풀이를 말로 설명해 빈틈을 찾는다(FR-25). self-explanation(①)은 한 번 쓰는 일방향 기록이고, 러버덕은 되묻는 대화다.
  - 러버덕에서 `suggestHint=true`로 돌아오면(`/training/attempts/{attemptId}#hints`) 힌트 섹션으로 스크롤하고 다음 단계 버튼에 포커스를 둔다(RD-3). 서버는 러버덕 턴을 자기설명으로 보지만(`06` §9.5), 화면의 사다리 잠금은 이 attempt의 self-explanation 기록으로 판단한다 — 러버덕 진입점을 결과 화면에 두는 이유다.
  - "그만두기"(메뉴, 평가 중에는 비활성): 확인 대화상자 → abandon → `taskId`가 있으면 `/today?complete={taskId}`, 없으면 `/training`.
  - 코드 입력칸은 한국어 IME 조합·붙여넣기(탭·공백 유지)를 지원해야 한다(스파이크 SP-1).
- **문구**

| key | 문구 |
|---|---|
| `training.problem` | 문제 |
| `training.locked` | 설명 후에 열려요 |
| `training.selfExplain.title` | 먼저 어떻게 풀지 설명해 주세요 |
| `training.selfExplain.help` | 어떤 예외를 어디서 처리할지, 왜 그렇게 할지처럼 접근 방법을 적어 보세요. |
| `training.selfExplain.save` | 설명 저장 |
| `training.selfExplain.final` | 저장한 설명은 고칠 수 없어요. |
| `training.selfExplain.skip` | 건너뛰기 |
| `training.selfExplain.skip.confirm` | 설명 없이 진행할까요? 설명을 먼저 쓰면 설명 능력도 함께 기록돼요. |
| `training.selfExplain.done` | 내 설명 |
| `training.hint.title` | 힌트 (필요할 때만) |
| `training.hint.button` | {level} {label} 보기 |
| `training.hint.generating` | AI가 힌트를 만들고 있어요 (최대 20초) |
| `training.hint.current` | 지금까지: {label} |
| `training.hint.impact.independent` | 맞히면 '스스로 해결'로 인정돼요 |
| `training.hint.impact.withHints` | 맞히면 '힌트로 해결'로 기록돼요 |
| `training.hint.impact.review` | '힌트로 해결'로 기록되고 복습 카드로 다시 나와요 |
| `training.hint.confirm.title` | {label} 힌트를 볼까요? |
| `training.hint.confirm.body` | 이 힌트를 보면\n• 이번 풀이는 정답이어도 '힌트로 해결'로 기록돼요\n• 이 문제가 복습 카드로 다시 나와요\n• 스스로 해결한 증거로는 인정되지 않아요 |
| `training.hint.confirm.ok` | 힌트 보기 |
| `training.hint.giveUp.title` | 포기하고 전체 예시를 볼까요? |
| `training.hint.giveUp.confirm` | 전체 예시 보기 |
| `training.submit.title` | 답 제출 ({used}/5회 사용) |
| `training.submit.answer` | 설명 답안 |
| `training.submit.code` | 코드 (선택) |
| `training.submit.language` | 언어 |
| `training.submit.button` | 제출 |
| `training.submit.validation.empty` | 설명 답안이나 코드 중 하나는 입력해 주세요 |
| `training.submit.validation.language` | 코드 언어를 골라 주세요 |
| `training.submit.limit` | 이 풀이에서 제출 5회를 모두 사용했어요. |
| `training.eval.pending` | AI가 답을 평가하고 있어요. 보통 1분 안팎 걸려요. |
| `training.eval.result` | 제출 {no} 결과 |
| `training.eval.rubricCount` | 핵심 기준 {total}개 중 {met}개 충족 |
| `training.eval.met` | 충족 |
| `training.eval.notMet` | 미충족 |
| `training.eval.misconceptions` | 짚어 볼 오해 |
| `training.eval.followUp` | 더 생각해 볼 질문 |
| `training.eval.reviewScheduled` | 이 문제는 {date}에 복습 카드로 나와요. |
| `training.eval.retry` | 다시 평가 |
| `training.eval.resubmit` | 수정해서 다시 제출 ({next}/5) |
| `training.eval.toToday` | Today로 돌아가 완료하기 |
| `training.eval.toEvidence` | 증거로 남기기 |
| `training.eval.explainWithDuck` | 러버덕으로 설명하기 |
| `training.eval.history` | 이전 제출 ({count}) |
| `training.attempt.timeLimit` | 시간 제한 {minutes}분 |
| `training.attempt.elapsed` | 지난 시간 {time} |
| `training.attempt.overTime` | 제한 시간을 넘었어요. 그대로 이어서 풀어도 괜찮아요. |
| `training.attempt.tookTime` | 이 풀이에 {time} 걸렸어요. |
| `training.attempt.menu.abandon` | 그만두기 |
| `training.attempt.abandon.confirm` | 이 풀이를 그만둘까요? 지금까지의 설명과 힌트 기록은 남아요. |
| `training.attempt.abandoned` | 그만둔 풀이예요. |

### 3.8 Project Coach

#### SCR-COACH-LIST

- **목적**: 지난 코드 리뷰를 보고 새 리뷰를 요청한다(FR-12). **진입**: rail Coach, More > 코드 리뷰. **Sprint**: S4.
- **레이아웃**: 앱 바 "코드 리뷰" → primary "코드 리뷰 요청"(목록 위, 폭 전체) → 목록 행: 1줄 `fileName` 또는 `{contentType 라벨} · {language}`, 2줄 `{createdAt} · {상태 라벨}`, 3줄(완료 시) `찾은 항목 {findingCount}개`, 원문 삭제 시 `원문 삭제됨`.
- **상태 라벨**: `PENDING`/`RUNNING` 분석 중 · `COMPLETED` + `closedAt=null` 확인 중 · `closedAt` 있음 마침 · `FAILED` 분석 실패.
- **데이터**: 진입 `GET /coach/reviews?cursor=`, 스크롤 페이지네이션. 목록은 polling하지 않는다(당겨서 새로고침·화면 복귀 시 재조회).
- **상태**: Loading — 행 skeleton 4개. Empty — `coach.list.empty` + "코드 리뷰 요청". Error·Offline — 공통. AI unavailable — 상단 배너, 요청 버튼 비활성(목록 열람은 가능). Budget warning — 버튼 아래 경고.
- **문구**

| key | 문구 |
|---|---|
| `coach.list.title` | 코드 리뷰 |
| `coach.list.new` | 코드 리뷰 요청 |
| `coach.list.status.analyzing` | 분석 중 |
| `coach.list.status.open` | 확인 중 |
| `coach.list.status.closed` | 마침 |
| `coach.list.status.failed` | 분석 실패 |
| `coach.list.counts` | 찾은 항목 {count}개 |
| `coach.list.purged` | 원문 삭제됨 |
| `coach.list.empty` | 아직 코드 리뷰가 없어요. 개인 프로젝트 코드를 붙여넣고, 먼저 스스로 살펴본 뒤 AI에게 질문을 받아 보세요. |

#### SCR-COACH-NEW

- **목적**: 코드·diff·로그를 붙여넣고 **분석 전에 스스로 우려점을 적은 뒤** 분석을 요청한다(FR-12, AC-14, AC-19). **진입**: SCR-COACH-LIST, SCR-TODAY COACH_REVIEW task(`?taskId=`). **Sprint**: S4.
- **레이아웃 (모바일)**

```text
┌────────────────────────────────┐
│ ← 코드 리뷰 요청                 │
│ ┌────────────────────────────┐ │  BUDGET_WARNING일 때
│ │ ⓘ 이번 달 AI 사용량이 80%를   │ │
│ │   넘었어요.                   │ │
│ └────────────────────────────┘ │
│ 무엇을 붙여넣나요?                │
│ [코드✓] [Diff] [로그]             │
│ 언어 [Java ▾] 프로젝트 [Spring Boot▾]│
│ 사이드 프로젝트 (선택) [주문 시스템 ▾]│  ACTIVE 프로젝트가 있을 때
│ 주제 (선택) [HTTP client        ] │
│ 파일 이름 (선택)                  │
│ [ConfigLoader.java             ] │
│ ┌────────────────────────────┐ │
│ │ public String read(Path p) { │ │
│ │   BufferedReader r = …       │ │  monospace, 최소 12줄 높이
│ │                              │ │
│ └────────────────────────────┘ │
│ 12.3KB / 30KB · 420 / 1,000줄    │
│ 비밀값으로 보이는 부분은 저장 전에 │
│ 가려요. 개인 키는 보낼 수 없어요.  │
│ ─────────────────────────────  │
│ 분석 전에, 먼저 살펴봐요           │
│ 이 코드에서 걱정되는 점은?         │
│ ┌────────────────────────────┐ │
│ │ 스트림을 닫는지 모르겠고,      │ │
│ │ 예외를 너무 넓게 잡은 것 같다. │ │
│ └────────────────────────────┘ │
│                       38 / 5000 │
│ ▸ 관련 기술 (선택)                │
│ ─────────────────────────────  │
│ [✓] 회사 코드나 비밀정보가 아닌     │
│     개인 프로젝트 코드예요          │
│ ⓘ 코드는 AI 공급자 DeepSeek(중국)  │
│   으로 전송돼요. 입력을 학습에      │
│   쓰지 않는다는 약정은 없어요.      │
│ 원문은 30일 뒤 자동으로 지워져요.   │
│ ┌────────────────────────────┐ │
│ │          분석 요청             │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

- **데스크톱**: 왼쪽 열(55%) 유형·언어·파일 이름·코드 입력, 오른쪽 열(45%) 우려점·관련 기술·동의·버튼.
- **컴포넌트**: `ContentTypeSegmented`, `LanguageDropdown`(`CodeLanguage`), `ProjectTypeDropdown`(필수: `SPRING_BOOT` Spring Boot · `JAVA_LIBRARY` Java 라이브러리 · `ANDROID` Android · `FLUTTER` Flutter · `OTHER` 기타. 기본 = 마지막 선택, 없으면 `SPRING_BOOT`), `SideProjectDropdown`(선택, FR-26: `ACTIVE` 사이드 프로젝트 목록 + "선택 안 함". 기본 = 목록 첫 항목(가장 최근에 고친 `ACTIVE` 프로젝트, SP-3과 같은 기준). `ACTIVE` 프로젝트가 없으면 드롭다운을 숨기고 `sideProjectId`를 보내지 않는다), `TopicField`(선택, ≤ 100자), `CodeField`, `ContentSizeMeter`, `SelfReviewField`, `SkillMultiPicker`(최대 10), `ConsentCheckbox`, `ProviderNotice`(`coach.new.providerNotice`, 동의 체크박스 바로 아래 `bodySmall` + `info` 아이콘 — ADR-013, `07` §8), `AiUnavailableBanner`, `BudgetWarningNote`.
- **데이터**: 진입 시 `GET /side-projects?status=ACTIVE`(실패하면 드롭다운만 숨김). "분석 요청" → `POST /coach/reviews` `{contentType, language, content, confidentialConsent: true, userSelfReview, sideProjectId(선택), context: {projectType, topic, skillCodes, fileName}}` → `202 AsyncStatusView {id, maskedSecretCount}` → `/coach/{id}`(`taskId` 유지) + `maskedSecretCount > 0`이면 토스트 `coach.new.masked`.
- **상태**
  - Loading: 없음(폼). 관련 기술 목록은 펼칠 때 `GET /skills/tree?role=JAVA_BACKEND`.
  - 전송 중: 버튼 로딩, 모든 입력 읽기 전용.
  - Error: `422 SECRET_DETECTED_BLOCKED` → 코드 입력칸 아래 인라인 오류 `coach.new.validation.privateKey`, `413 CONTENT_TOO_LARGE` → 인라인 `coach.new.validation.tooLarge`, `400 VALIDATION_FAILED`의 field `sideProjectId`(`REFERENCE_NOT_FOUND` — 그사이 프로젝트가 삭제됨) → 드롭다운 아래 인라인 `coach.new.projectGone` + 목록 재조회, 그 외 §5.
  - AI unavailable: 상단 배너, "분석 요청" 비활성 + `coach.new.aiDisabled`. 입력 내용은 유지.
  - Budget warning: 상단 안내(레이아웃 참고), 동작은 허용.
  - Offline: 공통.
- **행동·검증**
  - §3.2: 내용 30000B 이하, ≤ 1000줄, private key 정규식 불일치(내용·우려점 모두), 동의 체크, 프로젝트 유형 선택, 우려점 ≤ 5000자, 파일 이름 ≤ 200자(`/` `\` 제거), 주제 ≤ 100자, 관련 기술 ≤ 10개.
  - 우려점의 관점(`selfReviewAxes`)은 분석 시 서버가 분류하므로 화면에서 고르지 않는다.
  - `ContentSizeMeter`는 입력마다 UTF-8 byte 수(`utf8.encode(text).length`)와 줄 수(`'\n'` 개수 + 마지막 문자가 `'\n'`이 아니면 1)를 갱신한다. 90% 이상이면 경고 색 + 텍스트 `coach.new.nearLimit`, 초과면 오류 색 + 텍스트.
  - private key 정규식이 맞으면 즉시 인라인 오류를 보이고 버튼을 비활성화한다(서버로 보내지 않는다).
  - "분석 요청" 활성 조건: 내용 유효 + 동의 체크 + AI 사용 가능 + 온라인.
  - 우려점이 비어 있으면 요청 전에 대화상자 `coach.new.emptySelfReview`를 띄운다. "그래도 분석"을 누르면 진행한다.
  - 언어 기본값: 마지막으로 고른 값(`localStorage` `devpilot.coach.language`), 없으면 `JAVA`.
  - 입력 중 이탈 → §6.8.
- **문구**

| key | 문구 |
|---|---|
| `coach.new.title` | 코드 리뷰 요청 |
| `coach.new.contentType` | 무엇을 붙여넣나요? |
| `coach.new.language` | 언어 |
| `coach.new.projectType` | 프로젝트 |
| `coach.new.fileName` | 파일 이름 (선택) |
| `coach.new.content.hint` | 코드, git diff, 오류 로그를 붙여넣으세요. |
| `coach.new.size` | {kb}KB / 30KB · {lines} / 1,000줄 |
| `coach.new.nearLimit` | 크기 제한에 가까워요 |
| `coach.new.maskingNote` | 비밀값으로 보이는 부분은 저장 전에 가려요. 개인 키는 보낼 수 없어요. |
| `coach.new.selfReview.title` | 분석 전에, 먼저 살펴봐요 |
| `coach.new.selfReview.label` | 이 코드에서 걱정되는 점은? |
| `coach.new.selfReview.hint` | 예: 스트림을 닫는지 모르겠다, 예외를 너무 넓게 잡은 것 같다 |
| `coach.new.topic` | 주제 (선택) |
| `coach.new.sideProject` | 사이드 프로젝트 (선택) |
| `coach.new.sideProject.none` | 선택 안 함 |
| `coach.new.projectGone` | 이 프로젝트를 찾을 수 없어요. 다시 골라 주세요. |
| `coach.new.skills` | 관련 기술 (선택) |
| `coach.new.consent` | 회사 코드나 비밀정보가 아닌 개인 프로젝트 코드예요 |
| `coach.new.providerNotice` | 코드는 AI 공급자 DeepSeek(중국)으로 전송돼요. 입력을 학습에 쓰지 않는다는 약정은 없어요. |
| `coach.new.retention` | 원문은 30일 뒤 자동으로 지워져요. |
| `coach.new.submit` | 분석 요청 |
| `coach.new.aiDisabled` | 지금은 AI를 쓸 수 없어 분석을 요청할 수 없어요. |
| `coach.new.emptySelfReview.title` | 걱정되는 점 없이 분석할까요? |
| `coach.new.emptySelfReview.body` | 먼저 적어 두면 AI가 찾은 항목 중 내가 이미 떠올린 것을 '스스로 언급'으로 기록해요. 비워 두면 모든 항목이 질문을 받은 뒤 판단돼요. |
| `coach.new.emptySelfReview.write` | 적고 보낼게요 |
| `coach.new.emptySelfReview.proceed` | 그래도 분석 |
| `coach.new.masked` | 비밀값 {count}개를 가리고 저장했어요. |
| `coach.new.validation.tooLarge` | 30KB 이하로 줄여 주세요. 문제가 되는 부분만 붙여넣어도 충분해요. |
| `coach.new.validation.tooManyLines` | 1,000줄 이하로 줄여 주세요. |
| `coach.new.validation.privateKey` | 개인 키(private key)가 포함되어 있어 보낼 수 없어요. 해당 부분을 지워 주세요. |
| `coach.new.validation.consent` | 개인 프로젝트 코드인지 확인해 주세요 |

#### SCR-COACH-DETAIL

- **목적**: 분석 진행을 기다리고, finding마다 질문에 먼저 답하고, 필요하면 힌트를 받고, 수정 완료/해당 없음으로 정리한 뒤 리뷰를 마친다. 마친 뒤에는 발견 방식(`discoveredBy`)을 보여준다(FR-12, FR-13, FR-14). **진입**: SCR-COACH-NEW 요청 직후, SCR-COACH-LIST 행. **Sprint**: S4.
- **라우트 쿼리**: `?taskId=` (Today COACH_REVIEW task에서 온 경우).
- **레이아웃 ① 분석 중**

```text
┌────────────────────────────────┐
│ ← ConfigLoader.java             │
│ 코드 · Java · 10월 13일 오후 9:05 │
│ ┌────────────────────────────┐ │
│ │ ◌ 코드를 분석하고 있어요.      │ │
│ │   보통 1~2분 걸려요.           │ │
│ │   다른 화면으로 이동해도        │ │
│ │   결과는 저장돼요.              │ │
│ └────────────────────────────┘ │
│ ▸ 내가 먼저 적은 우려점           │
│ ▸ 붙여넣은 코드 (420줄)           │
└────────────────────────────────┘
```

- **레이아웃 ② finding 카드 (확인 중)**

```text
┌────────────────────────────────┐
│ ← ConfigLoader.java           ⋮ │
│ 코드 · Java · 10월 13일 오후 9:05 │
│ 비밀값 2개를 가렸어요             │
│ 찾은 항목 4개                     │
│ 버그 1 · 위험 2 · 더 나은 선택 1   │
│ ▸ 내가 먼저 적은 우려점           │
│ ▸ 붙여넣은 코드 (30일 뒤 삭제)    │
│ ┌────────────────────────────┐ │
│ │ [버그] 자원 수명   확인 전     │ │
│ │ [문서 근거] [확신 높음]        │ │
│ │ [내가 먼저 언급 · AI 분류]     │ │
│ │ 실제 오류 가능성이 높아요       │ │
│ │ 스트림이 닫히지 않을 수 있어요  │ │
│ │ 12–18행 보기                   │ │
│ │ 근거: pmd.github.io ↗          │ │
│ │ ────────────────────────────  │ │
│ │ 먼저 생각해 보세요              │ │
│ │ 이 InputStream을 누가, 언제     │ │
│ │ 닫아야 할까요?                   │ │
│ │ 내 생각                          │ │
│ │ ┌──────────────────────────┐ │ │
│ │ │                            │ │ │
│ │ └──────────────────────────┘ │ │
│ │           [답변 보내기]         │ │
│ │ ▸ 힌트 (필요할 때만)            │ │
│ │ [수정 완료]      [해당 없음]    │ │
│ └────────────────────────────┘ │
│ … 다음 finding 카드               │
│ ┌────────────────────────────┐ │
│ │          리뷰 마치기           │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

- **레이아웃 ③ 답변 후 (카드 안)**

```text
│ │ 내 답변 · 오후 9:12             │ │
│ │ try-with-resources로 메서드     │ │
│ │ 안에서 닫아야 해요.              │ │
│ │ AI 피드백                        │ │
│ │ ✓ 문제를 짚었어요                 │ │
│ │ 자원을 만든 쪽이 닫는다는 소유권  │ │
│ │ 관점도 함께 떠올려 보세요.        │ │
│ │ 답변 고치기                       │ │
│ │ ▾ 힌트 (필요할 때만)             │ │
│ │ [ 1 질문 보기 ]                   │ │
│ │   2 개념 힌트            🔒      │ │
│ │   …                               │ │
```

- **레이아웃 ④ 리뷰 마치기 대화상자**

```text
┌──────────────────────────────┐
│ 리뷰를 마칠까요?                │
│                              │
│ 확인 전 항목이 1개 있어요.       │
│ 마치면 지금 상태로 기록되고      │
│ 더 이상 답변·힌트·상태를 바꿀    │
│ 수 없어요.                       │
│ • 우려점에 이미 적은 항목         │
│   → 스스로 언급                   │
│ • 답변에서 문제를 짚은 항목       │
│   → 질문·힌트 후 발견             │
│ • 그 외 → 놓침                     │
│                              │
│      [계속 보기]  [마치기]       │
└──────────────────────────────┘
```

- **레이아웃 ⑤ 마친 뒤**

```text
┌────────────────────────────────┐
│ ← ConfigLoader.java           ⋮ │
│ ✓ 리뷰를 마쳤어요 · 10월 13일     │
│ 스스로 언급 1 · 질문·힌트 후 발견 2│
│ · 놓침 1                          │
│ ┌────────────────────────────┐ │
│ │ [버그] 자원 수명   수정 완료    │ │
│ │ [질문·힌트 후 발견]            │ │
│ │ [문서 근거] [확신 높음]         │ │
│ │ 스트림이 닫히지 않을 수 있어요   │ │
│ │ ▸ 내 답변과 피드백               │ │
│ │ ▸ 본 힌트 (1단계까지)            │ │
│ └────────────────────────────┘ │
│ …                                │
│ Today로 돌아가 완료하기            │  taskId 있을 때
│ 증거로 남기기                      │  S6
└────────────────────────────────┘
```

- **finding 카드 규칙**
  - 배지 순서: `FindingTypeBadge` → 축(`ThinkingAxis` 라벨, 일반 텍스트) → 상태 라벨 → 줄바꿈 → `VerificationBadge` → `ConfidenceBadge` → (`mentionedByUser=true`) `내가 먼저 언급 · AI 분류` 배지. 마친 뒤에는 `DiscoveredByBadge`를 첫 줄 아래에 추가한다.
  - 타입 설명 1줄: `BUG` `coach.finding.type.BUG.desc`, `RISK` `coach.finding.type.RISK.desc`, `LEARNING_POINT` `coach.finding.type.LEARNING_POINT.desc`.
  - 위치 `{start}–{end}행 보기`를 누르면 "붙여넣은 코드"를 펼치고 해당 줄로 스크롤해 배경을 강조한다. 원문이 purge됐으면 위치만 텍스트로 보여준다.
  - 근거: `sourceType ∈ {OFFICIAL_DOC, SECURITY_GUIDE}`이고 `sourceReference`가 `https://`면 호스트명 + `↗` 링크(새 탭, `rel=noopener`). 그 외 `sourceReference`는 일반 텍스트. `AI_REASONING`이고 참조가 없으면 근거 줄을 숨긴다.
  - finding 순서는 `sortOrder` 오름차순.
  - 수정 코드는 어디에도 표시하지 않는다(응답에 없다).
  - `followUpQuestion`과 `feedbackAiMeta`는 저장된다(`CoachFindingView`). 답변 직후뿐 아니라 `GET /coach/reviews/{reviewId}`로 다시 열 때도 AI 피드백 아래에 `coach.finding.followUp`으로 보여주고, 피드백 블록에 `AI` 배지와 `feedbackAiMeta.promptVersion`을 작게 표시한다.
- **컴포넌트**: `ReviewHeader`, `AsyncStatusIndicator`, `CollapsibleSelfReview`, `CodeViewer`(줄 번호, 강조 범위, purge 상태), `FindingCard`, `FindingTypeBadge`·`VerificationBadge`·`ConfidenceBadge`·`DiscoveredByBadge`(§6.1), `FindingResponseForm`, `AiFeedbackBlock`, `HintLadder`(§6.2, target=finding), `FindingStatusActions`, `CloseReviewDialog`, `ReviewMenu`.
- **데이터**

| 시점 | API |
|---|---|
| 진입 | `GET /coach/reviews/{reviewId}` |
| `PENDING`/`RUNNING` | 같은 API 2초 polling, 최대 3분(§3.3) |
| `FAILED` "다시 분석" | `POST /coach/reviews/{reviewId}/retry` → polling 재시작 |
| 답변 보내기 / 답변 고치기 | `POST /coach/reviews/{reviewId}/findings/{findingId}/responses` `{text}` (동기, 최대 20초) → `aiFeedback`, `userIdentifiedIssue`, `followUpQuestion`, `feedbackSkippedReason` |
| 힌트 단계 버튼 | `POST /coach/reviews/{reviewId}/findings/{findingId}/hints` `{requestedLevel, skipSelfExplanation?, acknowledgeEvidenceImpact?, giveUp?}` (동기) |
| 수정 완료 / 해당 없음 | `PATCH /coach/reviews/{reviewId}/findings/{findingId}` `{status: RESOLVED|DISMISSED, version}` |
| 리뷰 마치기 | `POST /coach/reviews/{reviewId}/complete` → `CoachReviewCompleteResponse` — `review`로 화면 갱신(`discoveredBy`), `createdReviewItemCount`로 복습 카드 안내 |
| 메뉴 "원문 지금 삭제" | `DELETE /coach/reviews/{reviewId}/content` |
| 증거로 남기기 (S6, 마친 뒤) | `CoachReviewView.evidenceSourceEventId`가 있고 AI 사용 가능 → `POST /evidence/drafts` `{sourceLearningEventId: evidenceSourceEventId}` (IK) → `/evidence/{id}`. null이거나 AI 불가 → `/evidence/new`(`extra`: 제목 = `fileName` 또는 첫 `BUG`/`RISK` finding `summary`, 기술 = 그 finding의 `skillCode`) |

- **상태**
  - Loading: 헤더 + finding 카드 skeleton 2개.
  - Async pending: 레이아웃 ①. 3분 초과 시 "나중에 다시 확인".
  - `FAILED`: 결과 자리에 `failureCode` 문구(§5.2) + "다시 분석"(`retryable=true`일 때만). `CONFIDENTIAL_SUSPECTED`면 `coach.detail.confidential` + "새로 요청"(→ `/coach/new`)만, 재시도 없음. `AI_BUDGET_EXCEEDED`면 "다시 분석" 비활성.
  - Empty(finding 0개): `coach.detail.noFindings` + "리뷰 마치기".
  - 답변 전송 중: 입력칸 읽기 전용 + 버튼 안 진행 표시 + `coach.detail.feedbackPending`.
  - 피드백 없음(`feedbackSkippedReason` 있음, 응답은 저장됨): `coach.detail.feedbackMissing` + 사유 문구(§5.2). `422 SECRET_DETECTED_BLOCKED`면 답변 입력칸 아래 `coach.new.validation.privateKey`.
  - AI unavailable: 답변 보내기는 허용(피드백 없이 저장됨을 버튼 아래 안내 `coach.detail.feedbackOff`), 힌트 버튼 전체 비활성. 상태 변경·마치기·원문 삭제는 가능.
  - Budget warning: 힌트 영역·답변 버튼 아래 경고.
  - 원문 삭제됨(`contentPurged=true`): 코드 영역 `coach.detail.purged`, 새 힌트 단계 버튼 잠금(`hint.purged`), "다시 분석" 숨김.
  - Closed: 모든 입력·버튼 숨김, 레이아웃 ⑤.
  - Offline: 공통.
- **행동·검증**
  - 답변 1~5000자. 같은 finding에 다시 답하면 최신 답변으로 바뀐다(`04` §4.3)는 안내를 "답변 고치기" 입력칸 위에 표시한다.
  - 힌트: 활성 버튼은 다음 단계 하나. 답변이 없는 상태에서 누르면 대화상자 `coach.hint.skipResponse` → 확인 시 `skipSelfExplanation=true`. `PSEUDOCODE` 이상은 `HintConfirmDialog`(문구 `coach.hint.confirm.body`). `FULL_EXAMPLE`은 항상 `giveUp=true`와 확인을 함께 보낸다.
  - "해당 없음": 확인 대화상자 `coach.finding.dismiss.confirm`. "수정 완료": 확인 없이 실행, 토스트.
  - "리뷰 마치기": 레이아웃 ④. `OPEN` 개수를 대화상자에 표시한다. 마친 뒤 결과 요약 영역으로 스크롤하고, 응답의 `createdReviewItemCount`가 1 이상이면 `coach.detail.reviewCardsAdded`를 보여준다(클라이언트가 세지 않는다, `06` §9.4).
  - "원문 지금 삭제"(분석 중에는 메뉴 숨김): 확인 대화상자 `coach.detail.purge.confirm`. 성공 시 코드 영역 `coach.detail.purged`.
  - 409 `REVIEW_ALREADY_CLOSED`: 재조회 후 읽기 전용으로 전환 + 토스트.
- **문구**

| key | 문구 |
|---|---|
| `coach.detail.meta` | {contentType} · {language} · {createdAt} |
| `coach.detail.analyzing` | 코드를 분석하고 있어요. 보통 1~2분 걸려요. |
| `coach.detail.masked` | 비밀값 {count}개를 가렸어요 |
| `coach.detail.summary` | 찾은 항목 {count}개 |
| `coach.detail.selfReview` | 내가 먼저 적은 우려점 |
| `coach.detail.selfReview.empty` | 적지 않았어요 |
| `coach.detail.code` | 붙여넣은 코드 ({lines}줄) |
| `coach.detail.code.retention` | 붙여넣은 코드 ({date}에 삭제) |
| `coach.detail.purged` | 원문은 삭제됐어요. 찾은 항목은 그대로 남아요. |
| `coach.detail.retry` | 다시 분석 |
| `coach.detail.confidential` | 회사 코드나 비밀정보로 보여 분석하지 않았어요. 개인 프로젝트 코드로 다시 요청해 주세요. |
| `coach.detail.newRequest` | 새로 요청 |
| `coach.detail.noFindings` | 눈에 띄는 항목을 찾지 못했어요. 우려점이 있었다면 그 부분만 다시 붙여넣어 질문해 보세요. |
| `coach.finding.type.BUG.desc` | 실제 오류 가능성이 높아요 |
| `coach.finding.type.RISK.desc` | 지금은 동작하지만 운영·보안·성능에서 문제가 될 수 있어요 |
| `coach.finding.type.LEARNING_POINT.desc` | 틀린 것은 아니에요. 더 나은 선택을 알아 두면 좋아요 |
| `coach.finding.mentionedByUser` | 내가 먼저 언급 · AI 분류 |
| `coach.finding.location` | {start}–{end}행 보기 |
| `coach.finding.source` | 근거: {source} |
| `coach.finding.question` | 먼저 생각해 보세요 |
| `coach.finding.response.label` | 내 생각 |
| `coach.finding.response.send` | 답변 보내기 |
| `coach.finding.response.edit` | 답변 고치기 |
| `coach.finding.response.editNote` | 다시 보내면 최신 답변으로 바뀌어요. |
| `coach.finding.myResponse` | 내 답변 · {time} |
| `coach.detail.feedbackPending` | AI가 답변을 살펴보고 있어요 (최대 20초) |
| `coach.finding.feedback.title` | AI 피드백 |
| `coach.finding.feedback.identified` | 문제를 짚었어요 |
| `coach.finding.feedback.notIdentified` | 다른 관점도 살펴봐요 |
| `coach.detail.feedbackMissing` | 피드백을 받지 못했지만 답변은 저장했어요. |
| `coach.detail.feedbackOff` | 지금은 AI 피드백 없이 답변만 저장돼요. |
| `coach.finding.followUp` | 더 생각해 볼 질문 |
| `coach.detail.reviewCardsAdded` | 복습 카드 {count}개가 추가됐어요. 내일부터 나와요. |
| `hint.purged` | 원문이 삭제되어 새 힌트를 만들 수 없어요. |
| `coach.hint.skipResponse.title` | 답변 없이 힌트를 볼까요? |
| `coach.hint.skipResponse.body` | 먼저 답해 보면 스스로 짚은 항목으로 기록될 수 있어요. |
| `coach.hint.confirm.body` | 이 힌트를 보면 해결 방법에 가까운 내용이 나와요. 이 항목을 스스로 짚었는지는 답변 기준으로만 기록돼요. |
| `coach.finding.resolve` | 수정 완료 |
| `coach.finding.resolved` | 수정 완료로 표시했어요. |
| `coach.finding.dismiss` | 해당 없음 |
| `coach.finding.dismiss.confirm` | 이 지적이 내 코드에 맞지 않다고 표시할까요? 되돌릴 수 없고, AI 지적의 정확도를 확인하는 데 쓰여요. |
| `coach.detail.close` | 리뷰 마치기 |
| `coach.detail.close.title` | 리뷰를 마칠까요? |
| `coach.detail.close.body` | 확인 전 항목이 {open}개 있어요. 마치면 지금 상태로 기록되고 더 이상 답변·힌트·상태를 바꿀 수 없어요.\n• 우려점에 이미 적은 항목 → 스스로 언급\n• 답변에서 문제를 짚은 항목 → 질문·힌트 후 발견\n• 그 외 → 놓침\n버그·위험 중 놓치거나 질문 후 찾은 항목은 복습 카드가 돼요. |
| `coach.detail.close.continue` | 계속 보기 |
| `coach.detail.close.confirm` | 마치기 |
| `coach.detail.closed` | 리뷰를 마쳤어요 · {date} |
| `coach.detail.discoveredSummary` | 스스로 언급 {mentioned} · 질문·힌트 후 발견 {found} · 놓침 {missed} |
| `coach.detail.menu.purge` | 원문 지금 삭제 |
| `coach.detail.purge.confirm` | 붙여넣은 코드를 지금 삭제할까요? 찾은 항목과 답변은 남아요. |
| `coach.detail.alreadyClosed` | 이미 마친 리뷰예요. |
| `coach.detail.toToday` | Today로 돌아가 완료하기 |
| `coach.detail.toEvidence` | 증거로 남기기 |

### 3.9 Plan · Goal

#### SCR-PLAN

- **목적**: 활성 계획의 milestone 타임라인과 마감 위험을 보고, 진행 상태·메모·순서를 바로 고친다(FR-04, FR-05). **진입**: 하단 탭·rail Plan, 온보딩 5단계 "계획 자세히". **Sprint**: S1 (budget 카드 S2).
- **레이아웃**

```text
┌────────────────────────────────┐
│ 계획                   버전 기록 │
│ Java 백엔드 성장 계획 · v3        │
│ 목표일 2027년 4월 1일  [목표 수정] │
│ ┌────────────────────────────┐ │  replanRecommended=true
│ │ ⓘ 목표 날짜가 바뀌었어요.       │ │
│ │   계획을 다시 맞춰 보세요.      │ │
│ │                  [계획 조정]  │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │  S2
│ │ 마감 위험 [빠듯함]              │ │
│ │ 4월 1일까지 가능  약 200시간    │ │
│ │ 필수 목표에 필요  약 240시간    │ │
│ │ 필요 ÷ 가능 120%                │ │
│ │ 최근 실제 완료율 70% 반영        │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │
│ │ 10월 1일 – 10월 31일    [필수] │ │
│ │ Java 기본기                    │ │
│ │ 상태 [진행 중 ▾]        [↑][↓] │ │
│ │ Collection · Exception · …    │ │
│ │ 메모: 주말에 Stream 복습  ✎    │ │
│ └────────────────────────────┘ │
│ ── 오늘 10월 13일 ──────────────  │
│ ┌────────────────────────────┐ │
│ │ 11월 1일 – 11월 30일    [필수] │ │
│ │ Spring · JPA   상태 [예정 ▾]   │ │
│ └────────────────────────────┘ │
│ …                                │
│ ┌────────────────────────────┐ │
│ │       계획 구조 바꾸기          │ │
│ └────────────────────────────┘ │
│ 진행 현황 보기 >                  │
│ 사이드 프로젝트 보기 >             │
├────────────────────────────────┤
│  Today   Review   Plan   More  │
└────────────────────────────────┘
```

- **타임라인**: 모바일은 milestone 카드 목록에 "오늘" 구분선을 날짜 위치에 끼워 넣는다. Tablet·Desktop은 목록 위에 가로 막대 타임라인(월 눈금, milestone 구간 막대 + 제목 라벨, 오늘 세로선, 목표일 표시 하나)을 추가한다. 날짜 표시는 목표일 하나뿐이다 — 마지막 milestone "설명과 정리"가 그 앞에서 끝난다. 막대의 priority는 막대 안 텍스트 라벨(`필수`/`권장`/`나중`)로 표시한다.
- **budget 카드 표시 규칙(S2)**: 분은 시간 단위로 반올림(`약 {h}시간`). `ratioBp=null`이면 "필요 ÷ 가능" 줄 대신 `plan.budget.noTime`. 완료율 줄은 `completionRateBp`를 쓴다. 기준일은 사용자가 등록한 목표일이다(`horizonDate` = `targetCompletionDate`, `06` §3.1). risk ≥ HIGH면 카드 아래 `plan.budget.tight` + "계획 조정"(보조 버튼), risk = LOW면 `plan.budget.roomy` + "계획 조정"(보조 텍스트 버튼), MEDIUM이면 "계획 조정" 텍스트 버튼만 둔다. 문구는 안내일 뿐이고, 실제 제안 여부는 SCR-REPLAN 미리보기가 정한다(`06` §4.4).
- **컴포넌트**: `PlanHeader`, `ReplanRecommendedBanner`, `BudgetRiskCard`, `PlanTimelineBar`(≥ 600), `MilestoneCard`(`MilestoneStatusDropdown`, `SortButtons`, `DescriptionEditor`), `TodayDivider`.
- **데이터**

| 시점 | API |
|---|---|
| 진입·탭 재선택 | `GET /plans/active` (+ S2 `GET /plans/active/budget`, 병렬) |
| 상태 변경 | `PATCH /plans/{planId}/milestones/{milestoneId}` `{status, version}` |
| 순서 ↑↓ | 인접 두 milestone에 `PATCH … {sortOrder, version}` 2회(순차). 두 번째 실패 시 재조회 |
| 메모 저장 | `PATCH … {description, version}` |

- **상태**: Loading — 헤더·카드 skeleton 3개. Empty — 활성 plan 없음(`404 PLAN_NOT_FOUND`) → `plan.empty` + "계획 만들기"(`POST /plans` → 재조회). Error·Offline — 공통. budget 조회만 실패 → budget 카드 자리에 인라인 오류 + "다시 시도"(나머지는 표시). AI — 해당 없음.
- **행동·검증**: 메모 0~2000자, 인라인 편집기(저장/취소). `409 CONCURRENT_MODIFICATION`·`PLAN_NOT_ACTIVE` → 재조회 + 토스트 `plan.reloaded`. 상태 드롭다운 선택 즉시 저장, 실패 시 이전 값으로 되돌림.
- **문구**

| key | 문구 |
|---|---|
| `plan.title` | 계획 |
| `plan.versions` | 버전 기록 |
| `plan.header` | {title} · v{version} |
| `plan.goal.completion` | 목표일 {date} |
| `plan.goal.edit` | 목표 수정 |
| `plan.replanRecommended` | 목표 날짜가 바뀌었어요. 계획을 다시 맞춰 보세요. |
| `plan.replan.button` | 계획 조정 |
| `plan.budget.risk` | 마감 위험 |
| `plan.budget.available` | {date}까지 가능 약 {hours}시간 |
| `plan.budget.required` | 필수 목표에 필요 약 {hours}시간 |
| `plan.budget.ratio` | 필요 ÷ 가능 {percent}% |
| `plan.budget.completionRate` | 최근 실제 완료율 {percent}% 반영 |
| `plan.budget.noTime` | 목표일까지 남은 학습 가능 시간이 없어요. 목표일이나 학습 시간을 확인해 주세요. |
| `plan.budget.tight` | 빠듯해요. 필수 위주로 줄이는 안을 볼 수 있어요. |
| `plan.budget.roomy` | 여유가 있어요. 더 깊이 공부하는 안을 볼 수 있어요. |
| `plan.today` | 오늘 {date} |
| `plan.milestone.status` | 상태 |
| `plan.milestone.memo` | 메모 |
| `plan.restructure` | 계획 구조 바꾸기 |
| `plan.dashboardLink` | 진행 현황 보기 |
| `plan.projectsLink` | 사이드 프로젝트 보기 |
| `plan.empty` | 활성 계획이 없어요. 템플릿으로 새 계획을 만들어요. |
| `plan.create` | 계획 만들기 |
| `plan.reloaded` | 다른 곳에서 계획이 바뀌어 최신 내용으로 다시 불러왔어요. |

#### SCR-REPLAN

- **목적**: milestone 추가·삭제·기간·우선순위·기술 구성을 바꿔 새 계획 버전을 만든다. S2부터는 저장 전에 마감 위험과 제안을 미리 보고 적용할 제안을 고른다 — 사용자가 등록한 목표일까지 **빠듯하면 필수 위주로 줄이는 안**(defer·목표 낮춤), **여유가 있으면 깊이를 더하는 안**(미뤄 둔 항목 복원·목표 올림)이다. 두 종류는 동시에 나오지 않는다(FR-04, FR-05, AC-03, AC-30). **진입**: SCR-PLAN "계획 구조 바꾸기"·"계획 조정", SCR-LEARNING-GOAL 저장 후(`?from=goal`). **Sprint**: S1(편집 → 저장), S2(편집 → 미리보기 → 저장).
- **레이아웃 ① 편집**

```text
┌────────────────────────────────┐
│ ← 계획 구조 바꾸기               │
│ ┌────────────────────────────┐ │  from=goal
│ │ ⓘ 바뀐 목표 날짜에 맞춰         │ │
│ │   기간을 조정해 보세요.          │ │
│ └────────────────────────────┘ │
│ 변경 이유 *                       │
│ [10월 야근으로 2주 지연          ] │
│ milestone 3개                     │
│ ┌────────────────────────────┐ │
│ │ 제목 [Spring · JPA          ]  │ │
│ │ 기간 [11월 1일] ~ [11월 30일]   │ │
│ │ 우선순위 [필수✓][권장][나중]    │ │
│ │ 기술 Spring MVC · Transaction   │ │
│ │      · JPA 매핑   [기술 선택]   │ │
│ │ 메모 [                       ] │ │
│ │ [↑] [↓]                [삭제]  │ │
│ └────────────────────────────┘ │
│ …                                │
│ + milestone 추가                  │
│ ┌────────────────────────────┐ │
│ │        변경 미리보기            │ │  S1: "새 버전으로 저장"
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

- **레이아웃 ② 미리보기 — 빠듯할 때: 축소 제안 (S2, 같은 라우트의 2단계 화면, risk ≥ HIGH)**

```text
┌────────────────────────────────┐
│ ← 미리보기                       │
│ 이대로 저장하면                   │
│ 마감 위험 [빠듯함]  (지금 [보통])  │
│ 가능 약 82시간 · 필수에 필요 약 98시간│
│ ─────────────────────────────  │
│ 빠듯해요 — 필수 위주로 줄이는 안   │
│ 미룰 수 있는 권장 항목             │
│ [✓] 시스템 설계 · 캐시             │
│     약 8시간 줄어요                 │
│ [ ] DevOps · Kubernetes 기초        │
│     약 15시간 줄어요                │
│ 필수 목표 낮추기                    │
│ [ ] Spring Security · 구현 4 → 3    │
│     약 6시간 줄어요                 │
│ 제안을 모두 적용하면 [보통]          │
│ ⓘ 제안은 고른 것만 적용돼요.        │
│ ┌────────────────────────────┐ │
│ │     새 버전(v4)으로 저장        │ │
│ └────────────────────────────┘ │
│ 편집으로 돌아가기                   │
└────────────────────────────────┘
```

- **레이아웃 ③ 미리보기 — 여유 있을 때: 확장 제안 (S2, risk = LOW이고 필요 ÷ 가능 ≤ 70%)**

```text
┌────────────────────────────────┐
│ ← 미리보기                       │
│ 이대로 저장하면                   │
│ 마감 위험 [여유]  (지금 [여유])    │
│ 가능 약 120시간 · 필수에 필요 약 80시간│
│ 필요 ÷ 가능 66%                    │
│ ─────────────────────────────  │
│ 여유가 있어요 — 더 깊이 하는 안     │
│ 미뤄 둔 항목 다시 넣기              │
│ [ ] 시스템 설계 · 캐시 [권장]       │
│     약 10시간 늘어요                │
│ 필수 목표 올리기                    │
│ [ ] Spring Transaction · 설명 3 → 4 │
│     약 8시간 늘어요                 │
│ 모두 받아들여도 마감 위험 [여유]     │
│ ⓘ 고른 것만 적용돼요. 다음 Today   │
│   계획부터 반영돼요.                │
│ ┌────────────────────────────┐ │
│ │     새 버전(v4)으로 저장        │ │
│ └────────────────────────────┘ │
│ 편집으로 돌아가기                   │
└────────────────────────────────┘
```

- **제안 영역 규칙** (`06` §4.4, `05` §7.7): 응답의 세 목록 중 무엇이 비어 있지 않은지로 영역을 고른다. 클라이언트는 risk·비율 임계값을 계산하지 않는다.

| 응답 | 보여줄 영역 |
|---|---|
| `mustTargetReductionSuggestions` 또는 `deferSuggestions`가 비어 있지 않음 | 레이아웃 ② 축소 제안 |
| `expansionSuggestions`가 비어 있지 않음 | 레이아웃 ③ 확장 제안 (`kind=RESTORE_DEFERRED` → "미뤄 둔 항목 다시 넣기", `RAISE_TARGET` → "필수 목표 올리기") |
| 셋 다 `[]` | `replan.preview.noSuggestions` |

  - 두 영역이 함께 나오는 경우는 없다(서버 보장). 함께 오면 축소 영역만 보여주고 오류 로그를 남긴다.
  - 확장 제안의 "늘어요" 시간은 `addedMinutes`(앞선 제안을 적용한 상태 기준)를 시간 단위로 반올림한다. "모두 받아들여도 {risk}"는 `riskAfterSuggestions`다 — 미뤄 둔 권장·나중 항목을 다시 넣어도 필수 기준의 마감 위험은 바뀌지 않고, 목표 올리기만 반영된다.
  - 첫 미리보기는 빈 선택 목록으로 호출해 제안 목록을 받고 화면에 고정한다. 체크를 바꾸면 "선택 반영해 다시 계산"으로 같은 편집안 + 선택 목록을 다시 preview해 `replan.preview.afterSelected`(서버가 선택을 적용해 계산, `05-api-spec.md` §7.7)를 보여준다. `riskAfterSuggestions`(모든 제안 적용 시)도 함께 보여준다. 클라이언트는 risk를 계산하지 않는다.
  - "미뤄 둔 기술"(S2): `PlanView.skillTargets` 중 `deferred=true` 목록을 체크박스로 보여주고, 체크한 skill은 `restoredDeferrals`로 보낸다(`06` §11.2). defer 제안과 같은 skill을 동시에 체크할 수 없다. 확장 제안(`RESTORE_DEFERRED`)에 나온 skill은 이 목록에서 빼고 확장 영역에만 한 번 보여준다(어느 쪽에서 체크해도 같은 `restoredDeferrals`다).
  - 확장 제안 `RAISE_TARGET`을 체크하면 `acceptedTargetRaises[]`에 `{skillCode, axis, newTarget}`(제안의 `newTarget`)로 넣는다. `RESTORE_DEFERRED`를 체크하면 `restoredDeferrals[]`에 `skill.code`를 넣는다.
- **컴포넌트**: `ReasonField`, `MilestoneEditorCard`(`DateRangeFields`, `PriorityChips`, `SkillMultiPicker`), `AddMilestoneButton`, `PreviewSummary`, `SuggestionCheckList`, `RiskBadge`.
- **데이터**

| 시점 | API |
|---|---|
| 진입 | `GET /plans/active` (편집 초기값, `version`), S2 `GET /plans/active/budget`(현재 risk), 기술 목록 `GET /skills/tree?role=JAVA_BACKEND` |
| "변경 미리보기"·"선택 반영해 다시 계산" (S2) | `POST /plans/{planId}/replan/preview` `{reason, version, milestones, acceptedDeferrals, acceptedTargetReductions, restoredDeferrals, acceptedTargetRaises}` — `Idempotency-Key` 보내지 않음 |
| "새 버전으로 저장" | `POST /plans/{planId}/replan` `{version, reason, milestones, acceptedDeferrals, acceptedTargetReductions, restoredDeferrals, acceptedTargetRaises}` |

  - `milestones[]` 항목: 기존 milestone은 `id` 포함, 새 항목은 `id: null`. `title`, `description`, `startDate`, `endDate`, `priority`, `status`(새 항목은 `PLANNED`), `sortOrder`(= 배열 index), `skillCodes`(≤ 30).
  - `acceptedTargetReductions[]`·`acceptedTargetRaises[]` 항목: `{skillCode, axis, newTarget}`(제안의 `newTarget`). S1은 네 목록 모두 `[]`.
- **상태**: Loading — 편집 카드 skeleton. 미리보기 계산 중 — 버튼 로딩(편집 잠금). 저장 중 — 버튼 로딩. Error — `VALIDATION_FAILED`는 해당 카드 필드 아래(제안 목록 필드 `acceptedTargetRaises[i]`·`restoredDeferrals[i]` 등의 `TARGET_NOT_RAISED`·`TARGET_NOT_REDUCED`·`MUTUALLY_EXCLUSIVE`는 그 제안 행 아래 인라인 + 미리보기를 다시 호출해 최신 제안으로 바꾼다), `CONCURRENT_MODIFICATION`·`PLAN_NOT_ACTIVE`는 대화상자 `replan.conflict`(확인 → 편집 내용을 버리고 `GET /plans/active` 재조회). Offline — 공통. AI — 해당 없음.
- **행동·검증**: §3.2(제목 1~200, 메모 ≤ 2000, 날짜 오늘−1년~+3년, `startDate ≤ endDate`, 1~24개, 기술 ≤ 30개, 저장 시 이유 1~1000자 필수). 기간 겹침은 허용한다. 삭제는 확인 없이 카드 제거 + 토스트 "되돌리기". 미리보기 후 편집으로 돌아가 내용을 바꾸면 선택한 제안을 초기화한다. 저장 성공 → `/plan` + 토스트 `replan.saved`. 입력 중 이탈 → §6.8.
- **문구**

| key | 문구 |
|---|---|
| `replan.title` | 계획 구조 바꾸기 |
| `replan.fromGoal` | 바뀐 목표 날짜에 맞춰 기간을 조정해 보세요. |
| `replan.reason` | 변경 이유 |
| `replan.count` | milestone {count}개 |
| `replan.field.title` | 제목 |
| `replan.field.period` | 기간 |
| `replan.field.priority` | 우선순위 |
| `replan.field.skills` | 기술 |
| `replan.field.skills.pick` | 기술 선택 |
| `replan.field.memo` | 메모 |
| `replan.delete` | 삭제 |
| `replan.deleted` | milestone을 삭제했어요. |
| `replan.add` | milestone 추가 |
| `replan.preview.button` | 변경 미리보기 |
| `replan.preview.title` | 미리보기 |
| `replan.preview.ifSaved` | 이대로 저장하면 |
| `replan.preview.currentRisk` | (지금 {risk}) |
| `replan.preview.budget` | 가능 약 {available}시간 · 필수에 필요 약 {required}시간 |
| `replan.preview.defer` | 미룰 수 있는 권장 항목 |
| `replan.preview.reduce` | 필수 목표 낮추기 |
| `replan.preview.reduceItem` | {skill} · {axis} {from} → {to} |
| `replan.preview.saved` | 약 {hours}시간 줄어요 |
| `replan.preview.afterAll` | 제안을 모두 적용하면 {risk} |
| `replan.preview.onlySelected` | 제안은 고른 것만 적용돼요. |
| `replan.preview.recalc` | 선택 반영해 다시 계산 |
| `replan.preview.afterSelected` | 선택 적용 시 {risk} |
| `replan.deferred.title` | 미뤄 둔 기술 |
| `replan.deferred.restore` | 다시 포함 |
| `replan.preview.noSuggestions` | 이대로도 기한 안에 가능해 보여요. 제안할 변경이 없어요. |
| `replan.preview.tight` | 빠듯해요 — 필수 위주로 줄이는 안 |
| `replan.preview.roomy` | 여유가 있어요 — 더 깊이 하는 안 |
| `replan.preview.restore` | 미뤄 둔 항목 다시 넣기 |
| `replan.preview.raise` | 필수 목표 올리기 |
| `replan.preview.raiseItem` | {skill} · {axis} {from} → {to} |
| `replan.preview.added` | 약 {hours}시간 늘어요 |
| `replan.preview.expandAfterAll` | 모두 받아들여도 마감 위험 {risk} |
| `replan.preview.expandNote` | 고른 것만 적용돼요. 다음 Today 계획부터 반영돼요. |
| `replan.save` | 새 버전(v{version})으로 저장 |
| `replan.backToEdit` | 편집으로 돌아가기 |
| `replan.saved` | 계획 v{version}을 저장했어요. |
| `replan.conflict.title` | 계획이 이미 바뀌었어요 |
| `replan.conflict.body` | 다른 기기나 탭에서 계획이 저장됐어요. 최신 계획을 불러온 뒤 다시 편집해 주세요. |
| `replan.conflict.reload` | 최신 계획 불러오기 |

#### SCR-LEARNING-GOAL

- **목적**: 학습 목표(무엇을·언제까지 — 학습 트랙과 목표일)와 집중 기술을 등록·수정한다(FR-03). **목표일은 사용자가 여기서 직접 정하는 날짜 하나**이고, DevPilot은 이 날짜로 남은 시간을 역산해 빠듯하면 필수 위주로, 여유 있으면 깊이 있게 안내한다(FR-05). **진입**: SCR-SETTINGS "목표"(주 진입점), SCR-PLAN "목표 수정". **Sprint**: S1.
- **레이아웃**: 학습 트랙(**읽기 전용** — 온보딩에서 고른 트랙 이름, 라벨 `onboarding.goal.role`, 아래에 `onboarding.goal.track.locked`) → 목표일(필수, 라벨 `onboarding.goal.completion`, SCR-ONBOARDING 1단계와 같은 빠른 선택 칩 `onboarding.goal.quick.*`과 날짜 선택기) → 집중 기술(칩 목록 + "기술 선택", 최대 10) → 안내 `goal.help` → primary "저장".
- **데이터**: 진입 `GET /learning-goal`. 저장 `PUT /learning-goal` `{targetRole, targetCompletionDate, focusSkillCodes, version}`.
- **상태**: Loading — 폼 skeleton. Error — `404 LEARNING_GOAL_NOT_FOUND`는 `ErrorView`(온보딩 이후 정상 흐름에서는 발생하지 않고, PUT은 목표를 새로 만들지 않는다). `409 CONCURRENT_MODIFICATION`은 최신 목표로 폼을 다시 채운다. 그 외 공통·§5. Offline — 공통.
- **행동·검증**: §3.2. 저장 성공 시 날짜가 바뀌었으면 대화상자 `goal.saved.datesChanged`("지금 조정" → `/plan/replan?from=goal`, "나중에" → `/plan`). 날짜가 그대로면 토스트 후 뒤로.
- **문구**

| key | 문구 |
|---|---|
| `goal.title` | 목표 |
| `goal.focus` | 집중 기술 (최대 10개) |
| `goal.help` | 목표일까지 남은 시간을 거꾸로 계산해요. 빠듯하면 필수 위주로, 여유가 있으면 더 깊이 안내해요. 날짜를 바꿔도 계획은 자동으로 바뀌지 않아요. 저장 후 계획 조정을 권해 드려요. |
| `goal.save` | 저장 |
| `goal.saved` | 목표를 저장했어요. |
| `goal.saved.datesChanged.title` | 목표 날짜가 바뀌었어요 |
| `goal.saved.datesChanged.body` | 남은 시간이 달라졌어요. 계획을 지금 맞춰 볼까요? |
| `goal.saved.datesChanged.now` | 지금 조정 |
| `goal.saved.datesChanged.later` | 나중에 |

#### SCR-PLAN-HISTORY

- **목적**: 계획 버전이 언제 왜 바뀌었는지 본다(FR-04, AC-01). **진입**: SCR-PLAN "버전 기록". **Sprint**: S1.
- **레이아웃**: 목록 행 — `v{n}` + 상태 라벨(`ACTIVE` 현재 · `SUPERSEDED` 이전 · `ARCHIVED` 보관), 생성일, 변경 이유(없으면 `plan.history.noReason`), milestone 수.
- **데이터**: `GET /plans?cursor=` + 스크롤 페이지네이션. 행 탭 → `/plan/versions/{planId}`.
- **상태**: Loading — 행 skeleton 3개. Empty — 발생하지 않음(온보딩 후 최소 v1). Error·Offline — 공통.
- **문구**: `plan.history.title` = "버전 기록", `plan.history.current` = "현재", `plan.history.previous` = "이전", `plan.history.noReason` = "이유를 적지 않았어요", `plan.history.milestones` = "milestone {count}개"

#### SCR-PLAN-VERSION

- **목적**: 특정 버전의 milestone과 skill 목표 조정 내역을 읽기 전용으로 본다(FR-04). **진입**: SCR-PLAN-HISTORY 행. **Sprint**: S1.
- **레이아웃**: 헤더(`v{n}`, 상태, 생성일, 대체일, 변경 이유) → milestone 목록(SCR-PLAN 카드와 같은 모양, 편집 컨트롤 없음) → "조정된 기술 목표" 목록: `adjustment ≠ ROLE_DEFAULT`인 `plan_skill_target`만(`DEFERRED` 미룸 · `TARGET_REDUCED` 목표 낮춤 · `USER_EDITED` 직접 수정).
- **데이터**: `GET /plans/{planId}`.
- **상태**: Loading — skeleton. Error — `404 PLAN_NOT_FOUND` → SCR-NOT-FOUND 표시. Offline — 공통.
- **문구**: `plan.version.readOnly` = "이전 버전은 읽기만 할 수 있어요.", `plan.version.adjusted` = "조정된 기술 목표", `plan.version.superseded` = "{date}에 새 버전으로 바뀜"

### 3.10 Skill

#### SCR-SKILL-TREE

- **목적**: 카테고리별 기술의 4축 레벨과 목표를 한눈에 보고 약한 곳을 찾는다(FR-06). **진입**: rail Skill, More > 기술. **Sprint**: S1.
- **레이아웃**

```text
┌────────────────────────────────┐
│ 기술                             │
│ [필수✓][권장][나중]  [목표 미달만]  │
│ ▾ Spring   필수 9개 중 목표 도달 1개│
│ ┌────────────────────────────┐ │
│ │ Spring Transaction     [필수]  │ │
│ │ 지식      ■■□□  2/4            │ │
│ │ 구현      ■□□□  1/4            │ │
│ │ 설명      ■■■□  3/4 자기평가   │ │
│ │ 문제 인지 □□□   0/3            │ │
│ └────────────────────────────┘ │
│ …                                │
│ ▸ Java     필수 8개 중 목표 도달 2개│
└────────────────────────────────┘
```

  - 막대 칸 수 = 목표 레벨, 채운 칸 = planning level. 오른쪽 텍스트 `{planning}/{target}`. planning > evidence(자기평가가 반영된 축)면 `자기평가` 텍스트 라벨.
  - 목표 미달 = 어느 축이든 planning < target. `deferred=true` 기술은 `미룸` 라벨을 붙이고 "목표 미달만" 필터에서 제외한다.
- **데이터**: 진입 `GET /skills/tree?role=JAVA_BACKEND` + `GET /skills/me`(병렬), 클라이언트에서 결합. 행 탭 → `/skills/{skillId}`.
- **상태**: Loading — 카테고리 헤더 skeleton 4개. Empty — 필터 결과 0 → `skill.tree.emptyFilter`. Error·Offline — 공통.
- **행동**: 필터 기본값 = 필수만 + 목표 미달만 꺼짐. 카테고리 펼침 상태는 `localStorage` `devpilot.skills.expanded`(try/catch).
- **문구**: `skill.tree.title` = "기술", `skill.tree.onlyGap` = "목표 미달만", `skill.tree.categorySummary` = "{priority} {total}개 중 목표 도달 {reached}개", `skill.tree.selfAssessed` = "자기평가", `skill.tree.deferred` = "미룸", `skill.tree.emptyFilter` = "조건에 맞는 기술이 없어요."

#### SCR-LESSON

경로 `/lessons/:lessonKey`. **가르치는 화면이다.** 지금까지 제품에는 이 자리가 없었고 문제·복습 카드·러버덕이 모두 "이미 안다"를 전제했다(`01` §4 *Teach before test*).

노트 하나는 **학습 단위** 3~6개다. 단위 하나가 5~15분짜리 한 바퀴이고, 화면은 그 한 바퀴를 **한 걸음씩** 보여 준다. 한 화면에 다 펼치지 않는다 — 답을 먼저 보게 되고, 평일 60분에 어디까지 했는지도 흐려진다.

**단위 한 바퀴 (걸음마다 화면 하나)**

| # | 걸음 | 보이는 것 | 다음으로 가는 조건 |
|---|---|---|---|
| 0 | 왜 배우나 | 노트의 `whyItMatters`, `oneLine`, 단위 목록(제목·분). **노트를 열면 처음 한 번만** | "시작" |
| 1 | 설명 | 단위의 `explain`(마크다운) | "예제 보기" |
| 2 | 예제 | `example.code`(코드 블록 — 복사 가능), `output`, `note` | "해 보기" |
| 3 | 예측 | `predict.question` + `code`. 선택형이면 버튼, 아니면 한 줄 입력 | 답을 내면 바로 채점 → 정답·해설 |
| 4 | 빈칸 | `complete.question` + 빈칸이 있는 코드. 빈칸마다 입력칸 | 답을 내면 바로 채점 → 정답·해설 |
| 5 | 문제 | `problem.prompt`, `deliverables`(번호 목록), `starterCode`가 채워진 답안칸 | "냈어요" |
| 6 | 견주기 | `modelAnswer`(코드 블록), `selfChecks` 체크박스, 내가 쓴 답(화면에만 있다 — 서버로 보내지 않는다) | "마쳤어요"를 누르면 도움 단계와 체크 개수를 보낸다 |
| 7 | 다음 걸음 | 노트의 `inProject`, 다음 단위 또는 "오늘은 여기까지" | — |

- **"이미 안다 → 문제부터"**: 0·1·2에서 항상 보이는 버튼. 누르면 5로 건너뛴다. 도움 없이 풀면 그 단위는 끝이다(`06` R-0 1번).
- **막혔을 때**(5번 화면): `힌트 보기`(1개씩, 콘텐츠의 `hints`) → `오리에게 설명하기`(러버덕) → `모범 답안 보기`. 이 순서대로 버튼이 하나씩 열린다. 옆에 안내 한 줄: "15분 넘게 막히면 도움을 쓰세요."
- **도움 단계는 앱이 센다.** 무엇을 열었는지가 `POST …/finish`의 `helpLevel`이다(`05` §21.7). 서버가 추적하지 않는다. 모범 답안은 `GET …/answer`로 가져온다.
- **`prerequisiteUnits`**: 5번 화면의 도움 목록 맨 위에 "먼저 볼 개념"으로 둔다. 아직 만들지 않은 단위를 가리키면 그 줄은 보이지 않는다.
- 3·4의 채점은 **즉시**다(AI 없음). 틀려도 다음으로 간다 — 해설을 보여 주고 그 단위를 복습 대상으로 둔다.
- 6번의 체크박스는 채점이 아니라 **스스로 견주기**다. 레벨을 올리지 않는다(`01` 원칙 4).

**들어오는 길**: SCR-SKILL-DETAIL의 "개념 익히기", SCR-TODAY 과제 카드의 "먼저 개념 익히기"(마친 과제에는 없다), SCR-RUBBER-DUCK ④의 "그래서 답이 뭔가요". 세 자리 모두 `GET /skills/{skillId}/lesson`(`05` §21.3)으로 노트를 찾고, **없으면 버튼을 그리지 않는다**.

**중간에 나가기**: 걸음은 로컬에 기억한다(어느 단위 몇 번째 걸음인지). 서버에 남는 것은 6번을 마친 단위뿐이다.

**접근성**: 걸음이 바뀌면 새 제목에 포커스를 준다(A-4). 코드 블록은 가로 스크롤(A-9, §2.4). 즉시 채점 결과는 live region.

**빈 상태**: 그 skill에 노트가 없으면 이 화면으로 오지 않는다 — 진입점이 보이지 않는다.


#### SCR-SKILL-DETAIL

- **목적**: 한 기술의 축별 레벨이 왜 그 값인지 근거 이력과 함께 보고, **반복 고리(학습 단계)에서 어디까지 왔는지** 확인하고, 관련 학습으로 이동한다(FR-06, AC-09). **진입**: SCR-SKILL-TREE 행, SCR-REQUIREMENT-DETAIL 항목. **Sprint**: S1(레벨), S3(이력·학습 단계).
- **레이아웃**: 헤더(기술 이름, 카테고리, priority, 설명, **`whyItMatters` 한 줄**) → **학습 단계 6칸**(S3) → 축 표(`축 | 증거 레벨 | 계획용 레벨 | 목표`, 레벨은 숫자 + `SkillLevel` 라벨) → 자기평가 줄(`self_assessed_level`, `self_assessment_active=false`면 `skill.detail.selfInactive`) → 버튼 행 "복습 카드"(`/review/items?skillId=`), "문제 풀기"(`/training?skillId=`, S3), "개념 설명하기"(S3, `rubber_duck` flag, AI 가능할 때 — `/rubber-duck/new?targetType=CONCEPT&conceptKey={skill.code}&skillCode={skill.code}`, 러버덕 `CONCEPT` 대상) → "레벨 변경 기록"(S3) 목록: `{날짜} · {축} {from} → {to}` + 규칙 문구 + `근거 기록 {n}개`(펼치면 `evidenceEvents`의 유형·날짜 목록. S6부터 `CHALLENGE_EVALUATED`·`COACH_FINDING_CLOSED`·`COACH_REVIEW_COMPLETED` 행에 "증거 초안 만들기" → `POST /evidence/drafts` `{sourceLearningEventId}` → `/evidence/{id}`).

```text
┌────────────────────────────────┐
│ ← Spring Transaction     [필수] │
│ Spring · 선언적 트랜잭션 경계    │
│ 왜 중요한가 — 경계를 모르면 어디서│  whyItMatters (있을 때)
│ 데이터가 어긋나는지 설명할 수 없다│
│ ────────────────────────────── │
│ 학습 단계  4 / 6                 │
│ ┌──┬──┬──┬──┬──┬──┐           │
│ │■ │■ │■ │■ │□ │□ │           │
│ └──┴──┴──┴──┴──┴──┘           │
│ 만들기 개념 코드 설명 복습 재현   │
│ 다음: 복습 — 이 기술의 복습 카드에│  첫 미완료 단계 1줄
│ 한 번 답하면 채워져요.            │
│ ▸ 단계별로 보기                  │
└────────────────────────────────┘
```

- **학습 단계 6칸 (S3)**
  - `SkillDetailView.learningStages`(`05` §6.4)를 그대로 그린다. **항상 6칸이고 순서는 응답 순서**(`LearningStage` 선언 순서: 만들기 → 개념 읽기 → 코드 읽기 → 설명하기 → 복습 → AI 없이 재현)다. 클라이언트가 정렬하거나 채워 넣지 않는다.
  - 칸 상태는 **완료 / 진행 / 미시작** 셋이다. `completed = true`면 **완료**(채운 칸 + 아래 `completedAt`의 날짜를 tooltip·semantics로). `completed = false`인 칸 중 **가장 앞의 한 칸**이 **진행**(테두리만 있는 칸 + 아래 "다음" 줄), 그 뒤는 **미시작**(빈 칸)이다. 6칸이 모두 완료면 진행 칸이 없고 `skill.detail.stage.allDone`을 한 줄 보인다.
  - **순서는 표시 순서이고 선행 조건이 아니다**(`06` §5.11 ST-2). 앞 칸이 비어 있어도 뒤 칸이 완료일 수 있으므로, "진행"은 잠금이 아니라 **다음에 하면 좋은 한 가지**를 가리키는 표시다. 잠금 아이콘을 쓰지 않는다.
  - 칸 아래에 단계 라벨(§3.1 `LearningStage`)을 항상 텍스트로 둔다(A-3). 폭 360에서 6칸이 좁으므로 `textScaler > 1.3`이면 칸을 2줄(3+3)로 접는다(A-10).
  - "단계별로 보기"를 펼치면 6행 목록으로 바꿔 `단계 · 상태 · 채운 날짜`를 보여주고, 미완료 단계에는 그 단계를 채우는 방법 한 줄(`skill.detail.stage.how.<STAGE>`)과 갈 곳을 둔다 — 만들기·재현은 Today에서 나오는 과제라 버튼 없이 안내만, 개념 읽기·코드 읽기도 안내만, 설명하기는 "개념 설명하기"(러버덕), 복습은 "복습 카드"다.
  - **저장하지 않는 파생 값이다**(ADR-042) — 이 화면에서 단계를 직접 체크하거나 되돌릴 수 없다. 관련 학습을 하면 다음 조회에서 채워진다는 것을 `skill.detail.stage.note`로 알린다.
- **`whyItMatters` 줄**: `SkillDetailView.whyItMatters`를 헤더 설명 아래 한 줄로 둔다. `null`이면 숨긴다. SCR-TODAY 과제 카드의 같은 이름 줄과 같은 콘텐츠 값이다(`05` §6.4·§8.1).
- **데이터**: `GET /skills/{skillId}`(S3 — `whyItMatters`·`learningStages` 포함), `GET /skills/tree?role={트랙}`·`GET /skills/me`(캐시 재사용, 축 표·목표), S3 `GET /skills/{skillId}/history?cursor=` + 페이지네이션. S3 이전 빌드는 `GET /skills/{skillId}`를 부르지 않고 단계 영역과 `whyItMatters`를 숨긴다.
- **상태**: Loading — 표 skeleton + 단계 6칸 skeleton. 이력 Empty — `skill.detail.historyEmpty`. 단계 6칸은 Empty가 없다(전부 미완료여도 6칸을 그대로 보인다). Error·Offline — 공통(이력만 실패하면 이력 영역만, 단계 조회만 실패하면 단계 영역만 인라인 오류 + "다시 시도"). `404 RESOURCE_NOT_FOUND` → SCR-NOT-FOUND. AI — 이 화면은 AI를 쓰지 않는다. 배너는 두지 않고 "개념 설명하기"만 막는다(§6.5).
- **규칙 문구** (`skill.rule.<rule_code>`, `06` §7.2~7.4)

| rule_code | 문구 |
|---|---|
| `K1_ANY_EVENT` | 이 기술의 학습 기록이 생겼어요 |
| `K2_RECALL_GUIDED` | 방향 힌트 이하로 복습을 2번 이상 잘 떠올렸어요 |
| `K3_RECALL_INDEPENDENT` | 힌트 없이 서로 다른 날 복습을 3번 이상 잘 떠올렸어요 |
| `K4_RECALL_LONG` | 14일 이상 간격의 복습을 힌트 없이 2번 떠올렸어요 |
| `K5_TRANSFER` | L5 문제를 스스로 해결했어요 |
| `I1_ATTEMPTED` | 문제 답안을 제출했어요 |
| `I2_SOLVED_GUIDED` | 힌트를 활용해 문제를 해결했어요 |
| `I3_SOLVED_INDEPENDENT` | L2 이상 문제를 서로 다른 2종 이상, 스스로 3번 해결했어요 |
| `I4_PRODUCTION_LIKE` | L4 이상 문제를 해결하고 승인된 증거가 있어요 |
| `I5_TRANSFER` | L5 문제 2종을 스스로 해결했어요(전이 문제 포함) |
| `E1_ANY_EXPLANATION` | 접근 방법을 설명했어요 |
| `E2_PARTIAL` | 설명 기준을 40% 이상 충족했어요 |
| `E3_COVERAGE` | 힌트 없이 설명 기준을 70% 이상 2번 충족했어요 |
| `E4_TRANSFER_QUESTION` | 설명 기준 80% 이상, 변형 문제도 맞혔어요 |
| `E5_TRADEOFF` | L5 문제에서 설명 기준을 80% 이상 충족했어요 |
| `D1_ANY_REVIEW` | 코드 리뷰를 마쳤어요 |
| `D2_FOUND_WITH_HINT` | 방향 힌트 이하로 코드 문제를 짚었어요 |
| `D3_FOUND_UNPROMPTED` | 버그·위험을 스스로 2번 언급했어요 |
| `D4_REPEATED_UNPROMPTED` | 여러 리뷰에서 버그·위험을 스스로 3번 이상 언급했어요 |
| `D5_TRANSFER` | 3가지 이상 관점에서 문제를 스스로 언급했어요 |
| `K_DOWN_RECALL_FAIL` | 최근 복습 2번이 연속으로 '다시'였어요 |
| `I_DOWN_TRANSFER_FAIL` | 전이 문제를 해결하지 못했어요 |
| `D_DOWN_REPEATED_MISS` | 같은 관점을 최근 3번 연속 놓쳤어요 |
| `DIAG_PASSED` | 실력 확인 문제를 통과했어요 |
| (그 외) | 규칙 {code} |

- **학습 단계 문구**

| key | 문구 |
|---|---|
| `skill.detail.whyItMatters` | 왜 중요한가 |
| `skill.detail.stage.title` | 학습 단계 |
| `skill.detail.stage.count` | {done} / 6 |
| `skill.detail.stage.state.completed` | 완료 |
| `skill.detail.stage.state.current` | 진행 |
| `skill.detail.stage.state.todo` | 미시작 |
| `skill.detail.stage.next` | 다음: {stage} |
| `skill.detail.stage.allDone` | 여섯 단계를 한 바퀴 돌았어요. |
| `skill.detail.stage.expand` | 단계별로 보기 |
| `skill.detail.stage.completedAt` | {date}에 채웠어요 |
| `skill.detail.stage.note` | 단계는 따로 체크하지 않아요. 과제·러버덕·복습 기록이 쌓이면 채워져요. |
| `skill.detail.stage.how.BUILD` | 이 기술의 문제 풀이나 프로젝트 과제를 하나 마치면 채워져요. |
| `skill.detail.stage.how.READ_CONCEPT` | 이 기술의 개념 읽기 과제를 마치면 채워져요. |
| `skill.detail.stage.how.READ_CODE` | 이 기술의 코드 읽기 과제를 마치면 채워져요. |
| `skill.detail.stage.how.EXPLAIN` | 러버덕으로 설명하거나, 설명하기 과제에서 '다른 사람에게 설명했어요'를 고르면 채워져요. |
| `skill.detail.stage.how.REVIEW` | 이 기술의 복습 카드에 한 번 답하면 채워져요. |
| `skill.detail.stage.how.REDO` | 재현 과제를 AI 도움 없이 마치면 채워져요. |

- **문구**: `skill.detail.axis` = "축", `skill.detail.evidence` = "증거 레벨", `skill.detail.planning` = "계획용 레벨", `skill.detail.target` = "목표", `skill.detail.self` = "자기평가 {level}", `skill.detail.selfInactive` = "최근 기록을 반영해 자기평가는 더 이상 쓰지 않아요", `skill.detail.reviewCards` = "복습 카드", `skill.detail.practice` = "문제 풀기", `skill.detail.explain` = "개념 설명하기", `skill.detail.history` = "레벨 변경 기록", `skill.detail.historyItem` = "{axis} {from} → {to}", `skill.detail.evidenceCount` = "근거 기록 {count}개", `skill.detail.historyEmpty` = "아직 레벨 변경 기록이 없어요. 복습과 문제 풀이를 하면 여기에 쌓여요.", `skill.detail.makeDraft` = "증거 초안 만들기"

### 3.11 Dashboard · Weekly

#### SCR-DASHBOARD

- **목적**: 오늘 상태와 이번 주 학습량을 보고(S2), 마감 위험 추세·milestone 위치·분야별 수준·자주 놓치는 관점을 확인한다(S5) (FR-16). 스스로 해결률·평균 힌트·복습 기억 같은 학습 방식 지표는 SCR-WEEKLY-DETAIL에 있다. **진입**: SCR-TODAY "진행 현황", SCR-PLAN, SCR-MORE. **Sprint**: S2(최소), S5(완성).
- **레이아웃**

```text
┌────────────────────────────────┐
│ ← 진행 현황                       │
│ ┌────────────────────────────┐ │  replanRecommended=true
│ │ ⓘ 목표 날짜가 바뀌었어요.       │ │
│ │                  [계획 조정]  │ │
│ └────────────────────────────┘ │
│ 오늘                              │
│ ┌────────────────────────────┐ │
│ │ [설명하기] Spring Transaction   │ │
│ │ 내 말로 설명하기                │ │
│ │ 진행 중 · 약 15분 · 복습 6장 남음 │ │
│ └────────────────────────────┘ │
│ 이번 주 (10월 12일 월요일부터)      │
│ 이번 주에 만든 것                   │  ← 시간보다 먼저
│ • [문제 풀이] 트랜잭션 전파 수정하기 │
│ • [프로젝트 과제] 주문 취소 흐름     │
│ • [AI 없이 재현] 설정 로더 다시 만들기│
│ 끝낸 과제 7개 · 적은 기록 2개        │
│ 공부한 시간 3시간 10분 (4회)         │
│ 이어서 학습한 날 5일                 │  streakDays ≥ 1일 때만
│ ── S5 ─────────────────────────  │
│ 마감 위험 (최근 기록 8개)           │
│ 매우 빠듯함 ┤                       │
│ 빠듯함     ┤             ┌─●      │
│ 보통       ┤ ●──●──●──●──┘        │
│ 여유       ┤                       │
│ 지금 [빠듯함] · 필요 ÷ 가능 120%     │
│ 계획 v3                            │
│ ━[Java 기본기]━┃━[Spring·JPA]━━━    │
│        오늘 10/13      목표일 4/1   │
│ 분야별 수준 (평균 / 목표)            │
│ Java     ■■■■□□□  2.1 / 3.4        │
│ Spring   ■■□□□□□  1.2 / 3.8        │
│ 자주 놓치는 관점 (최근 28일)         │
│ 1 자원 수명 · 2 동시성 · 3 관측성     │
│ 주간 리뷰 보기 >                     │
└────────────────────────────────┘
```

- **이번 주 요약 (S3)**: `weeklySummary`(`05` §13.1)를 응답의 필드 순서대로 그린다 — **`builtThisWeek`(만든 것) → `completedTasks`(끝낸 것) → `notesWritten`(적은 것) → `studyMinutes`(시간)**. 결과물을 공부 시간보다 **앞에** 두는 것이 이 화면의 규칙이다. 시간은 노력이지 결과가 아니다.
  - `builtThisWeek`는 이번 주에 마친 문제 풀이·프로젝트 과제·재현 과제 최대 5개다. 행마다 `TaskType` 배지 + 제목 + `planDate`를 보이고, 목록을 누르면 아무 데도 가지 않는다(과제 상세 화면이 없다 — 읽는 목록이다).
  - `builtThisWeek`가 비면 목록 대신 `dashboard.built.none` 한 줄을 둔다. "아직 없어요"라는 사실만 쓰고 재촉하지 않는다(§9).
  - `completedTasks`·`notesWritten`은 한 줄에 함께, `studyMinutes`는 `weekCompletedSessions`와 함께 그 아래 한 줄이다. `studyMinutes`는 `weekStudyMinutes`와 같은 값이라 두 번 표시하지 않는다.
  - 기준은 **이번 주 월요일부터 오늘까지**다(`05` §13.1). 머리줄에 `dashboard.week`로 그 날짜를 밝힌다.
- **연속 학습 일수 (S3)**: `streakDays`를 이번 주 요약 마지막 줄에 사실로 한 줄 둔다(`dashboard.streak`). **U-3의 예외이고 조건이 붙는다.**
  - `streakDays = 0`이면 **줄 자체를 숨긴다.** "0일", "끊겼어요", "며칠째 쉬고 있어요"를 쓰지 않는다.
  - 목표 일수·연속 기록 표시(불꽃·배지·게이지)·최고 기록·비교를 두지 않는다. 숫자 하나와 라벨뿐이다.
  - 재촉 문구("오늘도 이어 가세요")를 붙이지 않는다. 오늘 아직 완료가 없어도 어제까지의 수를 그대로 보인다(`05` §13.1 계산).
  - 이 줄은 SCR-DASHBOARD에만 둔다. SCR-TODAY·SCR-REVIEW-\*·SCR-PLAN에는 두지 않는다 — 매일 보는 화면에 연속 숫자를 두면 압박이 된다.
- **차트 규칙**: `fl_chart` 사용. 위험 추세는 `risk.trend`(최대 8점, `snapshotDate` ASC)를 계단형 선(단색 `color.textPrimary`)으로 그리고 y축은 4단계 텍스트 라벨이다. milestone 타임라인은 `milestoneTimeline.milestones` 구간 막대 + 막대 안 제목·priority 텍스트, 오늘(`todayMarker`) 세로선, 목표일(`horizonDate` = `targetCompletionDate`) 표시 하나다. 분야별 수준은 `skillCategories`의 `avgPlanningLevelMilli`·`avgTargetLevelMilli`를 `milli / 1000` 소수 1자리로 보여주는 가로 막대(채움 = planning, 숫자 = 목표)다. 모든 차트는 같은 내용을 문장으로 함께 제공하고 `Semantics(label:)`에 넣는다.
- **데이터**: 진입·화면 복귀 시 `GET /dashboard` 1회. S3 이전 빌드는 `streakDays = 0`, `weeklySummary = null`로 보고 두 영역을 숨긴다. S5 이전에는 `risk`, `milestoneTimeline`이 `null`, `skillCategories`, `weakThinkingAxes`가 `[]`이며 해당 섹션을 숨긴다.
- **상태**
  - Loading: 섹션별 skeleton.
  - `todaySummary.generated=false`: 오늘 카드에 `dashboard.today.none` + "Today로".
  - `generated=true`이고 `mainTaskId=null`: `today.noCandidate` + "계획 조정".
  - `risk=null`, `milestoneTimeline=null`: 해당 섹션 숨김. `weakThinkingAxes=[]`: `dashboard.weakAxes.none`.
  - `replanRecommended=true`: 상단 안내(레이아웃 참고) → `/plan/replan?from=goal`.
  - Error·Offline: 공통. AI 상태는 표시하지 않는다(AI 진입 화면이 아니다).
- **표시 금지**: 쉰 날 수, "놓친" 과제 수, 목표 대비 부족 퍼센트, 연속 기록이 끊겼다는 표시(U-3). 연속 학습 일수는 위 조건을 지킬 때만 사실로 보인다.
- **문구**: `dashboard.title` = "진행 현황", `dashboard.replanRecommended` = "목표 날짜가 바뀌었어요.", `dashboard.today` = "오늘", `dashboard.today.none` = "아직 오늘 계획을 만들지 않았어요.", `dashboard.today.reviewLeft` = "복습 {count}장 남음", `dashboard.week` = "이번 주 ({date} 월요일부터)", `dashboard.built` = "이번 주에 만든 것", `dashboard.built.none` = "이번 주에 마친 과제가 아직 없어요.", `dashboard.week.tasks` = "끝낸 과제 {tasks}개 · 적은 기록 {notes}개", `dashboard.week.summary` = "공부한 시간 {duration} ({sessions}회)", `dashboard.streak` = "이어서 학습한 날 {days}일", `dashboard.risk` = "마감 위험 (최근 기록 {count}개)", `dashboard.risk.now` = "지금 {risk} · 필요 ÷ 가능 {percent}%", `dashboard.plan` = "계획 v{version}", `dashboard.plan.today` = "오늘 {date}", `dashboard.plan.horizon` = "목표일 {date}", `dashboard.skills` = "분야별 수준 (평균 / 목표)", `dashboard.skills.value` = "{planning} / {target}", `dashboard.weakAxes` = "자주 놓치는 관점 (최근 28일)", `dashboard.weakAxes.none` = "관찰 기록이 더 쌓이면 보여 드려요.", `dashboard.weeklyLink` = "주간 리뷰 보기"

#### SCR-WEEKLY-LIST

- **목적**: 지난 주간 리뷰를 고른다(FR-17). **진입**: SCR-DASHBOARD "주간 리뷰 보기", SCR-MORE. **Sprint**: S5.
- **레이아웃**: 행 — `{M월 d일} 주`, `{sessions}회 · {duration} · 복습 기억 {percent}%`, 회고 작성 여부 라벨(`회고 있음`/`회고 전`).
- **데이터**: `GET /weekly-reviews?cursor=` + 페이지네이션. 행 탭 → `/weekly/{weekStartDate}`.
- **상태**: Loading — 행 skeleton. Empty — `weekly.list.empty`. Error·Offline — 공통.
- **문구**: `weekly.list.title` = "주간 리뷰", `weekly.list.week` = "{date} 주", `weekly.list.hasReflection` = "회고 있음", `weekly.list.noReflection` = "회고 전", `weekly.list.empty` = "첫 주간 리뷰는 다음 월요일에 만들어져요."

#### SCR-WEEKLY-DETAIL

- **목적**: 한 주의 지표와 관점 추세를 보고 회고를 쓴다(FR-17, FR-13). **진입**: SCR-WEEKLY-LIST 행. **Sprint**: S5.
- **레이아웃**: 헤더 `{date} 주` → 지표 타일 2열 그리드(완료 세션, 학습 시간, 스스로 해결, 평균 힌트, 복습 기억, 스스로 찾은 위험, 승인된 증거, 주말 마감 위험) → 자주 놓치는 관점(최대 3) → "관점 추세 (8주)": 축마다 한 행, 8주 누적 막대(주별 `mentionedUnprompted`·`foundAfterHint`·`missed` 개수. 구간 색은 success·info·neutral 톤이고 막대 옆에 `스스로 {n} · 발견 {n} · 놓침 {n}` 텍스트를 함께 표시), 표 보기 토글(축 × 주 × 발견 방식 숫자 표) → 회고 입력(`weekly.reflection.hint`, ≤ 5000자) + "저장".
- **데이터**: `GET /weekly-reviews/{weekStartDate}`, `GET /thinking-patterns/trend?weeks=8`(병렬). 저장 `PUT /weekly-reviews/{weekStartDate}/reflection` `{reflection, version}`.
- **상태**: Loading — 타일 skeleton 8개. 추세 조회 실패 → 추세 영역만 인라인 오류. `404 RESOURCE_NOT_FOUND` → SCR-NOT-FOUND. Error·Offline — 공통. 저장 `409 CONCURRENT_MODIFICATION` → 서버 회고를 불러와 입력 아래에 "다른 곳에서 저장된 회고" 비교 표시 + "내 내용으로 덮어쓰기"(새 version으로 재전송) / "불러온 내용 사용".
- **문구**: `weekly.detail.title` = "{date} 주", `weekly.metric.sessions` = "완료 세션", `weekly.metric.minutes` = "학습 시간", `weekly.metric.independent` = "스스로 해결", `weekly.metric.hint` = "평균 힌트", `weekly.metric.recall` = "복습 기억", `weekly.metric.selfFound` = "스스로 찾은 위험", `weekly.metric.evidence` = "승인된 증거", `weekly.metric.risk` = "주말 마감 위험", `weekly.trend.title` = "관점 추세 (8주)", `weekly.trend.table` = "표로 보기", `weekly.reflection.title` = "회고", `weekly.reflection.hint` = "이번 주 잘된 것, 막힌 것, 다음 주에 바꿀 것 하나", `weekly.reflection.save` = "저장", `weekly.reflection.saved` = "회고를 저장했어요.", `weekly.reflection.conflict` = "다른 곳에서 저장된 회고가 있어요.", `weekly.reflection.overwrite` = "내 내용으로 덮어쓰기", `weekly.reflection.useServer` = "불러온 내용 사용"

### 3.12 Evidence

#### SCR-EVIDENCE-LIST

- **목적**: 증거 후보·승인·거절 목록을 보고, 직접 작성하거나 승인된 증거를 Markdown으로 내보낸다(FR-18). **진입**: rail Evidence, More > 증거. **Sprint**: S6.
- **레이아웃**: 앱 바 "증거" + 메뉴 "Markdown 내보내기" → 상태 `SegmentedButton`(후보·승인·거절, 기본 후보) → 목록 행: 제목(없으면 `evidence.untitled`), 관련 기술, 생성일, `AI 초안` 배지(`aiDraft` 있음), 초안 생성 상태(`PENDING`/`RUNNING` 작성 중 · `FAILED` 초안 실패) → 하단 primary "직접 작성"(→ `/evidence/new`).
- **데이터**: `GET /evidence?status=&cursor=` + 페이지네이션. "Markdown 내보내기" → `GET /evidence/export?format=markdown` → 파일 저장 `devpilot-evidence-{yyyyMMdd}.md`(브라우저 다운로드) + 대화상자에 "복사" 버튼.
- **상태**: Loading — 행 skeleton. Empty — `evidence.list.empty.{status}` + (후보) "직접 작성". 승인 0건에서 내보내기 → 토스트 `evidence.export.none`. Error·Offline — 공통. AI — 목록은 영향 없음.
- **문구**: `evidence.list.title` = "증거", `evidence.untitled` = "제목 없음", `evidence.aiDraft` = "AI 초안", `evidence.drafting` = "초안 작성 중", `evidence.draftFailed` = "초안 실패", `evidence.new` = "직접 작성", `evidence.export` = "Markdown 내보내기", `evidence.export.done` = "파일로 저장했어요.", `evidence.export.copy` = "복사", `evidence.export.none` = "승인한 증거가 아직 없어요.", `evidence.list.empty.CANDIDATE` = "증거 후보가 없어요. 문제를 해결하거나 코드 리뷰를 마친 뒤 '증거로 남기기'를 누르거나 직접 작성해 보세요.", `evidence.list.empty.ACCEPTED` = "승인한 증거가 없어요.", `evidence.list.empty.REJECTED` = "거절한 증거가 없어요."

#### SCR-EVIDENCE-DETAIL

- **목적**: 증거를 STAR 형식으로 작성·편집하고 승인하거나 거절한다. AI 초안 원본과 내 편집본을 구분해 본다(FR-18, AC-21). **진입**: SCR-EVIDENCE-LIST 행·"직접 작성", SCR-TRAINING-ATTEMPT·SCR-COACH-DETAIL "증거로 남기기"(`evidenceSourceEventId`로 AI 초안, 없으면 수동 작성), SCR-SKILL-DETAIL "증거 초안 만들기". **Sprint**: S6.
- **레이아웃**: 상태 라벨 → (초안이 있으면) `AI 초안 보기` 토글(원본을 읽기 전용 회색 박스로 펼침, `aiDraft.promptVersion` 작게 표시) → 제목 → 관련 기술(선택) → 상황·문제 / 분석 / 행동 / 결과(각 여러 줄, 글자 수 표시) → 참고 링크(https, 최대 10, 추가·삭제) → 설명 주제(칩, 최대 10) → 하단 버튼: `CANDIDATE` = "저장"(보조) + "승인"(primary) + 메뉴 "거절" / `ACCEPTED` = "저장"(primary, 승인 상태 유지) / `REJECTED` = 읽기 전용 + 안내 + "후보로 되돌리기".
- **데이터**

| 시점 | API |
|---|---|
| 진입 (`:evidenceId`) | `GET /evidence/{evidenceId}` |
| `generationStatus ∈ {PENDING, RUNNING}` | 같은 API 2초 polling, 최대 3분 |
| 생성 (`/evidence/new`) "저장" | `POST /evidence` `{skillCode, title, problem, analysis, action, result, referenceLinks, explanationTopics}` → `201` → `/evidence/{id}`로 replace. `extra`로 받은 제목·기술을 초기값으로 채운다 |
| "저장" | `PATCH /evidence/{evidenceId}` `{title, problem, analysis, action, result, referenceLinks, explanationTopics, version}` (기술은 생성 후 바꾸지 않는다) |
| "승인" | (변경이 있으면 먼저 PATCH) → `POST /evidence/{evidenceId}/accept` |
| "거절" | `POST /evidence/{evidenceId}/reject` |
| "후보로 되돌리기" | `PATCH /evidence/{evidenceId}` `{status: CANDIDATE, version}` |
| 초안 실패 "다시 만들기" | `POST /evidence/drafts` `{sourceLearningEventId}` (같은 source) → 새 evidence로 이동 |

- **상태**: Loading — 폼 skeleton. Async pending — 폼 대신 `AsyncStatusIndicator` + `evidence.detail.drafting`. 초안 `FAILED` — `failureCode` 문구 + "다시 만들기" + "직접 작성으로 계속"(빈 편집 필드 활성). AI unavailable — 진행 중 초안이 없으면 영향 없음(직접 작성 가능). Error — 저장 실패 시 입력 유지 + §5. `REJECTED`는 "후보로 되돌리기" 전까지 읽기 전용. Offline — 공통.
- **행동·검증**: §3.2. "승인" 활성 조건: 제목·상황·행동·결과가 비어 있지 않음. 승인 확인 대화상자 `evidence.accept.confirm`. 거절 확인 대화상자 `evidence.reject.confirm`. 입력 중 이탈 → §6.8.
- **문구**: `evidence.detail.titleNew` = "증거 작성", `evidence.detail.title` = "증거", `evidence.detail.showAiDraft` = "AI 초안 보기", `evidence.detail.aiDraftNote` = "AI가 만든 원본이에요. 편집해도 원본은 바뀌지 않아요.", `evidence.field.title` = "제목", `evidence.field.skill` = "관련 기술 (선택)", `evidence.field.problem` = "상황·문제", `evidence.field.problem.hint` = "어떤 상황에서 어떤 문제가 있었나요?", `evidence.field.analysis` = "분석", `evidence.field.analysis.hint` = "원인을 어떻게 찾고 무엇을 판단했나요?", `evidence.field.action` = "행동", `evidence.field.action.hint` = "무엇을 바꿨나요? 왜 그 방법을 골랐나요?", `evidence.field.result` = "결과", `evidence.field.result.hint` = "무엇이 나아졌나요? 확인한 방법은?", `evidence.field.links` = "참고 링크 (커밋, PR 등)", `evidence.field.topics` = "설명 주제", `evidence.save` = "저장", `evidence.accept` = "승인", `evidence.reject` = "거절", `evidence.accept.validation.incomplete` = "제목, 상황·문제, 행동, 결과를 채우면 승인할 수 있어요", `evidence.accept.confirm` = "승인하면 내보내기에 포함되고 되돌릴 수 없어요. 승인 후에도 내용은 고칠 수 있어요.", `evidence.reject.confirm` = "이 후보를 거절할까요?", `evidence.detail.rejected` = "거절한 증거예요.", `evidence.detail.drafting` = "AI가 초안을 쓰고 있어요.", `evidence.detail.retryDraft` = "다시 만들기", `evidence.detail.writeManually` = "직접 작성으로 계속", `evidence.accepted` = "승인했어요.", `evidence.restore` = "후보로 되돌리기"

### 3.13 로드맵 비교 (Roadmap compare)

#### SCR-REQUIREMENTS-LIST

- **목적**: 비교한 로드맵 목록을 보고 새 로드맵·기술 목록을 붙여넣어 비교한다(FR-19). 붙여넣는 것은 공개된 학습 로드맵이나 기술 목록이다. 항목마다 지금 레벨 기준으로 준비됨·도전·나중에로 나눈다. **진입**: rail Radar, More > 로드맵 비교. **Sprint**: S7.
- **레이아웃**: 앱 바 "로드맵 비교" → primary "로드맵 비교하기" → 목록 행: 제목, 분석일, 상태(분석 중/완료/실패), 완료 시 `항목 {requirementCount}개`. 행 스와이프·메뉴 "삭제".
- **데이터**: `GET /requirement-docs?cursor=` + 페이지네이션. 삭제 → 확인 대화상자 → `DELETE /requirement-docs/{requirementDocId}`.
- **상태**: Loading — 행 skeleton. Empty — `radar.list.empty` + "로드맵 비교하기". AI unavailable — 배너, 분석 버튼 비활성. Budget warning — 버튼 아래 경고. Error·Offline — 공통.
- **문구**: `radar.list.title` = "로드맵 비교", `radar.list.new` = "로드맵 비교하기", `radar.list.counts` = "항목 {count}개", `radar.list.delete` = "삭제", `radar.list.delete.confirm` = "이 로드맵과 비교 결과를 삭제할까요?", `radar.list.empty` = "비교한 로드맵이 없어요. 공개 학습 로드맵이나 기술 목록을 붙여넣으면 항목별 준비 상태를 정리해 드려요."

#### SCR-REQUIREMENT-NEW

- **목적**: 공개 학습 로드맵이나 기술 목록을 붙여넣어 비교를 요청한다(FR-19). **진입**: SCR-REQUIREMENTS-LIST. **Sprint**: S7.
- **레이아웃**: 제목(필수) → 출처 링크(선택, 안내 `radar.new.urlNote`) → 로드맵 본문(여러 줄, 크기 `{kb}KB / 20KB`) → 안내 `radar.new.retention` → primary "비교 요청".
- **데이터**: `POST /requirement-docs` `{title, sourceUrl, sourceText}` → `202 {id}` → `/radar/{id}`.
- **상태**: 전송 중 — 버튼 로딩. AI unavailable — 배너 + 버튼 비활성. Error — `413`은 본문 필드 아래 `radar.new.validation.tooLarge`, 그 외 §5. Offline — 공통.
- **행동·검증**: §3.2(본문 UTF-8 20000 bytes 이하, 링크 http/https). 입력 중 이탈 → §6.8.
- **문구**: `radar.new.title` = "로드맵 붙여넣기", `radar.new.docTitle` = "제목 (예: 백엔드 개발자 로드맵)", `radar.new.url` = "출처 링크 (선택)", `radar.new.urlNote` = "링크는 기록용이에요. DevPilot은 링크의 내용을 가져오지 않아요.", `radar.new.sourceText` = "로드맵 본문", `radar.new.sourceText.hint` = "공개 학습 로드맵이나 기술 목록을 붙여넣어 주세요. 필수와 선택이 나뉘어 있으면 그대로 두세요.", `radar.new.retention` = "본문은 180일 뒤 자동으로 지워지고 비교 결과는 남아요.", `radar.new.submit` = "비교 요청", `radar.new.validation.tooLarge` = "본문이 너무 길어요. 기술 항목 위주로 줄여 주세요."

#### SCR-REQUIREMENT-DETAIL

- **목적**: 로드맵 항목별 준비 상태와 연결된 증거를 보고 보완할 기술로 이동한다. **확률·점수·퍼센트는 표시하지 않는다**(FR-19, AC-22). **진입**: SCR-REQUIREMENTS-LIST, SCR-REQUIREMENT-NEW 요청 직후. **Sprint**: S7.
- **레이아웃**: 헤더(제목, 분석일, 링크가 있으면 `출처 링크 ↗`) → 요약 줄: 필수·권장별 `fitCounts`(`준비됨 {n} · 도전 {n} · 나중에 {n} · 분류 불가 {n}`) → "필수" 섹션 → "권장" 섹션. 항목 행: `rawText`, 매칭 기술 이름(탭 → `/skills/{skillId}`) 또는 `radar.detail.noSkill`, `RequirementFitBadge`, 연결된 증거 링크(→ `/evidence/{id}`). 본문 purge 후에는 헤더에 `radar.detail.purged`.
- **데이터**: `GET /requirement-docs/{requirementDocId}`. `analysisStatus ∈ {PENDING, RUNNING}`이면 2초 polling, 최대 3분. `FAILED`면 `failureCode` 문구 + "다시 요청"(입력 화면으로 이동해 제목·링크를 채움. purge 전이면 본문도 `sourceText`로 채운다).
- **상태**: Loading — skeleton. Async pending — `AsyncStatusIndicator` + `radar.detail.analyzing`. Empty — 항목 0개 → `radar.detail.noRequirements`. Error·Offline — 공통.
- **문구**: `radar.detail.summary` = "준비됨 {ready} · 도전 {stretch} · 나중에 {later} · 분류 불가 {unknown}", `radar.detail.required` = "필수", `radar.detail.preferred` = "권장", `radar.detail.noSkill` = "기술 목록에 없는 항목", `radar.detail.evidence` = "연결된 증거", `radar.detail.source` = "출처 링크", `radar.detail.analyzing` = "로드맵 항목을 정리하고 있어요.", `radar.detail.purged` = "로드맵 본문은 보관 기간이 지나 삭제됐어요.", `radar.detail.noRequirements` = "기술 항목을 찾지 못했어요. 기술 항목이 적힌 로드맵으로 다시 요청해 주세요.", `radar.detail.retry` = "다시 요청", `radar.detail.fitHelp` = "준비됨·도전·나중에는 지금 기술 레벨과 증거로 나눈 분류예요. 점수나 달성 가능성을 뜻하지 않아요."

### 3.14 Settings · 계정

#### SCR-SETTINGS

- **목적**: 프로필·**학습 목표(학습 트랙·목표일)**·학습 시간·하루 경계를 고치고, AI 사용량을 확인하고, 캘린더 구독·앱 설치·데이터 내려받기·계정 삭제·로그아웃을 한다(FR-03, FR-20, FR-22, FR-23, FR-24). 목표일은 사용자가 여기("학습 목표" → SCR-LEARNING-GOAL)에서 직접 등록하고 바꾼다. **진입**: rail Settings, More > 설정. **Sprint**: S1(프로필·학습 목표·시간), S2(설치 안내), S3(AI 사용량), S5(캘린더), S6(데이터·삭제).
- **레이아웃**

```text
┌────────────────────────────────┐
│ 설정                             │
│ 프로필                            │
│ 표시 이름 [mt                   ] │
│ 학습 목표                       >  │
│ 목표일 2027년 4월 1일              │  사용자가 등록한 값 (예시)
│ 학습 시간                          │
│ 평일 [45분 ▾]    주말 [240분 ▾]    │
│ 하루 시작 시각 [새벽 4시 ▾]         │
│ 시간대 [Asia/Seoul ▾]               │
│ ┌────────────────────────────┐ │
│ │             저장              │ │
│ └────────────────────────────┘ │
│ ─────────────────────────────  │
│ AI 사용량                          │
│ 상태 [사용 가능]                     │
│ 이번 달 (서비스 전체)                 │
│ $12.40 / $25.00                      │
│ ▓▓▓▓▓▓▓▓░░░░░░░░ 49%                │
│ 오늘 내 호출 18 / 60회                │
│ ─────────────────────────────  │
│ 캘린더 구독                          │
│ 오늘 할 공부를 내 캘린더에 띄워요.     │
│ [구독 링크 만들기]                    │
│ 이미 만든 링크가 있다면 새 링크를      │
│ 만들 때 이전 링크는 끊겨요.            │
│ ─────────────────────────────  │
│ 앱 설치                               │
│ 홈 화면에 추가하는 방법            >   │
│ ─────────────────────────────  │
│ 내 데이터                             │
│ 내 데이터 내려받기 (JSON)              │
│ 계정 삭제                          >   │
│ ─────────────────────────────  │
│ 로그아웃                               │
│ 버전 1.0.0 (a1b2c3d)                   │
└────────────────────────────────┘
```

- **구독 링크 시트** (`POST /me/calendar-token` 성공 후)

```text
┌────────────────────────────────┐
│ 구독 링크                         │
│ https://<tailnet-host>/api/  │
│ v1/calendar/9f…c2.ics      [복사] │
│ [캘린더 앱에서 열기]               │  webcal://
│ ⚠ 이 링크는 지금만 볼 수 있어요.   │
│   링크를 가진 사람은 과제 제목을     │
│   볼 수 있으니 공유하지 마세요.      │
│ 추가 방법                          │
│ ▸ Google 캘린더                    │
│ ▸ iPhone·Mac                       │
│ ▸ Outlook                          │
│ ┌────────────────────────────┐ │
│ │             완료              │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

- **컴포넌트**: `ProfileForm`, `MinutesDropdown`, `DayStartDropdown`, `TimezonePicker`(검색 가능한 IANA 목록), `AiUsageCard`(`LinearProgressIndicator` + 숫자 텍스트), `CalendarSubscribeSection`, `CalendarLinkSheet`, `InstallGuideSheet`(§8.2), `DataSection`, `LogoutTile`, `AppVersionText`(빌드 시 `--dart-define=APP_VERSION`, `GIT_SHA`).
- **데이터**

| 시점 | API |
|---|---|
| 진입 | `GET /me` (프로필, `version`, `aiStatus`, `aiUsage`), `GET /learning-goal`(학습 목표 요약 줄, 실패하면 요약 줄만 숨김) |
| "학습 목표" | → `/plan/goal` (SCR-LEARNING-GOAL) |
| 프로필·시간 "저장" | `PATCH /me` `{displayName, timezone, dayStartHour, weekdayStudyMinutes, weekendStudyMinutes, version}` |
| "구독 링크 만들기" / "새 링크 만들기" | `POST /me/calendar-token` → `201 {feedUrl, issuedAt}` → 시트 표시 → `meProvider` 갱신 |
| "내 데이터 내려받기" | `GET /me/export` → 파일 저장 `devpilot-export-{yyyyMMdd}.json` |
| "로그아웃" | 저장된 토큰 삭제(`supabase` 모드면 `signOut()`) → `/login` |

- **상태**: Loading — 섹션 skeleton. Error·Offline — 공통. AI 사용량: `DISABLED`면 상태 라벨 `사용 불가` + `settings.ai.disabled`, `BALANCE_EXHAUSTED`면 `잔액 없음` 라벨 + `ai.balanceExhausted.banner`, `BUDGET_WARNING`이면 `예산 80% 이상` 라벨 + `ai.budgetWarning.note`. export 진행 중 — 행에 진행 표시.
- **행동·검증**
  - §3.2. "저장"은 변경이 있을 때만 활성.
  - `timezone` 또는 `dayStartHour`를 바꾸고 저장하면 먼저 대화상자 `settings.dayBoundary.confirm`.
  - `calendarSubscribed=true`면 섹션 제목 옆에 `settings.calendar.subscribed` 라벨을 붙이고 버튼을 "새 링크 만들기"로 바꾼다. 누르면 확인 대화상자 `settings.calendar.reissue.confirm` 후 발급한다.
  - 응답 `feedUrl`이 `null`(같은 키의 재생 응답, `03` §5.4)이면 시트 대신 토스트 `settings.calendar.replayed`를 보여주고, 다음 탭은 새 키로 발급한다.
  - 추가 방법 문구: Google `settings.calendar.howto.google`, iPhone·Mac `settings.calendar.howto.apple`, Outlook `settings.calendar.howto.outlook`.
  - "캘린더 앱에서 열기"는 URL의 `https://`를 `webcal://`로 바꿔 연다.
  - 로그아웃 확인 없음.
- **문구**

| key | 문구 |
|---|---|
| `settings.title` | 설정 |
| `settings.profile` | 프로필 |
| `settings.displayName` | 표시 이름 |
| `settings.goal` | 학습 목표 |
| `settings.goal.summary` | 목표일 {date} |
| `settings.study` | 학습 시간 |
| `settings.weekday` | 평일 |
| `settings.weekend` | 주말 |
| `settings.dayStart` | 하루 시작 시각 |
| `settings.timezone` | 시간대 |
| `settings.save` | 저장 |
| `settings.saved` | 저장했어요. |
| `settings.dayBoundary.confirm.title` | 하루 기준을 바꿀까요? |
| `settings.dayBoundary.confirm.body` | 바꾼 뒤부터 '오늘'이 새 기준으로 계산돼요. 이미 만든 계획과 기록의 날짜는 바뀌지 않아요. |
| `settings.ai.title` | AI 사용량 |
| `settings.ai.status` | 상태 |
| `settings.ai.month` | 이번 달 (서비스 전체) |
| `settings.ai.monthValue` | ${used} / ${budget} |
| `settings.ai.today` | 오늘 내 호출 {used} / {limit}회 |
| `settings.ai.disabled` | AI 기능을 지금 쓸 수 없어요. 복습·계획·Today는 그대로 쓸 수 있어요. |
| `settings.calendar.title` | 캘린더 구독 |
| `settings.calendar.desc` | 오늘 할 공부를 내 캘린더에 띄워요. |
| `settings.calendar.create` | 구독 링크 만들기 |
| `settings.calendar.note` | 이미 만든 링크가 있다면 새 링크를 만들 때 이전 링크는 끊겨요. |
| `settings.calendar.reissue.confirm` | 새 링크를 만들면 지금 쓰는 링크는 바로 끊겨요. 계속할까요? |
| `settings.calendar.subscribed` | 구독 중 |
| `settings.calendar.recreate` | 새 링크 만들기 |
| `settings.calendar.replayed` | 링크를 다시 보여 드릴 수 없어요. 새 링크를 만들어 주세요. |
| `settings.calendar.sheet.title` | 구독 링크 |
| `settings.calendar.copy` | 복사 |
| `settings.calendar.openApp` | 캘린더 앱에서 열기 |
| `settings.calendar.onceWarning` | 이 링크는 지금만 볼 수 있어요. 링크를 가진 사람은 과제 제목을 볼 수 있으니 공유하지 마세요. |
| `settings.calendar.howto.google` | 컴퓨터에서 Google 캘린더 → 다른 캘린더 옆 + → URL로 추가 → 링크 붙여넣기 |
| `settings.calendar.howto.apple` | iPhone: 설정 → 캘린더 → 계정 → 계정 추가 → 기타 → 구독 캘린더 추가. Mac: 캘린더 앱 → 파일 → 새로운 캘린더 구독 |
| `settings.calendar.howto.outlook` | Outlook 웹 → 캘린더 추가 → 웹에서 구독 → 링크 붙여넣기 |
| `settings.calendar.alarmTip` | 오늘부터 7일이 종일 일정으로 들어가요. 캘린더 앱에서 종일 일정 알림 시각을 정해 주세요. |
| `settings.install` | 앱 설치 |
| `settings.install.howto` | 홈 화면에 추가하는 방법 |
| `settings.data` | 내 데이터 |
| `settings.export` | 내 데이터 내려받기 (JSON) |
| `settings.export.done` | 파일로 저장했어요. |
| `settings.deleteAccount` | 계정 삭제 |
| `settings.logout` | 로그아웃 |
| `settings.version` | 버전 {version} ({sha}) |

#### SCR-ACCOUNT-DELETE

- **목적**: 삭제 범위를 알리고, 최근 재로그인과 확인 문구 입력 후 계정 삭제를 요청한다(FR-23, AC-15). **진입**: SCR-SETTINGS "계정 삭제", 재로그인 후 복귀(`?reauth=1`). **Sprint**: S6.
- **레이아웃**

```text
┌────────────────────────────────┐
│ ← 계정 삭제                       │
│ 삭제하면 되돌릴 수 없어요          │
│ 지워지는 것                        │
│ • 목표, 계획, 기술 레벨, 학습 기록   │
│ • 복습 카드, 문제 풀이, 코드 리뷰    │
│ • 증거, 주간 리뷰, 로드맵 비교       │
│ 알아 둘 것                          │
│ • 요청 후 몇 분 안에 지워져요.        │
│ • 백업에 남은 데이터는 보관 기간     │
│   (최대 6개월)이 지나면 사라져요.     │
│ • GitHub 로그인 연결 정보는 운영자가  │
│   따로 삭제해요.                      │
│ [내 데이터 먼저 내려받기]             │
│ ─────────────────────────────  │
│ 1. 본인 확인                          │
│ [GitHub로 다시 로그인]                │
│ ✓ 확인됐어요 · 4분 12초 안에 삭제하세요 │
│ 2. 확인 문구 입력                      │
│ '삭제합니다'를 입력하세요               │
│ [                                  ]   │
│ ┌────────────────────────────┐ │
│ │          계정 삭제              │ │  danger 색 + 텍스트
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

- **데이터**: 재인증 — `dev` 모드는 "다시 로그인" → 이메일 확인 후 `POST /api/v1/dev/token`으로 새 토큰을 받는다(발급 시각이 `amr[].timestamp`가 되어 5분 규칙을 만족). `supabase` 모드는 `supabase.auth.signInWithOAuth(github, redirectTo: '<origin>/auth/callback?from=/settings/delete-account%3Freauth%3D1')`. "내 데이터 먼저 내려받기" → `GET /me/export`. "계정 삭제" → `DELETE /me` → `202` → Supabase `signOut()` → `/login?reason=deleted`.
- **재로그인 판정**: 현재 access token의 JWT payload(base64 디코드, 서명 검증 없음)에서 `authTime` = `amr[].timestamp` 최댓값(`amr`이 없을 때만 `iat`)을 구한다(서버와 같은 기준, `05-api-spec.md` §3.4). `authTime`이 지금으로부터 4분 30초 이내면 "확인됐어요" + 남은 시간 카운트다운(5분 − 30초 여유). 0이 되면 다시 "GitHub로 다시 로그인"을 보여준다.
- **상태**: 삭제 중 — 버튼 로딩, 화면 잠금. Error — `403 RECENT_LOGIN_REQUIRED` → 확인 표시를 지우고 인라인 `account.delete.reauthAgain`. 그 외 §5. Offline — 공통.
- **행동·검증**: "계정 삭제" 활성 조건 = 재로그인 확인 유효 + 입력값이 정확히 `삭제합니다` + 온라인. 추가 확인 대화상자는 없다(두 단계 확인으로 충분).
- **문구**: `account.delete.title` = "계정 삭제", `account.delete.headline` = "삭제하면 되돌릴 수 없어요", `account.delete.what` = "지워지는 것", `account.delete.what.items` = "목표, 계획, 기술 레벨, 학습 기록\n복습 카드, 문제 풀이, 코드 리뷰\n증거, 주간 리뷰, 로드맵 비교", `account.delete.notes` = "알아 둘 것", `account.delete.notes.items` = "요청 후 몇 분 안에 지워져요.\n백업에 남은 데이터는 보관 기간(최대 6개월)이 지나면 사라져요.\nGitHub 로그인 연결 정보는 운영자가 따로 삭제해요.", `account.delete.exportFirst` = "내 데이터 먼저 내려받기", `account.delete.step1` = "1. 본인 확인", `account.delete.reauth` = "GitHub로 다시 로그인", `account.delete.reauthOk` = "확인됐어요 · {remaining} 안에 삭제하세요", `account.delete.reauthAgain` = "본인 확인 시간이 지났어요. 다시 로그인해 주세요.", `account.delete.step2` = "2. 확인 문구 입력", `account.delete.typeHint` = "'삭제합니다'를 입력하세요", `account.delete.phrase` = "삭제합니다", `account.delete.button` = "계정 삭제"

### 3.15 기타

#### SCR-MORE

- **목적**: 모바일에서 하단 탭에 없는 목적지로 이동한다. **진입**: 하단 탭 More(폭 < 600만). **Sprint**: S2.
- **레이아웃**: `ListTile` 목록(아이콘 + 라벨 + `>`), flag가 켜진 항목만: 사이드 프로젝트(`/projects`), 문제 풀이(`/training`), 코드 리뷰(`/coach`), 기술(`/skills`), 오늘의 팁(`/tips`, `tips` flag), 용어 사전(`/terms`, `terms` flag), 진행 현황(`/dashboard`), 주간 리뷰(`/weekly`), 증거(`/evidence`), 로드맵 비교(`/radar`), 설정(`/settings`). AI 상태가 `DISABLED`/`BALANCE_EXHAUSTED`/`BUDGET_WARNING`이면 목록 위에 §6.5 배너.
- **데이터**: `meProvider`(캐시). **상태**: 없음.
- **문구**: `more.title` = "더보기", `more.projects` = "사이드 프로젝트", `more.training` = "문제 풀이", `more.coach` = "코드 리뷰", `more.skills` = "기술", `more.tips` = "오늘의 팁", `more.terms` = "용어 사전", `more.dashboard` = "진행 현황", `more.weekly` = "주간 리뷰", `more.evidence` = "증거", `more.radar` = "로드맵 비교", `more.settings` = "설정"

#### SCR-NOT-FOUND

- **목적**: 없는 경로·없는 리소스(`404 RESOURCE_NOT_FOUND` 전체 화면 처리)를 알린다. **진입**: 알 수 없는 라우트, 상세 화면 404. **Sprint**: S0.
- **레이아웃**: 아이콘(`search_off`) + `notFound.title` + `notFound.body` + primary "Today로"(S1은 "Plan으로").
- **문구**: `notFound.title` = "페이지를 찾을 수 없어요", `notFound.body` = "주소가 바뀌었거나 삭제된 항목이에요.", `notFound.home` = "Today로"

### 3.16 러버덕 · 코드 읽기 · 사이드 프로젝트 · 프로젝트 기록

핵심 루프(`01` §5)의 "설명한다"·"읽는다"·"만든다"를 맡는 화면이다. 러버덕(SCR-RUBBER-DUCK)이 중심이고, 코드 읽기와 사이드 프로젝트는 러버덕으로 이어진다. 프로젝트에서 내린 결정과 겪은 장애는 SCR-PROJECT-DETAIL에 기록으로 남는다(FR-29).

#### SCR-RUBBER-DUCK

- **목적**: 사용자가 대상을 자기 말로 설명하고, AI가 **답 대신 질문 하나로 되묻는** 대화를 몇 턴 이어 간 뒤, 정리에서 막힌 곳(gaps)을 복습 카드로 남긴다(FR-25 ★ 중심 기능, RD-1~RD-7, AC-26). 한 번 쓰고 끝나는 self-explanation과 달리 대화다. **진입**: SCR-READ-CODE "읽었으면 설명하기"(`CODE_READING`), SCR-TRAINING-ATTEMPT 결과 "러버덕으로 설명하기"(`CHALLENGE`), SCR-REVIEW-SESSION 끝 화면·SCR-REVIEW-ITEMS 메뉴(`REVIEW_ITEM`), SCR-SKILL-DETAIL "개념 설명하기"·SCR-TODAY `EXPLAIN`/`READING` 카드(`CONCEPT`), SCR-TODAY `PROJECT_TASK` 카드·SCR-PROJECTS "이 프로젝트 작업 설명하기"(`PROJECT_WORK`), SCR-TODAY "이어서 설명하기". **Sprint**: S3.
- **화면 구성**: 하단 탭·rail을 숨기는 집중 화면이다. 앱 바 `✕`(나가기) + 제목 + `⋮`(그만두기). 상태는 넷이다 — ① 시작 전(`/rubber-duck/new`) ② 대화 중 ③ 정리 중 ④ 정리 결과. 데스크톱(≥ 1024)은 왼쪽 열(35%)에 대상 카드를 고정하고 오른쪽 열(65%)에 대화를 둔다.

- **레이아웃 ① 시작 전** (`/rubber-duck/new?targetType=CODE_READING&targetId={taskId}&skillCode=SPRING.MVC_REST&taskId={taskId}`)

```text
┌────────────────────────────────┐
│ ✕  러버덕                        │
│ ┌────────────────────────────┐ │
│ │ [읽은 코드] Spring MVC REST API│ │
│ │ Spring PetClinic ·            │ │
│ │ OwnerController.java 48~122줄 │ │
│ │ 이 컨트롤러는 Repository를 직접 │ │
│ │ 주입받고 Service 계층이 없습니다.│ │
│ │ 이렇게 두어도 괜찮은 경우와      │ │
│ │ 곤란해지는 경우를 나눠서 설명해  │ │
│ │ 보세요.                        │ │
│ └────────────────────────────┘ │
│ 내 말로 설명해 보세요. AI는 답을   │
│ 알려주지 않고 질문으로 되물어요.   │
│ 최대 5번 주고받은 뒤 막힌 곳을     │
│ 정리해 복습 카드로 만들어요.       │
│ 설명                              │
│ ┌────────────────────────────┐ │
│ │ 무엇을 하는 코드인지, 왜 그렇게 │ │
│ │ 했는지 내 말로 적어 보세요.     │ │
│ └────────────────────────────┘ │
│ 비밀값은 저장 전에 가려요. 0 / 2000│
│ ┌────────────────────────────┐ │
│ │          설명 보내기           │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

  - 대상 카드: 시작 전에는 서버 세션이 없으므로 진입 화면이 go_router `extra`로 넘긴 제목·요약을 쓴다(`CODE_READING`이면 SCR-READ-CODE의 저장소 이름·파일·줄 범위·`question`). `extra`가 없으면(새로고침) 대상 유형 라벨만 보이고, 첫 전송 뒤 `RubberDuckSessionView.targetTitle`로 바뀐다.
  - **세션은 첫 "설명 보내기"에서 만든다**: `POST /rubber-duck` → 201 → 같은 설명으로 곧바로 `POST /rubber-duck/{sessionId}/turns`. 성공하면 라우트를 `/rubber-duck/{sessionId}?taskId=`로 replace하고 기기에 진행 중 세션을 기록한다(§3.5 `ActiveRubberDuckTile`). 설명을 쓰기 전에는 세션을 만들지 않는다 — 빈 세션이 남지 않고, 다른 진행 중 세션을 괜히 끝내지 않는다. 시작은 성공했는데 첫 턴이 실패하면 라우트만 replace하고 설명은 입력칸에 남긴다(턴 0개 세션, ② 상태에서 다시 보내기).
  - 시작 응답의 `abandonedSessionId`가 있으면(다른 러버덕이 진행 중이었음) 토스트 `rubberDuck.previousAbandoned`.
  - `skillCode`는 진입 화면이 알면 넘기고(Today task의 `skillCode`, reading의 첫 skill 등), 모르면 생략해 서버가 대상에서 유도하게 한다(`05` §9.5). 유도하지 못하면 skill 없는 세션이 되고 정리 화면에 `rubberDuck.summary.noSkill`이 보인다(RD-7).

- **레이아웃 ② 대화 중** (`/rubber-duck/:sessionId`)

```text
┌────────────────────────────────┐
│ ✕  러버덕             턴 2 / 5  ⋮│
│ ▸ [읽은 코드] OwnerController.java│  대상 카드(접힘)
│ ─────────────────────────────  │
│              ┌───────────────┐ │
│              │ 나             │ │
│              │ OwnerController│ │
│              │ 가 Repository를 │ │
│              │ 직접 주입받는   │ │
│              │ 이유는 조회만   │ │
│              │ 하고 규칙이 없기│ │
│              │ 때문입니다.     │ │
│              └───────────────┘ │
│ ┌────────────────────┐         │
│ │ 질문            [AI] │         │
│ │ 조회만 한다고 하셨는데,│        │
│ │ 폼 제출을 처리하는    │         │
│ │ 메서드는 어디에 규칙을 │         │
│ │ 두고 있나요?          │         │
│ └────────────────────┘         │
│ ─────────────────────────────  │
│ 내 답                             │
│ ┌────────────────────────────┐ │
│ │ 모르면 모른다고 적어도 괜찮아요.│ │
│ └────────────────────────────┘ │
│ 3번 더 답할 수 있어요.   0 / 2000 │
│ [정리하고 끝내기]      [ 보내기 ] │
└────────────────────────────────┘
```

  - 말풍선: 내 설명(오른쪽, 라벨 "나", `userText` = 서버 마스킹본이라 가린 부분은 가린 그대로 보인다)과 AI 질문(왼쪽, 라벨 "질문" + `AiBadge`). **AI 말풍선에는 정답·채점·✓/✗ 표시를 두지 않는다**(U-9, RD-1). 질문은 200자 이하라 접지 않는다.
  - 남은 턴: 앱 바 `턴 {turnCount} / {maxTurns}`와 입력칸 아래 `rubberDuck.remaining`(= 응답의 `remainingTurns`). 압박 문구를 쓰지 않는다(§9).
  - "보내기" → `POST …/turns` `{explanation}`(IK, 동기 ≤ 20초). 보내는 동안 내 말풍선을 먼저 붙이고 그 아래 `ThinkingIndicator` + `rubberDuck.thinking`. 성공하면 질문 말풍선을 붙이고 입력칸을 비운다. 실패하면 내 말풍선을 지우고 **입력칸에 설명을 그대로 둔다**(턴이 저장되지 않음, `05` §9.7) + 아래 상태 표의 처리.
  - 입력 보관: 작성 중 설명은 `localStorage` `devpilot.rubberduck.draft.<sessionId>`에 1초 debounce로 저장(try/catch)하고 전송 성공 시 지운다.
  - **지시어 안내 (S3)**: 턴 응답의 `vagueReferenceCount`가 1 이상이면 방금 보낸 **내 말풍선 아래**에 보조 한 줄 `rubberDuck.vague.count`("'그거·이렇게' 같은 말을 {count}번 썼어요")를 둔다. 0이면 아무것도 보이지 않는다. 서버가 마스킹본에서 센 결정적 값이고 **저장하지 않는다**(`06` §9.6, `05` §9.7) — 화면도 이전 턴의 값을 누적하거나 기억하지 않고 그 턴 옆에만 둔다.
    - 오류·경고 색을 쓰지 않는다. 숫자 하나와 사실만 쓰고 "고치세요"라고 하지 않는다(U-3, §9). 점수·레벨·복습에 영향이 없다는 것은 정리 화면의 빈틈 항목에서 설명한다.
    - `vagueReferencePer100Words`는 화면에 쓰지 않는다 — 한 턴에서는 비율이 요동쳐 오해를 준다. 비율로 판단하는 곳은 서버의 정리 단계뿐이다(`06` §9.6 VR-8).
    - 스크린 리더는 이 줄을 말풍선의 보조 설명으로 읽고 live region으로 따로 알리지 않는다(새 AI 질문만 알린다).

- **Hint Ladder로 넘어가는 안내** (응답 `suggestHint = true` — RD-3, 2턴 연속 "모르겠다"류)

```text
│ ┌────────────────────────────┐ │
│ │ ⓘ 두 번 연속 막혔어요.        │ │
│ │ 힌트를 한 단계 받아 볼까요?    │ │
│ │ [계속 설명]    [힌트 사다리로] │ │  CHALLENGE 대상
│ └────────────────────────────┘ │
```

  - 대상이 `CHALLENGE`: "힌트 사다리로" → `/training/attempts/{targetId}#hints`(SCR-TRAINING-ATTEMPT 힌트 섹션, 다음 단계 버튼에 포커스). 러버덕 세션은 `IN_PROGRESS`로 남고 돌아오면 이어서 설명할 수 있다(턴 상한은 그대로). 힌트 공개는 그 attempt 기록에 남고(`HINT_DISCLOSED`) 결과 판정에 반영된다(FR-10).
  - 그 외 대상(`CODE_READING`, `REVIEW_ITEM`, `CONCEPT`, `PROJECT_WORK`): 힌트를 붙일 곳이 없으므로 둘째 줄을 `rubberDuck.stuck.summarize`로 바꾸고 버튼은 "계속 설명" / "정리하고 끝내기"다. 러버덕 전용 힌트는 없다(`05` §9.7).
  - 안내는 대화 끝에 한 번 붙는다. 닫기 버튼은 없고, 다음 턴을 보내면 사라진다.

- **턴 상한 도달** (`remainingTurns = 0`, 또는 조회한 `turnCount = maxTurns`): 입력칸을 숨기고 `rubberDuck.limitReached` + primary "정리하기"만 둔다(RD-4).

- **레이아웃 ③ 정리 중**: "정리하고 끝내기"·"정리하기" → 턴이 1개 이상이면 확인 없이 `POST …/complete`(IK, body 없음, 동기 ≤ 30초). 대화 위에 진행 표시 + `rubberDuck.summarizing`, 모든 버튼 비활성. 턴이 0개면 대화상자 `rubberDuck.endEmpty.*` → 확인 시 같은 요청(서버가 AI 없이 `ABANDONED`로 끝낸다).

- **레이아웃 ④ 정리 결과**

```text
┌────────────────────────────────┐
│ ✕  러버덕 정리                   │
│ Spring MVC REST API · 4턴        │
│ 막힌 곳 1개                      │
│ ┌────────────────────────────┐ │
│ │ 무엇을 몰랐나                  │ │
│ │ 읽기 전용 조회에도 트랜잭션    │ │
│ │ 경계가 필요한 이유를 설명하지  │ │
│ │ 못했다.                       │ │
│ │ 왜 중요한가                    │ │
│ │ 경계를 모르면 지연 로딩과      │ │
│ │ 커넥션 반환 시점을 예측할 수   │ │
│ │ 없다.                         │ │
│ │ 복습 질문                      │ │
│ │ 조회만 하는 메서드에 트랜잭션을 │ │
│ │ 여는 이유를 설명해 보세요.      │ │
│ │                 복습 카드 보기 > │ │
│ └────────────────────────────┘ │
│ 잘 설명한 것                      │
│ • 컨트롤러에 규칙이 없다는 점을   │
│   정확히 짚었다                   │
│ 한 줄 정리                  [AI]  │
│ 계층의 목적은 잡았고, 트랜잭션     │
│ 경계가 다음 차례다.                │
│ ⓘ 복습 카드 1장을 만들었어요.      │
│   내일부터 복습에 나와요.          │
│ ▸ 대화 다시 보기 (4턴)             │
│ ┌────────────────────────────┐ │
│ │    Today로 돌아가 완료하기      │ │  taskId 있을 때
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │
│ │     그래서 답이 뭔가요          │ │  skill에 노트가 있을 때
│ └────────────────────────────┘ │
│ 복습하러 가기                      │
└────────────────────────────────┘
```

  - gap 카드: "무엇을 몰랐나"(`whatWasMissed`) · "왜 중요한가"(`whyItMatters`) · "복습 질문"(`reviewQuestion`) · "복습 카드 보기"(`reviewItemId`가 있으면 → `/review/items?skillId={skill.id}`). gap이 0개면 카드 대신 `rubberDuck.summary.noGaps`. AI가 만든 문장이므로 영역 제목 옆에 `AiBadge`를 둔다(U-4).
  - **"용어" 빈틈 (S3)**: 지시어를 많이 쓰면 서버가 빈틈 목록에 `용어 — 지시어 대신 용어로 바꿔 말해 보기` 1건을 더한다(`06` §9.6 VR-9, 한 세션에 최대 1건). 이 항목은 **AI가 낸 것이 아니므로 `AiBadge`를 붙이지 않고**, `reviewItemId`가 없으므로 "복습 카드 보기"도 없다(VR-10 — 복습 카드를 만들지 않는다). 카드 아래에 `rubberDuck.summary.vagueNote`를 한 줄 두어 표현 습관을 센 것이고 레벨·복습에 영향이 없다는 것을 밝힌다. 다른 gap과 같은 카드 모양을 쓰되 이 두 가지만 다르다.
  - "잘 설명한 것"(`confirmed`)은 글머리표 목록이다. 채점 기호(✓)를 쓰지 않는다.
  - 복습 카드 안내: `createdReviewItemCount > 0` → `rubberDuck.summary.cardsCreated`. `reviewItemId`가 있는 gap 수가 `createdReviewItemCount`보다 크면 그 차이만큼 `rubberDuck.summary.cardsPulled`(이미 있던 같은 개념 카드의 due를 당김, `05` §9.8).
  - 증거 안내: gap 0개 + `turnCount ≥ 3` + skill 있음 → `rubberDuck.summary.evidence`(RD-5). skill이 `null`이면 → `rubberDuck.summary.noSkill`(RD-7).
  - `summarySkippedReason`이 있으면(정리 AI 실패·차단): gap·잘한 것·한 줄 정리 영역 대신 `rubberDuck.summary.skipped` + 사유 한 줄 `asyncFailure.<CODE>`(§5.2). **다시 정리하는 버튼은 없다**(`05` §9.8). 대화는 "대화 다시 보기"로 남아 있다. `CODE_READING`이면 이 경우에도 세션이 `COMPLETED`라 과제 완료 조건(RC-1)을 만족한다.
  - `status = ABANDONED`(턴 0개로 끝냄, 그만두기, 하루 이상 방치): 결과 대신 `rubberDuck.abandoned` + 대화 기록(있으면) 읽기 전용.
  - 버튼(primary 하나): `taskId`가 있으면 "Today로 돌아가 완료하기"(→ `/today?complete={taskId}` — `CODE_READING`은 이 경로로 완료 기록에 들어간다), 없으면 "닫기"(→ 뒤로, 히스토리가 없으면 `/today`). gap이 있으면 보조 "복습하러 가기"(→ `/review`).
  - **"그래서 답이 뭔가요"**: `skill`에 노트가 있으면(`GET /skills/{skillId}/lesson` 200, `05` §21.3) SCR-LESSON으로 가는 보조 버튼을 둔다. 러버덕은 끝까지 답을 말하지 않고(NA-4) 빈틈 카드에 담기는 것도 "답이 다뤄야 할 것"이지 답이 아니다 — 답까지 가는 길이 없으면 **답을 모른 채로 끝난다**. 노트가 없는 skill이면 버튼 자체가 없다(누를 곳이 있는데 404가 나는 것보다 낫다).
  - 정리 응답을 받으면 기기의 진행 중 세션 기록을 지우고, `targetType = CODE_READING`이면 `devpilot.rubberduck.task.<taskId>` = `sessionId`를 저장한다(§3.5 `READ_CODE` 완료 확인).

- **컴포넌트**: `RubberDuckTargetCard`(유형 라벨 + 제목 + 요약, 접기/펼치기), `ChatBubble`(나 / 질문 두 변형, 질문은 `AiBadge`), `ThinkingIndicator`, `ExplanationField`(여러 줄, 글자 수, private key 즉시 검사), `RemainingTurnsText`, `StuckHintCallout`, `RubberDuckSummaryView`(`GapCard`, `ConfirmedList`, `OverallNote`), `AiUnavailableBanner`, `BudgetWarningNote`, `RubberDuckMenu`(그만두기).
- **데이터**

| 시점 | API |
|---|---|
| ① 첫 "설명 보내기" | `POST /rubber-duck` `{targetType, targetId, conceptKey, skillCode}` (IK) → `201 {session, abandonedSessionId}` → `POST /rubber-duck/{sessionId}/turns` `{explanation}` (IK) |
| ② 진입(`/rubber-duck/:sessionId`), 화면 복귀(visibility) | `GET /rubber-duck/{sessionId}` (턴 전체, `suggestHint`, `summary` 포함) |
| "보내기" | `POST /rubber-duck/{sessionId}/turns` `{explanation}` (IK, 동기) → `201 {turnNo, question, suggestHint, remainingTurns, aiMeta, version}` |
| "정리하고 끝내기"·"정리하기" | `POST /rubber-duck/{sessionId}/complete` (IK, body 없음, 동기) → `200 RubberDuckCompleteResponse` |
| `⋮` "그만두기" | 대화상자 `rubberDuck.abandon.confirm` → `POST /rubber-duck/{sessionId}/abandon` (IK 없음) → 진입 화면으로 뒤로 |

- **상태**
  - Loading: 대상 카드 + 말풍선 skeleton 2개.
  - Empty: 해당 없음(① 시작 전이 빈 상태 역할).
  - Error (조회): `404 RESOURCE_NOT_FOUND`(다른 사용자의 세션도 같음) → SCR-NOT-FOUND. 그 외 공통.
  - Error (시작): `400 VALIDATION_FAILED` field `targetId`·`conceptKey`(`REFERENCE_NOT_FOUND` 등 — 대상이 지워짐) → 대화상자 `rubberDuck.targetGone` + 뒤로.
  - Error (턴 전송) — 입력은 항상 유지한다:

| 응답 | 처리 |
|---|---|
| `413 CONTENT_TOO_LARGE` | 입력칸 아래 인라인 `validation.maxLength`(클라이언트가 2000자에서 막으므로 드묾) |
| `422 SECRET_DETECTED_BLOCKED` | 입력칸 아래 인라인 `rubberDuck.validation.privateKey`. 턴 저장·AI 호출 없음 |
| `409 INVALID_STATE_TRANSITION` | 세션이 이미 끝났거나(다른 기기에서 정리·그만두기, 하루 이상 방치로 `ABANDONED`) 턴 상한 — `GET`으로 다시 읽어 ④ 또는 턴 상한 상태로 바꾸고 토스트 `rubberDuck.sessionChanged`. 작성한 설명은 "복사" 버튼과 함께 잠시 남긴다 |
| `409 CONCURRENT_MODIFICATION` | `GET` 후 같은 설명으로 다시 보낼 수 있게 둔다 |
| `429 AI_*` | §5.1 처리 + `meProvider` 갱신 |
| `502 AI_OUTPUT_INVALID`, `504 AI_TIMEOUT` | 토스트 `error.<CODE>` + "다시 보내기"(새 IK). AI 질문이 답을 담아 가드에 걸린 경우(NA-1~NA-3, `17` §6.8)도 여기로 온다 |
| `502 AI_REFUSED` | 입력칸 아래 인라인 `error.AI_REFUSED` |
| `503 AI_UNAVAILABLE` | 토스트 + `meProvider` 갱신 → 아래 AI 불가 상태 |
| 네트워크 오류 | 같은 IK로 자동 재시도(§6.6), 실패하면 "다시 보내기" |

  - Error (정리): `complete`는 AI 실패를 HTTP 오류로 주지 않는다 — `200` + `summarySkippedReason`으로 ④에 표시한다. `409 INVALID_STATE_TRANSITION`(이미 정리·중단됨)은 `GET`으로 다시 읽어 ④로 간다.
  - **AI 불가** (`aiStatus ∈ {DISABLED, BALANCE_EXHAUSTED}` 또는 `503 AI_UNAVAILABLE`): 상단 `AiUnavailableBanner`. ① 시작 전이면 "설명 보내기" 비활성 + 버튼 아래 `ai.disabledReason`/`ai.balanceExhaustedReason`(§6.5)과 `rubberDuck.aiOff`. 진입 화면의 러버덕 버튼도 같은 조건으로 미리 막고 같은 사유를 보인다. ② 대화 중이면 "보내기" 비활성, **"정리하고 끝내기"는 그대로 둔다** — 서버가 AI 없이 정리 실패 경로로 세션을 끝내고 대화는 남는다(`05` §9.8 4단계). 지난 세션 조회는 언제나 된다(`17` §3.10).
  - **재현 잠금 (S4)**: 대상이 `CHALLENGE`·`PROJECT_WORK`이고 그 대상의 재현 과제가 열려 있으면 시작 요청이 `409 AI_ASSIST_LOCKED_FOR_REDO`다(RE-5). ① 시작 전 화면에서 "설명 보내기"를 비활성으로 바꾸고 아래 `today.redo.lockedElsewhere` 1줄 + "Today로 가기"를 보인다(§5.1). 이미 시작한 세션은 영향을 받지 않는다(잠금은 **시작**에만 걸린다).
  - Budget warning: "보내기" 아래 `ai.budgetWarning.note`.
  - Offline: 공통. 입력은 계속 가능, 보내기·정리 버튼 비활성.
- **행동·검증**
  - 설명: 공백 아닌 문자 1개 이상, 최대 2000자(§3.2, `devpilot.rubberduck.max-explanation-chars`). 1900자부터 글자 수를 경고 색 + `rubberDuck.nearLimit`. private key 정규식이 맞으면 즉시 인라인 오류 + 버튼 비활성(서버로 보내지 않는다). 입력칸 아래 상시 안내 `rubberDuck.maskingNote` — 저장 전 secret 마스킹(RD-6).
  - 한 번에 하나만 보낸다. 전송 중에는 입력·보내기·정리 버튼 비활성.
  - `Ctrl/Cmd+Enter` = 보내기(A-7). IME 조합 중에는 무시한다(A-13).
  - "✕"(나가기): 세션은 `IN_PROGRESS`로 남고 Today에서 이어 열 수 있다. 작성 중 설명이 있으면 §6.8 이탈 확인. 나갈 때 토스트 `rubberDuck.leftOpen`.
  - 스크린 리더: 새 AI 질문을 live region으로 읽는다. 말풍선은 "나: …" / "질문: …"으로 읽는다.
- **문구**

| key | 문구 |
|---|---|
| `rubberDuck.title` | 러버덕 |
| `rubberDuck.summaryTitle` | 러버덕 정리 |
| `rubberDuck.intro` | 내 말로 설명해 보세요. AI는 답을 알려주지 않고 질문으로 되물어요. 최대 {maxTurns}번 주고받은 뒤 막힌 곳을 정리해 복습 카드로 만들어요. |
| `rubberDuck.input.first` | 설명 |
| `rubberDuck.input.first.hint` | 무엇을 하는 코드인지, 왜 그렇게 했는지 내 말로 적어 보세요. |
| `rubberDuck.input.answer` | 내 답 |
| `rubberDuck.input.answer.hint` | 모르면 모른다고 적어도 괜찮아요. |
| `rubberDuck.send.first` | 설명 보내기 |
| `rubberDuck.send` | 보내기 |
| `rubberDuck.maskingNote` | 비밀값으로 보이는 부분은 저장 전에 가려요. 개인 키는 보낼 수 없어요. |
| `rubberDuck.nearLimit` | 2,000자에 가까워요 |
| `rubberDuck.validation.privateKey` | 개인 키(private key)가 포함되어 있어 보낼 수 없어요. 해당 부분을 지워 주세요. |
| `rubberDuck.me` | 나 |
| `rubberDuck.question` | 질문 |
| `rubberDuck.thinking` | AI가 질문을 고르고 있어요 (최대 20초) |
| `rubberDuck.turns` | 턴 {turnCount} / {maxTurns} |
| `rubberDuck.remaining` | {count}번 더 답할 수 있어요. |
| `rubberDuck.vague.count` | '그거·이렇게' 같은 말을 {count}번 썼어요. |
| `rubberDuck.summary.vagueNote` | 표현 습관을 센 것이라 레벨이나 복습에는 영향이 없어요. |
| `rubberDuck.resend` | 다시 보내기 |
| `rubberDuck.stuck.title` | 두 번 연속 막혔어요. |
| `rubberDuck.stuck.hint` | 힌트를 한 단계 받아 볼까요? |
| `rubberDuck.stuck.toHint` | 힌트 사다리로 |
| `rubberDuck.stuck.summarize` | 여기서 정리하면 막힌 곳이 복습 카드가 돼요. |
| `rubberDuck.stuck.continue` | 계속 설명 |
| `rubberDuck.limitReached` | 설명을 모두 들었어요. 정리해 볼까요? |
| `rubberDuck.finish` | 정리하고 끝내기 |
| `rubberDuck.summarize` | 정리하기 |
| `rubberDuck.summarizing` | AI가 대화를 정리하고 있어요 (최대 30초) |
| `rubberDuck.endEmpty.title` | 설명 없이 끝낼까요? |
| `rubberDuck.endEmpty.body` | 보낸 설명이 없어 정리할 내용이 없어요. 기록은 남지 않아요. |
| `rubberDuck.endEmpty.confirm` | 끝내기 |
| `rubberDuck.summary.gaps` | 막힌 곳 {count}개 |
| `rubberDuck.summary.missed` | 무엇을 몰랐나 |
| `rubberDuck.summary.why` | 왜 중요한가 |
| `rubberDuck.summary.reviewQuestion` | 복습 질문 |
| `rubberDuck.summary.viewCard` | 복습 카드 보기 |
| `rubberDuck.summary.noGaps` | 막힌 곳 없이 설명했어요. |
| `rubberDuck.summary.confirmed` | 잘 설명한 것 |
| `rubberDuck.summary.note` | 한 줄 정리 |
| `rubberDuck.summary.cardsCreated` | 복습 카드 {count}장을 만들었어요. 내일부터 복습에 나와요. |
| `rubberDuck.summary.cardsPulled` | 이미 있던 카드 {count}장은 복습을 앞당겼어요. |
| `rubberDuck.summary.evidence` | 막힌 곳 없이 3번 이상 설명했어요. 설명 능력 기록으로 남아요. |
| `rubberDuck.summary.noSkill` | 기술이 정해지지 않은 대화라 레벨 기록에는 들어가지 않아요. |
| `rubberDuck.summary.skipped` | 대화는 저장했어요. AI가 정리하지 못해 복습 카드는 만들지 않았어요. |
| `rubberDuck.summary.history` | 대화 다시 보기 ({count}턴) |
| `rubberDuck.toToday` | Today로 돌아가 완료하기 |
| `rubberDuck.toReview` | 복습하러 가기 |
| `rubberDuck.close` | 닫기 |
| `rubberDuck.abandoned` | 정리하지 않고 끝난 대화예요. |
| `rubberDuck.menu.abandon` | 그만두기 |
| `rubberDuck.abandon.confirm` | 정리하지 않고 끝낼까요? 대화는 남지만 복습 카드는 만들지 않아요. |
| `rubberDuck.previousAbandoned` | 하던 다른 러버덕은 끝내고 새로 시작했어요. |
| `rubberDuck.sessionChanged` | 이 대화는 이미 끝났어요. 최신 상태를 불러왔어요. |
| `rubberDuck.leftOpen` | 대화는 저장돼 있어요. Today에서 이어서 할 수 있어요. 하루가 지나면 자동으로 끝나요. |
| `rubberDuck.targetGone` | 설명할 대상을 찾을 수 없어요. 삭제됐을 수 있어요. |
| `rubberDuck.aiOff` | 러버덕은 AI가 되물어야 해서 지금은 시작할 수 없어요. 지난 대화는 볼 수 있어요. |

#### SCR-READ-CODE

- **목적**: 검증된 오픈소스의 파일 1개·줄 범위 1개를 **내 컴퓨터의 IDE에서** 읽도록 무엇을·왜·어디서·무엇을 보며 읽는지 안내하고, 읽은 뒤 러버덕으로 설명하게 한다(FR-27, RC-1~RC-4, AC-28). **코드 본문은 화면에 없다** — 서버는 저장소를 가져오지 않고 응답에도 코드가 없다(`05` §19.7). **진입**: SCR-TODAY `READ_CODE` 카드 "시작"·"코드 읽기로 돌아가기"(`/today/read/{readingKey}?taskId=`). **Sprint**: S3.
- **레이아웃** (예: `READ.PETCLINIC.CONTROLLER_SLICE.001`)

```text
┌────────────────────────────────┐
│ ← 코드 읽기                       │
│ Spring PetClinic                  │
│ Spring Boot 4.1, Java 17,         │
│ Spring Data JPA · Apache-2.0       │
│ 왜 이 저장소?                      │
│ Spring 공식 샘플. 계층 구조와 테스트 │
│ 작성법의 정석이고, 테스트 코드가     │
│ main보다 많다.                     │
│ 저장소 열기 ↗                       │
│ ─────────────────────────────  │
│ ① 내 컴퓨터로 가져오기               │
│ ┌────────────────────────────┐ │
│ │ git clone https://github.com/ │ │  monospace, 줄바꿈 없이
│ │ spring-projects/spring-petcli…│ │  가로 스크롤
│ └─────────────────────── [복사] ┘ │
│ 기준 커밋 818c413  [전체 복사]      │
│ 줄 번호는 이 커밋 기준이에요. 이미   │
│ 받아 두었다면 같은 커밋인지 확인하세요.│
│ ─────────────────────────────  │
│ ② 이 파일을 여세요                   │
│ src/main/java/org/springframework/  │
│ samples/petclinic/owner/            │
│ OwnerController.java    [경로 복사]  │
│ 48~122줄 · 약 15분                   │
│ ─────────────────────────────  │
│ ③ 읽으면서 생각할 질문                │
│ 이 컨트롤러는 Repository를 직접      │
│ 주입받고 Service 계층이 없습니다.    │
│ 이렇게 두어도 괜찮은 경우와 곤란해진 │
│ 경우를 나눠서 설명해 보세요.         │
│ 볼 지점                              │
│ • 계층을 나누는 목적                   │
│ • 트랜잭션 경계가 어디에 생기는가       │
│ • 지금 내 프로젝트는 어느 쪽에 가까운가 │
│ ─────────────────────────────  │
│ ┌────────────────────────────┐ │
│ │       읽었으면 설명하기          │ │  → SCR-RUBBER-DUCK
│ └────────────────────────────┘ │
│ 러버덕으로 설명을 마치면 완료예요.    │
│ 여기까지 기록                         │
└────────────────────────────────┘
```

- **라이선스가 명시되지 않은 저장소** (`repo.license = UNSPECIFIED`, 현재 `restbucks`, `19` §3.8): 저장소 이름 아래 `StatusBadge` warning `readCode.readOnly.badge`("읽기만 · 복사 금지") + 한 줄 `readCode.readOnly.note`. 콘텐츠에 `licenseNote`가 있으면 그 문장을 보여준다. 라이선스가 있는 저장소는 `license` 값을 그대로 텍스트로 보인다.
- **코드가 없다는 안내**: ① 제목 바로 아래에 `readCode.noCodeNote`를 둔다. 줄 범위 미리보기·코드 발췌·검색 같은 표시는 만들지 않는다(서버에 코드가 없다).
- **하위 경로**: `repo.subPath`가 비어 있지 않으면(예: restbucks `server`, modulith 예제 폴더) ② 경로 위에 `readCode.subPathNote`를 둔다 — 파일 경로는 그 폴더 기준이고 `cloneHint`가 그 폴더로 이동한다(`19` §3.8).
- **컴포넌트**: `RepoHeader`(이름, 스택, 라이선스 또는 읽기 전용 배지, `why`, 외부 링크), `CloneHintBox`(monospace 한 줄, 가로 스크롤, 복사 버튼), `PinnedCommitRow`(앞 7자 + 전체 복사), `FilePathRow`(경로 + 복사, 줄 범위, 예상 시간), `ReadingQuestionCard`(`question`, `lookFor` 목록), `AiUnavailableBanner`.
- **데이터**

| 시점 | API / 동작 |
|---|---|
| 진입 | `GET /readings/{readingKey}` → `CuratedReadingView` (저장소·경로·줄 범위·질문·볼 지점·skill. **코드 본문 없음**) |
| "복사" | 클립보드에 `cloneHint` / `pinnedCommit` 전체 / `path`. 토스트 `common.error.copied` |
| "저장소 열기 ↗" | 브라우저 새 탭으로 `repo.url`을 연다(사용자의 브라우저가 여는 것이고 서버는 요청하지 않는다) |
| "읽었으면 설명하기" | `/rubber-duck/new?targetType=CODE_READING&targetId={taskId}&skillCode={skills[0].code}&taskId={taskId}` + `extra`(저장소 이름, 파일 이름, 줄 범위, `question`) |
| "여기까지 기록" | Today와 같은 `CompleteSessionSheet`(`today.partial.title`) → 세션 complete(0분이면 abandon) → `PATCH /today/tasks/{taskId}` `{status: DEFERRED, version}`. RC-1 조건이 없는 전이다 |

  - 과제 완료는 이 화면에서 하지 않는다. 러버덕 정리 화면의 "Today로 돌아가 완료하기"로 간다(RC-1 — 읽었다는 체크만으로는 완료가 아니다). 완료 시트에서 이 읽기의 평가(도움 됐어요 · 어려웠어요 · 지루했어요)를 **골라도 되고 안 골라도 된다**(SCR-TODAY 완료 시트, `readingFeedback`). 평가는 사람이 하는 소스 점검(`19` §8.5)의 입력이다.
- **상태**
  - Loading: 헤더·단계 블록 skeleton.
  - Empty: 해당 없음.
  - 은퇴한 단위(`retired = true`, `19` §8.2): 안내 ①~③은 그대로 보이고(좌표는 `pinnedCommit` 기준이라 여전히 맞다) 제목 아래에 `readCode.retired`를 둔다. 설명·완료도 그대로 할 수 있다. 새로 제안되지는 않는다.
  - Error: `404 RESOURCE_NOT_FOUND`(콘텐츠에 없는 key) → SCR-NOT-FOUND. `400 VALIDATION_FAILED`(key 형식) → SCR-NOT-FOUND. 그 외 공통.
  - `taskId`가 없거나 UUID가 아님: 읽기 안내는 그대로 보이고 "읽었으면 설명하기"·"여기까지 기록"을 숨긴 뒤 `readCode.noTask`.
  - **AI 불가**: 읽기 안내(①~③)는 모두 동작한다(`17` §3.10). 상단 `AiUnavailableBanner`, "읽었으면 설명하기" 비활성 + 버튼 아래 `ai.disabledReason`/`ai.balanceExhaustedReason`과 `readCode.aiOff`.
  - Budget warning: 버튼 아래 `ai.budgetWarning.note`.
  - Offline: 공통. 이미 받은 안내는 읽기 전용으로 보이고(복사는 동작) 버튼은 비활성.
- **행동·검증**: 입력이 없다. `CloneHintBox`는 가로 스크롤을 허용하는 유일한 영역이다(A-11). 긴 경로는 `/`에서 줄을 바꾼다. 스크린 리더는 ①②③을 제목으로 읽는다.
- **문구**

| key | 문구 |
|---|---|
| `readCode.title` | 코드 읽기 |
| `readCode.why` | 왜 이 저장소? |
| `readCode.openRepo` | 저장소 열기 |
| `readCode.step.clone` | 내 컴퓨터로 가져오기 |
| `readCode.step.open` | 이 파일을 여세요 |
| `readCode.step.think` | 읽으면서 생각할 질문 |
| `readCode.copy` | 복사 |
| `readCode.copyPath` | 경로 복사 |
| `readCode.pinnedCommit` | 기준 커밋 {shortSha} |
| `readCode.pinnedCommit.copy` | 전체 복사 |
| `readCode.pinnedCommit.note` | 줄 번호는 이 커밋 기준이에요. 이미 받아 두었다면 같은 커밋인지 확인하세요. |
| `readCode.subPathNote` | 경로는 저장소 안 `{subPath}` 폴더 기준이에요. 위 명령이 그 폴더로 이동해요. |
| `readCode.range` | {start}~{end}줄 · 약 {minutes} |
| `readCode.lookFor` | 볼 지점 |
| `readCode.explain` | 읽었으면 설명하기 |
| `readCode.explain.note` | 러버덕으로 설명을 마치면 완료예요. |
| `readCode.partial` | 여기까지 기록 |
| `readCode.readOnly.badge` | 읽기만 · 복사 금지 |
| `readCode.readOnly.note` | 라이선스가 명시되지 않은 저장소예요. 읽기만 하고 코드를 가져다 쓰지 마세요. |
| `readCode.noCodeNote` | 코드는 DevPilot이 아니라 내 컴퓨터에서 봐요. |
| `readCode.retired` | 더 이상 새로 제안하지 않는 읽기예요. 이 과제는 그대로 읽고 설명할 수 있어요. |
| `readCode.noTask` | Today의 코드 읽기 과제에서 열면 설명하고 완료할 수 있어요. |
| `readCode.aiOff` | 설명(러버덕)은 AI가 필요해 지금은 할 수 없어요. 읽기는 계속할 수 있어요. |

#### SCR-PROJECTS

- **목적**: 배운 것을 적용할 사이드 프로젝트를 한 화면에서 보고·등록·수정·삭제한다(FR-26, SP-1~SP-3, AC-27). Today의 `PROJECT_TASK`와 코드 리뷰, 러버덕 `PROJECT_WORK`의 대상이다. **진입**: More > 사이드 프로젝트, rail Projects, SCR-PLAN "사이드 프로젝트 보기". **Sprint**: S1 (러버덕 버튼 S3).
- **레이아웃 (목록)**

```text
┌────────────────────────────────┐
│ 사이드 프로젝트                    │
│ 배운 것을 적용하는 내 프로젝트예요.  │
│ Today 프로젝트 과제는 '지금 만드는   │
│ 것' 중 가장 최근에 고친 '진행 중'    │
│ 프로젝트로 나와요.                  │
│ ┌────────────────────────────┐ │
│ │ 주문 시스템          [진행 중] │ │
│ │ Today 과제 대상                │ │
│ │ 회원가입 · 상품 · 주문 · 취소까지 │
│ │ 직접 만드는 학습용 백엔드        │ │
│ │ Spring Boot, PostgreSQL         │ │
│ │ github.com/example/order-service↗│
│ │ 9월 30일 수정                 ⋮ │ │
│ │ [이 프로젝트 작업 설명하기]      │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │
│ │ 게시판 API        [잠시 멈춤]  │ │
│ │ 9월 12일 수정                 ⋮ │ │
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │
│ │          프로젝트 추가           │ │
│ └────────────────────────────┘ │
├────────────────────────────────┤
│  Today   Review   Plan   More  │
└────────────────────────────────┘
```

- **레이아웃 (등록·수정 시트)** — 같은 화면 위의 `ProjectEditSheet`(폭 < 600 bottom sheet, ≥ 600 대화상자, §6.7)

```text
┌────────────────────────────────┐
│ 프로젝트 수정                      │
│ 종류                               │
│ [지금 만드는 것✓][지난 경험]        │
│ 지난 경험은 Today 과제로 나오지     │
│ 않고 기록만 남겨요.                 │
│ 이름 *                            │
│ [주문 시스템                    ] │
│ 설명 (선택)                 34/1000│
│ [회원가입 · 상품 · 주문 · 취소까지 ]│
│ 저장소 주소 (선택)                  │
│ [https://github.com/example/…   ] │
│ 주소는 기록용이에요. DevPilot은     │
│ 저장소 내용을 가져오지 않아요.       │
│ 스택 (선택)                        │
│ [Spring Boot, PostgreSQL        ] │
│ 상태                               │  수정 때만
│ [진행 중✓][잠시 멈춤][완료]          │
│ ┌────────────────────────────┐ │
│ │              저장               │ │
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

- **목록 규칙**: 서버 정렬(`updatedAt` DESC, `id` DESC)을 그대로 쓴다. 목록에서 **`kind = SIDE`이면서** `ACTIVE`인 첫 번째 프로젝트에 `projects.todayTarget` 라벨을 붙인다(SP-3, I-23 — planner가 쓰는 프로젝트. 여러 개여도 하나만 쓴다). 그런 프로젝트가 하나도 없으면 목록 위에 `projects.noActive`(프로젝트 과제가 제안되지 않음)를 둔다. 상태 라벨은 §3.1 `SideProjectStatus`.
- **프로젝트 종류 (S3)**: 카드 제목 줄에 `kind = PAST_WORK`일 때만 `enum.SideProjectKind.PAST_WORK`("지난 경험") 라벨을 붙인다. `SIDE`는 기본값이라 라벨을 붙이지 않는다(라벨이 둘이면 목록이 시끄러워진다). 등록·수정 시트 맨 위 세그먼트로 고르고, **나중에 바꿀 수 있다**(`PATCH`, `05` §19.5). `PAST_WORK`로 바꾸면 다음 계획부터 Today 프로젝트 과제 대상에서 빠지고, 이미 만든 과제와 기록은 그대로 남는다 — 저장 후 토스트 `projects.kindChanged`.
- **`repoUrl`**: 링크 텍스트는 scheme을 뺀 주소, 탭하면 새 탭으로 연다(사용자 브라우저가 여는 것, 서버는 요청하지 않는다). 입력 시트에는 `projects.repoUrl.note`를 항상 둔다(FR-26, `07` §5.5).
- **컴포넌트**: `ProjectCard`(이름, 상태 배지, Today 대상 라벨, 설명 2줄 말줄임, 스택, 저장소 링크, 수정일, 기록 수, `⋮` 메뉴, 러버덕 버튼), `ProjectEditSheet`, `StatusSegmented`, `DeleteProjectDialog`, `EmptyState`.
- **기록으로 가기 (S3)**: 카드 전체를 누르면 SCR-PROJECT-DETAIL(`/projects/{sideProjectId}`)로 간다. 카드 아래에 `projects.noteCount`를 보인다 — 수는 목록 응답에 없으므로 상세 화면에 들어간 적이 있으면 캐시한 값을, 없으면 라벨을 생략한다(목록 API를 늘리지 않는다). `⋮` 메뉴와 러버덕 버튼은 카드 탭보다 우선한다.
- **데이터**

| 시점 | API |
|---|---|
| 진입·화면 복귀 | `GET /side-projects?cursor=` (status 필터 없이 전체, 스크롤 페이지네이션) |
| "프로젝트 추가" → "저장" | `POST /side-projects` `{name, description, repoUrl, stack, kind}` (IK) → `201 SideProjectView` → 목록 맨 위에 추가. `kind`를 고르지 않으면 보내지 않는다(서버 기본값 `SIDE`) |
| `⋮` "수정" → "저장" | `PATCH /side-projects/{sideProjectId}` — 바뀐 필드만 + `version`. 지운 선택 필드는 빈 문자열 `""`로 보낸다(서버가 `null`로 저장). `name`은 지울 수 없다 |
| `⋮` "잠시 멈춤"·"진행 중으로"·"완료로" | `PATCH /side-projects/{sideProjectId}` `{status, version}` |
| `⋮` "삭제" | 대화상자 → `DELETE /side-projects/{sideProjectId}` → `204` → 목록에서 제거 |
| "이 프로젝트 작업 설명하기" (S3) | `/rubber-duck/new?targetType=PROJECT_WORK&targetId={sideProjectId}` + `extra`(이름, 설명) |

- **상태**
  - Loading: 카드 skeleton 2개.
  - Empty: `projects.empty` + primary "주문 시스템으로 시작"(시트를 이름 `onboarding.project.defaultName`·설명 `onboarding.project.defaultDescription`으로 채워 연다). 온보딩에서 건너뛴 사용자가 여기서 만든다(SP-1).
  - Error (조회): 공통. 페이지 추가 로드 실패 → 목록 끝 "다시 불러오기" 행.
  - Error (저장): `400 VALIDATION_FAILED` field `repoUrl`(`URL`) → 그 필드 아래 인라인 `validation.url`, `NOT_BLANK_IF_PRESENT`(이름을 비움) → 이름 아래 `validation.required`. `422 SECRET_DETECTED_BLOCKED` → 시트 하단 인라인 `projects.secretBlocked`(이름·설명·스택 중 개인 키). `409 CONCURRENT_MODIFICATION` → 최신 값을 다시 읽어 시트를 채우고 토스트 `projects.reloaded`. `404 RESOURCE_NOT_FOUND`(다른 곳에서 삭제) → 시트 닫고 토스트 + 목록 재조회. 입력은 유지한다.
  - AI unavailable: 목록·등록·수정·삭제는 영향 없다. "이 프로젝트 작업 설명하기"만 비활성 + `ai.disabledReason`(§6.5). 이 화면에는 AI 배너를 두지 않는다.
  - Offline: 공통.
- **행동·검증**
  - §3.2: 이름 1~100, 설명 ≤ 1000, 저장소 주소 http(s)·≤ 500, 스택 ≤ 300. "저장"은 필수 입력 충족 + 변경이 있을 때만 활성. 입력 중 이탈 → §6.8.
  - 삭제 확인 대화상자 `projects.delete.*`: 이 프로젝트로 만든 Today 과제·코드 리뷰는 **남고 연결만 끊긴다**는 것, 지난 러버덕 대화도 남는다는 것, 되돌릴 수 없다는 것을 알린다. 지우는 것이 마지막 `ACTIVE` 프로젝트면 `projects.delete.lastActive`를 덧붙인다.
  - 상태를 `DONE`·`PAUSED`로 바꾸면 토스트 `projects.statusChanged`(다음 Today 계획부터 이 프로젝트로 과제를 만들지 않음). 이미 만든 과제는 그대로다.
  - 수정은 `updatedAt`을 바꾸므로 "Today 과제 대상"이 이 프로젝트로 옮겨 갈 수 있다. 저장 후 목록을 다시 읽어 라벨을 갱신한다.
- **문구**

| key | 문구 |
|---|---|
| `projects.title` | 사이드 프로젝트 |
| `projects.subtitle` | 배운 것을 적용하는 내 프로젝트예요. Today 프로젝트 과제는 '지금 만드는 것' 중 가장 최근에 고친 '진행 중' 프로젝트로 나와요. |
| `projects.todayTarget` | Today 과제 대상 |
| `projects.noActive` | '지금 만드는 것'으로 진행 중인 프로젝트가 없어서 Today가 프로젝트 과제를 제안하지 않아요. |
| `projects.updatedAt` | {date} 수정 |
| `projects.add` | 프로젝트 추가 |
| `projects.explain` | 이 프로젝트 작업 설명하기 |
| `projects.menu.edit` | 수정 |
| `projects.menu.pause` | 잠시 멈춤 |
| `projects.menu.activate` | 진행 중으로 |
| `projects.menu.done` | 완료로 |
| `projects.menu.delete` | 삭제 |
| `projects.edit.titleNew` | 프로젝트 추가 |
| `projects.edit.title` | 프로젝트 수정 |
| `projects.field.kind` | 종류 |
| `enum.SideProjectKind.SIDE` | 지금 만드는 것 |
| `enum.SideProjectKind.PAST_WORK` | 지난 경험 |
| `projects.kind.help` | 지난 경험은 Today 과제로 나오지 않고 기록만 남겨요. |
| `projects.kindChanged` | 다음 계획부터 반영돼요. 이미 만든 과제와 기록은 그대로예요. |
| `projects.field.name` | 이름 |
| `projects.field.description` | 설명 (선택) |
| `projects.field.repoUrl` | 저장소 주소 (선택) |
| `projects.repoUrl.note` | 주소는 기록용이에요. DevPilot은 저장소 내용을 가져오지 않아요. |
| `projects.field.stack` | 스택 (선택) |
| `projects.field.stack.hint` | 예: Spring Boot, PostgreSQL |
| `projects.field.status` | 상태 |
| `projects.save` | 저장 |
| `projects.saved` | 저장했어요. |
| `projects.created` | 프로젝트를 추가했어요. |
| `projects.statusChanged` | 다음 Today 계획부터 반영돼요. 이미 만든 과제는 그대로예요. |
| `projects.delete.title` | 이 프로젝트를 삭제할까요? |
| `projects.delete.body` | 이 프로젝트로 만든 Today 과제와 코드 리뷰는 남고 연결만 끊겨요. 지난 러버덕 대화도 남아요. **이 프로젝트의 결정·장애 기록은 함께 지워져요.** 삭제는 되돌릴 수 없어요. |
| `projects.delete.lastActive` | 진행 중인 프로젝트가 없으면 Today가 프로젝트 과제를 제안하지 않아요. |
| `projects.delete.confirm` | 삭제 |
| `projects.deleted` | 프로젝트를 삭제했어요. |
| `projects.noteCount` | 기록 {count}개 |
| `projects.openDetail` | 기록 보기 |
| `projects.empty` | 사이드 프로젝트가 없어요. 프로젝트가 있어야 Today가 배운 것을 적용하는 과제를 제안해요. |
| `projects.empty.start` | 주문 시스템으로 시작 |
| `projects.secretBlocked` | 개인 키(private key)로 보이는 내용이 있어 저장할 수 없어요. 해당 부분을 지워 주세요. |
| `projects.reloaded` | 다른 곳에서 바뀌어 최신 내용으로 다시 불러왔어요. |

#### SCR-PROJECT-DETAIL

- **목적**: 프로젝트 하나의 요약(종류 포함)과 **결정·장애 기록**을 보고, 기록을 Markdown으로 내려받거나 러버덕으로 설명한다(FR-29, PN-1~PN-4, AC-33). 프로젝트를 만들면서 내린 결정과 겪은 장애를 그 자리에서 남기고, 나중에 설명의 재료로 쓴다. **진입**: SCR-PROJECTS 카드 탭, SCR-TODAY `PROJECT_TASK` 카드의 "이 프로젝트 기록". **Sprint**: S3.
- **레이아웃** (`/projects/{sideProjectId}`)

```text
┌────────────────────────────────┐
│ ← 주문 시스템        [진행 중] ⋮ │  ⋮ = Markdown으로 내려받기
│ 지금 만드는 것                   │  kind
│ 회원가입 · 상품 · 주문 · 취소까지 │
│ 직접 만드는 학습용 백엔드        │
│ Spring Boot, PostgreSQL          │
│ github.com/example/order-service↗│
│ [수정] [이 프로젝트 작업 설명하기]│
│ ────────────────────────────── │
│ 기록 3개                        │
│ [전체✓] [결정] [장애]            │
│ ┌────────────────────────────┐ │
│ │ [장애] 10월 11일             │ │
│ │ 재고가 음수로 내려갔다        │ │
│ │ 동시에 주문 두 건이 들어오면… │ │  incidentSymptom 2줄
│ │ DATABASE.TRANSACTION       ⋮ │ │  ⋮ = 수정 / 이 기록 설명하기 / 삭제
│ └────────────────────────────┘ │
│ ┌────────────────────────────┐ │
│ │ [결정] 10월 9일              │ │
│ │ 주문 번호를 시퀀스 기반으로   │ │
│ │ yyyyMMdd + 일련번호 형식을…  │ │  decisionChoice 2줄
│ │ DATABASE.INDEX             ⋮ │ │
│ └────────────────────────────┘ │
│ ┌──────────────┬─────────────┐ │
│ │ + 결정 기록   │ + 장애 기록  │ │
│ └──────────────┴─────────────┘ │
└────────────────────────────────┘
```

- **규칙**
  - 상단은 `GET /side-projects/{sideProjectId}`의 요약이다. "수정"은 SCR-PROJECTS의 `ProjectEditSheet`를 그대로 연다(종류·이름·설명·저장소 주소·스택·상태). 삭제는 여기에 두지 않는다(목록에서 한다).
  - **프로젝트 종류**: 상태 배지 아래 `SideProjectKind` 라벨을 한 줄로 항상 보인다(§3.1 — `SIDE` "지금 만드는 것" / `PAST_WORK` "지난 경험"). 목록 카드와 달리 여기서는 두 값 모두 보인다. `PAST_WORK`이면 그 아래에 `projectNotes.pastWork.note`를 덧붙여 Today 과제로 나오지 않는다는 것을 알린다(I-23). 종류는 이 화면에서 "수정"으로 바꾼다.
  - **Markdown으로 내려받기**: 앱 바 `⋮` 메뉴 `projectNotes.export`. `GET /side-projects/{sideProjectId}/notes/export`(`05` §19.13)를 불러 `text/markdown`을 파일로 저장한다 — 파일명은 응답의 `Content-Disposition`(`notes-{sideProjectId}-{yyyyMMdd}.md`)을 그대로 쓴다. 저장 후 토스트 `projectNotes.export.done`과 "복사" 액션(본문을 클립보드로, SCR-EVIDENCE-LIST의 Markdown 내보내기와 같은 방식).
    - 기록이 0개여도 메뉴는 활성이다(서버가 제목만 있는 문서를 준다). 다만 0개면 먼저 토스트 `projectNotes.export.empty`를 보이고 내려받지 않는다 — 빈 파일을 만들지 않는다.
    - 내려받는 본문은 **저장된 마스킹본**이다. 메뉴 아래(또는 첫 내려받기 전 확인 없이 토스트로) 안내를 붙이지 않고, 대신 기록 작성 화면의 안내(SCR-PROJECT-NOTE-EDIT)가 이 역할을 한다.
    - Offline이면 메뉴 항목을 비활성한다(내려받기는 네트워크가 필요하다).
  - **기록에서 "러버덕으로 설명하기"**: 기록 카드 `⋮` 메뉴에 `projectNotes.menu.explain`을 둔다(S3, `rubber_duck` flag, AI 가능할 때만). → `/rubber-duck/new?targetType=PROJECT_WORK&targetId={sideProjectId}` + `skillCode`(기록의 `skillCode`가 있을 때만) + `extra`(기록 제목과 본문 첫 항목 — 시작 전 화면의 대상 카드에 쓴다, §3.16).
    - **대상은 기록이 아니라 프로젝트다** — `RubberDuckTargetType`에 기록 유형이 없고 새로 만들지도 않는다(`05` §9.5). 기록은 "무엇을 설명할지"를 고르는 입력일 뿐이라 서버로 가지 않는다.
    - AI를 쓸 수 없으면 메뉴 항목을 비활성하고 그 아래 사유 1줄(§6.5). 재현 잠금(`409 AI_ASSIST_LOCKED_FOR_REDO`)은 시작 요청에서만 알 수 있으므로 SCR-RUBBER-DUCK 시작 전 화면이 처리한다(§3.16).
  - 기록 목록은 `GET /side-projects/{sideProjectId}/notes?noteType=&cursor=`다. 서버 정렬(`occurredOn` DESC, `id` DESC)을 그대로 쓰고 스크롤 페이지네이션한다. 필터 칩 3개(전체·결정·장애)는 `noteType` query가 된다.
  - 카드는 유형 배지 + `occurredOn` + `title` + 본문 첫 항목 2줄 말줄임(`DECISION`은 `decisionChoice`, `INCIDENT`는 `incidentSymptom`) + skill 칩(있을 때)이다. 카드를 누르면 SCR-PROJECT-NOTE-EDIT(수정)로 간다.
  - 기록 카드 `⋮` 메뉴: "수정"(→ 편집 화면), "러버덕으로 설명하기"(위), "삭제"(확인 대화상자 → `DELETE …/notes/{noteId}` → 204 → 목록에서 제거).
  - 하단 버튼 둘은 각각 `/projects/{id}/notes/new?noteType=DECISION`·`…=INCIDENT`로 간다. **유형은 여기서 정해지고 이후 바뀌지 않는다**(PN-2).
  - 데스크톱(≥ 1024)은 왼쪽 열(35%)에 프로젝트 요약을 고정하고 오른쪽 열(65%)에 기록 목록을 둔다.
- **상태**
  - Loading: 요약 skeleton + 카드 skeleton 2개.
  - Empty: `projectNotes.empty` + 버튼 둘. 첫 기록을 권하는 한 줄 설명을 함께 둔다.
  - Error: 공통. `404 RESOURCE_NOT_FOUND`(다른 곳에서 프로젝트 삭제) → SCR-NOT-FOUND. 내려받기 실패는 토스트만(§5.1) — 화면 상태를 바꾸지 않는다.
  - AI unavailable: 이 화면은 AI를 쓰지 않는다(내려받기도 AI를 쓰지 않는다). 배너를 두지 않고 "이 프로젝트 작업 설명하기"와 기록 메뉴의 "러버덕으로 설명하기"만 비활성 + 사유 1줄(§6.5).
- **문구**

| key | 문구 |
|---|---|
| `projectNotes.title` | 기록 |
| `projectNotes.count` | 기록 {count}개 |
| `projectNotes.filter.all` | 전체 |
| `projectNotes.filter.decision` | 결정 |
| `projectNotes.filter.incident` | 장애 |
| `projectNotes.add.decision` | + 결정 기록 |
| `projectNotes.add.incident` | + 장애 기록 |
| `projectNotes.empty` | 아직 기록이 없어요. 무엇을 왜 골랐는지, 무엇이 어떻게 깨졌는지 지금 적어 두면 나중에 설명할 거리가 돼요. |
| `projectNotes.pastWork.note` | 지난 경험이라 Today 과제로는 나오지 않아요. 기록과 설명하기는 그대로 쓸 수 있어요. |
| `projectNotes.export` | Markdown으로 내려받기 |
| `projectNotes.export.done` | 파일로 저장했어요. |
| `projectNotes.export.copy` | 복사 |
| `projectNotes.export.empty` | 내려받을 기록이 아직 없어요. |
| `projectNotes.menu.edit` | 수정 |
| `projectNotes.menu.explain` | 러버덕으로 설명하기 |
| `projectNotes.menu.delete` | 삭제 |
| `projectNotes.delete.title` | 이 기록을 지울까요? |
| `projectNotes.delete.body` | 되돌릴 수 없어요. |
| `projectNotes.deleted` | 기록을 지웠어요. |

#### SCR-PROJECT-NOTE-EDIT

- **목적**: 결정 기록 또는 장애 기록 하나를 쓰고 고친다(FR-29, PN-1~PN-4). **진입**: SCR-PROJECT-DETAIL의 "+ 결정 기록"·"+ 장애 기록"(생성)과 기록 카드 탭(수정). **Sprint**: S3.
- **레이아웃 (장애 기록 생성)** (`/projects/{sideProjectId}/notes/new?noteType=INCIDENT`)

```text
┌────────────────────────────────┐
│ ✕  장애 기록                [저장]│
│ 제목 *                     12/200│
│ [재고가 음수로 내려갔다       ] │
│ 일어난 날 *                      │
│ [ 2026년 10월 11일        📅 ] │
│ 관련 기술 (선택)                 │
│ [ DATABASE.TRANSACTION    ▾ ] │
│ ────────────────────────────── │
│ 무엇이 잘못됐나요? *       88/4000│
│ [동시에 주문 두 건이 들어오면 ] │
│ [재고가 음수가 됐다.          ] │
│ 어떻게 찾았나요? *               │
│ [주문 목록에서 수량이 -1인 것을] │
│ 어떻게 고쳤나요? *               │
│ [재고 차감을 비관적 락으로 감쌌다]│
│ 다시 안 생기게 하려면? *          │
│ [동시 주문 테스트를 추가했다   ] │
│ 회사 소스·고객 정보는 적지 마세요.│
│ 상황과 판단만 적어요.             │
│ 비밀값은 저장 전에 가려요.        │
└────────────────────────────────┘
```

- **규칙**
  - 유형은 `noteType` query(생성) 또는 조회한 기록(수정)에서 오고 **화면에서 바꿀 수 없다**. 앱 바 제목이 유형이다. 수정 화면에는 유형을 바꾸는 입력이 없다(PN-2, `05` §19.11).
  - 본문 입력은 유형에 따라 다르다. 결정 기록은 `decisionChoice`("무엇을 골랐나요?")·`decisionOptions`("어떤 선택지가 있었나요?")·`decisionRationale`("왜 그것을 골랐나요?") 셋, 장애 기록은 `incidentSymptom`·`incidentDetection`·`incidentFix`·`incidentPrevention` 넷이다. **전부 필수**이고 각 1~4000자(§3.2). 유형에 맞지 않는 항목은 화면에 없다.
  - `occurredOn`은 필수이고 기본값은 오늘(plan-day)이다. 날짜 선택기는 오늘 이하만 연다.
  - 관련 기술은 선택이다. `GET /skills/tree`의 활성 skill을 검색해 고르고, 지우면 연결이 끊긴다(`skillCode: ""`). 아래에 `projectNote.skill.help`("고르면 나중에 이 기술의 학습 기록을 만들 때 함께 보여요.")를 둔다.
  - 저장: 생성은 `POST /side-projects/{id}/notes`(IK) → 201 → 상세로 돌아가 목록 맨 위 갱신. 수정은 `PATCH …/notes/{noteId}`(바뀐 필드 + `version`) → 200. `409 CONCURRENT_MODIFICATION`이면 최신 값을 다시 읽어 폼을 채우고 토스트(`projectNote.reloaded`), 입력은 유지한다.
  - "저장"은 필수 입력이 모두 찼고 변경이 있을 때만 활성이다. 입력 중 이탈은 §6.8.
  - **무엇을 적지 않는지 먼저 알린다.** 본문 입력 영역 아래(저장 버튼 위)에 `projectNote.scopeNote` = **"회사 소스·고객 정보는 적지 마세요. 상황과 판단만 적어요."** 를 **항상** 둔다. 마스킹 안내(`projectNote.maskingNote`)보다 위에 두고 접지 않는다 — 마스킹은 실수를 줄이는 장치이고, 이 안내는 애초에 무엇을 쓸지를 정한다. `PAST_WORK` 프로젝트의 기록도 같은 안내를 받는다(오히려 이쪽이 더 필요하다).
  - `422 SECRET_DETECTED_BLOCKED` → 폼 하단 인라인 `projectNote.secretBlocked`. 400 `VALIDATION_FAILED`의 `errors[]`는 필드별 인라인(`VALUE_REQUIRED`는 `validation.required`).
- **컴포넌트**: `NoteTypeHeader`, `DatePickerField`, `SkillPickerField`, `LongTextField`(글자 수 카운터), `UnsavedChangesGuard`.
- **문구**

| key | 문구 |
|---|---|
| `projectNote.title.decision` | 결정 기록 |
| `projectNote.title.incident` | 장애 기록 |
| `projectNote.field.title` | 제목 |
| `projectNote.field.occurredOn` | 일어난 날 |
| `projectNote.field.skill` | 관련 기술 (선택) |
| `projectNote.skill.help` | 고르면 나중에 이 기술의 학습 기록을 만들 때 함께 보여요. |
| `projectNote.decision.choice` | 무엇을 골랐나요? |
| `projectNote.decision.options` | 어떤 선택지가 있었나요? |
| `projectNote.decision.rationale` | 왜 그것을 골랐나요? |
| `projectNote.incident.symptom` | 무엇이 잘못됐나요? |
| `projectNote.incident.detection` | 어떻게 찾았나요? |
| `projectNote.incident.fix` | 어떻게 고쳤나요? |
| `projectNote.incident.prevention` | 다시 안 생기게 하려면? |
| `projectNote.scopeNote` | 회사 소스·고객 정보는 적지 마세요. 상황과 판단만 적어요. |
| `projectNote.maskingNote` | 비밀값은 저장 전에 가려요. |
| `projectNote.save` | 저장 |
| `projectNote.saved` | 기록을 저장했어요. |
| `projectNote.secretBlocked` | 개인 키(private key)로 보이는 내용이 있어 저장할 수 없어요. 해당 부분을 지워 주세요. |
| `projectNote.reloaded` | 다른 곳에서 바뀌어 최신 내용으로 다시 불러왔어요. |

### 3.17 오늘의 팁 · 용어 사전

짧게 읽는 것(팁)과 말을 고르는 것(용어)을 맡는 화면이다. 본문은 모두 **콘텐츠**이고 DB에 없다(ADR-041, `19` §3.9~§3.10). 네 화면 모두 **AI를 호출하지 않는다** — `05` §20의 endpoint는 registry를 읽고 사용자별 상태(`user_daily_tip`, 복습 카드)만 쓴다. 그래서 AI 불가 배너를 두지 않고, AI 상태와 상관없이 그대로 쓸 수 있다(§6.5). 모든 사용자가 같은 팁·용어 목록을 본다(소유 리소스가 아니다, `05` §20.4·§20.5).

#### SCR-TIP-DETAIL

- **목적**: 팁 하나를 **증상 → 원인 → 예제 → 확인할 곳 → 5분 실험** 순서로 읽고, 읽은 뒤 "알고 있었어요 / 새로 알았어요 / 직접 해 볼래요" 중 하나를 고른다(BL-TIP-01~05, `06` §5.12). **진입**: SCR-TODAY 오늘의 팁 카드 "자세히"(`?from=today`), SCR-TIPS 목록 행. **Sprint**: S3 (`tips` flag).
- **레이아웃** (`/tips/{tipKey}`)

```text
┌────────────────────────────────┐
│ ← 오늘의 팁                      │
│ [로그] [실무] · 약 3분            │  series · level · estimatedMinutes
│ 로그 레벨은 언제 무엇을 쓰나      │  title
│ ────────────────────────────── │
│ 이런 걸 보게 돼요                 │  symptom
│ 장애가 났는데 로그에는 INFO 한   │
│ 줄만 남아 있고, 평소에는 DEBUG가 │
│ 초당 수천 줄씩 쌓인다.            │
│ 왜 그런가요                       │  cause
│ 레벨은 "얼마나 자세한가"가 아니라 │
│ "누가 언제 보는가"로 나눈다. …    │
│ ▾ 예제                            │  example (있을 때, 기본 접힘)
│ 어디를 보면 되나요                │  whereToLook
│ 기본 레벨 설정과, 예외를 잡는     │
│ 자리에서 어떤 레벨로 남기는지     │
│ 두 곳을 본다.                     │
│ 5분 실험                          │  experiment (있을 때)
│ 레벨을 한 단계 올리고 같은 요청을 │
│ 한 번 보내 어떤 줄이 사라지는지   │
│ 본다.                             │
│ 공식 문서 ↗                       │  sourceUrl (있을 때)
│ 관련 기술: Spring Boot 로깅 >     │  skills[] → /skills/{id}
│ ────────────────────────────── │
│ 읽고 나서                         │
│ [알고 있었어요][새로 알았어요]     │
│ [직접 해 볼래요]                  │
└────────────────────────────────┘
```

- **규칙**
  - 다섯 영역의 **순서는 고정이다**(증상 → 원인 → 예제 → 확인할 곳 → 5분 실험). 증상을 먼저 두는 이유는 "이걸 본 적 있다"에서 시작해야 원인을 읽을 이유가 생기기 때문이다.
  - `example`은 코드라 기본 접힘이다(U-7). 펼치면 monospace로 보이고 가로 스크롤을 허용한다(A-11). `null`이면 영역 전체를 숨긴다.
  - `experiment`가 `null`이면 "5분 실험" 영역을 숨긴다. `sourceUrl`이 `null`이면 링크를 숨긴다. 콘텐츠 규칙상 **둘 중 하나는 반드시 있다**(`19` §3.9 CV-91).
  - `sourceUrl`은 호스트명 + `↗`로 보이고 새 탭으로 연다(사용자 브라우저가 여는 것이고 서버는 그 주소를 가져오지 않는다 — SCR-PROJECTS `repoUrl`과 같은 원칙).
  - `skills[]`는 칩으로 보이고 누르면 `/skills/{skillId}`로 간다. 빈 배열이면 줄을 숨긴다.
  - `series`·`level` 배지는 §3.1 라벨을 쓴다. 색으로만 구분하지 않는다(A-3).
- **읽고 나서 고르기**
  - 칩 3개는 `TipFeedback` 라벨이다. 하나를 누르면 `POST /tips/{tipKey}/feedback` `{feedback}`(IK)을 보내고, 성공하면 고른 칩만 선택 상태로 두고 나머지를 비활성한다.
  - **한 번 고르면 바꿀 수 없다.** 서버가 덮어쓰지 않고 현재 값을 200으로 돌려주므로(`05` §20.3) 이미 값이 있으면 화면도 그 값을 선택 상태로 열고 칩을 모두 비활성한다. 아래에 `tip.feedback.locked`를 한 줄 둔다.
  - 고른 뒤 결과 안내: `LEARNED` → 토스트 `tip.feedback.learned` + "복습하러 가기"(→ `/review`), `WILL_TRY` → 토스트 `tip.feedback.willTry`(다음 날 Today에 "직접 해 보기"로 올라온다), `KNEW_IT` → 토스트 `tip.feedback.knewIt`(다시 제안하지 않는다).
  - 아직 받아 본 적 없는 팁은 고를 수 없다(서버 404, `05` §20.3 2단계). 그런 팁의 상세에서는 칩 대신 `tip.feedback.notShownYet`을 보인다.
- **데이터**

| 시점 | API |
|---|---|
| 진입(`?from=today` 또는 오늘의 팁) | `GET /tips/today` — 본문 전체(`DailyTipView`)와 현재 `feedback` |
| 칩 선택 | `POST /tips/{tipKey}/feedback` `{feedback}` (IK) → 201(처음) / 200(이미 있음) `DailyTipView` |

  - **데이터 한계**: 팁 본문 전체를 주는 endpoint는 `GET /tips/today` 하나다(`05` §20에 팁 단건 조회가 없다). 그래서 이 화면은 **오늘의 팁**에만 본문을 채운다. 경로의 `tipKey`가 오늘의 팁과 다르면 전체 영역에 `tip.detail.notToday` + "오늘의 팁 보기"(→ `/tips/{오늘의 tipKey}`)와 "목록으로"(→ `/tips`)를 두고, 요약(제목·증상)만 아는 경우에는 그 두 줄을 함께 보인다. SCR-NOT-FOUND로 보내지 않는다 — 없는 팁이 아니라 지금 본문을 받을 수 없는 팁이다.
- **상태**
  - Loading: 제목 + 본문 skeleton.
  - Empty: 해당 없음.
  - Error: `404 RESOURCE_NOT_FOUND`(오늘 더 보여 줄 팁이 없음) → 전체 영역 `tip.detail.none` + "Today로". `400 VALIDATION_FAILED`(`Pattern` — 경로 형식이 틀림) → SCR-NOT-FOUND. 그 외 공통.
  - 칩 선택 실패: 토스트(§5.1) + 칩을 원래대로 되돌린다. `404`면 `tip.feedback.notShownYet`으로 바꾼다.
  - AI: 해당 없다(이 화면은 AI를 쓰지 않는다). Offline: 공통 — 읽기는 메모리의 값으로 되고 칩은 비활성.
- **행동·검증**: 칩은 한 번에 하나만 선택된다. 전송 중에는 세 칩 모두 비활성. 뒤로 가면 SCR-TODAY가 팁 카드를 다시 읽어 라벨을 갱신한다. `?from=today`이고 히스토리가 없으면 `←`는 `/today`로 간다.
- **문구**

| key | 문구 |
|---|---|
| `tip.detail.title` | 오늘의 팁 |
| `tip.detail.symptom` | 이런 걸 보게 돼요 |
| `tip.detail.cause` | 왜 그런가요 |
| `tip.detail.example` | 예제 |
| `tip.detail.whereToLook` | 어디를 보면 되나요 |
| `tip.detail.experiment` | 5분 실험 |
| `tip.detail.source` | 공식 문서 |
| `tip.detail.skills` | 관련 기술 |
| `tip.detail.feedback.title` | 읽고 나서 |
| `tip.feedback.locked` | 고른 답은 바꾸지 않아요. |
| `tip.feedback.learned` | 복습 카드를 만들었어요. 내일부터 복습에 나와요. |
| `tip.feedback.willTry` | 내일 Today에 '직접 해 보기'로 올려 둘게요. |
| `tip.feedback.knewIt` | 이 팁은 다시 보여 주지 않을게요. |
| `tip.feedback.notShownYet` | 아직 받아 본 팁이 아니라 여기서는 고를 수 없어요. |
| `tip.detail.notToday` | 지금은 오늘 받은 팁만 자세히 볼 수 있어요. |
| `tip.detail.none` | 오늘 보여 드릴 팁이 없어요. |

#### SCR-TIPS

- **목적**: 지금까지의 팁을 시리즈·난이도로 훑는다. 짧은 시간이 났을 때 하나씩 읽는 자리다(BL-TIP-01~05). **진입**: SCR-MORE "오늘의 팁", SCR-TIP-DETAIL "목록으로". **Sprint**: S3 (`tips` flag).
- **레이아웃**

```text
┌────────────────────────────────┐
│ ← 팁                             │
│ [시리즈: 전체 ▾] [난이도: 전체 ▾] │
│ ┌────────────────────────────┐ │
│ │ [로그] 로그 레벨은 언제 무엇을 │ │
│ │ 쓰나                  약 3분 │ │
│ │ 장애가 났는데 로그에는 INFO   │ │  symptom 2줄
│ │ 한 줄만 남아 있고…            │ │
│ │ 새로 알았어요                 │ │  feedback (있을 때)
│ └────────────────────────────┘ │
│ …                                │
│ ┌────────────────────────────┐ │
│ │        더 보기               │ │  nextCursor 가 있을 때
│ └────────────────────────────┘ │
└────────────────────────────────┘
```

- **규칙**
  - 행은 `TipSummaryView`다: `series` 배지 + `title` + `약 {estimatedMinutes}` + `symptom` **2줄 말줄임** + 이미 고른 `feedback` 라벨(없으면 줄 생략).
  - 필터는 둘이다 — **시리즈**(`TipSeries` 7개 + "전체")와 **난이도**(`TipLevel` 2개 + "전체"). 고르면 쿼리(`?series=&level=`)와 요청 파라미터에 함께 반영하고, 바꾸면 cursor를 버리고 첫 페이지부터 다시 읽는다.
  - 정렬은 서버 정렬(`tipKey` ASC)을 그대로 쓴다. 클라이언트가 다시 정렬하지 않는다.
  - **"더 보기"는 버튼이다.** 스크롤 자동 로드가 아니라 목록 끝의 `nextCursor` 버튼으로 다음 페이지를 읽는다 — 짧게 읽는 목록이라 끝이 있는 편이 낫다. (스크롤 자동 로드를 쓰는 목록과 다르다는 뜻이고, 나머지 cursor 규칙은 §6.6과 같다.)
  - 행을 누르면 SCR-TIP-DETAIL로 간다. **본문은 오늘의 팁에만 있으므로**(위 데이터 한계) 오늘의 팁이 아닌 행에는 `tips.list.todayOnly`를 한 줄 보이고, 그래도 열면 상세가 `tip.detail.notToday` 상태로 열린다. 오늘의 팁 행에는 `tips.list.todayBadge` 배지를 붙인다.
  - 은퇴한 팁은 목록에 없다(`retired = false`만, `05` §20.4). 이미 본 팁은 목록에 남는다.
- **데이터**: 진입·필터 변경 `GET /tips?series=&level=&limit=&cursor=` → `CursorPage<TipSummaryView>`. 오늘의 팁 배지를 위해 진입 시 `GET /tips/today`를 한 번 더 읽는다(404면 배지 없이 목록만 보인다).
- **상태**: Loading — 행 skeleton 4개. Empty — `tips.list.empty`(필터 결과 0건이면 `tips.list.emptyFilter` + "필터 지우기"). Error — 공통. 페이지 추가 로드 실패 → 목록 끝 "다시 불러오기" 행. `400 INVALID_CURSOR` → §5.1대로 첫 페이지부터 다시. Offline — 공통. AI — 해당 없음.
- **문구**

| key | 문구 |
|---|---|
| `tips.list.title` | 팁 |
| `tips.list.filter.series` | 시리즈: {name} |
| `tips.list.filter.level` | 난이도: {name} |
| `tips.list.filter.all` | 전체 |
| `tips.list.filter.clear` | 필터 지우기 |
| `tips.list.more` | 더 보기 |
| `tips.list.todayBadge` | 오늘의 팁 |
| `tips.list.todayOnly` | 자세한 내용은 오늘의 팁으로 받은 날 볼 수 있어요. |
| `tips.list.empty` | 아직 팁이 없어요. |
| `tips.list.emptyFilter` | 조건에 맞는 팁이 없어요. |

#### SCR-TERMS

- **목적**: 말이 헷갈릴 때 용어를 찾는다 — **대표 표기·영어·다른 표기 어느 쪽으로도** 찾을 수 있다(BL-TRM-01~04, FR-11). **진입**: SCR-REVIEW-HOME "용어 찾기", SCR-MORE "용어 사전", SCR-TERM-DETAIL "헷갈리는 짝". **Sprint**: S3 (`terms` flag).
- **레이아웃**

```text
┌────────────────────────────────┐
│ ← 용어                           │
│ ┌────────────────────────────┐ │
│ │ 🔍 칼럼                      │ │  q (대표 표기·영어·다른 표기)
│ └────────────────────────────┘ │
│ [기술: 전체 ▾]                   │
│ ┌────────────────────────────┐ │
│ │ 컬럼  column        [기본]   │ │  representative · english · level
│ │ 테이블에서 같은 뜻과 타입을   │ │  definition 2줄
│ │ 가진 값들이 세로로 모인 자리다│ │
│ │ 복습 카드 있음               │ │  cardCreated = true 일 때
│ └────────────────────────────┘ │
│ …                                │
│ ┌────────────────────────────┐ │
│ │        더 보기               │ │
│ └────────────────────────────┘ │
├────────────────────────────────┤
│  Today   Review   Plan   More  │
└────────────────────────────────┘
```

- **규칙**
  - 검색은 `q`(0~100자, §3.2)다. 입력을 **400ms debounce**하고 앞뒤 공백을 지워 보낸다. 서버가 `representative`·`english`·`aliases[]`를 부분 일치·대소문자 무시로 찾으므로(`05` §20.5) 화면은 **"칼럼"으로 찾아도 "컬럼"이 나온다**는 것을 검색칸 아래 `terms.search.help`로 알린다.
  - 행은 `TermSummaryView`다: `representative`(굵게) + `english` + `level` 배지 + `definition` 2줄 말줄임 + `cardCreated = true`면 `terms.list.hasCard` 한 줄.
  - 찾은 말이 대표 표기가 아닐 때(예: "칼럼"으로 찾음) 행은 **대표 표기로 보인다** — 저장소가 한 표기만 쓰기 때문이다(`19` §3.10). 어떤 말로 찾았는지는 강조하지 않는다.
  - 기술 필터는 `skillId`(칩 → `SkillPicker`)다. 검색어와 AND로 걸린다. 둘 다 비면 전체 목록이다.
  - `q`·`skillId`는 쿼리 파라미터로 유지해 뒤로가기·새로고침에도 남는다.
  - 정렬은 서버 정렬(`termKey` ASC)을 그대로 쓴다. "더 보기" 버튼은 SCR-TIPS와 같다. 은퇴한 용어는 검색되지 않는다(`05` §20.5).
  - 행을 누르면 `/terms/{termKey}`로 간다.
- **데이터**: 진입·검색어·필터 변경 `GET /terms?q=&skillId=&limit=&cursor=` → `CursorPage<TermSummaryView>`.
- **상태**: Loading — 행 skeleton 5개(검색 중에는 기존 목록을 유지하고 앱 바 아래 2px 진행 막대, §3.3). Empty — 검색어가 있으면 `terms.list.emptyQuery`(+ "검색어 지우기"), 없으면 `terms.list.empty`. Error·Offline — 공통. AI — 해당 없음.
- **행동·검증**: 검색칸은 한 줄 입력이라 Enter로 즉시 검색한다(A-7). IME 조합 중에는 무시한다(A-13). `✕`로 검색어를 지우면 전체 목록으로 돌아간다.
- **문구**

| key | 문구 |
|---|---|
| `terms.list.title` | 용어 |
| `terms.search.hint` | 용어를 찾아보세요 |
| `terms.search.help` | 영어나 다른 표기로 찾아도 대표 표기로 보여 드려요. |
| `terms.search.clear` | 검색어 지우기 |
| `terms.list.filter.skill` | 기술: {name} |
| `terms.list.hasCard` | 복습 카드 있음 |
| `terms.list.more` | 더 보기 |
| `terms.list.empty` | 아직 등록된 용어가 없어요. |
| `terms.list.emptyQuery` | '{query}'로 찾은 용어가 없어요. |

#### SCR-TERM-DETAIL

- **목적**: 용어 하나의 **대표 표기·영어·다른 표기·정의·실무 예문·헷갈리는 짝**을 보고, 원하면 복습 카드로 만든다(BL-TRM-01~04, FR-11). **진입**: SCR-TERMS 행, 다른 용어의 "헷갈리는 짝". **Sprint**: S3 (`terms` flag).
- **레이아웃** (`/terms/{termKey}`)

```text
┌────────────────────────────────┐
│ ← 용어                           │
│ 컬럼                      [기본] │  representative · level
│ column                           │  english
│ 이렇게도 불러요: 열, 칼럼         │  aliases (있을 때)
│ ────────────────────────────── │
│ 뜻                               │
│ 테이블에서 같은 뜻과 타입을 가진 │
│ 값들이 세로로 모인 자리다.        │
│ 이렇게 써요                       │
│ 주문 테이블에 상태 컬럼을 하나    │
│ 더했다.                           │
│ 헷갈리는 짝                       │  confusableWith (있을 때)
│ ┌────────────────────────────┐ │
│ │ 필드  field               > │ │
│ └────────────────────────────┘ │
│ 관련 기술: 데이터 모델링 >        │
│ 공식 문서 ↗                       │
│ ────────────────────────────── │
│ ┌────────────────────────────┐ │
│ │      복습 카드 만들기         │ │
│ └────────────────────────────┘ │
│ 앞뒤 두 장을 만들어요. 표기를 보고│
│ 뜻, 뜻을 보고 표기를 떠올려요.    │
└────────────────────────────────┘
```

- **규칙**
  - **대표 표기가 제목이다.** `aliases`는 "이렇게도 불러요" 한 줄로 내리고 제목과 같은 크기로 쓰지 않는다 — 어떤 표기를 쓸지 정해 주는 것이 이 화면의 일이다(`19` §3.10).
  - `aliases`가 비면 그 줄을 숨긴다. `confusableWith`가 비면 그 영역을 숨긴다.
  - "헷갈리는 짝" 행은 `TermRefView`(대표 표기 + 영어)이고 누르면 그 용어의 상세로 간다(같은 화면을 새 라우트로 쌓는다). 뒤로가기로 돌아온다.
  - `skills[]`는 칩 → `/skills/{skillId}`. `sourceUrl`은 호스트명 + `↗`(새 탭, 서버는 가져오지 않는다).
  - 은퇴한 용어도 이 화면으로는 열린다(`05` §20.6). 그때는 제목 아래 `term.detail.retired`를 한 줄 둔다.
- **복습 카드 만들기**
  - primary "복습 카드 만들기" → `POST /terms/{termKey}/card`(IK, body 없음). 버튼 아래에 `term.card.help`로 **앞뒤 두 장**이 생긴다는 것을 미리 알린다(`05` §20.7).
  - 응답의 `cards[]`를 버튼 자리 아래 목록으로 보인다(`conceptKey`로 방향을 구분해 `term.card.forward`("표기 → 뜻") / `term.card.reverse`("뜻 → 표기") 라벨 + `다음 복습 {date}`). `createdCount > 0`이면 토스트 `term.card.created`, `0`이면 `term.card.already`.
  - 이미 두 장이 있으면(진입 시 `TermView.cards`가 2건) 버튼을 "복습 카드 보기"(→ `/review/items?skillId={skills[0].id}`)로 바꾼다. 한 장만 있으면 버튼은 그대로 두고(나머지 한 장을 만든다) 아래에 있는 카드를 보인다.
  - `createdCount = 0`이고 `cards`가 비면(활성 기술이 없는 용어) 버튼을 비활성하고 `term.card.noSkill`을 보인다.
  - 만든 카드는 **복습 화면에서 다른 카드와 똑같이 나온다**. 출처 라벨은 `TERM` "용어 사전"이다(§3.6 SCR-REVIEW-ITEMS).
- **데이터**

| 시점 | API |
|---|---|
| 진입 | `GET /terms/{termKey}` → `TermView`(`confusableWith` 펼침, 이 사용자의 `cards` 포함) |
| "복습 카드 만들기" | `POST /terms/{termKey}/card` (IK, body 없음) → 201(새로 만듦) / 200(이미 있음) `TermCardResponse` |

- **상태**: Loading — 제목·본문 skeleton. Empty — 해당 없음. Error — `400 VALIDATION_FAILED`(`Pattern`)·`404 RESOURCE_NOT_FOUND` → SCR-NOT-FOUND. 카드 만들기 실패는 토스트(§5.1) + 버튼 복구. Offline — 공통(버튼 비활성). AI — 해당 없음.
- **행동·검증**: 버튼은 요청 중 비활성(§6.6 중복 탭 방지). 같은 행동을 재시도하면 같은 IK를 쓴다.
- **문구**

| key | 문구 |
|---|---|
| `term.detail.title` | 용어 |
| `term.detail.aliases` | 이렇게도 불러요 |
| `term.detail.definition` | 뜻 |
| `term.detail.example` | 이렇게 써요 |
| `term.detail.confusable` | 헷갈리는 짝 |
| `term.detail.skills` | 관련 기술 |
| `term.detail.source` | 공식 문서 |
| `term.detail.retired` | 지금은 이 표기 대신 다른 말을 써요. |
| `term.card.button` | 복습 카드 만들기 |
| `term.card.help` | 앞뒤 두 장을 만들어요. 표기를 보고 뜻, 뜻을 보고 표기를 떠올려요. |
| `term.card.forward` | 표기 → 뜻 |
| `term.card.reverse` | 뜻 → 표기 |
| `term.card.created` | 복습 카드를 만들었어요. 내일부터 복습에 나와요. |
| `term.card.already` | 이미 만들어 둔 카드가 있어요. |
| `term.card.view` | 복습 카드 보기 |
| `term.card.noSkill` | 이 용어에 연결된 기술이 없어 카드를 만들 수 없어요. |

---

## 4. 핵심 흐름

표기: `→` 다음 단계, `[서버]` 서버 상태 변화, `IK` = 새 `Idempotency-Key` 생성(§6.6). 모든 쓰기 요청은 성공 후 관련 provider를 무효화(`ref.invalidate`)해 다시 읽는다.

### 4.1 최초 로그인 → 온보딩 → 첫 Today

| # | 사용자 | 화면 | API / 클라이언트 동작 | [서버] |
|---|---|---|---|---|
| 1 | 앱 URL 접속 | SCR-LOGIN (redirect 1) | 세션 없음 | — |
| 2 | 이메일 입력 → "로그인" | SCR-LOGIN | `POST /api/v1/dev/token {email}` | allowlist 확인 → 서명 토큰 발급 (`supabase` 모드면 GitHub OAuth) |
| 3 | — | SCR-AUTH-CALLBACK | `GET /me` | JIT: allowlist 통과 → `app_user` INSERT(기본값). 실패 시 403 → SCR-NOT-ALLOWED |
| 4 | — | → `/onboarding/goal` | `onboardingCompleted=false` | — |
| 5 | 표시 이름·목표일 입력(학습 트랙은 Java 백엔드 고정) → 다음 | 1단계 | draft 저장 | — |
| 6 | 시간 입력 → 다음 | 2단계 | draft 저장 | — |
| 7 | "짧은 진단으로 시작"(기본) 그대로 → 다음 | 3단계 | draft 저장(`runDiagnostic=true`) | — |
| 8 | 이름 "주문 시스템" 그대로 → "계획 만들기" | 4단계 | `POST /onboarding` (IK) `{…, runDiagnostic: true, selfAssessments: [], sideProject: {name: "주문 시스템", description}}` | 한 트랜잭션: learning_goal, `user_skill_state`(진단 모드 — `self_assessed_level` 모두 null), plan v1(ACTIVE, 9개 milestone)·plan_skill_target, seed review_item 복사(하루 5장 분산), `side_project`(ACTIVE), `onboarding_completed_at`, 오늘 snapshot → 커밋 후 `suggestedDiagnostics`(카테고리당 1개, 최대 5개) |
| 9 | — | 5단계 | `meProvider` 갱신, `GET /plans/active`로 milestone 표시, draft 삭제. 진단 카드 "짧은 진단 {n}문제" | — |
| 10 | "진단 시작" | SCR-DIAGNOSTICS | 제안 목록 표시 → "풀기" → §4.6 | — |
| 11 | 진단을 풀었거나 "나중에 하고 시작" | SCR-TODAY | `GET /today` → `404 TODAY_NOT_GENERATED` → 생성 전 레이아웃. `GET /diagnostics/suggestions`(남은 진단이 있으면 카드) | — |
| 12 | 시간·컨디션 확인 → "오늘 계획 만들기" | SCR-TODAY | `POST /today/generate` `{availableMinutes, energyLevel, force:false}` (IK) | `daily_plan` + main task(PLANNED) + REVIEW task(due 있으면). 첫날은 seed 카드 5장이 due. `deadline_risk`는 S2부터 계산 |
| 13 | — | 생성 후 레이아웃 | 응답 표시 | — |

분기:
- 3단계에서 "진단 건너뛰고 직접 고르기": 7에서 13개 카테고리 수준을 고르고 8에 `runDiagnostic: false, selfAssessments: [13개]`를 보낸다([서버] 카테고리 값을 `self_assessed_level`에 전파). 5단계에는 진단 카드가 없고 primary는 "그대로 시작"이다. 자기평가 3 이상인 분야는 Today에서 진단 카드로 제안된다(§4.6).
- 4단계에서 "건너뛰기" → 대화상자 "건너뛰고 계획 만들기" → 8을 `sideProject: null`로 보낸다([서버] `side_project` 없음 → planner가 `PROJECT_TASK`를 제안하지 않음, SP-1). 5단계에 `onboarding.plan.noProject`.
- `diagnostics` flag가 꺼진 빌드(S1~S2): 3단계는 자기평가만 있다(위 첫 분기와 같음).

실패 분기: 8에서 `VALIDATION_FAILED` → 오류 필드가 있는 단계로 이동(`sideProject.*` → 4단계, `selfAssessments` → 3단계, `learningGoal.*` → 1단계). 네트워크 오류 → 같은 IK로 재시도(§6.6). `ONBOARDING_ALREADY_COMPLETED`(다른 탭에서 완료) → `meProvider` 갱신 → `/today`.

### 4.2 Today → 세션 시작 → 완료

| # | 사용자 | API | [서버] |
|---|---|---|---|
| 1 | "시작" | `PATCH /today/tasks/{mainId}` `{status: IN_PROGRESS, version}` | task `PLANNED → IN_PROGRESS` |
| 2 | — | 진행 중 세션이 없거나 그 `learningTaskId ≠ mainId`면 `POST /learning-sessions` `{learningTaskId: mainId}` (IK) | (기존 세션 ABANDONED) session `IN_PROGRESS`, `SESSION_STARTED` 이벤트 |
| 3a | (READING·EXPLAIN·PROJECT_TASK·RECALL) Today에서 공부 | 카드에 "진행 중 · n분째" | — |
| 3b | (CHALLENGE) 문제 화면으로 이동 | `/training/challenges/{challengeId}?taskId=` → §4.5 | — |
| 3c | (COACH_REVIEW) 코드 리뷰 요청으로 이동 | `/coach/new?taskId=` → §4.7 | — |
| 3d | (READ_CODE) 코드 읽기 화면으로 이동 | `/today/read/{readingKey}?taskId=` → §4.15 | — |
| 4 | "완료" 또는 Training/Coach의 "Today로 돌아가 완료하기" | `CompleteSessionSheet` 열기(기본 = 경과 분) | — |
| 5 | 시간·회고 확인 → "완료 기록" | `POST /learning-sessions/{sessionId}/complete` `{actualMinutes, selfReflection}` (IK) | session `COMPLETED`, `SESSION_COMPLETED` 이벤트 |
| 6 | — | `PATCH /today/tasks/{mainId}` `{status: COMPLETED, version}` | task `COMPLETED`, `completed_at` |
| 7 | — | `GET /today` 갱신 → "오늘의 핵심을 마쳤어요" | — |

- 5 성공 후 6이 실패하면: `GET /today` → 새 `version`으로 6을 1회 재시도 → 그래도 실패하면 토스트 `common.error.retry` 버튼으로 6만 다시 시도한다(세션 완료는 반복하지 않는다).
- "여기까지 기록": 5와 같되(0분이면 `…/abandon`) 6의 status는 `DEFERRED`.
- `READ_CODE`의 6이 `409 INVALID_STATE_TRANSITION`이면(RC-1 — 이 task의 러버덕이 `COMPLETED`가 아님) 재시도하지 않고 토스트 `today.readCode.needDuck` + SCR-READ-CODE로 안내한다. task는 `IN_PROGRESS`로 남고, 러버덕을 마친 뒤 Today "완료"는 진행 중 세션이 없으면 5를 건너뛰고 6만 보낸다. 클라이언트는 러버덕 `COMPLETED`를 확인했을 때만 "완료"를 보이므로(§3.5) 드문 경우다.
- "오늘은 건너뛰기"(PLANNED): `PATCH … {status: SKIPPED}` → 토스트 "되돌리기"(→ `PLANNED`).
- `mainTask = null`(후보 없음): 1~7 대신 복습 줄과 "계획 조정"만 보인다(§3.5).
- 재생성(§3.5 SCR-TODAY 행동 규칙): main `IN_PROGRESS`에서 `force=true`면 [서버] 기존 main `DEFERRED` → 새 main INSERT. 클라이언트는 진행 중 세션을 직접 닫지 않는다. 새 main의 "시작"에서 세션의 `learningTaskId`가 다르므로 새 세션을 시작하고, 서버가 기존 세션을 ABANDONED로 닫는다(I-05).

### 4.3 복습 세션

| # | 사용자 | API / 클라이언트 | [서버] |
|---|---|---|---|
| 1 | Review 탭 또는 Today 복습 줄 | `GET /reviews/due` (세션 동안 고정) | — |
| 2 | 카드 1: 떠올리고(선택) 답 입력 | 표시 시각 기록 | — |
| 3 | (선택) "힌트 보기" | `rubric[0]` 표시, `hintLevel=CONCEPT_HINT` | — |
| 4 | "답 확인" 또는 `Space` | 정답·핵심 포인트 표시 | — |
| 5 | 평가 버튼(예: 알맞음) | (첫 평가이고 진행 중 세션 없음) `taskId`가 PLANNED면 `PATCH /today/tasks/{taskId}` → `IN_PROGRESS`, `POST /learning-sessions` (IK) | session 시작 |
| 6 | — | `POST /reviews/{id}/answer` `{answerText, selfRating: GOOD, hintLevel, responseSeconds, wasVariant, evaluate}` (IK) | `FinalRatingPolicy` → `RuleBasedV1Scheduler` → review_item 갱신(due, interval), review_answer INSERT, `REVIEW_ANSWERED` → skill updater. 연속 실패 ≥ 2면 variant PENDING, ≥ 4면 SUSPENDED |
| 7 | — | 조정 토스트(`adjustedBy`, 다음 복습일) + 다음 카드 | — |
| 8 | 마지막 카드 후 | 요약 화면 | — |
| 9 | "완료 기록" | `POST /learning-sessions/{id}/complete` (IK) → `taskId`면 `PATCH … COMPLETED` | session·task 완료 |

- 6에서 `evaluate=true`이고 AI 실패: 응답에 `evaluationSkippedReason` → 토스트에 `review.adjust.evaluationSkipped`, 흐름은 계속.
- 6이 네트워크 오류: 같은 IK로 자동 재시도(§6.6) → 실패 시 카드 유지 + "다시 시도". 다음 카드로 넘어가지 않는다.
- 중간에 "✕": 답한 카드가 있고 이 화면이 세션을 시작했으면 `CompleteSessionSheet`(`today.partial.title`) → complete + (`taskId`) `PATCH … DEFERRED`.

### 4.4 수동 카드 추가

| # | 사용자 | API | [서버] |
|---|---|---|---|
| 1 | Review → "카드 직접 추가" | `GET /skills/tree?role=JAVA_BACKEND` (캐시) | — |
| 2 | 기술·유형·질문·정답·포인트 입력 | 클라이언트 검증(§3.2) | — |
| 3 | "저장" | `POST /review-items` `{skillCode, conceptKey: "MANUAL:{대문자 UUID}", reviewType, prompt, expectedAnswer, rubric}` (IK) | review_item INSERT(`origin=MANUAL`, `source_type=MANUAL`, due = 다음 plan-day 시작) → 201. 같은 conceptKey가 있으면 기존 카드 활성화 → 200 |
| 4 | — | 뒤로 + 토스트 "내일부터 복습에 나와요" | — |

### 4.5 Training attempt (Hint Ladder, 비동기 평가, 복습 카드 생성)

| # | 사용자 | API | [서버] |
|---|---|---|---|
| 1 | 문제 선택 → "풀기 시작" | `POST /challenges/{challengeId}/attempts` (IK) → `201 AttemptView` | attempt `STARTED`, `CHALLENGE_STARTED` |
| 2 | — | `GET /challenge-attempts/{attemptId}`, `GET /challenges/{challengeId}` | — |
| 3 | 접근 설명 작성 → "설명 저장" | `POST …/self-explanation` `{text}` (IK) | self_explanation 저장, `SELF_EXPLANATION_SUBMITTED` |
| 4 | 막힘 → "1 질문 보기" | `POST …/hints` `{requestedLevel: QUESTION_ONLY}` (IK) | seed/사전 생성 내용 공개, hint_disclosure, `HINT_DISCLOSED`, max=QUESTION_ONLY |
| 5 | "2 개념 힌트 보기" | `POST …/hints` `{requestedLevel: CONCEPT_HINT}` (IK) | max=CONCEPT_HINT |
| 6 | (선택) "4 의사코드 보기" → 확인 대화상자 → "힌트 보기" | `POST …/hints` `{requestedLevel: PSEUDOCODE, acknowledgeEvidenceImpact: true}` (IK), 동기 ≤ 20초 | `HINT_GENERATE` → 가드 → 저장, max=PSEUDOCODE |
| 7 | 답안·코드 입력 → "제출" | `POST …/submissions` `{answerText, code, language}` (IK) → `202 AsyncStatusView {submissionNo}` | submission PENDING, attempt `SUBMITTED`, `CHALLENGE_SUBMITTED`, 비동기 `CHALLENGE_EVALUATE` |
| 8 | 기다림(이동 가능) | `GET /challenge-attempts/{attemptId}` 2초 polling ≤ 3분 | submission RUNNING → COMPLETED: coverage·outcome 계산(`06` §8), attempt `EVALUATED`, `CHALLENGE_EVALUATED` → skill updater |
| 9 | — | `GET /challenges/{challengeId}` 재조회(rubric 공개) → 결과 카드 | outcome FAILED/PARTIAL 또는 (SOLVED_WITH_HINTS & max ≥ PSEUDOCODE)면 review_item upsert(due 다음 plan-day), 결과 카드는 `reviewScheduled`로 안내 |
| 10a | "수정해서 다시 제출" | 7로 (submissionNo+1, 최대 5) | attempt `EVALUATED → SUBMITTED` |
| 10b | "Today로 돌아가 완료하기" | `/today?complete={taskId}` → §4.2의 4 | — |

분기:
- 3 없이 4·7 시도: UI가 잠금으로 막는다. 서버 409 `SELF_EXPLANATION_REQUIRED`가 오면 ① 섹션으로 스크롤.
- 6에서 `HINT_CONFIRMATION_REQUIRED`(확인 플래그 누락 버그) → 확인 대화상자를 다시 띄운다.
- 8에서 submission `FAILED` → "다시 평가" → `POST …/submissions/{no}/retry` (IK) → 8로.
- 7에서 `EVALUATION_IN_PROGRESS` → 제출 버튼 잠금 + polling 시작. `SUBMISSION_LIMIT_REACHED` → 제출 영역 비활성.
- 7에서 `429 AI_*` → 토스트(§5), 입력 유지. `aiAvailable`이 false면(`DISABLED` 또는 `BALANCE_EXHAUSTED`) 7 버튼이 처음부터 비활성.

### 4.6 진단 challenge

**A. 온보딩 짧은 진단(기본, FR-02 진단 모드)**

| # | 사용자 | API | [서버] |
|---|---|---|---|
| 1 | 온보딩 3단계 "짧은 진단으로 시작" → 4단계 → "계획 만들기" | `POST /onboarding` `{runDiagnostic: true, selfAssessments: []}` | `self_assessed_level` 모두 null → 커밋 후 `suggestedDiagnostics`: 자기평가가 활성인 행이 있는 category를 선언 순서로, category당 1개, 최대 5개(`05` §4.2) |
| 2 | 5단계 "진단 시작" | SCR-DIAGNOSTICS (`GET /diagnostics/suggestions`) | 같은 규칙으로 다시 계산(이미 attempt가 있는 category 제외) |
| 3 | "풀기" | `POST /challenges/{challengeId}/attempts` (IK) | attempt STARTED (purpose=DIAGNOSTIC) |
| 4 | §4.5의 3~8과 같음 | — | — |
| 5 | 결과 확인 → SCR-DIAGNOSTICS로 돌아와 다음 문제 | 결과 카드 + 진단 결과 줄 | `DIAGNOSTIC_PASSED` / `DIAGNOSTIC_FAILED`(`06` §7.4) — 이 분야의 시작 수준이 정해진다 |
| 6 | 남은 문제는 "건너뛰기" 또는 나중에 | `localStorage`에 기록. Today 진단 카드가 남은 문제를 계속 보여준다 | 풀지 않은 분야는 4축 0에서 시작 |

**B. 자기평가 후 확인(FR-15)**

| # | 사용자 | API | [서버] |
|---|---|---|---|
| 1 | 온보딩 3단계 "진단 건너뛰고 직접 고르기"에서 Java "혼자 가능"(3) 선택 | `POST /onboarding` `{runDiagnostic: false, selfAssessments: [...]}` | Java skill들의 self_assessed_level=3 |
| 2 | Today(생성 전) 진입 | `GET /diagnostics/suggestions` | 자기평가 ≥ 3 카테고리마다 DIAGNOSTIC challenge 1개(최대 5개) |
| 3 | 진단 카드 "풀기" | `POST /challenges/{challengeId}/attempts` (IK) | attempt STARTED (purpose=DIAGNOSTIC) |
| 4 | §4.5의 3~8과 같음 | — | — |
| 5 | 결과 확인 | 결과 카드 + 진단 결과 줄 | CORRECT & max ≤ QUESTION_ONLY → `DIAGNOSTIC_PASSED` → K·I = max(현재, min(3, 3)) / 그 외 → `DIAGNOSTIC_FAILED` → self_assessment_active=false |
| 6 | (선택) 다른 진단은 "건너뛰기" | `localStorage`에 기록 | — |

- 결과 줄 문구: 통과 `diagnostics.result.passed` = "확인됐어요. 이 분야는 기초 과제를 건너뛰어요.", 미통과 `diagnostics.result.failed` = "이 분야는 기록을 쌓으며 다시 올려 가요. 괜찮아요." (미통과를 "실패"라고 쓰지 않는다)
- skill 레벨은 SCR-SKILL-DETAIL 이력에 `DIAG_PASSED`로 남는다.
- 진단 답안 평가는 AI가 필요하다. AI 불가이면 문제 보기·설명은 되지만 제출이 막힌다(SCR-DIAGNOSTICS `diagnostics.aiNote`).

### 4.7 Project Coach (self-review 먼저 → 분석 polling → 응답 → 힌트 → 정리 → 마치기 → discoveredBy)

| # | 사용자 | API | [서버] |
|---|---|---|---|
| 1 | "코드 리뷰 요청" | SCR-COACH-NEW | — |
| 2 | 코드 붙여넣기 | 크기·줄 수·private key 실시간 검사 | — |
| 3 | **우려점 먼저 작성**, 프로젝트 유형 선택 | 코드·우려점 private key 검사 | — |
| 4 | 동의 체크 → "분석 요청" | `POST /coach/reviews` (IK) | 검증 → SecretMasker(content·self-review, private key면 422, 저장 없음) → AiBudgetGuard → coach_review INSERT(PENDING, 마스킹본, `user_self_review`, retention +30일) → `202 {id, maskedSecretCount}` |
| 5 | — | `/coach/{id}` + 토스트(가린 개수) | 비동기 `CoachAnalysisTask`: RUNNING → `COACH_REVIEW` → 가드(Verification·CodeLeak·FindingCount…) → findings INSERT(최대 7), `self_review_axes`·`mentioned_by_user` 분류, `incorrectClaims` → INCORRECT_CLAIM observation → COMPLETED |
| 6 | 기다림(이동 가능) | `GET /coach/reviews/{id}` 2초 polling ≤ 3분 | — |
| 7 | finding 1의 질문을 읽고 "내 생각" 작성 → "답변 보내기" | `POST …/findings/{fid}/responses` `{text}` (IK), 동기 ≤ 20초 | masking → user_response 저장, status `USER_RESPONDED` → `COACH_RESPONSE_FEEDBACK` → ai_feedback, user_identified_issue, ai_follow_up_question 저장 → `followUpQuestion` 반환 (AI 실패·차단 시 `feedbackSkippedReason`) |
| 8 | 피드백 확인. 부족하면 "1 질문 보기" | `POST …/findings/{fid}/hints` `{requestedLevel: QUESTION_ONLY}` (IK) | `HINT_GENERATE`, hint_disclosure, `HINT_DISCLOSED`, max 갱신 |
| 9 | 답변 고치기(선택) | 7 반복 | 최신 응답으로 덮어씀, 이벤트는 남김 |
| 10 | 코드 수정 후 "수정 완료" / 오탐이면 "해당 없음"(확인) | `PATCH …/findings/{fid}` `{status, version}` | `RESOLVED` / `DISMISSED` |
| 11 | "리뷰 마치기" → 대화상자 "마치기" | `POST /coach/reviews/{id}/complete` (IK) | `closed_at` 설정, finding마다 discoveredBy 확정(`06` §9.3) → observation + `COACH_FINDING_CLOSED`, `COACH_REVIEW_COMPLETED` → skill updater(DEBUGGING), BUG/RISK 중 MISSED·FOUND_AFTER_HINT finding은 review_item upsert(`06` §9.4) |
| 12 | 결과 확인 | 응답 `review` → `DiscoveredByBadge`, 요약(스스로 언급·발견·놓침), `createdReviewItemCount` 안내 | — |
| 13 | (taskId) "Today로 돌아가 완료하기" | `/today?complete={taskId}` | — |

- 답변 없이 8: 대화상자 `coach.hint.skipResponse` → `skipSelfExplanation=true`로 요청.
- 분석 `FAILED`: `failureCode`별 문구 → "다시 분석" → `POST /coach/reviews/{id}/retry` (IK) → 6으로. `CONFIDENTIAL_SUSPECTED`는 재시도 없이 새 요청 안내.
- 30일 후 원문 purge: 코드 영역 `coach.detail.purged`, finding·답변은 유지.

### 4.8 Replan (편집 → 미리보기 → 제안 선택 → 저장)

| # | 사용자 | API | [서버] |
|---|---|---|---|
| 1 | Plan → "계획 구조 바꾸기" | `GET /plans/active`, `GET /plans/active/budget` | — |
| 2 | milestone 기간 연장, 권장 milestone 하나 삭제, 이유 입력 | 클라이언트 검증 | — |
| 3 | "변경 미리보기" | `POST /plans/{planId}/replan/preview` `{reason, version, milestones, acceptedDeferrals: [], acceptedTargetReductions: [], restoredDeferrals: [], acceptedTargetRaises: []}` (IK 없음) | budget·risk 계산(`06` §3~4). risk ≥ HIGH면 축소 제안, risk LOW이고 필요 ÷ 가능 ≤ 70%면 확장 제안(`06` §4.4, 둘은 배타). 저장 없음 |
| 4 | 결과: 위험 [빠듯함], defer 제안 2개·축소 제안 1개. defer 1개만 체크 → "선택 반영해 다시 계산" | 같은 preview + `acceptedDeferrals: [선택]` | 선택 적용 후 risk 계산(저장 없음) |
| 5 | "새 버전(v4)으로 저장" | `POST /plans/{planId}/replan` `{version, reason, milestones, acceptedDeferrals:[…], acceptedTargetReductions:[], restoredDeferrals:[], acceptedTargetRaises:[]}` (IK) | 한 트랜잭션: v3 SUPERSEDED → flush → v4 ACTIVE, milestones(새 UUID), plan_skill_target 복사 + defer 적용, `PLAN_REPLANNED`, snapshot upsert |
| 6 | — | `/plan` + 토스트, `GET /plans/active`(v4, 새 risk) | — |

- 5에서 `409 CONCURRENT_MODIFICATION`/`PLAN_NOT_ACTIVE`(다른 탭이 먼저 저장, AC-24) → 대화상자 `replan.conflict` → 최신 계획 재조회 후 편집 다시 시작.
- S1: 3·4 없이 2 → 5("새 버전으로 저장", 네 목록 빈 배열, 이유 필수). 미리보기·제안은 S2부터다.
- 여유가 있어 확장 제안이 나오는 흐름과, 목표일을 당겨 축소 제안이 나오는 흐름은 §4.17에 있다.
- 목표 날짜 변경에서 시작: SCR-LEARNING-GOAL `PUT /learning-goal` → [서버] `replan_recommended=true` → "지금 조정" → 1로(`?from=goal`).

### 4.9 Weekly review

| # | 사용자 | API | [서버] |
|---|---|---|---|
| 1 | — (월요일 dayStartHour) | — | `WeeklyReviewJob` → 지난주 `metrics_json` 생성 |
| 2 | Dashboard → "주간 리뷰 보기" → 최신 주 | `GET /weekly-reviews?cursor=` → `GET /weekly-reviews/{weekStartDate}` + `GET /thinking-patterns/trend?weeks=8` | — |
| 3 | 지표·관점 추세 확인 | — | — |
| 4 | 회고 작성 → "저장" | `PUT /weekly-reviews/{weekStartDate}/reflection` `{reflection, version}` | reflection 저장 |
| 5 | (선택) 위험이 높으면 "계획 조정" | → §4.8 | — |

### 4.10 Evidence (초안 → 편집 → 승인 → 내보내기)

| # | 사용자 | API | [서버] |
|---|---|---|---|
| 1 | 문제를 해결한 결과 화면에서 "증거로 남기기"(또는 기술 상세 레벨 변경 기록의 "증거 초안 만들기") | `POST /evidence/drafts` `{sourceLearningEventId: AttemptView.evidenceSourceEventId}` (IK) → `202 AsyncStatusView` | evidence CANDIDATE, generation PENDING → 비동기 `EVIDENCE_DRAFT` → `ai_draft_json` + 편집 필드 복사 → COMPLETED |
| 2 | — | `/evidence/{id}` polling ≤ 3분 | — |
| 3 | 초안을 내 말로 고침, 커밋 링크 추가 → "저장" | `PATCH /evidence/{id}` `{…, version}` | 편집 필드만 갱신(AI 원본 유지) |
| 4 | "승인" → 확인 | `POST /evidence/{id}/accept` (IK) | ACCEPTED, `accepted_at`, `EVIDENCE_ACCEPTED` → evidence_count |
| 5 | 증거 목록 → "Markdown 내보내기" | `GET /evidence/export?format=markdown` | — |
| 6 | 파일 저장 또는 복사 → 내 학습 노트에 보관 | 브라우저 다운로드 | — |

- AI 불가이거나 `evidenceSourceEventId`가 null이면: `/evidence/new`(제목·기술 채움) → `POST /evidence` (IK) → 3부터 같다.

### 4.11 로드맵 비교 (Roadmap compare)

| # | 사용자 | API | [서버] |
|---|---|---|---|
| 1 | Radar → "로드맵 비교하기" | SCR-REQUIREMENT-NEW | — |
| 2 | 제목·출처 링크(기록용)·공개 로드맵이나 기술 목록 본문 붙여넣기 → "비교 요청" | `POST /requirement-docs` (IK) → `202 {id}` | requirement_doc PENDING. **sourceUrl fetch 없음** → 비동기 `REQUIREMENT_EXTRACT` → requirement_item(rawText, type, skill 매칭) → `RequirementFitClassifier`가 fit·증거 연결(`06` §13) → COMPLETED |
| 3 | 기다림 | `GET /requirement-docs/{id}` polling ≤ 3분 | — |
| 4 | 필수·권장별 준비됨/도전/나중에 확인 | — | — |
| 5 | "도전" 항목의 기술 탭 → 기술 상세 → "문제 풀기" 또는 Plan에서 조정 | `/skills/{skillId}` | — |
| 6 | (선택) 비교한 로드맵 삭제 | `DELETE /requirement-docs/{id}` | cascade 삭제 |

### 4.12 캘린더 구독 설정

| # | 사용자 | API / 클라이언트 | [서버] |
|---|---|---|---|
| 1 | Settings → "구독 링크 만들기" | `POST /me/calendar-token` (IK) | 256bit 토큰 생성, 해시 저장(이전 해시 교체) → `201 {feedUrl, issuedAt}` (같은 키 재생 응답은 `feedUrl=null`) |
| 2 | 시트에서 "복사" 또는 "캘린더 앱에서 열기"(webcal) | 클립보드 / `webcal://` | — |
| 3 | 캘린더 앱에 URL 구독 추가, 종일 일정 알림 시각 설정 | (외부 앱) | — |
| 4 | — | 캘린더 앱이 주기적으로 `GET /calendar/{token}.ics` | 해시 조회 → 오늘부터 7 plan-day 종일 일정 생성. 불일치면 404, 시간당 60회 초과면 429 |
| 5 | "완료" → 시트 닫힘. 토큰 원문은 다시 볼 수 없다 | 메모리에서 제거 | — |

- 링크 유출이 의심되면 1을 다시 실행한다(이전 링크 즉시 무효).

### 4.13 데이터 export · 계정 삭제

| # | 사용자 | API / 클라이언트 | [서버] |
|---|---|---|---|
| 1 | Settings → "내 데이터 내려받기" | `GET /me/export` → `devpilot-export-yyyyMMdd.json` 저장 | `DATA_EXPORTED` 감사 로그 |
| 2 | Settings → "계정 삭제" | SCR-ACCOUNT-DELETE, 토큰 `authTime`(`amr[].timestamp` 최댓값, 없으면 `iat`) 확인 → 오래됨 | — |
| 3 | "GitHub로 다시 로그인" | `signInWithOAuth(github, redirectTo: …/auth/callback?from=/settings/delete-account?reauth=1)` | 새 access token(`amr[].timestamp` = 지금) |
| 4 | — | SCR-AUTH-CALLBACK → `GET /me` → `/settings/delete-account?reauth=1`, "확인됐어요 · 4분 30초" | — |
| 5 | `삭제합니다` 입력 → "계정 삭제" | `DELETE /me` | `authTime` ≤ 5분 확인 → status `DELETION_REQUESTED`, 캘린더 토큰 삭제, `ACCOUNT_DELETION_REQUESTED` → `202` |
| 6 | — | `signOut()` → `/login?reason=deleted` | ≤ 5분 내 `AccountDeletionJob`: app_user 삭제(cascade), ai_call_log.user_id=null, `ACCOUNT_DELETION_COMPLETED` |
| 7 | (운영자) | — | allowlist에서 이메일 제거(`supabase` 모드면 Auth 사용자도 수동 삭제, DEC-11) |

- 5에서 `403 RECENT_LOGIN_REQUIRED` → 확인 표시 해제 → 3부터.
- 6 이후 같은 계정으로 다시 로그인하면: 삭제 완료 전이면 `GET /me`의 `status=DELETION_REQUESTED` → `/login?reason=deleted`, 완료 후면(allowlist에 남아 있을 때) 새 `app_user`로 온보딩부터 시작한다.

### 4.14 복귀 모드 경험

| # | 상황 / 사용자 | 화면 / API | [서버] |
|---|---|---|---|
| 1 | 10/10까지 매일 학습, 10/11~10/13 학습 없음 | — | — |
| 2 | 10/14 앱 열기 | SCR-TODAY 생성 전: **평소와 같은 화면**. 쉰 날 수·밀린 카드 수를 보여주지 않는다 | — |
| 3 | 30분·보통 → "오늘 계획 만들기" | `POST /today/generate` (IK) | `comebackMode=true`(최근 3 plan-day COMPLETED 세션 0, 이전 ≥ 1): 복습 상한 10, 난이도 ≤ 2, `COMEBACK_HARD_TASK`, 이유 `COMEBACK_EASY_START` |
| 4 | 결과 확인 | 상단 배너 `today.comeback.banner`, 이유 "다시 시작하기 좋은 쉬운 과제", 복습 줄 "복습 10장" 이하 | `daily_plan.comeback_mode=true` |
| 5 | 과제 완료 | §4.2 | COMPLETED 세션 생김 |
| 6 | 10/15 앱 열기 → 계획 생성 | `POST /today/generate` | 최근 3 plan-day에 완료 세션 있음 → `comebackMode=false`. 배너 없음 |

- 복귀 배너는 닫기 버튼이 없고 한 줄이다. 다른 화면(Dashboard 등)에는 복귀 관련 표시가 없다.
- 밀린 due 카드는 상한 안에서 overdue 순으로 나온다(`06` §6.5). "밀린 카드 N장" 같은 합계는 어디에도 표시하지 않는다.

### 4.15 코드 읽기 → 러버덕 → 완료 (READ_CODE, RC-1)

| # | 사용자 | 화면 | API / 클라이언트 | [서버] |
|---|---|---|---|---|
| 1 | Today `READ_CODE` 카드 "시작" | SCR-TODAY | `PATCH /today/tasks/{taskId}` `{status: IN_PROGRESS}` → `POST /learning-sessions` `{learningTaskId}` (IK) | task `IN_PROGRESS`, session `IN_PROGRESS`, `SESSION_STARTED` |
| 2 | — | SCR-READ-CODE | `GET /readings/{readingKey}` | `CuratedReadingRegistry`에서 조회. 저장소 fetch 없음, AI 없음 |
| 3 | `cloneHint` 복사 → 터미널에서 clone·checkout → IDE로 파일 열어 줄 범위 읽기, 볼 지점 확인 | (내 컴퓨터) | 클립보드만 | 없음 — 서버는 코드를 보지 않는다 |
| 4 | "읽었으면 설명하기" | SCR-RUBBER-DUCK ① | `extra`로 저장소·파일·줄 범위·질문 전달 | — |
| 5 | 설명 작성 → "설명 보내기" | ① → ② | `POST /rubber-duck` `{targetType: CODE_READING, targetId: taskId, skillCode}` (IK) → `POST /rubber-duck/{id}/turns` `{explanation}` (IK) | 세션 `IN_PROGRESS`(`learning_session_id` = 1의 세션) → `SecretMasker` → 예산 확인 → `RUBBER_DUCK`(가드 CodeLeak·Language·NoAnswer) → turn 1 저장 |
| 6 | 질문에 답 → "보내기" (2~4번) | ② | `POST …/turns` (IK) | turn n 저장. 2턴 연속 "모르겠다"면 `suggestHint=true` → 이 대상은 정리로 안내 |
| 7 | "정리하고 끝내기"(또는 5턴 도달 → "정리하기") | ③ | `POST /rubber-duck/{id}/complete` (IK) | `RUBBER_DUCK_SUMMARY` → `summary_json`, `COMPLETED`. gap마다 복습 카드(`source_type = RUBBER_DUCK`, `EXPLAIN`, 첫 due 다음 plan-day — 같은 개념 카드가 있으면 due만 당김). skill이 있으면 `RUBBER_DUCK_COMPLETED` |
| 8 | 정리 확인 | ④ | — | 세션 `COMPLETED` → RC-1 조건 충족. 과제 상태는 그대로(`05` §9.8) |
| 9 | "Today로 돌아가 완료하기" → 시간·회고·(선택) 읽기 평가 → "완료 기록" | SCR-TODAY 완료 시트 | `POST /learning-sessions/{id}/complete` (IK) → `PATCH /today/tasks/{taskId}` `{status: COMPLETED, readingFeedback?}` | RC-1 확인: 이 task를 대상으로 한 `COMPLETED` 러버덕 있음 → task `COMPLETED`, 평가를 골랐으면 `reading_feedback` 저장 |

분기:
- AI 불가(`DISABLED`·`BALANCE_EXHAUSTED`): planner가 새 `READ_CODE`를 내지 않는다. 이미 있는 task는 1~3까지 되고 4의 버튼이 막힌다 → "여기까지 기록"(`DEFERRED`) 또는 Today "오늘은 건너뛰기".
- 7에서 정리 AI 실패: 200 + `summarySkippedReason` → ④에 "대화는 저장했어요" 안내. 세션은 `COMPLETED`이므로 9로 갈 수 있다. 복습 카드는 생기지 않는다.
- 5·6에서 `422 SECRET_DETECTED_BLOCKED`: 개인 키가 든 설명은 저장·AI 전송 없이 거절된다. 지우고 다시 보낸다.
- 9를 거치지 않고 Today로 돌아온 경우: Today가 8의 기록으로 러버덕 `COMPLETED`를 확인해 "완료"를 보인다(§3.5). 기록이 없는 기기에서는 "코드 읽기로 돌아가기"만 보인다.
- 이 흐름에서 "완료"를 누르는 것은 **읽었다는 체크가 아니다**. 설명(러버덕 1회)이 완료 조건이다(RC-1).

### 4.16 v3 핵심 루프 하루 — 읽는다 → 설명한다 → 만든다 → 반복한다

전제(예시): 사용자는 온보딩에서 목표일을 등록했고(예: 2027-04-01), 사이드 프로젝트 "주문 시스템"을 만들었다. 짧은 진단으로 Spring MVC REST API의 planning KNOWLEDGE가 3이 됐고 이 기술을 집중 기술로 골랐다(`projectNeed`). 현재 milestone은 "기반 다지기"이고 petclinic은 아직 받아 두지 않았다. 과제 유형은 planner가 정한다(`06` §5.3) — 아래는 그 규칙대로 나온 하루의 예다.

| 시점 | 사용자 | 화면 | DevPilot이 하는 일 |
|---|---|---|---|
| 아침 ① (2분) | Today 열기 → 45분·보통 → "오늘 계획 만들기" | SCR-TODAY | Spring MVC REST API가 1순위. 풀 challenge가 없고 KNOWLEDGE ≥ 1이며 reading이 있어 **`READ_CODE`**: "Spring PetClinic 읽기 — OwnerController.java 48~122줄", 약 15분. 이유 "Spring PetClinic에서 같은 문제를 어떻게 풀었는지 먼저 봅니다", "기반 다지기 milestone 핵심 항목". 복습 6장 줄 |
| 아침 ② 읽는다 (15분) | "시작" → `cloneHint` 복사 → 터미널에서 clone·checkout → 로컬 IDE에서 petclinic `OwnerController.java` 48~122줄을 읽으며 볼 지점 3개 확인 | SCR-READ-CODE, 내 IDE | 무엇을·왜·어디를 읽는지만 안내한다. 코드는 사용자 컴퓨터에만 있다 |
| 아침 ③ 설명한다 (10분) | "읽었으면 설명하기" → "이 컨트롤러는 조회만 해서 Service가 필요 없다"고 설명 → AI "조회만 한다고 하셨는데, 폼 제출을 처리하는 메서드는 어디에 규칙을 두고 있나요?" → 답 → AI "그 저장이 실패하면 어디까지 되돌려지나요?" → "잘 모르겠어요" → … 4턴 → "정리하고 끝내기" | SCR-RUBBER-DUCK | AI는 정답·"맞아요"를 말하지 않고 되묻기만 한다(RD-1). 정리: 막힌 곳 1개(조회·저장의 트랜잭션 경계) → **복습 카드 1장**(내일 due), 잘 설명한 것 1개 |
| 아침 ④ | "Today로 돌아가 완료하기" → 25분 기록, 읽기 평가 "도움 됐어요"(선택) | SCR-TODAY | 설명을 마쳤으므로 `READ_CODE` 완료(RC-1). 평가는 과제에 저장만 한다 |
| 출근길 (5분) | 복습 6장 | SCR-REVIEW-SESSION | 교차 학습 순서로 출제 — 같은 기술 카드가 3장 연속 나오지 않는다(RV-INTERLEAVE) |
| 저녁 ① 만든다 | "하나 더 하기" → 40분·보통 | SCR-TODAY | 아침에 완료한 reading은 후보에서 빠지고(`06` §5.3), KNOWLEDGE ≥ 2 + `projectNeed` + 컨디션 보통 + `ACTIVE` 프로젝트가 있어 **`PROJECT_TASK`**: "주문 시스템에 Spring MVC REST API 적용하기"(SP-2) |
| 저녁 ② (30분) | "시작" → 내 IDE에서 주문 시스템에 petclinic과 같은 계층 구조로 상품 조회 API를 만든다 | 내 IDE | — |
| 저녁 ③ 설명한다 (10분, 권장) | "러버덕으로 설명하기"(`PROJECT_WORK`) → "왜 여기에는 Service를 두었는지" 3턴 설명 → 정리 → "Today로 돌아가 완료하기" | SCR-RUBBER-DUCK, SCR-TODAY | 막힌 곳 0개·3턴이면 설명 능력 증거로 남는다(RD-5). `PROJECT_TASK`의 완료 조건은 아니다 |
| 다음 날 아침 반복한다 (5분) | 복습 | SCR-REVIEW-SESSION | 어제 러버덕에서 막힌 "조회만 하는 메서드에 트랜잭션을 여는 이유를 설명해 보세요" 카드가 다른 기술 카드 사이에 섞여 나온다(교차 학습). "다시"를 누르면 더 짧은 간격으로 또 나온다 |
| 주말 (선택) | 주문 시스템 코드를 붙여넣고 코드 리뷰 요청(사이드 프로젝트 "주문 시스템" 선택) | SCR-COACH-NEW | (S4 이후) 먼저 스스로 우려점을 쓰고 질문을 받는다(FR-12) |

- 하루에 한 번, Today 아래쪽에 **오늘의 팁** 카드가 한 장 붙는다(S3, §3.5). 읽고 "새로 알았어요"를 고르면 내일 복습 카드로 나오고, "직접 해 볼래요"를 고르면 다음 날 Today에 "직접 해 보기" 한 줄로 올라온다(`06` §5.12). 팁은 과제가 아니라서 이 표의 시간에 들어가지 않는다.
- 과제 카드에 **시작 전 확인 목록**이 붙어 있으면 "시작" 전에 세 줄을 읽고, 완료 시트에서 **끝내기 전 확인**을 한 번 더 본다(§3.5). 확인 목록은 저장하지 않는 콘텐츠이고 완료를 막지 않는다.
- 이 하루가 쌓인 결과물은 **주문 시스템**이고, 러버덕에서 설명해 본 기록과 복습 카드가 내가 무엇을 왜 만들었는지 설명할 수 있는 학습 기록이 된다(`01` §5 "증거가 된다").
- AI가 멈춘 날에는 `READ_CODE`가 제안되지 않고, 복습·계획·Today(READING·EXPLAIN·PROJECT_TASK)는 그대로 돈다.

### 4.17 기한 역산 — 촉박할 때와 여유 있을 때

**A. 촉박할 때 — 중요한 것 위주로 줄인다**

| # | 사용자 | 화면 | API | [서버] |
|---|---|---|---|---|
| 1 | 설정 > 학습 목표에서 목표일을 3개월 앞당김 → 저장 → "지금 조정" | SCR-SETTINGS → SCR-LEARNING-GOAL | `PUT /learning-goal` | goal 저장, `replan_recommended = true`(자동 replan 없음) |
| 2 | 권장 milestone(템플릿 7~9: 구조 정리·배포와 운영·설명과 정리) 기간을 줄이고 이유 입력 → "변경 미리보기" | SCR-REPLAN ①→② | `POST …/replan/preview` (IK 없음) | risk **HIGH** → 축소 제안: MUST 목표 1단계 낮춤(3 미만으로는 제안 안 함) → SHOULD 기술 defer(중요도 낮은 것부터). 제안은 skill 목표 단위다(`06` §4.4) |
| 3 | 제안 몇 개 체크 → "선택 반영해 다시 계산" → "새 버전으로 저장" | SCR-REPLAN ② | preview → `POST …/replan` `{acceptedDeferrals, acceptedTargetReductions}` (IK) | 새 plan version, snapshot |
| 4 | 다음 날 Today | SCR-TODAY | `POST /today/generate` | risk ≥ HIGH면 MUST 가중·LATER 제외, 이유 "마감 위험이 높아 필수 항목 우선" |

**B. 여유 있을 때 — 깊이를 더한다**

| # | 사용자 | 화면 | API | [서버] |
|---|---|---|---|---|
| 1 | 평일 학습 시간을 늘렸고 몇 주 꾸준히 해서 Plan에 "여유가 있어요" | SCR-PLAN | `GET /plans/active/budget` | risk **LOW**, 필요 ÷ 가능 66% |
| 2 | "계획 조정" → 이유 입력 → "변경 미리보기" | SCR-REPLAN ③ | `POST …/replan/preview` | 확장 제안: 미뤄 둔 권장 항목 복원(`RESTORE_DEFERRED`) → MUST 목표 +1(`RAISE_TARGET`, 예: Spring Transaction 설명 3 → 4). 확장 후 비율이 90%를 넘지 않는 데까지만 |
| 3 | "시스템 설계 · 캐시 다시 넣기", "Spring Transaction 설명 3 → 4" 체크 → "선택 반영해 다시 계산" → "새 버전으로 저장" | SCR-REPLAN ③ | `POST …/replan` `{restoredDeferrals: ["SYSTEM_DESIGN.CACHE"], acceptedTargetRaises: [{skillCode: "SPRING.TRANSACTION", axis: "EXPLANATION", newTarget: 4}]}` (IK) | 복원 skill `deferred = false`, 목표 올린 축 `adjustment = USER_EDITED`, 새 version |
| 4 | 다음 Today부터 | SCR-TODAY | — | 목표와 현재 수준의 차이가 커진 기술이 더 자주, 한 단계 어려운 과제로 나온다(`06` §5.3·§5.4). 복원한 기술도 다시 후보가 된다 |

- 제안은 저장하지 않고 자동 적용하지 않는다. 사용자가 체크한 것만 저장된다(FR-05).
- 필요 ÷ 가능이 70%~80%이거나 위험이 보통이면 어느 쪽 제안도 없다(`replan.preview.noSuggestions`).

### 4.18 재현 과제 — 며칠 뒤 AI 없이 혼자 다시 만들기 (S4)

전제: 4일 전(예시) 사용자가 challenge "트랜잭션 전파 수정하기"를 힌트 2단계를 보고 풀어 `COMPLETED`했다. 오늘은 그 창(3~7일) 안이다(`06` §5.10 RE-2).

| # | 사용자 | 화면 | API | [서버] |
|---|---|---|---|---|
| 1 | Today 생성 | SCR-TODAY | `POST /today/generate` (IK) | 그 skill의 재현 후보가 있어 제안 0번이 `REDO`. modifier `REDO_DUE` ×1.30으로 그 skill이 1위 → main task `REDO`(`redoSourceTaskId` 고정), 이유 `REDO_WITHOUT_AI` |
| 2 | 카드에서 잠금 줄을 읽고 "시작" | SCR-TODAY | `PATCH /today/tasks/{taskId}` `{status: IN_PROGRESS}` → `POST /learning-sessions` (IK) | task `IN_PROGRESS` |
| 3 | 원본 문제를 다시 열어 힌트를 누름 | SCR-TRAINING-ATTEMPT | `POST /challenge-attempts/{id}/hints` | **409 `AI_ASSIST_LOCKED_FOR_REDO`**(HL-9). `hint_disclosure`·이벤트·AI 호출 0. 화면은 이유와 "Today로 가기" |
| 4 | 러버덕으로 설명해 보려 함 | SCR-RUBBER-DUCK 시작 전 | `POST /rubber-duck` `{targetType: CHALLENGE, targetId}` (IK) | **409 `AI_ASSIST_LOCKED_FOR_REDO`**(RE-5). 세션 0개 |
| 5 | 내 IDE에서 처음부터 다시 구현 (35분) | 내 IDE | — | — |
| 6a | "완료" → 시간 입력 → **"네, 혼자 했어요"** → "완료 기록" | SCR-TODAY 완료 시트 | `POST /learning-sessions/{id}/complete` (IK) → `PATCH /today/tasks/{taskId}` `{status: COMPLETED, redoWithoutAi: true}` | `redo_without_ai = true`, `REDO_COMPLETED{withoutAi: true}` → §7.2 **독립 구현 증거** 1개. 복습 카드 없음 |
| 6b | (대신) **"아니요, 도움을 받았어요"** | SCR-TODAY 완료 시트 | 같은 요청, `redoWithoutAi: false` | `REDO_COMPLETED{withoutAi: false}`(레벨 증거 아님) + 복습 카드 1장(`REDO:{sourceTaskId}`, 내일 due). 토스트 + "복습하러 가기" |
| 7 | 다음 Today | SCR-TODAY | `POST /today/generate` | 6a면 그 원본은 끝났다(RE-3). 6b면 시도 1회로 세고, 그 완료일에서 다시 3~7일 뒤 창이 열린다(최대 `max-attempts` 2회) |

분기:
- 3·4의 잠금은 **그 대상만**이다. 다른 challenge의 힌트, 복습 카드·코드 읽기·개념 러버덕은 그대로 된다.
- "오늘은 건너뛰기"(`SKIPPED`)도 시도 1회로 센다. "여기까지 기록"(`DEFERRED`)은 세지 않고 다음 날 다시 제안된다.
- 답을 고르지 않으면 "완료 기록"이 비활성이다. 서버도 `400 VALUE_REQUIRED`로 막는다(RE-6).
- AI가 멈춘 날에도 재현 과제는 그대로 제안·수행·완료된다(RE-8 — AI를 부르지 않는다).

### 4.19 프로젝트 기록 — 결정과 장애를 그 자리에서 (S3)

| # | 사용자 | 화면 | API | [서버] |
|---|---|---|---|---|
| 1 | `PROJECT_TASK`를 하다 재고가 음수가 되는 것을 발견 | 내 IDE | — | — |
| 2 | Today 카드의 "이 프로젝트 기록" | SCR-TODAY → SCR-PROJECT-DETAIL | `GET /side-projects/{id}`, `GET …/notes` | 기록 목록 |
| 3 | "+ 장애 기록" → 제목·날짜·관련 기술 → 네 항목 작성 → "저장" | SCR-PROJECT-NOTE-EDIT | `POST …/notes` (IK) | 마스킹 → INSERT. 학습 이벤트·레벨 변화 없음(PN-3) |
| 4 | 며칠 뒤 왜 그렇게 정했는지 기록 | SCR-PROJECT-DETAIL → SCR-PROJECT-NOTE-EDIT | `POST …/notes` `{noteType: DECISION, …}` | 〃 |
| 5 | (S5) 주간 리뷰 | SCR-WEEKLY-DETAIL | `GET /weekly-reviews/{weekStartDate}` | `metrics_json.projectNoteCount`에 그 주의 기록 수 |
| 6 | (S6) 학습 기록 초안 만들기 | SCR-EVIDENCE-DETAIL | `POST /evidence/drafts` `{sourceProjectNoteId}` (IK) | 그 기록의 마스킹본을 입력으로 STAR 초안 생성. 기록의 skill이 evidence skill이 된다 |

- 유형은 3·4에서 정해지고 이후 바꿀 수 없다(PN-2). 잘못 골랐으면 지우고 다시 만든다.
- 프로젝트를 지우면 그 기록도 함께 사라진다. 삭제 확인 대화상자가 이를 알린다(SCR-PROJECTS).

---

## 5. 오류 코드 → UI 처리

### 5.1 HTTP 오류 코드

- 클라이언트는 **`code`로만 분기**한다. 문구는 `error.<CODE>` l10n key를 쓰고, 표에 없는 code는 서버 `detail`(없으면 `error.INTERNAL_ERROR`)을 보여준다.
- UI 처리 종류: **토스트**(SnackBar 4초) · **인라인**(필드·섹션 아래 오류 텍스트) · **대화상자** · **전체 화면**(`ErrorView`/전용 SCR) · **흐름 분기**(오류를 화면 상태로 바꿈).
- 모든 오류 표시(전체 화면·대화상자)에는 접힌 "문제 신고 정보"(code, `traceId`, 시각)를 둔다. 토스트는 생략한다.

| HTTP | code | 문구 (`error.<CODE>`) | UI 처리 | 사용자 행동 |
|---|---|---|---|---|
| 400 | `VALIDATION_FAILED` | 입력값을 확인해 주세요. | 인라인: `errors[]`를 필드별로 표시(서버 `message`), 매칭 필드가 없으면 토스트 | 수정 후 다시 전송 |
| 400 | `UNKNOWN_ENUM_VALUE` | 앱을 새로고침해 주세요. 새 버전이 있을 수 있어요. | 대화상자 | "새로고침"(페이지 reload) |
| 400 | `MALFORMED_REQUEST` | 요청을 처리하지 못했어요. 새로고침 후 다시 시도해 주세요. | 대화상자 + 신고 정보 | "새로고침" |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | 요청을 처리하지 못했어요. 새로고침 후 다시 시도해 주세요. | 대화상자 + 신고 정보 (클라이언트 버그) | "새로고침" |
| 400 | `INVALID_CURSOR` | 목록을 처음부터 다시 불러올게요. | 토스트 + 목록 cursor 초기화 후 첫 페이지 재조회(자동) | 없음 |
| 401 | `AUTHENTICATION_REQUIRED` | 로그인이 만료됐어요. 다시 로그인해 주세요. | 흐름 분기: 토큰 갱신 1회 재시도 → 실패 시 `/login?reason=expired&from=` | 다시 로그인 |
| 403 | `USER_NOT_ALLOWED` | 초대된 계정이 아니에요. | 전체 화면 SCR-NOT-ALLOWED | 다른 계정으로 로그인 |
| 403 | `FORBIDDEN` | 이 작업을 할 수 없어요. | `meProvider` 갱신 → `status=DELETION_REQUESTED`면 `signOut` → `/login?reason=deleted`, 그 외 대화상자 | 확인 |
| 403 | `RECENT_LOGIN_REQUIRED` | 안전을 위해 다시 로그인한 뒤 진행해 주세요. | 인라인(SCR-ACCOUNT-DELETE 본인 확인 영역) | "GitHub로 다시 로그인" |
| 404 | `RESOURCE_NOT_FOUND` | 찾을 수 없는 항목이에요. | 상세 화면 조회: 전체 화면 SCR-NOT-FOUND / 행동 중: 토스트 + 목록 재조회 | "Today로" / 없음 |
| 404 | `LEARNING_GOAL_NOT_FOUND` | 학습 목표가 없어요. | 전체 화면 `ErrorView`(온보딩 이후 정상 흐름에서는 발생하지 않음) | "다시 시도" |
| 404 | `PLAN_NOT_FOUND` | 계획을 찾을 수 없어요. | 흐름 분기: SCR-PLAN·SCR-TODAY는 "계획 만들기" 상태, SCR-PLAN-VERSION은 SCR-NOT-FOUND | "계획 만들기" |
| 404 | `TODAY_NOT_GENERATED` | (표시 안 함) | 흐름 분기: SCR-TODAY 생성 전 레이아웃 | 시간·컨디션 선택 |
| 409 | `ONBOARDING_REQUIRED` | 시작 설정을 먼저 마쳐 주세요. | 흐름 분기: `meProvider` 갱신 → `/onboarding/goal` | 온보딩 진행 |
| 409 | `ONBOARDING_ALREADY_COMPLETED` | 이미 시작 설정을 마쳤어요. | 흐름 분기: `meProvider` 갱신 → `/today` + 토스트 | 없음 |
| 409 | `ACTIVE_PLAN_EXISTS` | 이미 사용 중인 계획이 있어요. | 토스트 + `GET /plans/active` 재조회 | 없음 |
| 409 | `PLAN_NOT_ACTIVE` | 계획이 이미 새 버전으로 바뀌었어요. | 대화상자(`replan.conflict`) → 최신 계획 재조회 | "최신 계획 불러오기" |
| 409 | `TODAY_ALREADY_STARTED` | 진행 중인 과제가 있어요. | 대화상자(`today.regenerate.startedDialog`) | "새로 만들기"(`force=true`) / 취소 |
| 409 | `TODAY_ALREADY_COMPLETED` | 오늘 핵심 과제를 이미 마쳤어요. | 대화상자: 하나 더 만들지 확인 | "하나 더 하기"(`force=true`) / 취소 |
| 409 | `SELF_EXPLANATION_REQUIRED` | 먼저 내 생각을 적어 주세요. | 인라인: self-explanation(또는 finding 답변) 섹션으로 스크롤·포커스 | 설명 작성 또는 건너뛰기 |
| 409 | `HINT_CONFIRMATION_REQUIRED` | 이 힌트는 기록에 영향을 줘요. 확인 후 볼 수 있어요. | 대화상자: `HintConfirmDialog` 다시 표시 | "힌트 보기"(ack=true) / 취소 |
| 409 | `FULL_EXAMPLE_NOT_ALLOWED` | 한 번 제출한 뒤에 전체 예시를 볼 수 있어요. | 토스트 + attempt 재조회 | 답 제출 또는 포기 확인 |
| 409 | `SUBMISSION_LIMIT_REACHED` | 이 풀이에서 제출 5회를 모두 사용했어요. | 인라인: 제출 영역 비활성 + 문구 | 새로 풀기(SCR-CHALLENGE-DETAIL) |
| 409 | `EVALUATION_IN_PROGRESS` | 이전 제출을 평가하고 있어요. | 인라인: 제출 버튼 잠금 + polling 시작 | 결과 기다리기 |
| 409 | `AI_TASK_NOT_RETRYABLE` | 다시 시도할 수 없는 상태예요. | 토스트 + 리소스 재조회 | 없음 |
| 409 | `REVIEW_ALREADY_CLOSED` | 이미 마친 리뷰예요. | 토스트 + 재조회 → 읽기 전용 | 없음 |
| 409 | `AI_ASSIST_LOCKED_FOR_REDO` | 지금은 이 과제를 AI 없이 혼자 다시 만드는 중이에요. 재현 과제를 마치면 다시 쓸 수 있어요. | 대화상자(`today.redo.lockedElsewhere`) + "Today로 가기"(→ `/today`). SCR-TRAINING-ATTEMPT는 힌트 버튼을, SCR-RUBBER-DUCK 시작 전 화면은 "설명 보내기"를 비활성으로 바꾸고 그 아래 같은 문구 1줄 | "Today로 가기" / 확인 |
| 409 | `INVALID_STATE_TRANSITION` | 지금 상태에서는 할 수 없는 작업이에요. | 토스트 + 해당 리소스 재조회. 복습 답변이면 카드 건너뛰기(§3.6), attempt 시작이면 기존 attempt 열기(§3.7) | 새 상태 확인 |
| 409 | `CONCURRENT_MODIFICATION` | 다른 곳에서 먼저 바뀌었어요. 최신 내용으로 다시 불러왔어요. | 상태 변경(PATCH status): 재조회 후 1회 자동 재시도, 실패 시 토스트 / 긴 입력 편집(replan, weekly reflection, evidence): 대화상자로 최신 내용 불러오기 또는 덮어쓰기 선택 | 확인 후 다시 편집 |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | (표시 안 함) | 흐름 분기: API 계층이 1초 뒤 같은 키로 재시도(최대 5회, `05-api-spec.md` §1.7). 5회 모두 같으면 토스트 `error.IDEMPOTENCY_IN_PROGRESS` = "요청을 처리하고 있어요. 잠시 후 확인해 주세요." | 잠시 후 새로고침 |
| 413 | `CONTENT_TOO_LARGE` | 내용이 너무 길어요. 줄여서 다시 보내 주세요. | 인라인: 해당 입력 필드 아래 | 내용 줄이기 |
| 413 | `REQUEST_TOO_LARGE` | 보낼 수 있는 크기를 넘었어요. 입력을 줄여 주세요. | 인라인(폼 하단) | 내용 줄이기 |
| 422 | `IDEMPOTENCY_KEY_REUSED` | 요청을 처리하지 못했어요. 다시 시도해 주세요. | 토스트 + 신고 정보 로그. API 계층이 새 키를 만들어 다음 시도에 쓴다 | "다시 시도" |
| 422 | `SECRET_DETECTED_BLOCKED` | 개인 키(private key)가 포함되어 있어 보낼 수 없어요. 해당 부분을 지워 주세요. | 인라인: 코드 입력칸 아래 | 해당 부분 삭제 |
| 429 | `AI_DAILY_LIMIT_EXCEEDED` | 오늘 AI 사용 횟수를 모두 썼어요. 내일 다시 쓸 수 있어요. 복습과 계획은 그대로 쓸 수 있어요. | 대화상자 + `meProvider` 갱신 | 확인 |
| 429 | `AI_MONTHLY_BUDGET_EXCEEDED` | 이번 달 AI 예산을 모두 썼어요. 다음 달에 다시 쓸 수 있어요. | 대화상자 + `meProvider` 갱신(→ `DISABLED` 배너) | 확인 |
| 429 | `AI_CONCURRENCY_LIMIT` | 진행 중인 AI 작업이 끝난 뒤 다시 시도해 주세요. | 토스트 | 잠시 후 "다시 시도" |
| 429 | `RATE_LIMITED` | 요청이 많아요. 잠시 후 다시 시도해 주세요. | 인증 API 전체에 적용(사용자당 120 req/min). 토스트 + API 계층이 `Retry-After` 초 동안 해당 요청 재시도와 모든 polling을 멈춘 뒤 재개한다. 입력 유지 | 잠시 후 다시 시도 |
| 502 | `AI_OUTPUT_INVALID` | AI 응답을 처리하지 못했어요. 다시 시도해 주세요. | 토스트(hint 요청·러버덕 턴에서 발생 — 러버덕은 AI 질문이 답을 담아 가드에 걸린 경우 포함). 입력 유지 | "다시 시도"(새 키) |
| 502 | `AI_REFUSED` | 이 요청은 AI가 처리할 수 없어요. 내용을 바꿔 다시 시도해 주세요. | 인라인(요청한 섹션) | 입력 수정 |
| 503 | `AI_UNAVAILABLE` | AI 기능을 지금 쓸 수 없어요. 복습·계획·Today는 그대로 쓸 수 있어요. | 토스트 + `meProvider` 갱신 + 해당 화면 AI 배너 | 나중에 다시 시도 |
| 504 | `AI_TIMEOUT` | AI 응답이 늦어요. 잠시 후 다시 시도해 주세요. | 토스트(hint 요청·러버덕 턴에서 발생). 입력 유지 | "다시 시도"(새 키) |
| 500 | `INTERNAL_ERROR` | 문제가 생겼어요. 잠시 후 다시 시도해 주세요. | 조회: 전체 화면 `ErrorView` / 행동: 대화상자 + 신고 정보 | "다시 시도" |
| — | (클라이언트) `NETWORK_ERROR` | 연결이 불안정해요. 다시 시도해 주세요. | API 계층 자동 재시도(§6.6) 후 토스트 + 오프라인이면 배너 | "다시 시도"(같은 키) |

### 5.2 비동기 작업 `failureCode` (`AsyncFailureCode`)

challenge 생성, 제출 평가, coach 분석, evidence 초안, 로드맵 비교 분석의 `FAILED` 상태와 러버덕 정리 실패(`summarySkippedReason`, SCR-RUBBER-DUCK ④)에 표시한다. 문구 key `asyncFailure.<CODE>`.

| failureCode | 문구 | 재시도 버튼 |
|---|---|---|
| `AI_UNAVAILABLE` | AI 기능을 쓸 수 없어 완료하지 못했어요. | 표시(AI 사용 가능할 때만 활성) |
| `AI_TIMEOUT` | AI 응답이 늦어 완료하지 못했어요. | 표시 |
| `AI_REFUSED` | AI가 이 내용을 처리하지 않았어요. 내용을 바꿔 새로 요청해 주세요. | 숨김(새 요청 안내) |
| `AI_OUTPUT_INVALID` | AI 응답을 처리하지 못했어요. | 표시 |
| `AI_BUDGET_EXCEEDED` | AI 사용 한도에 걸려 완료하지 못했어요. | 표시(비활성, 한도 회복 후 활성) |
| `AI_RATE_LIMITED` | 요청이 몰려 완료하지 못했어요. | 표시 |
| `CONFIDENTIAL_SUSPECTED` | 회사 코드나 비밀정보로 보여 분석하지 않았어요. | 숨김(새 요청 안내) |
| `INTERRUPTED` | 서버가 다시 시작되어 작업이 중단됐어요. | 표시 |
| `INTERNAL_ERROR` | 문제가 생겨 완료하지 못했어요. | 표시 |

재시도 API: 제출 평가 `POST …/submissions/{submissionNo}/retry`, coach `POST /coach/reviews/{reviewId}/retry`. 러버덕 정리는 재시도가 없어 재시도 버튼을 숨긴다(대화는 남는다). challenge 생성·evidence 초안·로드맵 비교 분석은 retry API가 없으므로 "다시 만들기/다시 요청"(새 요청)으로 처리한다. 재시도 버튼은 리소스의 `retryable=true`일 때만 보인다.

---

## 6. 공통 컴포넌트

위치: `lib/shared/widgets/`. 모든 색·크기는 §10 토큰만 쓴다.

### 6.1 상태 배지 (`StatusBadge`)

- 모양: radius 6, 패딩 세로 2 · 가로 8, 높이 24, 선택적 앞 아이콘 14, 라벨 `labelSmall`. 상호작용 없음.
- **항상 텍스트 라벨을 가진다.** 색은 보조 신호다. Semantics label은 `{그룹}: {라벨}`(예: "검증 상태: 문서 근거").
- 톤 → 토큰: `success`(`color.success` on `color.successContainer`), `info`, `warning`, `danger`, `neutral`, `primary`(§10.2).

| 배지 | 값 | 라벨 | 톤 | 아이콘 |
|---|---|---|---|---|
| `VerificationBadge` | `VERIFIED` | 검증됨 | success | `verified` |
| | `SUPPORTED` | 문서 근거 | info | `menu_book` |
| | `AI_JUDGMENT` | AI 판단 | neutral | `smart_toy` |
| | `UNCERTAIN` | 불확실 | warning | `help` |
| `ConfidenceBadge` | `HIGH` | 확신 높음 ●●● | neutral | — |
| | `MEDIUM` | 확신 보통 ●●○ | neutral | — |
| | `LOW` | 확신 낮음 ●○○ | neutral | — |
| `FindingTypeBadge` | `BUG` | 버그 | danger | `bug_report` |
| | `RISK` | 위험 | warning | `warning` |
| | `LEARNING_POINT` | 더 나은 선택 | info | `lightbulb` |
| `RiskBadge` | `LOW` | 여유 | success | `schedule` |
| | `MEDIUM` | 보통 | info | `schedule` |
| | `HIGH` | 빠듯함 | warning | `schedule` |
| | `CRITICAL` | 매우 빠듯함 | danger | `schedule` |
| `DiscoveredByBadge` | `MENTIONED_UNPROMPTED` | 스스로 언급 | success | `emoji_objects` |
| | `FOUND_AFTER_HINT` | 질문·힌트 후 발견 | info | `search` |
| | `MISSED` | 놓침 | neutral | `radio_button_unchecked` |
| `PriorityBadge` | `MUST` / `SHOULD` / `LATER` | 필수 / 권장 / 나중 | primary / neutral / neutral | — |
| `OutcomeBadge` | `CORRECT` / `PARTIAL` / `INCORRECT` / `NOT_EVALUATED` | 충족 / 일부 충족 / 보완 필요 / 채점 안 함 | success / info / neutral / neutral | — |
| | `SOLVED_INDEPENDENTLY` / `SOLVED_WITH_HINTS` / `PARTIAL` / `FAILED` / `ABANDONED` | 스스로 해결 / 힌트로 해결 / 일부 해결 / 다시 도전 / 그만둠 | success / info / info / neutral / neutral | — |
| `AiBadge` | AI 생성 콘텐츠 | AI | neutral | `smart_toy` |
| `RequirementFitBadge` | `READY` / `STRETCH` / `LATER` / null | 준비됨 / 도전 / 나중에 / 분류 불가 | success / info / neutral / neutral | — |
| `SideProjectStatusBadge` | `ACTIVE` / `PAUSED` / `DONE` | 진행 중 / 잠시 멈춤 / 완료 | primary / neutral / success | — |
| `ReadOnlyLicenseBadge` | 저장소 `license = UNSPECIFIED` | 읽기만 · 복사 금지 | warning | `lock` |

- `MISSED`, `INCORRECT`, `FAILED`에는 danger 톤을 쓰지 않는다(U-3). danger는 `BUG`와 `CRITICAL`, 파괴적 버튼에만 쓴다.
- 확신도 점(●○)은 장식이다. Semantics에서는 제외한다.

### 6.2 Hint Ladder (`HintLadder`)

```text
┌──────────────────────────────┐
│ ✓ 1 질문                 [AI] │  공개됨: 내용 펼침/접힘, AI 생성이면 AI 배지
│   …내용…                      │
│ ┌──────────────────────────┐ │
│ │   2 개념 힌트 보기         │ │  다음 단계: 유일한 활성 버튼(OutlinedButton)
│ └──────────────────────────┘ │
│   3 방향                  🔒  │  잠김: 비활성 텍스트 + 자물쇠
│   4 의사코드 ⚠            🔒  │  ⚠ = 확인 필요 단계(PSEUDOCODE 이상)
│   5 부분 코드 ⚠           🔒  │
│   6 전체 예시 ⚠           🔒  │
│ 지금까지: 질문                  │
│ 맞히면 '스스로 해결'로 인정돼요  │  영향 요약(대상별 문구)
└──────────────────────────────┘
```

| 속성 | 타입 | 설명 |
|---|---|---|
| `targetType` | `HintTargetType` | `CHALLENGE_ATTEMPT` / `COACH_FINDING` |
| `maxHintLevel` | `HintLevel` | 서버 값 |
| `disclosures` | `List<HintDisclosure>` | 공개된 단계·내용·origin |
| `preconditionMet` | `bool` | challenge: self-explanation 있음 / finding: 응답 있음 |
| `submissionCount` | `int?` | challenge만. `FULL_EXAMPLE` giveUp 판단 |
| `aiAvailable` | `bool` | `aiStatus ∉ {DISABLED, BALANCE_EXHAUSTED}` |
| `closed` | `bool` | coach 리뷰 마침·attempt 종료 → 전부 읽기 전용 |
| `onRequest` | `Future<void> Function(HintLevel level, {bool ack, bool giveUp, bool skipSelf})` | |

규칙:
1. 활성 버튼은 `maxHintLevel.ordinal + 1` 단계 하나뿐이다(HL-3 건너뛰기는 UI에서 제공하지 않는다).
2. 다음 단계가 AI 생성 대상(challenge 4~6단계, finding 전 단계)이고 `aiAvailable=false`면 그 버튼도 잠그고 `hint.aiOff` = "AI를 쓸 수 없어 이 단계는 잠시 막혀 있어요."를 보여준다.
3. `PSEUDOCODE` 이상은 `HintConfirmDialog`를 거친 뒤 `ack=true`로 요청한다. `FULL_EXAMPLE`은 challenge 제출 0회 또는 coach finding이면 `giveUp=true`도 보낸다.
4. challenge에서 `preconditionMet=false`면 사다리 전체를 잠근다(§3.7). finding에서 `preconditionMet=false`면 다음 단계 버튼은 활성이지만 누르면 `coach.hint.skipResponse` 대화상자를 거쳐 `skipSelf=true`로 요청한다.
5. 서버에 공개 기록 없이 max가 더 높은 단계(다른 기기에서 건너뛴 경우)는 `건너뜀` 텍스트로 표시한다.
6. 요청 중에는 해당 행에 진행 표시, 다른 행·제출 버튼 비활성. AI 단계는 `hint.generating` = "AI가 힌트를 만들고 있어요 (최대 20초)".
7. 스크린 리더: 각 행 "{n}단계 {라벨}, 공개됨/다음 단계/잠김". 새 힌트가 공개되면 내용을 live region으로 읽는다.
8. coach 리뷰 원문이 삭제됐으면(`contentPurged=true`) 새 단계 버튼을 잠그고 `hint.purged`를 보여준다.
9. 응답 `HintView`로 상태를 갱신한다(`maxHintLevel`, `skippedLevels`). 반환된 `level`은 요청 단계보다 낮을 수 있다(HL-1).

### 6.3 비동기 상태 표시 (`AsyncStatusIndicator` + `AsyncPoller`)

- 표시: 20px 원형 진행 + 주 문구(화면별) + 보조 문구 `async.leaveOk`. 3분 초과 시 진행 표시를 정보 아이콘으로 바꾸고 `async.checkLater` + "다시 확인" 버튼.
- `AsyncPoller`(Riverpod `AutoDisposeNotifier`): 간격 2초, 최대 90회. 종료 조건: 상태가 `COMPLETED`/`FAILED`(또는 리소스별 종료 상태) · 화면 dispose · 90회 도달. 브라우저 탭이 숨겨지면(`visibilitychange=hidden`) 일시정지하고 다시 보이면 남은 횟수로 재개한다. "다시 확인"은 즉시 1회 조회 후 90회 창을 새로 시작한다.
- polling 요청이 네트워크 오류면 횟수를 소모하고 계속한다. 연속 5회 실패하면 멈추고 오프라인/오류 상태를 보여준다.
- 상태 변화는 `Semantics(liveRegion: true)`로 알린다.

### 6.4 빈 상태 (`EmptyState`)

- 가운데 정렬, 최대 폭 360: 아이콘 48(`color.textSecondary`) → 본문 1~2줄(`bodyMedium`) → 버튼 0~1개.
- 버튼은 화면에 다른 primary가 없으면 `FilledButton`, 있으면 `OutlinedButton`.
- 문구는 "없어요" 사실 + 다음 행동 또는 생기는 조건을 함께 쓴다. 일러스트는 쓰지 않는다.

### 6.5 AI 불가 배너 · 예산 경고 (`AiUnavailableBanner`, `BudgetWarningNote`)

| 컴포넌트 | 표시 조건 | 모양 | 문구 |
|---|---|---|---|
| `AiUnavailableBanner` | `meProvider.aiStatus ∈ {DISABLED, BALANCE_EXHAUSTED}` | 앱 바 아래 전체 폭, `color.surfaceVariant` 배경, 아이콘 `cloud_off`, 닫기 없음 | `DISABLED` → `ai.unavailable.banner` = "AI 기능을 지금 쓸 수 없어요. 복습·계획·Today는 그대로 쓸 수 있어요." / `BALANCE_EXHAUSTED` → `ai.balanceExhausted.banner` = "AI 잔액이 떨어졌어요. 충전하면 다시 쓸 수 있어요. 복습·계획·Today는 그대로예요." |
| `BudgetWarningNote` | `aiStatus == BUDGET_WARNING` | AI 실행 버튼 바로 아래 한 줄, 아이콘 `info` + `color.warning` 텍스트 | `ai.budgetWarning.note` = "이번 달 AI 사용량이 예산의 80%를 넘었어요." |
| AI 버튼 비활성 사유 | `aiAvailable == false` | 버튼 아래 `bodySmall` | `DISABLED` → `ai.disabledReason` = "AI를 쓸 수 없어 잠시 막아 두었어요." / `BALANCE_EXHAUSTED` → `ai.balanceExhaustedReason` = "AI 잔액이 떨어져 잠시 막아 두었어요." |

- 배너 표시 화면: SCR-RUBBER-DUCK, SCR-READ-CODE, SCR-TRAINING-LIST, SCR-CHALLENGE-DETAIL, SCR-TRAINING-ATTEMPT, SCR-COACH-LIST, SCR-COACH-NEW, SCR-COACH-DETAIL(마치기 전), SCR-EVIDENCE-DETAIL(초안 생성 중·실패), SCR-REQUIREMENTS-LIST, SCR-REQUIREMENT-NEW, SCR-MORE. **SCR-TODAY, SCR-REVIEW-\*, SCR-PLAN, SCR-PROJECTS, SCR-PROJECT-DETAIL, SCR-PROJECT-NOTE-EDIT, SCR-TIPS, SCR-TIP-DETAIL, SCR-TERMS, SCR-TERM-DETAIL에는 표시하지 않는다**(AI 없이 동작하는 화면). 러버덕 진입 버튼만 있는 화면(SCR-TODAY, SCR-REVIEW-SESSION, SCR-REVIEW-ITEMS, SCR-SKILL-DETAIL, SCR-PROJECTS, SCR-PROJECT-DETAIL)은 배너 없이 그 버튼만 막고 버튼 아래 사유 1줄(`ai.disabledReason`/`ai.balanceExhaustedReason`)을 보인다.
- **배너를 띄우는 기준은 "그 화면이 AI를 쓰는가"다.** Today·복습·계획 화면은 AI가 꺼져 있어도 **하던 일을 그대로 끝낼 수 있으므로** 배너를 띄우지 않는다 — 쓰지도 않는 기능이 멈췄다는 배너는 "지금 아무것도 못 한다"로 읽힌다. 서버도 이 세 기능에서 AI를 호출하지 않는다(`05` §8 Today 모듈, §11 복습의 AI 채점은 선택, §7 Plan 모듈). AI가 꺼져 있을 때 이 화면들이 달라지는 부분은 **배너가 아니라 그 자리에서** 알린다 — Today는 `CHALLENGE`·`READ_CODE` 과제가 새로 제안되지 않고(`06` §5.3), 복습은 AI 채점 스위치가 꺼지며, 러버덕 진입 버튼은 버튼 아래 사유 1줄로 막힌다. 오늘의 팁·용어 화면도 AI를 쓰지 않으므로 같은 기준으로 배너가 없다(§3.17).
- **재현 잠금은 AI 불가와 다르다.** `409 AI_ASSIST_LOCKED_FOR_REDO`(RE-5)는 AI가 멈춘 것이 아니라 **지금 그 대상만** 잠긴 것이므로 배너를 쓰지 않고 해당 버튼 아래 `today.redo.lockedElsewhere` 1줄과 "Today로 가기"를 보인다(§5.1).
- `aiStatus`는 `meProvider` 갱신 시점(§2.4)에 바뀐다. AI 관련 429/503 응답을 받으면 즉시 갱신한다.

### 6.6 API 계층 (`lib/core/api/`)

| 항목 | 규칙 |
|---|---|
| 클라이언트 | dio 인스턴스 1개, `baseUrl = '/api/v1'`(같은 origin, `03` §1). local 개발은 `--dart-define=API_BASE_URL` |
| 인증 | `AuthInterceptor`: 저장된 access token을 `Authorization: Bearer`로. `supabase` 모드는 만료 60초 전 `refreshSession()`, 401이면 1회 갱신 후 재시도. `dev` 모드는 갱신이 없어 401이면 바로 로그아웃 흐름(§5.1) |
| Idempotency-Key | **POST repository 메서드는 `IdempotencyKey key`를 필수 인자로 받는다**(타입으로 강제). 키는 사용자 행동 1회(버튼 탭)마다 UUID v4로 만들고, 그 행동의 notifier state에 `(key, bodyHash)`로 보관한다. 같은 행동을 같은 body로 재시도하면 같은 키, body가 바뀌면 새 키. 성공하면 폐기한다. 예외: `POST /plans/{planId}/replan/preview`는 키를 보내지 않는다 |
| 중복 탭 방지 | 요청 중 버튼 비활성(`isSubmitting`) |
| 재시도 | 응답이 없는 연결 오류·타임아웃만 자동 재시도: 최대 2회, 1초·3초 후. GET은 502/503/504도 같은 규칙으로 재시도. POST는 같은 키로만 재시도하고 5xx 응답은 재시도하지 않는다. `IDEMPOTENCY_IN_PROGRESS`는 1초 간격 최대 5회. `429 RATE_LIMITED`는 자동 재시도하지 않고 `Retry-After`까지 전역 `rateLimitedUntil`을 설정해 `AsyncPoller`를 일시정지한다 |
| 타임아웃 | connect 10초, receive 기본 15초. 동기 AI 요청(`…/hints`, `POST /reviews/{id}/answer`(evaluate=true), `…/responses`, `POST /rubber-duck/{id}/turns`)은 receive 30초, `POST /rubber-duck/{id}/complete`(서버 30초)는 receive 45초 |
| 오류 파싱 | `application/problem+json` → `ApiException(status, code, detail, traceId, fieldErrors)`. `traceId`는 body 또는 `X-Trace-Id` 헤더. 파싱 불가 응답은 `code = INTERNAL_ERROR` |
| 모델 | freezed 수기 모델(DEC-12). 모든 enum에 `unknown` 값 + `@JsonKey(unknownEnumValue: …unknown)`. null 필드 허용 |
| 재생 응답 | `Idempotent-Replayed: true` 응답 body는 최초 처리 시점 스냅샷이다. 202 재생이면 곧바로 `pollPath`를 GET해 최신 상태를 읽는다. 캘린더 토큰 재생은 `feedUrl=null`(§3.14) |
| 날짜 | instant는 `DateTime.parse(...).toUtc()` 저장, 표시 때 `/me.timezone`으로 변환(`timezone` 패키지). plan date는 `String` 그대로 → 표시 시 `LocalDate` 파싱 |
| 로깅 | 요청·응답 body를 콘솔에 남기지 않는다. debug 빌드만 method·path·status·traceId |

### 6.7 토스트 · 대화상자 · 시트

- 토스트: floating SnackBar, 기본 4초, 최대 2줄, 액션 최대 1개. 연속 토스트는 이전 것을 교체한다.
- 대화상자: 제목 1줄, 본문, 버튼 최대 2개(왼쪽 취소 `TextButton`, 오른쪽 확인). 파괴적 확인은 `color.danger` 텍스트 버튼. "확인" 단독 라벨 금지 — 동작 이름을 쓴다.
- 입력 폼 모달: 폭 < 600은 bottom sheet(드래그 핸들, 키보드 위로 올라옴), ≥ 600은 대화상자(최대 폭 480).

### 6.8 이탈 확인 (`UnsavedChangesGuard`)

- 대상: SCR-ONBOARDING ①~④, SCR-RUBBER-DUCK(작성 중 설명), SCR-PROJECTS(편집 시트), SCR-PROJECT-NOTE-EDIT, SCR-REVIEW-ITEM-EDIT, SCR-TRAINING-ATTEMPT(작성 중 설명·답안), SCR-COACH-NEW, SCR-COACH-DETAIL(작성 중 답변), SCR-REPLAN, SCR-LEARNING-GOAL, SCR-WEEKLY-DETAIL(회고), SCR-EVIDENCE-DETAIL, SCR-REQUIREMENT-NEW, SCR-SETTINGS.
- 앱 안 이동(go_router `onExit`, `PopScope`): 대화상자 `common.unsaved.title` = "저장하지 않은 내용이 있어요", `common.unsaved.body` = "나가면 입력한 내용이 사라져요.", 버튼 "계속 작성" / "나가기".
- 브라우저 탭 닫기·새로고침: dirty 상태일 때 `beforeunload` 기본 확인을 켠다.

---

## 7. 접근성 · 반응형

| # | 항목 | 규칙 | 확인 방법 |
|---|---|---|---|
| A-1 | 터치 대상 | 모든 상호작용 요소 ≥ 44×44 CSS px. Material 기본 `minimumSize 48×48`, 칩·아이콘 버튼 hit area 48 | widget test(`tester.getSize`), 수동 |
| A-2 | 대비 | 본문 텍스트 ≥ 4.5:1, 큰 텍스트(18px 굵게/24px) ≥ 3:1, 아이콘·입력 테두리·포커스 링 ≥ 3:1. §10.2 토큰은 이 기준으로 정했다 | 토큰 대비표, 수동 |
| A-3 | 색 외 표시 | 상태·등급·배지·차트는 텍스트 라벨 또는 텍스트 요약을 함께 둔다. 폼 오류는 아이콘 + 텍스트 | 리뷰 체크리스트 |
| A-4 | 포커스 순서 | 위 → 아래, 왼쪽 → 오른쪽. 데스크톱 2열 화면은 왼쪽 열 → 오른쪽 열. 대화상자·시트는 포커스를 안에 가두고 닫히면 연 버튼으로 돌려준다. 라우트 이동 후 포커스는 화면 제목(`Semantics(header: true)`) | 키보드 수동 점검 |
| A-5 | 포커스 표시 | 2px `color.focus` 외곽선, 요소와 2px 간격 | 수동 |
| A-6 | 스크린 리더 | web 시작 시 `SemanticsBinding.instance.ensureSemantics()`를 호출한다(성능 영향은 SP-1에서 확인). 아이콘 버튼은 `tooltip` + semantics label. 진행 표시 "카드 6장 중 2번째". 비동기 상태·토스트·새 힌트는 live region | TalkBack(Android Chrome), VoiceOver(iOS Safari) 수동 |
| A-7 | 키보드 | 모든 기능을 Tab·Enter·Space·Esc로 사용. 한 줄 입력은 Enter 제출, 여러 줄 입력은 `Ctrl/Cmd+Enter` 제출. Esc는 대화상자·시트 닫기 | 수동 |
| A-8 | 복습 단축키(데스크톱) | 텍스트 입력 포커스가 없을 때 `Space` 답 확인, `H` 힌트, `1` 다시 · `2` 어려움 · `3` 알맞음 · `4` 쉬움, `E` AI 채점 스위치. 평가 버튼에 숫자를 표시한다(§3.6) | widget test(키 이벤트) |
| A-9 | 코드 입력 | `Tab`은 공백 4칸 입력. `Esc`를 누른 다음 `Tab`은 포커스 이동(포커스 갇힘 방지). 입력칸 아래 안내 `common.codeField.tabHint` = "Esc 후 Tab으로 다음 항목으로 이동" | 수동 |
| A-10 | 글자 크기 | 브라우저·OS 글자 크기 200%에서 잘림·겹침 없음. `textScaler > 1.3` 또는 폭 < 360이면 복습 평가 버튼을 2×2 격자로, SCR-SKILL-DETAIL 학습 단계 6칸을 3+3 두 줄로 바꾼다 | golden test(textScale 2.0) |
| A-11 | 폭 | 360px에서 모든 SCR 가로 스크롤 없음. 코드 뷰어, SCR-READ-CODE의 `cloneHint` 상자, SCR-TIP-DETAIL의 `example` 상자만 내부 가로 스크롤 허용 | golden test(360×640) |
| A-12 | 움직임 | `MediaQuery.disableAnimations`면 skeleton shimmer·페이지 전환 애니메이션 끔 | 수동 |
| A-13 | 한국어 입력 | 조합 중 Backspace·커서 이동·붙여넣기, 코드 붙여넣기 시 탭·공백 유지(스파이크 SP-1, `11` §4). IME 조합 중에는 Enter 제출·단축키를 무시한다(`TextEditingValue.composing` 확인) | 스파이크 SP-1 체크리스트 |
| A-14 | 문서 언어 | `web/index.html`에 `<html lang="ko">` | 빌드 확인 |

---

## 8. PWA

### 8.1 매니페스트 · 빌드

| 항목 | 값 |
|---|---|
| `name` / `short_name` | DevPilot / DevPilot |
| `start_url` / `scope` | `/today` / `/` |
| `display` | `standalone` |
| `background_color` / `theme_color` | `color.bg` (#F7F8FA) / `color.primary` (#2F5BD3) |
| `lang` | `ko` |
| icons | 192×192, 512×512, 512×512 `purpose: maskable` (PNG) |
| 웹 리소스 | CanvasKit 등 Flutter 웹 런타임 파일을 CDN이 아니라 자체 제공한다(빌드 옵션은 구현 시 확인). 외부 폰트 요청 없음(§10.3) |
| service worker | 앱 셸(`index.html`, JS, wasm, assets, fonts)만 캐시한다. `index.html`은 network-first, 나머지는 해시 파일명 cache-first. **`/api/*` 응답은 캐시하지 않는다.** Flutter 기본 service worker를 쓸 수 없는 버전이면 같은 규칙의 최소 `sw.js`를 직접 둔다(구현 시 확인) |

### 8.2 설치 안내 (`InstallPrompt`)

| 환경 | 판별 | 동작 |
|---|---|---|
| Android Chrome, 데스크톱 Chrome·Edge | `beforeinstallprompt` 이벤트 수신(JS interop으로 보관) | SCR-TODAY 하단에 설치 카드 `pwa.install.card` + "설치"(→ `prompt()`) + "닫기". SCR-SETTINGS "앱 설치"에서도 "설치" 버튼 |
| iOS·iPadOS Safari | UA가 iOS이고 `navigator.standalone != true` | 같은 카드, 버튼은 "방법 보기" → 시트 `pwa.install.ios.steps` |
| 이미 설치됨 | `display-mode: standalone` 매치 | 카드·버튼 숨김 |
| 그 외 | — | SCR-SETTINGS에 일반 안내 `pwa.install.generic`만 |

- 카드 노출 조건: 서로 다른 plan-day에 2번째 방문 이상 + 최근 30일 안에 닫지 않음(`localStorage` `devpilot.install.dismissedAt`, try/catch).
- 문구: `pwa.install.card` = "홈 화면에 추가하면 앱처럼 바로 열 수 있어요.", `pwa.install.button` = "설치", `pwa.install.howto` = "방법 보기", `pwa.install.dismiss` = "닫기", `pwa.install.ios.steps` = "1. Safari 아래쪽 공유 버튼을 누르세요.\n2. '홈 화면에 추가'를 고르세요.\n3. 오른쪽 위 '추가'를 누르세요.", `pwa.install.generic` = "Chrome이나 Edge 주소창 오른쪽의 설치 아이콘으로 설치할 수 있어요."
- iOS standalone에서 GitHub OAuth는 같은 창 redirect로 진행해 PWA 컨텍스트를 유지한다. 세션이 PWA로 돌아오지 않으면 SP-5에서 대안을 정한다(구현 시 확인).

### 8.3 오프라인 동작 (결정)

| 항목 | MVP 결정 |
|---|---|
| 쓰기 | **오프라인 쓰기 없음.** 큐에 쌓았다가 나중에 보내지 않는다. 저장·전송 버튼 비활성 |
| 읽기 | 이번 실행 동안 메모리(Riverpod)에 이미 있는 데이터만 읽기 전용으로 표시. API 데이터를 `localStorage`·IndexedDB·service worker에 저장하지 않는다(개인정보, 단순성) |
| 첫 실행이 오프라인 | 앱 셸은 캐시로 뜨고, `GET /me` 실패 → 전체 화면 `offline.fullPage` + "다시 시도" |
| 표시 | 앱 바 아래 `OfflineBanner`(`offline.banner`) |
| 복귀 | `online` 이벤트 → 배너 제거 → 현재 화면 provider 재조회 → polling 중이던 화면은 polling 재개 |
| 작성 중 입력 | 화면 state와 §3의 draft 저장(`localStorage`, 사용자가 쓴 입력만)으로 유지 |

### 8.4 업데이트

- 새 service worker가 설치되면 SnackBar(자동으로 닫히지 않음) `pwa.update.available` = "새 버전이 있어요." + "새로고침". 누르면 §6.8 이탈 확인을 거친 뒤 reload.
- `UNKNOWN_ENUM_VALUE` 오류(§5.1)도 새로고침을 안내한다.

---

## 9. 문구 톤 가이드

| 규칙 | 예 (피할 것 → 쓸 것) |
|---|---|
| 해요체, 한 문구 최대 2문장, 한 문장 40자 안팎 | "학습 계획이 성공적으로 생성되었습니다." → "계획을 만들었어요." |
| 평가 대신 관찰과 제안 | "틀렸습니다." → "이 부분은 다시 보면 좋아요." |
| 결과 라벨은 사실만, 감정 수식 금지 | "아쉽게도 실패했어요 😢" → "다시 도전" |
| 죄책감·압박 표현 금지: N일째 쉬고 있어요, 연속 기록이 끊겼어요, 아직도, 벌써, 꼭·반드시(강요), 느낌표 남용 | "3일째 공부를 안 했어요!" → (표시하지 않음) / "다시 시작해도 괜찮아요. 오늘은 가볍게 시작해요." |
| 연속 학습 일수는 사실 한 줄로만. 목표 일수·불꽃·"이어 가세요"를 붙이지 않는다(U-3, §3.11) | "🔥 5일 연속! 내일도 이어 가세요" → "이어서 학습한 날 5일" |
| 결과물을 시간보다 먼저 쓴다 | "이번 주 3시간 10분 공부했어요" → "이번 주에 만든 것 3개 · 공부한 시간 3시간 10분" |
| `BUG`는 분명하게, `LEARNING_POINT`는 틀리지 않았음을 먼저 | "버그일 수도 있을 것 같아요" → "실제 오류 가능성이 높아요" / "틀린 것은 아니에요. 더 나은 선택을 알아 두면 좋아요" |
| AI 출처를 밝히고 단정하지 않는다 | "이 코드는 안전하지 않습니다." → [AI 판단] "입력값이 그대로 쿼리에 들어가 위험할 수 있어요." |
| 위험은 사실 + 다음 행동. 겁주기 금지 | "이대로면 목표일을 못 지켜요." → "마감 위험 빠듯함 · 필수 목표에 필요 약 98시간, 가능 약 82시간 · 계획 조정" |
| 오류는 무슨 일 + 할 수 있는 것. 기술 정보는 접힌 영역에만 | "500 Internal Server Error (traceId …)" → "문제가 생겼어요. 잠시 후 다시 시도해 주세요." |
| 식별자·기술 용어·API·enum은 영어 원문 | "스프링 트랜잭셔널 어노테이션" → "`@Transactional`" |
| 버튼은 동작 이름(2~8자). "확인"·"예" 단독 금지 | "확인" → "새로 만들기", "마치기", "힌트 보기" |
| 사용자 관점 라벨은 "내" | "사용자 응답" → "내 답변", "내 생각" |
| 숫자에는 기준을 함께 | "72%" → "복습 기억 72%" |
| 이름 호칭·이모지·과장 금지 | "최고예요, 민수님! 🔥" → "수고했어요" |

---

## 10. 테마 토큰

### 10.1 원칙

- MVP는 **라이트 테마만** 제공한다. 다크 모드는 Later다.
- 토큰은 `lib/app/theme/tokens.dart`에 두고 `ThemeExtension<DevPilotColors>`와 `ThemeData.colorScheme`에 매핑한다. **위젯에서 `Color(0x…)`·`Colors.*`를 직접 쓰지 않는다**(CI에서 `lib/app/theme/` 밖의 `Color(0x`를 grep으로 실패 처리).
- 다크 모드 추가 시 토큰 이름은 유지하고 `DevPilotColors.dark` 값만 추가한다.

### 10.2 색 토큰

| 토큰 | 값 | 용도 | 대비 (배경 대비) |
|---|---|---|---|
| `color.bg` | `#F7F8FA` | 앱 배경 | — |
| `color.surface` | `#FFFFFF` | 카드, 시트, 입력 배경 | — |
| `color.surfaceVariant` | `#EEF1F5` | 배너, 접힌 영역, neutral 배지 배경 | — |
| `color.border` | `#D5DAE1` | 카드 구분선(장식) | — |
| `color.borderStrong` | `#8A939E` | 입력 테두리, 체크박스 외곽 | 3.1:1 (surface) |
| `color.textPrimary` | `#1B1F24` | 본문, 제목 | 16:1 (surface) |
| `color.textSecondary` | `#505A66` | 보조 텍스트, 아이콘 | 7.0:1 (surface) |
| `color.textDisabled` | `#8A939E` | 비활성 텍스트 | 3.1:1 (비활성 요소는 대비 기준 예외) |
| `color.primary` | `#2F5BD3` | primary 버튼, 링크, 선택 상태 | 흰 텍스트 5.9:1 |
| `color.onPrimary` | `#FFFFFF` | primary 위 텍스트 | — |
| `color.primaryContainer` | `#E3EAFB` | 선택 칩 배경, primary 배지 배경 | — |
| `color.onPrimaryContainer` | `#1C3C94` | primaryContainer 위 텍스트 | 8.2:1 |
| `color.focus` | `#2F5BD3` | 포커스 링 | 5.9:1 (surface) |
| `color.success` / `color.successContainer` | `#1E7B45` / `#E6F4EC` | success 톤 | 4.7:1 |
| `color.info` / `color.infoContainer` | `#1F5FA8` / `#E8F1FB` | info 톤 | 5.6:1 |
| `color.warning` / `color.warningContainer` | `#8A5A00` / `#FFF4DB` | warning 톤, 코드 줄 강조 배경 | 5.5:1 |
| `color.danger` / `color.dangerContainer` | `#B42318` / `#FDECEA` | danger 톤, 파괴적 버튼(흰 텍스트 6.6:1) | 5.7:1 |
| `color.neutral` / `color.neutralContainer` | `#505A66` / `#EEF1F5` | neutral 톤 | 6.1:1 |
| `color.skeleton` | `#E4E8ED` | skeleton 블록 | — |
| `color.codeBg` | `#F3F5F8` | 코드 입력·뷰어 배경 | textPrimary 15:1 |
| `color.scrim` | `#000000` 40% | 모달 뒤 | — |

- 배지 톤 `{tone}`은 전경 `color.{tone}`, 배경 `color.{tone}Container`다(§6.1). 대비 칸은 전경/배경 조합 값이다.
- 차트: 단일 계열은 `color.primary`, 보조선·축은 `color.textSecondary`. 여러 계열(주간 관점 추세)은 success·info·neutral 톤을 쓰고 반드시 범례 텍스트와 표 보기를 함께 제공한다.

### 10.3 타이포그래피

| 항목 | 결정 |
|---|---|
| 본문 글꼴 | **Pretendard**(SIL OFL 1.1) Regular 400 · SemiBold 600 · Bold 700을 `assets/fonts/`에 **번들**한다. 사용자 입력의 모든 한글 음절을 표시하기 위해 subset하지 않는다. 런타임 외부 폰트 요청(`google_fonts` 패키지) 금지 |
| 코드 글꼴 | **JetBrains Mono**(SIL OFL 1.1) Regular 400 번들 |
| 라이선스 | OFL 전문을 `assets/licenses/`에 두고 `LicenseRegistry`에 등록 |
| fallback | `fontFamilyFallback: ['Apple SD Gothic Neo', 'Noto Sans KR', 'sans-serif']` |

| 스타일 | 크기/줄높이 | 굵기 | 용도 |
|---|---|---|---|
| `displaySmall` | 28/36 | 700 | 빈 화면·완료 화면 큰 제목 |
| `titleLarge` | 22/28 | 600 | 화면 제목 |
| `titleMedium` | 18/24 | 600 | 섹션 제목, 카드 제목 |
| `bodyLarge` | 16/24 | 400 | 본문, **모든 텍스트 입력**(iOS 확대 방지로 16 미만 금지) |
| `bodyMedium` | 15/22 | 400 | 목록 보조 본문 |
| `bodySmall` | 13/18 | 400 | 안내, 캡션 |
| `labelLarge` | 15/20 | 600 | 버튼 |
| `labelSmall` | 12/16 | 600 | 배지 |
| `code` | 14/20 | 400 (JetBrains Mono) | 코드 입력·뷰어 |

### 10.4 간격 · 모양 · 크기

| 토큰 | 값 |
|---|---|
| `space.xs` / `sm` / `md` / `lg` / `xl` / `xxl` | 4 / 8 / 12 / 16 / 24 / 32 |
| `radius.sm` / `md` / `lg` / `full` | 6(배지) / 8(버튼·입력) / 12(카드·시트 상단) / 999(칩) |
| `size.button` / `size.input` / `size.listTile` / `size.navBar` | 높이 48 / 최소 48 / 56 / 64 |
| elevation | 카드 0 + `color.border` 1px, 시트·대화상자는 Material 3 기본 |
| 화면 여백 | 모바일 좌우 16, 섹션 간 24 |

### 10.5 움직임

| 토큰 | 값 | 용도 |
|---|---|---|
| `motion.fast` | 150ms | 버튼·칩 상태 변화 |
| `motion.page` | 250ms | 라우트 전환(fade-through) |
| `motion.shimmer` | 1200ms | skeleton |

`MediaQuery.disableAnimations`가 true면 모두 0으로 둔다(A-12).
