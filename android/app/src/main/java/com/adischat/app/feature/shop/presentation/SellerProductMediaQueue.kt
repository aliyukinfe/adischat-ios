package com.adischat.app.feature.shop.presentation

internal fun <T> eligibleSellerMediaSelection(selected: List<T>, uploadedCount: Int): List<T> =
    selected.distinct().take((10 - uploadedCount).coerceAtLeast(0))
