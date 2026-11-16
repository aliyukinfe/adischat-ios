// Focused seller rejection checks in the disposable Phase 9C.5 PostgreSQL 17.11 fixture.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';

const binary = 'C:/PostgreSQL/17/pgsql/bin/psql.exe';
const args = ['-X', '-v', 'ON_ERROR_STOP=1', '-A', '-t', '-h', '127.0.0.1', '-p', '55441', '-U', 'postgres', '-d', 'phase9c5'];
const seller = '20000000-0000-4000-8000-000000000001';
const store = '10000000-0000-4000-8000-000000000001';
const product = '30000000-0000-4000-8000-000000000001';
const foreign = '20000000-0000-4000-8000-000000000099';
let passed = 0;

function sql(query) {
  return execFileSync(binary, [...args, '-c', query], { encoding: 'utf8' }).trim().split('\n').at(-1);
}
function check(condition, message) { assert.ok(condition, message); passed++; }
function fixture(payment = 'unpaid') {
  return JSON.parse(sql(`select public.phase9c3_new_order(interval '5 minutes','${payment}')`));
}
function stock() {
  return Number(sql(`select quantity_available from public.shop_inventory where product_id='${product}' and variant_id is null`));
}
function count(table, orderId) {
  return Number(sql(`select count(*) from public.${table} where order_id='${orderId}'`));
}
function eventCount(orderId) {
  return Number(sql(`select count(*) from public.shop_payment_events e
    join public.shop_payments p on p.id=e.payment_id where p.order_id='${orderId}'`));
}
function reject(groupId, requestId, actor = seller, selectedStore = store) {
  return JSON.parse(sql(`select public.adishop_transition_seller_order_group_scoped(
    '${actor}','${groupId}','${selectedStore}','reject','Stock unavailable',
    '{"reasonCode":"out_of_stock","requestId":"${requestId}"}')`));
}
function accept(groupId) {
  return JSON.parse(sql(`select public.adishop_transition_seller_order_group_scoped(
    '${seller}','${groupId}','${store}','accept',null,'{}')`));
}
function denied(fn, code) {
  try { fn(); return false; } catch (error) { return String(error.stderr).includes(code); }
}

sql(`update public.shop_inventory set quantity_available=100, quantity_reserved=0 where product_id='${product}' and variant_id is null`);
{
  const { orderId, groupId } = fixture();
  sql(`insert into public.shop_order_items(order_group_id,product_id,quantity)
    values ('${groupId}','${product}',2)`);
  sql(`update public.shop_inventory set quantity_available=quantity_available-2 where product_id='${product}' and variant_id is null`);
  const before = stock();
  check(reject(groupId,'duplicate-line').fulfillmentStatus === 'rejected', 'rejection response');
  check(stock() - before === 3, 'combined quantity released');
  check(sql(`select inventory_released_at is not null from public.shop_order_groups where id='${groupId}'`) === 't', 'release marker');
  check(reject(groupId,'duplicate-line').idempotent === true && stock() - before === 3, 'repeated rejection released twice');
  check(denied(() => reject(groupId,'different-request'),'invalid-transition') && stock() - before === 3,
    'stale rejection released twice');
  check(count('shop_refund_requests',orderId) === 0, 'unpaid reversal');
  check(sql(`select count(*) from public.shop_order_status_history where order_id='${orderId}' and event_code='seller_rejected'`) === '1', 'history duplicated');
  check(sql(`select count(*) from public.shop_audit_logs where entity_id='${groupId}' and action='seller_order_group.reject' and actor_profile_id='${seller}'`) === '1', 'audit actor/count');
}
{
  const { orderId, groupId } = fixture('paid');
  check(reject(groupId,'paid').sellerResponseStatus === 'rejected', 'paid rejection');
  check(count('shop_refund_requests',orderId) === 1, 'pending refund count');
  check(sql(`select status from public.shop_refund_requests where order_id='${orderId}'`) === 'submitted', 'refund falsely completed');
  check(eventCount(orderId) === 1 && sql(`select status from public.shop_payments where order_id='${orderId}'`) === 'paid', 'payment event/status');
  check(reject(groupId,'paid').idempotent === true && eventCount(orderId) === 1, 'refund duplicated');
}
{
  const { orderId, groupId } = fixture('authorized');
  check(reject(groupId,'authorized').sellerResponseStatus === 'rejected', 'authorized rejection');
  check(sql(`select seller_response_refund_status from public.shop_order_groups where id='${groupId}'`) === 'authorization_void_pending', 'void not pending');
  check(count('shop_refund_requests',orderId) === 0 && eventCount(orderId) === 1, 'void/refund record');
}
{
  const { orderId, groupId } = fixture('paid');
  const before = stock();
  sql(`delete from public.shop_inventory where product_id='${product}' and variant_id is null`);
  check(denied(() => reject(groupId,'missing'),'inventory-release-missing'), 'missing inventory accepted');
  check(sql(`select fulfillment_status from public.shop_order_groups where id='${groupId}'`) === 'awaiting_seller_confirmation', 'partial rejection state');
  check(count('shop_refund_requests',orderId) === 0 && eventCount(orderId) === 0, 'partial reversal');
  check(sql(`select inventory_released_at is null from public.shop_order_groups where id='${groupId}'`) === 't', 'partial release marker');
  sql(`insert into public.shop_inventory(product_id,variant_id,quantity_available) values ('${product}',null,${before})`);
}
{
  const { groupId } = fixture();
  const before = stock();
  check(denied(() => reject(groupId,'foreign',foreign),'seller-order-not-found'), 'foreign seller authorized');
  check(denied(() => reject(groupId,'wrong-store',seller,foreign),'seller-order-not-found'), 'wrong store authorized');
  check(stock() === before && sql(`select fulfillment_status from public.shop_order_groups where id='${groupId}'`) === 'awaiting_seller_confirmation', 'denied mutation');
}
{
  const { groupId } = fixture();
  check(accept(groupId).fulfillmentStatus === 'accepted', 'accept state');
  check(reject(groupId,'after-accept').fulfillmentStatus === 'rejected', 'existing policy permits rejection after acceptance');
}
for (const state of ['shipped','delivered','cancelled','expired']) {
  const { groupId } = fixture();
  sql(`update public.shop_order_groups set fulfillment_status='${state}',status='${state}' where id='${groupId}'`);
  const before = stock();
  check(denied(() => reject(groupId,`after-${state}`),'invalid-transition'), `${state} rejected`);
  check(stock() === before, `${state} altered inventory`);
}
{
  sql(`update public.shop_inventory set quantity_reserved=2 where product_id='${product}' and variant_id is null`);
  const { groupId } = fixture();
  const reserved = sql(`select quantity_reserved from public.shop_inventory where product_id='${product}' and variant_id is null`);
  check(reject(groupId,'reserved').sellerResponseStatus === 'rejected', 'reserved-stock rejection');
  check(sql(`select quantity_reserved from public.shop_inventory where product_id='${product}' and variant_id is null`) === reserved,
    'reserved quantity changed');
  sql(`update public.shop_inventory set quantity_reserved=0 where product_id='${product}' and variant_id is null`);
}
check(sql('select count(*) from public.shop_inventory where quantity_available<0 or quantity_reserved<0 or quantity_reserved>quantity_available') === '0', 'inventory constraint');
process.stdout.write(`phase9c6 rejection focused checks passed: ${passed}\n`);
