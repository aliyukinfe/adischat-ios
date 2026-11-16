begin;

insert into public.shop_addresses(
  id, profile_id, label, recipient_name, phone, city, address_line1, is_default
) values (
  '14000000-0000-4000-8000-000000000010',
  '71ff5b90-2ca1-4728-8021-b4e6c66929c4',
  'Validation', 'Controlled Buyer A', '0900000000', 'Addis Ababa',
  'Controlled rollback-only address', false
);

select public.adishop_create_buy_now_checkout_session(
  '71ff5b90-2ca1-4728-8021-b4e6c66929c4',
  'variant-cancel-checkout-20260813',
  'b333e438-87c2-4d17-bbfe-ae8a941966ce',
  '5cad5629-dad3-42bc-a076-5dd7ce3d6920',
  1
);

select public.adishop_confirm_checkout(
  '71ff5b90-2ca1-4728-8021-b4e6c66929c4',
  (select id from public.shop_checkout_sessions where idempotency_key = 'variant-cancel-checkout-20260813'),
  '14000000-0000-4000-8000-000000000010',
  'cash_on_delivery',
  'variant-cancel-confirm-20260813'
);

select public.adishop_request_order_cancellation(
  '71ff5b90-2ca1-4728-8021-b4e6c66929c4',
  (select id from public.shop_orders where confirmation_idempotency_key = 'variant-cancel-confirm-20260813'),
  'customer_request',
  'Controlled exact-variant cancellation validation'
);

do $validation$
declare
  duplicate_rejected boolean := false;
  black_available integer;
  black_reserved integer;
  aggregate_available integer;
  other_total integer;
  snapshot jsonb;
begin
  begin
    perform public.adishop_request_order_cancellation(
      '71ff5b90-2ca1-4728-8021-b4e6c66929c4',
      (select id from public.shop_orders where confirmation_idempotency_key = 'variant-cancel-confirm-20260813'),
      'customer_request',
      'Controlled duplicate cancellation validation'
    );
  exception when others then
    duplicate_rejected := sqlerrm = 'cancellation-not-eligible';
  end;

  select i.quantity_available, i.quantity_reserved
    into black_available, black_reserved
  from public.shop_inventory i
  where i.variant_id = '5cad5629-dad3-42bc-a076-5dd7ce3d6920';

  select p.aggregate_available_quantity into aggregate_available
  from public.shop_products p
  where p.id = 'b333e438-87c2-4d17-bbfe-ae8a941966ce';

  select sum(i.quantity_available) into other_total
  from public.shop_inventory i
  where i.product_id = 'b333e438-87c2-4d17-bbfe-ae8a941966ce'
    and i.variant_id is not null
    and i.variant_id <> '5cad5629-dad3-42bc-a076-5dd7ce3d6920';

  select oi.variant_snapshot into snapshot
  from public.shop_order_items oi
  join public.shop_order_groups og on og.id = oi.order_group_id
  join public.shop_orders o on o.id = og.order_id
  where o.confirmation_idempotency_key = 'variant-cancel-confirm-20260813';

  if black_available <> 9 or black_reserved <> 0 then raise exception 'exact-variant-not-restored'; end if;
  if aggregate_available <> 29 then raise exception 'aggregate-not-restored'; end if;
  if other_total <> 20 then raise exception 'unrelated-variant-changed'; end if;
  if snapshot->'options'->>'COLOR' <> 'Black' then raise exception 'variant-snapshot-missing'; end if;
  if not duplicate_rejected then raise exception 'duplicate-cancellation-not-rejected'; end if;
end;
$validation$;

select 'PASS' as exact_variant_cancellation,
       i.quantity_available, i.quantity_reserved,
       p.aggregate_available_quantity,
       oi.variant_snapshot
from public.shop_inventory i
join public.shop_products p on p.id = i.product_id
join public.shop_order_items oi on oi.variant_id = i.variant_id
join public.shop_order_groups og on og.id = oi.order_group_id
join public.shop_orders o on o.id = og.order_id
where i.variant_id = '5cad5629-dad3-42bc-a076-5dd7ce3d6920'
  and o.confirmation_idempotency_key = 'variant-cancel-confirm-20260813';

rollback;
