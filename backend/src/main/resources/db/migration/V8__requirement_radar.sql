-- V8__requirement_radar.sql
-- Sprint: S7 · Requirement Radar
-- 생성 기준: database/schema.sql (v2). 적용된 migration은 수정하지 않는다.
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

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
