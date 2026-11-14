package com.adischat.app.feature.restaurants.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Future Cloudflare Worker identity bridge endpoint for Restaurants-scoped assertions.
 * Never shares credentials or secrets with AdisGebeya (`ADISHOP_*`).
 */
interface RestaurantsIdentityBridgeApi {
    @POST("api/restaurants/session-assertion")
    suspend fun requestSessionAssertion(
        @Header("Authorization") firebaseBearerAuth: String,
        @Body body: RestaurantsAssertionRequest = RestaurantsAssertionRequest(),
    ): RestaurantsAssertionResponse
}

/**
 * GoTrue Auth endpoint on the Restaurants Supabase project for token refresh.
 */
interface RestaurantsGoTrueApi {
    @POST("token?grant_type=refresh_token")
    suspend fun refreshSession(
        @Body body: GoTrueRefreshRequest,
    ): GoTrueRefreshResponse
}

/**
 * Phase R3B Restaurants Supabase Edge Functions (`functions/v1/<function>?action=<action>`).
 */
interface RestaurantsEdgeApi {
    // ------------------------------------------------------------------------
    // restaurants-exchange-session
    // ------------------------------------------------------------------------
    @POST("restaurants-exchange-session")
    suspend fun exchangeSession(
        @Query("action") action: String = "exchange",
        @Body body: RestaurantsExchangeSessionRequest,
    ): RestaurantsExchangeSessionResponse

    @POST("restaurants-exchange-session")
    suspend fun logoutSession(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "logout",
        @Body body: Map<String, String> = emptyMap(),
    ): RestaurantsOkResponse

    @POST("restaurants-exchange-session")
    suspend fun authorizeQrChallenge(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "authorize-qr-challenge",
        @Body body: AuthorizeQrChallengeRequest,
    ): AuthorizeQrChallengeResponse

    @POST("restaurants-exchange-session")
    suspend fun createHandoffTicket(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "create-handoff-ticket",
        @Body body: Map<String, String> = emptyMap(),
    ): CreateHandoffTicketResponse

    @POST("restaurants-exchange-session")
    suspend fun consumeHandoffTicket(
        @Query("action") action: String = "consume-handoff-ticket",
        @Body body: ConsumeHandoffTicketRequest,
    ): ConsumeHandoffTicketResponse

    // ------------------------------------------------------------------------
    // restaurants-application
    // ------------------------------------------------------------------------
    @POST("restaurants-application")
    suspend fun listMyApplications(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "list-mine",
        @Body body: Map<String, String> = emptyMap(),
    ): RestaurantApplicationListResponse

    @POST("restaurants-application")
    suspend fun saveApplicationDraft(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "save-draft",
        @Body body: SaveApplicationDraftRequest,
    ): RestaurantApplicationResponse

    @POST("restaurants-application")
    suspend fun requestDocumentUploadUrl(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "document-upload-url",
        @Body body: DocumentUploadUrlRequest,
    ): DocumentUploadUrlResponse

    @POST("restaurants-application")
    suspend fun confirmDocument(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "confirm-document",
        @Body body: ConfirmDocumentRequest,
    ): ConfirmDocumentResponse

    @POST("restaurants-application")
    suspend fun requestApplicationMediaUploadUrl(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "media-upload-url",
        @Body body: ApplicationMediaUploadUrlRequest,
    ): MediaUploadUrlResponse

    @POST("restaurants-application")
    suspend fun submitApplication(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "submit",
        @Body body: SubmitApplicationRequest,
    ): RestaurantApplicationResponse

    // ------------------------------------------------------------------------
    // restaurants-admin-review
    // ------------------------------------------------------------------------
    @POST("restaurants-admin-review")
    suspend fun adminStartReview(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "start-review",
        @Body body: AdminReviewActionRequest,
    ): AdminApplicationStatusResponse

    @POST("restaurants-admin-review")
    suspend fun adminApproveApplication(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "approve",
        @Body body: AdminReviewActionRequest,
    ): AdminApproveResponse

    @POST("restaurants-admin-review")
    suspend fun adminRejectApplication(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "reject",
        @Body body: AdminReviewActionRequest,
    ): AdminApplicationStatusResponse

    @POST("restaurants-admin-review")
    suspend fun adminRequestChanges(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "request-changes",
        @Body body: AdminReviewActionRequest,
    ): AdminApplicationStatusResponse

    @POST("restaurants-admin-review")
    suspend fun amIAdmin(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "am-i-admin",
        @Body body: Map<String, String> = emptyMap(),
    ): AmIAdminResponse

    @POST("restaurants-admin-review")
    suspend fun adminListPendingApplications(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "list-pending-applications",
        @Body body: AdminListApplicationsRequest,
    ): AdminListApplicationsResponse

    @POST("restaurants-admin-review")
    suspend fun adminInspectDocument(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "inspect-document",
        @Body body: AdminInspectDocumentRequest,
    ): AdminInspectDocumentResponse

    @POST("restaurants-admin-review")
    suspend fun adminSetRestaurantStatus(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String, // "suspend-restaurant" | "reactivate-restaurant"
        @Body body: AdminRestaurantStatusRequest,
    ): AdminRestaurantStatusResponse

    @POST("restaurants-admin-review")
    suspend fun adminListReports(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "list-reports",
        @Body body: AdminListReportsRequest,
    ): AdminListReportsResponse

    @POST("restaurants-admin-review")
    suspend fun adminResolveReport(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "resolve-report",
        @Body body: AdminResolveReportRequest,
    ): AdminResolveReportResponse

    @POST("restaurants-admin-review")
    suspend fun adminListAuditLogs(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "list-audit-logs",
        @Body body: AdminListAuditLogsRequest,
    ): AdminListAuditLogsResponse

    // ------------------------------------------------------------------------
    // restaurants-staff
    // ------------------------------------------------------------------------
    @POST("restaurants-staff")
    suspend fun getMyWorkspace(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "my-workspace",
        @Body body: Map<String, String> = emptyMap(),
    ): AuthorizedWorkspaceResponse

    @POST("restaurants-staff")
    suspend fun listMyInvitations(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "my-invitations",
        @Body body: Map<String, String> = emptyMap(),
    ): StaffListInvitationsResponse

    @POST("restaurants-staff")
    suspend fun acceptStaffInvitation(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "accept",
        @Body body: StaffAcceptRequest,
    ): StaffAcceptResponse

    @POST("restaurants-staff")
    suspend fun listStaffMembers(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "list-members",
        @Body body: StaffListMembersRequest,
    ): StaffListMembersResponse

    @POST("restaurants-staff")
    suspend fun listStaffInvitations(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "list-invitations",
        @Body body: StaffListInvitationsRequest,
    ): StaffListInvitationsResponse

    @POST("restaurants-staff")
    suspend fun revokeStaffInvitation(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "revoke-invitation",
        @Body body: StaffRevokeInvitationRequest,
    ): StaffRevokeInvitationResponse

    @POST("restaurants-staff")
    suspend fun inviteStaffMember(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "invite",
        @Body body: StaffInviteRequest,
    ): StaffInviteResponse

    @POST("restaurants-staff")
    suspend fun revokeStaffMember(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "revoke",
        @Body body: StaffRevokeRequest,
    ): StaffRevokeResponse

    // ------------------------------------------------------------------------
    // restaurants-menu
    // ------------------------------------------------------------------------
    @POST("restaurants-menu")
    suspend fun setBranchAvailability(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "set-branch-availability",
        @Body body: SetBranchAvailabilityRequest,
    ): RestaurantsOkResponse

    @POST("restaurants-menu")
    suspend fun createBranch(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "create-branch",
        @Body body: CreateBranchRequest,
    ): BranchMutationResponse

    @POST("restaurants-menu")
    suspend fun updateBranch(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "update-branch",
        @Body body: UpdateBranchRequest,
    ): BranchMutationResponse

    @POST("restaurants-menu")
    suspend fun updateRestaurantSettings(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "update-settings",
        @Body body: UpdateRestaurantSettingsRequest,
    ): RestaurantSettingsResponse

    @POST("restaurants-menu")
    suspend fun upsertMenuCategory(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "upsert-category",
        @Body body: UpsertCategoryRequest,
    ): CategoryMutationResponse

    @POST("restaurants-menu")
    suspend fun upsertMenuItem(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "upsert-item",
        @Body body: UpsertMenuItemRequest,
    ): MenuItemMutationResponse

    @POST("restaurants-menu")
    suspend fun upsertOptionGroup(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "upsert-option-group",
        @Body body: UpsertOptionGroupRequest,
    ): OptionGroupMutationResponse

    @POST("restaurants-menu")
    suspend fun upsertOption(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "upsert-option",
        @Body body: UpsertOptionRequest,
    ): OptionMutationResponse

    @POST("restaurants-menu")
    suspend fun attachMenuItemImage(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "attach-item-image",
        @Body body: AttachMenuItemImageRequest,
    ): MenuItemImageMutationResponse

    @POST("restaurants-menu")
    suspend fun removeMenuItemImage(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "remove-item-image",
        @Body body: RemoveMenuItemImageRequest,
    ): RemoveMenuItemImageResponse

    @POST("restaurants-menu")
    suspend fun requestMenuPhotoUploadUrl(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "photo-upload-url",
        @Body body: PhotoUploadUrlRequest,
    ): MediaUploadUrlResponse

    @POST("restaurants-menu")
    suspend fun activateRestaurant(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "activate-restaurant",
        @Body body: RestaurantScopedRequest,
    ): ActivateRestaurantResponse

    @POST("restaurants-menu")
    suspend fun publishLive(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "publish-live",
        @Body body: PublishLiveRequest,
    ): PublishLiveResponse

    // ------------------------------------------------------------------------
    // restaurants-tables-qr
    // ------------------------------------------------------------------------
    @POST("restaurants-tables-qr")
    suspend fun resolveTableQr(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "resolve-qr",
        @Body body: ResolveQrRequest,
    ): ResolveQrResponse

    @POST("restaurants-tables-qr")
    suspend fun listTables(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "list-tables",
        @Body body: ListTablesRequest,
    ): ListTablesResponse

    @POST("restaurants-tables-qr")
    suspend fun upsertTable(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "upsert-table",
        @Body body: UpsertTableRequest,
    ): TableMutationResponse

    @POST("restaurants-tables-qr")
    suspend fun rotateTableQr(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "rotate-qr",
        @Body body: TableScopedRequest,
    ): RotateQrResponse

    @POST("restaurants-tables-qr")
    suspend fun revokeTableQr(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "revoke-qr",
        @Body body: TableScopedRequest,
    ): RestaurantsOkResponse

    @POST("restaurants-tables-qr")
    suspend fun openDiningSession(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "open-session",
        @Body body: TableScopedRequest,
    ): OpenSessionResponse

    @POST("restaurants-tables-qr")
    suspend fun rotateSessionCode(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "rotate-session-code",
        @Body body: DiningSessionScopedRequest,
    ): RotateSessionCodeResponse

    @POST("restaurants-tables-qr")
    suspend fun closeDiningSession(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "close-session",
        @Body body: DiningSessionScopedRequest,
    ): RestaurantsOkResponse

    // ------------------------------------------------------------------------
    // restaurants-orders
    // ------------------------------------------------------------------------
    @POST("restaurants-orders")
    suspend fun submitDineInOrder(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "submit",
        @Body body: SubmitDineInOrderRequest,
    ): SubmitOrderResponse

    @POST("restaurants-orders")
    suspend fun transitionOrderStatus(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "transition",
        @Body body: TransitionOrderRequest,
    ): TransitionOrderResponse

    @POST("restaurants-orders")
    suspend fun confirmOrderPayment(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "confirm-payment",
        @Body body: ConfirmPaymentRequest,
    ): ConfirmPaymentResponse

    @POST("restaurants-orders")
    suspend fun syncBranchOrders(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "sync-branch-orders",
        @Body body: SyncBranchOrdersRequest,
    ): SyncBranchOrdersResponse

    @POST("restaurants-orders")
    suspend fun reportRestaurantContent(
        @Header("Authorization") bearerAuth: String,
        @Query("action") action: String = "report-content",
        @Body body: ReportContentRequest,
    ): ReportContentResponse
}

/**
 * RLS-protected PostgREST read interface for Restaurants tables explicitly readable
 * by authenticated users in Phase R2B (`005_rls_and_realtime.sql`).
 * Sensitive tables (`table_qr_tokens`, `restaurant_staff_invitations`, `restaurant_reports`,
 * `restaurant_audit_logs`, `platform_admins`, `restaurant_user_links`) remain Edge-Function only.
 */
interface RestaurantsPostgrestApi {
    @GET("restaurants")
    suspend fun listRestaurants(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("status") statusFilter: String? = "eq.active",
        @Query("is_live_ordering_enabled") liveFilter: String? = "eq.true",
        @Query("order") order: String = "name.asc",
    ): List<RestaurantSummaryDto>

    @GET("restaurants")
    suspend fun getRestaurantById(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("id") idFilter: String,
    ): List<RestaurantSummaryDto>

    @GET("restaurant_branches")
    suspend fun listBranches(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("restaurant_id") restaurantIdFilter: String? = null,
        @Query("status") statusFilter: String? = "eq.active",
        @Query("order") order: String = "name.asc",
    ): List<RestaurantBranchDto>

    @GET("menu_categories")
    suspend fun listMenuCategories(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("restaurant_id") restaurantIdFilter: String,
        @Query("is_active") isActiveFilter: String? = "eq.true",
        @Query("order") order: String = "sort_order.asc",
    ): List<MenuCategoryDto>

    @GET("menu_items")
    suspend fun listMenuItems(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("restaurant_id") restaurantIdFilter: String,
        @Query("is_active") isActiveFilter: String? = "eq.true",
        @Query("order") order: String = "sort_order.asc,name.asc",
    ): List<MenuItemDto>

    @GET("menu_item_images")
    suspend fun listMenuItemImages(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("restaurant_id") restaurantIdFilter: String,
        @Query("order") order: String = "sort_order.asc",
    ): List<MenuItemImageDto>

    @GET("menu_option_groups")
    suspend fun listMenuOptionGroups(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("restaurant_id") restaurantIdFilter: String,
        @Query("order") order: String = "sort_order.asc",
    ): List<MenuOptionGroupDto>

    @GET("menu_options")
    suspend fun listMenuOptions(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("restaurant_id") restaurantIdFilter: String,
        @Query("order") order: String = "sort_order.asc",
    ): List<MenuOptionDto>

    @GET("branch_menu_item_overrides")
    suspend fun listBranchOverrides(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("branch_id") branchIdFilter: String,
    ): List<BranchMenuItemOverrideDto>

    @GET("orders")
    suspend fun listOrders(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("customer_profile_id") customerFilter: String? = null,
        @Query("restaurant_id") restaurantIdFilter: String? = null,
        @Query("dining_session_id") diningSessionFilter: String? = null,
        @Query("branch_id") branchFilter: String? = null,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50,
    ): List<OrderRecordDto>

    @GET("orders")
    suspend fun getOrderById(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("id") idFilter: String,
    ): List<OrderRecordDto>

    @GET("order_items")
    suspend fun listOrderItems(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("order_id") orderIdFilter: String,
    ): List<OrderItemRecordDto>

    @GET("order_status_events")
    suspend fun listOrderStatusEvents(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("order_id") orderIdFilter: String,
        @Query("order") order: String = "created_at.asc",
    ): List<OrderStatusEventDto>

    @GET("order_payments")
    suspend fun listOrderPayments(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("order_id") orderIdFilter: String,
    ): List<OrderPaymentRecordDto>

    @GET("kitchen_tickets")
    suspend fun listKitchenTickets(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("branch_id") branchIdFilter: String,
        @Query("order") order: String = "created_at.desc",
    ): List<KitchenTicketDto>

    @GET("restaurant_notifications")
    suspend fun listNotifications(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50,
    ): List<RestaurantNotificationDto>

    @GET("restaurant_profiles")
    suspend fun getMyProfile(
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "*",
    ): List<RestaurantProfileDto>
}

object RestaurantsApiClientFactory {
    fun buildSupabaseOkHttpClient(environment: RestaurantsEnvironment): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(RestaurantsEnvironment.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(RestaurantsEnvironment.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(RestaurantsEnvironment.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(SupabaseApiKeyInterceptor(environment.supabasePublishableKey))
            .addInterceptor(QaOrderMutationGuardInterceptor(environment))
            .build()

    fun buildBridgeOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(RestaurantsEnvironment.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(RestaurantsEnvironment.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(RestaurantsEnvironment.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    fun buildSignedUploadOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(RestaurantsEnvironment.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(RestaurantsEnvironment.UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(RestaurantsEnvironment.UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

    fun createEdgeApi(
        environment: RestaurantsEnvironment,
        okHttpClient: OkHttpClient = buildSupabaseOkHttpClient(environment),
    ): RestaurantsEdgeApi? {
        val baseUrl = environment.functionsBaseUrl()
        if (baseUrl.isEmpty()) return null
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RestaurantsEdgeApi::class.java)
    }

    fun createPostgrestApi(
        environment: RestaurantsEnvironment,
        okHttpClient: OkHttpClient = buildSupabaseOkHttpClient(environment),
    ): RestaurantsPostgrestApi? {
        val baseUrl = environment.restBaseUrl()
        if (baseUrl.isEmpty()) return null
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RestaurantsPostgrestApi::class.java)
    }

    fun createGoTrueApi(
        environment: RestaurantsEnvironment,
        okHttpClient: OkHttpClient = buildSupabaseOkHttpClient(environment),
    ): RestaurantsGoTrueApi? {
        val baseUrl = environment.authBaseUrl()
        if (baseUrl.isEmpty()) return null
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RestaurantsGoTrueApi::class.java)
    }

    fun createIdentityBridgeApi(
        environment: RestaurantsEnvironment,
        okHttpClient: OkHttpClient = buildBridgeOkHttpClient(),
    ): RestaurantsIdentityBridgeApi? {
        val baseUrl = environment.normalizedIdentityBridgeBaseUrl()
        if (baseUrl.isEmpty()) return null
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RestaurantsIdentityBridgeApi::class.java)
    }
}

private class SupabaseApiKeyInterceptor(
    private val publishableKey: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("Accept", "application/json")
        if (publishableKey.isNotBlank()) {
            builder.header("apikey", publishableKey)
        }
        return chain.proceed(builder.build())
    }
}

private class QaOrderMutationGuardInterceptor(
    private val environment: RestaurantsEnvironment,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (environment.areOrderMutationsBlockedInQa() &&
            request.method.equals("POST", ignoreCase = true) &&
            request.url.encodedPath.contains("restaurants-orders")
        ) {
            val action = request.url.queryParameter("action").orEmpty()
            if (action == "submit" || action == "transition" || action == "confirm-payment") {
                throw IOException("Order mutations are disabled in QA builds.")
            }
        }
        return chain.proceed(request)
    }
}
