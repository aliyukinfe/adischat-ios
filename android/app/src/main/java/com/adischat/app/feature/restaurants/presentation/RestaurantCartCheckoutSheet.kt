package com.adischat.app.feature.restaurants.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Super-app floating dark-green cart bar inspired by the target UI.
 * Displays bag + quantity badge, mathematically consistent ETB total, optional verified savings,
 * and prominent warm gold/orange Checkout CTA. Tapping summary or swiping up opens the bottom sheet.
 */
@Composable
fun RestaurantFloatingCartBar(
    itemCount: Int,
    subtotalMinor: Long,
    onOpenCartSheet: () -> Unit,
    modifier: Modifier = Modifier,
    verifiedSavingsMinor: Long = 0L,
    tableLabel: String? = null,
    onCheckoutClick: () -> Unit = onOpenCartSheet,
) {
    val extColors = RestaurantsLightTheme.colors
    var verticalDragAccum by remember { mutableFloatStateOf(0f) }
    var triggeredByDrag by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            verticalDragAccum = 0f
                            triggeredByDrag = 0f
                        },
                        onDragEnd = {
                            verticalDragAccum = 0f
                            triggeredByDrag = 0f
                        },
                        onDragCancel = {
                            verticalDragAccum = 0f
                            triggeredByDrag = 0f
                        },
                        onVerticalDrag = { _, dragAmount ->
                            verticalDragAccum += dragAmount
                            if (verticalDragAccum < -28f && triggeredByDrag == 0f) {
                                triggeredByDrag = 1f
                                onOpenCartSheet()
                            }
                        },
                    )
                }
                .clickable(onClick = onOpenCartSheet),
            shape = RoundedCornerShape(28.dp),
            color = extColors.cartBarDarkGreen,
            border = BorderStroke(1.dp, extColors.cartBarDarkGreenBorder),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(modifier = Modifier.padding(end = 2.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.22f),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ShoppingBag,
                                contentDescription = "Cart",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    AnimatedContent(
                        targetState = itemCount,
                        transitionSpec = {
                            fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                        },
                        label = "cart_badge_count",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 5.dp, y = (-4).dp),
                    ) { count ->
                        Surface(
                            shape = CircleShape,
                            color = extColors.sidebarBadgeHot,
                            border = BorderStroke(1.5.dp, extColors.cartBarDarkGreen),
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    AnimatedContent(
                        targetState = subtotalMinor,
                        transitionSpec = {
                            fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                        },
                        label = "cart_total_etb",
                    ) { total ->
                        Text(
                            text = RestaurantsUiReducers.formatEtbMinor(total),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    val subtitleText = when {
                        verifiedSavingsMinor > 0L ->
                            "Saved ${RestaurantsUiReducers.formatEtbMinor(verifiedSavingsMinor)}"
                        !tableLabel.isNullOrBlank() ->
                            "$itemCount item${if (itemCount == 1) "" else "s"} • $tableLabel • Tap or swipe up"
                        else ->
                            "$itemCount item${if (itemCount == 1) "" else "s"} • Tap or swipe up to inspect"
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = Color(0xFFA7F3D0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = onCheckoutClick),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    shadowElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(extColors.checkoutGoldStart, extColors.checkoutGoldEnd),
                                ),
                            )
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Checkout",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 15.sp,
                            ),
                            color = extColors.checkoutGoldText,
                            maxLines = 1,
                        )
                        Text(
                            text = "Order Now",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = extColors.checkoutGoldText.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draggable Material 3 bottom sheet unifying Cart Review (Step A) and Multi-Step
 * Dine-In Checkout (Steps B-E) over the menu without leaving the menu screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantCartCheckoutModalSheet(
    cartState: RestaurantCartUiState,
    checkoutStep: CheckoutSheetStep,
    restaurantName: String,
    selectedBranchId: String?,
    selectedBranchName: String?,
    onStepChange: (CheckoutSheetStep) -> Unit,
    onSelectOrderMode: (DineInOrderMode) -> Unit,
    onIncrementQuantity: (lineId: String, currentQty: Int) -> Unit,
    onDecrementQuantity: (lineId: String, currentQty: Int) -> Unit,
    onRemoveItem: (lineId: String) -> Unit,
    onCustomerNotesChange: (String) -> Unit,
    onSubmitDineInOrder: () -> Unit,
    offlineDemoOrderLabel: String? = null,
    onOfflineDemoOrderClick: (() -> Unit)? = null,
    onScanTableQrClick: () -> Unit,
    onDismiss: () -> Unit,
    onViewActiveOrdersClick: () -> Unit = onDismiss,
) {
    val extColors = RestaurantsLightTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val handleSheetBack: () -> Unit = {
        if (checkoutStep == CheckoutSheetStep.CHECKOUT_CONFIRM) {
            onStepChange(CheckoutSheetStep.CART_REVIEW)
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
        BackHandler(onBack = handleSheetBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Sheet Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (checkoutStep == CheckoutSheetStep.CHECKOUT_CONFIRM && cartState.checkoutPhase !is CartCheckoutPhase.Submitted) {
                    IconButton(onClick = { onStepChange(CheckoutSheetStep.CART_REVIEW) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to cart",
                            tint = extColors.primaryText,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (checkoutStep == CheckoutSheetStep.CART_REVIEW || cartState.checkoutPhase is CartCheckoutPhase.Submitted) 8.dp else 0.dp),
                ) {
                    Text(
                        text = when {
                            cartState.checkoutPhase is CartCheckoutPhase.Submitted -> "Order Confirmation"
                            checkoutStep == CheckoutSheetStep.CART_REVIEW -> "Your Cart (${cartState.totalItemCount})"
                            else -> "Confirm Dine-In Order"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = extColors.primaryText,
                    )
                    Text(
                        text = listOfNotNull(
                            restaurantName.takeIf { it.isNotBlank() },
                            selectedBranchName?.takeIf { it.isNotBlank() },
                            cartState.activeSession?.tableLabel,
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = extColors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close cart",
                        tint = extColors.secondaryText,
                    )
                }
            }

            HorizontalDivider(color = extColors.divider)

            val submittedPhase = cartState.checkoutPhase as? CartCheckoutPhase.Submitted
            if (submittedPhase != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = extColors.brandGreen,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = "Order #${submittedPhase.order.orderNumber} Placed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = extColors.primaryText,
                    )
                    Text(
                        text = "Pay at Restaurant • ${RestaurantsUiReducers.formatEtbMinor(submittedPhase.order.totalMinor)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = extColors.secondaryText,
                    )
                    Button(
                        onClick = onViewActiveOrdersClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extColors.brandGreen,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                    ) {
                        Text("View Active Orders", fontWeight = FontWeight.SemiBold)
                    }
                }
                return@Column
            }

            if (cartState.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ShoppingBag,
                            contentDescription = null,
                            tint = extColors.secondaryText,
                            modifier = Modifier.size(34.dp),
                        )
                        Text(
                            text = "Your cart is empty",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = extColors.primaryText,
                        )
                        Text(
                            text = "Tap + on any available dish on the menu to add items.",
                            style = MaterialTheme.typography.bodySmall,
                            color = extColors.secondaryText,
                        )
                        OutlinedButton(onClick = onDismiss) {
                            Text("Browse Menu")
                        }
                    }
                }
                return@Column
            }

            val session = cartState.activeSession
            val branchMatchesSession = session != null &&
                (selectedBranchId.isNullOrBlank() || session.branchId == selectedBranchId)

            when (checkoutStep) {
                CheckoutSheetStep.CART_REVIEW -> {
                    CartReviewSheetBody(
                        cartState = cartState,
                        onIncrementQuantity = onIncrementQuantity,
                        onDecrementQuantity = onDecrementQuantity,
                        onRemoveItem = onRemoveItem,
                        onProceedToCheckout = { onStepChange(CheckoutSheetStep.CHECKOUT_CONFIRM) },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                CheckoutSheetStep.CHECKOUT_CONFIRM -> {
                    CheckoutConfirmationSheetBody(
                        cartState = cartState,
                        restaurantName = restaurantName,
                        selectedBranchName = selectedBranchName,
                        branchMatchesSession = branchMatchesSession,
                        onSelectOrderMode = onSelectOrderMode,
                        onCustomerNotesChange = onCustomerNotesChange,
                        onScanTableQrClick = {
                            onDismiss()
                            onScanTableQrClick()
                        },
                        onSubmitDineInOrder = onSubmitDineInOrder,
                        offlineDemoOrderLabel = offlineDemoOrderLabel,
                        onOfflineDemoOrderClick = onOfflineDemoOrderClick,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

@Composable
private fun CartReviewSheetBody(
    cartState: RestaurantCartUiState,
    onIncrementQuantity: (lineId: String, currentQty: Int) -> Unit,
    onDecrementQuantity: (lineId: String, currentQty: Int) -> Unit,
    onRemoveItem: (lineId: String) -> Unit,
    onProceedToCheckout: () -> Unit,
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
            items(cartState.items, key = { it.lineId }) { line ->
                SheetCartItemCard(
                    item = line,
                    controlsEnabled = !cartState.isSubmitting,
                    onIncrement = { onIncrementQuantity(line.lineId, line.quantity) },
                    onDecrement = { onDecrementQuantity(line.lineId, line.quantity) },
                    onRemove = { onRemoveItem(line.lineId) },
                )
            }
        }

        HorizontalDivider(color = extColors.divider)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(extColors.mainBackground)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Estimated Subtotal (${cartState.totalItemCount} items)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = extColors.secondaryText,
                )
                Text(
                    text = RestaurantsUiReducers.formatEtbMinor(cartState.estimatedSubtotalMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = extColors.primaryText,
                )
            }
            Button(
                onClick = onProceedToCheckout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = extColors.brandGreen,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = "Proceed to Checkout",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SheetCartItemCard(
    item: DineInCartItemUi,
    controlsEnabled: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = extColors.cardSurface,
        border = BorderStroke(1.dp, extColors.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RestaurantArtworkImage(
                imageUrl = item.imageUrl,
                contentDescription = item.itemName,
                aspectRatio = 1f,
                cornerRadius = 8.dp,
                modifier = Modifier.size(56.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = item.itemName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = extColors.primaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onRemove,
                        enabled = controlsEnabled,
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Remove ${item.itemName}",
                            tint = extColors.secondaryText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (item.selectedOptions.isNotEmpty()) {
                    Text(
                        text = item.selectedOptions.joinToString(" • ") { opt ->
                            val delta = if (opt.priceDeltaMinor > 0) {
                                " (+${RestaurantsUiReducers.formatEtbMinor(opt.priceDeltaMinor)})"
                            } else {
                                ""
                            }
                            "${opt.groupName}: ${opt.optionName}$delta"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = extColors.secondaryText,
                    )
                }

                if (!item.specialInstructions.isNullOrBlank()) {
                    Text(
                        text = "Note: ${item.specialInstructions}",
                        style = MaterialTheme.typography.labelSmall,
                        color = extColors.secondaryText,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = RestaurantsUiReducers.formatEtbMinor(item.lineTotalMinor),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = extColors.primaryText,
                        )
                        if (item.quantity > 1) {
                            Text(
                                text = "${RestaurantsUiReducers.formatEtbMinor(item.unitPriceMinor)} each",
                                style = MaterialTheme.typography.labelSmall,
                                color = extColors.secondaryText,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = extColors.brandGreenSoft,
                        border = BorderStroke(1.dp, extColors.brandGreen),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(30.dp)
                                    .fillMaxHeight()
                                    .clickable(enabled = controlsEnabled, onClick = onDecrement),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Decrease quantity",
                                    tint = extColors.brandGreenDeep,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                text = item.quantity.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = extColors.brandGreenDeep,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .width(30.dp)
                                    .fillMaxHeight()
                                    .clickable(enabled = controlsEnabled, onClick = onIncrement),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Increase quantity",
                                    tint = extColors.brandGreenDeep,
                                    modifier = Modifier.size(16.dp),
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
private fun CheckoutConfirmationSheetBody(
    cartState: RestaurantCartUiState,
    restaurantName: String,
    selectedBranchName: String?,
    branchMatchesSession: Boolean,
    onSelectOrderMode: (DineInOrderMode) -> Unit,
    onCustomerNotesChange: (String) -> Unit,
    onScanTableQrClick: () -> Unit,
    onSubmitDineInOrder: () -> Unit,
    offlineDemoOrderLabel: String?,
    onOfflineDemoOrderClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val extColors = RestaurantsLightTheme.colors
    val session = cartState.activeSession
    val canSubmitRealOrder = cartState.canSubmitOrder && branchMatchesSession && !cartState.isSubmitting

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Step B: Order Mode Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Order Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = extColors.primaryText,
                    )
                    val displayModes = listOf(DineInOrderMode.DINE_IN, DineInOrderMode.TAKEAWAY)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        displayModes.forEach { mode ->
                            val isSelected = cartState.selectedOrderMode == mode && mode.isEnabled
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = mode.isEnabled) { onSelectOrderMode(mode) },
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isSelected -> extColors.brandGreenSoft
                                    mode.isEnabled -> extColors.cardSurface
                                    else -> extColors.mutedSurface
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) extColors.brandGreen else extColors.cardBorder,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (mode.isEnabled) extColors.primaryText else extColors.secondaryText,
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = extColors.brandGreen,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        } else if (!mode.statusBadge.isNullOrBlank()) {
                                            RestaurantTagPill(
                                                label = mode.statusBadge,
                                                containerColor = extColors.mainBackground,
                                                contentColor = extColors.secondaryText,
                                            )
                                        }
                                    }
                                    Text(
                                        text = when (mode) {
                                            DineInOrderMode.DINE_IN -> "Served at your QR table"
                                            DineInOrderMode.TAKEAWAY -> "Available in a later update"
                                            DineInOrderMode.DELIVERY -> "Planned for a future release"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extColors.secondaryText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Step C: Restaurant, Branch & Table Confirmation
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extColors.cardSurface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (branchMatchesSession) extColors.brandGreenBorder else extColors.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Restaurant,
                                    contentDescription = null,
                                    tint = if (branchMatchesSession) extColors.brandGreen else extColors.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Table & Branch Confirmation",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = extColors.primaryText,
                                )
                            }
                            TextButton(onClick = onScanTableQrClick) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Switch QR")
                            }
                        }
                        if (session != null && branchMatchesSession) {
                            Text(
                                text = "${session.restaurantName} • ${selectedBranchName ?: session.branchName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = extColors.primaryText,
                            )
                            Text(
                                text = "${session.tableLabel} • Active Dining Session Verified",
                                style = MaterialTheme.typography.bodySmall,
                                color = extColors.brandGreenDeep,
                            )
                        } else {
                            Text(
                                text = if (session == null) {
                                    "Active table dining session required for ${selectedBranchName ?: restaurantName}."
                                } else {
                                    "Your active table session is at ${session.branchName}, which does not match selected branch ${selectedBranchName ?: ""}."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = extColors.error,
                            )
                        }
                    }
                }
            }

            // Step D: Order Review, Notes & Pay at Restaurant
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
                            text = "Order Summary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = extColors.primaryText,
                        )
                        cartState.items.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${line.quantity}x ${line.itemName}",
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
                        HorizontalDivider(
                            color = extColors.divider,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
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
                                    text = "Pay at Restaurant",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = extColors.primaryText,
                                )
                                Text(
                                    text = "No online payment or browser checkout. Pay staff after dining.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extColors.secondaryText,
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = cartState.customerNotes,
                    onValueChange = onCustomerNotesChange,
                    enabled = !cartState.isSubmitting,
                    label = { Text("Kitchen instructions (optional)") },
                    placeholder = { Text("Less spicy, serve together, allergies...") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (cartState.checkoutPhase is CartCheckoutPhase.Failed) {
                item {
                    Surface(
                        color = extColors.errorSoft,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, extColors.error.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = extColors.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = cartState.checkoutPhase.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = extColors.error,
                            )
                        }
                    }
                }
            }

            if (!offlineDemoOrderLabel.isNullOrBlank() && onOfflineDemoOrderClick != null) {
                item {
                    Surface(
                        color = extColors.qaNoticeBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, extColors.qaNoticeBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Offline QA Demo Action (Local Only)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = extColors.qaNoticeText,
                            )
                            Text(
                                text = "Real backend submission remains disabled offline. Simulate a local QA order to verify Active Orders & multi-round ordering without claiming real restaurant acceptance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = extColors.qaNoticeText,
                            )
                            OutlinedButton(
                                onClick = onOfflineDemoOrderClick,
                                enabled = cartState.items.isNotEmpty() &&
                                    branchMatchesSession &&
                                    !cartState.isSubmitting,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(offlineDemoOrderLabel)
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = extColors.divider)

        // Step E: Place Order Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(extColors.mainBackground)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Order Total",
                    style = MaterialTheme.typography.titleSmall,
                    color = extColors.secondaryText,
                )
                Text(
                    text = RestaurantsUiReducers.formatEtbMinor(cartState.estimatedSubtotalMinor),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = extColors.primaryText,
                )
            }
            Button(
                onClick = onSubmitDineInOrder,
                enabled = canSubmitRealOrder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = extColors.brandGreen,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (cartState.isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Placing Order...")
                } else {
                    Icon(
                        imageVector = if (offlineDemoOrderLabel != null) {
                            Icons.Rounded.Lock
                        } else {
                            Icons.Rounded.CheckCircle
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Place Dine-In Order",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
