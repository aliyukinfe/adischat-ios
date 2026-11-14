package com.adischat.app.feature.restaurants.qa

import com.adischat.app.BuildConfig
import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.admin.RestaurantAdminTab
import com.adischat.app.feature.restaurants.center.CenterUploadPhase
import com.adischat.app.feature.restaurants.center.RestaurantCenterTab
import com.adischat.app.feature.restaurants.center.RestaurantStaffRole
import com.adischat.app.feature.restaurants.center.RestaurantsPendingPickerTarget
import com.adischat.app.feature.restaurants.center.RestaurantsPickerMetadataHelper
import com.adischat.app.feature.restaurants.data.RestaurantsBinaryUploadSource
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RestaurantsUploadKind
import com.adischat.app.feature.restaurants.presentation.ActiveDiningSessionUi
import com.adischat.app.feature.restaurants.presentation.CartCheckoutPhase
import com.adischat.app.feature.restaurants.presentation.CheckoutSheetStep
import com.adischat.app.feature.restaurants.presentation.DineInOrderMode
import com.adischat.app.feature.restaurants.presentation.OrderTrackingSyncState
import com.adischat.app.feature.restaurants.presentation.RestaurantOrderIsolationReducers
import com.adischat.app.feature.restaurants.presentation.RestaurantOrderStatus
import com.adischat.app.feature.restaurants.presentation.RestaurantWorkspaceTab
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadState
import com.adischat.app.feature.restaurants.presentation.TableScanPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class RestaurantsQaPreviewTest {

    private fun qaOfflineEnvironment(): RestaurantsEnvironment = RestaurantsEnvironment(
        supabaseUrl = "",
        supabasePublishableKey = "",
        identityBridgeUrl = "",
        isIdentityBridgeEnabled = false,
        isDineInEnabled = true,
        isRestaurantCenterEnabled = true,
        isAdminEnabled = true,
        isQaBuild = true,
        areQaOrderMutationsEnabled = false,
    )

    private fun qaConnectedEnvironment(): RestaurantsEnvironment = RestaurantsEnvironment(
        supabaseUrl = "https://qa-restaurants.supabase.co",
        supabasePublishableKey = "sb_publishable_qa_key",
        identityBridgeUrl = "https://qa-worker.example.com/api/restaurants/assertion",
        isIdentityBridgeEnabled = true,
        isDineInEnabled = true,
        isRestaurantCenterEnabled = true,
        isAdminEnabled = true,
        isQaBuild = true,
        areQaOrderMutationsEnabled = false,
    )

    @Test
    fun `feature flags remain disabled in non-qa variants including debug and release`() {
        assertFalse(BuildConfig.QA_BUILD)
        assertFalse(BuildConfig.OFFLINE_QA_PREVIEW)
        assertFalse(FeatureFlags.RESTAURANTS_DINE_IN_ENABLED)
        assertFalse(FeatureFlags.RESTAURANT_CENTER_ENABLED)
        assertFalse(FeatureFlags.RESTAURANT_ADMIN_ENABLED)
        assertFalse(FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED)
    }

    @Test
    fun `offline preview environment blocks live backend and bridge connections and labels clearly`() {
        val offlinePreviewWithUrls = RestaurantsEnvironment(
            supabaseUrl = "https://should-never-connect.supabase.co",
            supabasePublishableKey = "ignored_key",
            identityBridgeUrl = "https://should-never-connect.workers.dev/assertion",
            isIdentityBridgeEnabled = true,
            isDineInEnabled = true,
            isRestaurantCenterEnabled = true,
            isAdminEnabled = true,
            isQaBuild = false,
            isOfflinePreview = true,
            areQaOrderMutationsEnabled = true,
        )
        assertFalse(offlinePreviewWithUrls.isBackendConfigured())
        assertFalse(offlinePreviewWithUrls.isIdentityBridgeConfigured())
        assertFalse(offlinePreviewWithUrls.isStagingConnected())
        assertTrue(offlinePreviewWithUrls.areOrderMutationsBlockedInQa())
        assertTrue(
            offlinePreviewWithUrls.stagingStatusMessage().contains(
                RestaurantsEnvironment.OFFLINE_QA_PREVIEW_LABEL,
            ),
        )

        val controller = RestaurantsQaPreviewController(offlinePreviewWithUrls)
        assertTrue(controller.state.value.isQaAllowed)
        assertFalse(controller.state.value.isStagingConnected)
        assertTrue(
            controller.state.value.stagingBannerText.contains(
                RestaurantsEnvironment.OFFLINE_QA_PREVIEW_LABEL,
            ),
        )
        controller.onCenterBackendActionBlocked("Order transition")
        assertTrue(
            controller.state.value.centerState.errorBanner.orEmpty().contains(
                RestaurantsEnvironment.OFFLINE_QA_PREVIEW_LABEL,
            ),
        )
    }

    @Test
    fun `restaurants environment explicitly reports staging connection status`() {
        val offline = qaOfflineEnvironment()
        assertFalse(offline.isStagingConnected())
        assertEquals("Staging backend not connected", offline.stagingStatusMessage())
        assertEquals(
            "Staging backend not connected (Order submission unavailable offline)",
            offline.stagingStatusMessage("Order submission"),
        )

        val connected = qaConnectedEnvironment()
        assertTrue(connected.isStagingConnected())
    }

    @Test
    fun `qa controller stays blocked and disabled when isQaBuild is false`() {
        val controller = RestaurantsQaPreviewController(
            environment = RestaurantsEnvironment(
                isDineInEnabled = true,
                isRestaurantCenterEnabled = true,
                isAdminEnabled = true,
                isQaBuild = false,
            ),
        )

        val initial = controller.state.value
        assertFalse(initial.isQaAllowed)
        assertEquals(RestaurantsLoadState.Disabled, initial.discoverState.loadState)
        assertEquals(RestaurantsLoadState.Disabled, initial.centerState.loadState)
        assertEquals(RestaurantsLoadState.Disabled, initial.adminState.loadState)

        controller.openRoute(RestaurantsQaRoute.DISCOVER)
        assertEquals(RestaurantsQaRoute.HUB, controller.state.value.currentRoute)
    }

    @Test
    fun `qa navigation reaches unified customer workspace center and admin flows and unwinds cleanly`() {
        val controller = RestaurantsQaPreviewController(qaOfflineEnvironment())
        assertTrue(controller.state.value.isQaAllowed)
        assertFalse(controller.state.value.isStagingConnected)
        assertTrue(
            controller.state.value.stagingBannerText.contains(
                RestaurantsEnvironment.STAGING_NOT_CONNECTED_MESSAGE,
            ),
        )

        controller.openRoute(RestaurantsQaRoute.DISCOVER)
        assertEquals(RestaurantsQaRoute.DISCOVER, controller.state.value.currentRoute)
        controller.onDiscoverSearchChange("Habesha")
        assertEquals(1, controller.state.value.discoverState.filteredRestaurants.size)

        controller.openRestaurantWorkspace(RestaurantsQaSampleData.QA_RESTAURANT_ID)
        assertEquals(RestaurantsQaRoute.WORKSPACE, controller.state.value.currentRoute)
        assertEquals(RestaurantsQaSampleData.QA_RESTAURANT_ID, controller.state.value.selectedRestaurantId)
        val firstItem = controller.state.value.menuState.items.first()
        controller.openMenuItemSheet(firstItem)
        assertNotNull(controller.state.value.activeMenuItemSheet)
        // Pressing Back dismisses the active item sheet first before exiting workspace
        assertTrue(controller.navigateBack())
        assertEquals(null, controller.state.value.activeMenuItemSheet)
        assertEquals(RestaurantsQaRoute.WORKSPACE, controller.state.value.currentRoute)

        controller.openMenuItemSheet(firstItem)
        val beforeCount = controller.state.value.cartState.totalItemCount
        controller.addSheetItemToPreviewCart()
        assertTrue(controller.state.value.cartState.totalItemCount > beforeCount)

        controller.openWorkspaceCart()
        assertTrue(controller.state.value.isWorkspaceCartOpen)
        assertTrue(controller.navigateBack())
        assertFalse(controller.state.value.isWorkspaceCartOpen)

        controller.selectWorkspaceTab(RestaurantWorkspaceTab.MY_ORDERS)
        val habeshaOrder = RestaurantOrderIsolationReducers.filterActiveOrdersForRestaurant(
            orders = controller.state.value.workspaceOrders,
            restaurantId = RestaurantsQaSampleData.QA_RESTAURANT_ID,
            customerUid = RestaurantsQaSampleData.QA_CUSTOMER_UID,
        ).first()
        controller.openWorkspaceOrderTracking(habeshaOrder.orderId)
        assertEquals(habeshaOrder.orderId, controller.state.value.selectedWorkspaceOrderId)
        assertTrue(controller.navigateBack())
        assertEquals(null, controller.state.value.selectedWorkspaceOrderId)

        // Opening Table Scan from Workspace returns straight back to Workspace
        controller.openTableScan()
        assertEquals(RestaurantsQaRoute.TABLE_SCAN, controller.state.value.currentRoute)
        assertTrue(controller.navigateBack())
        assertEquals(RestaurantsQaRoute.WORKSPACE, controller.state.value.currentRoute)

        controller.openCenterTab(RestaurantCenterTab.APPLICATION)
        assertEquals(RestaurantsQaRoute.CENTER, controller.state.value.currentRoute)
        assertEquals(RestaurantCenterTab.APPLICATION, controller.state.value.centerState.selectedTab)

        controller.onCenterTabSelected(RestaurantCenterTab.MENU)
        controller.onCenterTabSelected(RestaurantCenterTab.SETTINGS)
        controller.onCenterTabSelected(RestaurantCenterTab.ORDERS)
        controller.onCenterRolePreviewSelected(RestaurantStaffRole.KITCHEN_STAFF)
        assertEquals(RestaurantStaffRole.KITCHEN_STAFF, controller.state.value.centerState.activeRole)

        controller.openAdminTab(RestaurantAdminTab.APPLICATIONS)
        assertEquals(RestaurantsQaRoute.ADMIN, controller.state.value.currentRoute)
        controller.onAdminTabSelected(RestaurantAdminTab.RESTAURANTS)
        assertEquals(RestaurantAdminTab.RESTAURANTS, controller.state.value.adminState.selectedTab)

        while (controller.navigateBack()) {
            // Unwind back stack to QA Hub
        }
        assertEquals(RestaurantsQaRoute.HUB, controller.state.value.currentRoute)
    }

    @Test
    fun `backend dependent actions report staging not connected without simulating success`() {
        val controller = RestaurantsQaPreviewController(qaOfflineEnvironment())

        controller.openRestaurantWorkspace(RestaurantsQaSampleData.QA_RESTAURANT_ID)
        controller.openWorkspaceCart()
        controller.onSubmitDineInOrder()
        val checkoutPhase = controller.state.value.cartState.checkoutPhase
        assertTrue(checkoutPhase is CartCheckoutPhase.Failed)
        assertTrue(
            (checkoutPhase as CartCheckoutPhase.Failed).message.contains(
                RestaurantsEnvironment.STAGING_NOT_CONNECTED_MESSAGE,
            ),
        )

        val secretToken = "raw-qr-token-should-never-surface"
        controller.openTableScan()
        controller.onQrTokenScanned(secretToken)
        val scanPhase = controller.state.value.tableScanState.phase
        assertTrue(scanPhase is TableScanPhase.Error)
        val scanErr = (scanPhase as TableScanPhase.Error).message
        assertTrue(scanErr.contains(RestaurantsEnvironment.STAGING_NOT_CONNECTED_MESSAGE))
        assertFalse(scanErr.contains(secretToken))

        controller.onRefreshOrderTracking()
        val syncState = controller.state.value.orderTrackingState.syncState
        assertTrue(syncState is OrderTrackingSyncState.OfflineOrReconnecting)
        assertTrue(
            (syncState as OrderTrackingSyncState.OfflineOrReconnecting).message.contains(
                RestaurantsEnvironment.STAGING_NOT_CONNECTED_MESSAGE,
            ),
        )

        controller.openCenterTab(RestaurantCenterTab.APPLICATION)
        controller.onCenterBackendActionBlocked("Submit restaurant application")
        assertTrue(
            controller.state.value.centerState.errorBanner.orEmpty().contains(
                RestaurantsEnvironment.STAGING_NOT_CONNECTED_MESSAGE,
            ),
        )
        // Center renders its own pinned errorBanner, so bannerNotice must stay null (no duplicate banner)
        assertEquals(null, controller.state.value.bannerNotice)

        controller.openAdminTab(RestaurantAdminTab.APPLICATIONS)
        controller.onAdminBackendActionBlocked("Approve restaurant application")
        assertTrue(
            controller.state.value.adminState.errorBanner.orEmpty().contains(
                RestaurantsEnvironment.STAGING_NOT_CONNECTED_MESSAGE,
            ),
        )
        // Admin renders its own pinned errorBanner, so bannerNotice must stay null (no duplicate banner)
        assertEquals(null, controller.state.value.bannerNotice)

        // QA Hub shortcut resets backStack to [HUB] without accumulating duplicate HUB entries
        controller.openRoute(RestaurantsQaRoute.HUB)
        assertEquals(listOf(RestaurantsQaRoute.HUB), controller.state.value.backStack)
        assertFalse(controller.navigateBack())
    }

    @Test
    fun `multi-restaurant isolation separates carts sessions active orders and history per restaurant and customer`() {
        val controller = RestaurantsQaPreviewController(qaOfflineEnvironment())

        // 1. Open Habesha Hearth -> verify own menu, session, cart item, active order, and history
        controller.openRestaurantWorkspace(RestaurantsQaSampleData.QA_RESTAURANT_ID)
        var s = controller.state.value
        assertEquals("Habesha Hearth", s.menuState.restaurantName)
        assertFalse(s.menuState.isBrowseOnly)
        assertEquals("prev-br-bole", s.menuState.selectedBranchId)
        assertEquals(1, s.cartState.totalItemCount)

        val habeshaActive = RestaurantOrderIsolationReducers.filterActiveOrdersForRestaurant(
            s.workspaceOrders,
            RestaurantsQaSampleData.QA_RESTAURANT_ID,
            RestaurantsQaSampleData.QA_CUSTOMER_UID,
        )
        val habeshaHistory = RestaurantOrderIsolationReducers.filterHistoryOrdersForRestaurant(
            s.workspaceOrders,
            RestaurantsQaSampleData.QA_RESTAURANT_ID,
            RestaurantsQaSampleData.QA_CUSTOMER_UID,
        )
        assertEquals(1, habeshaActive.size)
        assertEquals("104", habeshaActive.first().orderNumber)
        assertEquals(2, habeshaHistory.size)
        // Order #999 belongs to OTHER_CUSTOMER_UID and must NEVER appear
        assertFalse(habeshaActive.any { it.orderNumber == "999" })
        assertFalse(habeshaHistory.any { it.orderNumber == "999" })

        // 2. Switch to Kaffa Roastery -> cart MUST NOT contain Habesha items, nor Habesha orders
        controller.openRestaurantWorkspace(RestaurantsQaSampleData.QA_KAFFA_RESTAURANT_ID)
        s = controller.state.value
        assertEquals("Kaffa Roastery & Bistro", s.menuState.restaurantName)
        assertEquals(0, s.cartState.totalItemCount)
        assertEquals(RestaurantsQaSampleData.QA_KAFFA_RESTAURANT_ID, s.cartState.activeSession?.restaurantId)

        val kaffaItem = s.menuState.items.first { it.id == "item-kaffa-pourover" }
        controller.openMenuItemSheet(kaffaItem)
        controller.addSheetItemToPreviewCart()
        assertEquals(1, controller.state.value.cartState.totalItemCount)

        val kaffaActive = RestaurantOrderIsolationReducers.filterActiveOrdersForRestaurant(
            controller.state.value.workspaceOrders,
            RestaurantsQaSampleData.QA_KAFFA_RESTAURANT_ID,
            RestaurantsQaSampleData.QA_CUSTOMER_UID,
        )
        val kaffaHistory = RestaurantOrderIsolationReducers.filterHistoryOrdersForRestaurant(
            controller.state.value.workspaceOrders,
            RestaurantsQaSampleData.QA_KAFFA_RESTAURANT_ID,
            RestaurantsQaSampleData.QA_CUSTOMER_UID,
        )
        assertEquals(1, kaffaActive.size)
        assertEquals("207", kaffaActive.first().orderNumber)
        assertEquals(1, kaffaHistory.size)
        assertEquals("201", kaffaHistory.first().orderNumber)

        // 3. Switch back to Habesha -> Habesha's cart still contains only Shekla Tibs (not Kaffa Pour-Over)
        controller.openRestaurantWorkspace(RestaurantsQaSampleData.QA_RESTAURANT_ID)
        val habeshaCartItems = controller.state.value.cartState.items
        assertEquals(1, habeshaCartItems.size)
        assertEquals("item-shekla-tibs", habeshaCartItems.first().menuItemId)
    }

    @Test
    fun `offline simulated QA order workflow creates labeled multiple orders preserves dining session and enforces auth session guards`() {
        val controller = RestaurantsQaPreviewController(qaOfflineEnvironment())

        // 1. Browse without dining session (Entoto Terrace) allows sheet preview but blocks cart add
        controller.openRestaurantWorkspace(RestaurantsQaSampleData.QA_ENTOTO_RESTAURANT_ID)
        assertTrue(controller.state.value.menuState.isBrowseOnly)
        val entotoItem = controller.state.value.menuState.items.first()
        controller.openMenuItemSheet(entotoItem)
        controller.addSheetItemToPreviewCart()
        assertEquals(0, controller.state.value.cartState.totalItemCount)
        assertNotNull(controller.state.value.activeMenuItemSheet)
        assertTrue(controller.state.value.bannerNotice.orEmpty().contains("dining session"))

        // 2. Habesha Hearth -> real checkout still blocked, while simulated QA order succeeds & keeps session
        controller.openRestaurantWorkspace(RestaurantsQaSampleData.QA_RESTAURANT_ID)
        val beforeActiveCount = RestaurantOrderIsolationReducers.filterActiveOrdersForRestaurant(
            controller.state.value.workspaceOrders,
            RestaurantsQaSampleData.QA_RESTAURANT_ID,
            RestaurantsQaSampleData.QA_CUSTOMER_UID,
        ).size

        controller.openWorkspaceCart()
        controller.onSubmitDineInOrder()
        assertTrue(controller.state.value.cartState.checkoutPhase is CartCheckoutPhase.Failed)

        controller.createSimulatedQaOrder()
        var afterState = controller.state.value
        assertEquals(RestaurantWorkspaceTab.MY_ORDERS, afterState.selectedWorkspaceTab)
        assertNotNull(afterState.selectedWorkspaceOrderId)
        assertTrue(afterState.cartState.items.isEmpty())
        // Critical: active dining session MUST remain intact so user can order more rounds
        val preservedSession = afterState.cartState.activeSession
        assertNotNull(preservedSession)
        assertEquals("qa-ds-habesha-bole-4", preservedSession?.diningSessionId)

        val simOrder = afterState.workspaceOrders.first { it.orderId == afterState.selectedWorkspaceOrderId }
        assertTrue(simOrder.isSimulatedQa)
        assertTrue(afterState.orderTrackingState.orderNumber.startsWith("QA-"))

        // 3. Click "Order More Food" -> returns to Menu in SAME restaurant & place a second active order!
        controller.onOrderMoreFood()
        assertEquals(RestaurantWorkspaceTab.MENU, controller.state.value.selectedWorkspaceTab)
        assertEquals(null, controller.state.value.selectedWorkspaceOrderId)
        assertFalse(controller.state.value.menuState.isBrowseOnly)

        val secondDish = controller.state.value.menuState.items.first { it.id == "item-beyaynetu" }
        controller.openMenuItemSheet(secondDish)
        controller.addSheetItemToPreviewCart()
        assertEquals(1, controller.state.value.cartState.totalItemCount)
        controller.createSimulatedQaOrder()

        afterState = controller.state.value
        val activeAfterTwoRounds = RestaurantOrderIsolationReducers.filterActiveOrdersForRestaurant(
            afterState.workspaceOrders,
            RestaurantsQaSampleData.QA_RESTAURANT_ID,
            RestaurantsQaSampleData.QA_CUSTOMER_UID,
        )
        assertEquals(beforeActiveCount + 2, activeAfterTwoRounds.size)
        // Switch between multiple active orders in My Orders
        val olderOrder = activeAfterTwoRounds.last()
        controller.openWorkspaceOrderTracking(olderOrder.orderId)
        assertEquals(olderOrder.orderId, controller.state.value.selectedWorkspaceOrderId)
        assertEquals(olderOrder.orderNumber, controller.state.value.orderTrackingState.orderNumber)

        // 4. Sign out / unauthenticated UID blocks cart mutations & hides customer orders
        controller.setCustomerUidForQa(null)
        assertEquals(null, controller.state.value.cartState.activeSession)
        assertTrue(
            RestaurantOrderIsolationReducers.filterActiveOrdersForRestaurant(
                controller.state.value.workspaceOrders,
                RestaurantsQaSampleData.QA_RESTAURANT_ID,
                controller.state.value.currentCustomerUid,
            ).isEmpty(),
        )
        controller.openMenuItemSheet(secondDish)
        controller.addSheetItemToPreviewCart()
        assertEquals(0, controller.state.value.cartState.totalItemCount)
    }

    @Test
    fun `scan table qr confirmation in QA preview binds dining session and returns to workspace`() {
        val controller = RestaurantsQaPreviewController(qaOfflineEnvironment())
        controller.openRestaurantWorkspace(RestaurantsQaSampleData.QA_ENTOTO_RESTAURANT_ID)
        controller.openTableScan()
        assertEquals(RestaurantsQaRoute.TABLE_SCAN, controller.state.value.currentRoute)

        val entotoSession = ActiveDiningSessionUi(
            ownerAdisChatUid = RestaurantsQaSampleData.QA_CUSTOMER_UID,
            restaurantId = RestaurantsQaSampleData.QA_ENTOTO_RESTAURANT_ID,
            restaurantName = "Entoto Terrace",
            branchId = "prev-br-sarbet",
            branchName = "Sarbet Rooftop",
            tableId = "tbl-entoto-1",
            tableLabel = "Table 1 • View",
            diningSessionId = "qa-ds-entoto-1",
        )
        controller.onContinueAfterScanConfirmed(entotoSession)

        val state = controller.state.value
        assertEquals(RestaurantsQaRoute.WORKSPACE, state.currentRoute)
        assertEquals(RestaurantsQaSampleData.QA_ENTOTO_RESTAURANT_ID, state.selectedRestaurantId)
        assertFalse(state.menuState.isBrowseOnly)
        assertEquals("qa-ds-entoto-1", state.cartState.activeSession?.diningSessionId)
        assertFalse(state.backStack.contains(RestaurantsQaRoute.TABLE_SCAN))
    }

    @Test
    fun `quick add and decrement handle simple items required options multi variants and branch session guards`() {
        val controller = RestaurantsQaPreviewController(qaOfflineEnvironment())
        controller.openRestaurantWorkspace(RestaurantsQaSampleData.QA_RESTAURANT_ID)

        // Initial Habesha state: 1 Shekla Tibs in Bole cart
        assertEquals(1, controller.state.value.cartState.totalItemCount)
        assertEquals(null, controller.state.value.activeMenuItemSheet)
        assertFalse(controller.state.value.isWorkspaceCartOpen)

        // 1. Simple item without required options adds directly on Menu without opening sheet or leaving Menu
        val beyaynetu = controller.state.value.menuState.items.first { it.id == "item-beyaynetu" }
        assertFalse(beyaynetu.hasRequiredOptions())
        controller.onQuickAddMenuItem(beyaynetu)
        assertEquals(RestaurantsQaRoute.WORKSPACE, controller.state.value.currentRoute)
        assertFalse(controller.state.value.isWorkspaceCartOpen)
        assertEquals(null, controller.state.value.activeMenuItemSheet)
        assertEquals(2, controller.state.value.cartState.totalItemCount)

        // 2. Quick decrement on simple item updates cart without leaving Menu
        controller.onQuickDecrementMenuItem(beyaynetu.id)
        assertEquals(1, controller.state.value.cartState.totalItemCount)

        // 3. Item with required options opens customization sheet instead of adding silently
        val tibs = controller.state.value.menuState.items.first { it.id == "item-shekla-tibs" }
        assertTrue(tibs.hasRequiredOptions())
        controller.onQuickAddMenuItem(tibs)
        assertNotNull(controller.state.value.activeMenuItemSheet)
        assertEquals("item-shekla-tibs", controller.state.value.activeMenuItemSheet?.item?.id)

        // Completing a different required option creates a 2nd line for Shekla Tibs
        controller.onSheetToggleOption("og-portion", "opt-share")
        controller.addSheetItemToPreviewCart()
        assertEquals(2, controller.state.value.cartState.items.size)

        // Quick decrement with 2 customized variants opens Cart review safely instead of guessing which to remove
        controller.onQuickDecrementMenuItem(tibs.id)
        assertTrue(controller.state.value.isWorkspaceCartOpen)
        assertEquals(2, controller.state.value.cartState.items.size)

        // 4. Switching to CMC branch without active CMC table session blocks quick add cleanly
        val cmcBranch = controller.state.value.menuState.branches[1]
        controller.onWorkspaceBranchSelected(cmcBranch.id)
        assertTrue(controller.state.value.menuState.isBrowseOnly)
        controller.onQuickAddMenuItem(beyaynetu)
        assertTrue(controller.state.value.bannerNotice.orEmpty().contains("Scan Table QR"))
    }

    @Test
    fun `philia cart sheet checkout step order mode guards orders sheet unwind and timeline labels work end to end`() {
        val controller = RestaurantsQaPreviewController(qaOfflineEnvironment())
        controller.openRestaurantWorkspace(RestaurantsQaSampleData.QA_RESTAURANT_ID)

        // Verify 5-stage active timeline display labels
        assertEquals("Order Placed", RestaurantOrderStatus.ORDER_SUBMITTED.timelineDisplayLabel)
        assertEquals("Restaurant Accepted", RestaurantOrderStatus.ACCEPTED.timelineDisplayLabel)
        assertEquals("Preparing", RestaurantOrderStatus.PREPARING.timelineDisplayLabel)
        assertEquals("Ready", RestaurantOrderStatus.READY.timelineDisplayLabel)
        assertEquals("Served", RestaurantOrderStatus.SERVED.timelineDisplayLabel)

        // 1. Open cart sheet -> verify variant & quantity edit recalculates subtotal -> proceed to checkout -> back unwinds cleanly
        controller.openWorkspaceCart()
        assertTrue(controller.state.value.isWorkspaceCartOpen)
        assertEquals(CheckoutSheetStep.CART_REVIEW, controller.state.value.checkoutSheetStep)
        val initialLine = controller.state.value.cartState.items.first()
        controller.onCartIncrementQuantity(initialLine.lineId, initialLine.quantity)
        assertEquals(2, controller.state.value.cartState.totalItemCount)
        assertEquals(136000L, controller.state.value.cartState.estimatedSubtotalMinor)

        controller.onCheckoutStepChange(CheckoutSheetStep.CHECKOUT_CONFIRM)
        assertEquals(CheckoutSheetStep.CHECKOUT_CONFIRM, controller.state.value.checkoutSheetStep)

        // 2. Disabled modes (Takeaway Coming Soon, Delivery Future) are rejected cleanly
        controller.onSelectOrderMode(DineInOrderMode.TAKEAWAY)
        assertEquals(DineInOrderMode.DINE_IN, controller.state.value.cartState.selectedOrderMode)
        assertTrue(controller.state.value.bannerNotice.orEmpty().contains("Coming Soon"))
        controller.onSelectOrderMode(DineInOrderMode.DELIVERY)
        assertEquals(DineInOrderMode.DINE_IN, controller.state.value.cartState.selectedOrderMode)

        // First Back from CHECKOUT_CONFIRM steps back to CART_REVIEW; second Back dismisses cart sheet cleanly
        assertTrue(controller.navigateBack())
        assertTrue(controller.state.value.isWorkspaceCartOpen)
        assertEquals(CheckoutSheetStep.CART_REVIEW, controller.state.value.checkoutSheetStep)
        assertTrue(controller.navigateBack())
        assertFalse(controller.state.value.isWorkspaceCartOpen)

        // 3. Re-open checkout -> real order remains blocked offline -> simulated QA order closes cart, keeps session & opens Orders sheet
        controller.openWorkspaceCart()
        controller.onCheckoutStepChange(CheckoutSheetStep.CHECKOUT_CONFIRM)
        controller.onSubmitDineInOrder()
        assertTrue(controller.state.value.cartState.checkoutPhase is CartCheckoutPhase.Failed)

        controller.createSimulatedQaOrder()
        var s = controller.state.value
        assertFalse(s.isWorkspaceCartOpen)
        assertEquals(CheckoutSheetStep.CART_REVIEW, s.checkoutSheetStep)
        assertTrue(s.isOrdersSheetOpen)
        assertEquals(RestaurantWorkspaceTab.MY_ORDERS, s.selectedWorkspaceTab)
        assertNotNull(s.selectedWorkspaceOrderId)
        assertNotNull(s.cartState.activeSession)

        // 4. Back inside Orders sheet unwinds detail -> list -> dismisses Orders sheet -> remains on Workspace Menu
        assertTrue(controller.navigateBack())
        assertTrue(controller.state.value.isOrdersSheetOpen)
        assertEquals(null, controller.state.value.selectedWorkspaceOrderId)
        assertTrue(controller.navigateBack())
        assertFalse(controller.state.value.isOrdersSheetOpen)
        assertEquals(RestaurantsQaRoute.WORKSPACE, controller.state.value.currentRoute)

        // 5. Open Orders sheet directly -> History tab remains isolated to selected restaurant & customer UID
        controller.openOrdersSheet(RestaurantWorkspaceTab.HISTORY)
        assertTrue(controller.state.value.isOrdersSheetOpen)
        val habeshaHistory = RestaurantOrderIsolationReducers.filterHistoryOrdersForRestaurant(
            controller.state.value.workspaceOrders,
            RestaurantsQaSampleData.QA_RESTAURANT_ID,
            RestaurantsQaSampleData.QA_CUSTOMER_UID,
        )
        assertEquals(2, habeshaHistory.size)
        assertTrue(habeshaHistory.all { it.restaurantId == RestaurantsQaSampleData.QA_RESTAURANT_ID && !it.isActiveOrder })
    }

    @Test
    fun `picked uploads preserve real metadata validation and block offline uploads cleanly`() {
        val controller = RestaurantsQaPreviewController(qaOfflineEnvironment())
        controller.openCenterTab(RestaurantCenterTab.APPLICATION)

        val target = RestaurantsPendingPickerTarget.ApplicationDocument(
            applicationId = "qa-app-habesha-01",
            docType = "business_license",
            label = "Business License",
        )
        val source = RestaurantsBinaryUploadSource(
            fileName = "license.pdf",
            mimeType = "application/pdf",
            byteSize = 1024L,
            openStream = { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) },
        )

        controller.onUploadPickedFile(target, source)
        val upload = controller.state.value.centerState.uploadState
        assertEquals(CenterUploadPhase.FAILED, upload.phase)
        assertEquals("license.pdf", upload.fileName)
        assertEquals("application/pdf", upload.mimeType)
        assertEquals(1024L, upload.byteSize)
        assertTrue(
            upload.errorMessage.orEmpty().contains(
                RestaurantsEnvironment.STAGING_NOT_CONNECTED_MESSAGE,
            ),
        )
        assertEquals(null, controller.state.value.centerState.errorBanner)
        assertEquals(null, controller.state.value.bannerNotice)

        controller.onPickerCancelled()
        assertEquals(CenterUploadPhase.CANCELLED, controller.state.value.centerState.uploadState.phase)
        assertEquals(null, controller.state.value.centerState.actionFeedback)
        assertEquals(null, controller.state.value.bannerNotice)

        controller.onUploadValidationError(target, "Selected file exceeds 10 MB")
        assertEquals(CenterUploadPhase.FAILED, controller.state.value.centerState.uploadState.phase)
        assertEquals("Selected file exceeds 10 MB", controller.state.value.centerState.uploadState.errorMessage)
        assertEquals(null, controller.state.value.centerState.errorBanner)
        assertEquals(null, controller.state.value.bannerNotice)

        val invalidType = RestaurantsPickerMetadataHelper.normalizeMimeType(
            kind = RestaurantsUploadKind.PUBLIC_MEDIA,
            reportedMime = "application/pdf",
            fileName = "menu.pdf",
        )
        assertTrue(invalidType.isFailure)
    }
}
