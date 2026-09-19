# 14. Architecture Decision Records

> Status: Accepted (v3) · Last updated: 2026-09-18 · Related: `20-decisions-and-risks.md`, `03-system-architecture.md`, `11-development-roadmap.md` §4
>
> 되돌리기 비용이 큰 결정과 그 이유를 기록한다. 결정을 바꿀 때는 기존 ADR을 고치지 않고 `Superseded by ADR-xxx`로 표시한 뒤 새 ADR을 추가한다. 적용된 기본값(DEC)은 `20-decisions-and-risks.md` §1, 확인할 외부 사실은 같은 문서 §3이 기준이다.

---

## 0. 운영 규칙

| 항목 | 규칙 |
|---|---|
| 새 ADR | 다음 번호(ADR-039~)로 추가. 필드: Status, Date, Context, Decision, Alternatives considered, Consequences, Related DEC |
| Status 값 | `Proposed`(에이전트 초안, 사용자 승인 전) · `Accepted` · `Superseded by ADR-xxx` |
| 에이전트 | 문서 모순·미정 결정을 발견하면 구현을 멈추고 `Proposed` ADR 초안을 PR로 올린다(`11-development-roadmap.md` §7). `Accepted`로 바꾸는 것은 사용자만 한다 |
| Spike 결과 | SP-1~SP-5 결과는 해당 ADR의 Consequences에 "SP-n 결과 (날짜)" 줄로 추가한다 |
| 파일 분리 | 저장소에서는 `docs/adr/ADR-0xx-<slug>.md`로 나눌 수 있다. 그 경우 이 문서는 색인과 요약을 유지한다 |

## 1. 색인

| ADR | 제목 | Status | Related DEC |
|---|---|---|---|
| ADR-001 | PostgreSQL 단일 DB (Supabase) | **Superseded by ADR-030** | DEC-15 |
| ADR-002 | Modular monolith와 모듈 의존 규칙 | Accepted | — |
| ADR-003 | Flutter Web 우선 클라이언트 | Accepted (조건: SP-1 통과) | DEC-10, DEC-18 |
| ADR-004 | 자체 IDE를 만들지 않는다 | Accepted | — |
| ADR-005 | Java 25 + Spring Boot 4.1.x | Accepted (v1 "Java 21 + Boot 3.5.x"를 대체) | DEC-02, DEC-03 |
| ADR-006 | OCI Ampere A1 + Oracle Linux 9 | **Deferred** — 공개 전환 시 채택 후보 | DEC-07, DEC-13 |
| ADR-007 | DB가 장기 학습 기억 | Accepted | — |
| ADR-008 | 결정적 planner, LLM은 콘텐츠만 | Accepted | — |
| ADR-009 | Verification 분류 + 서버 가드 + DB CHECK | Accepted | — |
| ADR-010 | 코딩 스타일: google-java-format AOSP, 영어 식별자, 자동 강제 | Accepted | DEC-09 |
| ADR-011 | AI 공급자 Anthropic `claude-opus-5`, port 뒤에 두고 설정으로 교체 | **Superseded by ADR-032** | DEC-05 |
| ADR-012 | 비동기 AI 작업: DB 상태 + in-process executor, MQ 없음 | Accepted | DEC-06 |
| ADR-013 | AI 데이터 보존과 공급자 약관 | Accepted (2026-09-18 DeepSeek 확인 결과 반영) | DEC-16 |
| ADR-014 | 전용 스키마 `devpilot` + Supabase Data API 비활성 | Accepted (현재 DB에서는 DO 블록이 no-op) | DEC-15 |
| ADR-015 | RFC 9457 Problem Details + 오류 코드 카탈로그 | Accepted | — |
| ADR-016 | Tailscale 배포 접속, 공개 SSH 차단 | Accepted (자동화 부분은 **ADR-034**가 대체) | DEC-04, DEC-13 |
| ADR-017 | Plan-day 경계 (`dayStartHour`) | Accepted | — |
| ADR-018 | 규칙 기반 복습 스케줄 v1 (HARD ×1.2) + `review_answer` 기록 | Accepted | DEC-17 |
| ADR-019 | 아키텍처 규칙을 ArchUnit·Checkstyle·PMD로 강제 | Accepted | DEC-09 |
| ADR-020 | 규칙 계산은 정수(bp·micro) | Accepted | — |
| ADR-021 | Plan versioning: `plan_skill_target` + flush 후 INSERT | Accepted | — |
| ADR-022 | 모든 인증 POST에 `Idempotency-Key` 필수 | Accepted | — |
| ADR-023 | GitHub OAuth 단일 로그인 + allowlist | **Superseded by ADR-033** | DEC-01, DEC-08, DEC-11 |
| ADR-024 | PWA 먼저, Android 앱은 Later | Accepted | DEC-18 |
| ADR-025 | Flutter: Riverpod + go_router + dio + freezed | Accepted | DEC-10, DEC-12 |
| ADR-026 | Seed 콘텐츠는 저장소 YAML, 기동 시 적재 | Accepted | DEC-14 |
| ADR-027 | AI 호출은 DB 트랜잭션 밖에서 | Accepted | — |
| ADR-028 | Public GitHub 저장소 + secret scanning | Accepted | DEC-04 |
| ADR-029 | AI 월 예산은 서비스 전체 한도 | Accepted (Console 한도 부분은 **ADR-035**가 대체) | DEC-06 |
| ADR-030 | 자체 서버의 공용 PostgreSQL 16 | Accepted | DEC-15 |
| ADR-031 | 자체 Linux 서버 + Tailscale HTTPS (**임시 배포**) | Accepted (임시) | DEC-07, DEC-13, DEC-19 |
| ADR-032 | AI 공급자 DeepSeek `deepseek-flash`, RestClient 직접 호출 | Accepted | DEC-05 |
| ADR-033 | 개발 토큰(devtoken) 인증 — **비공개 배포 전용** | Accepted (조건부) | DEC-01, DEC-08 |
| ADR-034 | 배포는 수동 트리거, Actions는 이미지 빌드까지 | Accepted | DEC-13 |
| ADR-035 | AI 지출 상한은 선불 잔액 + 앱 예산 (외부 하드 캡 없음) | Accepted | DEC-06 |
| ADR-036 | 학습 루프: 읽는다 → 만든다 → 러버덕으로 설명한다 → 반복한다 | Accepted | DEC-24, DEC-26 |
| ADR-037 | 날짜 없는 단계(M1/M2) | Accepted | DEC-25 |
| ADR-038 | 첫 릴리스 전 migration 확정, 이후 불변 | Accepted | — |

---

## ADR-001 PostgreSQL 단일 DB (Supabase)

- **Status**: **Superseded by ADR-030** (2026-09-18) · **Date**: 2026-09-17 · **Related DEC**: DEC-15

**Context** — 사용자 3명 이하, 데이터는 수천~수만 행이다. 인프라 비용은 무료 플랜 안이어야 한다(NFR-01). 인증도 관리형이 필요하다.

**Decision** — Supabase PostgreSQL 17 하나만 쓴다. 캐시 서버·검색 엔진·두 번째 DB를 두지 않는다. backend만 JDBC(shared pooler, session mode, TLS)로 접근한다. 로컬·테스트는 PostgreSQL 17(Docker, Testcontainers)로 major 버전을 맞춘다.

**Alternatives considered** — MySQL 병행(운영 복잡도 증가, Supabase와 무관) · OCI VM에 PostgreSQL 자체 운영(백업·패치 부담, VM 회수 위험) · Supabase 클라이언트 직접 접근 + RLS(도메인 규칙을 서버에서 강제할 수 없음).

**Consequences** — 무료 플랜 제약(500MB, 7일 비활성 일시정지, 자동 백업 없음)을 운영으로 보완한다(`BL-OPS-11`, `BL-OPS-16`). 연결 방식은 SP-2 결과로 확정한다. Supabase 전용 role은 migration에서 조건부 DO 블록으로만 다룬다.

---

## ADR-002 Modular monolith와 모듈 의존 규칙

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: —

**Context** — 1인 개발·운영, 단일 VM(2 OCPU / 12GB). 구현의 대부분을 AI 에이전트가 하므로 경계가 코드로 강제되지 않으면 무너진다.

**Decision** — Spring Boot 애플리케이션 1개, 단일 인스턴스. 패키지 `com.devpilot.<module>`로 모듈을 나누고, 모듈 간 허용 의존은 `03-system-architecture.md` §2.2 표로 고정한다. 다른 모듈은 `application` 공개 서비스·DTO와 `domain`의 enum·불변 record만 쓴다. 역방향 알림은 Spring application event. 표에 없는 의존은 ArchUnit으로 빌드를 실패시킨다(ADR-019).

**Alternatives considered** — MSA(운영 비용, 분산 트랜잭션) · 계층형 단일 패키지(경계 없음, 에이전트가 임의 호출) · Spring Modulith 전면 채택(학습 비용, Boot 4.1 호환 확인 필요 — 선택 도구로만 검토).

**Consequences** — 새 의존이 필요하면 03 §2.2와 ArchUnit 규칙을 같은 PR에서 바꾼다. 스케줄 job은 단일 인스턴스 전제이며, 인스턴스를 늘리면 분산 락 ADR이 필요하다. 메시지 큐는 쓰지 않는다(ADR-012).

---

## ADR-003 Flutter Web 우선 클라이언트

- **Status**: Accepted (조건: SP-1 통과) · **Date**: 2026-09-17 · **Related DEC**: DEC-10, DEC-18

**Context** — PC에서 계획·코치 작업, 출퇴근 중 휴대폰에서 5분 복습을 한 코드베이스로 제공해야 한다. Flutter Web은 canvas 렌더링이라 한글 IME 조합, 폰트 fallback, 초기 로드에 알려진 이슈가 있다.

**Decision** — 클라이언트는 Flutter Web(PWA)으로 만든다. 한글 폰트를 번들한다. 채택은 SP-1 합격 기준(`11-development-roadmap.md` §4) 통과를 조건으로 한다.

**Alternatives considered** — React/Next.js 웹(한글 입력 안정, 모바일 앱 별도) · 네이티브 Android + 웹 2벌(작업량 2배) · Flutter 모바일 앱만(PC 사용성 부족).

**Consequences** — DOM 기반 E2E 자동화가 어려워 UI E2E는 Flutter `integration_test` 1~2개 흐름으로 제한하고 API E2E를 두껍게 한다. CSP에 `wasm-unsafe-eval`이 필요하다. SP-1 실패 시 입력 위젯을 `<textarea>` 플랫폼 뷰로 대체하고, 그래도 실패하면 이 ADR을 Superseded로 바꾸고 웹 스택 변경 ADR을 추가한다(API는 유지).

---

## ADR-004 자체 IDE를 만들지 않는다

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: —

**Context** — Project Coach는 실제 코드가 필요하지만, 코드 편집·실행 환경을 만드는 것은 제품 목표(개발 역량 학습 코칭)와 멀고 비용이 크다.

**Decision** — 코드는 브라우저에 붙여넣기(코드·diff·로그, 30KB·1,000줄 이하)로만 받는다. 코드 에디터·실행기를 만들지 않는다. IDE 연동은 Later(JetBrains plugin → VS Code)이며 선택 코드·diff 전송만 한다.

**Alternatives considered** — 웹 IDE 임베드(Monaco 등, Flutter Web과 통합 비용) · 서버 코드 실행 샌드박스(보안·비용, Later `BL-TRN-14`).

**Consequences** — MVP에는 `COMPILER`/`TEST_RESULT`/`STATIC_ANALYSIS` 근거가 없으므로 AI가 주장해도 강등된다(ADR-009). challenge 평가는 rubric 기반 AI 판정이다.

---

## ADR-005 Java 25 + Spring Boot 4.1.x

- **Status**: Accepted — v1 ADR-005 "Java 21 + Spring Boot 3.5.x"를 대체 · **Date**: 2026-09-17 · **Related DEC**: DEC-02, DEC-03

**Context** — Spring Boot 3.5.x OSS 지원은 2026-06-30에 끝났다(마지막 OSS 3.5.16). 4.1.x OSS 지원은 2027-07-31까지, 4.2.0 GA는 2026-11-30 예정이다(2026-09-17 확인). JDK 25는 LTS이고 Eclipse Temurin으로 제공된다. 신규 스택이라 품질 도구·SDK 호환이 불확실하고, AI 에이전트는 Boot 3 패턴을 생성하는 경향이 있다.

**Decision** — Eclipse Temurin 25 + Spring Boot 4.1.x 최신 patch, Gradle Kotlin DSL + version catalog. 의존성 좌표는 Spring Initializr 결과를 기준으로 하고 기억으로 쓰지 않는다. SP-4에서 Spotless·Checkstyle·PMD·SpotBugs·springdoc·ArchUnit 중 하나라도 실패하면(AI SDK는 쓰지 않는다 — ADR-032) **Java 21(Temurin) + Boot 4.1.x**로 내린다.

**Alternatives considered** — Java 21 + Boot 3.5.x(보안 패치 없음) · Java 21 + Boot 4.1.x(안전하지만 LTS 수명이 짧음 — fallback으로 유지) · Kotlin(학습 목표와 불일치).

**Consequences** — `AGENTS.md`에 버전 기준과 "Boot 3 설정을 추측으로 쓰지 않는다"를 둔다. property 이름은 S0에 Boot 4.1 문서로 재확인한다(`20-decisions-and-risks.md` §3.1). 4.1 OSS 종료(2027-07-31) 전에 4.2로 올리는 작업을 Later 백로그로 등록한다. SP-4 결과를 이 ADR에 추가한다.

**SP-4 결과 (2026-09-18)** — 통과. Temurin 25.0.4 + Gradle 9.7.1 + Boot 4.1.1(Framework 7.0.9, Security 7.1.1, Hibernate 7.4.5, Jackson 3.1.5, Flyway 12.4.0, Testcontainers 2.0.5)에서 Spotless(google-java-format 1.36.1 AOSP)·Checkstyle 14.1.0·PMD 7.27.0·SpotBugs 4.10.4·JaCoCo 0.8.15·springdoc 3.1.1·ArchUnit 1.5.0이 함께 동작하고 `./gradlew check`(단위 40 + 통합 24, `postgres:16`은 서버 Docker 터널)가 통과했다. Java 21 fallback은 필요 없다. 확인하면서 바로잡은 것: (1) Spring Security 7의 `NimbusJwtDecoder.withPublicKey`는 RSA 전용이라 EC P-256 devtoken 검증은 `withJwkSource`(메모리의 공개 JWK)로 한다(`03` §4.2) (2) Boot의 `MessageSource` 자동 설정은 기본 파일 `messages.properties`가 있어야 켜진다 — 빈 기본 파일을 두고 문구는 `messages_ko.properties`에 둔다 (3) YAML flow mapping(`{ … }`) 안의 `${VAR:}` 값은 따옴표가 필요하다 (4) Swagger 모델 API의 raw 타입은 `-Werror`에 걸리므로 변수에 담아 쓴다.

---

## ADR-006 OCI Ampere A1 + Oracle Linux 9

- **Status**: **Deferred** (2026-09-18) — 대체된 것이 아니라 **공개 전환 시 채택 후보**다. 아래 내용은 그대로 유효하며, 현재는 ADR-031의 임시 배포를 쓴다 · **Date (원)**: 2026-09-17
- **2026-09-18 메모**: 사용자 확인 — 최종 목표는 공개 배포이고, 방식은 (a) 자체 서버를 웹서버로 공개 (b) **OCI Always Free 배포** 둘 중 그때 결정한다. 그래서 이 ADR의 OCI 절차(A1 인스턴스, arm64 이미지, idle reclamation 대응, 도메인·ACME)는 폐기하지 않고 보존한다. 컨테이너 이미지를 `linux/amd64,linux/arm64` 멀티아치로 빌드하는 이유도 이것이다(Ampere A1은 arm64) · **Date**: 2026-09-17 · **Related DEC**: DEC-07, DEC-13

**Context** — 인프라 비용 0이 목표다. OCI Always Free A1(arm64)은 2026년 한도가 축소되었고(구현 시 확인), 7일 저사용 인스턴스 회수 정책이 있다. Oracle Linux 7은 신규 사용 금지.

**Decision** — OCI home region의 A1 인스턴스(Oracle Linux 9 aarch64)에 Docker Compose(api + Caddy)를 운영한다. 이미지는 GitHub `ubuntu-24.04-arm` runner에서 네이티브 빌드해 GHCR에 올린다. VM은 상태가 없고 `bootstrap-vm.sh`로 1시간 안에 재구축할 수 있어야 한다. SELinux enforcing·firewalld를 유지한다. 구매 도메인 + Caddy 자동 HTTPS.

**Alternatives considered** — PaaS 무료 플랜(슬립·콜드스타트, arm64 무관) · amd64 Micro shape(1GB 메모리, JVM 부족 — SP-3 fallback) · Kubernetes(과도).

**Consequences** — firewalld와 Docker publish 우회 문제 때문에 API 컨테이너는 포트를 publish하지 않는다. bind mount에 SELinux 라벨이 필요하다: 한 컨테이너만 쓰는 경로는 `:Z`, 여러 컨테이너가 공유하는 경로(인증서 디렉터리 등)는 `:z`. 회수·장애 대응은 uptime 모니터와 `vm-rebuild` runbook(RISK-04). SP-3 결과를 이 ADR에 추가한다.

---

## ADR-007 DB가 장기 학습 기억

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: —

**Context** — LLM 대화 문맥은 휘발적이고 비용이 크며 재현할 수 없다. 레벨·복습·계획은 몇 달간 일관되게 누적되어야 한다.

**Decision** — 사용자 학습 상태의 source of truth는 PostgreSQL이다. 모든 학습 사건은 append-only `learning_event`로 남기고, skill 상태 변경은 `skill_state_change`에 근거 이벤트 ID와 함께 기록한다. AI 대화는 저장하지 않고 필요한 결과만 구조화해 저장한다.

**Alternatives considered** — 대화 기록을 context로 재주입(비용·불일치) · 벡터 DB 기억(검증 불가, 규칙 계산 불가).

**Consequences** — 이벤트 payload(`04` §6)만으로 규칙을 계산할 수 있어야 하므로 규칙이 바뀌면 payload와 `payload_version`을 함께 바꾼다. 잘못된 이벤트는 삭제하지 않고 `invalidated_at`으로 무효화한다.

---

## ADR-008 결정적 planner, LLM은 콘텐츠만

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: —

**Context** — "오늘 무엇을 할지"와 "레벨이 올랐는지"는 매일 신뢰해야 하는 판단이다. LLM 출력은 확률적이고 비용·장애가 있다(NFR-03).

**Decision** — 우선순위·시간 배분·reason·budget·risk·복습 간격·레벨 판정·attempt 판정은 `06-learning-engine-rules.md`의 규칙으로만 계산한다. LLM은 문제·hint·평가 근거·피드백·초안 같은 콘텐츠만 만든다. reason 문구는 템플릿이다.

**Alternatives considered** — LLM이 오늘 과제를 고름(설명 불가, 재현 불가) · 규칙 + LLM 문장 다듬기(v1 `TODAY_REASON_POLISH`, 비용 대비 가치 낮아 삭제).

**Consequences** — 모든 규칙은 test vector로 검증하고, 수치는 설정값으로 두어 4주 사용 후 조정한다(`BL-CNT-14`). AI가 없어도 Today·Review·Plan이 동작한다.

---

## ADR-009 Verification 분류 + 서버 가드 + DB CHECK

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: —

**Context** — 코드 리뷰 finding의 근거 신뢰도가 제각각이다. 모델은 "컴파일러로 확인했다"처럼 도구 근거를 사칭할 수 있고, prompt injection으로 `VERIFIED`를 요구받을 수 있다.

**Decision** — finding마다 `verificationStatus`(VERIFIED/SUPPORTED/AI_JUDGMENT/UNCERTAIN), `confidence`, `sourceType`, `sourceReference`를 둔다. `VerificationGuard`(`06` §10)가 파싱 직후 도구 주장·미등록 curated ID·allowlist 밖 호스트를 강등한다. 서버는 URL을 fetch하지 않는다. `coach_finding` CHECK 제약으로 이중 방어한다.

**Alternatives considered** — 모델 출력 그대로 표시(사칭 위험) · URL fetch로 근거 확인(SSRF, 비용, 약관).

**Consequences** — MVP의 VERIFIED는 curated source뿐이다. 화면은 배지를 항상 텍스트로 표시한다. 강등 내역은 `ai_call_log.guard_actions`로 남는다.

---

## ADR-010 코딩 스타일: google-java-format AOSP, 영어 식별자, 자동 강제

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: DEC-09

**Context** — v1은 Google Java Style + NAVER 명명 규칙을 사람 해석에 맡겼다. 에이전트가 생성하는 코드의 형식 논쟁과 한국어 로마자 식별자를 리뷰로 막기 어렵다.

**Decision** — Java 포맷은 Spotless + google-java-format **AOSP 스타일(4칸)**, 100열. 식별자는 영어만(한글·로마자 한국어 금지), 이름은 `15-glossary.md` 명명 사전을 따른다. Dart는 `dart format` + lint 0건. CI에서 `spotlessCheck`와 Checkstyle 명명 규칙으로 강제한다.

**Alternatives considered** — google-java-format 기본(2칸, 국내 실무 코드와 가독성 차이) · Checkstyle 전체 포맷 규칙(formatter와 충돌).

**Consequences** — 수동 포맷 금지, `spotlessApply`만 쓴다. 명명 사전에 없는 도메인 용어가 필요하면 사전을 먼저 갱신한다.

---

## ADR-011 AI 공급자 Anthropic `claude-opus-5`, port 뒤에 두고 설정으로 교체

- **Status**: **Superseded by ADR-032** (2026-09-18) · **Date (원)**: 2026-09-17 · **Date**: 2026-09-17 · **Related DEC**: DEC-05

**Context** — 코드 리뷰·문제 생성 품질이 제품 가치를 좌우한다. 모델·단가·SDK는 자주 바뀐다.

**Decision** — 공급자는 Anthropic, 기본 모델 `claude-opus-5`(`devpilot.ai.model`). 도메인은 `AiGateway`와 `integration.ai.api` 타입만 쓰고, SDK(`com.anthropic..`)는 `integration.ai.anthropic` 패키지에서만 import한다. provider는 `anthropic` / `fake` / `disabled` 설정으로 바꾼다. operation별 effort·max-tokens·timeout·단가는 설정값이다. 서버 측 refusal fallback(beta) 적용 여부는 `BL-AIP-04`에서 Java SDK 지원을 확인해 결정한다.

**Alternatives considered** — 여러 공급자 동시 추상화(eval·가드 비용 증가) · 더 저렴한 기본 모델(품질 저하 위험 — eval 결과로 operation별 전환은 설정 변경으로 가능).

**Consequences** — 모델 교체는 eval(`BL-AIP-13`) 통과를 조건으로 한다. 테스트는 `FakeAiProvider` fixture만 쓰고 실제 API는 수동 eval에서만 호출한다. 단가는 공식 가격표 확인 후 갱신한다(`20-decisions-and-risks.md` §3.1).

---

## ADR-012 비동기 AI 작업: DB 상태 + in-process executor, MQ 없음

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: DEC-06

**Context** — coach 분석·문제 생성·평가는 수십 초~3분이 걸린다. HTTP 요청을 붙잡으면 timeout과 스레드·커넥션 점유가 생긴다. 단일 인스턴스·저트래픽이다.

**Decision** — 긴 operation(`COACH_REVIEW`, `CHALLENGE_GENERATE`, `CHALLENGE_EVALUATE`, `REVIEW_VARIANT`, `EVIDENCE_DRAFT`, `REQUIREMENT_EXTRACT`)은 202 + 리소스 상태(`PENDING → RUNNING → COMPLETED/FAILED`) + 클라이언트 polling(2초, 최대 3분). 실행은 커밋 후 이벤트 + `aiTaskExecutor`(core 2, max 2, queue 20). 동기 AI는 `HINT_GENERATE`, `REVIEW_EVALUATE`, `COACH_RESPONSE_FEEDBACK`뿐이며 20초·재시도 0. 재시작으로 멈춘 작업은 `OrphanAsyncTaskJob`이 `FAILED(INTERRUPTED)`로 정리하고 사용자가 retry한다.

**Alternatives considered** — 메시지 큐(Redis/RabbitMQ — 운영 부담) · 전부 동기(timeout, UX 저하) · SSE/WebSocket 푸시(구현·프록시 복잡도).

**Consequences** — 프로세스 재시작 시 진행 중 작업은 유실되고 재시도가 필요하다. 동시 실행 한도는 사용자당 2(ADR-029). 인스턴스를 늘리면 이 ADR을 다시 연다.

---

## ADR-013 AI 데이터 보존과 공급자 약관

- **Status**: Accepted · **Date**: 2026-09-17 (공급자 확인 결과 2026-09-18 추가) · **Related DEC**: DEC-16

**Context** — 사용자가 개인 프로젝트 코드와 서술을 외부 AI로 보낸다. 회사 코드·secret이 섞일 수 있다(RISK-08). 개인정보 최소 보관 원칙(NFR-05).

**Decision** — (1) 모든 저장되는 자유 입력은 저장·전송 전에 `SecretMasker`로 마스킹하고 private key는 차단한다. (2) coach 요청마다 기밀 동의를 받는다. (3) `coach_review.content`는 30일 후 purge, 즉시 삭제 API 제공. (4) 프롬프트·응답 원문은 DB·로그에 저장하지 않는다(`ai_call_log`는 메타데이터와 hash만, 180일). (5) prod에서 실제 공급자를 켜기 전에 API 데이터 보존·학습 사용 약관을 확인하고 날짜·URL·결론을 이 ADR에 기록한다.

**Alternatives considered** — 원문 0일 보존(재분석·복습 카드 생성 불가) · 무기한 보존(유출 영향 증가) · 프롬프트 로그 저장(디버깅 편의 < 유출 위험, local profile 파일 로그로 대체).

**공급자 확인 결과 (2026-09-18, DeepSeek)** — https://api-docs.deepseek.com/ 및 플랫폼 약관 확인:

| 확인 항목 | 결과 |
|---|---|
| API 입력을 모델 학습에 쓰지 않는다는 약정 | **공개 문서에 없음** |
| zero-retention(무보존) 옵션 | **없음** |
| 데이터 처리 위치 | **중국** |

**결정(사용자, 2026-09-18)** — 학습용 개인 프로젝트이므로 위 세 항목을 수용하고 그대로 진행한다. 대신 (1) coach 제출 화면과 초대 안내에 이 사실을 명시한 동의 문구를 넣고(`02` SCR-COACH-NEW, `07` §8), (2) 회사 코드 금지·secret 마스킹·원문 30일 보존은 그대로 유지한다. 회사 코드나 고객 데이터를 다루게 되면 이 ADR을 다시 연다.

**Consequences** — 30일 후에는 coach 재분석이 불가능하고 finding만 남는다. 복습 문항에 코드를 넣지 않는다(`06` §9.4). 공급자를 바꾸면 이 표를 다시 채우고 동의 문구를 갱신한다.

---

## ADR-014 전용 스키마 `devpilot` + Supabase Data API 비활성

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: DEC-15
- **2026-09-18 보완**: 운영 DB가 자체 서버의 순수 PostgreSQL 16이 되어 `anon`·`authenticated` role이 없다. `V1__baseline.sql`의 DO 블록은 현재 **no-op**이고, Supabase 도입(Later) 시에 의미가 생긴다. 스키마 분리와 "backend만 DB 접근" 원칙은 그대로 유효하다.

**Context** — Supabase는 publishable key로 호출하는 Data API(REST/GraphQL)를 제공하고, 클라이언트 번들에는 publishable key가 들어간다. 설정 실수 한 번으로 전체 학습 데이터가 노출될 수 있다. backend만 DB에 접근하므로 Data API가 필요 없다.

**Decision** — 모든 테이블은 `devpilot` 스키마에 만든다. Supabase Data API를 비활성화한다. `V1__baseline.sql`의 조건부 DO 블록으로 `anon`, `authenticated`의 스키마·테이블 권한을 revoke한다(순수 PostgreSQL에서는 no-op). RLS는 쓰지 않는다.

**Alternatives considered** — `public` 스키마 + RLS(모든 테이블 정책 유지 비용, 서버 규칙과 이중화) · Data API 유지 + 스키마 미노출(SP-5 fallback).

**Consequences** — AC-20으로 매 하드닝 시점에 확인한다. Data API 비활성 상태에서 Auth가 동작하지 않으면 fallback(스키마 미노출 + revoke)을 적용하고 이 ADR에 기록한다.

---

## ADR-015 RFC 9457 Problem Details + 오류 코드 카탈로그

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: —

**Context** — v1은 자체 오류 포맷이었다. 클라이언트 분기와 내부 정보 노출 방지를 일관되게 하려면 표준 형식과 고정 code가 필요하다.

**Decision** — 모든 오류는 `application/problem+json`(RFC 9457) + 확장 필드 `code`, `traceId`, `errors[]`. `code`는 `05-api-spec.md` 카탈로그와 `common.error.ErrorCode` enum이 1:1이다. `detail`은 `messages_ko.properties`의 사용자용 한국어 문장만 쓰고, 예외 메시지·SQL·클래스명·stack trace는 넣지 않는다. 비동기 작업 실패는 HTTP 오류가 아니라 리소스의 `failureCode`(`AsyncFailureCode`)로 표현한다.

**Alternatives considered** — 자체 포맷 유지(표준 도구 미지원) · HTTP status만 사용(클라이언트 분기 불가).

**Consequences** — 새 오류는 카탈로그·enum·메시지 파일·클라이언트 처리를 같은 PR에서 추가한다. `catch (Exception)`은 세 곳에서만 허용한다: `GlobalExceptionHandler` 최후 방어선, 비동기 `*Task` 최상위, 스케줄 `*Job`의 사용자 단위 루프(`03-system-architecture.md` §7).

---

## ADR-016 Tailscale 배포 접속, 공개 SSH 차단

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: DEC-04, DEC-13
- **2026-09-18 부분 대체 (ADR-034)**: "접속은 Tailscale로만, 공개 SSH 없음"이라는 **원칙은 유효**하다. 다만 아래 Decision의 자동화 부분(deploy workflow가 Tailscale OAuth client `tag:ci`로 tailnet에 합류해 Tailscale SSH로 `deploy.sh`를 실행)은 **쓰지 않는다**. 현재는 Actions가 amd64 이미지와 `web.zip`을 만들어 GHCR·Release에 올리는 데서 멈추고, 배포는 운영자 PC에서 `ssh <server>`로 수동 실행한다(ADR-034). Tailscale OAuth·ACL·노드 태깅은 Later다.

**Context** — GitHub-hosted runner IP는 고정되지 않아 "SSH를 특정 IP로 제한"과 "CI에서 SSH 배포"가 함께 성립하지 않는다. 저장소가 public이므로 self-hosted runner는 쓸 수 없다(외부 PR이 VM 코드 실행 권한을 얻음).

**Decision** — deploy workflow가 Tailscale OAuth client(`tag:ci`)로 tailnet에 합류해 Tailscale SSH로 VM의 `deploy.sh`를 실행한다. OCI security list와 firewalld에서 22번을 공개망에 열지 않는다. 운영자 접속도 Tailscale로 한다.

**Alternatives considered** — VM self-hosted runner(public repo에서 금지) · SSH 22 전체 개방 + key + fail2ban(공격 면적) · 수동 배포(S0 fallback만).

**Consequences** — Tailscale 계정이 배포 경로의 외부 의존이 되고 2FA가 필요하다(RISK-12). 공개 SSH는 열지 않으므로(`10-deployment-and-operations.md` §3.2) Tailscale 장애 시 원격 접속 수단이 없다. 이때는 서버에 물리적·LAN으로 접근하거나 `10-deployment-and-operations.md` §13.6 server-restore runbook 절차를 따른다.

---

## ADR-017 Plan-day 경계 (`dayStartHour`)

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: —

**Context** — 퇴근 후 자정을 넘겨 공부하는 사용자에게 달력 날짜 기준 "오늘"은 계획·기록·복습 due를 둘로 쪼갠다.

**Decision** — 모든 "오늘"은 plan-day다: `planDate = (now.atZone(zone) − dayStartHour).toLocalDate()`, `planDayStart = date.atTime(dayStartHour).atZone(zone)`(`06` §2). `dayStartHour`는 0~6, 기본 4, timezone 기본 `Asia/Seoul`. 저장은 UTC `timestamptz` + `plan_date`(date). 서버는 주입된 `Clock`만 쓴다.

**Alternatives considered** — 달력 자정(새벽 학습이 다음 날로 분리) · UTC 날짜(한국 사용자와 9시간 차이) · 클라이언트가 날짜 계산(기기 timezone 불일치).

**Consequences** — due, daily plan, 세션, AI 일일 한도, snapshot·weekly job이 같은 경계를 쓴다(AC-17). 설정 변경은 과거 `plan_date`를 재계산하지 않는다. 무인자 `now()` 호출은 ArchUnit으로 금지한다.

---

## ADR-018 규칙 기반 복습 스케줄 v1 (HARD ×1.2) + `review_answer` 기록

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: DEC-17

**Context** — FSRS 같은 모델은 파라미터 학습에 충분한 응답 기록이 필요하다. 초기에는 데이터가 없다. v1 초안의 HARD 2일 고정은 긴 간격 카드를 과하게 짧게 만든다.

**Decision** — `RuleBasedV1Scheduler`(`06` §6): 자기평가를 증거(평가 결과·hint)로 조정한 최종 등급 → AGAIN 1일, HARD `max(2, round(prev × 1.2))`, GOOD `max(2, prev × 2)`, EASY `max(4, prev × 3)`, 1~60일, horizon cap. `hard-strategy = FIXED_2`로 설정 전환 가능. 모든 답변을 `review_answer`에 append-only로 남긴다(자기평가, 최종 등급, hint, 응답 시간, 간격 전후, `strategy`). 스케줄러는 `ReviewSchedulingStrategy` 인터페이스 뒤에 둔다.

**Alternatives considered** — FSRS 즉시 도입(데이터 부족, 설명 난이도) · SM-2 그대로(자기평가 과신 보정 없음).

**Consequences** — FSRS 전환(`BL-MEM-11`) 검토 조건: 사용자 1명 기준 `review_answer` 1,000행 이상 + 4주 이상 사용. 전환 시 새 ADR로 이 ADR을 Superseded 처리하고, 과거 기록으로 오프라인 비교 결과를 첨부한다.

---

## ADR-019 아키텍처 규칙을 ArchUnit·Checkstyle·PMD로 강제

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: DEC-09

**Context** — `AGENTS.md`의 금지 규칙(Controller → Repository 직접 호출, 트랜잭션 안 AI 호출, 무인자 시간 호출 등)은 사람 리뷰만으로 지켜지지 않는다.

**Decision** — `./gradlew check` 하나로 Spotless, Checkstyle(명명), PMD 7, SpotBugs, JaCoCo, ArchUnit을 실행하고 CI에서 실패시킨다. ArchUnit 규칙 목록은 `08-coding-conventions.md`의 ARCH-* 카탈로그가 기준이다. 규칙을 우회(비활성화·예외 추가)하려면 ADR이 필요하다.

**Alternatives considered** — 리뷰 체크리스트만(누락) · Error Prone/NullAway 추가(Java 25 호환 확인 비용 — MVP 제외).

**Consequences** — 도구가 Java 25에서 동작하지 않으면 도구를 끄지 않고 Java 21로 내린다(ADR-005). 새 모듈·규칙은 ArchUnit 테스트를 같은 PR에 추가한다.

---

## ADR-020 규칙 계산은 정수(bp·micro)

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: —

**Context** — 부동소수점 비교와 반올림은 플랫폼·구현마다 달라 test vector 재현성을 깨고, 경계값(ratio 1.0, coverage 0.8) 판정을 흔든다.

**Decision** — 규칙 계산은 `long` 정수만 쓴다. 비율은 basis point(1.0 = 10,000), 점수·factor는 micro(1.0 = 1,000,000), 비용은 micro USD. 나눗셈은 `floorDiv`/`ceilDiv`/`roundHalfUpDiv`만, 곱셈은 `Math.multiplyExact`(N-1~N-7). 설정의 소수값은 기동 시 정수로 변환하고 정수가 아니면 기동 실패. DB·API 필드는 `*Bp`, `*Micro`, `*Milli` 접미사를 쓴다.

**Alternatives considered** — `double` + epsilon(경계 판정 불안정) · `BigDecimal`(성능·가독성 비용, 동등성 비교 함정).

**Consequences** — `double`/`float` 사용은 ArchUnit으로 금지한다. 화면 표시용 소수 변환은 클라이언트에서만 한다.

---

## ADR-021 Plan versioning: `plan_skill_target` + flush 후 INSERT

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: —

**Context** — 계획 변경 이력을 보존해야 하고(AC-01), 과거 daily plan은 당시 milestone을 참조해야 한다. replan의 defer·목표 축소도 버전에 남아야 한다. 사용자당 ACTIVE plan 1개는 partial unique index로 강제한다.

**Decision** — milestone 구조·날짜·priority·skill 구성·skill target 변경은 새 plan version(`POST /plans/{id}/replan`)이다. status·description·sortOrder만 in-place. 계획 버전 번호는 `learning_plan.plan_version`이고 JPA `@Version version`과 분리한다. 계획별 skill 목표는 `plan_skill_target`(role 기본값 복사 → defer/축소/복원). replan은 한 트랜잭션에서 이전 plan `SUPERSEDED` → **flush** → 새 plan INSERT(`06` §11.2).

**Alternatives considered** — milestone in-place 수정 + 변경 로그(과거 참조 깨짐) · 이벤트 소싱(과도).

**Consequences** — flush 순서를 어기면 unique index 위반이 난다(통합 테스트로 확인). 동시 replan은 한쪽이 409(AC-24). 새 버전마다 milestone ID가 바뀌므로 응답에 id mapping을 준다.

---

## ADR-022 모든 인증 POST에 `Idempotency-Key` 필수

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: —

**Context** — 모바일 네트워크 재시도와 더블클릭이 AI 비용 중복, 중복 세션·답변을 만든다. endpoint별로 "권장"으로 두면 에이전트 구현이 제각각이 된다.

**Decision** — 인증이 필요한 모든 `POST`는 `Idempotency-Key` 헤더 필수(없으면 400). 예외는 저장하지 않는 `POST /plans/{id}/replan/preview` 하나. `(user_id, key)` 단위로 `idempotency_record`에 request hash와 2xx 응답을 24시간 저장해 재생한다. 다른 body면 422, 처리 중이면 409. `PUT`/`PATCH`/`DELETE`는 `version`으로 동시성을 제어한다.

**Alternatives considered** — AI endpoint만 필수(규칙 분기) · 서버 규칙별 중복 방지만(범용성 없음).

**Consequences** — 클라이언트 dio interceptor가 사용자 행동당 UUID를 만든다. 재생 응답은 최초 스냅샷이므로 최신 상태는 GET으로 본다. 응답 헤더는 저장하지 않으므로 IK endpoint는 `Location` 헤더에 의존하지 않는다.

---

## ADR-023 GitHub OAuth 단일 로그인 + allowlist

- **Status**: **Superseded by ADR-033** (2026-09-18) · **Date (원)**: 2026-09-17 · **Date**: 2026-09-17 · **Related DEC**: DEC-01, DEC-08, DEC-11

**Context** — 사용자는 소유자 + 초대 최대 2명이다. 가입이 열리면 제3자가 AI 비용을 소모할 수 있다. 비밀번호 관리를 하지 않는다.

**Decision** — Supabase Auth GitHub OAuth만 켜고 신규 가입을 비활성화한다. backend는 JWKS(비대칭 alg)로 JWT를 검증하고, 요청마다 allowlist(`allowed-emails` 소문자 비교, `allowed-subjects`)를 확인한다. 첫 요청에 JIT로 `app_user`를 만든다(ON CONFLICT). backend는 Supabase secret key를 쓰지 않으며, 계정 삭제 시 Supabase Auth 사용자는 운영자가 수동 삭제한다.

**Alternatives considered** — Email magic link/OTP(메일 발송 설정) · backend 자체 인증(범위 밖) · Admin API 자동 삭제(secret key 서버 보관 필요).

**Consequences** — allowlist 제거를 빠뜨리면 삭제된 계정이 빈 사용자로 다시 생성된다(runbook 경고). `DELETE /me`의 최근 로그인 판정 claim은 SP-5에서 확인한다.

---

## ADR-024 PWA 먼저, Android 앱은 Later

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: DEC-18

**Context** — 모바일 5분 복습(AC-10)은 실사용 시작(M1 완료, ADR-037)에 필요하다. 스토어 배포는 서명 키·심사·업데이트 운영을 늘린다.

**Decision** — S2에 Flutter Web을 PWA(manifest, 아이콘, 홈 화면 추가 안내)로 제공한다. Android 앱 빌드·배포는 Later(`BL-CLI-30`). Web Push는 쓰지 않고, 학습 시작 알림은 캘린더 구독(ICS)으로 대신한다.

**Alternatives considered** — Android 앱 동시 출시(일정 부담) · 모바일 전용 화면 별도 개발(중복).

**Consequences** — iOS PWA 제약(푸시, 저장소 정리)을 감수한다. 정적 파일은 `Cache-Control: no-cache`로 제공해 새 버전 반영을 보장한다.

---

## ADR-025 Flutter: Riverpod + go_router + dio + freezed

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: DEC-10, DEC-12

**Context** — 화면 30여 개, 비동기 polling, 인증 redirect, Problem Details 처리를 일관된 구조로 에이전트가 구현해야 한다.

**Decision** — 상태관리 `flutter_riverpod`, 라우팅 `go_router`(인증·온보딩 redirect), HTTP `dio`(interceptor: 토큰, `Idempotency-Key`, trace id, Problem Details), 모델 `freezed` + `json_serializable`. API 모델은 OpenAPI 스냅샷(`docs/api/openapi.yaml`)을 기준으로 **수기 작성**하고 스냅샷 diff로 변경을 감지한다. 알 수 없는 enum 값은 `unknown`으로 파싱한다. 문자열은 ARB(ko).

**Alternatives considered** — Bloc(보일러플레이트) · Provider(대규모 상태 관리 한계) · openapi-generator 자동 생성(산출물 품질 편차, Boot 4 스펙 호환 불확실).

**Consequences** — API 변경 PR은 스냅샷과 Dart 모델을 함께 바꾼다. 패키지 버전은 pub.dev에서 확인 후 고정한다.

---

## ADR-026 Seed 콘텐츠는 저장소 YAML, 기동 시 적재

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: DEC-14

**Context** — skill tree, 역할 목표, 계획 템플릿, 복습 카드, challenge 품질이 planner·budget·AI 불가 시 동작을 결정한다. 사람이 리뷰하고 버전 관리할 수 있어야 한다.

**Decision** — 원본은 저장소 `content/*.yaml`(`19-content-spec.md`). 빌드 시 classpath로 복사하고 `ContentSeeder`(ApplicationRunner)가 `ContentValidator` 검증 후 `catalog_version` 비교로 upsert한다. 검증 실패는 기동 실패. 사라진 항목은 비활성화(`active=false`, `RETIRED`)만 한다. seed 복습 카드는 사용자 온보딩 시 사용자 `review_item`으로 복사한다. 기본 역할 목표에서 ALGORITHM은 SHOULD, EXPLANATION(기술 설명)은 MUST로 둔다 — 문제 풀이 속도보다 자기 코드와 결정을 설명하는 능력이 학습 루프의 중심이다.

**Alternatives considered** — Flyway repeatable migration SQL(검증 로직 작성 어려움) · 관리자 화면 편집(범위 밖) · AI로 catalog 생성(검수 불가).

**Consequences** — 콘텐츠 변경은 PR + CI content job. DB는 사본이므로 직접 수정하지 않는다. 학습 목표에 맞춰 우선순위를 바꿀 때는 role target YAML만 바꾸고 `catalog_version`을 올린다.

---

## ADR-027 AI 호출은 DB 트랜잭션 밖에서

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: —

**Context** — AI 호출은 수 초~3분이 걸린다. 트랜잭션 안에서 호출하면 Hikari 풀(5개)이 고갈되고 row lock이 길어진다.

**Decision** — `AiGateway` 호출은 `@Transactional` 경계 밖에서만 한다(T-2). 동기 AI는 `tx1(조회·검증) → AI → tx2(저장)`, tx 사이에 `version`이 바뀌었으면 저장하지 않고 409. 비동기 AI는 `tx(RUNNING) → AI → tx(결과)`. ArchUnit으로 검사한다(`BL-AIP-12`).

**Alternatives considered** — 트랜잭션 안 호출(풀 고갈) · 보상 트랜잭션 프레임워크(과도).

**Consequences** — 두 트랜잭션 사이 상태 변경을 처리하는 코드가 필요하다. AI 결과 저장 실패 시 비용은 이미 발생했으므로 `ai_call_log`는 별도 트랜잭션으로 기록한다.

---

## ADR-028 Public GitHub 저장소 + secret scanning

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: DEC-04

**Context** — DevPilot은 사용자가 **정한 목표 시점까지 개발 실력을 쌓기 위해 매일 쓰는 학습 코치 도구**다. 공개해서 곤란한 데이터는 코드에 없고, public 저장소는 Actions 무료 한도와 보안 도구(secret scanning, Dependabot)를 제약 없이 쓸 수 있다. 대신 secret 유출과 외부 PR을 통한 공격 면적이 생긴다.

**Decision** — 저장소는 public. GitHub secret scanning + push protection 활성, CI gitleaks, Dependabot(S0), Trivy·CodeQL(S2 P1). third-party action은 commit SHA로 고정. 실제 secret은 저장소에 두지 않고(`.env.example`은 키 이름만), 배포 secret은 GitHub environment secret과 서버 env 파일(권한 600)에만 둔다. **서버 IP·tailnet 호스트명·계정·개인 이메일도 저장소에 쓰지 않는다**(자리표시자만, 실제 값은 저장소 밖). self-hosted runner는 쓰지 않는다(ADR-016). eval workflow의 API key는 environment 승인으로 보호한다.

**Alternatives considered** — private 저장소(개인 도구이므로 공개할 이유가 강하지는 않으나, 일부 무료 기능이 제한된다. 언제든 전환 가능 — DEC-04) · public + self-hosted runner(금지).

**Consequences** — 유출 대응 runbook(`incident-api-key-leak`)과 키 교체 절차가 필수다. 커밋 이력에 개인 데이터·실제 요구사항 문서·업무 코드를 넣지 않는다.

---

## ADR-029 AI 월 예산은 서비스 전체 한도

- **Status**: Accepted · **Date**: 2026-09-17 · **Related DEC**: DEC-06
- **2026-09-18 부분 대체 (ADR-035)**: "서비스 전체 합계 + Asia/Seoul 달력 월"이라는 **판정 방식은 유효**하다. 다만 아래 Decision의 두 가지는 바뀌었다: (1) 월 예산 기본값 **USD 25 → USD 3** (2) Anthropic Console workspace 지출 한도(`devpilot-prod` 30 / `devpilot-dev` 10 / 조직 40)는 공급자가 DeepSeek으로 바뀌면서 **존재하지 않는다** — 외부 상한은 선불 잔액뿐이고 `AiBalanceCheckJob`이 이를 감시한다(ADR-035).

**Context** — 인프라가 무료이므로 AI 비용이 사실상 전체 비용이다. 비용 부담자는 소유자 1명이고 사용자는 최대 3명이다. 사용자별 월 예산은 합계가 USD 25를 넘을 수 있다.

**Decision** — 월 예산 USD 25는 **모든 사용자 `ai_call_log.cost_micro_usd` 합계**에 적용한다. 기간은 **Asia/Seoul 달력 월**(1일 00:00 KST ~ 다음 달 1일 00:00 KST). 80% 이상 `BUDGET_WARNING`, 100% 이상 AI 차단(429 `AI_MONTHLY_BUDGET_EXCEEDED`). 일일 호출 한도 60회는 **사용자별**, 사용자의 plan-day 기준(`BUDGET_BLOCKED`·가드 재시도 제외). 동시 실행 2개도 **사용자별**(진행 중 동기 호출 + `PENDING`/`RUNNING` 비동기 작업). Anthropic Console 지출 한도를 이중 방어로 둔다: workspace `devpilot-prod` USD 30(앱 예산 25가 먼저 차단되도록 여유), `devpilot-dev` USD 10(local·eval), 조직 USD 40.

**Alternatives considered** — 사용자별 월 예산(합계 초과 가능) · UTC 월(한국 사용자에게 월 경계가 오전 9시) · 사용자 timezone 월(사용자마다 경계가 달라 서비스 합계 판정 불가).

**Consequences** — 한 사용자가 예산을 소진하면 다른 사용자도 AI를 못 쓴다(초대 사용자 수가 적어 수용). 호출 1회의 예상 비용을 미리 빼지 않으므로 마지막 호출만큼 예산을 넘을 수 있다. 비-AI 기능은 영향이 없다(AC-12, AC-13).

---

## ADR-030 자체 서버의 공용 PostgreSQL 16

- **Status**: Accepted · **Date**: 2026-09-18 · **Supersedes**: ADR-001 · **Related DEC**: DEC-15

**Context** — 사용자가 이미 Linux 서버를 운영하고 있고 거기에 PostgreSQL 16 컨테이너가 돌고 있다. Supabase 무료 플랜은 7일 비활성 일시정지·자동 백업 없음·500MB 제약이 있고, 계정·프로젝트를 새로 만들어야 한다. 사용자 결정(2026-09-18): "supabase는 일단 건너뛰고 db 서버에 계정 만들어서 진행".

**Decision** — 개발·운영 모두 **자체 서버의 공용 PostgreSQL 16 인스턴스**를 쓴다. 전용 DB `devpilot`과 소유 role `devpilot`을 만들고 그 범위 밖을 건드리지 않는다(같은 인스턴스에 다른 사용자 DB가 6개 있다). 스키마는 `devpilot` 하나다. 접속은 Tailscale 구간이라 TLS를 쓰지 않는다. 테스트도 `postgres:16`으로 major를 맞춘다. 접속값은 저장소 밖 `DevPilot-ops/02-database.md`에 둔다.

**Alternatives considered** — Supabase(계정·연동 작업, 무료 플랜 제약, 지금 필요 없음 — Later로 남김) · 서버에 DevPilot 전용 PG 17 컨테이너 추가(포트·메모리 소비, 공용 인스턴스로 충분) · 로컬 PC의 Docker PG(서버 배포본과 다른 DB를 보게 됨).

**Consequences** — **PG 17 전용 문법을 쓸 수 없다**(Testcontainers도 16으로 고정해 CI에서 먼저 잡는다). 백업은 관리형이 아니므로 직접 한다(ADR-031의 백업 정책). 공용 인스턴스라 다른 사용자의 재시작·업그레이드가 영향을 준다(RISK: 20 §4). Supabase로 옮길 때는 ADR-001·ADR-014를 되살리고 `pg_dump`/`pg_restore`로 이전한다.

---

## ADR-031 자체 Linux 서버 + Tailscale HTTPS (임시 배포)

- **Status**: Accepted (**임시** — 공개 전환 시 ADR-006 또는 후속 ADR로 교체) · **Date**: 2026-09-18 · **Related DEC**: DEC-07, DEC-13, DEC-19

**Context** — **지금 당장 필요한 것은 개발용 DB와 초기 실사용 환경이다.** 사용자는 Rocky Linux 9(x86_64, Proxmox VM, Docker 29.6.1, Tailscale 1.98.8)와 그 위의 PostgreSQL 16을 이미 운영 중이고 tailnet이 있어, 계정 생성·도메인 구매 없이 오늘 바로 쓸 수 있다. 최종 목표는 공개 배포이지만(사용자 확인, 2026-09-18) 그건 나중 일이다.

**Decision** — **공개 전환 게이트(`11` §3.10) 전까지의 임시 배포**로 기존 자체 서버에서 Docker Compose(api + Caddy)를 돌린다. 이 기간에는 공개 인터넷에 노출하지 않고 **Tailscale로만** 접근한다. TLS는 호스트에서 `tailscale serve --bg --https=443 http://127.0.0.1:18080`이 종단하고, Caddy는 `127.0.0.1:18080`에만 바인딩해 보안 헤더·정적 파일·`/api` 프록시만 한다. 도메인·ACME·공개 80/443을 쓰지 않는다. 이미지는 **`linux/amd64,linux/arm64` 멀티아치**로 빌드한다(현재 서버는 x86_64, OCI Always Free의 Ampere A1은 arm64 — 어느 쪽으로 가든 같은 태그를 쓴다). 서버 준비는 `prepare-server.sh`가 `deploy` 사용자·`/opt/devpilot`·systemd 유닛만 만들고 **OS 설정(dockerd, firewalld, sshd, podman)은 건드리지 않는다**(공용 서버). 백업은 서버에서 매일 `pg_dump -Fc`(7일 보관) + 로컬 PC가 주 1회 pull(8주 보관).

**Alternatives considered** — 처음부터 OCI + 구매 도메인으로 공개 배포(계정·도메인·인증 작업이 S0에 들어와 2주 이상 밀린다 — ADR-006으로 보존, 공개 전환 시 채택 후보) · Cloudflare Tunnel(공개 노출이 생겨 devtoken을 못 씀) · Caddy가 `tailscale cert`로 직접 발급(tailscaled 소켓 마운트 필요, `tailscale serve`가 더 단순).

**Consequences** — **Tailscale이 없으면 접근할 수 없다**(모바일 복습도 Tailscale 앱 필요). 단일 서버 SPOF(전원·ISP·하드웨어)와 공용 Docker/PG 간섭을 받는다(20 §4). JVM은 `-Xmx1g`, `mem_limit 1.5g`로 제한해 다른 컨테이너를 밀어내지 않는다(가용 4.7GB). 외부 모니터링(UptimeRobot)이 닿지 않아 호스트 systemd timer가 health를 확인하고 healthchecks.io로 ping한다. **공개 전환 시 바뀌는 것** — 이 ADR은 임시이므로 아래를 교체 대상으로 미리 적어 둔다.

| 항목 | 지금 (임시) | 공개 전환 후 |
|---|---|---|
| 인증 | `devtoken`(이메일 입력만) | **실제 신원 확인(필수 선행, `BL-SEC-18`)** — GitHub OAuth 직접(권장) 또는 Supabase Auth |
| TLS | `tailscale serve` | Caddy 자동 HTTPS(ACME) + 도메인 또는 OCI 공개 IP |
| Caddy publish | `127.0.0.1:18080` | `0.0.0.0:80,443` |
| 호스트 | 자체 서버 | 자체 서버 웹 공개 또는 OCI Always Free(ADR-006) — 그때 결정 |
| 접근 | Tailscale 설치 기기만 | 아무 브라우저 |

전환은 Sprint 범위가 아니라 별도 게이트다(`11` §3). 전환 시 이 ADR을 `Superseded by ADR-036+`로 바꾸고 새 ADR을 쓴다.

---

## ADR-032 AI 공급자 DeepSeek `deepseek-flash`, RestClient 직접 호출

- **Status**: Accepted · **Date**: 2026-09-18 · **Supersedes**: ADR-011 · **Related DEC**: DEC-05

**Context** — 예산이 제약이다(사용자: "가장 저렴한 DeepSeek를 이용할거야"). 2026-09-18에 실제 키로 확인한 사실: 모델은 `deepseek-flash`·`deepseek-v4-pro`(구 `deepseek-chat`·`deepseek-reasoner`는 2026-07 종료), 단가는 flash 기준 입력 $0.15 / 캐시적중 $0.003 / 출력 $0.60 per 1M(피크 ×2), 엄격한 JSON 스키마는 `POST /responses`의 `text.format: {type: json_schema}`에서만 동작하고, **Java SDK가 없다**.

**Decision** — 운영 기본 공급자는 **DeepSeek**, 기본 모델 `deepseek-flash`다. Spring **`RestClient`로 `POST /responses`를 직접 호출**한다(`instructions`=system prompt, `text.format` json_schema, `max_output_tokens`, `thinking`, `reasoning_effort`, `store: false`, 요청에 `user`/`user_id` 금지 — 캐시가 사용자별로 쪼개진다). 도메인은 `AiGateway`와 `integration.ai.api` 타입만 쓰고 HTTP 타입은 `integration.ai.deepseek` 밖으로 나가지 않는다. provider는 `deepseek` / `fake` / `disabled` 설정으로 바꾼다. **thinking은 기본이 켜짐이므로 값싼 operation은 명시적으로 끈다**(`03` §9 operations). 다른 공급자는 `AiProvider` 구현을 추가해 붙인다.

**Alternatives considered** — Anthropic Claude(품질은 높지만 단가가 약 30배, ADR-011) · `/chat/completions` + `json_object`(공식 문서에 빈 응답 이슈, 스키마 강제 불가) · OpenAI 호환 Java SDK 전용(검증되지 않은 호환성, 의존성 추가).

**Consequences** — 응답 필드(`status: incomplete`, `incomplete_details.reason`, `usage.*_details`)는 구현 시 실제 호출로 확인하고 다르면 `17`을 먼저 고친다. 모델 은퇴 주기가 짧다(2026-07 사례) — 모델 상수는 설정값으로 두고 은퇴 공지를 확인한다. 데이터 정책은 ADR-013 결과표를 따른다. `anthropic-java`·WireMock 의존성을 제거하고 provider 테스트는 `MockRestServiceServer`로 한다.

---

## ADR-033 개발 토큰(devtoken) 인증 — 비공개 배포 전용

- **Status**: Accepted (**조건부** — 비공개 네트워크 배포에서만. 공개 전환 시 ADR-023의 Supabase Auth로 되돌아간다) · **Date**: 2026-09-18 · **Related DEC**: DEC-01, DEC-08

**Context** — Supabase 도입을 연기하면서(ADR-030) Supabase Auth도 빠졌다. 그런데 S0부터 서버에 배포하고 M1 완료부터 실사용하려면 인증 경로가 필요하다. 이 기간의 배포는 **tailnet 안에서만** 접근된다(ADR-031, 임시). 사용자는 1~3명이고 모두 소유자가 초대한다. **공개 배포는 나중에 하며, 그때는 이 방식을 쓸 수 없다**(사용자 확인, 2026-09-18).

**Decision** — `devpilot.security.auth-mode = devtoken`을 **비공개 네트워크 배포의 기본값**으로 둔다. backend가 EC P-256 키(`DEVPILOT_DEV_JWT_KEY`, prod 필수)로 JWT를 서명하고 같은 키의 공개키로 검증한다. `POST /api/v1/dev/token {email}`은 allowlist에 있는 이메일에만 720시간 토큰을 발급하고, `GET /api/v1/dev/jwks.json`이 공개키를 제공한다. 검증·프로비저닝·allowlist·JIT 생성은 기존 경로(`03` §4.2~§4.3)를 그대로 쓴다. Flutter는 `AUTH_MODE=dev|supabase` dart-define으로 로그인 화면을 고른다. **Supabase Auth(GitHub OAuth)는 Later**이고, 그때 `auth-mode=supabase`로 바꾸면 이 endpoint는 등록되지 않는다.

**Alternatives considered** — Supabase Auth를 실사용 전에 도입(S0가 2주 늘어남, 지금 필요 없는 외부 의존) · local profile 전용으로만 두고 운영은 Supabase(운영 인증이 없어 배포 자체가 불가) · Basic 인증(JWT 검증 경로를 두 벌 만들게 됨) · 인증 없음(allowlist·소유권 검사·감사 로그가 모두 무의미해짐).

**Consequences** — **이 방식은 "tailnet 밖에서 endpoint에 닿을 수 없다"는 전제 위에서만 안전하다.** 공개 인터넷에 노출하는 순간 **허용 이메일만 알면 누구나 토큰을 받는다**(비밀번호도 OAuth도 없다).

**공개 전환의 필수 선행 조건 (게이트)** — 공개 노출 전에 반드시:
1. `BL-SEC-18`(공개 인증 도입) 완료 — **실제 신원 확인이 있는 로그인**으로 바꾼다. 후보 (a) backend가 GitHub OAuth를 직접 처리하고 현재의 JWT 발급·검증 경로를 재사용(권장 — 외부 의존 없음, `DevTokenService` 대부분 재사용) (b) Supabase Auth → `DEVPILOT_AUTH_MODE=supabase`
2. `devtoken` 관련 bean이 prod에서 등록되지 않음을 확인(`/api/v1/dev/token`·`/api/v1/dev/jwks.json` 404)
3. `07` §2.4 잔여 위험의 "tailnet 밖 도달 불가" 전제 해제를 기록

방어 장치로 **`prod` profile + `auth-mode=devtoken`이면 기동 시 WARN 1줄**("이 인스턴스는 비공개 네트워크에서만 노출해야 한다")을 남긴다(`03` §4.2). 기동을 막지는 않는다 — 지금의 임시 배포가 바로 그 조합이기 때문이다. 키가 비어 기동 시 생성되면 재기동마다 기존 토큰이 무효가 되므로 운영은 PEM을 `api.env`에 고정한다. 토큰 폐기 수단은 allowlist 제거뿐이다(720시간 안에는 기존 토큰이 살아 있으나 `UserContextFilter`가 요청마다 allowlist를 다시 검사하므로 즉시 차단된다).

---

## ADR-034 배포는 수동 트리거, Actions는 이미지 빌드까지

- **Status**: Accepted · **Date**: 2026-09-18 · **Related DEC**: DEC-13 · **Note**: ADR-016(Tailscale 접속, 공개 SSH 차단)의 자동화 부분을 대체한다

**Context** — ADR-016은 deploy workflow가 Tailscale OAuth client(`tag:ci`)로 tailnet에 합류해 Tailscale SSH로 `deploy.sh`를 실행하는 방식이었다. 이를 위해 OAuth client 발급, ACL 작성, 노드 태깅(소유권·key expiry가 바뀐다), `deploy` 사용자용 SSH 규칙이 필요하다. 배포 빈도는 스프린트당 1~2회이고 서버는 사용자 PC에서 Tailscale로 바로 닿는다.

**Decision** — S0~S2에서 GitHub Actions(`release.yml`)는 태그 `v0.<sprint>.<patch>` push에 **Flutter web 빌드 + linux/amd64 이미지 빌드 + GHCR push까지만** 한다. 배포는 로컬 PC에서 `ssh <server> "sudo -u deploy /opt/devpilot/deploy.sh v0.x.y"`로 실행한다(GHCR pull → compose up → `127.0.0.1:18080/actuator/health` 확인 → 실패 시 이전 태그 롤백). Tailscale OAuth·ACL·노드 태깅·Tailscale SSH는 **Later**다. ADR-016의 원칙(접속은 Tailscale로만, 공개 SSH 없음)은 그대로다.

**Alternatives considered** — Actions에서 tailnet 배포(설정 4종이 S0를 늘리고, 실패 시 디버깅이 어렵다) · 서버에서 이미지 직접 빌드(2 vCPU에서 느리고 공용 서버 자원을 오래 점유) · watchtower 같은 자동 pull(태그 승인 없이 배포됨).

**Consequences** — 배포에 사람이 필요하다(스프린트당 1~2회라 수용). 배포 이력은 GHCR 태그와 서버의 현재 태그 파일로 남는다. 자동 배포가 필요해지면 Tailscale OAuth를 추가하고 이 ADR을 대체한다.

---

## ADR-035 AI 지출 상한은 선불 잔액 + 앱 예산 (외부 하드 캡 없음)

- **Status**: Accepted · **Date**: 2026-09-18 · **Related DEC**: DEC-06 · **Note**: ADR-029의 "Console 지출 한도" 부분을 대체한다

**Context** — ADR-029는 앱 예산(USD 25)의 이중 방어로 Anthropic Console workspace 지출 한도를 뒀다. DeepSeek(ADR-032)에는 workspace도 콘솔 하드 캡도 없고 **선불 잔액**만 있으며, 운영과 eval이 같은 키·같은 잔액을 쓴다. 잔액이 떨어지면 호출은 HTTP 402로 실패한다.

**Decision** — (1) 월 예산 기본값을 **USD 3**으로 낮춘다(실측: coach 리뷰 1회 ≈ USD 0.005~0.011). 서비스 전체 합계·Asia/Seoul 달력 월 규칙은 ADR-029 그대로다. (2) 외부 상한은 **선불 잔액을 소액으로 유지**하는 것으로 대신한다. (3) `AiBalanceCheckJob`(매시)이 `GET /user/balance`를 확인해 잔액 < `min-balance-usd`(1.00)이거나 호출에서 402를 받으면 `aiStatus = BALANCE_EXHAUSTED`로 바꾸고 감사 이벤트 `AI_BALANCE_LOW`를 남긴다. 다음 정상 조회에서 해제한다. (4) **피크 시간대(월~금 UTC 01–04, 06–10) 판정을 구현하지 않고 항상 ×2로 보수 계산한다.** (5) eval은 `workflow_dispatch` 수동 실행만, 1회 상한 USD 0.5.

**Alternatives considered** — 피크 시각 규칙 구현(경계 버그 위험 대비 이득이 작다 — 과소 집계는 예산 가드를 무력화하지만 과대 집계는 안전하다) · eval 전용 계정·키 분리(계정 2개 관리, 잔액 2곳 충전) · 잔액 확인 없이 402만 처리(첫 402 전까지 사용자가 이유 없이 실패를 본다).

**Consequences** — 실제 비용은 계산값의 절반 이하일 수 있다(항상 ×2). 잔액이 떨어지면 AI 기능 전체가 멈추므로(비-AI 기능은 정상, AC-12) 잔액 경고를 놓치지 않는 것이 운영 항목이다(`10` §11). eval 1회(≈ USD 0.28)가 월 예산의 10%다.

## ADR-036 학습 루프: 읽는다 → 만든다 → 러버덕으로 설명한다 → 반복한다

- **Status**: Accepted · **Date**: 2026-09-18 · **Related DEC**: DEC-24, DEC-26

**Context** — 사용자 확인(2026-09-18): DevPilot은 **학습 도구**이고 목적은 개발 실력을 올리는 것이다. 실무에 쓰이는 것(회원가입·로그인·주문·CRUD·트랜잭션 같은 정석 프로젝트) 위주로 공부하고 반복하며, 그 결과물인 사이드 프로젝트가 배운 것을 확인하는 근거가 된다. 공부 방식의 중심은 **AI와 러버덕 코딩**이다. 교재 예제는 너무 간단해서 실무 방식과 다르다. 기존 설계의 루프(개념 → 연습문제 → 복습)는 "만든다"와 "설명한다"가 비어 있었고, 자기설명은 한 번 쓰고 채점받는 일방향이었다. 계획 템플릿도 과목 순서(Java 기초 → Spring → DB 심화 → 배포 → 정리)라 한 달을 배워도 만들 것이 없었다.

**Decision** —
1. **러버덕**(독립 모듈 `rubberduck`의 `RubberDuckSession` — 학습 세션·복습·challenge·과제·프로젝트를 모두 대상으로 읽으므로 `learning`에 두면 순환이 생긴다)을 중심 기능으로 둔다. 사용자가 설명하고 AI는 **답을 주지 않고 질문만 되묻는다**(RD-1~7, `06` §9.5). 서버 가드 `NoAnswerGuard`(`17` §6.8)가 단정 표현·코드를 막는다. 막힌 지점(`gaps`)은 복습 카드가 되고, 빈틈 없이 3턴 이상 설명한 세션만 EXPLANATION 증거가 된다(coverage 고정 7000, `06` §7.2).
2. **코드 읽기**(`TaskType.READ_CODE`): 검증된 오픈소스 3개(spring-modulith example, spring-petclinic, spring-restbucks — 모두 Boot 4.x)의 **파일 하나·줄 범위 하나**를 읽고 러버덕으로 설명한다. 저장소는 런타임에 AI가 찾지 않고 **콘텐츠로 큐레이션**한다(`content/curated-repos.yaml`, `pinnedCommit` 고정). **서버는 코드를 가져오지 않는다** — 사용자가 로컬에 clone한다(URL fetch 금지 `07` §5.5 유지, AI 비용 0).
3. **사이드 프로젝트**(새 모듈 `project`, `side_project`): 온보딩 마지막에 하나 만든다(기본 이름 "주문 시스템", 건너뛰기 가능). `PROJECT_TASK`와 Coach 리뷰가 이 프로젝트를 가리킨다.
4. 계획 템플릿을 **주문 시스템을 만드는 순서**로 바꾼다(9개 milestone, 1~6 MUST, 7~9 SHOULD — 기한이 촉박하면 7~9부터 잘린다).
5. 기한 역산을 **양방향**으로 한다: 촉박하면 축소·defer(기존), 여유가 30% 이상이면 확장(복원·목표 +1, `06` §4.4 6단계, `acceptedTargetRaises`).
6. 복습에 **교차 학습**(`RV-INTERLEAVE`, 결정적 재배치)을 넣고, `06` §6.0에 규칙별 학습 원리 근거(간격·인출·교차·생성·정교화·적정 난이도)를 적는다. 근거가 약하거나 반증된 이론(학습 유형론 등)은 쓰지 않는다.
7. 온보딩 시작점은 **짧은 진단**을 우선한다(건너뛰면 자기평가).

**Alternatives considered** — 러버덕을 기존 자기설명 확장으로만 두기(일방향이라 "막히는 지점"이 드러나지 않는다) · AI가 GitHub에서 저장소를 실시간 검색·요약(URL fetch 금지 위반, 별점 ≠ 품질 — 조사 결과 84k 별 저장소의 테스트가 162줄, 코드를 AI에 보내면 월 USD 3 예산 초과) · 사이드 프로젝트를 DevPilot 자체로 하기(사용자 부정: DevPilot은 학습 도구이지 사이드 프로젝트가 아니다) · 과목 순서 템플릿 유지(배운 것을 바로 구현할 수 없다).

**Consequences** — AI operation 2개(`RUBBER_DUCK`, `RUBBER_DUCK_SUMMARY`), 테이블 3개(`side_project`, `rubber_duck_session`, `rubber_duck_turn`)와 컬럼 3개(`learning_task.side_project_id`·`reading_key`, `coach_review.side_project_id`, V9), endpoint 11개(`/rubber-duck*` 5, `/side-projects*` 5, `GET /readings/{key}`)가 늘었다. 러버덕 5턴 + 정리 1회 ≈ USD 0.008(피크 ×2). 러버덕·코드 읽기는 AI가 필요하므로 `aiStatus`가 불가면 제안하지 않는다 — 계획·복습·기록은 AI 없이 동작한다(ADR-008 유지). 큐레이션 저장소의 줄 번호는 `pinnedCommit`에 묶이므로 갱신은 콘텐츠 작업이다(`19` §8.4). `NoAnswerGuard`는 문자열 검사라 서술형 정답은 통과한다 — 명백한 위반을 잡는 안전망이고, prompt 품질은 eval로 확인한다(`BL-AIP-13`을 S3으로 당긴 이유).

## ADR-037 날짜 없는 단계(M1/M2)

- **Status**: Accepted · **Date**: 2026-09-18 · **Related DEC**: DEC-25

**Context** — v2 로드맵은 2주 스프린트 8개를 날짜로 고정했고(S0 2026-09-17 ~ S7 2027-01-25), "2026-10-26 실사용 시작", "2027-01-05 중간 점검", "2026-11-09 stop-loss" 같은 날짜를 문서 곳곳에 썼다. 이 날짜들은 사용자가 정한 적이 없다. 사용자는 "최대한 빠르게 만들고 공부할 때 쓴다"고 했고, 학습 목표일은 사용자가 설정창에 등록하는 값이다. 구현은 전부 에이전트가 하므로 "사람이 저녁에 짬 내서 만드는" 전제의 날짜 고정 스프린트가 맞지 않는다. 한편 S0~S7 ID는 문서 전체에 1,200곳 넘게 참조되어 있다.

**Decision** — (1) **S0~S7 ID는 유지하되 의미를 "구현 순서 단계"로 바꾼다.** 기간·날짜가 없고 exit criteria를 통과하면 끝난다. (2) **M1 = S0~S3**(쓸 수 있는 최소, S3 완료 = 실사용 시작), **M2 = S4~S7**(M1을 쓰면서 필요한 순서로, 잠정). (3) budget·risk·replan 제안은 S5 → **S2**(기한 역산은 MUST이고 planner 입력이다), ICS 캘린더·AI 문제 생성은 S3 → S5, CSP 강제는 S3 → S4, evals v1은 S4 → S3. (4) stop-loss는 날짜 대신 "실사용 시작 + 14 plan-day"로 판정한다(`11` §6). (5) 문서의 지어낸 날짜를 지운다. 알고리즘 예시 입력 날짜(`19` §5.4, `05` JSON 예시 등)는 예시임이 분명하면 둔다. 기존 ADR 본문의 날짜 표현(ADR-024·031·033)은 결정 내용을 바꾸지 않고 단계 표현으로만 고쳤다.

**Alternatives considered** — M1/M2로 ID 전면 교체(1,200곳 수정, 모순 위험이 이득보다 크다) · 날짜 유지·범위만 조정(사용자가 정하지 않은 날짜가 계속 판단 기준이 된다) · 단계 없이 BL 우선순위만(구현 순서·의존이 흐려지고 에이전트 작업 지시 단위가 사라진다).

**Consequences** — `13` BL `Sprint` 열 값은 그대로(S0~S7/Later)이고 M1/M2는 `11` §3에서 묶는다. 실사용 시작일은 날짜가 아니라 사건이며, 통과한 날을 `20`에 기록해 stop-loss·성공 지표의 기준일로 쓴다. M1이 커졌다(S2에 기한 역산, S3에 러버덕·코드 읽기) — 대신 "매일 쓰기 시작"에 필요한 것이 M1 안에 모두 있다.

## ADR-038 첫 릴리스 전 migration 확정, 이후 불변

- **Status**: Accepted · **Date**: 2026-09-19 · **Related DEC**: —

**Context** — 첫 릴리스 전(pre-release)이라 운영 데이터가 없고, V1~V9는 개발 DB에만 적용되었다.

**Decision** — pre-release 동안 V1~V9는 첫 릴리스 전에 제자리에서 확정한다(`database/schema.sql`은 Flyway 결과와 같게 유지). 첫 릴리스부터 적용된 migration은 불변이고, 스키마 변경은 새 migration(V10~)으로만 한다.

**Alternatives considered** — 지금부터 보정 migration을 쌓기(운영 데이터가 없는데 이력만 길어진다).

**Consequences** — 개발 DB는 확정된 V1~V9로 다시 만든다. 첫 릴리스 이후에는 `AGENTS.md`의 "적용된 Flyway migration 수정 금지"가 예외 없이 적용된다.
