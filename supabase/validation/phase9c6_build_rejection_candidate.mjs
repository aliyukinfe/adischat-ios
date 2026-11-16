// Mechanical extraction from the deployed-hash-matched transition migration.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';

const source = readFileSync('supabase/migrations/20260810101713_adishop_phase_1_4j_order_acceptance_sla.sql', 'utf8');
const start = source.indexOf('create or replace function public.adishop_transition_seller_order_group(');
const end = source.indexOf('\n$function$;', start) + '\n$function$;'.length;
assert.ok(start >= 0 && end > start);
const deployed = source.slice(start, end);
const oldBlock = `  if next_status = 'rejected' and group_record.inventory_released_at is null then
    update public.shop_inventory i
    set quantity_available = i.quantity_available + oi.quantity, updated_at = clock_timestamp()
    from public.shop_order_items oi
    where oi.order_group_id = group_record.id and i.product_id = oi.product_id and i.variant_id is not distinct from oi.variant_id;
    update public.shop_order_groups set inventory_released_at = clock_timestamp() where id = group_record.id and inventory_released_at is null;
  end if;`;
const newBlock = `  if next_status = 'rejected' and group_record.inventory_released_at is null then
    select count(*) into expected_release_rows
    from (select distinct product_id, variant_id from public.shop_order_items
          where order_group_id = group_record.id) inventory_keys;
    if expected_release_rows = 0 or exists (
      select 1 from public.shop_order_items oi
      where oi.order_group_id = group_record.id
        and not exists (
          select 1 from public.shop_inventory i
          where i.product_id = oi.product_id
            and i.variant_id is not distinct from oi.variant_id
        )
    ) then
      raise exception 'inventory-release-missing';
    end if;

    with release_totals as (
      select i.id as inventory_id, sum(oi.quantity)::bigint as release_quantity
      from public.shop_order_items oi
      join public.shop_inventory i
        on i.product_id = oi.product_id
       and i.variant_id is not distinct from oi.variant_id
      where oi.order_group_id = group_record.id
      group by i.id
    )
    update public.shop_inventory i
    set quantity_available = (i.quantity_available::bigint + r.release_quantity)::integer,
        updated_at = clock_timestamp()
    from release_totals r
    where i.id = r.inventory_id;
    get diagnostics released_rows = row_count;
    if released_rows <> expected_release_rows then
      raise exception 'inventory-release-missing';
    end if;

    update public.shop_order_groups set inventory_released_at = clock_timestamp()
    where id = group_record.id and inventory_released_at is null;
  end if;`;
assert.equal(deployed.split(oldBlock).length, 2, 'deployed rejection block changed');
assert.equal(deployed.split('  payment_result jsonb := jsonb_build_object(\'status\', \'not_required\');').length, 2);
const candidate = deployed
  .replace('  payment_result jsonb := jsonb_build_object(\'status\', \'not_required\');',
    '  payment_result jsonb := jsonb_build_object(\'status\', \'not_required\');\n  expected_release_rows integer;\n  released_rows integer;')
  .replace(oldBlock, newBlock);
const sql = `-- LOCAL CANDIDATE ONLY. Generated from the deployed-hash-matched seller transition body.
begin;
set local lock_timeout = '5s';
${candidate}
revoke all on function public.adishop_transition_seller_order_group(uuid,uuid,text,text,jsonb) from public, anon, authenticated;
grant execute on function public.adishop_transition_seller_order_group(uuid,uuid,text,text,jsonb) to service_role;
commit;
`;
writeFileSync('supabase/validation/phase9c6_seller_rejection_inventory_candidate.sql', sql, 'utf8');
process.stdout.write('Generated scoped seller rejection candidate\n');
