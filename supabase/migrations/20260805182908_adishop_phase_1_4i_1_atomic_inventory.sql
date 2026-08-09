-- Atomic, idempotent inventory updates for the internally authenticated seller API.
create or replace function public.shop_seller_bulk_inventory_update(
  p_actor_profile_id uuid,
  p_store_id uuid,
  p_request_id text,
  p_reason text,
  p_items jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_item jsonb;
  v_inventory public.shop_inventory%rowtype;
  v_quantity integer;
  v_threshold integer;
  v_changed integer := 0;
begin
  if p_actor_profile_id is null or p_store_id is null then
    raise exception 'seller-store-not-found';
  end if;

  if not exists (
    select 1
    from public.shop_stores s
    where s.id = p_store_id
      and s.deleted_at is null
      and (
        s.owner_profile_id = p_actor_profile_id
        or exists (
          select 1
          from public.shop_store_members m
          where m.store_id = s.id
            and m.profile_id = p_actor_profile_id
            and m.status = 'active'
            and m.role in ('owner', 'admin')
        )
      )
  ) then
    raise exception 'seller-store-not-found';
  end if;

  if nullif(btrim(p_request_id), '') is null
     or jsonb_typeof(p_items) <> 'array'
     or jsonb_array_length(p_items) = 0
     or jsonb_array_length(p_items) > 100 then
    raise exception 'invalid-inventory-request';
  end if;

  if exists (
    select 1 from public.shop_inventory_history h
    where h.store_id = p_store_id and h.request_id = p_request_id
  ) then
    return jsonb_build_object('updatedCount', 0, 'idempotent', true);
  end if;

  for v_item in select value from jsonb_array_elements(p_items)
  loop
    if coalesce(v_item ->> 'inventoryId', '') = ''
       or (v_item ->> 'quantityAvailable') !~ '^[0-9]+$'
       or (v_item ->> 'lowStockThreshold') !~ '^[0-9]+$' then
      raise exception 'invalid-inventory-item';
    end if;

    v_quantity := (v_item ->> 'quantityAvailable')::integer;
    v_threshold := (v_item ->> 'lowStockThreshold')::integer;
    if v_quantity > 1000000 or v_threshold > 1000000 then
      raise exception 'invalid-inventory-item';
    end if;

    select i.* into v_inventory
    from public.shop_inventory i
    join public.shop_products p on p.id = i.product_id
    where i.id = (v_item ->> 'inventoryId')::uuid
      and p.store_id = p_store_id
      and p.deleted_at is null
    for update of i;

    if not found then
      raise exception 'inventory-item-not-found';
    end if;
    if v_quantity < v_inventory.quantity_reserved then
      raise exception 'inventory-below-reserved';
    end if;

    if v_quantity is distinct from v_inventory.quantity_available
       or v_threshold is distinct from v_inventory.low_stock_threshold then
      update public.shop_inventory
      set quantity_available = v_quantity,
          low_stock_threshold = v_threshold,
          updated_at = now()
      where id = v_inventory.id;

      insert into public.shop_inventory_history(
        store_id, product_id, variant_id, actor_profile_id,
        previous_quantity, new_quantity, quantity_delta, reason, request_id
      ) values (
        p_store_id, v_inventory.product_id, v_inventory.variant_id, p_actor_profile_id,
        v_inventory.quantity_available, v_quantity,
        v_quantity - v_inventory.quantity_available,
        left(coalesce(nullif(btrim(p_reason), ''), 'manual_adjustment'), 120),
        left(p_request_id, 160)
      );
      v_changed := v_changed + 1;
    end if;
  end loop;

  if v_changed > 0 then
    insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
    values (
      p_actor_profile_id,
      'seller.inventory.bulk_update',
      'shop_store',
      p_store_id,
      jsonb_build_object('requestId', left(p_request_id, 160), 'count', v_changed)
    );
  end if;

  return jsonb_build_object('updatedCount', v_changed, 'idempotent', false);
end;
$$;

revoke all on function public.shop_seller_bulk_inventory_update(uuid, uuid, text, text, jsonb) from public, anon, authenticated;
grant execute on function public.shop_seller_bulk_inventory_update(uuid, uuid, text, text, jsonb) to service_role;
