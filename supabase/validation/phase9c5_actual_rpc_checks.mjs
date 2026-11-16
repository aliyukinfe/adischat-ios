// Disposable PostgreSQL 17 only. Loads unchanged order RPCs from reviewed migrations.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const psql = 'C:/PostgreSQL/17/pgsql/bin/psql.exe';
const baseArgs = ['-X', '-v', 'ON_ERROR_STOP=1', '-A', '-t', '-h', '127.0.0.1', '-p', '55441', '-U', 'postgres', '-d', process.argv[3] || 'phase9c5'];
const root = 'supabase/migrations/';
const slaSource = readFileSync(root + '20260810101713_adishop_phase_1_4j_order_acceptance_sla.sql', 'utf8');
const lifecycleSource = readFileSync(root + '20260720112511_adishop_phase_1_4c_order_lifecycle_foundation.sql', 'utf8');
const rejectionSource = readFileSync(root + '20260726141304_adishop_phase_1_4g_2_buyer_protection_rejections.sql', 'utf8');
const scopedSource = readFileSync(root + '20261027134112_adisgebeya_seller_order_store_scope.sql', 'utf8');
const syntheticSource = readFileSync('supabase/validation/phase9c3_order_test_setup.sql', 'utf8');
const buyer = '40000000-0000-4000-8000-000000000001';
const seller = '20000000-0000-4000-8000-000000000001';
const store = '10000000-0000-4000-8000-000000000001';
const product = '30000000-0000-4000-8000-000000000001';

function run(input) {
  return execFileSync(psql, [...baseArgs, '-f', '-'], { input, encoding: 'utf8' }).trim();
}
function query(input) {
  return execFileSync(psql, [...baseArgs, '-c', input], { encoding: 'utf8' }).trim();
}
function functionSql(source, name) {
  const regex = new RegExp(`create (?:or replace )?function public\\.${name}\\([\\s\\S]*?\\n(?:\\$function\\$|\\$\\$);`, 'i');
  const match = source.match(regex);
  assert.ok(match, `missing source ${name}`);
  return match[0];
}
function install(source, name) {
  run(functionSql(source, name));
}
function fixture(age = '5 minutes', payment = 'unpaid') {
  return JSON.parse(query(`select public.phase9c3_new_order(interval '${age}', '${payment}')`));
}
function scalar(sql) {
  return query(sql).split('\n').at(-1);
}
function count(table, orderId) {
  return Number(scalar(`select count(*) from public.${table} where order_id='${orderId}'`));
}
function paymentEventCount(orderId) {
  return Number(scalar(`select count(*) from public.shop_payment_events e
    join public.shop_payments p on p.id=e.payment_id where p.order_id='${orderId}'`));
}
function inventory() {
  return Number(scalar(`select quantity_available from public.shop_inventory where product_id='${product}' and variant_id is null`));
}
function addSecondLine(groupId) {
  query(`insert into public.shop_order_items(order_group_id, product_id, quantity) values ('${groupId}','${product}',2)`);
  query(`update public.shop_inventory set quantity_available=quantity_available-2 where product_id='${product}' and variant_id is null`);
}
function expire(groupId) {
  return JSON.parse(query(`select public.adishop_expire_seller_order_group('${groupId}','manual')`));
}
function makeDue(groupId) {
  query(`update public.shop_order_groups set seller_response_required_at=clock_timestamp()-interval '1 second' where id='${groupId}'`);
}
function accept(groupId) {
  return JSON.parse(query(`select public.adishop_transition_seller_order_group_scoped('${seller}','${groupId}','${store}','accept',null,'{}')`));
}
function cancel(orderId) {
  return JSON.parse(query(`select public.adishop_request_order_cancellation('${buyer}','${orderId}','customer_request','QA')`));
}

if (process.argv[2] === 'setup') {
  install(lifecycleSource, 'adishop_profile_can_fulfill_store');
  install(rejectionSource, 'adishop_rejection_reason_label');
  for (const name of [
    'adishop_prepare_seller_response_sla', 'adishop_create_notification_once',
    'adishop_notify_new_seller_order_group', 'adishop_refresh_parent_order_status',
    'adishop_prepare_group_payment_reversal', 'adishop_expire_seller_order_group',
    'adishop_expire_due_seller_order_groups', 'adishop_transition_seller_order_group',
  ]) install(slaSource, name);
  install(scopedSource, 'adishop_transition_seller_order_group_scoped');
  install(syntheticSource, 'phase9c3_new_order');
  run(`create trigger shop_order_groups_prepare_seller_response_sla before insert or update of fulfillment_status
    on public.shop_order_groups for each row execute function public.adishop_prepare_seller_response_sla();
    create trigger shop_order_groups_notify_new_seller_order after insert on public.shop_order_groups
    for each row execute function public.adishop_notify_new_seller_order_group();`);
  const hashes = query(`select p.proname || ':' || md5(pg_get_functiondef(p.oid)) from pg_proc p
    where p.pronamespace='public'::regnamespace and p.proname in
    ('adishop_expire_seller_order_group','adishop_transition_seller_order_group',
     'adishop_transition_seller_order_group_scoped','adishop_prepare_group_payment_reversal')
    order by p.proname`);
  process.stdout.write(`PostgreSQL 17.11 loaded exact migration RPC bodies (local canonical hashes):\n${hashes}\n`);
} else if (process.argv[2] === 'baseline') {
  const { orderId, groupId } = fixture();
  addSecondLine(groupId);
  const before = inventory();
  makeDue(groupId);
  assert.equal(expire(groupId).sellerResponseStatus, 'expired');
  const delta = inventory() - before;
  assert.ok(delta > 0 && delta < 3, `expected deployed under-release, got ${delta}`);
  process.stdout.write(`deployed expiry duplicate-line defect: PASS, released ${delta}/3 units\n`);
} else if (process.argv[2] === 'candidate') {
  run(readFileSync('supabase/validation/phase9c5_seller_expiry_inventory_candidate.sql', 'utf8'));
  run(readFileSync('supabase/validation/phase9c2_buyer_cancellation_candidate.sql', 'utf8'));
  query(`update public.shop_inventory set quantity_available=100, quantity_reserved=0 where product_id='${product}' and variant_id is null`);
  let passed = 0;
  const check = (condition, message) => { assert.ok(condition, message); passed++; };
  {
    const { orderId, groupId } = fixture();
    addSecondLine(groupId);
    const before = inventory();
    makeDue(groupId);
    check(expire(groupId).sellerResponseStatus === 'expired', 'expiry failed');
    check(inventory() - before === 3, 'combined release must be 3');
    check(expire(groupId).idempotent === true && inventory() - before === 3, 'repeat released twice');
    check(scalar(`select seller_response_status from public.shop_order_groups where id='${groupId}'`) === 'expired', 'status');
    check(count('shop_refund_requests', orderId) === 0, 'unpaid should not refund');
  }
  {
    const { orderId, groupId } = fixture('25 hours', 'paid');
    const before = inventory();
    makeDue(groupId);
    check(expire(groupId).refundStatus === 'refund_pending', 'paid refund pending');
    check(inventory() - before === 1, 'paid inventory release');
    check(count('shop_refund_requests', orderId) === 1, 'refund request count');
    check(scalar(`select status from public.shop_refund_requests where order_id='${orderId}'`) === 'submitted', 'refund not provider-confirmed');
    check(scalar(`select status from public.shop_payments where order_id='${orderId}'`) === 'paid', 'payment mutated');
    check(expire(groupId).idempotent === true && count('shop_refund_requests', orderId) === 1, 'duplicate refund');
  }
  {
    const { orderId, groupId } = fixture('25 hours', 'authorized');
    makeDue(groupId);
    check(expire(groupId).refundStatus === 'authorization_void_pending', 'void pending');
    check(count('shop_refund_requests', orderId) === 0, 'authorized payment refunded');
    check(scalar(`select status from public.shop_payments where order_id='${orderId}'`) === 'authorized', 'authorization mutated');
  }
  {
    const { orderId, groupId } = fixture();
    check(accept(groupId).sellerResponseStatus === 'accepted', 'actual acceptance');
    makeDue(groupId);
    check(expire(groupId).idempotent === true, 'accepted expired');
    check(scalar(`select fulfillment_status from public.shop_order_groups where id='${groupId}'`) === 'accepted', 'accepted state changed');
    let denied = false;
    try { cancel(orderId); } catch (error) { denied = String(error.stderr).includes('cancellation-not-eligible'); }
    check(denied, 'cancel after acceptance');
  }
  {
    const { orderId, groupId } = fixture('25 hours', 'paid');
    const before = inventory();
    makeDue(groupId);
    query(`delete from public.shop_inventory where product_id='${product}' and variant_id is null`);
    let denied = false;
    try { expire(groupId); } catch (error) { denied = String(error.stderr).includes('inventory-release-missing'); }
    check(denied, 'missing inventory did not fail');
    check(scalar(`select seller_response_status from public.shop_order_groups where id='${groupId}'`) === 'pending', 'partial status write');
    check(count('shop_refund_requests', orderId) === 0, 'partial refund write');
    check(count('shop_order_status_history', orderId) === 1, 'partial history write');
    check(paymentEventCount(orderId) === 0, 'partial payment event');
    query(`insert into public.shop_inventory(product_id,variant_id,quantity_available) values ('${product}',null,${before})`);
  }
  for (const status of ['pending','cancelled','shipped','delivered','rejected','expired']) {
    const { orderId, groupId } = fixture();
    if (status !== 'pending') {
      query(`update public.shop_order_groups set status='${status}', fulfillment_status='${status}',
        seller_response_status=case when '${status}'='expired' then 'expired' else seller_response_status end
        where id='${groupId}'`);
      query(`update public.shop_orders set status='${status}', fulfillment_status='${status}' where id='${orderId}'`);
    }
    const before = inventory();
    const response = expire(groupId);
    check(status === 'pending' ? response.code === 'seller-response-not-due' : response.idempotent === true,
      `${status} expiry eligibility`);
    check(inventory() === before, `${status} altered inventory`);
  }
  {
    const { orderId, groupId } = fixture('5 minutes', 'paid');
    const before = inventory();
    check(cancel(orderId).status === 'cancelled', 'paid buyer cancellation');
    check(inventory() - before === 1, 'cancellation released one unit');
    check(count('shop_refund_requests', orderId) === 1, 'buyer cancellation refund request');
    check(scalar(`select status from public.shop_refund_requests where order_id='${orderId}'`) === 'submitted', 'buyer refund confirmation inferred');
    check(paymentEventCount(orderId) === 1, 'buyer refund event dedupe');
    check(scalar(`select status from public.shop_payments where order_id='${orderId}'`) === 'paid', 'buyer payment status changed');
  }
  {
    const { orderId, groupId } = fixture();
    addSecondLine(groupId);
    const before = inventory();
    check(cancel(orderId).status === 'cancelled', 'duplicate-line cancellation');
    check(inventory() - before === 3, 'duplicate-line cancellation released combined quantity');
    let denied = false;
    try { cancel(orderId); } catch (error) { denied = String(error.stderr).includes('cancellation-not-eligible'); }
    check(denied && inventory() - before === 3, 'duplicate cancellation released twice');
    check(count('shop_order_cancellations', orderId) === 1, 'duplicate cancellation record');
  }
  check(scalar(`select count(*) from public.shop_inventory where quantity_available<0 or quantity_reserved<0
    or quantity_reserved>quantity_available`) === '0', 'inventory invariant');
  process.stdout.write(`phase9c5 candidate focused SQL checks passed: ${passed}\n`);
} else if (process.argv[2] === 'rejection-baseline') {
  const { groupId } = fixture();
  addSecondLine(groupId);
  const before = inventory();
  const response = JSON.parse(query(`select public.adishop_transition_seller_order_group_scoped(
    '${seller}','${groupId}','${store}','reject','Stock unavailable',
    '{"reasonCode":"out_of_stock","requestId":"phase9c5-rejection"}')`));
  assert.equal(response.fulfillmentStatus, 'rejected');
  const delta = inventory() - before;
  assert.ok(delta > 0 && delta < 3, `expected deployed rejection under-release, got ${delta}`);
  process.stdout.write(`deployed seller rejection duplicate-line defect: PASS, released ${delta}/3 units\n`);
} else {
  throw new Error('use setup, baseline, candidate, or rejection-baseline');
}
