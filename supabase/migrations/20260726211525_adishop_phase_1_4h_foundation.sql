-- AdisGebeya Phase 1.4H.1 foundation.
-- Compatibility-first migration: extend existing tables, do not replace prior systems.

-- Review compatibility and aggregates.
alter table public.shop_reviews add column if not exists order_id uuid references public.shop_orders(id) on delete set null;
alter table public.shop_reviews add column if not exists store_id uuid references public.shop_stores(id) on delete set null;
alter table public.shop_reviews add column if not exists title text;
alter table public.shop_reviews add column if not exists verified_purchase boolean not null default false;
alter table public.shop_reviews add column if not exists seller_response text;
alter table public.shop_reviews add column if not exists seller_response_at timestamptz;
alter table public.shop_reviews add column if not exists moderation_reason_code text;
alter table public.shop_reviews add column if not exists moderated_by uuid references public.shop_profiles(id) on delete set null;
alter table public.shop_reviews add column if not exists moderated_at timestamptz;
alter table public.shop_reviews add column if not exists idempotency_key text;
alter table public.shop_reviews add column if not exists version integer not null default 1;

alter table public.shop_store_reviews add column if not exists order_id uuid references public.shop_orders(id) on delete set null;
alter table public.shop_store_reviews add column if not exists service_rating integer;
alter table public.shop_store_reviews add column if not exists communication_rating integer;
alter table public.shop_store_reviews add column if not exists delivery_rating integer;
alter table public.shop_store_reviews add column if not exists verified_purchase boolean not null default false;
alter table public.shop_store_reviews add column if not exists seller_response text;
alter table public.shop_store_reviews add column if not exists seller_response_at timestamptz;
alter table public.shop_store_reviews add column if not exists moderation_reason_code text;
alter table public.shop_store_reviews add column if not exists moderated_by uuid references public.shop_profiles(id) on delete set null;
alter table public.shop_store_reviews add column if not exists moderated_at timestamptz;
alter table public.shop_store_reviews add column if not exists idempotency_key text;
alter table public.shop_store_reviews add column if not exists version integer not null default 1;

do $$
declare constraint_record record;
begin
  for constraint_record in
    select conname from pg_constraint
    where conrelid = 'public.shop_reviews'::regclass
      and contype = 'c'
      and pg_get_constraintdef(oid) like '%status%'
  loop
    execute format('alter table public.shop_reviews drop constraint if exists %I', constraint_record.conname);
  end loop;
  for constraint_record in
    select conname from pg_constraint
    where conrelid = 'public.shop_store_reviews'::regclass
      and contype = 'c'
      and pg_get_constraintdef(oid) like '%status%'
  loop
    execute format('alter table public.shop_store_reviews drop constraint if exists %I', constraint_record.conname);
  end loop;
end $$;

alter table public.shop_reviews
  add constraint shop_reviews_status_check
  check (status in ('pending','published','hidden','rejected','deleted_by_buyer','removed_by_admin','removed'));

alter table public.shop_store_reviews
  add constraint shop_store_reviews_status_check
  check (status in ('pending','published','hidden','rejected','deleted_by_buyer','removed_by_admin','removed'));

alter table public.shop_store_reviews
  drop constraint if exists shop_store_reviews_service_rating_check;
alter table public.shop_store_reviews
  add constraint shop_store_reviews_service_rating_check
  check (service_rating is null or service_rating between 1 and 5);
alter table public.shop_store_reviews
  drop constraint if exists shop_store_reviews_communication_rating_check;
alter table public.shop_store_reviews
  add constraint shop_store_reviews_communication_rating_check
  check (communication_rating is null or communication_rating between 1 and 5);
alter table public.shop_store_reviews
  drop constraint if exists shop_store_reviews_delivery_rating_check;
alter table public.shop_store_reviews
  add constraint shop_store_reviews_delivery_rating_check
  check (delivery_rating is null or delivery_rating between 1 and 5);

create unique index if not exists shop_reviews_profile_idempotency_idx
  on public.shop_reviews(profile_id, idempotency_key)
  where idempotency_key is not null;

create unique index if not exists shop_reviews_verified_order_item_idx
  on public.shop_reviews(profile_id, order_item_id)
  where order_item_id is not null and status <> 'deleted_by_buyer';

create index if not exists shop_reviews_product_public_idx
  on public.shop_reviews(product_id, status, created_at desc);

create unique index if not exists shop_store_reviews_profile_idempotency_idx
  on public.shop_store_reviews(profile_id, idempotency_key)
  where idempotency_key is not null;

create unique index if not exists shop_store_reviews_verified_group_idx
  on public.shop_store_reviews(profile_id, order_group_id)
  where order_group_id is not null and status <> 'deleted_by_buyer';

create index if not exists shop_store_reviews_store_public_idx
  on public.shop_store_reviews(store_id, status, created_at desc);

alter table public.shop_products add column if not exists rating_distribution jsonb not null default '{}'::jsonb;
alter table public.shop_stores add column if not exists rating_count integer not null default 0 check (rating_count >= 0);
alter table public.shop_stores add column if not exists service_rating_average numeric(3,2) not null default 0 check (service_rating_average >= 0 and service_rating_average <= 5);
alter table public.shop_stores add column if not exists communication_rating_average numeric(3,2) not null default 0 check (communication_rating_average >= 0 and communication_rating_average <= 5);
alter table public.shop_stores add column if not exists delivery_rating_average numeric(3,2) not null default 0 check (delivery_rating_average >= 0 and delivery_rating_average <= 5);

-- Store policy fields.
alter table public.shop_stores add column if not exists refund_window_days integer not null default 7 check (refund_window_days between 1 and 30);
alter table public.shop_stores add column if not exists exchange_window_days integer not null default 7 check (exchange_window_days between 1 and 30);
alter table public.shop_stores add column if not exists review_window_days integer not null default 30 check (review_window_days between 1 and 180);
alter table public.shop_stores add column if not exists returns_accepted boolean not null default true;
alter table public.shop_stores add column if not exists exchanges_accepted boolean not null default true;
alter table public.shop_stores add column if not exists return_shipping_responsibility text not null default 'buyer' check (return_shipping_responsibility in ('buyer','seller','marketplace','case_by_case'));
alter table public.shop_stores add column if not exists refund_policy_text text;
alter table public.shop_stores add column if not exists exchange_policy_text text;
alter table public.shop_stores add column if not exists shipping_policy_text text;
alter table public.shop_stores add column if not exists warranty_policy_text text;
alter table public.shop_stores add column if not exists customer_service_policy_text text;
alter table public.shop_stores add column if not exists policy_updated_at timestamptz;

do $$
declare constraint_record record;
begin
  for constraint_record in
    select conname from pg_constraint
    where conrelid = 'public.shop_stores'::regclass
      and contype = 'c'
      and pg_get_constraintdef(oid) like '%status%'
  loop
    execute format('alter table public.shop_stores drop constraint if exists %I', constraint_record.conname);
  end loop;
end $$;

alter table public.shop_stores
  add constraint shop_stores_status_check
  check (status in ('application_pending','under_review','draft','active','paused','suspended','banned','closed','closed_by_owner','archived','deleted'));

-- Review event/media/moderation tables.
create table if not exists public.shop_review_events (
  id uuid primary key default gen_random_uuid(),
  review_type text not null check (review_type in ('product','store')),
  review_id uuid not null,
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  actor_role text not null check (actor_role in ('BUYER','SELLER','ADMIN','SYSTEM')),
  event_type text not null,
  previous_status text,
  next_status text,
  public_message text,
  private_note text,
  metadata jsonb not null default '{}'::jsonb,
  request_id text,
  created_at timestamptz not null default now()
);

create index if not exists shop_review_events_review_idx
  on public.shop_review_events(review_type, review_id, created_at asc);

create table if not exists public.shop_review_media (
  id uuid primary key default gen_random_uuid(),
  review_type text not null check (review_type in ('product','store')),
  review_id uuid not null,
  storage_path text not null,
  mime_type text not null check (mime_type in ('image/jpeg','image/png','image/webp')),
  byte_size integer not null check (byte_size > 0 and byte_size <= 5242880),
  sort_order integer not null default 0,
  status text not null default 'pending' check (status in ('pending','approved','hidden','removed')),
  uploaded_by uuid references public.shop_profiles(id) on delete set null,
  created_at timestamptz not null default now()
);

create index if not exists shop_review_media_review_idx
  on public.shop_review_media(review_type, review_id, sort_order asc);

create table if not exists public.shop_review_moderation_actions (
  id uuid primary key default gen_random_uuid(),
  review_type text not null check (review_type in ('product','store')),
  review_id uuid not null,
  admin_profile_id uuid references public.shop_profiles(id) on delete set null,
  action text not null,
  reason_code text,
  public_reason text,
  private_note text,
  previous_status text,
  new_status text,
  request_id text,
  created_at timestamptz not null default now()
);

create unique index if not exists shop_review_moderation_request_idx
  on public.shop_review_moderation_actions(admin_profile_id, request_id)
  where request_id is not null;

-- Refund/exchange request tables.
create table if not exists public.shop_refund_requests (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.shop_orders(id) on delete cascade,
  order_group_id uuid references public.shop_order_groups(id) on delete set null,
  buyer_profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  store_id uuid references public.shop_stores(id) on delete set null,
  seller_profile_id uuid references public.shop_profiles(id) on delete set null,
  linked_issue_id uuid references public.shop_order_issues(id) on delete set null,
  reason_code text not null check (reason_code in ('item_not_received','wrong_item','damaged_item','incomplete_order','not_as_described','defective_item','duplicate_charge','seller_cancelled','delivery_issue','other')),
  description text not null default '',
  requested_amount integer not null default 0 check (requested_amount >= 0),
  currency char(3) not null default 'ETB',
  status text not null default 'submitted' check (status in ('submitted','under_review','awaiting_buyer','awaiting_seller','approved','partially_approved','rejected','cancelled','completed','closed')),
  resolution_code text,
  buyer_visible_update text,
  private_admin_note text,
  idempotency_key text not null,
  version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  resolved_at timestamptz
);

create table if not exists public.shop_refund_request_items (
  id uuid primary key default gen_random_uuid(),
  refund_request_id uuid not null references public.shop_refund_requests(id) on delete cascade,
  order_item_id uuid not null references public.shop_order_items(id) on delete cascade,
  quantity integer not null check (quantity > 0),
  unit_price integer not null check (unit_price >= 0),
  requested_amount integer not null check (requested_amount >= 0),
  created_at timestamptz not null default now(),
  unique (refund_request_id, order_item_id)
);

create table if not exists public.shop_refund_events (
  id uuid primary key default gen_random_uuid(),
  refund_request_id uuid not null references public.shop_refund_requests(id) on delete cascade,
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  actor_role text not null check (actor_role in ('BUYER','SELLER','ADMIN','SYSTEM')),
  event_code text not null,
  previous_status text,
  next_status text,
  public_message text,
  private_note text,
  metadata jsonb not null default '{}'::jsonb,
  request_id text,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_refund_evidence (
  id uuid primary key default gen_random_uuid(),
  refund_request_id uuid not null references public.shop_refund_requests(id) on delete cascade,
  storage_path text not null,
  mime_type text not null check (mime_type in ('image/jpeg','image/png','image/webp')),
  byte_size integer not null check (byte_size > 0 and byte_size <= 5242880),
  status text not null default 'submitted' check (status in ('submitted','approved','hidden','removed')),
  uploaded_by uuid references public.shop_profiles(id) on delete set null,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_exchange_requests (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.shop_orders(id) on delete cascade,
  order_group_id uuid references public.shop_order_groups(id) on delete set null,
  buyer_profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  store_id uuid references public.shop_stores(id) on delete set null,
  seller_profile_id uuid references public.shop_profiles(id) on delete set null,
  linked_issue_id uuid references public.shop_order_issues(id) on delete set null,
  reason_code text not null check (reason_code in ('wrong_size','wrong_color','wrong_variant','damaged_item','defective_item','wrong_item','not_as_described','other')),
  description text not null default '',
  status text not null default 'submitted' check (status in ('submitted','under_review','awaiting_buyer','awaiting_seller','approved','rejected','awaiting_return','replacement_processing','replacement_shipped','completed','cancelled','closed')),
  seller_response text,
  buyer_visible_update text,
  private_admin_note text,
  idempotency_key text not null,
  version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  resolved_at timestamptz
);

create table if not exists public.shop_exchange_request_items (
  id uuid primary key default gen_random_uuid(),
  exchange_request_id uuid not null references public.shop_exchange_requests(id) on delete cascade,
  order_item_id uuid not null references public.shop_order_items(id) on delete cascade,
  requested_variant_id uuid references public.shop_product_variants(id) on delete set null,
  quantity integer not null check (quantity > 0),
  created_at timestamptz not null default now(),
  unique (exchange_request_id, order_item_id)
);

create table if not exists public.shop_exchange_events (
  id uuid primary key default gen_random_uuid(),
  exchange_request_id uuid not null references public.shop_exchange_requests(id) on delete cascade,
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  actor_role text not null check (actor_role in ('BUYER','SELLER','ADMIN','SYSTEM')),
  event_code text not null,
  previous_status text,
  next_status text,
  public_message text,
  private_note text,
  metadata jsonb not null default '{}'::jsonb,
  request_id text,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_exchange_evidence (
  id uuid primary key default gen_random_uuid(),
  exchange_request_id uuid not null references public.shop_exchange_requests(id) on delete cascade,
  storage_path text not null,
  mime_type text not null check (mime_type in ('image/jpeg','image/png','image/webp')),
  byte_size integer not null check (byte_size > 0 and byte_size <= 5242880),
  status text not null default 'submitted' check (status in ('submitted','approved','hidden','removed')),
  uploaded_by uuid references public.shop_profiles(id) on delete set null,
  created_at timestamptz not null default now()
);

create unique index if not exists shop_refund_requests_buyer_idempotency_idx
  on public.shop_refund_requests(buyer_profile_id, idempotency_key);
create index if not exists shop_refund_requests_buyer_idx
  on public.shop_refund_requests(buyer_profile_id, created_at desc);
create index if not exists shop_refund_requests_store_idx
  on public.shop_refund_requests(store_id, status, created_at desc);
create index if not exists shop_refund_request_items_item_idx
  on public.shop_refund_request_items(order_item_id);

create unique index if not exists shop_exchange_requests_buyer_idempotency_idx
  on public.shop_exchange_requests(buyer_profile_id, idempotency_key);
create index if not exists shop_exchange_requests_buyer_idx
  on public.shop_exchange_requests(buyer_profile_id, created_at desc);
create index if not exists shop_exchange_requests_store_idx
  on public.shop_exchange_requests(store_id, status, created_at desc);
create index if not exists shop_exchange_request_items_item_idx
  on public.shop_exchange_request_items(order_item_id);

-- Seller application expansion.
alter table public.shop_seller_applications add column if not exists legal_business_name text;
alter table public.shop_seller_applications add column if not exists business_registration_number text;
alter table public.shop_seller_applications add column if not exists tax_identifier text;
alter table public.shop_seller_applications add column if not exists store_description text;
alter table public.shop_seller_applications add column if not exists primary_category_id uuid references public.shop_categories(id) on delete set null;
alter table public.shop_seller_applications add column if not exists expected_product_categories text[] not null default '{}';
alter table public.shop_seller_applications add column if not exists short_tagline text;
alter table public.shop_seller_applications add column if not exists logo_path text;
alter table public.shop_seller_applications add column if not exists banner_path text;
alter table public.shop_seller_applications add column if not exists owner_full_name text;
alter table public.shop_seller_applications add column if not exists contact_person_name text;
alter table public.shop_seller_applications add column if not exists phone text;
alter table public.shop_seller_applications add column if not exists email text;
alter table public.shop_seller_applications add column if not exists support_phone text;
alter table public.shop_seller_applications add column if not exists support_email text;
alter table public.shop_seller_applications add column if not exists country text default 'Ethiopia';
alter table public.shop_seller_applications add column if not exists region_state text;
alter table public.shop_seller_applications add column if not exists city text;
alter table public.shop_seller_applications add column if not exists district text;
alter table public.shop_seller_applications add column if not exists address_line text;
alter table public.shop_seller_applications add column if not exists postal_code text;
alter table public.shop_seller_applications add column if not exists location_notes text;
alter table public.shop_seller_applications add column if not exists fulfillment_method text;
alter table public.shop_seller_applications add column if not exists service_area text;
alter table public.shop_seller_applications add column if not exists estimated_handling_time text;
alter table public.shop_seller_applications add column if not exists return_policy text;
alter table public.shop_seller_applications add column if not exists exchange_policy text;
alter table public.shop_seller_applications add column if not exists shipping_policy text;
alter table public.shop_seller_applications add column if not exists warranty_policy text;
alter table public.shop_seller_applications add column if not exists customer_service_policy text;
alter table public.shop_seller_applications add column if not exists terms_accepted boolean not null default false;
alter table public.shop_seller_applications add column if not exists privacy_acknowledged boolean not null default false;
alter table public.shop_seller_applications add column if not exists seller_policy_accepted boolean not null default false;
alter table public.shop_seller_applications add column if not exists declaration_of_accuracy boolean not null default false;
alter table public.shop_seller_applications add column if not exists current_step integer not null default 1 check (current_step between 1 and 7);
alter table public.shop_seller_applications add column if not exists draft_saved_at timestamptz;
alter table public.shop_seller_applications add column if not exists version integer not null default 1;

create table if not exists public.shop_seller_application_documents (
  id uuid primary key default gen_random_uuid(),
  application_id uuid not null references public.shop_seller_applications(id) on delete cascade,
  document_type text not null,
  storage_path text not null,
  mime_type text not null,
  byte_size integer not null check (byte_size > 0 and byte_size <= 10485760),
  status text not null default 'submitted' check (status in ('draft','submitted','approved','rejected','removed')),
  uploaded_at timestamptz not null default now(),
  reviewed_at timestamptz
);

create table if not exists public.shop_seller_application_events (
  id uuid primary key default gen_random_uuid(),
  application_id uuid not null references public.shop_seller_applications(id) on delete cascade,
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  actor_role text not null check (actor_role in ('SELLER','ADMIN','SYSTEM')),
  event_code text not null,
  previous_status text,
  next_status text,
  public_message text,
  private_note text,
  metadata jsonb not null default '{}'::jsonb,
  request_id text,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_seller_application_admin_notes (
  id uuid primary key default gen_random_uuid(),
  application_id uuid not null references public.shop_seller_applications(id) on delete cascade,
  admin_profile_id uuid references public.shop_profiles(id) on delete set null,
  note text not null,
  visibility text not null default 'private' check (visibility in ('private','applicant_visible')),
  created_at timestamptz not null default now()
);

create index if not exists shop_seller_application_documents_app_idx
  on public.shop_seller_application_documents(application_id, uploaded_at desc);
create index if not exists shop_seller_application_events_app_idx
  on public.shop_seller_application_events(application_id, created_at asc);

-- Store compliance.
create table if not exists public.shop_store_status_events (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete cascade,
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  actor_role text not null check (actor_role in ('SELLER','ADMIN','SYSTEM')),
  event_code text not null,
  previous_status text,
  next_status text,
  seller_visible_message text,
  private_admin_note text,
  reason_code text,
  request_id text,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_store_warnings (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete cascade,
  reason_code text not null,
  severity text not null default 'medium' check (severity in ('low','medium','high','critical')),
  seller_visible_message text not null,
  private_admin_note text,
  status text not null default 'open' check (status in ('open','acknowledged','resolved','dismissed')),
  created_by uuid references public.shop_profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  resolved_at timestamptz
);

create table if not exists public.shop_store_violations (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete cascade,
  category text not null check (category in ('prohibited_product','counterfeit_or_misleading','fraud_or_scam','order_fulfillment_failure','abusive_behavior','policy_violation','repeated_cancellation','fake_reviews','buyer_data_misuse','intellectual_property','other')),
  severity text not null default 'medium' check (severity in ('low','medium','high','critical')),
  status text not null default 'open' check (status in ('open','under_review','resolved','dismissed')),
  public_message text,
  private_admin_note text,
  created_by uuid references public.shop_profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  resolved_at timestamptz
);

create table if not exists public.shop_store_admin_notes (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete cascade,
  admin_profile_id uuid references public.shop_profiles(id) on delete set null,
  note text not null,
  visibility text not null default 'private' check (visibility in ('private','seller_visible')),
  created_at timestamptz not null default now()
);

create table if not exists public.shop_store_appeals (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete cascade,
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  reason text not null,
  status text not null default 'submitted' check (status in ('submitted','under_review','approved','rejected','closed')),
  admin_response text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists shop_store_status_events_store_idx
  on public.shop_store_status_events(store_id, created_at desc);
create index if not exists shop_store_warnings_store_idx
  on public.shop_store_warnings(store_id, status, created_at desc);
create index if not exists shop_store_violations_store_idx
  on public.shop_store_violations(store_id, status, created_at desc);

-- RLS: protected writes use service-role Edge Functions/RPCs.
do $$
declare table_name text;
begin
  foreach table_name in array array[
    'shop_review_events','shop_review_media','shop_review_moderation_actions',
    'shop_refund_requests','shop_refund_request_items','shop_refund_events','shop_refund_evidence',
    'shop_exchange_requests','shop_exchange_request_items','shop_exchange_events','shop_exchange_evidence',
    'shop_seller_application_documents','shop_seller_application_events','shop_seller_application_admin_notes',
    'shop_store_status_events','shop_store_warnings','shop_store_violations','shop_store_admin_notes','shop_store_appeals'
  ] loop
    execute format('alter table public.%I enable row level security', table_name);
    execute format('drop policy if exists client_access_deferred on public.%I', table_name);
    execute format('create policy client_access_deferred on public.%I for all to anon, authenticated using (false) with check (false)', table_name);
    execute format('revoke all on table public.%I from anon, authenticated', table_name);
  end loop;
end $$;

-- Helper labels.
create or replace function public.adishop_request_reason_label(p_reason_code text)
returns text
language sql
stable
as $$
  select case p_reason_code
    when 'item_not_received' then 'Item not received'
    when 'wrong_item' then 'Wrong item received'
    when 'damaged_item' then 'Item arrived damaged'
    when 'incomplete_order' then 'Incomplete order'
    when 'not_as_described' then 'Item not as described'
    when 'defective_item' then 'Defective item'
    when 'duplicate_charge' then 'Duplicate charge'
    when 'seller_cancelled' then 'Seller cancelled'
    when 'delivery_issue' then 'Delivery issue'
    when 'wrong_size' then 'Wrong size'
    when 'wrong_color' then 'Wrong color'
    when 'wrong_variant' then 'Wrong variant'
    when 'other' then 'Other'
    else 'Request'
  end
$$;

create or replace function public.adishop_review_public_payload(p_product_id uuid default null, p_store_id uuid default null)
returns jsonb
language sql
stable
as $$
  select jsonb_build_object(
    'productReviews', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', r.id,
        'product_id', r.product_id,
        'store_id', r.store_id,
        'rating', r.rating,
        'title', r.title,
        'body', r.body,
        'verified_purchase', r.verified_purchase,
        'seller_response', r.seller_response,
        'seller_response_at', r.seller_response_at,
        'created_at', r.created_at
      ) order by r.created_at desc)
      from public.shop_reviews r
      where r.status = 'published' and (p_product_id is null or r.product_id = p_product_id)
    ), '[]'::jsonb),
    'storeReviews', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', sr.id,
        'store_id', sr.store_id,
        'rating', sr.rating,
        'service_rating', sr.service_rating,
        'communication_rating', sr.communication_rating,
        'delivery_rating', sr.delivery_rating,
        'body', sr.body,
        'verified_purchase', sr.verified_purchase,
        'seller_response', sr.seller_response,
        'seller_response_at', sr.seller_response_at,
        'created_at', sr.created_at
      ) order by sr.created_at desc)
      from public.shop_store_reviews sr
      where sr.status = 'published' and (p_store_id is null or sr.store_id = p_store_id)
    ), '[]'::jsonb)
  )
$$;

create or replace function public.adishop_recompute_product_rating(p_product_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare avg_rating numeric(3,2); review_total integer; distribution jsonb;
begin
  select coalesce(round(avg(rating)::numeric, 2), 0), count(*)
  into avg_rating, review_total
  from public.shop_reviews
  where product_id = p_product_id and status = 'published';

  select coalesce(jsonb_object_agg(rating::text, total), '{}'::jsonb)
  into distribution
  from (
    select rating, count(*) total
    from public.shop_reviews
    where product_id = p_product_id and status = 'published'
    group by rating
  ) grouped;

  update public.shop_products
  set rating_average = coalesce(avg_rating, 0),
      rating_count = coalesce(review_total, 0),
      rating_distribution = coalesce(distribution, '{}'::jsonb),
      updated_at = now()
  where id = p_product_id;

  return jsonb_build_object('rating_average', coalesce(avg_rating, 0), 'rating_count', coalesce(review_total, 0), 'rating_distribution', coalesce(distribution, '{}'::jsonb));
end;
$$;

create or replace function public.adishop_recompute_store_rating(p_store_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare avg_rating numeric(3,2); review_total integer; service_avg numeric(3,2); comm_avg numeric(3,2); delivery_avg numeric(3,2);
begin
  select coalesce(round(avg(rating)::numeric, 2), 0), count(*),
         coalesce(round(avg(service_rating)::numeric, 2), 0),
         coalesce(round(avg(communication_rating)::numeric, 2), 0),
         coalesce(round(avg(delivery_rating)::numeric, 2), 0)
  into avg_rating, review_total, service_avg, comm_avg, delivery_avg
  from public.shop_store_reviews
  where store_id = p_store_id and status = 'published';

  update public.shop_stores
  set average_store_rating = coalesce(avg_rating, 0),
      rating_count = coalesce(review_total, 0),
      service_rating_average = coalesce(service_avg, 0),
      communication_rating_average = coalesce(comm_avg, 0),
      delivery_rating_average = coalesce(delivery_avg, 0),
      updated_at = now()
  where id = p_store_id;

  return jsonb_build_object('rating_average', coalesce(avg_rating, 0), 'rating_count', coalesce(review_total, 0));
end;
$$;

create or replace function public.adishop_post_delivery_actions(p_profile_id uuid, p_order_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare order_record public.shop_orders%rowtype; completed_at_value timestamptz; review_deadline_value timestamptz; refund_deadline_value timestamptz; exchange_deadline_value timestamptz; store_policy record; product_review_ids jsonb; store_review_ids jsonb; refund_ids jsonb; exchange_ids jsonb; disabled jsonb := '{}'::jsonb; is_completed boolean;
begin
  select * into order_record from public.shop_orders where id = p_order_id and profile_id = p_profile_id;
  if not found then raise exception 'order-not-found'; end if;

  is_completed := coalesce(order_record.fulfillment_status, order_record.status) in ('delivered','completed')
    or order_record.status in ('delivered','completed');
  completed_at_value := coalesce(order_record.completed_at, (
    select max(delivered_at) from public.shop_order_groups where order_id = p_order_id
  ), order_record.updated_at);

  select
    least(coalesce(max(s.review_window_days), 30), 180) as review_days,
    least(coalesce(max(s.refund_window_days), 7), 30) as refund_days,
    least(coalesce(max(s.exchange_window_days), 7), 30) as exchange_days,
    bool_or(s.returns_accepted) as returns_accepted,
    bool_or(s.exchanges_accepted) as exchanges_accepted
  into store_policy
  from public.shop_order_groups g
  join public.shop_stores s on s.id = g.store_id
  where g.order_id = p_order_id;

  review_deadline_value := completed_at_value + make_interval(days => coalesce(store_policy.review_days, 30));
  refund_deadline_value := completed_at_value + make_interval(days => coalesce(store_policy.refund_days, 7));
  exchange_deadline_value := completed_at_value + make_interval(days => coalesce(store_policy.exchange_days, 7));

  select coalesce(jsonb_object_agg(order_item_id, id), '{}'::jsonb)
  into product_review_ids
  from public.shop_reviews
  where profile_id = p_profile_id and order_id = p_order_id and status <> 'deleted_by_buyer';

  select coalesce(jsonb_object_agg(order_group_id, id), '{}'::jsonb)
  into store_review_ids
  from public.shop_store_reviews
  where profile_id = p_profile_id and order_id = p_order_id and status <> 'deleted_by_buyer';

  select coalesce(jsonb_agg(id), '[]'::jsonb)
  into refund_ids
  from public.shop_refund_requests
  where buyer_profile_id = p_profile_id and order_id = p_order_id and status in ('submitted','under_review','awaiting_buyer','awaiting_seller','approved','partially_approved');

  select coalesce(jsonb_agg(id), '[]'::jsonb)
  into exchange_ids
  from public.shop_exchange_requests
  where buyer_profile_id = p_profile_id and order_id = p_order_id and status in ('submitted','under_review','awaiting_buyer','awaiting_seller','approved','awaiting_return','replacement_processing','replacement_shipped');

  if not is_completed then disabled := disabled || jsonb_build_object('post_delivery', 'Order is not completed yet.'); end if;
  if now() > review_deadline_value then disabled := disabled || jsonb_build_object('review', 'Review window has ended.'); end if;
  if now() > refund_deadline_value then disabled := disabled || jsonb_build_object('refund', 'Refund window has ended.'); end if;
  if now() > exchange_deadline_value then disabled := disabled || jsonb_build_object('exchange', 'Exchange window has ended.'); end if;

  return jsonb_build_object(
    'is_completed', is_completed,
    'can_review_product', is_completed and now() <= review_deadline_value,
    'can_review_store', is_completed and now() <= review_deadline_value,
    'can_request_refund', is_completed and coalesce(store_policy.returns_accepted, true) and now() <= refund_deadline_value,
    'can_request_exchange', is_completed and coalesce(store_policy.exchanges_accepted, true) and now() <= exchange_deadline_value,
    'can_open_buyer_protection', true,
    'can_contact_seller', true,
    'can_buy_again', true,
    'can_view_receipt', true,
    'can_view_timeline', true,
    'review_deadline', review_deadline_value,
    'refund_deadline', refund_deadline_value,
    'exchange_deadline', exchange_deadline_value,
    'existing_product_review_ids', coalesce(product_review_ids, '{}'::jsonb),
    'existing_store_review_ids', coalesce(store_review_ids, '{}'::jsonb),
    'active_refund_request_ids', coalesce(refund_ids, '[]'::jsonb),
    'active_exchange_request_ids', coalesce(exchange_ids, '[]'::jsonb),
    'disabled_reasons', disabled
  );
end;
$$;

create or replace function public.adishop_buyer_create_product_review(
  p_profile_id uuid,
  p_order_id uuid,
  p_order_item_id uuid,
  p_rating integer,
  p_title text,
  p_body text,
  p_idempotency_key text,
  p_request_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare item_record record; action_contract jsonb; review_record public.shop_reviews%rowtype; store_owner uuid;
begin
  if p_rating not between 1 and 5 then raise exception 'invalid-rating'; end if;
  if length(trim(coalesce(p_idempotency_key,''))) < 8 then raise exception 'invalid-idempotency-key'; end if;

  select i.*, g.store_id, o.profile_id, o.fulfillment_status, o.status as order_status, p.id as product_id
  into item_record
  from public.shop_order_items i
  join public.shop_order_groups g on g.id = i.order_group_id
  join public.shop_orders o on o.id = i.order_id
  join public.shop_products p on p.id = i.product_id
  where i.id = p_order_item_id and i.order_id = p_order_id and o.profile_id = p_profile_id;
  if not found then raise exception 'order-item-not-found'; end if;

  action_contract := public.adishop_post_delivery_actions(p_profile_id, p_order_id);
  if not coalesce((action_contract->>'can_review_product')::boolean, false) then raise exception 'review-not-eligible'; end if;

  select * into review_record from public.shop_reviews where profile_id = p_profile_id and idempotency_key = p_idempotency_key;
  if found then return jsonb_build_object('review', row_to_json(review_record), 'idempotent', true); end if;

  select * into review_record from public.shop_reviews where profile_id = p_profile_id and order_item_id = p_order_item_id and status <> 'deleted_by_buyer';
  if found then raise exception 'review-already-exists'; end if;

  insert into public.shop_reviews(profile_id, product_id, store_id, order_id, order_item_id, rating, title, body, status, verified_purchase, idempotency_key)
  values (p_profile_id, item_record.product_id, item_record.store_id, p_order_id, p_order_item_id, p_rating, left(trim(coalesce(p_title,'')), 120), left(trim(coalesce(p_body,'')), 2000), 'published', true, p_idempotency_key)
  returning * into review_record;

  insert into public.shop_review_events(review_type, review_id, actor_profile_id, actor_role, event_type, next_status, public_message, request_id)
  values ('product', review_record.id, p_profile_id, 'BUYER', 'created', review_record.status, 'Product review submitted.', p_request_id);

  perform public.adishop_recompute_product_rating(review_record.product_id);
  select owner_profile_id into store_owner from public.shop_stores where id = item_record.store_id;
  if store_owner is not null then
    perform public.adishop_create_notification(store_owner, 'review_received', 'Review received', 'A buyer reviewed one of your products.', jsonb_build_object('reviewId', review_record.id, 'productId', review_record.product_id));
  end if;
  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'buyer_review.create', 'shop_review', review_record.id, jsonb_build_object('orderId', p_order_id, 'requestId', p_request_id));
  return jsonb_build_object('review', row_to_json(review_record), 'idempotent', false);
end;
$$;

create or replace function public.adishop_buyer_create_store_review(
  p_profile_id uuid,
  p_order_id uuid,
  p_order_group_id uuid,
  p_rating integer,
  p_service_rating integer,
  p_communication_rating integer,
  p_delivery_rating integer,
  p_body text,
  p_idempotency_key text,
  p_request_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare group_record record; action_contract jsonb; review_record public.shop_store_reviews%rowtype; store_owner uuid;
begin
  if p_rating not between 1 and 5 or p_service_rating not between 1 and 5 or p_communication_rating not between 1 and 5 or p_delivery_rating not between 1 and 5 then raise exception 'invalid-rating'; end if;
  if length(trim(coalesce(p_idempotency_key,''))) < 8 then raise exception 'invalid-idempotency-key'; end if;
  select g.*, o.profile_id into group_record
  from public.shop_order_groups g
  join public.shop_orders o on o.id = g.order_id
  where g.id = p_order_group_id and g.order_id = p_order_id and o.profile_id = p_profile_id;
  if not found then raise exception 'order-group-not-found'; end if;
  action_contract := public.adishop_post_delivery_actions(p_profile_id, p_order_id);
  if not coalesce((action_contract->>'can_review_store')::boolean, false) then raise exception 'review-not-eligible'; end if;
  select * into review_record from public.shop_store_reviews where profile_id = p_profile_id and idempotency_key = p_idempotency_key;
  if found then return jsonb_build_object('review', row_to_json(review_record), 'idempotent', true); end if;
  select * into review_record from public.shop_store_reviews where profile_id = p_profile_id and order_group_id = p_order_group_id and status <> 'deleted_by_buyer';
  if found then raise exception 'review-already-exists'; end if;
  insert into public.shop_store_reviews(profile_id, store_id, order_id, order_group_id, rating, service_rating, communication_rating, delivery_rating, body, status, verified_purchase, idempotency_key)
  values (p_profile_id, group_record.store_id, p_order_id, p_order_group_id, p_rating, p_service_rating, p_communication_rating, p_delivery_rating, left(trim(coalesce(p_body,'')), 2000), 'published', true, p_idempotency_key)
  returning * into review_record;
  insert into public.shop_review_events(review_type, review_id, actor_profile_id, actor_role, event_type, next_status, public_message, request_id)
  values ('store', review_record.id, p_profile_id, 'BUYER', 'created', review_record.status, 'Store review submitted.', p_request_id);
  perform public.adishop_recompute_store_rating(review_record.store_id);
  select owner_profile_id into store_owner from public.shop_stores where id = group_record.store_id;
  if store_owner is not null then
    perform public.adishop_create_notification(store_owner, 'store_review_received', 'Store review received', 'A buyer reviewed your store.', jsonb_build_object('reviewId', review_record.id, 'storeId', review_record.store_id));
  end if;
  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'buyer_store_review.create', 'shop_store_review', review_record.id, jsonb_build_object('orderId', p_order_id, 'requestId', p_request_id));
  return jsonb_build_object('review', row_to_json(review_record), 'idempotent', false);
end;
$$;

create or replace function public.adishop_buyer_order_reviews(p_profile_id uuid, p_order_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if not exists (select 1 from public.shop_orders where id = p_order_id and profile_id = p_profile_id) then raise exception 'order-not-found'; end if;
  return jsonb_build_object(
    'productReviews', coalesce((select jsonb_agg(row_to_json(r) order by r.created_at desc) from public.shop_reviews r where r.profile_id = p_profile_id and r.order_id = p_order_id), '[]'::jsonb),
    'storeReviews', coalesce((select jsonb_agg(row_to_json(sr) order by sr.created_at desc) from public.shop_store_reviews sr where sr.profile_id = p_profile_id and sr.order_id = p_order_id), '[]'::jsonb)
  );
end;
$$;

create or replace function public.adishop_buyer_create_refund_request(
  p_profile_id uuid,
  p_order_id uuid,
  p_order_item_id uuid,
  p_quantity integer,
  p_reason_code text,
  p_description text,
  p_idempotency_key text,
  p_request_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare item_record record; action_contract jsonb; request_record public.shop_refund_requests%rowtype; amount_value integer; store_owner uuid;
begin
  if p_quantity < 1 then raise exception 'invalid-quantity'; end if;
  if p_reason_code = 'other' and length(trim(coalesce(p_description,''))) < 10 then raise exception 'description-required'; end if;
  select i.*, g.store_id, g.id as group_id, o.currency, s.owner_profile_id
  into item_record
  from public.shop_order_items i
  join public.shop_order_groups g on g.id = i.order_group_id
  join public.shop_orders o on o.id = i.order_id
  join public.shop_stores s on s.id = g.store_id
  where i.id = p_order_item_id and i.order_id = p_order_id and o.profile_id = p_profile_id;
  if not found then raise exception 'order-item-not-found'; end if;
  if p_quantity > item_record.quantity then raise exception 'invalid-quantity'; end if;
  action_contract := public.adishop_post_delivery_actions(p_profile_id, p_order_id);
  if not coalesce((action_contract->>'can_request_refund')::boolean, false) then raise exception 'refund-not-eligible'; end if;
  select * into request_record from public.shop_refund_requests where buyer_profile_id = p_profile_id and idempotency_key = p_idempotency_key;
  if found then return jsonb_build_object('refundRequest', row_to_json(request_record), 'idempotent', true); end if;
  if exists (select 1 from public.shop_exchange_request_items eri join public.shop_exchange_requests er on er.id = eri.exchange_request_id where eri.order_item_id = p_order_item_id and er.status in ('submitted','under_review','awaiting_buyer','awaiting_seller','approved','awaiting_return','replacement_processing','replacement_shipped')) then raise exception 'conflicting-exchange-request'; end if;
  if exists (select 1 from public.shop_refund_request_items rri join public.shop_refund_requests rr on rr.id = rri.refund_request_id where rri.order_item_id = p_order_item_id and rr.status in ('submitted','under_review','awaiting_buyer','awaiting_seller','approved','partially_approved')) then raise exception 'refund-already-active'; end if;
  amount_value := item_record.unit_price_minor * p_quantity;
  insert into public.shop_refund_requests(order_id, order_group_id, buyer_profile_id, store_id, seller_profile_id, reason_code, description, requested_amount, currency, idempotency_key)
  values (p_order_id, item_record.group_id, p_profile_id, item_record.store_id, item_record.owner_profile_id, p_reason_code, left(trim(coalesce(p_description,'')), 1600), amount_value, item_record.currency, p_idempotency_key)
  returning * into request_record;
  insert into public.shop_refund_request_items(refund_request_id, order_item_id, quantity, unit_price, requested_amount)
  values (request_record.id, p_order_item_id, p_quantity, item_record.unit_price_minor, amount_value);
  insert into public.shop_refund_events(refund_request_id, actor_profile_id, actor_role, event_code, next_status, public_message, request_id)
  values (request_record.id, p_profile_id, 'BUYER', 'buyer_refund.create', request_record.status, 'Refund request submitted.', p_request_id);
  if request_record.seller_profile_id is not null then perform public.adishop_create_notification(request_record.seller_profile_id, 'refund_request_received', 'Refund request', 'A buyer submitted a refund request.', jsonb_build_object('refundRequestId', request_record.id, 'orderId', p_order_id)); end if;
  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'buyer_refund.create', 'shop_refund_request', request_record.id, jsonb_build_object('orderId', p_order_id, 'requestId', p_request_id));
  return jsonb_build_object('refundRequest', row_to_json(request_record), 'idempotent', false);
end;
$$;

create or replace function public.adishop_buyer_create_exchange_request(
  p_profile_id uuid,
  p_order_id uuid,
  p_order_item_id uuid,
  p_requested_variant_id uuid,
  p_quantity integer,
  p_reason_code text,
  p_description text,
  p_idempotency_key text,
  p_request_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare item_record record; action_contract jsonb; request_record public.shop_exchange_requests%rowtype;
begin
  if p_quantity < 1 then raise exception 'invalid-quantity'; end if;
  if p_reason_code = 'other' and length(trim(coalesce(p_description,''))) < 10 then raise exception 'description-required'; end if;
  select i.*, g.store_id, g.id as group_id, s.owner_profile_id
  into item_record
  from public.shop_order_items i
  join public.shop_order_groups g on g.id = i.order_group_id
  join public.shop_orders o on o.id = i.order_id
  join public.shop_stores s on s.id = g.store_id
  where i.id = p_order_item_id and i.order_id = p_order_id and o.profile_id = p_profile_id;
  if not found then raise exception 'order-item-not-found'; end if;
  if p_quantity > item_record.quantity then raise exception 'invalid-quantity'; end if;
  if p_requested_variant_id is not null and not exists (select 1 from public.shop_product_variants where id = p_requested_variant_id and product_id = item_record.product_id and status = 'active') then raise exception 'invalid-variant'; end if;
  action_contract := public.adishop_post_delivery_actions(p_profile_id, p_order_id);
  if not coalesce((action_contract->>'can_request_exchange')::boolean, false) then raise exception 'exchange-not-eligible'; end if;
  select * into request_record from public.shop_exchange_requests where buyer_profile_id = p_profile_id and idempotency_key = p_idempotency_key;
  if found then return jsonb_build_object('exchangeRequest', row_to_json(request_record), 'idempotent', true); end if;
  if exists (select 1 from public.shop_refund_request_items rri join public.shop_refund_requests rr on rr.id = rri.refund_request_id where rri.order_item_id = p_order_item_id and rr.status in ('submitted','under_review','awaiting_buyer','awaiting_seller','approved','partially_approved')) then raise exception 'conflicting-refund-request'; end if;
  if exists (select 1 from public.shop_exchange_request_items eri join public.shop_exchange_requests er on er.id = eri.exchange_request_id where eri.order_item_id = p_order_item_id and er.status in ('submitted','under_review','awaiting_buyer','awaiting_seller','approved','awaiting_return','replacement_processing','replacement_shipped')) then raise exception 'exchange-already-active'; end if;
  insert into public.shop_exchange_requests(order_id, order_group_id, buyer_profile_id, store_id, seller_profile_id, reason_code, description, idempotency_key)
  values (p_order_id, item_record.group_id, p_profile_id, item_record.store_id, item_record.owner_profile_id, p_reason_code, left(trim(coalesce(p_description,'')), 1600), p_idempotency_key)
  returning * into request_record;
  insert into public.shop_exchange_request_items(exchange_request_id, order_item_id, requested_variant_id, quantity)
  values (request_record.id, p_order_item_id, p_requested_variant_id, p_quantity);
  insert into public.shop_exchange_events(exchange_request_id, actor_profile_id, actor_role, event_code, next_status, public_message, request_id)
  values (request_record.id, p_profile_id, 'BUYER', 'buyer_exchange.create', request_record.status, 'Exchange request submitted.', p_request_id);
  if request_record.seller_profile_id is not null then perform public.adishop_create_notification(request_record.seller_profile_id, 'exchange_request_received', 'Exchange request', 'A buyer submitted an exchange request.', jsonb_build_object('exchangeRequestId', request_record.id, 'orderId', p_order_id)); end if;
  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'buyer_exchange.create', 'shop_exchange_request', request_record.id, jsonb_build_object('orderId', p_order_id, 'requestId', p_request_id));
  return jsonb_build_object('exchangeRequest', row_to_json(request_record), 'idempotent', false);
end;
$$;

create or replace function public.adishop_buyer_request_payloads(p_profile_id uuid, p_order_id uuid default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  return jsonb_build_object(
    'refundRequests', coalesce((
      select jsonb_agg(to_jsonb(r) || jsonb_build_object(
        'reason_label', public.adishop_request_reason_label(r.reason_code),
        'items', coalesce((select jsonb_agg(to_jsonb(i)) from public.shop_refund_request_items i where i.refund_request_id = r.id), '[]'::jsonb),
        'events', coalesce((select jsonb_agg(jsonb_build_object('event_code', e.event_code, 'next_status', e.next_status, 'public_message', e.public_message, 'created_at', e.created_at) order by e.created_at asc) from public.shop_refund_events e where e.refund_request_id = r.id), '[]'::jsonb)
      ) order by r.created_at desc)
      from public.shop_refund_requests r
      where r.buyer_profile_id = p_profile_id and (p_order_id is null or r.order_id = p_order_id)
    ), '[]'::jsonb),
    'exchangeRequests', coalesce((
      select jsonb_agg(to_jsonb(x) || jsonb_build_object(
        'reason_label', public.adishop_request_reason_label(x.reason_code),
        'items', coalesce((select jsonb_agg(to_jsonb(i)) from public.shop_exchange_request_items i where i.exchange_request_id = x.id), '[]'::jsonb),
        'events', coalesce((select jsonb_agg(jsonb_build_object('event_code', e.event_code, 'next_status', e.next_status, 'public_message', e.public_message, 'created_at', e.created_at) order by e.created_at asc) from public.shop_exchange_events e where e.exchange_request_id = x.id), '[]'::jsonb)
      ) order by x.created_at desc)
      from public.shop_exchange_requests x
      where x.buyer_profile_id = p_profile_id and (p_order_id is null or x.order_id = p_order_id)
    ), '[]'::jsonb)
  );
end;
$$;

create or replace function public.adishop_buyer_buy_again(p_profile_id uuid, p_order_id uuid, p_request_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare cart_id_value uuid; row_record record; added jsonb := '[]'::jsonb; unavailable jsonb := '[]'::jsonb; price_changed jsonb := '[]'::jsonb; stock_changed jsonb := '[]'::jsonb; current_price integer; available_qty integer;
begin
  if not exists (select 1 from public.shop_orders where id = p_order_id and profile_id = p_profile_id) then raise exception 'order-not-found'; end if;
  select id into cart_id_value from public.shop_carts where profile_id = p_profile_id order by created_at desc limit 1;
  if cart_id_value is null then
    insert into public.shop_carts(profile_id, currency) values (p_profile_id, 'ETB') returning id into cart_id_value;
  end if;
  for row_record in
    select i.product_id, i.variant_id, i.quantity, i.unit_price_minor, p.title, p.status, p.moderation_status, coalesce(v.price_minor, p.price_minor) as price_minor
    from public.shop_order_items i
    join public.shop_products p on p.id = i.product_id
    left join public.shop_product_variants v on v.id = i.variant_id
    where i.order_id = p_order_id
  loop
    select coalesce(sum(quantity_available - quantity_reserved), 0) into available_qty
    from public.shop_inventory
    where product_id = row_record.product_id and (row_record.variant_id is null or variant_id = row_record.variant_id);
    current_price := row_record.price_minor;
    if row_record.status <> 'active' or row_record.moderation_status <> 'approved' or available_qty < row_record.quantity then
      unavailable := unavailable || jsonb_build_array(jsonb_build_object('productId', row_record.product_id, 'title', row_record.title, 'reason', 'Unavailable or insufficient stock'));
    else
      insert into public.shop_cart_items(cart_id, product_id, variant_id, quantity, unit_price_minor)
      values (cart_id_value, row_record.product_id, row_record.variant_id, row_record.quantity, current_price)
      on conflict (cart_id, product_id, variant_id) do update
        set quantity = least(99, public.shop_cart_items.quantity + excluded.quantity),
            unit_price_minor = excluded.unit_price_minor,
            updated_at = now();
      added := added || jsonb_build_array(jsonb_build_object('productId', row_record.product_id, 'title', row_record.title, 'quantity', row_record.quantity));
      if current_price <> row_record.unit_price_minor then
        price_changed := price_changed || jsonb_build_array(jsonb_build_object('productId', row_record.product_id, 'oldPrice', row_record.unit_price_minor, 'currentPrice', current_price));
      end if;
      if available_qty < row_record.quantity * 2 then
        stock_changed := stock_changed || jsonb_build_array(jsonb_build_object('productId', row_record.product_id, 'available', available_qty));
      end if;
    end if;
  end loop;
  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'buyer.buy_again', 'shop_order', p_order_id, jsonb_build_object('requestId', p_request_id));
  return jsonb_build_object('added_items', added, 'unavailable_items', unavailable, 'price_changed_items', price_changed, 'stock_changed_items', stock_changed);
end;
$$;

create or replace function public.adishop_buyer_order_receipt(p_profile_id uuid, p_order_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if not exists (select 1 from public.shop_orders where id = p_order_id and profile_id = p_profile_id) then raise exception 'order-not-found'; end if;
  return (
    select jsonb_build_object(
      'order_number', o.order_number,
      'order_date', o.created_at,
      'currency', o.currency,
      'subtotal_minor', o.subtotal_minor,
      'delivery_fee_minor', o.delivery_fee_minor,
      'discount_minor', o.discount_minor,
      'total_minor', o.total_minor,
      'payment_status', o.payment_status,
      'fulfillment_status', o.fulfillment_status,
      'delivery_status', o.delivery_status,
      'completed_at', o.completed_at,
      'stores', coalesce((select jsonb_agg(jsonb_build_object(
        'store_name', g.store_name_snapshot,
        'status', g.fulfillment_status,
        'items', coalesce((select jsonb_agg(jsonb_build_object('title', i.title_snapshot, 'quantity', i.quantity, 'unit_price_minor', i.unit_price_minor, 'line_total_minor', i.line_total_minor)) from public.shop_order_items i where i.order_group_id = g.id), '[]'::jsonb)
      )) from public.shop_order_groups g where g.order_id = o.id), '[]'::jsonb),
      'payment', (select jsonb_build_object('method_code', p.method_code, 'status', p.status, 'amount_minor', p.amount_minor, 'currency', p.currency) from public.shop_payments p where p.order_id = o.id order by p.created_at desc limit 1)
    )
    from public.shop_orders o
    where o.id = p_order_id
  );
end;
$$;

create or replace function public.adishop_seller_request_payloads(p_profile_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare store_ids uuid[];
begin
  select coalesce(array_agg(id), '{}') into store_ids
  from public.shop_stores
  where owner_profile_id = p_profile_id
     or exists (select 1 from public.shop_store_members m where m.store_id = shop_stores.id and m.profile_id = p_profile_id and m.status = 'active');
  return jsonb_build_object(
    'refundRequests', coalesce((select jsonb_agg(to_jsonb(r) || jsonb_build_object('reason_label', public.adishop_request_reason_label(r.reason_code)) order by r.created_at desc) from public.shop_refund_requests r where r.store_id = any(store_ids)), '[]'::jsonb),
    'exchangeRequests', coalesce((select jsonb_agg(to_jsonb(x) || jsonb_build_object('reason_label', public.adishop_request_reason_label(x.reason_code)) order by x.created_at desc) from public.shop_exchange_requests x where x.store_id = any(store_ids)), '[]'::jsonb)
  );
end;
$$;

create or replace function public.adishop_admin_list_stores(p_profile_id uuid, p_status text default 'all', p_search text default '', p_limit integer default 30, p_offset integer default 0)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'store.moderation.read') and not public.adishop_profile_has_permission(p_profile_id, 'seller.application.read') then raise exception 'admin-forbidden'; end if;
  return coalesce((
    select jsonb_agg(to_jsonb(s) || jsonb_build_object(
      'owner_name', p.display_name,
      'owner_email', p.email,
      'product_count', (select count(*) from public.shop_products pr where pr.store_id = s.id and pr.deleted_at is null),
      'completed_order_count', (select count(*) from public.shop_order_groups g where g.store_id = s.id and g.fulfillment_status = 'delivered'),
      'open_issue_count', (select count(*) from public.shop_order_issues i where i.store_id = s.id and i.status in ('open','under_review','awaiting_buyer','awaiting_seller')),
      'warning_count', (select count(*) from public.shop_store_warnings w where w.store_id = s.id and w.status = 'open')
    ) order by s.created_at desc)
    from public.shop_stores s
    left join public.shop_profiles p on p.id = s.owner_profile_id
    where (p_status = 'all' or s.status = p_status)
      and (coalesce(p_search,'') = '' or s.name ilike '%' || p_search || '%' or p.display_name ilike '%' || p_search || '%')
    limit greatest(1, least(100, p_limit))
    offset greatest(0, p_offset)
  ), '[]'::jsonb);
end;
$$;

create or replace function public.adishop_admin_get_store(p_profile_id uuid, p_store_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare result jsonb;
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'store.moderation.read') and not public.adishop_profile_has_permission(p_profile_id, 'seller.application.read') then raise exception 'admin-forbidden'; end if;
  select jsonb_build_object(
    'store', to_jsonb(s),
    'owner', jsonb_build_object('id', p.id, 'display_name', p.display_name, 'email', p.email),
    'products', coalesce((select jsonb_agg(to_jsonb(pr) order by pr.created_at desc) from public.shop_products pr where pr.store_id = s.id limit 50), '[]'::jsonb),
    'warnings', coalesce((select jsonb_agg(to_jsonb(w) order by w.created_at desc) from public.shop_store_warnings w where w.store_id = s.id), '[]'::jsonb),
    'violations', coalesce((select jsonb_agg(to_jsonb(v) order by v.created_at desc) from public.shop_store_violations v where v.store_id = s.id), '[]'::jsonb),
    'statusEvents', coalesce((select jsonb_agg(to_jsonb(e) order by e.created_at desc) from public.shop_store_status_events e where e.store_id = s.id), '[]'::jsonb),
    'refundRequests', coalesce((select jsonb_agg(to_jsonb(r) order by r.created_at desc) from public.shop_refund_requests r where r.store_id = s.id limit 50), '[]'::jsonb),
    'exchangeRequests', coalesce((select jsonb_agg(to_jsonb(x) order by x.created_at desc) from public.shop_exchange_requests x where x.store_id = s.id limit 50), '[]'::jsonb)
  )
  into result
  from public.shop_stores s
  left join public.shop_profiles p on p.id = s.owner_profile_id
  where s.id = p_store_id;
  if result is null then raise exception 'store-not-found'; end if;
  return result;
end;
$$;

create or replace function public.adishop_admin_update_store_status(
  p_actor_profile_id uuid,
  p_store_id uuid,
  p_action text,
  p_reason_code text,
  p_seller_visible_message text,
  p_private_note text default '',
  p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare store_record public.shop_stores%rowtype; new_status text; old_state jsonb; new_state jsonb; permission text;
begin
  permission := case p_action
    when 'warn' then 'store.warn'
    when 'suspend' then 'store.suspend'
    when 'reactivate' then 'store.reactivate'
    when 'ban' then 'store.ban'
    when 'archive' then 'store.archive'
    when 'delete' then 'store.delete'
    else 'store.moderation.read'
  end;
  if not public.adishop_profile_has_permission(p_actor_profile_id, permission) then raise exception 'admin-forbidden'; end if;
  if p_action not in ('warn','suspend','reactivate','ban','archive','delete') then raise exception 'unsupported-action'; end if;
  if p_action <> 'reactivate' and length(trim(coalesce(p_seller_visible_message, p_reason_code, ''))) < 3 then raise exception 'reason-required'; end if;
  select * into store_record from public.shop_stores where id = p_store_id for update;
  if not found then raise exception 'store-not-found'; end if;
  old_state := to_jsonb(store_record);
  if p_action = 'delete' and exists (select 1 from public.shop_order_groups where store_id = p_store_id) then raise exception 'store-has-dependencies'; end if;

  new_status := case p_action
    when 'suspend' then 'suspended'
    when 'reactivate' then 'active'
    when 'ban' then 'banned'
    when 'archive' then 'archived'
    when 'delete' then 'deleted'
    else store_record.status
  end;

  if p_action = 'warn' then
    insert into public.shop_store_warnings(store_id, reason_code, severity, seller_visible_message, private_admin_note, created_by)
    values (p_store_id, coalesce(nullif(p_reason_code,''),'policy_violation'), 'medium', p_seller_visible_message, nullif(p_private_note,''), p_actor_profile_id);
  else
    update public.shop_stores
    set status = new_status,
        approval_status = case when new_status in ('suspended','banned') then 'suspended' when new_status = 'active' then 'approved' else approval_status end,
        suspended_at = case when new_status = 'suspended' then now() when new_status = 'active' then null else suspended_at end,
        deleted_at = case when new_status = 'deleted' then now() else deleted_at end,
        updated_at = now()
    where id = p_store_id;
  end if;

  insert into public.shop_store_status_events(store_id, actor_profile_id, actor_role, event_code, previous_status, next_status, seller_visible_message, private_admin_note, reason_code, request_id)
  values (p_store_id, p_actor_profile_id, 'ADMIN', 'admin_store.' || p_action, store_record.status, new_status, nullif(p_seller_visible_message,''), nullif(p_private_note,''), p_reason_code, p_request_id);

  select to_jsonb(s) into new_state from public.shop_stores s where s.id = p_store_id;
  perform public.adishop_admin_audit(p_actor_profile_id, 'admin_store.' || p_action, 'shop_store', p_store_id, old_state, coalesce(new_state, old_state), p_reason_code, jsonb_build_object('requestId', p_request_id), p_request_id);
  if store_record.owner_profile_id is not null then
    perform public.adishop_create_notification(store_record.owner_profile_id, 'store_' || p_action, 'Store update', coalesce(nullif(p_seller_visible_message,''), 'Your store status was updated.'), jsonb_build_object('storeId', p_store_id, 'action', p_action));
  end if;
  return public.adishop_admin_get_store(p_actor_profile_id, p_store_id);
end;
$$;

-- Grants for service-role-only RPCs. Public helper labels remain callable via service only where sensitive.
do $$
declare fn text;
begin
  foreach fn in array array[
    'public.adishop_post_delivery_actions(uuid, uuid)',
    'public.adishop_buyer_create_product_review(uuid, uuid, uuid, integer, text, text, text, text)',
    'public.adishop_buyer_create_store_review(uuid, uuid, uuid, integer, integer, integer, integer, text, text, text)',
    'public.adishop_buyer_order_reviews(uuid, uuid)',
    'public.adishop_buyer_create_refund_request(uuid, uuid, uuid, integer, text, text, text, text)',
    'public.adishop_buyer_create_exchange_request(uuid, uuid, uuid, uuid, integer, text, text, text, text)',
    'public.adishop_buyer_request_payloads(uuid, uuid)',
    'public.adishop_buyer_buy_again(uuid, uuid, text)',
    'public.adishop_buyer_order_receipt(uuid, uuid)',
    'public.adishop_seller_request_payloads(uuid)',
    'public.adishop_admin_list_stores(uuid, text, text, integer, integer)',
    'public.adishop_admin_get_store(uuid, uuid)',
    'public.adishop_admin_update_store_status(uuid, uuid, text, text, text, text, text)',
    'public.adishop_recompute_product_rating(uuid)',
    'public.adishop_recompute_store_rating(uuid)'
  ] loop
    execute format('revoke all on function %s from public', fn);
    execute format('revoke execute on function %s from anon', fn);
    execute format('revoke execute on function %s from authenticated', fn);
    execute format('grant execute on function %s to service_role', fn);
  end loop;
end $$;
