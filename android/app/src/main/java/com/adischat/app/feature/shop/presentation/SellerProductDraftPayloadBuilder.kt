package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopSellerProductDraftPayload

internal data class SellerProductFormValues(
    val title: String,
    val brand: String,
    val categoryId: String,
    val sellingUnitCode: String,
    val minimumOrderQuantity: String,
    val productTypeDescription: String,
    val shortDescription: String,
    val description: String,
    val price: String,
    val stock: String,
    val shipping: String,
    val returns: String,
    val specs: String,
    val variantOutputsJson: String,
    val imageIds: Set<String>,
    val discountPercent: String = "",
    val compareAtPrice: String = "",
)

internal fun computeDiscountedPrice(regularPriceEtb: Int, discountPercent: Int): Int {
    if (discountPercent !in 1..90 || regularPriceEtb <= 0) return regularPriceEtb
    return ((regularPriceEtb * (100 - discountPercent) + 50) / 100).coerceAtLeast(1)
}

internal fun computeSavingsEtb(regularPriceEtb: Int, discountPercent: Int): Int {
    if (discountPercent !in 1..90 || regularPriceEtb <= 0) return 0
    return regularPriceEtb - computeDiscountedPrice(regularPriceEtb, discountPercent)
}

internal fun computeDiscountPercent(regularPriceEtb: Int, promotionalPriceEtb: Int): Int? {
    if (regularPriceEtb <= promotionalPriceEtb || regularPriceEtb <= 0) return null
    return Math.round((regularPriceEtb - promotionalPriceEtb) * 100.0 / regularPriceEtb).toInt()
}

internal data class SellerProductValidationErrors(
    val fieldErrors: Map<String, String> = emptyMap(),
    val summaryMessages: List<String> = emptyList(),
) {
    val isValid: Boolean get() = fieldErrors.isEmpty()
}

internal fun validateSellerProductForm(
    values: SellerProductFormValues,
    uploadedImagesCount: Int,
    duplicateSkus: Set<String>,
    specificationErrors: List<String>,
): SellerProductValidationErrors {
    val errors = mutableMapOf<String, String>()
    val messages = mutableListOf<String>()

    if (values.title.trim().isBlank()) {
        errors["title"] = "Product title is required."
        messages.add("Product title is missing.")
    } else if (values.title.trim().length < 3) {
        errors["title"] = "Product title must be at least 3 characters."
        messages.add("Product title is too short.")
    }

    if (values.categoryId.trim().isBlank()) {
        errors["category"] = "Product category must be selected."
        messages.add("Product category is not selected.")
    }

    if (values.sellingUnitCode.trim().isBlank()) {
        errors["sellingUnit"] = "Selling unit must be selected."
        messages.add("Selling unit is missing.")
    }

    if (values.description.trim().isBlank()) {
        errors["description"] = "Full product description is required."
        messages.add("Product description is missing.")
    } else if (values.description.trim().length < 10) {
        errors["description"] = "Product description must be at least 10 characters."
        messages.add("Product description is too brief.")
    }

    if (uploadedImagesCount < 1) {
        errors["images"] = "At least 1 product image must be uploaded before submitting."
        messages.add("No product images uploaded.")
    }

    val price = values.price.toIntOrNull()
    if (price == null || price <= 0) {
        errors["price"] = "Valid base price in ETB is required (greater than 0)."
        messages.add("Base price must be greater than 0 ETB.")
    }

    val stock = values.stock.toIntOrNull()
    if (stock == null || stock < 0) {
        errors["stock"] = "Stock quantity must be 0 or more."
        messages.add("Stock quantity cannot be negative.")
    }

    val discountPct = values.discountPercent.toIntOrNull()
    if (values.discountPercent.isNotBlank() && (discountPct == null || discountPct !in 1..90)) {
        errors["discount"] = "Discount must be between 1% and 90%."
        messages.add("Invalid discount percentage (1–90%).")
    }

    if (duplicateSkus.isNotEmpty()) {
        errors["variants"] = "Duplicate SKU detected: ${duplicateSkus.joinToString(", ")}. Each variant must have a unique SKU."
        messages.add("Duplicate variant SKUs detected.")
    }

    if (specificationErrors.isNotEmpty()) {
        errors["specs"] = specificationErrors.first()
        messages.addAll(specificationErrors)
    }

    return SellerProductValidationErrors(errors, messages)
}

internal fun sellerProductDraftPayload(values: SellerProductFormValues): ShopSellerProductDraftPayload {
    val variantOutput = decodeSellerVariantOutputs(values.variantOutputsJson)
    val rawPrice = values.price.toIntOrNull() ?: 0
    val discountPct = values.discountPercent.toIntOrNull()?.takeIf { it in 1..90 } ?: 0
    val (finalPriceMinor, compareAtMinor) = if (discountPct > 0) {
        val promoPrice = computeDiscountedPrice(rawPrice, discountPct)
        (promoPrice * 100) to (rawPrice * 100)
    } else {
        val manualCompareAt = values.compareAtPrice.toIntOrNull()?.takeIf { it > rawPrice }
        (rawPrice * 100) to (manualCompareAt?.times(100))
    }

    val baseStock = values.stock.toIntOrNull() ?: 0
    val variants = syncSingleDefaultVariant(
        options = variantOutput.options,
        variants = removeUnavailableVariantImages(variantOutput.variants, values.imageIds),
        basePriceMinor = finalPriceMinor,
        baseStock = baseStock,
        compareAtPriceMinor = compareAtMinor,
    )
    val manualSpecs = values.specs.lines().mapNotNull { line ->
        val parts = line.split(":", limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim() to parts[1].trim() else null
    }.toMap()

    return ShopSellerProductDraftPayload(
        title = values.title.trim().ifBlank { "Untitled product" },
        brand = values.brand.trim().takeIf(String::isNotBlank),
        categoryId = values.categoryId.takeIf(String::isNotBlank),
        sellingUnitCode = values.sellingUnitCode,
        minimumOrderQuantity = values.minimumOrderQuantity.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        productTypeDescription = values.productTypeDescription.trim().takeIf(String::isNotBlank),
        shortDescription = values.shortDescription.trim(),
        description = values.description.trim(),
        priceMinor = finalPriceMinor,
        compareAtPriceMinor = compareAtMinor,
        stockQuantity = if (variants.isNotEmpty()) variants.filter { it.status == "active" }.sumOf { it.quantityAvailable } else baseStock,
        images = null,
        shippingDetails = mapOf("notes" to values.shipping.trim()),
        returnPolicy = mapOf("notes" to values.returns.trim()),
        specifications = manualSpecs,
        specificationValues = variantOutput.specifications.takeIf { it.isNotEmpty() },
        productOptions = variantOutput.options,
        variantCatalog = variants,
    )
}
