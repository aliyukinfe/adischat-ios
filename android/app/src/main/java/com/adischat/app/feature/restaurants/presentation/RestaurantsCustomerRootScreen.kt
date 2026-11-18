package com.adischat.app.feature.restaurants.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.center.RestaurantCenterPortalScreen
import com.adischat.app.feature.restaurants.data.RestaurantsConfig
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.google.firebase.auth.FirebaseAuth

/**
 * Pure gating helpers keeping AdisChat Restaurants strictly isolated from production users
 * and preventing simulated orders outside explicit QA/offline preview contexts.
 */
internal object RestaurantsAppIntegrationGuards {
    fun isCustomerDineInFlowAllowed(environment: RestaurantsEnvironment): Boolean =
        environment.isDineInEnabled

    fun isBackendReadyForLiveCustomerFlow(environment: RestaurantsEnvironment): Boolean =
        environment.isDineInEnabled &&
            environment.isBackendConfigured() &&
            environment.isIdentityBridgeConfigured()

    fun isRestaurantCenterEntryAllowed(environment: RestaurantsEnvironment): Boolean =
        environment.isRestaurantCenterEnabled
}

/**
 * Main-app native Compose root for the AdisChat Restaurants customer flow:
 * AdisChat Home -> Restaurants (Discover) -> Scan Restaurant QR -> Restaurant Menu -> Cart -> Checkout -> Active Orders / History.
 *
 * Strictly gated by [FeatureFlags.RESTAURANTS_DINE_IN_ENABLED] and current authenticated Firebase UID.
 * Never opens customer WebViews or simulates real orders when backend integration is unavailable.
 */
@Composable
fun RestaurantsCustomerRootScreen(
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
    environment: RestaurantsEnvironment = remember { RestaurantsConfig.currentEnvironment },
    onOpenQaPreview: (() -> Unit)? = null,
    initialQrToken: String? = null,
    onInitialQrTokenConsumed: () -> Unit = {},
    initialSearchRestaurantId: String? = null,
    onInitialSearchRestaurantConsumed: () -> Unit = {},
) {
    val allowed = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED &&
        RestaurantsAppIntegrationGuards.isCustomerDineInFlowAllowed(environment)

    RestaurantsSystemBarAppearance(useDarkIcons = true)

    RestaurantsLightTheme {
        val extColors = RestaurantsLightTheme.colors
        if (!allowed) {
            LaunchedEffect(initialQrToken) {
                if (initialQrToken != null) {
                    onInitialQrTokenConsumed()
                }
            }
            BackHandler(onBack = onBackToHome)
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = extColors.mainBackground,
                topBar = {
                    RestaurantsTopBar(
                        title = "Restaurants",
                        subtitle = "Currently unavailable",
                        onBackClick = onBackToHome,
                    )
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    RestaurantsDisabledNoticeCard(
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }
            }
            return@RestaurantsLightTheme
        }

        if (!RestaurantsAppIntegrationGuards.isBackendReadyForLiveCustomerFlow(environment)) {
            BackHandler(onBack = onBackToHome)
            RestaurantsBackendUnavailableScreen(
                statusMessage = environment.stagingStatusMessage("Live dine-in ordering"),
                canOpenQaPreview = FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED && onOpenQaPreview != null,
                onOpenQaPreview = onOpenQaPreview ?: {},
                onBackClick = onBackToHome,
                modifier = modifier,
            )
            return@RestaurantsLightTheme
        }

        var currentFirebaseUid by remember {
            mutableStateOf(
                runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull(),
            )
        }
        DisposableEffect(Unit) {
            val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                currentFirebaseUid = firebaseAuth.currentUser?.uid
            }
            auth?.addAuthStateListener(listener)
            onDispose {
                auth?.removeAuthStateListener(listener)
            }
        }

        if (currentFirebaseUid.isNullOrBlank()) {
            BackHandler(onBack = onBackToHome)
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = extColors.mainBackground,
                topBar = {
                    RestaurantsTopBar(
                        title = "Restaurants",
                        subtitle = "Sign in required",
                        onBackClick = onBackToHome,
                    )
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    RestaurantsStateMessageCard(
                        icon = Icons.Rounded.Lock,
                        title = "AdisChat Account Required",
                        message = "Sign in with your AdisChat account to browse restaurants, scan table QR codes, and view dine-in orders.",
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }
            }
            return@RestaurantsLightTheme
        }

        val customerViewModel: RestaurantsCustomerViewModel = viewModel()
        val coordinator = customerViewModel.coordinator
        val flowState by customerViewModel.state.collectAsStateWithLifecycle()

        var isCartSheetOpen by remember { mutableStateOf(false) }
        var checkoutStep by remember { mutableStateOf(CheckoutSheetStep.CART_REVIEW) }
        var isOrdersSheetOpen by remember { mutableStateOf(false) }
        var selectedOrderIdInSheet by remember { mutableStateOf<String?>(null) }
        var activeRestaurantId by remember { mutableStateOf<String?>(null) }
        var scanReturnRestaurantId by remember { mutableStateOf<String?>(null) }

        val lifecycleOwner = LocalLifecycleOwner.current
        var isForegroundStarted by remember(lifecycleOwner) {
            mutableStateOf(
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            )
        }
        var wasBackgrounded by remember { mutableStateOf(false) }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, _ ->
                isForegroundStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                coordinator.setOrderTrackingVisibility(orderId = null, isForegrounded = false)
            }
        }

        val visibleTrackedOrderId = when (val dest = flowState.destination) {
            is RestaurantsCustomerDestination.OrderTracking -> dest.orderId
            else -> if (isOrdersSheetOpen) selectedOrderIdInSheet?.takeIf { it.isNotBlank() } else null
        }

        LaunchedEffect(visibleTrackedOrderId, isForegroundStarted, currentFirebaseUid) {
            coordinator.setOrderTrackingVisibility(
                orderId = visibleTrackedOrderId,
                isForegrounded = isForegroundStarted,
            )
        }

        LaunchedEffect(isForegroundStarted) {
            if (!isForegroundStarted) {
                wasBackgrounded = true
            } else if (wasBackgrounded) {
                wasBackgrounded = false
                val activeTrackedId = visibleTrackedOrderId ?: return@LaunchedEffect
                val tracking = flowState.orderTrackingState
                val isTerminal = tracking.orderId == activeTrackedId && tracking.currentStatus.isTerminal
                if (!isTerminal) {
                    coordinator.refreshActiveOrder(activeTrackedId)
                }
            }
        }

        LaunchedEffect(initialQrToken, currentFirebaseUid) {
            val token = initialQrToken?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
            coordinator.openTableScan()
            coordinator.submitScannedQrToken(token)
            onInitialQrTokenConsumed()
        }

        LaunchedEffect(currentFirebaseUid) {
            coordinator.reconcileAccountState()
            isCartSheetOpen = false
            checkoutStep = CheckoutSheetStep.CART_REVIEW
            isOrdersSheetOpen = false
            selectedOrderIdInSheet = null
            coordinator.loadDiscover()
        }

        LaunchedEffect(initialSearchRestaurantId, currentFirebaseUid) {
            val restaurantId = initialSearchRestaurantId?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
            coordinator.openRestaurantProfile(restaurantId)
            onInitialSearchRestaurantConsumed()
        }

        LaunchedEffect(flowState.destination) {
            when (val dest = flowState.destination) {
                is RestaurantsCustomerDestination.Menu -> {
                    activeRestaurantId = dest.restaurantId
                }
                is RestaurantsCustomerDestination.Profile -> {
                    activeRestaurantId = dest.restaurantId
                }
                is RestaurantsCustomerDestination.OrderTracking -> {
                    isCartSheetOpen = false
                    checkoutStep = CheckoutSheetStep.CART_REVIEW
                    isOrdersSheetOpen = true
                    selectedOrderIdInSheet = dest.orderId
                }
                is RestaurantsCustomerDestination.CartReview -> {
                    isCartSheetOpen = true
                    checkoutStep = CheckoutSheetStep.CART_REVIEW
                }
                else -> Unit
            }
        }

        when (val destination = flowState.destination) {
            is RestaurantsCustomerDestination.Discover -> {
                BackHandler(onBack = onBackToHome)
                RestaurantsDiscoverScreen(
                    state = flowState.discoverState,
                    onSearchQueryChange = coordinator::updateSearchQuery,
                    onCuisineFilterSelected = coordinator::selectCuisineFilter,
                    onRestaurantClick = { card ->
                        activeRestaurantId = card.id
                        isCartSheetOpen = false
                        checkoutStep = CheckoutSheetStep.CART_REVIEW
                        isOrdersSheetOpen = false
                        selectedOrderIdInSheet = null
                        coordinator.openRestaurantMenu(card.id)
                        coordinator.loadCustomerOrdersForRestaurant(card.id)
                    },
                    onRetryClick = coordinator::loadDiscover,
                    onBackClick = onBackToHome,
                    onScanQrClick = {
                        scanReturnRestaurantId = null
                        coordinator.openTableScan()
                    },
                    modifier = modifier,
                )
            }

            is RestaurantsCustomerDestination.TableScan -> {
                val returnToDiscoverOrMenu: () -> Unit = {
                    val targetRestId = scanReturnRestaurantId
                    if (!targetRestId.isNullOrBlank()) {
                        coordinator.openRestaurantMenu(targetRestId)
                    } else {
                        coordinator.navigateTo(RestaurantsCustomerDestination.Discover)
                    }
                }
                BackHandler {
                    coordinator.cancelTableScan()
                    returnToDiscoverOrMenu()
                }
                RestaurantTableScanScreen(
                    state = flowState.tableScanState,
                    onScannerTokenReceived = coordinator::submitScannedQrToken,
                    onSessionCodeChanged = coordinator::updateSessionCodeInput,
                    onSubmitSessionCode = coordinator::submitSessionCode,
                    onRetryClick = coordinator::retryTableActivationCheck,
                    onScanAgainClick = coordinator::resetTableScan,
                    onContinueToMenuClick = { session ->
                        activeRestaurantId = session.restaurantId
                        isCartSheetOpen = false
                        checkoutStep = CheckoutSheetStep.CART_REVIEW
                        isOrdersSheetOpen = false
                        selectedOrderIdInSheet = null
                        coordinator.openRestaurantMenu(session.restaurantId, session.branchId)
                        coordinator.loadCustomerOrdersForRestaurant(session.restaurantId)
                    },
                    onCancelClick = {
                        coordinator.cancelTableScan()
                        returnToDiscoverOrMenu()
                    },
                    onBackClick = {
                        coordinator.cancelTableScan()
                        returnToDiscoverOrMenu()
                    },
                    cameraPreviewContent = {
                        TableQrCameraPreviewBox(
                            onQrTokenDetected = coordinator::submitScannedQrToken,
                            enabled = flowState.tableScanState.phase is TableScanPhase.AwaitingScan,
                            resetKey = flowState.tableScanState.phase,
                        )
                    },
                    modifier = modifier,
                )
            }

            is RestaurantsCustomerDestination.Profile -> {
                BackHandler {
                    coordinator.navigateTo(RestaurantsCustomerDestination.Discover)
                }
                RestaurantProfileScreen(
                    state = flowState.profileState,
                    onBranchSelected = coordinator::selectProfileBranch,
                    onBrowseMenuClick = { restId, brId ->
                        activeRestaurantId = restId
                        coordinator.openRestaurantMenu(restId, brId)
                        coordinator.loadCustomerOrdersForRestaurant(restId)
                    },
                    onScanTableQrClick = { restId, _ ->
                        scanReturnRestaurantId = restId
                        coordinator.openTableScan()
                    },
                    onRetryClick = { coordinator.openRestaurantProfile(destination.restaurantId) },
                    onBackClick = {
                        coordinator.navigateTo(RestaurantsCustomerDestination.Discover)
                    },
                    modifier = modifier,
                )
            }

            is RestaurantsCustomerDestination.Menu,
            is RestaurantsCustomerDestination.CartReview,
            is RestaurantsCustomerDestination.OrderTracking, -> {
                val resolvedRestaurantId = when (destination) {
                    is RestaurantsCustomerDestination.Menu -> destination.restaurantId
                    else -> flowState.cartState.activeSession?.restaurantId
                        ?: activeRestaurantId
                        ?: flowState.menuState.branches.firstOrNull()?.restaurantId
                        ?: ""
                }
                val selectedRestaurantCard = flowState.discoverState.allRestaurants.firstOrNull {
                    it.id == resolvedRestaurantId
                } ?: flowState.profileState.restaurant?.takeIf { it.id == resolvedRestaurantId }

                RestaurantWorkspaceScreen(
                    restaurant = selectedRestaurantCard,
                    restaurantId = resolvedRestaurantId,
                    menuState = flowState.menuState,
                    cartState = flowState.cartState,
                    selectedTab = flowState.selectedWorkspaceTab,
                    orders = flowState.workspaceOrders,
                    currentCustomerUid = flowState.activeAccountUid,
                    isCartReviewOpen = isCartSheetOpen,
                    checkoutStep = checkoutStep,
                    activeTrackingState = flowState.orderTrackingState.takeIf {
                        it.orderId.isNotBlank() && it.orderId == selectedOrderIdInSheet
                    },
                    activeTrackingIsSimulatedQa = false,
                    activeMenuItemSheet = flowState.activeMenuItemSheet,
                    isOrdersSheetOpen = isOrdersSheetOpen,
                    selectedOrdersOrderId = selectedOrderIdInSheet,
                    onSelectTab = coordinator::selectWorkspaceTab,
                    onBranchSelected = coordinator::selectMenuBranch,
                    onCategorySelected = coordinator::selectMenuCategory,
                    onMenuItemClick = coordinator::openMenuItem,
                    onQuickAddClick = coordinator::quickAddMenuItem,
                    onQuickDecrementClick = { item ->
                        coordinator.quickDecrementMenuItem(item.id)
                    },
                    onMenuRetry = {
                        if (resolvedRestaurantId.isNotBlank()) {
                            coordinator.openRestaurantMenu(resolvedRestaurantId, flowState.menuState.selectedBranchId)
                        }
                    },
                    onScanTableQrClick = {
                        scanReturnRestaurantId = resolvedRestaurantId.ifBlank { null }
                        isCartSheetOpen = false
                        isOrdersSheetOpen = false
                        coordinator.openTableScan()
                    },
                    onOpenCartReview = {
                        isOrdersSheetOpen = false
                        isCartSheetOpen = true
                    },
                    onCloseCartReview = {
                        isCartSheetOpen = false
                        checkoutStep = CheckoutSheetStep.CART_REVIEW
                        if (flowState.destination !is RestaurantsCustomerDestination.Menu &&
                            resolvedRestaurantId.isNotBlank()
                        ) {
                            coordinator.orderMoreFoodInRestaurant()
                        }
                    },
                    onCheckoutStepChange = { step ->
                        checkoutStep = step
                    },
                    onSelectOrderMode = { mode ->
                        coordinator.selectOrderMode(mode)
                    },
                    onCartIncrementQuantity = { lineId, qty ->
                        coordinator.updateCartItemQuantity(lineId, qty + 1)
                    },
                    onCartDecrementQuantity = { lineId, qty ->
                        coordinator.updateCartItemQuantity(lineId, qty - 1)
                    },
                    onCartRemoveItem = coordinator::removeCartItem,
                    onCartNotesChange = coordinator::updateCustomerNotes,
                    onSubmitDineInOrder = coordinator::submitDineInOrder,
                    offlineDemoOrderLabel = null,
                    onOfflineDemoOrderClick = null,
                    onOpenOrderTracking = { summary ->
                        isCartSheetOpen = false
                        isOrdersSheetOpen = true
                        selectedOrderIdInSheet = summary.orderId
                        coordinator.openOrderTracking(summary.orderId)
                    },
                    onCloseOrderTracking = {
                        selectedOrderIdInSheet = null
                    },
                    onOpenOrdersSheet = { tab ->
                        coordinator.selectWorkspaceTab(tab)
                        isCartSheetOpen = false
                        isOrdersSheetOpen = true
                        if (resolvedRestaurantId.isNotBlank()) {
                            coordinator.loadCustomerOrdersForRestaurant(resolvedRestaurantId)
                        }
                    },
                    onCloseOrdersSheet = {
                        isOrdersSheetOpen = false
                        selectedOrderIdInSheet = null
                        if (flowState.destination is RestaurantsCustomerDestination.OrderTracking) {
                            coordinator.orderMoreFoodInRestaurant()
                        }
                    },
                    onSelectOrderInSheet = { orderId ->
                        selectedOrderIdInSheet = orderId
                        coordinator.openOrderTracking(orderId)
                    },
                    onBackToOrderListInSheet = {
                        selectedOrderIdInSheet = null
                    },
                    onRefreshOrdersClick = {
                        if (resolvedRestaurantId.isNotBlank()) {
                            coordinator.loadCustomerOrdersForRestaurant(resolvedRestaurantId)
                        }
                    },
                    onRefreshTrackingClick = {
                        coordinator.refreshActiveOrder(selectedOrderIdInSheet)
                    },
                    onOrderMoreFoodClick = {
                        isOrdersSheetOpen = false
                        selectedOrderIdInSheet = null
                        isCartSheetOpen = false
                        coordinator.orderMoreFoodInRestaurant()
                    },
                    onSheetToggleOption = coordinator::toggleSheetOption,
                    onSheetIncrementQuantity = coordinator::incrementSheetQuantity,
                    onSheetDecrementQuantity = coordinator::decrementSheetQuantity,
                    onSheetDismiss = coordinator::dismissMenuItemSheet,
                    onSheetAddToCartClick = { coordinator.addActiveSheetItemToCart() },
                    onBackToDiscover = {
                        isCartSheetOpen = false
                        isOrdersSheetOpen = false
                        selectedOrderIdInSheet = null
                        coordinator.navigateTo(RestaurantsCustomerDestination.Discover)
                    },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun RestaurantsBackendUnavailableScreen(
    statusMessage: String,
    canOpenQaPreview: Boolean,
    onOpenQaPreview: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extColors = RestaurantsLightTheme.colors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = extColors.mainBackground,
        topBar = {
            RestaurantsTopBar(
                title = "Restaurants",
                subtitle = "Dine-in ordering preview",
                onBackClick = onBackClick,
                trailingContent = {
                    RestaurantTagPill(
                        label = "Dev Preview",
                        containerColor = extColors.qaNoticeBackground,
                        contentColor = extColors.qaNoticeText,
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RestaurantsStateMessageCard(
                    icon = Icons.Rounded.Storefront,
                    title = "Restaurants Backend Not Connected",
                    message = "$statusMessage. Live restaurant discovery, QR verification, and dine-in orders require backend configuration. No real or simulated orders are submitted from this state.",
                )
                if (canOpenQaPreview) {
                    Button(
                        onClick = onOpenQaPreview,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extColors.brandGreen,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = RestaurantsUiDefaults.ScreenHorizontalPadding),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Offline QA Preview",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RestaurantsUiDefaults.ScreenHorizontalPadding),
                ) {
                    Text("Back to AdisChat Home")
                }
            }
        }
    }
}

/**
 * Feature-gated Profile entry point for the Restaurant Center partner foundation (Phase R4A).
 * Renders [RestaurantCenterPortalScreen] behind [FeatureFlags.RESTAURANT_CENTER_ENABLED]
 * with a native 5-step application draft workflow and Coming Soon / Pending Approval placeholders.
 */
@Composable
fun RestaurantCenterPlaceholderScreen(
    onBackToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    environment: RestaurantsEnvironment = remember { RestaurantsConfig.currentEnvironment },
) {
    RestaurantCenterPortalScreen(
        onBackToProfile = onBackToProfile,
        modifier = modifier,
        environment = environment,
    )
}
