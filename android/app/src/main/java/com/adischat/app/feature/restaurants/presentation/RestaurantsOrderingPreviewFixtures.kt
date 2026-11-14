package com.adischat.app.feature.restaurants.presentation

/**
 * Isolated offline-only Jetpack Compose preview fixtures for Phase R3E
 * table scan, dine-in cart review, and order tracking screens.
 *
 * Strictly for `@Preview` composables — never fabricate live orders or payment
 * confirmations in production repositories or live UI state.
 */
object RestaurantsOrderingPreviewFixtures {

    val previewDiningSession = ActiveDiningSessionUi(
        ownerAdisChatUid = "preview-uid-1",
        restaurantId = "prev-rest-habesha",
        restaurantName = "Habesha Hearth",
        branchId = "prev-br-bole",
        branchName = "Bole Atlas Flagship",
        tableId = "prev-table-4",
        tableLabel = "Table 4 • Patio",
        diningSessionId = "prev-session-001",
        expiresAt = "Today until 10:30 PM",
    )

    val previewCartState = RestaurantCartUiState(
        activeSession = previewDiningSession,
        items = listOf(
            DineInCartItemUi(
                lineId = "item-shekla-tibs|opt-single,opt-med",
                menuItemId = "item-shekla-tibs",
                itemName = "Clay-Pot Shekla Tibs",
                restaurantId = "prev-rest-habesha",
                branchId = "prev-br-bole",
                baseUnitPriceMinor = 68000L,
                selectedOptions = listOf(
                    SelectedCartOptionUi("opt-single", "Solo Platter", "Portion Size", 0L),
                    SelectedCartOptionUi("opt-med", "Medium Mitmita", "Heat Level", 0L),
                    SelectedCartOptionUi("opt-ayib", "Fresh Ayib Cottage Cheese", "Optional Table Add-ons", 9500L),
                ),
                quantity = 2,
                specialInstructions = "Serve hot in clay pot",
            ),
            DineInCartItemUi(
                lineId = "item-jebena-buna|",
                menuItemId = "item-jebena-buna",
                itemName = "Jebena Buna Service (For 2)",
                restaurantId = "prev-rest-habesha",
                branchId = "prev-br-bole",
                baseUnitPriceMinor = 28000L,
                selectedOptions = emptyList(),
                quantity = 1,
            ),
        ),
        customerNotes = "Water with lemon if available",
        checkoutPhase = CartCheckoutPhase.Idle,
        isDineInEnabled = false,
    )

    fun previewPreparingTrackingState(): RestaurantOrderTrackingUiState {
        val status = RestaurantOrderStatus.PREPARING
        return RestaurantOrderTrackingUiState(
            loadState = RestaurantsLoadState.Content,
            syncState = OrderTrackingSyncState.Synced,
            orderId = "prev-ord-1",
            orderNumber = "ORD-2026-0142",
            restaurantName = previewDiningSession.restaurantName,
            branchName = previewDiningSession.branchName,
            tableLabel = previewDiningSession.tableLabel,
            currentStatus = status,
            paymentStatus = PaymentStatusUi.UNPAID,
            confirmedTotalMinor = 183000L,
            items = listOf(
                TrackingOrderLineUi("l1", "Clay-Pot Shekla Tibs", 2, 155000L, "Serve hot"),
                TrackingOrderLineUi("l2", "Jebena Buna Service (For 2)", 1, 28000L),
            ),
            timelineSteps = OrderTrackingReducers.buildTimelineSteps(status),
            staffUpdates = listOf(
                KitchenStaffUpdateUi(
                    id = "u2",
                    status = RestaurantOrderStatus.PREPARING,
                    title = "Preparing in Kitchen",
                    detail = "Chef started grilling your Clay-Pot Shekla Tibs.",
                    timestampLabel = "12:18 PM",
                ),
                KitchenStaffUpdateUi(
                    id = "u1",
                    status = RestaurantOrderStatus.ACCEPTED,
                    title = "Accepted by Restaurant",
                    detail = "Server Dawit confirmed Table 4 order.",
                    timestampLabel = "12:14 PM",
                ),
            ),
        )
    }

    fun previewRejectedTrackingState(): RestaurantOrderTrackingUiState {
        val status = RestaurantOrderStatus.REJECTED
        return RestaurantOrderTrackingUiState(
            loadState = RestaurantsLoadState.Content,
            syncState = OrderTrackingSyncState.OfflineOrReconnecting(
                "Offline — showing last confirmed status. Tap Refresh when back online.",
            ),
            orderId = "prev-ord-2",
            orderNumber = "ORD-2026-0139",
            restaurantName = previewDiningSession.restaurantName,
            branchName = previewDiningSession.branchName,
            tableLabel = previewDiningSession.tableLabel,
            currentStatus = status,
            terminalReason = "Kitchen ran out of prime beef ribs for this shift.",
            paymentStatus = PaymentStatusUi.UNPAID,
            confirmedTotalMinor = 68000L,
            items = listOf(
                TrackingOrderLineUi("l1", "Clay-Pot Shekla Tibs", 1, 68000L),
            ),
            timelineSteps = OrderTrackingReducers.buildTimelineSteps(status),
            staffUpdates = listOf(
                KitchenStaffUpdateUi(
                    id = "u1",
                    status = RestaurantOrderStatus.REJECTED,
                    title = "Order Declined",
                    detail = "Kitchen ran out of prime beef ribs for this shift.",
                    timestampLabel = "11:42 AM",
                ),
            ),
        )
    }
}
