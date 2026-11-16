create or replace function public.adishop_buyer_receipt(p_profile_id uuid, p_order_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if not exists (select 1 from public.shop_orders where id = p_order_id and profile_id = p_profile_id) then raise exception 'order-not-found'; end if;
  return (
    select jsonb_build_object(
      'order_number', o.order_number,
      'order_date', o.created_at,
      'currency', o.currency,
      'subtotal_minor', o.subtotal_minor,
      'delivery_fee_minor', o.delivery_fee_minor,
      'discount_minor', o.discount_minor,
      'total_minor', o.total_minor,
      'payment_status', o.payment_status,
      'fulfillment_status', o.fulfillment_status,
      'delivery_status', o.delivery_status,
      'completed_at', o.completed_at,
      'stores', coalesce((
        select jsonb_agg(jsonb_build_object(
          'store_name', g.store_name_snapshot,
          'status', g.fulfillment_status,
          'items', coalesce((
            select jsonb_agg(jsonb_build_object(
              'title', i.title_snapshot,
              'variant', i.variant_snapshot,
              'quantity', i.quantity,
              'unit_price_minor', i.unit_price_minor,
              'line_total_minor', i.line_total_minor
            ) order by i.created_at)
            from public.shop_order_items i
            where i.order_group_id = g.id
          ), '[]'::jsonb)
        ) order by g.created_at)
        from public.shop_order_groups g
        where g.order_id = o.id
      ), '[]'::jsonb),
      'payment', (
        select jsonb_build_object('method_code', p.method_code, 'status', p.status, 'amount_minor', p.amount_minor, 'currency', p.currency)
        from public.shop_payments p
        where p.order_id = o.id
        order by p.created_at desc
        limit 1
      )
    )
    from public.shop_orders o
    where o.id = p_order_id
  );
end;
$$;
