-- Phase AG-R2: Safe Idempotent Synthetic Catalog Seeding
-- Target: opkqyvttbyezjbqwkvho (AdisGebeya Staging)
-- Strictly NEVER apply to production (mcewjfnkrtiiolzibsuh).

BEGIN;

-- 1. Ensure Categories exist and are buyer_visible
INSERT INTO public.shop_categories (
  id, name, slug, code, description, level, is_leaf, seller_selectable, buyer_visible, is_active, display_order
) VALUES
  ('91000000-0000-4000-8000-000000000010', 'QA Electronics', 'qa-electronics', 'QA_ELECTRONICS', 'Synthetic QA Electronics Category', 1, true, true, true, true, 10),
  ('91000000-0000-4000-8000-000000000020', 'QA Fashion', 'qa-fashion', 'QA_FASHION', 'Synthetic QA Fashion Category', 1, true, true, true, true, 20),
  ('91000000-0000-4000-8000-000000000030', 'QA Home', 'qa-home', 'QA_HOME', 'Synthetic QA Home Category', 1, true, true, true, true, 30),
  ('91000000-0000-4000-8000-000000000040', 'QA Groceries', 'qa-groceries', 'QA_GROCERIES', 'Synthetic QA Groceries Category', 1, true, true, true, true, 40)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  slug = EXCLUDED.slug,
  code = EXCLUDED.code,
  description = EXCLUDED.description,
  buyer_visible = EXCLUDED.buyer_visible,
  is_active = EXCLUDED.is_active;

-- 2. Store: Associate Synthetic QA Store with active QA seller and approve
UPDATE public.shop_stores SET
  owner_profile_id = 'e1a39afa-59ff-4775-a145-ba2dab90eb13', -- qa.seller.active@example.com
  status = 'active',
  approval_status = 'approved',
  description = 'Authoritative synthetic staging store for QA testing',
  city = 'Addis Ababa',
  updated_at = now()
WHERE id = '92000000-0000-4000-8000-000000000001';

-- 3. Store Review Record in shop_qa_reviewed_stores (reviewed by qa.admin.active@example.com)
INSERT INTO public.shop_qa_reviewed_stores (
  store_id, store_updated_at, reviewed_by_profile_id, reviewed_at, name, description, city
)
SELECT s.id, s.updated_at, '6d51194b-6f0d-44ba-84d1-50e812d972ec', now(), s.name, s.description, s.city
FROM public.shop_stores s
WHERE s.id = '92000000-0000-4000-8000-000000000001'
ON CONFLICT (store_id) DO UPDATE SET
  store_updated_at = EXCLUDED.store_updated_at,
  reviewed_by_profile_id = EXCLUDED.reviewed_by_profile_id,
  reviewed_at = EXCLUDED.reviewed_at,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  city = EXCLUDED.city;

-- 4. 12 Clearly Marked Synthetic Products Across 4 Categories
-- ==========================================================

-- CATEGORY 1: ELECTRONICS (qa-electronics)
-- Product 1: Synthetic QA Smartphone 5G (Published, with variants)
INSERT INTO public.shop_products (
  id, store_id, category_id, category_slug, brand, title, slug,
  short_description, description, price_minor, currency, status,
  moderation_status, has_variants, aggregate_available_quantity, stock_status
) VALUES (
  '93000000-0000-4000-8000-000000000011', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000010', 'qa-electronics', 'QA-Tech',
  'Synthetic QA Smartphone 5G', 'syn-qa-smartphone-5g',
  'Next-gen 5G synthetic smartphone for staging QA tests',
  'High performance synthetic 5G smartphone with AMOLED display and dual SIM support.',
  2800000, 'ETB', 'active', 'approved', true, 23, 'in_stock'
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, slug = EXCLUDED.slug, short_description = EXCLUDED.short_description,
  description = EXCLUDED.description, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, moderation_status = EXCLUDED.moderation_status,
  has_variants = EXCLUDED.has_variants, aggregate_available_quantity = EXCLUDED.aggregate_available_quantity,
  stock_status = EXCLUDED.stock_status, updated_at = now();

-- Product 2: Synthetic QA Wireless Earbuds (Published, no variants)
INSERT INTO public.shop_products (
  id, store_id, category_id, category_slug, brand, title, slug,
  short_description, description, price_minor, currency, status,
  moderation_status, has_variants, aggregate_available_quantity, stock_status
) VALUES (
  '93000000-0000-4000-8000-000000000012', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000010', 'qa-electronics', 'QA-Audio',
  'Synthetic QA Wireless Earbuds', 'syn-qa-wireless-earbuds',
  'True wireless stereo earbuds with active noise cancellation',
  'Bluetooth 5.3 synthetic earbuds with 30-hour battery life and fast charging.',
  350000, 'ETB', 'active', 'approved', false, 25, 'in_stock'
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, slug = EXCLUDED.slug, short_description = EXCLUDED.short_description,
  description = EXCLUDED.description, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, moderation_status = EXCLUDED.moderation_status,
  has_variants = EXCLUDED.has_variants, aggregate_available_quantity = EXCLUDED.aggregate_available_quantity,
  stock_status = EXCLUDED.stock_status, updated_at = now();

-- Product 3: Synthetic QA Power Bank 20000mAh (Pending Review, unapproved)
INSERT INTO public.shop_products (
  id, store_id, category_id, category_slug, brand, title, slug,
  short_description, description, price_minor, currency, status,
  moderation_status, has_variants, aggregate_available_quantity, stock_status
) VALUES (
  '93000000-0000-4000-8000-000000000013', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000010', 'qa-electronics', 'QA-Power',
  'Synthetic QA Power Bank 20000mAh', 'syn-qa-power-bank-20000mah',
  'High capacity 22.5W fast-charging power bank',
  'Dual USB-C and USB-A outputs for reliable synthetic backup power.',
  220000, 'ETB', 'draft', 'pending', false, 30, 'in_stock'
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, slug = EXCLUDED.slug, short_description = EXCLUDED.short_description,
  description = EXCLUDED.description, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, moderation_status = EXCLUDED.moderation_status,
  has_variants = EXCLUDED.has_variants, aggregate_available_quantity = EXCLUDED.aggregate_available_quantity,
  stock_status = EXCLUDED.stock_status, updated_at = now();

-- CATEGORY 2: FASHION (qa-fashion)
-- Product 4: Synthetic QA Cotton T-Shirt (Published, with variants)
INSERT INTO public.shop_products (
  id, store_id, category_id, category_slug, brand, title, slug,
  short_description, description, price_minor, currency, status,
  moderation_status, has_variants, aggregate_available_quantity, stock_status
) VALUES (
  '93000000-0000-4000-8000-000000000021', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000020', 'qa-fashion', 'QA-Apparel',
  'Synthetic QA Cotton T-Shirt', 'syn-qa-cotton-tshirt',
  '100% premium breathable cotton crewneck t-shirt',
  'Classic fit synthetic cotton t-shirt available in multiple sizes.',
  85000, 'ETB', 'active', 'approved', true, 50, 'in_stock'
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, slug = EXCLUDED.slug, short_description = EXCLUDED.short_description,
  description = EXCLUDED.description, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, moderation_status = EXCLUDED.moderation_status,
  has_variants = EXCLUDED.has_variants, aggregate_available_quantity = EXCLUDED.aggregate_available_quantity,
  stock_status = EXCLUDED.stock_status, updated_at = now();

-- Product 5: Synthetic QA Denim Jeans (Published, with variants)
INSERT INTO public.shop_products (
  id, store_id, category_id, category_slug, brand, title, slug,
  short_description, description, price_minor, currency, status,
  moderation_status, has_variants, aggregate_available_quantity, stock_status
) VALUES (
  '93000000-0000-4000-8000-000000000022', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000020', 'qa-fashion', 'QA-Denim',
  'Synthetic QA Denim Jeans', 'syn-qa-denim-jeans',
  'Slim fit durable stretch denim jeans',
  'Quality synthetic indigo denim with reinforced stitching.',
  210000, 'ETB', 'active', 'approved', true, 26, 'in_stock'
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, slug = EXCLUDED.slug, short_description = EXCLUDED.short_description,
  description = EXCLUDED.description, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, moderation_status = EXCLUDED.moderation_status,
  has_variants = EXCLUDED.has_variants, aggregate_available_quantity = EXCLUDED.aggregate_available_quantity,
  stock_status = EXCLUDED.stock_status, updated_at = now();

-- Product 6: Synthetic QA Running Shoes (Pending Review, unapproved)
INSERT INTO public.shop_products (
  id, store_id, category_id, category_slug, brand, title, slug,
  short_description, description, price_minor, currency, status,
  moderation_status, has_variants, aggregate_available_quantity, stock_status
) VALUES (
  '93000000-0000-4000-8000-000000000023', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000020', 'qa-fashion', 'QA-Footwear',
  'Synthetic QA Running Shoes', 'syn-qa-running-shoes',
  'Lightweight synthetic athletic running sneakers',
  'Shock-absorbing cushioned midsole designed for distance training.',
  420000, 'ETB', 'draft', 'pending', false, 18, 'in_stock'
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, slug = EXCLUDED.slug, short_description = EXCLUDED.short_description,
  description = EXCLUDED.description, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, moderation_status = EXCLUDED.moderation_status,
  has_variants = EXCLUDED.has_variants, aggregate_available_quantity = EXCLUDED.aggregate_available_quantity,
  stock_status = EXCLUDED.stock_status, updated_at = now();

-- CATEGORY 3: HOME (qa-home)
-- Product 7: Synthetic QA Electric Kettle 1.8L (Published, no variants)
INSERT INTO public.shop_products (
  id, store_id, category_id, category_slug, brand, title, slug,
  short_description, description, price_minor, currency, status,
  moderation_status, has_variants, aggregate_available_quantity, stock_status
) VALUES (
  '93000000-0000-4000-8000-000000000031', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000030', 'qa-home', 'QA-Home',
  'Synthetic QA Electric Kettle 1.8L', 'syn-qa-electric-kettle-18l',
  'Rapid boil stainless steel electric water kettle',
  '1500W synthetic kettle with auto-shutoff and boil-dry protection.',
  160000, 'ETB', 'active', 'approved', false, 22, 'in_stock'
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, slug = EXCLUDED.slug, short_description = EXCLUDED.short_description,
  description = EXCLUDED.description, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, moderation_status = EXCLUDED.moderation_status,
  has_variants = EXCLUDED.has_variants, aggregate_available_quantity = EXCLUDED.aggregate_available_quantity,
  stock_status = EXCLUDED.stock_status, updated_at = now();

-- Product 8: Synthetic QA French Press Coffee Maker (Published, no variants)
INSERT INTO public.shop_products (
  id, store_id, category_id, category_slug, brand, title, slug,
  short_description, description, price_minor, currency, status,
  moderation_status, has_variants, aggregate_available_quantity, stock_status
) VALUES (
  '93000000-0000-4000-8000-000000000032', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000030', 'qa-home', 'QA-Kitchen',
  'Synthetic QA French Press Coffee Maker', 'syn-qa-french-press',
  'Heat resistant borosilicate glass coffee press 1000ml',
  '4-level filtration system for rich, smooth Ethiopian coffee brew.',
  125000, 'ETB', 'active', 'approved', false, 16, 'in_stock'
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, slug = EXCLUDED.slug, short_description = EXCLUDED.short_description,
  description = EXCLUDED.description, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, moderation_status = EXCLUDED.moderation_status,
  has_variants = EXCLUDED.has_variants, aggregate_available_quantity = EXCLUDED.aggregate_available_quantity,
  stock_status = EXCLUDED.stock_status, updated_at = now();

-- Product 9: Synthetic QA Desk LED Lamp (Draft, unapproved)
INSERT INTO public.shop_products (
  id, store_id, category_id, category_slug, brand, title, slug,
  short_description, description, price_minor, currency, status,
  moderation_status, has_variants, aggregate_available_quantity, stock_status
) VALUES (
  '93000000-0000-4000-8000-000000000033', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000030', 'qa-home', 'QA-Living',
  'Synthetic QA Desk LED Lamp', 'syn-qa-desk-led-lamp',
  'Dimmable eye-care LED desk lamp with USB charging',
  'Adjustable arm and color temperatures for study and office work.',
  95000, 'ETB', 'draft', 'pending', false, 30, 'in_stock'
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, slug = EXCLUDED.slug, short_description = EXCLUDED.short_description,
  description = EXCLUDED.description, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, moderation_status = EXCLUDED.moderation_status,
  has_variants = EXCLUDED.has_variants, aggregate_available_quantity = EXCLUDED.aggregate_available_quantity,
  stock_status = EXCLUDED.stock_status, updated_at = now();

-- CATEGORY 4: GROCERIES (qa-groceries)
-- Product 10: Synthetic QA Ethiopian Organic Honey 1kg (Published, no variants)
INSERT INTO public.shop_products (
  id, store_id, category_id, category_slug, brand, title, slug,
  short_description, description, price_minor, currency, status,
  moderation_status, has_variants, aggregate_available_quantity, stock_status
) VALUES (
  '93000000-0000-4000-8000-000000000041', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000040', 'qa-groceries', 'QA-Organics',
  'Synthetic QA Ethiopian Organic Honey 1kg', 'syn-qa-ethiopian-honey-1kg',
  '100% pure raw unprocessed multi-flora Ethiopian honey',
  'Naturally harvested white multi-flora honey from the highlands of Ethiopia.',
  90000, 'ETB', 'active', 'approved', false, 40, 'in_stock'
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, slug = EXCLUDED.slug, short_description = EXCLUDED.short_description,
  description = EXCLUDED.description, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, moderation_status = EXCLUDED.moderation_status,
  has_variants = EXCLUDED.has_variants, aggregate_available_quantity = EXCLUDED.aggregate_available_quantity,
  stock_status = EXCLUDED.stock_status, updated_at = now();

-- Product 11: Synthetic QA Yirgacheffe Roast Coffee 500g (Published, no variants)
INSERT INTO public.shop_products (
  id, store_id, category_id, category_slug, brand, title, slug,
  short_description, description, price_minor, currency, status,
  moderation_status, has_variants, aggregate_available_quantity, stock_status
) VALUES (
  '93000000-0000-4000-8000-000000000042', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000040', 'qa-groceries', 'QA-Coffee',
  'Synthetic QA Yirgacheffe Roast Coffee 500g', 'syn-qa-yirgacheffe-coffee-500g',
  'Freshly roasted specialty single-origin Yirgacheffe coffee beans',
  'Medium roast with floral aroma, citrus notes, and crisp finish.',
  75000, 'ETB', 'active', 'approved', false, 50, 'in_stock'
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, slug = EXCLUDED.slug, short_description = EXCLUDED.short_description,
  description = EXCLUDED.description, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, moderation_status = EXCLUDED.moderation_status,
  has_variants = EXCLUDED.has_variants, aggregate_available_quantity = EXCLUDED.aggregate_available_quantity,
  stock_status = EXCLUDED.stock_status, updated_at = now();

-- Product 12: Synthetic QA Teff Flour White 25kg (Pending Review, unapproved)
INSERT INTO public.shop_products (
  id, store_id, category_id, category_slug, brand, title, slug,
  short_description, description, price_minor, currency, status,
  moderation_status, has_variants, aggregate_available_quantity, stock_status
) VALUES (
  '93000000-0000-4000-8000-000000000043', '92000000-0000-4000-8000-000000000001',
  '91000000-0000-4000-8000-000000000040', 'qa-groceries', 'QA-Grains',
  'Synthetic QA Teff Flour White 25kg', 'syn-qa-teff-flour-white-25kg',
  'Stone-ground gluten-free organic white teff flour 25kg sack',
  'Pure Grade-A white teff for traditional Ethiopian injera baking.',
  450000, 'ETB', 'draft', 'pending', false, 15, 'in_stock'
) ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, slug = EXCLUDED.slug, short_description = EXCLUDED.short_description,
  description = EXCLUDED.description, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, moderation_status = EXCLUDED.moderation_status,
  has_variants = EXCLUDED.has_variants, aggregate_available_quantity = EXCLUDED.aggregate_available_quantity,
  stock_status = EXCLUDED.stock_status, updated_at = now();

-- 5. Product Variants (for products 1, 4, 5)
-- ===========================================
INSERT INTO public.shop_product_variants (
  id, product_id, sku, title, options, currency, price_minor, status
) VALUES
  -- Variants for Product 1 (Smartphone)
  ('94000000-0000-4000-8000-000000000011', '93000000-0000-4000-8000-000000000011', 'QA-PHN-128', '128GB / 8GB RAM', '{"storage":"128GB","ram":"8GB"}'::jsonb, 'ETB', 2800000, 'active'),
  ('94000000-0000-4000-8000-000000000012', '93000000-0000-4000-8000-000000000011', 'QA-PHN-256', '256GB / 12GB RAM', '{"storage":"256GB","ram":"12GB"}'::jsonb, 'ETB', 3400000, 'active'),
  -- Variants for Product 4 (T-Shirt)
  ('94000000-0000-4000-8000-000000000021', '93000000-0000-4000-8000-000000000021', 'QA-TSH-M', 'Size M / Black', '{"size":"M","color":"Black"}'::jsonb, 'ETB', 85000, 'active'),
  ('94000000-0000-4000-8000-000000000022', '93000000-0000-4000-8000-000000000021', 'QA-TSH-L', 'Size L / Black', '{"size":"L","color":"Black"}'::jsonb, 'ETB', 85000, 'active'),
  ('94000000-0000-4000-8000-000000000023', '93000000-0000-4000-8000-000000000021', 'QA-TSH-XL', 'Size XL / Black', '{"size":"XL","color":"Black"}'::jsonb, 'ETB', 90000, 'active'),
  -- Variants for Product 5 (Jeans)
  ('94000000-0000-4000-8000-000000000024', '93000000-0000-4000-8000-000000000022', 'QA-JNS-32', 'Waist 32', '{"waist":"32"}'::jsonb, 'ETB', 210000, 'active'),
  ('94000000-0000-4000-8000-000000000025', '93000000-0000-4000-8000-000000000022', 'QA-JNS-34', 'Waist 34', '{"waist":"34"}'::jsonb, 'ETB', 210000, 'active')
ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title, options = EXCLUDED.options, price_minor = EXCLUDED.price_minor,
  status = EXCLUDED.status, updated_at = now();

-- 6. Inventory Records
-- ====================
-- Product 1 variants inventory
INSERT INTO public.shop_inventory (product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('93000000-0000-4000-8000-000000000011', '94000000-0000-4000-8000-000000000011', 15, 0, 3),
  ('93000000-0000-4000-8000-000000000011', '94000000-0000-4000-8000-000000000012', 8, 0, 2)
ON CONFLICT (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) DO UPDATE SET
  quantity_available = EXCLUDED.quantity_available, quantity_reserved = EXCLUDED.quantity_reserved,
  low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now();

-- Product 2 inventory (no variant)
INSERT INTO public.shop_inventory (product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('93000000-0000-4000-8000-000000000012', NULL, 25, 0, 5)
ON CONFLICT (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) DO UPDATE SET
  quantity_available = EXCLUDED.quantity_available, quantity_reserved = EXCLUDED.quantity_reserved,
  low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now();

-- Product 3 inventory (no variant)
INSERT INTO public.shop_inventory (product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('93000000-0000-4000-8000-000000000013', NULL, 30, 0, 5)
ON CONFLICT (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) DO UPDATE SET
  quantity_available = EXCLUDED.quantity_available, quantity_reserved = EXCLUDED.quantity_reserved,
  low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now();

-- Product 4 variants inventory
INSERT INTO public.shop_inventory (product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('93000000-0000-4000-8000-000000000021', '94000000-0000-4000-8000-000000000021', 20, 0, 4),
  ('93000000-0000-4000-8000-000000000021', '94000000-0000-4000-8000-000000000022', 20, 0, 4),
  ('93000000-0000-4000-8000-000000000021', '94000000-0000-4000-8000-000000000023', 10, 0, 2)
ON CONFLICT (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) DO UPDATE SET
  quantity_available = EXCLUDED.quantity_available, quantity_reserved = EXCLUDED.quantity_reserved,
  low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now();

-- Product 5 variants inventory
INSERT INTO public.shop_inventory (product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('93000000-0000-4000-8000-000000000022', '94000000-0000-4000-8000-000000000024', 12, 0, 3),
  ('93000000-0000-4000-8000-000000000022', '94000000-0000-4000-8000-000000000025', 14, 0, 3)
ON CONFLICT (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) DO UPDATE SET
  quantity_available = EXCLUDED.quantity_available, quantity_reserved = EXCLUDED.quantity_reserved,
  low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now();

-- Product 6 inventory (no variant)
INSERT INTO public.shop_inventory (product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('93000000-0000-4000-8000-000000000023', NULL, 18, 0, 3)
ON CONFLICT (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) DO UPDATE SET
  quantity_available = EXCLUDED.quantity_available, quantity_reserved = EXCLUDED.quantity_reserved,
  low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now();

-- Product 7 inventory (no variant)
INSERT INTO public.shop_inventory (product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('93000000-0000-4000-8000-000000000031', NULL, 22, 0, 4)
ON CONFLICT (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) DO UPDATE SET
  quantity_available = EXCLUDED.quantity_available, quantity_reserved = EXCLUDED.quantity_reserved,
  low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now();

-- Product 8 inventory (no variant)
INSERT INTO public.shop_inventory (product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('93000000-0000-4000-8000-000000000032', NULL, 16, 0, 3)
ON CONFLICT (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) DO UPDATE SET
  quantity_available = EXCLUDED.quantity_available, quantity_reserved = EXCLUDED.quantity_reserved,
  low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now();

-- Product 9 inventory (no variant)
INSERT INTO public.shop_inventory (product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('93000000-0000-4000-8000-000000000033', NULL, 30, 0, 5)
ON CONFLICT (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) DO UPDATE SET
  quantity_available = EXCLUDED.quantity_available, quantity_reserved = EXCLUDED.quantity_reserved,
  low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now();

-- Product 10 inventory (no variant)
INSERT INTO public.shop_inventory (product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('93000000-0000-4000-8000-000000000041', NULL, 40, 0, 5)
ON CONFLICT (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) DO UPDATE SET
  quantity_available = EXCLUDED.quantity_available, quantity_reserved = EXCLUDED.quantity_reserved,
  low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now();

-- Product 11 inventory (no variant)
INSERT INTO public.shop_inventory (product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('93000000-0000-4000-8000-000000000042', NULL, 50, 0, 10)
ON CONFLICT (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) DO UPDATE SET
  quantity_available = EXCLUDED.quantity_available, quantity_reserved = EXCLUDED.quantity_reserved,
  low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now();

-- Product 12 inventory (no variant)
INSERT INTO public.shop_inventory (product_id, variant_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
  ('93000000-0000-4000-8000-000000000043', NULL, 15, 0, 2)
ON CONFLICT (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)) DO UPDATE SET
  quantity_available = EXCLUDED.quantity_available, quantity_reserved = EXCLUDED.quantity_reserved,
  low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now();

-- 7. Review Records for Product Variants (Products 1, 4, 5)
-- ========================================================
-- Variant reviews must match v.updated_at exactly
INSERT INTO public.shop_qa_reviewed_variants (
  variant_id, variant_updated_at, reviewed_by_profile_id, reviewed_at, title, options, price_minor
)
SELECT v.id, v.updated_at, '6d51194b-6f0d-44ba-84d1-50e812d972ec', now(), v.title, v.options, v.price_minor
FROM public.shop_product_variants v
WHERE v.id IN (
  '94000000-0000-4000-8000-000000000011', '94000000-0000-4000-8000-000000000012',
  '94000000-0000-4000-8000-000000000021', '94000000-0000-4000-8000-000000000022', '94000000-0000-4000-8000-000000000023',
  '94000000-0000-4000-8000-000000000024', '94000000-0000-4000-8000-000000000025'
)
ON CONFLICT (variant_id) DO UPDATE SET
  variant_updated_at = EXCLUDED.variant_updated_at,
  reviewed_by_profile_id = EXCLUDED.reviewed_by_profile_id,
  reviewed_at = EXCLUDED.reviewed_at,
  title = EXCLUDED.title,
  options = EXCLUDED.options,
  price_minor = EXCLUDED.price_minor;

-- 8. Review Records for the 8 Published Products in shop_qa_reviewed_catalog
-- =========================================================================
-- Products: 1, 2 (Electronics), 4, 5 (Fashion), 7, 8 (Home), 10, 11 (Groceries)
-- Crucial: product_updated_at and store_updated_at must match current p.updated_at and s.updated_at
INSERT INTO public.shop_qa_reviewed_catalog (
  product_id, product_updated_at, store_updated_at, reviewed_by_profile_id, reviewed_at,
  title, short_description, price_minor, currency
)
SELECT p.id, p.updated_at, s.updated_at, '6d51194b-6f0d-44ba-84d1-50e812d972ec', now(),
  p.title, p.short_description, p.price_minor, p.currency
FROM public.shop_products p
JOIN public.shop_stores s ON s.id = p.store_id
WHERE p.id IN (
  '93000000-0000-4000-8000-000000000011', -- Smartphone 5G
  '93000000-0000-4000-8000-000000000012', -- Wireless Earbuds
  '93000000-0000-4000-8000-000000000021', -- Cotton T-Shirt
  '93000000-0000-4000-8000-000000000022', -- Denim Jeans
  '93000000-0000-4000-8000-000000000031', -- Electric Kettle
  '93000000-0000-4000-8000-000000000032', -- French Press
  '93000000-0000-4000-8000-000000000041', -- Organic Honey
  '93000000-0000-4000-8000-000000000042'  -- Yirgacheffe Coffee
)
ON CONFLICT (product_id) DO UPDATE SET
  product_updated_at = EXCLUDED.product_updated_at,
  store_updated_at = EXCLUDED.store_updated_at,
  reviewed_by_profile_id = EXCLUDED.reviewed_by_profile_id,
  reviewed_at = EXCLUDED.reviewed_at,
  title = EXCLUDED.title,
  short_description = EXCLUDED.short_description,
  price_minor = EXCLUDED.price_minor,
  currency = EXCLUDED.currency;

COMMIT;
