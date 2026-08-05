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
    categories: List<ShopCategoryDto>,
    onCreateProduct: () -> Unit,
    onEditProduct: (String) -> Unit,
    onCloseEditor: () -> Unit,
    onSaveProduct: (String, ShopSellerProductDraftPayload) -> Unit,
    onSubmitProduct: (String) -> Unit,
    onUploadProductMedia: (String, ByteArray, String, Int, Int, String) -> Unit,
    onDeleteProductMedia: (String, String) -> Unit,
    onReorderProductMedia: (String, List<String>, String) -> Unit,
) {
    val selected = protectedState.selectedSellerProduct
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Products", color = SellerProductText, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("Create drafts and submit them for review.", color = SellerProductMuted, fontSize = 13.sp)
            }
            Button(
                onClick = onCreateProduct,
                enabled = !protectedState.loading,
                colors = ButtonDefaults.buttonColors(containerColor = SellerProductGreen, contentColor = SellerProductOnColor),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New")
            }
        }

        selected?.let { product ->
            SellerProductEditor(
                product = product,
                categories = categories,
                busy = protectedState.busyIds.contains(product.id),
                onClose = onCloseEditor,
                onSave = { onSaveProduct(product.id, it) },
                onSubmit = { onSubmitProduct(product.id) },
                onUploadImage = { bytes, mime, width, height -> onUploadProductMedia(product.id, bytes, mime, width, height, product.title.ifBlank { "Product image" }) },
                onDeleteImage = { imageId -> onDeleteProductMedia(product.id, imageId) },
                onReorderImages = { imageIds, primaryId -> onReorderProductMedia(product.id, imageIds, primaryId) },
            )
        }

        if (protectedState.sellerProducts.isEmpty()) {
            EmptyCard("No seller products yet. Create your first draft product.")
        } else {
            protectedState.sellerProducts.forEach { product ->
                SellerProductCard(product = product, busy = protectedState.busyIds.contains(product.id), onEdit = { onEditProduct(product.id) }, onSubmit = { onSubmitProduct(product.id) })
            }
        }
    }
}

@Composable
private fun SellerProductCard(product: ShopSellerProductDto, busy: Boolean, onEdit: () -> Unit, onSubmit: () -> Unit) {
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
    busy: Boolean,
    onClose: () -> Unit,
    onSave: (ShopSellerProductDraftPayload) -> Unit,
    onSubmit: () -> Unit,
    onUploadImage: (ByteArray, String, Int, Int) -> Unit,
    onDeleteImage: (String) -> Unit,
    onReorderImages: (List<String>, String) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(product.id) { mutableStateOf(product.title.takeUnless { it == "Untitled product" }.orEmpty()) }
    var brand by remember(product.id) { mutableStateOf(product.brand.orEmpty()) }
    var selectedCategoryId by remember(product.id) { mutableStateOf(product.categoryId.orEmpty()) }
    var shortDescription by remember(product.id) { mutableStateOf(product.shortDescription.orEmpty()) }
    var description by remember(product.id) { mutableStateOf(product.description.orEmpty()) }
    var price by remember(product.id) { mutableStateOf(if (product.priceMinor > 0) (product.priceMinor / 100).toString() else "") }
    var stock by remember(product.id) { mutableStateOf(product.inventory.firstOrNull { it.variantId == null }?.quantityAvailable?.takeIf { it > 0 }?.toString().orEmpty()) }
    var shipping by remember(product.id) { mutableStateOf(product.shippingDetails?.get("notes")?.toString().orEmpty()) }
    var returns by remember(product.id) { mutableStateOf(product.returnPolicy?.get("notes")?.toString().orEmpty()) }
    var specs by remember(product.id) { mutableStateOf(product.specifications?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }.orEmpty()) }
    var mediaMessage by remember(product.id) { mutableStateOf<String?>(null) }
    val uploadedImages = product.images
        .filter { it.imageUrl?.isNotBlank() == true }
        .sortedWith(compareByDescending<ShopProductImageDto> { it.isPrimary == true }.thenBy { it.displayOrder ?: 0 })
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        mediaMessage = null
        val remaining = (10 - uploadedImages.size).coerceAtLeast(0)
        uris.take(remaining).forEach { uri ->
            val processed = runCatching { processSellerProductImage(context, uri) }.getOrElse { error ->
                mediaMessage = error.message ?: "Image could not be prepared."
                null
            }
            if (processed != null) onUploadImage(processed.bytes, processed.mimeType, processed.width, processed.height)
        }
        if (uris.size > remaining) mediaMessage = "A product can have up to 10 images."
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(SellerProductSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, SellerProductGreen.copy(alpha = .25f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Product draft", color = SellerProductText, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(productStatusLabel(product), color = SellerProductMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close product editor") }
            }
            OutlinedTextField(title, { title = it }, label = { Text("Product name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = shopOutlinedTextFieldColors())
            OutlinedTextField(brand, { brand = it }, label = { Text("Brand optional") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = shopOutlinedTextFieldColors())
            Text("Category", color = SellerProductText, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { category ->
                    val selected = selectedCategoryId == category.id
                    OutlinedButton(
                        onClick = { selectedCategoryId = category.id },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selected) SellerProductGreen else SellerProductText),
                    ) { Text(category.name, maxLines = 1) }
                }
            }
            OutlinedTextField(shortDescription, { shortDescription = it }, label = { Text("Short description") }, modifier = Modifier.fillMaxWidth(), minLines = 2, colors = shopOutlinedTextFieldColors())
            OutlinedTextField(description, { description = it }, label = { Text("Full description") }, modifier = Modifier.fillMaxWidth(), minLines = 4, colors = shopOutlinedTextFieldColors())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(price, { price = it.filter { char -> char.isDigit() } }, label = { Text("Price ETB") }, modifier = Modifier.weight(1f), singleLine = true, colors = shopOutlinedTextFieldColors())
                OutlinedTextField(stock, { stock = it.filter { char -> char.isDigit() } }, label = { Text("Stock") }, modifier = Modifier.weight(1f), singleLine = true, colors = shopOutlinedTextFieldColors())
            }
            SellerProductMediaSection(
                images = uploadedImages,
                busy = busy,
                message = mediaMessage,
                onAdd = { imagePicker.launch("image/*") },
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
            OutlinedTextField(shipping, { shipping = it }, label = { Text("Shipping notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2, colors = shopOutlinedTextFieldColors())
            OutlinedTextField(returns, { returns = it }, label = { Text("Return policy") }, modifier = Modifier.fillMaxWidth(), minLines = 2, colors = shopOutlinedTextFieldColors())
            OutlinedTextField(specs, { specs = it }, label = { Text("Specifications, one per line") }, modifier = Modifier.fillMaxWidth(), minLines = 3, colors = shopOutlinedTextFieldColors())
            if (product.validationErrors.isNotEmpty()) ErrorCardMessage(product.validationErrors.joinToString("\n"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(.8f), shape = RoundedCornerShape(14.dp)) { Text("Close") }
                Button(
                    onClick = {
                        onSave(
                            ShopSellerProductDraftPayload(
                                title = title.trim(),
                                brand = brand.trim().takeIf { it.isNotBlank() },
                                categoryId = selectedCategoryId.takeIf { it.isNotBlank() },
                                shortDescription = shortDescription.trim(),
                                description = description.trim(),
                                priceMinor = (price.toIntOrNull() ?: 0) * 100,
                                stockQuantity = stock.toIntOrNull() ?: 0,
                                images = null,
                                variants = null,
                                shippingDetails = mapOf("notes" to shipping.trim()),
                                returnPolicy = mapOf("notes" to returns.trim()),
                                specifications = specs.lines().mapNotNull { line ->
                                    val parts = line.split(":", limit = 2)
                                    if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim() to parts[1].trim() else null
                                }.toMap(),
                            )
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SellerProductGreen, contentColor = SellerProductOnColor),
                ) { Text("Save") }
                Button(
                    onClick = onSubmit,
                    enabled = !busy && uploadedImages.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SellerProductOrange, contentColor = SellerProductOnColor),
                ) { Text("Submit") }
            }
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
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onSetPrimary: (ShopProductImageDto) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Product images", color = SellerProductText, fontWeight = FontWeight.SemiBold)
            Text("${images.size}/10 uploaded. Submit requires at least one image.", color = SellerProductMuted, fontSize = 12.sp)
            OutlinedButton(
                onClick = onAdd,
                enabled = !busy && images.size < 10,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (images.isEmpty()) "Add product images" else "Add more images")
            }
        }
        if (message != null) Text(message, color = SellerProductRed, fontSize = 12.sp)
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
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(SellerProductOnColor), border = androidx.compose.foundation.BorderStroke(1.dp, if (image.isPrimary == true) SellerProductGreen else SellerProductLine)) {
                        Column(Modifier.width(156.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(SellerProductSoft)) {
                                ShopAssetImage(image.imageUrl, image.altText ?: "Product image", Modifier.fillMaxSize(), iconSize = 30.dp)
                                if (image.isPrimary == true) {
                                    Surface(Modifier.align(Alignment.TopStart).padding(6.dp), shape = RoundedCornerShape(999.dp), color = SellerProductGreen) {
                                        Text("Primary", color = SellerProductOnColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                    }
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
                            if (image.isPrimary != true) {
                                TextButton(onClick = { onSetPrimary(image) }, enabled = !busy && image.id.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                    Text("Set primary", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun productStatusLabel(product: ShopSellerProductDto): String = "${productStatusText(product.moderationStatus)} • ${product.status.replace("_", " ")}"
