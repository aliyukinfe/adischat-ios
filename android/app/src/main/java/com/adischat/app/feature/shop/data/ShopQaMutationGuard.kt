package com.adischat.app.feature.shop.data

import okhttp3.Interceptor
import java.io.IOException
import java.net.URI

internal object ShopQaMutationGuard {
    private const val STAGING_HOST = "opkqyvttbyezjbqwkvho.supabase.co"
    private val safeRpcPosts = setOf(
        "adishop_qa_catalog_page", "adishop_qa_product_detail", "adishop_qa_cart_action",
        "adishop_qa_buyer_orders", "adishop_qa_buyer_order_detail",
        "adishop_product_catalog_payload", "adishop_category_attribute_schema",
    )
    private val unsafeFunctionWords = listOf(
        "checkout", "order", "refund", "payment", "voucher", "coupon", "discount",
        "delivery-code", "buy-again", "inventory", "reservation", "redeem", "wallet",
        "withdraw", "payout", "pay-code", "balance", "carrier", "handoff", "ship", "void", "credit",
    )

    fun stagingUrlValid(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == STAGING_HOST && uri.port == -1 &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
    }.getOrDefault(false)

    fun blocks(method: String, segments: List<String>): Boolean {
        if (segments.take(3) == listOf("rest", "v1", "rpc") && method != "GET") {
            return segments.size != 4 || segments[3] !in safeRpcPosts
        }
        if (segments.take(2) == listOf("rest", "v1")) return method != "GET"
        if (segments.take(2) != listOf("functions", "v1")) return true
        val function = segments.getOrNull(2).orEmpty().lowercase()
        if (function == "adishop-reveal-delivery-code") return true
        if (function.startsWith("adishop-seller-mark-")) return true
        if (function.contains("exchange") && function != "adishop-exchange-adischat-session") return true
        return method != "GET" && unsafeFunctionWords.any { it in function }
    }

    fun interceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.url.scheme != "https" || request.url.host != STAGING_HOST || request.url.port != 443 ||
            blocks(request.method, request.url.pathSegments)) {
            throw IOException("Shop order and payment actions are disabled in QA")
        }
        chain.proceed(request)
    }
}
