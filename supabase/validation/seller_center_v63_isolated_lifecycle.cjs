// Local PostgreSQL QA only. Run against a fresh disposable database.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { Client } = require('pg');

const root = path.resolve(__dirname, '..', '..');
const connectionString = process.env.V63_LOCAL_DATABASE_URL;
if (!connectionString || !/^postgres(?:ql)?:\/\/[^@]*@?127\.0\.0\.1:/.test(connectionString)) {
  throw new Error('V63_LOCAL_DATABASE_URL must point to 127.0.0.1');
}

const admin = '11111111-1111-4111-8111-111111111111';
const seller = '22222222-2222-4222-8222-222222222222';
const foreignSeller = '33333333-3333-4333-8333-333333333333';
const adminRole = '44444444-4444-4444-8444-444444444444';
const storeA = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const storeB = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const category = '00b13779-567f-42cd-88ca-190682262904'; // School Supplies, no required mappings.
let serial = 1;
let passed = 0;

async function connect() {
  const client = new Client({ connectionString, application_name: 'adsg-v63-local-qa' });
  await client.connect();
  return client;
}

function id() {
  return `dddddddd-dddd-4ddd-8ddd-${String(serial++).padStart(12, '0')}`;
}

async function check(name, run) {
  await run();
  passed++;
  process.stdout.write(`PASS ${name}\n`);
}

async function expectDenied(run, pattern) {
  let error;
  try { await run(); } catch (caught) { error = caught; }
  assert.ok(error, 'operation unexpectedly succeeded');
  assert.match(error.message, pattern);
}

async function product(client, options = {}) {
  const productId = id();
  await client.query(`insert into public.shop_products
    (id, store_id, category_id, title, description, price_minor, status, moderation_status,
      submitted_at, submitted_version)
    values ($1,$2,$3,'Controlled QA product','A controlled local product description.',2500,$4,$5,$6,$7)`,
    [productId, options.store || storeA, options.category || category,
      options.status || 'draft', options.moderation || 'pending',
      options.submittedAt || null, options.version || 0]);
  await client.query(`insert into public.shop_inventory
    (id, product_id, quantity_available, quantity_reserved) values ($1,$2,5,0)`, [id(), productId]);
  if (options.image !== false) {
    const imageId = id();
    const imageStore = options.imageStore || options.store || storeA;
    const storagePath = `local/${imageId}.jpg`;
    if (options.storageObject !== false) {
      await client.query(`insert into storage.objects(bucket_id,name) values ('shop-product-images',$1)`, [storagePath]);
    }
    await client.query(`insert into public.shop_product_images
      (id, product_id, store_id, image_url, bucket, storage_path, deleted_at)
      values ($1,$2,$3,$4,'shop-product-images',$5,$6)`,
      [imageId, productId, imageStore, `https://qa.invalid/${imageId}.jpg`,
        storagePath, options.softDeleted ? new Date() : null]);
  }
  return productId;
}

async function submitted(client) {
  const productId = await product(client);
  await client.query(`select public.adishop_seller_submit_product($1::uuid,$2::uuid,$3::text)`,
    [seller, productId, `submit-${productId}`]);
  const { rows } = await client.query(`select submitted_at::text as submitted_at, submitted_version, moderation_status
    from public.shop_products where id=$1`, [productId]);
  assert.equal(rows[0].moderation_status, 'submitted');
  assert.equal(rows[0].submitted_version, 1);
  return { productId, timestamp: rows[0].submitted_at };
}

async function review(client, productId, action, timestamp, actor = admin, requestId = id()) {
  return client.query(`select public.adishop_admin_review_product(
    $1::uuid,$2::uuid,$3::text,null::text,$4::text,''::text,$5::text,$6::timestamptz)`,
    [actor, productId, action,
      action === 'reject' || action === 'request_changes' ? 'Controlled QA reason.' : '',
      requestId, timestamp]);
}

async function counts(client, productId) {
  const { rows } = await client.query(`select
    (select count(*)::int from public.shop_product_moderation_reviews where product_id=$1) reviews,
    (select count(*)::int from public.shop_admin_audit_logs where target_id=$1) audits,
    (select moderation_status from public.shop_products where id=$1) moderation`, [productId]);
  return rows[0];
}

async function footprint(client, productId) {
  const { rows } = await client.query(`select
    (select to_jsonb(p) from public.shop_products p where id=$1) product,
    (select count(*)::int from public.shop_product_moderation_reviews where product_id=$1) reviews,
    (select count(*)::int from public.shop_admin_audit_logs where target_id=$1) audits,
    (select count(*)::int from public.shop_notifications where data->>'productId'=$1::text) notifications`,
    [productId]);
  return rows[0];
}

async function main() {
  const c = await connect();
  try {
    await c.query(fs.readFileSync(path.join(__dirname, 'seller_center_v63_isolated_schema.sql'), 'utf8'));
    await c.query(fs.readFileSync(path.join(__dirname, 'seller_center_v63_category_metadata_snapshot.sql'), 'utf8'));
    const metadata = await c.query(`select
      (select count(*)::int from public.shop_categories where is_leaf and seller_selectable and is_active) leaves,
      (select count(*)::int from public.shop_attribute_definitions) definitions,
      (select count(*)::int from public.shop_category_attributes) mappings`);
    assert.deepEqual(metadata.rows[0], { leaves: 185, definitions: 45, mappings: 832 });
    await c.query(fs.readFileSync(path.join(root, 'supabase', 'migrations',
      '20260814084026_adisgebeya_submit_variant_attribute_validation.sql'), 'utf8'));
    const proposal = fs.readFileSync(path.join(__dirname,
      'seller_center_v62_approval_security_proposal.sql'), 'utf8');
    const digest = require('node:crypto').createHash('sha256').update(proposal).digest('hex').toUpperCase();
    assert.equal(digest, '348003C303D5E17B662546A9AD11A9406E4F24ED8731A83756FD3D86F2D4F594');
    await c.query(proposal);
    process.stdout.write(`SQL_SHA256 ${digest}\n`);

    await c.query(`insert into public.shop_profiles(id) values ($1),($2),($3)`,
      [admin, seller, foreignSeller]);
    await c.query(`insert into public.shop_stores(id,owner_profile_id) values ($1,$2),($3,$4)`,
      [storeA, seller, storeB, foreignSeller]);
    await c.query(`insert into public.shop_roles(id,code) values ($1,'SHOP_ADMIN')`, [adminRole]);
    await c.query(`insert into public.shop_user_roles(profile_id,role_id) values ($1,$2)`,
      [admin, adminRole]);
    for (const permission of ['product.moderation.read','product.moderation.approve',
      'product.moderation.reject','product.moderation.request_changes']) {
      await c.query(`insert into public.shop_role_permissions(role_id,permission) values ($1,$2)`,
        [adminRole, permission]);
    }

    await check('review RPC is service-role-only', async () => {
      const { rows } = await c.query(`select
        has_function_privilege('anon', 'public.adishop_admin_review_product(uuid,uuid,text,text,text,text,text,timestamptz)', 'EXECUTE') anon,
        has_function_privilege('authenticated', 'public.adishop_admin_review_product(uuid,uuid,text,text,text,text,text,timestamptz)', 'EXECUTE') authenticated,
        has_function_privilege('service_role', 'public.adishop_admin_review_product(uuid,uuid,text,text,text,text,text,timestamptz)', 'EXECUTE') service_role`);
      assert.deepEqual(rows[0], { anon: false, authenticated: false, service_role: true });
    });
    await check('foreign seller cannot submit another store product', async () => {
      const p = await product(c);
      await expectDenied(() => c.query(`select public.adishop_seller_submit_product($1::uuid,$2::uuid,$3::text)`,
        [foreignSeller, p, id()]), /seller-product-not-found/);
      assert.equal((await counts(c, p)).moderation, 'pending');
    });
    await check('deployed V6 required category field blocks incomplete submission', async () => {
      const required = await c.query(`select ca.category_id from public.shop_category_attributes ca
        join public.shop_categories cat on cat.id=ca.category_id
        where ca.required and cat.is_leaf and cat.seller_selectable and cat.is_active limit 1`);
      assert.ok(required.rows[0], 'no required V6 mapping found');
      const p = await product(c, { category: required.rows[0].category_id });
      await expectDenied(() => c.query(`select public.adishop_seller_submit_product($1::uuid,$2::uuid,$3::text)`,
        [seller, p, id()]), /product-validation-failed/);
      assert.equal((await counts(c, p)).moderation, 'pending');
    });

    await check('pending draft cannot be approved', async () => {
      const p = await product(c);
      const before = await footprint(c, p);
      await expectDenied(() => review(c, p, 'approve', null), /stale-product-review/);
      assert.deepEqual(await footprint(c, p), before);
      assert.deepEqual(await counts(c, p), { reviews: 0, audits: 0, moderation: 'pending' });
    });
    await check('real seller submission and admin review/approval', async () => {
      const { productId: p, timestamp: ts } = await submitted(c);
      await review(c, p, 'start_review', ts);
      await review(c, p, 'approve', ts);
      const { rows } = await c.query(`select status,moderation_status,published_at
        from public.shop_products where id=$1`, [p]);
      assert.equal(rows[0].status, 'active');
      assert.equal(rows[0].moderation_status, 'approved');
      assert.ok(rows[0].published_at);
      assert.deepEqual(await counts(c, p), { reviews: 2, audits: 2, moderation: 'approved' });
    });
    await check('archived product cannot be approved', async () => {
      const p = await product(c, { status: 'archived', moderation: 'archived',
        submittedAt: new Date(), version: 1 });
      await expectDenied(() => review(c, p, 'approve', new Date()), /invalid-product-state|stale-product-review/);
    });
    await check('soft-deleted, missing, and foreign-store images do not count', async () => {
      for (const opts of [{ softDeleted: true }, { image: false },
        { imageStore: storeB }, { storageObject: false }]) {
        const ts = new Date();
        const p = await product(c, { ...opts, moderation: 'submitted', submittedAt: ts, version: 1 });
        const before = await footprint(c, p);
        await expectDenied(() => review(c, p, 'approve', ts), /missing-images/);
        assert.deepEqual(await footprint(c, p), before);
        assert.deepEqual(await counts(c, p), { reviews: 0, audits: 0, moderation: 'submitted' });
      }
    });
    await check('self-review and foreign actor denied', async () => {
      const { productId: p, timestamp: ts } = await submitted(c);
      await c.query(`insert into public.shop_user_roles(profile_id,role_id) values ($1,$2)`,
        [seller, adminRole]);
      const before = await footprint(c, p);
      await expectDenied(() => review(c, p, 'approve', ts, seller), /self-product-review-blocked/);
      await expectDenied(() => review(c, p, 'approve', ts, foreignSeller), /admin-forbidden/);
      assert.deepEqual(await footprint(c, p), before);
      assert.deepEqual(await counts(c, p), { reviews: 0, audits: 0, moderation: 'submitted' });
    });
    await check('stale timestamp and repeat decision denied', async () => {
      const { productId: p, timestamp: ts } = await submitted(c);
      const before = await footprint(c, p);
      await expectDenied(() => review(c, p, 'approve', new Date(0)), /stale-product-review/);
      assert.deepEqual(await footprint(c, p), before);
      await review(c, p, 'approve', ts);
      await expectDenied(() => review(c, p, 'approve', ts), /invalid-product-state/);
      assert.deepEqual(await counts(c, p), { reviews: 1, audits: 1, moderation: 'approved' });
    });
    await check('reject and request-changes transitions', async () => {
      const rejection = await submitted(c);
      const changes = await submitted(c);
      await review(c, rejection.productId, 'reject', rejection.timestamp);
      await review(c, changes.productId, 'request_changes', changes.timestamp);
      assert.equal((await counts(c, rejection.productId)).moderation, 'rejected');
      assert.equal((await counts(c, changes.productId)).moderation, 'changes_requested');
    });
    await check('image restoration is locked during review', async () => {
      const ts = new Date();
      const p = await product(c, { softDeleted: true, moderation: 'submitted', submittedAt: ts, version: 1 });
      await expectDenied(() => c.query(`update public.shop_product_images set deleted_at=null
        where product_id=$1`, [p]), /product-image-locked/);
    });

    const a = await connect();
    const b = await connect();
    try {
      await check('concurrent approve versus image deletion has one valid outcome', async () => {
        const { productId: p, timestamp: ts } = await submitted(c);
        const image = await c.query(`select id from public.shop_product_images where product_id=$1`, [p]);
        const results = await Promise.allSettled([
          review(a, p, 'approve', ts),
          b.query(`update public.shop_product_images set deleted_at=now() where id=$1`,
            [image.rows[0].id])
        ]);
        assert.equal(results[0].status, 'fulfilled');
        assert.equal(results[1].status, 'rejected');
        assert.match(results[1].reason.message, /product-image-locked/);
        assert.equal((await counts(c, p)).moderation, 'approved');
        const active = await c.query(`select count(*)::int n from public.shop_product_images
          where product_id=$1 and deleted_at is null`, [p]);
        assert.equal(active.rows[0].n, 1);
      });
      await check('competing admin decisions commit only once', async () => {
        const { productId: p, timestamp: ts } = await submitted(c);
        const results = await Promise.allSettled([
          review(a, p, 'approve', ts), review(b, p, 'reject', ts)
        ]);
        assert.equal(results.filter(x => x.status === 'fulfilled').length, 1);
        assert.equal(results.filter(x => x.status === 'rejected').length, 1);
        const totals = await counts(c, p);
        assert.equal(totals.reviews, 1);
        assert.equal(totals.audits, 1);
      });
      await check('concurrent duplicate request id is idempotent', async () => {
        const { productId: p, timestamp: ts } = await submitted(c);
        const requestId = id();
        const results = await Promise.allSettled([
          review(a, p, 'approve', ts, admin, requestId),
          review(b, p, 'approve', ts, admin, requestId)
        ]);
        assert.ok(results.every(x => x.status === 'fulfilled'));
        assert.deepEqual(await counts(c, p), { reviews: 1, audits: 1, moderation: 'approved' });
      });
    } finally {
      await a.end();
      await b.end();
    }
    if (process.env.V63_LOCAL_API_URL) {
      await check('Android buyer catalog HTTP query enforces published visibility', async () => {
        await c.query("notify pgrst, 'reload schema'");
        await new Promise(resolve => setTimeout(resolve, 1000));
        const source = fs.readFileSync(path.join(root, 'android', 'app', 'src', 'main',
          'java', 'com', 'adischat', 'app', 'feature', 'shop', 'data', 'ShopSupabaseApi.kt'), 'utf8');
        const match = source.match(/@GET\("(rest\/v1\/shop_products\?[^\"]+)"\)\s*suspend fun products\(/);
        assert.ok(match, 'Android products endpoint missing');
        const endpoint = match[1].replace(/^rest\/v1\//, '');
        const url = new URL(endpoint, process.env.V63_LOCAL_API_URL);
        const response = await fetch(url);
        if (!response.ok) throw new Error(`Buyer API HTTP ${response.status}: ${await response.text()}`);
        const products = await response.json();
        const visible = new Set(products.map(p => p.id));
        const expected = await c.query(`select id from public.shop_products
          where status='active' and moderation_status='approved' and deleted_at is null`);
        assert.deepEqual([...visible].sort(), expected.rows.map(p => p.id).sort());
        assert.ok(products.length > 0, 'approved product missing');
        assert.ok(products.every(p => p.shop_stores?.status === 'active'));
        const hidden = await c.query(`select id from public.shop_products
          where moderation_status='pending' limit 1`);
        const direct = await fetch(new URL(`shop_products?select=id&id=eq.${hidden.rows[0].id}`,
          process.env.V63_LOCAL_API_URL));
        assert.equal(direct.status, 200);
        assert.deepEqual(await direct.json(), []);
      });
    }
    process.stdout.write(`RESULT ${passed} isolated PostgreSQL checks passed\n`);
  } finally {
    await c.end();
  }
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
