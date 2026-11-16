-- LOCAL STAGING DRAFT ONLY. DO NOT RUN ON PRODUCTION.
-- Preconditions NOT YET MET: all approved product/store/image public fields
-- must be human-reviewed, copied to safe public columns, and kept synchronized
-- by authorized writes. The catalog RPC and other public projections must be
-- independently safe. Unknown older select=* clients WILL BREAK.
begin;

revoke select on public.shop_products, public.shop_product_images,
  public.shop_product_variants, public.shop_stores,
  public.shop_public_products from anon, authenticated;

-- Known Home/Trending/Search columns plus filter, sort, join and RLS keys.
grant select (id,store_id,title,slug,brand,created_at,category_id,
  category_slug,short_description,selling_unit_code,minimum_order_quantity,
  price_minor,price_max_minor,has_variants,aggregate_available_quantity,
  compare_at_price_minor,discount_percent,rating_average,rating_count,
  completed_sales_count,stock_status,status,moderation_status,deleted_at)
  on public.shop_products to anon, authenticated;
grant select (id,product_id,image_url,is_primary,display_order,alt_text,deleted_at)
  on public.shop_product_images to anon, authenticated;
grant select (id,name,slug,description,logo_url,cover_url,category,city,
  approval_status,status,average_store_rating,total_sales,created_at,deleted_at)
  on public.shop_stores to anon, authenticated;

-- No public grant on variants or the raw shop_public_products view.
-- Detail must use a separately reviewed buyer-safe RPC with the same JSON keys.
commit;
