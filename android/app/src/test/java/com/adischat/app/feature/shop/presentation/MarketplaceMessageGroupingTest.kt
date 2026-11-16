package com.adischat.app.feature.shop.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceMessageGroupingTest {
    @Test
    fun firstMessageStartsADateGroup() {
        assertTrue(shouldShowMarketplaceDateSeparator(null, "2026-08-10T08:00:00Z"))
    }

    @Test
    fun messagesOnSameLocalDayShareAGroup() {
        assertFalse(
            shouldShowMarketplaceDateSeparator(
                "2026-08-10T08:00:00Z",
                "2026-08-10T10:30:00Z",
            ),
        )
    }

    @Test
    fun messagesOnDifferentDaysStartANewGroup() {
        assertTrue(
            shouldShowMarketplaceDateSeparator(
                "2026-08-10T08:00:00Z",
                "2026-08-11T08:00:00Z",
            ),
        )
    }
}
