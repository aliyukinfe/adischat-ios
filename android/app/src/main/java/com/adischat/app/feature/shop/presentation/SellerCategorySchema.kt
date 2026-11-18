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

internal fun enrichCategoryAttributes(
    categoryId: String,
    categoryCode: String?,
    baseAttributes: List<ShopCategoryAttributeDto>,
): List<ShopCategoryAttributeDto> {
    val id = categoryId.trim().lowercase()
    val code = categoryCode?.trim()?.uppercase().orEmpty()

    val mutable = baseAttributes.toMutableList()

    val isElectronics = id in CategoryVariantPresets.ELECTRONICS_CATEGORY_IDS || code.contains("ELECTRONIC") ||
        code.contains("PHONE") || code.contains("LAPTOP") || code.contains("TABLET")
    val isShoes = id in CategoryVariantPresets.FOOTWEAR_CATEGORY_IDS || code.contains("SHOE") || code.contains("FOOTWEAR")
    val isFurniture = id in CategoryVariantPresets.FURNITURE_CATEGORY_IDS || code.contains("FURNITURE") || code.contains("BEDDING")

    // If base is empty, inject standard attributes
    if (mutable.isEmpty()) {
        if (isElectronics) {
            mutable.add(ShopCategoryAttributeDto(code = "BRAND", displayName = "Brand", required = true, variantCapable = false))
            mutable.add(ShopCategoryAttributeDto(code = "MODEL", displayName = "Model", required = true, variantCapable = false))
            mutable.add(ShopCategoryAttributeDto(code = "COLOR", displayName = "Color", variantCapable = true, suggestions = listOf("Black", "Silver", "Blue", "White", "Grey", "Gold")))
            mutable.add(ShopCategoryAttributeDto(code = "STORAGE", displayName = "Storage", variantCapable = true, suggestions = CategoryVariantPresets.ELECTRONICS_STORAGE_CHOICES))
            mutable.add(ShopCategoryAttributeDto(code = "RAM", displayName = "RAM", variantCapable = true, suggestions = CategoryVariantPresets.ELECTRONICS_RAM_CHOICES))
        } else if (isShoes) {
            mutable.add(ShopCategoryAttributeDto(code = "COLOR", displayName = "Color", variantCapable = true, suggestions = listOf("Black", "White", "Brown", "Blue", "Grey", "Red")))
            val shoeSizes = if (id in CategoryVariantPresets.KIDS_SHOE_CATEGORY_IDS || code.contains("KIDS")) CategoryVariantPresets.KIDS_SHOE_SIZES else CategoryVariantPresets.SHOE_EU_SIZES
            mutable.add(ShopCategoryAttributeDto(code = "SIZE", displayName = "Shoe Size", variantCapable = true, suggestions = shoeSizes))
            mutable.add(ShopCategoryAttributeDto(code = "MATERIAL", displayName = "Upper Material", variantCapable = true, suggestions = CategoryVariantPresets.SHOE_UPPER_MATERIAL_CHOICES))
        } else if (isFurniture) {
            mutable.add(ShopCategoryAttributeDto(code = "COLOR", displayName = "Color", variantCapable = true, suggestions = listOf("Brown", "Black", "White", "Natural Wood", "Grey")))
            mutable.add(ShopCategoryAttributeDto(code = "DIMENSIONS", displayName = "Dimensions", variantCapable = true, suggestions = CategoryVariantPresets.FURNITURE_SIZES))
            mutable.add(ShopCategoryAttributeDto(code = "MATERIAL", displayName = "Material", variantCapable = true, suggestions = CategoryVariantPresets.FURNITURE_MATERIAL_CHOICES))
        } else {
            // General Apparel / Clothing
            mutable.add(ShopCategoryAttributeDto(code = "COLOR", displayName = "Color", variantCapable = true, suggestions = listOf("Black", "White", "Navy", "Grey", "Blue", "Red", "Green")))
            mutable.add(ShopCategoryAttributeDto(code = "SIZE", displayName = "Size", variantCapable = true, suggestions = CategoryVariantPresets.APPAREL_ALPHA_SIZES))
            mutable.add(ShopCategoryAttributeDto(code = "MATERIAL", displayName = "Material", variantCapable = true, suggestions = listOf("100% Cotton", "Cotton Blend", "Polyester", "Linen", "Denim")))
        }
    }

    if (isElectronics) {
        mutable.removeAll { it.code == "SIZE" || it.code == "CLOTHING_SIZE" }
    }

    val hasJeans = id in CategoryVariantPresets.JEANS_CATEGORY_IDS || code.contains("JEAN")
    val hasSuits = id in CategoryVariantPresets.SUIT_CATEGORY_IDS || code.contains("SUIT")
    val hasShirts = id in CategoryVariantPresets.SHIRT_CATEGORY_IDS || code.contains("SHIRT")

    if (hasJeans) {
        if (mutable.none { it.code == "WASH" }) {
            mutable.add(ShopCategoryAttributeDto(code = "WASH", displayName = "Wash", variantCapable = true, suggestions = CategoryVariantPresets.JEANS_WASH_CHOICES))
        }
        if (mutable.none { it.code == "FIT" }) {
            mutable.add(ShopCategoryAttributeDto(code = "FIT", displayName = "Fit", variantCapable = true, suggestions = CategoryVariantPresets.JEANS_FIT_CHOICES))
        }
        if (mutable.none { it.code == "INSEAM" }) {
            mutable.add(ShopCategoryAttributeDto(code = "INSEAM", displayName = "Inseam Length", variantCapable = true, suggestions = CategoryVariantPresets.JEANS_INSEAM_SIZES))
        }
    }

    if (hasSuits) {
        if (mutable.none { it.code == "FIT" }) {
            mutable.add(ShopCategoryAttributeDto(code = "FIT", displayName = "Fit", variantCapable = true, suggestions = CategoryVariantPresets.SUIT_FIT_CHOICES))
        }
        if (mutable.none { it.code == "PIECES" }) {
            mutable.add(ShopCategoryAttributeDto(code = "PIECES", displayName = "Suit Pieces", variantCapable = true, suggestions = CategoryVariantPresets.SUIT_PIECES_CHOICES))
        }
    }

    if (hasShirts) {
        if (mutable.none { it.code == "FABRIC" }) {
            mutable.add(ShopCategoryAttributeDto(code = "FABRIC", displayName = "Fabric", variantCapable = true, suggestions = CategoryVariantPresets.SHIRT_FABRIC_CHOICES))
        }
        if (mutable.none { it.code == "FIT" }) {
            mutable.add(ShopCategoryAttributeDto(code = "FIT", displayName = "Fit", variantCapable = true, suggestions = CategoryVariantPresets.SHIRT_FIT_CHOICES))
        }
    }

    if (isShoes) {
        val materialAttr = mutable.firstOrNull { it.code == "MATERIAL" || it.code == "UPPER_MATERIAL" }
        if (materialAttr == null) {
            mutable.add(ShopCategoryAttributeDto(code = "MATERIAL", displayName = "Upper Material", variantCapable = true, suggestions = CategoryVariantPresets.SHOE_UPPER_MATERIAL_CHOICES))
        }
    }

    return mutable.sortedWith(compareBy({ it.sortOrder }, { it.displayName }, { it.code }))
}
