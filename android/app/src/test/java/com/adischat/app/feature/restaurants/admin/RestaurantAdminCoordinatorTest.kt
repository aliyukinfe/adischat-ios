package com.adischat.app.feature.restaurants.admin

import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.center.RestaurantApplicationStatus
import com.adischat.app.feature.restaurants.center.RestaurantOperationalStatus
import com.adischat.app.feature.restaurants.data.AdminApplicationStatusResponse
import com.adischat.app.feature.restaurants.data.AdminApproveResponse
import com.adischat.app.feature.restaurants.data.AdminApproveResultDto
import com.adischat.app.feature.restaurants.data.AdminRestaurantStatusResponse
import com.adischat.app.feature.restaurants.data.RestaurantAuditLogDto
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RestaurantAdminCoordinatorTest {

    @Test
    fun `disabled admin flag blocks admin workspace and review actions`() = runBlocking {
        assertFalse(FeatureFlags.RESTAURANT_ADMIN_ENABLED)
        val fake = FakeAdminDataSource()
        val coordinator = RestaurantAdminCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { RestaurantsEnvironment(isAdminEnabled = false) },
            accountUidProvider = { "admin-1" },
        )

        coordinator.loadAdminWorkspace()
        coordinator.approveApplication("app-1", slug = "habesha-hearth")
        delay(30)

        assertEquals(0, fake.calls.get())
        assertEquals(RestaurantsLoadState.Disabled, coordinator.state.value.loadState)
    }

    @Test
    fun `non-admin user cannot access admin workspace and self-approval is strictly blocked`() = runBlocking {
        val fake = FakeAdminDataSource()
        fake.authorizedAdminUid = "real-admin-1"

        // Unauthorized caller blocked
        val nonAdminCoordinator = RestaurantAdminCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledAdminEnv() },
            accountUidProvider = { "regular-user" },
        )
        nonAdminCoordinator.loadAdminWorkspace()
        delay(40)
        assertFalse(nonAdminCoordinator.state.value.isAuthorizedAdmin)
        assertTrue(nonAdminCoordinator.state.value.loadState is RestaurantsLoadState.Error)

        // Admin who is also the applicant of app-self CANNOT approve own application
        val callsBefore = fake.calls.get()
        val adminCoordinator = RestaurantAdminCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledAdminEnv() },
            accountUidProvider = { "real-admin-1" },
        )
        adminCoordinator.loadAdminWorkspace()
        delay(40)

        adminCoordinator.approveApplication("app-self", slug = "conflict-bistro")
        delay(30)
        assertNotNull(adminCoordinator.state.value.errorBanner)
        assertEquals(
            RestaurantApplicationStatus.SUBMITTED,
            adminCoordinator.state.value.applications.first { it.id == "app-self" }.status,
        )
    }

    @Test
    fun `admin review actions inspect docs, approve into pending setup, request changes, reject, and suspend`() = runBlocking {
        val fake = FakeAdminDataSource()
        val coordinator = RestaurantAdminCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledAdminEnv() },
            accountUidProvider = { "real-admin-1" },
        )

        coordinator.loadAdminWorkspace()
        delay(40)
        assertTrue(coordinator.state.value.isAuthorizedAdmin)
        assertEquals(3, coordinator.state.value.applications.size)

        // Start review + inspect private doc
        coordinator.startReview("app-1")
        delay(30)
        assertEquals(
            RestaurantApplicationStatus.UNDER_REVIEW,
            coordinator.state.value.applications.first { it.id == "app-1" }.status,
        )

        coordinator.inspectDocument("app-1", "doc-1")
        delay(30)
        val doc = coordinator.state.value.applications.first { it.id == "app-1" }.documents.first()
        assertEquals(AdminDocReviewState.VERIFIED, doc.reviewState)
        assertNotNull(doc.lastInspectedUrlPreview)

        // Approve app-1 -> restaurant starts in PENDING_SETUP with live ordering OFF
        coordinator.approveApplication("app-1", slug = "habesha-hearth", notes = "Verified documents")
        delay(40)
        assertEquals(
            RestaurantApplicationStatus.APPROVED,
            coordinator.state.value.applications.first { it.id == "app-1" }.status,
        )
        val createdRest = coordinator.state.value.restaurants.first { it.id == "rest-new-1" }
        assertEquals(RestaurantOperationalStatus.PENDING_SETUP, createdRest.status)
        assertFalse(createdRest.isLiveOrderingEnabled)

        // Request changes & Reject require feedback notes
        coordinator.requestChanges("app-2", "   ")
        assertNotNull(coordinator.state.value.errorBanner)
        coordinator.requestChanges("app-2", "Upload clearer TIN photo")
        delay(30)
        assertEquals(
            RestaurantApplicationStatus.CHANGES_REQUESTED,
            coordinator.state.value.applications.first { it.id == "app-2" }.status,
        )

        coordinator.rejectApplication("app-self", "Missing valid license")
        delay(30)
        assertEquals(
            RestaurantApplicationStatus.REJECTED,
            coordinator.state.value.applications.first { it.id == "app-self" }.status,
        )

        // Suspend active restaurant turns live ordering OFF cleanly
        coordinator.suspendRestaurant("rest-active", "Food safety audit hold")
        delay(30)
        val suspended = coordinator.state.value.restaurants.first { it.id == "rest-active" }
        assertEquals(RestaurantOperationalStatus.SUSPENDED, suspended.status)
        assertFalse(suspended.isLiveOrderingEnabled)

        // Account switch wipes admin session completely
        coordinator.onAccountChanged("other-user")
        assertFalse(coordinator.state.value.isAuthorizedAdmin)
        assertTrue(coordinator.state.value.applications.isEmpty())
        assertTrue(coordinator.state.value.restaurants.isEmpty())
        assertNull(coordinator.state.value.selectedApplication)
    }

    private fun enabledAdminEnv() = RestaurantsEnvironment(
        supabaseUrl = "https://test.supabase.co",
        supabasePublishableKey = "pk_test",
        identityBridgeUrl = "https://bridge.example",
        isIdentityBridgeEnabled = true,
        isAdminEnabled = true,
    )
}

private class FakeAdminDataSource : RestaurantAdminDataSource {
    val calls = AtomicInteger(0)
    var authorizedAdminUid = "real-admin-1"

    private var restaurants = mutableListOf(
        AdminRestaurantOverviewUi(
            id = "rest-active",
            name = "Existing Grill",
            slug = "existing-grill",
            status = RestaurantOperationalStatus.ACTIVE,
            isLiveOrderingEnabled = true,
        ),
    )

    private var applications = mutableListOf(
        AdminApplicationUi(
            id = "app-1",
            applicantProfileId = "owner-other",
            brandName = "Habesha Hearth",
            legalName = "Hearth PLC",
            contactEmail = "partner1@example.invalid",
            contactPhone = "+251900000001",
            tinNumber = "0041234",
            status = RestaurantApplicationStatus.SUBMITTED,
            submittedAt = "2026-11-10",
            branchCount = 2,
            draftMenuItemsCount = 8,
            documents = listOf(AdminApplicationDocUi("doc-1", "business_license", "lic.pdf", "application/pdf", 120_000L)),
        ),
        AdminApplicationUi(
            id = "app-2",
            applicantProfileId = "owner-2",
            brandName = "Kaffa Roastery",
            legalName = "Kaffa Ltd",
            contactEmail = "partner2@example.invalid",
            contactPhone = "+251900000002",
            tinNumber = "0056789",
            status = RestaurantApplicationStatus.SUBMITTED,
            submittedAt = "2026-11-10",
            branchCount = 1,
            draftMenuItemsCount = 5,
        ),
        AdminApplicationUi(
            id = "app-self",
            applicantProfileId = "real-admin-1",
            brandName = "Conflict Bistro",
            legalName = "Conflict LLC",
            contactEmail = "self@example.invalid",
            contactPhone = "+251900000003",
            tinNumber = "0099999",
            status = RestaurantApplicationStatus.SUBMITTED,
            submittedAt = "2026-11-10",
            branchCount = 1,
            draftMenuItemsCount = 3,
        ),
    )

    override suspend fun verifyPlatformAdminAccess(accountUid: String): Result<Boolean> {
        calls.incrementAndGet()
        return Result.success(accountUid == authorizedAdminUid)
    }

    override suspend fun loadApplications(): Result<List<AdminApplicationUi>> {
        calls.incrementAndGet()
        return Result.success(applications.toList())
    }

    override suspend fun loadRestaurantsAndBranches(): Result<List<AdminRestaurantOverviewUi>> {
        calls.incrementAndGet()
        return Result.success(restaurants.toList())
    }

    override suspend fun loadAuditLogs(): Result<List<RestaurantAuditLogDto>> {
        calls.incrementAndGet()
        return Result.success(
            listOf(RestaurantAuditLogDto("log-1", "rest-active", authorizedAdminUid, "admin.viewed", "restaurant", "rest-active", null, "2026-11-10")),
        )
    }

    override suspend fun startReview(applicationId: String, notes: String?): Result<AdminApplicationStatusResponse> {
        calls.incrementAndGet()
        return Result.success(AdminApplicationStatusResponse(true, applicationId, "UNDER_REVIEW"))
    }

    override suspend fun inspectDocument(applicationId: String, documentId: String): Result<String?> {
        calls.incrementAndGet()
        return Result.success("https://signed-mock.invalid/private-doc/$documentId")
    }

    override suspend fun approveApplication(applicationId: String, slug: String, notes: String?): Result<AdminApproveResponse> {
        calls.incrementAndGet()
        restaurants.add(
            AdminRestaurantOverviewUi(
                id = "rest-new-1",
                name = "Habesha Hearth",
                slug = slug,
                status = RestaurantOperationalStatus.PENDING_SETUP,
                isLiveOrderingEnabled = false,
            ),
        )
        return Result.success(
            AdminApproveResponse(
                ok = true,
                result = AdminApproveResultDto(applicationId, "rest-new-1", "APPROVED", 8),
            ),
        )
    }

    override suspend fun rejectApplication(applicationId: String, notes: String): Result<AdminApplicationStatusResponse> {
        calls.incrementAndGet()
        return Result.success(AdminApplicationStatusResponse(true, applicationId, "REJECTED"))
    }

    override suspend fun requestChanges(applicationId: String, notes: String): Result<AdminApplicationStatusResponse> {
        calls.incrementAndGet()
        return Result.success(AdminApplicationStatusResponse(true, applicationId, "CHANGES_REQUESTED"))
    }

    override suspend fun suspendRestaurant(restaurantId: String, reason: String): Result<AdminRestaurantStatusResponse> {
        calls.incrementAndGet()
        return Result.success(AdminRestaurantStatusResponse(true, restaurantId, "suspended"))
    }

    override suspend fun reactivateRestaurant(restaurantId: String, reason: String): Result<AdminRestaurantStatusResponse> {
        calls.incrementAndGet()
        return Result.success(AdminRestaurantStatusResponse(true, restaurantId, "active"))
    }
}
