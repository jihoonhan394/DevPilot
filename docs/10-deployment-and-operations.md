# 10. Deployment & Operations

> Status: Accepted (v2) · Last updated: 2026-09-18 · Related: DEC-01, DEC-04, DEC-05, DEC-06, DEC-13, DEC-16, `03-system-architecture.md`, `04-domain-model-and-db.md`, `07-security-and-privacy.md`, `18-project-setup-and-local-dev.md`, `14-adrs.md` ADR-030~035, `20-decisions-and-risks.md` DEC-05~23
>
> 이 문서는 **운영 환경 구성(자체 서버 + Tailscale HTTPS), 컨테이너, 환경변수, CI/CD, 백업·복구, 릴리스·롤백, Flyway 운영, 모니터링, 비용, runbook**을 정의한다. **지금의 tailnet 전용 구성은 임시 단계이고 최종 목표는 공개 배포다 — 단계 구분과 공개 전환 전 필수 조건은 §1.4·§1.5에 있다.** 로컬 개발환경과 버전 표는 `18-project-setup-and-local-dev.md`가 기준이다. 이 문서의 파일은 `repo-seed/`에 있다(`infra/`, `backend/Dockerfile`, `.github/`).
>
> 2026-09-18 결정(`14-adrs.md` ADR-031·ADR-034, `20-decisions-and-risks.md` DEC-07·DEC-13·DEC-19): 운영은 **사용자 소유 Linux 서버**에서 하고 **Tailscale 안에서만** 접근한다. OCI·구매 도메인·Supabase는 쓰지 않는다(부록 §13.8에 "운영 전환 시" 요약만 남긴다). 서버 접속 정보(주소·계정·DB 접속값)는 공개 저장소에 두지 않고 저장소 밖 `DevPilot-ops/`(`01-servers.md`, `02-database.md`)에 둔다. 이 문서의 `<server>`, `<tailnet-host>`, `<user>`는 그 값을 가리키는 자리표시자다.

---

## 1. 환경 · 토폴로지

### 1.1 환경

| 환경 | 용도 | Web | Backend | DB | Auth | AI |
|---|---|---|---|---|---|---|
| local | 개발 | `flutter run` :5173 | `bootRun` :8080 (`local`) | **서버 PostgreSQL 16** (Tailscale 경유, DB `devpilot`) | devtoken (`POST /api/v1/dev/token`) | `fake` (필요 시 `deepseek`) |
| CI | PR·developer·main·tag 검증 | `flutter build web` | `./gradlew check` (`test`) | Testcontainers `postgres:16` | 테스트 JWT | `fake` |
| prod | 실사용 (소유자 + 초대 최대 2명, DEC-01) | `caddy` 컨테이너 (`/opt/devpilot/web` 정적 파일) | `api` 컨테이너 (`ghcr.io/<owner>/devpilot-api`, `prod`) | 같은 서버의 PostgreSQL 16 (DB `devpilot`) | devtoken (**tailnet 전용이라서** 허용, 결정 A. 공개 전환 시 `supabase`로 바꾼다 — §1.5) | `deepseek` |

- staging 환경은 두지 않는다. 사용자가 3명 이하이고, 배포는 불변 tag + health check + 롤백(§9)으로 보호한다.
- **prod의 tailnet 전용 노출은 임시다**(§1.4). 공개 전환 전에 §1.5 체크리스트를 끝낸다.
- local과 prod가 **같은 PostgreSQL 인스턴스의 같은 DB**를 쓴다. 실사용 시작(M1 완료 = S3 완료) 전까지는 문제가 없고, 시작 전에 로컬 개발 DB를 `devpilot_dev`로 분리한다(§2.4, BL-OPS 신설 항목). S2 완료 후 Today·Review를 먼저 쓰기 시작한다면(선택, `11` §3) 그 전에 분리한다. Testcontainers는 서버 Docker에 임시 컨테이너를 띄우므로 운영 DB를 건드리지 않는다.
- 서버 사실(2026-09-18 확인): Rocky Linux 9.8 (Proxmox VM), **x86_64**, 2 vCPU / 8 GB (가용 약 4.7 GB), 디스크 여유 32 GB, Docker 29.6.1, Tailscale 1.98.8, firewalld active(`tailscale0`은 `trusted` zone), SELinux disabled, 시간대 UTC. **다른 사용자의 컨테이너 6개와 공용 PostgreSQL 컨테이너가 같은 Docker에서 돈다.** 80/443은 비어 있고 8080~8084·8090·5432는 사용 중이다.

### 1.2 토폴로지

```text
   [소유자 PC · 폰]  ──Tailscale (WireGuard)──▶  tailnet
                                                   │ https://<tailnet-host>  (443, tailnet 전용)
                                                   ▼
┌──────────────── <server> · Rocky Linux 9 x86_64 · Docker · tailscaled ───────────────┐
│ tailscale serve :443 ──▶ http://127.0.0.1:18080                                        │
│  ┌──────────── docker compose project "devpilot" (network: devpilot) ───────────────┐ │
│  │ caddy (caddy:2.11.4-alpine, /opt/devpilot/web → /srv/web)  127.0.0.1:18080 → :80  │ │
│  │   /api/*, /actuator/health ──▶ api (ghcr.io/<owner>/devpilot-api) :8080 (publish 없음) │ │
│  └────────────────────────────────────────────────────────────────────────────────────┘ │
│ api ──JDBC (host.docker.internal:5432)──▶ 공용 PostgreSQL 16 컨테이너 (DB devpilot)     │
│ systemd devpilot-backup.timer (19:00 UTC) ──▶ backup.sh ──▶ /opt/devpilot/backups/     │
│ systemd devpilot-healthping.timer (5분) ──▶ healthping.sh ──▶ healthchecks.io          │
└──────────────────────────────────────────────────────────────────────────────────────────┘
        ▲ ssh (Tailscale 경유, 배포·백업 pull)                       │ HTTPS
   [소유자 PC]  ◀── pull-backup.ps1 (주 1회 scp) ──                 ▼
   GitHub Actions: ci.yml(검증) · release.yml(api 이미지 amd64+arm64 → GHCR,   DeepSeek API
                   web.zip(Flutter web 정적 파일) → GitHub Release)
```

### 1.3 포트

| 포트 | 어디에 열리나 | 공개 | 비고 |
|---|---|---|---|
| 443/tcp | tailnet 주소만 (`tailscale serve`) | tailnet 전용 | TLS는 tailscaled가 종료한다 (§3) |
| 18080/tcp | `127.0.0.1`만 | 비공개 | `caddy` 컨테이너 publish. Docker publish는 firewalld를 우회하므로 **반드시 `127.0.0.1:` 접두사**를 붙인다 (§5.2) |
| 8080/tcp | 컨테이너 네트워크 | 비공개 | `expose`만. 호스트 8080은 다른 사용자의 컨테이너가 쓴다 |
| 5432/tcp | 호스트 (공용 PostgreSQL 컨테이너) | tailnet·LAN | 기존 인스턴스. DevPilot은 DB `devpilot`·role `devpilot`만 쓴다 |
| 22/tcp | 호스트 sshd | tailnet·LAN | 기존 설정 그대로. 배포·백업 pull은 Tailscale 경유 |

DevPilot은 서버의 방화벽·sshd·Docker daemon 설정을 **바꾸지 않는다** (§2.1).

### 1.4 호스팅 단계 — 지금 구성은 임시다

**Tailscale 전용은 목표가 아니라 지금 단계의 수단이다.** 개발에 쓸 DB와 서버가 이미 tailnet 안에 있어 거기에 맞춰 묶었을 뿐이고, **최종 목표는 공개 배포다.**

| 단계 | 노출 | 내용 |
|---|---|---|
| **현재 (공개 전환 게이트 `11` §3.10 통과 전까지)** | tailnet 전용 | 자체 서버 + `tailscale serve`(§2.3). 이 문서의 §2~§13은 이 단계를 기준으로 쓴다. **임시** |
| **공개 전환 (추후, 시점 미정)** | 공개 인터넷 | (a) 같은 자체 서버를 웹서버로 공개하거나 (b) **OCI Always Free**(Ampere A1 = **arm64**)에 배포한다. 어느 쪽인지는 전환 시점에 정한다. 절차 요지는 §13.8 |

전환 비용을 줄이려고 지금부터 지키는 것:

| 항목 | 지금 하는 것 | 전환 때 덕을 보는 부분 |
|---|---|---|
| 이미지 | `linux/amd64` + `linux/arm64` **멀티아치**로 빌드한다 (§5.3, §7.2) | OCI A1(arm64)로 가도 이미지 파이프라인을 고치지 않는다 |
| 앱 주소 | prod 빌드는 same-origin(`API_BASE_URL` 비움, §4.1) | 호스트명이 산출물·저장소에 없어 주소가 바뀌어도 재빌드가 필요 없다 |
| 인증 | `DEVPILOT_AUTH_MODE` 설정 하나로 `devtoken` ↔ `supabase`를 바꾼다 (§6) | 코드 변경 없이 전환한다 (§1.5) |
| 접속값 | 서버 주소·계정·DB 값은 `DevPilot-ops/`에만 둔다 | 서버가 바뀌어도 저장소를 고치지 않는다 |

### 1.5 공개 전환 전 필수 조건

**아래를 전부 끝내기 전에는 공개 인터넷에 노출하지 않는다.** 하나라도 남아 있으면 tailnet 안에 둔다.

- [ ] **인증을 실제 신원 확인이 있는 방식으로 바꾼다 (가장 중요, `13` BL-SEC-18).** `devtoken`은 이메일 한 줄을 보내면 토큰을 내준다(`03` §4.2, `05` §1.4.5). **tailnet 전용이라는 전제에서만 안전하고**, 공개되면 allowlist 이메일을 아는 사람 누구나 로그인한다. 고칠 지점은 **발급 직전의 신원 확인 단계 하나**이고 JWT 발급·검증 경로는 그대로 쓸 수 있다.

  | 후보 | 내용 | 필요한 것 |
  |---|---|---|
  | **(a) GitHub OAuth 직접** (권장) | backend가 `spring-boot-starter-oauth2-client`로 GitHub 로그인을 처리 → 이메일 확인 → allowlist 검사 → **지금의 EC 키 JWT를 그대로 발급**. 외부 인증 서비스 의존 없음 | GitHub OAuth App 1개(callback은 우리 도메인), client id·secret을 `api.env`에. Flutter는 로그인 버튼만 교체 |
  | (b) Supabase Auth | `DEVPILOT_AUTH_MODE=supabase`. JWKS 검증 경로로 바뀐다 | Supabase 프로젝트, GitHub OAuth App, `SUPABASE_URL`·`SUPABASE_JWT_ALGORITHM`·`SUPABASE_PUBLISHABLE_KEY`(§6), 앱 `AUTH_MODE=supabase` 빌드. 요지는 §13.8 |

  (a)가 단순한 이유: `DevTokenService`의 서명·검증·claim 구성·allowlist 검사·JIT 프로비저닝이 모두 재사용되고, 바뀌는 것은 "이메일을 입력받는다" → "GitHub가 확인해 준 이메일을 받는다"뿐이다. 외부 서비스 중단·무료 플랜 제약(7일 비활성 일시정지)도 없다.
- [ ] TLS를 `tailscale serve`에서 **Caddy 자동 HTTPS(ACME)**로 바꾼다. 도메인(A 레코드) 또는 공개 IP가 필요하다. `infra/Caddyfile`의 `auto_https off`와 사이트 주소 `:80`을 함께 고친다(§4.2, §5.4).
- [ ] `compose.prod.yml`의 caddy publish를 `127.0.0.1:18080:80`에서 **`80:80`·`443:443`(HTTP/3를 쓰면 `443/udp`도)**로 바꾼다. Docker publish는 firewalld를 우회하므로(§3.2) 이 순간부터 실제로 공개된다. 자체 서버를 쓴다면 호스트 80/443이 비어 있는지, 공용 서버 규칙(§2.1)을 고칠 수 있는지를 먼저 확인한다.
- [ ] CSP `connect-src`를 확인하고(후보 (b)를 고르면 Supabase 호스트 추가, (a)는 `'self'` 그대로), rate limit(`03` §9)을 공개 트래픽 기준으로 다시 정한다. 값의 기준은 `07` §9.3·§9.4다.
- [ ] `07-security-and-privacy.md` §2.4 잔여 위험의 **"tailnet 밖에서 도달할 수 없다"** 전제가 해제됨을 확인하고 위협 모델을 갱신한다(이 항목은 `07`이 기준).
- [ ] 백업·모니터링을 공개 기준으로 다시 본다(§8, §11). healthchecks.io는 공개 주소에서도 그대로 동작한다.
- [ ] 전환 자체를 ADR로 남긴다(ADR-031 대체 또는 보완, `14-adrs.md`).

---

## 2. 서버 준비 체크리스트

(v2 초판의 Supabase 절은 §13.8 부록으로 옮겼다.)

### 2.1 공용 서버 규칙

이 서버는 다른 서비스가 함께 쓰는 서버다. DevPilot 작업은 아래 범위를 벗어나지 않는다.

| 허용 | 금지 |
|---|---|
| `deploy` 사용자 생성, `/opt/devpilot` 이하 파일, compose project `devpilot`, systemd 유닛 `devpilot-*`, `tailscale serve` 설정, PostgreSQL DB `devpilot`·role `devpilot` | `/etc/docker/daemon.json` 변경·dockerd 재시작, podman 제거, `sshd_config`·firewalld·SELinux·dnf-automatic 변경, 패키지 설치, `tailscale up`(이미 합류한 노드), 다른 DB·role·컨테이너 접근, 호스트 포트 80/443/8080 사용 |

### 2.2 `prepare-server.sh` (root, 1회, 멱등)

```bash
# 로컬 PC → 서버 (Tailscale 경유)
ssh <user>@<server>
su -                                  # 또는 sudo -i
git clone --depth 1 https://github.com/<owner>/DevPilot.git /tmp/devpilot
bash /tmp/devpilot/infra/scripts/prepare-server.sh --operator <user>
rm -rf /tmp/devpilot
```

| 단계 | 내용 |
|---|---|
| 전제 조건 확인 | `uname -m`=x86_64, `docker info`, `tailscale status`(Running), `ss -ltn`에 `127.0.0.1:18080` 없음, 가용 메모리 ≥ 2 GB. 하나라도 실패하면 아무것도 만들지 않고 종료 |
| 사용자 | `deploy`(비밀번호 잠금, 로그인 셸 bash, 그룹 `docker`). `--operator <user>`로 지정한 운영자 계정을 `deploy` 그룹에 추가(백업 pull용, `/opt/devpilot/backups` 읽기) |
| 디렉터리·파일 | `/opt/devpilot/{backups,web,state}` (§5.1. `backups`는 setgid 2750). clone의 `infra/compose.prod.yml`·`infra/Caddyfile`(644)과 `infra/scripts/{deploy,backup,healthping,host-check}.sh`(**750**)를 `/opt/devpilot/`에 복사(내용이 다를 때만). 첫 배포 전에도 caddy가 뜨도록 `web/index.html` 자리표시자를 둔다. `api.env`·`ops.env` **템플릿**(0600, 이미 있으면 덮어쓰지 않음) |
| systemd | `devpilot-backup.service/.timer`, `devpilot-healthping.service/.timer` 설치·`daemon-reload`. `devpilot-healthping.timer`는 바로 켠다(ping URL이 비면 스크립트가 스스로 건너뛴다). `devpilot-backup.timer`는 `api.env`의 `DATABASE_PASSWORD`가 채워져 있을 때만 켠다(§8.3) |
| 출력 | 다음 할 일(§2.3 `tailscale serve`, §5.5 env 작성, §9 첫 배포) |

스크립트는 OS 설정을 건드리지 않으므로 다른 서비스에 영향이 없다. 멱등이라 다시 실행해도 기존 env 파일을 덮어쓰지 않는다.

**infra 파일(compose.prod.yml·Caddyfile·스크립트)을 갱신하는 방법은 이 스크립트를 다시 실행하는 것 하나뿐이다.** 릴리스 자산 `web.zip`에는 Flutter web 정적 파일만 들어 있고 deploy.sh는 이 파일들을 건드리지 않는다(§5.3, §9.3).

```bash
# 서버(root): 배포할 tag를 clone해서 다시 실행한다
git clone --depth 1 --branch <tag> https://github.com/<owner>/DevPilot.git /tmp/devpilot
bash /tmp/devpilot/infra/scripts/prepare-server.sh --operator <user>
rm -rf /tmp/devpilot
# Caddyfile이 바뀌었으면 (파일 마운트는 reload로 반영되지 않는다)
sudo -u deploy docker compose -f /opt/devpilot/compose.prod.yml restart caddy
# compose.prod.yml이 바뀌었으면
sudo -u deploy docker compose -f /opt/devpilot/compose.prod.yml up -d
```

### 2.3 Tailscale HTTPS (결정 B, **현재 단계**)

> 이 절은 §1.4의 "현재" 단계 절차다. 공개 전환 시에는 Caddy 자동 HTTPS(ACME)로 바뀐다(§1.5, §13.8).

1. [사람] Tailscale admin console → **DNS**: MagicDNS 켜짐 확인 → **HTTPS Certificates → Enable HTTPS**. tailnet 이름(`<tailnet>.ts.net`)이 표시된다.
2. 서버(root):
   ```bash
   tailscale serve --bg --https=443 http://127.0.0.1:18080
   tailscale serve status            # https://<tailnet-host>/ → http://127.0.0.1:18080
   ```
   `--bg`는 설정을 tailscaled 상태에 저장하므로 재부팅 후에도 유지된다.
3. 확인(첫 배포 뒤): 로컬 PC에서 `curl -sS https://<tailnet-host>/actuator/health` → `{"status":"UP"}`. 첫 요청에서 인증서 발급에 몇 초 걸릴 수 있다.

| 항목 | 내용 |
|---|---|
| 인증서 | tailscaled가 발급·갱신한다(90일 주기, 자동). Caddy는 TLS를 하지 않는다 |
| 접근 범위 | tailnet에 합류한 기기만. 공개 인터넷·LAN에서는 443이 보이지 않는다 |
| 끄기 | `tailscale serve reset` |
| HTTP/3, HTTP→HTTPS redirect | 없음(tailscale serve가 처리하지 않는다). 앱 URL은 항상 `https://` |
| 대안 | Caddy 컨테이너에 `/var/run/tailscale/tailscaled.sock`을 마운트해 Caddy가 직접 Tailscale 인증서를 받는 방식. 설정이 더 많아 채택하지 않는다 |

### 2.4 DB (공용 PostgreSQL 16)

- DB `devpilot`, role `devpilot`(소유자)은 2026-09-18에 만들었다(`DevPilot-ops/02-database.md`). 접속값은 서버 `/opt/devpilot/api.env`와 로컬 `.env`에만 둔다.
- api 컨테이너 → DB: `DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/devpilot`. compose가 `extra_hosts: ["host.docker.internal:host-gateway"]`를 넣는다(§5.2). 공용 PostgreSQL 컨테이너가 5432를 호스트에 publish하고 있어야 한다(`ss -ltn | grep 5432` → `*:5432` 또는 `0.0.0.0:5432`, 2026-09-18 확인). 아니면 서버 LAN IP를 쓴다.
- TLS 없음(같은 호스트 안 연결). `sslmode`를 붙이지 않는다.
- [ ] 실사용 시작(M1 완료 = S3 완료) 전, 백업 timer를 켜는 S2 안에서: 로컬 개발용 DB `devpilot_dev`를 같은 role 소유로 추가하고 로컬 `.env`를 옮긴다(§1.1). 명령은 `DevPilot-ops/02-database.md`.
- 버전: 개발·운영 16, Testcontainers `postgres:16`. **17 전용 문법을 migration에 쓰지 않는다** (`04` §10, `18` §1.1).

### 2.5 GHCR

- 이미지는 `ghcr.io/<owner>/devpilot-api` 하나다. 첫 push 후 **Package settings → Change visibility → Public**으로 바꾼다. 서버는 인증 없이 pull한다(저장소가 public이고 이미지에 secret이 없다). Flutter web 정적 파일은 이미지가 아니라 GitHub Release 자산 `web.zip`으로 배포한다(§5.3, §7.2) — public 저장소라 이것도 인증 없이 받는다. infra 파일은 릴리스 자산에 없다(§2.2).
- private으로 둬야 할 이유가 생기면 `deploy` 사용자로 `docker login ghcr.io`(read-only PAT)를 하고 §6에 PAT 행을 추가한다.

---

## 3. 서버 운영 설정

(v2 초판의 OCI 절은 §13.8 부록으로 옮겼다.)

### 3.1 호스트

| 항목 | 규칙 |
|---|---|
| 시간대 | UTC (애플리케이션이 사용자 시간대로 변환, `03` §5.1·NFR-09). 서버가 이미 UTC다 |
| OS 업데이트·재부팅 | 서버 소유자(= 사용자 본인)의 기존 운영 방식을 따른다. 재부팅 후 `restart: unless-stopped` 컨테이너와 `tailscale serve` 설정은 자동 복구된다 → §11 healthping 확인 |
| 메모리 | 가용 4.7 GB 중 DevPilot은 **약 1.7 GB**(api 1.5 GB + caddy 128 MB)만 쓴다(§5.2). heap을 미리 점유하지 않는다(`AlwaysPreTouch` 없음) |
| 디스크 | Docker 로그는 컨테이너별 `logging: { driver: json-file, options: { max-size: 10m, max-file: "5" } }`로 compose에서 제한한다(daemon.json을 바꾸지 않는다). 백업은 7일치(≈ 수십 MB) |
| SELinux | disabled. bind mount에 `:Z`/`:z` 라벨을 쓰지 않는다 |

### 3.2 Docker와 firewalld

- Docker는 publish한 포트에 자체 규칙을 넣어 **firewalld public zone과 무관하게 연다.** 그래서 DevPilot의 publish는 `127.0.0.1:18080:80` 하나뿐이고 api는 `expose`만 쓴다.
- 확인:
  ```bash
  sudo ss -ltnp | grep -E ':(18080|8080)\b'   # 18080은 127.0.0.1에만, 8080은 DevPilot 컨테이너가 아니어야 한다
  ```
  로컬 PC에서 `Test-NetConnection <server> -Port 18080` → 실패해야 한다.
- `tailscale0`이 `trusted` zone이므로 Testcontainers가 서버 Docker에 띄운 컨테이너의 publish 포트는 tailnet에서 열린다(`18` §3.1). 이 포트들은 테스트 동안만 존재한다.

### 3.3 리소스 한도 (compose)

| 서비스 | `mem_limit` | JVM | 이유 |
|---|---|---|---|
| api | `1.5g` | `JAVA_TOOL_OPTIONS=-Xmx1g -XX:+ExitOnOutOfMemoryError` | heap 1 GB + non-heap ≤ 0.5 GB. 컨테이너 한도가 heap보다 커야 cgroup OOM kill 대신 `ExitOnOutOfMemoryError`로 종료하고 `restart: unless-stopped`로 재시작된다 |
| caddy | `128m` | — | Caddy + 정적 파일 |

`-Xms`·`AlwaysPreTouch`는 쓰지 않는다(공용 서버에서 기동 즉시 메모리를 점유해 다른 컨테이너를 밀어낸다). 배포 후 1주 동안 `docker stats --no-stream`으로 api RSS가 1.2 GB를 넘지 않는지 본다.

### 3.4 (예약)

v2 초판 §3.4~§3.8(OCI Security List, 부트스트랩, Object Storage)은 폐기했다. 절 번호는 참조 안정성을 위해 남긴다.

---

## 4. TLS · HTTP 헤더

### 4.1 주소

- 앱 주소는 `https://<tailnet-host>`(MagicDNS 이름, `<host>.<tailnet>.ts.net`)다. 구매 도메인·DNS 레코드·AAAA·CAA는 없다.
- backend `APP_BASE_URL`(캘린더 피드 URL 생성)과 Flutter의 API 주소는 이 값을 쓴다. Flutter prod 빌드는 **same-origin**(`API_BASE_URL` 비움 → `/api/v1`)이라 호스트명을 저장소에 넣지 않는다(`18` §6.3).

### 4.2 HTTP 헤더 (Caddy)

헤더 값과 CSP의 기준은 `07-security-and-privacy.md` §9.3, §9.4다. 이 절은 `infra/Caddyfile`에 적용한 위치만 정리한다. 값을 바꿀 때는 07을 먼저 고친다.

| 항목 | 설정 (`infra/Caddyfile`) |
|---|---|
| TLS | **없음.** 사이트 주소 `:80`, `auto_https off`. TLS는 `tailscale serve`가 종료한다(§2.3) |
| 사이트 전체 헤더 | `Strict-Transport-Security: max-age=31536000; includeSubDomains`(07 §9.3 값 그대로. `infra/Caddyfile`·`infra/scripts/smoke-headers.sh`가 이 문자열을 정확히 비교한다), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=()`, `Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Resource-Policy: same-origin`, `Server` 제거(`header -Server`) |
| `/api/*` | `Cache-Control: no-store` — api가 값을 정하지 않은 응답에만 넣는다(`header ?Cache-Control`). 캘린더 피드의 값은 덮어쓰지 않는다 |
| 정적 파일 | `Cache-Control: no-cache` (§5.4) |
| 압축 | `encode zstd gzip` |
| CSP | `Content-Security-Policy-Report-Only` — 07 §9.4의 정책을 한 줄로. `connect-src 'self'`(외부 호스트 없음) |

- 폰트는 번들한다(`18` §6.6). 브라우저 Network 탭에 `fonts.gstatic.com` 요청이 보이면 정책을 넓히지 말고 번들을 고친다.
- 강제 전환 절차와 시점은 07 §9.4를 따른다(Report-Only 위반 0건 확인 → 헤더 이름을 `Content-Security-Policy`로 바꿔 릴리스).
- 배포 후 헤더 비교 스모크 `infra/scripts/smoke-headers.sh https://<tailnet-host>`(07 §9.3, ST-27)를 **로컬 PC(tailnet)에서** 실행한다(§9.3). `GET /`(200, `no-cache`)와 `GET /api/v1/me`(401, `no-store`)의 공통 보안 헤더를 비교하고, 불일치하면 이전 tag로 롤백한다.

---

## 5. 컨테이너 구성

### 5.1 서버 디렉터리

```text
/opt/devpilot/                         750 deploy:deploy   (spec 기준 경로. prepare-server.sh가 만든다)
├── compose.prod.yml                   644 deploy  저장소 infra/compose.prod.yml (prepare-server.sh가 복사·갱신)
├── Caddyfile                          644 deploy  저장소 infra/Caddyfile (prepare-server.sh가 복사·갱신)
├── deploy.sh · backup.sh · healthping.sh · host-check.sh   750 deploy  저장소 infra/scripts/ (prepare-server.sh가 복사·갱신)
├── api.env                            600 deploy  애플리케이션 환경변수 (secret 포함, §6)
├── ops.env                            600 deploy  운영 스크립트 설정: GITHUB_OWNER, HEALTHCHECKS_PING_URL, HEALTHCHECK_BACKUP_URL, HEALTHCHECK_HOST_URL (§5.5)
├── .env                               644 deploy  compose 치환 변수 TAG, API_IMAGE (deploy.sh가 기록. compose가 프로젝트 디렉터리에서 자동으로 읽는다)
├── .last_good_tag                          마지막 정상 배포 tag (deploy.sh가 기록)
├── web/                               755 deploy  Flutter web 정적 파일 (caddy가 /srv/web 으로 읽음. deploy.sh가 web.zip 내용으로 교체)
├── backups/                          2750 deploy:deploy  devpilot-<UTC timestamp>.dump (640 deploy:deploy, 7일)
└── state/                             750 deploy  deploy.lock, last_backup_ok, web-<tag>.zip 사본(최근 2개), host-check 카운터
```

운영자 편의 함수 (`deploy` 사용자의 `~/.bashrc`):

```bash
dc() { docker compose -f /opt/devpilot/compose.prod.yml "$@"; }
# 예: dc ps · dc logs --since 1h api · dc up -d --force-recreate api · dc restart caddy
```

- 변수는 넘기지 않는다. compose가 프로젝트 디렉터리(`/opt/devpilot`)의 `.env`에서 `TAG`·`API_IMAGE`를 읽고, project 이름은 `compose.prod.yml`의 `name: devpilot`이 정한다.
- **`--env-file`을 주면 안 된다.** `--env-file`은 기본 `.env`를 대체하므로 `ops.env`를 지정하면 `TAG`가 비어 `${TAG:?}` 때문에 `dc ps`부터 실패한다.
- 서비스는 `api`와 `caddy` 둘뿐이다(`web` 서비스는 없다 — 정적 파일은 caddy가 bind mount로 제공한다).

서버 접속: `ssh <user>@<server>` → `sudo -iu deploy`(sudo가 없으면 `su -` → `su - deploy`).

### 5.2 `infra/compose.prod.yml`

| 서비스·키 | 값 | 이유 |
|---|---|---|
| project | `devpilot`, network `devpilot` | 다른 사용자의 compose project와 이름이 겹치지 않게 |
| api `image` | `${API_IMAGE:?}:${TAG:?}` | 불변 tag. 두 값 모두 deploy.sh가 `/opt/devpilot/.env`에 기록하고 compose가 프로젝트 디렉터리에서 자동으로 읽는다. 기본값을 두지 않는다(값이 없으면 compose가 바로 멈춘다) |
| api `env_file` | `/opt/devpilot/api.env` | secret은 이미지·저장소에 없다 |
| api `SPRING_PROFILES_ACTIVE` | `prod` | |
| api `JAVA_TOOL_OPTIONS` | `-Xmx1g -XX:+ExitOnOutOfMemoryError` | §3.3 |
| api `extra_hosts` | `host.docker.internal:host-gateway` | 공용 PostgreSQL(호스트 5432) 접근 (§2.4) |
| api `expose` | `8080` | host에 publish하지 않는다 (§3.2) |
| api `read_only` + `tmpfs /tmp` | 256 MB | 파일시스템 쓰기 차단, Tomcat 임시 파일만 허용 |
| api `security_opt`, `cap_drop` | `no-new-privileges:true`, `ALL` | 최소 권한 (daemon 기본값에 의존하지 않는다) |
| api `mem_limit` | `1.5g` | §3.3 |
| api `stop_grace_period` | `40s` | `server.shutdown=graceful` + `timeout-per-shutdown-phase=30s` |
| api `logging` | json-file, `max-size: 10m`, `max-file: "5"` | 컨테이너당 최대 50 MB (§3.1) |
| caddy `image` | `caddy:2.11.4-alpine` | 공식 이미지 그대로(자체 web 이미지를 만들지 않는다). digest 고정은 S2 P1(§7.4) |
| caddy `ports` | **`127.0.0.1:18080:80`** | 호스트 loopback에만. `tailscale serve`가 여기로 프록시한다 |
| caddy `volumes` | `/opt/devpilot/Caddyfile:/etc/caddy/Caddyfile:ro`, `/opt/devpilot/web:/srv/web:ro` | 정적 파일·Caddyfile은 호스트 bind mount(읽기 전용). `web/`은 deploy.sh가 디렉터리 안의 내용만 교체하므로(디렉터리 inode 유지) 컨테이너에 바로 보인다. Caddyfile 갱신은 `prepare-server.sh` 재실행 + `dc restart caddy`다(§2.2) |
| caddy `cap_drop`/`cap_add` | `ALL` / `NET_BIND_SERVICE` | 컨테이너 안 80 바인딩 |
| caddy `mem_limit`, `logging` | `128m`, api와 같음 | |
| `restart` | `unless-stopped` (둘 다) | 서버 재부팅·프로세스 종료 시 자동 기동 |

- named volume이 없다(인증서 저장소 없음 — TLS는 tailscaled). bind mount는 caddy의 두 개뿐이고 SELinux가 disabled라 `:z` 라벨을 쓰지 않는다(§3.1).
- api 컨테이너에는 compose `healthcheck`(이미지에 curl이 없어 `bash /dev/tcp`로 `/actuator/health`를 읽는다, interval 30s·start_period 60s)가 있다. 이것은 `docker ps` 상태 표시용이고, **배포 성공 판단은 deploy.sh가 호스트에서 `http://127.0.0.1:18080/actuator/health`로 따로 한다**(§9.3).

### 5.3 이미지 · 릴리스 자산

| 산출물 | 출처 | 내용 |
|---|---|---|
| 이미지 `ghcr.io/<owner>/devpilot-api:<tag>` | `backend/Dockerfile` | base `eclipse-temurin:25-jre`. **`linux/amd64` + `linux/arm64` 멀티아치**(amd64 = 현재 서버, arm64 = 공개 전환 후보인 OCI Ampere A1, §1.4). Dockerfile 자체에는 아키텍처 의존이 없다. 이미지 안에서 Gradle을 돌리지 않는다. CI가 `./gradlew bootJar`로 만든 `build/libs/devpilot-api.jar`를 복사한다(빌드 컨텍스트 `backend/`). jar 이름은 `build.gradle.kts`의 `tasks.bootJar { archiveFileName = "devpilot-api.jar" }`, `tasks.jar { enabled = false }`와 짝이다(`18` §4.3). 실행 사용자 `app`(uid/gid 10001, 로그인 셸 없음), jar `root:root 0444`. JVM 옵션은 이미지에 넣지 않는다 |
| 자산 `web.zip` (GitHub Release) | release.yml `build-web` job | **Flutter web 정적 파일만** 담는다: `flutter build web` 결과(`app/build/web`)의 내용을 zip 루트에 넣는다(`index.html`이 zip 루트에 있어야 한다). deploy.sh가 `/opt/devpilot/web` 안의 내용을 이것으로 교체한다(§9.3). 이미지가 아니므로 web 쪽 Dockerfile은 없다. **compose.prod.yml·Caddyfile·스크립트는 들어 있지 않다**(§2.2) |

- 이미지와 `web.zip`은 같은 tag로 만들어지고 같이 배포된다. 헤더·라우팅 변경(Caddyfile)과 compose·스크립트 변경은 릴리스 자산에 들어가지 않으므로 `prepare-server.sh` 재실행으로 따로 반영한다(§2.2). CHANGELOG의 `Ops` 절에 그 사실을 적는다(§9.1).
- base image(`eclipse-temurin`, `caddy`) 갱신은 Dependabot `docker` ecosystem(S2 P1 활성화, §7.4) 전까지 수동으로 한다. digest 고정(S2 P1)은 멀티아치 이미지의 **manifest list digest**로 한다.

### 5.4 `infra/Caddyfile` 라우팅

```text
{ auto_https off }
:80 {
  encode zstd gzip
  header { ...(§4.2 공통 헤더)... }
  handle /api/* {
    request_body { max_size 1MB }
    header ?Cache-Control no-store
    reverse_proxy api:8080 { trusted_proxies private_ranges }
  }
  # 중첩 handle: matcher 없는 블록이 마지막에 평가되므로 /actuator/health 만 통과하고 나머지는 404
  handle /actuator* {
    @health path /actuator/health
    handle @health { reverse_proxy api:8080 { trusted_proxies private_ranges } }
    handle { respond 404 }
  }
  handle {
    root * /srv/web
    header Cache-Control no-cache
    try_files {path} /index.html
    file_server
  }
}
```

`trusted_proxies private_ranges`는 `tailscale serve`(127.0.0.1) → docker bridge를 거쳐 오는 요청의 `X-Forwarded-Proto=https`를 backend까지 전달하기 위한 것이다(`server.forward-headers-strategy=framework`, `18` §4.4).

| 요청 경로 | 처리 |
|---|---|
| `/api/*` | `reverse_proxy api:8080`, request body 상한 1 MB (backend 필터 64 KB가 실제 기준, `03` §3.1) |
| `/actuator/health` | `reverse_proxy api:8080` (상세 비노출) |
| `/actuator`, `/actuator/*` (그 외) | `404` |
| 그 밖의 모든 경로 | `/srv/web` 정적 파일, 파일이 없으면 `/index.html` (SPA), `Cache-Control: no-cache` |

Flutter web 산출물(`main.dart.js`, `flutter_bootstrap.js` 등)은 파일명에 해시가 없다. 그래서 정적 파일 전체를 `no-cache`(ETag·Last-Modified 재검증)로 제공한다. 배포 직후 새 버전이 적용된다.

### 5.5 환경 파일 권한과 변경

| 파일 | 권한 | 내용 | 백업 위치 |
|---|---|---|---|
| `api.env` | `600 deploy:deploy` | §6의 prod backend 변수 | password manager 보안 노트 "DevPilot prod api.env" (파일 전체) |
| `ops.env` | `600 deploy:deploy` | 운영 스크립트 설정 4개: `GITHUB_OWNER=<owner>`(deploy.sh가 여기서 `web.zip` 다운로드 URL과 이미지 이름 `ghcr.io/<owner 소문자>/devpilot-api`를 **파생한다** — 이미지 이름·저장소 이름을 따로 두지 않는다), `HEALTHCHECKS_PING_URL`(healthping.sh, §11.2), `HEALTHCHECK_BACKUP_URL`(backup.sh, §8.3, 비우면 ping 생략), `HEALTHCHECK_HOST_URL`(host-check.sh, §11.3, 비우면 ping 생략) | 같은 노트 |

- 형식: `KEY=VALUE` 한 줄씩, 따옴표·공백 없이 쓴다. PEM처럼 여러 줄인 값(`DEVPILOT_DEV_JWT_KEY`)은 `\n`으로 이어 한 줄로 쓴다(앱이 되돌린다). 스크립트는 env 파일을 `source`하지 않고 필요한 키만 `grep`으로 읽는다.
- 값을 바꾼 뒤: `dc up -d --force-recreate api` → `curl -s http://127.0.0.1:18080/actuator/health`
- 파일 내용을 로그·채팅·이슈에 붙여넣지 않는다. `cat api.env` 대신 `grep -c '=' api.env`처럼 확인한다.

---

## 6. 환경변수 · Secret 매트릭스

Secret 열: **Yes** = 노출 시 즉시 교체(§13.3), 개인정보 = secret처럼 취급, 공개 = 브라우저에 노출되는 값.

| 변수 | 소비자 | local | CI | prod | Secret | prod 저장 위치 |
|---|---|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | backend | `local` (`bootRun` 인자) | `test` (`@ActiveProfiles`) | `prod` (compose `environment`) | 아니오 | `compose.prod.yml` |
| `DEVPILOT_AUTH_MODE` | backend `devpilot.security.auth-mode` (`03` §4·§9) | `devtoken` (기본값) | `supabase` (`TestJwksConfig`가 JWKS 주입, `09` §6.2) | `devtoken` (결정 A) | 아니오 | `api.env` |
| `DEVPILOT_DEV_JWT_KEY` | backend devtoken 서명 키 (EC P-256 PEM) | 비움 → 기동 시 생성(재기동 시 기존 토큰 무효) | — | **필수**: `openssl ecparam -genkey -name prime256v1 -noout` 결과(재기동해도 로그인 유지) | **Yes** | `api.env` |
| `DEVPILOT_ALLOWED_EMAILS` | backend `devpilot.security.allowed-emails` | `<your-email>` (`.env`) | `application-test.yml` 고정값 | 소유자 + 초대 사용자 (쉼표, 소문자) | 개인정보 | `api.env` |
| `DEVPILOT_ALLOWED_SUBJECTS` | backend `devpilot.security.allowed-subjects` | 비움 | 비움 | 비움 (Supabase 도입 시 user UUID) | 아니오 | `api.env` |
| `DEVPILOT_LOG_HASH_KEY` | backend `devpilot.security.log-hash-key` (`userRef` HMAC, `03` §8) | 비움 | 테스트 고정값 | `openssl rand -hex 32` | **Yes** | `api.env` |
| `DATABASE_URL` | backend, `backup.sh` | `jdbc:postgresql://<server>:5432/devpilot` (값은 `DevPilot-ops/02-database.md`, **기본값 없음**) | Testcontainers가 주입 | `jdbc:postgresql://host.docker.internal:5432/devpilot` (§2.4) | 아니오 | `api.env` |
| `DATABASE_USERNAME` | backend, `backup.sh` | `devpilot` | Testcontainers | `devpilot` | 아니오 | `api.env` |
| `DATABASE_PASSWORD` | backend, `backup.sh` | DB 비밀번호 | Testcontainers | DB 비밀번호 | **Yes** | `api.env` |
| `DEEPSEEK_API_KEY` | backend `devpilot.ai.deepseek.api-key` (`RestClient`, Spring property로 읽는다) · `aiEval` | 실제 호출이 필요할 때만 `.env`에 (`18` §5.3) | `ai-eval` environment secret만 | DeepSeek 키 (prod·eval 공용 잔액, 결정 E) | **Yes** | `api.env` |
| `DEVPILOT_AI_PROVIDER` | backend `devpilot.ai.provider` | `fake` (실제 호출 시 `deepseek`) | `fake` (`aiEval`은 `deepseek`) | `deepseek` (S3 전에는 `disabled`) | 아니오 | `api.env` |
| `DEVPILOT_AI_MODEL` | backend `devpilot.ai.model` | `deepseek-flash` | — | `deepseek-flash` (DEC-05) | 아니오 | `api.env` |
| `DEVPILOT_AI_MONTHLY_BUDGET_USD` | backend `devpilot.ai.monthly-budget-usd` (서비스 전체, Asia/Seoul 월 경계) | `3` | test profile 고정 `25` | `3` (DEC-06) | 아니오 | `api.env` |
| `CORS_ALLOWED_ORIGINS` | backend `devpilot.web.cors-allowed-origins` | `http://localhost:5173` | 비움 (test profile CORS 없음, `07` §9.1) | 비움 (same-origin) | 아니오 | `api.env` |
| `APP_BASE_URL` | backend `devpilot.web.app-base-url` (캘린더 피드 URL, devtoken issuer) | `http://localhost:5173` (local 기본값) | test profile 고정값 | **필수** `https://<tailnet-host>` (없으면 기동 실패) | 아니오 (tailnet 호스트명은 저장소에 두지 않는다) | `api.env` |
| `DOCKER_HOST` / `TESTCONTAINERS_HOST_OVERRIDE` | Testcontainers·docker CLI (로컬 PC만, 선택) | `tcp://localhost:2375` / `<server tailnet IP>` (`dev-docker-tunnel.ps1`이 안내, `18` §3.1) | — (runner의 Docker) | — | 아니오 (tailnet IP는 저장소에 두지 않는다) | OS 사용자 환경변수 |
| `GITHUB_OWNER` | `deploy.sh` (`web.zip` 다운로드 URL과 `API_IMAGE`를 파생한다) | — | — | GitHub 소유자 이름 (`<owner>`, `[A-Za-z0-9-]+`) | 아니오 | `ops.env` |
| `TAG` | `compose.prod.yml` 이미지 tag | — | — | 배포할 tag (deploy.sh 인자) | 아니오 | deploy.sh가 `/opt/devpilot/.env`에 기록 |
| `API_IMAGE` | `compose.prod.yml` | — | release.yml이 push | `ghcr.io/<owner 소문자>/devpilot-api` | 아니오 | deploy.sh가 `GITHUB_OWNER`로 만들어 `/opt/devpilot/.env`에 기록 |
| `HEALTHCHECK_BACKUP_URL` | `backup.sh` ping (§8.3) | — | — | healthchecks.io ping URL (비우면 ping 생략) | **Yes** (알림을 무력화할 수 있음) | `ops.env` |
| `HEALTHCHECKS_PING_URL` | `healthping.sh` ping (§11.2) | — | — | healthchecks.io ping URL (비우면 ping 생략) | **Yes** | `ops.env` |
| `HEALTHCHECK_HOST_URL` | `host-check.sh` ping (§11.3) | — | — | healthchecks.io ping URL (비우면 출력만) | **Yes** | `ops.env` |
| `AUTH_MODE` (app) | Flutter `--dart-define` | `dev` (`config/local.json`, 커밋 안 함) | `dev` (`config/local.example.json`) | `dev` | 공개 | release.yml의 inline `--dart-define` |
| `API_BASE_URL` (app) | Flutter `--dart-define` | `http://localhost:8080` | 예시값 | **비움** = same-origin | 공개 | release.yml의 inline `--dart-define` |
| `GITHUB_TOKEN` | CI (GHCR push, Release 생성, gitleaks) | — | Actions 자동 발급 | — | Yes (자동 만료) | — |

규칙:
- Secret은 저장소, 이미지, CI 로그에 두지 않는다. GitHub에는 **environment secret**으로만 둔다(`ai-eval` environment의 `DEEPSEEK_API_KEY` 하나). `production` environment는 만들지 않는다(배포가 Actions 밖에서 일어난다, 결정 D).
- 서버 주소·tailnet 호스트명·이메일은 `api.env`와 `DevPilot-ops/`에만 있다.
- 새 변수를 추가하면 이 표, `.env.example`(`18` §5.2), `prepare-server.sh`의 `api.env`·`ops.env` 템플릿을 같이 바꾸고, 서버 `api.env`에 **배포 전에** 추가한다. `ops.env` 템플릿이 바뀌면 `prepare-server.sh`는 기존 파일을 덮어쓰지 않으므로 서버 파일에 직접 줄을 추가한다.
- Supabase 도입 시 추가되는 변수(`SUPABASE_URL`, `SUPABASE_JWT_ALGORITHM`, `SUPABASE_PUBLISHABLE_KEY`)는 §13.8 부록에 있다.

---

## 7. CI/CD

### 7.1 `ci.yml`

트리거: `main`·`developer` push, `main`·`developer` 대상 `pull_request`, `workflow_call`(release.yml이 호출). path filter는 쓰지 않는다 — 필수 status check가 path filter로 건너뛰면 PR이 병합 대기 상태로 남는다.

| Job | Runner | 단계 (순서 = gate 순서) |
|---|---|---|
| `security` | `ubuntu-24.04` | checkout(전체 이력) → **actions-pin-check**(`uses:`가 40자 SHA가 아니면 실패, 07 §11.4. S0 고정 전까지 `continue-on-error: true`) → gitleaks. (Trivy fs 스캔은 S2 P1에 추가, §7.4) |
| `backend` | `ubuntu-24.04` | checkout(전체 이력) → setup-java(Temurin 25) → gradle wrapper-validation → setup-gradle → **migration immutability**(PR에서만: base 대비 `db/migration/V*` 수정이 있으면 실패, `04` §10) → `spotlessCheck` → `compileJava compileTestJava`(main `-Werror`, lockfile) → `checkstyleMain checkstyleTest` → `pmdMain spotbugsMain` → `test`(tag `unit`: 규칙, ArchUnit, web slice) → `integrationTest`(tag `integration`: Testcontainers `postgres:16`, E2E, content 검증, OpenAPI 스냅샷 생성) → `jacocoTestCoverageVerification` → `openApiCheck` → 실패 시 리포트 업로드. 순서·실패 기준은 `09-test-and-quality.md` §14.1 |
| `app` | `ubuntu-24.04` | checkout → `.fvmrc`에서 Flutter 버전 읽기 → flutter-action → `flutter pub get --enforce-lockfile` → `dart format --output=none --set-exit-if-changed lib test` → `bash tool/check_identifiers.sh` → `flutter gen-l10n` + `build_runner build` → `flutter analyze --fatal-infos --fatal-warnings` → `flutter test` → `flutter build web --release --no-web-resources-cdn --csp --dart-define-from-file=config/local.example.json`(빌드 확인용 예시 설정. `config/local.json`·`prod.json`은 `.gitignore` 대상이라 CI에 없다) |
| `content` | `ubuntu-24.04` | PR이면 base 대비 `content/**` 변경 여부 판정(변경 없으면 검증 단계만 생략, job은 성공) → venv + `pyyaml` → `python3 content/tools/validate_content.py` (ERROR 1건이면 실패, `19-content-spec.md`) |

- job은 병렬로 실행한다. **`main`과 `developer` 둘 다** branch protection(ruleset)에 필수 check `security`, `backend`, `app`, `content`를 건다(`09-test-and-quality.md` §14.1). `feature/*` → `developer`는 squash merge, `developer` → `main`은 merge commit(`08` §10.1, `20` DEC-22).
- `app` job은 format check 뒤에 `bash tool/check_identifiers.sh`(08 §2.2 로마자 식별자 정규식, 생성 파일 제외)를 실행한다.
- `backend` job의 `integrationTest`가 `OpenApiSnapshotTest`로 `build/openapi/openapi.yaml`을 만들고, 마지막 `openApiCheck` step이 `docs/api/openapi.yaml`과 비교한다(BL-FND-22, S0).
- `backend`·`app` job은 **S0에서 `backend/gradlew`(+`gradle/wrapper/`)와 `app/.fvmrc`·`app/pubspec.yaml`을 만들기 전에는 통과하지 못한다**(파일이 없어 첫 step에서 멈춘다). S0의 첫 작업이 이 파일 생성이다(`18` §4.1·§6.1, §7.1 체크리스트).
- `security` job의 actions-pin-check는 S0 SHA 고정(§7.5) 전까지 저장소의 `uses:` 23개를 모두 잡아내 실패한다. 필수 check가 첫 push부터 막히지 않도록 이 step에는 **`continue-on-error: true`**를 붙여 두었다(검사 결과는 로그에 남고 job은 통과한다). **S0에서 SHA 고정을 끝낸 뒤 `ci.yml`의 `continue-on-error` 줄과 그 위 `TODO(S0)` 주석을 지운다**(§7.5-3).
- seed YAML 검증은 두 곳에서 같은 규칙 ID로 한다: `content` job의 참조 검증기(`content/tools/validate_content.py`)와 backend 기동·`integrationTest`의 Java `ContentValidator` (`04` §9, `19-content-spec.md`).
- PR에서는 같은 ref의 이전 실행을 취소한다(`concurrency`).

### 7.2 `release.yml` (구 `deploy.yml`)

트리거: `v*.*.*` tag push(`main`의 커밋). **서버 배포는 하지 않는다** — 이미지를 만들어 GHCR에 올리는 것까지다(결정 D). 배포는 §9.2대로 운영자가 서버에서 실행한다.

| Job | Runner | 단계 |
|---|---|---|
| `ci` | (reusable `ci.yml`) | §7.1 전체 |
| `build-api` | `ubuntu-24.04` | tag 형식·`main` 조상 여부 검증 → setup-java → setup-gradle → `./gradlew bootJar` → 이미지 이름 계산(owner 소문자) → **setup-qemu + setup-buildx**(멀티아치 빌드에 필요) → docker/login-action(GHCR, `GITHUB_TOKEN`) → docker/build-push-action(**`linux/amd64,linux/arm64`**, `push: true`, tags `<tag>`, OCI 라벨 source·revision·version) |
| `build-web` | `ubuntu-24.04` | tag 형식 검증 → `app/.fvmrc`에서 Flutter 버전 읽기 → flutter-action → `flutter pub get --enforce-lockfile` → `gen-l10n` + `build_runner build` → `flutter build web --release --no-web-resources-cdn --csp --dart-define=AUTH_MODE=dev --dart-define=API_BASE_URL=`(**inline dart-define**. `app/config/prod.json`은 `.gitignore`(`app/config/*.json`)에 걸려 커밋되지 않으므로 파일을 쓰지 않는다) → `app/build/web` 내용을 `web.zip`으로 압축(zip 루트에 `index.html`) → `gh release create <tag> --generate-notes`(Release가 없을 때만) → `gh release upload <tag> web.zip --clobber` |
| `summary` | `ubuntu-24.04` | needs `build-api`·`build-web`(`if: always()`) → tag, 두 job의 결과, 배포 명령을 Job summary 표로 출력하고 **둘 중 하나라도 실패하면 job을 실패시킨다** |

- environment·secret·variable이 필요 없다(`GITHUB_TOKEN`만: `build-api`에 `packages: write`, `build-web`에 `contents: write`).
- Release notes는 `--generate-notes`(GitHub 자동 생성)다. `CHANGELOG.md`는 저장소에서 사람이 관리하며(§9.1) 워크플로가 읽지 않는다.
- `web.zip`에 infra 파일이 들어가지 않으므로, compose·Caddyfile·스크립트가 바뀐 릴리스는 배포 전에 서버에서 `prepare-server.sh`를 다시 실행한다(§2.2).
- 이미지 스캔(Trivy)은 S2 P1에서 `build-api` 뒤에 추가한다(§7.4).
- **arm64는 QEMU 에뮬레이션으로 빌드하므로 `build-api`가 amd64 단독일 때보다 몇 분 더 걸린다.** 그 대가로 공개 전환 시 OCI Ampere A1(arm64)에서 같은 tag를 그대로 pull할 수 있다(§1.4). arm64가 필요 없어지면 `platforms`에서 빼고 두 setup step을 지우면 된다.
- Job summary 항목: tag, `api image (linux/amd64 + linux/arm64 → GHCR)` 결과, `web.zip (GitHub Release 자산)` 결과, 배포 명령. 운영자는 둘 다 `success`인 것을 확인하고 배포한다(§9.2).

### 7.3 `ai-eval.yml`

| 항목 | 값 |
|---|---|
| 트리거 | `workflow_dispatch`만 (input `suite` = `[a-z0-9-]+`, `repeat` **1~5**(기본 3), `max_cost_usd` **0.1~0.5**(기본 0.5)). 범위를 벗어나면 첫 step `Validate inputs`가 실패한다. PR label 트리거는 두지 않는다(결정 E: prod와 같은 잔액을 쓰므로 수동으로만) |
| environment | `ai-eval` — Required reviewers = 저장소 소유자 (실행마다 승인), secret `DEEPSEEK_API_KEY` |
| 단계 | 입력 검증 → setup-java → setup-gradle → `./gradlew aiEval -PevalSuite=<suite> -PevalRepeat=<n> -PevalMaxCostUsd=<usd>` (그 밖의 옵션 `evalCase`, `evalModel`, `evalUpdateBaseline`은 `evals/README.md`) → `backend/build/reports/ai-eval/` 업로드 → `summary.md`를 Job summary로 게시 |
| 비용 통제 | `-PevalMaxCostUsd`(0.1~0.5, 기본 0.5)로 실행 전 상한 + DeepSeek 선불 잔액(§11.6). 실행 전 예상 비용 출력은 `17-ai-integration.md`의 eval 규칙을 따른다 |
| 금지 | fork PR에서 실행하지 않는다 (`workflow_dispatch`만이므로 해당 없음) |

### 7.4 `dependabot.yml` · 보류한 도구

| 파일 | 설정 |
|---|---|
| `dependabot.yml` | `gradle`(/backend), `pub`(/app), `github-actions`(/). 매주 월요일 09:00 KST. minor/patch는 생태계별 그룹 PR 1개, major는 개별 PR. 대상 브랜치 `developer` |
| 저장소 설정 | Settings → Code security: Dependabot alerts·security updates, Secret scanning, **Push protection** 켜기 (public 저장소 무료, DEC-04) |

S2 P1로 보류(BL-OPS 신설 항목, S0 일정 보호): Trivy fs·image 스캔, CodeQL(`codeql.yml`은 seed에서 제외), 컨테이너 base image digest 고정 + Dependabot `docker`/`docker-compose`. 모두 무료이며 추가 시 이 절과 §7.1·§7.2를 갱신한다.

Dependabot PR 처리 규칙: CI green이면 minor/patch 그룹은 주 1회 `developer`에 병합한다. major는 해당 라이브러리의 migration guide를 읽고 `18` §1 버전 표를 함께 고친다.

### 7.5 Action commit SHA 고정 (S0)

seed workflow의 `uses:` 줄은 major tag와 `# TODO(S0): pin to commit SHA` 주석을 가진다. S0에서 전부 SHA로 바꾼다. **tag는 옮겨질 수 있다** — 2026-03-19 `aquasecurity/trivy-action`의 tag 75개가 악성 커밋으로 force-push된 사고가 있었다.

1. action마다 최신 릴리스 tag와 SHA를 확인한다:
   ```bash
   repo=actions/checkout
   tag=$(gh api "repos/$repo/releases/latest" --jq .tag_name)
   gh api "repos/$repo/git/ref/tags/$tag" --jq '.object.type + " " + .object.sha'
   # type이 "tag"(annotated tag)이면 커밋까지 따라간다
   gh api "repos/$repo/git/tags/<위 sha>" --jq .object.sha
   ```
   `gradle/actions/setup-gradle`은 `gradle/actions` 저장소의 SHA를 쓴다.
2. 릴리스 노트와 보안 공지를 확인한다.
3. `uses: actions/checkout@<40자리 SHA> # v7.0.1` 형식으로 바꾸고 TODO 주석을 지운다. 대상은 로컬 reusable workflow(`uses: ./.github/workflows/ci.yml`)를 뺀 **23개**다(`docker/setup-qemu-action`·`docker/setup-buildx-action` 포함). 같은 커밋에서 `ci.yml`의 `actions-pin-check` step에 붙은 **`continue-on-error: true`와 그 위 `TODO(S0)` 주석도 지운다** — 이 줄이 남아 있으면 고정이 안 된 action이 있어도 `security` job이 통과한다.
4. 검사 (결과 0줄이어야 한다):
   ```bash
   # ci.yml actions-pin-check와 같은 패턴 (주석·step 이름의 "uses" 문자열은 대상이 아니다)
   grep -rnE '^[[:space:]]*-?[[:space:]]*uses:' .github/workflows \
     | grep -vE 'uses:[[:space:]]*\./' \
     | grep -vE '@[0-9a-f]{40}([[:space:]]|$)'
   grep -rn "TODO(S0)" .github/
   ```
5. Settings → Actions → General → Action permissions → **Require actions to be pinned to a full-length commit SHA** 켜기.
6. 이후 갱신은 Dependabot `github-actions`가 SHA와 버전 주석을 함께 바꾼다. 컨테이너 base image digest 고정은 S2 P1(§7.4).

---

## 8. 백업 · 복구

### 8.1 정책 (결정 C)

| 항목 | 값 |
|---|---|
| 대상 | DB `devpilot` 전체 (`devpilot` 스키마 + `flyway_schema_history`) |
| 도구 | `pg_dump` 16 (`postgres:16` 컨테이너, `--network host`), `--format=custom` |
| 암호화 | 없음. 파일은 tailnet 안의 서버와 로컬 PC에만 존재한다. 오브젝트 스토리지·`age`·`rclone`은 쓰지 않는다 |
| 1차 저장소 | 서버 `/opt/devpilot/backups/devpilot-<UTC timestamp>.dump` (**640 deploy:deploy**. `backups/`가 setgid 2750이라 `deploy` 그룹에 든 운영자 계정이 scp로 읽는다), **7일** 보관 |
| 2차 사본 | 로컬 PC `%USERPROFILE%\DevPilot-backups\` — `infra/scripts/pull-backup.ps1`로 **주 1회** 최신 파일 pull, **8주** 보관. 같은 호스트의 디스크는 백업이 아니므로 이 사본이 실제 재해 대비다 |
| 주기 | 매일 **19:00 UTC** (KST 04:00). 앱 스케줄 job이 끝난 뒤다 — `CoachContentPurgeJob` 18:20 UTC, `RetentionCleanupJob` 18:30 UTC(`03` §6). systemd timer, 무작위 지연 5분(`RandomizedDelaySec=300`), `Persistent=true` |
| 배포 전 백업 | **opt-in이다.** `deploy.sh <tag> --backup-first`를 줄 때만 같은 backup.sh를 먼저 실행하고, 실패하면 아무것도 바꾸지 않고 중단한다(DB가 작아 수 초, §9.3). 플래그가 없으면 백업하지 않는다. **migration이 포함된 릴리스에는 반드시 붙인다**(§9.2) — 서버에 git 저장소가 없어 자동 감지를 할 수 없다 |
| 감시 | healthchecks.io check `devpilot-backup` (§11.2) |
| 적용 시점 | 스크립트는 S0 seed에 있다. **timer 활성화와 첫 pull은 실사용 시작(M1 완료 = S3 완료) 전, S2 안에서 한다** — S2 완료 후 Today·Review를 먼저 쓰기 시작하는 경우(선택, `11` §3)에도 실데이터보다 백업이 먼저다. 복구 리허설 기록은 S6에 완성한다 |

### 8.2 `backup.sh` · `pull-backup.ps1`

`backup.sh`(서버, `deploy`, 입력 `/opt/devpilot/api.env`의 `DATABASE_*`와 `ops.env`의 `HEALTHCHECK_BACKUP_URL`(선택)): 입력 파일 검증 → healthchecks `/start` → `DATABASE_URL`(JDBC) 파싱 → `docker run --rm --network host -e PGPASSWORD postgres:16 pg_dump -h 127.0.0.1 -p 5432 -U devpilot -Fc devpilot > backups/devpilot-<ts>.dump.part` → 크기 ≥ 1 KB 확인 → `.part` 제거(원자적 rename) → 7일 지난 파일 삭제(**이번 백업이 성공했을 때만**, 보관 일수는 스크립트 상수 7) → `state/last_backup_ok` 기록 → healthchecks 성공 ping. 어느 단계든 실패하면 healthchecks `/fail`을 보내고 non-zero로 끝난다. ping URL이 비어 있으면 ping만 생략한다(S0~S1, timer 활성화 전에도 deploy.sh가 호출한다).

`pull-backup.ps1`(로컬 PC, PowerShell): `ssh <user>@<server> ls -t /opt/devpilot/backups/*.dump | head -1` → `scp`로 `%USERPROFILE%\DevPilot-backups\`에 복사(같은 이름이 있으면 건너뜀) → 8주 지난 로컬 파일 삭제 → 결과 1줄 출력. 서버 주소·계정은 `-Server` 인자 또는 `$env:DEVPILOT_SERVER_SSH`(`user@host`)로 받고 스크립트에 하드코딩하지 않는다. 종료 코드 0 성공 / 1 ssh·scp 실패 또는 백업 파일 없음 / 2 사용법·도구 없음. Windows 작업 스케줄러에 주 1회(일요일 21:00) 등록한다.

### 8.3 설치 · 활성화 (S2)

1. healthchecks.io → check `devpilot-backup`: Period 1 day, Grace 2 hours, 이메일 알림 → ping URL 복사
2. 서버 `/opt/devpilot/ops.env`에 추가:
   ```dotenv
   HEALTHCHECK_BACKUP_URL=https://hc-ping.com/<uuid>
   ```
3. 활성화와 첫 실행 (`prepare-server.sh`는 `api.env`의 `DATABASE_PASSWORD`가 채워져 있으면 이미 켜 둔다 — 그때는 확인만 한다):
   ```bash
   sudo systemctl enable --now devpilot-backup.timer
   sudo systemctl start devpilot-backup.service
   journalctl -u devpilot-backup.service -n 30 --no-pager      # "백업 완료"
   systemctl list-timers devpilot-backup.timer
   ls -l /opt/devpilot/backups/
   ```
4. 로컬 PC에서 `pull-backup.ps1` 1회 실행 → `%USERPROFILE%\DevPilot-backups\`에 파일 확인 → 작업 스케줄러 등록.

### 8.4 복구 방식

| 상황 | 방식 | 절차 |
|---|---|---|
| 월간 리허설 | 서버 Docker에 격리 PostgreSQL을 띄워 복원 | §8.5 |
| 운영 데이터 일부 손상 (잘못된 migration·버그) | 격리 DB에 복원 → 필요한 행을 SQL로 비교·복사 | §13.2.2 |
| DB `devpilot` 전체 손실·손상 | 같은 인스턴스의 `devpilot` DB에 `pg_restore --clean` | §13.2.3 `db-restore` |
| 서버 전체 손실 | 새 서버 준비(§2) → 로컬 PC의 2차 사본으로 `db-restore` | §13.6 `server-restore` |

### 8.5 월간 복구 리허설

매월 첫째 주. 로컬 PC에서 서버 Docker를 터널로 쓴다(`18` §3.1 `dev-docker-tunnel.ps1`). 로컬 Docker Desktop은 필요 없다.

1. 최신 사본 확인: `pull-backup.ps1` 실행 → `%USERPROFILE%\DevPilot-backups\`의 최신 파일을 `restore.dump`로 복사
2. 격리 DB 기동과 복원(`DOCKER_HOST`가 서버를 가리키는 터미널):
   ```powershell
   docker run -d --name devpilot-restore -e POSTGRES_PASSWORD=restore -p 55432:5432 postgres:16
   Start-Sleep -Seconds 5
   docker cp restore.dump devpilot-restore:/tmp/restore.dump
   docker exec devpilot-restore pg_restore --no-owner --no-privileges --exit-on-error -U postgres -d postgres /tmp/restore.dump
   ```
   (`-p 55432:5432`는 서버에서 publish되며 `tailscale0`이 trusted zone이라 로컬 PC에서 `<server>:55432`로 닿는다. 리허설이 끝나면 즉시 지운다.)
3. 검증 SQL (`docker exec -it devpilot-restore psql -U postgres`):
   ```sql
   select version, description, success from devpilot.flyway_schema_history order by installed_rank desc limit 3;
   select count(*) as app_users from devpilot.app_user;
   select count(*) as events, max(occurred_at) as last_event from devpilot.learning_event;   -- V4(S2) 이후
   select count(*) as review_items from devpilot.review_item;                                -- V4(S2) 이후
   ```
   합격 기준: flyway 최신 version = 운영에 배포된 최신 migration, `success` 모두 true, `app_user` 수 = 운영 DB 조회값, `last_event`가 백업 시각 기준 48시간 이내(사용하지 않은 기간은 예외로 기록).
4. 분기 1회 추가: 복원 DB로 애플리케이션 기동(`ddl-auto=validate`) 확인
   ```powershell
   cd backend
   $env:DATABASE_URL="jdbc:postgresql://<server>:55432/postgres"; $env:DATABASE_USERNAME="postgres"; $env:DATABASE_PASSWORD="restore"
   ./gradlew bootRun
   ```
   (`.env`보다 프로세스 환경변수가 우선한다. 확인 후 터미널을 닫는다.)
5. 정리: `docker rm -f devpilot-restore`, `Remove-Item restore.dump`.
6. 기록: `docs/ops/restore-rehearsal-log.md`에 한 줄 추가한다(아래 행은 형식 예시다).

| 날짜 | 백업 파일 | 백업 시각 (UTC) | pg_restore | flyway 최신 | app_user | learning_event / 최신 | 앱 기동 | 소요 | 결과·조치 |
|---|---|---|---|---|---|---|---|---|---|
| 2026-11-02 | `devpilot-20261101T183212Z.dump` | 2026-11-01 18:32 | OK | V4 | 2 | 1,204 / 2026-11-01 13:10 | — | 10분 | 정상 |

---

## 9. 릴리스 · 배포 · 롤백

### 9.1 버전 · tag · CHANGELOG

| 규칙 | 내용 |
|---|---|
| tag 형식 | **`v0.<sprint>.<patch>`** — S0 `v0.0.x`, S1 `v0.1.x`, … S7 `v0.7.x`. Later 작업은 `v0.8.x`부터. `1.0.0` 시점은 ADR로 정한다 |
| tag 대상 | `main`의 커밋만 (release.yml이 검증). `developer` → `main` PR(merge commit) 뒤에 tag를 만든다 |
| tag 생성 | `git tag -a v0.2.0 -m "S2: Today, Session, Review, PWA"` → `git push origin v0.2.0` |
| 불변 | tag를 지우거나 다른 커밋으로 옮기지 않는다. 잘못 배포했으면 patch를 올린다 |
| CHANGELOG | 저장소 루트 `CHANGELOG.md`, tag마다 섹션 1개. tag push 전에 main에 커밋한다 |
| migration 포함 릴리스 | **`deploy.sh <tag> --backup-first`로 배포한다.** 백업은 opt-in이라 플래그가 없으면 실행되지 않는다(§8.1, §9.3). 백업이 실패하면 아무것도 바꾸지 않고 중단한다. CHANGELOG `Migrations` 절은 사람이 호환성을 확인한 기록이고, 배포자가 이 절을 보고 플래그를 붙인다 |

`CHANGELOG.md` 섹션 형식:

```markdown
## [v0.2.0] - <YYYY-MM-DD, tag를 만든 날>
### Added
- Today 생성·task 상태 (FR-07)
### Changed
### Fixed
### Migrations
- V4__learning_today_review.sql — 직전 릴리스와 호환: 예
### Ops
- api.env 추가 변수: 없음
- infra 파일 변경(compose.prod.yml / Caddyfile / scripts): 없음   ← 있으면 배포 전 prepare-server.sh 재실행 (§2.2)
- 배포 명령: deploy.sh v0.2.0 --backup-first   ← Migrations 절이 비어 있으면 플래그 없이
```

릴리스 전 확인:
- [ ] `main`의 CI green
- [ ] `CHANGELOG.md` 작성
- [ ] 새 환경변수가 있으면 서버 `api.env`에 먼저 추가 (§6)
- [ ] migration이 §10 규칙(직전 릴리스와 호환)을 지키는지 PR에서 확인
- [ ] `docs/api/openapi.yaml`이 최신 (`openApiCheck` 통과)

### 9.2 배포 흐름 (결정 D)

```text
git push origin vX.Y.Z
  └─ release.yml
       ├─ ci (security, backend, app, content)             ~10분
       ├─ build-api (amd64+arm64) ─ bootJar → image → GHCR push
       ├─ build-web ─ flutter build web → web.zip → GitHub Release vX.Y.Z (--generate-notes)
       └─ summary ─ Job summary (두 job 결과)

운영자 (로컬 PC, tailnet):
  # infra 파일(compose.prod.yml·Caddyfile·scripts)이 바뀐 릴리스면 먼저: prepare-server.sh 재실행 (§2.2)
  ssh <user>@<server> sudo -u deploy /opt/devpilot/deploy.sh vX.Y.Z                  # 서버에서 실행 (결정 D)
  ssh <user>@<server> sudo -u deploy /opt/devpilot/deploy.sh vX.Y.Z --backup-first   # migration이 있으면 (§9.1)
  bash infra/scripts/smoke-headers.sh https://<tailnet-host>                          # 로컬 PC에서 (Git Bash)
```

release.yml의 Job summary가 녹색인 것을 확인한 뒤 배포한다. Actions는 서버에 접근하지 않는다(Tailscale OAuth·ACL 태그 불필요). Actions→tailnet 자동 배포는 Later(BL 신설 항목).

배포 전 판단 두 가지:

| 릴리스에 있는 것 | 추가로 할 일 |
|---|---|
| DB migration (`CHANGELOG`의 `Migrations` 절) | `deploy.sh <tag> **--backup-first**` — 백업은 opt-in이다 (§8.1) |
| `infra/` 파일 변경 (compose.prod.yml·Caddyfile·scripts) | 배포 **전에** 서버에서 `prepare-server.sh` 재실행. Caddyfile이 바뀌었으면 배포 후 `dc restart caddy` (§2.2) |

### 9.3 `deploy.sh` 동작

```text
deploy.sh <tag> [--backup-first] [--no-pull]
 0. 플래그는 이 둘뿐이다. 그 밖의 `-`로 시작하는 인자나 두 번째 tag는 usage를 출력하고 exit 2
 1. tag 형식 검증(v0.<sprint>.<patch>), deploy 사용자인지 확인,
    compose.prod.yml·Caddyfile·ops.env·api.env 존재 확인, api.env 권한이 정확히 '600 deploy'인지 확인,
    ops.env의 GITHUB_OWNER 읽기 → API_IMAGE = ghcr.io/<owner 소문자>/devpilot-api,
    flock(state/deploy.lock, 동시 배포 차단)
 2. previous = .last_good_tag
 3. --backup-first 일 때만: backup.sh 실행, 실패 시 exit 1 (변경 없음). 플래그가 없으면 백업하지 않는다
 4. activate(<tag>)
    a. docker pull <API_IMAGE>:<tag>          (--no-pull이면 생략. 단, 이미지가 없으면 무시하고 pull한다)
    b. web.zip 확보: state/web-<tag>.zip 사본이 있으면 그것을, 없으면
       curl https://github.com/<owner>/DevPilot/releases/download/<tag>/web.zip
       → 임시 디렉터리에 풀고 index.html 이 있는지 확인(없으면 사본을 지우고 실패)
       → /opt/devpilot/web 안의 내용만 교체(디렉터리 inode는 유지 — caddy의 bind mount가 보고 있다)
    c. /opt/devpilot/.env 에 TAG=<tag>, API_IMAGE=... 기록
    d. docker compose -f compose.prod.yml up -d --remove-orphans
    e. health poll: http://127.0.0.1:18080/actuator/health, 3초 간격 최대 60초, "status":"UP"
 5. 성공 → .last_good_tag = <tag> → 이 저장소의 이미지·web.zip 사본 정리(<tag>와 previous 2개만 남긴다)
    → DEPLOY_RESULT=success, exit 0
 6. 실패 → api 로그 200줄 출력 → activate(previous, --no-pull) → 성공 DEPLOY_RESULT=rolled_back exit 10
                                                             → 실패 DEPLOY_RESULT=rollback_failed exit 11
    previous가 없거나 <tag>와 같으면 롤백을 시도하지 않는다 → DEPLOY_RESULT=failed_no_previous exit 12
```

| exit | `DEPLOY_RESULT` | 서버 상태 |
|---|---|---|
| 0 | `success` | 새 tag로 동작 중 |
| 1 | (없음) | 사전 단계 실패 — 아무것도 바꾸지 않았다 |
| 2 | (없음) | 사용법 오류 — 아무것도 바꾸지 않았다 |
| 10 | `rolled_back` | 이전 tag로 동작 중. 원인 조사 후 patch 릴리스 (§13.1) |
| 11 | `rollback_failed` | **중간 상태. 수동 복구가 필요하다** (§13.1) |
| 12 | `failed_no_previous` | 되돌릴 tag가 없어 롤백을 시도하지 않았다. 첫 배포가 실패한 경우다 (§13.1) |

- **deploy.sh는 `compose.prod.yml`·`Caddyfile`·운영 스크립트를 갱신하지 않는다.** 자기 자신도 교체하지 않는다. 이 파일들은 `prepare-server.sh` 재실행으로만 바뀐다(§2.2).
- 외부 HTTPS 확인은 배포 스크립트가 하지 않는다(서버에서 tailnet 주소로 요청하면 `tailscale serve`를 거치지 않는다). 운영자가 로컬 PC에서 `smoke-headers.sh`로 확인한다(§4.2).
- 배포 중 API 재시작 동안(약 20~40초) 요청은 실패할 수 있다. web 정적 파일도 교체하는 1초 미만 동안 404가 날 수 있다. 사용자 3명 이하이므로 무중단 배포를 하지 않는다.
- 롤백(`activate(previous)`)은 `state/web-<tag>.zip` 사본과 서버에 남은 이전 이미지를 쓰므로 GitHub에 접근하지 않는다. 사본은 최근 2개 tag만 남는다.
- 서버에 git clone을 상시로 두지 않는다. 배포에 필요한 것은 이미지와 `web.zip`뿐이고, infra 파일 갱신 때만 임시로 clone한다(§2.2).

### 9.4 롤백

| 종류 | 트리거 | 방법 |
|---|---|---|
| 자동 | health 60초 실패 | deploy.sh가 `.last_good_tag`로 재활성화 (§9.3-7) |
| 수동 (배포는 성공했으나 기능 문제) | 운영자 판단 | 서버에서 `deploy.sh <이전 tag> --no-pull`. 이미지·`web.zip` 사본이 정리됐으면 `--no-pull` 없이(GHCR·Release에서 다시 받는다) |
| 오래된 tag | — | GHCR 이미지와 Release 자산이 남아 있는 한 언제든 `deploy.sh <tag>`. GHCR 이미지·Release 자산은 지우지 않는다 |

`--skip-backup` 같은 플래그는 없다. 백업은 `--backup-first`를 줄 때만 실행되므로 롤백·재활성화에는 아무것도 붙이지 않는다.

**롤백은 애플리케이션만 되돌린다. DB migration은 되돌리지 않는다.** 그래서 모든 migration은 직전 릴리스 앱과 호환되어야 한다(§10). 데이터 자체가 손상됐으면 §13.2로 복구한다.

---

## 10. Flyway 운영 규칙

1. **실행 시점**: Flyway는 애플리케이션 기동 시 실행한다(Spring Boot auto-configuration). 단일 인스턴스이므로 별도 migration job을 두지 않는다.
2. **적용된 migration 수정 금지** (`04` §10). 체크섬이 다르면 기동이 실패한다. 수정은 새 `V{n}`으로 한다. 운영에서 `flyway repair`를 쓰지 않는다. 불가피하면 incident로 기록하고 ADR을 남긴다.
3. **직전 릴리스와 호환 (expand → contract)**: 새 migration 적용 후에도 **직전 tag의 앱이 정상 기동·동작**해야 한다.
   - 허용: 테이블·인덱스 추가, nullable 컬럼 추가, default가 있는 NOT NULL 컬럼 추가, CHECK 값 추가
   - 2단계: 컬럼·테이블 삭제, 이름 변경, 타입 축소, CHECK 값 제거 — (1) 코드가 쓰지 않는 릴리스 배포 (2) 다음 릴리스의 migration에서 제거 (`04` §10)
   - 직전 앱은 자신이 모르는 이후 버전 migration 기록을 무시하고 기동한다(Flyway 기본 `ignoreMigrationPatterns=*:future`). **SP-2에서 migration 추가 후 이전 이미지 기동을 1회 확인한다.**
4. **트랜잭션**: PostgreSQL DDL은 트랜잭션 안에서 실행되므로, 실패한 migration은 통째로 롤백되고 기록되지 않는다. 이 경우 앱 기동이 실패하고 deploy.sh가 자동 롤백한다. 트랜잭션 밖에서만 되는 문장(`create index concurrently` 등)은 쓰지 않는다 — 테이블이 작아서 필요 없다.
5. **lock**: 큰 테이블 rewrite(타입 변경 등)를 피한다. 필요한 migration은 첫 줄에 `set lock_timeout = '5s';`를 둔다.
6. **배포 전 백업**: 백업은 opt-in이다. migration이 포함된 릴리스는 `deploy.sh <tag> --backup-first`로 배포한다 (§8.1, §9.1, §9.2). 서버에 git 저장소가 없어 스크립트가 migration 포함 여부를 스스로 판단하지 못하므로, CHANGELOG의 `Migrations` 절을 보고 사람이 플래그를 붙인다.
7. **스키마 생성**: `spring.flyway.create-schemas=true`가 `devpilot` 스키마를 만들고 그 안에 `flyway_schema_history`를 둔다. `V1__baseline.sql`은 `create schema if not exists devpilot;`를 쓴다 (`18` §4.4).
8. **안전 설정** (`application.yml`): `clean-disabled: true`, `baseline-on-migrate: false`, `out-of-order: false`, `validate-migration-naming: true`, `ddl-auto: validate`.
9. **검증 순서**: PR CI의 Testcontainers(`postgres:16`) 테스트(빈 DB → 전체 migration → `validate` 기동) → 배포 → `flyway_schema_history` 확인(`docker exec` psql 또는 로컬 DB 클라이언트).
10. **실패한 migration 수정**: 운영 `flyway_schema_history`에 `success=true` 행이 없는 migration만 같은 버전 번호로 고칠 수 있다. 개발 DB에 이미 적용됐으면 개발 DB의 `devpilot` 스키마를 초기화한다 (`18` §5.3).
11. `database/schema.sql` 스냅샷을 migration과 함께 갱신한다 (`04` §10).
12. **PostgreSQL 16 기준**: 17 전용 문법·함수를 쓰지 않는다. Testcontainers가 16이므로 CI가 잡는다.

---

## 11. 모니터링 · 알림

| 대상 | 방법 | 주기 | 알림 |
|---|---|---|---|
| API 가용성 | 서버 `healthping.sh`(systemd timer) → healthchecks.io `devpilot-health` | 5분 (grace 10분) | 이메일 |
| 백업 | healthchecks.io `devpilot-backup` | 1일 (grace 2시간) | 이메일 |
| 호스트 (디스크, 컨테이너 상태·재시작, health, tailscale serve·인증서, 백업 최신성) | `host-check.sh` → healthchecks.io `devpilot-host`. **systemd 유닛은 아직 없다 — S6에 추가한다**(§11.3). 그 전에는 월간 점검에서 수동 실행 | (S6 이후) 1시간 (grace 30분) | 이메일 |
| 애플리케이션 일일 요약 | `RetentionCleanupJob` 끝의 INFO 1줄 (`03` §8) | 매일 | 없음 (주간 점검) |
| 러버덕 방치 세션 정리 | 앱 `StaleRubberDuckJob`(rubberduck 모듈, `0 25 * * * *` = 매시 25분 UTC, `03` §6): `status = IN_PROGRESS`이고 `started_at < now − devpilot.rubberduck.stale-after`(24h)인 세션 → `ABANDONED`. AI를 부르지 않고 복습 카드·학습 이벤트도 만들지 않는다. 실행마다 INFO 1줄(처리 건수), 사용자 단위 실패는 `JOB_FAILED` WARN | 매시 | 없음 (주간 점검 §11.4) |
| DeepSeek 잔액·비용 | 앱 `AiBalanceCheckJob`(매시 `GET /user/balance`) → `min-balance-usd` 이하이면 WARN 로그 + `aiStatus=BALANCE_EXHAUSTED`; 앱 예산 가드; `ai_call_log` 주간 합계 | 매시 / 주 1회 | 앱 `aiStatus`, 로그 |
| Tailscale | 노드 상태(`tailscale status`), 인증서 만료(`host-check.sh`) | 매시 | 이메일(host-check) |

외부 uptime 서비스(UptimeRobot 등)는 tailnet 주소에 닿지 못하므로 쓰지 않는다. 대신 서버 안에서 5분마다 health를 확인해 healthchecks.io로 "살아 있음"을 보내고, **끊기면** healthchecks.io가 메일을 보낸다(서버·tailscaled·api 어느 것이 죽어도 감지된다).

알림 이메일은 모두 소유자 이메일 한 곳으로 받고, 메일 필터로 "DevPilot-Ops" 라벨을 붙인다. 스팸함으로 가지 않는지 설정 직후 테스트 알림으로 확인한다.

### 11.1 `healthping.sh` (S0, `infra/scripts/healthping.sh`)

요지(전체는 `infra/scripts/healthping.sh`가 기준):

```bash
#!/usr/bin/env bash
# DevPilot health ping: local health -> healthchecks.io (dead-man switch)
set -euo pipefail
url="$(grep -E '^HEALTHCHECKS_PING_URL=' /opt/devpilot/ops.env | tail -n 1 | cut -d '=' -f 2-)"
[[ -n "${url}" ]] || exit 0          # URL이 비면 ping 없이 0으로 끝난다 (첫 배포 전 timer가 실패 로그를 남기지 않게)
if curl -fsS -m 10 http://127.0.0.1:18080/actuator/health | grep -q '"status":"UP"'; then
  curl -fsS -m 10 --retry 3 -o /dev/null "${url}"; exit 0
else
  curl -fsS -m 10 --retry 3 -o /dev/null --data-binary "health not UP $(date -u +%FT%TZ)" "${url}/fail"; exit 1
fi
```

systemd `devpilot-healthping.timer`: `OnCalendar=*:0/5`, `AccuracySec=30s`, `Persistent=false`, service `User=deploy`, `Type=oneshot`. **`prepare-server.sh`가 설치하면서 바로 `enable --now` 한다**(URL이 비면 스크립트가 스스로 건너뛰므로 안전하다). §11.2에서는 ping URL만 채우면 된다.

### 11.2 healthchecks.io

- 서비스: **healthchecks.io Hobbyist(무료)** — check 20개 (2026-09-17 확인)
- `devpilot-health`: Period 5 minutes, Grace 10 minutes — `healthping.sh`. 활성화: `/opt/devpilot/ops.env`에 `HEALTHCHECKS_PING_URL=…`을 채우면 끝이다(timer는 `prepare-server.sh`가 이미 켜 두었다, §11.1). 확인: `systemctl list-timers devpilot-healthping.timer`, `journalctl -u devpilot-healthping.service -n 10` (S0 첫 배포 직후)
- `devpilot-backup`: Period 1 day, Grace 2 hours — `backup.sh`가 `/start`, 성공, `/fail`을 보낸다 (S2)
- `devpilot-host`: Period 1 hour, Grace 30 minutes — `host-check.sh` (**S6**: timer 유닛을 만든 뒤에 check를 만든다. §11.3)

### 11.3 `host-check.sh` (S6, `infra/scripts/host-check.sh`)

현재는 **수동 실행**이다: `sudo -u deploy /opt/devpilot/host-check.sh`. 설정은 `/opt/devpilot/ops.env`의 `HEALTHCHECK_HOST_URL`(§5.5·§6. 비어 있으면 결과를 출력만 한다).

**S6에 `devpilot-host-check.service`/`.timer`(`OnCalendar=hourly`, `User=deploy`)를 `infra/systemd/`에 추가하고 `prepare-server.sh`의 유닛 목록에 넣는다.** 2026-09-18 기준 두 유닛 파일은 저장소에 없다(있는 것은 `devpilot-backup.*`, `devpilot-healthping.*` 4개뿐이다).

아래는 `infra/scripts/host-check.sh`의 검사 항목 요약이다(전체는 파일이 기준. 디스크 80%, 인증서 14일, 백업 26시간, 그리고 `/actuator/health`와 `tailscale serve status` 확인).

```bash
#!/usr/bin/env bash
# DevPilot host check - disk, containers, tailscale cert, backup freshness -> healthchecks.io
set -euo pipefail

readonly DEVPILOT_HOME="/opt/devpilot"
readonly DISK_MAX_PERCENT=80
readonly CERT_MIN_DAYS=14
readonly BACKUP_MAX_AGE_HOURS=26

read_env_value() { grep -E "^$1=" "$2" | tail -n 1 | cut -d '=' -f 2- || true; }

url="$(read_env_value HEALTHCHECK_HOST_URL "${DEVPILOT_HOME}/ops.env")"
problems=()

disk="$(df --output=pcent / | tail -n 1 | tr -dc '0-9')"
((disk < DISK_MAX_PERCENT)) || problems+=("disk ${disk}%")

for container in devpilot-api-1 devpilot-caddy-1; do
  state="$(docker inspect -f '{{.State.Status}} {{.RestartCount}}' "${container}" 2>/dev/null || echo 'missing 0')"
  status="${state% *}"
  restarts="${state#* }"
  [[ "${status}" == "running" ]] || problems+=("${container} ${status}")
  counter_file="${DEVPILOT_HOME}/state/${container}.restarts"
  previous="$(cat "${counter_file}" 2>/dev/null || echo 0)"
  ((restarts <= previous)) || problems+=("${container} restarted $((restarts - previous))x")
  printf '%s\n' "${restarts}" >"${counter_file}"
done

# tailscale serve가 종료하는 인증서: tailnet 주소로 확인 (127.0.0.1:443은 tailscale serve가 듣지 않는다)
ts_ip="$(tailscale ip -4 2>/dev/null || true)"
ts_host="$(tailscale status --self --json 2>/dev/null | sed -n 's/.*"DNSName": *"\([^"]*\)\.".*/\1/p' | head -n 1)"
end_date="$(openssl s_client -servername "${ts_host}" -connect "${ts_ip}:443" </dev/null 2>/dev/null \
  | openssl x509 -noout -enddate | cut -d '=' -f 2 || true)"
if [[ -n "${end_date}" ]]; then
  days=$(( ($(date -d "${end_date}" +%s) - $(date +%s)) / 86400 ))
  ((days >= CERT_MIN_DAYS)) || problems+=("tls cert expires in ${days}d")
else
  problems+=("tls cert unreadable")
fi

if [[ -f "${DEVPILOT_HOME}/state/last_backup_ok" ]]; then
  age_hours=$(( ($(date +%s) - $(stat -c %Y "${DEVPILOT_HOME}/state/last_backup_ok")) / 3600 ))
  ((age_hours <= BACKUP_MAX_AGE_HOURS)) || problems+=("last backup ${age_hours}h ago")
fi

if ((${#problems[@]} == 0)); then
  curl -fsS -m 10 --retry 3 -o /dev/null "${url}"
else
  printf '%s\n' "${problems[@]}" | curl -fsS -m 10 --retry 3 -o /dev/null --data-binary @- "${url}/fail"
fi
```

- 메모리 사용률 검사는 없다(OCI 유휴 회수 대응이었다). 공용 서버의 메모리는 `docker stats`로 월간 점검한다(§11.8).
- 컨테이너 재생성(배포) 시 `RestartCount`가 0으로 돌아가므로 이전 값보다 작으면 알리지 않는다.
- 백업 timer 활성화(S2) 전에는 `last_backup_ok`가 없으므로 검사를 건너뛴다.
- `tailscale` 명령은 `deploy` 사용자가 읽기 전용으로 실행할 수 있어야 한다(`tailscale ip`, `tailscale status`는 일반 사용자도 된다).

### 11.4 애플리케이션 로그 점검

```bash
dc logs --since 168h api | grep -c '"log.level":"ERROR"'     # 주간 ERROR 수
dc logs --since 168h api | grep -c '"log.level":"WARN"'
dc logs --since 26h api | grep 'RetentionCleanupJob'          # 일일 요약 줄 (03 §8)
dc logs --since 26h api | grep 'AiBalanceCheckJob'            # 잔액 (§11.6)
dc logs --since 168h api | grep 'StaleRubberDuckJob'          # 러버덕 방치 세션 정리 건수 (03 §6)
dc logs --since 168h api | grep 'JOB_FAILED'                  # job 실패 (07 §6.3)
```

- `StaleRubberDuckJob`의 처리 건수가 매주 꾸준히 크면(예: 완료한 세션보다 많음) 사용자가 러버덕을 끝내지 못하고 떠난다는 뜻이다. 운영 문제가 아니라 제품 신호이므로 `docs/ops/ops-log.md`에 적고 기능 개선 이슈로 넘긴다.

- prod 로그는 ECS JSON이다(`logging.structured.format.console=ecs`). 로그에는 이메일·토큰·본문이 없다(`03` §8).
- Docker 로그 로테이션(컨테이너당 50 MB) 범위 안에서만 조회된다. 장기 로그 보관은 하지 않는다.
- compose 서비스는 `api`와 `caddy` 둘뿐이다. caddy 접근 로그는 `dc logs --since 1h caddy`로 본다.

### 11.5 DB 크기 (공용 PostgreSQL)

월 1회, 로컬 DB 클라이언트 또는 서버에서 `docker exec <pg-container> psql -U devpilot -d devpilot`:

```sql
select pg_size_pretty(pg_database_size('devpilot')) as database_size;
select c.relname, pg_size_pretty(pg_total_relation_size(c.oid)) as total
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'devpilot' and c.relkind = 'r'
order by pg_total_relation_size(c.oid) desc limit 10;
```

**1 GB 이상**이면 큰 테이블의 보존 기간 설정(`03` §9 `devpilot.privacy.*`)과 purge job 로그를 확인하고 조치를 ADR로 남긴다(공용 디스크 여유 32 GB 기준).

### 11.6 DeepSeek 비용 · 잔액

| 층 | 설정 |
|---|---|
| 앱 예산 가드 (1차) | `devpilot.ai.monthly-budget-usd=3`, **서비스 전체 합계**, 월 경계는 `devpilot.time.default-zone`(Asia/Seoul). 80%에서 `aiStatus=BUDGET_WARNING`, 100%에서 AI 차단 (`03` §9, `17-ai-integration.md`). 단가는 피크(월~금 01–04·06–10 UTC ×2)를 **항상 ×2로 보수 계산**한다(결정 F) |
| 선불 잔액 (백스톱) | DeepSeek는 콘솔 지출 한도가 없다. **선불 잔액을 소액(USD 10 안팎)으로 유지**하는 것이 유일한 하드 캡이다. 잔액 소진 시 API가 402를 반환하고 앱은 `aiStatus=BALANCE_EXHAUSTED`로 AI 기능을 끈다(비-AI 기능 유지, NFR-03) |
| 잔액 확인 | `AiBalanceCheckJob`(매시 15분): `GET /user/balance` → `min-balance-usd`(1.00) 이하이면 WARN + `BALANCE_EXHAUSTED`, 회복하면 해제. 402 수신 시 즉시 같은 상태 |
| 주간 확인 | `select date_trunc('day', created_at) as day, count(*) as calls, round(sum(cost_micro_usd) / 1e6, 3) as usd from devpilot.ai_call_log where created_at > now() - interval '7 days' group by 1 order by 1;` |
| 경보 기준 | 주간 합계가 **USD 0.9**(월 예산 USD 3의 30%)를 넘으면 원인(operation별 호출 수)을 확인한다 |
| 러버덕 비용 (추정) | 세션 1회 = `RUBBER_DUCK` 턴 5회 + `RUBBER_DUCK_SUMMARY` 1회 ≈ **USD 0.008**(피크 ×2, `17` §3.11·§3.12 설정 기준). 사용자 1명이 하루 2세션이면 월 ≈ USD 0.5다. 러버덕은 매일 쓰는 기능(M1)이므로 주간 확인 때 operation별 합계도 본다: `select operation, count(*) as calls, round(sum(cost_micro_usd) / 1e6, 3) as usd from devpilot.ai_call_log where created_at > now() - interval '7 days' group by 1 order by 3 desc;` |
| 충전 | 잔액 알림(로그) 또는 platform.deepseek.com에서 확인 후 소액 충전. eval도 같은 잔액을 쓴다(결정 E) |

### 11.7 호스트 자원 (공용 서버)

- 월 1회 `docker stats --no-stream`으로 DevPilot 컨테이너의 메모리(api ≤ 1.2 GB)와 `free -m`의 가용 메모리(≥ 1 GB)를 확인한다. 부족하면 §3.3 한도를 낮추거나 서버 소유자와 조정한다.
- `df -h /`, `docker system df`로 디스크를 확인한다. DevPilot 이미지와 `state/web-<tag>.zip` 사본은 배포가 성공할 때마다 deploy.sh가 **방금 배포한 tag와 그 직전 tag 2개만** 남기고 지운다(공용 Docker이므로 `docker image prune`은 쓰지 않는다, §9.3).

### 11.8 정기 점검표

주간 (월요일, 10분):
- [ ] healthchecks.io 3개 check 정상, 지난 7일 `devpilot-health` 실패 이력
- [ ] §11.4 ERROR/WARN 수 확인, 새 오류 유형이면 이슈 생성
- [ ] §11.6 AI 비용 주간 합계·잔액
- [ ] 계정 삭제 요청 로그 확인 → 있으면 §13.7 (`07-security-and-privacy.md` §6.3의 감사 이벤트)
- [ ] Dependabot PR 처리 (§7.4)
- [ ] `pull-backup.ps1`이 이번 주에 성공했는지(작업 스케줄러 기록)

월간 (첫째 주, 40분):
- [ ] 복구 리허설 §8.5
- [ ] DB 크기 §11.5, 호스트 자원 §11.7
- [ ] `tailscale status`에서 서버 노드의 key expiry 확인(만료 전 갱신)
- [ ] 운영 기록 파일 `docs/ops/ops-log.md`에 점검 결과 1줄

분기:
- [ ] §14 출처 재확인: Tailscale serve·인증서 정책, DeepSeek 모델·단가·은퇴 공지, GitHub runner 정책, healthchecks.io 약관
- [ ] `18` §1 버전 표와 Boot 지원 기간 확인

---

## 12. 운영 비용

| 항목 | 월 비용 (USD) | 근거 |
|---|---|---|
| DeepSeek API (앱) | ≤ 3 | DEC-06, 앱 예산 가드 (Asia/Seoul 월 경계, 피크 ×2 보수 계산). 실측: coach review 1회 ≈ 0.004. 추정: 러버덕 세션 1회(턴 5 + 정리 1) ≈ 0.008 (§11.6) |
| DeepSeek API (eval) | ≤ 0.5/회, 수동 실행 | prod와 같은 선불 잔액 (결정 E) |
| 서버 | 0 (기존 보유) | 전기·회선은 기존 비용 |
| GitHub (public repo: Actions, GHCR, secret scanning, Dependabot) | 0 | DEC-04 |
| Tailscale | 0 | Personal plan (HTTPS 인증서 포함) |
| healthchecks.io | 0 | Hobbyist |
| 도메인 · OCI · Supabase | 0 | 현재 단계에서는 쓰지 않는다. 공개 전환 시 도메인 비용이 생긴다(OCI Always Free·Supabase 무료 플랜은 0, §1.4·§13.8) |
| **합계** | **약 3~4** | |

NFR-01 기준: prod 앱 AI 예산 USD 3/월(DEC-06, 앱 가드). eval 실행은 `-PevalMaxCostUsd`(기본 0.5)로 실행 전 상한을 건다. 선불 잔액은 USD 10 안팎으로 유지한다.

---

## 13. Runbooks

모든 runbook 공통:
- 명령의 `dc`는 §5.1 함수다. 서버 접속은 `ssh <user>@<server>` → `sudo -iu deploy`
- 수행 후 `docs/ops/ops-log.md`에 날짜, runbook, 원인, 조치, 소요 시간을 1줄로 남긴다. **서버 주소·계정·이메일은 기록하지 않는다**(public 저장소)
- 확인 URL: 로컬 PC에서 `https://<tailnet-host>/actuator/health` → `{"status":"UP"}`; 서버에서 `http://127.0.0.1:18080/actuator/health`

### 13.1 deploy-and-rollback

**정상 배포**
1. §9.1 릴리스 전 확인을 끝낸다.
2. `git checkout main && git pull` → `git tag -a vX.Y.Z -m "<요약>"` → `git push origin vX.Y.Z`
3. GitHub → Actions → release 실행이 녹색인지 확인한다(Job summary의 `api image`·`web.zip` 둘 다 success, Release에 `web.zip` 자산).
4. `infra/` 파일이 바뀐 릴리스면 먼저 서버에서 `prepare-server.sh`를 다시 실행한다(§2.2).
5. 서버: `ssh <user>@<server> sudo -u deploy /opt/devpilot/deploy.sh vX.Y.Z`(migration이 있으면 `--backup-first`) → `DEPLOY_RESULT=success`
6. Caddyfile이 바뀌었으면 `dc restart caddy`.
7. 로컬 PC: `smoke-headers.sh https://<tailnet-host>` → 불일치 0건. 브라우저에서 로그인 → 변경 기능 1개 확인. 새 버전이 안 보이면 새로고침(정적 파일은 `no-cache`).

**smoke-headers 실패**
1. 불일치 목록을 본다. 대부분 Caddyfile 헤더 변경이 07 §9.3과 어긋난 경우다.
2. 서버에서 `deploy.sh <previous> --no-pull`로 되돌린다. Caddyfile은 배포로 돌아가지 않으므로, 직전에 `prepare-server.sh`를 재실행해 Caddyfile을 바꿨다면 이전 tag로 다시 clone해 재실행하고 `dc restart caddy`를 한다(§2.2).
3. Caddyfile 또는 07 §9.3 중 틀린 쪽을 고치고 patch tag로 다시 배포한다. 헤더 기준을 바꾸는 경우 07을 먼저 고친다.

**자동 롤백이 발생한 경우** (`DEPLOY_RESULT=rolled_back`)
1. 서비스는 이전 tag로 동작 중이다. health로 확인한다.
2. 출력된 `api 로그 마지막 200줄`에서 기동 실패 원인(설정 누락, Flyway 오류, DB 연결 실패)을 찾는다.
3. 원인별 조치:
   - 환경변수 누락 → `api.env`에 추가 (§5.5) → 같은 tag를 `deploy.sh <tag> --no-pull`로 재활성화
   - Flyway 오류 → §10-10 규칙으로 수정 → patch tag 릴리스
   - 코드 오류 → 수정 → patch tag 릴리스
4. migration이 이미 적용됐는지 확인한다: `select version, success from devpilot.flyway_schema_history order by installed_rank desc limit 3;`

**롤백도 실패한 경우** (`DEPLOY_RESULT=rollback_failed`, exit 11)
1. `dc ps`, `dc logs --tail 200 api`, `dc logs --tail 100 caddy` (서비스는 `api`·`caddy` 둘뿐이다)
2. DB 상태 확인(§13.5)
3. 마지막 정상 tag를 명시해 수동 재활성화: `deploy.sh <last-good>`
4. 그래도 실패하고 원인이 서버면 §13.6.

**첫 배포가 실패한 경우** (`DEPLOY_RESULT=failed_no_previous`, exit 12)
1. 되돌릴 tag가 없어 롤백을 시도하지 않았다. 새 tag의 이미지가 떠 있고 `/opt/devpilot/web`은 새 내용이다.
2. 출력된 api 로그 200줄에서 원인을 찾는다. 대부분 `api.env` 누락(`DEVPILOT_DEV_JWT_KEY`, `APP_BASE_URL`, `DATABASE_*`)이나 DB 연결 실패다(§5.5, §2.4).
3. 고친 뒤 같은 tag로 다시 실행한다: `deploy.sh <tag> --no-pull`

**수동 롤백** (배포는 성공, 기능 문제)
1. 서버에서 `deploy.sh <이전 tag> --no-pull`
2. 문제 릴리스에 migration이 있었다면 이전 앱이 새 스키마에서 정상인지 주요 화면으로 확인한다 (§10-3)
3. 수정은 새 patch tag로 배포한다. 문제 tag를 재사용하지 않는다.

### 13.2 backup-restore

#### 13.2.1 백업 상태 확인
1. healthchecks.io `devpilot-backup`가 정상인지 확인한다.
2. 서버: `cat /opt/devpilot/state/last_backup_ok`, `journalctl -u devpilot-backup.service -n 50 --no-pager`, `ls -l /opt/devpilot/backups/`
3. 실패 원인별 조치:
   - `DATABASE_URL 형식 오류`, 인증 실패 → `api.env` 확인 (§2.4)
   - `postgres:16` pull 실패(Docker Hub) → 일시적이면 `sudo systemctl start devpilot-backup.service`로 재실행
   - 디스크 부족 → §11.7
4. 수동 백업: `sudo systemctl start devpilot-backup.service` → 로그에서 `백업 완료` 확인
5. 로컬 사본: `pull-backup.ps1` 수동 실행

#### 13.2.2 운영 데이터 일부 복구 (잘못된 migration·버그로 행 손상)
1. 손상 범위(테이블, 사용자, 시각)를 기록한다. 추가 손상을 막기 위해 필요하면 문제 릴리스를 롤백한다 (§13.1).
2. 손상 시각 **이전** 백업을 고른다 (파일 이름의 UTC 시각. 서버 7일치 또는 로컬 8주치).
3. §8.5의 1~2단계로 격리 DB(`devpilot-restore`)에 복원한다.
4. 격리 DB에서 필요한 행을 CSV로 추출한다:
   ```powershell
   docker exec devpilot-restore psql -U postgres -c "\copy (select * from devpilot.<table> where user_id = '<uuid>') to '/tmp/rows.csv' csv header"
   docker cp devpilot-restore:/tmp/rows.csv .
   ```
5. 운영 DB에 **트랜잭션 안에서** 비교 후 반영한다. 먼저 `begin;` → 확인용 `select` → 수정 → 결과 확인 → `commit;` (자신 없으면 `rollback;`)
6. `learning_event`는 수정하지 않는다(append-only, `04` §7 I-13). 잘못 기록된 이벤트는 `invalidated_at`/`invalidated_reason`만 설정한다.
7. 정리: `docker rm -f devpilot-restore`, CSV 삭제.

#### 13.2.3 db-restore (DB `devpilot` 전체 복구)
같은 PostgreSQL 인스턴스의 `devpilot` DB로 되돌린다. 다른 사용자의 DB는 건드리지 않는다.

1. api 중지: `dc stop api` (복구 중 쓰기 방지)
2. 복구할 파일을 서버 `/opt/devpilot/backups/`에 둔다(서버에 없으면 로컬 사본을 `scp`로 올린다).
3. 복원 (서버, deploy):
   ```bash
   docker run --rm --network host -e PGPASSWORD="$DATABASE_PASSWORD" \
     -v /opt/devpilot/backups:/backups:ro postgres:16 \
     pg_restore --clean --if-exists --no-owner --no-privileges --exit-on-error \
       -h 127.0.0.1 -p 5432 -U devpilot -d devpilot /backups/devpilot-<ts>.dump
   ```
   `--clean --if-exists`가 `devpilot` 스키마의 객체를 지우고 다시 만든다. role `devpilot`이 DB 소유자이므로 다른 권한이 필요 없다.
4. 확인: `flyway_schema_history` 최신 version이 배포된 앱과 같은지, `app_user` 수
5. `dc start api` → health `UP` → 로그인 → 데이터 표시 확인
6. 복원 시점 이후 데이터(최대 24시간)는 손실된다. 사용자에게 알린다.
7. 배포된 앱보다 오래된 백업을 복원했다면(migration이 더 있음) 앱 기동 시 Flyway가 누락 migration을 다시 적용한다. §10-3 호환 규칙 덕분에 정상이다.

### 13.3 secret-rotation

공통 원칙: **새 값 발급 → 적용 → 동작 확인 → 이전 값 폐기** 순서. 유출이 의심되면 순서를 바꿔 **이전 값을 먼저 폐기**한다(§13.4). 교체 후 password manager와 `DevPilot-ops/`의 값을 갱신한다.

#### 13.3.1 DB 비밀번호 (정기: 연 1회)
1. password manager로 새 비밀번호(32자 이상) 생성
2. 서버에서 `docker exec -it <pg-container> psql -U postgres -c "alter role devpilot password '<새 값>';"` (명령은 `DevPilot-ops/02-database.md`)
3. 즉시 `api.env`의 `DATABASE_PASSWORD` 수정 → `dc up -d --force-recreate api` (재기동 사이 약 30초 API 오류 가능)
4. health `UP` 확인 → `sudo systemctl start devpilot-backup.service`로 백업 경로도 확인 (`backup.sh`는 `api.env`를 읽는다)
5. 로컬 `.env`와 저장된 이전 비밀번호를 갱신·삭제한다

#### 13.3.2 DeepSeek API key (정기: 연 1회)
1. platform.deepseek.com → API keys → Create new key (이름 `devpilot-YYYYMM`)
2. `api.env`의 `DEEPSEEK_API_KEY` 교체 → `dc up -d --force-recreate api`
3. 확인: 앱에서 동기 AI 기능 1회(예: hint) 성공, 또는 `select status, created_at from devpilot.ai_call_log order by created_at desc limit 3;`에 `SUCCESS`
4. 이전 key **Delete**
5. GitHub environment `ai-eval` secret과 로컬 `.env`도 같은 값으로 교체한다(키가 하나다, 결정 E)

#### 13.3.3 devtoken 서명 키 `DEVPILOT_DEV_JWT_KEY` (유출 시 또는 연 1회)
1. `openssl ecparam -genkey -name prime256v1 -noout | openssl pkcs8 -topk8 -nocrypt`로 새 PEM 생성
2. `api.env` 교체(한 줄 `\n` 형식, §5.5) → `dc up -d --force-recreate api`
3. **모든 사용자의 기존 토큰이 즉시 무효**가 된다. 사용자(최대 3명)에게 재로그인을 안내한다
4. Supabase Auth를 쓰는 경우의 키 회전은 §13.8 부록

#### 13.3.4 (예약 — GitHub OAuth App client secret은 Supabase 도입 시, §13.8)

#### 13.3.5 로그 해시 키 `DEVPILOT_LOG_HASH_KEY` (유출 시에만)
1. `openssl rand -hex 32`로 새 값 생성
2. `api.env` 교체 → `dc up -d --force-recreate api`
3. 교체 시각을 `docs/ops/ops-log.md`에 기록한다 — 이 시각 전후로 같은 사용자의 `userRef`가 달라진다

#### 13.3.6 SSH 키 · Tailscale
**서버 접속 SSH 키 (유출 시)**
1. 로컬 PC에서 새 ed25519 키 생성 → 서버 `~<user>/.ssh/authorized_keys`에 추가 → 새 키로 로그인 확인 → 이전 공개키 줄 삭제
2. `DevPilot-ops/03-accounts-and-secrets.md`의 키 파일 이름 갱신

**Tailscale 노드 (노드 키 유출·서버 침해 의심 시)**
1. Admin console → Machines → 서버 노드 → **Remove**(다른 서비스도 tailnet 접근이 끊긴다 — 서버 소유자로서 판단)
2. 서버에서 `tailscale up`으로 재합류(서버 소유자의 기존 절차) → `tailscale serve` 설정이 남아 있는지 `tailscale serve status`로 확인
3. 서버 침해가 의심되면 §13.6

#### 13.3.7 (예약 — 백업 암호화 키 없음)

### 13.4 incident-api-key-leak

대상: `DEEPSEEK_API_KEY`(주), `DATABASE_PASSWORD`, `DEVPILOT_DEV_JWT_KEY`, `DEVPILOT_LOG_HASH_KEY`, healthchecks ping URL.

**트리거**: GitHub secret scanning/push protection 알림, gitleaks CI 실패, 예상 밖 AI 비용·잔액 감소, 로그·채팅·스크린샷에 값이 노출된 사실 발견. (2026-09-18 기준 DB 비밀번호·DeepSeek 키·서버 계정은 개발 대화 기록에 남아 있다 — `DevPilot-ops/03-accounts-and-secrets.md` §4의 정리 목록. 학습용이라 보류 중이지만 S2 실사용 전에 교체한다.)

1. **즉시 폐기 (5분 안)** — 새 값을 만들기 전에 이전 값을 끊는다
   - DeepSeek: platform.deepseek.com → API keys → 해당 key **Delete** (앱 AI 기능은 `AI_UNAVAILABLE`/`BALANCE_EXHAUSTED`로 동작하고 비-AI 기능은 유지된다, NFR-03)
   - 서버가 폐기한 키로 계속 호출하지 않게 같이 끈다: `api.env`에 `DEVPILOT_AI_PROVIDER=disabled` → `dc up -d --force-recreate api`. 그러면 `GET /me`의 `aiStatus = DISABLED`이고 AI endpoint는 503 `AI_UNAVAILABLE`이며 `ai_call_log`에 행이 남지 않는다(`17` §3.10). 새 키를 넣은 뒤 `deepseek`으로 되돌린다
   - DB 비밀번호: §13.3.1-2
   - devtoken 키: §13.3.3
2. **새 값 발급·적용**: §13.3의 해당 절차
3. **영향 확인**
   - DeepSeek: platform.deepseek.com → Usage: 노출 시점부터 사용량·비용. `ai_call_log` 합계와 비교:
     ```sql
     select date_trunc('day', created_at) as day, count(*), round(sum(cost_micro_usd) / 1e6, 3) as usd
     from devpilot.ai_call_log where created_at > '<노출 추정 시각>' group by 1 order by 1;
     ```
     콘솔 비용이 `ai_call_log`보다 크면 외부 사용이 있었던 것이다 → 잔액이 소액이라 피해는 그 범위로 제한된다. `ai_call_log`에는 프롬프트·응답 원문이 없으므로(`17` §8.2) 노출된 것은 키뿐이다
   - 잔액: 감사 로그 `AI_BALANCE_LOW`(`17` §8.7)와 platform.deepseek.com의 잔액을 같이 본다. 외부 사용으로 잔액이 비면 `aiStatus = BALANCE_EXHAUSTED`가 되고 다음 정상 조회(`AiBalanceCheckJob`, 매시 15분)에서 풀린다
   - DB: PostgreSQL 컨테이너 로그(`docker logs <pg-container> --since <시각>`)에서 낯선 연결을 확인한다. 5432는 tailnet·LAN에서만 열려 있다. 데이터 변조가 의심되면 §13.2.2
4. **노출 경로 찾기**
   ```bash
   gitleaks git --redact -v .                          # 저장소 전체 이력
   git log -S '<key 앞 12자>' --all --oneline          # 커밋 위치
   ```
   CI 로그, 이슈·PR 본문, 로컬 셸 기록(`Get-History`, `~/.bash_history`), 스크린샷을 확인한다.
5. **git 이력 처리**: key는 이미 폐기됐으므로 **이력 재작성은 하지 않는다**(public 저장소는 fork·캐시에 이미 복제됐을 수 있어 재작성해도 회수되지 않는다). HEAD에서 값을 제거하는 커밋만 만든다. 개인정보·서버 주소 등 폐기할 수 없는 데이터가 함께 노출된 경우에만 `git filter-repo --replace-text`로 재작성하고 GitHub Support에 cached view 삭제를 요청한다.
6. **재발 방지**: 원인에 맞게 gitleaks 규칙(DeepSeek 키 패턴 `sk-` + hex 32자, 07 §11.3), push protection 우회 여부, `.gitignore`(`.env`, `DevPilot-ops/`), 로그 마스킹(`07-security-and-privacy.md`)을 보강한다.
7. **기록**: `docs/ops/ops-log.md`에 노출 시각, 발견 시각, 폐기 시각, 비용 영향, 원인, 조치를 남긴다.
8. **복귀 확인**: 새 키 + `DEVPILOT_AI_PROVIDER=deepseek`으로 재기동 → `GET /me`의 `aiStatus = ENABLED` → 동기 AI 1회(러버덕 턴 또는 hint) 성공 → `select status, error_code, created_at from devpilot.ai_call_log order by created_at desc limit 3;`가 `SUCCESS`.

### 13.5 db-or-server-down (구 supabase-paused)

**증상**: healthchecks.io `devpilot-health` 알림, `/actuator/health`가 `DOWN` 또는 503, api 로그에 DB 연결 실패, 브라우저에서 접속 불가.

1. 로컬 PC에서 `tailscale status`로 서버 노드가 online인지 본다. offline이면 서버 전원·네트워크·Proxmox 상태(서버 소유자 절차) → 복구 후 3단계.
2. 서버 접속 → `docker ps`로 공용 PostgreSQL 컨테이너와 DevPilot 컨테이너 상태 확인.
   - PostgreSQL 컨테이너가 죽었으면 서버 소유자로서 재시작한다(다른 서비스도 영향을 받는다).
   - api만 죽었으면 `dc logs --tail 200 api` → 원인 → `dc up -d api`
   - `tailscale serve status`에 설정이 없으면 §2.3-2를 다시 실행한다.
3. health 확인. Hikari가 자동으로 재연결한다. 5분 안에 `UP`이 아니면 `dc up -d --force-recreate api`
4. 로그인 → `/me` → Today 화면 확인
5. 원인·조치를 `ops-log.md`에 기록. 반복되면 RISK 항목(`20` §4)을 갱신한다.

### 13.6 server-restore (목표: 2시간 이내)

**트리거**: 서버 손실(디스크·VM 삭제), 침해 의심으로 재설치, 다른 서버로 이전.

사전 보관물 (평소 유지): password manager의 `api.env`·`ops.env` 전체 내용, 로컬 PC의 백업 사본(§8.1 2차), `DevPilot-ops/`, Tailscale admin 접근, 저장소 public 접근.

| 단계 | 시간 | 작업 |
|---|---|---|
| 1 | — | 서버 소유자 절차로 OS·Docker·Tailscale·PostgreSQL 컨테이너 준비(DevPilot 범위 밖). 새 서버면 tailnet 합류 후 MagicDNS 이름을 확인한다 |
| 2 | 5분 | PostgreSQL에 role `devpilot`·DB `devpilot` 생성 (`DevPilot-ops/02-database.md`) |
| 3 | 10분 | §2.2 `prepare-server.sh` → §2.3 `tailscale serve` |
| 4 | 5분 | password manager 내용으로 `/opt/devpilot/api.env`, `ops.env` 작성(권한 §5.5). 호스트명이 바뀌었으면 `APP_BASE_URL` 갱신 |
| 5 | 10분 | `deploy.sh <last-good tag>`(백업은 opt-in이므로 플래그를 붙이지 않는다 — 빈 DB라 백업할 것이 없다) → health `UP`(빈 DB에 migration 적용됨) → `dc stop api` |
| 6 | 15분 | 로컬 사본을 `scp`로 올려 §13.2.3 `db-restore` 3~5단계 |
| 7 | 5분 | `sudo systemctl enable --now devpilot-backup.timer devpilot-healthping.timer`, 수동 백업 1회, healthchecks 정상 |
| 8 | 5분 | 로컬 PC: `smoke-headers.sh`, 로그인 → 기존 데이터 표시 확인. 침해가 원인이면 §13.3의 DB 비밀번호·DeepSeek 키·devtoken 키·로그 해시 키를 교체한다(서버에 있던 값) |

### 13.7 account-deletion (DEC-11)

계정 삭제는 **앱 데이터 자동 삭제 + allowlist 제거**다. devtoken 모드에는 외부 사용자 저장소가 없다(Supabase 도입 시 Auth 사용자 수동 삭제 단계가 추가된다, §13.8).

**트리거**: 사용자가 앱에서 계정 삭제(`DELETE /api/v1/me`, 202) → `AccountDeletionJob`이 5분 안에 `app_user`와 모든 사용자 데이터를 cascade 삭제하고 감사 로그를 남긴다 (`03` §6, `04` §8). 운영자는 주간 점검(§11.8) 또는 사용자 연락으로 요청을 인지한다.

1. 요청 확인: 감사 로그를 찾는다 (이벤트 이름은 `07-security-and-privacy.md` §6.3).
   ```bash
   dc logs --since 168h api | grep '"event":"ACCOUNT_'
   ```
   `userRef`와 시각을 기록한다.
2. 앱 데이터 삭제 완료 확인 (DB 클라이언트): `select count(*) from devpilot.app_user where external_auth_id = '<sub>';` → 0. (`sub`는 devtoken 모드에서 이메일 기반 UUID v5 — `03` §4.2.) 1 이상이면 삭제 job이 끝나지 않은 것이다 → api 로그의 `JOB_FAILED` 확인 후 5분 뒤 다시 확인.
3. allowlist 제거: `api.env`의 `DEVPILOT_ALLOWED_EMAILS`에서 제거 → `dc up -d --force-recreate api`. 이후 그 이메일로는 `POST /api/v1/dev/token`이 403이다.
4. 백업 안내: 삭제 전 데이터는 서버 백업에 **최대 7일**, 로컬 사본에 **최대 8주** 남고 보관 기간이 지나면 삭제된다. 복구 리허설·복구 작업 외에는 열지 않는다. 사용자에게 이 사실을 알린다.
5. 기록: `docs/ops/ops-log.md`에 요청 일시, 앱 데이터 삭제 확인 일시, allowlist 제거 일시, `userRef`를 남긴다. **이메일 원문은 기록하지 않는다.**
6. 기한: 요청 후 **7일 이내**에 3단계를 끝낸다 (기한 정책의 기준은 `07-security-and-privacy.md`).

### 13.8 부록 — 공개 전환 후보 (OCI · 도메인 · Supabase)

**이 절은 폐기한 대안이 아니라 §1.4의 "공개 전환" 단계에서 실제로 쓸 후보다.** v2 초판(2026-09-17)이 OCI Always Free VM + 구매 도메인 + Supabase(Auth·DB)를 전제했고, 그 내용을 여기에 보존한다. 전환을 결정하면 아래 요지에서 시작해 git 이력의 2026-09-17 판 §2~§4·§8·§13.2.3·§13.5·§13.6을 꺼내 쓴다. 전환 전 필수 조건은 **§1.5**다.

| 후보 | 언제 고르나 |
|---|---|
| (a) 지금 자체 서버를 공개 | 서버·회선을 그대로 쓰고 도메인만 붙일 때. 호스트 80/443과 공용 서버 규칙(§2.1)을 먼저 정리한다 |
| (b) OCI Always Free (Ampere A1, **arm64**) | 자체 서버를 공개하고 싶지 않을 때. 이미지가 이미 멀티아치라(§5.3) 이미지 파이프라인은 그대로다 |

요지:

| 항목 | 요지 |
|---|---|
| Supabase Auth (**§1.5 필수 조건**) | GitHub OAuth App(dev/prod 각 1개, callback `https://<ref>.supabase.co/auth/v1/callback`), Site/Redirect URL, "Allow new users to sign up" 끄기 + backend allowlist 병행, JWT signing key는 비대칭(ES256/RS256), JWKS 캐시 20분, publishable key만 앱에, Data API 비활성화(SP-5), 변수 `SUPABASE_URL`·`SUPABASE_JWT_ALGORITHM`·`SUPABASE_PUBLISHABLE_KEY`·CSP `connect-src`에 호스트 추가, 계정 삭제 시 Auth 사용자 수동 삭제, 키 회전은 standby → 20분 → rotate → 2시간 후 revoke |
| Supabase DB | session pooler 5432(`postgres.<ref>`, transaction mode 6543 금지), `sslmode=verify-full` + CA 마운트, 무료 플랜 일시정지(1주 비활동)·500 MB read-only·백업 없음 → 매일 job으로 활동 유지 |
| OCI (후보 b) | home region 고정, **A1 = Ampere arm64** 2 OCPU/12 GB(이미지가 `linux/arm64`를 포함하므로 같은 tag를 그대로 pull한다, §5.3), Security List 80/443/443udp, `bootstrap-vm.sh`(Docker CE·SELinux·firewalld·dnf-automatic·Tailscale `--advertise-tags`), idle reclamation 대응(`-Xms3g -XX:+AlwaysPreTouch`), Object Storage + instance principal + rclone + age 암호화 백업, Actions→tailnet 배포(OAuth client, ACL `tag:ci`) |
| 도메인 | A 레코드 → 공개 IP, Caddy ACME(80/443), HSTS `includeSubDomains`(현재 값과 같다, §4.2), UptimeRobot keyword monitor(tailnet에서는 쓸 수 없었던 것) |

---

## 14. 확인 출처 (2026-09-18 조회)

- Tailscale serve: https://tailscale.com/kb/1312/serve · HTTPS 인증서: https://tailscale.com/kb/1153/enabling-https · MagicDNS: https://tailscale.com/kb/1081/magicdns
- Docker와 방화벽 (publish가 firewalld를 우회): https://docs.docker.com/engine/network/packet-filtering-firewalls/ · `host-gateway`: https://docs.docker.com/reference/cli/docker/container/run/#add-host
- Docker Compose 서비스 옵션(`mem_limit`, `logging`, `extra_hosts`): https://docs.docker.com/reference/compose-file/services/
- Caddy Caddyfile 지시어·`auto_https off`: https://caddyserver.com/docs/caddyfile/directives · https://caddyserver.com/docs/caddyfile/options#auto-https
- GitHub-hosted runners: https://docs.github.com/en/actions/reference/runners/github-hosted-runners · GHCR 가시성: https://docs.github.com/en/packages/learn-github-packages/configuring-a-packages-access-control-and-visibility
- GitHub Actions SHA pinning 정책: https://github.blog/changelog/2025-08-15-github-actions-policy-now-supports-blocking-and-sha-pinning-actions/ · 보안 사용 가이드 https://docs.github.com/en/actions/reference/security/secure-use
- trivy-action 공급망 사고: https://github.com/aquasecurity/trivy/security/advisories/GHSA-69fq-xp46-6x23
- PostgreSQL 16 `pg_dump`/`pg_restore`: https://www.postgresql.org/docs/16/app-pgdump.html · https://www.postgresql.org/docs/16/app-pgrestore.html
- Testcontainers 원격 Docker(`DOCKER_HOST`, `TESTCONTAINERS_HOST_OVERRIDE`): https://java.testcontainers.org/features/configuration/
- DeepSeek API(모델·단가·잔액): https://api-docs.deepseek.com/ · 잔액 https://api-docs.deepseek.com/api/get-user-balance · 단가 https://api-docs.deepseek.com/quick_start/pricing
- healthchecks.io 요금제: https://healthchecks.io/pricing/
- Spring Boot 지원 기간: https://spring.io/projects/spring-boot#support
