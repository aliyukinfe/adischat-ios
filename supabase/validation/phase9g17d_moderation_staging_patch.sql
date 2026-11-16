-- Phase 9G.17d staging-only surgical proposal; not executed remotely.
begin;
create or replace function public.adishop_qa_review_product(
  p_session_jti text,p_product_id uuid,p_expected_updated_at timestamptz,
  p_decision text,p_public_title text default null,p_public_short_description text default null)
returns jsonb language plpgsql volatile security definer set search_path='' as $$
declare actor uuid; target record; owner_id uuid; target_store_id uuid;
begin
  actor:=shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles role on role.id=ur.role_id
    where ur.profile_id=actor and ur.revoked_at is null and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN')
    for share of ur;
  if not found then raise exception 'review-forbidden' using errcode='42501'; end if;
  if p_decision not in ('approve','reject') or p_product_id is null or p_expected_updated_at is null then
    raise exception 'invalid-review-input' using errcode='22023'; end if;
  select store_id into target_store_id from public.shop_products where id=p_product_id;
  select owner_profile_id into owner_id from public.shop_stores where id=target_store_id for share;
  select id,store_id,updated_at,price_minor,short_description into target
    from public.shop_products where id=p_product_id and store_id=target_store_id for update;
  if target.id is null then raise exception 'product-not-found' using errcode='22023'; end if;
  if owner_id=actor then raise exception 'self-review-forbidden' using errcode='42501'; end if;
  if target.updated_at is distinct from p_expected_updated_at then
    raise exception 'stale-revision' using errcode='40001'; end if;
  if exists(select 1 from public.shop_audit_logs audit
    where audit.actor_profile_id=actor and audit.action='qa.review.'||p_decision
      and audit.entity_id=target.id
      and audit.metadata @> jsonb_build_object('expectedUpdatedAt',p_expected_updated_at)) then
    raise exception 'duplicate-review' using errcode='40001'; end if;
  if p_decision='approve' then
    if length(btrim(coalesce(p_public_title,''))) not between 2 and 160 then
      raise exception 'invalid-public-title' using errcode='22023'; end if;
    insert into public.shop_qa_reviewed_catalog(product_id,product_updated_at,store_updated_at,
      reviewed_by_profile_id,title,short_description,price_minor)
      select target.id,target.updated_at,store.updated_at,actor,
        btrim(p_public_title),nullif(btrim(p_public_short_description),''),target.price_minor
        from public.shop_stores store where store.id=target.store_id
      on conflict(product_id) do update set product_updated_at=excluded.product_updated_at,
        store_updated_at=excluded.store_updated_at,reviewed_by_profile_id=excluded.reviewed_by_profile_id,
        title=excluded.title,short_description=excluded.short_description,price_minor=excluded.price_minor,
        reviewed_at=now();
  else delete from public.shop_qa_reviewed_catalog where product_id=target.id; end if;
  insert into public.shop_audit_logs(actor_profile_id,action,entity_type,entity_id,metadata)
    values(actor,'qa.review.'||p_decision,'shop_product',target.id,
      jsonb_build_object('expectedUpdatedAt',p_expected_updated_at));
  return jsonb_build_object('productId',target.id,'decision',p_decision,'revision',target.updated_at);
end $$;
revoke all on function public.adishop_qa_review_product(text,uuid,timestamptz,text,text,text)
  from public,anon,authenticated;
grant execute on function public.adishop_qa_review_product(text,uuid,timestamptz,text,text,text) to service_role;

create or replace function public.adishop_qa_review_store(
  p_session_jti text,p_store_id uuid,p_expected_updated_at timestamptz,
  p_decision text,p_public_name text default null,p_public_description text default null,
  p_public_city text default null)
returns jsonb language plpgsql volatile security definer set search_path='' as $$
declare actor uuid; target record;
begin
  actor:=shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles role on role.id=ur.role_id
    where ur.profile_id=actor and ur.revoked_at is null
      and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN') for share of ur;
  if not found then raise exception 'review-forbidden' using errcode='42501'; end if;
  if p_store_id is null or p_expected_updated_at is null or
     p_decision not in ('approve','reject','revoke') then
    raise exception 'invalid-review-input' using errcode='22023'; end if;
  select id,owner_profile_id,status,approval_status,deleted_at,updated_at into target
    from public.shop_stores where id=p_store_id for update;
  if target.id is null then raise exception 'store-not-found' using errcode='22023'; end if;
  if target.owner_profile_id=actor then
    raise exception 'self-review-forbidden' using errcode='42501'; end if;
  if target.updated_at is distinct from p_expected_updated_at then
    raise exception 'stale-revision' using errcode='40001'; end if;
  if exists(select 1 from public.shop_audit_logs audit
    where audit.actor_profile_id=actor and audit.action='qa.store.'||p_decision
      and audit.entity_id=target.id
      and audit.metadata @> jsonb_build_object('expectedUpdatedAt',p_expected_updated_at)) then
    raise exception 'duplicate-review' using errcode='40001'; end if;
  if p_decision='approve' then
    if target.status<>'active' or target.approval_status<>'approved' or
       target.deleted_at is not null then
      raise exception 'store-unavailable' using errcode='22023'; end if;
    if length(btrim(coalesce(p_public_name,''))) not between 2 and 160 or
       length(coalesce(p_public_description,''))>500 or
       length(btrim(coalesce(p_public_city,''))) not between 1 and 120 then
      raise exception 'invalid-public-store-content' using errcode='22023'; end if;
    insert into public.shop_qa_reviewed_stores(store_id,store_updated_at,
      reviewed_by_profile_id,name,description,city)
      values(target.id,target.updated_at,actor,btrim(p_public_name),
        nullif(btrim(p_public_description),''),btrim(p_public_city))
      on conflict(store_id) do update set store_updated_at=excluded.store_updated_at,
        reviewed_by_profile_id=excluded.reviewed_by_profile_id,
        name=excluded.name,description=excluded.description,city=excluded.city,reviewed_at=now();
  else
    delete from public.shop_qa_reviewed_stores where store_id=target.id;
  end if;
  insert into public.shop_audit_logs(actor_profile_id,action,entity_type,entity_id,metadata)
    values(actor,'qa.store.'||p_decision,'shop_store',target.id,
      jsonb_build_object('expectedUpdatedAt',p_expected_updated_at));
  return jsonb_build_object('storeId',target.id,'decision',p_decision,
    'revision',target.updated_at);
end $$;
revoke all on function public.adishop_qa_review_store(text,uuid,timestamptz,text,text,text,text)
  from public,anon,authenticated;
grant execute on function public.adishop_qa_review_store(text,uuid,timestamptz,text,text,text,text)
  to service_role;

create or replace function public.adishop_qa_seller_product_edit(
  p_session_jti text,p_store_id uuid,p_product_id uuid,p_expected_updated_at timestamptz,
  p_action text,p_title text,p_price_minor integer,p_variant_id uuid default null)
returns jsonb language plpgsql volatile security definer set search_path='' as $$
declare actor uuid; product_row record; variant_row record; updated_revision timestamptz;
  owner_id uuid;
begin
  actor:=shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles role on role.id=ur.role_id
    where ur.profile_id=actor and ur.revoked_at is null and role.code='SELLER' for share of ur;
  if not found then raise exception 'seller-forbidden' using errcode='42501'; end if;
  select store.owner_profile_id into owner_id from public.shop_stores store where store.id=p_store_id
    and store.status='active' and store.approval_status='approved' for share;
  if not found then raise exception 'store-forbidden' using errcode='42501'; end if;
  if owner_id is distinct from actor then
    perform 1 from public.shop_store_members member where member.store_id=p_store_id
      and member.profile_id=actor and member.status='active'
      and member.role in ('owner','admin','manager') for share;
    if not found then raise exception 'store-forbidden' using errcode='42501'; end if;
  end if;
  if p_action not in ('edit_product','edit_variant') or
     length(btrim(coalesce(p_title,''))) not between 2 and 160 or
     p_price_minor is null or p_price_minor<0 or p_expected_updated_at is null then
    raise exception 'invalid-seller-edit' using errcode='22023'; end if;
  select id,updated_at into product_row from public.shop_products
    where id=p_product_id and store_id=p_store_id and deleted_at is null for update;
  if product_row.id is null then raise exception 'product-not-found' using errcode='22023'; end if;
  if p_action='edit_product' then
    if product_row.updated_at is distinct from p_expected_updated_at then
      raise exception 'stale-revision' using errcode='40001'; end if;
    update public.shop_products set title=btrim(p_title),price_minor=p_price_minor
      where id=p_product_id returning updated_at into updated_revision;
  else
    if p_variant_id is null then raise exception 'variant-required' using errcode='22023'; end if;
    select id,updated_at into variant_row from public.shop_product_variants
      where id=p_variant_id and product_id=p_product_id for update;
    if variant_row.id is null then raise exception 'variant-not-found' using errcode='22023'; end if;
    if variant_row.updated_at is distinct from p_expected_updated_at then
      raise exception 'stale-revision' using errcode='40001'; end if;
    update public.shop_product_variants set title=btrim(p_title),price_minor=p_price_minor,
      updated_at=clock_timestamp() where id=p_variant_id returning updated_at into updated_revision;
  end if;
  insert into public.shop_audit_logs(actor_profile_id,action,entity_type,entity_id,metadata)
    values(actor,'qa.seller.'||p_action,case when p_variant_id is null then 'shop_product' else 'shop_product_variant' end,
      coalesce(p_variant_id,p_product_id),jsonb_build_object('storeId',p_store_id));
  return jsonb_build_object('productId',p_product_id,'variantId',p_variant_id,'revision',updated_revision);
end $$;
revoke all on function public.adishop_qa_seller_product_edit(text,uuid,uuid,timestamptz,text,text,integer,uuid)
  from public,anon,authenticated;
grant execute on function public.adishop_qa_seller_product_edit(text,uuid,uuid,timestamptz,text,text,integer,uuid)
  to service_role;

create or replace function public.adishop_qa_review_image(
  p_session_jti text,p_image_id uuid,p_expected_revision integer,
  p_decision text,p_alt_text text default null)
returns jsonb language plpgsql volatile security definer set search_path='' as $$
declare actor uuid; target_product uuid; target_store uuid; owner_id uuid; image_row record;
begin
  actor:=shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles role on role.id=ur.role_id
    where ur.profile_id=actor and ur.revoked_at is null and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN')
    for share of ur;
  if not found then raise exception 'review-forbidden' using errcode='42501'; end if;
  if p_decision not in ('approve','reject') or p_expected_revision is null or p_expected_revision<1 then
    raise exception 'invalid-review-input' using errcode='22023'; end if;
  select product_id into target_product from public.shop_product_images where id=p_image_id;
  select store_id into target_store from public.shop_products where id=target_product;
  select owner_profile_id into owner_id from public.shop_stores where id=target_store for share;
  if owner_id=actor then raise exception 'self-review-forbidden' using errcode='42501'; end if;
  perform 1 from public.shop_products where id=target_product and store_id=target_store for update;
  select id,product_id,revision,storage_path,is_primary,display_order into image_row
    from public.shop_product_images where id=p_image_id and product_id=target_product for update;
  if image_row.id is null then raise exception 'image-not-found' using errcode='22023'; end if;
  if image_row.revision<>p_expected_revision then raise exception 'stale-revision' using errcode='40001'; end if;
  if p_decision='approve' then
    raise exception 'visual-preview-required' using errcode='42501';
  end if;
  if exists(select 1 from public.shop_audit_logs audit
    where audit.actor_profile_id=actor and audit.action='qa.media.'||p_decision
      and audit.entity_id=image_row.id
      and audit.metadata @> jsonb_build_object('revision',p_expected_revision)) then
    raise exception 'duplicate-review' using errcode='40001'; end if;
  if p_decision='approve' then
    if image_row.storage_path is null or
       split_part(image_row.storage_path,'/',1)<>'seller-products' or
       split_part(image_row.storage_path,'/',3)<>target_store::text or
       split_part(image_row.storage_path,'/',4)<>target_product::text or
       split_part(image_row.storage_path,'/',5)='' or
       image_row.storage_path ~ '(\\|%|(^|/)\.\.?(/|$)|[[:cntrl:]])' then
      raise exception 'invalid-private-media-path' using errcode='22023'; end if;
    insert into public.shop_qa_reviewed_images(image_id,image_revision,reviewed_by_profile_id,
      alt_text,is_primary,display_order)
      values(image_row.id,image_row.revision,actor,nullif(btrim(p_alt_text),''),
        image_row.is_primary,image_row.display_order)
      on conflict(image_id) do update set image_revision=excluded.image_revision,
        reviewed_by_profile_id=excluded.reviewed_by_profile_id,reviewed_at=now(),
        alt_text=excluded.alt_text,is_primary=excluded.is_primary,display_order=excluded.display_order;
  else delete from public.shop_qa_reviewed_images where image_id=p_image_id; end if;
  insert into public.shop_audit_logs(actor_profile_id,action,entity_type,entity_id,metadata)
    values(actor,'qa.media.'||p_decision,'shop_product_image',p_image_id,
      jsonb_build_object('revision',p_expected_revision));
  return jsonb_build_object('imageId',p_image_id,'decision',p_decision,'revision',p_expected_revision);
end $$;
revoke all on function public.adishop_qa_review_image(text,uuid,integer,text,text)
  from public,anon,authenticated;
grant execute on function public.adishop_qa_review_image(text,uuid,integer,text,text) to service_role;
commit;

