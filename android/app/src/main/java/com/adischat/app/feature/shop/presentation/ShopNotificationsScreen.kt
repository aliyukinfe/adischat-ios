package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopNotificationDto
import com.adischat.app.ui.theme.AdisGebeyaColors

internal sealed interface ShopNotificationTarget {
    data class BuyerOrder(val orderId: String? = null, val orderGroupId: String? = null) : ShopNotificationTarget
    data class BuyerTracking(val orderId: String) : ShopNotificationTarget
    data class SellerOrder(val orderGroupId: String) : ShopNotificationTarget
    data object SellerRequests : ShopNotificationTarget
    data object SellerProducts : ShopNotificationTarget
    data object SellerMore : ShopNotificationTarget
    data class Conversation(val conversationId: String) : ShopNotificationTarget
    data class BuyerProtection(val requestId: String?) : ShopNotificationTarget
    data class AdminOrder(val orderId: String) : ShopNotificationTarget
    data object Center : ShopNotificationTarget
}

private enum class NotificationFilter(val label: String) {
    All("All"), Orders("Orders"), Messages("Messages"), Promotions("Promotions")
}

private fun ShopNotificationDto.matches(filter: NotificationFilter): Boolean = when (filter) {
    NotificationFilter.All -> true
    NotificationFilter.Messages -> "message" in type.lowercase()
    NotificationFilter.Promotions -> listOf("promotion", "campaign", "deal", "coupon").any { it in type.lowercase() }
    NotificationFilter.Orders -> listOf("order", "seller", "ship", "deliver", "tracking", "refund", "exchange").any { it in type.lowercase() }
}

private fun ShopNotificationDto.dataValue(vararg keys: String): String? = keys
    .firstNotNullOfOrNull { key -> data?.get(key)?.toString()?.trim()?.takeIf(String::isNotEmpty) }

internal fun resolveShopNotificationTarget(
    notification: ShopNotificationDto,
    sellerView: Boolean = false,
): ShopNotificationTarget {
    val orderId = notification.dataValue("orderId", "order_id")
    val orderGroupId = notification.dataValue("orderGroupId", "order_group_id")
    val conversationId = notification.dataValue("conversationId", "conversation_id")
    val requestId = notification.dataValue("requestId", "request_id", "refundRequestId", "exchangeRequestId")
    return when (notification.type.lowercase()) {
        "shop_message", "seller_message", "buyer_message" -> conversationId?.let(ShopNotificationTarget::Conversation)
        "tracking_updated",
        "shipment_updated",
        "order_shipped",
        "order_delivered",
        "delivery_confirmed",
        "handed_to_carrier",
        "seller_processing",
        "seller_packed" -> orderId?.let(ShopNotificationTarget::BuyerTracking)
        "seller_order_received" -> orderGroupId?.let(ShopNotificationTarget::SellerOrder)
        "seller_response_reminder", "seller_response_expired" -> if (sellerView) {
            orderGroupId?.let(ShopNotificationTarget::SellerOrder)
        } else {
            when {
                !orderId.isNullOrBlank() -> ShopNotificationTarget.BuyerOrder(orderId = orderId)
                !orderGroupId.isNullOrBlank() -> ShopNotificationTarget.BuyerOrder(orderGroupId = orderGroupId)
                else -> null
            }
        }
        "refund_request_received",
        "exchange_request_received",
        "refund_request_updated",
        "refund_request_update",
        "refund_seller_response",
        "exchange_request_updated",
        "automatic_refund_initiated",
        "refund_completed",
        "buyer_issue_received",
        "buyer_issue_reported",
        "buyer_issue_status_changed" -> if (sellerView) ShopNotificationTarget.SellerRequests else ShopNotificationTarget.BuyerProtection(requestId)
        "product_submitted",
        "product_moderation_approve",
        "product_moderation_reject",
        "product_moderation_start_review",
        "product_moderation_request_changes" -> if (sellerView) ShopNotificationTarget.SellerProducts else null
        "review_received", "store_review_received", "store_warn" -> if (sellerView) ShopNotificationTarget.SellerMore else null
        "admin_order_updated", "admin_order_issue" -> orderId?.let(ShopNotificationTarget::AdminOrder)
        "seller_confirmation_pending",
        "order_confirmed",
        "order_cancelled",
        "order_expired",
        "order_updated",
        "order_placed",
        "seller_accepted",
        "seller_rejected" -> when {
            !orderId.isNullOrBlank() -> ShopNotificationTarget.BuyerOrder(orderId = orderId)
            !orderGroupId.isNullOrBlank() -> ShopNotificationTarget.BuyerOrder(orderGroupId = orderGroupId)
            else -> null
        }
        else -> null
    } ?: ShopNotificationTarget.Center
}

@Composable
internal fun ShopNotificationsScreen(
    notifications: List<ShopNotificationDto>,
    unreadCount: Int,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onOpen: (ShopNotificationTarget) -> Unit,
    sellerView: Boolean = false,
) {
    var filter by remember { mutableStateOf(NotificationFilter.All) }
    val visible = remember(notifications, filter) {
        notifications.filter { it.matches(filter) }
    }
    LaunchedEffect(Unit) { onRefresh() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AdisGebeyaColors.Background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text(if (sellerView) "Seller Notifications" else "Notifications", color = AdisGebeyaColors.PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (unreadCount > 0) "$unreadCount unread" else "You're all caught up",
                        color = AdisGebeyaColors.SecondaryText,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh notifications") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    items(NotificationFilter.entries) { option ->
                        FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label, fontSize = 11.sp) })
                    }
                }
                Button(
                    onClick = onMarkAllRead,
                    enabled = unreadCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = AdisGebeyaColors.Primary),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Mark all read", fontSize = 12.sp) }
            }
        }
        if (visible.isEmpty() && !loading) {
            item {
                Card(
                    colors = CardDefaults.cardColors(AdisGebeyaColors.Surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AdisGebeyaColors.Border),
                ) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = AdisGebeyaColors.SecondaryText, modifier = Modifier.size(34.dp))
                        Text(if (filter == NotificationFilter.All) "No notifications yet" else "No ${filter.label.lowercase()} notifications", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        items(visible, key = { it.id }) { notification ->
            ShopNotificationRow(
                notification = notification,
                onClick = {
                    if (notification.readAt.isNullOrBlank()) onMarkRead(notification.id)
                    onOpen(resolveShopNotificationTarget(notification, sellerView))
                },
            )
        }
    }
}

@Composable
private fun ShopNotificationRow(notification: ShopNotificationDto, onClick: () -> Unit) {
    val unread = notification.readAt.isNullOrBlank()
    val icon = notificationIcon(notification.type)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(if (unread) Color(0xFFF1F0FF) else AdisGebeyaColors.Surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (unread) Color(0xFFDCD9F8) else AdisGebeyaColors.Divider),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(icon.second),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon.first, contentDescription = null, tint = icon.third, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    notification.title.ifBlank { "AdisGebeya update" },
                    color = AdisGebeyaColors.PrimaryText,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                notification.body?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(formatMarketplaceRelativeTime(notification.createdAt), color = AdisGebeyaColors.SecondaryText, fontSize = 11.sp)
            }
            if (unread) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(9.dp).clip(CircleShape).background(AdisGebeyaColors.AccentOrange))
            }
        }
    }
}

private fun notificationIcon(type: String): Triple<ImageVector, Color, Color> = when {
    "message" in type -> Triple(Icons.Default.ChatBubbleOutline, Color(0xFFEDE9FE), Color(0xFF5B21B6))
    "refund" in type || "exchange" in type -> Triple(Icons.Default.Replay, Color(0xFFFFF3E0), Color(0xFFB45309))
    "ship" in type || "deliver" in type || "tracking" in type -> Triple(Icons.Default.LocalShipping, Color(0xFFE0F2FE), Color(0xFF0369A1))
    "confirm" in type || "accept" in type -> Triple(Icons.Default.CheckCircle, Color(0xFFDCFCE7), Color(0xFF15803D))
    else -> Triple(Icons.Default.ReceiptLong, Color(0xFFEDE9FE), AdisGebeyaColors.Primary)
}
