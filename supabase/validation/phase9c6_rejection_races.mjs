// Disposable PostgreSQL 17.11 two-connection rejection races.
import assert from 'node:assert/strict';
import { execFile, execFileSync } from 'node:child_process';
import { promisify } from 'node:util';

const binary = 'C:/PostgreSQL/17/pgsql/bin/psql.exe';
const args = ['-X', '-v', 'ON_ERROR_STOP=1', '-A', '-t', '-h', '127.0.0.1', '-p', '55441', '-U', 'postgres', '-d', 'phase9c5'];
const execAsync = promisify(execFile);
const seller = '20000000-0000-4000-8000-000000000001';
const store = '10000000-0000-4000-8000-000000000001';
const product = '30000000-0000-4000-8000-000000000001';
function sql(query) {
  return execFileSync(binary, [...args, '-c', query], { encoding: 'utf8' }).trim().split('\n').at(-1);
}
async function barrier(key) {
  for (let i = 0; i < 100; i++) {
    if (sql(`select count(*) from pg_locks where locktype='advisory' and objid=${key}`) === '1') return;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error(`barrier ${key} not reached`);
}
const cases = [
  { first: 'accept', second: 'reject', due: 'future', result: 'rejected', payment: 'unpaid' },
  { first: 'reject', second: 'accept', due: 'future', result: 'rejected', payment: 'unpaid' },
  { first: 'expire', second: 'reject', due: 'past', result: 'expired', payment: 'unpaid' },
  { first: 'reject', second: 'expire', due: 'soon', result: 'rejected', payment: 'unpaid' },
  { first: 'reject', second: 'reject', due: 'future', result: 'rejected', payment: 'paid' },
];
let passed = 0;
for (const test of cases) {
  const { orderId, groupId } = JSON.parse(sql(`select public.phase9c3_new_order(interval '5 minutes','${test.payment}')`));
  if (test.due === 'past') {
    sql(`update public.shop_order_groups set seller_response_required_at=clock_timestamp()-interval '1 second' where id='${groupId}'`);
  } else if (test.due === 'soon') {
    sql(`update public.shop_order_groups set seller_response_required_at=clock_timestamp()+interval '2 seconds' where id='${groupId}'`);
  }
  const before = Number(sql(`select quantity_available from public.shop_inventory where product_id='${product}' and variant_id is null`));
  const actions = {
    accept: `select public.adishop_transition_seller_order_group_scoped('${seller}','${groupId}','${store}','accept',null,'{}')`,
    reject: `select public.adishop_transition_seller_order_group_scoped('${seller}','${groupId}','${store}','reject','Stock unavailable','{"reasonCode":"out_of_stock","requestId":"race-${passed}"}')`,
    expire: `select public.adishop_expire_seller_order_group('${groupId}','manual')`,
  };
  const key = 9600 + passed;
  const pause = test.due === 'soon' ? 2.2 : 0.5;
  const leader = execAsync(binary, [...args, '-c', `begin; select 1 from public.shop_order_groups where id='${groupId}' for update;
    select pg_advisory_xact_lock(${key}); ${actions[test.first]}; select pg_sleep(${pause}); commit;`], { encoding: 'utf8' });
  await barrier(key);
  const follower = execAsync(binary, [...args, '-c', actions[test.second]], { encoding: 'utf8' }).then(
    (value) => ({ ok: true, output: value.stdout }),
    (error) => ({ ok: false, output: error.stderr || String(error) }),
  );
  await leader;
  const second = await follower;
  const state = sql(`select fulfillment_status from public.shop_order_groups where id='${groupId}'`);
  const after = Number(sql(`select quantity_available from public.shop_inventory where product_id='${product}' and variant_id is null`));
  assert.equal(state, test.result);
  assert.equal(after - before, 1, 'rejection/expiry release exactly once');
  if (test.first === 'reject' && test.second === 'accept') {
    assert.equal(second.ok, false);
    assert.match(second.output, /invalid-transition/);
  } else if (test.first === 'expire' && test.second === 'reject') {
    assert.equal(second.ok, false);
    assert.match(second.output, /invalid-transition/);
  } else {
    assert.equal(second.ok, true);
  }
  if (test.payment === 'paid') {
    assert.equal(Number(sql(`select count(*) from public.shop_refund_requests where order_id='${orderId}'`)), 1);
    assert.equal(sql(`select status from public.shop_refund_requests where order_id='${orderId}'`), 'submitted');
    assert.equal(sql(`select status from public.shop_payments where order_id='${orderId}'`), 'paid');
  }
  passed++;
  process.stdout.write(`${test.first}/${test.second}: PASS, ${state}\n`);
}
{
  const a = JSON.parse(sql("select public.phase9c3_new_order(interval '5 minutes','unpaid')"));
  const b = JSON.parse(sql("select public.phase9c3_new_order(interval '5 minutes','unpaid')"));
  const before = Number(sql(`select quantity_available from public.shop_inventory where product_id='${product}' and variant_id is null`));
  const rejection = (groupId) => `select public.adishop_transition_seller_order_group_scoped(
    '${seller}','${groupId}','${store}','reject','Stock unavailable',
    '{"reasonCode":"out_of_stock","requestId":"parallel-${groupId}"}')`;
  await Promise.all([a.groupId,b.groupId].map((id) => execAsync(binary, [...args, '-c', rejection(id)], { encoding: 'utf8' })));
  const after = Number(sql(`select quantity_available from public.shop_inventory where product_id='${product}' and variant_id is null`));
  assert.equal(after - before, 2, 'parallel groups lost inventory release');
  passed++;
  process.stdout.write('parallel groups/same inventory: PASS, released 2/2\n');
}
process.stdout.write(`phase9c6 two-connection rejection races passed: ${passed}/${cases.length + 1}\n`);
