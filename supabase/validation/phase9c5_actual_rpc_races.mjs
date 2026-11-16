// Two independent PostgreSQL 17.11 connections against exact deployed transition RPCs,
// the local cancellation proposal, and the local corrected expiry proposal.
import assert from 'node:assert/strict';
import { execFile, execFileSync } from 'node:child_process';
import { promisify } from 'node:util';

const binary = 'C:/PostgreSQL/17/pgsql/bin/psql.exe';
const args = ['-X', '-v', 'ON_ERROR_STOP=1', '-A', '-t', '-h', '127.0.0.1', '-p', '55441', '-U', 'postgres', '-d', 'phase9c5'];
const execAsync = promisify(execFile);
const buyer = '40000000-0000-4000-8000-000000000001';
const seller = '20000000-0000-4000-8000-000000000001';
const store = '10000000-0000-4000-8000-000000000001';
const product = '30000000-0000-4000-8000-000000000001';

function sql(query) {
  return execFileSync(binary, [...args, '-c', query], { encoding: 'utf8' }).trim().split('\n').at(-1);
}
async function asyncSql(query) {
  return execAsync(binary, [...args, '-c', query], { encoding: 'utf8' });
}
async function barrier(key) {
  for (let i = 0; i < 100; i++) {
    if (sql(`select count(*) from pg_locks where locktype='advisory' and objid=${key}`) === '1') return;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error(`leader barrier ${key} not reached`);
}

let passed = 0;
const cases = [
  ['accept', 'cancel', false, 'accepted', false, 'unpaid'],
  ['cancel', 'accept', false, 'cancelled', true, 'unpaid'],
  ['expire', 'accept', true, 'expired', true, 'unpaid'],
  ['accept', 'expire', false, 'accepted', false, 'unpaid'],
  ['cancel', 'expire', true, 'cancelled', true, 'unpaid'],
  ['expire', 'cancel', true, 'expired', true, 'unpaid'],
  ['expire', 'expire', true, 'expired', true, 'unpaid'],
  ['cancel', 'expire', true, 'cancelled', true, 'paid'],
];
for (const [first, second, due, expectedStatus, released, payment] of cases) {
  const { orderId, groupId } = JSON.parse(sql(`select public.phase9c3_new_order(interval '5 minutes','${payment}')`));
  if (due) sql(`update public.shop_order_groups set seller_response_required_at=clock_timestamp()-interval '1 second' where id='${groupId}'`);
  const before = Number(sql(`select quantity_available from public.shop_inventory where product_id='${product}' and variant_id is null`));
  const actions = {
    accept: `select public.adishop_transition_seller_order_group_scoped('${seller}','${groupId}','${store}','accept',null,'{}')`,
    cancel: `select public.adishop_request_order_cancellation('${buyer}','${orderId}','customer_request','QA race')`,
    expire: `select public.adishop_expire_seller_order_group('${groupId}','manual')`,
  };
  const key = 9400 + passed;
  const leader = asyncSql(`begin; select 1 from public.shop_order_groups where id='${groupId}' for update;
    select pg_advisory_xact_lock(${key}); ${actions[first]}; select pg_sleep(0.6); commit;`);
  await barrier(key);
  const follower = asyncSql(actions[second]).then(
    (result) => ({ ok: true, output: result.stdout }),
    (error) => ({ ok: false, output: error.stderr || String(error) }),
  );
  await leader;
  const result = await follower;
  const status = sql(`select fulfillment_status from public.shop_order_groups where id='${groupId}'`);
  const after = Number(sql(`select quantity_available from public.shop_inventory where product_id='${product}' and variant_id is null`));
  assert.equal(status, expectedStatus, `${first}/${second} final state`);
  assert.equal(after - before, released ? 1 : 0, `${first}/${second} inventory released once`);
  if (first === 'accept' && second === 'cancel' || first === 'expire' && second === 'cancel') {
    assert.equal(result.ok, false, `${first}/${second} denial`);
    assert.match(result.output, /cancellation-not-eligible/);
  } else if (first === 'cancel' && second === 'accept' || first === 'expire' && second === 'accept') {
    assert.equal(result.ok, false, `${first}/${second} denial`);
    assert.match(result.output, /invalid-transition/);
  } else {
    assert.equal(result.ok, true, `${first}/${second} idempotent result`);
  }
  if (payment === 'paid') {
    assert.equal(Number(sql(`select count(*) from public.shop_refund_requests where order_id='${orderId}'`)), 1);
    assert.equal(sql(`select status from public.shop_refund_requests where order_id='${orderId}'`), 'submitted');
    assert.equal(sql(`select status from public.shop_payments where order_id='${orderId}'`), 'paid');
  }
  passed++;
  process.stdout.write(`${first}/${second} (${payment}): PASS, ${status}\n`);
}
process.stdout.write(`phase9c5 two-connection races passed: ${passed}/${cases.length}\n`);
