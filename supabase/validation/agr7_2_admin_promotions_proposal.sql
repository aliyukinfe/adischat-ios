-- ==============================================================================
-- AdisChat Super App — Phase H2 Administrative Promotions Proposal
-- ==============================================================================
-- IMPORTANT DBA REVIEW NOTICE:
-- PROPOSAL ONLY — DO NOT EXECUTE DIRECTLY ON PRODUCTION/STAGING SUPABASE.
-- STATUS: BLOCKED PENDING SEPARATE ARCHITECT & SECURITY REVIEW.
--
-- This file specifies database structures to support manual administrative
-- promotion of products, stores, restaurants, and community channels onto the
-- Super App Home feed.
-- Manual promotions carry priority over automatic algorithmic rules, support
-- custom campaign banners/copy, and honor active start/end date windows.
-- ==============================================================================

BEGIN;

-- 1. Administrative Promotions Table
CREATE TABLE IF NOT EXISTS public.admin_feed_promotions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_type VARCHAR(32) NOT NULL CHECK (target_type IN ('product', 'store', 'restaurant', 'channel')),
    target_id VARCHAR(128) NOT NULL,
    custom_title VARCHAR(120),
    custom_subtitle VARCHAR(250),
    custom_banner_url TEXT,
    priority INTEGER NOT NULL DEFAULT 100, -- Higher number = higher feed priority
    starts_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ends_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Partial Index for Active Campaigns
CREATE INDEX IF NOT EXISTS idx_admin_feed_promotions_active
ON public.admin_feed_promotions (priority DESC, created_at DESC)
WHERE is_active = true;

-- 3. Row Level Security Policies
ALTER TABLE public.admin_feed_promotions ENABLE ROW LEVEL SECURITY;

-- Public read access for active campaigns within validity window
CREATE POLICY "Public users can view active feed promotions"
ON public.admin_feed_promotions
FOR SELECT
TO authenticated, anon
USING (
    is_active = true 
    AND starts_at <= now() 
    AND (ends_at IS NULL OR ends_at > now())
);

-- Admin-only modification policy
CREATE POLICY "Admins can manage feed promotions"
ON public.admin_feed_promotions
FOR ALL
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.profiles
        WHERE profiles.id = auth.uid()
        AND profiles.role = 'admin'
    )
);

-- 4. Unified Discovery View
CREATE OR REPLACE VIEW public.v_active_superapp_promotions AS
SELECT 
    p.id AS promotion_id,
    p.target_type,
    p.target_id,
    p.custom_title,
    p.custom_subtitle,
    p.custom_banner_url,
    p.priority,
    p.starts_at,
    p.ends_at,
    p.created_at
FROM public.admin_feed_promotions p
WHERE p.is_active = true
  AND p.starts_at <= now()
  AND (p.ends_at IS NULL OR p.ends_at > now())
ORDER BY p.priority DESC, p.created_at DESC;

COMMIT;
