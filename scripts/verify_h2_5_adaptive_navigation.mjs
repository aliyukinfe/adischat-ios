import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve('c:/Users/adema/adischat-android');

function readFile(relPath) {
    return fs.readFileSync(path.join(ROOT, relPath), 'utf8');
}

test('1. ChatScreen TabDock is docked flush at bottom in Scaffold bottomBar', () => {
    const chatSrc = readFile('android/app/src/main/java/com/adischat/app/ui/chat/ChatScreen.kt');
    
    // Check Scaffold bottomBar contains TabDock
    assert.match(chatSrc, /bottomBar\s*=\s*\{\s*TabDock\(/, 'TabDock should be anchored in Scaffold bottomBar');
    
    const convScreen = chatSrc.slice(chatSrc.indexOf('fun ConversationsListScreen'), chatSrc.indexOf('fun ListScreenHeader'));

    // In ConversationsListScreen, TabDock is ONLY in bottomBar, not inside PullToRefreshBox
    assert.doesNotMatch(convScreen, /PullToRefreshBox[\s\S]*TabDock\(/, 'TabDock should not be inside PullToRefreshBox');
    
    // Check TabDock itself is anchored flush with navigationBarsPadding
    assert.match(chatSrc, /private\s+fun\s+TabDock[\s\S]*?Surface\([\s\S]*?\.navigationBarsPadding\(\)/, 'TabDock Surface must respect navigationBarsPadding');
    
    // Check empty black space padding was reduced
    assert.doesNotMatch(convScreen, /contentPadding\s*=\s*PaddingValues\(top\s*=\s*4\.dp,\s*bottom\s*=\s*92\.dp\)/, 'LazyColumn bottom padding should not be 92dp');
    assert.doesNotMatch(chatSrc, /contentPadding\s*=\s*PaddingValues\(start\s*=\s*0\.dp,\s*end\s*=\s*0\.dp,\s*top\s*=\s*10\.dp,\s*bottom\s*=\s*112\.dp\)/, 'Community LazyColumn bottom padding should not be 112dp');
});

test('2. SuperAppSavedScreen uses real ShopViewModel favorites and no QA banner', () => {
    const savedSrc = readFile('android/app/src/main/java/com/adischat/app/ui/home/SuperAppSavedScreen.kt');
    
    // Check QA banner is removed
    assert.doesNotMatch(savedSrc, /QA PREVIEW/i, 'QA banner must be removed');
    assert.doesNotMatch(savedSrc, /defaultSavedItems/, 'Fictional defaultSavedItems must be removed');
    
    // Check ShopViewModel integration
    assert.match(savedSrc, /ShopViewModel/, 'ShopViewModel must be referenced');
    assert.match(savedSrc, /loadFavorites\(\)/, 'loadFavorites() must be called');
    assert.match(savedSrc, /protectedState\.favorites/, 'Real favorites must be mapped');
    
    // Check professional empty state
    assert.match(savedSrc, /No saved items yet/, 'Clean empty state text must be present');
    assert.match(savedSrc, /Explore AdisGebeya/, 'Explore AdisGebeya button must be present');
});

test('3. ShopHeader adapts to sidebar presence across all screens', () => {
    const shopSrc = readFile('android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt');
    
    // Check ShopHeader has sidebar start padding
    assert.match(shopSrc, /internal\s+fun\s+ShopHeader[\s\S]*?val\s+sidebar\s*=\s*com\.adischat\.app\.ui\.home\.LocalSidebarBounds\.current/, 'ShopHeader must check LocalSidebarBounds');
    assert.match(shopSrc, /val\s+startPad\s*=\s*if\s*\(sidebar\s*!=\s*null\)\s*60\.dp\s*else\s*8\.dp/, 'ShopHeader startPad must adapt to sidebar');
});

test('4. CategoryProductsScreen implements AdaptiveFlowAround', () => {
    const shopSrc = readFile('android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt');
    
    assert.match(shopSrc, /private\s+fun\s+CategoryProductsScreen[\s\S]*?AdaptiveFlowAround\(besideStart\s*=\s*60\.dp,\s*belowStart\s*=\s*12\.dp\)/, 'CategoryProductsScreen must wrap content in AdaptiveFlowAround');
});

test('5. ProtectedPage adapts all 6 subpages with AdaptiveFlowAround', () => {
    const shopSrc = readFile('android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt');
    
    assert.match(shopSrc, /internal\s+fun\s+ProtectedPage[\s\S]*?AdaptiveFlowAround\(besideStart\s*=\s*60\.dp,\s*belowStart\s*=\s*16\.dp\)/, 'ProtectedPage must wrap content in AdaptiveFlowAround');
});

test('6. StoreDetailsScreen implements AdaptiveFlowAround', () => {
    const storeSrc = readFile('android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopStoreScreen.kt');
    
    assert.match(storeSrc, /internal\s+fun\s+StoreDetailsScreen[\s\S]*?AdaptiveFlowAround\(besideStart\s*=\s*60\.dp,\s*belowStart\s*=\s*10\.dp\)/, 'StoreDetailsScreen must implement AdaptiveFlowAround');
});

test('7. MarketplaceSearchScreen implements sidebar padding and AdaptiveFlowAround', () => {
    const searchSrc = readFile('android/app/src/main/java/com/adischat/app/feature/shop/presentation/MarketplaceSearchScreen.kt');
    
    assert.match(searchSrc, /val\s+sidebar\s*=\s*LocalSidebarBounds\.current/, 'MarketplaceSearchScreen header must check LocalSidebarBounds');
    assert.match(searchSrc, /AdaptiveFlowAround\(besideStart\s*=\s*60\.dp,\s*belowStart\s*=\s*12\.dp\)/, 'MarketplaceSearchScreen items must implement AdaptiveFlowAround');
});

test('8. MarketplaceVisualSearch implements sidebar padding and AdaptiveFlowAround', () => {
    const visualSrc = readFile('android/app/src/main/java/com/adischat/app/feature/shop/presentation/MarketplaceVisualSearch.kt');
    
    assert.match(visualSrc, /private\s+fun\s+VisualSearchTopBar[\s\S]*?val\s+sidebar\s*=\s*com\.adischat\.app\.ui\.home\.LocalSidebarBounds\.current/, 'VisualSearchTopBar must check LocalSidebarBounds');
    assert.match(visualSrc, /private\s+fun\s+VisualSearchResults[\s\S]*?AdaptiveFlowAround\(besideStart\s*=\s*60\.dp,\s*belowStart\s*=\s*8\.dp\)/, 'VisualSearchResults must implement AdaptiveFlowAround');
});

test('9. ShopTrackingScreen and SellerOrderDetailPage implement AdaptiveFlowAround', () => {
    const uxSrc = readFile('android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopOrderUxScreens.kt');
    
    assert.match(uxSrc, /internal\s+fun\s+SellerOrderDetailPage[\s\S]*?AdaptiveFlowAround\(besideStart\s*=\s*60\.dp,\s*belowStart\s*=\s*14\.dp\)/, 'SellerOrderDetailPage must implement AdaptiveFlowAround');
    assert.match(uxSrc, /internal\s+fun\s+ShopTrackingScreen[\s\S]*?AdaptiveFlowAround\(besideStart\s*=\s*60\.dp,\s*belowStart\s*=\s*14\.dp\)/, 'ShopTrackingScreen must implement AdaptiveFlowAround');
});

test('10. ShopHomeBrowseScreen and BuyerReviewsScreen implement sidebar adaptation', () => {
    const browseSrc = readFile('android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopHomeBrowseScreen.kt');
    const reviewSrc = readFile('android/app/src/main/java/com/adischat/app/feature/shop/presentation/BuyerReviewsScreen.kt');
    
    assert.match(browseSrc, /val\s+sidebar\s*=\s*com\.adischat\.app\.ui\.home\.LocalSidebarBounds\.current/, 'ShopHomeBrowseScreen must check LocalSidebarBounds');
    assert.match(reviewSrc, /val\s+sidebar\s*=\s*LocalSidebarBounds\.current/, 'BuyerReviewsScreen must check LocalSidebarBounds');
    assert.match(reviewSrc, /AdaptiveFlowAround\(besideStart\s*=\s*60\.dp,\s*belowStart\s*=\s*12\.dp\)/, 'BuyerReviewsScreen must implement AdaptiveFlowAround');
});
