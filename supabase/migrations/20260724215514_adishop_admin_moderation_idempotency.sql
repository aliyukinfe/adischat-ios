create unique index if not exists shop_admin_audit_request_id_unique_idx
  on public.shop_admin_audit_logs(request_id)
  where request_id is not null;

create or replace function public.adishop_admin_review_seller_application(
  p_actor_profile_id uuid,
  p_application_id uuid,
  p_action text,
  p_reason text default '',
  p_private_notes text default '',
  p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  app_record public.shop_seller_applications%rowtype;
  new_status text;
  store_id uuid;
  seller_role_id uuid;
  old_state jsonb;
  new_state jsonb;
begin
  if p_action in ('start_review') and not public.adishop_profile_has_permission(p_actor_profile_id, 'seller.application.review') then raise exception 'admin-forbidden'; end if;
  if p_action in ('approve') and not public.adishop_profile_has_permission(p_actor_profile_id, 'seller.application.approve') then raise exception 'admin-forbidden'; end if;
  if p_action in ('reject') and not public.adishop_profile_has_permission(p_actor_profile_id, 'seller.application.reject') then raise exception 'admin-forbidden'; end if;
  if p_action in ('suspend') and not public.adishop_profile_has_permission(p_actor_profile_id, 'seller.application.suspend') then raise exception 'admin-forbidden'; end if;
  if p_action in ('reactivate') and not public.adishop_profile_has_permission(p_actor_profile_id, 'seller.application.reactivate') then raise exception 'admin-forbidden'; end if;

  select * into app_record from public.shop_seller_applications where id = p_application_id for update;
  if not found then raise exception 'seller-application-not-found'; end if;

  if coalesce(trim(p_request_id), '') <> ''
    and exists (select 1 from public.shop_admin_audit_logs where request_id = p_request_id)
  then
    return public.adishop_admin_get_seller_application(p_actor_profile_id, app_record.id);
  end if;

  old_state := to_jsonb(app_record);

  if p_action in ('reject','suspend') and length(trim(coalesce(p_reason,''))) < 3 then
    raise exception 'reason-required';
  end if;

  new_status := case p_action
    when 'start_review' then 'under_review'
    when 'approve' then 'approved'
    when 'reject' then 'rejected'
    when 'suspend' then 'suspended'
    when 'reactivate' then 'approved'
    else null
  end;
  if new_status is null then raise exception 'unsupported-action'; end if;

  if p_action = 'start_review' and app_record.status not in ('submitted','under_review') then raise exception 'invalid-seller-application-state'; end if;
  if p_action = 'approve' and app_record.status not in ('submitted','under_review','approved') then raise exception 'invalid-seller-application-state'; end if;
  if p_action = 'reject' and app_record.status not in ('submitted','under_review','rejected') then raise exception 'invalid-seller-application-state'; end if;
  if p_action = 'suspend' and app_record.status not in ('approved','suspended') then raise exception 'invalid-seller-application-state'; end if;
  if p_action = 'reactivate' and app_record.status not in ('suspended','approved') then raise exception 'invalid-seller-application-state'; end if;

  if app_record.status = new_status then
    return public.adishop_admin_get_seller_application(p_actor_profile_id, app_record.id);
  end if;

  update public.shop_seller_applications
  set status = new_status,
      rejection_reason = case when p_action = 'reject' then p_reason when p_action in ('approve','reactivate') then null else rejection_reason end,
      reviewed_at = case when p_action in ('approve','reject','suspend','reactivate') then now() else reviewed_at end,
      reviewed_by_profile_id = p_actor_profile_id,
      suspended_at = case when p_action = 'suspend' then now() when p_action = 'reactivate' then null else suspended_at end,
      updated_at = now()
  where id = app_record.id
  returning to_jsonb(public.shop_seller_applications.*) into new_state;

  insert into public.shop_seller_application_reviews(application_id, reviewer_profile_id, decision, reason, private_notes, previous_status, new_status)
  values (app_record.id, p_actor_profile_id, p_action, nullif(trim(coalesce(p_reason,'')), ''), nullif(trim(coalesce(p_private_notes,'')), ''), app_record.status, new_status);

  if p_action in ('approve','reactivate') then
    select id into seller_role_id from public.shop_roles where code = 'SELLER';
    insert into public.shop_user_roles(profile_id, role_id, assigned_by_profile_id, reason)
    values (app_record.profile_id, seller_role_id, p_actor_profile_id, 'seller application approved')
    on conflict (profile_id, role_id) do update set revoked_at = null, assigned_by_profile_id = excluded.assigned_by_profile_id, reason = excluded.reason;

    insert into public.shop_stores(owner_profile_id, name, slug, description, category, city, status, approval_status)
    values (
      app_record.profile_id,
      app_record.proposed_store_name,
      lower(regexp_replace(app_record.proposed_store_name, '[^a-zA-Z0-9]+', '-', 'g')) || '-' || substr(app_record.id::text, 1, 8),
      'Approved AdisGebeya seller store',
      app_record.business_type,
      null,
      'active',
      'approved'
    )
    on conflict do nothing
    returning id into store_id;

    if store_id is null then
      select id into store_id from public.shop_stores where owner_profile_id = app_record.profile_id order by created_at desc limit 1;
      update public.shop_stores set status = 'active', approval_status = 'approved', suspended_at = null, updated_at = now() where id = store_id;
    end if;

    insert into public.shop_store_members(store_id, profile_id, role, status)
    values (store_id, app_record.profile_id, 'owner', 'active')
    on conflict do nothing;
  elsif p_action = 'suspend' then
    update public.shop_stores set status = 'suspended', approval_status = 'suspended', suspended_at = now(), updated_at = now() where owner_profile_id = app_record.profile_id;
  end if;

  perform public.adishop_admin_audit(p_actor_profile_id, 'seller_application.' || p_action, 'shop_seller_application', app_record.id, old_state, new_state, p_reason, jsonb_build_object('requestId', p_request_id), nullif(trim(coalesce(p_request_id,'')), ''));
  perform public.adishop_create_notification(app_record.profile_id, 'seller_application_' || p_action, 'Seller application update', case p_action when 'approve' then 'Your seller application was approved.' when 'reject' then coalesce(p_reason,'Your seller application was rejected.') when 'suspend' then 'Your seller account was suspended.' else 'Your seller application was updated.' end, jsonb_build_object('applicationId', app_record.id, 'action', p_action));

  return public.adishop_admin_get_seller_application(p_actor_profile_id, app_record.id);
end;
$$;

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
