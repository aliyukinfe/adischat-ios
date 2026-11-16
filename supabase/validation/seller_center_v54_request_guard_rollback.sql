-- Execute only with a service-role database connection against the reviewed
-- AdisGebeya project. Every QA record is confined to this transaction.
begin;

do $qa$
declare
  qa_order uuid := gen_random_uuid();
  group_a uuid := gen_random_uuid();
  group_b uuid := gen_random_uuid();
  seller uuid := '6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee';
  foreign_seller uuid := '1dcac349-9ab1-42dd-a9f6-7a11c97c3ea9';
  buyer uuid := '98118bab-7f68-468d-9b28-75bf9ccfe12e';
  store_a uuid := '00000000-0000-4000-8000-000000000201';
  store_b uuid := '64ec0a16-559c-471f-870f-2320db527afd';
  kind text;
  request_status text;
  qa_request_id uuid;
  selected_store uuid;
  other_store uuid;
  before_row jsonb;
  after_row jsonb;
  before_events integer;
  before_notices integer;
  before_audit integer;
  before_inventory bigint;
  before_payments integer;
  result jsonb;
begin
  insert into public.shop_orders(id, profile_id, order_number, status, total_minor)
  values (qa_order, buyer, 'V54QA-' || replace(qa_order::text, '-', ''), 'delivered', 0);
  insert into public.shop_order_groups(id, order_id, store_id, status, fulfillment_status, subtotal_minor)
  values (group_a, qa_order, store_a, 'delivered', 'delivered', 0),
         (group_b, qa_order, store_b, 'delivered', 'delivered', 0);
  select coalesce(sum(quantity_available), 0) into before_inventory from public.shop_inventory;
  select count(*) into before_payments from public.shop_payments where order_id = qa_order;

  for kind in select unnest(array['refund', 'exchange']) loop
    selected_store := case when kind = 'refund' then store_a else store_b end;
    other_store := case when kind = 'refund' then store_b else store_a end;

    for request_status in select unnest(array['rejected', 'cancelled', 'completed', 'closed']) loop
      qa_request_id := gen_random_uuid();
      if kind = 'refund' then
        insert into public.shop_refund_requests(
          id, order_id, order_group_id, buyer_profile_id, store_id, seller_profile_id,
          reason_code, requested_amount, idempotency_key, status
        ) values (
          qa_request_id, qa_order, group_a, buyer, store_a, seller,
          'wrong_item', 0, 'v54-' || qa_request_id::text, request_status
        );
        select to_jsonb(r) into before_row from public.shop_refund_requests r where id = qa_request_id;
        select count(*) into before_events from public.shop_refund_events where refund_request_id = qa_request_id;
      else
        insert into public.shop_exchange_requests(
          id, order_id, order_group_id, buyer_profile_id, store_id, seller_profile_id,
          reason_code, idempotency_key, status
        ) values (
          qa_request_id, qa_order, group_b, buyer, store_b, seller,
          'wrong_item', 'v54-' || qa_request_id::text, request_status
        );
        select to_jsonb(r) into before_row from public.shop_exchange_requests r where id = qa_request_id;
        select count(*) into before_events from public.shop_exchange_events where exchange_request_id = qa_request_id;
      end if;
      select count(*) into before_notices from public.shop_notifications where data->>'requestId' = qa_request_id::text;
      select count(*) into before_audit from public.shop_audit_logs where entity_id = qa_request_id;

      begin
        perform public.adishop_seller_respond_request_scoped(
          seller, selected_store, kind, qa_request_id, 'QA late response', 'v54-late-response', 1
        );
        raise exception 'QA terminal response accepted';
      exception when others then
        if sqlerrm <> 'seller-request-closed' then raise; end if;
      end;
      begin
        perform public.adishop_seller_respond_request_scoped(
          seller, other_store, kind, qa_request_id, 'QA wrong-store response', 'v54-wrong-store', 1
        );
        raise exception 'QA wrong-store response accepted';
      exception when others then
        if sqlerrm <> 'seller-request-not-found' then raise; end if;
      end;
      begin
        perform public.adishop_seller_respond_request_scoped(
          foreign_seller, selected_store, kind, qa_request_id, 'QA foreign response', 'v54-foreign', 1
        );
        raise exception 'QA foreign-seller response accepted';
      exception when others then
        if sqlerrm <> 'seller-request-not-found' then raise; end if;
      end;

      if kind = 'refund' then
        select to_jsonb(r) into after_row from public.shop_refund_requests r where id = qa_request_id;
        if (select count(*) from public.shop_refund_events where refund_request_id = qa_request_id) <> before_events then
          raise exception 'QA denied refund event changed';
        end if;
      else
        select to_jsonb(r) into after_row from public.shop_exchange_requests r where id = qa_request_id;
        if (select count(*) from public.shop_exchange_events where exchange_request_id = qa_request_id) <> before_events then
          raise exception 'QA denied exchange event changed';
        end if;
      end if;
      if before_row is distinct from after_row
        or (select count(*) from public.shop_notifications where data->>'requestId' = qa_request_id::text) <> before_notices
        or (select count(*) from public.shop_audit_logs where entity_id = qa_request_id) <> before_audit then
        raise exception 'QA denied response changed row, timestamp, notice, or audit';
      end if;
    end loop;

    qa_request_id := gen_random_uuid();
    if kind = 'refund' then
      insert into public.shop_refund_requests(
        id, order_id, order_group_id, buyer_profile_id, store_id, seller_profile_id,
        reason_code, requested_amount, idempotency_key, status
      ) values (
        qa_request_id, qa_order, group_a, buyer, store_a, seller,
        'wrong_item', 0, 'v54-' || qa_request_id::text, 'submitted'
      );
    else
      insert into public.shop_exchange_requests(
        id, order_id, order_group_id, buyer_profile_id, store_id, seller_profile_id,
        reason_code, idempotency_key, status
      ) values (
        qa_request_id, qa_order, group_b, buyer, store_b, seller,
        'wrong_item', 'v54-' || qa_request_id::text, 'submitted'
      );
    end if;
    result := public.adishop_seller_respond_request_scoped(
      seller, selected_store, kind, qa_request_id, 'QA valid response', 'v54-open-response', 1
    );
    if result->>'version' <> '2' or result->>'idempotent' <> 'false' then
      raise exception 'QA authorized open response failed';
    end if;
    if kind = 'refund' then
      if (select count(*) from public.shop_refund_events where refund_request_id = qa_request_id and event_code = 'refund.seller_response') <> 1 then
        raise exception 'QA refund response event missing';
      end if;
    else
      if (select count(*) from public.shop_exchange_events where exchange_request_id = qa_request_id and event_code = 'exchange.seller_response') <> 1 then
        raise exception 'QA exchange response event missing';
      end if;
    end if;
  end loop;
  if (select coalesce(sum(quantity_available), 0) from public.shop_inventory) <> before_inventory
    or (select count(*) from public.shop_payments where order_id = qa_order) <> before_payments then
    raise exception 'QA response changed inventory or payment';
  end if;
end $qa$;

rollback;
select count(*) as durable_v54_qa_orders from public.shop_orders where order_number like 'V54QA-%';
