package com.adischat.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.data.repository.ChatRepository
import com.adischat.app.data.model.CommunityRoom
import com.adischat.app.data.model.Conversation
import com.adischat.app.feature.restaurants.data.RestaurantsRepository
import com.adischat.app.feature.shop.data.ProductRepository
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopStoreDto
import com.adischat.app.feature.shop.data.ShopSupabaseClient
import com.adischat.app.feature.shop.data.StoreRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope

data class HomeSearchHit(
    val category: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val service: SuperAppService,
    val target: HomeSearchTarget,
)

sealed interface HomeSearchTarget {
    data class Chat(val conversation: Conversation) : HomeSearchTarget
    data class Community(val room: CommunityRoom) : HomeSearchTarget
    data class Product(val product: ShopProductDto) : HomeSearchTarget
    data class Store(val store: ShopStoreDto) : HomeSearchTarget
    data class Restaurant(val restaurantId: String) : HomeSearchTarget
}

internal data class HomeSearchSnapshot(
    val hits: List<HomeSearchHit>,
    val failedCategories: List<String>,
)

internal interface HomeSearchSources {
    suspend fun chats(query: String): List<HomeSearchHit>
    suspend fun groups(query: String): List<HomeSearchHit>
    suspend fun channels(query: String): List<HomeSearchHit>
    suspend fun products(query: String): List<HomeSearchHit>
    suspend fun stores(query: String): List<HomeSearchHit>
    suspend fun restaurants(query: String): List<HomeSearchHit>
}

internal class DefaultHomeSearchSources(
    private val chat: ChatRepository = ChatRepository(),
    private val products: ProductRepository = ProductRepository(),
    private val stores: StoreRepository = StoreRepository(),
    private val restaurants: RestaurantsRepository = RestaurantsRepository(),
) : HomeSearchSources {
    override suspend fun chats(query: String): List<HomeSearchHit> =
        chat.getConversationsOnce()
            .asSequence()
            .filter { it.otherUserName.contains(query, ignoreCase = true) && it.id.isNotBlank() }
            .take(8)
            .map { HomeSearchHit("Chats", it.id, it.otherUserName, "Conversation", SuperAppService.CHAT, HomeSearchTarget.Chat(it)) }
            .toList()

    override suspend fun groups(query: String): List<HomeSearchHit> =
        chat.searchCommunities("group", query)
            .asSequence()
            .filter { it.id.isNotBlank() && it.name.contains(query, ignoreCase = true) }
            .take(8)
            .map { HomeSearchHit("Groups", it.id, it.name, "Group", SuperAppService.CHAT, HomeSearchTarget.Community(it)) }
            .toList()

    override suspend fun channels(query: String): List<HomeSearchHit> =
        chat.searchCommunities("channel", query)
            .asSequence()
            .filter { it.id.isNotBlank() && it.name.contains(query, ignoreCase = true) }
            .take(8)
            .map { HomeSearchHit("Channels", it.id, it.name, "Channel", SuperAppService.CHAT, HomeSearchTarget.Community(it)) }
            .toList()

    override suspend fun products(query: String): List<HomeSearchHit> {
        check(ShopSupabaseClient.isConfigured) { "Shop unavailable" }
        return products.searchCatalogPage(query, null, false, "created_at.desc", 0, 12)
            .getOrThrow()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .map { HomeSearchHit("Products", it.id, it.title, it.brand.orEmpty(), SuperAppService.ADISGEBEYA, HomeSearchTarget.Product(it)) }
    }

    override suspend fun stores(query: String): List<HomeSearchHit> {
        check(ShopSupabaseClient.isConfigured) { "Shop unavailable" }
        return stores.searchApprovedStores(query)
            .asSequence()
            .filter { it.id.isNotBlank() && it.name.contains(query, ignoreCase = true) }
            .take(8)
            .map { HomeSearchHit("Stores", it.id, it.name, it.city.orEmpty(), SuperAppService.ADISGEBEYA, HomeSearchTarget.Store(it)) }
            .toList()
    }

    override suspend fun restaurants(query: String): List<HomeSearchHit> =
        restaurants.listLiveRestaurants().getOrThrow()
            .asSequence()
            .filter { it.id.isNotBlank() && (it.name.contains(query, ignoreCase = true) || it.cuisineCategory.orEmpty().contains(query, ignoreCase = true)) }
            .take(8)
            .map { HomeSearchHit("Restaurants", it.id, it.name, it.cuisineCategory.orEmpty(), SuperAppService.RESTAURANTS, HomeSearchTarget.Restaurant(it.id)) }
            .toList()
}

internal class HomeSearchCoordinator(private val sources: HomeSearchSources = DefaultHomeSearchSources()) {
    suspend fun search(rawQuery: String): HomeSearchSnapshot = supervisorScope {
        val query = rawQuery.trim().take(80)
        if (query.length < 2) return@supervisorScope HomeSearchSnapshot(emptyList(), emptyList())
        val requests = listOf(
            "Chats" to async { checked { sources.chats(query) } },
            "Groups" to async { checked { sources.groups(query) } },
            "Channels" to async { checked { sources.channels(query) } },
            "Products" to async { checked { sources.products(query) } },
            "Stores" to async { checked { sources.stores(query) } },
            "Restaurants" to async { checked { sources.restaurants(query) } },
        )
        val results = requests.map { (category, task) -> category to task.await() }
        HomeSearchSnapshot(
            hits = results.flatMap { it.second.getOrNull().orEmpty() },
            failedCategories = results.filter { it.second.isFailure }.map { it.first },
        )
    }

    private suspend fun <T> checked(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}

@Composable
internal fun HomeUniversalSearchResults(
    userUid: String?,
    query: String,
    sidebarBounds: Rect?,
    onOpenResult: (HomeSearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coordinator = remember(userUid) { HomeSearchCoordinator() }
    var snapshot by remember(userUid) { mutableStateOf<HomeSearchSnapshot?>(null) }
    var loading by remember(userUid) { mutableStateOf(false) }
    val cleanQuery = query.trim()
    LaunchedEffect(userUid, cleanQuery) {
        snapshot = null
        loading = false
        if (userUid.isNullOrBlank() || cleanQuery.length < 2) return@LaunchedEffect
        delay(450)
        loading = true
        snapshot = coordinator.search(cleanQuery)
        loading = false
    }

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val ink = if (isLight) Color(0xFF0F172A) else Color.White
    val muted = if (isLight) Color(0xFF475569) else Color(0xFFCBD5E1)
    val hits = snapshot?.hits.orEmpty()
    val categories = listOf("Chats", "Groups", "Channels", "Products", "Stores", "Restaurants")
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "search-state") {
            FlowAroundRow(sidebarBounds, 54.dp) {
                val label = when {
                    userUid.isNullOrBlank() -> "Sign in to search"
                    cleanQuery.length < 2 -> "Enter at least two characters"
                    loading -> "Searching..."
                    snapshot == null -> "Searching..."
                    hits.isEmpty() && snapshot?.failedCategories?.size == categories.size -> "Search unavailable. Try again."
                    hits.isEmpty() -> "No matches"
                    else -> "${hits.size} results"
                }
                Text(label, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (snapshot?.failedCategories?.isNotEmpty() == true && hits.isNotEmpty()) {
            item(key = "partial-warning") {
                FlowAroundRow(sidebarBounds, 42.dp) {
                    Text("Some categories could not load", color = Color(0xFFF59E0B), fontSize = 12.sp)
                }
            }
        }
        categories.forEach { category ->
            val categoryHits = hits.filter { it.category == category }
            if (categoryHits.isNotEmpty()) {
                item(key = "header-$category") {
                    FlowAroundRow(sidebarBounds, 32.dp) {
                        Text(category, color = muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                items(categoryHits, key = { "${it.category}-${it.id}" }) { hit ->
                    FlowAroundRow(sidebarBounds, 68.dp) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(64.dp).clickable { onOpenResult(hit) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isLight) Color.White else Color(0xFF171C24),
                            border = BorderStroke(1.dp, if (isLight) Color(0xFFE2E8F0) else Color(0xFF334155)),
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Text(hit.title, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(hit.subtitle.ifBlank { category }, color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
