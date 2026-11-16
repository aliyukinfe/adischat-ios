-- Deny new seller responses after a request reaches a terminal state.
-- Keep the existing response RPC, history, idempotency, and store checks intact.
create or replace function public.adishop_seller_respond_request_scoped(
  p_profile_id uuid, p_selected_store_id uuid, p_kind text, p_request_id uuid,
  p_message text, p_idempotency_key text, p_expected_version integer
) returns jsonb
language plpgsql security invoker set search_path = public, pg_temp
as $$
declare
  actual_store_id uuid;
  request_status text;
begin
  if p_kind = 'refund' then
    select store_id, status into actual_store_id, request_status
    from public.shop_refund_requests where id = p_request_id for update;
  elsif p_kind = 'exchange' then
    select store_id, status into actual_store_id, request_status
    from public.shop_exchange_requests where id = p_request_id for update;
  else
    raise exception 'seller-request-not-found';
  end if;
  if not found or p_selected_store_id is null
     or actual_store_id is distinct from p_selected_store_id
     or not public.adishop_profile_can_fulfill_store(p_profile_id, p_selected_store_id) then
    raise exception 'seller-request-not-found';
  end if;
  if request_status in ('rejected', 'cancelled', 'completed', 'closed') then
    raise exception 'seller-request-closed';
  end if;
  return public.adishop_seller_respond_request(
    p_profile_id, p_kind, p_request_id, p_message, p_idempotency_key, p_expected_version
  );
end;
$$;

revoke all on function public.adishop_seller_respond_request_scoped(uuid,uuid,text,uuid,text,text,integer) from public, anon, authenticated;
grant execute on function public.adishop_seller_respond_request_scoped(uuid,uuid,text,uuid,text,text,integer) to service_role;
