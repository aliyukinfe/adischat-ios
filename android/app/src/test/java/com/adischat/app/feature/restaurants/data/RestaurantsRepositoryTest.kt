package com.adischat.app.feature.restaurants.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

class RestaurantsRepositoryTest {
    @Before
    fun setUp() {
        RestaurantsSessionMemory.finishSignOut()
        RestaurantsSessionMemory.clear()
    }

    @After
    fun tearDown() {
        RestaurantsSessionMemory.finishSignOut()
        RestaurantsSessionMemory.clear()
    }

    @Test
    fun `feature flag disabled blocks repository calls before touching network`() = runBlocking {
        val edgeCalls = AtomicInteger(0)
        val env = RestaurantsEnvironment(
            supabaseUrl = "https://restaurants-test.supabase.co",
            supabasePublishableKey = "pk_test",
            identityBridgeUrl = "https://bridge-test.example",
            isIdentityBridgeEnabled = false,
            isDineInEnabled = false,
        )
        val repo = RestaurantsRepository(
            environmentProvider = { env },
            edgeApiProvider = {
                object : StubRestaurantsEdgeApi() {
                    override suspend fun resolveTableQr(
                        bearerAuth: String,
                        action: String,
                        body: ResolveQrRequest,
                    ): ResolveQrResponse {
                        edgeCalls.incrementAndGet()
                        return ResolveQrResponse(ok = true)
                    }
                }
            },
        )

        val result = repo.resolveTableQr("qr-token-123")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RestaurantsFailure.FeatureDisabled)
        assertEquals(0, edgeCalls.get())
    }

    @Test
    fun `submitDineInOrder preserves exact idempotency key across 401 retry`() = runBlocking {
        var nowSeconds = 1000L
        var currentAccess = "expired-access"
        val refreshCount = AtomicInteger(0)
        val observedKeys = mutableListOf<String>()
        val observedOrderTypes = mutableListOf<String>()
        val observedPaymentMethods = mutableListOf<String>()

        val env = RestaurantsEnvironment(
            supabaseUrl = "https://restaurants-test.supabase.co",
            supabasePublishableKey = "pk_test",
            identityBridgeUrl = "https://bridge-test.example",
            isIdentityBridgeEnabled = true,
            isDineInEnabled = true,
            isQaBuild = false,
        )

        RestaurantsSessionMemory.setSessionIfCurrent(
            RestaurantsSession(
                ownerAdisChatUid = "uid-1",
                profileId = "prof-1",
                accessToken = "expired-access",
                refreshToken = "refresh-1",
                expiresAtEpochSeconds = 5000L,
            ),
            RestaurantsSessionMemory.generation(),
        )

        val sessionManager = RestaurantsSessionManager(
            environmentProvider = { env },
            firebaseIdentityProvider = object : FirebaseIdentityProvider {
                override fun currentUid(): String = "uid-1"
                override suspend fun getIdToken(forceRefresh: Boolean): String = "fb-token"
            },
            goTrueApiProvider = {
                object : RestaurantsGoTrueApi {
                    override suspend fun refreshSession(body: GoTrueRefreshRequest): GoTrueRefreshResponse {
                        refreshCount.incrementAndGet()
                        currentAccess = "fresh-access"
                        return GoTrueRefreshResponse(
                            accessToken = "fresh-access",
                            refreshToken = "refresh-2",
                            expiresIn = 3600L,
                        )
                    }
                }
            },
            clock = EpochClock { nowSeconds },
        )

        val edgeApi = object : StubRestaurantsEdgeApi() {
            override suspend fun submitDineInOrder(
                bearerAuth: String,
                action: String,
                body: SubmitDineInOrderRequest,
            ): SubmitOrderResponse {
                observedKeys.add(body.idempotencyKey)
                observedOrderTypes.add(body.orderType)
                observedPaymentMethods.add(body.paymentMethod)

                if (bearerAuth == "Bearer expired-access") {
                    throw HttpException(
                        Response.error<String>(
                            401,
                            """{"ok":false,"error":{"code":"invalid_token","message":"Expired"}}"""
                                .toResponseBody("application/json".toMediaType()),
                        ),
                    )
                }
                assertEquals("Bearer fresh-access", bearerAuth)
                return SubmitOrderResponse(
                    ok = true,
                    order = SubmitOrderResultDto(
                        orderId = "ord-99",
                        orderNumber = "ORD-2026-001",
                        status = "SUBMITTED",
                        paymentMethod = "PAY_AT_RESTAURANT",
                        paymentStatus = "UNPAID",
                        totalMinor = 25000L,
                        idempotentReplay = false,
                    ),
                )
            }
        }

        val repo = RestaurantsRepository(
            environmentProvider = { env },
            sessionManager = sessionManager,
            edgeApiProvider = { edgeApi },
        )

        val res = repo.submitDineInOrder(
            diningSessionId = "ds-1",
            branchId = "br-1",
            items = listOf(SubmitOrderItemRequest(menuItemId = "mi-1", quantity = 1)),
            idempotencyKey = "fixed-idem-key-12345",
        )

        assertTrue(res.isSuccess)
        assertEquals("ord-99", res.getOrNull()?.orderId)
        assertEquals(1, refreshCount.get())
        assertEquals(listOf("fixed-idem-key-12345", "fixed-idem-key-12345"), observedKeys)
        assertEquals(listOf("DINE_IN", "DINE_IN"), observedOrderTypes)
        assertEquals(listOf("PAY_AT_RESTAURANT", "PAY_AT_RESTAURANT"), observedPaymentMethods)
    }

    @Test
    fun `qa build blocks order mutation when QA_ORDER_MUTATIONS_ENABLED is false`() = runBlocking {
        val qaEnv = RestaurantsEnvironment(
            supabaseUrl = "https://qa-restaurants.supabase.co",
            supabasePublishableKey = "pk_qa",
            identityBridgeUrl = "https://qa-bridge.example",
            isIdentityBridgeEnabled = true,
            isDineInEnabled = true,
            isQaBuild = true,
            areQaOrderMutationsEnabled = false,
        )
        val repo = RestaurantsRepository(environmentProvider = { qaEnv })

        val result = repo.submitDineInOrder(
            diningSessionId = "ds-qa",
            items = listOf(SubmitOrderItemRequest(menuItemId = "m-1", quantity = 1)),
            idempotencyKey = "qa-idem-1",
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RestaurantsFailure.QaBlocked)
    }

    @Test
    fun `restaurants-menu and restaurants-tables-qr endpoint routing annotations match backend contracts`() {
        val methods = RestaurantsEdgeApi::class.java.declaredMethods

        fun getPostPath(methodName: String): String? {
            val m = methods.firstOrNull { it.name == methodName } ?: return null
            val post = m.getAnnotation(retrofit2.http.POST::class.java)
            return post?.value
        }

        // restaurants-menu endpoints
        assertEquals("restaurants-menu", getPostPath("upsertMenuCategory"))
        assertEquals("restaurants-menu", getPostPath("upsertMenuItem"))
        assertEquals("restaurants-menu", getPostPath("activateRestaurant"))
        assertEquals("restaurants-menu", getPostPath("publishLive"))

        // restaurants-tables-qr endpoints
        assertEquals("restaurants-tables-qr", getPostPath("upsertTable"))
        assertEquals("restaurants-tables-qr", getPostPath("listTables"))
        assertEquals("restaurants-tables-qr", getPostPath("rotateTableQr"))
        assertEquals("restaurants-tables-qr", getPostPath("revokeTableQr"))
        assertEquals("restaurants-tables-qr", getPostPath("resolveTableQr"))
        assertEquals("restaurants-tables-qr", getPostPath("openDiningSession"))
    }
}

/**
 * Shared unit-test stub for [RestaurantsEdgeApi] so tests only override relevant endpoints.
 */
open class StubRestaurantsEdgeApi : RestaurantsEdgeApi {
    override suspend fun exchangeSession(action: String, body: RestaurantsExchangeSessionRequest): RestaurantsExchangeSessionResponse = error("unused")
    override suspend fun logoutSession(bearerAuth: String, action: String, body: Map<String, String>): RestaurantsOkResponse = error("unused")
    override suspend fun authorizeQrChallenge(bearerAuth: String, action: String, body: AuthorizeQrChallengeRequest): AuthorizeQrChallengeResponse = error("unused")
    override suspend fun createHandoffTicket(bearerAuth: String, action: String, body: Map<String, String>): CreateHandoffTicketResponse = error("unused")
    override suspend fun consumeHandoffTicket(action: String, body: ConsumeHandoffTicketRequest): ConsumeHandoffTicketResponse = error("unused")
    override suspend fun listMyApplications(bearerAuth: String, action: String, body: Map<String, String>): RestaurantApplicationListResponse = error("unused")
    override suspend fun saveApplicationDraft(bearerAuth: String, action: String, body: SaveApplicationDraftRequest): RestaurantApplicationResponse = error("unused")
    override suspend fun requestDocumentUploadUrl(bearerAuth: String, action: String, body: DocumentUploadUrlRequest): DocumentUploadUrlResponse = error("unused")
    override suspend fun confirmDocument(bearerAuth: String, action: String, body: ConfirmDocumentRequest): ConfirmDocumentResponse = error("unused")
    override suspend fun requestApplicationMediaUploadUrl(bearerAuth: String, action: String, body: ApplicationMediaUploadUrlRequest): MediaUploadUrlResponse = error("unused")
    override suspend fun submitApplication(bearerAuth: String, action: String, body: SubmitApplicationRequest): RestaurantApplicationResponse = error("unused")
    override suspend fun amIAdmin(bearerAuth: String, action: String, body: Map<String, String>): AmIAdminResponse = error("unused")
    override suspend fun adminListPendingApplications(bearerAuth: String, action: String, body: AdminListApplicationsRequest): AdminListApplicationsResponse = error("unused")
    override suspend fun adminStartReview(bearerAuth: String, action: String, body: AdminReviewActionRequest): AdminApplicationStatusResponse = error("unused")
    override suspend fun adminApproveApplication(bearerAuth: String, action: String, body: AdminReviewActionRequest): AdminApproveResponse = error("unused")
    override suspend fun adminRejectApplication(bearerAuth: String, action: String, body: AdminReviewActionRequest): AdminApplicationStatusResponse = error("unused")
    override suspend fun adminRequestChanges(bearerAuth: String, action: String, body: AdminReviewActionRequest): AdminApplicationStatusResponse = error("unused")
    override suspend fun adminInspectDocument(bearerAuth: String, action: String, body: AdminInspectDocumentRequest): AdminInspectDocumentResponse = error("unused")
    override suspend fun adminSetRestaurantStatus(bearerAuth: String, action: String, body: AdminRestaurantStatusRequest): AdminRestaurantStatusResponse = error("unused")
    override suspend fun adminListReports(bearerAuth: String, action: String, body: AdminListReportsRequest): AdminListReportsResponse = error("unused")
    override suspend fun adminResolveReport(bearerAuth: String, action: String, body: AdminResolveReportRequest): AdminResolveReportResponse = error("unused")
    override suspend fun adminListAuditLogs(bearerAuth: String, action: String, body: AdminListAuditLogsRequest): AdminListAuditLogsResponse = error("unused")
    override suspend fun getMyWorkspace(bearerAuth: String, action: String, body: Map<String, String>): AuthorizedWorkspaceResponse = error("unused")
    override suspend fun listMyInvitations(bearerAuth: String, action: String, body: Map<String, String>): StaffListInvitationsResponse = error("unused")
    override suspend fun acceptStaffInvitation(bearerAuth: String, action: String, body: StaffAcceptRequest): StaffAcceptResponse = error("unused")
    override suspend fun listStaffMembers(bearerAuth: String, action: String, body: StaffListMembersRequest): StaffListMembersResponse = error("unused")
    override suspend fun listStaffInvitations(bearerAuth: String, action: String, body: StaffListInvitationsRequest): StaffListInvitationsResponse = error("unused")
    override suspend fun revokeStaffInvitation(bearerAuth: String, action: String, body: StaffRevokeInvitationRequest): StaffRevokeInvitationResponse = error("unused")
    override suspend fun inviteStaffMember(bearerAuth: String, action: String, body: StaffInviteRequest): StaffInviteResponse = error("unused")
    override suspend fun revokeStaffMember(bearerAuth: String, action: String, body: StaffRevokeRequest): StaffRevokeResponse = error("unused")
    override suspend fun setBranchAvailability(bearerAuth: String, action: String, body: SetBranchAvailabilityRequest): RestaurantsOkResponse = error("unused")
    override suspend fun createBranch(bearerAuth: String, action: String, body: CreateBranchRequest): BranchMutationResponse = error("unused")
    override suspend fun updateBranch(bearerAuth: String, action: String, body: UpdateBranchRequest): BranchMutationResponse = error("unused")
    override suspend fun updateRestaurantSettings(bearerAuth: String, action: String, body: UpdateRestaurantSettingsRequest): RestaurantSettingsResponse = error("unused")
    override suspend fun upsertMenuCategory(bearerAuth: String, action: String, body: UpsertCategoryRequest): CategoryMutationResponse = error("unused")
    override suspend fun upsertMenuItem(bearerAuth: String, action: String, body: UpsertMenuItemRequest): MenuItemMutationResponse = error("unused")
    override suspend fun upsertOptionGroup(bearerAuth: String, action: String, body: UpsertOptionGroupRequest): OptionGroupMutationResponse = error("unused")
    override suspend fun upsertOption(bearerAuth: String, action: String, body: UpsertOptionRequest): OptionMutationResponse = error("unused")
    override suspend fun attachMenuItemImage(bearerAuth: String, action: String, body: AttachMenuItemImageRequest): MenuItemImageMutationResponse = error("unused")
    override suspend fun removeMenuItemImage(bearerAuth: String, action: String, body: RemoveMenuItemImageRequest): RemoveMenuItemImageResponse = error("unused")
    override suspend fun requestMenuPhotoUploadUrl(bearerAuth: String, action: String, body: PhotoUploadUrlRequest): MediaUploadUrlResponse = error("unused")
    override suspend fun activateRestaurant(bearerAuth: String, action: String, body: RestaurantScopedRequest): ActivateRestaurantResponse = error("unused")
    override suspend fun publishLive(bearerAuth: String, action: String, body: PublishLiveRequest): PublishLiveResponse = error("unused")
    override suspend fun resolveTableQr(bearerAuth: String, action: String, body: ResolveQrRequest): ResolveQrResponse = error("unused")
    override suspend fun listTables(bearerAuth: String, action: String, body: ListTablesRequest): ListTablesResponse = error("unused")
    override suspend fun upsertTable(bearerAuth: String, action: String, body: UpsertTableRequest): TableMutationResponse = error("unused")
    override suspend fun rotateTableQr(bearerAuth: String, action: String, body: TableScopedRequest): RotateQrResponse = error("unused")
    override suspend fun revokeTableQr(bearerAuth: String, action: String, body: TableScopedRequest): RestaurantsOkResponse = error("unused")
    override suspend fun openDiningSession(bearerAuth: String, action: String, body: TableScopedRequest): OpenSessionResponse = error("unused")
    override suspend fun rotateSessionCode(bearerAuth: String, action: String, body: DiningSessionScopedRequest): RotateSessionCodeResponse = error("unused")
    override suspend fun closeDiningSession(bearerAuth: String, action: String, body: DiningSessionScopedRequest): RestaurantsOkResponse = error("unused")
    override suspend fun submitDineInOrder(bearerAuth: String, action: String, body: SubmitDineInOrderRequest): SubmitOrderResponse = error("unused")
    override suspend fun transitionOrderStatus(bearerAuth: String, action: String, body: TransitionOrderRequest): TransitionOrderResponse = error("unused")
    override suspend fun confirmOrderPayment(bearerAuth: String, action: String, body: ConfirmPaymentRequest): ConfirmPaymentResponse = error("unused")
    override suspend fun syncBranchOrders(bearerAuth: String, action: String, body: SyncBranchOrdersRequest): SyncBranchOrdersResponse = error("unused")
    override suspend fun reportRestaurantContent(bearerAuth: String, action: String, body: ReportContentRequest): ReportContentResponse = error("unused")
}
