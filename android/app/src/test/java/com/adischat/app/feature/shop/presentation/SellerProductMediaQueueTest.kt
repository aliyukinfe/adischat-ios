package com.adischat.app.feature.shop.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class SellerProductMediaQueueTest {
    @Test fun acceptsOneThreeAndFiveDistinctImages() {
        for (count in listOf(1, 3, 5)) {
            val selected = (1..count).map(Int::toString)
            assertEquals(selected, eligibleSellerMediaSelection(selected, 0))
        }
    }

    @Test fun deduplicatesAndRespectsRemainingSlots() {
        assertEquals(listOf("a", "b"), eligibleSellerMediaSelection(listOf("a", "a", "b", "c"), 8))
        assertEquals(emptyList<String>(), eligibleSellerMediaSelection(listOf("a"), 10))
        assertEquals(emptyList<String>(), eligibleSellerMediaSelection(listOf("a"), 11))
    }
}
