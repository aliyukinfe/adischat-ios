-- Preserve the buyer-visible product reference used to start a marketplace chat.
-- Current availability remains live; this snapshot is presentation-only history.
alter table public.shop_conversations
  add column if not exists product_context_snapshot jsonb,
  add column if not exists product_context_snapshotted_at timestamptz;

alter table public.shop_conversations
  drop constraint if exists shop_conversations_product_context_snapshot_object;

alter table public.shop_conversations
  add constraint shop_conversations_product_context_snapshot_object
  check (product_context_snapshot is null or jsonb_typeof(product_context_snapshot) = 'object');

with primary_images as (
  select distinct on (i.product_id)
    i.product_id,
    i.image_url
  from public.shop_product_images i
  where i.deleted_at is null
  order by i.product_id, i.is_primary desc, i.display_order asc, i.created_at asc
), variant_context as (
  select
    v.id,
    v.title,
    v.options,
    v.price_minor,
    v.currency
  from public.shop_product_variants v
), snapshots as (
  select
    c.id,
    jsonb_strip_nulls(jsonb_build_object(
      'product_id', p.id,
      'title', p.title,
      'image_url', pi.image_url,
      'price_minor', coalesce(v.price_minor, p.price_minor),
      'currency', coalesce(v.currency, p.currency),
      'store_id', s.id,
      'store_name', s.name,
      'variant_id', v.id,
      'variant_title', v.title,
      'selected_options', coalesce(v.options, '{}'::jsonb),
      'selling_unit_code', p.selling_unit_code
    )) as snapshot
  from public.shop_conversations c
  join public.shop_products p on p.id = c.product_id and p.store_id = c.store_id
  join public.shop_stores s on s.id = c.store_id
  left join primary_images pi on pi.product_id = p.id
  left join variant_context v on v.id = c.product_variant_id and v.id is not null
  where c.context_type = 'product'
    and c.product_context_snapshot is null
)
update public.shop_conversations c
set product_context_snapshot = snapshots.snapshot,
    product_context_snapshotted_at = c.created_at
from snapshots
where c.id = snapshots.id;

comment on column public.shop_conversations.product_context_snapshot is
  'Immutable UI-safe product reference captured when a product conversation starts.';

revoke update(product_context_snapshot, product_context_snapshotted_at)
  on public.shop_conversations from anon, authenticated;
