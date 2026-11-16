-- REVIEW DRAFT ONLY. Depends on 01_trust_schema; do not execute on production.
begin;
create table public.shop_delivery_zones (
  id uuid primary key default gen_random_uuid(),
  country_code char(2) not null default 'ET' check (country_code = 'ET'),
  region_code text not null,
  city_code text,
  subcity_code text,
  active boolean not null default true,
  unique nulls not distinct (country_code, region_code, city_code, subcity_code),
  check (subcity_code is null or city_code is not null)
);
-- Proposed Addis Ababa codes; confirm canonical naming/localization before rollout.
insert into public.shop_delivery_zones(country_code,region_code,city_code,subcity_code)
values ('ET','ADDIS_ABABA',null,null),('ET','ADDIS_ABABA','ADDIS_ABABA',null),
  ('ET','ADDIS_ABABA','ADDIS_ABABA','ADDIS_KETEMA'),
  ('ET','ADDIS_ABABA','ADDIS_ABABA','AKAKI_KALITI'),
  ('ET','ADDIS_ABABA','ADDIS_ABABA','ARADA'),
  ('ET','ADDIS_ABABA','ADDIS_ABABA','BOLE'),
  ('ET','ADDIS_ABABA','ADDIS_ABABA','GULELE'),
  ('ET','ADDIS_ABABA','ADDIS_ABABA','KIRKOS'),
  ('ET','ADDIS_ABABA','ADDIS_ABABA','KOLFE_KERANIYO'),
  ('ET','ADDIS_ABABA','ADDIS_ABABA','LEMI_KURA'),
  ('ET','ADDIS_ABABA','ADDIS_ABABA','LIDETA'),
  ('ET','ADDIS_ABABA','ADDIS_ABABA','NIFAS_SILK_LAFTO'),
  ('ET','ADDIS_ABABA','ADDIS_ABABA','YEKA')
on conflict do nothing;
create table public.shop_shipping_methods (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete restrict,
  method_code text not null,
  label text not null,
  base_fee_minor integer not null check (base_fee_minor >= 0),
  currency char(3) not null default 'ETB' check (currency = 'ETB'),
  min_delivery_hours integer check (min_delivery_hours > 0),
  max_delivery_hours integer check (max_delivery_hours >= min_delivery_hours),
  active boolean not null default false,
  unique (store_id, method_code),
  unique (id, store_id)
);
create table public.shop_delivery_rules (
  case_id uuid primary key,
  store_id uuid not null,
  zone_id uuid not null references public.shop_delivery_zones(id) on delete restrict,
  method_id uuid not null,
  product_id uuid references public.shop_products(id) on delete restrict,
  minimum_group_subtotal_minor integer not null default 0 check (minimum_group_subtotal_minor >= 0),
  delivery_fee_minor integer not null check (delivery_fee_minor >= 0),
  funding_source text not null check (funding_source in ('seller','platform')),
  benefit_type text not null check (benefit_type in ('free_delivery','fast_shipping','standard')),
  foreign key (case_id, store_id) references public.shop_benefit_cases(id, store_id) on delete restrict,
  foreign key (method_id, store_id) references public.shop_shipping_methods(id, store_id) on delete restrict,
  check (benefit_type <> 'free_delivery' or delivery_fee_minor = 0)
);
create index shop_delivery_rules_lookup_idx on public.shop_delivery_rules(store_id, zone_id, product_id, method_id);

create table public.shop_price_offers (
  case_id uuid primary key,
  store_id uuid not null,
  product_id uuid not null references public.shop_products(id) on delete restrict,
  variant_id uuid references public.shop_product_variants(id) on delete restrict,
  campaign_id uuid references public.shop_campaigns(id) on delete restrict,
  currency char(3) not null default 'ETB' check (currency = 'ETB'),
  offer_type text not null check (offer_type in ('promotion','quantity_tier')),
  offer_price_minor integer not null check (offer_price_minor > 0),
  reference_price_minor integer check (reference_price_minor > offer_price_minor),
  reference_window_start timestamptz,
  reference_window_end timestamptz,
  minimum_quantity integer not null default 1 check (minimum_quantity > 0),
  maximum_quantity integer check (maximum_quantity >= minimum_quantity),
  foreign key (case_id, store_id) references public.shop_benefit_cases(id, store_id) on delete restrict,
  check (reference_price_minor is null or (reference_window_start is not null and reference_window_end > reference_window_start))
);
create index shop_price_offers_lookup_idx on public.shop_price_offers(product_id, variant_id, minimum_quantity);

-- Required before deployment: transactional product/variant/campaign ownership
-- guards; no overlapping conflicting approved tiers; historical price snapshots;
-- platform-funding ledger and checkout quote integration. Draft tables alone
-- intentionally do not enable badges or alter checkout totals.
do $$ declare t text; begin
  foreach t in array array['shop_delivery_zones','shop_shipping_methods','shop_delivery_rules','shop_price_offers'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on public.%I from public, anon, authenticated', t);
    execute format('grant select, insert, update on public.%I to service_role', t);
  end loop;
end $$;
commit;
