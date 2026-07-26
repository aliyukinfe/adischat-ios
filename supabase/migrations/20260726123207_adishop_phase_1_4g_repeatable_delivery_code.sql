-- Phase 1.4G hotfix: repeatable buyer delivery-code reveal.
--
-- The buyer must be able to tap "Delivery code" repeatedly and see the same
-- code for the same order. We still do not persist the plaintext code. Instead
-- the code is derived from the order id and a per-order salt, and only the
-- salted verification hash is stored.

create or replace function public.adishop_delivery_code_for_order(
  p_order_id uuid,
  p_salt text
)
returns text
language sql
stable
set search_path = public, extensions
as $$
  select lpad(
    (
      (
        ('x' || substr(encode(digest(p_order_id::text || ':' || p_salt || ':adisgebeya-delivery-v1', 'sha256'), 'hex'), 1, 12))::bit(48)::bigint
      ) % 1000000
    )::text,
    6,
    '0'
  );
$$;

revoke all on function public.adishop_delivery_code_for_order(uuid, text) from public;
revoke execute on function public.adishop_delivery_code_for_order(uuid, text) from anon;
revoke execute on function public.adishop_delivery_code_for_order(uuid, text) from authenticated;
grant execute on function public.adishop_delivery_code_for_order(uuid, text) to service_role;

create or replace function public.adishop_reveal_delivery_code(p_profile_id uuid, p_order_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  order_record public.shop_orders%rowtype;
  code_record public.shop_delivery_confirmation_codes%rowtype;
  code_plain text;
  salt_value text;
begin
  select * into order_record
  from public.shop_orders
  where id = p_order_id and profile_id = p_profile_id
  for update;
  if not found then raise exception 'order-not-found'; end if;

  if order_record.fulfillment_status not in ('handed_to_carrier','shipped','out_for_delivery') then
    raise exception 'delivery-code-not-available';
  end if;

  select * into code_record
  from public.shop_delivery_confirmation_codes
  where order_id = p_order_id
  for update;

  if not found then
    insert into public.shop_delivery_confirmation_codes(order_id)
    values (p_order_id)
    returning * into code_record;
  end if;

  if code_record.verified_at is not null then
    raise exception 'delivery-code-already-used';
  end if;

  salt_value := coalesce(
    code_record.code_salt,
    encode(digest(gen_random_uuid()::text || ':' || random()::text || ':' || clock_timestamp()::text, 'sha256'), 'hex')
  );
  code_plain := public.adishop_delivery_code_for_order(p_order_id, salt_value);

  update public.shop_delivery_confirmation_codes
  set delivery_code = null,
      code_salt = salt_value,
      code_hash = encode(digest(code_plain || ':' || salt_value, 'sha256'), 'hex'),
      revealed_at = coalesce(revealed_at, now()),
      failed_attempts = 0,
      locked_until = null
  where id = code_record.id;

  return jsonb_build_object(
    'orderId', p_order_id,
    'deliveryCode', code_plain,
    'message', 'Show this code to the delivery person only when you receive the order.'
  );
end;
$$;

revoke all on function public.adishop_reveal_delivery_code(uuid, uuid) from public;
revoke execute on function public.adishop_reveal_delivery_code(uuid, uuid) from anon;
revoke execute on function public.adishop_reveal_delivery_code(uuid, uuid) from authenticated;
grant execute on function public.adishop_reveal_delivery_code(uuid, uuid) to service_role;
