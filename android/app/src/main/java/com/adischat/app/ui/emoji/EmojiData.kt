package com.adischat.app.ui.emoji

import android.content.Context

/**
 * Emoji categories shown in the picker's horizontal slider (Telegram/WhatsApp style).
 * `icon` is the tab glyph. Recent/Favorites/Habesha are special (dynamic / custom pack).
 */
enum class EmojiCategory(val title: String, val icon: String) {
    Recent("Recent", "-"),
    Favorites("Favorites", "-"),
    Smileys("Smileys", "-"),
    Animals("Animals & Nature", "-"),
    Food("Food & Drink", "-"),
    Activities("Activities", "-"),
    Travel("Travel & Places", "-"),
    Objects("Objects", "-"),
    Symbols("Symbols", "-"),
    Flags("Flags", "-"),
    Habesha("Habesha", "-"),
}

/**
 * Curated, categorized Unicode emoji source. Kept intentionally broad-but-curated so the
 * picker stays instant on low-end devices; extend any list freely (and future Unicode
 * updates just append). Skin-tone base emojis get tones applied at render time.
 */
object EmojiData {

    data class GridItem(val key: String, val emoji: String)

    fun gridItems(emojis: List<String>): List<GridItem> {
        val occurrences = HashMap<String, Int>()
        return emojis.map { emoji ->
            val occurrence = occurrences[emoji] ?: 0
            occurrences[emoji] = occurrence + 1
            GridItem("${emoji.length}:$emoji:$occurrence", emoji)
        }
    }

    val smileys = listOf(
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","- ","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","- ","-","-","-","-","-","-","-",
        "-","- ï¸","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
    )

    // Hands/people that support skin tones (applied dynamically).
    val people = listOf(
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","- ","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-",
    )

    val animals = listOf(
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","- ","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-",
    )

    val food = listOf(
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","- ","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","- ","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
    )

    val activities = listOf(
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-",
    )

    val travel = listOf(
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","- ","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","- ","-","-","-","-","-","-","-","-",
        "-","-","-","- ","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
    )

    val objects = listOf(
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","- ","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","- ","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","- ","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
    )

    val symbols = listOf(
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","- ï¸","-","-","-","-","-","-","-","-","-","-","- ",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","0ï¸-","1ï¸-","2ï¸-","3ï¸-","4ï¸-","5ï¸-","6ï¸-","7ï¸-","8ï¸-","9ï¸-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","- ","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","- ï¸","-","-","-","-","-","-","-","-",
    )

    val flags = listOf(
        "-","-","-","-","-","-","- ï¸","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
        "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
    )

    fun categoryEmojis(category: EmojiCategory): List<String> = when (category) {
        EmojiCategory.Smileys    -> smileys + people
        EmojiCategory.Animals    -> animals
        EmojiCategory.Food -> food
        EmojiCategory.Activities -> activities
        EmojiCategory.Travel     -> travel
        EmojiCategory.Objects    -> objects
        EmojiCategory.Symbols    -> symbols
        EmojiCategory.Flags      -> flags
        else -> emptyList()   // Recent / Favorites / Habesha handled separately
    }

    /** The default reaction strip:      (+ opens full picker). */
    val quickReactions = listOf("-", "-", "-", "-", "-", "-")

    // - Lightweight keyword search -
    private val keywords: Map<String, String> = mapOf(
        "-" to "happy smile grin", "-" to "happy smile", "-" to "happy laugh", "-" to "grin beam",
        "-" to "laugh tears joy lol", "-" to "rofl laugh lol", "-" to "sweat laugh", "-" to "smile",
        "-" to "blush happy smile", "-" to "love heart eyes", "-" to "love smiling hearts", "-" to "kiss love",
        "-" to "cool sunglasses", "-" to "thinking", "-" to "sad cry", "-" to "cry sob sad", "-" to "angry mad",
        "- " to "angry mad", "-" to "thumbs up like yes good", "-" to "thumbs down dislike no",
        "-" to "pray thanks please", "-" to "clap applause", "-" to "muscle strong", "-" to "fire lit hot",
        "-" to "heart love red", "-" to "broken heart", "-" to "sparkle shine", "-" to "party celebrate tada",
        "-" to "cake birthday", "-" to "dog puppy", "-" to "cat kitten", "-" to "lion", "-" to "bear",
        "-" to "burger food", "-" to "pizza food", "-" to "fries food", "-" to "coffee buna drink",
        "-" to "beer drink", "-" to "car auto travel", "-" to "plane flight travel", "-" to "rocket space",
        "- " to "house home", "-" to "soccer football ball", "-" to "basketball", "-" to "music note",
        "-" to "phone mobile", "-" to "laptop computer", "-" to "idea light bulb", "-" to "ethiopia habesha flag",
    )

    fun search(query: String): List<String> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        val all = smileys + people + animals + food + activities + travel + objects + symbols + flags
        // Match by keyword first, then keep any emoji whose keywords contain the query.
        return all.filter { e -> keywords[e]?.contains(q) == true }
        .ifEmpty { all.filter { e -> (keywords[e] ?: "").split(" ").any { it.startsWith(q) } } }
        .distinct()
    }
}

// Hand/gesture emojis that actually support Fitzpatrick skin tones.
private val ToneableEmojis = setOf(
    "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
    "-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-","-",
)

/** Apply a skin tone (0 = default/none, 1..5 = light-) to a base emoji that supports it. */
fun applySkinTone(base: String, tone: Int): String {
    if (tone <= 0 || base !in ToneableEmojis) return base
    val modifier = when (tone) { 1 -> "-"; 2 -> "-"; 3 -> "-"; 4 -> "-"; else -> "-" }
    return base + modifier
}

fun supportsSkinTone(base: String): Boolean = base in ToneableEmojis

/** Persists recent emojis, favorites, and the chosen skin tone across restarts. */
class EmojiStore(context: Context) {
    private val prefs = context.getSharedPreferences("adis_emoji_prefs", Context.MODE_PRIVATE)

    fun recents(): List<String> = prefs.getString("recent", "").orEmpty().split("").filter { it.isNotBlank() }
    fun addRecent(emoji: String) {
        val updated = (listOf(emoji) + recents().filter { it != emoji }).take(40)
        prefs.edit().putString("recent", updated.joinToString("")).apply()
    }

    fun favorites(): List<String> = prefs.getString("favorites", "").orEmpty().split("").filter { it.isNotBlank() }
    fun isFavorite(emoji: String) = favorites().contains(emoji)
    fun toggleFavorite(emoji: String) {
        val cur = favorites()
        val updated = if (emoji in cur) cur - emoji else (listOf(emoji) + cur).take(60)
        prefs.edit().putString("favorites", updated.joinToString("")).apply()
    }

    fun skinTone(): Int = prefs.getInt("skin_tone", 0)
    fun setSkinTone(tone: Int) { prefs.edit().putInt("skin_tone", tone).apply() }
}
