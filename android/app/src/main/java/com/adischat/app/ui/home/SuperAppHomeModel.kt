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
    val validityNote: String = "QA Preview • Demo voucher only",
)

object SuperAppDemoPromotions {
    val items: List<SuperAppPromotionItem> = listOf(
        SuperAppPromotionItem(
            id = "promo-dinein-50",
            title = "Habesha Dine-In Welcome Offer",
            subtitle = "Enjoy ETB 100 off slow-simmered Doro Wat and Kitfo across partner tables.",
            highlightBadge = "50% OFF",
            categoryTag = "Restaurants",
            imageUrl = "https://images.unsplash.com/photo-1541544741938-0af808871cc0?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.RESTAURANTS,
            ctaLabel = "Explore Menus",
            accentColor = Color(0xFFFB923C),
            originalPriceLabel = "ETB 580",
            promoPriceLabel = "ETB 480",
        ),
        SuperAppPromotionItem(
            id = "promo-shop-gadgets",
            title = "AdisGebeya Tech Flash Sale",
            subtitle = "Premium wireless earbuds and mobile accessories with free express delivery in Addis.",
            highlightBadge = "FREE DELIVERY",
            categoryTag = "AdisGebeya",
            imageUrl = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.ADISGEBEYA,
            ctaLabel = "Shop Deals",
            accentColor = Color(0xFF34D399),
            originalPriceLabel = "ETB 2,400",
            promoPriceLabel = "ETB 1,890",
        ),
        SuperAppPromotionItem(
            id = "promo-burgers-craft",
            title = "Bun & Patty Smash Special",
            subtitle = "Dry-aged double smash patties with loaded parmesan herb fries.",
            highlightBadge = "POPULAR",
            categoryTag = "Restaurants",
            imageUrl = "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.RESTAURANTS,
            ctaLabel = "View Restaurant",
            accentColor = Color(0xFFF59E0B),
            originalPriceLabel = "ETB 440",
            promoPriceLabel = "ETB 390",
        ),
        SuperAppPromotionItem(
            id = "promo-super-weekend",
            title = "Super App Weekend Bonus",
            subtitle = "Unified voucher valid for dining sessions, marketplace orders, and chat perks.",
            highlightBadge = "SUPER PACK",
            categoryTag = "Promotions",
            imageUrl = "https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.PROMOTIONS,
            ctaLabel = "Claim Voucher",
            accentColor = Color(0xFFF87171),
        ),
        SuperAppPromotionItem(
            id = "promo-channels-creators",
            title = "Community & Channel Broadcasts",
            subtitle = "Join tech updates, startup discussions, and city announcements inside Chat Channels.",
            highlightBadge = "VERIFIED",
            categoryTag = "Chat",
            imageUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.CHAT,
            ctaLabel = "Open Chat Channels",
            accentColor = Color(0xFF60A5FA),
        ),
        SuperAppPromotionItem(
            id = "promo-voucher-welcome",
            title = "Welcome Voucher 15% OFF",
            subtitle = "Apply voucher code ADIS15 at table checkout or marketplace delivery.",
            highlightBadge = "15% OFF",
            categoryTag = "Promotions",
            imageUrl = "https://images.unsplash.com/photo-1472851294608-062f824d29cc?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.PROMOTIONS,
            ctaLabel = "View Deals",
            accentColor = Color(0xFFFBBF24),
        ),
    )
}
