// Disposable loopback fixture only. Never point this at a hosted project.
import { createHmac } from 'node:crypto';
import assert from 'node:assert/strict';

const base = 'http://127.0.0.1:55450';
const productId = '93000000-0000-4000-8000-000000000001';
let passed = 0;
let failed = 0;

function localJwt() {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const head = encode({ alg: 'HS256', typ: 'JWT' });
  const body = encode({ role: 'authenticated', exp: Math.floor(Date.now() / 1000) + 300 });
  const signed = `${head}.${body}`;
  return `${signed}.${createHmac('sha256', 'phase8b3-postgrest-local-only-secret-2026').update(signed).digest('base64url')}`;
}

async function request(path, body, token) {
  const response = await fetch(`${base}/${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const text = await response.text();
  return { status: response.status, data: text ? JSON.parse(text) : null };
}

async function check(label, test) {
  try {
    await test();
    passed++;
    console.log(`PASS ${label}`);
  } catch (error) {
    failed++;
    console.error(`FAIL ${label}: ${error.message}`);
  }
}

const page = await request('rpc/adishop_qa_catalog_page', { p_limit: 24 });
await check('reviewed page available to anonymous', () => {
  assert.equal(page.status, 200);
  assert.equal(page.data.length, 1);
  assert.equal(page.data[0].id, productId);
});
await check('no private product or media text in page', () => {
  assert.equal(page.data[0].shop_product_images.length, 0);
  for (const field of ['description', 'sku', 'image_url', 'storage_path', 'owner_profile_id']) {
    assert.equal(field in page.data[0], false);
  }
});
await check('category filter and pagination', async () => {
  const matching = await request('rpc/adishop_qa_catalog_page', { p_category_slug: 'qa-laptops', p_limit: 1 });
  const next = await request('rpc/adishop_qa_catalog_page', { p_category_slug: 'qa-laptops', p_limit: 1, p_offset: 1 });
  assert.equal(matching.status, 200);
  assert.equal(matching.data.length, 1);
  assert.equal(next.status, 200);
  assert.equal(next.data.length, 0);
});
await check('reviewed title search', async () => {
  const found = await request('rpc/adishop_qa_catalog_page', { p_term: 'Laptop' });
  assert.equal(found.status, 200);
  assert.equal(found.data.length, 1);
});
await check('detail keeps reviewed variant IDs, prices, stock', async () => {
  const detail = await request('rpc/adishop_qa_product_detail', { p_product_id: productId });
  assert.equal(detail.status, 200);
  assert.equal(detail.data.variants.length, 2);
  assert.deepEqual(detail.data.variants.map((v) => v.id).sort(), [
    '94000000-0000-4000-8000-000000000001',
    '94000000-0000-4000-8000-000000000002',
  ]);
  assert.deepEqual(detail.data.variants.map((v) => v.availableQuantity), [3, 3]);
  assert.deepEqual(detail.data.variants.map((v) => v.price_minor), [5700000, 6300000]);
});
await check('unknown detail withheld', async () => {
  const result = await request('rpc/adishop_qa_product_detail', { p_product_id: '93000000-0000-4000-8000-000000000099' });
  assert.equal(result.status, 200);
  assert.equal(result.data, null);
});
await check('invalid page size rejected', async () => {
  const result = await request('rpc/adishop_qa_catalog_page', { p_limit: 101 });
  assert.equal(result.status, 400);
});
await check('raw product denied to anonymous', async () => {
  const result = await request('shop_products?select=id,title,description');
  assert.ok([401, 403].includes(result.status));
});
await check('raw store denied to synthetic authenticated role', async () => {
  const result = await request('shop_stores?select=id,name,description', undefined, localJwt());
  assert.ok([401, 403].includes(result.status));
});
await check('category image URL column denied', async () => {
  const result = await request('shop_categories?select=id,image_url');
  assert.ok([401, 403].includes(result.status));
});
await check('reviewed store view readable', async () => {
  const result = await request('shop_qa_public_stores?select=id,name,city');
  assert.equal(result.status, 200);
  assert.equal(result.data.length, 1);
});
await check('cart table denied to anonymous', async () => {
  const result = await request('shop_carts?select=*');
  assert.ok([401, 403].includes(result.status));
});

console.log(`TOTAL ${passed} PASS ${failed} FAIL`);
if (failed) process.exitCode = 1;
