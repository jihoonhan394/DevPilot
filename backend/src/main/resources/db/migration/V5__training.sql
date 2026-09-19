-- V5__training.sql
-- Sprint: S3 · hint 공개 기록, challenge skill 연결, attempt, submission
-- 생성 기준: database/schema.sql (v2). 적용된 migration은 수정하지 않는다.
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

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
