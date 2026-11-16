package com.adischat.app.feature.shop.data

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ShopSessionMemoryTest {
    @After fun tearDown() {
        ShopSessionMemory.finishSignOut()
    }

    @Test fun logoutBlocksLateRefreshAndAccountSwitchRestartsCleanly() {
        ShopSessionMemory.clear()
        val oldGeneration = ShopSessionMemory.generation()
        val oldSession = ShopSessionDto(accessToken = "old-access", refreshToken = "old-refresh")
        val oldProfile = ShopProfileDto(adisChatUserId = "account-a")
        assertTrue(ShopSessionMemory.updateIfCurrent(oldGeneration, oldSession, oldProfile))

        assertEquals(oldSession, ShopSessionMemory.beginSignOut())
        assertTrue(ShopSessionMemory.isSigningOut())
        assertFalse(ShopSessionMemory.updateIfCurrent(oldGeneration, ShopSessionDto(accessToken = "late"), oldProfile))
        assertFalse(ShopSessionMemory.updateProfileIfCurrent(oldGeneration, oldProfile))

        ShopSessionMemory.finishSignOut()
        assertNull(ShopSessionMemory.session())
        assertNull(ShopSessionMemory.profile())
        assertFalse(ShopSessionMemory.updateIfCurrent(oldGeneration, oldSession, oldProfile))

        val newGeneration = ShopSessionMemory.generation()
        val newProfile = ShopProfileDto(adisChatUserId = "account-b")
        assertTrue(ShopSessionMemory.updateIfCurrent(newGeneration, ShopSessionDto(accessToken = "new"), newProfile))
        assertEquals("account-b", ShopSessionMemory.profile()?.adisChatUserId)
    }

    @Test fun clearingForExchangeInvalidatesOlderResponse() {
        ShopSessionMemory.clear()
        val older = ShopSessionMemory.generation()
        ShopSessionMemory.clear()
        assertFalse(ShopSessionMemory.updateIfCurrent(older, ShopSessionDto(accessToken = "stale"), null))
        assertNull(ShopSessionMemory.session())
    }
}
