create or replace function public.adishop_seller_respond_request(
  p_profile_id uuid,
  p_kind text,
  p_request_id uuid,
  p_message text,
  p_idempotency_key text,
  p_expected_version integer
)
returns jsonb
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  request_record record;
  duplicate_event boolean := false;
  normalized_message text := btrim(coalesce(p_message, ''));
  normalized_key text := btrim(coalesce(p_idempotency_key, ''));
begin
  if p_kind not in ('refund', 'exchange') then
    raise exception 'seller-request-kind-invalid';
  end if;
  if char_length(normalized_message) < 5 or char_length(normalized_message) > 1600 then
    raise exception 'seller-response-required';
  end if;
  if char_length(normalized_key) < 8 or char_length(normalized_key) > 160 then
    raise exception 'seller-idempotency-key-required';
  end if;

  if p_kind = 'refund' then
    select id, buyer_profile_id, status, version, seller_response, buyer_visible_update
      into request_record
    from public.shop_refund_requests
    where id = p_request_id and seller_profile_id = p_profile_id
    for update;

    if not found then raise exception 'seller-request-not-found'; end if;

    select exists (
      select 1 from public.shop_refund_events
      where refund_request_id = p_request_id
        and actor_profile_id = p_profile_id
        and actor_role = 'SELLER'
        and request_id = normalized_key
    ) into duplicate_event;

    if duplicate_event then
      return jsonb_build_object('idempotent', true, 'version', request_record.version);
    end if;
    if p_expected_version is null or p_expected_version <> request_record.version then
      raise exception 'stale-seller-request';
    end if;
    if request_record.seller_response = normalized_message
       and request_record.buyer_visible_update = normalized_message then
      return jsonb_build_object('idempotent', true, 'version', request_record.version);
    end if;

    update public.shop_refund_requests
    set seller_response = normalized_message,
        buyer_visible_update = normalized_message,
        version = version + 1,
        updated_at = now()
    where id = p_request_id;

    insert into public.shop_refund_events(
      refund_request_id, actor_profile_id, actor_role, event_code,
      previous_status, next_status, public_message, request_id
    ) values (
      p_request_id, p_profile_id, 'SELLER', 'refund.seller_response',
      request_record.status, request_record.status, normalized_message, normalized_key
    );
  else
    select id, buyer_profile_id, status, version, seller_response, buyer_visible_update
      into request_record
    from public.shop_exchange_requests
    where id = p_request_id and seller_profile_id = p_profile_id
    for update;

    if not found then raise exception 'seller-request-not-found'; end if;

    select exists (
      select 1 from public.shop_exchange_events
      where exchange_request_id = p_request_id
        and actor_profile_id = p_profile_id
        and actor_role = 'SELLER'
        and request_id = normalized_key
    ) into duplicate_event;

    if duplicate_event then
      return jsonb_build_object('idempotent', true, 'version', request_record.version);
    end if;
    if p_expected_version is null or p_expected_version <> request_record.version then
      raise exception 'stale-seller-request';
    end if;
    if request_record.seller_response = normalized_message
       and request_record.buyer_visible_update = normalized_message then
      return jsonb_build_object('idempotent', true, 'version', request_record.version);
    end if;

    update public.shop_exchange_requests
    set seller_response = normalized_message,
        buyer_visible_update = normalized_message,
        version = version + 1,
        updated_at = now()
    where id = p_request_id;

    insert into public.shop_exchange_events(
      exchange_request_id, actor_profile_id, actor_role, event_code,
      previous_status, next_status, public_message, request_id
    ) values (
      p_request_id, p_profile_id, 'SELLER', 'exchange.seller_response',
      request_record.status, request_record.status, normalized_message, normalized_key
    );
  end if;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (
    p_profile_id,
    'seller_' || p_kind || '.response',
    'shop_' || p_kind || '_request',
    p_request_id,
    jsonb_build_object('requestId', normalized_key)
  );

  perform public.adishop_create_notification(
    request_record.buyer_profile_id,
    p_kind || '_seller_response',
    'Seller response',
    normalized_message,
    jsonb_build_object('requestId', p_request_id, 'mutationId', normalized_key)
  );

  return jsonb_build_object('idempotent', false, 'version', request_record.version + 1);
end;
$$;

revoke all on function public.adishop_seller_respond_request(uuid, text, uuid, text, text, integer) from public, anon, authenticated;
grant execute on function public.adishop_seller_respond_request(uuid, text, uuid, text, text, integer) to service_role;
