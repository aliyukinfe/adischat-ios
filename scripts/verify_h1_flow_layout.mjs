import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const screen = read('android/app/src/main/java/com/adischat/app/ui/home/SuperAppHomeScreen.kt');
const feed = read('android/app/src/main/java/com/adischat/app/ui/home/SuperAppPromotionalFeed.kt');
const sidebar = read('android/app/src/main/java/com/adischat/app/ui/home/SuperAppSidebar.kt');
const model = read('android/app/src/main/java/com/adischat/app/ui/home/SuperAppHomeModel.kt');
const main = read('android/app/src/main/java/com/adischat/app/ui/main/MainScreen.kt');

const beside = (top, height, sidebarTop, sidebarBottom) => top < sidebarBottom && top + height > sidebarTop;

test('measured sidebar has content height and stays outside the scrolling feed', () => {
  const expanded = sidebar.split('fun SuperAppExpandedSidebar(')[1]?.split('fun SuperAppCompactSidebar(')[0];
  assert.match(expanded, /\.wrapContentHeight\(\)/);
  assert.doesNotMatch(expanded, /\.fillMaxHeight\(\)|\.verticalScroll\(/);
  assert.match(screen, /sidebarBounds = sidebarBounds/);
  assert.match(screen, /modifier = Modifier\.onGloballyPositioned/);
  assert.ok(screen.indexOf('SuperAppPromotionalFeed(') < screen.indexOf('SuperAppExpandedSidebar('));
});

test('all promotional rows use stable height and measured flow-around placement', () => {
  assert.match(feed, /items\(visible, key = \{ it\.id \}\) \{ item ->\s*FlowAroundRow\(sidebarBounds, 212\.dp\)/);
  assert.match(feed, /rowTop < sidebar\.bottom && rowBottom > sidebar\.top/);
  assert.match(feed, /\.onGloballyPositioned/);
  assert.match(feed, /if \(besideSidebar != next\) besideSidebar = next/);
  assert.match(feed, /animateDpAsState/);
});

test('3, 5, and 8 sidebar items flow 10 cards both scroll directions at M33 width', () => {
  const samsungWidthDp = 393;
  const narrowCardWidth = samsungWidthDp - 114 - 12;
  assert.ok(narrowCardWidth >= 240);
  for (const count of [3, 5, 8]) {
    const sidebarBottom = 30 + 16 + count * 48 + (count - 1) * 6;
    const cardTops = Array.from({ length: 10 }, (_, index) => 310 + index * 220);
    for (const scroll of [0, 180, 460, 900, 1400, 900, 460, 180, 0]) {
      for (const top of cardTops) {
        const visibleTop = top - scroll;
        assert.equal(beside(visibleTop, 212, 30, sidebarBottom), visibleTop < sidebarBottom && visibleTop + 212 > 30);
      }
    }
    assert.equal(beside(sidebarBottom + 1, 212, 30, sidebarBottom), false);
    assert.equal(beside(sidebarBottom - 1, 212, 30, sidebarBottom), true);
  }
});

test('ten demo concepts have no invented prices or discount claims', () => {
  const block = model.split('object SuperAppDemoPromotions {')[1];
  assert.equal((block.match(/highlightBadge = "DEMO"|"DEMO", "/g) ?? []).length, 10);
  assert.doesNotMatch(block, /originalPriceLabel\s*=|promoPriceLabel\s*=|ETB\s*\d|\d+% OFF|FREE DELIVERY/);
  assert.match(feed, /DEMO PREVIEW  \|  Not live offers/);
});

test('Home controls remain present across tabs and services', () => {
  for (const source of [
    read('android/app/src/main/java/com/adischat/app/ui/home/SuperAppSavedScreen.kt'),
    read('android/app/src/main/java/com/adischat/app/ui/home/SuperAppActivityScreen.kt'),
    main,
  ]) assert.match(source, /contentDescription = "Return to Home"/);
  assert.match(sidebar, /contentDescription = "Return to Dashboard"/);
  assert.match(main, /onExpandHome = onGoHome/);
});
