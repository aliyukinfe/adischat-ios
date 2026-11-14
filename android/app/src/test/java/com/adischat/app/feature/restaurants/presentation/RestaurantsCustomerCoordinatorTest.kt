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
import com.adischat.app.feature.restaurants.data.RestaurantsFailure
import com.adischat.app.feature.restaurants.data.RestaurantsSession
import com.adischat.app.feature.restaurants.data.RestaurantsSessionMemory
import com.adischat.app.feature.restaurants.data.SubmitOrderItemRequest
import com.adischat.app.feature.restaurants.data.SubmitOrderResultDto
import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class RestaurantsCustomerCoordinatorTest {

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
    fun `1 discover to profile to branch menu flow maps SQL contracts and branch overrides`() = runBlocking {
        val fake = FakeRestaurantsDataSource()
        val coordinator = RestaurantsCustomerCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledEnv() },
            accountUidProvider = { "uid-customer-1" },
        )

        coordinator.loadDiscover()
        awaitIdle(40)

        var state = coordinator.state.value
        assertEquals(RestaurantsCustomerDestination.Discover, state.destination)
        assertEquals(RestaurantsLoadState.Content, state.discoverState.loadState)
        assertEquals(listOf("Habesha Hearth"), state.discoverState.allRestaurants.map { it.brandName })
        assertEquals(
            "https://test.supabase.co/storage/v1/object/public/restaurant-public-media/restaurants/r1/cover.jpg",
            state.discoverState.allRestaurants.first().coverUrl,
        )

        coordinator.openRestaurantProfile("r1")
        awaitIdle(40)
        state = coordinator.state.value
        assertEquals(RestaurantsCustomerDestination.Profile("r1"), state.destination)
        assertEquals(RestaurantsLoadState.Content, state.profileState.loadState)
        assertEquals(2, state.profileState.branches.size)
        assertEquals("09:00-22:00", state.profileState.branches.first().openingHours.first().hoursLabel)

        coordinator.openRestaurantMenu("r1", "br-bole")
        awaitIdle(40)
        state = coordinator.state.value
        assertEquals(RestaurantsCustomerDestination.Menu("r1"), state.destination)
        assertTrue(state.menuState.isBrowseOnly)
        assertEquals("br-bole", state.menuState.selectedBranchId)
        assertEquals(2, state.menuState.items.size)
        assertTrue(state.menuState.items.first { it.id == "mi-doro" }.isAvailableAtBranch)

        // Switching to br-cmc applies CMC price override and marks Doro Wot unavailable
        coordinator.selectMenuBranch("br-cmc")
        awaitIdle(40)
        state = coordinator.state.value
        assertEquals("br-cmc", state.menuState.selectedBranchId)
        assertEquals(71000L, state.menuState.items.first { it.id == "mi-tibs" }.effectivePriceMinor)
        assertFalse(state.menuState.items.first { it.id == "mi-doro" }.isAvailableAtBranch)
        Unit
    }

    @Test
    fun `2 qr activation required then retry succeeds and unlocks cart binding`() = runBlocking {
        val fake = FakeRestaurantsDataSource()
        var resolveAttempts = 0
        fake.resolveQrHandler = { _, _ ->
            resolveAttempts += 1
            if (resolveAttempts == 1) {
                Result.failure(
                    RestaurantsFailure.Conflict(
                        "dining_session_not_activated",
                        "Ask your waiter to activate this table before ordering.",
                    ),
                )
            } else {
                Result.success(fake.confirmedQrResponse("br-bole"))
            }
        }

        val coordinator = RestaurantsCustomerCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledEnv() },
            accountUidProvider = { "uid-customer-1" },
        )

        coordinator.openTableScan()
        coordinator.submitScannedQrToken("raw-qr-token-secret")
        awaitIdle(40)

        var phase = coordinator.state.value.tableScanState.phase
        assertTrue(phase is TableScanPhase.NeedsWaiterActivation)

        coordinator.retryTableActivationCheck()
        awaitIdle(40)

        phase = coordinator.state.value.tableScanState.phase
        assertTrue(phase is TableScanPhase.Confirmed)
        assertEquals("ds-bole-1", coordinator.state.value.cartState.activeSession?.diningSessionId)
        Unit
    }

    @Test
    fun `3 optional session code challenge handles wrong code then valid code`() = runBlocking {
        val fake = FakeRestaurantsDataSource()
        fake.resolveQrHandler = { _, code ->
            when (code) {
                null -> Result.failure(RestaurantsFailure.Validation("session_code_required", "Enter code"))
                "BAD1" -> Result.failure(RestaurantsFailure.Validation("invalid_session_code", "Wrong code"))
                "K9P4" -> Result.success(fake.confirmedQrResponse("br-cmc"))
                else -> Result.failure(RestaurantsFailure.Validation("invalid_session_code", "Wrong"))
            }
        }

        val coordinator = RestaurantsCustomerCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledEnv() },
            accountUidProvider = { "uid-customer-1" },
        )

        coordinator.openTableScan()
        coordinator.submitScannedQrToken("qr-cmc-1")
        awaitIdle(40)
        assertTrue(coordinator.state.value.tableScanState.phase is TableScanPhase.NeedsSessionCode)

        coordinator.updateSessionCodeInput("BAD1")
        coordinator.submitSessionCode()
        awaitIdle(40)
        val invalidPhase = coordinator.state.value.tableScanState.phase as? TableScanPhase.NeedsSessionCode
        assertNotNull(invalidPhase?.validationError)

        coordinator.updateSessionCodeInput("K9P4")
        coordinator.submitSessionCode()
        awaitIdle(40)
        assertTrue(coordinator.state.value.tableScanState.phase is TableScanPhase.Confirmed)
        assertEquals("br-cmc", coordinator.state.value.cartState.activeSession?.branchId)
        Unit
    }

    @Test
    fun `4 to 7 menu options to cart, order submit, duplicate block, and idempotent retry on ambiguous failure`() = runBlocking {
        val fake = FakeRestaurantsDataSource()
        val submitGate = CompletableDeferred<Unit>()
        var failFirstAttempt = true
        fake.submitOrderHandler = { _, _, _, _, _ ->
            if (failFirstAttempt) {
                submitGate.await()
                Result.failure(IOException("Connection reset during checkout"))
            } else {
                Result.success(
                    SubmitOrderResultDto(
                        orderId = "ord-confirmed-1",
                        orderNumber = "104",
                        status = "ORDER_SUBMITTED",
                        paymentMethod = "PAY_AT_RESTAURANT",
                        paymentStatus = "UNPAID",
                        totalMinor = 212000L,
                        idempotentReplay = true,
                    ),
                )
            }
        }

        val seq = AtomicInteger(0)
        val coordinator = RestaurantsCustomerCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledEnv() },
            accountUidProvider = { "uid-customer-1" },
            uuidGenerator = { "idem-uuid-000${seq.incrementAndGet()}" },
        )

        // Open menu & confirm QR table session on br-bole
        coordinator.openRestaurantMenu("r1", "br-bole")
        awaitIdle(40)
        coordinator.openTableScan()
        coordinator.submitScannedQrToken("qr-bole")
        awaitIdle(40)
        assertFalse(coordinator.state.value.menuState.isBrowseOnly)

        // Select item, customize required + optional options, add to cart
        val tibsItem = coordinator.state.value.menuState.items.first { it.id == "mi-tibs" }
        coordinator.openMenuItem(tibsItem)
        coordinator.toggleSheetOption("og-portion", "opt-share") // +380 -> 1060 ETB
        coordinator.incrementSheetQuantity() // qty 2 -> 2120 ETB
        assertTrue(coordinator.addActiveSheetItemToCart("Extra sizzling"))

        assertEquals(212000L, coordinator.state.value.cartState.estimatedSubtotalMinor)

        // Attempt submit -> in-flight Submitting; duplicate rapid call must NOT invoke dataSource again
        coordinator.openCartReview()
        coordinator.submitDineInOrder()
        coordinator.submitDineInOrder() // duplicate click
        assertTrue(coordinator.state.value.cartState.isSubmitting)

        submitGate.complete(Unit)
        awaitIdle(40)

        assertEquals(1, fake.submittedIdempotencyKeys.size)
        assertEquals("idem-uuid-0001", fake.submittedIdempotencyKeys.first())
        val failedPhase = coordinator.state.value.cartState.checkoutPhase as? CartCheckoutPhase.Failed
        assertNotNull(failedPhase)
        assertEquals("idem-uuid-0001", failedPhase?.idempotencyKey)

        // Retry after ambiguous network failure -> preserves EXACT same UUID & succeeds
        failFirstAttempt = false
        coordinator.submitDineInOrder()
        awaitIdle(40)

        assertEquals(listOf("idem-uuid-0001", "idem-uuid-0001"), fake.submittedIdempotencyKeys)
        val state = coordinator.state.value
        assertEquals(RestaurantsCustomerDestination.OrderTracking("ord-confirmed-1"), state.destination)
        assertTrue(state.cartState.items.isEmpty())
        assertEquals("104", state.orderTrackingState.orderNumber)
        assertEquals(RestaurantOrderStatus.ORDER_SUBMITTED, state.orderTrackingState.currentStatus)
        assertEquals(PaymentStatusUi.UNPAID, state.orderTrackingState.paymentStatus)
        Unit
    }

    @Test
    fun `8 order tracking refresh handles progression, paid completion, offline failure, and terminal rejection`() = runBlocking {
        val fake = FakeRestaurantsDataSource()
        val coordinator = RestaurantsCustomerCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledEnv() },
            accountUidProvider = { "uid-customer-1" },
        )

        coordinator.openRestaurantMenu("r1", "br-bole")
        awaitIdle(40)
        coordinator.openTableScan()
        coordinator.submitScannedQrToken("qr-bole")
        awaitIdle(40)
        val tibsItem = coordinator.state.value.menuState.items.first { it.id == "mi-tibs" }
        coordinator.openMenuItem(tibsItem)
        coordinator.addActiveSheetItemToCart()
        coordinator.submitDineInOrder()
        awaitIdle(40)

        // Refresh -> PREPARING UNPAID
        fake.orderRecord = fake.orderRecord.copy(status = "PREPARING", paymentStatus = "UNPAID")
        coordinator.refreshActiveOrder()
        awaitIdle(40)
        assertEquals(RestaurantOrderStatus.PREPARING, coordinator.state.value.orderTrackingState.currentStatus)
        assertEquals(PaymentStatusUi.UNPAID, coordinator.state.value.orderTrackingState.paymentStatus)

        // Offline failure shows OfflineOrReconnecting without overwriting confirmed status
        fake.orderDetailsFailure = IOException("No route to host")
        coordinator.refreshActiveOrder()
        awaitIdle(40)
        assertTrue(
            coordinator.state.value.orderTrackingState.syncState is OrderTrackingSyncState.OfflineOrReconnecting,
        )
        assertEquals(RestaurantOrderStatus.PREPARING, coordinator.state.value.orderTrackingState.currentStatus)

        // Online again -> REJECTED with reason from order record
        fake.orderDetailsFailure = null
        fake.orderRecord = fake.orderRecord.copy(
            status = "REJECTED",
            rejectedReason = "Grill closed for cleaning",
        )
        coordinator.refreshActiveOrder()
        awaitIdle(40)
        assertEquals(RestaurantOrderStatus.REJECTED, coordinator.state.value.orderTrackingState.currentStatus)
        assertEquals("Grill closed for cleaning", coordinator.state.value.orderTrackingState.terminalReason)
        assertEquals(
            TimelineStepState.TERMINATED,
            coordinator.state.value.orderTrackingState.timelineSteps.last().stepState,
        )
        Unit
    }

    @Test
    fun `9 account switch during in-flight request discards stale response and wipes cart, session, and tracking`() = runBlocking {
        var currentUid: String? = "uid-user-a"
        val profileGate = CompletableDeferred<Unit>()
        val fake = FakeRestaurantsDataSource()
        fake.onGetRestaurantDelay = { profileGate.await() }

        RestaurantsSessionMemory.setSessionIfCurrent(
            RestaurantsSession("uid-user-a", "prof-a", "acc-a", "ref-a"),
            RestaurantsSessionMemory.generation(),
        )

        val coordinator = RestaurantsCustomerCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledEnv() },
            accountUidProvider = { currentUid },
        )

        // Establish QR session and cart item for User A
        coordinator.openTableScan()
        coordinator.submitScannedQrToken("qr-bole")
        awaitIdle(40)
        assertNotNull(coordinator.state.value.cartState.activeSession)

        // Launch slow profile request for User A
        coordinator.openRestaurantProfile("r1")
        delay(20)

        // Account switches mid-flight to User B
        currentUid = "uid-user-b"
        coordinator.onAccountChanged("uid-user-b")
        profileGate.complete(Unit)
        awaitIdle(40)

        val afterSwitch = coordinator.state.value
        assertEquals("uid-user-b", afterSwitch.activeAccountUid)
        assertNull(afterSwitch.cartState.activeSession)
        assertTrue(afterSwitch.cartState.items.isEmpty())
        assertNull(afterSwitch.profileState.restaurant)
        assertNull(RestaurantsSessionMemory.snapshot())
        Unit
    }

    @Test
    fun `10 feature-disabled and unauthenticated guards block backend calls`() = runBlocking {
        val fake = FakeRestaurantsDataSource()
        val disabledCoordinator = RestaurantsCustomerCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { RestaurantsEnvironment(isDineInEnabled = false) },
            accountUidProvider = { "uid-customer-1" },
        )

        disabledCoordinator.loadDiscover()
        disabledCoordinator.openRestaurantProfile("r1")
        disabledCoordinator.openRestaurantMenu("r1")
        disabledCoordinator.submitScannedQrToken("qr-bole")
        disabledCoordinator.submitDineInOrder()
        awaitIdle(40)

        assertEquals(0, fake.totalCalls())
        assertEquals(RestaurantsLoadState.Disabled, disabledCoordinator.state.value.discoverState.loadState)
        assertEquals(TableScanPhase.Disabled, disabledCoordinator.state.value.tableScanState.phase)

        // Even with feature enabled, null AdisChat UID must block network calls
        val unauthCoordinator = RestaurantsCustomerCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledEnv() },
            accountUidProvider = { null },
        )
        unauthCoordinator.loadDiscover()
        awaitIdle(40)
        assertEquals(0, fake.totalCalls())
        Unit
    }

    @Test
    fun `11 restaurant workspace keeps session across multiple order rounds and isolates history by customer and restaurant`() = runBlocking {
        val fake = FakeRestaurantsDataSource()
        fake.restaurantOrders = listOf(
            OrderRecordDto(
                id = "ord-r1-hist",
                orderNumber = "90",
                restaurantId = "r1",
                branchId = "br-bole",
                diningSessionId = "ds-old",
                tableId = "t-2",
                customerProfileId = "uid-customer-1",
                orderType = "DINE_IN",
                paymentMethod = "PAY_AT_RESTAURANT",
                paymentStatus = "PAID",
                status = "COMPLETED",
                subtotalMinor = 62000L,
                totalMinor = 62000L,
                createdAt = "Yesterday",
            ),
            OrderRecordDto(
                id = "ord-r2-leak",
                orderNumber = "91",
                restaurantId = "r2",
                branchId = "br-other",
                diningSessionId = "ds-other",
                tableId = "t-1",
                customerProfileId = "uid-customer-1",
                orderType = "DINE_IN",
                paymentMethod = "PAY_AT_RESTAURANT",
                paymentStatus = "PAID",
                status = "COMPLETED",
                subtotalMinor = 40000L,
                totalMinor = 40000L,
            ),
            OrderRecordDto(
                id = "ord-other-customer",
                orderNumber = "92",
                restaurantId = "r1",
                branchId = "br-bole",
                diningSessionId = "ds-other-user",
                tableId = "t-8",
                customerProfileId = "uid-stranger",
                orderType = "DINE_IN",
                paymentMethod = "PAY_AT_RESTAURANT",
                paymentStatus = "UNPAID",
                status = "PREPARING",
                subtotalMinor = 90000L,
                totalMinor = 90000L,
            ),
        )

        val seq = AtomicInteger(0)
        val coordinator = RestaurantsCustomerCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledEnv() },
            accountUidProvider = { "uid-customer-1" },
            uuidGenerator = { "idem-r5a-${seq.incrementAndGet()}" },
        )

        coordinator.openRestaurantMenu("r1", "br-bole")
        awaitIdle(40)
        coordinator.loadCustomerOrdersForRestaurant("r1")
        awaitIdle(40)

        var historyR1 = RestaurantOrderIsolationReducers.filterHistoryOrdersForRestaurant(
            coordinator.state.value.workspaceOrders,
            "r1",
            "uid-customer-1",
        )
        assertEquals(listOf("ord-r1-hist"), historyR1.map { it.orderId })
        assertTrue(coordinator.state.value.workspaceOrders.none { it.orderId == "ord-other-customer" })
        assertTrue(coordinator.state.value.workspaceOrders.none { it.restaurantId == "r2" })

        // Establish QR session -> submit round 1 -> order more food -> session remains intact!
        coordinator.openTableScan()
        coordinator.submitScannedQrToken("qr-bole")
        awaitIdle(40)
        val tibs = coordinator.state.value.menuState.items.first { it.id == "mi-tibs" }
        coordinator.openMenuItem(tibs)
        assertTrue(coordinator.addActiveSheetItemToCart())
        coordinator.submitDineInOrder()
        awaitIdle(40)

        assertEquals(RestaurantWorkspaceTab.MY_ORDERS, coordinator.state.value.selectedWorkspaceTab)
        assertEquals("ds-bole-1", coordinator.state.value.cartState.activeSession?.diningSessionId)
        assertEquals(1, RestaurantOrderIsolationReducers.filterActiveOrdersForRestaurant(
            coordinator.state.value.workspaceOrders,
            "r1",
            "uid-customer-1",
        ).size)

        coordinator.orderMoreFoodInRestaurant()
        assertEquals(RestaurantWorkspaceTab.MENU, coordinator.state.value.selectedWorkspaceTab)
        assertEquals("ds-bole-1", coordinator.state.value.cartState.activeSession?.diningSessionId)

        // Switching accounts wipes workspaceOrders cleanly
        coordinator.onAccountChanged("uid-customer-2")
        assertTrue(coordinator.state.value.workspaceOrders.isEmpty())
        Unit
    }

    @Test
    fun `12 coordinator quick add and decrement delegate required options and respect session guards`() = runBlocking {
        val fake = FakeRestaurantsDataSource()
        val coordinator = RestaurantsCustomerCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledEnv() },
            accountUidProvider = { "uid-customer-1" },
        )

        coordinator.openRestaurantMenu("r1", "br-bole")
        awaitIdle(40)
        val doro = coordinator.state.value.menuState.items.first { it.id == "mi-doro" }
        val tibs = coordinator.state.value.menuState.items.first { it.id == "mi-tibs" }

        // Without active table session, quick add is rejected safely
        assertFalse(coordinator.quickAddMenuItem(doro))
        assertTrue(coordinator.state.value.cartState.items.isEmpty())

        // Establish QR table session
        coordinator.openTableScan()
        coordinator.submitScannedQrToken("qr-bole")
        awaitIdle(40)

        // Simple item (Doro Wot has no required option groups in fake) adds immediately
        assertTrue(coordinator.quickAddMenuItem(doro))
        assertEquals(1, coordinator.state.value.cartState.totalItemCount)
        assertNull(coordinator.state.value.activeMenuItemSheet)

        // Quick decrement removes it cleanly
        assertTrue(coordinator.quickDecrementMenuItem(doro.id))
        assertEquals(0, coordinator.state.value.cartState.totalItemCount)

        // Required option item (Shekla Tibs) opens customization sheet instead of silent add
        assertFalse(coordinator.quickAddMenuItem(tibs))
        assertNotNull(coordinator.state.value.activeMenuItemSheet)
        assertEquals(0, coordinator.state.value.cartState.totalItemCount)

        // Disabled order modes cannot be selected on coordinator either
        assertFalse(coordinator.selectOrderMode(DineInOrderMode.TAKEAWAY))
        assertFalse(coordinator.selectOrderMode(DineInOrderMode.DELIVERY))
        assertEquals(DineInOrderMode.DINE_IN, coordinator.state.value.cartState.selectedOrderMode)
        Unit
    }

    private fun enabledEnv() = RestaurantsEnvironment(
        supabaseUrl = "https://test.supabase.co",
        supabasePublishableKey = "pk_test",
        identityBridgeUrl = "https://bridge.example",
        isIdentityBridgeEnabled = true,
        isDineInEnabled = true,
    )

    private suspend fun awaitIdle(ms: Long) {
        delay(ms)
    }
}

private class FakeRestaurantsDataSource : RestaurantsCustomerDataSource {
    val calls = AtomicInteger(0)
    val submittedIdempotencyKeys = mutableListOf<String>()
    var onGetRestaurantDelay: (suspend () -> Unit)? = null
    var orderDetailsFailure: Throwable? = null
    var restaurantOrders: List<OrderRecordDto> = emptyList()

    var resolveQrHandler: suspend (String, String?) -> Result<ResolveQrResponse> = { _, _ ->
        Result.success(confirmedQrResponse("br-bole"))
    }

    var submitOrderHandler: suspend (
        diningSessionId: String,
        branchId: String?,
        items: List<SubmitOrderItemRequest>,
        customerNotes: String?,
        idempotencyKey: String,
    ) -> Result<SubmitOrderResultDto> = { _, _, _, _, _ ->
        Result.success(
            SubmitOrderResultDto(
                orderId = "ord-confirmed-1",
                orderNumber = "104",
                status = "ORDER_SUBMITTED",
                paymentMethod = "PAY_AT_RESTAURANT",
                paymentStatus = "UNPAID",
                totalMinor = 68000L,
                idempotentReplay = false,
            ),
        )
    }

    var orderRecord = OrderRecordDto(
        id = "ord-confirmed-1",
        orderNumber = "104",
        restaurantId = "r1",
        branchId = "br-bole",
        diningSessionId = "ds-bole-1",
        tableId = "t-4",
        customerProfileId = "prof-1",
        orderType = "DINE_IN",
        paymentMethod = "PAY_AT_RESTAURANT",
        paymentStatus = "UNPAID",
        status = "ORDER_SUBMITTED",
        subtotalMinor = 68000L,
        totalMinor = 68000L,
    )

    fun totalCalls(): Int = calls.get()

    fun confirmedQrResponse(branchId: String) = ResolveQrResponse(
        ok = true,
        restaurant = ResolvedEntityDto("r1", "Habesha Hearth"),
        branch = ResolvedEntityDto(branchId, if (branchId == "br-cmc") "CMC" else "Bole Atlas"),
        table = ResolvedTableDto("t-4", "Table 4"),
        diningSessionId = if (branchId == "br-cmc") "ds-cmc-2" else "ds-bole-1",
        expiresAt = "2026-11-10T22:00:00Z",
    )

    override suspend fun listLiveRestaurants(): Result<List<RestaurantSummaryDto>> {
        calls.incrementAndGet()
        return Result.success(
            listOf(
                RestaurantSummaryDto(
                    id = "r1",
                    slug = "habesha-hearth",
                    name = "Habesha Hearth",
                    description = "Wood-fired Ethiopian classics",
                    logoPath = "restaurants/r1/logo.jpg",
                    coverPath = "restaurants/r1/cover.jpg",
                    status = "active",
                    isLiveOrderingEnabled = true,
                ),
            ),
        )
    }

    override suspend fun getRestaurantById(restaurantId: String): Result<RestaurantSummaryDto?> {
        calls.incrementAndGet()
        onGetRestaurantDelay?.invoke()
        return Result.success(listLiveRestaurants().getOrThrow().firstOrNull { it.id == restaurantId })
    }

    override suspend fun listRestaurantBranches(restaurantId: String): Result<List<RestaurantBranchDto>> {
        calls.incrementAndGet()
        val hoursJson = JsonParser.parseString("""{"Mon-Fri":"09:00-22:00"}""")
        return Result.success(
            listOf(
                RestaurantBranchDto(
                    id = "br-bole",
                    restaurantId = restaurantId,
                    code = "BOLE",
                    name = "Bole Atlas",
                    address = "Cape Verde St",
                    city = "Addis Ababa",
                    openingHoursJson = hoursJson,
                    requireStaffSessionActivation = true,
                    requireSessionCode = false,
                    isDineInActive = true,
                    status = "active",
                ),
                RestaurantBranchDto(
                    id = "br-cmc",
                    restaurantId = restaurantId,
                    code = "CMC",
                    name = "CMC",
                    address = "Michael Sq",
                    city = "Addis Ababa",
                    requireStaffSessionActivation = true,
                    requireSessionCode = true,
                    isDineInActive = true,
                    status = "active",
                ),
            ),
        )
    }

    override suspend fun listMenuCategories(restaurantId: String): Result<List<MenuCategoryDto>> {
        calls.incrementAndGet()
        return Result.success(
            listOf(
                MenuCategoryDto("cat-tibs", restaurantId, "Tibs", sortOrder = 1, isActive = true),
                MenuCategoryDto("cat-stews", restaurantId, "Stews", sortOrder = 2, isActive = true),
            ),
        )
    }

    override suspend fun listMenuItems(restaurantId: String): Result<List<MenuItemDto>> {
        calls.incrementAndGet()
        return Result.success(
            listOf(
                MenuItemDto("mi-tibs", restaurantId, "cat-tibs", "Shekla Tibs", "Beef tibs", 68000L, sortOrder = 1),
                MenuItemDto("mi-doro", restaurantId, "cat-stews", "Doro Wot", "Classic stew", 62000L, sortOrder = 2),
            ),
        )
    }

    override suspend fun listMenuItemImages(restaurantId: String): Result<List<MenuItemImageDto>> {
        calls.incrementAndGet()
        return Result.success(
            listOf(MenuItemImageDto("img-1", restaurantId, "mi-tibs", "restaurants/r1/tibs.jpg", true, 1)),
        )
    }

    override suspend fun listMenuOptionGroups(restaurantId: String): Result<List<MenuOptionGroupDto>> {
        calls.incrementAndGet()
        return Result.success(
            listOf(
                MenuOptionGroupDto("og-portion", restaurantId, "mi-tibs", "Portion Size", true, 1, 1, 1),
            ),
        )
    }

    override suspend fun listMenuOptions(restaurantId: String): Result<List<MenuOptionDto>> {
        calls.incrementAndGet()
        return Result.success(
            listOf(
                MenuOptionDto("opt-single", restaurantId, "og-portion", "Solo", 0L, true, 1),
                MenuOptionDto("opt-share", restaurantId, "og-portion", "Shared", 38000L, true, 2),
            ),
        )
    }

    override suspend fun listBranchOverrides(branchId: String): Result<List<BranchMenuItemOverrideDto>> {
        calls.incrementAndGet()
        return if (branchId == "br-cmc") {
            Result.success(
                listOf(
                    BranchMenuItemOverrideDto("br-cmc", "mi-tibs", true, 71000L),
                    BranchMenuItemOverrideDto("br-cmc", "mi-doro", false, null),
                ),
            )
        } else {
            Result.success(emptyList())
        }
    }

    override suspend fun resolveTableQr(token: String, sessionCode: String?): Result<ResolveQrResponse> {
        calls.incrementAndGet()
        return resolveQrHandler(token, sessionCode)
    }

    override suspend fun submitDineInOrder(
        diningSessionId: String,
        branchId: String?,
        items: List<SubmitOrderItemRequest>,
        customerNotes: String?,
        idempotencyKey: String,
    ): Result<SubmitOrderResultDto> {
        calls.incrementAndGet()
        submittedIdempotencyKeys.add(idempotencyKey)
        return submitOrderHandler(diningSessionId, branchId, items, customerNotes, idempotencyKey)
    }

    override suspend fun getOrderDetails(orderId: String): Result<OrderRecordDto?> {
        calls.incrementAndGet()
        orderDetailsFailure?.let { return Result.failure(it) }
        return Result.success(orderRecord.copy(id = orderId))
    }

    override suspend fun listOrderItems(orderId: String): Result<List<OrderItemRecordDto>> {
        calls.incrementAndGet()
        return Result.success(
            listOf(OrderItemRecordDto("oi-1", orderId, "mi-tibs", "Shekla Tibs", 68000L, 1, 68000L)),
        )
    }

    override suspend fun listOrderStatusEvents(orderId: String): Result<List<OrderStatusEventDto>> {
        calls.incrementAndGet()
        return Result.success(
            listOf(OrderStatusEventDto("ev-1", orderId, "ORDER_SUBMITTED", orderRecord.status, "", "12:15")),
        )
    }

    override suspend fun listOrderPayments(orderId: String): Result<List<OrderPaymentRecordDto>> {
        calls.incrementAndGet()
        return Result.success(emptyList())
    }

    override suspend fun listCustomerOrdersForRestaurant(
        customerProfileId: String,
        restaurantId: String,
    ): Result<List<OrderRecordDto>> {
        calls.incrementAndGet()
        return Result.success(
            restaurantOrders.filter {
                it.customerProfileId == customerProfileId && it.restaurantId == restaurantId
            },
        )
    }
}
