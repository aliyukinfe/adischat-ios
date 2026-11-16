-- Phase 9B.1 proposal only. Do not execute without fresh production preflight and approval.
-- All fields are seller-entered product specifications, not verification claims or variants.
begin;
set local lock_timeout = '5s';
lock table public.shop_attribute_definitions, public.shop_category_attributes in share row exclusive mode;

do $$
begin
  if (select count(*) from public.shop_attribute_definitions where status = 'active') <> 45
     or (select count(*) from public.shop_category_attributes) <> 832 then
    raise exception 'Phase 9B.1 metadata baseline drift; review before applying';
  end if;
  if (select count(*) from public.shop_categories where id in (
      '77ad8181-2011-4d40-8367-4b91c4562793', -- Mobile Phones
      '313f0538-35b0-4a63-85c2-30eaf93f2b8b', -- Tablets
      '8f7be2bd-0876-42e4-84ec-6eb8b12d4314', -- Laptops
      'cae1b10f-05d9-4616-82db-2a002ed465ab', -- Desktop Computers
      '43f976c7-6f24-4b7b-81fa-b3f41a7717f8', -- Phone Accessories
      '73060705-1d2b-4f7b-8f3a-4d6bd8ac0cfb', -- Computer Accessories
      '5cdc6a5a-8ce3-45ce-8669-a4d51d9e9051'  -- Chargers & Cables
    ) and is_active and is_leaf and seller_selectable) <> 7 then
    raise exception 'Phase 9B.1 category drift; review before applying';
  end if;
  if exists (select 1 from public.shop_attribute_definitions where code in (
      'CONDITION','OPERATING_SYSTEM','DISPLAY_RESOLUTION','GRAPHICS','STORAGE_TYPE','PORTS',
      'KNOWN_DEFECTS','REPAIR_HISTORY','BATTERY_HEALTH_PERCENT','INCLUDED_ACCESSORIES','DEVICE_LOCK_STATUS'
    )) then
    raise exception 'Phase 9B.1 attribute-code collision; review reuse before applying';
  end if;
end $$;

insert into public.shop_attribute_definitions
  (code,name,display_name,data_type,input_type,is_variant_option,is_filterable,is_searchable,allows_multiple,unit,suggestions,sort_order)
values
  ('CONDITION','Condition','Condition','choice','chips',false,false,false,false,null,
   '["New","Open Box","Like New","Slightly Used","Used","Refurbished","For Parts"]'::jsonb,200),
  ('OPERATING_SYSTEM','Operating system','Operating system','text','text',false,false,false,false,null,'[]'::jsonb,210),
  ('DISPLAY_RESOLUTION','Display resolution','Display resolution','text','text',false,false,false,false,null,'[]'::jsonb,220),
  ('GRAPHICS','Graphics','Graphics','text','text',false,false,false,false,null,'[]'::jsonb,230),
  ('STORAGE_TYPE','Storage type','Storage type','text','text',false,false,false,false,null,'[]'::jsonb,240),
  ('PORTS','Ports','Ports','text','text',false,false,false,false,null,'[]'::jsonb,250),
  ('KNOWN_DEFECTS','Known defects','Known defects (seller-reported)','text','text',false,false,false,false,null,'[]'::jsonb,260),
  ('REPAIR_HISTORY','Repair history','Repair history (seller-reported)','text','text',false,false,false,false,null,'[]'::jsonb,270),
  ('BATTERY_HEALTH_PERCENT','Measured battery health','Measured battery health (seller-reported)','number','number',false,false,false,false,'%', '[]'::jsonb,280),
  ('INCLUDED_ACCESSORIES','Included accessories','Included accessories (seller-reported)','text','text',false,false,false,false,null,'[]'::jsonb,290),
  ('DEVICE_LOCK_STATUS','Device lock status','Device lock status (seller-reported)','choice','select',false,false,false,false,null,
   '["Unlocked","Carrier locked","Account or activation lock present","Unknown"]'::jsonb,300);

with target(category_id, codes) as (values
  ('77ad8181-2011-4d40-8367-4b91c4562793'::uuid,
   array['CONDITION','OPERATING_SYSTEM','DISPLAY_RESOLUTION','KNOWN_DEFECTS','REPAIR_HISTORY','BATTERY_HEALTH_PERCENT','INCLUDED_ACCESSORIES','DEVICE_LOCK_STATUS']),
  ('313f0538-35b0-4a63-85c2-30eaf93f2b8b'::uuid,
   array['CONDITION','OPERATING_SYSTEM','DISPLAY_RESOLUTION','KNOWN_DEFECTS','REPAIR_HISTORY','BATTERY_HEALTH_PERCENT','INCLUDED_ACCESSORIES','DEVICE_LOCK_STATUS']),
  ('8f7be2bd-0876-42e4-84ec-6eb8b12d4314'::uuid,
   array['CONDITION','OPERATING_SYSTEM','DISPLAY_RESOLUTION','GRAPHICS','STORAGE_TYPE','PORTS','KNOWN_DEFECTS','REPAIR_HISTORY','BATTERY_HEALTH_PERCENT','INCLUDED_ACCESSORIES','DEVICE_LOCK_STATUS']),
  ('cae1b10f-05d9-4616-82db-2a002ed465ab'::uuid,
   array['CONDITION','OPERATING_SYSTEM','GRAPHICS','STORAGE_TYPE','PORTS','KNOWN_DEFECTS','REPAIR_HISTORY','INCLUDED_ACCESSORIES','DEVICE_LOCK_STATUS']),
  ('43f976c7-6f24-4b7b-81fa-b3f41a7717f8'::uuid,array['CONDITION']),
  ('73060705-1d2b-4f7b-8f3a-4d6bd8ac0cfb'::uuid,array['CONDITION']),
  ('5cdc6a5a-8ce3-45ce-8669-a4d51d9e9051'::uuid,array['CONDITION'])
)
insert into public.shop_category_attributes
  (category_id,attribute_definition_id,required,variant_capable,filterable,sort_order)
select t.category_id, a.id, false, false, false, a.sort_order
from target t
cross join lateral unnest(t.codes) as u(code)
join public.shop_attribute_definitions a on a.code = u.code and a.status = 'active';

do $$
begin
  if (select count(*) from public.shop_attribute_definitions where status = 'active') <> 56
     or (select count(*) from public.shop_category_attributes) <> 871 then
    raise exception 'Phase 9B.1 postcondition mismatch; transaction will roll back';
  end if;
end $$;
commit;
