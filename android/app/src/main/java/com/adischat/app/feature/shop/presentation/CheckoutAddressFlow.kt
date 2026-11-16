package com.adischat.app.feature.shop.presentation

internal data class CheckoutAddressFlow(
    val checkoutId: String,
    val selectedAddressId: String = "",
    val formOpen: Boolean = false,
    val saving: Boolean = false,
) {
    fun open() = copy(formOpen = true)
    fun select(addressId: String) = copy(selectedAddressId = addressId)
    fun beginSave() = copy(saving = true)
    fun completeSave(requestCheckoutId: String, savedAddressId: String?): CheckoutAddressFlow =
        if (checkoutId != requestCheckoutId) this else copy(
            selectedAddressId = savedAddressId ?: selectedAddressId,
            formOpen = savedAddressId == null,
            saving = false,
        )
    fun cancel() = if (saving) this else copy(formOpen = false)
}
