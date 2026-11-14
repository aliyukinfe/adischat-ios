package com.adischat.app.feature.restaurants.presentation

import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.data.OrderItemRecordDto
import com.adischat.app.feature.restaurants.data.OrderPaymentRecordDto
import com.adischat.app.feature.restaurants.data.OrderRecordDto
import com.adischat.app.feature.restaurants.data.OrderStatusEventDto
import com.adischat.app.feature.restaurants.data.ResolveQrResponse
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RestaurantsFailure
import com.adischat.app.feature.restaurants.data.SubmitDineInOrderRequest
import com.adischat.app.feature.restaurants.data.SubmitOrderItemRequest
import com.adischat.app.feature.restaurants.data.SubmitOrderResultDto
import java.net.URI
import java.util.UUID

// ============================================================================
// Task 2 — Table QR & Staff-Activated Dining Session UI State & Reducers
// ============================================================================

/**
 * Bound customer dine-in table context returned after backend `resolve-qr` verification.
 */
data class ActiveDiningSessionUi(
    val ownerAdisChatUid: String,
    val restaurantId: String,
    val restaurantName: String,
    val branchId: String,
    val branchName: String,
    val tableId: String,
    val tableLabel: String,
    val diningSessionId: String,
    val expiresAt: String? = null,
)

sealed interface TableScanPhase {
    data object AwaitingScan : TableScanPhase
    data object Resolving : TableScanPhase
    data class NeedsWaiterActivation(
        val message: String = "Ask your waiter to activate this table",
    ) : TableScanPhase

    data class NeedsSessionCode(
        val promptMessage: String = "Enter the short table session code shared by your waiter.",
        val validationError: String? = null,
    ) : TableScanPhase

    data class SessionExpiredOrClosed(
        val message: String,
        val isQrInvalidOrRevoked: Boolean,
    ) : TableScanPhase

    data class Confirmed(val session: ActiveDiningSessionUi) : TableScanPhase
    data class Error(val message: String) : TableScanPhase
    data object Cancelled : TableScanPhase
    data object Disabled : TableScanPhase
}

/**
 * Note: [pendingQrToken] holds the scanned opaque token strictly in memory for submitting
 * retry / session-code confirmation and is never rendered directly in UI labels or errors.
 */
data class RestaurantTableScanUiState(
    val phase: TableScanPhase = TableScanPhase.AwaitingScan,
    val pendingQrToken: String = "",
    val sessionCodeInput: String = "",
    val isDineInEnabled: Boolean = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED,
) {
    val hasTokenCaptured: Boolean
        get() = pendingQrToken.isNotBlank()
}

object RestaurantQrParser {
    private const val EXPECTED_HOST = "adischat.com"
    private const val EXPECTED_PATH_PREFIX = "/d/"
    private const val MIN_URL_TOKEN_LENGTH = 8
    private val BARE_TOKEN_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{5,127}$")

    fun isValidBareToken(token: String): Boolean {
        return BARE_TOKEN_REGEX.matches(token)
    }

    /**
     * Parses only strict production-format table QR links:
     * `https://adischat.com/d/<tbl_token>` with no port, userinfo, query, fragment,
     * extra path segments, or whitespace.
     */
    fun parseStrictRestaurantQrUrl(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        if (trimmed.contains('?') || trimmed.contains('#')) return null

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return null
        }

        if (uri.scheme?.equals("https", ignoreCase = true) != true) return null
        if (uri.host?.equals(EXPECTED_HOST, ignoreCase = true) != true) return null
        if (uri.port != -1) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null

        val rawPath = uri.rawPath ?: return null
        if (!rawPath.startsWith(EXPECTED_PATH_PREFIX)) return null

        val token = rawPath.removePrefix(EXPECTED_PATH_PREFIX)
        if (token.isEmpty() || token.contains('/')) return null
        if (!token.startsWith("tbl_") || token.length < MIN_URL_TOKEN_LENGTH) return null
        if (!isValidBareToken(token)) return null
        return token
    }

    /**
     * Extracts a table token for the dedicated Restaurants table scanner / manual entry.
     * Accepts either a strict `https://adischat.com/d/tbl_...` URL or an opaque bare token.
     */
    fun extractTokenForTableScanner(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val looksLikeUrlOrPath = trimmed.contains("://") ||
            trimmed.startsWith("http", ignoreCase = true) ||
            trimmed.contains('/') ||
            trimmed.contains('?') ||
            trimmed.contains('#') ||
            trimmed.contains('.')
        if (looksLikeUrlOrPath) {
            return parseStrictRestaurantQrUrl(trimmed)
        }
        return trimmed.takeIf(::isValidBareToken)
    }

    /**
     * Extracts a restaurant table token from incoming deep-link URIs:
     * - `https://adischat.com/d/<tbl_token>` or `http://...`
     * - `adischat://restaurant/table?token=<tbl_token>`
     * Returns null for any non-restaurant link without modifying existing routing.
     */
    fun extractRestaurantDeepLink(rawUri: String?): String? {
        val trimmed = rawUri?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (scheme in listOf("http", "https") && host == EXPECTED_HOST) {
            val rawPath = uri.rawPath.orEmpty()
            if (rawPath.startsWith(EXPECTED_PATH_PREFIX)) {
                val token = rawPath.removePrefix(EXPECTED_PATH_PREFIX)
                if (isValidBareToken(token)) return token
            }
        } else if (scheme == "adischat" && host == "restaurant") {
            val query = uri.rawQuery.orEmpty()
            val tokenParam = query.split("&")
                .firstOrNull { it.startsWith("token=") }
                ?.removePrefix("token=")
            if (tokenParam != null && isValidBareToken(tokenParam)) return tokenParam
            val rawPath = uri.rawPath.orEmpty().trim('/')
            if (isValidBareToken(rawPath)) return rawPath
        }
        return null
    }
}

object TableQrScanReducers {
    const val MAX_SESSION_CODE_LENGTH = 12

    fun initialState(
        environment: RestaurantsEnvironment = RestaurantsEnvironment(),
    ): RestaurantTableScanUiState = RestaurantTableScanUiState(
        phase = if (environment.isDineInEnabled) {
            TableScanPhase.AwaitingScan
        } else {
            TableScanPhase.Disabled
        },
        isDineInEnabled = environment.isDineInEnabled,
    )

    fun onQrTokenCaptured(
        state: RestaurantTableScanUiState,
        rawToken: String,
    ): RestaurantTableScanUiState {
        if (!state.isDineInEnabled) return state.copy(phase = TableScanPhase.Disabled)
        val extracted = RestaurantQrParser.extractTokenForTableScanner(rawToken)
        if (extracted == null) {
            return state.copy(
                phase = TableScanPhase.Error("Please scan a valid restaurant table QR code."),
                pendingQrToken = "",
            )
        }
        return state.copy(
            phase = TableScanPhase.Resolving,
            pendingQrToken = extracted,
            sessionCodeInput = "",
        )
    }

    fun onSessionCodeChanged(
        state: RestaurantTableScanUiState,
        code: String,
    ): RestaurantTableScanUiState {
        val cleaned = code.trimStart().take(MAX_SESSION_CODE_LENGTH)
        val nextPhase = when (val current = state.phase) {
            is TableScanPhase.NeedsSessionCode -> current.copy(validationError = null)
            else -> current
        }
        return state.copy(sessionCodeInput = cleaned, phase = nextPhase)
    }

    fun beginSessionCodeSubmit(state: RestaurantTableScanUiState): RestaurantTableScanUiState {
        if (!state.isDineInEnabled) return state.copy(phase = TableScanPhase.Disabled)
        if (state.pendingQrToken.isBlank()) {
            return state.copy(phase = TableScanPhase.AwaitingScan)
        }
        if (state.sessionCodeInput.trim().isEmpty()) {
            return state.copy(
                phase = TableScanPhase.NeedsSessionCode(
                    validationError = "Please enter the session code from your waiter.",
                ),
            )
        }
        return state.copy(phase = TableScanPhase.Resolving)
    }

    fun onResolveSuccess(
        state: RestaurantTableScanUiState,
        response: ResolveQrResponse,
        ownerAdisChatUid: String,
    ): RestaurantTableScanUiState {
        val restaurant = response.restaurant
        val branch = response.branch
        val table = response.table
        val sessionId = response.diningSessionId?.trim().orEmpty()
        if (!response.ok || restaurant == null || branch == null || table == null || sessionId.isEmpty()) {
            return state.copy(
                phase = TableScanPhase.Error("Unable to confirm table session details. Please scan again."),
                pendingQrToken = "",
            )
        }
        return state.copy(
            phase = TableScanPhase.Confirmed(
                session = ActiveDiningSessionUi(
                    ownerAdisChatUid = ownerAdisChatUid,
                    restaurantId = restaurant.id,
                    restaurantName = restaurant.name,
                    branchId = branch.id,
                    branchName = branch.name,
                    tableId = table.id,
                    tableLabel = table.label,
                    diningSessionId = sessionId,
                    expiresAt = response.expiresAt,
                ),
            ),
            pendingQrToken = "",
            sessionCodeInput = "",
        )
    }

    fun onResolveFailure(
        state: RestaurantTableScanUiState,
        failure: RestaurantsFailure,
    ): RestaurantTableScanUiState {
        if (failure is RestaurantsFailure.FeatureDisabled) {
            return state.copy(phase = TableScanPhase.Disabled, pendingQrToken = "")
        }

        val safeError = scrubTokenFromMessage(failure.userMessage, state.pendingQrToken)
        val nextPhase: TableScanPhase = when (failure.code) {
            "dining_session_not_activated" -> TableScanPhase.NeedsWaiterActivation(
                message = "Ask your waiter to activate this table",
            )
            "session_code_required" -> TableScanPhase.NeedsSessionCode(
                promptMessage = "This table requires a waiter-provided session code.",
                validationError = null,
            )
            "invalid_session_code" -> TableScanPhase.NeedsSessionCode(
                promptMessage = "Ask your waiter for the active code for this table.",
                validationError = "That session code does not match. Please double-check with your waiter.",
            )
            "qr_revoked_or_invalid", "qr_revoked", "qr_expired", "invalid_qr" ->
                TableScanPhase.SessionExpiredOrClosed(
                    message = "This table QR code is expired or has been replaced. Scan the current QR on your table.",
                    isQrInvalidOrRevoked = true,
                )
            "dining_session_expired", "dining_session_not_open", "session_closed", "session_expired" ->
                TableScanPhase.SessionExpiredOrClosed(
                    message = "This table dining session has closed or expired. Ask staff to open a new table session.",
                    isQrInvalidOrRevoked = false,
                )
            else -> TableScanPhase.Error(safeError)
        }

        val keepToken = nextPhase is TableScanPhase.NeedsWaiterActivation ||
            nextPhase is TableScanPhase.NeedsSessionCode ||
            nextPhase is TableScanPhase.Error

        return state.copy(
            phase = nextPhase,
            pendingQrToken = if (keepToken) state.pendingQrToken else "",
        )
    }

    fun onRetryWaiterCheck(state: RestaurantTableScanUiState): RestaurantTableScanUiState {
        if (!state.isDineInEnabled) return state.copy(phase = TableScanPhase.Disabled)
        if (state.pendingQrToken.isBlank()) {
            return state.copy(phase = TableScanPhase.AwaitingScan)
        }
        return state.copy(phase = TableScanPhase.Resolving)
    }

    fun onResetOrCancel(
        state: RestaurantTableScanUiState,
        cancelledByUser: Boolean,
    ): RestaurantTableScanUiState = state.copy(
        phase = if (cancelledByUser) TableScanPhase.Cancelled else TableScanPhase.AwaitingScan,
        pendingQrToken = "",
        sessionCodeInput = "",
    )

    internal fun scrubTokenFromMessage(message: String, rawToken: String): String {
        if (rawToken.isBlank()) return message
        return if (message.contains(rawToken, ignoreCase = false)) {
            "Table verification could not be completed. Please rescan the QR code."
        } else {
            message
        }
    }
}

// ============================================================================
// Task 3 — Dine-In Cart & Checkout Review State & Reducers
// ============================================================================

data class SelectedCartOptionUi(
    val optionId: String,
    val optionName: String,
    val groupName: String,
    val priceDeltaMinor: Long,
)

data class DineInCartItemUi(
    val lineId: String,
    val menuItemId: String,
    val itemName: String,
    val restaurantId: String,
    val branchId: String,
    val baseUnitPriceMinor: Long,
    val selectedOptions: List<SelectedCartOptionUi> = emptyList(),
    val quantity: Int = 1,
    val specialInstructions: String? = null,
    val imageUrl: String? = null,
) {
    val unitPriceMinor: Long
        get() = (baseUnitPriceMinor + selectedOptions.sumOf { it.priceDeltaMinor }).coerceAtLeast(0L)

    val lineTotalMinor: Long
        get() = unitPriceMinor * quantity.coerceAtLeast(1)
}

sealed interface CartCheckoutPhase {
    data object Idle : CartCheckoutPhase
    data class Submitting(val idempotencyKey: String) : CartCheckoutPhase
    data class Submitted(val order: SubmitOrderResultDto) : CartCheckoutPhase
    data class Failed(
        val message: String,
        val idempotencyKey: String,
    ) : CartCheckoutPhase
}

enum class DineInOrderMode(
    val wireValue: String,
    val label: String,
    val statusBadge: String?,
    val isEnabled: Boolean,
    val requiresActiveTableSession: Boolean,
) {
    DINE_IN(
        wireValue = "DINE_IN",
        label = "Dine In",
        statusBadge = null,
        isEnabled = true,
        requiresActiveTableSession = true,
    ),
    TAKEAWAY(
        wireValue = "TAKEAWAY",
        label = "Takeaway",
        statusBadge = "Coming Soon",
        isEnabled = false,
        requiresActiveTableSession = false,
    ),
    DELIVERY(
        wireValue = "DELIVERY",
        label = "Delivery",
        statusBadge = "Future Phase",
        isEnabled = false,
        requiresActiveTableSession = false,
    ),
}

data class RestaurantCartUiState(
    val activeSession: ActiveDiningSessionUi? = null,
    val items: List<DineInCartItemUi> = emptyList(),
    val customerNotes: String = "",
    val checkoutPhase: CartCheckoutPhase = CartCheckoutPhase.Idle,
    val pendingIdempotencyKey: String? = null,
    val invalidationNotice: String? = null,
    val isDineInEnabled: Boolean = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED,
    val selectedOrderMode: DineInOrderMode = DineInOrderMode.DINE_IN,
) {
    val estimatedSubtotalMinor: Long
        get() = items.sumOf { it.lineTotalMinor }

    val totalItemCount: Int
        get() = items.sumOf { it.quantity }

    val isSubmitting: Boolean
        get() = checkoutPhase is CartCheckoutPhase.Submitting

    val canSubmitOrder: Boolean
        get() = isDineInEnabled &&
            selectedOrderMode.isEnabled &&
            (!selectedOrderMode.requiresActiveTableSession || activeSession != null) &&
            items.isNotEmpty() &&
            checkoutPhase !is CartCheckoutPhase.Submitting &&
            checkoutPhase !is CartCheckoutPhase.Submitted
}

sealed interface CartMutationOutcome {
    data class Updated(val state: RestaurantCartUiState) : CartMutationOutcome
    data class Rejected(val reason: String, val state: RestaurantCartUiState) : CartMutationOutcome
}

sealed interface QuickAddOutcome {
    data class Updated(val state: RestaurantCartUiState) : QuickAddOutcome
    data class RequiresCustomization(
        val sheetState: MenuItemSheetState,
        val reason: String,
    ) : QuickAddOutcome
    data class Rejected(val reason: String, val state: RestaurantCartUiState) : QuickAddOutcome
}

sealed interface QuickDecrementOutcome {
    data class Updated(val state: RestaurantCartUiState) : QuickDecrementOutcome
    data class RequiresVariantResolution(
        val menuItemId: String,
        val matchingLines: List<DineInCartItemUi>,
        val reason: String,
    ) : QuickDecrementOutcome
    data class NoOp(val state: RestaurantCartUiState) : QuickDecrementOutcome
    data class Rejected(val reason: String, val state: RestaurantCartUiState) : QuickDecrementOutcome
}

enum class CheckoutSheetStep {
    CART_REVIEW,
    CHECKOUT_CONFIRM,
}

object DineInCartReducers {
    const val ORDER_TYPE_DINE_IN = "DINE_IN"
    const val PAYMENT_METHOD_PAY_AT_RESTAURANT = "PAY_AT_RESTAURANT"
    const val MAX_ITEM_QUANTITY = 20
    const val MAX_CUSTOMER_NOTES_LENGTH = 500

    fun bindActiveSession(
        state: RestaurantCartUiState,
        newSession: ActiveDiningSessionUi?,
    ): RestaurantCartUiState {
        if (newSession == null) {
            return invalidateCart(
                state = state,
                notice = if (state.items.isNotEmpty()) {
                    "Dining session ended, so your dine-in cart was cleared."
                } else {
                    null
                },
            )
        }
        val prev = state.activeSession
        val contextChanged = prev != null && (
            prev.ownerAdisChatUid != newSession.ownerAdisChatUid ||
                prev.restaurantId != newSession.restaurantId ||
                prev.branchId != newSession.branchId ||
                prev.diningSessionId != newSession.diningSessionId
            )

        return if (contextChanged) {
            state.copy(
                activeSession = newSession,
                items = emptyList(),
                customerNotes = "",
                checkoutPhase = CartCheckoutPhase.Idle,
                pendingIdempotencyKey = null,
                invalidationNotice = if (state.items.isNotEmpty()) {
                    "Switched table or restaurant session. Previous cart items were cleared."
                } else {
                    null
                },
            )
        } else {
            state.copy(activeSession = newSession, invalidationNotice = null)
        }
    }

    fun reconcileAccountSwitch(
        state: RestaurantCartUiState,
        currentAdisChatUid: String?,
    ): RestaurantCartUiState {
        val activeUid = state.activeSession?.ownerAdisChatUid
        if (currentAdisChatUid.isNullOrBlank() || (activeUid != null && activeUid != currentAdisChatUid)) {
            return invalidateCart(
                state = state,
                notice = if (state.items.isNotEmpty() || state.activeSession != null) {
                    "Account signed out or switched. Dine-in session and cart were cleared."
                } else {
                    null
                },
            )
        }
        return state
    }

    fun invalidateCart(
        state: RestaurantCartUiState,
        notice: String? = null,
    ): RestaurantCartUiState = state.copy(
        activeSession = null,
        items = emptyList(),
        customerNotes = "",
        checkoutPhase = CartCheckoutPhase.Idle,
        pendingIdempotencyKey = null,
        invalidationNotice = notice,
    )

    fun addItemFromSheet(
        state: RestaurantCartUiState,
        sheetState: MenuItemSheetState,
        branchId: String,
        specialInstructions: String? = null,
    ): CartMutationOutcome {
        if (!state.isDineInEnabled) {
            return CartMutationOutcome.Rejected("Dine-in ordering is disabled.", state)
        }
        if (state.isSubmitting) {
            return CartMutationOutcome.Rejected("Order submission is already in progress.", state)
        }
        val session = state.activeSession
            ?: return CartMutationOutcome.Rejected("Scan your table QR code before adding items.", state)
        val item = sheetState.item
        if (item.restaurantId != session.restaurantId || branchId != session.branchId) {
            return CartMutationOutcome.Rejected(
                "This item does not match your active table's restaurant branch.",
                state,
            )
        }
        if (!item.isAvailableAtBranch) {
            return CartMutationOutcome.Rejected("This dish is currently unavailable at this branch.", state)
        }
        if (!sheetState.isSelectionValid()) {
            return CartMutationOutcome.Rejected("Please complete all required item options.", state)
        }

        val selectedOptions = item.optionGroups
            .sortedBy { it.sortOrder }
            .flatMap { group ->
                val chosenIds = sheetState.selectedOptionsByGroup[group.id].orEmpty()
                group.options
                    .filter { chosenIds.contains(it.id) }
                    .map { option ->
                        SelectedCartOptionUi(
                            optionId = option.id,
                            optionName = option.name,
                            groupName = group.name,
                            priceDeltaMinor = option.priceDeltaMinor,
                        )
                    }
            }

        val cleanInstructions = specialInstructions?.trim()?.take(200)?.ifEmpty { null }
        val signature = buildLineSignature(item.id, selectedOptions, cleanInstructions)
        val existingIdx = state.items.indexOfFirst { it.lineId == signature }

        val updatedItems = if (existingIdx >= 0) {
            state.items.mapIndexed { idx, existing ->
                if (idx == existingIdx) {
                    existing.copy(
                        quantity = (existing.quantity + sheetState.quantityPreview)
                            .coerceAtMost(MAX_ITEM_QUANTITY),
                    )
                } else {
                    existing
                }
            }
        } else {
            state.items + DineInCartItemUi(
                lineId = signature,
                menuItemId = item.id,
                itemName = item.name,
                restaurantId = item.restaurantId,
                branchId = branchId,
                baseUnitPriceMinor = item.effectivePriceMinor,
                selectedOptions = selectedOptions,
                quantity = sheetState.quantityPreview.coerceIn(1, MAX_ITEM_QUANTITY),
                specialInstructions = cleanInstructions,
                imageUrl = item.imageUrls.firstOrNull(),
            )
        }

        // Cart contents changed -> reset any stale idempotency key from a previous failed attempt.
        return CartMutationOutcome.Updated(
            state.copy(
                items = updatedItems,
                checkoutPhase = CartCheckoutPhase.Idle,
                pendingIdempotencyKey = null,
                invalidationNotice = null,
            ),
        )
    }

    fun selectOrderMode(
        state: RestaurantCartUiState,
        mode: DineInOrderMode,
    ): CartMutationOutcome {
        if (!mode.isEnabled) {
            val badge = mode.statusBadge?.let { " ($it)" }.orEmpty()
            return CartMutationOutcome.Rejected(
                reason = "${mode.label} ordering is unavailable$badge. Please use Dine In.",
                state = state,
            )
        }
        return CartMutationOutcome.Updated(state.copy(selectedOrderMode = mode))
    }

    fun quickAddItem(
        state: RestaurantCartUiState,
        item: MenuItemUiModel,
        branchId: String,
    ): QuickAddOutcome {
        if (!state.isDineInEnabled) {
            return QuickAddOutcome.Rejected("Dine-in ordering is disabled.", state)
        }
        if (!state.selectedOrderMode.isEnabled) {
            return QuickAddOutcome.Rejected(
                "${state.selectedOrderMode.label} ordering is unavailable.",
                state,
            )
        }
        if (state.isSubmitting) {
            return QuickAddOutcome.Rejected("Order submission is already in progress.", state)
        }
        val session = state.activeSession
            ?: return QuickAddOutcome.Rejected("Scan your table QR code before adding items.", state)
        if (item.restaurantId != session.restaurantId || branchId != session.branchId) {
            return QuickAddOutcome.Rejected(
                "This item does not match your active table's restaurant branch.",
                state,
            )
        }
        if (!item.isAvailableAtBranch) {
            return QuickAddOutcome.Rejected("This dish is currently unavailable at this branch.", state)
        }
        if (item.hasRequiredOptions()) {
            return QuickAddOutcome.RequiresCustomization(
                sheetState = MenuItemSheetState.initialFor(item).copy(isBrowseOnly = false),
                reason = "Select required options for ${item.name}.",
            )
        }

        val simpleSheet = MenuItemSheetState(
            item = item,
            selectedOptionsByGroup = emptyMap(),
            quantityPreview = 1,
            isBrowseOnly = false,
        )
        return when (
            val outcome = addItemFromSheet(
                state = state,
                sheetState = simpleSheet,
                branchId = branchId,
                specialInstructions = null,
            )
        ) {
            is CartMutationOutcome.Updated -> QuickAddOutcome.Updated(outcome.state)
            is CartMutationOutcome.Rejected -> QuickAddOutcome.Rejected(outcome.reason, outcome.state)
        }
    }

    fun quickDecrementItem(
        state: RestaurantCartUiState,
        menuItemId: String,
        branchId: String,
    ): QuickDecrementOutcome {
        if (state.isSubmitting) {
            return QuickDecrementOutcome.Rejected("Order submission is already in progress.", state)
        }
        val session = state.activeSession
            ?: return QuickDecrementOutcome.Rejected("Scan your table QR code before updating your cart.", state)
        if (branchId != session.branchId) {
            return QuickDecrementOutcome.Rejected(
                "Switch back to your seated branch before updating this cart.",
                state,
            )
        }
        val matchingLines = state.items.filter {
            it.menuItemId == menuItemId &&
                it.restaurantId == session.restaurantId &&
                it.branchId == branchId
        }
        if (matchingLines.isEmpty()) {
            return QuickDecrementOutcome.NoOp(state)
        }
        if (matchingLines.size > 1) {
            return QuickDecrementOutcome.RequiresVariantResolution(
                menuItemId = menuItemId,
                matchingLines = matchingLines,
                reason = "Multiple customized variations in cart. Open cart to adjust a specific item.",
            )
        }
        val onlyLine = matchingLines.first()
        return QuickDecrementOutcome.Updated(
            updateItemQuantity(state, onlyLine.lineId, onlyLine.quantity - 1),
        )
    }

    fun updateItemQuantity(
        state: RestaurantCartUiState,
        lineId: String,
        newQuantity: Int,
    ): RestaurantCartUiState {
        if (state.isSubmitting) return state
        val updated = if (newQuantity <= 0) {
            state.items.filterNot { it.lineId == lineId }
        } else {
            val clamped = newQuantity.coerceIn(1, MAX_ITEM_QUANTITY)
            state.items.map { item ->
                if (item.lineId == lineId) item.copy(quantity = clamped) else item
            }
        }
        return state.copy(
            items = updated,
            checkoutPhase = CartCheckoutPhase.Idle,
            pendingIdempotencyKey = null,
        )
    }

    fun removeItem(
        state: RestaurantCartUiState,
        lineId: String,
    ): RestaurantCartUiState {
        if (state.isSubmitting) return state
        return state.copy(
            items = state.items.filterNot { it.lineId == lineId },
            checkoutPhase = CartCheckoutPhase.Idle,
            pendingIdempotencyKey = null,
        )
    }

    fun updateCustomerNotes(
        state: RestaurantCartUiState,
        notes: String,
    ): RestaurantCartUiState {
        if (state.isSubmitting) return state
        return state.copy(
            customerNotes = notes.take(MAX_CUSTOMER_NOTES_LENGTH),
            checkoutPhase = CartCheckoutPhase.Idle,
            pendingIdempotencyKey = null,
        )
    }

    /**
     * Transitions the cart into an in-flight submission while enforcing:
     * 1) duplicate-submission prevention when already [CartCheckoutPhase.Submitting]
     * 2) single stable UUID idempotency key reused across retries of the exact same cart attempt
     */
    fun beginCheckoutAttempt(
        state: RestaurantCartUiState,
        uuidGenerator: () -> String = { UUID.randomUUID().toString() },
    ): RestaurantCartUiState {
        if (!state.isDineInEnabled ||
            state.activeSession == null ||
            state.items.isEmpty() ||
            state.checkoutPhase is CartCheckoutPhase.Submitting ||
            state.checkoutPhase is CartCheckoutPhase.Submitted
        ) {
            return state
        }

        val stableKey = when (val phase = state.checkoutPhase) {
            is CartCheckoutPhase.Failed -> phase.idempotencyKey
            else -> state.pendingIdempotencyKey?.takeIf { it.isNotBlank() } ?: uuidGenerator()
        }

        return state.copy(
            checkoutPhase = CartCheckoutPhase.Submitting(stableKey),
            pendingIdempotencyKey = stableKey,
            invalidationNotice = null,
        )
    }

    fun onSubmitSucceeded(
        state: RestaurantCartUiState,
        order: SubmitOrderResultDto,
    ): RestaurantCartUiState = state.copy(
        items = emptyList(),
        customerNotes = "",
        checkoutPhase = CartCheckoutPhase.Submitted(order),
        pendingIdempotencyKey = null,
    )

    fun onSubmitFailed(
        state: RestaurantCartUiState,
        message: String,
    ): RestaurantCartUiState {
        val currentKey = when (val phase = state.checkoutPhase) {
            is CartCheckoutPhase.Submitting -> phase.idempotencyKey
            is CartCheckoutPhase.Failed -> phase.idempotencyKey
            else -> state.pendingIdempotencyKey ?: ""
        }
        return state.copy(
            checkoutPhase = CartCheckoutPhase.Failed(
                message = message,
                idempotencyKey = currentKey,
            ),
            pendingIdempotencyKey = currentKey.ifBlank { null },
        )
    }

    fun buildSubmitOrderRequest(
        state: RestaurantCartUiState,
        idempotencyKey: String,
    ): SubmitDineInOrderRequest? {
        if (!state.selectedOrderMode.isEnabled) return null
        val session = state.activeSession ?: return null
        if (state.items.isEmpty() || idempotencyKey.isBlank()) return null
        return SubmitDineInOrderRequest(
            diningSessionId = session.diningSessionId,
            branchId = session.branchId,
            idempotencyKey = idempotencyKey,
            orderType = state.selectedOrderMode.wireValue,
            paymentMethod = PAYMENT_METHOD_PAY_AT_RESTAURANT,
            customerNotes = state.customerNotes.trim().ifEmpty { null },
            items = state.items.map { item ->
                SubmitOrderItemRequest(
                    menuItemId = item.menuItemId,
                    quantity = item.quantity,
                    optionIds = item.selectedOptions.map { it.optionId }.ifEmpty { null },
                    specialInstructions = item.specialInstructions,
                )
            },
        )
    }

    private fun buildLineSignature(
        menuItemId: String,
        options: List<SelectedCartOptionUi>,
        instructions: String?,
    ): String {
        val optionPart = options.map { it.optionId }.sorted().joinToString(",")
        return "$menuItemId|$optionPart|${instructions.orEmpty()}"
    }
}

// ============================================================================
// Task 4 — Order Confirmation & Tracking State & Reducers
// ============================================================================

enum class RestaurantOrderStatus(val wireValue: String, val label: String) {
    ORDER_SUBMITTED("ORDER_SUBMITTED", "Order Submitted"),
    ACCEPTED("ACCEPTED", "Accepted by Restaurant"),
    PREPARING("PREPARING", "Preparing in Kitchen"),
    READY("READY", "Ready to Serve"),
    SERVED("SERVED", "Served at Table"),
    COMPLETED("COMPLETED", "Completed"),
    REJECTED("REJECTED", "Order Declined"),
    CANCELLED("CANCELLED", "Order Cancelled");

    val isTerminalNegative: Boolean
        get() = this == REJECTED || this == CANCELLED

    val isTerminal: Boolean
        get() = this == COMPLETED || isTerminalNegative

    val timelineDisplayLabel: String
        get() = when (this) {
            ORDER_SUBMITTED -> "Order Placed"
            ACCEPTED -> "Restaurant Accepted"
            PREPARING -> "Preparing"
            READY -> "Ready"
            SERVED -> "Served"
            COMPLETED -> "Completed"
            REJECTED -> "Order Declined"
            CANCELLED -> "Order Cancelled"
        }

    companion object {
        val activeProgressionSteps: List<RestaurantOrderStatus> = listOf(
            ORDER_SUBMITTED,
            ACCEPTED,
            PREPARING,
            READY,
            SERVED,
            COMPLETED,
        )

        fun fromWireValue(raw: String?): RestaurantOrderStatus =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) }
                ?: ORDER_SUBMITTED
    }
}

enum class PaymentStatusUi(val label: String) {
    UNPAID("Unpaid • Pay at Restaurant"),
    PAID("Paid at Restaurant"),
}

enum class TimelineStepState {
    COMPLETED,
    CURRENT,
    UPCOMING,
    TERMINATED,
}

data class OrderTimelineStepUi(
    val status: RestaurantOrderStatus,
    val title: String,
    val subtitle: String,
    val stepState: TimelineStepState,
    val timestampLabel: String? = null,
)

data class KitchenStaffUpdateUi(
    val id: String,
    val status: RestaurantOrderStatus,
    val title: String,
    val detail: String? = null,
    val timestampLabel: String? = null,
)

data class TrackingOrderLineUi(
    val id: String,
    val name: String,
    val quantity: Int,
    val lineTotalMinor: Long,
    val notes: String? = null,
)

sealed interface OrderTrackingSyncState {
    data object Synced : OrderTrackingSyncState
    data object Refreshing : OrderTrackingSyncState
    data class OfflineOrReconnecting(val message: String) : OrderTrackingSyncState
    data class Error(val message: String) : OrderTrackingSyncState
}

data class RestaurantOrderTrackingUiState(
    val loadState: RestaurantsLoadState = RestaurantsLoadState.Content,
    val syncState: OrderTrackingSyncState = OrderTrackingSyncState.Synced,
    val orderId: String = "",
    val orderNumber: String = "",
    val restaurantName: String = "",
    val branchName: String = "",
    val tableLabel: String = "",
    val currentStatus: RestaurantOrderStatus = RestaurantOrderStatus.ORDER_SUBMITTED,
    val terminalReason: String? = null,
    val paymentStatus: PaymentStatusUi = PaymentStatusUi.UNPAID,
    val paymentReceiptRef: String? = null,
    val confirmedTotalMinor: Long = 0L,
    val items: List<TrackingOrderLineUi> = emptyList(),
    val timelineSteps: List<OrderTimelineStepUi> = emptyList(),
    val staffUpdates: List<KitchenStaffUpdateUi> = emptyList(),
    val isDineInEnabled: Boolean = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED,
)

object OrderTrackingReducers {
    fun buildTimelineSteps(
        currentStatus: RestaurantOrderStatus,
        events: List<OrderStatusEventDto> = emptyList(),
    ): List<OrderTimelineStepUi> {
        val timestampByStatus = events.associate { event ->
            RestaurantOrderStatus.fromWireValue(event.toStatus) to event.createdAt
        }

        if (currentStatus.isTerminalNegative) {
            val reachedSet = events.map { RestaurantOrderStatus.fromWireValue(it.toStatus) }.toSet() +
                RestaurantOrderStatus.ORDER_SUBMITTED
            val baseSteps = RestaurantOrderStatus.activeProgressionSteps.mapNotNull { step ->
                if (reachedSet.contains(step)) {
                    OrderTimelineStepUi(
                        status = step,
                        title = step.label,
                        subtitle = stepSubtitle(step),
                        stepState = TimelineStepState.COMPLETED,
                        timestampLabel = timestampByStatus[step],
                    )
                } else {
                    null
                }
            }
            return baseSteps + OrderTimelineStepUi(
                status = currentStatus,
                title = currentStatus.label,
                subtitle = if (currentStatus == RestaurantOrderStatus.REJECTED) {
                    "Restaurant staff could not fulfill this order."
                } else {
                    "This order was cancelled."
                },
                stepState = TimelineStepState.TERMINATED,
                timestampLabel = timestampByStatus[currentStatus],
            )
        }

        val currentIdx = RestaurantOrderStatus.activeProgressionSteps.indexOf(currentStatus).coerceAtLeast(0)
        return RestaurantOrderStatus.activeProgressionSteps.mapIndexed { idx, step ->
            val stepState = when {
                idx < currentIdx -> TimelineStepState.COMPLETED
                idx == currentIdx && currentStatus == RestaurantOrderStatus.COMPLETED -> TimelineStepState.COMPLETED
                idx == currentIdx -> TimelineStepState.CURRENT
                else -> TimelineStepState.UPCOMING
            }
            OrderTimelineStepUi(
                status = step,
                title = step.label,
                subtitle = stepSubtitle(step),
                stepState = stepState,
                timestampLabel = timestampByStatus[step],
            )
        }
    }

    /**
     * Initializes tracking UI strictly from a server-confirmed [SubmitOrderResultDto].
     * Never fabricates confirmations offline without an explicit server response.
     */
    fun fromServerSubmitResult(
        result: SubmitOrderResultDto,
        session: ActiveDiningSessionUi,
        submittedItems: List<DineInCartItemUi>,
        isDineInEnabled: Boolean = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED,
    ): RestaurantOrderTrackingUiState {
        val status = RestaurantOrderStatus.fromWireValue(result.status)
        val paymentStatus = if (result.paymentStatus.equals("PAID", ignoreCase = true)) {
            PaymentStatusUi.PAID
        } else {
            PaymentStatusUi.UNPAID
        }
        return RestaurantOrderTrackingUiState(
            loadState = RestaurantsLoadState.Content,
            syncState = OrderTrackingSyncState.Synced,
            orderId = result.orderId,
            orderNumber = result.orderNumber,
            restaurantName = session.restaurantName,
            branchName = session.branchName,
            tableLabel = session.tableLabel,
            currentStatus = status,
            paymentStatus = paymentStatus,
            confirmedTotalMinor = result.totalMinor,
            items = submittedItems.map { item ->
                TrackingOrderLineUi(
                    id = item.lineId,
                    name = item.itemName,
                    quantity = item.quantity,
                    lineTotalMinor = item.lineTotalMinor,
                    notes = item.specialInstructions,
                )
            },
            timelineSteps = buildTimelineSteps(status),
            staffUpdates = listOf(
                KitchenStaffUpdateUi(
                    id = "init-${result.orderId}",
                    status = status,
                    title = "Sent to ${session.restaurantName}",
                    detail = "Waiting for staff acceptance at ${session.branchName}.",
                ),
            ),
            isDineInEnabled = isDineInEnabled,
        )
    }

    fun applyServerSnapshot(
        state: RestaurantOrderTrackingUiState,
        order: OrderRecordDto,
        items: List<OrderItemRecordDto>,
        events: List<OrderStatusEventDto>,
        payments: List<OrderPaymentRecordDto>,
    ): RestaurantOrderTrackingUiState {
        val status = RestaurantOrderStatus.fromWireValue(order.status)
        val paidRecord = payments.firstOrNull { it.status.equals("PAID", ignoreCase = true) }
        val paymentUi = if (paidRecord != null || order.paymentStatus.equals("PAID", ignoreCase = true)) {
            PaymentStatusUi.PAID
        } else {
            PaymentStatusUi.UNPAID
        }
        val terminalReason = if (status.isTerminalNegative) {
            events.lastOrNull {
                RestaurantOrderStatus.fromWireValue(it.toStatus) == status
            }?.reason?.takeIf { it.isNotBlank() }
                ?: order.rejectedReason?.takeIf { it.isNotBlank() }
                ?: order.cancelledReason?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val updates = events.sortedByDescending { it.createdAt.orEmpty() }.map { event ->
            val evStatus = RestaurantOrderStatus.fromWireValue(event.toStatus)
            KitchenStaffUpdateUi(
                id = event.id,
                status = evStatus,
                title = evStatus.label,
                detail = event.reason?.takeIf { it.isNotBlank() } ?: stepSubtitle(evStatus),
                timestampLabel = event.createdAt,
            )
        }
        return state.copy(
            loadState = RestaurantsLoadState.Content,
            syncState = OrderTrackingSyncState.Synced,
            orderId = order.id,
            orderNumber = order.orderNumber,
            currentStatus = status,
            terminalReason = terminalReason,
            paymentStatus = paymentUi,
            paymentReceiptRef = paidRecord?.receiptRef,
            confirmedTotalMinor = order.totalMinor,
            items = items.map { item ->
                TrackingOrderLineUi(
                    id = item.id,
                    name = item.itemNameSnapshot,
                    quantity = item.quantity,
                    lineTotalMinor = item.lineTotalMinor,
                    notes = item.specialInstructions,
                )
            },
            timelineSteps = buildTimelineSteps(status, events),
            staffUpdates = updates,
        )
    }

    fun onRefreshStarted(state: RestaurantOrderTrackingUiState): RestaurantOrderTrackingUiState =
        state.copy(syncState = OrderTrackingSyncState.Refreshing)

    fun onRefreshFailed(
        state: RestaurantOrderTrackingUiState,
        failure: RestaurantsFailure,
    ): RestaurantOrderTrackingUiState {
        val nextSync = if (failure is RestaurantsFailure.Network) {
            OrderTrackingSyncState.OfflineOrReconnecting(
                "Offline — showing last confirmed status. Tap Refresh when back online.",
            )
        } else {
            OrderTrackingSyncState.Error(failure.userMessage)
        }
        return state.copy(syncState = nextSync)
    }

    fun clearForAccountSwitchOrSignOut(): RestaurantOrderTrackingUiState =
        RestaurantOrderTrackingUiState(loadState = RestaurantsLoadState.Empty)

    private fun stepSubtitle(status: RestaurantOrderStatus): String = when (status) {
        RestaurantOrderStatus.ORDER_SUBMITTED -> "Order sent to restaurant queue."
        RestaurantOrderStatus.ACCEPTED -> "Kitchen and floor staff accepted your order."
        RestaurantOrderStatus.PREPARING -> "Kitchen is preparing your dishes."
        RestaurantOrderStatus.READY -> "Dishes are ready at the pass."
        RestaurantOrderStatus.SERVED -> "Served to your table. Pay directly at restaurant."
        RestaurantOrderStatus.COMPLETED -> "Dine-in order completed and settled."
        RestaurantOrderStatus.REJECTED -> "Restaurant declined this order."
        RestaurantOrderStatus.CANCELLED -> "Order was cancelled."
    }
}

// ============================================================================
// Task 5 / Phase R5A — Unified Restaurant Workspace Tabs, Summary & Isolation
// ============================================================================

enum class RestaurantWorkspaceTab(val label: String) {
    MENU("Menu"),
    MY_ORDERS("My Orders"),
    HISTORY("History"),
}

data class RestaurantOrderSummaryUi(
    val orderId: String,
    val orderNumber: String,
    val restaurantId: String,
    val restaurantName: String,
    val branchId: String,
    val branchName: String,
    val tableLabel: String,
    val diningSessionId: String?,
    val customerUid: String,
    val status: RestaurantOrderStatus,
    val paymentStatus: PaymentStatusUi,
    val itemsSummaryLabel: String,
    val itemsCount: Int,
    val totalMinor: Long,
    val createdAtLabel: String,
    val isSimulatedQa: Boolean = false,
    val trackingSnapshot: RestaurantOrderTrackingUiState? = null,
) {
    val isActiveOrder: Boolean
        get() = status != RestaurantOrderStatus.COMPLETED && !status.isTerminalNegative
}

object RestaurantOrderIsolationReducers {
    fun filterActiveOrdersForRestaurant(
        orders: List<RestaurantOrderSummaryUi>,
        restaurantId: String,
        customerUid: String?,
    ): List<RestaurantOrderSummaryUi> {
        val cleanUid = customerUid?.trim().orEmpty()
        if (cleanUid.isEmpty() || restaurantId.isBlank()) return emptyList()
        return orders.filter { order ->
            order.customerUid == cleanUid &&
                order.restaurantId == restaurantId &&
                order.isActiveOrder
        }
    }

    fun filterHistoryOrdersForRestaurant(
        orders: List<RestaurantOrderSummaryUi>,
        restaurantId: String,
        customerUid: String?,
    ): List<RestaurantOrderSummaryUi> {
        val cleanUid = customerUid?.trim().orEmpty()
        if (cleanUid.isEmpty() || restaurantId.isBlank()) return emptyList()
        return orders.filter { order ->
            order.customerUid == cleanUid &&
                order.restaurantId == restaurantId &&
                !order.isActiveOrder
        }
    }

    fun itemQuantitiesInCartForRestaurant(
        cart: RestaurantCartUiState,
        restaurantId: String,
        branchId: String?,
    ): Map<String, Int> {
        val session = cart.activeSession ?: return emptyMap()
        if (session.restaurantId != restaurantId) return emptyMap()
        if (!branchId.isNullOrBlank() && session.branchId != branchId) return emptyMap()
        return cart.items
            .filter { it.restaurantId == restaurantId && (branchId.isNullOrBlank() || it.branchId == branchId) }
            .groupBy { it.menuItemId }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity } }
    }

    fun cartMatchesRestaurantAndBranch(
        cart: RestaurantCartUiState,
        restaurantId: String,
        branchId: String?,
    ): Boolean {
        val session = cart.activeSession ?: return false
        if (session.restaurantId != restaurantId) return false
        return branchId.isNullOrBlank() || session.branchId == branchId
    }

    fun mapOrderRecordToSummary(
        dto: OrderRecordDto,
        restaurantName: String,
        branchName: String,
        tableLabel: String,
    ): RestaurantOrderSummaryUi {
        val status = RestaurantOrderStatus.fromWireValue(dto.status)
        val payment = if (dto.paymentStatus.equals("PAID", ignoreCase = true)) {
            PaymentStatusUi.PAID
        } else {
            PaymentStatusUi.UNPAID
        }
        return RestaurantOrderSummaryUi(
            orderId = dto.id,
            orderNumber = dto.orderNumber.ifBlank { dto.id.take(8) },
            restaurantId = dto.restaurantId,
            restaurantName = restaurantName,
            branchId = dto.branchId,
            branchName = branchName,
            tableLabel = tableLabel,
            diningSessionId = dto.diningSessionId,
            customerUid = dto.customerProfileId,
            status = status,
            paymentStatus = payment,
            itemsSummaryLabel = "Dine-in order #${dto.orderNumber.ifBlank { dto.id.take(8) }}",
            itemsCount = 1,
            totalMinor = dto.totalMinor,
            createdAtLabel = dto.createdAt.orEmpty(),
            isSimulatedQa = false,
            trackingSnapshot = null,
        )
    }
}
