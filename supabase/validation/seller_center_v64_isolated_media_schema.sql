-- Local disposable PostgreSQL QA only; supplements the V6.3 isolated schema.
alter table public.shop_product_images add column seller_profile_id uuid references public.shop_profiles(id);
alter table public.shop_product_variants add column primary_image_id uuid references public.shop_product_images(id);
create table public.shop_variant_media (
  variant_id uuid not null references public.shop_product_variants(id),
  product_image_id uuid not null references public.shop_product_images(id),
  position integer not null default 0,
  primary key (variant_id, product_image_id)
);
create table public.shop_audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_profile_id uuid references public.shop_profiles(id),
  action text not null,
  entity_type text not null,
  entity_id uuid,
  metadata jsonb not null default '{}'::jsonb
);
create function public.adishop_seller_get_product_for_store(
  p_profile_id uuid, p_store_id uuid, p_product_id uuid
)
returns jsonb language sql stable as $$
  select to_jsonb(p) || jsonb_build_object(
    'images', coalesce((select jsonb_agg(to_jsonb(i) order by i.display_order, i.id)
      from public.shop_product_images i
      where i.product_id = p.id and i.deleted_at is null), '[]'::jsonb)
  )
  from public.shop_products p join public.shop_stores s on s.id = p.store_id
  where p.id = p_product_id and p.store_id = p_store_id
    and s.owner_profile_id = p_profile_id;
$$;

grant all on public.shop_variant_media, public.shop_audit_logs to service_role;
