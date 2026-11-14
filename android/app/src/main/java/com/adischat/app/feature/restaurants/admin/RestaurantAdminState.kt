package com.adischat.app.feature.restaurants.admin

import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.center.RestaurantApplicationStatus
import com.adischat.app.feature.restaurants.center.RestaurantOperationalStatus
import com.adischat.app.feature.restaurants.data.AdminApplicationStatusResponse
import com.adischat.app.feature.restaurants.data.AdminApproveResponse
import com.adischat.app.feature.restaurants.data.AdminRestaurantStatusResponse
import com.adischat.app.feature.restaurants.data.RestaurantAuditLogDto
import com.adischat.app.feature.restaurants.presentation.RestaurantBranchUiModel
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadState

enum class RestaurantAdminTab(val label: String) {
    APPLICATIONS("Applications"),
    RESTAURANTS("Restaurants"),
    AUDIT_LOGS("Audit Logs"),
}

enum class AdminDocReviewState(val label: String) {
    UNREVIEWED("Pending Check"),
    VERIFIED("Verified"),
    FLAGGED("Flagged"),
}

data class AdminApplicationDocUi(
    val id: String,
    val docType: String,
    val fileName: String,
    val mimeType: String,
    val byteSize: Long,
    val reviewState: AdminDocReviewState = AdminDocReviewState.UNREVIEWED,
    val lastInspectedUrlPreview: String? = null,
    val signedUrlExpiresAtEpochSeconds: Long? = null,
) {
    fun hasValidSignedUrl(nowEpochSeconds: Long): Boolean =
        !lastInspectedUrlPreview.isNullOrBlank() &&
            signedUrlExpiresAtEpochSeconds != null &&
            nowEpochSeconds < signedUrlExpiresAtEpochSeconds
}

data class AdminApplicationUi(
    val id: String,
    val applicantProfileId: String,
    val brandName: String,
    val legalName: String,
    val contactEmail: String,
    val contactPhone: String,
    val tinNumber: String,
    val status: RestaurantApplicationStatus,
    val submittedAt: String?,
    val branchCount: Int,
    val draftMenuItemsCount: Int,
    val reviewNotes: String? = null,
    val approvedRestaurantId: String? = null,
    val documents: List<AdminApplicationDocUi> = emptyList(),
)

data class AdminRestaurantOverviewUi(
    val id: String,
    val name: String,
    val slug: String,
    val status: RestaurantOperationalStatus,
    val isLiveOrderingEnabled: Boolean,
    val suspendedReason: String? = null,
    val branches: List<RestaurantBranchUiModel> = emptyList(),
)

data class AdminAuditEntryUi(
    val id: String,
    val restaurantId: String?,
    val actorProfileId: String?,
    val action: String,
    val targetType: String?,
    val targetId: String?,
    val createdAt: String?,
)

data class RestaurantAdminUiState(
    val loadState: RestaurantsLoadState = RestaurantsLoadState.Empty,
    val isAdminEnabled: Boolean = FeatureFlags.RESTAURANT_ADMIN_ENABLED,
    val activeAccountUid: String? = null,
    val isAuthorizedAdmin: Boolean = false,
    val selectedTab: RestaurantAdminTab = RestaurantAdminTab.APPLICATIONS,
    val applications: List<AdminApplicationUi> = emptyList(),
    val selectedApplicationId: String? = null,
    val restaurants: List<AdminRestaurantOverviewUi> = emptyList(),
    val auditLogs: List<AdminAuditEntryUi> = emptyList(),
    val isWorking: Boolean = false,
    val errorBanner: String? = null,
    val actionNotice: String? = null,
) {
    val selectedApplication: AdminApplicationUi?
        get() = applications.firstOrNull { it.id == selectedApplicationId } ?: applications.firstOrNull()
}

/**
 * Isolated offline/mock data interface for Platform Admin review workflows.
 * Inaccessible in production while [FeatureFlags.RESTAURANT_ADMIN_ENABLED] is false.
 */
interface RestaurantAdminDataSource {
    suspend fun verifyPlatformAdminAccess(accountUid: String): Result<Boolean>
    suspend fun loadApplications(): Result<List<AdminApplicationUi>>
    suspend fun loadRestaurantsAndBranches(): Result<List<AdminRestaurantOverviewUi>>
    suspend fun loadAuditLogs(): Result<List<RestaurantAuditLogDto>>
    suspend fun startReview(applicationId: String, notes: String?): Result<AdminApplicationStatusResponse>
    suspend fun inspectDocument(applicationId: String, documentId: String): Result<String?>
    suspend fun approveApplication(applicationId: String, slug: String?, notes: String?): Result<AdminApproveResponse>
    suspend fun rejectApplication(applicationId: String, notes: String): Result<AdminApplicationStatusResponse>
    suspend fun requestChanges(applicationId: String, notes: String): Result<AdminApplicationStatusResponse>
    suspend fun suspendRestaurant(restaurantId: String, reason: String): Result<AdminRestaurantStatusResponse>
    suspend fun reactivateRestaurant(restaurantId: String, reason: String): Result<AdminRestaurantStatusResponse>
}
