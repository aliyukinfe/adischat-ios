package com.adischat.app.feature.shop.data

class ShopRepository(private val delegate: ShopHomeRepository = ShopHomeRepository()) {
    suspend fun loadHome(): ShopHomeState = delegate.loadHome()
}
