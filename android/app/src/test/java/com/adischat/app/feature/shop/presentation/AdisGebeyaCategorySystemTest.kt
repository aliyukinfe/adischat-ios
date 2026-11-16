package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopCategoryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdisGebeyaCategorySystemTest {
    private val root = ShopCategoryDto(id = "root", name = "Crafts, Sewing & Accessories", slug = "crafts", isLeaf = false, sellerSelectable = false)
    private val parent = ShopCategoryDto(id = "parent", parentId = root.id, name = "Garment Accessories", slug = "garment-accessories", level = 1, isLeaf = false, sellerSelectable = false)
    private val rhinestone = ShopCategoryDto(
        id = "leaf",
        parentId = parent.id,
        name = "Rhinestones & Decorative Stones",
        slug = "rhinestones-decorative-stones",
        level = 2,
        searchTerms = listOf("rhinestone", "garment stone", "crystal stone"),
    )

    @Test
    fun breadcrumbBuildsCompleteHierarchy() {
        assertEquals(
            "Crafts, Sewing & Accessories > Garment Accessories > Rhinestones & Decorative Stones",
            categoryBreadcrumb(rhinestone, listOf(root, parent, rhinestone)),
        )
    }

    @Test
    fun searchUsesSafeCategorySynonyms() {
        assertTrue(categoryMatchesQuery(rhinestone, "rhinestone"))
        assertTrue(categoryMatchesQuery(rhinestone, "crystal stone"))
        assertFalse(categoryMatchesQuery(rhinestone, "groceries"))
    }

    @Test
    fun onlyLeafIsSellerSelectableInFixture() {
        assertFalse(root.sellerSelectable)
        assertFalse(parent.sellerSelectable)
        assertTrue(rhinestone.sellerSelectable)
        assertTrue(rhinestone.isLeaf)
    }
}

