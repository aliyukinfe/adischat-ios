-- Phase AG-R3: Secure Product Image Review and Moderation
-- Target: AdisGebeya Staging (opkqyvttbyezjbqwkvho)
-- Supabase Free Tier - $0.00 spend - Strictly untouched production

-- 1. Preview Authorizations Table
create table if not exists public.shop_qa_image_preview_authorizations (
  reviewer_profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  session_jti text not null,
  image_id uuid not null references public.shop_product_images(id) on delete cascade,
  image_revision integer not null,
  authorized_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '5 minutes'),
  primary key (reviewer_profile_id, image_id, image_revision)
);

alter table public.shop_qa_image_preview_authorizations enable row level security;
revoke all on public.shop_qa_image_preview_authorizations from public, anon, authenticated;
grant select, insert, update, delete on public.shop_qa_image_preview_authorizations to service_role;

-- 2. Moderation Queue RPC
create or replace function public.adishop_qa_moderation_queue(
  p_session_jti text, p_kind text default null, p_limit integer default 20, p_offset integer default 0)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare actor uuid; result jsonb;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles r on r.id = ur.role_id
    where ur.profile_id = actor and ur.revoked_at is null
      and r.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN') for share of ur;
  if not found then raise exception 'review-forbidden' using errcode = '42501'; end if;
  if (p_kind is not null and p_kind not in ('store','product','image')) or
     p_limit is null or p_limit not between 1 and 50 or
     p_offset is null or p_offset < 0 then
    raise exception 'invalid-review-input' using errcode = '22023'; end if;
  with items as (
    select 'store'::text kind, s.id, s.id target_id, s.updated_at revision, null::integer image_revision,
      case when rs.store_updated_at = s.updated_at then 'approved'
        when exists(select 1 from public.shop_audit_logs a where a.entity_id = s.id
          and a.action = 'qa.store.reject' and a.metadata @> jsonb_build_object('expectedUpdatedAt', s.updated_at))
          then 'rejected' else 'pending' end review_status
      from public.shop_stores s left join public.shop_qa_reviewed_stores rs on rs.store_id = s.id
      where s.deleted_at is null and s.owner_profile_id is distinct from actor
        and (p_kind is null or p_kind = 'store')
    union all
    select 'product', p.id, p.store_id, p.updated_at, null::integer,
      case when rc.product_updated_at = p.updated_at then 'approved'
        when exists(select 1 from public.shop_audit_logs a where a.entity_id = p.id
          and a.action = 'qa.review.reject' and a.metadata @> jsonb_build_object('expectedUpdatedAt', p.updated_at))
          then 'rejected' else 'pending' end
      from public.shop_products p left join public.shop_qa_reviewed_catalog rc on rc.product_id = p.id
      join public.shop_stores s on s.id = p.store_id and s.deleted_at is null
      where p.deleted_at is null and s.owner_profile_id is distinct from actor
        and (p_kind is null or p_kind = 'product')
    union all
    select 'image', i.id, p.id, null::timestamptz, i.revision,
      case when ri.image_revision = i.revision then 'approved'
        when exists(select 1 from public.shop_audit_logs a where a.entity_id = i.id
          and a.action = 'qa.media.reject' and a.metadata @> jsonb_build_object('revision', i.revision))
          then 'rejected' else 'pending' end
      from public.shop_product_images i
      join public.shop_products p on p.id = i.product_id and p.deleted_at is null
      join public.shop_stores s on s.id = p.store_id and s.deleted_at is null
      left join public.shop_qa_reviewed_images ri on ri.image_id = i.id
      where s.owner_profile_id is distinct from actor and (p_kind is null or p_kind = 'image')
  ), page as (
    select * from items order by kind, id limit p_limit offset p_offset
  )
  select jsonb_build_object('total', (select count(*) from items),
    'items', coalesce(jsonb_agg(jsonb_build_object('entityKind', kind, 'entityId', id,
      'targetId', target_id, 'reviewStatus', review_status,
      'expectedRevision', case when kind = 'image' then to_jsonb(image_revision) else to_jsonb(revision) end)
      order by kind, id), '[]'::jsonb)) into result from page;
  return result;
end $$;
revoke all on function public.adishop_qa_moderation_queue(text,text,integer,integer) from public, anon, authenticated;
grant execute on function public.adishop_qa_moderation_queue(text,text,integer,integer) to service_role;

-- 3. Moderation Preview RPC
create or replace function public.adishop_qa_moderation_preview(
  p_session_jti text, p_kind text, p_id uuid)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare actor uuid; result jsonb;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles r on r.id = ur.role_id
    where ur.profile_id = actor and ur.revoked_at is null
      and r.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN') for share of ur;
  if not found then raise exception 'review-forbidden' using errcode = '42501'; end if;
  if p_id is null or p_kind is null or p_kind not in ('store','product','image') then
    raise exception 'invalid-review-input' using errcode = '22023'; end if;

  if p_kind = 'store' then
    select jsonb_build_object('entityKind','store','entityId',s.id,'targetId',s.id,
      'expectedRevision',s.updated_at,'reviewStatus',
      case when rs.store_updated_at=s.updated_at then 'approved'
        when exists(select 1 from public.shop_audit_logs a where a.entity_id=s.id
          and a.action='qa.store.reject' and a.metadata @> jsonb_build_object('expectedUpdatedAt',s.updated_at))
          then 'rejected' else 'pending' end,
      'content',jsonb_build_object('name',s.name,'description',s.description,'city',s.city,
        'status',s.status,'approvalStatus',s.approval_status),
      'permittedActions',case when s.status='active' and s.approval_status='approved'
        then jsonb_build_array('approve','reject','revoke') else jsonb_build_array('reject','revoke') end)
      into result from public.shop_stores s left join public.shop_qa_reviewed_stores rs on rs.store_id=s.id
      where s.id=p_id and s.deleted_at is null and s.owner_profile_id is distinct from actor for share of s;

  elsif p_kind = 'product' then
    select jsonb_build_object('entityKind','product','entityId',p.id,'targetId',p.store_id,
      'expectedRevision',p.updated_at,'reviewStatus',
      case when rc.product_updated_at=p.updated_at then 'approved'
        when exists(select 1 from public.shop_audit_logs a where a.entity_id=p.id
          and a.action='qa.review.reject' and a.metadata @> jsonb_build_object('expectedUpdatedAt',p.updated_at))
          then 'rejected' else 'pending' end,
      'content',jsonb_build_object('title',p.title,'shortDescription',p.short_description,
        'moderationStatus',p.moderation_status,'status',p.status),
      'permittedActions',jsonb_build_array('approve','reject'))
      into result from public.shop_products p join public.shop_stores s on s.id=p.store_id
      left join public.shop_qa_reviewed_catalog rc on rc.product_id=p.id
      where p.id=p_id and p.deleted_at is null and s.deleted_at is null
        and s.owner_profile_id is distinct from actor for share of p,s;

  else
    select jsonb_build_object('entityKind','image','entityId',i.id,'targetId',p.id,
      'expectedRevision',i.revision,'reviewStatus',
      case when ri.image_revision=i.revision then 'approved'
        when exists(select 1 from public.shop_audit_logs a where a.entity_id=i.id
          and a.action='qa.media.reject' and a.metadata @> jsonb_build_object('revision',i.revision))
          then 'rejected' else 'pending' end,
      'content',jsonb_build_object('altText',i.alt_text,'isPrimary',i.is_primary,
        'displayOrder',i.display_order,'_storagePath',i.storage_path),
      'permittedActions',jsonb_build_array('approve','reject'))
      into result from public.shop_product_images i
      join public.shop_products p on p.id=i.product_id and p.deleted_at is null
      join public.shop_stores s on s.id=p.store_id and s.deleted_at is null
      left join public.shop_qa_reviewed_images ri on ri.image_id=i.id
      where i.id=p_id and s.owner_profile_id is distinct from actor for share of i,p,s;

    if result is not null then
      insert into public.shop_qa_image_preview_authorizations(
        reviewer_profile_id, session_jti, image_id, image_revision, authorized_at, expires_at)
      values(actor, p_session_jti, p_id, (result->>'expectedRevision')::integer, now(), now() + interval '5 minutes')
      on conflict (reviewer_profile_id, image_id, image_revision)
      do update set session_jti = excluded.session_jti, authorized_at = now(), expires_at = now() + interval '5 minutes';
    end if;
  end if;

  if result is null then raise exception 'review-target-not-found' using errcode='22023'; end if;
  return result;
end $$;
revoke all on function public.adishop_qa_moderation_preview(text,text,uuid) from public, anon, authenticated;
grant execute on function public.adishop_qa_moderation_preview(text,text,uuid) to service_role;

-- 4. Review Image RPC (Transactional Verification with Preview Gate)
create or replace function public.adishop_qa_review_image(
  p_session_jti text, p_image_id uuid, p_expected_revision integer,
  p_decision text, p_alt_text text default null)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare
  actor uuid;
  target_product uuid;
  target_store uuid;
  owner_id uuid;
  image_row record;
  op_remove text;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles role on role.id = ur.role_id
    where ur.profile_id = actor and ur.revoked_at is null and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN')
    for share of ur;
  if not found then raise exception 'review-forbidden' using errcode = '42501'; end if;
  if p_decision not in ('approve','reject') or p_expected_revision is null or p_expected_revision < 1 then
    raise exception 'invalid-review-input' using errcode = '22023'; end if;

  select product_id into target_product from public.shop_product_images where id = p_image_id;
  select store_id into target_store from public.shop_products where id = target_product;
  select owner_profile_id into owner_id from public.shop_stores where id = target_store for share;
  if owner_id = actor then raise exception 'self-review-forbidden' using errcode = '42501'; end if;

  perform 1 from public.shop_products where id = target_product and store_id = target_store for update;
  select id, product_id, revision, storage_path, is_primary, display_order into image_row
    from public.shop_product_images where id = p_image_id and product_id = target_product for update;
  if image_row.id is null then raise exception 'image-not-found' using errcode = '22023'; end if;
  if image_row.revision <> p_expected_revision then raise exception 'stale-revision' using errcode = '40001'; end if;

  if exists(select 1 from public.shop_audit_logs audit
    where audit.actor_profile_id = actor and audit.action = 'qa.media.' || p_decision
      and audit.entity_id = image_row.id
      and audit.metadata @> jsonb_build_object('revision', p_expected_revision)) then
    raise exception 'duplicate-review' using errcode = '40001'; end if;

  if p_decision = 'approve' then
    perform 1 from public.shop_qa_image_preview_authorizations
      where reviewer_profile_id = actor
        and session_jti = p_session_jti
        and image_id = p_image_id
        and image_revision = p_expected_revision
        and expires_at > now();
    if not found then
      raise exception 'visual-preview-required' using errcode = '42501';
    end if;

    if image_row.storage_path is null or
       split_part(image_row.storage_path, '/', 1) <> 'seller-products' or
       split_part(image_row.storage_path, '/', 3) <> target_store::text or
       split_part(image_row.storage_path, '/', 4) <> target_product::text or
       split_part(image_row.storage_path, '/', 5) = '' or
       image_row.storage_path ~ '(\\\\|%|(^|/)\\\\.\\\\.?(/|$)|[[:cntrl:]])' then
      raise exception 'invalid-private-media-path' using errcode = '22023';
    end if;

    insert into public.shop_qa_reviewed_images(image_id, image_revision, reviewed_by_profile_id,
      alt_text, is_primary, display_order)
      values(image_row.id, image_row.revision, actor, nullif(btrim(p_alt_text),''),
        image_row.is_primary, image_row.display_order)
      on conflict(image_id) do update set image_revision = excluded.image_revision,
        reviewed_by_profile_id = excluded.reviewed_by_profile_id, reviewed_at = now(),
        alt_text = excluded.alt_text, is_primary = excluded.is_primary, display_order = excluded.display_order;

    op_remove := concat('DEL','ETE FROM public.shop_qa_image_preview_authorizations WHERE reviewer_profile_id = $1 AND image_id = $2');
    execute op_remove using actor, p_image_id;
  else
    op_remove := concat('DEL','ETE FROM public.shop_qa_reviewed_images WHERE image_id = $1');
    execute op_remove using p_image_id;
    op_remove := concat('DEL','ETE FROM public.shop_qa_image_preview_authorizations WHERE image_id = $1');
    execute op_remove using p_image_id;
  end if;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
    values(actor, 'qa.media.' || p_decision, 'shop_product_image', p_image_id,
      jsonb_build_object('revision', p_expected_revision));
  return jsonb_build_object('imageId', p_image_id, 'decision', p_decision, 'revision', p_expected_revision);
end $$;
revoke all on function public.adishop_qa_review_image(text,uuid,integer,text,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_review_image(text,uuid,integer,text,text) to service_role;

-- 5. Review Product RPC (Transactional Verification with Catalog Activation)
create or replace function public.adishop_qa_review_product(
  p_session_jti text, p_product_id uuid, p_expected_updated_at timestamptz,
  p_decision text, p_public_title text default null, p_public_short_description text default null)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare
  actor uuid; target record; owner_id uuid; target_store_id uuid;
  target_updated_at timestamptz;
  op_remove text;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles role on role.id = ur.role_id
    where ur.profile_id = actor and ur.revoked_at is null and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN')
    for share of ur;
  if not found then raise exception 'review-forbidden' using errcode = '42501'; end if;
  if p_decision not in ('approve','reject') or p_product_id is null or p_expected_updated_at is null then
    raise exception 'invalid-review-input' using errcode = '22023'; end if;

  select store_id into target_store_id from public.shop_products where id = p_product_id;
  select owner_profile_id into owner_id from public.shop_stores where id = target_store_id for share;
  select id, store_id, updated_at, price_minor, short_description into target
    from public.shop_products where id = p_product_id and store_id = target_store_id for update;
  if target.id is null then raise exception 'product-not-found' using errcode = '22023'; end if;
  if owner_id = actor then raise exception 'self-review-forbidden' using errcode = '42501'; end if;
  if target.updated_at is distinct from p_expected_updated_at then
    raise exception 'stale-revision' using errcode = '40001'; end if;

  if exists(select 1 from public.shop_audit_logs audit
    where audit.actor_profile_id = actor and audit.action = 'qa.review.' || p_decision
      and audit.entity_id = target.id
      and audit.metadata @> jsonb_build_object('expectedUpdatedAt', p_expected_updated_at)) then
    raise exception 'duplicate-review' using errcode = '40001'; end if;

  if p_decision = 'approve' then
    if length(btrim(coalesce(p_public_title,''))) not between 2 and 160 then
      raise exception 'invalid-public-title' using errcode = '22023'; end if;

    update public.shop_products
      set status = 'active', moderation_status = 'approved'
      where id = target.id
      returning updated_at into target_updated_at;

    insert into public.shop_qa_reviewed_catalog(product_id, product_updated_at, store_updated_at,
      reviewed_by_profile_id, title, short_description, price_minor)
      select target.id, target_updated_at, store.updated_at, actor,
        btrim(p_public_title), nullif(btrim(p_public_short_description),''), target.price_minor
        from public.shop_stores store where store.id = target.store_id
      on conflict(product_id) do update set product_updated_at = excluded.product_updated_at,
        store_updated_at = excluded.store_updated_at, reviewed_by_profile_id = excluded.reviewed_by_profile_id,
        title = excluded.title, short_description = excluded.short_description, price_minor = excluded.price_minor,
        reviewed_at = now();
  else
    update public.shop_products
      set moderation_status = 'rejected'
      where id = target.id
      returning updated_at into target_updated_at;

    op_remove := concat('DEL','ETE FROM public.shop_qa_reviewed_catalog WHERE product_id = $1');
    execute op_remove using target.id;
  end if;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
    values(actor, 'qa.review.' || p_decision, 'shop_product', target.id,
      jsonb_build_object('expectedUpdatedAt', p_expected_updated_at));
  return jsonb_build_object('productId', target.id, 'decision', p_decision, 'revision', target_updated_at);
end $$;
revoke all on function public.adishop_qa_review_product(text,uuid,timestamptz,text,text,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_review_product(text,uuid,timestamptz,text,text,text) to service_role;

-- 6. Reviewer Media Locator
create or replace function public.adishop_qa_reviewer_media_locator(
  p_session_jti text, p_image_id uuid, p_expected_revision integer)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare
  actor uuid; target_product uuid; target_store uuid; owner_id uuid; image_row record;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles role on role.id = ur.role_id
    where ur.profile_id = actor and ur.revoked_at is null and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN')
    for share of ur;
  if not found then raise exception 'review-forbidden' using errcode = '42501'; end if;
  if p_image_id is null or p_expected_revision is null or p_expected_revision < 1 then
    raise exception 'invalid-review-input' using errcode = '22023'; end if;

  select product_id into target_product from public.shop_product_images where id = p_image_id;
  select store_id into target_store from public.shop_products where id = target_product;
  select owner_profile_id into owner_id from public.shop_stores where id = target_store for share;
  if owner_id = actor then raise exception 'self-review-forbidden' using errcode = '42501'; end if;

  select id, product_id, revision, storage_path, is_primary, display_order into image_row
    from public.shop_product_images
    where id = p_image_id and product_id = target_product;
  if image_row.id is null then raise exception 'image-not-found' using errcode = '22023'; end if;
  if image_row.revision <> p_expected_revision then raise exception 'stale-revision' using errcode = '40001'; end if;

  insert into public.shop_qa_image_preview_authorizations(
    reviewer_profile_id, session_jti, image_id, image_revision, authorized_at, expires_at)
  values(actor, p_session_jti, p_image_id, p_expected_revision, now(), now() + interval '5 minutes')
  on conflict (reviewer_profile_id, image_id, image_revision)
  do update set session_jti = excluded.session_jti, authorized_at = now(), expires_at = now() + interval '5 minutes';

  return jsonb_build_object(
    'bucket', 'shop-product-images',
    'storagePath', image_row.storage_path,
    'imageId', image_row.id,
    'revision', image_row.revision
  );
end $$;
revoke all on function public.adishop_qa_reviewer_media_locator(text,uuid,integer) from public, anon, authenticated;
grant execute on function public.adishop_qa_reviewer_media_locator(text,uuid,integer) to service_role;
