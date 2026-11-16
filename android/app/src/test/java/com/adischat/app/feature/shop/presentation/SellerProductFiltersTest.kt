package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopSellerProductDto
import com.adischat.app.feature.shop.data.ShopSellerProductVariantDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SellerProductFiltersTest {
    private val products = listOf(
        ShopSellerProductDto(id = "one", title = "Office Printer", sellerSku = "OFF-01", status = "draft", moderationStatus = "pending"),
        ShopSellerProductDto(id = "two", title = "Blue Shirt", status = "submitted", moderationStatus = "under_review", variants = listOf(ShopSellerProductVariantDto(sku = "BLUE-M"))),
        ShopSellerProductDto(id = "three", title = "Desk Lamp", status = "active", moderationStatus = "approved"),
        ShopSellerProductDto(id = "four", title = "Paper Pack", status = "draft", moderationStatus = "changes_requested"),
    )

    @Test fun searchMatchesTitleSellerSkuAndVariantSku() {
        assertEquals(listOf("one"), filterSellerProducts(products, "printer", "All").map { it.id })
        assertEquals(listOf("one"), filterSellerProducts(products, "off-01", "All").map { it.id })
        assertEquals(listOf("two"), filterSellerProducts(products, "blue-m", "All").map { it.id })
    }

    @Test fun statusFiltersUseRealModerationState() {
        assertEquals(listOf("one"), filterSellerProducts(products, "", "Draft").map { it.id })
        assertEquals(listOf("two"), filterSellerProducts(products, "", "In review").map { it.id })
        assertEquals(listOf("three"), filterSellerProducts(products, "", "Approved").map { it.id })
        assertEquals(listOf("four"), filterSellerProducts(products, "", "Rejected").map { it.id })
    }
}
