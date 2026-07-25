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
declare
  product_record public.shop_products%rowtype;
  store_record public.shop_stores%rowtype;
  old_state jsonb;
  new_status text;
  new_public_status text;
  image_count integer;
  stock_count integer;
begin
  if p_action = 'start_review' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.read') then raise exception 'admin-forbidden'; end if;
  if p_action = 'approve' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.approve') then raise exception 'admin-forbidden'; end if;
  if p_action = 'reject' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.reject') then raise exception 'admin-forbidden'; end if;
  if p_action = 'request_changes' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.request_changes') then raise exception 'admin-forbidden'; end if;
  if p_action in ('suspend','reactivate') and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.approve') then raise exception 'admin-forbidden'; end if;
  if p_action = 'archive' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.archive') then raise exception 'admin-forbidden'; end if;

  select * into product_record from public.shop_products where id = p_product_id for update;
  if not found then raise exception 'product-not-found'; end if;
  select * into store_record from public.shop_stores where id = product_record.store_id for update;
  if not found then raise exception 'store-not-found'; end if;

  if p_action in ('approve','reject','request_changes','suspend','reactivate','archive')
    and store_record.owner_profile_id = p_actor_profile_id
  then
    raise exception 'self-product-review-blocked';
  end if;

  if coalesce(trim(p_request_id), '') <> ''
    and exists (select 1 from public.shop_admin_audit_logs where request_id = p_request_id)
  then
    return public.adishop_admin_get_product(p_actor_profile_id, product_record.id);
  end if;

  old_state := to_jsonb(product_record);

  if p_action in ('reject','request_changes','suspend') and length(trim(coalesce(p_public_reason, p_reason_code, ''))) < 3 then
    raise exception 'reason-required';
  end if;

  if p_action = 'approve' then
    if store_record.status <> 'active' or store_record.approval_status <> 'approved' then raise exception 'store-not-active'; end if;
    if length(trim(product_record.title)) < 2 or product_record.price_minor <= 0 then raise exception 'product-validation-failed'; end if;
    select count(*) into image_count from public.shop_product_images where product_id = product_record.id;
    select count(*) into stock_count from public.shop_inventory where product_id = product_record.id and quantity_available >= quantity_reserved;
    if image_count < 1 then raise exception 'missing-images'; end if;
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
    else null
  end;
  if new_status is null then raise exception 'unsupported-action'; end if;

  if p_action = 'start_review' and product_record.moderation_status not in ('pending','submitted','under_review') then raise exception 'invalid-product-state'; end if;
  if p_action in ('approve','reject','request_changes') and product_record.moderation_status not in ('pending','submitted','under_review','changes_requested','approved','rejected') then raise exception 'invalid-product-state'; end if;
  if p_action = 'suspend' and product_record.moderation_status not in ('approved','suspended') then raise exception 'invalid-product-state'; end if;
  if p_action = 'reactivate' and product_record.moderation_status not in ('suspended','approved') then raise exception 'invalid-product-state'; end if;

  if product_record.moderation_status = new_status then
    return public.adishop_admin_get_product(p_actor_profile_id, product_record.id);
  end if;

  new_public_status := case
    when p_action in ('approve','reactivate') then 'active'
    when p_action in ('reject','request_changes','start_review') then 'draft'
    when p_action = 'suspend' then 'paused'
    when p_action = 'archive' then 'archived'
    else product_record.status
  end;

  update public.shop_products
  set moderation_status = new_status,
      status = new_public_status,
      reviewed_at = case when p_action <> 'start_review' then now() else reviewed_at end,
      reviewed_by_profile_id = p_actor_profile_id,
      published_at = case when p_action in ('approve','reactivate') then coalesce(published_at, now()) else published_at end,
      suspended_at = case when p_action = 'suspend' then now() when p_action = 'reactivate' then null else suspended_at end,
      updated_at = now()
  where id = product_record.id;

  insert into public.shop_product_moderation_reviews(product_id, reviewer_profile_id, decision, reason_code, public_reason, private_notes, previous_status, new_status, snapshot)
  values (product_record.id, p_actor_profile_id, p_action, p_reason_code, nullif(trim(coalesce(p_public_reason,'')), ''), nullif(trim(coalesce(p_private_notes,'')), ''), product_record.moderation_status, new_status, old_state);

  perform public.adishop_admin_audit(p_actor_profile_id, 'product_moderation.' || p_action, 'shop_product', product_record.id, old_state, jsonb_build_object('moderation_status', new_status, 'status', new_public_status), coalesce(p_public_reason,p_reason_code), jsonb_build_object('requestId', p_request_id), nullif(trim(coalesce(p_request_id,'')), ''));
  perform public.adishop_create_notification(store_record.owner_profile_id, 'product_moderation_' || p_action, 'Product moderation update', case p_action when 'approve' then 'Your product was approved and published.' when 'reject' then coalesce(p_public_reason,'Your product was rejected.') when 'request_changes' then coalesce(p_public_reason,'Changes are required before publishing.') when 'suspend' then 'Your product was suspended.' else 'Your product moderation status changed.' end, jsonb_build_object('productId', product_record.id, 'action', p_action));

  return public.adishop_admin_get_product(p_actor_profile_id, product_record.id);
end;
$$;
