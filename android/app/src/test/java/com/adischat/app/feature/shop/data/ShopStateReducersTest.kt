package com.adischat.app.feature.shop.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

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
        assertEquals("Your session expired. Please sign in again.", ShopStateReducers.safeErrorMessage("HTTP 401 Client Error", "Couldn't load orders."))
        assertEquals("Couldn't load orders.", ShopStateReducers.safeErrorMessage("HTTP 503 Server Error", "Couldn't load orders."))
    }

    @Test
    fun accountSwitchClearsBuyerSellerAdminAndErrorState() {
        val accountA = ShopProtectedState(
            ownerUid = "buyer-a",
            error = "old error",
            orders = listOf(ShopOrderDto(id = "order-a")),
            sellerStatus = "approved",
            adminSession = ShopAdminSessionDto(primaryRole = "super_admin"),
        )

        val accountB = ShopStateReducers.stateForActiveIdentity(accountA, "buyer-b")

        assertEquals("buyer-b", accountB.ownerUid)
        assertTrue(accountB.orders.isEmpty())
        assertEquals("not_started", accountB.sellerStatus)
        assertEquals(null, accountB.adminSession)
        assertEquals(null, accountB.error)
    }

    @Test
    fun authRetryRefreshesOnceAndClearsSuccessfulRetryPath() = runBlocking {
        val coordinator = ShopAuthRetryCoordinator { it is TestUnauthorized }
        var authorization = "Bearer stale"
        val refreshes = AtomicInteger()
        val attempts = AtomicInteger()

        val result = coordinator.execute(
            authorization = { authorization },
            refreshAuthorization = {
                refreshes.incrementAndGet()
                authorization = "Bearer fresh"
                authorization
            },
        ) { token ->
            attempts.incrementAndGet()
            if (token == "Bearer stale") throw TestUnauthorized()
            "orders"
        }

        assertEquals("orders", result)
        assertEquals(1, refreshes.get())
        assertEquals(2, attempts.get())
    }

    @Test
    fun concurrentUnauthorizedRequestsShareOneRefresh() = runBlocking {
        val coordinator = ShopAuthRetryCoordinator { it is TestUnauthorized }
        var authorization = "Bearer stale"
        val refreshes = AtomicInteger()

        val results = coroutineScope {
            List(4) {
                async {
                    coordinator.execute(
                        authorization = { authorization },
                        refreshAuthorization = {
                            refreshes.incrementAndGet()
                            authorization = "Bearer fresh"
                            authorization
                        },
                    ) { token ->
                        if (token == "Bearer stale") throw TestUnauthorized()
                        "ok"
                    }
                }
            }.awaitAll()
        }

        assertEquals(listOf("ok", "ok", "ok", "ok"), results)
        assertEquals(1, refreshes.get())
    }

    @Test
    fun secondUnauthorizedResponseStopsWithoutRefreshLoop() = runBlocking {
        val coordinator = ShopAuthRetryCoordinator { it is TestUnauthorized }
        var authorization = "Bearer stale"
        val refreshes = AtomicInteger()

        val failure = runCatching {
            coordinator.execute(
                authorization = { authorization },
                refreshAuthorization = {
                    refreshes.incrementAndGet()
                    authorization = "Bearer fresh"
                    authorization
                },
            ) { throw TestUnauthorized() }
        }.exceptionOrNull()

        assertTrue(failure is ShopSessionExpiredException)
        assertEquals("Your session expired. Please sign in again.", failure?.message)
        assertEquals(1, refreshes.get())
    }

    @Test
    fun refreshFailureStopsWithHumanReadableSessionMessage() = runBlocking {
        val coordinator = ShopAuthRetryCoordinator { it is TestUnauthorized }

        val failure = runCatching {
            coordinator.execute(
                authorization = { "Bearer stale" },
                refreshAuthorization = { throw ShopSessionExpiredException() },
            ) { throw TestUnauthorized() }
        }.exceptionOrNull()

        assertTrue(failure is ShopSessionExpiredException)
        assertEquals("Your session expired. Please sign in again.", failure?.message)
    }

    private class TestUnauthorized : RuntimeException()

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
