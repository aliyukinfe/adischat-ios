package com.adischat.app.feature.restaurants.presentation

import com.adischat.app.feature.restaurants.data.BranchMenuItemOverrideDto
import com.adischat.app.feature.restaurants.data.MenuCategoryDto
import com.adischat.app.feature.restaurants.data.MenuItemDto
import com.adischat.app.feature.restaurants.data.MenuItemImageDto
import com.adischat.app.feature.restaurants.data.MenuOptionDto
import com.adischat.app.feature.restaurants.data.MenuOptionGroupDto
import com.adischat.app.feature.restaurants.data.OrderItemRecordDto
import com.adischat.app.feature.restaurants.data.OrderPaymentRecordDto
import com.adischat.app.feature.restaurants.data.OrderRecordDto
import com.adischat.app.feature.restaurants.data.OrderStatusEventDto
import com.adischat.app.feature.restaurants.data.ResolveQrResponse
import com.adischat.app.feature.restaurants.data.ResolvedEntityDto
import com.adischat.app.feature.restaurants.data.ResolvedTableDto
import com.adischat.app.feature.restaurants.data.RestaurantBranchDto
import com.adischat.app.feature.restaurants.data.RestaurantSummaryDto
import com.adischat.app.feature.restaurants.data.RestaurantsCustomerDataSource
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RestaurantsSessionMemory
import com.adischat.app.feature.restaurants.data.SubmitOrderItemRequest
import com.adischat.app.feature.restaurants.data.SubmitOrderResultDto
import com.adischat.app.ui.scan.ScanResult
import com.adischat.app.ui.scan.resolveMainScanResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class RestaurantsQrAndTrackingHardeningTest {

    @Before
    fun setUp() {
        RestaurantsSessionMemory.finishSignOut()
        RestaurantsSessionMemory.clear()
    }

    @After
    fun tearDown() {
        RestaurantsSessionMemory.finishSignOut()
        RestaurantsSessionMemory.clear()
    }

    @Test
    fun `qr parser validates strict adischat table urls and bare tokens without leaking token`() {
        val token = "tbl_AbCdEfGhIjKl1234"
        assertEquals(token, RestaurantQrParser.parseStrictRestaurantQrUrl("https://adischat.com/d/$token"))
        assertEquals(token, RestaurantQrParser.parseStrictRestaurantQrUrl("  https://adischat.com/d/$token  "))
        assertEquals(token, RestaurantQrParser.extractTokenForTableScanner("https://adischat.com/d/$token"))
        assertEquals(token, RestaurantQrParser.extractTokenForTableScanner(token))
        assertEquals("qr-secret-998877", RestaurantQrParser.extractTokenForTableScanner("qr-secret-998877"))

        // Bare tokens and malformed URLs must NOT route from the generic Main Scan parser
        assertNull(RestaurantQrParser.parseStrictRestaurantQrUrl(token))
        val invalidUrls = listOf(
            "http://adischat.com/d/$token",
            "https://evil.com/d/$token",
            "https://adischat.com/d/other_123456",
            "https://adischat.com/d/$token?ref=1",
            "https://adischat.com/d/$token#section",
            "https://user@adischat.com/d/$token",
            "https://adischat.com:8443/d/$token",
            "https://adischat.com/d/$token/extra",
            "https://adischat.com/x/$token",
            "https://adischat.com/d/tbl_1",
            "   ",
        )
        invalidUrls.forEach { bad ->
            assertNull("Expected parseStrictRestaurantQrUrl to reject $bad", RestaurantQrParser.parseStrictRestaurantQrUrl(bad))
            assertNull("Expected extractTokenForTableScanner to reject $bad", RestaurantQrParser.extractTokenForTableScanner(bad))
        }

        // Reducer errors must not include the rejected input string
        val errorState = TableQrScanReducers.onQrTokenCaptured(
            RestaurantTableScanUiState(isDineInEnabled = true),
            "https://evil.com/d/tbl_secret999",
        )
        assertTrue(errorState.phase is TableScanPhase.Error)
        val msg = (errorState.phase as TableScanPhase.Error).message
        assertFalse(msg.contains("tbl_secret999"))
        assertFalse(msg.contains("evil.com"))
    }

    @Test
    fun `main scan classifier routes strict restaurant qr urls only when dine-in is enabled`() {
        val url = "https://adischat.com/d/tbl_QA12345678"
        val enabledResult = resolveMainScanResult(url, restaurantsDineInEnabled = true)
        assertEquals(ScanResult.RestaurantQr("tbl_QA12345678"), enabledResult)

        // When dine-in is disabled (production), treat as ordinary URL instead of opening Restaurants
        val disabledResult = resolveMainScanResult(url, restaurantsDineInEnabled = false)
        assertEquals(ScanResult.Found(url, "url"), disabledResult)

        // Non-restaurant URL stays ordinary URL so normal QR features continue unchanged
        val genericUrl = "https://adischat.com/help"
        assertEquals(ScanResult.Found(genericUrl, "url"), resolveMainScanResult(genericUrl, restaurantsDineInEnabled = true))
    }

    @Test
    fun `qr parser extracts deep links for adischat table urls and custom scheme without colliding with other links`() {
        val token = "tbl_AbCdEfGhIjKl1234"
        assertEquals(token, RestaurantQrParser.extractRestaurantDeepLink("https://adischat.com/d/$token"))
        assertEquals(token, RestaurantQrParser.extractRestaurantDeepLink("http://adischat.com/d/$token"))
        assertEquals(token, RestaurantQrParser.extractRestaurantDeepLink("adischat://restaurant/table?token=$token"))
        assertEquals(token, RestaurantQrParser.extractRestaurantDeepLink("adischat://restaurant/$token"))

        // Other links must return null without modifying existing routing
        assertNull(RestaurantQrParser.extractRestaurantDeepLink("https://me.adischat.com/u/alice"))
        assertNull(RestaurantQrParser.extractRestaurantDeepLink("adischat://user?username=alice"))
        assertNull(RestaurantQrParser.extractRestaurantDeepLink("adischat://channel?id=test"))
        assertNull(RestaurantQrParser.extractRestaurantDeepLink("adischat://group?id=test"))
        assertNull(RestaurantQrParser.extractRestaurantDeepLink("https://google.com"))
        assertNull(RestaurantQrParser.extractRestaurantDeepLink(null))
        assertNull(RestaurantQrParser.extractRestaurantDeepLink(""))
    }

    @Test
    fun `submitScannedQrToken ignores duplicate rapid scans while resolving or confirmed`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val fake = HardeningFakeDataSource().apply {
            resolveGate = gate
        }
        val coordinator = RestaurantsCustomerCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { qaEnabledEnv() },
            accountUidProvider = { "uid-qa-1" },
        )

        coordinator.openTableScan()
        coordinator.submitScannedQrToken("https://adischat.com/d/tbl_QA12345678")
        coordinator.submitScannedQrToken("https://adischat.com/d/tbl_QA12345678")
        coordinator.submitScannedQrToken("tbl_QA12345678")
        delay(20)

        assertEquals(1, fake.resolveQrCalls.get())
        assertTrue(coordinator.state.value.tableScanState.phase is TableScanPhase.Resolving)

        gate.complete(Unit)
        delay(30)
        assertTrue(coordinator.state.value.tableScanState.phase is TableScanPhase.Confirmed)
        assertEquals(1, fake.resolveQrCalls.get())

        // Duplicate scan for the exact same confirmed token does not re-hit backend
        coordinator.submitScannedQrToken("https://adischat.com/d/tbl_QA12345678")
        delay(20)
        assertEquals(1, fake.resolveQrCalls.get())
    }

    @Test
    fun `foreground tracking polls while visible and stops on background terminal account change or error preserves order`() = runBlocking {
        val fake = HardeningFakeDataSource()
        var activeUid: String? = "uid-qa-1"
        val coordinator = RestaurantsCustomerCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { qaEnabledEnv() },
            accountUidProvider = { activeUid },
            trackingPollIntervalMs = 25L,
        )

        coordinator.openOrderTracking("ord-1")
        delay(30)
        assertEquals(RestaurantOrderStatus.ORDER_SUBMITTED, coordinator.state.value.orderTrackingState.currentStatus)
        val baselineCalls = fake.orderDetailsCalls.get()

        // Backgrounding immediately suppresses polling
        coordinator.setOrderTrackingVisibility("ord-1", isForegrounded = false)
        delay(80)
        assertEquals(baselineCalls, fake.orderDetailsCalls.get())

        // Foregrounding polls and observes server progression ORDER_SUBMITTED -> PREPARING
        fake.currentOrder = fake.currentOrder.copy(status = "PREPARING")
        coordinator.setOrderTrackingVisibility("ord-1", isForegrounded = true)
        delay(80)
        assertEquals(RestaurantOrderStatus.PREPARING, coordinator.state.value.orderTrackingState.currentStatus)
        assertTrue(fake.orderDetailsCalls.get() > baselineCalls)

        // Transient network error keeps existing order details intact
        fake.orderDetailsFailure = IOException("offline")
        delay(45)
        assertEquals(RestaurantOrderStatus.PREPARING, coordinator.state.value.orderTrackingState.currentStatus)
        assertTrue(coordinator.state.value.orderTrackingState.syncState is OrderTrackingSyncState.OfflineOrReconnecting)

        // Recovery updates to terminal COMPLETED and stops polling loop automatically
        fake.orderDetailsFailure = null
        fake.currentOrder = fake.currentOrder.copy(status = "COMPLETED", paymentStatus = "PAID")
        delay(70)
        assertEquals(RestaurantOrderStatus.COMPLETED, coordinator.state.value.orderTrackingState.currentStatus)
        val countAtTerminal = fake.orderDetailsCalls.get()
        delay(90)
        assertEquals(countAtTerminal, fake.orderDetailsCalls.get())

        // Sign-out / account switch stops polling immediately even for active non-terminal order
        fake.currentOrder = fake.currentOrder.copy(id = "ord-2", status = "ACCEPTED", paymentStatus = "UNPAID")
        coordinator.openOrderTracking("ord-2")
        coordinator.setOrderTrackingVisibility("ord-2", isForegrounded = true)
        delay(40)
        activeUid = null
        coordinator.onAccountChanged(null)
        val countOnSignOut = fake.orderDetailsCalls.get()
        delay(90)
        assertEquals(countOnSignOut, fake.orderDetailsCalls.get())
        assertEquals("", coordinator.state.value.orderTrackingState.orderId)
    }

    private fun qaEnabledEnv() = RestaurantsEnvironment(
        supabaseUrl = "https://gnhemfikofnzpemgnqdv.supabase.co",
        supabasePublishableKey = "anon-key",
        identityBridgeUrl = "https://adischat-api-staging.adischat.workers.dev",
        isIdentityBridgeEnabled = true,
        isDineInEnabled = true,
        isQaBuild = true,
        areQaOrderMutationsEnabled = true,
    )
}

private class HardeningFakeDataSource : RestaurantsCustomerDataSource {
    val resolveQrCalls = AtomicInteger(0)
    val orderDetailsCalls = AtomicInteger(0)
    var resolveGate: CompletableDeferred<Unit>? = null
    var orderDetailsFailure: Throwable? = null
    var currentOrder = OrderRecordDto(
        id = "ord-1",
        orderNumber = "101",
        restaurantId = "r1",
        branchId = "b1",
        diningSessionId = "ds-1",
        tableId = "t1",
        customerProfileId = "prof-1",
        orderType = "DINE_IN",
        paymentMethod = "PAY_AT_RESTAURANT",
        paymentStatus = "UNPAID",
        status = "ORDER_SUBMITTED",
        subtotalMinor = 50000L,
        totalMinor = 50000L,
    )

    override suspend fun listLiveRestaurants(): Result<List<RestaurantSummaryDto>> = Result.success(emptyList())
    override suspend fun getRestaurantById(restaurantId: String): Result<RestaurantSummaryDto?> = Result.success(null)
    override suspend fun listRestaurantBranches(restaurantId: String): Result<List<RestaurantBranchDto>> = Result.success(emptyList())
    override suspend fun listMenuCategories(restaurantId: String): Result<List<MenuCategoryDto>> = Result.success(emptyList())
    override suspend fun listMenuItems(restaurantId: String): Result<List<MenuItemDto>> = Result.success(emptyList())
    override suspend fun listMenuItemImages(restaurantId: String): Result<List<MenuItemImageDto>> = Result.success(emptyList())
    override suspend fun listMenuOptionGroups(restaurantId: String): Result<List<MenuOptionGroupDto>> = Result.success(emptyList())
    override suspend fun listMenuOptions(restaurantId: String): Result<List<MenuOptionDto>> = Result.success(emptyList())
    override suspend fun listBranchOverrides(branchId: String): Result<List<BranchMenuItemOverrideDto>> = Result.success(emptyList())

    override suspend fun resolveTableQr(token: String, sessionCode: String?): Result<ResolveQrResponse> {
        resolveQrCalls.incrementAndGet()
        resolveGate?.await()
        return Result.success(
            ResolveQrResponse(
                ok = true,
                restaurant = ResolvedEntityDto("r1", "QA Bistro"),
                branch = ResolvedEntityDto("b1", "Branch B"),
                table = ResolvedTableDto("t1", "QA-B-01"),
                diningSessionId = "ds-1",
                expiresAt = "2026-11-12T23:59:00Z",
            ),
        )
    }

    override suspend fun submitDineInOrder(
        diningSessionId: String,
        branchId: String?,
        items: List<SubmitOrderItemRequest>,
        customerNotes: String?,
        idempotencyKey: String,
    ): Result<SubmitOrderResultDto> = Result.failure(UnsupportedOperationException())

    override suspend fun getOrderDetails(orderId: String): Result<OrderRecordDto?> {
        orderDetailsCalls.incrementAndGet()
        orderDetailsFailure?.let { return Result.failure(it) }
        return Result.success(currentOrder.copy(id = orderId))
    }

    override suspend fun listOrderItems(orderId: String): Result<List<OrderItemRecordDto>> =
        Result.success(listOf(OrderItemRecordDto("oi-1", orderId, "mi-1", "Shiro", 50000L, 1, 50000L)))

    override suspend fun listOrderStatusEvents(orderId: String): Result<List<OrderStatusEventDto>> =
        Result.success(emptyList())

    override suspend fun listOrderPayments(orderId: String): Result<List<OrderPaymentRecordDto>> =
        Result.success(emptyList())

    override suspend fun listCustomerOrdersForRestaurant(
        customerProfileId: String,
        restaurantId: String,
    ): Result<List<OrderRecordDto>> = Result.success(emptyList())
}
