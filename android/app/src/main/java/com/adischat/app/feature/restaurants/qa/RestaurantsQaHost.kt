package com.adischat.app.feature.restaurants.qa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adischat.app.BuildConfig
import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.admin.RestaurantAdminScreen
import com.adischat.app.feature.restaurants.admin.RestaurantAdminTab
import com.adischat.app.feature.restaurants.center.RestaurantCenterScreen
import com.adischat.app.feature.restaurants.center.RestaurantCenterTab
import com.adischat.app.feature.restaurants.center.RestaurantStaffRole
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.presentation.DineInNoticeBanner
import com.adischat.app.feature.restaurants.presentation.LocalRestaurantsTopBarStatusPadding
import com.adischat.app.feature.restaurants.presentation.RestaurantTableScanScreen
import com.adischat.app.feature.restaurants.presentation.RestaurantWorkspaceScreen
import com.adischat.app.feature.restaurants.presentation.RestaurantsDisabledNoticeCard
import com.adischat.app.feature.restaurants.presentation.RestaurantsDiscoverScreen
import com.adischat.app.feature.restaurants.presentation.RestaurantsLightPalette
import com.adischat.app.feature.restaurants.presentation.RestaurantsLightTheme
import com.adischat.app.feature.restaurants.presentation.RestaurantsSystemBarAppearance
import com.adischat.app.feature.restaurants.presentation.RestaurantsTopBar
import com.adischat.app.feature.restaurants.presentation.RestaurantsUiDefaults
import com.adischat.app.feature.restaurants.presentation.TableQrCameraPreviewBox

/**
 * Dedicated zero-backend entry shell used exclusively when [BuildConfig.OFFLINE_QA_PREVIEW] is true.
 * Renders Home/Profile preview entry points and opens [RestaurantsQaHost] without touching Firebase,
 * Cloudflare, Supabase, or AdisGebeya Shop.
 */
@Composable
fun OfflinePreviewShellScreen() {
    if (!BuildConfig.OFFLINE_QA_PREVIEW || !FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED) {
        RestaurantsDisabledNoticeCard()
        return
    }
    var activeInitialRoute by remember { mutableStateOf<RestaurantsQaRoute?>(null) }

    val route = activeInitialRoute
    if (route != null) {
        val env = remember { RestaurantsEnvironment() }
        RestaurantsQaHost(
            initialRoute = route,
            onClose = { activeInitialRoute = null },
            environment = env,
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
                    color = RestaurantsUiDefaults.WarmAmberSoft,
                    border = BorderStroke(1.dp, RestaurantsUiDefaults.WarmAmberAccent.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = RestaurantsEnvironment.OFFLINE_QA_PREVIEW_LABEL,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "App ID: ${BuildConfig.APPLICATION_ID} • Local fixtures only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "No Supabase, Cloudflare, Firebase, AdisGebeya, or payment calls are active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                QaSectionTitle("Home & Profile Preview Entry Points")
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.Restaurant,
                    title = "Dining QA (Restaurants Home Entry)",
                    subtitle = "Opens the Restaurants QA Hub covering Discovery, Menu, Cart, Scan, Center, Uploads & Admin",
                    onClick = { activeInitialRoute = RestaurantsQaRoute.HUB },
                )
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.Storefront,
                    title = "Restaurants QA (Profile Hub Entry)",
                    subtitle = "Mirrors the QA-gated Profile Account Hub item into Restaurants QA Hub",
                    onClick = { activeInitialRoute = RestaurantsQaRoute.HUB },
                )
            }

            item {
                QaSectionTitle("Direct Smoke-Test Shortcuts")
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.Restaurant,
                    title = "Customer Discovery -> Menu -> Cart",
                    subtitle = "Jump directly to offline customer browsing & local preview cart",
                    onClick = { activeInitialRoute = RestaurantsQaRoute.DISCOVER },
                )
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.QrCodeScanner,
                    title = "QR Camera Permission & Scanner Preview",
                    subtitle = "Test Android CAMERA permission + live CameraX/ML Kit preview with offline block",
                    onClick = { activeInitialRoute = RestaurantsQaRoute.TABLE_SCAN },
                )
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.UploadFile,
                    title = "Restaurant Center (Tabs & Native Pickers)",
                    subtitle = "Open Overview, Orders, Menu, Tables QR, Staff, Application & Document/Photo pickers",
                    onClick = { activeInitialRoute = RestaurantsQaRoute.CENTER },
                )
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.AdminPanelSettings,
                    title = "Platform Admin Console Preview",
                    subtitle = "Inspect Applications, Restaurants & Audit Logs with offline backend guard",
                    onClick = { activeInitialRoute = RestaurantsQaRoute.ADMIN },
                )
            }
        }
    }
}

/**
 * Root QA-only host for verifying AdisChat Restaurants flows on device.
 * Strictly gated by [FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED] and either
 * [RestaurantsEnvironment.isQaBuild] or [RestaurantsEnvironment.isOfflinePreview].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RestaurantsQaHost(
    initialRoute: RestaurantsQaRoute = RestaurantsQaRoute.HUB,
    onClose: () -> Unit,
    environment: RestaurantsEnvironment = remember { RestaurantsEnvironment() },
) {
    val allowedVariant = FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED &&
        (environment.isQaBuild || environment.isOfflinePreview)
    if (!allowedVariant) {
        Scaffold(
            topBar = {
                RestaurantsTopBar(
                    title = "Restaurants QA",
                    subtitle = "Disabled in this build variant",
                    onBackClick = onClose,
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                RestaurantsDisabledNoticeCard()
            }
        }
        return
    }

    val controller = remember(environment) { RestaurantsQaPreviewController(environment) }
    LaunchedEffect(initialRoute) {
        if (initialRoute != RestaurantsQaRoute.HUB) {
            controller.openRoute(initialRoute)
        }
    }

    val uiState by controller.state.collectAsStateWithLifecycle()

    BackHandler {
        if (!controller.navigateBack()) {
            onClose()
        }
    }

    val isCustomerLightRoute = uiState.currentRoute == RestaurantsQaRoute.DISCOVER ||
        uiState.currentRoute == RestaurantsQaRoute.WORKSPACE ||
        uiState.currentRoute == RestaurantsQaRoute.TABLE_SCAN

    val hostSurface: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            CompositionLocalProvider(LocalRestaurantsTopBarStatusPadding provides false) {
                Column(modifier = Modifier.fillMaxSize()) {
                    QaStagingBannerStrip(
                        bannerText = uiState.stagingBannerText,
                        isConnected = uiState.isStagingConnected,
                        noticeText = uiState.bannerNotice,
                        isLightTheme = isCustomerLightRoute,
                        onDismissNotice = controller::dismissBannerNotice,
                        onOpenHub = if (uiState.currentRoute != RestaurantsQaRoute.HUB &&
                            uiState.currentRoute != RestaurantsQaRoute.WORKSPACE
                        ) {
                            { controller.openRoute(RestaurantsQaRoute.HUB) }
                        } else {
                            null
                        },
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        when (uiState.currentRoute) {
                            RestaurantsQaRoute.HUB -> {
                                RestaurantsQaHubScreen(
                                    state = uiState,
                                    onNavigateRoute = controller::openRoute,
                                    onOpenHabeshaWorkspace = {
                                        controller.openRestaurantWorkspace(RestaurantsQaSampleData.QA_RESTAURANT_ID)
                                    },
                                    onNavigateCenterTab = controller::openCenterTab,
                                    onNavigateAdminTab = controller::openAdminTab,
                                    onClose = onClose,
                                )
                            }

                            RestaurantsQaRoute.DISCOVER -> {
                                RestaurantsDiscoverScreen(
                                    state = uiState.discoverState,
                                    onSearchQueryChange = controller::onDiscoverSearchChange,
                                    onCuisineFilterSelected = controller::onDiscoverCuisineSelected,
                                    onRestaurantClick = { card -> controller.openRestaurantWorkspace(card.id) },
                                    onRetryClick = controller::onDiscoverRetry,
                                    onBackClick = {
                                        if (!controller.navigateBack()) onClose()
                                    },
                                )
                            }

                            RestaurantsQaRoute.WORKSPACE -> {
                                val selectedRestaurant = uiState.discoverState.allRestaurants.firstOrNull {
                                    it.id == uiState.selectedRestaurantId
                                }
                                val selectedOrderSummary = uiState.selectedWorkspaceOrderId?.let { targetOrderId ->
                                    uiState.workspaceOrders.firstOrNull { it.orderId == targetOrderId }
                                }
                                val activeTrackingState = selectedOrderSummary?.let { uiState.orderTrackingState }
                                RestaurantWorkspaceScreen(
                                    restaurant = selectedRestaurant,
                                    restaurantId = uiState.selectedRestaurantId,
                                    menuState = uiState.menuState,
                                    cartState = uiState.cartState,
                                    selectedTab = uiState.selectedWorkspaceTab,
                                    orders = uiState.workspaceOrders,
                                    currentCustomerUid = uiState.currentCustomerUid,
                                    isCartReviewOpen = uiState.isWorkspaceCartOpen,
                                    checkoutStep = uiState.checkoutSheetStep,
                                    activeTrackingState = activeTrackingState,
                                    activeTrackingIsSimulatedQa = selectedOrderSummary?.isSimulatedQa == true,
                                    activeMenuItemSheet = uiState.activeMenuItemSheet,
                                    isOrdersSheetOpen = uiState.isOrdersSheetOpen,
                                    selectedOrdersOrderId = uiState.selectedWorkspaceOrderId,
                                    onSelectTab = controller::selectWorkspaceTab,
                                    onBranchSelected = controller::onWorkspaceBranchSelected,
                                    onCategorySelected = controller::onMenuCategorySelected,
                                    onMenuItemClick = controller::openMenuItemSheet,
                                    onQuickAddClick = controller::onQuickAddMenuItem,
                                    onQuickDecrementClick = { item -> controller.onQuickDecrementMenuItem(item.id) },
                                    onMenuRetry = controller::onMenuRetry,
                                    onScanTableQrClick = { controller.openTableScan() },
                                    onOpenCartReview = controller::openWorkspaceCart,
                                    onCloseCartReview = controller::closeWorkspaceCart,
                                    onCheckoutStepChange = controller::onCheckoutStepChange,
                                    onSelectOrderMode = controller::onSelectOrderMode,
                                    onCartIncrementQuantity = controller::onCartIncrementQuantity,
                                    onCartDecrementQuantity = controller::onCartDecrementQuantity,
                                    onCartRemoveItem = controller::onCartRemoveItem,
                                    onCartNotesChange = controller::onCartNotesChange,
                                    onSubmitDineInOrder = controller::onSubmitDineInOrder,
                                    offlineDemoOrderLabel = if (!uiState.isStagingConnected) {
                                        "Create Simulated QA Order (Offline Demo)"
                                    } else {
                                        null
                                    },
                                    onOfflineDemoOrderClick = if (!uiState.isStagingConnected) {
                                        controller::createSimulatedQaOrder
                                    } else {
                                        null
                                    },
                                    onOpenOrderTracking = { summary ->
                                        controller.openWorkspaceOrderTracking(summary.orderId)
                                    },
                                    onCloseOrderTracking = controller::closeWorkspaceOrderTracking,
                                    onOpenOrdersSheet = controller::openOrdersSheet,
                                    onCloseOrdersSheet = controller::closeOrdersSheet,
                                    onSelectOrderInSheet = controller::openWorkspaceOrderTracking,
                                    onBackToOrderListInSheet = controller::closeWorkspaceOrderTracking,
                                    onRefreshOrdersClick = controller::onRefreshWorkspaceOrders,
                                    onRefreshTrackingClick = controller::onRefreshOrderTracking,
                                    onOrderMoreFoodClick = controller::onOrderMoreFood,
                                    onSheetToggleOption = controller::onSheetToggleOption,
                                    onSheetIncrementQuantity = controller::onSheetIncrementQuantity,
                                    onSheetDecrementQuantity = controller::onSheetDecrementQuantity,
                                    onSheetDismiss = controller::dismissMenuItemSheet,
                                    onSheetAddToCartClick = { controller.addSheetItemToPreviewCart() },
                                    onBackToDiscover = {
                                        if (!controller.navigateBack()) onClose()
                                    },
                                )
                            }

                            RestaurantsQaRoute.TABLE_SCAN -> {
                                RestaurantTableScanScreen(
                                    state = uiState.tableScanState,
                                    onScannerTokenReceived = controller::onQrTokenScanned,
                                    onSessionCodeChanged = controller::onScanSessionCodeChanged,
                                    onSubmitSessionCode = controller::onSubmitScanSessionCode,
                                    onRetryClick = controller::onRetryTableScan,
                                    onScanAgainClick = controller::onScanAgain,
                                    onContinueToMenuClick = controller::onContinueAfterScanConfirmed,
                                    onCancelClick = {
                                        controller.onCancelTableScan()
                                        if (!controller.navigateBack()) onClose()
                                    },
                                    onBackClick = {
                                        if (!controller.navigateBack()) onClose()
                                    },
                                    cameraPreviewContent = {
                                        TableQrCameraPreviewBox(
                                            onQrTokenDetected = controller::onQrTokenScanned,
                                            permissionHeadline = "Camera permission needed for Table QR preview",
                                            permissionBody = "Grant camera access to preview live QR detection with staging guard.",
                                        )
                                    },
                                )
                            }

                            RestaurantsQaRoute.CENTER -> {
                        val activeRole = uiState.centerState.activeRole
                        Column(modifier = Modifier.fillMaxSize()) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                RestaurantStaffRole.entries.forEach { role ->
                                    FilterChip(
                                        selected = activeRole == role,
                                        onClick = { controller.onCenterRolePreviewSelected(role) },
                                        label = { Text(role.label) },
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                RestaurantCenterScreen(
                                    state = uiState.centerState,
                                    onTabSelected = controller::onCenterTabSelected,
                                    onRestaurantSelected = controller::onCenterRestaurantSelected,
                                    onBranchSelected = controller::onCenterBranchSelected,
                                    onOrderFilterSelected = controller::onCenterOrderFilterSelected,
                                    onTransitionOrder = { _, target, _ ->
                                        controller.onCenterBackendActionBlocked("Order transition (${target.label})")
                                    },
                                    onToggleItemStock = { _, available ->
                                        controller.onCenterBackendActionBlocked(
                                            if (available) "Mark item in stock" else "Mark item out of stock",
                                        )
                                    },
                                    onRotateTableQr = {
                                        controller.onCenterBackendActionBlocked("Rotate table QR")
                                    },
                                    onOpenDiningSession = {
                                        controller.onCenterBackendActionBlocked("Activate table session")
                                    },
                                    onCloseDiningSession = {
                                        controller.onCenterBackendActionBlocked("Close table session")
                                    },
                                    onInviteStaff = { _, _ ->
                                        controller.onCenterBackendActionBlocked("Invite restaurant staff")
                                    },
                                    onRevokeStaff = {
                                        controller.onCenterBackendActionBlocked("Revoke staff access")
                                    },
                                    onTogglePublishLive = {
                                        controller.onCenterBackendActionBlocked("Publish live ordering")
                                    },
                                    onUpdateSettings = {
                                        controller.onCenterBackendActionBlocked("Save restaurant settings")
                                    },
                                    onSubmitApplication = {
                                        controller.onCenterBackendActionBlocked("Submit restaurant application")
                                    },
                                    onBackClick = {
                                        if (!controller.navigateBack()) onClose()
                                    },
                                    onUploadPickedFile = controller::onUploadPickedFile,
                                    onUploadValidationError = controller::onUploadValidationError,
                                    onPickerCancelled = controller::onPickerCancelled,
                                    onCancelUpload = controller::onCancelUpload,
                                    onRetryUpload = controller::onRetryUpload,
                                    onDismissUploadStatus = controller::onDismissUploadStatus,
                                )
                            }
                        }
                    }

                    RestaurantsQaRoute.ADMIN -> {
                        RestaurantAdminScreen(
                            state = uiState.adminState,
                            onTabSelected = controller::onAdminTabSelected,
                            onSelectApplication = controller::onAdminSelectApplication,
                            onInspectDocument = { _, _ ->
                                controller.onAdminBackendActionBlocked("Signed document preview")
                            },
                            onStartReview = {
                                controller.onAdminBackendActionBlocked("Start application review")
                            },
                            onApproveApplication = { _, _, _ ->
                                controller.onAdminBackendActionBlocked("Approve restaurant application")
                            },
                            onRequestChanges = { _, _ ->
                                controller.onAdminBackendActionBlocked("Request application changes")
                            },
                            onRejectApplication = { _, _ ->
                                controller.onAdminBackendActionBlocked("Reject application")
                            },
                            onSuspendRestaurant = { _, _ ->
                                controller.onAdminBackendActionBlocked("Suspend restaurant")
                            },
                            onReactivateRestaurant = { _, _ ->
                                controller.onAdminBackendActionBlocked("Reactivate restaurant")
                            },
                            onBackClick = {
                                if (!controller.navigateBack()) onClose()
                            },
                        )
                    }
                }
            }
        }
        }
    }
    }
    if (isCustomerLightRoute) {
        RestaurantsSystemBarAppearance(useDarkIcons = true)
        RestaurantsLightTheme {
            hostSurface()
        }
    } else {
        hostSurface()
    }
}

@Composable
private fun QaStagingBannerStrip(
    bannerText: String,
    isConnected: Boolean,
    noticeText: String?,
    isLightTheme: Boolean,
    onDismissNotice: () -> Unit,
    onOpenHub: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Surface(
            color = when {
                isLightTheme && isConnected -> RestaurantsLightPalette.BrandGreenSoft
                isLightTheme -> RestaurantsLightPalette.QaNoticeBackground
                isConnected -> RestaurantsUiDefaults.DineInGreenSoft
                else -> RestaurantsUiDefaults.WarmAmberSoft
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = bannerText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        isLightTheme && isConnected -> RestaurantsLightPalette.BrandGreenDeep
                        isLightTheme -> RestaurantsLightPalette.QaNoticeText
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                if (onOpenHub != null) {
                    TextButton(onClick = onOpenHub) {
                        Text("QA Hub")
                    }
                }
            }
        }
        if (!noticeText.isNullOrBlank()) {
            DineInNoticeBanner(
                headline = "Offline QA Preview Notice",
                supportingText = noticeText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismissNotice) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun RestaurantsQaHubScreen(
    state: RestaurantsQaUiState,
    onNavigateRoute: (RestaurantsQaRoute) -> Unit,
    onOpenHabeshaWorkspace: () -> Unit,
    onNavigateCenterTab: (RestaurantCenterTab) -> Unit,
    onNavigateAdminTab: (RestaurantAdminTab) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        RestaurantsTopBar(
            title = "Restaurants QA Mode",
            subtitle = "Isolated offline preview & native hardware/picker QA",
            onBackClick = onClose,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                QaSectionTitle("Customer Dine-In Flow")
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.Restaurant,
                    title = "1. Restaurants Discover",
                    subtitle = "Search, filter cuisines & open any unified restaurant workspace",
                    onClick = { onNavigateRoute(RestaurantsQaRoute.DISCOVER) },
                )
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.Storefront,
                    title = "2. Habesha Restaurant Workspace",
                    subtitle = "Unified Menu, Cart (${state.cartState.totalItemCount}), My Orders & History preview",
                    onClick = onOpenHabeshaWorkspace,
                )
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.QrCodeScanner,
                    title = "3. Table QR Scan & Camera Permission",
                    subtitle = "CameraX viewfinder, ML Kit detection & offline block notice",
                    onClick = { onNavigateRoute(RestaurantsQaRoute.TABLE_SCAN) },
                )
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                QaSectionTitle("Restaurant Center & Partner Operations")
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.Storefront,
                    title = "7. Restaurant Center Overview",
                    subtitle = "Role switcher (Owner / Kitchen / Cashier / Waiter) & branch summary",
                    onClick = { onNavigateCenterTab(RestaurantCenterTab.OVERVIEW) },
                )
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.UploadFile,
                    title = "8. Restaurant Application & Document Picker",
                    subtitle = "PDF/image picker, ContentResolver validation & logo/cover upload block",
                    onClick = { onNavigateCenterTab(RestaurantCenterTab.APPLICATION) },
                )
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.MenuBook,
                    title = "9. Menu Management & Photo Picker",
                    subtitle = "Branch availability cards & native photo picker integration",
                    onClick = { onNavigateCenterTab(RestaurantCenterTab.MENU) },
                )
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.Settings,
                    title = "10. Restaurant Settings & Brand Media",
                    subtitle = "Logo & cover image pickers, opening hours & staging guard",
                    onClick = { onNavigateCenterTab(RestaurantCenterTab.SETTINGS) },
                )
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.SupervisorAccount,
                    title = "11. Staff, Kitchen Orders & Tables QR",
                    subtitle = "Kitchen/Cashier board filters, table QR sessions & staff RBAC preview",
                    onClick = { onNavigateCenterTab(RestaurantCenterTab.ORDERS) },
                )
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                QaSectionTitle("Platform Admin")
            }
            item {
                QaDestinationCard(
                    icon = Icons.Rounded.AdminPanelSettings,
                    title = "12. Platform Admin Console",
                    subtitle = "Applications, documents & restaurants moderation (staging-guarded)",
                    onClick = { onNavigateAdminTab(RestaurantAdminTab.APPLICATIONS) },
                )
            }
        }
    }
}

@Composable
private fun QaSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun QaDestinationCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
