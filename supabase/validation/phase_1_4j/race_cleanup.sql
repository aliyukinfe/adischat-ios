delete from public.shop_notifications
where data->>'orderGroupId' = '14a40000-0000-4000-8000-000000000002';

delete from public.shop_audit_logs
where entity_id = '14a40000-0000-4000-8000-000000000002'::uuid;

delete from public.shop_orders
where id = '14a40000-0000-4000-8000-000000000001'::uuid
  and order_number = 'PHASE14J-RACE-14A4';
