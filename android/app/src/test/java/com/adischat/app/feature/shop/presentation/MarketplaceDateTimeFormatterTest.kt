package com.adischat.app.feature.shop.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class MarketplaceDateTimeFormatterTest {
    private val addis = ZoneId.of("Africa/Addis_Ababa")
    private val london = ZoneId.of("Europe/London")
    private val locale = Locale.US

    @Test
    fun `UTC timestamp uses device timezone without seconds or suffix`() {
        val source = "2026-08-10T12:35:00Z"
        val formatted = formatMarketplaceDateTime(source, zoneId = addis, locale = locale)

        assertEquals("Aug 10, 2026 \u2022 3:35 PM", formatted)
        assertFalse(formatted.contains(":00"))
        assertFalse(formatted.contains("UTC"))
        assertFalse(formatted.contains("+03"))
        assertFalse(formatted.contains("Z"))
    }

    @Test
    fun `same server time changes with phone timezone`() {
        val source = "2026-08-10T12:35:00Z"

        assertEquals("Aug 10, 2026 \u2022 1:35 PM", formatMarketplaceDateTime(source, zoneId = london, locale = locale))
        assertEquals("Aug 10, 2026 \u2022 3:35 PM", formatMarketplaceDateTime(source, zoneId = addis, locale = locale))
    }

    @Test
    fun `relative and separator labels stay compact`() {
        val now = Instant.parse("2026-08-10T15:00:00Z")

        assertEquals("5m ago", formatMarketplaceRelativeTime("2026-08-10T14:55:00Z", now = now, zoneId = addis, locale = locale))
        assertEquals("Today", formatMarketplaceDateSeparator("2026-08-10T08:00:00Z", now, addis, locale))
        assertEquals("Yesterday", formatMarketplaceDateSeparator("2026-08-09T08:00:00Z", now, addis, locale))
    }

    @Test
    fun `invalid timestamp uses safe fallback`() {
        assertEquals("Date unavailable", formatMarketplaceDateTime("not-a-date", zoneId = addis, locale = locale))
        assertNull(marketplaceLocalDate("not-a-date", addis))
    }
}
