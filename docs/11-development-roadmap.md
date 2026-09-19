# 11. Development Roadmap

> Status: Accepted (v3) · Last updated: 2026-09-19 · Related: `13-product-backlog.md`, `12-acceptance-criteria.md`, `16-definition-of-ready-done.md`, `20-decisions-and-risks.md`, ADR-003, ADR-005, ADR-030, ADR-031, ADR-032, ADR-036, ADR-037
>
> 목표는 **M1(S0~S3)을 최대한 빨리 끝내고 DevPilot으로 매일 공부하기 시작하는 것**이다. 단계에는 날짜가 없다 — exit criteria를 통과하면 끝난다. 목표일은 사용자가 설정창에 등록하는 값(`learning_goal.target_completion_date`)이고, 개발 일정이 아니다.
>
> 2026-09-18 확정(`20` DEC-19~23): 자체 서버 + Tailscale HTTPS(도메인·OCI·Supabase 없음), 개발·운영 DB는 서버 PostgreSQL 16, 인증은 `devtoken` 모드(운영 포함), AI 공급자 DeepSeek(월 USD 3), 배포는 `release.yml`(amd64 이미지·web zip) + 로컬에서 `deploy.sh` 수동 실행, 백업은 `pg_dump` + 로컬 PC 주 1회 pull. 서버 주소·계정은 저장소 밖 `DevPilot-ops/`에만 둔다.
>
> 2026-09-18 v3(`20` DEC-24~26, ADR-036·037): 학습 루프를 **읽는다 → 만든다 → 러버덕으로 설명한다 → 반복한다**로 바꿨다(러버덕·코드 읽기·사이드 프로젝트·교차 학습·확장 제안·진단 우선). 스프린트 날짜를 없애고 단계를 **M1(S0~S3) / M2(S4~S7)** 로 묶었다.

---

## 1. 원칙

| # | 원칙 | 적용 규칙 |
|---|---|---|
| R-1 | **Walking skeleton** | S0에서 브라우저 → Tailscale HTTPS(`tailscale serve`) → Caddy(`127.0.0.1:18080`) → API → 서버 PostgreSQL 16 전 구간을 먼저 연결한다(인증은 `devtoken` 모드, `POST /api/v1/dev/token`). 기능은 이 골격 위에 세로로 한 조각씩 붙인다 |
| R-2 | **S0부터 배포** | S0 이후 모든 단계의 exit criteria에는 "배포 환경 확인"이 들어간다. 배포는 `release.yml`이 만든 이미지·web zip을 로컬에서 `ssh <server>` → `deploy.sh v0.<단계 번호>.<patch>`로 올리는 수동 절차다(결정 D). 로컬에서만 동작하는 기능은 완료가 아니다 |
| R-3 | **AI 없이 먼저 쓴다** | S1~S2는 AI 호출이 없다(prod `DEVPILOT_AI_PROVIDER=disabled`, `GET /me` `aiStatus=DISABLED`). Today·Review·Plan·기한 역산이 AI 없이 동작한 뒤 S3에서 AI(DeepSeek)와 러버덕을 붙인다(NFR-03) |
| R-4 | **날짜 없는 단계** | 단계는 기간이 아니라 exit criteria로 끝난다. 날짜·기간을 계획에 쓰지 않는다. M1이 늦어지면 M1의 P1·P2를 M2로 미루고 M1을 먼저 끝낸다(`13-product-backlog.md` §1) |
| R-5 | **결정적 규칙은 test vector 먼저** | `06-learning-engine-rules.md`의 vector를 `@ParameterizedTest`로 옮긴 실패 테스트가 구현보다 먼저 merge된다 |
| R-6 | **Spike로 기술 리스크를 S0에 소진** | 신규 스택(Boot 4.1, Java 25, Flutter Web 한글 입력, 자체 서버 amd64 배포 + Tailscale HTTPS(공개 전환 시 arm64 추가, §3.10), DeepSeek `/responses` 구조화 출력)의 불확실성은 S0 spike에서 확인하고 ADR로 결론을 남긴다. SP-2·SP-4 6번은 2026-09-18에 이미 확인됐다(§4) |
| R-7 | **DevPilot은 학습 도구다** | DevPilot 구현 작업을 사용자 학습 plan의 과제나 evidence로 쓰지 않는다. 사용자가 배운 것을 적용하는 사이드 프로젝트(기본: 주문 시스템)는 DevPilot과 별개이고, DevPilot 안에서는 `side_project`로 등록되는 **학습 대상**이다(`20-decisions-and-risks.md` RISK-01) |
| R-8 | **레벨 도달 상한을 단계 목표에 맞춘다** | skill 레벨 4·5의 상승 규칙 입력(evidence 승인, coach finding, variant 답변, difficulty 5 challenge)은 S4 이후에야 생긴다. M1(S3까지)은 K·I·E ≤ 3, D = 0이 실질 상한이다 — 러버덕 설명 증거도 coverage 7000으로 E3까지다(`06` §7.2 "도달 가능 상한"). M1의 데모·AC에 레벨 4·5를 기대하지 않는다 |

---

## 2. 용량 가정과 단계 회고

### 2.1 가정

| 항목 | 값 |
|---|---|
| 사용자 투입 | 주 4시간 이하 (PR 리뷰·결정·수동 운영 작업. 구현은 하지 않는다 — §5) |
| 에이전트 | 구현의 대부분(골격, DTO·Controller, 화면, 인프라 스크립트, 테스트 작성). 사용자 리뷰 없이 merge하지 않는다 |
| 사용자 시간 배분 기준 | PR 리뷰 60% · 결정·수동 운영 40%. 남는 시간은 DevPilot으로 공부하는 데 쓴다(RISK-01) |

사용자가 직접 해야 하는 운영 작업(에이전트가 대신할 수 없는 것, S0): tailnet에서 MagicDNS + HTTPS Certificates 활성화(`tailscale serve`의 전제), healthchecks.io 계정·check 생성(`HEALTHCHECKS_PING_URL`), DeepSeek 잔액 소액 충전(USD 10 안팎)과 API 키 발급, GitHub secrets(`ai-eval` environment의 `DEEPSEEK_API_KEY`) 등록, 서버에서 `prepare-server.sh` 1회 실행과 `api.env`·`ops.env` 채우기. 실제 값은 `DevPilot-ops/`에만 기록한다.

| 단계 | 묶음 | 사용자 수동 작업 |
|---|---|---|
| S0 | M1 | spike 수동 확인, 위 운영 작업(tailnet HTTPS, healthchecks.io, DeepSeek 충전, GitHub secrets, 서버 준비) |
| S1 | M1 | (없음 — PR 리뷰·결정) |
| S2 | M1 | 백업 pull 스크립트 로컬 등록(`pull-backup.ps1`) |
| S3 | M1 | **S3 구현 시작 전 첫 소스 점검**(`19` §8.5 — 제안 목록을 보고 추가·교체·은퇴를 결정), 큐레이션 저장소를 로컬에 clone(`content/curated-repos.yaml`의 `cloneHint`) → **실사용 시작** |
| S4~S7 | M2 | (없음 — Coach로 자기 사이드 프로젝트 코드 리뷰 시작) |

### 2.2 단계 회고 (S1·S2·S3 종료 시)

각 M1 단계가 끝나면 다음 3개를 기록한다(`20-decisions-and-risks.md` RISK-01 조기 신호로도 쓴다).

| 측정 | 계산 |
|---|---|
| 경과 | 단계 시작 ~ exit까지 주 수 |
| 사용자 실제 투입 | 그 단계의 합계 시간 / 경과 주 수 |
| 재작업률 | 에이전트 PR 중 리뷰 후 2회 이상 수정 요청한 PR 수 / 전체 PR 수 |

| 결과 | 조치 |
|---|---|
| 투입 ≤ 3h/주, 재작업률 ≤ 20% | 계획 유지 |
| 투입 3~4h/주 또는 재작업률 20~30% | 다음 M1 단계의 P2를 M2로 미리 옮긴다 |
| 투입 > 4h/주 또는 재작업률 > 30% | 위 조치 + 다음 M1 단계의 P1도 M2로 미리 옮긴다(순서는 `13-product-backlog.md` §5). 결정은 `20-decisions-and-risks.md`에 기록 |

**소스 점검**: `READ_CODE`가 켜진 뒤(S3 이후)에는 단계 회고마다 소스 점검(`19` §8.5)을 한다 — 빈틈 목록·읽기 평가·저장소 상태를 보고 저장소·읽기 단위의 추가·교체·은퇴를 사용자가 정한다. 사용자가 요청하면 회고와 무관하게 한다. 자동으로 도는 것은 없다.

**중간 트리거 (S1 직렬 사슬)**: S1의 `BL-CNT-01 → CNT-02·CNT-03 → CNT-05·SKL-01 → SKL-02 → GOL-06 → CLI-07`은 병렬화할 수 없는 직렬 사슬이다(`13-product-backlog.md` §4.14 주석). S1을 시작하고 1주 안에 `BL-CNT-03`(skill tree v0)이 merge되지 않으면 S1 종료를 기다리지 않고 위 표의 두 번째 행 조치를 미리 적용한다.

---

## 3. 단계 계획

### 3.1 요약

S0~S7은 **구현 순서를 나타내는 단계 ID**다. 기간·날짜가 없고, exit criteria를 통과하면 다음 단계로 간다.

- **M1 = S0 · S1 · S2 · S3** — 쓸 수 있는 최소. **S3 완료 = 실사용 시작.** 날짜가 아니라 조건이다(§3.5 끝). S2가 끝나면 AI 없이 Today·Review·Plan을 먼저 써도 된다(선택).
- **M2 = S4 · S5 · S6 · S7** — M1을 쓰면서 필요한 순서로. 단계 순서는 잠정이고, 실사용 데이터를 보고 바꿀 수 있다(`20`에 기록).
- **Later** — 현재 계획 밖.

| 묶음 | 단계 | 목표 | 범위 (BL, `13-product-backlog.md` §5) | Exit criteria |
|---|---|---|---|---|
| M1 | **S0** | Bootstrap + walking skeleton + 수동 배포 + spike | FND-01~07·22, SEC-03·07·09·12·16, OPS-01·02·04~07·09·10·21, CLI-01~03 | CI green(`main`·`developer`), `https://<tailnet-host>`에서 dev 로그인(`POST /api/v1/dev/token`) → `/me` 200(AC-25), SP-1~4 결론 ADR 기록, `deploy.sh` 배포 + 롤백 확인, 배포 환경 확인 |
| M1 | **S1** | Identity · Onboarding(진단 선택·사이드 프로젝트) · Goal · Plan · seed v0 | FND-08~14·20·21, SEC-04~06·14, AIP-16, CLI-04~11·32, GOL-01~06, SKL-01~02, CNT-01~05, PRJ-01 | AC-01, AC-11(온보딩 — seed 카드·snapshot·진단 풀이 생략), AC-18, AC-25(allowlist 재확인), AC-24, AC-27(사이드 프로젝트 CRUD), AC-08(S1 endpoint), AC-09(planning level), AC-15(`DELETE /me` 상태 변경), 배포 환경 확인 |
| M1 | **S2** | Today · Session · Review(교차 학습, AI 없음) · **기한 역산(budget·risk·축소/확장 제안)** · Dashboard 최소 · PWA · 백업 자동화 | FND-15, SEC-10·11, OPS-11~13·19, CLI-12·13·15~17·19·25, GOL-07~14·17, SKL-03, TDY-01~11, MEM-01~06·08·12, CNT-06 | AC-02, AC-03, AC-05(복습 스케줄), AC-10, AC-11(seed 카드), AC-17, AC-29, AC-30, AC-08(S2 endpoint), 배포 환경 확인 |
| M1 | **S3** | AI Platform(DeepSeek) · Training · 진단 · Skill updater · **러버덕 · 코드 읽기** · 수동 카드 · evals | FND-16·23~26, OPS-15, CLI-14·20~23·33·34, SKL-04~06, TDY-14·16, MEM-07·09, TRN-01~03·05~13, AIP-01~03·05~13·15·17, RDK-01~04, CNT-07~10·15·16 | AC-04, AC-05(challenge 실패·수동 카드), AC-09, AC-11(진단), AC-12, AC-13, AC-14(submission·러버덕), AC-16(challenge), AC-23, AC-26, AC-28, 배포 환경 확인 → **실사용 시작** |
| M2 | **S4** | Project Coach · CSP 강제 | FND-17, SEC-08, CLI-24, COA-01~10, CNT-11 | AC-06, AC-07(coach E2E), AC-12(coach 재검증), AC-14, AC-16(coach), AC-19(기록), AC-23(coach 재검증), 배포 환경 확인 |
| M2 | **S5** | Weekly · Dashboard 완성 · 캘린더 · AI 문제 생성 · 실측 튜닝 | FND-18, CLI-18·26, SKL-07, TDY-12·13, TRN-04, EVD-01~04, AIP-14, CNT-14 | AC-12(402·content_filter), AC-19(추세), AC-21(weekly), FR-20(캘린더), 배포 환경 확인 |
| M2 | **S6** | Evidence · Export · 복구 리허설 · 하드닝 | SEC-13·15, OPS-16~18, CLI-27~28, EVD-05~08, CNT-12 | AC-15(export), AC-21(evidence), AC-08·AC-18 전체 재검증, 복구 리허설 1회 기록, 배포 환경 확인 |
| M2 | **S7** | 로드맵 비교 | FND-19, CLI-29, REQ-01~04, CNT-13 | AC-22, 배포 환경 확인 |
| — | **Later** | 현재 계획 밖 | OPS-20·22, SEC-17, CLI-30·31, GOL-15·16, MEM-10·11, TDY-15, TRN-14·15, COA-11, REQ-05. **`BL-SEC-18`(공개 인증)은 Later가 아니라 공개 전환 게이트의 필수 선행(P0)이다 — §3.10** | AC-20(Supabase 도입 시), AC-15(삭제 job) |

"배포 환경 확인" = `main`의 `v0.<단계 번호>.<patch>` tag를 `release.yml`이 빌드(amd64 이미지 → GHCR, web zip → release)하고, 로컬에서 `ssh <server>` → `sudo -u deploy /opt/devpilot/deploy.sh v0.x.y`로 배포해 `/actuator/health`가 `UP`이며, 해당 단계 데모 체크리스트를 **tailnet URL**(`https://<tailnet-host>`)에서 모두 통과한 상태. 배포 자동화(Actions → tailnet)는 Later(`BL-OPS-22`).

범위 조정 기록:
- (2026-09-18 v2) 공개 인증은 단계 범위 밖(`BL-SEC-18` — 공개 전환 게이트의 필수 선행, §3.10), `POST /plans` Later(`BL-GOL-16`), `REVIEW_VARIANT` Later(`BL-MEM-10`), 계정 삭제 job Later(`BL-SEC-17`, `DELETE /me` 상태 변경은 S1), 수동 카드 CRUD는 S3, Trivy·CodeQL·digest 고정은 S2 P1(`BL-SEC-10`). S1 온보딩은 8·9단계(seed 카드 배정·snapshot)를 생략한다(`assignedSeedCardCount = 0`, `latestRiskLevel = null`).
- (2026-09-18 v3, DEC-25) **budget·risk·replan 제안(축소·확장)은 S5 → S2** — "목표일을 등록하면 중요한 것 위주로"가 MUST이고 risk는 planner 입력이다. 이제 S2부터 `deadline_risk`가 계산된다. **ICS 캘린더·AI 문제 생성(`BL-TRN-04`)은 S3 → S5**, **CSP 강제(`BL-SEC-08`)는 S3 → S4**(실사용 시작이 S3 완료로 옮겨졌으므로), **evals v1(`BL-AIP-13`)은 S4 → S3**(러버덕 prompt 품질을 M1 안에서 확인), 진단(`BL-TRN-13`·`BL-CLI-23`·`BL-CNT-08`)은 S3 P1 → P0. 새 BL: 사이드 프로젝트(`BL-PRJ-01`, `BL-CLI-32`)는 S1, 교차 학습(`BL-MEM-12`)·확장 제안(`BL-GOL-17`)은 S2, 러버덕(`BL-RDK-01~04`, `BL-CLI-33`)·코드 읽기(`BL-TDY-16`, `BL-CNT-15`, `BL-CLI-34`)는 S3.

### 3.2 S0 — Bootstrap

순서(SP-2와 SP-4 6번은 완료): **서버 준비 → devtoken → amd64 이미지 → Tailscale HTTPS → CI 브랜치(main·developer) → DeepSeek smoke**.

| 순서 | 작업 |
|---|---|
| 1 | 계정 2FA(`BL-SEC-12`), GitHub 저장소·브랜치 규칙(`BL-OPS-21`, `BL-SEC-09`), `BL-FND-02`, 서버 준비 `prepare-server.sh`(`BL-OPS-04`) + `api.env`·`ops.env`, SP-4 남은 확인 시작 |
| 2 | SP-4 결론 → `BL-FND-03~07·22`, devtoken 백엔드(`BL-SEC-16`, `BL-SEC-03`), `BL-OPS-09` 로컬 환경(서버 Docker 터널) |
| 3 | SP-3(amd64 이미지 → GHCR → `deploy.sh` → Caddy `127.0.0.1:18080` → `tailscale serve` HTTPS), `BL-OPS-05~07·10`, SP-1(Flutter Web 입력), `BL-CLI-02~03`(dev 로그인 화면) |
| 4 | CI `main`·`developer` 정리(`BL-OPS-06`), DeepSeek smoke(RestClient `/responses`, `fake` provider와 동일 파싱 확인), spike ADR 작성, 데모 |

데모 체크리스트:
- [ ] `https://<tailnet-host>` 인증서 유효(Tailscale 발급, 브라우저 경고 없음), `/actuator/health` → `{"status":"UP"}`, `/actuator/env` → 404, `curl http://127.0.0.1:18080/actuator/health`(서버 안)도 UP
- [ ] SCR-LOGIN(dev 모드)에서 allowlist 이메일 입력 → `POST /api/v1/dev/token` → 화면에 `/api/v1/me` 응답(sub) 표시. 토큰 없이 `/api/v1/me` → 401 ProblemDetail. allowlist 밖 이메일 → 403 `USER_NOT_ALLOWED` (AC-25)
- [ ] `feature/*` → `developer` PR 1개와 `developer` → `main` PR 1개 모두 `ci.yml` 4개 job green, gitleaks·actions SHA 고정 검사 통과
- [ ] `v0.0.1` tag → `release.yml`이 linux/amd64 이미지를 GHCR에, web zip을 release에 올림 → 로컬에서 `deploy.sh v0.0.1` → health 통과. 기동에 실패하는 `v0.0.2`를 `deploy.sh v0.0.2`로 배포하면 `v0.0.1`로 자동 롤백(2분 이내)
- [ ] 서버의 `ss -ltnp`에서 DevPilot 관련 listen은 `127.0.0.1:18080`뿐(api 8080 publish 없음), tailnet 밖(휴대폰 LTE)에서 `https://<tailnet-host>` 연결 실패
- [ ] `devpilot-healthping.timer`가 5분마다 healthchecks.io로 ping(대시보드에서 확인), `devpilot-backup.timer` 등록 확인(첫 실행은 S2까지)
- [ ] SP-1~SP-4 결과 표와 ADR 갱신 PR merge(SP-5는 취소)

### 3.3 S1 — Identity · Onboarding · Plan · 사이드 프로젝트

데모 체크리스트:
- [ ] allowlist 밖 이메일로 `POST /api/v1/dev/token` → 403, SCR-NOT-ALLOWED, DB `app_user` 0행. allowlist 이메일은 첫 `GET /me`에서 JIT 생성(`AUTH_USER_PROVISIONED` 감사 로그)
- [ ] 온보딩 완료(3단계 "자기평가"를 고른 경우) → plan v1(템플릿 9개 milestone — 기반 다지기 → … → 설명과 정리, 1~6 MUST·7~9 SHOULD) 표시, `GET /skills/me`에 자기평가가 planning level로 반영(최대 3). 응답 `assignedSeedCardCount = 0`, `latestRiskLevel = null`(S1 생략 단계)
- [ ] 온보딩 3단계에서 "진단"을 고르면 `selfAssessments = []`로 저장되고 4축 0에서 시작(진단 풀이는 S3). 4단계 사이드 프로젝트 "주문 시스템" 생성 → SCR-PROJECTS에 표시, 이름 수정·삭제, 건너뛰기도 가능(AC-27)
- [ ] `GET /me` `aiStatus = DISABLED`, `aiUsage`는 0·예산값만(AI 호출 없음)
- [ ] milestone 상태 변경(PATCH) 후 버전 증가 없음, milestone 추가(replan) 후 v2 ACTIVE·v1 SUPERSEDED 이력 표시
- [ ] 설정에서 하루 시작 시각 변경 → `GET /me` 반영
- [ ] 같은 plan에 replan 2개 동시 요청 → ACTIVE plan 1개 (AC-24 테스트 결과 첨부)
- [ ] 테스트 계정으로 `DELETE /me` → 202·`DELETION_REQUESTED`, 이후 `GET /today` 403(삭제 job은 Later, 데이터는 남는다)
- [ ] §2.2 단계 회고 기록

### 3.4 S2 — Today · Review · 기한 역산

- S2부터 `REVIEW_ANSWERED`, `SESSION_*` 이벤트가 쌓인다. skill 레벨 갱신은 S3(`BL-SKL-05`)부터 적용되고, S2 이벤트도 60일 창 안에서 규칙 입력이 된다.
- S1에 온보딩한 사용자의 seed 카드는 S2 첫 배포 기동 시 배정된다(`BL-MEM-08`).
- **S2부터 budget·risk를 계산한다**(`BL-GOL-08~14`). 사용자가 등록한 목표일이 첫 Today부터 planner 우선순위(`DEADLINE_RISK_MUST`)에 반영되고, replan 화면이 촉박하면 축소·defer를, 여유가 있으면 확장(복원·목표 +1)을 제안한다(`06` §4.4). 복습 due 목록은 교차 학습 재배치(`RV-INTERLEAVE`, `06` §6.5)를 거친다.
- 일일 자동 백업(`BL-OPS-11`: `backup.sh` → `pg_dump -Fc` → `/opt/devpilot/backups`, `devpilot-backup.timer` 19:00 UTC)을 S2 안에서 활성화하고, 로컬 PC에서 `pull-backup.ps1`로 받은 첫 파일로 `db-restore` 절차의 로컬 복구를 1회 확인한다. 휴대폰 PWA는 Tailscale 앱이 켜져 있어야 접속된다.

데모 체크리스트 (휴대폰 PWA + 데스크톱, 둘 다 tailnet 안):
- [ ] 30분·NORMAL로 Today 생성 → main 1개, reason 1~3개, 합계 시간 ≤ 33분. 사이드 프로젝트가 ACTIVE면 `PROJECT_TASK` 제안 문구에 프로젝트 이름이 들어간다(SP-2)
- [ ] 시작 → 세션 완료(실제 시간 입력) → task COMPLETED → 재생성 요청 시 409 안내
- [ ] 휴대폰 홈 화면 PWA로 복습 5장 완료, 힌트 보기 → `HARD` 상한 조정 사유 표시. 같은 skill 카드가 3장 연속으로 나오지 않는다(바꿀 카드가 있을 때, AC-29)
- [ ] 00:00~03:59 KST에 조회 시 전날 plan-day로 표시 (AC-17)
- [ ] `GET /plans/active/budget` 값이 수동 계산(`06` §3~4 공식)과 일치. Today 응답의 `deadline_risk`가 같은 값
- [ ] 편집 중 risk HIGH → 제안(MUST 목표 축소 먼저 → SHOULD defer, `06` §4.4) 체크 → 새 버전 저장 → risk 재계산. 목표일을 멀리 옮겨 risk LOW·ratio ≤ 7000 → 확장 제안(복원·목표 +1) → 받아들여 새 버전(`acceptedTargetRaises`, AC-30)
- [ ] 로컬 dayStartHour 시각 이후 snapshot 생성 확인(`plan_progress_snapshot` 오늘 날짜)
- [ ] Dashboard 최소 화면에 오늘 상태·due 수·7일 학습 시간 표시
- [ ] healthchecks.io 알림 수신 테스트(`devpilot-healthping.timer`를 잠시 stop → 누락 알림 → start → 복구 알림)
- [ ] 전날 `pg_dump` 파일이 `/opt/devpilot/backups/`에 있고(7일 보관), `pull-backup.ps1`로 `%USERPROFILE%\DevPilot-backups\`에 사본, `pg_restore`로 로컬 복구 성공
- [ ] §2.2 단계 회고 기록

### 3.5 S3 — AI Platform · Training · 러버덕 · 코드 읽기

- 순서: `BL-AIP-15`(DeepSeek 잔액 소액 충전·`AiBalanceCheckJob`·동의 문구)와 `BL-OPS-15`(키 유출 runbook) 완료 전에는 prod provider를 `deepseek`로 바꾸지 않는다. 개발·테스트는 `fake`로 진행하고 실제 호출은 eval(`ai-eval.yml`, 1회 상한 USD 0.5, `BL-AIP-13`)에서만 한다.
- 가장 무거운 단계다(항목 수는 `13-product-backlog.md` §5.1). 러버덕(`BL-RDK-*`)은 `AiGateway`(`BL-AIP-07`)·가드(`BL-AIP-08`)·`SecretMasker`(`BL-AIP-09`) 뒤에 붙는다. P1·P2 이월 순서는 같은 문서 §5.
- 코드 읽기는 서버가 저장소에 접근하지 않는다. 사용자가 `cloneHint`로 로컬에 clone하고 IDE로 읽는다(`07` §5.5, `19` §3.8).
- **S3 시작 전 첫 소스 점검**(`19` §8.5, `BL-CNT-16`): `READ_CODE`가 처음 제안되기 전에 현재 저장소 3개·읽기 단위 13개를 점검한다. 에이전트가 빈틈 목록(skill별 reading 유무)과 저장소의 라이선스·유지 상태를 모아 추가·교체·은퇴 제안을 만들고, 사용자가 고른 것만 `content/curated-repos.yaml`에 반영한다(`pinnedCommit` 고정, 경로·줄 재확인, `validate_content.py`, `catalogVersion` +1). 라이선스가 없는 저장소(`restbucks`)가 먼저 교체 후보다. 이 시점에는 읽기 평가가 아직 없다.

데모 체크리스트:
- [ ] seed challenge 풀이: self-explanation → hint 2단계(AI 호출 0회) → 제출 → 평가 COMPLETED → rubric coverage·outcome 표시
- [ ] `PSEUDOCODE` hint 확인 대화상자 없이 요청 불가, 확인 후 공개
- [ ] 평가 FAILED/PARTIAL → 다음 plan-day due 복습 카드 생성
- [ ] 온보딩에서 "진단"을 고른 사용자에게 진단 challenge 제안 → 1문제 통과 → 해당 skill K·I가 `DIAG_PASSED`로 올라감(AC-11)
- [ ] Today에 `READ_CODE` 과제(planning level ≥ 1인 skill, RC-3) → SCR-READ-CODE에 저장소·`pinnedCommit`·경로·줄 범위·질문 표시, **코드 본문 없음**, 서버 외부 요청 0건(AC-28)
- [ ] 러버덕: 설명 → AI 질문(물음표로 끝남, 정답 단정 없음) 3턴 → "모르겠다" 2연속이면 hint 안내 → 종료 → gaps가 복습 카드로, `RUBBER_DUCK_COMPLETED` 기록 → Today 완료 기록(읽기 평가 "도움 됐어요"·"어려웠어요"·"지루했어요" 중 선택 또는 생략) → `READ_CODE` 과제 COMPLETED(RC-1), 평가는 `learning_task.reading_feedback`에 저장(AC-26, AC-28)
- [ ] skill 상세에 레벨 변경 이력(rule_code) 표시
- [ ] 수동 카드 생성 → 다음 plan-day에 due
- [ ] provider=`disabled`로 재배포 → Today·Review·Plan·기한 역산 정상, Today에 CHALLENGE·READ_CODE 제안 없음, 러버덕 시작 버튼 비활성, challenge 생성·제출 503 안내 (AC-12)
- [ ] 설정 화면 AI 사용량(오늘 호출 수, 이번 달 비용/USD 3, 잔액 상태) 표시. `ai_call_log`의 `provider = deepseek`, `cost_micro_usd`가 peak ×2 공식과 일치
- [ ] `rubber.duck`·`rubber.duck.summary` eval 1회 결과(합격 기준 충족 여부)를 PR에 첨부
- [ ] 첫 소스 점검(`19` §8.5) 결과 — 제안 목록과 사용자 결정, 반영한 콘텐츠 PR — 가 기록되어 있다
- [ ] §2.2 단계 회고 기록

**실사용 시작 조건 (M1 완료)**: S0~S3 exit criteria 전부 통과. 통과한 날을 **실사용 시작일**로 `20-decisions-and-risks.md`에 기록한다 — stop-loss(§6)와 성공 지표(`01`)의 기준일이다. 미달이면 미달 AC와 대체 사용 방법(예: Today·Review만 먼저 사용)을 같은 곳에 기록한다.

### 3.6 S4 — Project Coach

데모 체크리스트:
- [ ] SCR-COACH-NEW에 DeepSeek 동의 문구("코드는 AI 공급자 DeepSeek(중국 소재)로 전송되며 … 회사 코드를 붙여넣지 마세요.")가 동의 체크 옆에 표시된다
- [ ] 본인 사이드 프로젝트(예: 주문 시스템) 코드로 self-review 작성(사이드 프로젝트 선택) → 분석 → finding 카드(배지 4종) 확인, 수정 코드 없음
- [ ] `password=` 문자열 포함 코드 → 가린 개수 표시, private key 블록 → 422, DeepSeek 키 형식(`sk-` + hex 32자) → 마스킹
- [ ] finding 응답 → 피드백, hint, 해결/해당 없음 → complete → discoveredBy 표시, MISSED·FOUND_AFTER_HINT인 BUG/RISK finding의 복습 카드가 다음 plan-day due로 생성
- [ ] 원문 삭제 버튼 → content null, finding 유지
- [ ] coach eval case 실행 결과 요약(합격 기준 충족 여부)을 PR에 첨부
- [ ] 실사용 시작 후 1주 동안 CSP Report-Only 위반 0건 → `Content-Security-Policy` 강제 전환(`BL-SEC-08`)

### 3.7 S5 — Weekly · Dashboard · 캘린더 · AI 문제 생성

데모 체크리스트:
- [ ] provider `fake`의 402·`content_filter` fixture로 AC-12 S5 행(잔액 소진 → `BALANCE_EXHAUSTED`, 거절 → `AI_REFUSED` 재시도 불가) 확인 결과 첨부
- [ ] 월요일 dayStartHour 이후 지난주 weekly review 생성, 회고 작성
- [ ] Dashboard에 risk 추세·약한 thinking 축 표시
- [ ] 캘린더 구독 URL 발급 → 캘린더 앱에 Today 일정 표시
- [ ] AI challenge 생성(`BL-TRN-04`) → `ChallengeValidationService` 통과 후 VALIDATED, Today 제안에 포함
- [ ] 실측 2주 `ai_call_log`로 `17` §11 비용 표 갱신(`BL-AIP-14`)

### 3.8 S6 — Evidence · Hardening

데모 체크리스트:
- [ ] 독립 해결 이벤트로 evidence 초안 → 편집 → accept → skill `evidence_count` 증가
- [ ] 학습 기록(STAR) Markdown export 다운로드
- [ ] `GET /me/export` JSON에 모든 섹션 존재
- [ ] `account-deletion` runbook 리허설: 테스트 계정 `DELETE /me` → allowlist 제거 → 재기동 → 그 이메일로 `/api/v1/dev/token` 403(자동 삭제 job은 Later `BL-SEC-17`)
- [ ] 백업 보관 개수(서버 7일·로컬 8주)·healthchecks.io 알림 확인, 복구 리허설(`db-restore`) 기록표 1행
- [ ] CSP 강제 상태에서 전체 화면 콘솔 위반 0건

### 3.9 S7 — 로드맵 비교

- 분류 규칙은 `06-learning-engine-rules.md`의 `RequirementFitClassifier` 절이다. 확률·점수·퍼센트는 어떤 응답에도 넣지 않는다.

데모 체크리스트:
- [ ] 공개 학습 로드맵이나 기술 목록 붙여넣기 → 항목별 READY/STRETCH/LATER, 화면·응답에 숫자 점수 없음
- [ ] `sourceUrl` 입력 후에도 서버 외부 요청 없음(로그·테스트 결과)
- [ ] 비교한 로드맵(요구사항 문서) 삭제 → 항목 함께 삭제

### 3.10 공개 전환 게이트 (단계 밖)

**Tailscale 전용은 목표가 아니라 임시 수단이다. 최종은 공개 배포다.**

- **현재 (공개 전환 게이트 전)**: 자체 서버 + Tailscale HTTPS, 인증은 `devtoken`. 지금 서버 PostgreSQL을 테스트 DB로 써야 해서 이렇게 연결한 것이고, **임시 배포**다.
- **공개 전환 (M1 이후, 시점 미정)**: 자체 서버를 웹으로 공개하거나 OCI Always Free에 배포한다. 어느 쪽인지는 그때 정한다. 선행 조건 세 가지를 모두 채우기 전에는 공개하지 않는다.
  1. `BL-SEC-18`(공개 인증, P0) 완료 — `devtoken`은 "tailnet 밖에서 `/api/v1/dev/token`에 닿을 수 없다"는 전제 하나로만 안전하다(`03` §4.2). 후보: (a) backend가 GitHub OAuth를 직접 처리하고 현재 JWT 발급·검증 경로를 재사용(권장 — 외부 서비스 의존이 없고 devtoken 코드의 대부분을 그대로 쓴다) (b) Supabase Auth.
  2. TLS를 `tailscale serve`에서 **ACME**로 전환.
  3. Caddy를 공개 publish(현재는 `127.0.0.1:18080`에만 바인딩).
  - OCI Always Free는 arm64(Ampere)이므로 이미지를 `linux/amd64,linux/arm64` 멀티아치로 빌드해야 한다(`release.yml`).
- 이 전환은 **단계 범위가 아니라 별도 게이트**다. 단계 계획 자체는 바뀌지 않고, 게이트를 통과한 날부터 노출 방식만 바뀐다.

---

## 4. Spikes (S0)

공통 규칙:
- spike 코드는 `spike/SP-n-*` 브랜치에 두고 main에 merge하지 않는다. 결과만 ADR과 설정 파일로 반영한다.
- 결과는 "확인 항목 × 결과(통과/실패) × 증거(명령 출력, 스크린샷)" 표로 ADR PR에 첨부한다.
- 각 spike의 timebox를 넘기면 fallback을 적용하고 그 사실을 ADR에 기록한다.

### SP-1 Flutter Web 한글 입력·폰트 (`BL-CLI-01`, timebox 1.5일)

**목적**: 긴 한국어 서술(자기설명, 회고, self-review)과 코드 붙여넣기를 Flutter Web에서 손실 없이 입력할 수 있는지 확인한다. ADR-003의 채택 조건이다.

**절차**
1. 스파이크 화면 1개에 입력 3종을 둔다: (a) `TextField(maxLines: null, keyboardType: TextInputType.multiline)` 서술 입력, (b) monospace 폰트 코드 입력, (c) 한 줄 입력. 기본 font family를 번들 한글 폰트(Pretendard 또는 Noto Sans KR)로 지정한다.
2. `flutter build web --release`로 빌드해 S0 서버(`https://<tailnet-host>`, 또는 `flutter run -d chrome --release`)에서 연다. 엔진 리소스를 로컬 번들로 빌드하는 옵션을 적용한 빌드도 함께 만든다(`20-decisions-and-risks.md` §3.1 확인 항목).
3. 대상 브라우저: Windows Chrome, Windows Edge, iOS Safari(홈 화면 PWA 포함), Android Chrome.
4. 브라우저마다 다음 케이스를 실행하고 결과를 기록한다.

| # | 케이스 | 기대 결과 |
|---|---|---|
| a | "트랜잭션 전파 속성은" 입력 중 조합 상태에서 Backspace 3회 | 조합 중 글자만 지워지고 앞 글자 유지 |
| b | 문장 중간으로 화살표·터치 이동 후 `REQUIRES_NEW` 삽입 | 커서 위치에 정확히 삽입 |
| c | 단어 더블클릭/길게 눌러 선택 후 한글로 치환 | 선택 영역만 치환 |
| d | 탭과 4칸 들여쓰기가 섞인 200줄 Java 코드 붙여넣기 | 줄 수 200, 탭 문자 수·전체 문자 수가 원본과 동일 |
| e | 한글 5,000자 붙여넣기 후 끝에서 입력 | 지연 없이 입력, 문자 수 일치 |
| f | 조합 중 다른 창/앱으로 전환 후 복귀 | 확정된 글자 손실 없음(조합 중이던 1글자 손실은 허용) |
| g | 캐시 비운 첫 로드 (DevTools "Fast 4G" 제한) | 한글 깨짐(□)·폰트 교체 깜박임 없음, 로그인 화면 표시까지 6초 이내 |

**합격 기준**: 4개 브라우저 모두 a~e 통과, f는 확정 글자 손실 없음, g 통과. 외부 폰트 도메인 요청 0건.

**Fallback**: (1) 실패가 특정 입력 유형에 한정되면 그 입력만 `HtmlElementView` 기반 `<textarea>`로 바꾸고 같은 케이스를 재실행한다. (2) 일반 텍스트 입력 a~c가 2개 이상 브라우저에서 실패하고 (1)로 해결되지 않으면 웹 클라이언트를 다른 웹 스택으로 바꾸는 ADR을 새로 만들고 ADR-003을 Superseded로 바꾼다. API·백엔드는 바꾸지 않는다.

**ADR 기록**: ADR-003 (통과 시 Context에 SP-1 결과 추가, 실패 시 Superseded + 신규 ADR)

### SP-2 서버 PostgreSQL 16 + 원격 Docker Testcontainers (`BL-OPS-01`, 완료 2026-09-18)

**목적**: 개발·운영 DB인 서버 PostgreSQL 16(공용 인스턴스, DB `devpilot`/role `devpilot`, TLS 없음 — tailnet 구간 암호화)에서 Flyway migration 전체가 적용되고, 로컬 PC가 Docker Desktop 없이 서버 Docker로 Testcontainers를 실행할 수 있는지 확인한다.

**확인된 것 (2026-09-18)**
1. 서버 PG 16에 `devpilot` DB·role 생성, 현재 스키마(PG13+ 기능만) 전체 migration 적용·`ddl-auto=validate` 통과. 접속값은 `DevPilot-ops/02-database.md`.
2. 로컬 PC: `infra/scripts/dev-docker-tunnel.ps1`이 `ssh -N -L 2375:/var/run/docker.sock <user>@<server>`를 띄우고 `DOCKER_HOST=tcp://localhost:2375`, `TESTCONTAINERS_HOST_OVERRIDE=<server tailnet IP>`를 안내 → Testcontainers `postgres:16` 컨테이너의 publish 포트가 tailnet에서 열림(서버 `tailscale0`이 firewalld trusted zone).
3. 개발 DB와 Testcontainers를 같은 major(16)로 고정. 17 전용 기능을 쓰면 개발 DB에서 깨지므로 금지.

**남은 확인 (S0 안, `BL-FND-06`)**: `V1__baseline.sql`의 Supabase 하드닝 DO 블록이 순수 PG 16에서 no-op으로 통과하는지(AC-20 S1), Hikari `maximum-pool-size=5`로 동시 20요청 × 60초 연결 오류 0건, 공용 인스턴스의 다른 DB(타 사용자 6개)에 영향 없음(`devpilot` role 권한이 자기 DB에 한정).

**Fallback**: 서버 Docker 터널을 쓸 수 없는 환경(SSH 불가) → `infra/compose.dev.yml`(오프라인 대체, `postgres:16`, 포트 55432)로 로컬 PG를 띄운다. 서버 PG 장애 → `db-restore` runbook(`10-deployment-and-operations.md`).

**ADR 기록**: ADR-001 Consequences("서버 PG 16, Supabase는 Later"), `18-project-setup-and-local-dev.md` §3 절차

### SP-3 amd64 이미지 → GHCR → 서버 `deploy.sh` + Tailscale HTTPS (`BL-OPS-02`, timebox 2일)

> 아키텍처: 현재 서버는 **amd64**다. 공개 전환 후보인 OCI Always Free는 **arm64(Ampere)**이므로, 게이트(§3.10)를 통과할 때 `release.yml`을 `linux/amd64,linux/arm64` 멀티아치 빌드로 바꾼다. 이 spike는 amd64만 확인한다.

**목적**: `release.yml`이 만든 linux/amd64 이미지를 서버가 pull해 Caddy(`127.0.0.1:18080`, TLS 없음) 뒤에서 기동하고, 호스트의 `tailscale serve`가 HTTPS를 붙여 tailnet 안 브라우저에서 유효한 인증서로 접속되는지, 수동 배포·자동 롤백 경로가 동작하는지 확인한다. 서버는 Rocky 9.8 x86_64, 2 vCPU/8GB(가용 4.7GB), Docker 29.6.1, Tailscale 1.98.8, 80/443 비어 있음, 8080~8084·8090·5432 사용 중(공용).

**절차**
1. `release.yml`의 build job을 `ubuntu-24.04`에서 실행한다: `./gradlew bootJar` → `docker build --platform linux/amd64` → `ghcr.io/<owner>/devpilot-api:v0.0.1` push(tag는 `^v0\.\d+\.\d+$` 형식만 허용), Flutter web 빌드 zip을 release 아티팩트로 올린다. `docker buildx imagetools inspect`로 `linux/amd64` manifest를 확인하고 빌드 시간을 기록한다.
2. 서버에서 root로 `infra/scripts/prepare-server.sh` 1회 실행: 전제 조건 확인(docker, tailscale, 18080 비어 있음) → `deploy` 사용자(+docker 그룹) → `/opt/devpilot/{backups,web}` + `api.env`·`ops.env` 템플릿(0600) → systemd 유닛(`devpilot-backup`, `devpilot-healthping`) 설치·enable. OS 설정(daemon.json, sshd, firewalld, `tailscale up`)은 건드리지 않는다.
3. 호스트에서 1회 `tailscale serve --bg --https=443 http://127.0.0.1:18080`(tailnet의 MagicDNS + HTTPS Certificates가 켜져 있어야 한다). `tailscale serve status`로 확인.
4. `/opt/devpilot/api.env`를 채우고(`DATABASE_*`, `DEVPILOT_DEV_JWT_KEY` PEM, `DEVPILOT_ALLOWED_EMAILS`, `DEVPILOT_LOG_HASH_KEY`, `APP_BASE_URL=https://<tailnet-host>`) 로컬에서 `ssh <server>` → `sudo -u deploy /opt/devpilot/deploy.sh v0.0.1`을 실행한다(web zip 다운로드 → `/opt/devpilot/web`, GHCR pull → `docker compose -f /opt/devpilot/compose.prod.yml up -d` → `curl http://127.0.0.1:18080/actuator/health` 60초). compose를 직접 실행하지 않는다.
5. tailnet 안 다른 기기에서 확인: `curl -sI https://<tailnet-host>`(유효 인증서, `-k` 없이), `curl https://<tailnet-host>/actuator/health` → UP, `/actuator/env` → 404. 서버 안에서 `ss -ltnp`로 DevPilot listen이 `127.0.0.1:18080`뿐인지, `api` 컨테이너가 publish하지 않는지 확인. tailnet 밖(휴대폰 LTE)에서 연결 실패.
6. 메모리: `JAVA_TOOL_OPTIONS=-Xmx1g`, `mem_limit 1.5g`(api)·`128m`(caddy)에서 기동 후 `docker stats`로 30분 사용량을 기록한다(공용 서버, AlwaysPreTouch 없음).
7. 롤백: 기동에 실패하는 이미지(예: 필수 설정 누락)로 `v0.0.2`를 만들어 `deploy.sh v0.0.2`를 실행하고, `deploy.sh`가 이전 태그(`v0.0.1`)로 되돌려 2분 안에 health UP이 되는지 확인한다.
8. `devpilot-healthping.timer`(5분)가 health 200이면 `HEALTHCHECKS_PING_URL`을, 실패면 `${HEALTHCHECKS_PING_URL}/fail`을 호출하는지 healthchecks.io 대시보드에서 확인한다.

**합격 기준**: amd64 빌드 15분 이내, 브라우저 신뢰 인증서(Tailscale 발급), 서버 listen은 `127.0.0.1:18080`뿐, tailnet 밖 접속 불가, `deploy.sh` 수동 배포 성공, 자동 롤백 2분 이내, api 컨테이너 RSS ≤ 1.5GB, healthchecks.io ping 수신.

**Fallback**: `tailscale serve`가 인증서를 받지 못함(HTTPS Certificates 미활성) → tailnet 관리 콘솔에서 활성화 후 재시도. 443이 이미 점유됨 → `--https=8443`으로 임시 운영하고 URL에 포트를 붙인다. GHCR pull 실패(package private) → GHCR package를 public으로 바꾸거나 서버에 read-only PAT 로그인. 메모리 부족(OOM) → `-Xmx768m`로 낮추고 결과를 ADR-030에 기록. Actions → tailnet 자동 배포는 Later(`BL-OPS-22`)이므로 SP-3 범위가 아니다.

**ADR 기록**: ADR-030(자체 서버 + Tailscale HTTPS, 메모리 설정), ADR-032(수동 배포·백업)

### SP-4 Boot 4.1 + Java 25 툴체인 호환 (`BL-FND-01`, timebox 1.5일)

**목적**: Java 25 toolchain에서 품질 게이트 도구와 주요 라이브러리가 실제로 동작하는지 확인한다. 하나라도 실패하면 Java 21로 내린다(DEC-02).

**이미 확인된 것 (2026-09-17/18)**: `repo-seed` 빌드 파일로 Gradle 9.7.1 + Spring Boot 4.1.1 + Temurin 25.0.4에서 `compileJava`(-Werror), Spotless 8.10.2(google-java-format 1.36.1 AOSP), Checkstyle 14.1.0, PMD 7.27.0, SpotBugs 4.10.4, `bootJar`가 통과했다(09-17). 6(RestClient + DeepSeek `/responses` smoke)과 7(서버 Docker 터널로 Testcontainers 실행)은 09-18에 확인했다. 아래 절차 중 1~2는 이 결과를 재현만 하고, **남은 확인은 3(위반 검출), 4(ArchUnit 1.5.0 실행), 5(springdoc 3.1.1)**이다.

**절차**
1. Spring Initializr에서 Boot 4.1.x 최신 patch, Java 25, Gradle Kotlin으로 생성한다(의존성 목록은 `18-project-setup-and-local-dev.md`). 생성 결과를 수정 없이 첫 커밋한다.
2. Spotless(google-java-format `.aosp()`), Checkstyle(명명 규칙 설정), PMD 7, SpotBugs, JaCoCo 플러그인을 추가하고 `./gradlew spotlessCheck check`를 실행한다.
3. **도구가 실제로 검사하는지** 확인한다: 위반 샘플 브랜치(필드 주입, 빈 catch, `printStackTrace`, 한글 식별자, wildcard import, 포맷 위반)를 만들어 각 도구가 해당 위반을 보고하는지 확인한다. SpotBugs는 리포트의 분석 클래스 수가 0보다 큰지 확인한다(Java 25 class file을 읽지 못하면 조용히 0개가 된다).
4. ArchUnit: Java 25로 컴파일된 클래스를 import하는 규칙 테스트 1개가 통과하고, 위반 샘플에서 실패하는지 확인한다.
5. springdoc-openapi(Boot 4 호환 major): local profile에서 `/v3/api-docs` 생성, 스냅샷 task로 `docs/api/openapi.yaml` 생성.
6. (완료 2026-09-18) Java SDK 없이 Spring `RestClient`로 DeepSeek `POST /responses` + `text.format {type: json_schema}`를 실제 키로 호출해 구조화 출력·`usage`(cached tokens, reasoning tokens)를 확인했다. 결정적 테스트는 `MockRestServiceServer`(`DeepSeekAiProviderRequestTest`)로 한다. 별도 라이브러리·WireMock 추가 없음.
7. (완료 2026-09-18) 서버 Docker 터널(`dev-docker-tunnel.ps1`)로 Testcontainers `postgres:16` 통합 테스트 1개 실행. S0 안에 `./gradlew check` 전체 시간을 기록한다.

**합격 기준**: Java 25 toolchain에서 `spotlessCheck`, `checkstyleMain`, `pmdMain`, `spotbugsMain`(분석 클래스 > 0), `test`(ArchUnit, Testcontainers 포함) 모두 통과. 각 도구가 심어 둔 위반을 검출. springdoc 스펙 생성. 런타임 `NoSuchMethodError`/`ClassNotFoundException` 0건.

**Fallback**: 하나라도 실패하고 timebox 안에 수정 릴리스가 없으면 toolchain을 Temurin 21로 바꾸고 Boot 4.1은 유지한다. 도구를 끄는 방식으로 Java 25를 유지하지 않는다(ADR-019).

**ADR 기록**: ADR-005(적용 Java 버전과 근거), ADR-019(도구 버전)

### SP-5 ~~취소~~ Supabase Auth 단독 운용 (`BL-SEC-01`, 취소 2026-09-18)

공개 인증 도입이 단계 범위 밖(`BL-SEC-18` — 공개 전환 게이트의 필수 선행, §3.10)으로 미뤄지면서 취소됐다(결정 A: 현재 운영 인증도 `devtoken` 모드). 원래 절차(Data API 비활성 상태의 GitHub OAuth 로그인·토큰 갱신, JWKS `alg`·`iss`·`aud` 확정, 가입 비활성, 타 프로젝트 토큰 401)는 `BL-SEC-18` 착수 시 spike로 되살린다. `devtoken` 모드의 확인 항목은 spike가 아니라 AC-25(`12-acceptance-criteria.md`)로 S0에서 자동 테스트한다.

**ADR 기록**: ADR-033(devtoken 운영 인증), ADR-014·ADR-023은 Supabase 도입 시 재적용

---

## 5. 구현 분담

**규칙 클래스를 포함해 DevPilot 구현은 전부 에이전트가 한다.** 사용자 작업은 결정(ADR 승인), PR 리뷰, 계정·서버 설정뿐이다.

2026-09-18 전제: DevPilot은 **이걸로 공부해서 실력을 만드는 학습 도구**이고, DevPilot을 만드는 일 자체는 학습 과제가 아니다. 그래서 "핵심 규칙은 사용자가 직접 구현한다"는 근거가 없고, 직접 구현은 RISK-01(DevPilot 개발이 학습 시간을 잠식, 확률 H·영향 H)을 키우기만 한다. 사용자 시간은 DevPilot **으로 공부하는 데** 쓴다(`20-decisions-and-risks.md` RISK-01).

- (선택) Planner(`06` §5), Review scheduler(`06` §6), Skill updater(`06` §7), Budget·risk(`06` §3~4) 네 영역은 구현은 에이전트가 하되 `06`의 test vector 테스트가 함께 붙으므로, 나중에 Java·Spring 학습 소재로 읽을 수 있다. **일정 기준이 아니다.**
- 사용자 리뷰 없이 merge하지 않는다(§2.1). 리뷰에서 볼 것: N-1~N-7(부동소수점·나눗셈 함수·`multiplyExact`), 설정값 하드코딩, 무인자 시간 호출, 누락 vector, 트랜잭션 범위·N+1.

---

## 6. Stop-loss 규칙 (RISK-01)

**판정 시점: 실사용 시작일(§3.5 끝, M1 완료)부터 14 plan-day가 지난 다음 plan-day.** 날짜를 미리 정하지 않는다. 판정 기준은 prod DB 조회로 확인한다(`:start` = 실사용 시작일, `:end` = `:start` + 13일).

| # | 조건 | 확인 방법 |
|---|---|---|
| C-1 | M1 exit criteria(AC-02, AC-05, AC-10, AC-11, AC-17, AC-26, AC-28)가 prod 배포 상태에서 통과 | S0~S3 데모 체크리스트 기록 |
| C-2 | 14 plan-day 중 본인 `daily_plan`이 있는 날 ≥ 7일 | `select count(distinct plan_date) from devpilot.daily_plan where user_id = ? and plan_date between :start and :end` |
| C-3 | 같은 기간 `review_answer`가 있는 plan-day ≥ 7일 | `review_answer.plan_date` 기준 같은 쿼리 |

- **셋 중 하나라도 불충족이면 발동한다.**
- 경고(발동 조건 아님): 같은 기간 `COMPLETED` 러버덕 세션이 3개 미만이면 핵심 루프가 쓰이지 않는 것이다. `20`에 기록하고 러버덕 진입 경로(SCR-TODAY·SCR-READ-CODE)의 사용성부터 본다.
- 발동 시 조치:
  1. `COA`·`REQ` epic 전체, `BL-EVD-04~08`, `BL-CLI-24`, `BL-CLI-27`, `BL-CLI-29`, `BL-CNT-11~13`, `BL-FND-17`, `BL-FND-19`를 Later로 옮긴다. Weekly(`BL-EVD-01~03`)와 `BL-FND-18`은 유지하고, `BL-CLI-26`에서는 thinking 추세 표시만 뺀다.
  2. M2 단계를 다음으로 바꾼다: S4 = M1 미완료 항목 + Today·Review·러버덕 사용성 개선, S5 = Weekly·Dashboard(축소), S6 = export·백업·하드닝(변경 없음), S7 = 사용 데이터 기반 튜닝.
  3. 결정을 `20-decisions-and-risks.md`에 날짜와 판정 수치와 함께 기록하고 RISK-01 상태를 갱신한다.
- 미발동이어도 C-2 또는 C-3이 7~9일이면 경고로 기록하고, 14 plan-day 뒤에 같은 판정을 1회 더 한다.

---

## 7. 에이전트 작업 지시 템플릿

S0는 저장소 루트의 `START_HERE_FOR_AI_AGENT.md`(`repo-seed/`에서 복사)로 시작한다. S1부터는 아래 템플릿으로 BL 단위 작업을 지시한다.

규칙:
- 한 지시 = BL 1개, 또는 같은 단계·같은 Epic에서 서로 의존하는 BL 최대 3개.
- DoR(`16-definition-of-ready-done.md` §1)을 통과하지 못한 BL은 지시하지 않는다.
- 브랜치는 `feature/BL-<EPIC>-<nn>-<슬러그>`(수정·잡무·문서는 `fix/`·`chore/`·`docs/`)에서 시작해 `developer`로 PR(squash merge)한다. `main`은 `developer`에서만 PR + merge commit으로 받는다(`16-definition-of-ready-done.md` §3, DEC-21).

```text
[작업] <단계(S0~S7)> / <BL-ID…> <제목>
[목표] <이 작업이 끝나면 사용자가 할 수 있는 것 1문장>

[읽을 문서]
- AGENTS.md
- docs/03-system-architecture.md §<클래스·트랜잭션 관련 절>
- docs/04-domain-model-and-db.md §<enum·상태 전이·JSON 절>
- docs/05-api-spec.md <해당 endpoint 절>
- docs/06-learning-engine-rules.md §<규칙 절> (규칙 작업일 때)
- docs/12-acceptance-criteria.md <AC-ID>
- docs/<07|08|09|17|19 중 해당 문서>

[범위]
- <만들 클래스·파일·endpoint·migration을 이름으로 나열>
- <작성할 테스트: 테스트 클래스 이름, vector 출처>

[범위 밖]
- <이번에 하지 않는 BL, 화면, 리팩터링, 의존성 추가>

[제약]
- 이름은 03 §3과 15-glossary.md 명명 사전을 따른다. 새 이름이 필요하면 멈추고 질문한다
- 주입된 Clock, devpilot.* 설정값, 정수 연산(N-1~N-7), 트랜잭션 밖 AI 호출(T-2)
- 적용된 migration 수정 금지, 새 라이브러리 추가 금지(필요하면 멈추고 질문)
- Boot 4.1 / Java 25 기준. 의존성 좌표·설정 이름을 기억으로 쓰지 않고 공식 문서나 실제 jar로 확인

[완료 조건]
- ./gradlew spotlessCheck check 통과 (ArchUnit·PMD·SpotBugs·Checkstyle 포함)
- <AC-ID>의 Given/When/Then을 테스트로 구현, test vector 100% 통과
- API 변경 시 docs/api/openapi.yaml 갱신, DB 변경 시 migration + database/schema.sql 갱신
- 사용자 격리 테스트 목록에 새 endpoint 추가 (BL-SEC-05)
- 16-definition-of-ready-done.md §2 DoD 체크리스트 전 항목

[중단 조건] — 아래 중 하나면 구현을 멈추고 보고한다
- 문서 간 모순, 문서에 없는 결정 필요 (ADR 초안 Proposed 작성 후 질문)
- 테스트를 통과시키려면 테스트를 약화하거나 삭제해야 함
- 범위 밖 파일을 5개 넘게 바꿔야 함
- 새 외부 서비스·라이브러리·설정 키가 필요함
- 같은 컴파일·테스트 오류로 3회 연속 실패

[보고 형식]
Changed:          변경 파일과 요약
Tests:            추가·수정 테스트, 실행 결과 (통과 수 / 전체)
Security impact:  인증·인가·로그·입력 검증 영향, 없으면 "없음"과 이유
Docs updated:     갱신한 문서·절, OpenAPI·schema.sql 변경 여부
Decisions needed: 사용자 결정이 필요한 항목 (선택지와 권장안)
Known limitations: 남은 제약, 후속 BL 후보
```
