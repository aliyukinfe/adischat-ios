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
            select jsonb_build_object('id', s.id, 'name', s.name, 'status', s.status, 'approval_status', s.approval_status)
            from public.shop_stores s
            where s.id = g.store_id
          ),
          'items', coalesce((select jsonb_agg(to_jsonb(i) order by i.created_at) from public.shop_order_items i where i.order_group_id = g.id), '[]'::jsonb)
        )
        order by g.created_at
      )
      from public.shop_order_groups g
      where g.order_id = o.id
    ), '[]'::jsonb),
    'address', (select to_jsonb(a) from public.shop_order_addresses a where a.order_id = o.id order by a.created_at desc limit 1),
    'payments', coalesce((select jsonb_agg(to_jsonb(p) order by p.created_at desc) from public.shop_payments p where p.order_id = o.id), '[]'::jsonb),
    'history', coalesce((
      select jsonb_agg(jsonb_build_object(
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
      ) order by h.occurred_at, h.created_at)
      from public.shop_order_status_history h
      where h.order_id = o.id
    ), '[]'::jsonb),
    'issues', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', i.id,
        'order_id', i.order_id,
        'order_group_id', i.order_group_id,
        'reason_code', i.reason_code,
        'reason_label', public.adishop_issue_reason_label(i.reason_code),
        'description', i.description,
        'status', i.status,
        'buyer_visible_resolution', i.buyer_visible_resolution,
        'created_at', i.created_at,
        'updated_at', i.updated_at,
        'events', coalesce((
          select jsonb_agg(jsonb_build_object(
            'id', e.id,
            'event_code', e.event_code,
            'previous_status', e.previous_status,
            'next_status', e.next_status,
            'public_message', e.public_message,
            'created_at', e.created_at
          ) order by e.created_at asc)
          from public.shop_order_issue_events e
          where e.issue_id = i.id
        ), '[]'::jsonb)
      ) order by i.created_at desc)
      from public.shop_order_issues i
      where i.order_id = o.id
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
    'deliveryAttempts', coalesce((select jsonb_agg(jsonb_build_object('id', da.id, 'order_group_id', da.order_group_id, 'success', da.success, 'created_at', da.created_at) order by da.created_at desc) from public.shop_delivery_code_attempts da where da.order_id = o.id), '[]'::jsonb),
    'notes', coalesce((select jsonb_agg(jsonb_build_object('id', n.id, 'order_group_id', n.order_group_id, 'visibility', n.visibility, 'note', n.note, 'created_at', n.created_at) order by n.created_at desc) from public.shop_admin_order_notes n where n.order_id = o.id), '[]'::jsonb),
    'audit', coalesce((
      select jsonb_agg(jsonb_build_object('id', a.id, 'action', a.action, 'entity_type', a.entity_type, 'entity_id', a.entity_id, 'created_at', a.created_at) order by a.created_at desc)
      from public.shop_audit_logs a
      where a.entity_id = o.id
         or a.entity_id in (select g2.id from public.shop_order_groups g2 where g2.order_id = o.id)
         or a.entity_id in (select i2.id from public.shop_order_issues i2 where i2.order_id = o.id)
    ), '[]'::jsonb),
    'inventory', coalesce((
      select jsonb_agg(distinct jsonb_build_object('product_id', inv.product_id, 'variant_id', inv.variant_id, 'quantity_available', inv.quantity_available, 'quantity_reserved', inv.quantity_reserved))
      from public.shop_order_groups g3
      join public.shop_order_items i3 on i3.order_group_id = g3.id
      left join public.shop_inventory inv on inv.product_id = i3.product_id and inv.variant_id is not distinct from i3.variant_id
      where g3.order_id = o.id and inv.id is not null
    ), '[]'::jsonb)
  )
  into result
  from public.shop_orders o
  where o.id = p_order_id;

  if result is null then raise exception 'order-not-found'; end if;
  return result;
end;
$$;

create or replace function public.adishop_admin_update_order_issue(
  p_actor_profile_id uuid,
  p_issue_id uuid,
  p_status text,
  p_buyer_visible_message text,
  p_private_note text,
  p_request_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  issue_record public.shop_order_issues%rowtype;
  previous_status text;
  clean_public text;
  clean_private text;
begin
  if not public.adishop_profile_has_permission(p_actor_profile_id, 'order.support.read') then
    raise exception 'admin-forbidden';
  end if;
  if p_status not in ('under_review','awaiting_buyer','awaiting_seller','resolved','rejected','closed') then
    raise exception 'invalid-issue-status';
  end if;
  clean_public := left(trim(coalesce(p_buyer_visible_message, '')), 600);
  clean_private := left(trim(coalesce(p_private_note, '')), 1200);
  if p_status in ('resolved','rejected','closed','awaiting_buyer','awaiting_seller') and length(clean_public) < 3 then
    raise exception 'buyer-message-required';
  end if;
  if length(trim(coalesce(p_request_id, ''))) < 8 then raise exception 'invalid-request-id'; end if;

  select * into issue_record from public.shop_order_issues where id = p_issue_id for update;
  if not found then raise exception 'issue-not-found'; end if;
  previous_status := issue_record.status;

  if previous_status = p_status and exists (
    select 1 from public.shop_order_issue_events
    where issue_id = p_issue_id and metadata->>'requestId' = p_request_id
  ) then
    return jsonb_build_object('issue', row_to_json(issue_record), 'idempotent', true);
  end if;

  update public.shop_order_issues
  set status = p_status,
      buyer_visible_resolution = case when p_status in ('resolved','rejected','closed') then clean_public else buyer_visible_resolution end,
      resolved_at = case when p_status in ('resolved','rejected','closed') then coalesce(resolved_at, now()) else resolved_at end,
      updated_at = now()
  where id = p_issue_id
  returning * into issue_record;

  insert into public.shop_order_issue_events(
    issue_id, actor_profile_id, actor_role, event_code, previous_status, next_status, public_message, private_note, metadata
  )
  values (
    p_issue_id, p_actor_profile_id, 'ADMIN', 'admin_issue.status_change',
    previous_status, p_status, nullif(clean_public, ''), nullif(clean_private, ''),
    jsonb_build_object('requestId', p_request_id)
  );

  perform public.adishop_create_notification(
    issue_record.buyer_profile_id,
    'buyer_issue_status_changed',
    'Buyer Protection update',
    coalesce(nullif(clean_public, ''), 'Your issue status was updated.'),
    jsonb_build_object('orderId', issue_record.order_id, 'issueId', issue_record.id, 'status', p_status)
  );

  if issue_record.seller_profile_id is not null then
    perform public.adishop_create_notification(
      issue_record.seller_profile_id,
      'buyer_issue_status_changed',
      'Buyer Protection update',
      coalesce(nullif(clean_public, ''), 'An order issue status was updated.'),
      jsonb_build_object('orderId', issue_record.order_id, 'orderGroupId', issue_record.order_group_id, 'issueId', issue_record.id, 'status', p_status)
    );
  end if;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (
    p_actor_profile_id,
    'admin_issue.status_change',
    'shop_order_issue',
    p_issue_id,
    jsonb_build_object('previousStatus', previous_status, 'newStatus', p_status, 'requestId', p_request_id)
  );

  return jsonb_build_object('issue', row_to_json(issue_record), 'idempotent', false);
end;
$$;

revoke all on function public.adishop_admin_get_order(uuid, uuid) from public;
revoke execute on function public.adishop_admin_get_order(uuid, uuid) from anon;
revoke execute on function public.adishop_admin_get_order(uuid, uuid) from authenticated;
grant execute on function public.adishop_admin_get_order(uuid, uuid) to service_role;

revoke all on function public.adishop_admin_update_order_issue(uuid, uuid, text, text, text, text) from public;
revoke execute on function public.adishop_admin_update_order_issue(uuid, uuid, text, text, text, text) from anon;
revoke execute on function public.adishop_admin_update_order_issue(uuid, uuid, text, text, text, text) from authenticated;
grant execute on function public.adishop_admin_update_order_issue(uuid, uuid, text, text, text, text) to service_role;
