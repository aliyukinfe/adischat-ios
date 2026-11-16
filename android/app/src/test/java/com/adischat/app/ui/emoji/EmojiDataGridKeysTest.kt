package com.adischat.app.ui.emoji

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiDataGridKeysTest {
    @Test fun repeatedEmojiValuesReceiveUniqueStableKeys() {
        val values = listOf("-", "-", "a", "-", "a")
        val items = EmojiData.gridItems(values)

        assertEquals(values, items.map { it.emoji })
        assertEquals(values.size, items.map { it.key }.distinct().size)
        assertEquals(items, EmojiData.gridItems(values))
        assertEquals(items.first().key, EmojiData.gridItems(listOf("-", "a")).first().key)
    }

    @Test fun existingEmojiCategoryDoesNotReuseGridKeys() {
        val items = EmojiData.gridItems(EmojiData.categoryEmojis(EmojiCategory.Smileys))
        assertEquals(items.size, items.map { it.key }.distinct().size)
    }
}
