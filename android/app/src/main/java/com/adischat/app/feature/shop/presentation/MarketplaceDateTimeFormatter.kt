package com.adischat.app.feature.shop.presentation

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private fun marketplaceInstant(value: String?): Instant? {
    val clean = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val normalized = if ('T' !in clean && ' ' in clean) clean.replaceFirst(' ', 'T') else clean
    return sequenceOf<(String) -> Instant?>(
        { runCatching { Instant.parse(it) }.getOrNull() },
        { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() },
        { runCatching { ZonedDateTime.parse(it).toInstant() }.getOrNull() },
        { runCatching { LocalDateTime.parse(it).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull() },
    ).firstNotNullOfOrNull { parser -> parser(normalized) }
}

internal fun formatMarketplaceDateTime(
    value: String?,
    fallback: String = "Date unavailable",
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val instant = marketplaceInstant(value) ?: return fallback
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy \u2022 h:mm a", locale)
    return formatter.format(instant.atZone(zoneId))
}

internal fun formatMarketplaceDate(
    value: String?,
    fallback: String = "Date unavailable",
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val instant = marketplaceInstant(value) ?: return fallback
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(instant.atZone(zoneId))
}

internal fun formatMarketplaceTime(
    value: String?,
    fallback: String = "",
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val instant = marketplaceInstant(value) ?: return fallback
    return DateTimeFormatter.ofPattern("h:mm a", locale).format(instant.atZone(zoneId))
}

internal fun formatMarketplaceRelativeTime(
    value: String?,
    fallback: String = "",
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val instant = marketplaceInstant(value) ?: return fallback
    val elapsed = Duration.between(instant, now).coerceAtLeast(Duration.ZERO)
    val localDate = instant.atZone(zoneId).toLocalDate()
    val today = now.atZone(zoneId).toLocalDate()
    return when {
        elapsed < Duration.ofMinutes(1) -> "Just now"
        elapsed < Duration.ofHours(1) -> "${elapsed.toMinutes()}m ago"
        localDate == today -> formatMarketplaceTime(value, fallback, zoneId, locale)
        localDate == today.minusDays(1) -> "Yesterday"
        localDate.year == today.year -> DateTimeFormatter.ofPattern("MMM d", locale).format(localDate)
        else -> DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(localDate)
    }
}

internal fun formatMarketplaceDateSeparator(
    value: String?,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val instant = marketplaceInstant(value) ?: return ""
    val date = instant.atZone(zoneId).toLocalDate()
    val today = now.atZone(zoneId).toLocalDate()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(date)
    }
}

internal fun marketplaceLocalDate(
    value: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate? = marketplaceInstant(value)?.atZone(zoneId)?.toLocalDate()
