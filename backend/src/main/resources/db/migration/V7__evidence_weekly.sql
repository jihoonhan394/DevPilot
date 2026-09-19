-- V7__evidence_weekly.sql
-- Sprint: S5-S6 · evidence, weekly review
-- 생성 기준: database/schema.sql (v2). 적용된 migration은 수정하지 않는다.
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

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
