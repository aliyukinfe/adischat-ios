// Phase AG-R3: Secure Product Image Review & Moderation Contracts Verification
// Target: https://opkqyvttbyezjbqwkvho.supabase.co (AdisGebeya Staging)
// Supabase Free Tier - $0.00 spend - Strictly zero production impact

import assert from 'node:assert/strict';

const baseUrl = 'https://opkqyvttbyezjbqwkvho.supabase.co';
const apikey = 'sb_publishable_CXGbIKQ34YbyDHaChnPUTA_q9vDP7YO';

let passed = 0;
let failed = 0;

async function check(label, fn) {
  try {
    await fn();
    passed++;
    console.log(`PASS ${label}`);
  } catch (err) {
    failed++;
    console.error(`FAIL ${label}: ${err.message}`);
    throw err;
  }
}

async function httpGet(path) {
  const res = await fetch(`${baseUrl}/${path}`, {
    headers: { apikey, 'content-type': 'application/json' },
  });
  let data;
  try {
    data = await res.json();
  } catch {
    data = await res.text();
  }
  return { status: res.status, data };
}

async function httpPost(path, body) {
  const res = await fetch(`${baseUrl}/${path}`, {
    method: 'POST',
    headers: { apikey, 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  let data;
  try {
    data = await res.json();
  } catch {
    data = await res.text();
  }
  return { status: res.status, data };
}

console.log('--- Starting Phase AG-R3 Staging Moderation & Security Contract Tests ---');

// 1. Private Storage Security: shop-product-images is private
await check('storage: public GET to private shop-product-images bucket is blocked', async () => {
  const path = 'storage/v1/object/public/shop-product-images/seller-products/90000000-0000-4000-8000-000000000002/92000000-0000-4000-8000-000000000001/93000000-0000-4000-8000-000000000013/powerbank.png';
  const { status, data } = await httpGet(path);
  // Private buckets reject public requests with 400 or 404
  assert.ok([400, 404].includes(status), `Expected 400 or 404 for private storage, got ${status}: ${JSON.stringify(data)}`);
});

// 2. Private Storage Security: shop-store-assets is private
await check('storage: public GET to private shop-store-assets bucket is blocked', async () => {
  const path = 'storage/v1/object/public/shop-store-assets/store-assets/92000000-0000-4000-8000-000000000001/logo.png';
  const { status, data } = await httpGet(path);
  assert.ok([400, 404].includes(status), `Expected 400 or 404 for private storage, got ${status}`);
});

// 3. Moderation RPC Protection: adishop_qa_moderation_queue cannot be called by public/anon
await check('rpc-guard: adishop_qa_moderation_queue rejects unprivileged/anonymous execution', async () => {
  const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_moderation_queue', {
    p_session_jti: 'unauthorized-token',
  });
  // Service-role only RPCs return 401, 403 or 404 (function not found for role)
  assert.ok([401, 403, 404].includes(status), `Expected 401/403/404, got ${status}: ${JSON.stringify(data)}`);
});

// 4. Moderation RPC Protection: adishop_qa_moderation_preview cannot be called by public/anon
await check('rpc-guard: adishop_qa_moderation_preview rejects unprivileged/anonymous execution', async () => {
  const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_moderation_preview', {
    p_session_jti: 'unauthorized-token',
    p_kind: 'image',
    p_id: '98000000-0000-4000-8000-000000000013',
  });
  assert.ok([401, 403, 404].includes(status), `Expected 401/403/404, got ${status}`);
});

// 5. Moderation RPC Protection: adishop_qa_review_image cannot be called by public/anon
await check('rpc-guard: adishop_qa_review_image rejects unprivileged/anonymous execution', async () => {
  const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_review_image', {
    p_session_jti: 'unauthorized-token',
    p_image_id: '98000000-0000-4000-8000-000000000013',
    p_expected_revision: 1,
    p_decision: 'approve',
  });
  assert.ok([401, 403, 404].includes(status), `Expected 401/403/404, got ${status}`);
});

// 6. Moderation RPC Protection: adishop_qa_reviewer_media_locator cannot be called by public/anon
await check('rpc-guard: adishop_qa_reviewer_media_locator rejects unprivileged/anonymous execution', async () => {
  const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_reviewer_media_locator', {
    p_session_jti: 'unauthorized-token',
    p_image_id: '98000000-0000-4000-8000-000000000013',
    p_expected_revision: 1,
  });
  assert.ok([401, 403, 404].includes(status), `Expected 401/403/404, got ${status}`);
});

// 7. Public Catalog Projection: Approved product is now visible in public catalog
await check('catalog: approved Power Bank is now published in shop_qa_public_catalog', async () => {
  const { status, data } = await httpGet('rest/v1/shop_qa_public_catalog?select=id,title,price_minor,currency,store_name');
  assert.equal(status, 200);
  assert.ok(Array.isArray(data));
  // 8 originally + 1 approved = 9 published products
  assert.equal(data.length, 9, `Expected 9 published products, found ${data.length}`);
  const approvedItem = data.find((p) => p.id === '93000000-0000-4000-8000-000000000013');
  assert.ok(approvedItem, 'Approved Power Bank (93000000-0000-4000-8000-000000000013) must be visible in public catalog');
  assert.equal(approvedItem.title, 'Synthetic QA Power Bank 20000mAh');
});

// 8. Public Catalog Projection: Rejected product remains hidden
await check('catalog: rejected Running Shoes is strictly excluded from shop_qa_public_catalog', async () => {
  const { status, data } = await httpGet('rest/v1/shop_qa_public_catalog?select=id,title');
  assert.equal(status, 200);
  const rejectedItem = data.find((p) => p.id === '93000000-0000-4000-8000-000000000023');
  assert.equal(rejectedItem, undefined, 'Rejected Running Shoes must NEVER appear in public catalog');
});

// 9. Public Catalog Projection: Pending unapproved product remains hidden
await check('catalog: pending Teff Flour is strictly excluded from shop_qa_public_catalog', async () => {
  const { status, data } = await httpGet('rest/v1/shop_qa_public_catalog?select=id,title');
  assert.equal(status, 200);
  const pendingItem = data.find((p) => p.id === '93000000-0000-4000-8000-000000000043');
  assert.equal(pendingItem, undefined, 'Pending Teff Flour must NEVER appear in public catalog before approval');
});

// 10. Catalog Page RPC: adishop_qa_catalog_page returns 9 approved products including reviewed Power Bank
await check('rpc: adishop_qa_catalog_page returns 9 items including reviewed product', async () => {
  const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_catalog_page', {
    p_sort: 'newest',
    p_limit: 24,
    p_offset: 0,
  });
  assert.equal(status, 200);
  assert.equal(data.length, 9, `Expected 9 items, got ${data.length}`);
  const item = data.find((p) => p.id === '93000000-0000-4000-8000-000000000013');
  assert.ok(item, 'Power Bank must be returned in catalog page');
  assert.equal(item.title, 'Synthetic QA Power Bank 20000mAh');
});

// 11. Product Detail RPC: Approved Power Bank detail returns complete data
await check('detail: adishop_qa_product_detail returns details for approved Power Bank', async () => {
  const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_product_detail', {
    p_product_id: '93000000-0000-4000-8000-000000000013',
  });
  assert.equal(status, 200);
  assert.ok(data);
  assert.equal(data.title, 'Synthetic QA Power Bank 20000mAh');
  assert.equal(data.has_variants, false);
  assert.equal(data.price_minor, 220000);
  assert.equal(data.stock_status, 'in_stock');
  assert.equal(data.shop_stores.name, 'Synthetic QA Store');
});

// 12. Product Detail RPC: Rejected Running Shoes detail returns null
await check('detail: adishop_qa_product_detail returns null for rejected Running Shoes', async () => {
  const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_product_detail', {
    p_product_id: '93000000-0000-4000-8000-000000000023',
  });
  assert.equal(status, 200);
  assert.equal(data, null, 'Rejected product detail must return null');
});

console.log(`\nAll Phase AG-R3 moderation contract tests passed! Total: ${passed}, Failed: ${failed}`);
