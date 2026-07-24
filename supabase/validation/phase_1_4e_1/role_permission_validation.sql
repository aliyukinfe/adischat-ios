select public.adishop_admin_assign_role(
  '6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee',
  '10000000-0000-4000-8000-000000014e02',
  'SUPPORT_AGENT',
  'Phase 1.4E.1 role boundary validation'
) ? 'ok' as support_role_assigned;

select public.adishop_admin_assign_role(
  '6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee',
  '10000000-0000-4000-8000-000000014e03',
  'SHOP_ADMIN',
  'Phase 1.4E.1 role boundary validation'
) ? 'ok' as shop_admin_role_assigned;

do $$
begin
  begin
    perform public.adishop_admin_review_product(
      '10000000-0000-4000-8000-000000014e02',
      '10000000-0000-4000-8000-000000014e13',
      'approve',
      null,
      '',
      '',
      'phase-1-4e-1-support-denied-product-approve'
    );
    raise exception 'support-agent-product-approve-unexpectedly-allowed';
  exception when others then
    if sqlerrm <> 'admin-forbidden' then raise; end if;
  end;

  begin
    perform public.adishop_admin_assign_role(
      '10000000-0000-4000-8000-000000014e03',
      '10000000-0000-4000-8000-000000014e02',
      'SHOP_SUPER_ADMIN',
      'Phase 1.4E.1 forbidden escalation probe'
    );
    raise exception 'shop-admin-super-admin-assignment-unexpectedly-allowed';
  exception when others then
    if sqlerrm <> 'admin-forbidden' then raise; end if;
  end;

  begin
    perform public.adishop_admin_assign_role(
      '6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee',
      '6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee',
      'SHOP_SUPER_ADMIN',
      'Phase 1.4E.1 self escalation probe'
    );
    raise exception 'self-escalation-unexpectedly-allowed';
  exception when others then
    if sqlerrm <> 'self-escalation-blocked' then raise; end if;
  end;

  begin
    perform public.adishop_admin_revoke_role(
      '6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee',
      '6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee',
      'SHOP_SUPER_ADMIN',
      'Phase 1.4E.1 last super admin protection probe'
    );
    raise exception 'last-super-admin-revoke-unexpectedly-allowed';
  exception when others then
    if sqlerrm <> 'last-super-admin-protected' then raise; end if;
  end;
end $$;

select
  public.adishop_profile_has_permission('10000000-0000-4000-8000-000000014e02','order.support.read') as support_can_read_orders,
  public.adishop_profile_has_permission('10000000-0000-4000-8000-000000014e02','product.moderation.approve') as support_can_approve_product,
  public.adishop_profile_has_permission('10000000-0000-4000-8000-000000014e03','product.moderation.approve') as shop_admin_can_approve_product,
  public.adishop_profile_has_permission('10000000-0000-4000-8000-000000014e03','admin.role.assign') as shop_admin_can_assign_roles;
