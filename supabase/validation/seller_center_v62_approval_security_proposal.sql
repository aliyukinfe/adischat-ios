-- REVIEW ONLY. Do not add to supabase/migrations or execute in production
-- without a separately approved deployment and rollback-only validation.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';

-- Serialize image removal with the product row lock taken by moderation.
-- The existing media endpoint already treats these states as non-editable;
-- this trigger closes the race between its read and its image update.
create or replace function public.adishop_guard_reviewed_product_image()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  product_record public.shop_products%rowtype;
begin
  if tg_op = 'DELETE' and old.deleted_at is not null then return old; end if;
  if tg_op = 'UPDATE' and old.deleted_at is not null and new.deleted_at is not null then
    return new;
  end if;

  select * into product_record
  from public.shop_products
  where id = old.product_id
  for update;

  if found and (
    product_record.status = 'active'
    or product_record.moderation_status in ('submitted', 'under_review', 'approved', 'suspended')
  ) then
    raise exception 'product-image-locked';
  end if;

  if tg_op = 'DELETE' then return old; end if;
  return new;
end;
$$;

revoke all on function public.adishop_guard_reviewed_product_image() from public, anon, authenticated;
grant execute on function public.adishop_guard_reviewed_product_image() to service_role;

create trigger shop_product_images_review_lock
before delete or update of deleted_at, product_id, store_id, image_url, bucket, storage_path
on public.shop_product_images
for each row execute function public.adishop_guard_reviewed_product_image();

-- The extra required argument is an optimistic token from the product the
-- admin actually reviewed. The old seven-argument RPC remains fail-closed for
-- review decisions until the existing Edge Function and Android client update.
create function public.adishop_admin_review_product(
  p_actor_profile_id uuid,
  p_product_id uuid,
  p_action text,
  p_reason_code text,
  p_public_reason text,
  p_private_notes text,
  p_request_id text,
  p_expected_submitted_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  product_record public.shop_products%rowtype;
  store_record public.shop_stores%rowtype;
  prior_audit public.shop_admin_audit_logs%rowtype;
  old_state jsonb;
  new_status text;
  new_public_status text;
  has_active_image boolean;
  stock_count integer;
  idempotency_key text := nullif(trim(coalesce(p_request_id, '')), '');
begin
  if p_action = 'start_review' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.read') then raise exception 'admin-forbidden'; end if;
  if p_action = 'approve' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.approve') then raise exception 'admin-forbidden'; end if;
  if p_action = 'reject' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.reject') then raise exception 'admin-forbidden'; end if;
  if p_action = 'request_changes' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.request_changes') then raise exception 'admin-forbidden'; end if;
  if p_action in ('suspend', 'reactivate') and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.approve') then raise exception 'admin-forbidden'; end if;
  if p_action = 'archive' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.archive') then raise exception 'admin-forbidden'; end if;
  if p_action is null or p_action not in ('start_review', 'approve', 'reject', 'request_changes', 'suspend', 'reactivate', 'archive') then raise exception 'unsupported-action'; end if;

  select * into product_record from public.shop_products where id = p_product_id for update;
  if not found or product_record.deleted_at is not null then raise exception 'product-not-found'; end if;
  select * into store_record from public.shop_stores where id = product_record.store_id for update;
  if not found then raise exception 'store-not-found'; end if;

  if p_action in ('approve', 'reject', 'request_changes', 'suspend', 'reactivate', 'archive')
    and store_record.owner_profile_id = p_actor_profile_id then
    raise exception 'self-product-review-blocked';
  end if;

  if idempotency_key is not null then
    select * into prior_audit from public.shop_admin_audit_logs where request_id = idempotency_key;
    if found then
      if prior_audit.actor_profile_id = p_actor_profile_id
        and prior_audit.target_type = 'shop_product'
        and prior_audit.target_id = p_product_id
        and prior_audit.action_type = 'product_moderation.' || p_action then
        return public.adishop_admin_get_product(p_actor_profile_id, p_product_id);
      end if;
      raise exception 'idempotency-conflict';
    end if;
  end if;

  if p_action in ('start_review', 'approve', 'reject', 'request_changes') then
    if p_expected_submitted_at is null or product_record.submitted_at is distinct from p_expected_submitted_at then
      raise exception 'stale-product-review';
    end if;
    if product_record.status <> 'draft'
      or product_record.submitted_at is null
      or coalesce(product_record.submitted_version, 0) < 1
      or (p_action = 'start_review' and product_record.moderation_status <> 'submitted')
      or (p_action <> 'start_review' and product_record.moderation_status not in ('submitted', 'under_review')) then
      raise exception 'invalid-product-state';
    end if;
  end if;

  if p_action = 'suspend' and (product_record.status <> 'active' or product_record.moderation_status <> 'approved') then
    raise exception 'invalid-product-state';
  end if;
  if p_action = 'reactivate' and (product_record.status <> 'paused' or product_record.moderation_status <> 'suspended' or product_record.published_at is null) then
    raise exception 'invalid-product-state';
  end if;
  if p_action = 'archive' and product_record.moderation_status = 'archived' then
    return public.adishop_admin_get_product(p_actor_profile_id, product_record.id);
  end if;

  old_state := to_jsonb(product_record);
  if p_action in ('reject', 'request_changes', 'suspend') and length(trim(coalesce(p_public_reason, p_reason_code, ''))) < 3 then
    raise exception 'reason-required';
  end if;

  if p_action in ('approve', 'reactivate') then
    if store_record.status <> 'active' or store_record.approval_status <> 'approved' or store_record.deleted_at is not null then
      raise exception 'store-not-active';
    end if;
    if length(trim(product_record.title)) < 2 or product_record.price_minor <= 0 then
      raise exception 'product-validation-failed';
    end if;
    select exists (
      select 1 from public.shop_product_images i
      where i.product_id = product_record.id
        and i.store_id = product_record.store_id
        and i.deleted_at is null
        and i.bucket = 'shop-product-images'
        and i.image_url ~* '^https://'
        and (i.storage_path is null or exists (
          select 1 from storage.objects o where o.bucket_id = i.bucket and o.name = i.storage_path
        ))
    ) into has_active_image;
    select count(*) into stock_count from public.shop_inventory
      where product_id = product_record.id and quantity_available >= quantity_reserved;
    if not has_active_image then raise exception 'missing-images'; end if;
    if product_record.stock_status <> 'out_of_stock' and stock_count < 1 then raise exception 'missing-stock'; end if;
  end if;

  new_status := case p_action
    when 'start_review' then 'under_review'
    when 'approve' then 'approved'
    when 'reject' then 'rejected'
    when 'request_changes' then 'changes_requested'
    when 'suspend' then 'suspended'
    when 'reactivate' then 'approved'
    when 'archive' then 'archived'
  end;

  new_public_status := case
    when p_action in ('approve', 'reactivate') then 'active'
    when p_action in ('reject', 'request_changes', 'start_review') then 'draft'
    when p_action = 'suspend' then 'paused'
    when p_action = 'archive' then 'archived'
    else product_record.status
  end;

  update public.shop_products
  set moderation_status = new_status,
      status = new_public_status,
      reviewed_at = case when p_action <> 'start_review' then now() else reviewed_at end,
      reviewed_by_profile_id = p_actor_profile_id,
      published_at = case when p_action in ('approve', 'reactivate') then coalesce(published_at, now()) else published_at end,
      suspended_at = case when p_action = 'suspend' then now() when p_action = 'reactivate' then null else suspended_at end,
      updated_at = now()
  where id = product_record.id;

  insert into public.shop_product_moderation_reviews(product_id, reviewer_profile_id, decision, reason_code, public_reason, private_notes, previous_status, new_status, snapshot)
  values (product_record.id, p_actor_profile_id, p_action, p_reason_code, nullif(trim(coalesce(p_public_reason, '')), ''), nullif(trim(coalesce(p_private_notes, '')), ''), product_record.moderation_status, new_status, old_state);

  perform public.adishop_admin_audit(p_actor_profile_id, 'product_moderation.' || p_action, 'shop_product', product_record.id, old_state, jsonb_build_object('moderation_status', new_status, 'status', new_public_status), coalesce(p_public_reason, p_reason_code), jsonb_build_object('requestId', idempotency_key), idempotency_key);
  perform public.adishop_create_notification(store_record.owner_profile_id, 'product_moderation_' || p_action, 'Product moderation update', case p_action when 'approve' then 'Your product was approved and published.' when 'reject' then coalesce(p_public_reason, 'Your product was rejected.') when 'request_changes' then coalesce(p_public_reason, 'Changes are required before publishing.') when 'suspend' then 'Your product was suspended.' else 'Your product moderation status changed.' end, jsonb_build_object('productId', product_record.id, 'action', p_action));

  return public.adishop_admin_get_product(p_actor_profile_id, product_record.id);
end;
$$;

revoke all on function public.adishop_admin_review_product(uuid, uuid, text, text, text, text, text, timestamptz) from public, anon, authenticated;
grant execute on function public.adishop_admin_review_product(uuid, uuid, text, text, text, text, text, timestamptz) to service_role;

create or replace function public.adishop_admin_review_product(
  p_actor_profile_id uuid,
  p_product_id uuid,
  p_action text,
  p_reason_code text default null,
  p_public_reason text default '',
  p_private_notes text default '',
  p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if p_action in ('start_review', 'approve', 'reject', 'request_changes') then
    raise exception 'stale-product-review';
  end if;
  return public.adishop_admin_review_product(
    p_actor_profile_id, p_product_id, p_action, p_reason_code,
    p_public_reason, p_private_notes, p_request_id, null::timestamptz
  );
end;
$$;

revoke all on function public.adishop_admin_review_product(uuid, uuid, text, text, text, text, text) from public, anon, authenticated;
grant execute on function public.adishop_admin_review_product(uuid, uuid, text, text, text, text, text) to service_role;

commit;
