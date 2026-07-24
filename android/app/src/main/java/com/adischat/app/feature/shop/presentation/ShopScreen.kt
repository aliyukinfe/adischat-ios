package com.adischat.app.feature.shop.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Reviews
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.adischat.app.R
import com.adischat.app.feature.shop.data.ShopBannerDto
import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.feature.shop.data.ShopAddressDto
import com.adischat.app.feature.shop.data.ShopAddressRequest
import com.adischat.app.feature.shop.data.ShopAdminAuditLogDto
import com.adischat.app.feature.shop.data.ShopAdminOrderDto
import com.adischat.app.feature.shop.data.ShopAdminProductDto
import com.adischat.app.feature.shop.data.ShopAdminSellerApplicationDto
import com.adischat.app.feature.shop.data.ShopCartDto
import com.adischat.app.feature.shop.data.ShopCartItemDto
import com.adischat.app.feature.shop.data.ShopFavoriteDto
import com.adischat.app.feature.shop.data.ShopHomeState
import com.adischat.app.feature.shop.data.ShopOrderGroupDto
import com.adischat.app.feature.shop.data.ShopOrderDto
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopProtectedState
import com.adischat.app.feature.shop.data.ShopRecentlyViewedDto
import com.adischat.app.feature.shop.data.ShopStoreDto
import com.adischat.app.ui.theme.AdisGebeyaColors
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val ShopBg = AdisGebeyaColors.Background
private val ShopSurface = AdisGebeyaColors.Surface
private val ShopSoft = AdisGebeyaColors.SecondarySurface
private val ShopInk = AdisGebeyaColors.PrimaryText
private val ShopMuted = AdisGebeyaColors.SecondaryText
private val ShopGreen = AdisGebeyaColors.Primary
private val ShopGreenDark = AdisGebeyaColors.PrimaryDark
private val ShopGreenMid = AdisGebeyaColors.PrimaryMid
private val ShopGreenSoft = AdisGebeyaColors.PrimaryLight
private val ShopOrange = AdisGebeyaColors.AccentOrange
private val ShopOrangeDark = AdisGebeyaColors.AccentOrangeDark
private val ShopLine = AdisGebeyaColors.Divider
private val ShopBorder = AdisGebeyaColors.Border
private val ShopRed = AdisGebeyaColors.Error
private val ShopRedSoft = AdisGebeyaColors.ErrorSoft
private val ShopGold = AdisGebeyaColors.Warning
private val ShopBlue = AdisGebeyaColors.Info
private val ShopBlueSoft = AdisGebeyaColors.SoftBlue
private val ShopPinkSoft = AdisGebeyaColors.SoftPink
private val ShopAmberSoft = AdisGebeyaColors.SoftOrange
private val ShopPurple = AdisGebeyaColors.Purple
private val ShopPurpleSoft = AdisGebeyaColors.SoftPurple
private val ShopOnColor = AdisGebeyaColors.OnColor
private val ShopOverlay = AdisGebeyaColors.OverlayScrim
private val ShopTransparent = AdisGebeyaColors.Transparent

private val AdisGebeyaLightScheme = lightColorScheme(
    primary = AdisGebeyaColors.Primary,
    onPrimary = AdisGebeyaColors.OnColor,
    primaryContainer = AdisGebeyaColors.PrimaryLight,
    onPrimaryContainer = AdisGebeyaColors.PrimaryDark,
    secondary = AdisGebeyaColors.PrimaryDark,
    onSecondary = AdisGebeyaColors.OnColor,
    tertiary = AdisGebeyaColors.AccentOrange,
    onTertiary = AdisGebeyaColors.OnColor,
    background = AdisGebeyaColors.Background,
    onBackground = AdisGebeyaColors.PrimaryText,
    surface = AdisGebeyaColors.Surface,
    onSurface = AdisGebeyaColors.PrimaryText,
    surfaceVariant = AdisGebeyaColors.SecondarySurface,
    onSurfaceVariant = AdisGebeyaColors.SecondaryText,
    outline = AdisGebeyaColors.Border,
    error = AdisGebeyaColors.Error,
    onError = AdisGebeyaColors.OnColor,
)

private val AdisGebeyaDarkScheme = darkColorScheme(
    primary = AdisGebeyaColors.Primary,
    onPrimary = AdisGebeyaColors.OnColor,
    primaryContainer = AdisGebeyaColors.PrimaryDark,
    onPrimaryContainer = AdisGebeyaColors.PrimaryLight,
    secondary = AdisGebeyaColors.AccentOrange,
    onSecondary = AdisGebeyaColors.OnColor,
    tertiary = AdisGebeyaColors.AccentOrange,
    onTertiary = AdisGebeyaColors.OnColor,
    background = AdisGebeyaColors.DarkBackground,
    onBackground = AdisGebeyaColors.OnColor,
    surface = AdisGebeyaColors.DarkCard,
    onSurface = AdisGebeyaColors.OnColor,
    surfaceVariant = AdisGebeyaColors.DarkCard,
    onSurfaceVariant = AdisGebeyaColors.Disabled,
    outline = AdisGebeyaColors.Border,
    error = AdisGebeyaColors.Error,
    onError = AdisGebeyaColors.OnColor,
)

private data class ShopHomeFilters(
    val categorySlug: String? = null,
    val inStockOnly: Boolean = false,
    val discountedOnly: Boolean = false,
    val sort: String = "Newest",
)

private fun ShopHomeFilters.activeLabels(categories: List<ShopCategoryDto>): List<String> = buildList {
    categorySlug?.let { slug ->
        categories.firstOrNull { it.slug == slug }?.name?.let(::add)
    }
    if (inStockOnly) add("In stock")
    if (discountedOnly) add("Discounted")
    if (sort != "Newest") add(sort)
}

private data class PromotionBannerUiModel(
    val id: String,
    val title: String,
    val imageResource: Int,
    val accessibilityLabel: String,
    val displayOrder: Int,
    val category: ShopCategoryDto,
)

private data class PromotionBannerDefinition(
    val id: String,
    val title: String,
    val imageResource: Int,
    val accessibilityLabel: String,
    val displayOrder: Int,
    val match: (ShopCategoryDto) -> Boolean,
)

private val localPromotionDefinitions = listOf(
    PromotionBannerDefinition(
        id = "electronics",
        title = "Electronics",
        imageResource = R.drawable.adisgebeya_promo_electronics,
        accessibilityLabel = "Electronics promotion",
        displayOrder = 1,
        match = { category -> category.matchesCategory("electronics", "electronic", "tech", "phone", "laptop") },
    ),
    PromotionBannerDefinition(
        id = "fashion",
        title = "Fashion",
        imageResource = R.drawable.adisgebeya_promo_fashion,
        accessibilityLabel = "Fashion promotion",
        displayOrder = 2,
        match = { category -> category.matchesCategory("fashion", "clothing", "clothes", "apparel", "wear") },
    ),
    PromotionBannerDefinition(
        id = "home_items",
        title = "Home Items",
        imageResource = R.drawable.adisgebeya_promo_home_items,
        accessibilityLabel = "Home items promotion",
        displayOrder = 3,
        match = { category -> category.matchesCategory("home", "home items", "home living", "living", "kitchen", "furniture") },
    ),
    PromotionBannerDefinition(
        id = "cosmetics_jewellery",
        title = "Cosmetics & Jewellery",
        imageResource = R.drawable.adisgebeya_promo_cosmetics_jewellery,
        accessibilityLabel = "Cosmetics and jewellery promotion",
        displayOrder = 4,
        match = { category -> category.matchesCategory("cosmetics", "jewellery", "jewelry", "beauty", "skin", "makeup") },
    ),
)

private fun ShopCategoryDto.matchesCategory(vararg tokens: String): Boolean {
    val haystack = "$name $slug ${icon.orEmpty()}".lowercase()
    return tokens.any { token -> haystack.contains(token.lowercase()) }
}

private fun promotionBannersFor(categories: List<ShopCategoryDto>): List<PromotionBannerUiModel> =
    localPromotionDefinitions.mapNotNull { definition ->
        categories.firstOrNull(definition.match)?.let { category ->
            PromotionBannerUiModel(
                id = definition.id,
                title = definition.title,
                imageResource = definition.imageResource,
                accessibilityLabel = definition.accessibilityLabel,
                displayOrder = definition.displayOrder,
                category = category,
            )
        }
    }.sortedBy { it.displayOrder }

sealed class ShopRoute {
    data object Home : ShopRoute()
    data object Trending : ShopRoute()
    data object Cart : ShopRoute()
    data object Account : ShopRoute()
    data object Orders : ShopRoute()
    data object Saved : ShopRoute()
    data object RecentlyViewed : ShopRoute()
    data object Addresses : ShopRoute()
    data object Reviews : ShopRoute()
    data object BuyerProtection : ShopRoute()
    data object Support : ShopRoute()
    data object SellerApply : ShopRoute()
    data object SellerStatus : ShopRoute()
    data object SellerDashboard : ShopRoute()
    data object Admin : ShopRoute()
    data object Checkout : ShopRoute()
    data class Product(val product: ShopProductDto) : ShopRoute()
    data class Category(val category: ShopCategoryDto) : ShopRoute()
    data class Store(val store: ShopStoreDto) : ShopRoute()
}

private enum class ShopRootTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Trending("Trending", Icons.Default.TrendingUp),
    Cart("Cart", Icons.Default.ShoppingCart),
    Account("Account", Icons.Default.Person),
}

private enum class ShopAccountSubpage(val title: String) {
    Orders("My AdisGebeya Orders"),
    Saved("Saved Products"),
    RecentlyViewed("Recently Viewed"),
    Addresses("Delivery Addresses"),
    Reviews("My Reviews"),
    BuyerProtection("Buyer Protection"),
}

private fun ShopRoute.rootTab(): ShopRootTab = when (this) {
    ShopRoute.Trending -> ShopRootTab.Trending
    ShopRoute.Cart -> ShopRootTab.Cart
    ShopRoute.Account,
    ShopRoute.Orders,
    ShopRoute.Saved,
    ShopRoute.RecentlyViewed,
    ShopRoute.Addresses,
    ShopRoute.Reviews,
    ShopRoute.BuyerProtection,
    ShopRoute.Support -> ShopRootTab.Account
    else -> ShopRootTab.Home
}

private fun ShopRoute.accountSubpage(): ShopAccountSubpage? = when (this) {
    ShopRoute.Orders -> ShopAccountSubpage.Orders
    ShopRoute.Saved -> ShopAccountSubpage.Saved
    ShopRoute.RecentlyViewed -> ShopAccountSubpage.RecentlyViewed
    ShopRoute.Addresses -> ShopAccountSubpage.Addresses
    ShopRoute.Reviews -> ShopAccountSubpage.Reviews
    ShopRoute.BuyerProtection -> ShopAccountSubpage.BuyerProtection
    else -> null
}

@Composable
fun ShopRootScreen(
    initialRoute: ShopRoute = ShopRoute.Home,
    onBackToHome: () -> Unit,
) {
    val vm: ShopViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val protectedState by vm.protectedState.collectAsStateWithLifecycle()
    var selectedTab by remember(initialRoute) { mutableStateOf(initialRoute.rootTab()) }
    var detailRoute by remember(initialRoute) {
        mutableStateOf<ShopRoute?>(
            when (initialRoute) {
                is ShopRoute.Product, is ShopRoute.Category, is ShopRoute.Store, ShopRoute.Support, ShopRoute.Admin, ShopRoute.Checkout -> initialRoute
                else -> null
            }
        )
    }
    var accountSubpage by remember(initialRoute) { mutableStateOf(initialRoute.accountSubpage()) }
    val snackbar = remember { SnackbarHostState() }

    BackHandler {
        when {
            detailRoute != null -> detailRoute = null
            accountSubpage != null -> accountSubpage = null
            else -> onBackToHome()
        }
    }

    AdisGebeyaPageTheme {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = ShopBg,
        bottomBar = {
            if (detailRoute == null) {
                ShopBottomBar(
                    selectedTab = selectedTab,
                    cartCount = state.cartCount,
                    onSelect = { tab ->
                        selectedTab = tab
                        detailRoute = null
                        if (tab != ShopRootTab.Account) accountSubpage = null
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            val activeDetail = detailRoute
            if (activeDetail != null) {
                when (activeDetail) {
                    is ShopRoute.Product -> ProductDetailsScreen(
                        product = activeDetail.product,
                        onBack = { detailRoute = null },
                        onSearch = {
                            detailRoute = null
                            selectedTab = ShopRootTab.Home
                        },
                        onAddToCart = { vm.addToCart(activeDetail.product) },
                        onCart = {
                            detailRoute = null
                            selectedTab = ShopRootTab.Cart
                        },
                        onToggleFavorite = { vm.toggleFavorite(activeDetail.product) },
                        onBuyNow = {
                            vm.buyNow(activeDetail.product)
                            detailRoute = ShopRoute.Checkout
                        },
                        onChatSeller = { vm.chatSeller(activeDetail.product) },
                        onVisitStore = { detailRoute = ShopRoute.Store(activeDetail.product.toStoreDto()) },
                        saved = protectedState.favorites.any { it.productId == activeDetail.product.id },
                        cartCount = state.cartCount,
                    )
                    is ShopRoute.Category -> CategoryProductsScreen(
                        category = activeDetail.category,
                        state = state,
                        protectedState = protectedState,
                        onBack = { detailRoute = null },
                        onProduct = { detailRoute = ShopRoute.Product(it) },
                        onAddToCart = vm::addToCart,
                        onToggleFavorite = vm::toggleFavorite,
                    )
                    ShopRoute.Checkout -> CheckoutScreen(
                        protectedState = protectedState,
                        onBack = { detailRoute = null },
                        onLoadAddresses = vm::loadAddresses,
                        onConfirm = vm::confirmCheckout,
                    )
                    is ShopRoute.Store -> StoreDetailsScreen(
                        store = activeDetail.store,
                        state = state,
                        onBack = { detailRoute = null },
                        onProduct = { detailRoute = ShopRoute.Product(it) },
                        onAddToCart = vm::addToCart,
                        onToggleFavorite = vm::toggleFavorite,
                        onChatSeller = { vm.chatSeller(it) },
                        savedIds = protectedState.favorites.map { it.productId }.toSet(),
                        busyIds = protectedState.busyIds,
                    )
                    ShopRoute.Support -> PlaceholderShopPage("AdisGebeya Support", "Support for orders, sellers and delivery will appear here.", onBack = { detailRoute = null })
                    ShopRoute.Admin -> AdminCenterScreen(
                        protectedState = protectedState,
                        onBack = { detailRoute = null },
                        onLoad = {
                            vm.loadAdminSession()
                            vm.loadAdminQueues()
                            vm.loadAdminOrders()
                            vm.loadAdminAuditLog()
                        },
                        onRefresh = {
                            vm.loadAdminDashboard()
                            vm.loadAdminQueues()
                            vm.loadAdminOrders()
                            vm.loadAdminAuditLog()
                        },
                        onReviewSeller = vm::adminReviewSeller,
                        onReviewProduct = vm::adminReviewProduct,
                    )
                    else -> detailRoute = null
                }
            } else {
                when (selectedTab) {
                    ShopRootTab.Home -> ShopHomeScreen(
                    state = state,
                    onBack = onBackToHome,
                    onRefresh = vm::refresh,
                    onCart = { selectedTab = ShopRootTab.Cart },
                    onProduct = { detailRoute = ShopRoute.Product(it) },
                    onStore = { detailRoute = ShopRoute.Store(it) },
                    onCategory = { detailRoute = ShopRoute.Category(it) },
                    onAddToCart = { vm.addToCart(it) },
                    onToggleFavorite = { vm.toggleFavorite(it) },
                    savedIds = protectedState.favorites.map { it.productId }.toSet(),
                    busyIds = protectedState.busyIds,
                    )
                    ShopRootTab.Trending -> TrendingScreen(
                        state = state,
                        protectedState = protectedState,
                        onBack = onBackToHome,
                        onRefresh = vm::refresh,
                        onProduct = { detailRoute = ShopRoute.Product(it) },
                        onCategory = { detailRoute = ShopRoute.Category(it) },
                        onAddToCart = vm::addToCart,
                        onToggleFavorite = vm::toggleFavorite,
                    )
                    ShopRootTab.Cart -> CartScreen(
                        protectedState,
                        state.cartCount,
                        onBackToHome,
                        vm::loadCart,
                        vm::updateCartItem,
                        vm::clearCart,
                        onCheckout = {
                            vm.createCheckout()
                            detailRoute = ShopRoute.Checkout
                        },
                    )
                    ShopRootTab.Account -> ShopAccountScreen(
                        state = state,
                        protectedState = protectedState,
                        selectedSubpage = accountSubpage,
                        onBack = onBackToHome,
                        onSubpage = { accountSubpage = it },
                        onSubpageBack = { accountSubpage = null },
                        onAdminCenter = { detailRoute = ShopRoute.Admin },
                        onProduct = { detailRoute = ShopRoute.Product(it) },
                        actions = ShopAccountActions(
                            loadFavorites = vm::loadFavorites,
                            loadRecentlyViewed = vm::loadRecentlyViewed,
                            loadAddresses = vm::loadAddresses,
                            loadOrders = vm::loadOrders,
                            loadOrderDetail = vm::loadOrderDetail,
                            requestOrderCancellation = vm::requestOrderCancellation,
                            revealDeliveryCode = vm::revealDeliveryCode,
                            loadNotifications = vm::loadNotifications,
                            markAllNotificationsRead = vm::markAllNotificationsRead,
                            loadSellerStatus = vm::loadSellerStatus,
                            loadSellerOrders = vm::loadSellerOrders,
                            sellerOrderAction = vm::sellerOrderAction,
                            addToCart = vm::addToCart,
                            toggleFavorite = vm::toggleFavorite,
                            saveAddress = vm::saveAddress,
                            deleteAddress = vm::deleteAddress,
                            setDefaultAddress = vm::setDefaultAddress,
                            submitSellerApplication = vm::submitSellerApplication,
                            loadAdminSession = vm::loadAdminSession,
                        ),
                    )
                }
            }
        }
    }
    }

    LaunchedEffect(selectedTab, detailRoute, accountSubpage) {
        when {
            selectedTab == ShopRootTab.Cart && detailRoute == null -> vm.loadCart()
            selectedTab == ShopRootTab.Account && accountSubpage == ShopAccountSubpage.Orders -> vm.loadOrders()
            selectedTab == ShopRootTab.Account && accountSubpage == ShopAccountSubpage.Saved -> vm.loadFavorites()
            selectedTab == ShopRootTab.Account && accountSubpage == ShopAccountSubpage.RecentlyViewed -> vm.loadRecentlyViewed()
            selectedTab == ShopRootTab.Account && accountSubpage == ShopAccountSubpage.Addresses -> vm.loadAddresses()
            selectedTab == ShopRootTab.Account && accountSubpage == null -> vm.loadAdminSession()
            detailRoute is ShopRoute.Product -> vm.recordProductView((detailRoute as ShopRoute.Product).product)
            else -> Unit
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    LaunchedEffect(detailRoute, protectedState.checkout) {
        if (detailRoute == ShopRoute.Checkout && protectedState.checkout != null) {
            snackbar.currentSnackbarData?.dismiss()
        }
    }
}

@Composable
private fun AdisGebeyaPageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) AdisGebeyaDarkScheme else AdisGebeyaLightScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}

@Composable
fun SellerAccountRootScreen(onBackToHome: () -> Unit) {
    val vm: ShopViewModel = viewModel()
    val protectedState by vm.protectedState.collectAsStateWithLifecycle()
    BackHandler { onBackToHome() }
    AdisGebeyaPageTheme {
        SellerCenterScreen(
            protectedState = protectedState,
            onBack = onBackToHome,
            onLoad = {
                vm.loadSellerStatus()
                vm.loadSellerOrders()
            },
            onSubmit = vm::submitSellerApplication,
            onSellerAction = vm::sellerOrderAction,
        )
    }
}

private data class ShopAccountActions(
    val loadFavorites: () -> Unit,
    val loadRecentlyViewed: () -> Unit,
    val loadAddresses: () -> Unit,
    val loadOrders: () -> Unit,
    val loadOrderDetail: (String) -> Unit,
    val requestOrderCancellation: (String, String) -> Unit,
    val revealDeliveryCode: (String) -> Unit,
    val loadNotifications: () -> Unit,
    val markAllNotificationsRead: () -> Unit,
    val loadSellerStatus: () -> Unit,
    val loadSellerOrders: () -> Unit,
    val sellerOrderAction: (String, String, String, String?, String?) -> Unit,
    val addToCart: (ShopProductDto) -> Unit,
    val toggleFavorite: (ShopProductDto) -> Unit,
    val saveAddress: (ShopAddressRequest) -> Unit,
    val deleteAddress: (String) -> Unit,
    val setDefaultAddress: (String) -> Unit,
    val submitSellerApplication: (String) -> Unit,
    val loadAdminSession: () -> Unit,
)

@Composable
private fun ShopBottomBar(
    selectedTab: ShopRootTab,
    cartCount: Int,
    onSelect: (ShopRootTab) -> Unit,
) {
    NavigationBar(
        containerColor = ShopSurface,
        tonalElevation = 10.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .border(1.dp, ShopLine.copy(alpha = .65f)),
    ) {
        ShopRootTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tab == ShopRootTab.Cart && cartCount > 0) {
                                Badge(containerColor = ShopRed, contentColor = ShopOnColor) {
                                    Text(cartCount.coerceAtMost(99).toString(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        },
                    ) {
                        Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(23.dp))
                    }
                },
                label = { Text(tab.label, fontSize = 11.sp, fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Medium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ShopGreen,
                    selectedTextColor = ShopInk,
                    indicatorColor = ShopGreenSoft,
                    unselectedIconColor = ShopMuted,
                    unselectedTextColor = ShopMuted,
                ),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrendingScreen(
    state: ShopHomeState,
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onCategory: (ShopCategoryDto) -> Unit,
    onAddToCart: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
) {
    val savedIds = protectedState.favorites.map { it.productId }.toSet()
    LazyColumn(
        Modifier.fillMaxSize().background(ShopBg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { PageTitle("Discover", "Recently added products and real marketplace signals") }
        when {
            state.loading -> items(4) { SkeletonCard() }
            state.error -> item { ErrorCard(maintenance = state.maintenance, onRefresh = onRefresh) }
            else -> {
                val productsWithSales = state.products.filter { (it.soldCount ?: 0) > 0 }
                val productsWithRatings = state.products.filter { (it.reviewCount ?: 0) > 0 }
                item { ProductRail("Recently added", state.products.sortedByDescending { it.id }, onProduct, onToggleFavorite, savedIds) }
                if (productsWithSales.isNotEmpty()) {
                    item {
                        ProductRail("Ordered by customers", productsWithSales.sortedByDescending { it.soldCount ?: 0 }, onProduct, onToggleFavorite, savedIds)
                    }
                }
                item { CategoryRail(state.categories, onCategory) }
                if (productsWithRatings.isNotEmpty()) {
                    item {
                        ProductRail("Reviewed products", productsWithRatings.sortedByDescending { it.averageRating ?: 0.0 }, onProduct, onToggleFavorite, savedIds)
                    }
                }
                if (state.banners.isNotEmpty()) item { PromoCarousel(state.banners) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CategoryProductsScreen(
    category: ShopCategoryDto,
    state: ShopHomeState,
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onAddToCart: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
) {
    val products = state.products.filter { it.categorySlug == category.slug || it.categorySlug == category.name.lowercase() }
    LazyColumn(Modifier.fillMaxSize().background(ShopBg).statusBarsPadding(), contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        stickyHeader { ShopHeader(cartCount = state.cartCount, onBack = onBack, onCart = {}) }
        item { PageTitle(category.name, "${products.size} products available") }
        item {
            if (products.isEmpty()) EmptyCard("No products available in this category yet.")
            else ProductGridSection("Products", products, onProduct, onToggleFavorite, protectedState.favorites.map { it.productId }.toSet(), maxItems = Int.MAX_VALUE)
        }
    }
}

@Composable
private fun ShopAccountScreen(
    state: ShopHomeState,
    protectedState: ShopProtectedState,
    selectedSubpage: ShopAccountSubpage?,
    onBack: () -> Unit,
    onSubpage: (ShopAccountSubpage) -> Unit,
    onSubpageBack: () -> Unit,
    onAdminCenter: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    actions: ShopAccountActions,
) {
    LaunchedEffect(Unit) {
        actions.loadAdminSession()
    }
    when (selectedSubpage) {
        ShopAccountSubpage.Orders -> OrdersScreen(
            protectedState,
            onSubpageBack,
            actions.loadOrders,
            actions.loadOrderDetail,
            actions.requestOrderCancellation,
            actions.revealDeliveryCode,
        )
        ShopAccountSubpage.Saved -> SavedProductsScreen(protectedState, state.cartCount, onSubpageBack, actions.loadFavorites, onProduct, actions.toggleFavorite, actions.addToCart)
        ShopAccountSubpage.RecentlyViewed -> RecentlyViewedScreen(protectedState, state.cartCount, onSubpageBack, actions.loadRecentlyViewed, onProduct, actions.addToCart)
        ShopAccountSubpage.Addresses -> AddressesScreen(protectedState, onSubpageBack, actions.loadAddresses, actions.saveAddress, actions.deleteAddress, actions.setDefaultAddress)
        ShopAccountSubpage.Reviews -> PlaceholderShopPage("My Reviews", "No reviews yet. Reviews will appear here after verified AdisGebeya purchases support reviews.", onBack = onSubpageBack)
        ShopAccountSubpage.BuyerProtection -> BuyerProtectionPage(onBack = onSubpageBack)
        null -> ShopAccountHome(state, protectedState, onBack, onSubpage, onAdminCenter)
    }
}

@Composable
private fun ShopAccountHome(
    state: ShopHomeState,
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onSubpage: (ShopAccountSubpage) -> Unit,
    onAdminCenter: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(ShopBg).statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(2.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(ShopGreenSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your AdisGebeya Account", color = ShopInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (state.shopIdentityReady) "Connected to your AdisChat identity" else "Connecting to AdisGebeya...", color = ShopMuted, fontSize = 13.sp)
                    }
                }
            }
        }
        item { AccountGroup("AdisGebeya", listOf(
            Triple("My Orders", "Completed AdisGebeya purchases", ShopAccountSubpage.Orders),
            Triple("Saved Products", "Products you saved", ShopAccountSubpage.Saved),
            Triple("Recently Viewed", "Products you opened recently", ShopAccountSubpage.RecentlyViewed),
            Triple("Delivery Addresses", "Manage delivery locations", ShopAccountSubpage.Addresses),
        ), onSubpage) }
        item { AccountGroup("Support", listOf(
            Triple("Reviews", "Your product and store feedback", ShopAccountSubpage.Reviews),
            Triple("Buyer Protection", "How AdisGebeya protects eligible purchases", ShopAccountSubpage.BuyerProtection),
        ), onSubpage) }
        if (protectedState.adminSession != null) {
            item { AdminAccountEntry(protectedState, onAdminCenter) }
        }
        item { EmptyCard("AdisGebeya settings will appear here when configurable shopping preferences are available.") }
    }
}

@Composable
private fun AdminAccountEntry(
    protectedState: ShopProtectedState,
    onAdminCenter: () -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { onAdminCenter() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(ShopOrange.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Security, contentDescription = null, tint = ShopOrangeDark, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("AdisGebeya Admin", color = ShopInk, fontWeight = FontWeight.SemiBold)
                Text(
                    protectedState.adminSession?.primaryRole?.let { backendLabel(it) } ?: "Moderation Center",
                    color = ShopMuted,
                    fontSize = 12.sp,
                )
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = ShopMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AccountGroup(
    title: String,
    rows: List<Triple<String, String, ShopAccountSubpage>>,
    onSubpage: (ShopAccountSubpage) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(1.dp)) {
            Column(Modifier.padding(vertical = 6.dp)) {
                rows.forEachIndexed { index, row ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSubpage(row.third) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(ShopGreenSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.first, color = ShopInk, fontWeight = FontWeight.Medium)
                            Text(row.second, color = ShopMuted, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = ShopMuted, modifier = Modifier.size(18.dp))
                    }
                    if (index != rows.lastIndex) HorizontalDivider(color = ShopLine.copy(alpha = .6f), modifier = Modifier.padding(start = 68.dp))
                }
            }
        }
    }
}

private enum class AdminTab(val label: String) {
    Sellers("Sellers"),
    Products("Products"),
    Orders("Orders"),
    Audit("Audit"),
}

@Composable
private fun AdminCenterScreen(
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onReviewSeller: (String, String, String) -> Unit,
    onReviewProduct: (String, String, String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(AdminTab.Sellers) }
    var reason by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { onLoad() }

    LazyColumn(
        Modifier.fillMaxSize().background(ShopBg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ShopInk) }
                Column(Modifier.weight(1f)) {
                    Text("AdisGebeya Admin", color = ShopInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(backendLabel(protectedState.adminSession?.primaryRole).ifBlank { "Moderation Center" }, color = ShopMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ShopGreen) }
            }
        }

        if (protectedState.adminLoading) {
            item { EmptyCard("Loading admin workspace...") }
        }

        protectedState.adminError?.takeIf { it.isNotBlank() }?.let { message ->
            item { EmptyCard(message) }
        }

        if (protectedState.adminSession == null && !protectedState.adminLoading) {
            item { EmptyCard("You do not have access to AdisGebeya Admin.") }
        }

        item { AdminDashboardCards(protectedState) }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AdminTab.entries) { tab ->
                    val selected = selectedTab == tab
                    Surface(
                        color = if (selected) ShopGreen else ShopSurface,
                        contentColor = if (selected) ShopOnColor else ShopInk,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.clickable { selectedTab = tab },
                    ) {
                        Text(tab.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        }

        if (selectedTab == AdminTab.Sellers || selectedTab == AdminTab.Products) {
            item {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reason for rejection or changes") },
                    singleLine = false,
                )
            }
        }

        when (selectedTab) {
            AdminTab.Sellers -> {
                if (protectedState.adminSellerApplications.isEmpty()) item { EmptyCard("No seller applications are waiting for review.") }
                items(protectedState.adminSellerApplications, key = { it.id }) { application ->
                    AdminSellerApplicationCard(application, protectedState.busyIds.contains(application.id), reason, onReviewSeller)
                }
            }
            AdminTab.Products -> {
                if (protectedState.adminProducts.isEmpty()) item { EmptyCard("No products are waiting for moderation.") }
                items(protectedState.adminProducts, key = { it.id }) { product ->
                    AdminProductCard(product, protectedState.busyIds.contains(product.id), reason, onReviewProduct)
                }
            }
            AdminTab.Orders -> {
                if (protectedState.adminOrders.isEmpty()) item { EmptyCard("No open orders found.") }
                items(protectedState.adminOrders, key = { it.id }) { order ->
                    AdminOrderCard(order)
                }
            }
            AdminTab.Audit -> {
                if (protectedState.adminAuditLog.isEmpty()) item { EmptyCard("No admin audit events yet.") }
                items(protectedState.adminAuditLog, key = { it.id }) { audit ->
                    AdminAuditCard(audit)
                }
            }
        }
    }
}

@Composable
private fun AdminDashboardCards(protectedState: ShopProtectedState) {
    val dashboard = protectedState.adminDashboard
    val metrics = listOf(
        "Seller apps" to (dashboard?.pendingSellerApplications ?: protectedState.adminSellerApplications.size),
        "Products" to (dashboard?.productsAwaitingReview ?: protectedState.adminProducts.size),
        "Open orders" to (dashboard?.openOrders ?: protectedState.adminOrders.size),
        "Audit 24h" to (dashboard?.auditEvents24h ?: 0),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(metrics) { metric ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(1.dp), modifier = Modifier.width(132.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(metric.first, color = ShopMuted, fontSize = 12.sp)
                    Text(metric.second.toString(), color = ShopInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AdminSellerApplicationCard(
    application: ShopAdminSellerApplicationDto,
    busy: Boolean,
    reason: String,
    onReviewSeller: (String, String, String) -> Unit,
) {
    val status = statusKey(application.status)
    AdminReviewCard(
        title = application.storeName.ifBlank { "Unnamed store" },
        subtitle = "${application.applicantName.ifBlank { "Seller" }} • ${backendLabel(application.status)}",
        meta = application.applicantEmail.orEmpty(),
        busy = busy,
        primaryLabel = if (status == "suspended") "Reactivate" else "Approve",
        secondaryLabel = when (status) {
            "submitted" -> "Start review"
            "approved" -> "Suspend"
            else -> "Reject"
        },
        tertiaryLabel = if (status in listOf("under_review", "submitted")) "Reject" else null,
        onPrimary = { onReviewSeller(application.id, if (status == "suspended") "reactivate" else "approve", "") },
        onSecondary = {
            when (status) {
                "submitted" -> onReviewSeller(application.id, "start_review", "")
                "approved" -> onReviewSeller(application.id, "suspend", reason.ifBlank { "Seller account requires administrative review." })
                else -> onReviewSeller(application.id, "reject", reason.ifBlank { "Application needs more information." })
            }
        },
        onTertiary = { onReviewSeller(application.id, "reject", reason.ifBlank { "Application needs more information." }) },
    )
}

@Composable
private fun AdminProductCard(
    product: ShopAdminProductDto,
    busy: Boolean,
    reason: String,
    onReviewProduct: (String, String, String) -> Unit,
) {
    AdminReviewCard(
        title = product.title.ifBlank { "Untitled product" },
        subtitle = "${product.storeName.ifBlank { "Store" }} • ${backendLabel(product.moderationStatus.ifBlank { product.status })}",
        meta = formatEtb(product.priceMinor),
        busy = busy,
        primaryLabel = "Approve",
        secondaryLabel = if (statusKey(product.moderationStatus) == "submitted") "Start review" else "Request changes",
        tertiaryLabel = "Reject",
        onPrimary = { onReviewProduct(product.id, "approve", "") },
        onSecondary = {
            val action = if (statusKey(product.moderationStatus) == "submitted") "start_review" else "request_changes"
            onReviewProduct(product.id, action, reason.ifBlank { "Product needs changes before publishing." })
        },
        onTertiary = { onReviewProduct(product.id, "reject", reason.ifBlank { "Product does not meet AdisGebeya listing standards." }) },
    )
}

@Composable
private fun AdminReviewCard(
    title: String,
    subtitle: String,
    meta: String,
    busy: Boolean,
    primaryLabel: String,
    secondaryLabel: String,
    tertiaryLabel: String? = null,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onTertiary: () -> Unit = {},
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = ShopMuted, fontSize = 12.sp)
            if (meta.isNotBlank()) Text(meta, color = ShopGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSecondary, enabled = !busy, modifier = Modifier.weight(1f)) { Text(secondaryLabel) }
                Button(onClick = onPrimary, enabled = !busy, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) {
                    Text(if (busy) "Saving..." else primaryLabel)
                }
            }
            tertiaryLabel?.let {
                TextButton(onClick = onTertiary, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = ShopRed)
                }
            }
        }
    }
}

@Composable
private fun AdminOrderCard(order: ShopAdminOrderDto) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(order.orderNumber.ifBlank { "Order" }, color = ShopInk, fontWeight = FontWeight.SemiBold)
            Text("${orderStatusLabel(order.status)} • ${fulfillmentStatusLabel(order.fulfillmentStatus)}", color = ShopMuted, fontSize = 12.sp)
            Text("${paymentStatusLabel(order.paymentStatus)} • ${formatEtb(order.totalMinor)}", color = ShopGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AdminAuditCard(audit: ShopAdminAuditLogDto) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(backendLabel(audit.action), color = ShopInk, fontWeight = FontWeight.SemiBold)
            Text("${backendLabel(audit.entityType)} • ${audit.actorName ?: "Admin"}", color = ShopMuted, fontSize = 12.sp)
            audit.createdAt?.let { Text(it, color = ShopMuted, fontSize = 11.sp) }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ShopHomeScreen(
    state: ShopHomeState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCart: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onStore: (ShopStoreDto) -> Unit,
    onCategory: (ShopCategoryDto) -> Unit,
    onAddToCart: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
    savedIds: Set<String>,
    busyIds: Set<String>,
) {
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(ShopHomeFilters()) }
    var showFilters by remember { mutableStateOf(false) }
    val selectedCategory = filters.categorySlug
    val listState = rememberLazyListState()
    val products = remember(state.products, query, filters) {
        state.products
            .filter { product ->
                val matchesQuery = query.isBlank() ||
                    product.title.contains(query, ignoreCase = true) ||
                    product.brand?.contains(query, ignoreCase = true) == true ||
                    product.store?.name?.contains(query, ignoreCase = true) == true
                val matchesCategory = selectedCategory == null || product.categorySlug == selectedCategory
                val matchesStock = !filters.inStockOnly || product.stockStatus?.contains("out", ignoreCase = true) != true
                val matchesDiscount = !filters.discountedOnly || (product.discountPercent ?: 0) > 0
                matchesQuery && matchesCategory && matchesStock && matchesDiscount
            }
            .let { list ->
                when (filters.sort) {
                    "Price low to high" -> list.sortedBy { it.priceMinor }
                    "Price high to low" -> list.sortedByDescending { it.priceMinor }
                    "Most discounted" -> list.sortedByDescending { it.discountPercent ?: 0 }
                    else -> list
                }
            }
    }
    val deals = products.filter { (it.discountPercent ?: 0) > 0 }
    val newArrivals = products.take(8)
    val topProducts = products.filter { (it.soldCount ?: 0) > 0 || (it.reviewCount ?: 0) > 0 }
        .sortedByDescending { (it.soldCount ?: 0) + ((it.averageRating ?: 0.0) * 10).toInt() }
    val promotionBanners = remember(state.categories) { promotionBannersFor(state.categories) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(ShopBg).navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        stickyHeader {
            AdisGebeyaBrandHeader(
                cartCount = state.cartCount,
                onCart = onCart,
                query = query,
                onQuery = { query = it },
                onFilter = { showFilters = true },
                activeFilterCount = filters.activeLabels(state.categories).size,
                modifier = Modifier.fillParentMaxWidth(),
            )
        }
        if (filters.activeLabels(state.categories).isNotEmpty()) {
            item {
                ShopHomePadded {
                    ActiveFilterRow(
                        labels = filters.activeLabels(state.categories),
                        onClear = { filters = ShopHomeFilters() },
                    )
                }
            }
        }
        when {
            state.loading -> items(5) { ShopHomePadded { SkeletonCard() } }
            state.error -> item { ShopHomePadded { ErrorCard(maintenance = state.maintenance, onRefresh = onRefresh) } }
            else -> {
                if (promotionBanners.isNotEmpty()) {
                    item { ShopHomePadded { LocalPromotionCarousel(promotionBanners, onCategory) } }
                }
                if (state.categories.isNotEmpty()) {
                    item { ShopHomePadded { CategoryShortcutRow(state.categories, onCategory, onSelectFilter = { filters = filters.copy(categorySlug = it.slug) }) } }
                }
                if (deals.isNotEmpty()) {
                    item { ShopHomePadded { ProductRail("Flash deals", deals, onProduct, onToggleFavorite, savedIds, compact = true) } }
                }
                if (products.isNotEmpty()) {
                    item { ShopHomePadded { ProductGridSection("Recommended for you", products, onProduct, onToggleFavorite, savedIds) } }
                } else {
                    item { ShopHomePadded { EmptyCard("No matching products yet. Adjust your search or filters.") } }
                }
                item { ShopHomePadded { TrustBenefits() } }
                if (newArrivals.isNotEmpty()) item { ShopHomePadded { ProductRail("New arrivals", newArrivals, onProduct, onToggleFavorite, savedIds, compact = true) } }
                if (topProducts.isNotEmpty()) item { ShopHomePadded { ProductGridSection("Popular products", topProducts, onProduct, onToggleFavorite, savedIds) } }
            }
        }
    }

    if (showFilters) {
        FilterBottomSheet(
            filters = filters,
            categories = state.categories,
            onDismiss = { showFilters = false },
            onApply = {
                filters = it
                showFilters = false
            },
            onReset = {
                filters = ShopHomeFilters()
                showFilters = false
            },
        )
    }
}

@Composable
private fun ShopHomePadded(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        content()
    }
}

@Composable
private fun AdisGebeyaBrandHeader(
    cartCount: Int,
    onCart: () -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    onFilter: () -> Unit,
    activeFilterCount: Int,
    modifier: Modifier = Modifier,
) {
    val headerHeight = 132.dp
    val cornerRadius = 16.dp
    Surface(
        color = ShopTransparent,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(bottomStart = cornerRadius, bottomEnd = cornerRadius)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .clip(RoundedCornerShape(bottomStart = cornerRadius, bottomEnd = cornerRadius))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ShopGreenDark,
                            ShopGreenMid,
                            ShopGreen,
                        ),
                    ),
                )
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        ) {
            BrandBackgroundDecoration()
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StickyHeaderDeliveryChip()
                    Column(
                        Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = "AdisGebeya, አዲስ ገበያ"
                                heading()
                            },
                    ) {
                        Text("AdisGebeya", color = ShopOnColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("አዲስ ገበያ", color = ShopGreenSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MarketplaceSearchField(
                        query = query,
                        onQuery = onQuery,
                        modifier = Modifier.weight(1f),
                    )
                    MarketplaceHeaderAction(Icons.Default.FilterList, "Filter products", onFilter, activeFilterCount, active = activeFilterCount > 0)
                    MarketplaceHeaderAction(Icons.Default.Notifications, "Marketplace notifications", onClick = {}, badge = 0)
                    MarketplaceHeaderAction(Icons.Default.ShoppingCart, "Shopping cart", onCart, cartCount)
                }
            }
        }
    }
}

@Composable
private fun StickyHeaderDeliveryChip() {
    Row(
        Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ShopOnColor.copy(alpha = .16f))
            .border(1.dp, ShopOnColor.copy(alpha = .18f), RoundedCornerShape(18.dp))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = ShopOnColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Column(
            Modifier
                .semantics { contentDescription = "Deliver to, set delivery location" },
        ) {
            Text("Deliver to", color = ShopOnColor.copy(alpha = .78f), fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text("Set location", color = ShopOnColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun BrandBackgroundDecoration() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .size(126.dp)
                .clip(CircleShape)
                .background(ShopOnColor.copy(alpha = .08f)),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 44.dp, bottom = 14.dp)
                .height(1.dp)
                .fillMaxWidth(.72f)
                .clip(RoundedCornerShape(999.dp))
                .background(Brush.horizontalGradient(listOf(ShopOnColor.copy(alpha = .26f), ShopTransparent))),
        )
        Row(
            Modifier.align(Alignment.BottomEnd).graphicsLayer(alpha = .10f),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            repeat(5) { index ->
                Box(
                    Modifier
                        .size((9 + index * 2).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, ShopOnColor.copy(alpha = .65f), RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

@Composable
private fun MarketplaceSearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(ShopOnColor)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = "Search products, brands and stores", tint = ShopInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = ShopInk, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isBlank()) Text("Search products, brands and stores", color = ShopMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            },
        )
        if (query.isNotBlank()) {
            IconButton(onClick = { onQuery("") }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = ShopMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MarketplaceHeaderAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    badge: Int = 0,
    active: Boolean = false,
) {
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(ShopOnColor.copy(alpha = .96f))
                .border(1.dp, ShopOnColor.copy(alpha = .72f), CircleShape),
        ) {
            Icon(icon, contentDescription = description, tint = if (active) ShopGreen else ShopInk, modifier = Modifier.size(21.dp))
        }
        if (badge > 0) MarketplaceCountBadge(badge, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun MarketplaceCountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = if (count > 99) 28.dp else 19.dp, height = 19.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(ShopRed),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = ShopOnColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ShopHeader(
    cartCount: Int,
    onBack: () -> Unit,
    onCart: () -> Unit,
    query: String = "",
    onQuery: (String) -> Unit = {},
    onFilter: () -> Unit = {},
    activeFilterCount: Int = 0,
) {
    Surface(color = ShopBg.copy(alpha = .96f), tonalElevation = 0.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ShopSurface)
                        .border(1.dp, ShopLine, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = ShopInk, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = ShopInk, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isBlank()) Text("Search products, brands and stores", color = ShopMuted, fontSize = 12.sp, maxLines = 1)
                            inner()
                        },
                    )
                }
                HeaderIcon(Icons.Default.FilterList, "Filter products", onClick = onFilter, badge = activeFilterCount)
                HeaderIcon(Icons.Default.Notifications, "AdisGebeya notifications", onClick = {})
                HeaderIcon(Icons.Default.ShoppingCart, "Cart", onClick = onCart, badge = cartCount)
            }
        }
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit, badge: Int = 0, tint: Color = ShopInk) {
    Box {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp).clip(CircleShape).background(ShopSurface).border(1.dp, ShopLine, CircleShape)) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
        }
        if (badge > 0) {
            Box(Modifier.align(Alignment.TopEnd).size(width = if (badge > 99) 28.dp else 18.dp, height = 18.dp).clip(RoundedCornerShape(999.dp)).background(ShopRed), contentAlignment = Alignment.Center) {
                Text(if (badge > 99) "99+" else badge.toString(), color = ShopOnColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ActiveFilterRow(labels: List<String>, onClear: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels, key = { it }) { label ->
            Surface(color = ShopGreenSoft, shape = RoundedCornerShape(999.dp), border = androidx.compose.foundation.BorderStroke(1.dp, ShopGreen.copy(alpha = .18f))) {
                Text(label, color = ShopGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        item {
            TextButton(onClick = onClear) {
                Text("Clear all", color = ShopMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun FilterBottomSheet(
    filters: ShopHomeFilters,
    categories: List<ShopCategoryDto>,
    onDismiss: () -> Unit,
    onApply: (ShopHomeFilters) -> Unit,
    onReset: () -> Unit,
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ShopBg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Filter products", color = ShopInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (categories.isNotEmpty()) {
                Text("Category", color = ShopInk, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterPill("All", draft.categorySlug == null) { draft = draft.copy(categorySlug = null) } }
                    items(categories, key = { it.id }) { category ->
                        FilterPill(category.name, draft.categorySlug == category.slug) { draft = draft.copy(categorySlug = category.slug) }
                    }
                }
            }
            Text("Availability", color = ShopInk, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("In stock", draft.inStockOnly) { draft = draft.copy(inStockOnly = !draft.inStockOnly) }
                FilterPill("Discounted", draft.discountedOnly) { draft = draft.copy(discountedOnly = !draft.discountedOnly) }
            }
            Text("Sort by", color = ShopInk, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Newest", "Price low to high", "Price high to low", "Most discounted"), key = { it }) { sort ->
                    FilterPill(sort, draft.sort == sort) { draft = draft.copy(sort = sort) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Text("Reset", color = ShopInk)
                }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) {
                    Text("Apply", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (selected) ShopGreen else ShopSurface,
        contentColor = if (selected) ShopOnColor else ShopInk,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) ShopGreen else ShopLine),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp))
    }
}

@Composable
private fun DeliveryDestination() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ShopGreenSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Deliver to", color = ShopMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text("Set delivery location", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Text("Change", color = ShopGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.semantics { contentDescription = "Change delivery location" })
    }
}

@Composable
private fun LocalPromotionCarousel(
    promotions: List<PromotionBannerUiModel>,
    onCategory: (ShopCategoryDto) -> Unit,
) {
    if (promotions.isEmpty()) return
    val lifecycleOwner = LocalLifecycleOwner.current
    val pagerState = rememberPagerState(pageCount = { promotions.size })

    LaunchedEffect(promotions.size, lifecycleOwner) {
        if (promotions.size <= 1) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(4500)
                if (!pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage((pagerState.currentPage + 1) % promotions.size)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val promotion = promotions[page]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1481f / 396f)
                    .clickable { onCategory(promotion.category) },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ShopSoft),
                border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine.copy(alpha = .7f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Image(
                    painter = painterResource(promotion.imageResource),
                    contentDescription = promotion.accessibilityLabel,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ShopSoft),
                )
            }
        }
        if (promotions.size > 1) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                promotions.forEachIndexed { index, _ ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = if (pagerState.currentPage == index) 18.dp else 7.dp, height = 7.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (pagerState.currentPage == index) ShopGreen else ShopLine),
                    )
                }
            }
        }
    }
}

@Composable
private fun PromoCarousel(banners: List<ShopBannerDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(banners, key = { it.id }) { banner ->
            Box(
                Modifier.fillParentMaxWidth().height(186.dp).clip(RoundedCornerShape(22.dp)).background(ShopGreen),
            ) {
                if (banner.imageUrl.isNullOrBlank()) {
                    BannerFallbackArt()
                } else {
                    ShopAssetImage(banner.imageUrl, banner.title, Modifier.fillMaxSize(), iconSize = 48.dp)
                }
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(ShopOverlay.copy(alpha = .58f), ShopTransparent))))
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Center) {
                    Surface(color = ShopOnColor.copy(alpha = .18f), shape = RoundedCornerShape(999.dp)) {
                        Text("New arrivals", color = ShopOnColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(banner.title, color = ShopOnColor, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!banner.subtitle.isNullOrBlank()) {
                        Text(banner.subtitle, color = ShopOnColor.copy(alpha = .9f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(12.dp))
                    Surface(color = ShopOrange, shape = RoundedCornerShape(999.dp)) {
                        Text("Shop now", color = ShopOnColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            banners.take(4).forEachIndexed { index, _ ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (index == 0) 18.dp else 7.dp, height = 7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (index == 0) ShopGreen else ShopLine),
                )
            }
        }
    }
}

@Composable
private fun BannerFallbackArt() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(listOf(ShopGreen, ShopGreenSoft))),
    ) {
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Icons.Default.Devices, Icons.Default.Spa, Icons.Default.Checkroom).forEachIndexed { index, icon ->
                Box(
                    Modifier
                        .size((64 + index * 10).dp)
                        .clip(CircleShape)
                        .background(ShopOnColor.copy(alpha = .24f))
                        .border(1.dp, ShopOnColor.copy(alpha = .32f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = ShopOnColor.copy(alpha = .88f), modifier = Modifier.size((28 + index * 3).dp))
                }
            }
        }
    }
}

@Composable
private fun TrustBenefits() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(Icons.Default.LocalShipping, "Delivery support", "Where sellers support it"),
            Triple(Icons.Default.Security, "Secure checkout", "Protected AdisChat identity"),
            Triple(Icons.Default.Verified, "Verified sellers", "Approved marketplace stores"),
            Triple(Icons.Default.CheckCircle, "Buyer protection", "Order records and support"),
        ).forEach { item ->
            item { TrustChip(item.first, item.second, item.third) }
        }
    }
}

@Composable
private fun TrustChip(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.width(210.dp).clip(RoundedCornerShape(16.dp)).background(ShopGreenSoft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(subtitle, color = ShopMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CategoryShortcutRow(categories: List<ShopCategoryDto>, onCategory: (ShopCategoryDto) -> Unit, onSelectFilter: (ShopCategoryDto) -> Unit) {
    if (categories.isEmpty()) {
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            CategoryShortcut("Categories", Icons.Default.GridView, ShopGreenSoft, ShopGreen) {
                categories.firstOrNull()?.let(onSelectFilter)
            }
        }
        items(categories, key = { it.id }) { category ->
            CategoryShortcut(category.name, categoryIcon(category), categoryTone(category), ShopInk) { onCategory(category) }
        }
        item {
            CategoryShortcut("More", Icons.Default.MoreHoriz, ShopPurpleSoft, ShopInk) {
                categories.firstOrNull()?.let(onCategory)
            }
        }
    }
}

@Composable
private fun CategoryRail(categories: List<ShopCategoryDto>, onCategory: (ShopCategoryDto) -> Unit) {
    if (categories.isEmpty()) return
    SectionHeader("Popular categories")
    CategoryShortcutRow(categories, onCategory, onSelectFilter = onCategory)
}

@Composable
private fun TopCategoryRail(categories: List<ShopCategoryDto>, onCategory: (ShopCategoryDto) -> Unit) {
    if (categories.isEmpty()) return
    SectionHeader("Top categories")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(categories, key = { it.id }) { category ->
            CategoryShortcut(category.name, categoryIcon(category), categoryTone(category), ShopInk) { onCategory(category) }
        }
    }
}

@Composable
private fun CategoryShortcut(label: String, icon: ImageVector, background: Color, tint: Color, onClick: () -> Unit) {
    Column(Modifier.width(82.dp).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = ShopInk, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun categoryIcon(category: ShopCategoryDto): ImageVector {
    val key = "${category.name} ${category.slug}".lowercase()
    return when {
        "elect" in key || "phone" in key || "laptop" in key -> Icons.Default.Devices
        "fashion" in key || "cloth" in key || "shirt" in key -> Icons.Default.Checkroom
        "beauty" in key || "skin" in key || "perfume" in key -> Icons.Default.Spa
        "home" in key || "kitchen" in key || "furniture" in key -> Icons.Default.Chair
        "grocery" in key || "food" in key -> Icons.Default.LocalGroceryStore
        else -> Icons.Default.Storefront
    }
}

private fun categoryTone(category: ShopCategoryDto): Color {
    val key = category.name.lowercase()
    return when {
        "beauty" in key -> ShopPinkSoft
        "home" in key -> ShopAmberSoft
        "fashion" in key -> ShopPurpleSoft
        "elect" in key -> ShopBlueSoft
        else -> ShopGreenSoft
    }
}

@Composable
private fun ProductRail(
    title: String,
    products: List<ShopProductDto>,
    onProduct: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
    savedIds: Set<String>,
    compact: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(products, key = { it.id }) { product ->
                ProductCard(product, Modifier.width(if (compact) 144.dp else 164.dp), onProduct, onToggleFavorite, product.id in savedIds, compact = compact)
            }
        }
    }
}

@Composable
private fun ProductGridSection(
    title: String,
    products: List<ShopProductDto>,
    onProduct: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
    savedIds: Set<String>,
    maxItems: Int = 10,
) {
    if (products.isEmpty()) {
        return
    }
    val visibleProducts = products.take(maxItems)
    val rowCount = (visibleProducts.size + 1) / 2
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val horizontalPadding = 12.dp
    val columnSpacing = 6.dp
    val rowSpacing = 8.dp
    val cardWidth = (screenWidth - horizontalPadding - columnSpacing) / 2
    val rowHeight = maxOf(292.dp, cardWidth + 112.dp)
    val gridHeight = (rowHeight * rowCount) + (rowSpacing * (rowCount - 1).coerceAtLeast(0))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(gridHeight),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(columnSpacing),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            items(visibleProducts, key = { it.id }) { product ->
                ProductCard(product, Modifier.fillMaxWidth(), onProduct, onToggleFavorite, product.id in savedIds)
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: ShopProductDto,
    modifier: Modifier,
    onProduct: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
    saved: Boolean,
    compact: Boolean = false,
) {
    Card(
        modifier = modifier.clickable { onProduct(product) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ShopSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine.copy(alpha = .82f)),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).clip(RoundedCornerShape(12.dp)).background(ShopSoft)) {
                val imageUrl = product.images
                    ?.sortedWith(
                        compareByDescending<ShopProductImageDtoSafe> { it.isPrimary == true }
                            .thenBy { it.displayOrder ?: 0 }
                    )
                    .orEmpty()
                    .firstOrNull()
                    ?.imageUrl
                ShopAssetImage(imageUrl, product.title, Modifier.fillMaxSize(), iconSize = 42.dp)
                if ((product.discountPercent ?: 0) > 0) {
                    Surface(Modifier.align(Alignment.TopStart).padding(6.dp), color = ShopRed, shape = RoundedCornerShape(999.dp)) {
                        Text("-${product.discountPercent}%", color = ShopOnColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                if (!product.stockStatus.isNullOrBlank()) {
                    Surface(Modifier.align(Alignment.BottomStart).padding(8.dp), color = stockStatusBg(product.stockStatus), shape = RoundedCornerShape(999.dp), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                        Text(product.stockStatus.replace("_", " "), color = stockStatusColor(product.stockStatus), fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 1)
                    }
                }
                IconButton(onClick = { onToggleFavorite(product) }, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(34.dp).clip(CircleShape).background(ShopOnColor.copy(alpha = .92f))) {
                    Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = if (saved) "Remove from favorites" else "Add to favorites", tint = if (saved) ShopRed else ShopInk, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.padding(start = 9.dp, end = 9.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(product.title, color = ShopInk, fontSize = if (compact) 11.sp else 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val hasReviews = (product.reviewCount ?: 0) > 0
                val hasSales = (product.soldCount ?: 0) > 0
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(16.dp)) {
                    if (hasReviews) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = ShopGold, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("${"%.1f".format(product.averageRating ?: 0.0)} (${product.reviewCount})", color = ShopMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    if (hasReviews && hasSales) Text("  •  ", color = ShopMuted, fontSize = 11.sp)
                    if (hasSales) Text("${product.soldCount} sold", color = ShopMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                Column(Modifier.fillMaxWidth().height(36.dp), verticalArrangement = Arrangement.Bottom) {
                    Text(formatEtb(product.priceMinor), color = ShopGreen, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if ((product.compareAtPriceMinor ?: 0) > 0) Text(formatEtb(product.compareAtPriceMinor ?: 0), color = ShopMuted, fontSize = 10.sp, textDecoration = TextDecoration.LineThrough)
                }
            }
        }
    }
}

@Composable
private fun MiniBadge(label: String, background: Color, content: Color) {
    Surface(color = background, shape = RoundedCornerShape(999.dp)) {
        Text(label, color = content, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

private fun statusKey(status: String?): String = status.orEmpty().trim().lowercase()

private fun backendLabel(value: String?): String = when (statusKey(value)) {
    "cash_on_delivery" -> "Cash on delivery"
    "awaiting_seller_confirmation" -> "Awaiting seller confirmation"
    "handed_to_carrier" -> "Shipped"
    "out_for_delivery" -> "Out for delivery"
    "unpaid" -> "Payment due on delivery"
    "payment_pending" -> "Payment pending"
    "ready_for_pickup" -> "Ready for pickup"
    "not_shipped" -> "Not shipped"
    "under_review" -> "Under review"
    "not_started" -> "Not started"
    "" -> ""
    else -> statusKey(value)
        .replace("_", " ")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun orderStatusLabel(status: String?): String = backendLabel(status).ifBlank { "Created" }

private fun fulfillmentStatusLabel(status: String?): String = backendLabel(status).ifBlank { "Awaiting seller confirmation" }

private fun paymentStatusLabel(status: String?): String = backendLabel(status).ifBlank { "Payment not recorded" }

private fun paymentMethodLabel(value: String?): String = backendLabel(value).ifBlank { "Payment method" }

private fun stockStatusBg(status: String?): Color = when {
    statusKey(status).contains("low") -> ShopAmberSoft
    statusKey(status).contains("out") -> ShopRedSoft
    else -> ShopGreenSoft
}

private fun stockStatusColor(status: String?): Color = when {
    statusKey(status).contains("low") -> ShopOrangeDark
    statusKey(status).contains("out") -> ShopRed
    else -> ShopGreen
}

private fun orderStatusBg(status: String?): Color = when (statusKey(status)) {
    "created", "pending", "payment_pending", "awaiting_seller_confirmation" -> ShopAmberSoft
    "confirmed", "accepted" -> ShopBlueSoft
    "processing" -> ShopGreenSoft
    "packed" -> ShopPurpleSoft
    "shipped", "handed_to_carrier", "out_for_delivery" -> ShopAmberSoft
    "delivered", "completed" -> ShopGreenSoft
    "cancelled", "canceled", "rejected" -> ShopRedSoft
    "refunded" -> ShopSoft
    else -> ShopSoft
}

private fun orderStatusColor(status: String?): Color = when (statusKey(status)) {
    "created", "pending", "payment_pending", "awaiting_seller_confirmation" -> ShopOrangeDark
    "confirmed", "accepted" -> ShopBlue
    "processing" -> ShopGreen
    "packed" -> ShopPurple
    "shipped", "handed_to_carrier", "out_for_delivery" -> ShopOrangeDark
    "delivered", "completed" -> ShopGreen
    "cancelled", "canceled", "rejected" -> ShopRed
    "refunded" -> ShopMuted
    else -> ShopMuted
}

private fun fulfillmentStatusBg(status: String?): Color = orderStatusBg(status)

private fun fulfillmentStatusColor(status: String?): Color = orderStatusColor(status)

private fun paymentStatusBg(status: String?): Color = when (statusKey(status)) {
    "paid", "settled", "released" -> ShopGreenSoft
    "unpaid", "pending", "payment_pending" -> ShopAmberSoft
    "failed", "cancelled", "canceled", "refunded" -> ShopRedSoft
    else -> ShopSoft
}

private fun paymentStatusColor(status: String?): Color = when (statusKey(status)) {
    "paid", "settled", "released" -> ShopGreen
    "unpaid", "pending", "payment_pending" -> ShopOrangeDark
    "failed", "cancelled", "canceled", "refunded" -> ShopRed
    else -> ShopMuted
}

private fun trackingStatusColor(status: String?): Color = when (statusKey(status)) {
    "created", "pending", "payment_pending", "awaiting_seller_confirmation" -> ShopOrange
    "confirmed", "accepted" -> ShopBlue
    "processing" -> ShopGreen
    "packed" -> ShopPurple
    "shipped", "handed_to_carrier", "out_for_delivery" -> ShopOrange
    "delivered", "completed" -> ShopGreen
    "cancelled", "canceled", "rejected", "failed" -> ShopRed
    else -> ShopMuted
}

@Composable
private fun ShopAssetImage(imageUrl: String?, contentDescription: String?, modifier: Modifier, iconSize: androidx.compose.ui.unit.Dp) {
    Box(modifier.background(ShopSoft), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.Storefront,
            contentDescription = null,
            tint = AdisGebeyaColors.Disabled,
            modifier = Modifier.size(iconSize),
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private typealias ShopProductImageDtoSafe = com.adischat.app.feature.shop.data.ShopProductImageDto

@Composable
private fun StoreRail(stores: List<ShopStoreDto>, onStore: (ShopStoreDto) -> Unit) {
    SectionHeader("Featured stores")
    if (stores.isEmpty()) {
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(stores, key = { it.id }) { store ->
            Card(Modifier.width(260.dp).clickable { onStore(store) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopOnColor), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(ShopGreenSoft), contentAlignment = Alignment.Center) {
                        ShopAssetImage(store.logoUrl, store.name, Modifier.fillMaxSize(), iconSize = 28.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(store.name, color = ShopInk, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${store.category ?: "Marketplace"} • ${store.city ?: "Addis Ababa"}", color = ShopMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if ((store.averageStoreRating ?: 0.0) > 0.0) Text("${"%.1f".format(store.averageStoreRating)} rating", color = ShopMuted, fontSize = 11.sp)
                            if ((store.totalSales ?: 0) > 0) Text("  ${store.totalSales} sales", color = ShopMuted, fontSize = 11.sp)
                        }
                    }
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = ShopMuted)
                }
            }
        }
    }
}

private fun ShopProductDto.toStoreDto(): ShopStoreDto {
    val current = store
    return ShopStoreDto(
        id = current?.id.orEmpty(),
        name = current?.name ?: "Verified store",
        slug = current?.slug.orEmpty(),
        description = current?.description,
        logoUrl = current?.logoUrl,
        coverUrl = current?.coverUrl,
        category = current?.category,
        city = current?.city,
        status = current?.status,
        approvalStatus = current?.approvalStatus,
        averageStoreRating = current?.averageStoreRating,
        totalSales = current?.totalSales,
        createdAt = current?.createdAt,
    )
}

private fun com.adischat.app.feature.shop.data.ShopOrderAddressSnapshotDto.formatAddress(): String =
    listOfNotNull(addressLine1, addressLine2, city)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(", ")

@Composable
private fun StoreDetailsScreen(
    store: ShopStoreDto,
    state: ShopHomeState,
    onBack: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onAddToCart: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
    onChatSeller: (ShopProductDto) -> Unit,
    savedIds: Set<String>,
    busyIds: Set<String>,
) {
    var query by remember(store.slug, store.id) { mutableStateOf("") }
    var selectedCategory by remember(store.slug, store.id) { mutableStateOf("All") }
    val storeProducts = remember(state.products, store) {
        state.products.filter { product ->
            val productStore = product.store
            productStore?.id == store.id && store.id.isNotBlank() ||
                productStore?.slug == store.slug && store.slug.isNotBlank() ||
                productStore?.name == store.name
        }
    }
    val categories = remember(storeProducts) {
        listOf("All") + storeProducts.mapNotNull { it.categorySlug?.replace("-", " ")?.replaceFirstChar { char -> char.uppercase() } }.distinct()
    }
    val filtered = remember(storeProducts, query, selectedCategory) {
        storeProducts.filter { product ->
            val matchesQuery = query.isBlank() || product.title.contains(query, ignoreCase = true) || product.brand.orEmpty().contains(query, ignoreCase = true)
            val matchesCategory = selectedCategory == "All" || product.categorySlug.orEmpty().replace("-", " ").equals(selectedCategory, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(ShopBg).statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            ShopHeader(cartCount = state.cartCount, onBack = onBack, onCart = {})
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(ShopSurface)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(ShopGreenSoft), contentAlignment = Alignment.Center) {
                        ShopAssetImage(store.coverUrl, store.name, Modifier.fillMaxSize(), iconSize = 64.dp)
                    }
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(ShopSoft), contentAlignment = Alignment.Center) {
                            ShopAssetImage(store.logoUrl, store.name, Modifier.fillMaxSize(), iconSize = 34.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(store.name, color = ShopInk, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Verified, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(18.dp))
                            }
                            Text("${store.category ?: "Marketplace"} • ${store.city ?: "Addis Ababa"}", color = ShopMuted, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                    if (!store.description.isNullOrBlank()) {
                        Text(store.description, color = ShopMuted, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat("Products", storeProducts.size.toString())
                        MiniStat("Rating", "%.1f".format(store.averageStoreRating ?: 0.0))
                        MiniStat("Sales", (store.totalSales ?: 0).toString())
                    }
                    store.createdAt?.let { Text("Joined $it", color = ShopMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search this store") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    FilterChipLike(category, selectedCategory == category) { selectedCategory = category }
                }
            }
        }
        item {
            SectionTitle("Products")
            if (filtered.isEmpty()) {
                EmptyCard("No products match this store filter.")
            } else {
                ProductGridSection("Available now", filtered, onProduct, onToggleFavorite, savedIds, maxItems = Int.MAX_VALUE)
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Policies")
                    Text("Orders use AdisGebeya checkout, protected seller fulfillment and customer delivery confirmation where available.", color = ShopMuted, lineHeight = 20.sp)
                    storeProducts.firstOrNull()?.let {
                        OutlinedButton(onClick = { onChatSeller(it) }, enabled = false, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                            Text("Chat unavailable", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductDetailsScreen(
    product: ShopProductDto,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onAddToCart: () -> Unit,
    onCart: () -> Unit,
    onToggleFavorite: () -> Unit,
    onBuyNow: () -> Unit,
    onChatSeller: () -> Unit,
    onVisitStore: () -> Unit,
    saved: Boolean,
    cartCount: Int,
) {
    val context = LocalContext.current
    val outOfStock = product.stockStatus?.contains("out", ignoreCase = true) == true
    val shareProduct = {
        val storeName = product.store?.name?.takeIf { it.isNotBlank() }
        val text = buildString {
            append("Check out ${product.title}")
            if (storeName != null) append(" from $storeName")
            append(" on AdisGebeya.")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share product"))
    }
    Scaffold(
        containerColor = ShopBg,
        topBar = {
            ProductDetailsTopBar(
                saved = saved,
                cartCount = cartCount,
                onBack = onBack,
                onSearch = onSearch,
                onShare = shareProduct,
                onToggleFavorite = onToggleFavorite,
                onCart = onCart,
            )
        },
        bottomBar = {
            StickyPurchaseFooter(
                outOfStock = outOfStock,
                onStore = onVisitStore,
                onChat = onChatSeller,
                onAddToCart = onAddToCart,
                onBuyNow = onBuyNow,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            Modifier.fillMaxSize().background(ShopBg).padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(.92f).clip(RoundedCornerShape(30.dp)).background(ShopSoft), contentAlignment = Alignment.Center) {
                    val images = product.images.orEmpty()
                    val imageUrl = images.firstOrNull { it.isPrimary == true }?.imageUrl ?: images.firstOrNull()?.imageUrl
                    ShopAssetImage(imageUrl, product.title, Modifier.fillMaxSize(), iconSize = 84.dp)
                    if (images.size > 1) {
                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            images.take(5).forEachIndexed { index, _ ->
                                Box(Modifier.size(width = if (index == 0) 18.dp else 7.dp, height = 7.dp).clip(RoundedCornerShape(999.dp)).background(if (index == 0) ShopGreen else ShopOnColor.copy(alpha = .86f)))
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(1.dp), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(product.title, color = ShopInk, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                        if ((product.reviewCount ?: 0) > 0 || (product.soldCount ?: 0) > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                            if ((product.reviewCount ?: 0) > 0) {
                                Text("${"%.1f".format(product.averageRating ?: 0.0)} rating", color = ShopInk, fontWeight = FontWeight.Medium)
                                Text("  •  ${product.reviewCount} reviews", color = ShopMuted, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            }
                            if ((product.soldCount ?: 0) > 0) Text("  •  ${product.soldCount} sold", color = ShopMuted, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(formatEtb(product.priceMinor), color = ShopGreen, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                            if ((product.compareAtPriceMinor ?: 0) > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text(formatEtb(product.compareAtPriceMinor ?: 0), color = ShopMuted, fontSize = 13.sp, textDecoration = TextDecoration.LineThrough)
                            }
                        }
                    }
                }
            }
            item { ProductInfoCard(product) }
            item { SellerSummary(product, onChatSeller, onVisitStore) }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Reviews")
                        if ((product.reviewCount ?: 0) > 0) {
                            Text("${"%.1f".format(product.averageRating ?: 0.0)} average rating from ${product.reviewCount} reviews", color = ShopMuted, fontWeight = FontWeight.Medium)
                        } else {
                            Text("No customer reviews yet.", color = ShopMuted, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductDetailsTopBar(
    saved: Boolean,
    cartCount: Int,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCart: () -> Unit,
) {
    Surface(
        color = ShopSurface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIcon(Icons.Default.ArrowBack, "Back", onClick = onBack)
            ProductSearchPill(Modifier.weight(1f), onSearch)
            HeaderIcon(Icons.Default.Share, "Share product", onClick = onShare)
            HeaderIcon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (saved) "Remove from favorites" else "Add to favorites", onClick = onToggleFavorite, tint = if (saved) ShopRed else ShopInk)
            BadgedBox(
                badge = {
                    if (cartCount > 0) Badge(containerColor = ShopRed, contentColor = ShopOnColor) {
                        Text(cartCount.coerceAtMost(99).toString(), fontSize = 9.sp)
                    }
                },
            ) {
                HeaderIcon(Icons.Default.ShoppingCart, "Cart", onClick = onCart)
            }
        }
    }
}

@Composable
private fun ProductSearchPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(ShopSoft)
            .border(1.dp, ShopLine, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = ShopMuted, modifier = Modifier.size(18.dp))
        Text("Search AdisGebeya", color = ShopMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StickyPurchaseFooter(
    outOfStock: Boolean,
    onStore: () -> Unit,
    onChat: () -> Unit,
    onAddToCart: () -> Unit,
    onBuyNow: () -> Unit,
) {
    Surface(
        color = ShopSurface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
    ) {
        Column {
            HorizontalDivider(color = ShopLine)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FooterIconAction(Icons.Default.Storefront, "Visit store", "Store", onStore)
                FooterIconAction(Icons.Default.Message, "Seller chat unavailable", "Chat", onChat, enabled = false)
                OutlinedButton(
                    onClick = onAddToCart,
                    enabled = !outOfStock,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (outOfStock) ShopMuted else ShopGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (outOfStock) ShopMuted else ShopGreen),
                ) {
                    Text(if (outOfStock) "Out" else "Add to Cart", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                }
                Button(
                    onClick = onBuyNow,
                    enabled = !outOfStock,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShopOrange, disabledContainerColor = ShopLine, disabledContentColor = ShopMuted),
                ) {
                    Text(if (outOfStock) "Unavailable" else "Buy Now", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FooterIconAction(icon: ImageVector, contentDescription: String, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val actionTint = if (enabled) ShopGreen else ShopMuted
    val actionModifier = if (enabled) Modifier.clickable { onClick() } else Modifier
    Column(
        Modifier
            .width(52.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ShopSoft)
            .border(1.dp, ShopLine, RoundedCornerShape(16.dp))
            .then(actionModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = actionTint, modifier = Modifier.size(19.dp))
        Text(label, color = actionTint, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun ProductInfoCard(product: ShopProductDto) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(ShopSurface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("About product")
            InfoRow("Stock", product.stockStatus?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Available")
            InfoRow("Brand", product.brand ?: "Marketplace seller")
            InfoRow("Protection", "Buyer Protection eligible")
        }
    }
}

@Composable
private fun SellerSummary(product: ShopProductDto, onChatSeller: () -> Unit, onVisitStore: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(ShopSurface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(ShopGreenSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = ShopGreen)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(product.store?.name ?: "Verified store", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("Approved marketplace store", color = ShopMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Icon(Icons.Default.Verified, contentDescription = null, tint = ShopGreen)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onChatSeller, enabled = false, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                    Text("Chat unavailable", fontWeight = FontWeight.SemiBold)
                }
                Button(onClick = onVisitStore, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) {
                    Text("Visit Store", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun RowScope.MiniStat(label: String, value: String) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(ShopSoft).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(label, color = ShopMuted, fontSize = 11.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ShopMuted, fontWeight = FontWeight.Medium)
        Text(value, color = ShopInk, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CartScreen(
    protectedState: ShopProtectedState,
    cartCount: Int,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onUpdate: (String, Int) -> Unit,
    onClear: () -> Unit,
    onCheckout: () -> Unit,
) {
    ProtectedPage("Cart", cartCount, onBack, onLoad, protectedState, showHeader = false) {
        val cart = protectedState.cart
        if (cart == null || cart.items.isEmpty()) {
            EmptyCard("Your cart is empty.")
        } else {
            CartSummary(cart, protectedState.busyIds, onUpdate, onClear, onCheckout)
        }
    }
}

@Composable
private fun CartSummary(cart: ShopCartDto, busyIds: Set<String>, onUpdate: (String, Int) -> Unit, onClear: () -> Unit, onCheckout: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cart.items.forEach { item ->
            CartItemRow(item, item.id in busyIds, onUpdate)
        }
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", color = ShopMuted, fontWeight = FontWeight.SemiBold)
                    Text(formatEtb(cart.totals.subtotalMinor), color = ShopInk, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Delivery estimate", color = ShopMuted, fontWeight = FontWeight.SemiBold)
                    Text(if (cart.totals.deliveryEstimateMinor > 0) formatEtb(cart.totals.deliveryEstimateMinor) else "Shown at checkout", color = ShopInk, fontWeight = FontWeight.Medium)
                }
                HorizontalDivider(color = ShopLine)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Estimated total", color = ShopInk, fontWeight = FontWeight.SemiBold)
                    Text(formatEtb(cart.totals.totalMinor.takeIf { it > 0 } ?: cart.totals.subtotalMinor), color = ShopGreen, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopOrange)) {
                    Text("Start secure checkout", fontWeight = FontWeight.SemiBold)
                }
                Text("Final totals and stock are verified by AdisGebeya before an order is created.", color = ShopMuted, fontSize = 12.sp, lineHeight = 17.sp)
                Button(onClick = onClear, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopSoft, contentColor = ShopInk)) {
                    Text("Clear cart", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CartItemRow(item: ShopCartItemDto, busy: Boolean, onUpdate: (String, Int) -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(74.dp).clip(RoundedCornerShape(18.dp)).background(ShopSoft)) {
                val imageUrl = item.product?.images?.firstOrNull { it.isPrimary == true }?.imageUrl ?: item.product?.images?.firstOrNull()?.imageUrl
                ShopAssetImage(imageUrl, item.product?.title, Modifier.fillMaxSize(), iconSize = 30.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.product?.title ?: "AdisGebeya product", color = ShopInk, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatEtb(item.unitPriceMinor), color = ShopGreen, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (!busy) onUpdate(item.id, item.quantity - 1) }) { Icon(Icons.Default.Remove, contentDescription = "Decrease") }
                    Text(item.quantity.toString(), color = ShopInk, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { if (!busy) onUpdate(item.id, item.quantity + 1) }) { Icon(Icons.Default.Add, contentDescription = "Increase") }
                    IconButton(onClick = { if (!busy) onUpdate(item.id, 0) }) { Icon(Icons.Default.Delete, contentDescription = "Remove", tint = ShopRed) }
                }
            }
            if (busy) CircularProgressIndicator(color = ShopGreen, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun CheckoutScreen(
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onLoadAddresses: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var selectedAddressId by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf("") }
    val checkout = protectedState.checkout
    LaunchedEffect(Unit) { onLoadAddresses() }
    LaunchedEffect(protectedState.addresses, checkout) {
        if (selectedAddressId.isBlank()) selectedAddressId = protectedState.addresses.firstOrNull { it.isDefault }?.id ?: protectedState.addresses.firstOrNull()?.id.orEmpty()
        if (selectedPaymentMethod.isBlank()) selectedPaymentMethod = checkout?.paymentMethods?.firstOrNull()?.code.orEmpty()
    }

    LazyColumn(
        Modifier.fillMaxSize().background(ShopBg).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageTitle("Checkout", "Server-verified totals, stock and payment availability") }
        if (protectedState.loading && checkout == null) {
            item { SkeletonCard() }
        }
        if (checkout == null) {
            protectedState.error?.let { item { ErrorCardMessage(it) } }
        }
        if (checkout != null) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Delivery address", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        if (protectedState.addresses.isEmpty()) {
                            Text("Add a delivery address before confirming checkout.", color = ShopMuted, lineHeight = 20.sp)
                        } else {
                            protectedState.addresses.forEach { address ->
                                val selected = selectedAddressId == address.id
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (selected) ShopGreenSoft else ShopSoft)
                                        .clickable { selectedAddressId = address.id }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(address.label.ifBlank { "Address" }, color = ShopInk, fontWeight = FontWeight.Medium)
                                        Text("${address.recipientName} • ${address.phone}", color = ShopMuted, fontSize = 12.sp)
                                        Text("${address.addressLine1}, ${address.city}", color = ShopMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ShopGreen)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Authoritative summary", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        InfoRow("Subtotal", formatEtb(checkout.subtotalMinor))
                        InfoRow("Discount", formatEtb(checkout.discountMinor))
                        InfoRow("Shipping", if (checkout.shippingMinor > 0) formatEtb(checkout.shippingMinor) else "Not configured")
                        InfoRow("Tax", formatEtb(checkout.taxMinor))
                        HorizontalDivider(color = ShopLine)
                        InfoRow("Grand total", formatEtb(checkout.grandTotalMinor))
                        checkout.expiresAt?.let { Text("Reservation expires: $it", color = ShopMuted, fontSize = 12.sp) }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Payment method", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        if (checkout.paymentMethods.isEmpty()) {
                            ErrorCardMessage("No payment method is enabled yet. Online payments are not configured and Cash on Delivery is disabled by platform rules.")
                        } else {
                            checkout.paymentMethods.forEach { method ->
                                val selected = selectedPaymentMethod == method.code
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (selected) ShopGreenSoft else ShopSoft)
                                        .clickable { selectedPaymentMethod = method.code }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(method.label, color = ShopInk, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ShopGreen)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { onConfirm(checkout.id, selectedAddressId, selectedPaymentMethod) },
                    enabled = selectedAddressId.isNotBlank() && selectedPaymentMethod.isNotBlank() && !protectedState.loading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShopGreen, disabledContainerColor = ShopLine, disabledContentColor = ShopMuted),
                ) {
                    Text(if (protectedState.loading) "Confirming..." else "Confirm order", fontWeight = FontWeight.SemiBold)
                }
            }
            protectedState.checkoutOrder?.let { order ->
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopGreenSoft)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Order created", color = ShopInk, fontWeight = FontWeight.SemiBold)
                            Text("Order ${order.id.takeLast(8)} is ${order.status.ifBlank { "payment pending" }}.", color = ShopMuted)
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopSoft, contentColor = ShopInk)) {
                Text("Back to cart", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ErrorCardMessage(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopRedSoft)) {
        Text(message, color = ShopRed, modifier = Modifier.padding(14.dp), lineHeight = 19.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SavedProductsScreen(
    protectedState: ShopProtectedState,
    cartCount: Int,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
    onAddToCart: (ShopProductDto) -> Unit,
) {
    ProtectedPage("Saved Products", cartCount, onBack, onLoad, protectedState) {
        val products = protectedState.favorites.mapNotNull { it.product }
        if (products.isEmpty()) EmptyCard("No saved products yet.")
        else ProductList(products, onProduct, onToggleFavorite, products.map { it.id }.toSet())
    }
}

@Composable
private fun RecentlyViewedScreen(
    protectedState: ShopProtectedState,
    cartCount: Int,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onAddToCart: (ShopProductDto) -> Unit,
) {
    ProtectedPage("Recently Viewed", cartCount, onBack, onLoad, protectedState) {
        val products = protectedState.recentlyViewed.mapNotNull { it.product }
        if (products.isEmpty()) EmptyCard("Products you view will appear here.")
        else ProductList(products, onProduct, {}, protectedState.favorites.map { it.productId }.toSet())
    }
}

@Composable
private fun ProductList(
    products: List<ShopProductDto>,
    onProduct: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
    savedIds: Set<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        products.forEach { product ->
            ProductCard(product, Modifier.fillMaxWidth(), onProduct, onToggleFavorite, product.id in savedIds)
        }
    }
}

@Composable
private fun AddressesScreen(
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onSave: (ShopAddressRequest) -> Unit,
    onDelete: (String) -> Unit,
    onDefault: (String) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    ProtectedPage("Delivery Addresses", 0, onBack, onLoad, protectedState) {
        Button(onClick = { adding = !adding }, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) {
            Text(if (adding) "Close form" else "Add address", fontWeight = FontWeight.Black)
        }
        if (adding) AddressForm(onSave)
        if (protectedState.addresses.isEmpty()) EmptyCard("No delivery addresses yet.")
        protectedState.addresses.forEach { address ->
            AddressCard(address, onDelete, onDefault)
        }
    }
}

@Composable
private fun AddressForm(onSave: (ShopAddressRequest) -> Unit) {
    var label by remember { mutableStateOf("Home") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Addis Ababa") }
    var line1 by remember { mutableStateOf("") }
    var line2 by remember { mutableStateOf("") }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopOnColor)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(label, { label = it }, label = { Text("Label") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(name, { name = it }, label = { Text("Recipient name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(city, { city = it }, label = { Text("City") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(line1, { line1 = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(line2, { line2 = it }, label = { Text("Landmark optional") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { onSave(ShopAddressRequest(label = label, recipientName = name, phone = phone, city = city, addressLine1 = line1, addressLine2 = line2.ifBlank { null }, isDefault = true)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShopGreen),
            ) { Text("Save address", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun AddressCard(address: ShopAddressDto, onDelete: (String) -> Unit, onDefault: (String) -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopOnColor)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(address.label.ifBlank { "Address" }, color = ShopInk, fontWeight = FontWeight.Black)
                if (address.isDefault) Text("Default", color = ShopGreen, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Text("${address.recipientName} • ${address.phone}", color = ShopMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${address.addressLine1}${address.addressLine2?.let { ", $it" } ?: ""}, ${address.city}", color = ShopInk, lineHeight = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDefault(address.id) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) { Text("Set default") }
                Button(onClick = { onDelete(address.id) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopRed)) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun OrdersScreen(
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onOpen: (String) -> Unit,
    onCancel: (String, String) -> Unit,
    onRevealCode: (String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf("All") }
    val tabs = listOf("All", "To Pay", "Processing", "Shipped", "Delivered", "Cancelled")
    val filtered = protectedState.orders.filter { order ->
        when (selectedTab) {
            "To Pay" -> order.paymentStatus in listOf("unpaid", "pending", "payment_pending")
            "Processing" -> order.fulfillmentStatus in listOf("awaiting_seller_confirmation", "accepted", "processing", "packed")
            "Shipped" -> order.status == "shipped" || order.fulfillmentStatus in listOf("handed_to_carrier", "shipped", "out_for_delivery")
            "Delivered" -> order.status in listOf("delivered", "completed") || order.fulfillmentStatus == "delivered"
            "Cancelled" -> order.status in listOf("cancelled", "rejected") || order.fulfillmentStatus in listOf("cancelled", "rejected")
            else -> true
        }
    }
    ProtectedPage("My AdisGebeya Orders", 0, onBack, onLoad, protectedState) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs) { tab ->
                FilterChipLike(tab, selectedTab == tab) { selectedTab = tab }
            }
        }
        if (filtered.isEmpty()) EmptyCard("No orders in this section yet.")
        filtered.forEach { order ->
            val visibleCode = protectedState.deliveryCode?.takeIf { it.orderId == order.id }
            OrderCard(
                order = order,
                deliveryCode = visibleCode,
                busy = protectedState.busyIds.contains("delivery-code-${order.id}"),
                onOpen = onOpen,
                onCancel = onCancel,
                onRevealCode = onRevealCode,
            )
        }
        protectedState.selectedOrder?.let { order ->
            OrderDetailCard(order, protectedState.deliveryCode?.takeIf { it.orderId == order.id })
        }
        if (protectedState.selectedOrder == null) {
            protectedState.deliveryCode?.let {
                ErrorCardMessage("Delivery code: ${it.deliveryCode}. Show it only after receiving the order.")
            }
        }
    }
}

@Composable
private fun OrderCard(
    order: ShopOrderDto,
    deliveryCode: com.adischat.app.feature.shop.data.ShopDeliveryCodeDto?,
    busy: Boolean,
    onOpen: (String) -> Unit,
    onCancel: (String, String) -> Unit,
    onRevealCode: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val canCancel = order.fulfillmentStatus in listOf("awaiting_seller_confirmation", "accepted", "created")
    val canRevealCode = order.fulfillmentStatus in listOf("handed_to_carrier", "shipped", "out_for_delivery")
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(order.orderNumber ?: "Order ${order.id.takeLast(8)}", color = ShopInk, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniBadge(orderStatusLabel(order.status), orderStatusBg(order.status), orderStatusColor(order.status))
                order.paymentStatus?.let { MiniBadge(paymentStatusLabel(it), paymentStatusBg(it), paymentStatusColor(it)) }
                order.fulfillmentStatus?.let { MiniBadge(fulfillmentStatusLabel(it), fulfillmentStatusBg(it), fulfillmentStatusColor(it)) }
            }
            if (order.paymentStatus in listOf("unpaid", "pending", "payment_pending")) {
                Text("Payment due on delivery", color = ShopMuted, fontSize = 13.sp)
            }
            order.createdAt?.let { Text(it, color = ShopMuted, fontSize = 12.sp) }
            Text(formatEtb(order.totalMinor), color = ShopGreen, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen(order.id) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) {
                    Text("Track")
                }
                Button(
                    onClick = { onRevealCode(order.id) },
                    enabled = canRevealCode && !busy,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShopInk),
                ) {
                    Text(if (busy) "Loading..." else "Delivery code")
                }
                if (canCancel) {
                    Button(onClick = { onCancel(order.id, "Customer requested cancellation") }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopRed)) {
                        Text("Cancel")
                    }
                }
            }
            deliveryCode?.takeIf { it.deliveryCode.isNotBlank() }?.let { code ->
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopGreenSoft), border = androidx.compose.foundation.BorderStroke(1.dp, ShopGreen.copy(alpha = .2f))) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Delivery code", color = ShopInk, fontWeight = FontWeight.SemiBold)
                            Text(code.deliveryCode, color = ShopGreen, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            Text(code.message ?: "Show it only after receiving the order.", color = ShopMuted, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(code.deliveryCode)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy delivery code", tint = ShopGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipLike(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) ShopGreen else ShopSurface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, ShopLine),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            color = if (selected) ShopOnColor else ShopInk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun OrderDetailCard(order: ShopOrderDto, deliveryCode: com.adischat.app.feature.shop.data.ShopDeliveryCodeDto?) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tracking timeline", color = ShopInk, fontWeight = FontWeight.Black, fontSize = 18.sp)
            if (order.history.orEmpty().isEmpty()) {
                Text("No tracking events yet.", color = ShopMuted)
            } else {
                order.history.orEmpty().sortedBy { it.occurredAt ?: it.createdAt ?: "" }.forEach { event ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(trackingStatusColor(event.status.ifBlank { event.nextStatus.orEmpty() })))
                        Column {
                            Text(event.publicMessage ?: event.nextStatus ?: event.status, color = ShopInk, fontWeight = FontWeight.SemiBold)
                            Text(event.occurredAt ?: event.createdAt.orEmpty(), color = ShopMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
            order.groups.orEmpty().forEach { group ->
                HorizontalDivider(color = ShopLine)
                Text(group.storeNameSnapshot ?: "Seller group", color = ShopInk, fontWeight = FontWeight.SemiBold)
                Text("Fulfilment: ${fulfillmentStatusLabel(group.fulfillmentStatus)}", color = ShopMuted)
                group.shipmentCarrier?.let { Text("Carrier: $it", color = ShopMuted) }
                group.shipmentTrackingNumber?.let { Text("Tracking: $it", color = ShopMuted) }
            }
            deliveryCode?.let {
                ErrorCardMessage("Delivery code: ${it.deliveryCode}. Show it only after receiving the order.")
            }
        }
    }
}

@Composable
private fun SellerCenterScreen(
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onSubmit: (String) -> Unit,
    onSellerAction: (String, String, String, String?, String?) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(SellerCenterTab.Dashboard) }
    var orderFilter by remember { mutableStateOf("New") }
    var storeName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { onLoad() }
    Scaffold(
        containerColor = ShopBg,
        bottomBar = {
            SellerCenterBottomBar(selectedTab = selectedTab, onSelect = { selectedTab = it })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().background(ShopBg).statusBarsPadding().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SellerHeader(
                    protectedState = protectedState,
                    onBack = onBack,
                    onSearch = { selectedTab = SellerCenterTab.Products },
                    onMessages = { selectedTab = SellerCenterTab.Messages },
                )
            }
            if (protectedState.loading) {
                items(3) { SkeletonCard() }
            }
            protectedState.error?.let { message ->
                item { EmptyCard(message) }
            }
            when (selectedTab) {
                SellerCenterTab.Dashboard -> {
                    item {
                        SellerApplicationCard(
                            protectedState = protectedState,
                            storeName = storeName,
                            onStoreName = { storeName = it },
                            onSubmit = onSubmit,
                        )
                    }
                    item { SellerKpiGrid(protectedState.sellerOrderGroups) }
                    item {
                        SellerQuickActions(
                            onTab = { selectedTab = it },
                            onMore = { selectedTab = SellerCenterTab.More },
                        )
                    }
                    item { SellerRecentOrders(protectedState.sellerOrderGroups, onOpenOrders = { selectedTab = SellerCenterTab.Orders }) }
                }
                SellerCenterTab.Orders -> {
                    item { SellerOrdersPageHeader(orderFilter, onFilter = { orderFilter = it }) }
                    val filtered = protectedState.sellerOrderGroups.filter { sellerOrderMatchesTab(it, orderFilter) }
                    if (filtered.isEmpty()) {
                        item { EmptyCard("No ${orderFilter.lowercase()} seller orders right now.") }
                    } else {
                        items(filtered, key = { it.id }) { group ->
                            SellerOrderGroupCard(group, protectedState.busyIds.contains(group.id), onSellerAction)
                        }
                    }
                }
                SellerCenterTab.Products -> {
                    item {
                        SellerUnavailableModulePage(
                            title = "Products",
                            icon = Icons.Default.Inventory2,
                            body = "Seller product management needs a protected seller-products endpoint before Android can safely show, edit, archive or submit products for review.",
                            actions = listOf("Published", "Pending review", "Drafts", "Out of stock"),
                        )
                    }
                }
                SellerCenterTab.Messages -> {
                    item {
                        SellerUnavailableModulePage(
                            title = "Messages",
                            icon = Icons.Default.Message,
                            body = "Seller inbox APIs are not available in the current Android contract yet. Customer, order and product chats must remain hidden until backend ownership checks exist.",
                            actions = listOf("Customer chats", "Order chats", "Product chats", "Unread"),
                        )
                    }
                }
                SellerCenterTab.More -> {
                    item { SellerMorePage() }
                }
            }
        }
    }
}

private enum class SellerCenterTab(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.Analytics),
    Orders("Orders", Icons.Default.ReceiptLong),
    Products("Products", Icons.Default.Inventory2),
    Messages("Messages", Icons.Default.Message),
    More("More", Icons.Default.MoreHoriz),
}

@Composable
private fun SellerCenterBottomBar(selectedTab: SellerCenterTab, onSelect: (SellerCenterTab) -> Unit) {
    NavigationBar(
        containerColor = ShopSurface,
        tonalElevation = 8.dp,
        modifier = Modifier.navigationBarsPadding().border(1.dp, ShopLine.copy(alpha = .65f)),
    ) {
        SellerCenterTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(22.dp)) },
                label = { Text(tab.label, fontSize = 10.sp, fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Medium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ShopGreen,
                    selectedTextColor = ShopInk,
                    indicatorColor = ShopGreenSoft,
                    unselectedIconColor = ShopMuted,
                    unselectedTextColor = ShopMuted,
                ),
            )
        }
    }
}

@Composable
private fun SellerHeader(
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onMessages: () -> Unit,
) {
    val app = protectedState.sellerApplication
    val status = protectedState.sellerStatus.ifBlank { app?.status ?: "not_started" }
    val storeName = app?.proposedStoreName ?: protectedState.sellerOrderGroups.firstOrNull()?.storeNameSnapshot ?: "Seller Account"
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(ShopOnColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine),
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(108.dp)
                    .background(Brush.horizontalGradient(listOf(ShopGreen, ShopGreenSoft))),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(48.dp).clip(CircleShape).background(ShopOnColor.copy(alpha = .2f))) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ShopOnColor)
                }
                Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderIcon(Icons.Default.Search, "Search seller center", onClick = onSearch)
                    HeaderIcon(Icons.Default.Notifications, "Seller notifications", onClick = {})
                    HeaderIcon(Icons.Default.Message, "Seller messages", onClick = onMessages)
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(ShopGreenSoft).border(1.dp, ShopLine, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(storeName, color = ShopInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (status == "approved") {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Verified, contentDescription = "Verified", tint = ShopGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text("Joined ${app?.submittedAt ?: app?.createdAt ?: "after approval"}", color = ShopMuted, fontSize = 12.sp, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    MiniBadge(backendLabel(status), sellerStatusBg(status), sellerStatusColor(status))
                }
            }
        }
    }
}

private fun sellerStatusBg(status: String): Color = when (status) {
    "approved" -> ShopGreenSoft
    "rejected", "suspended" -> ShopRedSoft
    "submitted", "under_review", "pending" -> ShopAmberSoft
    else -> ShopSoft
}

private fun sellerStatusColor(status: String): Color = when (status) {
    "approved" -> ShopGreen
    "rejected", "suspended" -> ShopRed
    "submitted", "under_review", "pending" -> ShopOrangeDark
    else -> ShopMuted
}

@Composable
private fun SellerApplicationCard(
    protectedState: ShopProtectedState,
    storeName: String,
    onStoreName: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    val status = protectedState.sellerStatus
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopOnColor), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Seller status", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(backendLabel(status), color = sellerStatusColor(status), fontWeight = FontWeight.SemiBold)
            when (status) {
                "not_started", "draft" -> {
                    Text("Open your store with a real seller application. Approval remains handled by AdisChat review.", color = ShopMuted, lineHeight = 20.sp)
                    OutlinedTextField(storeName, onStoreName, label = { Text("Store name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { onSubmit(storeName) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) {
                        Text("Apply to open store", fontWeight = FontWeight.SemiBold)
                    }
                }
                "approved" -> Text("Your seller account is approved. Order actions below are protected by backend ownership checks.", color = ShopMuted, lineHeight = 20.sp)
                "rejected" -> Text("Your seller application was not approved. Contact support or resubmit when the backend enables edited applications.", color = ShopMuted, lineHeight = 20.sp)
                "suspended" -> Text("Seller actions are restricted while your store is suspended.", color = ShopMuted, lineHeight = 20.sp)
                else -> Text("Your application is under review. Store management unlocks only after approval.", color = ShopMuted, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun SellerKpiGrid(groups: List<ShopOrderGroupDto>) {
    val totalOrders = groups.size
    val pendingOrders = groups.count { (it.fulfillmentStatus ?: it.status) in listOf("awaiting_seller_confirmation", "created") }
    val processing = groups.count { (it.fulfillmentStatus ?: it.status) in listOf("accepted", "processing", "packed", "handed_to_carrier", "shipped", "out_for_delivery") }
    val completed = groups.count { (it.fulfillmentStatus ?: it.status) == "delivered" }
    val cancelled = groups.count { (it.fulfillmentStatus ?: it.status) in listOf("cancelled", "rejected") }
    val revenueMinor = groups.filter { (it.fulfillmentStatus ?: it.status) == "delivered" }.sumOf { it.order?.totalMinor ?: it.items.orEmpty().sumOf { item -> item.lineTotalMinor } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Seller summary", action = null)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(372.dp),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SellerKpiCard("Orders", totalOrders.toString(), Icons.Default.ReceiptLong, ShopGreenSoft, ShopGreen) }
            item { SellerKpiCard("New", pendingOrders.toString(), Icons.Default.Assignment, ShopAmberSoft, ShopOrangeDark) }
            item { SellerKpiCard("Processing", processing.toString(), Icons.Default.LocalShipping, ShopBlueSoft, ShopBlue) }
            item { SellerKpiCard("Completed", completed.toString(), Icons.Default.CheckCircle, ShopGreenSoft, ShopGreen) }
            item { SellerKpiCard("Cancelled", cancelled.toString(), Icons.Default.Delete, ShopRedSoft, ShopRed) }
            item { SellerKpiCard("Revenue", formatEtb(revenueMinor), Icons.Default.AccountBalanceWallet, ShopPurpleSoft, ShopInk) }
        }
    }
}

@Composable
private fun SellerKpiCard(title: String, value: String, icon: ImageVector, bg: Color, tint: Color) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopOnColor), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Text(value, color = ShopInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = ShopMuted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SellerQuickActions(onTab: (SellerCenterTab) -> Unit, onMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Quick actions", action = null)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.height(202.dp),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SellerShortcut("Add Product", Icons.Default.Add, ShopGreenSoft, ShopGreen) { onTab(SellerCenterTab.Products) } }
            item { SellerShortcut("Orders", Icons.Default.ReceiptLong, ShopBlueSoft, ShopBlue) { onTab(SellerCenterTab.Orders) } }
            item { SellerShortcut("Products", Icons.Default.Inventory2, ShopPurpleSoft, ShopInk) { onTab(SellerCenterTab.Products) } }
            item { SellerShortcut("Messages", Icons.Default.Message, ShopGreenSoft, ShopGreen) { onTab(SellerCenterTab.Messages) } }
            item { SellerShortcut("Analytics", Icons.Default.Analytics, ShopAmberSoft, ShopOrangeDark) { onMore() } }
            item { SellerShortcut("Marketing", Icons.Default.Campaign, ShopPinkSoft, ShopRed) { onMore() } }
            item { SellerShortcut("Finance", Icons.Default.AccountBalanceWallet, ShopPurpleSoft, ShopInk) { onMore() } }
            item { SellerShortcut("Settings", Icons.Default.Settings, ShopSoft, ShopMuted) { onMore() } }
        }
    }
}

@Composable
private fun SellerShortcut(label: String, icon: ImageVector, bg: Color, tint: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(ShopOnColor).border(1.dp, ShopLine, RoundedCornerShape(18.dp)).clickable { onClick() }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = ShopInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun SellerRecentOrders(groups: List<ShopOrderGroupDto>, onOpenOrders: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Recent orders", action = if (groups.isNotEmpty()) "View all" else null)
        if (groups.isEmpty()) {
            EmptyCard("No seller orders assigned to your approved store yet.")
        } else {
            groups.take(3).forEach { group ->
                SellerCompactOrderCard(group, onClick = onOpenOrders)
            }
        }
    }
}

@Composable
private fun SellerCompactOrderCard(group: ShopOrderGroupDto, onClick: () -> Unit) {
    val fulfillment = group.fulfillmentStatus.orEmpty().ifBlank { group.status }
    Card(Modifier.clickable { onClick() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopOnColor), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(ShopGreenSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = ShopGreen)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(group.order?.orderNumber ?: "Seller order", color = ShopInk, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("${group.items.orEmpty().sumOf { it.quantity }} items • ${formatEtb(group.order?.totalMinor ?: group.items.orEmpty().sumOf { it.lineTotalMinor })}", color = ShopMuted, fontSize = 12.sp)
            }
            MiniBadge(fulfillmentStatusLabel(fulfillment), fulfillmentStatusBg(fulfillment), fulfillmentStatusColor(fulfillment))
        }
    }
}

@Composable
private fun SellerOrdersPageHeader(selected: String, onFilter: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Orders", action = null)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("New", "Confirmed", "Processing", "Packed", "Shipped", "Completed", "Cancelled", "Returns"), key = { it }) { tab ->
                FilterPill(tab, selected == tab) { onFilter(tab) }
            }
        }
    }
}

private fun sellerOrderMatchesTab(group: ShopOrderGroupDto, tab: String): Boolean {
    val status = group.fulfillmentStatus.orEmpty().ifBlank { group.status }
    return when (tab) {
        "New" -> status in listOf("awaiting_seller_confirmation", "created")
        "Confirmed" -> status == "accepted"
        "Processing" -> status == "processing"
        "Packed" -> status == "packed"
        "Shipped" -> status in listOf("handed_to_carrier", "shipped", "out_for_delivery")
        "Completed" -> status == "delivered"
        "Cancelled" -> status in listOf("cancelled", "rejected")
        "Returns" -> status.contains("return", ignoreCase = true)
        else -> true
    }
}

@Composable
private fun SellerUnavailableModulePage(title: String, icon: ImageVector, body: String, actions: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(ShopOnColor), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(ShopGreenSoft), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(28.dp))
                }
                Text(title, color = ShopInk, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(body, color = ShopMuted, lineHeight = 20.sp)
            }
        }
        SectionHeader("Planned tools", action = null)
        actions.forEach { label ->
            SellerMoreRow(label, "Unavailable until the protected backend endpoint is enabled.", icon)
        }
    }
}

@Composable
private fun SellerMorePage() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("More", action = null)
        listOf(
            Triple("Store Profile", "Banner, logo, business hours and policies", Icons.Default.Storefront),
            Triple("Inventory", "Stock, reserved items and low-stock tools", Icons.Default.Inventory2),
            Triple("Marketing", "Coupons, campaigns and featured products", Icons.Default.Campaign),
            Triple("Finance", "Payouts are not configured yet.", Icons.Default.AccountBalanceWallet),
            Triple("Analytics", "Only real analytics will appear when backend metrics exist.", Icons.Default.Analytics),
            Triple("Customers", "Recent buyers without private account identifiers.", Icons.Default.People),
            Triple("Reviews", "Product and store reviews when review APIs are ready.", Icons.Default.Reviews),
            Triple("Returns", "Return requests and seller-safe actions.", Icons.Default.Assignment),
            Triple("Settings", "Seller notifications, shipping, security and help.", Icons.Default.Settings),
        ).forEach { row ->
            SellerMoreRow(row.first, row.second, row.third)
        }
    }
}

@Composable
private fun SellerMoreRow(title: String, subtitle: String, icon: ImageVector) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopOnColor), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(ShopGreenSoft), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = ShopInk, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = ShopMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = ShopMuted)
        }
    }
}

@Composable
private fun SellerOrderGroupCard(
    group: ShopOrderGroupDto,
    busy: Boolean,
    onSellerAction: (String, String, String, String?, String?) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var trackingNumber by remember(group.id) { mutableStateOf("") }
    var deliveryCode by remember(group.id) { mutableStateOf("") }
    val fulfillment = group.fulfillmentStatus.orEmpty().ifBlank { group.status }
    val addressText = group.deliveryAddress?.formatAddress().orEmpty()
    val phone = group.customer?.phone.orEmpty().ifBlank { group.deliveryAddress?.phone.orEmpty() }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopOnColor)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(group.storeNameSnapshot ?: "Seller order", color = ShopInk, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text("Order ${group.order?.orderNumber ?: "pending"}", color = ShopMuted)
                    group.order?.createdAt?.let { Text(it, color = ShopMuted, fontSize = 12.sp) }
                }
                if (fulfillment == "delivered") {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ShopGreen)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniBadge(fulfillmentStatusLabel(fulfillment), fulfillmentStatusBg(fulfillment), fulfillmentStatusColor(fulfillment))
                (group.paymentStatus ?: group.payment?.status)?.let { MiniBadge(paymentStatusLabel(it), paymentStatusBg(it), paymentStatusColor(it)) }
            }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopSoft)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Order summary", color = ShopInk, fontWeight = FontWeight.Black)
                    InfoRow("Payment", listOfNotNull(paymentMethodLabel(group.payment?.methodCode), paymentStatusLabel(group.payment?.status)).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Not recorded" })
                    InfoRow("Items", group.items.orEmpty().sumOf { it.quantity }.toString())
                    InfoRow("Total", formatEtb(group.order?.totalMinor ?: group.items.orEmpty().sumOf { it.lineTotalMinor }))
                    InfoRow("Delivery code", when {
                        group.deliveryCodeStatus?.verified == true -> "Verified"
                        group.deliveryCodeStatus?.revealed == true -> "Shown to customer"
                        fulfillment in listOf("handed_to_carrier", "shipped", "out_for_delivery") -> "Waiting for customer code"
                        else -> "Hidden until shipped"
                    })
                }
            }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopSoft)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Customer information", color = ShopInk, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(ShopGreenSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = ShopGreen)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(group.customer?.displayName ?: group.deliveryAddress?.recipientName ?: "Customer", color = ShopInk, fontWeight = FontWeight.SemiBold)
                            if (phone.isNotBlank()) Text(phone, color = ShopMuted, fontSize = 13.sp)
                        }
                    }
                    if (addressText.isNotBlank()) Text(addressText, color = ShopMuted, lineHeight = 19.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (phone.isNotBlank()) SellerActionButton("Call", busy = false) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
                        }
                        if (phone.isNotBlank()) SellerActionButton("Copy Phone", busy = false) {
                            clipboard.setText(AnnotatedString(phone))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (addressText.isNotBlank()) SellerActionButton("Copy Address", busy = false) {
                            clipboard.setText(AnnotatedString(addressText))
                        }
                        if (addressText.isNotBlank()) SellerActionButton("Navigate", busy = false) {
                            val encoded = URLEncoder.encode(addressText, StandardCharsets.UTF_8.toString())
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))) }
                        }
                    }
                }
            }
            Text("Ordered items", color = ShopInk, fontWeight = FontWeight.Black)
            group.items.orEmpty().forEach { item ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ShopSoft).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(ShopOnColor), contentAlignment = Alignment.Center) {
                        ShopAssetImage(item.imageSnapshot, item.titleSnapshot, Modifier.fillMaxSize(), iconSize = 26.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.titleSnapshot, color = ShopInk, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${item.quantity} x ${formatEtb(item.unitPriceMinor)}", color = ShopMuted, fontSize = 12.sp)
                    }
                    Text(formatEtb(item.lineTotalMinor), color = ShopGreen, fontWeight = FontWeight.Black)
                }
            }
            if (group.history.orEmpty().isNotEmpty()) {
                Text("Timeline", color = ShopInk, fontWeight = FontWeight.Black)
                group.history.orEmpty().forEach { event ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(trackingStatusColor(event.status.ifBlank { event.nextStatus.orEmpty() })))
                        Column {
                            Text(event.publicMessage ?: event.nextStatus ?: event.status, color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(event.occurredAt ?: event.createdAt.orEmpty(), color = ShopMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
            if (fulfillment == "packed") {
                OutlinedTextField(
                    value = trackingNumber,
                    onValueChange = { trackingNumber = it },
                    label = { Text("Tracking number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            if (fulfillment in listOf("handed_to_carrier", "shipped", "out_for_delivery")) {
                OutlinedTextField(
                    value = deliveryCode,
                    onValueChange = { value -> deliveryCode = value.filter { it.isDigit() }.take(6) },
                    label = { Text("Customer delivery code") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            when (fulfillment) {
                "awaiting_seller_confirmation", "created" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SellerActionButton("Accept", busy) { onSellerAction(group.id, "accept", "", null, null) }
                    SellerActionButton("Reject", busy) { onSellerAction(group.id, "reject", "Seller rejected order", null, null) }
                }
                "accepted" -> SellerActionButton("Processing", busy) { onSellerAction(group.id, "processing", "", null, null) }
                "processing" -> SellerActionButton("Packed", busy) { onSellerAction(group.id, "packed", "", null, null) }
                "packed" -> SellerActionButton("Ship", busy) { onSellerAction(group.id, "handed_to_carrier", "", "Seller delivery", trackingNumber.ifBlank { null }) }
                "handed_to_carrier", "shipped", "out_for_delivery" -> SellerActionButton("Delivered", busy || deliveryCode.length != 6) {
                    onSellerAction(group.id, "delivered", "", null, deliveryCode)
                }
                "delivered" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ShopGreen)
                    Text("Done", color = ShopGreen, fontWeight = FontWeight.Black)
                }
                "cancelled", "rejected" -> Text(fulfillment.replaceFirstChar { it.uppercase() }, color = ShopMuted, fontWeight = FontWeight.SemiBold)
                else -> EmptyCard("No seller action is available for this order state.")
            }
        }
    }
}

@Composable
private fun SellerActionButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ShopGreen),
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun BuyerProtectionPage(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(ShopBg).statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ShopHeader(cartCount = 0, onBack = onBack, onCart = {})
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(ShopOnColor)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Buyer Protection", color = ShopInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                listOf(
                    "Secure order handling through verified AdisChat accounts.",
                    "Seller accountability through marketplace review workflows.",
                    "Delivery confirmation support where an order provides tracking.",
                    "Dispute support for eligible completed AdisGebeya purchases.",
                    "Refund eligibility depends on order status, seller policy and support review.",
                ).forEach { Text(it, color = ShopMuted, lineHeight = 20.sp) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ProtectedPage(
    title: String,
    cartCount: Int,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    protectedState: ShopProtectedState,
    showHeader: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    LaunchedEffect(title) { onLoad() }
    LazyColumn(Modifier.fillMaxSize().background(ShopBg).statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showHeader) {
            stickyHeader { ShopHeader(cartCount = cartCount, onBack = onBack, onCart = {}) }
        }
        item { PageTitle(title, "Manage your AdisGebeya activity") }
        if (protectedState.loading) {
            item { SkeletonCard() }
        }
        protectedState.error?.let { message ->
            item { EmptyCard(message) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun PlaceholderShopPage(title: String, message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(ShopBg).statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ShopHeader(cartCount = 0, onBack = onBack, onCart = {})
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(ShopSurface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = ShopInk, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(message, color = ShopMuted, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = ShopInk, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = ShopMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = "See all") {
    Surface(
        modifier = Modifier.fillMaxWidth().zIndex(1f),
        color = ShopBg,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = ShopInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (!action.isNullOrBlank()) {
                Text(action, color = ShopInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = ShopInk, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ShopInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(ShopLine.copy(alpha = .75f)))
    }
}

@Composable
private fun SkeletonCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ShopSurface)
            .border(1.dp, ShopLine.copy(alpha = .72f), RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp)).background(ShopSoft))
        Box(Modifier.fillMaxWidth(.72f).height(13.dp).clip(RoundedCornerShape(999.dp)).background(ShopLine.copy(alpha = .82f)))
        Box(Modifier.fillMaxWidth(.46f).height(13.dp).clip(RoundedCornerShape(999.dp)).background(ShopLine.copy(alpha = .58f)))
    }
}

@Composable
private fun ErrorCard(maintenance: Boolean, onRefresh: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (maintenance) "AdisGebeya is temporarily unavailable." else "We couldn't load AdisGebeya.",
                color = ShopInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Check your connection and try again.", color = ShopMuted)
            Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = ShopGreen), shape = RoundedCornerShape(14.dp)) {
                Text("Retry", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(1.dp), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine.copy(alpha = .72f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(ShopGreenSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = ShopGreen)
            }
            Text(message, color = ShopMuted, fontWeight = FontWeight.Medium, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun BuyerProtectionBanner() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ShopGreenDark, ShopGreen)))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Your purchases are protected", color = ShopOnColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("AdisChat keeps marketplace payments secure and releases seller settlement only after delivery confirmation.", color = ShopOnColor.copy(alpha = .78f), lineHeight = 20.sp)
    }
}

private fun formatEtb(minor: Int): String = "ETB ${"%,d".format(minor / 100)}"
