package com.adischat.app.feature.shop.presentation

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SellerResponseCountdownStateTest {
    @Test
    fun moreThanOneHourUsesHoursAndMinutes() {
        val state = sellerResponseCountdownState((23 * 60L + 42L) * 60L * 1_000L)

        assertEquals("23h 42m remaining", state.label)
        assertEquals(SellerResponseUrgency.NORMAL, state.urgency)
        assertTrue(state.progress in 0f..1f)
    }

    @Test
    fun underOneHourUsesMinutesAndSeconds() {
        val state = sellerResponseCountdownState((42L * 60L + 18L) * 1_000L)

        assertEquals("42m 18s remaining", state.label)
        assertEquals(SellerResponseUrgency.CRITICAL, state.urgency)
    }

    @Test
    fun elapsedDeadlineNeverShowsNegativeTime() {
        val state = sellerResponseCountdownState(-5_000L)

        assertEquals("Checking order status...", state.label)
        assertEquals(0L, state.remainingMillis)
        assertEquals(0f, state.progress)
        assertEquals(SellerResponseUrgency.EXPIRED, state.urgency)
    }

    @Test
    fun serverOffsetUsesResponseReceiptTimeInsteadOfRecompositionTime() {
        val serverNow = "2026-08-10T11:15:00Z"
        val receivedAt = Instant.parse("2026-08-10T11:14:55Z").toEpochMilli()

        assertEquals(5_000L, sellerResponseServerOffsetMillis(serverNow, receivedAt))
        assertEquals(0L, sellerResponseServerOffsetMillis(serverNow, null))
    }
}
