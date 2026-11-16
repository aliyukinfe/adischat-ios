-- Disposable extension of phase9b9_public_column_fixture.sql.
alter table public.shop_stores add column owner_profile_id uuid;
alter table public.shop_stores add column contact_phone text;
alter table public.shop_stores add column address_line text;
alter table public.shop_stores add column region_state text;
alter table public.shop_stores add column refund_policy_text text;
alter table public.shop_stores add column exchange_policy_text text;
alter table public.shop_stores add column shipping_policy_text text;
alter table public.shop_stores add column warranty_policy_text text;
alter table public.shop_stores add column customer_service_policy_text text;
alter table public.shop_stores add column business_hours jsonb;
alter table public.shop_stores add column social_links jsonb;
alter table public.shop_products add column product_type_description text;
alter table public.shop_products add column description text;
alter table public.shop_products add column shipping_details text;
alter table public.shop_products add column return_policy text;
alter table public.shop_product_images add column bucket text;
alter table public.shop_product_images add column store_id uuid;
alter table public.shop_product_variants add column status text not null default 'active';
alter table public.shop_product_variants add column store_id uuid;
alter table public.shop_product_variants add column sku text;
alter table public.shop_product_variants add column option_signature text;
create table public.shop_product_attribute_values (
  id uuid primary key,product_id uuid references public.shop_products(id),
  value_text text,value_number numeric,value_boolean boolean,value_json jsonb,unit text
);
create table public.shop_product_options (
  id uuid primary key,product_id uuid references public.shop_products(id),
  code text,name text,status text
);
create table public.shop_product_option_values (
  id uuid primary key,product_option_id uuid references public.shop_product_options(id),
  code text,value text,display_value text,color_hex text,status text
);
create table public.shop_variant_media (
  variant_id uuid references public.shop_product_variants(id),
  product_image_id uuid references public.shop_product_images(id)
);
create table public.shop_profiles (id uuid primary key,status text);
create table public.fixture_permissions(profile_id uuid,permission text);
create function public.adishop_profile_has_permission(p_profile_id uuid,p_permission text)
returns boolean language sql stable as $$
  select exists(select 1 from public.fixture_permissions
    where profile_id=p_profile_id and permission=p_permission)
$$;
create function public.adishop_product_catalog_payload_description_core(p_product_id uuid)
returns jsonb language sql stable security definer set search_path=public as $$
  select jsonb_build_object('id',p.id,'title',p.title,'description',p.description,
    'options',coalesce((select jsonb_agg(jsonb_build_object('name',o.name))
      from public.shop_product_options o where o.product_id=p.id),'[]'::jsonb),
    'specifications',coalesce((select jsonb_agg(jsonb_build_object('valueText',v.value_text))
      from public.shop_product_attribute_values v where v.product_id=p.id),'[]'::jsonb),
    'variants',coalesce((select jsonb_agg(jsonb_build_object('id',v.id,'title',v.title,
      'options',v.options)) from public.shop_product_variants v
      where v.product_id=p.id),'[]'::jsonb))
  from public.shop_products p where p.id=p_product_id
$$;
update public.shop_stores set owner_profile_id='40000000-0000-4000-8000-000000000001';
update public.shop_product_images set store_id='10000000-0000-4000-8000-000000000001';
insert into public.shop_profiles values
('40000000-0000-4000-8000-000000000001','active'),
('40000000-0000-4000-8000-000000000002','active'),
('40000000-0000-4000-8000-000000000003','active'),
('40000000-0000-4000-8000-000000000004','suspended');
insert into public.fixture_permissions values
('40000000-0000-4000-8000-000000000002','product.moderation.approve'),
('40000000-0000-4000-8000-000000000004','product.moderation.approve');
insert into public.shop_product_variants(id,product_id,title,options,status,store_id)
values ('50000000-0000-4000-8000-000000000001',
  '20000000-0000-4000-8000-000000000001','Reviewed 256 GB','{}','active',
  '10000000-0000-4000-8000-000000000001');
drop policy p on public.shop_products;
drop policy i on public.shop_product_images;
drop policy s on public.shop_stores;
create policy public_read_approved_products on public.shop_products for select
  to anon,authenticated using (status='active' and moderation_status='approved');
create policy public_read_approved_product_images on public.shop_product_images for select
  to anon,authenticated using (deleted_at is null);
create policy public_read_approved_stores on public.shop_stores for select
  to anon,authenticated using (status='active' and approval_status='approved');
