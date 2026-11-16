package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopProductDto

internal fun productForDetailRoute(
    listProduct: ShopProductDto,
    details: Map<String, ShopProductDto>,
): ShopProductDto = details[listProduct.id] ?: listProduct

internal fun productDetailWithCatalogFallback(detail: ShopProductDto, catalog: ShopProductDto): ShopProductDto =
    detail.copy(
        images = detail.images ?: catalog.images,
        store = detail.store ?: catalog.store,
        averageRating = detail.averageRating ?: catalog.averageRating,
        reviewCount = detail.reviewCount ?: catalog.reviewCount,
        soldCount = detail.soldCount ?: catalog.soldCount,
    )

internal fun isCurrentDetailResponse(requestGeneration: Long, currentGeneration: Long): Boolean =
    requestGeneration == currentGeneration

internal fun storeApprovalDescription(approvalStatus: String?): String? =
    if (approvalStatus.equals("approved", ignoreCase = true)) "Approved marketplace store" else null
