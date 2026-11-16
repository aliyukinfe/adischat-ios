package com.adischat.app.feature.shop.presentation


import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.adischat.app.feature.shop.data.ShopSellingUnitDto
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private val SellerCenterBackground = AdisGebeyaColors.Background
private val SellerCenterSurface = AdisGebeyaColors.Surface
private val SellerCenterSoft = AdisGebeyaColors.SecondarySurface
private val SellerCenterText = AdisGebeyaColors.PrimaryText
private val SellerCenterMuted = AdisGebeyaColors.SecondaryText
private val SellerCenterGreen = AdisGebeyaColors.Primary
private val SellerCenterGreenSoft = AdisGebeyaColors.PrimaryLight
private val SellerCenterOrange = AdisGebeyaColors.AccentOrange
private val SellerCenterOrangeDark = AdisGebeyaColors.AccentOrangeDark
private val SellerCenterLine = AdisGebeyaColors.Divider
private val SellerCenterRed = AdisGebeyaColors.Error
private val SellerCenterRedSoft = AdisGebeyaColors.ErrorSoft
private val SellerCenterAmberSoft = AdisGebeyaColors.SoftOrange
private val SellerCenterBlue = AdisGebeyaColors.Info
private val SellerCenterBlueSoft = AdisGebeyaColors.SoftBlue
private val SellerCenterPinkSoft = AdisGebeyaColors.SoftPink
private val SellerCenterPurpleSoft = AdisGebeyaColors.SoftPurple
private val SellerCenterOnColor = AdisGebeyaColors.OnColor


@Composable
internal fun SellerCenterScreen(
    protectedState: ShopProtectedState,
    openNotificationsInitially: Boolean = false,
    categories: List<ShopCategoryDto>,
    sellingUnits: List<ShopSellingUnitDto>,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onLoadSellerFeatures: () -> Unit,
    onLoadNotifications: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    messageActions: SellerMessageActions,
    businessActions: SellerBusinessActions,
    onSaveApplicationDraft: (ShopSellerApplicationRequest) -> Unit,
    onSubmitCompleteApplication: (ShopSellerApplicationRequest) -> Unit,
    onRespondApplicationInfoRequest: (String) -> Unit,
    onUploadApplicationDocument: (String, ByteArray, String, String) -> Unit,
    onRemoveApplicationDocument: (String) -> Unit,
    onRespondSellerRequest: (String, String, String, Int) -> Unit,
    onSellerAction: (String, String, String, String?, String?, String?, Int?) -> Unit,
    onCreateProduct: () -> Unit,
    onEditProduct: (String) -> Unit,
    onCloseProductEditor: () -> Unit,
    onSaveProduct: (String, ShopSellerProductDraftPayload) -> Unit,
    onSaveBeforeImagePick: (String, ShopSellerProductDraftPayload, () -> Unit) -> Unit,
    onSaveAndSubmitProduct: (String, ShopSellerProductDraftPayload) -> Unit,
    onLoadCategoryAttributes: (String) -> Unit,
    onSubmitProduct: (String) -> Unit,
    onArchiveProduct: (String) -> Unit,
    onUploadProductMedia: (String, List<android.net.Uri>, android.content.Context, String) -> Unit,
    onRetryProductMedia: (String) -> Unit,
    onDeleteProductMedia: (String, String) -> Unit,
    onReorderProductMedia: (String, List<String>, String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(SellerCenterTab.Dashboard) }
    var tabHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var orderFilter by remember { mutableStateOf("New") }
    var selectedOrderGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNotifications by rememberSaveable { mutableStateOf(openNotificationsInitially) }
    var notificationOriginTab by rememberSaveable { mutableStateOf<SellerCenterTab?>(null) }
    var returnToNotifications by rememberSaveable { mutableStateOf(false) }
    var confirmEditorClose by rememberSaveable { mutableStateOf(false) }
    val productEditorStateHolder = rememberSaveableStateHolder()
    val currentProtectedState by rememberUpdatedState(protectedState)
    fun openTab(tab: SellerCenterTab) {
        returnToNotifications = false
        notificationOriginTab = null
        if (tab != selectedTab) {
            tabHistory = tabHistory + selectedTab.name
            selectedTab = tab
        }
    }
    fun closeNotifications() {
        showNotifications = false
        notificationOriginTab?.let { origin ->
            if (selectedTab != origin) {
                selectedTab = origin
                if (tabHistory.lastOrNull() == origin.name) tabHistory = tabHistory.dropLast(1)
            }
        }
        notificationOriginTab = null
        returnToNotifications = false
    }
    fun openNotificationDestination(tab: SellerCenterTab) {
        val origin = notificationOriginTab ?: selectedTab
        openTab(tab)
        notificationOriginTab = origin
        returnToNotifications = true
        showNotifications = false
    }
    fun goBack() {
        when {
            showNotifications -> closeNotifications()
            selectedOrderGroupId != null -> {
                selectedOrderGroupId = null
                if (returnToNotifications) {
                    showNotifications = true
                    returnToNotifications = false
                }
            }
            currentProtectedState.selectedConversation != null -> {
                messageActions.close()
                if (returnToNotifications) {
                    showNotifications = true
                    returnToNotifications = false
                }
            }
            selectedTab == SellerCenterTab.Products && currentProtectedState.selectedSellerProduct != null -> confirmEditorClose = true
            returnToNotifications -> {
                showNotifications = true
                returnToNotifications = false
            }
            tabHistory.isNotEmpty() -> {
                selectedTab = SellerCenterTab.valueOf(tabHistory.last())
                tabHistory = tabHistory.dropLast(1)
            }
            selectedTab != SellerCenterTab.Dashboard -> selectedTab = SellerCenterTab.Dashboard
            else -> onBack()
        }
    }
    BackHandler { goBack() }
    if (confirmEditorClose) {
        AlertDialog(
            onDismissRequest = { confirmEditorClose = false },
            title = { Text("Close product editor?") },
            text = { Text("Unsaved changes stay in this Seller Center session. Save the draft to keep changes on your store before leaving Seller Center.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEditorClose = false
                    onCloseProductEditor()
                }) { Text("Close editor") }
            },
            dismissButton = { TextButton(onClick = { confirmEditorClose = false }) { Text("Keep editing") } },
        )
    }
    LaunchedEffect(openNotificationsInitially) {
        if (openNotificationsInitially) {
            notificationOriginTab = selectedTab
            showNotifications = true
        }
    }
    val notificationStoreId = protectedState.sellerBusiness?.selectedStore?.id
    val storeOrderGroups = if (protectedState.sellerOrdersLoaded && protectedState.sellerOrderStoreId == notificationStoreId) {
        protectedState.sellerOrderGroups.filter { it.storeId == notificationStoreId }
    } else emptyList()
    val storeConversations = protectedState.sellerConversations.filter { it.storeId == notificationStoreId }
    LaunchedEffect(notificationStoreId) { selectedOrderGroupId = null }
    LaunchedEffect(notificationStoreId, protectedState.sellerApplication?.id) {
        if (!notificationStoreId.isNullOrBlank() || protectedState.sellerApplication != null) onLoadNotifications()
    }
    var sellerResolutionStarted by remember { mutableStateOf(false) }
    var initialBrandPhaseComplete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        sellerResolutionStarted = true
        onLoad()
        delay(500)
        initialBrandPhaseComplete = true
    }
    if (shouldShowSellerInitialLoader(initialBrandPhaseComplete, protectedState.error != null)) {
        SellerCenterInitialLoader()
        return
    }
    val resolvingSeller = isSellerIdentityResolving(
        resolutionStarted = sellerResolutionStarted,
        loading = protectedState.loading,
        sellerStatus = protectedState.sellerStatus,
        hasApplication = protectedState.sellerApplication != null,
        hasError = protectedState.error != null,
    )
    if (resolvingSeller) {
        SellerCenterDashboardSkeleton()
        return
    }

    val featuresUnlocked = sellerFeaturesUnlocked(protectedState.sellerStatus)
    LaunchedEffect(featuresUnlocked) {
        if (featuresUnlocked) onLoadSellerFeatures()
    }

    val resolvingSellerDashboard = featuresUnlocked &&
        protectedState.loading &&
        protectedState.sellerBusiness == null &&
        protectedState.sellerOrderGroups.isEmpty() &&
        protectedState.sellerProducts.isEmpty() &&
        protectedState.sellerConversations.isEmpty()
    if (resolvingSellerDashboard) {
        SellerCenterDashboardSkeleton()
        return
    }

    if (!featuresUnlocked && !showNotifications) {
        SellerApplicantScreen(
            protectedState = protectedState,
            onBack = onBack,
            notificationUnreadCount = protectedState.sellerNotificationUnreadCount,
            onOpenNotifications = {
                notificationOriginTab = selectedTab
                showNotifications = true
            },
            onSaveDraft = onSaveApplicationDraft,
            onSubmit = onSubmitCompleteApplication,
            onRespondInfoRequest = onRespondApplicationInfoRequest,
            onUploadDocument = onUploadApplicationDocument,
            onRemoveDocument = onRemoveApplicationDocument,
        )
        return
    }

    if (showNotifications) {
        ShopNotificationsScreen(
            notifications = protectedState.sellerNotifications.takeIf { protectedState.sellerNotificationStoreId == notificationStoreId }.orEmpty(),
            unreadCount = protectedState.sellerNotificationUnreadCount.takeIf { protectedState.sellerNotificationStoreId == notificationStoreId } ?: 0,
            loading = protectedState.loading,
            onBack = ::closeNotifications,
            onRefresh = onLoadNotifications,
            onMarkRead = onMarkNotificationRead,
            onMarkAllRead = onMarkAllNotificationsRead,
            sellerView = true,
            onOpen = { target ->
                when (target) {
                    is ShopNotificationTarget.SellerOrder -> {
                        openNotificationDestination(SellerCenterTab.Orders)
                        if (storeOrderGroups.any { it.id == target.orderGroupId }) {
                            selectedOrderGroupId = target.orderGroupId
                        }
                    }
                    is ShopNotificationTarget.Conversation -> {
                        if (storeConversations.any {
                                it.id == target.conversationId && it.storeId == notificationStoreId
                            }) {
                            openNotificationDestination(SellerCenterTab.Messages)
                            messageActions.open(target.conversationId)
                        }
                    }
                    ShopNotificationTarget.SellerRequests -> {
                        openNotificationDestination(SellerCenterTab.Requests)
                    }
                    ShopNotificationTarget.SellerProducts -> {
                        openNotificationDestination(SellerCenterTab.Products)
                    }
                    ShopNotificationTarget.SellerMore -> {
                        openNotificationDestination(SellerCenterTab.More)
                    }
                    else -> Unit
                }
            },
        )
        return
    }

    selectedOrderGroupId?.let { groupId ->
        val group = storeOrderGroups.firstOrNull { it.id == groupId }
        if (group != null) {
            SellerOrderDetailPage(
                group = group,
                busy = protectedState.busyIds.contains(group.id),
                onBack = { goBack() },
                onSellerAction = onSellerAction,
                onDeadlineReached = onLoadSellerFeatures,
            )
            return
        }
    }

    if (selectedTab == SellerCenterTab.Messages && protectedState.selectedConversation != null) {
        Box(
            Modifier.fillMaxSize().background(SellerCenterBackground).statusBarsPadding().padding(16.dp),
        ) {
            SellerMessagesPage(
                state = protectedState,
                onLoad = messageActions.load,
                onOpen = messageActions.open,
                onClose = messageActions.close,
                onRefreshConversation = messageActions.refresh,
                onSend = messageActions.send,
                onUpload = messageActions.upload,
                onAction = messageActions.update,
                onTyping = messageActions.typing,
                onOpenProduct = { productId ->
                    messageActions.close()
                    openTab(SellerCenterTab.Products)
                    messageActions.openProduct(productId)
                },
            )
        }
        return
    }

    Scaffold(
        containerColor = SellerCenterBackground,
        bottomBar = {
            SellerCenterBottomBar(selectedTab = selectedTab, onSelect = ::openTab)
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().background(SellerCenterBackground).statusBarsPadding().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SellerHeader(
                    protectedState = protectedState,
                    onBack = { goBack() },
                    onSearch = { openTab(SellerCenterTab.Products) },
                    onMessages = { openTab(SellerCenterTab.Messages) },
                    onNotifications = {
                        notificationOriginTab = selectedTab
                        showNotifications = true
                    },
                )
            }
            protectedState.error?.let { message ->
                item { EmptyCard(message) }
            }
            when (selectedTab) {
                SellerCenterTab.Dashboard -> {
                    item {
                        SellerApplicationFormCard(
                            protectedState = protectedState,
                            onSaveDraft = onSaveApplicationDraft,
                            onSubmit = onSubmitCompleteApplication,
                            onRespondInfoRequest = onRespondApplicationInfoRequest,
                            onUploadDocument = onUploadApplicationDocument,
                            onRemoveDocument = onRemoveApplicationDocument,
                        )
                    }
                    item { SellerKpiGrid(storeOrderGroups) }
                    item {
                        SellerQuickActions(
                            onTab = ::openTab,
                            onMore = { openTab(SellerCenterTab.More) },
                        )
                    }
                    item { SellerRecentOrders(storeOrderGroups, onOpenOrders = { openTab(SellerCenterTab.Orders) }) }
                }
                SellerCenterTab.Orders -> {
                    item { SellerOrdersPageHeader(orderFilter, onFilter = { orderFilter = it }) }
                    val filtered = storeOrderGroups.filter { sellerOrderMatchesTab(it, orderFilter) }
                    if (!protectedState.sellerOrdersLoaded || protectedState.sellerOrderStoreId != notificationStoreId) {
                        item { CircularProgressIndicator() }
                    } else if (filtered.isEmpty()) {
                        item { EmptyCard("No ${orderFilter.lowercase()} seller orders right now.") }
                    } else {
                        items(filtered, key = { it.id }) { group ->
                            SellerOrderListCard(
                                group = group,
                                onOpen = { selectedOrderGroupId = group.id },
                                onDeadlineReached = onLoadSellerFeatures,
                            )
                        }
                    }
                }
                SellerCenterTab.Products -> {
                    item(key = "seller-products-page") {
                        SellerProductsPage(
                            protectedState = protectedState,
                            editorStateHolder = productEditorStateHolder,
                            categories = categories,
                            sellingUnits = sellingUnits,
                            onCreateProduct = onCreateProduct,
                            onEditProduct = onEditProduct,
                            onCloseEditor = { confirmEditorClose = true },
                            onSaveProduct = onSaveProduct,
                            onSaveBeforeImagePick = onSaveBeforeImagePick,
                            onSaveAndSubmitProduct = onSaveAndSubmitProduct,
                            onLoadCategoryAttributes = onLoadCategoryAttributes,
                            onSubmitProduct = onSubmitProduct,
                            onArchiveProduct = onArchiveProduct,
                            onUploadProductMedia = onUploadProductMedia,
                            onRetryProductMedia = onRetryProductMedia,
                            onDeleteProductMedia = onDeleteProductMedia,
                            onReorderProductMedia = onReorderProductMedia,
                        )
                    }
                }
                SellerCenterTab.Requests -> {
                    item {
                        SellerRequestsPage(
                            if (protectedState.sellerRequestsStoreId == notificationStoreId)
                                protectedState.sellerRequests ?: ShopSellerRequestsDto()
                            else ShopSellerRequestsDto(),
                            protectedState.busyIds,
                            onRespondSellerRequest,
                        )
                    }
                }
                SellerCenterTab.Messages -> {
                    item {
                        SellerMessagesPage(
                            state = protectedState.copy(
                                sellerConversations = storeConversations,
                                sellerMessageUnreadCount = storeConversations.sumOf { it.unreadCount.coerceAtLeast(0) },
                                selectedConversation = protectedState.selectedConversation?.takeIf { it.storeId == notificationStoreId },
                            ),
                            onLoad = messageActions.load,
                            onOpen = messageActions.open,
                            onClose = messageActions.close,
                            onRefreshConversation = messageActions.refresh,
                            onSend = messageActions.send,
                            onUpload = messageActions.upload,
                            onAction = messageActions.update,
                            onTyping = messageActions.typing,
                            onOpenProduct = { productId ->
                                messageActions.close()
                                openTab(SellerCenterTab.Products)
                                messageActions.openProduct(productId)
                            },
                        )
                    }
                }
                SellerCenterTab.More -> {
                    item {
                        SellerBusinessSuite(
                            protectedState.sellerBusiness,
                            protectedState.busyIds,
                            businessActions,
                            loading = protectedState.loading,
                            hasError = protectedState.error != null,
                        )
                    }
                }
            }
        }
    }
}

private enum class SellerCenterTab(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.Analytics),
    Orders("Orders", Icons.Default.ReceiptLong),
    Products("Products", Icons.Default.Inventory2),
    Requests("Requests", Icons.Default.Assignment),
    Messages("Messages", Icons.Default.Message),
    More("More", Icons.Default.MoreHoriz),
}

@Composable
private fun SellerCenterBottomBar(selectedTab: SellerCenterTab, onSelect: (SellerCenterTab) -> Unit) {
    NavigationBar(
        containerColor = SellerCenterSurface,
        tonalElevation = 8.dp,
        modifier = Modifier.navigationBarsPadding().border(1.dp, SellerCenterLine.copy(alpha = .65f)),
    ) {
        SellerCenterTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(22.dp)) },
                label = { Text(tab.label, fontSize = 10.sp, fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Medium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SellerCenterGreen,
                    selectedTextColor = SellerCenterText,
                    indicatorColor = SellerCenterGreenSoft,
                    unselectedIconColor = SellerCenterMuted,
                    unselectedTextColor = SellerCenterMuted,
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
    onNotifications: () -> Unit,
) {
    val app = protectedState.sellerApplication
    val notificationStoreId = protectedState.sellerBusiness?.selectedStore?.id
    val status = protectedState.sellerStatus.ifBlank { app?.status ?: "not_started" }
    val storeName = if (status == "approved") "Seller Center" else app?.proposedStoreName ?: "Seller Account"
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(SellerCenterOnColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, SellerCenterLine),
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(108.dp)
                    .background(Brush.horizontalGradient(listOf(SellerCenterGreen, SellerCenterGreenSoft))),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(48.dp).clip(CircleShape).background(SellerCenterOnColor.copy(alpha = .2f))) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = SellerCenterOnColor)
                }
                Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderIcon(Icons.Default.Search, "Search seller center", onClick = onSearch)
                    HeaderIcon(
                        Icons.Default.Notifications,
                        "Seller notifications",
                        onClick = onNotifications,
                        badge = protectedState.sellerNotificationUnreadCount.takeIf { protectedState.sellerNotificationStoreId == notificationStoreId } ?: 0,
                    )
                    HeaderIcon(Icons.Default.Message, "Seller messages", onClick = onMessages)
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(SellerCenterGreenSoft).border(1.dp, SellerCenterLine, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = SellerCenterGreen, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(storeName, color = SellerCenterText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (status == "approved") {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Verified, contentDescription = "Verified", tint = SellerCenterGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        "Joined ${formatMarketplaceDate(app?.submittedAt ?: app?.createdAt, "after approval")}",
                        color = SellerCenterMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(6.dp))
                    MiniBadge(backendLabel(status), sellerStatusBg(status), sellerStatusColor(status))
                }
            }
        }
    }
}

internal fun sellerStatusBg(status: String): Color = when (status) {
    "approved" -> SellerCenterGreenSoft
    "rejected", "suspended" -> SellerCenterRedSoft
    "submitted", "under_review", "pending" -> SellerCenterAmberSoft
    else -> SellerCenterSoft
}


@Composable
private fun SellerRequestsPage(
    requests: ShopSellerRequestsDto?,
    busyIds: Set<String>,
    onRespond: (String, String, String, Int) -> Unit,
) {
    var responseText by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Returns and Requests", color = SellerCenterText, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text("Refund and exchange requests for your own stores. Private Admin notes are hidden.", color = SellerCenterMuted, lineHeight = 19.sp)
        OutlinedTextField(
            responseText,
            { responseText = it },
            label = { Text("Seller response") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
            colors = shopOutlinedTextFieldColors(),
        )
        val refunds = requests?.refundRequests.orEmpty()
        val exchanges = requests?.exchangeRequests.orEmpty()
        if (refunds.isEmpty() && exchanges.isEmpty()) {
            EmptyCard("No refund or exchange requests yet.")
        }
        refunds.forEach { request ->
            SellerRequestCard(
                type = "Refund",
                title = request.reasonLabel ?: backendLabel(request.reasonCode),
                status = request.status,
                body = request.buyerVisibleUpdate ?: request.description.orEmpty(),
                amount = formatEtb(request.requestedAmount),
                busy = busyIds.contains(request.id),
                onRespond = { onRespond("refund", request.id, responseText.ifBlank { "Seller has acknowledged this refund request." }, request.version) },
            )
        }
        exchanges.forEach { request ->
            SellerRequestCard(
                type = "Exchange",
                title = request.reasonLabel ?: backendLabel(request.reasonCode),
                status = request.status,
                body = request.buyerVisibleUpdate ?: request.description.orEmpty(),
                amount = "",
                busy = busyIds.contains(request.id),
                onRespond = { onRespond("exchange", request.id, responseText.ifBlank { "Seller has acknowledged this exchange request." }, request.version) },
            )
        }
    }
}

@Composable
private fun SellerRequestCard(type: String, title: String, status: String, body: String, amount: String, busy: Boolean, onRespond: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(SellerCenterSurface), border = androidx.compose.foundation.BorderStroke(1.dp, SellerCenterLine)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(type, color = SellerCenterGreen, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Text(title, color = SellerCenterText, fontWeight = FontWeight.SemiBold)
                }
                MiniBadge(backendLabel(status), orderStatusBg(status), orderStatusColor(status))
            }
            if (body.isNotBlank()) Text(body, color = SellerCenterMuted, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (amount.isNotBlank()) Text(amount, color = SellerCenterGreen, fontWeight = FontWeight.Black)
            if (sellerRequestCanRespond(status)) {
                OutlinedButton(onClick = onRespond, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(if (busy) "Sending..." else "Send seller response")
                }
            }
        }
    }
}

internal fun sellerRequestCanRespond(status: String): Boolean =
    status !in setOf("rejected", "cancelled", "completed", "closed")

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
            item { SellerKpiCard("Orders", totalOrders.toString(), Icons.Default.ReceiptLong, SellerCenterGreenSoft, SellerCenterGreen) }
            item { SellerKpiCard("New", pendingOrders.toString(), Icons.Default.Assignment, SellerCenterAmberSoft, SellerCenterOrangeDark) }
            item { SellerKpiCard("Processing", processing.toString(), Icons.Default.LocalShipping, SellerCenterBlueSoft, SellerCenterBlue) }
            item { SellerKpiCard("Completed", completed.toString(), Icons.Default.CheckCircle, SellerCenterGreenSoft, SellerCenterGreen) }
            item { SellerKpiCard("Cancelled", cancelled.toString(), Icons.Default.Delete, SellerCenterRedSoft, SellerCenterRed) }
            item { SellerKpiCard("Revenue", formatEtb(revenueMinor), Icons.Default.AccountBalanceWallet, SellerCenterPurpleSoft, SellerCenterText) }
        }
    }
}

@Composable
private fun SellerKpiCard(title: String, value: String, icon: ImageVector, bg: Color, tint: Color) {
    PremiumMetricCard(label = title, value = value, icon = icon, iconBackground = bg, iconTint = tint)
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
            item { SellerShortcut("Add Product", Icons.Default.Add, SellerCenterGreenSoft, SellerCenterGreen) { onTab(SellerCenterTab.Products) } }
            item { SellerShortcut("Orders", Icons.Default.ReceiptLong, SellerCenterBlueSoft, SellerCenterBlue) { onTab(SellerCenterTab.Orders) } }
            item { SellerShortcut("Products", Icons.Default.Inventory2, SellerCenterPurpleSoft, SellerCenterText) { onTab(SellerCenterTab.Products) } }
            item { SellerShortcut("Messages", Icons.Default.Message, SellerCenterGreenSoft, SellerCenterGreen) { onTab(SellerCenterTab.Messages) } }
            item { SellerShortcut("Analytics", Icons.Default.Analytics, SellerCenterAmberSoft, SellerCenterOrangeDark) { onMore() } }
            item { SellerShortcut("Marketing", Icons.Default.Campaign, SellerCenterPinkSoft, SellerCenterRed) { onMore() } }
            item { SellerShortcut("Finance", Icons.Default.AccountBalanceWallet, SellerCenterPurpleSoft, SellerCenterText) { onMore() } }
            item { SellerShortcut("Settings", Icons.Default.Settings, SellerCenterSoft, SellerCenterMuted) { onMore() } }
        }
    }
}

@Composable
private fun SellerShortcut(label: String, icon: ImageVector, bg: Color, tint: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(SellerCenterOnColor).border(1.dp, SellerCenterLine, RoundedCornerShape(18.dp)).clickable { onClick() }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = SellerCenterText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun SellerRecentOrders(groups: List<ShopOrderGroupDto>, onOpenOrders: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Recent orders", action = if (groups.isNotEmpty()) "View all" else null, onAction = onOpenOrders)
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
    Card(Modifier.clickable { onClick() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(SellerCenterOnColor), border = androidx.compose.foundation.BorderStroke(1.dp, SellerCenterLine)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(SellerCenterGreenSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = SellerCenterGreen)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(group.order?.orderNumber ?: "Seller order", color = SellerCenterText, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("${group.items.orEmpty().sumOf { it.quantity }} items • ${formatEtb(group.order?.totalMinor ?: group.items.orEmpty().sumOf { it.lineTotalMinor })}", color = SellerCenterMuted, fontSize = 12.sp)
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
        "Cancelled" -> status in listOf("cancelled", "rejected", "expired")
        "Returns" -> status.contains("return", ignoreCase = true)
        else -> true
    }
}

@Composable
private fun SellerUnavailableModulePage(title: String, icon: ImageVector, body: String, actions: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumEmptyState(title = title, message = body, icon = icon)
        SectionHeader("Planned tools", action = null)
        actions.forEach { label ->
            PremiumUnavailableModuleCard(
                title = label,
                message = "Unavailable until the protected backend endpoint is enabled.",
                icon = icon,
            )
        }
    }
}
