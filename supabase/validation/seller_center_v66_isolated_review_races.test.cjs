// Disposable local PostgreSQL only. Never point this test at production.
const assert = require('node:assert/strict');
const os = require('node:os');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const { Client } = require(path.join(os.tmpdir(), 'adsg-v63-node', 'node_modules', 'pg'));

const connectionString = process.env.V66_LOCAL_DATABASE_URL;
if (connectionString !== 'postgres://postgres@127.0.0.1:55463/adsg_v64') {
  throw new Error('V66_LOCAL_DATABASE_URL must target the disposable local adsg_v64 database');
}

const seller = '22222222-2222-4222-8222-222222222222';
const adminA = '11111111-1111-4111-8111-111111111111';
const adminB = '66666666-6666-4666-8666-666666666666';
const store = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const adminRole = '44444444-4444-4444-8444-444444444444';

async function connect() {
  const client = new Client({ connectionString, application_name: 'adsg-v66-local-review-race' });
  await client.connect();
  await client.query("set lock_timeout='2s'");
  await client.query("set statement_timeout='8s'");
  return client;
}

async function fixture(client) {
  const productId = randomUUID();
  const submittedAt = new Date();
  await client.query(`insert into public.shop_products
    (id,store_id,title,price_minor,status,moderation_status,submitted_at,submitted_version)
    values ($1,$2,'V66 isolated review race',2500,'draft','submitted',$3,1)`,
    [productId, store, submittedAt]);
  await client.query(`insert into public.shop_inventory(id,product_id,quantity_available,quantity_reserved)
    values ($1,$2,5,0)`, [randomUUID(), productId]);
  await client.query(`insert into public.shop_product_images
    (id,product_id,store_id,seller_profile_id,image_url,is_primary,display_order)
    values ($1,$2,$3,$4,$5,true,0)`,
    [randomUUID(), productId, store, seller, 'https://qa.invalid/review-race.jpg']);
  return { productId, submittedAt };
}

async function review(client, item, actor, action) {
  const reason = action === 'approve' ? null : 'Isolated QA decision';
  return client.query(`select public.adishop_admin_review_product(
    $1::uuid,$2::uuid,$3::text,null::text,$4::text,''::text,$5::text,$6::timestamptz)`,
    [actor, item.productId, action, reason, randomUUID(), item.submittedAt]);
}

async function runRace(primaryAction, competingAction) {
  const setup = await connect();
  const primary = await connect();
  const competing = await connect();
  try {
    const item = await fixture(setup);
    await primary.query('begin');
    await primary.query('select id from public.shop_products where id=$1 for update', [item.productId]);
    const waiting = review(competing, item, adminB, competingAction).then(
      () => ({ accepted: true }),
      error => ({ accepted: false, message: error.message }),
    );
    await new Promise(resolve => setTimeout(resolve, 100));
    await review(primary, item, adminA, primaryAction);
    await primary.query('commit');
    const loser = await waiting;
    assert.equal(loser.accepted, false, 'competing review must lose after the first commit');
    assert.match(loser.message, /invalid-product-state|stale-product-review/);
    const { rows } = await setup.query(`select
      (select moderation_status from public.shop_products where id=$1) status,
      (select count(*)::int from public.shop_product_moderation_reviews where product_id=$1) reviews,
      (select count(*)::int from public.shop_admin_audit_logs where target_id=$1) audits,
      (select count(*)::int from public.shop_notifications where data->>'productId'=$1::text) notifications`,
      [item.productId]);
    const expectedStatus = primaryAction === 'approve' ? 'approved' : primaryAction;
    assert.deepEqual(rows[0], { status: expectedStatus, reviews: 1, audits: 1, notifications: 1 });
    process.stdout.write(`PASS ${primaryAction} beats ${competingAction}: one decision and one audit\n`);
  } finally {
    await primary.query('rollback').catch(() => {});
    await primary.end();
    await competing.end();
    await setup.end();
  }
}

async function main() {
  const setup = await connect();
  try {
    await setup.query('insert into public.shop_profiles(id) values ($1),($2),($3) on conflict do nothing',
      [seller, adminA, adminB]);
    await setup.query(`insert into public.shop_stores(id,owner_profile_id,name,slug)
      values ($1,$2,'V66 QA Store','v66-qa-store') on conflict do nothing`, [store, seller]);
    await setup.query(`insert into public.shop_roles(id,code) values ($1,'SHOP_SUPER_ADMIN') on conflict do nothing`, [adminRole]);
    await setup.query('insert into public.shop_user_roles(profile_id,role_id) values ($1,$3),($2,$3) on conflict do nothing',
      [adminA, adminB, adminRole]);
    for (const permission of ['product.moderation.approve', 'product.moderation.reject', 'product.moderation.request_changes']) {
      await setup.query(`insert into public.shop_role_permissions(role_id,permission)
        values ($1,$2) on conflict do nothing`, [adminRole, permission]);
    }
  } finally {
    await setup.end();
  }
  await runRace('approve', 'reject');
  await runRace('approve', 'request_changes');
  await runRace('approve', 'approve');
  process.stdout.write('TOTAL 3 passed\n');
}

main().catch(error => { process.stderr.write(`${error.stack}\n`); process.exitCode = 1; });
