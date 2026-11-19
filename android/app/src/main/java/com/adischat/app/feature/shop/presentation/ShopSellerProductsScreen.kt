package com.adischat.app.feature.shop.presentation


import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.SaveableStateHolder
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
import com.adischat.app.feature.shop.data.ShopSellerSpecificationInput
import com.adischat.app.feature.shop.data.ShopSellerProductOptionInput
import com.adischat.app.feature.shop.data.ShopSellerVariantInput
import com.adischat.app.feature.shop.data.SellerVariantOutputs
import com.adischat.app.feature.shop.data.ShopStoreDto
import com.adischat.app.feature.shop.data.ShopSellingUnitDto
import com.adischat.app.ui.theme.AdisGebeyaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private val SellerProductSurface = AdisGebeyaColors.Surface
private val SellerProductSoft = AdisGebeyaColors.SecondarySurface
private val SellerProductText = AdisGebeyaColors.PrimaryText
private val SellerProductMuted = AdisGebeyaColors.SecondaryText
private val SellerProductGreen = AdisGebeyaColors.Primary
private val SellerProductGreenSoft = AdisGebeyaColors.PrimaryLight
private val SellerProductOrange = AdisGebeyaColors.AccentOrange
private val SellerProductOrangeDark = AdisGebeyaColors.AccentOrangeDark
private val SellerProductLine = AdisGebeyaColors.Divider
private val SellerProductRed = AdisGebeyaColors.Error
private val SellerProductRedSoft = AdisGebeyaColors.ErrorSoft
private val SellerProductAmberSoft = AdisGebeyaColors.SoftOrange
private val SellerProductOnColor = AdisGebeyaColors.OnColor


@Composable
internal fun SellerProductsPage(
    protectedState: ShopProtectedState,
    editorStateHolder: SaveableStateHolder,
    categories: List<ShopCategoryDto>,
    sellingUnits: List<ShopSellingUnitDto>,
    onCreateProduct: () -> Unit,
    onEditProduct: (String) -> Unit,
    onCloseEditor: () -> Unit,
    onSaveProduct: (String, ShopSellerProductDraftPayload) -> Unit,
    onSaveBeforeImagePick: (String, ShopSellerProductDraftPayload, () -> Unit) -> Unit,
    onSaveAndSubmitProduct: (String, ShopSellerProductDraftPayload) -> Unit,
    onLoadCategoryAttributes: (String) -> Unit,
    onSubmitProduct: (String) -> Unit,
    onArchiveProduct: (String) -> Unit,
    onUploadProductMedia: (String, List<Uri>, Context, String) -> Unit,
    onRetryProductMedia: (String) -> Unit,
    onDeleteProductMedia: (String, String) -> Unit,
    onReorderProductMedia: (String, List<String>, String) -> Unit,
) {
    val selectedStoreId = protectedState.sellerBusiness?.selectedStore?.id
    val productsReady = !selectedStoreId.isNullOrBlank() &&
        protectedState.sellerProductsStoreId == selectedStoreId && protectedState.sellerProductsLoaded
    val selected = protectedState.selectedSellerProduct?.takeIf { it.storeId == selectedStoreId && productsReady }
    var productPendingRemoval by remember { mutableStateOf<ShopSellerProductDto?>(null) }
    var searchQuery by rememberSaveable(selectedStoreId) { mutableStateOf("") }
    var statusFilter by rememberSaveable(selectedStoreId) { mutableStateOf("All") }
    val storeProducts = if (productsReady) com.adischat.app.feature.shop.data.ShopStateReducers.sellerProductsForStore(
        protectedState.sellerProducts, selectedStoreId,
    ) else emptyList()
    val visibleProducts = filterSellerProducts(storeProducts, searchQuery, statusFilter)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Products", color = SellerProductText, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("Create drafts and submit them for review.", color = SellerProductMuted, fontSize = 13.sp)
            }
            Button(
                onClick = onCreateProduct,
                enabled = productsReady && !protectedState.loading,
                colors = ButtonDefaults.buttonColors(containerColor = SellerProductGreen, contentColor = SellerProductOnColor),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New")
            }
        }

        selected?.let { product ->
            editorStateHolder.SaveableStateProvider(product.id) {
                SellerProductEditor(
                    product = product,
                    categories = categories,
                    sellingUnits = sellingUnits,
                    busy = protectedState.busyIds.contains(product.id),
                    mediaUpload = protectedState.sellerMediaUpload?.takeIf { it.productId == product.id },
                    onClose = {
                        onCloseEditor()
                    },
                    onSave = { onSaveProduct(product.id, it) },
                    onSaveBeforeImagePick = { payload, onSaved -> onSaveBeforeImagePick(product.id, payload, onSaved) },
                    onLoadCategoryAttributes = onLoadCategoryAttributes,
                    categoryAttributes = protectedState.sellerCategoryAttributes,
                    categoryLoadingIds = protectedState.sellerCategoryLoadingIds,
                    categoryErrors = protectedState.sellerCategoryErrors,
                    onSubmit = { payload -> onSaveAndSubmitProduct(product.id, payload) },
                    onArchive = { productPendingRemoval = product },
                    onUploadImages = { uris, context -> onUploadProductMedia(product.id, uris, context, product.title.ifBlank { "Product image" }) },
                    onRetryUpload = { onRetryProductMedia(product.id) },
                    onDeleteImage = { imageId -> onDeleteProductMedia(product.id, imageId) },
                    onReorderImages = { imageIds, primaryId -> onReorderProductMedia(product.id, imageIds, primaryId) },
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Search products or SKU") },
            colors = shopOutlinedTextFieldColors(),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("All", "Draft", "In review", "Approved", "Rejected")) { status ->
                androidx.compose.material3.FilterChip(
                    selected = statusFilter == status,
                    onClick = { statusFilter = status },
                    label = { Text(status) },
                )
            }
        }
        if (!productsReady) {
            EmptyCard(if (protectedState.error != null) "Products for this store could not be loaded." else "Loading products for this store...")
        } else if (storeProducts.isEmpty()) {
            EmptyCard("No seller products yet. Create your first draft product.")
        } else if (visibleProducts.isEmpty()) {
            EmptyCard("No products match this search and status.")
        } else {
            visibleProducts.forEach { product ->
                SellerProductCard(
                    product = product,
                    busy = protectedState.busyIds.contains(product.id),
                    onEdit = { onEditProduct(product.id) },
                    onSubmit = { onSubmitProduct(product.id) },
                    onArchive = { productPendingRemoval = product },
                )
            }
        }
    }

    productPendingRemoval?.let { product ->
        AlertDialog(
            onDismissRequest = { productPendingRemoval = null },
            title = { Text("Remove product from store?") },
            text = { Text("${product.title.ifBlank { "This product" }} will disappear from your store and AdisGebeya. Existing orders and history will be preserved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        productPendingRemoval = null
                        onArchiveProduct(product.id)
                    },
                ) { Text("Remove", color = SellerProductRed) }
            },
            dismissButton = { TextButton(onClick = { productPendingRemoval = null }) { Text("Keep product") } },
        )
    }
}

internal fun filterSellerProducts(products: List<ShopSellerProductDto>, query: String, status: String): List<ShopSellerProductDto> {
    val term = query.trim()
    return products.filter { product ->
        val matchesQuery = term.isEmpty() || product.title.contains(term, ignoreCase = true) ||
            product.sellerSku?.contains(term, ignoreCase = true) == true ||
            product.variants.any { it.sku?.contains(term, ignoreCase = true) == true }
        val matchesStatus = when (status) {
            "Draft" -> product.status == "draft" && product.moderationStatus in setOf("pending", "draft")
            "In review" -> product.moderationStatus in setOf("submitted", "under_review")
            "Approved" -> product.moderationStatus == "approved"
            "Rejected" -> product.moderationStatus in setOf("rejected", "changes_requested")
            else -> true
        }
        matchesQuery && matchesStatus
    }
}

@Composable
private fun SellerProductCard(product: ShopSellerProductDto, busy: Boolean, onEdit: () -> Unit, onSubmit: () -> Unit, onArchive: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(SellerProductSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, SellerProductLine),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(SellerProductGreenSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = SellerProductGreen)
                }
                Column(Modifier.weight(1f)) {
                    Text(product.title.ifBlank { "Untitled product" }, color = SellerProductText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${productStatusLabel(product)} • ${formatEtb(product.priceMinor)}", color = SellerProductMuted, fontSize = 12.sp)
                }
                StatusPill(product.moderationStatus)
            }
            if (product.validationErrors.isNotEmpty()) {
                Text(product.validationErrors.joinToString(" • "), color = SellerProductRed, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("Edit")
                }
                Button(
                    onClick = onSubmit,
                    enabled = !busy && product.moderationStatus !in listOf("submitted", "under_review", "approved", "suspended", "archived"),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SellerProductOrange, contentColor = SellerProductOnColor),
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SellerProductOnColor)
                    else Text("Submit")
                }
                IconButton(onClick = onArchive, enabled = !busy) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove product from store", tint = SellerProductRed)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status) {
        "approved" -> SellerProductGreen
        "submitted", "under_review" -> SellerProductOrange
        "rejected", "changes_requested" -> SellerProductRed
        else -> SellerProductMuted
    }
    Surface(shape = RoundedCornerShape(999.dp), color = color.copy(alpha = .12f)) {
        Text(productStatusText(status), color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun SellerProductEditor(
    product: ShopSellerProductDto,
    categories: List<ShopCategoryDto>,
    sellingUnits: List<ShopSellingUnitDto>,
    busy: Boolean,
    mediaUpload: com.adischat.app.feature.shop.data.ShopSellerMediaUploadState?,
    onClose: () -> Unit,
    onSave: (ShopSellerProductDraftPayload) -> Unit,
    onSaveBeforeImagePick: (ShopSellerProductDraftPayload, () -> Unit) -> Unit,
    onLoadCategoryAttributes: (String) -> Unit,
    categoryAttributes: Map<String, List<com.adischat.app.feature.shop.data.ShopCategoryAttributeDto>>,
    categoryLoadingIds: Set<String>,
    categoryErrors: Map<String, String>,
    onSubmit: (ShopSellerProductDraftPayload) -> Unit,
    onArchive: () -> Unit,
    onUploadImages: (List<Uri>, Context) -> Unit,
    onRetryUpload: () -> Unit,
    onDeleteImage: (String) -> Unit,
    onReorderImages: (List<String>, String) -> Unit,
) {
    val context = LocalContext.current
    val editorBusy = busy || mediaUpload?.uploading == true
    var title by rememberSaveable(product.id) { mutableStateOf(product.title.takeUnless { it == "Untitled product" }.orEmpty()) }
    var brand by rememberSaveable(product.id) { mutableStateOf(product.brand.orEmpty()) }
    var selectedCategoryId by rememberSaveable(product.id) { mutableStateOf(product.categoryId.orEmpty()) }
    var selectedSellingUnitCode by rememberSaveable(product.id) { mutableStateOf(product.sellingUnitCode) }
    var minimumOrderQuantity by rememberSaveable(product.id) { mutableStateOf(product.minimumOrderQuantity.toString()) }
    var productTypeDescription by rememberSaveable(product.id) { mutableStateOf(product.productTypeDescription.orEmpty()) }
    var showCategoryPicker by rememberSaveable(product.id) { mutableStateOf(false) }
    var pendingCategoryId by rememberSaveable(product.id) { mutableStateOf<String?>(null) }
    var showUnitPicker by rememberSaveable(product.id) { mutableStateOf(false) }
    var shortDescription by rememberSaveable(product.id) { mutableStateOf(product.shortDescription.orEmpty()) }
    var description by rememberSaveable(product.id) { mutableStateOf(product.description.orEmpty()) }
    val initialRegularPrice = if (product.compareAtPriceMinor != null && product.compareAtPriceMinor > product.priceMinor) {
        (product.compareAtPriceMinor / 100).toString()
    } else if (product.priceMinor > 0) {
        (product.priceMinor / 100).toString()
    } else ""
    val initialDiscount = if (product.compareAtPriceMinor != null && product.compareAtPriceMinor > product.priceMinor) {
        computeDiscountPercent(product.compareAtPriceMinor / 100, product.priceMinor / 100)?.toString().orEmpty()
    } else ""

    var price by rememberSaveable(product.id) { mutableStateOf(initialRegularPrice) }
    var discountPercent by rememberSaveable(product.id) { mutableStateOf(initialDiscount) }
    var stock by rememberSaveable(product.id) { mutableStateOf(product.inventory.firstOrNull { it.variantId == null }?.quantityAvailable?.takeIf { it > 0 }?.toString().orEmpty()) }
    var shipping by rememberSaveable(product.id) { mutableStateOf(product.shippingDetails?.get("notes")?.toString().orEmpty()) }
    var returns by rememberSaveable(product.id) { mutableStateOf(product.returnPolicy?.get("notes")?.toString().orEmpty()) }
    var specs by rememberSaveable(product.id) { mutableStateOf(product.specifications?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }.orEmpty()) }
    var variantSnapshotsJson by rememberSaveable(product.id) { mutableStateOf("{}") }
    var variantOutputsJson by rememberSaveable(product.id) { mutableStateOf("") }
    var mediaMessage by rememberSaveable(product.id) { mutableStateOf<String?>(null) }
    var specificationMessage by rememberSaveable(product.id) { mutableStateOf<String?>(null) }
    var validationErrors by rememberSaveable(product.id) { mutableStateOf(emptyMap<String, String>()) }
    var showSuccessDialog by rememberSaveable(product.id) { mutableStateOf(false) }
    var submittedTitle by rememberSaveable(product.id) { mutableStateOf("") }
    var submittedCategoryBreadcrumb by rememberSaveable(product.id) { mutableStateOf("") }
    var submittedPriceLabel by rememberSaveable(product.id) { mutableStateOf("") }

    val fullCategories = remember(categories) { CategoryHierarchyDefinition.buildAuthoritativeHierarchy(categories) }
    val selectedCategory = fullCategories.firstOrNull { it.id == selectedCategoryId } ?: categories.firstOrNull { it.id == selectedCategoryId }
    val selectedUnit = sellingUnits.firstOrNull { it.code == selectedSellingUnitCode }
    val categoryBreadcrumbText = selectedCategory?.let { CategoryHierarchyDefinition.canonicalBreadcrumb(it, fullCategories) }

    fun specificationErrors(requireRequired: Boolean): List<String> {
        val snapshot = decodeSellerVariantSnapshot(decodeSellerVariantSnapshots(variantSnapshotsJson)[selectedCategoryId])
        val values = snapshot?.specificationText ?: product.specificationValues.associate { value ->
            value.attributeCode to (value.valueText ?: value.valueNumber?.toString() ?: value.valueBoolean?.toString()).orEmpty()
        }
        return sellerSpecificationErrors(categoryAttributes[selectedCategoryId].orEmpty(), values, requireRequired)
    }
    fun currentPayload(): ShopSellerProductDraftPayload {
        val saveableCategoryId = CategoryHierarchyDefinition.resolveSaveableCategoryId(selectedCategoryId, categories)
        val resolvedProductType = if (productTypeDescription.isNotBlank()) {
            productTypeDescription
        } else {
            selectedCategory?.name.orEmpty()
        }
        return sellerProductDraftPayload(SellerProductFormValues(
            title = title,
            brand = brand,
            categoryId = saveableCategoryId,
            sellingUnitCode = selectedSellingUnitCode,
            minimumOrderQuantity = minimumOrderQuantity,
            productTypeDescription = resolvedProductType,
            shortDescription = shortDescription,
            description = description,
            price = price,
            stock = stock,
            shipping = shipping,
            returns = returns,
            specs = specs,
            variantOutputsJson = variantOutputsJson,
            imageIds = product.images.map { it.id }.toSet(),
            discountPercent = discountPercent,
        ))
    }
    val uploadedImages = product.images
        .filter { it.imageUrl?.isNotBlank() == true }
        .sortedWith(compareByDescending<ShopProductImageDto> { it.isPrimary == true }.thenBy { it.displayOrder ?: 0 })
    val duplicateSkus = remember(variantOutputsJson) {
        findDuplicateSkusFromOutputs(variantOutputsJson)
    }
    val isBasicInfoReady = title.trim().isNotBlank() && description.trim().isNotBlank()
    val isCategoryReady = selectedCategoryId.isNotBlank() && selectedSellingUnitCode.isNotBlank() && minimumOrderQuantity.isNotBlank()
    val isMediaReady = uploadedImages.isNotEmpty()
    val isPricingReady = (price.toIntOrNull() ?: 0) > 0 && (stock.toIntOrNull() ?: 0) >= 0
    val isVariantsReady = duplicateSkus.isEmpty()
    val isSpecsReady = specificationErrors(false).isEmpty()
    val isListingReady = isBasicInfoReady && isCategoryReady && isMediaReady && isPricingReady && isVariantsReady && isSpecsReady

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)) { uris ->
        mediaMessage = null
        val selected = eligibleSellerMediaSelection(uris, uploadedImages.size)
        if (selected.isNotEmpty()) onUploadImages(selected, context.applicationContext)
        if (selected.size < uris.distinct().size) mediaMessage = "A product can have up to 10 images."
    }
    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId.isNotBlank()) onLoadCategoryAttributes(selectedCategoryId)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(SellerProductSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SellerProductGreen.copy(alpha = 0.25f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Product Editor", color = SellerProductText, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(productStatusLabel(product), color = SellerProductMuted, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onArchive, enabled = !editorBusy) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove product from store", tint = SellerProductRed)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close product editor")
                    }
                }
            }
        }

        // Section 1: Basic Information
        SellerSectionCard(
            number = 1,
            title = "Basic Information",
            subtitle = "Product title, brand, descriptions, and policies",
            isComplete = isBasicInfoReady && !validationErrors.containsKey("title") && !validationErrors.containsKey("description"),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (validationErrors.containsKey("title")) validationErrors = validationErrors - "title"
                },
                label = { Text("Product name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = validationErrors.containsKey("title"),
                supportingText = validationErrors["title"]?.let { err -> { Text(err, color = SellerProductRed) } },
                colors = shopOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Brand (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = shopOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = shortDescription,
                onValueChange = { shortDescription = it },
                label = { Text("Short description (one-liner)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = shopOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    if (validationErrors.containsKey("description")) validationErrors = validationErrors - "description"
                },
                label = { Text("Full description *") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                isError = validationErrors.containsKey("description"),
                supportingText = {
                    val err = validationErrors["description"]
                    if (err != null) {
                        Text(err, color = SellerProductRed)
                    } else {
                        val count = description.trim().length
                        val hint = if (count < 10) "$count/10 characters minimum" else "$count characters"
                        Text(hint, color = if (count < 10) SellerProductMuted else SellerProductGreen)
                    }
                },
                colors = shopOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = shipping,
                onValueChange = { shipping = it },
                label = { Text("Shipping notes (delivery expectations, handling)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = shopOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = returns,
                onValueChange = { returns = it },
                label = { Text("Return policy (return window, exchange policy)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = shopOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = specs,
                onValueChange = { specs = it },
                label = { Text("Additional specifications (e.g. Material: Leather, one per line)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = shopOutlinedTextFieldColors(),
            )
        }

        // Section 2: Category & Attributes
        SellerSectionCard(
            number = 2,
            title = "Category & Attributes",
            subtitle = "Catalog classification, selling unit, and minimum order",
            isComplete = isCategoryReady && !validationErrors.containsKey("category") && !validationErrors.containsKey("sellingUnit"),
        ) {
            Text("Product Category *", color = SellerProductText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            OutlinedButton(
                onClick = { showCategoryPicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = if (validationErrors.containsKey("category")) androidx.compose.foundation.BorderStroke(1.dp, SellerProductRed) else null,
            ) {
                Text(
                    categoryBreadcrumbText ?: "Choose the most specific category",
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selectedCategory != null) SellerProductText else SellerProductMuted,
                )
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
            if (validationErrors.containsKey("category")) {
                Text(validationErrors["category"].orEmpty(), color = SellerProductRed, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
            if (selectedCategory != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SellerProductSoft,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SellerProductLine),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = SellerProductGreen, modifier = Modifier.size(14.dp))
                        Text(
                            "Exact classification: ${selectedCategory.name}",
                            fontSize = 12.sp,
                            color = SellerProductText,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            if (showCategoryPicker) {
                SellerCategoryPicker(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onDismiss = { showCategoryPicker = false },
                    onSelect = {
                        showCategoryPicker = false
                        if (validationErrors.containsKey("category")) {
                            validationErrors = validationErrors - "category"
                        }
                        if (selectedCategoryId != it.id) {
                            val currentSnapshot = decodeSellerVariantSnapshot(
                                decodeSellerVariantSnapshots(variantSnapshotsJson)[selectedCategoryId],
                            )
                            if (hasSellerCategoryDraft(variantOutputsJson, currentSnapshot)) pendingCategoryId = it.id
                            else {
                                selectedCategoryId = it.id
                                productTypeDescription = it.name
                                variantOutputsJson = ""
                            }
                        }
                    },
                )
            }
            pendingCategoryId?.let { nextId ->
                AlertDialog(
                    onDismissRequest = { pendingCategoryId = null },
                    title = { Text("Change category?") },
                    text = { Text("Your current category details are kept if you switch back. Saving under the new category replaces incompatible details. Your product name and other basic fields stay unchanged.") },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedCategoryId = nextId
                            val nextCat = fullCategories.firstOrNull { it.id == nextId } ?: categories.firstOrNull { it.id == nextId }
                            productTypeDescription = nextCat?.name.orEmpty()
                            variantOutputsJson = ""
                            pendingCategoryId = null
                        }) { Text("Change category") }
                    },
                    dismissButton = { TextButton(onClick = { pendingCategoryId = null }) { Text("Keep editing") } },
                )
            }

            Text("Selling Unit & Minimum Order *", color = SellerProductText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            OutlinedButton(
                onClick = { showUnitPicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = if (validationErrors.containsKey("sellingUnit")) androidx.compose.foundation.BorderStroke(1.dp, SellerProductRed) else null,
            ) {
                Text(selectedUnit?.let { "${it.name} (${it.symbol})" } ?: "Choose selling unit", modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
            if (validationErrors.containsKey("sellingUnit")) {
                Text(validationErrors["sellingUnit"].orEmpty(), color = SellerProductRed, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
            if (showUnitPicker) {
                SellingUnitPicker(
                    units = sellingUnits,
                    categoryId = selectedCategoryId,
                    selectedCode = selectedSellingUnitCode,
                    onDismiss = { showUnitPicker = false },
                    onSelect = {
                        selectedSellingUnitCode = it.code
                        if (validationErrors.containsKey("sellingUnit")) {
                            validationErrors = validationErrors - "sellingUnit"
                        }
                        showUnitPicker = false
                    },
                )
            }
            OutlinedTextField(
                value = minimumOrderQuantity,
                onValueChange = { minimumOrderQuantity = it.filter(Char::isDigit) },
                label = { Text("Minimum order quantity (${selectedUnit?.symbol ?: "units"})") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = shopOutlinedTextFieldColors(),
            )
            if (selectedCategory?.slug == "other-specialized-products") {
                OutlinedTextField(
                    value = productTypeDescription,
                    onValueChange = { productTypeDescription = it },
                    label = { Text("Product type description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = shopOutlinedTextFieldColors(),
                )
            }
        }

        // Section 3: Product Images
        SellerSectionCard(
            number = 3,
            title = "Product Images",
            subtitle = "Upload 1-10 photos. First image is the store cover.",
            isComplete = isMediaReady && !validationErrors.containsKey("images"),
        ) {
            if (validationErrors.containsKey("images")) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AdisGebeyaColors.ErrorSoft),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AdisGebeyaColors.Error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AdisGebeyaColors.Error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            validationErrors["images"].orEmpty(),
                            color = AdisGebeyaColors.Error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            SellerProductMediaSection(
                images = uploadedImages,
                busy = editorBusy,
                message = mediaMessage,
                upload = mediaUpload,
                onAdd = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRetry = onRetryUpload,
                onDelete = onDeleteImage,
                onSetPrimary = { image ->
                    val ids = uploadedImages.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }
                    if (image.id.isNotBlank()) onReorderImages(ids, image.id)
                },
                onMove = { from, to ->
                    val mutable = uploadedImages.toMutableList()
                    if (from in mutable.indices && to in mutable.indices) {
                        val moved = mutable.removeAt(from)
                        mutable.add(to, moved)
                        val primaryId = mutable.firstOrNull { it.isPrimary == true }?.id ?: mutable.firstOrNull()?.id.orEmpty()
                        onReorderImages(mutable.map { it.id }.filter { it.isNotBlank() }, primaryId)
                    }
                },
            )
        }

        // Section 4: Sizes, Colors & Variations
        SellerSectionCard(
            number = 4,
            title = "Sizes, Colors & Variations",
            subtitle = "Category specifications, options, and unique SKUs",
            isComplete = isVariantsReady && isSpecsReady,
        ) {
            if (selectedCategoryId.isNotBlank()) {
                val dynamicAttributes = categoryAttributes[selectedCategoryId].orEmpty()
                when {
                    selectedCategoryId in categoryLoadingIds -> Text("Loading category fields...", color = SellerProductMuted, fontSize = 13.sp)
                    categoryErrors.containsKey(selectedCategoryId) -> {
                        Text(categoryErrors.getValue(selectedCategoryId), color = SellerProductRed, fontSize = 13.sp)
                        OutlinedButton(onClick = { onLoadCategoryAttributes(selectedCategoryId) }) { Text("Retry category fields") }
                    }
                    !categoryAttributes.containsKey(selectedCategoryId) -> Text("Preparing category fields...", color = SellerProductMuted, fontSize = 13.sp)
                    dynamicAttributes.isEmpty() -> Text("No additional attributes or options for this category.", color = SellerProductMuted, fontSize = 13.sp)
                    else -> SellerVariantCatalogEditor(
                        product = product,
                        categoryId = selectedCategoryId,
                        categoryCode = selectedCategory?.code,
                        attributes = dynamicAttributes,
                        productImages = uploadedImages,
                        baseStock = stock.toIntOrNull() ?: 0,
                        basePriceMinor = (price.toIntOrNull() ?: (product.priceMinor / 100)) * 100,
                        savedSnapshot = decodeSellerVariantSnapshot(decodeSellerVariantSnapshots(variantSnapshotsJson)[selectedCategoryId]),
                        onSnapshotChanged = { snapshot ->
                            variantSnapshotsJson = putSellerVariantSnapshot(variantSnapshotsJson, selectedCategoryId, snapshot)
                        },
                        onChanged = { nextSpecifications, nextOptions, nextVariants ->
                            variantOutputsJson = encodeSellerVariantOutputs(
                                SellerVariantOutputs(nextSpecifications, nextOptions, nextVariants),
                            )
                        },
                    )
                }
            } else {
                Text("Select a category above to configure specifications, size, and color options.", color = SellerProductMuted, fontSize = 13.sp)
            }
        }

        // Section 5: Base Price & Inventory (Promotions & Discounts)
        val regularPriceEtb = price.toIntOrNull() ?: 0
        val discountPctInt = discountPercent.toIntOrNull()?.takeIf { it in 1..90 }
        val promotionalPriceEtb = if (discountPctInt != null && regularPriceEtb > 0) computeDiscountedPrice(regularPriceEtb, discountPctInt) else null
        val savingsEtb = if (discountPctInt != null && regularPriceEtb > 0) computeSavingsEtb(regularPriceEtb, discountPctInt) else 0

        SellerSectionCard(
            number = 5,
            title = "Price & Promotional Discount",
            subtitle = "Base regular price, promotional discount percentage, and stock",
            isComplete = isPricingReady && !validationErrors.containsKey("price") && !validationErrors.containsKey("stock") && !validationErrors.containsKey("discount"),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = price,
                    onValueChange = {
                        price = it.filter(Char::isDigit)
                        if (validationErrors.containsKey("price")) validationErrors = validationErrors - "price"
                    },
                    label = { Text("Base Price ETB / ${selectedUnit?.symbol ?: "unit"} *") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = validationErrors.containsKey("price"),
                    supportingText = validationErrors["price"]?.let { err -> { Text(err, color = SellerProductRed) } },
                    colors = shopOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = {
                        stock = it.filter(Char::isDigit)
                        if (validationErrors.containsKey("stock")) validationErrors = validationErrors - "stock"
                    },
                    label = { Text("Base Stock (${selectedUnit?.symbol ?: "units"}) *") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = validationErrors.containsKey("stock"),
                    supportingText = validationErrors["stock"]?.let { err -> { Text(err, color = SellerProductRed) } },
                    colors = shopOutlinedTextFieldColors(),
                )
            }

            OutlinedTextField(
                value = discountPercent,
                onValueChange = {
                    val filtered = it.filter(Char::isDigit).take(2)
                    discountPercent = filtered
                    if (validationErrors.containsKey("discount")) validationErrors = validationErrors - "discount"
                },
                label = { Text("Promotional Discount (% OFF, optional)") },
                placeholder = { Text("e.g. 10, 15, 20, 50") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = validationErrors.containsKey("discount"),
                supportingText = validationErrors["discount"]?.let { err -> { Text(err, color = SellerProductRed) } },
                colors = shopOutlinedTextFieldColors(),
            )

            // Preset discount chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Presets:", color = SellerProductMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val presets = listOf("5", "10", "15", "20", "25", "30", "50")
                    items(presets) { pct ->
                        val isSelected = discountPercent == pct
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                discountPercent = if (isSelected) "" else pct
                                if (validationErrors.containsKey("discount")) validationErrors = validationErrors - "discount"
                            },
                            label = { Text("$pct% OFF", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SellerProductGreen,
                                selectedLabelColor = SellerProductOnColor,
                            ),
                        )
                    }
                    if (discountPercent.isNotBlank()) {
                        item {
                            TextButton(
                                onClick = {
                                    discountPercent = ""
                                    if (validationErrors.containsKey("discount")) validationErrors = validationErrors - "discount"
                                },
                            ) {
                                Text("Clear", color = SellerProductRed, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Live Promotional Pricing Preview Card
            if (regularPriceEtb > 0 && promotionalPriceEtb != null && discountPctInt != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SellerProductGreen.copy(alpha = 0.08f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SellerProductGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Promotional Pricing Preview",
                                color = SellerProductGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = SellerProductGreen,
                            ) {
                                Text(
                                    "-$discountPctInt% OFF",
                                    color = SellerProductOnColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "ETB %,d".format(promotionalPriceEtb),
                                color = SellerProductGreen,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "ETB %,d".format(regularPriceEtb),
                                color = SellerProductMuted,
                                fontSize = 14.sp,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                        Text(
                            "Buyers save ETB %,d (${discountPctInt}%% discount). Applied immediately upon listing or approval.".format(savingsEtb),
                            color = SellerProductText,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                Text(
                    "Base price and stock apply to standard single-option products or serve as the default baseline when generating multi-variant combinations.",
                    color = SellerProductMuted,
                    fontSize = 12.sp,
                )
            }
        }

        // Section 6: Review & Submit
        SellerSectionCard(
            number = 6,
            title = "Review & Submit",
            subtitle = "Listing readiness checklist and moderation submission",
            isComplete = isListingReady && validationErrors.isEmpty(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Listing Readiness Checklist", color = SellerProductText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                ListingChecklistRow("Basic details (Title & full description)", isBasicInfoReady)
                ListingChecklistRow("Category & selling unit", isCategoryReady)
                ListingChecklistRow("At least 1 product photo uploaded", isMediaReady, "${uploadedImages.size} uploaded")
                ListingChecklistRow("Base price & inventory count", isPricingReady)
                ListingChecklistRow("Unique variant SKUs (No duplicates)", isVariantsReady, if (duplicateSkus.isNotEmpty()) "Duplicate: ${duplicateSkus.joinToString(", ")}" else null)
                ListingChecklistRow("Category specifications verified", isSpecsReady)
            }

            if (validationErrors.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AdisGebeyaColors.ErrorSoft),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AdisGebeyaColors.Error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AdisGebeyaColors.Error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Please correct the following before submitting:", color = AdisGebeyaColors.Error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        validationErrors.values.distinct().forEach { msg ->
                            Text("• $msg", color = AdisGebeyaColors.Error, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (duplicateSkus.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AdisGebeyaColors.ErrorSoft),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AdisGebeyaColors.Error.copy(alpha = 0.5f)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AdisGebeyaColors.Error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Duplicate SKU detected (${duplicateSkus.joinToString(", ")}). You must resolve duplicates in Section 4 before submitting.",
                            color = AdisGebeyaColors.Error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(SellerProductSoft),
                border = androidx.compose.foundation.BorderStroke(1.dp, SellerProductLine),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Moderation & Review Process", color = SellerProductText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        "You can save drafts at any time. When you submit, our team checks product images, category classification, and specifications for catalog accuracy within 24 hours.",
                        color = SellerProductMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            if (product.validationErrors.isNotEmpty()) ErrorCardMessage(product.validationErrors.joinToString("\n"))
            specificationMessage?.let { ErrorCardMessage(it) }
            if (com.adischat.app.feature.shop.data.ShopBenefitFlags.managementEnabled) {
                SellerBenefitClaimsPanel(product.storeId, product.id)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(0.7f), shape = RoundedCornerShape(14.dp)) {
                    Text("Close")
                }
                Button(
                    onClick = {
                        specificationMessage = specificationErrors(false).firstOrNull()
                        if (specificationMessage == null) onSave(currentPayload())
                    },
                    enabled = !editorBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SellerProductGreen, contentColor = SellerProductOnColor),
                ) {
                    Text("Save Draft")
                }
                Button(
                    onClick = {
                        val formValues = SellerProductFormValues(
                            title = title,
                            brand = brand,
                            categoryId = selectedCategoryId,
                            sellingUnitCode = selectedSellingUnitCode,
                            minimumOrderQuantity = minimumOrderQuantity,
                            productTypeDescription = productTypeDescription.ifBlank { selectedCategory?.name.orEmpty() },
                            shortDescription = shortDescription,
                            description = description,
                            price = price,
                            stock = stock,
                            shipping = shipping,
                            returns = returns,
                            specs = specs,
                            variantOutputsJson = variantOutputsJson,
                            imageIds = product.images.map { it.id }.toSet(),
                            discountPercent = discountPercent,
                        )
                        val validation = validateSellerProductForm(
                            values = formValues,
                            uploadedImagesCount = uploadedImages.size,
                            duplicateSkus = duplicateSkus,
                            specificationErrors = specificationErrors(true),
                        )
                        if (!validation.isValid) {
                            validationErrors = validation.fieldErrors
                            specificationMessage = validation.fieldErrors["specs"]
                            return@Button
                        }
                        validationErrors = emptyMap()
                        specificationMessage = null
                        val payload = currentPayload()
                        submittedTitle = payload.title
                        submittedCategoryBreadcrumb = categoryBreadcrumbText ?: selectedCategory?.name.orEmpty()
                        submittedPriceLabel = if (payload.compareAtPriceMinor != null && payload.compareAtPriceMinor > payload.priceMinor) {
                            "ETB %,d (Regular: ETB %,d)".format(payload.priceMinor / 100, payload.compareAtPriceMinor / 100)
                        } else {
                            "ETB %,d".format(payload.priceMinor / 100)
                        }
                        onSubmit(payload)
                        showSuccessDialog = true
                    },
                    enabled = !editorBusy,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SellerProductOrange, contentColor = SellerProductOnColor),
                ) {
                    if (editorBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SellerProductOnColor)
                    } else {
                        Text("Submit for Review")
                    }
                }
            }
        }

        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showSuccessDialog = false },
                icon = {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = SellerProductGreen,
                        modifier = Modifier.size(36.dp),
                    )
                },
                title = {
                    Text(
                        "Product Submitted for Review",
                        fontWeight = FontWeight.Bold,
                        color = SellerProductText,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Your listing is now pending catalog moderation.",
                            color = SellerProductText,
                            fontSize = 14.sp,
                        )
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(SellerProductSoft),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SellerProductLine),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(submittedTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SellerProductText)
                                if (submittedCategoryBreadcrumb.isNotBlank()) {
                                    Text("Category: $submittedCategoryBreadcrumb", fontSize = 12.sp, color = SellerProductMuted)
                                }
                                Text("Price: $submittedPriceLabel", fontSize = 12.sp, color = SellerProductGreen, fontWeight = FontWeight.SemiBold)
                                Text("Status: Pending Moderation", fontSize = 11.sp, color = SellerProductOrange, fontWeight = FontWeight.Medium)
                            }
                        }
                        Text(
                            "Our team checks listing accuracy within 24 hours. You can continue updating draft details or return to your products list.",
                            color = SellerProductMuted,
                            fontSize = 12.sp,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSuccessDialog = false
                            onClose()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SellerProductGreen, contentColor = SellerProductOnColor),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Return to Products")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSuccessDialog = false }) {
                        Text("Continue Editing")
                    }
                },
            )
        }
    }
}

private fun productStatusText(status: String): String = when (status) {
    "draft", "pending" -> "Draft"
    "submitted" -> "Submitted"
    "under_review" -> "Under review"
    "changes_requested" -> "Changes requested"
    "approved" -> "Approved"
    "rejected" -> "Rejected"
    "suspended" -> "Suspended"
    "archived" -> "Archived"
    else -> status.replace("_", " ").replaceFirstChar { it.uppercase() }
}

@Composable
private fun SellerProductMediaSection(
    images: List<ShopProductImageDto>,
    busy: Boolean,
    message: String?,
    upload: com.adischat.app.feature.shop.data.ShopSellerMediaUploadState?,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
    onDelete: (String) -> Unit,
    onSetPrimary: (ShopProductImageDto) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var previewImage by remember { mutableStateOf<ShopProductImageDto?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Product images", color = SellerProductText, fontWeight = FontWeight.SemiBold)
            Text("${images.size}/10 uploaded. First image will be used as the store cover photo.", color = SellerProductMuted, fontSize = 12.sp)
            OutlinedButton(
                onClick = onAdd,
                enabled = !busy && images.size < 10,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (images.isEmpty()) "Add product images" else "Add more images (${10 - images.size} remaining)")
            }
        }
        if (message != null) Text(message, color = SellerProductRed, fontSize = 12.sp)
        upload?.let { progress ->
            if (progress.uploading) {
                Text("Uploading ${progress.completed + progress.failed + 1} of ${progress.total} images...", color = SellerProductMuted, fontSize = 12.sp)
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (progress.completed + progress.failed).toFloat() / progress.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (progress.failed > 0) {
                Text(progress.message ?: "${progress.failed} image uploads failed. Saved product details are unchanged.", color = SellerProductRed, fontSize = 12.sp)
                OutlinedButton(onClick = onRetry) { Text("Retry failed images") }
            } else {
                Text("${progress.completed} images uploaded.", color = SellerProductGreen, fontSize = 12.sp)
            }
        }
        if (images.isEmpty()) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(SellerProductSoft), border = androidx.compose.foundation.BorderStroke(1.dp, SellerProductLine)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = SellerProductMuted)
                    Spacer(Modifier.width(10.dp))
                    Text("Add real product photos before submitting for review.", color = SellerProductMuted)
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(images, key = { it.id.ifBlank { it.imageUrl.orEmpty() } }) { image ->
                    val index = images.indexOf(image)
                    val isCover = image.isPrimary == true || index == 0
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(SellerProductOnColor),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isCover) SellerProductGreen else SellerProductLine),
                    ) {
                        Column(Modifier.width(160.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SellerProductSoft)
                                    .clickable { previewImage = image },
                            ) {
                                ShopAssetImage(image.imageUrl, image.altText ?: "Product image", Modifier.fillMaxSize(), iconSize = 30.dp)
                                Surface(
                                    Modifier.align(Alignment.TopStart).padding(6.dp),
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isCover) SellerProductGreen else AdisGebeyaColors.DarkBackground.copy(alpha = 0.65f),
                                ) {
                                    Text(
                                        if (isCover) "Cover Photo" else "Photo ${index + 1}",
                                        color = SellerProductOnColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onMove(index, index - 1) }, enabled = !busy && index > 0, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Move image left", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onMove(index, index + 1) }, enabled = !busy && index < images.lastIndex, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Move image right", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDelete(image.id) }, enabled = !busy && image.id.isNotBlank(), modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove image", tint = SellerProductRed, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (!isCover) {
                                TextButton(onClick = { onSetPrimary(image) }, enabled = !busy && image.id.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                    Text("Set as cover", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewImage?.let { img ->
        AlertDialog(
            onDismissRequest = { previewImage = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = SellerProductGreen, modifier = Modifier.size(20.dp))
                    Text(if (img.isPrimary == true) "Cover Photo Preview" else "Product Photo Preview", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SellerProductSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        ShopAssetImage(img.imageUrl, img.altText ?: "Product image", Modifier.fillMaxSize(), iconSize = 48.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        img.altText?.takeIf(String::isNotBlank) ?: (if (img.isPrimary == true) "Primary store cover image" else "Product gallery image"),
                        color = SellerProductMuted,
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { previewImage = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun SellerSectionCard(
    number: Int,
    title: String,
    subtitle: String? = null,
    isComplete: Boolean? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(SellerProductSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isComplete == true) SellerProductGreen.copy(alpha = 0.35f) else SellerProductLine,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isComplete == true) SellerProductGreen else SellerProductMuted.copy(alpha = 0.15f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isComplete == true) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = SellerProductOnColor, modifier = Modifier.size(16.dp))
                        } else {
                            Text(
                                number.toString(),
                                color = SellerProductText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    Column {
                        Text(
                            title,
                            color = SellerProductText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                color = SellerProductMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                if (isComplete != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isComplete) SellerProductGreenSoft else AdisGebeyaColors.SoftOrange,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                if (isComplete) Icons.Default.Check else Icons.Default.PriorityHigh,
                                contentDescription = null,
                                tint = if (isComplete) SellerProductGreen else AdisGebeyaColors.Warning,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                if (isComplete) "Ready" else "Incomplete",
                                color = if (isComplete) SellerProductGreen else AdisGebeyaColors.Warning,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun ListingChecklistRow(
    label: String,
    passed: Boolean,
    detail: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                if (passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (passed) SellerProductGreen else AdisGebeyaColors.Warning,
                modifier = Modifier.size(16.dp),
            )
            Column {
                Text(
                    label,
                    color = SellerProductText,
                    fontSize = 13.sp,
                    fontWeight = if (passed) FontWeight.Normal else FontWeight.Medium,
                )
                if (!detail.isNullOrBlank()) {
                    Text(detail, color = SellerProductMuted, fontSize = 11.sp)
                }
            }
        }
        Text(
            if (passed) "Ready" else "Required",
            color = if (passed) SellerProductGreen else AdisGebeyaColors.Warning,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
        )
    }
}

private fun productStatusLabel(product: ShopSellerProductDto): String = "${productStatusText(product.moderationStatus)} • ${product.status.replace("_", " ")}"
