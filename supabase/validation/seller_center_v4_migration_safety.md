# Seller Center V4 migration safety review

Status: **NOT APPROVED FOR PRODUCTION EXECUTION**. Read-only live checks on 2026-10-26 against project `mcewjfnkrtiiolzibsuh`. The V3 SQL files are proposals, not applied migrations. The reviewed delta below supersedes their overbroad deletion list; do not run the V3 SQL unchanged. Re-run every preflight immediately before an explicitly approved rollout.

## Live baseline and exact proposed changes

| Object | Live | Proposal if fully approved | Notes |
| --- | ---: | ---: | --- |
| `shop_categories` | 210 rows, 185 active seller-selectable leaves | unchanged | No hierarchy edits. |
| `shop_attribute_definitions` | 26 active rows | 45 active rows | 19 new codes; live code collisions: 0. |
| `shop_category_attributes` | 373 rows, 342 on selectable leaves | 840 rows, 809 on selectable leaves | 472 new optional mappings, five reviewed removals in two zero-product leaves. Six existing `required=true` flags become false. |
| `shop_selling_units` | 25 active rows | unchanged | No existing global unit deleted. |
| `shop_category_selling_units` | absent | 849 candidate pairs across 185 leaves | Separate proposal; **do not enforce yet**. |
| `shop_products` | 54 rows, 3 on active selectable leaves | unchanged | No product rewrite. |

Before/after direct leaf mapping coverage is 73/185 to a proposed 180/185; five provisional minimal leaves remain unmapped. This is attribute presence, not proof of semantic completeness. There are no duplicate category/attribute pairs or attribute codes in the live baseline.

The three invalid leaves are Chargers & Cables, Gaming, and Phone Accessories. Each has ten current mappings, including required BRAND and MODEL, and each has **zero associated products**, including deleted products (rechecked 2026-10-26). The reviewed delta makes BRAND and MODEL optional in all three leaves. It removes PROCESSOR, RAM, and STORAGE from Chargers & Cables, and PROCESSOR and RAM from Phone Accessories. It removes **no** inherited mapping from broad Gaming. The other optional fields remain because they can describe some products in these broad leaves; a later taxonomy split may narrow them. Recheck product associations inside the deployment transaction.

The old V3 SQL proposes 18 mapping deletions. Its extra 13 deletions are **not** in the reviewed delta. The 19 new definitions and 472 optional mapping additions remain proposed, not approved for production execution. Exact per-leaf additions and candidate units are enumerated in `seller_center_v3_category_proposal.json`; the V3 SQL is source material only and must be regenerated/reviewed before any migration. The 19 proposed new definition codes are AGE_RANGE, ALLERGEN_INFO, AUTHOR, COMPATIBILITY, CONNECTOR_TYPE, DIMENSIONS, EQUIPMENT_TYPE, INGREDIENTS, ISBN, METAL_PURITY, PART_NUMBER, PLATFORM, POWER_REQUIREMENTS, SCENT, SEED_VARIETY, STORAGE_INSTRUCTIONS, VEHICLE_FITMENT, VOLTAGE, and WATTAGE.

Office Equipment's EQUIPMENT_TYPE (choice), BRAND (text), MODEL (text), CAPACITY (choice), DIMENSIONS (text), POWER_REQUIREMENTS (text), and COMPATIBILITY (text) are accepted as **optional**. Sewing Accessories, Event Accessories, Gifts, Wedding Supplies, and Jewelry Accessories remain intentionally minimal; universal title, category, unit, price, image, and stock/submission validation still applies.

The five provisional minimal leaves are Sewing Accessories, Event Accessories, Gifts, Wedding Supplies, and Jewelry Accessories. Keep them minimal unless a product owner identifies a field essential across that entire leaf. A deleted Sewing Accessories listing used METER; the proposed unit list retains METER.

Existing active-leaf listings use Rhinestones/KILOGRAM and T-Shirts/PIECE; both units are in their candidate lists. The candidate lists are not approved business rules. Some broad inherited unit choices still need per-leaf review before enforcement.

## Compatibility verdict

- Existing category-attribute RPCs already project mapped definitions, data types, suggestions, and required flags. The seller save RPC resolves attributes by category and code. No new endpoint is inherently required for optional metadata.
- The current Android form renders non-variant fields as free-text `OutlinedTextField`s. It does **not** present EQUIPMENT_TYPE's proposed choice options as a controlled selector. Number fields are parsed from text; invalid non-empty numbers can produce an unusable null value. Resolve this before exposing those fields.
- The current server accepts mapped specification values without enforcing `data_type` or choice suggestions. Do not claim the suggested equipment types are an allowed-value constraint.
- Android now ranks provisional category-aware unit suggestions while preserving every active server unit and the existing selected unit. This is advisory only, not a category-unit API contract. The V3 unit trigger would reject units still offered by the picker. Split unit rollout into mapping/projection, Android compatibility, then enforcement after API/database validation and a separate approval.
- `shop_products` already has classification and submission triggers. A new unit trigger would add a second classification check; review ordering and the existing trigger function before enabling it. The V3 unit SQL is **not safe to run as one step**.
- Of the 472 candidate new mappings, 19 are on leaves previously marked COMPLETE and 53 already-mapped leaves gain at least one field. Product-owner approval is required for these additions, not just the zero-field leaves.

## Approved-rollout sequence to prepare (none executed)

1. Product owner signs off every leaf's optional fields, choice semantics, and unit candidates. The three invalid and five minimal leaves have business direction, but the other optional additions remain proposed. Freeze a newly generated reviewed JSON and SQL checksum; do not execute the old V3 file.
2. Fix and test Android choice/number inputs. Add a read-only category-unit API projection and filtered seller picker; deploy these before any unit enforcement.
3. Create a real CLI migration only after a separate explicit SQL execution approval. In one transaction, lock the relevant category/mapping configuration, recheck all 185 leaves and the three zero-product guards, insert the 19 definitions and approved mappings with conflict checks, adjust six required flags, and delete only the five reviewed invalid mappings. Abort on any drift. The V3 metadata DML is syntactically repeatable, but `ON CONFLICT DO NOTHING` must not silently mask a changed definition.
4. Verify category forms, draft save/reload, and submission in a controlled seller account. Only then deploy category-unit mapping data as a separate read-only stage. Verify existing listings and new draft unit choices.
5. In a later separately approved migration, add server-side unit enforcement. Gate it on the API and Android version, use an existing-compatible validation path, and test legacy root-category listings. Do not combine the table, data, API change, Android rollout, and trigger in one transaction.

**Rollback:** Before commit, any failed preflight or statement rolls back the whole transaction. After metadata commit, use a reviewed compensating migration: restore the captured six required flags and five mappings, and remove only newly added mappings unused by products. Do not drop new definitions that acquired product values; leave them inactive/hidden until safely migrated. For unit enforcement, disable/drop only the new trigger first; retain mapping data for diagnosis. Never delete production products or rewrite seller inventory as rollback.

## Required preflight (read-only until explicit execution approval)

```sql
select count(*) from public.shop_categories where is_active and is_leaf and seller_selectable;
select count(*) from public.shop_attribute_definitions where status='active';
select count(*) from public.shop_category_attributes;
select c.name, count(p.id) as products
from public.shop_categories c left join public.shop_products p on p.category_id=c.id
where c.id in ('5cdc6a5a-8ce3-45ce-8669-a4d51d9e9051',
               '46f2788b-2431-477b-897e-5e9dce3d1595',
               '43f976c7-6f24-4b7b-81fa-b3f41a7717f8')
group by c.id, c.name;
select c.id, c.name, p.selling_unit_code, count(*) as listings
from public.shop_products p join public.shop_categories c on c.id=p.category_id
where c.is_active and c.is_leaf and c.seller_selectable
group by c.id, c.name, p.selling_unit_code;
```

Also compare the live definitions for every proposed code, all 472 proposed `(category_id, attribute_definition_id)` pairs, mapped product values, API responses, RLS and grants, and remote migration history. Refuse rollout if the live state differs from the reviewed snapshot.

## Post-deployment checks (for a future approved rollout)

```sql
select count(*) from public.shop_attribute_definitions where status='active';
select count(*) from public.shop_category_attributes;
select count(*) from public.shop_category_attributes ca
join public.shop_categories c on c.id=ca.category_id
where c.is_active and c.is_leaf and c.seller_selectable;
select count(*) from (
  select category_id, attribute_definition_id from public.shop_category_attributes
  group by 1,2 having count(*)>1
) duplicates;
select c.name, a.code, ca.required from public.shop_category_attributes ca
join public.shop_categories c on c.id=ca.category_id
join public.shop_attribute_definitions a on a.id=ca.attribute_definition_id
where c.name in ('Chargers & Cables','Gaming','Phone Accessories')
order by c.name,a.code;
```

Expected after *separately approved reviewed metadata only*: 45 active definitions, 840 total mappings, 809 selectable-leaf mappings, zero duplicate pairs, six required flags relaxed, five reviewed mappings absent, unchanged products and selling units. Do not interpret these counts alone as rollout success: verify Office Equipment's seven optional fields, forms, drafts, submission, buyer display, and authorization. Unit mapping/enforcement counts belong to later stages.

## Approval checklist

- [ ] Human business-rule signoff for 185 leaf proposals, especially Gaming, Phone Accessories, 29 formerly COMPLETE leaves, and five minimal leaves.
- [ ] EQUIPMENT_TYPE choice and numeric field behavior fixed and tested end to end.
- [ ] Existing-product values and all candidate unit restrictions reconciled.
- [ ] API projection and Android unit filtering available before server enforcement.
- [ ] Fresh remote migration-history, RLS/grant, row-count, and association preflight reviewed.
- [ ] Backup and compensating rollback migration reviewed.
- [ ] Separate explicit approval to execute the specific final migration; this document is **not** that approval.
