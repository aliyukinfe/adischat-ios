-- Controlled Phase 1.4C validation seller mapping.
-- Uses the latest server-confirmed Shop session as Account S.
-- Safe guards stop execution if the selected profile/store is conflicting.

do $$
declare
  account_s uuid;
  seller_role uuid;
  selected_store uuid := '00000000-0000-4000-8000-000000000201';
  previous_owner uuid;
  audit_id uuid;
  correlation_id text := 'phase_1_4c_test_identity_setup_20260720';
  product_count integer;
  active_group_count integer;
begin
  select profile_id into account_s
  from public.shop_sessions
  where revoked_at is null
  order by created_at desc
  limit 1;

  if account_s is null then
    raise exception 'No server-confirmed Shop session found for Account S.';
  end if;

  if exists (
    select 1 from public.shop_stores
    where owner_profile_id = account_s
      and status = 'active'
      and approval_status = 'approved'
  ) then
    raise exception 'Account S already owns an active approved store.';
  end if;

  select owner_profile_id into previous_owner
  from public.shop_stores
  where id = selected_store
    and is_seed = true
    and status = 'active'
    and approval_status = 'approved'
    and accepts_cash_on_delivery = true
  for update;

  if not found then
    raise exception 'Selected validation store is not an approved active COD seed store.';
  end if;

  if previous_owner is not null then
    raise exception 'Selected validation store already has an owner.';
  end if;

  select count(*) into active_group_count
  from public.shop_order_groups
  where store_id = selected_store
    and fulfillment_status not in ('cancelled', 'rejected', 'delivered');

  if active_group_count > 0 then
    raise exception 'Selected validation store already has active order groups.';
  end if;

  select count(*) into product_count
  from public.shop_products p
  join public.shop_inventory i on i.product_id = p.id
  where p.store_id = selected_store
    and p.status = 'active'
    and p.moderation_status = 'approved'
    and p.cash_on_delivery_enabled = true
    and p.product_type = 'physical'
    and (i.quantity_available - i.quantity_reserved) > 0;

  if product_count < 1 then
    raise exception 'Selected validation store has no eligible product inventory.';
  end if;

  select id into seller_role from public.shop_roles where code = 'SELLER';
  if seller_role is null then
    raise exception 'SELLER role does not exist.';
  end if;

  update public.shop_stores
  set owner_profile_id = account_s,
      name = case when name ilike '%Validation%' then name else name || ' Validation' end,
      description = coalesce(description, '') || E'\nControlled Phase 1.4C live validation store.',
      accepts_cash_on_delivery = true,
      updated_at = now()
  where id = selected_store;

  insert into public.shop_user_roles(profile_id, role_id, assigned_by_profile_id, reason)
  select account_s, seller_role, null, 'Phase 1.4C controlled live validation'
  where not exists (
    select 1
    from public.shop_user_roles
    where profile_id = account_s
      and role_id = seller_role
      and revoked_at is null
  );

  insert into public.shop_seller_applications(
    profile_id, proposed_store_name, business_type, status, submitted_at, reviewed_at, updated_at
  )
  values (
    account_s, 'Phase 1.4C Validation Store', 'validation', 'approved', now(), now(), now()
  );

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (
    null,
    'seller_test_mapping_created',
    'shop_store',
    selected_store,
    jsonb_build_object(
      'targetProfileId', account_s,
      'storeId', selected_store,
      'previousOwnerProfileId', previous_owner,
      'newOwnerProfileId', account_s,
      'reason', 'Phase 1.4C controlled live validation',
      'correlationId', correlation_id,
      'eligibleProductCount', product_count
    )
  )
  returning id into audit_id;

  raise notice 'PHASE_1_4C_TEST_SELLER_MAPPING_CREATED profile=% store=% audit=% products=%',
    left(account_s::text, 8), left(selected_store::text, 8), audit_id, product_count;
end $$;

