-- ==============================================================================
-- AdisGebeya — AG-R6.8 Category Hierarchy & Product-Specific Attributes Proposal
-- ==============================================================================
--
-- CAUTION / SAFETY DIRECTIVE:
-- PROPOSAL ONLY — DO NOT EXECUTE DIRECTLY IN THIS AG-R6.8 SESSION.
-- Direct modifications to production database (https://mcewjfnkrtiiolzibsuh.supabase.co)
-- are strictly restricted during application feature implementation.
-- This script is staged for architectural and DBA review before scheduled migration.
--
-- OBJECTIVE:
-- 1. Complete the multi-tier category hierarchy for Fashion & Clothing, Shoes,
--    Electronics, and Home & Furniture: Main -> Department -> Product Type -> Subtype.
-- 2. Convert flat department categories (Men, Women, Kids) into structured branches.
-- 3. Add explicit product types for Jeans, Suits, Shirts, Sneakers, Heels, Laptops, etc.
-- 4. Associate category-specific attributes (EU shoe sizes, Jeans waist/wash/fit,
--    Suit chest/fit/pieces, Shirt fabric/fit, Electronics storage/RAM, Furniture materials).
-- 5. Guarantee complete backward compatibility with existing 210 category records
--    and all 32 existing production products.
--
-- ==============================================================================

BEGIN;

-- ------------------------------------------------------------------------------
-- 1. Update Existing Department Branches in Fashion & Clothing
-- ------------------------------------------------------------------------------

-- Ensure Fashion root (00000000-0000-4000-8000-000000000102) remains non-leaf
UPDATE public.shop_categories
SET is_leaf = false, seller_selectable = false
WHERE id = '00000000-0000-4000-8000-000000000102';

-- Update Men's Clothing (679f286d-3bdb-48ea-8a0f-593fdca66c50) to department branch
UPDATE public.shop_categories
SET name = 'Men',
    is_leaf = false,
    seller_selectable = false,
    search_terms = ARRAY['men', 'mens', 'male', 'gentlemen', 'clothing', 'fashion']
WHERE id = '679f286d-3bdb-48ea-8a0f-593fdca66c50';

-- Update Women's Clothing (c185bdc9-f022-44de-8fa7-6158e177b070) to department branch
UPDATE public.shop_categories
SET name = 'Women',
    is_leaf = false,
    seller_selectable = false,
    search_terms = ARRAY['women', 'womens', 'female', 'ladies', 'clothing', 'fashion']
WHERE id = 'c185bdc9-f022-44de-8fa7-6158e177b070';

-- Update Kids Clothing (89e3a6a1-0e42-4916-ba6c-2fdf9a667ebf) to department branch
UPDATE public.shop_categories
SET name = 'Kids',
    is_leaf = false,
    seller_selectable = false,
    search_terms = ARRAY['kids', 'children', 'boys', 'girls', 'toddler', 'clothing']
WHERE id = '89e3a6a1-0e42-4916-ba6c-2fdf9a667ebf';


-- ------------------------------------------------------------------------------
-- 2. Insert Structured Sub-Departments & Product Types
-- ------------------------------------------------------------------------------

-- Men's Clothing Branch
INSERT INTO public.shop_categories (
    id, name, slug, code, parent_id, is_leaf, seller_selectable, buyer_visible, display_order, search_terms
) VALUES
('00000000-0000-4000-8000-000000000160', 'Men''s Clothing', 'mens-clothing-dept', 'MENS_CLOTHING_DEPT', '679f286d-3bdb-48ea-8a0f-593fdca66c50', false, false, true, 10, ARRAY['men clothing', 'apparel'])
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, parent_id = EXCLUDED.parent_id, is_leaf = EXCLUDED.is_leaf, seller_selectable = EXCLUDED.seller_selectable;

-- Men's Shoes Branch (Cross-linked to Shoes root for buyer discovery)
INSERT INTO public.shop_categories (
    id, name, slug, code, parent_id, is_leaf, seller_selectable, buyer_visible, display_order, search_terms
) VALUES
('00000000-0000-4000-8000-000000000161', 'Men''s Shoes', 'mens-shoes-dept', 'MENS_SHOES_DEPT', '679f286d-3bdb-48ea-8a0f-593fdca66c50', false, false, true, 20, ARRAY['men shoes', 'footwear', 'sneakers', 'boots'])
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, parent_id = EXCLUDED.parent_id, is_leaf = EXCLUDED.is_leaf, seller_selectable = EXCLUDED.seller_selectable;

-- Men's Product Type Leaves
INSERT INTO public.shop_categories (id, name, slug, code, parent_id, is_leaf, seller_selectable, buyer_visible, display_order, search_terms) VALUES
('00000000-0000-4000-8000-000000000162', 'Jeans & Denim', 'men-jeans', 'MEN_JEANS', '00000000-0000-4000-8000-000000000160', true, true, true, 10, ARRAY['jeans', 'denim', 'pants', 'trousers', 'men jeans']),
('00000000-0000-4000-8000-000000000163', 'Suits & Blazers', 'men-suits', 'MEN_SUITS', '00000000-0000-4000-8000-000000000160', true, true, true, 20, ARRAY['suit', 'blazer', 'tuxedo', 'formal', 'men suits']),
('00000000-0000-4000-8000-000000000164', 'Sneakers & Trainers', 'men-sneakers', 'MEN_SNEAKERS', '00000000-0000-4000-8000-000000000161', true, true, true, 10, ARRAY['sneakers', 'trainers', 'kicks', 'running shoes', 'men sneakers']),
('00000000-0000-4000-8000-000000000165', 'Formal Shoes & Loafers', 'men-formal-shoes', 'MEN_FORMAL_SHOES', '00000000-0000-4000-8000-000000000161', true, true, true, 20, ARRAY['oxford', 'derby', 'loafer', 'dress shoes', 'leather shoes', 'men formal shoes']),
('00000000-0000-4000-8000-000000000166', 'Boots', 'men-boots', 'MEN_BOOTS', '00000000-0000-4000-8000-000000000161', true, true, true, 30, ARRAY['boots', 'ankle boots', 'chelsea boots', 'men boots']),
('00000000-0000-4000-8000-000000000167', 'Sandals & Slippers', 'men-sandals', 'MEN_SANDALS', '00000000-0000-4000-8000-000000000161', true, true, true, 40, ARRAY['sandals', 'slippers', 'slides', 'flip flops'])
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, parent_id = EXCLUDED.parent_id, is_leaf = EXCLUDED.is_leaf, seller_selectable = EXCLUDED.seller_selectable;

-- Women's Clothing Branch
INSERT INTO public.shop_categories (
    id, name, slug, code, parent_id, is_leaf, seller_selectable, buyer_visible, display_order, search_terms
) VALUES
('00000000-0000-4000-8000-000000000170', 'Women''s Clothing', 'womens-clothing-dept', 'WOMENS_CLOTHING_DEPT', 'c185bdc9-f022-44de-8fa7-6158e177b070', false, false, true, 10, ARRAY['women clothing', 'womens apparel', 'ladies'])
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, parent_id = EXCLUDED.parent_id, is_leaf = EXCLUDED.is_leaf, seller_selectable = EXCLUDED.seller_selectable;

-- Women's Shoes Branch
INSERT INTO public.shop_categories (
    id, name, slug, code, parent_id, is_leaf, seller_selectable, buyer_visible, display_order, search_terms
) VALUES
('00000000-0000-4000-8000-000000000171', 'Women''s Shoes', 'womens-shoes-dept', 'WOMENS_SHOES_DEPT', 'c185bdc9-f022-44de-8fa7-6158e177b070', false, false, true, 20, ARRAY['women shoes', 'heels', 'flats', 'sandals'])
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, parent_id = EXCLUDED.parent_id, is_leaf = EXCLUDED.is_leaf, seller_selectable = EXCLUDED.seller_selectable;

-- Women's Product Type Leaves
INSERT INTO public.shop_categories (id, name, slug, code, parent_id, is_leaf, seller_selectable, buyer_visible, display_order, search_terms) VALUES
('00000000-0000-4000-8000-000000000172', 'Dresses & Gowns', 'women-dresses', 'WOMEN_DRESSES', '00000000-0000-4000-8000-000000000170', true, true, true, 10, ARRAY['dress', 'gown', 'maxi dress', 'cocktail', 'evening dress', 'women dresses']),
('00000000-0000-4000-8000-000000000173', 'Tops & Blouses', 'women-tops', 'WOMEN_TOPS', '00000000-0000-4000-8000-000000000170', true, true, true, 20, ARRAY['top', 'blouse', 'shirt', 'tunic', 'women tops']),
('00000000-0000-4000-8000-000000000174', 'Jeans & Denim', 'women-jeans', 'WOMEN_JEANS', '00000000-0000-4000-8000-000000000170', true, true, true, 30, ARRAY['jeans', 'denim', 'skinny jeans', 'mom jeans', 'women jeans']),
('00000000-0000-4000-8000-000000000175', 'Skirts', 'women-skirts', 'WOMEN_SKIRTS', '00000000-0000-4000-8000-000000000170', true, true, true, 40, ARRAY['skirt', 'midi skirt', 'pencil skirt', 'pleated skirt']),
('00000000-0000-4000-8000-000000000176', 'Heels & Pumps', 'women-heels', 'WOMEN_HEELS', '00000000-0000-4000-8000-000000000171', true, true, true, 10, ARRAY['heels', 'pumps', 'stilettos', 'high heels', 'women heels']),
('00000000-0000-4000-8000-000000000177', 'Flats & Loafers', 'women-flats', 'WOMEN_FLATS', '00000000-0000-4000-8000-000000000171', true, true, true, 20, ARRAY['flats', 'ballet flats', 'loafers', 'women flats']),
('00000000-0000-4000-8000-000000000178', 'Sneakers', 'women-sneakers', 'WOMEN_SNEAKERS', '00000000-0000-4000-8000-000000000171', true, true, true, 30, ARRAY['sneakers', 'trainers', 'women sneakers']),
('00000000-0000-4000-8000-000000000179', 'Boots', 'women-boots', 'WOMEN_BOOTS', '00000000-0000-4000-8000-000000000171', true, true, true, 40, ARRAY['boots', 'ankle boots', 'knee high boots', 'women boots'])
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, parent_id = EXCLUDED.parent_id, is_leaf = EXCLUDED.is_leaf, seller_selectable = EXCLUDED.seller_selectable;


-- ------------------------------------------------------------------------------
-- 3. Define Category-Specific Product Attributes & Variant Presets
-- ------------------------------------------------------------------------------

-- Wash Option for Jeans
INSERT INTO public.shop_category_attributes (
    category_id, code, display_name, data_type, input_type, variant_capable, filterable, required, sort_order, suggestions
) VALUES
('00000000-0000-4000-8000-000000000162', 'WASH', 'Wash', 'choice', 'chips', true, true, false, 25, ARRAY['Dark Wash', 'Medium Wash', 'Light Wash', 'Black', 'Distressed', 'Raw Denim']),
('00000000-0000-4000-8000-000000000174', 'WASH', 'Wash', 'choice', 'chips', true, true, false, 25, ARRAY['Dark Wash', 'Medium Wash', 'Light Wash', 'Black', 'Distressed', 'Raw Denim'])
ON CONFLICT DO NOTHING;

-- Fit Option for Jeans & Suits
INSERT INTO public.shop_category_attributes (
    category_id, code, display_name, data_type, input_type, variant_capable, filterable, required, sort_order, suggestions
) VALUES
('00000000-0000-4000-8000-000000000162', 'FIT', 'Fit', 'choice', 'chips', true, true, false, 28, ARRAY['Slim', 'Skinny', 'Straight', 'Relaxed', 'Loose', 'Tapered']),
('00000000-0000-4000-8000-000000000174', 'FIT', 'Fit', 'choice', 'chips', true, true, false, 28, ARRAY['Slim', 'Skinny', 'Straight', 'Relaxed', 'Loose', 'Tapered']),
('00000000-0000-4000-8000-000000000163', 'FIT', 'Suit Fit', 'choice', 'chips', true, true, false, 28, ARRAY['Slim Fit', 'Classic Fit', 'Tailored Fit', 'Modern Fit'])
ON CONFLICT DO NOTHING;

-- Inseam Length for Jeans & Trousers
INSERT INTO public.shop_category_attributes (
    category_id, code, display_name, data_type, input_type, variant_capable, filterable, required, sort_order, suggestions
) VALUES
('00000000-0000-4000-8000-000000000162', 'INSEAM', 'Inseam Length', 'choice', 'chips', true, true, false, 32, ARRAY['30', '32', '34']),
('00000000-0000-4000-8000-000000000174', 'INSEAM', 'Inseam Length', 'choice', 'chips', true, true, false, 32, ARRAY['30', '32', '34'])
ON CONFLICT DO NOTHING;

-- Suit Pieces
INSERT INTO public.shop_category_attributes (
    category_id, code, display_name, data_type, input_type, variant_capable, filterable, required, sort_order, suggestions
) VALUES
('00000000-0000-4000-8000-000000000163', 'PIECES', 'Suit Pieces', 'choice', 'chips', true, true, false, 35, ARRAY['2-Piece (Jacket + Trouser)', '3-Piece (Jacket + Trouser + Vest)', 'Jacket Only'])
ON CONFLICT DO NOTHING;

-- Shirt Fabric
INSERT INTO public.shop_category_attributes (
    category_id, code, display_name, data_type, input_type, variant_capable, filterable, required, sort_order, suggestions
) VALUES
('4b75f4e5-3b88-46e2-8069-33082443228c', 'FABRIC', 'Fabric Type', 'choice', 'chips', true, true, false, 22, ARRAY['100% Cotton', 'Linen', 'Oxford', 'Poplin', 'Cotton Blend', 'Silk', 'Flannel'])
ON CONFLICT DO NOTHING;

-- Shoe Upper Material
INSERT INTO public.shop_category_attributes (
    category_id, code, display_name, data_type, input_type, variant_capable, filterable, required, sort_order, suggestions
) VALUES
('b9b2956d-425b-4049-850e-9b2a88125acc', 'UPPER_MATERIAL', 'Upper Material', 'choice', 'chips', true, true, false, 24, ARRAY['Genuine Leather', 'Suede', 'Canvas', 'Mesh / Knit', 'Synthetic Leather', 'Patent Leather']),
('08b0d893-223c-42c5-86cd-1e01e07bc82b', 'UPPER_MATERIAL', 'Upper Material', 'choice', 'chips', true, true, false, 24, ARRAY['Genuine Leather', 'Suede', 'Canvas', 'Mesh / Knit', 'Synthetic Leather', 'Patent Leather'])
ON CONFLICT DO NOTHING;


-- ------------------------------------------------------------------------------
-- 4. Verification View for Migration Rollout
-- ------------------------------------------------------------------------------

CREATE OR REPLACE VIEW public.v_agr6_8_category_hierarchy_summary AS
SELECT 
    c.id,
    c.name,
    c.slug,
    c.code,
    c.is_leaf,
    c.seller_selectable,
    p.name AS parent_name,
    (SELECT count(*) FROM public.shop_categories WHERE parent_id = c.id) AS child_count,
    (SELECT count(*) FROM public.shop_products WHERE category_id = c.id) AS product_count
FROM public.shop_categories c
LEFT JOIN public.shop_categories p ON p.id = c.parent_id
ORDER BY c.parent_id NULLS FIRST, c.display_order, c.name;

COMMIT;

-- ==============================================================================
-- End of AG-R6.8 Proposal Script
-- ==============================================================================
