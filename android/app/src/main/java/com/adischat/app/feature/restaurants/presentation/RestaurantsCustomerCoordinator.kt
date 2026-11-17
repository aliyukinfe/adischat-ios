package com.adischat.app.feature.restaurants.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.feature.restaurants.data.BranchMenuItemOverrideDto
import com.adischat.app.feature.restaurants.data.MenuCategoryDto
import com.adischat.app.feature.restaurants.data.MenuItemDto
import com.adischat.app.feature.restaurants.data.MenuItemImageDto
import com.adischat.app.feature.restaurants.data.MenuOptionDto
import com.adischat.app.feature.restaurants.data.MenuOptionGroupDto
import com.adischat.app.feature.restaurants.data.OrderItemRecordDto
import com.adischat.app.feature.restaurants.data.OrderPaymentRecordDto
import com.adischat.app.feature.restaurants.data.OrderRecordDto
import com.adischat.app.feature.restaurants.data.OrderStatusEventDto
import com.adischat.app.feature.restaurants.data.RestaurantBranchDto
import com.adischat.app.feature.restaurants.data.RestaurantSummaryDto
import com.adischat.app.feature.restaurants.data.RestaurantsConfig
import com.adischat.app.feature.restaurants.data.RestaurantsCustomerDataSource
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RestaurantsErrorMapper
import com.adischat.app.feature.restaurants.data.RestaurantsFailure
import com.adischat.app.feature.restaurants.data.RestaurantsRepository
import com.adischat.app.feature.restaurants.data.RestaurantsSessionMemory
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

sealed interface RestaurantsCustomerDestination {
    data object Discover : RestaurantsCustomerDestination
    data class Profile(val restaurantId: String) : RestaurantsCustomerDestination
    data class Menu(val restaurantId: String) : RestaurantsCustomerDestination
    data object TableScan : RestaurantsCustomerDestination
    data object CartReview : RestaurantsCustomerDestination
    data class OrderTracking(val orderId: String) : RestaurantsCustomerDestination
}

/**
 * Unified immutable customer flow state spanning Discover -> Profile -> Branch Menu ->
 * Table QR -> Active Dining Session -> Cart -> Order Submission -> Order Tracking.
 */
data class RestaurantsCustomerFlowState(
    val destination: RestaurantsCustomerDestination = RestaurantsCustomerDestination.Discover,
    val activeAccountUid: String? = null,
    val isDineInEnabled: Boolean = RestaurantsConfig.isDineInEnabled(),
    val discoverState: RestaurantsDiscoverUiState = RestaurantsDiscoverUiState(),
    val profileState: RestaurantProfileUiState = RestaurantProfileUiState(),
    val menuState: RestaurantMenuUiState = RestaurantMenuUiState(),
    val activeMenuItemSheet: MenuItemSheetState? = null,
    val tableScanState: RestaurantTableScanUiState = RestaurantTableScanUiState(),
    val cartState: RestaurantCartUiState = RestaurantCartUiState(),
    val orderTrackingState: RestaurantOrderTrackingUiState = RestaurantOrderTrackingUiState(),
    val lastActionNotice: String? = null,
    val selectedWorkspaceTab: RestaurantWorkspaceTab = RestaurantWorkspaceTab.MENU,
    val workspaceOrders: List<RestaurantOrderSummaryUi> = emptyList(),
    val isOrdersLoading: Boolean = false,
)

private data class CachedCatalogBundle(
    val restaurantId: String,
    val branches: List<RestaurantBranchDto>,
    val categories: List<MenuCategoryDto>,
    val items: List<MenuItemDto>,
    val images: List<MenuItemImageDto>,
    val optionGroups: List<MenuOptionGroupDto>,
    val options: List<MenuOptionDto>,
)

/**
 * Isolated lifecycle-safe coordinator wiring R3C [RestaurantsCustomerDataSource] to
 * R3D/R3E pure UI states & reducers. Remains strictly inactive when feature flags are false
 * and isolates session/cart/order state per authenticated AdisChat UID.
 */
class RestaurantsCustomerCoordinator(
    private val scope: CoroutineScope,
    private val dataSource: RestaurantsCustomerDataSource,
    private val environmentProvider: () -> RestaurantsEnvironment = { RestaurantsConfig.currentEnvironment },
    private val accountUidProvider: () -> String? = {
        try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
    },
    private val uuidGenerator: () -> String = { UUID.randomUUID().toString() },
    private val trackingPollIntervalMs: Long = 5_000L,
) {
    private val flowEpoch = AtomicLong(0L)
    private var inFlightCatalogJob: Job? = null
    private var inFlightQrJob: Job? = null
    private var inFlightOrderJob: Job? = null
    private var inFlightTrackingJob: Job? = null
    private var inFlightTrackingLoopJob: Job? = null
    private var inFlightOrdersJob: Job? = null
    private var cachedCatalogBundle: CachedCatalogBundle? = null
    private var activeTrackingVisibleOrderId: String? = null
    private var isTrackingForegrounded: Boolean = false

    private val _state = MutableStateFlow(createInitialFlowState())
    val state: StateFlow<RestaurantsCustomerFlowState> = _state.asStateFlow()

    private fun createInitialFlowState(): RestaurantsCustomerFlowState {
        val env = environmentProvider()
        val uid = accountUidProvider()?.takeIf { it.isNotBlank() }
        val disabled = !env.isDineInEnabled
        return RestaurantsCustomerFlowState(
            destination = RestaurantsCustomerDestination.Discover,
            activeAccountUid = uid,
            isDineInEnabled = env.isDineInEnabled,
            discoverState = RestaurantsDiscoverUiState(
                loadState = if (disabled) RestaurantsLoadState.Disabled else RestaurantsLoadState.Empty,
                isDineInEnabled = env.isDineInEnabled,
            ),
            profileState = RestaurantProfileUiState(
                loadState = if (disabled) RestaurantsLoadState.Disabled else RestaurantsLoadState.Empty,
                isDineInEnabled = env.isDineInEnabled,
            ),
            menuState = RestaurantMenuUiState(
                loadState = if (disabled) RestaurantsLoadState.Disabled else RestaurantsLoadState.Empty,
                isDineInEnabled = env.isDineInEnabled,
            ),
            tableScanState = TableQrScanReducers.initialState(env),
            cartState = RestaurantCartUiState(isDineInEnabled = env.isDineInEnabled),
            orderTrackingState = RestaurantOrderTrackingUiState(
                loadState = if (disabled) RestaurantsLoadState.Disabled else RestaurantsLoadState.Empty,
                isDineInEnabled = env.isDineInEnabled,
            ),
        )
    }

    // ========================================================================
    // Account switch & sign-out boundary
    // ========================================================================

    fun reconcileAccountState() {
        val currentUid = accountUidProvider()?.takeIf { it.isNotBlank() }
        val previousUid = _state.value.activeAccountUid
        RestaurantsSessionMemory.reconcileAdisChatUid(currentUid)
        if (currentUid != previousUid) {
            onAccountChanged(currentUid)
        }
    }

    fun onAccountChanged(newAdisChatUid: String?) {
        val cleanUid = newAdisChatUid?.takeIf { it.isNotBlank() }
        flowEpoch.incrementAndGet()
        cancelAllInFlightJobs()
        cachedCatalogBundle = null
        RestaurantsSessionMemory.reconcileAdisChatUid(cleanUid)

        val env = environmentProvider()
        _state.update { current ->
            val disabled = !env.isDineInEnabled
            current.copy(
                destination = RestaurantsCustomerDestination.Discover,
                activeAccountUid = cleanUid,
                isDineInEnabled = env.isDineInEnabled,
                discoverState = RestaurantsDiscoverUiState(
                    loadState = if (disabled) RestaurantsLoadState.Disabled else RestaurantsLoadState.Empty,
                    isDineInEnabled = env.isDineInEnabled,
                ),
                profileState = RestaurantProfileUiState(
                    loadState = if (disabled) RestaurantsLoadState.Disabled else RestaurantsLoadState.Empty,
                    isDineInEnabled = env.isDineInEnabled,
                ),
                menuState = RestaurantMenuUiState(
                    loadState = if (disabled) RestaurantsLoadState.Disabled else RestaurantsLoadState.Empty,
                    isDineInEnabled = env.isDineInEnabled,
                ),
                activeMenuItemSheet = null,
                tableScanState = TableQrScanReducers.initialState(env),
                cartState = DineInCartReducers.reconcileAccountSwitch(
                    current.cartState.copy(isDineInEnabled = env.isDineInEnabled),
                    cleanUid,
                ),
                orderTrackingState = OrderTrackingReducers.clearForAccountSwitchOrSignOut()
                    .copy(
                        loadState = if (disabled) RestaurantsLoadState.Disabled else RestaurantsLoadState.Empty,
                        isDineInEnabled = env.isDineInEnabled,
                    ),
                selectedWorkspaceTab = RestaurantWorkspaceTab.MENU,
                workspaceOrders = emptyList(),
                isOrdersLoading = false,
                lastActionNotice = if (cleanUid == null) {
                    "Signed out of AdisChat. Restaurant session cleared."
                } else {
                    "Switched AdisChat account. Previous restaurant session cleared."
                },
            )
        }
    }

    // ========================================================================
    // Discover -> Profile -> Branch Menu
    // ========================================================================

    fun loadDiscover() {
        val env = environmentProvider()
        if (!env.isDineInEnabled) {
            _state.update {
                it.copy(
                    destination = RestaurantsCustomerDestination.Discover,
                    isDineInEnabled = false,
                    discoverState = it.discoverState.copy(
                        loadState = RestaurantsLoadState.Disabled,
                        allRestaurants = emptyList(),
                        isDineInEnabled = false,
                    ),
                )
            }
            return
        }

        val uid = requireAuthenticatedUid() ?: return
        val startEpoch = flowEpoch.get()
        inFlightCatalogJob?.cancel()

        _state.update {
            it.copy(
                destination = RestaurantsCustomerDestination.Discover,
                isDineInEnabled = true,
                discoverState = it.discoverState.copy(
                    loadState = RestaurantsLoadState.Loading,
                    isDineInEnabled = true,
                ),
                lastActionNotice = null,
            )
        }

        inFlightCatalogJob = scope.launch {
            val result = dataSource.listLiveRestaurants()
            if (!isEpochAndUserStillValid(startEpoch, uid)) return@launch

            val mapped = result.map { rows ->
                rows.map { dto ->
                    mapRestaurantCard(dto = dto, supabaseUrl = env.supabaseUrl)
                }
            }
            val nextDiscover = RestaurantsUiReducers.buildDiscoverStateFromLiveResult(
                environment = env,
                liveResult = mapped,
            ).copy(
                searchQuery = _state.value.discoverState.searchQuery,
                selectedCuisineFilter = _state.value.discoverState.selectedCuisineFilter,
            )

            _state.update { current ->
                if (!isEpochAndUserStillValid(startEpoch, uid)) current
                else current.copy(discoverState = nextDiscover)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(discoverState = it.discoverState.copy(searchQuery = query)) }
    }

    fun selectCuisineFilter(cuisine: String) {
        _state.update { it.copy(discoverState = it.discoverState.copy(selectedCuisineFilter = cuisine)) }
    }

    fun openRestaurantProfile(restaurantId: String) {
        val env = environmentProvider()
        if (!env.isDineInEnabled) {
            _state.update {
                it.copy(
                    destination = RestaurantsCustomerDestination.Profile(restaurantId),
                    profileState = RestaurantProfileUiState(
                        loadState = RestaurantsLoadState.Disabled,
                        isDineInEnabled = false,
                    ),
                )
            }
            return
        }
        val uid = requireAuthenticatedUid() ?: return
        val startEpoch = flowEpoch.get()
        inFlightCatalogJob?.cancel()

        _state.update {
            it.copy(
                destination = RestaurantsCustomerDestination.Profile(restaurantId),
                profileState = RestaurantProfileUiState(
                    loadState = RestaurantsLoadState.Loading,
                    isDineInEnabled = true,
                ),
                lastActionNotice = null,
            )
        }

        inFlightCatalogJob = scope.launch {
            val (restRes, branchesRes) = coroutineScope {
                val r = async { dataSource.getRestaurantById(restaurantId) }
                val b = async { dataSource.listRestaurantBranches(restaurantId) }
                r.await() to b.await()
            }
            if (!isEpochAndUserStillValid(startEpoch, uid)) return@launch

            val dto = restRes.getOrElse { err ->
                val failure = RestaurantsErrorMapper.mapThrowable(err)
                _state.update {
                    if (!isEpochAndUserStillValid(startEpoch, uid)) it
                    else it.copy(
                        profileState = RestaurantProfileUiState(
                            loadState = RestaurantsLoadState.Error(failure.userMessage),
                            isDineInEnabled = true,
                        ),
                    )
                }
                return@launch
            }

            if (dto == null) {
                _state.update {
                    if (!isEpochAndUserStillValid(startEpoch, uid)) it
                    else it.copy(
                        profileState = RestaurantProfileUiState(
                            loadState = RestaurantsLoadState.Empty,
                            isDineInEnabled = true,
                        ),
                    )
                }
                return@launch
            }

            val branchDtos = branchesRes.getOrElse { err ->
                val failure = RestaurantsErrorMapper.mapThrowable(err)
                _state.update {
                    if (!isEpochAndUserStillValid(startEpoch, uid)) it
                    else it.copy(
                        profileState = RestaurantProfileUiState(
                            loadState = RestaurantsLoadState.Error(failure.userMessage),
                            isDineInEnabled = true,
                        ),
                    )
                }
                return@launch
            }

            val branchUis = branchDtos.map(::mapBranchUiModel)
            val cardUi = mapRestaurantCard(dto = dto, branches = branchUis, supabaseUrl = env.supabaseUrl)
            val selectedBranch = RestaurantsUiReducers.resolveSelectedBranch(branchUis, null)?.id

            _state.update { current ->
                if (!isEpochAndUserStillValid(startEpoch, uid)) current
                else current.copy(
                    profileState = RestaurantProfileUiState(
                        loadState = RestaurantsLoadState.Content,
                        restaurant = cardUi,
                        branches = branchUis,
                        selectedBranchId = selectedBranch,
                        isDineInEnabled = true,
                    ),
                )
            }
        }
    }

    fun selectProfileBranch(branchId: String) {
        _state.update { current ->
            current.copy(
                profileState = current.profileState.copy(selectedBranchId = branchId),
            )
        }
    }

    fun openRestaurantMenu(restaurantId: String, branchId: String? = null) {
        val env = environmentProvider()
        if (!env.isDineInEnabled) {
            _state.update {
                it.copy(
                    destination = RestaurantsCustomerDestination.Menu(restaurantId),
                    menuState = RestaurantMenuUiState(
                        loadState = RestaurantsLoadState.Disabled,
                        isBrowseOnly = true,
                        isDineInEnabled = false,
                    ),
                )
            }
            return
        }
        val uid = requireAuthenticatedUid() ?: return
        val startEpoch = flowEpoch.get()
        inFlightCatalogJob?.cancel()

        val knownRestaurantName = _state.value.profileState.restaurant
            ?.takeIf { it.id == restaurantId }?.brandName
            ?: _state.value.discoverState.allRestaurants.firstOrNull { it.id == restaurantId }?.brandName
            ?: ""

        _state.update {
            it.copy(
                destination = RestaurantsCustomerDestination.Menu(restaurantId),
                selectedWorkspaceTab = RestaurantWorkspaceTab.MENU,
                activeMenuItemSheet = null,
                menuState = RestaurantMenuUiState(
                    loadState = RestaurantsLoadState.Loading,
                    restaurantName = knownRestaurantName,
                    isBrowseOnly = true,
                    isDineInEnabled = true,
                ),
                lastActionNotice = null,
            )
        }

        inFlightCatalogJob = scope.launch {
            val bundleResult = loadCatalogBundle(restaurantId)
            if (!isEpochAndUserStillValid(startEpoch, uid)) return@launch

            val (restaurantDto, bundle) = bundleResult.getOrElse { err ->
                val failure = RestaurantsErrorMapper.mapThrowable(err)
                _state.update { current ->
                    if (!isEpochAndUserStillValid(startEpoch, uid)) current
                    else current.copy(
                        menuState = current.menuState.copy(
                            loadState = RestaurantsLoadState.Error(failure.userMessage),
                        ),
                    )
                }
                return@launch
            }
            cachedCatalogBundle = bundle

            val branchUis = bundle.branches.map(::mapBranchUiModel)
            val resolvedBranch = RestaurantsUiReducers.resolveSelectedBranch(
                branches = branchUis,
                selectedBranchId = branchId ?: _state.value.cartState.activeSession
                    ?.takeIf { it.restaurantId == restaurantId }?.branchId,
            )

            val overrides = if (resolvedBranch != null) {
                dataSource.listBranchOverrides(resolvedBranch.id).getOrElse { err ->
                    val failure = RestaurantsErrorMapper.mapThrowable(err)
                    _state.update { current ->
                        if (!isEpochAndUserStillValid(startEpoch, uid)) current
                        else current.copy(
                            menuState = current.menuState.copy(
                                loadState = RestaurantsLoadState.Error(failure.userMessage),
                            ),
                        )
                    }
                    return@launch
                }
            } else {
                emptyList()
            }
            if (!isEpochAndUserStillValid(startEpoch, uid)) return@launch

            val uiItems = buildMenuItemUiModels(
                bundle = bundle,
                overrides = overrides,
                supabaseUrl = env.supabaseUrl,
            )
            val uiCategories = bundle.categories
                .filter { it.isActive }
                .sortedBy { it.sortOrder }
                .map { MenuCategoryUiModel(id = it.id, name = it.name, sortOrder = it.sortOrder) }

            val activeSession = _state.value.cartState.activeSession
            val isBrowseOnly = activeSession == null ||
                activeSession.restaurantId != restaurantId ||
                (resolvedBranch != null && activeSession.branchId != resolvedBranch.id)

            _state.update { current ->
                if (!isEpochAndUserStillValid(startEpoch, uid)) current
                else current.copy(
                    menuState = RestaurantMenuUiState(
                        loadState = if (uiItems.isEmpty()) RestaurantsLoadState.Empty else RestaurantsLoadState.Content,
                        restaurantName = restaurantDto?.name?.takeIf { it.isNotBlank() } ?: knownRestaurantName,
                        restaurantBrandTagline = resolvedBranch?.city ?: "Dine-in menu",
                        branches = branchUis,
                        selectedBranchId = resolvedBranch?.id,
                        categories = uiCategories,
                        selectedCategoryId = null,
                        items = uiItems,
                        isBrowseOnly = isBrowseOnly,
                        isDineInEnabled = true,
                    ),
                )
            }
        }
    }

    fun selectMenuBranch(branchId: String) {
        val env = environmentProvider()
        if (!env.isDineInEnabled) return
        val uid = requireAuthenticatedUid() ?: return
        val bundle = cachedCatalogBundle ?: return
        val startEpoch = flowEpoch.get()
        inFlightCatalogJob?.cancel()

        inFlightCatalogJob = scope.launch {
            val overridesRes = dataSource.listBranchOverrides(branchId)
            if (!isEpochAndUserStillValid(startEpoch, uid)) return@launch

            val overrides = overridesRes.getOrElse { err ->
                val failure = RestaurantsErrorMapper.mapThrowable(err)
                _state.update { current ->
                    if (!isEpochAndUserStillValid(startEpoch, uid)) current
                    else current.copy(
                        menuState = current.menuState.copy(
                            loadState = RestaurantsLoadState.Error(failure.userMessage),
                        ),
                    )
                }
                return@launch
            }

            val updatedItems = buildMenuItemUiModels(bundle, overrides, env.supabaseUrl)
            val activeSession = _state.value.cartState.activeSession
            val isBrowseOnly = activeSession == null ||
                activeSession.restaurantId != bundle.restaurantId ||
                activeSession.branchId != branchId

            _state.update { current ->
                if (!isEpochAndUserStillValid(startEpoch, uid)) current
                else current.copy(
                    activeMenuItemSheet = null,
                    menuState = current.menuState.copy(
                        loadState = if (updatedItems.isEmpty()) RestaurantsLoadState.Empty else RestaurantsLoadState.Content,
                        selectedBranchId = branchId,
                        items = updatedItems,
                        isBrowseOnly = isBrowseOnly,
                    ),
                )
            }
        }
    }

    fun selectMenuCategory(categoryId: String?) {
        _state.update {
            it.copy(menuState = it.menuState.copy(selectedCategoryId = categoryId))
        }
    }

    fun openMenuItem(item: MenuItemUiModel) {
        _state.update {
            val activeSession = it.cartState.activeSession
            val branchId = it.menuState.selectedBranchId
            val isBrowseOnly = activeSession == null ||
                activeSession.restaurantId != item.restaurantId ||
                (branchId != null && activeSession.branchId != branchId)
            it.copy(
                activeMenuItemSheet = MenuItemSheetState.initialFor(item)
                    .copy(isBrowseOnly = isBrowseOnly),
                lastActionNotice = null,
            )
        }
    }

    fun toggleSheetOption(groupId: String, optionId: String) {
        _state.update { current ->
            val sheet = current.activeMenuItemSheet ?: return@update current
            current.copy(activeMenuItemSheet = sheet.toggleOption(groupId, optionId))
        }
    }

    fun incrementSheetQuantity() {
        _state.update { current ->
            val sheet = current.activeMenuItemSheet ?: return@update current
            current.copy(activeMenuItemSheet = sheet.incrementQuantity())
        }
    }

    fun decrementSheetQuantity() {
        _state.update { current ->
            val sheet = current.activeMenuItemSheet ?: return@update current
            current.copy(activeMenuItemSheet = sheet.decrementQuantity())
        }
    }

    fun dismissMenuItemSheet() {
        _state.update { it.copy(activeMenuItemSheet = null) }
    }

    // ========================================================================
    // Table QR & Active Dining Session
    // ========================================================================

    fun openTableScan() {
        val env = environmentProvider()
        _state.update {
            it.copy(
                destination = RestaurantsCustomerDestination.TableScan,
                tableScanState = TableQrScanReducers.initialState(env),
                lastActionNotice = null,
            )
        }
    }

    fun submitScannedQrToken(rawToken: String) {
        val env = environmentProvider()
        if (!env.isDineInEnabled) {
            _state.update {
                it.copy(tableScanState = TableQrScanReducers.initialState(env))
            }
            return
        }
        val uid = requireAuthenticatedUid() ?: return
        val currentScan = _state.value.tableScanState
        if (inFlightQrJob?.isActive == true ||
            currentScan.phase is TableScanPhase.Resolving ||
            currentScan.phase is TableScanPhase.Confirmed
        ) {
            return
        }
        val nextScanState = TableQrScanReducers.onQrTokenCaptured(currentScan, rawToken)
        _state.update { it.copy(tableScanState = nextScanState) }
        if (nextScanState.phase !is TableScanPhase.Resolving) return

        executeQrResolution(rawToken = nextScanState.pendingQrToken, sessionCode = null, uid = uid)
    }

    fun updateSessionCodeInput(code: String) {
        _state.update {
            it.copy(tableScanState = TableQrScanReducers.onSessionCodeChanged(it.tableScanState, code))
        }
    }

    fun submitSessionCode() {
        val env = environmentProvider()
        if (!env.isDineInEnabled) return
        val uid = requireAuthenticatedUid() ?: return
        val nextScanState = TableQrScanReducers.beginSessionCodeSubmit(_state.value.tableScanState)
        _state.update { it.copy(tableScanState = nextScanState) }
        if (nextScanState.phase !is TableScanPhase.Resolving) return

        executeQrResolution(
            rawToken = nextScanState.pendingQrToken,
            sessionCode = nextScanState.sessionCodeInput.trim(),
            uid = uid,
        )
    }

    fun retryTableActivationCheck() {
        val env = environmentProvider()
        if (!env.isDineInEnabled) return
        val uid = requireAuthenticatedUid() ?: return
        val nextScanState = TableQrScanReducers.onRetryWaiterCheck(_state.value.tableScanState)
        _state.update { it.copy(tableScanState = nextScanState) }
        if (nextScanState.phase !is TableScanPhase.Resolving) return

        val code = nextScanState.sessionCodeInput.trim().ifEmpty { null }
        executeQrResolution(rawToken = nextScanState.pendingQrToken, sessionCode = code, uid = uid)
    }

    fun cancelTableScan() {
        inFlightQrJob?.cancel()
        _state.update {
            it.copy(
                tableScanState = TableQrScanReducers.onResetOrCancel(
                    it.tableScanState,
                    cancelledByUser = true,
                ),
            )
        }
    }

    fun resetTableScan() {
        inFlightQrJob?.cancel()
        _state.update {
            it.copy(
                tableScanState = TableQrScanReducers.onResetOrCancel(
                    it.tableScanState,
                    cancelledByUser = false,
                ),
            )
        }
    }

    private fun executeQrResolution(
        rawToken: String,
        sessionCode: String?,
        uid: String,
    ) {
        val startEpoch = flowEpoch.get()
        inFlightQrJob?.cancel()
        inFlightQrJob = scope.launch {
            val result = dataSource.resolveTableQr(token = rawToken, sessionCode = sessionCode)
            if (!isEpochAndUserStillValid(startEpoch, uid)) return@launch

            result.fold(
                onSuccess = { response ->
                    val confirmedScan = TableQrScanReducers.onResolveSuccess(
                        state = _state.value.tableScanState,
                        response = response,
                        ownerAdisChatUid = uid,
                    )
                    val boundSession = (confirmedScan.phase as? TableScanPhase.Confirmed)?.session
                    _state.update { current ->
                        if (!isEpochAndUserStillValid(startEpoch, uid)) return@update current
                        val updatedCart = if (boundSession != null) {
                            DineInCartReducers.bindActiveSession(current.cartState, boundSession)
                        } else {
                            current.cartState
                        }
                        val activeMenuMatchesSession = boundSession != null &&
                            cachedCatalogBundle?.restaurantId == boundSession.restaurantId &&
                            current.menuState.selectedBranchId == boundSession.branchId
                        current.copy(
                            tableScanState = confirmedScan,
                            cartState = updatedCart,
                            menuState = if (activeMenuMatchesSession) {
                                current.menuState.copy(isBrowseOnly = false)
                            } else {
                                current.menuState
                            },
                        )
                    }
                },
                onFailure = { throwable ->
                    val failure = RestaurantsErrorMapper.mapThrowable(throwable)
                    _state.update { current ->
                        if (!isEpochAndUserStillValid(startEpoch, uid)) return@update current
                        current.copy(
                            tableScanState = TableQrScanReducers.onResolveFailure(
                                current.tableScanState,
                                failure,
                            ),
                        )
                    }
                },
            )
        }
    }

    // ========================================================================
    // Cart & Dine-In Order Submission
    // ========================================================================

    fun addActiveSheetItemToCart(specialInstructions: String? = null): Boolean {
        val current = _state.value
        val sheet = current.activeMenuItemSheet ?: return false
        val branchId = current.menuState.selectedBranchId
            ?: current.cartState.activeSession?.branchId
            ?: ""

        return when (val outcome = DineInCartReducers.addItemFromSheet(
            state = current.cartState,
            sheetState = sheet,
            branchId = branchId,
            specialInstructions = specialInstructions,
        )) {
            is CartMutationOutcome.Updated -> {
                _state.update {
                    it.copy(
                        cartState = outcome.state,
                        activeMenuItemSheet = null,
                        lastActionNotice = null,
                    )
                }
                true
            }
            is CartMutationOutcome.Rejected -> {
                _state.update {
                    it.copy(lastActionNotice = outcome.reason)
                }
                false
            }
        }
    }

    fun updateCartItemQuantity(lineId: String, newQuantity: Int) {
        _state.update { current ->
            current.copy(cartState = DineInCartReducers.updateItemQuantity(current.cartState, lineId, newQuantity))
        }
    }

    fun removeCartItem(lineId: String) {
        _state.update { current ->
            current.copy(cartState = DineInCartReducers.removeItem(current.cartState, lineId))
        }
    }

    fun quickAddMenuItem(item: MenuItemUiModel): Boolean {
        if (requireAuthenticatedUid() == null) return false
        val current = _state.value
        val branchId = current.menuState.selectedBranchId
            ?: current.cartState.activeSession?.branchId
            ?: ""
        return when (val outcome = DineInCartReducers.quickAddItem(current.cartState, item, branchId)) {
            is QuickAddOutcome.Updated -> {
                _state.update {
                    it.copy(
                        cartState = outcome.state,
                        activeMenuItemSheet = null,
                        lastActionNotice = null,
                    )
                }
                true
            }

            is QuickAddOutcome.RequiresCustomization -> {
                _state.update {
                    it.copy(
                        activeMenuItemSheet = outcome.sheetState,
                        lastActionNotice = null,
                    )
                }
                false
            }

            is QuickAddOutcome.Rejected -> {
                _state.update { it.copy(lastActionNotice = outcome.reason) }
                false
            }
        }
    }

    fun quickDecrementMenuItem(menuItemId: String): Boolean {
        if (requireAuthenticatedUid() == null) return false
        val current = _state.value
        val branchId = current.menuState.selectedBranchId
            ?: current.cartState.activeSession?.branchId
            ?: ""
        return when (val outcome = DineInCartReducers.quickDecrementItem(current.cartState, menuItemId, branchId)) {
            is QuickDecrementOutcome.Updated -> {
                _state.update { it.copy(cartState = outcome.state, lastActionNotice = null) }
                true
            }

            is QuickDecrementOutcome.RequiresVariantResolution -> {
                _state.update {
                    it.copy(
                        destination = RestaurantsCustomerDestination.CartReview,
                        lastActionNotice = outcome.reason,
                    )
                }
                false
            }

            is QuickDecrementOutcome.Rejected -> {
                _state.update { it.copy(lastActionNotice = outcome.reason) }
                false
            }

            is QuickDecrementOutcome.NoOp -> false
        }
    }

    fun updateCustomerNotes(notes: String) {
        _state.update { current ->
            current.copy(cartState = DineInCartReducers.updateCustomerNotes(current.cartState, notes))
        }
    }

    fun selectOrderMode(mode: DineInOrderMode): Boolean {
        val current = _state.value
        return when (val outcome = DineInCartReducers.selectOrderMode(current.cartState, mode)) {
            is CartMutationOutcome.Updated -> {
                _state.update { it.copy(cartState = outcome.state, lastActionNotice = null) }
                true
            }

            is CartMutationOutcome.Rejected -> {
                _state.update { it.copy(lastActionNotice = outcome.reason) }
                false
            }
        }
    }

    fun openCartReview() {
        reconcileAccountState()
        _state.update { it.copy(destination = RestaurantsCustomerDestination.CartReview) }
    }

    fun submitDineInOrder() {
        val env = environmentProvider()
        if (!env.isDineInEnabled) {
            _state.update {
                it.copy(
                    cartState = it.cartState.copy(
                        isDineInEnabled = false,
                        checkoutPhase = CartCheckoutPhase.Failed(
                            "Dine-in ordering is disabled.",
                            it.cartState.pendingIdempotencyKey.orEmpty(),
                        ),
                    ),
                )
            }
            return
        }
        val uid = requireAuthenticatedUid() ?: return

        var capturedAttemptKey: String? = null
        var capturedRequestSession: ActiveDiningSessionUi? = null
        var capturedItemsSnapshot: List<DineInCartItemUi> = emptyList()
        var capturedCartBeforeLaunch: RestaurantCartUiState? = null

        _state.update { current ->
            if (!current.cartState.canSubmitOrder) {
                return@update current
            }
            val nextCart = DineInCartReducers.beginCheckoutAttempt(current.cartState, uuidGenerator)
            val submittingPhase = nextCart.checkoutPhase as? CartCheckoutPhase.Submitting
                ?: return@update current
            capturedAttemptKey = submittingPhase.idempotencyKey
            capturedRequestSession = nextCart.activeSession
            capturedItemsSnapshot = nextCart.items
            capturedCartBeforeLaunch = nextCart
            current.copy(cartState = nextCart, lastActionNotice = null)
        }

        val idempotencyKey = capturedAttemptKey ?: return
        val activeSession = capturedRequestSession ?: return
        val cartForRequest = capturedCartBeforeLaunch ?: return
        val request = DineInCartReducers.buildSubmitOrderRequest(cartForRequest, idempotencyKey) ?: return

        val startEpoch = flowEpoch.get()
        inFlightOrderJob = scope.launch {
            val result = dataSource.submitDineInOrder(
                diningSessionId = request.diningSessionId,
                branchId = request.branchId,
                items = request.items,
                customerNotes = request.customerNotes,
                idempotencyKey = request.idempotencyKey,
            )

            // Guard against logout or account switch while submission was in-flight
            if (!isEpochAndUserStillValid(startEpoch, uid)) return@launch

            result.fold(
                onSuccess = { orderResult ->
                    val tracking = OrderTrackingReducers.fromServerSubmitResult(
                        result = orderResult,
                        session = activeSession,
                        submittedItems = capturedItemsSnapshot,
                        isDineInEnabled = env.isDineInEnabled,
                    )
                    val itemsLabel = capturedItemsSnapshot.joinToString(", ") { "${it.quantity}x ${it.itemName}" }
                    val newSummary = RestaurantOrderSummaryUi(
                        orderId = orderResult.orderId,
                        orderNumber = orderResult.orderNumber.ifBlank { orderResult.orderId.take(8) },
                        restaurantId = activeSession.restaurantId,
                        restaurantName = activeSession.restaurantName,
                        branchId = activeSession.branchId,
                        branchName = activeSession.branchName,
                        tableLabel = activeSession.tableLabel,
                        diningSessionId = activeSession.diningSessionId,
                        customerUid = uid,
                        status = RestaurantOrderStatus.fromWireValue(orderResult.status),
                        paymentStatus = if (orderResult.paymentStatus.equals("PAID", ignoreCase = true)) {
                            PaymentStatusUi.PAID
                        } else {
                            PaymentStatusUi.UNPAID
                        },
                        itemsSummaryLabel = itemsLabel.ifBlank { "Order #${orderResult.orderNumber}" },
                        itemsCount = capturedItemsSnapshot.sumOf { it.quantity }.coerceAtLeast(1),
                        totalMinor = orderResult.totalMinor,
                        createdAtLabel = "Just now",
                        isSimulatedQa = false,
                        trackingSnapshot = tracking,
                    )
                    _state.update { current ->
                        if (!isEpochAndUserStillValid(startEpoch, uid)) return@update current
                        val updatedOrders = listOf(newSummary) +
                            current.workspaceOrders.filterNot { it.orderId == newSummary.orderId }
                        current.copy(
                            destination = RestaurantsCustomerDestination.OrderTracking(orderResult.orderId),
                            cartState = DineInCartReducers.onSubmitSucceeded(current.cartState, orderResult),
                            orderTrackingState = tracking,
                            selectedWorkspaceTab = RestaurantWorkspaceTab.MY_ORDERS,
                            workspaceOrders = updatedOrders,
                        )
                    }
                },
                onFailure = { throwable ->
                    val failure = RestaurantsErrorMapper.mapThrowable(throwable)
                    _state.update { current ->
                        if (!isEpochAndUserStillValid(startEpoch, uid)) return@update current
                        current.copy(
                            cartState = DineInCartReducers.onSubmitFailed(
                                state = current.cartState,
                                message = failure.userMessage,
                            ),
                        )
                    }
                },
            )
        }
    }

    // ========================================================================
    // Order Tracking & Refresh
    // ========================================================================

    fun openOrderTracking(orderId: String) {
        _state.update {
            it.copy(destination = RestaurantsCustomerDestination.OrderTracking(orderId))
        }
        refreshActiveOrder(orderId)
    }

    /**
     * Manages foreground-only polling for an actively displayed order tracking view.
     * Stops immediately when the tracking surface closes, the app leaves foreground,
     * the account changes/signs out, or the order reaches a terminal status.
     */
    fun setOrderTrackingVisibility(orderId: String?, isForegrounded: Boolean) {
        val env = environmentProvider()
        val targetOrderId = orderId?.trim()?.takeIf { it.isNotEmpty() }
        val uid = accountUidProvider()?.takeIf { it.isNotBlank() }

        if (!env.isDineInEnabled || !isForegrounded || targetOrderId == null || uid == null) {
            activeTrackingVisibleOrderId = null
            isTrackingForegrounded = false
            inFlightTrackingLoopJob?.cancel()
            inFlightTrackingLoopJob = null
            return
        }

        if (activeTrackingVisibleOrderId == targetOrderId &&
            isTrackingForegrounded &&
            inFlightTrackingLoopJob?.isActive == true
        ) {
            return
        }

        val currentStatus = resolveKnownOrderStatus(targetOrderId)
        if (currentStatus?.isTerminal == true) {
            activeTrackingVisibleOrderId = null
            isTrackingForegrounded = false
            inFlightTrackingLoopJob?.cancel()
            inFlightTrackingLoopJob = null
            return
        }

        activeTrackingVisibleOrderId = targetOrderId
        isTrackingForegrounded = true
        inFlightTrackingLoopJob?.cancel()
        val startEpoch = flowEpoch.get()
        val intervalMs = trackingPollIntervalMs.coerceAtLeast(25L)

        inFlightTrackingLoopJob = scope.launch {
            while (isActive && isEpochAndUserStillValid(startEpoch, uid)) {
                if (!isTrackingForegrounded || activeTrackingVisibleOrderId != targetOrderId) break
                if (resolveKnownOrderStatus(targetOrderId)?.isTerminal == true) break

                delay(intervalMs)

                if (!isActive || !isEpochAndUserStillValid(startEpoch, uid)) break
                if (!isTrackingForegrounded || activeTrackingVisibleOrderId != targetOrderId) break
                if (resolveKnownOrderStatus(targetOrderId)?.isTerminal == true) break

                if (inFlightTrackingJob?.isActive == true) {
                    inFlightTrackingJob?.join()
                    continue
                }

                val fetchJob = launchOrderTrackingRefresh(
                    orderId = targetOrderId,
                    startEpoch = startEpoch,
                    uid = uid,
                )
                inFlightTrackingJob = fetchJob
                fetchJob.join()
            }
        }
    }

    private fun resolveKnownOrderStatus(orderId: String): RestaurantOrderStatus? {
        val current = _state.value
        return when {
            current.orderTrackingState.orderId == orderId -> current.orderTrackingState.currentStatus
            else -> current.workspaceOrders.firstOrNull { it.orderId == orderId }?.status
        }
    }

    fun refreshActiveOrder(orderIdOverride: String? = null) {
        val env = environmentProvider()
        if (!env.isDineInEnabled) {
            _state.update {
                it.copy(
                    orderTrackingState = it.orderTrackingState.copy(
                        loadState = RestaurantsLoadState.Disabled,
                        isDineInEnabled = false,
                    ),
                )
            }
            return
        }
        val uid = requireAuthenticatedUid() ?: return
        val orderId = (orderIdOverride ?: _state.value.orderTrackingState.orderId).trim()
        if (orderId.isEmpty()) return

        val startEpoch = flowEpoch.get()
        inFlightTrackingJob?.cancel()
        inFlightTrackingJob = launchOrderTrackingRefresh(
            orderId = orderId,
            startEpoch = startEpoch,
            uid = uid,
        )
    }

    private fun launchOrderTrackingRefresh(
        orderId: String,
        startEpoch: Long,
        uid: String,
    ): Job {
        _state.update {
            it.copy(orderTrackingState = OrderTrackingReducers.onRefreshStarted(it.orderTrackingState))
        }

        return scope.launch {
            val detailsRes = dataSource.getOrderDetails(orderId)
            if (!isEpochAndUserStillValid(startEpoch, uid)) return@launch

            val order = detailsRes.getOrElse { err ->
                val failure = RestaurantsErrorMapper.mapThrowable(err)
                _state.update { current ->
                    if (!isEpochAndUserStillValid(startEpoch, uid)) return@update current
                    current.copy(
                        orderTrackingState = OrderTrackingReducers.onRefreshFailed(
                            current.orderTrackingState,
                            failure,
                        ),
                    )
                }
                return@launch
            }

            if (order == null) {
                _state.update { current ->
                    if (!isEpochAndUserStillValid(startEpoch, uid)) return@update current
                    current.copy(
                        orderTrackingState = OrderTrackingReducers.onRefreshFailed(
                            current.orderTrackingState,
                            RestaurantsFailure.NotFound("not_found", "Order could not be found."),
                        ),
                    )
                }
                return@launch
            }

            val (itemsRes, eventsRes, paymentsRes) = coroutineScope {
                val i = async { dataSource.listOrderItems(orderId) }
                val e = async { dataSource.listOrderStatusEvents(orderId) }
                val p = async { dataSource.listOrderPayments(orderId) }
                Triple(i.await(), e.await(), p.await())
            }
            if (!isEpochAndUserStillValid(startEpoch, uid)) return@launch

            val items: List<OrderItemRecordDto> = itemsRes.getOrElse { emptyList() }
            val events: List<OrderStatusEventDto> = eventsRes.getOrElse { emptyList() }
            val payments: List<OrderPaymentRecordDto> = paymentsRes.getOrElse { emptyList() }

            _state.update { current ->
                if (!isEpochAndUserStillValid(startEpoch, uid)) return@update current
                val updatedTracking = OrderTrackingReducers.applyServerSnapshot(
                    state = current.orderTrackingState,
                    order = order,
                    items = items,
                    events = events,
                    payments = payments,
                )
                val updatedOrders = current.workspaceOrders.map { existing ->
                    if (existing.orderId == updatedTracking.orderId) {
                        existing.copy(
                            status = updatedTracking.currentStatus,
                            paymentStatus = updatedTracking.paymentStatus,
                            totalMinor = updatedTracking.confirmedTotalMinor,
                            trackingSnapshot = updatedTracking,
                        )
                    } else {
                        existing
                    }
                }
                current.copy(
                    orderTrackingState = updatedTracking,
                    workspaceOrders = updatedOrders,
                )
            }
        }
    }

    fun selectWorkspaceTab(tab: RestaurantWorkspaceTab) {
        _state.update { current ->
            current.copy(selectedWorkspaceTab = tab, lastActionNotice = null)
        }
    }

    fun orderMoreFoodInRestaurant() {
        val current = _state.value
        val targetRestaurantId = current.cartState.activeSession?.restaurantId
            ?: current.menuState.branches.firstOrNull()?.restaurantId
            ?: when (val dest = current.destination) {
                is RestaurantsCustomerDestination.Menu -> dest.restaurantId
                is RestaurantsCustomerDestination.Profile -> dest.restaurantId
                else -> null
            }
        _state.update { state ->
            state.copy(
                destination = if (targetRestaurantId != null) {
                    RestaurantsCustomerDestination.Menu(targetRestaurantId)
                } else {
                    state.destination
                },
                selectedWorkspaceTab = RestaurantWorkspaceTab.MENU,
                lastActionNotice = null,
            )
        }
    }

    fun loadCustomerOrdersForRestaurant(restaurantId: String) {
        val env = environmentProvider()
        if (!env.isDineInEnabled || restaurantId.isBlank()) return
        val uid = requireAuthenticatedUid() ?: return
        val startEpoch = flowEpoch.get()
        inFlightOrdersJob?.cancel()
        _state.update { it.copy(isOrdersLoading = true) }

        inFlightOrdersJob = scope.launch {
            val res = dataSource.listCustomerOrdersForRestaurant(
                customerProfileId = uid,
                restaurantId = restaurantId,
            )
            if (!isEpochAndUserStillValid(startEpoch, uid)) return@launch

            val rows = res.getOrElse {
                _state.update { current ->
                    if (!isEpochAndUserStillValid(startEpoch, uid)) current
                    else current.copy(isOrdersLoading = false)
                }
                return@launch
            }

            val restaurantName = _state.value.discoverState.allRestaurants
                .firstOrNull { it.id == restaurantId }?.brandName
                ?: _state.value.menuState.restaurantName.ifBlank { "Restaurant" }
            val branchNames = _state.value.menuState.branches.associate { it.id to it.name }

            val mapped = rows
                .filter { it.customerProfileId == uid && it.restaurantId == restaurantId }
                .map { dto ->
                    RestaurantOrderIsolationReducers.mapOrderRecordToSummary(
                        dto = dto,
                        restaurantName = restaurantName,
                        branchName = branchNames[dto.branchId] ?: dto.branchId,
                        tableLabel = _state.value.cartState.activeSession
                            ?.takeIf { it.diningSessionId == dto.diningSessionId }
                            ?.tableLabel ?: "Dine-in table",
                    )
                }

            _state.update { current ->
                if (!isEpochAndUserStillValid(startEpoch, uid)) current
                else current.copy(
                    isOrdersLoading = false,
                    workspaceOrders = mapped,
                )
            }
        }
    }

    fun navigateTo(destination: RestaurantsCustomerDestination) {
        _state.update { it.copy(destination = destination, lastActionNotice = null) }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun requireAuthenticatedUid(): String? {
        val currentUid = accountUidProvider()?.takeIf { it.isNotBlank() }
        if (currentUid == null) {
            onAccountChanged(null)
            return null
        }
        if (currentUid != _state.value.activeAccountUid) {
            onAccountChanged(currentUid)
        }
        return currentUid
    }

    private fun isEpochAndUserStillValid(expectedEpoch: Long, expectedUid: String): Boolean {
        val liveUid = accountUidProvider()?.takeIf { it.isNotBlank() }
        return flowEpoch.get() == expectedEpoch && liveUid == expectedUid
    }

    private fun cancelAllInFlightJobs() {
        inFlightCatalogJob?.cancel()
        inFlightQrJob?.cancel()
        inFlightOrderJob?.cancel()
        inFlightTrackingLoopJob?.cancel()
        inFlightTrackingLoopJob = null
        activeTrackingVisibleOrderId = null
        isTrackingForegrounded = false
        inFlightTrackingJob?.cancel()
        inFlightOrdersJob?.cancel()
    }

    private suspend fun loadCatalogBundle(
        restaurantId: String,
    ): Result<Pair<RestaurantSummaryDto?, CachedCatalogBundle>> = runCatching {
        coroutineScope {
            val restDef = async { dataSource.getRestaurantById(restaurantId) }
            val branchDef = async { dataSource.listRestaurantBranches(restaurantId) }
            val catDef = async { dataSource.listMenuCategories(restaurantId) }
            val itemDef = async { dataSource.listMenuItems(restaurantId) }
            val imgDef = async { dataSource.listMenuItemImages(restaurantId) }
            val groupDef = async { dataSource.listMenuOptionGroups(restaurantId) }
            val optDef = async { dataSource.listMenuOptions(restaurantId) }

            val restaurant = restDef.await().getOrThrow()
            val bundle = CachedCatalogBundle(
                restaurantId = restaurantId,
                branches = branchDef.await().getOrThrow(),
                categories = catDef.await().getOrThrow(),
                items = itemDef.await().getOrThrow(),
                images = imgDef.await().getOrThrow(),
                optionGroups = groupDef.await().getOrThrow(),
                options = optDef.await().getOrThrow(),
            )
            restaurant to bundle
        }
    }

    private fun mapRestaurantCard(
        dto: RestaurantSummaryDto,
        branches: List<RestaurantBranchUiModel> = emptyList(),
        supabaseUrl: String,
    ): RestaurantCardUiModel {
        val primary = branches.firstOrNull { it.isDineInActive } ?: branches.firstOrNull()
        return RestaurantCardUiModel(
            id = dto.id,
            slug = dto.slug,
            brandName = dto.name,
            cuisineTag = dto.cuisineCategory?.trim()?.takeIf { it.isNotEmpty() } ?: "Restaurant",
            city = primary?.city ?: "Addis Ababa",
            primaryBranchName = primary?.name ?: "Main Branch",
            primaryAddress = primary?.address ?: "",
            description = dto.description,
            logoUrl = resolvePublicMediaUrl(supabaseUrl, dto.logoPath),
            coverUrl = resolvePublicMediaUrl(supabaseUrl, dto.coverPath),
            isOpenNowLabel = if (dto.isLiveOrderingEnabled) "Dine-in Live" else "Coming Soon",
            branchCount = branches.size.coerceAtLeast(1),
            isLiveOrderingEnabled = dto.isLiveOrderingEnabled,
        )
    }

    private fun mapBranchUiModel(dto: RestaurantBranchDto): RestaurantBranchUiModel =
        RestaurantBranchUiModel(
            id = dto.id,
            restaurantId = dto.restaurantId,
            code = dto.code,
            name = dto.name,
            address = dto.address,
            city = dto.city,
            openingHours = parseOpeningHours(dto.openingHoursJson),
            requireStaffSessionActivation = dto.requireStaffSessionActivation,
            requireSessionCode = dto.requireSessionCode,
            isDineInActive = dto.isDineInActive && dto.isActive,
        )

    private fun buildMenuItemUiModels(
        bundle: CachedCatalogBundle,
        overrides: List<BranchMenuItemOverrideDto>,
        supabaseUrl: String,
    ): List<MenuItemUiModel> {
        val overrideMap = overrides.associateBy { it.menuItemId }
        val imagesByItem = bundle.images.groupBy { it.menuItemId }
        val optionsByGroup = bundle.options.groupBy { it.optionGroupId }
        val groupsByItem = bundle.optionGroups.groupBy { it.menuItemId }

        return bundle.items
            .filter { it.isActive }
            .sortedWith(compareBy<MenuItemDto> { it.sortOrder }.thenBy { it.name })
            .map { dto ->
                val override = overrideMap[dto.id]
                val tags = buildList {
                    if (dto.isVeg) add("Vegetarian")
                    if (dto.isFasting) add("Fasting")
                }
                val itemImages = imagesByItem[dto.id].orEmpty()
                    .sortedWith(compareByDescending<MenuItemImageDto> { it.isPrimary }.thenBy { it.sortOrder })
                    .mapNotNull { resolvePublicMediaUrl(supabaseUrl, it.storagePath) }
                val itemGroups = groupsByItem[dto.id].orEmpty()
                    .sortedBy { it.sortOrder }
                    .map { group ->
                        MenuOptionGroupUiModel(
                            id = group.id,
                            name = group.name,
                            isRequired = group.isRequired,
                            minSelect = group.minSelect,
                            maxSelect = group.maxSelect,
                            sortOrder = group.sortOrder,
                            options = optionsByGroup[group.id].orEmpty()
                                .sortedBy { it.sortOrder }
                                .map { opt ->
                                    MenuOptionUiModel(
                                        id = opt.id,
                                        groupId = opt.optionGroupId,
                                        name = opt.name,
                                        priceDeltaMinor = opt.priceDeltaMinor,
                                        isAvailable = opt.isAvailable,
                                    )
                                },
                        )
                    }

                MenuItemUiModel(
                    id = dto.id,
                    restaurantId = dto.restaurantId,
                    categoryId = dto.categoryId,
                    name = dto.name,
                    description = dto.description,
                    basePriceMinor = dto.basePriceMinor,
                    priceOverrideMinor = override?.priceOverrideMinor,
                    isAvailableAtBranch = override?.isAvailable ?: true,
                    imageUrls = itemImages,
                    optionGroups = itemGroups,
                    dietaryTags = tags,
                )
            }
    }

    private fun parseOpeningHours(json: JsonElement?): List<OpeningHourRowUiModel> {
        if (json == null || json.isJsonNull) return emptyList()
        return try {
            when {
                json.isJsonObject -> json.asJsonObject.entrySet().map { (day, value) ->
                    val hoursText = if (value.isJsonPrimitive) value.asString else value.toString()
                    OpeningHourRowUiModel(dayLabel = day, hoursLabel = hoursText)
                }
                json.isJsonArray -> json.asJsonArray.mapNotNull { el ->
                    if (!el.isJsonObject) null
                    else {
                        val obj = el.asJsonObject
                        val day = obj.get("day")?.asString ?: obj.get("label")?.asString ?: return@mapNotNull null
                        val hrs = obj.get("hours")?.asString ?: obj.get("value")?.asString ?: ""
                        OpeningHourRowUiModel(dayLabel = day, hoursLabel = hrs)
                    }
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        internal fun resolvePublicMediaUrl(supabaseUrl: String, storagePath: String?): String? {
            val clean = storagePath?.trim().orEmpty()
            if (clean.isEmpty()) return null
            if (clean.startsWith("https://", ignoreCase = true) || clean.startsWith("http://", ignoreCase = true)) {
                return clean
            }
            val base = supabaseUrl.trim().trimEnd('/')
            if (base.isEmpty()) return null
            val normalizedPath = clean.removePrefix("restaurant-public-media/").trimStart('/')
            return "$base/storage/v1/object/public/restaurant-public-media/$normalizedPath"
        }
    }
}

/**
 * Android ViewModel lifecycle wrapper around [RestaurantsCustomerCoordinator].
 * Not registered in any navigation graph while restaurant feature flags remain disabled.
 */
class RestaurantsCustomerViewModel(
    dataSource: RestaurantsCustomerDataSource = RestaurantsRepository(),
    environmentProvider: () -> RestaurantsEnvironment = { RestaurantsConfig.currentEnvironment },
    accountUidProvider: () -> String? = {
        try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
    },
) : ViewModel() {
    val coordinator = RestaurantsCustomerCoordinator(
        scope = viewModelScope,
        dataSource = dataSource,
        environmentProvider = environmentProvider,
        accountUidProvider = accountUidProvider,
    )

    val state: StateFlow<RestaurantsCustomerFlowState> = coordinator.state
}
