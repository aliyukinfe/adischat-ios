package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopProductDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuyerSearchPagingTest {
    private val key = BuyerSearchKey("laptop", null, BuyerSearchSort.Newest, false)

    @Test fun pagesBeyondOneHundredAndStopsOnPartialPage() {
        var state = BuyerSearchState(key = key, loading = true)
        repeat(5) { page ->
            state = appendBuyerSearchPage(state, (0 until 24).map { product("${page * 24 + it}") })
            assertEquals((page + 1) * 24, state.nextOffset)
            assertTrue(canLoadNextBuyerSearchPage(state))
        }
        state = appendBuyerSearchPage(state, listOf(product("120"), product("121")))
        assertEquals(122, state.products.size)
        assertEquals(122, state.nextOffset)
        assertFalse(canLoadNextBuyerSearchPage(state))
    }

    @Test fun duplicateProductsDoNotChangeServerOffset() {
        var state = appendBuyerSearchPage(BuyerSearchState(key = key), (0 until 24).map { product("$it") })
        state = appendBuyerSearchPage(state, listOf(product("23"), product("24")))
        assertEquals(25, state.products.size)
        assertEquals(26, state.nextOffset)
        assertFalse(state.hasMore)
    }

    @Test fun emptyPageEndsSearchHonestly() {
        val state = appendBuyerSearchPage(BuyerSearchState(key = key), emptyList())
        assertTrue(state.loaded)
        assertFalse(state.hasMore)
        assertEquals("Showing 0 products", buyerSearchCountLabel(state.products.size))
    }

    @Test fun rapidQueryOrFilterChangeInvalidatesOldResponse() {
        val changed = key.copy(term = "phone")
        assertFalse(isCurrentBuyerSearchResponse(key, changed, 1, 1))
        assertFalse(isCurrentBuyerSearchResponse(key, key, 1, 2))
        assertFalse(isCurrentBuyerSearchResponse(key, key.copy(inStockOnly = true), 2, 2))
        assertTrue(isCurrentBuyerSearchResponse(changed, changed, 2, 2))
    }

    @Test fun pendingAndErrorStatesGateRequests() {
        val loading = BuyerSearchState(key = key, loading = true)
        assertFalse(canLoadNextBuyerSearchPage(loading))
        assertFalse(canRetryBuyerSearch(loading))
        val failed = BuyerSearchState(key = key, error = "offline")
        assertTrue(canRetryBuyerSearch(failed))
        assertFalse(canLoadNextBuyerSearchPage(failed))
        val pageFailed = failed.copy(loaded = true, nextOffset = 24, products = listOf(product("1")))
        assertTrue(canRetryBuyerSearch(pageFailed))
        assertEquals(24, pageFailed.nextOffset)
    }

    @Test fun countAndSortDoNotClaimUnverifiedTotals() {
        assertEquals("Showing 1 product", buyerSearchCountLabel(1))
        assertEquals("Showing 24 products", buyerSearchCountLabel(24))
        assertEquals("created_at.desc,id.desc", BuyerSearchSort.Newest.apiOrder)
        assertEquals("price_minor.asc,id.asc", BuyerSearchSort.PriceLow.apiOrder)
    }

    @Test fun inputNormalizationSupportsAmharicAndRemovesFilterPunctuation() {
        assertEquals("laptop 5G", normalizedBuyerSearchTerm("  laptop, 5G  "))
        assertEquals("ሳምሰንግ", normalizedBuyerSearchTerm("ሳምሰንግ"))
    }

    private fun product(id: String) = ShopProductDto(id = id)
}
