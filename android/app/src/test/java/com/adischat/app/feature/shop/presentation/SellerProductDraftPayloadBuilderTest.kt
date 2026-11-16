package com.adischat.app.feature.shop.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SellerProductDraftPayloadBuilderTest {
    @Test fun imagePreparationSaveKeepsAllEnteredFieldsWithoutReplacingImages() {
        val payload = sellerProductDraftPayload(SellerProductFormValues(
            title = " QA Image Retention ",
            brand = " Test Brand ",
            categoryId = "category-id",
            sellingUnitCode = "PIECE",
            minimumOrderQuantity = "2",
            productTypeDescription = "",
            shortDescription = " Short details ",
            description = " Full details ",
            price = "123",
            stock = "7",
            shipping = " local only ",
            returns = " QA policy ",
            specs = "Color: Blue\nCapacity: 8GB",
            variantOutputsJson = "",
            imageIds = emptySet(),
        ))
        assertEquals("QA Image Retention", payload.title)
        assertEquals("Test Brand", payload.brand)
        assertEquals("category-id", payload.categoryId)
        assertEquals("Short details", payload.shortDescription)
        assertEquals("Full details", payload.description)
        assertEquals(12300, payload.priceMinor)
        assertEquals(7, payload.stockQuantity)
        assertEquals(2, payload.minimumOrderQuantity)
        assertEquals("Blue", payload.specifications["Color"])
        assertEquals("8GB", payload.specifications["Capacity"])
        assertEquals("local only", payload.shippingDetails["notes"])
        assertNull(payload.images)
    }
}
