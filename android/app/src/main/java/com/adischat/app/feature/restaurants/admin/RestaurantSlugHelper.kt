package com.adischat.app.feature.restaurants.admin

import java.util.Locale

/**
 * Utility helper for restaurant slug generation, normalization, and validation.
 *
 * Backend contract enforces: `^[a-z0-9-]{3,50}$`
 */
object RestaurantSlugHelper {

    private val SLUG_REGEX = Regex("^[a-z0-9-]{3,50}$")

    /**
     * Prepopulates a valid restaurant URL slug from the restaurant brand name (or legal name).
     *
     * Handles non-Latin or Amharic-only strings by providing a valid Latin fallback
     * rather than producing an invalid or empty slug.
     */
    fun generateSlug(brandName: String?, legalName: String? = null): String {
        val raw = (brandName?.trim().takeUnless { it.isNullOrBlank() }
            ?: legalName?.trim().orEmpty())

        val lower = raw.lowercase(Locale.ROOT)

        // Convert non-alphanumeric chars (including Amharic, spaces, symbols) into hyphens
        val cleaned = lower
            .replace(Regex("[^a-z0-9-]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

        // If cleaned string has >= 3 chars, return it up to 50 chars
        if (cleaned.length >= 3) {
            val candidate = cleaned.take(50).trimEnd('-')
            if (candidate.length >= 3) return candidate
        }

        // For empty, symbols-only, or Amharic-only names without Latin characters:
        // Provide a meaningful Latin fallback
        return when {
            cleaned.isEmpty() -> "restaurant-app"
            cleaned.length == 1 -> "$cleaned-restaurant"
            cleaned.length == 2 -> "$cleaned-rest"
            else -> "restaurant-app"
        }.take(50).trimEnd('-')
    }

    /**
     * Returns true if [slug] strictly satisfies the backend slug contract: `^[a-z0-9-]{3,50}$`
     */
    fun isValidSlug(slug: String?): Boolean {
        if (slug.isNullOrBlank()) return false
        return SLUG_REGEX.matches(slug.trim())
    }

    /**
     * Returns a human-friendly validation error message, or null if the slug is valid.
     */
    fun getSlugValidationError(slug: String?): String? {
        val trimmed = slug?.trim().orEmpty()
        return when {
            trimmed.isEmpty() -> "Restaurant slug cannot be empty."
            trimmed.length < 3 -> "Slug must be at least 3 characters."
            trimmed.length > 50 -> "Slug cannot exceed 50 characters."
            trimmed.any { it.isUpperCase() } -> "Slug must be all lowercase."
            !trimmed.matches(Regex("^[a-z0-9-]+$")) -> "Only lowercase letters (a-z), numbers (0-9), and hyphens (-) are allowed."
            trimmed.startsWith("-") || trimmed.endsWith("-") -> "Slug cannot start or end with a hyphen."
            else -> null
        }
    }
}
