package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adischat.app.feature.shop.data.ShopAdminProductDto
import com.adischat.app.feature.shop.data.ShopAdminProductQuery

private val adminInk = Color(0xFF17153B)
private val adminMuted = Color(0xFF747A94)
private val adminBorder = Color(0xFFECEEF5)
private val adminIndigo = Color(0xFF38269C)

@Composable
internal fun AdminAllProductsControls(
    query: ShopAdminProductQuery,
    loading: Boolean,
    onQueryChange: (ShopAdminProductQuery) -> Unit,
    onRetry: () -> Unit,
) {
    var searchText by remember(query.search) { mutableStateOf(query.search) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("All Products", color = adminInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onRetry, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh products", tint = adminIndigo)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it.take(120) },
                label = { Text("Product or store") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onQueryChange(query.copy(search = searchText.trim(), offset = 0)) }, enabled = !loading) {
                Icon(Icons.Default.Search, contentDescription = "Search products", tint = adminIndigo)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "all" to "All",
                "pending" to "Awaiting review",
                "approved" to "Approved",
                "rejected" to "Rejected",
                "changes_requested" to "Changes requested",
                "archived" to "Archived",
            ).forEach { (value, label) ->
                val selected = query.status == value
                Surface(
                    color = if (selected) adminIndigo else Color.White,
                    contentColor = if (selected) Color.White else adminInk,
                    shape = RoundedCornerShape(8.dp),
                    border = if (selected) null else BorderStroke(1.dp, adminBorder),
                    modifier = Modifier.height(48.dp).clickable { onQueryChange(query.copy(status = value, offset = 0)) },
                ) {
                    Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
        if (loading) Text("Loading products...", color = adminMuted, fontSize = 12.sp)
    }
}

@Composable
internal fun AdminAllProductRow(
    product: ShopAdminProductDto,
    formatPrice: (Int) -> String,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, adminBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(68.dp).background(Color(0xFFF4F4FA), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Image, contentDescription = null, tint = adminMuted, modifier = Modifier.size(24.dp))
                product.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    AsyncImage(model = url, contentDescription = product.title, contentScale = ContentScale.Crop, modifier = Modifier.size(68.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(product.title.orEmpty().ifBlank { "Untitled product" }, color = adminInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(product.storeName.orEmpty().ifBlank { "Store unavailable" }, color = adminMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatPrice(product.priceMinor), color = adminIndigo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${product.status.orEmpty().ifBlank { "Unknown" }} / ${product.moderationStatus.orEmpty().ifBlank { "Unknown" }}".replace('_', ' '), color = adminMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = adminMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun AdminAllProductsMessage(message: String) {
    Text(message, color = adminMuted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
}

@Composable
internal fun AdminAllProductsPagination(
    offset: Int,
    pageSize: Int,
    hasNext: Boolean,
    loading: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(onClick = onPrevious, enabled = !loading && offset > 0) { Text("Previous") }
        Text("Page ${offset / pageSize + 1}", color = adminMuted, fontSize = 12.sp)
        OutlinedButton(onClick = onNext, enabled = !loading && hasNext) { Text("Next") }
    }
}
