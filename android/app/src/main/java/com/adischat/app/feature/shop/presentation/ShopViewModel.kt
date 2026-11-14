package com.adischat.app.feature.shop.presentation

import android.content.Context
import android.net.Uri
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
import com.adischat.app.feature.shop.data.MarketplaceNotificationContext
import com.adischat.app.feature.shop.data.ShopOrderReviewsDto
import com.adischat.app.feature.shop.data.ShopMessageRepository
import com.adischat.app.feature.shop.data.ShopProtectedState
import com.adischat.app.feature.shop.data.ShopAdminRepository
import com.adischat.app.feature.shop.data.ShopAdminProductQuery
import com.adischat.app.feature.shop.data.ShopQaModerationContract
import com.adischat.app.feature.shop.data.ShopQaModerationRetryKeys
import com.adischat.app.feature.shop.data.ShopPublicContentItemDto
import com.adischat.app.feature.shop.data.ShopPublicContentReviewFlags
import com.adischat.app.feature.shop.data.ShopAdminReviewProductRequest
import com.adischat.app.feature.shop.data.ShopAdminReviewSellerRequest
import com.adischat.app.feature.shop.data.ShopSellerApplicationRequest
import com.adischat.app.feature.shop.data.ShopSellerProductDraftPayload
import com.adischat.app.feature.shop.data.ShopSellerCustomerNoteRequest
import com.adischat.app.feature.shop.data.ShopSellerInventoryChange
import com.adischat.app.feature.shop.data.ShopSellerInventoryUpdateRequest
import com.adischat.app.feature.shop.data.ShopSellerReviewReplyRequest
import com.adischat.app.feature.shop.data.ShopStateReducers
import com.adischat.app.feature.shop.data.ProductRepository
import com.adischat.app.feature.shop.data.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.time.Instant

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
    private val messageRepository: ShopMessageRepository = ShopMessageRepository(identityRepository),
    private val adminRepository: ShopAdminRepository = ShopAdminRepository(identityRepository),
    private val productRepository: ProductRepository = ProductRepository(),
    private val categoryRepository: CategoryRepository = CategoryRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ShopHomeState())
    val state: StateFlow<ShopHomeState> = _state.asStateFlow()

    private val _buyerSearchState = MutableStateFlow(BuyerSearchState())
    internal val buyerSearchState: StateFlow<BuyerSearchState> = _buyerSearchState.asStateFlow()

    private val _protectedState = MutableStateFlow(ShopProtectedState())
    val protectedState: StateFlow<ShopProtectedState> = _protectedState.asStateFlow()

    internal val visualSearchSession = MarketplaceVisualSearchSession(viewModelScope)

    private val buyNowIdempotencyKeys = mutableMapOf<String, String>()
    private val confirmIdempotencyKeys = mutableMapOf<String, String>()
    private val conversationRefreshesInFlight = mutableSetOf<String>()
    private var protectedRequestGeneration = 0L
    private var sellerProductRequestGeneration = 0L
    private var sellerBusinessRequestGeneration = 0L
    private var sellerOrderRequestGeneration = 0L
    private var sellerRequestGeneration = 0L
    private var adminRequestGeneration = 0L
    private var publicContentRequestGeneration = 0L
    private var publicContentPreviewGeneration = 0L
    private val publicContentRetryKeys = ShopQaModerationRetryKeys()
    private var publicContentQueueKind: String? = null
    private var adminProductListGeneration = 0L
    private var discoveryRequestGeneration = 0L
    private var buyerSearchGeneration = 0L
    private var buyerSearchJob: Job? = null
    private var homeRefreshJob: Job? = null
    private var productDetailJob: Job? = null
    private var productDetailSelection = 0L
    private var sellerProductCreationInFlight = false
    private var sellerMediaUploadJob: Job? = null
    private var sellerMediaUploadContext: Context? = null
    private val failedSellerMediaUris = mutableMapOf<String, List<Uri>>()

    init {
        refresh()
        syncShopIdentity()
    }

    fun refresh() {
        if (homeRefreshJob?.isActive == true) return
        _state.value = _state.value.copy(loading = true, error = false)
        homeRefreshJob = viewModelScope.launch {
            _state.value = repository.loadHome()
        }
    }

    fun syncShopIdentity() {
        ensureActiveAccountState()
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

    fun loadProductDetail(product: ShopProductDto) {
        productDetailJob?.cancel()
        val selection = ++productDetailSelection
        if (product.id.isBlank()) return
        _state.value = _state.value.copy(productDetails = _state.value.productDetails - product.id)
        productDetailJob = viewModelScope.launch {
            val result = productRepository.detail(product.id)
            if (!isCurrentDetailResponse(selection, productDetailSelection) || !isActive) return@launch
            result.onSuccess { detail ->
                if (detail.id != product.id) {
                    _state.value = _state.value.copy(message = "Product details could not be refreshed.")
                    return@onSuccess
                }
                val catalog = _state.value.products.firstOrNull { it.id == product.id } ?: product
                val display = productDetailWithCatalogFallback(detail, catalog)
                _state.value = _state.value.copy(productDetails = _state.value.productDetails + (product.id to display))
            }.onFailure { _state.value = _state.value.copy(message = "Product details could not be refreshed.") }
        }
    }

    fun loadSellerCategoryAttributes(categoryId: String) {
        if (categoryId.isBlank() || _protectedState.value.sellerCategoryAttributes.containsKey(categoryId) ||
            categoryId in _protectedState.value.sellerCategoryLoadingIds) return
        val ownerUid = _protectedState.value.ownerUid
        _protectedState.value = _protectedState.value.copy(
            sellerCategoryLoadingIds = _protectedState.value.sellerCategoryLoadingIds + categoryId,
            sellerCategoryErrors = _protectedState.value.sellerCategoryErrors - categoryId,
        )
        viewModelScope.launch {
            val result = withTimeoutOrNull(15_000) {
                runCatching {
                    val lineage = sellerCategoryLineage(categoryId, _state.value.categories)
                    require(lineage.isNotEmpty()) { "The selected category is unavailable." }
                    mergeSellerCategoryAttributes(lineage.map { id -> categoryRepository.attributes(id).getOrThrow() })
                }
            }
            if (_protectedState.value.ownerUid != ownerUid) return@launch
            val current = _protectedState.value
            _protectedState.value = if (result == null) {
                current.copy(
                    sellerCategoryLoadingIds = current.sellerCategoryLoadingIds - categoryId,
                    sellerCategoryErrors = current.sellerCategoryErrors + (categoryId to "Category fields timed out. Check your connection and retry."),
                )
            } else result.fold(
                onSuccess = { attributes ->
                    current.copy(
                        sellerCategoryAttributes = current.sellerCategoryAttributes + (categoryId to attributes),
                        sellerCategoryLoadingIds = current.sellerCategoryLoadingIds - categoryId,
                        sellerCategoryErrors = current.sellerCategoryErrors - categoryId,
                    )
                },
                onFailure = {
                    current.copy(
                        sellerCategoryLoadingIds = current.sellerCategoryLoadingIds - categoryId,
                        sellerCategoryErrors = current.sellerCategoryErrors + (categoryId to "Category fields are temporarily unavailable. Retry to continue."),
                    )
                },
            )
        }
    }

    fun loadBuyerCategoryFilters(categoryId: String) {
        if (categoryId.isBlank()) return
        val current = _state.value
        val categorySlug = current.categories.firstOrNull { it.id == categoryId }?.slug
        val categoryProducts = current.products.filter { it.categoryId == categoryId || it.categorySlug == categorySlug }
        if (current.buyerCategoryAttributes.containsKey(categoryId) &&
            categoryProducts.all { current.productDetails.containsKey(it.id) }
        ) return
        _state.value = current.copy(buyerFilterLoadingCategoryId = categoryId)
        viewModelScope.launch {
            categoryRepository.attributes(categoryId)
                .onSuccess { attributes ->
                    val details = categoryProducts.mapNotNull { product ->
                        productRepository.detail(product.id).getOrNull()?.let { product.id to it }
                    }.toMap()
                    _state.value = _state.value.copy(
                        productDetails = _state.value.productDetails + details,
                        buyerCategoryAttributes = _state.value.buyerCategoryAttributes +
                            (categoryId to attributes.filter { it.filterable }),
                        buyerFilterLoadingCategoryId = null,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        buyerFilterLoadingCategoryId = null,
                        message = "Category filters are temporarily unavailable.",
                    )
                }
        }
    }

    fun searchProducts(
        categorySlugs: Set<String>,
        minimumPriceMinor: Int?,
        maximumPriceMinor: Int?,
        minimumRating: Int,
        inStockOnly: Boolean,
        discountedOnly: Boolean,
        order: String,
    ) {
        val generation = ++discoveryRequestGeneration
        _state.value = _state.value.copy(discoveryLoading = true)
        viewModelScope.launch {
            productRepository.search(categorySlugs, minimumPriceMinor, maximumPriceMinor, minimumRating, inStockOnly, discountedOnly, order)
                .onSuccess { products ->
                    if (generation == discoveryRequestGeneration) {
                        _state.value = _state.value.copy(discoveryProducts = products, discoveryLoading = false)
                    }
                }
                .onFailure {
                    if (generation == discoveryRequestGeneration) {
                        _state.value = _state.value.copy(discoveryLoading = false, message = "Products could not be filtered right now.")
                    }
                }
        }
    }

    internal fun searchBuyerCatalog(key: BuyerSearchKey) {
        val current = _buyerSearchState.value
        if (current.key == key && (current.loaded || current.loading)) return
        buyerSearchJob?.cancel()
        val generation = ++buyerSearchGeneration
        _buyerSearchState.value = BuyerSearchState(key = key, loading = true)
        fetchBuyerSearchPage(key, generation)
    }

    fun clearBuyerSearch() {
        if (_buyerSearchState.value.key == null) return
        buyerSearchJob?.cancel()
        buyerSearchGeneration++
        _buyerSearchState.value = BuyerSearchState()
    }

    fun loadNextBuyerSearchPage() {
        val current = _buyerSearchState.value
        if (!canLoadNextBuyerSearchPage(current)) return
        val key = current.key ?: return
        _buyerSearchState.value = current.copy(loading = true)
        fetchBuyerSearchPage(key, buyerSearchGeneration)
    }

    fun retryBuyerSearch() {
        val current = _buyerSearchState.value
        if (!canRetryBuyerSearch(current)) return
        val key = current.key ?: return
        if (!current.loaded) {
            buyerSearchJob?.cancel()
            val generation = ++buyerSearchGeneration
            _buyerSearchState.value = BuyerSearchState(key = key, loading = true)
            fetchBuyerSearchPage(key, generation)
        } else {
            _buyerSearchState.value = current.copy(error = null, loading = true)
            fetchBuyerSearchPage(key, buyerSearchGeneration)
        }
    }

    private fun fetchBuyerSearchPage(key: BuyerSearchKey, generation: Long) {
        val offset = _buyerSearchState.value.nextOffset
        buyerSearchJob = viewModelScope.launch {
            val result = productRepository.searchCatalogPage(
                term = key.term,
                categorySlug = key.categorySlug,
                inStockOnly = key.inStockOnly,
                order = key.sort.apiOrder,
                offset = offset,
                pageSize = BUYER_SEARCH_PAGE_SIZE,
            )
            if (!isCurrentBuyerSearchResponse(key, _buyerSearchState.value.key, generation, buyerSearchGeneration) || !isActive) return@launch
            result.onSuccess { page ->
                _buyerSearchState.value = appendBuyerSearchPage(_buyerSearchState.value, page)
            }.onFailure {
                _buyerSearchState.value = _buyerSearchState.value.copy(
                    loading = false,
                    error = "Products could not be loaded. Check your connection and retry.",
                )
            }
        }
    }

    fun clearProductSearch() {
        discoveryRequestGeneration++
        _state.value = _state.value.copy(discoveryProducts = null, discoveryLoading = false)
    }

    fun addToCart(product: ShopProductDto) = addToCart(product, null, 1)

    fun addToCart(product: ShopProductDto, variantId: String?, quantity: Int) {
        if (product.id.isBlank()) return
        viewModelScope.launch {
            setBusy(product.id, true)
            cartRepository.add(product.id, variantId, quantity)
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
        ensureActiveAccountState()
        val requestedOwner = _protectedState.value.ownerUid
        loadProtected { cartRepository.getCart().onSuccess { cart ->
            if (_protectedState.value.ownerUid != requestedOwner || identityRepository.activeUserId() != requestedOwner) return@onSuccess
            _protectedState.value = _protectedState.value.copy(cart = cart, cartAvailableQuantities = emptyMap(), error = null)
            _state.value = _state.value.copy(cartCount = ShopStateReducers.cartItemCount(cart), shopIdentityReady = true)
            cart.items.forEach { item ->
                viewModelScope.launch {
                    val detail = productRepository.detail(item.productId).getOrNull()
                    val available = cartQuantityLimit(item, detail)
                    if (available != null && _protectedState.value.ownerUid == requestedOwner && identityRepository.activeUserId() == requestedOwner &&
                        _protectedState.value.cart?.items?.any { it.id == item.id && it.productId == item.productId && it.variantId == item.variantId } == true) {
                        _protectedState.value = _protectedState.value.copy(
                            cartAvailableQuantities = _protectedState.value.cartAvailableQuantities + (item.id to available),
                        )
                    }
                }
            }
        } }
    }

    fun updateCartItem(itemId: String, quantity: Int) {
        if (!canStartCartMutation(_protectedState.value.busyIds, itemId)) return
        val item = _protectedState.value.cart?.items?.firstOrNull { it.id == itemId }
        if (item != null) {
            val available = _protectedState.value.cartAvailableQuantities[itemId]
            if (quantity > item.quantity && available != null && quantity > available) {
                _protectedState.value = _protectedState.value.copy(error = "Only $available ${if (available == 1) "item" else "items"} available")
                return
            }
            val message = cartQuantityLimitMessage(item, quantity, _state.value.productDetails[item.productId])
            if (message != null) {
                _protectedState.value = _protectedState.value.copy(error = message)
                return
            }
        }
        setBusy(CART_MUTATION_BUSY_ID, true)
        setBusy(itemId, true)
        viewModelScope.launch {
            try {
                val result = if (quantity <= 0) cartRepository.remove(itemId) else cartRepository.update(itemId, quantity)
                result.onSuccess { cart ->
                    _protectedState.value = _protectedState.value.copy(cart = cart, error = null)
                    _state.value = _state.value.copy(cartCount = ShopStateReducers.cartItemCount(cart))
                }.onFailure {
                    val message = cartQuantityFailureMessage(it)
                    _protectedState.value = _protectedState.value.copy(error = message, loading = false)
                    _state.value = _state.value.copy(message = message)
                }
            } finally {
                setBusy(itemId, false)
                setBusy(CART_MUTATION_BUSY_ID, false)
            }
        }
    }

    fun clearCart() {
        if (!canStartCartMutation(_protectedState.value.busyIds)) return
        setBusy(CART_MUTATION_BUSY_ID, true)
        setBusy(CART_CLEAR_BUSY_ID, true)
        viewModelScope.launch {
            try {
                val result = cartRepository.clear()
                result.onSuccess { cart ->
                    _protectedState.value = _protectedState.value.copy(cart = cart, error = null)
                    _state.value = _state.value.copy(cartCount = 0)
                }.onFailure { showProtectedError(it, "Couldn't clear your cart. Try again.") }
            } finally {
                setBusy(CART_CLEAR_BUSY_ID, false)
                setBusy(CART_MUTATION_BUSY_ID, false)
            }
        }
    }

    fun loadFavorites() {
        loadProtected { favoriteRepository.list().onSuccess { favorites ->
            _protectedState.value = _protectedState.value.copy(favoritesLoaded = true, favorites = favorites, error = null)
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

    fun saveAddress(request: ShopAddressRequest, onComplete: ((String?) -> Unit)? = null) {
        viewModelScope.launch {
            val owner = _protectedState.value.ownerUid
            val previousIds = _protectedState.value.addresses.map { it.id }.toSet()
            val result = addressRepository.save(request)
            result.onSuccess { addresses ->
                if (_protectedState.value.ownerUid != owner) {
                    onComplete?.invoke(null)
                    return@onSuccess
                }
                _protectedState.value = _protectedState.value.copy(addresses = addresses, error = null)
                _state.value = _state.value.copy(message = "Address saved")
                onComplete?.invoke(request.id?.takeIf { id -> addresses.any { it.id == id } }
                    ?: addresses.firstOrNull { it.id !in previousIds }?.id)
            }.onFailure {
                showProtectedError(it, "Couldn't save the address. Try again.")
                onComplete?.invoke(null)
            }
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
            _protectedState.value = _protectedState.value.copy(ordersLoaded = true, orders = orders, error = null)
        } }
    }

    fun loadBuyerReviews() {
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(buyerReviewsLoaded = false)
            runCatching {
                val current = _protectedState.value
                val orders = if (current.ordersLoaded) current.orders else orderRepository.list().getOrThrow()
                val completedOrders = orders.filter { order ->
                    order.status in listOf("delivered", "completed") || order.fulfillmentStatus == "delivered"
                }
                val payloads = completedOrders.mapNotNull { order -> orderRepository.reviews(order.id).getOrNull() }
                val productReviews = payloads.flatMap { it.productReviews }.distinctBy { it.id }
                val storeReviews = payloads.flatMap { it.storeReviews }.distinctBy { it.id }
                Triple(orders, productReviews, storeReviews)
            }.onSuccess { (orders, productReviews, storeReviews) ->
                _protectedState.value = _protectedState.value.copy(
                    ordersLoaded = true,
                    orders = orders,
                    buyerReviewsLoaded = true,
                    buyerReviews = ShopOrderReviewsDto(productReviews, storeReviews),
                    error = null,
                )
            }.onFailure {
                _protectedState.value = _protectedState.value.copy(buyerReviewsLoaded = true)
                showProtectedError(it, "Reviews are temporarily unavailable.")
            }
        }
    }

    fun loadOrderDetail(orderId: String) {
        if (orderId.isBlank()) return
        loadProtected { orderRepository.detail(orderId).onSuccess { order ->
            applySelectedOrder(order)
        } }
    }

    fun loadOrderDetailForGroup(orderGroupId: String, onResolved: ((String?) -> Unit)? = null) {
        if (orderGroupId.isBlank()) {
            onResolved?.invoke(null)
            return
        }
        loadProtected {
            orderRepository.detailForGroup(orderGroupId)
                .onSuccess { order ->
                    applySelectedOrder(order)
                    onResolved?.invoke(order.id)
                }
                .onFailure {
                    showProtectedError(it, "This order is no longer available.")
                    onResolved?.invoke(null)
                }
        }
    }

    private fun applySelectedOrder(order: com.adischat.app.feature.shop.data.ShopOrderDto) {
        _protectedState.value = _protectedState.value.copy(
            selectedOrder = order,
            selectedOrderIssues = order.issues,
            selectedOrderActions = order.postDeliveryActions,
            selectedOrderReviews = order.reviews,
            selectedRefundRequests = order.refundRequests,
            selectedExchangeRequests = order.exchangeRequests,
            error = null,
        )
        loadIssueOptions(order.id)
        loadPostDeliveryPayloads(order.id)
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
        ensureActiveAccountState()
        val ownerUid = _protectedState.value.ownerUid
        loadProtected {
            notificationRepository.list(MarketplaceNotificationContext.BUYER).onSuccess { notifications ->
                if (_protectedState.value.ownerUid == ownerUid) {
                    _protectedState.value = _protectedState.value.copy(notifications = notifications, error = null)
                }
            }
        }
        viewModelScope.launch {
            notificationRepository.unreadCount(MarketplaceNotificationContext.BUYER).onSuccess { count ->
                ensureActiveAccountState()
                if (_protectedState.value.ownerUid == ownerUid) {
                    _protectedState.value = _protectedState.value.copy(notificationUnreadCount = count)
                }
            }
        }
    }

    fun loadNotificationUnreadCount() {
        ensureActiveAccountState()
        viewModelScope.launch {
            val ownerUid = _protectedState.value.ownerUid
            notificationRepository.unreadCount(MarketplaceNotificationContext.BUYER).onSuccess { count ->
                ensureActiveAccountState()
                if (_protectedState.value.ownerUid == ownerUid) {
                    _protectedState.value = _protectedState.value.copy(notificationUnreadCount = count.coerceAtLeast(0))
                }
            }
        }
    }

    fun markAllNotificationsRead() {
        ensureActiveAccountState()
        val ownerUid = _protectedState.value.ownerUid
        viewModelScope.launch {
            notificationRepository.markAllRead(MarketplaceNotificationContext.BUYER)
                .onSuccess { count ->
                    ensureActiveAccountState()
                    if (_protectedState.value.ownerUid != ownerUid) return@onSuccess
                    _protectedState.value = ShopStateReducers.allNotificationsMarkedRead(
                        state = _protectedState.value,
                        serverUnreadCount = count,
                        readAt = Instant.now().toString(),
                    )
                }
                .onFailure { showProtectedError(it, "Couldn't update notifications.") }
        }
    }

    fun markNotificationRead(notificationId: String) {
        if (notificationId.isBlank()) return
        ensureActiveAccountState()
        val ownerUid = _protectedState.value.ownerUid
        viewModelScope.launch {
            notificationRepository.markRead(notificationId, MarketplaceNotificationContext.BUYER)
                .onSuccess { count ->
                    ensureActiveAccountState()
                    if (_protectedState.value.ownerUid != ownerUid) return@onSuccess
                    _protectedState.value = ShopStateReducers.notificationMarkedRead(
                        state = _protectedState.value,
                        notificationId = notificationId,
                        serverUnreadCount = count,
                        readAt = Instant.now().toString(),
                    )
                }
                .onFailure { showProtectedError(it, "Couldn't update this notification.") }
        }
    }

    fun loadSellerNotifications() {
        ensureActiveAccountState()
        val storeId = _protectedState.value.sellerBusiness?.selectedStore?.id?.takeIf { it.isNotBlank() }
        if (storeId == null && _protectedState.value.sellerApplication == null) {
            _protectedState.value = _protectedState.value.copy(
                sellerNotifications = emptyList(), sellerNotificationUnreadCount = 0, sellerNotificationStoreId = null,
            )
            return
        }
        val ownerUid = _protectedState.value.ownerUid
        if (_protectedState.value.sellerNotificationStoreId != storeId) {
            _protectedState.value = _protectedState.value.copy(
                sellerNotifications = emptyList(), sellerNotificationUnreadCount = 0, sellerNotificationStoreId = storeId,
            )
        }
        viewModelScope.launch {
            notificationRepository.list(MarketplaceNotificationContext.SELLER, storeId).onSuccess { notifications ->
                ensureActiveAccountState()
                if (_protectedState.value.ownerUid == ownerUid && _protectedState.value.sellerBusiness?.selectedStore?.id == storeId) {
                    _protectedState.value = _protectedState.value.copy(sellerNotifications = notifications)
                }
            }.onFailure { showProtectedError(it, "Couldn't load seller notifications.") }
            notificationRepository.unreadCount(MarketplaceNotificationContext.SELLER, storeId).onSuccess { count ->
                ensureActiveAccountState()
                if (_protectedState.value.ownerUid == ownerUid && _protectedState.value.sellerBusiness?.selectedStore?.id == storeId) {
                    _protectedState.value = _protectedState.value.copy(sellerNotificationUnreadCount = count.coerceAtLeast(0))
                }
            }
        }
    }

    fun markAllSellerNotificationsRead() {
        val storeId = _protectedState.value.sellerBusiness?.selectedStore?.id
        if (storeId == null && _protectedState.value.sellerApplication == null) return
        val ownerUid = _protectedState.value.ownerUid
        viewModelScope.launch {
            notificationRepository.markAllRead(MarketplaceNotificationContext.SELLER, storeId).onSuccess { count ->
                ensureActiveAccountState()
                if (_protectedState.value.ownerUid == ownerUid && _protectedState.value.sellerBusiness?.selectedStore?.id == storeId) {
                    val readAt = Instant.now().toString()
                    _protectedState.value = _protectedState.value.copy(
                        sellerNotificationUnreadCount = count,
                        sellerNotifications = _protectedState.value.sellerNotifications.map { it.copy(readAt = it.readAt ?: readAt) },
                    )
                }
            }.onFailure { showProtectedError(it, "Couldn't update seller notifications.") }
        }
    }

    fun markSellerNotificationRead(notificationId: String) {
        if (notificationId.isBlank()) return
        val storeId = _protectedState.value.sellerBusiness?.selectedStore?.id
        if (storeId == null && _protectedState.value.sellerApplication == null) return
        val ownerUid = _protectedState.value.ownerUid
        viewModelScope.launch {
            notificationRepository.markRead(notificationId, MarketplaceNotificationContext.SELLER, storeId).onSuccess { count ->
                ensureActiveAccountState()
                if (_protectedState.value.ownerUid == ownerUid && _protectedState.value.sellerBusiness?.selectedStore?.id == storeId) {
                    val readAt = Instant.now().toString()
                    _protectedState.value = _protectedState.value.copy(
                        sellerNotificationUnreadCount = count,
                        sellerNotifications = _protectedState.value.sellerNotifications.map {
                            if (it.id == notificationId) it.copy(readAt = it.readAt ?: readAt) else it
                        },
                    )
                }
            }.onFailure { showProtectedError(it, "Couldn't update seller notifications.") }
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

    fun buyNow(product: ShopProductDto) = buyNow(product, null, 1)

    fun buyNow(product: ShopProductDto, variantId: String?, quantity: Int) {
        if (product.id.isBlank()) return
        if (_protectedState.value.busyIds.contains(product.id) || _protectedState.value.loading) return
        viewModelScope.launch {
            setBusy(product.id, true)
            _protectedState.value = _protectedState.value.copy(loading = true, error = null, checkout = null)
            val selectionKey = "${product.id}:${variantId.orEmpty()}:$quantity"
            val idempotencyKey = buyNowIdempotencyKeys.getOrPut(selectionKey) {
                "android-buy-now-$selectionKey-${UUID.randomUUID()}"
            }
            checkoutRepository.createBuyNow(
                productId = product.id,
                variantId = variantId,
                quantity = quantity,
                idempotencyKey = idempotencyKey,
            )
                .onSuccess { checkout ->
                    _protectedState.value = _protectedState.value.copy(checkout = checkout, loading = false, error = null)
                }
                .onFailure {
                    buyNowIdempotencyKeys.remove(selectionKey)
                    showCheckoutError(it, "Checkout could not start. Please refresh your cart and try again.")
                }
            setBusy(product.id, false)
        }
    }

    fun chatSeller(product: ShopProductDto, variantId: String? = null) {
        if (product.id.isBlank()) return
        viewModelScope.launch {
            setBusy("chat-${product.id}", true)
            messageRepository.open(productId = product.id, variantId = variantId)
                .onSuccess { conversation ->
                    _protectedState.value = _protectedState.value.copy(
                        selectedConversation = conversation,
                        conversationMessages = emptyList(),
                        error = null,
                    )
                    loadConversationMessages(conversation.id)
                    _state.value = _state.value.copy(message = "Seller conversation opened")
                }
                .onFailure { showProtectedError(it, "Conversation could not be started.") }
            setBusy("chat-${product.id}", false)
        }
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
                    val orderMsg = if (order.paymentStatus == "paid") "Order created and verified (paid via voucher)." else "Order created. Payment is pending."
                    _state.value = _state.value.copy(message = orderMsg)
                    if (!com.adischat.app.BuildConfig.QA_BUILD) {
                        loadOrders()
                        loadCart()
                    }
                }
                .onFailure {
                    confirmIdempotencyKeys.remove(checkoutSessionId)
                    showProtectedError(it, "Order could not be created.")
                }
        }
    }

    fun applyCoupon(couponCode: String) {
        val checkoutId = _protectedState.value.checkout?.id.orEmpty()
        if (checkoutId.isBlank() || couponCode.isBlank() || _protectedState.value.loading) return
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(loading = true, error = null)
            checkoutRepository.applyCoupon(checkoutId, couponCode)
                .onSuccess { updated ->
                    _protectedState.value = _protectedState.value.copy(checkout = updated, loading = false, error = null)
                    _state.value = _state.value.copy(message = "Coupon applied successfully!")
                }
                .onFailure {
                    _protectedState.value = _protectedState.value.copy(loading = false, error = it.message ?: "Invalid or ineligible coupon")
                }
        }
    }

    fun applyVoucher(voucherCode: String) {
        val checkoutId = _protectedState.value.checkout?.id.orEmpty()
        if (checkoutId.isBlank() || voucherCode.isBlank() || _protectedState.value.loading) return
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(loading = true, error = null)
            checkoutRepository.applyVoucher(checkoutId, voucherCode)
                .onSuccess { updated ->
                    _protectedState.value = _protectedState.value.copy(checkout = updated, loading = false, error = null)
                    _state.value = _state.value.copy(message = "Voucher applied successfully!")
                }
                .onFailure {
                    _protectedState.value = _protectedState.value.copy(loading = false, error = it.message ?: "Invalid or expired voucher")
                }
        }
    }

    fun removeDiscount(discountType: String) {
        val checkoutId = _protectedState.value.checkout?.id.orEmpty()
        if (checkoutId.isBlank() || _protectedState.value.loading) return
        viewModelScope.launch {
            _protectedState.value = _protectedState.value.copy(loading = true, error = null)
            checkoutRepository.removeDiscount(checkoutId, discountType)
                .onSuccess { updated ->
                    _protectedState.value = _protectedState.value.copy(checkout = updated, loading = false, error = null)
                    _state.value = _state.value.copy(message = "Discount removed")
                }
                .onFailure {
                    _protectedState.value = _protectedState.value.copy(loading = false, error = it.message ?: "Failed to remove discount")
                }
        }
    }

    fun loadSellerStatus() {
        loadProtected { sellerRepository.status().onSuccess { (status, application) ->
            _protectedState.value = _protectedState.value.copy(sellerStatus = status, sellerApplication = application, error = null)
        } }
    }

    fun loadSellerOrders() {
        ensureActiveAccountState()
        val storeId = _protectedState.value.sellerBusiness?.selectedStore?.id ?: return
        val ownerUid = _protectedState.value.ownerUid
        val generation = ++sellerOrderRequestGeneration
        _protectedState.value = _protectedState.value.copy(
            sellerOrderGroups = emptyList(), sellerOrderStoreId = storeId, sellerOrdersLoaded = false,
        )
        viewModelScope.launch {
            sellerRepository.orderGroups(storeId)
                .onSuccess { groups ->
                    if (generation == sellerOrderRequestGeneration && _protectedState.value.ownerUid == ownerUid &&
                        _protectedState.value.sellerBusiness?.selectedStore?.id == storeId) {
                        _protectedState.value = _protectedState.value.copy(
                            sellerOrderGroups = groups, sellerOrderStoreId = storeId, sellerOrdersLoaded = true, error = null,
                        )
                    }
                }
                .onFailure {
                    if (generation == sellerOrderRequestGeneration && _protectedState.value.ownerUid == ownerUid &&
                        _protectedState.value.sellerBusiness?.selectedStore?.id == storeId) {
                        showProtectedError(it, "Seller orders could not be loaded.")
                    }
                }
        }
    }

    fun loadSellerProducts() {
        ensureActiveAccountState()
        val storeId = _protectedState.value.sellerBusiness?.selectedStore?.id
        if (storeId.isNullOrBlank()) {
            sellerProductRequestGeneration++
            _protectedState.value = _protectedState.value.copy(
                sellerProducts = emptyList(), sellerProductsStoreId = null,
                sellerProductsLoaded = false, selectedSellerProduct = null,
            )
            return
        }
        val requestGeneration = ++sellerProductRequestGeneration
        val ownerUid = _protectedState.value.ownerUid
        _protectedState.value = _protectedState.value.copy(
            sellerProducts = emptyList(), sellerProductsStoreId = storeId,
            sellerProductsLoaded = false, selectedSellerProduct = null,
        )
        viewModelScope.launch {
            sellerRepository.products(storeId)
                .onSuccess { products ->
                    if (requestGeneration == sellerProductRequestGeneration &&
                        _protectedState.value.ownerUid == ownerUid &&
                        _protectedState.value.sellerBusiness?.selectedStore?.id == storeId
                    ) {
                        _protectedState.value = _protectedState.value.copy(
                            sellerProducts = products, sellerProductsStoreId = storeId,
                            sellerProductsLoaded = true, error = null,
                        )
                    }
                }
                .onFailure {
                    if (requestGeneration == sellerProductRequestGeneration &&
                        _protectedState.value.ownerUid == ownerUid &&
                        _protectedState.value.sellerBusiness?.selectedStore?.id == storeId
                    ) showProtectedError(it, "Products for this store could not be loaded.")
                }
        }
    }

    fun loadSellerRequests() {
        ensureActiveAccountState()
        val storeId = _protectedState.value.sellerBusiness?.selectedStore?.id ?: return
        val ownerUid = _protectedState.value.ownerUid
        val generation = ++sellerRequestGeneration
        _protectedState.value = _protectedState.value.copy(sellerRequests = null, sellerRequestsStoreId = null)
        viewModelScope.launch {
            sellerRepository.requests(storeId)
                .onSuccess { requests ->
                    if (generation == sellerRequestGeneration && _protectedState.value.ownerUid == ownerUid &&
                        _protectedState.value.sellerBusiness?.selectedStore?.id == storeId) {
                        _protectedState.value = _protectedState.value.copy(sellerRequests = requests, sellerRequestsStoreId = storeId, error = null)
                    }
                }
                .onFailure {
                    if (generation == sellerRequestGeneration && _protectedState.value.ownerUid == ownerUid &&
                        _protectedState.value.sellerBusiness?.selectedStore?.id == storeId) {
                        showProtectedError(it, "Seller requests could not be loaded.")
                    }
                }
        }
    }

    fun loadSellerBusiness(storeId: String? = null, start: String? = null, end: String? = null) {
        ensureActiveAccountState()
        val requestGeneration = ++sellerBusinessRequestGeneration
        val ownerUid = _protectedState.value.ownerUid
        if (!storeId.isNullOrBlank() && storeId != _protectedState.value.sellerBusiness?.selectedStore?.id) {
            sellerMediaUploadJob?.cancel()
            failedSellerMediaUris.clear()
            sellerMediaUploadContext = null
            sellerProductRequestGeneration++
            sellerOrderRequestGeneration++
            sellerRequestGeneration++
            _protectedState.value = _protectedState.value.copy(
                sellerProducts = emptyList(), sellerProductsStoreId = null,
                sellerProductsLoaded = false, selectedSellerProduct = null, sellerMediaUpload = null,
                sellerOrderGroups = emptyList(), sellerOrderStoreId = null, sellerOrdersLoaded = false,
                sellerRequests = null, sellerRequestsStoreId = null, sellerConversations = emptyList(),
                selectedConversation = null, conversationMessages = emptyList(),
                sellerNotifications = emptyList(), sellerNotificationUnreadCount = 0,
                sellerNotificationStoreId = null,
            )
        }
        loadProtected {
            sellerRepository.business(storeId, start, end).onSuccess { business ->
                if (requestGeneration == sellerBusinessRequestGeneration && _protectedState.value.ownerUid == ownerUid) {
                    val previousStoreId = _protectedState.value.sellerBusiness?.selectedStore?.id
                    _protectedState.value = _protectedState.value.copy(sellerBusiness = business, error = null)
                    if (business.selectedStore?.id != previousStoreId) {
                        loadSellerProducts()
                        loadSellerMessages()
                        loadSellerNotifications()
                    }
                    loadSellerOrders()
                    loadSellerRequests()
                }
            }
        }
    }

    fun updateSellerStore(body: Map<String, Any?>) = runSellerBusinessMutation("store-update", body)

    fun saveSellerCoupon(body: Map<String, Any?>) = runSellerBusinessMutation("coupon-save", body)

    fun saveSellerCampaign(body: Map<String, Any?>) = runSellerBusinessMutation("campaign-save", body)

    fun saveSellerSettings(body: Map<String, Any?>) = runSellerBusinessMutation("settings-save", body)

    fun updateSellerInventory(storeId: String, inventoryId: String, quantity: Int, threshold: Int) {
        val busyKey = "inventory-$inventoryId"
        viewModelScope.launch {
            setBusy(busyKey, true)
            sellerRepository.updateInventory(
                ShopSellerInventoryUpdateRequest(
                    storeId = storeId,
                    requestId = "android-inventory-${UUID.randomUUID()}",
                    reason = "manual_adjustment",
                    items = listOf(ShopSellerInventoryChange(inventoryId, quantity, threshold)),
                ),
            ).onSuccess { loadSellerBusiness(storeId) }
                .onFailure { showProtectedError(it, "Inventory could not be updated.") }
            setBusy(busyKey, false)
        }
    }

    fun updateSellerInventoryBulk(storeId: String, changes: List<ShopSellerInventoryChange>) {
        if (changes.isEmpty()) return
        viewModelScope.launch {
            setBusy("inventory-bulk", true)
            sellerRepository.updateInventory(
                ShopSellerInventoryUpdateRequest(
                    storeId = storeId,
                    requestId = UUID.randomUUID().toString(),
                    reason = "bulk_adjustment",
                    items = changes,
                ),
            ).onSuccess { loadSellerBusiness(storeId) }
                .onFailure { showProtectedError(it, "Inventory could not be updated.") }
            setBusy("inventory-bulk", false)
        }
    }

    fun replySellerReview(storeId: String, reviewId: String, reviewType: String, reply: String) {
        viewModelScope.launch {
            setBusy("review-$reviewId", true)
            sellerRepository.replyToReview(ShopSellerReviewReplyRequest(storeId, reviewId, reviewType, reply))
                .onSuccess { loadSellerBusiness(storeId) }
                .onFailure { showProtectedError(it, "Review reply could not be saved.") }
            setBusy("review-$reviewId", false)
        }
    }

    fun saveSellerCustomerNote(storeId: String, orderGroupId: String, note: String) {
        viewModelScope.launch {
            sellerRepository.saveCustomerNote(ShopSellerCustomerNoteRequest(storeId, orderGroupId, note))
                .onSuccess { loadSellerBusiness(storeId) }
                .onFailure { showProtectedError(it, "Customer note could not be saved.") }
        }
    }

    fun uploadSellerStoreAsset(storeId: String, kind: String, bytes: ByteArray, mimeType: String) {
        viewModelScope.launch {
            setBusy("store-$kind", true)
            sellerRepository.uploadStoreAsset(storeId, kind, bytes, mimeType)
                .onSuccess { loadSellerBusiness(storeId) }
                .onFailure { showProtectedError(it, "Store image could not be uploaded.") }
            setBusy("store-$kind", false)
        }
    }

    private fun runSellerBusinessMutation(action: String, body: Map<String, Any?>) {
        viewModelScope.launch {
            setBusy("seller-$action", true)
            sellerRepository.businessAction(action, body)
                .onSuccess { loadSellerBusiness(body["storeId"] as? String) }
                .onFailure { showProtectedError(it, "Seller changes could not be saved.") }
            setBusy("seller-$action", false)
        }
    }

    fun loadSellerMessages(search: String = "", archived: Boolean = false) {
        ensureActiveAccountState()
        val requestOwnerUid = identityRepository.activeUserId()
        if (requestOwnerUid.isBlank()) return
        viewModelScope.launch {
            messageRepository.sellerConversations(search, archived)
                .onSuccess { (conversations, unreadCount) ->
                    if (identityRepository.activeUserId() != requestOwnerUid) {
                        ensureActiveAccountState()
                        return@onSuccess
                    }
                    _protectedState.value = _protectedState.value.copy(
                        sellerConversations = conversations,
                        sellerMessageUnreadCount = unreadCount,
                        error = null,
                    )
                }
                .onFailure {
                    if (identityRepository.activeUserId() == requestOwnerUid) {
                        showProtectedError(it, "Seller messages are temporarily unavailable.")
                    }
                }
        }
    }

    fun loadConversationMessages(conversationId: String) {
        fetchConversationMessages(conversationId, allowOpen = true)
    }

    fun openSellerCustomerConversation(orderGroupId: String) {
        if (orderGroupId.isBlank()) return
        viewModelScope.launch {
            messageRepository.open(orderGroupId = orderGroupId)
                .onSuccess { conversation -> loadConversationMessages(conversation.id) }
                .onFailure { showProtectedError(it, "Customer conversation could not be opened.") }
        }
    }

    fun refreshConversationMessages(conversationId: String) {
        fetchConversationMessages(conversationId, allowOpen = false)
    }

    private fun fetchConversationMessages(conversationId: String, allowOpen: Boolean) {
        if (conversationId.isBlank()) return
        ensureActiveAccountState()
        val requestOwnerUid = identityRepository.activeUserId()
        val refreshKey = "$requestOwnerUid:$conversationId"
        if (requestOwnerUid.isBlank() || !conversationRefreshesInFlight.add(refreshKey)) return
        viewModelScope.launch {
            try {
                setBusy("conversation-$conversationId", true)
                messageRepository.messages(conversationId)
                    .onSuccess { (conversation, messages) ->
                        if (identityRepository.activeUserId() != requestOwnerUid) {
                            ensureActiveAccountState()
                            return@onSuccess
                        }
                        val selectedId = _protectedState.value.selectedConversation?.id
                        if (!allowOpen && selectedId != conversationId) return@onSuccess
                        _protectedState.value = _protectedState.value.copy(
                            selectedConversation = conversation ?: _protectedState.value.selectedConversation,
                            conversationMessages = messages.distinctBy { message ->
                                message.id.ifBlank { message.clientMessageId }
                            },
                            error = null,
                        )
                        if ((conversation?.unreadCount ?: 0) > 0) {
                            messageRepository.update(conversationId, "read")
                        }
                        loadSellerMessages()
                    }
                    .onFailure {
                        if (identityRepository.activeUserId() == requestOwnerUid) {
                            showProtectedError(it, "Messages could not be loaded.")
                        }
                    }
            } finally {
                conversationRefreshesInFlight.remove(refreshKey)
                setBusy("conversation-$conversationId", false)
            }
        }
    }

    fun closeConversation() {
        _protectedState.value = _protectedState.value.copy(
            selectedConversation = null,
            conversationMessages = emptyList(),
        )
    }

    fun sendSellerMessage(conversationId: String, body: String) {
        if (conversationId.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            setBusy("send-$conversationId", true)
            messageRepository.send(conversationId, body)
                .onSuccess { message ->
                    val merged = (_protectedState.value.conversationMessages + message)
                        .distinctBy { item -> item.id.ifBlank { item.clientMessageId } }
                    _protectedState.value = _protectedState.value.copy(conversationMessages = merged, error = null)
                    loadSellerMessages()
                }
                .onFailure { showProtectedError(it, "Message could not be sent.") }
            setBusy("send-$conversationId", false)
        }
    }

    fun sendSellerAttachment(
        conversationId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
    ) {
        if (conversationId.isBlank() || bytes.isEmpty()) return
        viewModelScope.launch {
            setBusy("attachment-$conversationId", true)
            messageRepository.sendAttachment(conversationId, bytes, mimeType, fileName)
                .onSuccess { message ->
                    val merged = (_protectedState.value.conversationMessages + message)
                        .distinctBy { item -> item.id.ifBlank { item.clientMessageId } }
                    _protectedState.value = _protectedState.value.copy(conversationMessages = merged, error = null)
                    loadSellerMessages()
                }
                .onFailure { showProtectedError(it, "Attachment could not be sent.") }
            setBusy("attachment-$conversationId", false)
        }
    }

    fun updateSellerConversation(conversationId: String, action: String) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            messageRepository.update(conversationId, action)
                .onSuccess { conversation ->
                    if (conversation != null && action !in setOf("delete", "archive")) {
                        _protectedState.value = _protectedState.value.copy(selectedConversation = conversation)
                    }
                    if (action == "delete" || action == "archive") closeConversation()
                    loadSellerMessages()
                }
                .onFailure { showProtectedError(it, "Conversation could not be updated.") }
        }
    }

    fun updateConversationTyping(conversationId: String, typing: Boolean) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            messageRepository.update(conversationId, if (typing) "typing" else "stopped_typing")
        }
    }

    fun createSellerProductDraft() {
        if (sellerProductCreationInFlight) return
        val storeId = _protectedState.value.sellerBusiness?.selectedStore?.id
            ?.takeIf { it == _protectedState.value.sellerProductsStoreId && _protectedState.value.sellerProductsLoaded }
            ?: return
        sellerProductCreationInFlight = true
        val requestGeneration = sellerProductRequestGeneration
        viewModelScope.launch {
            try {
                _protectedState.value = _protectedState.value.copy(loading = true, error = null)
                sellerRepository.createProduct(storeId, "android-seller-product-create-${UUID.randomUUID()}")
                .onSuccess { product ->
                    if (requestGeneration != sellerProductRequestGeneration ||
                        _protectedState.value.sellerBusiness?.selectedStore?.id != storeId) return@onSuccess
                    _protectedState.value = _protectedState.value.copy(
                        selectedSellerProduct = product,
                        sellerProducts = listOf(product) + _protectedState.value.sellerProducts.filterNot { it.id == product.id },
                        loading = false,
                        error = null,
                    )
                    _state.value = _state.value.copy(message = "Draft product created")
                }
                .onFailure { showProtectedError(it, "Product creation is temporarily unavailable.") }
            } finally {
                sellerProductCreationInFlight = false
            }
        }
    }

    fun editSellerProduct(productId: String) {
        val storeId = _protectedState.value.sellerBusiness?.selectedStore?.id ?: return
        val product = _protectedState.value.sellerProducts.firstOrNull { it.id == productId && it.storeId == storeId } ?: return
        _protectedState.value = _protectedState.value.copy(selectedSellerProduct = product)
    }

    fun clearSellerProductEditor() {
        _protectedState.value = _protectedState.value.copy(selectedSellerProduct = null)
    }

    private fun selectedSellerProductStore(productId: String): String? {
        val state = _protectedState.value
        val storeId = state.sellerBusiness?.selectedStore?.id ?: return null
        return storeId.takeIf { state.sellerProductsLoaded && state.sellerProductsStoreId == it &&
            state.sellerProducts.any { product -> product.id == productId && product.storeId == it } }
    }

    fun saveSellerProduct(productId: String, payload: ShopSellerProductDraftPayload) {
        val storeId = selectedSellerProductStore(productId) ?: return
        viewModelScope.launch {
            setBusy(productId, true)
            sellerRepository.saveProduct(storeId, productId, payload, "android-seller-product-save-$productId-${UUID.randomUUID()}")
                .onSuccess { product ->
                    if (_protectedState.value.sellerBusiness?.selectedStore?.id != storeId) return@onSuccess
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

    fun saveSellerProductBeforeImagePick(productId: String, payload: ShopSellerProductDraftPayload, onSaved: () -> Unit) {
        val storeId = selectedSellerProductStore(productId) ?: return
        if (_protectedState.value.busyIds.contains(productId) || sellerMediaUploadJob?.isActive == true) return
        val ownerUid = _protectedState.value.ownerUid
        setBusy(productId, true)
        viewModelScope.launch {
            try {
                sellerRepository.saveProduct(storeId, productId, payload, "android-seller-product-before-picker-$productId-${UUID.randomUUID()}")
                    .onSuccess { product ->
                        if (_protectedState.value.ownerUid != ownerUid ||
                            _protectedState.value.sellerBusiness?.selectedStore?.id != storeId) return@onSuccess
                        val stillEditing = _protectedState.value.selectedSellerProduct?.id == productId
                        _protectedState.value = _protectedState.value.copy(
                            selectedSellerProduct = if (stillEditing) product else _protectedState.value.selectedSellerProduct,
                            sellerProducts = listOf(product) + _protectedState.value.sellerProducts.filterNot { it.id == product.id },
                            error = null,
                        )
                        if (stillEditing) onSaved()
                    }
                    .onFailure { showProtectedError(it, "Save your draft before adding images.") }
            } finally {
                setBusy(productId, false)
            }
        }
    }

    fun submitSellerProduct(productId: String) {
        val storeId = selectedSellerProductStore(productId) ?: return
        viewModelScope.launch {
            setBusy(productId, true)
            sellerRepository.submitProduct(storeId, productId, "android-seller-product-submit-$productId-${UUID.randomUUID()}")
                .onSuccess { product ->
                    if (_protectedState.value.sellerBusiness?.selectedStore?.id != storeId) return@onSuccess
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

    fun saveAndSubmitSellerProduct(productId: String, payload: ShopSellerProductDraftPayload) {
        val storeId = selectedSellerProductStore(productId) ?: return
        viewModelScope.launch {
            setBusy(productId, true)
            val saveResult = sellerRepository.saveProduct(
                storeId,
                productId,
                payload,
                "android-seller-product-save-before-submit-$productId-${UUID.randomUUID()}",
            )
            saveResult.onSuccess { saved ->
                if (_protectedState.value.sellerBusiness?.selectedStore?.id != storeId) return@onSuccess
                _protectedState.value = _protectedState.value.copy(
                    selectedSellerProduct = saved,
                    sellerProducts = listOf(saved) + _protectedState.value.sellerProducts.filterNot { it.id == saved.id },
                    error = null,
                )
                sellerRepository.submitProduct(storeId, productId, "android-seller-product-submit-$productId-${UUID.randomUUID()}")
                    .onSuccess { submitted ->
                        if (_protectedState.value.sellerBusiness?.selectedStore?.id != storeId) return@onSuccess
                        _protectedState.value = _protectedState.value.copy(
                            selectedSellerProduct = submitted,
                            sellerProducts = listOf(submitted) + _protectedState.value.sellerProducts.filterNot { it.id == submitted.id },
                            error = null,
                        )
                        _state.value = _state.value.copy(message = "Product saved and submitted for review")
                        loadAdminDashboard()
                    }
                    .onFailure { showProtectedError(it, "Product was saved, but submission needs attention.") }
            }.onFailure { showProtectedError(it, "Product could not be saved before submission.") }
            setBusy(productId, false)
        }
    }

    fun archiveSellerProduct(productId: String) {
        val storeId = selectedSellerProductStore(productId) ?: return
        viewModelScope.launch {
            setBusy(productId, true)
            sellerRepository.archiveProduct(storeId, productId, "android-seller-product-archive-$productId-${UUID.randomUUID()}")
                .onSuccess {
                    if (_protectedState.value.sellerBusiness?.selectedStore?.id != storeId) return@onSuccess
                    _protectedState.value = _protectedState.value.copy(
                        selectedSellerProduct = _protectedState.value.selectedSellerProduct?.takeUnless { it.id == productId },
                        sellerProducts = _protectedState.value.sellerProducts.filterNot { it.id == productId },
                        error = null,
                    )
                    _state.value = _state.value.copy(message = "Product removed from your store")
                }
                .onFailure { showProtectedError(it, "Product removal is temporarily unavailable.") }
            setBusy(productId, false)
        }
    }

    fun uploadSellerProductMediaBatch(productId: String, uris: List<Uri>, context: Context, altText: String) {
        val storeId = selectedSellerProductStore(productId) ?: return
        if (sellerMediaUploadJob?.isActive == true) return
        val uploadedCount = _protectedState.value.selectedSellerProduct?.takeIf { it.id == productId }?.images?.size ?: 0
        val selectedUris = eligibleSellerMediaSelection(uris, uploadedCount)
        if (selectedUris.isEmpty()) return
        val ownerUid = _protectedState.value.ownerUid
        sellerMediaUploadContext = context.applicationContext
        failedSellerMediaUris.remove(productId)
        _protectedState.value = _protectedState.value.copy(
            sellerMediaUpload = com.adischat.app.feature.shop.data.ShopSellerMediaUploadState(productId, selectedUris.size),
        )
        sellerMediaUploadJob = viewModelScope.launch {
            var completed = 0
            val failed = mutableListOf<Uri>()
            try {
                for (uri in selectedUris) {
                    if (!isActive || _protectedState.value.ownerUid != ownerUid ||
                        _protectedState.value.sellerBusiness?.selectedStore?.id != storeId) break
                    val processed = try {
                        withContext(Dispatchers.IO) { processSellerProductImage(context.applicationContext, uri) }
                    } catch (error: Exception) {
                        null
                    }
                    val uploaded = processed?.let { image ->
                        sellerRepository.uploadProductMedia(
                            productId = productId,
                            bytes = image.bytes,
                            mimeType = image.mimeType,
                            width = image.width,
                            height = image.height,
                            altText = altText,
                            requestId = "android-seller-product-media-$productId-${UUID.randomUUID()}",
                        ).isSuccess
                    } == true
                    if (uploaded) completed++ else failed += uri
                    _protectedState.value = _protectedState.value.copy(
                        sellerMediaUpload = com.adischat.app.feature.shop.data.ShopSellerMediaUploadState(
                            productId, selectedUris.size, completed, failed.size,
                        ),
                    )
                }
                if (_protectedState.value.ownerUid == ownerUid &&
                    _protectedState.value.sellerBusiness?.selectedStore?.id == storeId) {
                    sellerRepository.products(storeId).onSuccess { products ->
                        val refreshed = products.firstOrNull { it.id == productId }
                        val stillEditing = _protectedState.value.selectedSellerProduct?.id == productId
                        _protectedState.value = _protectedState.value.copy(
                            sellerProducts = products,
                            selectedSellerProduct = if (stillEditing) refreshed ?: _protectedState.value.selectedSellerProduct
                                else _protectedState.value.selectedSellerProduct,
                            error = null,
                        )
                    }
                    failedSellerMediaUris[productId] = failed
                    _protectedState.value = _protectedState.value.copy(
                        sellerMediaUpload = com.adischat.app.feature.shop.data.ShopSellerMediaUploadState(
                            productId, selectedUris.size, completed, failed.size, uploading = false,
                        ),
                    )
                }
            } finally {
                sellerMediaUploadJob = null
            }
        }
    }

    fun retrySellerProductMedia(productId: String) {
        val uris = failedSellerMediaUris[productId].orEmpty()
        if (uris.isEmpty()) return
        val context = sellerMediaUploadContext ?: return
        uploadSellerProductMediaBatch(productId, uris, context, _protectedState.value.selectedSellerProduct?.title.orEmpty())
    }

    fun deleteSellerProductMedia(productId: String, imageId: String) {
        val storeId = selectedSellerProductStore(productId) ?: return
        if (imageId.isBlank()) return
        viewModelScope.launch {
            setBusy("media-$productId", true)
            sellerRepository.deleteProductMedia(storeId, productId, imageId)
                .onSuccess { product ->
                    if (product.storeId != storeId || _protectedState.value.sellerBusiness?.selectedStore?.id != storeId) return@onSuccess
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
        val storeId = selectedSellerProductStore(productId) ?: return
        if (imageIds.isEmpty() || primaryImageId.isBlank()) return
        viewModelScope.launch {
            setBusy("media-$productId", true)
            sellerRepository.reorderProductMedia(productId, imageIds, primaryImageId)
                .onSuccess { product ->
                    if (product.storeId != storeId || _protectedState.value.sellerBusiness?.selectedStore?.id != storeId) return@onSuccess
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

    fun sellerOrderAction(orderGroupId: String, action: String, reason: String = "", reasonCode: String? = null, carrier: String? = null, trackingNumber: String? = null, expectedVersion: Int? = null) {
        if (orderGroupId.isBlank()) return
        val storeId = _protectedState.value.sellerBusiness?.selectedStore?.id ?: return
        if (!_protectedState.value.sellerOrdersLoaded || _protectedState.value.sellerOrderStoreId != storeId ||
            _protectedState.value.sellerOrderGroups.none { it.id == orderGroupId && it.storeId == storeId }) return
        viewModelScope.launch {
            setBusy(orderGroupId, true)
            sellerRepository.transition(storeId, orderGroupId, action, reason, reasonCode, carrier, trackingNumber, expectedVersion)
                .onSuccess { groups ->
                    if (_protectedState.value.sellerBusiness?.selectedStore?.id != storeId) return@onSuccess
                    _protectedState.value = _protectedState.value.copy(sellerOrderGroups = groups, error = null)
                    _state.value = _state.value.copy(message = "Seller order updated")
                    loadOrders()
                    loadSellerOrders()
                }
                .onFailure {
                    if (_protectedState.value.sellerBusiness?.selectedStore?.id != storeId) return@onFailure
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

    fun saveSellerApplicationDraft(request: ShopSellerApplicationRequest) {
        if (_protectedState.value.busyIds.contains("seller-application")) return
        viewModelScope.launch {
            setBusy("seller-application", true)
            sellerRepository.saveApplicationDraft(request.copy(requestId = "android-seller-draft-${UUID.randomUUID()}"))
                .onSuccess { (status, application) ->
                    _protectedState.value = _protectedState.value.copy(sellerStatus = status, sellerApplication = application, error = null)
                    _state.value = _state.value.copy(message = "Seller application draft saved")
                }
                .onFailure { showProtectedError(it, "Couldn't save seller application. Try again.") }
            setBusy("seller-application", false)
        }
    }

    fun submitCompleteSellerApplication(request: ShopSellerApplicationRequest) {
        if (_protectedState.value.busyIds.contains("seller-application")) return
        viewModelScope.launch {
            setBusy("seller-application", true)
            sellerRepository.submitApplication(request.copy(requestId = "android-seller-submit-${UUID.randomUUID()}"))
                .onSuccess { (status, application) ->
                    _protectedState.value = _protectedState.value.copy(sellerStatus = status, sellerApplication = application, error = null)
                    _state.value = _state.value.copy(message = "Seller application submitted")
                }
                .onFailure { showProtectedError(it, "Couldn't submit seller application. Complete required fields and try again.") }
            setBusy("seller-application", false)
        }
    }

    fun respondSellerApplicationInfoRequest(message: String) {
        if (_protectedState.value.busyIds.contains("seller-application")) return
        viewModelScope.launch {
            setBusy("seller-application", true)
            sellerRepository.respondApplicationInfoRequest(message)
                .onSuccess { (status, application) ->
                    _protectedState.value = _protectedState.value.copy(sellerStatus = status, sellerApplication = application, error = null)
                    _state.value = _state.value.copy(message = "Application response sent")
                }
                .onFailure { showProtectedError(it, "Couldn't send your response. Try again.") }
            setBusy("seller-application", false)
        }
    }

    fun uploadSellerApplicationDocument(documentType: String, bytes: ByteArray, fileName: String, mimeType: String) {
        if (_protectedState.value.busyIds.contains("seller-application-document")) return
        viewModelScope.launch {
            setBusy("seller-application-document", true)
            sellerRepository.uploadApplicationDocument(documentType, bytes, fileName, mimeType)
                .onSuccess { (status, application) ->
                    _protectedState.value = _protectedState.value.copy(sellerStatus = status, sellerApplication = application, error = null)
                    _state.value = _state.value.copy(message = "Document uploaded securely")
                }
                .onFailure { showProtectedError(it, "Document could not be uploaded. Use JPG, PNG or PDF up to 10 MB.") }
            setBusy("seller-application-document", false)
        }
    }

    fun removeSellerApplicationDocument(documentId: String) {
        if (documentId.isBlank() || _protectedState.value.busyIds.contains("seller-application-document")) return
        viewModelScope.launch {
            setBusy("seller-application-document", true)
            sellerRepository.removeApplicationDocument(documentId)
                .onSuccess { (status, application) ->
                    _protectedState.value = _protectedState.value.copy(sellerStatus = status, sellerApplication = application, error = null)
                    _state.value = _state.value.copy(message = "Document removed")
                }
                .onFailure { showProtectedError(it, "Document could not be removed. Try again.") }
            setBusy("seller-application-document", false)
        }
    }

    fun respondSellerRequest(kind: String, requestId: String, message: String, expectedVersion: Int) {
        val storeId = _protectedState.value.sellerBusiness?.selectedStore?.id ?: return
        if (_protectedState.value.sellerRequests == null || _protectedState.value.sellerRequestsStoreId != storeId) return
        viewModelScope.launch {
            setBusy(requestId, true)
            sellerRepository.respondToRequest(storeId, kind, requestId, message, expectedVersion)
                .onSuccess { requests ->
                    if (_protectedState.value.sellerBusiness?.selectedStore?.id != storeId) return@onSuccess
                    _protectedState.value = _protectedState.value.copy(sellerRequests = requests, sellerRequestsStoreId = storeId, error = null)
                    _state.value = _state.value.copy(message = "Response sent")
                }
                .onFailure {
                    if (_protectedState.value.sellerBusiness?.selectedStore?.id == storeId)
                        showProtectedError(it, "Couldn't send the response. Try again.")
                }
            setBusy(requestId, false)
        }
    }

    fun loadAdminSession() {
        ensureActiveAccountState()
        val accountUid = identityRepository.activeUserId()
        val requestGeneration = ++adminRequestGeneration
        val selectedTab = _protectedState.value.adminSelectedTab
        val allProductsQuery = _protectedState.value.allAdminProductsQuery
        _protectedState.value = ShopStateReducers.clearAdminWorkspace(_protectedState.value).copy(
            adminSelectedTab = selectedTab,
            allAdminProductsQuery = allProductsQuery,
            adminLoading = true,
            adminError = null,
        )
        viewModelScope.launch {
            adminRepository.session()
                .onSuccess { session ->
                    if (requestGeneration != adminRequestGeneration || accountUid != identityRepository.activeUserId()) return@onSuccess
                    _protectedState.value = _protectedState.value.copy(adminSession = session, adminLoading = false, adminError = null)
                    loadAdminDashboard()
                    loadAdminQueues()
                    loadAdminOrders()
                    loadAdminAuditLog()
                    loadAdminStores()
                }
                .onFailure { error ->
                    if (requestGeneration != adminRequestGeneration || accountUid != identityRepository.activeUserId()) return@onFailure
                    _protectedState.value = ShopStateReducers.clearAdminWorkspace(_protectedState.value).copy(
                        adminLoading = false,
                        adminError = ShopStateReducers.safeErrorMessage(error.message, "Admin access could not be verified."),
                    )
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
            val refunds = adminRepository.refunds().getOrDefault(emptyList())
            val exchanges = adminRepository.exchanges().getOrDefault(emptyList())
            val reviews = adminRepository.reviews().getOrDefault(emptyList())
            _protectedState.value = _protectedState.value.copy(
                adminSellerApplications = sellers,
                adminProducts = products,
                adminStores = stores,
                adminRefundRequests = refunds,
                adminExchangeRequests = exchanges,
                adminReviews = reviews,
                adminLoading = false,
                adminError = null,
            )
        }
    }

    fun loadAllAdminProducts(query: ShopAdminProductQuery) {
        val accountUid = identityRepository.activeUserId()
        val requestGeneration = ++adminProductListGeneration
        val safeQuery = query.normalized()
        _protectedState.value = _protectedState.value.copy(
            allAdminProductsLoading = true,
            allAdminProductsError = null,
            allAdminProducts = emptyList(),
        )
        viewModelScope.launch {
            adminRepository.allProducts(safeQuery)
                .onSuccess { page ->
                    if (requestGeneration != adminProductListGeneration || accountUid != identityRepository.activeUserId() || _protectedState.value.adminSession == null) return@onSuccess
                    _protectedState.value = _protectedState.value.copy(
                        allAdminProducts = page.products,
                        allAdminProductsLoading = false,
                        allAdminProductsError = null,
                        allAdminProductsOffset = safeQuery.offset,
                        allAdminProductsHasNext = page.hasNext,
                    )
                }
                .onFailure { error ->
                    if (requestGeneration != adminProductListGeneration || accountUid != identityRepository.activeUserId() || _protectedState.value.adminSession == null) return@onFailure
                    _protectedState.value = _protectedState.value.copy(
                        allAdminProductsLoading = false,
                        allAdminProductsError = ShopStateReducers.safeErrorMessage(error.message, "Couldn't load products."),
                        allAdminProductsHasNext = false,
                    )
                }
        }
    }

    fun selectAdminTab(tab: String) {
        _protectedState.value = _protectedState.value.copy(adminSelectedTab = tab)
    }

    fun loadPublicContentQueue(offset: Int = 0, kind: String? = null) {
        if (!ShopPublicContentReviewFlags.enabled ||
            "product.moderation.approve" !in _protectedState.value.adminSession?.permissions.orEmpty() ||
            kind !in listOf(null, "store", "product", "image")) return
        val generation = adminRequestGeneration
        val requestGeneration = ++publicContentRequestGeneration
        publicContentPreviewGeneration++
        publicContentQueueKind = kind
        val accountUid = identityRepository.activeUserId()
        publicContentRetryKeys.clear()
        _protectedState.value = _protectedState.value.copy(
            publicContentLoading = true, publicContentError = null,
            publicContentPreview = null, publicContentQueue = null,
        )
        viewModelScope.launch {
            adminRepository.publicContentQueue(offset.coerceAtLeast(0), kind)
                .onSuccess { queue ->
                    if (generation != adminRequestGeneration || requestGeneration != publicContentRequestGeneration || accountUid != identityRepository.activeUserId()) return@onSuccess
                    _protectedState.value = _protectedState.value.copy(
                        publicContentQueue = queue, publicContentOffset = offset.coerceAtLeast(0),
                        publicContentLoading = false, publicContentError = null,
                    )
                }
                .onFailure { error ->
                    if (generation != adminRequestGeneration || requestGeneration != publicContentRequestGeneration || accountUid != identityRepository.activeUserId()) return@onFailure
                    _protectedState.value = _protectedState.value.copy(
                        publicContentLoading = false,
                        publicContentError = ShopStateReducers.safeErrorMessage(error.message, "Couldn't load content for review."),
                    )
                }
        }
    }

    fun previewPublicContent(item: ShopPublicContentItemDto) {
        if (!ShopPublicContentReviewFlags.enabled || _protectedState.value.publicContentReviewing ||
            _protectedState.value.publicContentQueue?.items?.none {
                it.entityKind == item.entityKind && it.entityId == item.entityId &&
                    ShopQaModerationContract.sameRevision(item.entityKind, it.expectedRevision, item.expectedRevision)
            } != false) return
        val generation = adminRequestGeneration
        val previewGeneration = ++publicContentPreviewGeneration
        val accountUid = identityRepository.activeUserId()
        _protectedState.value = _protectedState.value.copy(publicContentLoading = true, publicContentError = null)
        viewModelScope.launch {
            adminRepository.publicContentPreview(item)
                .onSuccess { preview ->
                    if (generation != adminRequestGeneration || previewGeneration != publicContentPreviewGeneration ||
                        accountUid != identityRepository.activeUserId()) return@onSuccess
                    if (preview.entityId != item.entityId || preview.entityKind != item.entityKind ||
                        preview.targetId != item.targetId ||
                        !ShopQaModerationContract.sameRevision(item.entityKind, preview.expectedRevision, item.expectedRevision)) {
                        _protectedState.value = _protectedState.value.copy(
                            publicContentPreview = null, publicContentLoading = false,
                            publicContentError = "Content changed. Refresh before reviewing.",
                        )
                    } else {
                        publicContentRetryKeys.clear()
                        _protectedState.value = _protectedState.value.copy(publicContentPreview = preview, publicContentLoading = false)
                    }
                }
                .onFailure { error ->
                    if (generation != adminRequestGeneration || previewGeneration != publicContentPreviewGeneration ||
                        accountUid != identityRepository.activeUserId()) return@onFailure
                    _protectedState.value = _protectedState.value.copy(
                        publicContentLoading = false,
                        publicContentError = ShopStateReducers.safeErrorMessage(error.message, "Couldn't load the current content."),
                    )
                }
        }
    }

    fun closePublicContentPreview() {
        publicContentPreviewGeneration++
        publicContentRetryKeys.clear()
        _protectedState.value = _protectedState.value.copy(publicContentPreview = null)
    }

    fun decidePublicContent(decision: String) {
        val action = when (decision) { "approved" -> "approve"; "rejected" -> "reject"; else -> return }
        if (!ShopPublicContentReviewFlags.enabled ||
            _protectedState.value.publicContentReviewing) return
        val preview = _protectedState.value.publicContentPreview ?: return
        val request = runCatching { ShopQaModerationContract.decision(preview, action) }.getOrElse {
            _protectedState.value = _protectedState.value.copy(publicContentError = "Review details are incomplete. Refresh before reviewing.")
            return
        }
        val actionIdentity = "${preview.entityKind}:${preview.entityId}:${preview.expectedRevision}:$action"
        val key = publicContentRetryKeys.keyFor(actionIdentity)
        val generation = adminRequestGeneration
        val accountUid = identityRepository.activeUserId()
        _protectedState.value = _protectedState.value.copy(publicContentReviewing = true, publicContentError = null)
        viewModelScope.launch {
            adminRepository.reviewPublicContent(request, key).onSuccess { confirmed ->
                if (generation != adminRequestGeneration || accountUid != identityRepository.activeUserId()) return@onSuccess
                if (ShopQaModerationContract.matchesDecision(preview, action, confirmed)) {
                    publicContentRetryKeys.clear()
                    _protectedState.value = _protectedState.value.copy(publicContentPreview = null, publicContentReviewing = false)
                    loadPublicContentQueue(_protectedState.value.publicContentOffset, publicContentQueueKind)
                } else {
                    publicContentRetryKeys.clear()
                    _protectedState.value = _protectedState.value.copy(
                        publicContentReviewing = false, publicContentPreview = null,
                        publicContentError = "Content changed. Refresh before reviewing.",
                    )
                }
            }.onFailure { error ->
                if (generation != adminRequestGeneration || accountUid != identityRepository.activeUserId()) return@onFailure
                if (error.message?.contains("Content changed. Refresh") == true) {
                    publicContentRetryKeys.clear()
                    _protectedState.value = _protectedState.value.copy(publicContentPreview = null,
                        publicContentReviewing = false, publicContentError = "Content changed. Refresh before reviewing.")
                    return@onFailure
                }
                _protectedState.value = _protectedState.value.copy(
                    publicContentReviewing = false,
                    publicContentError = ShopStateReducers.safeErrorMessage(error.message, "Content review could not be saved."),
                )
            }
        }
    }

    fun setAllAdminProductsQuery(query: ShopAdminProductQuery) {
        _protectedState.value = _protectedState.value.copy(allAdminProductsQuery = query.normalized())
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

    fun adminReviewProduct(productId: String, action: String, reason: String, expectedSubmittedAt: String?) {
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
                    expectedSubmittedAt = expectedSubmittedAt,
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

    fun updateAdminRefund(requestId: String, action: String, reason: String) {
        if (requestId.isBlank()) return
        viewModelScope.launch {
            setBusy(requestId, true)
            adminRepository.updateRefund(requestId, action, reason)
                .onSuccess {
                    _state.value = _state.value.copy(message = "Refund updated")
                    loadAdminQueues()
                }
                .onFailure { showAdminError(it, "Refund update could not be saved.") }
            setBusy(requestId, false)
        }
    }

    fun updateAdminExchange(requestId: String, action: String, reason: String) {
        if (requestId.isBlank()) return
        viewModelScope.launch {
            setBusy(requestId, true)
            adminRepository.updateExchange(requestId, action, reason)
                .onSuccess {
                    _state.value = _state.value.copy(message = "Exchange updated")
                    loadAdminQueues()
                }
                .onFailure { showAdminError(it, "Exchange update could not be saved.") }
            setBusy(requestId, false)
        }
    }

    fun moderateAdminReview(reviewId: String, reviewType: String, action: String, reason: String) {
        if (reviewId.isBlank()) return
        viewModelScope.launch {
            setBusy(reviewId, true)
            adminRepository.moderateReview(reviewId, reviewType, action, reason)
                .onSuccess {
                    val actionLabel = action.replaceFirstChar { character ->
                        if (character.isLowerCase()) character.titlecase() else character.toString()
                    }
                    _state.value = _state.value.copy(message = "Review ${actionLabel.lowercase()}.")
                    loadAdminQueues()
                }
                .onFailure { showAdminError(it, "Review moderation could not be saved.") }
            setBusy(reviewId, false)
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun loadProtected(block: suspend () -> Result<*>) {
        ensureActiveAccountState()
        val requestGeneration = ++protectedRequestGeneration
        _protectedState.value = _protectedState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            block().onFailure {
                if (requestGeneration == protectedRequestGeneration) {
                    showProtectedError(it, "Couldn't load this right now. Try again.")
                }
            }
            if (requestGeneration == protectedRequestGeneration) {
                _protectedState.value = _protectedState.value.copy(loading = false)
            }
        }
    }

    private fun ensureActiveAccountState() {
        val activeUid = identityRepository.activeUserId()
        val current = _protectedState.value
        val accountState = ShopStateReducers.stateForActiveIdentity(current, activeUid)
        if (accountState != current) {
            sellerMediaUploadJob?.cancel()
            failedSellerMediaUris.clear()
            sellerMediaUploadContext = null
            visualSearchSession.reset()
            _protectedState.value = accountState
            _state.value = _state.value.copy(cartCount = 0, shopIdentityReady = false, message = null)
            buyNowIdempotencyKeys.clear()
            confirmIdempotencyKeys.clear()
            conversationRefreshesInFlight.clear()
            protectedRequestGeneration++
            sellerProductRequestGeneration++
            sellerBusinessRequestGeneration++
            sellerOrderRequestGeneration++
            sellerRequestGeneration++
            adminRequestGeneration++
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
