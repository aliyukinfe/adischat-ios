-- Run only after phase9b5_synthetic_fixture.sql and the local Phase 9B.4 candidate.
do $$
declare
  owner_a uuid := '10000000-0000-4000-8000-000000000001';
  owner_b uuid := '10000000-0000-4000-8000-000000000002';
  store_a uuid := '00000000-0000-4000-8000-000000000001';
  product_a uuid := '20000000-0000-4000-8000-000000000001';
  product_b uuid := '20000000-0000-4000-8000-000000000002';
  choice text;
  battery text;
  payload jsonb;
  projected jsonb;
  baseline_title text;
  baseline_price integer;
  baseline_spec_count integer;
  baseline_variant_price integer;
  failed boolean;
  passed integer := 0;
begin
  foreach choice in array array['New','Open Box','Like New','Slightly Used','Used','Refurbished','For Parts'] loop
    perform public.adishop_phase9b4_validate_electronics(jsonb_build_object(
      'specificationValues',jsonb_build_array(jsonb_build_object('attributeCode','CONDITION','valueText',choice))));
    passed := passed + 1;
  end loop;
  foreach choice in array array['new','Damaged',''] loop
    failed := false;
    begin
      perform public.adishop_phase9b4_validate_electronics(jsonb_build_object(
        'specificationValues',jsonb_build_array(jsonb_build_object('attributeCode','CONDITION','valueText',choice))));
    exception when others then failed := sqlerrm='invalid-condition'; end;
    if not failed then raise exception 'invalid condition accepted: %',choice; end if;
    passed := passed + 1;
  end loop;
  foreach battery in array array['0','100','42.5'] loop
    perform public.adishop_phase9b4_validate_electronics(jsonb_build_object(
      'specificationValues',jsonb_build_array(jsonb_build_object('attributeCode','BATTERY_HEALTH_PERCENT','valueNumber',battery))));
    passed := passed + 1;
  end loop;
  foreach battery in array array['-1','100.1','NaN',''] loop
    failed := false;
    begin
      perform public.adishop_phase9b4_validate_electronics(jsonb_build_object(
        'specificationValues',jsonb_build_array(jsonb_build_object('attributeCode','BATTERY_HEALTH_PERCENT','valueNumber',battery))));
    exception when others then failed := sqlerrm='invalid-battery-health'; end;
    if not failed then raise exception 'invalid battery health accepted: %',battery; end if;
    passed := passed + 1;
  end loop;
  failed := false;
  begin
    perform public.adishop_phase9b4_validate_electronics('{"productOptions":[{"attributeCode":"CONDITION"}]}'::jsonb);
  exception when others then failed := sqlerrm='invalid-variant-option'; end;
  if not failed then raise exception 'condition variant option accepted'; end if;
  passed := passed + 1;

  payload := jsonb_build_object('title','Updated laptop','priceMinor',12000,
    'specificationValues',jsonb_build_array(jsonb_build_object('attributeCode','CONDITION','valueText','Used')));
  perform public.adishop_seller_update_product_atomic_for_store(owner_a,store_a,product_a,payload,null);
  if not exists(select 1 from public.shop_products where id=product_a and title='Updated laptop' and price_minor=12000)
     or not exists(select 1 from public.shop_product_attribute_values v join public.shop_attribute_definitions a
       on a.id=v.attribute_definition_id where v.product_id=product_a and a.code='CONDITION' and v.value_text='Used') then
    raise exception 'valid atomic save failed';
  end if;
  passed := passed + 1;

  select title,price_minor into baseline_title,baseline_price from public.shop_products where id=product_a;
  select count(*) into baseline_spec_count from public.shop_product_attribute_values where product_id=product_a;
  select price_minor into baseline_variant_price from public.shop_product_variants where product_id=product_a;
  failed := false;
  begin
    perform public.adishop_seller_update_product_atomic_for_store(owner_a,store_a,product_a,
      jsonb_build_object('title','Invalid condition write','priceMinor',1,'specificationValues',
        jsonb_build_array(jsonb_build_object('attributeCode','CONDITION','valueText','invalid'))),null);
  exception when others then failed := sqlerrm='invalid-condition'; end;
  if not failed then raise exception 'invalid save accepted'; end if;
  passed := passed + 1;
  failed := false;
  begin
    perform public.adishop_seller_update_product_atomic_for_store(owner_a,store_a,product_a,
      jsonb_build_object('title','Partial write','priceMinor',1,'forceCatalogFailure',true,
        'specificationValues',jsonb_build_array(jsonb_build_object('attributeCode','CONDITION','valueText','New'))),null);
  exception when others then failed := sqlerrm='synthetic-catalog-failure'; end;
  if not failed then raise exception 'catalog failure not triggered'; end if;
  if not exists(select 1 from public.shop_products where id=product_a and title=baseline_title and price_minor=baseline_price)
     or (select count(*) from public.shop_product_attribute_values where product_id=product_a)<>baseline_spec_count
     or (select price_minor from public.shop_product_variants where product_id=product_a)<>baseline_variant_price then
    raise exception 'partial write survived rejected update';
  end if;
  passed := passed + 1;

  failed := false;
  begin
    perform public.adishop_seller_update_product_atomic_for_store(owner_b,store_a,product_a,
      '{"title":"Foreign write"}'::jsonb,null);
  exception when others then failed := sqlerrm='seller-store-not-found'; end;
  if not failed or (select title from public.shop_products where id=product_a)<>baseline_title then
    raise exception 'foreign store mutation accepted';
  end if;
  passed := passed + 1;

  perform set_config('adishop.selected_store_id','',true);
  perform public.adishop_seller_submit_product_validated(owner_b,product_b,null);
  if not exists(select 1 from public.shop_products where id=product_b and moderation_status='submitted') then
    raise exception 'legacy listing submission failed';
  end if;
  passed := passed + 1;

  perform set_config('adishop.selected_store_id','',true);
  update public.shop_product_attribute_values set value_text='Invalid old condition'
    where product_id=product_a and attribute_definition_id=(select id from public.shop_attribute_definitions where code='CONDITION');
  failed := false;
  begin
    perform public.adishop_seller_submit_product_validated_for_store(owner_a,store_a,product_a,null);
  exception when others then failed := sqlerrm='invalid-condition'; end;
  if not failed or (select submitted_at from public.shop_products where id=product_a) is not null then
    raise exception 'invalid stored condition submitted';
  end if;
  passed := passed + 1;
  update public.shop_product_attribute_values set value_text='Used'
    where product_id=product_a and attribute_definition_id=(select id from public.shop_attribute_definitions where code='CONDITION');

  insert into public.shop_product_attribute_values(product_id,attribute_definition_id,value_text)
    select product_a,id,'ABC123456789' from public.shop_attribute_definitions where code='SERIAL_NUMBER';
  insert into public.shop_product_attribute_values(product_id,attribute_definition_id,value_text)
    select product_a,id,'Model X' from public.shop_attribute_definitions where code='MODEL';

  projected := public.adishop_product_catalog_payload(product_a);
  if projected::text ~* '(ABC123456789|123456789012345)' or
     not projected::text ~ 'Model X' or
     not exists(select 1 from public.shop_product_attribute_values v join public.shop_attribute_definitions a
       on a.id=v.attribute_definition_id where v.product_id=product_a and a.code='SERIAL_NUMBER' and v.value_text='ABC123456789') then
    raise exception 'public redaction or source preservation failed';
  end if;
  passed := passed + 1;
  if has_function_privilege('anon','public.adishop_seller_update_product_atomic_for_store(uuid,uuid,uuid,jsonb,text)','EXECUTE')
     or has_function_privilege('authenticated','public.adishop_seller_update_product_atomic_for_store(uuid,uuid,uuid,jsonb,text)','EXECUTE') then
    raise exception 'unprivileged atomic RPC execute grant';
  end if;
  passed := passed + 1;
  raise notice 'Phase 9B.5 synthetic SQL assertions passed: %',passed;
end;
$$;
