-- Disposable local PostgreSQL only. Synthetic identities and products.
create role anon nologin;
create role authenticated nologin;
create role service_role nologin;
create table public.shop_stores (id uuid primary key, name text not null);
create table public.shop_seller_stores (profile_id uuid not null, store_id uuid not null references public.shop_stores(id));
create table public.shop_products (
  id uuid primary key, store_id uuid not null references public.shop_stores(id),
  title text not null, price_minor integer not null, description text,
  short_description text, status text not null default 'draft',
  moderation_status text not null default 'pending', deleted_at timestamptz,
  submitted_at timestamptz
);
create table public.shop_attribute_definitions (id uuid primary key, code text not null unique);
create table public.shop_product_attribute_values (
  product_id uuid not null references public.shop_products(id),
  attribute_definition_id uuid not null references public.shop_attribute_definitions(id),
  value_text text, value_number numeric
);
create table public.shop_product_options (product_id uuid not null, code text not null, status text not null);
create table public.shop_product_variants (
  id uuid primary key, product_id uuid not null references public.shop_products(id),
  title text not null, price_minor integer not null, available_quantity integer not null
);

create function public.adishop_seller_owned_store(p_profile_id uuid)
returns public.shop_stores language plpgsql security definer set search_path = public as $$
declare owned public.shop_stores%rowtype; selected text;
begin
  selected := nullif(current_setting('adishop.selected_store_id', true),'');
  if selected is not null then
    select s.* into owned from public.shop_stores s join public.shop_seller_stores os on os.store_id=s.id
      where os.profile_id=p_profile_id and s.id=selected::uuid;
  else
    if (select count(*) from public.shop_seller_stores where profile_id=p_profile_id) <> 1 then
      raise exception 'seller-store-selection-required';
    end if;
    select s.* into owned from public.shop_stores s join public.shop_seller_stores os on os.store_id=s.id
      where os.profile_id=p_profile_id;
  end if;
  if owned.id is null then raise exception 'seller-store-not-found'; end if;
  return owned;
end;
$$;

create function public.adishop_seller_update_product_for_store(
  p_profile_id uuid,p_store_id uuid,p_product_id uuid,p_payload jsonb,p_request_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare owned public.shop_stores%rowtype; updated public.shop_products%rowtype;
begin
  perform set_config('adishop.selected_store_id',p_store_id::text,true);
  owned := public.adishop_seller_owned_store(p_profile_id);
  update public.shop_products set title=coalesce(p_payload->>'title',title),
    price_minor=coalesce((p_payload->>'priceMinor')::integer,price_minor)
    where id=p_product_id and store_id=owned.id and deleted_at is null returning * into updated;
  if updated.id is null then raise exception 'seller-product-not-found'; end if;
  return jsonb_build_object('id',updated.id,'title',updated.title);
end;
$$;

create function public.adishop_seller_save_variant_catalog_for_store(
  p_profile_id uuid,p_store_id uuid,p_product_id uuid,p_payload jsonb,p_request_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare owned public.shop_stores%rowtype; spec jsonb;
begin
  perform set_config('adishop.selected_store_id',p_store_id::text,true);
  owned := public.adishop_seller_owned_store(p_profile_id);
  if not exists(select 1 from public.shop_products where id=p_product_id and store_id=owned.id) then
    raise exception 'seller-product-not-found';
  end if;
  if p_payload->>'forceCatalogFailure' = 'true' then raise exception 'synthetic-catalog-failure'; end if;
  if p_payload ? 'specificationValues' then
    delete from public.shop_product_attribute_values where product_id=p_product_id;
    for spec in select value from jsonb_array_elements(p_payload->'specificationValues') loop
      insert into public.shop_product_attribute_values(product_id,attribute_definition_id,value_text,value_number)
      select p_product_id,id,spec->>'valueText',nullif(spec->>'valueNumber','')::numeric
      from public.shop_attribute_definitions where code=upper(spec->>'attributeCode');
    end loop;
  end if;
  return jsonb_build_object('id',p_product_id,'catalogSaved',true);
end;
$$;

create function public.adishop_seller_submit_product_for_store(
  p_profile_id uuid,p_store_id uuid,p_product_id uuid,p_request_id text default null
) returns jsonb language plpgsql security definer set search_path = public as $$
declare owned public.shop_stores%rowtype;
begin
  perform set_config('adishop.selected_store_id',p_store_id::text,true);
  owned := public.adishop_seller_owned_store(p_profile_id);
  update public.shop_products set moderation_status='submitted',submitted_at=now()
    where id=p_product_id and store_id=owned.id and deleted_at is null;
  if not found then raise exception 'seller-product-not-found'; end if;
  return jsonb_build_object('id',p_product_id,'moderationStatus','submitted');
end;
$$;

create function public.adishop_product_catalog_payload_description_core(p_product_id uuid)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object('id',p.id,'title',p.title,'description',p.description,
    'specifications',coalesce((select jsonb_agg(jsonb_build_object(
      'code',a.code,'name',a.code,'valueText',v.value_text,'valueNumber',v.value_number,
      'valueJson',null::jsonb)) from public.shop_product_attribute_values v
      join public.shop_attribute_definitions a on a.id=v.attribute_definition_id
      where v.product_id=p.id),'[]'::jsonb))
  from public.shop_products p where p.id=p_product_id and p.status='active'
    and p.moderation_status='approved' and p.deleted_at is null
$$;

insert into public.shop_stores values
  ('00000000-0000-4000-8000-000000000001','Synthetic A'),
  ('00000000-0000-4000-8000-000000000002','Synthetic B');
insert into public.shop_seller_stores values
  ('10000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000001'),
  ('10000000-0000-4000-8000-000000000002','00000000-0000-4000-8000-000000000002');
insert into public.shop_products(id,store_id,title,price_minor,description,short_description,status,moderation_status) values
  ('20000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000001',
    'Synthetic laptop',10000,'Serial Number: ABC123456789 is private','IMEI: 123456789012345','active','approved'),
  ('20000000-0000-4000-8000-000000000002','00000000-0000-4000-8000-000000000002',
    'Older listing',20000,null,null,'draft','pending');
insert into public.shop_attribute_definitions values
  ('30000000-0000-4000-8000-000000000001','CONDITION'),
  ('30000000-0000-4000-8000-000000000002','BATTERY_HEALTH_PERCENT'),
  ('30000000-0000-4000-8000-000000000003','SERIAL_NUMBER'),
  ('30000000-0000-4000-8000-000000000004','MODEL');
insert into public.shop_product_attribute_values values
  ('20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000003','ABC123456789',null),
  ('20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000004','Model X',null);
insert into public.shop_product_variants values
  ('40000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001','256 GB',10000,2);
