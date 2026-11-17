import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const main = read('android/app/src/main/java/com/adischat/app/ui/main/MainScreen.kt');
const home = read('android/app/src/main/java/com/adischat/app/ui/home/HomeScreen.kt');
const sidebar = read('android/app/src/main/java/com/adischat/app/ui/home/SuperAppSidebar.kt');

test('H1 Home clears every service and overlay before dashboard rendering', () => {
  const block = main.split('val onGoHome: () -> Unit = {')[1]?.split('val onSelectSidebarService:')[0];
  assert.ok(block);
  for (const assignment of [
    'activeSidebarService = null', 'activeBottomTab = null', 'selected = 0',
    'shopRoute = null', 'sellerAccountOpen = false', 'restaurantsCustomerOpen = false',
    'pendingRestaurantQrToken = null', 'restaurantCenterNativeOpen = false', 'restaurantsQaRoute = null',
  ]) assert.ok(block.includes(assignment), `missing Home reset: ${assignment}`);
  assert.match(block, /if \(FeatureFlags\.SUPER_APP_HOME_ENABLED\) \{/);
});

test('direct Chat, Shop, and Restaurants switching retains service state', () => {
  const block = main.split('val onSelectSidebarService: (SuperAppService) -> Unit =')[1]?.split('var isRestaurantCenterEligible')[0];
  assert.ok(block);
  for (const service of ['CHAT', 'ADISGEBEYA', 'RESTAURANTS']) {
    assert.ok(block.includes(`SuperAppService.${service} ->`), `missing ${service}`);
  }
  assert.match(block, /activeSidebarService = targetService/);
  assert.match(block, /activeBottomTab = null/);
  assert.match(block, /if \(!FeatureFlags\.SUPER_APP_HOME_ENABLED && FeatureFlags\.RESTAURANTS_DINE_IN_ENABLED\)/);
  assert.doesNotMatch(block, /shopRoute = null|restaurantsCustomerOpen = false/);
});

test('H1 Home sidebar dispatches each service once', () => {
  const block = home.split('if (com.adischat.app.FeatureFlags.SUPER_APP_HOME_ENABLED) {')[1]?.split('return')[0];
  assert.ok(block);
  assert.match(block, /onSelectSidebarService\(service\)/);
  assert.doesNotMatch(block, /onOpenShop\(\)|onOpenRestaurants\(\)|onGoChat\(\)/);
});

test('global bottom navigation stays hidden for any open service overlay', () => {
  assert.match(main, /!isSuperAppActive && !legacyChatSelected && !legacyShopOpen && !legacySellerOpen && !qaOpen && !restaurantsOpen && !centerOpen/);
  assert.match(main, /val restaurantsOpen = restaurantsCustomerOpen && FeatureFlags\.RESTAURANTS_DINE_IN_ENABLED/);
  assert.match(main, /val legacyShopOpen = shopRoute != null/);
});

test('expanded Home sidebar has one Home icon and text-only service rows', () => {
  const block = sidebar.split('fun SuperAppExpandedSidebar(')[1]?.split('fun SuperAppCompactSidebar(')[0];
  assert.ok(block);
  assert.doesNotMatch(block, /SERVICES|service\.selectedIcon|service\.unselectedIcon/);
  assert.match(block, /dashboardServices\.filter \{ it != SuperAppService\.HOME \}/);
  assert.match(block, /contentDescription = "Home"/);
  assert.match(block, /\.size\(48\.dp\)/);
  assert.match(block, /text = service\.title/);
});

test('compact sidebar is icon-only, top-aligned, inset and touch-accessible', () => {
  const block = sidebar.split('fun SuperAppCompactSidebar(')[1];
  assert.ok(block);
  assert.match(block, /\.width\(52\.dp\)/);
  assert.match(block, /\.statusBarsPadding\(\)/);
  assert.match(block, /verticalArrangement = Arrangement\.Top/);
  assert.match(block, /\.size\(48\.dp\)/);
  assert.doesNotMatch(block, /text = service\.title/);
});

test('Activity Personal and Business routes remain present', () => {
  assert.match(main, /SuperAppActivityScreen\(/);
  assert.match(main, /isRestaurantCenterAuthorized = isRestaurantCenterEligible/);
});
