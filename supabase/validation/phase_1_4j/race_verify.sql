do $validation$
begin
  if (select seller_response_status from public.shop_order_groups where id = '14a40000-0000-4000-8000-000000000002'::uuid) <> 'expired' then
    raise exception 'race-terminal-state-invalid';
  end if;
  if (select count(*) from public.shop_order_status_history where order_group_id = '14a40000-0000-4000-8000-000000000002'::uuid and event_code = 'seller_response_expired') <> 1 then
    raise exception 'race-expiration-not-exactly-once';
  end if;
  if (select count(*) from public.shop_order_status_history where order_group_id = '14a40000-0000-4000-8000-000000000002'::uuid and event_code = 'seller_accepted') <> 0 then
    raise exception 'race-produced-both-outcomes';
  end if;
  if (select count(*) from public.shop_notifications where type = 'seller_response_expired' and data->>'orderGroupId' = '14a40000-0000-4000-8000-000000000002') <> 2 then
    raise exception 'race-notifications-not-exactly-once-per-recipient';
  end if;
end;
$validation$;
