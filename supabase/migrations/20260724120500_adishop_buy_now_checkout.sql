-- Dedicated Buy Now checkout sessions.
-- This uses the same checkout/session, reservation and confirm-order path, but
-- does not add anything to the customer's persistent cart.

create or replace function public.adishop_create_buy_now_checkout_session(
  p_profile_id uuid,
  p_idempotency_key text,
  p_product_id uuid,
  p_variant_id uuid,
  p_quantity integer
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  existing_session public.shop_checkout_sessions%rowtype;
  product_record record;
  inventory_record public.shop_inventory%rowtype;
  checkout_id uuid;
  expires_at_value timestamptz := now() + interval '15 minutes';
  quantity_value integer := greatest(1, least(99, coalesce(p_quantity, 1)));
  unit_price integer := 0;
  subtotal integer := 0;
  payment_methods jsonb := '[]'::jsonb;
begin
  if p_profile_id is null
    or p_product_id is null
    or coalesce(length(trim(p_idempotency_key)), 0) < 8 then
    raise exception 'invalid-checkout-request';
  end if;

  perform public.release_expired_shop_checkout_reservations();

  select coalesce(jsonb_agg(jsonb_build_object('code', code, 'label', label, 'provider', provider)), '[]'::jsonb)
    into payment_methods
  from public.shop_payment_methods
  where enabled = true;

  select * into existing_session
  from public.shop_checkout_sessions
  where profile_id = p_profile_id and idempotency_key = p_idempotency_key
  order by created_at desc
  limit 1;

  if found then
    return jsonb_build_object(
      'id', existing_session.id,
      'state', existing_session.state,
      'currency', existing_session.currency,
      'subtotalMinor', existing_session.subtotal_minor,
      'discountMinor', existing_session.discount_total_minor,
      'shippingMinor', existing_session.shipping_total_minor,
      'taxMinor', existing_session.tax_total_minor,
      'grandTotalMinor', existing_session.grand_total_minor,
      'expiresAt', existing_session.expires_at,
      'paymentMethods', payment_methods,
      'source', 'BUY_NOW'
    );
  end if;

  select
    p.id as product_id,
    p.store_id,
    p.title,
    p.currency,
    p.price_minor,
    p.status as product_status,
    p.moderation_status,
    p.stock_status,
    p.deleted_at,
    s.name as store_name,
    s.status as store_status,
    s.approval_status as store_approval_status,
    v.id as variant_id,
    v.title as variant_title,
    v.options as variant_options,
    v.price_minor as variant_price_minor,
    v.status as variant_status,
    (select image_url from public.shop_product_images pi where pi.product_id = p.id order by pi.is_primary desc, pi.display_order asc limit 1) as image_url
  into product_record
  from public.shop_products p
  join public.shop_stores s on s.id = p.store_id
  left join public.shop_product_variants v on v.id = p_variant_id and v.product_id = p.id
  where p.id = p_product_id;

  if not found
    or product_record.product_status <> 'active'
    or product_record.moderation_status <> 'approved'
    or product_record.deleted_at is not null
    or product_record.store_status <> 'active'
    or product_record.store_approval_status <> 'approved'
    or product_record.stock_status = 'out_of_stock' then
    raise exception 'unavailable-item';
  end if;

  if p_variant_id is not null and (product_record.variant_id is null or product_record.variant_status <> 'active') then
    raise exception 'unavailable-item';
  end if;

  select * into inventory_record
  from public.shop_inventory
  where product_id = p_product_id
    and ((p_variant_id is null and variant_id is null) or variant_id = p_variant_id)
  for update;

  if not found or inventory_record.quantity_available - inventory_record.quantity_reserved < quantity_value then
    raise exception 'insufficient-stock';
  end if;

  unit_price := coalesce(product_record.variant_price_minor, product_record.price_minor);
  subtotal := unit_price * quantity_value;

  insert into public.shop_checkout_sessions(profile_id, cart_id, idempotency_key, state, currency, subtotal_minor, grand_total_minor, expires_at)
  values (p_profile_id, null, p_idempotency_key, 'reserved', product_record.currency, subtotal, subtotal, expires_at_value)
  returning id into checkout_id;

  update public.shop_inventory
    set quantity_reserved = quantity_reserved + quantity_value,
        updated_at = now()
    where id = inventory_record.id;

  insert into public.shop_checkout_items(
    checkout_session_id, cart_item_id, product_id, variant_id, store_id, inventory_id,
    quantity, currency, unit_price_minor, line_total_minor, product_title_snapshot,
    variant_snapshot, image_snapshot, store_snapshot
  ) values (
    checkout_id, null, product_record.product_id, product_record.variant_id,
    product_record.store_id, inventory_record.id, quantity_value, product_record.currency,
    unit_price, subtotal, product_record.title,
    jsonb_build_object('id', product_record.variant_id, 'title', product_record.variant_title, 'options', coalesce(product_record.variant_options, '{}'::jsonb)),
    product_record.image_url,
    jsonb_build_object('id', product_record.store_id, 'name', product_record.store_name)
  );

  insert into public.shop_inventory_reservations(
    checkout_session_id, inventory_id, product_id, variant_id, quantity, status, expires_at
  ) values (
    checkout_id, inventory_record.id, product_record.product_id, product_record.variant_id, quantity_value, 'active', expires_at_value
  );

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (
    p_profile_id,
    'checkout.buy_now.create',
    'shop_checkout_session',
    checkout_id,
    jsonb_build_object('productId', p_product_id, 'variantId', p_variant_id, 'quantity', quantity_value, 'subtotalMinor', subtotal)
  );

  return jsonb_build_object(
    'id', checkout_id,
    'state', 'reserved',
    'currency', product_record.currency,
    'subtotalMinor', subtotal,
    'discountMinor', 0,
    'shippingMinor', 0,
    'taxMinor', 0,
    'grandTotalMinor', subtotal,
    'expiresAt', expires_at_value,
    'paymentMethods', payment_methods,
    'source', 'BUY_NOW'
  );
end;
$$;

revoke all on function public.adishop_create_buy_now_checkout_session(uuid, text, uuid, uuid, integer) from public;
revoke execute on function public.adishop_create_buy_now_checkout_session(uuid, text, uuid, uuid, integer) from anon;
revoke execute on function public.adishop_create_buy_now_checkout_session(uuid, text, uuid, uuid, integer) from authenticated;
grant execute on function public.adishop_create_buy_now_checkout_session(uuid, text, uuid, uuid, integer) to service_role;
