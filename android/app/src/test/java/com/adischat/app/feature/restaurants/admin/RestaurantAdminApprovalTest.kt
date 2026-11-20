package com.adischat.app.feature.restaurants.admin

import com.adischat.app.feature.restaurants.center.RestaurantApplicationStatus
import com.adischat.app.feature.restaurants.center.RestaurantOperationalStatus
import com.adischat.app.feature.restaurants.data.AdminApplicationStatusResponse
import com.adischat.app.feature.restaurants.data.AdminApproveResponse
import com.adischat.app.feature.restaurants.data.AdminApproveResultDto
import com.adischat.app.feature.restaurants.data.AdminRestaurantStatusResponse
import com.adischat.app.feature.restaurants.data.AdminReviewActionRequest
import com.adischat.app.feature.restaurants.data.RestaurantAuditLogDto
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RestaurantsErrorMapper
import com.adischat.app.feature.restaurants.data.RestaurantsFailure
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

class RestaurantAdminApprovalTest {

    private val gson = Gson()

    // ========================================================================
    // 1. Slug Generation & Validation Tests
    // ========================================================================

    @Test
    fun `generates valid slug from English brand name`() {
        val slug = RestaurantSlugHelper.generateSlug("lala habesha", "lela")
        assertEquals("lala-habesha", slug)
        assertTrue(RestaurantSlugHelper.isValidSlug(slug))
        assertNull(RestaurantSlugHelper.getSlugValidationError(slug))
    }

    @Test
    fun `generates valid Latin fallback for Amharic-only brand name`() {
        val amharicSlug = RestaurantSlugHelper.generateSlug("ላላ ሀበሻ", "ሌላ")
        assertEquals("restaurant-app", amharicSlug)
        assertTrue(RestaurantSlugHelper.isValidSlug(amharicSlug))
        assertNull(RestaurantSlugHelper.getSlugValidationError(amharicSlug))
    }

    @Test
    fun `generates valid Latin fallback for mixed Amharic and Latin brand name`() {
        val mixedSlug = RestaurantSlugHelper.generateSlug("ላላ Habesha Cafe 2", null)
        assertEquals("habesha-cafe-2", mixedSlug)
        assertTrue(RestaurantSlugHelper.isValidSlug(mixedSlug))
    }

    @Test
    fun `generates valid fallback for empty or whitespace-only name`() {
        val emptySlug = RestaurantSlugHelper.generateSlug("   ", "")
        assertEquals("restaurant-app", emptySlug)
        assertTrue(RestaurantSlugHelper.isValidSlug(emptySlug))
    }

    @Test
    fun `validates slug length boundaries and invalid characters`() {
        // Less than 3 chars
        assertFalse(RestaurantSlugHelper.isValidSlug("ab"))
        assertNotNull(RestaurantSlugHelper.getSlugValidationError("ab"))

        // More than 50 chars
        val tooLong = "a".repeat(51)
        assertFalse(RestaurantSlugHelper.isValidSlug(tooLong))
        assertNotNull(RestaurantSlugHelper.getSlugValidationError(tooLong))

        // Uppercase forbidden
        assertFalse(RestaurantSlugHelper.isValidSlug("Lala-Habesha"))
        assertEquals("Slug must be all lowercase.", RestaurantSlugHelper.getSlugValidationError("Lala-Habesha"))

        // Invalid characters
        assertFalse(RestaurantSlugHelper.isValidSlug("lala_habesha"))
        assertFalse(RestaurantSlugHelper.isValidSlug("lala habesha"))
        assertFalse(RestaurantSlugHelper.isValidSlug("lala@habesha!"))

        // Leading / trailing hyphen
        assertNotNull(RestaurantSlugHelper.getSlugValidationError("-lala-habesha"))
        assertNotNull(RestaurantSlugHelper.getSlugValidationError("lala-habesha-"))

        // Valid slugs
        assertTrue(RestaurantSlugHelper.isValidSlug("lala-habesha"))
        assertTrue(RestaurantSlugHelper.isValidSlug("cafe-123"))
        assertTrue(RestaurantSlugHelper.isValidSlug("a-1"))
    }

    // ========================================================================
    // 2. API Request Serialization Tests
    // ========================================================================

    @Test
    fun `serializes AdminReviewActionRequest with slug, notes, and application_id`() {
        val req = AdminReviewActionRequest(
            applicationId = "12106373-55e8-441a-8522-954945789f16",
            slug = "lala-habesha",
            notes = "Approved via admin console",
        )
        val json = gson.toJson(req)
        assertTrue(json.contains("\"application_id\":\"12106373-55e8-441a-8522-954945789f16\""))
        assertTrue(json.contains("\"slug\":\"lala-habesha\""))
        assertTrue(json.contains("\"notes\":\"Approved via admin console\""))
    }

    // ========================================================================
    // 3. Error Mapping Tests
    // ========================================================================

    @Test
    fun `maps backend invalid_slug error to clear user validation failure`() {
        val http400 = HttpException(
            Response.error<Any>(
                400,
                """{"ok":false,"error":{"code":"invalid_slug","message":"Valid lowercase slug (3-50 chars) required."}}"""
                    .toResponseBody("application/json".toMediaType()),
            ),
        )
        val failure = RestaurantsErrorMapper.mapThrowable(http400)
        assertTrue(failure is RestaurantsFailure.Validation)
        assertEquals("invalid_slug", failure.code)
        assertTrue(failure.userMessage.contains("slug"))
    }

    @Test
    fun `maps backend slug_already_taken error to conflict failure`() {
        val http409 = HttpException(
            Response.error<Any>(
                409,
                """{"ok":false,"error":{"code":"slug_already_taken","message":"Restaurant URL slug is already taken."}}"""
                    .toResponseBody("application/json".toMediaType()),
            ),
        )
        val failure = RestaurantsErrorMapper.mapThrowable(http409)
        assertTrue(failure is RestaurantsFailure.Conflict)
        assertEquals("slug_already_taken", failure.code)
        assertTrue(failure.userMessage.contains("already taken"))
    }

    @Test
    fun `maps backend missing_business_license error to validation failure`() {
        val http400 = HttpException(
            Response.error<Any>(
                400,
                """{"ok":false,"error":{"code":"missing_business_license","message":"Approved business license document is required."}}"""
                    .toResponseBody("application/json".toMediaType()),
            ),
        )
        val failure = RestaurantsErrorMapper.mapThrowable(http400)
        assertTrue(failure is RestaurantsFailure.Validation)
        assertEquals("missing_business_license", failure.code)
        assertTrue(failure.userMessage.contains("business license"))
    }

    @Test
    fun `maps backend self_approval_forbidden to forbidden failure`() {
        val http403 = HttpException(
            Response.error<Any>(
                403,
                """{"ok":false,"error":{"code":"self_approval_forbidden","message":"Self-approval is forbidden: applicants cannot review their own application."}}"""
                    .toResponseBody("application/json".toMediaType()),
            ),
        )
        val failure = RestaurantsErrorMapper.mapThrowable(http403)
        assertTrue(failure is RestaurantsFailure.Forbidden)
        assertEquals("self_approval_forbidden", failure.code)
        assertTrue(failure.userMessage.contains("Self-approval"))
    }

    // ========================================================================
    // 4. Coordinator Workflow Tests
    // ========================================================================

    @Test
    fun `coordinator rejects invalid slug without calling network`() = runBlocking {
        val fake = FakeApprovalAdminDataSource()
        val coordinator = createCoordinator(fake)
        coordinator.loadAdminWorkspace()
        delay(30)

        // Invalid slug: uppercase characters
        coordinator.approveApplication("app-test", slug = "INVALID_SLUG")
        delay(20)

        assertEquals(0, fake.approvalCalls.get())
        assertNotNull(coordinator.state.value.errorBanner)
        assertTrue(coordinator.state.value.errorBanner!!.contains("lowercase"))
    }

    @Test
    fun `coordinator executes approval, refreshes state, and sets action notice`() = runBlocking {
        val fake = FakeApprovalAdminDataSource()
        val coordinator = createCoordinator(fake)
        coordinator.loadAdminWorkspace()
        delay(30)

        coordinator.approveApplication("app-test", slug = "lala-habesha", notes = "Approved")
        delay(40)

        assertEquals(1, fake.approvalCalls.get())
        assertNull(coordinator.state.value.errorBanner)
        assertNotNull(coordinator.state.value.actionNotice)
        assertTrue(coordinator.state.value.actionNotice!!.contains("Restaurant Approved"))
        assertTrue(coordinator.state.value.actionNotice!!.contains("rest-created-1"))

        // Application status is APPROVED
        val app = coordinator.state.value.applications.first { it.id == "app-test" }
        assertEquals(RestaurantApplicationStatus.APPROVED, app.status)
        assertEquals("rest-created-1", app.approvedRestaurantId)

        // Created restaurant has pending_setup status and is not live
        val created = coordinator.state.value.restaurants.first { it.id == "rest-created-1" }
        assertEquals(RestaurantOperationalStatus.PENDING_SETUP, created.status)
        assertFalse(created.isLiveOrderingEnabled)
    }

    @Test
    fun `duplicate approval calls while isWorking are guarded`() = runBlocking {
        val fake = FakeApprovalAdminDataSource(delayMs = 100)
        val coordinator = createCoordinator(fake)
        coordinator.loadAdminWorkspace()
        delay(30)

        // First call sets isWorking
        coordinator.approveApplication("app-test", slug = "lala-habesha")
        assertTrue(coordinator.state.value.isWorking)

        // Immediate second call should be ignored
        coordinator.approveApplication("app-test", slug = "lala-habesha")

        delay(150)
        assertEquals(1, fake.approvalCalls.get())
        assertFalse(coordinator.state.value.isWorking)
    }

    private fun createCoordinator(dataSource: RestaurantAdminDataSource): RestaurantAdminCoordinator {
        return RestaurantAdminCoordinator(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            dataSource = dataSource,
            environmentProvider = {
                RestaurantsEnvironment(
                    supabaseUrl = "https://test.supabase.co",
                    supabasePublishableKey = "pk_test",
                    identityBridgeUrl = "https://bridge.example",
                    isIdentityBridgeEnabled = true,
                    isAdminEnabled = true,
                )
            },
            accountUidProvider = { "admin-uid" },
        )
    }
}

private class FakeApprovalAdminDataSource(
    private val delayMs: Long = 0,
) : RestaurantAdminDataSource {
    val approvalCalls = AtomicInteger(0)

    private val apps = mutableListOf(
        AdminApplicationUi(
            id = "app-test",
            applicantProfileId = "applicant-other-uid",
            brandName = "lala habesha",
            legalName = "lela",
            contactEmail = "partner@example.invalid",
            contactPhone = "+251911223344",
            tinNumber = "0012345678",
            status = RestaurantApplicationStatus.UNDER_REVIEW,
            submittedAt = "2026-11-20T21:56:23Z",
            branchCount = 1,
            draftMenuItemsCount = 4,
            documents = listOf(
                AdminApplicationDocUi("doc-1", "business_license", "license.png", "image/png", 177638L),
            ),
        ),
    )

    private val rests = mutableListOf<AdminRestaurantOverviewUi>()

    override suspend fun verifyPlatformAdminAccess(accountUid: String): Result<Boolean> = Result.success(true)

    override suspend fun loadApplications(): Result<List<AdminApplicationUi>> = Result.success(apps.toList())

    override suspend fun loadRestaurantsAndBranches(): Result<List<AdminRestaurantOverviewUi>> = Result.success(rests.toList())

    override suspend fun loadAuditLogs(): Result<List<RestaurantAuditLogDto>> = Result.success(emptyList())

    override suspend fun startReview(applicationId: String, notes: String?): Result<AdminApplicationStatusResponse> =
        Result.success(AdminApplicationStatusResponse(true, applicationId, "UNDER_REVIEW"))

    override suspend fun inspectDocument(applicationId: String, documentId: String): Result<String?> =
        Result.success("https://preview.invalid/doc")

    override suspend fun approveApplication(applicationId: String, slug: String, notes: String?): Result<AdminApproveResponse> {
        if (delayMs > 0) delay(delayMs)
        approvalCalls.incrementAndGet()
        val restId = "rest-created-1"
        rests.add(
            AdminRestaurantOverviewUi(
                id = restId,
                name = "lala habesha",
                slug = slug,
                status = RestaurantOperationalStatus.PENDING_SETUP,
                isLiveOrderingEnabled = false,
            ),
        )
        apps.replaceAll {
            if (it.id == applicationId) {
                it.copy(status = RestaurantApplicationStatus.APPROVED, approvedRestaurantId = restId)
            } else it
        }
        return Result.success(
            AdminApproveResponse(
                ok = true,
                result = AdminApproveResultDto(applicationId, restId, "APPROVED", 4),
            ),
        )
    }

    override suspend fun rejectApplication(applicationId: String, notes: String): Result<AdminApplicationStatusResponse> =
        Result.success(AdminApplicationStatusResponse(true, applicationId, "REJECTED"))

    override suspend fun requestChanges(applicationId: String, notes: String): Result<AdminApplicationStatusResponse> =
        Result.success(AdminApplicationStatusResponse(true, applicationId, "CHANGES_REQUESTED"))

    override suspend fun suspendRestaurant(restaurantId: String, reason: String): Result<AdminRestaurantStatusResponse> =
        Result.success(AdminRestaurantStatusResponse(true, restaurantId, "suspended"))

    override suspend fun reactivateRestaurant(restaurantId: String, reason: String): Result<AdminRestaurantStatusResponse> =
        Result.success(AdminRestaurantStatusResponse(true, restaurantId, "active"))
}
