-- Disposable PostgreSQL fixture only. State changes roll back.
begin;
create function pg_temp.qa_assert(ok boolean, label text) returns void
language plpgsql as $$ begin
  if not coalesce(ok, false) then raise exception 'FAIL %', label; end if;
  raise notice 'PASS %', label;
end $$;
insert into public.shop_session_families(id, profile_id)
values ('98000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000001');
insert into public.shop_sessions(profile_id,family_id,session_jti,refresh_token_hash,expires_at,refresh_expires_at)
values ('90000000-0000-4000-8000-000000000001','98000000-0000-4000-8000-000000000001',
  'qa-g11-active','qa-g11-hash-1',now()+interval '1 hour',now()+interval '1 day');
select pg_temp.qa_assert(shop_qa_private.active_read_scope('qa-g11-active') is not null, 'active linked session');
select pg_temp.qa_assert(shop_qa_private.active_read_scope('qa-g11-missing') is null, 'unknown session denied');
select pg_temp.qa_assert(not has_function_privilege('anon','shop_qa_private.active_read_scope(text)','EXECUTE'), 'anon execute denied');
select pg_temp.qa_assert(not has_function_privilege('authenticated','shop_qa_private.active_read_scope(text)','EXECUTE'), 'client execute denied');
select pg_temp.qa_assert(has_function_privilege('service_role','shop_qa_private.active_read_scope(text)','EXECUTE'), 'service execute allowed');
savepoint revoked_session;
update public.shop_sessions set revoked_at=now() where session_jti='qa-g11-active';
select pg_temp.qa_assert(shop_qa_private.active_read_scope('qa-g11-active') is null, 'revoked session denied');
rollback to revoked_session;
savepoint revoked_family;
update public.shop_session_families set revoked_at=now() where id='98000000-0000-4000-8000-000000000001';
select pg_temp.qa_assert(shop_qa_private.active_read_scope('qa-g11-active') is null, 'revoked family denied');
rollback to revoked_family;
savepoint expired_session;
update public.shop_sessions set expires_at=now()-interval '1 second' where session_jti='qa-g11-active';
select pg_temp.qa_assert(shop_qa_private.active_read_scope('qa-g11-active') is null, 'expired session denied');
rollback to expired_session;
savepoint suspended_link;
update public.shop_user_links set account_status='suspended'
where shop_profile_id='90000000-0000-4000-8000-000000000001';
select pg_temp.qa_assert(shop_qa_private.active_read_scope('qa-g11-active') is null, 'suspended link denied');
rollback to suspended_link;
savepoint suspended_profile;
update public.shop_profiles set status='suspended' where id='90000000-0000-4000-8000-000000000001';
select pg_temp.qa_assert(shop_qa_private.active_read_scope('qa-g11-active') is null, 'suspended profile denied');
rollback to suspended_profile;
rollback;
