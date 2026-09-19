# 18. Project Setup & Local Development

> Status: Accepted (v2) · Last updated: 2026-09-18 · Related: DEC-02, DEC-03, DEC-04, DEC-08(Later), DEC-09, DEC-10, DEC-12, DEC-18, `03-system-architecture.md`, `04-domain-model-and-db.md`, `10-deployment-and-operations.md`, `14-adrs.md` ADR-030~035, `20-decisions-and-risks.md` DEC-05~23
>
> 이 문서는 **저장소 생성부터 로컬에서 로그인 → `/api/v1/me` 호출이 되는 상태까지**의 절차를 정의한다. 개발 DB·테스트 Docker를 Tailscale로 묶은 것은 **지금 단계의 수단이고 최종 목표는 공개 배포다**(`10` §1.4·§1.5). 버전, 저장소 구조, Windows 11 개발환경, backend/Flutter bootstrap, Gradle 구성, profile 파일, 로컬 실행 순서, S0 완료 조건, 문제 해결을 다룬다. 운영 환경(자체 서버·Tailscale HTTPS, CI/CD, 백업)은 `10-deployment-and-operations.md`가 기준이다.
>
> 2026-09-18 결정(`14-adrs.md` ADR-030·ADR-033, `20-decisions-and-risks.md` DEC-08·DEC-15·DEC-20·DEC-21): 개발 DB는 **서버의 공용 PostgreSQL 16**(Tailscale 경유), 인증은 **devtoken 모드**(`POST /api/v1/dev/token`, 결정 A), 로컬 테스트용 Docker는 **서버 Docker를 SSH 소켓 포워딩으로** 쓴다(Docker Desktop 불필요). 서버 주소·계정·DB 접속값은 저장소 밖 `DevPilot-ops/`(`01-servers.md`, `02-database.md`)에만 있고, 이 문서의 `<server>`, `<user>`, `<server tailnet IP>`, `<your-email>`은 자리표시자다.
>
> 이 문서가 참조하는 시드 파일은 `repo-seed/`에 있다. 저장소를 만들 때 같은 경로로 복사한다.

---

## 1. 버전 표 (2026-09-17 확인)

버전은 2026-09-17에 Maven Central·Gradle Plugin Portal 메타데이터, Spring Initializr 메타데이터, pub.dev API, 각 공식 저장소 릴리스로 확인했다(출처 §9). **표에 없는 버전을 추측으로 쓰지 않는다.** 올릴 때는 Dependabot PR(`10` §7.4)로 올리고 이 표를 함께 고친다.

### 1.1 런타임 · 빌드

| 영역 | 버전 | 고정 위치 | 비고 |
|---|---|---|---|
| JDK | Eclipse Temurin **25** (25.0.4 LTS) | `build.gradle.kts` toolchain, CI `setup-java`, `backend/Dockerfile` `eclipse-temurin:25-jre` | DEC-02. SP-4 실패 시 21로 내림 |
| Gradle | **9.7.1** (wrapper) | `backend/gradle/wrapper/gradle-wrapper.properties` | Initializr 생성값 = Gradle current |
| Spring Boot | **4.1.1** | `libs.versions.toml` | DEC-03. 4.1.x OSS 지원 2027-07-31까지, 4.2.0 GA 2026-11-30 예정 |
| Spring dependency-management plugin | 1.1.7 | `libs.versions.toml` | Initializr 생성값 |
| Boot BOM이 관리하는 주요 라이브러리 | Spring Framework 7.0.9, Spring Security 7.1.1, Hibernate 7.4.5.Final, Jackson 3.1.5 (+Jackson 2 BOM 2.21.5), Flyway 12.4.0, PostgreSQL JDBC 42.7.13, HikariCP 7.0.2, Tomcat 11.0.24, Testcontainers 2.0.5, JUnit Jupiter 6.0.3, Mockito 5.23.0, JSpecify 1.0.1 | **지정하지 않음** (Boot BOM) | `spring-boot-dependencies-4.1.1.pom` 기준 |
| PostgreSQL | **16** | 서버 공용 PostgreSQL 16 컨테이너(개발·운영, `10` §2.4), Testcontainers `postgres:16`, `infra/compose.dev.yml` `postgres:16`(오프라인 대체) | 세 곳의 major를 같게 유지한다. **17 전용 문법·함수를 migration에 쓰지 않는다** (`04` §10, `10` §10-12) |

### 1.2 Backend 추가 라이브러리 · 품질 도구

| 이름 | 버전 | 용도 |
|---|---|---|
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | **3.1.1** | OpenAPI 생성. springdoc 3.x = Boot 4 라인. Initializr(Boot 4.1.1)는 3.1.0을 넣으므로 3.1.1로 올린다 |
| `com.tngtech.archunit:archunit-junit5` | **1.5.0** | 모듈 의존 규칙 (`03` §2.2, `09-test-and-quality.md`) |
| (AI 공급자 SDK 없음) | — | DeepSeek는 Java SDK가 없다. `integration.ai.deepseek.DeepSeekAiProvider`가 Spring Framework `RestClient`(Boot BOM, 추가 의존성 없음)로 `POST /responses`를 호출한다 (`17-ai-integration.md`, `14` ADR-032) |
| `org.jspecify:jspecify` | 1.0.1 (Boot BOM) | null 계약 annotation |
| (HTTP stub 라이브러리 없음) | — | `DeepSeekAiProviderRequestTest`는 spring-test의 `MockRestServiceServer`로 요청·응답을 검증한다. WireMock을 쓰지 않는다 |
| Spotless Gradle plugin | 8.10.2 | 포맷 적용·검사 |
| google-java-format | 1.36.1 (AOSP) | DEC-09 |
| Checkstyle | 14.1.0 | 명명 규칙 |
| PMD | 7.27.0 | 버그 패턴 |
| SpotBugs / SpotBugs Gradle plugin | 4.10.4 / 6.5.11 | 바이트코드 분석 |
| JaCoCo | 0.8.15 | 커버리지 |

**테스트용 HTTP 스텁은 두 가지만 쓴다.** (1) `supabase` 모드 JWT 검증 테스트의 JWKS는 `TestJwksServer`(JDK `com.sun.net.httpserver.HttpServer`, 추가 의존성 없음, `09-test-and-quality.md` §2). devtoken 모드는 JWKS HTTP 조회가 없으므로(앱이 가진 공개키로 `NimbusJwtDecoder.withJwkSource`) 스텁이 필요 없다. (2) `DeepSeekAiProvider` 요청·응답 매핑 테스트는 `MockRestServiceServer`(spring-test, `RestClient.Builder`에 바인딩). WireMock·OkHttp MockWebServer는 쓰지 않는다. 라이브러리를 추가하지 않으므로 SP-4의 "SDK 공존" 확인 항목은 없어졌다(§7.2).

### 1.3 Flutter

| 이름 | 버전 | 비고 |
|---|---|---|
| Flutter | **3.47.4 stable** (Dart 3.13.3) | 2026-09-11 릴리스. `app/.fvmrc`로 고정 |
| FVM | 4.3.1 | Windows는 Chocolatey 설치 |
| `supabase_flutter` | 2.17.2 | **Later** — Supabase Auth 도입 시 추가(DEC-08 Later, `AUTH_MODE=supabase`). S0~S7에는 넣지 않는다 |
| `web` | Flutter SDK가 고정하는 버전 (`flutter pub add web`) | dev 로그인 토큰을 `window.sessionStorage`에 보관 (§6.3, 구현 시 확인) |
| `flutter_riverpod` | 3.4.3 | DEC-10 |
| `go_router` | 18.0.1 | |
| `dio` | 5.11.1 | |
| `freezed` / `freezed_annotation` | 4.0.1 / 3.1.0 | DEC-12 |
| `json_serializable` / `json_annotation` | 6.14.1 / 4.12.0 | |
| `build_runner` | 2.16.1 | |
| `flutter_markdown_plus` | 1.0.12 | `flutter_markdown`은 discontinued, 대체 패키지 |
| `url_launcher` | 6.3.2 | Markdown 링크(https만) |
| `uuid` | 4.6.0 | `Idempotency-Key` 생성 |
| `flutter_lints` | 6.0.0 | |
| `mocktail` | 1.0.5 | |
| `intl` | `any` | `flutter_localizations`가 고정한 버전을 따른다 |

pub 패키지 버전은 `flutter pub add`가 고른 값을 `pubspec.lock`으로 고정한다. 위 표는 2026-09-17 최신값이며 하한 기준이다.

### 1.4 인프라 · CI

| 이름 | 버전 | 비고 |
|---|---|---|
| Caddy | 2.11.4 (`caddy:2.11.4-alpine`) | 공식 이미지 그대로 사용, TLS 없음 (`10` §5.2) |
| Docker Engine | 서버 Docker **29.6.1** (Rocky Linux 9.8, x86_64, 2026-09-18 확인) | 로컬 PC는 SSH 소켓 포워딩으로 이 Docker를 쓴다 (§3.1). 서버 설정은 바꾸지 않는다 (`10` §2.1) |
| Tailscale | 서버 1.98.8 (`serve` 사용 가능), 로컬 PC·폰은 최신 클라이언트 | `10` §2.3 |
| PostgreSQL 클라이언트 (`pg_dump`/`pg_restore`) | `postgres:16` 이미지로 실행 | 서버 backup.sh·복구 리허설 (`10` §8) |
| GitHub-hosted runner | `ubuntu-24.04` (amd64) | runner는 amd64 하나만 쓰고, arm64 이미지는 같은 runner에서 QEMU + buildx로 빌드한다(`10` §7.2). 이미지는 `linux/amd64` + `linux/arm64` 멀티아치다 — 공개 전환 후보인 OCI Ampere A1이 arm64이기 때문이다(`10` §1.4) |
| Actions (major) | `actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/wrapper-validation@v6`, `gradle/actions/setup-gradle@v6`, `subosito/flutter-action@v2`, `docker/setup-qemu-action@v3`, `docker/setup-buildx-action@v3`, `docker/login-action@v4`, `docker/build-push-action@v7`, `gitleaks/gitleaks-action@v3`, `actions/upload-artifact@v7` — 로컬 reusable workflow(`uses: ./.github/workflows/ci.yml`)를 뺀 **`uses:` 23개** | S0에 전부 commit SHA로 고정하고 `ci.yml`의 `continue-on-error: true`를 지운다 (`10` §7.5). Trivy·CodeQL action은 S2 P1(`10` §7.4), Tailscale action은 Later(결정 D) |

---

## 2. 저장소 구조

monorepo 하나에 backend, app, content, evals, infra, docs, CI를 둔다. backend 패키지는 `03` §2.1 모듈 목록과 같다. **빈 패키지·빈 디렉터리는 미리 만들지 않는다** (`03` §2.3). 아래 트리는 최종 형태이며, 표시한 Sprint에 생긴다.

```text
devpilot/
├── AGENTS.md                         # 에이전트 공통 규칙
├── CLAUDE.md                         # "@AGENTS.md" import
├── README.md
├── CHANGELOG.md                      # 릴리스 기록 (10 §9.1)
├── .editorconfig
├── .gitattributes                    # * text=auto eol=lf (§3.2)
├── .gitignore
├── .env.example                      # backend 로컬 환경변수 키 목록 (§5.2)
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                    # main·developer push + PR: security, backend, app, content
│   │   ├── release.yml               # tag v0.<sprint>.<patch> → api 이미지 → GHCR, web.zip → Release (배포는 수동)
│   │   └── ai-eval.yml               # workflow_dispatch만: 실제 모델 eval (DEEPSEEK_API_KEY, 상한 USD 0.5)
│   └── dependabot.yml                # gradle, pub, github-actions (대상 브랜치 developer)
├── database/
│   └── schema.sql                    # 전체 migration 적용 결과 스냅샷 (04 §10)
├── docs/
│   ├── 01-…20-*.md                   # 이 문서 세트 (repo의 docs/ 에 둔다)
│   ├── adr/                          # ADR 파일
│   └── api/openapi.yaml              # OpenAPI 스냅샷 (openApiCheck 기준)
├── backend/
│   ├── Dockerfile                    # build/libs/devpilot-api.jar 복사
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradlew, gradlew.bat, gradle/wrapper/
│   ├── gradle/libs.versions.toml
│   ├── config/
│   │   ├── checkstyle/checkstyle.xml
│   │   ├── pmd/ruleset.xml
│   │   └── spotbugs/exclude.xml
│   └── src/
│       ├── main/java/com/devpilot/
│       │   ├── DevPilotApplication.java
│       │   ├── common/               # config, time, math, logging, error, web, security, idempotency, async, jpa
│       │   ├── user/  account/  goal/  skill/  learning/  plan/  review/  today/
│       │   ├── training/  coach/  evidence/  radar/  onboarding/  dashboard/  content/
│       │   └── integration/ai/       # api, deepseek, fake, disabled, prompt, guard, budget, log, masking
│       ├── main/resources/
│       │   ├── application.yml, application-local.yml, application-prod.yml, application-test.yml
│       │   ├── messages_ko.properties
│       │   ├── db/migration/V1__baseline.sql …   (04 §10)
│       │   └── prompts/<id>/<version>/           (03 §11)
│       ├── test/java/com/devpilot/…  # unit, integration(Testcontainers), architecture(ArchUnit)
│       ├── test/resources/ai-fixtures/<operation>/<case>.json
│       └── evalTest/java/com/devpilot/…          # aiEval 전용 (S4)
├── app/
│   ├── .fvmrc                        # {"flutter": "3.47.4"}
│   ├── pubspec.yaml, pubspec.lock, analysis_options.yaml, l10n.yaml
│   ├── config/local.example.json     # 커밋하는 유일한 config. local.json 은 .gitignore (§6.3)
│   │                                 #   prod.json 은 만들지 않는다 — release.yml이 inline --dart-define 을 쓴다 (10 §7.2)
│   ├── assets/fonts/                 # Pretendard, JetBrains Mono + OFL 라이선스
│   ├── tool/check_identifiers.sh     # 로마자 식별자 검사 (08 §2.2, CI app job)
│   ├── lib/                          # §6.3
│   ├── web/                          # index.html(lang="ko"), manifest.json, icons/
│   └── test/
├── content/                          # seed YAML (19-content-spec.md). 빌드 시 classpath:content/ 로 복사
│   ├── tools/validate_content.py     # CI content job
│   ├── skill-tree/
│   ├── curated-sources.yaml
│   └── …
├── evals/                            # 17-ai-integration.md (S4)
│   ├── README.md
│   ├── cases/<suite>/*.yaml
│   ├── baselines/
│   └── reports/                      # .gitignore
└── infra/
    ├── compose.dev.yml               # 오프라인 대체용 로컬 PostgreSQL 16 (:55432, §5.3)
    ├── compose.prod.yml              # api + caddy (10 §5.2)
    ├── Caddyfile
    ├── scripts/
    │   ├── prepare-server.sh         # 서버 1회 준비 (10 §2.2)
    │   ├── deploy.sh                 # 서버에서 실행: web.zip·이미지 활성화, 롤백 (10 §9.3)
    │   ├── backup.sh                 # pg_dump → /opt/devpilot/backups (10 §8)
    │   ├── pull-backup.ps1           # 로컬 PC: 주 1회 scp pull (10 §8.2)
    │   ├── dev-docker-tunnel.ps1     # 로컬 PC: 서버 Docker SSH 터널 (§3.1)
    │   ├── healthping.sh             # health → healthchecks.io (10 §11.1)
    │   ├── host-check.sh             # 호스트 점검 (10 §11.3, S6)
    │   └── smoke-headers.sh          # 배포 후 헤더 검사 (07 §9.3)
    └── systemd/
        ├── devpilot-backup.service · devpilot-backup.timer          # 19:00 UTC (10 §8.1)
        └── devpilot-healthping.service · devpilot-healthping.timer  # 5분 (10 §11.1)
                                                                     # devpilot-host-check.* 는 S6에 추가 (10 §11.3)
```

규칙:
- `content/`의 원본은 저장소 루트 한 곳이다. `backend/src/main/resources/content/`를 만들지 않는다. Gradle `processResources`가 빌드 결과(`build/resources/main/content/`)로 복사한다 (`04` §9, §4.3).
- 셸 스크립트와 `gradlew`는 git 실행 비트를 가진다. Windows에서는 `git update-index --chmod=+x backend/gradlew infra/scripts/*.sh`로 설정한다.
- 생성 코드(`*.g.dart`, `*.freezed.dart`, `lib/l10n/app_localizations*.dart`)는 커밋하지 않는다. CI가 생성한다 (`10` §7.1).

`.gitignore` 필수 항목:

```gitignore
# secrets / 저장소 밖 운영 정보
.env
DevPilot-ops/
*.dump
app/config/local.json

# backend
backend/.gradle/
backend/build/

# app
app/.fvm/
app/.dart_tool/
app/build/
**/*.g.dart
**/*.freezed.dart
app/lib/l10n/app_localizations*.dart

# evals
evals/reports/

# IDE / OS
.idea/
*.iml
.vscode/
.DS_Store
```

---

## 3. Windows 11 개발환경

### 3.1 설치

관리자 PowerShell에서 실행한다. 설치 후 새 터미널을 연다.

```powershell
winget install --id Git.Git -e
winget install --id EclipseAdoptium.Temurin.25.JDK -e
winget install --id Tailscale.Tailscale -e
winget install --id Docker.DockerCLI -e            # docker CLI만 (Docker Desktop 불필요, 패키지 ID는 구현 시 확인)
winget install --id JetBrains.IntelliJIDEA.Ultimate -e
winget install --id Google.Chrome -e
winget install --id Chocolatey.Chocolatey -e
choco install fvm -y
```

| 도구 | 설정 | 확인 명령 |
|---|---|---|
| Git for Windows | §3.2 | `git --version` |
| Temurin 25 JDK | `JAVA_HOME` = Temurin 25 설치 경로 (winget 설치가 설정함) | `java -version` → `25.0.x` |
| Tailscale | 서버와 같은 tailnet에 로그인. 서버 PostgreSQL(5432)·SSH·`https://<tailnet-host>`는 tailnet 안에서만 닿는다 | `tailscale status`에 서버 노드 표시 |
| OpenSSH 클라이언트 (Windows 내장) | `ssh-keygen -t ed25519`로 키를 만들어 `~/.ssh/`에 두고, 공개키를 서버 `<user>` 계정의 `authorized_keys`에 등록한다(서버 주소·계정은 `DevPilot-ops/01-servers.md`). `<user>`는 서버의 `docker` 그룹에 있어야 한다 | `ssh <user>@<server> docker info` |
| 서버 Docker (SSH 터널) | **Docker Desktop을 쓰지 않는다.** 아래 "서버 Docker 터널" 절차. Testcontainers·`docker` CLI가 터널을 통해 서버 Docker를 쓴다 | 터널이 켜진 상태에서 `docker info` (Server: 29.6.1, x86_64) |
| FVM + Flutter | `fvm install 3.47.4` → `fvm flutter doctor` | `fvm --version` |
| IntelliJ IDEA | §3.3 | |
| Chrome | Flutter web 실행·디버깅 기본 브라우저 | `fvm flutter devices` 에 Chrome 표시 |

**서버 Docker 터널** (2026-09-18 검증 완료, `20` DEC-21):

```powershell
# 터널은 백그라운드 ssh 프로세스다. 끌 때는 -Stop 을 쓴다 (Ctrl+C 로는 꺼지지 않는다)
$env:DEVPILOT_SERVER_SSH = "<user>@<server>"             # 또는 -Server 인자. 값은 DevPilot-ops/01-servers.md
$env:DEVPILOT_SERVER_IP  = "<server tailnet IP>"         # 또는 -ServerIp 인자. TESTCONTAINERS_HOST_OVERRIDE 값
. .\infra\scripts\dev-docker-tunnel.ps1                  # dot-source: 이 세션에 DOCKER_HOST 등을 남긴다
                                                         # = ssh -N -L 2375:/var/run/docker.sock <user>@<server>
.\infra\scripts\dev-docker-tunnel.ps1 -Stop             # 종료
# 스크립트가 아래 두 값을 출력한다. 1회만 사용자 환경변수로 등록해 두면 IntelliJ·터미널이 모두 읽는다
[Environment]::SetEnvironmentVariable("DOCKER_HOST", "tcp://localhost:2375", "User")
[Environment]::SetEnvironmentVariable("TESTCONTAINERS_HOST_OVERRIDE", "<server tailnet IP>", "User")
```

| 항목 | 내용 |
|---|---|
| 스크립트 | `infra/scripts/dev-docker-tunnel.ps1`. 인자 `-Server`(기본 `$env:DEVPILOT_SERVER_SSH`), `-ServerIp`(기본 `$env:DEVPILOT_SERVER_IP`), `-Port`(기본 2375), `-Stop`. 2375가 이미 열려 있으면 새로 띄우지 않고 재사용한다. **Windows PowerShell 5.1로 실행한다**(`powershell.exe`. 파일은 UTF-8 BOM으로 저장되어 있어야 한다) |
| 원리 | SSH가 서버의 `/var/run/docker.sock`을 로컬 `localhost:2375`로 포워딩한다. Testcontainers(docker-java)와 `docker` CLI는 `DOCKER_HOST=tcp://localhost:2375`로 서버 Docker에 컨테이너를 만든다 |
| `TESTCONTAINERS_HOST_OVERRIDE` | 컨테이너의 publish 포트는 **서버**에 열리므로 Testcontainers가 접속할 호스트를 서버의 tailnet IP로 바꾼다. 서버 `tailscale0`이 firewalld `trusted` zone이라 tailnet에서 그 포트에 닿는다. 값은 `tailscale status`에서 서버 노드의 IP(100.x.y.z), 저장소에 적지 않는다 |
| Ryuk·포트 | Testcontainers 기본 설정 그대로. 테스트 동안만 서버에 임시 포트가 열리고 Ryuk이 정리한다. 다른 사용자의 컨테이너와 이름이 겹치지 않도록 Testcontainers 기본 이름(무작위)을 바꾸지 않는다 |
| 오프라인 대체 | 서버에 닿지 않을 때만 로컬 Docker Desktop(선택 설치) + `infra/compose.dev.yml`(`postgres:16`, 포트 **55432**)을 쓴다(§5.3). 이때는 `DOCKER_HOST`·`TESTCONTAINERS_HOST_OVERRIDE`를 비운다 |
| 터널 없이 되는 것 | `./gradlew test`(tag `unit`), `bootRun`(서버 PostgreSQL은 5432로 직접 접속, Docker 불필요), Flutter |

추가 Windows 설정:

```powershell
# 긴 경로 허용 (Flutter pub cache, Gradle 캐시). 관리자 권한, 재부팅 후 적용
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" `
  -Name "LongPathsEnabled" -Value 1 -PropertyType DWORD -Force

# 개발자 모드: FVM(.fvm 심볼릭 링크)과 Flutter plugin 빌드가 심볼릭 링크를 만든다
start ms-settings:developers   # "개발자 모드" 켜기
```

- 저장소 경로에 공백·한글을 넣지 않는다. 예: `D:\work\devpilot`
- Docker Desktop·WSL2를 쓰지 않으므로 `.wslconfig` 설정이 필요 없다.

### 3.2 Git 설정과 줄바꿈

```powershell
git config --global core.autocrlf false
git config --global core.longpaths true
git config --global init.defaultBranch main
```

- `core.autocrlf=false`: 줄바꿈 변환은 `.gitattributes`만 결정한다.
- 저장소 루트 `.gitattributes` (전체 파일):

```gitattributes
# 기본: 모든 텍스트 파일 LF (Linux 컨테이너·CI에서 CRLF 스크립트는 "bad interpreter" 오류)
* text=auto eol=lf

# Windows 전용 스크립트만 CRLF
*.bat  text eol=crlf
*.cmd  text eol=crlf
*.ps1  text eol=crlf

# Binary
*.jar   binary
*.png   binary
*.jpg   binary
*.ico   binary
*.ttf   binary
*.otf   binary
*.woff2 binary
*.dump  binary
```

- Initializr가 만든 `backend/.gitattributes`는 삭제한다(루트 파일이 기준).
- 이미 CRLF로 커밋된 파일이 있으면 `git add --renormalize .` 후 커밋한다.

`.editorconfig` (요지. 기준 파일은 `repo-seed/.editorconfig`):

```editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 2

[*.{java,kts}]
indent_size = 4
max_line_length = 100

[*.dart]
max_line_length = 100

[*.{bat,cmd,ps1}]
end_of_line = crlf

[*.md]
trim_trailing_whitespace = false

[{Caddyfile,Makefile}]
indent_style = tab
```

### 3.3 IntelliJ IDEA

| 항목 | 설정 |
|---|---|
| Plugins | **google-java-format** (Settings → google-java-format Settings → Enable, Code style = **Android Open Source Project (AOSP)**. 플러그인 안내에 따라 `idea64.exe.vmoptions`에 `--add-exports` 줄 추가), **CheckStyle-IDEA** (config = `backend/config/checkstyle/checkstyle.xml`, 버전 14.1.0), **PMD** (ruleset = `backend/config/pmd/ruleset.xml`), **SpotBugs**, **Flutter**(Dart 포함) |
| Project SDK | Temurin 25 |
| Gradle | Settings → Build Tools → Gradle → Gradle JVM = Project SDK, "Build and run using" = Gradle |
| Flutter SDK | `app\.fvm\flutter_sdk` |
| Actions on Save | Reformat code(google-java-format), Optimize imports |
| Spring Boot Run Configuration | Active profiles `local`, Working directory `$PROJECT_DIR$/backend` (§4.4 `.env` import 기준) |
| 테스트 Run Configuration | 환경변수 `DOCKER_HOST`, `TESTCONTAINERS_HOST_OVERRIDE`는 §3.1처럼 사용자 환경변수로 두면 따로 설정할 것이 없다. `integrationTest`는 터널이 켜져 있어야 한다 |

포맷의 최종 기준은 IDE가 아니라 `./gradlew spotlessApply`다.

---

## 4. Backend Bootstrap

### 4.1 Spring Initializr

| 항목 | 값 |
|---|---|
| Project | Gradle - Kotlin |
| Language | Java |
| Spring Boot | **4.1.1** |
| Group / Artifact / Name | `com.devpilot` / `devpilot-api` / `DevPilot` |
| Package name | `com.devpilot` |
| Packaging | Jar |
| Configuration | YAML |
| Java | 25 |
| Dependencies | Spring Web, Validation, Spring Security, OAuth2 Resource Server, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Spring Boot Actuator, Spring Configuration Processor, Testcontainers, SpringDoc OpenAPI |

같은 결과를 명령으로 받는다 (PowerShell, 저장소 루트):

```powershell
curl.exe -L -o backend.zip "https://start.spring.io/starter.zip?type=gradle-project-kotlin&language=java&bootVersion=4.1.1&baseDir=backend&groupId=com.devpilot&artifactId=devpilot-api&name=DevPilot&packageName=com.devpilot&packaging=jar&javaVersion=25&configurationFileFormat=yaml&dependencies=web,validation,security,oauth2-resource-server,data-jpa,postgresql,flyway,actuator,configuration-processor,testcontainers,springdoc-openapi"
Expand-Archive backend.zip -DestinationPath . ; Remove-Item backend.zip
```

생성 후 정리:
0. **wrapper 확인 (S0 첫 단계)**: Initializr zip에는 `gradlew`·`gradlew.bat`·`gradle/wrapper/`가 들어 있다. 저장소에 seed 파일만 있고 wrapper가 없으면(2026-09-18 현재 상태) 여기서 만든다 — `cd backend; gradle wrapper --gradle-version 9.7.1`(로컬 Gradle이 없으면 Initializr zip에서 네 파일을 복사). **wrapper가 생기기 전에는 `./gradlew`를 쓰는 모든 명령과 CI `backend` job이 실패한다**(`10` §7.1). 실행 비트도 함께 준다: `git update-index --chmod=+x backend/gradlew`.
1. `backend/build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`을 `repo-seed/backend/`의 파일로 교체한다. **Initializr가 만든 starter 좌표는 seed 파일에 그대로 들어 있다** — Boot 4 starter 이름(`spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server`, `*-test` starter 등)을 기억으로 바꾸지 않는다.
2. `HELP.md`, `backend/.gitattributes`, `application.properties`(있으면)를 지운다.
3. `src/main/resources/`에 seed의 `application*.yml` 4개를 복사한다.
4. 생성된 `TestcontainersConfiguration`의 이미지 `postgres:latest`를 **`postgres:16`으로 바꾼다** (개발·운영 DB와 같은 major).

Initializr가 생성한 좌표 (2026-09-17, Boot 4.1.1):

| 구분 | 좌표 |
|---|---|
| implementation | `spring-boot-starter-actuator`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-validation`, `spring-boot-starter-webmvc`, `org.flywaydb:flyway-database-postgresql`, `org.springdoc:springdoc-openapi-starter-webmvc-ui` |
| runtimeOnly | `org.postgresql:postgresql` |
| annotationProcessor | `spring-boot-configuration-processor` |
| testImplementation | `spring-boot-starter-actuator-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-flyway-test`, `spring-boot-starter-security-oauth2-resource-server-test`, `spring-boot-starter-security-test`, `spring-boot-starter-validation-test`, `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers`, `org.testcontainers:testcontainers-junit-jupiter`, `org.testcontainers:testcontainers-postgresql` |
| testRuntimeOnly | `org.junit.platform:junit-platform-launcher` |

(`spring-boot-starter-*`의 group은 `org.springframework.boot`)

### 4.2 의존성 (목적별)

| 목적 | 의존성 | 추가 Sprint |
|---|---|---|
| REST API, Problem Details | `spring-boot-starter-webmvc` | S0 |
| 요청 검증 | `spring-boot-starter-validation` | S0 |
| JWT 검증 (devtoken 공개키, `supabase` 모드에서는 JWKS) | `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server` (Nimbus JOSE 포함 — devtoken 서명·`NimbusJwtDecoder.withJwkSource`) | S0 |
| DB 접근 | `spring-boot-starter-data-jpa`, `org.postgresql:postgresql` | S0 |
| Migration | `spring-boot-starter-flyway`, `flyway-database-postgresql` | S0 |
| Health | `spring-boot-starter-actuator` | S0 |
| `@ConfigurationProperties` 메타데이터 | `spring-boot-configuration-processor` | S0 |
| OpenAPI 스냅샷 | `springdoc-openapi-starter-webmvc-ui` 3.1.1 | S0 |
| null 계약 | `org.jspecify:jspecify` (Boot BOM) | S0 |
| AI 공급자 (DeepSeek `POST /responses`) | 추가 의존성 없음 — Spring Framework `RestClient`(`spring-boot-starter-webmvc`가 끌어오는 spring-web) | S3 (`DeepSeekAiProvider`; SP-4 smoke는 완료) |
| 통합 테스트 | `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql` (이미지 `postgres:16`) | S0 |
| 슬라이스/보안 테스트 | `*-test` starter 7종 | S0 |
| 아키텍처 규칙 | `archunit-junit5` 1.5.0 | S0 |
| HTTP stub (DeepSeek provider 테스트) | 추가 의존성 없음 — spring-test `MockRestServiceServer`. JWKS는 의존성 없는 `TestJwksServer` | S3 (`DeepSeekAiProviderRequestTest`) |

**문서에 없는 라이브러리를 추가하지 않는다.** 필요하면 ADR을 먼저 쓴다.

### 4.3 Gradle 구성

전체 파일: `repo-seed/backend/build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`.

| 항목 | 구성 | 이유 |
|---|---|---|
| Toolchain | `java.toolchain.languageVersion = JavaLanguageVersion.of(25)` | 로컬·CI 동일 JDK |
| Version catalog | Boot가 관리하지 않는 라이브러리·도구·plugin만 `libs.versions.toml`. Boot starter는 Initializr 문자열 그대로 | 추측 좌표 방지, BOM과 충돌 방지 |
| 저장소 선언 | `settings.gradle.kts`의 `dependencyResolutionManagement` (`PREFER_SETTINGS`) | 저장소를 한 곳에서 관리 |
| Spotless | `googleJavaFormat("1.36.1").aosp().reflowLongStrings()`, 대상 `src/*/java/**/*.java` | DEC-09 (4칸, 100열) |
| Checkstyle | `config/checkstyle/checkstyle.xml`, `maxWarnings = 0` | 명명 규칙 전용. 규칙 내용은 `09-test-and-quality.md` |
| PMD | `config/pmd/ruleset.xml`, 기본 ruleSets 비움 | |
| SpotBugs | `effort = MAX`, `reportLevel = MEDIUM`, `spotbugsMain`만 실행, HTML 리포트 | test 코드는 제외 |
| JaCoCo | `test` 후 리포트, `jacocoTestCoverageVerification`을 `check`에 연결 (`com.devpilot.*.domain` LINE 0.80) | 수치는 `09-test-and-quality.md`가 우선 |
| content 복사 | `processResources { from(file("../content")) { into("content"); include("**/*.yaml", "**/*.yml") } }` | `devpilot.content.location: classpath:content/` (`03` §9) |
| jar 이름 | `tasks.bootJar { archiveFileName = "devpilot-api.jar" }`, `tasks.jar { enabled = false }` | `backend/Dockerfile`이 `build/libs/devpilot-api.jar`를 복사한다. plain jar(`*-plain.jar`)가 있으면 이름 매칭이 흔들린다 |
| bootRun | `workingDir = projectDir`, `args("--spring.profiles.active=local")` | `.env` import 경로 기준 (§4.4) |
| Mockito agent | `mockitoAgent` configuration → `-javaagent:` | JDK 21+ dynamic agent 경고 제거 |
| `evalTest` source set | `src/evalTest/java`. classpath에 **main output만** 포함(test output 제외), test 의존성 configuration만 상속 | 실제 모델 eval은 일반 테스트와 분리 (`09-test-and-quality.md` §3.2) |
| `aiEval` task | `src/evalTest/java` 실행(main에만 의존). `-PevalSuite`(필수), `-PevalCase`, `-PevalRepeat`(3), `-PevalModel`(기본 `deepseek-flash`), `-PevalMaxCostUsd`(**0.5**, 결정 E), `-PevalUpdateBaseline`을 `devpilot.eval.*` system property로 전달. `DEEPSEEK_API_KEY` 없으면 실패, 항상 재실행 | 옵션 의미는 `evals/README.md`. CI는 `ai-eval.yml`(`10` §7.3). prod와 같은 선불 잔액을 쓴다 |
| 테스트 태그 | `unit` 규칙·ArchUnit·web slice → `test` / `integration` Testcontainers·E2E·스냅샷 → `integrationTest` (`shouldRunAfter(test)`) | `09-test-and-quality.md` §3.2. 태그 없는 테스트는 실행되지 않는다 |
| OpenAPI 스냅샷 | `integrationTest`의 `OpenApiSnapshotTest`(tag `integration`)가 `build/openapi/openapi.yaml` 생성 → `openApiCheck`가 `../docs/api/openapi.yaml`과 비교(`check`에 연결) → 다르면 `./gradlew openApiUpdate` 후 커밋 | DEC-12 |

OpenAPI 스냅샷 테스트 (S0에 작성):

```java
@IntegrationTest // @Tag("integration") + @SpringBootTest + @ActiveProfiles("test") + Testcontainers 등 (09 §3.3)
@AutoConfigureMockMvc
class OpenApiSnapshotTest {

    private static final UUID OWNER_SUB = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired private MockMvc mockMvc;

    @Test
    void writesOpenApiYaml() throws Exception {
        String yaml =
                mockMvc.perform(
                                get("/v3/api-docs.yaml")
                                        .with(jwt().jwt(token -> token
                                                .subject(OWNER_SUB.toString())
                                                .claim("email", "owner@devpilot.test"))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        Path output = Path.of(System.getProperty("devpilot.openapi.output"));
        Files.createDirectories(output.getParent());
        Files.writeString(output, yaml, StandardCharsets.UTF_8);
    }
}
```

- `application-test.yml`에서 `springdoc.api-docs.enabled: true`(기본 `application.yml`은 false)와 `writer-with-order-by-keys: true`를 켠다. 출력 순서가 고정되어야 스냅샷 비교가 안정적이다.
- 운영(`prod`)에서는 api-docs와 Swagger UI가 꺼져 있다. 인증 제외 경로 `/v3/api-docs/**`는 `local` profile에서만 연다 (`03` §4.1). test profile에서는 `jwt()`로 인증해서 읽는다.
- import 경로는 Boot 4의 test 모듈 구조를 따른다. IDE 자동 import로 확인하고 추측하지 않는다.

주요 명령:

| 명령 | 내용 | 서버 Docker 터널 필요 (§3.1) |
|---|---|---|
| `./gradlew spotlessApply` | 포맷 적용 | 아니오 |
| `./gradlew test` | tag `unit` (규칙·ArchUnit·web slice) | 아니오 |
| `./gradlew integrationTest` | tag `integration` (Testcontainers `postgres:16`·E2E·OpenAPI 스냅샷) | **예** |
| `./gradlew check` | spotlessCheck, checkstyle, pmd, spotbugsMain, test, integrationTest, jacoco verification, openApiCheck | **예** |
| `./gradlew bootRun` | local profile 실행 (서버 PostgreSQL에 5432로 직접 접속) | 아니오 (Tailscale 연결과 `.env`만 필요) |
| `./gradlew bootJar` | `build/libs/devpilot-api.jar` | 아니오 |
| `./gradlew openApiUpdate` | 스냅샷 갱신 | 예 |
| `./gradlew aiEval -PevalSuite=coach-review -PevalRepeat=3` | 실제 모델 eval (비용, `DEEPSEEK_API_KEY`) | 아니오 |

PowerShell에서는 `./gradlew`가 `gradlew.bat`을 실행한다.

### 4.4 Profile 설정 파일

전체 파일: `repo-seed/backend/src/main/resources/application*.yml`. property 이름은 Spring Boot 4.1 Application Properties 부록으로 확인했다(2026-09-17). Boot 3에서 쓰던 `server.error.include-stacktrace`는 Boot 4에서 **`spring.web.error.include-stacktrace`**다.

| Profile | DB | 인증 | AI | 용도 |
|---|---|---|---|---|
| `local` | **서버 PostgreSQL 16** (`DATABASE_URL` 필수, 기본값 없음. Tailscale 경유, DB `devpilot`) | devtoken (`POST /api/v1/dev/token`, `DEVPILOT_AUTH_MODE` 기본 `devtoken`) | `fake` (실제 호출 시 `.env`에 `deepseek` + `DEEPSEEK_API_KEY`) | 개발자 PC |
| `test` | Testcontainers `postgres:16` (`@ServiceConnection`) | 테스트 JWKS (`auth-mode: supabase` + `TestJwksConfig`/`TestJwksServer`, `09` §6.2). devtoken endpoint 테스트는 `@SpringBootTest(properties = "devpilot.security.auth-mode=devtoken")` | `fake` | `./gradlew check`, CI |
| `prod` | 서버 PostgreSQL 16 (`host.docker.internal:5432`, `10` §2.4) | devtoken (`DEVPILOT_DEV_JWT_KEY` 필수, 결정 A) | `deepseek` (`DEEPSEEK_API_KEY`) | 서버 `api` 컨테이너 |

| 파일 | 내용 요약 | 핵심 property |
|---|---|---|
| `application.yml` | 모든 profile 공통. `devpilot.*` 블록은 `03` §9 기준(2026-09-18 판: `security.auth-mode`·`security.devtoken.*`, `ai.provider=deepseek`, `ai.deepseek.*`, `ai.pricing.peak-multiplier`, `ai.min-balance-usd`, `ai.balance-check-cron`) + `devpilot.web.app-base-url: ${APP_BASE_URL}`(캘린더 피드 URL·devtoken issuer, 기본값 없음) | `spring.jackson.deserialization.fail-on-unknown-properties=true`, `fail-on-null-for-primitives=true`, `spring.threads.virtual.enabled=true`, `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`, `hibernate.default_schema=devpilot`, `hibernate.jdbc.time_zone=UTC`, `spring.flyway.schemas/default-schema=devpilot`, `create-schemas=true`, `clean-disabled=true`, `spring.datasource.hikari.maximum-pool-size=5`, `devpilot.security.auth-mode=${DEVPILOT_AUTH_MODE:devtoken}` + `devtoken.{issuer=${APP_BASE_URL}/dev, private-key-pem=${DEVPILOT_DEV_JWT_KEY:}, token-ttl=720h}`, `spring.security.oauth2.resourceserver.jwt.*`는 `supabase` 모드에서만 쓰고 `${SUPABASE_URL:}` 기본값을 비워 기동을 막지 않는다, `spring.mvc.problemdetails.enabled=true`, `spring.web.error.include-stacktrace=never`, `server.shutdown=graceful`, `server.forward-headers-strategy=framework`, actuator `health`만 노출·`show-details=never`·`probes.enabled=true`, springdoc 비활성 |
| `application-local.yml` | 개발자 PC | `spring.config.import=optional:file:../.env[.properties]`, datasource `${DATABASE_URL}` / `${DATABASE_USERNAME}` / `${DATABASE_PASSWORD}` — **기본값 없음**(`.env` 없이는 기동 실패, `localhost:5432` 기본값을 두지 않는다: 서버 Docker 터널을 쓰는 PC에서 로컬 5432를 가정하면 안 된다), springdoc api-docs·Swagger UI 활성, CORS `http://localhost:5173`, `app-base-url` 기본 `http://localhost:5173`, AI `fake`, `com.devpilot` DEBUG 로그 |
| `application-test.yml` | `./gradlew test` | datasource 없음(Testcontainers `@ServiceConnection`), `devpilot.security.auth-mode: supabase`, JWT issuer/JWKS는 파일에 두지 않고 `TestJwksConfig`가 `DynamicPropertyRegistrar`로 `SUPABASE_URL`(로컬 `TestJwksServer`)을 주입(`09` §6.2), CORS 없음(`07` §9.1), api-docs 활성(스냅샷)·Swagger UI 비활성, allowlist `owner@devpilot.test,invited@devpilot.test`, 고정 log-hash-key·`app-base-url`, AI `fake`, 월 예산 고정 `25`(테스트 벡터 기준; 운영 기본은 3), 로그 WARN |
| `application-prod.yml` | 서버 `api` 컨테이너 | datasource·`DEVPILOT_DEV_JWT_KEY`·`DEVPILOT_ALLOWED_EMAILS`·`DEVPILOT_LOG_HASH_KEY`·`APP_BASE_URL` 기본값 없음(없으면 기동 실패), `logging.structured.format.console=ecs`(JSON), springdoc 비활성, CORS 없음, AI 기본 `deepseek`(S3 전에는 `api.env`에서 `disabled`) |

규칙:
- **local·prod가 실사용 시작(M1 완료 = S3 완료) 전까지 같은 DB `devpilot`을 쓴다** (`10` §1.1). 로컬에서 스키마를 초기화하거나 데이터를 지울 때는 그 사실을 기억한다. 실사용 시작 전(늦어도 S3 안)에 로컬용 `devpilot_dev`로 분리한다(`10` §2.4).
- **Flyway 스키마 생성**: `spring.flyway.create-schemas=true`이므로 Flyway가 `devpilot` 스키마를 먼저 만들고 그 안에 `flyway_schema_history`를 둔다. 따라서 `V1__baseline.sql`의 스키마 생성문은 `create schema if not exists devpilot;`로 쓴다 (`database/schema.sql` 첫 줄과 동일).
- **테스트 스케줄러**: property로 끌 수 없다. `@EnableScheduling`을 붙인 설정 클래스에 `@Profile("!test")`를 붙인다 (`03` §10 test profile "스케줄러 비활성").
- **`.env`는 `local` profile에서만 읽는다.** 그래서 `SPRING_PROFILES_ACTIVE`는 `.env`에 두지 않고 `bootRun` 인자(§4.3)나 IDE Run Configuration으로 준다. `DEEPSEEK_API_KEY`는 Spring property(`devpilot.ai.deepseek.api-key`)로 읽으므로 `.env`에 둬도 된다(OS 환경변수가 있으면 그것이 우선).
- **devtoken 서명 키**: `DEVPILOT_DEV_JWT_KEY`가 비면 기동 시 EC P-256 키를 생성하므로 backend를 재기동하면 발급했던 토큰이 모두 무효가 된다(재로그인). 매번 재로그인이 번거로우면 `openssl ecparam -genkey -name prime256v1 -noout | openssl pkcs8 -topk8 -nocrypt`로 만든 PEM을 `\n`으로 이어 한 줄로 `.env`에 넣는다(`10` §5.5와 같은 형식).
- `test` profile 파일은 seed 경로대로 `src/main/resources`에 둔다. 운영 컨테이너는 `SPRING_PROFILES_ACTIVE=prod`로 고정되어 있어 활성화되지 않는다.

---

## 5. 로컬 실행 순서

### 5.1 구성

```text
[Chrome: Flutter web :5173, AUTH_MODE=dev]
          │ POST /api/v1/dev/token {email}  →  {accessToken, expiresAt}   (이메일 1칸 로그인 화면)
          │ Authorization: Bearer <JWT>  (이후 모든 /api/v1/* 요청)
          ▼
[Spring Boot :8080, profile=local, DEVPILOT_AUTH_MODE=devtoken]  ── 앱이 가진 EC P-256 공개키로 JWT 검증 (JWKS 조회 없음)
          │ JDBC jdbc:postgresql://<server>:5432/devpilot  (Tailscale, TLS 없음)
          ▼
[서버 공용 PostgreSQL 16 컨테이너 (DB devpilot, role devpilot)]        ← prod의 api 컨테이너도 같은 DB (S2 전까지)

[./gradlew integrationTest] ──DOCKER_HOST=tcp://localhost:2375──▶ [ssh -L 터널] ──▶ 서버 Docker ──▶ Testcontainers postgres:16 (임시)
                            ──TESTCONTAINERS_HOST_OVERRIDE=<server tailnet IP>:<random port>──▶ 그 컨테이너
```

인증(devtoken)·데이터(서버 PostgreSQL)·테스트 컨테이너(서버 Docker) 모두 Tailscale 안에서 끝난다. 외부 SaaS(Supabase 등)는 없다. Docker Desktop은 오프라인 대체(§3.1)일 때만 쓴다.

### 5.2 최초 1회 준비

1. Tailscale 로그인(§3.1) → `tailscale status`에 서버 노드가 보이는지 확인 → SSH 키를 `~/.ssh/`에 만들고 서버 `<user>`에 등록 → `ssh <user>@<server> docker info` 성공. 서버 주소·계정·DB 접속값은 `DevPilot-ops/01-servers.md`·`02-database.md`(저장소 밖, `.gitignore`에 `DevPilot-ops/`).
2. 저장소 루트에서 `.env.example`을 `.env`로 복사하고 값을 채운다:

```dotenv
# DevPilot backend local environment (.env 는 커밋하지 않는다). 기준: docs/10 §6, docs/18 §5
# SPRING_PROFILES_ACTIVE 는 두지 않는다 (§4.4)
# DB: 서버 공용 PostgreSQL 16 (Tailscale 경유). 값은 DevPilot-ops/02-database.md. 기본값이 없으므로 비우면 기동 실패
DATABASE_URL=jdbc:postgresql://<server>:5432/devpilot
DATABASE_USERNAME=devpilot
DATABASE_PASSWORD=<DevPilot-ops/02-database.md>
# 인증 (결정 A). 비우면 기동마다 새 키 → 재로그인. 고정하려면 PEM을 \n 으로 이어 한 줄로 (§4.4)
DEVPILOT_AUTH_MODE=devtoken
DEVPILOT_DEV_JWT_KEY=
DEVPILOT_ALLOWED_EMAILS=<your-email>
DEVPILOT_ALLOWED_SUBJECTS=
# 64 hex chars (openssl rand -hex 32), prod 필수. local은 비워도 된다
DEVPILOT_LOG_HASH_KEY=
# AI: local 기본 fake. 실제 호출이 필요할 때만 deepseek + 키 (§5.3-6)
DEVPILOT_AI_PROVIDER=fake
DEVPILOT_AI_MODEL=deepseek-flash
DEVPILOT_AI_MONTHLY_BUDGET_USD=3
DEEPSEEK_API_KEY=
CORS_ALLOWED_ORIGINS=http://localhost:5173
APP_BASE_URL=http://localhost:5173
```

   `.env`는 `.properties` 형식으로 읽힌다. 값에 따옴표를 쓰지 않는다.
3. 서버 Docker 터널을 켜고(§3.1 터미널 A) `DOCKER_HOST`·`TESTCONTAINERS_HOST_OVERRIDE`를 사용자 환경변수로 등록한 뒤 새 터미널에서 `cd backend; ./gradlew check`가 통과하는지 본다(Testcontainers가 서버 Docker에 `postgres:16`을 띄운다).
4. `cd backend; ./gradlew bootRun` → `curl.exe -s http://localhost:8080/actuator/health` → `{"status":"UP"}`. 첫 기동에서 Flyway가 서버 DB `devpilot`에 migration을 적용한다(이미 적용돼 있으면 검증만).
5. 토큰 발급 확인:
   ```powershell
   curl.exe -s -X POST http://localhost:8080/api/v1/dev/token -H "Content-Type: application/json" -d "{\"email\":\"<your-email>\"}"
   # → {"accessToken":"eyJ...","expiresAt":"..."}   (allowlist 밖 이메일이면 403 USER_NOT_ALLOWED)
   curl.exe -s http://localhost:8080/api/v1/me -H "Authorization: Bearer <accessToken>"
   ```
6. `app/config/local.example.json`을 `app/config/local.json`으로 복사한다(`AUTH_MODE=dev`, `API_BASE_URL=http://localhost:8080`, §6.3). `local.json`은 `.gitignore` 대상이다. Supabase URL·key는 없다.

### 5.3 매일 실행

```powershell
# 0) Tailscale 연결 확인 (서버 PostgreSQL·SSH는 tailnet 안에서만 닿는다)
tailscale status | Select-String "<server>"

# 1) 서버 Docker 터널 — 통합 테스트(./gradlew check, integrationTest)를 돌릴 때만. 백그라운드 ssh로 뜬다
. .\infra\scripts\dev-docker-tunnel.ps1            # 종료는 -Stop. DOCKER_HOST 등은 §5.2-3에서 등록해 둔 값

# 2) Backend (새 터미널)
cd backend
./gradlew bootRun                                   # profile=local, ../.env import, 서버 PostgreSQL 16
curl.exe -s http://localhost:8080/actuator/health   # {"status":"UP"}

# 3) Flutter web (새 터미널)
cd app
fvm flutter run -d chrome --web-port 5173 --dart-define-from-file=config/local.json   # AUTH_MODE=dev
```

4. 브라우저 로그인 화면에 allowlist 이메일을 입력 → 앱이 `POST /api/v1/dev/token`으로 토큰을 받아 메모리와 `sessionStorage`에 두고 `GET /api/v1/me` 결과를 표시하면 정상이다. backend를 재시작했는데 `DEVPILOT_DEV_JWT_KEY`가 비어 있으면 401이 나므로 다시 로그인한다(§4.4).
5. API 문서(local만): `http://localhost:8080/swagger-ui/index.html`
6. 실제 AI 호출이 필요할 때만 `.env`에 `DEVPILOT_AI_PROVIDER=deepseek`, `DEEPSEEK_API_KEY=sk-…`를 넣고 backend를 재시작한다(Spring property로 읽으므로 `.env`로 충분). 키는 prod·eval과 같은 DeepSeek 계정의 선불 잔액을 쓴다(결정 E) — 로컬 호출도 `DEVPILOT_AI_MONTHLY_BUDGET_USD=3` 가드 안에서 한다. 잔액이 없으면 402 → `aiStatus=BALANCE_EXHAUSTED`(§8).

DB 초기화(개발 DB): 서버 DB `devpilot`의 `devpilot` 스키마를 지우고 backend를 재기동하면 Flyway가 전체 migration을 다시 적용한다. DB 클라이언트 또는 `ssh <user>@<server> docker exec -i <pg-container> psql -U devpilot -d devpilot -c "drop schema devpilot cascade;"` (`DevPilot-ops/02-database.md`). **실사용 시작(M1 완료) 전까지 local과 prod가 같은 DB를 쓰므로(§4.4) 운영 데이터가 생긴 뒤에는 `devpilot_dev`에서만 한다.**

오프라인 대체(서버에 닿지 않을 때만): Docker Desktop 실행 → `docker compose -f infra/compose.dev.yml up -d`(`postgres:16`, **55432**) → `.env`의 `DATABASE_URL=jdbc:postgresql://localhost:55432/devpilot`, `DATABASE_USERNAME/PASSWORD=devpilot` → `DOCKER_HOST`·`TESTCONTAINERS_HOST_OVERRIDE`를 비운다. 초기화는 `down -v` → `up -d`.

---

## 6. Flutter Bootstrap

### 6.1 생성

저장소 루트에서:

```powershell
fvm install 3.47.4
fvm spawn 3.47.4 create --org com.devpilot --project-name devpilot_app --platforms web app
cd app
fvm use 3.47.4 --force      # app/.fvmrc = {"flutter": "3.47.4"}, app/.fvm/ 생성 (.gitignore)
fvm flutter --version       # Flutter 3.47.4 / Dart 3.13.3
```

- 플랫폼은 `web`만 만든다. Android는 DEC-18에 따라 Later이며, 그때 `fvm flutter create --platforms android .`로 추가한다.
- 이후 모든 Flutter 명령은 `fvm flutter …`, `fvm dart …`로 실행한다. CI는 `.fvmrc`의 버전을 읽어 같은 버전을 설치한다 (`10` §7.1).

### 6.2 패키지 (목적별)

```powershell
fvm flutter pub add flutter_riverpod go_router dio freezed_annotation json_annotation "intl:any" flutter_markdown_plus url_launcher uuid web
fvm flutter pub add flutter_localizations --sdk=flutter
fvm flutter pub add dev:build_runner dev:freezed dev:json_serializable dev:mocktail
```

| 목적 | 패키지 | 결정 |
|---|---|---|
| 인증 | (패키지 없음) `AUTH_MODE=dev`: 이메일 입력 화면 → `POST /api/v1/dev/token` → 토큰을 메모리 + `sessionStorage`(`package:web`)에 보관, 만료(`expiresAt`, 720h) 전 재로그인 없음 | 결정 A. `supabase_flutter`(GitHub OAuth PKCE)는 Supabase Auth 도입 시 `AUTH_MODE=supabase`로 추가(Later, BL 신설) |
| 상태관리 | `flutter_riverpod` | DEC-10. Riverpod code generation은 쓰지 않는다 (Notifier 수기 작성) |
| 라우팅 | `go_router` | 인증 redirect, 웹 URL |
| HTTP | `dio` | interceptor: `Authorization`, `Idempotency-Key`(POST), `X-Trace-Id`, Problem Details 파싱 |
| API 모델 | `freezed_annotation`, `json_annotation` / dev `freezed`, `json_serializable`, `build_runner` | DEC-12: `docs/api/openapi.yaml` 기준 수기 모델. 알 수 없는 enum 값은 `unknown` |
| i18n | `flutter_localizations`, `intl` | MVP는 한국어만. 사용자 노출 문자열은 ARB |
| Markdown | `flutter_markdown_plus`, `url_launcher` | AI 응답 표시. raw HTML 비활성, https 링크만 연다 |
| ID | `uuid` | 버튼 클릭 1회당 `Idempotency-Key` 1개 (`03` §5.4) |
| Lint | `flutter_lints` (create 기본 포함) | §6.4 |
| Test | `flutter_test`(SDK), `mocktail` | |

### 6.3 폴더 구조

```text
app/lib/
├── main.dart                  # AppConfig.validate(), runApp(ProviderScope(child: DevPilotApp()))  — 외부 SDK 초기화 없음
├── app/
│   ├── app.dart               # MaterialApp.router, theme, locale ko, localizationsDelegates
│   └── router.dart            # go_router, 로그인 여부 redirect
├── core/
│   ├── api/                   # ApiClient(dio), interceptors, ProblemDetail, ApiException
│   ├── auth/                  # authStateProvider: AUTH_MODE=dev → DevTokenAuth(/api/v1/dev/token, 메모리+sessionStorage). supabase → Later
│   ├── config/                # AppConfig: String.fromEnvironment 값 검증 (AUTH_MODE, API_BASE_URL)
│   ├── theme/                 # AppTheme (fontFamily Pretendard, code = JetBrainsMono)
│   └── widgets/               # 공통 위젯
├── l10n/
│   └── app_ko.arb             # 생성 파일 app_localizations*.dart 는 커밋하지 않음
└── features/
    └── <feature>/             # auth, onboarding, today, review, training, coach, plan, dashboard, evidence, radar, settings
        ├── data/              # repository, freezed DTO
        ├── domain/            # 화면 독립 상태·모델
        └── presentation/      # screen, widget, Notifier
```

`AppConfig` 값은 빌드 시 `--dart-define-from-file`로 들어온다:

| 키 | local (`config/local.json`, 커밋 안 함) | prod (**파일 없음** — `release.yml`이 inline `--dart-define`으로 준다, `10` §7.2) |
|---|---|---|
| `AUTH_MODE` | `dev` | `dev` (결정 A). `supabase`는 Later |
| `API_BASE_URL` | `http://localhost:8080` | **빈 문자열** = same-origin (`/api/v1`). tailnet 호스트명을 저장소에 넣지 않는다 (`10` §4.1) |
| `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` | (없음) | (없음) — `AUTH_MODE=supabase`일 때만 필요(Later) |

클라이언트는 `API_BASE_URL` 뒤에 `/api/v1/...`을 붙이고, 비어 있으면 현재 origin을 쓴다. `AUTH_MODE`가 `dev`·`supabase` 외의 값이면 앱 시작 시 오류 화면을 표시한다. `AUTH_MODE=dev`의 로그인 화면은 이메일 입력 1칸 + 로그인 버튼이며 `POST /api/v1/dev/token {email}` → 403 `USER_NOT_ALLOWED`면 "허용된 사용자가 아닙니다"를 표시한다. 토큰은 `Authorization: Bearer`로 붙이고, 401이면 토큰을 지우고 로그인 화면으로 보낸다.

### 6.4 analysis_options

전체 파일: `repo-seed/app/analysis_options.yaml`.

- `include: package:flutter_lints/flutter.yaml`
- `formatter.page_width: 100`, `trailing_commas: preserve`
- `analyzer.language`: `strict-casts`, `strict-inference`, `strict-raw-types` = true
- 생성 파일 제외: `**/*.g.dart`, `**/*.freezed.dart`, `lib/l10n/app_localizations*.dart`
- 추가 lint: `avoid_dynamic_calls`, `avoid_print`, `unawaited_futures`, `prefer_final_locals`, `prefer_single_quotes`, `directives_ordering`, `use_build_context_synchronously` 등
- 완료 기준: `fvm flutter analyze --fatal-infos` 0건, `fvm dart format --output=none --set-exit-if-changed lib test` 통과
- `flutter create` 템플릿의 `test/widget_test.dart`는 `directives_ordering` info를 1건 낸다. S0 widget test로 교체한다 (Flutter 3.44.6 + 이 설정으로 2026-09-17 확인: 설정 오류 0건)

### 6.5 l10n

`app/l10n.yaml`:

```yaml
arb-dir: lib/l10n
template-arb-file: app_ko.arb
output-localization-file: app_localizations.dart
output-class: AppLocalizations
nullable-getter: false
```

`pubspec.yaml`의 `flutter:` 아래에 `generate: true`를 둔다. 생성 파일은 `lib/l10n/`에 생긴다(`fvm flutter gen-l10n`, `flutter run`이 자동 실행).

```dart
MaterialApp.router(
  locale: const Locale('ko'),
  supportedLocales: AppLocalizations.supportedLocales,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  // ...
);
```

`app_ko.arb` 예:

```json
{
  "@@locale": "ko",
  "appTitle": "DevPilot",
  "loginEmailLabel": "이메일",
  "loginButton": "로그인",
  "loginNotAllowed": "허용된 사용자가 아닙니다.",
  "errorGeneric": "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
}
```

### 6.6 한글 폰트 번들

Flutter web은 번들 폰트에 없는 글자를 `https://fonts.gstatic.com/s/`에서 fallback 폰트로 내려받는다. 운영 CSP(`connect-src 'self'`, `font-src 'self'`, `10` §4.2 — 외부 호스트 없음)는 이 요청을 막는다. 따라서 **화면에 표시되는 모든 글자를 번들 폰트가 덮어야 한다.**

| 용도 | 폰트 | 파일 | 라이선스 |
|---|---|---|---|
| 본문 (한글 11,172자 전체 + 라틴) | Pretendard 1.3.9 | `Pretendard-Regular.otf`(400), `Pretendard-SemiBold.otf`(600), `Pretendard-Bold.otf`(700) | OFL-1.1 |
| 코드 | JetBrains Mono 2.304 | `JetBrainsMono-Regular.ttf` (한글은 `fontFamilyFallback: ['Pretendard']`) | OFL-1.1 |

- Pretendard는 한글 2,350자만 담은 Std/서브셋 버전을 쓰지 않는다. 사용자 입력에 드문 음절이 들어오면 CSP 때문에 표시되지 않는다.
- 폰트 파일과 `OFL.txt`를 `app/assets/fonts/`에 둔다.
- 이모지는 번들하지 않는다. 운영에서 이모지는 빈 상자로 보일 수 있다(수용). 앱 UI 문자열에 이모지를 쓰지 않는다.
- 번들 크기는 SP-1에서 `build/web/assets/fonts/` 합계로 측정한다. 6MB를 넘으면 `Pretendard-SemiBold`를 빼고 600 weight를 Bold로 대체한다.

`pubspec.yaml`:

```yaml
flutter:
  uses-material-design: true
  generate: true
  fonts:
    - family: Pretendard
      fonts:
        - asset: assets/fonts/Pretendard-Regular.otf
          weight: 400
        - asset: assets/fonts/Pretendard-SemiBold.otf
          weight: 600
        - asset: assets/fonts/Pretendard-Bold.otf
          weight: 700
    - family: JetBrainsMono
      fonts:
        - asset: assets/fonts/JetBrainsMono-Regular.ttf
```

```dart
ThemeData(fontFamily: 'Pretendard');
const codeStyle = TextStyle(fontFamily: 'JetBrainsMono', fontFamilyFallback: ['Pretendard']);
```

### 6.7 Web 빌드

```powershell
# 운영 빌드: release.yml의 build-web job과 같은 값 (10 §7.2). config/prod.json 파일은 만들지 않는다
fvm flutter build web --release --no-web-resources-cdn --csp `
  --dart-define=AUTH_MODE=dev --dart-define=API_BASE_URL=

# 로컬에서 빌드만 확인할 때 (CI app job과 같은 값)
fvm flutter build web --release --no-web-resources-cdn --csp --dart-define-from-file=config/local.example.json
```

| 옵션 | 결정 | 이유 |
|---|---|---|
| `--no-web-resources-cdn` | 사용 | CanvasKit 등 엔진 리소스를 CDN 대신 산출물(`build/web/canvaskit/`)에 포함한다. CSP가 `'self'`만 허용한다. flutter_tools 3.47.4 소스에서 `web-resources-cdn` 플래그(기본 true, negatable) 존재를 확인했다(2026-09-17). **SP-1에서 확인**: `build/web/canvaskit/` 존재, 브라우저 Network 탭에 `gstatic.com` 요청 0건 |
| `--csp` | 사용 | dart2js 동적 코드 생성을 끈다 (`script-src 'self' 'wasm-unsafe-eval'` 충족) |
| `--wasm` | 사용 안 함 | SP-1 한글 입력 검증은 기본 JS + CanvasKit 빌드 기준. 바꾸려면 SP-1 재검증 후 ADR |
| `--pwa-strategy` | 지정 안 함 | flutter_tools에서 deprecated(숨김) 옵션이다 |
| `--base-href` | 기본값 `/` | `https://<tailnet-host>/` 루트에 배포 |
| `--source-maps` | 사용 안 함 | 운영 산출물에 소스맵을 올리지 않는다 |

`web/index.html`은 `<html lang="ko">`로 바꾸고, 인라인 `<script>`를 추가하지 않는다(CSP).

### 6.8 PWA 기본

전체 파일: `repo-seed/app/web/manifest.json`. DEC-18에 따라 모바일은 PWA로 먼저 제공한다.

| 항목 | 값 |
|---|---|
| `name` / `short_name` | `DevPilot 학습 코치` / `DevPilot` |
| `lang` | `ko` |
| `start_url` / `scope` | `.` / `.` |
| `display` | `standalone` |
| `theme_color` / `background_color` | `#1E40AF` / `#FFFFFF` |
| `icons` | `icons/Icon-192.png`, `Icon-512.png`, maskable 192/512 (create 템플릿 파일을 DevPilot 아이콘으로 교체) |

- 오프라인 동작은 MVP 범위가 아니다. 설치 가능 여부만 확인한다: Chrome DevTools → Application → Manifest에 오류 0건, Android Chrome "홈 화면에 추가" 동작 (S2).
- 정적 파일은 `Cache-Control: no-cache`로 제공되므로(`10` §4) 배포 직후 새 버전이 적용된다.

---

## 7. S0 완료 조건

S0(2026-09-17 ~ 09-27) 종료 시 아래 항목이 모두 체크되어야 한다. 사람이 해야 하는 항목(계정·콘솔 작업)은 `[사람]`으로 표시한다.

### 7.1 저장소 · GitHub

- [ ] **S0 첫 단계 — 빌드 진입점 만들기**: `backend/gradlew`·`backend/gradlew.bat`·`backend/gradle/wrapper/`(§4.1-0, `gradle wrapper --gradle-version 9.7.1`)와 `app/.fvmrc`·`app/pubspec.yaml`(§6.1·§6.2). **2026-09-18 현재 저장소에 없다.** 이 파일들이 생기기 전에는 CI의 `backend`·`app` job이 첫 step에서 실패한다(`10` §7.1)
- [ ] §2 구조, `AGENTS.md`, `CLAUDE.md`, `README.md`, `CHANGELOG.md`, `.gitattributes`(`* text=auto eol=lf`), `.editorconfig`, `.gitignore`(`.env`, `DevPilot-ops/`, `app/config/*.json`), `.env.example`
- [ ] 셸 스크립트·`gradlew` 실행 비트 (`git ls-files -s infra/scripts backend/gradlew` → `100755`)
- [ ] [사람] 저장소 public (DEC-04), Settings → Code security: **Secret scanning + Push protection 활성화**, Dependabot alerts/security updates 활성화
- [ ] [사람] 브랜치 `developer` 생성(기본 브랜치는 `main`). Git 전략: `feature/BL-<EPIC>-<nn>-<슬러그>` → `developer`(squash) → `main`(PR + merge commit), `fix/`·`chore/`·`docs/`, Conventional Commits 영문, PR 제목에 BL ID (`20` DEC-22, `08` §10.1)
- [ ] [사람] Branch protection(ruleset) **`main`·`developer` 둘 다**: PR 필수, status check `security`, `backend`, `app`, `content` 필수 (`10` §7.1)
- [ ] [사람] Actions 설정: Workflow permissions = Read repository contents, "Require approval for all outside collaborators"
- [ ] 모든 workflow의 `uses:` **23개**를 commit SHA로 고정하고 `ci.yml`의 `actions-pin-check` step에서 `continue-on-error: true`를 지운다 (`10` §7.5). 저장소 전체 `# TODO(S0)` 주석 0건. base image `@sha256` digest 고정·Trivy·CodeQL은 S2 P1(`10` §7.4)
- [ ] [사람] GitHub environment `ai-eval`(Required reviewers = 소유자, secret `DEEPSEEK_API_KEY`, `10` §7.3) 생성. `production` environment는 만들지 않는다(결정 D)

### 7.2 Backend

- [ ] Boot 4.1.1 + Java 25 toolchain, `./gradlew check` 통과 (spotless, checkstyle, pmd, spotbugs, jacoco, openApiCheck 포함) — 서버 Docker 터널(§3.1)로
- [ ] `GET /actuator/health` → `{"status":"UP"}` (서버 PostgreSQL 16에 Flyway 적용)
- [ ] `V1__baseline.sql` (`create schema if not exists devpilot` …, `04` §10). PG16에서 전체 migration 적용 확인(2026-09-18 완료, `20` DEC-20)
- [ ] Testcontainers(`postgres:16`) migration 통합 테스트 1개 (빈 DB → migration → `ddl-auto=validate` 기동)
- [ ] ArchUnit 테스트: controller→repository 금지, 필드 주입 금지, 무인자 `now()` 금지
- [ ] BL-SEC-16 devtoken 모드: `POST /api/v1/dev/token {email}` → `200 {accessToken, expiresAt}` / allowlist 밖 `403 USER_NOT_ALLOWED`, `GET /api/v1/dev/jwks.json`(permitAll), JWT `iss=${APP_BASE_URL}/dev`·`aud=authenticated`·`sub=uuid v5(email)`·`email`·`amr=[{method:"devtoken"}]`·ttl 720h, `JwtDecoder`는 `NimbusJwtDecoder.withJwkSource`(JWKS HTTP 조회 없음), `DEVPILOT_DEV_JWT_KEY` 비면 기동 시 키 생성 + 테스트(`auth-mode=devtoken` property로 기동)
- [ ] `GET /api/v1/me` 임시 구현(JWT `sub` 반환) + 테스트: 토큰 없음 401, 유효 토큰 200, 잘못된 `aud`·`iss` 401, allowlist 밖 403
- [ ] `TraceIdFilter`, `ClockConfig`
- [ ] `OpenApiSnapshotTest` + `docs/api/openapi.yaml` 커밋
- [ ] SP-4: Spotless/PMD/SpotBugs/Checkstyle/springdoc/ArchUnit이 Java 25 + Boot 4.1에서 공존 → 결과 ADR. 6번 "RestClient + DeepSeek `/responses` smoke"는 2026-09-18 완료(`14` ADR-032), SDK 공존 항목 없음

### 7.3 App

- [ ] Flutter 3.47.4 (FVM), Riverpod, go_router, dio, `web` (supabase_flutter 없음)
- [ ] 로그인 화면(이메일 입력, `AUTH_MODE=dev`) → `POST /api/v1/dev/token` → `/api/v1/me` 결과 표시. 403이면 "허용된 사용자가 아닙니다"
- [ ] 한글 폰트 번들 (§6.6), l10n(§6.5), widget test 1개
- [ ] `fvm flutter analyze --fatal-infos` 0건, `fvm flutter test` 통과, `flutter build web --release --no-web-resources-cdn --csp --dart-define=AUTH_MODE=dev --dart-define=API_BASE_URL=` 성공(§6.7, release.yml과 같은 값)
- [ ] SP-1: 한글 긴 서술(조합 중 Backspace·커서 이동·붙여넣기), 코드 붙여넣기(탭·공백 유지) — Chrome, Edge, 모바일 Safari → 결과 ADR

### 7.4 서버 · Tailscale · 배포 (`10-deployment-and-operations.md`)

(v2 초판의 Supabase·OCI·도메인 항목은 취소했다. 요지는 `10` §13.8 부록.)

- [ ] [사람] Tailscale admin console: MagicDNS·HTTPS Certificates 활성화 — `10` §2.3
- [ ] [사람] 서버(root) `prepare-server.sh --operator <user>` → `deploy` 사용자, `/opt/devpilot`, systemd 유닛 — `10` §2.2. OS 설정은 건드리지 않는다
- [ ] [사람] `tailscale serve --bg --https=443 http://127.0.0.1:18080` — `10` §2.3
- [ ] [사람] 서버 `/opt/devpilot/api.env`(`DEVPILOT_DEV_JWT_KEY`·`DEVPILOT_ALLOWED_EMAILS`·`DATABASE_*`·`APP_BASE_URL=https://<tailnet-host>` 등)·`ops.env`(`GITHUB_OWNER` 필수 — deploy.sh가 여기서 이미지 이름과 `web.zip` URL을 만든다) 작성, password manager 백업 — `10` §5.5, §6
- [ ] SP-2: 서버 PostgreSQL 16 연결 + Flyway 전체 적용 + 원격 Docker Testcontainers — **2026-09-18 완료** (`20` DEC-20·DEC-21) → 결과 ADR
- [ ] SP-3: `ubuntu-24.04`에서 `linux/amd64,linux/arm64` 멀티아치 이미지 빌드(QEMU + buildx) → GHCR → 서버 pull(amd64) → Tailscale HTTPS로 `/actuator/health` → 결과 ADR. arm64 레이어를 돌려 볼 서버가 지금은 없으므로 manifest에 들어갔는지만 확인한다(`docker buildx imagetools inspect`). 공개 전환 후보가 OCI Ampere A1(arm64)이다 (`10` §1.4)
- [ ] `v0.0.1` tag → `release.yml` 녹색 → 서버 `deploy.sh v0.0.1` 성공, tailnet 기기(폰 포함)에서 `https://<tailnet-host>` 로그인 → `/me` 성공
- [ ] 롤백 1회 검증: `deploy.sh <이전 tag> --no-pull` (`10` §13.1. `--skip-backup` 플래그는 없다 — 백업은 `--backup-first`일 때만 실행된다)
- [ ] healthchecks.io `devpilot-health` + `devpilot-healthping.timer` 활성화 — `10` §11.2
- [ ] ~~Supabase dev/prod 프로젝트·GitHub OAuth App·JWKS `alg`·SP-5 Data API·"Allow new users to sign up"~~ 취소(Supabase Auth Later)
- [ ] ~~OCI 계정·A1 VM·`bootstrap-vm.sh`·도메인 A 레코드·Caddy ACME·`ubuntu-24.04-arm`~~ 취소(자체 서버, 결정 B·D)
- [ ] 저장소에 실제 secret·서버 주소·이메일 없음 (gitleaks 통과 + 수동 확인)

---

## 8. 문제 해결

| 증상 | 원인 | 조치 |
|---|---|---|
| CI나 서버에서 `/bin/bash^M: bad interpreter`, `./gradlew: not found`, `$'\r': command not found` | 스크립트가 CRLF로 커밋됨 | `.gitattributes`가 §3.2와 같은지 확인 → `git add --renormalize .` → 커밋. `git ls-files --eol infra/scripts/deploy.sh`가 `i/lf w/lf`인지 확인 |
| `Permission denied` (서버에서 `deploy.sh`, `backup.sh`, CI에서 `./gradlew`) | git 실행 비트 없음 | `git update-index --chmod=+x backend/gradlew infra/scripts/*.sh` → 커밋 |
| `bootRun`: `Connection to <server>:5432 refused` / `connect timed out` | Tailscale 미연결, 서버 오프라인, 또는 `.env`의 `DATABASE_URL` 호스트 오타 | `tailscale status`에 서버 노드 online 확인 → `Test-NetConnection <server> -Port 5432` → `.env` 값을 `DevPilot-ops/02-database.md`와 대조. 서버 PostgreSQL 컨테이너가 죽었으면 `10` §13.5 |
| `bootRun`: `password authentication failed for user "devpilot"` | `.env`의 `DATABASE_PASSWORD` 오류(비밀번호 교체 후 미반영) | `DevPilot-ops/02-database.md`의 현재 값으로 갱신 (`10` §13.3.1) |
| `bootRun`: `Could not resolve placeholder 'DATABASE_URL'` | `.env` 미생성, 작업 디렉터리가 `backend/`가 아님, 또는 profile이 local이 아님 | 루트 `.env` 확인, IDE Working directory = `backend`, Active profile = `local`. local은 datasource 기본값이 없다(§4.4) |
| Testcontainers: `Could not find a valid Docker environment` / `Connection refused: localhost/127.0.0.1:2375` | 서버 Docker 터널이 꺼짐(터미널 A 종료, PC 절전, SSH 끊김), 또는 `DOCKER_HOST` 미설정 | `. .\infra\scripts\dev-docker-tunnel.ps1` 다시 실행(dot-source) → `docker info` 성공 확인 → 재실행. `DOCKER_HOST=tcp://localhost:2375`가 사용자 환경변수에 있는지 확인(§3.1). Docker 없이 돌릴 테스트는 `./gradlew test`(tag `unit`) |
| Testcontainers: 컨테이너는 떴는데 JDBC `Connection to localhost:<random port> refused` 또는 Ryuk `Connection refused` | `TESTCONTAINERS_HOST_OVERRIDE` 미설정 — publish 포트가 서버에 열렸는데 localhost로 접속 | `TESTCONTAINERS_HOST_OVERRIDE=<server tailnet IP>` 설정(§3.1) 후 터미널·IDE 재시작 |
| Testcontainers: `TESTCONTAINERS_HOST_OVERRIDE`를 설정했는데 `<server tailnet IP>:<port>` timeout | 서버 firewalld에서 `tailscale0`이 `trusted` zone이 아님, 또는 Tailscale 끊김 | 서버 소유자로서 `firewall-cmd --get-zone-of-interface=tailscale0` → `trusted`인지 확인(2026-09-18 기준 trusted). 로컬 `tailscale status` 확인 |
| `ssh: Permission denied (publickey)` | 공개키가 서버 `authorized_keys`에 없거나 `~/.ssh/` 키 파일 권한·경로 문제 | §3.1·§5.2-1. `ssh -v <user>@<server>`로 어떤 키를 시도하는지 확인 |
| Testcontainers: `client version 1.xx is too old. Minimum supported API version is 1.44` | 오래된 Testcontainers/docker-java와 최신 Docker Engine(29.x)의 API 버전 불일치 | Boot BOM이 정한 Testcontainers(2.0.5)를 쓰는지 확인(버전 직접 지정 금지). 계속되면 `%USERPROFILE%\.docker-java.properties`에 `api.version=1.44` 추가 |
| Testcontainers가 매우 느리거나 Ryuk 컨테이너 시작 실패 | 서버 자원 부족(다른 사용자 컨테이너), 업무용 VPN이 Tailscale 트래픽을 막음 | `ssh <user>@<server> docker stats --no-stream`·`free -m` 확인, VPN 끄고 재시도. 오프라인 대체는 §3.1 |
| Flutter: `Filename too long`, pub get 실패 | Windows 경로 길이 제한 | §3.1 `LongPathsEnabled`, `git config --global core.longpaths true`, 저장소 경로 단축 |
| Flutter: `Building with plugins requires symlink support` / FVM `.fvm` 링크 생성 실패 | 개발자 모드 꺼짐 | Windows 설정 → 개발자 모드 켜기 → 터미널 재시작 |
| 브라우저 콘솔 CORS 오류 (localhost:5173 → 8080) | `CORS_ALLOWED_ORIGINS` 불일치 | `.env`에 정확히 `http://localhost:5173` (끝 `/` 없음), backend 재시작 |
| 로그인 버튼 → `POST /api/v1/dev/token` 404 | backend가 `supabase` 모드로 떠 있음(`DEVPILOT_AUTH_MODE`), 경로에 `/api/v1` 접두사를 빠뜨림, 또는 `API_BASE_URL` 오타 | `.env`의 `DEVPILOT_AUTH_MODE=devtoken`(기본), `app/config/local.json`의 `API_BASE_URL=http://localhost:8080` |
| 로그인 직후 또는 backend 재시작 후 API 401 | `DEVPILOT_DEV_JWT_KEY`가 비어 있어 재기동 시 새 키가 생성됨(기존 토큰 무효) | 다시 로그인. 반복이 싫으면 §4.4대로 PEM을 `.env`에 고정 |
| API 401, 로그에 `iss` 불일치 | 토큰 발급 시점과 검증 시점의 `APP_BASE_URL`이 다름(`.env` 수정 후 옛 토큰 사용) | 다시 로그인. issuer는 `${APP_BASE_URL}/dev` 고정 |
| API 401, 로그에 `aud` 불일치 | 다른 환경(prod)의 토큰을 local에 붙임, 또는 `supabase` 모드 토큰 | local에서 다시 로그인. 토큰 `aud`는 `authenticated` |
| API 403 `USER_NOT_ALLOWED` (`/api/v1/dev/token` 또는 `/api/v1/*`) | allowlist에 이메일 없음 | `.env`의 `DEVPILOT_ALLOWED_EMAILS`에 이메일을 소문자로 추가 → backend 재시작 |
| AI 기능이 꺼지고 `aiStatus=BALANCE_EXHAUSTED`, 로그에 DeepSeek 402 `insufficient_balance` | DeepSeek 선불 잔액 소진 (`min-balance-usd` 1.00 이하 또는 402 수신) | platform.deepseek.com에서 소액 충전 → 다음 `AiBalanceCheckJob`(매시 15분) 또는 backend 재시작으로 해제 (`10` §11.6). 로컬 개발은 `DEVPILOT_AI_PROVIDER=fake`로 돌린다 |
| AI 호출 401 `authentication_error` | `DEEPSEEK_API_KEY` 미설정·오타·폐기된 키 | `.env` 값 확인(`sk-` + 16진수 32자), 키 교체는 `10` §13.3.2 |
| `remaining connection slots are reserved` / 연결 수 초과 | 공용 PostgreSQL의 `max_connections` 소진(다른 사용자 포함) | Hikari `maximum-pool-size=5` 확인, 로컬에서 서버 DB에 붙은 도구(DB 클라이언트, 죽은 bootRun) 종료. 계속되면 서버 소유자로서 PostgreSQL 설정 확인 |
| 로컬 Flyway가 `Validate failed: Migration checksum mismatch` | local과 prod가 같은 DB를 쓰는데(§4.4) 로컬에서 적용된 migration 파일을 수정함 | 적용된 migration은 수정하지 않는다(`04` §10). 개발 중 임시 migration은 `drop schema devpilot cascade`(§5.3, 운영 데이터가 없을 때만) 후 재적용 |

---

## 9. 확인 출처 (2026-09-17 조회, 2026-09-18 갱신)

- Spring Boot 지원 기간: https://spring.io/projects/spring-boot#support (API: https://api.spring.io/projects/spring-boot/generations)
- Spring Boot 4.1 Application Properties: https://docs.spring.io/spring-boot/4.1/appendix/application-properties/index.html
- Spring Framework `RestClient`·`MockRestServiceServer`: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html · https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html
- Spring Security `NimbusJwtDecoder.withJwkSource` (EC 키는 `withPublicKey`가 RSA 전용이라 JWK source로 넘긴다): https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Spring Initializr 메타데이터·생성: https://start.spring.io
- Spring Boot BOM: https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/
- Maven Central 메타데이터: springdoc https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml · ArchUnit https://repo1.maven.org/maven2/com/tngtech/archunit/archunit-junit5/maven-metadata.xml · JSpecify https://repo1.maven.org/maven2/org/jspecify/jspecify/maven-metadata.xml · google-java-format https://repo1.maven.org/maven2/com/google/googlejavaformat/google-java-format/maven-metadata.xml · PMD https://repo1.maven.org/maven2/net/sourceforge/pmd/pmd-java/maven-metadata.xml · Checkstyle https://repo1.maven.org/maven2/com/puppycrawl/tools/checkstyle/maven-metadata.xml · SpotBugs https://repo1.maven.org/maven2/com/github/spotbugs/spotbugs/maven-metadata.xml · JaCoCo https://repo1.maven.org/maven2/org/jacoco/org.jacoco.agent/maven-metadata.xml
- Testcontainers 원격 Docker(`DOCKER_HOST`, `TESTCONTAINERS_HOST_OVERRIDE`): https://java.testcontainers.org/features/configuration/ · Docker 원격 소켓 SSH 포워딩: https://docs.docker.com/engine/security/protect-access/
- DeepSeek API(`/responses`, 잔액, 단가): https://api-docs.deepseek.com/ · https://api-docs.deepseek.com/api/get-user-balance
- Tailscale serve·MagicDNS: https://tailscale.com/kb/1312/serve · https://tailscale.com/kb/1081/magicdns
- Windows OpenSSH 클라이언트: https://learn.microsoft.com/windows-server/administration/openssh/openssh_install_firstuse
- Gradle Plugin Portal: Spotless https://plugins.gradle.org/plugin/com.diffplug.spotless · SpotBugs https://plugins.gradle.org/plugin/com.github.spotbugs · Gradle 현재 버전 https://services.gradle.org/versions/current
- springdoc-openapi (3.x = Boot 4): https://springdoc.org/
- OpenJDK 25: https://openjdk.org/projects/jdk/25/ · Temurin: https://adoptium.net/support/ · Oracle JDK 21 라이선스 변경: https://blogs.oracle.com/java/jdk-21-approaches-end-of-permissive-license
- Flutter releases: https://docs.flutter.dev/release/archive · 릴리스 JSON https://storage.googleapis.com/flutter_infra_release/releases/releases_linux.json
- Flutter web 빌드 옵션: https://docs.flutter.dev/deployment/web · 초기화(`fontFallbackBaseUrl`): https://docs.flutter.dev/platform-integration/web/initialization · flutter_tools 소스 https://github.com/flutter/flutter/blob/3.47.4/packages/flutter_tools/lib/src/commands/build_web.dart
- Flutter 이슈: https://github.com/flutter/flutter/issues/138288 , https://github.com/flutter/flutter/issues/183078
- pub.dev 패키지: https://pub.dev/packages/flutter_riverpod · https://pub.dev/packages/go_router · https://pub.dev/packages/dio · https://pub.dev/packages/freezed · https://pub.dev/packages/flutter_markdown_plus · https://pub.dev/packages/web · (Later) https://pub.dev/packages/supabase_flutter
- FVM 설치: https://fvm.app/documentation/getting-started/installation
- winget 패키지: https://github.com/microsoft/winget-pkgs
- Pretendard: https://github.com/orioncactus/pretendard · JetBrains Mono: https://github.com/JetBrains/JetBrainsMono
- GitHub-hosted runners: https://docs.github.com/en/actions/reference/runners/github-hosted-runners
- AGENTS.md 규약: https://code.claude.com/docs/en/memory#agentsmd
