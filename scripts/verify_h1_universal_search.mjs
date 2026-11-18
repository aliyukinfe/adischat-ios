import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const home = read('android/app/src/main/java/com/adischat/app/ui/home/SuperAppHomeScreen.kt');
const feed = read('android/app/src/main/java/com/adischat/app/ui/home/SuperAppPromotionalFeed.kt');
const caller = read('android/app/src/main/java/com/adischat/app/ui/home/HomeScreen.kt');
const search = read('android/app/src/main/java/com/adischat/app/ui/home/HomeUniversalSearch.kt');
const promotions = read('android/app/src/main/java/com/adischat/app/ui/home/SuperAppPromotionsScreen.kt');
const sidebar = read('android/app/src/main/java/com/adischat/app/ui/home/SuperAppSidebar.kt');
const main = read('android/app/src/main/java/com/adischat/app/ui/main/MainScreen.kt');
const chat = read('android/app/src/main/java/com/adischat/app/ui/chat/ChatScreen.kt');
const restaurantRoot = read('android/app/src/main/java/com/adischat/app/feature/restaurants/presentation/RestaurantsCustomerRootScreen.kt');

test('Home search is sticky, first and never routes to Explore', () => {
  assert.match(home, /\.align\(androidx\.compose\.ui\.Alignment\.TopEnd\)/);
  assert.match(home, /\.statusBarsPadding\(\)/);
  assert.match(home, /BasicTextField\(/);
  assert.match(home, /HomeUniversalSearchResults\(/);
  assert.match(home, /SuperAppPromotionalFeed\(/);
  assert.doesNotMatch(home, /onGoDiscover/);
  assert.match(home, /onOpenResult = onOpenSearchResult/);
  assert.doesNotMatch(feed, /item\(key = "search"\)/);
  assert.match(caller, /key\(appUser\?\.uid\)/);
});

test('each result opens its exact native destination', () => {
  for (const target of ['Chat', 'Community', 'Product', 'Store', 'Restaurant']) {
    assert.match(search, new RegExp(`HomeSearchTarget\\.${target}\\(`));
    assert.match(main, new RegExp(`is HomeSearchTarget\\.${target}`));
  }
  assert.match(main, /ShopRoute\.Product\(target\.product\)/);
  assert.match(main, /ShopRoute\.Store\(target\.store\)/);
  assert.match(main, /pendingHomeChatTarget = target/);
  assert.match(main, /pendingHomeRestaurantId = target\.restaurantId/);
  assert.match(chat, /vm\.openConversationByNotification\(target\.conversation\.id\)/);
  assert.match(chat, /vm\.openCommunity\(target\.room\)/);
  assert.match(restaurantRoot, /coordinator\.openRestaurantProfile\(restaurantId\)/);
  const restaurantInit = restaurantRoot.split('LaunchedEffect(currentFirebaseUid) {')[1]?.split('LaunchedEffect(flowState.destination) {')[0];
  assert.ok(restaurantInit);
  assert.ok(restaurantInit.indexOf('coordinator.reconcileAccountState()') < restaurantInit.indexOf('coordinator.loadDiscover()'));
  assert.match(restaurantInit, /coordinator\.loadDiscover\(\)\s*discoverInitialized = true/);
  assert.match(restaurantRoot, /LaunchedEffect\(initialSearchRestaurantId, currentFirebaseUid, discoverInitialized\)/);
  assert.match(restaurantRoot, /if \(!discoverInitialized\) return@LaunchedEffect/);
  assert.match(restaurantRoot, /coordinator\.openRestaurantProfile\(restaurantId\)\s*onInitialSearchRestaurantConsumed\(\)/);
  assert.match(restaurantRoot, /LaunchedEffect\(initialQrToken, currentFirebaseUid\)/);
});

test('search uses six existing read sources and does not mutate domain state', () => {
  for (const name of ['getConversationsOnce', 'searchCommunities("group"', 'searchCommunities("channel"', 'searchCatalogPage', 'searchApprovedStores', 'listLiveRestaurants']) {
    assert.ok(search.includes(name), name);
  }
  assert.doesNotMatch(search, /startConversation|createCommunity|submitDineInOrder|checkout|service_role/);
  assert.match(search, /userUid\.isNullOrBlank\(\)/);
  assert.match(search, /delay\(450\)/);
  assert.match(search, /catch \(cancelled: CancellationException\)/);
});

test('search restores feed and does not leak raw failures', () => {
  assert.match(home, /val feedListState = rememberLazyListState\(\)/);
  assert.match(home, /listState = feedListState/);
  assert.match(home, /searchOpen = false/);
  assert.match(search, /failedCategories/);
  assert.match(search, /Some categories could not load/);
  assert.doesNotMatch(search, /error\.message|Log\./);
});

test('Promotions uses measured content-height rail; native services retain safe inset', () => {
  assert.match(main, /shouldShowCompactSidebar && activeSidebarService == SuperAppService\.PROMOTIONS/);
  assert.match(main, /contentHeightOnly = true/);
  assert.match(main, /sidebarBounds = promotionsRailBounds/);
  assert.match(sidebar, /\.then\(if \(contentHeightOnly\) Modifier\.wrapContentHeight\(\) else Modifier\.fillMaxHeight\(\)\)/);
  assert.match(promotions, /FlowAroundRow\(sidebarBounds, 182\.dp, besideStart = 62\.dp\)/);
  assert.match(main, /else if \(shouldShowCompactSidebar\) \{\s*Row\(/);
});
