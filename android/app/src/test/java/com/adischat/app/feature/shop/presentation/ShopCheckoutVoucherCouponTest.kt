package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopCheckoutDto
import com.adischat.app.feature.shop.data.ShopPaymentMethodDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopCheckoutVoucherCouponTest {

    @Test
    fun defaultCheckout_hasZeroDiscounts() {
        val checkout = ShopCheckoutDto(
            id = "chk-123",
            subtotalMinor = 2800000,
            grandTotalMinor = 2800000,
            remainingPayableMinor = 2800000,
        )
        assertEquals(0, checkout.couponDiscountMinor)
        assertEquals(0, checkout.voucherAppliedMinor)
        assertEquals(2800000, checkout.remainingPayableMinor)
        assertFalse(checkout.isFullyCoveredByVoucher)
    }

    @Test
    fun couponApplication_calculatesDiscount() {
        val checkout = ShopCheckoutDto(
            id = "chk-123",
            subtotalMinor = 2800000,
            couponCode = "QA-PERCENT-10",
            couponDiscountMinor = 100000,
            grandTotalMinor = 2700000,
            remainingPayableMinor = 2700000,
        )
        assertEquals("QA-PERCENT-10", checkout.couponCode)
        assertEquals(100000, checkout.couponDiscountMinor)
        assertEquals(2700000, checkout.remainingPayableMinor)
        assertFalse(checkout.isFullyCoveredByVoucher)
    }

    @Test
    fun partialVoucher_leavesRemainingPayable() {
        val checkout = ShopCheckoutDto(
            id = "chk-123",
            subtotalMinor = 2800000,
            voucherCode = "VCH-PARTIAL-1000",
            voucherAppliedMinor = 100000,
            grandTotalMinor = 2700000,
            remainingPayableMinor = 2700000,
            paymentMethods = listOf(
                ShopPaymentMethodDto(code = "cash_on_delivery", label = "Cash on Delivery", provider = "manual")
            )
        )
        assertEquals("VCH-PARTIAL-1000", checkout.voucherCode)
        assertEquals(100000, checkout.voucherAppliedMinor)
        assertEquals(2700000, checkout.remainingPayableMinor)
        assertFalse(checkout.isFullyCoveredByVoucher)
        assertEquals("cash_on_delivery", checkout.paymentMethods.first().code)
    }

    @Test
    fun fullVoucher_marksFullyCoveredAndPrepaid() {
        val checkout = ShopCheckoutDto(
            id = "chk-123",
            subtotalMinor = 2800000,
            voucherCode = "VCH-FULL-COVERAGE-2026",
            voucherAppliedMinor = 2800000,
            grandTotalMinor = 0,
            remainingPayableMinor = 0,
            isFullyCoveredByVoucher = true,
            paymentMethods = listOf(
                ShopPaymentMethodDto(code = "voucher_prepaid", label = "Store Credit Voucher", provider = "voucher")
            )
        )
        assertEquals(0, checkout.remainingPayableMinor)
        assertTrue(checkout.isFullyCoveredByVoucher)
        assertEquals("voucher_prepaid", checkout.paymentMethods.first().code)
    }

    @Test
    fun combinedCouponAndVoucher_stacksCorrectly() {
        val checkout = ShopCheckoutDto(
            id = "chk-123",
            subtotalMinor = 2800000,
            couponCode = "QA-FIXED-500",
            couponDiscountMinor = 50000,
            voucherCode = "VCH-FULL-COVERAGE-2026",
            voucherAppliedMinor = 2750000,
            grandTotalMinor = 0,
            remainingPayableMinor = 0,
            isFullyCoveredByVoucher = true,
        )
        assertEquals(50000, checkout.couponDiscountMinor)
        assertEquals(2750000, checkout.voucherAppliedMinor)
        assertEquals(0, checkout.remainingPayableMinor)
        assertTrue(checkout.isFullyCoveredByVoucher)
    }
}
