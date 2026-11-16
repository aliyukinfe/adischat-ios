// Disposable PostgreSQL 17 fixture only. Synthetic identities and orders.
import assert from 'node:assert/strict';
import { execFile, execFileSync } from 'node:child_process';
import { promisify } from 'node:util';

const runAsync = promisify(execFile);
const binary = 'C:/PostgreSQL/17/pgsql/bin/psql.exe';
const args = ['-X', '-v', 'ON_ERROR_STOP=1', '-A', '-t', '-h', '127.0.0.1', '-p', '55441', '-U', 'postgres', '-d', 'phase9c3'];
const buyer = '40000000-0000-4000-8000-000000000001';
const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;

function sql(query) {
  return execFileSync(binary, [...args, '-c', query], { encoding: 'utf8' }).trim();
}

async function asyncSql(query) {
  return runAsync(binary, [...args, '-c', query], { encoding: 'utf8' });
}

async function barrier(key) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (sql(`select count(*) from pg_locks where locktype='advisory' and objid=${key}`) === '1') return;
    await new Promise((resolve) => setTimeout(resolve, 15));
  }
  throw new Error(`barrier ${key} never acquired`);
}

function fixture() {
  const value = JSON.parse(sql("select public.phase9c3_new_order(interval '5 minutes')"));
  assert.match(value.orderId, uuid);
  assert.match(value.groupId, uuid);
  return value;
}

function state(orderId, groupId) {
  return JSON.parse(sql(`select json_build_object(
    'order', (select fulfillment_status from shop_orders where id='${orderId}'),
    'group', (select fulfillment_status from shop_order_groups where id='${groupId}'),
    'cancellations', (select count(*) from shop_order_cancellations where order_id='${orderId}'),
    'released', (select inventory_released_at is not null from shop_order_groups where id='${groupId}')
  )`));
}

let passed = 0;
for (const first of ['cancel', 'accept', 'duplicate-cancel']) {
  const { orderId, groupId } = fixture();
  const key = 9100 + passed;
  const cancel = `select public.adishop_request_order_cancellation('${buyer}','${orderId}','customer_request','race')`;
  const accept = `select public.phase9c3_accept('${groupId}')`;
  const firstAction = first === 'accept' ? accept : cancel;
  const secondAction = first === 'accept' ? cancel : first === 'cancel' ? accept : cancel;
  const leader = asyncSql(`begin; select 1 from shop_order_groups where id='${groupId}' for update;
    select pg_advisory_xact_lock(${key}); ${firstAction}; select pg_sleep(1.5); commit;`);
  await barrier(key);
  const follower = asyncSql(secondAction).then(
    (value) => ({ ok: true, output: value.stdout }),
    (error) => ({ ok: false, output: error.stderr || String(error) }),
  );
  await leader;
  const second = await follower;
  const final = state(orderId, groupId);
  if (first === 'accept') {
    assert.equal(second.ok, false);
    assert.match(second.output, /cancellation-not-eligible/);
    assert.equal(final.group, 'accepted');
    assert.equal(final.cancellations, 0);
    assert.equal(final.released, false);
  } else if (first === 'cancel') {
    assert.equal(second.ok, true);
    assert.match(second.output, /denied/);
    assert.equal(final.group, 'cancelled');
    assert.equal(final.cancellations, 1);
    assert.equal(final.released, true);
  } else {
    assert.equal(second.ok, false);
    assert.match(second.output, /cancellation-not-eligible/);
    assert.equal(final.cancellations, 1);
    assert.equal(final.released, true);
  }
  passed++;
  process.stdout.write(`${first}: PASS, order=${final.order}, group=${final.group}\n`);
}
process.stdout.write(`phase9c3 two-connection races passed: ${passed}\n`);
