-- ==============================================================================
-- AdisGebeya — AG-R6.9 Seller Submission Reliability & Product Promotions Proposal
-- ==============================================================================
-- IMPORTANT DBA REVIEW NOTICE:
-- PROPOSAL ONLY — DO NOT EXECUTE DIRECTLY IN THIS AG-R6.9 SESSION.
-- This file documents database enhancements for seller promotions and submission
-- moderation workflows. It is prepared for DBA inspection and future migration.
-- No production writes are performed during the AG-R6.9 Android release cycle.
-- ==============================================================================

BEGIN;

-- 1. Promotional Pricing Validation Constraint
-- Ensure that when a compare_at_price_minor is specified, it strictly exceeds the active promotional price.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'chk_shop_products_promotional_pricing'
    ) THEN
        ALTER TABLE public.shop_products
        ADD CONSTRAINT chk_shop_products_promotional_pricing
        CHECK (
            compare_at_price_minor IS NULL OR 
            (compare_at_price_minor > price_minor AND price_minor > 0)
        ) NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'chk_shop_product_variants_promotional_pricing'
    ) THEN
        ALTER TABLE public.shop_product_variants
        ADD CONSTRAINT chk_shop_product_variants_promotional_pricing
        CHECK (
            compare_at_price_minor IS NULL OR 
            (compare_at_price_minor > price_minor AND price_minor > 0)
        ) NOT VALID;
    END IF;
END $$;

-- 2. Partial Index for Fast Discovery of Promotional / Sale Products
-- Buyers frequently filter by discounts ("On Sale", "Deals", "Promotions")
CREATE INDEX IF NOT EXISTS idx_shop_products_active_promotions
ON public.shop_products (store_id, category_id, price_minor)
WHERE status = 'approved' AND compare_at_price_minor IS NOT NULL AND compare_at_price_minor > price_minor;

-- 3. Moderation Submission Audit & Verification View
CREATE OR REPLACE VIEW public.v_agr6_9_promotions_and_submissions AS
SELECT 
    p.id AS product_id,
    p.store_id,
    p.title,
    p.status,
    c.name AS category_name,
    c.slug AS category_slug,
    p.product_type_description,
    p.price_minor / 100 AS active_price_etb,
    p.compare_at_price_minor / 100 AS regular_price_etb,
    CASE 
        WHEN p.compare_at_price_minor IS NOT NULL AND p.compare_at_price_minor > p.price_minor THEN
            ROUND(((p.compare_at_price_minor - p.price_minor)::numeric / p.compare_at_price_minor::numeric) * 100)::integer
        ELSE NULL
    END AS discount_percentage,
    CASE 
        WHEN p.compare_at_price_minor IS NOT NULL AND p.compare_at_price_minor > p.price_minor THEN
            (p.compare_at_price_minor - p.price_minor) / 100
        ELSE 0
    END AS savings_etb,
    COUNT(v.id) AS variant_count,
    p.updated_at
FROM public.shop_products p
LEFT JOIN public.shop_categories c ON c.id = p.category_id
LEFT JOIN public.shop_product_variants v ON v.product_id = p.id AND v.status = 'active'
GROUP BY p.id, p.store_id, p.title, p.status, c.name, c.slug, p.product_type_description, p.price_minor, p.compare_at_price_minor, p.updated_at;

-- 4. Sample Verification Query for DBA Review
-- SELECT * FROM public.v_agr6_9_promotions_and_submissions WHERE discount_percentage > 0 LIMIT 10;

COMMIT;
