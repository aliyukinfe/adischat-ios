-- LOCAL CUTOVER CANDIDATE ONLY. Requires separately authorized reviewed-history preflight.
begin;
set local lock_timeout = '5s';
lock table public.shop_products, public.shop_stores, public.shop_product_images,
  public.shop_product_variants, public.shop_product_attribute_values,
  public.shop_product_options, public.shop_product_option_values,
  public.shop_variant_media in share row exclusive mode;
do $$
declare total_products integer;
begin
  select count(*) into total_products from public.shop_products p
    join public.shop_stores s on s.id=p.store_id
    where p.status='active' and p.moderation_status='approved' and p.deleted_at is null
      and s.status='active' and s.approval_status='approved' and s.deleted_at is null;
  if total_products=0 or exists(select 1 from public.shop_products p
      join public.shop_stores s on s.id=p.store_id
      where p.status='active' and p.moderation_status='approved' and p.deleted_at is null
        and s.status='active' and s.approval_status='approved' and s.deleted_at is null
        and not shop_catalog_private.is_public_product(p.id))
    or exists(select 1 from public.shop_stores s
      where s.status='active' and s.approval_status='approved' and s.deleted_at is null
        and not shop_catalog_private.is_current_reviewed('store',s.id)) then
    raise exception 'historical-review-incomplete';
  end if;
end $$;
do $$
begin
  if (select count(*) from pg_policies where schemaname='public'
      and tablename='shop_products' and cmd in ('SELECT','ALL'))<>1
    or not exists(select 1 from pg_policies where schemaname='public'
      and tablename='shop_products' and policyname='public_read_approved_products')
    or (select count(*) from pg_policies where schemaname='public'
      and tablename='shop_product_images' and cmd in ('SELECT','ALL'))<>1
    or not exists(select 1 from pg_policies where schemaname='public'
      and tablename='shop_product_images' and policyname='public_read_approved_product_images')
    or (select count(*) from pg_policies where schemaname='public'
      and tablename='shop_stores' and cmd in ('SELECT','ALL'))<>1
    or not exists(select 1 from pg_policies where schemaname='public'
      and tablename='shop_stores' and policyname='public_read_approved_stores') then
    raise exception 'public-policy-drift';
  end if;
end $$;
drop policy if exists public_read_approved_products on public.shop_products;
create policy public_read_approved_products on public.shop_products for select
  to anon,authenticated using (shop_catalog_private.is_public_product(id));
drop policy if exists public_read_approved_product_images on public.shop_product_images;
create policy public_read_approved_product_images on public.shop_product_images for select
  to anon,authenticated using (deleted_at is null
    and shop_catalog_private.is_current_reviewed('image',id)
    and shop_catalog_private.is_public_product(product_id));
drop policy if exists public_read_approved_stores on public.shop_stores;
create policy public_read_approved_stores on public.shop_stores for select
  to anon,authenticated using (status='active' and approval_status='approved'
    and deleted_at is null and shop_catalog_private.is_current_reviewed('store',id));

-- This wrapper keeps the known detail keys, but never returns the full core JSON.
-- Metadata-derived specification labels and option values are part of product review.
create or replace function public.adishop_product_catalog_payload(p_product_id uuid)
returns jsonb language sql stable security definer set search_path = public, shop_catalog_private as $$
  select case when not shop_catalog_private.is_public_product(p_product_id) then null
    else jsonb_build_object(
      'id',core.payload->'id','title',pr.public_payload->'title',
      'slug',pr.public_payload->'slug','brand',pr.public_payload->'brand',
      'categoryId',core.payload->'categoryId','categorySlug',pr.public_payload->'categorySlug',
      'description',pr.public_payload->'description',
      'shortDescription',pr.public_payload->'shortDescription',
      'priceMinor',core.payload->'priceMinor','compareAtPriceMinor',core.payload->'compareAtPriceMinor',
      'stockStatus',core.payload->'stockStatus',
      'specifications',pr.public_payload->'specifications',
      'options',pr.public_payload->'options',
      'variants',coalesce((select jsonb_agg(v.value || jsonb_build_object(
          'title',vr.public_payload->'title','options',vr.public_payload->'options'))
        from jsonb_array_elements(coalesce(core.payload->'variants','[]'::jsonb)) v(value)
        join shop_catalog_private.content_revisions rr
          on rr.entity_kind='variant' and rr.entity_id::text=v.value->>'id'
        join shop_catalog_private.reviewed_content vr
          on vr.entity_kind='variant' and vr.entity_id=rr.entity_id
          and vr.source_version=rr.revision::text and vr.decision='approved'),'[]'::jsonb),
      'priceRange',core.payload->'priceRange','aggregateAvailable',core.payload->'aggregateAvailable',
      'has_variants',core.payload->'has_variants','price_max_minor',core.payload->'price_max_minor',
      'aggregate_available_quantity',core.payload->'aggregate_available_quantity',
      'sellingUnitCode',core.payload->'sellingUnitCode',
      'minimumOrderQuantity',core.payload->'minimumOrderQuantity',
      'sellingUnit',core.payload->'sellingUnit',
      'shop_product_images',coalesce((select jsonb_agg(jsonb_build_object(
          'id',i.id,'image_url',null,
          'is_primary',i.is_primary,'display_order',i.display_order,
          'alt_text',ir.public_payload->'altText')
        order by i.display_order,i.id) from public.shop_product_images i
        join shop_catalog_private.content_revisions ix
          on ix.entity_kind='image' and ix.entity_id=i.id
        join shop_catalog_private.reviewed_content ir
          on ir.entity_kind='image' and ir.entity_id=i.id
          and ir.source_version=ix.revision::text and ir.decision='approved'
        where i.product_id=p.id and i.deleted_at is null
          and shop_catalog_private.is_current_reviewed('image',i.id)),'[]'::jsonb),
      'shop_stores',jsonb_build_object('id',s.id,'name',sr.public_payload->'name',
        'slug',sr.public_payload->'slug',
        'description',sr.public_payload->'description',
        'logo_url',null,'cover_url',null,
        'category',sr.public_payload->'category','city',sr.public_payload->'city',
        'approval_status',s.approval_status,
        'status',s.status,'average_store_rating',s.average_store_rating,
        'total_sales',s.total_sales,'created_at',s.created_at)
    ) end
  from (select public.adishop_product_catalog_payload_description_core(p_product_id) payload) core
  join public.shop_products p on p.id=p_product_id
  join public.shop_stores s on s.id=p.store_id
  join shop_catalog_private.content_revisions px
    on px.entity_kind='product' and px.entity_id=p.id
  join shop_catalog_private.reviewed_content pr
    on pr.entity_kind='product' and pr.entity_id=p.id
    and pr.source_version=px.revision::text and pr.decision='approved'
  join shop_catalog_private.content_revisions sx
    on sx.entity_kind='store' and sx.entity_id=s.id
  join shop_catalog_private.reviewed_content sr
    on sr.entity_kind='store' and sr.entity_id=s.id
    and sr.source_version=sx.revision::text and sr.decision='approved'
$$;
-- Replace broad table/view grants in the same transaction as the RLS/RPC switch.
revoke select on public.shop_products, public.shop_product_images,
  public.shop_product_variants, public.shop_stores,
  public.shop_public_products from anon, authenticated;
revoke select(image_url) on public.shop_product_images from anon, authenticated;
revoke select(logo_url,cover_url) on public.shop_stores from anon, authenticated;
grant select (id,store_id,title,slug,brand,created_at,category_id,
  category_slug,short_description,selling_unit_code,minimum_order_quantity,
  price_minor,price_max_minor,has_variants,aggregate_available_quantity,
  compare_at_price_minor,discount_percent,rating_average,rating_count,
  completed_sales_count,stock_status,status,moderation_status,deleted_at)
  on public.shop_products to anon, authenticated;
grant select (id,product_id,is_primary,display_order,alt_text,deleted_at)
  on public.shop_product_images to anon, authenticated;
grant select (id,name,slug,description,category,city,
  approval_status,status,average_store_rating,total_sales,created_at,deleted_at)
  on public.shop_stores to anon, authenticated;
commit;
