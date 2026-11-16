-- Rollback for controlled Phase 1.4C validation seller mapping.
-- Preserves orders and audit history. Run only after validation is complete.

do $$
declare
  account_s uuid;
  seller_role uuid;
  selected_store uuid := '00000000-0000-4000-8000-000000000201';
begin
  select (metadata->>'targetProfileId')::uuid into account_s
  from public.shop_audit_logs
  where action = 'seller_test_mapping_created'
    and entity_type = 'shop_store'
    and entity_id = selected_store
    and metadata->>'correlationId' = 'phase_1_4c_test_identity_setup_20260720'
  order by created_at desc
  limit 1;

  if account_s is null then
    raise exception 'No controlled validation mapping audit row found.';
  end if;

  select id into seller_role from public.shop_roles where code = 'SELLER';

  update public.shop_stores
  set owner_profile_id = null,
      updated_at = now()
  where id = selected_store
    and owner_profile_id = account_s;

  update public.shop_user_roles
  set revoked_at = now()
  where profile_id = account_s
    and role_id = seller_role
    and revoked_at is null
    and reason = 'Phase 1.4C controlled live validation';

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (
    null,
    'seller_test_mapping_rolled_back',
    'shop_store',
    selected_store,
    jsonb_build_object(
      'targetProfileId', account_s,
      'storeId', selected_store,
      'reason', 'Phase 1.4C controlled live validation rollback',
      'correlationId', 'phase_1_4c_test_identity_setup_20260720'
    )
  );
end $$;

