-- Disposable loopback-only Phase 8B.3 fixture. Synthetic records only.
create role anon nologin;
create role service_role nologin bypassrls;
grant usage on schema public to anon, service_role;

create table public.shop_profiles (
  id uuid primary key,
  status text not null,
  display_name text,
  email text,
  phone text,
  adischat_user_id text
);
create table public.shop_sessions (
  id uuid primary key,
  profile_id uuid not null references public.shop_profiles(id),
  session_jti text not null unique,
  expires_at timestamptz not null,
  revoked_at timestamptz
);
create table public.shop_roles (id uuid primary key, code text not null unique);
create table public.shop_user_roles (
  profile_id uuid not null references public.shop_profiles(id),
  role_id uuid not null references public.shop_roles(id),
  revoked_at timestamptz,
  primary key (profile_id, role_id)
);
create table public.shop_role_permissions (
  role_id uuid not null references public.shop_roles(id),
  permission text not null,
  primary key (role_id, permission)
);
create table public.shop_stores (
  id uuid primary key,
  name text not null,
  status text not null default 'active',
  approval_status text not null default 'approved'
);
create table public.shop_products (
  id uuid primary key,
  store_id uuid not null references public.shop_stores(id),
  title text not null,
  slug text not null,
  seller_sku text,
  category_slug text,
  status text not null,
  moderation_status text not null,
  price_minor integer not null,
  currency text not null default 'ETB',
  stock_status text not null,
  aggregate_available_quantity integer not null default 0,
  submitted_at timestamptz,
  reviewed_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  deleted_at timestamptz
);
create table public.shop_product_variants (
  id uuid primary key,
  product_id uuid not null references public.shop_products(id),
  sku text
);
create table public.shop_product_images (
  id uuid primary key,
  product_id uuid not null references public.shop_products(id),
  image_url text not null,
  is_primary boolean not null default false,
  display_order integer not null default 0,
  deleted_at timestamptz
);

alter table public.shop_profiles enable row level security;
alter table public.shop_sessions enable row level security;
alter table public.shop_roles enable row level security;
alter table public.shop_user_roles enable row level security;
alter table public.shop_role_permissions enable row level security;
alter table public.shop_stores enable row level security;
alter table public.shop_products enable row level security;
alter table public.shop_product_variants enable row level security;
alter table public.shop_product_images enable row level security;
grant select on public.shop_profiles, public.shop_sessions, public.shop_roles,
  public.shop_user_roles, public.shop_role_permissions, public.shop_stores,
  public.shop_products, public.shop_product_variants, public.shop_product_images
  to service_role;
create index shop_products_store_status_idx on public.shop_products(store_id, moderation_status, updated_at desc);
create index shop_products_category_idx on public.shop_products(category_slug, status, moderation_status, created_at desc);
create index shop_product_images_product_idx on public.shop_product_images(product_id, is_primary desc, display_order);
create index shop_product_variants_product_idx on public.shop_product_variants(product_id);

create function public.current_shop_roles(target_profile_id uuid)
returns text[] language sql stable as $$
  select coalesce(array_agg(r.code order by r.code), array['CUSTOMER']::text[])
  from public.shop_roles r join public.shop_user_roles ur on ur.role_id = r.id
  where ur.profile_id = target_profile_id and ur.revoked_at is null
$$;
create function public.adishop_profile_permissions(p_profile_id uuid)
returns text[] language sql stable as $$
  select coalesce(array_agg(distinct rp.permission order by rp.permission), array[]::text[])
  from public.shop_user_roles ur join public.shop_roles r on r.id = ur.role_id
  join public.shop_role_permissions rp on rp.role_id = r.id
  where ur.profile_id = p_profile_id and ur.revoked_at is null
$$;
create function public.adishop_profile_has_permission(p_profile_id uuid, p_permission text)
returns boolean language sql stable as $$
  select exists (
    select 1 from public.shop_user_roles ur
    join public.shop_roles r on r.id = ur.role_id
    join public.shop_role_permissions rp on rp.role_id = r.id
    where ur.profile_id = p_profile_id and ur.revoked_at is null
      and (rp.permission = p_permission or r.code = 'SHOP_SUPER_ADMIN')
  )
$$;
create function public.adishop_admin_primary_role(p_profile_id uuid)
returns text language sql stable as $$
  select r.code from public.shop_user_roles ur join public.shop_roles r on r.id = ur.role_id
  where ur.profile_id = p_profile_id and ur.revoked_at is null
    and r.code in ('SHOP_SUPER_ADMIN','SHOP_ADMIN','SUPPORT_AGENT')
  order by case r.code when 'SHOP_SUPER_ADMIN' then 1 when 'SHOP_ADMIN' then 2 else 3 end limit 1
$$;
create function public.adishop_admin_session(p_profile_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare roles text[]; permissions text[];
begin
  select public.current_shop_roles(p_profile_id) into roles;
  select public.adishop_profile_permissions(p_profile_id) into permissions;
  if not exists (select 1 from unnest(roles) role_code
    where role_code in ('SHOP_SUPER_ADMIN','SHOP_ADMIN','SUPPORT_AGENT')) then
    raise exception 'admin-forbidden';
  end if;
  return jsonb_build_object('profileId', p_profile_id,
    'primaryRole', public.adishop_admin_primary_role(p_profile_id),
    'roles', roles, 'permissions', permissions);
end;
$$;
create function public.adishop_admin_list_products(
  p_profile_id uuid, p_status text default 'pending', p_search text default '',
  p_limit integer default 30, p_offset integer default 0
)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'product.moderation.read') then
    raise exception 'admin-forbidden';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(row_data) order by row_data.created_at desc)
    from (
      select p.id, p.title, p.slug, p.status, p.moderation_status, p.price_minor, p.currency,
        p.stock_status, p.submitted_at, p.reviewed_at, p.created_at,
        s.name as store_name, s.status as store_status,
        s.approval_status as store_approval_status,
        (select image_url from public.shop_product_images i where i.product_id = p.id
          order by i.is_primary desc, i.display_order asc limit 1) as image_url
      from public.shop_products p join public.shop_stores s on s.id = p.store_id
      where (p_status = 'all' or p.moderation_status = p_status
        or (p_status = 'pending' and p.moderation_status in ('pending','submitted','under_review')))
        and (coalesce(p_search,'') = '' or p.title ilike '%' || p_search || '%'
          or s.name ilike '%' || p_search || '%')
      order by p.created_at desc
      limit greatest(1, least(100, p_limit)) offset greatest(0, p_offset)
    ) row_data
  ), '[]'::jsonb);
end;
$$;
revoke all on function public.adishop_admin_session(uuid) from public, anon;
revoke all on function public.adishop_admin_list_products(uuid,text,text,integer,integer) from public, anon;
grant execute on function public.adishop_admin_session(uuid) to service_role;
grant execute on function public.adishop_admin_list_products(uuid,text,text,integer,integer) to service_role;

insert into public.shop_profiles(id,status) values
  ('10000000-0000-4000-8000-000000000001','active'),
  ('10000000-0000-4000-8000-000000000002','active'),
  ('10000000-0000-4000-8000-000000000003','active'),
  ('10000000-0000-4000-8000-000000000004','active'),
  ('10000000-0000-4000-8000-000000000005','active');
insert into public.shop_roles(id,code) values
  ('11000000-0000-4000-8000-000000000001','CUSTOMER'),
  ('11000000-0000-4000-8000-000000000002','SELLER'),
  ('11000000-0000-4000-8000-000000000003','SUPPORT_AGENT'),
  ('11000000-0000-4000-8000-000000000004','SHOP_ADMIN'),
  ('11000000-0000-4000-8000-000000000005','SHOP_SUPER_ADMIN');
insert into public.shop_user_roles(profile_id,role_id)
  select p.id, r.id from public.shop_profiles p join public.shop_roles r
    on right(p.id::text, 12) = right(r.id::text, 12);
insert into public.shop_role_permissions(role_id,permission) values
  ('11000000-0000-4000-8000-000000000004','product.moderation.read'),
  ('11000000-0000-4000-8000-000000000005','product.moderation.read');
insert into public.shop_sessions(id,profile_id,session_jti,expires_at) values
  ('12000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','customer-jti',now()+interval '1 day'),
  ('12000000-0000-4000-8000-000000000002','10000000-0000-4000-8000-000000000002','seller-jti',now()+interval '1 day'),
  ('12000000-0000-4000-8000-000000000003','10000000-0000-4000-8000-000000000003','support-jti',now()+interval '1 day'),
  ('12000000-0000-4000-8000-000000000004','10000000-0000-4000-8000-000000000004','admin-jti',now()+interval '1 day'),
  ('12000000-0000-4000-8000-000000000005','10000000-0000-4000-8000-000000000005','super-jti',now()+interval '1 day'),
  ('12000000-0000-4000-8000-000000000006','10000000-0000-4000-8000-000000000004','expired-jti',now()-interval '1 hour');
insert into public.shop_sessions(id,profile_id,session_jti,expires_at,revoked_at) values
  ('12000000-0000-4000-8000-000000000007','10000000-0000-4000-8000-000000000004','revoked-jti',now()+interval '1 day',now());
insert into public.shop_stores(id,name,status,approval_status) values
  ('20000000-0000-4000-8000-000000000001','Hulu QA Store','active','approved'),
  ('20000000-0000-4000-8000-000000000002','Merkato QA Store','active','approved');
insert into public.shop_products(id,store_id,title,slug,seller_sku,category_slug,status,
  moderation_status,price_minor,stock_status,aggregate_available_quantity,created_at,updated_at) values
  ('30000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001','Galaxy A16 5G','galaxy-a16','A16/128.5','phones','active','approved',2800000,'in_stock',8,'2026-01-01','2026-02-01'),
  ('30000000-0000-4000-8000-000000000002','20000000-0000-4000-8000-000000000002','Galaxy A56','galaxy-a56','A56-256','phones','active','approved',6300000,'low_stock',2,'2026-01-02','2026-02-02'),
  ('30000000-0000-4000-8000-000000000003','20000000-0000-4000-8000-000000000001','Leather Bag','leather-bag','BAG-3','fashion','draft','pending',290000,'out_of_stock',0,'2026-01-03','2026-02-03'),
  ('30000000-0000-4000-8000-000000000004','20000000-0000-4000-8000-000000000002','Galaxy Case','galaxy-case','CASE-4','accessories','active','approved',30000,'in_stock',10,'2026-01-04','2026-02-04'),
  ('30000000-0000-4000-8000-000000000005','20000000-0000-4000-8000-000000000001','Headphones','headphones','HEAD-5','audio','archived','archived',240000,'out_of_stock',0,'2026-01-05','2026-02-05'),
  ('30000000-0000-4000-8000-000000000006','20000000-0000-4000-8000-000000000001','Galaxy Watch','galaxy-watch','WATCH-6','electronics','active','approved',450000,'in_stock',4,'2026-01-06','2026-02-06'),
  ('30000000-0000-4000-8000-000000000007','20000000-0000-4000-8000-000000000002','Desk Lamp','desk-lamp','LAMP-7','home','active','approved',80000,'low_stock',1,'2026-01-07','2026-02-07'),
  ('30000000-0000-4000-8000-000000000008','20000000-0000-4000-8000-000000000001','Phone Charger','phone-charger','CHARGER-8','accessories','draft','rejected',50000,'out_of_stock',0,'2026-01-08','2026-02-08');
insert into public.shop_product_variants(id,product_id,sku) values
  ('31000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001','SKU/A16.128'),
  ('31000000-0000-4000-8000-000000000002','30000000-0000-4000-8000-000000000004','SKU-CASE');
insert into public.shop_product_images(id,product_id,image_url,is_primary,display_order) values
  ('32000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001','https://example.invalid/a16.jpg',true,0),
  ('32000000-0000-4000-8000-000000000002','30000000-0000-4000-8000-000000000002','https://example.invalid/a56.jpg',true,0);
