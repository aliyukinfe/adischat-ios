-- LOCAL QA CANDIDATE ONLY. Depends on phase9g9; never apply to production.
begin;

create table if not exists public.shop_qa_reviewed_variants (
  variant_id uuid primary key references public.shop_product_variants(id),
  variant_updated_at timestamptz not null,
  reviewed_by_profile_id uuid not null references public.shop_profiles(id),
  reviewed_at timestamptz not null default now(),
  title text not null, options jsonb not null default '{}'::jsonb,
  price_minor integer not null check (price_minor >= 0)
);
alter table public.shop_qa_reviewed_variants enable row level security;
revoke all on public.shop_qa_reviewed_variants from public,anon,authenticated;
grant select on public.shop_qa_reviewed_variants to service_role;

create or replace function public.adishop_qa_variant_reviewed(p_variant_id uuid, p_store_id uuid)
returns boolean language sql stable security definer set search_path='' as $$
  select exists (
    select 1 from public.shop_qa_reviewed_variants r
    join public.shop_product_variants v on v.id=r.variant_id
    join public.shop_stores s on s.id=p_store_id
    join public.shop_profiles reviewer on reviewer.id=r.reviewed_by_profile_id and reviewer.status='active'
    join public.shop_user_roles ur on ur.profile_id=reviewer.id and ur.revoked_at is null
    join public.shop_roles role on role.id=ur.role_id and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN')
    where v.id=p_variant_id and v.updated_at=r.variant_updated_at and v.status='active'
      and r.reviewed_by_profile_id is distinct from s.owner_profile_id
  )
$$;
revoke all on function public.adishop_qa_variant_reviewed(uuid,uuid) from public,anon,authenticated;

create or replace function public.adishop_qa_catalog_page(
  p_term text default null, p_category_slug text default null,
  p_min_price integer default null, p_max_price integer default null,
  p_in_stock boolean default false, p_sort text default 'newest',
  p_limit integer default 24, p_offset integer default 0
) returns setof jsonb language plpgsql stable security definer set search_path='' as $$
begin
  if p_limit not between 1 and 100 or p_offset not between 0 and 10000 or
     p_sort not in ('newest','oldest','updated','price_low','price_high') or
     length(coalesce(p_term,'')) > 80 or
     (p_term is not null and length(btrim(p_term)) < 2) or
     (p_min_price is not null and p_min_price < 0) or
     (p_max_price is not null and p_max_price < 0) then
    raise exception 'invalid-qa-catalog-query' using errcode='22023';
  end if;
  return query
  select jsonb_build_object(
    'id',p.id,'title',p.title,'short_description',p.short_description,
    'category_id',p.category_id,'category_slug',c.slug,
    'created_at',p.created_at,'price_minor',p.price_minor,
    'stock_status',p.stock_status,'has_variants',raw.has_variants,
    'shop_stores',jsonb_build_object('id',s.id,'name',s.name,'city',s.city),
    'shop_product_images','[]'::jsonb
  )
  from public.shop_qa_public_catalog p
  join public.shop_products raw on raw.id=p.id
  join public.shop_qa_public_stores s on s.id=p.store_id
  left join public.shop_categories c on c.id=p.category_id
  where (p_term is null or p.title ilike '%' || p_term || '%' or s.name ilike '%' || p_term || '%')
    and (p_category_slug is null or c.slug=p_category_slug)
    and (p_min_price is null or p.price_minor>=p_min_price)
    and (p_max_price is null or p.price_minor<=p_max_price)
    and (not p_in_stock or p.stock_status<>'out_of_stock')
    and not exists (
      select 1 from public.shop_product_variants v where v.product_id=p.id and v.status='active'
        and not public.adishop_qa_variant_reviewed(v.id,p.store_id)
    )
  order by
    case when p_sort='newest' then p.created_at end desc,
    case when p_sort='oldest' then p.created_at end asc,
    case when p_sort='updated' then raw.updated_at end desc,
    case when p_sort='price_low' then p.price_minor end asc,
    case when p_sort='price_high' then p.price_minor end desc,
    p.id desc
  limit p_limit offset p_offset;
end;
$$;

create or replace function public.adishop_qa_product_detail(p_product_id uuid)
returns jsonb language sql stable security definer set search_path='' as $$
  select jsonb_build_object(
    'id',p.id,'title',p.title,'short_description',p.short_description,
    'category_id',p.category_id,'category_slug',c.slug,'created_at',p.created_at,
    'price_minor',p.price_minor,'stock_status',p.stock_status,
    'has_variants',raw.has_variants,
    'shop_stores',jsonb_build_object('id',s.id,'name',s.name,'city',s.city),
    'shop_product_images','[]'::jsonb,
    'variants',coalesce((select jsonb_agg(jsonb_build_object(
      'id',v.id,'title',rv.title,'options',rv.options,'price_minor',rv.price_minor,
      'status',v.status,'availableQuantity',coalesce(i.quantity_available,0),
      'lowStockThreshold',coalesce(i.low_stock_threshold,0)) order by v.id)
      from public.shop_product_variants v
      join public.shop_qa_reviewed_variants rv on rv.variant_id=v.id and rv.variant_updated_at=v.updated_at
      left join public.shop_inventory i on i.variant_id=v.id and i.product_id=p.id
      where v.product_id=p.id and v.status='active'
        and public.adishop_qa_variant_reviewed(v.id,p.store_id)), '[]'::jsonb)
  )
  from public.shop_qa_public_catalog p
  join public.shop_products raw on raw.id=p.id
  join public.shop_qa_public_stores s on s.id=p.store_id
  left join public.shop_categories c on c.id=p.category_id
  where p.id=p_product_id and not exists (
    select 1 from public.shop_product_variants v where v.product_id=p.id and v.status='active'
      and not public.adishop_qa_variant_reviewed(v.id,p.store_id)
  )
$$;

revoke all on function public.adishop_qa_catalog_page(text,text,integer,integer,boolean,text,integer,integer)
  from public,anon,authenticated;
revoke all on function public.adishop_qa_product_detail(uuid) from public,anon,authenticated;
grant execute on function public.adishop_qa_catalog_page(text,text,integer,integer,boolean,text,integer,integer)
  to anon,authenticated;
grant execute on function public.adishop_qa_product_detail(uuid) to anon,authenticated;

commit;
