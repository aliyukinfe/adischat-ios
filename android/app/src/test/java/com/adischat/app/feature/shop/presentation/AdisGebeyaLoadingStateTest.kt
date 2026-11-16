package com.adischat.app.feature.shop.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdisGebeyaLoadingStateTest {
    @Test
    fun buyerErrorDoesNotRenderAsLoading() {
        assertFalse(shouldShowBuyerInitialLoader(initialPhaseComplete = false, hasError = true))
    }

    @Test
    fun sellerErrorDoesNotRenderAsLoading() {
        assertFalse(shouldShowSellerInitialLoader(initialPhaseComplete = false, hasError = true))
    }

    @Test
    fun sellerResolutionIsLoadingBeforeCurrentAccountReturns() {
        assertTrue(
            isSellerIdentityResolving(
                resolutionStarted = true,
                loading = true,
                sellerStatus = "not_started",
                hasApplication = false,
                hasError = false,
            ),
        )
    }

    @Test
    fun resolvedSignedOutOrNonSellerStateIsNotLoading() {
        assertFalse(
            isSellerIdentityResolving(
                resolutionStarted = true,
                loading = false,
                sellerStatus = "not_started",
                hasApplication = false,
                hasError = false,
            ),
        )
    }
}
