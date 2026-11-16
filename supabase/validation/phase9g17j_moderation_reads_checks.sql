-- Disposable PostgreSQL 17 fixture only. All test mutations roll back.
begin;
create temp table qa_results(label text primary key);
create function pg_temp.qa_check(label text, condition boolean) returns void language plpgsql as $$
begin
  if condition is distinct from true then raise exception 'FAIL %',label; end if;
  insert into qa_results values(label);
end $$;

insert into public.shop_product_images(id,product_id,image_url,alt_text)
values ('98000000-0000-4000-8000-000000000001',
  '93000000-0000-4000-8000-000000000001','private-only','Synthetic image');

select pg_temp.qa_check('admin store queue',
  (public.adishop_qa_moderation_queue('qa-g11-3','store',20,0)->>'total')::integer=2);
select pg_temp.qa_check('admin product queue',
  (public.adishop_qa_moderation_queue('qa-g11-3','product',20,0)->>'total')::integer=1);
select pg_temp.qa_check('admin image queue',
  (public.adishop_qa_moderation_queue('qa-g11-3','image',20,0)->>'total')::integer=1);
select pg_temp.qa_check('filter excludes other kinds',
  (public.adishop_qa_moderation_queue('qa-g11-3','image',20,0)->'items'->0->>'entityKind')='image');
select pg_temp.qa_check('stable first page',
  (public.adishop_qa_moderation_queue('qa-g11-3','store',1,0)->'items'->0->>'entityId')=
  '92000000-0000-4000-8000-000000000001');
select pg_temp.qa_check('stable second page',
  (public.adishop_qa_moderation_queue('qa-g11-3','store',1,1)->'items'->0->>'entityId')=
  '92000000-0000-4000-8000-000000000002');
select pg_temp.qa_check('empty page',
  jsonb_array_length(public.adishop_qa_moderation_queue('qa-g11-3','store',20,20)->'items')=0);
select pg_temp.qa_check('store timestamp revision',
  jsonb_typeof(public.adishop_qa_moderation_preview('qa-g11-3','store',
    '92000000-0000-4000-8000-000000000001')->'expectedRevision')='string');
select pg_temp.qa_check('product timestamp revision',
  jsonb_typeof(public.adishop_qa_moderation_preview('qa-g11-3','product',
    '93000000-0000-4000-8000-000000000001')->'expectedRevision')='string');
select pg_temp.qa_check('image numeric revision',
  jsonb_typeof(public.adishop_qa_moderation_preview('qa-g11-3','image',
    '98000000-0000-4000-8000-000000000001')->'expectedRevision')='number');
select pg_temp.qa_check('image approval absent from preview actions',
  not (public.adishop_qa_moderation_preview('qa-g11-3','image',
    '98000000-0000-4000-8000-000000000001')->'permittedActions' ? 'approve'));
select pg_temp.qa_check('image preview does not expose path or URL',
  public.adishop_qa_moderation_preview('qa-g11-3','image',
    '98000000-0000-4000-8000-000000000001')::text !~* '(storage_path|image_url|https?://|seller-products/)');
select pg_temp.qa_check('reviewable product text',
  (public.adishop_qa_moderation_preview('qa-g11-3','product',
    '93000000-0000-4000-8000-000000000001')->'content'->>'title')='Synthetic QA Laptop');
select pg_temp.qa_check('reviewable image text',
  (public.adishop_qa_moderation_preview('qa-g11-3','image',
    '98000000-0000-4000-8000-000000000001')->'content'->>'altText')='Synthetic image');
select pg_temp.qa_check('permitted actions',
  public.adishop_qa_moderation_preview('qa-g11-3','store',
    '92000000-0000-4000-8000-000000000001')->'permittedActions' ? 'reject');
select pg_temp.qa_check('pending status',
  (public.adishop_qa_moderation_preview('qa-g11-3','store',
    '92000000-0000-4000-8000-000000000001')->>'reviewStatus')='pending');
select pg_temp.qa_check('public unreviewed hidden',
  not exists(select 1 from public.shop_qa_public_stores
    where id='92000000-0000-4000-8000-000000000001'));
select pg_temp.qa_check('service-role-only grants',
  has_function_privilege('service_role','public.adishop_qa_moderation_queue(text,text,integer,integer)','EXECUTE')
  and not has_function_privilege('anon','public.adishop_qa_moderation_queue(text,text,integer,integer)','EXECUTE')
  and not has_function_privilege('authenticated','public.adishop_qa_moderation_preview(text,text,uuid)','EXECUTE'));

do $$ begin
  begin perform public.adishop_qa_moderation_queue('qa-g11-1',null,20,0);
    raise exception 'seller unexpectedly allowed';
  exception when insufficient_privilege then null; end;
  begin perform public.adishop_qa_moderation_preview('qa-g11-4','store',
    '92000000-0000-4000-8000-000000000001');
    raise exception 'buyer unexpectedly allowed';
  exception when insufficient_privilege then null; end;
  begin perform public.adishop_qa_moderation_queue('qa-g11-2',null,20,0);
    raise exception 'suspended unexpectedly allowed';
  exception when others then if sqlerrm='suspended unexpectedly allowed' then raise; end if; end;
  begin perform public.adishop_qa_moderation_queue('missing-jti',null,20,0);
    raise exception 'missing session unexpectedly allowed';
  exception when others then if sqlerrm='missing session unexpectedly allowed' then raise; end if; end;
end $$;
select pg_temp.qa_check('seller buyer suspended and missing sessions denied',true);

insert into public.shop_stores(id,owner_profile_id,name,slug,status,approval_status,city)
values ('92000000-0000-4000-8000-000000000003',
  '90000000-0000-4000-8000-000000000003','Admin-owned','admin-owned','active','approved','QA');
select pg_temp.qa_check('owner store excluded from queue',
  (public.adishop_qa_moderation_queue('qa-g11-3','store',20,0)->>'total')::integer=2);
do $$ begin
  begin perform public.adishop_qa_moderation_preview('qa-g11-3','store',
    '92000000-0000-4000-8000-000000000003');
    raise exception 'owner preview allowed';
  exception when invalid_parameter_value then null; end;
end $$;
select pg_temp.qa_check('owner preview denied',true);

update public.shop_stores set status='active',approval_status='approved'
where id='92000000-0000-4000-8000-000000000001';
do $$ declare rev timestamptz; begin
  rev := (public.adishop_qa_moderation_preview('qa-g11-3','store',
    '92000000-0000-4000-8000-000000000001')->>'expectedRevision')::timestamptz;
  perform public.adishop_qa_review_store('qa-g11-3',
    '92000000-0000-4000-8000-000000000001',rev,'approve','Reviewed Store',null,'QA');
  begin perform public.adishop_qa_review_store('qa-g11-3',
    '92000000-0000-4000-8000-000000000001',rev,'approve','Reviewed Store',null,'QA');
    raise exception 'duplicate allowed';
  exception when serialization_failure then null; end;
  update public.shop_stores set name='Edited After Preview'
    where id='92000000-0000-4000-8000-000000000001';
  begin perform public.adishop_qa_review_store('qa-g11-3',
    '92000000-0000-4000-8000-000000000001',rev,'approve','Stale Store',null,'QA');
    raise exception 'stale allowed';
  exception when serialization_failure then null; end;
end $$;
select pg_temp.qa_check('approved then edited store hidden',
  not exists(select 1 from public.shop_qa_public_stores
    where id='92000000-0000-4000-8000-000000000001'));
select pg_temp.qa_check('audit UUID typed',
  exists(select 1 from public.shop_audit_logs where entity_id=
    '92000000-0000-4000-8000-000000000001'::uuid and action='qa.store.approve'));

select public.adishop_qa_review_store('qa-g11-3',
  '92000000-0000-4000-8000-000000000001',
  (public.adishop_qa_moderation_preview('qa-g11-3','store',
    '92000000-0000-4000-8000-000000000001')->>'expectedRevision')::timestamptz,
  'reject');
select pg_temp.qa_check('exact-revision rejected status',
  (public.adishop_qa_moderation_preview('qa-g11-3','store',
    '92000000-0000-4000-8000-000000000001')->>'reviewStatus')='rejected');
select pg_temp.qa_check('reject does not publish',
  not exists(select 1 from public.shop_qa_public_stores
    where id='92000000-0000-4000-8000-000000000001'));
update public.shop_stores set deleted_at=now()
  where id='92000000-0000-4000-8000-000000000002';
select pg_temp.qa_check('deleted store excluded from queue',
  (public.adishop_qa_moderation_queue('qa-g11-3','store',20,0)->>'total')::integer=1);
do $$ begin
  begin perform public.adishop_qa_moderation_preview('qa-g11-3','store',
    '92000000-0000-4000-8000-000000000002');
    raise exception 'deleted preview allowed';
  exception when invalid_parameter_value then null; end;
end $$;
select pg_temp.qa_check('deleted preview denied',true);
update public.shop_sessions set expires_at=now()-interval '1 second' where session_jti='qa-g11-3';
do $$ begin
  begin perform public.adishop_qa_moderation_queue('qa-g11-3',null,20,0);
    raise exception 'expired session allowed';
  exception when others then if sqlerrm='expired session allowed' then raise; end if; end;
end $$;
select pg_temp.qa_check('expired session denied',true);
update public.shop_sessions set expires_at=now()+interval '1 hour' where session_jti='qa-g11-3';
update public.shop_sessions set revoked_at=now() where session_jti='qa-g11-3';
do $$ begin
  begin perform public.adishop_qa_moderation_queue('qa-g11-3',null,20,0);
    raise exception 'revoked session allowed';
  exception when others then if sqlerrm='revoked session allowed' then raise; end if; end;
end $$;
select pg_temp.qa_check('revoked session denied',true);

select count(*) as passed from qa_results;
rollback;
