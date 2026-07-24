create or replace function public.set_shop_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

do $$
begin
  if exists (
    select 1
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'rls_auto_enable'
      and p.pronargs = 0
  ) then
    revoke execute on function public.rls_auto_enable() from public, anon, authenticated;
  end if;
end $$;

do $$
declare
  t text;
begin
  foreach t in array array[
    'shop_profiles','shop_user_links','shop_store_members','shop_seller_applications',
    'shop_product_variants','shop_inventory','shop_favorites','shop_recently_viewed',
    'shop_carts','shop_cart_items','shop_addresses','shop_orders','shop_order_groups',
    'shop_order_items','shop_order_status_history','shop_notifications','shop_audit_logs'
  ] loop
    if not exists (
      select 1
      from pg_policies
      where schemaname = 'public'
        and tablename = t
        and policyname = 'client_access_deferred'
    ) then
      execute format(
        'create policy client_access_deferred on public.%I for all to anon, authenticated using (false) with check (false)',
        t
      );
    end if;
  end loop;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'shop_reviews' and policyname = 'public_read_published_reviews'
  ) then
    create policy public_read_published_reviews
    on public.shop_reviews for select
    to anon, authenticated
    using (status = 'published');
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'shop_store_reviews' and policyname = 'public_read_published_store_reviews'
  ) then
    create policy public_read_published_store_reviews
    on public.shop_store_reviews for select
    to anon, authenticated
    using (status = 'published');
  end if;
end $$;

create index if not exists shop_addresses_profile_id_idx on public.shop_addresses(profile_id);
create index if not exists shop_audit_logs_actor_profile_id_idx on public.shop_audit_logs(actor_profile_id);
create index if not exists shop_campaign_products_product_id_idx on public.shop_campaign_products(product_id);
create index if not exists shop_cart_items_product_id_idx on public.shop_cart_items(product_id);
create index if not exists shop_cart_items_variant_id_idx on public.shop_cart_items(variant_id);
create index if not exists shop_categories_parent_id_idx on public.shop_categories(parent_id);
create index if not exists shop_favorites_product_id_idx on public.shop_favorites(product_id);
create index if not exists shop_inventory_variant_id_idx on public.shop_inventory(variant_id);
create index if not exists shop_order_groups_store_id_idx on public.shop_order_groups(store_id);
create index if not exists shop_order_items_order_group_id_idx on public.shop_order_items(order_group_id);
create index if not exists shop_order_items_product_id_idx on public.shop_order_items(product_id);
create index if not exists shop_order_items_variant_id_idx on public.shop_order_items(variant_id);
create index if not exists shop_order_status_history_actor_profile_id_idx on public.shop_order_status_history(actor_profile_id);
create index if not exists shop_order_status_history_order_group_id_idx on public.shop_order_status_history(order_group_id);
create index if not exists shop_order_status_history_order_id_idx on public.shop_order_status_history(order_id);
create index if not exists shop_orders_address_id_idx on public.shop_orders(address_id);
create index if not exists shop_product_variants_product_id_idx on public.shop_product_variants(product_id);
create index if not exists shop_products_category_id_idx on public.shop_products(category_id);
create index if not exists shop_recently_viewed_product_id_idx on public.shop_recently_viewed(product_id);
create index if not exists shop_reviews_order_item_id_idx on public.shop_reviews(order_item_id);
create index if not exists shop_reviews_product_id_idx on public.shop_reviews(product_id);
create index if not exists shop_seller_applications_profile_id_idx on public.shop_seller_applications(profile_id);
create index if not exists shop_store_members_profile_id_idx on public.shop_store_members(profile_id);
create index if not exists shop_store_reviews_order_group_id_idx on public.shop_store_reviews(order_group_id);
create index if not exists shop_store_reviews_store_id_idx on public.shop_store_reviews(store_id);
create index if not exists shop_stores_owner_profile_id_idx on public.shop_stores(owner_profile_id);
create index if not exists shop_user_links_shop_profile_id_idx on public.shop_user_links(shop_profile_id);
