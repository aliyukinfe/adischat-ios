import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const migration = readFileSync(new URL('../migrations/20261026201025_adisgebeya_explicit_seller_store_scope.sql', import.meta.url), 'utf8');
const edge = readFileSync(new URL('../functions/_shared/adishop.ts', import.meta.url), 'utf8');

test('legacy product operations reject ambiguous multi-store selection', () => {
  assert.match(migration, /selected_store_id is null and exists \(/);
  assert.match(migration, /raise exception 'seller-store-selection-required'/);
  assert.match(migration, /s\.id = selected_store_id/);
  assert.match(migration, /s\.owner_profile_id = p_profile_id or sm\.role in \('owner', 'manager'\)/);
});

test('all product RPC wrappers carry the selected store through one transaction', () => {
  for (const operation of ['list_products', 'get_product', 'create_product', 'update_product', 'save_variant_catalog', 'submit_product']) {
    const name = `adishop_seller_${operation}_for_store`;
    assert.match(migration, new RegExp(`create or replace function public\\.${name}\\(`));
    assert.match(migration, new RegExp(`grant execute on function public\\.${name}\\(`));
    assert.match(edge, new RegExp(`"${name}"`));
  }
  assert.equal((migration.match(/set_config\('adishop\.selected_store_id', p_store_id::text, true\)/g) || []).length, 6);
});

test('new privileged wrappers are not callable by anon or authenticated roles', () => {
  assert.equal((migration.match(/revoke all on function public\.adishop_seller_\w+_for_store\([^\n]+ from public, anon, authenticated;/g) || []).length, 6);
  assert.match(edge, /sellerProductStoreId: storeId/);
});

test('variant media is scoped to the selected product before base update', () => {
  const update = edge.slice(edge.indexOf('async function updateSellerProduct('), edge.indexOf('async function submitSellerProduct('));
  assert.match(update, /sellerOwnedEditableProduct\(client, profileId, productId\)/);
  assert.match(update, /\.eq\("product_id", productId\)\.eq\("store_id", storeId\)\.is\("deleted_at", null\)\.in\("id", imageIds\)/);
  assert.ok(update.indexOf('referencedImageIds') < update.indexOf('const basePayload'));
  assert.ok(update.indexOf('variant_image_not_found') < update.indexOf('const basePayload'));
  assert.match(edge, /normalized\.includes\("variant-image-product-mismatch"\)/);
});
