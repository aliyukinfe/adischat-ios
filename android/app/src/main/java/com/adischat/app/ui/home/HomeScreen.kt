package com.adischat.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.adischat.app.ui.UserViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private val HomeBgTop: Color
    @Composable get() = if (isHomeLight()) Color(0xFFF8FFF9) else Color(0xFF050706)
private val HomeBgBottom: Color
    @Composable get() = if (isHomeLight()) Color(0xFFF3F5F7) else Color(0xFF08100C)
private val HomeText: Color
    @Composable get() = if (isHomeLight()) Color(0xFF111827) else Color(0xFFF5F7FA)
private val HomeMuted: Color
    @Composable get() = if (isHomeLight()) Color(0xFF6B7280) else Color(0xFFAEB7B2)
private val HomeCard: Color
    @Composable get() = if (isHomeLight()) Color.White.copy(alpha = 0.92f) else Color(0xFF151B22).copy(alpha = 0.94f)
private val HomeGreen: Color
    @Composable get() = if (isHomeLight()) Color(0xFF22C55E) else Color(0xFF7CFF8A)
private val HomeGreenDark: Color
    @Composable get() = if (isHomeLight()) Color(0xFF22C55E) else Color(0xFF7CFF8A)
private val HomeIconGlass: Color
    @Composable get() = if (isHomeLight()) Color.White.copy(alpha = 0.88f) else Color(0xFF19231D).copy(alpha = 0.96f)
private val HomePromoBrush: Brush
    @Composable get() = Brush.linearGradient(
        if (isHomeLight()) listOf(Color.White, Color(0xFFE9FFF0), Color(0xFFD8FFE5))
        else listOf(Color(0xFF151B22), Color(0xFF102319), Color(0xFF0A120E))
    )
private val HomePromoButtonBg: Color
    @Composable get() = if (isHomeLight()) Color.White else HomeGreen
private val HomePromoButtonText: Color
    @Composable get() = if (isHomeLight()) HomeGreenDark else Color(0xFF061008)
private val HomeBlue = Color(0xFF2684FF)
private val HomeOrange = Color(0xFFFF8A1F)
private val HomePurple = Color(0xFF8B5CF6)
private val HomeRed = Color(0xFFFF3B30)

@Composable
private fun isHomeLight(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

private data class ServiceItem(val label: String, val subtitle: String, val icon: ImageVector, val tint: Color)
private data class RestaurantItem(val name: String, val imageUrl: String, val rating: String, val eta: String, val price: String)
private data class FeatureItem(val label: String, val icon: ImageVector, val tint: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenUrl: (String) -> Unit,
    onGoScan: () -> Unit,
    onGoChat: () -> Unit,
    onGoWallet: () -> Unit,
) {
    val vm: UserViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val appUser by vm.user.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val snackbarHost = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val firstName = appUser?.name?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "akab"
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

    val services = listOf(
        ServiceItem("Delivery", "Fast food", Icons.Default.DeliveryDining, HomeGreenDark),
        ServiceItem("Pickup", "Ready now", Icons.Default.ShoppingBag, HomeBlue),
        ServiceItem("Offers", "Save more", Icons.Default.LocalOffer, HomeOrange),
        ServiceItem("Scan QR", "Pay & open", Icons.Default.QrCodeScanner, HomeGreenDark),
        ServiceItem("Wallet", "Balance", Icons.Default.AccountBalanceWallet, HomeGreenDark),
        ServiceItem("Pay", "Merchant", Icons.Default.CreditCard, HomePurple),
        ServiceItem("Chat", "Messages", Icons.Default.Chat, HomeBlue),
        ServiceItem("Shops", "Nearby", Icons.Default.Storefront, HomeOrange),
        ServiceItem("Favorites", "Saved", Icons.Default.Favorite, HomeRed),
        ServiceItem("More", "All tools", Icons.Default.MoreHoriz, HomeMuted),
    )
    val restaurants = remember {
        listOf(
            RestaurantItem("Habesha Grill", "https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=800&q=80", "4.9", "18-25 min", "$$"),
            RestaurantItem("Green Bowl", "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=800&q=80", "4.8", "15-20 min", "$"),
            RestaurantItem("Urban Burger", "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=800&q=80", "4.7", "20-30 min", "$$"),
            RestaurantItem("Sushi Garden", "https://images.unsplash.com/photo-1579871494447-9811cf80d66c?auto=format&fit=crop&w=800&q=80", "4.9", "25-35 min", "$$$"),
        )
    }
    val categories = listOf("Pizza", "Burgers", "Sushi", "Chicken", "Desserts", "More")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(HomeBgTop, HomeBgBottom)))
                .padding(padding)
        ) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    vm.loadUser()
                    scope.launch {
                        delay(650)
                        refreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 18.dp)
                        .statusBarsPadding()
                ) {
                    HomeHeader(greeting = "$greeting, $firstName! 👋")
                    Spacer(Modifier.height(14.dp))
                    SearchBar(query = query, onQueryChange = { query = it }, onClear = { query = "" })
                    Spacer(Modifier.height(22.dp))
                    if (query.isNotBlank()) {
                        SearchResults(query, restaurants, services, onGoScan, onGoChat, onGoWallet)
                        Spacer(Modifier.height(22.dp))
                    } else {
                        ServicesSection(services, onGoScan, onGoChat, onGoWallet)
                        Spacer(Modifier.height(22.dp))
                        PromoBanner()
                        Spacer(Modifier.height(18.dp))
                        QuickFeaturesBar()
                        Spacer(Modifier.height(22.dp))
                        OrderAgainSection(restaurants)
                        Spacer(Modifier.height(22.dp))
                        CategoryChips(categories)
                        Spacer(Modifier.height(20.dp))
                        ExtraFeaturesBar()
                        Spacer(Modifier.height(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(greeting: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Text(greeting, color = HomeMuted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "What would you like today?",
            color = HomeText,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 36.sp,
        )
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(0.08f), spotColor = HomeGreen.copy(0.16f)),
        color = HomeCard,
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = HomeMuted, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Search restaurants, cuisines, dishes...", color = HomeMuted, fontSize = 17.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = HomeText, fontSize = 17.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(HomeGreenDark),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(HomeGreen.copy(alpha = 0.14f))
                    .clickable(enabled = query.isNotEmpty(), onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (query.isEmpty()) Icons.Default.FilterList else Icons.Default.Close,
                    contentDescription = if (query.isEmpty()) "Filter" else "Clear",
                    tint = HomeGreenDark,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    restaurants: List<RestaurantItem>,
    services: List<ServiceItem>,
    onGoScan: () -> Unit,
    onGoChat: () -> Unit,
    onGoWallet: () -> Unit,
) {
    val q = query.trim().lowercase()
    val matchedServices = services.filter {
        it.label.lowercase().contains(q) || it.subtitle.lowercase().contains(q)
    }
    val matchedRestaurants = restaurants.filter { it.name.lowercase().contains(q) }

    if (matchedServices.isEmpty() && matchedRestaurants.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(top = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.SearchOff, contentDescription = null, tint = HomeMuted, modifier = Modifier.size(46.dp))
            Spacer(Modifier.height(12.dp))
            Text("No results for \"$query\"", color = HomeText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Try a different name or cuisine.", color = HomeMuted, fontSize = 14.sp)
        }
        return
    }

    if (matchedServices.isNotEmpty()) {
        SectionTitle("Services", "")
        Spacer(Modifier.height(12.dp))
        matchedServices.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { service ->
                    ServiceCard(
                        service = service,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (service.label) {
                                "Scan QR" -> onGoScan()
                                "Chat" -> onGoChat()
                                "Wallet", "Pay" -> onGoWallet()
                            }
                        },
                    )
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
    }

    if (matchedRestaurants.isNotEmpty()) {
        SectionTitle("Restaurants", "")
        Spacer(Modifier.height(12.dp))
        matchedRestaurants.forEach { item ->
            SearchRestaurantRow(item)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SearchRestaurantRow(item: RestaurantItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = HomeCard,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(62.dp).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = HomeText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = HomeOrange, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${item.rating}  •  ${item.eta}  •  ${item.price}", color = HomeMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ServicesSection(
    services: List<ServiceItem>,
    onGoScan: () -> Unit,
    onGoChat: () -> Unit,
    onGoWallet: () -> Unit,
) {
    SectionTitle("Services", "See all")
    Spacer(Modifier.height(12.dp))
    services.chunked(5).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { service ->
                ServiceCard(
                    service = service,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when (service.label) {
                            "Scan QR" -> onGoScan()
                            "Chat" -> onGoChat()
                            "Wallet" -> onGoWallet()
                            "Pay" -> onGoWallet()
                        }
                    }
                )
            }
            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ServiceCard(service: ServiceItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(104.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(0.06f), spotColor = service.tint.copy(0.14f))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = HomeCard,
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            if (isHomeLight()) listOf(service.tint.copy(alpha = 0.22f), Color.White)
                            else listOf(service.tint.copy(alpha = 0.22f), Color(0xFF252B31))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(service.icon, contentDescription = service.label, tint = service.tint, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.height(7.dp))
            Text(service.label, color = HomeText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(service.subtitle, color = HomeMuted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PromoBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(174.dp)
            .shadow(22.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black.copy(0.07f), spotColor = HomeGreen.copy(0.22f)),
        color = Color.Transparent,
        shape = RoundedCornerShape(30.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(HomePromoBrush)
        ) {
            Column(Modifier.padding(20.dp).fillMaxWidth(0.58f)) {
                Text("20% OFF Today", color = HomeText, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text("Modern restaurant ordering", color = HomeMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Use code: SAVE20", color = HomeGreenDark, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = HomePromoButtonBg, contentColor = HomePromoButtonText),
                    shape = RoundedCornerShape(999.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    Text("Order Now", fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
            AsyncImage(
                model = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=700&q=80",
                contentDescription = "Food promo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 18.dp)
                    .size(172.dp)
                    .clip(CircleShape),
            )
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.width(18.dp).height(5.dp).clip(CircleShape).background(HomeGreenDark))
                Box(Modifier.size(5.dp).clip(CircleShape).background(HomeGreenDark.copy(alpha = 0.28f)))
                Box(Modifier.size(5.dp).clip(CircleShape).background(HomeGreenDark.copy(alpha = 0.28f)))
            }
        }
    }
}

@Composable
private fun QuickFeaturesBar() {
    val items = listOf(
        FeatureItem("Fast Delivery", Icons.Default.DeliveryDining, HomeGreenDark),
        FeatureItem("Top Rated", Icons.Default.Star, HomeOrange),
        FeatureItem("Free Delivery", Icons.Default.ShoppingBag, HomeBlue),
        FeatureItem("Safe & Secure", Icons.Default.VerifiedUser, HomePurple),
    )
    GlassFeatureRow(items)
}

@Composable
private fun OrderAgainSection(restaurants: List<RestaurantItem>) {
    SectionTitle("Order Again", "See all")
    Spacer(Modifier.height(12.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 18.dp)) {
        items(restaurants) { item ->
            RestaurantCard(item)
        }
    }
}

@Composable
private fun RestaurantCard(item: RestaurantItem) {
    Surface(
        modifier = Modifier
            .width(214.dp)
            .shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(0.07f), spotColor = Color.Black.copy(0.06f)),
        shape = RoundedCornerShape(28.dp),
        color = HomeCard,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(126.dp)) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = HomeRed, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(item.name, color = HomeText, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = HomeOrange, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(item.rating, color = HomeText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("  •  ${item.eta}  •  ${item.price}", color = HomeMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(categories: List<String>) {
    SectionTitle("Explore Categories", "More")
    Spacer(Modifier.height(12.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 18.dp)) {
        items(categories) { category ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = HomeCard,
                shadowElevation = 3.dp,
            ) {
                Text(
                    category,
                    color = HomeText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ExtraFeaturesBar() {
    val items = listOf(
        FeatureItem("Live Tracking", Icons.Default.QrCodeScanner, HomeBlue),
        FeatureItem("Easy Returns", Icons.Default.ShoppingBag, HomeOrange),
        FeatureItem("Exclusive Offers", Icons.Default.LocalOffer, HomePurple),
        FeatureItem("Safe Payments", Icons.Default.CreditCard, HomeGreenDark),
    )
    GlassFeatureRow(items)
}

@Composable
private fun GlassFeatureRow(items: List<FeatureItem>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(0.05f), spotColor = HomeGreen.copy(0.12f)),
        shape = RoundedCornerShape(28.dp),
        color = HomeCard,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(item.icon, contentDescription = null, tint = item.tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(item.label, color = HomeMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = HomeText, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        if (action.isNotBlank()) {
            Text(action, color = HomeGreenDark, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun GlassIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(0.07f), spotColor = HomeGreen.copy(0.14f))
            .clip(CircleShape)
            .background(HomeIconGlass)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = HomeText, modifier = Modifier.size(22.dp))
    }
}
