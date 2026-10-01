-- V10__track_notes_redo.sql
-- 단계: S3(학습 트랙 3종, 프로젝트 기록, 경험 기록 분류, 오늘의 팁, 용어 카드, 설명 기록, 문제 시간 제한, 개념 읽기) · S4(재현 과제)
--       — docs/04 §10.1, docs/11 §3
-- V1~V9는 고치지 않는다(ADR-038). 이 파일은 alter만 쓴다.
-- 인라인 CHECK를 다시 만들 때는 PostgreSQL 자동 이름(<table>_<column>_check)을 그대로 쓰고
-- drop constraint if exists → add constraint <같은 이름>으로 붙인다(다음 migration이 또 찾을 수 있게).
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

-- §10.1 1번. 학습 트랙 3종 (docs/04 §3 TargetRole)
alter table learning_goal drop constraint if exists learning_goal_target_role_check;
alter table learning_goal add constraint learning_goal_target_role_check
    check (target_role in ('JAVA_BACKEND','JAVA_BACKEND_STARTER','INTEGRATION_ENGINEER'));

alter table role_skill_target drop constraint if exists role_skill_target_target_role_check;
alter table role_skill_target add constraint role_skill_target_target_role_check
    check (target_role in ('JAVA_BACKEND','JAVA_BACKEND_STARTER','INTEGRATION_ENGINEER'));

-- §10.1 8번. skill 카테고리에 INTEGRATION(연동) 추가. 값 순서는 docs/04 §3과 같다
alter table skill drop constraint if exists skill_category_check;
alter table skill add constraint skill_category_check
    check (category in (
        'JAVA','SPRING','DATABASE','WEB_HTTP','NETWORK','CS','ALGORITHM',
        'TESTING','DEVOPS','SECURITY','INTEGRATION','PRACTICAL_ENGINEERING','SYSTEM_DESIGN','EXPLANATION'));

-- §10.1 2번. 재현 과제(REDO, docs/06 §5.10)
alter table learning_task drop constraint if exists learning_task_task_type_check;
alter table learning_task add constraint learning_task_task_type_check
    check (task_type in ('RECALL','REVIEW','CHALLENGE','PROJECT_TASK','COACH_REVIEW',
                         'READING','READ_CODE','EXPLAIN','REDO'));

-- §10.1 3번. 재현 과제의 원본과 "AI 없이 했는가" 답 (docs/04 I-20·I-21)
-- 자기 참조 FK는 cascade 없음 — 원본 행은 사용자 삭제 때만 사라진다
alter table learning_task
    add column redo_source_task_id uuid references learning_task(id);

alter table learning_task
    add column redo_without_ai boolean;

alter table learning_task add constraint learning_task_redo_source_type
    check ((redo_source_task_id is not null) = (task_type = 'REDO'));

alter table learning_task add constraint learning_task_redo_without_ai_type
    check (redo_without_ai is null or task_type = 'REDO');

alter table learning_task add constraint learning_task_redo_answer_required
    check (not (task_type = 'REDO' and status = 'COMPLETED' and redo_without_ai is null));

-- §10.1 10번. 설명 기록 (docs/04 I-24). explained_note는 마스킹본이다 (docs/05 §1.11)
alter table learning_task
    add column explained_to_person boolean;

alter table learning_task
    add column explained_note varchar(500);

alter table learning_task add constraint learning_task_explained_by_type
    check ((explained_to_person is null and explained_note is null)
           or task_type in ('EXPLAIN','READ_CODE'));

-- §10.1 13번. V9의 CHECK는 (reading_key is not null) = (task_type = 'READ_CODE') 였다.
-- 개념 읽기(docs/19 §3.13)를 코드 읽기와 같은 칸에 담으므로 READING도 reading_key를 가질 수 있다 (docs/04 I-17)
alter table learning_task drop constraint if exists learning_task_reading_key_type;
alter table learning_task add constraint learning_task_reading_key_type check (
       (task_type = 'READ_CODE' and reading_key is not null)                    -- 코드 읽기: 반드시 있다
    or (task_type = 'READING')                                                  -- 개념 읽기: 있을 수도 없을 수도
    or (task_type not in ('READ_CODE', 'READING') and reading_key is null));    -- 그 밖: 없다
-- learning_task_reading_feedback_type은 그대로 둔다 (읽기 평가는 READ_CODE 전용)

-- §10.1 4번. 재현 후보 조회 (docs/04 §11, docs/06 §5.10 RE-2·RE-5). 부분 인덱스라 READING·EXPLAIN 등은 담지 않는다
create index idx_learning_task_redo_candidate
    on learning_task(user_id, task_type, status, completed_at desc)
    where task_type in ('CHALLENGE','PROJECT_TASK','REDO');

-- §10.1 5번. 재현 완료·팁 표시·용어 카드 생성 이벤트 (docs/04 §6)
alter table learning_event drop constraint if exists learning_event_event_type_check;
alter table learning_event add constraint learning_event_event_type_check
    check (event_type in (
        'SESSION_STARTED','SESSION_COMPLETED',
        'SELF_EXPLANATION_SUBMITTED','SELF_EXPLANATION_SKIPPED','HINT_DISCLOSED',
        'CHALLENGE_STARTED','CHALLENGE_SUBMITTED','CHALLENGE_EVALUATED',
        'REVIEW_ANSWERED','LEECH_DETECTED',
        'RUBBER_DUCK_COMPLETED',
        'COACH_REVIEW_COMPLETED','COACH_FINDING_CLOSED',
        'DIAGNOSTIC_PASSED','DIAGNOSTIC_FAILED',
        'EVIDENCE_ACCEPTED','PLAN_REPLANNED',
        'REDO_COMPLETED','TIP_VIEWED','TERM_CARD_CREATED'));

alter table learning_event drop constraint if exists learning_event_source_type_check;
alter table learning_event add constraint learning_event_source_type_check
    check (source_type in (
        'LEARNING_SESSION','CHALLENGE_ATTEMPT','CHALLENGE_SUBMISSION','REVIEW_ITEM',
        'COACH_REVIEW','COACH_FINDING','EVIDENCE','LEARNING_PLAN','RUBBER_DUCK_SESSION',
        'LEARNING_TASK'));

-- §10.1 6번. 재현 실패·용어·팁에서 만든 복습 카드 (docs/06 §5.10 RE-7, §5.12 TIP-5, docs/05 §20.7)
alter table review_item drop constraint if exists review_item_source_type_check;
alter table review_item add constraint review_item_source_type_check
    check (source_type in ('SEED_CARD','MANUAL','CHALLENGE_ATTEMPT','COACH_FINDING','EVIDENCE',
                           'RUBBER_DUCK','REDO_TASK','TERM','TIP'));

-- §10.1 9번. 경험 기록 분류 (docs/04 §4.9, I-23). 상태가 아니라 분류이므로 전이표가 없다
alter table side_project
    add column kind varchar(20) not null default 'SIDE';

alter table side_project add constraint side_project_kind_check
    check (kind in ('SIDE','PAST_WORK'));

-- §10.1 7번. 프로젝트 기록 (결정·장애) — docs/04 §4.10, I-22
create table side_project_note (
    id                  uuid primary key default gen_random_uuid(),
    user_id             uuid not null references app_user(id) on delete cascade,
    side_project_id     uuid not null references side_project(id) on delete cascade,
    note_type           varchar(20) not null check (note_type in ('DECISION','INCIDENT')),
    title               varchar(200) not null,
    occurred_on         date not null,
    skill_id            uuid references skill(id),          -- 선택. skill이 비활성화돼도 기록은 남는다
    decision_choice     text,                                -- DECISION: 무엇을 골랐나
    decision_options    text,                                -- DECISION: 어떤 선택지가 있었나
    decision_rationale  text,                                -- DECISION: 왜 그것을 골랐나
    incident_symptom    text,                                -- INCIDENT: 무엇이 잘못됐나
    incident_detection  text,                                -- INCIDENT: 어떻게 찾았나
    incident_fix        text,                                -- INCIDENT: 무엇으로 고쳤나
    incident_prevention text,                                -- INCIDENT: 무엇으로 다시 막나
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    version             bigint not null default 0,
    constraint side_project_note_body_by_type check (
        (note_type = 'DECISION'
         and decision_choice is not null and decision_options is not null and decision_rationale is not null
         and incident_symptom is null and incident_detection is null
         and incident_fix is null and incident_prevention is null)
     or (note_type = 'INCIDENT'
         and incident_symptom is not null and incident_detection is not null
         and incident_fix is not null and incident_prevention is not null
         and decision_choice is null and decision_options is null and decision_rationale is null))
);

-- 기록 목록·cursor 정렬 (docs/04 §11, docs/05 §19.10)
create index idx_side_project_note_project
    on side_project_note(side_project_id, occurred_on desc, id desc);

-- §10.1 11번. 문제 시간 제한과 시도 경과 시간 (docs/04 I-25). 둘 다 규칙 입력이 아니다
alter table challenge
    add column time_limit_minutes integer;

alter table challenge add constraint challenge_time_limit_minutes_check
    check (time_limit_minutes is null or time_limit_minutes between 1 and 120);

alter table challenge_attempt
    add column elapsed_seconds integer;

alter table challenge_attempt add constraint challenge_attempt_elapsed_seconds_check
    check (elapsed_seconds is null or elapsed_seconds >= 0);

-- §10.1 12번. 오늘의 팁 표시 이력 (docs/04 §4.11, I-26)
create table user_daily_tip (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references app_user(id) on delete cascade,
    tip_key     varchar(120) not null,               -- content/tips 의 key. FK 없음(코드 읽기 reading_key 선례)
    shown_on    date not null,                        -- plan-day
    feedback    varchar(20) check (feedback in ('KNEW_IT','LEARNED','WILL_TRY')),
    created_at  timestamptz not null default now(),
    constraint user_daily_tip_unique unique (user_id, tip_key)
);

-- 그 plan-day에 이미 보여 준 팁이 있는지와 최근 표시 이력 (docs/04 §11, docs/05 §20.2)
create index idx_user_daily_tip_user_shown on user_daily_tip(user_id, shown_on desc);
