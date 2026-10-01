-- V13__lesson_reexplain_operation.sql
-- ADR-047: 설명이 안 통할 때 다른 방식으로 한 번 더 설명한다 (docs/17 §3.12, docs/05 §21.10)
-- V1~V12는 고치지 않는다(ADR-038). 이 파일은 alter만 쓴다.
-- 인라인 CHECK를 다시 만들 때는 PostgreSQL 자동 이름(<table>_<column>_check)을 그대로 쓰고
-- drop constraint if exists → add constraint <같은 이름>으로 붙인다(다음 migration이 또 찾을 수 있게).
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

-- LESSON_REEXPLAIN은 아무것도 저장하지 않지만(ADR-047) 호출 비용은 다른 operation과 똑같이
-- ai_call_log에 남는다. 그래서 이 값이 CHECK에 있어야 한다.
alter table ai_call_log drop constraint if exists ai_call_log_operation_check;
alter table ai_call_log add constraint ai_call_log_operation_check
    check (operation in (
        'COACH_REVIEW','COACH_RESPONSE_FEEDBACK','CHALLENGE_GENERATE','CHALLENGE_EVALUATE',
        'HINT_GENERATE','REVIEW_VARIANT','REVIEW_EVALUATE','EVIDENCE_DRAFT','REQUIREMENT_EXTRACT',
        'RUBBER_DUCK','RUBBER_DUCK_SUMMARY','LESSON_REEXPLAIN'));
