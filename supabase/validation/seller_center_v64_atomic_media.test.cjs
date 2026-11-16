// Disposable local PostgreSQL only. Never point this at production.
const assert = require('node:assert/strict');
const os = require('node:os');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const { Client } = require(path.join(os.tmpdir(), 'adsg-v63-node', 'node_modules', 'pg'));

const connectionString = process.env.V64_LOCAL_DATABASE_URL;
if (!connectionString || !/^postgres(?:ql)?:\/\/[^@]*@?127\.0\.0\.1:55463\/adsg_v64$/.test(connectionString)) {
  throw new Error('V64_LOCAL_DATABASE_URL must target the disposable adsg_v64 database on 127.0.0.1:55463');
}

const sellerA = '22222222-2222-4222-8222-222222222222';
const sellerB = '33333333-3333-4333-8333-333333333333';
const admin = '11111111-1111-4111-8111-111111111111';
const storeA = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const storeB = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const storeC = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
let passed = 0;

async function connect() {
  const client = new Client({ connectionString, application_name: 'adsg-v64-local-qa' });
  await client.connect();
  return client;
}

async function check(name, fn) {
  await fn();
  passed += 1;
  process.stdout.write(`PASS ${name}\n`);
}

async function denied(fn, pattern) {
  await assert.rejects(fn, pattern);
}

async function fixture(client, options = {}) {
  const productId = randomUUID();
  const imageId = randomUUID();
  const variantId = randomUUID();
  const storeId = options.storeId || storeA;
  await client.query(`insert into public.shop_products
    (id,store_id,title,price_minor,status,moderation_status,submitted_at,submitted_version)
    values ($1,$2,'Local media QA product',2500,$3,$4,$5,$6)`,
    [productId, storeId, options.status || 'draft', options.moderation || 'pending',
      options.submittedAt || null, options.submittedAt ? 1 : 0]);
  await client.query(`insert into public.shop_inventory(id,product_id,quantity_available,quantity_reserved)
    values ($1,$2,5,0)`, [randomUUID(), productId]);
  await client.query(`insert into storage.objects(bucket_id,name)
    values ('shop-product-images',$1)`, [`qa/${imageId}.jpg`]);
  await client.query(`insert into public.shop_product_images
    (id,product_id,store_id,seller_profile_id,image_url,bucket,storage_path,is_primary,display_order,deleted_at)
    values ($1,$2,$3,$4,$5,'shop-product-images',$6,true,0,$7)`,
    [imageId, productId, storeId, options.imageSeller || sellerA,
      `https://qa.invalid/${imageId}.jpg`, `qa/${imageId}.jpg`, options.softDeleted ? new Date() : null]);
  await client.query(`insert into public.shop_product_variants
    (id,product_id,price_minor,primary_image_id) values ($1,$2,2500,$3)`,
    [variantId, productId, imageId]);
  await client.query(`insert into public.shop_variant_media(variant_id,product_image_id,position)
    values ($1,$2,0)`, [variantId, imageId]);
  return { productId, imageId, variantId, storeId };
}

async function deleteImage(client, item, profileId = sellerA, selectedStoreId = item.storeId, imageId = item.imageId) {
  const result = await client.query(`select public.adishop_seller_delete_product_media_for_store(
    $1::uuid,$2::uuid,$3::uuid,$4::uuid) result`,
    [profileId, selectedStoreId, item.productId, imageId]);
  return result.rows[0].result;
}

async function footprint(client, item) {
  const { rows } = await client.query(`select
    (select deleted_at from public.shop_product_images where id=$1) deleted_at,
    (select is_primary from public.shop_product_images where id=$1) is_primary,
    (select count(*)::int from public.shop_variant_media where product_image_id=$1) links,
    (select primary_image_id from public.shop_product_variants where id=$2) variant_primary,
    (select count(*)::int from public.shop_audit_logs where entity_id=$3) audits,
    (select moderation_status from public.shop_products where id=$3) moderation_status`,
    [item.imageId, item.variantId, item.productId]);
  return rows[0];
}

async function main() {
  const client = await connect();
  try {
    await client.query(`insert into public.shop_profiles(id) values ($1),($2),($3)
      on conflict do nothing`, [admin, sellerA, sellerB]);
    await client.query(`insert into public.shop_stores(id,owner_profile_id,name,slug)
      values ($1,$2,'QA Store A','qa-store-a'),($3,$2,'QA Store B','qa-store-b')
      on conflict do nothing`, [storeA, sellerA, storeB]);
    await client.query(`insert into public.shop_stores(id,owner_profile_id,name,slug)
      values ($1,$2,'QA Store C','qa-store-c') on conflict do nothing`, [storeC, sellerB]);
    await client.query(`insert into public.shop_roles(id,code) values ($1,'SHOP_SUPER_ADMIN')
      on conflict do nothing`, ['44444444-4444-4444-8444-444444444444']);
    await client.query(`insert into public.shop_user_roles(profile_id,role_id) values ($1,$2)
      on conflict do nothing`, [admin, '44444444-4444-4444-8444-444444444444']);
    await client.query(`insert into public.shop_role_permissions(role_id,permission)
      values ($1,'product.moderation.approve') on conflict do nothing`,
      ['44444444-4444-4444-8444-444444444444']);

    await check('service-role-only RPC grant', async () => {
      const { rows } = await client.query(`select
        has_function_privilege('anon',p.oid,'EXECUTE') anon,
        has_function_privilege('authenticated',p.oid,'EXECUTE') authenticated,
        has_function_privilege('service_role',p.oid,'EXECUTE') service_role
        from pg_proc p where p.oid='public.adishop_seller_delete_product_media_for_store(uuid,uuid,uuid,uuid)'::regprocedure`);
      assert.deepEqual(rows[0], { anon: false, authenticated: false, service_role: true });
    });

    await check('public image RLS hides deleted rows but retains active images', async () => {
      const item = await fixture(client, {
        status: 'active', moderation: 'approved', softDeleted: true,
      });
      const activeId = randomUUID();
      await client.query(`insert into public.shop_product_images
        (id,product_id,store_id,seller_profile_id,image_url,display_order)
        values ($1,$2,$3,$4,$5,1)`,
        [activeId, item.productId, storeA, sellerA, `https://qa.invalid/${activeId}.jpg`]);
      await client.query('set role anon');
      try {
        const { rows } = await client.query(`select id from public.shop_product_images
          where product_id=$1 order by display_order`, [item.productId]);
        assert.deepEqual(rows.map(row => row.id), [activeId]);
      } finally {
        await client.query('reset role');
      }
    });

    await check('authorized deletion, variant cleanup and deterministic cover', async () => {
      const item = await fixture(client);
      const nextId = randomUUID();
      await client.query(`insert into public.shop_product_images
        (id,product_id,store_id,seller_profile_id,image_url,display_order)
        values ($1,$2,$3,$4,$5,1)`,
        [nextId, item.productId, storeA, sellerA, `https://qa.invalid/${nextId}.jpg`]);
      const result = await deleteImage(client, item);
      assert.equal(result.storageBucket, 'shop-product-images');
      assert.equal(result.storagePath, `qa/${item.imageId}.jpg`);
      assert.deepEqual(result.sellerProduct.images.map(image => image.id), [nextId]);
      const after = await footprint(client, item);
      assert.ok(after.deleted_at);
      assert.equal(after.links, 0);
      assert.equal(after.variant_primary, null);
      assert.equal(after.audits, 1);
      const cover = await client.query(`select is_primary from public.shop_product_images where id=$1`, [nextId]);
      assert.equal(cover.rows[0].is_primary, true);
      const storage = await client.query(`select count(*)::int n from storage.objects where name=$1`, [`qa/${item.imageId}.jpg`]);
      assert.equal(storage.rows[0].n, 1, 'storage cleanup is deliberately outside the DB transaction');
    });

    await check('wrong selected store leaves all rows unchanged', async () => {
      const item = await fixture(client);
      const before = await footprint(client, item);
      await denied(() => deleteImage(client, item, sellerA, storeB), /seller-product-media-not-found/);
      assert.deepEqual(await footprint(client, item), before);
    });
    await check('foreign seller leaves all rows unchanged', async () => {
      const item = await fixture(client);
      const before = await footprint(client, item);
      await denied(() => deleteImage(client, item, sellerB), /seller-product-media-not-found/);
      assert.deepEqual(await footprint(client, item), before);
    });
    await check('soft-deleted image is denied without mutation', async () => {
      const item = await fixture(client, { softDeleted: true });
      const before = await footprint(client, item);
      await denied(() => deleteImage(client, item), /seller-product-media-not-found/);
      assert.deepEqual(await footprint(client, item), before);
    });
    await check('another product image is denied without mutation', async () => {
      const item = await fixture(client);
      const other = await fixture(client);
      const before = await footprint(client, item);
      await denied(() => deleteImage(client, item, sellerA, storeA, other.imageId), /seller-product-media-not-found/);
      assert.deepEqual(await footprint(client, item), before);
    });
    await check('multi-store seller must select a store', async () => {
      const item = await fixture(client);
      const before = await footprint(client, item);
      await denied(() => deleteImage(client, item, sellerA, null), /seller-store-selection-required/);
      assert.deepEqual(await footprint(client, item), before);
    });
    await check('single-store legacy request still works', async () => {
      const item = await fixture(client, { storeId: storeC, imageSeller: sellerB });
      const result = await deleteImage(client, item, sellerB, null);
      assert.equal(result.sellerProduct.store_id, storeC);
      assert.ok((await footprint(client, item)).deleted_at);
    });
    await check('repeated deletion is denied and audited once', async () => {
      const item = await fixture(client);
      await deleteImage(client, item);
      const afterFirst = await footprint(client, item);
      await denied(() => deleteImage(client, item), /seller-product-media-not-found/);
      assert.deepEqual(await footprint(client, item), afterFirst);
      assert.equal(afterFirst.audits, 1);
    });
    await check('failure after image update rolls back image and variant mutations', async () => {
      const item = await fixture(client);
      await client.query(`create or replace function public.v64_reject_variant_delete()
        returns trigger language plpgsql as $$
        begin
          if old.product_image_id::text = current_setting('v64.fail_image_id', true) then
            raise exception 'v64-forced-variant-failure';
          end if;
          return old;
        end $$`);
      await client.query(`create trigger v64_reject_variant_delete before delete on public.shop_variant_media
        for each row execute function public.v64_reject_variant_delete()`);
      const before = await footprint(client, item);
      await client.query(`select set_config('v64.fail_image_id',$1,false)`, [item.imageId]);
      await denied(() => deleteImage(client, item), /v64-forced-variant-failure/);
      assert.deepEqual(await footprint(client, item), before);
      await client.query(`select set_config('v64.fail_image_id','',false)`);
      await client.query('drop trigger v64_reject_variant_delete on public.shop_variant_media');
      await client.query('drop function public.v64_reject_variant_delete()');
    });

    await check('audit failure rolls back every media and variant mutation', async () => {
      const item = await fixture(client);
      await client.query(`create or replace function public.v64_reject_media_audit()
        returns trigger language plpgsql as $$
        begin
          if new.entity_id::text = current_setting('v64.fail_product_id', true) then
            raise exception 'v64-forced-audit-failure';
          end if;
          return new;
        end $$`);
      await client.query(`create trigger v64_reject_media_audit before insert on public.shop_audit_logs
        for each row execute function public.v64_reject_media_audit()`);
      const before = await footprint(client, item);
      await client.query(`select set_config('v64.fail_product_id',$1,false)`, [item.productId]);
      await denied(() => deleteImage(client, item), /v64-forced-audit-failure/);
      assert.deepEqual(await footprint(client, item), before);
      await client.query(`select set_config('v64.fail_product_id','',false)`);
      await client.query('drop trigger v64_reject_media_audit on public.shop_audit_logs');
      await client.query('drop function public.v64_reject_media_audit()');
    });

    await check('approval wins the product lock; deletion cannot change approved image', async () => {
      const timestamp = new Date();
      const item = await fixture(client, { moderation: 'submitted', submittedAt: timestamp });
      const approving = await connect();
      const deleting = await connect();
      try {
        await approving.query('begin');
        await approving.query('select id from public.shop_products where id=$1 for update', [item.productId]);
        const deletion = deleteImage(deleting, item).then(
          () => ({ ok: true }), error => ({ ok: false, message: error.message }));
        await new Promise(resolve => setTimeout(resolve, 100));
        await approving.query(`select public.adishop_admin_review_product(
          $1::uuid,$2::uuid,'approve',null::text,''::text,''::text,$3::text,$4::timestamptz)`,
          [admin, item.productId, randomUUID(), timestamp]);
        await approving.query('commit');
        const rejected = await deletion;
        assert.equal(rejected.ok, false);
        assert.match(rejected.message, /seller-product-locked/);
        const after = await footprint(client, item);
        assert.equal(after.moderation_status, 'approved');
        assert.equal(after.deleted_at, null);
        assert.equal(after.links, 1);
        assert.equal(after.audits, 0);
      } finally {
        await approving.query('rollback').catch(() => {});
        await approving.end();
        await deleting.end();
      }
    });

    await check('deletion wins the product lock; later approval cannot use removed image', async () => {
      const item = await fixture(client);
      const deleting = await connect();
      const reviewing = await connect();
      try {
        await deleting.query('begin');
        await deleteImage(deleting, item);
        const approval = (async () => {
          await reviewing.query('begin');
          await reviewing.query(`update public.shop_products set moderation_status='submitted',
            submitted_at=now(), submitted_version=1 where id=$1`, [item.productId]);
          const { rows } = await reviewing.query('select submitted_at::text as submitted_at from public.shop_products where id=$1', [item.productId]);
          await reviewing.query(`select public.adishop_admin_review_product(
            $1::uuid,$2::uuid,'approve',null::text,''::text,''::text,$3::text,$4::timestamptz)`,
            [admin, item.productId, randomUUID(), rows[0].submitted_at]);
          await reviewing.query('commit');
        })().then(() => ({ ok: true }), error => ({ ok: false, message: error.message }));
        await new Promise(resolve => setTimeout(resolve, 100));
        await deleting.query('commit');
        const rejected = await approval;
        assert.equal(rejected.ok, false);
        assert.match(rejected.message, /missing-images/);
        await reviewing.query('rollback');
        const after = await footprint(client, item);
        assert.ok(after.deleted_at);
        assert.equal(after.audits, 1);
        assert.equal(after.moderation_status, 'pending');
      } finally {
        await deleting.query('rollback').catch(() => {});
        await reviewing.query('rollback').catch(() => {});
        await deleting.end();
        await reviewing.end();
      }
    });
  } finally {
    await client.end();
  }
  process.stdout.write(`TOTAL ${passed} passed\n`);
}

main().catch(error => { process.stderr.write(`${error.stack}\n`); process.exitCode = 1; });
