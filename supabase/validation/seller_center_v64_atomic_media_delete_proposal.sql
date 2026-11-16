-- REVIEW ONLY: do not execute in production without separate authorization.
-- Keep this separate from the checksum-approved V6.2 and V6.3 proposals.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';

create function public.adishop_seller_delete_product_media_for_store(
  p_profile_id uuid,
  p_store_id uuid,
  p_product_id uuid,
  p_image_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  selected_store_id uuid := p_store_id;
  owned_store_ids uuid[];
  product_record public.shop_products%rowtype;
  image_record public.shop_product_images%rowtype;
  replacement_id uuid;
  product_result jsonb;
begin
  if p_profile_id is null or p_product_id is null or p_image_id is null then
    raise exception 'seller-product-media-not-found';
  end if;

  -- Preserve the legacy single-store request while requiring an explicit
  -- selection for sellers who own more than one approved store.
  if selected_store_id is null then
    select array_agg(s.id order by s.created_at, s.id) into owned_store_ids
    from public.shop_stores s
    where s.owner_profile_id = p_profile_id
      and s.status = 'active' and s.approval_status = 'approved'
      and s.deleted_at is null;
    if coalesce(array_length(owned_store_ids, 1), 0) > 1 then
      raise exception 'seller-store-selection-required';
    end if;
    selected_store_id := owned_store_ids[1];
  end if;

  if selected_store_id is null or not exists (
    select 1 from public.shop_stores s
    where s.id = selected_store_id and s.owner_profile_id = p_profile_id
      and s.status = 'active' and s.approval_status = 'approved'
      and s.deleted_at is null
  ) then
    raise exception 'seller-product-media-not-found';
  end if;

  -- Approval also takes this product row lock first. No variant or image
  -- mutation is possible until authorization and the locked state pass.
  select * into product_record from public.shop_products p
  where p.id = p_product_id and p.store_id = selected_store_id
    and p.deleted_at is null
  for update;
  if not found then raise exception 'seller-product-media-not-found'; end if;
  if product_record.status <> 'draft' or product_record.moderation_status
    not in ('pending', 'rejected', 'changes_requested') then
    raise exception 'seller-product-locked';
  end if;

  select * into image_record from public.shop_product_images i
  where i.id = p_image_id and i.product_id = p_product_id
    and i.store_id = selected_store_id and i.seller_profile_id = p_profile_id
    and i.deleted_at is null
  for update;
  if not found then raise exception 'seller-product-media-not-found'; end if;

  -- The V6.2 guard fires on this update. Any rejection aborts the whole RPC.
  update public.shop_product_images
  set deleted_at = now(), is_primary = false
  where id = image_record.id;

  delete from public.shop_variant_media
  where product_image_id = image_record.id;
  update public.shop_product_variants
  set primary_image_id = null
  where product_id = product_record.id and primary_image_id = image_record.id;

  if not exists (
    select 1 from public.shop_product_images i
    where i.product_id = product_record.id and i.deleted_at is null
      and i.is_primary
  ) then
    select i.id into replacement_id from public.shop_product_images i
    where i.product_id = product_record.id and i.deleted_at is null
    order by i.display_order, i.id limit 1 for update;
    if replacement_id is not null then
      update public.shop_product_images set is_primary = true
      where id = replacement_id;
    end if;
  end if;

  insert into public.shop_audit_logs
    (actor_profile_id, action, entity_type, entity_id, metadata)
  values
    (p_profile_id, 'seller_product_media.delete', 'shop_product',
     product_record.id, jsonb_build_object('imageId', image_record.id));

  product_result := public.adishop_seller_get_product_for_store(
    p_profile_id, selected_store_id, product_record.id
  );
  if product_result is null then
    raise exception 'seller-product-media-not-found';
  end if;
  return jsonb_build_object(
    'sellerProduct', product_result,
    'storageBucket', image_record.bucket,
    'storagePath', image_record.storage_path
  );
end;
$$;

revoke all on function public.adishop_seller_delete_product_media_for_store(uuid, uuid, uuid, uuid)
  from public, anon, authenticated;
grant execute on function public.adishop_seller_delete_product_media_for_store(uuid, uuid, uuid, uuid)
  to service_role;

-- The existing public policy admits soft-deleted image rows after a product
-- is later approved. Keep its current store/product checks and hide only
-- deleted image rows; active image visibility is unchanged.
alter policy public_read_approved_product_images on public.shop_product_images
  to anon, authenticated
  using (
    deleted_at is null
    and exists (
      select 1 from public.shop_products p
      join public.shop_stores s on s.id = p.store_id
      where p.id = shop_product_images.product_id
        and p.status = 'active' and p.moderation_status = 'approved'
        and p.deleted_at is null
        and s.status = 'active' and s.approval_status = 'approved'
        and s.deleted_at is null
    )
  );

commit;
