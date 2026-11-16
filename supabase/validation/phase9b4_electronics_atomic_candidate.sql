-- LOCAL REVIEW CANDIDATE ONLY. Do not execute against production.
-- INCOMPLETE PRIVACY COVERAGE: Android Home/search read shop_products directly.
-- This wrapper protects product-detail RPC output only; a buyer-safe listing
-- projection and legacy-client access plan are required before deployment.
-- Requires the separately approved Phase 9B.1 metadata migration and live code/mapping preflight.
begin;

create or replace function public.adishop_phase9b4_validate_electronics(p_payload jsonb)
returns void language plpgsql security definer set search_path = public as $$
declare
  spec jsonb;
  option_value jsonb;
  code text;
  measured text;
begin
  if p_payload ? 'specificationValues' then
    if jsonb_typeof(p_payload->'specificationValues') <> 'array' then
      raise exception 'invalid-specifications';
    end if;
    for spec in select value from jsonb_array_elements(p_payload->'specificationValues') loop
      code := upper(trim(spec->>'attributeCode'));
      if code = 'CONDITION' and coalesce(spec->>'valueText','') not in
        ('New','Open Box','Like New','Slightly Used','Used','Refurbished','For Parts') then
        raise exception 'invalid-condition';
      end if;
      if code = 'BATTERY_HEALTH_PERCENT' then
        measured := trim(coalesce(spec->>'valueNumber',''));
        if measured !~ '^[0-9]+(\.[0-9]+)?$' or measured::numeric > 100 then
          raise exception 'invalid-battery-health';
        end if;
      end if;
    end loop;
  end if;
  if p_payload ? 'productOptions' then
    if jsonb_typeof(p_payload->'productOptions') <> 'array' then
      raise exception 'invalid-product-options';
    end if;
    for option_value in select value from jsonb_array_elements(p_payload->'productOptions') loop
      if upper(trim(option_value->>'attributeCode')) in ('CONDITION','BATTERY_HEALTH_PERCENT',
          'KNOWN_DEFECTS','REPAIR_HISTORY','INCLUDED_ACCESSORIES','DEVICE_LOCK_STATUS') then
        raise exception 'invalid-variant-option';
      end if;
    end loop;
  end if;
end;
$$;

create or replace function public.adishop_seller_update_product_atomic_for_store(
  p_profile_id uuid, p_store_id uuid, p_product_id uuid,
  p_payload jsonb, p_request_id text default null
)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  base_payload jsonb;
  result jsonb;
begin
  if p_store_id is null then raise exception 'seller-store-selection-required'; end if;
  perform public.adishop_phase9b4_validate_electronics(p_payload);
  base_payload := p_payload - 'productOptions' - 'specificationValues' - 'variants';
  if base_payload->'images' = '[]'::jsonb then
    base_payload := base_payload - 'images';
  end if;
  result := public.adishop_seller_update_product_for_store(
    p_profile_id, p_store_id, p_product_id, base_payload, p_request_id
  );
  if p_payload ? 'productOptions' or p_payload ? 'specificationValues' or p_payload ? 'variants' then
    result := public.adishop_seller_save_variant_catalog_for_store(
      p_profile_id, p_store_id, p_product_id, p_payload, p_request_id
    );
  end if;
  return result;
end;
$$;

create or replace function public.adishop_seller_update_product_atomic(
  p_profile_id uuid, p_product_id uuid, p_payload jsonb, p_request_id text default null
)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  owned_store public.shop_stores%rowtype;
begin
  owned_store := public.adishop_seller_owned_store(p_profile_id);
  return public.adishop_seller_update_product_atomic_for_store(
    p_profile_id, owned_store.id, p_product_id, p_payload, p_request_id
  );
end;
$$;

create or replace function public.adishop_seller_submit_product_validated_for_store(
  p_profile_id uuid, p_store_id uuid, p_product_id uuid, p_request_id text default null
)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  owned_store public.shop_stores%rowtype;
  stored_specs jsonb;
begin
  if p_store_id is null then raise exception 'seller-store-selection-required'; end if;
  perform set_config('adishop.selected_store_id', p_store_id::text, true);
  owned_store := public.adishop_seller_owned_store(p_profile_id);
  if owned_store.id <> p_store_id then raise exception 'seller-product-not-found'; end if;
  perform 1 from public.shop_products
    where id = p_product_id and store_id = p_store_id and deleted_at is null for update;
  if not found then raise exception 'seller-product-not-found'; end if;
  select coalesce(jsonb_agg(jsonb_build_object(
    'attributeCode', a.code, 'valueText', v.value_text, 'valueNumber', v.value_number
  )), '[]'::jsonb) into stored_specs
  from public.shop_product_attribute_values v
  join public.shop_attribute_definitions a on a.id = v.attribute_definition_id
  where v.product_id = p_product_id;
  perform public.adishop_phase9b4_validate_electronics(
    jsonb_build_object('specificationValues', stored_specs)
  );
  if exists (select 1 from public.shop_product_options
      where product_id = p_product_id and status = 'active'
      and upper(code) in ('CONDITION','BATTERY_HEALTH_PERCENT','KNOWN_DEFECTS',
        'REPAIR_HISTORY','INCLUDED_ACCESSORIES','DEVICE_LOCK_STATUS')) then
    raise exception 'invalid-variant-option';
  end if;
  return public.adishop_seller_submit_product_for_store(
    p_profile_id, p_store_id, p_product_id, p_request_id
  );
end;
$$;

create or replace function public.adishop_seller_submit_product_validated(
  p_profile_id uuid, p_product_id uuid, p_request_id text default null
)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  owned_store public.shop_stores%rowtype;
begin
  owned_store := public.adishop_seller_owned_store(p_profile_id);
  return public.adishop_seller_submit_product_validated_for_store(
    p_profile_id, owned_store.id, p_product_id, p_request_id
  );
end;
$$;

revoke all on function public.adishop_phase9b4_validate_electronics(jsonb) from public, anon, authenticated;
revoke all on function public.adishop_seller_update_product_atomic_for_store(uuid,uuid,uuid,jsonb,text) from public, anon, authenticated;
revoke all on function public.adishop_seller_update_product_atomic(uuid,uuid,jsonb,text) from public, anon, authenticated;
grant execute on function public.adishop_seller_update_product_atomic_for_store(uuid,uuid,uuid,jsonb,text) to service_role;
grant execute on function public.adishop_seller_update_product_atomic(uuid,uuid,jsonb,text) to service_role;
revoke all on function public.adishop_seller_submit_product_validated_for_store(uuid,uuid,uuid,text) from public, anon, authenticated;
revoke all on function public.adishop_seller_submit_product_validated(uuid,uuid,text) from public, anon, authenticated;
grant execute on function public.adishop_seller_submit_product_validated_for_store(uuid,uuid,uuid,text) to service_role;
grant execute on function public.adishop_seller_submit_product_validated(uuid,uuid,text) to service_role;

-- Redaction is projection-only: seller/admin source values are unchanged.
create or replace function public.adishop_phase9b4_redact_identifiers(p_text text)
returns text language sql immutable security definer set search_path = public as $$
  select regexp_replace(p_text,
    '(IMEI|serial number|serial no|serial|S/N|MAC address)[[:space:]#:=/-]*[A-Za-z0-9:-]{6,}',
    '[device identifier redacted]', 'gi')
$$;
revoke all on function public.adishop_phase9b4_redact_identifiers(text) from public, anon, authenticated;

-- Preserve the current public payload signature and all existing fields.
-- Revalidate this definition against the live function body before any deployment.
create or replace function public.adishop_product_catalog_payload(p_product_id uuid)
returns jsonb language sql stable security definer set search_path = public as $$
  select case when core.payload is null then null else
    core.payload || jsonb_build_object(
      'title', public.adishop_phase9b4_redact_identifiers(core.payload->>'title'),
      'description', public.adishop_phase9b4_redact_identifiers(core.payload->>'description'),
      'shortDescription', public.adishop_phase9b4_redact_identifiers(p.short_description),
      'specifications', coalesce((
        select jsonb_agg(
          spec.value || jsonb_build_object('valueText',
            public.adishop_phase9b4_redact_identifiers(spec.value->>'valueText'))
        )
        from jsonb_array_elements(coalesce(core.payload->'specifications','[]'::jsonb)) spec(value)
        where upper(coalesce(spec.value->>'code','')) !~ '(IMEI|SERIAL|DEVICE_ID|MAC_ADDRESS)'
          and upper(coalesce(spec.value->>'name','')) !~ '(IMEI|SERIAL|MAC ADDRESS|DEVICE IDENTIFIER)'
          and coalesce(spec.value->'valueJson','null'::jsonb)::text !~* '(IMEI|SERIAL)[[:space:]#:=/-]*[A-Za-z0-9-]{6,}'
      ), '[]'::jsonb)
    )
  end
  from (select public.adishop_product_catalog_payload_description_core(p_product_id) payload) core
  left join public.shop_products p on p.id=p_product_id
$$;

-- The caller must invoke the atomic RPC in one HTTP request. The existing two-RPC
-- Edge path is not atomic; merely installing this function does not repair it.
commit;
