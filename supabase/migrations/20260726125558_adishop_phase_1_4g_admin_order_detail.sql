-- Phase 1.4G.1: native Admin Order Detail support.
-- Read-only, permission-gated, and intentionally excludes plaintext delivery
-- codes, code hashes, salts, and service-role-only internals.

create or replace function public.adishop_admin_get_order(
  p_profile_id uuid,
  p_order_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  result jsonb;
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'order.support.read') then
    raise exception 'admin-forbidden';
  end if;

  select jsonb_build_object(
    'order', to_jsonb(o),
    'groups', coalesce((
      select jsonb_agg(
        to_jsonb(g) || jsonb_build_object(
          'store', (
            select jsonb_build_object(
              'id', s.id,
              'name', s.name,
              'status', s.status,
              'approval_status', s.approval_status
            )
            from public.shop_stores s
            where s.id = g.store_id
          ),
          'items', coalesce((
            select jsonb_agg(to_jsonb(i) order by i.created_at)
            from public.shop_order_items i
            where i.order_group_id = g.id
          ), '[]'::jsonb)
        )
        order by g.created_at
      )
      from public.shop_order_groups g
      where g.order_id = o.id
    ), '[]'::jsonb),
    'address', (
      select to_jsonb(a)
      from public.shop_order_addresses a
      where a.order_id = o.id
      order by a.created_at desc
      limit 1
    ),
    'payments', coalesce((
      select jsonb_agg(to_jsonb(p) order by p.created_at desc)
      from public.shop_payments p
      where p.order_id = o.id
    ), '[]'::jsonb),
    'history', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'id', h.id,
          'order_id', h.order_id,
          'order_group_id', h.order_group_id,
          'event_code', h.event_code,
          'previous_status', h.previous_status,
          'next_status', h.next_status,
          'public_message', h.public_message,
          'actor_role', h.actor_role,
          'reason', h.reason,
          'occurred_at', h.occurred_at,
          'created_at', h.created_at
        )
        order by h.occurred_at, h.created_at
      )
      from public.shop_order_status_history h
      where h.order_id = o.id
    ), '[]'::jsonb),
    'deliveryCodeStatus', (
      select jsonb_build_object(
        'revealed', dc.revealed_at is not null,
        'verified', dc.verified_at is not null,
        'failed_attempts', coalesce(dc.failed_attempts, 0),
        'locked', dc.locked_until is not null and dc.locked_until > now()
      )
      from public.shop_delivery_confirmation_codes dc
      where dc.order_id = o.id
      limit 1
    ),
    'deliveryAttempts', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'id', da.id,
          'order_group_id', da.order_group_id,
          'success', da.success,
          'created_at', da.created_at
        )
        order by da.created_at desc
      )
      from public.shop_delivery_code_attempts da
      where da.order_id = o.id
    ), '[]'::jsonb),
    'notes', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'id', n.id,
          'order_group_id', n.order_group_id,
          'visibility', n.visibility,
          'note', n.note,
          'created_at', n.created_at
        )
        order by n.created_at desc
      )
      from public.shop_admin_order_notes n
      where n.order_id = o.id
    ), '[]'::jsonb),
    'audit', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'id', a.id,
          'action', a.action,
          'entity_type', a.entity_type,
          'entity_id', a.entity_id,
          'created_at', a.created_at
        )
        order by a.created_at desc
      )
      from public.shop_audit_logs a
      where a.entity_id = o.id
         or a.entity_id in (select g2.id from public.shop_order_groups g2 where g2.order_id = o.id)
    ), '[]'::jsonb),
    'inventory', coalesce((
      select jsonb_agg(
        distinct jsonb_build_object(
          'product_id', inv.product_id,
          'variant_id', inv.variant_id,
          'quantity_available', inv.quantity_available,
          'quantity_reserved', inv.quantity_reserved
        )
      )
      from public.shop_order_groups g3
      join public.shop_order_items i3 on i3.order_group_id = g3.id
      left join public.shop_inventory inv
        on inv.product_id = i3.product_id
       and inv.variant_id is not distinct from i3.variant_id
      where g3.order_id = o.id
        and inv.id is not null
    ), '[]'::jsonb)
  )
  into result
  from public.shop_orders o
  where o.id = p_order_id;

  if result is null then
    raise exception 'order-not-found';
  end if;

  return result;
end;
$$;

revoke all on function public.adishop_admin_get_order(uuid, uuid) from public;
revoke execute on function public.adishop_admin_get_order(uuid, uuid) from anon;
revoke execute on function public.adishop_admin_get_order(uuid, uuid) from authenticated;
grant execute on function public.adishop_admin_get_order(uuid, uuid) to service_role;
