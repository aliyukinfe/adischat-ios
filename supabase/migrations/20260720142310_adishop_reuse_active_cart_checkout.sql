-- Reuse active reserved checkouts for the same profile/cart.
-- This prevents repeated Android checkout taps from stacking inventory reservations.

create or replace function public.adishop_create_checkout_session(
  p_profile_id uuid,
  p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  existing_session public.shop_checkout_sessions%rowtype;
  cart_record public.shop_carts%rowtype;
  checkout_id uuid;
  expires_at_value timestamptz := now() + interval '15 minutes';
  subtotal integer := 0;
  cart_item record;
  inventory_record public.shop_inventory%rowtype;
  payment_methods jsonb := '[]'::jsonb;
begin
  if p_profile_id is null or coalesce(length(trim(p_idempotency_key)), 0) < 8 then
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
      'paymentMethods', payment_methods
    );
  end if;

  select * into cart_record
  from public.shop_carts
  where profile_id = p_profile_id and status = 'active' and currency = 'ETB'
  order by updated_at desc
  limit 1;

  if not found then
    raise exception 'empty-cart';
  end if;

  select * into existing_session
  from public.shop_checkout_sessions
  where profile_id = p_profile_id
    and cart_id = cart_record.id
    and state = 'reserved'
    and expires_at > now()
  order by created_at desc
  limit 1;

  if found then
    insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
    values (
      p_profile_id,
      'checkout.reuse_active',
      'shop_checkout_session',
      existing_session.id,
      jsonb_build_object('cartId', cart_record.id)
    );

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
      'paymentMethods', payment_methods
    );
  end if;

  insert into public.shop_checkout_sessions(profile_id, cart_id, idempotency_key, state, currency, expires_at)
  values (p_profile_id, cart_record.id, p_idempotency_key, 'reserved', cart_record.currency, expires_at_value)
  returning id into checkout_id;

  for cart_item in
    select
      ci.id as cart_item_id,
      ci.product_id,
      ci.variant_id,
      ci.quantity,
      p.store_id,
      p.title,
      p.currency,
      coalesce(v.price_minor, p.price_minor) as price_minor,
      p.status as product_status,
      p.moderation_status,
      p.stock_status,
      p.deleted_at,
      s.name as store_name,
      s.status as store_status,
      s.approval_status as store_approval_status,
      v.title as variant_title,
      v.options as variant_options,
      v.status as variant_status,
      (select image_url from public.shop_product_images pi where pi.product_id = p.id order by pi.is_primary desc, pi.display_order asc limit 1) as image_url
    from public.shop_cart_items ci
    join public.shop_products p on p.id = ci.product_id
    join public.shop_stores s on s.id = p.store_id
    left join public.shop_product_variants v on v.id = ci.variant_id
    where ci.cart_id = cart_record.id
    order by ci.created_at
  loop
    if cart_item.product_status <> 'active'
      or cart_item.moderation_status <> 'approved'
      or cart_item.deleted_at is not null
      or cart_item.store_status <> 'active'
      or cart_item.store_approval_status <> 'approved'
      or cart_item.stock_status = 'out_of_stock'
      or (cart_item.variant_id is not null and cart_item.variant_status <> 'active') then
      raise exception 'unavailable-item';
    end if;

    select * into inventory_record
    from public.shop_inventory
    where product_id = cart_item.product_id
      and ((cart_item.variant_id is null and variant_id is null) or variant_id = cart_item.variant_id)
    for update;

    if not found or inventory_record.quantity_available - inventory_record.quantity_reserved < cart_item.quantity then
      raise exception 'insufficient-stock';
    end if;

    update public.shop_inventory
      set quantity_reserved = quantity_reserved + cart_item.quantity,
          updated_at = now()
      where id = inventory_record.id;

    insert into public.shop_checkout_items(
      checkout_session_id, cart_item_id, product_id, variant_id, store_id, inventory_id,
      quantity, currency, unit_price_minor, line_total_minor, product_title_snapshot,
      variant_snapshot, image_snapshot, store_snapshot
    ) values (
      checkout_id, cart_item.cart_item_id, cart_item.product_id, cart_item.variant_id,
      cart_item.store_id, inventory_record.id, cart_item.quantity, cart_item.currency,
      cart_item.price_minor, cart_item.price_minor * cart_item.quantity, cart_item.title,
      jsonb_build_object('id', cart_item.variant_id, 'title', cart_item.variant_title, 'options', coalesce(cart_item.variant_options, '{}'::jsonb)),
      cart_item.image_url,
      jsonb_build_object('id', cart_item.store_id, 'name', cart_item.store_name)
    );

    insert into public.shop_inventory_reservations(
      checkout_session_id, inventory_id, product_id, variant_id, quantity, status, expires_at
    ) values (
      checkout_id, inventory_record.id, cart_item.product_id, cart_item.variant_id, cart_item.quantity, 'active', expires_at_value
    );

    subtotal := subtotal + (cart_item.price_minor * cart_item.quantity);
  end loop;

  if subtotal <= 0 then
    raise exception 'empty-cart';
  end if;

  update public.shop_checkout_sessions
    set subtotal_minor = subtotal,
        grand_total_minor = subtotal
    where id = checkout_id;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'checkout.create', 'shop_checkout_session', checkout_id, jsonb_build_object('subtotalMinor', subtotal));

  return jsonb_build_object(
    'id', checkout_id,
    'state', 'reserved',
    'currency', cart_record.currency,
    'subtotalMinor', subtotal,
    'discountMinor', 0,
    'shippingMinor', 0,
    'taxMinor', 0,
    'grandTotalMinor', subtotal,
    'expiresAt', expires_at_value,
    'paymentMethods', payment_methods
  );
exception
  when others then
    raise;
end;
$$;

revoke all on function public.adishop_create_checkout_session(uuid, text) from public;
revoke execute on function public.adishop_create_checkout_session(uuid, text) from anon;
revoke execute on function public.adishop_create_checkout_session(uuid, text) from authenticated;
grant execute on function public.adishop_create_checkout_session(uuid, text) to service_role;
