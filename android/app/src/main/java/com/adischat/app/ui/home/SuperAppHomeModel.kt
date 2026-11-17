package com.adischat.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The approved 5 modern Super App services (Phase H1 Unified Navigation):
 * 1. Home — Unified dashboard and promotional feed
 * 2. Chat — Includes messages, groups, and channels
 * 3. AdisGebeya — Marketplace & delivery
 * 4. Restaurants — Dine-in menus & table QR
 * 5. Promotions — Curated vouchers, deals & highlights
 *
 * Duplicate Channels and Seller Center entries are removed from the sidebar.
 * (Channels live inside Chat; Seller Center lives inside Activity Center -> Business).
 */
enum class SuperAppService(
    val id: String,
    val title: String,
    val subtitle: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val accentColor: Color,
    val badgeLabel: String? = null,
) {
    HOME(
        id = "home",
        title = "Home",
        subtitle = "Dashboard & Feed",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        accentColor = Color(0xFF10B981), // Emerald green
    ),
    CHAT(
        id = "chat",
        title = "Chat",
        subtitle = "Messages, Groups & Channels",
        selectedIcon = Icons.Filled.Chat,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
        accentColor = Color(0xFF60A5FA), // Blue
    ),
    ADISGEBEYA(
        id = "adisgebeya",
        title = "AdisGebeya",
        subtitle = "Marketplace & Delivery",
        selectedIcon = Icons.Filled.ShoppingBag,
        unselectedIcon = Icons.Outlined.ShoppingBag,
        accentColor = Color(0xFF34D399), // Mint green
        badgeLabel = "HOT",
    ),
    RESTAURANTS(
        id = "restaurants",
        title = "Restaurants",
        subtitle = "Dine-in Menus & QR",
        selectedIcon = Icons.Filled.Restaurant,
        unselectedIcon = Icons.Outlined.Restaurant,
        accentColor = Color(0xFFFB923C), // Warm orange
        badgeLabel = "NEW",
    ),
    PROMOTIONS(
        id = "promotions",
        title = "Promotions",
        subtitle = "Vouchers & Deals",
        selectedIcon = Icons.Filled.LocalOffer,
        unselectedIcon = Icons.Outlined.LocalOffer,
        accentColor = Color(0xFFF87171), // Coral red
        badgeLabel = "DEALS",
    );

    companion object {
        /**
         * Services displayed in the expanded sidebar on the Home dashboard:
         * Home, Chat, AdisGebeya, Restaurants, Promotions.
         */
        val dashboardServices: List<SuperAppService> = listOf(HOME, CHAT, ADISGEBEYA, RESTAURANTS, PROMOTIONS)

        /**
         * Services displayed in the collapsed 48-56dp compact rail inside full-screen services:
         * Chat, AdisGebeya, Restaurants, Promotions (with Home as the dedicated top icon).
         */
        val compactRailServices: List<SuperAppService> = listOf(CHAT, ADISGEBEYA, RESTAURANTS, PROMOTIONS)
    }
}

/**
 * Curated promotional feed items for the Super App Home preview.
 * Strictly local QA demo fixtures with zero backend mutations.
 */
data class SuperAppPromotionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val highlightBadge: String,
    val categoryTag: String,
    val imageUrl: String,
    val targetService: SuperAppService,
    val ctaLabel: String,
    val accentColor: Color,
    val originalPriceLabel: String? = null,
    val promoPriceLabel: String? = null,
    val validityNote: String = "Demo concept, not a live offer",
)

object SuperAppDemoPromotions {
    val items: List<SuperAppPromotionItem> = listOf(
        SuperAppPromotionItem(
            id = "demo-dinein",
            title = "Ethiopian dining",
            subtitle = "Explore menus and restaurant details in the app.",
            highlightBadge = "DEMO",
            categoryTag = "Restaurants",
            imageUrl = "https://images.unsplash.com/photo-1541544741938-0af808871cc0?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.RESTAURANTS,
            ctaLabel = "View Menu",
            accentColor = Color(0xFFFB923C),
        ),
        SuperAppPromotionItem(
            id = "demo-audio",
            title = "Audio & accessories",
            subtitle = "Browse products and check current prices in AdisGebeya.",
            highlightBadge = "DEMO",
            categoryTag = "AdisGebeya",
            imageUrl = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.ADISGEBEYA,
            ctaLabel = "View Products",
            accentColor = Color(0xFF34D399),
        ),
        SuperAppPromotionItem(
            id = "demo-burgers",
            title = "Burger menus",
            subtitle = "Find restaurant menus and prices before ordering.",
            highlightBadge = "DEMO",
            categoryTag = "Restaurants",
            imageUrl = "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.RESTAURANTS,
            ctaLabel = "View Menu",
            accentColor = Color(0xFFF59E0B),
        ),
        SuperAppPromotionItem(
            id = "demo-promotions",
            title = "Discover promotions",
            subtitle = "Preview the offers area; no voucher is issued here.",
            highlightBadge = "DEMO",
            categoryTag = "Promotions",
            imageUrl = "https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.PROMOTIONS,
            ctaLabel = "Explore",
            accentColor = Color(0xFFF87171),
        ),
        SuperAppPromotionItem(
            id = "demo-channels",
            title = "Community channels",
            subtitle = "Open Chat to explore channels and conversations.",
            highlightBadge = "DEMO",
            categoryTag = "Chat",
            imageUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.CHAT,
            ctaLabel = "Open Chat",
            accentColor = Color(0xFF60A5FA),
        ),
        SuperAppPromotionItem(
            id = "demo-marketplace",
            title = "Everyday essentials",
            subtitle = "Browse the marketplace for current listings.",
            highlightBadge = "DEMO",
            categoryTag = "Promotions",
            imageUrl = "https://images.unsplash.com/photo-1472851294608-062f824d29cc?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.ADISGEBEYA,
            ctaLabel = "View Products",
            accentColor = Color(0xFFFBBF24),
        ),
        SuperAppPromotionItem("demo-cafe", "Cafe menus", "Explore food and drink menus in Restaurants.", "DEMO", "Restaurants", "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=1000&q=80", SuperAppService.RESTAURANTS, "View Menu", Color(0xFFFB923C)),
        SuperAppPromotionItem("demo-pizza", "Pizza & more", "See available restaurants and their current menus.", "DEMO", "Restaurants", "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=1000&q=80", SuperAppService.RESTAURANTS, "View Menu", Color(0xFFF59E0B)),
        SuperAppPromotionItem("demo-shopping", "Local shopping", "Discover sellers and current products in AdisGebeya.", "DEMO", "AdisGebeya", "https://images.unsplash.com/photo-1441986300917-64674bd600d8?auto=format&fit=crop&w=1000&q=80", SuperAppService.ADISGEBEYA, "View Products", Color(0xFF34D399)),
        SuperAppPromotionItem("demo-tables", "Dine-in discovery", "Browse participating restaurants and menus.", "DEMO", "Restaurants", "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=1000&q=80", SuperAppService.RESTAURANTS, "View Menu", Color(0xFFFB923C)),
    )
}
