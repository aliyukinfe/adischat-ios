package com.adischat.app.feature.shop.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopStateReducersTest {
    @Test
    fun cartItemCountSumsPositiveQuantities() {
        val cart = ShopCartDto(items = listOf(
            ShopCartItemDto(quantity = 2),
            ShopCartItemDto(quantity = 3),
            ShopCartItemDto(quantity = -4),
        ))

        assertEquals(5, ShopStateReducers.cartItemCount(cart))
    }

    @Test
    fun cachedSessionRequiresTokenAndFutureExpiryBuffer() {
        assertFalse(ShopStateReducers.canUseCachedSession(null, nowMs = 1_000))
        assertFalse(ShopStateReducers.canUseCachedSession(ShopSessionDto(accessToken = "", expiresAt = 90_000), nowMs = 1_000))
        assertFalse(ShopStateReducers.canUseCachedSession(ShopSessionDto(accessToken = "token", expiresAt = 30_000), nowMs = 1_000))
        assertTrue(ShopStateReducers.canUseCachedSession(ShopSessionDto(accessToken = "token", expiresAt = 90_000), nowMs = 1_000))
    }

    @Test
    fun cachedIdentityRequiresMatchingActiveAccount() {
        val session = ShopSessionDto(accessToken = "token", expiresAt = 90_000)
        val profile = ShopProfileDto(id = "profile-a", adisChatUserId = "firebase-a")

        assertTrue(ShopStateReducers.canUseCachedIdentity(session, profile, "firebase-a", nowMs = 1_000))
        assertFalse(ShopStateReducers.canUseCachedIdentity(session, profile, "firebase-b", nowMs = 1_000))
        assertFalse(ShopStateReducers.canUseCachedIdentity(session, profile, "", nowMs = 1_000))
        assertFalse(ShopStateReducers.canUseCachedIdentity(session, null, "firebase-a", nowMs = 1_000))
    }

    @Test
    fun shopSessionMemoryClearsProtectedIdentityState() {
        ShopSessionMemory.update(
            ShopSessionDto(accessToken = "token", refreshToken = "refresh", expiresAt = 90_000),
            ShopProfileDto(id = "profile-a", displayName = "Account A"),
        )

        assertEquals("token", ShopSessionMemory.session()?.accessToken)
        assertEquals("profile-a", ShopSessionMemory.profile()?.id)

        ShopSessionMemory.clear()

        assertEquals(null, ShopSessionMemory.session())
        assertEquals(null, ShopSessionMemory.profile())
    }

    @Test
    fun safeErrorMessageHidesTechnicalDetails() {
        assertEquals("Your cart is temporarily unavailable.", ShopStateReducers.safeErrorMessage("JWT policy failed", "Your cart is temporarily unavailable."))
        assertEquals("Check your connection and try again.", ShopStateReducers.safeErrorMessage("", "Check your connection and try again."))
        assertEquals("Unable to add this product.", ShopStateReducers.safeErrorMessage("Unable to add this product.", "Your cart is temporarily unavailable."))
    }

    @Test
    fun sellerRouteMatchesStatus() {
        assertEquals("apply", ShopStateReducers.sellerRoute("not_started"))
        assertEquals("status", ShopStateReducers.sellerRoute("under_review"))
        assertEquals("dashboard", ShopStateReducers.sellerRoute("approved"))
    }

    @Test
    fun shopRootTabMappingMatchesNativeDestinations() {
        assertEquals("home", ShopStateReducers.rootTabForRoute("home"))
        assertEquals("trending", ShopStateReducers.rootTabForRoute("trending"))
        assertEquals("cart", ShopStateReducers.rootTabForRoute("cart"))
        assertEquals("account", ShopStateReducers.rootTabForRoute("orders"))
        assertEquals("account", ShopStateReducers.rootTabForRoute("saved"))
        assertEquals("account", ShopStateReducers.rootTabForRoute("recently_viewed"))
        assertEquals("account", ShopStateReducers.rootTabForRoute("addresses"))
        assertEquals("account", ShopStateReducers.rootTabForRoute("seller_apply"))
        assertEquals("account", ShopStateReducers.rootTabForRoute("buyer_protection"))
    }

    @Test
    fun profileShopRoutesMapToAccountSubpages() {
        assertEquals("orders", ShopStateReducers.accountSubpageForRoute("orders"))
        assertEquals("saved", ShopStateReducers.accountSubpageForRoute("saved"))
        assertEquals("recently_viewed", ShopStateReducers.accountSubpageForRoute("recently_viewed"))
        assertEquals("addresses", ShopStateReducers.accountSubpageForRoute("addresses"))
        assertEquals("reviews", ShopStateReducers.accountSubpageForRoute("reviews"))
        assertEquals("seller", ShopStateReducers.accountSubpageForRoute("seller_status"))
        assertEquals("buyer_protection", ShopStateReducers.accountSubpageForRoute("buyer_protection"))
        assertEquals(null, ShopStateReducers.accountSubpageForRoute("home"))
    }

    @Test
    fun cartBadgeUsesServerCartCountReducer() {
        val cart = ShopCartDto(items = listOf(
            ShopCartItemDto(quantity = 1),
            ShopCartItemDto(quantity = 2),
        ))

        assertEquals(3, ShopStateReducers.cartItemCount(cart))
    }

    @Test
    fun sellerProductUpdatePayloadOmitsNullMediaCollections() {
        val request = ShopSellerProductUpdateRequest(
            productId = "product-a",
            requestId = "request-a",
            product = ShopSellerProductDraftPayload(
                title = "Gallery product",
                shortDescription = "Short description",
                description = "Long description",
                priceMinor = 1_299_00,
                stockQuantity = 12,
                images = null,
                variants = null,
            ),
        )

        val json = Gson().toJson(request)

        assertFalse(json.contains("\"images\""))
        assertFalse(json.contains("\"variants\""))
    }

    @Test
    fun sellerOrderGroupMapsSellerVisibleIssuesOnly() {
        val json = """
            {
              "id": "group-a",
              "status": "delivered",
              "issues": [
                {
                  "status": "under_review",
                  "reason_code": "not_as_described",
                  "reason_label": "Item not as described",
                  "description": "Buyer-visible description",
                  "created_at": "2026-07-26T12:00:00Z",
                  "events": [
                    {
                      "event_code": "admin_issue.status_change",
                      "next_status": "under_review",
                      "public_message": "Support is reviewing this issue.",
                      "created_at": "2026-07-26T12:10:00Z"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val group = Gson().fromJson(json, ShopOrderGroupDto::class.java)

        assertEquals(1, group.issues.size)
        assertEquals("under_review", group.issues.first().status)
        assertEquals("Item not as described", group.issues.first().reasonLabel)
        assertEquals("Buyer-visible description", group.issues.first().description)
        assertEquals("", group.issues.first().id)
        assertEquals("", group.issues.first().orderId)
        assertEquals(null, group.issues.first().orderGroupId)
        assertEquals(1, group.issues.first().events.size)
        assertEquals("", group.issues.first().events.first().id)
    }

    @Test
    fun sellerOrderGroupNoIssueStateDefaultsToEmptyList() {
        val group = Gson().fromJson("""{"id":"group-a","status":"delivered"}""", ShopOrderGroupDto::class.java)

        assertTrue(group.issues.isEmpty())
    }
}
