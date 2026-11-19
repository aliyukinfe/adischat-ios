import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const REPO_ROOT = path.resolve(__dirname, '..');

console.log('=== AdisChat H2.3: Complete AdisGebeya Adaptive UI & Compact Checkout Verification ===\n');

// 1. All Six Shop Screens Adaptive Layout Verification
test('1. Marketplace Home implements AdaptiveFlowAround and sidebar-aware top bar', () => {
  const shopScreen = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt'), 'utf8');
  const homeChrome = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/AdisGebeyaHomeChrome.kt'), 'utf8');

  // ShopHomePadded uses AdaptiveFlowAround
  assert.ok(shopScreen.includes('private fun ShopHomePadded(content: @Composable () -> Unit) {'));
  assert.ok(shopScreen.includes('AdaptiveFlowAround'));
  assert.ok(shopScreen.includes('besideStart = 60.dp'));

  // AdisGebeyaHomeTopBar reacts to LocalSidebarBounds
  assert.ok(homeChrome.includes('val sidebar = LocalSidebarBounds.current'));
  assert.ok(homeChrome.includes('val startPad = if (sidebar != null) 60.dp else 12.dp'));
  assert.ok(homeChrome.includes('.padding(start = startPad, end = 12.dp'));
});

test('2. TrendingScreen implements AdaptiveFlowAround', () => {
  const trending = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopTrendingScreen.kt'), 'utf8');
  assert.ok(trending.includes('import com.adischat.app.ui.home.AdaptiveFlowAround'));
  assert.ok(trending.includes('AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp)'));
  assert.ok(trending.includes('Text("Trending"'));
  assert.ok(trending.includes('PremiumHomeProductGrid'));
});

test('3. CartScreen implements AdaptiveFlowAround and sticky checkout bar', () => {
  const cart = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopCartScreen.kt'), 'utf8');
  assert.ok(cart.includes('import com.adischat.app.ui.home.AdaptiveFlowAround'));
  assert.ok(cart.includes('AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp)'));
  assert.ok(cart.includes('CartCheckoutBar'));
  assert.ok(cart.includes('Text("Cart (${cart?.items?.size ?: cartCount})"'));
});

test('4. ProductDetailsScreen implements AdaptiveFlowAround and sidebar-aware top bar', () => {
  const productDetails = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ProductDetailsScreen.kt'), 'utf8');
  assert.ok(productDetails.includes('import com.adischat.app.ui.home.AdaptiveFlowAround'));
  assert.ok(productDetails.includes('AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp)'));
  assert.ok(productDetails.includes('ProductImageGallery'));
  assert.ok(productDetails.includes('ProductSummary'));
  assert.ok(productDetails.includes('StickyPurchaseBar'));

  // Top bar bounds
  assert.ok(productDetails.includes('val sidebar = LocalSidebarBounds.current'));
  assert.ok(productDetails.includes('val startPad = if (sidebar != null) 60.dp else 8.dp'));
});

test('5. AdisGebeyaBuyerAccountScreen implements AdaptiveFlowAround', () => {
  const account = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/AdisGebeyaBuyerAccountScreen.kt'), 'utf8');
  assert.ok(account.includes('import com.adischat.app.ui.home.AdaptiveFlowAround'));
  assert.ok(account.includes('AdaptiveFlowAround(besideStart = 60.dp, belowStart = 16.dp)'));
  assert.ok(account.includes('BuyerIdentityHeader'));
  assert.ok(account.includes('AccountOrderSection'));
  assert.ok(account.includes('AccountActionGrid'));
});

test('6. CheckoutScreen implements AdaptiveFlowAround and compact bottom action bar', () => {
  const shopScreen = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt'), 'utf8');
  const checkoutDef = shopScreen.slice(shopScreen.indexOf('private fun CheckoutScreen('), shopScreen.indexOf('internal fun ErrorCardMessage('));

  assert.ok(checkoutDef.includes('AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp)'));
  assert.ok(checkoutDef.includes('CheckoutBottomBar('));
  assert.ok(shopScreen.includes('private fun CheckoutBottomBar('));
});

// 2. Checkout Redesign Specifics Verification
test('7. Checkout address section is compact with Edit/Change action', () => {
  const shopScreen = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt'), 'utf8');
  const checkoutDef = shopScreen.slice(shopScreen.indexOf('private fun CheckoutScreen('), shopScreen.indexOf('internal fun ErrorCardMessage('));

  assert.ok(checkoutDef.includes('Text("Delivery address"'));
  assert.ok(checkoutDef.includes('if (showAddressSelector) "Done" else "Change"'));
  assert.ok(checkoutDef.includes('activeAddress.label.ifBlank'));
  assert.ok(checkoutDef.includes('Default'));
});

test('8. Checkout includes compact order items preview and compact order summary', () => {
  const shopScreen = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt'), 'utf8');
  const checkoutDef = shopScreen.slice(shopScreen.indexOf('private fun CheckoutScreen('), shopScreen.indexOf('internal fun ErrorCardMessage('));

  assert.ok(checkoutDef.includes('Text("Order items (${cartItems.size})"'));
  assert.ok(checkoutDef.includes('cartItems.take(3)'));
  assert.ok(checkoutDef.includes('ShopAssetImage'));
  assert.ok(checkoutDef.includes('Text("Order summary"'));
  assert.ok(checkoutDef.includes('Text("Total payable"'));
});

test('9. Payment & Vouchers is located BELOW Order summary', () => {
  const shopScreen = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt'), 'utf8');
  const checkoutDef = shopScreen.slice(shopScreen.indexOf('private fun CheckoutScreen('), shopScreen.indexOf('internal fun ErrorCardMessage('));

  const orderSummaryIdx = checkoutDef.indexOf('Text("Order summary"');
  const paymentIdx = checkoutDef.indexOf('Text("Payment & Vouchers"');

  assert.ok(orderSummaryIdx > 0);
  assert.ok(paymentIdx > 0);
  assert.ok(paymentIdx > orderSummaryIdx, 'Payment & Vouchers must appear below Order summary');
});

test('10. Store Voucher is primary supported payment and coupon is collapsed by default', () => {
  const shopScreen = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt'), 'utf8');
  const checkoutDef = shopScreen.slice(shopScreen.indexOf('private fun CheckoutScreen('), shopScreen.indexOf('internal fun ErrorCardMessage('));

  // Collapsed coupon with icon
  assert.ok(checkoutDef.includes('Have a coupon?'));
  assert.ok(checkoutDef.includes('Icons.Default.LocalOffer'));
  assert.ok(checkoutDef.includes('couponExpanded = !couponExpanded'));

  // Voucher
  assert.ok(checkoutDef.includes('Prepaid Store Voucher'));
  assert.ok(checkoutDef.includes('Redeem'));
  assert.ok(checkoutDef.includes('100% Covered by Store Voucher — No payment due'));
});

test('11. Telebirr and Chapa are disabled with Coming Soon, and zero Cash on Delivery', () => {
  const shopScreen = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt'), 'utf8');
  const checkoutDef = shopScreen.slice(shopScreen.indexOf('private fun CheckoutScreen('), shopScreen.indexOf('internal fun ErrorCardMessage('));

  assert.ok(checkoutDef.includes('TelebirrLogo()'));
  assert.ok(checkoutDef.includes('ChapaLogo()'));
  assert.ok(checkoutDef.includes('"Coming Soon"'));
  assert.ok(checkoutDef.includes('"Direct mobile wallet"'));
  assert.ok(checkoutDef.includes('"Cards & Bank transfer"'));

  // No COD UI
  assert.ok(!checkoutDef.includes('Cash on delivery (available)'));
  assert.ok(!checkoutDef.includes('Cash on Delivery'));
});

test('12. Preserves strict backend gating on canPlaceOrder without simulation', () => {
  const shopScreen = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt'), 'utf8');
  const checkoutDef = shopScreen.slice(shopScreen.indexOf('private fun CheckoutScreen('), shopScreen.indexOf('internal fun ErrorCardMessage('));

  assert.ok(checkoutDef.includes('val canPlaceOrder = addressFlow.selectedAddressId.isNotBlank() &&'));
  assert.ok(checkoutDef.includes('(checkout.isFullyCoveredByVoucher || (checkout.remainingPayableMinor == 0 && checkout.voucherAppliedMinor > 0) || (selectedPaymentMethod.isNotBlank() && eligiblePaymentMethods.any { it.code == selectedPaymentMethod }))'));
  assert.ok(checkoutDef.includes('enabled = canPlaceOrder'));
  assert.ok(checkoutDef.includes('onConfirm(checkout.id, addressFlow.selectedAddressId, selectedPaymentMethod)'));
});

test('13. No duplicate floating Home icon in shop feature', () => {
  const shopScreen = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt'), 'utf8');
  assert.ok(!shopScreen.includes('FloatingActionButton'));
  assert.ok(!shopScreen.includes('floatingHome'));
});
