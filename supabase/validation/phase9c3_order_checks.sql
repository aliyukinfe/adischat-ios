-- Focused synthetic checks. Every mutation rolls back.
begin;
do $$
#variable_conflict use_variable
declare
  buyer_id constant uuid := '40000000-0000-4000-8000-000000000001';
  order_id uuid;
  group_id uuid;
  fixture jsonb;
  result jsonb;
  denied boolean;
  stock_before integer;
  passed integer := 0;
begin
  if has_function_privilege('anon','public.adishop_request_order_cancellation(uuid,uuid,text,text)','execute')
     or has_function_privilege('authenticated','public.adishop_request_order_cancellation(uuid,uuid,text,text)','execute')
     or not has_function_privilege('service_role','public.adishop_request_order_cancellation(uuid,uuid,text,text)','execute') then
    raise exception 'unsafe cancellation grants';
  end if;
  passed := passed + 1;

  if has_table_privilege('anon','public.shop_seller_response_policy','select')
     or has_table_privilege('authenticated','public.shop_seller_response_policy','select')
     or not (select relrowsecurity from pg_class where oid='public.shop_seller_response_policy'::regclass) then
    raise exception 'SLA policy access unsafe'; end if;
  passed := passed + 1;
  denied := false;
  begin update public.shop_seller_response_policy set default_hours=0 where singleton;
  exception when check_violation then denied := true; end;
  if not denied then raise exception 'invalid SLA accepted'; end if;
  passed := passed + 1;

  fixture := public.phase9c3_new_order(interval '5 minutes');
  order_id := (fixture->>'orderId')::uuid; group_id := (fixture->>'groupId')::uuid;
  if abs(extract(epoch from (
    (select seller_response_required_at from public.shop_order_groups where id=group_id) -
    (select created_at from public.shop_orders where id=order_id)))) <> 86400 then
    raise exception 'default deadline does not start at placement';
  end if;
  passed := passed + 1;
  select quantity_available into stock_before from public.shop_inventory;
  result := public.adishop_request_order_cancellation(buyer_id,order_id,'customer_request','test');
  if result->>'status'<>'cancelled' or (select quantity_available from public.shop_inventory) <> stock_before+1
     or (select fulfillment_status from public.shop_order_groups where id=group_id)<>'cancelled'
     or (select count(*) from public.shop_refund_requests r where r.order_id=order_id)<>0 then
    raise exception 'pending COD cancellation failed'; end if;
  passed := passed + 1;
  denied := false;
  begin perform public.adishop_request_order_cancellation(buyer_id,order_id,'customer_request','repeat');
  exception when others then denied := sqlerrm like '%cancellation-not-eligible%'; end;
  if not denied or (select quantity_available from public.shop_inventory)<>stock_before+1
     or (select count(*) from public.shop_order_cancellations c where c.order_id=order_id)<>1 then
    raise exception 'duplicate cancellation changed state'; end if;
  passed := passed + 1;

  fixture := public.phase9c3_new_order(interval '30 minutes 1 second');
  order_id := (fixture->>'orderId')::uuid;
  select quantity_available into stock_before from public.shop_inventory;
  denied := false;
  begin perform public.adishop_request_order_cancellation(buyer_id,order_id,'customer_request','late');
  exception when others then denied := sqlerrm like '%cancellation-not-eligible%'; end;
  if not denied or (select quantity_available from public.shop_inventory)<>stock_before then
    raise exception '30-minute boundary failed'; end if;
  passed := passed + 1;

  fixture := public.phase9c3_new_order(interval '5 minutes');
  order_id := (fixture->>'orderId')::uuid; group_id := (fixture->>'groupId')::uuid;
  if public.phase9c3_accept(group_id)<>'accepted' then raise exception 'accept setup failed'; end if;
  denied := false;
  begin perform public.adishop_request_order_cancellation(buyer_id,order_id,'customer_request','accepted');
  exception when others then denied := sqlerrm like '%cancellation-not-eligible%'; end;
  if not denied then raise exception 'accepted order cancelled'; end if;
  passed := passed + 1;

  foreach result in array array['{"status":"shipped"}'::jsonb,'{"status":"delivered"}'::jsonb,'{"status":"expired"}'::jsonb] loop
    fixture := public.phase9c3_new_order(interval '5 minutes');
    order_id := (fixture->>'orderId')::uuid; group_id := (fixture->>'groupId')::uuid;
    update public.shop_order_groups set fulfillment_status=result->>'status' where id=group_id;
    update public.shop_orders set fulfillment_status=result->>'status',status=result->>'status' where id=order_id;
    denied := false;
    begin perform public.adishop_request_order_cancellation(buyer_id,order_id,'customer_request','late-state');
    exception when others then denied := sqlerrm like '%cancellation-not-eligible%'; end;
    if not denied then raise exception 'terminal order cancelled: %',result; end if;
    passed := passed + 1;
  end loop;

  fixture := public.phase9c3_new_order(interval '5 minutes');
  order_id := (fixture->>'orderId')::uuid;
  denied := false;
  begin perform public.adishop_request_order_cancellation(gen_random_uuid(),order_id,'customer_request','foreign');
  exception when others then denied := sqlerrm like '%order-not-found%'; end;
  if not denied then raise exception 'foreign buyer cancelled'; end if;
  passed := passed + 1;

  fixture := public.phase9c3_new_order(interval '5 minutes','paid');
  order_id := (fixture->>'orderId')::uuid; group_id := (fixture->>'groupId')::uuid;
  perform public.adishop_request_order_cancellation(buyer_id,order_id,'customer_request','paid');
  if (select status from public.shop_payments p where p.order_id=order_id)<>'paid'
     or (select seller_response_refund_status from public.shop_order_groups where id=group_id)<>'refund_pending'
     or (select count(*) from public.shop_refund_requests r where r.order_id=order_id and status='submitted')<>1
     or (select count(*) from public.shop_payment_events e join public.shop_payments p on p.id=e.payment_id where p.order_id=order_id and e.event_type='refund_requested')<>1 then
    raise exception 'paid cancellation did not remain pending'; end if;
  passed := passed + 1;

  fixture := public.phase9c3_new_order(interval '5 minutes','authorized');
  order_id := (fixture->>'orderId')::uuid; group_id := (fixture->>'groupId')::uuid;
  perform public.adishop_request_order_cancellation(buyer_id,order_id,'customer_request','authorized');
  if (select seller_response_refund_status from public.shop_order_groups where id=group_id)<>'authorization_void_pending'
     or (select count(*) from public.shop_payment_events e join public.shop_payments p on p.id=e.payment_id where p.order_id=order_id and e.event_type='authorization_void_requested')<>1 then
    raise exception 'authorized payment void not pending'; end if;
  passed := passed + 1;

  fixture := public.phase9c3_new_order(interval '5 minutes', 'unpaid', 1);
  order_id := (fixture->>'orderId')::uuid; group_id := (fixture->>'groupId')::uuid;
  insert into public.shop_order_items(order_group_id,product_id,quantity)
  values(group_id,'30000000-0000-4000-8000-000000000001',2);
  update public.shop_inventory set quantity_available=quantity_available-2;
  select quantity_available into stock_before from public.shop_inventory;
  perform public.adishop_request_order_cancellation(buyer_id,order_id,'customer_request','duplicate-lines');
  if (select quantity_available from public.shop_inventory)<>stock_before+3 then
    raise exception 'duplicate product lines did not restore all stock'; end if;
  passed := passed + 1;

  update public.shop_seller_response_policy set default_hours=12 where singleton;
  fixture := public.phase9c3_new_order(interval '1 hour');
  order_id := (fixture->>'orderId')::uuid; group_id := (fixture->>'groupId')::uuid;
  if (select seller_response_sla_hours from public.shop_order_groups where id=group_id)<>12
     or extract(epoch from ((select seller_response_required_at from public.shop_order_groups where id=group_id) -
          (select created_at from public.shop_orders where id=order_id)))<>43200 then
    raise exception 'configured deadline incorrect'; end if;
  passed := passed + 1;
  update public.shop_seller_response_policy set default_hours=24 where singleton;

  fixture := public.phase9c3_new_order(interval '25 hours','paid');
  order_id := (fixture->>'orderId')::uuid; group_id := (fixture->>'groupId')::uuid;
  select quantity_available into stock_before from public.shop_inventory;
  result := public.phase9c3_expire(group_id);
  if result->>'status'<>'expired' or result->>'refundStatus'<>'refund_pending'
     or (select quantity_available from public.shop_inventory)<>stock_before+1 then
    raise exception 'pending expiry failed'; end if;
  passed := passed + 1;
  result := public.phase9c3_expire(group_id);
  if result->>'idempotent'<>'true' or (select quantity_available from public.shop_inventory)<>stock_before+1
     or (select count(*) from public.shop_refund_requests r where r.order_id=order_id)<>1 then
    raise exception 'expiry retry not idempotent'; end if;
  passed := passed + 1;

  fixture := public.phase9c3_new_order(interval '5 minutes');
  order_id := (fixture->>'orderId')::uuid; group_id := (fixture->>'groupId')::uuid;
  perform public.phase9c3_accept(group_id);
  update public.shop_order_groups set seller_response_required_at=clock_timestamp()-interval '1 second' where id=group_id;
  result := public.phase9c3_expire(group_id);
  if result->>'idempotent'<>'true' or (select fulfillment_status from public.shop_order_groups where id=group_id)<>'accepted' then
    raise exception 'accepted group expired'; end if;
  passed := passed + 1;

  fixture := public.phase9c3_new_order(interval '5 minutes','paid');
  order_id := (fixture->>'orderId')::uuid;
  delete from public.shop_inventory where product_id='30000000-0000-4000-8000-000000000001';
  denied := false;
  begin perform public.adishop_request_order_cancellation(buyer_id,order_id,'customer_request','missing-inventory');
  exception when others then denied := sqlerrm like '%inventory-release-missing%'; end;
  if not denied or (select status from public.shop_orders where id=order_id)<>'pending'
     or (select count(*) from public.shop_refund_requests r where r.order_id=order_id)<>0
     or (select count(*) from public.shop_payment_events e join public.shop_payments p on p.id=e.payment_id where p.order_id=order_id)<>0 then
    raise exception 'failed inventory release made partial writes'; end if;
  passed := passed + 1;

  raise notice 'phase9c3 synthetic checks passed: %',passed;
end $$;
rollback;
