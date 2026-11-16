-- REVIEW ONLY. Do not execute in production without separate approval.
-- Keep the public product policy aligned with the existing public store policy.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';

alter policy public_read_approved_products on public.shop_products
  to anon, authenticated
  using (
    status = 'active'
    and moderation_status = 'approved'
    and deleted_at is null
    and exists (
      select 1 from public.shop_stores s
      where s.id = shop_products.store_id
        and s.status = 'active'
        and s.approval_status = 'approved'
        and s.deleted_at is null
    )
  );

commit;
