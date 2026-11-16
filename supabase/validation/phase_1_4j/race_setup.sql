delete from public.shop_notifications where data->>'orderGroupId' = '14a40000-0000-4000-8000-000000000002';
delete from public.shop_audit_logs where entity_id = '14a40000-0000-4000-8000-000000000002'::uuid;
delete from public.shop_orders where id = '14a40000-0000-4000-8000-000000000001'::uuid;

insert into public.shop_orders(
  id, profile_id, order_number, status, payment_status, fulfillment_status,
  delivery_status, subtotal_minor, total_minor
) values (
  '14a40000-0000-4000-8000-000000000001'::uuid,
  '1dcac349-9ab1-42dd-a9f6-7a11c97c3ea9'::uuid,
  'PHASE14J-RACE-14A4', 'pending', 'unpaid',
  'awaiting_seller_confirmation', 'not_shipped', 1000, 1000
);

insert into public.shop_order_groups(
  id, order_id, store_id, status, fulfillment_status, payment_status,
  subtotal_minor, store_name_snapshot
) values (
  '14a40000-0000-4000-8000-000000000002'::uuid,
  '14a40000-0000-4000-8000-000000000001'::uuid,
  'cfad4ae7-acb2-491d-accb-513935222a0c'::uuid,
  'created', 'awaiting_seller_confirmation', 'unpaid', 1000, 'Hulu Store'
);

update public.shop_order_groups
set seller_response_required_at = clock_timestamp() - interval '1 millisecond'
where id = '14a40000-0000-4000-8000-000000000002'::uuid;
