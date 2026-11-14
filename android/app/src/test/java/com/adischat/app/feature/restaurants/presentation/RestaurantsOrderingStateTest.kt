package com.adischat.app.feature.restaurants.presentation

import com.adischat.app.feature.restaurants.data.OrderItemRecordDto
import com.adischat.app.feature.restaurants.data.OrderPaymentRecordDto
import com.adischat.app.feature.restaurants.data.OrderRecordDto
import com.adischat.app.feature.restaurants.data.OrderStatusEventDto
import com.adischat.app.feature.restaurants.data.ResolveQrResponse
import com.adischat.app.feature.restaurants.data.ResolvedEntityDto
import com.adischat.app.feature.restaurants.data.ResolvedTableDto
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RestaurantsErrorMapper
import com.adischat.app.feature.restaurants.data.RestaurantsFailure
import com.adischat.app.feature.restaurants.data.SubmitOrderResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class RestaurantsOrderingStateTest {

    private val sessionUserA = ActiveDiningSessionUi(
        ownerAdisChatUid = "uid-user-a",
        restaurantId = "prev-rest-habesha",
        restaurantName = "Habesha Hearth",
        branchId = "prev-br-bole",
        branchName = "Bole Atlas Flagship",
        tableId = "t-4",
        tableLabel = "Table 4",
        diningSessionId = "ds-001",
        expiresAt = "2026-11-10T22:00:00Z",
    )

    @Test
    fun `qr scan handles waiter activation, session code errors, revoked qr, and raw token redaction`() {
        val disabled = TableQrScanReducers.initialState(RestaurantsEnvironment(isDineInEnabled = false))
        assertEquals(TableScanPhase.Disabled, disabled.phase)

        var state = TableQrScanReducers.initialState(RestaurantsEnvironment(isDineInEnabled = true))
        val secretQrToken = "qr-secret-998877"

        // Capture QR -> Resolving
        state = TableQrScanReducers.onQrTokenCaptured(state, secretQrToken)
        assertEquals(TableScanPhase.Resolving, state.phase)
        assertTrue(state.hasTokenCaptured)

        // Waiter activation required -> keeps token privately for Retry Check without exposing token in message
        state = TableQrScanReducers.onResolveFailure(
            state,
            RestaurantsFailure.Conflict(
                "dining_session_not_activated",
                "Ask your waiter to activate this table $secretQrToken",
            ),
        )
        val waiterPhase = state.phase as? TableScanPhase.NeedsWaiterActivation
        assertNotNull(waiterPhase)
        assertEquals("Ask your waiter to activate this table", waiterPhase?.message)
        assertFalse(waiterPhase!!.message.contains(secretQrToken))

        // Retry -> back to Resolving, then backend requests session code
        state = TableQrScanReducers.onRetryWaiterCheck(state)
        assertEquals(TableScanPhase.Resolving, state.phase)
        state = TableQrScanReducers.onResolveFailure(
            state,
            RestaurantsFailure.Validation("session_code_required", "Enter the waiter session code."),
        )
        assertTrue(state.phase is TableScanPhase.NeedsSessionCode)

        // Submitting empty code shows validation error locally
        state = TableQrScanReducers.beginSessionCodeSubmit(state)
        assertNotNull((state.phase as TableScanPhase.NeedsSessionCode).validationError)

        // Entering wrong code returns invalid_session_code prompt
        state = TableQrScanReducers.onSessionCodeChanged(state, "WXYZ")
        state = TableQrScanReducers.beginSessionCodeSubmit(state)
        assertEquals(TableScanPhase.Resolving, state.phase)
        state = TableQrScanReducers.onResolveFailure(
            state,
            RestaurantsFailure.Validation("invalid_session_code", "Code incorrect."),
        )
        val invalidPhase = state.phase as? TableScanPhase.NeedsSessionCode
        assertNotNull(invalidPhase?.validationError)

        // Valid code -> Confirmed
        state = TableQrScanReducers.onSessionCodeChanged(state, "A7K2")
        state = TableQrScanReducers.beginSessionCodeSubmit(state)
        state = TableQrScanReducers.onResolveSuccess(
            state,
            ResolveQrResponse(
                ok = true,
                restaurant = ResolvedEntityDto("prev-rest-habesha", "Habesha Hearth"),
                branch = ResolvedEntityDto("prev-br-bole", "Bole Atlas Flagship"),
                table = ResolvedTableDto("t-4", "Table 4"),
                diningSessionId = "ds-001",
                expiresAt = "2026-11-10T22:00:00Z",
            ),
            ownerAdisChatUid = "uid-user-a",
        )
        assertTrue(state.phase is TableScanPhase.Confirmed)
        assertFalse(state.hasTokenCaptured)

        // Revoked QR wipes captured token and shows expiry state
        var revoked = TableQrScanReducers.initialState(RestaurantsEnvironment(isDineInEnabled = true))
        revoked = TableQrScanReducers.onQrTokenCaptured(revoked, secretQrToken)
        revoked = TableQrScanReducers.onResolveFailure(
            revoked,
            RestaurantsFailure.Conflict("qr_revoked_or_invalid", "Leak check: $secretQrToken"),
        )
        val expiredPhase = revoked.phase as? TableScanPhase.SessionExpiredOrClosed
        assertNotNull(expiredPhase)
        assertTrue(expiredPhase!!.isQrInvalidOrRevoked)
        assertFalse(expiredPhase.message.contains(secretQrToken))
        assertFalse(revoked.hasTokenCaptured)

        // Closed session check
        val closedMapped = RestaurantsErrorMapper.mapHttpStatusAndBody(
            409,
            """{"ok":false,"error":{"code":"dining_session_expired","message":"Expired"}}""",
        )
        assertEquals("dining_session_expired", closedMapped.code)
    }

    @Test
    fun `cart enforces tenant, branch, and dining session isolation and clears on switch`() {
        var cart = RestaurantCartUiState(isDineInEnabled = true)
        val tibs = RestaurantsPreviewFixtures.menuItemsFor("prev-rest-habesha", "prev-br-bole")
            .first { it.id == "item-shekla-tibs" }
        val sheet = MenuItemSheetState.initialFor(tibs)

        // Adding without an active table session is rejected
        val withoutSession = DineInCartReducers.addItemFromSheet(cart, sheet, "prev-br-bole")
        assertTrue(withoutSession is CartMutationOutcome.Rejected)

        // Bind Table Session A
        cart = DineInCartReducers.bindActiveSession(cart, sessionUserA)

        // Item from another branch of the same restaurant is rejected
        val wrongBranch = DineInCartReducers.addItemFromSheet(cart, sheet, "prev-br-cmс")
        assertTrue(wrongBranch is CartMutationOutcome.Rejected)

        // Unavailable item at branch is rejected
        val unavailableItem = tibs.copy(isAvailableAtBranch = false)
        val unavailableOutcome = DineInCartReducers.addItemFromSheet(
            cart,
            MenuItemSheetState.initialFor(unavailableItem),
            "prev-br-bole",
        )
        assertTrue(unavailableOutcome is CartMutationOutcome.Rejected)

        // Valid add succeeds and calculates option deltas + quantities accurately
        val configuredSheet = sheet
            .toggleOption("og-portion", "opt-share") // +380 ETB -> 1060 ETB
            .incrementQuantity() // qty = 2 -> 2120 ETB
        val added = DineInCartReducers.addItemFromSheet(cart, configuredSheet, "prev-br-bole", "No garlic")
        assertTrue(added is CartMutationOutcome.Updated)
        cart = (added as CartMutationOutcome.Updated).state
        assertEquals(212000L, cart.estimatedSubtotalMinor)
        assertEquals(2, cart.totalItemCount)

        // Switching branch or dining session immediately invalidates previous cart items
        val sessionOtherBranch = sessionUserA.copy(
            branchId = "prev-br-cmс",
            diningSessionId = "ds-999",
        )
        val switchedCart = DineInCartReducers.bindActiveSession(cart, sessionOtherBranch)
        assertTrue(switchedCart.items.isEmpty())
        assertEquals("ds-999", switchedCart.activeSession?.diningSessionId)
        assertNotNull(switchedCart.invalidationNotice)

        // Switching AdisChat account wipes session and cart completely
        val accountSwitched = DineInCartReducers.reconcileAccountSwitch(cart, "uid-user-b")
        assertNull(accountSwitched.activeSession)
        assertTrue(accountSwitched.items.isEmpty())
        assertNotNull(accountSwitched.invalidationNotice)
    }

    @Test
    fun `checkout prevents duplicate submit, enforces dine-in pay-at-restaurant, and preserves idempotency key on retry`() {
        val tibs = RestaurantsPreviewFixtures.menuItemsFor("prev-rest-habesha", "prev-br-bole")
            .first { it.id == "item-shekla-tibs" }
        var cart = DineInCartReducers.bindActiveSession(
            RestaurantCartUiState(isDineInEnabled = true),
            sessionUserA,
        )
        cart = (DineInCartReducers.addItemFromSheet(cart, MenuItemSheetState.initialFor(tibs), "prev-br-bole")
            as CartMutationOutcome.Updated).state

        val keyCounter = AtomicInteger(0)
        val fakeUuid = { "idem-uuid-${keyCounter.incrementAndGet()}" }

        // First submit starts Submitting with idem-uuid-1
        cart = DineInCartReducers.beginCheckoutAttempt(cart, fakeUuid)
        val submitting1 = cart.checkoutPhase as CartCheckoutPhase.Submitting
        assertEquals("idem-uuid-1", submitting1.idempotencyKey)

        // Duplicate click while Submitting does nothing and creates no second UUID
        val duplicate = DineInCartReducers.beginCheckoutAttempt(cart, fakeUuid)
        assertEquals(cart, duplicate)
        assertEquals(1, keyCounter.get())

        // Editing cart while Submitting is blocked
        val blockedQtyEdit = DineInCartReducers.updateItemQuantity(cart, cart.items.first().lineId, 4)
        assertEquals(1, blockedQtyEdit.items.first().quantity)

        // Verify exact request payload enforces DINE_IN + PAY_AT_RESTAURANT
        val request = DineInCartReducers.buildSubmitOrderRequest(cart, submitting1.idempotencyKey)!!
        assertEquals("DINE_IN", request.orderType)
        assertEquals("PAY_AT_RESTAURANT", request.paymentMethod)
        assertEquals("ds-001", request.diningSessionId)
        assertEquals("idem-uuid-1", request.idempotencyKey)

        // Failure retains the SAME idempotency key for safe retry
        cart = DineInCartReducers.onSubmitFailed(cart, "Temporary connection issue.")
        assertTrue(cart.checkoutPhase is CartCheckoutPhase.Failed)
        cart = DineInCartReducers.beginCheckoutAttempt(cart, fakeUuid)
        val retrySubmitting = cart.checkoutPhase as CartCheckoutPhase.Submitting
        assertEquals("idem-uuid-1", retrySubmitting.idempotencyKey)
        assertEquals(1, keyCounter.get())

        // Modifying the cart after failure resets idempotency key so the next attempt gets a fresh UUID
        cart = DineInCartReducers.onSubmitFailed(cart, "Try adjusting item.")
        cart = DineInCartReducers.updateItemQuantity(cart, cart.items.first().lineId, 2)
        assertNull(cart.pendingIdempotencyKey)
        cart = DineInCartReducers.beginCheckoutAttempt(cart, fakeUuid)
        val freshKey = (cart.checkoutPhase as CartCheckoutPhase.Submitting).idempotencyKey
        assertNotEquals("idem-uuid-1", freshKey)
        assertEquals("idem-uuid-2", freshKey)
    }

    @Test
    fun `order tracking handles progression, rejected or cancelled terminal states, payment status, offline retry, and logout cleanup`() {
        val initialResult = SubmitOrderResultDto(
            orderId = "ord-1",
            orderNumber = "ORD-100",
            status = "ORDER_SUBMITTED",
            paymentMethod = "PAY_AT_RESTAURANT",
            paymentStatus = "UNPAID",
            totalMinor = 68000L,
            idempotentReplay = false,
        )
        var tracking = OrderTrackingReducers.fromServerSubmitResult(
            result = initialResult,
            session = sessionUserA,
            submittedItems = emptyList(),
            isDineInEnabled = true,
        )
        assertEquals(RestaurantOrderStatus.ORDER_SUBMITTED, tracking.currentStatus)
        assertEquals(PaymentStatusUi.UNPAID, tracking.paymentStatus)
        assertEquals(6, tracking.timelineSteps.size)
        assertEquals(TimelineStepState.CURRENT, tracking.timelineSteps.first().stepState)

        // Apply PREPARING snapshot
        tracking = OrderTrackingReducers.applyServerSnapshot(
            state = tracking,
            order = OrderRecordDto(
                id = "ord-1",
                orderNumber = "ORD-100",
                restaurantId = sessionUserA.restaurantId,
                branchId = sessionUserA.branchId,
                customerProfileId = "prof-1",
                status = "PREPARING",
                paymentStatus = "UNPAID",
                totalMinor = 68000L,
            ),
            items = listOf(OrderItemRecordDto("oi-1", "ord-1", "mi-1", "Shekla Tibs", 68000L, 1, 68000L)),
            events = listOf(
                OrderStatusEventDto("e1", "ord-1", "ORDER_SUBMITTED", "ACCEPTED", "Waiter approved", "12:01"),
                OrderStatusEventDto("e2", "ord-1", "ACCEPTED", "PREPARING", "On grill", "12:04"),
            ),
            payments = emptyList(),
        )
        assertEquals(RestaurantOrderStatus.PREPARING, tracking.currentStatus)
        assertEquals(PaymentStatusUi.UNPAID, tracking.paymentStatus)
        assertEquals(TimelineStepState.COMPLETED, tracking.timelineSteps[0].stepState)
        assertEquals(TimelineStepState.COMPLETED, tracking.timelineSteps[1].stepState)
        assertEquals(TimelineStepState.CURRENT, tracking.timelineSteps[2].stepState)
        assertEquals(2, tracking.staffUpdates.size)

        // Payment confirmed at restaurant -> PAID with receipt ref
        tracking = OrderTrackingReducers.applyServerSnapshot(
            state = tracking,
            order = OrderRecordDto(
                id = "ord-1",
                orderNumber = "ORD-100",
                restaurantId = sessionUserA.restaurantId,
                branchId = sessionUserA.branchId,
                customerProfileId = "prof-1",
                status = "COMPLETED",
                paymentStatus = "PAID",
                totalMinor = 68000L,
            ),
            items = emptyList(),
            events = emptyList(),
            payments = listOf(
                OrderPaymentRecordDto("p1", "ord-1", "PAY_AT_RESTAURANT", "PAID", 68000L, "RCPT-7788"),
            ),
        )
        assertEquals(RestaurantOrderStatus.COMPLETED, tracking.currentStatus)
        assertEquals(PaymentStatusUi.PAID, tracking.paymentStatus)
        assertEquals("RCPT-7788", tracking.paymentReceiptRef)
        assertTrue(tracking.timelineSteps.all { it.stepState == TimelineStepState.COMPLETED })

        // Offline refresh preserves last known snapshot and switches sync state cleanly
        tracking = OrderTrackingReducers.onRefreshStarted(tracking)
        assertTrue(tracking.syncState is OrderTrackingSyncState.Refreshing)
        val offline = RestaurantsErrorMapper.mapThrowable(IOException("timeout"))
        tracking = OrderTrackingReducers.onRefreshFailed(tracking, offline)
        assertTrue(tracking.syncState is OrderTrackingSyncState.OfflineOrReconnecting)

        // REJECTED & CANCELLED terminal timeline states
        val rejected = OrderTrackingReducers.applyServerSnapshot(
            state = tracking,
            order = OrderRecordDto(
                id = "ord-2",
                orderNumber = "ORD-101",
                restaurantId = sessionUserA.restaurantId,
                branchId = sessionUserA.branchId,
                customerProfileId = "prof-1",
                status = "REJECTED",
                paymentStatus = "UNPAID",
                totalMinor = 68000L,
            ),
            items = emptyList(),
            events = listOf(
                OrderStatusEventDto("e3", "ord-2", "ORDER_SUBMITTED", "REJECTED", "Kitchen closing", "12:10"),
            ),
            payments = emptyList(),
        )
        assertEquals(RestaurantOrderStatus.REJECTED, rejected.currentStatus)
        assertEquals("Kitchen closing", rejected.terminalReason)
        assertEquals(TimelineStepState.TERMINATED, rejected.timelineSteps.last().stepState)

        val cancelledSteps = OrderTrackingReducers.buildTimelineSteps(RestaurantOrderStatus.CANCELLED)
        assertEquals(RestaurantOrderStatus.CANCELLED, cancelledSteps.last().status)
        assertEquals(TimelineStepState.TERMINATED, cancelledSteps.last().stepState)

        // Account switch clears tracking state cleanly
        val cleared = OrderTrackingReducers.clearForAccountSwitchOrSignOut()
        assertEquals(RestaurantsLoadState.Empty, cleared.loadState)
        assertTrue(cleared.orderId.isEmpty())
    }

    @Test
    fun `quick add adds simple items immediately but requires sheet when mandatory options exist`() {
        val simpleItem = MenuItemUiModel(
            id = "item-beyaynetu",
            restaurantId = sessionUserA.restaurantId,
            categoryId = "cat-sides",
            name = "Beyaynetu",
            description = "Fasting platter",
            basePriceMinor = 35000L,
            isAvailableAtBranch = true,
            optionGroups = listOf(
                MenuOptionGroupUiModel(
                    id = "og-opt",
                    name = "Optional Spice",
                    isRequired = false,
                    minSelect = 0,
                    maxSelect = 1,
                    options = listOf(MenuOptionUiModel("opt-1", "og-opt", "Extra Mitmita", 2000L, true)),
                ),
            ),
        )
        val requiredOptionItem = MenuItemUiModel(
            id = "item-shekla-tibs",
            restaurantId = sessionUserA.restaurantId,
            categoryId = "cat-tibs",
            name = "Shekla Tibs",
            description = "Sizzling beef",
            basePriceMinor = 68000L,
            isAvailableAtBranch = true,
            optionGroups = listOf(
                MenuOptionGroupUiModel(
                    id = "og-portion",
                    name = "Portion Size",
                    isRequired = true,
                    minSelect = 1,
                    maxSelect = 1,
                    options = listOf(
                        MenuOptionUiModel("opt-solo", "og-portion", "Solo", 0L, true),
                        MenuOptionUiModel("opt-share", "og-portion", "Shared", 38000L, true),
                    ),
                ),
            ),
        )

        var cart = RestaurantCartUiState(
            activeSession = sessionUserA,
            isDineInEnabled = true,
            pendingIdempotencyKey = "stale-key",
        )

        // 1. Simple item quick-add updates cart immediately without sheet & resets stale idempotency key
        val added1 = DineInCartReducers.quickAddItem(cart, simpleItem, sessionUserA.branchId)
        assertTrue(added1 is QuickAddOutcome.Updated)
        cart = (added1 as QuickAddOutcome.Updated).state
        assertEquals(1, cart.totalItemCount)
        assertEquals(35000L, cart.estimatedSubtotalMinor)
        assertNull(cart.pendingIdempotencyKey)

        // Second quick-add increments quantity cleanly
        val added2 = DineInCartReducers.quickAddItem(cart, simpleItem, sessionUserA.branchId)
        cart = (added2 as QuickAddOutcome.Updated).state
        assertEquals(2, cart.totalItemCount)
        assertEquals(70000L, cart.estimatedSubtotalMinor)

        // 2. Item with required option group must trigger customization sheet instead of adding silently
        val reqOutcome = DineInCartReducers.quickAddItem(cart, requiredOptionItem, sessionUserA.branchId)
        assertTrue(reqOutcome is QuickAddOutcome.RequiresCustomization)
        assertFalse((reqOutcome as QuickAddOutcome.RequiresCustomization).sheetState.isBrowseOnly)
        assertEquals(2, cart.totalItemCount)

        // 3. Unavailable item, wrong branch, and missing session are rejected safely
        val soldOut = DineInCartReducers.quickAddItem(cart, simpleItem.copy(isAvailableAtBranch = false), sessionUserA.branchId)
        assertTrue(soldOut is QuickAddOutcome.Rejected)
        val wrongBranch = DineInCartReducers.quickAddItem(cart, simpleItem, "other-branch")
        assertTrue(wrongBranch is QuickAddOutcome.Rejected)
        val noSession = DineInCartReducers.quickAddItem(cart.copy(activeSession = null), simpleItem, sessionUserA.branchId)
        assertTrue(noSession is QuickAddOutcome.Rejected)
    }

    @Test
    fun `safe decrement removes single line and flags multi-variant customized lines without guessing`() {
        val baseItem = MenuItemUiModel(
            id = "item-shekla-tibs",
            restaurantId = sessionUserA.restaurantId,
            categoryId = "cat-tibs",
            name = "Shekla Tibs",
            description = "",
            basePriceMinor = 68000L,
            isAvailableAtBranch = true,
            optionGroups = listOf(
                MenuOptionGroupUiModel(
                    id = "og-portion",
                    name = "Portion Size",
                    isRequired = true,
                    minSelect = 1,
                    maxSelect = 1,
                    options = listOf(
                        MenuOptionUiModel("opt-solo", "og-portion", "Solo", 0L, true),
                        MenuOptionUiModel("opt-share", "og-portion", "Shared", 38000L, true),
                    ),
                ),
            ),
        )

        var cart = RestaurantCartUiState(activeSession = sessionUserA, isDineInEnabled = true)

        // Empty cart decrement is a safe NoOp
        assertTrue(DineInCartReducers.quickDecrementItem(cart, baseItem.id, sessionUserA.branchId) is QuickDecrementOutcome.NoOp)

        // Add one Solo customized line (qty 2) -> safe single-line decrement goes 2 -> 1 -> empty
        val soloSheet = MenuItemSheetState(
            item = baseItem,
            selectedOptionsByGroup = mapOf("og-portion" to setOf("opt-solo")),
            quantityPreview = 2,
            isBrowseOnly = false,
        )
        cart = (DineInCartReducers.addItemFromSheet(cart, soloSheet, sessionUserA.branchId) as CartMutationOutcome.Updated).state
        val dec1 = DineInCartReducers.quickDecrementItem(cart, baseItem.id, sessionUserA.branchId)
        cart = (dec1 as QuickDecrementOutcome.Updated).state
        assertEquals(1, cart.totalItemCount)

        // Add a second distinct Shared variant -> now 2 customized lines exist -> decrement requires resolution
        val shareSheet = MenuItemSheetState(
            item = baseItem,
            selectedOptionsByGroup = mapOf("og-portion" to setOf("opt-share")),
            quantityPreview = 1,
            isBrowseOnly = false,
        )
        cart = (DineInCartReducers.addItemFromSheet(cart, shareSheet, sessionUserA.branchId) as CartMutationOutcome.Updated).state
        val ambiguous = DineInCartReducers.quickDecrementItem(cart, baseItem.id, sessionUserA.branchId)
        assertTrue(ambiguous is QuickDecrementOutcome.RequiresVariantResolution)
        assertEquals(2, (ambiguous as QuickDecrementOutcome.RequiresVariantResolution).matchingLines.size)
        // Cart left untouched until user picks variant in Cart Sheet
        assertEquals(2, cart.totalItemCount)
    }

    @Test
    fun `dine in order mode enables dine in and keeps takeaway and delivery disabled`() {
        assertTrue(DineInOrderMode.DINE_IN.isEnabled)
        assertTrue(DineInOrderMode.DINE_IN.requiresActiveTableSession)
        assertFalse(DineInOrderMode.TAKEAWAY.isEnabled)
        assertEquals("Coming Soon", DineInOrderMode.TAKEAWAY.statusBadge)
        assertFalse(DineInOrderMode.DELIVERY.isEnabled)

        val itemLine = DineInCartItemUi(
            lineId = "l1",
            menuItemId = "m1",
            itemName = "Tea",
            restaurantId = sessionUserA.restaurantId,
            branchId = sessionUserA.branchId,
            baseUnitPriceMinor = 5000L,
            quantity = 1,
        )
        val cart = RestaurantCartUiState(
            activeSession = sessionUserA,
            items = listOf(itemLine),
            isDineInEnabled = true,
        )
        assertTrue(cart.canSubmitOrder)
        assertNotNull(DineInCartReducers.buildSubmitOrderRequest(cart, "idem-1"))

        val takeawayTry = DineInCartReducers.selectOrderMode(cart, DineInOrderMode.TAKEAWAY)
        assertTrue(takeawayTry is CartMutationOutcome.Rejected)
        assertEquals(DineInOrderMode.DINE_IN, (takeawayTry as CartMutationOutcome.Rejected).state.selectedOrderMode)

        val deliveryTry = DineInCartReducers.selectOrderMode(cart, DineInOrderMode.DELIVERY)
        assertTrue(deliveryTry is CartMutationOutcome.Rejected)
        assertEquals(DineInOrderMode.DINE_IN, (deliveryTry as CartMutationOutcome.Rejected).state.selectedOrderMode)

        // Table session check still enforced for DINE_IN
        val unseated = cart.copy(activeSession = null)
        assertFalse(unseated.canSubmitOrder)
        assertNull(DineInCartReducers.buildSubmitOrderRequest(unseated, "idem-1"))
    }
}
