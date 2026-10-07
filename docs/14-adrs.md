# 14. Architecture Decision Records

> Status: Accepted (v3) · Last updated: 2026-09-20 · Related: `20-decisions-and-risks.md`, `03-system-architecture.md`, `11-development-roadmap.md` §4
>
> 되돌리기 비용이 큰 결정과 그 이유를 기록한다. 결정을 바꿀 때는 기존 ADR을 고치지 않고 `Superseded by ADR-xxx`로 표시한 뒤 새 ADR을 추가한다. 적용된 기본값(DEC)은 `20-decisions-and-risks.md` §1, 확인할 외부 사실은 같은 문서 §3이 기준이다.

---

## 0. 운영 규칙

| 항목 | 규칙 |
|---|---|
| 새 ADR | 다음 번호(ADR-041~)로 추가. 필드: Status, Date, Context, Decision, Alternatives considered, Consequences, Related DEC |
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
| ADR-039 | 학습 목표 = 학습 트랙 + 목표일 하나 | Accepted | DEC-27 |
| ADR-040 | 실력을 증명하는 세 가지: AI 없는 재현 · 학습자별 트랙 · 프로젝트 기록 | Accepted | DEC-29, DEC-30, DEC-31 |
| ADR-041 | 오늘의 팁·용어·과제 체크리스트는 DB가 아니라 콘텐츠로 둔다 | Accepted | DEC-32 |
| ADR-042 | 학습 단계(반복 고리)는 저장하지 않고 파생 계산한다 | Accepted | DEC-33 |
| ADR-043 | 하루를 추가 과제로 채우고, 같은 과제 유형이 이어지면 누른다 | Accepted | DEC-35 |

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

**Context** — v2 로드맵은 2주 스프린트 8개를 날짜로 고정했고(S0 2026-09-17 ~ S7 2027-01-25), "2026-10-26 실사용 시작", "2026-11-09 stop-loss" 같은 날짜를 문서 곳곳에 썼다. 이 날짜들은 사용자가 정한 적이 없다. 사용자는 "최대한 빠르게 만들고 공부할 때 쓴다"고 했고, 목표일은 사용자가 설정창에 등록하는 값이다. 구현은 전부 에이전트가 하므로 "사람이 저녁에 짬 내서 만드는" 전제의 날짜 고정 스프린트가 맞지 않는다. 한편 S0~S7 ID는 문서 전체에 1,200곳 넘게 참조되어 있다.

**Decision** — (1) **S0~S7 ID는 유지하되 의미를 "구현 순서 단계"로 바꾼다.** 기간·날짜가 없고 exit criteria를 통과하면 끝난다. (2) **M1 = S0~S3**(쓸 수 있는 최소, S3 완료 = 실사용 시작), **M2 = S4~S7**(M1을 쓰면서 필요한 순서로, 잠정). (3) budget·risk·replan 제안은 S5 → **S2**(기한 역산은 MUST이고 planner 입력이다), ICS 캘린더·AI 문제 생성은 S3 → S5, CSP 강제는 S3 → S4, evals v1은 S4 → S3. (4) stop-loss는 날짜 대신 "실사용 시작 + 14 plan-day"로 판정한다(`11` §6). (5) 문서의 지어낸 날짜를 지운다. 알고리즘 예시 입력 날짜(`19` §5.4, `05` JSON 예시 등)는 예시임이 분명하면 둔다. 기존 ADR 본문의 날짜 표현(ADR-024·031·033)은 결정 내용을 바꾸지 않고 단계 표현으로만 고쳤다.

**Alternatives considered** — M1/M2로 ID 전면 교체(1,200곳 수정, 모순 위험이 이득보다 크다) · 날짜 유지·범위만 조정(사용자가 정하지 않은 날짜가 계속 판단 기준이 된다) · 단계 없이 BL 우선순위만(구현 순서·의존이 흐려지고 에이전트 작업 지시 단위가 사라진다).

**Consequences** — `13` BL `Sprint` 열 값은 그대로(S0~S7/Later)이고 M1/M2는 `11` §3에서 묶는다. 실사용 시작일은 날짜가 아니라 사건이며, 통과한 날을 `20`에 기록해 stop-loss·성공 지표의 기준일로 쓴다. M1이 커졌다(S2에 기한 역산, S3에 러버덕·코드 읽기) — 대신 "매일 쓰기 시작"에 필요한 것이 M1 안에 모두 있다.

## ADR-038 첫 릴리스 전 migration 확정, 이후 불변

- **Status**: Accepted · **Date**: 2026-09-19 · **Related DEC**: —

**Context** — 첫 릴리스 전(pre-release)이라 운영 데이터가 없고, V1~V9는 개발 DB에만 적용되었다.

**Decision** — pre-release 동안 V1~V9는 첫 릴리스 전에 제자리에서 확정한다(`database/schema.sql`은 Flyway 결과와 같게 유지). 첫 릴리스부터 적용된 migration은 불변이고, 스키마 변경은 새 migration(V10~)으로만 한다.

**Alternatives considered** — 지금부터 보정 migration을 쌓기(운영 데이터가 없는데 이력만 길어진다).

**Consequences** — 개발 DB는 확정된 V1~V9로 다시 만든다. 첫 릴리스 이후에는 `AGENTS.md`의 "적용된 Flyway migration 수정 금지"가 예외 없이 적용된다.

## ADR-039 학습 목표 = 학습 트랙 + 목표일 하나

- **Status**: Accepted · **Date**: 2026-09-19 · **Related DEC**: DEC-27

**Context** — 계획과 기한 역산에 필요한 사용자 입력은 **무엇을**(학습 트랙)과 **언제까지**(목표일)다. 날짜가 하나면 budget horizon(`06` §3.1)과 계획 템플릿 배치 창(`19` §5)의 끝이 같은 날이 되어 규칙이 한 갈래로 정해지고, 온보딩 첫 단계가 짧아진다. 현재 실력은 자기 신고가 아니라 짧은 진단(FR-02)과 증거 레벨(FR-06)로 잰다.

**Decision** —
1. 학습 목표(`learning_goal`)는 학습 트랙(`target_role`, 화면 "학습 트랙")과 **목표일**(`target_completion_date`, 화면 "목표일") 두 값이다. 목표일은 내일(plan-day + 1)부터 오늘 + 3년까지다.
2. budget·risk horizon은 항상 목표일이다: `horizonDate = targetCompletionDate`(`06` §3.1).
3. 계획 템플릿 배치는 창 하나 `[today, targetCompletionDate]`에 모든 milestone을 템플릿 순서대로 `weightBp`에 따라 배분한다. `PREPARATION` milestone이 앞에, `CONSOLIDATION`("설명과 정리")이 목표일 바로 앞에 온다(CV-33). test vector는 `19` §5.4 V1~V8이다.
4. 사용자 프로필(`app_user`)은 계획·날짜 계산에 쓰는 값(표시 이름, timezone, 하루 시작 시각, 평일·주말 학습 시간)만 받는다. 개발 경험 같은 자기 신고 값은 받지 않는다.
5. 온보딩 1단계 제목은 "무엇을, 언제까지 공부할지 정해요"이고, 입력은 표시 이름·학습 트랙·목표일(빠른 선택 "3개월 후"·"6개월 후"·"1년 후"·"직접 선택")이다(`02` SCR-ONBOARDING).

**Alternatives considered** — 필수(MUST) 항목용 날짜를 따로 받아 배치 창과 horizon을 둘로 나누기(규칙이 두 갈래가 되고 사용자가 정할 값이 는다. "설명과 정리"의 위치는 템플릿 순서만으로 정해진다) · 개발 경험 프로필로 시작 수준이나 AI 맥락을 조정하기(자기 신고라 부정확하다. 진단과 증거 레벨이 같은 일을 더 정확하게 한다).

**Consequences** — API: `LearningGoalView`·`LearningGoalUpdateRequest`·`LearningGoalInput`의 날짜 필드는 `targetCompletionDate` 하나이고, `OnboardingRequest`·`MeResponse`·`UpdateMeRequest`는 프로필 필드로 표시 이름·timezone·하루 시작 시각·학습 시간만 갖는다(`05` §3·§4·§5). DB: `learning_goal`의 날짜 컬럼은 `target_completion_date` 하나다(V2, ADR-038에 따라 첫 릴리스 전 확정). 목표일을 바꾸면 horizon과 배치 창이 함께 바뀌므로 활성 plan에 `replan_recommended = true`를 둔다(`06` §11.1). 화면에는 날짜 표시가 목표일 하나다(계획 헤더·설정 요약 "목표일 {date}", 타임라인의 목표일 표시).

## ADR-040 실력을 증명하는 세 가지: AI 없는 재현 · 학습자별 트랙 · 프로젝트 기록

- **Status**: Accepted · **Date**: 2026-09-20 · **Related DEC**: DEC-29, DEC-30, DEC-31

**Context** — DevPilot이 재는 것은 "얼마나 공부했나"가 아니라 **무엇을 할 수 있게 됐나**다(`01` §5). 지금까지의 증거 경로에는 세 가지 빈틈이 있다.

1. **도움을 받은 상태에서만 잰다.** challenge는 Hint Ladder와 함께 풀고, 코드 읽기는 러버덕으로 설명하며 끝난다. `06` §7.2의 "독립"은 그 한 번의 세션 안에서 힌트를 봤는지만 본다. AI가 옆에 있을 때 풀린 것은 이해한 것처럼 느껴지지만, 며칠 뒤 혼자 같은 것을 만들 수 있는지는 확인된 적이 없다.
2. **학습자 하나를 전제한다.** `TargetRole`이 `JAVA_BACKEND` 하나여서 role target·계획 템플릿·과제 난이도가 모두 한 수준이다. 같은 사이드 프로젝트 주제를 **개발을 막 시작한 사람**이 함께 공부하면 필수 항목이 너무 많고 과제가 너무 어렵다.
3. **프로젝트의 과정이 남지 않는다.** 사이드 프로젝트(`side_project`)는 이름·설명·저장소 주소만 있고, 그 안에서 **무엇을 왜 골랐는지**와 **무엇이 어떻게 깨졌는지**는 어디에도 기록되지 않는다. 며칠만 지나도 흐려지고, 나중에 설명(FR-25)이나 학습 기록(FR-18)을 만들 때 재료가 없다.

셋은 따로 보면 별개 기능이지만 같은 질문의 세 면이다 — **누가, 무엇을, 정말로 할 수 있는가.**

**Decision** —

1. **재현 과제(`TaskType.REDO`, FR-28)를 둔다.** `CHALLENGE`·`PROJECT_TASK`를 마치고 **3~7일**(`devpilot.planner.redo.*`, 양 끝 포함) 뒤의 plan-day에 Today가 같은 것을 **AI 없이 처음부터 다시 만드는** 과제를 제안한다. 규칙은 `06` §5.10 RE-1~RE-8이다.
   - **열려 있는 동안 그 대상의 AI 지원을 잠근다**(RE-5, HL-9): 그 challenge의 hint와 그 대상의 러버덕 시작이 409 `AI_ASSIST_LOCKED_FOR_REDO`다. 다른 대상과 복습·계획·기록은 그대로다. 잠금 판정은 `learning.application.RedoLockProvider` port(구현 `today`)로 한다.
   - 완료할 때 **질문 하나**에 답한다(RE-6): "AI 도움 없이 끝냈나요?" 답은 `learning_task.redo_without_ai`와 `REDO_COMPLETED` payload에 남는다.
   - **성공한 재현만 독립 구현 증거**다(RE-8). `06` §7.2의 `I3_SOLVED_INDEPENDENT`는 "독립 해결한 challenge 평가 **또는** 성공한 재현"을 `evidenceKey`로 구분해 센다. 실패는 벌이 아니라 복습 카드가 되고(RE-7), 창이 다시 열려 최대 2회까지 시도한다.
   - 재현 과제는 **AI를 부르지 않으므로** `aiStatus`와 무관하게 동작한다.
2. **학습 트랙을 둘로 한다(FR-03).** `TargetRole`에 `JAVA_BACKEND_STARTER`("Java 백엔드 입문")를 더한다. 트랙이 바꾸는 것은 네 가지뿐이다 — role target 파일(필수 skill 수와 목표 레벨), 계획 템플릿, planner 기본값(`devpilot.tracks.<트랙>`의 `max-task-difficulty`·`read-code-min-knowledge`), 진단 제안 범위. **점수·복습 간격·레벨 갱신·기한 역산 규칙은 두 트랙에서 같다.** skill 카탈로그도 공유한다(같은 Java 백엔드 스택이다). 트랙은 온보딩 1단계에서 고르고 **이후 바꾸지 않는다**. 두 사용자는 allowlist로 초대된 **독립 계정**이고 데이터 공유도 상호 조회도 없다(DEC-01, `07` §4.3).
3. **사이드 프로젝트에 기록을 둔다(FR-29).** `side_project_note` 한 테이블에 **결정 기록**(무엇을 골랐나·선택지·왜)과 **장애 기록**(증상·발견·수정·예방)을 남긴다. 텍스트와 날짜, 선택 skill 하나뿐이고 파일 업로드는 없다. 유형은 생성 시 고정이고(PN-2) 본문 컬럼 조합은 DB CHECK로 강제한다(I-22). 기록은 **학습 이벤트를 만들지 않고 레벨을 바꾸지 않는다**(PN-3) — 자기 신고 텍스트이기 때문이다. 대신 주간 리뷰 지표(`projectNoteCount`)와 학습 기록 초안(`POST /evidence/drafts`의 `sourceProjectNoteId`)의 입력이 된다.

세 기능이 함께 쓰는 스키마 변경은 `V10__track_notes_redo.sql` 하나로 묶는다(`04` §10.1). `V1`~`V9`는 고치지 않는다(ADR-038).

**Alternatives considered** —

- **재현 대신 기존 증거를 더 엄격하게** (예: 힌트를 본 풀이를 아예 증거에서 빼기) — 힌트는 막혔을 때 쓰라고 둔 것이고(FR-10), 쓰면 손해라는 신호를 주면 좌절한 채로 버티게 된다. 시간을 두고 다시 재는 쪽이 원리(간격 효과·인출 연습, `06` §6.0)와도 맞는다.
- **재현 결과를 서버가 판정** (제출물 비교·유사도·AI 채점) — DevPilot은 사용자의 구현물을 받지 않는다(코드 읽기·프로젝트 과제 모두 로컬에서 한다). AI에게 "혼자 했는지"를 묻는 것은 `AGENTS.md`가 금지하는 "AI가 증거를 결정"하는 경로다. 질문 하나를 믿는다.
- **잠금 없이 권고만** — 잠기지 않으면 막혔을 때 힌트를 보게 되고, 그러면 재현의 의미가 사라진다. 대신 잠금을 **그 대상·열려 있는 동안**으로 좁히고 화면이 이유를 먼저 보여 준다.
- **트랙을 난이도 슬라이더 하나로** (같은 role target에 배율) — 입문자에게 필요한 것은 "같은 목록을 쉽게"가 아니라 **더 짧은 필수 목록**이다. 배율로는 MUST 수를 줄일 수 없다.
- **입문 트랙에 별도 skill 카탈로그** — 같은 스택을 배우는데 카탈로그가 둘이면 증거·복습 카드·문제를 공유하지 못하고 콘텐츠가 두 배가 된다.
- **트랙 변경 허용** — role target·계획 템플릿·`user_skill_state` 대상 집합이 통째로 달라져 계획과 증거를 이을 수 없다. 필요하면 별도 ADR로 마이그레이션 규칙을 정한다.
- **기록을 러버덕 대화에서 자동 추출** — 러버덕은 대상이 있어야 시작하고 AI가 필요하다. 결정·장애는 AI 없이 즉시 남길 수 있어야 한다.
- **기록을 skill 레벨 입력으로** — 자기 신고 텍스트라 흔들린다. `06` §7의 입력은 관찰된 행동(평가·복습·힌트)으로만 유지한다.

**Consequences** —

- **DB**: `V10` 하나로 `learning_task`에 `redo_source_task_id`·`redo_without_ai`(+ CHECK 3종, I-20·I-21), `side_project_note`(+ CHECK, I-22), 그리고 enum CHECK 재생성(`TargetRole`, `TaskType`, `LearningEventType`, `EventSourceType`, `ReviewItemSourceType`)이 들어간다. 인덱스 둘(`idx_learning_task_redo_candidate`, `idx_side_project_note_project`).
- **API**: 새 오류 코드 `AI_ASSIST_LOCKED_FOR_REDO`(409)와 field error `VALUE_REQUIRED`. `PATCH /today/tasks/{taskId}`에 `redoWithoutAi`, `MainTaskView`에 `redoSourceTaskId`·`redoSourceTaskType`·`redoWithoutAi`, 프로젝트 기록 endpoint 5개, `POST /evidence/drafts`의 `sourceProjectNoteId`, `PUT /learning-goal`의 트랙 변경 차단.
- **모듈**: `learning.application.RedoLockProvider` port가 하나 늘고(`today`가 구현) `project → skill`, `evidence → project` 의존이 추가된다. 새 규칙 클래스는 `today.domain.RedoTaskPolicy` 하나다.
- **단계**: 학습 트랙과 프로젝트 기록은 **S3**(실사용 시작 시점에 둘 다 있어야 한다), 재현 과제는 **S4**(창 특성상 첫 재현은 실사용 시작 뒤에 생기고, 잠금이 S3의 hint·러버덕 위에 붙는다). `V10`은 S3다.
- **콘텐츠**: 입문 트랙의 role target 75개와 계획 템플릿 1개를 새로 쓴다(`19` §10.4, `BL-CNT-17`). skill tree·복습 카드·challenge·curated repo는 그대로 쓴다.
- **레벨 도달 속도**: `I3`에 도달하는 경로가 하나 늘지만 조건은 더 엄격해진 셈이다 — 힌트를 보고 푼 challenge는 여전히 증거가 아니고, 재현은 며칠을 기다려야 한다. `11` §1 R-8의 "M1은 K·I·E ≤ 3" 상한은 그대로다(재현 과제는 S4).
- **AI 비용**: 0이 늘어난다. 재현 과제와 프로젝트 기록은 AI를 부르지 않고, 잠금은 오히려 hint·러버덕 호출을 줄인다.

---

## ADR-041 오늘의 팁·용어·과제 체크리스트는 DB가 아니라 콘텐츠로 둔다

- **Status**: Accepted · **Date**: 2026-09-20 · **Related DEC**: DEC-32

**Context** — 매일 배우는 것 옆에 붙일 짧은 재료 세 가지가 필요해졌다(`02` SCR-TIP-*, SCR-TERM-*).

1. **오늘의 팁** — 실무에서 실제로 터지는 것(자원을 닫지 않으면 무엇이 로그에 남는가, 로그 레벨을 언제 무엇으로 쓰는가)을 하루 1개, 2~3분 분량으로.
2. **용어 사전** — 같은 것을 "컬럼/열/칼럼"으로 다르게 부르면 설명이 흐려진다. 대표 표기 하나와 "이렇게도 부른다"를 함께.
3. **과제 체크리스트** — 이름 짓기·커밋·API 설계처럼 아무도 따로 알려 주지 않는 관례를, 필요한 순간(과제 시작 전·끝내기 전)에.

셋 다 **기존 challenge·seed 카드처럼 테이블에 넣을 수도 있고, 코드 읽기(`reading_key`)처럼 콘텐츠 파일로 둘 수도 있다.**

**Decision** — **콘텐츠 파일 + 메모리 레지스트리**로 둔다. 사용자별 상태만 DB에 남긴다.

- `content/tips/*.yaml` → `DailyTipRegistry`(today), `content/terms/*.yaml` → `TermRegistry`(review), `content/checklists/*.yaml` → `TaskChecklistRegistry`(today). 등록은 기동 시 `content` 모듈이 한다(`CuratedReadingRegistry` 선례, `03` §2.2).
- DB에 만드는 것은 **`user_daily_tip` 한 테이블뿐**이다(누가 어떤 팁을 언제 봤고 무엇을 골랐나). 용어 복습 카드는 기존 `review_item`에 `source_type = TERM`으로 들어간다. 체크리스트는 사용자별 상태가 없어 테이블이 없다.
- 팁·용어를 가리키는 값은 FK가 아니라 **key 문자열**이다(`learning_task.reading_key` 선례). 콘텐츠에서 사라진 key는 조회만 되고 새로 제안되지 않는다(`19` §8.2 은퇴).

**Alternatives considered**

| 대안 | 버린 이유 |
|---|---|
| challenge처럼 테이블 5개(`daily_tip`, `daily_tip_skill`, `term`, `term_skill`, `task_checklist`)에 seed upsert | 사용자 데이터가 붙지 않는 순수 읽기 콘텐츠에 테이블·FK·migration·seed 서비스가 다섯 벌 생긴다. 문구 하나 고치는 데 배포가 필요하고, 은퇴·교체는 `19` §8.2가 이미 콘텐츠 쪽에서 푼 문제다 |
| 앱에 하드코딩 | 콘텐츠 검증기(`19` §4)를 못 쓴다. 출처 확인·표기 통일·금지 표현 검사가 전부 사라진다 |
| 팁만 테이블, 용어·체크리스트는 콘텐츠 | 셋의 성격이 같은데 다루는 방식만 갈라진다. 일관성이 없으면 다음 콘텐츠를 어디에 둘지 매번 다시 정해야 한다 |

**Consequences**

- **좋은 점**: 팁·용어를 고치는 데 migration이 필요 없다. 검증기가 출처(`sourceUrl`)와 표기 통일(CV-104)을 강제한다. `V10`에 테이블 하나만 는다.
- **비용**: 팁·용어로 **조인·집계 쿼리를 쓸 수 없다**(예: "가장 많이 LEARNED로 표시된 팁"). 지금은 필요 없고, 필요해지면 `user_daily_tip`의 key로 콘텐츠를 메모리에서 붙이면 된다.
- **제약**: 레지스트리는 메모리라 인스턴스마다 같은 콘텐츠를 읽어야 한다. 단일 인스턴스 배포(`10` §2)라 문제되지 않는다.
- **AI 비용**: 0이다. 팁 선택·용어 검색·체크리스트 모두 AI를 부르지 않는다(`06` §5.12는 결정적 규칙이다).

---

## ADR-042 학습 단계(반복 고리)는 저장하지 않고 파생 계산한다

- **Status**: Accepted · **Date**: 2026-09-20 · **Related DEC**: DEC-32

**Context** — 한 번 보고 넘어간 개념은 남지 않는다. 그래서 필수 skill마다 **만들기 → 읽기 → 남의 코드 읽기 → 설명 → 복습 → AI 없이 다시 하기**의 여섯 단계를 한 바퀴 돌게 하고, 기술 상세 화면에 어디까지 왔는지 보여 주기로 했다(`06` §5.11, `02` SCR-SKILL-DETAIL).

문제는 **이 진행 상태를 어디에 두느냐**다. `user_skill_stage` 같은 테이블을 만들어 단계를 마칠 때마다 갱신하는 것이 흔한 방식이다.

**Decision** — **저장하지 않는다.** 조회 시점에 그 사용자·그 skill의 **학습 이벤트**(`04` §6)로 여섯 단계를 판정한다(`06` §5.11 ST-1).

- 판정 입력은 `learning_event`뿐이다. `skill` 모듈은 `learning`에만 의존하므로 `today`·`review`·`rubberduck`의 entity를 직접 읽지 않아도 된다(`03` §2.2).
- 단계 상태를 담는 컬럼·테이블·이벤트를 만들지 않는다. `LearningStageEvaluator`는 `skill.domain`의 순수 클래스다.

**Alternatives considered**

| 대안 | 버린 이유 |
|---|---|
| `user_skill_stage` 테이블에 단계별 완료 시각 저장 | **같은 사실이 두 곳에 생긴다.** 이벤트는 이미 다 있는데 파생 상태를 따로 쓰면 어긋날 수 있고(이벤트는 기록됐는데 갱신이 실패한 경우), 규칙을 고칠 때 과거 데이터를 backfill해야 한다 |
| 단계 완료를 `learning_event`의 새 이벤트(`STAGE_COMPLETED`)로 | 이벤트가 다른 이벤트에서 파생되는 구조가 된다. 순서가 꼬이면 복구가 어렵고, `06` §7.1 레벨 규칙이 이 이벤트를 또 세지 않도록 매번 제외해야 한다 |
| 화면에서만 계산(서버는 모름) | 앱 3곳(기술 상세·Today·대시보드)에 같은 규칙이 중복된다. planner의 `stageGap`(§5.4)도 서버에서 필요하다 |

**Consequences**

- **좋은 점**: 상태 동기화 문제가 없다. 규칙(§5.11 표)을 고치면 **과거 기록에 즉시 반영**된다 — backfill이 없다. `V10`에 테이블이 늘지 않는다.
- **비용**: 기술 상세를 열 때마다 이벤트를 읽는다. `idx_learning_event_user_skill_time`(`04` §11)이 이미 있고, 한 skill의 이벤트는 많아야 수백 건이라 감당된다. 느려지면 캐시를 앞에 두면 되고, 그때도 **저장소는 이벤트 하나**다.
- **주의**: `TIP_VIEWED`·`TERM_CARD_CREATED`는 단계 판정과 레벨 규칙 **양쪽에서 제외**한다(`06` §7.1) — 팁을 읽은 것만으로 단계가 차면 안 된다.

---

## ADR-043 하루를 추가 과제로 채우고, 같은 과제 유형이 이어지면 누른다

- **Status**: Accepted · **Date**: 2026-09-21 · **Related DEC**: DEC-35

**Context** — 12주 학습 시뮬레이션(`StudyJourneySimulationTest`, `09` §14)을 실제로 돌려 두 가지가 드러났다. 하루치 응답만 보면 둘 다 보이지 않는다.

1. **하루의 절반 이상이 빈다.** `06` §5.6은 하루에 main 1개와 REVIEW 1개만 만든다. 평일 60분이면 main 25~40 + 복습 8 = 33~48분(55~80%)이지만, **주말 270분이면 48분(18%)**이다. 그 결과 `06` §3.3의 `completionRateBp = Σactual / Σavail`가 **매일 빠짐없이 완주해도 24%**로 나와 하한 3_000에 붙고, 기한 위험도가 `CRITICAL`에서 내려오지 않는다. 기한 역산은 이 제품의 핵심 기능인데(`01` FR-05) 신호 역할을 못 한다.
2. **12주에 문제를 2번 풀고 설명하기를 50일 한다.** 성실한 연동 트랙 학습자의 84일 분포가 `{EXPLAIN 50, PROJECT_TASK 18, READING 9, READ_CODE 5, CHALLENGE 2}`였고 **20 plan-day 연속 `EXPLAIN`** 구간이 있었다. `06` §5.5의 `FATIGUE_*`는 **같은 skill**만 억제하고 **같은 과제 유형**은 보지 않는다. 그래서 §5.3의 마지막 분기(`EXPLAIN`)로 떨어지는 skill이 여럿이면 skill은 매일 바뀌어도 하는 일은 몇 주씩 같다.

둘 다 **규칙에 가드가 아예 없어서** 생긴 일이고, 콘텐츠를 채워도 규칙이 그대로면 다시 생긴다.

**Decision** — `06` §5.5에 **과제 유형 단조로움 modifier**를, §5.6에 **추가 과제**를 넣는다. 둘 다 결정적 규칙이고 AI를 부르지 않는다.

- **추가 과제** — main을 정한 뒤 `remaining = mainBudget − main.estimated`가 `extra-task-min-minutes`(기본 **15**) 이상이면 **순위 2위 후보부터** 같은 조정 규칙으로 과제를 더 만든다. 상한은 `max-extra-tasks`(기본 **3**)다. 같은 skill과 같은 재료(challenge·reading)는 하루에 한 번만 쓴다. `RECALL`로는 내려가지 않는다 — `RECALL`은 main을 대신하는 예비 과제이지 덧붙이는 과제가 아니다.
- **저장**: 추가 과제는 `learning_task.is_main = false`, `sort_order = main + 1, +2, …`다. daily plan당 활성 main은 1개여야 하므로(I-04, `uq_learning_task_one_active_main`) main으로 저장할 수 없다. **migration이 필요 없다** — 기존 컬럼만 쓴다.
- **응답**: 새 필드를 만들지 않는다. `TodayView.earlierMainTasks`(`05` §8.1)의 뜻을 "`mainTask`를 뺀 같은 날의 다른 학습 과제"로 넓혀 추가 과제를 함께 싣는다. `REVIEW`만 `reviewTask`로 따로 나간다.
- **단조로움 modifier** — 최근 5 plan-day의 main 과제 유형을 보고(main이 없는 날에서 끊는다) 오늘 제안과 같은 유형이 **3일 연속이면 ×0.70**, **5일 연속이면 ×0.40**이다. 적용 순서는 `FATIGUE_*` 다음(4번), `COMEBACK_HARD_TASK` 앞이다.

**Alternatives considered**

| 대안 | 버린 이유 |
|---|---|
| 남는 시간을 **무제한으로** 채운다(하루를 100% 채운다) | 주말 270분이면 과제가 6~8개가 된다. 다 못 하면 실패로 보이는 목록이라 **U-3(죄책감을 주는 UI 금지, `02` §1)**을 정면으로 어긴다. 끝까지 채운 하루보다 **끝낼 수 있는 하루**가 먼저다. 채우지 못한 시간은 `06` §3.3이 정직하게 드러낸다 |
| 추가 과제를 만들지 않고 **`completionRateBp`의 분모를 "계획한 시간"으로 바꾼다** | 숫자만 좋아지고 사용자의 하루는 그대로 빈다. 목표일 역산이 "선언한 시간을 다 쓰면 언제 끝나는가"를 답해야 하는데, 분모를 계획으로 바꾸면 그 질문에 답할 수 없다 |
| 추가 과제를 **`is_main = true`로 저장**하고 화면에서 순서대로 보여 준다 | `uq_learning_task_one_active_main`(I-04)을 지울 수밖에 없다. 그 index는 "오늘 지금 할 것은 하나"를 DB가 보장하는 장치이고, 세션·재생성·이어하기 규칙이 전부 그 위에 서 있다. 채우기 기능 하나를 위해 걷어낼 것이 아니다 |
| **새 응답 필드**(`extraTasks`)를 추가한다 | 같은 모양(`MainTaskView`)의 목록이 둘이 된다. 앱은 두 목록을 같은 카드로 그리고 상태 변경도 같은 엔드포인트로 한다 — 나눌 이유가 없다. `earlierMainTasks`는 이미 "main을 뺀 그날의 다른 과제"였고, 뜻을 넓히는 것으로 충분하다 |
| 단조로움을 **`06` §5.3 분기에서** 막는다(같은 유형이 N일 이어지면 그 분기를 건너뛴다) | 제안 분기는 "이 skill에 지금 줄 수 있는 최선"을 고르는 자리다. 거기서 유형을 막으면 재료가 있는데도 못 주는 날이 생긴다. 유형 다양성은 **후보 사이의 우선순위** 문제이므로 §5.5 modifier가 맞는 자리다 |
| `FATIGUE_*`의 문턱을 유형까지 포함해 **1·2일로** 당긴다 | 이틀에 걸친 코드 읽기, 이어서 푸는 문제처럼 **정상적인 이틀 연속**을 깨뜨린다. 같은 유형은 같은 skill보다 덜 해롭다 — 유형이 같아도 다루는 기술이 다르면 새 내용을 배운다 |
| 하루 안에서도 **같은 유형을 2개까지**로 제한한다(추가 과제에) | 규칙이 후보 목록을 두 번 훑어야 하고 vector가 배로 는다. 하루 안의 유형 반복은 날짜 축 modifier가 이미 줄이고, 하루에 진짜 문제가 되는 것은 **같은 skill을 네 번 하는 것**이다. 그쪽만 막는다 |

**Consequences**

- **좋은 점**: 선언한 시간이 실제 과제로 이어져 `06` §3.3의 완주율이 의미 있는 값이 되고, 기한 위험도가 움직인다. 유형이 한쪽으로 쏠리면 다른 유형 후보가 뒤집을 수 있게 되어 §12의 학습 루프(읽는다 → 만든다 → 설명한다)가 유지된다. **migration이 없다**.
- **비용**: 추가 과제도 reading을 소비하므로 `06` §5.3의 "최근 14 plan-day 안에 제안된 것 제외"에 걸리는 재료가 하루 최대 4배 빨리 는다. 재료가 얇은 skill은 `EXPLAIN`으로 더 빨리 떨어진다 — 콘텐츠 보강(`19` §4.1 CV-125 WARN)과 함께 가야 한다.
- **한계**: 단조로움 modifier는 후보 **사이**의 순서만 바꾼다. 모든 후보의 제안이 같은 유형이면 아무것도 바뀌지 않는다. 규칙으로 풀 수 있는 부분은 여기까지다.
- **남는 시간**: 상한 3개 때문에 주말 270분은 여전히 다 차지 않는다. **의도한 것이다** — 더 채우면 U-3를 어긴다. 상한을 올리려면 이 ADR을 대체한다.
- **AI 비용**: 0이다. 두 규칙 모두 결정적이다.
- **화면**: 추가 과제는 `02` SCR-TODAY의 `today.earlier` 접힘 목록에 들어간다. **개수 배지·진행률·퍼센트를 붙이지 않는다**(U-3).

---

## ADR-044 학습 순서는 프로젝트를 만드는 순서다

- **Status**: Accepted · **Date**: 2026-09-23 · **Related**: `06` §5.2, `19` §3.4, ADR-043

**Context** — 사용자 통합 테스트에서 드러났다. 활성 계획의 milestone은 이미 **프로젝트를 만드는 순서**다.

```
기반 다지기 → 회원과 인증 → 상품과 CRUD → 주문 생성 → 취소와 환불
          → 조회 성능 → 구조 정리 → 배포와 운영 → 설명과 정리
```

계획 템플릿에도 그렇게 적혀 있다 — *"milestone은 과목 순서가 아니라 하나를 고쳐서 배포하고 확인하기까지의 순서다."*

그런데 첫날 Today가 배정한 과제는 **`DATABASE.SQL_BASICS`**(3번째 단계 "상품과 CRUD")였고, 사유는 `DEADLINE_RISK_MUST` · `HIGH_PRACTICAL_IMPORTANCE` · `LARGE_SKILL_GAP`이었다. `milestoneId`는 `null`이다. Git·Gradle·`application.yml`·첫 API(1번째 단계)를 하나도 하지 않은 상태다.

원인은 버그가 아니라 `06` §5.2 후보 규칙 **4번**이다.

> 4. `plan_skill_target`에서 priority MUST/SHOULD인 skill

1·2번이 현재·다음 milestone으로 후보를 좁혀 놓는데, 4번이 **계획 전체의 MUST/SHOULD를 첫날부터 다시 열어 준다.** 그 뒤 §5.4 점수는 중요도·격차로 정렬하므로 결과가 **중요도 순**이 된다.

**중요도 순의 문제** — 어디에 쓰는지 모르는 채로 배우면 남지 않는다. Spring 프로젝트를 띄우지도 못하는 상태에서 HTTP 클라이언트를 배울 수는 없다. 만들면서 필요해진 순간에 배워야 개념이 자리를 잡는다.

또 하나. milestone은 **날짜**로만 넘어간다. 2주를 쉬면 1번 단계를 하나도 못 했어도 달력이 2번 단계로 옮겨 간다. 순서를 지켜도 이 구멍으로 다시 무너진다.

**Decision** — 두 가지를 바꾼다.

1. **§5.2 후보 규칙 4번을 삭제하고, 3번을 좁힌다.** 후보는 현재 milestone ∪ 다음 milestone ∪ **이미 손대 본** due review skill ∪ 재현 후보 skill이다. 중요도는 **그 단계 안에서** 순서를 정하는 데만 쓴다(§5.4 그대로).
   - 다음 milestone을 남겨 두는 이유: 현재 단계를 다 끝냈을 때 할 일이 없어지지 않게 하는 완충이다.
   - 3번을 좁히는 이유: 씨앗 카드가 9개 단계 전체에 미리 배정되어 있어, 손대 본 적 없는 뒷 단계 skill이 due라는 이유로 main task가 된다. 실제로 그렇게 나왔다(1단계를 시작도 안 한 상태에서 8단계 `EXPLANATION.PROJECT_STORY`가 오늘의 과제). 거른 skill의 **복습은 그대로 나온다** — REVIEW 과제는 별개다.
   - prerequisite 대체 규칙(§5.2 마지막 제외 항목)은 그대로 둔다 — 단계 안에서도 선행이 안 된 skill을 먼저 집어 준다.

2. **현재 milestone을 날짜가 아니라 진행으로 정한다.** `currentMilestones`를 "오늘이 기간에 포함된 milestone"에서 **"아직 완료되지 않은 가장 앞선 milestone"**으로 바꾼다.
   - **완료 기준**: 그 milestone의 MUST skill이 전부 `planningLevel ≥ target`. MUST가 없으면 SHOULD로 본다.
   - 날짜는 **위험도 계산과 표시에만** 쓴다(`06` §4). 늦어도 단계를 건너뛰지 않는다 — 늦었다는 사실은 위험도로 알린다.

**Consequences**

- 첫날 후보가 "기반 다지기" 12개로 좁혀진다. Git·Gradle·Spring 부팅·첫 API가 먼저 나온다.
- 한 단계를 끝내야 다음이 열린다. 쉬어도 순서가 보존된다.
- 기한이 촉박해도 순서를 건너뛰지 않는다. 대신 `06` §4 위험도가 올라간다. **속도를 포기하고 순서를 지킨다**는 선택이다.
- 진행이 느리면 뒷 단계 skill을 영영 못 볼 수 있다. 그건 실제로 그 상태이며, 위험도가 알릴 일이다.
- 영향: `PlannerScoring.selectCandidates`·`currentMilestones`, `06` §5.2, test vector, `StudyJourneySimulationTest`.

**Alternatives**

- *4번을 남기고 점수에 milestone 보너스만 추가* — 가중합이라 중요도가 큰 뒷 단계 skill이 계속 이긴다. 같은 문제가 남는다.
- *날짜 기반 유지* — 쉬는 기간이 순서를 깨뜨리는 구멍이 그대로다.

---

## ADR-045 개념을 한 번도 안 본 skill에는 문제를 내지 않는다

- **Status**: Accepted · **Date**: 2026-09-23 · **Related**: `06` §5.3, ADR-044

**Context** — `06` §5.3의 제안 분기는 이렇다.

```text
1. CHALLENGE    조건: AI 사용 가능 + 풀 만한 challenge 있음          ← 지식 수준 검사 없음
2. READ_CODE    조건: AI 사용 가능 + KNOWLEDGE ≥ readCodeMinKnowledge ← 문턱 있음
3. READING      조건: KNOWLEDGE < 2
```

**코드를 읽는 데는 문턱이 있는데 문제를 푸는 데는 없다.** 그래서 그 skill을 한 줄도 읽어 본 적 없는(planning KNOWLEDGE 0) 학습자도 난이도 1 challenge가 있으면 그것부터 받는다. 사용자 통합 테스트에서 실제로 그렇게 나왔고, 1단계 "기반 다지기"의 12개 skill 중 8개에 challenge가 있어 ADR-044 뒤에도 그대로 재현된다.

ADR-044가 "만들면서 필요해진 순간에 배운다"를 순서로 세웠다면, 이 분기는 그 순서 **안에서** 같은 문제를 다시 만든다 — 개념 없이 문제부터 풀면 어디에 쓰는지 모른 채 답만 맞히게 된다.

**Decision** — 1번 분기에 **`planning KNOWLEDGE ≥ trackDefaults.challengeMinKnowledge`** 조건을 더한다. 기본값 **1**이다.

- KNOWLEDGE 0이면 3번 READING으로 떨어진다. 개념 노트나 개념 읽기를 한 번 마치면 KNOWLEDGE가 1이 되고(§7) 그때 challenge가 열린다.
- **읽기 → 문제 → 복습**이 한 skill 안에서 순서를 갖는다. 계획 템플릿이 적어 둔 milestone 안의 순환(읽기 → 개념 → 구현 → 러버덕 → 복습)과 같은 모양이다.
- `readCodeMinKnowledge`와 같은 자리(`devpilot.tracks.<트랙>`)에 둔다. 트랙마다 다르게 정할 수 있다.
- **진단(`DIAGNOSTIC`) challenge는 이 문턱을 받지 않는다** — 진단은 지금 수준을 재는 것이지 가르치는 과제가 아니다(§7.4).

**Consequences**

- 새 skill의 첫 과제는 항상 읽기다. 초급자가 첫날 백지 문제를 마주하지 않는다.
- 개념 읽기도 노트도 없는 skill은 estimated 25분짜리 READING으로 떨어진다(§5.3 3번) — 재료가 없다는 사실이 드러난다. 그건 콘텐츠로 메울 일이다.
- 이미 KNOWLEDGE ≥ 1인 skill은 아무것도 바뀌지 않는다.

**Alternatives**

- *문턱 2* — 개념 읽기 하나로는 열리지 않는다. 너무 느리다.
- *challenge 난이도로만 거르기* — 난이도 1이어도 개념을 모르면 풀 수 없다. 난이도는 문제의 성질이고 문턱은 학습자의 상태다.

---

## ADR-046 위험도가 여러 날 높으면 replan을 권한다

- **Status**: Accepted · **Date**: 2026-09-23 · **Related**: `06` §4, §11.1, ADR-044

**Context** — `replan_recommended`는 지금 **학습 목표일을 바꿀 때만** 켜진다(`06` §11.1). 위험도가 몇 주 내내 `CRITICAL`이어도 앱은 아무것도 권하지 않는다. 사용자 통합 테스트에서 첫날부터 `deadlineRisk = CRITICAL`이면서 `replanRecommended = false`였다 — **"심각"이라고만 하고 무엇을 하라는 말이 없다.**

ADR-044 전에는 밀려도 달력이 단계를 넘겨 줘서 겉보기 진도는 나갔다. 이제는 **순서를 지키고 늦어진다**를 택했으므로, 밀린 사람은 1단계에 계속 머무르고 위험도만 올라간다. 그 상태를 알리고 계획을 줄이도록 이끄는 신호가 없으면, 남는 것은 영영 따라잡을 수 없는 계획과 매일 뜨는 빨간 표시뿐이다.

**Decision** — 진행 스냅샷을 만들 때(`StudyBudgetService.upsertSnapshot`, `06` §4) **최근 스냅샷이 연속으로 `riskLevel ≥ HIGH`인 날이 `devpilot.plan.replan-recommend-after-days`(기본 **7**)에 닿으면** 활성 plan의 `replan_recommended`를 켠다.

- 세는 단위는 **스냅샷이 있는 날**이다(`snapshot_date` DESC 연속). 앱을 안 연 날은 스냅샷이 없어 건너뛴다 — 쉰 날을 세어 재촉하지 않는다.
- 오늘 스냅샷을 포함해 센다. `MEDIUM` 이하가 하나라도 끼면 연속이 끊긴다.
- **켜기만 한다.** 끄는 것은 replan이 새 plan version을 만들 때다(그 plan은 `replan_recommended = false`로 시작한다).
- 이미 켜져 있으면 아무 일도 하지 않는다(멱등).

**Consequences**

- 일주일 내리 위험한 사람에게 "계획을 줄이자"가 뜬다. 지금은 아무 말도 없다.
- 하루 이틀 나쁜 날에는 뜨지 않는다 — 7일이라는 값이 그 완충이다.
- ADR-044의 대가(늦어도 순서를 지킨다)에 대응하는 출구가 생긴다. 순서를 건너뛰는 대신 **계획을 줄여서** 맞춘다.
- 스냅샷은 하루 한 번(`ProgressSnapshotJob`) 또는 Today 생성 시 만들어지므로 추가 비용이 없다. AI를 부르지 않는다.

**Alternatives**

- *`CRITICAL` 하루만으로 권고* — 첫날부터 뜬다. 계획이 원래 빡빡하면 늘 켜져 있어 신호가 죽는다.
- *달력 날짜로 세기* — 앱을 안 연 날까지 세어, 오래 쉬고 돌아온 사람에게 첫날부터 권고가 뜬다. 돌아온 날 할 말은 그게 아니다.

---

## ADR-047 설명이 안 통할 때 다른 방식으로 한 번 더 설명한다

- **Status**: Accepted · **Date**: 2026-09-23 · **Related**: `05` §21, `17` §3, ADR-045

**Context** — 사용자 통합 테스트에서 학습 흐름을 끝까지 돌려 보니, 막히는 자리가 한 군데 뚜렷했다. **노트의 `explain`이 안 통하면 거기서 끝난다.**

지금 있는 출구는 이렇다.

- **힌트** — 백지 문제를 푸는 중일 때만 쓸 수 있다. 설명 단계에서는 열리지 않는다
- **러버덕** — 질문만 하고 답을 주지 않는다(RD-1). "모르겠다"가 2턴이면 힌트 사다리로 넘어가는데, 그 사다리도 문제 풀이용이다
- **개념 읽기·코드 읽기** — 다른 자료로 옮겨 가는 것이지, 그 설명을 다시 말해 주지 않는다

즉 **"이 문단이 이해가 안 된다"에 답하는 자리가 없다.** 현재 AI operation 11개 중에도 없다. 학습자는 같은 문단을 다시 읽거나, 덮는다.

콘텐츠로 메우는 방법(설명을 두 벌 쓰기)은 글 양이 두 배가 되고, **어디서 막혔는지에 맞춰 주지도 못한다.**

**Decision** — AI operation **`LESSON_REEXPLAIN`**(`lesson.reexplain`)을 더한다. 학습 단위의 설명을 **다른 각도로 한 번 더** 풀어 준다.

- **입력은 콘텐츠와 막힌 이유뿐**이다. 단위 제목·`explain`·`oneLine`·skill 이름, 그리고 학습자가 고른 **`ConfusionReason`**(`UNFAMILIAR_TERMS` | `WHY_NOT_CLEAR` | `EXAMPLE_UNCLEAR`) 하나.
- **자유 입력을 받지 않는다.** 고정 선택지 하나면 방향을 잡기에 충분하고, 마스킹·개인정보·프롬프트 주입을 고민할 일이 없어진다.
- **백지 문제와 모범 답안을 보내지 않는다.** 보내지 않으면 흘릴 수 없다. 여기에 `CodeLeakGuard`를 걸어 **코드 블록과 3줄 이상 코드**를 막는다 — 설명은 개념에 머문다(ADR-045와 같은 결: 개념이 먼저다).
- **저장하지 않는다.** 응답으로만 보여 준다. 같은 요청을 다시 하면 다시 부른다. 학습 기록(`learning_event`)도 남기지 않는다 — 이건 배움의 증거가 아니라 읽기를 돕는 일이다.
- 호출은 **학습자가 누를 때만** 한다. 자동으로 부르지 않는다(비용, `17` §8).

**Consequences**

- 막히는 자리에 출구가 생긴다. 노트를 덮는 대신 한 번 더 시도한다.
- 콘텐츠를 두 벌 쓰지 않아도 된다. 작성 비용이 늘지 않는다.
- 저장하지 않으므로 migration이 없다. `ai_call_log`에는 다른 operation과 같게 남는다(비용 집계).
- 매번 다른 문장이 나온다 — **노트 본문과 달리 재현되지 않는다.** 그래서 본문을 대체하지 않고 보조로만 둔다.
- AI가 꺼져 있으면 버튼을 숨긴다(`02` §6.2 `aiAvailable`).

**Alternatives**

- *설명을 두 벌 쓰기* — 글 양이 두 배이고, 두 번째도 안 통하면 같은 자리에 선다. 어디서 막혔는지에 맞출 수 없다.
- *자유 입력을 받기* — 더 잘 맞출 수 있지만 마스킹·주입·저장 정책이 따라붙는다. 고정 선택지로 시작하고, 부족하면 그때 넓힌다.
- *러버덕으로 보내기* — 러버덕은 **답을 주지 않는 것**이 정체성이다(RD-1). 여기서 필요한 것은 답 쪽이다.

---

## ADR-048 만들기 과제는 무엇을 만들고 언제 끝인지 말해 준다

- **Status**: Accepted · **Date**: 2026-09-24 · **Related**: `05` §8.1, `06` §5.3·§5.8, `19` §3.11·§3.14

**Context** — `PROJECT_TASK`가 만드는 것은 제목 한 줄과 **모든 skill에 똑같은 한 문장**이다.

```java
// TaskProposalPolicy.projectTask
title       = "{프로젝트 이름}에 {skill.name} 적용하기"
description = "{프로젝트}에서 이 개념을 적용할 지점을 찾아 구현하고 이유를 적어 보세요."
```

다른 과제와 견주면 차이가 크다.

| 과제 | 주어지는 것 |
|---|---|
| 개념 노트 | 4단위 × (설명·예제·예측·빈칸·백지 문제·힌트·모범 답안·확인 목록) |
| challenge | 시나리오·문제·제약·채점 루브릭·힌트·흔한 실수 |
| **PROJECT_TASK** | **제목 한 줄, 30분** |

**만들어 본 것과 겪은 것이 남아야 하는 자리인데 가장 비어 있다.** 무엇을 만들어야 하는지도, 다 만들었는지도 알 수 없다.

재료는 이미 있다. **노트의 `inProject`**(필수 필드, `19` §3.14)가 바로 *"주문 시스템의 어디에 쓰는지"* 이고 25개 노트가 전부 갖고 있다. 그리고 **과제 체크리스트**(`19` §3.11)는 스키마·붙이는 규칙·작성 규칙까지 명세돼 있고 `05` §8.1의 `MainTaskView.checklist`로 응답에 넣기로 돼 있는데, **콘텐츠 파일도 배선도 없다.** catalog의 `files.checklists`는 필수(1개 이상)인데 목록에 없다.

**Decision** — 둘을 잇는다. 새 개념을 만들지 않는다.

1. **`PROJECT_TASK`의 설명은 그 skill 노트의 `inProject`다.** 노트가 없으면 지금의 일반 문장을 그대로 쓴다.
   - 노트가 "이 개념을 프로젝트 어디에 쓰는가"를 이미 쓰고 있으므로 따로 쓸 콘텐츠가 없다.
   - **읽기 → 만들기**가 같은 글로 이어진다. 노트를 읽은 사람은 그 문장을 다시 만난다.
2. **과제 카드에 체크리스트를 붙인다** (`19` §3.11 그대로). `content/checklists/*.yaml`을 만들고 catalog에 등록하며, `ContentSeeder`가 registry에 올리고 `MainTaskView.checklist`로 내려준다.
   - `after`가 **"언제 끝인가"** 에 답한다. 확인 질문이라 스스로 대조할 수 있다.
   - **저장하지 않는다.** 응답을 만들 때 콘텐츠 최신본을 읽는다(`05` §8.1).
   - 붙이는 규칙은 명세 그대로 — `taskType` ∈ `taskTypes` 이고 skill ∈ `skillCodes`, 여럿이면 `key` ASC 하나.

**Consequences**

- 만들기 과제가 "무엇을"과 "언제 끝"을 둘 다 말한다. 지금은 둘 다 말하지 않는다.
- 노트를 쓰면 만들기 과제도 같이 좋아진다 — 한 곳을 고치면 두 자리가 나아진다.
- 체크리스트는 `CHALLENGE`에도 붙는다(`taskTypes`). 관례만 담으므로 특정 문제의 답을 흘리지 않는다(`19` §3.11 작성 규칙).
- `whyItMatters`(같은 문장에 명세된 나머지 하나)는 이번에 다루지 않는다. skill 트리 registry가 따로 필요하고, "왜 하는가"는 "무엇을·언제"보다 급하지 않다.
- **남는 문제**: `PROJECT_TASK`의 추정 시간이 30분 고정이다. "로그인 붙이기"에 30분은 맞지 않는다. 시간까지 콘텐츠가 정하게 하는 것은 별도 결정으로 둔다.

**Alternatives**

- *만들기 과제용 콘텐츠를 새로 만든다* — 노트의 `inProject`와 내용이 겹친다. 두 곳에 같은 말을 쓰면 갈라진다.
- *milestone description을 쓴다* — 단계 단위라 skill 단위 과제에 맞지 않고, 같은 단계의 모든 과제가 같은 설명을 갖게 된다.

## ADR-049 주장은 증거가 아니다 — 단계를 넘기는 것은 증거다

- **Status**: Accepted · **Date**: 2026-09-25 · **Related**: `06` §5.2·§7.5, ADR-044, ADR-045

**Context** — 유지보수만 해 온 경력자를 가정하고 연차별로 첫 과제를 추적했더니, **4년차 이상이 첫날 빈 화면을 본다.**

입문 트랙(`JAVA_BACKEND_STARTER`)의 role target은 92개 skill 전부가 목표 3 이하다(목표 4 이상 0개). 그런데 자기평가도 3에서 잘린다.

```text
selfCap = min(self_assessed_level, 3)          # 06 §7.5
planningLevel = max(evidenceLevel, selfCap)
```

그래서 온보딩에서 모든 category에 3을 주면 **92개 skill이 전부 목표 달성으로 계산된다.** 결과는 이렇다.

| 단계 | 결과 |
|---|---|
| `isComplete` | 6개 milestone 전부 완료 |
| `currentMilestone` | 없음 |
| `selectCandidates` | 1·2번 비어 있고, `excluded`가 나머지를 전부 거른다 |
| 화면 | **"목표를 모두 달성했어요. 계획을 조정해 새 목표를 잡아 보세요."** |

**가장 도움이 필요한 사람이 가장 먼저 튕겨 나간다.** "중간쯤은 한다"고 정직하게 답한 대가다.

**Decision** — `planningLevel ≥ target` 하나로 판단하던 두 자리에 **증거 조건을 더한다.**

```java
private static boolean targetReached(SkillProfile profile, AxisLevels targets) {
    return profile.lastPracticedAt() != null && allMet(profile.planning(), targets);
}
```

1. `isComplete` — 손대 본 적 없는 skill이 있으면 그 milestone은 완료가 아니다
2. `excluded` — 손대 본 적 없는 skill은 "목표 달성"으로 걸러 내지 않는다

`lastPracticedAt`은 그 skill의 마지막 학습 이벤트 시각이다(`06` §7.1). **진단 통과(`DIAGNOSTIC_PASSED`)도 이벤트이므로 증거로 센다** — 진단을 풀면 그 자리는 그대로 넘어간다.

**Consequences**

- 자기평가만으로는 단계를 넘지 않는다. 넘기려면 **풀어 보이거나 진단을 통과해야** 한다.
- **자기평가가 버려지는 것은 아니다.** 그 skill은 후보로 남고, `planningLevel`이 높으므로 §5.3이 **자기평가한 수준의 난이도**로 제안한다. 3을 주면 난이도 3 문제가 나온다 — 과소평가당하는 느낌 없이 확인받는다.
- 그래서 온보딩에서 수준을 잘못 답해도 **첫 과제에서 스스로 교정된다.** "어느 수준으로 시작해야 할지 모르겠다"는 사람이 아무거나 답해도 되는 상태가 된다.
- 이미 증거가 쌓인 사용자에게는 아무 변화가 없다.
- **남는 문제**: 진단은 category당 1문제·최대 5개라, 92개 skill 대부분은 여전히 증거가 없다. 실력 있는 사람이 빠르게 넘어가려면 진단을 더 촘촘히 주거나 "이미 안다" 표시를 따로 둬야 한다. 지금은 문제를 풀어 넘어가는 경로 하나뿐이다.

---

## ADR-050 만들 것을 정하지 않은 사람에게는 기본 프로젝트를 준다

- **Status**: Accepted · **Date**: 2026-09-25 · **Related**: `05` §4.1·§19, `06` §5.3, `19` §3.14, ADR-048

**Context** — `PROJECT_TASK`는 두 조건이 모두 참일 때만 제안된다.

```java
if (skill.projectNeed() && input.energy() != EnergyLevel.LOW && project != null)
```

그런데 온보딩에서 사이드 프로젝트는 **건너뛸 수 있다**(SP-1, `sideProject: null`). 건너뛰면 `project == null`이라 **만들기 과제가 한 번도 나오지 않는다.** 읽기와 문제만 돈다.

처음부터 만들어 본 적 없는 사람일수록 "무엇을 만들지"를 못 정해 여기서 건너뛰기 쉬운데, **그 사람에게 가장 필요한 것이 만들기다.** ADR-048로 만들기 과제의 내용을 채웠는데 그 과제에 도달하지 못한다.

앱은 이미 기본값("주문 시스템")을 채워 보내지만, 그것은 클라이언트의 배려일 뿐 서버 계약이 아니다.

**Decision** — 온보딩에서 사이드 프로젝트가 없으면 **서버가 기본 프로젝트를 만든다.**

```yaml
devpilot:
  side-project:
    default-name: 주문 시스템
    default-description: 상품을 고르고 주문하고 결제하는 가장 작은 흐름을 직접 만든다. …
    default-stack: Java 25, Spring Boot 4.1, PostgreSQL 16
```

기본값을 주문 시스템으로 두는 이유는 **개념 노트 47개의 `inProject`가 전부 그 기준으로 쓰여 있기** 때문이다(`19` §3.14). 노트를 읽고 만들기 과제로 넘어갈 때 같은 프로젝트를 말하게 된다.

**Consequences**

- 온보딩을 마치면 사이드 프로젝트가 **언제나 있다.** `OnboardingResponse.sideProject`는 더 이상 null이 아니다.
- 이름·설명·스택은 언제든 고칠 수 있다(`05` §19.5). 원하지 않으면 지우면 된다.
- 콘텐츠가 아니라 설정에 둔 이유는 값이 세 줄뿐이고 운영 환경마다 바꿀 수 있어야 해서다. 늘어나면 콘텐츠로 옮긴다.
- **남는 문제**: 프로젝트가 생겨도 `projectNeed`가 **온보딩에서 고른 집중 skill 10개**로 제한되고, 제안 순서에서 `PROJECT_TASK`가 CHALLENGE·READ_CODE·READING 다음 네 번째다. 그래서 여전히 드물게 나온다. 만들기를 더 자주 내보낼지는 별도 결정으로 둔다.

## ADR-051 혼자 푼 것도 한 번은 돌아온다

- **Status**: Accepted · **Date**: 2026-09-25 · **Related**: `06` §6.0·§6.2·§6.3, `05` §21.7

**Context** — 노트 63개·학습 단위 210개를 채웠는데, **그중 절반이 한 번 풀고 끝난다.**

`LessonQueryService.finish`가 이랬다.

```java
if (helpLevel != HelpLevel.NONE && skillId != null) {
    registerReview(userId, skillId, unit);     // 막힌 것만 돌아온다
}
```

문서에도 임시라고 적혀 있었다.

> **도움 없이 푼 단위는 카드를 만들지 않는다** — "도움 없이 풀어도 7일 뒤 한 번"(재설계안 D-10)은 §6.2 간격 사다리와 함께 온다

**그 자리에서 풀렸다는 것이 2주 뒤에도 떠오른다는 뜻은 아니다.** §6.0이 인용한 간격 효과가 바로 그 이야기인데, 정작 노트 단위에는 적용되지 않고 있었다.

그리고 첫 due를 7일 뒤로 미루기만 하면 **사다리가 거꾸로 간다.** `fromGap`이 `interval_days = 1`로 시작하므로, 7일 뒤 처음 만나 `GOOD`으로 답하면 §6.2가 `max(2, 1 × 2) = 2`를 준다 — 7일 간격이 2일로 줄어든다.

**Decision** — 둘을 함께 고친다.

1. **푼 단위는 모두 카드가 된다.** 첫 due만 도움 여부로 가른다.

   | 어떻게 풀었나 | 첫 due | 설정 |
   |---|---|---|
   | 막혀서 도움을 받음 | 다음 날 | `lesson-helped-first-due-days: 1` |
   | 혼자 풀었음 | **7일 뒤** | `lesson-solved-alone-first-due-days: 7` |

2. **`interval_days`를 첫 due까지의 날수와 같게 시작한다.** `ReviewItem.fromGap`에 첫 간격을 넘긴다.

그래서 사다리가 이렇게 된다(`GOOD` 기준).

```text
혼자 푼 단위    7 → 14 → 28 → 56        (1주 → 2주 → 1달 → 2달)
막혔던 단위     1 → 2 → 4 → 8 → 16 → 32
```

**§6.2의 배수(×2·×3)는 바꾸지 않는다.** 1·7·30을 고정 사다리로 박는 방안도 검토했으나, 지금의 배수 방식이 이미 간격 효과를 구현하고 있고 **성적에 따라 조정된다**는 이점이 있다 — `AGAIN`이면 1로 돌아가고 `HARD`면 ×1.2로 천천히 는다. 고정 사다리는 그 적응을 버린다. 연구가 지지하는 것은 **방향**이지 특정 수치가 아니라는 §6.0의 단서를 그대로 따른다.

**Consequences**

- 210개 단위가 전부 복습에 올라온다. 읽고 한 번 푼 뒤 잊는 구조가 없어진다.
- 복습 카드 수가 늘어난다. 하루 상한(`review.max-per-day` 20)이 그것을 받아 내고, 넘치면 오래 미뤄진 것부터 나온다(§6.5).
- 혼자 푼 것이 7일 뒤에 나오므로 **첫 주에는 부담이 없다.** 노트를 많이 읽은 주의 다음 주가 무거워진다.
- 두 값 모두 설정이다. 4주 써 보고 조정한다(§6.0의 단서와 같다).
- `REDO`(며칠 뒤 AI 없이 혼자 다시 만들기, §5.10)는 이 ADR 직후 따로 구현했다. 카드 한 장이 아니라 만든 것 전체를 다시 만드는 가장 강한 인출이라 과제 생성·원본 연결·AI 잠금·증거 판정이 모두 필요했고, 그래서 한 작업으로 묶지 않았다.

---

## ADR-052 가르치는 것의 공식 문서는 allowlist에 있어야 한다

- **Status**: Accepted · **Date**: 2026-09-25 · **Related**: `06` §11.2, `03` §9, `19` §4.1 CV-71·CV-121·CV-131

**Context** — 4단계(배포와 로그 확인)의 노트를 쓰다가 막혔다. `DEVOPS.CI_GITHUB_ACTIONS` 노트의 `sources`에 **GitHub Actions 문서를 적을 수 없었다.** `docs.github.com`이 `devpilot.ai.trusted-source-hosts`에 없기 때문이다. `DEVOPS.KUBERNETES_BASICS`와 `DEVOPS.AWS_BASICS`도 같다.

가르치는 대상의 **공식 문서를 인용하지 못하는 노트**는 두 가지로 나쁘다. 읽는 사람이 원문을 확인할 길이 없고, "공식 문서를 읽고 직접 쓴다"는 콘텐츠 규칙(`19` §3.14)이 형식만 남는다.

allowlist를 그때그때 늘리는 것도 답이 아니다. 이 목록은 **AI가 지어낸 출처를 거르는 가드**이고(`06` §11.2), 늘어날수록 가드가 약해진다.

**Decision** — allowlist에 **넣는 기준**을 정하고, 그 기준으로 세 호스트를 더한다.

넣을 수 있는 호스트는 셋을 모두 만족한다.

1. **그 기술을 만든 곳이 직접 내는 문서**다 (블로그·튜토리얼·정리 글이 아니다)
2. **skill tree에 있는 skill**을 가르는 데 쓴다 (앞으로 쓸지 모른다는 이유로는 넣지 않는다)
3. 호스트가 문서 전용이다 (`docs.github.com`은 되고 `github.com`은 안 된다 — 뒤는 아무나 올리는 저장소다)

이번에 더하는 셋:

| 호스트 | 무엇을 가르치는 데 | 기준 3 |
|---|---|---|
| `docs.github.com` | `DEVOPS.CI_GITHUB_ACTIONS` | `github.com`이 아니라 문서 하위 도메인이다 |
| `kubernetes.io` | `DEVOPS.KUBERNETES_BASICS` | 프로젝트 공식 사이트이고 `/docs` 아래가 문서다 |
| `docs.aws.amazon.com` | `DEVOPS.AWS_BASICS` | 문서 전용 호스트다 |

**Consequences** — allowlist는 네 곳에 적혀 있다(`application.yml`, `03` §9, `06` §11.2, `content/tools/validate_content.py`). 넷을 같이 고쳐야 하고, 어긋나면 `ContentValidator` 쪽과 Python 쪽이 다르게 판단한다.

가드는 그대로다 — **서버는 여전히 URL을 열어 보지 않고 호스트 문자열만 본다.** 늘어난 것은 "이 호스트는 믿는다"의 목록이지 검사 방식이 아니다.

기준 2 때문에 이 목록은 커리큘럼보다 먼저 자라지 않는다. skill이 생기고, 그 skill의 노트를 쓸 때 그 자리에서 한 줄 는다.

**대안** — 노트에서 공식 문서를 빼고 allowlist 안의 문서로만 쓰기. 실제로 이번에 그렇게 써 보았는데, GitHub Actions를 Gradle 문서로만 설명하게 되어 **정작 배우는 대상이 빠졌다.** 가드를 지키려다 가르치는 것을 버리는 교환이라 택하지 않았다.

---

## ADR-053 배울 때는 이어서, 꺼낼 때는 섞어서

- **Status**: Accepted · **Date**: 2026-09-25 · **Related**: `06` §5.3·§5.5·§5.13, `05` §19.7, `03` §9

**Context** — 개념 노트를 70장 썼는데 **Today가 그것을 모른다.** `TaskProposalPolicy`가 만드는 유형에 노트가 없고(`CHALLENGE`·`READ_CODE`·`READING`·`PROJECT_TASK`·`EXPLAIN`·`RECALL`·`REDO`), 노트로 가는 길은 화면의 버튼 두 개뿐이었다. 매일 열리는 화면이 가르치는 자료를 지나쳐 문제부터 내고 있었다 — **"가르치고 나서 시험한다"가 우연에 맡겨져 있었다.**

노트를 계획에 넣으려 하자 곧바로 두 번째 문제가 드러났다. `FATIGUE_ONE_DAY`(×8,000)·`FATIGUE_TWO_DAYS`(×6,000)는 **같은 skill을 이어 하면 점수를 깎는다.** 노트 하나는 단위 3~6개이고 하루에 한두 단위씩 떼므로 여러 날에 걸친다. 어제 절반 뗀 노트가 오늘 감점을 받아 밀리고, 다음 날 또 밀린다. **무엇 하나 끝나지 않는 구조**다.

두 규칙이 같은 가정 위에 있었다 — "같은 주제를 이어 하는 것은 지루하고 비효율적이다". 이것은 **복습에는 맞고 처음 배우는 구간에는 틀리다.** 섞어 내기(interleaving)의 근거는 인출 연습에 관한 것이지, 개념을 처음 익히는 구간에 관한 것이 아니다(§6.0).

**Decision** — 셋을 함께 바꾼다.

1. **개념 익히기를 제안 분기 1번에 넣는다**(§5.3). KNOWLEDGE < 2이고 그 skill의 노트에 안 푼 단위가 남아 있으면 `CHALLENGE`·`READ_CODE`보다 앞선다. 새 `TaskType`을 만들지 않고 `READING`을 재사용하고(D-1), `reading_key`가 노트 key를 가리킨다. 화면은 `kind = LESSON`으로 구분한다(`05` §19.7) — 접두사로 추측하지 않는다.

2. **묶음을 만든다**(§5.13 `StudyThreadPolicy`). 최근 main의 skill에 노트가 남아 있으면 오늘도 그 skill이 main이다. 점수 경쟁을 하지 않는다. 저장하지 않고 `UNIT_SOLVED`에서 계산한다.

3. **`FATIGUE_*`를 폐지한다.** 이어 하는 구간이 너무 길어지는 것은 감점이 아니라 **연속 7 plan-day 상한**(TH-4)이 막는다. 감점은 "오늘 하지 마라"이고 상한은 "이만큼 했으면 넘어가라"다 — 뒤가 원하는 것이다.

**하루 몫을 자르는 법** — 노트 전체가 아니라 **다음 미완료 단위부터 오늘 시간이 되는 만큼**이다. 예산을 넘어도 **한 단위는 반드시 낸다.** 안 그러면 10분 남은 날에 12분짜리 단위가 남은 노트는 시간이 넉넉한 날이 올 때까지 영영 안 나온다.

**Consequences**
- 노트가 있는 skill은 노트를 다 뗀 뒤에야 문제가 나온다. 첫 며칠이 전부 개념 익히기가 될 수 있다 — 의도한 것이다.
- 같은 skill이 최대 7일 연속 main이 된다. `MONOTONY_*`(같은 **유형** 3·5일 연속)는 그대로라 유형이 계속 `READING`이면 그쪽이 누른다. 묶음 안에서도 적용되므로 노트가 6단위를 넘으면 후반에 점수가 눌린다 — **묶음은 순위를 앞으로 올리는 것이라 눌려도 유지된다.**
- 노트가 없는 skill은 동작이 그대로다.
- `score_breakdown.modifiers`에서 `FATIGUE_*`가 사라진다. **지난 과제의 기록에는 남아 있다** — enum 값을 지우지 않고 읽기만 멈춘다면 좋겠지만, 이 값은 `learning_task.score_breakdown` JSON이라 CHECK도 migration도 없다. 과거 JSON에 든 문자열은 그대로 두고 앱은 모르는 code를 무시한다.
- **남는 문제**: 묶음이 ①까지만이라 노트를 다 뗀 뒤 ② 종합 문제로 이어지는 것은 점수에 맡긴다. 같은 milestone 안이라 대개 이어지지만 보장은 아니다. ②·③까지 묶으려면 challenge 해결 상태를 묶음 판정에 넣어야 해서 따로 둔다.

**대안** — `FATIGUE_*`를 두고 묶음일 때만 면제하기. 규칙이 둘로 갈려 "언제 깎이는지"를 설명할 수 없게 된다. 감점의 원래 목적(한 주제에 매몰되는 것 방지)은 연속 일수 상한이 더 직접적으로 달성한다.

---

## ADR-054 지금 단계가 비어야 다음을 본다

- **Status**: Accepted · **Date**: 2026-09-25 · **Related**: `06` §5.2, ADR-044, ADR-053

**Context** — ADR-053으로 개념 노트를 계획에 넣은 뒤 **닷새를 실제로 돌려 봤다**(`FiveDayStudyThreadSimulationTest`). 결과가 이랬다.

```text
day | skill              | type      | 마친 단위
  1 | SPRING.TRANSACTION | CHALLENGE | 0
  2 | SPRING.TRANSACTION | CHALLENGE | 0
  3 | SPRING.TRANSACTION | CHALLENGE | 0
  4 | SPRING.TRANSACTION | REDO      | 0
  5 | SPRING.TRANSACTION | REDO      | 0
```

노트가 한 번도 안 나왔다. 그런데 `SPRING.TRANSACTION`은 **첫 단계 skill이 아니다.** 첫 단계의 넷(`WEB_HTTP.HTTP_BASICS`·`JAVA.EXCEPTION`·`JAVA.COLLECTION`·`TESTING.JUNIT`)은 전부 K0이고 전부 노트가 있는데, 닷새 내내 한 번도 main이 되지 못했다.

원인은 §5.2가 후보를 **합집합**으로 만들기 때문이다. 온보딩이 배정한 씨앗 카드(111장)가 뒷 단계 skill에도 due를 만들고, 그 skill은 진단으로 `lastPracticedAt`이 있어 3번 경로를 통과한다. 후보에 들어오면 `reviewUrgency`가 첫 단계의 `skillGap`·`milestoneUrgency`를 이긴다.

ADR-044는 "후보는 지금 단계로 제한한다"고 적어 두었는데, **2·3번 경로가 그 제한을 매일 우회하고 있었다.** 문서와 동작이 갈라진 자리다.

**Decision** — 합집합을 그만두고 **차례로** 본다.

1. 현재 milestone
2. 1번에 제외 규칙까지 적용해 **후보가 0개면** 다음 milestone
3. 2번까지 0개면 due review가 있고 이미 손대 본 skill
4. 재현 후보(§5.10)는 순서와 무관하게 언제나 후보 — 창이 며칠뿐이라 미룰 수 없다

같은 시뮬레이션이 이렇게 바뀌었다.

```text
day | skill                | type      | 자료                         | 마친 단위
  1 | TESTING.JUNIT        | READING   | LESSON.TESTTESTING.JUNIT.001 | 1
  2 | TESTING.JUNIT        | READING   | LESSON.TESTTESTING.JUNIT.001 | 2
  3 | TESTING.JUNIT        | READING   | LESSON.TESTTESTING.JUNIT.001 | 3
  4 | TESTING.JUNIT        | READ_CODE | READ.TESTREPO.ORDER_TEST.001 | 3
  5 | WEB_HTTP.HTTP_BASICS | READING   | LESSON.TESTSPRING.MVC.001    | 4
```

노트를 사흘에 걸쳐 떼고(1→2→3단위), 다 뗀 날 같은 skill의 코드 읽기로 넘어가고, 그다음 skill로 옮겨 간다.

**Consequences**
- **복습은 그대로 나온다.** 3번에서 걸러지는 것은 "그 skill을 오늘의 main 주제로 삼을지"뿐이고, REVIEW 과제는 main 선정과 별개다(§5.6).
- 뒷 단계 skill은 지금 단계를 마칠 때까지 main이 되지 않는다. 늦었다는 사실은 위험도와 replan 권고로 알린다(§4) — 단계를 건너뛰지 않는다는 ADR-044의 원칙 그대로다.
- 지금 단계 skill이 전부 제외되면(목표 달성·deferred·선행 미준비) 자동으로 다음 단계로 내려간다. 할 일이 없어지지 않는다.
- 테스트 fixture에 첫 단계 skill용 PRACTICE challenge를 하나 더했다. 지금 단계에 문제가 하나도 없으면 §5.3 2번 분기를 실제와 다르게 보게 된다.

**대안** — 씨앗 카드 일괄 배포를 그만두기(재설계안 R-3). 근본 원인에 더 가깝지만 온보딩·복습 전반을 건드린다. 이번에는 planner 쪽에서 막고, 일괄 배포는 따로 다룬다.

---

## ADR-055 복습은 배운 것만 돌아온다

- **Status**: Accepted · **Date**: 2026-09-25 · **Related**: `06` §6.3·§5.2, `05` §4.1, `04` §9, 재설계안 R-3

**Context** — 온보딩 8단계가 seed 카드 **111장을 전부** 복사했다. 첫 due는 하루 5장씩 나누므로 **가입 첫날부터 23일치** 복습이 깔린다.

그 23일치가 전부 **배운 적 없는 개념**이다. 0단계를 시작한 사람에게 `SYSTEM_DESIGN.CACHING`, `ALGORITHM.SORT_SEARCH` 카드가 둘째 날부터 나온다. 복습은 **인출 연습**인데(§6.0), 넣은 적 없는 것을 꺼낼 수는 없다 — 그냥 모르는 문제다.

문제는 그 카드가 안 풀린다는 것으로 끝나지 않는다. 매일 복습 칸이 모르는 것으로 차 있으면 **그 칸 자체를 안 보게 되고**, 정작 어제 배운 것의 복습까지 같이 묻힌다. 하루 상한이 20장(§6.5)이라 밀린 것부터 나오므로, 안 푸는 카드가 쌓일수록 배운 카드가 뒤로 밀린다.

ADR-054에서 planner 쪽 증상은 막았다(due skill이 지금 단계를 밀어내지 못하게). 그러나 **복습 목록 자체는 그대로**였다.

**Decision** — seed 카드를 **그 skill을 처음 배울 때** 배정한다.

- 온보딩은 카드를 하나도 깔지 않는다. `assignedSeedCardCount`는 항상 0이고 필드는 호환을 위해 남긴다.
- 방아쇠는 **그 skill을 실제로 공부한 학습 이벤트**다(`SeedCardOnFirstStudy`): `UNIT_SOLVED`, `CHALLENGE_SUBMITTED`, `CHALLENGE_EVALUATED`, `SELF_EXPLANATION_SUBMITTED`, `RUBBER_DUCK_COMPLETED`, `COACH_REVIEW_COMPLETED`, `REDO_COMPLETED`.
- **진단·팁·계획 변경은 방아쇠가 아니다.** 온보딩 진단은 지금 수준을 *재는* 것이지 배우는 것이 아니다. 여기를 넓게 잡으면 온보딩 한 번에 카드가 다시 깔려 이 변경이 무의미해진다.
- 첫 due는 **그 사용자의 마지막 seed due 다음 plan-day**부터 하루 5장씩. 여러 skill을 잇따라 시작해도 한 날짜에 겹쳐 쌓이지 않는다.
- 기동 backfill은 **이미 카드가 있거나 학습 이벤트가 있는 skill**에만 새 콘텐츠를 더한다.

**Consequences**
- 첫날 복습 칸이 **비어 있다.** 그것이 맞다 — 아직 배운 것이 없다. 첫 노트 단위를 풀면 그 단위의 카드(ADR-051)와 그 skill의 seed 카드가 같이 들어온다.
- 복습에 나오는 것은 전부 **한 번은 본 것**이 된다. "모르겠는데 왜 나오지"가 사라진다.
- 배우는 속도만큼 복습이 늘어난다 — 총량이 아니라 **진행에 비례**한다.
- `assignedSeedCardCount`를 보고 있던 화면·테스트는 0을 받는다.
- 기존 사용자(이미 111장을 받은)는 그대로다. 이 변경은 **새로 배정하는 시점**만 바꾼다.

**대안** — 지금 단계 milestone의 skill 카드만 온보딩에서 깔기. 23일이 3~4일로 줄 뿐 성격은 같다(아직 안 배운 것이 복습에 있다). 그리고 단계를 넘어갈 때마다 같은 일이 반복된다.

## ADR-056 진도는 스스로 되돌릴 수 있고, 내가 쓴 글은 남는다

- **Status**: Proposed · **Date**: 2026-09-26 · **Related**: `05` §3.4·§3.7, `02` SCR-SETTINGS·SCR-ACCOUNT-RESET, `04` §8, `07` §4.4, BL-SEC-19·BL-CLI-50

**Context** — 처음부터 다시 해 보려면 방법이 하나도 없었다. 있는 것은 `DELETE /me`뿐인데 그것은 **계정 삭제 요청**이다: `status = DELETION_REQUESTED`가 되어 `GET /me`·`DELETE /me` 외에는 전부 403이 되고, 실제 행 삭제 job(`AccountDeletionJob`)은 아직 Later다. 누르면 데이터는 그대로 남은 채 계정만 잠긴다 — 다시 시작하기의 반대다.

그래서 2026-09-26에 개발 DB에서 진도를 되돌릴 때 `psql`로 `app_user` 행을 직접 지워야 했다. 도구를 쓰는 사람이 스스로 할 수 없고, 운영자가 서버에 들어가야만 하는 일이 하나 생긴 것이다.

ADMIN 화면을 만드는 선택지도 있었다. 그러나 `07` §4.4는 **MVP에 ADMIN 전용 endpoint를 두지 않는다**고 정해 두었고, 쓰는 사람이 서너 명인 도구에서 인증 모델·화면·격리 테스트를 새로 얹는 비용은 지금 낼 이유가 없다.

**Decision** — `POST /me/reset`으로 **자기 진도만** 되돌린다. 계정은 그대로 두고 온보딩 이전 상태로 돌아간다.

- 지운다: 학습 목표·계획·milestone, 오늘 계획·과제·세션, 기술 레벨과 변경 이력, 복습 카드와 답변, 학습 이벤트, 문제 시도·제출·힌트 기록, 러버덕, 코치 리뷰, 오늘의 팁 기록, 주간 리뷰·증거 후보·요구사항 문서, 그 사용자가 만든 문제(`challenge.owner_user_id`).
- 남긴다: **계정**(`app_user` — `onboarding_completed_at`과 `calendar_token_hash`만 null로), **내가 쓴 글**(`side_project`와 `side_project_note`), **AI 호출 기록**(`ai_call_log` — 비용·감사)과 `idempotency_record`(요청 중복 처리용, 자체 TTL), **솔루션 콘텐츠**(skill·role target·seed 문제).
- 사이드 프로젝트와 그 기록은 `includeProjects: true`일 때만 함께 지운다. **기본은 남기기다** — 진도가 아니라 사용자가 직접 쓴 글이고, "진도 초기화"를 눌렀다가 몇 주치 기록이 사라지면 사고다.
- 확인은 계정 삭제와 같은 방식이다(`02` §3.2): `초기화합니다`를 정확히 입력해야 버튼이 켜진다. 되돌릴 수 없는 일에 "예/아니오"를 쓰지 않는다.
- 최근 로그인 재인증(`RECENT_LOGIN_REQUIRED`)은 **요구하지 않는다.** 계정 삭제와 달리 이 동작은 계정을 잃게 하지 않고, 남의 진도를 지우려면 이미 그 사람 토큰을 가지고 있어야 한다.

**Consequences**

- 사용자가 서버에 들어가지 않고 스스로 다시 시작할 수 있다. 운영자가 `psql`을 여는 일이 없어진다.
- 지우는 테이블 목록이 코드에 박힌다 — 새 사용자 테이블이 생기면 빠뜨릴 수 있다. `information_schema`에서 `user_id`를 가진 테이블을 읽어 목록과 대조하는 테스트로 막는다.
- `side_project`가 남은 채 온보딩을 다시 하면 프로젝트가 하나 더 생길 수 있다. 온보딩 4단계가 기존 프로젝트를 보여 주도록 하는 것은 별도 작업이다(BL-CLI-50 Known limitation).
- `AccountDeletionJob`(BL-SEC-17, Later)이 생겨도 이 endpoint와 겹치지 않는다. 하나는 계정을 지우고 하나는 진도만 지운다.

**대안** — ADMIN 페이지에서 운영자가 개별 계정을 초기화. 쓰는 사람이 늘면 다시 볼 수 있지만, 지금은 자기 것을 자기가 되돌리는 것으로 충분하고 새 인증 표면이 생기지 않는다.

---

## ADR-057 개념 노트를 건너뛰는 근거는 자기평가가 아니라 기록이다

- **Status**: Accepted · **Date**: 2026-09-29 · **Related**: `06` §5.3·§5.13 TH-5, `03` §9, ADR-045, `19` §3.14

**Context** — `06` §5.3 1번 분기는 `planning KNOWLEDGE < 2`일 때만 개념 노트를 낸다. 그런데 planning level은 `max(evidenceLevel, min(selfAssessed, 3))`이다(§7.5). **기록이 하나도 없어도 자기평가만으로 KNOWLEDGE가 3까지 올라간다.**

2026-09-29 실사용에서 드러났다. 6년 경력자가 온보딩 자가평가에서 14개 분야에 0~3을 고르자 **10개 분야가 KNOWLEDGE ≥ 2가 되어 개념 노트를 통째로 건너뛰고 바로 문제로 갔다.** 그중에는 본인이 "막 시작, 사실상 없음"이라고 말한 Spring Boot도 있었다 — 2(도움받아 가능)로 답했기 때문이다. 자기평가의 다섯 칸은 "얼마나 익숙한가"를 묻는데, 이 분기는 그 답을 "개념을 이미 배웠는가"로 읽는다. 둘은 같은 질문이 아니다.

ADR-045는 같은 문제를 반대쪽에서 고쳤다 — 개념을 한 번도 안 본 skill에 문제를 내지 않도록 CHALLENGE에 문턱을 세웠다. 그 문턱도 planning level을 보므로 자기평가로 그냥 열린다.

**Decision** — 1번 분기의 상수 2를 **`trackDefaults.lessonMaxKnowledge`**로 바꾸고, 기본값을 트랙마다 둔다.

| 트랙 | `lessonMaxKnowledge` | 뜻 |
|---|---|---|
| `JAVA_BACKEND` | 2 | 지금과 같다 |
| `JAVA_BACKEND_STARTER` | 4 | 입문 트랙은 노트를 다 뗀다 |
| `INTEGRATION_ENGINEER` | 4 | 기본기를 넓게 덮는 구성이라 노트가 출발점이다 |

- 조건이 `KNOWLEDGE < 문턱`이므로 **4는 "KNOWLEDGE 3까지 노트를 낸다"**는 뜻이다. 자기평가 상한이 3이므로(`devpilot.skill.self-assessment-cap`), 문턱 4에서는 **자기평가만으로는 노트를 건너뛸 수 없다.** 실제로 과제를 풀어 evidence가 4에 닿은 분야만 건너뛴다. 3으로 두면 자기평가 3이 그대로 통과해 이 ADR이 고치려는 상황이 그대로 남는다.
- "이미 아는 사람에게 개념부터 시키지 않는다"는 원래 의도는 그대로다. 바뀌는 것은 그 판단의 근거뿐이다 — **자기 말에서 실제 기록으로.**
- 짝이 되는 변경으로 단위마다 **"이건 알아요"**를 둔다(`02` §3.18). 누르면 `POST /lessons/{k}/units/{u}/finish`를 `helpLevel = NONE`으로 부르고 다음 단위로 간다. 아는 단위를 10초에 지나갈 수 있어야 문턱을 올려도 지루해지지 않는다.
- `readCodeMinKnowledge`·`challengeMinKnowledge`와 같은 자리(`devpilot.tracks.<트랙>`, `03` §9)에 둔다.

**Consequences**

- 연동·입문 트랙에서 새 skill의 첫 과제는 거의 항상 개념 노트다. 노트가 없는 skill은 지금처럼 3번·4번 분기로 떨어진다.
- 노트를 다 떼기 전에는 CHALLENGE·READ_CODE가 안 나온다. 노트 단위 수만큼 문제 시작이 늦어지는데, "이건 알아요"가 그 비용을 실제로 아는 만큼만 남긴다.
- `JAVA_BACKEND`는 아무것도 바뀌지 않는다(기본값 2 = 지금 값).
- **"이건 알아요"는 자기 보고다.** `helpLevel`은 복습 일정에만 쓰고 레벨에는 쓰지 않으므로(`01` 원칙 4) 이 값으로 실력을 올려 주지 않는다. 거짓으로 눌러도 손해는 본인 것이고, 레벨은 그대로 evidence로만 오른다.

**Alternatives**

- *자기평가를 낮게 적게 한다* — 도구에 사실과 다른 값을 넣는 일이다. 필요 시간이 부풀고 위험도가 첫날부터 CRITICAL로 돌아간다.
- *분기 조건을 `evidenceLevel < 2`로 바꾼다* — 자기평가를 아예 무시하게 된다. 진단으로 4에 닿은 사람도 노트를 다시 보게 되어 반대쪽으로 치우친다. 설정값 하나면 트랙마다 고를 수 있다.
- *노트를 건너뛴 skill에 안내만 띄운다* — 무엇을 하라는 말 없이 경고만 늘어난다. ADR-046에서 같은 실수를 이미 고쳤다.

---

## ADR-058 진단은 주장한 수준을 재고, 주장이 있는 모든 분야에 준다

- **Status**: Accepted · **Date**: 2026-09-30 · **Related**: `05` §4.2, `06` §7.4, `02` §4.6, `19` §3.6, ADR-057

**Context** — 진단(`purpose = DIAGNOSTIC`)은 자기평가를 실제 기록으로 바꾸는 가장 싼 수단이다. 통과하면 `06` §7.4가 그 skill의 K·I를 `max(현재, min(claimedLevel, 3))`으로 올리고, 실패하면 `self_assessment_active`를 끈다.

그런데 지금 규칙은 두 곳에서 막혀 있다.

1. **자기평가 3 이상인 category만 제안한다**(`05` §4.2 1단계, `SELF_ASSESSMENT_THRESHOLD = 3`). 2026-09-30 실사용 계정을 보면 14개 category 중 5개만 제안되고, 1~2로 답한 8개(Spring, 데이터베이스·JPA, CS 기초, 알고리즘, DevOps, 시스템 설계, 기술 설명, 테스트)는 **영원히 측정되지 않는다.** 그 분야들은 자기평가 값만 들고 증거 없이 계획에 들어간다.
2. **콘텐츠에 난이도가 하나뿐이다.** `content/challenges/diagnostic.yaml`의 10문제가 전부 difficulty 3이고, 파일 머리글이 그것을 규칙으로 적어 두었다. 1이나 2로 주장한 사람의 주장을 확인할 문제가 없다.

여기에 함정이 하나 더 있다. 후보 정렬의 마지막 키가 `seed_key ASC`이므로(4단계), 난이도 사다리를 **콘텐츠만** 추가하면 `DIAGNOSTIC.JAVA.L1.001`이 `...L3.001`보다 앞서 정렬된다. 3을 주장한 사람에게 난이도 1 문제가 나가고, 통과해도 얻는 것이 없다. 콘텐츠와 규칙을 함께 바꿔야 한다.

**Decision** — 진단 제안을 둘 다 고친다.

1. **문턱을 3에서 1로 내린다.** 자기평가 모드에서 그 category의 자기평가 최댓값이 **1 이상**이면 제안한다. 0은 제외한다 — 확인할 주장이 없고, 그 분야는 개념 노트부터 가는 것이 맞다(ADR-057).
2. **난이도를 주장한 수준에 맞춘다.** 후보 정렬에 **첫 번째 키**로 난이도 거리를 넣는다.
   - 자기평가 모드: `목표 난이도 = min(claimedLevel, 3)`. `difficulty`가 목표와 같은 것이 가장 앞이고, 없으면 **목표보다 낮은 쪽**으로 가까운 것, 그다음 높은 쪽이다.
   - 진단 모드(자기평가가 하나도 없음): **가장 높은 난이도**를 고른다. 이 모드에서는 `claimedLevel`이 null이라 `difficulty`가 그대로 레벨이 되므로(`06` §7.4), 현행 동작(d3 통과 → 3)을 유지한다.
   - 나머지 키(priority → practicalImportance DESC → `seed_key` ASC)는 그대로 뒤에 붙는다.

**왜 목표보다 낮은 쪽을 먼저 보나** — 주장보다 어려운 문제를 내면 정직하게 답한 사람이 떨어지고 `self_assessment_active`가 꺼져 레벨이 0으로 내려간다. 측정하려다 없는 벌을 주는 셈이다. 낮은 쪽은 반대로 "확인은 됐지만 덜 어려웠다"로 끝나고, 부족한 확인은 실제 과제가 이어서 한다.

**Consequences**

- 1~2로 답한 분야도 측정된다. 통과하면 자기평가가 **증거로 바뀌고**(K·I가 evidence로 기록된다), 실패하면 그 분야의 자기평가를 더 쓰지 않는다. 계획이 주장이 아니라 기록 위에 선다.
- 제안 대상이 늘어난다. `MAX_SUGGESTIONS = 5`는 그대로 두므로 한 번에 5개까지만 보이고, 진단을 받은 category는 빠지므로(2단계) 남은 것이 다음에 올라온다. 온보딩 직후 화면이 길어지지 않는다.
- 난이도 1·2 진단 콘텐츠가 필요해진다. 없는 category는 목표와 가장 가까운 난이도로 떨어지므로 **동작은 깨지지 않는다** — 다만 확인이 느슨해진다.
- `19` §3.6의 "진단은 difficulty 3" 규칙을 "1~3"으로 고친다.

**Alternatives**

- *적응형 사다리* — d2를 내고 통과하면 d3, 틀리면 d1을 낸다. 측정이 가장 정확하지만 "category당 1회" 제외 규칙(`05` §4.2 2단계)을 바꿔야 하고, 어느 rung까지 갔는지 저장해야 한다. 이번에 하지 않는다. 지금 고치는 것은 "측정 자체가 없는 8개 분야"이고, 그것이 훨씬 크다.
- *문턱만 내리고 난이도는 그대로* — 2를 주장한 사람에게 difficulty 3을 낸다. 떨어뜨리고 레벨을 0으로 내리는 결과가 되어, 측정이 아니라 벌이 된다.
- *난이도 콘텐츠만 추가* — 위 Context의 `seed_key ASC` 함정 때문에 지금보다 나빠진다.

---

## ADR-059 안다고 본 것은 다시 물어볼 수 있어야 한다

- **Status**: Accepted · **Date**: 2026-09-30 · **Related**: `05` §4.2, `06` §7.4·§7.5, ADR-057, ADR-058

**Context** — 도구는 자기평가를 받아 "이 분야는 이만큼 안다"로 계획을 세운다. 그 추정이 틀렸을 때 빠져나오는 길이 좁다.

레벨이 내려가는 규칙은 셋뿐이고(§7.3) 모두 누적 증거가 필요하며 하한이 있다. 문제를 틀리는 것으로는 내려가지 않는다 — `I_DOWN_TRANSFER_FAIL`은 전이 문제에만 걸린다. 보통 문제 한 번 틀린 것으로 레벨을 내리지 않는 판단 자체는 맞다.

남은 길은 진단인데, `05` §4.2 2단계가 **category당 1회**로 못박는다. 한 번 풀고 나면 그 분야는 다시 묻지 않는다. 그래서 자기평가를 고쳐 수준이 올라가도(ADR-058로 이제 고칠 수 있다) 그 새 주장을 확인할 자리가 없다.

즉 **도구가 자기 추정을 맞다고 전제하고, 반증할 기회를 한 번만 준다.** 2026-09-30 실사용에서 나온 지적이다 — "저 수준 평가가 완벽하게 정확히 사용자 수준을 안다고 평가하는건 자만 아닌가?"

**Decision** — 제외 단위를 category에서 **이미 푼 문제**로 내린다.

- `05` §4.2 2단계의 제외 조건을 바꾼다: `SUBMITTED`·`EVALUATED` attempt가 있는 **그 DIAGNOSTIC challenge만** 후보에서 뺀다. category는 빼지 않는다.
- 남은 후보 중에서 고르는 규칙은 그대로다 — 목표 난이도에 가까운 것 우선(ADR-058: `min(claimedLevel, 3)`, 진단 모드는 가장 높은 것).
- 그래서 **주장이 올라가면 그 새 수준에 맞는 다른 문제로 한 번 더 묻는다.** 같은 문제를 다시 내지는 않고, 안 푼 문제가 없으면 그 category는 조용히 빠진다.
- (category, 난이도) 단위로 빼는 것도 검토했다. 지금은 category당 난이도가 겹치지 않아 결과가 같지만, 같은 난이도의 문제를 나중에 더 넣으면 두 번째 문제를 못 쓰게 된다. 제외는 **실제로 푼 것**에만 걸리는 쪽이 콘텐츠가 늘어도 버틴다.

**틀렸을 때의 연쇄는 이미 있다.** `DIAGNOSTIC_FAILED`가 `self_assessment_active = false`로 바꾸고(§7.4), planning이 evidence로 떨어지고(§7.5), 문턱 아래가 되어 **다음 과제가 그 skill의 개념 노트가 된다**(ADR-057). 이 ADR은 그 문을 다시 열 뿐이고, 화면이 그 일이 일어났다고 말하게 한다.

**화면 문구**도 함께 고친다. 지금은 통과·미통과를 결과로만 알린다.

- 제안: "이건 쉬우실 거예요. 맞으면 넘어가고, 아니면 여기부터 다시 봐요."
- 미통과: "여기는 생각보다 덜 익었네요. 이 분야는 개념부터 다시 봅니다." — 무슨 일이 일어났는지와 다음이 무엇인지를 같이 말한다.

**Consequences**

- 추정이 매번 반증 가능해진다. 자기평가를 고치면(ADR-058) 그 수준으로 확인 문제가 다시 온다.
- 새 이벤트도 migration도 없다. 기존 `DIAGNOSTIC` 판정과 ADR-057 경로를 그대로 쓴다.
- 확인 문제는 여전히 **선택**이다. 건너뛰면 아무것도 바뀌지 않는다 — 넘어갈 자유가 이 설계의 전제다.
- 난이도 사다리가 없는 category는 목표 난이도가 늘 같으므로 사실상 1회로 남는다. 사다리를 채우는 만큼 이 규칙이 살아난다(ADR-058 콘텐츠 후속).

**Alternatives**

- *"모르겠어요" 자기 보고 버튼* — 누르기 민망해 안 누르고, 틀린 것보다 증거가 약하다. 버린다.
- *보통 문제 실패로 레벨 내리기* — 한 번 틀린 것은 신호가 아니다. 확인 문제는 "내밀었다"는 맥락이 있어 다르다.
- *skill 단위 확인 문제* — 더 정확하지만 진단 콘텐츠가 category 단위다(CV-59). 콘텐츠를 다시 만드는 일이라 따로 둔다.

---

## ADR-060 만들 수 있는지는 기록으로만 센다

- **Status**: Accepted · **Date**: 2026-09-30 · **Related**: `05` §7.10, `06` §11.4, `19` §3.4, ADR-044, ADR-057, ADR-059

**Context** — 계획의 milestone은 과목 목록이 아니라 **사이드 프로젝트를 만드는 순서**다(`19` §3.4). 그런데 그 순서가 화면에서는 날짜 막대와 제목으로만 보인다. 무엇을 배웠는지는 skill 화면에, 오늘 할 일은 Today에 있는데, **"그래서 지금 프로젝트를 어디까지 만들 수 있나"에 답하는 자리가 없었다.** 2026-09-30 실사용에서 나온 요구다 — "오늘까지 배운 내용을 토대로 어디까지 진행하실 수 있습니다, 어디까지 진행해보세요를 제안해주는 기능은 없나?"

답을 계산할 재료는 이미 다 있다. milestone마다 그 단계를 여는 skill(`plan_skill_target`)과 목표 레벨이 있고, 사용자의 레벨도 있다. 문제는 **어느 레벨을 쓰느냐**다.

- **계획 레벨**(§7.5)은 자기평가를 섞는다 — `active`면 `max(evidence, min(selfAssessed, 3))`.
- **근거 레벨**은 실제로 푼 문제·복습·러버덕으로만 오른다.

대시보드 타임라인의 "지금 단계"는 계획 레벨을 쓴다(ADR-044). 그래야 Today가 고르는 단계와 같아진다.

**Decision** — 이 화면은 **근거 레벨로만** 판정한다.

- 단계를 여는 skill이 모두 근거로 목표에 닿으면 `BUILDABLE`, 아니면 그중 가장 앞선 하나가 `NEXT`, 나머지는 `NOT_YET`이다(`06` §11.4).
- 자기평가는 세지 않는다. **"안다고 답한 것"과 "직접 해본 것"은 다른 질문이고, 만들 수 있느냐는 뒤쪽 질문이다.**
- 그래서 대시보드의 `current`와 이 화면의 `NEXT`가 어긋날 수 있다. 어긋남을 숨기지 않는다 — 계획은 주장을 믿고 앞서 가지만 손은 기록만큼만 움직인다는 뜻이고, 화면이 그렇게 말한다.
- 저장하지 않고 요청 시점에 계산한다. AI를 부르지 않는다.

**Alternatives**

- *계획 레벨로 판정* — 대시보드와 한 줄로 맞지만, 자기평가 3만 적어도 앞 단계가 전부 "만들 수 있음"이 된다. 확인 문제(ADR-059)를 만든 이유와 정면으로 어긋난다.
- *두 레벨을 나란히* — 정확하지만 한 화면에 레벨 체계가 둘이 되고, "그래서 지금 뭘 만들 수 있냐"는 한 줄짜리 질문에 표로 답하게 된다.
- *AI에게 단계 제안을 맡김* — 이미 있는 milestone 설명(계획 템플릿이 적어 둔 것)보다 나을 근거가 없고, 매번 비용이 든다.

**Consequences**

- 새 테이블도 migration도 이벤트도 없다. 읽기 계산 하나와 화면 하나다.
- 근거가 아직 0인 초기에는 1단계가 `NEXT`로 뜬다 — 그것이 맞는 답이다. 첫 단계가 지금 만들 단계다.
- milestone 설명이 곧 "무엇을 만들라"는 안내가 된다. 설명이 부실한 템플릿은 이 화면에서 바로 드러난다.

---

## ADR-061 잴 수 없는 축은 진도를 막지 않는다

- **Status**: Accepted · **Date**: 2026-10-01 · **Related**: `03` §9, `05` §7.10·§13.1, `06` §5.2·§7.2·§7.6·§11.4, ADR-044, ADR-049, ADR-060

**Context** — 레벨 상승 규칙(`06` §7.2)에서 **DEBUGGING은 `COACH_*` 이벤트로만 오른다.** coach(코드 리뷰) 모듈은 S4라 아직 코드가 없다. 즉 그 축의 근거는 지금 **구조적으로 쌓일 수 없다.**

그런데 연동 트랙의 MUST skill 29개 중 **22개가 디버깅 목표 ≥ 1**이다. 진도 판정은 "모든 축에서 목표 이상"을 요구하므로, 그 22개는 무엇을 해도 **영원히 목표에 닿지 않는다.** 결과가 세 곳에서 같은 모양으로 나온다.

| 곳 | 증상 |
|---|---|
| `06` §5.2 현재 milestone · 후보 제외 | 1단계가 끝나지 않아 **Today가 1단계 skill만 계속 돌린다** |
| `05` §13.1 타임라인 `current` | 대시보드가 1단계를 영원히 가리킨다 |
| `06` §11.4 만들 수 있는 단계 | 9단계 중 7단계가 절대 열리지 않는다 |

ADR-060(만들 수 있는지는 기록으로만 센다)을 만들면서 드러났다. 화면은 맞게 계산하는데 **입력이 도달 불가능**이었다. `06` §7.2에 이미 "도달 가능 상한(Sprint별, 규칙이 아니라 사실)"이라는 각주가 있었지만, 사실로만 적혀 있고 규칙이 읽지 않았다.

**Decision** — 그 각주를 설정으로 올린다. `devpilot.skill.measurable-axes`가 **지금 근거를 쌓을 수 있는 축**을 적고, 진도 판정에 들어가는 목표에서 **나머지 축을 0으로 내린다**(`06` §7.6).

- 지금 값은 `[KNOWLEDGE, IMPLEMENTATION, EXPLANATION]`이다. coach가 들어오면 `DEBUGGING`을 더하는 것으로 끝난다.
- **저장된 `plan_skill_target`은 바꾸지 않는다.** 판정에서 빼는 것이지 목표를 낮추는 것이 아니다 — 목표는 그대로 두고, 잴 방법이 생기면 그때 다시 센다.
- **예산과 위험도(`06` §3·§4)는 건드리지 않는다.** 나중에 들일 시간은 지금도 계획에 있어야 한다. 빼면 남은 일이 실제보다 적어 보이고 목표일 판단이 헐거워진다.
- **상승 규칙(§7.2)도 그대로다.** 축이 오를 길이 생기면 그때 오른다.
- 설정 값이 `SkillAxis`에 없거나 비어 있으면 **기동 실패**다. common은 도메인 enum을 모르므로(`03` §2.2) 검사는 skill 모듈이 한다.
- **화면이 말한다.** `GET /plans/active/buildable`이 `countedAxes`·`uncountedAxes`를 함께 주고, SCR-BUILDABLE이 "지금 세는 축"과 빠진 축을 보인다. 조용히 빼면 "왜 9/10에서 안 움직이나"를 알 수 없다.

**Alternatives**

- *디버깅 목표를 role target에서 0으로 낮춘다* — 콘텐츠를 도구의 사정에 맞춰 고치는 것이다. coach가 생기면 다시 올려야 하고, 그때 올렸다는 사실을 아무도 기억하지 못한다.
- *coach 모듈을 앞당긴다* — 옳지만 S4 한 스프린트짜리다. 그때까지 Today가 멈춰 있을 수는 없다.
- *"모든 축" 대신 "과반 축"* — 임의 기준이고, 잴 수 있는 축 하나를 빼먹어도 넘어가 버린다.
- *판정에서만 조용히 뺀다* — 계산은 맞지만 사용자가 9/10에서 멈춘 이유를 알 수 없다. 빼는 것은 되돌릴 결정이므로 화면에 남긴다.

**Consequences**

- Today가 1단계를 마치면 2단계로 넘어간다. 대시보드·SCR-BUILDABLE도 같은 단계를 가리킨다.
- 지금 세 축만으로 목표에 닿은 skill은 coach가 들어오면 **다시 미달이 될 수 있다.** 그때 진도가 뒤로 가는 것처럼 보이므로, coach를 들일 때 이 전이를 함께 설계한다.
- 새 테이블도 migration도 없다. 설정 한 줄과 판정 입력을 거치는 자리 셋이다.

---

## ADR-062 가이드가 먼저고 판정은 뒤다

- **Status**: Accepted · **Date**: 2026-10-01 · **Related**: `01` §4, `05` §7.9·§8.1, `06` §3.3·§3.4·§4.2·§4.3, ADR-046, ADR-060, ADR-061

**Context** — 2026-10-01 세 수준 시나리오 테스트에서 하·중 수준 모두 **첫날 Today에 빨간 배지**(`매우 빠듯함`, `AppTone.danger`)를 받았다. 사용자의 말이 정확했다 — *"목표일을 못 지킵니다 빨간색 뜨면 할 맘이 없어져. 완벽하지 않으면 시도도 못하잖아."*

사용자가 목적을 가장 짧게 적어 줬다 — *"결국 아무것도 안 하는 것보다 꾸준히 하는 게 나으니까, 그걸 습관화하고 머리에 남도록 하고, 부족하지만 목표에 다가가게 하는 솔루션이지."* 세 가지 순서가 그대로 설계 우선순위다: **꾸준함 → 기억에 남기기 → 목표에 가까워지기.** 날짜 판정은 그 셋 중 어디에도 없다.

이 도구의 목적은 **공부 가이드**다(`01` §4). "이 속도면 목표를 달성하지 못합니다"를 알려 주는 도구가 아니다. 지금 수준은 그 사람에게 **주어진 사실**이고, 목표가 크고 수준이 낮으면 도구가 할 일은 점수를 매기는 것이 아니라 **그 목표로 갈 수 있게 길을 잡아 주는 것**이다.

게다가 그 빨간 숫자는 **부풀어 있었다.** `requiredMust`는 네 축을 모두 세는데 DEBUGGING은 `COACH_*` 이벤트로만 오르고 coach 모듈은 S4다(ADR-061). axis cost에서 그 축이 약 30%(8_000 / 27_000)다. 즉 **도구가 아직 시켜 주지도 않는 일에 대해 "너 늦었다"고 말하고 있었다.**

세 번째로, 첫날의 `completionRateBp`는 기록이 없어 기본값 7_000이다(§3.3). **추정값으로 계산한 결과에 경고색을 쓸 근거가 없다.**

**Decision** — 판정 대신 **세 문장**을 먼저 말한다.

1. **방향은 맞다** — 지금 계획이 목표를 향하고 있다는 것.
2. **지금 수준으로 어디까지 되는가** — 만드는 순서 중 지금 열리는 단계(ADR-060 `SCR-BUILDABLE`).
3. **목표 전체는 언제쯤 되는가** — `feasibleDate` 역산(§3.4). *"이 기간까지는 어렵지만 언제까지는 도달 가능하다"*를 숫자로 말한다.

그리고 SCR-BUILDABLE의 `NEXT` 단계에는 **"기술이 다 차지 않아도 지금 할 수 있는 것"**을 붙인다 — 모자란 축을 다 채운 뒤에 시작하면 남는 게 없다. 만든 만큼 기록으로 남기면 그것이 설명 자료가 된다(`02` SCR-BUILDABLE).

**반대 방향 거짓말도 거짓말이다.** 예산은 계획 레벨(= 자기평가)로 계산하므로(`06` §7.5), 근거가 0인 사람에게도 "목표일 안에 들어와요"가 나올 수 있다 — 2026-10-01 실제 계정이 그랬다(107개 skill 중 83개가 자기평가 2, 근거는 106개가 0). 그래서 기록이 없을 때(`completionRateEstimated`)는 **날짜가 자기평가 기준이라고 말한다.** 안심시켜서 아무것도 안 하게 만드는 것은 빨간 배지와 같은 실패다. 예산을 근거 레벨로 바꾸는 것은 별개 결정이고 여기서 하지 않는다.

그리고 **꾸준함이 그 날짜를 당긴다는 것을 함께 말한다.** 이것은 격려가 아니라 계산이다 — `completionRateBp`는 최근 28일의 `Σactual / Σavail`이고(§3.3), 완료한 날이 늘면 그 비율이 올라가 `effectiveMinutes`가 커지고 `feasibleDate`가 실제로 앞으로 온다. 하루를 한 것이 날짜를 당긴다는 말은 참이다.

이를 위해 넷을 바꾼다.

- **risk는 지금 잴 수 있는 축으로만 센다**(§4.2). 나머지는 `requiredMustLaterMinutes`로 따로 보인다 — 없애는 것이 아니라 "아직 열리지 않은 몫"이다.
- **도달 가능 날짜를 역산한다**(§3.4, `05` §7.9 `feasibleCompletionDate`). 5년 안에 못 닿으면 null이고, 그때는 날짜가 아니라 범위를 줄이는 제안(§4.4)만 말한다.
- **완료율이 추정값이면 그 사실을 함께 준다**(`completionRateEstimated`). 화면은 추정값으로 경고색을 쓰지 않는다.
- **첫 화면에서 risk 배지를 주인공에서 내린다.** 위험도는 replan 권고(ADR-046)의 입력으로 남고, 사용자가 먼저 보는 것은 위 세 문장이다. 자세한 수치는 계획 화면에 그대로 둔다.

**무엇을 하지 않는가**

- **목표일을 자동으로 바꾸지 않는다.** 역산값은 사실이고 선택은 사용자 것이다(`06` §4.4 "자동 적용 금지"와 같은 원칙).
- **위험도를 숨기지 않는다.** 빠듯하다는 사실은 그대로 말한다 — 색과 자리만 바꾼다.
- **없는 날짜를 만들지 않는다.** 5년 안에 안 되면 null이고, null일 때 날짜를 지어내지 않는다.

**Alternatives**

- *목표일을 자동으로 늘린다* — 사용자가 정한 날짜를 도구가 조용히 바꾸는 것이다. 그 날짜에는 도구가 모르는 이유가 있을 수 있다.
- *위험도를 아예 없앤다* — 빠듯한 것은 사실이고, 그 사실이 replan 제안과 ADR-046 권고의 근거다. 없애면 줄이자는 말을 할 근거가 사라진다.
- *DEBUGGING axis cost를 0으로* — coach가 들어오면 되돌려야 하고, 그때 되돌렸다는 사실을 아무도 기억하지 못한다. 설정(§7.6)으로 나누는 쪽이 한 줄로 복구된다.
- *예산에서 later 몫을 빼고 아예 안 보인다* — 나중에 들일 시간이 계획에서 사라져 목표일 판단이 헐거워진다.

**Consequences**

- `requiredMustMinutes`의 뜻이 바뀐다(네 축 → 잴 수 있는 축). AC-03 벡터는 함께 고쳤다.
- **이미 저장된 `plan_progress_snapshot` 행은 고치지 못한다.** 그 날의 planning level을 남겨 두지 않으므로 재계산할 입력이 없다. 그래서 2026-10-01 이전 행은 **네 축 기준**, 그 뒤 행은 **잴 수 있는 축 기준**이고 `05` §13 `risk.trend`에 경계가 하나 생긴다 — 그 지점에서 위험도가 내려간 것은 **사용자가 나아진 것이 아니다.** 지금은 앱에 추세 화면이 없어 보이지 않지만, 만들 때 그 경계를 표시하거나 경계 이전 점을 빼야 한다. 날짜를 코드에 박는 대신 `plan_progress_snapshot`에 계산 기준을 적는 열을 두는 쪽이 낫다(필요해질 때 새 migration).
- coach가 들어와 `DEBUGGING`이 측정 가능해지면 `requiredMust`가 커지고 위험도가 올라간다. 그때 사용자에게 "늘어난 이유"를 말해야 한다 — ADR-061의 전이 설계와 같은 자리에서 다룬다.
- 위험도가 첫 화면에서 내려가므로, 정말 빠듯할 때 사용자가 늦게 알아챌 수 있다. ADR-046의 연속 7일 replan 권고가 그 안전망이다.

---

## ADR-063 주장한 수준에서 막히면 주장을 거둔다

- **Status**: Accepted (2026-10-01)
- **Context**: ADR-062를 실제 계정에 띄워 놓고 확인하다 나왔다.

ADR-062로 첫 화면이 "목표일 안에 들어와요"라고 말하게 됐다. 숫자는 규칙대로 맞았다 — 그런데 그 근거가 **자기평가**였다. 실제 계정은 skill 107개 중 83개가 자기평가 2이고, 근거 레벨은 106개가 0이었다.

예산(`06` §3·§4)은 planning level로 계산하고, planning level은 `self_assessment_active ? max(evidence, selfCap) : evidence`다(`06` §7.5). 자기평가가 꺼지지 않으면 근거가 0이어도 예산이 넉넉하다.

그러면 저절로 맞춰지지 않나 — **올라가는 쪽은 맞춰진다.** 근거가 `selfCap`을 넘으면 `max()`가 근거를 택한다. 내려오는 쪽이 비어 있었다:

| rule_code | 발동 조건 | 근거 0에서 |
|---|---|---|
| `K_DOWN_RECALL_FAIL` | 근거 K ≥ 3 | 불가 |
| `I_DOWN_TRANSFER_FAIL` | 근거 I ≥ 3 | 불가 |
| `D_DOWN_REPEATED_MISS` | 근거 D ≥ 2 + coach(S4) | 불가 |
| `DIAGNOSTIC_FAILED` | 확인 문제를 **풀어야** 한다 | 가능 |

즉 자기평가를 거두는 문이 **확인 문제 하나**였다. 그 문은 화면에 "실력 확인 **(선택)**"으로 붙어 있고, 사용자가 요구한 것은 "아무 생각없이 써도 공부할 수 있게"였다. 선택 항목에 정확성을 의존한 셈이다.

안 맞춰졌을 때의 조합이 문제다. 난이도는 주장한 수준에서 나오고(`06` §5.3 `d = planning I + 1`) 날짜는 낙관적으로 고정된다 — **어려운 문제 + 거짓 안심.** ADR-062가 고친 빨간 배지와 방향만 반대인 같은 실패다. 빨간 배지는 적어도 신호였다.

- **Decision**: `06` §7.3에 `I_DOWN_CLAIM_UNSUPPORTED`를 더한다. `self_assessment_active`이고 `claimCap = min(self_assessed_level, 3) ≥ 1`일 때, 그 skill의 `CHALLENGE_EVALUATED` 중 **purpose ≠ DIAGNOSTIC 이고 difficulty ≤ claimCap**인 가장 최근 2개가 모두 `FAILED`면 **레벨을 바꾸지 않고 `self_assessment_active = false`**로 한다.

세 가지가 이 규칙의 전부다:

1. **레벨을 내리지 않고 주장만 거둔다.** 근거는 0이라 내릴 것이 없다. `DIAGNOSTIC_FAILED`와 같은 이유다(`06` §7.4) — 증거가 아니라 주장이 틀렸다.
2. **주장한 수준에서 막힌 것만 센다.** §5.3이 난이도를 일부러 한 단계 위로 내므로 `claimCap + 1`을 틀리는 것은 설계가 작동하는 중이다. 그것까지 세면 모든 사용자의 자기평가가 첫날 꺼지고 바람직한 난이도 원리와 싸운다.
3. **가장 최근 2개**를 본다. 1회는 컨디션일 수 있다. 사이에 성공이 있으면 둘 중 하나가 성공이므로 발동하지 않아, 주장을 되살리는 별도 규칙이 필요 없다.

- **Alternatives**:
  - **확인 문제에 맡긴다** — 이미 있는 문이고 코드도 안 바뀐다. 다만 선택 항목이다. 안 누르는 사람에게는 영원히 안 맞춰지고, 그게 바로 지금 상태다.
  - **예산을 근거 레벨로 계산한다** — 정확하지만 `06` §7.5·§3·§4를 함께 뒤집는 큰 변경이고, 첫날부터 모든 사용자의 날짜가 멀어진다. 자기평가는 "어디서 시작할지"를 정하는 값으로는 쓸모가 있다. 과하게 고치는 쪽을 택하지 않았다.
  - **실패 1회로 거둔다** — 하루 컨디션으로 주장을 거두면 난이도가 출렁인다.

- **Consequences**:
  - 주장한 수준에서 두 번 막히면 그 skill의 난이도가 내려가고(`planning = 0` → `d = 1`) 다음 과제가 개념 노트가 된다(ADR-057). 사용자가 기대한 "유기적으로 맞춰짐"이 확인 문제 없이도 돈다.
  - 자기평가가 꺼지면 `requiredMust`가 커지고 도달 가능 날짜가 뒤로 간다. **ADR-062 이전이라면 이 변경은 곧 빨간 배지였다.** 그 자리가 "언제면 되는지"로 바뀐 뒤라 날짜가 밀려도 버틸 수 있다 — 순서가 먼저다.
  - `RuleInput`이 자기평가를 받는다. 다른 규칙은 근거만 보므로, 자기평가를 보지 않는 편의 생성자를 남겨 기존 호출부와 벡터를 그대로 둔다.
  - 되살리는 길은 근거뿐이다(§7.2 상승 규칙). 자기평가를 다시 켜는 규칙은 두지 않는다 — 한 번 틀린 주장을 사용자가 다시 말하는 것으로 복구하면 같은 자리로 돌아온다.
  - **화면 문구가 "자기평가"를 지목할 수 없다.** 가이드가 쓰는 `completionRateEstimated`는 "기록이 2주를 넘지 않았다"는 뜻이고(`06` §3.3), 이 규칙이 주장을 거둔 뒤에도 참이다. 그래서 그 줄은 "기록이 아직 적어서 이 날짜는 예상이에요"로 적는다. **날짜가 주장에 기대는지를 정확히 아는 값은 아직 응답에 없다** — 필요해지면 `05` §7.9에 더한다.

---

## ADR-064 난이도가 내려간 이유를 말한다

- **Status**: Accepted (2026-10-01)
- **Context**: ADR-063을 넣고 나서 사용자가 물었다 — *"틀려서 레벨 하락하면 팝업으로 알려주면 되지 않아? 평가가 하락해서 난이도가 하락합니다. 뭐 안내해주면 되지 않을까싶네?"*

맞는 지적이다. ADR-063이 주장을 거두면 `06` §7.5가 planning을 근거로 떨어뜨리고, 그 결과 **다음 과제의 난이도가 내려가고(§5.3) 필요 시간이 늘어 목표일이 멀어진다.** 지금까지 그 두 변화를 사용자에게 아무도 말하지 않았다. 말하지 않으면 "왜 갑자기 쉬워졌지", "왜 날짜가 멀어졌지"가 남는다 — 설명 없는 변화는 도구를 못 믿게 만든다.

용어는 한 가지 고친다. **ADR-063은 레벨을 내리지 않는다.** 주장을 거둘 뿐이고 근거 레벨은 그대로다(보통 0이다). 그래서 "평가가 하락했습니다"는 사실과 다르고, 사용자가 실제로 겪는 변화는 **"난이도가 내려갔다"**다. 화면 문구도 그렇게 쓴다.

- **Decision**: `AttemptView`에 `claimWithdrawnSkills`를 더하고(`05` §10.6), 평가가 끝난 attempt 결과에 **한 줄로** 보인다.

- **왜 모달 팝업이 아닌가**: 문제를 막 틀린 직후에 모달로 "수준이 내려갔습니다"를 띄우는 것은 ADR-062가 걷어낸 빨간 배지와 같은 자리다. 틀린 순간에 도구가 할 일은 판정을 들이미는 것이 아니라 **다음에 할 것을 말하는 것**이다. 결과 화면 안의 한 줄이면 읽는 사람이 자기 속도로 본다.
- **왜 토스트가 아닌가**: 토스트는 4초 뒤 사라진다(`02` §6.7). 이 설명은 "왜 쉬워졌나"에 대한 답이라 나중에 다시 봐도 있어야 한다. 또 토스트는 한 번만 떠야 맞는데, 거둬진 시점을 저장하지 않으므로 "한 번"을 알 수 없다.
- **왜 "방금"이라고 말하지 않는가**: `self_assessment_active`가 꺼진 시점을 저장하지 않는다. 저장하려면 컬럼이 하나 필요하고(migration), 지금 필요한 것은 **지금 왜 이런가**이지 **언제 바뀌었나**가 아니다. 그래서 문구는 "거둬졌습니다"가 아니라 "지금은 기록 기준으로 난이도를 잡아요"다. 정말 일회성 알림이 필요해지면 `user_skill_state.self_assessment_withdrawn_at`를 새 migration으로 더한다.

- **Alternatives**:
  - **`skill_state_change`에 행을 남긴다** — 그 테이블은 `check (from_level <> to_level)`이고 V2는 이미 적용된 migration이다. 레벨 전이표에 레벨이 안 바뀐 행을 넣는 것은 뜻이 다르다.
  - **이번 attempt가 바꾼 레벨 전부를 보고한다**(`skill_state_change.evidence_event_ids`로 조회) — 정확하고 "방금"이라고 말할 수 있다. 다만 하락 규칙 세 개는 근거 3 이상에서만 돌아서 지금 사용자가 겪는 경우가 아니다. 저장소 조회를 더해야 하므로 필요해질 때 한다.

- **Consequences**:
  - `AttemptViewAssembler`가 `UserSkillStateQueryService`를 읽는다 — `ReviewQueryService`·`SkillCatalogQueryService`와 같은 모듈 간 질의 경로다(`03` §2.2).
  - 평가 전 attempt에는 `[]`다. 푸는 중인 화면에 끼면 지금 할 일을 밀어낸다.
  - 주장이 거둬진 skill의 문제를 다시 풀면 성공한 attempt에도 같은 줄이 보인다. 그 상태 설명으로는 여전히 참이다 — 거짓이 아니라 뉴스가 아닐 뿐이다.

---

## ADR-065 말한 수준과 기록을 나란히 보여 준다

- **Status**: Accepted (2026-10-03)
- **Context**: 사용자가 "자기평가 모델 같은 거 이미 만들어져 있는 시스템을 참고할 게 있는지" 찾아 달라고 해서 선행 사례를 조사했다.

**Open Learner Model(OLM)** 연구에 우리가 안 하고 있던 것이 하나 있었다 — 학습자의 자기평가와 **시스템이 가진 모델 사이의 일치/불일치를 보여 주면** 자기 점검과 성취가 개선되고, **특히 낮은 성취자에게 그렇다**([ASEE 2023 tag-enhanced OLM](https://peer.asee.org/42141), [Birmingham 학위논문](https://etheses.bham.ac.uk/8590)). 학습자가 자기 수준을 체계적으로 과대평가한다는 것도 반복 확인된 결과다 — ADR-063의 전제가 맞다는 뜻이다.

SCR-SKILL-DETAIL은 축 표에 `증거 레벨 | 계획용 레벨 | 목표`를 숫자로 보여 주고, 그 아래에 `자기평가 {레벨}`을 따로 한 줄 둔다. **숫자 세 열과 떨어진 한 줄로는 "내가 3이라고 했는데 기록은 0"이 읽히지 않는다.** ADR-063이 주장을 거둘 때도 `최근 기록을 반영해 자기평가는 더 이상 쓰지 않아요`라고만 말하고 **두 숫자를 나란히 놓지 않았다** — ADR-064에서 "한계"로 적어 둔 자리다.

- **Decision**: 자기평가가 있는 skill의 상세 화면에 **한 문장**을 둔다(`skillDetail.calibration`). 세 경우를 구분한다:

| 상태 | 문구 |
|---|---|
| 주장 > 기록, 주장 유효 | 말한 수준은 {self}, 기록은 아직 {evidence}예요. 지금 난이도는 말한 수준을 따라가요 — 한 문제만 풀어도 기록이 따라옵니다. |
| 기록 ≥ 주장 | 기록이 말한 수준({self})을 따라잡았어요. 이제 기록으로 난이도를 잡습니다. |
| 주장이 거둬짐(ADR-063) | 말한 수준({self})에서 막혀서 더 쓰지 않아요. 지금은 기록({evidence})으로 난이도를 잡습니다. |

기록 쪽은 **축 중 가장 높은 값**으로 본다 — 한 축이라도 주장에 닿으면 그 축에서는 §7.5가 기록을 쓴다.

- **판정이 아니다.** 세 문구 모두 **다음에 무엇이 달라지는지**로 끝난다. "당신 평가가 틀렸다"고 말하는 자리가 아니다(ADR-062). 기록이 따라잡은 경우를 함께 말하는 것도 그래서다 — 차이를 보여 주는 것이 목적이고 벌을 주는 것이 아니다.
- **Alternatives**:
  - **숫자만 더 보여 준다**(축 표에 자기평가 열 추가) — 열이 네 개가 되고, OLM 연구가 말하는 "일치/불일치"는 여전히 읽는 사람이 직접 계산해야 한다.
  - **Today 가이드에 올린다** — 가이드는 계획 전체를 말하는 자리이고 이것은 skill 하나의 이야기다. skill 상세가 맞는 자리다.
- **Consequences**: 서버 변경이 없다 — `selfAssessedLevel`·`selfAssessmentActive`·`evidenceLevels`가 이미 `GET /skills/me`에 있다. 기존 `skillDetailSelfInactive` 한 줄은 이 문장이 대신하므로 화면에서 빠진다(문구 자체는 ARB에 남는다).

---

## ADR-066 난이도를 측정된 성공률로 맞춘다 (85% 밴드)

- **Status**: Accepted (2026-10-03)
- **Context**: `06` §5.3은 난이도를 `planning IMPLEMENTATION + 1`로 정한다. 고정 규칙이고 **실제로 몇 개를 맞히는지 보지 않는다.** 그래서 자기평가가 높은 사용자에게는 계속 어려운 문제가, 낮은 사용자에게는 계속 쉬운 문제가 나간다. ADR-063이 그 일부를 "주장 수준에서 2회 실패"라는 **이진 트립와이어**로 막았지만, 그것은 자기평가를 거두는 규칙이고 난이도 자체를 조절하지는 않는다.

Wilson et al.(2019) *The Eighty Five Percent Rule for optimal learning*(Nature Communications)은 정답률 **85%**(오답 15%) 부근에서 학습이 가장 빠르다고 본다. 다 맞히면 무엇을 고칠지 알 수 없고, 다 틀리면 무엇이 통했는지 알 수 없다. **이 수치를 그대로 끌어오지 않는다** — 논문은 이진 분류 과제에서 유도한 값이고 백엔드 만들기 과제에 옮겨 간다는 직접 근거가 없다. 아래 "왜 한 번에 ±1인가"에 그 한계를 적었고, **법칙이 아니라 실측으로 확인할 휴리스틱으로 쓴다**(`BL-CNT-14` 실측 튜닝에서 재검토).

- **Decision**: `DifficultyBandPolicy`(`today.domain`, 순수 규칙)를 더해 `d = planning I + 1` **위에** 측정된 성공률로 ±1을 얹는다. 규칙과 vector는 `06` §5.3, 설정은 `03` §9 `devpilot.planner.difficulty-band`.

- **왜 사용자 전체를 세나(skill별이 아니라)**: skill 하나에 attempt가 5개 쌓이는 데 몇 주가 걸린다 — skill별 표본으로는 규칙이 영원히 발동하지 않는다. 85% 규칙이 재는 것도 "지금 내는 난이도에서 학습자가 얼마나 맞히는가"이므로 학습자 단위가 맞다. skill별 차이는 이미 `planning`이 들고 있다.
- **왜 한 번에 ±1인가**: 논문은 **이진 분류 과제**에서 유도한 값이고 저자 본인이 모든 학습에 그대로 적용되지는 않을 것이라고 적었다. 그래서 법칙이 아니라 **목표 밴드**로 쓴다 — 한 단계씩만 움직이고, 표본이 `min-samples` 미만이면 아예 움직이지 않는다.
- **왜 힌트를 보고 맞힌 것도 성공인가**: 85% 규칙이 재는 것은 "맞혔는가"다. 힌트를 썼는지는 `06` §7.2 상승 규칙이 이미 따로 본다 — 한 신호를 두 곳에서 깎으면 이중 벌이 된다.
- **ADR-063과 겹치지 않는다**: 그 규칙은 `planning`(= `d`의 기준)을 내리고, 밴드는 그 위에서 움직인다. 밴드가 보는 성공률은 **이미 지금 난이도에서 나온 결과**이므로 이중 보정이 아니다. 그리고 ADR-063은 난이도만이 아니라 **예산·목표일의 정직성**까지 고치므로 밴드가 대신할 수 없다 — 조사 요약에서 "흡수한다"고 쓴 것은 과장이었다.
- **Alternatives**:
  - **Elo로 수준 추정을 교체한다**(Pelánek) — 답변 하나를 학습자 vs 문항의 경기로 보고 양쪽을 갱신한다. `selfCap`/`evidence` 이진 구조를 연속값으로 바꾸는 더 나은 설계지만 `06` §7 전체를 다시 쓰는 일이고, **문항 난이도 보정은 학습자가 많아야** 된다(사용자 2명). 밴드로 먼저 보고 부족하면 그때 한다.
  - **BKT**(pyBKT 계열) — 숙달 확률을 답변마다 갱신한다. 파라미터 fitting에 데이터가 필요해 지금은 문헌 기본값만 쓸 수 있고, 그러면 밴드보다 설명하기 어렵다.
  - **FSRS로 복습 간격을 교체한다** — Anki 25.07의 기본 스케줄러이고 SM-2 대비 같은 retention에서 복습 20~30%를 줄인다. 다만 흔히 꼽히는 "low interval hell"은 `suspend-after-failures: 4`로 이미 막혀 있고, 카드 111장·사용자 2명에서 절감의 절대량이 작다. 파라미터 21개 비선형 모델은 vector로 묶기도 화면에서 설명하기도 어려워 "가이드가 먼저"(ADR-062)와 반대 방향이다. `desired_retention`을 제품 손잡이로 원하게 되면 그때 한다 — JVM 쪽은 `FSRS-Kotlin`(v6, 순수 JVM, 스케줄러만)이 후보다.
- **Consequences**:
  - `TaskProposalPolicy`가 `DifficultyBandPolicy`를 받는다. 밴드를 쓰지 않는 무인자 생성자를 남겨 기존 §5.3 vector는 그대로 돈다(표본이 없으면 밴드가 움직이지 않는다).
  - `ChallengeQueryService.recentPracticeSolved`가 새로 생긴다 — planner가 training을 읽는 모듈 간 질의다(`03` §2.2).
  - 성공률이 높은 사용자는 문제가 점점 어려워진다. `maxTaskDifficulty`가 트랙 상한이므로 거기서 멈춘다.
  - **되먹임이 생긴다**: 난이도를 올리면 성공률이 떨어지고, 떨어지면 다시 내린다. 밴드 폭(70~85%)이 그 진동을 받는 자리다. 폭을 좁히면 출렁이므로 설정으로 뺐다.

---

## ADR-067 목표일이 바뀌면 남은 일정을 다시 배치한다

- **Status**: Accepted (2026-10-05)
- **Context**: 사용자가 물었다 — *"12월 31일까지로 했는데 1월 31일까지는 공부를 진행할 수 있을 것 같아. 그럴 경우 전체적인 진도와 학습 내용을 수정할 수 있는 기능이 있나?"*

목표일 변경 자체는 된다(`05` §5.2 `PUT /learning-goal`). 바뀌는 것도 적지 않다 — 예산 horizon이 즉시 새 날짜가 되고, 위험도와 역산 날짜(ADR-062)가 다시 계산되고, `replan_recommended = true`가 된다. 학습 내용은 replan 확장·축소 제안(`06` §4.4)이 다룬다.

**빠진 것은 milestone 날짜다.** `05` §5.2에 명시돼 있다 — *"plan 구조는 바꾸지 않는다."* 12/31 → 1/31로 늘리면 한 달이 비는데 일정표는 12월에 몰려 있고, 고치려면 milestone 9개의 날짜 18개를 손으로 적어야 한다.

처음에는 이것을 표시 문제로 봤다. 진도는 날짜로 정하지 않기 때문이다 — 현재 단계는 `sortOrder`로 고르고(ADR-044가 **날짜로 정하지 않는다**고 명시), 만들 수 있는 단계는 근거 레벨로 본다(ADR-060). **그런데 코드를 보니 표시 문제가 아니었다.** `06` §5.4 `milestoneUrgency`가 현재 단계의 `end`를 쓰고 식에 **위쪽 한계가 없다**:

```text
max(200_000, 1_000_000 - floorDiv(daysLeft x 1_000_000, length))
```

`daysLeft`가 음수면 1_000_000을 넘어 계속 커진다. 남은 단계가 전부 지난 날짜면 그 factor가 포화돼 **우선순위를 가리지 못한다.** 목표일만 늘리고 일정을 그대로 두면 정확히 그 상태가 된다.

- **Decision**: `MilestoneRescheduler`(`plan.domain`, 순수 규칙)를 더해 **남은 milestone만** 새 목표일에 다시 배치하고, replan 미리보기가 **제안으로** 돌려준다(`05` §7.7 `milestoneSchedule`). 규칙과 vector는 `06` §11.5.

세 가지가 설계의 전부다:

1. **새 알고리즘을 만들지 않는다.** 온보딩이 쓰는 `19` §5 배치 알고리즘을 그대로 부른다. 입력만 다르다.
2. **끝난 단계는 건드리지 않는다.** `status != PLANNED`인 것의 날짜를 옮기면 그 기간에 쌓인 기록과 어긋난다.
3. **가중치는 지금 길이다.** 템플릿 `weightBp`를 다시 꺼내지 않는다 — `plan_milestone`에 열이 없고, 사용자가 손으로 늘려 둔 비율이 유지되어야 한다. 지금 길이를 쓰면 **모양을 지키며 창에만 맞추는 비례 재배치**가 된다.

- **저장하지 않는다.** 미리보기는 제안만 주고 확정은 기존 replan 경로를 쓴다 — 새 쓰기 엔드포인트도, idempotency 변경도 없다.
- **목표일을 바꾸지 않아도 제안이 나온다.** 온보딩은 그날부터 배치했고 재배치는 **오늘부터** 본다. 그래서 이것은 "목표일을 바꿀 때만 뜨는 알림"이 아니라 **언제든 누를 수 있는 동작**이고, 화면도 경고가 아니라 버튼으로 둔다.
- **Alternatives**:
  - **날짜를 저장하지 않고 읽을 때 계산한다** — 썩을 일이 없어 가장 깔끔하지만, replan 요청(`MilestoneInput`)이 날짜를 받는 계약이고 *"이 단계는 이때까지"*를 사용자가 못 박는 길을 없앤다. 저장은 유지하고 제안만 주는 쪽이 균형점이다.
  - **`milestoneUrgency`에 위쪽 한계를 더한다** — 포화는 막지만 일정이 틀린 사실은 그대로다. 식은 `06` §5.4 사양 그대로라 바꾸려면 별도 ADR이 필요하고, 재배치가 되면 그 상황 자체가 드물어진다. 지금은 두고 본다.
  - **목표일 변경 시 서버가 바로 적용한다** — 사용자가 모르는 사이 일정이 움직인다. ADR-062의 방향(도구가 정하지 않고 선택을 준다)과 반대다.
- **Consequences**:
  - `ReplanService`가 `MilestoneScheduleSuggester`를 받는다(생성자 인자 한도 때문에 템플릿 조회와 재배치를 묶었다).
  - `minMilestoneDays`는 계획 템플릿 콘텐츠에 있어(`19` §5.1) 학습 트랙으로 템플릿을 찾는다. 트랙이 없으면 제안하지 않는다.
  - 남은 **"여유로 무엇을 할지"는 아직 묻지 않는다.** 같은 범위를 느긋하게(재배치만) vs 더 깊게(확장 제안)는 서로 다른 선택인데 지금 화면은 후자만 권한다. 재배치가 전자를 가능하게 했으니, 둘 중 무엇인지 묻는 것은 다음 작업이다.

---

## ADR-068 수준을 모르는 사람에게 최고 난이도를 내지 않는다

- **Status**: Accepted (2026-10-06)
- **Context**: 사용자가 말했다 — *"나는 실력 선택도 안 했고 실력 문제 풀다가 중간에 포기했거든? 근데 내 실력을 선택할 수 있는 방법이 없네?"*

확인한 상태: skill 107개 중 **자기평가가 적힌 것 0개, 근거가 있는 것 0개**, 남은 진단 제안 5개의 난이도가 **전부 3**이었다.

두 가지가 겹쳐 있었다.

**① 진단 모드는 가장 어려운 문제를 냈다.** `DiagnosticSuggestionService.targetDifficulty`가 진단 모드에서 `null`을 돌려주고, `difficultyRank`가 `null`이면 `-difficulty`로 정렬해 **높은 난이도를 앞세웠다.** 근거는 ADR-058의 *"진단 모드는 `difficulty`가 그대로 레벨이 되므로 가장 높은 것을 고른다"* — 통과하면 최고 레벨을 주려는 의도였다.

그런데 `06` §7.4 `DIAGNOSTIC_FAILED`는 **레벨을 올리지 않는다.** 그래서 결과가 **전부 아니면 전무**다. 수준을 모르는 사람에게 최고 난이도를 내면 대부분 실패하고, 실패는 아무것도 주지 않는다. 사용자는 중간에 그만뒀고 그 뒤 모든 분야가 0에서 시작했다.

**② 수준을 직접 고를 길이 설정 안에만 있었다.** `/settings/self-assessment`는 있고 동작한다(`05` §6.5). 그런데 그 화면으로 가는 링크가 **설정 화면 하나뿐**이었다. 온보딩에서 진단 모드를 고른 사람은 그 화면의 존재를 알 길이 없다 — 진단 화면에도, Today 진단 카드에도, 기술 목록에도 없었다.

`05` §4.1에 *"진단을 하나도 풀지 않으면 4축 0에서 시작한다"*고 적혀 있으니 동작 자체는 의도된 것이다. **의도가 좋은 결과를 내지 않는다는 것이 실사용으로 드러났다.**

- **Decision**: 둘을 함께 고친다.

1. **진단 모드의 목표 난이도를 2로 한다**(`05` §4.2 4단계, `UNKNOWN_LEVEL_DIFFICULTY`). 통과하면 레벨 2에서 시작하고, 더 높다고 생각하면 자기평가로 올린다. `difficultyRank`는 더 이상 `null`을 다루지 않는다.
2. **진단 화면과 Today 진단 카드에 "직접 고르기"를 둔다**(→ SCR-SELF-ASSESSMENT). 진단을 그만두는 사람이 바로 다음 선택을 볼 수 있어야 한다.

- **왜 1이 아니라 2인가**: 1은 경력이 있는 사람에게 질문 한 번을 버리는 셈이다. 2는 통과하면 쓸모 있는 시작점이 되고 실패해도 한 문제로 끝난다. `19` §12.1 기준으로 진단 사다리는 L1·L2·L3가 모두 있어 2는 항상 존재한다.
- **왜 사다리를 만들지 않았나**: "통과하면 다음 난이도를 제안한다"가 더 정확하지만, `05` §4.2 2단계의 제외 규칙이 **목표 난이도와 같은 난이도를 이미 받았으면 제외**하는 구조라 목표가 고정 2면 category당 한 번으로 끝난다. 적응형 사다리는 목표를 근거 레벨에서 끌어와야 하고(§7.5) 제안 서비스가 근거를 읽지 않는다 — 별도 작업이다.
- **Alternatives**:
  - **온보딩에서 자기평가와 진단을 함께 받는다** — `05` §4.1이 둘을 배타로 두고 있다(`runDiagnostic = true`면 `selfAssessments`가 비어 있어야 한다). 진단 전 시작점을 자기평가로 두고 진단 결과가 덮는 설계가 더 낫지만, 온보딩 계약과 `19` §9 전파 규칙을 함께 바꿔야 한다. 위 둘을 써 보고 판단한다.
  - **진단을 필수로 만든다** — 선택이라는 점이 이 도구의 방향(ADR-062: 도구가 정하지 않고 선택을 준다)과 맞다. 필수로 만들면 첫 화면이 시험이 된다.
- **Consequences**:
  - 진단 모드로 온보딩한 기존 사용자의 **남은 제안 난이도가 3에서 2로 내려간다.** 이미 난이도 3을 받은 category는 제외 규칙이 난이도 기준이라 **다시 난이도 2로 제안될 수 있다** — 재는 기회가 늘어나는 쪽이라 그대로 둔다.
  - 통과 시 받는 레벨이 3에서 2로 낮아진다. 그만큼은 자기평가나 실제 기록으로 올린다.
  - `DiagnosticSuggestionIntegrationTest.shouldPickTheHighestDifficultyInDiagnosticMode`를 `shouldStartFromTheMiddleDifficultyInDiagnosticMode`로 바꿨다 — 단정을 약하게 만든 것이 아니라 **기대 동작이 바뀐 것**이다.

## ADR-069 노트는 "어디까지 알아야 하는지"를 함께 보여 준다

- **Status**: Accepted (2026-10-06)
- **Context**: 사용자가 노트 `LESSON.PRACTICAL.REQUIREMENTS.001` U1("만들기 전에 무엇을 만들지 정하기")을 보고 말했다 — *"403, 409, 404 이런 내용이 나오는데 학습 내용만 봐서는 무슨 개념인지 알 수 없어. … 실 업무에서 자주 쓰는 건 사실 500, 404, 403 이 정도잖아. 그러면 저게 중요한지 안 한지도 모르겠고 저걸 외워야 하는지 아닌지도 모르겠어. 저 정도 코드·개념은 알아야 하는지 몰라도 프로젝트 만드는 데 괜찮은지 그런 기준을 모르겠네."*

세 가지가 겹쳐 있었다.

**① 노트가 주제 밖 어휘를 설명 없이 썼다.** U1의 주제는 "되는 경우와 안 되는 경우를 모두 적는다"다. 상태 코드는 결과 칸을 채우려고 쓴 어휘인데, 본문에 그 사실이 적혀 있지 않아 읽는 사람은 그것도 외울 거리로 읽는다.

**② 용어 카드가 없었다.** `TERM.*`는 뜻 한 줄과 출처를 주는 자리다(`19` §11). `403`·`409`·`404`는 카드가 없어 본문 밖에서 뜻을 볼 곳이 없었다.

**③ 기준을 보여 주는 자리가 어디에도 없었다.** "어디까지 알아야 하나"의 답은 **이미 데이터에 있다** — 활성 계획의 `plan_skill_target`에 우선순위와 4축 목표가 있고, `UserSkillState`에 지금 수준이 있다. 그것이 화면에 없었다. SCR-SKILL-DETAIL에만 있고, 노트를 읽는 중에는 볼 수 없었다.

- **Decision**: 셋을 함께 고친다.

1. **용어 카드 두 장을 추가한다** — `TERM.WEB_HTTP.STATUS_CODE`(BASIC: 숫자는 "무슨 일이 생겼는지"를 한 칸으로 말하는 약속), `TERM.WEB_HTTP.NOT_FOUND_VS_FORBIDDEN`(PRACTICAL: 404와 403을 가르는 기준은 "없다"와 "있지만 당신 것이 아니다"). 출처는 RFC 9110.
2. **노트 본문이 주제 밖 어휘를 쓸 때는 그렇다고 적는다.** U1 `explain`에 "상태 코드는 이 단위의 주제가 아니다 … 지금 가져갈 것은 '되는 경우와 안 되는 경우를 모두 적는다' 하나다"를 넣었다.
3. **노트 화면에 기준 한 줄을 띄운다** — `LessonView.requirement`(`05` §21.1·§21.2). "이 기술은 필수이고, 목표는 지식 3(혼자 기본 가능)이에요. 지금은 모름이에요." 목표에 닿았으면 "목표 혼자 기본 가능에 이미 닿았어요."

- **왜 AI가 아닌가**: 사용자가 물은 두 선택지(인터넷을 찾아보라고 할까, AI가 알려줄까)는 **둘 다 ②만 푼다.** 뜻을 모르는 것은 카드로 풀리고, 모르는 채로 남아도 되는지는 AI가 답할 수 없다 — 그 답은 그 사람 계획에 있다. AI에 물으면 매번 달라지고(ADR-047의 재설명이 이미 그 자리를 맡고 있다) 서버 가드도 더 필요하다. **기준은 결정론이어야 한다.**
- **왜 지식 축만 쓰나**: 노트는 가르치는 단계이고 `19` §7.2에서 노트가 올리는 축이 지식이다. 구현·설명 목표는 문제와 러버덕이 맡고, 그 화면에서 보여 준다. 네 축을 다 띄우면 노트 머리가 계기판이 된다.
- **왜 계획에 목표가 없으면 비우나**: 기준을 꾸며 내면 틀린 기준을 외우게 된다. 역할 기본 목표(`role_skill_target`)로 메울 수도 있지만 그건 그 사람 계획이 아니다 — 비우고, 계획에 넣으면 보인다.
- **Alternatives**:
  - **노트마다 "외울 것 / 찾아볼 것"을 콘텐츠에 적는다** — 사람마다 기준이 다르다. 노트는 콘텐츠라 모두에게 같고, 계획은 그 사람 것이다. 콘텐츠에 박으면 목표가 바뀌어도 따라오지 않는다.
  - **상태 코드를 U1에서 빼고 다른 어휘로 쓴다** — 결과 칸을 설명할 더 쉬운 어휘가 없고, 실제 요구사항 표에는 상태 코드가 들어간다. 주제가 아니라고 적는 쪽이 정직하다.
- **Consequences**:
  - `LessonQueryService`가 생성자 매개변수 7개(PMD 상한)에 이미 닿아 있어 `LessonSkillNotes`로 묶었다 — skill catalog·plan·skill state 조회를 한 자리에 모은다.
  - 노트를 열 때마다 계획 목표와 skill 상태를 읽는다. 둘 다 사용자당 한 번의 조회이고 노트 본문은 registry(메모리)라 추가 비용이 작다.
  - `catalogVersion` 69 → 70.
  - 용어 카드의 근거를 RFC 9110 본문으로 확인했다(2026-10-06): §15 머리글 *"The first digit of the status code defines the class of response"*, §15.5.4 *"An origin server that wishes to 'hide' the current existence of a forbidden target resource MAY instead respond with a status code of 404 (Not Found)"*, §15.5.5(404), §15.5.10(409).
  - 남은 것: 다른 노트에도 주제 밖 어휘가 있는지 훑기.

## ADR-070 목표는 그대로 두고, 지금 근거를 만들 수 있는 범위로 판정한다

- **Status**: **Proposed** (2026-10-07) — 구현 전에 소유자 승인이 필요하다. 근거 토론은 `docs/roadmap/` (git 밖).
- **Context**: ADR-061이 *"잴 수 없는 축은 진도를 막지 않는다"*로 **축 단위** 제외를 들여왔다. 그런데 막히는 것은 축만이 아니다. **축 안의 레벨**도 막힌다.

`docs/06` §7.2가 이미 사실로 적어 놓았다 — *"도달 가능 상한(Sprint별, 규칙이 아니라 사실): S3까지는 D = 0, I ≤ 3(I4는 `EVIDENCE_ACCEPTED`가 필요 → S6), E ≤ 3(E4는 variant 답변이 필요 → `REVIEW_VARIANT`가 Later), K·I·E의 5는 difficulty 5 challenge가 필요."* 코드로도 확인했다: `EVIDENCE_ACCEPTED`는 `LearningEventType`에 있고 `SkillLevelRules`가 읽지만 **만드는 곳이 없다.** `variantReady`를 켜는 setter가 main에 하나도 없고 `ReviewService`는 `wasVariant != isVariantReady()`면 409로 거절한다. PRACTICE challenge의 난이도 분포는 `{1:11, 2:32, 3:18, 4:2}`로 **5가 0개**다.

그런데 `MeasurableAxes.forProgress`는 **축 전체만 0으로 내리고 레벨 상한을 모른다.** 그 결과:

- 기본 `JAVA_BACKEND` 트랙 MUST 43개 중 **32개(74%)**가 I≥4 또는 E≥4를 목표로 한다. 입문·연동 트랙은 0개다.
- `PlannerScoring.isComplete`가 그 목표로 `targetReached`를 요구하고, `currentMilestone`은 `isComplete`가 false인 첫 milestone을 고른다 → **1단계 `FOUNDATION_SETUP`의 gate 10개 중 8개가 막혀 첫날부터 1단계에 고정된다.**
- `DailyPlanComposer`는 `currentMilestone`을 `selectCandidates`보다 먼저 정하고, `selectCandidates`는 현재 단계 후보가 **빌 때만** 다음 단계를 합집합에 넣는다 → **다음 단계가 후보에 들어오지 않는다. 점수 비교에 오르지도 못한다.**
- `DeadlineRiskEvaluator`가 도달 불가능한 몫까지 `now`에 넣는다(planning 0에서 2,455분, 9%) → **`feasibleDate`가 영원히 못 끝낼 일에 유한한 날짜를 약속한다.** ADR-062가 세운 "거짓 안심을 주지 않는다"를 어긴다.
- `DashboardProgressAssembler`는 레벨만 비교하고 `lastPracticedAt` 조건을 빠뜨렸다 → **자기평가만 있고 학습 기록이 없으면 Today는 1단계에 남고 대시보드는 완료로 본다.** `docs/05` §13.1은 둘이 같은 단계를 가리켜야 한다고 적는다.

**실사용자가 겪는 것**: 기본 트랙으로 시작하면 "기반 다지기" 8개 skill만 계속 받고 "회원과 인증"으로 영원히 넘어가지 않는다. 주문 생성·조회 성능·배포를 한 번도 제안받지 못한다.

- **Decision**: 저장된 목표를 바꾸지 않고 **판정 범위를 좁힌다.**

**① `evidenceCeiling`** — "지금 제품이 어느 레벨까지 근거를 만들 수 있는가"를 **전역 설정**으로 둔다. skill별 값이 아니다. 현재 값 **`(K4, I3, E3, D0)`**. 설정 키는 `devpilot.skill.evidence-ceiling` (`03` §9). 값이 `SkillAxis`를 전부 덮지 않으면 **기동 실패**다 — `measurable-axes`와 같은 방식이다(§7.6).

**② 현재 판정 목표 = 축별 `min(원래 목표, evidenceCeiling)`**. 조회 때마다 계산한다. `plan_skill_target`은 **바꾸지 않는다.** DB 추가 없다.

**③ 세 결과를 구분한다.** 새 enum 셋을 반드시 만든다는 뜻이 아니다 — 기존 상태와 추가 필드로 표현하고 DTO·문구는 `05`에서 고정한다.

| 결과 | 목표 범위 | 비교 레벨 | 추가 조건 | 쓰는 곳 |
|---|---|---|---|---|
| 계획상 현재 학습 범위 충족 | 현재 | `planningLevel` | **`lastPracticedAt != null`** | Today 현재 milestone · 후보 · 점수 · 대시보드 타임라인 |
| 근거상 현재 관문 충족 | 현재 | `evidenceLevel` | — | `BUILDABLE` |
| 근거상 전체 목표 충족 | **원래 목표** | `evidenceLevel` | — | 전체 목표 별도 표시 |

**④ 여섯 소비자가 쓸 범위**

| 소비자 | 범위 |
|---|---|
| `PlannerSkillInputs` → planner 전체 입력 | 현재 |
| `PlannerScoring.excluded` → 후보 | 현재 |
| `PlannerScoring.isComplete` → `currentMilestone` | 현재 |
| `PlannerScoring.skillGap` → 점수 | 현재 |
| `BuildableStepService` → `BUILDABLE` | 현재 × `evidenceLevel` |
| `DeadlineRiskEvaluator` → 예산·risk·`feasibleDate` | 현재 + 대기 몫을 따로 |
| `DashboardProgressAssembler` → 진도 | **둘 다 보여 준다.** 현재 단계 판정은 Today와 **같은 조건**(`lastPracticedAt` 포함) |

**⑤ 예산 분할.** 축별 목표 `T`, 상한 `C`, 계획 레벨 `P`:

```text
현재 gap      = max(0, min(T, C) − P)
기능 대기 gap = max(0, T − max(P, C))
raw gap 합    = max(0, T − P)              # 원래 값과 같다
```

`requiredMustMinutes`·`requiredShouldMinutes`는 각각 **현재 몫만** 센다. **`requiredMustLaterMinutes`에는 MUST의 대기 몫만** 넣는다(SHOULD를 섞지 않는다). `ratioBp`·risk·snapshot·Today risk·`feasibleCompletionDate`는 전부 현재 범위다. **raw gap은 보존되지만 각각 분으로 올림하면 합이 한 번에 올린 값과 1분 차이 날 수 있다** — 올림 단위·시점은 §4.2 식을 그대로 쓰고 벡터로 고정한다.

**⑥ 재계획은 현재 범위 위로만 제안한다.** `RAISE_TARGET`은 현재 기능 상한 **이내**의 실제 학습 증가만 제안한다. **현재 필요 시간 절감이 0인 축소**, **현재 학습 증가가 0인 복원·상향**은 자동 제안에서 뺀다. 사용자가 직접 원래 목표를 편집하는 권한과 자동 추천은 다르다. 상한 위 목표를 없애서 시간을 절약했다고 표현하지 않는다.

**⑦ 기능이 열리면 상한만 올린다.** 저장된 목표를 건드리지 않고 다음 조회부터 재계산된다. 과거 학습 기록과 이미 획득한 레벨은 고치지 않는다.

- **왜 목표를 낮추지 않는가**: `06` §11.2가 plan 생성 시 `role_skill_target`을 복사하므로 **`role-targets`를 낮춰도 이미 계획을 가진 사용자에게는 반영되지 않는다.** 다음 replan도 이전 plan을 복사하니 안 따라온다. 그리고 S6에 증거 기록이 들어오면 되돌려야 하는데, 낮추면 "원래 어디까지 가려 했는지"가 사라진다. §7.6의 *"판정에서 빼는 것이지 목표를 낮추는 것이 아니다"*를 레벨로 확장하는 것이다.
- **왜 `min(T,C)`를 "목표 완료"라고 쓰지 않는가**: 그러면 **74%의 미완료가 74%의 허위 완료로 바뀐다.** 그래서 ③의 세 결과를 **따로** 표시한다. 전역 상한은 **모든 skill이 그 레벨까지 도달할 수 있다는 보장이 아니다** — skill별 콘텐츠·근거 경로의 공백은 따로 본다.
- **왜 관문용 `usable`을 따로 만들지 않는가**: `DailyPlanComposer`가 `currentMilestone`을 `selectCandidates`보다 **먼저** 정하므로, `isComplete`만 고치면 1단계 충족 시 현재 단계가 이미 2단계다. 이전 단계의 due 복습은 §5.6 예산 경로로 따로 나온다(`PlannerScoring`의 *"REVIEW 과제는 main task 선정과 별개다"*).
- **Alternatives**:
  - **상한을 skill별로 둔다** — 콘텐츠 공백을 더 정확히 담지만, 상한의 뜻이 "제품의 능력"에서 "그 skill의 사정"으로 바뀌어 기능이 열릴 때 107개를 다 손봐야 한다. 전역으로 두고 skill별 공백은 콘텐츠 작업으로 다룬다.
  - **`forProgress`에 `min(target, cap)`을 넣고 기존 판정을 그대로 쓴다** — 가장 작은 변경이지만 ③ 없이는 "완료"가 거짓이 된다. **상한 값이 아니라 결과에 붙이는 이름이 문제다.**
- **Consequences**:
  - **Q16 반영 직후 risk가 낮아지는 것은 학습자가 성장한 결과가 아니다.** `05` §13.1에 ADR-062 전후의 추세 경계 규칙이 이미 있다 — **이번 적용 시점도 같은 방식으로 기록**하고, 추세에서 계산 기준이 다른 점을 학습 개선으로 읽지 않게 한다. 이를 위해 migration이 필요하다고 미리 가정하지 않는다.
  - `05` §7.10에 전역 `evidenceCeiling`과 **단계별 기능 대기 목록**이 생긴다. `gaps[].targets`는 **지금 이미 현재 판정 목표이므로 그 뜻을 유지한다**(`BuildableStepService`가 `forProgress`로 만들어 넣는다). 대기 목록은 **`GAP_LIMIT = 5`와 독립**이어야 한다 — `gaps=[]`·`status=BUILDABLE`이어도 남는다.
  - `05` §7.10의 예시가 D3을 쓰는데 현 코드는 D0으로 판정한다 → **예시를 고친다.**
  - `06` §4.2·§5.2·§5.3·§7.6·§11.4와 `12` AC-01·02·03·09·30의 정수 예시가 바뀔 수 있다. **예시를 조용히 약화하지 않고** 변경 이유와 새 수치를 여기에 적는다.
  - **미결정**: `05` API 필드 모양과 사용자 문구, 전체 목표를 계획 단위로 집계할 때 MUST/SHOULD/LATER·`deferred`의 분모, 기능 대기 목록의 순서와 0개일 때 문구.
  - **이 ADR은 교육 효과를 판정하지 않는다.** 콘텐츠 품질·planner 가중치·복습 간격·Hint Ladder·skill tree 분해·AI 프롬프트·나머지 두 트랙은 미검토다.
