# DevPilot

정한 목표 시점까지 필요한 개발 역량을 확보하도록, 남은 시간·현재 실력·학습 기억·프로젝트 경험을 계속 다시 계산해 **매일 가장 가치 높은 학습 하나**를 안내하는 개인용 개발 학습 코치입니다.

개인 학습용 프로젝트이며, tailnet(Tailscale) 안에서만 동작합니다. 공개 서비스가 아닙니다.

## 구성

| 영역 | 기술 |
|---|---|
| Backend | Java 25, Spring Boot 4.1, PostgreSQL 16, Flyway — modular monolith, 단일 인스턴스 |
| Client | Flutter Web (PWA) — Riverpod, go_router, dio, freezed |
| AI | DeepSeek `deepseek-flash`. 서버 측 출력 가드·예산 가드를 거치며, 계획·복습·레벨 계산은 AI 없이 결정적으로 한다 |
| 운영 | 자체 Linux 서버 + Docker Compose + Caddy, Tailscale HTTPS. GitHub Actions는 이미지 빌드까지 |

설계 원칙은 [`docs/README.md`](docs/README.md) §5에 있습니다. 요약하면 **마감 우선 · 하루 하나 · AI는 답 대신 질문 · 레벨은 증거로만 · AI가 멈춰도 동작**입니다.

## 문서

구현 기준 문서는 [`docs/`](docs/README.md)에 있습니다. 코드와 문서가 다르면 문서를 먼저 고칩니다.

| 목적 | 문서 |
|---|---|
| 전체 지도 | [`docs/README.md`](docs/README.md) |
| 모듈·클래스 이름, 설정값 | [`docs/03-system-architecture.md`](docs/03-system-architecture.md) |
| API 계약 | [`docs/05-api-spec.md`](docs/05-api-spec.md) |
| 학습 엔진 규칙 (test vector 포함) | [`docs/06-learning-engine-rules.md`](docs/06-learning-engine-rules.md) |
| 로컬 개발 환경 | [`docs/18-project-setup-and-local-dev.md`](docs/18-project-setup-and-local-dev.md) |
| 배포·운영 | [`docs/10-deployment-and-operations.md`](docs/10-deployment-and-operations.md) |
| AI 코딩 에이전트 규칙 | [`AGENTS.md`](AGENTS.md), [`START_HERE_FOR_AI_AGENT.md`](START_HERE_FOR_AI_AGENT.md) |

## 개발

```bash
cp .env.example .env          # 값은 저장소 밖에서 가져온다
pwsh infra/scripts/dev-docker-tunnel.ps1   # 서버 Docker를 Testcontainers에 연결 (로컬 Docker가 있으면 생략)
cd backend && ./gradlew check
```

브랜치: `feature/BL-<EPIC>-<nn>-<슬러그>` → `developer` (squash merge) → `main` (merge commit). 태그 `v0.<sprint>.<patch>`를 `main`에 밀면 릴리스 workflow가 이미지를 빌드합니다.

## 비밀값

`.env`, 서버·DB 접속 정보, API 키는 저장소에 넣지 않습니다. 문서에도 서버 주소·계정·이메일 대신 `<server>`, `<tailnet-host>`, `<your-email>` 자리표시자를 씁니다.
