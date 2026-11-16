-- Run only against disposable adsg_phase8b7_parity; no production credentials.
\set ON_ERROR_STOP on

do $$ begin
  if current_setting('server_version_num')::int / 10000 <> 17 then raise exception 'wrong PostgreSQL major'; end if;
  raise notice 'PASS PostgreSQL 17 major';
end $$;

do $$ begin
  if (select count(*) from pg_extension where extname in ('pgcrypto','plpgsql','uuid-ossp')) <> 3 then raise exception 'extensions missing'; end if;
  raise notice 'PASS relevant extensions';
end $$;

do $$ begin
  if (select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace
      where n.nspname='public' and c.relkind='r' and c.relrowsecurity
      and c.relname in ('shop_profiles','shop_sessions','shop_roles','shop_user_roles',
        'shop_role_permissions','shop_stores','shop_products','shop_product_variants','shop_product_images')) <> 9
  then raise exception 'RLS table mismatch'; end if;
  raise notice 'PASS relevant tables have RLS enabled';
end $$;

do $$ begin
  if (select count(*) from pg_policies where schemaname='public' and policyname in
      ('public_read_approved_stores','public_read_approved_products',
       'public_read_approved_product_images','client_access_deferred_shop_sessions')) <> 4
  then raise exception 'policy mismatch'; end if;
  raise notice 'PASS relevant RLS policies exist';
end $$;

do $$ begin
  if (select count(*) from pg_constraint where connamespace='public'::regnamespace and contype='c'
      and conname in ('shop_profiles_status_check','shop_stores_status_check',
        'shop_products_status_check','shop_products_moderation_status_check',
        'shop_products_stock_status_check','shop_products_price_minor_check')) <> 6
  then raise exception 'check constraint mismatch'; end if;
  if (select count(*) from pg_constraint where connamespace='public'::regnamespace and contype='f') < 9
  then raise exception 'foreign key mismatch'; end if;
  raise notice 'PASS relevant checks and foreign keys';
end $$;

do $$ begin
  if (select count(*) from pg_indexes where schemaname='public' and indexname in
      ('shop_products_public_idx','shop_products_category_slug_idx',
       'shop_product_moderation_queue_idx','shop_products_seller_store_status_idx',
       'shop_sessions_profile_active_idx','shop_product_images_one_primary_idx')) <> 6
  then raise exception 'index mismatch'; end if;
  raise notice 'PASS relevant indexes';
end $$;

do $$ begin
  if has_table_privilege('anon','public.shop_sessions','select') then raise exception 'anon can select sessions'; end if;
  if not has_table_privilege('service_role','public.shop_sessions','select') then raise exception 'service role denied sessions'; end if;
  if not (select rolbypassrls from pg_roles where rolname='service_role') then raise exception 'service role lacks RLS bypass'; end if;
  raise notice 'PASS service-role and private-session grants';
end $$;

do $$ begin
  if not public.adishop_profile_has_permission('10000000-0000-4000-8000-000000000004','product.moderation.read')
    or not public.adishop_profile_has_permission('10000000-0000-4000-8000-000000000005','product.moderation.read')
    or public.adishop_profile_has_permission('10000000-0000-4000-8000-000000000002','product.moderation.read')
  then raise exception 'admin permission mismatch'; end if;
  raise notice 'PASS admin permission RPC';
end $$;

do $$ begin
  if (public.adishop_admin_session('10000000-0000-4000-8000-000000000004'::uuid)->'permissions')
      is null then raise exception 'admin session missing permissions'; end if;
  begin
    perform public.adishop_admin_session('10000000-0000-4000-8000-000000000002'::uuid);
    raise exception 'seller received admin session';
  exception when raise_exception then
    if sqlerrm <> 'admin-forbidden' then raise; end if;
  end;
  raise notice 'PASS admin session RPC denies seller';
end $$;

set role anon;
do $$ declare n integer; begin
  select count(*) into n from public.shop_products;
  if n <> 5006 then raise exception 'public product count %', n; end if;
  if (select count(*) from public.shop_profiles) <> 0 then raise exception 'profile RLS exposed rows'; end if;
  begin
    perform count(*) from public.shop_sessions;
    raise exception 'anon read private sessions';
  exception when insufficient_privilege then null;
  end;
  raise notice 'PASS anon catalog visibility and private data denial';
end $$;
reset role;

do $$ begin
  begin
    update public.shop_products set price_minor = -1 where id = '30000000-0000-4000-8000-000000000001';
    raise exception 'negative price accepted';
  exception when check_violation then null;
  end;
  raise notice 'PASS price check rejects negative value';
end $$;

do $$ begin
  begin
    update public.shop_products set status = 'invalid' where id = '30000000-0000-4000-8000-000000000001';
    raise exception 'invalid status accepted';
  exception when check_violation then null;
  end;
  raise notice 'PASS product status check rejects invalid value';
end $$;

do $$ begin
  begin
    update public.shop_product_variants set store_id = 'ffffffff-ffff-4fff-8fff-ffffffffffff'
      where id = (select id from public.shop_product_variants limit 1);
    raise exception 'foreign store accepted';
  exception when foreign_key_violation then null;
  end;
  raise notice 'PASS variant store foreign key rejects missing store';
end $$;

begin;
update public.shop_stores set status = 'suspended'
  where id = '20000000-0000-4000-8000-000000000001';
set local role anon;
do $$ declare n integer; begin
  select count(*) into n from public.shop_products;
  if n <> 3 then raise exception 'suspended-store products visible: %', n; end if;
  raise notice 'PASS suspended store hidden from public catalog';
end $$;
rollback;

set role service_role;
do $$ begin
  if (select count(*) from public.shop_sessions) <> 7 then raise exception 'service session access mismatch'; end if;
  raise notice 'PASS service-role session access';
end $$;
reset role;
