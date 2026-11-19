package com.adischat.app.feature.shop.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Watch
import com.adischat.app.feature.shop.data.ShopCategoryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShopCategoryIconsTest {
    @Test fun mapsAllKnownBuyerVisibleLeafSlugs() {
        assertEquals(219, categoryLeafIcons.size)
    }

    @Test fun fashionAndFootwearChildrenUseDistinctSemanticIcons() {
        val expected = mapOf(
            "men-sneakers" to Icons.Default.DirectionsRun,
            "men-boots" to Icons.Default.Hiking,
            "men-formal-shoes" to Icons.Default.Style,
            "men-suits" to Icons.Default.DryCleaning,
            "men-jeans" to Icons.Default.Texture,
            "women-dresses" to Icons.Default.Celebration,
            "women-heels" to Icons.Default.Diamond,
            "men-sportswear" to Icons.Default.FitnessCenter,
            "men-jackets" to Icons.Default.AcUnit,
            "men-traditional" to Icons.Default.AutoAwesome,
        )
        expected.forEach { (slug, icon) -> assertEquals(icon, categoryIcon(ShopCategoryDto(slug = slug))) }
    }

    @Test fun electronicsChildrenUseDistinctSemanticIcons() {
        val expected = mapOf(
            "mobile-phones" to Icons.Default.PhoneAndroid,
            "laptops" to Icons.Default.Laptop,
            "tablets" to Icons.Default.Tablet,
            "earphones-headphones" to Icons.Default.Headphones,
            "cameras" to Icons.Default.CameraAlt,
            "tvs" to Icons.Default.Tv,
            "smart-watches" to Icons.Default.Watch,
        )
        expected.forEach { (slug, icon) -> assertEquals(icon, categoryIcon(ShopCategoryDto(slug = slug))) }
        assertEquals(expected.size, expected.values.toSet().size)
    }

    @Test fun unknownLeafUsesItsKnownParentIcon() {
        val parent = ShopCategoryDto(id = "electronics", slug = "electronics", parentId = null)
        val unknown = ShopCategoryDto(id = "new-child", slug = "future-category", parentId = parent.id)
        assertEquals(categoryIcon(parent), categoryIcon(unknown, listOf(parent, unknown)))
        assertNotEquals(categoryIcon(parent), categoryIcon(ShopCategoryDto(slug = "mobile-phones")))
    }
}
