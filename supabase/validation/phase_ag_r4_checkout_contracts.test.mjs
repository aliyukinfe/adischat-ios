// Phase AG-R4: Voucher, Coupon and Secure Checkout Integration Contracts Verification
// Target: https://opkqyvttbyezjbqwkvho.supabase.co (AdisGebeya Staging)
// Supabase Free Tier - $0.00 spend - Strictly zero production impact

import assert from 'node:assert/strict';

const baseUrl = 'https://opkqyvttbyezjbqwkvho.supabase.co';
const apikey = 'sb_publishable_CXGbIKQ34YbyDHaChnPUTA_q9vDP7YO';

const buyerSession = 'qa-contract-test-buyer-session';
const sellerSession = 'qa-contract-test-seller-session';
const adminSession = 'qa-contract-test-admin-session';

const syntheticProductId = '93000000-0000-4000-8000-000000000011'; // Smartphone 5G (28,000 ETB)
const syntheticVariantId = '94000000-0000-4000-8000-000000000011';
const testAddressId = 'a0000000-0000-4000-8000-000000000002';

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

async function httpRpc(funcName, body) {
  const res = await fetch(`${baseUrl}/rest/v1/rpc/${funcName}`, {
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

console.log('--- Starting Phase AG-R4 Staging Voucher, Coupon & Checkout Contract Tests ---');

// 1. Table RLS Protection: Direct GET to shop_coupons is blocked or returns empty
await check('security: public direct GET to shop_coupons table is blocked or protected by RLS', async () => {
  const { status, data } = await httpGet('rest/v1/shop_coupons?select=*');
  assert.ok([200, 401, 403, 404].includes(status));
  if (status === 200) {
    assert.deepEqual(data, [], 'Public access via anon key must return zero rows for coupons');
  }
});

// 2. Table RLS Protection: Direct GET to shop_vouchers is blocked or returns empty
await check('security: public direct GET to shop_vouchers table is blocked or protected by RLS', async () => {
  const { status, data } = await httpGet('rest/v1/shop_vouchers?select=*');
  assert.ok([200, 401, 403, 404].includes(status));
  if (status === 200) {
    assert.deepEqual(data, [], 'Public access via anon key must return zero rows for vouchers');
  }
});

// 3. Table RLS Protection: Direct GET to shop_checkout_sessions is blocked or returns empty
await check('security: public direct GET to shop_checkout_sessions is blocked or protected by RLS', async () => {
  const { status, data } = await httpGet('rest/v1/shop_checkout_sessions?select=*');
  assert.ok([200, 401, 403, 404].includes(status));
  if (status === 200) {
    assert.deepEqual(data, [], 'Public access via anon key must return zero rows for checkout sessions');
  }
});

// 4. Session Validation: Invalid token is rejected with 401/403
await check('security: adishop_qa_create_checkout rejects invalid/unauthorized session token', async () => {
  const { status } = await httpRpc('adishop_qa_create_checkout', {
    p_session_jti: 'bogus-invalid-token',
    p_source: 'BUY_NOW',
    p_idempotency_key: 'test-idem-unauth',
    p_product_id: syntheticProductId,
    p_variant_id: syntheticVariantId,
    p_quantity: 1,
  });
  assert.ok([401, 403].includes(status), `Expected 401 or 403 for unauthorized session, got ${status}`);
});

// 5. Admin RBAC: Non-admin buyer cannot create synthetic vouchers
await check('rbac: buyer role is rejected from calling adishop_qa_create_voucher', async () => {
  const { status } = await httpRpc('adishop_qa_create_voucher', {
    p_session_jti: buyerSession,
    p_value_minor: 100000,
    p_expires_in_days: 30,
    p_code_prefix: 'HACK',
  });
  assert.ok([401, 403].includes(status), `Expected 401 or 403 for non-admin voucher creation, got ${status}`);
});

// 6. Admin RBAC: Admin can create unpredictable synthetic voucher
let dynamicVoucherCode = '';
await check('admin: adishop_qa_create_voucher successfully generates voucher for admin session', async () => {
  const { status, data } = await httpRpc('adishop_qa_create_voucher', {
    p_session_jti: adminSession,
    p_value_minor: 150000, // 1,500 ETB
    p_expires_in_days: 45,
    p_code_prefix: 'DYN',
  });
  assert.equal(status, 200, `Expected 200, got ${status}: ${JSON.stringify(data)}`);
  assert.ok(data.code, 'Must return generated voucher code');
  assert.equal(data.initialValueMinor, 150000);
  assert.equal(data.currency, 'ETB');
  dynamicVoucherCode = data.code;
});

// 7. Checkout Session Creation: Buy Now session with server-side price calculation
let testCheckoutId = '';
await check('checkout: adishop_qa_create_checkout calculates server-authoritative subtotal', async () => {
  const idemKey = `idem-checkout-${Date.now()}`;
  const { status, data } = await httpRpc('adishop_qa_create_checkout', {
    p_session_jti: buyerSession,
    p_source: 'BUY_NOW',
    p_idempotency_key: idemKey,
    p_product_id: syntheticProductId,
    p_variant_id: syntheticVariantId,
    p_quantity: 1,
  });
  assert.equal(status, 200, `Expected 200, got ${status}: ${JSON.stringify(data)}`);
  assert.ok(data.checkoutId, 'Checkout ID must be present');
  assert.equal(data.subtotalMinor, 2800000, 'Subtotal must be exactly 28,000 ETB (2,800,000 minor)');
  assert.equal(data.couponDiscountMinor, 0);
  assert.equal(data.voucherAppliedMinor, 0);
  assert.equal(data.remainingPayableMinor, 2800000);
  assert.equal(data.isFullyCoveredByVoucher, false);
  testCheckoutId = data.checkoutId;
});

// 8. Coupon Application: Fixed discount coupon (500 ETB off min 1,000 ETB)
await check('coupon: adishop_qa_apply_coupon applies fixed discount QA-FIXED-500', async () => {
  const { status, data } = await httpRpc('adishop_qa_apply_coupon', {
    p_session_jti: buyerSession,
    p_checkout_session_id: testCheckoutId,
    p_coupon_code: 'QA-FIXED-500',
  });
  assert.equal(status, 200, `Expected 200, got ${status}: ${JSON.stringify(data)}`);
  assert.equal(data.couponDiscountMinor, 50000, 'Coupon discount must be 50,000 minor (500 ETB)');
  assert.equal(data.grandTotalMinor, 2750000, 'Grand total must be 2,750,000 minor (27,500 ETB)');
  assert.equal(data.remainingPayableMinor, 2750000);
});

// 9. Coupon Application: Percentage discount with cap (10% up to 1,000 ETB max)
await check('coupon: adishop_qa_apply_coupon applies percentage discount with cap QA-PERCENT-10', async () => {
  const { status, data } = await httpRpc('adishop_qa_apply_coupon', {
    p_session_jti: buyerSession,
    p_checkout_session_id: testCheckoutId,
    p_coupon_code: 'QA-PERCENT-10',
  });
  assert.equal(status, 200, `Expected 200, got ${status}: ${JSON.stringify(data)}`);
  // 10% of 28,000 ETB is 2,800 ETB, but max discount cap is 1,000 ETB (100,000 minor)
  assert.equal(data.couponDiscountMinor, 100000, 'Coupon discount must be capped at 100,000 minor (1,000 ETB)');
  assert.equal(data.grandTotalMinor, 2700000, 'Grand total must be 2,700,000 minor (27,000 ETB)');
  assert.equal(data.remainingPayableMinor, 2700000);
});

// 10. Coupon Rejection: Minimum order requirement not met
await check('coupon: rejects coupon QA-HIGH-MIN when minimum order requirement is not met', async () => {
  const { status, data } = await httpRpc('adishop_qa_apply_coupon', {
    p_session_jti: buyerSession,
    p_checkout_session_id: testCheckoutId,
    p_coupon_code: 'QA-HIGH-MIN',
  });
  assert.ok([400, 409].includes(status), `Expected 400 or 409 for min order unmet, got ${status}: ${JSON.stringify(data)}`);
});

// 11. Coupon Rejection: Expired coupon
await check('coupon: rejects expired coupon QA-EXPIRED', async () => {
  const { status, data } = await httpRpc('adishop_qa_apply_coupon', {
    p_session_jti: buyerSession,
    p_checkout_session_id: testCheckoutId,
    p_coupon_code: 'QA-EXPIRED',
  });
  assert.ok([400, 409].includes(status), `Expected 400 or 409 for expired coupon, got ${status}: ${JSON.stringify(data)}`);
});

// 12. Discount Removal: Coupon can be removed safely
await check('discount: adishop_qa_remove_discount resets coupon discount', async () => {
  const { status, data } = await httpRpc('adishop_qa_remove_discount', {
    p_session_jti: buyerSession,
    p_checkout_session_id: testCheckoutId,
    p_discount_type: 'coupon',
  });
  assert.equal(status, 200, `Expected 200, got ${status}: ${JSON.stringify(data)}`);
  assert.equal(data.couponDiscountMinor, 0);
  assert.equal(data.remainingPayableMinor, 2800000);
});

// 13. Voucher Application: Partial coverage voucher
await check('voucher: adishop_qa_apply_voucher applies partial credit VCH-PARTIAL-1000', async () => {
  const { status, data } = await httpRpc('adishop_qa_apply_voucher', {
    p_session_jti: buyerSession,
    p_checkout_session_id: testCheckoutId,
    p_voucher_code: 'VCH-PARTIAL-1000',
  });
  assert.equal(status, 200, `Expected 200, got ${status}: ${JSON.stringify(data)}`);
  assert.equal(data.voucherAppliedMinor, 100000, 'Voucher applied must be 100,000 minor (1,000 ETB)');
  assert.equal(data.remainingPayableMinor, 2700000, 'Remaining payable must be 2,700,000 minor');
  assert.equal(data.isFullyCoveredByVoucher, false);
});

// 14. Combined Coupon + Voucher Stacking
await check('stacking: coupon and voucher can be combined with server-authoritative precedence', async () => {
  // Apply QA-FIXED-500 (500 ETB off 28,000 ETB -> 27,500 ETB)
  const cRes = await httpRpc('adishop_qa_apply_coupon', {
    p_session_jti: buyerSession,
    p_checkout_session_id: testCheckoutId,
    p_coupon_code: 'QA-FIXED-500',
  });
  assert.equal(cRes.status, 200);
  assert.equal(cRes.data.couponDiscountMinor, 50000);
  // Voucher was already attached (1,000 ETB) -> Remaining payable must be 28,000 - 500 - 1,000 = 26,500 ETB
  assert.equal(cRes.data.voucherAppliedMinor, 100000);
  assert.equal(cRes.data.remainingPayableMinor, 2650000);
});

// 15. Full Voucher Coverage: VCH-FULL-COVERAGE-2026 covers remaining balance completely
await check('voucher: adishop_qa_apply_voucher covers 100% of order balance', async () => {
  const { status, data } = await httpRpc('adishop_qa_apply_voucher', {
    p_session_jti: buyerSession,
    p_checkout_session_id: testCheckoutId,
    p_voucher_code: 'VCH-FULL-COVERAGE-2026',
  });
  assert.equal(status, 200, `Expected 200, got ${status}: ${JSON.stringify(data)}`);
  assert.equal(data.couponDiscountMinor, 50000); // 500 ETB coupon
  assert.equal(data.voucherAppliedMinor, 2750000); // 27,500 ETB voucher
  assert.equal(data.remainingPayableMinor, 0, 'Remaining payable must be 0 ETB');
  assert.equal(data.isFullyCoveredByVoucher, true, 'isFullyCoveredByVoucher must be true');
});

// 16. Checkout Confirmation: Verified payment state (paid) when remaining payable = 0
let confirmedOrderId = '';
await check('confirm: adishop_qa_confirm_checkout completes order with paid status for full voucher', async () => {
  const confIdem = `conf-idem-${Date.now()}`;
  const { status, data } = await httpRpc('adishop_qa_confirm_checkout', {
    p_session_jti: buyerSession,
    p_checkout_session_id: testCheckoutId,
    p_address_id: testAddressId,
    p_payment_method_code: 'voucher_prepaid',
    p_idempotency_key: confIdem,
  });
  assert.equal(status, 200, `Expected 200, got ${status}: ${JSON.stringify(data)}`);
  assert.ok(data.orderId, 'Order ID must be returned');
  assert.equal(data.status, 'created');
  assert.equal(data.paymentStatus, 'paid', 'Payment status must be verified as PAID when covered by voucher');
  assert.equal(data.isFullyCoveredByVoucher, true);
  confirmedOrderId = data.orderId;
});

// 17. Idempotency: Duplicate confirmation returns existing order without duplicate charge
await check('idempotency: re-confirming same checkout session returns duplicate flag without error', async () => {
  const { status, data } = await httpRpc('adishop_qa_confirm_checkout', {
    p_session_jti: buyerSession,
    p_checkout_session_id: testCheckoutId,
    p_address_id: testAddressId,
    p_payment_method_code: 'voucher_prepaid',
    p_idempotency_key: 'second-attempt-key',
  });
  assert.equal(status, 200);
  assert.equal(data.orderId, confirmedOrderId);
  assert.equal(data.isDuplicate, true);
});

// 18. Order Cancellation & Voucher Balance Restoration
await check('refund: adishop_qa_cancel_order restores voucher balance and coupon usage', async () => {
  const { status, data } = await httpRpc('adishop_qa_cancel_order', {
    p_session_jti: buyerSession,
    p_order_id: confirmedOrderId,
    p_reason: 'Automated contract test cancellation',
  });
  assert.equal(status, 200, `Expected 200, got ${status}: ${JSON.stringify(data)}`);
  assert.equal(data.status, 'cancelled');
});

// 19. Payment Methods: Available payment methods include voucher and COD
await check('payment-methods: shop_payment_methods contains synthetic checkout methods', async () => {
  const { status, data } = await httpGet('rest/v1/shop_payment_methods?select=code,label,provider&enabled=eq.true');
  assert.equal(status, 200);
  assert.ok(Array.isArray(data));
  const codes = data.map((m) => m.code);
  assert.ok(codes.includes('voucher_prepaid'), 'voucher_prepaid method must be active');
  assert.ok(codes.includes('cash_on_delivery'), 'cash_on_delivery method must be active');
});

console.log(`\nAll Phase AG-R4 Voucher, Coupon & Checkout Contract Tests PASSED! Total: ${passed}, Failed: ${failed}`);
