package com.adischat.app.feature.shop.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopOrderDto
import com.adischat.app.feature.shop.data.ShopOrderReviewsDto
import com.adischat.app.feature.shop.data.ShopReviewDto
import com.adischat.app.ui.theme.AdisGebeyaColors

private data class BuyerReviewItem(
    val review: ShopReviewDto,
    val orderNumber: String,
    val type: String,
)

@Composable
internal fun BuyerReviewsScreen(
    orders: List<ShopOrderDto>,
    reviews: ShopOrderReviewsDto?,
    loading: Boolean,
    onLoad: () -> Unit,
    onBack: () -> Unit,
    onOrder: (String) -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onLoad() }
    val orderNumbers = orders.associate { it.id to (it.orderNumber ?: "Order") }
    val reviewItems = reviews?.productReviews.orEmpty().map {
        BuyerReviewItem(it, orderNumbers[it.orderId] ?: "Order", "Product review")
    } + reviews?.storeReviews.orEmpty().map {
        BuyerReviewItem(it, orderNumbers[it.orderId] ?: "Order", "Store review")
    }
    val eligibleOrders = orders.filter {
        it.postDeliveryActions?.canReviewProduct == true || it.postDeliveryActions?.canReviewStore == true
    }

    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AdisGebeyaColors.Background),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to account")
                }
                Column {
                    Text("My Reviews", color = AdisGebeyaColors.PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("Feedback from your verified purchases", color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp)
                }
            }
        }

        if (loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = AdisGebeyaColors.Primary, strokeWidth = 3.dp)
                }
            }
        } else {
            if (eligibleOrders.isNotEmpty()) {
                item { Text("Ready to review", color = AdisGebeyaColors.PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                items(eligibleOrders, key = { "eligible-${it.id}" }) { order ->
                    ReviewOrderCard(order, onOrder)
                }
            }
            if (reviewItems.isNotEmpty()) {
                item { Text("Your feedback", color = AdisGebeyaColors.PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                items(reviewItems, key = { "${it.type}-${it.review.id}" }) { item ->
                    SubmittedReviewCard(item)
                }
            }
            if (eligibleOrders.isEmpty() && reviewItems.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(AdisGebeyaColors.Surface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AdisGebeyaColors.Border),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.RateReview,
                                contentDescription = null,
                                tint = AdisGebeyaColors.Primary,
                                modifier = Modifier.size(30.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("No reviews yet", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Eligible completed purchases will appear here.",
                                color = AdisGebeyaColors.SecondaryText,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewOrderCard(order: ShopOrderDto, onOrder: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOrder(order.id) },
        colors = CardDefaults.cardColors(AdisGebeyaColors.Surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AdisGebeyaColors.Border),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.RateReview,
                contentDescription = null,
                tint = AdisGebeyaColors.Primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(order.orderNumber ?: "Order", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold)
                Text("Share feedback from this purchase", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open order", tint = AdisGebeyaColors.SecondaryText)
        }
    }
}

@Composable
private fun SubmittedReviewCard(item: BuyerReviewItem) {
    Card(
        colors = CardDefaults.cardColors(AdisGebeyaColors.Surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AdisGebeyaColors.Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(CircleShape).background(Color(0xFFFFF3D6)).padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = AdisGebeyaColors.Warning, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(item.review.rating.toString(), color = AdisGebeyaColors.PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(item.type, color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp)
            }
            item.review.title?.takeIf(String::isNotBlank)?.let {
                Text(it, color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            item.review.body?.takeIf(String::isNotBlank)?.let {
                Text(it, color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text("${item.orderNumber}  •  ${formatMarketplaceDateTime(item.review.createdAt)}", color = AdisGebeyaColors.Disabled, fontSize = 11.sp)
        }
    }
}
