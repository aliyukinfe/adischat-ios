package com.adischat.app.feature.shop.presentation

import com.adischat.app.ui.home.AdaptiveFlowAround
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

private val StoreBackground = AdisGebeyaColors.Background
private val StoreSurface = AdisGebeyaColors.Surface
private val StoreSoft = AdisGebeyaColors.SecondarySurface
private val StoreText = AdisGebeyaColors.PrimaryText
private val StoreMuted = AdisGebeyaColors.SecondaryText
private val StoreGreen = AdisGebeyaColors.Primary
private val StoreGreenSoft = AdisGebeyaColors.PrimaryLight
private val StoreLine = AdisGebeyaColors.Divider


@Composable
internal fun StoreDetailsScreen(
    store: ShopStoreDto,
    state: ShopHomeState,
    onBack: () -> Unit,
    onCart: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onAddToCart: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
    onChatSeller: (ShopProductDto) -> Unit,
    savedIds: Set<String>,
    busyIds: Set<String>,
) {
    var query by remember(store.slug, store.id) { mutableStateOf("") }
    var selectedCategory by remember(store.slug, store.id) { mutableStateOf("All") }
    val storeProducts = remember(state.products, store) {
        state.products.filter { product ->
            val productStore = product.store
            productStore?.id == store.id && store.id.isNotBlank() ||
                productStore?.slug == store.slug && store.slug.isNotBlank() ||
                productStore?.name == store.name
        }
    }
    val categories = remember(storeProducts) {
        listOf("All") + storeProducts.mapNotNull { it.categorySlug?.replace("-", " ")?.replaceFirstChar { char -> char.uppercase() } }.distinct()
    }
    val filtered = remember(storeProducts, query, selectedCategory) {
        storeProducts.filter { product ->
            val matchesQuery = query.isBlank() || product.title.contains(query, ignoreCase = true) || product.brand.orEmpty().contains(query, ignoreCase = true)
            val matchesCategory = selectedCategory == "All" || product.categorySlug.orEmpty().replace("-", " ").equals(selectedCategory, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(StoreBackground).statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ShopHeader(cartCount = state.cartCount, onBack = onBack, onCart = onCart)
            AdaptiveFlowAround(besideStart = 60.dp, belowStart = 10.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(StoreSurface), border = androidx.compose.foundation.BorderStroke(1.dp, StoreLine)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(StoreGreenSoft), contentAlignment = Alignment.Center) {
                                ShopAssetImage(store.coverUrl, store.name, Modifier.fillMaxSize(), iconSize = 64.dp)
                            }
                            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(StoreSoft), contentAlignment = Alignment.Center) {
                                    ShopAssetImage(store.logoUrl, store.name, Modifier.fillMaxSize(), iconSize = 34.dp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(store.name, color = StoreText, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (store.approvalStatus.equals("approved", ignoreCase = true)) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Default.Verified, contentDescription = "Approved store", tint = StoreGreen, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Text(
                                        listOfNotNull(
                                            store.category?.trim()?.takeIf(String::isNotEmpty),
                                            store.city?.trim()?.takeIf(String::isNotEmpty),
                                        ).joinToString(" • ").ifBlank { "Marketplace" },
                                        color = StoreMuted,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                            if (!store.description.isNullOrBlank()) {
                                Text(store.description, color = StoreMuted, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiniStat("Products", storeProducts.size.toString())
                                MiniStat("Rating", store.averageStoreRating?.let { "%.1f".format(it) } ?: "New")
                                MiniStat("Sales", store.totalSales?.toString() ?: "New")
                            }
                            Text(
                                "Joined ${formatMarketplaceDate(store.createdAt, "date unavailable")}",
                                color = StoreMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search in store") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = shopOutlinedTextFieldColors(),
                        singleLine = true,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { category ->
                            FilterChipLike(category, selectedCategory == category) { selectedCategory = category }
                        }
                    }
                    PremiumSectionHeader("Products", subtitle = "${filtered.size} available")
                    if (filtered.isEmpty()) {
                        EmptyCard("No products match this store filter.")
                    } else {
                        ProductGridSection("Available now", filtered, onProduct, onToggleFavorite, savedIds, maxItems = Int.MAX_VALUE)
                    }
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(StoreSurface), border = androidx.compose.foundation.BorderStroke(1.dp, StoreLine)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionTitle("Policies")
                            Text("Orders use AdisGebeya checkout, protected seller fulfillment and customer delivery confirmation where available.", color = StoreMuted, lineHeight = 20.sp)
                            storeProducts.firstOrNull()?.let {
                                OutlinedButton(onClick = { onChatSeller(it) }, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, StoreLine)) {
                                    Icon(Icons.Default.Message, contentDescription = null, tint = StoreGreen)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Chat with seller", color = StoreGreen, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
