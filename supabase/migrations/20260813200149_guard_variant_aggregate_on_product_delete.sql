create or replace function public.adisgebeya_sync_variant_inventory_aggregate()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  target_product_id uuid := coalesce(new.product_id, old.product_id);
  total_available integer;
  total_reserved integer;
begin
  if coalesce(new.variant_id, old.variant_id) is null then
    return coalesce(new, old);
  end if;

  -- Cascading product deletion removes the parent before its inventory rows.
  if not exists (
    select 1
    from public.shop_products p
    where p.id = target_product_id
  ) then
    return coalesce(new, old);
  end if;

  select coalesce(sum(i.quantity_available), 0), coalesce(sum(i.quantity_reserved), 0)
    into total_available, total_reserved
  from public.shop_product_variants v
  join public.shop_inventory i on i.variant_id = v.id
  where v.product_id = target_product_id
    and v.status = 'active';

  insert into public.shop_inventory(
    product_id, variant_id, quantity_available, quantity_reserved,
    low_stock_threshold, inventory_scope
  ) values (
    target_product_id, null, total_available, total_reserved, 3, 'aggregate'
  )
  on conflict (product_id) where variant_id is null do update set
    quantity_available = excluded.quantity_available,
    quantity_reserved = excluded.quantity_reserved,
    inventory_scope = 'aggregate',
    updated_at = now();

  update public.shop_products
  set aggregate_available_quantity = greatest(0, total_available - total_reserved),
      stock_status = case when total_available - total_reserved > 0 then 'in_stock' else 'out_of_stock' end,
      updated_at = now()
  where id = target_product_id
    and has_variants = true;

  return coalesce(new, old);
end;
$$;

revoke all on function public.adisgebeya_sync_variant_inventory_aggregate() from public, anon, authenticated;
grant execute on function public.adisgebeya_sync_variant_inventory_aggregate() to service_role;
