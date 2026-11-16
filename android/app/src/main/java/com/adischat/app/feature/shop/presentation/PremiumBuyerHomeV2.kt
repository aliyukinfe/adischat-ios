package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adischat.app.feature.shop.data.ShopBannerDto
import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopProductImageDto
import com.adischat.app.ui.theme.AdisGebeyaHomeV2Colors as HomeColors
import java.time.Instant
import java.time.OffsetDateTime

internal fun eligibleHomeBanners(banners: List<ShopBannerDto>, now: Instant = Instant.now()): List<ShopBannerDto> =
    banners.filter { banner ->
        if (banner.isActive != true || banner.isSeed == true || banner.title.isBlank() || banner.imageUrl.isNullOrBlank()) return@filter false
        val start = banner.startAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        val end = banner.endAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        (banner.startAt == null || start != null && !now.isBefore(start)) &&
            (banner.endAt == null || end != null && now.isBefore(end))
    }

private fun ShopProductDto.primaryImageUrl(): String? = images.orEmpty()
    .sortedWith(compareByDescending<ShopProductImageDto> { it.isPrimary == true }.thenBy { it.displayOrder ?: 0 })
    .firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl

internal fun homeCategoryDisplayLabel(category: ShopCategoryDto): String = when (category.slug) {
    "electronics" -> "Electronics"
    "fashion" -> "Fashion"
    "home" -> "Home & Living"
    "beauty" -> "Beauty"
    "groceries" -> "Groceries"
    "books" -> "Books & Media"
    "sports" -> "Sports"
    "toys" -> "Toys & Kids"
    "jewelry-watches" -> "Jewelry"
    else -> category.name
}

internal fun homeProductDisplayTitle(product: ShopProductDto): String {
    val title = product.title.trim().split(Regex("\\s+")).joinToString(" ") { word ->
        if (word.all { !it.isLetter() || it.isLowerCase() }) word.replaceFirstChar { it.titlecase() } else word
    }
    val brand = product.brand?.trim().orEmpty()
    return if (brand.any(Char::isUpperCase) && title.startsWith(brand, ignoreCase = true)) brand + title.drop(brand.length) else title
}

private fun homeCategoryIconTint(category: ShopCategoryDto): Color = when (category.slug) {
    "electronics", "books" -> HomeColors.CategoryBlue
    "beauty", "jewelry-watches" -> HomeColors.CategoryPink
    "home", "groceries" -> HomeColors.CategoryAmber
    "sports", "toys" -> HomeColors.CategoryTeal
    else -> HomeColors.Purple
}

private fun homeProductImageScale(product: ShopProductDto): ContentScale {
    val category = product.categorySlug.orEmpty()
    return if (listOf("phone", "electronic", "fashion", "clothing", "shoes", "jewelry").any(category::contains)) {
        ContentScale.Fit
    } else {
        ContentScale.Crop
    }
}

@Composable
internal fun PremiumBuyerHomeHero(
    banners: List<ShopBannerDto>,
    onSearch: () -> Unit,
) {
    val eligible = eligibleHomeBanners(banners)
    if (eligible.isEmpty()) {
        HomeHeroPanel(
            title = "Find what you need on AdisGebeya",
            subtitle = "Explore products from marketplace stores.",
            imageUrl = null,
            onExplore = onSearch,
        )
        return
    }
    val listState = rememberLazyListState()
    Box {
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(eligible, key = { it.id }) { banner ->
                HomeHeroPanel(
                    title = productionBannerTitle(banner.title),
                    subtitle = banner.subtitle?.let(::productionBannerSubtitle),
                    imageUrl = banner.imageUrl,
                    onExplore = if (banner.actionUrl in listOf("search", "adisgebeya://search")) onSearch else null,
                    modifier = Modifier.fillParentMaxWidth(),
                )
            }
        }
        if (eligible.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                eligible.indices.forEach { index ->
                    Box(Modifier.size(if (index == listState.firstVisibleItemIndex) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(HomeColors.Surface.copy(alpha = if (index == listState.firstVisibleItemIndex) 1f else .45f)))
                }
            }
        }
    }
}

@Composable
private fun HomeHeroPanel(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onExplore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    Box(
        modifier.fillMaxWidth().height((148f + 120f * (fontScale - 1f).coerceAtLeast(0f)).dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(HomeColors.PurpleDark, HomeColors.Purple, HomeColors.PurpleBright))),
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.42f),
            )
        }
        Column(
            Modifier.fillMaxHeight().fillMaxWidth(if (imageUrl == null) 1f else .56f)
                .padding(start = 16.dp, end = if (imageUrl == null) 16.dp else 4.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, color = HomeColors.Surface, fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = HomeColors.Surface.copy(alpha = .9f), fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (onExplore != null) {
                Spacer(Modifier.height(9.dp))
                Surface(
                    modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onExplore),
                    color = HomeColors.Gold,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Explore", color = HomeColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(7.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = HomeColors.Text, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun PremiumHomeCategories(
    categories: List<ShopCategoryDto>,
    onCategory: (ShopCategoryDto) -> Unit,
) {
    if (categories.isEmpty()) return
    val categoryWidth = (68f * LocalDensity.current.fontScale.coerceIn(1f, 1.4f)).dp
    LazyRow(contentPadding = PaddingValues(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it.id }) { category ->
                Column(
                    Modifier.width(categoryWidth).clickable { onCategory(category) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(60.dp).clip(CircleShape).background(categoryTone(category)), contentAlignment = Alignment.Center) {
                        if (category.imageUrl.isNullOrBlank()) {
                            Icon(categoryIcon(category), contentDescription = null, tint = homeCategoryIconTint(category), modifier = Modifier.size(29.dp))
                        } else {
                            Icon(categoryIcon(category), contentDescription = null, tint = homeCategoryIconTint(category), modifier = Modifier.size(29.dp))
                            AsyncImage(category.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        homeCategoryDisplayLabel(category),
                        color = HomeColors.Text,
                        fontSize = 10.5.sp,
                        lineHeight = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
    }
}

@Composable
internal fun PremiumHomeDealStrip(product: ShopProductDto?, onProduct: (ShopProductDto) -> Unit) {
    if (product == null || homePreviousPriceMinor(product) == null) return
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onProduct(product) },
        shape = RoundedCornerShape(12.dp),
        color = HomeColors.PurpleLight,
        border = BorderStroke(1.dp, HomeColors.Border),
    ) {
        Row(Modifier.height(66.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(94.dp).fillMaxHeight().background(HomeColors.PurpleDark).padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Featured\nDeals", color = HomeColors.Gold, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Current offer", color = HomeColors.Purple, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(homeProductDisplayTitle(product), color = HomeColors.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("-${verifiedDiscountPercent(product)}%", color = HomeColors.PurpleDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ArrowForward, contentDescription = "Open deal", tint = HomeColors.Purple, modifier = Modifier.padding(horizontal = 10.dp).size(18.dp))
        }
    }
}

@Composable
private fun HomeV2SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = HomeColors.Text, fontSize = if (LocalDensity.current.fontScale > 1.15f) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TextButton(onClick = onSeeAll) {
            Text("See All", color = HomeColors.Purple, fontSize = 12.sp)
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = HomeColors.Purple, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun PremiumHomeProductGrid(
    title: String,
    products: List<ShopProductDto>,
    savedIds: Set<String>,
    onProduct: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
    onSeeAll: () -> Unit,
    showSeeAll: Boolean = true,
    maxItems: Int = 8,
) {
    if (products.isEmpty()) return
    val visible = products.take(maxItems)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showSeeAll) HomeV2SectionHeader(title, onSeeAll)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 600.dp) 3 else 2
            val cardWidth = (maxWidth - 8.dp * (columns - 1)) / columns
            val cardHeight = homeProductCardHeight(cardWidth, LocalDensity.current.fontScale)
            val rows = (visible.size + columns - 1) / columns
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxWidth().height(cardHeight * rows + 8.dp * (rows - 1)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false,
            ) {
                items(visible, key = { it.id }) { product ->
                    HomeV2ProductCard(product, product.id in savedIds, cardHeight, onProduct, onToggleFavorite)
                }
            }
        }
    }
}

internal fun homeProductCardHeight(width: androidx.compose.ui.unit.Dp, fontScale: Float): androidx.compose.ui.unit.Dp =
    width + 144.dp + ((fontScale - 1f).coerceAtLeast(0f) * 104).dp

@Composable
private fun HomeV2PriceBadge(priceMinor: Int) {
    val label = formatEtb(priceMinor)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.widthIn(max = maxWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF121A47), Color(0xFF176CE4))))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Default.Sell, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            Text(
                label,
                color = Color.White,
                fontSize = if (label.length > 14) 13.sp else 15.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun HomeV2ProductCard(
    product: ShopProductDto,
    saved: Boolean,
    height: androidx.compose.ui.unit.Dp,
    onProduct: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(height).clickable { onProduct(product) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.Surface),
        border = BorderStroke(1.dp, HomeColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(HomeColors.ImageBackground), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Image, contentDescription = null, tint = HomeColors.Muted.copy(alpha = .45f), modifier = Modifier.size(38.dp))
                product.primaryImageUrl()?.let { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = product.title,
                        contentScale = homeProductImageScale(product),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (homePreviousPriceMinor(product) != null) {
                    Surface(Modifier.align(Alignment.TopStart).padding(5.dp), color = HomeColors.Purple, shape = RoundedCornerShape(6.dp)) {
                        Text("-${verifiedDiscountPercent(product)}%", color = HomeColors.Surface, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
                IconButton(
                    onClick = { onToggleFavorite(product) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(48.dp).clip(CircleShape).background(HomeColors.Surface),
                ) {
                    Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (saved) "Remove from favorites" else "Add to favorites",
                        tint = if (saved) HomeColors.Purple else HomeColors.Text,
                        modifier = Modifier.size(21.dp))
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if ((product.reviewCount ?: 0) > 0 && product.averageRating != null || (product.soldCount ?: 0) > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if ((product.reviewCount ?: 0) > 0 && product.averageRating != null) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = HomeColors.Gold, modifier = Modifier.size(13.dp))
                            Text("${"%.1f".format(product.averageRating)} (${product.reviewCount})", color = HomeColors.Muted, fontSize = 10.sp, maxLines = 1)
                        }
                        if ((product.soldCount ?: 0) > 0) {
                            Text("  |  ${product.soldCount} sold", color = HomeColors.Muted, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
                Text(homeProductDisplayTitle(product), color = HomeColors.Text, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                product.store?.name?.takeIf(String::isNotBlank)?.let { name ->
                    Text(name, color = HomeColors.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                product.shortDescription?.takeIf(String::isNotBlank)?.let { description ->
                    Text(description, color = HomeColors.Muted, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(1f))
                homePreviousPriceMinor(product)?.let { previous ->
                    Surface(color = Color(0xFFFFEFF0), shape = RoundedCornerShape(5.dp)) {
                        Text(formatEtb(previous), color = Color(0xFFAD4E59), fontSize = 10.sp,
                            textDecoration = TextDecoration.LineThrough, maxLines = 1,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
                HomeV2PriceBadge(product.priceMinor)
                if (com.adischat.app.feature.shop.data.ShopBenefitFlags.buyerBadgesEnabled) {
                    VerifiedBenefitBadges(product.verifiedBenefits, maxBadges = 2)
                }
            }
        }
    }
}
