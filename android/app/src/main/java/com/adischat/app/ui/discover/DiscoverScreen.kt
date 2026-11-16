package com.adischat.app.ui.discover

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.R
import com.adischat.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.adischat.app.ui.localization.tr

private data class Place(
    val emoji: String, val name: String, val category: String,
    val rating: Float, val eta: String, val price: String, val isOpen: Boolean,
    val url: String = "", val logoRes: Int? = null
)

private const val AMROGN_CHICKEN_ORDER_URL = "https://www.adischat.com/amrognchicken"
private const val DRAGON_BURGER_URL = "https://www.adischat.com/dragonburger"
private const val MAMA_PIZZA_URL = "https://www.adischat.com/mamapizza"
private const val SKYLINE_CAFE_URL = "https://www.adischat.com/skylinecafe"
private const val SAKURA_SUSHI_URL = "https://www.adischat.com/sakurasushi"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(onOpenUrl: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val places = remember {
        listOf(
        Place(
        "Amrogn",
        "Amrogn Chicken",
        "Crispy chicken and fast bites",
        4.9f,
        "15 min",
        "ETB 60-150",
        true,
        AMROGN_CHICKEN_ORDER_URL,
        R.drawable.logo_amrogn_chicken,
        ),
        Place(
        "Burger",
        "Dragon Burger",
        "Burgers and American fast food",
        4.6f,
        "20 min",
        "ETB 90-260",
        true,
        DRAGON_BURGER_URL,
        R.drawable.logo_injoy_burger,
        ),
        Place(
        "Pizza",
        "Mama Pizza",
        "Pizza and Italian favorites",
        4.5f,
        "25 min",
        "ETB 110-350",
        true,
        MAMA_PIZZA_URL,
        R.drawable.logo_pizza_hut,
        ),
        Place(
        "Cafe",
        "Skyline Cafe",
        "Cafe, breakfast, and coffee",
        4.9f,
        "30 min",
        "ETB 80-240",
        true,
        SKYLINE_CAFE_URL,
        R.drawable.logo_nonna,
        ),
        Place(
        "Sushi",
        "Sakura Sushi",
        "Sushi and Japanese plates",
        4.7f,
        "40 min",
        "ETB 180-480",
        false,
        SAKURA_SUSHI_URL,
        R.drawable.logo_chanoly,
        ),
        )
    }

    val filtered = places.filter {
        query.isEmpty() || it.name.contains(query, ignoreCase = true) ||
        it.category.contains(query, ignoreCase = true)
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
        refreshing = true
        query = ""
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
        .statusBarsPadding()
    ) {
        // Header
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(tr("Discover"), style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground)
        Text("${filtered.size} restaurants near you", fontSize = 13.sp, color = Primary)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
        value      = query,
        onValueChange = { query = it },
        modifier      = Modifier.fillMaxWidth(),
        shape      = RoundedCornerShape(14.dp),
        placeholder   = { Text(tr("Search restaurants or food...")) },
        leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine    = true,
        )
        }

        LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        items(filtered) { place ->
        Card(
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier  = Modifier
        .fillMaxWidth()
        .clickable(enabled = place.url.isNotBlank()) { onOpenUrl(place.url) }
        ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
        Modifier
        .size(70.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
        ) {
        if (place.logoRes != null) {
        Image(
        painter = painterResource(place.logoRes),
        contentDescription = place.name,
        modifier = Modifier
        .fillMaxSize()
        .padding(6.dp),
        contentScale = ContentScale.Fit
        )
        } else {
        Text(place.emoji, fontSize = 32.sp)
        }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(place.name, fontWeight = FontWeight.Bold, fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (place.isOpen) GreenBg else ErrorBg
        ) {
        Text(
        if (place.isOpen) "Open" else "Closed",
        color = if (place.isOpen) Primary else ErrorRed,
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
        }
        }
        Text(place.category, fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, contentDescription = null,
        tint = GoldColor, modifier = Modifier.size(14.dp))
        Text(" ${place.rating}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground)
        Text(" | ${place.eta}", fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(" | ${place.price}", fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        }
        }
        }
        item { Spacer(Modifier.height(8.dp)) }
        }
    }
    }
}

