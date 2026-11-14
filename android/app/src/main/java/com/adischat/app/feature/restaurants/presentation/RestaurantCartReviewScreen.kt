package com.adischat.app.feature.restaurants.presentation

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
 * Isolated native dine-in cart and order submission review screen (Phase R3E offline foundation).
 * Supports DINE_IN + PAY_AT_RESTAURANT only, binds cart strictly to restaurant/branch/session,
 * prevents duplicate submits while in-flight, and highlights server-authoritative pricing.
 */
@Composable
fun RestaurantCartReviewScreen(
    state: RestaurantCartUiState,
    onIncrementQuantity: (lineId: String, currentQty: Int) -> Unit,
    onDecrementQuantity: (lineId: String, currentQty: Int) -> Unit,
    onRemoveItem: (lineId: String) -> Unit,
    onCustomerNotesChange: (String) -> Unit,
    onSubmitDineInOrder: () -> Unit,
    onScanTableClick: () -> Unit,
    onBrowseMenuClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    offlineDemoActionLabel: String? = null,
    onOfflineDemoSubmitClick: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RestaurantsTopBar(
                title = "Review Dine-In Order",
                subtitle = state.activeSession?.let { "${it.restaurantName} • ${it.tableLabel}" }
                    ?: "No active table session",
                onBackClick = onBackClick,
                trailingContent = {
                    RestaurantTagPill(label = "Pay at Restaurant")
                },
            )
        },
        bottomBar = {
            if (state.isDineInEnabled && state.activeSession != null && state.items.isNotEmpty()) {
                CartCheckoutBottomBar(
                    estimatedSubtotalMinor = state.estimatedSubtotalMinor,
                    itemCount = state.totalItemCount,
                    isSubmitting = state.isSubmitting,
                    canSubmit = state.canSubmitOrder,
                    onSubmitClick = onSubmitDineInOrder,
                )
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

                state.activeSession == null -> {
                    RestaurantsStateMessageCard(
                        icon = Icons.Rounded.QrCodeScanner,
                        title = "No active table session",
                        message = state.invalidationNotice
                            ?: "Scan the QR code at your restaurant table before reviewing or submitting a dine-in order.",
                        actionLabel = "Scan Table QR",
                        onActionClick = onScanTableClick,
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }

                state.items.isEmpty() -> {
                    RestaurantsStateMessageCard(
                        icon = Icons.Rounded.Restaurant,
                        title = "Your table cart is empty",
                        message = state.invalidationNotice
                            ?: "Browse dishes from ${state.activeSession.restaurantName} (${state.activeSession.branchName}) to build your dine-in order.",
                        actionLabel = "Browse Menu",
                        onActionClick = onBrowseMenuClick,
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
                        if (!state.invalidationNotice.isNullOrBlank()) {
                            item {
                                DineInNoticeBanner(
                                    headline = "Session Updated",
                                    supportingText = state.invalidationNotice,
                                    icon = Icons.Rounded.Info,
                                )
                            }
                        }

                        item {
                            ActiveTableContextCard(session = state.activeSession)
                        }

                        if (state.checkoutPhase is CartCheckoutPhase.Failed) {
                            item {
                                RestaurantsStateMessageCard(
                                    icon = Icons.Rounded.ErrorOutline,
                                    title = "Order not submitted",
                                    message = state.checkoutPhase.message,
                                    isError = true,
                                    modifier = Modifier.padding(0.dp),
                                )
                            }
                        }

                        items(
                            items = state.items,
                            key = { it.lineId },
                        ) { item ->
                            DineInCartLineCard(
                                item = item,
                                controlsEnabled = !state.isSubmitting,
                                onIncrement = { onIncrementQuantity(item.lineId, item.quantity) },
                                onDecrement = { onDecrementQuantity(item.lineId, item.quantity) },
                                onRemove = { onRemoveItem(item.lineId) },
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = state.customerNotes,
                                onValueChange = onCustomerNotesChange,
                                enabled = !state.isSubmitting,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                                label = { Text("Notes for kitchen or waiter (optional)") },
                                placeholder = { Text("Allergies, table cutlery, or serving preference...") },
                            )
                        }

                        item {
                            PaymentAtRestaurantSummaryCard(
                                estimatedSubtotalMinor = state.estimatedSubtotalMinor,
                            )
                        }
                        if (!offlineDemoActionLabel.isNullOrBlank() && onOfflineDemoSubmitClick != null) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "Offline QA Order Simulation",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                        Text(
                                            text = "Real order submission stays blocked offline. Use this demo action to simulate a local-only order in My Orders and test tracking without claiming real restaurant acceptance.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                        OutlinedButton(
                                            onClick = onOfflineDemoSubmitClick,
                                            enabled = state.items.isNotEmpty() && !state.isSubmitting,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(offlineDemoActionLabel)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTableContextCard(session: ActiveDiningSessionUi) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${session.restaurantName} • ${session.branchName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${session.tableLabel} • Dine-In Session Verified",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DineInCartLineCard(
    item: DineInCartItemUi,
    controlsEnabled: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = item.itemName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (item.selectedOptions.isNotEmpty()) {
                        Text(
                            text = item.selectedOptions.joinToString(" • ") { opt ->
                                if (opt.priceDeltaMinor > 0) {
                                    "${opt.optionName} (${RestaurantsUiReducers.formatPriceDeltaMinor(opt.priceDeltaMinor)})"
                                } else {
                                    opt.optionName
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!item.specialInstructions.isNullOrBlank()) {
                        Text(
                            text = "Note: ${item.specialInstructions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RestaurantsUiDefaults.WarmAmberAccent,
                        )
                    }
                }

                IconButton(
                    onClick = onRemove,
                    enabled = controlsEnabled,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove item",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = RestaurantsUiReducers.formatEtbMinor(item.lineTotalMinor),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = onDecrement,
                        enabled = controlsEnabled,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Decrease quantity",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = item.quantity.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    FilledTonalIconButton(
                        onClick = onIncrement,
                        enabled = controlsEnabled && item.quantity < DineInCartReducers.MAX_ITEM_QUANTITY,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Increase quantity",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentAtRestaurantSummaryCard(estimatedSubtotalMinor: Long) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
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
            ) {
                Text(
                    text = "Order Type",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Dine-In Only",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Payment Method",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Pay Directly at Restaurant",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Estimated Subtotal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = RestaurantsUiReducers.formatEtbMinor(estimatedSubtotalMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Final bill, applicable taxes, and branch prices are calculated by the restaurant server upon submission. No online charge happens in AdisChat — pay directly at the restaurant.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CartCheckoutBottomBar(
    estimatedSubtotalMinor: Long,
    itemCount: Int,
    isSubmitting: Boolean,
    canSubmit: Boolean,
    onSubmitClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$itemCount item${if (itemCount == 1) "" else "s"} • Est.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = RestaurantsUiReducers.formatEtbMinor(estimatedSubtotalMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = onSubmitClick,
                enabled = canSubmit && !isSubmitting,
                modifier = Modifier.height(46.dp),
                shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submitting...")
                } else {
                    Text("Submit Dine-In Order")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RestaurantCartReviewScreenPreview() {
    RestaurantCartReviewScreen(
        state = RestaurantsOrderingPreviewFixtures.previewCartState.copy(isDineInEnabled = true),
        onIncrementQuantity = { _, _ -> },
        onDecrementQuantity = { _, _ -> },
        onRemoveItem = {},
        onCustomerNotesChange = {},
        onSubmitDineInOrder = {},
        onScanTableClick = {},
        onBrowseMenuClick = {},
        onBackClick = {},
    )
}
