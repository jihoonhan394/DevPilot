-- V15__review_item_graduated.sql
-- 단계: 복습 카드 졸업 — docs/04 §3·§4.5, docs/06 §6.4, ADR-074
-- V1~V14는 고치지 않는다(ADR-038). 이 파일은 alter만 쓴다.
-- 인라인 CHECK를 다시 만들 때는 PostgreSQL 자동 이름(<table>_<column>_check)을 그대로 쓰고
-- drop constraint if exists → add constraint <같은 이름>으로 붙인다(다음 migration이 또 찾을 수 있게).
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

-- 카드가 빠져나가는 길이 사용자가 치우는 ARCHIVED뿐이라, 굳은 카드가 60일마다 계속 돌아왔다.
-- 긴 간격을 혼자 넘긴 카드를 GRADUATED로 옮겨 due에서 뺀다 — 테이블·컬럼은 늘리지 않는다.
alter table review_item drop constraint if exists review_item_status_check;
alter table review_item add constraint review_item_status_check
    check (status in ('ACTIVE','SUSPENDED','GRADUATED','ARCHIVED'));
