// Local-only regression for the buyer catalog's suspended-store visibility.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { Client } = require('pg');

const databaseUrl = process.env.V63_LOCAL_DATABASE_URL;
const apiUrl = process.env.V63_LOCAL_API_URL;
if (!databaseUrl?.includes('@127.0.0.1:55463/') || apiUrl !== 'http://127.0.0.1:55464/') {
  throw new Error('This regression may run only against the disposable local V6.3 services');
}

async function main() {
  const client = new Client({ connectionString: databaseUrl });
  await client.connect();
  try {
    const product = await client.query(`select p.id,p.store_id from public.shop_products p
      join public.shop_stores s on s.id=p.store_id
      where p.status='active' and p.moderation_status='approved' and s.status='active' limit 1`);
    assert.ok(product.rows[0], 'approved QA product missing');
    const { id, store_id: storeId } = product.rows[0];
    const source = fs.readFileSync(path.join(__dirname, '..', '..', 'android', 'app', 'src',
      'main', 'java', 'com', 'adischat', 'app', 'feature', 'shop', 'data', 'ShopSupabaseApi.kt'), 'utf8');
    const match = source.match(/@GET\("(rest\/v1\/shop_products\?[^\"]+)"\)\s*suspend fun products\(/);
    assert.ok(match);
    const url = new URL(match[1].replace(/^rest\/v1\//, ''), apiUrl);
    url.searchParams.set('id', `eq.${id}`);
    const fetchRows = async () => {
      const response = await fetch(url);
      if (!response.ok) throw new Error(`Buyer API HTTP ${response.status}`);
      return response.json();
    };
    await client.query(`update public.shop_stores
      set status='suspended',approval_status='suspended' where id=$1`, [storeId]);
    try {
      const baseline = await fetchRows();
      assert.equal(baseline.length, 1, 'baseline defect did not reproduce');
      assert.equal(baseline[0].shop_stores, null);
      process.stdout.write('PASS baseline defect reproduced: suspended-store product visible\n');
      const sql = fs.readFileSync(path.join(__dirname,
        'seller_center_v63_catalog_store_guard_proposal.sql'), 'utf8');
      await client.query(sql);
      const corrected = await fetchRows();
      assert.deepEqual(corrected, []);
      process.stdout.write('PASS local catalog guard hides suspended-store product\n');
    } finally {
      await client.query(`update public.shop_stores
        set status='active',approval_status='approved' where id=$1`, [storeId]);
    }
    const restored = await fetchRows();
    assert.equal(restored.length, 1);
    process.stdout.write('PASS active approved store product remains visible\n');
  } finally {
    await client.end();
  }
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
