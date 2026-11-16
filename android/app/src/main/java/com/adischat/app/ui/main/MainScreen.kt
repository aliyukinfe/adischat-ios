package com.adischat.app.ui.main

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adischat.app.FeatureFlags
import com.adischat.app.data.model.AdisLinkTarget
import com.adischat.app.data.preferences.AppPreferences
import com.adischat.app.feature.restaurants.presentation.RestaurantCenterPlaceholderScreen
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
import com.adischat.app.ui.browser.InAppBrowserScreen
import com.adischat.app.feature.restaurants.data.RestaurantsRepository
import com.adischat.app.feature.restaurants.data.RestaurantsConfig
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

private val standardNavItems = listOf(
    NavItem("Home",     Icons.Filled.Home,      Icons.Outlined.Home),
    NavItem("Discover", Icons.Filled.Explore,       Icons.Outlined.Explore),
    NavItem("Scan",     Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner),
    NavItem("Chat",     Icons.Filled.Chat,      Icons.Outlined.ChatBubbleOutline),
    NavItem("Profile",  Icons.Filled.Person,      Icons.Outlined.Person),
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

    val navItems = remember(isAdmin) {
        if (isAdmin) standardNavItems + adminNavItem else standardNavItems
    }

    // Global unread aggregator - drives the Chat-tab notification badge app-wide.
    val context = LocalContext.current
    LaunchedEffect(Unit) { com.adischat.app.notifications.UnreadCenter.start(context) }
    val totalUnread by com.adischat.app.notifications.UnreadCenter.total.collectAsStateWithLifecycle()

    var selected by remember { mutableIntStateOf(0) }
    var shopRoute by remember { mutableStateOf<ShopRoute?>(null) }
    var sellerAccountOpen by remember { mutableStateOf(false) }
    var sellerNotificationsRequested by remember { mutableStateOf(false) }
    var sellerNotificationStoreId by remember { mutableStateOf<String?>(null) }
    var restaurantsCustomerOpen by remember { mutableStateOf(false) }
    var pendingRestaurantQrToken by remember { mutableStateOf<String?>(null) }
    var restaurantCenterPlaceholderOpen by remember { mutableStateOf(false) }
    var restaurantCenterWebViewOpen by remember { mutableStateOf(false) }
    var restaurantCenterWebUrl by remember { mutableStateOf("") }
    var restaurantCenterHandoffData by remember { mutableStateOf<ByteArray?>(null) }
    var restaurantsQaRoute by remember { mutableStateOf<RestaurantsQaRoute?>(null) }

    var lastUid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (lastUid != null && lastUid != uid) {
            restaurantsCustomerOpen = false
            pendingRestaurantQrToken = null
            restaurantCenterPlaceholderOpen = false
            restaurantCenterWebViewOpen = false
            restaurantCenterHandoffData = null
        }
        lastUid = uid
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
        if (restaurantCenterWebViewOpen && FeatureFlags.RESTAURANT_CENTER_ENABLED) {
            restaurantCenterWebViewOpen = false
            restaurantCenterHandoffData = null
        } else if (restaurantsQaRoute != null && FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED) {
        restaurantsQaRoute = null
        } else if (restaurantsCustomerOpen && FeatureFlags.RESTAURANTS_DINE_IN_ENABLED) {
        restaurantsCustomerOpen = false
        } else if (restaurantCenterPlaceholderOpen && FeatureFlags.RESTAURANT_CENTER_ENABLED) {
        restaurantCenterPlaceholderOpen = false
        } else if (sellerAccountOpen) {
        sellerAccountOpen = false
        } else if (shopRoute != null) {
        shopRoute = null
        } else if (selected != 0) {
        selected = 0
        } else {
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

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
        val qaOpen = restaurantsQaRoute != null && FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED
        val restaurantsOpen = restaurantsCustomerOpen && FeatureFlags.RESTAURANTS_DINE_IN_ENABLED
        val centerOpen = (restaurantCenterPlaceholderOpen || restaurantCenterWebViewOpen) && FeatureFlags.RESTAURANT_CENTER_ENABLED
        if (!keyboardOpen && !isChatSelected && shopRoute == null && !sellerAccountOpen && !qaOpen && !restaurantsOpen && !centerOpen) {
        Box(
        modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .background(if (selected == 0) Color(0xFF0B0D12) else Color.Transparent)
        .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
        ) {
        Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF12151C).copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 24.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2530)),
        ) {
        Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
        ) {
        navItems.forEachIndexed { index, item ->
        val active = selected == index
        Column(
        modifier = Modifier
        .weight(1f)
        .clip(RoundedCornerShape(20.dp))
        .clickable { selected = index }
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
        if (item.label == "Chat" && totalUnread > 0) {
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
        val centerOverlayActive = (restaurantCenterPlaceholderOpen || restaurantCenterWebViewOpen) && FeatureFlags.RESTAURANT_CENTER_ENABLED
        Box(
            if (isChatSelected || shopRoute != null || sellerAccountOpen || qaOverlayActive || restaurantsOverlayActive || centerOverlayActive) {
                Modifier.fillMaxSize()
            } else {
                Modifier.padding(padding)
            },
        ) {
        val activeShopRoute = shopRoute
        val activeQaRoute = restaurantsQaRoute
        if (restaurantCenterWebViewOpen && FeatureFlags.RESTAURANT_CENTER_ENABLED) {
            InAppBrowserScreen(
                url = restaurantCenterWebUrl,
                postData = restaurantCenterHandoffData,
                onClose = {
                    restaurantCenterWebViewOpen = false
                    restaurantCenterHandoffData = null
                },
            )
        } else if (activeQaRoute != null && FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED) {
        RestaurantsQaHost(
            initialRoute = activeQaRoute,
            onClose = { restaurantsQaRoute = null },
        )
        } else if (restaurantsOverlayActive) {
        RestaurantsCustomerRootScreen(
            initialQrToken = pendingRestaurantQrToken,
            onInitialQrTokenConsumed = { pendingRestaurantQrToken = null },
            onBackToHome = {
                pendingRestaurantQrToken = null
                restaurantsCustomerOpen = false
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
        } else if (centerOverlayActive) {
        RestaurantCenterPlaceholderScreen(
            onBackToProfile = { restaurantCenterPlaceholderOpen = false },
        )
        } else if (sellerAccountOpen) {
        SellerAccountRootScreen(
            onBackToHome = { sellerAccountOpen = false; sellerNotificationsRequested = false; sellerNotificationStoreId = null },
            openNotificationsInitially = sellerNotificationsRequested,
            initialStoreId = sellerNotificationStoreId,
        )
        } else if (activeShopRoute != null) {
        ShopRootScreen(
        initialRoute = activeShopRoute,
        onBackToHome = { shopRoute = null },
        marketplaceLanguage = marketplaceLanguage,
        onMarketplaceLanguageChange = { language -> scope.launch { prefs.setLanguage(language) } },
        accountIdentity = AdisChatAccountIdentity(
        displayName = currentUser?.name.orEmpty(),
        photoUrl = currentUser?.photoUrl.orEmpty(),
        ),
        onOpenAdisChatProfile = {
        shopRoute = null
        selected = 4
        },
        )
        } else {
        when (selected) {
        0 -> HomeScreen(
        onOpenUrl = onOpenUrl,
        onOpenShop = { shopRoute = ShopRoute.Home },
        onGoScan = { selected = 2 },
        onGoChat = { selected = 3 },
        onGoWallet = { },
        onGoDiscover = { selected = 1 },
        onOpenSellerAccount = { sellerAccountOpen = true },
        onOpenRestaurants = {
            if (FeatureFlags.RESTAURANTS_DINE_IN_ENABLED) {
                restaurantsCustomerOpen = true
            }
        },
        onOpenRestaurantsQa = {
            if (FeatureFlags.RESTAURANTS_QA_PREVIEW_ENABLED) {
                restaurantsQaRoute = RestaurantsQaRoute.HUB
            }
        },
        onOpenRestaurantCenter = {
            if (FeatureFlags.RESTAURANT_CENTER_ENABLED) {
                scope.launch {
                    val repo = RestaurantsRepository()
                    repo.createRestaurantCenterHandoffTicket()
                        .onSuccess { ticketRes ->
                            val ticket = ticketRes.ticket
                            if (!ticket.isNullOrBlank()) {
                                val env = RestaurantsConfig.currentEnvironment
                                val handoffUrl = env.handoffAuthUrl()
                                val postBody = "ticket=${java.net.URLEncoder.encode(ticket, "UTF-8")}".toByteArray(Charsets.UTF_8)
                                restaurantCenterHandoffData = postBody
                                restaurantCenterWebUrl = handoffUrl
                                restaurantCenterWebViewOpen = true
                            }
                        }
                }
            }
        },
        )
        1 -> DiscoverScreen(onOpenUrl = onOpenUrl)
        2 -> ScanScreen(
            prefs = prefs,
            onOpenUrl = onOpenUrl,
            onOpenRestaurantQr = { token ->
                if (FeatureFlags.RESTAURANTS_DINE_IN_ENABLED) {
                    pendingRestaurantQrToken = token
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
        onSignOut = onSignOut,
        onGoScan = { selected = 2 },
        onOpenUrl = onOpenUrl,
        onOpenShopRoute = { route -> shopRoute = route },
        onOpenRestaurantCenter = {
            if (FeatureFlags.RESTAURANT_CENTER_ENABLED) {
                restaurantCenterPlaceholderOpen = true
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
    }
}
