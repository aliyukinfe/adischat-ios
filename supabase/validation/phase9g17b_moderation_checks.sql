-- Disposable synthetic fixture only; all changes roll back.
begin;
create temp table qa_results(label text primary key);
create function pg_temp.qa_check(label text, condition boolean) returns void
language plpgsql as $$ begin
  if condition is distinct from true then raise exception 'FAIL %',label; end if;
  insert into qa_results values(label);
end $$;

-- Existing marketplace moderation status is fixture state, not reviewed-content approval.
update public.shop_products set status='active',moderation_status='approved'
  where id='93000000-0000-4000-8000-000000000001';
insert into public.shop_stores(id,owner_profile_id,name,slug,status,approval_status,city)
  values('92000000-0000-4000-8000-000000000003',
    '90000000-0000-4000-8000-000000000003','Admin-owned QA Store',
    'admin-owned-qa-store','active','approved','QA City');
insert into public.shop_product_images(id,product_id,image_url,storage_path)
  values('98000000-0000-4000-8000-000000000001',
    '93000000-0000-4000-8000-000000000001','private-only',
    'seller-products/qa/92000000-0000-4000-8000-000000000001/93000000-0000-4000-8000-000000000001/image.jpg');

select pg_temp.qa_check('unreviewed store hidden',not exists(
  select 1 from public.shop_qa_public_stores where id='92000000-0000-4000-8000-000000000001'));
do $$ declare revision timestamptz; begin
  select updated_at into revision from public.shop_stores
    where id='92000000-0000-4000-8000-000000000001';
  perform public.adishop_qa_review_store('qa-g11-3',
    '92000000-0000-4000-8000-000000000001',revision,'approve',
    'Reviewed QA Store','Synthetic only','QA City');
end $$;
select pg_temp.qa_check('authorized store approval visible',exists(
  select 1 from public.shop_qa_public_stores where id='92000000-0000-4000-8000-000000000001'));
select pg_temp.qa_check('store audit UUID',exists(select 1 from public.shop_audit_logs
  where entity_id='92000000-0000-4000-8000-000000000001' and action='qa.store.approve'));
do $$ declare revision timestamptz; begin
  select updated_at into revision from public.shop_stores
    where id='92000000-0000-4000-8000-000000000001';
  begin
    perform public.adishop_qa_review_store('qa-g11-3',
      '92000000-0000-4000-8000-000000000001',revision,'approve',
      'Reviewed QA Store','Synthetic only','QA City');
    raise exception 'duplicate store review succeeded';
  exception when serialization_failure then insert into qa_results values('duplicate store review denied'); end;
end $$;

do $$ declare revision timestamptz; attempt text; expected text;
begin
  select updated_at into revision from public.shop_stores
    where id='92000000-0000-4000-8000-000000000001';
  foreach attempt in array array['qa-g11-4','qa-g11-1','qa-g11-2','invalid-jti'] loop
    begin
      perform public.adishop_qa_review_store(attempt,
        '92000000-0000-4000-8000-000000000001',revision,'approve',
        'Reviewed QA Store',null,'QA City');
      raise exception 'unexpected authorization success %',attempt;
    exception when insufficient_privilege or invalid_authorization_specification then
      insert into qa_results values('denied '||attempt);
    end;
  end loop;
  begin
    perform public.adishop_qa_review_store('qa-g11-3',
      '92000000-0000-4000-8000-000000000003',
      (select updated_at from public.shop_stores where id='92000000-0000-4000-8000-000000000003'),
      'approve','Owner Store',null,'QA City');
    raise exception 'owner self-review unexpectedly succeeded';
  exception when insufficient_privilege then insert into qa_results values('owner self-review denied'); end;
  begin
    perform public.adishop_qa_review_store('qa-g11-3',
      '92000000-0000-4000-8000-000000000001',revision-interval '1 second',
      'approve','Stale Store',null,'QA City');
    raise exception 'stale review unexpectedly succeeded';
  exception when serialization_failure then insert into qa_results values('stale store revision denied'); end;
  begin
    perform public.adishop_qa_review_store('qa-g11-3',
      '92000000-0000-4000-8000-000000000001',revision,'unknown',
      'Invalid Decision',null,'QA City');
    raise exception 'invalid decision unexpectedly succeeded';
  exception when invalid_parameter_value then insert into qa_results values('invalid decision denied'); end;
end $$;
select pg_temp.qa_check('failed store reviews no audit',
  (select count(*) from public.shop_audit_logs where entity_id='92000000-0000-4000-8000-000000000001')=1);
select pg_temp.qa_check('failed store reviews leave projection',
  (select count(*) from public.shop_qa_reviewed_stores where store_id='92000000-0000-4000-8000-000000000001')=1);

do $$ begin
  begin
    perform public.adishop_qa_review_image('qa-g11-3',
      '98000000-0000-4000-8000-000000000001',1,'approve','Synthetic laptop');
    raise exception 'image approval unexpectedly succeeded';
  exception when insufficient_privilege then insert into qa_results values('image approval denied without visual preview'); end;
end $$;
select pg_temp.qa_check('image approval has no audit or projection',
  not exists(select 1 from public.shop_audit_logs
    where entity_id='98000000-0000-4000-8000-000000000001' and action='qa.media.approve')
  and not exists(select 1 from public.shop_qa_reviewed_images
    where image_id='98000000-0000-4000-8000-000000000001'));
select public.adishop_qa_review_image('qa-g11-3',
  '98000000-0000-4000-8000-000000000001',1,'reject');
select pg_temp.qa_check('image rejection audit UUID',exists(select 1 from public.shop_audit_logs
  where entity_id='98000000-0000-4000-8000-000000000001' and action='qa.media.reject'));
do $$ begin
  begin
    perform public.adishop_qa_review_image('qa-g11-3',
      '98000000-0000-4000-8000-000000000001',2,'reject');
    raise exception 'stale image rejection succeeded';
  exception when serialization_failure then insert into qa_results values('stale image revision denied'); end;
end $$;
do $$ begin
  begin
    perform public.adishop_qa_review_image('qa-g11-3',
      '98000000-0000-4000-8000-000000000001',1,'reject');
    raise exception 'duplicate image rejection succeeded';
  exception when serialization_failure then insert into qa_results values('duplicate image review denied'); end;
end $$;
do $$ declare revision timestamptz; begin
  select updated_at into revision from public.shop_products
    where id='93000000-0000-4000-8000-000000000001';
  perform public.adishop_qa_review_product('qa-g11-3',
    '93000000-0000-4000-8000-000000000001',revision,'approve',
    'Reviewed QA Laptop','Synthetic only');
end $$;
select pg_temp.qa_check('product approval audit UUID',exists(select 1 from public.shop_audit_logs
  where entity_id='93000000-0000-4000-8000-000000000001' and action='qa.review.approve'));
do $$ declare revision timestamptz; begin
  select updated_at into revision from public.shop_products
    where id='93000000-0000-4000-8000-000000000001';
  begin
    perform public.adishop_qa_review_product('qa-g11-3',
      '93000000-0000-4000-8000-000000000001',revision,'approve',
      'Reviewed QA Laptop','Synthetic only');
    raise exception 'duplicate product review succeeded';
  exception when serialization_failure then insert into qa_results values('duplicate product review denied'); end;
end $$;
select pg_temp.qa_check('store and product visible together',exists(select 1
  from public.shop_qa_public_catalog where id='93000000-0000-4000-8000-000000000001'));
do $$ declare revision timestamptz; begin
  select updated_at into revision from public.shop_products
    where id='93000000-0000-4000-8000-000000000001';
  perform public.adishop_qa_seller_product_edit('qa-g11-1',
    '92000000-0000-4000-8000-000000000001',
    '93000000-0000-4000-8000-000000000001',revision,
    'edit_product','Updated Synthetic Laptop',5600000);
end $$;
select pg_temp.qa_check('seller edit audit UUID',exists(select 1 from public.shop_audit_logs
  where entity_id='93000000-0000-4000-8000-000000000001' and action='qa.seller.edit_product'));
select pg_temp.qa_check('seller edit invalidates product visibility',not exists(select 1
  from public.shop_qa_public_catalog where id='93000000-0000-4000-8000-000000000001'));
do $$ declare revision timestamptz; begin
  select updated_at into revision from public.shop_stores
    where id='92000000-0000-4000-8000-000000000001';
  perform public.adishop_qa_review_store('qa-g11-3',
    '92000000-0000-4000-8000-000000000001',revision,'reject');
end $$;
select pg_temp.qa_check('rejection invalidates store projection',not exists(select 1
  from public.shop_qa_reviewed_stores where store_id='92000000-0000-4000-8000-000000000001'));
select pg_temp.qa_check('anonymous cannot execute store review',not has_function_privilege(
  'anon','public.adishop_qa_review_store(text,uuid,timestamptz,text,text,text,text)','EXECUTE'));
select pg_temp.qa_check('authenticated cannot execute store review',not has_function_privilege(
  'authenticated','public.adishop_qa_review_store(text,uuid,timestamptz,text,text,text,text)','EXECUTE'));
select pg_temp.qa_check('service role can execute store review',has_function_privilege(
  'service_role','public.adishop_qa_review_store(text,uuid,timestamptz,text,text,text,text)','EXECUTE'));
select pg_temp.qa_check('private Storage buckets unchanged',
  (select count(*) from storage.buckets where public)=0);
select count(*) as passed from qa_results;
rollback;
