-- PHASE 9G.13 STAGING DEPLOYMENT PACKAGE: NON-DESTRUCTIVE ROLLBACK PROCEDURE
-- Target Project: opkqyvttbyezjbqwkvho (QA Supabase)
-- NEVER APPLY TO PRODUCTION (mcewjfnkrtiiolzibsuh).
--
-- Notice:
-- 1. This script safely removes ONLY the additive marketplace and review objects created in Phase 9G.
-- 2. It DOES NOT touch, alter, or delete ANY identity objects:
--    - shop_profiles
--    - shop_user_links
--    - shop_roles / shop_user_roles
--    - shop_sessions / shop_session_families
--    - shop_audit_logs
--    - auth.*
--    - storage.*
-- 3. Restores staging to the exact pre-Phase 9G foundation state.

begin;

-- 1. Drop Security-Barrier Views
drop view if exists public.shop_qa_public_catalog cascade;
drop view if exists public.shop_qa_public_stores cascade;

-- 2. Drop Triggers on Marketplace Tables
drop trigger if exists shop_qa_store_revision on public.shop_stores;
drop trigger if exists shop_qa_product_revision on public.shop_products;
drop trigger if exists shop_qa_variant_revision on public.shop_product_variants;
drop trigger if exists shop_qa_image_revision on public.shop_product_images;
drop trigger if exists shop_qa_image_invalidate on public.shop_product_images;

-- 3. Drop Trigger Functions
drop function if exists public.shop_qa_touch_review_revision();
drop function if exists public.shop_qa_invalidate_variant_review();
drop function if exists shop_qa_private.touch_image_revision();
drop function if exists shop_qa_private.invalidate_product_for_image();

-- 4. Drop Public & Private RPC Functions
drop function if exists public.adishop_qa_catalog_page(text,text,integer,integer,boolean,text,integer,integer);
drop function if exists public.adishop_qa_product_detail(uuid);
drop function if exists public.adishop_qa_variant_reviewed(uuid,uuid);
drop function if exists public.adishop_qa_active_read_scope(text);
drop function if exists shop_qa_private.active_read_scope(text);
drop function if exists shop_qa_private.locked_actor(text);
drop function if exists public.adishop_qa_review_product(text,uuid,timestamptz,text,text,text);
drop function if exists public.adishop_qa_review_image(text,uuid,integer,text,text);
drop function if exists public.adishop_qa_media_locator(text,uuid);
drop function if exists public.adishop_qa_seller_product_edit(text,uuid,uuid,timestamptz,text,text,integer,uuid);
drop function if exists public.adishop_qa_favorite_action(text,text,uuid);
drop function if exists public.adishop_qa_address_action(text,text,uuid,text,text,text,text,text,text,boolean);
drop function if exists public.adishop_qa_cart_action(text,text,uuid,uuid,uuid,integer);

-- 5. Drop Review & Invalidation Projection Tables
drop table if exists public.shop_qa_reviewed_images cascade;
drop table if exists public.shop_qa_reviewed_variants cascade;
drop table if exists public.shop_qa_reviewed_catalog cascade;
drop table if exists public.shop_qa_reviewed_stores cascade;

-- 6. Drop Core Marketplace Tables
drop table if exists public.shop_order_status_history cascade;
drop table if exists public.shop_order_items cascade;
drop table if exists public.shop_order_groups cascade;
drop table if exists public.shop_orders cascade;
drop table if exists public.shop_addresses cascade;
drop table if exists public.shop_cart_items cascade;
drop table if exists public.shop_carts cascade;
drop table if exists public.shop_favorites cascade;
drop table if exists public.shop_inventory cascade;
drop table if exists public.shop_product_images cascade;
drop table if exists public.shop_product_variants cascade;
drop table if exists public.shop_products cascade;
drop table if exists public.shop_seller_applications cascade;
drop table if exists public.shop_store_members cascade;
drop table if exists public.shop_stores cascade;
drop table if exists public.shop_categories cascade;

-- 7. Drop Private QA Schema
drop schema if exists shop_qa_private cascade;

commit;
