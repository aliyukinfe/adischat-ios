with constants as (
  select
    '6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee'::uuid as admin_id,
    '10000000-0000-4000-8000-000000014e01'::uuid as seller_a,
    '10000000-0000-4000-8000-000000014e02'::uuid as seller_b,
    '10000000-0000-4000-8000-000000014e03'::uuid as seller_c,
    '10000000-0000-4000-8000-000000014e1a'::uuid as app_a,
    '10000000-0000-4000-8000-000000014e1b'::uuid as app_b,
    '10000000-0000-4000-8000-000000014e1c'::uuid as app_c,
    '10000000-0000-4000-8000-000000014e11'::uuid as product_a,
    '10000000-0000-4000-8000-000000014e12'::uuid as product_b,
    '10000000-0000-4000-8000-000000014e13'::uuid as product_c,
    '00000000-0000-4000-8000-000000000201'::uuid as store_id,
    '00000000-0000-4000-8000-000000000104'::uuid as category_id
),
profiles as (
  insert into public.shop_profiles(id, adischat_user_id, display_name, email, status)
  select seller_a, 'phase-1-4e-1-validation-seller-a', 'Phase 1.4E.1 Seller A', 'phase-1-4e-1-seller-a@example.invalid', 'active' from constants
  union all select seller_b, 'phase-1-4e-1-validation-seller-b', 'Phase 1.4E.1 Seller B', 'phase-1-4e-1-seller-b@example.invalid', 'active' from constants
  union all select seller_c, 'phase-1-4e-1-validation-seller-c', 'Phase 1.4E.1 Seller C', 'phase-1-4e-1-seller-c@example.invalid', 'active' from constants
  on conflict (id) do update set status = 'active', updated_at = now()
  returning id
),
applications as (
  insert into public.shop_seller_applications(id, profile_id, proposed_store_name, business_type, status, submitted_at, rejection_reason, reviewed_at, reviewed_by_profile_id, suspended_at)
  select app_a, seller_a, 'Phase 1.4E.1 Approval Store', 'validation', 'submitted', now(), null::text, null::timestamptz, null::uuid, null::timestamptz from constants
  union all select app_b, seller_b, 'Phase 1.4E.1 Rejection Store', 'validation', 'submitted', now(), null::text, null::timestamptz, null::uuid, null::timestamptz from constants
  union all select app_c, seller_c, 'Phase 1.4E.1 Suspension Store', 'validation', 'submitted', now(), null::text, null::timestamptz, null::uuid, null::timestamptz from constants
  on conflict (id) do update set status = 'submitted', submitted_at = now(), rejection_reason = null, reviewed_at = null, reviewed_by_profile_id = null, suspended_at = null, updated_at = now()
  returning id
),
products as (
  insert into public.shop_products(id, store_id, category_id, title, slug, category_slug, short_description, description, status, moderation_status, currency, price_minor, stock_status, submitted_at)
  select product_a, store_id, category_id, 'Phase 1.4E.1 Approved Validation Product', 'phase-1-4e-1-approved-validation-product', 'beauty', 'Validation product', 'Controlled admin moderation approval product.', 'draft', 'submitted', 'ETB', 123400, 'in_stock', now() from constants
  union all select product_b, store_id, category_id, 'Phase 1.4E.1 Rejected Validation Product', 'phase-1-4e-1-rejected-validation-product', 'beauty', 'Validation product', 'Controlled admin moderation rejection product.', 'draft', 'submitted', 'ETB', 125000, 'in_stock', now() from constants
  union all select product_c, store_id, category_id, 'Phase 1.4E.1 Changes Validation Product', 'phase-1-4e-1-changes-validation-product', 'beauty', 'Validation product', 'Controlled admin moderation changes product.', 'draft', 'submitted', 'ETB', 127500, 'in_stock', now() from constants
  on conflict (id) do update set status = 'draft', moderation_status = 'submitted', submitted_at = now(), reviewed_at = null, reviewed_by_profile_id = null, published_at = null, suspended_at = null, updated_at = now()
  returning id
),
images as (
  insert into public.shop_product_images(product_id, image_url, storage_path, alt_text, is_primary, display_order)
  select product_a, 'https://example.invalid/adisgebeya/phase-1-4e-1-product-a.jpg', 'validation/phase-1-4e-1/product-a.jpg', 'Validation product image', true, 0 from constants
  union all select product_b, 'https://example.invalid/adisgebeya/phase-1-4e-1-product-b.jpg', 'validation/phase-1-4e-1/product-b.jpg', 'Validation product image', true, 0 from constants
  union all select product_c, 'https://example.invalid/adisgebeya/phase-1-4e-1-product-c.jpg', 'validation/phase-1-4e-1/product-c.jpg', 'Validation product image', true, 0 from constants
  on conflict do nothing
  returning id
),
inventory as (
  insert into public.shop_inventory(product_id, quantity_available, quantity_reserved, low_stock_threshold)
  select product_a, 10, 0, 2 from constants
  union all select product_b, 10, 0, 2 from constants
  union all select product_c, 10, 0, 2 from constants
  on conflict (product_id, variant_id) do update set quantity_available = 10, quantity_reserved = 0, updated_at = now()
  returning id
)
select
  (select count(*) from profiles) as profiles_ready,
  (select count(*) from applications) as applications_ready,
  (select count(*) from products) as products_ready;

select public.adishop_admin_review_seller_application('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e1a','start_review','','','phase-1-4e-1-app-a-start') ? 'application' as app_a_start_review_ok;
select public.adishop_admin_review_seller_application('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e1a','approve','','','phase-1-4e-1-app-a-approve') ? 'application' as app_a_approve_ok;
select public.adishop_admin_review_seller_application('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e1a','approve','','','phase-1-4e-1-app-a-approve') ? 'application' as app_a_duplicate_approve_ok;

select public.adishop_admin_review_seller_application('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e1b','start_review','','','phase-1-4e-1-app-b-start') ? 'application' as app_b_start_review_ok;
select public.adishop_admin_review_seller_application('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e1b','reject','Controlled validation rejection reason.','','phase-1-4e-1-app-b-reject') ? 'application' as app_b_reject_ok;
select public.adishop_admin_review_seller_application('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e1b','reject','Controlled validation rejection reason.','','phase-1-4e-1-app-b-reject') ? 'application' as app_b_duplicate_reject_ok;

select public.adishop_admin_review_seller_application('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e1c','approve','','','phase-1-4e-1-app-c-approve') ? 'application' as app_c_approve_ok;
select public.adishop_admin_review_seller_application('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e1c','suspend','Controlled validation suspension reason.','','phase-1-4e-1-app-c-suspend') ? 'application' as app_c_suspend_ok;
select public.adishop_admin_review_seller_application('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e1c','reactivate','','','phase-1-4e-1-app-c-reactivate') ? 'application' as app_c_reactivate_ok;

select public.adishop_admin_review_product('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e11','start_review',null,'','','phase-1-4e-1-product-a-start') ? 'product' as product_a_start_review_ok;
select public.adishop_admin_review_product('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e11','approve',null,'','','phase-1-4e-1-product-a-approve') ? 'product' as product_a_approve_ok;
select public.adishop_admin_review_product('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e11','approve',null,'','','phase-1-4e-1-product-a-approve') ? 'product' as product_a_duplicate_approve_ok;

select public.adishop_admin_review_product('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e12','start_review',null,'','','phase-1-4e-1-product-b-start') ? 'product' as product_b_start_review_ok;
select public.adishop_admin_review_product('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e12','reject','content_quality','Controlled validation rejection reason.','','phase-1-4e-1-product-b-reject') ? 'product' as product_b_reject_ok;
select public.adishop_admin_review_product('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e12','reject','content_quality','Controlled validation rejection reason.','','phase-1-4e-1-product-b-reject') ? 'product' as product_b_duplicate_reject_ok;

select public.adishop_admin_review_product('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e13','start_review',null,'','','phase-1-4e-1-product-c-start') ? 'product' as product_c_start_review_ok;
select public.adishop_admin_review_product('6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee','10000000-0000-4000-8000-000000014e13','request_changes','content_quality','Controlled validation changes requested reason.','','phase-1-4e-1-product-c-changes') ? 'product' as product_c_changes_ok;

select
  'seller_applications' as check_name,
  jsonb_agg(jsonb_build_object('id', id, 'status', status, 'reviewed', reviewed_at is not null, 'reviewer', reviewed_by_profile_id is not null) order by id) as result
from public.shop_seller_applications
where id in ('10000000-0000-4000-8000-000000014e1a','10000000-0000-4000-8000-000000014e1b','10000000-0000-4000-8000-000000014e1c');

select
  'products' as check_name,
  jsonb_agg(jsonb_build_object('id', id, 'status', status, 'moderation', moderation_status, 'reviewed', reviewed_at is not null, 'published', published_at is not null) order by id) as result
from public.shop_products
where id in ('10000000-0000-4000-8000-000000014e11','10000000-0000-4000-8000-000000014e12','10000000-0000-4000-8000-000000014e13');

select
  count(*) filter (where request_id like 'phase-1-4e-1-%')::int as audit_events,
  count(distinct request_id) filter (where request_id like 'phase-1-4e-1-%')::int as unique_request_ids
from public.shop_admin_audit_logs;

select type,count(*)::int as notifications
from public.shop_notifications
where data->>'action' in ('start_review','approve','reject','suspend','reactivate','request_changes')
  and (data->>'applicationId' in ('10000000-0000-4000-8000-000000014e1a','10000000-0000-4000-8000-000000014e1b','10000000-0000-4000-8000-000000014e1c')
    or data->>'productId' in ('10000000-0000-4000-8000-000000014e11','10000000-0000-4000-8000-000000014e12','10000000-0000-4000-8000-000000014e13'))
group by type
order by type;

select
  count(*)::int as public_visible_validation_products
from public.shop_products p
join public.shop_stores s on s.id = p.store_id
where p.id in ('10000000-0000-4000-8000-000000014e11','10000000-0000-4000-8000-000000014e12','10000000-0000-4000-8000-000000014e13')
  and p.status = 'active'
  and p.moderation_status = 'approved'
  and p.deleted_at is null
  and s.status = 'active'
  and s.approval_status = 'approved'
  and s.deleted_at is null;
