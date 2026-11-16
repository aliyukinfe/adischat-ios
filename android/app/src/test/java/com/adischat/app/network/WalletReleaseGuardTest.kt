package com.adischat.app.network

import com.adischat.app.FeatureFlags
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletReleaseGuardTest {
    private fun blocked(method: String, path: String): Boolean =
        WalletReleaseGuard.blocks(method, "https://example.invalid$path".toHttpUrl().pathSegments)

    @Test fun walletReleaseIsDisabled() {
        assertFalse(FeatureFlags.FEATURE_WALLET_ENABLED)
        assertTrue(blocked("POST", "/api/wallet/deposit"))
        assertTrue(blocked("POST", "/api/wallet/change-pin"))
        assertTrue(blocked("GET", "/api/wallet/me"))
        assertTrue(blocked("POST", "/api/admin/wallets/user/adjust"))
        assertTrue(blocked("GET", "/api/merchant/pay-code"))
    }

    @Test fun walletBackedGameMutationsAreBlockedButReadsRemain() {
        assertTrue(blocked("POST", "/api/games/game/buy"))
        assertTrue(blocked("POST", "/api/games/game/draw"))
        assertFalse(blocked("GET", "/api/games/game/detail"))
    }

    @Test fun chatAndShopRoutesRemainAvailable() {
        assertFalse(blocked("POST", "/api/messages/send"))
        assertFalse(blocked("GET", "/api/health"))
        assertFalse(blocked("POST", "/api/shop/cart"))
    }
}
