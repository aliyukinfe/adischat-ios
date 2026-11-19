package com.adischat.app.feature.shop.presentation

import com.adischat.app.ui.home.AdaptiveFlowAround
import com.adischat.app.ui.home.LocalSidebarBounds
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopOrderGroupDto
import com.adischat.app.feature.shop.data.ShopOrderStatusHistoryDto
import com.adischat.app.feature.shop.data.ShopOrderDto
import com.adischat.app.ui.theme.AdisGebeyaColors
import java.time.Instant

internal object BuyerCancellationFlag {
    // Enable only after the server enforces the 30-minute, pre-acceptance contract.
    const val enabled = false
}

internal fun buyerCancellationEligible(order: ShopOrderDto): Boolean {
    val placed = order.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
    val serverNow = order.serverNow?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
    val groups = order.groups.orEmpty()
    return groups.isNotEmpty() && !serverNow.isBefore(placed) && serverNow.isBefore(placed.plusSeconds(30 * 60)) &&
        order.status !in listOf("cancelled", "rejected", "expired", "delivered", "completed") &&
        order.fulfillmentStatus in listOf("awaiting_seller_confirmation", "created") &&
        groups.all {
            it.fulfillmentStatus in listOf("awaiting_seller_confirmation", "created") &&
                it.sellerResponseStatus == "pending" && it.sellerRespondedAt == null
        }
}

internal fun buyerTrackingMessage(order: ShopOrderDto): String = when {
    order.fulfillmentStatus == "expired" || order.groups.orEmpty().any { it.sellerResponseStatus == "expired" } ->
        "The seller response deadline expired. Any refund is pending provider confirmation."
    order.status == "rejected" ->
        "The seller rejected this order. Any payment reversal is confirmed separately."
    order.groups.orEmpty().any { it.sellerResponseStatus == "rejected" } ->
        "The seller rejected an item in this order. Any payment reversal is confirmed separately."
    order.status == "cancelled" -> "This order was cancelled. Any payment reversal is confirmed separately."
    buyerDeliveryConfirmed(order) -> "Delivery was confirmed. The delivery code can no longer be used."
    buyerDeliveryCodeAvailable(order) -> "Your order is on its way. Show the delivery code only when it arrives."
    order.fulfillmentStatus in listOf("accepted", "processing", "packed", "ready_for_pickup") -> "The seller accepted your order and is preparing it."
    else -> "Waiting for the seller to confirm your order."
}

internal fun marketplaceStatusLabel(value: String?): String = value
    .orEmpty()
    .trim()
    .replace('_', ' ')
    .split(' ')
    .filter(String::isNotBlank)
    .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
    .ifBlank { "Order update" }

@Composable
internal fun SellerOrderListCard(
    group: ShopOrderGroupDto,
    onOpen: () -> Unit,
    onDeadlineReached: () -> Unit,
) {
    val order = group.order
    val item = group.items.orEmpty().firstOrNull()
    val itemCount = group.items.orEmpty().sumOf { it.quantity }
    val status = group.fulfillmentStatus.orEmpty().ifBlank { group.status }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(AdisGebeyaColors.Surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AdisGebeyaColors.Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(AdisGebeyaColors.SecondarySurface),
                    contentAlignment = Alignment.Center,
                ) {
                    ShopAssetImage(item?.imageSnapshot, item?.titleSnapshot ?: "Order", Modifier.fillMaxSize(), iconSize = 24.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(order?.orderNumber ?: "Seller order", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        group.customer?.displayName ?: "Customer",
                        color = AdisGebeyaColors.SecondaryText,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item?.variantDisplayLabel()?.let {
                        Text(it, color = AdisGebeyaColors.SecondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("$itemCount item${if (itemCount == 1) "" else "s"} \u2022 ${formatMarketplaceDateTime(order?.createdAt)}", color = AdisGebeyaColors.SecondaryText, fontSize = 11.sp)
                }
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Open seller order", tint = AdisGebeyaColors.SecondaryText, modifier = Modifier.size(18.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = sellerStatusBackground(status), shape = RoundedCornerShape(999.dp)) {
                    Text(marketplaceStatusLabel(status), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = sellerListStatusColor(status), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(formatEtb(order?.totalMinor ?: group.items.orEmpty().sumOf { it.lineTotalMinor }), color = AdisGebeyaColors.Primary, fontWeight = FontWeight.Bold)
            }
            group.payment?.methodCode?.takeIf(String::isNotBlank)?.let { method ->
                Text("Payment: ${marketplaceStatusLabel(method)}", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp)
            }
            if (group.sellerResponseStatus == "pending") {
                SellerResponseDeadlineCard(group, SellerResponseAudience.SELLER, onDeadlineReached)
            }
        }
    }
}

@Composable
internal fun SellerOrderDetailPage(
    group: ShopOrderGroupDto,
    busy: Boolean,
    onBack: () -> Unit,
    onSellerAction: (String, String, String, String?, String?, String?, Int?) -> Unit,
    onDeadlineReached: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AdisGebeyaColors.Background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val sidebar = LocalSidebarBounds.current
            val startPad = if (sidebar != null) 60.dp else 8.dp
            Row(Modifier.fillMaxWidth().padding(start = startPad, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to seller orders") }
                Column {
                    Text("Seller Order Details", color = AdisGebeyaColors.PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(group.order?.orderNumber ?: "Order", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp)
                }
            }
        }
        item {
            AdaptiveFlowAround(besideStart = 60.dp, belowStart = 14.dp) {
                SellerOrderGroupCard(group, busy, onSellerAction, onDeadlineReached)
            }
        }
    }
}

internal fun buyerDeliveryCodeAvailable(order: ShopOrderDto): Boolean =
    order.status !in listOf("delivered", "completed", "cancelled", "rejected", "expired") &&
        order.fulfillmentStatus in listOf("handed_to_carrier", "shipped", "out_for_delivery")

internal fun buyerDeliveryConfirmed(order: ShopOrderDto): Boolean =
    order.status in listOf("delivered", "completed") || order.fulfillmentStatus == "delivered"

@Composable
internal fun BuyerDeliveryCodeSection(
    order: ShopOrderDto,
    deliveryCode: String?,
    busy: Boolean,
    onReveal: (String) -> Unit,
) {
    when {
        buyerDeliveryConfirmed(order) -> {
            val confirmedAt = order.history.orEmpty().lastOrNull {
                it.nextStatus == "delivered" || it.status == "delivered"
            }?.let { it.occurredAt ?: it.createdAt }
            Text(
                "Delivery confirmed" + (confirmedAt?.let { " on ${formatMarketplaceDateTime(it)}" } ?: ""),
                color = AdisGebeyaColors.PrimaryText,
                fontWeight = FontWeight.SemiBold,
            )
        }
        buyerDeliveryCodeAvailable(order) -> {
            Text("Share the code only when you receive the order.", color = AdisGebeyaColors.SecondaryText)
            if (!deliveryCode.isNullOrBlank()) {
                Text("Delivery code: $deliveryCode", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.Bold)
            } else {
                TextButton(onClick = { onReveal(order.id) }, enabled = !busy) {
                    Text(if (busy) "Loading code..." else "Reveal delivery code")
                }
            }
        }
    }
}

@Composable
internal fun ShopTrackingScreen(
    order: ShopOrderDto,
    deliveryCode: String?,
    codeBusy: Boolean,
    onRevealCode: (String) -> Unit,
    onCancel: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmCancellation by remember(order.id) { mutableStateOf(false) }
    if (confirmCancellation) AlertDialog(
        onDismissRequest = { confirmCancellation = false },
        title = { Text("Cancel order?") },
        text = { Text("This is available only before seller acceptance and within 30 minutes of placement.") },
        confirmButton = { TextButton(onClick = {
            confirmCancellation = false
            onCancel(order.id, "")
        }) { Text("Cancel order") } },
        dismissButton = { TextButton(onClick = { confirmCancellation = false }) { Text("Keep order") } },
    )
    val events = order.history.orEmpty()
        .sortedBy { it.occurredAt ?: it.createdAt ?: "" }
        .distinctBy { it.id.ifBlank { "${it.eventCode}-${it.occurredAt ?: it.createdAt}" } }
    val latest = events.lastOrNull()
    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AdisGebeyaColors.Background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            val sidebar = LocalSidebarBounds.current
            val startPad = if (sidebar != null) 60.dp else 8.dp
            Row(Modifier.fillMaxWidth().padding(start = startPad, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to order details") }
                Column {
                    Text("Order Tracking", color = AdisGebeyaColors.PrimaryText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    Text(order.orderNumber ?: "Order", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp)
                }
            }
        }
        item {
            AdaptiveFlowAround(besideStart = 60.dp, belowStart = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(Color(0xFFF1F0FF)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFC7C4F2)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Current status", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp)
                            Text(
                                marketplaceStatusLabel(latest?.nextStatus ?: latest?.status ?: order.fulfillmentStatus ?: order.status),
                                color = Color(0xFF1E1B4B),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            latest?.publicMessage?.takeIf(String::isNotBlank)?.let {
                                Text(it, color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp)
                            }
                            Text(buyerTrackingMessage(order), color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp)
                        }
                    }
                    BuyerDeliveryCodeSection(order, deliveryCode, codeBusy, onRevealCode)
                    if (BuyerCancellationFlag.enabled && buyerCancellationEligible(order)) {
                        TextButton(onClick = { confirmCancellation = true }) { Text("Cancel order") }
                    }
                    order.groups.orEmpty().filter { it.sellerResponseStatus == "pending" }.forEach { group ->
                        SellerResponseDeadlineCard(group, SellerResponseAudience.BUYER, onRefresh)
                    }
                    if (events.isEmpty()) {
                        Card(colors = CardDefaults.cardColors(AdisGebeyaColors.Surface), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, AdisGebeyaColors.Border)) {
                            Text("No tracking events have been recorded yet.", modifier = Modifier.padding(18.dp), color = AdisGebeyaColors.SecondaryText)
                        }
                    } else {
                        Card(colors = CardDefaults.cardColors(AdisGebeyaColors.Surface), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, AdisGebeyaColors.Border)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                events.forEachIndexed { index, event ->
                                    TrackingTimelineRow(
                                        event = event,
                                        current = index == events.lastIndex,
                                        showConnector = index < events.lastIndex,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingTimelineRow(event: ShopOrderStatusHistoryDto, current: Boolean, showConnector: Boolean) {
    val status = event.nextStatus ?: event.status.ifBlank { event.eventCode.orEmpty() }
    val color = if (current) Color(0xFF1E1B4B) else Color(0xFF8B8AA7)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(if (current) 28.dp else 24.dp).clip(CircleShape)
                    .background(if (current) Color(0xFFE9E7FF) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (current) trackingIcon(status) else Icons.Default.Check,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(if (current) 18.dp else 15.dp),
                )
            }
            if (showConnector) Box(Modifier.width(2.dp).height(46.dp).background(Color(0xFFD5D8E2)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = if (showConnector) 16.dp else 0.dp)) {
            Text(
                event.publicMessage?.takeIf(String::isNotBlank) ?: marketplaceStatusLabel(status),
                color = if (current) AdisGebeyaColors.PrimaryText else AdisGebeyaColors.SecondaryText,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
            )
            event.reason?.takeIf(String::isNotBlank)?.let { Text(it, color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp) }
            Text(formatMarketplaceDateTime(event.occurredAt ?: event.createdAt), color = AdisGebeyaColors.SecondaryText, fontSize = 11.sp)
        }
    }
}

private fun trackingIcon(status: String): ImageVector = when {
    "deliver" in status -> Icons.Default.CheckCircle
    "ship" in status || "carrier" in status -> Icons.Default.LocalShipping
    "pack" in status || "process" in status -> Icons.Default.Inventory2
    "accept" in status || "confirm" in status -> Icons.Default.Check
    "placed" in status || "created" in status -> Icons.Default.ReceiptLong
    else -> Icons.Default.Schedule
}

private fun sellerStatusBackground(status: String): Color = when {
    status in listOf("delivered", "accepted") -> Color(0xFFDCFCE7)
    status in listOf("cancelled", "rejected", "expired") -> Color(0xFFFEE2E2)
    status in listOf("awaiting_seller_confirmation", "created") -> Color(0xFFFFF3E0)
    else -> Color(0xFFEDE9FE)
}

private fun sellerListStatusColor(status: String): Color = when {
    status in listOf("delivered", "accepted") -> Color(0xFF15803D)
    status in listOf("cancelled", "rejected", "expired") -> Color(0xFFB91C1C)
    status in listOf("awaiting_seller_confirmation", "created") -> Color(0xFFB45309)
    else -> Color(0xFF3730A3)
}
