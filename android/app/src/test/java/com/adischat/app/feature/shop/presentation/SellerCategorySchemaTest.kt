package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopCategoryAttributeDto
import com.adischat.app.feature.shop.data.ShopCategoryDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SellerCategorySchemaTest {
    @Test fun threeLevelLineageFollowsSelectedLeaf() {
        val categories = listOf(
            ShopCategoryDto(id = "root", name = "Office", isLeaf = false),
            ShopCategoryDto(id = "branch", parentId = "root", name = "School", isLeaf = false),
            ShopCategoryDto(id = "leaf", parentId = "branch", name = "Equipment"),
        )
        assertEquals(listOf("leaf", "branch", "root"), sellerCategoryLineage("leaf", categories))
        assertEquals(emptyList<String>(), sellerCategoryLineage("missing", categories))
    }

    @Test fun leafOverridesInheritedAttributeAndEmptySchemaStaysEmpty() {
        val inherited = ShopCategoryAttributeDto(id = "size", code = "SIZE", required = false, sortOrder = 20)
        val own = inherited.copy(required = true, sortOrder = 2)
        val color = ShopCategoryAttributeDto(id = "color", code = "COLOR", sortOrder = 10)
        assertEquals(listOf(own, color), mergeSellerCategoryAttributes(listOf(listOf(own), listOf(inherited, color))))
        assertEquals(emptyList<ShopCategoryAttributeDto>(), mergeSellerCategoryAttributes(listOf(emptyList(), emptyList())))
    }

    @Test fun specificationValidationUsesServerTypesAndChoices() {
        val attributes = listOf(
            ShopCategoryAttributeDto(code = "EQUIPMENT_TYPE", displayName = "Equipment type", dataType = "choice", suggestions = listOf("Printer", "Scanner"), required = true),
            ShopCategoryAttributeDto(code = "CAPACITY", displayName = "Capacity", dataType = "number"),
            ShopCategoryAttributeDto(code = "WIRELESS", displayName = "Wireless", dataType = "boolean"),
        )
        assertEquals(emptyList<String>(), sellerSpecificationErrors(attributes, emptyMap(), false))
        assertEquals(listOf("Equipment type is required."), sellerSpecificationErrors(attributes, emptyMap(), true))
        assertEquals(3, sellerSpecificationErrors(attributes, mapOf("EQUIPMENT_TYPE" to "Other", "CAPACITY" to "abc", "WIRELESS" to "maybe"), false).size)
        assertEquals(emptyList<String>(), sellerSpecificationErrors(attributes, mapOf("EQUIPMENT_TYPE" to "Printer", "CAPACITY" to "12.5", "WIRELESS" to "false"), true))
        assertEquals(emptyList<String>(), sellerSpecificationErrors(
            listOf(ShopCategoryAttributeDto(code = "OPEN_CHOICE", dataType = "choice")),
            mapOf("OPEN_CHOICE" to "Existing custom value"),
            false,
        ))
    }

    @Test fun conditionAndDisclosuresAppearOnlyFromMetadataAndNeverBecomeOptions() {
        val condition = ShopCategoryAttributeDto(code = "CONDITION", displayName = "Condition", dataType = "choice", variantCapable = true)
        val defects = ShopCategoryAttributeDto(code = "KNOWN_DEFECTS", displayName = "Known defects")
        val color = ShopCategoryAttributeDto(code = "COLOR", displayName = "Color", variantCapable = true)
        val processor = ShopCategoryAttributeDto(code = "PROCESSOR", displayName = "Processor")
        assertEquals(emptyList<ShopCategoryAttributeDto>(), sellerAttributeSections(emptyList()).conditionAndDisclosures)
        val sections = sellerAttributeSections(listOf(processor, condition, color, defects))
        assertEquals(listOf(processor), sections.specifications)
        assertEquals(listOf(color), sections.options)
        assertEquals(listOf(condition, defects), sections.conditionAndDisclosures)
        assertEquals(sellerConditionChoices, sellerSpecificationChoices(condition))
        assertEquals(emptyList<String>(), sellerSpecificationErrors(listOf(condition), emptyMap(), true))
        assertEquals(listOf("Choose a listed value for Condition."), sellerSpecificationErrors(listOf(condition), mapOf("CONDITION" to "Like new-ish"), false))
        assertEquals(emptyList<String>(), sellerSpecificationErrors(listOf(condition), mapOf("CONDITION" to "Like New"), true))
    }

    @Test fun measuredBatteryHealthMustBeWithinPercentageBounds() {
        val battery = ShopCategoryAttributeDto(code = "BATTERY_HEALTH_PERCENT", displayName = "Measured battery health", dataType = "number", unit = "%")
        assertEquals(emptyList<String>(), sellerSpecificationErrors(listOf(battery), emptyMap(), false))
        for (value in listOf("0", "85.5", "100")) {
            assertEquals(emptyList<String>(), sellerSpecificationErrors(listOf(battery), mapOf(battery.code to value), false))
        }
        for (value in listOf("-1", "100.1")) {
            assertEquals(listOf("Measured battery health must be from 0 to 100%."), sellerSpecificationErrors(listOf(battery), mapOf(battery.code to value), false))
        }
    }
}
