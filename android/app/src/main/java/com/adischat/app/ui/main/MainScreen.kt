package com.adischat.app.ui.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adischat.app.FeatureFlags
import com.adischat.app.R
import com.adischat.app.data.model.AdisLinkTarget
import com.adischat.app.data.preferences.AppPreferences
import com.adischat.app.feature.restaurants.center.RestaurantCenterEntryScreen
import com.adischat.app.feature.restaurants.presentation.RestaurantsCustomerRootScreen
import com.adischat.app.feature.restaurants.qa.RestaurantsQaHost
import com.adischat.app.feature.restaurants.qa.RestaurantsQaRoute
import com.adischat.app.ui.UserViewModel
import com.adischat.app.ui.admin.AdminScreen
import com.adischat.app.ui.chat.ChatScreen
import com.adischat.app.ui.discover.DiscoverScreen
import com.adischat.app.feature.shop.presentation.SellerAccountRootScreen
import com.adischat.app.feature.shop.presentation.AdisChatAccountIdentity
import com.adischat.app.feature.shop.presentation.ShopRootScreen
import com.adischat.app.feature.shop.presentation.ShopRoute
import com.adischat.app.feature.shop.presentation.ShopNotificationRouteEffect
import com.adischat.app.ui.home.HomeScreen
import com.adischat.app.ui.home.SuperAppService
import com.adischat.app.ui.home.SuperAppCompactSidebar
import com.adischat.app.ui.home.SuperAppPromotionsScreen
import com.adischat.app.ui.home.SuperAppSavedScreen
import com.adischat.app.ui.home.SuperAppActivityScreen
import com.adischat.app.ui.home.HomeSearchHit
import com.adischat.app.ui.home.HomeSearchTarget
import com.adischat.app.feature.restaurants.data.RestaurantsRepository
import com.adischat.app.feature.shop.data.SellerRepository
import com.adischat.app.feature.shop.presentation.sellerFeaturesUnlocked
import com.adischat.app.ui.localization.AdisLocalizer
import com.adischat.app.ui.localization.tr
import com.adischat.app.ui.profile.ProfileScreen
import com.adischat.app.ui.scan.ScanScreen
import kotlinx.coroutines.launch

private data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/** Small modern red notification pill: 1- then "99+". Hidden by caller when 0. */
@Composable
private fun NavUnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(Color(0xFFFF3B30))
        .border(1.5.dp, Color(0xFF0B0D12), RoundedCornerShape(999.dp))
        .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
        text = if (count > 99) "99+" else count.toString(),
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}

// Approved Phase H1.1 unified bottom navigation: Saved | Explore | Scan | Activity | Profile
private val superAppNavItems = listOf(
    NavItem("Saved",    Icons.Filled.Bookmark,      Icons.Outlined.BookmarkBorder),
    NavItem("Explore",  Icons.Filled.Explore,       Icons.Outlined.Explore),
    NavItem("Scan",     Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner),
    NavItem("Activity", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    NavItem("Profile",  Icons.Filled.Person,        Icons.Outlined.Person),
)

// Legacy bottom navigation fallback
private val standardNavItems = listOf(
    NavItem("Home",     Icons.Filled.Home,          Icons.Outlined.Home),
    NavItem("Discover", Icons.Filled.Explore,       Icons.Outlined.Explore),
    NavItem("Scan",     Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner),
    NavItem("Chat",     Icons.Filled.Chat,          Icons.Outlined.ChatBubbleOutline),
    NavItem("Profile",  Icons.Filled.Person,        Icons.Outlined.Person),
)

private val adminNavItem = NavItem(
    label      = "Admin",
    selectedIcon  = Icons.Filled.ManageAccounts,
    unselectedIcon = Icons.Outlined.ManageAccounts,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    prefs:     AppPreferences,
    pendingAdisLink: AdisLinkTarget? = null,
    onAdisLinkConsumed: () -> Unit = {},
    pendingNotificationConversationId: String? = null,
    onNotificationConversationConsumed: () -> Unit = {},
    pendingShopConversationId: String? = null,
    onShopConversationConsumed: () -> Unit = {},
    pendingShopNotificationContext: String? = null,
    pendingShopNotificationStoreId: String? = null,
    onShopNotificationConsumed: () -> Unit = {},
    initialRestaurantQrToken: String? = null,
    onRestaurantQrTokenConsumed: () -> Unit = {},
    onOpenUrl: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    // Shared user VM - same instance as HomeScreen uses
    val userVm: UserViewModel = viewModel()
    val currentUser by userVm.user.collectAsStateWithLifecycle()
    val marketplaceLanguage by prefs.languageFlow.collectAsStateWithLifecycle(initialValue = "en")
    AdisLocalizer.language = "en"
    val isAdmin     = currentUser?.role == "admin"

    val navItems = remember(isAdmin, FeatureFlags.SUPER_APP_HOME_ENABLED) {
        val base = if (FeatureFlags.SUPER_APP_HOME_ENABLED) superAppNavItems else standardNavItems
        if (isAdmin) base + adminNavItem else base
    }

    // Global unread aggregator - drives notification badges app-wide.
    val context = LocalContext.current
    LaunchedEffect(Unit) { com.adischat.app.notifications.UnreadCenter.start(context) }
    val totalUnread by com.adischat.app.notifications.UnreadCenter.total.collectAsStateWithLifecycle()

    var selected by rememberSaveable { mutableIntStateOf(0) }
    var activeBottomTab by rememberSaveable { mutableStateOf<Int?>(null) } // null = Home in Super App mode
    var activeSidebarService by rememberSaveable { mutableStateOf<SuperAppService?>(null) }
    var promotionsRailBounds by remember { mutableStateOf<Rect?>(null) }
    var pendingHomeChatTarget by remember { mutableStateOf<HomeSearchTarget?>(null) }
    var pendingHomeRestaurantId by remember { mutableStateOf<String?>(null) }
    val saveableStateHolder = rememberSaveableStateHolder()

    var shopRoute by rememberSaveable { mutableStateOf<ShopRoute?>(null) }
    var sellerAccountOpen by rememberSaveable { mutableStateOf(false) }
    var sellerNotificationsRequested by rememberSaveable { mutableStateOf(false) }
    var sellerNotificationStoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var restaurantsCustomerOpen by rememberSaveable { mutableStateOf(false) }
    var pendingRestaurantQrToken by rememberSaveable { mutableStateOf<String?>(null) }
    var restaurantCenterNativeOpen by rememberSaveable { mutableStateOf(false) }
    var restaurantsQaRoute by rememberSaveable { mutableStateOf<RestaurantsQaRoute?>(null) }

    val onGoHome: () -> Unit = {
        pendingHomeChatTarget = null
        pendingHomeRestaurantId = null
        activeSidebarService = null
        activeBottomTab = null
        selected = 0
        if (FeatureFlags.SUPER_APP_HOME_ENABLED) {
            shopRoute = null
            sellerAccountOpen = false
            sellerNotificationsRequested = false
            sellerNotificationStoreId = null
            restaurantsCustomerOpen = false
            pendingRestaurantQrToken = null
            restaurantCenterNativeOpen = false
            restaurantsQaRoute = null
        }
    }

    val onSelectSidebarService: (SuperAppService) -> Unit = { targetService ->
        restaurantCenterNativeOpen = false
        sellerAccountOpen = false
        if (targetService == SuperAppService.HOME) {
            onGoHome()
        } else {
            activeSidebarService = targetService
            activeBottomTab = null
            when (targetService) {
                SuperAppService.HOME -> Unit
                SuperAppService.CHAT -> Unit
                SuperAppService.ADISGEBEYA -> {
                    if (shopRoute == null) {
                        shopRoute = ShopRoute.Home
                    }
                }
                SuperAppService.RESTAURANTS -> {
                    if (!FeatureFlags.SUPER_APP_HOME_ENABLED && FeatureFlags.RESTAURANTS_DINE_IN_ENABLED) {
                        restaurantsCustomerOpen = true
                    }
                }
                SuperAppService.PROMOTIONS -> Unit
            }
        }
    }

    val onOpenHomeSearchResult: (HomeSearchHit) -> Unit = { hit ->
        when (val target = hit.target) {
            is HomeSearchTarget.Chat, is HomeSearchTarget.Community -> {
                pendingHomeChatTarget = target
                onSelectSidebarService(SuperAppService.CHAT)
            }
            is HomeSearchTarget.Product -> {
                shopRoute = ShopRoute.Product(target.product)
                onSelectSidebarService(SuperAppService.ADISGEBEYA)
            }
            is HomeSearchTarget.Store -> {
                shopRoute = ShopRoute.Store(target.store)
                onSelectSidebarService(SuperAppService.ADISGEBEYA)
            }
            is HomeSearchTarget.Restaurant -> {
                pendingHomeRestaurantId = target.restaurantId
                onSelectSidebarService(SuperAppService.RESTAURANTS)
            }
        }
    }

    var isRestaurantCenterEligible by remember { mutableStateOf(false) }
    var isSellerEligible by remember { mutableStateOf(false) }
    var sellerApplicationStatus by remember { mutableStateOf<String?>("not_started") }

    var lastUid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (lastUid != null && lastUid != uid) {
            pendingHomeChatTarget = null
            pendingHomeRestaurantId = null
            restaurantsCustomerOpen = false
            pendingRestaurantQrToken = null
            restaurantCenterNativeOpen = false
            isRestaurantCenterEligible = false
            isSellerEligible = false
            sellerApplicationStatus = null
        }
        lastUid = uid

        if (!uid.isNullOrBlank()) {
            if (FeatureFlags.RESTAURANT_CENTER_ENABLED) {
                RestaurantsRepository().checkRestaurantCenterEligibility()
                    .onSuccess { eligibility ->
                        isRestaurantCenterEligible = eligibility.isEligible || (currentUser?.role == "admin")
                    }
                    .onFailure {
                        isRestaurantCenterEligible = currentUser?.role == "admin"
                    }
            } else {
                isRestaurantCenterEligible = currentUser?.role == "admin"
            }

            SellerRepository().status()
                .onSuccess { (status, _) ->
                    sellerApplicationStatus = status
                    isSellerEligible = sellerFeaturesUnlocked(status) || (currentUser?.role == "admin")
                }
                .onFailure {
                    sellerApplicationStatus = "not_started"
                    isSellerEligible = currentUser?.role == "admin"
                }
        } else {
            isRestaurantCenterEligible = false
            isSellerEligible = false
            sellerApplicationStatus = null
        }
    }

    LaunchedEffect(initialRestaurantQrToken, currentUser?.uid) {
        val token = initialRestaurantQrToken
        if (!token.isNullOrBlank() && FeatureFlags.RESTAURANTS_DINE_IN_ENABLED) {
            pendingRestaurantQrToken = token
            restaurantsCustomerOpen = true
            onRestaurantQrTokenConsumed()
        }
    }

    LaunchedEffect(pendingAdisLink?.slug, pendingAdisLink?.type) {
        if (pendingAdisLink != null) selected = 3
    }

    LaunchedEffect(pendingNotificationConversationId) {
        if (!pendingNotificationConversationId.isNullOrBlank()) selected = 3
    }

    ShopNotificationRouteEffect(
        conversationId = pendingShopConversationId,
        onOpen = { route ->
            sellerAccountOpen = false
            sellerNotificationsRequested = false
            sellerNotificationStoreId = null
            shopRoute = route
        },
        onConsumed = onShopConversationConsumed,
    )
    LaunchedEffect(pendingShopNotificationContext) {
        when (pendingShopNotificationContext) {
            "buyer" -> { sellerAccountOpen = false; shopRoute = ShopRoute.Notifications; onShopNotificationConsumed() }
            "seller" -> {
                shopRoute = null
                sellerNotificationStoreId = pendingShopNotificationStoreId
                sellerNotificationsRequested = true
                sellerAccountOpen = true
                onShopNotificationConsumed()
            }
        }
    }

    // If admin tab disappears (e.g. role changed), snap back to Home
    LaunchedEffect(isAdmin) {
        if (!isAdmin && selected >= standardNavItems.size) selected = 0
    }

    val keyboardOpen   = WindowInsets.isImeVisible
    val isChatSelected = selected == 3
    val lightMode      = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val navSurface     = if (lightMode) Color.White.copy(alpha = 0.96f) else Color(0xFF12151C)
    val navIndicator   = if (lightMode) Color(0xFFEAF7F1) else Color(0xFF34D399).copy(alpha = 0.14f)
    val navActive      = if (lightMode) Color(0xFF22C55E) else Color(0xFF34D399)
    val navInactive    = if (lightMode) Color(0xFF536072) else Color(0xFF6B7280)
    val navSelectedText = if (lightMode) Color(0xFF111827) else Color.White

    // - Double-back-press to exit -
    val activity      = LocalContext.current as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val scope      = rememberCoroutineScope()
    var lastBackTime      by remember { mutableLongStateOf(0L) }

    BackHandler {
        when {
            restaurantCenterNativeOpen && FeatureFlags.RESTAURANT_CENTER_ENABLED -> {
                restaurantCenterNativeOpen = false
            }
            restaurantsQaRoute != null && FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED -> {
                restaurantsQaRoute = null
            }
            sellerAccountOpen -> {
                sellerAccountOpen = false
                sellerNotificationsRequested = false
                sellerNotificationStoreId = null
            }
            activeSidebarService != null && activeSidebarService != SuperAppService.HOME -> {
                onGoHome()
            }
            FeatureFlags.SUPER_APP_HOME_ENABLED && activeBottomTab != null -> {
                onGoHome()
            }
            !FeatureFlags.SUPER_APP_HOME_ENABLED && selected != 0 -> {
                selected = 0
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackTime < 2_000L) {
                    activity?.finish()
                } else {
                    lastBackTime = now
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message  = "Press back again to exit",
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val qaOpen = restaurantsQaRoute != null && FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED
            val isSuperAppActive = FeatureFlags.SUPER_APP_HOME_ENABLED && activeSidebarService != null && activeSidebarService != SuperAppService.HOME
            val restaurantsOpen = restaurantsCustomerOpen && FeatureFlags.RESTAURANTS_DINE_IN_ENABLED
            val centerOpen = restaurantCenterNativeOpen && FeatureFlags.RESTAURANT_CENTER_ENABLED
            val legacyChatSelected = !FeatureFlags.SUPER_APP_HOME_ENABLED && selected == 3
            val legacyShopOpen = shopRoute != null
            val legacySellerOpen = sellerAccountOpen

            if (!keyboardOpen && !isSuperAppActive && !legacyChatSelected && !legacyShopOpen && !legacySellerOpen && !qaOpen && !restaurantsOpen && !centerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .background(if (if (FeatureFlags.SUPER_APP_HOME_ENABLED) activeBottomTab == null else selected == 0) Color(0xFF0B0D12) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF12151C).copy(alpha = 0.96f),
                        tonalElevation = 0.dp,
                        shadowElevation = 24.dp,
                        border = BorderStroke(1.dp, Color(0xFF1F2530)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            navItems.forEachIndexed { index, item ->
                                val active = if (FeatureFlags.SUPER_APP_HOME_ENABLED) {
                                    activeBottomTab == index
                                } else {
                                    selected == index
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            if (FeatureFlags.SUPER_APP_HOME_ENABLED) {
                                                activeBottomTab = index
                                                activeSidebarService = null
                                            } else {
                                                selected = index
                                                if (index == 0) {
                                                    activeSidebarService = null
                                                    shopRoute = null
                                                    sellerAccountOpen = false
                                                    restaurantsCustomerOpen = false
                                                    restaurantsQaRoute = null
                                                } else if (index == 3) {
                                                    if (activeSidebarService != null) {
                                                        activeSidebarService = SuperAppService.CHAT
                                                    }
                                                } else {
                                                    activeSidebarService = null
                                                }
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        modifier = Modifier.size(48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (active) navActive else Color.Transparent),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = if (active) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = tr(item.label),
                                                tint = if (active) Color.Black else navInactive,
                                                modifier = Modifier.size(21.dp),
                                            )
                                        }
                                        if ((item.label == "Chat" || item.label == "Activity") && totalUnread > 0) {
                                            NavUnreadBadge(
                                                count = totalUnread,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = (-2).dp, y = 1.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = tr(item.label),
                                        color = if (active) navActive else navInactive,
                                        fontSize = 10.sp,
                                        fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        val qaOverlayActive = restaurantsQaRoute != null && FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED
        val restaurantsOverlayActive = restaurantsCustomerOpen && FeatureFlags.RESTAURANTS_DINE_IN_ENABLED
        val centerOverlayActive = restaurantCenterNativeOpen && FeatureFlags.RESTAURANT_CENTER_ENABLED
        val promotionsActive = activeSidebarService == SuperAppService.PROMOTIONS
        val isSuperAppActive = FeatureFlags.SUPER_APP_HOME_ENABLED && activeSidebarService != null && activeSidebarService != SuperAppService.HOME

        val shouldShowCompactSidebar = FeatureFlags.SUPER_APP_HOME_ENABLED &&
            ((activeSidebarService != null && activeSidebarService != SuperAppService.HOME) || centerOverlayActive || sellerAccountOpen) &&
            !keyboardOpen &&
            restaurantsQaRoute == null &&
            pendingRestaurantQrToken == null

        val mainContentModifier = if (isChatSelected || shopRoute != null || sellerAccountOpen || qaOverlayActive || restaurantsOverlayActive || centerOverlayActive || promotionsActive || isSuperAppActive) {
            Modifier.fillMaxSize()
        } else {
            Modifier.padding(padding)
        }

        Box(modifier = mainContentModifier) {
            val renderCoreScreenContent: @Composable () -> Unit = {
                val activeShopRoute = shopRoute
                val activeQaRoute = restaurantsQaRoute
                if (centerOverlayActive) {
                    val uid = currentUser?.uid
                    if (uid != null) {
                        RestaurantCenterEntryScreen(
                            accountUid = uid,
                            onBack = { restaurantCenterNativeOpen = false },
                        )
                    }
                } else if (activeQaRoute != null && FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED) {
                    RestaurantsQaHost(
                        initialRoute = activeQaRoute,
                        onClose = { restaurantsQaRoute = null },
                    )
                } else if (FeatureFlags.SUPER_APP_HOME_ENABLED && activeSidebarService != null && activeSidebarService != SuperAppService.HOME) {
                    // Unified service switching with state and scroll preservation
                    saveableStateHolder.SaveableStateProvider(key = "SERVICE_${activeSidebarService?.name}") {
                        when (activeSidebarService) {
                            SuperAppService.HOME -> Unit
                            SuperAppService.CHAT -> {
                                ChatScreen(
                                    pendingHomeSearchTarget = pendingHomeChatTarget,
                                    onHomeSearchTargetConsumed = { pendingHomeChatTarget = null },
                                    pendingAdisLink = pendingAdisLink,
                                    onAdisLinkConsumed = onAdisLinkConsumed,
                                    pendingNotificationConversationId = pendingNotificationConversationId,
                                    onNotificationConversationConsumed = onNotificationConversationConsumed,
                                    onOpenUrl = onOpenUrl,
                                )
                            }
                            SuperAppService.ADISGEBEYA -> {
                                ShopRootScreen(
                                    initialRoute = activeShopRoute ?: ShopRoute.Home,
                                    onBackToHome = onGoHome,
                                    marketplaceLanguage = marketplaceLanguage,
                                    onMarketplaceLanguageChange = { language -> scope.launch { prefs.setLanguage(language) } },
                                    accountIdentity = AdisChatAccountIdentity(
                                        displayName = currentUser?.name.orEmpty(),
                                        photoUrl = currentUser?.photoUrl.orEmpty(),
                                    ),
                                    onOpenAdisChatProfile = {
                                        activeSidebarService = null
                                        activeBottomTab = 4
                                    },
                                )
                            }
                            SuperAppService.RESTAURANTS -> {
                                RestaurantsCustomerRootScreen(
                                    initialSearchRestaurantId = pendingHomeRestaurantId,
                                    onInitialSearchRestaurantConsumed = { pendingHomeRestaurantId = null },
                                    initialQrToken = pendingRestaurantQrToken,
                                    onInitialQrTokenConsumed = { pendingRestaurantQrToken = null },
                                    onBackToHome = {
                                        pendingRestaurantQrToken = null
                                        restaurantsCustomerOpen = false
                                        onGoHome()
                                    },
                                    onOpenQaPreview = if (FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED) {
                                        {
                                            restaurantsCustomerOpen = false
                                            restaurantsQaRoute = RestaurantsQaRoute.HUB
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                            SuperAppService.PROMOTIONS -> {
                                SuperAppPromotionsScreen(
                                    onBack = onGoHome,
                                    onSelectService = onSelectSidebarService,
                                    sidebarBounds = promotionsRailBounds,
                                )
                            }
                            null -> Unit
                        }
                    }
                } else if (restaurantsOverlayActive) {
                    RestaurantsCustomerRootScreen(
                        initialQrToken = pendingRestaurantQrToken,
                        onInitialQrTokenConsumed = { pendingRestaurantQrToken = null },
                        onBackToHome = {
                            pendingRestaurantQrToken = null
                            restaurantsCustomerOpen = false
                            onGoHome()
                        },
                        onOpenQaPreview = if (FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED) {
                            {
                                restaurantsCustomerOpen = false
                                restaurantsQaRoute = RestaurantsQaRoute.HUB
                            }
                        } else {
                            null
                        },
                    )
                } else if (sellerAccountOpen) {
                    SellerAccountRootScreen(
                        onBackToHome = {
                            sellerAccountOpen = false
                            sellerNotificationsRequested = false
                            sellerNotificationStoreId = null
                            onGoHome()
                        },
                        openNotificationsInitially = sellerNotificationsRequested,
                        initialStoreId = sellerNotificationStoreId,
                    )
                } else if (activeShopRoute != null) {
                    ShopRootScreen(
                        initialRoute = activeShopRoute,
                        onBackToHome = {
                            shopRoute = null
                            onGoHome()
                        },
                        marketplaceLanguage = marketplaceLanguage,
                        onMarketplaceLanguageChange = { language -> scope.launch { prefs.setLanguage(language) } },
                        accountIdentity = AdisChatAccountIdentity(
                            displayName = currentUser?.name.orEmpty(),
                            photoUrl = currentUser?.photoUrl.orEmpty(),
                        ),
                        onOpenAdisChatProfile = {
                            shopRoute = null
                            activeSidebarService = null
                            selected = 4
                        },
                    )
                } else if (FeatureFlags.SUPER_APP_HOME_ENABLED) {
                    // Unified Super App bottom navigation
                    saveableStateHolder.SaveableStateProvider(key = "BOTTOM_TAB_${activeBottomTab ?: -1}") {
                        when (activeBottomTab) {
                            null -> HomeScreen(
                                onOpenUrl = onOpenUrl,
                                onOpenShop = { onSelectSidebarService(SuperAppService.ADISGEBEYA) },
                                onGoScan = { activeBottomTab = 2 },
                                onGoChat = { onSelectSidebarService(SuperAppService.CHAT) },
                                onGoWallet = { },
                                onGoDiscover = { activeBottomTab = 1 },
                                onOpenSellerAccount = { sellerAccountOpen = true },
                                onOpenRestaurants = { onSelectSidebarService(SuperAppService.RESTAURANTS) },
                                onOpenRestaurantsQa = {
                                    if (FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED) {
                                        restaurantsQaRoute = RestaurantsQaRoute.HUB
                                    }
                                },
                                onOpenRestaurantCenter = {
                                    if (FeatureFlags.RESTAURANT_CENTER_ENABLED && currentUser?.uid != null) {
                                        restaurantCenterNativeOpen = true
                                    }
                                },
                                onSelectSidebarService = onSelectSidebarService,
                                onOpenSearchResult = onOpenHomeSearchResult,
                            )
                            0 -> SuperAppSavedScreen(
                                onGoHome = onGoHome,
                                onNavigateService = onSelectSidebarService,
                            )
                            1 -> Column(modifier = Modifier.fillMaxSize()) {
                                Surface(
                                    color = if (lightMode) Color.White else Color(0xFF12151C),
                                    tonalElevation = 2.dp,
                                    shadowElevation = 4.dp,
                                    border = BorderStroke(1.dp, if (lightMode) Color(0xFFE2E8F0) else Color(0xFF1F2530)),
                                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.clickable(onClick = onGoHome),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (lightMode) Color(0xFFF1F5F9) else Color(0xFF1E2532)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Home,
                                                    contentDescription = "Return to Home",
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Explore",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (lightMode) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                                                    ),
                                                )
                                                Text(
                                                    text = "Discover services",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        color = if (lightMode) Color(0xFF64748B) else Color(0xFF94A3B8),
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    DiscoverScreen(onOpenUrl = onOpenUrl)
                                }
                            }
                            2 -> ScanScreen(
                                prefs = prefs,
                                onOpenUrl = onOpenUrl,
                                onOpenRestaurantQr = { token ->
                                    if (FeatureFlags.RESTAURANTS_DINE_IN_ENABLED) {
                                        pendingRestaurantQrToken = token
                                        onSelectSidebarService(SuperAppService.RESTAURANTS)
                                    }
                                },
                            )
                            3 -> SuperAppActivityScreen(
                                onGoHome = onGoHome,
                                onNavigateService = onSelectSidebarService,
                                onOpenRestaurantCenter = {
                                    if (FeatureFlags.RESTAURANT_CENTER_ENABLED && currentUser?.uid != null) {
                                        restaurantCenterNativeOpen = true
                                    }
                                },
                                onOpenSellerCenter = {
                                    sellerAccountOpen = true
                                },
                                isRestaurantCenterAuthorized = isRestaurantCenterEligible,
                                isSellerAuthorized = isSellerEligible,
                                sellerStatus = sellerApplicationStatus,
                            )
                            4 -> Column(modifier = Modifier.fillMaxSize()) {
                                Surface(
                                    color = if (lightMode) Color.White else Color(0xFF12151C),
                                    tonalElevation = 2.dp,
                                    shadowElevation = 4.dp,
                                    border = BorderStroke(1.dp, if (lightMode) Color(0xFFE2E8F0) else Color(0xFF1F2530)),
                                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.clickable(onClick = onGoHome),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (lightMode) Color(0xFFF1F5F9) else Color(0xFF1E2532)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Home,
                                                    contentDescription = "Return to Home",
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Profile",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (lightMode) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                                                    ),
                                                )
                                                Text(
                                                    text = "Account and settings",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        color = if (lightMode) Color(0xFF64748B) else Color(0xFF94A3B8),
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ProfileScreen(
                                        prefs = prefs,
                                        onSignOut = { restaurantCenterNativeOpen = false; onSignOut() },
                                        onGoScan = { activeBottomTab = 2 },
                                        onOpenUrl = onOpenUrl,
                                        onOpenShopRoute = { route ->
                                            shopRoute = route
                                            onSelectSidebarService(SuperAppService.ADISGEBEYA)
                                        },
                                        onOpenRestaurantCenter = {
                                            if (FeatureFlags.RESTAURANT_CENTER_ENABLED && currentUser?.uid != null) {
                                                restaurantCenterNativeOpen = true
                                            }
                                        },
                                        onOpenRestaurantsQa = {
                                            if (FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED) {
                                                restaurantsQaRoute = RestaurantsQaRoute.HUB
                                            }
                                        },
                                    )
                                }
                            }
                            5 -> AdminScreen()
                            else -> HomeScreen(
                                onOpenUrl = onOpenUrl,
                                onOpenShop = { onSelectSidebarService(SuperAppService.ADISGEBEYA) },
                                onGoScan = { activeBottomTab = 2 },
                                onGoChat = { onSelectSidebarService(SuperAppService.CHAT) },
                                onGoWallet = { },
                                onGoDiscover = { activeBottomTab = 1 },
                                onOpenSellerAccount = { sellerAccountOpen = true },
                                onOpenRestaurants = { onSelectSidebarService(SuperAppService.RESTAURANTS) },
                                onSelectSidebarService = onSelectSidebarService,
                            )
                        }
                    }
                } else {
                    // Legacy when (selected)
                    when (selected) {
                        0 -> HomeScreen(
                            onOpenUrl = onOpenUrl,
                            onOpenShop = {
                                activeSidebarService = SuperAppService.ADISGEBEYA
                                shopRoute = ShopRoute.Home
                            },
                            onGoScan = {
                                activeSidebarService = null
                                selected = 2
                            },
                            onGoChat = {
                                activeSidebarService = SuperAppService.CHAT
                                selected = 3
                            },
                            onGoWallet = { },
                            onGoDiscover = {
                                activeSidebarService = null
                                selected = 1
                            },
                            onOpenSellerAccount = {
                                sellerAccountOpen = true
                            },
                            onOpenRestaurants = {
                                if (FeatureFlags.RESTAURANTS_DINE_IN_ENABLED) {
                                    activeSidebarService = SuperAppService.RESTAURANTS
                                    restaurantsCustomerOpen = true
                                }
                            },
                            onOpenRestaurantsQa = {
                                if (FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED) {
                                    restaurantsQaRoute = RestaurantsQaRoute.HUB
                                }
                            },
                            onOpenRestaurantCenter = {
                                if (FeatureFlags.RESTAURANT_CENTER_ENABLED && currentUser?.uid != null) {
                                    restaurantCenterNativeOpen = true
                                }
                            },
                            onSelectSidebarService = onSelectSidebarService,
                        )
                        1 -> DiscoverScreen(onOpenUrl = onOpenUrl)
                        2 -> ScanScreen(
                            prefs = prefs,
                            onOpenUrl = onOpenUrl,
                            onOpenRestaurantQr = { token ->
                                if (FeatureFlags.RESTAURANTS_DINE_IN_ENABLED) {
                                    pendingRestaurantQrToken = token
                                    activeSidebarService = SuperAppService.RESTAURANTS
                                    restaurantsCustomerOpen = true
                                }
                            },
                        )
                        3 -> ChatScreen(
                            pendingAdisLink = pendingAdisLink,
                            onAdisLinkConsumed = onAdisLinkConsumed,
                            pendingNotificationConversationId = pendingNotificationConversationId,
                            onNotificationConversationConsumed = onNotificationConversationConsumed,
                            onOpenUrl = onOpenUrl,
                        )
                        4 -> ProfileScreen(
                            prefs = prefs,
                            onSignOut = { restaurantCenterNativeOpen = false; onSignOut() },
                            onGoScan = {
                                activeSidebarService = null
                                selected = 2
                            },
                            onOpenUrl = onOpenUrl,
                            onOpenShopRoute = { route ->
                                activeSidebarService = SuperAppService.ADISGEBEYA
                                shopRoute = route
                            },
                            onOpenRestaurantCenter = {
                                if (FeatureFlags.RESTAURANT_CENTER_ENABLED && currentUser?.uid != null) {
                                    restaurantCenterNativeOpen = true
                                }
                            },
                            onOpenRestaurantsQa = {
                                if (FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED) {
                                    restaurantsQaRoute = RestaurantsQaRoute.HUB
                                }
                            },
                        )
                        5 -> AdminScreen()
                    }
                }
            }

            val compactSidebarSelectedService = when {
                centerOverlayActive -> SuperAppService.RESTAURANTS
                sellerAccountOpen -> SuperAppService.ADISGEBEYA
                else -> activeSidebarService
            }

            if (shouldShowCompactSidebar && activeSidebarService == SuperAppService.PROMOTIONS) {
                Box(modifier = Modifier.fillMaxSize()) {
                    renderCoreScreenContent()
                    SuperAppCompactSidebar(
                        selectedService = compactSidebarSelectedService,
                        onSelectService = onSelectSidebarService,
                        onExpandHome = onGoHome,
                        totalUnreadChat = totalUnread,
                        contentHeightOnly = true,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInRoot()
                            val next = Rect(position.x, position.y, position.x + coordinates.size.width, position.y + coordinates.size.height)
                            if (promotionsRailBounds != next) promotionsRailBounds = next
                        },
                    )
                    SmallFloatingActionButton(
                        onClick = onGoHome,
                        containerColor = Color(0xFF0F766E),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Return Home",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else if (shouldShowCompactSidebar) {
                Row(modifier = Modifier.fillMaxSize()) {
                    SuperAppCompactSidebar(
                        selectedService = compactSidebarSelectedService,
                        onSelectService = onSelectSidebarService,
                        onExpandHome = onGoHome,
                        totalUnreadChat = totalUnread,
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        renderCoreScreenContent()
                        SmallFloatingActionButton(
                            onClick = onGoHome,
                            containerColor = Color(0xFF0F766E),
                            contentColor = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Return Home",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                renderCoreScreenContent()
            }
        }
    }
}
