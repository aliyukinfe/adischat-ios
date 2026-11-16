package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopSellingUnitDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SellerUnitSuggestionsTest {
    private val units = listOf(
        ShopSellingUnitDto(code = "METER", name = "Meter", displayOrder = 1),
        ShopSellingUnitDto(code = "PIECE", name = "Piece", displayOrder = 2),
        ShopSellingUnitDto(code = "KILOGRAM", name = "Kilogram", displayOrder = 3),
        ShopSellingUnitDto(code = "SET", name = "Set", displayOrder = 4),
    )

    @Test fun existingUnitOutsideCategorySuggestionsStaysAvailableAndSelectedFirst() {
        val officeEquipment = "8449e8e5-bff0-4db4-81b0-1f588b9b2a61"
        assertEquals(setOf("PIECE", "SET"), suggestedSellingUnitCodes(officeEquipment))
        assertEquals(
            listOf("KILOGRAM", "PIECE", "SET", "METER"),
            orderSellingUnits(units, officeEquipment, "KILOGRAM").map { it.code },
        )
    }

    @Test fun minimalCategoryRetainsObservedMeterUnit() {
        val sewingAccessories = "d1d6adcd-ff39-4892-89df-a637c8288df4"
        assertEquals(true, "METER" in suggestedSellingUnitCodes(sewingAccessories))
        assertEquals(units.size, orderSellingUnits(units, sewingAccessories, "METER").size)
    }

    @Test fun unknownCategoryStillOffersEveryActiveServerUnit() {
        assertEquals(emptySet<String>(), suggestedSellingUnitCodes("new-category"))
        assertEquals(
            listOf("SET", "METER", "PIECE", "KILOGRAM"),
            orderSellingUnits(units, "new-category", "SET").map { it.code },
        )
    }
}
