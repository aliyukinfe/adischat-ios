package com.adischat.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

internal fun overlapsSidebar(rowTop: Float, rowBottom: Float, sidebar: Rect?): Boolean =
    sidebar == null || (rowTop < sidebar.bottom && rowBottom > sidebar.top)

@Composable
private fun FlowAroundRow(
    sidebarBounds: Rect?,
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var besideSidebar by remember { mutableStateOf(true) }
    val startPadding by animateDpAsState(
        targetValue = if (besideSidebar) 114.dp else 12.dp,
        animationSpec = tween(160),
        label = "flowAroundSidebar",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onGloballyPositioned { coordinates ->
                val top = coordinates.positionInRoot().y
                val next = overlapsSidebar(top, top + coordinates.size.height, sidebarBounds)
                if (besideSidebar != next) besideSidebar = next
            }
            .padding(start = startPadding, end = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
fun SuperAppPromotionalFeed(
    userName: String,
    onSelectService: (SuperAppService) -> Unit,
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    promotions: List<SuperAppPromotionItem> = SuperAppDemoPromotions.items,
    sidebarBounds: Rect? = null,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val background = if (isLight) Color(0xFFF8FAFC) else Color(0xFF0B0D12)
    val ink = if (isLight) Color(0xFF0F172A) else Color.White
    val muted = if (isLight) Color(0xFF475569) else Color(0xFFCBD5E1)
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = remember { listOf("All", "Restaurants", "AdisGebeya", "Chat", "Promotions") }
    val visible = remember(selectedCategory, promotions) {
        if (selectedCategory == "All") promotions
        else promotions.filter { it.categoryTag == selectedCategory }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(background),
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "welcome") {
            FlowAroundRow(sidebarBounds, 42.dp) {
                Column {
                    Text("AdisChat", color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Welcome, ${userName.ifBlank { "Akab" }}", color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        items(visible.take(2), key = { "lead-${it.id}" }) { item ->
            FlowAroundRow(sidebarBounds, 148.dp) {
                DemoPromotionCard(item, isLight, onClick = { onSelectService(item.targetService) })
            }
        }
        item(key = "demo-notice") {
            FlowAroundRow(sidebarBounds, 48.dp) {
                Text(
                    "DEMO PREVIEW  |  Not live offers",
                    color = if (isLight) Color(0xFF065F46) else Color(0xFF6EE7B7),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "search") {
            FlowAroundRow(sidebarBounds, 52.dp) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onOpenSearch),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLight) Color.White else Color(0xFF171C24),
                    border = BorderStroke(1.dp, if (isLight) Color(0xFFE2E8F0) else Color(0xFF334155)),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = muted)
                        Text("Search", color = muted, fontSize = 13.sp)
                    }
                }
            }
        }
        item(key = "categories") {
            FlowAroundRow(sidebarBounds, 44.dp) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { category ->
                        val selected = selectedCategory == category
                        Surface(
                            modifier = Modifier.clickable { selectedCategory = category },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) Color(0xFF10B981) else if (isLight) Color.White else Color(0xFF171C24),
                        ) {
                            Text(category, color = if (selected) Color(0xFF052E26) else ink, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                        }
                    }
                }
            }
        }
        item(key = "section-title") {
            FlowAroundRow(sidebarBounds, 40.dp) {
                Column {
                    Text("Discover", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${visible.size} demo concepts", color = muted, fontSize = 11.sp)
                }
            }
        }
        items(visible.drop(2), key = { it.id }) { item ->
            FlowAroundRow(sidebarBounds, 148.dp) {
                DemoPromotionCard(item, isLight, onClick = { onSelectService(item.targetService) })
            }
        }
    }
}

@Composable
private fun DemoPromotionCard(item: SuperAppPromotionItem, isLight: Boolean, onClick: () -> Unit) {
    val ink = if (isLight) Color(0xFF0F172A) else Color.White
    val muted = if (isLight) Color(0xFF475569) else Color(0xFFCBD5E1)
    Surface(
        modifier = Modifier.fillMaxWidth().height(144.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isLight) Color.White else Color(0xFF171C24),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, if (isLight) Color(0xFFE2E8F0) else Color(0xFF334155)),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(70.dp)) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    "DEMO",
                    color = Color(0xFF052E26),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(7.dp).background(Color(0xFF6EE7B7), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, color = muted, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (item.targetService == SuperAppService.ADISGEBEYA || item.targetService == SuperAppService.RESTAURANTS) "Prices in app" else "Demo only",
                        color = muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.ctaLabel, color = item.accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = item.accentColor, modifier = Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}
