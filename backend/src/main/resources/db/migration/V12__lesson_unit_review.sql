-- V12__lesson_unit_review.sql
-- 단계: 학습 단위를 도움받아 풀면 복습 카드가 생긴다 — docs/04 §3, docs/06 §6.3, 재설계안 R-0 6번
-- V1~V11은 고치지 않는다(ADR-038). 이 파일은 alter만 쓴다.
-- 인라인 CHECK를 다시 만들 때는 PostgreSQL 자동 이름(<table>_<column>_check)을 그대로 쓰고
-- drop constraint if exists → add constraint <같은 이름>으로 붙인다(다음 migration이 또 찾을 수 있게).
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

-- 지금까지 복습 카드가 생기는 자리는 러버덕·challenge·재현 과제뿐이었다. 개념 노트의 학습 단위를
-- 힌트나 모범 답안을 보고 푼 경우에도 카드가 생겨야 "도움 받고 풀면 다시 돌아온다"가 성립한다.
-- source_id는 두지 않는다 — 단위를 가리키는 것은 UUID가 아니라 key 문자열이고, 그 값은 concept_key에 담는다.
alter table review_item drop constraint if exists review_item_source_type_check;
alter table review_item add constraint review_item_source_type_check
    check (source_type in (
        'SEED_CARD','MANUAL','CHALLENGE_ATTEMPT','COACH_FINDING','EVIDENCE','RUBBER_DUCK',
        'REDO_TASK','TERM','TIP','LESSON_UNIT'));
