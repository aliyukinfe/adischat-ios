-- shop_order_items belong to an order through shop_order_groups.
-- Repair Phase 1.4H RPCs that incorrectly referenced shop_order_items.order_id.

create or replace function public.adishop_buyer_create_product_review(
  p_profile_id uuid,
  p_order_id uuid,
  p_order_item_id uuid,
  p_rating integer,
  p_title text,
  p_body text,
  p_idempotency_key text,
  p_request_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare item_record record; action_contract jsonb; review_record public.shop_reviews%rowtype; store_owner uuid;
begin
  if p_rating not between 1 and 5 then raise exception 'invalid-rating'; end if;
  if length(trim(coalesce(p_idempotency_key,''))) < 8 then raise exception 'invalid-idempotency-key'; end if;

  select i.*, g.store_id, o.profile_id, o.fulfillment_status, o.status as order_status, p.id as product_id
  into item_record
  from public.shop_order_items i
  join public.shop_order_groups g on g.id = i.order_group_id
  join public.shop_orders o on o.id = g.order_id
  join public.shop_products p on p.id = i.product_id
  where i.id = p_order_item_id and g.order_id = p_order_id and o.profile_id = p_profile_id;
  if not found then raise exception 'order-item-not-found'; end if;

  action_contract := public.adishop_post_delivery_actions(p_profile_id, p_order_id);
  if not coalesce((action_contract->>'can_review_product')::boolean, false) then raise exception 'review-not-eligible'; end if;

  select * into review_record from public.shop_reviews where profile_id = p_profile_id and idempotency_key = p_idempotency_key;
  if found then return jsonb_build_object('review', row_to_json(review_record), 'idempotent', true); end if;

  select * into review_record from public.shop_reviews where profile_id = p_profile_id and order_item_id = p_order_item_id and status <> 'deleted_by_buyer';
  if found then raise exception 'review-already-exists'; end if;

  insert into public.shop_reviews(profile_id, product_id, store_id, order_id, order_item_id, rating, title, body, status, verified_purchase, idempotency_key)
  values (p_profile_id, item_record.product_id, item_record.store_id, p_order_id, p_order_item_id, p_rating, left(trim(coalesce(p_title,'')), 120), left(trim(coalesce(p_body,'')), 2000), 'published', true, p_idempotency_key)
  returning * into review_record;

  insert into public.shop_review_events(review_type, review_id, actor_profile_id, actor_role, event_type, next_status, public_message, request_id)
  values ('product', review_record.id, p_profile_id, 'BUYER', 'created', review_record.status, 'Product review submitted.', p_request_id);

  perform public.adishop_recompute_product_rating(review_record.product_id);
  select owner_profile_id into store_owner from public.shop_stores where id = item_record.store_id;
  if store_owner is not null then
    perform public.adishop_create_notification(store_owner, 'review_received', 'Review received', 'A buyer reviewed one of your products.', jsonb_build_object('reviewId', review_record.id, 'productId', review_record.product_id));
  end if;
  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'buyer_review.create', 'shop_review', review_record.id, jsonb_build_object('orderId', p_order_id, 'requestId', p_request_id));
  return jsonb_build_object('review', row_to_json(review_record), 'idempotent', false);
end;
$$;

create or replace function public.adishop_buyer_create_refund_request(
  p_profile_id uuid,
  p_order_id uuid,
  p_order_item_id uuid,
  p_quantity integer,
  p_reason_code text,
  p_description text,
  p_idempotency_key text,
  p_request_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare item_record record; action_contract jsonb; request_record public.shop_refund_requests%rowtype; amount_value integer; store_owner uuid;
begin
  if p_quantity < 1 then raise exception 'invalid-quantity'; end if;
  if p_reason_code = 'other' and length(trim(coalesce(p_description,''))) < 10 then raise exception 'description-required'; end if;
  select i.*, g.store_id, g.id as group_id, o.currency, s.owner_profile_id
  into item_record
  from public.shop_order_items i
  join public.shop_order_groups g on g.id = i.order_group_id
  join public.shop_orders o on o.id = g.order_id
  join public.shop_stores s on s.id = g.store_id
  where i.id = p_order_item_id and g.order_id = p_order_id and o.profile_id = p_profile_id;
  if not found then raise exception 'order-item-not-found'; end if;
  if p_quantity > item_record.quantity then raise exception 'invalid-quantity'; end if;
  action_contract := public.adishop_post_delivery_actions(p_profile_id, p_order_id);
  if not coalesce((action_contract->>'can_request_refund')::boolean, false) then raise exception 'refund-not-eligible'; end if;
  select * into request_record from public.shop_refund_requests where buyer_profile_id = p_profile_id and idempotency_key = p_idempotency_key;
  if found then return jsonb_build_object('refundRequest', row_to_json(request_record), 'idempotent', true); end if;
  if exists (select 1 from public.shop_exchange_request_items eri join public.shop_exchange_requests er on er.id = eri.exchange_request_id where eri.order_item_id = p_order_item_id and er.status in ('submitted','under_review','awaiting_buyer','awaiting_seller','approved','awaiting_return','replacement_processing','replacement_shipped')) then raise exception 'conflicting-exchange-request'; end if;
  if exists (select 1 from public.shop_refund_request_items rri join public.shop_refund_requests rr on rr.id = rri.refund_request_id where rri.order_item_id = p_order_item_id and rr.status in ('submitted','under_review','awaiting_buyer','awaiting_seller','approved','partially_approved')) then raise exception 'refund-already-active'; end if;
  amount_value := item_record.unit_price_minor * p_quantity;
  insert into public.shop_refund_requests(order_id, order_group_id, buyer_profile_id, store_id, seller_profile_id, reason_code, description, requested_amount, currency, idempotency_key)
  values (p_order_id, item_record.group_id, p_profile_id, item_record.store_id, item_record.owner_profile_id, p_reason_code, left(trim(coalesce(p_description,'')), 1600), amount_value, item_record.currency, p_idempotency_key)
  returning * into request_record;
  insert into public.shop_refund_request_items(refund_request_id, order_item_id, quantity, unit_price, requested_amount)
  values (request_record.id, p_order_item_id, p_quantity, item_record.unit_price_minor, amount_value);
  insert into public.shop_refund_events(refund_request_id, actor_profile_id, actor_role, event_code, next_status, public_message, request_id)
  values (request_record.id, p_profile_id, 'BUYER', 'buyer_refund.create', request_record.status, 'Refund request submitted.', p_request_id);
  if request_record.seller_profile_id is not null then perform public.adishop_create_notification(request_record.seller_profile_id, 'refund_request_received', 'Refund request', 'A buyer submitted a refund request.', jsonb_build_object('refundRequestId', request_record.id, 'orderId', p_order_id)); end if;
  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'buyer_refund.create', 'shop_refund_request', request_record.id, jsonb_build_object('orderId', p_order_id, 'requestId', p_request_id));
  return jsonb_build_object('refundRequest', row_to_json(request_record), 'idempotent', false);
end;
$$;

create or replace function public.adishop_buyer_create_exchange_request(
  p_profile_id uuid,
  p_order_id uuid,
  p_order_item_id uuid,
  p_requested_variant_id uuid,
  p_quantity integer,
  p_reason_code text,
  p_description text,
  p_idempotency_key text,
  p_request_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare item_record record; action_contract jsonb; request_record public.shop_exchange_requests%rowtype;
begin
  if p_quantity < 1 then raise exception 'invalid-quantity'; end if;
  if p_reason_code = 'other' and length(trim(coalesce(p_description,''))) < 10 then raise exception 'description-required'; end if;
  select i.*, g.store_id, g.id as group_id, s.owner_profile_id
  into item_record
  from public.shop_order_items i
  join public.shop_order_groups g on g.id = i.order_group_id
  join public.shop_orders o on o.id = g.order_id
  join public.shop_stores s on s.id = g.store_id
  where i.id = p_order_item_id and g.order_id = p_order_id and o.profile_id = p_profile_id;
  if not found then raise exception 'order-item-not-found'; end if;
  if p_quantity > item_record.quantity then raise exception 'invalid-quantity'; end if;
  if p_requested_variant_id is not null and not exists (select 1 from public.shop_product_variants where id = p_requested_variant_id and product_id = item_record.product_id and status = 'active') then raise exception 'invalid-variant'; end if;
  action_contract := public.adishop_post_delivery_actions(p_profile_id, p_order_id);
  if not coalesce((action_contract->>'can_request_exchange')::boolean, false) then raise exception 'exchange-not-eligible'; end if;
  select * into request_record from public.shop_exchange_requests where buyer_profile_id = p_profile_id and idempotency_key = p_idempotency_key;
  if found then return jsonb_build_object('exchangeRequest', row_to_json(request_record), 'idempotent', true); end if;
  if exists (select 1 from public.shop_refund_request_items rri join public.shop_refund_requests rr on rr.id = rri.refund_request_id where rri.order_item_id = p_order_item_id and rr.status in ('submitted','under_review','awaiting_buyer','awaiting_seller','approved','partially_approved')) then raise exception 'conflicting-refund-request'; end if;
  if exists (select 1 from public.shop_exchange_request_items eri join public.shop_exchange_requests er on er.id = eri.exchange_request_id where eri.order_item_id = p_order_item_id and er.status in ('submitted','under_review','awaiting_buyer','awaiting_seller','approved','awaiting_return','replacement_processing','replacement_shipped')) then raise exception 'exchange-already-active'; end if;
  insert into public.shop_exchange_requests(order_id, order_group_id, buyer_profile_id, store_id, seller_profile_id, reason_code, description, idempotency_key)
  values (p_order_id, item_record.group_id, p_profile_id, item_record.store_id, item_record.owner_profile_id, p_reason_code, left(trim(coalesce(p_description,'')), 1600), p_idempotency_key)
  returning * into request_record;
  insert into public.shop_exchange_request_items(exchange_request_id, order_item_id, requested_variant_id, quantity)
  values (request_record.id, p_order_item_id, p_requested_variant_id, p_quantity);
  insert into public.shop_exchange_events(exchange_request_id, actor_profile_id, actor_role, event_code, next_status, public_message, request_id)
  values (request_record.id, p_profile_id, 'BUYER', 'buyer_exchange.create', request_record.status, 'Exchange request submitted.', p_request_id);
  if request_record.seller_profile_id is not null then perform public.adishop_create_notification(request_record.seller_profile_id, 'exchange_request_received', 'Exchange request', 'A buyer submitted an exchange request.', jsonb_build_object('exchangeRequestId', request_record.id, 'orderId', p_order_id)); end if;
  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'buyer_exchange.create', 'shop_exchange_request', request_record.id, jsonb_build_object('orderId', p_order_id, 'requestId', p_request_id));
  return jsonb_build_object('exchangeRequest', row_to_json(request_record), 'idempotent', false);
end;
$$;

create or replace function public.adishop_buyer_buy_again(p_profile_id uuid, p_order_id uuid, p_request_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare cart_id_value uuid; row_record record; added jsonb := '[]'::jsonb; unavailable jsonb := '[]'::jsonb; price_changed jsonb := '[]'::jsonb; stock_changed jsonb := '[]'::jsonb; current_price integer; available_qty integer;
begin
  if not exists (select 1 from public.shop_orders where id = p_order_id and profile_id = p_profile_id) then raise exception 'order-not-found'; end if;
  select id into cart_id_value from public.shop_carts where profile_id = p_profile_id order by created_at desc limit 1;
  if cart_id_value is null then
    insert into public.shop_carts(profile_id, currency) values (p_profile_id, 'ETB') returning id into cart_id_value;
  end if;
  for row_record in
    select i.product_id, i.variant_id, i.quantity, i.unit_price_minor, p.title, p.status, p.moderation_status, coalesce(v.price_minor, p.price_minor) as price_minor
    from public.shop_order_items i
    join public.shop_order_groups g on g.id = i.order_group_id
    join public.shop_products p on p.id = i.product_id
    left join public.shop_product_variants v on v.id = i.variant_id
    where g.order_id = p_order_id
  loop
    select coalesce(sum(quantity_available - quantity_reserved), 0) into available_qty
    from public.shop_inventory
    where product_id = row_record.product_id and (row_record.variant_id is null or variant_id = row_record.variant_id);
    current_price := row_record.price_minor;
    if row_record.status <> 'active' or row_record.moderation_status <> 'approved' or available_qty < row_record.quantity then
      unavailable := unavailable || jsonb_build_array(jsonb_build_object('productId', row_record.product_id, 'title', row_record.title, 'reason', 'Unavailable or insufficient stock'));
    else
      insert into public.shop_cart_items(cart_id, product_id, variant_id, quantity, unit_price_minor)
      values (cart_id_value, row_record.product_id, row_record.variant_id, row_record.quantity, current_price)
      on conflict (cart_id, product_id, variant_id) do update
        set quantity = least(99, public.shop_cart_items.quantity + excluded.quantity),
            unit_price_minor = excluded.unit_price_minor,
            updated_at = now();
      added := added || jsonb_build_array(jsonb_build_object('productId', row_record.product_id, 'title', row_record.title, 'quantity', row_record.quantity));
      if current_price <> row_record.unit_price_minor then
        price_changed := price_changed || jsonb_build_array(jsonb_build_object('productId', row_record.product_id, 'oldPrice', row_record.unit_price_minor, 'currentPrice', current_price));
      end if;
      if available_qty < row_record.quantity * 2 then
        stock_changed := stock_changed || jsonb_build_array(jsonb_build_object('productId', row_record.product_id, 'available', available_qty));
      end if;
    end if;
  end loop;
  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'buyer.buy_again', 'shop_order', p_order_id, jsonb_build_object('requestId', p_request_id));
  return jsonb_build_object('added_items', added, 'unavailable_items', unavailable, 'price_changed_items', price_changed, 'stock_changed_items', stock_changed);
end;
$$;
