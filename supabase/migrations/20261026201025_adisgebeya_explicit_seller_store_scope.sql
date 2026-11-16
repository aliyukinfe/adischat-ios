-- Applied remotely as migration 20261026201025. Product RPCs share this helper, so a
-- transaction-local store scope lets existing validation and history stay intact.
create or replace function public.adishop_seller_owned_store(p_profile_id uuid)
returns public.shop_stores
language plpgsql
security definer
set search_path = public
as $$
declare
  store_record public.shop_stores%rowtype;
  selected_store_id uuid;
begin
  selected_store_id := nullif(current_setting('adishop.selected_store_id', true), '')::uuid;

  select s.* into store_record
  from public.shop_stores s
  left join public.shop_store_members sm
    on sm.store_id = s.id and sm.profile_id = p_profile_id and sm.status = 'active'
  where s.deleted_at is null
    and s.status = 'active'
    and s.approval_status = 'approved'
    and (s.owner_profile_id = p_profile_id or sm.role in ('owner', 'manager'))
    and (selected_store_id is null or s.id = selected_store_id)
  order by s.created_at asc
  limit 1;

  if not found then raise exception 'seller-store-not-found'; end if;
  if selected_store_id is null and exists (
    select 1 from public.shop_stores s
    left join public.shop_store_members sm
      on sm.store_id = s.id and sm.profile_id = p_profile_id and sm.status = 'active'
    where s.deleted_at is null
      and s.status = 'active'
      and s.approval_status = 'approved'
      and (s.owner_profile_id = p_profile_id or sm.role in ('owner', 'manager'))
      and s.id <> store_record.id
  ) then
    raise exception 'seller-store-selection-required';
  end if;
  return store_record;
end;
$$;

create or replace function public.adishop_seller_list_products_for_store(
  p_profile_id uuid, p_store_id uuid, p_status text default 'all',
  p_limit integer default 30, p_offset integer default 0
)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if p_store_id is null then raise exception 'seller-store-selection-required'; end if;
  perform set_config('adishop.selected_store_id', p_store_id::text, true);
  return public.adishop_seller_list_products(p_profile_id, p_status, p_limit, p_offset);
end;
$$;

create or replace function public.adishop_seller_get_product_for_store(
  p_profile_id uuid, p_store_id uuid, p_product_id uuid
)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if p_store_id is null then raise exception 'seller-store-selection-required'; end if;
  perform set_config('adishop.selected_store_id', p_store_id::text, true);
  return public.adishop_seller_get_product(p_profile_id, p_product_id);
end;
$$;

create or replace function public.adishop_seller_create_product_for_store(
  p_profile_id uuid, p_store_id uuid, p_request_id text default null
)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if p_store_id is null then raise exception 'seller-store-selection-required'; end if;
  perform set_config('adishop.selected_store_id', p_store_id::text, true);
  return public.adishop_seller_create_product(p_profile_id, p_request_id);
end;
$$;

create or replace function public.adishop_seller_update_product_for_store(
  p_profile_id uuid, p_store_id uuid, p_product_id uuid,
  p_payload jsonb, p_request_id text default null
)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if p_store_id is null then raise exception 'seller-store-selection-required'; end if;
  perform set_config('adishop.selected_store_id', p_store_id::text, true);
  return public.adishop_seller_update_product(p_profile_id, p_product_id, p_payload, p_request_id);
end;
$$;

create or replace function public.adishop_seller_save_variant_catalog_for_store(
  p_profile_id uuid, p_store_id uuid, p_product_id uuid,
  p_payload jsonb, p_request_id text default null
)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if p_store_id is null then raise exception 'seller-store-selection-required'; end if;
  perform set_config('adishop.selected_store_id', p_store_id::text, true);
  return public.adishop_seller_save_variant_catalog(p_profile_id, p_product_id, p_payload, p_request_id);
end;
$$;

create or replace function public.adishop_seller_submit_product_for_store(
  p_profile_id uuid, p_store_id uuid, p_product_id uuid, p_request_id text default null
)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if p_store_id is null then raise exception 'seller-store-selection-required'; end if;
  perform set_config('adishop.selected_store_id', p_store_id::text, true);
  return public.adishop_seller_submit_product(p_profile_id, p_product_id, p_request_id);
end;
$$;

revoke all on function public.adishop_seller_list_products_for_store(uuid,uuid,text,integer,integer) from public, anon, authenticated;
revoke all on function public.adishop_seller_get_product_for_store(uuid,uuid,uuid) from public, anon, authenticated;
revoke all on function public.adishop_seller_create_product_for_store(uuid,uuid,text) from public, anon, authenticated;
revoke all on function public.adishop_seller_update_product_for_store(uuid,uuid,uuid,jsonb,text) from public, anon, authenticated;
revoke all on function public.adishop_seller_save_variant_catalog_for_store(uuid,uuid,uuid,jsonb,text) from public, anon, authenticated;
revoke all on function public.adishop_seller_submit_product_for_store(uuid,uuid,uuid,text) from public, anon, authenticated;

grant execute on function public.adishop_seller_list_products_for_store(uuid,uuid,text,integer,integer) to service_role;
grant execute on function public.adishop_seller_get_product_for_store(uuid,uuid,uuid) to service_role;
grant execute on function public.adishop_seller_create_product_for_store(uuid,uuid,text) to service_role;
grant execute on function public.adishop_seller_update_product_for_store(uuid,uuid,uuid,jsonb,text) to service_role;
grant execute on function public.adishop_seller_save_variant_catalog_for_store(uuid,uuid,uuid,jsonb,text) to service_role;
grant execute on function public.adishop_seller_submit_product_for_store(uuid,uuid,uuid,text) to service_role;
