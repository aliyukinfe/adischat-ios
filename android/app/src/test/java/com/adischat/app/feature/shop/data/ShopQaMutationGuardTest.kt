package com.adischat.app.feature.shop.data

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ShopQaMutationGuardTest {
    private val staging = "https://opkqyvttbyezjbqwkvho.supabase.co"

    @Test fun exactStagingEndpointOnly() {
        assertTrue(ShopQaMutationGuard.stagingUrlValid(staging))
        assertFalse(ShopQaMutationGuard.stagingUrlValid(""))
        assertFalse(ShopQaMutationGuard.stagingUrlValid("https://mcewjfnkrtiiolzibsuh.supabase.co"))
        assertFalse(ShopQaMutationGuard.stagingUrlValid("$staging/other"))
    }

    @Test fun checkoutDiscountAndOrderRpcPostsAreDenied() {
        listOf("create_checkout", "confirm_checkout", "apply_coupon", "apply_voucher",
            "remove_discount", "reveal_delivery_code").forEach { name ->
            assertTrue(ShopQaMutationGuard.blocks("POST", listOf("rest", "v1", "rpc", "adishop_qa_$name")))
        }
        assertTrue(ShopQaMutationGuard.blocks("POST", listOf("rest", "v1", "rpc", "unknown_mutation")))
        assertTrue(ShopQaMutationGuard.blocks("POST", listOf("rest", "v1", "shop_orders")))
        listOf("buyer_orders", "buyer_order_detail", "cart_action").forEach { name ->
            assertTrue(ShopQaMutationGuard.blocks("POST", listOf("rest", "v1", "rpc", "adishop_qa_$name")))
        }
    }

    @Test fun edgeOrderPaymentAndDeliveryAliasesAreDenied() {
        listOf("adishop-create-checkout", "adishop-confirm-checkout", "adishop-apply-voucher",
            "adishop-apply-coupon", "adishop-admin-update-refund", "adishop-seller-accept-order-group",
            "adishop-seller-reject-order-group", "adishop-seller-hand-to-carrier",
            "adishop-seller-mark-shipped", "adishop-seller-mark-packed", "adishop-seller-mark-processing",
            "adishop-buyer-create-exchange-request", "adishop-seller-respond-exchange-request",
            "adishop-request-order-cancellation").forEach { name ->
            assertTrue(ShopQaMutationGuard.blocks("POST", listOf("functions", "v1", name)))
        }
        assertTrue(ShopQaMutationGuard.blocks("GET", listOf("functions", "v1", "adishop-reveal-delivery-code")))
        assertFalse(ShopQaMutationGuard.blocks("POST", listOf("functions", "v1", "adishop-create-address")))
        assertFalse(ShopQaMutationGuard.blocks("POST", listOf("functions", "v1", "adishop-exchange-adischat-session")))
    }

    @Test fun blockedRequestsNeverReachNetworkInterceptor() {
        val reached = AtomicInteger()
        val client = OkHttpClient.Builder()
            .addInterceptor(ShopQaMutationGuard.interceptor())
            .addInterceptor { chain ->
                reached.incrementAndGet()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body("ok".toResponseBody()).build()
            }.build()
        val checkout = Request.Builder().url("$staging/rest/v1/rpc/adishop_qa_confirm_checkout")
            .post("{}".toRequestBody()).build()
        assertThrows(IOException::class.java) { client.newCall(checkout).execute().use { } }
        val knownSessionRead = Request.Builder().url("$staging/rest/v1/rpc/adishop_qa_buyer_orders")
            .post("{}".toRequestBody()).build()
        assertThrows(IOException::class.java) { client.newCall(knownSessionRead).execute().use { } }
        val production = Request.Builder().url("https://mcewjfnkrtiiolzibsuh.supabase.co/functions/v1/adishop-create-address")
            .post("{}".toRequestBody()).build()
        assertThrows(IOException::class.java) { client.newCall(production).execute().use { } }
        assertEquals(0, reached.get())
        val catalog = Request.Builder().url("$staging/rest/v1/rpc/adishop_qa_catalog_page")
            .post("{}".toRequestBody()).build()
        client.newCall(catalog).execute().use { assertEquals(200, it.code) }
        assertEquals(1, reached.get())
    }
}
