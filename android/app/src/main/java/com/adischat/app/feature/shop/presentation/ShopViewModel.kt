package com.adischat.app.feature.shop.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.feature.shop.data.ShopHomeState
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopHomeRepository
import com.adischat.app.feature.shop.data.AddressRepository
import com.adischat.app.feature.shop.data.CartRepository
import com.adischat.app.feature.shop.data.CheckoutRepository
import com.adischat.app.feature.shop.data.FavoriteRepository
import com.adischat.app.feature.shop.data.OrderRepository
import com.adischat.app.feature.shop.data.RecentlyViewedRepository
import com.adischat.app.feature.shop.data.SellerRepository
import com.adischat.app.feature.shop.data.ShopAddressRequest
import com.adischat.app.feature.shop.data.ShopIdentityRepository
import com.adischat.app.feature.shop.data.ShopNotificationRepository
import com.adischat.app.feature.shop.data.ShopProtectedState
import com.adischat.app.feature.shop.data.ShopStateReducers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ShopViewModel(
    private val repository: ShopHomeRepository = ShopHomeRepository(),
    private val identityRepository: ShopIdentityRepository = ShopIdentityRepository(),
    private val cartRepository: CartRepository = CartRepository(identityRepository),
    private val favoriteRepository: FavoriteRepository = FavoriteRepository(identityRepository),
    private val recentRepository: RecentlyViewedRepository = RecentlyViewedRepository(identityRepository),
    private val addressRepository: AddressRepository = AddressRepository(identityRepository),
    private val orderRepository: OrderRepository = OrderRepository(identityRepository),
    private val notificationRepository: ShopNotificationRepository = ShopNotificationRepository(identityRepository),
    private val checkoutRepository: CheckoutRepository = CheckoutRepository(identityRepository),
    private val sellerRepository: SellerRepository = SellerRepository(identityRepository),
) : ViewModel() {
    private val _state = MutableStateFlow(ShopHomeState())
    val state: StateFlow<ShopHomeState> = _state.asStateFlow()

    private val _protectedState = MutableStateFlow(ShopProtectedState())
    val protectedState: StateFlow<ShopProtectedState> = _protectedState.asStateFlow()

    private val buyNowIdempotencyKeys = mutableMapOf<String, String>()
    private val confirmIdempotencyKeys = mutableMapOf<String, String>()

    init {
        refresh()
        syncShopIdentity()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            _state.value = repository.loadHome()
        }
    }

    fun syncShopIdentity() {
        viewModelScope.launch {
            identityRepository.currentProfile()
                .onSuccess {
                    val cart = cartRepository.getCart().getOrNull()
                    _state.value = _state.value.copy(
                        shopIdentityReady = it != null,
                        cartCount = cart?.let { currentCart -> ShopStateReducers.cartItemCount(currentCart) } ?: _state.value.cartCount,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(shopIdentityReady = false)
                }
        }
    }

    fun addToCart(product: ShopProductDto) {
        if (product.id.isBlank()) return
        viewModelScope.launch {
            setBusy(product.id, true)
            cartRepository.add(product.id)
                .onSuccess { cart ->
                    _state.value = _state.value.copy(
                        cartCount = ShopStateReducers.cartItemCount(cart),
                        shopIdentityReady = true,
                        message = "Added to cart",
                    )
                    _protectedState.value = _protectedState.value.copy(cart = cart, error = null)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        message = ShopStateReducers.safeErrorMessage(error.message, "Your cart is temporarily unavailable."),
                    )
                }
            setBusy(product.id, false)
        }
    }

    fun loadCart() {
        loadProtected { cartRepository.getCart().onSuccess { cart ->
            _protectedState.value = _protectedState.value.copy(cart = cart, error = null)
            _state.value = _state.value.copy(cartCount = ShopStateReducers.cartItemCount(cart), shopIdentityReady = true)
        } }
    }

    fun updateCartItem(itemId: String, quantity: Int) {
        if (itemId.isBlank()) return
        viewModelScope.launch {
            setBusy(itemId, true)
            val result = if (quantity <= 0) cartRepository.remove(itemId) else cartRepository.update(itemId, quantity)
            result.onSuccess { cart ->
                _protectedState.value = _protectedState.value.copy(cart = cart, error = null)
                _state.value = _state.value.copy(cartCount = ShopStateReducers.cartItemCount(cart))
            }.onFailure { showProtectedError(it, "Couldn't update your cart. Try again.") }
            setBusy(itemId, false)
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            val result = cartRepository.clear()
            result.onSuccess { cart ->
                _protectedState.value = _protectedState.value.copy(cart = cart, error = null)
                _state.value = _state.value.copy(cartCount = 0)
            }.onFailure { showProtectedError(it, "Couldn't clear your cart. Try again.") }
        }
    }

    fun loadFavorites() {
        loadProtected { favoriteRepository.list().onSuccess { favorites ->
            _protectedState.value = _protectedState.value.copy(favorites = favorites, error = null)
        } }
    }

    fun toggleFavorite(product: ShopProductDto) {
        if (product.id.isBlank()) return
        viewModelScope.launch {
            setBusy(product.id, true)
            val saved = _protectedState.value.favorites.any { it.productId == product.id }
            val result = if (saved) favoriteRepository.remove(product.id) else favoriteRepository.add(product.id)
            result.onSuccess { favorites ->
                _protectedState.value = _protectedState.value.copy(favorites = favorites, error = null)
                _state.value = _state.value.copy(message = if (saved) "Removed from saved products" else "Saved product")
            }.onFailure { showProtectedError(it, "Couldn't update saved products. Try again.") }
            setBusy(product.id, false)
        }
    }

    fun recordProductView(product: ShopProductDto) {
        if (product.id.isBlank()) return
        viewModelScope.launch { recentRepository.record(product.id) }
    }

    fun loadRecentlyViewed() {
        loadProtected { recentRepository.list().onSuccess { recent ->
            _protectedState.value = _protectedState.value.copy(recentlyViewed = recent, error = null)
        } }
    }

    fun loadAddresses() {
        loadProtected { addressRepository.list().onSuccess { addresses ->
            _protectedState.value = _protectedState.value.copy(addresses = addresses, error = null)
        } }
    }

    fun saveAddress(request: ShopAddressRequest) {
        viewModelScope.launch {
            val result = addressRepository.save(request)
            result.onSuccess { addresses ->
                _protectedState.value = _protectedState.value.copy(addresses = addresses, error = null)
                _state.value = _state.value.copy(message = "Address saved")
            }.onFailure { showProtectedError(it, "Couldn't save the address. Try again.") }
        }
    }

    fun deleteAddress(id: String) {
        viewModelScope.launch {
            addressRepository.delete(id)
                .onSuccess { _protectedState.value = _protectedState.value.copy(addresses = it, error = null) }
                .onFailure { showProtectedError(it, "Couldn't delete the address. Try again.") }
        }
    }

    fun setDefaultAddress(id: String) {
        viewModelScope.launch {
            addressRepository.setDefault(id)
                .onSuccess { _protectedState.value = _protectedState.value.copy(addresses = it, error = null) }
                .onFailure { showProtectedError(it, "Couldn't update the address. Try again.") }
        }
    }

    fun loadOrders() {
        loadProtected { orderRepository.list().onSuccess { orders ->
            _protectedState.value = _protectedState.value.copy(orders = orders, error = null)
        } }
    }

    fun loadOrderDetail(orderId: String) {
        if (orderId.isBlank()) return
        loadProtected { orderRepository.detail(orderId).onSuccess { order ->
            _protectedState.value = _protectedState.value.copy(selectedOrder = order, error = null)
        } }
    }

    fun requestOrderCancellation(orderId: String, reason: String = "") {
        if (orderId.isBlank()) return
        viewModelScope.launch {
            orderRepository.cancel(orderId, reason)
                .onSuccess { order ->
                    _protectedState.value = _protectedState.value.copy(selectedOrder = order, error = null)
                    _state.value = _state.value.copy(message = "Cancellation requested")
                    loadOrders()
                }
                .onFailure { showProtectedError(it, "This order cannot be cancelled right now.") }
        }
    }

    fun revealDeliveryCode(orderId: String) {
        if (orderId.isBlank()) return
        viewModelScope.launch {
            orderRepository.revealDeliveryCode(orderId)
                .onSuccess { code -> _protectedState.value = _protectedState.value.copy(deliveryCode = code, error = null) }
                .onFailure { showProtectedError(it, "Delivery code is not available yet.") }
        }
    }

    fun loadNotifications() {
        loadProtected {
            notificationRepository.list().onSuccess { notifications ->
                _protectedState.value = _protectedState.value.copy(notifications = notifications, error = null)
            }
        }
        viewModelScope.launch {
            notificationRepository.unreadCount().onSuccess { count ->
                _protectedState.value = _protectedState.value.copy(notificationUnreadCount = count)
            }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            notificationRepository.markAllRead()
                .onSuccess { count -> _protectedState.value = _protectedState.value.copy(notificationUnreadCount = count) }
                .onFailure { showProtectedError(it, "Couldn't update notifications.") }
        }
    }

    fun createCheckout() {
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(loading = true, error = null, checkout = null)
            _state.value = _state.value.copy(message = null)
            checkoutRepository.create("android-${UUID.randomUUID()}")
                .onSuccess { checkout ->
                    _protectedState.value = _protectedState.value.copy(checkout = checkout, loading = false, error = null)
                    _state.value = _state.value.copy(
                        message = if (checkout.paymentMethods.isEmpty()) {
                            "Checkout is ready, but no payment method is enabled yet."
                        } else {
                            null
                        },
                    )
                }
                .onFailure {
                    showCheckoutError(it, "Checkout could not start. Please refresh your cart and try again.")
                }
        }
    }

    fun buyNow(product: ShopProductDto) {
        if (product.id.isBlank()) return
        if (_protectedState.value.busyIds.contains(product.id) || _protectedState.value.loading) return
        viewModelScope.launch {
            setBusy(product.id, true)
            _protectedState.value = _protectedState.value.copy(loading = true, error = null, checkout = null)
            val idempotencyKey = buyNowIdempotencyKeys.getOrPut(product.id) {
                "android-buy-now-${product.id}-${UUID.randomUUID()}"
            }
            checkoutRepository.createBuyNow(
                productId = product.id,
                quantity = 1,
                idempotencyKey = idempotencyKey,
            )
                .onSuccess { checkout ->
                    _protectedState.value = _protectedState.value.copy(checkout = checkout, loading = false, error = null)
                }
                .onFailure {
                    buyNowIdempotencyKeys.remove(product.id)
                    showCheckoutError(it, "Checkout could not start. Please refresh your cart and try again.")
                }
            setBusy(product.id, false)
        }
    }

    fun chatSeller(product: ShopProductDto) {
        val storeName = product.store?.name ?: "this seller"
        _state.value = _state.value.copy(message = "Chat with $storeName is being connected to AdisChat messaging.")
    }

    fun confirmCheckout(checkoutSessionId: String, addressId: String, paymentMethodCode: String) {
        if (checkoutSessionId.isBlank() || _protectedState.value.loading) return
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(loading = true, error = null)
            val idempotencyKey = confirmIdempotencyKeys.getOrPut(checkoutSessionId) {
                "android-confirm-$checkoutSessionId-${UUID.randomUUID()}"
            }
            checkoutRepository.confirm(checkoutSessionId, addressId, paymentMethodCode, idempotencyKey)
                .onSuccess { order ->
                    confirmIdempotencyKeys.remove(checkoutSessionId)
                    _protectedState.value = _protectedState.value.copy(checkoutOrder = order, loading = false, error = null)
                    _state.value = _state.value.copy(message = "Order created. Payment is pending.")
                    loadOrders()
                    loadCart()
                }
                .onFailure {
                    confirmIdempotencyKeys.remove(checkoutSessionId)
                    showProtectedError(it, "Order could not be created.")
                }
        }
    }

    fun loadSellerStatus() {
        loadProtected { sellerRepository.status().onSuccess { (status, application) ->
            _protectedState.value = _protectedState.value.copy(sellerStatus = status, sellerApplication = application, error = null)
        } }
    }

    fun loadSellerOrders() {
        loadProtected { sellerRepository.orderGroups().onSuccess { groups ->
            _protectedState.value = _protectedState.value.copy(sellerOrderGroups = groups, error = null)
        } }
    }

    fun sellerOrderAction(orderGroupId: String, action: String, reason: String = "", carrier: String? = null, trackingNumber: String? = null) {
        if (orderGroupId.isBlank()) return
        viewModelScope.launch {
            setBusy(orderGroupId, true)
            sellerRepository.transition(orderGroupId, action, reason, carrier, trackingNumber)
                .onSuccess { groups ->
                    _protectedState.value = _protectedState.value.copy(sellerOrderGroups = groups, error = null)
                    _state.value = _state.value.copy(message = "Seller order updated")
                    loadOrders()
                    loadSellerOrders()
                }
                .onFailure {
                    showProtectedError(it, "Seller action is not allowed for this order state.")
                    loadSellerOrders()
                }
            setBusy(orderGroupId, false)
        }
    }

    fun submitSellerApplication(storeName: String) {
        viewModelScope.launch {
            sellerRepository.submit(storeName)
                .onSuccess { (status, application) ->
                    _protectedState.value = _protectedState.value.copy(sellerStatus = status, sellerApplication = application, error = null)
                    _state.value = _state.value.copy(message = "Seller application submitted")
                }
                .onFailure { showProtectedError(it, "Couldn't submit seller application. Try again.") }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun loadProtected(block: suspend () -> Result<*>) {
        _protectedState.value = _protectedState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            block().onFailure { showProtectedError(it, "Couldn't load this right now. Try again.") }
            _protectedState.value = _protectedState.value.copy(loading = false)
        }
    }

    private fun showProtectedError(error: Throwable, fallback: String) {
        val message = ShopStateReducers.safeErrorMessage(error.message, fallback)
        _protectedState.value = _protectedState.value.copy(error = message, loading = false)
        _state.value = _state.value.copy(message = message)
    }

    private fun showCheckoutError(error: Throwable, fallback: String) {
        val message = ShopStateReducers.safeErrorMessage(error.message, fallback)
        _protectedState.value = _protectedState.value.copy(error = message, loading = false, checkout = null)
        _state.value = _state.value.copy(message = null)
    }

    private fun setBusy(id: String, busy: Boolean) {
        val ids = _protectedState.value.busyIds
        _protectedState.value = _protectedState.value.copy(
            busyIds = if (busy) ids + id else ids - id,
        )
    }
}
