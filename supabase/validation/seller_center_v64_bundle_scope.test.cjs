const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const { readFileSync } = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, 'seller_center_v64_bundles');
const read = (bundle, stage, file) => readFileSync(path.join(root, bundle, stage, file), 'utf8');
const hash = value => createHash('sha256').update(value).digest('hex').toUpperCase();

const adminBefore = read('admin-review', 'baseline', '_shared/adishop.ts');
const adminAfter = read('admin-review', 'candidate', '_shared/adishop.ts');
const tokenAnchor = '    p_request_id: String(body.requestId || request.headers.get("x-request-id") || ""),\n  }, "productDetail", "p_actor_profile_id");';
const tokenReplacement = '    p_request_id: String(body.requestId || request.headers.get("x-request-id") || ""),\n    p_expected_submitted_at: body.expectedSubmittedAt ? String(body.expectedSubmittedAt) : null,\n  }, "productDetail", "p_actor_profile_id");';
const errorAnchor = '  if (normalized.includes("invalid-product-state")) return { code: "invalid_state", message: "This product has already moved to another state.", status: 409 };';
const errorReplacement = errorAnchor + '\n  if (normalized.includes("stale-product-review")) return { code: "stale_product_review", message: "This product changed. Refresh it before reviewing.", status: 409 };\n  if (normalized.includes("idempotency-conflict")) return { code: "idempotency_conflict", message: "This review request was already used.", status: 409 };';
assert.ok(adminBefore.includes(tokenAnchor));
assert.ok(adminBefore.includes(errorAnchor));
assert.equal(adminAfter, adminBefore.replace(tokenAnchor, tokenReplacement).replace(errorAnchor, errorReplacement));
assert.equal(read('admin-review', 'baseline', 'adishop-admin-review-product/index.ts'),
  read('admin-review', 'candidate', 'adishop-admin-review-product/index.ts'));

const mediaBefore = read('seller-delete-media', 'baseline', '_shared/adishop.ts');
const mediaAfter = read('seller-delete-media', 'candidate', '_shared/adishop.ts');
const start = 'async function deleteSellerProductMedia(request: Request) {';
const end = '\nasync function reorderSellerProductMedia(';
const withoutDelete = source => {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from);
  assert.ok(from >= 0 && to > from);
  return source.slice(0, from) + '<scoped-media-deletion>' + source.slice(to);
};
const deleteRoute = source => {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from);
  assert.ok(from >= 0 && to > from);
  return source.slice(from, to);
};
const mediaErrorAnchor = '  if (normalized.includes("media-too-large")) return { code: "media_too_large", message: "Image is too large. Choose a smaller photo.", status: 413, retryable: false };';
const mediaErrorReplacement = '  if (normalized.includes("product-image-locked")) return { code: "product_image_locked", message: "This product is already under review.", status: 409, retryable: false };\n' + mediaErrorAnchor;
assert.equal(withoutDelete(mediaAfter), withoutDelete(mediaBefore).replace(mediaErrorAnchor, mediaErrorReplacement));
assert.match(mediaAfter, /client\.rpc\("adishop_seller_delete_product_media_for_store"/);
assert.match(mediaAfter, /p_store_id: storeId \|\| null/);
assert.match(mediaAfter, /storageCleanupPending/);
assert.equal(deleteRoute(readFileSync(path.join(__dirname, '..', 'functions', '_shared', 'adishop.ts'), 'utf8')),
  deleteRoute(mediaAfter));
for (const file of ['adishop-seller-delete-product-media/index.ts', '_shared/adishop-notification-dispatch.ts']) {
  assert.equal(read('seller-delete-media', 'baseline', file), read('seller-delete-media', 'candidate', file));
}

const reviewedSql = [
  ['seller_center_v62_approval_security_proposal.sql', '348003C303D5E17B662546A9AD11A9406E4F24ED8731A83756FD3D86F2D4F594'],
  ['seller_center_v63_catalog_store_guard_proposal.sql', '6F4DA035E0E8595B7077332B2AE57E5F52A701F2249A893E59975AFDA2E22871'],
  ['seller_center_v64_atomic_media_delete_proposal.sql', '36A1293992902ED9EA4A7D463388719D5E2FAD4BF6F4909C9FA0E629E4A5B88B'],
];
for (const [file, expected] of reviewedSql) {
  assert.equal(hash(readFileSync(path.join(__dirname, file))), expected);
}
const bundleDigest = (bundle, files) => {
  const digest = createHash('sha256');
  for (const file of files.sort()) {
    digest.update(file);
    digest.update(Buffer.from([0]));
    digest.update(readFileSync(path.join(root, bundle, 'candidate', file)));
    digest.update(Buffer.from([0]));
  }
  return digest.digest('hex').toUpperCase();
};
assert.equal(bundleDigest('admin-review', [
  '_shared/adishop.ts', 'adishop-admin-review-product/index.ts',
]), 'EE5415A0EA7402989DAEA0BC35D62CDFD9D329DF2E7AABCB6B884AAAF6C265A9');
assert.equal(bundleDigest('seller-delete-media', [
  '_shared/adishop-notification-dispatch.ts', '_shared/adishop.ts',
  'adishop-seller-delete-product-media/index.ts',
]), '96819558E5D985C42978FD61D7C5B40F4C8EE35E785346792CF6212E9EDBC32A');
process.stdout.write('PASS scoped deployed-baseline bundle diffs and reviewed SQL checksums\n');
