package com.adischat.app.feature.restaurants.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Light-mode ModalBottomSheet presenting restaurant-scoped Active Orders and Order History
 * triggered from the prominent Active Orders header action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantOrdersModalSheet(
    restaurantName: String,
    activeOrders: List<RestaurantOrderSummaryUi>,
    historyOrders: List<RestaurantOrderSummaryUi>,
    selectedSection: RestaurantWorkspaceTab,
    selectedOrderId: String?,
    activeTrackingSnapshot: RestaurantOrderTrackingUiState?,
    onSelectSection: (RestaurantWorkspaceTab) -> Unit,
    onSelectOrder: (String) -> Unit,
    onBackToOrderList: () -> Unit,
    onRefreshOrdersClick: () -> Unit,
    onOrderMoreFoodClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visibleTab = if (selectedSection == RestaurantWorkspaceTab.HISTORY) {
        RestaurantWorkspaceTab.HISTORY
    } else {
        RestaurantWorkspaceTab.MY_ORDERS
    }
    val currentPool = if (visibleTab == RestaurantWorkspaceTab.HISTORY) historyOrders else activeOrders
    val inspectedOrder = selectedOrderId?.let { id ->
        currentPool.firstOrNull { it.orderId == id }
    }

    val handleBack: () -> Unit = {
        if (inspectedOrder != null) {
            onBackToOrderList()
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = extColors.mainBackground,
        scrimColor = Color.Black.copy(alpha = 0.36f),
    ) {
        BackHandler(onBack = handleBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (inspectedOrder != null) {
                    IconButton(onClick = onBackToOrderList) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to orders list",
                            tint = extColors.primaryText,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (inspectedOrder == null) 8.dp else 0.dp),
                ) {
                    Text(
                        text = if (inspectedOrder != null) {
                            "Order #${inspectedOrder.orderNumber}"
                        } else {
                            "Orders • $restaurantName"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = extColors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = inspectedOrder?.let { "${it.branchName} • ${it.tableLabel}" }
                            ?: "Active preparation & restaurant history",
                        style = MaterialTheme.typography.labelSmall,
                        color = extColors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onRefreshOrdersClick) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh orders",
                        tint = extColors.secondaryText,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close orders",
                        tint = extColors.secondaryText,
                    )
                }
            }

            if (inspectedOrder == null) {
                OrdersSectionSwitcher(
                    visibleTab = visibleTab,
                    activeCount = activeOrders.size,
                    historyCount = historyOrders.size,
                    onSelectTab = onSelectSection,
                )
            }

            HorizontalDivider(color = extColors.divider)

            if (inspectedOrder != null) {
                val resolvedSnapshot = activeTrackingSnapshot
                    ?.takeIf { it.orderId == inspectedOrder.orderId }
                    ?: inspectedOrder.trackingSnapshot
                InspectedOrderDetailBody(
                    order = inspectedOrder,
                    trackingSnapshot = resolvedSnapshot,
                    multiActiveOrders = if (visibleTab == RestaurantWorkspaceTab.MY_ORDERS) activeOrders else emptyList(),
                    onSwitchActiveOrder = onSelectOrder,
                    onOrderMoreFoodClick = onOrderMoreFoodClick,
                    modifier = Modifier.weight(1f, fill = false),
                )
            } else {
                OrdersListBody(
                    restaurantName = restaurantName,
                    orders = currentPool,
                    isHistory = visibleTab == RestaurantWorkspaceTab.HISTORY,
                    onOrderClick = { onSelectOrder(it.orderId) },
                    onOrderMoreFoodClick = onOrderMoreFoodClick,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
private fun OrdersSectionSwitcher(
    visibleTab: RestaurantWorkspaceTab,
    activeCount: Int,
    historyCount: Int,
    onSelectTab: (RestaurantWorkspaceTab) -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val tabs = listOf(
            RestaurantWorkspaceTab.MY_ORDERS to "Active Orders ($activeCount)",
            RestaurantWorkspaceTab.HISTORY to "Order History ($historyCount)",
        )
        tabs.forEach { (tab, title) ->
            val selected = visibleTab == tab
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectTab(tab) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) extColors.brandGreenSoft else extColors.mutedSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) extColors.brandGreen else Color.Transparent,
                ),
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) extColors.brandGreenDeep else extColors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrdersListBody(
    restaurantName: String,
    orders: List<RestaurantOrderSummaryUi>,
    isHistory: Boolean,
    onOrderClick: (RestaurantOrderSummaryUi) -> Unit,
    onOrderMoreFoodClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extColors = RestaurantsLightTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (orders.isEmpty()) {
                item {
                    RestaurantsStateMessageCard(
                        icon = if (isHistory) Icons.Rounded.Storefront else Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = if (isHistory) "No order history at $restaurantName" else "No active orders",
                        message = if (isHistory) {
                            "Completed, cancelled, or declined orders placed here with your account will appear under History."
                        } else {
                            "Your active dine-in orders for $restaurantName will appear here once placed."
                        },
                        actionLabel = if (!isHistory) "Order Food" else null,
                        onActionClick = if (!isHistory) onOrderMoreFoodClick else null,
                    )
                }
            } else {
                items(orders, key = { it.orderId }) { order ->
                    LightOrderSummaryCard(
                        order = order,
                        onClick = { onOrderClick(order) },
                    )
                }
            }
        }

        if (!isHistory && orders.isNotEmpty()) {
            HorizontalDivider(color = extColors.divider)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(extColors.mainBackground)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                OutlinedButton(
                    onClick = onOrderMoreFoodClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Order More Food",
                        fontWeight = FontWeight.SemiBold,
                        color = extColors.brandGreenDeep,
                    )
                }
            }
        }
    }
}

@Composable
private fun LightOrderSummaryCard(
    order: RestaurantOrderSummaryUi,
    onClick: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    val statusColors = when {
        order.status.isTerminalNegative -> extColors.errorSoft to extColors.error
        order.status == RestaurantOrderStatus.COMPLETED -> extColors.brandGreenSoft to extColors.brandGreenDeep
        else -> extColors.brandGreenSoft to extColors.brandGreenDeep
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = extColors.cardSurface,
        border = BorderStroke(1.dp, extColors.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Order #${order.orderNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = extColors.primaryText,
                    )
                    if (order.isSimulatedQa) {
                        RestaurantTagPill(
                            label = "Simulated QA",
                            containerColor = extColors.qaNoticeBackground,
                            contentColor = extColors.qaNoticeText,
                        )
                    }
                }
                RestaurantTagPill(
                    label = order.status.timelineDisplayLabel,
                    containerColor = statusColors.first,
                    contentColor = statusColors.second,
                )
            }

            Text(
                text = "${order.branchName} • ${order.tableLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = extColors.secondaryText,
            )

            Text(
                text = order.itemsSummaryLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = extColors.primaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            HorizontalDivider(color = extColors.divider)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = RestaurantsUiReducers.formatEtbMinor(order.totalMinor),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = extColors.primaryText,
                    )
                    val paymentLabel = if (order.paymentStatus == PaymentStatusUi.PAID) {
                        "Payment: Paid at Restaurant"
                    } else {
                        "Payment: Pay at Restaurant (Unpaid)"
                    }
                    Text(
                        text = listOfNotNull(
                            paymentLabel,
                            order.createdAtLabel.takeIf { it.isNotBlank() },
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = extColors.secondaryText,
                    )
                }
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = extColors.brandGreenDeep,
                )
            }
        }
    }
}

@Composable
private fun InspectedOrderDetailBody(
    order: RestaurantOrderSummaryUi,
    trackingSnapshot: RestaurantOrderTrackingUiState?,
    multiActiveOrders: List<RestaurantOrderSummaryUi>,
    onSwitchActiveOrder: (String) -> Unit,
    onOrderMoreFoodClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extColors = RestaurantsLightTheme.colors
    val steps = resolveSheetPipelineSteps(order = order, trackingSnapshot = trackingSnapshot)
    val terminalReason = trackingSnapshot?.terminalReason
    val lines = trackingSnapshot?.items.orEmpty()
    val paymentStatus = trackingSnapshot?.paymentStatus ?: order.paymentStatus
    val receiptRef = trackingSnapshot?.paymentReceiptRef

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (multiActiveOrders.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        multiActiveOrders.forEach { active ->
                            FilterChip(
                                selected = active.orderId == order.orderId,
                                onClick = { onSwitchActiveOrder(active.orderId) },
                                label = { Text("#${active.orderNumber} (${active.status.timelineDisplayLabel})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = extColors.brandGreenSoft,
                                    selectedLabelColor = extColors.brandGreenDeep,
                                ),
                            )
                        }
                    }
                }
            }

            if (order.isSimulatedQa) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = extColors.qaNoticeBackground,
                        border = BorderStroke(1.dp, extColors.qaNoticeBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Simulated QA Order (Local Only)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = extColors.qaNoticeText,
                            )
                            Text(
                                text = "Offline fixture/demo order only — no real kitchen progress is simulated.",
                                style = MaterialTheme.typography.bodySmall,
                                color = extColors.qaNoticeText,
                            )
                        }
                    }
                }
            }

            if (order.status.isTerminalNegative) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = extColors.errorSoft,
                        border = BorderStroke(1.dp, extColors.error.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cancel,
                                contentDescription = null,
                                tint = extColors.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = order.status.timelineDisplayLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = extColors.error,
                                )
                                Text(
                                    text = terminalReason?.takeIf { it.isNotBlank() }
                                        ?: "Please contact restaurant staff for details.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extColors.primaryText,
                                )
                            }
                        }
                    }
                }
            }

            // Context Card: Restaurant, Branch & Table
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extColors.cardSurface,
                    border = BorderStroke(1.dp, extColors.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Restaurant,
                            contentDescription = null,
                            tint = extColors.brandGreen,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = order.restaurantName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = extColors.primaryText,
                            )
                            Text(
                                text = "${order.branchName} • ${order.tableLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = extColors.secondaryText,
                            )
                        }
                    }
                }
            }

            // 5-Step Kitchen Progression Card (or terminal timeline)
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extColors.cardSurface,
                    border = BorderStroke(1.dp, extColors.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Preparation Status",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = extColors.primaryText,
                        )
                        steps.forEach { step ->
                            SheetTimelineStepRow(step = step)
                        }
                    }
                }
            }

            // Separate Payment Status Card
            item {
                val isPaid = paymentStatus == PaymentStatusUi.PAID
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extColors.cardSurface,
                    border = BorderStroke(1.dp, extColors.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Payments,
                                contentDescription = null,
                                tint = extColors.brandGreen,
                                modifier = Modifier.size(18.dp),
                            )
                            Column {
                                Text(
                                    text = "Payment Status (Separate)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extColors.secondaryText,
                                )
                                Text(
                                    text = if (isPaid) "Paid at Restaurant" else "Unpaid • Pay at Restaurant",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isPaid) extColors.brandGreenDeep else extColors.primaryText,
                                )
                                if (!receiptRef.isNullOrBlank()) {
                                    Text(
                                        text = "Receipt: $receiptRef",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extColors.secondaryText,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Ordered Items & Total
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extColors.cardSurface,
                    border = BorderStroke(1.dp, extColors.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Items Ordered",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = extColors.primaryText,
                        )
                        if (lines.isNotEmpty()) {
                            lines.forEach { line ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${line.quantity}x ${line.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = extColors.primaryText,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = RestaurantsUiReducers.formatEtbMinor(line.lineTotalMinor),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = extColors.primaryText,
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = order.itemsSummaryLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = extColors.primaryText,
                            )
                        }
                        HorizontalDivider(color = extColors.divider, modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = extColors.primaryText,
                            )
                            Text(
                                text = RestaurantsUiReducers.formatEtbMinor(order.totalMinor),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = extColors.primaryText,
                            )
                        }
                    }
                }
            }
        }

        if (order.isActiveOrder) {
            HorizontalDivider(color = extColors.divider)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(extColors.mainBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = onOrderMoreFoodClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extColors.brandGreen,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                ) {
                    Text(
                        text = "Order More Food",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetTimelineStepRow(step: OrderTimelineStepUi) {
    val extColors = RestaurantsLightTheme.colors
    val dotBg = when (step.stepState) {
        TimelineStepState.COMPLETED -> extColors.brandGreen
        TimelineStepState.CURRENT -> extColors.brandGreenSoft
        TimelineStepState.UPCOMING -> extColors.mutedSurface
        TimelineStepState.TERMINATED -> extColors.errorSoft
    }
    val dotBorder = when (step.stepState) {
        TimelineStepState.COMPLETED -> extColors.brandGreen
        TimelineStepState.CURRENT -> extColors.brandGreen
        TimelineStepState.UPCOMING -> extColors.cardBorder
        TimelineStepState.TERMINATED -> extColors.error
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.size(22.dp),
            shape = CircleShape,
            color = dotBg,
            border = BorderStroke(1.5.dp, dotBorder),
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (step.stepState) {
                    TimelineStepState.COMPLETED -> Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )

                    TimelineStepState.CURRENT -> Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(extColors.brandGreen, CircleShape),
                    )

                    TimelineStepState.TERMINATED -> Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = extColors.error,
                        modifier = Modifier.size(13.dp),
                    )

                    TimelineStepState.UPCOMING -> Unit
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            val stepLabel = when (step.status) {
                RestaurantOrderStatus.ACCEPTED -> "Accepted"
                else -> step.status.timelineDisplayLabel
            }
            Text(
                text = stepLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (step.stepState == TimelineStepState.CURRENT) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                },
                color = when (step.stepState) {
                    TimelineStepState.UPCOMING -> extColors.secondaryText
                    TimelineStepState.TERMINATED -> extColors.error
                    else -> extColors.primaryText
                },
            )
            if (!step.timestampLabel.isNullOrBlank()) {
                Text(
                    text = step.timestampLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = extColors.secondaryText,
                )
            }
        }
    }
}

private fun resolveSheetPipelineSteps(
    order: RestaurantOrderSummaryUi,
    trackingSnapshot: RestaurantOrderTrackingUiState?,
): List<OrderTimelineStepUi> {
    val activeFiveStages = listOf(
        RestaurantOrderStatus.ORDER_SUBMITTED,
        RestaurantOrderStatus.ACCEPTED,
        RestaurantOrderStatus.PREPARING,
        RestaurantOrderStatus.READY,
        RestaurantOrderStatus.SERVED,
    )
    val base = trackingSnapshot?.timelineSteps?.takeIf { it.isNotEmpty() }
        ?: OrderTrackingReducers.buildTimelineSteps(order.status)

    if (order.status.isTerminalNegative) {
        return base
    }
    val byStatus = base.associateBy { it.status }
    val currentStatus = order.status
    val currentIdx = if (currentStatus == RestaurantOrderStatus.COMPLETED) {
        activeFiveStages.size
    } else {
        activeFiveStages.indexOf(currentStatus).coerceAtLeast(0)
    }

    return activeFiveStages.mapIndexed { idx, stage ->
        val existing = byStatus[stage]
        val stepState = when {
            idx < currentIdx || currentStatus == RestaurantOrderStatus.COMPLETED -> TimelineStepState.COMPLETED
            idx == currentIdx -> TimelineStepState.CURRENT
            else -> TimelineStepState.UPCOMING
        }
        OrderTimelineStepUi(
            status = stage,
            title = stage.timelineDisplayLabel,
            subtitle = existing?.subtitle.orEmpty(),
            stepState = stepState,
            timestampLabel = existing?.timestampLabel,
        )
    }
}
