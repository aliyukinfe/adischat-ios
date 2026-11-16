-- Keep the established state machines intact; lock and verify the selected store
-- in the same transaction before invoking them.
create or replace function public.adishop_transition_seller_order_group_scoped(
  p_profile_id uuid, p_order_group_id uuid, p_selected_store_id uuid,
  p_action text, p_reason text default null, p_metadata jsonb default '{}'::jsonb
) returns jsonb
language plpgsql security invoker set search_path = public, pg_temp
as $$
declare actual_store_id uuid;
begin
  select store_id into actual_store_id from public.shop_order_groups
  where id = p_order_group_id for update;
  if not found or p_selected_store_id is null
     or actual_store_id is distinct from p_selected_store_id
     or not public.adishop_profile_can_fulfill_store(p_profile_id, p_selected_store_id) then
    raise exception 'seller-order-not-found';
  end if;
  return public.adishop_transition_seller_order_group(
    p_profile_id, p_order_group_id, p_action, p_reason, p_metadata
  );
end;
$$;

create or replace function public.adishop_verify_delivery_code_scoped(
  p_profile_id uuid, p_order_group_id uuid, p_selected_store_id uuid, p_code text
) returns jsonb
language plpgsql security invoker set search_path = public, pg_temp
as $$
declare actual_store_id uuid;
begin
  select store_id into actual_store_id from public.shop_order_groups
  where id = p_order_group_id for update;
  if not found or p_selected_store_id is null
     or actual_store_id is distinct from p_selected_store_id
     or not public.adishop_profile_can_fulfill_store(p_profile_id, p_selected_store_id) then
    raise exception 'seller-order-not-found';
  end if;
  return public.adishop_verify_delivery_code(p_profile_id, p_order_group_id, p_code);
end;
$$;

create or replace function public.adishop_seller_respond_request_scoped(
  p_profile_id uuid, p_selected_store_id uuid, p_kind text, p_request_id uuid,
  p_message text, p_idempotency_key text, p_expected_version integer
) returns jsonb
language plpgsql security invoker set search_path = public, pg_temp
as $$
declare actual_store_id uuid;
begin
  if p_kind = 'refund' then
    select store_id into actual_store_id from public.shop_refund_requests
    where id = p_request_id for update;
  elsif p_kind = 'exchange' then
    select store_id into actual_store_id from public.shop_exchange_requests
    where id = p_request_id for update;
  else
    raise exception 'seller-request-not-found';
  end if;
  if not found or p_selected_store_id is null
     or actual_store_id is distinct from p_selected_store_id
     or not public.adishop_profile_can_fulfill_store(p_profile_id, p_selected_store_id) then
    raise exception 'seller-request-not-found';
  end if;
  return public.adishop_seller_respond_request(
    p_profile_id, p_kind, p_request_id, p_message, p_idempotency_key, p_expected_version
  );
end;
$$;

revoke all on function public.adishop_transition_seller_order_group_scoped(uuid,uuid,uuid,text,text,jsonb) from public, anon, authenticated;
revoke all on function public.adishop_verify_delivery_code_scoped(uuid,uuid,uuid,text) from public, anon, authenticated;
revoke all on function public.adishop_seller_respond_request_scoped(uuid,uuid,text,uuid,text,text,integer) from public, anon, authenticated;
grant execute on function public.adishop_transition_seller_order_group_scoped(uuid,uuid,uuid,text,text,jsonb) to service_role;
grant execute on function public.adishop_verify_delivery_code_scoped(uuid,uuid,uuid,text) to service_role;
grant execute on function public.adishop_seller_respond_request_scoped(uuid,uuid,text,uuid,text,text,integer) to service_role;
