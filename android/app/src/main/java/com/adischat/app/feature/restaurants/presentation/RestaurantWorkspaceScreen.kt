package com.adischat.app.feature.restaurants.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Light-mode unified customer restaurant workspace featuring a compact Philia-style header
 * (branding, branch picker, verified table badge, Switch QR, prominent Active Orders button),
 * full-height left sidebar + continuous food menu, persistent floating cart bar with expandable
 * native cart & multi-step checkout bottom sheets, and Active Orders / History sheet.
 */
@Composable
fun RestaurantWorkspaceScreen(
    restaurant: RestaurantCardUiModel?,
    restaurantId: String,
    menuState: RestaurantMenuUiState,
    cartState: RestaurantCartUiState,
    selectedTab: RestaurantWorkspaceTab,
    orders: List<RestaurantOrderSummaryUi>,
    currentCustomerUid: String?,
    isCartReviewOpen: Boolean,
    activeTrackingState: RestaurantOrderTrackingUiState?,
    activeTrackingIsSimulatedQa: Boolean,
    activeMenuItemSheet: MenuItemSheetState?,
    onSelectTab: (RestaurantWorkspaceTab) -> Unit,
    onBranchSelected: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onMenuItemClick: (MenuItemUiModel) -> Unit,
    onMenuRetry: () -> Unit,
    onScanTableQrClick: () -> Unit,
    onOpenCartReview: () -> Unit,
    onCloseCartReview: () -> Unit,
    onCartIncrementQuantity: (lineId: String, currentQty: Int) -> Unit,
    onCartDecrementQuantity: (lineId: String, currentQty: Int) -> Unit,
    onCartRemoveItem: (lineId: String) -> Unit,
    onCartNotesChange: (String) -> Unit,
    onSubmitDineInOrder: () -> Unit,
    offlineDemoOrderLabel: String? = null,
    onOfflineDemoOrderClick: (() -> Unit)? = null,
    onOpenOrderTracking: (RestaurantOrderSummaryUi) -> Unit,
    onCloseOrderTracking: () -> Unit,
    onRefreshOrdersClick: () -> Unit,
    onRefreshTrackingClick: () -> Unit,
    onOrderMoreFoodClick: () -> Unit,
    onSheetToggleOption: (groupId: String, optionId: String) -> Unit,
    onSheetIncrementQuantity: () -> Unit,
    onSheetDecrementQuantity: () -> Unit,
    onSheetDismiss: () -> Unit,
    onSheetAddToCartClick: () -> Unit,
    onBackToDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    onQuickAddClick: (MenuItemUiModel) -> Unit = onMenuItemClick,
    onQuickDecrementClick: (MenuItemUiModel) -> Unit = { _ -> },
    checkoutStep: CheckoutSheetStep = CheckoutSheetStep.CART_REVIEW,
    onCheckoutStepChange: (CheckoutSheetStep) -> Unit = {},
    onSelectOrderMode: (DineInOrderMode) -> Unit = {},
    isOrdersSheetOpen: Boolean = false,
    selectedOrdersOrderId: String? = null,
    onOpenOrdersSheet: (RestaurantWorkspaceTab) -> Unit = { tab ->
        onSelectTab(tab)
    },
    onCloseOrdersSheet: () -> Unit = onCloseOrderTracking,
    onSelectOrderInSheet: (String) -> Unit = { orderId ->
        orders.firstOrNull { it.orderId == orderId }?.let(onOpenOrderTracking)
    },
    onBackToOrderListInSheet: () -> Unit = onCloseOrderTracking,
) {
    RestaurantsLightTheme {
        val extColors = RestaurantsLightTheme.colors
        Surface(
            modifier = modifier.fillMaxSize(),
            color = extColors.mainBackground,
        ) {
            RestaurantWorkspaceScreenContent(
                restaurant = restaurant,
                restaurantId = restaurantId,
                menuState = menuState,
                cartState = cartState,
                selectedTab = selectedTab,
                orders = orders,
                currentCustomerUid = currentCustomerUid,
                isCartReviewOpen = isCartReviewOpen,
                checkoutStep = checkoutStep,
                activeTrackingState = activeTrackingState,
                activeTrackingIsSimulatedQa = activeTrackingIsSimulatedQa,
                activeMenuItemSheet = activeMenuItemSheet,
                isOrdersSheetOpen = isOrdersSheetOpen,
                selectedOrdersOrderId = selectedOrdersOrderId,
                onSelectTab = onSelectTab,
                onBranchSelected = onBranchSelected,
                onCategorySelected = onCategorySelected,
                onMenuItemClick = onMenuItemClick,
                onQuickAddClick = onQuickAddClick,
                onQuickDecrementClick = onQuickDecrementClick,
                onMenuRetry = onMenuRetry,
                onScanTableQrClick = onScanTableQrClick,
                onOpenCartReview = onOpenCartReview,
                onCloseCartReview = onCloseCartReview,
                onCheckoutStepChange = onCheckoutStepChange,
                onSelectOrderMode = onSelectOrderMode,
                onCartIncrementQuantity = onCartIncrementQuantity,
                onCartDecrementQuantity = onCartDecrementQuantity,
                onCartRemoveItem = onCartRemoveItem,
                onCartNotesChange = onCartNotesChange,
                onSubmitDineInOrder = onSubmitDineInOrder,
                offlineDemoOrderLabel = offlineDemoOrderLabel,
                onOfflineDemoOrderClick = onOfflineDemoOrderClick,
                onOpenOrdersSheet = onOpenOrdersSheet,
                onCloseOrdersSheet = onCloseOrdersSheet,
                onSelectOrderInSheet = onSelectOrderInSheet,
                onBackToOrderListInSheet = onBackToOrderListInSheet,
                onRefreshOrdersClick = onRefreshOrdersClick,
                onOrderMoreFoodClick = onOrderMoreFoodClick,
                onSheetToggleOption = onSheetToggleOption,
                onSheetIncrementQuantity = onSheetIncrementQuantity,
                onSheetDecrementQuantity = onSheetDecrementQuantity,
                onSheetDismiss = onSheetDismiss,
                onSheetAddToCartClick = onSheetAddToCartClick,
                onBackToDiscover = onBackToDiscover,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestaurantWorkspaceScreenContent(
    restaurant: RestaurantCardUiModel?,
    restaurantId: String,
    menuState: RestaurantMenuUiState,
    cartState: RestaurantCartUiState,
    selectedTab: RestaurantWorkspaceTab,
    orders: List<RestaurantOrderSummaryUi>,
    currentCustomerUid: String?,
    isCartReviewOpen: Boolean,
    checkoutStep: CheckoutSheetStep,
    activeTrackingState: RestaurantOrderTrackingUiState?,
    @Suppress("UNUSED_PARAMETER") activeTrackingIsSimulatedQa: Boolean,
    activeMenuItemSheet: MenuItemSheetState?,
    isOrdersSheetOpen: Boolean,
    selectedOrdersOrderId: String?,
    onSelectTab: (RestaurantWorkspaceTab) -> Unit,
    onBranchSelected: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onMenuItemClick: (MenuItemUiModel) -> Unit,
    onQuickAddClick: (MenuItemUiModel) -> Unit,
    onQuickDecrementClick: (MenuItemUiModel) -> Unit,
    onMenuRetry: () -> Unit,
    onScanTableQrClick: () -> Unit,
    onOpenCartReview: () -> Unit,
    onCloseCartReview: () -> Unit,
    onCheckoutStepChange: (CheckoutSheetStep) -> Unit,
    onSelectOrderMode: (DineInOrderMode) -> Unit,
    onCartIncrementQuantity: (lineId: String, currentQty: Int) -> Unit,
    onCartDecrementQuantity: (lineId: String, currentQty: Int) -> Unit,
    onCartRemoveItem: (lineId: String) -> Unit,
    onCartNotesChange: (String) -> Unit,
    onSubmitDineInOrder: () -> Unit,
    offlineDemoOrderLabel: String?,
    onOfflineDemoOrderClick: (() -> Unit)?,
    onOpenOrdersSheet: (RestaurantWorkspaceTab) -> Unit,
    onCloseOrdersSheet: () -> Unit,
    onSelectOrderInSheet: (String) -> Unit,
    onBackToOrderListInSheet: () -> Unit,
    onRefreshOrdersClick: () -> Unit,
    onOrderMoreFoodClick: () -> Unit,
    onSheetToggleOption: (groupId: String, optionId: String) -> Unit,
    onSheetIncrementQuantity: () -> Unit,
    onSheetDecrementQuantity: () -> Unit,
    onSheetDismiss: () -> Unit,
    onSheetAddToCartClick: () -> Unit,
    onBackToDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extColors = RestaurantsLightTheme.colors
    val selectedBranch = menuState.selectedBranch
    val matchingSession = cartState.activeSession?.takeIf { session ->
        session.restaurantId == restaurantId &&
            (selectedBranch == null || session.branchId == selectedBranch.id)
    }
    val sessionForRestaurantOtherBranch = cartState.activeSession?.takeIf { session ->
        session.restaurantId == restaurantId &&
            selectedBranch != null &&
            session.branchId != selectedBranch.id
    }
    val hasValidAuthAndSession = !currentCustomerUid.isNullOrBlank() && matchingSession != null

    val activeOrders = RestaurantOrderIsolationReducers.filterActiveOrdersForRestaurant(
        orders = orders,
        restaurantId = restaurantId,
        customerUid = currentCustomerUid,
    )
    val historyOrders = RestaurantOrderIsolationReducers.filterHistoryOrdersForRestaurant(
        orders = orders,
        restaurantId = restaurantId,
        customerUid = currentCustomerUid,
    )
    val cartQtyByItem = RestaurantOrderIsolationReducers.itemQuantitiesInCartForRestaurant(
        cart = cartState,
        restaurantId = restaurantId,
        branchId = selectedBranch?.id,
    )
    val cartMatchesContext = RestaurantOrderIsolationReducers.cartMatchesRestaurantAndBranch(
        cart = cartState,
        restaurantId = restaurantId,
        branchId = selectedBranch?.id,
    )

    var menuSearchQuery by rememberSaveable(restaurantId) { mutableStateOf("") }
    var activeSpotlight by rememberSaveable(restaurantId) { mutableStateOf(MenuSpotlightFilter.NONE) }
    var inspectedDemoPromo by remember { mutableStateOf<RestaurantPromotionUiModel?>(null) }

    val handleBack: () -> Unit = {
        when {
            activeMenuItemSheet != null -> onSheetDismiss()
            isCartReviewOpen -> onCloseCartReview()
            isOrdersSheetOpen -> onCloseOrdersSheet()
            menuSearchQuery.isNotBlank() || activeSpotlight != MenuSpotlightFilter.NONE -> {
                menuSearchQuery = ""
                activeSpotlight = MenuSpotlightFilter.NONE
            }
            else -> onBackToDiscover()
        }
    }

    BackHandler(onBack = handleBack)

    val displayName = restaurant?.brandName
        ?: menuState.restaurantName.ifBlank { "Restaurant" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(extColors.mainBackground),
    ) {
        RestaurantCompactWorkspaceHeader(
            restaurant = restaurant,
            displayName = displayName,
            branches = menuState.branches,
            selectedBranch = selectedBranch,
            matchingSession = matchingSession,
            otherBranchSession = sessionForRestaurantOtherBranch,
            activeOrderCount = activeOrders.size,
            searchQuery = menuSearchQuery,
            onSearchQueryChange = { menuSearchQuery = it },
            onBackClick = handleBack,
            onBranchSelected = onBranchSelected,
            onScanTableQrClick = onScanTableQrClick,
            onActiveOrdersClick = { onOpenOrdersSheet(RestaurantWorkspaceTab.MY_ORDERS) },
        )

        val showFloatingCartBar = cartMatchesContext && cartState.items.isNotEmpty()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            RestaurantMenuScreen(
                state = menuState,
                onBranchSelected = onBranchSelected,
                onCategorySelected = onCategorySelected,
                onMenuItemClick = onMenuItemClick,
                onRetryClick = onMenuRetry,
                onBackClick = handleBack,
                cartQuantityByItemId = cartQtyByItem,
                showStandaloneChrome = false,
                bottomContentPadding = if (showFloatingCartBar) 92.dp else 24.dp,
                onQuickAddClick = onQuickAddClick,
                onQuickDecrementClick = onQuickDecrementClick,
                searchQuery = menuSearchQuery,
                activeSpotlight = activeSpotlight,
                onSpotlightSelected = { spotlight ->
                    activeSpotlight = spotlight
                },
                onClearFilters = {
                    menuSearchQuery = ""
                    activeSpotlight = MenuSpotlightFilter.NONE
                },
                onPromotionClick = { promo ->
                    inspectedDemoPromo = promo
                },
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = showFloatingCartBar,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                RestaurantFloatingCartBar(
                    itemCount = cartState.totalItemCount,
                    subtotalMinor = cartState.estimatedSubtotalMinor,
                    tableLabel = matchingSession?.tableLabel,
                    onOpenCartSheet = {
                        onCheckoutStepChange(CheckoutSheetStep.CART_REVIEW)
                        onOpenCartReview()
                    },
                    onCheckoutClick = {
                        onCheckoutStepChange(CheckoutSheetStep.CHECKOUT_CONFIRM)
                        onOpenCartReview()
                    },
                )
            }
        }
    }

    val promoNotice = inspectedDemoPromo
    if (promoNotice != null) {
        AlertDialog(
            onDismissRequest = { inspectedDemoPromo = null },
            containerColor = extColors.mainBackground,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.ConfirmationNumber,
                    contentDescription = null,
                    tint = extColors.voucherGoldText,
                )
            },
            title = {
                Text(
                    text = if (promoNotice.isDemoFixture) {
                        "${promoNotice.title} (Offline QA Demo)"
                    } else {
                        promoNotice.title
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = extColors.primaryText,
                )
            },
            text = {
                Text(
                    text = if (promoNotice.isDemoFixture) {
                        "This voucher is local QA fixture data for layout verification only. No promotional discount is deducted from your order total offline."
                    } else {
                        promoNotice.subtitle ?: "Verified restaurant promotion."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = extColors.secondaryText,
                )
            },
            confirmButton = {
                TextButton(onClick = { inspectedDemoPromo = null }) {
                    Text(
                        text = "Got it",
                        color = extColors.brandGreenDeep,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }

    if (activeMenuItemSheet != null) {
        val modalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onSheetDismiss,
            sheetState = modalSheetState,
            containerColor = extColors.mainBackground,
            scrimColor = Color.Black.copy(alpha = 0.36f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                RestaurantMenuItemSheetContent(
                    state = activeMenuItemSheet,
                    onToggleOption = onSheetToggleOption,
                    onIncrementQuantity = onSheetIncrementQuantity,
                    onDecrementQuantity = onSheetDecrementQuantity,
                    modifier = Modifier.weight(1f, fill = false),
                    showBrowseOnlyNotice = !hasValidAuthAndSession,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasValidAuthAndSession) {
                        Button(
                            onClick = onSheetAddToCartClick,
                            enabled = activeMenuItemSheet.isSelectionValid(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extColors.brandGreen,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Add to Cart • ${
                                    RestaurantsUiReducers.formatEtbMinor(
                                        activeMenuItemSheet.previewLineTotalMinor(),
                                    )
                                }",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        val gateReason = when {
                            currentCustomerUid.isNullOrBlank() ->
                                "Sign in with your AdisChat account and scan a table QR to add items."
                            sessionForRestaurantOtherBranch != null ->
                                "Your active table is ${sessionForRestaurantOtherBranch.tableLabel} at ${sessionForRestaurantOtherBranch.branchName}. Switch back to that branch or scan a table QR here."
                            else ->
                                "Browsing menu freely. Scan this branch's table QR to start a dining session and add items."
                        }
                        Text(
                            text = gateReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = extColors.secondaryText,
                        )
                        Button(
                            onClick = {
                                onSheetDismiss()
                                onScanTableQrClick()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extColors.brandGreen,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Table QR to Order", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (isCartReviewOpen) {
        RestaurantCartCheckoutModalSheet(
            cartState = cartState,
            checkoutStep = checkoutStep,
            restaurantName = displayName,
            selectedBranchId = selectedBranch?.id,
            selectedBranchName = selectedBranch?.name,
            onStepChange = onCheckoutStepChange,
            onSelectOrderMode = onSelectOrderMode,
            onIncrementQuantity = onCartIncrementQuantity,
            onDecrementQuantity = onCartDecrementQuantity,
            onRemoveItem = onCartRemoveItem,
            onCustomerNotesChange = onCartNotesChange,
            onSubmitDineInOrder = onSubmitDineInOrder,
            offlineDemoOrderLabel = offlineDemoOrderLabel,
            onOfflineDemoOrderClick = onOfflineDemoOrderClick,
            onScanTableQrClick = onScanTableQrClick,
            onDismiss = onCloseCartReview,
            onViewActiveOrdersClick = {
                onCloseCartReview()
                onOpenOrdersSheet(RestaurantWorkspaceTab.MY_ORDERS)
            },
        )
    }

    if (isOrdersSheetOpen) {
        RestaurantOrdersModalSheet(
            restaurantName = displayName,
            activeOrders = activeOrders,
            historyOrders = historyOrders,
            selectedSection = selectedTab,
            selectedOrderId = selectedOrdersOrderId,
            activeTrackingSnapshot = activeTrackingState,
            onSelectSection = onSelectTab,
            onSelectOrder = onSelectOrderInSheet,
            onBackToOrderList = onBackToOrderListInSheet,
            onRefreshOrdersClick = onRefreshOrdersClick,
            onOrderMoreFoodClick = onOrderMoreFoodClick,
            onDismiss = onCloseOrdersSheet,
        )
    }
}

@Composable
private fun RestaurantCompactWorkspaceHeader(
    restaurant: RestaurantCardUiModel?,
    displayName: String,
    branches: List<RestaurantBranchUiModel>,
    selectedBranch: RestaurantBranchUiModel?,
    matchingSession: ActiveDiningSessionUi?,
    otherBranchSession: ActiveDiningSessionUi?,
    activeOrderCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onBranchSelected: (String) -> Unit,
    onScanTableQrClick: () -> Unit,
    onActiveOrdersClick: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    val applyStatusPadding = LocalRestaurantsTopBarStatusPadding.current
    val openStatusLabel = restaurant?.isOpenNowLabel?.takeIf { it.isNotBlank() }
        ?: if (selectedBranch?.isDineInActive == false) "Dine-in Paused" else "Dine-in Open"
    var branchMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        extColors.headerGradientTop,
                        extColors.headerGradientMid,
                        extColors.mainBackground,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (applyStatusPadding) it.statusBarsPadding() else it }
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Row 1: Back + Restaurant Title + Rounded Search Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = extColors.primaryText,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = extColors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Color(0xFFD6E5DC)),
                    modifier = Modifier
                        .width(136.dp)
                        .height(32.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search dishes",
                            tint = extColors.secondaryText,
                            modifier = Modifier.size(15.dp),
                        )
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                    color = extColors.tertiaryText,
                                    maxLines = 1,
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    color = extColors.primaryText,
                                ),
                                cursorBrush = SolidColor(extColors.brandGreen),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = extColors.secondaryText,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onSearchQueryChange("") },
                            )
                        }
                    }
                }
            }

            // Row 2: Compact Branch + Table Info + Switch QR + Active Orders CTA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box {
                        val hasMultipleBranches = branches.size > 1
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = hasMultipleBranches) { branchMenuExpanded = true }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = extColors.brandGreenDeep,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = selectedBranch?.name ?: "Main Branch",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = extColors.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (hasMultipleBranches) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Select branch",
                                    tint = extColors.secondaryText,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = branchMenuExpanded,
                            onDismissRequest = { branchMenuExpanded = false },
                            containerColor = extColors.mainBackground,
                        ) {
                            branches.forEach { branch ->
                                val isSelected = branch.id == selectedBranch?.id
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = branch.name,
                                            color = if (isSelected) extColors.brandGreenDeep else extColors.primaryText,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = extColors.brandGreen,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        branchMenuExpanded = false
                                        onBranchSelected(branch.id)
                                    },
                                )
                            }
                        }
                    }

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = extColors.secondaryText,
                    )

                    val sessionText = when {
                        matchingSession != null -> matchingSession.tableLabel
                        otherBranchSession != null -> "Other: ${otherBranchSession.tableLabel}"
                        else -> "Browse Mode"
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onScanTableQrClick),
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            matchingSession != null -> Color.White.copy(alpha = 0.85f)
                            otherBranchSession != null -> extColors.qaNoticeBackground
                            else -> Color.White.copy(alpha = 0.72f)
                        },
                        border = BorderStroke(
                            width = 0.8.dp,
                            color = if (matchingSession != null) extColors.brandGreenBorder else extColors.cardBorder,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = "Scan or Switch Table QR",
                                tint = if (matchingSession != null) extColors.brandGreenDeep else extColors.secondaryText,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = sessionText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = if (matchingSession != null) FontWeight.SemiBold else FontWeight.Medium,
                                color = when {
                                    matchingSession != null -> extColors.brandGreenDeep
                                    otherBranchSession != null -> extColors.qaNoticeText
                                    else -> extColors.secondaryText
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onActiveOrdersClick),
                    shape = RoundedCornerShape(50),
                    color = if (activeOrderCount > 0) Color.White else Color.White.copy(alpha = 0.78f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (activeOrderCount > 0) extColors.brandGreen else extColors.cardBorder,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                            contentDescription = "Active Orders",
                            tint = if (activeOrderCount > 0) extColors.brandGreenDeep else extColors.primaryText,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "Active ($activeOrderCount)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (activeOrderCount > 0) extColors.brandGreenDeep else extColors.primaryText,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Row 3: Trust / Status / Promotional Chips (only real or explicitly marked demo data)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val badgeText = restaurant?.highlightBadge?.takeIf { it.isNotBlank() }
                if (badgeText != null) {
                    item(key = "chip_highlight") {
                        val suffix = if (restaurant.isHighlightBadgeDemo) " • QA Demo" else ""
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = extColors.trustGoldSurface,
                            border = BorderStroke(0.8.dp, extColors.trustGoldBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "$badgeText$suffix",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = extColors.trustGoldText,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                item(key = "chip_dine_in") {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.88f),
                        border = BorderStroke(0.8.dp, Color(0xFFDCE7E0)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storefront,
                                contentDescription = null,
                                tint = extColors.brandGreenDeep,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = openStatusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                fontWeight = FontWeight.Medium,
                                color = extColors.primaryText,
                                maxLines = 1,
                            )
                        }
                    }
                }

                val realRating = restaurant?.ratingLabel?.takeIf { it.isNotBlank() }
                if (realRating != null) {
                    item(key = "chip_rating") {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.88f),
                            border = BorderStroke(0.8.dp, Color(0xFFDCE7E0)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = realRating,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = extColors.primaryText,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
