package com.adischat.app.feature.shop.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class AdisGebeyaAccountPresentationTest {
    @Test
    fun firstName_usesAuthenticatedDisplayNameWithoutExposingOtherFields() {
        assertEquals("Aliyu", adisGebeyaFirstName("  Aliyu Kinfe  "))
        assertEquals("there", adisGebeyaFirstName(""))
    }

    @Test
    fun metricValue_neverFlashesAFakeZeroBeforeProtectedDataLoads() {
        assertEquals("—", accountMetricValue(loaded = false, count = 0))
        assertEquals("0", accountMetricValue(loaded = true, count = 0))
        assertEquals("12", accountMetricValue(loaded = true, count = 12))
    }
}
