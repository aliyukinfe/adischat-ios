package com.adischat.app.feature.restaurants.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adischat.app.BuildConfig
import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RestaurantsSession
import com.adischat.app.feature.restaurants.data.RestaurantsSessionMemory
import com.adischat.app.feature.shop.data.ShopProfileDto
import com.adischat.app.feature.shop.data.ShopSessionDto
import com.adischat.app.feature.shop.data.ShopSessionMemory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantsPresentationStateTest {

    @After
    fun tearDown() {
        RestaurantsSessionMemory.finishSignOut()
        RestaurantsSessionMemory.clear()
        ShopSessionMemory.finishSignOut()
        ShopSessionMemory.clear()
    }

    @Test
    fun `feature flags and identity bridge remain disabled in R3D`() {
        assertFalse(FeatureFlags.RESTAURANTS_DINE_IN_ENABLED)
        assertFalse(FeatureFlags.RESTAURANT_CENTER_ENABLED)
        assertFalse(FeatureFlags.RESTAURANT_ADMIN_ENABLED)
        assertFalse(BuildConfig.RESTAURANTS_IDENTITY_BRIDGE_ENABLED)

        val disabledDiscover = RestaurantsUiReducers.buildDiscoverStateFromLiveResult(
            environment = RestaurantsEnvironment(isDineInEnabled = false),
            liveResult = Result.success(RestaurantsPreviewFixtures.restaurants),
        )
        assertEquals(RestaurantsLoadState.Disabled, disabledDiscover.loadState)
        assertTrue(disabledDiscover.allRestaurants.isEmpty())
    }

    @Test
    fun `live failure enters error state without falling back to preview fixtures`() {
        val enabledEnv = RestaurantsEnvironment(isDineInEnabled = true)

        val loadingState = RestaurantsUiReducers.buildDiscoverStateFromLiveResult(
            environment = enabledEnv,
            liveResult = null,
        )
        assertEquals(RestaurantsLoadState.Loading, loadingState.loadState)

        val emptyState = RestaurantsUiReducers.buildDiscoverStateFromLiveResult(
            environment = enabledEnv,
            liveResult = Result.success(emptyList()),
        )
        assertEquals(RestaurantsLoadState.Empty, emptyState.loadState)

        val errorState = RestaurantsUiReducers.buildDiscoverStateFromLiveResult(
            environment = enabledEnv,
            liveResult = Result.failure(IllegalStateException("Network unavailable")),
        )
        assertEquals(RestaurantsLoadState.Error("Network unavailable"), errorState.loadState)
        assertTrue(errorState.allRestaurants.isEmpty())
    }

    @Test
    fun `discover search and cuisine filters narrow preview restaurants accurately`() {
        val base = RestaurantsPreviewFixtures.discoverState(isDineInEnabled = true)
        assertEquals(4, base.filteredRestaurants.size)

        val ethiopianOnly = base.copy(selectedCuisineFilter = "Ethiopian").filteredRestaurants
        assertEquals(listOf("Habesha Hearth"), ethiopianOnly.map { it.brandName })

        val byArea = base.copy(searchQuery = "kazanchis").filteredRestaurants
        assertEquals(listOf("Kaffa Roastery & Bistro"), byArea.map { it.brandName })

        val noneFound = base.copy(searchQuery = "nonexistent-dish-xyz").filteredRestaurants
        assertTrue(noneFound.isEmpty())
    }

    @Test
    fun `branch selection updates dine-in availability and branch override prices`() {
        val boleMenu = RestaurantsPreviewFixtures.menuState(
            restaurantId = "prev-rest-habesha",
            branchId = "prev-br-bole",
        )
        assertTrue(boleMenu.isBrowseOnly)
        assertEquals("Bole Atlas Flagship", boleMenu.selectedBranch?.name)

        val boleTibs = boleMenu.items.first { it.id == "item-shekla-tibs" }
        val boleDoro = boleMenu.items.first { it.id == "item-doro-wot" }
        assertEquals(68000L, boleTibs.effectivePriceMinor)
        assertTrue(boleDoro.isAvailableAtBranch)

        val cmcMenu = RestaurantsPreviewFixtures.menuState(
            restaurantId = "prev-rest-habesha",
            branchId = "prev-br-cmс",
        )
        assertEquals("CMC Courtyard", cmcMenu.selectedBranch?.name)
        val cmcTibs = cmcMenu.items.first { it.id == "item-shekla-tibs" }
        val cmcDoro = cmcMenu.items.first { it.id == "item-doro-wot" }
        assertEquals(71000L, cmcTibs.effectivePriceMinor)
        assertFalse(cmcDoro.isAvailableAtBranch)

        // Unknown branch ID falls back safely to first active branch
        val resolvedFallback = RestaurantsUiReducers.resolveSelectedBranch(
            boleMenu.branches,
            selectedBranchId = "missing-branch",
        )
        assertEquals("prev-br-bole", resolvedFallback?.id)
    }

    @Test
    fun `menu category filtering selects specific categories or shows all`() {
        val menu = RestaurantsPreviewFixtures.menuState(
            restaurantId = "prev-rest-habesha",
            branchId = "prev-br-bole",
        )
        assertEquals(10, menu.categories.size)
        assertEquals(18, menu.visibleItems.size)

        val sectionMap = MenuSectionIndexMap.build(menu.categories, menu.items)
        assertEquals(10, sectionMap.categories.size)
        assertEquals(28, sectionMap.entries.size)
        assertTrue(menu.items.any { !it.isAvailableAtBranch })
        assertTrue(menu.items.any { !it.hasRequiredOptions() })
        assertTrue(menu.items.any { it.hasRequiredOptions() })

        val filtered = menu.copy(selectedCategoryId = "cat-sides").visibleItems
        assertEquals(listOf("Royal Beyaynetu Mosaic"), filtered.map { it.name })
    }

    @Test
    fun `option selection enforces single-select, multi-select cap, unavailable guard, and quantity preview`() {
        val sheklaTibs = RestaurantsPreviewFixtures.menuItemsFor(
            restaurantId = "prev-rest-habesha",
            branchId = "prev-br-bole",
        ).first { it.id == "item-shekla-tibs" }

        var sheet = MenuItemSheetState.initialFor(sheklaTibs)
        assertTrue(sheet.isBrowseOnly)
        assertTrue(sheet.isSelectionValid())
        assertTrue(sheet.missingRequiredGroups().isEmpty())
        assertEquals(68000L, sheet.previewUnitTotalMinor())

        // Single-select required group replaces previous option rather than deselecting below 1
        sheet = sheet.toggleOption("og-portion", "opt-share")
        assertEquals(setOf("opt-share"), sheet.selectedOptionsByGroup["og-portion"])
        assertEquals(106000L, sheet.previewUnitTotalMinor()) // 680 + 380 ETB

        // Unavailable option cannot be selected
        sheet = sheet.toggleOption("og-extras", "opt-injera")
        assertFalse(sheet.selectedOptionsByGroup["og-extras"].orEmpty().contains("opt-injera"))

        // Multi-select up to maxSelect = 2
        sheet = sheet.toggleOption("og-extras", "opt-ayib")
        sheet = sheet.toggleOption("og-extras", "opt-kocho")
        assertEquals(setOf("opt-ayib", "opt-kocho"), sheet.selectedOptionsByGroup["og-extras"])
        assertEquals(123000L, sheet.previewUnitTotalMinor()) // 1060 + 95 + 75 ETB

        // Quantity preview clamping [1..20] and line total computation
        sheet = sheet.decrementQuantity()
        assertEquals(1, sheet.quantityPreview)
        sheet = sheet.incrementQuantity().incrementQuantity()
        assertEquals(3, sheet.quantityPreview)
        assertEquals(369000L, sheet.previewLineTotalMinor())

        // Unsatisfied required group is reported accurately
        val incomplete = MenuItemSheetState(item = sheklaTibs, selectedOptionsByGroup = emptyMap())
        assertFalse(incomplete.isSelectionValid())
        assertEquals(2, incomplete.missingRequiredGroups().size)
        assertEquals("ETB 680", RestaurantsUiReducers.formatEtbMinor(68000L))
        assertEquals("+ETB 95", RestaurantsUiReducers.formatPriceDeltaMinor(9500L))
    }

    @Test
    fun `restaurants session sign-out stays strictly isolated from shop session memory`() {
        ShopSessionMemory.clear()
        RestaurantsSessionMemory.clear()

        val shopGen = ShopSessionMemory.generation()
        assertTrue(
            ShopSessionMemory.updateIfCurrent(
                shopGen,
                ShopSessionDto(accessToken = "shop-access", refreshToken = "shop-refresh"),
                ShopProfileDto(adisChatUserId = "uid-shared"),
            ),
        )

        val restGen = RestaurantsSessionMemory.generation()
        assertTrue(
            RestaurantsSessionMemory.setSessionIfCurrent(
                RestaurantsSession(
                    ownerAdisChatUid = "uid-shared",
                    profileId = "rest-prof-1",
                    accessToken = "rest-access",
                    refreshToken = "rest-refresh",
                ),
                restGen,
            ),
        )

        // Clearing Restaurants credentials must not disturb Shop credentials
        RestaurantsSessionMemory.beginSignOut()
        assertNull(RestaurantsSessionMemory.snapshot())
        assertNotNull(ShopSessionMemory.session())
        assertEquals("shop-access", ShopSessionMemory.session()?.accessToken)

        RestaurantsSessionMemory.finishSignOut()
        assertEquals("uid-shared", ShopSessionMemory.profile()?.adisChatUserId)
    }

    @Test
    fun `menu section index map skips empty categories preserves sort order and maps headers and items back to categories`() {
        val categories = listOf(
            MenuCategoryUiModel(id = "cat-burgers", name = "Burgers", sortOrder = 3),
            MenuCategoryUiModel(id = "cat-empty", name = "Seasonal Specials", sortOrder = 2),
            MenuCategoryUiModel(id = "cat-juice", name = "Fresh Juice", sortOrder = 1),
            MenuCategoryUiModel(id = "cat-shakes", name = "Shakes", sortOrder = 1), // Tie on sortOrder -> preserves order
        )
        val items = listOf(
            MenuItemUiModel(id = "i-b1", restaurantId = "r1", categoryId = "cat-burgers", name = "Classic Burger", description = "", basePriceMinor = 45000L),
            MenuItemUiModel(id = "i-b2", restaurantId = "r1", categoryId = "cat-burgers", name = "Cheese Burger", description = "", basePriceMinor = 49000L),
            MenuItemUiModel(id = "i-j1", restaurantId = "r1", categoryId = "cat-juice", name = "Mango Juice", description = "", basePriceMinor = 15000L),
            MenuItemUiModel(id = "i-j2", restaurantId = "r1", categoryId = "cat-juice", name = "Orange Juice", description = "", basePriceMinor = 16000L),
            MenuItemUiModel(id = "i-s1", restaurantId = "r1", categoryId = "cat-shakes", name = "Vanilla Shake", description = "", basePriceMinor = 22000L),
        )

        val map = MenuSectionIndexMap.build(categories = categories, items = items)

        // Empty "Seasonal Specials" skipped; ties on sortOrder=1 keep Fresh Juice before Shakes
        assertEquals(listOf("cat-juice", "cat-shakes", "cat-burgers"), map.categories.map { it.id })
        assertEquals(
            listOf(
                "cat_cat-juice",
                "item_i-j1",
                "item_i-j2",
                "cat_cat-shakes",
                "item_i-s1",
                "cat_cat-burgers",
                "item_i-b1",
                "item_i-b2",
            ),
            map.entries.map { it.key },
        )
        assertEquals(0, map.startIndexForCategory("cat-juice"))
        assertEquals(3, map.startIndexForCategory("cat-shakes"))
        assertEquals(5, map.startIndexForCategory("cat-burgers"))
        assertNull(map.startIndexForCategory("cat-empty"))

        // Item lookup and index -> category mapping
        assertEquals(2, map.indexForItem("i-j2"))
        assertEquals("cat-juice", map.categoryIdForIndex(1))
        assertEquals("cat-juice", map.categoryIdForIndex(2))
        assertEquals("cat-shakes", map.categoryIdForIndex(4))
        assertEquals("cat-burgers", map.categoryIdForIndex(6))
        assertEquals("cat-burgers", map.categoryIdForIndex(7))
        assertNull(map.categoryIdForIndex(99))

        // Completely empty items list produces empty map even when categories exist
        assertTrue(MenuSectionIndexMap.build(categories = categories, items = emptyList()).isEmpty)
    }

    @Test
    fun `light theme palette exposes specified Philia-inspired white warm sidebar and AdisChat green tokens`() {
        assertEquals(Color(0xFFFFFFFF), RestaurantsLightPalette.MainBackground)
        assertEquals(Color(0xFFF8F6F2), RestaurantsLightPalette.SidebarBackground)
        assertEquals(Color(0xFF202124), RestaurantsLightPalette.PrimaryText)
        assertEquals(Color(0xFF5F6368), RestaurantsLightPalette.SecondaryText)
        assertEquals(Color(0xFF16A34A), RestaurantsLightPalette.BrandGreen)
        assertEquals(Color(0xFFEAECEF), RestaurantsLightPalette.CardBorder)

        assertEquals(RestaurantsLightPalette.MainBackground, RestaurantsLightColorScheme.background)
        assertEquals(RestaurantsLightPalette.SidebarBackground, RestaurantsLightColorScheme.surfaceContainerLow)
        assertEquals(RestaurantsLightPalette.PrimaryText, RestaurantsLightColorScheme.onBackground)
        assertEquals(RestaurantsLightPalette.SecondaryText, RestaurantsLightColorScheme.onSurfaceVariant)
        assertEquals(RestaurantsLightPalette.BrandGreen, RestaurantsLightColorScheme.primary)
        assertEquals(RestaurantsLightPalette.CardBorder, RestaurantsLightColorScheme.outline)
    }

    @Test
    fun `continuous scroll sync updates active category cleanly downward upward and for short final categories`() {
        val categories = listOf(
            MenuCategoryUiModel("cat-juice", "Fresh Juice", sortOrder = 1),
            MenuCategoryUiModel("cat-shakes", "Shakes", sortOrder = 2),
            MenuCategoryUiModel("cat-dessert", "Dessert", sortOrder = 3),
        )
        val items = listOf(
            MenuItemUiModel("j1", "r1", "cat-juice", "Mango Juice", "", 15000L),
            MenuItemUiModel("j2", "r1", "cat-juice", "Orange Juice", "", 15000L),
            MenuItemUiModel("s1", "r1", "cat-shakes", "Berry Shake", "", 22000L),
            MenuItemUiModel("s2", "r1", "cat-shakes", "Vanilla Shake", "", 22000L),
            MenuItemUiModel("d1", "r1", "cat-dessert", "Tiramisu", "", 28000L),
        )
        val map = MenuSectionIndexMap.build(categories, items)
        // Indices: 0=hdr_juice, 1=j1, 2=j2, 3=hdr_shakes, 4=s1, 5=s2, 6=hdr_dessert, 7=d1

        // 1. At very top of scrollable list -> highlights first category
        assertEquals(
            "cat-juice",
            resolveVisibleActiveCategoryId(
                sectionMap = map,
                firstMeaningfulIndex = 0,
                lastVisibleIndex = 4,
                canScrollForward = true,
                canScrollBackward = false,
            ),
        )
        // 2. Scrolled down into Shakes -> highlights Shakes
        assertEquals(
            "cat-shakes",
            resolveVisibleActiveCategoryId(
                sectionMap = map,
                firstMeaningfulIndex = 4,
                lastVisibleIndex = 6,
                canScrollForward = true,
                canScrollBackward = true,
            ),
        )
        // 3. Reached very bottom -> short final category Dessert highlights even if Shakes is still at top
        assertEquals(
            "cat-dessert",
            resolveVisibleActiveCategoryId(
                sectionMap = map,
                firstMeaningfulIndex = 4,
                lastVisibleIndex = 7,
                canScrollForward = false,
                canScrollBackward = true,
            ),
        )
        // 4. Scrolling upward back to Juice restores Juice cleanly
        assertEquals(
            "cat-juice",
            resolveVisibleActiveCategoryId(
                sectionMap = map,
                firstMeaningfulIndex = 2,
                lastVisibleIndex = 5,
                canScrollForward = true,
                canScrollBackward = true,
            ),
        )
        // 5. Non-scrollable short list (!canScrollForward && !canScrollBackward) stays on top category at rest
        assertEquals(
            "cat-juice",
            resolveVisibleActiveCategoryId(
                sectionMap = map,
                firstMeaningfulIndex = 0,
                lastVisibleIndex = 7,
                canScrollForward = false,
                canScrollBackward = false,
            ),
        )

        // Adaptive sidebar width & category fallback icon keys
        assertEquals(80.dp, resolveCategorySidebarWidth(320.dp))
        assertEquals(89.7.dp.value, resolveCategorySidebarWidth(390.dp).value, 0.2f)
        assertEquals(124.dp, resolveCategorySidebarWidth(720.dp))
        assertEquals("juice", resolveCategoryFallbackIconKey(null, "Fresh Juice"))
        assertEquals("cafe", resolveCategoryFallbackIconKey(null, "Coffee & Honey Drinks"))
        assertEquals("burger", resolveCategoryFallbackIconKey(null, "Burgers"))
        assertEquals("pizza", resolveCategoryFallbackIconKey(null, "Artisan Pizza"))
    }

    @Test
    fun `menu search spotlight filters and demo voucher fixtures preserve strict metadata boundaries`() {
        val menu = RestaurantsPreviewFixtures.menuState(
            restaurantId = "prev-rest-habesha",
            branchId = "prev-br-bole",
        )
        val habeshaCard = RestaurantsPreviewFixtures.restaurants.first { it.id == "prev-rest-habesha" }
        assertNull(habeshaCard.ratingLabel)
        assertTrue(habeshaCard.isHighlightBadgeDemo)
        assertTrue(menu.promotions.isNotEmpty())
        assertTrue(menu.promotions.all { it.isDemoFixture })

        val spotlights = RestaurantsUiReducers.availableSpotlights(menu.items)
        assertTrue(spotlights.contains(MenuSpotlightFilter.DEALS))
        assertTrue(spotlights.contains(MenuSpotlightFilter.CHEFS_PICK))
        assertTrue(spotlights.contains(MenuSpotlightFilter.HOT_SELLERS))

        val mangoSearch = RestaurantsUiReducers.filterMenuItemsForDisplay(
            items = menu.items,
            searchQuery = "mango",
        )
        assertEquals(listOf("item-mango-juice"), mangoSearch.map { it.id })

        val chefsPicks = RestaurantsUiReducers.filterMenuItemsForDisplay(
            items = menu.items,
            spotlightFilter = MenuSpotlightFilter.CHEFS_PICK,
        )
        assertTrue(chefsPicks.isNotEmpty())
        assertTrue(chefsPicks.all { it.spotlightTags.contains("chefs_pick") })
    }

    @Test
    fun `r3 app integration guards block production default and require configured backend for live flow`() {
        val defaultEnv = RestaurantsEnvironment()
        assertFalse(FeatureFlags.RESTAURANTS_DINE_IN_ENABLED)
        assertFalse(FeatureFlags.RESTAURANT_CENTER_ENABLED)
        assertFalse(RestaurantsAppIntegrationGuards.isCustomerDineInFlowAllowed(defaultEnv))
        assertFalse(RestaurantsAppIntegrationGuards.isBackendReadyForLiveCustomerFlow(defaultEnv))
        assertFalse(RestaurantsAppIntegrationGuards.isRestaurantCenterEntryAllowed(defaultEnv))

        val offlinePreviewEnv = RestaurantsEnvironment(
            supabaseUrl = "https://example.supabase.co",
            supabasePublishableKey = "pk_test",
            identityBridgeUrl = "https://bridge.example.com/assertion",
            isIdentityBridgeEnabled = true,
            isDineInEnabled = true,
            isRestaurantCenterEnabled = true,
            isOfflinePreview = true,
        )
        assertTrue(RestaurantsAppIntegrationGuards.isCustomerDineInFlowAllowed(offlinePreviewEnv))
        assertFalse(RestaurantsAppIntegrationGuards.isBackendReadyForLiveCustomerFlow(offlinePreviewEnv))
        assertTrue(RestaurantsAppIntegrationGuards.isRestaurantCenterEntryAllowed(offlinePreviewEnv))

        val qaUnconfiguredEnv = RestaurantsEnvironment(
            isDineInEnabled = true,
            isRestaurantCenterEnabled = true,
            isQaBuild = true,
        )
        assertTrue(RestaurantsAppIntegrationGuards.isCustomerDineInFlowAllowed(qaUnconfiguredEnv))
        assertFalse(RestaurantsAppIntegrationGuards.isBackendReadyForLiveCustomerFlow(qaUnconfiguredEnv))

        val qaConfiguredEnv = qaUnconfiguredEnv.copy(
            supabaseUrl = "https://qa.supabase.co",
            supabasePublishableKey = "pk_qa",
            identityBridgeUrl = "https://qa.workers.dev/assertion",
            isIdentityBridgeEnabled = true,
        )
        assertTrue(RestaurantsAppIntegrationGuards.isBackendReadyForLiveCustomerFlow(qaConfiguredEnv))
    }
}
