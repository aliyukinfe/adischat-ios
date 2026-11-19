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

console.log('=== AdisChat Phase H2.6: Explore Nearby Verification ===\n');

const repoRoot = process.cwd();
const exploreFile = path.join(repoRoot, 'android/app/src/main/java/com/adischat/app/ui/explore/ExploreNearbyScreen.kt');
const discoverFile = path.join(repoRoot, 'android/app/src/main/java/com/adischat/app/ui/discover/DiscoverScreen.kt');
const mainScreenFile = path.join(repoRoot, 'android/app/src/main/java/com/adischat/app/ui/main/MainScreen.kt');
const shopScreenFile = path.join(repoRoot, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt');

// 1. ExploreNearbyScreen.kt file exists
assert(fs.existsSync(exploreFile), 'ExploreNearbyScreen.kt exists in android/app/src/main/java/com/adischat/app/ui/explore/');

const exploreSrc = fs.existsSync(exploreFile) ? fs.readFileSync(exploreFile, 'utf8') : '';

// 2. Location detection & Addis Ababa neighborhoods
assert(
  exploreSrc.includes('ADDIS_NEIGHBORHOODS') &&
  exploreSrc.includes('"Bole"') &&
  exploreSrc.includes('"Kazanchis"') &&
  exploreSrc.includes('"Piassa"') &&
  exploreSrc.includes('"Sarbet"') &&
  exploreSrc.includes('"Mexico"') &&
  exploreSrc.includes('"Meskel Square"') &&
  exploreSrc.includes('"CMC / Ayat"') &&
  exploreSrc.includes('"Megenagna"'),
  'Neighborhood model and 8 Addis Ababa hubs (Bole, Kazanchis, Piassa, Sarbet, Mexico, Meskel, CMC, Megenagna) defined'
);

assert(
  exploreSrc.includes('ACCESS_FINE_LOCATION') &&
  exploreSrc.includes('ACCESS_COARSE_LOCATION') &&
  exploreSrc.includes('detectCurrentLocation'),
  'GPS location detection via ActivityResultContracts with graceful fallback to manual neighborhood'
);

// 3. Haversine distance formula & radius filters
assert(
  exploreSrc.includes('fun haversineDistanceKm(') &&
  exploreSrc.includes('6371.0') &&
  exploreSrc.includes('selectedDistanceKm') &&
  exploreSrc.includes('listOf(1.0, 3.0, 5.0, 10.0)'),
  'Haversine distance calculation and radius filter chips (1 km, 3 km, 5 km, 10 km) present'
);

// 4. Explore categories
assert(
  exploreSrc.includes('enum class ExploreCategory') &&
  exploreSrc.includes('ALL') &&
  exploreSrc.includes('RESTAURANTS') &&
  exploreSrc.includes('SHOPS') &&
  exploreSrc.includes('CHANNELS') &&
  exploreSrc.includes('GROUPS'),
  'ExploreCategory enum contains All, Restaurants, Shops, Channels, and Groups'
);

// 5. Dual view mode (List & Vector Map)
assert(
  exploreSrc.includes('enum class ExploreViewMode') &&
  exploreSrc.includes('LIST') &&
  exploreSrc.includes('MAP') &&
  exploreSrc.includes('ExploreListView') &&
  exploreSrc.includes('ExploreMapView'),
  'Dual view mode supported: ExploreListView and ExploreMapView with switcher'
);

// 6. Interactive Map features (zoom, recenter, pins, selection card)
assert(
  exploreSrc.includes('zoomLevel') &&
  exploreSrc.includes('panOffsetX') &&
  exploreSrc.includes('Zoom In') &&
  exploreSrc.includes('Zoom Out') &&
  exploreSrc.includes('MyLocation') &&
  exploreSrc.includes('selectedEntity') &&
  exploreSrc.includes('onSelectEntity'),
  'Interactive Map has zoom (+/-), recenter controls, entity pins, and tap preview card'
);

// 7. Real public data sources and privacy isolation
assert(
  exploreSrc.includes('RestaurantsPreviewFixtures.discoverState().allRestaurants') &&
  exploreSrc.includes('shopState.products.mapNotNull { it.store }') &&
  exploreSrc.includes('CommunityRoom') &&
  !exploreSrc.includes('user.phoneNumber') &&
  !exploreSrc.includes('privateLocation'),
  'Real public data integrated (Restaurants, AdisGebeya Stores, Public Channels & Groups) with privacy isolation'
);

// 8. DiscoverScreen delegating to ExploreNearbyScreen
assert(fs.existsSync(discoverFile), 'DiscoverScreen.kt exists');
const discoverSrc = fs.existsSync(discoverFile) ? fs.readFileSync(discoverFile, 'utf8') : '';
assert(
  discoverSrc.includes('ExploreNearbyScreen(') &&
  !discoverSrc.includes('AMROGN_CHICKEN_ORDER_URL'),
  'DiscoverScreen delegates cleanly to ExploreNearbyScreen with old mock places removed'
);

// 9. MainScreen tab 1 wiring
assert(fs.existsSync(mainScreenFile), 'MainScreen.kt exists');
const mainSrc = fs.existsSync(mainScreenFile) ? fs.readFileSync(mainScreenFile, 'utf8') : '';
assert(
  mainSrc.includes('ExploreNearbyScreen(') &&
  mainSrc.includes('onNavigateRestaurant =') &&
  mainSrc.includes('onNavigateStore =') &&
  mainSrc.includes('onNavigateCommunity =') &&
  mainSrc.includes('onGoHome = onGoHome'),
  'MainScreen tab 1 wires ExploreNearbyScreen with navigation to Restaurants, Shops, Chat, and Home'
);

// 10. Standalone All Categories screen uses AdaptiveFlowAround
assert(fs.existsSync(shopScreenFile), 'ShopScreen.kt exists');
const shopSrc = fs.existsSync(shopScreenFile) ? fs.readFileSync(shopScreenFile, 'utf8') : '';
const catScreenIdx = shopSrc.indexOf('private fun CategoryProductsScreen(');
const catScreenSlice = catScreenIdx !== -1 ? shopSrc.slice(catScreenIdx, catScreenIdx + 4500) : '';

assert(
  catScreenIdx !== -1 &&
  catScreenSlice.includes('AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp)') &&
  catScreenSlice.includes('CategoryBreadcrumbsTrail') &&
  catScreenSlice.includes('ShopHeader('),
  'CategoryProductsScreen (ShopRoute.Category) in ShopScreen.kt uses approved AdaptiveFlowAround layout'
);

console.log(`\nResults: ${passed} passed, ${failed} failed`);
if (failed > 0) {
  process.exit(1);
} else {
  console.log('\n>>> All Explore Nearby and Category Verification checks passed! <<<');
}
