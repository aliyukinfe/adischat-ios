import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const migration = readFileSync(new URL('../migrations/20261027134112_adisgebeya_seller_order_store_scope.sql', import.meta.url), 'utf8');
const edge = readFileSync(new URL('../functions/_shared/adishop.ts', import.meta.url), 'utf8');

test('seller mutations lock and match the selected store before existing atomic RPCs', () => {
  for (const [scoped, original] of [
    ['adishop_transition_seller_order_group_scoped', 'adishop_transition_seller_order_group'],
    ['adishop_verify_delivery_code_scoped', 'adishop_verify_delivery_code'],
    ['adishop_seller_respond_request_scoped', 'adishop_seller_respond_request'],
  ]) {
    const start = migration.indexOf(`create or replace function public.${scoped}(`);
    assert.ok(start >= 0, scoped);
    const section = migration.slice(start, migration.indexOf('$$;', start) + 3);
    assert.match(section, /for update;/);
    assert.match(section, /actual_store_id is distinct from p_selected_store_id/);
    assert.match(section, /adishop_profile_can_fulfill_store\(p_profile_id, p_selected_store_id\)/);
    assert.match(section, new RegExp(`return public\\.${original}\\(`));
    assert.ok(section.indexOf('actual_store_id is distinct') < section.indexOf(`return public.${original}(`));
    assert.match(migration, new RegExp(`revoke all on function public\\.${scoped}\\(`));
    assert.match(migration, new RegExp(`grant execute on function public\\.${scoped}\\(`));
    assert.match(edge, new RegExp(`client\\.rpc\\("${scoped}"`));
  }
});

test('seller order reads and actions resolve selected store without multi-store fallback', () => {
  const scope = edge.slice(edge.indexOf('async function sellerOrderStoreScope('), edge.indexOf('async function sellerOrderGroupMatchesStore('));
  assert.match(scope, /!storeId && storeIds\.length > 1/);
  assert.match(scope, /seller_store_required/);
  assert.match(scope, /adishop_profile_can_fulfill_store/);
  for (const handler of ['listSellerOrderGroups', 'getSellerOrderGroup', 'transitionSellerOrderGroup', 'verifySellerDeliveryCode', 'sellerRequests', 'sellerRespondToRequest']) {
    const start = edge.indexOf(`async function ${handler}(`);
    assert.ok(start >= 0, handler);
    assert.match(edge.slice(start, start + 1400), /sellerOrderStoreScope\(/, handler);
  }
  assert.match(edge, /sellerOrderStoreId: storeId/);
});
