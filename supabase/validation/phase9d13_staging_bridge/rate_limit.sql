-- Staging-only. Install only on opkqyvttbyezjbqwkvho.
begin;
create table public.adishop_staging_assertion_rate_limits (
  auth_user_id uuid not null,
  window_start timestamptz not null,
  attempts integer not null check (attempts > 0),
  primary key (auth_user_id, window_start)
);
alter table public.adishop_staging_assertion_rate_limits enable row level security;
revoke all on public.adishop_staging_assertion_rate_limits from public, anon, authenticated;
grant select, insert, update on public.adishop_staging_assertion_rate_limits to service_role;

create function public.adishop_staging_take_assertion_slot(p_auth_user_id uuid)
returns boolean language plpgsql security invoker set search_path = '' as $$
declare v_attempts integer;
begin
  if p_auth_user_id is null then return false; end if;
  insert into public.adishop_staging_assertion_rate_limits(auth_user_id, window_start, attempts)
    values (p_auth_user_id, date_trunc('minute', clock_timestamp()), 1)
    on conflict (auth_user_id, window_start) do update
      set attempts = public.adishop_staging_assertion_rate_limits.attempts + 1
    returning attempts into v_attempts;
  return v_attempts <= 8;
end;
$$;
revoke all on function public.adishop_staging_take_assertion_slot(uuid) from public, anon, authenticated;
grant execute on function public.adishop_staging_take_assertion_slot(uuid) to service_role;
commit;
