package com.adischat.app.feature.shop.presentation

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopMessageRefreshPolicyTest {
    @Test
    fun intervalRemainsTenSeconds() {
        assertEquals(10_000L, ShopMessageRefreshPolicy.INTERVAL_MS)
    }

    @Test
    fun refreshRunsOnlyForVisibleStartedConversation() {
        assertTrue(ShopMessageRefreshPolicy.isActive(Lifecycle.State.STARTED, conversationVisible = true))
        assertTrue(ShopMessageRefreshPolicy.isActive(Lifecycle.State.RESUMED, conversationVisible = true))
        assertFalse(ShopMessageRefreshPolicy.isActive(Lifecycle.State.CREATED, conversationVisible = true))
        assertFalse(ShopMessageRefreshPolicy.isActive(Lifecycle.State.STARTED, conversationVisible = false))
    }
}
