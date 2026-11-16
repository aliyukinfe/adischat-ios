package com.adischat.app.feature.shop.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutAddressFlowTest {
    @Test fun successfulSaveReturnsToSameCheckoutWithNewAddress() {
        val flow = CheckoutAddressFlow(checkoutId = "buy-now-checkout")
            .open().beginSave().completeSave("buy-now-checkout", "new-address")
        assertEquals("buy-now-checkout", flow.checkoutId)
        assertEquals("new-address", flow.selectedAddressId)
        assertFalse(flow.formOpen)
        assertFalse(flow.saving)
    }

    @Test fun cancelKeepsCheckoutAndPreviousAddress() {
        val flow = CheckoutAddressFlow("cart-checkout", "existing-address").open().cancel()
        assertEquals("cart-checkout", flow.checkoutId)
        assertEquals("existing-address", flow.selectedAddressId)
        assertFalse(flow.formOpen)
    }

    @Test fun failedSaveKeepsFormAndCheckoutForRetry() {
        val flow = CheckoutAddressFlow("buy-now-checkout").open().beginSave().completeSave("buy-now-checkout", null)
        assertEquals("buy-now-checkout", flow.checkoutId)
        assertTrue(flow.formOpen)
        assertFalse(flow.saving)
        assertEquals("", flow.selectedAddressId)
    }

    @Test fun savingCannotBeCancelledMidRequest() {
        val flow = CheckoutAddressFlow("buy-now-checkout").open().beginSave()
        assertEquals(flow, flow.cancel())
    }

    @Test fun staleAddressResponseCannotModifyNextCheckout() {
        val nextCheckout = CheckoutAddressFlow("next-checkout").open()
        assertEquals(nextCheckout, nextCheckout.completeSave("previous-checkout", "old-address"))
    }
}
