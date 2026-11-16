-- LOCAL PREPARATION CANDIDATE ONLY. No public RLS/grant or catalog RPC change.
-- and before phase9b9_public_grant_cutover_candidate.sql in an isolated DB.
-- Do not deploy: service Edge review transport and production HTTP parity remain unverified.
begin;

alter table shop_catalog_private.reviewed_content
  add column if not exists decision text not null default 'approved'
    check (decision in ('approved','rejected'));

create table if not exists shop_catalog_private.content_revisions (
  entity_kind text not null check (entity_kind in ('product','image','variant','store')),
  entity_id uuid not null,
  revision bigint not null check (revision > 0),
  primary key (entity_kind, entity_id)
);
alter table shop_catalog_private.content_revisions enable row level security;
revoke all on shop_catalog_private.content_revisions from public, anon, authenticated;
revoke insert,update,delete on shop_catalog_private.source_content,
  shop_catalog_private.reviewed_content from service_role;

create or replace function shop_catalog_private.capture_revision(p_kind text, p_id uuid)
returns void language plpgsql security definer set search_path = public, shop_catalog_private as $$
declare v_revision bigint; v_source jsonb;
begin
  if p_id is null then return; end if;
  if p_kind = 'product' then
    select to_jsonb(p) into v_source from public.shop_products p where p.id=p_id;
  elsif p_kind = 'image' then
    select to_jsonb(i) into v_source from public.shop_product_images i where i.id=p_id;
  elsif p_kind = 'variant' then
    select to_jsonb(v) into v_source from public.shop_product_variants v where v.id=p_id;
  elsif p_kind = 'store' then
    select to_jsonb(s) into v_source from public.shop_stores s where s.id=p_id;
  else raise exception 'invalid-content-kind'; end if;
  insert into shop_catalog_private.content_revisions(entity_kind,entity_id,revision)
    values(p_kind,p_id,1)
    on conflict(entity_kind,entity_id) do update
      set revision=shop_catalog_private.content_revisions.revision+1
    returning revision into v_revision;
  insert into shop_catalog_private.source_content
    (entity_kind,entity_id,source_version,private_payload)
    values(p_kind,p_id,v_revision::text,coalesce(v_source,'{"deleted":true}'::jsonb));
end;
$$;

create or replace function shop_catalog_private.invalidate_content()
returns trigger language plpgsql security definer set search_path = public, shop_catalog_private as $$
declare kind text := tg_argv[0]; old_product uuid; new_product uuid;
begin
  if kind in ('product','store','image','variant') then
    perform shop_catalog_private.capture_revision(kind,
      case when tg_op='DELETE' then old.id else new.id end);
    if kind in ('image','variant') then
      old_product := case when tg_op='INSERT' then null else old.product_id end;
      new_product := case when tg_op='DELETE' then null else new.product_id end;
      perform shop_catalog_private.capture_revision('product',old_product);
      if new_product is distinct from old_product then
        perform shop_catalog_private.capture_revision('product',new_product);
      end if;
    end if;
  elsif kind in ('specification','option') then
    old_product := case when tg_op='INSERT' then null else old.product_id end;
    new_product := case when tg_op='DELETE' then null else new.product_id end;
    perform shop_catalog_private.capture_revision('product',old_product);
    if new_product is distinct from old_product then
      perform shop_catalog_private.capture_revision('product',new_product);
    end if;
  elsif kind = 'option_value' then
    select product_id into old_product from public.shop_product_options
      where id=(case when tg_op='INSERT' then new.product_option_id else old.product_option_id end);
    perform shop_catalog_private.capture_revision('product',old_product);
    if tg_op='UPDATE' and new.product_option_id is distinct from old.product_option_id then
      select product_id into new_product from public.shop_product_options where id=new.product_option_id;
      perform shop_catalog_private.capture_revision('product',new_product);
    end if;
  elsif kind = 'variant_media' then
    select product_id into old_product from public.shop_product_variants
      where id=(case when tg_op='INSERT' then new.variant_id else old.variant_id end);
    perform shop_catalog_private.capture_revision('product',old_product);
    if tg_op='UPDATE' and new.variant_id is distinct from old.variant_id then
      select product_id into new_product from public.shop_product_variants where id=new.variant_id;
      if new_product is distinct from old_product then
        perform shop_catalog_private.capture_revision('product',new_product);
      end if;
    end if;
  end if;
  return case when tg_op='DELETE' then old else new end;
end;
$$;

create trigger adishop_phase9b10_product_revision
after insert or update of title,slug,brand,category_slug,short_description,description,
  product_type_description,specifications,shipping_details,return_policy,
  seller_sku,validation_errors,status,moderation_status,deleted_at,store_id
  or delete on public.shop_products for each row
execute function shop_catalog_private.invalidate_content('product');
create trigger adishop_phase9b10_store_revision
after insert or update of name,slug,description,logo_url,cover_url,category,city,
  status,approval_status,deleted_at,owner_profile_id,contact_email,contact_phone,
  address_line,region_state,refund_policy_text,exchange_policy_text,
  shipping_policy_text,warranty_policy_text,customer_service_policy_text,
  business_hours,social_links or delete on public.shop_stores for each row
execute function shop_catalog_private.invalidate_content('store');
create trigger adishop_phase9b10_image_revision
after insert or update of alt_text,image_url,storage_path,bucket,deleted_at,
  product_id,store_id or delete on public.shop_product_images for each row
execute function shop_catalog_private.invalidate_content('image');
create trigger adishop_phase9b10_variant_revision
after insert or update of title,options,status,product_id,store_id,sku,
  barcode,option_signature or delete on public.shop_product_variants for each row
execute function shop_catalog_private.invalidate_content('variant');
create trigger adishop_phase9b10_spec_revision
after insert or update or delete on public.shop_product_attribute_values for each row
execute function shop_catalog_private.invalidate_content('specification');
create trigger adishop_phase9b10_option_revision
after insert or update or delete on public.shop_product_options for each row
execute function shop_catalog_private.invalidate_content('option');
create trigger adishop_phase9b10_option_value_revision
after insert or update or delete on public.shop_product_option_values for each row
execute function shop_catalog_private.invalidate_content('option_value');
create trigger adishop_phase9b10_variant_media_revision
after insert or update or delete on public.shop_variant_media for each row
execute function shop_catalog_private.invalidate_content('variant_media');

-- Existing listings start at revision 1 but have no review. No auto-approval.
insert into shop_catalog_private.content_revisions(entity_kind,entity_id,revision)
select 'store',id,1 from public.shop_stores on conflict do nothing;
insert into shop_catalog_private.content_revisions(entity_kind,entity_id,revision)
select 'product',id,1 from public.shop_products on conflict do nothing;
insert into shop_catalog_private.content_revisions(entity_kind,entity_id,revision)
select 'image',id,1 from public.shop_product_images on conflict do nothing;
insert into shop_catalog_private.content_revisions(entity_kind,entity_id,revision)
select 'variant',id,1 from public.shop_product_variants on conflict do nothing;
insert into shop_catalog_private.source_content(entity_kind,entity_id,source_version,private_payload)
select 'store',s.id,'1',to_jsonb(s) from public.shop_stores s on conflict do nothing;
insert into shop_catalog_private.source_content(entity_kind,entity_id,source_version,private_payload)
select 'product',p.id,'1',to_jsonb(p) from public.shop_products p on conflict do nothing;
insert into shop_catalog_private.source_content(entity_kind,entity_id,source_version,private_payload)
select 'image',i.id,'1',to_jsonb(i) from public.shop_product_images i on conflict do nothing;
insert into shop_catalog_private.source_content(entity_kind,entity_id,source_version,private_payload)
select 'variant',v.id,'1',to_jsonb(v) from public.shop_product_variants v on conflict do nothing;

create or replace function shop_catalog_private.is_current_reviewed(p_kind text,p_id uuid)
returns boolean language sql stable security definer set search_path = public, shop_catalog_private as $$
  select exists(select 1 from shop_catalog_private.content_revisions r
    join shop_catalog_private.reviewed_content c
      on c.entity_kind=r.entity_kind and c.entity_id=r.entity_id
      and c.source_version=r.revision::text and c.decision='approved'
    where r.entity_kind=p_kind and r.entity_id=p_id)
$$;
grant usage on schema shop_catalog_private to anon,authenticated;
grant execute on function shop_catalog_private.is_current_reviewed(text,uuid) to anon,authenticated;
revoke all on function shop_catalog_private.capture_revision(text,uuid),
  shop_catalog_private.invalidate_content() from public,anon,authenticated;

create or replace function shop_catalog_private.is_public_product(p_id uuid)
returns boolean language sql stable security definer set search_path = public, shop_catalog_private as $$
  select exists(select 1 from public.shop_products p join public.shop_stores s on s.id=p.store_id
    where p.id=p_id and p.status='active' and p.moderation_status='approved'
      and p.deleted_at is null and s.status='active' and s.approval_status='approved'
      and s.deleted_at is null
      and shop_catalog_private.is_current_reviewed('product',p.id)
      and shop_catalog_private.is_current_reviewed('store',s.id)
      and not exists(select 1 from public.shop_product_images i
        where i.product_id=p.id and i.deleted_at is null
          and not shop_catalog_private.is_current_reviewed('image',i.id))
      and not exists(select 1 from public.shop_product_variants v
        where v.product_id=p.id and v.status='active'
          and not shop_catalog_private.is_current_reviewed('variant',v.id)))
$$;
grant execute on function shop_catalog_private.is_public_product(uuid) to anon,authenticated;

create or replace function public.adishop_review_public_content(
  p_actor_profile_id uuid,p_kind text,p_id uuid,p_expected_revision bigint,
  p_decision text
) returns bigint language plpgsql security definer set search_path = public, shop_catalog_private as $$
declare v_revision bigint; v_owner uuid; v_store uuid; v_source jsonb; v_core jsonb;
begin
  if p_kind not in ('product','image','variant','store')
      or p_decision not in ('approved','rejected') then raise exception 'invalid-review'; end if;
  if not exists(select 1 from public.shop_profiles where id=p_actor_profile_id and status='active')
      or not public.adishop_profile_has_permission(p_actor_profile_id,'product.moderation.approve')
    then raise exception 'admin-forbidden'; end if;
  select revision into v_revision from shop_catalog_private.content_revisions
    where entity_kind=p_kind and entity_id=p_id for update;
  if v_revision is null or v_revision<>p_expected_revision then raise exception 'stale-review'; end if;
  if p_kind='store' then v_store:=p_id;
  elsif p_kind='product' then select store_id into v_store from public.shop_products where id=p_id;
  elsif p_kind='image' then select p.store_id into v_store from public.shop_product_images i
    join public.shop_products p on p.id=i.product_id where i.id=p_id;
  else select p.store_id into v_store from public.shop_product_variants v
    join public.shop_products p on p.id=v.product_id where v.id=p_id;
  end if;
  select owner_profile_id into v_owner from public.shop_stores where id=v_store;
  if v_owner is null or v_owner=p_actor_profile_id then raise exception 'admin-forbidden'; end if;
  select private_payload into v_source from shop_catalog_private.source_content
    where entity_kind=p_kind and entity_id=p_id and source_version=v_revision::text;
  if v_source is null or v_source ? 'deleted' then raise exception 'stale-review'; end if;
  if p_decision='approved' and p_kind='product' and not exists(
      select 1 from public.shop_products where id=p_id and status='active'
        and moderation_status='approved' and deleted_at is null) then
    raise exception 'product-not-published'; end if;
  if p_decision='approved' and p_kind='product' then
    v_core:=public.adishop_product_catalog_payload_description_core(p_id);
    if v_core is null then raise exception 'product-not-published'; end if;
  end if;
  insert into shop_catalog_private.reviewed_content
    (entity_kind,entity_id,source_version,public_payload,reviewer_profile_id,decision)
    values(p_kind,p_id,v_revision::text,
      case when p_decision='approved' then
        case p_kind
          when 'product' then jsonb_build_object('title',v_source->'title',
            'slug',v_source->'slug','brand',v_source->'brand',
            'categorySlug',v_source->'category_slug',
            'shortDescription',v_source->'short_description',
            'description',v_source->'description',
            'specifications',coalesce(v_core->'specifications','[]'::jsonb),
            'options',coalesce(v_core->'options','[]'::jsonb))
          when 'image' then jsonb_build_object('altText',v_source->'alt_text',
            'imageUrl',v_source->'image_url')
          when 'variant' then jsonb_build_object('title',v_source->'title',
            'options',v_source->'options')
          else jsonb_build_object('name',v_source->'name',
            'slug',v_source->'slug','description',v_source->'description',
            'logoUrl',v_source->'logo_url','coverUrl',v_source->'cover_url',
            'category',v_source->'category','city',v_source->'city') end
        else '{}'::jsonb end,p_actor_profile_id,p_decision);
  return v_revision;
end;
$$;
revoke all on function public.adishop_review_public_content(uuid,text,uuid,bigint,text)
  from public,anon,authenticated;
grant execute on function public.adishop_review_public_content(uuid,text,uuid,bigint,text)
  to service_role;

create or replace function public.adishop_admin_public_content_queue(
  p_actor_profile_id uuid,p_kind text default null,p_limit integer default 50,
  p_offset integer default 0
) returns jsonb language plpgsql security definer
set search_path = public, shop_catalog_private as $$
declare v_total integer; v_items jsonb;
begin
  if not exists(select 1 from public.shop_profiles where id=p_actor_profile_id and status='active')
      or not public.adishop_profile_has_permission(p_actor_profile_id,'product.moderation.approve')
    then raise exception 'admin-forbidden'; end if;
  if p_kind is not null and p_kind not in ('product','image','variant','store') then
    raise exception 'invalid-review'; end if;
  if p_limit is null or p_limit<1 or p_limit>100 or p_offset is null or p_offset<0 then
    raise exception 'invalid-page'; end if;
  with eligible as (
    select r.entity_kind,r.entity_id,r.revision,
      case r.entity_kind
        when 'product' then (select p.id from public.shop_products p
          join public.shop_stores s on s.id=p.store_id where p.id=r.entity_id
          and p.status='active' and p.moderation_status='approved' and p.deleted_at is null
          and s.status='active' and s.approval_status='approved' and s.deleted_at is null)
        when 'image' then (select i.product_id from public.shop_product_images i
          join public.shop_products p on p.id=i.product_id
          join public.shop_stores s on s.id=p.store_id where i.id=r.entity_id
          and i.deleted_at is null and p.status='active' and p.moderation_status='approved'
          and p.deleted_at is null and s.status='active' and s.approval_status='approved'
          and s.deleted_at is null)
        when 'variant' then (select v.product_id from public.shop_product_variants v
          join public.shop_products p on p.id=v.product_id
          join public.shop_stores s on s.id=p.store_id where v.id=r.entity_id
          and v.status='active' and p.status='active' and p.moderation_status='approved'
          and p.deleted_at is null and s.status='active' and s.approval_status='approved'
          and s.deleted_at is null)
        else (select s.id from public.shop_stores s where s.id=r.entity_id
          and s.status='active' and s.approval_status='approved' and s.deleted_at is null)
      end as target_id
    from shop_catalog_private.content_revisions r
    where p_kind is null or r.entity_kind=p_kind
  ), pending as (
    select e.* from eligible e where e.target_id is not null
      and not shop_catalog_private.is_current_reviewed(e.entity_kind,e.entity_id)
  )
  select count(*),coalesce((select jsonb_agg(jsonb_build_object(
      'entityKind',x.entity_kind,'entityId',x.entity_id,
      'revision',x.revision,'targetId',x.target_id) order by x.entity_kind,x.entity_id)
      from (select * from pending order by entity_kind,entity_id
        limit p_limit offset p_offset) x),'[]'::jsonb)
  into v_total,v_items from pending;
  return jsonb_build_object('total',v_total,'items',v_items);
end;
$$;
revoke all on function public.adishop_admin_public_content_queue(uuid,text,integer,integer)
  from public,anon,authenticated;
grant execute on function public.adishop_admin_public_content_queue(uuid,text,integer,integer)
  to service_role;

create or replace function public.adishop_admin_public_content_preview(
  p_actor_profile_id uuid,p_kind text,p_id uuid
) returns jsonb language plpgsql security definer
set search_path = public, shop_catalog_private as $$
declare v_revision bigint; v_source jsonb; v_core jsonb; v_target uuid;
begin
  if not exists(select 1 from public.shop_profiles where id=p_actor_profile_id and status='active')
      or not public.adishop_profile_has_permission(p_actor_profile_id,'product.moderation.approve')
    then raise exception 'admin-forbidden'; end if;
  if p_kind not in ('product','image','variant','store') then raise exception 'invalid-review'; end if;
  select revision into v_revision from shop_catalog_private.content_revisions
    where entity_kind=p_kind and entity_id=p_id;
  if v_revision is null then raise exception 'review-target-not-found'; end if;
  select private_payload into v_source from shop_catalog_private.source_content
    where entity_kind=p_kind and entity_id=p_id and source_version=v_revision::text;
  if v_source is null or v_source ? 'deleted' then raise exception 'review-target-not-found'; end if;
  if p_kind='product' then
    v_target:=p_id;
    v_core:=public.adishop_product_catalog_payload_description_core(p_id);
  elsif p_kind='image' then
    select product_id into v_target from public.shop_product_images where id=p_id;
  elsif p_kind='variant' then
    select product_id into v_target from public.shop_product_variants where id=p_id;
  else v_target:=p_id;
  end if;
  return jsonb_build_object('entityKind',p_kind,'entityId',p_id,
    'revision',v_revision,'targetId',v_target,
    'content',case p_kind
      when 'product' then jsonb_build_object('title',v_source->'title',
        'slug',v_source->'slug','brand',v_source->'brand',
        'shortDescription',v_source->'short_description',
        'description',v_source->'description',
        'specifications',coalesce(v_core->'specifications','[]'::jsonb),
        'options',coalesce(v_core->'options','[]'::jsonb))
      when 'image' then jsonb_build_object('altText',v_source->'alt_text',
        'imageUrl',v_source->'image_url','storagePath',v_source->'storage_path')
      when 'variant' then jsonb_build_object('title',v_source->'title',
        'options',v_source->'options','sku',v_source->'sku','barcode',v_source->'barcode')
      else jsonb_build_object('name',v_source->'name','slug',v_source->'slug',
        'description',v_source->'description','logoUrl',v_source->'logo_url',
        'coverUrl',v_source->'cover_url','city',v_source->'city') end);
end;
$$;
revoke all on function public.adishop_admin_public_content_preview(uuid,text,uuid)
  from public,anon,authenticated;
grant execute on function public.adishop_admin_public_content_preview(uuid,text,uuid)
  to service_role;

commit;
