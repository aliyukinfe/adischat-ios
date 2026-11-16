-- PHASE 9G.13 STAGING DEPLOYMENT PACKAGE: SYNTHETIC SEED DATA
-- Target Project: opkqyvttbyezjbqwkvho (QA Supabase)
-- NEVER APPLY TO PRODUCTION (mcewjfnkrtiiolzibsuh).
-- Dependencies: Requires 02_schema_marketplace_qa.sql to have been applied successfully.
--
-- Notice:
-- 1. All products and stores are seeded in 'draft' / 'pending' moderation status.
-- 2. They do NOT appear in the public catalog until an authorized QA reviewer reviews them.
-- 3. No synthetic Auth users or fake identity profiles are inserted.
-- 4. To associate the store with a specific QA tester on staging:
--      UPDATE public.shop_stores SET owner_profile_id = '<tester_profile_id>'
--      WHERE id = '92000000-0000-4000-8000-000000000001';

begin;

-- Categories
insert into public.shop_categories(id, name, slug, code, is_active, buyer_visible)
values
  ('91000000-0000-4000-8000-000000000001', 'QA Laptops', 'qa-laptops', 'QA_LAPTOPS', true, true),
  ('91000000-0000-4000-8000-000000000002', 'QA Phones', 'qa-phones', 'QA_PHONES', true, true)
on conflict (id) do nothing;

-- Store (unreviewed draft)
insert into public.shop_stores(id, name, slug, city, status, approval_status)
values
  ('92000000-0000-4000-8000-000000000001', 'Synthetic QA Store', 'synthetic-qa-store', 'Addis Ababa', 'draft', 'not_applied')
on conflict (id) do nothing;

-- Product (unreviewed draft, pending moderation)
insert into public.shop_products(
  id, store_id, category_id, title, slug, category_slug, short_description,
  price_minor, currency, status, moderation_status, has_variants, stock_status
) values (
  '93000000-0000-4000-8000-000000000001', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000001', 'Synthetic QA Laptop', 'synthetic-qa-laptop',
  'qa-laptops', 'Synthetic device for QA testing', 5600000, 'ETB', 'draft', 'pending', true, 'in_stock'
) on conflict (id) do nothing;

-- Product Variants
insert into public.shop_product_variants(id, product_id, sku, title, options, currency, price_minor, status)
values
  ('94000000-0000-4000-8000-000000000001', '93000000-0000-4000-8000-000000000001',
   'QA-LAP-8-256', '8GB / 256GB', '{"ram":"8GB","storage":"256GB"}'::jsonb, 'ETB', 5600000, 'active'),
  ('94000000-0000-4000-8000-000000000002', '93000000-0000-4000-8000-000000000001',
   'QA-LAP-16-512', '16GB / 512GB', '{"ram":"16GB","storage":"512GB"}'::jsonb, 'ETB', 6300000, 'active')
on conflict (id) do nothing;

-- Inventory
insert into public.shop_inventory(product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
values
  ('93000000-0000-4000-8000-000000000001', '94000000-0000-4000-8000-000000000001', 5, 0, 1),
  ('93000000-0000-4000-8000-000000000001', '94000000-0000-4000-8000-000000000002', 5, 0, 1)
on conflict (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) do nothing;

commit;
