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
import androidx.compose.foundation.layout.offset
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

    val activeBrandColor = RestaurantsLightPalette.parseHexColor(
        hex = restaurant?.brandColorHex ?: menuState.brandColorHex,
        fallback = extColors.brandGreen,
    )
    val activeBrandTextColor = RestaurantsLightPalette.getContrastTextColor(activeBrandColor)

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
            coverUrl = restaurant?.coverUrl ?: menuState.coverUrl,
            logoUrl = restaurant?.logoUrl ?: menuState.logoUrl,
            brandColorHex = restaurant?.brandColorHex ?: menuState.brandColorHex,
            isVerified = restaurant?.isVerified ?: menuState.isVerified,
            rating = restaurant?.rating ?: menuState.rating,
            reviewCount = restaurant?.reviewCount ?: menuState.reviewCount,
            isDemoPreview = menuState.isDemoPreview,
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
                                containerColor = activeBrandColor,
                                contentColor = activeBrandTextColor,
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
                                containerColor = activeBrandColor,
                                contentColor = activeBrandTextColor,
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
    coverUrl: String? = restaurant?.coverUrl,
    logoUrl: String? = restaurant?.logoUrl,
    brandColorHex: String? = restaurant?.brandColorHex,
    isVerified: Boolean = restaurant?.isVerified ?: true,
    rating: Double? = restaurant?.rating,
    reviewCount: Int? = restaurant?.reviewCount,
    isDemoPreview: Boolean = false,
) {
    val extColors = RestaurantsLightTheme.colors
    val applyStatusPadding = LocalRestaurantsTopBarStatusPadding.current
    val openStatusLabel = restaurant?.isOpenNowLabel?.takeIf { it.isNotBlank() }
        ?: if (selectedBranch?.isDineInActive == false) "Dine-in Paused" else "Dine-in Open"
    val brandColor = RestaurantsLightPalette.parseHexColor(brandColorHex, extColors.brandGreen)
    var branchMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        if (!coverUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
            ) {
                RestaurantArtworkImage(
                    imageUrl = coverUrl,
                    contentDescription = "$displayName cover",
                    aspectRatio = null,
                    cornerRadius = 0.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.35f),
                                ),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (applyStatusPadding) it.statusBarsPadding() else it }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.94f),
                        modifier = Modifier.size(36.dp),
                    ) {
                        IconButton(onClick = onBackClick, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search dishes",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(15.dp),
                            )
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search dishes...",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = Color(0xFF0F172A),
                                    ),
                                    cursorBrush = SolidColor(brandColor),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onSearchQueryChange("") },
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onActiveOrdersClick),
                        shape = RoundedCornerShape(50),
                        color = if (activeOrderCount > 0) Color.White else Color.White.copy(alpha = 0.92f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (activeOrderCount > 0) brandColor else Color(0xFFE2E8F0),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                                contentDescription = "Active Orders",
                                tint = if (activeOrderCount > 0) brandColor else Color(0xFF0F172A),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Active ($activeOrderCount)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = if (activeOrderCount > 0) brandColor else Color(0xFF0F172A),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (coverUrl.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (applyStatusPadding) it.statusBarsPadding() else it }
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onActiveOrdersClick),
                        shape = RoundedCornerShape(50),
                        color = if (activeOrderCount > 0) Color.White else Color.White.copy(alpha = 0.9f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (activeOrderCount > 0) brandColor else Color(0xFFE2E8F0),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                                contentDescription = "Active Orders",
                                tint = if (activeOrderCount > 0) brandColor else Color(0xFF0F172A),
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "Active ($activeOrderCount)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = if (activeOrderCount > 0) brandColor else Color(0xFF0F172A),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (!coverUrl.isNullOrBlank()) it.offset(y = (-18).dp) else it
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RestaurantLogoAvatar(
                    logoUrl = logoUrl,
                    brandName = displayName,
                    size = 46.dp,
                    brandColor = brandColor,
                    borderWidth = 2.5.dp,
                    borderColor = Color.White,
                    cornerRadius = 12.dp,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isVerified) {
                            RestaurantVerifiedBadge(showLabel = false)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (rating != null) {
                            RestaurantRatingPill(
                                rating = rating,
                                reviewCount = reviewCount,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(0.6.dp, Color(0xFFE2E8F0)),
                        ) {
                            Text(
                                text = openStatusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (!coverUrl.isNullOrBlank()) it.offset(y = (-12).dp) else it
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box {
                    val hasMultipleBranches = branches.size > 1
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = hasMultipleBranches) { branchMenuExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(0.8.dp, Color(0xFFE2E8F0)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = brandColor,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = selectedBranch?.name ?: "Main Branch",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (hasMultipleBranches) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Select branch",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = branchMenuExpanded,
                        onDismissRequest = { branchMenuExpanded = false },
                        containerColor = Color.White,
                    ) {
                        branches.forEach { branch ->
                            val isSelected = branch.id == selectedBranch?.id
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = branch.name,
                                        color = if (isSelected) brandColor else Color(0xFF0F172A),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                trailingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = brandColor,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else null,
                                onClick = {
                                    branchMenuExpanded = false
                                    onBranchSelected(branch.id)
                                },
                            )
                        }
                    }
                }

                val isSeated = matchingSession != null
                val tableLabel = when {
                    isSeated -> matchingSession!!.tableLabel
                    otherBranchSession != null -> "Other: ${otherBranchSession.tableLabel}"
                    else -> "Scan Table QR"
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onScanTableQrClick),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSeated) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSeated) Color(0xFF6EE7B7) else Color(0xFFE2E8F0),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = "Scan Table QR",
                            tint = if (isSeated) Color(0xFF047857) else brandColor,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = tableLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (isSeated) Color(0xFF047857) else Color(0xFF334155),
                            maxLines = 1,
                        )
                    }
                }
            }

            if (isDemoPreview) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(0.8.dp, Color(0xFFFDE68A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .let {
                            if (!coverUrl.isNullOrBlank()) it.offset(y = (-8).dp) else it
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "QA DEMO MODE • Fictional restaurant & menu preview (Zero charges)",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
    }
}
