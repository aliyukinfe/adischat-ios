-- REVIEW ONLY. Do not apply to production without explicit approval.
-- If approved, promote this reviewed DML into a CLI-created migration.
-- Scope: Office, School & Stationery > Office Equipment only.
-- No category ID, hierarchy, product, or existing mapping is changed.

begin;

insert into public.shop_attribute_definitions
  (code, name, display_name, data_type, input_type, is_variant_option, is_filterable, sort_order)
values
  ('EQUIPMENT_TYPE', 'Equipment type', 'Equipment type', 'choice', 'chips', false, true, 10),
  ('DIMENSIONS', 'Dimensions', 'Dimensions', 'text', 'text', false, false, 50),
  ('POWER_REQUIREMENTS', 'Power requirements', 'Power requirements', 'text', 'text', false, false, 60),
  ('COMPATIBILITY', 'Compatibility', 'Compatibility', 'text', 'text', false, false, 70)
on conflict (code) do nothing;

do $$
declare
  office_id uuid;
begin
  select c.id into strict office_id
  from public.shop_categories c
  join public.shop_categories parent on parent.id = c.parent_id
  where c.slug = 'office-equipment'
    and parent.slug = 'office-school-stationery'
    and c.is_active and c.is_leaf and c.seller_selectable;

  insert into public.shop_category_attributes
    (category_id, attribute_definition_id, required, variant_capable, filterable, sort_order)
  select office_id, a.id, false, a.code = 'CAPACITY',
    a.code in ('EQUIPMENT_TYPE', 'CAPACITY'),
    case a.code
      when 'EQUIPMENT_TYPE' then 10
      when 'BRAND' then 20
      when 'MODEL' then 30
      when 'CAPACITY' then 40
      when 'DIMENSIONS' then 50
      when 'POWER_REQUIREMENTS' then 60
      when 'COMPATIBILITY' then 70
    end
  from public.shop_attribute_definitions a
  where a.status = 'active'
    and a.code in ('EQUIPMENT_TYPE', 'BRAND', 'MODEL', 'CAPACITY',
      'DIMENSIONS', 'POWER_REQUIREMENTS', 'COMPATIBILITY')
  on conflict (category_id, attribute_definition_id) do nothing;
end $$;

commit;

-- Review before approval: all fields are optional to preserve existing listings.
-- Confirm whether Equipment type should become required only after inspecting
-- existing Office Equipment products and the seller validation flow.
-- The other 151 NEEDS_CONFIGURATION/INVALID categories require separate
-- category-specific proposals; do not propagate this set across the root.
