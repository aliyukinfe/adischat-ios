package com.adischat.app.feature.restaurants.data

import retrofit2.HttpException
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Injectable abstraction for isolated customer flow coordination & deterministic unit tests.
 */
interface RestaurantsCustomerDataSource {
    suspend fun listLiveRestaurants(): Result<List<RestaurantSummaryDto>>
    suspend fun getRestaurantById(restaurantId: String): Result<RestaurantSummaryDto?>
    suspend fun listRestaurantBranches(restaurantId: String): Result<List<RestaurantBranchDto>>
    suspend fun listMenuCategories(restaurantId: String): Result<List<MenuCategoryDto>>
    suspend fun listMenuItems(restaurantId: String): Result<List<MenuItemDto>>
    suspend fun listMenuItemImages(restaurantId: String): Result<List<MenuItemImageDto>>
    suspend fun listMenuOptionGroups(restaurantId: String): Result<List<MenuOptionGroupDto>>
    suspend fun listMenuOptions(restaurantId: String): Result<List<MenuOptionDto>>
    suspend fun listBranchOverrides(branchId: String): Result<List<BranchMenuItemOverrideDto>>
    suspend fun resolveTableQr(token: String, sessionCode: String? = null): Result<ResolveQrResponse>
    suspend fun submitDineInOrder(
        diningSessionId: String,
        branchId: String? = null,
        items: List<SubmitOrderItemRequest>,
        customerNotes: String? = null,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): Result<SubmitOrderResultDto>
    suspend fun getOrderDetails(orderId: String): Result<OrderRecordDto?>
    suspend fun listOrderItems(orderId: String): Result<List<OrderItemRecordDto>>
    suspend fun listOrderStatusEvents(orderId: String): Result<List<OrderStatusEventDto>>
    suspend fun listOrderPayments(orderId: String): Result<List<OrderPaymentRecordDto>>
    suspend fun listCustomerOrdersForRestaurant(
        customerProfileId: String,
        restaurantId: String,
    ): Result<List<OrderRecordDto>> = Result.success(emptyList())
}

/**
 * Isolated offline-ready repository covering all Phase R3B Restaurants endpoints
 * and RLS-protected PostgREST read queries.
 *
 * All customer dine-in, partner center, and admin operations remain gated behind
 * disabled-by-default feature flags until R4+ rollout.
 */
class RestaurantsRepository(
    private val environmentProvider: () -> RestaurantsEnvironment = { RestaurantsConfig.currentEnvironment },
    private val sessionManager: RestaurantsSessionManager = RestaurantsSessionManager(
        environmentProvider = environmentProvider,
    ),
    private val edgeApiProvider: () -> RestaurantsEdgeApi? = {
        RestaurantsApiClientFactory.createEdgeApi(environmentProvider())
    },
    private val postgrestApiProvider: () -> RestaurantsPostgrestApi? = {
        RestaurantsApiClientFactory.createPostgrestApi(environmentProvider())
    },
    private val signedUploaderProvider: () -> RestaurantsSignedUploader = {
        OkHttpRestaurantsSignedUploader()
    },
) : RestaurantsCustomerDataSource {
    private val confirmingStoragePaths = ConcurrentHashMap.newKeySet<String>()
    private val confirmedStoragePaths = Collections.synchronizedSet(LinkedHashSet<String>())
    // ========================================================================
    // Customer discovery & catalog reads (PostgREST + RLS)
    // ========================================================================

    override suspend fun listLiveRestaurants(): Result<List<RestaurantSummaryDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listRestaurants(bearerAuth = auth)
        }

    override suspend fun getRestaurantById(restaurantId: String): Result<RestaurantSummaryDto?> =
        runDineInRead { auth, postgrest ->
            postgrest.getRestaurantById(bearerAuth = auth, idFilter = "eq.$restaurantId").firstOrNull()
        }

    override suspend fun listRestaurantBranches(restaurantId: String): Result<List<RestaurantBranchDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listBranches(bearerAuth = auth, restaurantIdFilter = "eq.$restaurantId")
        }

    override suspend fun listMenuCategories(restaurantId: String): Result<List<MenuCategoryDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listMenuCategories(bearerAuth = auth, restaurantIdFilter = "eq.$restaurantId")
        }

    override suspend fun listMenuItems(restaurantId: String): Result<List<MenuItemDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listMenuItems(bearerAuth = auth, restaurantIdFilter = "eq.$restaurantId")
        }

    override suspend fun listMenuItemImages(restaurantId: String): Result<List<MenuItemImageDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listMenuItemImages(bearerAuth = auth, restaurantIdFilter = "eq.$restaurantId")
        }

    override suspend fun listMenuOptionGroups(restaurantId: String): Result<List<MenuOptionGroupDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listMenuOptionGroups(bearerAuth = auth, restaurantIdFilter = "eq.$restaurantId")
        }

    override suspend fun listMenuOptions(restaurantId: String): Result<List<MenuOptionDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listMenuOptions(bearerAuth = auth, restaurantIdFilter = "eq.$restaurantId")
        }

    override suspend fun listBranchOverrides(branchId: String): Result<List<BranchMenuItemOverrideDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listBranchOverrides(bearerAuth = auth, branchIdFilter = "eq.$branchId")
        }

    // ========================================================================
    // Dine-in QR resolution, order submission & tracking
    // ========================================================================

    override suspend fun resolveTableQr(
        token: String,
        sessionCode: String?,
    ): Result<ResolveQrResponse> = runDineInEdge { auth, edge ->
        edge.resolveTableQr(
            bearerAuth = auth,
            body = ResolveQrRequest(token = token, sessionCode = sessionCode),
        )
    }

    /**
     * Submits a DINE_IN + PAY_AT_RESTAURANT order while preserving caller-provided
     * [idempotencyKey] across 401 session-refresh retries.
     */
    override suspend fun submitDineInOrder(
        diningSessionId: String,
        branchId: String?,
        items: List<SubmitOrderItemRequest>,
        customerNotes: String?,
        idempotencyKey: String,
    ): Result<SubmitOrderResultDto> {
        val env = environmentProvider()
        if (env.areOrderMutationsBlockedInQa()) {
            return Result.failure(
                RestaurantsFailure.QaBlocked("Order mutations are disabled in QA builds."),
            )
        }
        val cleanKey = idempotencyKey.trim()
        if (cleanKey.length < 8) {
            return Result.failure(
                RestaurantsFailure.Validation(
                    "missing_idempotency_key",
                    "Valid idempotency key (minimum 8 characters) is required.",
                ),
            )
        }
        if (items.isEmpty() || items.any { it.quantity <= 0 }) {
            return Result.failure(
                RestaurantsFailure.Validation(
                    "invalid_items",
                    "Order must include at least one item with a positive quantity.",
                ),
            )
        }

        val request = SubmitDineInOrderRequest(
            diningSessionId = diningSessionId,
            branchId = branchId,
            idempotencyKey = cleanKey,
            orderType = "DINE_IN",
            paymentMethod = "PAY_AT_RESTAURANT",
            customerNotes = customerNotes,
            items = items,
        )

        return runDineInEdge { auth, edge ->
            val response = edge.submitDineInOrder(bearerAuth = auth, body = request)
            response.order ?: throw RestaurantsFailure.Server(
                "invalid_order_response",
                "Restaurants backend did not return order confirmation.",
            )
        }
    }

    suspend fun listCustomerOrders(customerProfileId: String): Result<List<OrderRecordDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listOrders(
                bearerAuth = auth,
                customerFilter = "eq.$customerProfileId",
            ).filter { it.customerProfileId == customerProfileId }
        }

    override suspend fun listCustomerOrdersForRestaurant(
        customerProfileId: String,
        restaurantId: String,
    ): Result<List<OrderRecordDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listOrders(
                bearerAuth = auth,
                customerFilter = "eq.$customerProfileId",
                restaurantIdFilter = "eq.$restaurantId",
            ).filter {
                it.customerProfileId == customerProfileId && it.restaurantId == restaurantId
            }
        }

    override suspend fun getOrderDetails(orderId: String): Result<OrderRecordDto?> =
        runDineInRead { auth, postgrest ->
            postgrest.getOrderById(bearerAuth = auth, idFilter = "eq.$orderId").firstOrNull()
        }

    override suspend fun listOrderItems(orderId: String): Result<List<OrderItemRecordDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listOrderItems(bearerAuth = auth, orderIdFilter = "eq.$orderId")
        }

    override suspend fun listOrderStatusEvents(orderId: String): Result<List<OrderStatusEventDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listOrderStatusEvents(bearerAuth = auth, orderIdFilter = "eq.$orderId")
        }

    override suspend fun listOrderPayments(orderId: String): Result<List<OrderPaymentRecordDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listOrderPayments(bearerAuth = auth, orderIdFilter = "eq.$orderId")
        }

    suspend fun listNotifications(): Result<List<RestaurantNotificationDto>> =
        runDineInRead { auth, postgrest ->
            postgrest.listNotifications(bearerAuth = auth)
        }

    suspend fun reportContent(
        restaurantId: String,
        menuItemId: String? = null,
        reason: String,
    ): Result<RestaurantReportDto?> = runDineInEdge { auth, edge ->
        edge.reportRestaurantContent(
            bearerAuth = auth,
            body = ReportContentRequest(
                restaurantId = restaurantId,
                menuItemId = menuItemId,
                reason = reason,
            ),
        ).report
    }

    // ========================================================================
    // Partner onboarding & Restaurant Center (`restaurants-application`, `staff`, `menu`, `tables-qr`, `orders`)
    // ========================================================================

    suspend fun listMyApplications(): Result<List<RestaurantApplicationDto>> =
        runPartnerEdge { auth, edge ->
            edge.listMyApplications(bearerAuth = auth).applications
        }

    suspend fun saveApplicationDraft(request: SaveApplicationDraftRequest): Result<RestaurantApplicationDto?> =
        runPartnerEdge { auth, edge ->
            edge.saveApplicationDraft(bearerAuth = auth, body = request).application
        }

    suspend fun requestDocumentUploadUrl(request: DocumentUploadUrlRequest): Result<DocumentUploadUrlResponse> =
        runPartnerEdge { auth, edge ->
            edge.requestDocumentUploadUrl(bearerAuth = auth, body = request)
        }

    suspend fun confirmDocument(request: ConfirmDocumentRequest): Result<ApplicationDocumentDto?> =
        executeSingleConfirmation(request.storagePath) {
            runPartnerEdge { auth, edge ->
                edge.confirmDocument(bearerAuth = auth, body = request).document
            }
        }

    suspend fun uploadAndConfirmDocument(
        applicationId: String,
        docType: String,
        source: RestaurantsBinaryUploadSource,
    ): Result<ApplicationDocumentDto?> {
        if (applicationId.isBlank()) {
            return Result.failure(
                RestaurantsFailure.Validation("invalid_application_id", "Valid application ID is required."),
            )
        }
        val normalizedDocType = try {
            RestaurantsUploadValidator.validateDocType(docType)
        } catch (t: Throwable) {
            return Result.failure(RestaurantsErrorMapper.mapThrowable(t))
        }
        return executeSignedUploadFlow(
            kind = RestaurantsUploadKind.APPLICATION_DOCUMENT,
            source = source,
            mintUploadUrl = { metadata ->
                val res = requestDocumentUploadUrl(
                    DocumentUploadUrlRequest(
                        applicationId = applicationId,
                        docType = normalizedDocType,
                        fileName = metadata.fileName,
                        mimeType = metadata.mimeType,
                        byteSize = metadata.byteSize,
                    ),
                ).getOrThrow()
                res.ok to (res.uploadUrl to res.storagePath)
            },
            afterUpload = { storagePath, metadata ->
                confirmDocument(
                    ConfirmDocumentRequest(
                        applicationId = applicationId,
                        docType = normalizedDocType,
                        storagePath = storagePath,
                        fileName = metadata.fileName,
                        mimeType = metadata.mimeType,
                        byteSize = metadata.byteSize,
                    ),
                ).getOrThrow()
            },
        )
    }

    suspend fun requestApplicationMediaUploadUrl(
        request: ApplicationMediaUploadUrlRequest,
    ): Result<MediaUploadUrlResponse> = runPartnerEdge { auth, edge ->
        edge.requestApplicationMediaUploadUrl(bearerAuth = auth, body = request)
    }

    suspend fun uploadApplicationMedia(
        applicationId: String,
        source: RestaurantsBinaryUploadSource,
    ): Result<String> {
        if (applicationId.isBlank()) {
            return Result.failure(
                RestaurantsFailure.Validation("invalid_application_id", "Valid application ID is required."),
            )
        }
        return executeSignedUploadFlow(
            kind = RestaurantsUploadKind.PUBLIC_MEDIA,
            source = source,
            mintUploadUrl = { metadata ->
                val res = requestApplicationMediaUploadUrl(
                    ApplicationMediaUploadUrlRequest(
                        applicationId = applicationId,
                        fileName = metadata.fileName,
                        mimeType = metadata.mimeType,
                        byteSize = metadata.byteSize,
                    ),
                ).getOrThrow()
                res.ok to (res.uploadUrl to res.storagePath)
            },
            afterUpload = { storagePath, _ -> storagePath },
        )
    }

    suspend fun submitApplication(
        applicationId: String,
        idempotencyKey: String? = null,
    ): Result<RestaurantApplicationDto?> =
        runPartnerEdge { auth, edge ->
            edge.submitApplication(
                bearerAuth = auth,
                body = SubmitApplicationRequest(
                    applicationId = applicationId,
                    idempotencyKey = idempotencyKey,
                ),
            ).application
        }

    suspend fun listMyStaffInvitations(): Result<List<StaffInvitationDto>> =
        runPartnerEdge { auth, edge ->
            edge.listMyInvitations(bearerAuth = auth).invitations
        }

    suspend fun acceptStaffInvitation(token: String): Result<StaffMembershipDto?> =
        runPartnerEdge { auth, edge ->
            edge.acceptStaffInvitation(bearerAuth = auth, body = StaffAcceptRequest(token)).membership
        }

    suspend fun listStaffMembers(
        restaurantId: String,
        branchId: String? = null,
    ): Result<List<StaffMemberSummaryDto>> = runPartnerEdge { auth, edge ->
        edge.listStaffMembers(
            bearerAuth = auth,
            body = StaffListMembersRequest(restaurantId, branchId),
        ).members
    }

    suspend fun listStaffInvitations(
        restaurantId: String,
        branchId: String? = null,
    ): Result<List<StaffInvitationDto>> = runPartnerEdge { auth, edge ->
        edge.listStaffInvitations(
            bearerAuth = auth,
            body = StaffListInvitationsRequest(restaurantId, branchId),
        ).invitations
    }

    suspend fun inviteStaffMember(request: StaffInviteRequest): Result<StaffInviteResponse> =
        runPartnerEdge { auth, edge ->
            edge.inviteStaffMember(bearerAuth = auth, body = request)
        }

    suspend fun revokeStaffInvitation(invitationId: String): Result<StaffRevokeInvitationResponse> =
        runPartnerEdge { auth, edge ->
            edge.revokeStaffInvitation(
                bearerAuth = auth,
                body = StaffRevokeInvitationRequest(invitationId),
            )
        }

    suspend fun revokeStaffMember(staffMemberId: String): Result<StaffRevokeResponse> =
        runPartnerEdge { auth, edge ->
            edge.revokeStaffMember(bearerAuth = auth, body = StaffRevokeRequest(staffMemberId))
        }

    suspend fun createBranch(request: CreateBranchRequest): Result<RestaurantBranchDto?> =
        runPartnerEdge { auth, edge ->
            edge.createBranch(bearerAuth = auth, body = request).branch
        }

    suspend fun updateBranch(request: UpdateBranchRequest): Result<RestaurantBranchDto?> =
        runPartnerEdge { auth, edge ->
            edge.updateBranch(bearerAuth = auth, body = request).branch
        }

    suspend fun updateRestaurantSettings(
        request: UpdateRestaurantSettingsRequest,
    ): Result<RestaurantSummaryDto?> = runPartnerEdge { auth, edge ->
        edge.updateRestaurantSettings(bearerAuth = auth, body = request).restaurant
    }

    suspend fun setBranchAvailability(request: SetBranchAvailabilityRequest): Result<RestaurantsOkResponse> =
        runPartnerEdge { auth, edge ->
            edge.setBranchAvailability(bearerAuth = auth, body = request)
        }

    suspend fun upsertMenuCategory(request: UpsertCategoryRequest): Result<MenuCategoryDto?> =
        runPartnerEdge { auth, edge ->
            edge.upsertMenuCategory(bearerAuth = auth, body = request).category
        }

    suspend fun upsertMenuItem(request: UpsertMenuItemRequest): Result<MenuItemDto?> =
        runPartnerEdge { auth, edge ->
            edge.upsertMenuItem(bearerAuth = auth, body = request).item
        }

    suspend fun upsertOptionGroup(request: UpsertOptionGroupRequest): Result<MenuOptionGroupDto?> =
        runPartnerEdge { auth, edge ->
            edge.upsertOptionGroup(bearerAuth = auth, body = request).optionGroup
        }

    suspend fun upsertOption(request: UpsertOptionRequest): Result<MenuOptionDto?> =
        runPartnerEdge { auth, edge ->
            edge.upsertOption(bearerAuth = auth, body = request).option
        }

    suspend fun requestMenuPhotoUploadUrl(request: PhotoUploadUrlRequest): Result<MediaUploadUrlResponse> =
        runPartnerEdge { auth, edge ->
            edge.requestMenuPhotoUploadUrl(bearerAuth = auth, body = request)
        }

    suspend fun attachMenuItemImage(request: AttachMenuItemImageRequest): Result<MenuItemImageDto?> =
        executeSingleConfirmation(request.storagePath) {
            runPartnerEdge { auth, edge ->
                edge.attachMenuItemImage(bearerAuth = auth, body = request).image
            }
        }

    suspend fun uploadAndAttachMenuItemImage(
        restaurantId: String,
        menuItemId: String,
        source: RestaurantsBinaryUploadSource,
        isPrimary: Boolean? = null,
        sortOrder: Int? = null,
    ): Result<MenuItemImageDto?> {
        if (restaurantId.isBlank() || menuItemId.isBlank()) {
            return Result.failure(
                RestaurantsFailure.Validation("invalid_menu_item_id", "Valid restaurant and menu item IDs are required."),
            )
        }
        return executeSignedUploadFlow(
            kind = RestaurantsUploadKind.PUBLIC_MEDIA,
            source = source,
            mintUploadUrl = { metadata ->
                val res = requestMenuPhotoUploadUrl(
                    PhotoUploadUrlRequest(
                        restaurantId = restaurantId,
                        fileName = metadata.fileName,
                        mimeType = metadata.mimeType,
                        byteSize = metadata.byteSize,
                    ),
                ).getOrThrow()
                res.ok to (res.uploadUrl to res.storagePath)
            },
            afterUpload = { storagePath, _ ->
                attachMenuItemImage(
                    AttachMenuItemImageRequest(
                        restaurantId = restaurantId,
                        menuItemId = menuItemId,
                        storagePath = storagePath,
                        isPrimary = isPrimary,
                        sortOrder = sortOrder,
                    ),
                ).getOrThrow()
            },
        )
    }

    suspend fun uploadRestaurantMedia(
        restaurantId: String,
        source: RestaurantsBinaryUploadSource,
    ): Result<String> {
        if (restaurantId.isBlank()) {
            return Result.failure(
                RestaurantsFailure.Validation("invalid_restaurant_id", "Valid restaurant ID is required."),
            )
        }
        return executeSignedUploadFlow(
            kind = RestaurantsUploadKind.PUBLIC_MEDIA,
            source = source,
            mintUploadUrl = { metadata ->
                val res = requestMenuPhotoUploadUrl(
                    PhotoUploadUrlRequest(
                        restaurantId = restaurantId,
                        fileName = metadata.fileName,
                        mimeType = metadata.mimeType,
                        byteSize = metadata.byteSize,
                    ),
                ).getOrThrow()
                res.ok to (res.uploadUrl to res.storagePath)
            },
            afterUpload = { storagePath, _ -> storagePath },
        )
    }

    suspend fun uploadAndApplyRestaurantMedia(
        restaurantId: String,
        slot: RestaurantMediaSlot,
        source: RestaurantsBinaryUploadSource,
        description: String? = null,
    ): Result<RestaurantSummaryDto?> {
        if (restaurantId.isBlank()) {
            return Result.failure(
                RestaurantsFailure.Validation("invalid_restaurant_id", "Valid restaurant ID is required."),
            )
        }
        return executeSignedUploadFlow(
            kind = RestaurantsUploadKind.PUBLIC_MEDIA,
            source = source,
            mintUploadUrl = { metadata ->
                val res = requestMenuPhotoUploadUrl(
                    PhotoUploadUrlRequest(
                        restaurantId = restaurantId,
                        fileName = metadata.fileName,
                        mimeType = metadata.mimeType,
                        byteSize = metadata.byteSize,
                    ),
                ).getOrThrow()
                res.ok to (res.uploadUrl to res.storagePath)
            },
            afterUpload = { storagePath, _ ->
                executeSingleConfirmation("settings:$restaurantId:${slot.name}:$storagePath") {
                    updateRestaurantSettings(
                        UpdateRestaurantSettingsRequest(
                            restaurantId = restaurantId,
                            description = description,
                            logoPath = if (slot == RestaurantMediaSlot.LOGO) storagePath else null,
                            coverPath = if (slot == RestaurantMediaSlot.COVER) storagePath else null,
                        ),
                    )
                }.getOrThrow()
            },
        )
    }

    suspend fun removeMenuItemImage(restaurantId: String, imageId: String): Result<Boolean> =
        runPartnerEdge { auth, edge ->
            edge.removeMenuItemImage(
                bearerAuth = auth,
                body = RemoveMenuItemImageRequest(restaurantId, imageId),
            ).removed
        }

    suspend fun publishLive(restaurantId: String, enable: Boolean): Result<Boolean> =
        runPartnerEdge { auth, edge ->
            edge.publishLive(
                bearerAuth = auth,
                body = PublishLiveRequest(restaurantId, enable),
            ).isLiveOrderingEnabled
        }

    suspend fun getMyWorkspace(): Result<AuthorizedWorkspaceResponse> =
        runPartnerEdge { auth, edge ->
            edge.getMyWorkspace(bearerAuth = auth)
        }

    suspend fun authorizeRestaurantCenterQr(
        challengeId: String,
        challengeNonce: String,
        cancel: Boolean = false,
    ): Result<AuthorizeQrChallengeResponse> = runPartnerEdge { auth, edge ->
        edge.authorizeQrChallenge(
            bearerAuth = auth,
            body = AuthorizeQrChallengeRequest(
                challengeId = challengeId.trim(),
                challengeNonce = challengeNonce.trim(),
                action = if (cancel) "cancel" else null,
            ),
        )
    }

    suspend fun createRestaurantCenterHandoffTicket(): Result<CreateHandoffTicketResponse> =
        runPartnerEdge { auth, edge ->
            edge.createHandoffTicket(bearerAuth = auth)
        }

    suspend fun checkRestaurantCenterEligibility(): Result<RestaurantCenterEligibility> =
        runCatching {
            val workspace = getMyWorkspace().getOrThrow()
            val eligibleMembership = workspace.memberships.firstOrNull { m ->
                m.status.equals("ACTIVE", ignoreCase = true) &&
                    (m.role == "RESTAURANT_OWNER" || m.role == "RESTAURANT_MANAGER" || m.role == "PLATFORM_SUPER_ADMIN")
            }
            if (eligibleMembership != null) {
                RestaurantCenterEligibility(
                    isEligible = true,
                    role = eligibleMembership.role,
                    restaurantId = eligibleMembership.restaurantId,
                    branchId = eligibleMembership.branchId,
                )
            } else {
                RestaurantCenterEligibility(isEligible = false)
            }
        }

    suspend fun activateRestaurant(restaurantId: String): Result<ActivateRestaurantResponse> =
        runPartnerEdge { auth, edge ->
            edge.activateRestaurant(
                bearerAuth = auth,
                body = RestaurantScopedRequest(restaurantId),
            )
        }

    suspend fun syncBranchOrders(
        restaurantId: String,
        branchId: String,
        since: String? = null,
        afterId: String? = null,
        limit: Int? = null,
    ): Result<SyncBranchOrdersResponse> = runPartnerEdge { auth, edge ->
        edge.syncBranchOrders(
            bearerAuth = auth,
            body = SyncBranchOrdersRequest(
                restaurantId = restaurantId,
                branchId = branchId,
                since = since,
                afterId = afterId,
                limit = limit,
            ),
        )
    }

    suspend fun listPartnerMenuCategories(restaurantId: String): Result<List<MenuCategoryDto>> =
        runPartnerRead { auth, postgrest ->
            postgrest.listMenuCategories(
                bearerAuth = auth,
                restaurantIdFilter = "eq.$restaurantId",
                isActiveFilter = null,
            )
        }

    suspend fun listPartnerMenuItems(restaurantId: String): Result<List<MenuItemDto>> =
        runPartnerRead { auth, postgrest ->
            postgrest.listMenuItems(
                bearerAuth = auth,
                restaurantIdFilter = "eq.$restaurantId",
                isActiveFilter = null,
            )
        }

    suspend fun listPartnerBranchOverrides(branchId: String): Result<List<BranchMenuItemOverrideDto>> =
        runPartnerRead { auth, postgrest ->
            postgrest.listBranchOverrides(bearerAuth = auth, branchIdFilter = "eq.$branchId")
        }

    suspend fun listPartnerMenuItemImages(restaurantId: String): Result<List<MenuItemImageDto>> =
        runPartnerRead { auth, postgrest ->
            postgrest.listMenuItemImages(bearerAuth = auth, restaurantIdFilter = "eq.$restaurantId")
        }

    suspend fun listBranchOrders(branchId: String): Result<List<OrderRecordDto>> =
        runPartnerRead { auth, postgrest ->
            postgrest.listOrders(bearerAuth = auth, branchFilter = "eq.$branchId")
        }

    suspend fun listKitchenTickets(branchId: String): Result<List<KitchenTicketDto>> =
        runPartnerRead { auth, postgrest ->
            postgrest.listKitchenTickets(bearerAuth = auth, branchIdFilter = "eq.$branchId")
        }

    suspend fun listTables(
        restaurantId: String,
        branchId: String,
    ): Result<List<DiningTableWithStatusDto>> = runPartnerEdge { auth, edge ->
        edge.listTables(
            bearerAuth = auth,
            body = ListTablesRequest(restaurantId, branchId),
        ).tables
    }

    suspend fun openDiningSession(
        restaurantId: String,
        branchId: String,
        tableId: String,
    ): Result<OpenSessionResponse> = runPartnerEdge { auth, edge ->
        edge.openDiningSession(
            bearerAuth = auth,
            body = TableScopedRequest(restaurantId, branchId, tableId),
        )
    }

    suspend fun upsertTable(request: UpsertTableRequest): Result<DiningTableDto?> =
        runPartnerEdge { auth, edge ->
            edge.upsertTable(bearerAuth = auth, body = request).table
        }

    suspend fun rotateTableQr(
        restaurantId: String,
        branchId: String,
        tableId: String,
    ): Result<RotateQrResponse> = runPartnerEdge { auth, edge ->
        edge.rotateTableQr(
            bearerAuth = auth,
            body = TableScopedRequest(restaurantId, branchId, tableId),
        )
    }

    suspend fun revokeTableQr(
        restaurantId: String,
        branchId: String,
        tableId: String,
    ): Result<RestaurantsOkResponse> = runPartnerEdge { auth, edge ->
        edge.revokeTableQr(
            bearerAuth = auth,
            body = TableScopedRequest(restaurantId, branchId, tableId),
        )
    }

    suspend fun rotateSessionCode(
        restaurantId: String,
        branchId: String,
        diningSessionId: String,
    ): Result<RotateSessionCodeResponse> = runPartnerEdge { auth, edge ->
        edge.rotateSessionCode(
            bearerAuth = auth,
            body = DiningSessionScopedRequest(restaurantId, branchId, diningSessionId),
        )
    }

    suspend fun closeDiningSession(
        restaurantId: String,
        branchId: String,
        diningSessionId: String,
    ): Result<RestaurantsOkResponse> = runPartnerEdge { auth, edge ->
        edge.closeDiningSession(
            bearerAuth = auth,
            body = DiningSessionScopedRequest(restaurantId, branchId, diningSessionId),
        )
    }

    suspend fun transitionOrderStatus(
        orderId: String,
        targetStatus: String,
        reason: String? = null,
    ): Result<TransitionOrderResultDto?> = runPartnerEdge { auth, edge ->
        edge.transitionOrderStatus(
            bearerAuth = auth,
            body = TransitionOrderRequest(orderId, targetStatus, reason),
        ).result
    }

    suspend fun confirmOrderPayment(
        orderId: String,
        receiptRef: String? = null,
        notes: String? = null,
    ): Result<ConfirmPaymentResultDto?> = runPartnerEdge { auth, edge ->
        edge.confirmOrderPayment(
            bearerAuth = auth,
            body = ConfirmPaymentRequest(orderId, receiptRef, notes),
        ).result
    }

    // ========================================================================
    // Admin moderation & reviews (`restaurants-admin-review`)
    // ========================================================================

    suspend fun amIAdmin(): Result<AmIAdminResponse> =
        runAdminEdge { auth, edge ->
            edge.amIAdmin(bearerAuth = auth)
        }

    suspend fun adminListPendingApplications(
        statuses: List<String>? = null,
        status: String? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): Result<AdminListApplicationsResponse> = runAdminEdge { auth, edge ->
        edge.adminListPendingApplications(
            bearerAuth = auth,
            body = AdminListApplicationsRequest(
                statuses = statuses,
                status = status,
                limit = limit,
                offset = offset,
            ),
        )
    }

    suspend fun adminInspectDocumentDetails(
        applicationId: String,
        documentId: String,
    ): Result<AdminInspectDocumentResponse> = runAdminEdge { auth, edge ->
        edge.adminInspectDocument(
            bearerAuth = auth,
            body = AdminInspectDocumentRequest(applicationId, documentId),
        )
    }

    suspend fun adminListAllRestaurants(): Result<List<RestaurantSummaryDto>> =
        runAdminRead { auth, postgrest ->
            postgrest.listRestaurants(bearerAuth = auth, statusFilter = null, liveFilter = null)
        }

    suspend fun adminListAllBranches(): Result<List<RestaurantBranchDto>> =
        runAdminRead { auth, postgrest ->
            postgrest.listBranches(bearerAuth = auth, restaurantIdFilter = null, statusFilter = null)
        }

    suspend fun adminStartReview(applicationId: String, notes: String? = null): Result<AdminApplicationStatusResponse> =
        runAdminEdge { auth, edge ->
            edge.adminStartReview(
                bearerAuth = auth,
                body = AdminReviewActionRequest(applicationId, notes = notes),
            )
        }

    suspend fun adminApproveApplication(
        applicationId: String,
        slug: String,
        notes: String? = null,
    ): Result<AdminApproveResultDto?> = runAdminEdge { auth, edge ->
        edge.adminApproveApplication(
            bearerAuth = auth,
            action = "approve",
            body = AdminReviewActionRequest(
                applicationId = applicationId,
                slug = slug.trim().lowercase(),
                notes = notes?.trim().orEmpty(),
            ),
        ).result
    }

    suspend fun adminRejectApplication(
        applicationId: String,
        notes: String,
    ): Result<AdminApplicationStatusResponse> = runAdminEdge { auth, edge ->
        edge.adminRejectApplication(
            bearerAuth = auth,
            body = AdminReviewActionRequest(applicationId, notes = notes),
        )
    }

    suspend fun adminRequestChanges(
        applicationId: String,
        notes: String,
    ): Result<AdminApplicationStatusResponse> = runAdminEdge { auth, edge ->
        edge.adminRequestChanges(
            bearerAuth = auth,
            body = AdminReviewActionRequest(applicationId, notes = notes),
        )
    }

    suspend fun adminInspectDocument(
        applicationId: String,
        documentId: String,
    ): Result<String?> = runAdminEdge { auth, edge ->
        edge.adminInspectDocument(
            bearerAuth = auth,
            body = AdminInspectDocumentRequest(applicationId, documentId),
        ).signedUrl
    }

    suspend fun adminSuspendRestaurant(
        restaurantId: String,
        reason: String,
    ): Result<AdminRestaurantStatusResponse> = runAdminEdge { auth, edge ->
        edge.adminSetRestaurantStatus(
            bearerAuth = auth,
            action = "suspend-restaurant",
            body = AdminRestaurantStatusRequest(restaurantId, reason),
        )
    }

    suspend fun adminReactivateRestaurant(
        restaurantId: String,
        reason: String,
    ): Result<AdminRestaurantStatusResponse> = runAdminEdge { auth, edge ->
        edge.adminSetRestaurantStatus(
            bearerAuth = auth,
            action = "reactivate-restaurant",
            body = AdminRestaurantStatusRequest(restaurantId, reason),
        )
    }

    suspend fun adminListReports(status: String? = null, limit: Int? = null): Result<List<RestaurantReportDto>> =
        runAdminEdge { auth, edge ->
            edge.adminListReports(bearerAuth = auth, body = AdminListReportsRequest(status, limit)).reports
        }

    suspend fun adminResolveReport(
        reportId: String,
        status: String,
        resolutionNotes: String? = null,
    ): Result<RestaurantReportDto?> = runAdminEdge { auth, edge ->
        edge.adminResolveReport(
            bearerAuth = auth,
            body = AdminResolveReportRequest(reportId, status, resolutionNotes),
        ).report
    }

    suspend fun adminListAuditLogs(
        restaurantId: String? = null,
        actionFilter: String? = null,
        limit: Int? = null,
    ): Result<List<RestaurantAuditLogDto>> = runAdminEdge { auth, edge ->
        edge.adminListAuditLogs(
            bearerAuth = auth,
            body = AdminListAuditLogsRequest(restaurantId, actionFilter, limit),
        ).logs
    }

    // ========================================================================
    // Gated execution + single 401 refresh-and-retry coordinator
    // ========================================================================

    private suspend fun <T> runDineInRead(
        block: suspend (bearerAuth: String, api: RestaurantsPostgrestApi) -> T,
    ): Result<T> = executeGated(
        isEnabled = { it.isDineInEnabled },
        featureLabel = "Restaurants dine-in",
        apiProvider = postgrestApiProvider,
        block = block,
    )

    private suspend fun <T> runDineInEdge(
        block: suspend (bearerAuth: String, api: RestaurantsEdgeApi) -> T,
    ): Result<T> = executeGated(
        isEnabled = { it.isDineInEnabled },
        featureLabel = "Restaurants dine-in",
        apiProvider = edgeApiProvider,
        block = block,
    )

    private suspend fun <T> runPartnerRead(
        block: suspend (bearerAuth: String, api: RestaurantsPostgrestApi) -> T,
    ): Result<T> = executeGated(
        isEnabled = { it.isRestaurantCenterEnabled },
        featureLabel = "Restaurant Center",
        apiProvider = postgrestApiProvider,
        block = block,
    )

    private suspend fun <T> runPartnerEdge(
        block: suspend (bearerAuth: String, api: RestaurantsEdgeApi) -> T,
    ): Result<T> = executeGated(
        isEnabled = { it.isRestaurantCenterEnabled },
        featureLabel = "Restaurant Center",
        apiProvider = edgeApiProvider,
        block = block,
    )

    private suspend fun <T> runAdminRead(
        block: suspend (bearerAuth: String, api: RestaurantsPostgrestApi) -> T,
    ): Result<T> = executeGated(
        isEnabled = { it.isAdminEnabled },
        featureLabel = "Restaurant Admin",
        apiProvider = postgrestApiProvider,
        block = block,
    )

    private suspend fun <T> runAdminEdge(
        block: suspend (bearerAuth: String, api: RestaurantsEdgeApi) -> T,
    ): Result<T> = executeGated(
        isEnabled = { it.isAdminEnabled },
        featureLabel = "Restaurant Admin",
        apiProvider = edgeApiProvider,
        block = block,
    )

    private suspend fun <Api, T> executeGated(
        isEnabled: (RestaurantsEnvironment) -> Boolean,
        featureLabel: String,
        apiProvider: () -> Api?,
        block: suspend (bearerAuth: String, api: Api) -> T,
    ): Result<T> {
        val env = environmentProvider()
        if (!isEnabled(env)) {
            return Result.failure(
                RestaurantsFailure.FeatureDisabled("$featureLabel is disabled in this build."),
            )
        }
        if (!env.isBackendConfigured()) {
            return Result.failure(
                RestaurantsFailure.NotConfigured("AdisChat Restaurants backend is not configured."),
            )
        }
        val api = apiProvider() ?: return Result.failure(
            RestaurantsFailure.NotConfigured("Restaurants API client is unavailable."),
        )

        return try {
            val bearer = sessionManager.getValidBearerToken(forceRefresh = false)
            Result.success(block(bearer, api))
        } catch (httpEx: HttpException) {
            if (httpEx.code() == 401) {
                try {
                    val refreshedBearer = sessionManager.getValidBearerToken(forceRefresh = true)
                    return Result.success(block(refreshedBearer, api))
                } catch (retryThrowable: Throwable) {
                    return Result.failure(RestaurantsErrorMapper.mapThrowable(retryThrowable))
                }
            }
            Result.failure(RestaurantsErrorMapper.mapHttpException(httpEx))
        } catch (t: Throwable) {
            Result.failure(RestaurantsErrorMapper.mapThrowable(t))
        }
    }

    private suspend fun <T> executeSignedUploadFlow(
        kind: RestaurantsUploadKind,
        source: RestaurantsBinaryUploadSource,
        mintUploadUrl: suspend (ValidatedUploadMetadata) -> Pair<Boolean, Pair<String?, String?>>,
        afterUpload: suspend (storagePath: String, metadata: ValidatedUploadMetadata) -> T,
    ): Result<T> {
        val env = environmentProvider()
        if (!env.isRestaurantCenterEnabled) {
            return Result.failure(
                RestaurantsFailure.FeatureDisabled("Restaurant Center is disabled in this build."),
            )
        }
        if (!env.isBackendConfigured()) {
            return Result.failure(
                RestaurantsFailure.NotConfigured("AdisChat Restaurants backend is not configured."),
            )
        }

        val metadata = try {
            RestaurantsUploadValidator.validateMetadata(kind, source)
        } catch (t: Throwable) {
            return Result.failure(RestaurantsErrorMapper.mapThrowable(t))
        }

        val (issuedOk, issuedPair) = try {
            mintUploadUrl(metadata)
        } catch (t: Throwable) {
            return Result.failure(RestaurantsErrorMapper.mapThrowable(t))
        }

        val rawUploadUrl = issuedPair.first?.trim().orEmpty()
        if (!issuedOk || rawUploadUrl.isEmpty()) {
            return Result.failure(
                RestaurantsFailure.Server(
                    "invalid_upload_url_response",
                    "Server did not return a valid upload link.",
                ),
            )
        }

        val uploadUrl = try {
            RestaurantsUploadValidator.validateSignedUploadUrl(
                rawUrl = rawUploadUrl,
                allowLoopbackHttp = true,
            ).toString()
        } catch (t: Throwable) {
            return Result.failure(RestaurantsErrorMapper.mapThrowable(t))
        }

        val storagePath = try {
            RestaurantsUploadValidator.validateStoragePath(issuedPair.second)
        } catch (t: Throwable) {
            return Result.failure(RestaurantsErrorMapper.mapThrowable(t))
        }

        val (expectedUid, expectedGeneration) = sessionManager.currentUidAndGeneration()
        if (expectedUid.isNullOrBlank()) {
            return Result.failure(
                RestaurantsFailure.Unauthenticated("not_signed_in", "Please sign in to AdisChat first."),
            )
        }

        return try {
            signedUploaderProvider().uploadToSignedUrl(
                uploadUrl = uploadUrl,
                kind = kind,
                source = source,
            )
            ensureSameUploadAccount(expectedUid, expectedGeneration)
            val result = afterUpload(storagePath, metadata)
            ensureSameUploadAccount(expectedUid, expectedGeneration)
            Result.success(result)
        } catch (t: Throwable) {
            Result.failure(RestaurantsErrorMapper.mapThrowable(t))
        }
    }

    private fun ensureSameUploadAccount(expectedUid: String, expectedGeneration: Long) {
        val (liveUid, liveGeneration) = sessionManager.currentUidAndGeneration()
        if (liveUid.isNullOrBlank() || liveUid != expectedUid || liveGeneration != expectedGeneration) {
            throw RestaurantsFailure.Unauthenticated(
                "account_switched",
                "Active account changed during upload. Upload cancelled before saving.",
            )
        }
    }

    private suspend fun <T> executeSingleConfirmation(
        dedupKey: String,
        block: suspend () -> Result<T>,
    ): Result<T> {
        val cleanKey = dedupKey.trim()
        if (cleanKey.isEmpty()) {
            return Result.failure(
                RestaurantsFailure.Validation("missing_storage_path", "Uploaded storage path is required."),
            )
        }
        synchronized(confirmedStoragePaths) {
            if (confirmedStoragePaths.contains(cleanKey)) {
                return Result.failure(
                    RestaurantsFailure.Conflict(
                        "duplicate_upload_confirmation",
                        "This uploaded file has already been confirmed.",
                    ),
                )
            }
        }
        if (!confirmingStoragePaths.add(cleanKey)) {
            return Result.failure(
                RestaurantsFailure.Conflict(
                    "confirm_already_in_progress",
                    "Upload confirmation is already in progress.",
                ),
            )
        }
        return try {
            val result = block()
            if (result.isSuccess) {
                synchronized(confirmedStoragePaths) {
                    if (confirmedStoragePaths.size >= MAX_TRACKED_CONFIRMED_PATHS) {
                        val oldest = confirmedStoragePaths.iterator()
                        if (oldest.hasNext()) {
                            oldest.next()
                            oldest.remove()
                        }
                    }
                    confirmedStoragePaths.add(cleanKey)
                }
            }
            result
        } finally {
            confirmingStoragePaths.remove(cleanKey)
        }
    }

    private companion object {
        const val MAX_TRACKED_CONFIRMED_PATHS = 256
    }
}
