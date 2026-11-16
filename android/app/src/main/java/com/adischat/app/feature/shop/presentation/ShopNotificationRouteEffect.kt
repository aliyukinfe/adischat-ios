package com.adischat.app.feature.shop.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun ShopNotificationRouteEffect(
    conversationId: String?,
    onOpen: (ShopRoute) -> Unit,
    onConsumed: () -> Unit,
) {
    LaunchedEffect(conversationId) {
        val cleanId = conversationId.orEmpty().trim()
        if (cleanId.isNotEmpty()) {
            onOpen(ShopRoute.MessageConversation(cleanId))
            onConsumed()
        }
    }
}
