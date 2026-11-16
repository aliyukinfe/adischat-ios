package com.adischat.app.feature.shop.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SellerApplicationRulesTest {
    @Test
    fun sellerFeaturesRequireApproval() {
        assertFalse(sellerFeaturesUnlocked("not_started"))
        assertFalse(sellerFeaturesUnlocked("draft"))
        assertFalse(sellerFeaturesUnlocked("submitted"))
        assertFalse(sellerFeaturesUnlocked("information_required"))
        assertFalse(sellerFeaturesUnlocked("rejected"))
        assertTrue(sellerFeaturesUnlocked("approved"))
        assertTrue(sellerFeaturesUnlocked("suspended"))
    }

    @Test
    fun businessTypeIsControlled() {
        assertTrue(validSellerBusinessType("individual"))
        assertTrue(validSellerBusinessType("plc"))
        assertFalse(validSellerBusinessType(""))
        assertFalse(validSellerBusinessType("partnership"))
    }

    @Test
    fun tinRequiresExactlyTenDigits() {
        assertTrue(validSellerTin("1234567890"))
        assertFalse(validSellerTin("123456789"))
        assertFalse(validSellerTin("12345678901"))
        assertFalse(validSellerTin("12345A7890"))
    }

    @Test
    fun subcityMustComeFromAddisAbabaList() {
        assertTrue(validSellerSubcity("Bole"))
        assertTrue(validSellerSubcity("Kolfe Keraniyo"))
        assertFalse(validSellerSubcity(""))
        assertFalse(validSellerSubcity("Bahir Dar"))
    }
}
