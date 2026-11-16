package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopCategoryAttributeDto
import com.adischat.app.feature.shop.data.ShopCategoryDto

internal val sellerConditionChoices = listOf("New", "Open Box", "Like New", "Slightly Used", "Used", "Refurbished", "For Parts")
private val sellerDisclosureCodes = setOf("KNOWN_DEFECTS", "REPAIR_HISTORY", "BATTERY_HEALTH_PERCENT", "INCLUDED_ACCESSORIES", "DEVICE_LOCK_STATUS")

internal fun sellerProductLevelAttribute(attribute: ShopCategoryAttributeDto): Boolean =
    !attribute.variantCapable || attribute.code == "CONDITION" || attribute.code in sellerDisclosureCodes

internal fun sellerSpecificationChoices(attribute: ShopCategoryAttributeDto): List<String> =
    if (attribute.code == "CONDITION") sellerConditionChoices else attribute.suggestions

internal data class SellerAttributeSections(
    val specifications: List<ShopCategoryAttributeDto>,
    val options: List<ShopCategoryAttributeDto>,
    val conditionAndDisclosures: List<ShopCategoryAttributeDto>,
)

internal fun sellerAttributeSections(attributes: List<ShopCategoryAttributeDto>): SellerAttributeSections = SellerAttributeSections(
    specifications = attributes.filter { sellerProductLevelAttribute(it) && it.code != "CONDITION" && it.code !in sellerDisclosureCodes },
    options = attributes.filterNot(::sellerProductLevelAttribute),
    conditionAndDisclosures = attributes.filter { it.code == "CONDITION" || it.code in sellerDisclosureCodes },
)

internal fun sellerCategoryLineage(categoryId: String, categories: List<ShopCategoryDto>): List<String> {
    val byId = categories.associateBy { it.id }
    val ids = mutableListOf<String>()
    val visited = mutableSetOf<String>()
    var currentId: String? = categoryId
    while (!currentId.isNullOrBlank() && visited.add(currentId)) {
        val category = byId[currentId] ?: break
        ids += category.id
        currentId = category.parentId
    }
    return ids
}

internal fun mergeSellerCategoryAttributes(schemasFromLeafToRoot: List<List<ShopCategoryAttributeDto>>): List<ShopCategoryAttributeDto> {
    val byIdentity = linkedMapOf<String, ShopCategoryAttributeDto>()
    schemasFromLeafToRoot.flatten().forEach { attribute ->
        val identity = attribute.id.ifBlank { attribute.code }
        if (identity.isNotBlank()) byIdentity.putIfAbsent(identity, attribute)
    }
    return byIdentity.values.sortedWith(compareBy({ it.sortOrder }, { it.displayName }, { it.code }))
}

internal fun sellerSpecificationErrors(
    attributes: List<ShopCategoryAttributeDto>,
    values: Map<String, String>,
    requireRequired: Boolean,
): List<String> = attributes.filter(::sellerProductLevelAttribute).mapNotNull { attribute ->
    val value = values[attribute.code].orEmpty().trim()
    when {
        value.isEmpty() && requireRequired && attribute.required -> "${attribute.displayName} is required."
        value.isEmpty() -> null
        attribute.dataType == "number" && (value.toDoubleOrNull()?.isFinite() != true) -> "Enter a valid number for ${attribute.displayName}."
        attribute.code == "BATTERY_HEALTH_PERCENT" && value.toDoubleOrNull()?.let { it in 0.0..100.0 } != true -> "Measured battery health must be from 0 to 100%."
        attribute.dataType == "boolean" && value != "true" && value != "false" -> "Choose Yes or No for ${attribute.displayName}."
        (attribute.code == "CONDITION" || attribute.dataType == "choice") && sellerSpecificationChoices(attribute).isNotEmpty() && sellerSpecificationChoices(attribute).none { it == value } ->
            "Choose a listed value for ${attribute.displayName}."
        else -> null
    }
}
