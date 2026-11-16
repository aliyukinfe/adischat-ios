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
)

internal fun sellerProductDraftPayload(values: SellerProductFormValues): ShopSellerProductDraftPayload {
    val variantOutput = decodeSellerVariantOutputs(values.variantOutputsJson)
    val basePriceMinor = (values.price.toIntOrNull() ?: 0) * 100
    val baseStock = values.stock.toIntOrNull() ?: 0
    val variants = syncSingleDefaultVariant(
        variantOutput.options,
        removeUnavailableVariantImages(variantOutput.variants, values.imageIds),
        basePriceMinor,
        baseStock,
    )
    val manualSpecs = values.specs.lines().mapNotNull { line ->
        val parts = line.split(":", limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim() to parts[1].trim() else null
    }.toMap()
    return ShopSellerProductDraftPayload(
        title = values.title.trim(),
        brand = values.brand.trim().takeIf(String::isNotBlank),
        categoryId = values.categoryId.takeIf(String::isNotBlank),
        sellingUnitCode = values.sellingUnitCode,
        minimumOrderQuantity = values.minimumOrderQuantity.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        productTypeDescription = values.productTypeDescription.trim().takeIf(String::isNotBlank),
        shortDescription = values.shortDescription.trim(),
        description = values.description.trim(),
        priceMinor = basePriceMinor,
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
