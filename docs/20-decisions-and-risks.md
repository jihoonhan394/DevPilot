# 20. Decisions & Risks

> Status: Accepted (v2) · Last updated: 2026-09-19 · Related: `14-adrs.md`, `11-development-roadmap.md`, `13-product-backlog.md`, `16-definition-of-ready-done.md` §4
>
> 적용된 기본 결정(DEC), 구현 시 다시 확인할 외부 사실과 문서 정합성 항목, 리스크 레지스터, 이번 주 할 일을 모은다. 결정 이유의 상세는 ADR에 있다. 이 문서는 단계 종료마다 갱신한다(`16-definition-of-ready-done.md` §4 S-7).

---

## 1. 적용된 결정 (DEC-01 ~ DEC-28)

모든 DEC는 기본값으로 **이미 적용되었다**. 바꾸려면 §2 절차를 따른다.

| ID | 적용된 결정 | 검토한 대안 | 변경 방법 · 시점 | 관련 ADR |
|---|---|---|---|---|
| DEC-01 | 사용자: 소유자 + 초대 최대 2명(allowlist). 사용자 간 데이터 공유 없음 | 소유자 1명만 / 공개 가입 | allowlist env 변경만으로 인원 조정. 3명 초과나 공유 기능은 ADR 필요(AI 예산·격리 재검토). 언제든 | ADR-023 |
| DEC-02 | Java 25 LTS (Eclipse Temurin). SP-4 실패 시 Java 21 | Java 21 고정 | SP-4 결과로 S0 안에서 확정. 이후 변경은 toolchain·Dockerfile·CI 동시 수정 PR | ADR-005 |
| DEC-03 | Spring Boot 4.1.x | Boot 3.5.x(OSS 종료) | 4.2 GA(2026-11-30 예정) 후 S5에 업그레이드 여부 판단. 4.1 OSS 종료(2027-07-31) 전 필수 | ADR-005 |
| DEC-04 | GitHub 저장소 public + secret scanning·push protection | private | private 전환 시 ADR-016(배포 경로)·CI 무료 범위 재검토. 언제든 | ADR-028, ADR-016 |
| DEC-05 | AI 공급자 **DeepSeek**, 모델 `deepseek-flash`, 설정으로 교체(2026-09-18 변경) | Anthropic Claude(단가 약 30배) · `deepseek-v4-pro` | `DEVPILOT_AI_MODEL`·operation 설정 변경 + eval 통과. 공급자 변경은 ADR. 언제든 | ADR-032 (ADR-011 대체) |
| DEC-06 | AI 월 예산 **USD 3**(**서비스 전체**, Asia/Seoul 달력 월), 일일 호출 60회/사용자(plan-day), 동시 2개/사용자. **외부 하드 캡 없음 — 선불 잔액이 상한**이고 잔액 < USD 1이면 `BALANCE_EXHAUSTED`. 피크 요금은 항상 ×2로 보수 계산 | 사용자별 월 예산 / USD 25 / 피크 시각 규칙 구현 | `DEVPILOT_AI_MONTHLY_BUDGET_USD` 등 설정 변경. 증액 시 잔액을 먼저 충전한다. `BL-AIP-14` 실측 후(S5) 재검토 | ADR-035 (ADR-029 보완), ADR-012 |
| DEC-07 | **당분간 도메인을 쓰지 않는다.** 주소는 tailnet 호스트명(`https://<tailnet-host>`), TLS는 `tailscale serve`가 처리. **임시**이며 공개 전환 시 도메인 또는 OCI 공개 IP로 간다 | 처음부터 구매 도메인 + ACME(S0가 밀림) | 공개 전환 게이트에서 결정(`11` §3). 시점 미정 | ADR-031(임시), ADR-006(후보) |
| DEC-08 | 로그인: **devtoken**(이메일 입력 → backend 서명 토큰, allowlist). **비공개 배포 전용**이며 공개 전환 전에 Supabase Auth GitHub OAuth로 바꾼다 | Supabase Auth 즉시 도입(S0 2주 증가) | **공개 노출 전 필수**: `BL-SEC-18` 완료 → `DEVPILOT_AUTH_MODE=supabase`. 그 전에는 바꿀 이유 없음 | ADR-033(조건부), ADR-023(공개 시 복귀) |
| DEC-09 | Java 포맷: google-java-format AOSP(4칸), 100열 | google-java-format 기본(2칸) | 코드가 쌓이기 전(S1 종료 전)에만 변경. 이후 변경은 전체 재포맷 PR 1개 | ADR-010, ADR-019 |
| DEC-10 | Flutter 상태관리 Riverpod (+ go_router, dio, freezed) | Bloc, Provider | S1 종료 이후 변경 금지(전체 재작성) | ADR-025 |
| DEC-11 | 계정 삭제 시 운영자가 **allowlist에서 이메일을 제거**한다(runbook). Supabase 모드에서는 Auth 사용자도 대시보드에서 수동 삭제 | backend가 Admin API로 자동 삭제 | 초대 사용자 운영이 번거로워지면 ADR. S6 이후 | ADR-033 |
| DEC-12 | Flutter API 모델: OpenAPI 스냅샷 기준 수기 freezed 모델 | openapi-generator | 모델 불일치 버그가 Sprint당 2건 이상이면 생성기 도입 ADR | ADR-025 |
| DEC-13 | 배포 접속: Tailscale. **Actions는 amd64 이미지 빌드·GHCR push까지만 하고 배포는 로컬에서 `ssh <server> deploy.sh`로 수동 실행**(2026-09-18 변경). 공개 SSH 없음 | Actions가 tailnet에 합류해 자동 배포(설정 4종 필요) | 배포 빈도가 늘면 Tailscale OAuth·ACL 추가 + ADR-034 대체 | ADR-034 (ADR-016 보완) |
| DEC-14 | 기본 역할 목표: ALGORITHM은 SHOULD, EXPLANATION(기술 설명)은 MUST — 자기 코드와 결정을 설명하는 능력이 러버덕 루프의 중심이다 | 알고리즘 문제 풀이 중심(ALGORITHM MUST) | role target YAML 수정 + `catalog_version` 증가. 학습 목표에 알고리즘 문제 풀이가 핵심으로 들어가면(늦어도 S5 replan 전) | ADR-026 |
| DEC-15 | DB는 **자체 서버의 공용 PostgreSQL 16**(DB/role `devpilot`). Supabase는 Later이고 도입 시 Data API를 비활성화한다(2026-09-18 변경) | Supabase PostgreSQL 17 즉시 도입 | Supabase 전환 시 `pg_dump`/`pg_restore` + ADR-001·ADR-014 복원 | ADR-030 (ADR-001 대체), ADR-014 |
| DEC-16 | coach 코드 원문 보존 30일 | 0일 / 7일 | `devpilot.privacy.coach-content-retention-days` 변경 + 화면 안내 문구. 단축은 언제든, 연장은 ADR | ADR-013 |
| DEC-17 | 복습 HARD 간격 ×1.2 | HARD 2일 고정(`FIXED_2`) | `devpilot.review.hard-strategy` 설정. 4주 사용 후(`BL-CNT-14`, S5) | ADR-018 |
| DEC-18 | 모바일: PWA 먼저, Android 앱은 Later | Android 앱 동시 | Later 백로그(`BL-CLI-30`). iOS/Android PWA 제약이 실사용을 막으면 재검토 | ADR-024 |
| DEC-19 | 호스팅(**임시, 공개 전환 게이트 전**): 기존 자체 Linux 서버(Rocky 9, x86_64, 공용 Docker/PG) + Tailscale HTTPS. 최종 목표는 **공개 배포**이며 방식은 (a) 자체 서버 웹 공개 (b) OCI Always Free 중 그때 결정 (2026-09-18 사용자 확인) | 처음부터 OCI·도메인(S0 2주 증가) | 공개 전환 게이트(`11` §3): Supabase Auth + ACME + Caddy 공개 publish. 이미지는 이미 amd64·arm64 멀티아치라 어느 쪽이든 그대로 배포된다 | ADR-031(임시), ADR-006(후보), ADR-033 |
| DEC-20 | 개발·테스트 DB도 같은 서버 PostgreSQL 16, Testcontainers 이미지 `postgres:16` (2026-09-18) | 로컬 Docker PG / Testcontainers 17 | 운영 DB major가 올라갈 때 함께 올린다 | ADR-030 |
| DEC-21 | 로컬 테스트용 Docker는 **서버 Docker를 SSH 소켓 포워딩**으로 쓴다(Docker Desktop 불필요, 2026-09-18 검증) | 로컬 Docker Desktop 설치 | 로컬에 Docker를 깔면 터널 없이 그대로 동작한다. 언제든 | — |
| DEC-22 | git 전략: `feature/BL-*` → `developer`(squash) → `main`(merge commit), 태그 `v0.<sprint>.<patch>`로 릴리스. CI는 두 브랜치 push·PR (2026-09-18, 사용자 방식) | `main` 단일 브랜치 | 협업자가 생기면 재검토. 언제든 | — |
| DEC-23 | S2 범위 축소: `POST /plans`·REVIEW_VARIANT·계정 삭제 job은 Later, 수동 카드 CRUD는 S3, Trivy·CodeQL은 S2 P1 (2026-09-18). **ICS 캘린더(S3)와 replan 제안(S5) 부분은 DEC-25가 대체** | 원래 범위 유지 | 실사용 시작 후 여유가 생기면 되돌린다 | ADR-037 |
| DEC-24 | **학습 루프 v3**: 읽는다(큐레이션 오픈소스 `READ_CODE`) → 만든다(사이드 프로젝트, 기본 "주문 시스템") → 러버덕으로 설명한다(AI는 질문만, `NoAnswerGuard`) → 반복한다(gaps → 복습 카드, 교차 학습). 계획 템플릿은 주문 시스템을 만드는 순서 9개 milestone(1~6 MUST, 7~9 SHOULD), 기한 역산 양방향(확장 제안), 온보딩 진단 우선 (2026-09-18 사용자 확인) | 기존 루프(개념 → 연습문제 → 복습) 유지 · AI가 GitHub 저장소를 실시간 검색 · DevPilot 자체를 사이드 프로젝트로 | 큐레이션 저장소 교체는 `content/curated-repos.yaml` + `catalogVersion` 증가(`19` §8.4). 러버덕 턴 상한 등은 `devpilot.rubberduck.*` | ADR-036 |
| DEC-25 | **날짜 없는 단계**: S0~S7은 구현 순서 ID(기간 없음), M1 = S0~S3(S3 완료 = 실사용 시작), M2 = S4~S7. budget·risk·replan 제안 S5 → S2, ICS 캘린더·AI 문제 생성 S3 → S5, CSP 강제 S3 → S4, evals v1 S4 → S3, 진단 P1 → P0 (2026-09-18) | 날짜 고정 스프린트 유지 · ID를 M1/M2로 전면 교체 | M2 단계 순서는 실사용 데이터를 보고 바꾼다(여기에 기록) | ADR-037 |
| DEC-26 | 러버덕 설명 증거의 coverage는 **고정 7000**(`devpilot.rubberduck.evidence-coverage-bp`) — gaps 0·턴 ≥ 3인 세션만, E2·E3에 쓰이고 E4에는 닿지 않는다. 독립은 `hintDisclosed = false`일 때 (2026-09-18) | AI가 coverage를 매김(AI가 레벨 입력을 직접 결정 — `AGENTS.md` 금지) · 8000(E4 가능 — 전이 증거 없이 E4가 되어 과대평가) | 4주 사용 후 레벨이 실제 설명력과 어긋나면 설정값만 바꾼다 | ADR-036 |
| DEC-27 | **학습 목표 = 무엇을, 언제까지**: 학습 트랙(`targetRole`)과 **목표일**(`targetCompletionDate`) 하나. 목표일은 내일 ~ 오늘+3년. budget·risk horizon은 목표일이고(`06` §3.1), 계획 템플릿 배치는 창 하나 `[today, 목표일]`에 PREPARATION milestone을 앞에, CONSOLIDATION("설명과 정리")을 목표일 바로 앞에 `weightBp`로 배분한다(`19` §5, vector V1~V8). 사용자 프로필은 표시 이름·timezone·하루 시작 시각·학습 시간만 받는다. 온보딩 1단계는 "무엇을, 언제까지 공부할지 정해요"(표시 이름·학습 트랙·목표일, 빠른 선택 3개월 후·6개월 후·1년 후·직접 선택). 화면의 날짜 표시는 "목표일 {date}" 하나다. S7 기능 이름은 **로드맵 비교**: 공개 학습 로드맵이나 기술 목록을 붙여넣으면 항목별 READY/STRETCH/LATER(식별자 `radar`·`requirement_*`·`REQUIREMENT_EXTRACT`는 그대로) (2026-09-19 사용자 결정) | 필수 항목용 날짜를 따로 두어 창·horizon을 둘로 나누기 · 개발 경험 프로필로 시작 수준을 조정하기 | 목표일 값은 사용자가 설정에서 언제든 바꾼다(`replan_recommended`). 날짜 모델·배치 규칙을 바꾸려면 ADR | ADR-039 |
| DEC-28 | **소스 점검과 읽기 평가**: `READ_CODE` 저장소·읽기 단위는 사람이 주기적으로 점검한다(`19` §8.5) — 첫 점검은 S3 구현 시작 직전, 이후 단계 회고마다와 사용자 요청 시. 입력은 빈틈 목록(낮은 레벨·막히는 복습·읽을 단위가 없는 skill), 읽기 평가, 저장소의 라이선스·유지 상태. 후보 기준은 OSI 라이선스(Apache-2.0·MIT 우선, 라이선스 없는 저장소부터 교체)·활발한 유지·가까운 스택·다룰 만한 크기·테스트·읽기 쉬운 도메인이고, 큰 실제 프로젝트의 부분 읽기를 허용한다. 결과는 사용자와 정한 추가·교체·은퇴이며 `pinnedCommit` 고정·경로·줄 재확인·검증기·`catalogVersion` +1로 반영한다. 은퇴한 단위는 지난 과제를 위해 계속 조회된다. 자동 실행과 서버의 저장소·코드 조회는 없다. `READ_CODE`를 완료할 때 선택 평가 "도움 됐어요·어려웠어요·지루했어요"(`readingFeedback`, `learning_task.reading_feedback`)를 받고, 어떤 규칙의 입력으로도 쓰지 않는다(`BL-CNT-16`) (2026-09-19 사용자 결정) | 저장소 자동 추천·수집(URL fetch 금지와 충돌) · 평가를 planner·레벨 규칙 입력으로 쓰기(자기 신고라 흔들린다) | 점검 시점·기준은 `19` §8.5를 고친다. 평가를 규칙 입력으로 쓰려면 ADR | ADR-036 |

## 2. 결정 변경 절차

1. 변경 이유와 영향 범위(문서, 코드, 운영, 비용)를 적은 `Proposed` ADR을 만든다(기존 ADR은 `Superseded by`로 표시).
2. 영향받는 canonical 문서(03·04·06, `database/schema.sql`)와 05·07·17 등을 같은 PR에서 고친다.
3. 사용자가 ADR을 `Accepted`로 바꾸고 이 표의 DEC 행을 갱신한다.
4. 진행 중 Sprint에 영향이 있으면 `13-product-backlog.md`에 BL을 추가하거나 이월을 기록한다.

에이전트는 DEC를 바꾸지 않는다. 필요하면 구현을 멈추고 ADR 초안과 질문을 보고한다.

---

## 3. 구현 시 재확인 항목

### 3.1 외부 사실 (S0부터 해당 BL 착수 전에 확인)

확인하면 결과와 날짜를 해당 ADR 또는 이 표의 "결과" 칸에 적는다.

| # | 확인할 사실 | 어디서 확인 | 언제 (BL) | 결과 |
|---|---|---|---|---|
| E-01 | Boot 4.1 property 이름: `spring.jpa.open-in-view`, `spring.mvc.problemdetails.enabled`, `spring.security.oauth2.resourceserver.jwt.audiences`·`jws-algorithms`, structured logging(`logging.structured.format.*`), `spring.threads.virtual.enabled`, actuator probe 설정 | Spring Boot 4.1 reference "Common Application Properties", Boot 4.0 migration guide, 기동 로그의 unknown property 경고 | S0 (`BL-FND-03`) | |
| E-02 | ~~`anthropic-java` 런타임 공존~~ | — | — | **무효**(2026-09-18): AI SDK를 쓰지 않고 Spring `RestClient`로 직접 호출한다(ADR-032). 의존성 제거 |
| E-03 | DeepSeek `/responses` 응답 형식: 미완료 표시(`status`·`incomplete_details.reason` 추정), usage 필드 이름(`input_tokens_details.cached_tokens`, `output_tokens_details.reasoning_tokens`), json_schema가 받는 키워드 범위($defs/$ref/enum/pattern) | 실제 호출 1회 + api-docs.deepseek.com | S3 (`BL-AIP-03`) | 기본 호출·구조화 출력은 2026-09-18 확인. 세부 필드는 구현 시 |
| E-04 | DeepSeek 요청 파라미터: `thinking {type}`·`reasoning_effort` 조합, `store`, `instructions`, `max_output_tokens` 상한, thinking on일 때 추론 토큰이 출력 상한에 포함되는지 | api-docs.deepseek.com + 실제 호출 | S3 (`BL-AIP-03`) | thinking 기본 on, 추론 토큰이 `output_tokens`에 포함됨(2026-09-18 확인) |
| E-05 | ~~Supabase pooler 커스텀 role~~ | — | — | **무효**(2026-09-18): 자체 서버 PostgreSQL 16 직접 접속(ADR-030). Supabase 도입 시 되살린다 |
| E-06 | ~~Supabase Data API·JWT 동작~~ | — | — | **무효**(2026-09-18): devtoken 모드에서는 backend가 claim을 직접 만든다(ADR-033). Supabase 도입 시 되살린다 |
| E-07 | ~~Supabase 무료 플랜 한도~~ | — | — | **무효**(2026-09-18). 대신 서버 디스크 여유와 공용 PG 용량을 `10` §11 점검표에서 본다 |
| E-08 | ~~OCI Always Free 한도·회수 정책~~ | — | — | **무효**(2026-09-18): 자체 서버 사용(ADR-031) |
| E-09 | Flutter 3.47.4 web 빌드의 엔진 리소스 로컬 번들 옵션(CDN 미사용 플래그)과 CSP 요구사항(`wasm-unsafe-eval` 등) | `flutter build web -h`, docs.flutter.dev web 배포·CSP 문서 | S0 (SP-1), S3 (`BL-SEC-08`) | |
| E-10 | DeepSeek 데이터 정책(학습 사용 여부·보존·처리 위치) | api-docs.deepseek.com, 플랫폼 약관 | 확인 완료 | **2026-09-18**: 학습 미사용 약정 없음 / zero-retention 없음 / 중국 처리. 사용자 결정으로 수용(ADR-013) |
| E-11 | 단가(USD/1M tokens): `deepseek-flash` 0.15 / 캐시적중 0.003 / 출력 0.60, `deepseek-v4-pro` 0.66 / ? / 1.98, 피크(월~금 UTC 01–04·06–10) ×2. **pro의 캐시적중 단가 미확인** | api-docs.deepseek.com/quick_start/pricing | 확인(2026-09-18), 이후 분기 1회 | flash 3종 확인. pro 캐시적중은 입력 단가로 보수 계산 중 |
| E-12 | GHCR package public 설정과 서버에서의 pull 권한(public이면 익명 pull 가능) | GitHub Packages 문서 | S0 (SP-3) | runner는 `ubuntu-24.04`(amd64) 사용 — 서버가 x86_64로 확인됨(2026-09-18) |
| E-13 | Tailscale HTTPS(MagicDNS + HTTPS Certificates)와 `tailscale serve --https=443`의 동작·인증서 갱신 | Tailscale 문서 + 서버 실측 | S0 | Tailscale 1.98.8, `serve`·`cert` 사용 가능(2026-09-18). 관리 콘솔 활성화는 사용자 작업 |
| E-14 | Spring Boot 지원 일정: 4.1 OSS 종료 2027-07-31, 4.2 GA 2026-11-30 예정 | spring.io support 페이지 | S0, S5 | 2026-09-17 확인 |
| E-15 | `eclipse-temurin:25-jre` **amd64** 태그 제공, Temurin 25.0.x 최신 patch | Docker Hub, adoptium.net | S0 (`BL-OPS-05`) | |

**검증된 버전 (2026-09-17 빌드 기준 · 2026-09-18 인프라 항목 갱신)** — 버전을 올릴 때는 CI 통과와 이 표 갱신을 같은 PR에서 한다.

| 구성 요소 | 버전 | 비고 |
|---|---|---|
| Gradle | 9.7.1 | wrapper |
| Eclipse Temurin | 25.0.4 | toolchain |
| Spring Boot | 4.1.1 | |
| springdoc-openapi | 3.1.1 | 실행 확인은 SP-4 |
| ArchUnit | 1.5.0 | 실행 확인은 SP-4 |
| Spotless / google-java-format | 8.10.2 / 1.36.1 | AOSP 스타일 |
| Checkstyle | 14.1.0 | |
| PMD | 7.27.0 | |
| SpotBugs | 4.10.4 | |
| JaCoCo | 0.8.15 | |
| Flutter | 3.47.4 | FVM 고정 |
| Caddy | 2.11.4 | |
| PostgreSQL | **16** | 자체 서버(16.14)·Testcontainers(`postgres:16`) 동일 major |
| Tailscale | 1.98.8 | 서버 설치본 (2026-09-18 확인) |
| Docker Engine | 29.6.1 | 서버 설치본 |

### 3.2 문서 정합성 확인 항목

canonical 문서끼리 또는 canonical과 다른 v2 문서 사이에서 발견한 불일치다. 해당 BL 착수 전에 문서를 고치고 이 표의 상태를 `해결`로 바꾼다.

| # | 내용 | 영향 BL | 결정 기한 | 제안 해소안 | 상태 |
|---|---|---|---|---|---|
| DOC-01 | `03-system-architecture.md` §2.2 모듈 의존표로는 규칙 입력을 읽을 수 없는 경우가 남아 있다: `plan`(completion rate의 `daily_plan`·`learning_session`, `06` §3.3), `review`(horizon cap의 학습 목표·due 정렬의 plan priority, `06` §6.2·§6.5), `evidence`(지표의 attempt·review answer·snapshot·요구사항 문서, `06` §12), `user`(export의 전 모듈 데이터), `today`(AI 상태에 따른 CHALLENGE 제외) | BL-MEM-03·04(S2), BL-TDY-14(S3), BL-GOL-11(S2), BL-EVD-01(S5), BL-SEC-13(S6) | S2 시작 전 | port로 해소: `plan.application.StudyHistoryProvider`(today 구현), `evidence.application.RequirementCoverageProvider`(radar 구현), `common.time.UserTimeSettingsProvider`(user 구현), `integration.ai.api.AiPendingJobCounter`(coach·training·review·evidence·radar 구현), 03 §2.2 의존표 갱신. 백로그 BL-GOL-11, BL-EVD-01, BL-REQ-04, BL-AIP-10에 반영 | 해결 |
| DOC-02 | `05-api-spec.md`의 `POST /onboarding` 처리(seed 카드 복사, snapshot upsert)가 S1에 필요한 테이블·규칙보다 앞선다(`review_item`은 `04` §10 V4=S2, risk 계산은 S5) | BL-GOL-06, BL-MEM-08, BL-GOL-13 | S1 시작 전 | 백로그 방식: S1은 해당 단계 생략(`assignedSeedCardCount = 0`, `latestRiskLevel = null`), S2 기동 시 기존 사용자 카드 배정, S5에 snapshot 연결. 05에 Sprint 주석 추가 | 해결: 백로그 방식 채택 — S1 온보딩은 카드 배정·snapshot 생략, S2 기동 시 backfill, S5 snapshot (`04` §9) |
| DOC-03 | `06` §10 규칙 4는 "https URL이고 allowlist 밖"만 강등한다. `http://` URL, URL이 아닌 문자열, reference 없음인 `OFFICIAL_DOC`·`SECURITY_GUIDE`의 결과가 표(allowlist 호스트일 때만 SUPPORTED)와 다르게 해석될 수 있다 | BL-AIP-08, AC-07 | S4 시작 전 | 표 기준으로 통일: "https URL이 아니거나 호스트가 allowlist 밖이면 강등". 06 규칙 문구와 vector 3행 추가 | 해결: `06` §10 규칙 4 "https URL이 아니거나 allowlist 밖이면 강등" (`17` §6 vector) |
| DOC-04 | AI 월 예산의 범위·월 경계 서술이 문서마다 다르다(사용자별 vs 서비스 전체, UTC 월 vs 사용자 timezone 월). 확정값은 서비스 전체 + Asia/Seoul 달력 월(ADR-029) | BL-AIP-10, AC-13 | S3 시작 전 | `05-api-spec.md`, `07-security-and-privacy.md`, `17-ai-integration.md`의 해당 서술을 ADR-029에 맞춘다 | 해결: `05` §1.9, `07` §12.1, `17` §8.2 모두 서비스 전체 + Asia/Seoul 달력 월 (ADR-029) |
| DOC-05 | `RATE_LIMITED` 발생 위치: `05-api-spec.md` 오류 카탈로그는 캘린더 피드만, `03`·`07-security-and-privacy.md`는 사용자당 120 req/min 일반 한도도 정의 | BL-SEC-11 | S2 시작 전 | 05 카탈로그에 일반 요청 한도 추가 | 해결: `05` §1.3 `RATE_LIMITED`에 사용자당 120 req/min 일반 한도 포함, §1.4.3 검사 순서 반영 |
| DOC-06 | `SECRET_DETECTED_BLOCKED` 발생 endpoint: `05-api-spec.md` 카탈로그는 coach 2곳, `17-ai-integration.md`는 저장되는 모든 자유 입력 | BL-AIP-09, AC-14 | S3 시작 전 | 05 카탈로그·endpoint 절을 17 적용 위치 표와 맞춘다 | 해결: `05` §1.11 masking 대상 endpoint 표와 각 endpoint 422 반영 |
| DOC-07 | Migration 파일 이름: `04` §10과 `repo-seed`가 `V5__training.sql`로 통일됨 | BL-FND-16 | — | — | 해결 |
| DOC-08 | `daily_plan.available_minutes` CHECK는 0~720, API·planner는 5~720 | BL-TDY-07 | S2 | API 검증을 기준으로 두고 CHECK는 다음 migration에서 5~720으로 좁히거나 차이를 04에 명시 | 해결: `schema.sql`·V4 CHECK를 5~720으로 변경 (migration 재생성·검증) |
| DOC-09 | `06` §2 `planDate`의 "− dayStartHour hours"가 instant 기준인지 벽시계 기준인지 DST timezone에서 결과가 다르다(Asia/Seoul은 영향 없음) | BL-FND-10 | S1 | `ZonedDateTime.minusHours`(instant 기준)로 명시하고 DST vector 1행 추가, 또는 timezone을 DST 없는 지역으로 제한 | 해결: 벽시계 기준으로 명시 + DST vector 추가 (`06` §2) |
| DOC-10 | `06` §5.9 `force = true` + main `IN_PROGRESS`에서 기존 `PLANNED` REVIEW task를 지우고 다시 만드는지 정의 없음 | BL-TDY-07 | S2 | PLANNED 행 규칙과 같게: PLANNED task 삭제 후 재생성 | 해결: PLANNED REVIEW task 삭제 후 재생성, 진행·완료된 REVIEW task는 유지 (`06` §5.9) |

---

## 4. 리스크 레지스터

확률(P)·영향(I): H/M/L. 소유자는 모두 사용자다. Sprint 종료마다 조기 신호를 점검하고 상태(`감시`/`발생`/`종료`)를 갱신한다.

| ID | 리스크 | P | I | 대응 | 조기 신호 · 트리거 | 소유자 | 상태 |
|---|---|---|---|---|---|---|---|
| RISK-01 | DevPilot 개발이 실제 학습 시간을 잠식해 목표일까지의 학습이 늦어진다 | H | H | **DevPilot 구현은 전부 에이전트에 위임한다**(2026-09-18: DevPilot은 학습 도구이지 그 자체가 학습 과제나 사이드 프로젝트가 아니므로 직접 구현할 이유가 없다 — RISK-09 취소). 날짜 없는 단계와 M1 우선(ADR-037), S2가 끝나면 AI 없는 Today·Review를 먼저 써도 된다, 사용자 작업은 결정·리뷰·계정 설정으로 한정, S1·S2·S3 단계 회고(`11` §2.2) | 사용자 투입 > 4h/주, 단계 재작업률 > 30%. **실사용 시작 + 14 plan-day stop-loss 판정 불충족 시 Coach·Evidence·로드맵 비교를 Later로 이동**(`11-development-roadmap.md` §6) | 사용자 | 감시 |
| RISK-02 | AI 비용이 예산을 넘는다 | L | M | 서비스 전체 월 USD 3 가드, 일일 60회·동시 2개, 피크 ×2 보수 계산, 선불 잔액 소액 유지, seed hint·캐시, `BL-AIP-14` 실측 후 thinking·effort 조정 | 주간 비용 > USD 0.9(월 예산 30%), `BUDGET_WARNING`이 월 20일 이전 발생 | 사용자 | 감시 |
| RISK-03 | AI 코드 리뷰 품질 문제(오탐, 누락, 잘못된 사실) | M | H | eval v1(오탐 함정·injection 포함), 검증 배지와 강등 가드, `VERIFIED` 제한, 질문형 finding, DISMISSED 기록 | 최근 finding 20개 중 DISMISSED > 30%, eval recall < 기준 | 사용자 | 감시 |
| RISK-04 | **단일 서버 SPOF** — 정전·ISP 장애·하드웨어 고장·Proxmox 호스트 문제로 서비스와 DB가 함께 멈춘다 | M | H | 상태를 서버 밖(GHCR 이미지 + 로컬 백업)에도 둔다, `prepare-server.sh`로 재구축, healthchecks.io 알림, 오프라인이어도 학습 자체는 가능(종이·IDE) | healthping 실패 알림, 서버 재부팅 로그 | 사용자 | 감시 |
| RISK-05 | **공용 Docker·PostgreSQL 간섭** — 다른 사용자의 컨테이너·PG 재시작·업그레이드·자원 점유가 DevPilot에 영향을 준다. 백업이 같은 호스트에 있으면 함께 잃는다 | M | H | JVM `-Xmx1g`·`mem_limit 1.5g`로 자원 점유 제한, `devpilot` DB/role 범위 밖을 건드리지 않음, OS 설정 미변경(`prepare-server.sh`), **주 1회 로컬 PC로 백업 pull**, S6 복구 리허설 | health 실패, PG 접속 오류, 디스크 여유 < 5GB | 사용자 | 감시 |
| RISK-06 | Flutter Web 한글 입력·폰트·초기 로딩 문제로 서술 입력이 불편하다 | M | M | SP-1, 폰트 번들, 입력 위젯 `<textarea>` 대체, 실패 시 웹 스택 변경 ADR | SP-1 실패 케이스, 실사용 중 입력 손실 1회 이상 | 사용자 | 감시 |
| RISK-07 | Boot 4.1·Java 25 자료 부족과 에이전트의 구버전(Boot 3) 패턴 생성 | H | M | 검증된 버전 표(§3.1), `AGENTS.md` 버전 규칙, Initializr 좌표, SP-4, 컴파일·deprecation 경고를 CI에서 실패 처리 | Sprint당 같은 유형 컴파일 오류·deprecated 경고 3회 이상, unknown property 경고 | 사용자 | 감시 |
| RISK-08 | 회사 코드·secret이 외부 AI로 전송되거나 저장된다 | L | H | 요청마다 기밀 동의, 모든 자유 입력 masking·private key 차단, 원문 30일, 프롬프트 원문 미저장, 공급자 약관 확인(ADR-013) | 감사 이벤트 `SECRET_BLOCKED`, AI `CONFIDENTIAL_SUSPECTED` 실패 | 사용자 | 감시 |
| RISK-09 | ~~에이전트 위임 과다로 본인이 DevPilot 핵심 코드를 설명하지 못한다~~ | — | — | **취소(2026-09-18)**: DevPilot은 공부에 쓰는 도구이지 그 자체가 학습 결과물이 아니어서 DevPilot 코드를 직접 설명할 수 있어야 할 이유가 없다. 전제가 틀렸으므로 이 리스크와 그 대응(`11` §5 직접 구현 영역 4개)을 삭제하고, 구현 전량 위임으로 RISK-01을 줄인다 | — | 사용자 | ~~종료~~ |
| RISK-10 | planner 추천이 체감상 맞지 않아 사용을 멈춘다 | M | H | reason 표시, skip·defer·재생성 쉬움, score breakdown 저장, 4주 후 가중치 튜닝(`BL-CNT-14`) | 2주 연속 main task `SKIPPED` 비율 > 50%, 재생성 평균 > 2회/일 | 사용자 | 감시 |
| RISK-11 | **DeepSeek 데이터 정책** — 서버가 중국에 있고 입력을 학습에 쓰지 않는다는 약정이 없다 | H | M | 학습용 프로젝트로 수용(ADR-013), 회사 코드 금지 동의·secret 마스킹·원문 30일, 동의 문구에 명시 | 회사 코드를 다루게 됨, 공급자 정책 변경 | 사용자 | **수용** |
| RISK-16 | **선불 잔액 소진(402)으로 AI 기능이 통째로 멈춘다** | M | M | `AiBalanceCheckJob` 매시 확인 + `BALANCE_EXHAUSTED` 표시, 비-AI 기능은 정상(AC-12), 잔액 소액 상시 유지 | `AI_BALANCE_LOW` 감사 이벤트, 잔액 < USD 1 | 사용자 | 감시 |
| RISK-17 | **모델 은퇴** — DeepSeek 모델 수명이 짧다(`deepseek-chat`·`reasoner`가 2026-07 종료) | M | M | 모델 ID는 설정값, eval로 교체 검증(`BL-AIP-13`), 공지 확인을 §3.1 분기 점검에 포함 | 공급자 공지, 400 `model not found` | 사용자 | 감시 |
| RISK-18 | **Tailscale 의존** — tailnet 장애·계정 문제·인증서 갱신 실패 시 전체 접근 불가(모바일 복습 포함) | L | H | `tailscale serve`가 인증서를 자동 갱신, 서버는 LAN에서도 접근 가능, 장애 시 로컬 실행으로 학습 지속 | Tailscale 상태 페이지, `tailscale status` 실패, HTTPS 인증서 오류 | 사용자 | 감시 |
| RISK-12 | 계정 탈취(GitHub, DeepSeek, Tailscale, healthchecks.io, 서버 SSH) | L | H | 2FA/passkey(지원하는 서비스), API key 최소 범위, 선불 잔액 소액 유지, 서버는 키 인증 전환, 키 유출 runbook | 알 수 없는 로그인 알림, 잔액 급감, 서버 auth 로그 | 사용자 | 감시 |
| RISK-13 | 외부 로드맵·기술 목록과 큐레이션 저장소 수집의 약관·저작권 문제 | L | M | 자동 수집 없음, 사용자가 붙여넣기, 서버 URL fetch 금지, 로드맵 비교 원문 180일 purge. 큐레이션 저장소는 소스 점검(`19` §8.5)에서 라이선스를 확인하고 라이선스 없는 저장소는 코드를 옮겨 적지 않는다 | 공개 로드맵 가져오기(`BL-REQ-05`) 요구 발생(Later에서 약관 검토 선행) | 사용자 | 감시 |
| RISK-14 | 1인 운영 피로·장애 대응 부담 | M | M | 관리형 서비스 우선, 자동 백업·롤백, runbook, 알림 최소화(uptime·백업 실패만) | 운영 작업이 주 1시간 초과, 같은 수동 작업 월 3회 이상 | 사용자 | 감시 |
| RISK-15 | 무료 플랜·모델 단가·SDK·지원 일정이 문서 작성 후 바뀐다 | M | M | §3.1 분기별 재확인, 설정 기반 단가·모델, 검증된 버전 표, 4.2 업그레이드 판단(S5) | 공급자 공지, 단가 변경, Boot 4.1 patch 중단 공지 | 사용자 | 감시 |

---

## 5. 이번 주 할 일 (2026-09-18 금 ~ 09-21 일)

인프라 결정(2026-09-18)이 끝나 §1 DEC 표와 ADR-030~035에 반영되었다. 남은 것은 아래다.

**사용자가 직접 (저장소 밖 `DevPilot-ops/00-todo-now.md`와 같은 목록)**

- [ ] Tailscale 관리 콘솔 → DNS → **MagicDNS + HTTPS Certificates 켜기** (없으면 `tailscale serve --https=443`가 인증서를 못 받는다)
- [ ] healthchecks.io 무료 계정 + check 1개 생성 → ping URL 확보 (`BL-OPS-13`)
- [ ] DeepSeek 잔액 확인(월 USD 3 기준 여유분), 5 USD 아래면 충전
- [ ] GitHub 저장소: `developer` 브랜치 생성, `main`·`developer` ruleset(PR 필수 + 필수 check 4개), environment `ai-eval`에 `DEEPSEEK_API_KEY`
- [x] 서버 개발 DB 확인(2026-09-18): SP-2 때 스키마가 남아 있지 않았다. 로컬 `bootRun`이 V1~V9를 적용했다(`04` §10)
- [ ] 서버: `prepare-server.sh` 실행 → `/opt/devpilot/api.env` 작성(`DEVPILOT_DEV_JWT_KEY`, `DEVPILOT_LOG_HASH_KEY`는 `openssl`로 생성) → `tailscale serve --bg --https=443 http://127.0.0.1:18080`

**저장소 준비**

- [ ] `repo-seed/` 내용을 저장소 루트로, `docs/`·`database/` 복사 후 첫 푸시 (`BL-FND-02`)
- [ ] 에이전트에게 `START_HERE_FOR_AI_AGENT.md`로 S0 지시

**끝난 것 (2026-09-18)**

- [x] DEC-01~23 확정. 인프라·AI 공급자·인증·배포 결정을 ADR-030~035로 기록
- [x] 서버·DB·DeepSeek 키·서버 Docker 원격 사용 연결 테스트 (§3.1 E-03·E-04·E-10·E-11·E-12·E-13 갱신)
- [x] 문서 3회차 교차 검토(약 90건)와 반영
- [x] §3.2 DOC-01~DOC-10 문서 정합성 항목 반영 완료(2026-09-17)
- [x] v3 학습 루프 설계 반영(DEC-24~26, ADR-036·037): 러버덕·코드 읽기·사이드 프로젝트·교차 학습·확장 제안·진단 우선, 계획 템플릿 재작성, 큐레이션 저장소 3개·reading 13개, 날짜 없는 단계(M1/M2)
