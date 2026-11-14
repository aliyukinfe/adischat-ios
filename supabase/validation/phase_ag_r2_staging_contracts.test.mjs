// Phase AG-R2: Staging Catalog & Backend Contracts Automated Verification
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
  const data = await res.json();
  return { status: res.status, data };
}

async function httpPost(path, body) {
  const res = await fetch(`${baseUrl}/${path}`, {
    method: 'POST',
    headers: { apikey, 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  const data = await res.json();
  return { status: res.status, data };
}

console.log('--- Starting Phase AG-R2 Staging Contract Tests ---');

// 1. Categories Contract
await check('categories: GET rest/v1/shop_categories returns active QA categories', async () => {
  const { status, data } = await httpGet(
    'rest/v1/shop_categories?select=id,name,slug,code,buyer_visible,is_active&is_active=eq.true&buyer_visible=eq.true&order=name.asc'
  );
  assert.equal(status, 200);
  assert.ok(Array.isArray(data));
  assert.ok(data.length >= 4);
  const slugs = data.map((c) => c.slug);
  assert.ok(slugs.includes('qa-electronics'), 'missing qa-electronics');
  assert.ok(slugs.includes('qa-fashion'), 'missing qa-fashion');
  assert.ok(slugs.includes('qa-home'), 'missing qa-home');
  assert.ok(slugs.includes('qa-groceries'), 'missing qa-groceries');
});

// 2. Public Stores Contract
await check('stores: GET rest/v1/shop_qa_public_stores returns approved QA store', async () => {
  const { status, data } = await httpGet('rest/v1/shop_qa_public_stores?select=id,name,city');
  assert.equal(status, 200);
  assert.ok(Array.isArray(data));
  const store = data.find((s) => s.id === '92000000-0000-4000-8000-000000000001');
  assert.ok(store, 'Synthetic QA Store not found in public stores projection');
  assert.equal(store.name, 'Synthetic QA Store');
  assert.equal(store.city, 'Addis Ababa');
  assert.equal(store.owner_profile_id, undefined, 'owner_profile_id must not leak');
});

// 3. Public Catalog Visibility
await check('catalog: GET rest/v1/shop_qa_public_catalog returns published products', async () => {
  const { status, data } = await httpGet('rest/v1/shop_qa_public_catalog?select=id,title,price_minor,currency,store_name');
  assert.equal(status, 200);
  assert.ok(data.length >= 8, `Expected at least 8 published products, found ${data.length}`);
  const titles = data.map((p) => p.title);
  // Verify published products exist
  assert.ok(titles.includes('Synthetic QA Smartphone 5G'));
  assert.ok(titles.includes('Synthetic QA Wireless Earbuds'));
  assert.ok(titles.includes('Synthetic QA Cotton T-Shirt'));
  assert.ok(titles.includes('Synthetic QA Denim Jeans'));
  assert.ok(titles.includes('Synthetic QA Electric Kettle 1.8L'));
  assert.ok(titles.includes('Synthetic QA French Press Coffee Maker'));
  assert.ok(titles.includes('Synthetic QA Ethiopian Organic Honey 1kg'));
  assert.ok(titles.includes('Synthetic QA Yirgacheffe Roast Coffee 500g'));
  // Verify unpublished products are absent
  assert.ok(!titles.includes('Synthetic QA Running Shoes'), 'Running Shoes must be hidden');
  assert.ok(!titles.includes('Synthetic QA Desk LED Lamp'), 'Desk Lamp must be hidden');
  assert.ok(!titles.includes('Synthetic QA Teff Flour White 25kg'), 'Teff Flour must be hidden');
  assert.ok(!titles.includes('Synthetic QA Laptop'), 'Pre-existing draft laptop must be hidden');
});

// 4. Catalog Page RPC
await check('rpc: adishop_qa_catalog_page returns complete item envelopes with store object', async () => {
  const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_catalog_page', {
    p_sort: 'newest',
    p_limit: 24,
    p_offset: 0,
  });
  assert.equal(status, 200);
  assert.ok(data.length >= 8);
  const first = data[0];
  assert.ok(first.id);
  assert.ok(first.title);
  assert.ok(first.price_minor > 0);
  assert.equal(first.stock_status, 'in_stock');
  assert.ok(first.shop_stores);
  assert.equal(first.shop_stores.name, 'Synthetic QA Store');
  assert.deepEqual(first.shop_product_images, []);
});

// 5. Category Filtering
await check('filtering: adishop_qa_catalog_page filters accurately by category slug', async () => {
  const categories = ['qa-electronics', 'qa-fashion', 'qa-home', 'qa-groceries'];
  for (const slug of categories) {
    const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_catalog_page', {
      p_category_slug: slug,
    });
    assert.equal(status, 200);
    assert.ok(data.length >= 2, `Category ${slug} expected at least 2 published products, found ${data.length}`);
    for (const item of data) {
      assert.equal(item.category_slug, slug);
    }
  }
});

// 6. Search Term Filtering
await check('search: adishop_qa_catalog_page performs case-insensitive substring search', async () => {
  const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_catalog_page', {
    p_term: 'Honey',
  });
  assert.equal(status, 200);
  assert.equal(data.length, 1);
  assert.equal(data[0].title, 'Synthetic QA Ethiopian Organic Honey 1kg');
});

// 7. Product Detail Contracts - With Variants
await check('detail: adishop_qa_product_detail returns product envelope with variants array', async () => {
  const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_product_detail', {
    p_product_id: '93000000-0000-4000-8000-000000000011',
  });
  assert.equal(status, 200);
  assert.equal(data.title, 'Synthetic QA Smartphone 5G');
  assert.equal(data.has_variants, true);
  assert.equal(data.variants.length, 2);
  const v1 = data.variants.find((v) => v.title === '128GB / 8GB RAM');
  assert.ok(v1);
  assert.equal(v1.price_minor, 2800000);
  assert.equal(v1.availableQuantity, 15);
  assert.equal(v1.lowStockThreshold, 3);
  const v2 = data.variants.find((v) => v.title === '256GB / 12GB RAM');
  assert.ok(v2);
  assert.equal(v2.price_minor, 3400000);
  assert.equal(v2.availableQuantity, 8);
});

// 8. Product Detail Contracts - Without Variants
await check('detail: adishop_qa_product_detail handles products without variants cleanly', async () => {
  const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_product_detail', {
    p_product_id: '93000000-0000-4000-8000-000000000012',
  });
  assert.equal(status, 200);
  assert.equal(data.title, 'Synthetic QA Wireless Earbuds');
  assert.equal(data.has_variants, false);
  assert.equal(data.variants.length, 0);
  assert.equal(data.price_minor, 350000);
});

// 9. Rejection of Unpublished Products
await check('rejection: adishop_qa_product_detail returns null for pending/draft products', async () => {
  const pendingIds = [
    '93000000-0000-4000-8000-000000000023', // Running Shoes (rejected in AG-R3)
    '93000000-0000-4000-8000-000000000033', // Desk Lamp (draft)
    '93000000-0000-4000-8000-000000000043', // Teff Flour (pending)
    '93000000-0000-4000-8000-000000000001', // Pre-existing laptop (draft)
  ];
  for (const id of pendingIds) {
    const { status, data } = await httpPost('rest/v1/rpc/adishop_qa_product_detail', {
      p_product_id: id,
    });
    assert.equal(status, 200);
    assert.equal(data, null, `Product ${id} must not be accessible via detail RPC`);
  }
});

// 10. Pagination and Sorting
await check('pagination: adishop_qa_catalog_page respects limit, offset and price_low sorting', async () => {
  const { status: s1, data: page1 } = await httpPost('rest/v1/rpc/adishop_qa_catalog_page', {
    p_sort: 'price_low',
    p_limit: 4,
    p_offset: 0,
  });
  assert.equal(s1, 200);
  assert.equal(page1.length, 4);
  assert.equal(page1[0].price_minor, 75000); // Coffee
  assert.equal(page1[1].price_minor, 85000); // T-Shirt

  const { status: s2, data: page2 } = await httpPost('rest/v1/rpc/adishop_qa_catalog_page', {
    p_sort: 'price_low',
    p_limit: 4,
    p_offset: 4,
  });
  assert.equal(s2, 200);
  assert.equal(page2.length, 4);
  // Ensure no overlap between page 1 and page 2
  const ids1 = page1.map((p) => p.id);
  const ids2 = page2.map((p) => p.id);
  for (const id of ids2) {
    assert.ok(!ids1.includes(id), `Page overlap detected for product ${id}`);
  }
});

console.log(`\nAll tests passed successfully! Total: ${passed}, Failed: ${failed}`);
