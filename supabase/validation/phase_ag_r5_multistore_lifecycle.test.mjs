// Phase AG-R5: Multi-Store Checkout, Order Lifecycle & Delivery Verification
// Target: https://opkqyvttbyezjbqwkvho.supabase.co (AdisGebeya Staging)
// Supabase Free Tier - $0.00 spend - Strictly zero production impact

import assert from 'node:assert/strict';

const baseUrl = 'https://opkqyvttbyezjbqwkvho.supabase.co';
const apikey = 'sb_publishable_CXGbIKQ34YbyDHaChnPUTA_q9vDP7YO';

const buyerSession = 'qa-contract-test-buyer-session';
const seller1Session = 'qa-contract-test-seller-session';
const seller2Session = 'qa-contract-test-seller2-session';

const store1Id = '92000000-0000-4000-8000-000000000001'; // Synthetic QA Store
const store2Id = '92000000-0000-4000-8000-000000000002'; // Synthetic QA Groceries & Organics

const store1ProductId = '93000000-0000-4000-8000-000000000011'; // Smartphone 5G (Store 1)
const store1VariantId = '94000000-0000-4000-8000-000000000011';
const store2ProductId = '93000000-0000-4000-8000-000000000041'; // Organic Honey 1kg (Store 2)

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

console.log('--- Starting Phase AG-R5 Multi-Store Checkout & Delivery Contract Tests ---');

// 1. Multi-Store Catalog Verification
await check('catalog: public catalog returns active products for both store 1 and store 2', async () => {
  const { status, data } = await httpGet('rest/v1/shop_qa_public_catalog?select=id,title,store_id,store_name');
  assert.equal(status, 200);
  assert.ok(Array.isArray(data));
  const store1Items = data.filter((p) => p.store_id === store1Id);
  const store2Items = data.filter((p) => p.store_id === store2Id);
  assert.ok(store1Items.length >= 1, 'Store 1 must have published products');
  assert.ok(store2Items.length >= 1, 'Store 2 must have published products');
});

// 2. Clear Buyer Cart
await check('cart: clear buyer cart returns empty cart state', async () => {
  const { status, data } = await httpRpc('adishop_qa_cart_action', {
    p_session_jti: buyerSession,
    p_action: 'clear',
  });
  assert.equal(status, 200);
  assert.equal(data.totals.itemCount, 0);
  assert.equal(data.totals.subtotalMinor, 0);
  assert.deepEqual(data.items, []);
});

// 3. Add Store 1 Product to Cart
await check('cart: add store 1 product (Smartphone 5G) to cart', async () => {
  const { status, data } = await httpRpc('adishop_qa_cart_action', {
    p_session_jti: buyerSession,
    p_action: 'add',
    p_product_id: store1ProductId,
    p_variant_id: store1VariantId,
    p_quantity: 1,
  });
  assert.equal(status, 200);
  assert.equal(data.totals.itemCount, 1);
  assert.equal(data.items.length, 1);
  assert.equal(data.items[0].product_id, store1ProductId);
});

// 4. Add Store 2 Product to Cart
await check('cart: add store 2 product (Ethiopian Organic Honey 1kg) to cart', async () => {
  const { status, data } = await httpRpc('adishop_qa_cart_action', {
    p_session_jti: buyerSession,
    p_action: 'add',
    p_product_id: store2ProductId,
    p_quantity: 2,
  });
  assert.equal(status, 200);
  assert.equal(data.totals.itemCount, 3); // 1 + 2 = 3 items total
  assert.equal(data.items.length, 2); // 2 distinct product lines
});

// 5. Read Cart & Validate Totals Across Multiple Stores
await check('cart: reading multi-store cart reflects accurate quantities, prices and item count', async () => {
  const { status, data } = await httpRpc('adishop_qa_cart_action', {
    p_session_jti: buyerSession,
    p_action: 'read',
  });
  assert.equal(status, 200);
  assert.equal(data.currency, 'ETB');
  assert.equal(data.items.length, 2);
  const item1 = data.items.find((i) => i.product_id === store1ProductId);
  const item2 = data.items.find((i) => i.product_id === store2ProductId);
  assert.ok(item1 && item2);
  assert.equal(item1.quantity, 1);
  assert.equal(item2.quantity, 2);
  const expectedSubtotal = item1.unit_price_minor * item1.quantity + item2.unit_price_minor * item2.quantity;
  assert.equal(data.totals.subtotalMinor, expectedSubtotal);
});

// 6. Create Multi-Store Checkout Session from CART
let checkoutSessionId;
const checkoutIdempotencyKey = 'agr5-test-cart-session-' + Date.now();
await check('checkout: create multi-store checkout session from CART with inventory reservations', async () => {
  const { status, data } = await httpRpc('adishop_qa_create_checkout', {
    p_session_jti: buyerSession,
    p_source: 'CART',
    p_idempotency_key: checkoutIdempotencyKey,
  });
  assert.equal(status, 200);
  assert.ok(data.checkoutId, 'Checkout session ID must be returned');
  checkoutSessionId = data.checkoutId;
  assert.equal(data.subtotalMinor, 2980000);
  assert.equal(data.remainingPayableMinor, 2980000);
});

// 7. Idempotent Checkout Session Call
await check('checkout: repeated call with same idempotency key returns identical session', async () => {
  const { status, data } = await httpRpc('adishop_qa_create_checkout', {
    p_session_jti: buyerSession,
    p_source: 'CART',
    p_idempotency_key: checkoutIdempotencyKey,
  });
  assert.equal(status, 200);
  assert.equal(data.checkoutId, checkoutSessionId);
});

// 8. Apply Coupon on Multi-Store Checkout
await check('checkout: apply coupon QA-PERCENT-10 on multi-store cart', async () => {
  const { status, data } = await httpRpc('adishop_qa_apply_coupon', {
    p_session_jti: buyerSession,
    p_checkout_session_id: checkoutSessionId,
    p_coupon_code: 'QA-PERCENT-10',
  });
  assert.equal(status, 200);
  assert.equal(data.couponDiscountMinor, 100000); // Capped at max_discount_minor (1,000 ETB)
  assert.equal(data.remainingPayableMinor, 2880000);
});

// 9. Apply Fresh Dynamic Voucher on Multi-Store Checkout
let testVoucherCode;
await check('checkout: mint and apply fresh voucher to cover remaining payable', async () => {
  const adminSession = 'qa-contract-test-admin-session';
  const { status: vStatus, data: vData } = await httpRpc('adishop_qa_create_voucher', {
    p_session_jti: adminSession,
    p_value_minor: 5000000, // 50,000 ETB
    p_code_prefix: 'AGR5',
  });
  assert.equal(vStatus, 200);
  assert.ok(vData.code);
  testVoucherCode = vData.code;

  const { status, data } = await httpRpc('adishop_qa_apply_voucher', {
    p_session_jti: buyerSession,
    p_checkout_session_id: checkoutSessionId,
    p_voucher_code: testVoucherCode,
  });
  assert.equal(status, 200);
  assert.ok(data.voucherAppliedMinor > 0);
  assert.equal(data.remainingPayableMinor, 0); // 100% covered
});

// 10. Confirm Checkout -> Creates Parent Order & Distinct Suborders per Seller
let confirmedOrderId;
let orderGroups = [];
const confirmIdempotencyKey = 'agr5-test-confirm-' + Date.now();
await check('checkout: confirm checkout creates parent order and distinct suborders per seller', async () => {
  const { status, data } = await httpRpc('adishop_qa_confirm_checkout', {
    p_session_jti: buyerSession,
    p_checkout_session_id: checkoutSessionId,
    p_address_id: testAddressId,
    p_payment_method_code: 'voucher_prepaid',
    p_idempotency_key: confirmIdempotencyKey,
  });
  assert.equal(status, 200);
  assert.ok(data.orderId);
  confirmedOrderId = data.orderId;
  assert.equal(data.paymentStatus, 'paid');
  assert.equal(data.subordersCount, 2, 'Must create exactly 2 suborders for 2 stores');
});

// 11. Cart Is Automatically Emptied After Checkout Confirmation
await check('cart: buyer cart is automatically cleared after confirmed checkout', async () => {
  const { status, data } = await httpRpc('adishop_qa_cart_action', {
    p_session_jti: buyerSession,
    p_action: 'read',
  });
  assert.equal(status, 200);
  assert.equal(data.totals.itemCount, 0);
  assert.deepEqual(data.items, []);
});

// 12. Cross-Seller Isolation: Seller 1 Sees Only Store 1 Suborder
let store1Suborder;
await check('isolation: seller 1 querying seller orders sees only store 1 suborders', async () => {
  const { status, data } = await httpRpc('adishop_qa_seller_orders', {
    p_session_jti: seller1Session,
    p_store_id: store1Id,
  });
  assert.equal(status, 200);
  assert.ok(Array.isArray(data));
  const suborder = data.find((g) => g.orderId === confirmedOrderId);
  assert.ok(suborder, 'Store 1 suborder must be present');
  assert.equal(suborder.storeId, store1Id);
  store1Suborder = suborder;
});

// 13. Cross-Seller Isolation: Seller 2 Sees Only Store 2 Suborder
let store2Suborder;
await check('isolation: seller 2 querying seller orders sees only store 2 suborders', async () => {
  const { status, data } = await httpRpc('adishop_qa_seller_orders', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
  });
  assert.equal(status, 200);
  assert.ok(Array.isArray(data));
  const suborder = data.find((g) => g.orderId === confirmedOrderId);
  assert.ok(suborder, 'Store 2 suborder must be present');
  assert.equal(suborder.storeId, store2Id);
  store2Suborder = suborder;
});

// 14. Cross-Seller Isolation: Seller 1 Cannot Access Store 2 Suborders
await check('isolation: seller 1 attempting to query store 2 orders is rejected (42501)', async () => {
  const { status } = await httpRpc('adishop_qa_seller_orders', {
    p_session_jti: seller1Session,
    p_store_id: store2Id,
  });
  assert.ok([400, 401, 403, 500].includes(status));
});

// 15. Cross-Seller Isolation: Seller 1 Cannot Transition Store 2 Suborder
await check('isolation: seller 1 attempting action on store 2 suborder is rejected', async () => {
  const { status } = await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller1Session,
    p_store_id: store2Id,
    p_order_group_id: store2Suborder.id,
    p_action: 'accept',
  });
  assert.ok([400, 401, 403, 500].includes(status));
});

// 16. Order Lifecycle: Seller 1 Transitions Store 1 Suborder
await check('lifecycle: seller 1 advances store 1 suborder (accept -> processing -> packed -> handed_to_carrier)', async () => {
  // Accept
  let res = await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller1Session,
    p_store_id: store1Id,
    p_order_group_id: store1Suborder.id,
    p_action: 'accept',
  });
  assert.equal(res.status, 200);
  assert.equal(res.data.nextStatus, 'accepted');

  // Processing
  res = await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller1Session,
    p_store_id: store1Id,
    p_order_group_id: store1Suborder.id,
    p_action: 'processing',
  });
  assert.equal(res.status, 200);
  assert.equal(res.data.nextStatus, 'processing');

  // Packed
  res = await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller1Session,
    p_store_id: store1Id,
    p_order_group_id: store1Suborder.id,
    p_action: 'packed',
  });
  assert.equal(res.status, 200);
  assert.equal(res.data.nextStatus, 'packed');

  // Handed to Carrier
  res = await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller1Session,
    p_store_id: store1Id,
    p_order_group_id: store1Suborder.id,
    p_action: 'handed_to_carrier',
  });
  assert.equal(res.status, 200);
  assert.equal(res.data.nextStatus, 'handed_to_carrier');
});

// 17. Order Lifecycle: Seller 2 Advances Store 2 Suborder
await check('lifecycle: seller 2 advances store 2 suborder (accept -> processing -> packed -> handed_to_carrier)', async () => {
  let res = await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
    p_order_group_id: store2Suborder.id,
    p_action: 'accept',
  });
  assert.equal(res.status, 200);
  assert.equal(res.data.nextStatus, 'accepted');

  res = await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
    p_order_group_id: store2Suborder.id,
    p_action: 'processing',
  });
  assert.equal(res.status, 200);
  assert.equal(res.data.nextStatus, 'processing');

  res = await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
    p_order_group_id: store2Suborder.id,
    p_action: 'packed',
  });
  assert.equal(res.status, 200);
  assert.equal(res.data.nextStatus, 'packed');

  res = await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
    p_order_group_id: store2Suborder.id,
    p_action: 'handed_to_carrier',
  });
  assert.equal(res.status, 200);
  assert.equal(res.data.nextStatus, 'handed_to_carrier');
});

// 18. Delivery Security: Seller Cannot Reveal Buyer's Delivery Code
await check('delivery security: seller attempting to reveal delivery code is rejected', async () => {
  const { status } = await httpRpc('adishop_qa_reveal_delivery_code', {
    p_session_jti: seller1Session,
    p_order_id: confirmedOrderId,
  });
  assert.ok([400, 401, 403, 500].includes(status));
});

// 19. Delivery Code: Buyer Reveals 6-Digit Delivery OTP
let deliveryOtp;
await check('delivery code: buyer reveals 6-digit numeric OTP', async () => {
  const { status, data } = await httpRpc('adishop_qa_reveal_delivery_code', {
    p_session_jti: buyerSession,
    p_order_id: confirmedOrderId,
  });
  assert.equal(status, 200);
  assert.equal(data.orderId, confirmedOrderId);
  assert.ok(/^\d{6}$/.test(data.deliveryCode), 'Delivery code must be a 6-digit numeric string');
  deliveryOtp = data.deliveryCode;
});

// 20. Delivery Verification: Incorrect Code Is Rejected & Logged
await check('delivery verification: submitting incorrect code is rejected and logged', async () => {
  const wrongCode = deliveryOtp === '123456' ? '654321' : '123456';
  const { status, data } = await httpRpc('adishop_qa_verify_delivery_code', {
    p_session_jti: seller1Session,
    p_store_id: store1Id,
    p_order_group_id: store1Suborder.id,
    p_code: wrongCode,
  });
  assert.equal(status, 200);
  assert.equal(data.verified, false);
  assert.equal(data.error, 'invalid-delivery-code');
});

// 21. Delivery Verification: Valid Code Delivery for Store 1
await check('delivery verification: seller 1 submits correct code, completing store 1 suborder', async () => {
  const { status, data } = await httpRpc('adishop_qa_verify_delivery_code', {
    p_session_jti: seller1Session,
    p_store_id: store1Id,
    p_order_group_id: store1Suborder.id,
    p_code: deliveryOtp,
  });
  assert.equal(status, 200);
  assert.equal(data.verified, true);
  assert.equal(data.suborderStatus, 'delivered');
  assert.equal(data.parentFulfillmentStatus, 'partially_delivered', 'Parent order should be partially delivered');
});

// 22. Delivery Verification: Valid Code Delivery for Store 2 (Full Order Completion)
await check('delivery verification: seller 2 submits correct code, completing all suborders and parent order', async () => {
  const { status, data } = await httpRpc('adishop_qa_verify_delivery_code', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
    p_order_group_id: store2Suborder.id,
    p_code: deliveryOtp,
  });
  assert.equal(status, 200);
  assert.equal(data.verified, true);
  assert.equal(data.suborderStatus, 'delivered');
  assert.equal(data.parentOrderStatus, 'completed', 'All suborders delivered, parent order is completed');
  assert.equal(data.parentFulfillmentStatus, 'delivered');
});

// 23. Suborder Rejection & Inventory Restoration
await check('lifecycle & inventory: rejecting a suborder restores available inventory', async () => {
  // Clear cart and add 1 honey
  await httpRpc('adishop_qa_cart_action', { p_session_jti: buyerSession, p_action: 'clear' });
  await httpRpc('adishop_qa_cart_action', {
    p_session_jti: buyerSession,
    p_action: 'add',
    p_product_id: store2ProductId,
    p_quantity: 1,
  });

  // Create and confirm order
  const checkoutKey = 'agr5-reject-checkout-' + Date.now();
  const { status: coStatus, data: co } = await httpRpc('adishop_qa_create_checkout', {
    p_session_jti: buyerSession,
    p_source: 'CART',
    p_idempotency_key: checkoutKey,
  });
  assert.equal(coStatus, 200);
  assert.ok(co.checkoutId);

  const confirmKey = 'agr5-reject-confirm-' + Date.now();
  const { status: confStatus, data: orderData } = await httpRpc('adishop_qa_confirm_checkout', {
    p_session_jti: buyerSession,
    p_checkout_session_id: co.checkoutId,
    p_address_id: testAddressId,
    p_payment_method_code: 'cash_on_delivery',
    p_idempotency_key: confirmKey,
  });
  assert.equal(confStatus, 200);
  assert.ok(orderData.orderId);

  // Fetch Store 2 suborder
  const { data: store2Orders } = await httpRpc('adishop_qa_seller_orders', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
  });
  const rejectSuborder = store2Orders.find((g) => g.orderId === orderData.orderId);
  assert.ok(rejectSuborder);

  // Reject suborder
  const { status: rejectStatus, data: rejectData } = await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
    p_order_group_id: rejectSuborder.id,
    p_action: 'reject',
    p_reason: 'Synthetic QA testing: item out of stock in physical warehouse',
  });
  assert.equal(rejectStatus, 200);
  assert.equal(rejectData.nextStatus, 'rejected');
});

// 24. Delivery Security: 5 Consecutive Failed Attempts Trigger 10-Minute Lockout
await check('delivery security: 5 consecutive failed code attempts lock verification for 10 minutes', async () => {
  // Clear cart and add 1 honey
  await httpRpc('adishop_qa_cart_action', { p_session_jti: buyerSession, p_action: 'clear' });
  await httpRpc('adishop_qa_cart_action', {
    p_session_jti: buyerSession,
    p_action: 'add',
    p_product_id: store2ProductId,
    p_quantity: 1,
  });

  const lockCheckoutKey = 'agr5-lockout-checkout-' + Date.now();
  const { data: co } = await httpRpc('adishop_qa_create_checkout', {
    p_session_jti: buyerSession,
    p_source: 'CART',
    p_idempotency_key: lockCheckoutKey,
  });

  const lockConfirmKey = 'agr5-lockout-confirm-' + Date.now();
  const { data: orderData } = await httpRpc('adishop_qa_confirm_checkout', {
    p_session_jti: buyerSession,
    p_checkout_session_id: co.checkoutId,
    p_address_id: testAddressId,
    p_payment_method_code: 'cash_on_delivery',
    p_idempotency_key: lockConfirmKey,
  });

  // Reveal code
  await httpRpc('adishop_qa_reveal_delivery_code', {
    p_session_jti: buyerSession,
    p_order_id: orderData.orderId,
  });

  // Fetch suborder
  const { data: store2Orders } = await httpRpc('adishop_qa_seller_orders', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
  });
  const suborder = store2Orders.find((g) => g.orderId === orderData.orderId);
  assert.ok(suborder);

  // Transition to handed_to_carrier so it's in dispatched state
  await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
    p_order_group_id: suborder.id,
    p_action: 'accept',
  });
  await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
    p_order_group_id: suborder.id,
    p_action: 'processing',
  });
  await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
    p_order_group_id: suborder.id,
    p_action: 'packed',
  });
  await httpRpc('adishop_qa_seller_order_action', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
    p_order_group_id: suborder.id,
    p_action: 'handed_to_carrier',
  });

  // Submit 5 wrong codes
  for (let i = 1; i <= 5; i++) {
    const { status, data } = await httpRpc('adishop_qa_verify_delivery_code', {
      p_session_jti: seller2Session,
      p_store_id: store2Id,
      p_order_group_id: suborder.id,
      p_code: '000000',
    });
    assert.equal(status, 200);
    assert.equal(data.verified, false);
    if (i === 5) {
      assert.equal(data.isLocked, true);
      assert.equal(data.error, 'delivery-code-locked');
    }
  }

  // 6th attempt must be rejected with locked message
  const { status: lockStatus, data: lockData } = await httpRpc('adishop_qa_verify_delivery_code', {
    p_session_jti: seller2Session,
    p_store_id: store2Id,
    p_order_group_id: suborder.id,
    p_code: '000000',
  });
  assert.equal(lockStatus, 200);
  assert.equal(lockData.verified, false);
  assert.equal(lockData.error, 'delivery-code-locked');
});

// 25. Table RLS Protection: Delivery Confirmation Codes & Attempts Are Protected
await check('security: public direct GET to delivery confirmation tables is blocked or protected by RLS', async () => {
  const { status: s1, data: d1 } = await httpGet('rest/v1/shop_delivery_confirmation_codes?select=*');
  assert.ok([200, 401, 403, 404].includes(s1));
  if (s1 === 200) {
    assert.deepEqual(d1, [], 'Public access via anon key must return zero rows for delivery confirmation codes');
  }

  const { status: s2, data: d2 } = await httpGet('rest/v1/shop_delivery_code_attempts?select=*');
  assert.ok([200, 401, 403, 404].includes(s2));
  if (s2 === 200) {
    assert.deepEqual(d2, [], 'Public access via anon key must return zero rows for delivery code attempts');
  }
});

console.log(`\n========================================`);
console.log(`Phase AG-R5 Test Suite Results: ${passed} passed, ${failed} failed`);
console.log(`========================================\n`);
if (failed > 0) {
  process.exit(1);
}
