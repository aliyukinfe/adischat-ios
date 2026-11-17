package com.adischat.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The 6 modern super app sidebar services as approved in Phase H1:
 * - Chat
 * - AdisGebeya
 * - Restaurants
 * - Channels
 * - Seller Center
 * - Promotions
 *
 * (Home is intentionally excluded from the sidebar and stays in bottom navigation).
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
    CHAT(
        id = "chat",
        title = "Chat",
        subtitle = "Messages & Calls",
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
    CHANNELS(
        id = "channels",
        title = "Channels",
        subtitle = "News & Community Feeds",
        selectedIcon = Icons.Filled.Campaign,
        unselectedIcon = Icons.Outlined.Campaign,
        accentColor = Color(0xFFC084FC), // Purple
    ),
    SELLER_CENTER(
        id = "seller_center",
        title = "Seller Center",
        subtitle = "Merchant Dashboard",
        selectedIcon = Icons.Filled.Storefront,
        unselectedIcon = Icons.Outlined.Storefront,
        accentColor = Color(0xFFFBBF24), // Amber gold
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
            id = "promo-channels-creators",
            title = "AdisChat Creator Channels",
            subtitle = "Follow official news, tech updates, and community broadcasts with instant push notifications.",
            highlightBadge = "VERIFIED",
            categoryTag = "Channels",
            imageUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.CHANNELS,
            ctaLabel = "Browse Channels",
            accentColor = Color(0xFFC084FC),
        ),
        SuperAppPromotionItem(
            id = "promo-seller-perks",
            title = "Seller Center Zero-Commission",
            subtitle = "List your store products or restaurant menu on AdisChat with 30 days zero platform commission.",
            highlightBadge = "0% FEE",
            categoryTag = "Seller Center",
            imageUrl = "https://images.unsplash.com/photo-1472851294608-062f824d29cc?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.SELLER_CENTER,
            ctaLabel = "Start Selling",
            accentColor = Color(0xFFFBBF24),
        ),
        SuperAppPromotionItem(
            id = "promo-super-weekend",
            title = "Super App Weekend Bonus",
            subtitle = "Unified voucher valid for dining sessions, marketplace orders, and premium channel perks.",
            highlightBadge = "SUPER PACK",
            categoryTag = "Promotions",
            imageUrl = "https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?auto=format&fit=crop&w=1000&q=80",
            targetService = SuperAppService.PROMOTIONS,
            ctaLabel = "Claim Voucher",
            accentColor = Color(0xFFF87171),
        ),
    )
}
