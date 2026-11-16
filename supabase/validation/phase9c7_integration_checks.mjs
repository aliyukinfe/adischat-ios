// Disposable PostgreSQL 17.11 integrated order candidate, synthetic identities only.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';

const psql = 'C:/PostgreSQL/17/pgsql/bin/psql.exe';
const args = ['-X', '-v', 'ON_ERROR_STOP=1', '-A', '-t', '-h', '127.0.0.1', '-p', '55441', '-U', 'postgres', '-d', 'phase9c7'];
const buyer = '40000000-0000-4000-8000-000000000001';
const seller = '20000000-0000-4000-8000-000000000001';
const store = '10000000-0000-4000-8000-000000000001';
const product = '30000000-0000-4000-8000-000000000001';
let passed = 0;
function sql(query) {
  return execFileSync(psql, [...args, '-c', query], { encoding: 'utf8' }).trim().split('\n').at(-1);
}
function check(condition, message) { assert.ok(condition, message); passed++; }
function fixture(age = '5 minutes', payment = 'unpaid') {
  return JSON.parse(sql(`select public.phase9c3_new_order(interval '${age}','${payment}')`));
}
function stock() {
  return Number(sql(`select quantity_available from public.shop_inventory where product_id='${product}' and variant_id is null`));
}
function count(table, orderId) {
  return Number(sql(`select count(*) from public.${table} where order_id='${orderId}'`));
}
function addSecondLine(groupId) {
  sql(`insert into public.shop_order_items(order_group_id,product_id,quantity) values ('${groupId}','${product}',2)`);
  sql(`update public.shop_inventory set quantity_available=quantity_available-2 where product_id='${product}' and variant_id is null`);
}
function cancel(orderId) {
  return JSON.parse(sql(`select public.adishop_request_order_cancellation('${buyer}','${orderId}','customer_request','QA')`));
}
function sellerAction(groupId, action, requestId) {
  return JSON.parse(sql(`select public.adishop_transition_seller_order_group_scoped(
    '${seller}','${groupId}','${store}','${action}','Stock unavailable',
    '{"reasonCode":"out_of_stock","requestId":"${requestId}"}')`));
}
function expire(groupId) {
  return JSON.parse(sql(`select public.adishop_expire_seller_order_group('${groupId}','manual')`));
}
function denied(fn, code) {
  try { fn(); return false; } catch (error) { return String(error.stderr).includes(code); }
}

sql(`update public.shop_inventory set quantity_available=100, quantity_reserved=0 where product_id='${product}' and variant_id is null`);
{
  const { orderId, groupId } = fixture('5 minutes','paid');
  const ageSeconds = Number(sql(`select extract(epoch from (g.seller_response_required_at-o.created_at))::integer
    from public.shop_order_groups g join public.shop_orders o on o.id=g.order_id where g.id='${groupId}'`));
  check(ageSeconds === 86400, '24-hour deadline must start at placement');
  addSecondLine(groupId);
  const before = stock();
  check(cancel(orderId).status === 'cancelled', 'buyer cancellation');
  check(stock() - before === 3, 'buyer cancellation combined release');
  check(count('shop_refund_requests',orderId) === 1 &&
    sql(`select status from public.shop_refund_requests where order_id='${orderId}'`) === 'submitted', 'buyer refund pending');
  check(denied(() => cancel(orderId),'cancellation-not-eligible') && stock() - before === 3, 'buyer cancellation repeat');
}
{
  const { orderId } = fixture('30 minutes 1 second');
  check(denied(() => cancel(orderId),'cancellation-not-eligible'), '30-minute expiry');
  check(sql(`select fulfillment_status from public.shop_orders where id='${orderId}'`) === 'awaiting_seller_confirmation', 'late cancellation wrote state');
}
{
  const { orderId, groupId } = fixture();
  check(sellerAction(groupId,'accept','accept').sellerResponseStatus === 'accepted', 'seller acceptance');
  check(denied(() => cancel(orderId),'cancellation-not-eligible'), 'accepted cancellation');
  const before = stock();
  check(sellerAction(groupId,'reject','reject-after-accept').fulfillmentStatus === 'rejected', 'existing accepted-rejection policy');
  check(stock() - before === 1, 'accepted rejection release');
}
{
  const { orderId, groupId } = fixture('25 hours','paid');
  addSecondLine(groupId);
  const before = stock();
  check(expire(groupId).refundStatus === 'refund_pending', 'expiry refund state');
  check(stock() - before === 3, 'expiry combined release');
  check(expire(groupId).idempotent === true && stock() - before === 3, 'expiry duplicate');
  check(count('shop_refund_requests',orderId) === 1 &&
    sql(`select status from public.shop_refund_requests where order_id='${orderId}'`) === 'submitted', 'expiry refund pending');
}
{
  sql('update public.shop_seller_response_policy set default_hours=2 where singleton');
  const { orderId, groupId } = fixture('1 hour');
  const ageSeconds = Number(sql(`select extract(epoch from (g.seller_response_required_at-o.created_at))::integer
    from public.shop_order_groups g join public.shop_orders o on o.id=g.order_id where g.id='${groupId}'`));
  check(ageSeconds === 7200, 'configured 2-hour SLA');
  check(expire(groupId).code === 'seller-response-not-due', 'premature expiry');
  sql('update public.shop_seller_response_policy set default_hours=24 where singleton');
}
check(sql('select count(*) from public.shop_inventory where quantity_available<0 or quantity_reserved<0 or quantity_reserved>quantity_available') === '0', 'inventory bounds');
process.stdout.write(`phase9c7 integrated focused checks passed: ${passed}\n`);
