-- Disposable PostgreSQL 17 fixture overlay; never run against production.
begin;

create role authenticated nologin;
grant usage on schema public to authenticated;

alter table public.shop_stores add column deleted_at timestamptz;
alter table public.shop_stores add constraint shop_stores_status_check
  check (status in ('application_pending','under_review','draft','active','paused','suspended','banned','closed','closed_by_owner','archived','deleted'));
alter table public.shop_stores add constraint shop_stores_approval_status_check
  check (approval_status in ('not_applied','submitted','under_review','approved','rejected','suspended'));
alter table public.shop_profiles add constraint shop_profiles_status_check
  check (status in ('active','suspended','deleted'));

alter table public.shop_products add constraint shop_products_status_check
  check (status in ('draft','active','paused','archived'));
alter table public.shop_products add constraint shop_products_moderation_status_check
  check (moderation_status in ('pending','submitted','under_review','changes_requested','approved','rejected','suspended','archived'));
alter table public.shop_products add constraint shop_products_stock_status_check
  check (stock_status in ('in_stock','low_stock','out_of_stock','preorder'));
alter table public.shop_products add constraint shop_products_price_minor_check check (price_minor >= 0);
alter table public.shop_products add constraint shop_products_currency_check check (currency ~ '^[A-Z]{3}$');
alter table public.shop_products add constraint shop_products_store_slug_key unique (store_id, slug);

alter table public.shop_product_variants add column store_id uuid;
update public.shop_product_variants v set store_id = p.store_id
  from public.shop_products p where p.id = v.product_id;
alter table public.shop_product_variants alter column store_id set not null;
alter table public.shop_product_variants add constraint shop_product_variants_store_id_fkey
  foreign key (store_id) references public.shop_stores(id) on delete cascade;

alter table public.shop_product_images add column store_id uuid;
update public.shop_product_images i set store_id = p.store_id
  from public.shop_products p where p.id = i.product_id;
alter table public.shop_product_images alter column store_id set not null;
alter table public.shop_product_images add constraint shop_product_images_store_id_fkey
  foreign key (store_id) references public.shop_stores(id);
create unique index shop_product_images_one_primary_idx on public.shop_product_images(product_id)
  where is_primary = true and deleted_at is null;

create index shop_products_public_idx on public.shop_products(status, moderation_status, deleted_at, created_at desc);
create index shop_products_category_slug_idx on public.shop_products(category_slug, status, moderation_status, created_at desc);
create index shop_product_moderation_queue_idx on public.shop_products(moderation_status, submitted_at desc, created_at desc);
create index shop_products_seller_store_status_idx on public.shop_products(store_id, moderation_status, updated_at desc);
create index shop_sessions_profile_active_idx on public.shop_sessions(profile_id, expires_at) where revoked_at is null;

grant all on public.shop_profiles, public.shop_sessions, public.shop_roles, public.shop_user_roles,
  public.shop_role_permissions, public.shop_stores, public.shop_products,
  public.shop_product_variants, public.shop_product_images to service_role;
grant select on public.shop_profiles, public.shop_roles, public.shop_user_roles,
  public.shop_role_permissions, public.shop_stores, public.shop_products,
  public.shop_product_variants, public.shop_product_images to anon, authenticated;

create policy public_read_approved_stores on public.shop_stores for select to anon, authenticated
  using (status = 'active' and approval_status = 'approved' and deleted_at is null);
create policy public_read_approved_products on public.shop_products for select to anon, authenticated
  using (status = 'active' and moderation_status = 'approved' and deleted_at is null
    and exists (select 1 from public.shop_stores s where s.id = shop_products.store_id
      and s.status = 'active' and s.approval_status = 'approved' and s.deleted_at is null));
create policy public_read_approved_product_images on public.shop_product_images for select to anon, authenticated
  using (exists (select 1 from public.shop_products p where p.id = shop_product_images.product_id
    and p.status = 'active' and p.moderation_status = 'approved' and p.deleted_at is null));
create policy client_access_deferred_shop_sessions on public.shop_sessions for all to anon, authenticated
  using (false) with check (false);
create policy client_access_deferred_shop_profiles on public.shop_profiles for all to anon, authenticated
  using (false) with check (false);

commit;
