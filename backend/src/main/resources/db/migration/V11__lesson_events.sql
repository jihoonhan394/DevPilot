-- V11__lesson_events.sql
-- 단계: 개념 노트(학습 단위) — docs/04 §6·§10, docs/05 §21, docs/19 §3.14
-- V1~V10은 고치지 않는다(ADR-038). 이 파일은 alter만 쓴다.
-- 인라인 CHECK를 다시 만들 때는 PostgreSQL 자동 이름(<table>_<column>_check)을 그대로 쓰고
-- drop constraint if exists → add constraint <같은 이름>으로 붙인다(다음 migration이 또 찾을 수 있게).
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

-- 개념 노트는 콘텐츠라 본문 테이블이 없고(docs/19 §3.14), 사용자 답도 저장하지 않는다(docs/05 §21.6).
-- 남는 것은 "이 단위를 언제 어떤 도움으로 풀었는가" 하나뿐이라 이벤트 값 하나만 더한다.
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
        'UNIT_SOLVED'));
