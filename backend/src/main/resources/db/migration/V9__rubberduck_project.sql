-- V9__rubberduck_project.sql
-- 단계: S1(side_project) · S3(러버덕 세션/턴, reading_key) — docs/11 §3
-- 생성 기준: database/schema.sql (v2). 적용된 migration은 수정하지 않는다.
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

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

alter table learning_task
    add column side_project_id uuid references side_project(id) on delete set null;

-- READ_CODE 과제가 가리키는 content/curated-repos.yaml reading key. 콘텐츠 key라 FK가 없다 (docs/19 §3.8)
alter table learning_task
    add column reading_key varchar(150);

alter table learning_task
    add constraint learning_task_reading_key_type check ((reading_key is not null) = (task_type = 'READ_CODE'));

alter table coach_review
    add column side_project_id uuid references side_project(id) on delete set null;
