-- LOCAL QA CANDIDATE ONLY. Private Storage buckets remain unchanged.
begin;
alter table public.shop_product_images add column if not exists revision integer not null default 1;
alter table public.shop_product_images add column if not exists updated_at timestamptz not null default now();
create table if not exists public.shop_qa_reviewed_images (
  image_id uuid primary key references public.shop_product_images(id),
  image_revision integer not null check (image_revision > 0),
  reviewed_by_profile_id uuid not null references public.shop_profiles(id),
  reviewed_at timestamptz not null default now(),
  alt_text text, is_primary boolean not null default false, display_order integer not null default 0
);
alter table public.shop_qa_reviewed_images enable row level security;
revoke all on public.shop_qa_reviewed_images from public,anon,authenticated;
grant select on public.shop_qa_reviewed_images to service_role;

create or replace function shop_qa_private.touch_image_revision()
returns trigger language plpgsql set search_path='' as $$
begin
  new.revision:=old.revision+1;
  new.updated_at:=clock_timestamp();
  return new;
end $$;
create or replace function shop_qa_private.invalidate_product_for_image()
returns trigger language plpgsql set search_path='' as $$
begin
  if tg_op='DELETE' then
    update public.shop_products set updated_at=clock_timestamp() where id=old.product_id;
    return old;
  end if;
  update public.shop_products set updated_at=clock_timestamp() where id=new.product_id;
  if tg_op='UPDATE' and old.product_id is distinct from new.product_id then
    update public.shop_products set updated_at=clock_timestamp() where id=old.product_id;
  end if;
  return new;
end $$;
revoke all on function shop_qa_private.touch_image_revision() from public,anon,authenticated;
revoke all on function shop_qa_private.invalidate_product_for_image() from public,anon,authenticated;
drop trigger if exists shop_qa_image_revision on public.shop_product_images;
create trigger shop_qa_image_revision before update on public.shop_product_images
  for each row execute function shop_qa_private.touch_image_revision();
drop trigger if exists shop_qa_image_invalidate on public.shop_product_images;
create trigger shop_qa_image_invalidate after insert or update or delete on public.shop_product_images
  for each row execute function shop_qa_private.invalidate_product_for_image();

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

create or replace function public.adishop_qa_media_locator(p_kind text,p_id uuid)
returns jsonb language sql stable security definer set search_path='' as $$
  select jsonb_build_object('kind',p_kind,'id',case when p_kind='image' then image.id else product.id end,
    'revision',image.revision,'bucket','shop-product-images','storagePath',image.storage_path,
    'productId',product.id,'storeId',product.store_id)
  from public.shop_product_images image
  join public.shop_products product on product.id=image.product_id
  join public.shop_qa_public_catalog reviewed_product on reviewed_product.id=product.id
  join public.shop_qa_reviewed_images review on review.image_id=image.id
    and review.image_revision=image.revision
  join public.shop_profiles reviewer on reviewer.id=review.reviewed_by_profile_id and reviewer.status='active'
  join public.shop_user_roles ur on ur.profile_id=reviewer.id and ur.revoked_at is null
  join public.shop_roles role on role.id=ur.role_id and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN')
  join public.shop_stores store on store.id=product.store_id
  where p_kind in ('image','product-cover') and image.storage_path is not null
    and review.reviewed_by_profile_id is distinct from store.owner_profile_id
    and (p_kind='image' and image.id=p_id or p_kind='product-cover' and product.id=p_id)
  order by image.is_primary desc,image.display_order,image.id limit 1
$$;
revoke all on function public.adishop_qa_media_locator(text,uuid) from public,anon,authenticated;
grant execute on function public.adishop_qa_media_locator(text,uuid) to service_role;
commit;
