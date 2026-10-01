-- V2__user_goal_skill.sql
-- Sprint: S1 · 사용자, 학습 목표, skill catalog, skill state, idempotency
-- 생성 기준: database/schema.sql (v2). 적용된 migration은 수정하지 않는다.
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

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
