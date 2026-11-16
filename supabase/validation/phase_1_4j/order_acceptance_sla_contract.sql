begin;

do $validation$
declare
  buyer_id constant uuid := '1dcac349-9ab1-42dd-a9f6-7a11c97c3ea9';
  hulu_seller_id constant uuid := '98118bab-7f68-468d-9b28-75bf9ccfe12e';
  other_seller_id constant uuid := '6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee';
  unrelated_profile_id constant uuid := '1dcac349-9ab1-42dd-a9f6-7a11c97c3ea9';
  hulu_store_id constant uuid := 'cfad4ae7-acb2-491d-accb-513935222a0c';
  other_store_id constant uuid := '00000000-0000-4000-8000-000000000201';
  hulu_product_id constant uuid := 'e7df32cd-4aea-4916-a2aa-cafc41f9c61a';
  other_product_id constant uuid := '59936111-62e4-4f18-9a64-16846bdd93f6';
  order_id uuid;
  group_id uuid;
  group_two_id uuid;
  test_payment_id uuid;
  before_stock integer;
  result jsonb;
  denied boolean;
  deadline timestamptz;
  test_prefix text := 'PHASE14J-' || replace(gen_random_uuid()::text, '-', '');
begin
  -- Server deadline ignores a client-supplied timestamp and uses the 24-hour SLA.
  order_id := gen_random_uuid();
  insert into public.shop_orders(
    id, profile_id, order_number, status, payment_status, fulfillment_status,
    delivery_status, subtotal_minor, total_minor
  ) values (
    order_id, buyer_id, test_prefix || '-DEADLINE', 'pending', 'unpaid',
    'awaiting_seller_confirmation', 'not_shipped', 1000, 1000
  );
  group_id := gen_random_uuid();
  insert into public.shop_order_groups(
    id, order_id, store_id, status, fulfillment_status, payment_status,
    subtotal_minor, store_name_snapshot, seller_response_required_at
  ) values (
    group_id, order_id, hulu_store_id, 'created', 'awaiting_seller_confirmation',
    'unpaid', 1000, 'Hulu Store', clock_timestamp() - interval '1 day'
  ) returning seller_response_required_at into deadline;
  if deadline < clock_timestamp() + interval '23 hours 59 minutes'
     or deadline > clock_timestamp() + interval '24 hours 1 minute' then
    raise exception 'deadline-calculation-failed: %', deadline;
  end if;

  -- COD expiration restores committed inventory exactly once and creates no refund.
  select quantity_available into before_stock
  from public.shop_inventory
  where product_id = hulu_product_id and variant_id is null
  for update;
  update public.shop_inventory
  set quantity_available = quantity_available - 1
  where product_id = hulu_product_id and variant_id is null;
  insert into public.shop_order_items(
    order_group_id, product_id, title_snapshot, quantity,
    unit_price_minor, line_total_minor
  ) values (group_id, hulu_product_id, 'Phase 1.4J validation', 1, 1000, 1000);
  insert into public.shop_payments(
    id, order_id, profile_id, provider, method_code, status, amount_minor
  ) values (
    gen_random_uuid(), order_id, buyer_id, 'manual_cod', 'cash_on_delivery',
    'unpaid', 1000
  );
  update public.shop_order_groups
  set seller_response_required_at = clock_timestamp() - interval '1 second'
  where id = group_id;
  result := public.adishop_expire_seller_order_group(group_id, 'automatic');
  if result->>'sellerResponseStatus' <> 'expired'
     or result->>'refundStatus' <> 'not_required' then
    raise exception 'cod-expiration-failed: %', result;
  end if;
  result := public.adishop_expire_seller_order_group(group_id, 'automatic');
  if coalesce((result->>'idempotent')::boolean, false) is not true then
    raise exception 'duplicate-expiration-not-idempotent: %', result;
  end if;
  if (select quantity_available from public.shop_inventory where product_id = hulu_product_id and variant_id is null) <> before_stock then
    raise exception 'inventory-not-restored-exactly-once';
  end if;
  if (select count(*) from public.shop_order_status_history where order_group_id = group_id and event_code = 'seller_response_expired') <> 1 then
    raise exception 'expiration-history-not-exactly-once';
  end if;
  if (select count(*) from public.shop_notifications where profile_id = buyer_id and type = 'seller_response_expired' and data->>'orderGroupId' = group_id::text) <> 1 then
    raise exception 'expiration-notification-not-exactly-once';
  end if;
  if (select count(*) from public.shop_audit_logs where entity_id = group_id and action = 'seller_order_group.expire') <> 1 then
    raise exception 'expiration-audit-not-exactly-once';
  end if;

  -- Acceptance is atomic/idempotent and a later expiration pass cannot change it.
  order_id := gen_random_uuid();
  insert into public.shop_orders(id, profile_id, order_number, status, payment_status, fulfillment_status, delivery_status, subtotal_minor, total_minor)
  values (order_id, buyer_id, test_prefix || '-ACCEPT', 'pending', 'unpaid', 'awaiting_seller_confirmation', 'not_shipped', 1000, 1000);
  group_id := gen_random_uuid();
  insert into public.shop_order_groups(id, order_id, store_id, status, fulfillment_status, payment_status, subtotal_minor, store_name_snapshot)
  values (group_id, order_id, hulu_store_id, 'created', 'awaiting_seller_confirmation', 'unpaid', 1000, 'Hulu Store');
  result := public.adishop_transition_seller_order_group(
    hulu_seller_id, group_id, 'accept', null,
    jsonb_build_object('requestId', test_prefix || '-accept', 'expectedVersion', 1)
  );
  if result->>'sellerResponseStatus' <> 'accepted' then raise exception 'accept-failed: %', result; end if;
  result := public.adishop_transition_seller_order_group(
    hulu_seller_id, group_id, 'accept', null,
    jsonb_build_object('requestId', test_prefix || '-accept', 'expectedVersion', 1)
  );
  if coalesce((result->>'idempotent')::boolean, false) is not true then raise exception 'accept-not-idempotent: %', result; end if;
  update public.shop_order_groups set seller_response_required_at = clock_timestamp() - interval '1 second' where id = group_id;
  result := public.adishop_expire_seller_order_group(group_id, 'automatic');
  if (select seller_response_status from public.shop_order_groups where id = group_id) <> 'accepted' then
    raise exception 'accepted-order-was-expired';
  end if;
  if (select count(*) from public.shop_order_status_history where order_group_id = group_id and event_code = 'seller_accepted') <> 1 then
    raise exception 'accept-history-not-exactly-once';
  end if;

  -- Rejection requires a server-validated reason and restores inventory once.
  order_id := gen_random_uuid();
  insert into public.shop_orders(id, profile_id, order_number, status, payment_status, fulfillment_status, delivery_status, subtotal_minor, total_minor)
  values (order_id, buyer_id, test_prefix || '-REJECT', 'pending', 'unpaid', 'awaiting_seller_confirmation', 'not_shipped', 1000, 1000);
  group_id := gen_random_uuid();
  insert into public.shop_order_groups(id, order_id, store_id, status, fulfillment_status, payment_status, subtotal_minor, store_name_snapshot)
  values (group_id, order_id, hulu_store_id, 'created', 'awaiting_seller_confirmation', 'unpaid', 1000, 'Hulu Store');
  select quantity_available into before_stock from public.shop_inventory where product_id = hulu_product_id and variant_id is null for update;
  update public.shop_inventory set quantity_available = quantity_available - 1 where product_id = hulu_product_id and variant_id is null;
  insert into public.shop_order_items(order_group_id, product_id, title_snapshot, quantity, unit_price_minor, line_total_minor)
  values (group_id, hulu_product_id, 'Phase 1.4J validation', 1, 1000, 1000);
  result := public.adishop_transition_seller_order_group(
    hulu_seller_id, group_id, 'reject', null,
    jsonb_build_object('requestId', test_prefix || '-reject', 'expectedVersion', 1, 'reasonCode', 'out_of_stock')
  );
  if result->>'sellerResponseStatus' <> 'rejected' then raise exception 'reject-failed: %', result; end if;
  result := public.adishop_transition_seller_order_group(
    hulu_seller_id, group_id, 'reject', null,
    jsonb_build_object('requestId', test_prefix || '-reject', 'expectedVersion', 1, 'reasonCode', 'out_of_stock')
  );
  if coalesce((result->>'idempotent')::boolean, false) is not true then raise exception 'reject-not-idempotent: %', result; end if;
  if (select quantity_available from public.shop_inventory where product_id = hulu_product_id and variant_id is null) <> before_stock then
    raise exception 'rejection-inventory-not-restored-once';
  end if;

  -- A captured payment creates one pending refund and never claims it is refunded.
  order_id := gen_random_uuid();
  insert into public.shop_orders(id, profile_id, order_number, status, payment_status, fulfillment_status, delivery_status, subtotal_minor, total_minor)
  values (order_id, buyer_id, test_prefix || '-PAID', 'pending', 'paid', 'awaiting_seller_confirmation', 'not_shipped', 1500, 1500);
  group_id := gen_random_uuid();
  insert into public.shop_order_groups(id, order_id, store_id, status, fulfillment_status, payment_status, subtotal_minor, store_name_snapshot)
  values (group_id, order_id, hulu_store_id, 'created', 'awaiting_seller_confirmation', 'paid', 1500, 'Hulu Store');
  test_payment_id := gen_random_uuid();
  insert into public.shop_payments(id, order_id, profile_id, provider, method_code, status, amount_minor)
  values (test_payment_id, order_id, buyer_id, 'phase14j_test', 'electronic', 'paid', 1500);
  update public.shop_order_groups set seller_response_required_at = clock_timestamp() - interval '1 second' where id = group_id;
  perform public.adishop_expire_seller_order_group(group_id, 'automatic');
  perform public.adishop_expire_seller_order_group(group_id, 'automatic');
  if (select seller_response_refund_status from public.shop_order_groups where id = group_id) <> 'refund_pending' then
    raise exception 'paid-expiration-not-refund-pending';
  end if;
  if (select status from public.shop_payments where id = test_payment_id) <> 'paid' then
    raise exception 'payment-falsely-marked-refunded';
  end if;
  if (select count(*) from public.shop_refund_requests where order_group_id = group_id and reason_code = 'seller_response_expired') <> 1 then
    raise exception 'refund-request-not-exactly-once';
  end if;

  -- Authorization is queued for voiding, without falsely changing provider state.
  order_id := gen_random_uuid();
  insert into public.shop_orders(id, profile_id, order_number, status, payment_status, fulfillment_status, delivery_status, subtotal_minor, total_minor)
  values (order_id, buyer_id, test_prefix || '-AUTH', 'pending', 'authorized', 'awaiting_seller_confirmation', 'not_shipped', 1200, 1200);
  group_id := gen_random_uuid();
  insert into public.shop_order_groups(id, order_id, store_id, status, fulfillment_status, payment_status, subtotal_minor, store_name_snapshot)
  values (group_id, order_id, hulu_store_id, 'created', 'awaiting_seller_confirmation', 'authorized', 1200, 'Hulu Store');
  test_payment_id := gen_random_uuid();
  insert into public.shop_payments(id, order_id, profile_id, provider, method_code, status, amount_minor)
  values (test_payment_id, order_id, buyer_id, 'phase14j_test', 'electronic', 'authorized', 1200);
  update public.shop_order_groups set seller_response_required_at = clock_timestamp() - interval '1 second' where id = group_id;
  perform public.adishop_expire_seller_order_group(group_id, 'automatic');
  if (select seller_response_refund_status from public.shop_order_groups where id = group_id) <> 'authorization_void_pending' then
    raise exception 'authorization-not-queued-for-void';
  end if;
  if (select status from public.shop_payments where id = test_payment_id) <> 'authorized' then
    raise exception 'authorization-falsely-marked-voided';
  end if;
  if (select count(*) from public.shop_payment_events where payment_id = test_payment_id and event_type = 'authorization_void_requested') <> 1 then
    raise exception 'authorization-void-event-not-exactly-once';
  end if;

  -- Mixed seller groups are isolated: accepted Hulu continues, only Store B expires/refunds.
  order_id := gen_random_uuid();
  insert into public.shop_orders(id, profile_id, order_number, status, payment_status, fulfillment_status, delivery_status, subtotal_minor, total_minor)
  values (order_id, buyer_id, test_prefix || '-MULTI', 'pending', 'paid', 'awaiting_seller_confirmation', 'not_shipped', 3000, 3000);
  group_id := gen_random_uuid();
  insert into public.shop_order_groups(id, order_id, store_id, status, fulfillment_status, payment_status, subtotal_minor, store_name_snapshot)
  values (group_id, order_id, hulu_store_id, 'created', 'awaiting_seller_confirmation', 'paid', 1000, 'Hulu Store');
  group_two_id := gen_random_uuid();
  insert into public.shop_order_groups(id, order_id, store_id, status, fulfillment_status, payment_status, subtotal_minor, store_name_snapshot)
  values (group_two_id, order_id, other_store_id, 'created', 'awaiting_seller_confirmation', 'paid', 2000, 'Validation Store');
  insert into public.shop_payments(id, order_id, profile_id, provider, method_code, status, amount_minor)
  values (gen_random_uuid(), order_id, buyer_id, 'phase14j_test', 'electronic', 'paid', 3000);
  perform public.adishop_transition_seller_order_group(
    hulu_seller_id, group_id, 'accept', null,
    jsonb_build_object('requestId', test_prefix || '-multi-accept', 'expectedVersion', 1)
  );
  update public.shop_order_groups set seller_response_required_at = clock_timestamp() - interval '1 second' where id = group_two_id;
  perform public.adishop_expire_seller_order_group(group_two_id, 'automatic');
  if (select seller_response_status from public.shop_order_groups where id = group_id) <> 'accepted'
     or (select seller_response_status from public.shop_order_groups where id = group_two_id) <> 'expired' then
    raise exception 'multi-seller-group-isolation-failed';
  end if;
  if (select fulfillment_status from public.shop_orders where id = order_id) <> 'accepted' then
    raise exception 'mixed-parent-summary-failed';
  end if;
  if (select requested_amount from public.shop_refund_requests where order_group_id = group_two_id) <> 2000 then
    raise exception 'mixed-refund-amount-failed';
  end if;

  -- An unrelated seller cannot see ownership through the mutation or change the group.
  order_id := gen_random_uuid();
  insert into public.shop_orders(id, profile_id, order_number, status, payment_status, fulfillment_status, delivery_status, subtotal_minor, total_minor)
  values (order_id, buyer_id, test_prefix || '-ISOLATION', 'pending', 'unpaid', 'awaiting_seller_confirmation', 'not_shipped', 1000, 1000);
  group_id := gen_random_uuid();
  insert into public.shop_order_groups(id, order_id, store_id, status, fulfillment_status, payment_status, subtotal_minor, store_name_snapshot)
  values (group_id, order_id, hulu_store_id, 'created', 'awaiting_seller_confirmation', 'unpaid', 1000, 'Hulu Store');
  denied := false;
  begin
    perform public.adishop_transition_seller_order_group(
      unrelated_profile_id, group_id, 'accept', null,
      jsonb_build_object('requestId', test_prefix || '-foreign', 'expectedVersion', 1)
    );
  exception when others then
    denied := position('seller-order-not-found' in sqlerrm) > 0;
  end;
  if not denied then raise exception 'foreign-seller-was-not-denied'; end if;
  if (select seller_response_status from public.shop_order_groups where id = group_id) <> 'pending' then
    raise exception 'foreign-seller-mutated-order';
  end if;

  -- Late acceptance is always denied and cannot resurrect the expired order.
  update public.shop_order_groups set seller_response_required_at = clock_timestamp() - interval '1 second' where id = group_id;
  perform public.adishop_expire_seller_order_group(group_id, 'automatic');
  denied := false;
  begin
    perform public.adishop_transition_seller_order_group(
      hulu_seller_id, group_id, 'accept', null,
      jsonb_build_object('requestId', test_prefix || '-late', 'expectedVersion', 2)
    );
  exception when others then
    denied := position('seller-response-deadline-expired' in sqlerrm) > 0
      or position('invalid-transition' in sqlerrm) > 0;
  end;
  if not denied then raise exception 'late-accept-was-not-denied'; end if;
  if (select seller_response_status from public.shop_order_groups where id = group_id) <> 'expired' then
    raise exception 'expired-order-was-resurrected';
  end if;

  raise notice 'PHASE_1_4J_TRANSACTIONAL_CONTRACT_TESTS_PASSED';
end;
$validation$;

rollback;
