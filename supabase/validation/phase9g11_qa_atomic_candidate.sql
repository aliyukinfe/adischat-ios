-- LOCAL QA CANDIDATE ONLY. Service-role RPCs require a verified Shop JWT at Edge.
begin;
create unique index if not exists shop_qa_one_active_cart
  on public.shop_carts(profile_id,currency) where status='active';
create unique index if not exists shop_qa_one_cart_line
  on public.shop_cart_items(cart_id,product_id,coalesce(variant_id,'00000000-0000-0000-0000-000000000000'::uuid));

create or replace function shop_qa_private.locked_actor(p_session_jti text)
returns uuid language plpgsql volatile security definer set search_path='' as $$
declare actor uuid; state text; v_family_id uuid; family_revoked timestamptz;
  session_revoked timestamptz; session_expires timestamptz; link record; link_count integer:=0;
begin
  select p.id,p.status into actor,state from public.shop_sessions sess
    join public.shop_profiles p on p.id=sess.profile_id
    where sess.session_jti=p_session_jti for share of p;
  if actor is null or state<>'active' then raise exception 'invalid-session' using errcode='28000'; end if;
  for link in select account_status from public.shop_user_links
      where shop_profile_id=actor for share loop
    link_count:=link_count+1;
    if link.account_status<>'active' then raise exception 'invalid-session' using errcode='28000'; end if;
  end loop;
  if link_count=0 then raise exception 'invalid-session' using errcode='28000'; end if;
  select family_id into v_family_id from public.shop_sessions where session_jti=p_session_jti;
  select revoked_at into family_revoked from public.shop_session_families
    where id=v_family_id and profile_id=actor for share;
  if not found then raise exception 'invalid-session' using errcode='28000'; end if;
  select revoked_at,expires_at into session_revoked,session_expires from public.shop_sessions
    where session_jti=p_session_jti and family_id=v_family_id and profile_id=actor for share;
  if not found or family_revoked is not null or session_revoked is not null or
     session_expires<=now() then raise exception 'invalid-session' using errcode='28000'; end if;
  return actor;
end $$;
revoke all on function shop_qa_private.locked_actor(text) from public,anon,authenticated;
grant execute on function shop_qa_private.locked_actor(text) to service_role;

create or replace function public.adishop_qa_cart_action(
  p_session_jti text,p_action text,p_product_id uuid default null,
  p_variant_id uuid default null,p_item_id uuid default null,p_quantity integer default null)
returns jsonb language plpgsql volatile security definer set search_path='' as $$
declare actor uuid; v_cart_id uuid; line record; unit_price integer; available integer;
  current_quantity integer; variants_exist boolean; result jsonb;
begin
  actor:=shop_qa_private.locked_actor(p_session_jti);
  if p_action not in ('read','add','update','remove') then
    raise exception 'invalid-cart-action' using errcode='22023'; end if;
  select id into v_cart_id from public.shop_carts
    where profile_id=actor and status='active' and currency='ETB' for update;
  if p_action='add' then
    if p_product_id is null or p_quantity not between 1 and 99 then
      raise exception 'invalid-cart-input' using errcode='22023'; end if;
    select price_minor into unit_price from public.shop_qa_public_catalog where id=p_product_id;
    if unit_price is null then raise exception 'product-unavailable' using errcode='22023'; end if;
    select exists(select 1 from public.shop_product_variants
      where product_id=p_product_id and status='active') into variants_exist;
    if variants_exist and p_variant_id is null then
      raise exception 'variant-required' using errcode='22023'; end if;
    if p_variant_id is not null then
      select reviewed.price_minor into unit_price
        from public.shop_product_variants variant
        join public.shop_qa_reviewed_variants reviewed on reviewed.variant_id=variant.id
          and reviewed.variant_updated_at=variant.updated_at
        where variant.id=p_variant_id and variant.product_id=p_product_id and variant.status='active'
          and public.adishop_qa_variant_reviewed(variant.id,
            (select store_id from public.shop_products where id=p_product_id));
      if unit_price is null then raise exception 'variant-unavailable' using errcode='22023'; end if;
    end if;
    if v_cart_id is null then
      insert into public.shop_carts(profile_id,status,currency) values(actor,'active','ETB') returning id into v_cart_id;
    end if;
    select id,quantity into line from public.shop_cart_items
      where cart_id=v_cart_id and product_id=p_product_id
        and variant_id is not distinct from p_variant_id for update;
    current_quantity:=coalesce(line.quantity,0)+p_quantity;
  elsif p_action='update' or p_action='remove' then
    if v_cart_id is null or p_item_id is null then raise exception 'cart-item-not-found' using errcode='22023'; end if;
    select id,product_id,variant_id,quantity into line from public.shop_cart_items
      where id=p_item_id and cart_id=v_cart_id for update;
    if line.id is null then raise exception 'cart-item-not-found' using errcode='22023'; end if;
    if p_action='update' then
      if p_quantity not between 1 and 99 then raise exception 'invalid-quantity' using errcode='22023'; end if;
      current_quantity:=p_quantity; p_product_id:=line.product_id; p_variant_id:=line.variant_id;
    end if;
  end if;
  if p_action in ('add','update') then
    select quantity_available-quantity_reserved into available from public.shop_inventory
      where product_id=p_product_id and variant_id is not distinct from p_variant_id for share;
    if available is null or current_quantity>available then
      raise exception 'insufficient-inventory' using errcode='22023'; end if;
    if p_action='add' then
      if line.id is null then
        insert into public.shop_cart_items(cart_id,product_id,variant_id,quantity,unit_price_minor)
          values(v_cart_id,p_product_id,p_variant_id,current_quantity,unit_price);
      else update public.shop_cart_items set quantity=current_quantity,unit_price_minor=unit_price,
        updated_at=now() where id=line.id; end if;
    else update public.shop_cart_items set quantity=current_quantity,updated_at=now() where id=line.id; end if;
  elsif p_action='remove' then delete from public.shop_cart_items where id=line.id; end if;
  select jsonb_build_object('id',v_cart_id,'items',coalesce(jsonb_agg(jsonb_build_object(
    'id',item.id,'product_id',item.product_id,'variant_id',item.variant_id,
    'quantity',item.quantity,'unit_price_minor',item.unit_price_minor)
    order by item.created_at) filter (where item.id is not null),'[]'::jsonb)) into result
    from public.shop_cart_items item where item.cart_id=v_cart_id;
  return result;
end $$;
revoke all on function public.adishop_qa_cart_action(text,text,uuid,uuid,uuid,integer)
  from public,anon,authenticated;
grant execute on function public.adishop_qa_cart_action(text,text,uuid,uuid,uuid,integer) to service_role;

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
commit;
