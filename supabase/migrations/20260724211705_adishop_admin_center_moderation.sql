-- AdisGebeya Phase 1.4E admin center and moderation foundation.
-- Additive only: no product/order data is deleted and public visibility rules
-- continue to require shop_products.status='active' and moderation_status='approved'.

create table if not exists public.shop_admin_audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  actor_role text,
  action_type text not null,
  target_type text not null,
  target_id uuid,
  previous_state jsonb not null default '{}'::jsonb,
  new_state jsonb not null default '{}'::jsonb,
  reason text,
  metadata jsonb not null default '{}'::jsonb,
  request_id text,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_seller_application_reviews (
  id uuid primary key default gen_random_uuid(),
  application_id uuid not null references public.shop_seller_applications(id) on delete restrict,
  reviewer_profile_id uuid references public.shop_profiles(id) on delete set null,
  decision text not null check (decision in ('start_review','approve','reject','suspend','reactivate')),
  reason text,
  private_notes text,
  previous_status text not null,
  new_status text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_product_moderation_reviews (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id) on delete restrict,
  reviewer_profile_id uuid references public.shop_profiles(id) on delete set null,
  decision text not null check (decision in ('start_review','approve','reject','request_changes','suspend','reactivate','archive')),
  reason_code text,
  public_reason text,
  private_notes text,
  previous_status text not null,
  new_status text not null,
  snapshot jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_admin_order_notes (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.shop_orders(id) on delete restrict,
  order_group_id uuid references public.shop_order_groups(id) on delete restrict,
  admin_profile_id uuid references public.shop_profiles(id) on delete set null,
  visibility text not null default 'private' check (visibility in ('private','buyer_visible','seller_visible')),
  note text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.shop_moderation_reason_codes (
  code text primary key,
  entity_type text not null check (entity_type in ('seller_application','product','store','order')),
  title text not null,
  description text,
  active boolean not null default true,
  sort_order integer not null default 100,
  created_at timestamptz not null default now()
);

alter table public.shop_products add column if not exists submitted_at timestamptz;
alter table public.shop_products add column if not exists reviewed_at timestamptz;
alter table public.shop_products add column if not exists reviewed_by_profile_id uuid references public.shop_profiles(id) on delete set null;
alter table public.shop_products add column if not exists published_at timestamptz;
alter table public.shop_products add column if not exists suspended_at timestamptz;

alter table public.shop_seller_applications add column if not exists reviewed_by_profile_id uuid references public.shop_profiles(id) on delete set null;
alter table public.shop_seller_applications add column if not exists suspended_at timestamptz;

alter table public.shop_stores add column if not exists suspended_at timestamptz;

do $$
begin
  alter table public.shop_products drop constraint if exists shop_products_moderation_status_check;
  alter table public.shop_products add constraint shop_products_moderation_status_check
    check (moderation_status in ('pending','submitted','under_review','changes_requested','approved','rejected','suspended','archived'));
end $$;

do $$
declare t text;
begin
  foreach t in array array[
    'shop_admin_audit_logs','shop_seller_application_reviews',
    'shop_product_moderation_reviews','shop_admin_order_notes',
    'shop_moderation_reason_codes'
  ] loop
    execute format('alter table public.%I enable row level security', t);
    if not exists (
      select 1 from pg_policies
      where schemaname = 'public' and tablename = t and policyname = 'client_access_deferred'
    ) then
      execute format('create policy client_access_deferred on public.%I for all to anon, authenticated using (false) with check (false)', t);
    end if;
  end loop;
end $$;

create index if not exists shop_seller_applications_status_submitted_idx on public.shop_seller_applications(status, submitted_at desc, created_at desc);
create index if not exists shop_product_moderation_queue_idx on public.shop_products(moderation_status, submitted_at desc, created_at desc);
create index if not exists shop_admin_audit_actor_idx on public.shop_admin_audit_logs(actor_profile_id, created_at desc);
create index if not exists shop_admin_audit_target_idx on public.shop_admin_audit_logs(target_type, target_id, created_at desc);
create index if not exists shop_store_status_idx on public.shop_stores(status, approval_status, created_at desc);
create index if not exists shop_orders_reference_idx on public.shop_orders(order_number);
create index if not exists shop_admin_order_notes_order_idx on public.shop_admin_order_notes(order_id, created_at desc);
create index if not exists shop_seller_reviews_application_idx on public.shop_seller_application_reviews(application_id, created_at desc);
create index if not exists shop_product_reviews_product_idx on public.shop_product_moderation_reviews(product_id, created_at desc);

insert into public.shop_moderation_reason_codes(code, entity_type, title, description, sort_order)
values
  ('missing_documents','seller_application','Missing documents','Required seller verification documents are missing.',10),
  ('business_mismatch','seller_application','Business details need review','Business details do not match the submitted information.',20),
  ('incomplete_product','product','Incomplete product','Required product information is missing.',10),
  ('invalid_price','product','Invalid price','Product price or compare-at price is invalid.',20),
  ('missing_images','product','Missing images','At least one product image is required.',30),
  ('policy_issue','product','Policy issue','Product needs policy or safety review.',40)
on conflict (code) do update set
  title = excluded.title,
  description = excluded.description,
  active = true,
  sort_order = excluded.sort_order;

insert into public.shop_role_permissions(role_id, permission)
select r.id, p.permission
from public.shop_roles r
join (
  values
    ('SHOP_SUPER_ADMIN','seller.application.read'),('SHOP_SUPER_ADMIN','seller.application.review'),('SHOP_SUPER_ADMIN','seller.application.approve'),('SHOP_SUPER_ADMIN','seller.application.reject'),('SHOP_SUPER_ADMIN','seller.application.suspend'),('SHOP_SUPER_ADMIN','seller.application.reactivate'),('SHOP_SUPER_ADMIN','seller.documents.read'),
    ('SHOP_SUPER_ADMIN','product.moderation.read'),('SHOP_SUPER_ADMIN','product.moderation.approve'),('SHOP_SUPER_ADMIN','product.moderation.reject'),('SHOP_SUPER_ADMIN','product.moderation.request_changes'),('SHOP_SUPER_ADMIN','product.moderation.archive'),('SHOP_SUPER_ADMIN','product.moderation.bulk_action'),
    ('SHOP_SUPER_ADMIN','store.read'),('SHOP_SUPER_ADMIN','store.suspend'),('SHOP_SUPER_ADMIN','store.reactivate'),('SHOP_SUPER_ADMIN','store.edit_limited'),('SHOP_SUPER_ADMIN','store.feature'),
    ('SHOP_SUPER_ADMIN','order.support.read'),('SHOP_SUPER_ADMIN','order.support.note'),('SHOP_SUPER_ADMIN','order.support.flag'),('SHOP_SUPER_ADMIN','order.support.resolve'),('SHOP_SUPER_ADMIN','order.support.override_limited'),
    ('SHOP_SUPER_ADMIN','admin.user.read'),('SHOP_SUPER_ADMIN','admin.role.assign'),('SHOP_SUPER_ADMIN','admin.role.revoke'),('SHOP_SUPER_ADMIN','audit.read'),('SHOP_SUPER_ADMIN','dashboard.read'),
    ('SHOP_ADMIN','seller.application.read'),('SHOP_ADMIN','seller.application.review'),('SHOP_ADMIN','seller.application.approve'),('SHOP_ADMIN','seller.application.reject'),('SHOP_ADMIN','seller.documents.read'),
    ('SHOP_ADMIN','product.moderation.read'),('SHOP_ADMIN','product.moderation.approve'),('SHOP_ADMIN','product.moderation.reject'),('SHOP_ADMIN','product.moderation.request_changes'),
    ('SHOP_ADMIN','store.read'),('SHOP_ADMIN','store.suspend'),('SHOP_ADMIN','store.reactivate'),('SHOP_ADMIN','dashboard.read'),('SHOP_ADMIN','audit.read'),('SHOP_ADMIN','order.support.read'),('SHOP_ADMIN','order.support.note'),
    ('SUPPORT_AGENT','order.support.read'),('SUPPORT_AGENT','order.support.note'),('SUPPORT_AGENT','order.support.flag'),('SUPPORT_AGENT','order.support.resolve'),('SUPPORT_AGENT','store.read')
) as p(role_code, permission) on p.role_code = r.code
on conflict (role_id, permission) do nothing;

create or replace function public.adishop_profile_permissions(p_profile_id uuid)
returns text[]
language sql
stable
as $$
  select coalesce(array_agg(distinct rp.permission order by rp.permission), array[]::text[])
  from public.shop_user_roles ur
  join public.shop_roles r on r.id = ur.role_id
  join public.shop_role_permissions rp on rp.role_id = r.id
  where ur.profile_id = p_profile_id
    and ur.revoked_at is null
$$;

create or replace function public.adishop_profile_has_permission(p_profile_id uuid, p_permission text)
returns boolean
language sql
stable
as $$
  select exists (
    select 1
    from public.shop_user_roles ur
    join public.shop_roles r on r.id = ur.role_id
    join public.shop_role_permissions rp on rp.role_id = r.id
    where ur.profile_id = p_profile_id
      and ur.revoked_at is null
      and (rp.permission = p_permission or r.code = 'SHOP_SUPER_ADMIN')
  )
$$;

create or replace function public.adishop_admin_primary_role(p_profile_id uuid)
returns text
language sql
stable
as $$
  select r.code
  from public.shop_user_roles ur
  join public.shop_roles r on r.id = ur.role_id
  where ur.profile_id = p_profile_id
    and ur.revoked_at is null
    and r.code in ('SHOP_SUPER_ADMIN','SHOP_ADMIN','SUPPORT_AGENT')
  order by case r.code when 'SHOP_SUPER_ADMIN' then 1 when 'SHOP_ADMIN' then 2 else 3 end
  limit 1
$$;

create or replace function public.adishop_admin_audit(
  p_actor_profile_id uuid,
  p_action_type text,
  p_target_type text,
  p_target_id uuid,
  p_previous_state jsonb default '{}'::jsonb,
  p_new_state jsonb default '{}'::jsonb,
  p_reason text default null,
  p_metadata jsonb default '{}'::jsonb,
  p_request_id text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  audit_id uuid;
begin
  insert into public.shop_admin_audit_logs(
    actor_profile_id, actor_role, action_type, target_type, target_id,
    previous_state, new_state, reason, metadata, request_id
  )
  values (
    p_actor_profile_id,
    public.adishop_admin_primary_role(p_actor_profile_id),
    p_action_type,
    p_target_type,
    p_target_id,
    coalesce(p_previous_state, '{}'::jsonb),
    coalesce(p_new_state, '{}'::jsonb),
    nullif(trim(coalesce(p_reason, '')), ''),
    coalesce(p_metadata, '{}'::jsonb),
    p_request_id
  )
  returning id into audit_id;
  return audit_id;
end;
$$;

create or replace function public.adishop_admin_session(p_profile_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  roles text[];
  permissions text[];
begin
  select public.current_shop_roles(p_profile_id) into roles;
  select public.adishop_profile_permissions(p_profile_id) into permissions;
  if not exists (
    select 1 from unnest(roles) role_code
    where role_code in ('SHOP_SUPER_ADMIN','SHOP_ADMIN','SUPPORT_AGENT')
  ) then
    raise exception 'admin-forbidden';
  end if;
  return jsonb_build_object(
    'profileId', p_profile_id,
    'primaryRole', public.adishop_admin_primary_role(p_profile_id),
    'roles', roles,
    'permissions', permissions
  );
end;
$$;

create or replace function public.adishop_admin_dashboard(p_profile_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'dashboard.read') then
    raise exception 'admin-forbidden';
  end if;
  return jsonb_build_object(
    'pendingSellerApplications', (select count(*) from public.shop_seller_applications where status in ('submitted','under_review')),
    'sellersUnderReview', (select count(*) from public.shop_seller_applications where status = 'under_review'),
    'pendingProductReviews', (select count(*) from public.shop_products where moderation_status in ('pending','submitted','under_review')),
    'productsRequiringChanges', (select count(*) from public.shop_products where moderation_status = 'changes_requested'),
    'suspendedSellers', (select count(*) from public.shop_seller_applications where status = 'suspended'),
    'suspendedProducts', (select count(*) from public.shop_products where moderation_status = 'suspended'),
    'activeOrders', (select count(*) from public.shop_orders where status not in ('delivered','completed','cancelled','rejected')),
    'ordersNeedingSupport', (select count(*) from public.shop_admin_order_notes where visibility = 'private' and created_at > now() - interval '7 days'),
    'todayModerationActions', (select count(*) from public.shop_admin_audit_logs where created_at >= date_trunc('day', now()))
  );
end;
$$;

create or replace function public.adishop_admin_list_seller_applications(
  p_profile_id uuid,
  p_status text default 'submitted',
  p_search text default '',
  p_limit integer default 30,
  p_offset integer default 0
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'seller.application.read') then
    raise exception 'admin-forbidden';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(row_data) order by row_data.submitted_at nulls last, row_data.created_at)
    from (
      select
        a.id, a.profile_id, a.proposed_store_name, a.business_type, a.status,
        a.rejection_reason, a.submitted_at, a.reviewed_at, a.created_at, a.updated_at,
        p.display_name as applicant_name, p.email as applicant_email,
        (select count(*) from public.shop_seller_application_reviews r where r.application_id = a.id) as review_count
      from public.shop_seller_applications a
      join public.shop_profiles p on p.id = a.profile_id
      where (p_status = 'all' or a.status = p_status or (p_status = 'submitted' and a.status in ('submitted','under_review')))
        and (
          coalesce(p_search,'') = ''
          or a.proposed_store_name ilike '%' || p_search || '%'
          or p.display_name ilike '%' || p_search || '%'
          or p.email ilike '%' || p_search || '%'
        )
      order by a.submitted_at nulls last, a.created_at
      limit greatest(1, least(100, p_limit))
      offset greatest(0, p_offset)
    ) row_data
  ), '[]'::jsonb);
end;
$$;

create or replace function public.adishop_admin_get_seller_application(p_profile_id uuid, p_application_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare result jsonb;
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'seller.application.read') then
    raise exception 'admin-forbidden';
  end if;
  select jsonb_build_object(
    'application', to_jsonb(a) || jsonb_build_object('applicantName', p.display_name, 'applicantEmail', p.email, 'applicantPhone', p.phone),
    'reviews', coalesce((select jsonb_agg(to_jsonb(r) order by r.created_at desc) from public.shop_seller_application_reviews r where r.application_id = a.id), '[]'::jsonb),
    'stores', coalesce((select jsonb_agg(to_jsonb(s) order by s.created_at desc) from public.shop_stores s where s.owner_profile_id = a.profile_id), '[]'::jsonb)
  )
  into result
  from public.shop_seller_applications a
  join public.shop_profiles p on p.id = a.profile_id
  where a.id = p_application_id;
  if result is null then raise exception 'seller-application-not-found'; end if;
  return result;
end;
$$;

create or replace function public.adishop_admin_review_seller_application(
  p_actor_profile_id uuid,
  p_application_id uuid,
  p_action text,
  p_reason text default '',
  p_private_notes text default '',
  p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  app_record public.shop_seller_applications%rowtype;
  new_status text;
  store_id uuid;
  seller_role_id uuid;
  old_state jsonb;
  new_state jsonb;
begin
  if p_action in ('start_review') and not public.adishop_profile_has_permission(p_actor_profile_id, 'seller.application.review') then raise exception 'admin-forbidden'; end if;
  if p_action in ('approve') and not public.adishop_profile_has_permission(p_actor_profile_id, 'seller.application.approve') then raise exception 'admin-forbidden'; end if;
  if p_action in ('reject') and not public.adishop_profile_has_permission(p_actor_profile_id, 'seller.application.reject') then raise exception 'admin-forbidden'; end if;
  if p_action in ('suspend') and not public.adishop_profile_has_permission(p_actor_profile_id, 'seller.application.suspend') then raise exception 'admin-forbidden'; end if;
  if p_action in ('reactivate') and not public.adishop_profile_has_permission(p_actor_profile_id, 'seller.application.reactivate') then raise exception 'admin-forbidden'; end if;

  select * into app_record from public.shop_seller_applications where id = p_application_id for update;
  if not found then raise exception 'seller-application-not-found'; end if;
  old_state := to_jsonb(app_record);

  if p_action in ('reject','suspend') and length(trim(coalesce(p_reason,''))) < 3 then
    raise exception 'reason-required';
  end if;

  new_status := case p_action
    when 'start_review' then 'under_review'
    when 'approve' then 'approved'
    when 'reject' then 'rejected'
    when 'suspend' then 'suspended'
    when 'reactivate' then 'approved'
    else null
  end;
  if new_status is null then raise exception 'unsupported-action'; end if;

  if p_action = 'start_review' and app_record.status not in ('submitted','under_review') then raise exception 'invalid-seller-application-state'; end if;
  if p_action = 'approve' and app_record.status not in ('submitted','under_review','approved') then raise exception 'invalid-seller-application-state'; end if;
  if p_action = 'reject' and app_record.status not in ('submitted','under_review','rejected') then raise exception 'invalid-seller-application-state'; end if;
  if p_action = 'suspend' and app_record.status not in ('approved','suspended') then raise exception 'invalid-seller-application-state'; end if;
  if p_action = 'reactivate' and app_record.status not in ('suspended','approved') then raise exception 'invalid-seller-application-state'; end if;

  update public.shop_seller_applications
  set status = new_status,
      rejection_reason = case when p_action = 'reject' then p_reason when p_action in ('approve','reactivate') then null else rejection_reason end,
      reviewed_at = case when p_action in ('approve','reject','suspend','reactivate') then now() else reviewed_at end,
      reviewed_by_profile_id = p_actor_profile_id,
      suspended_at = case when p_action = 'suspend' then now() when p_action = 'reactivate' then null else suspended_at end,
      updated_at = now()
  where id = app_record.id
  returning to_jsonb(public.shop_seller_applications.*) into new_state;

  insert into public.shop_seller_application_reviews(application_id, reviewer_profile_id, decision, reason, private_notes, previous_status, new_status)
  values (app_record.id, p_actor_profile_id, p_action, nullif(trim(coalesce(p_reason,'')), ''), nullif(trim(coalesce(p_private_notes,'')), ''), app_record.status, new_status);

  if p_action in ('approve','reactivate') then
    select id into seller_role_id from public.shop_roles where code = 'SELLER';
    insert into public.shop_user_roles(profile_id, role_id, assigned_by_profile_id, reason)
    values (app_record.profile_id, seller_role_id, p_actor_profile_id, 'seller application approved')
    on conflict (profile_id, role_id) do update set revoked_at = null, assigned_by_profile_id = excluded.assigned_by_profile_id, reason = excluded.reason;

    insert into public.shop_stores(owner_profile_id, name, slug, description, category, city, status, approval_status)
    values (
      app_record.profile_id,
      app_record.proposed_store_name,
      lower(regexp_replace(app_record.proposed_store_name, '[^a-zA-Z0-9]+', '-', 'g')) || '-' || substr(app_record.id::text, 1, 8),
      'Approved AdisGebeya seller store',
      app_record.business_type,
      null,
      'active',
      'approved'
    )
    on conflict do nothing
    returning id into store_id;

    if store_id is null then
      select id into store_id from public.shop_stores where owner_profile_id = app_record.profile_id order by created_at desc limit 1;
      update public.shop_stores set status = 'active', approval_status = 'approved', suspended_at = null, updated_at = now() where id = store_id;
    end if;

    insert into public.shop_store_members(store_id, profile_id, role, status)
    values (store_id, app_record.profile_id, 'owner', 'active')
    on conflict do nothing;
  elsif p_action = 'suspend' then
    update public.shop_stores set status = 'suspended', approval_status = 'suspended', suspended_at = now(), updated_at = now() where owner_profile_id = app_record.profile_id;
  end if;

  perform public.adishop_admin_audit(p_actor_profile_id, 'seller_application.' || p_action, 'shop_seller_application', app_record.id, old_state, new_state, p_reason, jsonb_build_object('requestId', p_request_id), p_request_id);
  perform public.adishop_create_notification(app_record.profile_id, 'seller_application_' || p_action, 'Seller application update', case p_action when 'approve' then 'Your seller application was approved.' when 'reject' then coalesce(p_reason,'Your seller application was rejected.') when 'suspend' then 'Your seller account was suspended.' else 'Your seller application was updated.' end, jsonb_build_object('applicationId', app_record.id, 'action', p_action));

  return public.adishop_admin_get_seller_application(p_actor_profile_id, app_record.id);
end;
$$;

create or replace function public.adishop_admin_list_products(
  p_profile_id uuid,
  p_status text default 'pending',
  p_search text default '',
  p_limit integer default 30,
  p_offset integer default 0
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'product.moderation.read') then
    raise exception 'admin-forbidden';
  end if;
  return coalesce((
    select jsonb_agg(to_jsonb(row_data) order by row_data.created_at desc)
    from (
      select
        p.id, p.title, p.slug, p.status, p.moderation_status, p.price_minor, p.currency, p.stock_status,
        p.submitted_at, p.reviewed_at, p.created_at, s.name as store_name, s.status as store_status, s.approval_status as store_approval_status,
        (select image_url from public.shop_product_images i where i.product_id = p.id order by i.is_primary desc, i.display_order asc limit 1) as image_url
      from public.shop_products p
      join public.shop_stores s on s.id = p.store_id
      where (p_status = 'all' or p.moderation_status = p_status or (p_status = 'pending' and p.moderation_status in ('pending','submitted','under_review')))
        and (coalesce(p_search,'') = '' or p.title ilike '%' || p_search || '%' or s.name ilike '%' || p_search || '%')
      order by p.created_at desc
      limit greatest(1, least(100, p_limit))
      offset greatest(0, p_offset)
    ) row_data
  ), '[]'::jsonb);
end;
$$;

create or replace function public.adishop_admin_get_product(p_profile_id uuid, p_product_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare result jsonb;
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'product.moderation.read') then
    raise exception 'admin-forbidden';
  end if;
  select jsonb_build_object(
    'product', to_jsonb(p),
    'store', to_jsonb(s),
    'images', coalesce((select jsonb_agg(to_jsonb(i) order by i.is_primary desc, i.display_order asc) from public.shop_product_images i where i.product_id = p.id), '[]'::jsonb),
    'inventory', coalesce((select jsonb_agg(to_jsonb(inv)) from public.shop_inventory inv where inv.product_id = p.id), '[]'::jsonb),
    'reviews', coalesce((select jsonb_agg(to_jsonb(r) order by r.created_at desc) from public.shop_product_moderation_reviews r where r.product_id = p.id), '[]'::jsonb)
  )
  into result
  from public.shop_products p
  join public.shop_stores s on s.id = p.store_id
  where p.id = p_product_id;
  if result is null then raise exception 'product-not-found'; end if;
  return result;
end;
$$;

create or replace function public.adishop_admin_review_product(
  p_actor_profile_id uuid,
  p_product_id uuid,
  p_action text,
  p_reason_code text default null,
  p_public_reason text default '',
  p_private_notes text default '',
  p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  product_record public.shop_products%rowtype;
  store_record public.shop_stores%rowtype;
  old_state jsonb;
  new_status text;
  new_public_status text;
  image_count integer;
  stock_count integer;
begin
  if p_action = 'start_review' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.read') then raise exception 'admin-forbidden'; end if;
  if p_action = 'approve' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.approve') then raise exception 'admin-forbidden'; end if;
  if p_action = 'reject' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.reject') then raise exception 'admin-forbidden'; end if;
  if p_action = 'request_changes' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.request_changes') then raise exception 'admin-forbidden'; end if;
  if p_action in ('suspend','reactivate') and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.approve') then raise exception 'admin-forbidden'; end if;
  if p_action = 'archive' and not public.adishop_profile_has_permission(p_actor_profile_id, 'product.moderation.archive') then raise exception 'admin-forbidden'; end if;

  select * into product_record from public.shop_products where id = p_product_id for update;
  if not found then raise exception 'product-not-found'; end if;
  select * into store_record from public.shop_stores where id = product_record.store_id for update;
  if not found then raise exception 'store-not-found'; end if;
  old_state := to_jsonb(product_record);

  if p_action in ('reject','request_changes','suspend') and length(trim(coalesce(p_public_reason, p_reason_code, ''))) < 3 then
    raise exception 'reason-required';
  end if;

  if p_action = 'approve' then
    if store_record.status <> 'active' or store_record.approval_status <> 'approved' then raise exception 'store-not-active'; end if;
    if length(trim(product_record.title)) < 2 or product_record.price_minor <= 0 then raise exception 'product-validation-failed'; end if;
    select count(*) into image_count from public.shop_product_images where product_id = product_record.id;
    select count(*) into stock_count from public.shop_inventory where product_id = product_record.id and quantity_available >= quantity_reserved;
    if image_count < 1 then raise exception 'missing-images'; end if;
    if product_record.stock_status <> 'out_of_stock' and stock_count < 1 then raise exception 'missing-stock'; end if;
  end if;

  new_status := case p_action
    when 'start_review' then 'under_review'
    when 'approve' then 'approved'
    when 'reject' then 'rejected'
    when 'request_changes' then 'changes_requested'
    when 'suspend' then 'suspended'
    when 'reactivate' then 'approved'
    when 'archive' then 'archived'
    else null
  end;
  if new_status is null then raise exception 'unsupported-action'; end if;

  if p_action = 'start_review' and product_record.moderation_status not in ('pending','submitted','under_review') then raise exception 'invalid-product-state'; end if;
  if p_action in ('approve','reject','request_changes') and product_record.moderation_status not in ('pending','submitted','under_review','changes_requested','approved','rejected') then raise exception 'invalid-product-state'; end if;
  if p_action = 'suspend' and product_record.moderation_status not in ('approved','suspended') then raise exception 'invalid-product-state'; end if;
  if p_action = 'reactivate' and product_record.moderation_status not in ('suspended','approved') then raise exception 'invalid-product-state'; end if;

  new_public_status := case
    when p_action in ('approve','reactivate') then 'active'
    when p_action in ('reject','request_changes','start_review') then 'draft'
    when p_action = 'suspend' then 'paused'
    when p_action = 'archive' then 'archived'
    else product_record.status
  end;

  update public.shop_products
  set moderation_status = new_status,
      status = new_public_status,
      reviewed_at = case when p_action <> 'start_review' then now() else reviewed_at end,
      reviewed_by_profile_id = p_actor_profile_id,
      published_at = case when p_action in ('approve','reactivate') then coalesce(published_at, now()) else published_at end,
      suspended_at = case when p_action = 'suspend' then now() when p_action = 'reactivate' then null else suspended_at end,
      updated_at = now()
  where id = product_record.id;

  insert into public.shop_product_moderation_reviews(product_id, reviewer_profile_id, decision, reason_code, public_reason, private_notes, previous_status, new_status, snapshot)
  values (product_record.id, p_actor_profile_id, p_action, p_reason_code, nullif(trim(coalesce(p_public_reason,'')), ''), nullif(trim(coalesce(p_private_notes,'')), ''), product_record.moderation_status, new_status, old_state);

  perform public.adishop_admin_audit(p_actor_profile_id, 'product_moderation.' || p_action, 'shop_product', product_record.id, old_state, jsonb_build_object('moderation_status', new_status, 'status', new_public_status), coalesce(p_public_reason,p_reason_code), jsonb_build_object('requestId', p_request_id), p_request_id);
  perform public.adishop_create_notification(store_record.owner_profile_id, 'product_moderation_' || p_action, 'Product moderation update', case p_action when 'approve' then 'Your product was approved and published.' when 'reject' then coalesce(p_public_reason,'Your product was rejected.') when 'request_changes' then coalesce(p_public_reason,'Changes are required before publishing.') when 'suspend' then 'Your product was suspended.' else 'Your product moderation status changed.' end, jsonb_build_object('productId', product_record.id, 'action', p_action));

  return public.adishop_admin_get_product(p_actor_profile_id, product_record.id);
end;
$$;

create or replace function public.adishop_admin_list_orders(p_profile_id uuid, p_search text default '', p_limit integer default 30, p_offset integer default 0)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'order.support.read') then raise exception 'admin-forbidden'; end if;
  return coalesce((
    select jsonb_agg(to_jsonb(o) order by o.created_at desc)
    from (
      select id, order_number, status, payment_status, fulfillment_status, delivery_status, currency, total_minor, created_at
      from public.shop_orders
      where coalesce(p_search,'') = '' or order_number ilike '%' || p_search || '%'
      order by created_at desc
      limit greatest(1, least(100, p_limit))
      offset greatest(0, p_offset)
    ) o
  ), '[]'::jsonb);
end;
$$;

create or replace function public.adishop_admin_add_order_note(p_profile_id uuid, p_order_id uuid, p_note text, p_visibility text default 'private')
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare note_record public.shop_admin_order_notes%rowtype;
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'order.support.note') then raise exception 'admin-forbidden'; end if;
  if length(trim(coalesce(p_note,''))) < 3 then raise exception 'note-required'; end if;
  if not exists (select 1 from public.shop_orders where id = p_order_id) then raise exception 'order-not-found'; end if;
  insert into public.shop_admin_order_notes(order_id, admin_profile_id, visibility, note)
  values (p_order_id, p_profile_id, case when p_visibility in ('private','buyer_visible','seller_visible') then p_visibility else 'private' end, trim(p_note))
  returning * into note_record;
  perform public.adishop_admin_audit(p_profile_id, 'order_support.note', 'shop_order', p_order_id, '{}'::jsonb, to_jsonb(note_record), null, '{}'::jsonb, null);
  return to_jsonb(note_record);
end;
$$;

create or replace function public.adishop_admin_audit_log(p_profile_id uuid, p_limit integer default 50, p_offset integer default 0)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'audit.read') then raise exception 'admin-forbidden'; end if;
  return coalesce((
    select jsonb_agg(to_jsonb(a) order by a.created_at desc)
    from (
      select id, actor_profile_id, actor_role, action_type, target_type, target_id, reason, metadata, created_at
      from public.shop_admin_audit_logs
      order by created_at desc
      limit greatest(1, least(100, p_limit))
      offset greatest(0, p_offset)
    ) a
  ), '[]'::jsonb);
end;
$$;

create or replace function public.adishop_admin_users(p_profile_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.adishop_profile_has_permission(p_profile_id, 'admin.user.read') then raise exception 'admin-forbidden'; end if;
  return coalesce((
    select jsonb_agg(to_jsonb(row_data) order by row_data.display_name)
    from (
      select p.id, p.display_name, p.email, array_agg(r.code order by r.code) filter (where ur.revoked_at is null) as roles
      from public.shop_profiles p
      join public.shop_user_roles ur on ur.profile_id = p.id and ur.revoked_at is null
      join public.shop_roles r on r.id = ur.role_id
      where r.code in ('SHOP_SUPER_ADMIN','SHOP_ADMIN','SUPPORT_AGENT')
      group by p.id, p.display_name, p.email
    ) row_data
  ), '[]'::jsonb);
end;
$$;

create or replace function public.adishop_admin_assign_role(p_actor_profile_id uuid, p_target_profile_id uuid, p_role_code text, p_reason text default '')
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare role_id_value uuid;
begin
  if not public.adishop_profile_has_permission(p_actor_profile_id, 'admin.role.assign') then raise exception 'admin-forbidden'; end if;
  if p_actor_profile_id = p_target_profile_id then raise exception 'self-escalation-blocked'; end if;
  if p_role_code not in ('SHOP_ADMIN','SUPPORT_AGENT','SHOP_SUPER_ADMIN') then raise exception 'unsupported-role'; end if;
  if p_role_code = 'SHOP_SUPER_ADMIN' and public.adishop_admin_primary_role(p_actor_profile_id) <> 'SHOP_SUPER_ADMIN' then raise exception 'admin-forbidden'; end if;
  select id into role_id_value from public.shop_roles where code = p_role_code;
  insert into public.shop_user_roles(profile_id, role_id, assigned_by_profile_id, reason)
  values (p_target_profile_id, role_id_value, p_actor_profile_id, nullif(trim(coalesce(p_reason,'')), ''))
  on conflict (profile_id, role_id) do update set revoked_at = null, assigned_by_profile_id = excluded.assigned_by_profile_id, reason = excluded.reason;
  perform public.adishop_admin_audit(p_actor_profile_id, 'admin_role.assign', 'shop_profile', p_target_profile_id, '{}'::jsonb, jsonb_build_object('role', p_role_code), p_reason);
  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.adishop_admin_revoke_role(p_actor_profile_id uuid, p_target_profile_id uuid, p_role_code text, p_reason text default '')
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare remaining_super_admins integer;
begin
  if not public.adishop_profile_has_permission(p_actor_profile_id, 'admin.role.revoke') then raise exception 'admin-forbidden'; end if;
  if p_role_code = 'SHOP_SUPER_ADMIN' then
    select count(*) into remaining_super_admins
    from public.shop_user_roles ur
    join public.shop_roles r on r.id = ur.role_id
    where r.code = 'SHOP_SUPER_ADMIN' and ur.revoked_at is null and ur.profile_id <> p_target_profile_id;
    if remaining_super_admins < 1 then raise exception 'last-super-admin-protected'; end if;
  end if;
  update public.shop_user_roles ur
  set revoked_at = now(), reason = nullif(trim(coalesce(p_reason,'')), '')
  from public.shop_roles r
  where r.id = ur.role_id and ur.profile_id = p_target_profile_id and r.code = p_role_code and ur.revoked_at is null;
  perform public.adishop_admin_audit(p_actor_profile_id, 'admin_role.revoke', 'shop_profile', p_target_profile_id, jsonb_build_object('role', p_role_code), '{}'::jsonb, p_reason);
  return jsonb_build_object('ok', true);
end;
$$;

-- Controlled one-time bootstrap for the known AdisGebeya owner/admin profile.
do $$
declare
  bootstrap_profile uuid := '6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee'::uuid;
  super_admin_role uuid;
begin
  if exists (
    select 1 from public.shop_profiles
    where id = bootstrap_profile
      and adischat_user_id = 'eZA5erSpzpgW43EuKhi6FdVEUGv2'
  ) then
    select id into super_admin_role from public.shop_roles where code = 'SHOP_SUPER_ADMIN';
    insert into public.shop_user_roles(profile_id, role_id, assigned_by_profile_id, reason)
    values (bootstrap_profile, super_admin_role, null, 'controlled Phase 1.4E bootstrap')
    on conflict (profile_id, role_id) do update set revoked_at = null, reason = excluded.reason;
    perform public.adishop_admin_audit(null, 'admin_role.bootstrap_super_admin', 'shop_profile', bootstrap_profile, '{}'::jsonb, jsonb_build_object('role','SHOP_SUPER_ADMIN'), 'controlled bootstrap');
  end if;
end $$;

revoke all on function public.adishop_profile_permissions(uuid) from public;
revoke all on function public.adishop_profile_has_permission(uuid, text) from public;
revoke all on function public.adishop_admin_primary_role(uuid) from public;
revoke all on function public.adishop_admin_audit(uuid, text, text, uuid, jsonb, jsonb, text, jsonb, text) from public;
revoke all on function public.adishop_admin_session(uuid) from public;
revoke all on function public.adishop_admin_dashboard(uuid) from public;
revoke all on function public.adishop_admin_list_seller_applications(uuid, text, text, integer, integer) from public;
revoke all on function public.adishop_admin_get_seller_application(uuid, uuid) from public;
revoke all on function public.adishop_admin_review_seller_application(uuid, uuid, text, text, text, text) from public;
revoke all on function public.adishop_admin_list_products(uuid, text, text, integer, integer) from public;
revoke all on function public.adishop_admin_get_product(uuid, uuid) from public;
revoke all on function public.adishop_admin_review_product(uuid, uuid, text, text, text, text, text) from public;
revoke all on function public.adishop_admin_list_orders(uuid, text, integer, integer) from public;
revoke all on function public.adishop_admin_add_order_note(uuid, uuid, text, text) from public;
revoke all on function public.adishop_admin_audit_log(uuid, integer, integer) from public;
revoke all on function public.adishop_admin_users(uuid) from public;
revoke all on function public.adishop_admin_assign_role(uuid, uuid, text, text) from public;
revoke all on function public.adishop_admin_revoke_role(uuid, uuid, text, text) from public;

grant execute on function public.adishop_profile_permissions(uuid) to service_role;
grant execute on function public.adishop_profile_has_permission(uuid, text) to service_role;
grant execute on function public.adishop_admin_primary_role(uuid) to service_role;
grant execute on function public.adishop_admin_audit(uuid, text, text, uuid, jsonb, jsonb, text, jsonb, text) to service_role;
grant execute on function public.adishop_admin_session(uuid) to service_role;
grant execute on function public.adishop_admin_dashboard(uuid) to service_role;
grant execute on function public.adishop_admin_list_seller_applications(uuid, text, text, integer, integer) to service_role;
grant execute on function public.adishop_admin_get_seller_application(uuid, uuid) to service_role;
grant execute on function public.adishop_admin_review_seller_application(uuid, uuid, text, text, text, text) to service_role;
grant execute on function public.adishop_admin_list_products(uuid, text, text, integer, integer) to service_role;
grant execute on function public.adishop_admin_get_product(uuid, uuid) to service_role;
grant execute on function public.adishop_admin_review_product(uuid, uuid, text, text, text, text, text) to service_role;
grant execute on function public.adishop_admin_list_orders(uuid, text, integer, integer) to service_role;
grant execute on function public.adishop_admin_add_order_note(uuid, uuid, text, text) to service_role;
grant execute on function public.adishop_admin_audit_log(uuid, integer, integer) to service_role;
grant execute on function public.adishop_admin_users(uuid) to service_role;
grant execute on function public.adishop_admin_assign_role(uuid, uuid, text, text) to service_role;
grant execute on function public.adishop_admin_revoke_role(uuid, uuid, text, text) to service_role;

do $$
declare
  fn regprocedure;
begin
  foreach fn in array array[
    'public.adishop_profile_permissions(uuid)'::regprocedure,
    'public.adishop_profile_has_permission(uuid,text)'::regprocedure,
    'public.adishop_admin_primary_role(uuid)'::regprocedure,
    'public.adishop_admin_audit(uuid,text,text,uuid,jsonb,jsonb,text,jsonb,text)'::regprocedure,
    'public.adishop_admin_session(uuid)'::regprocedure,
    'public.adishop_admin_dashboard(uuid)'::regprocedure,
    'public.adishop_admin_list_seller_applications(uuid,text,text,integer,integer)'::regprocedure,
    'public.adishop_admin_get_seller_application(uuid,uuid)'::regprocedure,
    'public.adishop_admin_review_seller_application(uuid,uuid,text,text,text,text)'::regprocedure,
    'public.adishop_admin_list_products(uuid,text,text,integer,integer)'::regprocedure,
    'public.adishop_admin_get_product(uuid,uuid)'::regprocedure,
    'public.adishop_admin_review_product(uuid,uuid,text,text,text,text,text)'::regprocedure,
    'public.adishop_admin_list_orders(uuid,text,integer,integer)'::regprocedure,
    'public.adishop_admin_add_order_note(uuid,uuid,text,text)'::regprocedure,
    'public.adishop_admin_audit_log(uuid,integer,integer)'::regprocedure,
    'public.adishop_admin_users(uuid)'::regprocedure,
    'public.adishop_admin_assign_role(uuid,uuid,text,text)'::regprocedure,
    'public.adishop_admin_revoke_role(uuid,uuid,text,text)'::regprocedure
  ] loop
    execute format('revoke execute on function %s from anon', fn);
    execute format('revoke execute on function %s from authenticated', fn);
    execute format('revoke execute on function %s from public', fn);
    execute format('grant execute on function %s to service_role', fn);
  end loop;
end $$;
