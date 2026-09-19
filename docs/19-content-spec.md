# 19. Content Spec (Seed 콘텐츠)

> Status: Accepted (v3) · Last updated: 2026-09-19 · Related: DEC-14, DEC-27, DEC-28, ADR-039, `03-system-architecture.md` §2.2·§9, `04-domain-model-and-db.md` §3·§5·§9, `05-api-spec.md` §4.2, `06-learning-engine-rules.md` §4·§5·§6.3·§7·§8·§9·§10·§11, `17-ai-integration.md` §6, `database/schema.sql`
>
> 저장소 `content/`의 **seed 파일 형식, 검증 규칙(CV-xx), 적재·은퇴 규칙, plan template 날짜 배치 알고리즘, 작성 가이드, 현재 인벤토리**를 정의한다. 초기 파일은 문서 세트의 `repo-seed/content/`에 있다.

---

## 1. 목적

DevPilot의 결정적 규칙은 seed 값이 없으면 동작하지 않는다. seed 콘텐츠는 AI가 꺼져 있거나 예산을 초과해도 계획·오늘 과제(READING/EXPLAIN/RECALL)·복습이 돌아가게 하는 최소 입력이고, AI가 켜져 있을 때는 문제 생성(`CHALLENGE_GENERATE`) 비용 없이 훈련을 시작하게 하는 문제 은행이다. challenge 평가(`CHALLENGE_EVALUATE`)는 seed challenge도 AI를 쓰므로, AI가 꺼져 있으면 Today는 CHALLENGE task를 제안하지 않는다.

| seed 값 | 소비하는 규칙 |
|---|---|
| skill tree (`minutesPerLevelStep`, prerequisites, description) | required minutes (`06` §4.2), prerequisiteReadiness (`06` §5.2·§5.4), READING/EXPLAIN task 설명 (`06` §5.3) |
| role target (priority, importance, 4축 target) | plan 생성 시 `plan_skill_target` 복사 (`06` §11.2), risk (`06` §4), planner factor (`06` §5.4) |
| plan template | 온보딩·`POST /plans`의 milestone 생성 (§5), milestone urgency (`06` §5.4) |
| seed review card | 온보딩 시 `review_item` 복사, 첫 due 분산 (`06` §6.3) |
| seed challenge | task 제안 (`06` §5.3, AI 사용 가능할 때), coverage 판정 (`06` §8.1), hint 1~3단계 (`06` §9.1 HL-6), 진단 (`06` §7.4) |
| curated source | `VerificationGuard`의 `CURATED_SOURCE` 근거 ID (`06` §10) |
| curated repo · reading | `READ_CODE` task 제안과 estimatedMinutes (`06` §5.3), 러버덕 시작 질문 (`06` §9.5 RD-2) |

이 문서가 정하는 것: 파일 스키마와 DB 매핑(§3 — `curated-repos.yaml`은 §3.8), 검증 규칙(§4), 날짜 배치(§5), 난이도(§6), 작성 가이드(§7), 버전·은퇴와 줄 번호 관리(§8 — pinnedCommit은 §8.4), 소스 점검(§8.5), 자기평가 전파 계약(§9), 우선순위 조정(§10), 체크리스트(§11), 인벤토리(§12), 기준 문서 반영 기록(§13).

---

## 2. 디렉터리 구조

```text
content/
├── catalog.yaml                     # catalogVersion, 파일 목록, 진단 카테고리, 은퇴 식별자
├── skill-tree/
│   └── java-backend.yaml            # skill 88개 (root 13 + non-root 75)
├── role-targets/
│   └── java-backend.yaml            # JAVA_BACKEND role target 75개
├── plan-templates/
│   └── java-backend.yaml            # JAVA_BACKEND_DEFAULT milestone 9개
├── review-cards/
│   ├── java.yaml  spring.yaml  database.yaml  web-http.yaml  testing.yaml
│   └── security.yaml  practical-engineering.yaml  devops.yaml  network-cs.yaml  explanation.yaml
├── challenges/
│   ├── java.yaml  spring.yaml  database.yaml  testing-security.yaml   # PRACTICE 18개
│   └── diagnostic.yaml                                                # DIAGNOSTIC 5개
├── curated-sources.yaml             # curated source 19개
├── curated-repos.yaml               # READ_CODE 저장소 9개 + reading 41개(활성 36, 은퇴 5) (§3.8)
└── tools/
    └── validate_content.py          # 구현 전 기준 검증기 (§4.3). 런타임에 복사하지 않는다
```

| 항목 | 규칙 |
|---|---|
| 빌드 | 원본은 저장소 루트 `content/` 한 곳이다. Gradle `processResources`가 `../content`를 빌드 결과 `build/resources/main/content/`로 복사하고(`*.yaml`, `*.yml`만 포함하므로 `content/tools/**`는 빠진다), 소스 트리 `backend/src/main/resources/content/`는 만들지 않는다(`04` §9, `18-project-setup-and-local-dev.md` §2·§4.3) |
| 런타임 위치 | `devpilot.content.location` (기본 `classpath:content/`), curated source는 `devpilot.ai.curated-sources-location` (`03` §9) |
| 읽는 파일 | `YamlContentReader`는 `catalog.yaml`의 `files`에 나열된 파일만 나열 순서대로 읽는다. 디렉터리 스캔을 하지 않는다 |
| 모듈 | `content` 모듈(`ContentSeeder`, `ContentValidator`, `YamlContentReader`)이 읽고 검증한다. 의존 대상은 `common`, `skill`, `plan`, `review`, `training`뿐이다(`03` §2.2). curated source 파일은 `integration.ai`가 직접 읽고, `ContentValidator`는 같은 파일을 검증만 한다 |
| 저장 여부 | skill, role target, challenge는 DB에 upsert한다. plan template, review card, curated repo/reading은 공용 테이블이 없다(§3.4, §3.5, §3.8) |

---

## 3. 파일 스키마

### 3.0 공통 규칙

| 항목 | 규칙 |
|---|---|
| 인코딩 | UTF-8, LF, 들여쓰기 2칸 |
| YAML 기능 | anchor(`&`), alias(`*`), tag(`!`), 다중 문서(`---`)를 쓰지 않는다 |
| 키 | lowerCamelCase. 스키마에 없는 키는 오류(CV-03) |
| 따옴표 | `@`, `` ` ``, `*`, `&`, `!`, `%`, `{`, `[`, `#`, `'`, `"`로 시작하거나 `: `, ` #`를 포함하는 문자열은 따옴표로 감싼다. 긴 문장은 `>-`, 줄바꿈이나 코드가 있는 문장은 `\|-` 블록을 쓴다 |
| 언어 | 한국어 문장, 식별자·API·enum·기술 용어는 영어 원문 |
| 파서 | SnakeYAML safe 로딩(임의 타입 생성 금지)으로 읽는다. 라이브러리 좌표·버전은 Spring Boot 4.1 의존성 관리 기준으로 구현 시 확인 |
| 소수 | `importance`는 YAML 스칼라 원문 문자열을 `new BigDecimal(text)`로 변환한다. `double`을 거치지 않는다(`06` §1 N-1). micro 변환은 `importance × 1_000_000` (정수로 떨어짐, CV-22) |

### 3.1 `catalog.yaml`

| 필드 | 타입 | 필수 | 제약 | 용도 |
|---|---|---|---|---|
| `catalogVersion` | int | Y | ≥ 1 | upsert 판단, `skill.catalog_version`·`role_skill_target.catalog_version` 값 (§8) |
| `files.skillTrees` | string[] | Y | 1개 이상, content 루트 기준 상대 경로 | skill tree 파일 |
| `files.roleTargets` | string[] | Y | 1개 이상 | role target 파일 |
| `files.planTemplates` | string[] | Y | 1개 이상 | plan template 파일 |
| `files.reviewCards` | string[] | Y | 1개 이상 | review card 파일 |
| `files.challenges` | string[] | Y | 1개 이상 | challenge 파일 |
| `files.curatedSources` | string | Y | | curated source 파일 |
| `files.curatedRepos` | string | Y | | curated repo/reading 파일 (§3.8) |
| `diagnosticCategories` | SkillCategory[] | Y | enum 값 | DIAGNOSTIC challenge가 반드시 있어야 하는 카테고리 (§9, CV-59) |
| `retired.skillCodes` | string[] | Y | 빈 목록 허용 | 은퇴한 skill code. 재사용 금지 (§8.3) |
| `retired.challengeSeedKeys` | string[] | Y | | 은퇴한 challenge seedKey |
| `retired.conceptKeys` | string[] | Y | | 은퇴한 review card conceptKey |
| `retired.curatedSourceIds` | string[] | Y | | 은퇴한 curated source ID |
| `retired.readingKeys` | string[] | Y | | 은퇴한 reading key (§3.8). 재사용 금지. 은퇴한 reading의 정의는 `curated-repos.yaml`에 `retired: true`로 남는다(§8.2) |

### 3.2 `skill-tree/*.yaml`

최상위 키 `skills` (목록). 파일 안 순서와 `catalog.yaml`의 파일 순서를 이어 붙인 0부터의 index가 `skill.sort_order`다.

| 필드 | 타입 | 필수 | 제약 | DB 매핑 |
|---|---|---|---|---|
| `code` | string | Y | `^[A-Z][A-Z0-9_]*(\.[A-Z][A-Z0-9_]*)*$`, ≤ 100자, 세그먼트 ≤ 3, 전체 유일 | `skill.code` (upsert 키) |
| `name` | string | Y | 1~200자 | `skill.name` |
| `category` | SkillCategory | Y | 13개 enum (`04` §3) | `skill.category` |
| `parent` | string | root: 없음, non-root: Y | 존재하는 code, 같은 category, `code == parent + "." + SEGMENT` | `skill.parent_id` (code → id) |
| `description` | string | Y | 10~300자, 1~2문장 | `skill.description` (READING task 설명에도 쓰임, `06` §5.3) |
| `minutesPerLevelStep` | int | non-root: Y, root: 선택(기본 120) | non-root **60**~2000 (§7.5 작성 규칙을 CV-15가 강제), root 10~2000 | `skill.minutes_per_level_step` |
| `prerequisites` | string[] | non-root: Y(빈 목록 허용), root: 금지 | 존재하는 non-root code, 자기 자신·중복 금지, 순환 금지 | `skill_prerequisite(skill_id, prerequisite_skill_id)` |
| — | | | | `skill.catalog_version = catalogVersion`, `skill.active = true` |

- **root skill**: `code == category`, `parent` 없음. 카테고리 묶음 노드이며 role target, milestone, card, challenge에서 참조하지 않는다.
- **non-root skill**: 학습 단위. 자식이 있는 non-root(예: `JAVA.EXCEPTION`)는 자식과 겹치지 않는 기본 개념을 담당한다. 예: `JAVA.EXCEPTION`은 예외 설계, `JAVA.EXCEPTION.TRY_WITH_RESOURCES`는 자원 해제.

```yaml
skills:
  - code: SPRING.TRANSACTION
    name: Spring Transaction
    category: SPRING
    parent: SPRING
    description: '@Transactional의 적용 범위, 기본 롤백 규칙, 트랜잭션 경계 설계를 이해한다.'
    minutesPerLevelStep: 150
    prerequisites: [SPRING.AOP_PROXY, DATABASE.TRANSACTION]
```

### 3.3 `role-targets/*.yaml`

| 필드 | 타입 | 필수 | 제약 | DB 매핑 |
|---|---|---|---|---|
| `targetRole` | TargetRole | Y | `JAVA_BACKEND` | `role_skill_target.target_role` |
| `targets[].skill` | string | Y | 존재하는 non-root code, 역할당 non-root skill마다 정확히 1개 | `role_skill_target.skill_id` (키: `(target_role, skill_id)`) |
| `targets[].priority` | Priority | Y | `MUST`/`SHOULD`/`LATER` | `role_skill_target.priority` |
| `targets[].importance` | decimal | Y | 0.00~1.00, 소수 둘째 자리까지 | `role_skill_target.practical_importance` numeric(3,2) |
| `targets[].target.knowledge` | int | Y | 0~5 | `target_knowledge_level` |
| `targets[].target.implementation` | int | Y | 0~5 | `target_implementation_level` |
| `targets[].target.explanation` | int | Y | 0~5 | `target_explanation_level` |
| `targets[].target.debugging` | int | Y | 0~5, 네 축 합 ≥ 1 | `target_debugging_level` |
| — | | | | `role_skill_target.catalog_version = catalogVersion` |

```yaml
targetRole: JAVA_BACKEND
targets:
  - skill: SPRING.TRANSACTION
    priority: MUST
    importance: 0.95
    target: { knowledge: 4, implementation: 4, explanation: 4, debugging: 3 }
```

### 3.4 `plan-templates/*.yaml`

DB 테이블이 없다. `ContentSeeder`가 검증한 템플릿을 plan 모듈 application 계층의 템플릿 보관 컴포넌트에 메모리로 등록하고(§13 O-2), plan 생성 시 아래처럼 사용한다.

| 필드 | 타입 | 필수 | 제약 | 사용처 |
|---|---|---|---|---|
| `templateKey` | string | Y | `^[A-Z][A-Z0-9_]{2,59}$` | 식별·로그 |
| `targetRole` | TargetRole | Y | 역할당 템플릿 정확히 1개 | 학습 목표의 `target_role`(학습 트랙)로 선택 |
| `planTitle` | string | Y | 1~200자 | `learning_plan.title` |
| `placement.minMilestoneDays` | int | Y | 1~28 | §5 배치 알고리즘 |
| `milestones[].key` | string | Y | `^[A-Z][A-Z0-9_]{2,59}$`, 템플릿 안 유일 | 저장하지 않음 (템플릿 추적용) |
| `milestones[].title` | string | Y | 1~200자 | `plan_milestone.title` |
| `milestones[].description` | string | N | 1~2000자 | `plan_milestone.description` |
| `milestones[].priority` | Priority | Y | enum | `plan_milestone.priority` |
| `milestones[].weightBp` | int | Y | 100~10000, 템플릿 전체 합 = 10000 | §5 기간 배분 |
| `milestones[].phase` | enum | Y | `PREPARATION` \| `CONSOLIDATION`. PREPARATION이 모두 앞에 오고, 단계마다 1개 이상 | §5 배치 순서(창은 하나이고 `CONSOLIDATION`이 목표일 바로 앞에 온다) |
| `milestones[].skillCodes` | string[] | Y | 1~24개, role target이 있는 skill, 한 skill은 템플릿 전체에서 최대 1개 milestone, 모든 MUST skill은 반드시 포함 | `milestone_skill` |
| — | | | | `plan_milestone.start_date`/`end_date` = §5, `status = PLANNED`, `sort_order` = milestone index |

`phase`, `key`, `weightBp`는 DB에 저장하지 않는다. 사용자가 이후 milestone을 편집하는 규칙은 `06` §11이다.

### 3.5 `review-cards/*.yaml`

최상위 키 `cards` (목록). 공용 테이블이 없고 사용자 온보딩 시 사용자별 `review_item`으로 복사한다(`04` §9). 새 카드는 다음 기동 때 기존 사용자에게도 추가된다(같은 `concept_key`는 건너뜀).

| 필드 | 타입 | 필수 | 제약 | `review_item` 매핑 |
|---|---|---|---|---|
| `conceptKey` | string | Y | `^[A-Z0-9_.:-]{3,150}$`, 전체 유일, `{skill code}.`로 시작. `CHALLENGE:`(challenge 실패 항목, `06` §8.3)와 `COACH:`(coach finding 항목, `COACH:{skillCode}:{category}`) 접두사 금지 | `concept_key` |
| `skill` | string | Y | role target이 있는 non-root code | `skill_id` |
| `reviewType` | ReviewType | Y | `RECALL`/`BUG_SPOT`/`EXPLAIN`/`CHOICE` | `review_type` |
| `prompt` | string | Y | 10~1200자. BUG_SPOT은 fenced code block(≤ 15줄) 필수, CHOICE는 `A)`부터 연속된 선택지 3~5개 | `prompt` |
| `expectedAnswer` | string | Y | 10~1500자. CHOICE는 정답 label로 시작 | `expected_answer` |
| `rubric[]` | `{id, criterion}` | Y | 2~4개, id `R1..Rn` 순서, criterion 5~200자 | `rubric_json` (`04` §5.4) |
| — | | | | `origin = SEED`, `source_type = SEED_CARD`, `source_id = null`, `status = ACTIVE`, `interval_days = 1`, `variant_status = NONE`, `due_at` = `06` §6.3 |

```yaml
cards:
  - conceptKey: SPRING.TRANSACTION.SELF_INVOCATION
    skill: SPRING.TRANSACTION
    reviewType: EXPLAIN
    prompt: >-
      같은 클래스 안에서 ... 이유와 해결 방법을 설명하세요.
    expectedAnswer: >-
      기본(proxy) 모드의 선언적 트랜잭션은 ...
    rubric:
      - id: R1
        criterion: 프록시 기반 선언적 트랜잭션
      - id: R2
        criterion: 내부 호출은 프록시를 우회한다
```

### 3.6 `challenges/*.yaml`

최상위 키 `challenges` (목록). `challenge` 행과 `challenge_skill` 행으로 upsert한다(키: `seed_key`).

| 필드 | 타입 | 필수 | 제약 | `challenge` 매핑 |
|---|---|---|---|---|
| `seedKey` | string | Y | `^(PRACTICE\|DIAGNOSTIC)\.<code>\.L[1-5]\.[0-9]{3}$`, ≤ 100자, 유일. 접두사 = `purpose`, `L{n}` = `difficulty`. `<code>`는 PRACTICE면 대표 skill code, DIAGNOSTIC이면 category | `seed_key` |
| `purpose` | ChallengePurpose | Y | `PRACTICE`/`DIAGNOSTIC` | `purpose` |
| `skills` | string[] | Y | 1~3개, role target이 있는 non-root code, 중복 금지 | `challenge_skill` |
| `difficulty` | int | Y | 1~5 (§6) | `difficulty` |
| `estimatedMinutes` | int | Y | 5~180 | `estimated_minutes` |
| `isTransfer` | bool | Y | true면 difficulty ≥ 3 | `is_transfer` |
| `title` | string | Y | 1~200자 | `title` |
| `scenario` | string | Y | 20~3000자, 코드 포함 가능 | `scenario` |
| `prompt` | string | Y | 20~3000자, 설명 요구를 포함 | `prompt` |
| `constraints` | string[] | N (기본 `[]`) | 0~6개, 항목 1~300자 | `constraints_json` |
| `expectedConcepts` | string[] | Y | 2~8개 | `expected_concepts_json` |
| `rubric[]` | `{id, criterion, weightBp, axis}` | Y | 2~6개, id `R1..Rn`, weightBp 500의 배수 500~6000, 합 = 10000, axis ∈ RubricAxis, EXPLANATION 1개 이상 + IMPLEMENTATION/DEBUGGING 1개 이상 | `rubric_json` (`04` §5.2) |
| `commonMistakes` | string[] | Y | 1~6개 | `common_mistakes_json` |
| `transferTargets` | string[] | Y (빈 목록 허용) | 0~5개, role target이 있는 code, `skills`와 중복 금지 | `transfer_targets_json` |
| `hints` | `{QUESTION_ONLY, CONCEPT_HINT, DIRECTION}` | Y | 정확히 3개 키, 각 10~300자, 코드 금지(CV-56) | `hints_json` |
| — | | | | `owner_user_id = null`, `origin = SEED`, `status = VALIDATED`, `generation_status = COMPLETED`, `ai_call_id = null`, `prompt_version = null` |

- Hint 공개 시 `hint_disclosure.content_origin = SEED` (`06` §9.1 HL-6).
- migration 순서상 `challenge`는 V4, `challenge_skill`은 V5다(`04` §10). **challenge seed 적재는 V5가 적용된 S3부터** 한다. 적재 여부는 설정 `devpilot.content.seed-challenges`(`03` §9)로 정한다: S2 배포는 `false`(challenge 파일을 읽고 §4 검증은 하되 §3.9 6번 upsert를 건너뜀 — `challenge_skill` 테이블이 없어도 기동), S3(V5 적용)부터 `true`(기본값). S1은 skill·role target·template, S2는 review card를 적재한다.

### 3.7 `curated-sources.yaml`

최상위 키 `sources` (목록).

| 필드 | 타입 | 필수 | 제약 | 용도 |
|---|---|---|---|---|
| `id` | string | Y | `^CS-[A-Z0-9]+(-[A-Z0-9]+)*$`, ≤ 80자, 유일 | AI 출력의 `sourceReference`와 비교 (`06` §10 규칙 2) |
| `title` | string | Y | 1~200자 | 표시 |
| `url` | string | Y | https, 호스트가 `devpilot.ai.trusted-source-hosts`에 정확 일치 또는 하위 도메인 | 표시 (서버는 fetch하지 않음) |
| `publisher` | string | Y | 1~100자 | 표시 |
| `versionScope` | string | Y | 1~100자 (예: `Spring Framework 7.0.x`) | 표시 |
| `claim` | string | Y | 1~300자, 문서가 직접 뒷받침하는 문장만 | 표시, prompt 컨텍스트 |
| `verifiedAt` | date | Y | `YYYY-MM-DD`, 작성자가 URL과 claim을 직접 확인한 날 | 재검증 주기 판단 |

### 3.8 `curated-repos.yaml`

최상위 키는 `repos`, `readings` 둘이다. `READ_CODE` 과제(`06` §5.3·§9.5 RC-1~RC-4)가 "무엇을 왜 읽는지"를 여기서 가져온다. **DB 테이블이 없다.** plan template·review card와 같이 `ContentSeeder`가 검증한 뒤 메모리에 등록하고, planner가 skill code로 찾아 쓴다.

**서버는 이 저장소를 fetch하지 않는다**(`07` §5.5). 사용자가 `cloneHint`로 직접 clone해서 IDE로 읽는다. 따라서 AI 비용도 네트워크 호출도 0이다.

`repos[]`

| 필드 | 타입 | 필수 | 제약 | 용도 |
|---|---|---|---|---|
| `key` | string | Y | `^[a-z][a-z0-9-]{1,29}$`, 파일 안 유일 | `readings[].repo` 참조 키 |
| `name` | string | Y | 1~200자 | 화면 표시, task title |
| `url` | string | Y | https URL | 화면 표시 (fetch하지 않는다) |
| `subPath` | string | Y | 상대 경로, `..` 세그먼트·선행 `/` 금지. 저장소 루트면 `""` | `readings[].path`의 기준 디렉터리 |
| `pinnedCommit` | string \| null | N (기본 null) | 40자 소문자 hex SHA 또는 null | 줄 번호의 기준 커밋 (§8.4). null이면 WARN |
| `license` | string | Y | 1~50자. 명시가 없으면 `UNSPECIFIED` | 코드 인용 가능 여부 판단 |
| `stack` | string | Y | 1~200자 | 화면 표시 |
| `why` | string | Y | 10~500자 | 왜 이 저장소를 읽는지 |
| `cloneHint` | string | Y | 10~500자. `pinnedCommit`이 있으면 그 커밋을 체크아웃하게 쓴다 | RC-4 화면 안내 |
| `licenseNote` | string | N | 1~500자 | 라이선스 주의사항(예: 명시 없음 → 읽기만) |

`readings[]`

| 필드 | 타입 | 필수 | 제약 | 용도 |
|---|---|---|---|---|
| `key` | string | Y | `^READ\.[A-Z][A-Z0-9_]*\.[A-Z][A-Z0-9_]*\.[0-9]{3}$`, ≤ 100자, 전체 유일, `retired.readingKeys`에 없음 | 완료 이력 키, 제안 정렬 키(`06` §5.3) |
| `repo` | string | Y | 존재하는 `repos[].key` | 어느 저장소인가 |
| `path` | string | Y | 1~300자, `subPath` 기준 상대 POSIX 경로. `..` 세그먼트·선행 `/`·`\` 금지 | 읽을 파일 1개 (RC-2) |
| `lines` | int[2] | Y | `[시작, 끝]`, 둘 다 ≥ 1, 시작 ≤ 끝 | 읽을 범위 1개 (RC-2) |
| `skillCodes` | string[] | Y | 1~4개, 중복 금지, role target이 있는 skill | planner 후보 매칭(`06` §5.3) |
| `estimatedMinutes` | int | Y | 5~60 | `learning_task.estimated_minutes`에 **그대로** 들어간다 |
| `question` | string | Y | **40~300자.** 답을 유도하지 않는 열린 질문 | 러버덕 세션의 시작점 (RD-2) |
| `lookFor` | string[] | Y | 1~5개, 항목 5~200자 | 무엇을 눈여겨볼지. 정답이 아니라 관점이다 |
| `retired` | bool | N (기본 false) | `true`면 key가 `retired.readingKeys`에 있어야 한다(CV-83) | 은퇴한 단위. planner가 제안하지 않지만(`06` §5.3) `GET /readings/{key}`는 계속 돌려준다 — 지난 과제·러버덕 세션이 가리키는 단위를 잃지 않게(§8.2) |

```yaml
repos:
  - key: petclinic
    name: Spring PetClinic
    url: https://github.com/spring-projects/spring-petclinic
    subPath: ""
    pinnedCommit: 818c4136ea971c21674525f9053de0d9c7ad8cfe
    license: Apache-2.0
    stack: "Spring Boot 4.1, Java 17, Spring Data JPA"
    why: "Spring 공식 샘플. 계층 구조와 테스트 작성법의 정석이고, 테스트 코드가 main보다 많다."
    cloneHint: "git clone https://github.com/spring-projects/spring-petclinic.git && cd spring-petclinic && git checkout 818c4136ea971c21674525f9053de0d9c7ad8cfe"
readings:
  - key: READ.PETCLINIC.CONTROLLER_SLICE.001
    repo: petclinic
    path: src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java
    lines: [48, 122]
    skillCodes: [SPRING.MVC_REST, SPRING.VALIDATION]
    estimatedMinutes: 15
    question: "이 컨트롤러는 Repository를 직접 주입받고 Service 계층이 없습니다. 이렇게 두어도 괜찮은 경우와 곤란해지는 경우를 나눠서 설명해 보세요."
    lookFor:
      - 계층을 나누는 목적
      - 트랜잭션 경계가 어디에 생기는가
      - 지금 내 프로젝트는 어느 쪽에 가까운가
```

**작성 규칙**

| 항목 | 규칙 |
|---|---|
| 경로·줄 번호 | **추측하지 않는다.** `pinnedCommit`으로 체크아웃한 사본에서 파일을 열어 범위를 눈으로 확인하고 적는다. 범위는 의미 단위(클래스 선언~메서드 끝)로 끊는다 |
| 범위 크기 | 한 과제가 `estimatedMinutes` 안에 끝나야 한다. 대략 40~90줄이 적당하고, 120줄을 넘으면 두 reading으로 쪼갠다 |
| 질문 | 답이 이미 들어 있는 질문("왜 EAGER가 N+1을 일으킬까요?")을 쓰지 않는다. 사용자가 코드를 보고 스스로 판단할 여지를 남긴다. 40자 미만은 CV-86 오류다 |
| 저장소당 개수 | 3~5개. 벗어나면 CV-87 WARN |
| 라이선스 | `license`가 `UNSPECIFIED`인 저장소는 **경로·줄 번호·질문만** 적는다. 코드를 이 파일이나 문서에 옮겨 적지 않는다 |
| 커버되지 않는 것 | 2026-09-19 점검 기준으로 조회 튜닝(인덱스·실행 계획), 오류 응답 설계(ProblemDetail), REST API 설계, Git 협업, EXPLANATION 카테고리는 reading이 없다. 읽기가 아니라 **직접 구현**, 기존 challenge 콘텐츠, 개념 읽기(curated source)로 채운다(milestone 6이 측정을 근거로 삼는 이유). 빈 곳을 채울 저장소·단위는 소스 점검(§8.5)에서 찾는다 |
| 저장소·단위 교체 | 추가·교체·은퇴는 소스 점검(§8.5)에서 사용자와 정한 뒤에만 한다 |

### 3.9 적재 순서 (`ContentSeeder`, 한 트랜잭션)

```text
0. devpilot.content.seed-on-startup = false 이면 종료
1. YamlContentReader: catalog.yaml → 나열된 파일 읽기
2. ContentValidator: §4 전체. ERROR 1개 이상 → 기동 실패, WARN → 로그 CONTENT_VALIDATION_WARNING
3. dbVersion = coalesce(max(skill.catalog_version), 0)
   catalogVersion < dbVersion → 적재하지 않고 WARN CONTENT_SEED_SKIPPED_OLDER_CATALOG, 7번(메모리 등록)만 수행
4. skill upsert (code 키): 1차로 parent 없이 upsert → 2차로 parent_id 설정 → skill_prerequisite를 YAML 집합으로 교체
   - 기존 skill의 category가 YAML과 다르면 기동 실패 (SD-01)
   - DB에 active인데 YAML에 없는 skill → active = false. retired.skillCodes에 없으면 WARN CONTENT_IMPLICIT_RETIREMENT
5. role_skill_target upsert ((target_role, skill_id) 키). YAML에 없는 행은 삭제하지 않는다
6. challenge upsert (seed_key 키, owner_user_id null, origin SEED) — V5 적용 후.
   devpilot.content.seed-challenges = false 이면 이 단계 전체를 건너뛰고 INFO CONTENT_CHALLENGE_SEED_SKIPPED 를 남긴다 (S2 배포, §3.6)
   - 구조 필드(skills, difficulty, purpose, isTransfer, expectedConcepts, rubric 항목의 id·weightBp·axis와 항목 수·순서) 중 하나라도 DB와 다르면 기동 실패 (SD-02, §8.1, `04` §9)
   - 텍스트 필드(title, scenario, prompt, constraints, commonMistakes, hints, rubric criterion 문구)와 비구조 필드(estimatedMinutes, transferTargets)는 갱신
   - YAML에 없는 seed challenge → status = RETIRED, status_updated_at = now. YAML에 다시 나타난 RETIRED → VALIDATED
7. plan template, review card, curated repo·reading 목록을 메모리에 등록
8. 새 seed card를 기존 온보딩 완료 사용자에게 추가 (`06` §6.3 "신규 seed 카드" 행). 이미 있는 concept_key는 건너뜀
```

---

## 4. ContentValidator 규칙

### 4.1 규칙표

Severity `ERROR`는 기동 실패와 CI 실패, `WARN`은 로그만 남긴다. Java `ContentValidator`는 아래 ID를 그대로 오류 코드로 쓰고, 규칙마다 실패 fixture 테스트를 1개 이상 둔다.

**Catalog·공통**

| ID | Sev | 규칙 |
|---|---|---|
| CV-01 | ERROR | `catalog.yaml`이 있고 필수 키(`catalogVersion`, `files`, `diagnosticCategories`, `retired`)가 있다. `catalogVersion`은 정수 ≥ 1. `diagnosticCategories`는 SkillCategory 값 |
| CV-02 | ERROR | `files`의 각 목록은 비어 있지 않고 중복이 없다. 나열된 파일이 존재하고 YAML로 파싱된다 |
| CV-03 | ERROR | 각 객체는 매핑이고, 스키마(§3)에 없는 필드가 없으며 필수 필드가 null이 아니다 |
| CV-04 | ERROR | (파일시스템 검사, CI 전용) `content/` 하위(`tools/` 제외)의 모든 `.yaml`이 `files`에 나열되어 있다. classpath 실행에서는 건너뛴다 |

**Skill tree**

| ID | Sev | 규칙 |
|---|---|---|
| CV-10 | ERROR | `code`가 `^[A-Z][A-Z0-9_]*(\.[A-Z][A-Z0-9_]*)*$`, ≤ 100자, 세그먼트 ≤ 3 |
| CV-11 | ERROR | `code`가 모든 skill tree 파일에서 유일하고 `retired.skillCodes`에 없다 |
| CV-12 | ERROR | `category`가 SkillCategory 값. parent가 없는 skill은 `code == category`. 13개 category 모두 root가 있다 |
| CV-13 | ERROR | `parent`가 존재하고 같은 category이며 `code == parent + "." + SEGMENT` |
| CV-14 | ERROR | `name` 1~200자, `description` 10~300자 |
| CV-15 | ERROR | `minutesPerLevelStep`이 정수이고 non-root는 필수·**60~2000**(§7.5 "60 미만은 쓰지 않는다"를 강제), root는 선택·10~2000 |
| CV-16 | ERROR | `prerequisites`는 목록(non-root 필수). 항목은 존재하는 code, 자기 자신·중복 금지 |
| CV-17 | ERROR | prerequisite 그래프에 순환이 없다(DFS, 오류 메시지에 순환 경로 출력) |
| CV-18 | ERROR | prerequisite로 쓰인 skill의 `target.implementation ≥ 2`. (readiness는 planning IMPLEMENTATION ≥ 2로 판정하므로 `06` §5.4, 그보다 낮은 target은 증거로 영원히 준비 상태가 되지 않는다) |
| CV-19 | ERROR | root skill은 prerequisite를 갖지 않고, prerequisite로 참조되지 않는다 |

**Role target**

| ID | Sev | 규칙 |
|---|---|---|
| CV-20 | ERROR | `targetRole`이 TargetRole 값. `skill`이 존재하는 non-root code |
| CV-21 | ERROR | 역할마다 non-root skill 하나당 target이 정확히 1개(누락·중복 금지) |
| CV-22 | ERROR | `priority` ∈ Priority. `importance`는 0.00~1.00이고 소수 둘째 자리까지 |
| CV-23 | ERROR | 네 축 target이 정수 0~5이고 합이 1 이상 |
| CV-24 | WARN | DEC-14 기본값과 다르다: ALGORITHM 카테고리가 SHOULD가 아니거나 EXPLANATION 카테고리가 MUST가 아니다 (§10.1에 따라 의도적으로 바꾼 경우 경고를 허용) |

**Plan template**

| ID | Sev | 규칙 |
|---|---|---|
| CV-30 | ERROR | `templateKey` 패턴, `targetRole` enum, `planTitle` 1~200자. TargetRole마다 템플릿이 정확히 1개 |
| CV-31 | ERROR | milestone 1~24개. `key` 패턴·유일, `title` 1~200자, `description` 1~2000자(있을 때) |
| CV-32 | ERROR | `weightBp` 정수 100~10000, 템플릿 전체 합 = 10000 |
| CV-33 | ERROR | `phase` enum. PREPARATION이 모두 CONSOLIDATION보다 앞. 두 단계 모두 1개 이상 |
| CV-34 | ERROR | milestone `priority` ∈ Priority |
| CV-35 | ERROR | `skillCodes` 1~24개, 모두 role target이 있는 skill, milestone 안 중복 금지, 한 skill은 템플릿 전체에서 최대 1개 milestone |
| CV-36 | ERROR | 해당 역할의 모든 MUST skill이 어떤 milestone에 들어 있다 |
| CV-37 | ERROR | `placement.minMilestoneDays` 정수 1~28 |

**Review card**

| ID | Sev | 규칙 |
|---|---|---|
| CV-40 | ERROR | `conceptKey`가 `^[A-Z0-9_.:-]{3,150}$`, 모든 카드 파일에서 유일, 예약 접두사 `CHALLENGE:`·`COACH:`로 시작하지 않음, `retired.conceptKeys`에 없음 |
| CV-41 | ERROR | `skill`이 role target이 있는 skill이고 `conceptKey`가 `skill + "."`로 시작 |
| CV-42 | ERROR | `reviewType` ∈ ReviewType |
| CV-43 | ERROR | `prompt` 10~1200자, `expectedAnswer` 10~1500자 |
| CV-44 | ERROR | rubric 2~4개, id `R1..Rn` 순서, criterion 5~200자 |
| CV-45 | ERROR | BUG_SPOT의 prompt에 fenced code block이 있고 코드가 15줄 이하 |
| CV-46 | ERROR | CHOICE의 prompt에 줄 시작 `A)`부터 연속된 선택지 3~5개, expectedAnswer가 존재하는 선택지 label로 시작 |
| CV-47 | ERROR | `R1` criterion이 expectedAnswer와 같지 않다(R1은 "힌트 보기"로 공개된다, §7.4) |
| CV-48 | WARN | MUST skill 중 seed card가 하나도 없는 skill이 있다 |
| CV-49 | WARN | BUG_SPOT이 아닌 카드 prompt에 fenced code block이 있다 |

**Challenge**

| ID | Sev | 규칙 |
|---|---|---|
| CV-50 | ERROR | `seedKey` 패턴(§3.6)·≤ 100자·유일·은퇴 목록에 없음. 접두사 = `purpose`, `L{n}` = `difficulty` |
| CV-51 | ERROR | `purpose` enum, `difficulty` 1~5, `estimatedMinutes` 5~180, `isTransfer` boolean |
| CV-52 | ERROR | `skills` 1~3개, 중복 없음, 모두 role target이 있는 skill |
| CV-53 | ERROR | `title` 1~200자, `scenario`·`prompt` 20~3000자. `constraints` 0~6, `expectedConcepts` 2~8, `commonMistakes` 1~6, `transferTargets` 0~5개, 항목은 1~300자 문자열 |
| CV-54 | ERROR | rubric 2~6개, id `R1..Rn`, criterion 5~200자, `weightBp` 500의 배수 500~6000, 합 = 10000(I-10), axis ∈ RubricAxis, EXPLANATION 1개 이상과 IMPLEMENTATION/DEBUGGING 1개 이상 |
| CV-55 | ERROR | `hints` 키가 정확히 `QUESTION_ONLY`, `CONCEPT_HINT`, `DIRECTION`이고 각 10~300자 |
| CV-56 | ERROR | hint에 백틱(`` ` ``)이 없고, §4.2 코드 줄 판정에 걸리는 줄이 0개 |
| CV-57 | ERROR | `QUESTION_ONLY`가 `?`로 끝나고, `expectedConcepts` 항목을 대소문자 무시 부분 문자열로 포함하지 않는다 |
| CV-58 | ERROR | `transferTargets`가 role target이 있는 skill이고 `skills`와 겹치지 않는다. `isTransfer = true`면 difficulty ≥ 3 |
| CV-59 | ERROR | DIAGNOSTIC은 difficulty 3, estimatedMinutes ≤ 15, isTransfer false, skills가 한 category이고 모두 role target priority `MUST`·importance ≥ 0.70. `diagnosticCategories`의 각 category에 DIAGNOSTIC이 1개 이상 |
| CV-60 | WARN | PRACTICE estimatedMinutes가 난이도별 상한 초과: L1 20, L2 30, L3 40, L4 60, L5 90 (§6) |
| CV-61 | ERROR | 템플릿 배치 smoke test: 고정 입력 4개(창 길이 1일(목표일 = 오늘), 10일, 31일, 211일)에서 §5 결과가 모두 `today ≤ start ≤ end ≤ targetCompletionDate` |

**Curated source**

| ID | Sev | 규칙 |
|---|---|---|
| CV-70 | ERROR | `id` 패턴·≤ 80자·유일·`retired.curatedSourceIds`에 없음 |
| CV-71 | ERROR | `url`이 https이고 호스트가 trusted host allowlist(정확 일치 또는 하위 도메인) |
| CV-72 | ERROR | `title` 1~200, `publisher` 1~100, `versionScope` 1~100, `claim` 1~300자, `verifiedAt` ISO 날짜 |

**Curated repo · reading (§3.8)**

| ID | Sev | 규칙 |
|---|---|---|
| CV-80 | ERROR | `curated-repos.yaml`의 최상위 키가 정확히 `repos`, `readings`이고 둘 다 비어 있지 않은 목록이다 |
| CV-81 | ERROR | `repos[].key`가 `^[a-z][a-z0-9-]{1,29}$`이고 파일 안에서 **유일**하다. `url`이 https다. `subPath`가 상대 경로이고 `..` 세그먼트·선행 `/`가 없다. `name` 1~200, `license` 1~50, `stack` 1~200, `why` 10~500, `cloneHint` 10~500자 |
| CV-82 | ERROR | `repos[].pinnedCommit`이 40자 소문자 hex SHA이거나 null(필드 생략 포함)이다. **null이면 WARN**을 함께 낸다 — 줄 번호가 고정되어 있지 않다는 뜻이다 (§8.4) |
| CV-83 | ERROR | `readings[].key`가 `^READ\.<REPO>\.<TOPIC>\.NNN$` 패턴·≤ 100자이고 파일 전체에서 **중복이 없다**. `retired: true`가 아닌 reading의 key는 `retired.readingKeys`에 없고, `retired: true`인 reading의 key는 `retired.readingKeys`에 **있다**. `retired.readingKeys`의 모든 key는 `retired: true` reading으로 남아 있다(은퇴 단위도 조회된다, §8.2) |
| CV-84 | ERROR | `readings[].repo`가 `repos[].key`에 **실재**한다 |
| CV-85 | ERROR | `readings[].path`가 1~300자 상대 POSIX 경로이고 `..` 세그먼트·선행 `/`·`\`가 **없다**. `lines`가 정수 2개 `[시작, 끝]`이고 **둘 다 양수, 시작 ≤ 끝(오름차순)**이다 |
| CV-86 | ERROR | `readings[].skillCodes`가 1~4개·중복 없음이고 **모두 role target이 있는 skill로 실재**한다. `estimatedMinutes` 5~60. `question` **40~300자**. `lookFor` 1~5개, 항목 5~200자 |
| CV-87 | WARN | 한 저장소의 `retired: true`가 아닌 reading 수가 3~5개 범위를 벗어난다. 은퇴하지 않은 reading이 0개인 저장소는 WARN 대상이 아니다(은퇴 단위를 위해서만 남은 저장소) |

**Seeder (DB 비교, `ContentSeeder`에서만 검사)**

| ID | 규칙 | 결과 |
|---|---|---|
| SD-01 | 기존 `skill.code`의 category가 YAML과 다르다 | 기동 실패 |
| SD-02 | 기존 seed challenge의 구조 필드(skills, difficulty, purpose, isTransfer, expectedConcepts, rubric 항목의 id·weightBp·axis)가 YAML과 다르다. rubric criterion 문구 차이는 대상이 아니다 | 기동 실패 |

### 4.2 Hint 코드 줄 판정 (CV-56)

AI hint의 코드 유출 판정은 `CodeLeakGuard`(`17-ai-integration.md` §6)가 정의하고, `DIRECTION` 이하 단계는 코드 줄 3줄 이상을 거절한다(`06` §9.1 HL-8). seed hint는 더 엄격하다.

- 백틱이 하나라도 있으면 실패한다.
- 줄 단위로 아래 패턴 중 하나라도 맞으면 코드 줄이다. 코드 줄이 **1줄이라도** 있으면 실패한다.
- Java 구현은 `CodeLeakGuard`의 코드 줄 판정 함수와 아래 패턴을 **둘 다** 적용하고, 어느 한쪽이라도 코드로 보면 실패로 처리한다. 따라서 CV-56을 통과한 seed hint는 `CodeLeakGuard`도 통과한다.

| # | 정규식 (줄 단위, Java는 `Pattern.UNICODE_CHARACTER_CLASS`) | 의미 |
|---|---|---|
| 1 | `;\s*$` | 문장 종료 |
| 2 | `\{\s*$` | 줄 끝 블록 시작 |
| 3 | `^\s*\}` | 블록 종료 |
| 4 | `\b[A-Za-z_]\w*\s*\([^()]*\)\s*(;\|\{\|->)` | 호출·선언 뒤 `;` `{` `->` |
| 5 | `\b[A-Za-z_]\w*\.[A-Za-z_]\w*\([^()]*\)` | `a.b(...)` 형태 호출 |
| 6 | `@[A-Za-z_]\w*\(` | 인자가 있는 애너테이션 |
| 7 | `=\s*new\s+[A-Z]\w*` | 객체 생성 대입 |
| 8 | `\b(public\|private\|protected)\s+(static\s+)?[\w<>\[\]]+\s+\w+\s*[(=;]` | 접근 제어자 선언 |
| 9 | `\b(SELECT\|UPDATE\|INSERT\|DELETE)\b.*\b(FROM\|SET\|INTO\|WHERE)\b` | 대문자 SQL 문 |

`\w`는 Python에서 유니코드 문자를 포함한다. Java는 `Pattern.UNICODE_CHARACTER_CLASS`를 켜서 같은 결과를 낸다.

### 4.3 기준 검증기 (`content/tools/validate_content.py`)

Java 구현 전에는 이 스크립트가 기준이다. Java `ContentValidator`는 같은 입력에 같은 규칙 ID를 보고해야 한다.

| 항목 | 내용 |
|---|---|
| 의존성 | Python 3.9+, 표준 라이브러리, PyYAML |
| 실행 | `python content/tools/validate_content.py [CONTENT_DIR] [--report] [--placement-vectors]` |
| 종료 코드 | 0: ERROR 없음(WARN 허용), 1: ERROR 1개 이상, 2: 사용법·IO 오류 |
| `--report` | §12 인벤토리 표와 budget 점검 표 출력 |
| `--placement-vectors` | §5.4 test vector 표 출력 |
| 구현 범위 | CV-01~CV-87 전부(CV-15 non-root 하한 60, CV-80~CV-87 curated repo 포함). SD-xx는 DB가 필요하므로 제외 |
| CI | content 검증 step에서 `python content/tools/validate_content.py`를 실행하고 종료 코드 1이면 실패 |

2026-09-18 실행 결과 (catalogVersion 3): `skills=88 roleTargets=75 templates=1 cards=82 challenges=23 curatedSources=10 curatedRepos=3 readings=13 catalogVersion=3` / `result: PASS (errors=0, warnings=0)`. 결함을 넣은 복사본으로 검증기 자체를 확인했다.

2026-09-19 실행 결과 (catalogVersion 5, 첫 소스 점검 반영 — §8.5 점검 기록): `skills=88 roleTargets=75 templates=1 cards=82 challenges=23 curatedSources=19 curatedRepos=9 readings=41 catalogVersion=5` / `result: PASS (errors=0, warnings=2)`. `readings`는 은퇴 단위 5개를 포함한다. WARN 2건은 CV-87(`petclinic` 활성 8개, `modular-monolith` 활성 7개)이고 사용자가 고른 단위 수가 권장 범위를 넘어서 난다.

| 넣은 결함 | 보고된 규칙 |
|---|---|
| `JAVA.OOP`와 `JAVA.EXCEPTION` 상호 prerequisite | CV-17 (순환 경로 출력) |
| `NETWORK.DNS`의 parent를 `CS`로 변경 | CV-13 (2건) |
| rubric weight 합 9500 | CV-54 |
| DIRECTION hint에 세미콜론으로 끝나는 코드 | CV-56 |
| 중복 conceptKey | CV-40 |
| importance 0.855, implementation target 6 | CV-22, CV-23 |
| catalog에 없는 `review-cards/stray.yaml` | CV-04 |
| conceptKey `COACH:...` | CV-40, CV-41 |
| DIAGNOSTIC skill에 SHOULD skill(`TESTING.TESTCONTAINERS`) | CV-59 |
| reading의 `repo`를 `nosuchrepo`로 변경 | CV-84 |
| reading `path`를 `../../etc/passwd`로, `lines`를 `[122, 48]`로 변경 | CV-85 (2건: `..` 세그먼트, 내림차순) |
| reading `lines`를 `[0, -3]`으로 변경 | CV-85 (양수 아님) |
| reading `skillCodes`에 `NOPE.FAKE_SKILL` 추가 | CV-86 |
| reading `question`을 7자로 줄임 | CV-86 |
| reading key 중복 | CV-83 |
| `pinnedCommit: null` | CV-82 (WARN) |
| `retired: true` reading의 key를 `retired.readingKeys`에서 뺌 (2026-09-19) | CV-83 (1건) |
| `retired.readingKeys`에 있는 key의 reading을 파일에서 지움 (2026-09-19) | CV-83 (1건, 위치 `catalog.yaml#retired.readingKeys`) |
| `retired.readingKeys`에 있는 key의 reading에서 `retired: true`를 뺌 (2026-09-19) | CV-83 (1건) + 그 저장소 활성 수에 따른 CV-87 WARN |
| 한 저장소의 활성 reading을 2개로 줄임 (2026-09-19) | CV-87 (WARN). 활성 0개인 `restbucks`는 WARN 없음 |

---

## 5. 계획 템플릿 배치 알고리즘 (plan template → milestone 날짜)

온보딩과 `POST /plans`에서 템플릿으로 milestone 날짜를 만든다. `06-learning-engine-rules.md`의 계획 규칙은 이 절을 기준으로 참조한다. 순수 Java 규칙 클래스(plan.domain, 이름은 §13 O-2)로 구현하고 §5.4 vector를 parameterized test로 옮긴다. 모든 계산은 `LocalDate`와 정수다.

### 5.1 입력과 전제

| 입력 | 값 |
|---|---|
| `today` | 요청 시점의 plan-day (`06` §2) |
| `targetCompletionDate` | 학습 목표(`learning_goal`), not null — 목표일 |
| `milestones` | 템플릿 순서 그대로 (`weightBp`, `phase`) |
| `minDays` | `placement.minMilestoneDays` |

- 전제: `today ≤ targetCompletionDate`. 아니면 배치를 호출하지 않고 `400 VALIDATION_FAILED`(field `targetCompletionDate`)로 거절한다.

### 5.2 창(window) 결정

```text
# 창은 하나다. 06 §3.1 horizonDate = targetCompletionDate 와 같은 끝
모든 milestone (템플릿 순서) → allocate([today, targetCompletionDate], weights = 전체 weightBp)
```

- 템플릿 순서는 CV-33이 보장한다: `PREPARATION` milestone이 모두 앞에, `CONSOLIDATION`("설명과 정리")이 맨 뒤에 온다. 그래서 한 창 안에서 앞부분은 만드는 milestone, 목표일 바로 앞은 설명과 정리 milestone이 된다.
- 분모는 템플릿 전체 weight 합(현재 10000)이다. `phase`는 배치 순서만 정하고 창을 나누지 않는다.

### 5.3 `allocate(windowStart, windowEnd, w[0..n−1], minDays)`

```text
D = daysBetween(windowStart, windowEnd) + 1          # 창의 날짜 수 (≥ 1)
W = Σ w[i]

# SEQUENTIAL: 모든 milestone에 minDays 이상을 주고 겹치지 않게 이어 붙인다
if D ≥ n × minDays:
    extra   = D − n × minDays
    base[i] = floorDiv(extra × w[i], W)
    rem[i]  = extra × w[i] − base[i] × W
    left    = extra − Σ base[i]                       # 0 ≤ left < n
    (rem DESC, i ASC) 순서로 앞의 left개 milestone에 +1
    len[i]  = minDays + base[i] + bonus[i]
    start[0] = windowStart
    end[i]   = start[i] + len[i] − 1
    start[i+1] = end[i] + 1                           # 마지막 end = windowEnd

# COMPRESSED: 날짜가 부족하면 같은 길이의 구간을 겹쳐서 배치한다
else:
    span  = min(minDays, D)
    slack = D − span
    C[i]  = Σ_{j<i} w[j]                              # 앞 milestone들의 누적 weight
    offset[i] = (n == 1 || slack == 0) ? 0 : floorDiv(C[i] × slack, C[n−1])
    start[i] = windowStart + offset[i]
    end[i]   = start[i] + span − 1                    # 마지막 end = windowEnd
```

성질 (테스트로 확인):
- 모든 milestone에 대해 `windowStart ≤ start ≤ end ≤ windowEnd`.
- SEQUENTIAL: 빈 날·겹침 없이 창 전체를 덮고, 각 길이 ≥ `minDays`.
- COMPRESSED: `start`가 단조 증가하고 각 길이 = `min(minDays, D)`. 목표일까지 남은 기간이 짧으면(현재 템플릿은 9 × 7 = 63일 미만) 이 모드가 된다. 구간이 겹칠 수 있고, `slack`이 크면 구간 사이에 빈 날이 생길 수도 있다(V6). `D < minDays`면 모든 milestone이 창 전체를 공유한다.
- 두 모드의 경계: `D = n × minDays`(현재 63일)는 SEQUENTIAL이고 모든 milestone이 정확히 `minDays`일이다(V8). 하루 짧은 62일은 COMPRESSED다(V6).
- 곱셈은 `Math.multiplyExact` (`06` §1 N-7).

### 5.4 Test vectors

템플릿 `JAVA_BACKEND_DEFAULT` (weightBp 템플릿 순서: 1200, 1200, 1500, 1500, 1200, 1100, 700, 800 (PREPARATION 8개) / 800 (CONSOLIDATION 1개), W = 10000, n = 9, `minDays` 7). 모든 vector는 창 하나 `[today, targetCompletionDate]`다. `python content/tools/validate_content.py --placement-vectors`로 재생성한다. 날짜는 알고리즘 검증용 **예시 입력**이다(사용자의 목표일과 무관).

**V1** today 2026-10-01, target 2027-04-30 (D=212 ≥ 63 → SEQUENTIAL, extra 149, base = 17, 17, 22, 22, 17, 16, 10, 11, 11 → Σbase 143, left 6 → rem 9200인 #8·#9, rem 8800인 #1·#2·#5, 그다음 rem 4300인 #7에 +1. rem 3900인 #6과 3500인 #3·#4는 +1 없음)

| milestone | start | end | 일수 |
|---|---|---|---|
| FOUNDATION_SETUP | 2026-10-01 | 2026-10-25 | 25 |
| MEMBER_AND_AUTH | 2026-10-26 | 2026-11-19 | 25 |
| CATALOG_AND_CRUD | 2026-11-20 | 2026-12-18 | 29 |
| ORDER_CREATION | 2026-12-19 | 2027-01-16 | 29 |
| CANCEL_AND_REFUND | 2027-01-17 | 2027-02-10 | 25 |
| QUERY_PERFORMANCE | 2027-02-11 | 2027-03-05 | 23 |
| STRUCTURE_CLEANUP | 2027-03-06 | 2027-03-23 | 18 |
| DEPLOY_AND_OPERATE | 2027-03-24 | 2027-04-11 | 19 |
| EXPLAIN_AND_CONSOLIDATE | 2027-04-12 | 2027-04-30 | 19 |

**V2** today 2026-10-01, target 2027-03-31 (D=182 → SEQUENTIAL, extra 119, base = 14, 14, 17, 17, 14, 13, 8, 9, 9 → Σbase 115, left 4 → rem 8500인 #3·#4, rem 5200인 #8·#9에 +1)

| milestone | start | end | 일수 |
|---|---|---|---|
| FOUNDATION_SETUP | 2026-10-01 | 2026-10-21 | 21 |
| MEMBER_AND_AUTH | 2026-10-22 | 2026-11-11 | 21 |
| CATALOG_AND_CRUD | 2026-11-12 | 2026-12-06 | 25 |
| ORDER_CREATION | 2026-12-07 | 2026-12-31 | 25 |
| CANCEL_AND_REFUND | 2027-01-01 | 2027-01-21 | 21 |
| QUERY_PERFORMANCE | 2027-01-22 | 2027-02-10 | 20 |
| STRUCTURE_CLEANUP | 2027-02-11 | 2027-02-25 | 15 |
| DEPLOY_AND_OPERATE | 2027-02-26 | 2027-03-14 | 17 |
| EXPLAIN_AND_CONSOLIDATE | 2027-03-15 | 2027-03-31 | 17 |

**V3** today 2026-10-01, target 2026-10-20 (D=20 < 9 × 7 = 63 → COMPRESSED, span 7, slack 13, C = 0, 1200, 2400, 3900, 5400, 6600, 7700, 8400, 9200, C[n−1] = 9200 → offset = 0, 1, 3, 5, 7, 9, 10, 11, 13)

| milestone | start | end |
|---|---|---|
| FOUNDATION_SETUP | 2026-10-01 | 2026-10-07 |
| MEMBER_AND_AUTH | 2026-10-02 | 2026-10-08 |
| CATALOG_AND_CRUD | 2026-10-04 | 2026-10-10 |
| ORDER_CREATION | 2026-10-06 | 2026-10-12 |
| CANCEL_AND_REFUND | 2026-10-08 | 2026-10-14 |
| QUERY_PERFORMANCE | 2026-10-10 | 2026-10-16 |
| STRUCTURE_CLEANUP | 2026-10-11 | 2026-10-17 |
| DEPLOY_AND_OPERATE | 2026-10-12 | 2026-10-18 |
| EXPLAIN_AND_CONSOLIDATE | 2026-10-14 | 2026-10-20 |

**V4** today 2026-10-01, target 2026-10-05 (D=5 < minDays → COMPRESSED, span 5, slack 0 → 9개 모두 2026-10-01 ~ 2026-10-05)

**V5** today 2026-10-01, target 2026-12-31 (D=92 → SEQUENTIAL, extra 29, base = 3, 3, 4, 4, 3, 3, 2, 2, 2 → Σbase 26, left 3 → rem 4800인 #1·#2·#5에 +1)

| milestone | start | end | 일수 |
|---|---|---|---|
| FOUNDATION_SETUP | 2026-10-01 | 2026-10-11 | 11 |
| MEMBER_AND_AUTH | 2026-10-12 | 2026-10-22 | 11 |
| CATALOG_AND_CRUD | 2026-10-23 | 2026-11-02 | 11 |
| ORDER_CREATION | 2026-11-03 | 2026-11-13 | 11 |
| CANCEL_AND_REFUND | 2026-11-14 | 2026-11-24 | 11 |
| QUERY_PERFORMANCE | 2026-11-25 | 2026-12-04 | 10 |
| STRUCTURE_CLEANUP | 2026-12-05 | 2026-12-13 | 9 |
| DEPLOY_AND_OPERATE | 2026-12-14 | 2026-12-22 | 9 |
| EXPLAIN_AND_CONSOLIDATE | 2026-12-23 | 2026-12-31 | 9 |

**V6** today 2026-10-01, target 2026-12-01 (D=62 < 63 → COMPRESSED 경계 바로 아래, span 7, slack 55, offset = floorDiv(C[i] × 55, 9200) = 0, 7, 14, 23, 32, 39, 46, 50, 55. 구간 사이에 빈 날이 생긴다: 10-22~10-23, 10-31~11-01)

| milestone | start | end |
|---|---|---|
| FOUNDATION_SETUP | 2026-10-01 | 2026-10-07 |
| MEMBER_AND_AUTH | 2026-10-08 | 2026-10-14 |
| CATALOG_AND_CRUD | 2026-10-15 | 2026-10-21 |
| ORDER_CREATION | 2026-10-24 | 2026-10-30 |
| CANCEL_AND_REFUND | 2026-11-02 | 2026-11-08 |
| QUERY_PERFORMANCE | 2026-11-09 | 2026-11-15 |
| STRUCTURE_CLEANUP | 2026-11-16 | 2026-11-22 |
| DEPLOY_AND_OPERATE | 2026-11-20 | 2026-11-26 |
| EXPLAIN_AND_CONSOLIDATE | 2026-11-25 | 2026-12-01 |

**V7** today 2026-10-01, target 2026-10-01 (D=1 → COMPRESSED, span 1, slack 0 → 9개 모두 2026-10-01 하루)

**V8** today 2026-10-01, target 2026-12-02 (D=63 = 9 × 7 → SEQUENTIAL 경계, extra 0, base·rem·left 모두 0 → 9개 모두 7일, 빈 날·겹침 없음)

| milestone | start | end | 일수 |
|---|---|---|---|
| FOUNDATION_SETUP | 2026-10-01 | 2026-10-07 | 7 |
| MEMBER_AND_AUTH | 2026-10-08 | 2026-10-14 | 7 |
| CATALOG_AND_CRUD | 2026-10-15 | 2026-10-21 | 7 |
| ORDER_CREATION | 2026-10-22 | 2026-10-28 | 7 |
| CANCEL_AND_REFUND | 2026-10-29 | 2026-11-04 | 7 |
| QUERY_PERFORMANCE | 2026-11-05 | 2026-11-11 | 7 |
| STRUCTURE_CLEANUP | 2026-11-12 | 2026-11-18 | 7 |
| DEPLOY_AND_OPERATE | 2026-11-19 | 2026-11-25 | 7 |
| EXPLAIN_AND_CONSOLIDATE | 2026-11-26 | 2026-12-02 | 7 |

---

## 6. 난이도 정의 (L1~L5)

Planner는 `d = clamp(planning IMPLEMENTATION + 1, 1, 5)`로 난이도를 정하고, 최근 14 plan-day 안에 attempt하지 않은 PRACTICE challenge를 difficulty d에서 먼저 찾고 없으면 d−1(≥ 1)에서 찾는다. AI 상태가 `DISABLED`면 CHALLENGE를 제안하지 않는다(`06` §5.3). 따라서 **L(n)은 implementation 레벨 n−1인 사람이 n으로 올라가는 문제**이고, 같은 skill에 L(n−1) 문제가 있으면 L(n)이 없을 때 대체로 제안된다.

| 레벨 | 정의 | 요구 | 범위 | estimatedMinutes (CV-60 상한) | rubric 특징 |
|---|---|---|---|---|---|
| L1 | 문법·API 사용 | 개념 1개를 정해진 위치에 적용 | 메서드 1개, 원인 1개 | 10~15 (20) | 구현 1~2개 + 짧은 설명 1개 |
| L2 | 작은 변환 | 개념 1개를 기존 코드에 적용하고 이유 설명 | 클래스 1~2개 | 15~25 (30) | 구현 + 원리 설명 + 흔한 대안 배제 |
| L3 | 다중 개념 | 2~3개 개념을 엮고 원인을 추론 | 계층 2개 이상, 실행 순서·상태 추적 | 25~40 (40) | 진단(DEBUGGING) 항목 포함, 트레이드오프 설명 1개 |
| L4 | production-like | 동시성·외부 호출·실패 복구 같은 운영 제약에서 여러 해법 중 선택 | 흐름 재설계, 테스트 전략 | 40~60 (60) | 설계 선택 근거, 실패 시나리오, 검증 방법 |
| L5 | trade-off·debugging·design | 정답이 하나가 아닌 설계 판단, 재현과 원인 분석, 새 맥락으로 전이 | 요구사항 해석부터 | 45~90 (90) | 대안 비교와 E5용 설명 비중 큼 |

작성 규칙:
- 평일 기본 45분이면 복습 5분을 뺀 main 한도가 44분이다(`06` §5.6). **L1~L3은 40분 이하**로 만들어 평일에 제안될 수 있게 한다. L4는 주말용이다.
- `isTransfer = true`는 L3 이상에서, 이미 익힌 개념을 다른 계층이나 도메인에 적용하는 문제에만 쓴다. transfer 실패는 IMPLEMENTATION 하락 규칙의 입력이다(`06` §7.3).
- seed v1에는 L5가 없다. role target이 4를 넘지 않으므로 L5 증거(K5·I5·E5)가 필요하지 않다. L5는 AI 생성 또는 이후 seed로 추가한다.
- DIAGNOSTIC은 L3 고정, 15분 이하다. 통과하면 challenge skill의 KNOWLEDGE·IMPLEMENTATION이 `min(claimedLevel, 3)`까지 올라가므로(`06` §7.4) **레벨 3 수준을 확인할 수 있는 결함 찾기·판단형 문제**로 만든다. skills는 그 category에서 priority MUST이고 importance가 높은(≥ 0.70) skill 2~3개로 고른다(CV-59, §9).

---

## 7. 작성 가이드

### 7.1 Challenge 본문

- `scenario`: 실제 서비스에서 일어날 법한 증상과 코드(또는 쿼리·로그). 증상은 관찰 가능한 사실로 쓴다(예: "합계가 10,000보다 작다").
- `prompt`: 번호 목록으로 요구를 나눈다. **설명 요구를 반드시 1개 이상** 넣는다(EXPLANATION rubric과 연결).
- `constraints`: 해결 범위를 좁히는 조건(시그니처 유지, 새 인프라 금지 등). 정답을 암시하지 않는다.
- 코드는 Java 25 문법으로 컴파일 가능한 수준으로 쓰고, 생략은 `// 생성자 생략`처럼 주석으로 표시한다.
- Spring Boot 4 / Spring Framework 7 / Hibernate 7 / PostgreSQL 16(운영·개발 DB, `18` §1.1)에서 사실인 내용만 쓴다. 버전에 따라 달라지는 동작은 다루지 않거나 파일 머리 주석에 기준 버전을 적는다.

### 7.2 Rubric

| 규칙 | 이유 |
|---|---|
| criterion은 제출물에서 **관찰 가능한 행동**으로 쓴다 ("cause를 생성자 인자로 전달한다"). "이해한다" 금지 | AI 평가의 `evidenceQuote` 근거 (`04` §5.3) |
| 항목 하나 = 판정 하나. "A와 B를 한다"처럼 둘을 묶지 않는다 (예외: 선택지 중 하나면 충족) | met/unmet 이분 판정 |
| EXPLANATION 항목을 1개 이상 둔다 | `explanationCoverageBp`가 null이 아니어야 E2·E3 규칙이 작동 (`06` §7.2) |
| 단일 항목 ≤ 6000bp, 500 단위 | 항목 하나로 CORRECT(≥ 8000) 판정이 나지 않게 |
| PRACTICE는 핵심 구현 항목을 R1로 두고 가장 큰 weight를 준다 | 부분 점수(PARTIAL ≥ 4000)가 핵심 이해를 반영 |
| DEBUGGING axis는 원인 진단·재현·검증 방법 항목에 쓴다 | 규칙 계산에서 axis가 따로 쓰이는 것은 EXPLANATION뿐이고, IMPLEMENTATION·DEBUGGING은 전체 coverage에만 합산된다(`06` §8.1). axis는 항목 성격을 기록해 이후 분석에 쓴다 |
| DIAGNOSTIC은 한 결함·판단이 항목 하나가 되게 나눈다 | 통과 = 모든 핵심 판단의 80% 이상 |

### 7.3 Hint (QUESTION_ONLY / CONCEPT_HINT / DIRECTION)

공통: **코드 금지, 정답 금지**, 300자 이하, 해요체, 한 hint는 한두 문장. 1~3단계만 seed로 쓰고 4단계 이상은 AI가 만든다(`06` §9.1 HL-6).

| 단계 | 해야 할 것 | 하지 말 것 | 좋은 예 | 나쁜 예 |
|---|---|---|---|---|
| `QUESTION_ONLY` | 관찰할 지점을 질문 1개로 가리킨다. `?`로 끝난다 | 개념 이름, 해결 방법, expectedConcepts 단어 (CV-57) | "반복문 안에서 읽기 도중 예외가 발생하면 그 아래 줄들은 실행될까요?" | "try-with-resources를 쓰면 되지 않을까요?" |
| `CONCEPT_HINT` | 관련 개념 이름 1~3개와 왜 관련 있는지 | 어디를 어떻게 고칠지, API 사용법 | "예외 변환과 원인 보존, 복구 가능성에 따른 예외 타입 선택을 떠올려 보세요." | "catch에서 new 예외(message, e)로 감싸세요." |
| `DIRECTION` | 접근 순서, 살펴볼 위치, 비교할 선택지 | 최종 클래스·메서드·설정값, 코드 조각, 정답 문장 | "흐름을 준비 저장, 외부 호출, 결과 반영의 세 단계로 나누고 DB가 필요한 단계만 짧은 트랜잭션으로 묶으세요." | "confirm을 세 메서드로 나누고 가운데 메서드에서 @Transactional을 빼세요." |

- 식별자 이름은 문장 속 일반 단어로만 쓴다(백틱 금지, CV-56). 메서드 호출 형태(`a.b()`), 인자가 있는 애너테이션, 세미콜론으로 끝나는 줄은 코드로 판정된다(§4.2).
- DIAGNOSTIC hint도 작성한다. 다만 QUESTION_ONLY를 넘는 hint를 보면 진단은 실패로 판정된다(`06` §7.4).

### 7.4 Review card

| 규칙 | 내용 |
|---|---|
| 한 카드 한 개념 | 개념 두 개면 카드 두 장. conceptKey는 `{skill code}.{CONCEPT}` (예: `SPRING.TRANSACTION.SELF_INVOCATION`) |
| 답변 시간 | 자료 없이 1~3분 안에 답할 수 있는 크기. expectedAnswer 2~5문장 |
| 언어 | 한국어 문장, 식별자·API·enum은 영어 원문 |
| rubric R1 | 복습 화면의 "힌트 보기"는 rubric 첫 항목을 공개한다(`06` §6.1). **R1은 핵심 개념 이름 수준**으로 쓰고 답 전체를 담지 않는다(CV-47) |
| rubric 개수 | 2~4개. 가중치 없이 균등 배분된다(`06` §8.1) |
| RECALL | 사실·규칙을 떠올리는 질문. 질문에 답의 핵심 단어를 넣지 않는다 |
| EXPLAIN | 이유·비교·트레이드오프. `REVIEW_EVALUATE` 평가 시 E축 증거가 된다(`06` §7.2) |
| BUG_SPOT | 15줄 이하 코드 + "문제와 수정 방법은?". 결함은 1~2개로 제한 |
| CHOICE | 줄 시작 `A)`부터 선택지 3~5개, 정답 1개. expectedAnswer는 정답 label로 시작하고 이유 한 문장을 붙인다 |
| 정확성 | 버전에 따라 달라지는 주장은 쓰지 않는다. 확인한 공식 문서가 있으면 curated source로 등록한다(§7.6) |
| 불변성 | 배포된 conceptKey의 의미는 바꾸지 않는다. 문구 수정은 새 사용자에게만 반영된다(`04` §9). 의미가 바뀌면 새 conceptKey + 기존 키 은퇴(§8.3) |
| 기술 설명 카드 | 설명의 구조를 떠올리게 하고, 사용자가 자기 코드·결정 사례로 답하게 한다 |

### 7.5 Skill · role target

| 항목 | 규칙 |
|---|---|
| `description` (non-root) | READING task 설명(`{description} + "공식 문서를 읽고 핵심 3가지를 스스로 적어 보세요."`)과 EXPLAIN task 설명(`{description} + "5문장 이내로 설명하고 예시를 하나 드세요."`)에 그대로 들어가고(`06` §5.3), 두 task에서 무엇을 공부할지 알려주는 유일한 문장이다. AI가 꺼져 있으면 CHALLENGE 대신 이 task가 나오므로 **학습 지시문**으로 쓴다: "무엇을 할 수 있어야 하는가" + 찾아볼 핵심 용어 2~4개, 1~2문장, "~한다"로 끝낸다. 설명 대상이 모호한 "~의 개념" 같은 문장은 금지 |
| `minutesPerLevelStep` | implementation 축 1레벨을 올리는 데 드는 집중 학습 분(복습 overhead 제외). 좁은 개념 60, 일반 90, 넓거나 실습 비중 큰 개념 120~150. 60 미만은 쓰지 않는다 |
| MUST target | 대부분 3~4, explanation 3~4, debugging 2~3. 핵심 skill(트랜잭션, 예외, N+1 등)은 (4,4,4,3) |
| SHOULD·LATER | SHOULD 3 중심, LATER 2 중심. SYSTEM_DESIGN은 SHOULD/LATER |
| 증거가 생기지 않는 축 | 해당 skill에 challenge·coach 증거가 생기지 않는 축은 0 (EXPLANATION 카테고리의 implementation·debugging). 0이 아니면 gap이 영원히 남는다 |
| importance | 0.90 이상: 실무 빈출 핵심, 0.70~0.85: MUST 일반, 0.50~0.65: SHOULD, 0.45 이하: LATER·주변 지식. `≥ 0.70`이면 `HIGH_PRACTICAL_IMPORTANCE` 사유가 붙는다(`06` §5.8) |
| prerequisite | 직접 필요한 것만 1~2개. prerequisite의 implementation target은 2 이상(CV-18). 긴 사슬(4단계 이상)을 만들지 않는다 |
| budget 점검 | 변경 후 `--report`의 budget 표를 확인한다. 기준: planning 2·26주는 MEDIUM, planning 3·12주는 LOW, planning 1·39주는 HIGH 이하 (§12.2) |

### 7.6 Curated source 등록 절차

1. 호스트가 trusted host allowlist에 있는 공식 문서만 쓴다(`06` §10).
2. 작성자가 URL을 브라우저로 열어 **claim 문장이 문서에 직접 있는지** 확인한다. 추론·요약 확장 금지.
3. `versionScope`에 문서 버전을 쓴다. 버전 없는 문서는 `버전 없음 (YYYY-MM-DD 기준 내용)`.
4. `verifiedAt`을 확인한 날로 쓴다. Java·Spring·PostgreSQL 메이저 버전이 바뀌면 전체를 다시 확인한다.

---

## 8. catalogVersion과 은퇴

### 8.1 버전 올림 규칙

**`catalog.yaml`에 나열된 파일 중 하나라도 바뀌면 `catalogVersion`을 1 올린다.** 문구 수정도 포함한다. 버전은 콘텐츠 세트 전체에 하나다.

| 변경 | 버전 | 반영 |
|---|---|---|
| skill 추가·문구·step·prerequisite 수정 | +1 | 다음 기동 upsert. 기존 plan의 required minutes는 skill 행을 읽으므로 즉시 달라진다 |
| role target 수정 | +1 | `role_skill_target` 갱신. **기존 plan의 `plan_skill_target`은 바뀌지 않는다.** 새로 추가된 skill만 다음 replan에서 기본값으로 추가된다 (§10.2) |
| plan template 수정 | +1 | 새로 만드는 plan부터 |
| review card 추가 | +1 | 새 사용자 + 기존 사용자에게 추가 (§3.9 8번) |
| review card 문구 수정 | +1 | 새 사용자만 (기존 `review_item`은 사용자 소유) |
| challenge 텍스트 수정 (title, scenario, prompt, constraints, commonMistakes, hints, rubric criterion 문구, estimatedMinutes, transferTargets) | +1 | 다음 기동 upsert |
| challenge 구조 필드 변경 (skills·difficulty·purpose·isTransfer·expectedConcepts, rubric 항목의 id·weightBp·axis) | +1 | 허용 안 함(SD-02) → 새 seedKey(`...L{n}.{NNN+1}`) 추가 + 기존 seedKey 은퇴 |
| curated source 추가·수정 | +1 | 다음 기동부터 guard 적용 |
| curated repo·reading 추가·수정, `pinnedCommit` 갱신 | +1 | 다음 기동부터 `READ_CODE` 제안에 반영. 이미 COMPLETED한 reading은 다시 제안되지 않는다(`06` §5.3) |
| `tools/` 수정 | 없음 | 런타임 비대상 |

- ContentSeeder는 `catalogVersion ≥ dbVersion`일 때만 적재한다(`04` §9). 같은 버전 재기동은 같은 결과로 upsert되어 무해하다. 더 낮은 버전(이전 이미지로 롤백)은 적재를 건너뛴다.
- PR 체크리스트(§11)가 버전 올림을 확인한다.

### 8.2 삭제 금지

catalog는 삭제하지 않고 비활성화한다(`04` §1, §8).

| 대상 | 은퇴 방법 | DB 결과 | 사용자 데이터 |
|---|---|---|---|
| skill | skill tree에서 제거 + `retired.skillCodes`에 추가 + 이를 참조하는 role target·template·card·challenge 항목 제거(CV가 강제) | `skill.active = false`, `role_skill_target` 행 유지 | `user_skill_state`, `plan_skill_target`, `review_item`은 그대로 |
| challenge | challenge 파일에서 제거 + `retired.challengeSeedKeys` | `status = RETIRED` | attempt·submission 유지. RETIRED는 목록·task 제안에서 제외 |
| review card | 카드 파일에서 제거 + `retired.conceptKeys` | 없음 | 기존 `review_item` 유지. 새 사용자에게 복사하지 않음 |
| curated source | 파일에서 제거 + `retired.curatedSourceIds` | 없음 | 저장된 finding 유지. 이후 AI 출력의 해당 ID는 `DOWNGRADED_UNKNOWN_CURATED` |
| reading | `curated-repos.yaml`에서 **지우지 않고** `retired: true`로 표시 + `retired.readingKeys`에 추가. 그 저장소 항목(`repos[]`)도 남긴다(CV-84) | 없음 | 완료한 `READ_CODE` task와 러버덕 세션은 그대로 남고, `GET /readings/{key}`가 은퇴한 단위를 계속 돌려준다(`retired = true`, `05` §19.7). 새로 제안되지 않는다(`06` §5.3). 좌표는 `pinnedCommit` 기준이라 은퇴 뒤에도 같은 코드를 가리킨다 |

### 8.3 식별자 재사용 금지

- `retired.*`에 있는 식별자는 활성 콘텐츠에서 다시 쓸 수 없다(CV-11, CV-40, CV-50, CV-70, CV-83).
- 같은 의미로 되살릴 때만 `retired`에서 빼고 원래 항목을 복원한다.
- 의미가 달라지면 새 식별자를 만든다. 예: 카드 질문의 핵심 개념이 바뀌면 `..._V2` conceptKey.

### 8.4 줄 번호 관리 (`curated-repos.yaml`)

**`readings[].lines`는 다른 사람의 저장소를 가리키는 좌표다.** 우리가 통제하지 못하는 코드가 바뀌면 조용히 틀어진다 — 파일이 옮겨지거나, 위쪽에 import 한 줄이 추가되거나, 포맷터가 돌면 범위 전체가 밀린다. 사용자는 엉뚱한 곳을 읽게 되고 `question`은 맞지 않는 질문이 된다. 이걸 막는 장치가 `pinnedCommit`이다.

| 규칙 | 내용 |
|---|---|
| LN-1 | 모든 `readings[].path`·`lines`는 그 저장소의 **`pinnedCommit` 기준**이다. 다른 커밋에서 확인한 값을 적지 않는다 |
| LN-2 | `cloneHint`는 **그 커밋을 체크아웃하게** 쓴다. `--depth 1`은 특정 커밋을 못 집으므로 `git clone … && git checkout <sha>` 형태로 적는다 |
| LN-3 | `pinnedCommit`을 올리면 **그 저장소의 모든 reading의 `path`와 `lines`를 다시 확인한다.** 바뀐 것만 고치는 것이 아니라 전부 눈으로 본다. 이건 자동화할 수 없고 콘텐츠 작업이다 |
| LN-4 | `pinnedCommit`을 못 구했으면 `null`로 두되, 이는 임시 상태다. CV-82가 WARN을 내고, WARN이 남아 있는 동안은 줄 번호를 신뢰할 수 없다고 본다 |
| LN-5 | 저장소가 사라지거나 재작성되면 해당 reading들을 §8.2대로 은퇴시키고(`retired: true` + `retired.readingKeys`) 대체 reading을 새 key로 만든다. **같은 key의 `lines`만 고쳐서 다른 코드를 가리키게 하지 않는다** |

갱신 절차 (콘텐츠 작업):

```text
1. 새 커밋 SHA를 구한다
   curl -s https://api.github.com/repos/<owner>/<repo>/commits/HEAD → .sha
2. 그 커밋으로 체크아웃한 사본을 만든다
3. 그 저장소의 reading을 하나씩 열어 path가 아직 있는지, lines 범위가 아직 그 코드인지 확인한다
   - 범위가 밀렸을 뿐이면 lines를 고친다 (key 유지)
   - 코드의 의미가 바뀌었으면 question도 맞는지 다시 본다
   - 대상이 사라졌으면 LN-5
4. repos[].pinnedCommit과 cloneHint를 새 SHA로 바꾼다
5. catalogVersion +1 (§8.1), validate_content.py 실행, §11 체크리스트
```

확인한 커밋 (2026-09-19 첫 소스 점검 기준, §8.5 점검 기록):

| repo | pinnedCommit | 기준 | 라이선스 | 확인 |
|---|---|---|---|---|
| `modulith` | `e9e003a6363e2793a0e5eebe320cb02816ed1e0c` | 2026-09-18 main | Apache-2.0 | reading 4개. 2026-09-19에 그 커밋의 파일로 path·lines를 다시 확인 |
| `petclinic` | `818c4136ea971c21674525f9053de0d9c7ad8cfe` | main (2026-09-19에도 HEAD) | Apache-2.0 | 기존 reading 4개 + 새 4개. 고정 커밋을 올리지 않았다 |
| `restbucks` | `ad97ad03c367a69ff1de70c660be2731cbe7bc3d` | 2026-09-18 main | UNSPECIFIED | reading 5개 모두 **은퇴**(2026-09-19). 좌표는 그대로 남고 조회만 된다. 라이선스 명시가 없어 코드를 옮겨 적지 않았다 |
| `modular-monolith` | `2933f5f14481615e214ee7c35166b957cac4c6cc` | main HEAD | Apache-2.0 (LICENSE) | reading 7개 |
| `jdk25` | `7b65d74bcbd7c3a0b4c1f5c5bd85e64aa2e51b4a` | 태그 `jdk-25.0.4.1+1` | GPL-2.0 + Classpath Exception | reading 5개. 파일 5개가 Temurin 25.0.4.1+1 설치본의 `lib/src.zip`과 같고, `jdk-25.0.4+1` 태그와도 같다 |
| `spring-framework` | `82a6b40b9366ec181ededdad282b307ee5381a52` | 태그 `v7.0.9` (backend `gradle.lockfile`의 spring-tx·spring-aop) | Apache-2.0 | reading 3개. 파일 3개가 Maven Central sources jar와 같다 |
| `hikaricp` | `80c46aee46a000af61d700a2bd144c9a3ff777af` | 태그 `HikariCP-7.0.2` (backend `gradle.lockfile`) | Apache-2.0 | reading 3개. 파일 3개가 Maven Central sources jar와 같다 |
| `security-samples` | `2e3f349b9a33b1b4e3d212398ef14d6383c447db` | main HEAD | Apache-2.0 (LICENSE 파일 없음, 파일별 헤더) | reading 3개 |
| `webgoat` | `872d6149d4ef29e4929c2f4eda279f7fbedc52e8` | main HEAD | GPL-2.0-or-later | reading 3개. 코드를 옮겨 적지 않았다 |

모든 reading의 path·lines는 그 커밋의 파일을 받아 범위의 첫 줄과 끝 줄을 눈으로 확인했다(LN-1). JDK·Spring Framework·HikariCP는 IDE에서 의존성 소스(src.zip, sources jar)를 열어도 같은 줄이 보이므로 `cloneHint`가 IDE를 먼저 안내하고 clone은 대안으로 적는다(clone 명령도 그 커밋을 checkout한다, LN-2).

### 8.5 소스 점검 (`READ_CODE` 저장소·읽기 단위의 주기적 수동 점검)

`READ_CODE`가 가리키는 저장소(`repos[]`)와 읽기 단위(`readings[]`)가 지금의 사용자에게 맞는지 사람이 다시 본다. **자동으로 도는 것은 없다** — 스케줄 job도, 서버의 저장소·코드 조회도 없다(`07` §5.5). 콘텐츠 작업자(에이전트)가 입력을 모아 제안을 만들고, 무엇을 반영할지는 사용자가 정한다.

**언제**

| 시점 | 이유 |
|---|---|
| **S3 구현 시작 직전 (첫 점검)** | `READ_CODE`는 S3에서 처음 제안된다(`BL-TDY-16`). 첫 제안 전에 그때의 저장소 3개·단위 13개를 한 번 검토한다(`11` §3.5, `16` R-15). 2026-09-19에 했다(아래 점검 기록) |
| 단계 회고마다 (`11` §2.2, `16` §4 S-8) | 쓰면서 쌓인 평가와 빈틈을 반영한다 |
| 사용자가 요청할 때 | 예: 특정 기술의 읽을거리가 부족하다고 느낄 때 |

**입력**

| # | 입력 | 얻는 방법 |
|---|---|---|
| I-1 | 빈틈 목록 (DevPilot 데이터) | 사용자가 내려받은 `GET /me/export` JSON(`05` §3.3)에서 뽑는다. ① 레벨이 낮은 skill — `skillStates[]`의 증거 레벨이 활성 plan `skillTargets[]`의 목표보다 크게 낮은 MUST·SHOULD skill ② 복습에서 막히는 skill — `reviewAnswers[]`에서 최종 등급 `AGAIN`이 반복되는 카드의 skill, 러버덕 gap 카드(`reviewItems[]`의 `sourceType = RUBBER_DUCK`)가 있는 skill ③ 읽을 단위가 없는 skill — role target이 있는 skill 중 은퇴하지 않은 어떤 reading의 `skillCodes`에도 없는 skill(`curated-repos.yaml`과 role target 파일을 대조) |
| I-2 | 완료한 `READ_CODE` 평가 | 같은 export의 `dailyPlans[].tasks[]` 중 `taskType = READ_CODE`인 행의 `readingKey`·`readingFeedback`(`HELPFUL`·`TOO_HARD`·`BORING`·null, `04` §3)을 단위·저장소별로 센다 |
| I-3 | 현재 저장소의 라이선스·유지 상태 | 사람이 브라우저로 저장소 페이지를 열어 본다: 라이선스 파일, 보관(archived) 여부, 마지막 커밋 시기, Spring Boot·Java 버전 |

평가 읽는 법(판단은 사람이 한다): `TOO_HARD`가 많은 단위는 범위를 줄이거나 앞에 둘 쉬운 단위를 찾는다. `BORING`이 많은 단위는 질문(`question`)을 바꾸거나 교체 후보로 둔다. `HELPFUL`이 많은 저장소는 같은 저장소에서 단위를 더 찾을 후보다. 평가는 이 점검의 입력일 뿐이고 레벨·planner 규칙의 입력이 아니다(`06` §5.3).

**후보 기준**

| 기준 | 내용 |
|---|---|
| 라이선스 | OSI 승인 라이선스. **Apache-2.0·MIT를 우선**한다. 라이선스가 없는 저장소(`UNSPECIFIED`, 예: 2026-09-19에 교체한 `restbucks`)가 **먼저 교체 대상**이다. 그 밖의 OSI 라이선스(예: GPL 계열)는 읽기만 하고 코드를 옮겨 적지 않는다(§3.8 라이선스 규칙과 같은 취급) |
| 유지 | 활발히 유지된다: 보관(archived) 상태가 아니고 최근 커밋이 있다 |
| 스택 | Spring Boot·Java 버전이 DevPilot이 가르치는 스택(Spring Boot 4, Java 25 — `18` §1.1)과 가깝다 |
| 크기 | 다룰 만하다: 한 단위를 `estimatedMinutes`(5~60분) 안에 읽을 수 있고, 저장소 구조를 짧은 `why`로 설명할 수 있다 |
| 테스트 | 테스트 코드가 있다 |
| 도메인 | 설명 없이 이해되는 업무(주문·결제·회원 등)라 읽기 쉽다 |

**큰 실제 프로젝트의 부분 읽기**는 허용한다. 저장소 전체를 읽지 않고 한 주제에 맞는 파일 하나·범위 하나만 단위로 삼는다(RC-2). 예:

- 커넥션 풀 라이브러리의 커넥션 대여·반납 — 자원 수명(`JAVA.EXCEPTION.TRY_WITH_RESOURCES`, `PRACTICAL_ENGINEERING.RESOURCE_TIMEOUT`)
- 프레임워크의 트랜잭션 프록시 코드(예: Spring Framework의 트랜잭션 인터셉터) — `SPRING.TRANSACTION`, `SPRING.AOP_PROXY`
- JDK 동시성 클래스(예: `java.util.concurrent`) — `JAVA.CONCURRENCY`

단위는 지금의 `readings[]` 형식 그대로 적는다: 저장소의 **`pinnedCommit` + `path` + `lines` + 열린 질문(`question`)**, 그리고 `skillCodes`·`estimatedMinutes`·`lookFor`. 큰 저장소는 `subPath`를 그 모듈로 두고, `cloneHint`는 부분 clone(예: `git clone --filter=blob:none …`)을 써도 되지만 반드시 그 커밋을 checkout한다(LN-2).

**출력과 반영**

1. 점검 결과는 저장소·단위별 **제안 목록**이다: 추가 / 교체 / 은퇴 / 유지. 제안마다 근거(I-1~I-3 중 무엇)를 붙인다.
2. 사용자와 함께 검토해 받아들일 제안만 고른다. 고르지 않은 제안은 반영하지 않는다.
3. 받아들인 제안을 콘텐츠 PR 하나로 `content/curated-repos.yaml`에 반영한다:
   - 새 저장소·갱신한 저장소는 `pinnedCommit`을 고정하고 `cloneHint`가 그 커밋을 checkout하게 쓴다(LN-2)
   - 그 커밋으로 체크아웃한 사본에서 해당 저장소의 **모든** `path`·`lines`를 눈으로 다시 확인한다(§8.4 LN-1·LN-3)
   - 은퇴는 §8.2대로 한다(`retired: true` + `retired.readingKeys`). 은퇴한 단위는 지난 과제를 위해 계속 조회된다
   - `python content/tools/validate_content.py` 통과, `catalogVersion` +1(§8.1), §11 체크리스트
4. 점검 날짜, 입력 요약, 결정(받아들인 것과 받아들이지 않은 것)을 PR 설명에 적고, §8.4의 "확인한 커밋" 표를 갱신한다. 같은 내용을 아래 **점검 기록**에 한 항목으로 남긴다.

**점검 기록**

2026-09-19 — 첫 점검 (S3 구현 시작 전, catalogVersion 4 → 5)

| 항목 | 내용 |
|---|---|
| 입력 I-1 (빈틈) | 실사용 전이라 export가 없어 ③ "읽을 단위가 없는 skill"만 썼다. 점검 전 MUST 43개 중 reading이 있는 skill 17개, role target 75개 중 20개. 비어 있던 영역: 인증·세션, 보안(OWASP), 컬렉션·스트림·자원 해제, 트랜잭션·프록시 내부, 커넥션 풀 타임아웃, CI·Docker, 설정·로깅·null 처리 |
| 입력 I-2 (평가) | 완료한 `READ_CODE`가 없다(S3 전) |
| 입력 I-3 (저장소 상태) | 후보 저장소마다 라이선스 파일, 보관 여부, 마지막 커밋, Spring Boot·Java 버전, 테스트 유무를 확인했다. `restbucks`는 라이선스 명시가 없다 |
| 결정 A (교체) | `restbucks` → `modular-monolith`(sivaprasadreddy/spring-modular-monolith, Apache-2.0, Boot 4.1, Java 25, 모듈 catalog·orders·inventory·users·notifications·config, Testcontainers·ArchitectureTests·ModularityTests·`compose.yml`·GitHub Actions 있음). reading 7개: 장바구니→주문 흐름, 재고 차감, 모듈 API 경계, 보안 설정, 구조 테스트, Testcontainers 설정, compose와 CI. `restbucks` reading 5개는 은퇴(`retired: true` + `retired.readingKeys`), 저장소 항목은 지난 과제 조회를 위해 남긴다 |
| 결정 B (보안) | `security-samples`(spring-projects/spring-security-samples, Apache-2.0 — LICENSE 파일 없이 파일별 헤더): JWT 로그인(토큰 발급 + resource server), 메서드 보안, 세션 수 제한 3개. `webgoat`(WebGoat/WebGoat, GPL-2.0-or-later, Boot 4.1.1): SQL 인젝션(이어 붙인 조회 vs 바인딩), 반사형 XSS, 기능 수준 접근 제어 3개. cloneHint는 커밋 하나만 얕게 받고 lessons 폴더만 꺼낸다 |
| 결정 C (큰 코드의 부분 읽기) | IDE에서 clone 없이 연다. `jdk25`(openjdk/jdk25u 태그 `jdk-25.0.4.1+1` — 사용자 JDK가 Temurin 25.0.4.1+1): HashMap put·resize/treeify, ArrayList grow, Collectors.groupingBy, Files.lines의 onClose 5개. `spring-framework`(v7.0.9 = backend lockfile): TransactionAspectSupport.invokeWithinTransaction, JdkDynamicAopProxy.invoke, CglibAopProxy의 DynamicAdvisedInterceptor 3개. `hikaricp`(7.0.2 = backend lockfile): HikariPool.getConnection, ProxyConnection.close, ConcurrentBag borrow/requite 3개(대기 타임아웃과 반납이 실제로 일어나는 곳이라 두 주제를 이어서 읽도록 셋으로 나눴다) |
| 결정 D (petclinic 추가) | 고정 커밋 유지(main이 그대로). reading 4개: OwnerControllerTests의 검색 테스트, ClinicServiceTests의 제약 테스트, `maven-build.yml`(CI와 `docker-compose.yml`을 함께 따라감), `application.properties`와 프로필 파일 |
| 개념 읽기 (curated source) | 9개 추가, 1개 재확인: SQL(PostgreSQL 16 외부 조인, WHERE와 HAVING), 인덱스(PostgreSQL 16 인덱스 쓰기 비용, 복합 인덱스 재확인), HTTP(RFC 9110 안전·멱등 메서드, MDN 상태 코드 분류), 로깅(Spring Boot 4.1 Logging), null 처리(`Optional`·`Objects` Java SE 25 API, JEP 358) |
| 받아들이지 않음 | buckpal — 라이선스 없음. eventuate-tram 예제 — 라이선스가 분명하지 않고 인프라 부담이 크다. ddd-example-ecommerce — 2023년 이후 갱신이 없다. dddsample — 다음 점검 후보로 보류. OWASP WrongSecrets — 선택 항목이라 이번에는 건너뜀. Pro Git(`DEVOPS.GIT` 개념 읽기) — 호스트 `git-scm.com`이 `devpilot.ai.trusted-source-hosts`에 없어 CV-71을 통과하지 못한다. 넣으려면 `03` §9의 허용 목록을 먼저 바꿔야 한다 |
| 결과 | 저장소 9개(활성 reading이 있는 저장소 8개), reading 41개(활성 36개, 은퇴 5개, 활성 합계 547분). reading이 있는 skill: MUST **17 → 34**/43, role target 전체 20 → 43/75. reading이 없는 MUST는 `DATABASE.SQL_BASICS`, `DATABASE.INDEX`, `SPRING.EXCEPTION_HANDLING`, `WEB_HTTP.REST_API_DESIGN`, `DEVOPS.GIT`, EXPLANATION 4개 — 앞의 셋은 개념 읽기(curated source)로 보완한다 |
| 검증 | `python content/tools/validate_content.py` → ERROR 0, WARN 2(CV-87: `petclinic` 8개, `modular-monolith` 7개). 고른 단위 수가 권장 3~5를 넘은 것이라 받아들였고, 다음 점검에서 평가(`TOO_HARD`·`BORING`)를 보고 줄일 단위를 고른다 |
| 다음 점검 후보 | 오류 응답(`modular-monolith`의 `OrdersExceptionHandler` — 예외 메시지를 그대로 보여 주는 500 처리), REST API 설계를 보여 줄 공개 예제, dddsample |

---

## 9. 온보딩 자기평가 전파와 진단 제안 (콘텐츠 계약)

이 절은 온보딩 규칙 중 **seed 콘텐츠에 의존하는 부분의 계약**만 정한다.

| 항목 | 규칙 |
|---|---|
| 입력 | SkillCategory 13개 각각의 자기평가 `c` (0~5, `SkillLevel` 의미) |
| 전파 (`SelfAssessmentPropagation`) | category의 **active non-root skill 중 학습 목표 트랙(`target_role`)의 role target이 있는 skill** 모두에 `user_skill_state(self_assessed_level = c, self_assessment_active = true, 4축 증거 레벨 = 0)`를 만든다. root skill에는 행을 만들지 않는다. skill별 가감은 하지 않는다 |
| planning level | `min(c, 3)`이 모든 축에 적용된다(`06` §7.5). 자기평가 5도 planning은 3이다 |
| 진단 제안 조건 | 자기평가 `c ≥ 3`인 category마다 1개 (`GET /diagnostics/suggestions`) |
| 진단 challenge 선택 | `05-api-spec.md` §4.2: challenge skills가 그 category에 속하는 `VALIDATED` DIAGNOSTIC seed challenge를 plan priority(MUST 먼저) → practical_importance DESC → seedKey ASC 순으로 골라 첫 번째. 콘텐츠는 이 선택이 의미 있도록 DIAGNOSTIC skills를 MUST·importance ≥ 0.70로 제한한다(CV-59) |
| `diagnosticCategories` | 진단 challenge가 **반드시 있어야 하는** category 목록(콘텐츠 완결성 계약, CV-59). 선택 로직은 이 목록이 아니라 challenge skills의 category를 본다 |
| claimedLevel | `c` (`DIAGNOSTIC_*` 이벤트 payload, `04` §6) |
| 통과 효과 | **challenge의 skills만** KNOWLEDGE·IMPLEMENTATION = `max(현재, min(c, 3))` (`06` §7.4). 같은 category의 다른 skill은 자기평가를 그대로 쓴다 |
| 실패 효과 | challenge skills의 `self_assessment_active = false` |
| 진단 없는 category | NETWORK, CS, ALGORITHM, DEVOPS, SECURITY, PRACTICAL_ENGINEERING, SYSTEM_DESIGN, EXPLANATION은 제안하지 않는다(seed v1) |

현재 진단 challenge와 대상 skill (priority, importance):

| category | seedKey | skills |
|---|---|---|
| JAVA | `DIAGNOSTIC.JAVA.L3.001` | JAVA.EXCEPTION (MUST 0.90), JAVA.COLLECTION (MUST 0.85), JAVA.EXCEPTION.TRY_WITH_RESOURCES (MUST 0.75) |
| SPRING | `DIAGNOSTIC.SPRING.L3.001` | SPRING.TRANSACTION (MUST 0.95), SPRING.AOP_PROXY (MUST 0.70) |
| DATABASE | `DIAGNOSTIC.DATABASE.L3.001` | DATABASE.JPA.FETCH_STRATEGY (MUST 0.90), DATABASE.INDEX (MUST 0.85) |
| WEB_HTTP | `DIAGNOSTIC.WEB_HTTP.L3.001` | WEB_HTTP.HTTP_BASICS (MUST 0.85), WEB_HTTP.REST_API_DESIGN (MUST 0.80) |
| TESTING | `DIAGNOSTIC.TESTING.L3.001` | TESTING.JUNIT (MUST 0.85), TESTING.UNIT_TEST_DESIGN (MUST 0.75) |

---

## 10. 우선순위 조정과 re-seed

### 10.1 기본값 변경 (운영자, seed 수정)

예: 학습 목표에 알고리즘 문제 풀이가 핵심으로 들어가 ALGORITHM을 MUST로 올린다(DEC-14 개정).

1. `role-targets/java-backend.yaml`에서 ALGORITHM 4개 skill의 `priority`를 MUST로, `importance`를 0.70 이상으로 바꾼다. 필요하면 implementation target을 올린다.
2. CV-36을 만족하도록 해당 skill이 milestone에 있는지 확인한다(현재 `EXPLAIN_AND_CONSOLIDATE`). 알고리즘 문제 풀이는 장기 연습이 필요하므로 PREPARATION milestone으로 옮기는 것을 함께 검토한다.
3. `--report`로 budget 변화를 확인한다. 현재 target 그대로 MUST로만 바꾸면 planning 2 기준 required MUST가 **12,501 → 13,238분**(+737)이 되어 26주 ratio가 9742 → **10317bp (MEDIUM → HIGH)** 가 된다.
4. DEC-14를 개정하고(CV-24 WARN은 의도된 변경으로 기록), `catalogVersion`을 올려 배포한다.

### 10.2 re-seed가 기존 plan에 주는 영향

| 대상 | 영향 |
|---|---|
| 기존 ACTIVE plan의 `plan_skill_target` | **바뀌지 않는다.** plan 생성 시 복사한 값이다(`06` §11.2) |
| replan으로 만든 새 version | 이전 plan의 `plan_skill_target`을 복사하므로(`06` §11.2 7번) **이미 있던 skill**의 priority·importance·target은 새 기본값으로 바뀌지 않는다 |
| 새로 만드는 plan (온보딩, 활성 plan이 없을 때 `POST /plans`) | 새 `role_skill_target`을 복사한다 (`adjustment = ROLE_DEFAULT`) |
| seed에 새로 추가된 skill | 기존 ACTIVE plan에는 바로 들어가지 않는다. 사용자의 **다음 replan** 때 `role_skill_target`에는 있고 이전 plan에 없는 skill로 판정되어 role 기본값으로 추가된다(`adjustment = ROLE_DEFAULT`, `06` §11.2 7번). 그 전까지는 milestone·target이 없으므로 그 skill의 seed card가 due가 되기 전에는 planner 후보(`06` §5.2)에 들어가지 않는다 |
| skill의 `minutesPerLevelStep` 변경 | skill 행을 읽는 계산(required minutes)에 즉시 반영된다 |
| 새 seed card | 기존 사용자에게도 추가된다. 사용자의 plan에 target이 없는 skill의 카드는 due 정렬에서 priority "없음"으로 처리된다(`06` §6.5) |

### 10.3 사용자별 조정

사용자가 자기 plan에서 skill target을 조정하는 경로는 replan이다(구조 변경 = 새 version, `06` §11.1). `06` §11.2 7번이 정의하는 조정은 defer(`DEFERRED`), target 축소(`TARGET_REDUCED`), defer 해제(`USER_EDITED`), 새 seed skill 자동 추가(`ROLE_DEFAULT`)다. target 상향은 확장 제안(`06` §4.4 6단계)을 받아들이는 `acceptedTargetRaises`로만 한다. **priority 변경과 그 밖의 target 상향은 MVP 범위 밖(Later)** 이다. 필요하면 운영자가 content YAML의 role target을 바꾸고(§10.1) 새 plan을 만든다(§13 O-4).

### 10.4 다른 학습 트랙을 추가할 때 (콘텐츠 작업)

`target_role`은 사용자별 컬럼이고 `TargetRole` CHECK에 값을 더하면 구조 변경 없이 늘어난다. 다만 **지금의 skill 카탈로그 88개는 Java 백엔드 전용**이라(JVM·JPA·Spring이 절반) 새 학습 트랙을 더하려면 migration과 seed 외에 **skill tree·role target·plan template·review card·challenge·curated repo를 그 트랙용으로 새로 쓰는 콘텐츠 작업**이 따른다. 절차는 `04-domain-model-and-db.md` §3(enum 값 추가 + migration) → `role_skill_target` seed → 이 문서의 §3.2~§3.8 파일 추가 순이고, CV-21(역할마다 non-root skill 하나당 target 1개)·CV-30(역할마다 template 1개)·CV-36(모든 MUST skill이 milestone에 있음)이 누락을 잡는다.

---

## 11. 품질 체크리스트 (콘텐츠 PR)

- [ ] `python content/tools/validate_content.py` 종료 코드 0, 새 WARN은 PR 설명에 이유를 적었다
- [ ] 나열된 파일이 바뀌었으면 `catalogVersion`을 1 올렸다
- [ ] 식별자를 삭제하지 않고 `retired`에 추가했다. 은퇴 식별자를 재사용하지 않았다
- [ ] challenge 구조 필드(skills·difficulty·purpose·isTransfer·expectedConcepts, rubric 항목의 id·weightBp·axis)를 바꿨다면 새 seedKey로 추가하고 기존 것을 은퇴했다 (SD-02). rubric criterion 문구만 고친 경우는 제자리 수정한다
- [ ] 코드 예시는 Java 25에서 컴파일 가능한 문법이고, Spring Boot 4 / Spring Framework 7 / Hibernate 7 / PostgreSQL 16에서 사실인 동작만 다룬다
- [ ] non-root skill의 `minutesPerLevelStep`이 60 이상이다(CV-15)
- [ ] 버전에 민감한 주장은 공식 문서로 확인했고, 확인한 문서는 curated source로 등록했다 (`verifiedAt`)
- [ ] hint에 코드·정답이 없고, QUESTION_ONLY는 개념 이름 없이 질문으로 끝난다
- [ ] rubric criterion이 관찰 가능한 행동이고 EXPLANATION 항목이 있다
- [ ] 카드 R1이 핵심 개념 이름 수준이고 답 전체를 드러내지 않는다
- [ ] L1~L3 challenge가 40분 이하다
- [ ] role target·step 변경 시 `--report` budget 표가 §7.5 기준을 벗어나지 않거나, 벗어난 이유를 적었다
- [ ] 사람 한 명이 실제로 challenge를 풀어 보고 estimatedMinutes를 확인했다
- [ ] reading을 추가·수정했다면 `pinnedCommit`으로 체크아웃한 사본에서 `path`와 `lines`를 **직접 열어 확인**했다 (§8.4 LN-1)
- [ ] reading `question`이 답을 유도하지 않는 열린 질문이고 40자 이상이다 (CV-86)
- [ ] `pinnedCommit`을 올렸다면 그 저장소의 **모든** reading을 다시 확인했다 (§8.4 LN-3)
- [ ] 라이선스가 `UNSPECIFIED`인 저장소의 코드를 문서·콘텐츠에 옮겨 적지 않았다
- [ ] 저장소·reading의 추가·교체·은퇴는 소스 점검(§8.5)에서 사용자가 고른 것만 반영했고, 은퇴한 reading은 지우지 않고 `retired: true`로 남겼다(§8.2)

---

## 12. 콘텐츠 인벤토리 (catalogVersion 5)

### 12.1 카테고리별 수량

`--report` 출력(2026-09-19, catalogVersion 5). skills는 root 포함. challenge는 첫 번째 skill의 category로 센다. 아래 카테고리별 수량은 catalogVersion 2 이후 같다. catalogVersion 5(첫 소스 점검, §8.5 점검 기록)에서는 **curated source, curated repo/reading, plan template milestone 설명 문구**가 바뀌었다.

| category | skills | MUST | SHOULD | LATER | cards | PRACTICE | DIAGNOSTIC |
|---|---|---|---|---|---|---|---|
| JAVA | 12 | 6 | 5 | 0 | 15 | 5 | 1 |
| SPRING | 12 | 9 | 2 | 0 | 13 | 4 | 1 |
| DATABASE | 11 | 8 | 2 | 0 | 16 | 4 | 1 |
| WEB_HTTP | 6 | 3 | 2 | 0 | 8 | 0 | 1 |
| NETWORK | 4 | 0 | 2 | 1 | 3 | 0 | 0 |
| CS | 4 | 0 | 3 | 0 | 2 | 0 | 0 |
| ALGORITHM | 5 | 0 | 4 | 0 | 0 | 0 | 0 |
| TESTING | 6 | 4 | 1 | 0 | 7 | 2 | 1 |
| DEVOPS | 7 | 3 | 3 | 0 | 4 | 0 | 0 |
| SECURITY | 6 | 4 | 1 | 0 | 6 | 3 | 0 |
| PRACTICAL_ENGINEERING | 6 | 2 | 3 | 0 | 4 | 0 | 0 |
| SYSTEM_DESIGN | 4 | 0 | 1 | 2 | 0 | 0 | 0 |
| EXPLANATION | 5 | 4 | 0 | 0 | 4 | 0 | 0 |
| **합계** | **88** | **43** | **29** | **3** | **82** | **18** | **5** |

| 항목 | 값 |
|---|---|
| skill | root 13, non-root 75 (3단계 skill 8개) |
| review card 유형 | RECALL 41, EXPLAIN 22, BUG_SPOT 16, CHOICE 3 |
| PRACTICE 난이도 | L1 2, L2 8, L3 6, L4 2 (isTransfer 2: `PRACTICE.SPRING.TRANSACTION.L4.001`, `PRACTICE.SECURITY.AUTHN_AUTHZ.L4.001`) |
| plan template | `JAVA_BACKEND_DEFAULT`: milestone **9개** — PREPARATION 8 (weight 9200), CONSOLIDATION 1 (800). priority는 1~6 MUST / 7~9 SHOULD. 75개 non-root skill 전부가 정확히 한 milestone에 들어간다 (CV-35·CV-36) |
| curated source | 19 (Oracle 4, Spring 5, PostgreSQL 5, OWASP 2, IETF 1, MDN 1, OpenJDK 1) |
| curated repo | **9** (`modulith`, `petclinic`, `restbucks`(은퇴 단위만), `modular-monolith`, `jdk25`, `spring-framework`, `hikaricp`, `security-samples`, `webgoat`) — 모두 `pinnedCommit` 있음 (§8.4) |
| reading | **41** — 활성 36 (modulith 4, petclinic 8, modular-monolith 7, jdk25 5, spring-framework 3, hikaricp 3, security-samples 3, webgoat 3, 합계 547분), 은퇴 5 (restbucks). 활성 reading이 서로 다른 skill **43개**(MUST 34/43)를 덮는다 |
| 온보딩 카드 분산 | 82장 ÷ 하루 5장 = 17 plan-day (`06` §6.3) |

plan template milestone (2026-09-18 재작성 — "과목 순서"에서 "주문 시스템을 만드는 순서"로):

| # | milestone | priority | phase | weightBp | skills | 읽기 |
|---|---|---|---|---|---|---|
| 1 | 기반 다지기 | MUST | PREPARATION | 1200 | 12 | `petclinic` 구조·테스트·설정, `modular-monolith` 모듈 API |
| 2 | 회원과 인증 | MUST | PREPARATION | 1200 | 9 | `security-samples` 로그인·세션, `webgoat` 취약 코드, `modular-monolith` 보안 설정 |
| 3 | 상품과 CRUD | MUST | PREPARATION | 1500 | 13 | `petclinic` Owner/Pet/Visit, `jdk25` 컬렉션 |
| 4 | 주문 생성 | MUST | PREPARATION | 1500 | 7 | `modular-monolith` 재고 차감, `spring-framework` 트랜잭션·프록시, `modulith` 이벤트 |
| 5 | 취소와 환불 | MUST | PREPARATION | 1200 | 5 | `hikaricp` 커넥션 대여·반납, `jdk25` 자원 해제, `modular-monolith` Testcontainers |
| 6 | 조회 성능 | MUST | PREPARATION | 1100 | 5 | 직접 측정이 중심 (`petclinic` 로딩 설정, `jdk25` groupingBy) |
| 7 | 구조 정리 | SHOULD | PREPARATION | 700 | 3 | `modulith` 전체, `modular-monolith` 구조 테스트 |
| 8 | 배포와 운영 | SHOULD | PREPARATION | 800 | 6 | `petclinic`·`modular-monolith` CI·compose, `hikaricp` 타임아웃 |
| 9 | 설명과 정리 | SHOULD | CONSOLIDATION | 800 | 15 | — |

- 모든 MUST skill에 seed card가 1장 이상 있다(CV-48 WARN 0).
- ALGORITHM, SYSTEM_DESIGN에는 seed card·challenge가 없다. 이 skill들의 task 제안은 READING/EXPLAIN으로 대체된다(`06` §5.3).
- milestone priority(잘라내는 순서)와 skill priority(role target)는 **다른 축**이다. SHOULD milestone 8에도 MUST skill(`DEVOPS.DOCKER`, `DEVOPS.CI_GITHUB_ACTIONS`, `PRACTICAL_ENGINEERING.LOGGING`)이 들어 있다. risk 계산은 `plan_skill_target.priority`를 쓰므로(`06` §4.1) 모순이 아니다. 1~6을 MUST로 둔 것은 기한이 촉박할 때 7~9부터 잘라내기 위해서다.
- 활성 reading이 덮는 skill은 43개(non-root 75개 중, MUST 34/43)다. 나머지 skill의 task 제안은 READ_CODE 후보가 비어 READING/PROJECT_TASK/EXPLAIN으로 내려간다(`06` §5.3). 조회 튜닝(인덱스·실행 계획), 오류 응답, REST API 설계, Git, EXPLANATION은 reading이 없는 영역이다(§3.8 "커버되지 않는 것").

### 12.2 Budget 점검 (`06` §3·§4)

모든 축 planning level이 같다고 가정, 평일 45분·주말 240분, completion 7000bp, 온전한 주 단위.

| planning | required MUST | required SHOULD | 12주 (effective 5,922) | 26주 (12,831) | 39주 (19,246) |
|---|---|---|---|---|---|
| 0 | 33,483 | 19,119 | CRITICAL | CRITICAL | CRITICAL |
| 1 | 22,997 | 11,296 | CRITICAL | CRITICAL | HIGH |
| 2 | 12,501 | 4,959 | CRITICAL | MEDIUM (9742bp) | LOW |
| 3 | 3,277 | 0 | LOW | LOW | LOW |

해석: 자기평가 2(도움받아 가능) 수준의 사용자가 6개월을 잡으면 MEDIUM, 3개월이면 CRITICAL이 나와 SHOULD defer와 MUST 축소 제안(`06` §4.4)이 동작한다. 4주 사용 후 실제 학습 기록으로 step 값을 조정한다.

### 12.3 Curated source 목록 (2026-09-17 확인, 2026-09-19 추가)

| id | 문서 |
|---|---|
| `CS-JAVA-TRY-WITH-RESOURCES` | JLS Java SE 25 §14.20.3 |
| `CS-JAVA-OBJECT-HASHCODE-CONTRACT` | `java.lang.Object` Java SE 25 API |
| `CS-SPRING-TX-SELF-INVOCATION` | Spring Framework 7.0 Using @Transactional |
| `CS-SPRING-TX-ROLLBACK-RULES` | Spring Framework 7.0 Rolling Back a Declarative Transaction |
| `CS-SPRING-TX-REQUIRES-NEW` | Spring Framework 7.0 Transaction Propagation |
| `CS-SPRING-MVC-PROBLEM-DETAIL` | Spring Framework 7.0 Error Responses |
| `CS-POSTGRESQL-TRANSACTION-ISOLATION` | PostgreSQL 16 §13.2 |
| `CS-POSTGRESQL-MULTICOLUMN-INDEX` | PostgreSQL 16 §11.3 (2026-09-19 재확인) |
| `CS-OWASP-SQL-INJECTION-PREVENTION` | OWASP SQL Injection Prevention Cheat Sheet |
| `CS-OWASP-LOGGING-SENSITIVE-DATA` | OWASP Logging Cheat Sheet |
| `CS-POSTGRESQL-OUTER-JOIN` | PostgreSQL 16 §7.2 Joined Tables (`DATABASE.SQL_BASICS`) |
| `CS-POSTGRESQL-WHERE-VS-HAVING` | PostgreSQL 16 §2.7 Aggregate Functions (`DATABASE.SQL_BASICS`) |
| `CS-POSTGRESQL-INDEX-WRITE-COST` | PostgreSQL 16 §11.1 Indexes Introduction (`DATABASE.INDEX`) |
| `CS-RFC9110-SAFE-IDEMPOTENT-METHODS` | RFC 9110 §9.2.1·§9.2.2 (`WEB_HTTP.HTTP_BASICS`) |
| `CS-MDN-HTTP-STATUS-CLASSES` | MDN HTTP response status codes (`WEB_HTTP.HTTP_BASICS`) |
| `CS-SPRING-BOOT-LOGGING-DEFAULTS` | Spring Boot 4.1 Logging (`PRACTICAL_ENGINEERING.LOGGING`) |
| `CS-JAVA-OPTIONAL-RETURN-TYPE` | `java.util.Optional` Java SE 25 API (`PRACTICAL_ENGINEERING.NULL_BOUNDARY`) |
| `CS-JAVA-OBJECTS-REQUIRE-NON-NULL` | `java.util.Objects` Java SE 25 API (`PRACTICAL_ENGINEERING.NULL_BOUNDARY`) |
| `CS-OPENJDK-JEP358-HELPFUL-NPE` | JEP 358 Helpful NullPointerExceptions (`PRACTICAL_ENGINEERING.NULL_BOUNDARY`) |

---

## 13. 기준 문서 반영 결과 (2026-09-17)

작성 중 발견한 gap은 모두 기준 문서에 반영했다. 이 절은 추적용 기록이다.

| ID | 결정 | 반영 위치 |
|---|---|---|
| O-1 | `onboarding` 모듈이 `review`에 의존하도록 허용하고 `SeedCardAssignmentService`를 직접 호출한다 | `03-system-architecture.md` §2.2, §3.2 |
| O-2 | `plan.application.PlanTemplateRegistry`(ContentSeeder가 기동 시 등록), `plan.domain.PlanTemplatePlacement`(§5 알고리즘), `plan.domain.PlanTemplate`(record) | `03-system-architecture.md` §3.2 |
| O-3 | seed challenge 구조 필드 변경은 기동 실패, 텍스트 필드만 갱신 (SD-02와 같음) | `04-domain-model-and-db.md` §2, §9 |
| O-4 | replan 시 이전 plan에 없는 role target skill은 기본값으로 자동 추가. priority 변경·target 상향은 Later | `06-learning-engine-rules.md` §11.2 |
| O-5 | milestone 단계(`phase`)와 무관하게 모든 MUST는 목표일까지의 budget에 포함 | `06-learning-engine-rules.md` §4.1 |
| O-6 | `skill.active=false`인 skill은 budget, planner 후보, due 선택에서 제외 | `06-learning-engine-rules.md` §4.1, §5.2, §6.5 |
| O-7 | DB 버전 = `max(skill.catalog_version)`(없으면 0), 작으면 seed 건너뜀 | `04-domain-model-and-db.md` §9 |
| O-8 | conceptKey 패턴은 seed·수동 카드에만 적용, 시스템 생성 키는 예외 | `04-domain-model-and-db.md` §7 I-06 |
| O-9 | challenge 제안은 difficulty d, 없으면 d−1 순서로 탐색. EXPLAIN task 설명에도 `skill.description` 사용 | `06-learning-engine-rules.md` §5.3 |
| O-10 (2026-09-18) | S2 배포에서 challenge upsert를 건너뛰는 방식 = 설정 플래그 `devpilot.content.seed-challenges`(S2 `false`, S3부터 `true` 기본). 검증은 항상 수행 | `03-system-architecture.md` §9 (`content` 블록에 키 추가 필요), `04-domain-model-and-db.md` §9 — 반영됨 (S1: `application.yml` 기본 `false`) |
| O-11 (2026-09-18) | CV-15 non-root 하한을 §7.5 작성 규칙과 같은 60으로 상향. 현재 seed(catalogVersion 2)는 모두 60 이상 | `content/tools/validate_content.py`, Java `ContentValidator` — 반영됨 (S1) |
