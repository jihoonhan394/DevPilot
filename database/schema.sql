-- =====================================================================
-- DevPilot database schema (PostgreSQL 16 — 자체 서버 공용 인스턴스. PG 17 전용 문법 사용 금지)
-- Status: Accepted (v2) · Last updated: 2026-09-18 (v3: side_project, rubber_duck_session/turn, READ_CODE, RUBBER_DUCK*)
-- Related: docs/04-domain-model-and-db.md (enum registry, state machines, JSON schemas)
--
-- 이 파일은 설계 기준(최종 형태)이다. 실제 적용은 Flyway migration으로 나눠서 한다
-- (docs/04-domain-model-and-db.md §10 migration plan).
--
-- 규칙
--  * 스키마: devpilot (DB `devpilot`, 소유 role `devpilot`. Supabase로 옮기면 Data API에 노출하지 않음)
--  * PK: uuid. 애플리케이션에서 UUID를 생성해 넣는다. DB default는 보조 수단
--  * 시각: timestamptz (UTC). 날짜: date (사용자 plan-day 기준)
--  * enum: varchar + CHECK. 값은 docs/04 §3 레지스트리와 1:1
--  * JPA @Version 컬럼 이름은 항상 version (bigint). 계획 버전 번호는 plan_version
--  * updated_at, status_updated_at은 애플리케이션이 갱신한다 (DB trigger 없음)
--  * Supabase 전용 role을 직접 참조하지 않는다 (현재 운영은 순수 PostgreSQL 16이므로 파일 끝의 조건부 DO 블록은 no-op)
-- =====================================================================

create schema if not exists devpilot;
set search_path to devpilot;

-- ---------------------------------------------------------------------
-- 1. User / Learning goal
-- ---------------------------------------------------------------------
create table app_user (
    id                      uuid primary key default gen_random_uuid(),
    external_auth_id        uuid not null unique,
    role                    varchar(20) not null default 'USER' check (role in ('USER','ADMIN')),
    status                  varchar(30) not null default 'ACTIVE' check (status in ('ACTIVE','DELETION_REQUESTED')),
    display_name            varchar(100) not null,             -- 프로비저닝 시 email local-part(없으면 '사용자')
    timezone                varchar(50) not null default 'Asia/Seoul',
    day_start_hour          smallint not null default 4 check (day_start_hour between 0 and 6),
    weekday_study_minutes   integer not null default 45 check (weekday_study_minutes between 0 and 720),
    weekend_study_minutes   integer not null default 240 check (weekend_study_minutes between 0 and 720),
    onboarding_completed_at timestamptz,
    calendar_token_hash     char(64) unique,
    deletion_requested_at   timestamptz,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),
    version                 bigint not null default 0
);

create table learning_goal (
    id                     uuid primary key default gen_random_uuid(),
    user_id                uuid not null unique references app_user(id) on delete cascade,
    target_role            varchar(40) not null check (target_role in ('JAVA_BACKEND')),
    target_completion_date date not null,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    version                bigint not null default 0
);

-- ---------------------------------------------------------------------
-- 2. Skill catalog (source: content/skill-tree/*.yaml → ContentSeeder)
-- ---------------------------------------------------------------------
create table skill (
    id                     uuid primary key default gen_random_uuid(),
    code                   varchar(100) not null unique,
    name                   varchar(200) not null,
    category               varchar(40) not null check (category in (
                              'JAVA','SPRING','DATABASE','WEB_HTTP','NETWORK','CS','ALGORITHM',
                              'TESTING','DEVOPS','SECURITY','PRACTICAL_ENGINEERING','SYSTEM_DESIGN','EXPLANATION')),
    parent_id              uuid references skill(id),
    description            text,
    minutes_per_level_step integer not null default 120 check (minutes_per_level_step between 10 and 2000),
    sort_order             integer not null default 0,
    catalog_version        integer not null default 1,
    active                 boolean not null default true
);
create index idx_skill_parent on skill(parent_id);
create index idx_skill_category on skill(category);

create table skill_prerequisite (
    skill_id              uuid not null references skill(id),
    prerequisite_skill_id uuid not null references skill(id),
    primary key (skill_id, prerequisite_skill_id),
    check (skill_id <> prerequisite_skill_id)
);

create table role_skill_target (                 -- 역할 기본 목표 (전역, seed)
    target_role                 varchar(40) not null check (target_role in ('JAVA_BACKEND')),
    skill_id                    uuid not null references skill(id),
    priority                    varchar(10) not null check (priority in ('MUST','SHOULD','LATER')),
    practical_importance        numeric(3,2) not null check (practical_importance between 0 and 1),
    target_knowledge_level      smallint not null check (target_knowledge_level between 0 and 5),
    target_implementation_level smallint not null check (target_implementation_level between 0 and 5),
    target_explanation_level    smallint not null check (target_explanation_level between 0 and 5),
    target_debugging_level      smallint not null check (target_debugging_level between 0 and 5),
    catalog_version             integer not null default 1,
    primary key (target_role, skill_id)
);

create table learning_goal_focus_skill (
    learning_goal_id uuid not null references learning_goal(id) on delete cascade,
    skill_id         uuid not null references skill(id),
    primary key (learning_goal_id, skill_id)
);

create table user_skill_state (
    id                       uuid primary key default gen_random_uuid(),
    user_id                  uuid not null references app_user(id) on delete cascade,
    skill_id                 uuid not null references skill(id),
    knowledge_level          smallint not null default 0 check (knowledge_level between 0 and 5),
    implementation_level     smallint not null default 0 check (implementation_level between 0 and 5),
    explanation_level        smallint not null default 0 check (explanation_level between 0 and 5),
    debugging_level          smallint not null default 0 check (debugging_level between 0 and 5),
    self_assessed_level      smallint check (self_assessed_level between 0 and 5),
    self_assessment_active   boolean not null default true,   -- 하락 규칙/진단 실패 시 false
    knowledge_changed_at      timestamptz,                    -- 축별 24h cooldown 판단
    implementation_changed_at timestamptz,
    explanation_changed_at    timestamptz,
    debugging_changed_at      timestamptz,
    evidence_count           integer not null default 0,
    last_practiced_at        timestamptz,
    updated_at               timestamptz not null default now(),
    version                  bigint not null default 0,
    unique (user_id, skill_id)
);

create table skill_state_change (
    id                 uuid primary key default gen_random_uuid(),
    user_id            uuid not null references app_user(id) on delete cascade,
    skill_id           uuid not null references skill(id),
    axis               varchar(20) not null check (axis in ('KNOWLEDGE','IMPLEMENTATION','EXPLANATION','DEBUGGING')),
    from_level         smallint not null check (from_level between 0 and 5),
    to_level           smallint not null check (to_level between 0 and 5),
    rule_code          varchar(40) not null,
    evidence_event_ids uuid[] not null default '{}',
    changed_at         timestamptz not null default now(),
    check (from_level <> to_level)
);
create index idx_skill_state_change_user_skill on skill_state_change(user_id, skill_id, changed_at desc);

-- ---------------------------------------------------------------------
-- 3. Learning (session, event, hint)
-- ---------------------------------------------------------------------
create table learning_session (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null references app_user(id) on delete cascade,
    learning_task_id uuid,                                    -- FK는 learning_task 생성 후 추가
    plan_date        date not null,
    started_at       timestamptz not null default now(),
    completed_at     timestamptz,
    actual_minutes   integer check (actual_minutes between 0 and 720),
    self_reflection  text,
    status           varchar(20) not null default 'IN_PROGRESS'
                     check (status in ('IN_PROGRESS','COMPLETED','ABANDONED')),
    version          bigint not null default 0
);
create index idx_learning_session_user_time on learning_session(user_id, started_at desc);
create unique index uq_learning_session_one_in_progress on learning_session(user_id) where status = 'IN_PROGRESS';

create table learning_event (
    id                 uuid primary key default gen_random_uuid(),
    user_id            uuid not null references app_user(id) on delete cascade,
    skill_id           uuid references skill(id),              -- 여러 skill 관련 사건은 skill별로 1행씩 기록
    session_id         uuid references learning_session(id) on delete set null,
    event_type         varchar(40) not null check (event_type in (
                       'SESSION_STARTED','SESSION_COMPLETED',
                       'SELF_EXPLANATION_SUBMITTED','SELF_EXPLANATION_SKIPPED','HINT_DISCLOSED',
                       'CHALLENGE_STARTED','CHALLENGE_SUBMITTED','CHALLENGE_EVALUATED',
                       'REVIEW_ANSWERED','LEECH_DETECTED',
                       'RUBBER_DUCK_COMPLETED',
                       'COACH_REVIEW_COMPLETED','COACH_FINDING_CLOSED',
                       'DIAGNOSTIC_PASSED','DIAGNOSTIC_FAILED',
                       'EVIDENCE_ACCEPTED','PLAN_REPLANNED')),
    source_type        varchar(30) check (source_type in (
                       'LEARNING_SESSION','CHALLENGE_ATTEMPT','CHALLENGE_SUBMISSION','REVIEW_ITEM',
                       'COACH_REVIEW','COACH_FINDING','EVIDENCE','LEARNING_PLAN','RUBBER_DUCK_SESSION')),
    source_id          uuid,
    plan_date          date not null,
    payload            jsonb not null default '{}'::jsonb,     -- docs/04 §6 event별 payload 스키마
    payload_version    smallint not null default 1,
    dedupe_key         varchar(200),
    occurred_at        timestamptz not null default now(),
    invalidated_at     timestamptz,
    invalidated_reason varchar(200)
);
create index idx_learning_event_user_time on learning_event(user_id, occurred_at desc);
create index idx_learning_event_user_skill_time on learning_event(user_id, skill_id, occurred_at desc);
create unique index uq_learning_event_dedupe on learning_event(user_id, dedupe_key) where dedupe_key is not null;

-- ---------------------------------------------------------------------
-- 4. AI call log
-- ---------------------------------------------------------------------
create table ai_call_log (
    id                  uuid primary key default gen_random_uuid(),
    user_id             uuid references app_user(id) on delete set null,
    operation           varchar(40) not null check (operation in (
                        'COACH_REVIEW','COACH_RESPONSE_FEEDBACK','CHALLENGE_GENERATE','CHALLENGE_EVALUATE',
                        'HINT_GENERATE','REVIEW_VARIANT','REVIEW_EVALUATE','EVIDENCE_DRAFT','REQUIREMENT_EXTRACT',
                        'RUBBER_DUCK','RUBBER_DUCK_SUMMARY')),
    provider            varchar(20) not null check (provider in ('deepseek','anthropic','fake','disabled')),
    model               varchar(80) not null,
    prompt_id           varchar(60) not null,
    prompt_version      varchar(10) not null,
    effort              varchar(10),                          -- off | low | high | max (thinking off이면 'off')
    attempt_no          smallint not null default 1,          -- 가드 위반 재시도 시 2
    input_tokens        integer,                              -- 캐시 적중분 포함한 전체 입력 토큰
    output_tokens       integer,                              -- 추론 토큰 포함
    reasoning_tokens    integer,                              -- output_tokens 중 추론 토큰 (공급자가 주면)
    cache_read_tokens   integer,                              -- 캐시 적중 입력 토큰
    cache_write_tokens  integer,                              -- DeepSeek는 항상 null (캐시 쓰기 과금 없음)
    cost_micro_usd      bigint not null default 0,
    latency_ms          integer,
    status              varchar(20) not null check (status in
                        ('SUCCESS','INVALID_OUTPUT','REFUSED','TIMEOUT','RATE_LIMITED','PROVIDER_ERROR','BUDGET_BLOCKED')),
    stop_reason         varchar(30),
    error_code          varchar(60),
    guard_actions       jsonb not null default '[]'::jsonb,
    request_fingerprint char(64),
    created_at          timestamptz not null default now()
);
create index idx_ai_call_log_user_time on ai_call_log(user_id, created_at desc);
create index idx_ai_call_log_time on ai_call_log(created_at desc);

create table hint_disclosure (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null references app_user(id) on delete cascade,
    target_type  varchar(30) not null check (target_type in ('CHALLENGE_ATTEMPT','COACH_FINDING')),
    target_id    uuid not null,
    hint_level   varchar(20) not null check (hint_level in
                 ('QUESTION_ONLY','CONCEPT_HINT','DIRECTION','PSEUDOCODE','PARTIAL_CODE','FULL_EXAMPLE')),
    content      text not null,
    content_origin varchar(20) not null check (content_origin in ('SEED','PREGENERATED','AI_GENERATED')),
    ai_call_id   uuid references ai_call_log(id) on delete set null,
    disclosed_at timestamptz not null default now(),
    unique (target_type, target_id, hint_level)
);
create index idx_hint_disclosure_user on hint_disclosure(user_id, disclosed_at desc);

-- ---------------------------------------------------------------------
-- 5. Plan
-- ---------------------------------------------------------------------
create table learning_plan (
    id                 uuid primary key default gen_random_uuid(),
    user_id            uuid not null references app_user(id) on delete cascade,
    learning_goal_id   uuid references learning_goal(id) on delete set null,
    plan_version       integer not null check (plan_version >= 1),
    status             varchar(20) not null check (status in ('ACTIVE','SUPERSEDED','ARCHIVED')),
    title              varchar(200) not null,
    supersedes_plan_id uuid references learning_plan(id),
    change_reason      varchar(1000),
    replan_recommended boolean not null default false,
    created_at         timestamptz not null default now(),
    superseded_at      timestamptz,
    version            bigint not null default 0,
    unique (user_id, plan_version)
);
-- replan 순서: 기존 plan SUPERSEDED + flush → 새 plan INSERT (docs/06 §11)
create unique index uq_learning_plan_one_active on learning_plan(user_id) where status = 'ACTIVE';

create table plan_milestone (
    id          uuid primary key default gen_random_uuid(),
    plan_id     uuid not null references learning_plan(id) on delete cascade,
    title       varchar(200) not null,
    description varchar(2000),
    start_date  date not null,
    end_date    date not null,
    priority    varchar(10) not null default 'MUST' check (priority in ('MUST','SHOULD','LATER')),
    status      varchar(20) not null default 'PLANNED'
                check (status in ('PLANNED','IN_PROGRESS','DONE','DEFERRED','DROPPED')),
    sort_order  integer not null default 0,
    updated_at  timestamptz not null default now(),
    version     bigint not null default 0,
    check (start_date <= end_date)
);
create index idx_plan_milestone_plan_start on plan_milestone(plan_id, start_date);

create table milestone_skill (
    milestone_id uuid not null references plan_milestone(id) on delete cascade,
    skill_id     uuid not null references skill(id),
    primary key (milestone_id, skill_id)
);
create index idx_milestone_skill_skill on milestone_skill(skill_id);

create table plan_skill_target (                 -- 계획별 목표 (role 기본값 복사 후 replan으로 조정)
    plan_id                     uuid not null references learning_plan(id) on delete cascade,
    skill_id                    uuid not null references skill(id),
    priority                    varchar(10) not null check (priority in ('MUST','SHOULD','LATER')),
    practical_importance        numeric(3,2) not null check (practical_importance between 0 and 1),
    target_knowledge_level      smallint not null check (target_knowledge_level between 0 and 5),
    target_implementation_level smallint not null check (target_implementation_level between 0 and 5),
    target_explanation_level    smallint not null check (target_explanation_level between 0 and 5),
    target_debugging_level      smallint not null check (target_debugging_level between 0 and 5),
    deferred                    boolean not null default false,
    adjustment                  varchar(20) not null default 'ROLE_DEFAULT'
                                check (adjustment in ('ROLE_DEFAULT','DEFERRED','TARGET_REDUCED','USER_EDITED')),
    primary key (plan_id, skill_id)
);

create table plan_progress_snapshot (
    id                       uuid primary key default gen_random_uuid(),
    user_id                  uuid not null references app_user(id) on delete cascade,
    plan_id                  uuid not null references learning_plan(id) on delete cascade,
    snapshot_date            date not null,
    horizon_date             date not null,
    nominal_budget_minutes   integer not null,
    completion_rate_bp       integer not null check (completion_rate_bp between 0 and 10000),
    effective_budget_minutes integer not null,
    required_must_minutes    integer not null,
    required_should_minutes  integer not null,
    ratio_bp                 integer,                         -- requiredMust/effective × 10000, effective=0이면 null
    risk_level               varchar(20) not null check (risk_level in ('LOW','MEDIUM','HIGH','CRITICAL')),
    generated_at             timestamptz not null default now(),
    unique (plan_id, snapshot_date)
);
create index idx_snapshot_user_date on plan_progress_snapshot(user_id, snapshot_date desc);

-- ---------------------------------------------------------------------
-- 6. Side project · Rubber duck
-- ---------------------------------------------------------------------
create table side_project (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references app_user(id) on delete cascade,
    name        varchar(100) not null,
    description varchar(1000),
    repo_url    varchar(500),                                -- 저장만 한다. 서버는 fetch하지 않는다 (docs/07 §5.5)
    stack       varchar(300),
    status      varchar(20) not null default 'ACTIVE' check (status in ('ACTIVE','PAUSED','DONE')),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    version     bigint not null default 0
);
-- planner는 가장 최근 updated_at인 ACTIVE 프로젝트 하나만 쓴다 (docs/06 SP-3)
create index idx_side_project_user_status on side_project(user_id, status, updated_at desc);

create table rubber_duck_session (
    id                  uuid primary key default gen_random_uuid(),
    user_id             uuid not null references app_user(id) on delete cascade,
    target_type         varchar(20) not null check (target_type in
                        ('CODE_READING','CHALLENGE','REVIEW_ITEM','CONCEPT','PROJECT_WORK')),
    target_id           uuid,                                -- CONCEPT면 null. PROJECT_WORK면 side_project.id (FK 없음)
    concept_key         varchar(120),                        -- CONCEPT 대상일 때
    skill_id            uuid references skill(id),           -- null이면 학습 이벤트를 남기지 않는다 (docs/06 RD-7)
    status              varchar(20) not null default 'IN_PROGRESS'
                        check (status in ('IN_PROGRESS','COMPLETED','ABANDONED')),
    turn_count          smallint not null default 0 check (turn_count >= 0),
    summary_json        jsonb,                               -- docs/04 §5.9 { gaps[], confirmed[] }
    learning_session_id uuid references learning_session(id) on delete set null,
    started_at          timestamptz not null default now(),
    completed_at        timestamptz,
    version             bigint not null default 0
);
create index idx_rubber_duck_session_user_time on rubber_duck_session(user_id, started_at desc);
-- 고아 세션 정리 job (IN_PROGRESS로 남은 세션을 ABANDONED로)
create index idx_rubber_duck_session_in_progress on rubber_duck_session(started_at)
    where status = 'IN_PROGRESS';

-- 사용자당 진행 중 세션 1개 (docs/04 I-18). 새 세션 시작 시 이전 세션을 ABANDONED로 바꾼 뒤 INSERT한다
create unique index uq_rubber_duck_session_one_in_progress on rubber_duck_session(user_id)
    where status = 'IN_PROGRESS';

create table rubber_duck_turn (
    id          uuid primary key default gen_random_uuid(),
    session_id  uuid not null references rubber_duck_session(id) on delete cascade,
    turn_no     smallint not null check (turn_no >= 1),
    user_text   text not null,                               -- SecretMasker 통과본 (docs/06 RD-6)
    ai_question text,                                        -- 마지막 턴은 null 가능
    learner_stuck boolean not null default false,            -- RD-3 "모르겠다" 판정 (서버 결정적 규칙, docs/06 §9.5)
    ai_call_id  uuid references ai_call_log(id) on delete set null,
    created_at  timestamptz not null default now(),
    unique (session_id, turn_no)                             -- 턴 순서 조회 인덱스를 겸한다
);

-- ---------------------------------------------------------------------
-- 7. Today
-- ---------------------------------------------------------------------
create table daily_plan (
    id                uuid primary key default gen_random_uuid(),
    user_id           uuid not null references app_user(id) on delete cascade,
    learning_plan_id  uuid references learning_plan(id) on delete set null,
    plan_date         date not null,
    available_minutes integer not null check (available_minutes between 5 and 720),
    energy_level      varchar(10) not null check (energy_level in ('LOW','NORMAL','HIGH')),
    deadline_risk     varchar(20) check (deadline_risk in ('LOW','MEDIUM','HIGH','CRITICAL')),
    comeback_mode     boolean not null default false,
    planner_version   varchar(20) not null default 'RULE_V1',
    generation_count  integer not null default 1,
    generated_at      timestamptz not null default now(),
    version           bigint not null default 0,
    unique (user_id, plan_date)
);

create table challenge (
    id                     uuid primary key default gen_random_uuid(),
    owner_user_id          uuid references app_user(id) on delete cascade,   -- null = 공용 seed
    seed_key               varchar(100) unique,                               -- seed challenge 식별자
    origin                 varchar(20) not null check (origin in ('SEED','MANUAL','AI_GENERATED')),
    status                 varchar(20) not null default 'DRAFT'
                           check (status in ('DRAFT','VALIDATED','REJECTED','RETIRED')),
    generation_status      varchar(20) not null default 'COMPLETED'
                           check (generation_status in ('PENDING','RUNNING','COMPLETED','FAILED')),
    failure_code           varchar(40),
    status_updated_at      timestamptz not null default now(),
    purpose                varchar(20) not null default 'PRACTICE' check (purpose in ('PRACTICE','DIAGNOSTIC')),
    is_transfer            boolean not null default false,
    title                  varchar(200),
    difficulty             smallint not null check (difficulty between 1 and 5),
    estimated_minutes      integer check (estimated_minutes between 5 and 180),
    scenario               text,
    prompt                 text,
    constraints_json       jsonb,
    expected_concepts_json jsonb,
    rubric_json            jsonb,
    common_mistakes_json   jsonb,
    transfer_targets_json  jsonb,
    hints_json             jsonb,                                            -- 1~3단계 사전 hint
    ai_call_id             uuid references ai_call_log(id) on delete set null,
    prompt_version         varchar(10),
    rejection_reason       varchar(1000),
    created_at             timestamptz not null default now(),
    check (status <> 'VALIDATED' or (prompt is not null and rubric_json is not null and expected_concepts_json is not null))
);
create index idx_challenge_owner on challenge(owner_user_id, created_at desc);

create table learning_task (
    id                uuid primary key default gen_random_uuid(),
    daily_plan_id     uuid not null references daily_plan(id) on delete cascade,
    user_id           uuid not null references app_user(id) on delete cascade,
    skill_id          uuid references skill(id),
    milestone_id      uuid references plan_milestone(id) on delete set null,
    challenge_id      uuid references challenge(id) on delete set null,
    task_type         varchar(20) not null check (task_type in
                      ('RECALL','REVIEW','CHALLENGE','PROJECT_TASK','COACH_REVIEW',
                       'READING','READ_CODE','EXPLAIN')),
    title             varchar(200) not null,
    description       varchar(2000),
    estimated_minutes integer not null check (estimated_minutes between 1 and 720),
    is_main           boolean not null default false,
    status            varchar(20) not null default 'PLANNED'
                      check (status in ('PLANNED','IN_PROGRESS','COMPLETED','SKIPPED','DEFERRED')),
    reason_codes      varchar(40)[] not null default '{}',
    score_breakdown   jsonb,
    sort_order        integer not null default 0,
    completed_at      timestamptz,
    version           bigint not null default 0,
    side_project_id   uuid references side_project(id) on delete set null,   -- PROJECT_TASK 대상 프로젝트 (V9에서 추가)
    reading_key       varchar(150),                                          -- READ_CODE 대상 reading key, FK 없음 (V9에서 추가)
    reading_feedback  varchar(20) check (reading_feedback in ('HELPFUL','TOO_HARD','BORING')), -- READ_CODE 읽기 평가, 선택 (V9에서 추가)
    constraint learning_task_reading_key_type check ((reading_key is not null) = (task_type = 'READ_CODE')),
    constraint learning_task_reading_feedback_type check (reading_feedback is null or task_type = 'READ_CODE')
);
create index idx_learning_task_daily_plan on learning_task(daily_plan_id);
create index idx_learning_task_user on learning_task(user_id);
-- WIP=1: 진행 가능한(main이면서 PLANNED/IN_PROGRESS) task는 하루 1개
create unique index uq_learning_task_one_active_main on learning_task(daily_plan_id)
    where is_main and status in ('PLANNED','IN_PROGRESS');

alter table learning_session
    add constraint fk_learning_session_task foreign key (learning_task_id) references learning_task(id) on delete set null;

-- ---------------------------------------------------------------------
-- 8. Training
-- ---------------------------------------------------------------------
create table challenge_skill (
    challenge_id uuid not null references challenge(id) on delete cascade,
    skill_id     uuid not null references skill(id),
    primary key (challenge_id, skill_id)
);
create index idx_challenge_skill_skill on challenge_skill(skill_id);

create table challenge_attempt (
    id                       uuid primary key default gen_random_uuid(),
    challenge_id             uuid not null references challenge(id) on delete cascade,
    user_id                  uuid not null references app_user(id) on delete cascade,
    status                   varchar(20) not null default 'STARTED'
                             check (status in ('STARTED','SUBMITTED','EVALUATED','ABANDONED')),
    self_explanation         text,
    self_explanation_skipped boolean not null default false,
    submission_count         integer not null default 0,
    max_hint_level           varchar(20) not null default 'SELF_EXPLAIN' check (max_hint_level in
                             ('SELF_EXPLAIN','QUESTION_ONLY','CONCEPT_HINT','DIRECTION','PSEUDOCODE','PARTIAL_CODE','FULL_EXAMPLE')),
    evaluated_outcome        varchar(20) check (evaluated_outcome in ('CORRECT','PARTIAL','INCORRECT','NOT_EVALUATED')),
    outcome                  varchar(30) check (outcome in
                             ('SOLVED_INDEPENDENTLY','SOLVED_WITH_HINTS','PARTIAL','FAILED','ABANDONED')),
    rubric_coverage_bp       integer check (rubric_coverage_bp between 0 and 10000),
    explanation_coverage_bp  integer check (explanation_coverage_bp between 0 and 10000),
    started_at               timestamptz not null default now(),
    completed_at             timestamptz,
    version                  bigint not null default 0
);
create index idx_challenge_attempt_user_time on challenge_attempt(user_id, started_at desc);
create index idx_challenge_attempt_challenge on challenge_attempt(challenge_id);

create table challenge_submission (
    id                      uuid primary key default gen_random_uuid(),
    attempt_id              uuid not null references challenge_attempt(id) on delete cascade,
    user_id                 uuid not null references app_user(id) on delete cascade,
    submission_no           integer not null check (submission_no >= 1),
    answer_text             text,
    code                    text,
    language                varchar(20) check (language in
                            ('JAVA','KOTLIN','SQL','DART','YAML','PROPERTIES','XML','SHELL','OTHER')),
    evaluation_status       varchar(20) not null default 'PENDING'
                            check (evaluation_status in ('PENDING','RUNNING','COMPLETED','FAILED')),
    failure_code            varchar(40),
    status_updated_at       timestamptz not null default now(),
    evaluated_outcome       varchar(20) check (evaluated_outcome in ('CORRECT','PARTIAL','INCORRECT','NOT_EVALUATED')),
    rubric_coverage_bp      integer check (rubric_coverage_bp between 0 and 10000),
    explanation_coverage_bp integer check (explanation_coverage_bp between 0 and 10000),
    evaluation_json         jsonb,
    ai_call_id              uuid references ai_call_log(id) on delete set null,
    submitted_at            timestamptz not null default now(),
    evaluated_at            timestamptz,
    unique (attempt_id, submission_no),
    check (answer_text is not null or code is not null)
);
create index idx_challenge_submission_status on challenge_submission(evaluation_status, status_updated_at)
    where evaluation_status in ('PENDING','RUNNING');

-- ---------------------------------------------------------------------
-- 9. Review (Memory)
-- ---------------------------------------------------------------------
create table review_item (
    id                       uuid primary key default gen_random_uuid(),
    user_id                  uuid not null references app_user(id) on delete cascade,
    skill_id                 uuid not null references skill(id),
    origin                   varchar(20) not null check (origin in ('SEED','MANUAL','AI_GENERATED')),
    source_type              varchar(30) not null check (source_type in
                             ('SEED_CARD','MANUAL','CHALLENGE_ATTEMPT','COACH_FINDING','EVIDENCE','RUBBER_DUCK')),
    source_id                uuid,
    concept_key              varchar(150) not null,
    review_type              varchar(20) not null check (review_type in ('RECALL','BUG_SPOT','EXPLAIN','CHOICE')),
    prompt                   text not null,
    expected_answer          text not null,
    rubric_json              jsonb not null default '[]'::jsonb,
    due_at                   timestamptz not null,
    interval_days            integer not null default 1 check (interval_days between 1 and 365),
    consecutive_successes    integer not null default 0,
    consecutive_failures     integer not null default 0,
    review_count             integer not null default 0,
    last_result              varchar(10) check (last_result in ('AGAIN','HARD','GOOD','EASY')),
    last_reviewed_at         timestamptz,
    variant_status           varchar(20) not null default 'NONE'
                             check (variant_status in ('NONE','PENDING','RUNNING','READY','FAILED')),
    variant_prompt           text,
    variant_expected_answer  text,
    variant_rubric_json      jsonb,
    variant_status_updated_at timestamptz,
    status                   varchar(20) not null default 'ACTIVE' check (status in ('ACTIVE','SUSPENDED','ARCHIVED')),
    created_at               timestamptz not null default now(),
    version                  bigint not null default 0,
    unique (user_id, concept_key)
);
create index idx_review_item_due on review_item(user_id, status, due_at);

create table review_answer (
    id                 uuid primary key default gen_random_uuid(),
    review_item_id     uuid not null references review_item(id) on delete cascade,
    user_id            uuid not null references app_user(id) on delete cascade,
    plan_date          date not null,
    was_variant        boolean not null default false,
    presented_prompt   text not null,
    answer_text        text,
    self_rating        varchar(10) not null check (self_rating in ('AGAIN','HARD','GOOD','EASY')),
    evaluated_outcome  varchar(20) not null default 'NOT_EVALUATED'
                       check (evaluated_outcome in ('CORRECT','PARTIAL','INCORRECT','NOT_EVALUATED')),
    rubric_coverage_bp integer check (rubric_coverage_bp between 0 and 10000),
    hint_level         varchar(20) not null default 'SELF_EXPLAIN' check (hint_level in
                       ('SELF_EXPLAIN','QUESTION_ONLY','CONCEPT_HINT','DIRECTION','PSEUDOCODE','PARTIAL_CODE','FULL_EXAMPLE')),
    final_rating       varchar(10) not null check (final_rating in ('AGAIN','HARD','GOOD','EASY')),
    adjusted_by        varchar(30)[] not null default '{}',
    response_seconds   integer check (response_seconds between 0 and 86400),
    interval_before    integer not null,
    interval_after     integer not null,
    strategy           varchar(20) not null default 'RULE_V1',
    ai_call_id         uuid references ai_call_log(id) on delete set null,
    answered_at        timestamptz not null default now()
);
create index idx_review_answer_item_time on review_answer(review_item_id, answered_at desc);
create index idx_review_answer_user_time on review_answer(user_id, answered_at desc);

-- ---------------------------------------------------------------------
-- 10. Project Coach
-- ---------------------------------------------------------------------
create table coach_review (
    id                      uuid primary key default gen_random_uuid(),
    user_id                 uuid not null references app_user(id) on delete cascade,
    status                  varchar(20) not null default 'PENDING'
                            check (status in ('PENDING','RUNNING','COMPLETED','FAILED')),
    closed_at               timestamptz,                     -- POST /complete 시각
    failure_code            varchar(40),
    status_updated_at       timestamptz not null default now(),
    content_type            varchar(10) not null check (content_type in ('CODE','DIFF','LOG')),
    language                varchar(20) check (language in
                            ('JAVA','KOTLIN','SQL','DART','YAML','PROPERTIES','XML','SHELL','OTHER')),
    content                 text,                            -- 마스킹된 원문, 보존기간 후 null
    content_sha256          char(64) not null,
    content_bytes           integer not null check (content_bytes > 0),
    content_lines           integer not null check (content_lines > 0),
    masked_secret_count     integer not null default 0,
    confidential_consent    boolean not null check (confidential_consent),
    user_self_review        varchar(5000),
    self_review_axes        varchar(40)[] not null default '{}',
    context_json            jsonb not null default '{}'::jsonb,
    ai_call_id              uuid references ai_call_log(id) on delete set null,
    content_retention_until timestamptz not null,
    content_purged_at       timestamptz,
    created_at              timestamptz not null default now(),
    completed_at            timestamptz,
    version                 bigint not null default 0,
    side_project_id         uuid references side_project(id) on delete set null   -- 리뷰 대상 프로젝트 (V9에서 추가)
);
create index idx_coach_review_user_time on coach_review(user_id, created_at desc);
create index idx_coach_review_retention on coach_review(content_retention_until) where content_purged_at is null;
create index idx_coach_review_status on coach_review(status, status_updated_at) where status in ('PENDING','RUNNING');

create table coach_finding (
    id                  uuid primary key default gen_random_uuid(),
    coach_review_id     uuid not null references coach_review(id) on delete cascade,
    finding_type        varchar(20) not null check (finding_type in ('BUG','RISK','LEARNING_POINT')),
    category            varchar(40) not null check (category in (
                        'CORRECTNESS','NULL_BOUNDARY','RESOURCE_LIFECYCLE','EXCEPTION_STRATEGY','SECURITY',
                        'PERFORMANCE','CONCURRENCY','OBSERVABILITY','MAINTAINABILITY','TRANSACTION_DATA_CONSISTENCY')),
    skill_id            uuid references skill(id),
    summary             varchar(500) not null,
    learning_question   varchar(1000) not null,
    location_start_line integer check (location_start_line >= 1),
    location_end_line   integer check (location_end_line >= 1),
    verification_status varchar(20) not null check (verification_status in ('VERIFIED','SUPPORTED','AI_JUDGMENT','UNCERTAIN')),
    confidence          varchar(10) not null check (confidence in ('HIGH','MEDIUM','LOW')),
    source_type         varchar(30) not null check (source_type in (
                        'COMPILER','TEST_RESULT','STATIC_ANALYSIS','CURATED_SOURCE','OFFICIAL_DOC','SECURITY_GUIDE','AI_REASONING')),
    source_reference    varchar(1000),
    mentioned_by_user   boolean not null default false,       -- AI 분류: self-review에 이미 언급됨
    user_response       varchar(5000),
    ai_feedback         varchar(2000),
    ai_follow_up_question varchar(300),
    user_identified_issue boolean,                             -- 응답 피드백 결과 (COACH_RESPONSE_FEEDBACK)
    feedback_ai_call_id uuid references ai_call_log(id) on delete set null,
    discovered_by       varchar(30) check (discovered_by in ('MENTIONED_UNPROMPTED','FOUND_AFTER_HINT','MISSED')),
    max_hint_level      varchar(20) not null default 'SELF_EXPLAIN' check (max_hint_level in
                        ('SELF_EXPLAIN','QUESTION_ONLY','CONCEPT_HINT','DIRECTION','PSEUDOCODE','PARTIAL_CODE','FULL_EXAMPLE')),
    status              varchar(20) not null default 'OPEN'
                        check (status in ('OPEN','USER_RESPONDED','RESOLVED','DISMISSED')),
    sort_order          integer not null default 0,
    version             bigint not null default 0,
    check (location_end_line is null or location_start_line is null or location_start_line <= location_end_line),
    check (verification_status <> 'VERIFIED' or source_type in ('COMPILER','TEST_RESULT','CURATED_SOURCE')),
    check (source_type not in ('COMPILER','TEST_RESULT','STATIC_ANALYSIS') or verification_status in ('VERIFIED','SUPPORTED'))
);
create index idx_coach_finding_review on coach_finding(coach_review_id);

create table thinking_pattern_observation (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references app_user(id) on delete cascade,
    source_type varchar(30) not null check (source_type in ('COACH_REVIEW','CHALLENGE_ATTEMPT')),
    source_id   uuid not null,
    axis        varchar(40) not null check (axis in (
                'CORRECTNESS','NULL_BOUNDARY','RESOURCE_LIFECYCLE','EXCEPTION_STRATEGY','SECURITY',
                'PERFORMANCE','CONCURRENCY','OBSERVABILITY','MAINTAINABILITY','TRANSACTION_DATA_CONSISTENCY')),
    observation varchar(30) not null check (observation in
                ('MENTIONED_UNPROMPTED','FOUND_AFTER_HINT','MISSED','INCORRECT_CLAIM')),
    finding_id  uuid references coach_finding(id) on delete set null,
    observed_at timestamptz not null default now()
);
create index idx_thinking_obs_user_axis_time on thinking_pattern_observation(user_id, axis, observed_at desc);

-- ---------------------------------------------------------------------
-- 11. Evidence / Weekly / Requirement Radar
-- ---------------------------------------------------------------------
create table evidence_candidate (
    id                       uuid primary key default gen_random_uuid(),
    user_id                  uuid not null references app_user(id) on delete cascade,
    skill_id                 uuid references skill(id),
    source_learning_event_id uuid references learning_event(id) on delete set null,
    status                   varchar(20) not null default 'CANDIDATE'
                             check (status in ('CANDIDATE','ACCEPTED','REJECTED')),
    generation_status        varchar(20) not null default 'NONE'
                             check (generation_status in ('NONE','PENDING','RUNNING','COMPLETED','FAILED')),
    failure_code             varchar(40),
    status_updated_at        timestamptz not null default now(),
    ai_draft_json            jsonb,                           -- AI 초안 원본 (사용자 편집본과 분리)
    ai_call_id               uuid references ai_call_log(id) on delete set null,
    title                    varchar(200),
    problem                  varchar(3000),
    analysis                 varchar(3000),
    action                   varchar(3000),
    result                   varchar(3000),
    reference_links          varchar(500)[] not null default '{}',
    explanation_topics       jsonb not null default '[]'::jsonb,
    accepted_at              timestamptz,
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    version                  bigint not null default 0
);
create index idx_evidence_user_status on evidence_candidate(user_id, status, created_at desc);

create table weekly_review (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references app_user(id) on delete cascade,
    week_start_date date not null,
    metrics_json    jsonb not null,
    reflection      varchar(5000),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    version         bigint not null default 0,
    unique (user_id, week_start_date)
);

create table requirement_doc (
    id                   uuid primary key default gen_random_uuid(),
    user_id              uuid not null references app_user(id) on delete cascade,
    title                varchar(200) not null,
    source_url           varchar(2000),
    source_text          text,
    source_text_purged_at timestamptz,
    analysis_status      varchar(20) not null default 'PENDING'
                         check (analysis_status in ('PENDING','RUNNING','COMPLETED','FAILED')),
    failure_code         varchar(40),
    status_updated_at    timestamptz not null default now(),
    ai_call_id           uuid references ai_call_log(id) on delete set null,
    created_at           timestamptz not null default now(),
    analyzed_at          timestamptz
);
create index idx_requirement_doc_user_time on requirement_doc(user_id, created_at desc);

create table requirement_item (
    id                   uuid primary key default gen_random_uuid(),
    requirement_doc_id   uuid not null references requirement_doc(id) on delete cascade,
    raw_text             varchar(1000) not null,
    requirement_type     varchar(20) not null check (requirement_type in ('REQUIRED','PREFERRED')),
    skill_id             uuid references skill(id),
    fit_category         varchar(20) check (fit_category in ('READY','STRETCH','LATER')),
    matched_evidence_ids uuid[] not null default '{}',
    sort_order           integer not null default 0
);
create index idx_requirement_item_doc on requirement_item(requirement_doc_id);

-- ---------------------------------------------------------------------
-- 12. Idempotency
-- ---------------------------------------------------------------------
create table idempotency_record (
    user_id         uuid not null references app_user(id) on delete cascade,
    idempotency_key varchar(100) not null,
    request_method  varchar(10) not null,
    request_path    varchar(300) not null,
    request_hash    char(64) not null,
    response_status integer,
    response_body   jsonb,
    created_at      timestamptz not null default now(),
    expires_at      timestamptz not null,
    primary key (user_id, idempotency_key)
);
create index idx_idempotency_expires on idempotency_record(expires_at);

-- ---------------------------------------------------------------------
-- 13. Supabase hardening (순수 PostgreSQL에서는 no-op)
-- ---------------------------------------------------------------------
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        execute 'revoke all on schema devpilot from anon';
        execute 'revoke all on all tables in schema devpilot from anon';
        execute 'alter default privileges in schema devpilot revoke all on tables from anon';
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        execute 'revoke all on schema devpilot from authenticated';
        execute 'revoke all on all tables in schema devpilot from authenticated';
        execute 'alter default privileges in schema devpilot revoke all on tables from authenticated';
    end if;
end
$$;
