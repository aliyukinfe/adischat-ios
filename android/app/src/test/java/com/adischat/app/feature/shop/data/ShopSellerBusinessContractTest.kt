package com.adischat.app.feature.shop.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopSellerBusinessContractTest {
    @Test
    fun sellerBusinessSnapshotMapsServerFields() {
        val envelope = Gson().fromJson(
            """{
              "ok": true,
              "sellerBusiness": {
                "stores": [{"id":"store-1","name":"Hulu Store"},{"id":"store-2","name":"Second Store"}],
                "selectedStore": {"id":"store-1","name":"Hulu Store","approval_status":"approved","contact_phone":"0911000000","customer_service_policy_text":"Contact support","updated_at":"2026-08-05T15:00:00Z"},
                "products": [{"id":"product-1","store_id":"store-1","title":"Test product","inventory":[{"id":"inventory-1","product_id":"product-1","quantity_available":8,"quantity_reserved":2,"low_stock_threshold":3}]}],
                "analytics": {"revenueMinor":12500,"orderCount":2,"deliveredOrderCount":1,"productsSold":3,"refundRate":0.0,"averageOrderValueMinor":6250,"topProducts":[{"title":"Test product","quantity":3,"revenueMinor":12500}]},
                "customers": [{"displayName":"Customer","orderCount":2,"lifetimeSpendMinor":12500,"latestOrderGroupId":"group-1","note":"repeat buyer","recentOrders":[{"orderNumber":"ADS-TEST","status":"delivered","totalMinor":12500}]}],
                "reviewSummary": {"total":2,"replied":1,"ratings":[{"rating":5,"count":2}]},
                "finance": {"payoutsAvailable":false,"capturedSalesMinor":12500,"pendingMinor":0,"transactions":[]},
                "settings": {"store_id":"store-1","notifications":{"orders":true},"language":"en","theme":"system"}
              }
            }""".trimIndent(),
            ShopEnvelope::class.java,
        ) as ShopEnvelope<*>

        val business = requireNotNull(envelope.sellerBusiness)
        assertEquals(listOf("store-1", "store-2"), business.stores.map { it.id })
        assertEquals("Hulu Store", business.selectedStore?.name)
        assertEquals("0911000000", business.selectedStore?.contactPhone)
        assertEquals("Contact support", business.selectedStore?.supportContact)
        assertEquals("2026-08-05T15:00:00Z", business.selectedStore?.updatedAt)
        assertEquals(8, business.products.single().inventory.single().quantityAvailable)
        assertEquals(12500L, business.analytics.revenueMinor)
        assertEquals(6250L, business.analytics.averageOrderValueMinor)
        assertEquals("Test product", business.analytics.topProducts.single().title)
        assertEquals("group-1", business.customers.single().latestOrderGroupId)
        assertEquals("ADS-TEST", business.customers.single().recentOrders.single().orderNumber)
        assertEquals(1, business.reviewSummary.replied)
        assertFalse(business.finance.payoutsAvailable)
        assertNotNull(business.settings.notifications["orders"])
    }

    @Test
    fun privacySafeCustomerModelHasNoAccountIdentifierFields() {
        val names = ShopSellerBusinessCustomerDto::class.java.declaredFields.map { it.name }.toSet()
        assertFalse("profileId" in names)
        assertFalse("firebaseUid" in names)
        assertFalse("email" in names)
        assertFalse("phone" in names)
    }

    @Test
    fun sellerResponseIdempotencyKeyIsStableForTheSameRequestVersion() {
        val first = sellerRequestIdempotencyKey("refund", "request-123", 4)
        val retry = sellerRequestIdempotencyKey("refund", "request-123", 4)

        assertEquals(first, retry)
        assertEquals("android-seller-refund-request-123-v4", first)
    }

    @Test
    fun sellerResponseIdempotencyKeyChangesWithRequestVersion() {
        val initial = sellerRequestIdempotencyKey("exchange", "request-123", 1)
        val nextVersion = sellerRequestIdempotencyKey("exchange", "request-123", 2)

        assertFalse(initial == nextVersion)
    }

    @Test
    fun sellerResponsePayloadIncludesExpectedVersion() {
        val json = Gson().toJson(
            ShopSellerRequestResponseRequest(
                requestId = "request-123",
                message = "Seller acknowledged the request.",
                idempotencyKey = sellerRequestIdempotencyKey("refund", "request-123", 7),
                expectedVersion = 7,
            ),
        )

        assertTrue(json.contains("\"expectedVersion\":7"))
        assertTrue(json.contains("\"idempotencyKey\":\"android-seller-refund-request-123-v7\""))
    }

    @Test
    fun sellerOrderActionsAndResponsesCarrySelectedStore() {
        val action = Gson().toJson(ShopSellerOrderActionRequest(orderGroupId = "group-1", storeId = "store-a"))
        val delivery = Gson().toJson(ShopSellerDeliveryCodeRequest("group-1", "123456", "store-a"))
        val response = Gson().toJson(ShopSellerRequestResponseRequest("request-1", "A valid reply", "request-key", 1, "store-a"))
        assertTrue(action.contains("\"storeId\":\"store-a\""))
        assertTrue(delivery.contains("\"storeId\":\"store-a\""))
        assertTrue(response.contains("\"storeId\":\"store-a\""))
    }

    @Test
    fun sellerOrderScopeEchoMapsFromServer() {
        val envelope = Gson().fromJson(
            """{"ok":true,"sellerOrderStoreId":"store-a","sellerOrderGroups":[]}""",
            ShopEnvelope::class.java,
        ) as ShopEnvelope<*>
        assertEquals("store-a", envelope.sellerOrderStoreId)
    }
}
