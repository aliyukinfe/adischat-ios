package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopProductSpecificationDto
import com.adischat.app.feature.shop.data.ShopSellingUnitDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductInformationPresentationTest {
    @Test
    fun `home hint prefers real structured attributes`() {
        val product = ShopProductDto(
            shortDescription = "Long seller summary",
            specifications = listOf(
                ShopProductSpecificationDto(code = "STORAGE", name = "Storage", valueText = "256GB"),
                ShopProductSpecificationDto(code = "RAM", name = "RAM", valueText = "8GB"),
            ),
        )
        assertEquals("8GB • 256GB", productCompactHighlight(product))
    }

    @Test
    fun `home hint uses honest short description for legacy product`() {
        val product = ShopProductDto(shortDescription = "Cotton jacket for everyday wear")
        assertEquals("Cotton jacket for everyday wear", productCompactHighlight(product))
    }

    @Test
    fun `selling unit hint is visible when not piece`() {
        val product = ShopProductDto(
            sellingUnitCode = "KILOGRAM",
            sellingUnit = ShopSellingUnitDto(code = "KILOGRAM", name = "Kilogram", symbol = "kg"),
        )
        assertEquals("Sold by Kilogram", productCompactHighlight(product))
    }

    @Test
    fun `private specification codes never map to buyer rows`() {
        val product = ShopProductDto(
            specifications = listOf(
                ShopProductSpecificationDto(code = "SELLER_COST", name = "Cost", valueNumber = 120.0),
                ShopProductSpecificationDto(code = "MODEL", name = "Model", valueText = "A56"),
            ),
        )
        val rows = publicProductDetails(product)
        assertEquals(listOf("MODEL"), rows.map { it.code })
        assertFalse(rows.any { it.value == "120" })
    }

    @Test
    fun `missing values and empty descriptions stay hidden`() {
        assertNull(productCompactHighlight(ShopProductDto()))
        assertNull(" \n\n ".cleanSellerText())
        assertTrue(publicProductDetails(ShopProductDto()).isEmpty())
    }

    @Test
    fun `long mixed language description is preserved but blank lines normalized`() {
        val text = "Samsung phone\n\n\nሳምሰንግ ስልክ " + "details ".repeat(80)
        val clean = text.cleanSellerText()
        assertTrue(clean!!.contains("ሳምሰንግ ስልክ"))
        assertFalse(clean.contains("\n\n\n"))
        assertTrue(clean.length > 300)
    }

    @Test
    fun `numeric specification retains public unit`() {
        val spec = ShopProductSpecificationDto(code = "SCREEN_SIZE", name = "Screen size", valueNumber = 6.7, unit = "inch")
        assertEquals("6.7 inch", spec.publicValue())
    }

    @Test
    fun `condition and disclosures show only supplied seller reported values`() {
        val product = ShopProductDto(specifications = listOf(
            ShopProductSpecificationDto(code = "CONDITION", name = "Condition", valueText = "Open Box"),
            ShopProductSpecificationDto(code = "KNOWN_DEFECTS", name = "Known defects", valueText = "Small scratch"),
            ShopProductSpecificationDto(code = "BATTERY_HEALTH_PERCENT", name = "Measured battery health", valueNumber = 85.0, unit = "%"),
            ShopProductSpecificationDto(code = "REPAIR_HISTORY", name = "Repair history"),
        ))
        val rows = publicProductDetails(product)
        assertEquals(listOf("CONDITION", "KNOWN_DEFECTS", "BATTERY_HEALTH_PERCENT"), rows.map { it.code })
        assertEquals("Condition (seller-reported)", rows[0].label)
        assertEquals("Open Box", rows[0].value)
        assertEquals("Known defects (seller-reported)", rows[1].label)
        assertEquals("85%", rows[2].value)
        assertTrue(publicProductDetails(ShopProductDto()).isEmpty())
    }

    @Test
    fun `invalid condition battery health and identifier fields stay out of buyer rows`() {
        val product = ShopProductDto(specifications = listOf(
            ShopProductSpecificationDto(code = "CONDITION", valueText = "Brand new verified"),
            ShopProductSpecificationDto(code = "BATTERY_HEALTH_PERCENT", valueNumber = 110.0, unit = "%"),
            ShopProductSpecificationDto(code = "IMEI_1", name = "IMEI 1", valueText = "private-id"),
            ShopProductSpecificationDto(code = "OTHER", name = "Serial Number", valueText = "private-serial"),
            ShopProductSpecificationDto(code = "MODEL", name = "Model", valueText = "Galaxy A16"),
        ))
        assertEquals(listOf("MODEL"), publicProductDetails(product).map { it.code })
    }
}
