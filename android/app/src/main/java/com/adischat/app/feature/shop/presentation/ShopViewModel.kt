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
import com.adischat.app.feature.shop.data.ShopAdminRepository
import com.adischat.app.feature.shop.data.ShopAdminReviewProductRequest
import com.adischat.app.feature.shop.data.ShopAdminReviewSellerRequest
import com.adischat.app.feature.shop.data.ShopSellerProductDraftPayload
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
    private val adminRepository: ShopAdminRepository = ShopAdminRepository(identityRepository),
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
            _protectedState.value = _protectedState.value.copy(
                selectedOrder = order,
                selectedOrderIssues = order.issues,
                selectedOrderActions = order.postDeliveryActions,
                selectedOrderReviews = order.reviews,
                selectedRefundRequests = order.refundRequests,
                selectedExchangeRequests = order.exchangeRequests,
                error = null,
            )
            loadIssueOptions(orderId)
            loadPostDeliveryPayloads(orderId)
        } }
    }

    fun loadPostDeliveryPayloads(orderId: String) {
        if (orderId.isBlank()) return
        viewModelScope.launch {
            orderRepository.postDeliveryActions(orderId)
                .onSuccess { actions -> _protectedState.value = _protectedState.value.copy(selectedOrderActions = actions, error = null) }
                .onFailure { _protectedState.value = _protectedState.value.copy(selectedOrderActions = null) }
            orderRepository.reviews(orderId)
                .onSuccess { reviews -> _protectedState.value = _protectedState.value.copy(selectedOrderReviews = reviews) }
            orderRepository.requestPayloads(orderId)
                .onSuccess { (refunds, exchanges) ->
                    _protectedState.value = _protectedState.value.copy(selectedRefundRequests = refunds, selectedExchangeRequests = exchanges)
                }
        }
    }

    fun loadIssueOptions(orderId: String) {
        if (orderId.isBlank()) return
        viewModelScope.launch {
            orderRepository.issueOptions(orderId)
                .onSuccess { options -> _protectedState.value = _protectedState.value.copy(issueOptions = options, error = null) }
                .onFailure { _protectedState.value = _protectedState.value.copy(issueOptions = null) }
            orderRepository.issues(orderId)
                .onSuccess { issues -> _protectedState.value = _protectedState.value.copy(selectedOrderIssues = issues) }
        }
    }

    fun submitOrderIssue(orderId: String, orderGroupId: String?, reasonCode: String, description: String) {
        if (orderId.isBlank() || reasonCode.isBlank()) return
        viewModelScope.launch {
            val busyKey = "issue-$orderId"
            setBusy(busyKey, true)
            orderRepository.createIssue(orderId, orderGroupId, reasonCode, description)
                .onSuccess {
                    _state.value = _state.value.copy(message = "Buyer Protection issue submitted")
                    loadOrderDetail(orderId)
                }
                .onFailure { showProtectedError(it, "Buyer Protection is temporarily unavailable.") }
            setBusy(busyKey, false)
        }
    }

    fun submitProductReview(orderId: String, orderItemId: String, rating: Int, title: String, body: String) {
        if (orderId.isBlank() || orderItemId.isBlank()) return
        val busyKey = "review-$orderItemId"
        viewModelScope.launch {
            setBusy(busyKey, true)
            orderRepository.createProductReview(orderId, orderItemId, rating, title, body)
                .onSuccess {
                    _state.value = _state.value.copy(message = "Product review submitted")
                    loadOrderDetail(orderId)
                }
                .onFailure { showProtectedError(it, "Review submission is temporarily unavailable.") }
            setBusy(busyKey, false)
        }
    }

    fun submitStoreReview(orderId: String, orderGroupId: String, rating: Int, body: String) {
        if (orderId.isBlank() || orderGroupId.isBlank()) return
        val busyKey = "store-review-$orderGroupId"
        viewModelScope.launch {
            setBusy(busyKey, true)
            orderRepository.createStoreReview(orderId, orderGroupId, rating, body)
                .onSuccess {
                    _state.value = _state.value.copy(message = "Store review submitted")
                    loadOrderDetail(orderId)
                }
                .onFailure { showProtectedError(it, "Store review is temporarily unavailable.") }
            setBusy(busyKey, false)
        }
    }

    fun submitRefundRequest(orderId: String, orderItemId: String, quantity: Int, reasonCode: String, description: String) {
        if (orderId.isBlank() || orderItemId.isBlank()) return
        val busyKey = "refund-$orderItemId"
        viewModelScope.launch {
            setBusy(busyKey, true)
            orderRepository.createRefundRequest(orderId, orderItemId, quantity, reasonCode, description)
                .onSuccess {
                    _state.value = _state.value.copy(message = "Refund request submitted")
                    loadOrderDetail(orderId)
                }
                .onFailure { showProtectedError(it, "Refund requests are temporarily unavailable.") }
            setBusy(busyKey, false)
        }
    }

    fun submitExchangeRequest(orderId: String, orderItemId: String, quantity: Int, reasonCode: String, description: String) {
        if (orderId.isBlank() || orderItemId.isBlank()) return
        val busyKey = "exchange-$orderItemId"
        viewModelScope.launch {
            setBusy(busyKey, true)
            orderRepository.createExchangeRequest(orderId, orderItemId, quantity, reasonCode, description)
                .onSuccess {
                    _state.value = _state.value.copy(message = "Exchange request submitted")
                    loadOrderDetail(orderId)
                }
                .onFailure { showProtectedError(it, "Exchange requests are temporarily unavailable.") }
            setBusy(busyKey, false)
        }
    }

    fun buyAgainFromOrder(orderId: String) {
        if (orderId.isBlank()) return
        val busyKey = "buy-again-$orderId"
        viewModelScope.launch {
            setBusy(busyKey, true)
            orderRepository.buyAgain(orderId)
                .onSuccess { result ->
                    _protectedState.value = _protectedState.value.copy(lastBuyAgainResult = result, error = null)
                    _state.value = _state.value.copy(message = "Eligible items were added to cart")
                    loadCart()
                }
                .onFailure { showProtectedError(it, "Buy Again is temporarily unavailable.") }
            setBusy(busyKey, false)
        }
    }

    fun loadReceipt(orderId: String) {
        if (orderId.isBlank()) return
        viewModelScope.launch {
            orderRepository.receipt(orderId)
                .onSuccess { receipt -> _protectedState.value = _protectedState.value.copy(selectedReceipt = receipt, error = null) }
                .onFailure { showProtectedError(it, "Receipt is temporarily unavailable.") }
        }
    }

    fun clearReceipt() {
        _protectedState.value = _protectedState.value.copy(selectedReceipt = null)
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
            val busyKey = "delivery-code-$orderId"
            setBusy(busyKey, true)
            orderRepository.revealDeliveryCode(orderId)
                .onSuccess { code ->
                    _protectedState.value = _protectedState.value.copy(deliveryCode = code, error = null)
                    loadOrderDetail(orderId)
                }
                .onFailure { showProtectedError(it, "Delivery code is not available yet.") }
            setBusy(busyKey, false)
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

    fun loadSellerProducts() {
        loadProtected { sellerRepository.products().onSuccess { products ->
            _protectedState.value = _protectedState.value.copy(sellerProducts = products, error = null)
        } }
    }

    fun loadSellerRequests() {
        loadProtected { sellerRepository.requests().onSuccess { requests ->
            _protectedState.value = _protectedState.value.copy(sellerRequests = requests, error = null)
        } }
    }

    fun createSellerProductDraft() {
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(loading = true, error = null)
            sellerRepository.createProduct("android-seller-product-create-${UUID.randomUUID()}")
                .onSuccess { product ->
                    _protectedState.value = _protectedState.value.copy(
                        selectedSellerProduct = product,
                        sellerProducts = listOf(product) + _protectedState.value.sellerProducts.filterNot { it.id == product.id },
                        loading = false,
                        error = null,
                    )
                    _state.value = _state.value.copy(message = "Draft product created")
                }
                .onFailure { showProtectedError(it, "Product creation is temporarily unavailable.") }
        }
    }

    fun editSellerProduct(productId: String) {
        val product = _protectedState.value.sellerProducts.firstOrNull { it.id == productId } ?: return
        _protectedState.value = _protectedState.value.copy(selectedSellerProduct = product)
    }

    fun clearSellerProductEditor() {
        _protectedState.value = _protectedState.value.copy(selectedSellerProduct = null)
    }

    fun saveSellerProduct(productId: String, payload: ShopSellerProductDraftPayload) {
        if (productId.isBlank()) return
        viewModelScope.launch {
            setBusy(productId, true)
            sellerRepository.saveProduct(productId, payload, "android-seller-product-save-$productId-${UUID.randomUUID()}")
                .onSuccess { product ->
                    _protectedState.value = _protectedState.value.copy(
                        selectedSellerProduct = product,
                        sellerProducts = listOf(product) + _protectedState.value.sellerProducts.filterNot { it.id == product.id },
                        error = null,
                    )
                    _state.value = _state.value.copy(message = "Product saved")
                }
                .onFailure { showProtectedError(it, "Product save is temporarily unavailable.") }
            setBusy(productId, false)
        }
    }

    fun submitSellerProduct(productId: String) {
        if (productId.isBlank()) return
        viewModelScope.launch {
            setBusy(productId, true)
            sellerRepository.submitProduct(productId, "android-seller-product-submit-$productId-${UUID.randomUUID()}")
                .onSuccess { product ->
                    _protectedState.value = _protectedState.value.copy(
                        selectedSellerProduct = product,
                        sellerProducts = listOf(product) + _protectedState.value.sellerProducts.filterNot { it.id == product.id },
                        error = null,
                    )
                    _state.value = _state.value.copy(message = "Product submitted for review")
                    loadAdminDashboard()
                }
                .onFailure { showProtectedError(it, "Product is missing required details.") }
            setBusy(productId, false)
        }
    }

    fun uploadSellerProductMedia(productId: String, bytes: ByteArray, mimeType: String, width: Int, height: Int, altText: String) {
        if (productId.isBlank() || bytes.isEmpty()) return
        viewModelScope.launch {
            setBusy("media-$productId", true)
            sellerRepository.uploadProductMedia(
                productId = productId,
                bytes = bytes,
                mimeType = mimeType,
                width = width,
                height = height,
                altText = altText,
                requestId = "android-seller-product-media-$productId-${UUID.randomUUID()}",
            ).onSuccess {
                sellerRepository.products()
                    .onSuccess { products ->
                        val refreshed = products.firstOrNull { product -> product.id == productId }
                        _protectedState.value = _protectedState.value.copy(
                            sellerProducts = products,
                            selectedSellerProduct = refreshed ?: _protectedState.value.selectedSellerProduct,
                            error = null,
                        )
                    }
                _state.value = _state.value.copy(message = "Image uploaded")
            }.onFailure { showProtectedError(it, "Image upload is temporarily unavailable.") }
            setBusy("media-$productId", false)
        }
    }

    fun deleteSellerProductMedia(productId: String, imageId: String) {
        if (productId.isBlank() || imageId.isBlank()) return
        viewModelScope.launch {
            setBusy("media-$productId", true)
            sellerRepository.deleteProductMedia(productId, imageId)
                .onSuccess { product ->
                    _protectedState.value = _protectedState.value.copy(
                        selectedSellerProduct = product,
                        sellerProducts = listOf(product) + _protectedState.value.sellerProducts.filterNot { it.id == product.id },
                        error = null,
                    )
                    _state.value = _state.value.copy(message = "Image removed")
                }
                .onFailure { showProtectedError(it, "Image removal is temporarily unavailable.") }
            setBusy("media-$productId", false)
        }
    }

    fun reorderSellerProductMedia(productId: String, imageIds: List<String>, primaryImageId: String) {
        if (productId.isBlank() || imageIds.isEmpty() || primaryImageId.isBlank()) return
        viewModelScope.launch {
            setBusy("media-$productId", true)
            sellerRepository.reorderProductMedia(productId, imageIds, primaryImageId)
                .onSuccess { product ->
                    _protectedState.value = _protectedState.value.copy(
                        selectedSellerProduct = product,
                        sellerProducts = listOf(product) + _protectedState.value.sellerProducts.filterNot { it.id == product.id },
                        error = null,
                    )
                    _state.value = _state.value.copy(message = "Image order updated")
                }
                .onFailure { showProtectedError(it, "Image order is temporarily unavailable.") }
            setBusy("media-$productId", false)
        }
    }

    fun sellerOrderAction(orderGroupId: String, action: String, reason: String = "", reasonCode: String? = null, carrier: String? = null, trackingNumber: String? = null) {
        if (orderGroupId.isBlank()) return
        viewModelScope.launch {
            setBusy(orderGroupId, true)
            sellerRepository.transition(orderGroupId, action, reason, reasonCode, carrier, trackingNumber)
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

    fun loadAdminSession() {
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(adminLoading = true, adminError = null)
            adminRepository.session()
                .onSuccess { session ->
                    _protectedState.value = _protectedState.value.copy(adminSession = session, adminLoading = false, adminError = null)
                    loadAdminDashboard()
                }
                .onFailure {
                    _protectedState.value = _protectedState.value.copy(adminSession = null, adminLoading = false, adminError = null)
                }
        }
    }

    fun loadAdminDashboard() {
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(adminLoading = true, adminError = null)
            adminRepository.dashboard()
                .onSuccess { dashboard -> _protectedState.value = _protectedState.value.copy(adminDashboard = dashboard, adminLoading = false, adminError = null) }
                .onFailure { showAdminError(it, "Couldn't load Admin dashboard.") }
        }
    }

    fun loadAdminQueues() {
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(adminLoading = true, adminError = null)
            val sellers = adminRepository.sellerApplications().getOrElse {
                showAdminError(it, "Couldn't load seller applications.")
                return@launch
            }
            val products = adminRepository.products().getOrElse {
                showAdminError(it, "Couldn't load products for review.")
                return@launch
            }
            val stores = adminRepository.stores().getOrDefault(emptyList())
            _protectedState.value = _protectedState.value.copy(
                adminSellerApplications = sellers,
                adminProducts = products,
                adminStores = stores,
                adminLoading = false,
                adminError = null,
            )
        }
    }

    fun loadAdminProductDetail(productId: String) {
        if (productId.isBlank()) return
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(adminLoading = true, adminError = null)
            adminRepository.productDetail(productId)
                .onSuccess { detail ->
                    _protectedState.value = _protectedState.value.copy(
                        selectedAdminProduct = detail,
                        adminLoading = false,
                        adminError = null,
                    )
                }
                .onFailure { showAdminError(it, "Couldn't load product details.") }
        }
    }

    fun clearAdminProductDetail() {
        _protectedState.value = _protectedState.value.copy(selectedAdminProduct = null, adminError = null)
    }

    fun loadAdminOrders() {
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(adminLoading = true, adminError = null)
            adminRepository.orders()
                .onSuccess { orders -> _protectedState.value = _protectedState.value.copy(adminOrders = orders, adminLoading = false, adminError = null) }
                .onFailure { showAdminError(it, "Couldn't load orders.") }
        }
    }

    fun loadAdminOrderDetail(orderId: String) {
        if (orderId.isBlank()) return
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(adminLoading = true, adminError = null)
            adminRepository.orderDetail(orderId)
                .onSuccess { detail ->
                    _protectedState.value = _protectedState.value.copy(
                        selectedAdminOrder = detail,
                        adminLoading = false,
                        adminError = null,
                    )
                }
                .onFailure { showAdminError(it, "Couldn't load order details.") }
        }
    }

    fun clearAdminOrderDetail() {
        _protectedState.value = _protectedState.value.copy(selectedAdminOrder = null, adminError = null)
    }

    fun loadAdminStores() {
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(adminLoading = true, adminError = null)
            adminRepository.stores()
                .onSuccess { stores -> _protectedState.value = _protectedState.value.copy(adminStores = stores, adminLoading = false, adminError = null) }
                .onFailure { showAdminError(it, "Couldn't load stores.") }
        }
    }

    fun loadAdminStoreDetail(storeId: String) {
        if (storeId.isBlank()) return
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(adminLoading = true, adminError = null)
            adminRepository.storeDetail(storeId)
                .onSuccess { detail -> _protectedState.value = _protectedState.value.copy(selectedAdminStore = detail, adminLoading = false, adminError = null) }
                .onFailure { showAdminError(it, "Couldn't load store detail.") }
        }
    }

    fun clearAdminStoreDetail() {
        _protectedState.value = _protectedState.value.copy(selectedAdminStore = null, adminError = null)
    }

    fun adminUpdateStoreStatus(storeId: String, action: String, reason: String) {
        if (storeId.isBlank() || action.isBlank()) return
        viewModelScope.launch {
            setBusy("admin-store-$storeId", true)
            adminRepository.updateStoreStatus(storeId, action, reason)
                .onSuccess { detail ->
                    if (detail != null) _protectedState.value = _protectedState.value.copy(selectedAdminStore = detail)
                    _state.value = _state.value.copy(message = "Store moderation updated")
                    loadAdminStores()
                }
                .onFailure { showAdminError(it, "Store moderation could not be saved.") }
            setBusy("admin-store-$storeId", false)
        }
    }

    fun adminUpdateOrderIssue(issueId: String, status: String, buyerVisibleMessage: String, privateNote: String = "") {
        if (issueId.isBlank() || status.isBlank()) return
        val currentOrderId = _protectedState.value.selectedAdminOrder?.order?.id.orEmpty()
        viewModelScope.launch {
            setBusy("admin-issue-$issueId", true)
            adminRepository.updateOrderIssue(issueId, status, buyerVisibleMessage, privateNote)
                .onSuccess {
                    _state.value = _state.value.copy(message = "Issue updated")
                    if (currentOrderId.isNotBlank()) loadAdminOrderDetail(currentOrderId)
                }
                .onFailure { showAdminError(it, "Issue update could not be saved.") }
            setBusy("admin-issue-$issueId", false)
        }
    }

    fun loadAdminAuditLog() {
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(adminLoading = true, adminError = null)
            adminRepository.auditLog()
                .onSuccess { log -> _protectedState.value = _protectedState.value.copy(adminAuditLog = log, adminLoading = false, adminError = null) }
                .onFailure { showAdminError(it, "Couldn't load audit log.") }
        }
    }

    fun adminReviewSeller(applicationId: String, action: String, reason: String = "") {
        if (applicationId.isBlank()) return
        viewModelScope.launch {
            setBusy(applicationId, true)
            adminRepository.reviewSeller(
                ShopAdminReviewSellerRequest(
                    applicationId = applicationId,
                    action = action,
                    reason = reason,
                    requestId = "android-admin-seller-$applicationId-$action-${UUID.randomUUID()}",
                ),
            ).onSuccess {
                _state.value = _state.value.copy(message = "Seller application updated")
                loadAdminDashboard()
                loadAdminQueues()
            }.onFailure { showAdminError(it, "Seller review could not be saved.") }
            setBusy(applicationId, false)
        }
    }

    fun adminReviewProduct(productId: String, action: String, reason: String = "") {
        if (productId.isBlank()) return
        viewModelScope.launch {
            setBusy(productId, true)
            adminRepository.reviewProduct(
                ShopAdminReviewProductRequest(
                    productId = productId,
                    action = action,
                    reasonCode = if (action == "approve") "" else "content_quality",
                    publicReason = reason,
                    privateNotes = reason,
                    requestId = "android-admin-product-$productId-$action-${UUID.randomUUID()}",
                ),
            ).onSuccess { detail ->
                if (detail != null) {
                    _protectedState.value = _protectedState.value.copy(selectedAdminProduct = detail)
                }
                _state.value = _state.value.copy(message = "Product moderation updated")
                loadAdminDashboard()
                loadAdminQueues()
            }.onFailure { showAdminError(it, "Product review could not be saved.") }
            setBusy(productId, false)
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

    private fun showAdminError(error: Throwable, fallback: String) {
        val message = ShopStateReducers.safeErrorMessage(error.message, fallback)
        _protectedState.value = _protectedState.value.copy(adminError = message, adminLoading = false)
        _state.value = _state.value.copy(message = message)
    }

    private fun setBusy(id: String, busy: Boolean) {
        val ids = _protectedState.value.busyIds
        _protectedState.value = _protectedState.value.copy(
            busyIds = if (busy) ids + id else ids - id,
        )
    }
}
