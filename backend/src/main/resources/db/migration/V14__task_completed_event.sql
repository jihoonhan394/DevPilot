-- V14__task_completed_event.sql
-- 단계: 학습 단계 6칸 — docs/04 §6, docs/06 §5.11, ADR-042
-- V1~V13은 고치지 않는다(ADR-038). 이 파일은 alter만 쓴다.
-- 인라인 CHECK를 다시 만들 때는 PostgreSQL 자동 이름(<table>_<column>_check)을 그대로 쓰고
-- drop constraint if exists → add constraint <같은 이름>으로 붙인다(다음 migration이 또 찾을 수 있게).
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

-- 학습 단계는 저장하지 않고 학습 이벤트에서 파생 계산한다(ADR-042). 그런데 과제를 끝낸 사실이
-- learning_event에 남지 않아 여섯 칸 중 세 칸(개념 읽기·코드 읽기·만들기의 PROJECT_TASK)을
-- 판정할 입력이 없었다. 과제 완료를 이벤트로 남겨 그 입력을 만든다 — 테이블·컬럼은 늘리지 않는다.
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
        'REDO_COMPLETED','TIP_VIEWED','TERM_CARD_CREATED',
        'UNIT_SOLVED','TASK_COMPLETED'));
