# DevPilot 개발 문서 v2 (구현 기준)

> Status: Accepted (v3) · Last updated: 2026-09-18 (인프라·AI 공급자 결정, v3 학습 루프·날짜 없는 단계 반영)
>
> **v3 (ADR-036·037)**: 학습 루프는 **읽는다(큐레이션 오픈소스) → 만든다(사이드 프로젝트, 기본 "주문 시스템") → 러버덕으로 설명한다(AI는 질문만) → 반복한다(막힌 곳이 복습 카드)** 다. 로드맵은 날짜가 없고 M1(S0~S3, 끝나면 실사용 시작)과 M2(S4~S7)로 묶는다.
>
> DevPilot은 **정한 목표 시점까지 필요한 개발 역량을 확보하도록, 남은 시간·현재 실력·학습 기억·프로젝트 경험을 계속 다시 계산해 매일 가장 가치 높은 학습 하나를 안내하는 개인용 개발 학습 코치**다.
>
> 이 폴더는 원문 문서 세트(`doc/devpilot-development-docs/`)와 검토 결과(`doc/devpilot-review/`)를 합쳐, **추측 없이 구현할 수 있는 수준**으로 다시 쓴 통합본이다. 구현은 이 폴더만 기준으로 한다.

---

## 1. 기술 스택 (2026-09-18 기준)

| 영역 | 기준 | 결정 |
|---|---|---|
| Backend | Java 25 LTS (Eclipse Temurin), Spring Boot 4.1.x, Gradle Kotlin DSL | ADR-005 |
| Architecture | Modular monolith, 단일 인스턴스 | ADR-002 |
| DB | 자체 서버의 공용 PostgreSQL 16 (DB/role `devpilot`), 전용 스키마 `devpilot`, Flyway. Testcontainers `postgres:16` | ADR-001, ADR-014 |
| Auth | `devtoken` 모드(backend가 서명·검증하는 JWT, allowlist 이메일, tailnet 전용) — Supabase Auth는 Later | ADR-023, 20 DEC |
| Client | Flutter Web PWA (Riverpod, go_router, dio, freezed) | ADR-003, ADR-024, ADR-025 |
| AI | DeepSeek `deepseek-flash` (Spring `RestClient`, `/responses` json_schema, 월 USD 3), 비동기 작업 + 서버 가드. 공급자 교체 가능 | ADR-011, ADR-012 |
| Hosting | 자체 Linux 서버(Rocky 9, x86_64, 공용 Docker) + Tailscale HTTPS(`tailscale serve` → Caddy 127.0.0.1:18080). 공개 도메인·OCI 없음 | ADR-006(대체) |
| CI/CD | GitHub Actions(amd64 이미지 빌드 → GHCR), 배포는 `ssh <server> deploy.sh`(수동). 브랜치 `feature/*` → `developer`(squash) → `main` | ADR-016 |

---

## 2. 문서 지도

| 문서 | 내용 | 언제 읽나 |
|---|---|---|
| [01-product-requirements.md](docs/01-product-requirements.md) | 문제, 목표, FR-01~27, NFR-01~10, 범위 | 기능의 의도를 확인할 때 |
| [02-user-scenarios-and-ux.md](docs/02-user-scenarios-and-ux.md) | 화면(SCR-*), 라우트, 흐름, 상태, 문구, 오류 UI | Flutter 작업 |
| [03-system-architecture.md](docs/03-system-architecture.md) | 모듈·의존 규칙, **클래스 이름**, 요청 흐름, 트랜잭션·비동기, 스케줄 작업, **설정값 전체** | 모든 backend 작업 |
| [04-domain-model-and-db.md](docs/04-domain-model-and-db.md) | aggregate, **enum 레지스트리**, 상태 전이, JSON 스키마, 이벤트 payload, 불변식, migration 계획 | 데이터를 다루는 모든 작업 |
| [05-api-spec.md](docs/05-api-spec.md) | 공통 규약, 오류 코드, **endpoint별 DTO·검증·오류** | API·클라이언트 작업 |
| [06-learning-engine-rules.md](docs/06-learning-engine-rules.md) | planner, budget/risk(축소·확장), 복습(교차 학습), skill 갱신, 판정, hint, 러버덕·코드 읽기, verification guard, 계획 버전, 지표 — **test vector 포함** | 도메인 규칙 작업 |
| [07-security-and-privacy.md](docs/07-security-and-privacy.md) | 위협 모델, 인증·인가, 로깅·감사, secret, AI 데이터, 웹 보안, 개인정보 | 보안 영향이 있는 모든 작업 |
| [08-coding-conventions.md](docs/08-coding-conventions.md) | Java/Spring/JPA/Flutter 규칙, 자동 강제 도구, ArchUnit 규칙 | 코드 작성 전 |
| [09-test-and-quality.md](docs/09-test-and-quality.md) | 테스트 전략, fixture, JWT·DB·권한 격리·AI 테스트, E2E 흐름, 품질 게이트 | 테스트 작성 |
| [10-deployment-and-operations.md](docs/10-deployment-and-operations.md) | 서버 준비, Tailscale HTTPS, 컨테이너, 환경변수, CI/CD, 백업, 모니터링, runbook | 배포·운영 |
| [11-development-roadmap.md](docs/11-development-roadmap.md) | 단계 S0~S7(날짜 없음, M1 = S0~S3 / M2 = S4~S7), spike, 구현 분담, stop-loss, 공개 전환 게이트, 작업 지시 템플릿 | 작업 계획 |
| [12-acceptance-criteria.md](docs/12-acceptance-criteria.md) | AC-01~30 (Given/When/Then) | 완료 판단 |
| [13-product-backlog.md](docs/13-product-backlog.md) | BL 항목 전체 (우선순위, 단계, 의존, AC) | 작업 선택 |
| [14-adrs.md](docs/14-adrs.md) | 아키텍처 결정 기록 | 결정 이유 확인·변경 |
| [15-glossary.md](docs/15-glossary.md) | 용어, **한국어 → 영어 식별자 명명 사전** | 이름 짓기 |
| [16-definition-of-ready-done.md](docs/16-definition-of-ready-done.md) | DoR / DoD / PR 체크리스트 | 작업 시작·종료 |
| [17-ai-integration.md](docs/17-ai-integration.md) | AI operation 카탈로그, **출력 스키마**, 파이프라인, 가드, masking, 예산, prompt, evals | AI 작업 |
| [18-project-setup-and-local-dev.md](docs/18-project-setup-and-local-dev.md) | 저장소 구조, Windows 개발환경, backend·Flutter bootstrap, 로컬 실행 | S0, 환경 문제 |
| [19-content-spec.md](docs/19-content-spec.md) | seed 콘텐츠 형식(큐레이션 저장소·reading 포함), 검증 규칙, 작성 가이드 | 콘텐츠 작업 |
| [20-decisions-and-risks.md](docs/20-decisions-and-risks.md) | 적용된 결정 DEC-01~26, 재확인 항목, 리스크 | 결정 변경, 리스크 점검 |
| [database/schema.sql](database/schema.sql) | 전체 스키마 스냅샷 (PostgreSQL에서 실행 검증) | DB 작업 |

### 작업별 필독 경로

| 작업 | 읽는 순서 |
|---|---|
| Backend 기능 | `AGENTS.md` → 03 → 04 → 05(해당 절) → 06(해당 절) → 12(해당 AC) → 09 |
| AI 기능 | 위 경로 + 17 → 07 §AI 데이터 보호 |
| Flutter 화면 | `AGENTS.md` → 02 → 05(해당 절) → 08(Flutter) → 12 |
| 인프라·배포 | 18 → 10 → 07 |
| 콘텐츠(seed) | 19 → 04 §3·§9 → 06 §5·§6 |

---

## 3. `repo-seed/` — 저장소에 그대로 복사할 초기 파일

| 경로 | 내용 |
|---|---|
| `AGENTS.md`, `CLAUDE.md` | 에이전트 규칙 (Claude Code는 `CLAUDE.md`의 `@AGENTS.md` import로 읽음) |
| `START_HERE_FOR_AI_AGENT.md` | S0 시작 지시문 |
| `.gitattributes`, `.editorconfig`, `.gitignore`, `.env.example` | 저장소 기본 설정 |
| `backend/src/main/resources/db/migration/V1~V9` | Flyway migration (`database/schema.sql`과 결과 동일함을 검증) |
| `backend/src/main/resources/prompts/` | AI prompt v1 |
| `backend/` 기타 | Gradle 설정, application.yml, Dockerfile |
| `app/` | Flutter 분석 설정, 로컬 설정 예시, PWA manifest |
| `content/` | seed 콘텐츠 (skill tree, 목표, 계획 템플릿, 복습 카드, challenge, 큐레이션 저장소·reading) + 검증 스크립트 |
| `evals/` | AI eval case |
| `infra/` | compose, Caddyfile, VM·배포·백업 스크립트 |
| `.github/` | CI/CD workflow, Dependabot |

---

## 4. 시작하는 방법

1. `docs/20-decisions-and-risks.md`의 DEC-01~26을 확인한다. 기본값과 다르게 가려면 해당 ADR을 먼저 수정한다.
2. 준비된 것: GitHub 저장소(공개), 서버(Tailscale, Docker, PostgreSQL 16의 `devpilot` DB/role), DeepSeek API 키(선불 잔액). 서버·DB 접속값은 저장소 밖 `DevPilot-ops/`에만 둔다. healthchecks.io 계정과 tailnet HTTPS 활성화는 S0에서 한다(`docs/10-deployment-and-operations.md`).
3. `repo-seed/` 내용을 저장소 루트에 복사한다. 이 문서 폴더의 `docs/`, `database/`는 저장소의 `docs/`, `database/`로 복사한다.
4. AI 코딩 에이전트에게 `START_HERE_FOR_AI_AGENT.md`로 S0을 지시한다.
5. S1부터는 `docs/11-development-roadmap.md`의 작업 지시 템플릿으로 BL 단위로 진행한다. 목표는 **M1(S0~S3)을 최대한 빨리 끝내고 실사용을 시작하는 것**이다. 날짜는 정하지 않는다(ADR-037). S2가 끝나면 AI 없이 Today·Review를 먼저 써도 된다.

---

## 5. 핵심 원칙

1. **Deadline first** — 중간 점검일(없으면 학습 완료 목표일)까지 MUST 역량을 우선한다.
2. **One important thing today** — 하루에 진행 중인 main task는 하나다.
3. **Do not replace thinking** — AI는 답보다 질문과 단계적 hint를 준다. 러버덕에서 AI는 질문만 한다. 첫 코드 리뷰 응답에는 수정 코드가 없다.
4. **Evidence over self-rating** — 레벨은 학습 이벤트 규칙으로만 바뀐다.
5. **Memory is database** — 장기 상태는 대화가 아니라 DB에 있다.
6. **Verified when possible** — `VERIFIED`는 도구 결과나 검수된 근거만 받는다.
7. **Deterministic rules** — 계획·복습·레벨 계산은 정수 연산 규칙이고 AI는 콘텐츠만 만든다.
8. **Privacy by default** — 저장·전송 전에 masking, 원문은 30일만 보관한다.
9. **AI-optional** — AI가 멈추거나 예산을 넘어도 계획·복습·기록은 동작한다.
10. **No IDE reinvention** — IDE를 만들지 않는다. 코드 읽기는 사용자가 로컬에 clone해 자기 IDE로 본다(서버는 코드를 가져오지 않는다).

---

## 6. 문서 변경 규칙

- 구현과 문서가 다르면 코드를 임의로 맞추지 않는다. 문서를 먼저 고치거나 ADR을 추가한다.
- API 변경은 05와 OpenAPI 스냅샷을, DB 변경은 migration과 `schema.sql`을, 규칙 변경은 06과 test vector를 같은 PR에서 함께 바꾼다.
- `doc/devpilot-review/`는 근거 기록일 뿐 구현 기준이 아니다.
