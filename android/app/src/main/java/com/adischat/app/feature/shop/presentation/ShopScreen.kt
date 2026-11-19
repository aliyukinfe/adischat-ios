package com.adischat.app.feature.shop.presentation

import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import com.adischat.app.feature.shop.data.ShopAdminOrderDetailDto
import com.adischat.app.feature.shop.data.ShopAdminOrderDto
import com.adischat.app.feature.shop.data.ShopAdminProductDetailDto
import com.adischat.app.feature.shop.data.ShopAdminProductDto
import com.adischat.app.feature.shop.data.ShopAdminProductReviewDto
import com.adischat.app.feature.shop.data.ShopAdminReviewModerationDto
import com.adischat.app.feature.shop.data.ShopAdminSellerApplicationDto
import com.adischat.app.feature.shop.data.ShopAdminStoreDetailDto
import com.adischat.app.feature.shop.data.ShopAdminStoreDto
import com.adischat.app.feature.shop.data.ShopCartDto
import com.adischat.app.feature.shop.data.ShopCartItemDto
import com.adischat.app.feature.shop.data.ShopFavoriteDto
import com.adischat.app.feature.shop.data.ShopHomeState
import com.adischat.app.feature.shop.data.ShopIssueOptionsDto
import com.adischat.app.feature.shop.data.ShopExchangeRequestDto
import com.adischat.app.feature.shop.data.ShopOrderIssueDto
import com.adischat.app.feature.shop.data.ShopOrderGroupDto
import com.adischat.app.feature.shop.data.ShopOrderDto
import com.adischat.app.feature.shop.data.ShopPostDeliveryActionsDto
import com.adischat.app.feature.shop.data.ShopProductImageDto
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopProtectedState
import com.adischat.app.feature.shop.data.ShopRecentlyViewedDto
import com.adischat.app.feature.shop.data.ShopReceiptDto
import com.adischat.app.feature.shop.data.ShopRefundRequestDto
import com.adischat.app.feature.shop.data.ShopReviewDto
import com.adischat.app.feature.shop.data.ShopSellerApplicationRequest
import com.adischat.app.feature.shop.data.ShopSellerRequestsDto
import com.adischat.app.feature.shop.data.ShopSellerProductDraftPayload
import com.adischat.app.feature.shop.data.ShopSellerProductDto
import com.adischat.app.feature.shop.data.ShopStoreDto
import com.adischat.app.ui.theme.AdisGebeyaColors
import com.adischat.app.ui.theme.AdisGebeyaHomeV2Colors
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

internal val ShopBg = AdisGebeyaColors.Background
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

@Composable
internal fun shopOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ShopInk,
    unfocusedTextColor = ShopInk,
    disabledTextColor = ShopMuted,
    focusedContainerColor = ShopSurface,
    unfocusedContainerColor = ShopSurface,
    disabledContainerColor = ShopSoft,
    cursorColor = ShopGreen,
    focusedBorderColor = ShopGreen,
    unfocusedBorderColor = ShopBorder,
    disabledBorderColor = ShopLine,
    focusedLabelColor = ShopGreen,
    unfocusedLabelColor = ShopMuted,
    disabledLabelColor = ShopMuted,
    focusedPlaceholderColor = ShopMuted,
    unfocusedPlaceholderColor = ShopMuted,
)

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

private val AdisGebeyaTypography = androidx.compose.material3.Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal),
        labelLarge = base.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    )
}

internal data class ShopHomeFilters(
    val categorySlug: String? = null,
    val categoryValues: Map<String, String> = emptyMap(),
    val inStockOnly: Boolean = false,
    val discountedOnly: Boolean = false,
    val minimumPriceEtb: String = "",
    val maximumPriceEtb: String = "",
    val minimumRating: Int = 0,
    val sort: String = "Newest",
)

internal fun ShopHomeFilters.activeLabels(categories: List<ShopCategoryDto>): List<String> = buildList {
    categorySlug?.let { slug ->
        categories.firstOrNull { it.slug == slug }?.name?.let(::add)
    }
    addAll(categoryValues.values)
    if (inStockOnly) add("In stock")
    if (discountedOnly) add("Discounted")
    minimumPriceEtb.toIntOrNull()?.let { add("From ETB $it") }
    maximumPriceEtb.toIntOrNull()?.let { add("Up to ETB $it") }
    if (minimumRating > 0) add("$minimumRating stars & above")
    if (sort != "Newest") add(sort)
}

internal fun marketplaceCategoryAndDescendantSlugs(
    categories: List<ShopCategoryDto>,
    selectedSlug: String?,
): Set<String> {
    if (selectedSlug == null) return emptySet()
    val fullCategories = CategoryHierarchyDefinition.buildAuthoritativeHierarchy(categories)
    val selected = fullCategories.firstOrNull { it.slug == selectedSlug }
        ?: categories.firstOrNull { it.slug == selectedSlug }
        ?: return setOf(selectedSlug)
    val descendantIds = CategoryHierarchyDefinition.resolveDescendantIds(selected.id, fullCategories)
    val result = linkedSetOf<String>()
    result.add(selected.slug)
    fullCategories.filter { it.id in descendantIds }.forEach { result.add(it.slug) }
    categories.filter { it.id in descendantIds }.forEach { result.add(it.slug) }
    return result
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
    data class AdminProductDetail(val productId: String) : ShopRoute()
    data class AdminOrderDetail(val orderId: String) : ShopRoute()
    data class AdminStoreDetail(val storeId: String) : ShopRoute()
    data object Checkout : ShopRoute()
    data object Notifications : ShopRoute()
    data object Search : ShopRoute()
    data class HomeBrowse(val section: ShopHomeBrowseSection) : ShopRoute()
    data object Conversation : ShopRoute()
    data class MessageConversation(val conversationId: String) : ShopRoute()
    data class BuyerOrderDetail(val orderId: String, val orderGroupId: String? = null) : ShopRoute()
    data class OrderTracking(val orderId: String) : ShopRoute()
    data class Product(val product: ShopProductDto, val visitId: Long = 0L) : ShopRoute()
    data class Category(val category: ShopCategoryDto) : ShopRoute()
    data class Store(val store: ShopStoreDto) : ShopRoute()
    data class VisualSearch(val entryMode: MarketplaceVisualSearchEntryMode) : ShopRoute()
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
    marketplaceLanguage: String = MARKETPLACE_LANGUAGE_ENGLISH,
    onMarketplaceLanguageChange: (String) -> Unit = {},
    accountIdentity: AdisChatAccountIdentity = AdisChatAccountIdentity(),
    onOpenAdisChatProfile: () -> Unit = onBackToHome,
) {
    val shopView = LocalView.current
    DisposableEffect(shopView) {
        val activity = generateSequence<Context>(shopView.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, shopView) }
        val originalLightStatusBars = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (originalLightStatusBars != null) controller?.isAppearanceLightStatusBars = originalLightStatusBars }
    }
    val vm: ShopViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val protectedState by vm.protectedState.collectAsStateWithLifecycle()
    var selectedTab by remember(initialRoute) { mutableStateOf(initialRoute.rootTab()) }
    var detailRoute by remember(initialRoute) {
        mutableStateOf<ShopRoute?>(
            when (initialRoute) {
                is ShopRoute.Product, is ShopRoute.Category, is ShopRoute.Store, is ShopRoute.VisualSearch, is ShopRoute.HomeBrowse, ShopRoute.Support, ShopRoute.Admin, is ShopRoute.AdminProductDetail, is ShopRoute.AdminOrderDetail, is ShopRoute.AdminStoreDetail, ShopRoute.Checkout, ShopRoute.Notifications, ShopRoute.Search, is ShopRoute.MessageConversation, is ShopRoute.BuyerOrderDetail, is ShopRoute.OrderTracking -> initialRoute
                else -> null
            }
        )
    }
    val detailHistory = remember(initialRoute) { mutableStateListOf<ShopRoute>() }
    var nextProductVisitId by remember(initialRoute) { mutableStateOf(0L) }
    var accountSubpage by remember(initialRoute) { mutableStateOf(initialRoute.accountSubpage()) }
    val snackbar = remember { SnackbarHostState() }
    val accountActions = remember(vm) { shopAccountActions(vm) }
    val visualSearchSession = vm.visualSearchSession

    LaunchedEffect(Unit) { vm.loadNotificationUnreadCount() }

    fun navigateDetail(next: ShopRoute?) {
        if (next == null) {
            val leavingVisualSearch = detailRoute is ShopRoute.VisualSearch
            detailRoute = if (detailHistory.isNotEmpty()) detailHistory.removeAt(detailHistory.lastIndex) else null
            if (leavingVisualSearch) visualSearchSession.reset()
            return
        }
        if (next is ShopRoute.Product && detailRoute is ShopRoute.Product &&
            (detailRoute as ShopRoute.Product).product.id == next.product.id) return
        val destination = if (next is ShopRoute.Product) next.copy(visitId = ++nextProductVisitId) else next
        detailRoute?.takeIf { it != destination }?.let(detailHistory::add)
        detailRoute = destination
    }

    BackHandler {
        when {
            detailRoute != null -> navigateDetail(null)
            accountSubpage != null -> accountSubpage = null
            else -> onBackToHome()
        }
    }

    AdisGebeyaPageTheme {
        ShopRootScaffold(
            state = state,
            protectedState = protectedState,
            selectedTab = selectedTab,
            detailRoute = detailRoute,
            accountSubpage = accountSubpage,
            accountActions = accountActions,
            accountIdentity = accountIdentity,
            vm = vm,
            visualSearchSession = visualSearchSession,
            marketplaceLanguage = marketplaceLanguage,
            onMarketplaceLanguageChange = onMarketplaceLanguageChange,
            onBackToHome = onBackToHome,
            onOpenAdisChatProfile = onOpenAdisChatProfile,
            onSelectTab = { tab ->
                selectedTab = tab
                detailRoute = null
                detailHistory.clear()
                if (tab != ShopRootTab.Account) accountSubpage = null
            },
            onDetailRoute = ::navigateDetail,
            onAccountSubpage = { accountSubpage = it },
            snackbar = snackbar,
        )
    }

    LaunchedEffect(selectedTab, detailRoute, accountSubpage) {
        when {
            selectedTab == ShopRootTab.Cart && detailRoute == null -> vm.loadCart()
            selectedTab == ShopRootTab.Account && accountSubpage == ShopAccountSubpage.Orders -> vm.loadOrders()
            selectedTab == ShopRootTab.Account && accountSubpage == ShopAccountSubpage.Saved -> vm.loadFavorites()
            selectedTab == ShopRootTab.Account && accountSubpage == ShopAccountSubpage.RecentlyViewed -> vm.loadRecentlyViewed()
            selectedTab == ShopRootTab.Account && accountSubpage == ShopAccountSubpage.Addresses -> vm.loadAddresses()
            selectedTab == ShopRootTab.Account && accountSubpage == null -> {
                vm.loadOrders()
                vm.loadFavorites()
                vm.loadAdminSession()
            }
            detailRoute is ShopRoute.Product -> vm.recordProductView((detailRoute as ShopRoute.Product).product)
            detailRoute is ShopRoute.MessageConversation -> vm.loadConversationMessages((detailRoute as ShopRoute.MessageConversation).conversationId)
            detailRoute == ShopRoute.Notifications -> vm.loadNotifications()
            detailRoute is ShopRoute.BuyerOrderDetail -> {
                val route = detailRoute as ShopRoute.BuyerOrderDetail
                if (route.orderId.isNotBlank()) vm.loadOrderDetail(route.orderId)
                else route.orderGroupId?.let(vm::loadOrderDetailForGroup)
            }
            detailRoute is ShopRoute.OrderTracking -> vm.loadOrderDetail((detailRoute as ShopRoute.OrderTracking).orderId)
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
        colorScheme = AdisGebeyaLightScheme,
        typography = AdisGebeyaTypography,
        content = content,
    )
}

@Composable
fun SellerAccountRootScreen(onBackToHome: () -> Unit, openNotificationsInitially: Boolean = false, initialStoreId: String? = null) {
    val vm: ShopViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val protectedState by vm.protectedState.collectAsStateWithLifecycle()
    LaunchedEffect(initialStoreId) {
        if (!initialStoreId.isNullOrBlank()) vm.loadSellerBusiness(initialStoreId)
    }
    AdisGebeyaPageTheme {
        SellerCenterScreen(
            protectedState = protectedState,
            openNotificationsInitially = openNotificationsInitially,
            categories = state.categories,
            sellingUnits = state.sellingUnits,
            onBack = onBackToHome,
            onLoad = vm::loadSellerStatus,
            onLoadSellerFeatures = {
                vm.loadSellerMessages()
                vm.loadSellerBusiness(initialStoreId)
                vm.loadSellerNotifications()
            },
            onLoadNotifications = vm::loadSellerNotifications,
            onMarkNotificationRead = vm::markSellerNotificationRead,
            onMarkAllNotificationsRead = vm::markAllSellerNotificationsRead,
            messageActions = remember(vm) {
                SellerMessageActions(
                    load = vm::loadSellerMessages,
                    open = vm::loadConversationMessages,
                    refresh = vm::refreshConversationMessages,
                    close = vm::closeConversation,
                    send = vm::sendSellerMessage,
                    upload = vm::sendSellerAttachment,
                    update = vm::updateSellerConversation,
                    typing = vm::updateConversationTyping,
                    openProduct = vm::editSellerProduct,
                )
            },
            businessActions = remember(vm) {
                SellerBusinessActions(
                    refresh = { storeId -> vm.loadSellerBusiness(storeId) },
                    loadRange = { storeId, start, end -> vm.loadSellerBusiness(storeId, start, end) },
                    updateStore = vm::updateSellerStore,
                    updateInventory = vm::updateSellerInventory,
                    updateInventoryBulk = vm::updateSellerInventoryBulk,
                    saveCoupon = vm::saveSellerCoupon,
                    saveCampaign = vm::saveSellerCampaign,
                    replyReview = vm::replySellerReview,
                    saveCustomerNote = vm::saveSellerCustomerNote,
                    openCustomerChat = vm::openSellerCustomerConversation,
                    saveSettings = vm::saveSellerSettings,
                    uploadStoreAsset = vm::uploadSellerStoreAsset,
                )
            },
            onSaveApplicationDraft = vm::saveSellerApplicationDraft,
            onSubmitCompleteApplication = vm::submitCompleteSellerApplication,
            onRespondApplicationInfoRequest = vm::respondSellerApplicationInfoRequest,
            onUploadApplicationDocument = vm::uploadSellerApplicationDocument,
            onRemoveApplicationDocument = vm::removeSellerApplicationDocument,
            onRespondSellerRequest = vm::respondSellerRequest,
            onSellerAction = vm::sellerOrderAction,
            onCreateProduct = vm::createSellerProductDraft,
            onEditProduct = vm::editSellerProduct,
            onCloseProductEditor = vm::clearSellerProductEditor,
            onSaveProduct = vm::saveSellerProduct,
            onSaveBeforeImagePick = vm::saveSellerProductBeforeImagePick,
            onSaveAndSubmitProduct = vm::saveAndSubmitSellerProduct,
            onLoadCategoryAttributes = vm::loadSellerCategoryAttributes,
            onSubmitProduct = vm::submitSellerProduct,
            onArchiveProduct = vm::archiveSellerProduct,
            onUploadProductMedia = vm::uploadSellerProductMediaBatch,
            onRetryProductMedia = vm::retrySellerProductMedia,
            onDeleteProductMedia = vm::deleteSellerProductMedia,
            onReorderProductMedia = vm::reorderSellerProductMedia,
        )
    }
}

@Composable
private fun ShopRootScaffold(
    state: ShopHomeState,
    protectedState: ShopProtectedState,
    selectedTab: ShopRootTab,
    detailRoute: ShopRoute?,
    accountSubpage: ShopAccountSubpage?,
    accountActions: ShopAccountActions,
    accountIdentity: AdisChatAccountIdentity,
    vm: ShopViewModel,
    visualSearchSession: MarketplaceVisualSearchSession,
    marketplaceLanguage: String,
    onMarketplaceLanguageChange: (String) -> Unit,
    onBackToHome: () -> Unit,
    onOpenAdisChatProfile: () -> Unit,
    onSelectTab: (ShopRootTab) -> Unit,
    onDetailRoute: (ShopRoute?) -> Unit,
    onAccountSubpage: (ShopAccountSubpage?) -> Unit,
    snackbar: SnackbarHostState,
) {
    val detailStateHolder = rememberSaveableStateHolder()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = ShopBg,
        contentWindowInsets = if (detailRoute is ShopRoute.Product) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        bottomBar = {
            if (detailRoute == null) {
                ShopBottomBar(
                    selectedTab = selectedTab,
                    cartCount = state.cartCount,
                    onSelect = onSelectTab,
                )
            }
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            if (detailRoute != null) {
                detailStateHolder.SaveableStateProvider(detailRoute.stateKey()) {
                    ShopDetailRouteContent(
                        route = detailRoute,
                        state = state,
                        protectedState = protectedState,
                        vm = vm,
                        visualSearchSession = visualSearchSession,
                        onRoute = onDetailRoute,
                        onSelectTab = onSelectTab,
                        onAccountSubpage = onAccountSubpage,
                    )
                }
            } else {
                ShopRootTabContent(
                    selectedTab = selectedTab,
                    state = state,
                    protectedState = protectedState,
                    accountSubpage = accountSubpage,
                    accountActions = accountActions,
                    accountIdentity = accountIdentity,
                    vm = vm,
                    marketplaceLanguage = marketplaceLanguage,
                    onMarketplaceLanguageChange = onMarketplaceLanguageChange,
                    onBackToHome = onBackToHome,
                    onOpenAdisChatProfile = onOpenAdisChatProfile,
                    onSelectTab = onSelectTab,
                    onRoute = onDetailRoute,
                    onAccountSubpage = onAccountSubpage,
                )
            }
        }
    }
}

internal fun ShopRoute.stateKey(): String = when (this) {
    is ShopRoute.HomeBrowse -> "home-browse:${section.name}"
    is ShopRoute.Product -> "product:${product.id}:$visitId"
    is ShopRoute.Category -> "category:${category.id}"
    is ShopRoute.Store -> "store:${store.id}"
    is ShopRoute.MessageConversation -> "conversation:${conversationId}"
    is ShopRoute.BuyerOrderDetail -> "buyer-order:${orderId}"
    is ShopRoute.OrderTracking -> "tracking:${orderId}"
    is ShopRoute.AdminProductDetail -> "admin-product:${productId}"
    is ShopRoute.AdminOrderDetail -> "admin-order:${orderId}"
    is ShopRoute.AdminStoreDetail -> "admin-store:${storeId}"
    is ShopRoute.VisualSearch -> "visual-search:${entryMode.name}"
    else -> this::class.simpleName.orEmpty()
}

@Composable
private fun ShopDetailRouteContent(
    route: ShopRoute,
    state: ShopHomeState,
    protectedState: ShopProtectedState,
    vm: ShopViewModel,
    visualSearchSession: MarketplaceVisualSearchSession,
    onRoute: (ShopRoute?) -> Unit,
    onSelectTab: (ShopRootTab) -> Unit,
    onAccountSubpage: (ShopAccountSubpage?) -> Unit,
) {
    when (route) {
        is ShopRoute.Product -> {
            LaunchedEffect(route.product.id, route.visitId) { vm.loadProductDetail(route.product) }
            val product = productForDetailRoute(route.product, state.productDetails)
            ProductDetailsScreen(
            product = product,
            categories = state.categories,
            catalogProducts = state.products,
            recentlyViewedProducts = protectedState.recentlyViewed.mapNotNull { it.product },
            onBack = { onRoute(null) },
            onSearch = { onRoute(ShopRoute.Search) },
            onAddToCart = { variantId, quantity -> vm.addToCart(product, variantId, quantity) },
            onCart = {
                onRoute(null)
                onSelectTab(ShopRootTab.Cart)
            },
            onToggleFavorite = { vm.toggleFavorite(product) },
            onBuyNow = { variantId, quantity ->
                vm.buyNow(product, variantId, quantity)
                onRoute(ShopRoute.Checkout)
            },
            onChatSeller = { variantId ->
                vm.chatSeller(product, variantId)
                onRoute(ShopRoute.Conversation)
            },
            onVisitStore = { onRoute(ShopRoute.Store(product.toStoreDto())) },
            onProduct = { onRoute(ShopRoute.Product(it)) },
            saved = protectedState.favorites.any { it.productId == route.product.id },
            cartCount = state.cartCount,
        )
        }
        is ShopRoute.Category -> CategoryProductsScreen(
            category = route.category,
            state = state,
            protectedState = protectedState,
            onBack = { onRoute(null) },
            onCart = {
                onRoute(null)
                onSelectTab(ShopRootTab.Cart)
            },
            onProduct = { onRoute(ShopRoute.Product(it)) },
            onAddToCart = vm::addToCart,
            onToggleFavorite = vm::toggleFavorite,
        )
        ShopRoute.Search -> MarketplaceSearchScreen(
            searchState = vm.buyerSearchState.collectAsStateWithLifecycle().value,
            categories = state.categories,
            recentlyViewed = protectedState.recentlyViewed.mapNotNull { it.product },
            savedIds = protectedState.favorites.map { it.productId }.toSet(),
            loading = state.loading,
            onBack = { onRoute(null) },
            onProduct = { onRoute(ShopRoute.Product(it)) },
            onCategory = { onRoute(ShopRoute.Category(it)) },
            onToggleFavorite = vm::toggleFavorite,
            onSearch = vm::searchBuyerCatalog,
            onClearSearch = vm::clearBuyerSearch,
            onLoadMore = vm::loadNextBuyerSearchPage,
            onRetry = vm::retryBuyerSearch,
        )
        is ShopRoute.HomeBrowse -> ShopHomeBrowseScreen(
            section = route.section,
            state = state,
            savedIds = protectedState.favorites.map { it.productId }.toSet(),
            onBack = { onRoute(null) },
            onRefresh = vm::refresh,
            onProduct = { onRoute(ShopRoute.Product(it)) },
            onToggleFavorite = vm::toggleFavorite,
        )
        ShopRoute.Checkout -> CheckoutScreen(
            protectedState = protectedState,
            onBack = { onRoute(null) },
            onLoadAddresses = vm::loadAddresses,
            onSaveAddress = vm::saveAddress,
            onConfirm = vm::confirmCheckout,
            onApplyCoupon = vm::applyCoupon,
            onApplyVoucher = vm::applyVoucher,
            onRemoveDiscount = vm::removeDiscount,
        )
        ShopRoute.Conversation -> BuyerConversationPage(
            state = protectedState,
            onBack = {
                vm.closeConversation()
                onRoute(null)
            },
            onRefresh = vm::refreshConversationMessages,
            onSend = vm::sendSellerMessage,
            onUpload = vm::sendSellerAttachment,
            onAction = vm::updateSellerConversation,
            onTyping = vm::updateConversationTyping,
            onOpenProduct = { productId ->
                onRoute(ShopRoute.Product(ShopProductDto(id = productId)))
            },
        )
        is ShopRoute.MessageConversation -> BuyerConversationPage(
            state = protectedState,
            onBack = {
                vm.closeConversation()
                onRoute(null)
            },
            onRefresh = vm::refreshConversationMessages,
            onSend = vm::sendSellerMessage,
            onUpload = vm::sendSellerAttachment,
            onAction = vm::updateSellerConversation,
            onTyping = vm::updateConversationTyping,
            onOpenProduct = { productId ->
                onRoute(ShopRoute.Product(ShopProductDto(id = productId)))
            },
        )
        ShopRoute.Notifications -> ShopNotificationsScreen(
            notifications = protectedState.notifications,
            unreadCount = protectedState.notificationUnreadCount,
            loading = protectedState.loading,
            onBack = { onRoute(null) },
            onRefresh = vm::loadNotifications,
            onMarkRead = vm::markNotificationRead,
            onMarkAllRead = vm::markAllNotificationsRead,
            onOpen = { target ->
                when (target) {
                    is ShopNotificationTarget.BuyerOrder -> {
                        onSelectTab(ShopRootTab.Account)
                        onAccountSubpage(ShopAccountSubpage.Orders)
                        val orderId = target.orderId.orEmpty()
                        if (orderId.isNotBlank()) {
                            vm.loadOrderDetail(orderId)
                            onRoute(ShopRoute.BuyerOrderDetail(orderId, target.orderGroupId))
                        } else {
                            target.orderGroupId?.let { orderGroupId ->
                                vm.loadOrderDetailForGroup(orderGroupId) { resolvedOrderId ->
                                    if (!resolvedOrderId.isNullOrBlank()) {
                                        onRoute(ShopRoute.BuyerOrderDetail(resolvedOrderId, orderGroupId))
                                    }
                                }
                            }
                        }
                    }
                    is ShopNotificationTarget.BuyerTracking -> {
                        onSelectTab(ShopRootTab.Account)
                        onAccountSubpage(ShopAccountSubpage.Orders)
                        vm.loadOrderDetail(target.orderId)
                        onRoute(ShopRoute.OrderTracking(target.orderId))
                    }
                    is ShopNotificationTarget.Conversation -> {
                        vm.loadConversationMessages(target.conversationId)
                        onRoute(ShopRoute.MessageConversation(target.conversationId))
                    }
                    is ShopNotificationTarget.BuyerProtection -> onRoute(ShopRoute.BuyerProtection)
                    is ShopNotificationTarget.AdminOrder -> onRoute(ShopRoute.AdminOrderDetail(target.orderId))
                    is ShopNotificationTarget.SellerOrder,
                    ShopNotificationTarget.SellerRequests,
                    ShopNotificationTarget.SellerProducts,
                    ShopNotificationTarget.SellerMore,
                    ShopNotificationTarget.Center -> Unit
                }
            },
        )
        is ShopRoute.BuyerOrderDetail -> BuyerOrderDetailRoute(
            orderId = route.orderId,
            orderGroupId = route.orderGroupId,
            protectedState = protectedState,
            vm = vm,
            onBack = { onRoute(null) },
            onTracking = {
                protectedState.selectedOrder?.id?.takeIf(String::isNotBlank)?.let { onRoute(ShopRoute.OrderTracking(it)) }
            },
        )
        is ShopRoute.OrderTracking -> {
            val order = protectedState.selectedOrder?.takeIf { it.id == route.orderId }
            if (order != null) {
                ShopTrackingScreen(
                    order = order,
                    deliveryCode = protectedState.deliveryCode?.takeIf { it.orderId == order.id }?.deliveryCode,
                    codeBusy = protectedState.busyIds.contains("delivery-code-${order.id}"),
                    onRevealCode = vm::revealDeliveryCode,
                    onCancel = vm::requestOrderCancellation,
                    onRefresh = { vm.loadOrderDetail(order.id) },
                    onBack = { onRoute(null) },
                )
            } else {
                ProtectedPage("Order Tracking", 0, { onRoute(null) }, { vm.loadOrderDetail(route.orderId) }, protectedState) {
                    EmptyCard("Loading tracking details...")
                }
            }
        }
        ShopRoute.BuyerProtection -> BuyerProtectionPage(onBack = { onRoute(null) })
        is ShopRoute.Store -> StoreDetailsScreen(
            store = route.store,
            state = state,
            onBack = { onRoute(null) },
            onCart = {
                onRoute(null)
                onSelectTab(ShopRootTab.Cart)
            },
            onProduct = { onRoute(ShopRoute.Product(it)) },
            onAddToCart = vm::addToCart,
            onToggleFavorite = vm::toggleFavorite,
            onChatSeller = {
                vm.chatSeller(it)
                onRoute(ShopRoute.Conversation)
            },
            savedIds = protectedState.favorites.map { it.productId }.toSet(),
            busyIds = protectedState.busyIds,
        )
        is ShopRoute.VisualSearch -> MarketplaceVisualSearchScreen(
            entryMode = route.entryMode,
            session = visualSearchSession,
            products = state.products,
            catalogLoading = state.loading,
            catalogError = state.error,
            onRefreshCatalog = vm::refresh,
            categories = state.categories,
            savedIds = protectedState.favorites.map { it.productId }.toSet(),
            onBack = { onRoute(null) },
            onProduct = { onRoute(ShopRoute.Product(it)) },
            onToggleFavorite = vm::toggleFavorite,
        )
        ShopRoute.Support -> PlaceholderShopPage(
            "AdisGebeya Support",
            "Support for orders, sellers and delivery will appear here.",
            onBack = { onRoute(null) },
        )
        ShopRoute.Admin,
        is ShopRoute.AdminProductDetail,
        is ShopRoute.AdminOrderDetail,
        is ShopRoute.AdminStoreDetail,
        -> ShopAdminRouteContent(route, protectedState, vm, onRoute)
        else -> Unit
    }
}

@Composable
private fun ShopAdminRouteContent(
    route: ShopRoute,
    protectedState: ShopProtectedState,
    vm: ShopViewModel,
    onRoute: (ShopRoute?) -> Unit,
) {
    if (route != ShopRoute.Admin && protectedState.adminSession == null) {
        LaunchedEffect(route) { vm.loadAdminSession() }
        AdminAccessGate(protectedState, onBack = { onRoute(ShopRoute.Admin) }, onRefresh = vm::loadAdminSession)
        return
    }
    when (route) {
        ShopRoute.Admin -> AdminCenterScreen(
            protectedState = protectedState,
            onBack = { onRoute(null) },
            onLoad = vm::loadAdminSession,
            onRefresh = vm::loadAdminSession,
            onReviewSeller = vm::adminReviewSeller,
            onReviewProduct = vm::adminReviewProduct,
            onUpdateRefund = vm::updateAdminRefund,
            onUpdateExchange = vm::updateAdminExchange,
            onModerateReview = vm::moderateAdminReview,
            onOpenProduct = { onRoute(ShopRoute.AdminProductDetail(it)) },
            onOpenOrder = { onRoute(ShopRoute.AdminOrderDetail(it)) },
            onOpenStore = { onRoute(ShopRoute.AdminStoreDetail(it)) },
            onLoadAllProducts = vm::loadAllAdminProducts,
            onAllProductsQueryChange = vm::setAllAdminProductsQuery,
            onSelectTab = vm::selectAdminTab,
            onLoadPublicContent = vm::loadPublicContentQueue,
            onPreviewPublicContent = vm::previewPublicContent,
            onClosePublicContentPreview = vm::closePublicContentPreview,
            onDecidePublicContent = vm::decidePublicContent,
        )
        is ShopRoute.AdminProductDetail -> AdminProductDetailScreen(
            productId = route.productId,
            protectedState = protectedState,
            onBack = {
                vm.clearAdminProductDetail()
                onRoute(ShopRoute.Admin)
            },
            onLoad = { vm.loadAdminProductDetail(route.productId) },
            onRefresh = { vm.loadAdminProductDetail(route.productId) },
            onReviewProduct = vm::adminReviewProduct,
        )
        is ShopRoute.AdminOrderDetail -> AdminOrderDetailScreen(
            orderId = route.orderId,
            protectedState = protectedState,
            onBack = {
                vm.clearAdminOrderDetail()
                onRoute(ShopRoute.Admin)
            },
            onLoad = { vm.loadAdminOrderDetail(route.orderId) },
            onRefresh = { vm.loadAdminOrderDetail(route.orderId) },
            onUpdateIssue = vm::adminUpdateOrderIssue,
        )
        is ShopRoute.AdminStoreDetail -> AdminStoreDetailScreen(
            storeId = route.storeId,
            protectedState = protectedState,
            onBack = {
                vm.clearAdminStoreDetail()
                onRoute(ShopRoute.Admin)
            },
            onLoad = { vm.loadAdminStoreDetail(route.storeId) },
            onRefresh = { vm.loadAdminStoreDetail(route.storeId) },
            onUpdateStore = vm::adminUpdateStoreStatus,
        )
        else -> Unit
    }
}

@Composable
private fun ShopRootTabContent(
    selectedTab: ShopRootTab,
    state: ShopHomeState,
    protectedState: ShopProtectedState,
    accountSubpage: ShopAccountSubpage?,
    accountActions: ShopAccountActions,
    accountIdentity: AdisChatAccountIdentity,
    vm: ShopViewModel,
    marketplaceLanguage: String,
    onMarketplaceLanguageChange: (String) -> Unit,
    onBackToHome: () -> Unit,
    onOpenAdisChatProfile: () -> Unit,
    onSelectTab: (ShopRootTab) -> Unit,
    onRoute: (ShopRoute?) -> Unit,
    onAccountSubpage: (ShopAccountSubpage?) -> Unit,
) {
    when (selectedTab) {
        ShopRootTab.Home -> ShopHomeScreen(
            state = state,
            onBack = onBackToHome,
            onRefresh = vm::refresh,
            onCart = { onSelectTab(ShopRootTab.Cart) },
            onNotifications = { onRoute(ShopRoute.Notifications) },
            notificationUnreadCount = protectedState.notificationUnreadCount,
            marketplaceLanguage = marketplaceLanguage,
            onMarketplaceLanguageChange = onMarketplaceLanguageChange,
            onImageSearch = { onRoute(ShopRoute.VisualSearch(MarketplaceVisualSearchEntryMode.Image)) },
            onScanProduct = { onRoute(ShopRoute.VisualSearch(MarketplaceVisualSearchEntryMode.Scan)) },
            onSearch = { onRoute(ShopRoute.Search) },
            onBrowse = { onRoute(ShopRoute.HomeBrowse(it)) },
            onProduct = { onRoute(ShopRoute.Product(it)) },
            onStore = { onRoute(ShopRoute.Store(it)) },
            onCategory = { onRoute(ShopRoute.Category(it)) },
            onAddToCart = vm::addToCart,
            onToggleFavorite = vm::toggleFavorite,
            savedIds = protectedState.favorites.map { it.productId }.toSet(),
            busyIds = protectedState.busyIds,
            onLoadCategoryFilters = vm::loadBuyerCategoryFilters,
            onSearchProducts = vm::searchProducts,
            onClearProductSearch = vm::clearProductSearch,
        )
        ShopRootTab.Trending -> TrendingScreen(
            state = state,
            protectedState = protectedState,
            onBack = onBackToHome,
            onRefresh = vm::refresh,
            onProduct = { onRoute(ShopRoute.Product(it)) },
            onCategory = { onRoute(ShopRoute.Category(it)) },
            onAddToCart = vm::addToCart,
            onToggleFavorite = vm::toggleFavorite,
        )
        ShopRootTab.Cart -> CartScreen(
            protectedState,
            state.cartCount,
            onBackToHome,
            vm::loadCart,
            vm::updateCartItem,
            onProduct = { onRoute(ShopRoute.Product(it)) },
            onClear = vm::clearCart,
            onCheckout = {
                vm.createCheckout()
                onRoute(ShopRoute.Checkout)
            },
        )
        ShopRootTab.Account -> ShopAccountScreen(
            state = state,
            protectedState = protectedState,
            selectedSubpage = accountSubpage,
            onBack = onBackToHome,
            accountIdentity = accountIdentity,
            onOpenAdisChatProfile = onOpenAdisChatProfile,
            onSubpage = onAccountSubpage,
            onSubpageBack = { onAccountSubpage(null) },
            onAdminCenter = { onRoute(ShopRoute.Admin) },
            onProduct = { onRoute(ShopRoute.Product(it)) },
            onRoute = onRoute,
            actions = accountActions,
        )
    }
}

private data class ShopAccountActions(
    val loadFavorites: () -> Unit,
    val loadRecentlyViewed: () -> Unit,
    val loadAddresses: () -> Unit,
    val loadOrders: () -> Unit,
    val loadBuyerReviews: () -> Unit,
    val loadOrderDetail: (String) -> Unit,
    val requestOrderCancellation: (String, String) -> Unit,
    val revealDeliveryCode: (String) -> Unit,
    val submitOrderIssue: (String, String?, String, String) -> Unit,
    val submitProductReview: (String, String, Int, String, String) -> Unit,
    val submitStoreReview: (String, String, Int, String) -> Unit,
    val submitRefundRequest: (String, String, Int, String, String) -> Unit,
    val submitExchangeRequest: (String, String, Int, String, String) -> Unit,
    val buyAgainFromOrder: (String) -> Unit,
    val loadReceipt: (String) -> Unit,
    val clearReceipt: () -> Unit,
    val loadNotifications: () -> Unit,
    val markAllNotificationsRead: () -> Unit,
    val loadSellerStatus: () -> Unit,
    val loadSellerOrders: () -> Unit,
    val sellerOrderAction: (String, String, String, String?, String?, String?, Int?) -> Unit,
    val addToCart: (ShopProductDto) -> Unit,
    val toggleFavorite: (ShopProductDto) -> Unit,
    val saveAddress: (ShopAddressRequest) -> Unit,
    val deleteAddress: (String) -> Unit,
    val setDefaultAddress: (String) -> Unit,
    val submitSellerApplication: (String) -> Unit,
    val saveSellerApplicationDraft: (ShopSellerApplicationRequest) -> Unit,
    val submitCompleteSellerApplication: (ShopSellerApplicationRequest) -> Unit,
    val respondSellerApplicationInfoRequest: (String) -> Unit,
    val respondSellerRequest: (String, String, String, Int) -> Unit,
    val loadAdminSession: () -> Unit,
)

private fun shopAccountActions(vm: ShopViewModel): ShopAccountActions = ShopAccountActions(
    loadFavorites = vm::loadFavorites,
    loadRecentlyViewed = vm::loadRecentlyViewed,
    loadAddresses = vm::loadAddresses,
    loadOrders = vm::loadOrders,
    loadBuyerReviews = vm::loadBuyerReviews,
    loadOrderDetail = vm::loadOrderDetail,
    requestOrderCancellation = vm::requestOrderCancellation,
    revealDeliveryCode = vm::revealDeliveryCode,
    submitOrderIssue = vm::submitOrderIssue,
    submitProductReview = vm::submitProductReview,
    submitStoreReview = vm::submitStoreReview,
    submitRefundRequest = vm::submitRefundRequest,
    submitExchangeRequest = vm::submitExchangeRequest,
    buyAgainFromOrder = vm::buyAgainFromOrder,
    loadReceipt = vm::loadReceipt,
    clearReceipt = vm::clearReceipt,
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
    saveSellerApplicationDraft = vm::saveSellerApplicationDraft,
    submitCompleteSellerApplication = vm::submitCompleteSellerApplication,
    respondSellerApplicationInfoRequest = vm::respondSellerApplicationInfoRequest,
    respondSellerRequest = vm::respondSellerRequest,
    loadAdminSession = vm::loadAdminSession,
)

@Composable
private fun ShopBottomBar(
    selectedTab: ShopRootTab,
    cartCount: Int,
    onSelect: (ShopRootTab) -> Unit,
) {
    NavigationBar(
        containerColor = ShopSurface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .height(76.dp)
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
                    selectedIconColor = AdisGebeyaHomeV2Colors.Purple,
                    selectedTextColor = AdisGebeyaHomeV2Colors.Purple,
                    indicatorColor = AdisGebeyaHomeV2Colors.PurpleLight,
                    unselectedIconColor = ShopMuted,
                    unselectedTextColor = ShopMuted,
                ),
            )
        }
    }
}
@Composable
private fun CategoryBreadcrumbsTrail(
    category: ShopCategoryDto,
    categories: List<ShopCategoryDto>,
    onSelect: (ShopCategoryDto) -> Unit,
) {
    val lineage = remember(category.id, categories) {
        val byId = categories.associateBy { it.id }
        val trail = mutableListOf(category)
        var parentId = category.parentId
        val visited = mutableSetOf(category.id)
        while (!parentId.isNullOrBlank() && visited.add(parentId)) {
            val parent = byId[parentId] ?: break
            trail.add(0, parent)
            parentId = parent.parentId
        }
        trail
    }
    if (lineage.size > 1) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(lineage, key = { _, cat -> cat.id }) { index, item ->
                val isCurrent = index == lineage.lastIndex
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCurrent) ShopGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isCurrent) ShopGreen else Color.LightGray.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable(!isCurrent) { onSelect(item) },
                ) {
                    Text(
                        text = item.name,
                        color = if (isCurrent) ShopGreen else ShopInk,
                        fontSize = 12.sp,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (index < lineage.lastIndex) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Gray,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubcategoryIconCards(
    categories: List<ShopCategoryDto>,
    onSelect: (ShopCategoryDto) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text("Subcategories", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ShopInk)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.width(100.dp).clickable { onSelect(item) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = ShopGreen.copy(alpha = 0.1f),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    categoryIcon(item),
                                    contentDescription = item.name,
                                    tint = ShopGreen,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = item.name,
                            fontSize = 11.5.sp,
                            lineHeight = 14.sp,
                            minLines = 2,
                            fontWeight = FontWeight.Medium,
                            color = ShopInk,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
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
    onCart: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onAddToCart: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
) {
    val fullCategories = remember(state.categories) {
        CategoryHierarchyDefinition.buildAuthoritativeHierarchy(state.categories)
    }
    var activeCategory by remember(category.id) {
        mutableStateOf(fullCategories.firstOrNull { it.id == category.id } ?: category)
    }
    var query by remember(activeCategory.id) { mutableStateOf("") }

    BackHandler {
        val parent = activeCategory.parentId?.let { id -> fullCategories.firstOrNull { it.id == id } }
        if (parent == null) onBack() else activeCategory = parent
    }

    val descendantIds = remember(activeCategory.id, fullCategories) {
        CategoryHierarchyDefinition.resolveDescendantIds(activeCategory.id, fullCategories)
    }
    val descendantSlugs = remember(descendantIds, fullCategories) {
        fullCategories.filter { it.id in descendantIds }.map { it.slug }.toSet()
    }
    val childCategories = remember(activeCategory.id, fullCategories) {
        fullCategories.filter { it.parentId == activeCategory.id && it.buyerVisible }
            .sortedWith(compareBy({ it.displayOrder }, { it.name }))
    }
    val products = remember(state.products, activeCategory, descendantIds, descendantSlugs, query) {
        state.products.filter {
            val inCategory = it.categoryId in descendantIds ||
                (it.categorySlug != null && it.categorySlug in descendantSlugs) ||
                (it.categoryId == null && (it.categorySlug == activeCategory.slug || it.categorySlug == activeCategory.name.lowercase()))
            val matchesSearch = query.isBlank() || it.title.contains(query, ignoreCase = true) || it.brand.orEmpty().contains(query, ignoreCase = true)
            inCategory && matchesSearch
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(ShopBg).statusBarsPadding(), contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        stickyHeader {
            ShopHeader(
                cartCount = state.cartCount,
                onBack = {
                    val parent = activeCategory.parentId?.let { id -> fullCategories.firstOrNull { it.id == id } }
                    if (parent == null) onBack() else activeCategory = parent
                },
                onCart = onCart,
                query = query,
                onQuery = { query = it },
            )
        }
        item {
            CategoryBreadcrumbsTrail(
                category = activeCategory,
                categories = fullCategories,
                onSelect = { activeCategory = it },
            )
        }
        item { PageTitle(activeCategory.name, "${products.size} products available") }
        if (childCategories.isNotEmpty()) {
            item { SubcategoryIconCards(childCategories) { activeCategory = it } }
        }
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
    accountIdentity: AdisChatAccountIdentity,
    onOpenAdisChatProfile: () -> Unit,
    onSubpage: (ShopAccountSubpage) -> Unit,
    onSubpageBack: () -> Unit,
    onAdminCenter: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onRoute: (ShopRoute?) -> Unit,
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
            onOpen = { orderId ->
                actions.loadOrderDetail(orderId)
                onRoute(ShopRoute.BuyerOrderDetail(orderId))
            },
            actions.requestOrderCancellation,
            actions.revealDeliveryCode,
            actions.submitOrderIssue,
            actions.submitProductReview,
            actions.submitStoreReview,
            actions.submitRefundRequest,
            actions.submitExchangeRequest,
            actions.buyAgainFromOrder,
            actions.loadReceipt,
            actions.clearReceipt,
        )
        ShopAccountSubpage.Saved -> SavedProductsScreen(protectedState, state.cartCount, onSubpageBack, actions.loadFavorites, onProduct, actions.toggleFavorite, actions.addToCart)
        ShopAccountSubpage.RecentlyViewed -> RecentlyViewedScreen(protectedState, state.cartCount, onSubpageBack, actions.loadRecentlyViewed, onProduct, actions.addToCart)
        ShopAccountSubpage.Addresses -> AddressesScreen(protectedState, onSubpageBack, actions.loadAddresses, actions.saveAddress, actions.deleteAddress, actions.setDefaultAddress)
        ShopAccountSubpage.Reviews -> BuyerReviewsScreen(
            orders = protectedState.orders,
            reviews = protectedState.buyerReviews,
            loading = !protectedState.buyerReviewsLoaded,
            onLoad = actions.loadBuyerReviews,
            onBack = onSubpageBack,
            onOrder = { orderId ->
                actions.loadOrderDetail(orderId)
                onRoute(ShopRoute.BuyerOrderDetail(orderId))
            },
        )
        ShopAccountSubpage.BuyerProtection -> BuyerProtectionPage(onBack = onSubpageBack)
        null -> ShopAccountHome(
            state = state,
            protectedState = protectedState,
            identity = accountIdentity,
            onOpenAdisChatProfile = onOpenAdisChatProfile,
            onSubpage = onSubpage,
            onAdminCenter = onAdminCenter,
        )
    }
}

@Composable
private fun ShopAccountHome(
    state: ShopHomeState,
    protectedState: ShopProtectedState,
    identity: AdisChatAccountIdentity,
    onOpenAdisChatProfile: () -> Unit,
    onSubpage: (ShopAccountSubpage) -> Unit,
    onAdminCenter: () -> Unit,
) {
    AdisGebeyaBuyerAccountScreen(
        identity = identity,
        identityConnected = state.shopIdentityReady,
        state = protectedState,
        onProfile = onOpenAdisChatProfile,
        onOrders = { onSubpage(ShopAccountSubpage.Orders) },
        onSaved = { onSubpage(ShopAccountSubpage.Saved) },
        onRecentlyViewed = { onSubpage(ShopAccountSubpage.RecentlyViewed) },
        onAddresses = { onSubpage(ShopAccountSubpage.Addresses) },
        onReviews = { onSubpage(ShopAccountSubpage.Reviews) },
        onBuyerProtection = { onSubpage(ShopAccountSubpage.BuyerProtection) },
        onAdmin = onAdminCenter,
    )
}

private enum class AdminTab(val label: String) {
    Sellers("Sellers"),
    Products("Products"),
    AllProducts("All Products"),
    PublicContent("Public Content"),
    Stores("Stores"),
    Refunds("Refunds"),
    Exchanges("Exchanges"),
    Reviews("Reviews"),
    Orders("Orders"),
    Audit("Audit"),
}

@Composable
private fun AdminAccessGate(
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(ShopBg).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ShopInk) }
            Text("AdisGebeya Admin", color = ShopInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = !protectedState.adminLoading) {
                Icon(Icons.Default.Refresh, contentDescription = "Retry admin access", tint = ShopGreen)
            }
        }
        EmptyCard(
            if (protectedState.adminLoading) "Loading admin workspace..."
            else protectedState.adminError?.takeIf { it.isNotBlank() } ?: "You do not have access to AdisGebeya Admin."
        )
    }
}

@Composable
private fun AdminCenterScreen(
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onReviewSeller: (String, String, String) -> Unit,
    onReviewProduct: (String, String, String, String?) -> Unit,
    onUpdateRefund: (String, String, String) -> Unit,
    onUpdateExchange: (String, String, String) -> Unit,
    onModerateReview: (String, String, String, String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenStore: (String) -> Unit,
    onLoadAllProducts: (com.adischat.app.feature.shop.data.ShopAdminProductQuery) -> Unit,
    onAllProductsQueryChange: (com.adischat.app.feature.shop.data.ShopAdminProductQuery) -> Unit,
    onSelectTab: (String) -> Unit,
    onLoadPublicContent: (Int, String?) -> Unit,
    onPreviewPublicContent: (com.adischat.app.feature.shop.data.ShopPublicContentItemDto) -> Unit,
    onClosePublicContentPreview: () -> Unit,
    onDecidePublicContent: (String) -> Unit,
) {
    val selectedTab = AdminTab.entries.firstOrNull { it.label == protectedState.adminSelectedTab } ?: AdminTab.Sellers
    val allProductsQuery = protectedState.allAdminProductsQuery
    var reason by remember { mutableStateOf("") }
    var pendingContentDecision by remember { mutableStateOf<String?>(null) }
    var publicContentKind by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(protectedState.publicContentPreview?.entityId, protectedState.adminSession?.profileId) {
        pendingContentDecision = null
    }

    LaunchedEffect(Unit) { if (protectedState.adminSession == null && !protectedState.adminLoading) onLoad() }
    LaunchedEffect(selectedTab, allProductsQuery, protectedState.adminSession?.profileId, publicContentKind) {
        if (selectedTab == AdminTab.AllProducts && protectedState.adminSession != null) onLoadAllProducts(allProductsQuery)
        if (selectedTab == AdminTab.PublicContent &&
            com.adischat.app.feature.shop.data.ShopPublicContentReviewFlags.enabled &&
            "product.moderation.approve" in protectedState.adminSession?.permissions.orEmpty()
        ) onLoadPublicContent(0, publicContentKind)
    }

    if (protectedState.adminSession == null) {
        AdminAccessGate(protectedState, onBack, onRefresh)
        return
    }

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

        item { AdminDashboardCards(protectedState) }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AdminTab.entries.filter { tab ->
                    when (tab) {
                        AdminTab.AllProducts -> "product.moderation.read" in protectedState.adminSession?.permissions.orEmpty()
                        AdminTab.PublicContent -> com.adischat.app.feature.shop.data.ShopPublicContentReviewFlags.enabled &&
                            "product.moderation.approve" in protectedState.adminSession?.permissions.orEmpty()
                        else -> true
                    }
                }) { tab ->
                    val selected = selectedTab == tab
                    Surface(
                        color = if (selected) ShopGreen else ShopSurface,
                        contentColor = if (selected) ShopOnColor else ShopInk,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.clickable { onSelectTab(tab.label) },
                    ) {
                        Text(tab.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        }

        if (selectedTab in listOf(AdminTab.Sellers, AdminTab.Products, AdminTab.Refunds, AdminTab.Exchanges, AdminTab.Reviews)) {
            item {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reason or public update") },
                    colors = shopOutlinedTextFieldColors(),
                    singleLine = false,
                )
            }
        }

        when (selectedTab) {
            AdminTab.PublicContent -> {
                if (com.adischat.app.feature.shop.data.ShopPublicContentReviewFlags.enabled &&
                    "product.moderation.approve" in protectedState.adminSession?.permissions.orEmpty()) {
                    item { Text("Historical content review", color = ShopInk, fontWeight = FontWeight.SemiBold) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(null, "store", "product", "image")) { kind ->
                                OutlinedButton(onClick = { publicContentKind = kind },
                                    enabled = !protectedState.publicContentLoading && !protectedState.publicContentReviewing) {
                                    Text(kind?.let(::backendLabel) ?: "All")
                                }
                            }
                        }
                    }
                    protectedState.publicContentError?.let { message ->
                        item { AdminAllProductsMessage(message) }
                    }
                    if (protectedState.publicContentLoading) item { EmptyCard("Loading content...") }
                    val queue = protectedState.publicContentQueue
                    if (!protectedState.publicContentLoading && queue?.items.isNullOrEmpty()) {
                        item { EmptyCard("No content awaiting review.") }
                    }
                    items(queue?.items.orEmpty(), key = { "public-${it.entityKind}-${it.entityId}" }) { entry ->
                        OutlinedButton(
                            onClick = { onPreviewPublicContent(entry) },
                            enabled = !protectedState.publicContentLoading && !protectedState.publicContentReviewing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${backendLabel(entry.entityKind)}  |  ${entry.targetId.take(8)}  |  ${entry.reviewStatus}  |  ${entry.expectedRevision?.asString ?: "Revision unavailable"}")
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onLoadPublicContent((protectedState.publicContentOffset - com.adischat.app.feature.shop.data.ShopPublicContentReviewFlags.pageSize).coerceAtLeast(0), publicContentKind) },
                                enabled = protectedState.publicContentOffset > 0 && !protectedState.publicContentLoading) { Text("Previous") }
                            OutlinedButton(onClick = { onLoadPublicContent(protectedState.publicContentOffset + com.adischat.app.feature.shop.data.ShopPublicContentReviewFlags.pageSize, publicContentKind) },
                                enabled = queue != null && protectedState.publicContentOffset + queue.items.size < queue.total && !protectedState.publicContentLoading) { Text("Next") }
                            TextButton(onClick = { onLoadPublicContent(protectedState.publicContentOffset, publicContentKind) },
                                enabled = !protectedState.publicContentLoading) { Text("Retry") }
                        }
                    }
                }
            }
            AdminTab.AllProducts -> {
                item {
                    AdminAllProductsControls(
                        query = allProductsQuery,
                        loading = protectedState.allAdminProductsLoading,
                        onQueryChange = onAllProductsQueryChange,
                        onRetry = { onLoadAllProducts(allProductsQuery) },
                    )
                }
                protectedState.allAdminProductsError?.let { message -> item { AdminAllProductsMessage(message) } }
                if (!protectedState.allAdminProductsLoading && protectedState.allAdminProductsError == null && protectedState.allAdminProducts.isEmpty()) {
                    item { AdminAllProductsMessage("No products match this search.") }
                }
                items(protectedState.allAdminProducts, key = { "all-${it.id}" }) { product ->
                    AdminAllProductRow(product, formatPrice = ::formatEtb, onOpen = { onOpenProduct(product.id) })
                }
                item {
                    AdminAllProductsPagination(
                        offset = protectedState.allAdminProductsOffset,
                        pageSize = allProductsQuery.limit,
                        hasNext = protectedState.allAdminProductsHasNext,
                        loading = protectedState.allAdminProductsLoading,
                        onPrevious = { onAllProductsQueryChange(allProductsQuery.copy(offset = (allProductsQuery.offset - allProductsQuery.limit).coerceAtLeast(0))) },
                        onNext = { onAllProductsQueryChange(allProductsQuery.copy(offset = allProductsQuery.offset + allProductsQuery.limit)) },
                    )
                }
            }
            AdminTab.Sellers -> {
                if (protectedState.adminSellerApplications.isEmpty()) item { EmptyCard("No seller applications are waiting for review.") }
                items(protectedState.adminSellerApplications, key = { it.id }) { application ->
                    AdminSellerApplicationCard(application, protectedState.busyIds.contains(application.id), reason, onReviewSeller)
                }
            }
            AdminTab.Products -> {
                if (com.adischat.app.feature.shop.data.ShopBenefitFlags.managementEnabled) {
                    item { AdminBenefitReviewPanel() }
                    item { AdminBenefitBrandPanel() }
                }
                if (protectedState.adminProducts.isEmpty()) item { EmptyCard("No products are waiting for moderation.") }
                items(protectedState.adminProducts, key = { it.id }) { product ->
                    AdminProductCard(product, protectedState.busyIds.contains(product.id), reason, onReviewProduct, onOpenProduct)
                }
            }
            AdminTab.Stores -> {
                if (protectedState.adminStores.isEmpty()) item { EmptyCard("No stores found.") }
                items(protectedState.adminStores, key = { it.id }) { store ->
                    AdminStoreCard(store, onOpen = { onOpenStore(store.id) })
                }
            }
            AdminTab.Refunds -> {
                if (protectedState.adminRefundRequests.isEmpty()) item { EmptyCard("No refund requests found.") }
                items(protectedState.adminRefundRequests, key = { it.id }) { request ->
                    AdminRefundCard(request, protectedState.busyIds.contains(request.id), reason, onUpdateRefund)
                }
            }
            AdminTab.Exchanges -> {
                if (protectedState.adminExchangeRequests.isEmpty()) item { EmptyCard("No exchange requests found.") }
                items(protectedState.adminExchangeRequests, key = { it.id }) { request ->
                    AdminExchangeCard(request, protectedState.busyIds.contains(request.id), reason, onUpdateExchange)
                }
            }
            AdminTab.Reviews -> {
                if (protectedState.adminReviews.isEmpty()) item { EmptyCard("No reviews found.") }
                items(protectedState.adminReviews, key = { "${it.reviewType}-${it.id}" }) { review ->
                    AdminReviewModerationCard(review, protectedState.busyIds.contains(review.id), reason, onModerateReview)
                }
            }
            AdminTab.Orders -> {
                if (protectedState.adminOrders.isEmpty()) item { EmptyCard("No open orders found.") }
                items(protectedState.adminOrders, key = { it.id }) { order ->
                    AdminOrderCard(order, onOpen = { onOpenOrder(order.id) })
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
    val preview = protectedState.publicContentPreview
    if (selectedTab == AdminTab.PublicContent && preview != null) {
        AlertDialog(
            onDismissRequest = { if (!protectedState.publicContentReviewing) onClosePublicContentPreview() },
            title = { Text("${backendLabel(preview.entityKind)} · ${preview.reviewStatus} · revision ${preview.expectedRevision?.asString ?: "unavailable"}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Record ${preview.targetId}", color = ShopMuted)
                    val imageUrl = (preview.content["imageUrl"] ?: preview.content["logoUrl"]) as? String
                    if (!imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Content under review",
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    }
                    preview.content.forEach { (key, value) ->
                        Text(backendLabel(key), fontWeight = FontWeight.SemiBold)
                        Text(value?.toString() ?: "Not provided")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingContentDecision = "approved" },
                    enabled = !protectedState.publicContentReviewing && "approve" in preview.permittedActions) { Text("Approve") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingContentDecision = "rejected" },
                        enabled = !protectedState.publicContentReviewing && "reject" in preview.permittedActions) { Text("Reject") }
                    TextButton(onClick = onClosePublicContentPreview, enabled = !protectedState.publicContentReviewing) { Text("Close") }
                }
            },
        )
    }
    pendingContentDecision?.takeIf { selectedTab == AdminTab.PublicContent && preview != null }?.let { decision ->
        AlertDialog(
            onDismissRequest = { pendingContentDecision = null },
            title = { Text("Confirm ${decision.removeSuffix("ed")}") },
            text = { Text("Review only the displayed revision. A changed revision will be rejected by the server.") },
            confirmButton = { TextButton(onClick = { pendingContentDecision = null; onDecidePublicContent(decision) }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { pendingContentDecision = null }) { Text("Cancel") } },
        )
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
private fun AdminStoreCard(store: ShopAdminStoreDto, onOpen: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(ShopSurface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(store.name.ifBlank { "Unnamed store" }, color = ShopInk, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(store.ownerName ?: store.ownerEmail ?: "Store owner", color = ShopMuted, fontSize = 12.sp)
                }
                MiniBadge(backendLabel(store.status), orderStatusBg(store.status), orderStatusColor(store.status))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniBadge("${store.productCount} products", ShopSoft, ShopMuted)
                MiniBadge("${store.completedOrderCount} orders", ShopGreenSoft, ShopGreen)
                if (store.warningCount > 0) MiniBadge("${store.warningCount} warnings", ShopAmberSoft, ShopOrangeDark)
            }
            Text("Issues ${store.openIssueCount} • Rating ${store.ratingAverage ?: 0.0} (${store.ratingCount ?: 0})", color = ShopMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AdminRefundCard(
    request: ShopRefundRequestDto,
    busy: Boolean,
    reason: String,
    onAction: (String, String, String) -> Unit,
) {
    AdminModerationRequestCard(
        label = "Refund",
        title = request.reasonLabel ?: backendLabel(request.reasonCode),
        status = request.status,
        body = adminRequestSummary(request.description, request.sellerResponse, request.buyerVisibleUpdate),
        meta = formatEtb(request.requestedAmount),
        busy = busy,
        reason = reason,
        onAction = { action, safeReason -> onAction(request.id, action, safeReason) },
    )
}

@Composable
private fun AdminExchangeCard(
    request: ShopExchangeRequestDto,
    busy: Boolean,
    reason: String,
    onAction: (String, String, String) -> Unit,
) {
    AdminModerationRequestCard(
        label = "Exchange",
        title = request.reasonLabel ?: backendLabel(request.reasonCode),
        status = request.status,
        body = adminRequestSummary(request.description, request.sellerResponse, request.buyerVisibleUpdate),
        meta = request.createdAt.orEmpty(),
        busy = busy,
        reason = reason,
        onAction = { action, safeReason -> onAction(request.id, action, safeReason) },
    )
}

private fun adminRequestSummary(description: String?, sellerResponse: String?, buyerVisibleUpdate: String?): String =
    listOfNotNull(
        description?.takeIf(String::isNotBlank),
        sellerResponse?.takeIf(String::isNotBlank)?.let { "Seller response: $it" },
        buyerVisibleUpdate
            ?.takeIf { it.isNotBlank() && it != sellerResponse }
            ?.let { "Buyer update: $it" },
    ).joinToString("\n")

@Composable
private fun AdminModerationRequestCard(
    label: String,
    title: String,
    status: String,
    body: String,
    meta: String,
    busy: Boolean,
    reason: String,
    onAction: (String, String) -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = ShopGreen, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(title, color = ShopInk, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                MiniBadge(backendLabel(status), orderStatusBg(status), orderStatusColor(status))
            }
            if (body.isNotBlank()) Text(body, color = ShopMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (meta.isNotBlank()) Text(meta, color = ShopGreen, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAction("review", reason.ifBlank { "Admin review started." }) }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Review") }
                OutlinedButton(onClick = { onAction("request_seller", reason.ifBlank { "Please provide more information." }) }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Ask seller") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction("approve", reason.ifBlank { "Request approved after review." }) }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) { Text("Approve") }
                TextButton(onClick = { onAction("reject", reason.ifBlank { "Request rejected after review." }) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Reject", color = ShopOrangeDark, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun AdminReviewModerationCard(
    review: ShopAdminReviewModerationDto,
    busy: Boolean,
    reason: String,
    onAction: (String, String, String, String) -> Unit,
) {
    var pendingAction by remember(review.id) { mutableStateOf<String?>(null) }
    var moderationReason by remember(review.id) { mutableStateOf(reason) }
    val currentStatus = review.status.orEmpty().lowercase()

    pendingAction?.let { action ->
        val actionLabel = backendLabel(action)
        AlertDialog(
            onDismissRequest = { if (!busy) pendingAction = null },
            title = { Text("$actionLabel review?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Confirm this moderation action. The reason is recorded in moderation history.",
                        color = ShopMuted,
                    )
                    OutlinedTextField(
                        value = moderationReason,
                        onValueChange = { moderationReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Reason") },
                        colors = shopOutlinedTextFieldColors(),
                        enabled = !busy,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAction(review.id, review.reviewType, action, moderationReason.trim())
                        pendingAction = null
                    },
                    enabled = !busy && moderationReason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action == "publish") ShopGreen else ShopOrangeDark,
                    ),
                ) {
                    Text("Confirm $actionLabel")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }, enabled = !busy) {
                    Text("Cancel")
                }
            },
        )
    }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(backendLabel(review.reviewType) + " review", color = ShopGreen, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(review.title ?: review.body ?: "Customer review", color = ShopInk, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                MiniBadge(backendLabel(review.status.orEmpty()), orderStatusBg(review.status.orEmpty()), orderStatusColor(review.status.orEmpty()))
            }
            Text("${review.rating}/5 stars${if (review.verifiedPurchase) " - verified purchase" else ""}", color = ShopMuted, fontSize = 12.sp)
            review.body?.takeIf { it.isNotBlank() }?.let { Text(it, color = ShopMuted, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pendingAction = "publish" }, enabled = !busy && currentStatus != "published", modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) { Text(if (currentStatus == "published") "Published" else "Publish") }
                OutlinedButton(onClick = { pendingAction = "hide" }, enabled = !busy && currentStatus != "hidden", modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(if (currentStatus == "hidden") "Hidden" else "Hide") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pendingAction = "reject" }, enabled = !busy && currentStatus != "rejected", modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(if (currentStatus == "rejected") "Rejected" else "Reject") }
                TextButton(onClick = { pendingAction = "remove" }, enabled = !busy && currentStatus != "removed", modifier = Modifier.weight(1f)) { Text(if (currentStatus == "removed") "Removed" else "Remove", color = ShopOrangeDark, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun AdminStoreDetailScreen(
    storeId: String,
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateStore: (String, String, String) -> Unit,
) {
    var reason by remember(storeId) { mutableStateOf("") }
    var pendingAction by remember(storeId) { mutableStateOf<String?>(null) }
    LaunchedEffect(storeId) { onLoad() }
    val detail = protectedState.selectedAdminStore
    val store = detail?.store
    val busy = protectedState.busyIds.contains("admin-store-$storeId")

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("${backendLabel(action)} store", color = ShopInk, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Confirm this action for ${store?.name ?: "the selected store"}. Historical orders stay preserved.", color = ShopMuted)
                    OutlinedTextField(reason, { reason = it }, label = { Text("Seller-visible reason") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = shopOutlinedTextFieldColors())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateStore(storeId, action, reason.ifBlank { defaultStoreActionReason(action) })
                        pendingAction = null
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = if (action == "reactivate") ShopGreen else ShopOrange),
                ) { Text(if (busy) "Saving..." else backendLabel(action)) }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } },
            containerColor = ShopSurface,
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().background(ShopBg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ShopInk) }
                Column(Modifier.weight(1f)) {
                    Text("Store Detail", color = ShopInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("Store moderation and compliance", color = ShopMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh store", tint = ShopGreen) }
            }
        }
        if (protectedState.adminLoading && detail == null) item { EmptyCard("Loading store detail...") }
        protectedState.adminError?.takeIf { it.isNotBlank() }?.let { item { EmptyCard(it) } }
        if (detail == null || store == null) {
            item { EmptyCard("Store detail is unavailable.") }
        } else {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(store.name.ifBlank { "Store" }, color = ShopInk, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniBadge(backendLabel(store.status), orderStatusBg(store.status), orderStatusColor(store.status))
                            MiniBadge(backendLabel(store.approvalStatus), ShopSoft, ShopMuted)
                        }
                        AdminInfoRow("Owner", store.ownerName ?: detail.owner?.displayName ?: "Seller")
                        AdminInfoRow("Products", store.productCount.toString())
                        AdminInfoRow("Completed orders", store.completedOrderCount.toString())
                        AdminInfoRow("Open issues", store.openIssueCount.toString())
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Moderation actions")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { pendingAction = "warn" }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Warn") }
                            OutlinedButton(onClick = { pendingAction = "suspend" }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Suspend") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { pendingAction = "reactivate" }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) { Text("Reactivate") }
                            TextButton(onClick = { pendingAction = "archive" }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Archive", color = ShopOrangeDark, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
            item { AdminRequestSummary("Refund requests", detail.refundRequests) }
            if (com.adischat.app.feature.shop.data.ShopBenefitFlags.managementEnabled) {
                item { AdminBenefitReviewPanel(storeId = storeId) }
            }
            item { AdminRequestSummary("Exchange requests", detail.exchangeRequests) }
            item { AdminSimpleMapList("Warnings", detail.warnings) }
            item { AdminSimpleMapList("Violations", detail.violations) }
        }
    }
}

private fun defaultStoreActionReason(action: String): String = when (action) {
    "warn" -> "Please review and follow AdisGebeya marketplace policies."
    "suspend" -> "Store temporarily suspended for marketplace review."
    "archive" -> "Store archived by Admin after review."
    else -> "Store reactivated after Admin review."
}

@Composable
private fun AdminRequestSummary(title: String, requests: List<Any>) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(title)
            if (requests.isEmpty()) Text("No requests.", color = ShopMuted)
            requests.take(5).forEach { request ->
                val status = when (request) {
                    is ShopRefundRequestDto -> request.status
                    is ShopExchangeRequestDto -> request.status
                    else -> ""
                }
                Text(backendLabel(status), color = ShopInk, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AdminSimpleMapList(title: String, rows: List<Map<String, Any>>) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(title)
            if (rows.isEmpty()) Text("No records.", color = ShopMuted)
            rows.take(5).forEach { row ->
                Text(backendLabel(row["reason_code"]?.toString() ?: row["event_code"]?.toString() ?: "record"), color = ShopInk, fontWeight = FontWeight.SemiBold)
                Text(row["created_at"]?.toString().orEmpty(), color = ShopMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AdminProductDetailScreen(
    productId: String,
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onReviewProduct: (String, String, String, String?) -> Unit,
) {
    var reason by remember(productId) { mutableStateOf("") }
    var pendingAction by remember(productId) { mutableStateOf<String?>(null) }
    LaunchedEffect(productId) { onLoad() }
    val detail = protectedState.selectedAdminProduct?.takeIf { it.product?.id == productId }
    val product = detail?.product
    val moderationStatus = product?.moderationStatus.orEmpty().ifBlank { product?.status.orEmpty() }
    val reviewable = product?.status == "draft" && moderationStatus in setOf("submitted", "under_review") && !product.submittedAt.isNullOrBlank()
    val busy = protectedState.busyIds.contains(productId)

    pendingAction?.let { action ->
        val actionLabel = backendLabel(action)
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("$actionLabel product", color = ShopInk, fontWeight = FontWeight.SemiBold) },
            text = { Text("Confirm this moderation action for the selected product.", color = ShopMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        onReviewProduct(productId, action, reason.ifBlank { defaultProductReviewReason(action) }, product?.submittedAt)
                        pendingAction = null
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = if (action == "approve" || action == "start_review") ShopGreen else ShopOrange),
                ) { Text(actionLabel) }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } },
            containerColor = ShopSurface,
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().background(ShopBg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ShopInk) }
                Column(Modifier.weight(1f)) {
                    Text("Product Detail", color = ShopInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("Admin moderation review", color = ShopMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh product", tint = ShopGreen) }
            }
        }

        if (protectedState.adminLoading && detail == null) item { EmptyCard("Loading product details...") }
        protectedState.adminError?.takeIf { it.isNotBlank() }?.let { item { EmptyCard(it) } }
        if (!protectedState.adminLoading && detail == null) {
            item { EmptyCard("Product detail is unavailable.") }
        } else if (detail != null && product != null) {
        item { AdminProductGallery(detail.images, product.title.ifBlank { "Product image" }) }
        if (com.adischat.app.feature.shop.data.ShopBenefitFlags.managementEnabled) {
            item { AdminBenefitReviewPanel(productId = productId) }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniBadge(backendLabel(moderationStatus), orderStatusBg(moderationStatus), orderStatusColor(moderationStatus))
                        MiniBadge(product.stockStatus.replace("_", " "), stockStatusBg(product.stockStatus), stockStatusColor(product.stockStatus))
                    }
                    Text(product.title.ifBlank { "Untitled product" }, color = ShopInk, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
                    if (!product.shortDescription.isNullOrBlank()) Text(product.shortDescription, color = ShopMuted, lineHeight = 20.sp)
                    Text(formatEtb(product.priceMinor), color = ShopGreen, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    AdminInfoRow("Category", product.categorySlug ?: "Not set")
                    AdminInfoRow("Submitted", formatMarketplaceDateTime(product.submittedAt, "Not submitted"))
                    AdminInfoRow("Reviewed", formatMarketplaceDateTime(product.reviewedAt, "Not reviewed"))
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Seller and Store")
                    AdminInfoRow("Store", detail.store?.name ?: "Store")
                    AdminInfoRow("Store status", "${backendLabel(detail.store?.status)} / ${backendLabel(detail.store?.approvalStatus)}")
                    AdminInfoRow("Seller", product.sellerSku?.takeIf { it.isNotBlank() } ?: "Marketplace seller")
                }
            }
        }
        item {
            AdminDetailSection("Description", adminReadableText(product.description).ifBlank { "No full description provided." })
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Inventory")
                    if (detail.inventory.isEmpty()) {
                        Text("No inventory rows found.", color = ShopMuted)
                    } else {
                        detail.inventory.forEach { inventory ->
                            AdminInfoRow("Available", "${inventory.quantityAvailable} available, ${inventory.quantityReserved} reserved")
                        }
                    }
                }
            }
        }
        if (detail.variants.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Product variants")
                        detail.variants.forEach { variant ->
                            val optionLabel = variant.options.orEmpty().entries.joinToString(" • ") { (key, value) ->
                                "${backendLabel(key)}: ${backendLabel(value?.toString())}"
                            }.ifBlank { variant.title.ifBlank { "Default option" } }
                            Text(optionLabel, color = ShopInk, fontWeight = FontWeight.SemiBold)
                            AdminInfoRow(
                                formatEtb(variant.priceMinor),
                                "${variant.inventory?.quantityAvailable ?: 0} available, ${variant.inventory?.quantityReserved ?: 0} reserved",
                            )
                        }
                    }
                }
            }
        }
        if (detail.specificationValues.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Category attributes")
                        detail.specificationValues.forEach { specification ->
                            val value = specification.valueText
                                ?: specification.valueNumber?.toString()
                                ?: specification.valueBoolean?.let { if (it) "Yes" else "No" }
                                ?: "Not provided"
                            AdminInfoRow(specification.displayName, listOfNotNull(value, specification.unit).joinToString(" "))
                        }
                    }
                }
            }
        }
        item { AdminMapSection("Specifications", product.specifications) }
        item { AdminMapSection("Shipping", product.shippingDetails) }
        item { AdminMapSection("Returns", product.returnPolicy) }
        item { AdminModerationHistory(detail.reviews) }
        if (reviewable) item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Review Action")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Seller-visible reason or private note") },
                        minLines = 2,
                        colors = shopOutlinedTextFieldColors(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pendingAction = "start_review" }, enabled = !busy && moderationStatus == "submitted", modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Start") }
                        Button(onClick = { pendingAction = "approve" }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) {
                            Text(if (busy) "Saving..." else "Approve")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pendingAction = "request_changes" }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Changes") }
                        TextButton(onClick = { pendingAction = "reject" }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Reject", color = ShopRed, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AdminProductGallery(images: List<ShopProductImageDto>, productTitle: String) {
    val safeImages = remember(images) {
        images
            .filter { it.imageUrl?.isNotBlank() == true }
            .distinctBy { it.id.ifBlank { it.imageUrl.orEmpty() } }
            .sortedWith(compareByDescending<ShopProductImageDto> { it.isPrimary == true }.thenBy { it.displayOrder ?: 0 })
    }
    val pagerState = rememberPagerState(pageCount = { safeImages.size.coerceAtLeast(1) })
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.18f).clip(RoundedCornerShape(24.dp)).background(ShopSoft).border(1.dp, ShopLine, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (safeImages.isEmpty()) {
                ShopAssetImage(null, productTitle, Modifier.fillMaxSize(), iconSize = 76.dp)
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val image = safeImages[page]
                    Box(Modifier.fillMaxSize()) {
                        ShopAssetImage(image.imageUrl, image.altText ?: productTitle, Modifier.fillMaxSize(), iconSize = 76.dp)
                        if (image.isPrimary == true) {
                            Surface(Modifier.align(Alignment.TopStart).padding(12.dp), color = ShopGreen, contentColor = ShopOnColor, shape = RoundedCornerShape(999.dp)) {
                                Text("Primary", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
                Surface(Modifier.align(Alignment.BottomEnd).padding(12.dp), color = ShopInk.copy(alpha = .72f), contentColor = ShopOnColor, shape = RoundedCornerShape(999.dp)) {
                    Text("${pagerState.currentPage + 1} / ${safeImages.size}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
        if (safeImages.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                safeImages.forEachIndexed { index, image ->
                    Box(Modifier.size(width = if (pagerState.currentPage == index) 20.dp else 8.dp, height = 8.dp).clip(RoundedCornerShape(999.dp)).background(if (image.isPrimary == true) ShopGreen else if (pagerState.currentPage == index) ShopOrange else ShopLine))
                }
            }
        }
    }
}

@Composable
private fun AdminInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = ShopMuted, fontSize = 13.sp)
        Text(value.ifBlank { "Not set" }, color = ShopInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}

@Composable
private fun AdminDetailSection(title: String, body: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(title)
            Text(body, color = ShopMuted, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun AdminMapSection(title: String, values: Map<String, Any>?) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(title)
            val entries = values.orEmpty().filterValues { it.toString().isNotBlank() }
            if (entries.isEmpty()) {
                Text("Not provided.", color = ShopMuted)
            } else {
                entries.forEach { (key, value) -> AdminInfoRow(backendLabel(key), adminReadableText(value.toString())) }
            }
        }
    }
}

@Composable
private fun AdminModerationHistory(reviews: List<ShopAdminProductReviewDto>) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Moderation History")
            if (reviews.isEmpty()) {
                Text("No moderation actions yet.", color = ShopMuted)
            } else {
                reviews.forEach { review ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(backendLabel(review.decision), color = ShopInk, fontWeight = FontWeight.SemiBold)
                        Text("${backendLabel(review.previousStatus)} -> ${backendLabel(review.newStatus)}", color = ShopMuted, fontSize = 12.sp)
                        review.publicReason?.takeIf { it.isNotBlank() }?.let { Text(it, color = ShopMuted, fontSize = 12.sp) }
                        review.privateNotes?.takeIf { it.isNotBlank() }?.let { Text("Private note recorded", color = ShopOrangeDark, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                        review.createdAt?.let { Text(formatMarketplaceDateTime(it), color = ShopMuted, fontSize = 11.sp) }
                    }
                    HorizontalDivider(color = ShopLine.copy(alpha = .55f))
                }
            }
        }
    }
}

private fun defaultProductReviewReason(action: String): String = when (action) {
    "request_changes" -> "Product needs changes before publishing."
    "reject" -> "Product does not meet AdisGebeya listing standards."
    else -> ""
}

private fun adminReadableText(value: String?): String {
    val raw = value.orEmpty()
    if (raw.isBlank()) return ""
    return runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrDefault(raw)
}

@Composable
private fun AdminSellerApplicationCard(
    application: ShopAdminSellerApplicationDto,
    busy: Boolean,
    reason: String,
    onReviewSeller: (String, String, String) -> Unit,
) {
    val status = statusKey(application.status.orEmpty())
    val storeName = application.storeName.orEmpty().ifBlank { "Unnamed store" }
    val applicantName = application.applicantName.orEmpty().ifBlank { "Seller" }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(storeName, color = ShopInk, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("$applicantName - ${backendLabel(application.status.orEmpty())}", color = ShopMuted, fontSize = 12.sp)
                }
                MiniBadge(backendLabel(application.status.orEmpty()), sellerStatusBg(status), sellerStatusColor(status))
            }
            AdminInfoRow("Email", application.applicantEmail ?: application.email ?: "Not provided")
            AdminInfoRow("Phone", application.phone ?: "Not provided")
            AdminInfoRow("Business", listOfNotNull(application.legalBusinessName, application.businessType).joinToString(" - ").ifBlank { "Not provided" })
            AdminInfoRow("Location", listOfNotNull(application.city, application.district).joinToString(", ").ifBlank { "Not provided" })
            AdminInfoRow("Documents", "${application.documents.size} uploaded")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onReviewSeller(application.id, if (status == "suspended") "reactivate" else "approve", "") }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) {
                    Text(if (status == "suspended") "Reactivate" else "Approve")
                }
                OutlinedButton(onClick = { onReviewSeller(application.id, "request_info", reason.ifBlank { "Please provide more information for your seller application." }) }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("Request info")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onReviewSeller(application.id, "start_review", "") }, enabled = !busy && status == "submitted", modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Start review") }
                TextButton(onClick = { onReviewSeller(application.id, "reject", reason.ifBlank { "Application does not meet seller onboarding requirements." }) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Reject", color = ShopOrangeDark, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AdminProductCard(
    product: ShopAdminProductDto,
    busy: Boolean,
    reason: String,
    onReviewProduct: (String, String, String, String?) -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    val moderationStatus = product.moderationStatus.orEmpty().ifBlank { product.status.orEmpty() }
    val reviewable = product.status == "draft" && moderationStatus in setOf("submitted", "under_review") && !product.submittedAt.isNullOrBlank()
    AdminReviewCard(
        title = product.title.orEmpty().ifBlank { "Untitled product" },
        subtitle = "${product.storeName.orEmpty().ifBlank { "Store" }} - ${backendLabel(moderationStatus)}",
        meta = formatEtb(product.priceMinor),
        busy = busy,
        actionsEnabled = reviewable,
        primaryLabel = "Approve",
        secondaryLabel = if (statusKey(moderationStatus) == "submitted") "Start review" else "Request changes",
        tertiaryLabel = "Reject",
        onPrimary = { onReviewProduct(product.id, "approve", "", product.submittedAt) },
        onSecondary = {
            val action = if (statusKey(moderationStatus) == "submitted") "start_review" else "request_changes"
            onReviewProduct(product.id, action, reason.ifBlank { "Product needs changes before publishing." }, product.submittedAt)
        },
        onTertiary = { onReviewProduct(product.id, "reject", reason.ifBlank { "Product does not meet AdisGebeya listing standards." }, product.submittedAt) },
        onOpen = { onOpenProduct(product.id) },
    )
}

@Composable
private fun AdminReviewCard(
    title: String,
    subtitle: String,
    meta: String,
    busy: Boolean,
    actionsEnabled: Boolean = true,
    primaryLabel: String,
    secondaryLabel: String,
    tertiaryLabel: String? = null,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onTertiary: () -> Unit = {},
    onOpen: (() -> Unit)? = null,
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = ShopMuted, fontSize = 12.sp)
            if (meta.isNotBlank()) Text(meta, color = ShopGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            onOpen?.let {
                OutlinedButton(onClick = it, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("View product detail", fontWeight = FontWeight.SemiBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSecondary, enabled = !busy && actionsEnabled, modifier = Modifier.weight(1f)) { Text(secondaryLabel) }
                Button(onClick = onPrimary, enabled = !busy && actionsEnabled, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) {
                    Text(if (busy) "Saving..." else primaryLabel)
                }
            }
            tertiaryLabel?.let {
                TextButton(onClick = onTertiary, enabled = !busy && actionsEnabled, modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = if (busy || !actionsEnabled) ShopMuted else ShopRed)
                }
            }
        }
    }
}

@Composable
private fun AdminOrderCard(order: ShopAdminOrderDto, onOpen: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(ShopSurface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.orderNumber.orEmpty().ifBlank { "Order" }, color = ShopInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Open order detail", tint = ShopMuted)
            }
            Text("${orderStatusLabel(order.status.orEmpty())} - ${fulfillmentStatusLabel(order.fulfillmentStatus.orEmpty())}", color = ShopMuted, fontSize = 12.sp)
            Text("${paymentStatusLabel(order.paymentStatus.orEmpty())} - ${formatEtb(order.totalMinor)}", color = ShopGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AdminOrderDetailScreen(
    orderId: String,
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateIssue: (String, String, String, String) -> Unit,
) {
    LaunchedEffect(orderId) { onLoad() }
    val detail = protectedState.selectedAdminOrder
    val order = detail?.order
    LazyColumn(
        Modifier.fillMaxSize().background(ShopBg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ShopInk) }
                Column(Modifier.weight(1f)) {
                    Text("Order Detail", color = ShopInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(order?.orderNumber ?: "Admin support view", color = ShopMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh order", tint = ShopGreen) }
            }
        }
        if (protectedState.adminLoading && detail == null) item { EmptyCard("Loading order details...") }
        protectedState.adminError?.takeIf { it.isNotBlank() }?.let { item { EmptyCard(it) } }
        if (!protectedState.adminLoading && detail == null) {
            item { EmptyCard("Order detail is unavailable.") }
        } else if (detail != null && order != null) {
            item { AdminOrderSummaryCard(order, detail) }
            item { AdminOrderGroupsSection(detail) }
            item { AdminOrderTimelineSection(detail.history) }
            item { AdminOrderIssuesSection(detail, protectedState.busyIds, onUpdateIssue) }
            item { AdminOrderDeliverySection(detail) }
            item { AdminOrderNotesSection(detail) }
            item { AdminOrderAuditSection(detail.audit) }
        }
    }
}

@Composable
private fun AdminOrderSummaryCard(order: ShopAdminOrderDto, detail: ShopAdminOrderDetailDto) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniBadge(orderStatusLabel(order.status.orEmpty()), orderStatusBg(order.status.orEmpty()), orderStatusColor(order.status.orEmpty()))
                MiniBadge(fulfillmentStatusLabel(order.fulfillmentStatus.orEmpty()), orderStatusBg(order.fulfillmentStatus.orEmpty()), orderStatusColor(order.fulfillmentStatus.orEmpty()))
            }
            Text(order.orderNumber.orEmpty().ifBlank { "Order" }, color = ShopInk, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            AdminInfoRow("Payment", "${paymentStatusLabel(order.paymentStatus.orEmpty())} - ${formatEtb(order.totalMinor)}")
            AdminInfoRow("Created", formatMarketplaceDateTime(order.createdAt, "Not available"))
            detail.address?.let { address ->
                AdminInfoRow("Recipient", listOfNotNull(address.recipientName, address.city).joinToString(" - ").ifBlank { "Customer" })
                AdminInfoRow("Delivery", listOfNotNull(address.addressLine1, address.addressLine2).joinToString(", ").ifBlank { "Address unavailable" })
            }
            detail.payments.firstOrNull()?.let { payment ->
                AdminInfoRow("Payment method", "${paymentMethodLabel(payment.methodCode.orEmpty())} / ${paymentStatusLabel(payment.status.orEmpty())}")
            }
        }
    }
}

@Composable
private fun AdminOrderGroupsSection(detail: ShopAdminOrderDetailDto) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Store Groups")
            detail.groups.forEach { group ->
                AdminInfoRow("Store", group.storeNameSnapshot ?: "Marketplace store")
                AdminInfoRow("Status", "${backendLabel(group.status)} / ${fulfillmentStatusLabel(group.fulfillmentStatus.orEmpty())}")
                AdminInfoRow("Seller response", backendLabel(group.sellerResponseStatus))
                AdminInfoRow("Response deadline", formatSellerResponseDeadline(group.sellerResponseRequiredAt))
                group.sellerRespondedAt?.let { AdminInfoRow("Seller responded", formatSellerResponseDeadline(it)) }
                group.buyerVisibleRejectionReason
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { AdminInfoRow("Rejection reason", backendLabel(it)) }
                group.sellerResponseExpiredAt?.let { AdminInfoRow("Expired", formatSellerResponseDeadline(it)) }
                group.sellerResponseExpirationSource?.let { AdminInfoRow("Expiration source", backendLabel(it)) }
                if (group.sellerResponseRefundStatus != "not_required") {
                    AdminInfoRow("Refund", backendLabel(group.sellerResponseRefundStatus))
                }
                group.items.orEmpty().forEach { item ->
                    AdminInfoRow(item.titleSnapshot.ifBlank { "Item" }, "${item.quantity} x ${formatEtb(item.unitPriceMinor)}")
                    item.variantDisplayLabel()?.let { AdminInfoRow("Configuration", it) }
                }
            }
            if (detail.inventory.isNotEmpty()) {
                HorizontalDivider(color = ShopLine)
                detail.inventory.forEach { inventory ->
                    AdminInfoRow("Inventory", "${inventory.quantityAvailable} available, ${inventory.quantityReserved} reserved")
                }
            }
        }
    }
}

@Composable
private fun AdminOrderTimelineSection(history: List<com.adischat.app.feature.shop.data.ShopOrderStatusHistoryDto>) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Timeline")
            if (history.isEmpty()) Text("No timeline events yet.", color = ShopMuted) else history.forEach { event ->
                AdminInfoRow(backendLabel(event.eventCode.orEmpty()), event.publicMessage ?: "${backendLabel(event.previousStatus.orEmpty())} -> ${backendLabel(event.nextStatus.orEmpty())}")
                AdminInfoRow("Time", formatMarketplaceDateTime(event.occurredAt ?: event.createdAt, "Time unavailable"))
                event.reason
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { AdminInfoRow("Reason", backendLabel(it)) }
            }
        }
    }
}

@Composable
private fun AdminOrderIssuesSection(
    detail: ShopAdminOrderDetailDto,
    busyIds: Set<String>,
    onUpdateIssue: (String, String, String, String) -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Buyer Protection Issues")
            if (detail.issues.isEmpty()) {
                Text("No buyer issues reported.", color = ShopMuted)
            } else {
                detail.issues.forEach { issue ->
                    var showUpdate by remember(issue.id) { mutableStateOf(false) }
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopSoft)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                MiniBadge(issueStatusLabel(issue.status), orderStatusBg(issue.status), orderStatusColor(issue.status))
                                Text(issue.reasonLabel ?: issueReasonLabel(issue.reasonCode), color = ShopInk, fontWeight = FontWeight.SemiBold)
                            }
                            if (issue.description.isNotBlank()) Text(issue.description, color = ShopMuted, lineHeight = 18.sp)
                            issue.buyerVisibleResolution?.takeIf { it.isNotBlank() }?.let { Text("Buyer update: $it", color = ShopGreen, fontSize = 12.sp) }
                            issue.events.forEach { event ->
                                AdminInfoRow(backendLabel(event.eventCode), event.publicMessage ?: issueStatusLabel(event.nextStatus))
                            }
                            Button(
                                onClick = { showUpdate = true },
                                enabled = !busyIds.contains("admin-issue-${issue.id}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ShopGreen),
                            ) { Text("Update Issue") }
                        }
                    }
                    if (showUpdate) {
                        AdminIssueUpdateDialog(
                            issue = issue,
                            busy = busyIds.contains("admin-issue-${issue.id}"),
                            onDismiss = { if (!busyIds.contains("admin-issue-${issue.id}")) showUpdate = false },
                            onUpdate = { status, publicMessage, privateNote ->
                                showUpdate = false
                                onUpdateIssue(issue.id, status, publicMessage, privateNote)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminIssueUpdateDialog(
    issue: ShopOrderIssueDto,
    busy: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (String, String, String) -> Unit,
) {
    val statuses = listOf(
        "under_review" to "Under review",
        "awaiting_buyer" to "Request buyer info",
        "awaiting_seller" to "Request seller info",
        "resolved" to "Resolve",
        "rejected" to "Reject issue",
        "closed" to "Close",
    )
    var selectedStatus by remember(issue.id) { mutableStateOf("under_review") }
    var publicMessage by remember(issue.id) { mutableStateOf("") }
    var privateNote by remember(issue.id) { mutableStateOf("") }
    val requiresPublic = selectedStatus in listOf("awaiting_buyer", "awaiting_seller", "resolved", "rejected", "closed")
    val canSubmit = !busy && (!requiresPublic || publicMessage.trim().length >= 3)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onUpdate(selectedStatus, publicMessage, privateNote) },
                enabled = canSubmit,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShopGreen),
            ) { Text(if (busy) "Saving..." else "Save Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
        title = { Text("Update issue", color = ShopInk, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(issue.reasonLabel ?: issueReasonLabel(issue.reasonCode), color = ShopMuted)
                statuses.forEach { (status, label) ->
                    Surface(
                        onClick = { selectedStatus = status },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedStatus == status) ShopGreenSoft else ShopSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedStatus == status) ShopGreen.copy(alpha = .35f) else ShopLine),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedStatus == status, onClick = { selectedStatus = status })
                            Text(label, color = ShopInk, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                OutlinedTextField(
                    value = publicMessage,
                    onValueChange = { publicMessage = it.take(600) },
                    label = { Text(if (requiresPublic) "Buyer-visible message required" else "Buyer-visible message optional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = shopOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = privateNote,
                    onValueChange = { privateNote = it.take(1200) },
                    label = { Text("Private support note") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = shopOutlinedTextFieldColors(),
                )
            }
        },
        containerColor = ShopSurface,
    )
}

@Composable
private fun AdminOrderDeliverySection(detail: ShopAdminOrderDetailDto) {
    val code = detail.deliveryCodeStatus
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Delivery Confirmation")
            AdminInfoRow("Code revealed", if (code?.revealed == true) "Yes" else "No")
            AdminInfoRow("Code verified", if (code?.verified == true) "Yes" else "No")
            AdminInfoRow("Failed attempts", (code?.failedAttempts ?: 0).toString())
            AdminInfoRow("Locked", if (code?.locked == true) "Yes" else "No")
            AdminInfoRow("Attempts", detail.deliveryAttempts.size.toString())
        }
    }
}

@Composable
private fun AdminOrderNotesSection(detail: ShopAdminOrderDetailDto) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Support Notes")
            if (detail.notes.isEmpty()) Text("No private support notes.", color = ShopMuted) else detail.notes.forEach { note ->
                AdminInfoRow(backendLabel(note.visibility.orEmpty()), note.note.orEmpty().ifBlank { "Note" })
            }
        }
    }
}

@Composable
private fun AdminOrderAuditSection(audit: List<ShopAdminAuditLogDto>) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Audit")
            if (audit.isEmpty()) Text("No audit entries.", color = ShopMuted) else audit.take(12).forEach { row ->
                AdminInfoRow(backendLabel(row.action.orEmpty()), formatMarketplaceDateTime(row.createdAt, "Recorded"))
            }
        }
    }
}

@Composable
private fun AdminAuditCard(audit: ShopAdminAuditLogDto) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopSurface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(backendLabel(audit.action.orEmpty()), color = ShopInk, fontWeight = FontWeight.SemiBold)
            Text("${backendLabel(audit.entityType.orEmpty())} - ${audit.actorName ?: "Admin"}", color = ShopMuted, fontSize = 12.sp)
            audit.createdAt?.let { Text(formatMarketplaceDateTime(it), color = ShopMuted, fontSize = 11.sp) }
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
    onNotifications: () -> Unit,
    notificationUnreadCount: Int,
    marketplaceLanguage: String,
    onMarketplaceLanguageChange: (String) -> Unit,
    onImageSearch: () -> Unit,
    onScanProduct: () -> Unit,
    onSearch: () -> Unit,
    onBrowse: (ShopHomeBrowseSection) -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onStore: (ShopStoreDto) -> Unit,
    onCategory: (ShopCategoryDto) -> Unit,
    onAddToCart: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
    savedIds: Set<String>,
    busyIds: Set<String>,
    onLoadCategoryFilters: (String) -> Unit,
    onSearchProducts: (Set<String>, Int?, Int?, Int, Boolean, Boolean, String) -> Unit,
    onClearProductSearch: () -> Unit,
) {
    var initialBrandPhaseComplete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500)
        initialBrandPhaseComplete = true
    }
    if (shouldShowBuyerInitialLoader(initialBrandPhaseComplete, state.error)) {
        BuyerMarketplaceInitialLoader()
        return
    }
    if (state.loading && !state.error) {
        BuyerHomeSkeleton()
        return
    }

    var filters by remember { mutableStateOf(ShopHomeFilters()) }
    var discoverySurface by remember { mutableStateOf<MarketplaceDiscoverySurface>(MarketplaceDiscoverySurface.None) }
    val selectedCategory = filters.categorySlug
    val selectedCategoryModel = state.categories.firstOrNull { it.slug == selectedCategory }
    val selectedCategorySlugs = remember(state.categories, selectedCategory) {
        marketplaceCategoryAndDescendantSlugs(state.categories, selectedCategory)
    }
    LaunchedEffect(selectedCategoryModel?.id) {
        selectedCategoryModel?.id?.let(onLoadCategoryFilters)
    }
    val serverFilterActive = selectedCategory != null || filters.minimumPriceEtb.isNotBlank() ||
        filters.maximumPriceEtb.isNotBlank() || filters.minimumRating > 0 || filters.inStockOnly ||
        filters.discountedOnly || filters.sort != MarketplaceSort.Newest.label
    LaunchedEffect(
        selectedCategorySlugs,
        filters.minimumPriceEtb,
        filters.maximumPriceEtb,
        filters.minimumRating,
        filters.inStockOnly,
        filters.discountedOnly,
        filters.sort,
    ) {
        if (serverFilterActive) {
            onSearchProducts(
                selectedCategorySlugs,
                filters.minimumPriceEtb.toIntOrNull()?.times(100),
                filters.maximumPriceEtb.toIntOrNull()?.times(100),
                filters.minimumRating,
                filters.inStockOnly,
                false,
                marketplaceBackendOrder(filters.sort),
            )
        } else {
            onClearProductSearch()
        }
    }
    val listState = rememberLazyListState()
    val products = remember(state.products, state.discoveryProducts, state.productDetails, filters) {
        (state.discoveryProducts ?: state.products)
            .filter { product ->
                val matchesCategory = selectedCategory == null || product.categorySlug in selectedCategorySlugs
                val matchesStock = !filters.inStockOnly || product.stockStatus?.contains("out", ignoreCase = true) != true
                val matchesDiscount = !filters.discountedOnly || homePreviousPriceMinor(product) != null
                val minimumPrice = filters.minimumPriceEtb.toIntOrNull()?.times(100)
                val maximumPrice = filters.maximumPriceEtb.toIntOrNull()?.times(100)
                val matchesPrice = (minimumPrice == null || product.priceMinor >= minimumPrice) &&
                    (maximumPrice == null || product.priceMinor <= maximumPrice)
                val matchesRating = filters.minimumRating <= 0 || (product.averageRating ?: 0.0) >= filters.minimumRating
                val detail = state.productDetails[product.id]
                val matchesAttributes = filters.categoryValues.all { (code, value) ->
                    detail?.matchesBuyerFilter(code, value) == true
                }
                matchesCategory && matchesStock && matchesDiscount && matchesPrice && matchesRating && matchesAttributes
            }
            .let { list ->
                when (filters.sort) {
                    "Price low to high" -> list.sortedBy { it.priceMinor }
                    "Price high to low" -> list.sortedByDescending { it.priceMinor }
                    "Best rated" -> list.sortedByDescending { it.averageRating ?: 0.0 }
                    "Most sold" -> list.sortedByDescending { it.soldCount ?: 0 }
                    "Most discounted" -> list.sortedByDescending { verifiedDiscountPercent(it) ?: 0 }
                    else -> list
                }
            }
    }
    val homeSections = remember(products) { compactHomeSections(products) }
    val rootCategories = remember(state.categories) { state.categories.filter { it.parentId == null && it.buyerVisible } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(AdisGebeyaHomeV2Colors.Background),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        stickyHeader {
            AdisGebeyaHomeTopBar(
                language = marketplaceLanguage,
                onNotifications = onNotifications,
                notificationUnreadCount = notificationUnreadCount,
                onSearch = onSearch,
                onImageSearch = onImageSearch,
                onScanner = onScanProduct,
                onLanguage = onMarketplaceLanguageChange,
                onDiscovery = { discoverySurface = it },
                activeFilterCount = filters.activeLabels(state.categories).size,
            )
        }
        if (filters.activeLabels(state.categories).isNotEmpty()) {
            item {
                ShopHomePadded {
                    MarketplaceActiveFilterChips(filters, state.categories) { filters = it }
                }
            }
        }
        when {
            state.error -> item { ShopHomePadded { ErrorCard(maintenance = state.maintenance, onRefresh = onRefresh) } }
            else -> {
                item { ShopHomePadded { PremiumBuyerHomeHero(state.banners, onSearch) } }
                if (rootCategories.isNotEmpty()) {
                    item { ShopHomePadded { PremiumHomeCategories(rootCategories, onCategory) } }
                }
                if (!serverFilterActive && homeSections.deals.isNotEmpty()) {
                    item { ShopHomePadded { PremiumHomeDealStrip(homeSections.deals.first(), onProduct) } }
                }
                if (products.isNotEmpty()) {
                    if (serverFilterActive) {
                        item { ShopHomePadded { ProductGridSection("Results", products, onProduct, onToggleFavorite, savedIds, maxItems = 20) } }
                    } else {
                        if (homeSections.recommended.isNotEmpty()) item { ShopHomePadded { PremiumHomeProductGrid("Recommended for you", homeSections.recommended, savedIds, onProduct, onToggleFavorite, { onBrowse(ShopHomeBrowseSection.Recommended) }) } }
                        if (homeSections.newArrivals.isNotEmpty()) item { ShopHomePadded { PremiumHomeProductGrid("New Arrivals", homeSections.newArrivals, savedIds, onProduct, onToggleFavorite, { onBrowse(ShopHomeBrowseSection.NewArrivals) }) } }
                        if (homeSections.deals.size > 1) item { ShopHomePadded { PremiumHomeProductGrid("Deals", homeSections.deals.drop(1), savedIds, onProduct, onToggleFavorite, { onBrowse(ShopHomeBrowseSection.Deals) }) } }
                    }
                } else {
                    item {
                        ShopHomePadded {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                PremiumEmptyState(
                                    title = if (serverFilterActive) "No matching products" else "No products available",
                                    message = if (serverFilterActive) "Try changing your filters." else "Check back for new arrivals.",
                                    icon = Icons.Default.Storefront,
                                )
                                if (serverFilterActive) TextButton(onClick = { filters = ShopHomeFilters() }) { Text("Clear filters", color = ShopGreen) }
                            }
                        }
                    }
                }
            }
        }
    }

    MarketplaceDiscoverySurfaceHost(
        surface = discoverySurface,
        filters = filters,
        categories = state.categories,
        attributes = selectedCategoryModel?.id?.let(state.buyerCategoryAttributes::get).orEmpty(),
        products = state.products.filter { selectedCategory == null || it.categorySlug in selectedCategorySlugs },
        productDetails = state.productDetails,
        loadingAttributes = selectedCategoryModel?.id == state.buyerFilterLoadingCategoryId,
        onDismiss = { discoverySurface = MarketplaceDiscoverySurface.None },
        onApply = {
            filters = it
            discoverySurface = MarketplaceDiscoverySurface.None
        },
    )
}

@Composable
private fun ShopHomePadded(content: @Composable () -> Unit) {
    com.adischat.app.ui.home.AdaptiveFlowAround(
        besideStart = 60.dp,
        belowStart = 8.dp,
    ) {
        content()
    }
}

@Composable
private fun AdisGebeyaBrandHeader(
    cartCount: Int,
    onCart: () -> Unit,
    onNotifications: () -> Unit,
    notificationUnreadCount: Int,
    onSearch: () -> Unit,
    onFilter: () -> Unit,
    activeFilterCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ShopSurface,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth().border(1.dp, ShopLine),
    ) {
        val sidebar = com.adischat.app.ui.home.LocalSidebarBounds.current
        val startPad = if (sidebar != null) 60.dp else 12.dp
        Box(
            Modifier
                .fillMaxWidth()
                .background(ShopSurface)
                .statusBarsPadding()
                .padding(start = startPad, end = 12.dp, top = 4.dp, bottom = 8.dp),
        ) {
            Column(
                Modifier.fillMaxWidth(),
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
                        Text("AdisGebeya", color = ShopInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("አዲስ ገበያ", color = ShopGreenSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    MarketplaceHeaderAction(
                        Icons.Default.Notifications,
                        "Marketplace notifications",
                        onClick = onNotifications,
                        badge = notificationUnreadCount,
                    )
                    MarketplaceHeaderAction(Icons.Default.ShoppingCart, "Shopping cart", onCart, cartCount)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MarketplaceSearchField(
                        onClick = onSearch,
                        modifier = Modifier.weight(1f),
                    )
                    MarketplaceHeaderAction(Icons.Default.FilterList, "Filter products", onFilter, activeFilterCount, active = activeFilterCount > 0)
                }
            }
        }
    }
}

@Composable
private fun StickyHeaderDeliveryChip() {
    Row(
        Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ShopGreenSoft)
            .border(1.dp, ShopLine, RoundedCornerShape(14.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(5.dp))
        Column(
            Modifier
                .semantics { contentDescription = "Deliver to, set delivery location" },
        ) {
            Text("Deliver to", color = ShopMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text("Addis Ababa", color = ShopInk, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(ShopSoft)
            .border(1.dp, ShopLine, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = ShopMuted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text("Search products, stores and categories", color = ShopMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                .size(44.dp)
                .clip(CircleShape)
                .background(ShopSurface),
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
internal fun ShopHeader(
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
                HeaderIcon(Icons.Default.ArrowBack, "Back", onClick = onBack)
                Row(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(ShopSurface)
                        .border(1.dp, ShopLine, RoundedCornerShape(15.dp))
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
                HeaderIcon(Icons.Default.ShoppingCart, "Cart", onClick = onCart, badge = cartCount)
            }
        }
    }
}

@Composable
internal fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit, badge: Int = 0, tint: Color = ShopInk) {
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
    attributes: List<com.adischat.app.feature.shop.data.ShopCategoryAttributeDto>,
    products: List<ShopProductDto>,
    productDetails: Map<String, ShopProductDto>,
    loadingAttributes: Boolean,
    onDismiss: () -> Unit,
    onApply: (ShopHomeFilters) -> Unit,
    onReset: () -> Unit,
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    val minimumPrice = draft.minimumPriceEtb.toIntOrNull()
    val maximumPrice = draft.maximumPriceEtb.toIntOrNull()
    val priceRangeValid = minimumPrice == null || maximumPrice == null || maximumPrice >= minimumPrice
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ShopBg) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Filter products", color = ShopInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (categories.isNotEmpty()) {
                MarketplaceCategoryFilter(
                    categories = categories,
                    selectedSlug = draft.categorySlug,
                    onSelect = { category ->
                        draft = draft.copy(categorySlug = category?.slug, categoryValues = emptyMap())
                    },
                )
            }
            if (draft.categorySlug != null) {
                if (loadingAttributes) {
                    Text("Loading category filters...", color = ShopMuted, fontSize = 13.sp)
                } else {
                    attributes.forEach { attribute ->
                        val values = buyerFilterValues(attribute.code, products, productDetails)
                        if (values.isNotEmpty()) {
                            Text(attribute.displayName.ifBlank { attribute.name }, color = ShopInk, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(values, key = { it }) { value ->
                                    FilterPill(value, draft.categoryValues[attribute.code] == value) {
                                        val updated = draft.categoryValues.toMutableMap()
                                        if (updated[attribute.code] == value) updated.remove(attribute.code) else updated[attribute.code] = value
                                        draft = draft.copy(categoryValues = updated)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text("Availability", color = ShopInk, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("In stock", draft.inStockOnly) { draft = draft.copy(inStockOnly = !draft.inStockOnly) }
                FilterPill("Discounted", draft.discountedOnly) { draft = draft.copy(discountedOnly = !draft.discountedOnly) }
            }
            Text("Price (ETB)", color = ShopInk, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.minimumPriceEtb,
                    onValueChange = { value -> draft = draft.copy(minimumPriceEtb = value.filter(Char::isDigit).take(8)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Minimum") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = shopOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = draft.maximumPriceEtb,
                    onValueChange = { value -> draft = draft.copy(maximumPriceEtb = value.filter(Char::isDigit).take(8)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Maximum") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = shopOutlinedTextFieldColors(),
                )
            }
            if (!priceRangeValid) {
                Text("Maximum price must be greater than or equal to minimum price.", color = ShopRed, fontSize = 12.sp)
            }
            Text("Rating", color = ShopInk, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(0 to "Any rating", 4 to "4 stars & above", 3 to "3 stars & above"), key = { it.first }) { option ->
                    FilterPill(option.second, draft.minimumRating == option.first) { draft = draft.copy(minimumRating = option.first) }
                }
            }
            Text("Sort by", color = ShopInk, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Newest", "Price low to high", "Price high to low", "Best rated", "Most sold"), key = { it }) { sort ->
                    FilterPill(sort, draft.sort == sort) { draft = draft.copy(sort = sort) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Text("Reset", color = ShopInk)
                }
                Button(onClick = { onApply(draft) }, enabled = priceRangeValid, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) {
                    Text("Show results", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
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
internal fun PromoCarousel(banners: List<ShopBannerDto>, fallbackImageUrl: String?, onExplore: () -> Unit) {
    if (banners.size == 1) {
        PromoHero(banners.first(), fallbackImageUrl, onExplore)
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(banners, key = { it.id }) { banner ->
                PromoHero(banner, fallbackImageUrl, onExplore, Modifier.fillParentMaxWidth())
            }
        }
    }
}

@Composable
private fun PromoHero(banner: ShopBannerDto, fallbackImageUrl: String?, onExplore: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(136.dp).clip(RoundedCornerShape(12.dp)).background(ShopGreenDark).clickable(onClick = onExplore)) {
        val imageUrl = banner.imageUrl?.takeIf(String::isNotBlank) ?: fallbackImageUrl
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = banner.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.43f),
            )
        }
        Column(
            Modifier.fillMaxHeight().fillMaxWidth(.64f).background(ShopGreenDark).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.width(22.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(ShopGold))
            Spacer(Modifier.height(4.dp))
            Text(productionBannerTitle(banner.title), color = ShopOnColor, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            banner.subtitle?.takeIf(String::isNotBlank)?.let {
                Text(productionBannerSubtitle(it), color = ShopOnColor.copy(alpha = .84f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(5.dp))
            Text("Explore  >", color = ShopGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TrustBenefits() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(Icons.Default.LocalShipping, "Delivery support", "Where sellers support it"),
            Triple(Icons.Default.Security, "Secure checkout", "Protected AdisChat identity"),
            Triple(Icons.Default.Verified, "Approved sellers", "Approved marketplace stores"),
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
private fun CategoryShortcutRow(categories: List<ShopCategoryDto>, onCategory: (ShopCategoryDto) -> Unit) {
    if (categories.isEmpty()) {
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(categories, key = { it.id }) { category ->
            CategoryShortcut(category.name, categoryIcon(category), categoryTone(category), ShopInk) { onCategory(category) }
        }
    }
}

@Composable
internal fun CategoryRail(categories: List<ShopCategoryDto>, onCategory: (ShopCategoryDto) -> Unit) {
    val roots = categories.filter { it.parentId == null }
    if (roots.isEmpty()) return
    SectionHeader("Popular categories")
    CategoryShortcutRow(roots, onCategory)
}

@Composable
private fun CompactHomeCategoryRail(categories: List<ShopCategoryDto>, onCategory: (ShopCategoryDto) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Shop by category")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemWidth = (maxWidth - 24.dp) / 4
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { category ->
                    CategoryShortcut(category.name, categoryIcon(category), ShopGreenSoft, ShopGreen, itemWidth, category.imageUrl) {
                        onCategory(category)
                    }
                }
            }
        }
    }
}

internal fun productionBannerTitle(value: String): String =
    if (value.contains("demo", ignoreCase = true) || value.contains("development", ignoreCase = true)) {
        "Find what you need on AdisGebeya"
    } else value

internal fun productionBannerSubtitle(value: String): String =
    if (value.contains("demo", ignoreCase = true) || value.contains("development", ignoreCase = true)) {
        "Explore products from marketplace stores."
    } else value

@Composable
private fun TopCategoryRail(categories: List<ShopCategoryDto>, onCategory: (ShopCategoryDto) -> Unit) {
    val roots = categories.filter { it.parentId == null }
    if (roots.isEmpty()) return
    SectionHeader("Top categories")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(roots, key = { it.id }) { category ->
            CategoryShortcut(category.name, categoryIcon(category), categoryTone(category), ShopInk) { onCategory(category) }
        }
    }
}

@Composable
private fun CategoryShortcut(
    label: String,
    icon: ImageVector,
    background: Color,
    tint: Color,
    itemWidth: androidx.compose.ui.unit.Dp = 82.dp,
    imageUrl: String? = null,
    onClick: () -> Unit,
) {
    Column(Modifier.width(itemWidth).heightIn(min = 88.dp).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            if (imageUrl.isNullOrBlank()) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(25.dp))
            } else {
                AsyncImage(model = imageUrl, contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = ShopInk, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, maxLines = if (LocalDensity.current.fontScale > 1.1f) 3 else 2, overflow = TextOverflow.Ellipsis)
    }
}

internal fun categoryTone(category: ShopCategoryDto): Color {
    val key = category.name.lowercase()
    return when {
        "beauty" in key -> ShopPinkSoft
        "home" in key -> ShopAmberSoft
        "fashion" in key -> ShopPurpleSoft
        "elect" in key -> ShopBlueSoft
        "book" in key || "media" in key -> ShopBlueSoft
        "sport" in key || "outdoor" in key -> ShopAmberSoft
        "jewel" in key || "watch" in key -> ShopPinkSoft
        "toy" in key || "baby" in key -> ShopPurpleSoft
        else -> ShopGreenSoft
    }
}

@Composable
internal fun ProductRail(
    title: String,
    products: List<ShopProductDto>,
    onProduct: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
    savedIds: Set<String>,
    compact: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.id }) { product ->
                ProductCard(product, Modifier.width(if (compact) 156.dp else 168.dp), onProduct, onToggleFavorite, product.id in savedIds, compact = compact)
            }
        }
    }
}

@Composable
internal fun ProductGridSection(
    title: String?,
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!title.isNullOrBlank()) SectionHeader(title)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= 900.dp -> 4
                maxWidth >= 600.dp -> 3
                else -> 2
            }
            val columnSpacing = 8.dp
            val rowSpacing = 8.dp
            val cardWidth = (maxWidth - columnSpacing * (columns - 1)) / columns
            val rowHeights = visibleProducts.chunked(columns).map { row ->
                val contentHeight = row.maxOf { product ->
                    88 + (if ((product.reviewCount ?: 0) > 0 || (product.soldCount ?: 0) > 0) 15 else 0) +
                        (if (homePreviousPriceMinor(product) != null) 14 else 0)
                }
                cardWidth + contentHeight.dp
            }
            val gridHeight = rowHeights.fold(0.dp) { total, height -> total + height } +
                rowSpacing * (rowHeights.size - 1).coerceAtLeast(0)
            val heightsById = visibleProducts.mapIndexed { index, product -> product.id to rowHeights[index / columns] }.toMap()
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxWidth().height(gridHeight),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(columnSpacing),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                items(visibleProducts, key = { it.id }) { product ->
                    ProductCard(product, Modifier.fillMaxWidth(), onProduct, onToggleFavorite, product.id in savedIds, cardHeight = heightsById.getValue(product.id))
                }
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
    cardHeight: androidx.compose.ui.unit.Dp = if (compact) 272.dp else 296.dp,
) {
    Card(
        modifier = modifier
            .height(cardHeight)
            .clickable { onProduct(product) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShopSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine.copy(alpha = .82f)),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(ShopSoft)) {
                val imageUrl = product.images
                    ?.sortedWith(
                        compareByDescending<ShopProductImageDtoSafe> { it.isPrimary == true }
                            .thenBy { it.displayOrder ?: 0 }
                    )
                    .orEmpty()
                    .firstOrNull()
                    ?.imageUrl
                ShopAssetImage(imageUrl, product.title, Modifier.fillMaxSize(), iconSize = 42.dp, contentScale = ContentScale.Fit)
                if (verifiedDiscountPercent(product) != null) {
                    Surface(Modifier.align(Alignment.TopStart).padding(6.dp), color = ShopRed, shape = RoundedCornerShape(999.dp)) {
                        Text("-${verifiedDiscountPercent(product)}%", color = ShopOnColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                if (shouldShowProductStock(product.stockStatus)) {
                    Surface(Modifier.align(Alignment.BottomStart).padding(8.dp), color = stockStatusBg(product.stockStatus), shape = RoundedCornerShape(999.dp), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                        Text(product.stockStatus.orEmpty().replace("_", " "), color = stockStatusColor(product.stockStatus), fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 1)
                    }
                }
                IconButton(onClick = { onToggleFavorite(product) }, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(42.dp).clip(CircleShape).background(ShopOnColor.copy(alpha = .92f))) {
                    Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = if (saved) "Remove from favorites" else "Add to favorites", tint = if (saved) ShopRed else ShopInk, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(product.title, color = ShopInk, fontSize = if (compact) 12.sp else 13.sp, lineHeight = if (compact) 15.sp else 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                productCompactHighlight(product)?.takeUnless { it.equals("none", ignoreCase = true) }?.let { highlight ->
                    Text(
                        highlight,
                        color = ShopMuted,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val hasReviews = (product.reviewCount ?: 0) > 0
                val hasSales = (product.soldCount ?: 0) > 0
                if (hasReviews || hasSales) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(15.dp)) {
                        if (hasReviews) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = ShopGold, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("${"%.1f".format(product.averageRating ?: 0.0)} (${product.reviewCount})", color = ShopMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                        if (hasReviews && hasSales) Text("  •  ", color = ShopMuted, fontSize = 11.sp)
                        if (hasSales) Text("${product.soldCount} sold", color = ShopMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
                    val formattedPrice = formatEtb(product.priceMinor)
                    Text(formattedPrice, color = ShopGreen, fontSize = if (formattedPrice.length > 14) 12.sp else if (compact) 14.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    homePreviousPriceMinor(product)?.let { Text(formatEtb(it), color = ShopMuted, fontSize = 11.sp, textDecoration = TextDecoration.LineThrough) }
                }
            }
        }
    }
}

@Composable
internal fun MiniBadge(label: String, background: Color, content: Color) {
    Surface(color = background, shape = RoundedCornerShape(999.dp)) {
        Text(label, color = content, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

private fun statusKey(status: String?): String = status.orEmpty().trim().lowercase()

internal fun backendLabel(value: String?): String = when (statusKey(value)) {
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
    "expired" -> "Seller response expired"
    "authorization_void_pending" -> "Authorization cancellation pending"
    "refund_pending" -> "Refund initiated"
    "refund_processing" -> "Refund processing"
    "refund_failed" -> "Refund needs support"
    "" -> ""
    else -> statusKey(value)
        .replace("_", " ")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun orderStatusLabel(status: String?): String = backendLabel(status).ifBlank { "Created" }

internal fun fulfillmentStatusLabel(status: String?): String = backendLabel(status).ifBlank { "Awaiting seller confirmation" }

internal fun paymentStatusLabel(status: String?): String = backendLabel(status).ifBlank { "Payment not recorded" }

internal fun paymentMethodLabel(value: String?): String = backendLabel(value).ifBlank { "Payment method" }

internal fun issueStatusLabel(status: String?): String = when (statusKey(status)) {
    "open" -> "Open"
    "under_review" -> "Under review"
    "awaiting_buyer" -> "Waiting for buyer"
    "awaiting_seller" -> "Waiting for seller"
    "resolved" -> "Resolved"
    "rejected" -> "Not approved"
    "closed" -> "Closed"
    else -> backendLabel(status).ifBlank { "Open" }
}

internal fun buyerFilterValues(
    code: String,
    products: List<ShopProductDto>,
    productDetails: Map<String, ShopProductDto>,
): List<String> = products.asSequence()
    .mapNotNull { productDetails[it.id] }
    .flatMap { detail ->
        sequence {
            detail.variants.filter { it.status == "active" && it.availableQuantity > 0 }.forEach { variant ->
                variant.options[code]?.takeIf(String::isNotBlank)?.let { yield(it) }
            }
            detail.specifications.filter { it.code == code && detail.stockStatus?.contains("out", ignoreCase = true) != true }.forEach { specification ->
                specification.valueText?.takeIf(String::isNotBlank)?.let { yield(it) }
                specification.valueNumber?.let { yield(it.toString()) }
                specification.valueBoolean?.let { yield(if (it) "Yes" else "No") }
            }
        }
    }
    .distinct()
    .sortedWith(String.CASE_INSENSITIVE_ORDER)
    .toList()

internal fun ShopProductDto.matchesBuyerFilter(code: String, value: String): Boolean =
    variants.any { it.status == "active" && it.availableQuantity > 0 && it.options[code] == value } ||
        specifications.any {
            it.code == code && listOfNotNull(
                it.valueText,
                it.valueNumber?.toString(),
                it.valueBoolean?.let { boolean -> if (boolean) "Yes" else "No" },
            ).contains(value)
        }

internal fun issueReasonLabel(reason: String?): String = when (statusKey(reason)) {
    "item_not_received" -> "Item not received"
    "wrong_item" -> "Wrong item received"
    "damaged_item" -> "Item arrived damaged"
    "incomplete_order" -> "Incomplete order"
    "not_as_described" -> "Item not as described"
    "seller_issue" -> "Seller issue"
    "delivery_issue" -> "Delivery issue"
    "other" -> "Other issue"
    else -> backendLabel(reason).ifBlank { "Order issue" }
}

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

private fun shouldShowProductStock(status: String?): Boolean {
    val value = statusKey(status)
    return value.isNotBlank() && value !in setOf("in_stock", "available", "active")
}

internal fun orderStatusBg(status: String?): Color = when (statusKey(status)) {
    "created", "pending", "payment_pending", "awaiting_seller_confirmation" -> ShopAmberSoft
    "confirmed", "accepted" -> ShopBlueSoft
    "processing" -> ShopGreenSoft
    "packed" -> ShopPurpleSoft
    "shipped", "handed_to_carrier", "out_for_delivery" -> ShopAmberSoft
    "delivered", "completed" -> ShopGreenSoft
    "cancelled", "canceled", "rejected", "expired" -> ShopRedSoft
    "refunded" -> ShopSoft
    else -> ShopSoft
}

internal fun orderStatusColor(status: String?): Color = when (statusKey(status)) {
    "created", "pending", "payment_pending", "awaiting_seller_confirmation" -> ShopOrangeDark
    "confirmed", "accepted" -> ShopBlue
    "processing" -> ShopGreen
    "packed" -> ShopPurple
    "shipped", "handed_to_carrier", "out_for_delivery" -> ShopOrangeDark
    "delivered", "completed" -> ShopGreen
    "cancelled", "canceled", "rejected", "expired" -> ShopRed
    "refunded" -> ShopMuted
    else -> ShopMuted
}

internal fun fulfillmentStatusBg(status: String?): Color = orderStatusBg(status)

internal fun fulfillmentStatusColor(status: String?): Color = orderStatusColor(status)

internal fun paymentStatusBg(status: String?): Color = when (statusKey(status)) {
    "paid", "settled", "released" -> ShopGreenSoft
    "unpaid", "pending", "payment_pending" -> ShopAmberSoft
    "failed", "cancelled", "canceled", "refunded", "refund_failed" -> ShopRedSoft
    "refund_pending", "refund_processing", "authorization_void_pending" -> ShopAmberSoft
    else -> ShopSoft
}

internal fun paymentStatusColor(status: String?): Color = when (statusKey(status)) {
    "paid", "settled", "released" -> ShopGreen
    "unpaid", "pending", "payment_pending" -> ShopOrangeDark
    "failed", "cancelled", "canceled", "refunded", "refund_failed" -> ShopRed
    "refund_pending", "refund_processing", "authorization_void_pending" -> ShopOrangeDark
    else -> ShopMuted
}

internal fun trackingStatusColor(status: String?): Color = when (statusKey(status)) {
    "created", "pending", "payment_pending", "awaiting_seller_confirmation" -> ShopOrange
    "confirmed", "accepted" -> ShopBlue
    "processing" -> ShopGreen
    "packed" -> ShopPurple
    "shipped", "handed_to_carrier", "out_for_delivery" -> ShopOrange
    "delivered", "completed" -> ShopGreen
    "cancelled", "canceled", "rejected", "expired", "failed" -> ShopRed
    else -> ShopMuted
}

@Composable
internal fun ShopAssetImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier,
    iconSize: androidx.compose.ui.unit.Dp,
    contentScale: ContentScale = ContentScale.Crop,
) {
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
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private typealias ShopProductImageDtoSafe = com.adischat.app.feature.shop.data.ShopProductImageDto

private fun ShopProductDto.toStoreDto(): ShopStoreDto {
    val current = store
    return ShopStoreDto(
        id = current?.id.orEmpty(),
        name = current?.name ?: "Marketplace store",
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

internal fun com.adischat.app.feature.shop.data.ShopOrderAddressSnapshotDto.formatAddress(): String =
    listOfNotNull(addressLine1, addressLine2, city)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(", ")

@Composable
internal fun RowScope.MiniStat(label: String, value: String) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(ShopSoft).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(label, color = ShopMuted, fontSize = 11.sp)
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ShopMuted, fontWeight = FontWeight.Medium)
        Text(value, color = ShopInk, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CheckoutScreen(
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onLoadAddresses: () -> Unit,
    onSaveAddress: (ShopAddressRequest, (String?) -> Unit) -> Unit,
    onConfirm: (String, String, String) -> Unit,
    onApplyCoupon: (String) -> Unit = {},
    onApplyVoucher: (String) -> Unit = {},
    onRemoveDiscount: (String) -> Unit = {},
) {
    val checkout = protectedState.checkout
    var addressFlow by remember(checkout?.id) { mutableStateOf(CheckoutAddressFlow(checkout?.id.orEmpty())) }
    val eligiblePaymentMethods = remember(checkout?.paymentMethods) {
        checkout?.paymentMethods?.filter {
            val code = it.code.lowercase().trim()
            code != "cash_on_delivery" && code != "cod"
        }.orEmpty()
    }
    var selectedPaymentMethod by remember { mutableStateOf("") }
    var couponInput by remember { mutableStateOf("") }
    var voucherInput by remember { mutableStateOf("") }
    var couponExpanded by remember { mutableStateOf(!checkout?.couponCode.isNullOrBlank()) }
    var showAddressSelector by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onLoadAddresses() }
    LaunchedEffect(protectedState.addresses, checkout) {
        if (addressFlow.selectedAddressId.isBlank()) {
            val defaultId = protectedState.addresses.firstOrNull { it.isDefault }?.id ?: protectedState.addresses.firstOrNull()?.id.orEmpty()
            if (defaultId.isNotBlank()) addressFlow = addressFlow.select(defaultId)
        }
        if (checkout != null && (checkout.isFullyCoveredByVoucher || (checkout.remainingPayableMinor == 0 && checkout.voucherAppliedMinor > 0))) {
            selectedPaymentMethod = "voucher_prepaid"
        } else if (selectedPaymentMethod.isBlank() || selectedPaymentMethod == "voucher_prepaid" || selectedPaymentMethod.lowercase() in setOf("cash_on_delivery", "cod")) {
            selectedPaymentMethod = eligiblePaymentMethods.firstOrNull { it.code != "voucher_prepaid" }?.code
                ?: eligiblePaymentMethods.firstOrNull()?.code.orEmpty()
        }
    }
    LaunchedEffect(checkout?.couponCode) {
        if (!checkout?.couponCode.isNullOrBlank()) couponExpanded = true
    }

    LazyColumn(
        Modifier.fillMaxSize().background(ShopBg).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ShopInk) }
                Text("Checkout", color = ShopInk, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (protectedState.loading && checkout == null) {
            item { SkeletonCard() }
        }
        if (checkout == null) {
            protectedState.error?.let { item { ErrorCardMessage(it) } }
        }
        if (checkout != null) {
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Delivery address", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            if (protectedState.addresses.isNotEmpty() && !addressFlow.formOpen) {
                                TextButton(
                                    onClick = { showAddressSelector = !showAddressSelector },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        if (showAddressSelector) "Done" else "Change",
                                        color = ShopGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        if (protectedState.addresses.isEmpty()) {
                            Text("Add a delivery address before confirming checkout.", color = ShopMuted, lineHeight = 18.sp, fontSize = 13.sp)
                            if (!addressFlow.formOpen) {
                                OutlinedButton(
                                    onClick = { addressFlow = addressFlow.open() },
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add delivery address", fontSize = 13.sp)
                                }
                            }
                        } else if (!showAddressSelector) {
                            val activeAddress = protectedState.addresses.firstOrNull { it.id == addressFlow.selectedAddressId }
                                ?: protectedState.addresses.firstOrNull { it.isDefault }
                                ?: protectedState.addresses.first()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ShopSoft)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = ShopGreen,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(activeAddress.label.ifBlank { "Delivery Address" }, color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                        if (activeAddress.isDefault) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(ShopGreenSoft)
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text("Default", color = ShopGreen, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text("${activeAddress.recipientName} • ${activeAddress.phone}", color = ShopMuted, fontSize = 12.sp)
                                    Text("${activeAddress.addressLine1}, ${activeAddress.city}", color = ShopMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                protectedState.addresses.forEach { address ->
                                    val selected = addressFlow.selectedAddressId == address.id
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) ShopGreenSoft else ShopSoft)
                                            .clickable {
                                                addressFlow = addressFlow.select(address.id)
                                                showAddressSelector = false
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(address.label.ifBlank { "Address" }, color = ShopInk, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                            Text("${address.recipientName} • ${address.phone}", color = ShopMuted, fontSize = 11.5.sp)
                                            Text("${address.addressLine1}, ${address.city}", color = ShopMuted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (!addressFlow.formOpen) {
                                    OutlinedButton(
                                        onClick = { addressFlow = addressFlow.open() },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add new address", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (addressFlow.formOpen) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddressForm(initial = null, busy = addressFlow.saving, onSave = { request ->
                        val savingCheckoutId = addressFlow.checkoutId
                        addressFlow = addressFlow.beginSave()
                        onSaveAddress(request) { savedId ->
                            addressFlow = addressFlow.completeSave(savingCheckoutId, savedId)
                        }
                    })
                    TextButton(onClick = { addressFlow = addressFlow.cancel() }, enabled = !addressFlow.saving) { Text("Cancel") }
                    protectedState.error?.let { ErrorCardMessage(it) }
                }
            }
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Discounts & Vouchers", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)

                        // 1. Prepaid Store Voucher (Visible by default)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Prepaid Store Voucher", color = ShopInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                if (checkout.voucherAppliedMinor > 0) {
                                    Text("Applied", color = ShopGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!checkout.voucherCode.isNullOrBlank()) {
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ShopGreenSoft).padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("${checkout.voucherCode} (- ${formatEtb(checkout.voucherAppliedMinor)})", color = ShopGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    TextButton(onClick = { onRemoveDiscount("voucher") }, contentPadding = PaddingValues(0.dp)) {
                                        Text("Remove", color = ShopMuted, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = voucherInput,
                                        onValueChange = { voucherInput = it.uppercase() },
                                        placeholder = { Text("Enter voucher code", fontSize = 13.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ShopGreen,
                                            unfocusedBorderColor = ShopLine,
                                        ),
                                    )
                                    Button(
                                        onClick = {
                                            onApplyVoucher(voucherInput.trim())
                                            voucherInput = ""
                                        },
                                        enabled = voucherInput.isNotBlank() && !protectedState.loading,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)
                                    ) {
                                        Text("Redeem", fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = ShopLine)

                        // 2. Coupon Section (Collapsible)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!checkout.couponCode.isNullOrBlank()) {
                                Text("Discount Coupon", color = ShopInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ShopGreenSoft).padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("${checkout.couponCode} (- ${formatEtb(checkout.couponDiscountMinor)})", color = ShopGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    TextButton(onClick = { onRemoveDiscount("coupon") }, contentPadding = PaddingValues(0.dp)) {
                                        Text("Remove", color = ShopMuted, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { couponExpanded = !couponExpanded }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocalOffer,
                                            contentDescription = null,
                                            tint = ShopGreen,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            "Have a coupon?",
                                            color = ShopInk,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                    Icon(
                                        imageVector = if (couponExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (couponExpanded) "Collapse" else "Expand",
                                        tint = ShopMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                if (couponExpanded) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = couponInput,
                                            onValueChange = { couponInput = it.uppercase() },
                                            placeholder = { Text("Enter coupon code", fontSize = 13.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = ShopGreen,
                                                unfocusedBorderColor = ShopLine,
                                            ),
                                        )
                                        Button(
                                            onClick = {
                                                onApplyCoupon(couponInput.trim())
                                                couponInput = ""
                                            },
                                            enabled = couponInput.isNotBlank() && !protectedState.loading,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)
                                        ) {
                                            Text("Apply", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Order summary", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        InfoRow("Subtotal", formatEtb(checkout.subtotalMinor))
                        if (checkout.couponDiscountMinor > 0) {
                            InfoRow("Coupon discount (${checkout.couponCode.orEmpty()})", "- " + formatEtb(checkout.couponDiscountMinor))
                        }
                        if (checkout.voucherAppliedMinor > 0) {
                            InfoRow("Voucher applied (${checkout.voucherCode.orEmpty()})", "- " + formatEtb(checkout.voucherAppliedMinor))
                        }
                        InfoRow("Shipping", if (checkout.shippingMinor > 0) formatEtb(checkout.shippingMinor) else "Not configured")
                        InfoRow("Tax", formatEtb(checkout.taxMinor))
                        HorizontalDivider(color = ShopLine, modifier = Modifier.padding(vertical = 2.dp))
                        val payableMinor = if (checkout.isFullyCoveredByVoucher) 0 else if (checkout.remainingPayableMinor > 0) checkout.remainingPayableMinor else checkout.grandTotalMinor
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ShopGreenSoft)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total payable", color = ShopInk, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text(formatEtb(payableMinor), color = ShopGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        if (checkout.isFullyCoveredByVoucher || (payableMinor == 0 && checkout.voucherAppliedMinor > 0)) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ShopGreenSoft).padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ShopGreen, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("100% Covered by Voucher — No payment due", color = ShopGreen, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                            }
                        }
                        checkout.expiresAt?.let {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = ShopMuted, modifier = Modifier.size(13.dp))
                                Text("Reserved until ${formatMarketplaceDateTime(it)}", color = ShopMuted, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(ShopSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Payment method", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (checkout.isFullyCoveredByVoucher || (checkout.remainingPayableMinor == 0 && checkout.voucherAppliedMinor > 0)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ShopGreenSoft)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Prepaid Store Voucher", color = ShopInk, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text("Order will be confirmed automatically with zero additional charge.", color = ShopMuted, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ShopGreen)
                            }
                        } else if (eligiblePaymentMethods.isEmpty()) {
                            ErrorCardMessage("No authorized online payment method is enabled. Online payment gateways are pending activation and Cash on Delivery is disabled by marketplace policy.")
                        } else {
                            eligiblePaymentMethods.forEach { method ->
                                val selected = selectedPaymentMethod == method.code
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
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

                        // Upcoming online payment options (Telebirr & Chapa)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Upcoming online payment options", color = ShopMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Telebirr Card (Coming Soon)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ShopSoft)
                                        .border(1.dp, ShopLine, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Telebirr", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(ShopLine)
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text("Coming Soon", color = ShopMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                        Text("Direct mobile wallet", color = ShopMuted, fontSize = 10.5.sp)
                                    }
                                }
                                // Chapa Card (Coming Soon)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ShopSoft)
                                        .border(1.dp, ShopLine, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Chapa", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(ShopLine)
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text("Coming Soon", color = ShopMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                        Text("Cards & Bank transfer", color = ShopMuted, fontSize = 10.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val canPlaceOrder = addressFlow.selectedAddressId.isNotBlank() &&
                (checkout.isFullyCoveredByVoucher || (checkout.remainingPayableMinor == 0 && checkout.voucherAppliedMinor > 0) || (selectedPaymentMethod.isNotBlank() && eligiblePaymentMethods.any { it.code == selectedPaymentMethod })) &&
                !addressFlow.formOpen &&
                !protectedState.loading

            item {
                Button(
                    onClick = { onConfirm(checkout.id, addressFlow.selectedAddressId, selectedPaymentMethod) },
                    enabled = canPlaceOrder,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShopGreen, disabledContainerColor = ShopLine, disabledContentColor = ShopMuted),
                ) {
                    Text(if (protectedState.loading) "Placing order..." else "Place order", fontWeight = FontWeight.SemiBold)
                }
            }
            protectedState.checkoutOrder?.let { order ->
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopGreenSoft)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Order created", color = ShopInk, fontWeight = FontWeight.SemiBold)
                            Text("Order ${order.orderNumber ?: order.id.takeLast(8)} • Payment: ${order.paymentStatus} • Status: ${order.status.ifBlank { "created" }}", color = ShopInk, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun ErrorCardMessage(message: String) {
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
    ProductGridSection(
        title = null,
        products = products,
        onProduct = onProduct,
        onToggleFavorite = onToggleFavorite,
        savedIds = savedIds,
        maxItems = Int.MAX_VALUE,
    )
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
    var editingAddress by remember { mutableStateOf<ShopAddressDto?>(null) }
    ProtectedPage("Delivery Addresses", 0, onBack, onLoad, protectedState) {
        Button(
            onClick = {
                adding = !adding
                if (!adding) editingAddress = null
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ShopGreen),
        ) {
            Text(if (adding) "Close form" else "Add address", fontWeight = FontWeight.SemiBold)
        }
        if (adding) {
            AddressForm(
                initial = editingAddress,
                onSave = { request ->
                    onSave(request)
                    adding = false
                    editingAddress = null
                },
            )
        }
        if (protectedState.addresses.isEmpty()) EmptyCard("No delivery addresses yet.")
        protectedState.addresses.forEach { address ->
            AddressCard(
                address = address,
                onEdit = {
                    editingAddress = address
                    adding = true
                },
                onDelete = onDelete,
                onDefault = onDefault,
            )
        }
    }
}

@Composable
private fun AddressForm(initial: ShopAddressDto?, busy: Boolean = false, onSave: (ShopAddressRequest) -> Unit) {
    var label by remember(initial?.id) { mutableStateOf(initial?.label ?: "Home") }
    var name by remember(initial?.id) { mutableStateOf(initial?.recipientName.orEmpty()) }
    var phone by remember(initial?.id) { mutableStateOf(initial?.phone.orEmpty()) }
    var city by remember(initial?.id) { mutableStateOf(initial?.city?.ifBlank { "Addis Ababa" } ?: "Addis Ababa") }
    var line1 by remember(initial?.id) { mutableStateOf(initial?.addressLine1.orEmpty()) }
    var line2 by remember(initial?.id) { mutableStateOf(initial?.addressLine2.orEmpty()) }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(ShopOnColor), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(label, { label = it }, label = { Text("Label") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = shopOutlinedTextFieldColors())
            OutlinedTextField(name, { name = it }, label = { Text("Recipient name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = shopOutlinedTextFieldColors())
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = shopOutlinedTextFieldColors())
            OutlinedTextField(city, { city = it }, label = { Text("City") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = shopOutlinedTextFieldColors())
            OutlinedTextField(line1, { line1 = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), colors = shopOutlinedTextFieldColors())
            OutlinedTextField(line2, { line2 = it }, label = { Text("Landmark optional") }, modifier = Modifier.fillMaxWidth(), colors = shopOutlinedTextFieldColors())
            Button(
                onClick = {
                    onSave(
                        ShopAddressRequest(
                            id = initial?.id,
                            label = label,
                            recipientName = name,
                            phone = phone,
                            city = city,
                            addressLine1 = line1,
                            addressLine2 = line2.ifBlank { null },
                            isDefault = initial?.isDefault ?: false,
                        ),
                    )
                },
                enabled = !busy && label.isNotBlank() && name.isNotBlank() && phone.isNotBlank() && city.isNotBlank() && line1.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShopGreen),
            ) { Text(if (busy) "Saving..." else if (initial == null) "Save address" else "Update address", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun AddressCard(address: ShopAddressDto, onEdit: () -> Unit, onDelete: (String) -> Unit, onDefault: (String) -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(ShopOnColor), border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(address.label.ifBlank { "Address" }, color = ShopInk, fontWeight = FontWeight.Black)
                if (address.isDefault) Text("Default", color = ShopGreen, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Text("${address.recipientName} • ${address.phone}", color = ShopMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${address.addressLine1}${address.addressLine2?.let { ", $it" } ?: ""}, ${address.city}", color = ShopInk, lineHeight = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(12.dp)) { Text("Edit") }
                if (!address.isDefault) {
                    Button(onClick = { onDefault(address.id) }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) { Text("Set default") }
                }
                TextButton(onClick = { onDelete(address.id) }) { Text("Delete", color = ShopRed) }
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
    onSubmitIssue: (String, String?, String, String) -> Unit,
    onSubmitProductReview: (String, String, Int, String, String) -> Unit,
    onSubmitStoreReview: (String, String, Int, String) -> Unit,
    onSubmitRefund: (String, String, Int, String, String) -> Unit,
    onSubmitExchange: (String, String, Int, String, String) -> Unit,
    onBuyAgain: (String) -> Unit,
    onLoadReceipt: (String) -> Unit,
    onClearReceipt: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf("All") }
    val tabs = listOf("All", "Pending", "Processing", "Shipped", "Delivered", "Cancelled")
    val filtered = protectedState.orders.filter { order ->
        when (selectedTab) {
            "Pending" -> order.fulfillmentStatus in listOf("awaiting_seller_confirmation", "created")
            "Processing" -> order.fulfillmentStatus in listOf("accepted", "processing", "packed")
            "Shipped" -> order.status == "shipped" || order.fulfillmentStatus in listOf("handed_to_carrier", "shipped", "out_for_delivery")
            "Delivered" -> order.status in listOf("delivered", "completed") || order.fulfillmentStatus == "delivered"
            "Cancelled" -> order.status in listOf("cancelled", "rejected") || order.fulfillmentStatus in listOf("cancelled", "rejected", "expired")
            else -> true
        }
    }
    ProtectedPage("My Orders", 0, onBack, onLoad, protectedState) {
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
                onRefresh = onLoad,
                onOpen = onOpen,
                onCancel = onCancel,
                onRevealCode = onRevealCode,
            )
        }
    }
}

@Composable
private fun BuyerOrderDetailRoute(
    orderId: String,
    orderGroupId: String?,
    protectedState: ShopProtectedState,
    vm: ShopViewModel,
    onBack: () -> Unit,
    onTracking: () -> Unit,
) {
    val order = protectedState.selectedOrder?.takeIf { selected ->
        selected.id == orderId || orderGroupId?.let { groupId -> selected.groups.orEmpty().any { it.id == groupId } } == true
    }
    ProtectedPage(
        title = "Order Details",
        cartCount = 0,
        onBack = onBack,
        onLoad = {
            if (orderId.isNotBlank()) vm.loadOrderDetail(orderId)
            else orderGroupId?.let(vm::loadOrderDetailForGroup)
        },
        protectedState = protectedState,
    ) {
        if (order == null) {
            EmptyCard("Loading order details...")
        } else {
            OrderDetailCard(
                order = order,
                deliveryCode = protectedState.deliveryCode?.takeIf { it.orderId == order.id },
                issueOptions = protectedState.issueOptions,
                issues = protectedState.selectedOrderIssues,
                actions = protectedState.selectedOrderActions ?: order.postDeliveryActions,
                productReviews = protectedState.selectedOrderReviews?.productReviews.orEmpty(),
                storeReviews = protectedState.selectedOrderReviews?.storeReviews.orEmpty(),
                refundRequests = protectedState.selectedRefundRequests,
                exchangeRequests = protectedState.selectedExchangeRequests,
                receipt = protectedState.selectedReceipt,
                busy = protectedState.busyIds.contains("issue-${order.id}"),
                onRefresh = { vm.loadOrderDetail(order.id) },
                onTracking = onTracking,
                onRevealCode = vm::revealDeliveryCode,
                onSubmitIssue = vm::submitOrderIssue,
                onSubmitProductReview = vm::submitProductReview,
                onSubmitStoreReview = vm::submitStoreReview,
                onSubmitRefund = vm::submitRefundRequest,
                onSubmitExchange = vm::submitExchangeRequest,
                onBuyAgain = vm::buyAgainFromOrder,
                onLoadReceipt = vm::loadReceipt,
                onClearReceipt = vm::clearReceipt,
            )
        }
    }
}

@Composable
private fun OrderCard(
    order: ShopOrderDto,
    deliveryCode: com.adischat.app.feature.shop.data.ShopDeliveryCodeDto?,
    busy: Boolean,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onCancel: (String, String) -> Unit,
    onRevealCode: (String) -> Unit,
) {
    val groups = order.groups.orEmpty()
    val firstItem = groups.flatMap { it.items.orEmpty() }.firstOrNull()
    val itemCount = groups.flatMap { it.items.orEmpty() }.sumOf { it.quantity }
    val storeNames = groups.mapNotNull { it.storeNameSnapshot?.trim()?.takeIf(String::isNotEmpty) }.distinct()
    Card(
        onClick = { onOpen(order.id) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(ShopSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, ShopLine),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(ShopSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    ShopAssetImage(firstItem?.imageSnapshot, firstItem?.titleSnapshot ?: "Order item", Modifier.fillMaxSize(), iconSize = 24.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(order.orderNumber ?: "Order ${order.id.takeLast(8)}", color = ShopInk, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        storeNames.joinToString().ifBlank { "AdisGebeya seller" },
                        color = ShopMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$itemCount item${if (itemCount == 1) "" else "s"} • ${formatMarketplaceDateTime(order.createdAt)}",
                        color = ShopMuted,
                        fontSize = 11.sp,
                    )
                }
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Open order details", tint = ShopMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniBadge(orderStatusLabel(order.status), orderStatusBg(order.status), orderStatusColor(order.status))
                order.paymentStatus?.let { MiniBadge(paymentStatusLabel(it), paymentStatusBg(it), paymentStatusColor(it)) }
                order.fulfillmentStatus?.let { MiniBadge(fulfillmentStatusLabel(it), fulfillmentStatusBg(it), fulfillmentStatusColor(it)) }
            }
            if (order.paymentStatus in listOf("unpaid", "pending", "payment_pending")) {
                Text("Payment due on delivery", color = ShopMuted, fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(formatEtb(order.totalMinor), color = ShopGreen, fontWeight = FontWeight.SemiBold)
                Text("View details", color = ShopGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun FilterChipLike(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
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
private fun OrderDetailCard(
    order: ShopOrderDto,
    deliveryCode: com.adischat.app.feature.shop.data.ShopDeliveryCodeDto?,
    issueOptions: ShopIssueOptionsDto?,
    issues: List<ShopOrderIssueDto>,
    actions: ShopPostDeliveryActionsDto?,
    productReviews: List<ShopReviewDto>,
    storeReviews: List<ShopReviewDto>,
    refundRequests: List<ShopRefundRequestDto>,
    exchangeRequests: List<ShopExchangeRequestDto>,
    receipt: ShopReceiptDto?,
    busy: Boolean,
    onRefresh: () -> Unit,
    onTracking: () -> Unit,
    onRevealCode: (String) -> Unit,
    onSubmitIssue: (String, String?, String, String) -> Unit,
    onSubmitProductReview: (String, String, Int, String, String) -> Unit,
    onSubmitStoreReview: (String, String, Int, String) -> Unit,
    onSubmitRefund: (String, String, Int, String, String) -> Unit,
    onSubmitExchange: (String, String, Int, String, String) -> Unit,
    onBuyAgain: (String) -> Unit,
    onLoadReceipt: (String) -> Unit,
    onClearReceipt: () -> Unit,
) {
    var showIssueForm by remember(order.id) { mutableStateOf(false) }
    var actionDialog by remember(order.id) { mutableStateOf<String?>(null) }
    val firstItem = order.groups.orEmpty().flatMap { it.items.orEmpty() }.firstOrNull()
    val firstGroup = order.groups.orEmpty().firstOrNull()
    actionDialog?.let { action ->
        when (action) {
            "product_review" -> PostDeliveryTextDialog(
                title = "Write product review",
                bodyLabel = "Review",
                showTitle = true,
                onDismiss = { actionDialog = null },
                onSubmit = { rating, title, body ->
                    firstItem?.id?.let { onSubmitProductReview(order.id, it, rating, title, body) }
                    actionDialog = null
                },
            )
            "store_review" -> PostDeliveryTextDialog(
                title = "Rate store",
                bodyLabel = "Store feedback",
                onDismiss = { actionDialog = null },
                onSubmit = { rating, _, body ->
                    firstGroup?.id?.let { onSubmitStoreReview(order.id, it, rating, body) }
                    actionDialog = null
                },
            )
            "refund" -> PostDeliveryRequestDialog(
                title = "Request refund",
                onDismiss = { actionDialog = null },
                onSubmit = { reason, description ->
                    firstItem?.id?.let { onSubmitRefund(order.id, it, firstItem.quantity.coerceAtLeast(1), reason, description) }
                    actionDialog = null
                },
            )
            "exchange" -> PostDeliveryRequestDialog(
                title = "Request exchange",
                onDismiss = { actionDialog = null },
                onSubmit = { reason, description ->
                    firstItem?.id?.let { onSubmitExchange(order.id, it, firstItem.quantity.coerceAtLeast(1), reason, description) }
                    actionDialog = null
                },
            )
        }
    }
    order.groups.orEmpty().forEach { group ->
        SellerResponseDeadlineCard(
            group = group,
            audience = SellerResponseAudience.BUYER,
            onDeadlineReached = onRefresh,
        )
    }
    receipt?.let {
        ReceiptDialog(receipt = it, onDismiss = onClearReceipt)
    }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(ShopSurface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(order.orderNumber ?: "Order details", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniBadge(orderStatusLabel(order.status), orderStatusBg(order.status), orderStatusColor(order.status))
                order.paymentStatus?.let { MiniBadge(paymentStatusLabel(it), paymentStatusBg(it), paymentStatusColor(it)) }
                order.fulfillmentStatus?.let { MiniBadge(fulfillmentStatusLabel(it), fulfillmentStatusBg(it), fulfillmentStatusColor(it)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMarketplaceDateTime(order.createdAt), color = ShopMuted, fontSize = 12.sp)
                Text(formatEtb(order.totalMinor), color = ShopGreen, fontWeight = FontWeight.Bold)
            }
            order.groups.orEmpty().forEach { group ->
                HorizontalDivider(color = ShopLine)
                Text(group.storeNameSnapshot ?: "Marketplace seller", color = ShopInk, fontWeight = FontWeight.SemiBold)
                group.items.orEmpty().forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(ShopSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            ShopAssetImage(item.imageSnapshot, item.titleSnapshot, Modifier.fillMaxSize(), iconSize = 22.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.titleSnapshot.ifBlank { "Order item" }, color = ShopInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            item.variantDisplayLabel()?.let { Text(it, color = ShopMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Text("Quantity ${item.quantity}", color = ShopMuted, fontSize = 12.sp)
                        }
                        Text(formatEtb(item.lineTotalMinor), color = ShopInk, fontWeight = FontWeight.SemiBold)
                    }
                }
                group.payment?.let { payment ->
                    Text("Payment: ${marketplaceStatusLabel(payment.methodCode)} • ${paymentStatusLabel(payment.status)}", color = ShopMuted, fontSize = 12.sp)
                }
                group.deliveryAddress?.let { address ->
                    Text("Delivery", color = ShopInk, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(address.addressLine1, address.addressLine2, address.city).filter(String::isNotBlank).joinToString(", "),
                        color = ShopMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            PostDeliveryActionSection(
                order = order,
                actions = actions,
                productReviews = productReviews,
                storeReviews = storeReviews,
                refundRequests = refundRequests,
                exchangeRequests = exchangeRequests,
                onAction = { action ->
                    when (action) {
                        "buy_again" -> onBuyAgain(order.id)
                        "receipt" -> onLoadReceipt(order.id)
                        else -> actionDialog = action
                    }
                },
            )
            Card(
                onClick = onTracking,
                colors = CardDefaults.cardColors(Color(0xFFF1F0FF)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7C4F2)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color(0xFF1E1B4B))
                    Column(Modifier.weight(1f)) {
                        Text("Track order", color = ShopInk, fontWeight = FontWeight.SemiBold)
                        Text(marketplaceStatusLabel(order.fulfillmentStatus ?: order.status), color = ShopMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ArrowForward, contentDescription = "Open order tracking", tint = ShopMuted)
                }
            }
            order.groups.orEmpty().forEach { group ->
                HorizontalDivider(color = ShopLine)
                Text(group.storeNameSnapshot ?: "Seller group", color = ShopInk, fontWeight = FontWeight.SemiBold)
                Text("Fulfilment: ${fulfillmentStatusLabel(group.fulfillmentStatus)}", color = ShopMuted)
                group.shipmentCarrier?.let { Text("Carrier: $it", color = ShopMuted) }
                group.shipmentTrackingNumber?.let { Text("Tracking: $it", color = ShopMuted) }
            }
            BuyerDeliveryCodeSection(order, deliveryCode?.deliveryCode, false, onRevealCode)
            BuyerProtectionIssueSection(
                order = order,
                issueOptions = issueOptions,
                issues = issues,
                busy = busy,
                showForm = showIssueForm,
                onShowForm = { showIssueForm = true },
                onDismiss = { if (!busy) showIssueForm = false },
                onSubmit = { groupId, reasonCode, description ->
                    showIssueForm = false
                    onSubmitIssue(order.id, groupId, reasonCode, description)
                },
            )
        }
    }
}

internal fun com.adischat.app.feature.shop.data.ShopOrderItemDto.variantDisplayLabel(): String? =
    variantSnapshot?.options?.values?.filter(String::isNotBlank)?.takeIf(List<String>::isNotEmpty)?.joinToString(" • ")
        ?: variantSnapshot?.title?.takeIf(String::isNotBlank)

internal fun com.adischat.app.feature.shop.data.ShopOrderItemDto.quantityWithUnit(): String =
    "$quantity${unitSnapshot?.symbol?.takeIf(String::isNotBlank)?.let { " $it" } ?: ""}"

@Composable
private fun PostDeliveryActionSection(
    order: ShopOrderDto,
    actions: ShopPostDeliveryActionsDto?,
    productReviews: List<ShopReviewDto>,
    storeReviews: List<ShopReviewDto>,
    refundRequests: List<ShopRefundRequestDto>,
    exchangeRequests: List<ShopExchangeRequestDto>,
    onAction: (String) -> Unit,
) {
    val completed = actions?.isCompleted == true || order.status in listOf("delivered", "completed") || order.fulfillmentStatus == "delivered"
    if (!completed) return
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(ShopGreenSoft),
        border = androidx.compose.foundation.BorderStroke(1.dp, ShopGreen.copy(alpha = .16f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ShopGreen)
                Column {
                    Text("Completed order actions", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Reviews, requests and receipts are verified by the server.", color = ShopMuted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            if (productReviews.isNotEmpty() || storeReviews.isNotEmpty()) {
                Text("${productReviews.size} product review / ${storeReviews.size} store review", color = ShopGreen, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            if (refundRequests.isNotEmpty() || exchangeRequests.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    refundRequests.take(2).forEach {
                        RequestStatusRow("Refund", it.reasonLabel ?: backendLabel(it.reasonCode), it.status, it.buyerVisibleUpdate)
                    }
                    exchangeRequests.take(2).forEach {
                        RequestStatusRow("Exchange", it.reasonLabel ?: backendLabel(it.reasonCode), it.status, it.buyerVisibleUpdate)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill(
                    label = if (productReviews.isNotEmpty()) "Reviewed" else "Review",
                    enabled = productReviews.isEmpty() && actions?.canReviewProduct == true,
                    icon = if (productReviews.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Star,
                ) { onAction("product_review") }
                ActionPill(
                    label = if (storeReviews.isNotEmpty()) "Store rated" else "Rate Store",
                    enabled = storeReviews.isEmpty() && actions?.canReviewStore == true,
                    icon = if (storeReviews.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Reviews,
                ) { onAction("store_review") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill("Refund", actions?.canRequestRefund == true, Icons.Default.Security) { onAction("refund") }
                ActionPill("Exchange", actions?.canRequestExchange == true, Icons.Default.LocalShipping) { onAction("exchange") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill("Buy Again", actions?.canBuyAgain != false, Icons.Default.ShoppingCart) { onAction("buy_again") }
                ActionPill("Receipt", actions?.canViewReceipt != false, Icons.Default.ReceiptLong) { onAction("receipt") }
            }
        }
    }
}

@Composable
private fun RowScope.ActionPill(label: String, enabled: Boolean, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f).height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (enabled) ShopGreen else ShopMuted),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) ShopGreen.copy(alpha = .42f) else ShopLine),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RequestStatusRow(type: String, reason: String, status: String, buyerVisibleUpdate: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("$type request", color = ShopInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(reason, color = ShopMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            buyerVisibleUpdate?.takeIf { it.isNotBlank() }?.let { update ->
                Text(update, color = ShopInk, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        MiniBadge(backendLabel(status), orderStatusBg(status), orderStatusColor(status))
    }
}

@Composable
private fun PostDeliveryTextDialog(
    title: String,
    bodyLabel: String,
    showTitle: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: (Int, String, String) -> Unit,
) {
    var rating by remember { mutableStateOf(5) }
    var reviewTitle by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = ShopInk, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { value ->
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "$value stars",
                            tint = if (value <= rating) ShopOrange else ShopLine,
                            modifier = Modifier.size(32.dp).clickable { rating = value },
                        )
                    }
                }
                if (showTitle) {
                    OutlinedTextField(reviewTitle, { reviewTitle = it.take(120) }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), colors = shopOutlinedTextFieldColors())
                }
                OutlinedTextField(body, { body = it.take(800) }, label = { Text(bodyLabel) }, minLines = 3, modifier = Modifier.fillMaxWidth(), colors = shopOutlinedTextFieldColors())
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(rating, reviewTitle, body) }, enabled = body.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = ShopSurface,
    )
}

@Composable
private fun PostDeliveryRequestDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var reason by remember { mutableStateOf("not_as_described") }
    var description by remember { mutableStateOf("") }
    val reasons = listOf("not_as_described", "damaged_item", "wrong_item", "defective_item", "other")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = ShopInk, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reasons) { item -> FilterChipLike(backendLabel(item), reason == item) { reason = item } }
                }
                OutlinedTextField(description, { description = it.take(1000) }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth(), colors = shopOutlinedTextFieldColors())
                Text("Submitting a request does not move money automatically. The seller and support team will review it.", color = ShopMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(reason, description) }, enabled = description.isNotBlank() || reason != "other", colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = ShopSurface,
    )
}

@Composable
private fun ReceiptDialog(receipt: ShopReceiptDto, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Order receipt", color = ShopInk, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ReceiptRow("Order", receipt.orderNumber ?: "Order")
                ReceiptRow("Date", receipt.orderDate ?: "")
                receipt.payment?.methodCode?.let { ReceiptRow("Payment method", backendLabel(it)) }
                ReceiptRow("Payment", paymentStatusLabel(receipt.payment?.status ?: receipt.paymentStatus))
                ReceiptRow("Delivery", fulfillmentStatusLabel(receipt.fulfillmentStatus))
                receipt.completedAt?.let { ReceiptRow("Completed", it) }
                receipt.stores.forEach { store ->
                    HorizontalDivider(color = ShopLine)
                    Text(store.storeName ?: "Store", color = ShopInk, fontWeight = FontWeight.SemiBold)
                    store.items.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.title ?: "Product", color = ShopInk, fontWeight = FontWeight.Medium)
                            Text(
                                "${receiptVariantLabel(item.variant)} / Qty ${item.quantity}",
                                color = ShopMuted,
                                fontSize = 12.sp,
                            )
                            ReceiptRow("${item.quantity} x ${formatEtb(item.unitPriceMinor)}", formatEtb(item.lineTotalMinor))
                        }
                    }
                }
                HorizontalDivider(color = ShopLine)
                ReceiptRow("Subtotal", formatEtb(receipt.subtotalMinor))
                ReceiptRow("Delivery", formatEtb(receipt.deliveryFeeMinor))
                if (receipt.discountMinor > 0) ReceiptRow("Discount", "-${formatEtb(receipt.discountMinor)}")
                ReceiptRow("Total", formatEtb(receipt.totalMinor), strong = true)
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ShopGreen)) { Text("Done") } },
        containerColor = ShopSurface,
    )
}

private fun receiptVariantLabel(variant: Map<String, Any>?): String {
    val title = variant?.get("title") as? String
    if (!title.isNullOrBlank()) return title
    val options = variant?.get("options") as? Map<*, *>
    val optionLabel = options.orEmpty().entries.joinToString(", ") { (key, value) ->
        "${backendLabel(key.toString())}: $value"
    }
    return optionLabel.ifBlank { "Standard" }
}

@Composable
private fun ReceiptRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = ShopMuted, fontSize = 13.sp)
        Text(value.ifBlank { "Not set" }, color = ShopInk, fontWeight = if (strong) FontWeight.Black else FontWeight.Medium, fontSize = if (strong) 16.sp else 13.sp)
    }
}

@Composable
private fun BuyerProtectionIssueSection(
    order: ShopOrderDto,
    issueOptions: ShopIssueOptionsDto?,
    issues: List<ShopOrderIssueDto>,
    busy: Boolean,
    showForm: Boolean,
    onShowForm: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String?, String, String) -> Unit,
) {
    HorizontalDivider(color = ShopLine)
    Text("Buyer Protection", color = ShopInk, fontWeight = FontWeight.Black, fontSize = 18.sp)
    Text(
        issueOptions?.message?.ifBlank { "Support can review eligible marketplace orders." }
            ?: "Support can review eligible marketplace orders.",
        color = ShopMuted,
        lineHeight = 19.sp,
    )
    if (issues.isNotEmpty()) {
        issues.forEach { issue ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(ShopGreenSoft), border = androidx.compose.foundation.BorderStroke(1.dp, ShopGreen.copy(alpha = .22f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(issueStatusLabel(issue.status), color = ShopGreen, fontWeight = FontWeight.Black)
                    Text(issue.reasonLabel ?: issueReasonLabel(issue.reasonCode), color = ShopInk, fontWeight = FontWeight.SemiBold)
                    if (issue.description.isNotBlank()) Text(issue.description, color = ShopMuted, lineHeight = 18.sp)
                    issue.events.lastOrNull()?.publicMessage?.let { Text(it, color = ShopMuted, fontSize = 12.sp) }
                }
            }
        }
    }
    Button(
        onClick = onShowForm,
        enabled = issueOptions?.eligible == true && !busy,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ShopGreen),
    ) { Text(if (busy) "Submitting..." else "Report an issue") }
    if (showForm) {
        BuyerProtectionIssueDialog(
            order = order,
            options = issueOptions?.options.orEmpty(),
            busy = busy,
            onDismiss = onDismiss,
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun BuyerProtectionIssueDialog(
    order: ShopOrderDto,
    options: List<com.adischat.app.feature.shop.data.ShopIssueReasonOptionDto>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String?, String, String) -> Unit,
) {
    var selectedCode by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val selected = options.firstOrNull { it.code == selectedCode }
    val requiresDescription = selected?.requiresDescription == true
    val canSubmit = selected != null && (!requiresDescription || description.trim().length >= 10) && !busy
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { selected?.let { onSubmit(order.groups.orEmpty().firstOrNull()?.id, it.code, description) } },
                enabled = canSubmit,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShopGreen),
            ) { Text(if (busy) "Submitting..." else "Submit issue") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
        title = { Text("Report an issue", color = ShopInk, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(order.orderNumber ?: "Order ${order.id.takeLast(8)}", color = ShopMuted)
                options.forEach { option ->
                    Surface(
                        onClick = { selectedCode = option.code },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedCode == option.code) ShopGreenSoft else ShopSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedCode == option.code) ShopGreen.copy(alpha = .35f) else ShopLine),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedCode == option.code, onClick = { selectedCode = option.code })
                            Text(option.label.ifBlank { issueReasonLabel(option.code) }, color = ShopInk, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(1200) },
                    label = { Text(if (requiresDescription) "Description required" else "Description optional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = shopOutlinedTextFieldColors(),
                )
                Text("Support will review the issue. This does not automatically refund or cancel the order.", color = ShopMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        },
        containerColor = ShopSurface,
    )
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
internal fun ProtectedPage(
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
        item { PageTitle(title, "") }
        if (protectedState.loading) {
            item { SkeletonCard() }
        }
        protectedState.error?.let { message ->
            item { EmptyCard("Couldn't load this page. Check your connection and try again.") }
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
internal fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = ShopInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) Text(subtitle, color = ShopMuted, fontSize = 13.sp, fontWeight = FontWeight.Normal)
    }
}

@Composable
internal fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth().zIndex(1f),
        color = ShopBg,
        tonalElevation = 0.dp,
    ) {
        PremiumSectionHeader(
            title = title,
            actionLabel = action,
            onAction = onAction,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ShopInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(ShopLine.copy(alpha = .75f)))
    }
}

@Composable
internal fun SkeletonCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ShopSurface)
            .border(1.dp, ShopLine.copy(alpha = .72f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp)).background(ShopSoft))
        Box(Modifier.fillMaxWidth(.72f).height(13.dp).clip(RoundedCornerShape(999.dp)).background(ShopLine.copy(alpha = .82f)))
        Box(Modifier.fillMaxWidth(.46f).height(13.dp).clip(RoundedCornerShape(999.dp)).background(ShopLine.copy(alpha = .58f)))
    }
}

@Composable
internal fun ErrorCard(maintenance: Boolean, onRefresh: () -> Unit) {
    PremiumErrorState(
        title = if (maintenance) "AdisGebeya is temporarily unavailable" else "We couldn't load AdisGebeya",
        message = "Check your connection and try again.",
        onRetry = onRefresh,
    )
}

@Composable
internal fun EmptyCard(message: String) {
    PremiumEmptyState(title = "Nothing here yet", message = message, icon = Icons.Default.Storefront)
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

internal fun formatEtb(minor: Int): String = "ETB ${"%,d".format(minor / 100)}"

internal data class ProcessedSellerProductImage(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

internal fun processSellerProductImage(context: Context, uri: Uri): ProcessedSellerProductImage {
    val resolver = context.contentResolver
    val declaredType = resolver.getType(uri).orEmpty().lowercase()
    val isLikelyImage = declaredType.isBlank() || declaredType.startsWith("image/") || declaredType == "application/octet-stream"
    if (!isLikelyImage) {
        error("Use a valid photo image (JPEG, PNG, WebP or HEIC).")
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Image could not be read.")
    if (bounds.outWidth > 12000 || bounds.outHeight > 12000) error("Image dimensions are too large.")

    var sample = 1
    val maxDimension = max(bounds.outWidth, bounds.outHeight)
    while (maxDimension / sample > 1800) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("Image could not be read.")

    val orientation = resolver.openInputStream(uri)?.use { stream ->
        runCatching {
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
    }
    var oriented: Bitmap = decoded
    var finalBitmap: Bitmap = decoded
    try {
        if (!matrix.isIdentity) oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val scale = minOf(1f, 1800f / max(oriented.width, oriented.height).toFloat())
        finalBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(oriented, (oriented.width * scale).roundToInt(), (oriented.height * scale).roundToInt(), true)
        } else {
            oriented
        }
        val out = ByteArrayOutputStream()
        if (!finalBitmap.compress(Bitmap.CompressFormat.JPEG, 86, out)) error("Image could not be compressed.")
        val bytes = out.toByteArray()
        if (bytes.isEmpty()) error("Image could not be prepared.")
        if (bytes.size > 5 * 1024 * 1024) error("Image is too large. Choose a smaller photo.")
        return ProcessedSellerProductImage(bytes = bytes, mimeType = "image/jpeg", width = finalBitmap.width, height = finalBitmap.height)
    } finally {
        if (finalBitmap !== oriented) finalBitmap.recycle()
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
    }
}
