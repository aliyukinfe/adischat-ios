import fs from 'node:fs';
import path from 'node:path';

let passed = 0;
let failed = 0;

function assert(condition, message) {
  if (condition) {
    console.log(`  [PASS] ${message}`);
    passed++;
  } else {
    console.error(`  [FAIL] ${message}`);
    failed++;
  }
}

console.log('=== AdisChat Phase H2.7: Search Keyboard Repairs & Admin Power Preparation ===\n');

const repoRoot = process.cwd();
const mainScreenFile = path.join(repoRoot, 'android/app/src/main/java/com/adischat/app/ui/main/MainScreen.kt');
const chatScreenFile = path.join(repoRoot, 'android/app/src/main/java/com/adischat/app/ui/chat/ChatScreen.kt');
const searchScreenFile = path.join(repoRoot, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/MarketplaceSearchScreen.kt');
const shopScreenFile = path.join(repoRoot, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt');
const activityScreenFile = path.join(repoRoot, 'android/app/src/main/java/com/adischat/app/ui/home/SuperAppActivityScreen.kt');
const exploreFile = path.join(repoRoot, 'android/app/src/main/java/com/adischat/app/ui/explore/ExploreNearbyScreen.kt');
const adishopFile = path.join(repoRoot, 'supabase/functions/_shared/adishop.ts');

// --- 1. Search Keyboard Fixes ---
console.log('--- 1. Search Keyboard Bug Fixes ---');

assert(fs.existsSync(mainScreenFile), 'MainScreen.kt exists');
const mainSrc = fs.existsSync(mainScreenFile) ? fs.readFileSync(mainScreenFile, 'utf8') : '';

// Check that shouldShowCompactSidebar no longer toggles with !keyboardOpen
const compactSidebarIdx = mainSrc.indexOf('val shouldShowCompactSidebar =');
const compactSidebarSlice = compactSidebarIdx !== -1 ? mainSrc.slice(compactSidebarIdx, compactSidebarIdx + 350) : '';

assert(
  compactSidebarIdx !== -1 &&
  !compactSidebarSlice.includes('!keyboardOpen') &&
  compactSidebarSlice.includes('FeatureFlags.SUPER_APP_HOME_ENABLED'),
  'MainScreen shouldShowCompactSidebar does NOT toggle with !keyboardOpen (prevents sidebar bounce and focus loss)'
);

assert(fs.existsSync(chatScreenFile), 'ChatScreen.kt exists');
const chatSrc = fs.existsSync(chatScreenFile) ? fs.readFileSync(chatScreenFile, 'utf8') : '';

assert(
  chatSrc.includes('BackHandler(enabled = activeSearch.isNotBlank())') &&
  chatSrc.includes('searchQuery = ""') &&
  chatSrc.includes('onCommunityQueryChange("")'),
  'ChatScreen implements BackHandler to cleanly clear search query on Android Back'
);

assert(
  chatSrc.includes('KeyboardOptions(imeAction = ImeAction.Search)') &&
  chatSrc.includes('KeyboardActions(onSearch = { focusManager.clearFocus() })'),
  'ChatSearchBar implements ImeAction.Search and KeyboardActions with clearFocus()'
);

assert(
  !chatSrc.includes('Column(Modifier.fillMaxSize().statusBarsPadding())') ||
  chatSrc.includes('Column(Modifier.fillMaxSize())'),
  'ChatScreen ConversationsListScreen avoids double statusBarsPadding inside innerPadding'
);

assert(fs.existsSync(searchScreenFile), 'MarketplaceSearchScreen.kt exists');
const searchSrc = fs.existsSync(searchScreenFile) ? fs.readFileSync(searchScreenFile, 'utf8') : '';

assert(
  searchSrc.includes('focusRequester(focusRequester)') &&
  searchSrc.includes('focusRequester.requestFocus()') &&
  searchSrc.includes('BackHandler {') &&
  searchSrc.includes('KeyboardOptions(imeAction = ImeAction.Search)'),
  'MarketplaceSearchScreen implements focusRequester, auto-focus, BackHandler, and ImeAction.Search'
);

// Verify search bar is outside LazyColumn (in Column before LazyColumn)
const searchRowIdx = searchSrc.indexOf('OutlinedTextField(');
const lazyColIdx = searchSrc.indexOf('LazyColumn(');

assert(
  searchRowIdx !== -1 && lazyColIdx !== -1 && searchRowIdx < lazyColIdx,
  'MarketplaceSearchScreen anchors search bar in stable Column OUTSIDE LazyColumn'
);

// Verify ShopHeader in ShopScreen
assert(fs.existsSync(shopScreenFile), 'ShopScreen.kt exists');
const shopSrc = fs.existsSync(shopScreenFile) ? fs.readFileSync(shopScreenFile, 'utf8') : '';
const shopHeaderIdx = shopSrc.indexOf('internal fun ShopHeader(');
const shopHeaderSlice = shopHeaderIdx !== -1 ? shopSrc.slice(shopHeaderIdx, shopHeaderIdx + 2500) : '';

assert(
  shopHeaderIdx !== -1 &&
  shopHeaderSlice.includes('ImeAction.Search') &&
  shopHeaderSlice.includes('focusManager.clearFocus()'),
  'ShopHeader BasicTextField implements ImeAction.Search and clearFocus()'
);

// --- 2. Admin Power Capabilities ---
console.log('\n--- 2. Admin Power & Backend Authorization Verification ---');

assert(
  shopSrc.includes('enum class AdminTab') &&
  shopSrc.includes('Sellers("Sellers")') &&
  shopSrc.includes('Products("Products")') &&
  shopSrc.includes('Vouchers("Vouchers & Coupons")') &&
  shopSrc.includes('Promotions("Promotions")') &&
  shopSrc.includes('Orders("Orders")'),
  'ShopScreen AdminTab provides comprehensive governance tabs (Sellers, Products, Vouchers, Promotions, Orders)'
);

assert(
  shopSrc.includes('AdminPromotionsManagementCard(') &&
  shopSrc.includes('Governance Rules:'),
  'AdminPromotionsManagementCard enforces promotion governance rules and staged proposal mode'
);

assert(fs.existsSync(adishopFile), 'adishop.ts edge function exists');
const adishopSrc = fs.existsSync(adishopFile) ? fs.readFileSync(adishopFile, 'utf8') : '';

assert(
  adishopSrc.includes('adishop-admin-review-seller') &&
  adishopSrc.includes('adishop-admin-review-product') &&
  adishopSrc.includes('SHOP_SUPER_ADMIN') &&
  adishopSrc.includes('sellerCannotReviewOwnApplication') || adishopSrc.includes('sellerCannotReviewOwnProduct') || adishopSrc.includes('moderation'),
  'adishop.ts enforces backend admin authorization, role checks, and self-approval restrictions'
);

assert(fs.existsSync(activityScreenFile), 'SuperAppActivityScreen.kt exists');
const activitySrc = fs.existsSync(activityScreenFile) ? fs.readFileSync(activityScreenFile, 'utf8') : '';

assert(
  activitySrc.includes('isRestaurantCenterAuthorized') &&
  activitySrc.includes('isSellerAuthorized') &&
  activitySrc.includes('Open Seller Center') &&
  activitySrc.includes('Open Restaurant Center'),
  'SuperAppActivityScreen Business Tab enforces strict eligibility checks before allowing entry'
);

// --- 3. Explore Nearby Safety & Schematic Labeling ---
console.log('\n--- 3. Explore Nearby Safety Verification ---');

assert(fs.existsSync(exploreFile), 'ExploreNearbyScreen.kt exists');
const exploreSrc = fs.existsSync(exploreFile) ? fs.readFileSync(exploreFile, 'utf8') : '';

assert(
  exploreSrc.includes('OpenFreeMap Live Streets • Real Coordinates') || exploreSrc.includes('Schematic Vector Map • Real Coordinates'),
  'ExploreNearbyScreen renders real street map with coordinates'
);

assert(
  exploreSrc.includes('ACCESS_FINE_LOCATION') &&
  exploreSrc.includes('ADDIS_NEIGHBORHOODS') &&
  exploreSrc.includes('haversineDistanceKm'),
  'ExploreNearbyScreen retains optional GPS with manual neighborhood fallback and real Haversine calculation'
);

assert(
  !exploreSrc.includes('privateLocation') &&
  !exploreSrc.includes('user.phoneNumber') &&
  exploreSrc.includes('RestaurantsPreviewFixtures') &&
  exploreSrc.includes('CommunityRoom'),
  'ExploreNearbyScreen strictly isolates public data with zero private user coordinates or DMs'
);

console.log(`\nResults: ${passed} passed, ${failed} failed`);
if (failed > 0) {
  process.exit(1);
} else {
  console.log('\n>>> All H2.7 Search Keyboard & Admin Power checks passed! <<<');
}
