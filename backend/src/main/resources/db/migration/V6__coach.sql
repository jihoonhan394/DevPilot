-- V6__coach.sql
-- Sprint: S4 · Project Coach, thinking pattern
-- 생성 기준: database/schema.sql (v2). 적용된 migration은 수정하지 않는다.
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

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
    version                 bigint not null default 0
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
