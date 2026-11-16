-- LOCAL STAGING DRAFT ONLY. Stage A is additive; Stage B is NOT in this file.
-- Never apply to production without a reviewed data backfill, authorization RPC,
-- and separate approval. Raw public tables remain exposed until Stage B.
begin;

create schema if not exists shop_catalog_private;
revoke all on schema shop_catalog_private from public, anon, authenticated;

create table if not exists shop_catalog_private.source_content (
  entity_kind text not null check (entity_kind in ('product','image','variant','store')),
  entity_id uuid not null,
  source_version text not null,
  private_payload jsonb not null check (jsonb_typeof(private_payload) = 'object'),
  captured_at timestamptz not null default now(),
  primary key (entity_kind, entity_id, source_version)
);

create table if not exists shop_catalog_private.reviewed_content (
  entity_kind text not null check (entity_kind in ('product','image','variant','store')),
  entity_id uuid not null,
  source_version text not null,
  public_payload jsonb not null check (jsonb_typeof(public_payload) = 'object'),
  reviewer_profile_id uuid not null,
  reviewed_at timestamptz not null default now(),
  primary key (entity_kind, entity_id, source_version),
  foreign key (entity_kind, entity_id, source_version)
    references shop_catalog_private.source_content (entity_kind, entity_id, source_version)
);

alter table shop_catalog_private.source_content enable row level security;
alter table shop_catalog_private.reviewed_content enable row level security;
revoke all on shop_catalog_private.source_content,
  shop_catalog_private.reviewed_content from public, anon, authenticated;
grant usage on schema shop_catalog_private to service_role;
grant select, insert, update on shop_catalog_private.source_content,
  shop_catalog_private.reviewed_content to service_role;

-- No public views or direct public grants. A later authorized review RPC must
-- validate entity ownership, reviewer permission, source version and field
-- allowlists before inserting reviewed_content. It must not accept seller
-- supplied reviewer identity or public verification flags.
commit;
