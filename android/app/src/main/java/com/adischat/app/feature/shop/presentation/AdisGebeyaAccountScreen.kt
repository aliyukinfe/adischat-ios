package com.adischat.app.feature.shop.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.core.view.WindowCompat
import com.adischat.app.R
import com.adischat.app.feature.shop.data.ShopHomeState
import com.adischat.app.feature.shop.data.ShopProtectedState

private val AccountBackground = Color(0xFFFFFFFF)
private val AccountSurface = Color.White
private val AccountInk = Color(0xFF17171C)
private val AccountMuted = Color(0xFF737780)
private val AccountDivider = Color(0xFFECECF0)
private val AccountIndigo = Color(0xFF211A72)
private val AccountIndigoDark = Color(0xFF171250)
private val AccountLavender = Color(0xFFF1F0FF)
private val AccountPurple = Color(0xFF6654D9)
private val AccountPink = Color(0xFFFFECF1)
private val AccountCoral = Color(0xFFF04467)
private val AccountMint = Color(0xFFE5F8F2)
private val AccountTeal = Color(0xFF18A67C)
private val AccountBlueSoft = Color(0xFFEAF4FF)
private val AccountBlue = Color(0xFF2674D9)
private val AccountOrangeSoft = Color(0xFFFFF1E4)
private val AccountOrange = Color(0xFFF58A1F)

@Immutable
data class AdisChatAccountIdentity(
    val displayName: String = "",
    val photoUrl: String = "",
)

private data class AccountMenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconBackground: Color,
    val iconTint: Color,
    val onClick: () -> Unit,
)

internal fun adisGebeyaFirstName(displayName: String): String =
    displayName.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifBlank { "there" }

internal fun accountMetricValue(loaded: Boolean, count: Int): String = if (loaded) count.toString() else "—"

@Composable
internal fun AdisGebeyaAccountScreen(
    state: ShopHomeState,
    protectedState: ShopProtectedState,
    identity: AdisChatAccountIdentity,
    onOpenAdisChatProfile: () -> Unit,
    onOrders: () -> Unit,
    onSaved: () -> Unit,
    onRecentlyViewed: () -> Unit,
    onAddresses: () -> Unit,
    onReviews: () -> Unit,
    onBuyerProtection: () -> Unit,
    onAdminCenter: () -> Unit,
) {
    val view = LocalView.current
    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    val buyerProtectionEligible = protectedState.orders.any {
        it.postDeliveryActions?.canOpenBuyerProtection == true
    }
    val primaryRows = listOf(
        AccountMenuItem("My Orders", "View and track your orders", Icons.Default.ShoppingBag, AccountLavender, AccountPurple, onOrders),
        AccountMenuItem("Saved Products", "Products you've saved", Icons.Default.FavoriteBorder, AccountPink, AccountCoral, onSaved),
        AccountMenuItem("Recently Viewed", "Products you opened recently", Icons.Outlined.RemoveRedEye, AccountMint, AccountTeal, onRecentlyViewed),
        AccountMenuItem("Delivery Addresses", "Manage your delivery locations", Icons.Default.LocationOn, AccountBlueSoft, AccountBlue, onAddresses),
    )
    val supportRows = listOf(
        AccountMenuItem("Reviews", "Your product and store feedback", Icons.Outlined.StarOutline, AccountOrangeSoft, AccountOrange, onReviews),
        AccountMenuItem("Buyer Protection", "How AdisGebeya protects eligible purchases", Icons.Default.VerifiedUser, AccountMint, AccountTeal, onBuyerProtection),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AccountBackground).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            AdisGebeyaProfileCard(
                identity = identity,
                identityConnected = state.shopIdentityReady,
                orderCount = accountMetricValue(protectedState.ordersLoaded, protectedState.orders.size),
                savedCount = accountMetricValue(protectedState.favoritesLoaded, protectedState.favorites.size),
                reviewCount = "—",
                buyerProtectionEligible = protectedState.ordersLoaded && buyerProtectionEligible,
                onClick = onOpenAdisChatProfile,
            )
        }
        if (protectedState.error != null) {
            item { AccountStatusNotice("Some account details couldn't be refreshed. Your saved data is unchanged.") }
        }
        item { AccountSectionCard("AdisGebeya", primaryRows) }
        item { AccountSectionCard("Support", supportRows) }
        item { AccountSettingsCard(onManage = onOpenAdisChatProfile) }
        if (protectedState.adminSession != null) {
            item {
                AccountSectionCard(
                    title = "Administration",
                    items = listOf(
                        AccountMenuItem(
                            title = "AdisGebeya Admin",
                            subtitle = "Marketplace moderation and operations",
                            icon = Icons.Default.AdminPanelSettings,
                            iconBackground = AccountLavender,
                            iconTint = AccountIndigo,
                            onClick = onAdminCenter,
                        )
                    ),
                )
            }
        }
    }
}

@Composable
internal fun AdisGebeyaProfileCard(
    identity: AdisChatAccountIdentity,
    identityConnected: Boolean,
    orderCount: String,
    savedCount: String,
    reviewCount: String,
    buyerProtectionEligible: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AccountSurface),
        border = BorderStroke(1.dp, AccountDivider.copy(alpha = .8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatar(identity.displayName, identity.photoUrl)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Hello, ${adisGebeyaFirstName(identity.displayName)} \uD83D\uDC4B",
                        color = AccountInk,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Welcome back to AdisGebeya", color = AccountMuted, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(
                            imageVector = if (identityConnected) Icons.Default.VerifiedUser else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (identityConnected) AccountTeal else AccountMuted,
                            modifier = Modifier.size(19.dp),
                        )
                        Text(
                            if (identityConnected) "Connected with AdisChat" else "Connecting with AdisChat...",
                            color = AccountMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF7F6FC)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Open AdisChat profile", tint = AccountIndigo, modifier = Modifier.size(25.dp))
                }
            }
            HorizontalDivider(color = AccountDivider)
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp)) {
                AccountMetric(Icons.Default.ShoppingBag, AccountLavender, AccountPurple, orderCount, "Orders", Modifier.weight(1f))
                MetricDivider()
                AccountMetric(Icons.Default.FavoriteBorder, AccountPink, AccountCoral, savedCount, "Saved", Modifier.weight(1f))
                MetricDivider()
                AccountMetric(Icons.Outlined.StarOutline, AccountOrangeSoft, AccountOrange, reviewCount, "Reviews", Modifier.weight(1f))
                MetricDivider()
                AccountMetric(
                    Icons.Default.Security,
                    AccountBlueSoft,
                    AccountBlue,
                    if (buyerProtectionEligible) "Buyer" else "—",
                    "Protected",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AccountAvatar(displayName: String, photoUrl: String) {
    Box(Modifier.size(74.dp)) {
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(AccountLavender),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                adisGebeyaFirstName(displayName).take(1).uppercase(),
                color = AccountIndigo,
                fontSize = 27.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (photoUrl.isNotBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "AdisChat profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
        }
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(AccountSurface),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.adisgebeya_loader_buyer),
                contentDescription = "AdisGebeya",
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
internal fun AccountMetric(
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(31.dp).clip(RoundedCornerShape(11.dp)).background(iconBackground), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(5.dp))
        Column(Modifier.weight(1f)) {
            Text(value, color = AccountInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, color = AccountMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
private fun MetricDivider() {
    Box(Modifier.width(1.dp).height(38.dp).background(AccountDivider))
}

@Composable
private fun AccountSectionCard(title: String, items: List<AccountMenuItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = AccountInk,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }.padding(start = 2.dp),
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AccountSurface),
            border = BorderStroke(1.dp, AccountDivider.copy(alpha = .75f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(Modifier.padding(vertical = 3.dp)) {
                items.forEachIndexed { index, item ->
                    AccountMenuRow(item)
                    if (index != items.lastIndex) {
                        HorizontalDivider(color = AccountDivider, modifier = Modifier.padding(start = 82.dp, end = 18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountMenuRow(item: AccountMenuItem) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = item.onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountIconContainer(item.icon, item.iconBackground, item.iconTint)
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, color = AccountInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(item.subtitle, color = AccountMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Open ${item.title}", tint = Color(0xFF8991A8), modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun AccountIconContainer(icon: ImageVector, background: Color, tint: Color) {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun AccountSettingsCard(onManage: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AccountSurface),
        border = BorderStroke(1.dp, AccountDivider.copy(alpha = .75f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            AccountSettingsIllustration()
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Account Settings", color = AccountInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Personalize your shopping experience and manage your preferences.",
                    color = AccountMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onManage,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccountIndigoDark),
                contentPadding = PaddingValues(horizontal = 17.dp, vertical = 12.dp),
            ) {
                Text("Manage", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AccountSettingsIllustration() {
    Box(Modifier.size(width = 76.dp, height = 70.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(AccountLavender), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Storefront, contentDescription = null, tint = AccountPurple, modifier = Modifier.size(38.dp))
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(AccountIndigo),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AccountStatusNotice(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AccountOrangeSoft),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AccountOrange.copy(alpha = .2f)),
    ) {
        Text(message, color = AccountInk, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(13.dp))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
