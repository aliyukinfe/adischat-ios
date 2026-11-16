import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const directory = fileURLToPath(new URL('.', import.meta.url));
const policy = readFileSync(join(directory, '../../functions/_shared/adishop-benefits-policy.ts'), 'utf8');
const policyUrl = `data:text/javascript,${encodeURIComponent(stripTypeScriptTypes(policy))}`;
const source = readFileSync(join(directory, '../../functions/_shared/adishop-benefits.ts'), 'utf8');
const handlers = await import(`data:text/javascript,${encodeURIComponent(stripTypeScriptTypes(source)
  .replace('"./adishop-benefits-policy.ts"', `"${policyUrl}"`))}`);
const { adminBenefitBrands, adminBenefitUploadCleanup, sellerBenefitEvidence } = handlers;
const storeId = 'aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa';
const caseId = 'cccccccc-cccc-4ccc-cccc-cccccccccccc';
const profileId = 'dddddddd-dddd-4ddd-dddd-dddddddddddd';
const jobId = 'eeeeeeee-eeee-4eee-eeee-eeeeeeeeeeee';
const path = `${storeId}/${caseId}/${jobId}.pdf`;
const bytes = Uint8Array.from([37, 80, 68, 70, 45]);

function query(row) {
  return {
    select() { return this; }, eq() { return this; }, is() { return this; },
    maybeSingle: async () => ({ data: row, error: null }),
  };
}

function uploadRequest() {
  const form = new FormData();
  form.set('storeId', storeId);
  form.set('caseId', caseId);
  form.set('evidenceType', 'invoice');
  form.set('requestKey', 'upload-qa-1234');
  form.set('file', new File([bytes], 'invoice.pdf', { type: 'application/pdf' }));
  return new Request('https://example.invalid/benefit-evidence', { method: 'POST', body: form });
}

test('mocked evidence upload reserves before Storage and finalizes afterward', async () => {
  const calls = [];
  const client = {
    from: (table) => query({ id: table === 'shop_stores' ? storeId : caseId }),
    rpc: async (name) => {
      calls.push(name);
      return { data: name.endsWith('_reserve') ? { state: 'pending', jobId, path } : {
        id: jobId, evidenceType: 'invoice', mimeType: 'application/pdf', createdAt: '2026-11-04T00:00:00Z',
      }, error: null };
    },
    storage: { from: () => ({ upload: async () => { calls.push('storage-upload'); return { error: null }; } }) },
  };
  const response = await sellerBenefitEvidence(uploadRequest(), client, profileId);
  assert.equal(response.status, 200);
  assert.deepEqual(calls, ['adishop_benefit_upload_reserve', 'storage-upload', 'adishop_benefit_upload_finalize']);
  assert.doesNotMatch(JSON.stringify(await response.json()), /storage_path|storagePath/);
});

test('mocked upload error with matching stored bytes can finalize an idempotent retry', async () => {
  const calls = [];
  const client = {
    from: (table) => query({ id: table === 'shop_stores' ? storeId : caseId }),
    rpc: async (name) => {
      calls.push(name);
      return { data: name.endsWith('_reserve') ? { state: 'pending', jobId, path } : { id: jobId }, error: null };
    },
    storage: { from: () => ({
      upload: async () => ({ error: { message: 'already exists' } }),
      download: async () => ({ data: new Blob([bytes]), error: null }),
    }) },
  };
  const response = await sellerBenefitEvidence(uploadRequest(), client, profileId);
  assert.equal(response.status, 200);
  assert.deepEqual(calls, ['adishop_benefit_upload_reserve', 'adishop_benefit_upload_finalize']);
});

test('mocked foreign seller cannot reserve or upload evidence', async () => {
  let calls = 0;
  const client = {
    from: () => query(null),
    rpc: async () => { calls++; return { data: null, error: null }; },
    storage: { from: () => { calls++; return {}; } },
  };
  const response = await sellerBenefitEvidence(uploadRequest(), client, profileId);
  assert.equal(response.status, 404);
  assert.equal(calls, 0);
});

test('mocked brand alias retirement and merge review use admin-only RPCs', async () => {
  const calls = [];
  const client = { rpc: async (name, args) => {
    calls.push([name, args]);
    return name === 'adishop_admin_session'
      ? { data: { permissions: ['benefit.brand.manage'] }, error: null }
      : { data: { id: jobId }, error: null };
  } };
  for (const payload of [
    { brandAction: 'retire_alias', brandId: storeId, name: 'old name', expectedVersion: 1,
      reason: 'Trademark retired', requestKey: 'alias-qa-1234' },
    { brandAction: 'merge_request', sourceBrandId: storeId, targetBrandId: caseId,
      reason: 'Possible duplicate', requestKey: 'merge-qa-1234' },
  ]) {
    const response = await adminBenefitBrands(new Request('https://example.invalid/', {
      method: 'POST', body: JSON.stringify(payload),
    }), client, profileId);
    assert.equal(response.status, 200);
  }
  assert.deepEqual(calls.filter(([name]) => name !== 'adishop_admin_session').map(([name]) => name),
    ['adishop_benefit_brand_manage', 'adishop_benefit_brand_merge_request']);
});

test('cleanup stays off by default and denies missing permission when enabled (mocked)', async () => {
  const previous = globalThis.Deno;
  try {
    let enabled = false;
    globalThis.Deno = { env: { get: () => enabled ? 'true' : undefined } };
    let calls = 0;
    const client = { rpc: async () => { calls++; return { data: { permissions: [] }, error: null }; } };
    const request = () => new Request('https://example.invalid/', {
      method: 'POST', body: JSON.stringify({ execute: true, limit: 1 }),
    });
    assert.equal((await adminBenefitUploadCleanup(request(), client, profileId)).status, 404);
    assert.equal(calls, 0);
    enabled = true;
    assert.equal((await adminBenefitUploadCleanup(request(), client, profileId)).status, 403);
    assert.equal(calls, 1);
  } finally {
    globalThis.Deno = previous;
  }
});

test('mocked cleanup removes only RPC-claimed private paths and records completion', async () => {
  const previous = globalThis.Deno;
  globalThis.Deno = { env: { get: () => 'true' } };
  try {
    const calls = [];
    const leaseId = 'ffffffff-ffff-4fff-ffff-ffffffffffff';
    const client = {
      rpc: async (name, args) => {
        calls.push(name);
        if (name === 'adishop_admin_session') return { data: { permissions: ['benefit.evidence.cleanup'] }, error: null };
        if (name === 'adishop_benefit_upload_claim_cleanup') return {
          data: [{ jobId, leaseId, path }], error: null,
        };
        assert.equal(args.p_success, true);
        return { data: true, error: null };
      },
      storage: { from: (bucket) => {
        assert.equal(bucket, 'shop-benefit-evidence');
        return { remove: async (paths) => {
          calls.push('storage-remove');
          assert.deepEqual(paths, [path]);
          return { data: [], error: null };
        } };
      } },
    };
    const response = await adminBenefitUploadCleanup(new Request('https://example.invalid/', {
      method: 'POST', body: JSON.stringify({ execute: true, limit: 1 }),
    }), client, profileId);
    assert.equal(response.status, 200);
    assert.deepEqual(calls, ['adishop_admin_session', 'adishop_benefit_upload_claim_cleanup',
      'storage-remove', 'adishop_benefit_upload_finish_cleanup']);
    assert.deepEqual(await response.json(), { ok: true, claimed: 1, cleaned: 1, failed: 0 });
  } finally {
    globalThis.Deno = previous;
  }
});

test('mocked cleanup treats only NoSuchKey as an idempotent deletion', async () => {
  const previous = globalThis.Deno;
  globalThis.Deno = { env: { get: () => 'true' } };
  try {
    for (const [code, expectedSuccess] of [['NoSuchKey', true], ['NoSuchBucket', false]]) {
      let finishedSuccess = null;
      const client = {
        rpc: async (name, args) => {
          if (name === 'adishop_admin_session') return { data: { permissions: ['benefit.evidence.cleanup'] }, error: null };
          if (name === 'adishop_benefit_upload_claim_cleanup') return {
            data: [{ jobId, leaseId: profileId, path }], error: null,
          };
          finishedSuccess = args.p_success;
          return { data: true, error: null };
        },
        storage: { from: () => ({ remove: async () => ({ error: { code } }) }) },
      };
      const response = await adminBenefitUploadCleanup(new Request('https://example.invalid/', {
        method: 'POST', body: JSON.stringify({ execute: true, limit: 1 }),
      }), client, profileId);
      assert.equal(finishedSuccess, expectedSuccess);
      assert.equal(response.status, expectedSuccess ? 200 : 503);
    }
  } finally {
    globalThis.Deno = previous;
  }
});

test('draft SQL statically reserves retired aliases and protects evidence cleanup', () => {
  const brands = readFileSync(join(directory, '07_brand_administration.draft.sql'), 'utf8');
  const uploads = readFileSync(join(directory, '10_pending_evidence_uploads.draft.sql'), 'utf8');
  assert.match(brands, /retired_at=now\(\)/);
  assert.match(brands, /shop_benefit_brand_merge_events_immutable/);
  assert.match(brands, /requested_by_profile_id=p_actor/);
  assert.doesNotMatch(brands, /update public\.shop_store_brand_authorizations/);
  assert.match(uploads, /status='cleanup_claimed'/);
  assert.match(uploads, /c\.status<>'draft'/);
  assert.match(uploads, /where e\.storage_path=j\.storage_path or e\.id=j\.evidence_id/);
  assert.match(uploads, /cleanup_lease_expires_at<now\(\)/);
  assert.match(uploads, /last_attempt_at<now\(\)-interval '1 hour'/);
  assert.match(uploads, /shop_benefit_upload_events_immutable/);
  assert.match(uploads, /revoke insert on public\.shop_benefit_evidence from service_role/);
});
