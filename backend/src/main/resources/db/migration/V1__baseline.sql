-- V1__baseline.sql
-- Sprint: S0 · devpilot 스키마와 Supabase hardening
-- 생성 기준: database/schema.sql (v2). 적용된 migration은 수정하지 않는다.
-- Flyway 설정: spring.flyway.schemas=devpilot, default-schema=devpilot (search_path = devpilot)

create schema if not exists devpilot;

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        execute 'revoke all on schema devpilot from anon';
        execute 'revoke all on all tables in schema devpilot from anon';
        execute 'alter default privileges in schema devpilot revoke all on tables from anon';
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        execute 'revoke all on schema devpilot from authenticated';
        execute 'revoke all on all tables in schema devpilot from authenticated';
        execute 'alter default privileges in schema devpilot revoke all on tables from authenticated';
    end if;
end
$$;
