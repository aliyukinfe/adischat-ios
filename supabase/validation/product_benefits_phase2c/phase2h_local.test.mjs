import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const directory = fileURLToPath(new URL('.', import.meta.url));
const policySource = readFileSync(join(directory, '../../functions/_shared/adishop-benefits-policy.ts'), 'utf8');
const policyUrl = `data:text/javascript,${encodeURIComponent(stripTypeScriptTypes(policySource))}`;
const policy = await import(policyUrl);
const handlerSource = readFileSync(join(directory, '../../functions/_shared/adishop-benefits.ts'), 'utf8');
const handlerJavaScript = stripTypeScriptTypes(handlerSource)
  .replace('"./adishop-benefits-policy.ts"', `"${policyUrl}"`);
const handlers = await import(`data:text/javascript,${encodeURIComponent(handlerJavaScript)}`);
const { benefitMime, benefitUuid } = policy;
const { adminBenefitBrands, adminBenefitDecision, adminBenefitPreview, publicCatalogBenefits,
  publicProductBenefits, sellerBenefitAction } = handlers;
const storeId = 'aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa';
const productId = 'bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb';
const caseId = 'cccccccc-cccc-4ccc-cccc-cccccccccccc';
const profileId = 'dddddddd-dddd-4ddd-dddd-dddddddddddd';
const evidenceId = 'eeeeeeee-eeee-4eee-eeee-eeeeeeeeeeee';
const pdfBytes = Uint8Array.from([37, 80, 68, 70, 45]);
const pdfHash = createHash('sha256').update(pdfBytes).digest('hex');

function request(action, value, method = 'POST') {
  return new Request(`https://example.invalid/functions/v1/adishop-seller-business?action=${action}&storeId=${storeId}`,
    { method, ...(method === 'POST' ? { body: JSON.stringify(value) } : {}) });
}

function chain(row) {
  return {
    select() { return this; }, eq() { return this; }, is() { return this; }, in() { return this; },
    order() { return this; }, limit() { return this; }, maybeSingle() { return Promise.resolve({ data: row, error: null }); },
  };
}

test('canonical IDs and evidence signatures are validated locally', () => {
  assert.equal(benefitUuid(productId), productId);
  assert.equal(benefitUuid('not-a-uuid'), null);
  assert.equal(benefitMime(Uint8Array.from([0xff, 0xd8, 0xff, 0x00])), 'image/jpeg');
  assert.equal(benefitMime(Uint8Array.from([37, 80, 68, 70, 45])), 'application/pdf');
  assert.equal(benefitMime(Uint8Array.from([0, 1, 2, 3])), null);
});

test('foreign store is denied before claim RPC is invoked (mocked client)', async () => {
  let rpcCalls = 0;
  const client = { from: () => chain(null), rpc: async () => { rpcCalls++; return { data: null, error: null }; } };
  const result = await sellerBenefitAction(request('benefit-create', {
    storeId, productId, kind: 'original_product', termsSummary: 'Seller claim', requestKey: 'qa-key-1234',
  }), client, profileId);
  assert.equal(result.status, 404);
  assert.equal(rpcCalls, 0);
});

test('forged product identifier is rejected before claim RPC (mocked client)', async () => {
  let rpcCalls = 0;
  const client = { from: () => chain({ id: storeId }), rpc: async () => { rpcCalls++; return { data: null, error: null }; } };
  const result = await sellerBenefitAction(request('benefit-create', {
    storeId, productId: 'forged', kind: 'original_product', termsSummary: 'Seller claim', requestKey: 'qa-key-1234',
  }), client, profileId);
  assert.equal(result.status, 400);
  assert.equal(rpcCalls, 0);
});

test('self-approval and stale version RPC errors are not treated as success (mocked RPC)', async () => {
  for (const [error, status] of [['benefit-separation-of-duties', 404], ['benefit-version-conflict', 409]]) {
    const client = { rpc: async (name) => name === 'adishop_admin_session'
      ? { data: { permissions: ['benefit.review'] }, error: null }
      : { data: null, error: { message: error } } };
    const result = await adminBenefitDecision(new Request('https://example.invalid/?action=benefit-decision', {
      method: 'POST', body: JSON.stringify({ caseId, expectedVersion: 1, benefitAction: 'approve',
        effectiveAt: '2026-11-04T00:00:00Z', expiresAt: '2027-01-01T00:00:00Z', requestKey: 'qa-key-1234' }),
    }), client, profileId);
    assert.equal(result.status, status);
    assert.equal((await result.json()).ok, false);
  }
});

test('admin action without benefit permission never invokes decision RPC (mocked client)', async () => {
  const names = [];
  const client = { rpc: async (name) => {
    names.push(name);
    return { data: { permissions: [] }, error: null };
  } };
  const result = await adminBenefitDecision(new Request('https://example.invalid/?action=benefit-decision', {
    method: 'POST', body: JSON.stringify({ caseId, expectedVersion: 1, benefitAction: 'approve',
      requestKey: 'qa-key-1234' }),
  }), client, profileId);
  assert.equal(result.status, 403);
  assert.deepEqual(names, ['adishop_admin_session']);
});

test('public projection strips private fields and expired or revoked-like rows (mocked RPC)', async () => {
  const client = { rpc: async () => ({ data: [
    { type: 'original_product', label: 'Original product verified', effectiveUntil: '2999-01-01T00:00:00Z', privateReason: 'secret' },
    { type: 'seller_warranty', label: 'Old warranty', effectiveUntil: '2020-01-01T00:00:00Z' },
    { type: 'price_offer', label: 'Unapproved price', effectiveUntil: '2999-01-01T00:00:00Z' },
  ], error: null }) };
  const result = await publicProductBenefits(new Request(`https://example.invalid/?productId=${productId}`), client);
  const payload = await result.json();
  assert.equal(payload.verifiedBenefits.length, 1);
  assert.equal(payload.verifiedBenefits[0].privateReason, undefined);
});

test('evidence preview rejects missing permission before Storage access (mocked client)', async () => {
  let storageCalls = 0;
  const client = { rpc: async () => ({ data: { permissions: [] }, error: null }),
    storage: { from: () => { storageCalls++; return {}; } } };
  const result = await adminBenefitPreview(new Request(`https://example.invalid/?evidenceId=${evidenceId}`), client, profileId);
  assert.equal(result.status, 403);
  assert.equal(storageCalls, 0);
});

test('evidence preview denies deleted evidence and foreign reviewer stores (mocked client)', async () => {
  for (const foreign of [false, true]) {
    let storageCalls = 0;
    const client = {
      rpc: async () => ({ data: { permissions: ['benefit.evidence.read'] }, error: null }),
      from: (table) => ({
        select() { return this; }, eq() { return this; }, is() { return this; },
        maybeSingle: async () => ({ data: table === 'shop_benefit_evidence'
          ? (foreign ? { id: evidenceId, case_id: caseId, storage_path: 'private', mime_type: 'application/pdf', size_bytes: 5, sha256_hex: pdfHash } : null)
          : { id: caseId, store_id: storeId, status: 'submitted' }, error: null }),
        then: (resolve) => Promise.resolve({ data: table === 'shop_stores' && foreign ? [{ id: storeId }] : [], error: null }).then(resolve),
      }),
      storage: { from: () => { storageCalls++; return {}; } },
    };
    const result = await adminBenefitPreview(new Request(`https://example.invalid/?evidenceId=${evidenceId}`), client, profileId);
    assert.equal(result.status, 404);
    assert.equal(storageCalls, 0);
  }
});

test('authorized preview returns only bounded bytes and no Storage URL (mocked client)', async () => {
  const rpcCalls = [];
  const client = {
    rpc: async (name) => {
      rpcCalls.push(name);
      return name === 'adishop_admin_session'
        ? { data: { permissions: ['benefit.evidence.read'] }, error: null }
        : { data: true, error: null };
    },
    from: (table) => ({
      select() { return this; }, eq() { return this; }, is() { return this; },
      maybeSingle: async () => ({ data: table === 'shop_benefit_evidence'
        ? { id: evidenceId, case_id: caseId, storage_path: 'private', mime_type: 'application/pdf', size_bytes: 5, sha256_hex: pdfHash }
        : { id: caseId, store_id: storeId, status: 'submitted' }, error: null }),
      then: (resolve) => Promise.resolve({ data: [], error: null }).then(resolve),
    }),
    storage: { from: () => ({ download: async () => ({
      data: new Blob([pdfBytes]), error: null,
    }) }) },
  };
  const result = await adminBenefitPreview(new Request(`https://example.invalid/?evidenceId=${evidenceId}`), client, profileId);
  assert.equal(result.status, 200);
  assert.match(result.headers.get('Cache-Control'), /no-store/);
  assert.equal(result.headers.get('Content-Type'), 'application/pdf');
  assert.deepEqual([...new Uint8Array(await result.arrayBuffer())], [37, 80, 68, 70, 45]);
  assert.deepEqual(rpcCalls, ['adishop_admin_session', 'adishop_benefit_record_evidence_preview']);
});

test('preview rejects content that no longer matches evidence metadata (mocked Storage)', async () => {
  const client = {
    rpc: async (name) => name === 'adishop_admin_session'
      ? { data: { permissions: ['benefit.evidence.read'] }, error: null }
      : { data: true, error: null },
    from: (table) => ({
      select() { return this; }, eq() { return this; }, is() { return this; },
      maybeSingle: async () => ({ data: table === 'shop_benefit_evidence'
        ? { id: evidenceId, case_id: caseId, storage_path: 'private', mime_type: 'application/pdf', size_bytes: 5, sha256_hex: '0'.repeat(64) }
        : { id: caseId, store_id: storeId, status: 'submitted' }, error: null }),
      then: (resolve) => Promise.resolve({ data: [], error: null }).then(resolve),
    }),
    storage: { from: () => ({ download: async () => ({ data: new Blob([pdfBytes]), error: null }) }) },
  };
  const result = await adminBenefitPreview(new Request(`https://example.invalid/?evidenceId=${evidenceId}`), client, profileId);
  assert.equal(result.status, 503);
});

test('evidence preview fails closed when access audit cannot be recorded (mocked client)', async () => {
  let storageCalls = 0;
  const client = {
    rpc: async (name) => name === 'adishop_admin_session'
      ? { data: { permissions: ['benefit.evidence.read'] }, error: null }
      : { data: null, error: { message: 'audit unavailable' } },
    from: (table) => ({
      select() { return this; }, eq() { return this; }, is() { return this; },
      maybeSingle: async () => ({ data: table === 'shop_benefit_evidence'
        ? { id: evidenceId, case_id: caseId, storage_path: 'private', mime_type: 'application/pdf', size_bytes: 5, sha256_hex: pdfHash }
        : { id: caseId, store_id: storeId, status: 'submitted' }, error: null }),
      then: (resolve) => Promise.resolve({ data: [], error: null }).then(resolve),
    }),
    storage: { from: () => { storageCalls++; return {}; } },
  };
  const result = await adminBenefitPreview(new Request(`https://example.invalid/?evidenceId=${evidenceId}`), client, profileId);
  assert.equal(result.status, 503);
  assert.equal(storageCalls, 0);
});

test('brand administration denies missing permission and reports duplicate conflicts (mocked RPC)', async () => {
  const input = { brandAction: 'create', name: 'Brand X', requestKey: 'brand-qa-1234' };
  for (const permitted of [false, true]) {
    const calls = [];
    const client = { rpc: async (name) => {
      calls.push(name);
      return name === 'adishop_admin_session'
        ? { data: { permissions: permitted ? ['benefit.brand.manage'] : [] }, error: null }
        : { data: null, error: { message: 'benefit-brand-name-conflict' } };
    } };
    const result = await adminBenefitBrands(new Request('https://example.invalid/', {
      method: 'POST', body: JSON.stringify(input),
    }), client, profileId);
    assert.equal(result.status, permitted ? 409 : 403);
    assert.deepEqual(calls, permitted
      ? ['adishop_admin_session', 'adishop_benefit_brand_manage'] : ['adishop_admin_session']);
  }
});

test('batch catalog benefits keep only eligible public fields (mocked RPC)', async () => {
  const client = { rpc: async (name, args) => {
    assert.equal(name, 'adishop_effective_catalog_trust');
    assert.deepEqual(args.p_product_ids, [productId, storeId]);
    return { data: {
      [productId]: [{ type: 'official_store', label: 'Official store', effectiveUntil: '2999-01-01T00:00:00Z', storage_path: 'private' }],
      [storeId]: [{ type: 'seller_warranty', label: 'Expired', effectiveUntil: '2020-01-01T00:00:00Z' }],
    }, error: null };
  } };
  const result = await publicCatalogBenefits(new Request('https://example.invalid/', {
    method: 'POST', body: JSON.stringify({ productIds: [productId, storeId] }),
  }), client);
  const payload = await result.json();
  assert.equal(payload.benefitsByProductId[productId].length, 1);
  assert.equal(payload.benefitsByProductId[productId][0].storage_path, undefined);
  assert.deepEqual(payload.benefitsByProductId[storeId], []);
});

test('brand and catalog drafts require collisions, active scope and server projection (static)', () => {
  const brand = readFileSync(join(directory, '07_brand_administration.draft.sql'), 'utf8');
  const catalog = readFileSync(join(directory, '08_catalog_projection.draft.sql'), 'utf8');
  const audit = readFileSync(join(directory, '09_evidence_access_audit.draft.sql'), 'utf8');
  const projection = readFileSync(join(directory, '03_guards_projection.draft.sql'), 'utf8');
  const home = readFileSync(join(directory, '../../../android/app/src/main/java/com/adischat/app/feature/shop/data/ShopRepositories.kt'), 'utf8');
  assert.match(brand, /benefit\.brand\.manage/);
  assert.match(brand, /shop_benefit_brand_aliases where normalized_alias=normalized/);
  assert.match(brand, /brand_version integer not null/);
  assert.match(catalog, /adishop_effective_product_trust\(ids\.id\)/);
  assert.match(projection, /s\.suspended_at is null and s\.deleted_at is null/);
  assert.match(projection, /a\.authorization_scope='all_brand_products' or p\.id=any\(a\.covered_product_ids\)/);
  assert.match(projection, /when 'original_product' then 1[\s\S]*when 'official_store' then 2/);
  assert.match(home, /if \(ShopBenefitFlags\.buyerBadgesEnabled && products\.isNotEmpty\(\)\)/);
  assert.match(audit, /shop_benefit_evidence_access_immutable/);
  assert.match(audit, /benefit\.evidence\.read/);
});

test('private preview stays in-app and never issues a reusable Storage URL (static)', () => {
  const viewer = readFileSync(join(directory,
    '../../../android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopBenefitEvidenceViewer.kt'), 'utf8');
  assert.doesNotMatch(handlerSource, /createSignedUrl|getPublicUrl/);
  assert.doesNotMatch(viewer, /Intent\.ACTION_VIEW|FileProvider/);
  assert.match(viewer, /PdfRenderer/);
  assert.match(handlerSource, /Cache-Control": "private, no-store/);
});

test('drafts retain security boundaries and buyer badge flag is disabled (static)', () => {
  const sql = readFileSync(join(directory, '05_claim_intake.draft.sql'), 'utf8');
  const transitions = readFileSync(join(directory, '04_case_transitions.draft.sql'), 'utf8');
  const projection = readFileSync(join(directory, '03_guards_projection.draft.sql'), 'utf8');
  const app = readFileSync(join(directory, '../../../android/app/src/main/java/com/adischat/app/feature/shop/data/ShopBenefitsModels.kt'), 'utf8');
  assert.match(sql, /owner_profile_id=p_actor/);
  assert.match(sql, /product_store is distinct from p_store_id/);
  assert.match(sql, /pg_advisory_xact_lock/);
  assert.match(transitions, /benefit-separation-of-duties/);
  assert.match(transitions, /c\.version <> p_expected_version/);
  assert.match(projection, /c\.status='approved'/);
  assert.match(projection, /c\.revoked_at is null/);
  assert.match(projection, /p\.brand_id=a\.brand_id/);
  assert.match(projection, /p\.brand_id=c\.brand_id/);
  assert.match(transitions, /benefit-brand-mismatch/);
  assert.match(sql, /p_kind in \('original_product','manufacturer_warranty'\) and p_brand_id is null/);
  assert.match(app, /buyerBadgesEnabled = false/);
  assert.match(app, /managementEnabled = false/);
});
