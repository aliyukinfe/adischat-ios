package com.adischat.app.feature.restaurants.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Isolated native dine-in order confirmation and live tracking UI (Phase R3E offline foundation).
 * Renders status timeline (ORDER_SUBMITTED -> COMPLETED), REJECTED/CANCELLED terminal states,
 * UNPAID/PAID restaurant payment status, and manual refresh / offline reconnecting states.
 */
@Composable
fun RestaurantOrderTrackingScreen(
    state: RestaurantOrderTrackingUiState,
    onRefreshClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSimulatedQaOrder: Boolean = false,
    onOrderMoreClick: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RestaurantsTopBar(
                title = if (state.orderNumber.isNotBlank()) "Order ${state.orderNumber}" else "Order Status",
                subtitle = listOfNotNull(
                    state.restaurantName.ifBlank { null },
                    state.tableLabel.ifBlank { null },
                ).joinToString(" • ").ifBlank { null },
                onBackClick = onBackClick,
                trailingContent = {
                    if (state.isDineInEnabled && state.loadState is RestaurantsLoadState.Content) {
                        if (state.syncState is OrderTrackingSyncState.Refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(onClick = onRefreshClick) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh order status",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (onOrderMoreClick != null) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = onOrderMoreClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Order More Food")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                !state.isDineInEnabled -> {
                    RestaurantsDisabledNoticeCard(
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }

                state.loadState is RestaurantsLoadState.Loading -> {
                    RestaurantsLoadingSkeletonList(
                        cardCount = 2,
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }

                state.loadState is RestaurantsLoadState.Error -> {
                    RestaurantsStateMessageCard(
                        icon = Icons.Rounded.ErrorOutline,
                        title = "Unable to load order",
                        message = state.loadState.message,
                        actionLabel = "Retry",
                        onActionClick = onRefreshClick,
                        isError = true,
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }

                state.loadState is RestaurantsLoadState.Empty || state.orderId.isBlank() -> {
                    RestaurantsStateMessageCard(
                        icon = Icons.Rounded.Restaurant,
                        title = "No active dine-in order",
                        message = "Submit an order from your table session to follow kitchen preparation here.",
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                        contentPadding = PaddingValues(
                            start = RestaurantsUiDefaults.ScreenHorizontalPadding,
                            end = RestaurantsUiDefaults.ScreenHorizontalPadding,
                            top = 12.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (isSimulatedQaOrder) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "Simulated QA Order (Local Only)",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                        Text(
                                            text = "Offline demo status only. No real restaurant kitchen received or accepted this order.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }
                        }

                        when (val sync = state.syncState) {
                            is OrderTrackingSyncState.OfflineOrReconnecting -> {
                                item {
                                    DineInNoticeBanner(
                                        headline = "Connection Interrupted",
                                        supportingText = sync.message,
                                        icon = Icons.Rounded.CloudOff,
                                    )
                                }
                            }
                            is OrderTrackingSyncState.Error -> {
                                item {
                                    RestaurantsStateMessageCard(
                                        icon = Icons.Rounded.ErrorOutline,
                                        title = "Could not refresh status",
                                        message = sync.message,
                                        actionLabel = "Retry",
                                        onActionClick = onRefreshClick,
                                        isError = true,
                                        modifier = Modifier.padding(0.dp),
                                    )
                                }
                            }
                            else -> Unit
                        }

                        if (state.currentStatus.isTerminalNegative) {
                            item {
                                TerminalOrderBanner(
                                    status = state.currentStatus,
                                    reason = state.terminalReason,
                                )
                            }
                        }

                        item {
                            PaymentAndSummaryCard(state = state)
                        }

                        item {
                            OrderTimelineCard(steps = state.timelineSteps)
                        }

                        if (state.staffUpdates.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Kitchen & Staff Updates",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                            items(
                                items = state.staffUpdates,
                                key = { it.id },
                            ) { update ->
                                StaffUpdateRowCard(update = update)
                            }
                        }

                        if (state.items.isNotEmpty()) {
                            item {
                                OrderItemsSummaryCard(
                                    items = state.items,
                                    confirmedTotalMinor = state.confirmedTotalMinor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalOrderBanner(
    status: RestaurantOrderStatus,
    reason: String?,
) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.Cancel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = reason?.takeIf { it.isNotBlank() }
                        ?: "Speak with your table server for details or place another order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PaymentAndSummaryCard(state: RestaurantOrderTrackingUiState) {
    val isPaid = state.paymentStatus == PaymentStatusUi.PAID
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.restaurantName.ifBlank { "Dine-In Restaurant" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            state.branchName.ifBlank { null },
                            state.tableLabel.ifBlank { null },
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RestaurantTagPill(
                    label = if (isPaid) "PAID" else "UNPAID",
                    containerColor = if (isPaid) {
                        RestaurantsUiDefaults.DineInGreenSoft
                    } else {
                        RestaurantsUiDefaults.WarmAmberSoft
                    },
                    contentColor = if (isPaid) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        RestaurantsUiDefaults.WarmAmberAccent
                    },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.paymentStatus.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = RestaurantsUiReducers.formatEtbMinor(state.confirmedTotalMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = if (isPaid) {
                    state.paymentReceiptRef?.let { "Staff confirmed payment (Receipt: $it)." }
                        ?: "Staff confirmed your payment at the restaurant."
                } else {
                    "Please pay your waiter or restaurant cashier directly at the restaurant."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OrderTimelineCard(steps: List<OrderTimelineStepUi>) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Order Progress",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            steps.forEachIndexed { index, step ->
                val isLast = index == steps.lastIndex
                val indicatorColor = when (step.stepState) {
                    TimelineStepState.COMPLETED, TimelineStepState.CURRENT -> MaterialTheme.colorScheme.primary
                    TimelineStepState.TERMINATED -> MaterialTheme.colorScheme.error
                    TimelineStepState.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when (step.stepState) {
                                TimelineStepState.COMPLETED -> Icons.Rounded.CheckCircle
                                TimelineStepState.CURRENT -> Icons.Rounded.Schedule
                                TimelineStepState.TERMINATED -> Icons.Rounded.Cancel
                                TimelineStepState.UPCOMING -> Icons.Rounded.RadioButtonUnchecked
                            },
                            contentDescription = null,
                            tint = indicatorColor,
                            modifier = Modifier.size(20.dp),
                        )
                        if (!isLast) {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .width(2.dp)
                                    .height(22.dp)
                                    .background(indicatorColor.copy(alpha = 0.35f)),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (step.stepState == TimelineStepState.CURRENT) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                },
                                color = if (step.stepState == TimelineStepState.UPCOMING) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            step.timestampLabel?.let { ts ->
                                Text(
                                    text = ts,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = step.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffUpdateRowCard(update: KitchenStaffUpdateUi) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = update.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    update.timestampLabel?.let { ts ->
                        Text(
                            text = ts,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                update.detail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderItemsSummaryCard(
    items: List<TrackingOrderLineUi>,
    confirmedTotalMinor: Long,
) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Items (${items.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            items.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${line.quantity}x ${line.name}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!line.notes.isNullOrBlank()) {
                            Text(
                                text = line.notes,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = RestaurantsUiReducers.formatEtbMinor(line.lineTotalMinor),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Confirmed Order Total",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = RestaurantsUiReducers.formatEtbMinor(confirmedTotalMinor),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RestaurantOrderTrackingScreenPreview() {
    RestaurantOrderTrackingScreen(
        state = RestaurantsOrderingPreviewFixtures.previewPreparingTrackingState()
            .copy(isDineInEnabled = true),
        onRefreshClick = {},
        onBackClick = {},
    )
}
