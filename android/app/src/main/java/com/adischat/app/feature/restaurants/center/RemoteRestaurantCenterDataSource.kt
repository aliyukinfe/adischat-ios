package com.adischat.app.feature.restaurants.center

import com.adischat.app.feature.restaurants.data.ActivateRestaurantResponse
import com.adischat.app.feature.restaurants.data.ApplicationDocumentDto
import com.adischat.app.feature.restaurants.data.AuthorizedWorkspaceResponse
import com.adischat.app.feature.restaurants.data.ConfirmDocumentRequest
import com.adischat.app.feature.restaurants.data.CreateBranchRequest
import com.adischat.app.feature.restaurants.data.DiningTableWithStatusDto
import com.adischat.app.feature.restaurants.data.MenuCategoryDto
import com.adischat.app.feature.restaurants.data.MenuItemDto
import com.adischat.app.feature.restaurants.data.MenuItemImageDto
import com.adischat.app.feature.restaurants.data.OpenSessionResponse
import com.adischat.app.feature.restaurants.data.RestaurantApplicationDto
import com.adischat.app.feature.restaurants.data.RestaurantBranchDto
import com.adischat.app.feature.restaurants.data.RestaurantMediaSlot
import com.adischat.app.feature.restaurants.data.RestaurantSummaryDto
import com.adischat.app.feature.restaurants.data.RestaurantsBinaryUploadSource
import com.adischat.app.feature.restaurants.data.RestaurantsFailure
import com.adischat.app.feature.restaurants.data.RestaurantsRepository
import com.adischat.app.feature.restaurants.data.RotateQrResponse
import com.adischat.app.feature.restaurants.data.RotateSessionCodeResponse
import com.adischat.app.feature.restaurants.data.SaveApplicationDraftRequest
import com.adischat.app.feature.restaurants.data.SetBranchAvailabilityRequest
import com.adischat.app.feature.restaurants.data.StaffInviteRequest
import com.adischat.app.feature.restaurants.data.StaffInviteResponse
import com.adischat.app.feature.restaurants.data.StaffInvitationDto
import com.adischat.app.feature.restaurants.data.StaffMemberSummaryDto
import com.adischat.app.feature.restaurants.data.SyncBranchOrderDto
import com.adischat.app.feature.restaurants.data.SyncBranchOrdersResponse
import com.adischat.app.feature.restaurants.data.TransitionOrderResultDto
import com.adischat.app.feature.restaurants.data.UpdateBranchRequest
import com.adischat.app.feature.restaurants.data.UpdateRestaurantSettingsRequest
import com.adischat.app.feature.restaurants.data.UpsertCategoryRequest
import com.adischat.app.feature.restaurants.data.UpsertMenuItemRequest
import com.adischat.app.feature.restaurants.data.UpsertTableRequest
import com.adischat.app.feature.restaurants.presentation.OpeningHourRowUiModel
import com.adischat.app.feature.restaurants.presentation.PaymentStatusUi
import com.adischat.app.feature.restaurants.presentation.RestaurantOrderStatus
import com.google.gson.JsonElement

/**
 * Concrete Phase R4B adapter backing [RestaurantCenterDataSource] with [RestaurantsRepository]
 * across Phase R3B/R4A Edge Functions and PostgREST RLS reads.
 *
 * Kept strictly behind `FeatureFlags.RESTAURANT_CENTER_ENABLED` inside [RestaurantsRepository].
 */
class RemoteRestaurantCenterDataSource(
    private val repository: RestaurantsRepository,
) : RestaurantCenterDataSource {

    @Volatile
    private var cachedWorkspace: AuthorizedWorkspaceResponse? = null

    fun clearCachedWorkspace() {
        cachedWorkspace = null
    }

    override suspend fun loadMyApplications(): Result<List<CenterApplicationUi>> =
        repository.listMyApplications().map { list ->
            list.map(::mapApplicationDto)
        }

    override suspend fun saveApplicationDraft(request: SaveApplicationDraftRequest): Result<CenterApplicationUi> =
        repository.saveApplicationDraft(request).mapCatching { dto ->
            dto?.let(::mapApplicationDto)
                ?: throw RestaurantsFailure.Server("invalid_application_response", "Missing application payload.")
        }

    override suspend fun attachApplicationDocument(request: ConfirmDocumentRequest): Result<CenterApplicationDocUi> {
        if (request.storagePath.isBlank()) {
            return Result.failure(
                RestaurantsFailure.Validation(
                    "missing_document_storage_path",
                    "Document must be uploaded via signed URL before confirming.",
                ),
            )
        }
        return repository.confirmDocument(request).mapCatching { doc ->
            doc?.let(::mapApplicationDocDto)
                ?: throw RestaurantsFailure.Server("invalid_document_response", "Missing confirmed document.")
        }
    }

    override suspend fun uploadAndConfirmApplicationDocument(
        applicationId: String,
        docType: String,
        source: RestaurantsBinaryUploadSource,
    ): Result<CenterApplicationDocUi> = repository.uploadAndConfirmDocument(
        applicationId = applicationId,
        docType = docType,
        source = source,
    ).mapCatching { doc ->
        doc?.let(::mapApplicationDocDto)
            ?: throw RestaurantsFailure.Server("invalid_document_response", "Missing confirmed document.")
    }

    override suspend fun uploadApplicationMedia(
        applicationId: String,
        source: RestaurantsBinaryUploadSource,
    ): Result<String> = repository.uploadApplicationMedia(
        applicationId = applicationId,
        source = source,
    )

    override suspend fun submitApplication(
        applicationId: String,
        idempotencyKey: String?,
    ): Result<CenterApplicationUi> =
        repository.submitApplication(applicationId, idempotencyKey).mapCatching { dto ->
            dto?.let(::mapApplicationDto)
                ?: throw RestaurantsFailure.Server("invalid_application_response", "Missing submitted application.")
        }

    override suspend fun loadAccessibleRestaurants(): Result<List<CenterRestaurantUi>> =
        repository.getMyWorkspace().mapCatching { workspace ->
            cachedWorkspace = workspace
            val membershipsByRestaurant = workspace.memberships
                .filter { it.status.equals("active", ignoreCase = true) }
                .groupBy { it.restaurantId }
            if (workspace.memberships.isNotEmpty() && membershipsByRestaurant.isEmpty()) {
                throw RestaurantsFailure.Forbidden("inactive_restaurant_membership", "Restaurant membership is not active.")
            }

            workspace.restaurants.mapNotNull { rest ->
                val activeMemberships = membershipsByRestaurant[rest.id].orEmpty()
                val primaryMembership = activeMemberships.firstOrNull {
                    it.role.equals("RESTAURANT_OWNER", ignoreCase = true)
                } ?: activeMemberships.firstOrNull()
                val role = RestaurantStaffRole.fromWireValue(primaryMembership?.role)
                if (primaryMembership == null || role == null ||
                    (role != RestaurantStaffRole.RESTAURANT_OWNER && primaryMembership.branchId == null)
                ) {
                    null
                } else {
                    CenterRestaurantUi(
                        id = rest.id,
                        slug = rest.slug,
                        name = rest.name,
                        description = rest.description,
                        status = RestaurantOperationalStatus.fromWireValue(rest.status),
                        isLiveOrderingEnabled = rest.isLiveOrderingEnabled,
                        suspendedReason = rest.suspendedReason,
                        logoPath = rest.logoPath,
                        coverPath = rest.coverPath,
                        currentUserRole = role,
                        currentUserMembershipStatus = primaryMembership.status,
                        assignedBranchId = primaryMembership.branchId,
                        branchRoles = activeMemberships.mapNotNull { membership ->
                            val branchId = membership.branchId ?: return@mapNotNull null
                            val branchRole = RestaurantStaffRole.fromWireValue(membership.role) ?: return@mapNotNull null
                            branchId to branchRole
                        }.toMap(),
                    )
                }
            }
        }

    override suspend fun loadBranches(restaurantId: String): Result<List<CenterBranchUi>> {
        val ws = cachedWorkspace ?: repository.getMyWorkspace().getOrElse { err ->
            return Result.failure(err)
        }.also { cachedWorkspace = it }

        val activeForRestaurant = ws.memberships.filter {
            it.restaurantId == restaurantId && it.status.equals("active", ignoreCase = true)
        }
        if (activeForRestaurant.isEmpty()) {
            return Result.failure(
                RestaurantsFailure.Forbidden("forbidden_not_restaurant_staff", "No active role at this restaurant."),
            )
        }
        val isOwner = activeForRestaurant.any { it.role.equals("RESTAURANT_OWNER", ignoreCase = true) }
        val allowedBranchIds = activeForRestaurant.mapNotNull { it.branchId }.toSet()

        val branches = ws.branches
            .filter { b ->
                b.restaurantId == restaurantId && (isOwner || allowedBranchIds.contains(b.id))
            }
            .map(::mapBranchDto)

        return Result.success(branches)
    }

    override suspend fun createBranch(request: CreateBranchRequest): Result<CenterBranchUi> =
        repository.createBranch(request).mapCatching { dto ->
            dto?.let(::mapBranchDto)
                ?: throw RestaurantsFailure.Server("invalid_branch_response", "Missing created branch.")
        }

    override suspend fun updateBranch(request: UpdateBranchRequest): Result<CenterBranchUi> =
        repository.updateBranch(request).mapCatching { dto ->
            dto?.let(::mapBranchDto)
                ?: throw RestaurantsFailure.Server("invalid_branch_response", "Missing updated branch.")
        }

    override suspend fun updateRestaurantSettings(
        request: UpdateRestaurantSettingsRequest,
    ): Result<RestaurantSummaryDto> = repository.updateRestaurantSettings(request).mapCatching { dto ->
        dto ?: throw RestaurantsFailure.Server("invalid_restaurant_response", "Missing restaurant summary.")
    }

    override suspend fun uploadAndUpdateRestaurantMedia(
        restaurantId: String,
        slot: RestaurantMediaSlot,
        source: RestaurantsBinaryUploadSource,
        description: String?,
    ): Result<RestaurantSummaryDto> = repository.uploadAndApplyRestaurantMedia(
        restaurantId = restaurantId,
        slot = slot,
        source = source,
        description = description,
    ).mapCatching { dto ->
        dto ?: throw RestaurantsFailure.Server("invalid_restaurant_response", "Missing updated restaurant summary.")
    }

    override suspend fun activateRestaurant(restaurantId: String): Result<ActivateRestaurantResponse> =
        repository.activateRestaurant(restaurantId)

    override suspend fun publishLive(restaurantId: String, enable: Boolean): Result<Boolean> =
        repository.publishLive(restaurantId, enable)

    override suspend fun loadMenuCategories(restaurantId: String): Result<List<MenuCategoryDto>> =
        repository.listPartnerMenuCategories(restaurantId)

    override suspend fun loadMenuItemsForBranch(
        restaurantId: String,
        branchId: String,
    ): Result<List<CenterMenuItemUi>> {
        val cats = repository.listPartnerMenuCategories(restaurantId).getOrElse { return Result.failure(it) }
        val items = repository.listPartnerMenuItems(restaurantId).getOrElse { return Result.failure(it) }
        val overrides = repository.listPartnerBranchOverrides(branchId).getOrElse { return Result.failure(it) }
        val images = repository.listPartnerMenuItemImages(restaurantId).getOrElse { emptyList() }

        val catNameById = cats.associate { it.id to it.name }
        val overrideByItem = overrides.associateBy { it.menuItemId }
        val primaryPhotoByItem = images
            .sortedWith(compareByDescending<com.adischat.app.feature.restaurants.data.MenuItemImageDto> { it.isPrimary }.thenBy { it.sortOrder })
            .groupBy { it.menuItemId }
            .mapValues { (_, imgs) -> imgs.firstOrNull()?.storagePath }

        return Result.success(
            items.map { item ->
                val ov = overrideByItem[item.id]
                CenterMenuItemUi(
                    id = item.id,
                    restaurantId = item.restaurantId,
                    categoryId = item.categoryId,
                    categoryName = catNameById[item.categoryId] ?: "Menu",
                    name = item.name,
                    description = item.description,
                    basePriceMinor = item.basePriceMinor,
                    branchPriceOverrideMinor = ov?.priceOverrideMinor,
                    isAvailableAtBranch = ov?.isAvailable ?: true,
                    isActive = item.isActive,
                    photoPath = primaryPhotoByItem[item.id],
                )
            },
        )
    }

    override suspend fun upsertMenuCategory(request: UpsertCategoryRequest): Result<MenuCategoryDto> =
        repository.upsertMenuCategory(request).mapCatching { cat ->
            cat ?: throw RestaurantsFailure.Server("invalid_category_response", "Missing category.")
        }

    override suspend fun upsertMenuItem(request: UpsertMenuItemRequest): Result<MenuItemDto> =
        repository.upsertMenuItem(request).mapCatching { item ->
            item ?: throw RestaurantsFailure.Server("invalid_menu_item_response", "Missing menu item.")
        }

    override suspend fun uploadAndAttachMenuItemImage(
        restaurantId: String,
        menuItemId: String,
        source: RestaurantsBinaryUploadSource,
        isPrimary: Boolean?,
        sortOrder: Int?,
    ): Result<MenuItemImageDto> = repository.uploadAndAttachMenuItemImage(
        restaurantId = restaurantId,
        menuItemId = menuItemId,
        source = source,
        isPrimary = isPrimary,
        sortOrder = sortOrder,
    ).mapCatching { image ->
        image ?: throw RestaurantsFailure.Server("invalid_menu_image_response", "Missing attached menu item image.")
    }

    override suspend fun setBranchAvailability(request: SetBranchAvailabilityRequest): Result<Unit> =
        repository.setBranchAvailability(request).map { Unit }

    override suspend fun loadTables(
        restaurantId: String,
        branchId: String,
    ): Result<List<DiningTableWithStatusDto>> = repository.listTables(restaurantId, branchId)

    override suspend fun upsertTable(request: UpsertTableRequest): Result<CenterTableUi> =
        repository.upsertTable(request).mapCatching { t ->
            val dto = t ?: throw RestaurantsFailure.Server("invalid_table_response", "Missing dining table.")
            CenterTableUi(
                id = dto.id,
                restaurantId = dto.restaurantId,
                branchId = dto.branchId,
                tableCode = dto.tableCode,
                label = dto.label,
                zone = dto.zone,
                capacity = dto.capacity,
                isActive = dto.isActive,
                activeQrVersion = null,
                activeQrStatus = null,
                openSessionId = null,
                openSessionExpiresAt = null,
                hasSessionCode = false,
            )
        }

    override suspend fun rotateTableQr(
        restaurantId: String,
        branchId: String,
        tableId: String,
    ): Result<RotateQrResponse> = repository.rotateTableQr(restaurantId, branchId, tableId)

    override suspend fun revokeTableQr(
        restaurantId: String,
        branchId: String,
        tableId: String,
    ): Result<Unit> = repository.revokeTableQr(restaurantId, branchId, tableId).map { Unit }

    override suspend fun openDiningSession(
        restaurantId: String,
        branchId: String,
        tableId: String,
    ): Result<OpenSessionResponse> = repository.openDiningSession(restaurantId, branchId, tableId)

    override suspend fun rotateSessionCode(
        restaurantId: String,
        branchId: String,
        diningSessionId: String,
    ): Result<RotateSessionCodeResponse> = repository.rotateSessionCode(restaurantId, branchId, diningSessionId)

    override suspend fun closeDiningSession(
        restaurantId: String,
        branchId: String,
        diningSessionId: String,
    ): Result<Unit> = repository.closeDiningSession(restaurantId, branchId, diningSessionId).map { Unit }

    override suspend fun loadStaffMembers(
        restaurantId: String,
        branchId: String?,
    ): Result<List<StaffMemberSummaryDto>> = repository.listStaffMembers(restaurantId, branchId)

    override suspend fun loadStaffInvitations(
        restaurantId: String,
        branchId: String?,
    ): Result<List<StaffInvitationDto>> = repository.listStaffInvitations(restaurantId, branchId)

    override suspend fun inviteStaff(request: StaffInviteRequest): Result<StaffInviteResponse> =
        repository.inviteStaffMember(request)

    override suspend fun revokeStaffMember(staffMemberId: String): Result<Unit> =
        repository.revokeStaffMember(staffMemberId).map { Unit }

    override suspend fun revokeStaffInvitation(invitationId: String): Result<Unit> =
        repository.revokeStaffInvitation(invitationId).map { Unit }

    override suspend fun loadBranchOrders(
        restaurantId: String,
        branchId: String,
    ): Result<List<CenterOrderTicketUi>> =
        syncBranchOrders(restaurantId, branchId, since = null, afterId = null, limit = 50).map { it.first }

    override suspend fun syncBranchOrders(
        restaurantId: String,
        branchId: String,
        since: String?,
        afterId: String?,
        limit: Int?,
    ): Result<Pair<List<CenterOrderTicketUi>, SyncBranchOrdersResponse>> =
        repository.syncBranchOrders(restaurantId, branchId, since, afterId, limit).map { response ->
            val tickets = response.orders
                .filter { it.restaurantId == restaurantId && it.branchId == branchId }
                .map(::mapSyncOrderToTicket)
            tickets to response
        }

    override suspend fun transitionOrderStatus(
        orderId: String,
        targetStatus: String,
        reason: String?,
    ): Result<TransitionOrderResultDto> =
        repository.transitionOrderStatus(orderId, targetStatus, reason).mapCatching { res ->
            res ?: throw RestaurantsFailure.Server("invalid_transition_response", "Missing order transition result.")
        }

    private fun mapApplicationDto(dto: RestaurantApplicationDto): CenterApplicationUi {
        val branchCount = if (dto.draftBranchesJson?.isJsonArray == true) {
            dto.draftBranchesJson.asJsonArray.size()
        } else {
            0
        }
        var primaryBranchName = ""
        var streetAddress = ""
        var cityAndArea = ""
        if (dto.draftBranchesJson?.isJsonArray == true && dto.draftBranchesJson.asJsonArray.size() > 0) {
            val first = dto.draftBranchesJson.asJsonArray.get(0)
            if (first.isJsonObject) {
                val obj = first.asJsonObject
                primaryBranchName = obj.get("name")?.asString.orEmpty()
                streetAddress = obj.get("address")?.asString.orEmpty()
                cityAndArea = obj.get("city")?.asString.orEmpty()
            }
        }
        return CenterApplicationUi(
            id = dto.id,
            applicantProfileId = dto.applicantProfileId.orEmpty(),
            legalName = dto.legalName,
            brandName = dto.brandName,
            description = dto.description,
            businessType = dto.businessType,
            contactPhone = dto.contactPhone,
            contactEmail = dto.contactEmail,
            tinNumber = dto.tinNumber,
            registrationNumber = dto.registrationNumber.orEmpty().ifEmpty { dto.tinNumber },
            operatingHoursSummary = dto.operatingHoursSummary.orEmpty(),
            isDineInAvailable = dto.isDineInAvailable,
            clientDraftKey = dto.clientDraftKey,
            primaryBranchName = primaryBranchName,
            streetAddress = streetAddress,
            cityAndArea = cityAndArea,
            logoPath = dto.logoPath,
            coverPath = dto.coverPath,
            status = RestaurantApplicationStatus.fromWireValue(dto.status),
            reviewNotes = dto.reviewNotes,
            approvedRestaurantId = dto.approvedRestaurantId,
            branchDraftCount = branchCount,
            documents = dto.documents.map(::mapApplicationDocDto),
        )
    }

    private fun mapApplicationDocDto(d: ApplicationDocumentDto): CenterApplicationDocUi =
        CenterApplicationDocUi(
            id = d.id,
            docType = d.docType,
            fileName = d.fileName,
            mimeType = d.mimeType,
            byteSize = d.byteSize,
        )

    private fun mapBranchDto(dto: RestaurantBranchDto): CenterBranchUi =
        CenterBranchUi(
            id = dto.id,
            restaurantId = dto.restaurantId,
            code = dto.code,
            name = dto.name,
            address = dto.address,
            city = dto.city,
            requireStaffSessionActivation = dto.requireStaffSessionActivation,
            requireSessionCode = dto.requireSessionCode,
            isDineInActive = dto.isDineInActive,
            status = dto.status,
            openingHours = parseOpeningHours(dto.openingHoursJson),
        )

    private fun mapSyncOrderToTicket(dto: SyncBranchOrderDto): CenterOrderTicketUi {
        val itemCount = dto.orderItems.sumOf { it.quantity }
        val itemsSummary = if (dto.orderItems.isNotEmpty()) {
            dto.orderItems.joinToString(", ") { "${it.quantity}x ${it.itemNameSnapshot}" }
        } else {
            "$itemCount item(s)"
        }
        val status = RestaurantOrderStatus.fromWireValue(dto.status)
        val terminalReason = if (status.isTerminalNegative) {
            dto.rejectedReason?.takeIf { it.isNotBlank() }
                ?: dto.cancelledReason?.takeIf { it.isNotBlank() }
                ?: dto.orderStatusEvents.lastOrNull {
                    RestaurantOrderStatus.fromWireValue(it.toStatus) == status
                }?.reason?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        return CenterOrderTicketUi(
            orderId = dto.id,
            orderNumber = dto.orderNumber,
            restaurantId = dto.restaurantId,
            branchId = dto.branchId,
            tableLabel = dto.tableId?.let { "Table ${it.take(6)}" } ?: "Seated Table",
            itemCountLabel = itemsSummary,
            status = status,
            paymentStatus = if (dto.paymentStatus.equals("PAID", ignoreCase = true)) {
                PaymentStatusUi.PAID
            } else {
                PaymentStatusUi.UNPAID
            },
            totalMinor = dto.totalMinor,
            customerNotes = dto.customerNotes?.takeIf { it.isNotBlank() },
            terminalReason = terminalReason,
            createdAtLabel = dto.createdAt,
        )
    }

    private fun parseOpeningHours(json: JsonElement?): List<OpeningHourRowUiModel> {
        if (json == null || json.isJsonNull) return emptyList()
        return try {
            when {
                json.isJsonObject -> json.asJsonObject.entrySet().map { (k, v) ->
                    OpeningHourRowUiModel(
                        dayLabel = k,
                        hoursLabel = if (v.isJsonPrimitive) v.asString else v.toString(),
                    )
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
