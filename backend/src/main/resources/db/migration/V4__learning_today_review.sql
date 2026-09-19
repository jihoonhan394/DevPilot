-- V4__learning_today_review.sql
-- Sprint: S2 · 학습 세션·이벤트, AI 호출 로그, Today, challenge(참조용), 복습
-- 생성 기준: database/schema.sql (v2). 적용된 migration은 수정하지 않는다.
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

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
    version           bigint not null default 0
);

create index idx_learning_task_daily_plan on learning_task(daily_plan_id);

create index idx_learning_task_user on learning_task(user_id);

create unique index uq_learning_task_one_active_main on learning_task(daily_plan_id)
    where is_main and status in ('PLANNED','IN_PROGRESS');

alter table learning_session
    add constraint fk_learning_session_task foreign key (learning_task_id) references learning_task(id) on delete set null;

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
