package com.adischat.app.feature.restaurants.center

import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.data.ActivateRestaurantResponse
import com.adischat.app.feature.restaurants.data.AdminApproveResponse
import com.adischat.app.feature.restaurants.data.SyncBranchOrdersResponse
import com.adischat.app.feature.restaurants.data.ApplicationMediaUploadUrlRequest
import com.adischat.app.feature.restaurants.data.AttachMenuItemImageRequest
import com.adischat.app.feature.restaurants.data.ConfirmDocumentRequest
import com.adischat.app.feature.restaurants.data.CreateBranchRequest
import com.adischat.app.feature.restaurants.data.DocumentUploadUrlRequest
import com.adischat.app.feature.restaurants.data.MenuCategoryDto
import com.adischat.app.feature.restaurants.data.MenuItemDto
import com.adischat.app.feature.restaurants.data.MenuItemImageDto
import com.adischat.app.feature.restaurants.data.OrderRecordDto
import com.adischat.app.feature.restaurants.data.PhotoUploadUrlRequest
import com.adischat.app.feature.restaurants.data.RestaurantApplicationDto
import com.adischat.app.feature.restaurants.data.RestaurantBranchDto
import com.adischat.app.feature.restaurants.data.RestaurantMediaSlot
import com.adischat.app.feature.restaurants.data.RestaurantSummaryDto
import com.adischat.app.feature.restaurants.data.RestaurantsBinaryUploadSource
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.SaveApplicationDraftRequest
import com.adischat.app.feature.restaurants.data.SetBranchAvailabilityRequest
import com.adischat.app.feature.restaurants.data.StaffInviteRequest
import com.adischat.app.feature.restaurants.data.StaffInviteResponse
import com.adischat.app.feature.restaurants.data.StaffInvitationDto
import com.adischat.app.feature.restaurants.data.StaffMemberSummaryDto
import com.adischat.app.feature.restaurants.data.UpdateBranchRequest
import com.adischat.app.feature.restaurants.data.UpdateRestaurantSettingsRequest
import com.adischat.app.feature.restaurants.data.UpsertCategoryRequest
import com.adischat.app.feature.restaurants.data.UpsertMenuItemRequest
import com.adischat.app.feature.restaurants.data.UpsertTableRequest
import com.adischat.app.feature.restaurants.data.DiningTableWithStatusDto
import com.adischat.app.feature.restaurants.data.OpenSessionResponse
import com.adischat.app.feature.restaurants.data.RotateQrResponse
import com.adischat.app.feature.restaurants.data.RotateSessionCodeResponse
import com.adischat.app.feature.restaurants.data.TransitionOrderResultDto
import com.adischat.app.feature.restaurants.presentation.OpeningHourRowUiModel
import com.adischat.app.feature.restaurants.presentation.PaymentStatusUi
import com.adischat.app.feature.restaurants.presentation.RestaurantOrderStatus
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadState
import java.util.Locale

// ============================================================================
// Onboarding Application & Operational Status Models
// ============================================================================

enum class RestaurantApplicationStatus(val wireValue: String, val label: String) {
    DRAFT("DRAFT", "Draft"),
    SUBMITTED("SUBMITTED", "Submitted"),
    UNDER_REVIEW("UNDER_REVIEW", "Under Review"),
    CHANGES_REQUESTED("CHANGES_REQUESTED", "Changes Requested"),
    APPROVED("APPROVED", "Approved"),
    REJECTED("REJECTED", "Rejected");

    val isEditableByApplicant: Boolean
        get() = this == DRAFT || this == CHANGES_REQUESTED

    companion object {
        fun fromWireValue(raw: String?): RestaurantApplicationStatus =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) }
                ?: DRAFT
    }
}

enum class RestaurantOperationalStatus(val wireValue: String, val label: String) {
    PENDING_SETUP("pending_setup", "Pending Activation"),
    ACTIVE("active", "Active"),
    SUSPENDED("suspended", "Suspended");

    companion object {
        fun fromWireValue(raw: String?): RestaurantOperationalStatus =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) }
                ?: PENDING_SETUP
    }
}

// ============================================================================
// Staff Roles & Permission Model (Separate from AdisGebeya Roles)
// ============================================================================

enum class RestaurantStaffRole(val wireValue: String, val label: String) {
    RESTAURANT_OWNER("RESTAURANT_OWNER", "Restaurant Owner"),
    RESTAURANT_MANAGER("RESTAURANT_MANAGER", "Restaurant Manager"),
    KITCHEN_STAFF("KITCHEN_STAFF", "Kitchen Staff"),
    CASHIER("CASHIER", "Cashier"),
    WAITER("WAITER", "Waiter");

    companion object {
        fun fromWireValue(raw: String?): RestaurantStaffRole? =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * Mirrors Phase R2B/R3B backend RBAC (`_shared/domain.ts` & `202611100008_r3b_gap_closure.sql`)
 * without inventing client-side overrides. Notice restaurant roles are strictly isolated
 * from AdisGebeya Shop seller roles.
 */
object RestaurantRolePermissions {
    fun canEditSettings(role: RestaurantStaffRole?): Boolean =
        role == RestaurantStaffRole.RESTAURANT_OWNER || role == RestaurantStaffRole.RESTAURANT_MANAGER

    fun canPublishLive(role: RestaurantStaffRole?): Boolean =
        role == RestaurantStaffRole.RESTAURANT_OWNER

    fun canManageMenu(role: RestaurantStaffRole?): Boolean =
        role == RestaurantStaffRole.RESTAURANT_OWNER || role == RestaurantStaffRole.RESTAURANT_MANAGER

    fun canToggleBranchStock(role: RestaurantStaffRole?): Boolean =
        role == RestaurantStaffRole.RESTAURANT_OWNER ||
            role == RestaurantStaffRole.RESTAURANT_MANAGER ||
            role == RestaurantStaffRole.KITCHEN_STAFF

    fun canManageTablesAndQr(role: RestaurantStaffRole?): Boolean =
        role == RestaurantStaffRole.RESTAURANT_OWNER || role == RestaurantStaffRole.RESTAURANT_MANAGER

    fun canActivateTableSession(role: RestaurantStaffRole?): Boolean =
        role == RestaurantStaffRole.RESTAURANT_OWNER ||
            role == RestaurantStaffRole.RESTAURANT_MANAGER ||
            role == RestaurantStaffRole.CASHIER ||
            role == RestaurantStaffRole.WAITER

    fun canInviteStaffRole(actor: RestaurantStaffRole?, target: RestaurantStaffRole): Boolean {
        if (target == RestaurantStaffRole.RESTAURANT_OWNER) return false
        return when (actor) {
            RestaurantStaffRole.RESTAURANT_OWNER -> true
            RestaurantStaffRole.RESTAURANT_MANAGER -> target != RestaurantStaffRole.RESTAURANT_MANAGER
            else -> false
        }
    }

    fun canRevokeStaff(actor: RestaurantStaffRole?, target: RestaurantStaffRole): Boolean {
        if (target == RestaurantStaffRole.RESTAURANT_OWNER) return false
        return when (actor) {
            RestaurantStaffRole.RESTAURANT_OWNER -> true
            RestaurantStaffRole.RESTAURANT_MANAGER -> target != RestaurantStaffRole.RESTAURANT_MANAGER
            else -> false
        }
    }

    fun allowedNextOrderStatuses(
        role: RestaurantStaffRole?,
        currentStatus: RestaurantOrderStatus,
    ): List<RestaurantOrderStatus> {
        if (role == null) return emptyList()
        val actions = mutableListOf<RestaurantOrderStatus>()
        when (currentStatus) {
            RestaurantOrderStatus.ORDER_SUBMITTED -> {
                if (role in setOf(
                        RestaurantStaffRole.RESTAURANT_OWNER,
                        RestaurantStaffRole.RESTAURANT_MANAGER,
                        RestaurantStaffRole.KITCHEN_STAFF,
                        RestaurantStaffRole.WAITER,
                    )
                ) {
                    actions.add(RestaurantOrderStatus.ACCEPTED)
                    actions.add(RestaurantOrderStatus.REJECTED)
                }
                if (role == RestaurantStaffRole.RESTAURANT_OWNER || role == RestaurantStaffRole.RESTAURANT_MANAGER) {
                    actions.add(RestaurantOrderStatus.CANCELLED)
                }
            }
            RestaurantOrderStatus.ACCEPTED -> {
                if (role in setOf(
                        RestaurantStaffRole.RESTAURANT_OWNER,
                        RestaurantStaffRole.RESTAURANT_MANAGER,
                        RestaurantStaffRole.KITCHEN_STAFF,
                    )
                ) {
                    actions.add(RestaurantOrderStatus.PREPARING)
                }
                if (role == RestaurantStaffRole.RESTAURANT_OWNER || role == RestaurantStaffRole.RESTAURANT_MANAGER) {
                    actions.add(RestaurantOrderStatus.CANCELLED)
                }
            }
            RestaurantOrderStatus.PREPARING -> {
                if (role in setOf(
                        RestaurantStaffRole.RESTAURANT_OWNER,
                        RestaurantStaffRole.RESTAURANT_MANAGER,
                        RestaurantStaffRole.KITCHEN_STAFF,
                    )
                ) {
                    actions.add(RestaurantOrderStatus.READY)
                }
                if (role == RestaurantStaffRole.RESTAURANT_OWNER || role == RestaurantStaffRole.RESTAURANT_MANAGER) {
                    actions.add(RestaurantOrderStatus.CANCELLED)
                }
            }
            RestaurantOrderStatus.READY -> {
                if (role in setOf(
                        RestaurantStaffRole.RESTAURANT_OWNER,
                        RestaurantStaffRole.RESTAURANT_MANAGER,
                        RestaurantStaffRole.WAITER,
                    )
                ) {
                    actions.add(RestaurantOrderStatus.SERVED)
                }
                if (role == RestaurantStaffRole.RESTAURANT_OWNER || role == RestaurantStaffRole.RESTAURANT_MANAGER) {
                    actions.add(RestaurantOrderStatus.CANCELLED)
                }
            }
            RestaurantOrderStatus.SERVED,
            RestaurantOrderStatus.COMPLETED,
            RestaurantOrderStatus.REJECTED,
            RestaurantOrderStatus.CANCELLED -> Unit
        }
        return actions
    }
}

// ============================================================================
// Restaurant Center UI Models & Tabs
// ============================================================================

enum class RestaurantCenterTab(val label: String) {
    OVERVIEW("Overview"),
    ORDERS("Orders"),
    MENU("Menu"),
    TABLES_QR("Tables & QR"),
    STAFF("Staff"),
    APPLICATION("Application"),
    SETTINGS("Settings"),
}

enum class OrderBoardFilter(val label: String) {
    ACTIVE("Active"),
    UNPAID_SERVED("Unpaid"),
    COMPLETED("Completed"),
    TERMINAL("Declined/Cancelled"),
}

enum class CenterUploadPhase {
    IDLE,
    PREPARING,
    UPLOADING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class CenterUploadUiState(
    val phase: CenterUploadPhase = CenterUploadPhase.IDLE,
    val targetKey: String? = null,
    val label: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val byteSize: Long? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
) {
    val isInProgress: Boolean
        get() = phase == CenterUploadPhase.PREPARING || phase == CenterUploadPhase.UPLOADING
}

data class CenterApplicationDocUi(
    val id: String,
    val docType: String,
    val fileName: String,
    val mimeType: String,
    val byteSize: Long,
)

data class CenterApplicationUi(
    val id: String,
    val applicantProfileId: String,
    val legalName: String,
    val brandName: String,
    val description: String,
    val businessType: String,
    val contactPhone: String,
    val contactEmail: String,
    val tinNumber: String,
    val logoPath: String? = null,
    val coverPath: String? = null,
    val status: RestaurantApplicationStatus,
    val reviewNotes: String? = null,
    val approvedRestaurantId: String? = null,
    val branchDraftCount: Int = 0,
    val documents: List<CenterApplicationDocUi> = emptyList(),
)

data class CenterRestaurantUi(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    val status: RestaurantOperationalStatus,
    val isLiveOrderingEnabled: Boolean,
    val suspendedReason: String? = null,
    val logoPath: String? = null,
    val coverPath: String? = null,
    val currentUserRole: RestaurantStaffRole? = null,
    val currentUserMembershipStatus: String = "active",
    val assignedBranchId: String? = null,
) {
    val hasActiveRole: Boolean
        get() = currentUserRole != null && currentUserMembershipStatus.equals("active", ignoreCase = true)
}

data class CenterBranchUi(
    val id: String,
    val restaurantId: String,
    val code: String,
    val name: String,
    val address: String,
    val city: String,
    val requireStaffSessionActivation: Boolean,
    val requireSessionCode: Boolean,
    val isDineInActive: Boolean,
    val status: String,
    val openingHours: List<OpeningHourRowUiModel> = emptyList(),
)

data class CenterMenuItemUi(
    val id: String,
    val restaurantId: String,
    val categoryId: String,
    val categoryName: String,
    val name: String,
    val description: String,
    val basePriceMinor: Long,
    val branchPriceOverrideMinor: Long? = null,
    val isAvailableAtBranch: Boolean = true,
    val isActive: Boolean = true,
    val photoPath: String? = null,
)

data class CenterTableUi(
    val id: String,
    val restaurantId: String,
    val branchId: String,
    val tableCode: String,
    val label: String,
    val zone: String,
    val capacity: Int,
    val isActive: Boolean,
    val activeQrVersion: Int?,
    val activeQrStatus: String?,
    val openSessionId: String?,
    val openSessionExpiresAt: String?,
    val hasSessionCode: Boolean,
    val latestGeneratedSessionCode: String? = null,
    val latestQrUrl: String? = null,
)

data class CenterStaffMemberUi(
    val staffMemberId: String,
    val restaurantId: String,
    val branchId: String?,
    val profileId: String,
    val displayName: String,
    val role: RestaurantStaffRole,
    val status: String,
)

data class CenterStaffInvitationUi(
    val id: String,
    val restaurantId: String,
    val branchId: String?,
    val role: RestaurantStaffRole,
    val invitedIdentifier: String,
    val status: String,
    val expiresAt: String?,
)

data class CenterOrderTicketUi(
    val orderId: String,
    val orderNumber: String,
    val restaurantId: String,
    val branchId: String,
    val tableLabel: String,
    val itemCountLabel: String,
    val status: RestaurantOrderStatus,
    val paymentStatus: PaymentStatusUi,
    val totalMinor: Long,
    val customerNotes: String? = null,
    val terminalReason: String? = null,
    val createdAtLabel: String? = null,
) {
    val isUnpaid: Boolean
        get() = paymentStatus == PaymentStatusUi.UNPAID
}

data class RestaurantCenterUiState(
    val loadState: RestaurantsLoadState = RestaurantsLoadState.Empty,
    val isCenterEnabled: Boolean = FeatureFlags.RESTAURANT_CENTER_ENABLED,
    val activeAccountUid: String? = null,
    val selectedTab: RestaurantCenterTab = RestaurantCenterTab.OVERVIEW,
    val applications: List<CenterApplicationUi> = emptyList(),
    val ownedRestaurants: List<CenterRestaurantUi> = emptyList(),
    val selectedRestaurantId: String? = null,
    val branches: List<CenterBranchUi> = emptyList(),
    val selectedBranchId: String? = null,
    val categories: List<MenuCategoryDto> = emptyList(),
    val menuItems: List<CenterMenuItemUi> = emptyList(),
    val tables: List<CenterTableUi> = emptyList(),
    val orders: List<CenterOrderTicketUi> = emptyList(),
    val orderSyncCursor: String? = null,
    val orderSyncCursorId: String? = null,
    val orderHasMore: Boolean = false,
    val orderFilter: OrderBoardFilter = OrderBoardFilter.ACTIVE,
    val staffMembers: List<CenterStaffMemberUi> = emptyList(),
    val staffInvitations: List<CenterStaffInvitationUi> = emptyList(),
    val uploadState: CenterUploadUiState = CenterUploadUiState(),
    val isWorking: Boolean = false,
    val errorBanner: String? = null,
    val actionFeedback: String? = null,
) {
    val selectedRestaurant: CenterRestaurantUi?
        get() = ownedRestaurants.firstOrNull { it.id == selectedRestaurantId }

    val selectedBranch: CenterBranchUi?
        get() = branches.firstOrNull { it.id == selectedBranchId }

    val activeRole: RestaurantStaffRole?
        get() = selectedRestaurant?.takeIf { it.hasActiveRole }?.currentUserRole

    val filteredOrders: List<CenterOrderTicketUi>
        get() = RestaurantCenterReducers.filterOrders(orders, orderFilter)
}

object RestaurantCenterReducers {
    fun filterOrders(
        orders: List<CenterOrderTicketUi>,
        filter: OrderBoardFilter,
    ): List<CenterOrderTicketUi> = when (filter) {
        OrderBoardFilter.ACTIVE -> orders.filter {
            it.status in setOf(
                RestaurantOrderStatus.ORDER_SUBMITTED,
                RestaurantOrderStatus.ACCEPTED,
                RestaurantOrderStatus.PREPARING,
                RestaurantOrderStatus.READY,
                RestaurantOrderStatus.SERVED,
            )
        }
        OrderBoardFilter.UNPAID_SERVED -> orders.filter {
            it.isUnpaid && !it.status.isTerminalNegative && it.status != RestaurantOrderStatus.COMPLETED
        }
        OrderBoardFilter.COMPLETED -> orders.filter {
            it.status == RestaurantOrderStatus.COMPLETED
        }
        OrderBoardFilter.TERMINAL -> orders.filter {
            it.status.isTerminalNegative
        }
    }

    fun filterForActiveRestaurant(
        restaurantId: String?,
        branches: List<CenterBranchUi>,
    ): List<CenterBranchUi> =
        if (restaurantId == null) emptyList() else branches.filter { it.restaurantId == restaurantId }

    fun filterStaffForRestaurant(
        restaurantId: String?,
        members: List<CenterStaffMemberUi>,
    ): List<CenterStaffMemberUi> =
        if (restaurantId == null) emptyList() else members.filter { it.restaurantId == restaurantId }

    fun filterOrdersForBranch(
        restaurantId: String?,
        branchId: String?,
        orders: List<CenterOrderTicketUi>,
    ): List<CenterOrderTicketUi> =
        if (restaurantId == null || branchId == null) emptyList()
        else orders.filter { it.restaurantId == restaurantId && it.branchId == branchId }

    fun isSelfApprovalBlocked(applicantProfileId: String?, actorUid: String?): Boolean =
        !applicantProfileId.isNullOrBlank() && applicantProfileId == actorUid
}

/**
 * Isolated offline/repository interface for Restaurant Center matching Phase R3B contracts.
 */
interface RestaurantCenterDataSource {
    suspend fun loadMyApplications(): Result<List<CenterApplicationUi>>
    suspend fun saveApplicationDraft(request: SaveApplicationDraftRequest): Result<CenterApplicationUi>
    suspend fun attachApplicationDocument(request: ConfirmDocumentRequest): Result<CenterApplicationDocUi>
    suspend fun uploadAndConfirmApplicationDocument(
        applicationId: String,
        docType: String,
        source: RestaurantsBinaryUploadSource,
    ): Result<CenterApplicationDocUi> =
        Result.failure(UnsupportedOperationException("uploadAndConfirmApplicationDocument not implemented"))
    suspend fun uploadApplicationMedia(
        applicationId: String,
        source: RestaurantsBinaryUploadSource,
    ): Result<String> =
        Result.failure(UnsupportedOperationException("uploadApplicationMedia not implemented"))
    suspend fun submitApplication(applicationId: String): Result<CenterApplicationUi>

    suspend fun loadAccessibleRestaurants(): Result<List<CenterRestaurantUi>>
    suspend fun loadBranches(restaurantId: String): Result<List<CenterBranchUi>>
    suspend fun createBranch(request: CreateBranchRequest): Result<CenterBranchUi>
    suspend fun updateBranch(request: UpdateBranchRequest): Result<CenterBranchUi>
    suspend fun updateRestaurantSettings(request: UpdateRestaurantSettingsRequest): Result<RestaurantSummaryDto>
    suspend fun uploadAndUpdateRestaurantMedia(
        restaurantId: String,
        slot: RestaurantMediaSlot,
        source: RestaurantsBinaryUploadSource,
        description: String? = null,
    ): Result<RestaurantSummaryDto> =
        Result.failure(UnsupportedOperationException("uploadAndUpdateRestaurantMedia not implemented"))
    suspend fun activateRestaurant(restaurantId: String): Result<ActivateRestaurantResponse> =
        Result.failure(UnsupportedOperationException("activateRestaurant not implemented"))
    suspend fun publishLive(restaurantId: String, enable: Boolean): Result<Boolean>

    suspend fun loadMenuCategories(restaurantId: String): Result<List<MenuCategoryDto>>
    suspend fun loadMenuItemsForBranch(restaurantId: String, branchId: String): Result<List<CenterMenuItemUi>>
    suspend fun upsertMenuCategory(request: UpsertCategoryRequest): Result<MenuCategoryDto>
    suspend fun upsertMenuItem(request: UpsertMenuItemRequest): Result<MenuItemDto>
    suspend fun uploadAndAttachMenuItemImage(
        restaurantId: String,
        menuItemId: String,
        source: RestaurantsBinaryUploadSource,
        isPrimary: Boolean? = null,
        sortOrder: Int? = null,
    ): Result<MenuItemImageDto> =
        Result.failure(UnsupportedOperationException("uploadAndAttachMenuItemImage not implemented"))
    suspend fun setBranchAvailability(request: SetBranchAvailabilityRequest): Result<Unit>

    suspend fun loadTables(restaurantId: String, branchId: String): Result<List<DiningTableWithStatusDto>>
    suspend fun upsertTable(request: UpsertTableRequest): Result<CenterTableUi>
    suspend fun rotateTableQr(restaurantId: String, branchId: String, tableId: String): Result<RotateQrResponse>
    suspend fun revokeTableQr(restaurantId: String, branchId: String, tableId: String): Result<Unit>
    suspend fun openDiningSession(restaurantId: String, branchId: String, tableId: String): Result<OpenSessionResponse>
    suspend fun rotateSessionCode(restaurantId: String, branchId: String, diningSessionId: String): Result<RotateSessionCodeResponse>
    suspend fun closeDiningSession(restaurantId: String, branchId: String, diningSessionId: String): Result<Unit>

    suspend fun loadStaffMembers(restaurantId: String, branchId: String?): Result<List<StaffMemberSummaryDto>>
    suspend fun loadStaffInvitations(restaurantId: String, branchId: String?): Result<List<StaffInvitationDto>>
    suspend fun inviteStaff(request: StaffInviteRequest): Result<StaffInviteResponse>
    suspend fun revokeStaffMember(staffMemberId: String): Result<Unit>
    suspend fun revokeStaffInvitation(invitationId: String): Result<Unit>

    suspend fun loadBranchOrders(restaurantId: String, branchId: String): Result<List<CenterOrderTicketUi>>
    suspend fun syncBranchOrders(
        restaurantId: String,
        branchId: String,
        since: String? = null,
        afterId: String? = null,
        limit: Int? = null,
    ): Result<Pair<List<CenterOrderTicketUi>, SyncBranchOrdersResponse>> =
        loadBranchOrders(restaurantId, branchId).map { tickets ->
            tickets to SyncBranchOrdersResponse(ok = true, branchId = branchId)
        }
    suspend fun transitionOrderStatus(orderId: String, targetStatus: String, reason: String?): Result<TransitionOrderResultDto>
}
