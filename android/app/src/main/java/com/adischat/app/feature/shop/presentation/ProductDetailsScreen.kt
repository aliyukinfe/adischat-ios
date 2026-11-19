package com.adischat.app.feature.shop.presentation

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.adischat.app.ui.home.AdaptiveFlowAround
import com.adischat.app.ui.home.LocalSidebarBounds
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopProductImageDto
import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.feature.shop.data.ShopPublicVariantDto
import com.adischat.app.ui.theme.AdisGebeyaColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ProductBackground = AdisGebeyaColors.Background
private val ProductSurface = AdisGebeyaColors.Surface
private val ProductSoft = AdisGebeyaColors.SecondarySurface
private val ProductText = AdisGebeyaColors.PrimaryText
private val ProductMuted = AdisGebeyaColors.SecondaryText
private val ProductGreen = AdisGebeyaColors.Primary
private val ProductGreenSoft = AdisGebeyaColors.PrimaryLight
private val ProductOrange = AdisGebeyaColors.AccentOrange
private val ProductBorder = AdisGebeyaColors.Border
private val ProductError = AdisGebeyaColors.Error
private val ProductGold = AdisGebeyaColors.Warning
private val ProductWhite = AdisGebeyaColors.OnColor

internal fun findVerifiedVariantImageIndex(
    images: List<ShopProductImageDto>,
    selectedVariant: ShopPublicVariantDto?,
    selectedColor: String? = null,
    allVariants: List<ShopPublicVariantDto> = emptyList(),
): Int? {
    if (images.isEmpty()) return null
    val variantImageIds = selectedVariant?.imageIds.orEmpty().filter { it.isNotBlank() }.toSet()
    if (variantImageIds.isNotEmpty()) {
        val idx = images.indexOfFirst { it.id in variantImageIds }
        if (idx >= 0) return idx
    }
    val colorVal = selectedColor ?: selectedVariant?.options?.get("COLOR")
        ?: selectedVariant?.options?.get("COLOUR")
        ?: selectedVariant?.options?.get("color")
    if (!colorVal.isNullOrBlank()) {
        val matchingVariant = allVariants.firstOrNull { v ->
            v.imageIds.isNotEmpty() &&
                (v.options["COLOR"].equals(colorVal, ignoreCase = true) ||
                 v.options["COLOUR"].equals(colorVal, ignoreCase = true) ||
                 v.options["color"].equals(colorVal, ignoreCase = true))
        }
        if (matchingVariant != null) {
            val idx = images.indexOfFirst { it.id in matchingVariant.imageIds }
            if (idx >= 0) return idx
        }
    }
    return null
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ProductDetailsScreen(
    product: ShopProductDto,
    categories: List<ShopCategoryDto>,
    catalogProducts: List<ShopProductDto>,
    recentlyViewedProducts: List<ShopProductDto>,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onAddToCart: (String?, Int) -> Unit,
    onCart: () -> Unit,
    onToggleFavorite: () -> Unit,
    onBuyNow: (String?, Int) -> Unit,
    onChatSeller: (String?) -> Unit,
    onVisitStore: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    saved: Boolean,
    cartCount: Int,
) {
    val context = LocalContext.current
    val allImages = remember(product.images) {
        product.images.orEmpty()
            .filter { !it.imageUrl.isNullOrBlank() }
            .sortedWith(
                compareByDescending<ShopProductImageDto> { it.isPrimary == true }
                    .thenBy { it.displayOrder ?: 0 },
            )
    }
    var purchaseAction by remember(product.id) { mutableStateOf<ProductPurchaseAction?>(null) }
    val purchaseOptionValues = remember(product.id) { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var purchaseVariantId by remember(product.id) { mutableStateOf<String?>(null) }
    val images = allImages
    val similar = remember(product.id, product.categorySlug, catalogProducts) {
        catalogProducts.filter {
            it.id != product.id &&
                !product.categorySlug.isNullOrBlank() &&
                it.categorySlug == product.categorySlug
        }.take(10)
    }
    val recommended = remember(product.id, similar, catalogProducts) {
        catalogProducts.filter { candidate ->
            candidate.id != product.id && similar.none { it.id == candidate.id }
        }.take(10)
    }
    val recent = remember(product.id, recentlyViewedProducts) {
        recentlyViewedProducts.distinctBy { it.id }.filter { it.id != product.id }.take(10)
    }
    val requiredOptionCodes = remember(product.options) { product.options.sortedBy { it.position }.map { it.code } }
    val selectedVariant = remember(product.variants, requiredOptionCodes, purchaseOptionValues.toMap(), purchaseVariantId) {
        if (requiredOptionCodes.isEmpty()) {
            product.variants.singleOrNull { it.id == purchaseVariantId && it.status == "active" }
                ?: directPurchaseVariant(product)
        } else {
            resolveExactVariant(product.variants, requiredOptionCodes, purchaseOptionValues)
        }
    }
    val selectedVariantOutOfStock = selectedVariant != null && selectedVariant.availableQuantity <= 0
    val allVariantsOutOfStock = product.variants.isNotEmpty() && product.variants.none { it.status == "active" && it.availableQuantity > 0 }
    val outOfStock = when {
        selectedVariant != null -> selectedVariantOutOfStock
        product.variants.isNotEmpty() -> allVariantsOutOfStock
        else -> product.stockStatus.orEmpty().contains("out", ignoreCase = true)
    }
    val requiresSelection = buyerSelectableConfigurationRequired(product)
    var lastPurchaseClickTime by remember(product.id) { mutableStateOf(0L) }
    fun purchase(action: ProductPurchaseAction) {
        val now = System.currentTimeMillis()
        if (now - lastPurchaseClickTime < 600L) return
        lastPurchaseClickTime = now
        if (requiresSelection) purchaseAction = action
        else {
            val directVariant = selectedVariant ?: directPurchaseVariant(product)
            if (directVariant != null && directVariant.availableQuantity <= 0) return
            val directVariantId = directVariant?.id
            if (action == ProductPurchaseAction.ADD_TO_CART) onAddToCart(directVariantId, 1)
            else onBuyNow(directVariantId, 1)
        }
    }
    val activeColor = purchaseOptionValues["COLOR"] ?: purchaseOptionValues["COLOUR"] ?: purchaseOptionValues["color"]
    val targetImageIndex = remember(images, selectedVariant?.id, activeColor) {
        findVerifiedVariantImageIndex(images, selectedVariant, activeColor, product.variants)
    }
    var fullscreenImage by remember(product.id) { mutableStateOf<Int?>(null) }
    BackHandler(enabled = fullscreenImage != null) { fullscreenImage = null }
    val shareProduct = {
        val storeName = product.store?.name?.takeIf { it.isNotBlank() }
        val shareText = buildString {
            append("Check out ${product.title}")
            if (storeName != null) append(" from $storeName")
            append(" on AdisGebeya.")
        }
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                },
                "Share product",
            ),
        )
    }

    Scaffold(
        containerColor = ProductBackground,
        topBar = {
            ProductDetailsTopBar(
                saved = saved,
                cartCount = cartCount,
                onBack = onBack,
                onSearch = onSearch,
                onShare = shareProduct,
                onFavorite = onToggleFavorite,
                onCart = onCart,
            )
        },
        bottomBar = {
            StickyPurchaseBar(
                outOfStock = outOfStock,
                onStore = onVisitStore,
                onChat = { onChatSeller(null) },
                onAddToCart = { purchase(ProductPurchaseAction.ADD_TO_CART) },
                onBuyNow = { purchase(ProductPurchaseAction.BUY_NOW) },
            )
        },
    ) { innerPadding ->
        key(product.id) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                        ProductImageGallery(
                            title = product.title,
                            images = images,
                            discountPercent = verifiedDiscountPercent(product),
                            onImageClick = { fullscreenImage = it },
                            targetImageIndex = targetImageIndex,
                        )
                    }
                }
                item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                        ProductSummary(
                            product = product,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            selectedVariant = selectedVariant,
                        )
                    }
                }
                item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                        ProductHighlights(product, Modifier.padding(horizontal = 4.dp))
                    }
                }
                if (com.adischat.app.feature.shop.data.ShopBenefitFlags.buyerBadgesEnabled) {
                    item {
                        AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                            VerifiedBenefitDetails(product.verifiedBenefits, Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
                item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                        DeliveryBenefits(Modifier.padding(horizontal = 4.dp))
                    }
                }
                item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                        ProductOptionsSummary(
                            product = product,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            selectedValues = purchaseOptionValues,
                            onSelectOption = { code, value ->
                                purchaseOptionValues[code] = value
                                clearInvalidDependentSelections(product.variants, code, purchaseOptionValues)
                            },
                        )
                    }
                }
                item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                        StoreMiniCard(
                            product = product,
                            onVisitStore = onVisitStore,
                            onChatSeller = { onChatSeller(null) },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                        ProductDetailsInformation(product, categories, Modifier.padding(horizontal = 4.dp))
                    }
                }
                item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                        ProductDescription(product, Modifier.padding(horizontal = 4.dp))
                    }
                }
                item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                        ProductReviewSummary(product, Modifier.padding(horizontal = 4.dp))
                    }
                }
                item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                        ReviewList(product, Modifier.padding(horizontal = 4.dp))
                    }
                }
                if (similar.isNotEmpty()) {
                    item {
                        AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                            SimilarProductsSection(similar, onProduct)
                        }
                    }
                }
                if (recommended.isNotEmpty()) {
                    item {
                        AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                            RecommendedProductsSection(recommended, onProduct)
                        }
                    }
                }
                if (recent.isNotEmpty()) {
                    item {
                        AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
                            RecentlyViewedSection(recent, onProduct)
                        }
                    }
                }
            }
        }
    }
    purchaseAction?.let { action ->
        ProductPurchaseVariantSheet(
            product = product,
            action = action,
            selectedValues = purchaseOptionValues,
            selectedVariantId = purchaseVariantId,
            onSelectedVariantId = { purchaseVariantId = it },
            onDismiss = { purchaseAction = null },
            onConfirm = { variantId, selectedQuantity ->
                purchaseAction = null
                if (action == ProductPurchaseAction.ADD_TO_CART) onAddToCart(variantId, selectedQuantity)
                else onBuyNow(variantId, selectedQuantity)
            },
        )
    }

    fullscreenImage?.let { selected ->
        FullscreenImageViewer(
            title = product.title,
            images = images,
            initialPage = selected,
            onDismiss = { fullscreenImage = null },
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ProductImageGallery(
    title: String,
    images: List<ShopProductImageDto>,
    discountPercent: Int?,
    onImageClick: (Int) -> Unit,
    targetImageIndex: Int? = null,
) {
    val pagerState = rememberPagerState(pageCount = { images.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val galleryHeight = (screenHeight * 0.42f).roundToInt().coerceIn(280, 420).dp

    LaunchedEffect(targetImageIndex) {
        if (targetImageIndex != null && targetImageIndex in images.indices && pagerState.currentPage != targetImageIndex) {
            pagerState.animateScrollToPage(targetImageIndex)
        }
    }

    Column(Modifier.fillMaxWidth().background(ProductSurface)) {
        Box(Modifier.fillMaxWidth().height(galleryHeight).background(ProductSurface)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ProductImage(
                    url = images.getOrNull(page)?.imageUrl,
                    description = images.getOrNull(page)?.altText ?: title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { if (images.isNotEmpty()) onImageClick(page) },
                )
            }
            if ((discountPercent ?: 0) > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                    color = ProductError,
                    contentColor = ProductWhite,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        "-${discountPercent}%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (images.isNotEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                    color = ProductText.copy(alpha = 0.76f),
                    contentColor = ProductWhite,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        "${pagerState.currentPage + 1}/${images.size}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (images.size > 1) {
            ProductThumbnailStrip(
                title = title,
                images = images,
                selectedIndex = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            )
        }
    }
}

@Composable
internal fun ProductThumbnailStrip(
    title: String,
    images: List<ShopProductImageDto>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(images.size, key = { index -> images[index].id.ifBlank { images[index].imageUrl.orEmpty() } }) { index ->
            val selected = index == selectedIndex
            ProductImage(
                url = images[index].imageUrl,
                description = "View image ${index + 1} of $title",
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ProductSurface)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) ProductGreen else ProductBorder,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(index) }
                    .padding(3.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun FullscreenImageViewer(
    title: String,
    images: List<ShopProductImageDto>,
    initialPage: Int,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            val previousStatusColor = window?.statusBarColor
            val previousNavigationColor = window?.navigationBarColor
            val previousNavigationContrast = if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced else null
            val controller = window?.let { WindowCompat.getInsetsController(it, dialogView) }
            val previousLightStatus = controller?.isAppearanceLightStatusBars
            val previousLightNavigation = controller?.isAppearanceLightNavigationBars
            window?.statusBarColor = AndroidColor.BLACK
            window?.navigationBarColor = AndroidColor.BLACK
            if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced = false
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            onDispose {
                if (previousStatusColor != null) window?.statusBarColor = previousStatusColor
                if (previousNavigationColor != null) window?.navigationBarColor = previousNavigationColor
                if (Build.VERSION.SDK_INT >= 29 && previousNavigationContrast != null) window?.isNavigationBarContrastEnforced = previousNavigationContrast
                if (previousLightStatus != null) controller?.isAppearanceLightStatusBars = previousLightStatus
                if (previousLightNavigation != null) controller?.isAppearanceLightNavigationBars = previousLightNavigation
            }
        }
        val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(images.indices), pageCount = { images.size })
        val scope = rememberCoroutineScope()
        val zoomedPages = remember(images) { mutableStateMapOf<Int, Boolean>() }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = zoomedPages[pagerState.currentPage] != true,
            ) { page ->
                ZoomableProductImage(images[page], title) { zoomed -> zoomedPages[page] = zoomed }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart)
                    .size(48.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.58f)),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close image viewer", tint = ProductWhite)
            }
            Surface(
                modifier = Modifier.statusBarsPadding().padding(16.dp).align(Alignment.TopCenter),
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.58f),
                contentColor = ProductWhite,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    "${pagerState.currentPage + 1}/${images.size}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LazyRow(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 56.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(images.size, key = { it }) { index ->
                    ProductImage(
                        url = images[index].imageUrl,
                        description = "Open image ${index + 1}",
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                            .border(
                                if (index == pagerState.currentPage) 2.dp else 1.dp,
                                if (index == pagerState.currentPage) ProductGreen else ProductWhite.copy(alpha = 0.45f),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ZoomableProductImage(image: ShopProductImageDto, title: String, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember(image.id, image.imageUrl) { mutableFloatStateOf(1f) }
    var offsetX by remember(image.id, image.imageUrl) { mutableFloatStateOf(0f) }
    var offsetY by remember(image.id, image.imageUrl) { mutableFloatStateOf(0f) }
    var viewport by remember(image.id, image.imageUrl) { mutableStateOf(IntSize.Zero) }
    var loading by remember(image.id, image.imageUrl) { mutableStateOf(true) }
    var failed by remember(image.id, image.imageUrl) { mutableStateOf(false) }
    fun clampPan(x: Float, y: Float, zoom: Float) {
        offsetX = x.coerceIn(-viewport.width * (zoom - 1f) / 2f, viewport.width * (zoom - 1f) / 2f)
        offsetY = y.coerceIn(-viewport.height * (zoom - 1f) / 2f, viewport.height * (zoom - 1f) / 2f)
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (nextScale == 1f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            clampPan(offsetX + panChange.x, offsetY + panChange.y, nextScale)
        }
        if ((scale > 1f) != (nextScale > 1f)) onZoomChanged(nextScale > 1f)
        scale = nextScale
    }
    val dragModifier = if (scale > 1f) Modifier.pointerInput(image.id, image.imageUrl) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            clampPan(offsetX + dragAmount.x, offsetY + dragAmount.y, scale)
        }
    } else Modifier
    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { viewport = it }, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = image.imageUrl,
            contentDescription = image.altText ?: title,
            contentScale = ContentScale.Fit,
            onLoading = { loading = true; failed = false },
            onSuccess = { loading = false; failed = false },
            onError = { loading = false; failed = true },
            modifier = Modifier.fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                .pointerInput(image.id, image.imageUrl) {
                    detectTapGestures(onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        if (scale == 1f) { offsetX = 0f; offsetY = 0f }
                        onZoomChanged(scale > 1f)
                    })
                }
                .then(dragModifier)
                .transformable(transformState, canPan = { scale > 1f }),
        )
        if (loading) CircularProgressIndicator(Modifier.size(30.dp), color = ProductWhite, strokeWidth = 2.dp)
        if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.BrokenImage, contentDescription = null, tint = ProductWhite)
            Text("Image unavailable", color = ProductWhite)
        }
    }
}

@Composable
private fun ProductImage(url: String?, description: String?, modifier: Modifier) {
    Box(modifier.background(ProductSurface), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Icon(
                Icons.Default.Storefront,
                contentDescription = description,
                tint = ProductMuted,
                modifier = Modifier.size(64.dp),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun ProductSummary(
    product: ShopProductDto,
    modifier: Modifier = Modifier,
    selectedVariant: ShopPublicVariantDto? = null,
) {
    val displayPriceMinor = selectedVariant?.priceMinor ?: product.priceMinor
    val oldPrice = selectedVariant?.compareAtPriceMinor ?: homePreviousPriceMinor(product)
    val savings = oldPrice?.minus(displayPriceMinor)?.takeIf { it > 0 }
    val discountPercent = if (oldPrice != null && oldPrice > displayPriceMinor) {
        (((oldPrice - displayPriceMinor).toDouble() / oldPrice) * 100).roundToInt().coerceIn(1, 99)
    } else verifiedDiscountPercent(product)

    ProductSectionCard(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            product.store?.name?.takeIf { it.isNotBlank() }?.let { storeName ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(storeName, color = ProductGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    storeApprovalDescription(product.store?.approvalStatus)?.let { desc ->
                        Icon(Icons.Default.Verified, contentDescription = desc, tint = ProductGreen, modifier = Modifier.size(15.dp))
                    }
                }
            }
            product.brand?.takeIf { it.isNotBlank() }?.let { brandName ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ProductGreenSoft,
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Icon(Icons.Default.Sell, contentDescription = null, tint = ProductGreen, modifier = Modifier.size(11.dp))
                        Text(brandName, color = ProductGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val priceLabel = if (selectedVariant != null) {
                productPrice(selectedVariant.priceMinor)
            } else {
                product.priceMaxMinor?.takeIf { product.hasVariants && it > product.priceMinor }
                    ?.let { "${productPrice(product.priceMinor)} – ${productPrice(it).removePrefix("ETB ")}" }
                    ?: productPrice(product.priceMinor)
            }
            Text(priceLabel, color = ProductGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            oldPrice?.let {
                Text(productPrice(it), color = ProductMuted, fontSize = 13.sp, textDecoration = TextDecoration.LineThrough)
            }
            discountPercent?.takeIf { it > 0 }?.let { pct ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ProductError,
                    contentColor = ProductWhite,
                ) {
                    Text(
                        "-${pct}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }

        savings?.let {
            Text("Save ${productPrice(it)}", color = ProductOrange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        Text(product.title, color = ProductText, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val stockLabel = when {
                selectedVariant != null -> when {
                    selectedVariant.availableQuantity <= 0 -> "Out of Stock"
                    selectedVariant.availableQuantity in 1..5 -> "Low Stock (Only ${selectedVariant.availableQuantity} left)"
                    else -> "In Stock (${selectedVariant.availableQuantity} available)"
                }
                product.variants.isNotEmpty() -> when {
                    product.variants.none { it.status == "active" && it.availableQuantity > 0 } -> "Out of Stock"
                    else -> "Options Available"
                }
                else -> product.stockStatus?.ifBlank { "In Stock" } ?: "In Stock"
            }
            StockPill(stockLabel)

            if (!selectedVariant?.sku.isNullOrBlank()) {
                Text("SKU: ${selectedVariant?.sku}", color = ProductMuted, fontSize = 12.sp)
            }
        }

        if ((product.reviewCount ?: 0) > 0 || (product.soldCount ?: 0) > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if ((product.reviewCount ?: 0) > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = ProductGold, modifier = Modifier.size(16.dp))
                        Text(
                            "${"%.1f".format(product.averageRating ?: 0.0)}",
                            color = ProductText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "(${product.reviewCount} reviews)",
                            color = ProductMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
                if ((product.soldCount ?: 0) > 0) {
                    if ((product.reviewCount ?: 0) > 0) {
                        Text("•", color = ProductMuted, fontSize = 12.sp)
                    }
                    Text("${product.soldCount} sold", color = ProductMuted, fontSize = 12.sp)
                }
            }
        }

        ExpandableProductText(
            text = product.shortDescription,
            collapsedLines = 3,
            expandLabel = "More",
            collapseLabel = "Less",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = ProductMuted,
                lineHeight = 20.sp,
            ),
        )
    }
}

@Composable
internal fun ProductOptions(product: ShopProductDto, modifier: Modifier = Modifier) {
    // The current public product model does not expose variants. Rendering a
    // selector here would create choices that checkout cannot validate.
}

@Composable
internal fun DeliveryBenefits(modifier: Modifier = Modifier) {
    ProductSectionCard(modifier) {
        ProductSectionTitle("Delivery & protection")
        BenefitRow(Icons.Default.Security, "Buyer Protection", "Protected order records and support")
        BenefitRow(Icons.Default.LocalShipping, "Delivery tracking", "Follow seller fulfillment updates")
        BenefitRow(Icons.Default.AssignmentReturn, "Issue support", "Eligible orders can request help")
    }
}

@Composable
private fun BenefitRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(ProductGreenSoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = ProductGreen, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = ProductText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = ProductMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
internal fun ProductDescription(product: ShopProductDto, modifier: Modifier = Modifier) {
    ProductDescriptionSection(product, modifier)
}

@Composable
internal fun ProductSpecifications(product: ShopProductDto, modifier: Modifier = Modifier) {
    ProductDetailsInformation(product, emptyList(), modifier)
}

@Composable
internal fun ProductReviewSummary(product: ShopProductDto, modifier: Modifier = Modifier) {
    ProductSectionCard(modifier) {
        ProductSectionTitle("Customer reviews")
        if ((product.reviewCount ?: 0) <= 0) {
            Text(
                "No customer reviews yet. Ratings and reviews will appear here after verified purchases.",
                color = ProductMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "${"%.1f".format(product.averageRating ?: 0.0)}",
                    color = ProductText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        val score = (product.averageRating ?: 0.0).roundToInt()
                        repeat(5) { index ->
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = if (index < score) ProductGold else ProductBorder,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        "${product.reviewCount} customer reviews",
                        color = ProductMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReviewList(product: ShopProductDto, modifier: Modifier = Modifier) {
    // The product feed exposes aggregate ratings only. Individual review cards are
    // intentionally omitted until the existing API supplies reviewer-safe records.
}

@Composable
internal fun StoreMiniCard(
    product: ShopProductDto,
    onVisitStore: () -> Unit,
    onChatSeller: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = product.store ?: return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ProductSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ProductBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(ProductGreenSoft),
                contentAlignment = Alignment.Center,
            ) {
                if (store.logoUrl.isNullOrBlank()) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = ProductGreen, modifier = Modifier.size(24.dp))
                } else {
                    AsyncImage(store.logoUrl, store.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        store.name ?: "Marketplace store",
                        color = ProductText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    storeApprovalDescription(store.approvalStatus)?.let { description ->
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Verified, contentDescription = description, tint = ProductGreen, modifier = Modifier.size(15.dp))
                    }
                }
                val ratingText = store.averageStoreRating?.let { "${"%.1f".format(it)} store rating" }
                val locationText = store.city?.takeIf(String::isNotBlank)
                val subText = listOfNotNull(ratingText, locationText).joinToString(" • ")
                if (subText.isNotBlank()) {
                    Text(subText, color = ProductMuted, fontSize = 12.sp)
                }
            }
            IconButton(
                onClick = onChatSeller,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(ProductGreenSoft),
            ) {
                Icon(Icons.Default.Message, contentDescription = "Chat with seller", tint = ProductGreen, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(
                onClick = onVisitStore,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ProductGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Text("Visit", color = ProductGreen, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
internal fun SimilarProductsSection(products: List<ShopProductDto>, onProduct: (ShopProductDto) -> Unit) {
    ProductRecommendationRail("Similar products", products, onProduct)
}

@Composable
internal fun RecommendedProductsSection(products: List<ShopProductDto>, onProduct: (ShopProductDto) -> Unit) {
    ProductRecommendationRail("Recommended for you", products, onProduct)
}

@Composable
internal fun RecentlyViewedSection(products: List<ShopProductDto>, onProduct: (ShopProductDto) -> Unit) {
    ProductRecommendationRail("Recently viewed", products, onProduct)
}

@Composable
private fun ProductRecommendationRail(title: String, products: List<ShopProductDto>, onProduct: (ShopProductDto) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = ProductText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(products, key = { it.id }) { product ->
                Card(
                    modifier = Modifier.width(156.dp).height(248.dp).clickable { onProduct(product) },
                    colors = CardDefaults.cardColors(containerColor = ProductSurface),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProductBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp),
                ) {
                    Column {
                        ProductImage(
                            url = product.images.orEmpty().sortedByDescending { it.isPrimary == true }.firstOrNull()?.imageUrl,
                            description = product.title,
                            modifier = Modifier.fillMaxWidth().height(156.dp),
                        )
                        Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(product.title, color = ProductText, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(16.dp)) {
                                if ((product.reviewCount ?: 0) > 0) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = ProductGold, modifier = Modifier.size(13.dp))
                                    Text(" ${"%.1f".format(product.averageRating ?: 0.0)} (${product.reviewCount})", color = ProductMuted, fontSize = 11.sp)
                                }
                            }
                            Text(productPrice(product.priceMinor), color = ProductGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProductDetailsTopBar(
    saved: Boolean,
    cartCount: Int,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onCart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sidebar = LocalSidebarBounds.current
    val startPad = if (sidebar != null) 60.dp else 8.dp
    Surface(modifier.fillMaxWidth(), color = ProductSurface, shadowElevation = 2.dp) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(start = startPad, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OverlayIcon(Icons.Default.ArrowBack, "Back", onBack)
        Row(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(999.dp))
                .background(ProductWhite.copy(alpha = 0.94f))
                .border(1.dp, ProductBorder, RoundedCornerShape(999.dp))
                .clickable { onSearch() }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = ProductMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("Search AdisGebeya", color = ProductMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OverlayIcon(Icons.Default.Share, "Share product", onShare)
        OverlayIcon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (saved) "Remove from favorites" else "Add to favorites", onFavorite, if (saved) ProductError else ProductText)
        BadgedBox(
            badge = {
                if (cartCount > 0) Badge(containerColor = ProductError, contentColor = ProductWhite) {
                    Text(cartCount.coerceAtMost(99).toString(), fontSize = 9.sp)
                }
            },
        ) {
            OverlayIcon(Icons.Default.ShoppingCart, "Cart", onCart)
        }
    }
    }
}

@Composable
private fun OverlayIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = ProductText,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).clip(CircleShape)
            .background(ProductWhite.copy(alpha = 0.94f))
            .border(1.dp, ProductBorder, CircleShape),
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun StickyPurchaseBar(
    outOfStock: Boolean,
    onStore: () -> Unit,
    onChat: () -> Unit,
    onAddToCart: () -> Unit,
    onBuyNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ProductSurface,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = ProductBorder)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PurchaseIconAction(
                    icon = Icons.Default.Storefront,
                    description = "Visit store",
                    label = "Store",
                    onClick = onStore,
                )
                PurchaseIconAction(
                    icon = Icons.Default.Message,
                    description = "Chat with seller",
                    label = "Chat",
                    onClick = onChat,
                )
                OutlinedButton(
                    onClick = onAddToCart,
                    enabled = !outOfStock,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (outOfStock) ProductBorder else ProductGreen,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ProductGreen,
                        disabledContentColor = ProductMuted,
                    ),
                ) {
                    val label = if (outOfStock) "Out of Stock" else "Add to Cart"
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Button(
                    onClick = onBuyNow,
                    enabled = !outOfStock,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProductGreen,
                        disabledContainerColor = ProductBorder,
                        contentColor = ProductWhite,
                        disabledContentColor = ProductMuted,
                    ),
                ) {
                    Text(if (outOfStock) "Unavailable" else "Buy Now", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PurchaseIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val tint = if (enabled) ProductGreen else ProductMuted
    Column(
        Modifier.width(50.dp).height(50.dp).clip(RoundedCornerShape(14.dp))
            .background(ProductSoft).border(1.dp, ProductBorder, RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        Text(label, color = tint, fontSize = 9.sp)
    }
}

@Composable
internal fun ProductSectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ProductSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ProductBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}

@Composable
internal fun ProductSectionTitle(text: String) {
    Text(text, color = ProductText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StockPill(stockStatus: String?) {
    val label = stockStatus.orEmpty().replace("_", " ").replaceFirstChar { it.uppercase() }.ifBlank { "Available" }
    val isOut = label.contains("out", ignoreCase = true)
    val isLow = label.contains("low", ignoreCase = true) || label.contains("only", ignoreCase = true)
    val bgColor = when {
        isOut -> AdisGebeyaColors.ErrorSoft
        isLow -> AdisGebeyaColors.SoftOrange
        else -> ProductGreenSoft
    }
    val textColor = when {
        isOut -> ProductError
        isLow -> AdisGebeyaColors.Warning
        else -> ProductGreen
    }
    Surface(color = bgColor, shape = RoundedCornerShape(999.dp)) {
        Text(
            label,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun productPrice(minor: Int): String = "ETB ${"%,d".format(minor / 100)}"
