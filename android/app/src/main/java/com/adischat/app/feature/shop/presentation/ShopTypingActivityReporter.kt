package com.adischat.app.feature.shop.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun ShopTypingActivityReporter(
    conversationId: String,
    hasText: Boolean,
    onTyping: (Boolean) -> Unit,
) {
    var typingReported by remember(conversationId) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(conversationId, hasText, lifecycleOwner) {
        if (!hasText) {
            if (typingReported) {
                typingReported = false
                onTyping(false)
            }
            return@LaunchedEffect
        }

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            typingReported = true
            try {
                while (true) {
                    onTyping(true)
                    delay(4_000)
                }
            } finally {
                if (typingReported) {
                    typingReported = false
                    onTyping(false)
                }
            }
        }
    }
}
