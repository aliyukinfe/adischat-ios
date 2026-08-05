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

private val SellerOrderSurface = AdisGebeyaColors.Surface
private val SellerOrderSoft = AdisGebeyaColors.SecondarySurface
private val SellerOrderText = AdisGebeyaColors.PrimaryText
private val SellerOrderMuted = AdisGebeyaColors.SecondaryText
private val SellerOrderGreen = AdisGebeyaColors.Primary
private val SellerOrderGreenSoft = AdisGebeyaColors.PrimaryLight
private val SellerOrderOrange = AdisGebeyaColors.AccentOrange
private val SellerOrderLine = AdisGebeyaColors.Divider
private val SellerOrderRed = AdisGebeyaColors.Error
private val SellerOrderRedSoft = AdisGebeyaColors.ErrorSoft
private val SellerOrderAmberSoft = AdisGebeyaColors.SoftOrange
private val SellerOrderOnColor = AdisGebeyaColors.OnColor


@Composable
internal fun SellerOrderGroupCard(
    group: ShopOrderGroupDto,
    busy: Boolean,
    onSellerAction: (String, String, String, String?, String?, String?) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var trackingNumber by remember(group.id) { mutableStateOf("") }
    var deliveryCode by remember(group.id) { mutableStateOf("") }
    var showRejectDialog by remember(group.id) { mutableStateOf(false) }
    val fulfillment = group.fulfillmentStatus.orEmpty().ifBlank { group.status }
    val addressText = group.deliveryAddress?.formatAddress().orEmpty()
    val phone = group.customer?.phone.orEmpty().ifBlank { group.deliveryAddress?.phone.orEmpty() }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(SellerOrderOnColor)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(group.storeNameSnapshot ?: "Seller order", color = SellerOrderText, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text("Order ${group.order?.orderNumber ?: "pending"}", color = SellerOrderMuted)
                    group.order?.createdAt?.let { Text(it, color = SellerOrderMuted, fontSize = 12.sp) }
                }
                if (fulfillment == "delivered") {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SellerOrderGreen)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniBadge(fulfillmentStatusLabel(fulfillment), fulfillmentStatusBg(fulfillment), fulfillmentStatusColor(fulfillment))
                (group.paymentStatus ?: group.payment?.status)?.let { MiniBadge(paymentStatusLabel(it), paymentStatusBg(it), paymentStatusColor(it)) }
            }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(SellerOrderSoft)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Order summary", color = SellerOrderText, fontWeight = FontWeight.Black)
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
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(SellerOrderSoft)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Customer information", color = SellerOrderText, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(SellerOrderGreenSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = SellerOrderGreen)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(group.customer?.displayName ?: group.deliveryAddress?.recipientName ?: "Customer", color = SellerOrderText, fontWeight = FontWeight.SemiBold)
                            if (phone.isNotBlank()) Text(phone, color = SellerOrderMuted, fontSize = 13.sp)
                        }
                    }
                    if (addressText.isNotBlank()) Text(addressText, color = SellerOrderMuted, lineHeight = 19.sp)
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
            Text("Ordered items", color = SellerOrderText, fontWeight = FontWeight.Black)
            group.items.orEmpty().forEach { item ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SellerOrderSoft).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(SellerOrderOnColor), contentAlignment = Alignment.Center) {
                        ShopAssetImage(item.imageSnapshot, item.titleSnapshot, Modifier.fillMaxSize(), iconSize = 26.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.titleSnapshot, color = SellerOrderText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${item.quantity} x ${formatEtb(item.unitPriceMinor)}", color = SellerOrderMuted, fontSize = 12.sp)
                    }
                    Text(formatEtb(item.lineTotalMinor), color = SellerOrderGreen, fontWeight = FontWeight.Black)
                }
            }
            if (group.history.orEmpty().isNotEmpty()) {
                Text("Timeline", color = SellerOrderText, fontWeight = FontWeight.Black)
                group.history.orEmpty().forEach { event ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(trackingStatusColor(event.status.ifBlank { event.nextStatus.orEmpty() })))
                        Column {
                            Text(event.publicMessage ?: event.nextStatus ?: event.status, color = SellerOrderText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(event.occurredAt ?: event.createdAt.orEmpty(), color = SellerOrderMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
            SellerBuyerProtectionSection(group.issues)
            if (fulfillment == "packed") {
                OutlinedTextField(
                    value = trackingNumber,
                    onValueChange = { trackingNumber = it },
                    label = { Text("Tracking number") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = shopOutlinedTextFieldColors(),
                    singleLine = true,
                )
            }
            if (fulfillment in listOf("handed_to_carrier", "shipped", "out_for_delivery")) {
                OutlinedTextField(
                    value = deliveryCode,
                    onValueChange = { value -> deliveryCode = value.filter { it.isDigit() }.take(6) },
                    label = { Text("Customer delivery code") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = shopOutlinedTextFieldColors(),
                    singleLine = true,
                )
            }
            when (fulfillment) {
                "awaiting_seller_confirmation", "created" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SellerActionButton("Accept", busy) { onSellerAction(group.id, "accept", "", null, null, null) }
                    SellerActionButton("Reject", busy) { showRejectDialog = true }
                }
                "accepted" -> SellerActionButton("Processing", busy) { onSellerAction(group.id, "processing", "", null, null, null) }
                "processing" -> SellerActionButton("Packed", busy) { onSellerAction(group.id, "packed", "", null, null, null) }
                "packed" -> SellerActionButton("Ship", busy) { onSellerAction(group.id, "handed_to_carrier", "", null, "Seller delivery", trackingNumber.ifBlank { null }) }
                "handed_to_carrier", "shipped", "out_for_delivery" -> SellerActionButton("Delivered", busy || deliveryCode.length != 6) {
                    onSellerAction(group.id, "delivered", "", null, null, deliveryCode)
                }
                "delivered" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SellerOrderGreen)
                    Text("Done", color = SellerOrderGreen, fontWeight = FontWeight.Black)
                }
                "cancelled", "rejected" -> Text(fulfillment.replaceFirstChar { it.uppercase() }, color = SellerOrderMuted, fontWeight = FontWeight.SemiBold)
                else -> EmptyCard("No seller action is available for this order state.")
            }
        }
    }
    if (showRejectDialog) {
        SellerRejectOrderDialog(
            orderReference = group.order?.orderNumber ?: group.id.takeLast(8),
            busy = busy,
            onDismiss = { if (!busy) showRejectDialog = false },
            onReject = { reasonCode, explanation ->
                showRejectDialog = false
                onSellerAction(group.id, "reject", explanation, reasonCode, null, null)
            },
        )
    }
}

@Composable
private fun SellerBuyerProtectionSection(issues: List<ShopOrderIssueDto>) {
    if (issues.isEmpty()) return
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(SellerOrderSoft)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Buyer Protection", color = SellerOrderText, fontWeight = FontWeight.Black)
            issues.forEach { issue ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniBadge(issueStatusLabel(issue.status), orderStatusBg(issue.status), orderStatusColor(issue.status))
                        Text(issue.reasonLabel ?: issueReasonLabel(issue.reasonCode), color = SellerOrderText, fontWeight = FontWeight.SemiBold)
                    }
                    if (issue.description.isNotBlank()) {
                        Text(issue.description, color = SellerOrderMuted, lineHeight = 19.sp)
                    }
                    issue.createdAt?.let { Text("Submitted $it", color = SellerOrderMuted, fontSize = 11.sp) }
                    val latestUpdate = issue.events.lastOrNull { it.publicMessage?.isNotBlank() == true }
                    latestUpdate?.publicMessage?.let { message ->
                        Text(message, color = SellerOrderGreen, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private data class SellerRejectionReason(val code: String, val label: String, val requiresExplanation: Boolean = false)

private val sellerRejectionReasons = listOf(
    SellerRejectionReason("out_of_stock", "Item is out of stock"),
    SellerRejectionReason("product_unavailable", "Item is unavailable"),
    SellerRejectionReason("unable_to_fulfill", "Unable to fulfill"),
    SellerRejectionReason("store_closed", "Store is temporarily closed"),
    SellerRejectionReason("invalid_delivery_area", "Delivery area is not supported"),
    SellerRejectionReason("buyer_request", "Buyer requested rejection"),
    SellerRejectionReason("duplicate_order", "Duplicate order"),
    SellerRejectionReason("pricing_or_listing_error", "Pricing or listing issue"),
    SellerRejectionReason("other", "Other", requiresExplanation = true),
)

@Composable
private fun SellerRejectOrderDialog(
    orderReference: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onReject: (String, String) -> Unit,
) {
    var selected by remember { mutableStateOf<SellerRejectionReason?>(null) }
    var explanation by remember { mutableStateOf("") }
    val explanationRequired = selected?.requiresExplanation == true
    val canSubmit = selected != null && (!explanationRequired || explanation.trim().length >= 10) && !busy
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { selected?.let { onReject(it.code, explanation.ifBlank { it.label }) } },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = SellerOrderRed),
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (busy) "Rejecting..." else "Reject Order") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
        title = { Text("Reject order $orderReference", color = SellerOrderText, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose a reason. This cannot be undone from Seller Center.", color = SellerOrderMuted, lineHeight = 19.sp)
                sellerRejectionReasons.forEach { reason ->
                    Surface(
                        onClick = { selected = reason },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected?.code == reason.code) SellerOrderRedSoft else SellerOrderSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected?.code == reason.code) SellerOrderRed.copy(alpha = .35f) else SellerOrderLine),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected?.code == reason.code, onClick = { selected = reason })
                            Text(reason.label, color = SellerOrderText, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it.take(280) },
                    label = { Text(if (explanationRequired) "Buyer-visible explanation required" else "Buyer-visible explanation optional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = shopOutlinedTextFieldColors(),
                )
            }
        },
        containerColor = SellerOrderSurface,
    )
}

@Composable
private fun SellerActionButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SellerOrderGreen),
    ) {
        Text(label, fontSize = 12.sp)
    }
}
