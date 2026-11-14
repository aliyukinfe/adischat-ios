package com.adischat.app.feature.restaurants

import com.adischat.app.feature.restaurants.admin.RemoteRestaurantAdminDataSource
import com.adischat.app.feature.restaurants.admin.RestaurantAdminCoordinator
import com.adischat.app.feature.restaurants.center.OrderBoardFilter
import com.adischat.app.feature.restaurants.center.RemoteRestaurantCenterDataSource
import com.adischat.app.feature.restaurants.center.RestaurantApplicationStatus
import com.adischat.app.feature.restaurants.center.RestaurantCenterCoordinator
import com.adischat.app.feature.restaurants.center.RestaurantOperationalStatus
import com.adischat.app.feature.restaurants.center.RestaurantStaffRole
import com.adischat.app.feature.restaurants.data.ActivateRestaurantResponse
import com.adischat.app.feature.restaurants.data.AdminInspectDocumentRequest
import com.adischat.app.feature.restaurants.data.AdminInspectDocumentResponse
import com.adischat.app.feature.restaurants.data.AdminListApplicationsRequest
import com.adischat.app.feature.restaurants.data.AdminListApplicationsResponse
import com.adischat.app.feature.restaurants.data.AdminListAuditLogsRequest
import com.adischat.app.feature.restaurants.data.AdminListAuditLogsResponse
import com.adischat.app.feature.restaurants.data.AdminPaginationDto
import com.adischat.app.feature.restaurants.data.AdminPendingApplicationDto
import com.adischat.app.feature.restaurants.data.AmIAdminResponse
import com.adischat.app.feature.restaurants.data.ApplicationDocumentDto
import com.adischat.app.feature.restaurants.data.AuthorizedWorkspaceResponse
import com.adischat.app.feature.restaurants.data.BranchMenuItemOverrideDto
import com.adischat.app.feature.restaurants.data.ConfirmDocumentRequest
import com.adischat.app.feature.restaurants.data.DiningTableWithStatusDto
import com.adischat.app.feature.restaurants.data.EpochClock
import com.adischat.app.feature.restaurants.data.FirebaseIdentityProvider
import com.adischat.app.feature.restaurants.data.KitchenTicketDto
import com.adischat.app.feature.restaurants.data.ListTablesRequest
import com.adischat.app.feature.restaurants.data.ListTablesResponse
import com.adischat.app.feature.restaurants.data.MenuCategoryDto
import com.adischat.app.feature.restaurants.data.MenuItemDto
import com.adischat.app.feature.restaurants.data.MenuItemImageDto
import com.adischat.app.feature.restaurants.data.MenuOptionDto
import com.adischat.app.feature.restaurants.data.MenuOptionGroupDto
import com.adischat.app.feature.restaurants.data.OrderItemRecordDto
import com.adischat.app.feature.restaurants.data.OrderPaymentRecordDto
import com.adischat.app.feature.restaurants.data.OrderRecordDto
import com.adischat.app.feature.restaurants.data.OrderStatusEventDto
import com.adischat.app.feature.restaurants.data.RestaurantApplicationDto
import com.adischat.app.feature.restaurants.data.RestaurantAuditLogDto
import com.adischat.app.feature.restaurants.data.RestaurantBranchDto
import com.adischat.app.feature.restaurants.data.RestaurantNotificationDto
import com.adischat.app.feature.restaurants.data.RestaurantProfileDto
import com.adischat.app.feature.restaurants.data.RestaurantScopedRequest
import com.adischat.app.feature.restaurants.data.RestaurantSummaryDto
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RestaurantsErrorMapper
import com.adischat.app.feature.restaurants.data.RestaurantsFailure
import com.adischat.app.feature.restaurants.data.RestaurantsPostgrestApi
import com.adischat.app.feature.restaurants.data.RestaurantsRepository
import com.adischat.app.feature.restaurants.data.RestaurantsSession
import com.adischat.app.feature.restaurants.data.RestaurantsSessionManager
import com.adischat.app.feature.restaurants.data.RestaurantsSessionMemory
import com.adischat.app.feature.restaurants.data.StaffInvitationDto
import com.adischat.app.feature.restaurants.data.StaffListInvitationsRequest
import com.adischat.app.feature.restaurants.data.StaffListInvitationsResponse
import com.adischat.app.feature.restaurants.data.StaffListMembersRequest
import com.adischat.app.feature.restaurants.data.StaffListMembersResponse
import com.adischat.app.feature.restaurants.data.StaffMemberSummaryDto
import com.adischat.app.feature.restaurants.data.StaffMembershipDto
import com.adischat.app.feature.restaurants.data.StubRestaurantsEdgeApi
import com.adischat.app.feature.restaurants.data.SyncBranchOrderDto
import com.adischat.app.feature.restaurants.data.SyncBranchOrdersRequest
import com.adischat.app.feature.restaurants.data.SyncBranchOrdersResponse
import com.adischat.app.feature.restaurants.data.RestaurantApplicationListResponse
import com.adischat.app.feature.restaurants.presentation.RestaurantOrderStatus
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadState
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

class RemoteRestaurantsAdaptersTest {
    private val gson = Gson()
    private var activeUid: String? = "uid-owner-1"

    @Before
    fun setUp() {
        RestaurantsSessionMemory.finishSignOut()
        RestaurantsSessionMemory.clear()
        seedSession("uid-owner-1", "prof-owner-1")
    }

    @After
    fun tearDown() {
        RestaurantsSessionMemory.finishSignOut()
        RestaurantsSessionMemory.clear()
    }

    @Test
    fun `disabled flags block partner center and admin calls without touching network`() = runBlocking {
        val networkCalls = AtomicInteger(0)
        val disabledEnv = RestaurantsEnvironment(
            supabaseUrl = "https://restaurants-test.supabase.co",
            supabasePublishableKey = "pk_test",
            identityBridgeUrl = "https://bridge-test.example",
            isIdentityBridgeEnabled = false,
            isDineInEnabled = false,
            isRestaurantCenterEnabled = false,
            isAdminEnabled = false,
        )
        val repo = RestaurantsRepository(
            environmentProvider = { disabledEnv },
            sessionManager = fakeSessionManager(disabledEnv),
            edgeApiProvider = {
                object : StubRestaurantsEdgeApi() {
                    override suspend fun getMyWorkspace(
                        bearerAuth: String,
                        action: String,
                        body: Map<String, String>,
                    ): AuthorizedWorkspaceResponse {
                        networkCalls.incrementAndGet()
                        return AuthorizedWorkspaceResponse(ok = true)
                    }

                    override suspend fun amIAdmin(
                        bearerAuth: String,
                        action: String,
                        body: Map<String, String>,
                    ): AmIAdminResponse {
                        networkCalls.incrementAndGet()
                        return AmIAdminResponse(ok = true, isPlatformAdmin = true)
                    }
                }
            },
            postgrestApiProvider = { StubRestaurantsPostgrestApi() },
        )

        val centerDs = RemoteRestaurantCenterDataSource(repo)
        val adminDs = RemoteRestaurantAdminDataSource(repo)

        assertTrue(centerDs.loadAccessibleRestaurants().exceptionOrNull() is RestaurantsFailure.FeatureDisabled)
        assertTrue(centerDs.activateRestaurant("rest-1").exceptionOrNull() is RestaurantsFailure.FeatureDisabled)
        assertTrue(centerDs.syncBranchOrders("rest-1", "br-1").exceptionOrNull() is RestaurantsFailure.FeatureDisabled)
        assertTrue(adminDs.verifyPlatformAdminAccess("uid-owner-1").exceptionOrNull() is RestaurantsFailure.FeatureDisabled)
        assertTrue(adminDs.loadApplications().exceptionOrNull() is RestaurantsFailure.FeatureDisabled)
        assertTrue(adminDs.loadRestaurantsAndBranches().exceptionOrNull() is RestaurantsFailure.FeatureDisabled)

        val centerCoordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = centerDs,
            environmentProvider = { disabledEnv },
            accountUidProvider = { activeUid },
        )
        val adminCoordinator = RestaurantAdminCoordinator(
            scope = this,
            dataSource = adminDs,
            environmentProvider = { disabledEnv },
            accountUidProvider = { activeUid },
        )

        centerCoordinator.loadCenter()
        adminCoordinator.loadAdminWorkspace()
        delay(30)

        assertEquals(0, networkCalls.get())
        assertEquals(RestaurantsLoadState.Disabled, centerCoordinator.state.value.loadState)
        assertEquals(RestaurantsLoadState.Disabled, adminCoordinator.state.value.loadState)
    }

    @Test
    fun `R4A JSON contracts deserialize accurately including omitted storage_path and keyset cursors`() {
        val amIAdmin = gson.fromJson(
            """{"ok":true,"is_platform_admin":true,"profile_id":"adm-1"}""",
            AmIAdminResponse::class.java,
        )
        assertTrue(amIAdmin.ok && amIAdmin.isPlatformAdmin)
        assertEquals("adm-1", amIAdmin.profileId)

        val listAppsJson = """
            {
              "ok": true,
              "applications": [
                {
                  "id": "app-101",
                  "applicant_profile_id": "prof-applicant",
                  "status": "UNDER_REVIEW",
                  "legal_name": "Sheba Kitchen PLC",
                  "brand_name": "Sheba Kitchen",
                  "description": "Traditional cuisine",
                  "business_type": "restaurant",
                  "contact_phone": "+251911000111",
                  "contact_email": "owner@sheba.invalid",
                  "tin_number": "0011223344",
                  "branch_count": 2,
                  "draft_menu_item_count": 7,
                  "documents": [
                    {
                      "id": "doc-1",
                      "doc_type": "business_license",
                      "file_name": "license.pdf",
                      "mime_type": "application/pdf",
                      "byte_size": 45000
                    }
                  ]
                }
              ],
              "pagination": {"limit": 50, "offset": 0, "count": 1}
            }
        """.trimIndent()
        val appsResp = gson.fromJson(listAppsJson, AdminListApplicationsResponse::class.java)
        assertEquals(1, appsResp.applications.size)
        assertTrue(appsResp.applications.first().documents.first().storagePath.isNullOrBlank())
        assertEquals(2, appsResp.applications.first().branchCount)
        assertEquals(1, appsResp.pagination?.count)

        val listMineJson = """
            {
              "ok": true,
              "applications": [
                {
                  "id": "app-draft-1",
                  "applicant_profile_id": "prof-owner-1",
                  "status": "DRAFT",
                  "legal_name": "My Bistro PLC",
                  "brand_name": "My Bistro",
                  "draft_branches_json": [{"code":"MAIN","name":"Bole"}],
                  "restaurant_application_documents": [
                    {"id":"doc-9","doc_type":"tin_certificate","file_name":"tin.pdf","mime_type":"application/pdf","byte_size":1024}
                  ]
                }
              ]
            }
        """.trimIndent()
        val myAppsResp = gson.fromJson(listMineJson, RestaurantApplicationListResponse::class.java)
        assertEquals(1, myAppsResp.applications.first().draftBranchesJson?.asJsonArray?.size())
        assertEquals("doc-9", myAppsResp.applications.first().documents.first().id)

        val inspectJson = """
            {
              "ok": true,
              "signed_url": "https://signed-private.invalid/doc-1?token=abc",
              "expires_in_seconds": 120,
              "document": {
                "id": "doc-1",
                "doc_type": "business_license",
                "file_name": "license.pdf",
                "mime_type": "application/pdf",
                "byte_size": 45000
              }
            }
        """.trimIndent()
        val inspectResp = gson.fromJson(inspectJson, AdminInspectDocumentResponse::class.java)
        assertEquals(120L, inspectResp.expiresInSeconds)
        assertEquals("license.pdf", inspectResp.document?.fileName)

        val syncJson = """
            {
              "ok": true,
              "branch_id": "br-1",
              "sync_mode": "incremental",
              "orders": [
                {
                  "id": "ord-2",
                  "restaurant_id": "rest-1",
                  "branch_id": "br-1",
                  "order_number": "ORD-002",
                  "status": "PREPARING",
                  "payment_status": "UNPAID",
                  "total_minor": 42000,
                  "updated_at": "2026-11-10T12:00:00Z",
                  "order_items": [{"id":"oi-1","order_id":"ord-2","menu_item_id":"mi-1","item_name_snapshot":"Kitfo","quantity":2,"line_total_minor":42000}]
                }
              ],
              "next_cursor": "2026-11-10T12:00:00Z",
              "next_cursor_id": "ord-2",
              "has_more": true,
              "server_time": "2026-11-10T12:00:01Z"
            }
        """.trimIndent()
        val syncResp = gson.fromJson(syncJson, SyncBranchOrdersResponse::class.java)
        assertEquals("ord-2", syncResp.nextCursorId)
        assertTrue(syncResp.hasMore)
        assertEquals("Kitfo", syncResp.orders.first().orderItems.first().itemNameSnapshot)

        val activateResp = gson.fromJson(
            """{"ok":true,"restaurant_id":"rest-1","status":"active","is_live_ordering_enabled":false,"already_active":false}""",
            ActivateRestaurantResponse::class.java,
        )
        assertEquals("active", activateResp.status)
        assertFalse(activateResp.isLiveOrderingEnabled)
    }

    @Test
    fun `error mapper translates R4A admin, center, and sync error codes cleanly`() {
        fun map(status: Int, code: String) = RestaurantsErrorMapper.mapHttpStatusAndBody(
            status,
            """{"ok":false,"error":{"code":"$code","message":"msg"}}""",
        )
        assertTrue(map(403, "forbidden_not_platform_admin") is RestaurantsFailure.Forbidden)
        assertTrue(map(403, "self_approval_forbidden") is RestaurantsFailure.Forbidden)
        assertTrue(map(403, "forbidden_suspended_restaurant") is RestaurantsFailure.Forbidden)
        assertTrue(map(409, "incomplete_restaurant_profile") is RestaurantsFailure.Conflict)
        assertTrue(map(409, "active_branch_required") is RestaurantsFailure.Conflict)
        assertTrue(map(409, "active_menu_item_required") is RestaurantsFailure.Conflict)
        assertTrue(map(400, "invalid_sync_cursor") is RestaurantsFailure.Validation)
        assertTrue(map(404, "document_not_found") is RestaurantsFailure.NotFound)
    }

    @Test
    fun `admin eligibility, pagination, self-approval block, 120s URL expiry, and account wipe work end-to-end`() = runBlocking {
        activeUid = "adm-user-1"
        seedSession("adm-user-1", "prof-adm-1")
        val adminEnv = enabledAdminEnv()
        var isPlatformAdminFlag = false
        val listCalls = AtomicInteger(0)

        val page1Apps = (1..50).map { i ->
            AdminPendingApplicationDto(
                id = "app-$i",
                applicantProfileId = if (i == 1) "adm-user-1" else "owner-$i",
                status = "SUBMITTED",
                legalName = "Legal $i",
                brandName = "Brand $i",
                documents = if (i == 2) {
                    listOf(
                        ApplicationDocumentDto(
                            id = "doc-$i",
                            docType = "business_license",
                            fileName = "lic.pdf",
                            mimeType = "application/pdf",
                            byteSize = 900L,
                        ),
                    )
                } else {
                    emptyList()
                },
            )
        }
        val page2App = AdminPendingApplicationDto(
            id = "app-51",
            applicantProfileId = "owner-51",
            status = "UNDER_REVIEW",
            legalName = "Legal 51",
            brandName = "Brand 51",
        )

        val edgeApi = object : StubRestaurantsEdgeApi() {
            override suspend fun amIAdmin(
                bearerAuth: String,
                action: String,
                body: Map<String, String>,
            ): AmIAdminResponse = AmIAdminResponse(ok = true, isPlatformAdmin = isPlatformAdminFlag, profileId = "prof-adm-1")

            override suspend fun adminListPendingApplications(
                bearerAuth: String,
                action: String,
                body: AdminListApplicationsRequest,
            ): AdminListApplicationsResponse {
                if (!isPlatformAdminFlag) {
                    throw httpJsonError(403, "forbidden_not_platform_admin", "Platform admin role required.")
                }
                listCalls.incrementAndGet()
                return if ((body.offset ?: 0) == 0) {
                    AdminListApplicationsResponse(true, page1Apps, AdminPaginationDto(50, 0, 50))
                } else {
                    AdminListApplicationsResponse(true, listOf(page2App), AdminPaginationDto(50, 50, 1))
                }
            }

            override suspend fun adminInspectDocument(
                bearerAuth: String,
                action: String,
                body: AdminInspectDocumentRequest,
            ): AdminInspectDocumentResponse = AdminInspectDocumentResponse(
                ok = true,
                signedUrl = "https://private-storage.invalid/${body.documentId}?exp=120",
                expiresInSeconds = 120L,
                document = ApplicationDocumentDto(
                    id = body.documentId,
                    docType = "business_license",
                    fileName = "lic.pdf",
                    mimeType = "application/pdf",
                    byteSize = 900L,
                ),
            )

            override suspend fun adminListAuditLogs(
                bearerAuth: String,
                action: String,
                body: AdminListAuditLogsRequest,
            ): AdminListAuditLogsResponse = AdminListAuditLogsResponse(ok = true, logs = emptyList())
        }

        val postgrest = object : StubRestaurantsPostgrestApi() {
            override suspend fun listRestaurants(
                bearerAuth: String,
                select: String,
                statusFilter: String?,
                liveFilter: String?,
                order: String,
            ): List<RestaurantSummaryDto> = listOf(
                RestaurantSummaryDto(id = "r-1", name = "Existing Rest", slug = "existing", status = "active"),
            )

            override suspend fun listBranches(
                bearerAuth: String,
                select: String,
                restaurantIdFilter: String?,
                statusFilter: String?,
                order: String,
            ): List<RestaurantBranchDto> = listOf(
                RestaurantBranchDto(id = "b-1", restaurantId = "r-1", name = "Main", isDineInActive = true),
            )
        }

        val repo = RestaurantsRepository(
            environmentProvider = { adminEnv },
            sessionManager = fakeSessionManager(adminEnv),
            edgeApiProvider = { edgeApi },
            postgrestApiProvider = { postgrest },
        )
        val adminDs = RemoteRestaurantAdminDataSource(repo)
        val coordinator = RestaurantAdminCoordinator(
            scope = this,
            dataSource = adminDs,
            environmentProvider = { adminEnv },
            accountUidProvider = { activeUid },
        )

        // 1. Non-admin fails eligibility cleanly before loading applications
        coordinator.loadAdminWorkspace()
        delay(50)
        assertFalse(coordinator.state.value.isAuthorizedAdmin)
        assertEquals(0, listCalls.get())
        assertTrue(coordinator.state.value.loadState is RestaurantsLoadState.Error)

        // 2. Direct admin repository read with non-admin maps HTTP 403 -> Forbidden
        val directNonAdmin = adminDs.loadApplications()
        assertTrue(directNonAdmin.exceptionOrNull() is RestaurantsFailure.Forbidden)

        // 3. Elevated platform admin loads paginated 51 applications
        isPlatformAdminFlag = true
        coordinator.loadAdminWorkspace()
        delay(60)
        assertTrue(coordinator.state.value.isAuthorizedAdmin)
        assertEquals(2, listCalls.get())
        assertEquals(51, coordinator.state.value.applications.size)
        assertEquals("r-1", coordinator.state.value.restaurants.first().id)

        // 4. Self-approval blocked locally
        coordinator.approveApplication("app-1", slug = "self-brand", notes = "Self check")
        assertTrue(coordinator.state.value.errorBanner?.contains("Self-approval", ignoreCase = true) == true)

        // 5. Private document signed URL sets 120s transient expiry in memory
        coordinator.inspectDocument("app-2", "doc-2")
        delay(50)
        val docAfterInspect = coordinator.state.value.applications.first { it.id == "app-2" }.documents.first()
        assertNotNull(docAfterInspect.lastInspectedUrlPreview)
        val expiresAt = docAfterInspect.signedUrlExpiresAtEpochSeconds
        assertNotNull(expiresAt)
        assertTrue(docAfterInspect.hasValidSignedUrl(expiresAt!! - 10))
        assertFalse(docAfterInspect.hasValidSignedUrl(expiresAt + 1))

        // 6. Account change wipes applications, restaurants, and temporary signed URLs
        coordinator.onAccountChanged("adm-user-2")
        assertFalse(coordinator.state.value.isAuthorizedAdmin)
        assertTrue(coordinator.state.value.applications.isEmpty())
        assertTrue(coordinator.state.value.restaurants.isEmpty())
    }

    @Test
    fun `center workspace isolation, revocation, activation pending_setup to active, and syncOrders keyset merge work cleanly`() = runBlocking {
        activeUid = "uid-owner-1"
        seedSession("uid-owner-1", "prof-owner-1")
        val centerEnv = enabledCenterEnv()

        var workspaceResponse = AuthorizedWorkspaceResponse(
            ok = true,
            memberships = listOf(
                StaffMembershipDto(
                    id = "m-1",
                    restaurantId = "rest-1",
                    branchId = null,
                    profileId = "prof-owner-1",
                    role = "RESTAURANT_OWNER",
                    status = "active",
                ),
                StaffMembershipDto(
                    id = "m-revoked",
                    restaurantId = "rest-revoked",
                    branchId = null,
                    profileId = "prof-owner-1",
                    role = "RESTAURANT_MANAGER",
                    status = "revoked",
                ),
            ),
            restaurants = listOf(
                RestaurantSummaryDto(
                    id = "rest-1",
                    slug = "habesha-hearth",
                    name = "Habesha Hearth",
                    status = "pending_setup",
                    isLiveOrderingEnabled = false,
                ),
                RestaurantSummaryDto(
                    id = "rest-revoked",
                    slug = "old-spot",
                    name = "Old Spot",
                    status = "active",
                    isLiveOrderingEnabled = true,
                ),
            ),
            branches = listOf(
                RestaurantBranchDto(id = "br-1", restaurantId = "rest-1", code = "MAIN", name = "Bole Branch"),
                RestaurantBranchDto(id = "br-2", restaurantId = "rest-1", code = "PIASSA", name = "Piassa Branch"),
                RestaurantBranchDto(id = "br-other", restaurantId = "rest-revoked", code = "OLD", name = "Revoked Branch"),
            ),
        )

        val recordedSyncRequests = mutableListOf<SyncBranchOrdersRequest>()
        var syncStep = 0
        var rejectActivationWithSuspended = false

        val edgeApi = object : StubRestaurantsEdgeApi() {
            override suspend fun listMyApplications(
                bearerAuth: String,
                action: String,
                body: Map<String, String>,
            ): RestaurantApplicationListResponse = RestaurantApplicationListResponse(
                ok = true,
                applications = listOf(
                    RestaurantApplicationDto(
                        id = "app-center-1",
                        applicantProfileId = "prof-owner-1",
                        status = "APPROVED",
                        legalName = "Hearth PLC",
                        brandName = "Habesha Hearth",
                        approvedRestaurantId = "rest-1",
                    ),
                ),
            )

            override suspend fun getMyWorkspace(
                bearerAuth: String,
                action: String,
                body: Map<String, String>,
            ): AuthorizedWorkspaceResponse = workspaceResponse

            override suspend fun activateRestaurant(
                bearerAuth: String,
                action: String,
                body: RestaurantScopedRequest,
            ): ActivateRestaurantResponse {
                if (rejectActivationWithSuspended) {
                    throw httpJsonError(403, "forbidden_suspended_restaurant", "Restaurant is suspended.")
                }
                return ActivateRestaurantResponse(
                    ok = true,
                    restaurantId = body.restaurantId,
                    status = "active",
                    isLiveOrderingEnabled = false,
                    alreadyActive = false,
                )
            }

            override suspend fun listTables(
                bearerAuth: String,
                action: String,
                body: ListTablesRequest,
            ): ListTablesResponse = ListTablesResponse(ok = true, tables = emptyList())

            override suspend fun listStaffMembers(
                bearerAuth: String,
                action: String,
                body: StaffListMembersRequest,
            ): StaffListMembersResponse = StaffListMembersResponse(ok = true, members = emptyList())

            override suspend fun listStaffInvitations(
                bearerAuth: String,
                action: String,
                body: StaffListInvitationsRequest,
            ): StaffListInvitationsResponse = StaffListInvitationsResponse(ok = true, invitations = emptyList())

            override suspend fun syncBranchOrders(
                bearerAuth: String,
                action: String,
                body: SyncBranchOrdersRequest,
            ): SyncBranchOrdersResponse {
                recordedSyncRequests.add(body)
                return when {
                    body.since == null -> {
                        SyncBranchOrdersResponse(
                            ok = true,
                            branchId = body.branchId,
                            syncMode = "snapshot",
                            orders = listOf(
                                SyncBranchOrderDto(
                                    id = "ord-1",
                                    restaurantId = body.restaurantId,
                                    branchId = body.branchId,
                                    orderNumber = "1001",
                                    status = "SUBMITTED",
                                    paymentStatus = "UNPAID",
                                    totalMinor = 15000L,
                                    updatedAt = "2026-11-10T10:00:00Z",
                                ),
                            ),
                            nextCursor = "2026-11-10T10:00:00Z",
                            nextCursorId = "ord-1",
                            hasMore = true,
                        )
                    }
                    syncStep == 1 -> {
                        SyncBranchOrdersResponse(
                            ok = true,
                            branchId = body.branchId,
                            syncMode = "incremental",
                            orders = listOf(
                                // Tied timestamp update + new order with identical updated_at
                                SyncBranchOrderDto(
                                    id = "ord-1",
                                    restaurantId = body.restaurantId,
                                    branchId = body.branchId,
                                    orderNumber = "1001",
                                    status = "PREPARING",
                                    paymentStatus = "UNPAID",
                                    totalMinor = 15000L,
                                    updatedAt = "2026-11-10T10:05:00Z",
                                ),
                                SyncBranchOrderDto(
                                    id = "ord-2",
                                    restaurantId = body.restaurantId,
                                    branchId = body.branchId,
                                    orderNumber = "1002",
                                    status = "SUBMITTED",
                                    paymentStatus = "UNPAID",
                                    totalMinor = 22000L,
                                    updatedAt = "2026-11-10T10:05:00Z",
                                ),
                            ),
                            nextCursor = "2026-11-10T10:05:00Z",
                            nextCursorId = "ord-2",
                            hasMore = false,
                        )
                    }
                    else -> SyncBranchOrdersResponse(ok = true, branchId = body.branchId, orders = emptyList())
                }
            }
        }

        val repo = RestaurantsRepository(
            environmentProvider = { centerEnv },
            sessionManager = fakeSessionManager(centerEnv),
            edgeApiProvider = { edgeApi },
            postgrestApiProvider = { StubRestaurantsPostgrestApi() },
        )
        val centerDs = RemoteRestaurantCenterDataSource(repo)

        // Blank storagePath fails explicitly before touching Edge Function
        val blankDocRes = centerDs.attachApplicationDocument(
            ConfirmDocumentRequest("app-1", "business_license", "   ", "lic.pdf", "application/pdf", 500L),
        )
        assertTrue(blankDocRes.exceptionOrNull() is RestaurantsFailure.Validation)

        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = centerDs,
            environmentProvider = { centerEnv },
            accountUidProvider = { activeUid },
        )

        // 1. Load center: strictly filters out revoked restaurant, loads branches + initial order snapshot
        coordinator.loadCenter()
        for (i in 0..40) {
            if (coordinator.state.value.orderSyncCursor != null) break
            delay(25)
        }
        assertEquals(listOf("rest-1"), coordinator.state.value.ownedRestaurants.map { it.id })
        assertEquals(listOf("br-1", "br-2"), coordinator.state.value.branches.map { it.id })
        assertEquals(RestaurantOperationalStatus.PENDING_SETUP, coordinator.state.value.selectedRestaurant?.status)
        assertEquals("2026-11-10T10:00:00Z", coordinator.state.value.orderSyncCursor)
        assertEquals("ord-1", coordinator.state.value.orderSyncCursorId)
        assertTrue(coordinator.state.value.orderHasMore)

        // Cross-restaurant branch read blocked strictly
        val crossRestBranches = centerDs.loadBranches("rest-revoked")
        assertTrue(crossRestBranches.exceptionOrNull() is RestaurantsFailure.Forbidden)

        // 2. Owner activation moves PENDING_SETUP -> ACTIVE while keeping live ordering OFF
        coordinator.activateRestaurant()
        delay(40)
        assertEquals(RestaurantOperationalStatus.ACTIVE, coordinator.state.value.selectedRestaurant?.status)
        assertFalse(coordinator.state.value.selectedRestaurant?.isLiveOrderingEnabled == true)

        // Suspended activation error maps cleanly to errorBanner
        rejectActivationWithSuspended = true
        coordinator.activateRestaurant()
        delay(40)
        assertTrue(coordinator.state.value.errorBanner?.contains("suspended", ignoreCase = true) == true)

        // 3. Incremental kitchen sync sends both since + afterId and merges/deduplicates orders in place
        syncStep = 1
        coordinator.syncOrders(incremental = true)
        delay(40)
        val incrementalReq = recordedSyncRequests.last()
        assertEquals("2026-11-10T10:00:00Z", incrementalReq.since)
        assertEquals("ord-1", incrementalReq.afterId)
        assertEquals(2, coordinator.state.value.orders.size)
        assertEquals(
            RestaurantOrderStatus.PREPARING,
            coordinator.state.value.orders.first { it.orderId == "ord-1" }.status,
        )
        assertEquals("ord-2", coordinator.state.value.orderSyncCursorId)
        assertFalse(coordinator.state.value.orderHasMore)

        // 4. Branch switch resets order cursors before syncing the newly selected branch
        syncStep = 0
        coordinator.selectBranch("br-2")
        delay(50)
        val postSwitchInitialReq = recordedSyncRequests.last()
        assertEquals("br-2", postSwitchInitialReq.branchId)
        assertNull(postSwitchInitialReq.since)
        assertNull(postSwitchInitialReq.afterId)

        // 5. Revoking membership remotely clears tenant state cleanly on reload
        workspaceResponse = AuthorizedWorkspaceResponse(ok = true, memberships = emptyList(), restaurants = emptyList(), branches = emptyList())
        coordinator.loadCenter()
        delay(50)
        assertTrue(coordinator.state.value.ownedRestaurants.isEmpty())
        assertNull(coordinator.state.value.selectedRestaurantId)
        assertTrue(coordinator.state.value.branches.isEmpty())
        assertTrue(coordinator.state.value.orders.isEmpty())
        assertNull(coordinator.state.value.orderSyncCursor)

        // 6. Account change wipes cached workspace and coordinator state
        centerDs.clearCachedWorkspace()
        coordinator.onAccountChanged("uid-owner-2")
        assertTrue(coordinator.state.value.applications.isEmpty())
        assertNull(coordinator.state.value.selectedBranchId)
    }

    private fun seedSession(uid: String, profileId: String) {
        RestaurantsSessionMemory.setSessionIfCurrent(
            RestaurantsSession(
                ownerAdisChatUid = uid,
                profileId = profileId,
                accessToken = "access-$uid",
                refreshToken = "refresh-$uid",
                expiresAtEpochSeconds = 9_999_999_999L,
            ),
            RestaurantsSessionMemory.generation(),
        )
    }

    private fun fakeSessionManager(env: RestaurantsEnvironment): RestaurantsSessionManager =
        RestaurantsSessionManager(
            environmentProvider = { env },
            firebaseIdentityProvider = object : FirebaseIdentityProvider {
                override fun currentUid(): String? = activeUid
                override suspend fun getIdToken(forceRefresh: Boolean): String = "fb-token"
            },
            clock = EpochClock { 1_000L },
        )

    private fun enabledCenterEnv() = RestaurantsEnvironment(
        supabaseUrl = "https://restaurants-test.supabase.co",
        supabasePublishableKey = "pk_test",
        identityBridgeUrl = "https://bridge-test.example",
        isIdentityBridgeEnabled = true,
        isDineInEnabled = false,
        isRestaurantCenterEnabled = true,
        isAdminEnabled = false,
        isQaBuild = false,
    )

    private fun enabledAdminEnv() = RestaurantsEnvironment(
        supabaseUrl = "https://restaurants-test.supabase.co",
        supabasePublishableKey = "pk_test",
        identityBridgeUrl = "https://bridge-test.example",
        isIdentityBridgeEnabled = true,
        isDineInEnabled = false,
        isRestaurantCenterEnabled = false,
        isAdminEnabled = true,
        isQaBuild = false,
    )

    private fun httpJsonError(status: Int, code: String, message: String): HttpException =
        HttpException(
            Response.error<String>(
                status,
                """{"ok":false,"error":{"code":"$code","message":"$message"}}"""
                    .toResponseBody("application/json".toMediaType()),
            ),
        )
}

private open class StubRestaurantsPostgrestApi : RestaurantsPostgrestApi {
    override suspend fun listRestaurants(bearerAuth: String, select: String, statusFilter: String?, liveFilter: String?, order: String): List<RestaurantSummaryDto> = emptyList()
    override suspend fun getRestaurantById(bearerAuth: String, select: String, idFilter: String): List<RestaurantSummaryDto> = emptyList()
    override suspend fun listBranches(bearerAuth: String, select: String, restaurantIdFilter: String?, statusFilter: String?, order: String): List<RestaurantBranchDto> = emptyList()
    override suspend fun listMenuCategories(bearerAuth: String, select: String, restaurantIdFilter: String, isActiveFilter: String?, order: String): List<MenuCategoryDto> = emptyList()
    override suspend fun listMenuItems(bearerAuth: String, select: String, restaurantIdFilter: String, isActiveFilter: String?, order: String): List<MenuItemDto> = emptyList()
    override suspend fun listMenuItemImages(bearerAuth: String, select: String, restaurantIdFilter: String, order: String): List<MenuItemImageDto> = emptyList()
    override suspend fun listMenuOptionGroups(bearerAuth: String, select: String, restaurantIdFilter: String, order: String): List<MenuOptionGroupDto> = emptyList()
    override suspend fun listMenuOptions(bearerAuth: String, select: String, restaurantIdFilter: String, order: String): List<MenuOptionDto> = emptyList()
    override suspend fun listBranchOverrides(bearerAuth: String, select: String, branchIdFilter: String): List<BranchMenuItemOverrideDto> = emptyList()
    override suspend fun listOrders(bearerAuth: String, select: String, customerFilter: String?, restaurantIdFilter: String?, diningSessionFilter: String?, branchFilter: String?, order: String, limit: Int): List<OrderRecordDto> = emptyList()
    override suspend fun getOrderById(bearerAuth: String, select: String, idFilter: String): List<OrderRecordDto> = emptyList()
    override suspend fun listOrderItems(bearerAuth: String, select: String, orderIdFilter: String): List<OrderItemRecordDto> = emptyList()
    override suspend fun listOrderStatusEvents(bearerAuth: String, select: String, orderIdFilter: String, order: String): List<OrderStatusEventDto> = emptyList()
    override suspend fun listOrderPayments(bearerAuth: String, select: String, orderIdFilter: String): List<OrderPaymentRecordDto> = emptyList()
    override suspend fun listKitchenTickets(bearerAuth: String, select: String, branchIdFilter: String, order: String): List<KitchenTicketDto> = emptyList()
    override suspend fun listNotifications(bearerAuth: String, select: String, order: String, limit: Int): List<RestaurantNotificationDto> = emptyList()
    override suspend fun getMyProfile(bearerAuth: String, select: String): List<RestaurantProfileDto> = emptyList()
}
