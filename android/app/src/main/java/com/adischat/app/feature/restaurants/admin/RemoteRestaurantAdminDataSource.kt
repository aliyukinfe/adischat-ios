package com.adischat.app.feature.restaurants.admin

import com.adischat.app.feature.restaurants.center.RestaurantApplicationStatus
import com.adischat.app.feature.restaurants.center.RestaurantOperationalStatus
import com.adischat.app.feature.restaurants.data.AdminApplicationStatusResponse
import com.adischat.app.feature.restaurants.data.AdminApproveResponse
import com.adischat.app.feature.restaurants.data.AdminRestaurantStatusResponse
import com.adischat.app.feature.restaurants.data.RestaurantAuditLogDto
import com.adischat.app.feature.restaurants.data.RestaurantsFailure
import com.adischat.app.feature.restaurants.data.RestaurantsRepository
import com.adischat.app.feature.restaurants.presentation.RestaurantBranchUiModel

/**
 * Concrete Phase R4B adapter connecting [RestaurantAdminDataSource] to
 * Phase R3B/R4A `restaurants-admin-review` Edge Functions and admin-scoped PostgREST reads.
 *
 * Kept strictly behind `FeatureFlags.RESTAURANT_ADMIN_ENABLED` inside [RestaurantsRepository].
 */
class RemoteRestaurantAdminDataSource(
    private val repository: RestaurantsRepository,
) : RestaurantAdminDataSource {

    override suspend fun verifyPlatformAdminAccess(accountUid: String): Result<Boolean> =
        repository.amIAdmin().map { res ->
            res.ok && res.isPlatformAdmin
        }

    override suspend fun loadApplications(): Result<List<AdminApplicationUi>> {
        val collected = mutableListOf<AdminApplicationUi>()
        var offset = 0
        val pageSize = 50
        while (true) {
            val page = repository.adminListPendingApplications(
                statuses = listOf("SUBMITTED", "UNDER_REVIEW", "CHANGES_REQUESTED", "APPROVED", "REJECTED"),
                limit = pageSize,
                offset = offset,
            ).getOrElse { return Result.failure(it) }

            val mapped = page.applications.map { app ->
                AdminApplicationUi(
                    id = app.id,
                    applicantProfileId = app.applicantProfileId,
                    brandName = app.brandName,
                    legalName = app.legalName,
                    contactEmail = app.contactEmail,
                    contactPhone = app.contactPhone,
                    tinNumber = app.tinNumber,
                    status = RestaurantApplicationStatus.fromWireValue(app.status),
                    submittedAt = app.submittedAt,
                    branchCount = app.branchCount,
                    draftMenuItemsCount = app.draftMenuItemCount,
                    reviewNotes = app.reviewNotes,
                    approvedRestaurantId = app.approvedRestaurantId,
                    documents = app.documents.map { d ->
                        AdminApplicationDocUi(
                            id = d.id,
                            docType = d.docType,
                            fileName = d.fileName,
                            mimeType = d.mimeType,
                            byteSize = d.byteSize,
                        )
                    },
                )
            }
            collected.addAll(mapped)
            val fetchedCount = page.pagination?.count ?: mapped.size
            if (fetchedCount < pageSize || mapped.isEmpty() || collected.size >= MAX_PAGINATED_APPLICATIONS) {
                break
            }
            offset += fetchedCount
        }
        return Result.success(collected)
    }

    override suspend fun loadRestaurantsAndBranches(): Result<List<AdminRestaurantOverviewUi>> {
        val restaurants = repository.adminListAllRestaurants().getOrElse { return Result.failure(it) }
        val branches = repository.adminListAllBranches().getOrElse { emptyList() }
        val branchesByRest = branches.groupBy { it.restaurantId }

        return Result.success(
            restaurants.map { r ->
                AdminRestaurantOverviewUi(
                    id = r.id,
                    name = r.name,
                    slug = r.slug,
                    status = RestaurantOperationalStatus.fromWireValue(r.status),
                    isLiveOrderingEnabled = r.isLiveOrderingEnabled,
                    suspendedReason = r.suspendedReason,
                    branches = branchesByRest[r.id].orEmpty().map { b ->
                        RestaurantBranchUiModel(
                            id = b.id,
                            restaurantId = b.restaurantId,
                            code = b.code,
                            name = b.name,
                            address = b.address,
                            city = b.city,
                            requireStaffSessionActivation = b.requireStaffSessionActivation,
                            requireSessionCode = b.requireSessionCode,
                            isDineInActive = b.isDineInActive && b.isActive,
                        )
                    },
                )
            },
        )
    }

    override suspend fun loadAuditLogs(): Result<List<RestaurantAuditLogDto>> =
        repository.adminListAuditLogs(limit = 50)

    override suspend fun startReview(
        applicationId: String,
        notes: String?,
    ): Result<AdminApplicationStatusResponse> = repository.adminStartReview(applicationId, notes)

    override suspend fun inspectDocument(
        applicationId: String,
        documentId: String,
    ): Result<String?> = repository.adminInspectDocumentDetails(applicationId, documentId).mapCatching { res ->
        val signedUrl = res.signedUrl?.takeIf { it.isNotBlank() }
            ?: throw RestaurantsFailure.NotFound("document_not_found", "Signed document preview unavailable.")
        signedUrl
    }

    override suspend fun approveApplication(
        applicationId: String,
        slug: String?,
        notes: String?,
    ): Result<AdminApproveResponse> =
        repository.adminApproveApplication(applicationId, slug, notes).map { result ->
            AdminApproveResponse(ok = result != null, result = result)
        }

    override suspend fun rejectApplication(
        applicationId: String,
        notes: String,
    ): Result<AdminApplicationStatusResponse> = repository.adminRejectApplication(applicationId, notes)

    override suspend fun requestChanges(
        applicationId: String,
        notes: String,
    ): Result<AdminApplicationStatusResponse> = repository.adminRequestChanges(applicationId, notes)

    override suspend fun suspendRestaurant(
        restaurantId: String,
        reason: String,
    ): Result<AdminRestaurantStatusResponse> = repository.adminSuspendRestaurant(restaurantId, reason)

    override suspend fun reactivateRestaurant(
        restaurantId: String,
        reason: String,
    ): Result<AdminRestaurantStatusResponse> = repository.adminReactivateRestaurant(restaurantId, reason)

    private companion object {
        const val MAX_PAGINATED_APPLICATIONS = 200
    }
}
