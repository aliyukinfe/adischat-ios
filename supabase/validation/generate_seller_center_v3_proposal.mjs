import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = dirname(fileURLToPath(import.meta.url));
const audit = JSON.parse(readFileSync(join(dir, 'seller_center_v2_category_coverage.json'), 'utf8'));
const definitions = {
  AGE_RANGE: ['Age range', 'text', 'text'],
  ALLERGEN_INFO: ['Allergen information', 'text', 'text'],
  AUTHOR: ['Author', 'text', 'text'],
  COMPATIBILITY: ['Compatibility', 'text', 'text'],
  CONNECTOR_TYPE: ['Connector type', 'text', 'text'],
  DIMENSIONS: ['Dimensions', 'text', 'text'],
  EQUIPMENT_TYPE: ['Equipment type', 'choice', 'chips', null, ['Printer', 'Scanner', 'Copier', 'Shredder', 'Projector', 'Laminator', 'Binding machine', 'Other']],
  INGREDIENTS: ['Ingredients', 'text', 'text'],
  ISBN: ['ISBN', 'text', 'text'],
  METAL_PURITY: ['Metal purity', 'text', 'text'],
  PART_NUMBER: ['Part number', 'text', 'text'],
  PLATFORM: ['Platform', 'text', 'text'],
  POWER_REQUIREMENTS: ['Power requirements', 'text', 'text'],
  SCENT: ['Scent', 'text', 'text'],
  SEED_VARIETY: ['Seed variety', 'text', 'text'],
  STORAGE_INSTRUCTIONS: ['Storage instructions', 'text', 'text'],
  VEHICLE_FITMENT: ['Vehicle fitment', 'text', 'text'],
  VOLTAGE: ['Voltage', 'number', 'number', 'V'],
  WATTAGE: ['Wattage', 'number', 'number', 'W'],
};
const existing = new Set([
  'BATTERY_CAPACITY', 'BRAND', 'CAPACITY', 'COLOR', 'FABRIC_WIDTH', 'FLAVOR',
  'GRADE', 'GSM', 'MATERIAL', 'MODEL', 'NETWORK', 'PACK_SIZE', 'PACKAGING',
  'PATTERN', 'PROCESSOR', 'RAM', 'ROLL_LENGTH', 'SCREEN_SIZE', 'SHADE', 'SHAPE',
  'SIZE', 'STONE_TYPE', 'STORAGE', 'VOLUME', 'WARRANTY', 'WEIGHT',
]);
const variantCodes = new Set(['CAPACITY', 'COLOR', 'FLAVOR', 'MATERIAL', 'PACK_SIZE', 'PATTERN', 'PROCESSOR', 'RAM', 'SHADE', 'SHAPE', 'SIZE', 'STORAGE', 'VOLUME', 'WEIGHT']);
const filterCodes = new Set(['CAPACITY', 'COLOR', 'MATERIAL', 'PACK_SIZE', 'SIZE', 'WEIGHT']);
const rootRules = {
  'Agriculture & Farming': { fields: ['BRAND', 'MATERIAL', 'DIMENSIONS'], units: ['PIECE', 'SET', 'PACK', 'BAG', 'KILOGRAM'] },
  Appliances: { fields: ['BRAND', 'MODEL', 'POWER_REQUIREMENTS', 'DIMENSIONS'], units: ['PIECE', 'SET'] },
  'Automotive & Motorcycle': { fields: ['BRAND', 'PART_NUMBER', 'VEHICLE_FITMENT'], units: ['PIECE', 'PAIR', 'SET', 'PACK', 'BOTTLE', 'LITER'] },
  'Beauty & Personal Care': { fields: ['INGREDIENTS'], units: ['PIECE', 'SET', 'PACK', 'BOTTLE', 'TUBE', 'JAR', 'MILLILITER'] },
  'Books, Media & Entertainment': { fields: ['AUTHOR'], units: ['PIECE', 'SET', 'PACK'] },
  'Construction & Building Materials': { fields: ['MATERIAL', 'DIMENSIONS'], units: ['PIECE', 'SET', 'PACK', 'BOX', 'BAG', 'METER', 'SQUARE_METER'] },
  'Crafts, Sewing & Accessories': { fields: ['MATERIAL', 'COLOR', 'SIZE'], units: ['PIECE', 'SET', 'PACK', 'ROLL', 'METER', 'GRAM', 'KILOGRAM'] },
  'Electronics & Technology': { fields: ['COMPATIBILITY'], units: ['PIECE', 'PAIR', 'SET', 'PACK'] },
  'Fabric, Textile & Leather': { fields: ['MATERIAL', 'FABRIC_WIDTH', 'GSM', 'COLOR', 'PATTERN', 'ROLL_LENGTH'], units: ['METER', 'YARD', 'ROLL', 'SQUARE_METER', 'PIECE'] },
  'Fashion & Clothing': { fields: [], units: ['PIECE', 'SET', 'PAIR'] },
  'Gifts, Party & Events': { fields: ['MATERIAL', 'COLOR'], units: ['PIECE', 'SET', 'PACK', 'DOZEN'] },
  'Groceries & Food': { fields: ['INGREDIENTS', 'ALLERGEN_INFO', 'STORAGE_INSTRUCTIONS'], units: ['PIECE', 'PACK', 'BOX', 'BAG', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER', 'BOTTLE', 'JAR'] },
  'Health & Wellness': { fields: ['BRAND', 'MATERIAL', 'SIZE'], units: ['PIECE', 'PAIR', 'SET', 'PACK', 'BOX'] },
  'Home, Furniture & Garden': { fields: ['DIMENSIONS'], units: ['PIECE', 'SET', 'PAIR', 'PACK', 'BOX', 'METER'] },
  'Industrial & Manufacturing': { fields: ['BRAND', 'MODEL', 'POWER_REQUIREMENTS', 'DIMENSIONS'], units: ['PIECE', 'SET', 'PACK', 'BOX', 'KILOGRAM'] },
  'Jewelry & Watches': { fields: ['MATERIAL', 'COLOR', 'SIZE'], units: ['PIECE', 'PAIR', 'SET'] },
  'Office, School & Stationery': { fields: ['BRAND', 'MATERIAL', 'PACK_SIZE'], units: ['PIECE', 'SET', 'PACK', 'BOX', 'SHEET', 'DOZEN'] },
  'Packaging & Printing': { fields: ['MATERIAL', 'DIMENSIONS', 'PACK_SIZE'], units: ['PIECE', 'SET', 'PACK', 'BOX', 'ROLL', 'SHEET', 'METER'] },
  'Pet Supplies': { fields: ['MATERIAL', 'SIZE'], units: ['PIECE', 'SET', 'PACK', 'BAG', 'KILOGRAM'] },
  'Shoes, Bags & Accessories': { fields: ['MATERIAL', 'COLOR', 'SIZE'], units: ['PIECE', 'PAIR', 'SET'] },
  'Sports & Outdoors': { fields: [], units: ['PIECE', 'PAIR', 'SET', 'PACK'] },
  'Tools & Hardware': { fields: ['BRAND', 'MODEL', 'MATERIAL'], units: ['PIECE', 'SET', 'PACK', 'BOX', 'KILOGRAM'] },
  'Toys, Kids & Baby': { fields: ['AGE_RANGE'], units: ['PIECE', 'SET', 'PACK', 'BOX', 'PAIR'] },
};
const leafFields = {
  'Agriculture & Farming > Agricultural Machinery': ['MODEL', 'POWER_REQUIREMENTS'],
  'Agriculture & Farming > Irrigation': ['COMPATIBILITY'],
  'Agriculture & Farming > Permitted Seeds': ['SEED_VARIETY', 'PACK_SIZE', 'WEIGHT'],
  'Automotive & Motorcycle > Oils': ['VOLUME', 'GRADE'],
  'Automotive & Motorcycle > Tires': ['SIZE'],
  'Beauty & Personal Care > Hair Care': ['SCENT'],
  'Beauty & Personal Care > Perfume': ['SCENT'],
  'Beauty & Personal Care > Skincare': ['SCENT'],
  'Books, Media & Entertainment > Books': ['ISBN'],
  'Books, Media & Entertainment > Instruments': ['BRAND', 'MODEL'],
  'Books, Media & Entertainment > Music Equipment': ['BRAND', 'MODEL'],
  'Construction & Building Materials > Paint & Finishes': ['COLOR', 'VOLUME'],
  'Construction & Building Materials > Tiles': ['SIZE', 'COLOR'],
  'Electronics & Technology > Chargers & Cables': ['CONNECTOR_TYPE', 'WATTAGE'],
  'Electronics & Technology > Gaming': ['AGE_RANGE', 'PLATFORM'],
  'Electronics & Technology > Phone Accessories': ['COMPATIBILITY'],
  'Electronics & Technology > Power Banks': ['BATTERY_CAPACITY', 'WATTAGE'],
  'Electronics & Technology > TVs': ['SCREEN_SIZE'],
  'Gifts, Party & Events > Balloons': ['SIZE'],
  'Groceries & Food > Dairy': ['PACK_SIZE'],
  'Groceries & Food > Grains': ['WEIGHT'],
  'Groceries & Food > Spices': ['WEIGHT'],
  'Home, Furniture & Garden > Lighting': ['POWER_REQUIREMENTS', 'WATTAGE'],
  'Industrial & Manufacturing > Factory Components': ['PART_NUMBER', 'COMPATIBILITY'],
  'Industrial & Manufacturing > Spare Parts': ['PART_NUMBER', 'COMPATIBILITY'],
  'Jewelry & Watches > Rings': ['STONE_TYPE', 'METAL_PURITY'],
  'Jewelry & Watches > Watches': ['BRAND', 'MODEL'],
  'Office, School & Stationery > Office Equipment': ['EQUIPMENT_TYPE', 'BRAND', 'MODEL', 'CAPACITY', 'DIMENSIONS', 'POWER_REQUIREMENTS', 'COMPATIBILITY'],
  'Office, School & Stationery > Printer Supplies': ['COMPATIBILITY'],
  'Packaging & Printing > Labels': ['COMPATIBILITY'],
  'Packaging & Printing > Printer Supplies': ['COMPATIBILITY'],
  'Pet Supplies > Pet Food': ['INGREDIENTS', 'WEIGHT', 'ALLERGEN_INFO'],
  'Shoes, Bags & Accessories > Backpacks': ['CAPACITY'],
  'Shoes, Bags & Accessories > Luggage': ['CAPACITY'],
  'Tools & Hardware > Electrical Hardware': ['VOLTAGE'],
  'Tools & Hardware > Power Tools': ['POWER_REQUIREMENTS', 'WATTAGE'],
  'Toys, Kids & Baby > Diapers': ['PACK_SIZE', 'WEIGHT'],
};
const leafUnitOverrides = {
  'Agriculture & Farming > Agricultural Machinery': ['PIECE', 'SET'],
  'Agriculture & Farming > Farming Tools': ['PIECE', 'SET'],
  'Agriculture & Farming > Permitted Seeds': ['PACK', 'BAG', 'GRAM', 'KILOGRAM'],
  'Automotive & Motorcycle > Oils': ['BOTTLE', 'CAN', 'LITER', 'MILLILITER'],
  'Automotive & Motorcycle > Tires': ['PIECE', 'PAIR', 'SET'],
  'Books, Media & Entertainment > Books': ['PIECE', 'SET'],
  'Books, Media & Entertainment > Instruments': ['PIECE', 'SET'],
  'Books, Media & Entertainment > Physical Media': ['PIECE', 'SET'],
  'Construction & Building Materials > Paint & Finishes': ['CAN', 'BOTTLE', 'LITER'],
  'Construction & Building Materials > Tiles': ['PIECE', 'BOX', 'SQUARE_METER'],
  'Construction & Building Materials > Doors': ['PIECE', 'SET'],
  'Construction & Building Materials > Windows': ['PIECE', 'SET'],
  'Fabric, Textile & Leather > Leather': ['PIECE', 'SQUARE_METER'],
  'Gifts, Party & Events > Balloons': ['PIECE', 'PACK', 'DOZEN'],
  'Groceries & Food > Beverages': ['BOTTLE', 'CAN', 'PACK', 'LITER', 'MILLILITER'],
  'Groceries & Food > Coffee': ['PACK', 'BAG', 'GRAM', 'KILOGRAM'],
  'Groceries & Food > Grains': ['BAG', 'PACK', 'KILOGRAM'],
  'Groceries & Food > Spices': ['PACK', 'BAG', 'GRAM', 'KILOGRAM'],
  'Home, Furniture & Garden > Furniture': ['PIECE', 'SET'],
  'Industrial & Manufacturing > Compressors': ['PIECE', 'SET'],
  'Industrial & Manufacturing > Industrial Machinery': ['PIECE', 'SET'],
  'Industrial & Manufacturing > Motors': ['PIECE', 'SET'],
  'Industrial & Manufacturing > Pumps': ['PIECE', 'SET'],
  'Jewelry & Watches > Earrings': ['PAIR', 'SET'],
  'Office, School & Stationery > Office Equipment': ['PIECE', 'SET'],
  'Office, School & Stationery > Notebooks': ['PIECE', 'PACK', 'BOX'],
  'Office, School & Stationery > Paper': ['SHEET', 'PACK', 'BOX'],
  'Packaging & Printing > Labels': ['PIECE', 'ROLL', 'SHEET', 'PACK'],
  'Pet Supplies > Pet Food': ['BAG', 'PACK', 'GRAM', 'KILOGRAM', 'CAN'],
  'Pet Supplies > Beds': ['PIECE', 'SET'],
  'Pet Supplies > Cages': ['PIECE', 'SET'],
  'Shoes, Bags & Accessories > Kids Shoes': ['PAIR'],
  "Shoes, Bags & Accessories > Men's Shoes": ['PAIR'],
  'Shoes, Bags & Accessories > Sports Shoes': ['PAIR'],
  "Shoes, Bags & Accessories > Women's Shoes": ['PAIR'],
  'Toys, Kids & Baby > Diapers': ['PACK', 'BOX'],
  'Toys, Kids & Baby > Strollers': ['PIECE', 'SET'],
};
const invalidPaths = new Set([
  'Electronics & Technology > Chargers & Cables',
  'Electronics & Technology > Gaming',
  'Electronics & Technology > Phone Accessories',
]);
const invalidRemoveCodes = ['STORAGE', 'RAM', 'SCREEN_SIZE', 'BATTERY_CAPACITY', 'PROCESSOR', 'NETWORK'];
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const rows = audit.categories.map(category => {
  const root = category.path.split(' > ')[0];
  const rule = rootRules[root];
  if (!rule) throw new Error(`No root rule for ${category.path}`);
  const existingCodes = new Set(category.attributes.map(attribute => attribute.code));
  const desired = category.classification === 'INTENTIONALLY_MINIMAL'
    ? []
    : category.path === 'Office, School & Stationery > Office Equipment'
      ? leafFields[category.path]
      : [...new Set([...rule.fields, ...(leafFields[category.path] || [])])];
  const addOptional = desired.filter(code => !existingCodes.has(code));
  for (const code of addOptional) if (!existing.has(code) && !definitions[code]) throw new Error(`Unknown attribute ${code}`);
  const allowedUnits = leafUnitOverrides[category.path] || rule.units;
  for (const code of allowedUnits) if (!audit.sellingUnits.activeCodes.includes(code)) throw new Error(`Unknown unit ${code}`);
  if (category.classification === 'NEEDS_CONFIGURATION' && addOptional.length === 0) {
    throw new Error(`No proposed addition for ${category.path}`);
  }
  return {
    id: category.id,
    path: category.path,
    beforeClassification: category.classification,
    beforeDirectAttributeCount: category.directAttributeCount,
    proposedOptionalAttributes: addOptional,
    proposedRequiredRuleChange: invalidPaths.has(category.path) ? { codes: ['BRAND', 'MODEL'], required: false } : null,
    proposedRemoveMappings: invalidPaths.has(category.path) ? invalidRemoveCodes : [],
    proposedAllowedSellingUnits: allowedUnits,
    reviewExistingMappings: category.classification === 'NEEDS_CONFIGURATION' && category.directAttributeCount > 0,
    productOwnerReviewRequired: true,
  };
});
if (rows.length !== 185 || rows.filter(row => row.beforeClassification === 'INVALID').length !== 3) throw new Error('Coverage drift');
const activeDefinitions = new Set(rows.flatMap(row => row.proposedOptionalAttributes).filter(code => !existing.has(code)));
const directAfter = rows.filter(row => row.beforeDirectAttributeCount > 0 || row.proposedOptionalAttributes.length > 0).length;
const summary = {
  status: 'PROPOSED_NOT_APPLIED',
  sourceAudit: 'seller_center_v2_category_coverage.json',
  leaves: rows.length,
  before: { directlyMapped: audit.summary.leavesWithDirectMappings, unmapped: audit.summary.leavesWithoutEffectiveAttributes, invalidRequiredRules: 3, categoryRestrictedSellingUnits: 0 },
  proposedAfter: { directlyMapped: directAfter, unmapped: rows.length - directAfter, invalidRequiredRules: 0, leavesWithProposedUnitLists: rows.length },
  caveat: 'Attribute presence is not semantic completeness. Broad mappings outside the three invalid leaves and five minimal classifications still need product-owner review. Unit lists require server validation and Android filtering before enforcement.',
};
writeFileSync(join(dir, 'seller_center_v3_category_proposal.json'), JSON.stringify({ summary, newDefinitions: Object.fromEntries([...activeDefinitions].sort().map(code => [code, definitions[code]])), categories: rows }, null, 2) + '\n');

const sql = [
  '-- REVIEW ONLY. Do not run without explicit approval, product-owner signoff, and a CLI-created migration.',
  '-- Existing product values, IDs, and hierarchy are untouched. All added fields are optional.',
  'begin;',
];
for (const code of [...activeDefinitions].sort()) {
  const [name, dataType, inputType, unit, suggestions = []] = definitions[code];
  sql.push(`insert into public.shop_attribute_definitions (code,name,display_name,data_type,input_type,is_variant_option,is_filterable,sort_order,unit,suggestions) values (${quote(code)},${quote(name)},${quote(name)},${quote(dataType)},${quote(inputType)},false,false,90,${unit ? quote(unit) : 'null'},${quote(JSON.stringify(suggestions))}::jsonb) on conflict (code) do nothing;`);
}
for (const row of rows) {
  for (const [index, code] of row.proposedOptionalAttributes.entries()) {
    sql.push(`insert into public.shop_category_attributes (category_id,attribute_definition_id,required,variant_capable,filterable,sort_order) select ${quote(row.id)}::uuid,id,false,${variantCodes.has(code)},${filterCodes.has(code)},${90 + index * 10} from public.shop_attribute_definitions where code=${quote(code)} and status='active' on conflict (category_id,attribute_definition_id) do nothing;`);
  }
  if (row.proposedRequiredRuleChange) {
    sql.push(`update public.shop_category_attributes ca set required=false from public.shop_attribute_definitions a where ca.category_id=${quote(row.id)}::uuid and ca.attribute_definition_id=a.id and a.code in ('BRAND','MODEL') and ca.required=true;`);
    sql.push(`delete from public.shop_category_attributes ca using public.shop_attribute_definitions a where ca.category_id=${quote(row.id)}::uuid and ca.attribute_definition_id=a.id and a.code in (${invalidRemoveCodes.map(quote).join(',')}) and not exists (select 1 from public.shop_products p where p.category_id=ca.category_id);`);
  }
}
sql.push('commit;', '', '-- Unit restrictions are deliberately NOT applied by this metadata DML. See', '-- seller_center_v3_selling_unit_enforcement_proposal.sql for a separate rollout.', '-- Broad mappings outside three zero-product invalid leaves are NOT removed before', '-- auditing stored product values and securing product-owner approval.');
writeFileSync(join(dir, 'seller_center_v3_category_metadata_proposal.sql'), sql.join('\n') + '\n');
writeFileSync(join(dir, 'seller_center_v3_coverage_diff.json'), JSON.stringify(summary, null, 2) + '\n');

const unitSql = [
  '-- REVIEW ONLY. Do not run without explicit approval and a coordinated Android/API rollout.',
  '-- Existing root-category listings stay valid; only newly selected leaf/unit pairs are checked.',
  'begin;',
  'create table if not exists public.shop_category_selling_units (',
  '  category_id uuid not null references public.shop_categories(id) on delete cascade,',
  '  unit_code text not null references public.shop_selling_units(code),',
  '  primary key (category_id, unit_code)',
  ');',
  'alter table public.shop_category_selling_units enable row level security;',
  'revoke all on public.shop_category_selling_units from public, anon, authenticated;',
  'grant select on public.shop_category_selling_units to anon, authenticated;',
  'grant all on public.shop_category_selling_units to service_role;',
  'drop policy if exists public_read_active_category_units on public.shop_category_selling_units;',
  'create policy public_read_active_category_units on public.shop_category_selling_units for select to anon, authenticated using (',
  '  exists (select 1 from public.shop_categories c where c.id=category_id and c.is_active)',
  '  and exists (select 1 from public.shop_selling_units u where u.code=unit_code and u.is_active)',
  ');',
];
for (const row of rows) {
  for (const unit of row.proposedAllowedSellingUnits) {
    unitSql.push(`insert into public.shop_category_selling_units (category_id,unit_code) values (${quote(row.id)}::uuid,${quote(unit)}) on conflict do nothing;`);
  }
}
unitSql.push(
  'create or replace function public.shop_validate_category_selling_unit() returns trigger language plpgsql as $$',
  'declare v_leaf boolean;',
  'begin',
  '  if new.category_id is null or new.selling_unit_code is null then return new; end if;',
  '  if tg_op = \'UPDATE\' and new.category_id is not distinct from old.category_id',
  '     and new.selling_unit_code is not distinct from old.selling_unit_code then return new; end if;',
  '  select is_active and is_leaf and seller_selectable into v_leaf from public.shop_categories where id=new.category_id;',
  '  if coalesce(v_leaf,false) and not exists (',
  '    select 1 from public.shop_category_selling_units where category_id=new.category_id and unit_code=new.selling_unit_code',
  '  ) then raise exception using errcode=\'23514\', message=\'invalid-category-selling-unit\'; end if;',
  '  return new;',
  'end $$;',
  'drop trigger if exists shop_validate_category_selling_unit_trg on public.shop_products;',
  'create trigger shop_validate_category_selling_unit_trg before insert or update of category_id,selling_unit_code on public.shop_products',
  'for each row execute function public.shop_validate_category_selling_unit();',
  'commit;',
  '',
  '-- Approval prerequisite: verify every existing leaf listing uses a recommended unit;',
  '-- deploy category-unit API projection and Android filtering before enabling this trigger;',
  '-- test draft save, category switches, submit, admin approval and legacy root listings.',
);
writeFileSync(join(dir, 'seller_center_v3_selling_unit_enforcement_proposal.sql'), unitSql.join('\n') + '\n');
console.log(JSON.stringify({ rows: rows.length, newDefinitions: activeDefinitions.size, addMappings: rows.reduce((n, row) => n + row.proposedOptionalAttributes.length, 0), directAfter }));
