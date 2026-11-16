-- REVIEW DRAFT ONLY. Do not put in supabase/migrations or run on production.
begin;

create table public.shop_benefit_brands (
  id uuid primary key default gen_random_uuid(),
  canonical_name text not null check (length(trim(canonical_name)) between 2 and 120),
  normalized_name text not null unique check (normalized_name = lower(trim(normalized_name))),
  status text not null default 'active' check (status in ('active','retired')),
  created_at timestamptz not null default now()
);
create table public.shop_benefit_brand_aliases (
  id uuid primary key default gen_random_uuid(),
  brand_id uuid not null references public.shop_benefit_brands(id) on delete restrict,
  normalized_alias text not null unique check (normalized_alias = lower(trim(normalized_alias))),
  created_at timestamptz not null default now()
);

create table public.shop_benefit_cases (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete restrict,
  product_id uuid references public.shop_products(id) on delete restrict,
  brand_id uuid references public.shop_benefit_brands(id) on delete restrict,
  kind text not null check (kind in ('original_product','official_store','seller_warranty','manufacturer_warranty','delivery_rule','price_offer')),
  status text not null default 'draft' check (status in ('draft','submitted','under_review','approved','rejected','revoked')),
  submitted_by_profile_id uuid not null references public.shop_profiles(id) on delete restrict,
  reviewed_by_profile_id uuid references public.shop_profiles(id) on delete restrict,
  submitted_at timestamptz,
  reviewed_at timestamptz,
  effective_at timestamptz,
  expires_at timestamptz,
  revoked_at timestamptz,
  public_reason text,
  private_reason text,
  version integer not null default 1 check (version > 0),
  request_key text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (store_id, request_key),
  unique (id, store_id),
  check (expires_at is null or (effective_at is not null and expires_at > effective_at)),
  check ((kind in ('original_product','seller_warranty','manufacturer_warranty') and product_id is not null)
    or (kind in ('official_store','delivery_rule') and product_id is null)
    or (kind = 'price_offer' and product_id is not null)),
  check (kind <> 'official_store' or brand_id is not null)
);
create index shop_benefit_cases_effective_idx on public.shop_benefit_cases(kind, store_id, product_id, expires_at)
  where status = 'approved';
create index shop_benefit_cases_review_idx on public.shop_benefit_cases(status, submitted_at);

create table public.shop_product_benefit_claims (
  case_id uuid primary key references public.shop_benefit_cases(id) on delete restrict,
  issuer text not null check (issuer in ('seller','manufacturer','platform_review')),
  terms_summary text not null check (length(trim(terms_summary)) between 4 and 500),
  coverage_details text,
  duration_months integer check (duration_months between 1 and 120),
  terms_version integer not null default 1 check (terms_version > 0)
);
create table public.shop_store_brand_authorizations (
  case_id uuid primary key references public.shop_benefit_cases(id) on delete restrict,
  brand_id uuid not null references public.shop_benefit_brands(id) on delete restrict,
  authorization_scope text not null check (authorization_scope in ('all_brand_products','listed_product_ids')),
  covered_product_ids uuid[] not null default '{}',
  check (authorization_scope <> 'listed_product_ids' or cardinality(covered_product_ids) between 1 and 1000)
);

create table public.shop_benefit_evidence (
  id uuid primary key default gen_random_uuid(),
  case_id uuid not null references public.shop_benefit_cases(id) on delete restrict,
  bucket text not null default 'shop-benefit-evidence' check (bucket = 'shop-benefit-evidence'),
  storage_path text not null unique,
  mime_type text not null check (mime_type in ('image/jpeg','image/png','application/pdf')),
  evidence_type text not null check (evidence_type in ('authorization_letter','manufacturer_terms','invoice','seller_terms','delivery_policy','price_history','other')),
  document_expires_at timestamptz,
  size_bytes bigint not null check (size_bytes between 1 and 10485760),
  sha256_hex text not null check (sha256_hex ~ '^[0-9a-f]{64}$'),
  uploaded_by_profile_id uuid not null references public.shop_profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index shop_benefit_evidence_case_idx on public.shop_benefit_evidence(case_id) where deleted_at is null;
create unique index shop_benefit_evidence_content_once_idx on public.shop_benefit_evidence(case_id,sha256_hex)
  where deleted_at is null;

create table public.shop_benefit_events (
  id uuid primary key default gen_random_uuid(),
  case_id uuid not null references public.shop_benefit_cases(id) on delete restrict,
  actor_profile_id uuid references public.shop_profiles(id) on delete restrict,
  action text not null check (action in ('create','evidence_added','submit','start_review','approve','reject','revoke','expire')),
  previous_status text,
  new_status text not null,
  case_version integer not null,
  reason text,
  request_key text not null,
  created_at timestamptz not null default now(),
  unique (case_id, request_key)
);
create index shop_benefit_events_case_idx on public.shop_benefit_events(case_id, created_at);

-- A trigger/RPC must enforce product.store_id = case.store_id, brand scope,
-- immutable event rows, and all lifecycle writes before this draft is deployable.
-- Existing shop_products.brand_id is not backfilled or constrained here.
do $$ declare t text; begin
  foreach t in array array['shop_benefit_brands','shop_benefit_brand_aliases','shop_benefit_cases',
    'shop_product_benefit_claims','shop_store_brand_authorizations','shop_benefit_evidence','shop_benefit_events'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on public.%I from public, anon, authenticated', t);
    execute format('grant select, insert, update on public.%I to service_role', t);
  end loop;
end $$;
revoke update, delete on public.shop_benefit_cases from service_role;
revoke delete on public.shop_benefit_events from service_role;
commit;
