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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adischat.app.feature.shop.data.ShopProtectedState
import com.adischat.app.ui.home.AdaptiveFlowAround
import com.adischat.app.ui.theme.AdisGebeyaColors

private data class BuyerAccountAction(
    val label: String,
    val icon: ImageVector,
    val background: Color,
    val tint: Color,
    val badge: Int? = null,
    val onClick: () -> Unit,
)

@Composable
internal fun AdisGebeyaBuyerAccountScreen(
    identity: AdisChatAccountIdentity,
    identityConnected: Boolean,
    state: ShopProtectedState,
    onProfile: () -> Unit,
    onOrders: () -> Unit,
    onSaved: () -> Unit,
    onRecentlyViewed: () -> Unit,
    onAddresses: () -> Unit,
    onReviews: () -> Unit,
    onBuyerProtection: () -> Unit,
    onAdmin: () -> Unit,
) {
    val orders = state.orders
    val orderActions = listOf(
        BuyerAccountAction("Pending", Icons.Default.Schedule, AdisGebeyaColors.PrimaryLight, AdisGebeyaColors.Primary, orders.count { it.fulfillmentStatus in listOf("created", "awaiting_seller_confirmation") }, onOrders),
        BuyerAccountAction("Active", Icons.Default.Inventory2, Color(0xFFFFF4DD), Color(0xFF9A6500), orders.count { it.fulfillmentStatus in listOf("accepted", "processing", "packed") }, onOrders),
        BuyerAccountAction("Shipped", Icons.Default.LocalShipping, Color(0xFFEAF4FF), Color(0xFF2674D9), orders.count { it.fulfillmentStatus in listOf("shipped", "handed_to_carrier", "out_for_delivery") }, onOrders),
        BuyerAccountAction("To review", Icons.Default.RateReview, Color(0xFFFFF1E4), Color(0xFFD97706), orders.count { it.postDeliveryActions?.canReviewProduct == true || it.postDeliveryActions?.canReviewStore == true }, onOrders),
        BuyerAccountAction("Returns", Icons.Default.Replay, Color(0xFFFFECF1), AdisGebeyaColors.Error, orders.count { it.refundRequests.isNotEmpty() || it.exchangeRequests.isNotEmpty() }, onOrders),
    )
    val quickActions = listOf(
        BuyerAccountAction("Saved", Icons.Default.FavoriteBorder, Color(0xFFFFECF1), Color(0xFFF04467), state.favorites.size.takeIf { state.favoritesLoaded }, onSaved),
        BuyerAccountAction("Recently viewed", Icons.Outlined.RemoveRedEye, Color(0xFFE5F8F2), AdisGebeyaColors.Success, null, onRecentlyViewed),
        BuyerAccountAction("Addresses", Icons.Default.LocationOn, Color(0xFFEAF4FF), Color(0xFF2674D9), null, onAddresses),
        BuyerAccountAction("Reviews", Icons.Outlined.StarOutline, Color(0xFFFFF1E4), Color(0xFFD97706), null, onReviews),
        BuyerAccountAction("Buyer protection", Icons.Default.VerifiedUser, Color(0xFFE5F8F2), AdisGebeyaColors.Success, null, onBuyerProtection),
    )

    LazyColumn(
        Modifier.fillMaxSize().background(AdisGebeyaColors.Background),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            AdaptiveFlowAround(besideStart = 60.dp, belowStart = 16.dp) {
                BuyerIdentityHeader(identity, identityConnected, onProfile)
            }
        }
        state.error?.let {
            item {
                AdaptiveFlowAround(besideStart = 60.dp, belowStart = 16.dp) {
                    Text("Some account details could not be refreshed.", color = AdisGebeyaColors.Error, fontSize = 12.sp)
                }
            }
        }
        item {
            AdaptiveFlowAround(besideStart = 60.dp, belowStart = 16.dp) {
                AccountOrderSection(orderActions, state.ordersLoaded, onOrders)
            }
        }
        item {
            AdaptiveFlowAround(besideStart = 60.dp, belowStart = 16.dp) {
                AccountActionGrid(quickActions)
            }
        }
        item {
            AdaptiveFlowAround(besideStart = 60.dp, belowStart = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Account", color = AdisGebeyaColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    AccountListRow("Shopping preferences", Icons.Default.Settings, onProfile)
                    if (state.adminSession != null) AccountListRow("AdisGebeya Admin", Icons.Default.AdminPanelSettings, onAdmin)
                }
            }
        }
    }
}

@Composable
private fun BuyerIdentityHeader(identity: AdisChatAccountIdentity, connected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(AdisGebeyaColors.PrimaryLight), contentAlignment = Alignment.Center) {
            Text(adisGebeyaFirstName(identity.displayName).take(1).uppercase(), color = AdisGebeyaColors.Primary, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            if (identity.photoUrl.isNotBlank()) AsyncImage(identity.photoUrl, "AdisChat profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(adisGebeyaFirstName(identity.displayName), color = AdisGebeyaColors.PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (connected) "View AdisChat profile" else "Connecting with AdisChat...", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp, maxLines = 2)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Open profile", tint = AdisGebeyaColors.SecondaryText)
    }
}

@Composable
private fun AccountOrderSection(actions: List<BuyerAccountAction>, countsLoaded: Boolean, onOrders: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("My Orders", color = AdisGebeyaColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("View all", color = AdisGebeyaColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(onClick = onOrders).padding(8.dp))
        }
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(AdisGebeyaColors.Surface), border = BorderStroke(1.dp, AdisGebeyaColors.Divider)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp)) {
                actions.forEach { action -> AccountShortcutItem(action, countsLoaded, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AccountActionGrid(actions: List<BuyerAccountAction>) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(AdisGebeyaColors.Surface), border = BorderStroke(1.dp, AdisGebeyaColors.Divider)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            actions.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { action -> AccountShortcutItem(action, true, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AccountShortcutItem(action: BuyerAccountAction, showBadge: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.heightIn(min = 88.dp).clickable(onClick = action.onClick).padding(horizontal = 2.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(action.background), contentAlignment = Alignment.Center) {
            Icon(action.icon, contentDescription = action.label, tint = action.tint, modifier = Modifier.size(21.dp))
            if (showBadge && (action.badge ?: 0) > 0) {
                Box(Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(AdisGebeyaColors.Error), contentAlignment = Alignment.Center) {
                    Text(action.badge!!.coerceAtMost(9).toString(), color = Color.White, fontSize = 9.sp)
                }
            }
        }
        Text(action.label, color = AdisGebeyaColors.PrimaryText, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AccountListRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(AdisGebeyaColors.Surface), border = BorderStroke(1.dp, AdisGebeyaColors.Divider)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AdisGebeyaColors.Primary)
            Spacer(Modifier.width(12.dp))
            Text(label, color = AdisGebeyaColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = "Open $label", tint = AdisGebeyaColors.SecondaryText)
        }
    }
}
