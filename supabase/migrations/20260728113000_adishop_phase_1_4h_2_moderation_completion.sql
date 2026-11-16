-- Phase 1.4H.2 stabilization: widen marketplace moderation states and
-- add metadata needed by the completed seller-application flow.

alter table public.shop_seller_applications
  drop constraint if exists shop_seller_applications_status_check;

alter table public.shop_seller_applications
  add constraint shop_seller_applications_status_check
  check (status in (
    'draft',
    'submitted',
    'under_review',
    'information_required',
    'approved',
    'rejected',
    'suspended',
    'banned'
  ));

alter table public.shop_seller_applications
  add column if not exists information_request_message text,
  add column if not exists information_requested_at timestamptz,
  add column if not exists applicant_response text,
  add column if not exists applicant_responded_at timestamptz,
  add column if not exists last_request_id text;

alter table public.shop_seller_application_documents
  drop constraint if exists shop_seller_application_documents_status_check;

alter table public.shop_seller_application_documents
  add constraint shop_seller_application_documents_status_check
  check (status in (
    'uploaded',
    'submitted',
    'under_review',
    'verified',
    'rejected',
    'replacement_required',
    'removed'
  ));

alter table public.shop_reviews
  drop constraint if exists shop_reviews_status_check;

alter table public.shop_reviews
  add constraint shop_reviews_status_check
  check (status in ('pending', 'published', 'hidden', 'rejected', 'removed', 'deleted_by_buyer'));

alter table public.shop_store_reviews
  drop constraint if exists shop_store_reviews_status_check;

alter table public.shop_store_reviews
  add constraint shop_store_reviews_status_check
  check (status in ('pending', 'published', 'hidden', 'rejected', 'removed', 'deleted_by_buyer'));

create index if not exists shop_seller_applications_profile_status_idx
  on public.shop_seller_applications(profile_id, status, updated_at desc);

create index if not exists shop_refund_requests_status_created_idx
  on public.shop_refund_requests(status, created_at desc);

create index if not exists shop_exchange_requests_status_created_idx
  on public.shop_exchange_requests(status, created_at desc);

create index if not exists shop_reviews_status_created_idx
  on public.shop_reviews(status, created_at desc);

create index if not exists shop_store_reviews_status_created_idx
  on public.shop_store_reviews(status, created_at desc);

insert into public.shop_role_permissions(role_id, permission)
select r.id, p.permission
from public.shop_roles r
join (
  values
    ('SHOP_SUPER_ADMIN','review.moderation.manage'),
    ('SHOP_SUPER_ADMIN','order.support.manage'),
    ('SHOP_ADMIN','review.moderation.manage'),
    ('SHOP_ADMIN','order.support.manage'),
    ('SUPPORT_AGENT','order.support.manage')
) as p(role_code, permission) on p.role_code = r.code
on conflict (role_id, permission) do nothing;
