package com.adischat.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.adischat.app.R
import com.adischat.app.ui.UserViewModel
import com.adischat.app.ui.localization.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private val AppBg = Color(0xFF0B0D12)
private val SurfaceDark = Color(0xFF12151C)
private val SurfaceBorder = Color(0xFF1F2530)
private val Mint = Color(0xFF34D399)
private val MintDark = Color(0xFF04342C)
private val DeepGreen = Color(0xFF0E2A1F)
private val DeepGreenBorder = Color(0xFF1D4435)
private val DeepGreenText = Color(0xFF86C7AE)
private val Muted = Color(0xFF6B7280)
private val LabelGrey = Color(0xFF9CA3AF)
private val Blue = Color(0xFF60A5FA)
private val Orange = Color(0xFFFB923C)
private val Purple = Color(0xFFC084FC)
private val Red = Color(0xFFF87171)
private val Amber = Color(0xFFFBBF24)

private data class ServiceItem(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val onClickType: ServiceAction = ServiceAction.None,
    val logoRes: Int? = null,
)

private data class RestaurantItem(
    val name: String,
    val imageUrl: String,
    val rating: String,
    val eta: String,
    val price: String,
    val url: String,
    val logoRes: Int? = null,
)

private enum class ServiceAction { None, Scan, Wallet, Chat, Pay, Discover, Shop, Seller }

private const val AMROGN_CHICKEN_ORDER_URL = "https://www.adischat.com/amrognchicken"
private const val DRAGON_BURGER_URL = "https://www.adischat.com/dragonburger"
private const val MAMA_PIZZA_URL = "https://www.adischat.com/mamapizza"
private const val SKYLINE_CAFE_URL = "https://www.adischat.com/skylinecafe"
private const val SAKURA_SUSHI_URL = "https://www.adischat.com/sakurasushi"
private const val AMROGN_CHICKEN_IMAGE_URL =
    "https://images.unsplash.com/photo-1626645738196-c2a7c87a8f58?auto=format&fit=crop&w=900&q=85"
private const val BURGER_IMAGE_URL =
    "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=900&q=85"
private const val PIZZA_IMAGE_URL =
    "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=900&q=85"
private const val CAFE_IMAGE_URL =
    "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=900&q=85"
private const val SUSHI_IMAGE_URL =
    "https://images.unsplash.com/photo-1579871494447-9811cf80d66c?auto=format&fit=crop&w=900&q=85"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenUrl: (String) -> Unit,
    onOpenShop: () -> Unit,
    onGoScan: () -> Unit,
    onGoChat: () -> Unit,
    onGoWallet: () -> Unit,
    onGoDiscover: () -> Unit = {},
    onOpenSellerAccount: () -> Unit = {},
) {
    val vm: UserViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val appUser by vm.user.collectAsStateWithLifecycle()
    val totalUnread by com.adischat.app.notifications.UnreadCenter.total.collectAsStateWithLifecycle()
    var refreshing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val firstName = appUser?.name?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "akab"
    val displayName = firstName.replaceFirstChar { it.uppercase() }
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

    val services = remember {
        listOfNotNull(
        ServiceItem("Delivery", Icons.Default.DeliveryDining, Mint, ServiceAction.Discover),
        ServiceItem("Pickup", Icons.Default.ShoppingBag, Blue, ServiceAction.Discover),
        ServiceItem("Offers", Icons.Default.LocalOffer, Orange, ServiceAction.Discover),
        ServiceItem("Scan QR", Icons.Default.QrCodeScanner, Purple, ServiceAction.Scan),
        // Wallet / Pay hidden while the wallet feature is paused (FeatureFlags.WALLET_ENABLED).
        if (com.adischat.app.FeatureFlags.WALLET_ENABLED) ServiceItem("Wallet", Icons.Default.AccountBalanceWallet, Mint, ServiceAction.Wallet) else null,
        if (com.adischat.app.FeatureFlags.WALLET_ENABLED) ServiceItem("Pay", Icons.Default.CreditCard, Purple, ServiceAction.Pay) else null,
        ServiceItem("Chat", Icons.Default.Chat, Blue, ServiceAction.Chat),
        ServiceItem("AdisGebeya", Icons.Default.Storefront, Mint, ServiceAction.Shop, R.drawable.adisgebeya_logo),
        ServiceItem("Seller Account", Icons.Default.Storefront, Orange, ServiceAction.Seller),
        ServiceItem("More", Icons.Default.MoreHoriz, LabelGrey, ServiceAction.Discover),
        )
    }

    val restaurants = remember {
        listOf(
        RestaurantItem(
        "Amrogn Chicken",
        AMROGN_CHICKEN_IMAGE_URL,
        "4.9",
        "15 min",
        "ETB 60-150",
        AMROGN_CHICKEN_ORDER_URL,
        R.drawable.logo_amrogn_chicken,
        ),
        RestaurantItem(
        "Dragon Burger",
        BURGER_IMAGE_URL,
        "4.6",
        "20 min",
        "ETB 90-260",
        DRAGON_BURGER_URL,
        R.drawable.logo_injoy_burger,
        ),
        RestaurantItem(
        "Mama Pizza",
        PIZZA_IMAGE_URL,
        "4.5",
        "25 min",
        "ETB 110-350",
        MAMA_PIZZA_URL,
        R.drawable.logo_pizza_hut,
        ),
        RestaurantItem(
        "Skyline Cafe",
        CAFE_IMAGE_URL,
        "4.9",
        "30 min",
        "ETB 80-240",
        SKYLINE_CAFE_URL,
        R.drawable.logo_nonna,
        ),
        RestaurantItem(
        "Sakura Sushi",
        SUSHI_IMAGE_URL,
        "4.7",
        "40 min",
        "ETB 180-480",
        SAKURA_SUSHI_URL,
        R.drawable.logo_chanoly,
        ),
        )
    }
    Scaffold(containerColor = AppBg) { padding ->
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
        modifier = Modifier.fillMaxSize().background(AppBg).padding(padding),
        ) {
        Column(
        modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp)
        .padding(top = 28.dp, bottom = 118.dp),
        ) {
        Header(greeting = "$greeting, $displayName", title = "What would you like today?")
        Spacer(Modifier.height(18.dp))
        SearchBar(query = query, onQueryChange = { query = it })
        Spacer(Modifier.height(16.dp))
        FilterChips(onGoDiscover = onGoDiscover)
        Spacer(Modifier.height(22.dp))
        FeaturedCard(onClick = { onOpenUrl(AMROGN_CHICKEN_ORDER_URL) })
        Spacer(Modifier.height(28.dp))
        AdisGebeyaCard(onClick = onOpenShop)
        Spacer(Modifier.height(28.dp))
        SectionHeader(title = "Services", action = "See all")
        Spacer(Modifier.height(14.dp))
        ServicesGrid(
        services = services,
        chatBadge = totalUnread,
        onAction = { action ->
        when (action) {
        ServiceAction.Scan -> onGoScan()
        ServiceAction.Wallet, ServiceAction.Pay -> onGoWallet()
        ServiceAction.Chat -> onGoChat()
        ServiceAction.Shop -> onOpenShop()
        ServiceAction.Seller -> onOpenSellerAccount()
        ServiceAction.Discover -> onGoDiscover()
        ServiceAction.None -> Unit
        }
        },
        )
        Spacer(Modifier.height(28.dp))
        TopTrendingRestaurantsSection(restaurants = restaurants, onOpenUrl = onOpenUrl)
        }
        }
    }
}

@Composable
private fun AdisGebeyaCard(onClick: () -> Unit) {
    PressScaleBox(
        modifier = Modifier.fillMaxWidth().height(154.dp),
        shape = RoundedCornerShape(26.dp),
        color = DeepGreen,
        borderColor = Mint.copy(alpha = 0.26f),
        glowColor = Mint.copy(alpha = 0.12f),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF052E22), Color(0xFF0E6B46))))) {
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = 20.dp, end = 150.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.adisgebeya_logo),
                        contentDescription = "AdisGebeya",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Text("AdisGebeya", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text("Shop from trusted local sellers", color = Color.White.copy(alpha = .78f), fontSize = 13.sp, lineHeight = 18.sp)
            }
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy((-14).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(Icons.Default.ShoppingBag, Icons.Default.LocalOffer, Icons.Default.Storefront).forEachIndexed { index, icon ->
                    Box(
                        Modifier
                            .size((58 + index * 8).dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.14f + index * 0.04f))
                            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size((25 + index * 2).dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(greeting: String, title: String) {
    Column(Modifier.fillMaxWidth()) {
        Box(
        Modifier.width(34.dp).height(4.dp).clip(RoundedCornerShape(999.dp)).background(Mint),
        )
        Spacer(Modifier.height(14.dp))
        Text(
        text = tr(greeting),
        color = LabelGrey,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
        text = tr(title),
        color = Color.White,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    PressScaleBox(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceDark,
        borderColor = SurfaceBorder,
        glowColor = Color.Transparent,
        onClick = {},
    ) {
        Row(
        Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (query.isBlank()) {
        Text(tr("Search restaurants, cuisines, dishes..."), color = Muted, fontSize = 14.sp, maxLines = 1)
        }
        BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        cursorBrush = SolidColor(Mint),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth(),
        )
        }
        Box(
        Modifier.size(40.dp).clip(CircleShape).background(Mint).clickable { },
        contentAlignment = Alignment.Center,
        ) {
        Icon(Icons.Default.Mic, contentDescription = null, tint = MintDark, modifier = Modifier.size(18.dp))
        }
        }
    }
}

@Composable
private fun FilterChips(onGoDiscover: () -> Unit) {
    var selected by remember { mutableStateOf("All") }
    val chips = listOf("All", "Delivery", "Pickup", "Offers")
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { label ->
        val active = selected == label
        Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (active) Mint else SurfaceDark,
        border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        modifier = Modifier.clickable {
        selected = label
        if (label != "All") onGoDiscover()
        },
        ) {
        Text(
        tr(label),
        color = if (active) MintDark else LabelGrey,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(title), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(tr(action), color = Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ServicesGrid(services: List<ServiceItem>, chatBadge: Int = 0, onAction: (ServiceAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        services.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { service ->
        ServiceTile(
        service = service,
        badge = if (service.onClickType == ServiceAction.Chat) chatBadge else 0,
        modifier = Modifier.weight(1f),
        onClick = { onAction(service.onClickType) },
        )
        }
        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        }
    }
}

@Composable
private fun ServiceTile(service: ServiceItem, badge: Int = 0, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PressScaleBox(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = service.color.copy(alpha = 0.10f),
        borderColor = service.color.copy(alpha = 0.14f),
        glowColor = Color.Transparent,
        onClick = onClick,
        ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (service.logoRes != null) {
            Image(
                painter = painterResource(service.logoRes),
                contentDescription = service.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(34.dp),
            )
        } else {
            Icon(service.icon, contentDescription = service.label, tint = service.color, modifier = Modifier.size(23.dp))
        }
        if (badge > 0) {
        Box(
        modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(8.dp)
        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(Color(0xFFFF3B30))
        .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
        ) {
        Text(
        text = if (badge > 99) "99+" else badge.toString(),
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        )
        }
        }
        }
        }
        Spacer(Modifier.height(6.dp))
        Text(
        tr(service.label),
        color = LabelGrey,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeaturedCard(onClick: () -> Unit) {
    PressScaleBox(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        shape = RoundedCornerShape(24.dp),
        color = DeepGreen,
        borderColor = DeepGreenBorder,
        glowColor = Mint.copy(alpha = 0.10f),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize()) {
        AsyncImage(
        model = AMROGN_CHICKEN_IMAGE_URL,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.align(Alignment.CenterEnd).offset(x = 30.dp).size(140.dp).clip(CircleShape),
        )
        Image(
        painter = painterResource(R.drawable.logo_amrogn_chicken),
        contentDescription = null,
        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(32.dp).clip(CircleShape),
        contentScale = ContentScale.Fit,
        )
        Column(Modifier.padding(20.dp).fillMaxWidth(0.62f).align(Alignment.CenterStart)) {
        Surface(shape = RoundedCornerShape(6.dp), color = Mint) {
        Text(
        tr("FEATURED"),
        color = MintDark,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        }
        Spacer(Modifier.height(10.dp))
        Text(tr("Amrogn Chicken"), color = Color.White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(tr("Crispy chicken, fast bites"), color = DeepGreenText, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = MintDark),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        ) {
        Text(tr("Order Now"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Icon(Icons.Default.Schedule, contentDescription = null, tint = DeepGreenText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(tr("15 min"), color = DeepGreenText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        }
        }
    }
}

@Composable
private fun TopTrendingRestaurantsSection(restaurants: List<RestaurantItem>, onOpenUrl: (String) -> Unit) {
    SectionHeader(title = "Top Trending Restaurants", action = "See all")
    Spacer(Modifier.height(14.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 20.dp)) {
        items(restaurants) { item ->
        TrendingRestaurantCard(item = item, onClick = { if (item.url.isNotBlank()) onOpenUrl(item.url) })
        }
    }
}

@Composable
private fun TrendingRestaurantCard(item: RestaurantItem, onClick: () -> Unit) {
    PressScaleBox(
        modifier = Modifier.width(232.dp).height(196.dp),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceDark,
        borderColor = SurfaceBorder,
        glowColor = Color.Transparent,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(118.dp)) {
        AsyncImage(
        model = item.imageUrl,
        contentDescription = item.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
        )
        Box(
        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.60f)).padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, contentDescription = null, tint = Amber, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(item.rating, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        }
        item.logoRes?.let { logo ->
        Surface(
        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(38.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
        ) {
        Image(painter = painterResource(logo), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(5.dp))
        }
        }
        }
        Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Column(Modifier.weight(1f)) {
        Text(item.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text("${item.eta}  -  ${item.price}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Box(
        Modifier.size(34.dp).clip(CircleShape).background(Mint).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        ) {
        Icon(Icons.Default.ArrowForward, contentDescription = tr("Order Now"), tint = MintDark, modifier = Modifier.size(16.dp))
        }
        }
        }
    }
}

@Composable
private fun PressScaleBox(
    modifier: Modifier,
    shape: RoundedCornerShape,
    color: Color,
    borderColor: Color,
    glowColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed) 0.96f else 1f, animationSpec = tween(140), label = "pressScale")

    Surface(
        modifier = modifier
        .scale(scale)
        .shadow(if (glowColor == Color.Transparent) 0.dp else 14.dp, shape, ambientColor = glowColor, spotColor = glowColor)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = shape,
        color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        content()
    }
}
