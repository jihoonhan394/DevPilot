-- V3__plan.sql
-- Sprint: S1 · 학습 계획, milestone, 계획별 skill 목표, 진행 스냅샷
-- 생성 기준: database/schema.sql (v2). 적용된 migration은 수정하지 않는다.
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

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
