package com.adischat.app.feature.shop.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShopAdminAllProductsQueryTest {
    @Test fun normalizesSearchAndPagination() {
        val query = ShopAdminProductQuery(search = "  phone  ", status = "all", limit = 200, offset = -5).normalized()
        assertEquals("phone", query.search)
        assertEquals("all", query.status)
        assertEquals(100, query.limit)
        assertEquals(0, query.offset)
    }

    @Test fun rejectsUnsupportedStatus() {
        assertEquals("all", ShopAdminProductQuery(status = "archived;drop").normalized().status)
        assertEquals("approved", ShopAdminProductQuery(status = "approved").normalized().status)
        assertEquals("archived", ShopAdminProductQuery(status = "archived").normalized().status)
    }

    @Test fun paginationUsesServerCountWhenAvailable() {
        val page = ShopAdminProductPage(products = listOf(ShopAdminProductDto(id = "one")), total = 1, limit = 1)
        assertEquals(false, page.hasNext)
        assertEquals(true, page.copy(total = null).hasNext)
    }
}
