-- Disposable Phase 9F.9 fixture only. Mirrors the Shop Data API deny-all policy.
alter role service_role bypassrls;
grant usage on schema public to anon, authenticated, service_role;
do $$
declare name text;
begin
  foreach name in array array[
    'shop_orders','shop_order_groups','shop_order_items','shop_inventory',
    'shop_payments','shop_payment_events','shop_refund_requests',
    'shop_order_cancellations','shop_order_status_history',
    'shop_notifications','shop_audit_logs','shop_stores','shop_store_members'
  ] loop
    execute format('alter table public.%I enable row level security', name);
    execute format('create policy client_access_deferred on public.%I for all to anon, authenticated using (false) with check (false)', name);
    execute format('grant select, insert, update, delete on public.%I to service_role', name);
    execute format('grant select on public.%I to anon, authenticated', name);
  end loop;
end $$;
revoke all on function public.adishop_request_order_cancellation(uuid,uuid,text,text) from public, anon, authenticated;
revoke all on function public.adishop_transition_seller_order_group_scoped(uuid,uuid,uuid,text,text,jsonb) from public, anon, authenticated;
grant execute on function public.adishop_transition_seller_order_group_scoped(uuid,uuid,uuid,text,text,jsonb) to service_role;
revoke all on function public.adishop_expire_seller_order_group(uuid,text) from public, anon, authenticated;
