package com.adischat.app.ui.home

import android.content.Context
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adischat.app.data.model.CommunityRoom
import com.adischat.app.data.repository.ChatRepository
import com.adischat.app.feature.restaurants.data.RestaurantSummaryDto
import com.adischat.app.feature.restaurants.data.RestaurantsRepository
import com.adischat.app.feature.shop.data.ProductRepository
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopStoreDto
import com.adischat.app.feature.shop.data.ShopSupabaseClient
import com.adischat.app.feature.shop.data.StoreRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Administrative promotion definition accommodating manual priority promotions.
 * Matches backend SQL proposal agr7_2_admin_promotions_proposal.sql.
 */
data class AdminPromotedItem(
    val id: String,
    val targetType: String, // "product", "restaurant", "store", "channel"
    val targetId: String,
    val customTitle: String? = null,
    val customSubtitle: String? = null,
    val customBannerUrl: String? = null,
    val priority: Int = 100,
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    val isActive: Boolean = true,
)

/**
 * Real Super App Home feed card models representing live authorized catalog data.
 */
sealed interface RealFeedCard {
    val id: String
    val categoryTag: String
    val targetService: SuperAppService
    val isPromoted: Boolean

    data class ProductCard(
        val product: ShopProductDto,
        val storeName: String? = null,
        val discountPercent: Int? = null,
        override val isPromoted: Boolean = false,
        val customTitle: String? = null,
        val customSubtitle: String? = null,
        val customBannerUrl: String? = null,
    ) : RealFeedCard {
        override val id: String = "product_${product.id}"
        override val categoryTag: String = "AdisGebeya"
        override val targetService: SuperAppService = SuperAppService.ADISGEBEYA
    }

    data class RestaurantCard(
        val restaurant: RestaurantSummaryDto,
        override val isPromoted: Boolean = false,
        val customTitle: String? = null,
        val customSubtitle: String? = null,
        val customBannerUrl: String? = null,
    ) : RealFeedCard {
        override val id: String = "restaurant_${restaurant.id}"
        override val categoryTag: String = "Restaurants"
        override val targetService: SuperAppService = SuperAppService.RESTAURANTS
    }

    data class ChannelCard(
        val room: CommunityRoom,
        override val isPromoted: Boolean = false,
        val snippet: String? = null,
    ) : RealFeedCard {
        override val id: String = "channel_${room.id}"
        override val categoryTag: String = "Community"
        override val targetService: SuperAppService = SuperAppService.CHAT
    }
}

data class RealHomeFeedState(
    val isLoading: Boolean = true,
    val featured: List<RealFeedCard> = emptyList(),
    val items: List<RealFeedCard> = emptyList(),
    val error: String? = null,
)

/**
 * Loader coordinating real authorized data from AdisGebeya, Restaurants, and Community.
 * Strictly queries live authorized stores/products/restaurants; zero hardcoded fictional items.
 */
class RealHomeFeedCoordinator(
    private val productRepo: ProductRepository = ProductRepository(),
    private val storeRepo: StoreRepository = StoreRepository(),
    private val restaurantRepo: RestaurantsRepository = RestaurantsRepository(),
    private val chatRepo: ChatRepository = ChatRepository(),
) {
    suspend fun loadFeed(adminPromotions: List<AdminPromotedItem> = emptyList()): RealHomeFeedState = supervisorScope {
        try {
            val productsTask = async {
                runCatching {
                    if (ShopSupabaseClient.isConfigured) productRepo.approvedProducts()
                    else emptyList<ShopProductDto>()
                }.getOrDefault(emptyList())
            }
            val storesTask = async {
                runCatching {
                    if (ShopSupabaseClient.isConfigured) storeRepo.approvedStores()
                    else emptyList<ShopStoreDto>()
                }.getOrDefault(emptyList())
            }
            val restaurantsTask = async {
                runCatching {
                    restaurantRepo.listLiveRestaurants().getOrDefault(emptyList<RestaurantSummaryDto>())
                }.getOrDefault(emptyList())
            }
            val channelsTask = async {
                runCatching {
                    chatRepo.searchCommunities("channel", "").take(6)
                }.getOrDefault(emptyList<CommunityRoom>())
            }

            val products: List<ShopProductDto> = productsTask.await()
            val stores: List<ShopStoreDto> = storesTask.await()
            val restaurants: List<RestaurantSummaryDto> = restaurantsTask.await()
            val channels: List<CommunityRoom> = channelsTask.await()

            val storeMap: Map<String, ShopStoreDto> = stores.associateBy { it.id }

            val cards = mutableListOf<RealFeedCard>()

            // 1. Map real products with promotional discount calculation
            products.forEach { prod ->
                val discount: Int? = if (prod.compareAtPriceMinor != null && prod.compareAtPriceMinor > prod.priceMinor) {
                    val diff = prod.compareAtPriceMinor - prod.priceMinor
                    ((diff.toDouble() / prod.compareAtPriceMinor) * 100).toInt()
                } else null

                val adminPromo = adminPromotions.firstOrNull { it.targetType == "product" && it.targetId == prod.id && it.isActive }
                val resolvedStoreName: String? = prod.store?.name ?: prod.store?.id?.let { storeMap[it]?.name }

                cards.add(
                    RealFeedCard.ProductCard(
                        product = prod,
                        storeName = resolvedStoreName,
                        discountPercent = discount,
                        isPromoted = adminPromo != null || discount != null,
                        customTitle = adminPromo?.customTitle,
                        customSubtitle = adminPromo?.customSubtitle,
                        customBannerUrl = adminPromo?.customBannerUrl,
                    )
                )
            }

            // 2. Map real live restaurants
            restaurants.forEach { rest ->
                val adminPromo = adminPromotions.firstOrNull { it.targetType == "restaurant" && it.targetId == rest.id && it.isActive }
                cards.add(
                    RealFeedCard.RestaurantCard(
                        restaurant = rest,
                        isPromoted = adminPromo != null,
                        customTitle = adminPromo?.customTitle,
                        customSubtitle = adminPromo?.customSubtitle,
                        customBannerUrl = adminPromo?.customBannerUrl,
                    )
                )
            }

            // 3. Map real community channels
            channels.forEach { room ->
                val adminPromo = adminPromotions.firstOrNull { it.targetType == "channel" && it.targetId == room.id && it.isActive }
                cards.add(
                    RealFeedCard.ChannelCard(
                        room = room,
                        isPromoted = adminPromo != null,
                        snippet = room.description?.takeIf { it.isNotBlank() },
                    )
                )
            }

            // Prioritize promoted items first
            val sortedCards = cards.sortedByDescending { it.isPromoted }

            // Pick 1-2 featured items for the discovery hero section
            val featuredItems = sortedCards.take(2)
            val feedItems = sortedCards.drop(2)

            RealHomeFeedState(
                isLoading = false,
                featured = featuredItems,
                items = feedItems,
                error = if (cards.isEmpty()) "No marketplace items or restaurants available right now." else null,
            )
        } catch (e: Exception) {
            RealHomeFeedState(
                isLoading = false,
                error = "Unable to load feed. Check connection.",
            )
        }
    }
}

/**
 * Real Home Feed Composable rendering real products, restaurants, and channels
 * with full adaptive flow-around sidebar behavior.
 */
@Composable
fun RealHomeFeed(
    onSelectService: (SuperAppService) -> Unit,
    onOpenSearchResult: (HomeSearchHit) -> Unit,
    modifier: Modifier = Modifier,
    sidebarBounds: Rect? = LocalSidebarBounds.current,
    listState: LazyListState = rememberLazyListState(),
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bg = if (isLight) Color(0xFFF8FAFC) else Color(0xFF0B0D12)
    val cardBg = if (isLight) Color.White else Color(0xFF141822)
    val cardBorder = if (isLight) Color(0xFFE2E8F0) else Color(0xFF1F2530)
    val textColor = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val mutedColor = if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8)
    val mintAccent = Color(0xFF34D399)

    val coordinator = remember { RealHomeFeedCoordinator() }
    var feedState by remember { mutableStateOf(RealHomeFeedState()) }
    val scope = rememberCoroutineScope()

    val refreshFeed: () -> Unit = {
        feedState = feedState.copy(isLoading = true, error = null)
        scope.launch {
            feedState = coordinator.loadFeed()
        }
    }

    LaunchedEffect(Unit) {
        feedState = coordinator.loadFeed()
    }

    var selectedCategory by remember { mutableStateOf("All") }
    val categories = remember { listOf("All", "AdisGebeya", "Restaurants", "Community", "Promotions") }

    val visibleCards = remember(selectedCategory, feedState) {
        val all = feedState.featured + feedState.items
        when (selectedCategory) {
            "All" -> all
            "Promotions" -> all.filter { it.isPromoted }
            "AdisGebeya" -> all.filter { it.categoryTag == "AdisGebeya" }
            "Restaurants" -> all.filter { it.categoryTag == "Restaurants" }
            "Community" -> all.filter { it.categoryTag == "Community" }
            else -> all
        }
    }

    if (feedState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = mintAccent,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(36.dp),
            )
        }
        return
    }

    if (visibleCards.isEmpty() && feedState.error != null) {
        Box(
            modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = mutedColor,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = feedState.error ?: "No items available",
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                OutlinedButton(onClick = refreshFeed) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(bg),
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Filter Chips Row (Flows around the sidebar)
        item(key = "filter-chips") {
            AdaptiveFlowAround(
                sidebarBounds = sidebarBounds,
                besideStart = 114.dp,
                belowStart = 12.dp,
            ) { _ ->
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        val chipBg = if (isSelected) mintAccent else cardBg
                        val chipText = if (isSelected) Color(0xFF04342C) else textColor
                        val chipBorder = if (isSelected) mintAccent else cardBorder

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(chipBg)
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = cat,
                                color = chipText,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        // 2. Discovery Section Header
        item(key = "feed-header") {
            AdaptiveFlowAround(
                sidebarBounds = sidebarBounds,
                besideStart = 114.dp,
                belowStart = 12.dp,
            ) { _ ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (selectedCategory == "All") "Featured Discovery" else selectedCategory,
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${visibleCards.size} verified live options",
                        color = mutedColor,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        // 3. Real Cards (Each adapts smoothly beside vs below the sidebar)
        items(visibleCards, key = { it.id }) { card ->
            AdaptiveFlowAround(
                sidebarBounds = sidebarBounds,
                besideStart = 114.dp,
                belowStart = 12.dp,
            ) { isBeside ->
                when (card) {
                    is RealFeedCard.ProductCard -> {
                        ProductFeedCard(
                            card = card,
                            isBeside = isBeside,
                            isLight = isLight,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            onClick = {
                                onOpenSearchResult(
                                    HomeSearchHit(
                                        category = "Products",
                                        id = card.product.id,
                                        title = card.product.title,
                                        subtitle = card.storeName.orEmpty(),
                                        service = SuperAppService.ADISGEBEYA,
                                        target = HomeSearchTarget.Product(card.product),
                                    )
                                )
                            },
                        )
                    }

                    is RealFeedCard.RestaurantCard -> {
                        RestaurantFeedCard(
                            card = card,
                            isBeside = isBeside,
                            isLight = isLight,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            onClick = {
                                onOpenSearchResult(
                                    HomeSearchHit(
                                        category = "Restaurants",
                                        id = card.restaurant.id,
                                        title = card.restaurant.name,
                                        subtitle = card.restaurant.cuisineCategory.orEmpty(),
                                        service = SuperAppService.RESTAURANTS,
                                        target = HomeSearchTarget.Restaurant(card.restaurant.id),
                                    )
                                )
                            },
                        )
                    }

                    is RealFeedCard.ChannelCard -> {
                        ChannelFeedCard(
                            card = card,
                            isBeside = isBeside,
                            isLight = isLight,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            onClick = {
                                onOpenSearchResult(
                                    HomeSearchHit(
                                        category = "Channels",
                                        id = card.room.id,
                                        title = card.room.name,
                                        subtitle = "Channel",
                                        service = SuperAppService.CHAT,
                                        target = HomeSearchTarget.Community(card.room),
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Adaptive Product Card:
 * Beside sidebar: Compact horizontal card with side-by-side photo, title, price.
 * Below sidebar: Wider visual card with large photo, discount badge, store, and clear CTA.
 */
@Composable
private fun ProductFeedCard(
    card: RealFeedCard.ProductCard,
    isBeside: Boolean,
    isLight: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
) {
    val prod = card.product
    val displayTitle = card.customTitle ?: prod.title
    val imageUrl = card.customBannerUrl ?: prod.images?.firstOrNull()
    val priceEtb = "ETB ${prod.priceMinor / 100}"
    val oldPriceEtb = prod.compareAtPriceMinor?.takeIf { it > prod.priceMinor }?.let { "ETB ${it / 100}" }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        if (isBeside) {
            // Compact layout beside sidebar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isLight) Color(0xFFF1F5F9) else Color(0xFF1E2532)),
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = mutedColor,
                            modifier = Modifier.size(28.dp).align(Alignment.Center),
                        )
                    }
                    if (card.discountPercent != null) {
                        Text(
                            text = "-${card.discountPercent}%",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .background(Color(0xFFEF4444), RoundedCornerShape(bottomEnd = 6.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (card.storeName != null) {
                        Text(
                            text = card.storeName,
                            color = Color(0xFF34D399),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = displayTitle,
                        color = textColor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = priceEtb,
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (oldPriceEtb != null) {
                            Text(
                                text = oldPriceEtb,
                                color = mutedColor,
                                fontSize = 10.sp,
                                textDecoration = TextDecoration.LineThrough,
                            )
                        }
                    }
                }
            }
        } else {
            // Full-width visual-first layout below sidebar
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(if (isLight) Color(0xFFF1F5F9) else Color(0xFF1E2532)),
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = mutedColor,
                            modifier = Modifier.size(36.dp).align(Alignment.Center),
                        )
                    }
                    if (card.discountPercent != null) {
                        Text(
                            text = "${card.discountPercent}% OFF",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color(0xFFEF4444), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                    if (card.storeName != null) {
                        Text(
                            text = card.storeName,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle,
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = priceEtb,
                                color = Color(0xFF34D399),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (oldPriceEtb != null) {
                                Text(
                                    text = oldPriceEtb,
                                    color = mutedColor,
                                    fontSize = 11.sp,
                                    textDecoration = TextDecoration.LineThrough,
                                )
                            }
                        }
                    }
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF34D399),
                            contentColor = Color(0xFF04342C),
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text("View Product", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Adaptive Restaurant Card:
 * Beside sidebar: Compact horizontal card.
 * Below sidebar: Wider visual card with cover photo, cuisine, rating, and View Menu button.
 */
@Composable
private fun RestaurantFeedCard(
    card: RealFeedCard.RestaurantCard,
    isBeside: Boolean,
    isLight: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
) {
    val rest = card.restaurant
    val name = card.customTitle ?: rest.name
    val cuisine = card.customSubtitle ?: rest.cuisineCategory ?: "Dining & Drinks"
    val coverUrl = card.customBannerUrl ?: rest.coverPath ?: rest.logoPath

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        if (isBeside) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isLight) Color(0xFFF1F5F9) else Color(0xFF1E2532)),
                ) {
                    if (coverUrl != null) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = Color(0xFFFB923C),
                            modifier = Modifier.size(28.dp).align(Alignment.Center),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "RESTAURANT",
                        color = Color(0xFFFB923C),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = name,
                        color = textColor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = cuisine,
                        color = mutedColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(if (isLight) Color(0xFFF1F5F9) else Color(0xFF1E2532)),
                ) {
                    if (coverUrl != null) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = Color(0xFFFB923C),
                            modifier = Modifier.size(36.dp).align(Alignment.Center),
                        )
                    }
                    Text(
                        text = "DINE-IN & QR",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color(0xFFFB923C), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = cuisine,
                            color = mutedColor,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFB923C),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text("View Menu", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Adaptive Community / Channel Card:
 * Displays real channel room with direct "Open Chat" action.
 */
@Composable
private fun ChannelFeedCard(
    card: RealFeedCard.ChannelCard,
    isBeside: Boolean,
    isLight: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
) {
    val room = card.room

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(if (isBeside) 50.dp else 56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF60A5FA).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = room.name.take(2).uppercase(),
                    color = Color(0xFF2563EB),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "COMMUNITY",
                        color = Color(0xFF60A5FA),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = room.name,
                    color = textColor,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.snippet ?: "Tap to join community discussion",
                    color = mutedColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF60A5FA),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp),
            ) {
                Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
