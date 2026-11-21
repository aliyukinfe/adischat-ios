package com.adischat.app.feature.restaurants.center

import com.adischat.app.BuildConfig
import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.data.ActivateRestaurantResponse
import com.adischat.app.feature.restaurants.data.ConfirmDocumentRequest
import com.adischat.app.feature.restaurants.data.CreateBranchRequest
import com.adischat.app.feature.restaurants.data.DiningTableWithStatusDto
import com.adischat.app.feature.restaurants.data.MenuCategoryDto
import com.adischat.app.feature.restaurants.data.MenuItemDto
import com.adischat.app.feature.restaurants.data.OpenSessionResponse
import com.adischat.app.feature.restaurants.data.RestaurantSummaryDto
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RotateQrResponse
import com.adischat.app.feature.restaurants.data.RotateSessionCodeResponse
import com.adischat.app.feature.restaurants.data.SaveApplicationDraftRequest
import com.adischat.app.feature.restaurants.data.SetBranchAvailabilityRequest
import com.adischat.app.feature.restaurants.data.StaffInviteRequest
import com.adischat.app.feature.restaurants.data.StaffInviteResponse
import com.adischat.app.feature.restaurants.data.StaffInvitationDto
import com.adischat.app.feature.restaurants.data.StaffMemberSummaryDto
import com.adischat.app.feature.restaurants.data.TransitionOrderResultDto
import com.adischat.app.feature.restaurants.data.UpdateBranchRequest
import com.adischat.app.feature.restaurants.data.UpdateRestaurantSettingsRequest
import com.adischat.app.feature.restaurants.data.UpsertCategoryRequest
import com.adischat.app.feature.restaurants.data.UpsertMenuItemRequest
import com.adischat.app.feature.restaurants.data.UpsertTableRequest
import com.adischat.app.feature.restaurants.presentation.PaymentStatusUi
import com.adischat.app.feature.restaurants.presentation.RestaurantOrderStatus
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

class RestaurantCenterCoordinatorTest {

    @Test
    fun `native tabs and owner-only menu permissions follow backend roles`() = runBlocking {
        assertTrue(RestaurantRolePermissions.canManageMenu(RestaurantStaffRole.RESTAURANT_OWNER))
        assertFalse(RestaurantRolePermissions.canManageMenu(RestaurantStaffRole.RESTAURANT_MANAGER))
        assertFalse(RestaurantRolePermissions.canEditSettings(RestaurantStaffRole.RESTAURANT_MANAGER))
        assertTrue(RestaurantRolePermissions.canEditBranchSettings(RestaurantStaffRole.RESTAURANT_MANAGER))
        assertEquals(emptyList<RestaurantCenterTab>(), RestaurantRolePermissions.visibleTabs(null))
        assertFalse(RestaurantCenterTab.STAFF in RestaurantRolePermissions.visibleTabs(RestaurantStaffRole.WAITER))
        assertFalse(RestaurantCenterTab.MENU in RestaurantRolePermissions.visibleTabs(RestaurantStaffRole.CASHIER))
        assertTrue(RestaurantCenterTab.MENU in RestaurantRolePermissions.visibleTabs(RestaurantStaffRole.KITCHEN_STAFF))
        val mixedBranchRoles = CenterRestaurantUi(
            id = "r-1", slug = "r-1", name = "Test", description = "",
            status = RestaurantOperationalStatus.ACTIVE, isLiveOrderingEnabled = false,
            currentUserRole = RestaurantStaffRole.RESTAURANT_MANAGER,
            branchRoles = mapOf("b-1" to RestaurantStaffRole.RESTAURANT_MANAGER,
                "b-2" to RestaurantStaffRole.WAITER),
        )
        assertEquals(RestaurantStaffRole.WAITER, mixedBranchRoles.roleForBranch("b-2"))
        assertNull(mixedBranchRoles.roleForBranch("b-other"))

        val fake = FakeCenterDataSource().apply { customRoleForHearth = RestaurantStaffRole.RESTAURANT_MANAGER }
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledCenterEnv() },
            accountUidProvider = { "manager-1" },
        )
        coordinator.loadCenter()
        delay(40)
        coordinator.selectTab(RestaurantCenterTab.SETTINGS)
        assertEquals(RestaurantCenterTab.OVERVIEW, coordinator.state.value.selectedTab)
        coordinator.upsertMenuCategory("Unauthorized", 0)
        assertNotNull(coordinator.state.value.errorBanner)
    }

    @Test
    fun `workspace authorization failure clears restaurant scoped data`() = runBlocking {
        val fake = FakeCenterDataSource()
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledCenterEnv() },
            accountUidProvider = { "owner-1" },
        )
        coordinator.loadCenter()
        delay(40)
        assertTrue(coordinator.state.value.ownedRestaurants.isNotEmpty())
        fake.workspaceFailure = com.adischat.app.feature.restaurants.data.RestaurantsFailure.Forbidden(
            "membership_revoked", "Restaurant access revoked.",
        )
        coordinator.loadCenter()
        delay(40)
        assertTrue(coordinator.state.value.loadState is RestaurantsLoadState.Error)
        assertTrue(coordinator.state.value.ownedRestaurants.isEmpty())
        assertTrue(coordinator.state.value.orders.isEmpty())
    }

    @Test
    fun `disabled feature flag blocks all center entry points and mutations`() = runBlocking {
        assertFalse(FeatureFlags.RESTAURANT_CENTER_ENABLED)
        assertFalse(BuildConfig.RESTAURANTS_IDENTITY_BRIDGE_ENABLED)

        val fake = FakeCenterDataSource()
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { RestaurantsEnvironment(isRestaurantCenterEnabled = false) },
            accountUidProvider = { "owner-1" },
        )

        coordinator.loadCenter()
        coordinator.submitApplication("app-1")
        coordinator.transitionOrderStatus("ord-1", RestaurantOrderStatus.ACCEPTED)
        delay(30)

        assertEquals(0, fake.calls.get())
        assertEquals(RestaurantsLoadState.Disabled, coordinator.state.value.loadState)
    }

    @Test
    fun `application transitions and separate approval vs operational activation work safely`() = runBlocking {
        val fake = FakeCenterDataSource()
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledCenterEnv() },
            accountUidProvider = { "owner-1" },
        )

        coordinator.loadCenter()
        delay(40)

        // Submit draft -> SUBMITTED
        coordinator.submitApplication("app-draft")
        delay(40)
        val afterSubmit = coordinator.state.value.applications.first { it.id == "app-draft" }
        assertEquals(RestaurantApplicationStatus.SUBMITTED, afterSubmit.status)

        // Re-submitting already submitted app is blocked cleanly
        coordinator.submitApplication("app-draft")
        assertNotNull(coordinator.state.value.errorBanner)

        // Restaurant starts PENDING_SETUP; owner cannot publish live until Admin/setup sets status ACTIVE
        assertEquals(RestaurantOperationalStatus.PENDING_SETUP, fake.restPending.status)
        coordinator.selectRestaurant("r-pending")
        delay(40)
        coordinator.togglePublishLive(true)
        assertNotNull(coordinator.state.value.errorBanner)

        // Restaurant ACTIVE can be toggled live by Owner
        coordinator.selectRestaurant("r-hearth")
        delay(40)
        coordinator.togglePublishLive(true)
        delay(40)
        assertTrue(coordinator.state.value.selectedRestaurant?.isLiveOrderingEnabled == true)
    }

    @Test
    fun `cross-restaurant isolation clears prior tenant branches and orders on restaurant switch`() = runBlocking {
        val fake = FakeCenterDataSource()
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledCenterEnv() },
            accountUidProvider = { "owner-1" },
        )

        coordinator.loadCenter()
        delay(40)

        coordinator.selectRestaurant("r-hearth")
        delay(40)
        assertTrue(coordinator.state.value.branches.all { it.restaurantId == "r-hearth" })
        assertTrue(coordinator.state.value.orders.all { it.restaurantId == "r-hearth" })
        assertEquals("o-hearth-1", coordinator.state.value.orders.first().orderId)

        coordinator.selectRestaurant("r-second")
        delay(40)
        assertTrue(coordinator.state.value.branches.all { it.restaurantId == "r-second" })
        assertTrue(coordinator.state.value.orders.all { it.restaurantId == "r-second" })
        assertFalse(coordinator.state.value.orders.any { it.orderId == "o-hearth-1" })
    }

    @Test
    fun `owner vs kitchen vs waiter vs manager permissions and staff revocation enforce isolation`() = runBlocking {
        // Role RBAC checks
        assertTrue(RestaurantRolePermissions.canInviteStaffRole(RestaurantStaffRole.RESTAURANT_OWNER, RestaurantStaffRole.RESTAURANT_MANAGER))
        assertFalse(RestaurantRolePermissions.canInviteStaffRole(RestaurantStaffRole.RESTAURANT_OWNER, RestaurantStaffRole.RESTAURANT_OWNER))
        assertFalse(RestaurantRolePermissions.canInviteStaffRole(RestaurantStaffRole.RESTAURANT_MANAGER, RestaurantStaffRole.RESTAURANT_MANAGER))
        assertTrue(RestaurantRolePermissions.canInviteStaffRole(RestaurantStaffRole.RESTAURANT_MANAGER, RestaurantStaffRole.WAITER))

        // Manager cannot publish live or revoke another manager
        val fake = FakeCenterDataSource()
        fake.customRoleForHearth = RestaurantStaffRole.RESTAURANT_MANAGER
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledCenterEnv() },
            accountUidProvider = { "manager-1" },
        )

        coordinator.loadCenter()
        delay(40)

        coordinator.togglePublishLive(true)
        assertNotNull(coordinator.state.value.errorBanner)

        // Manager trying to invite another Manager is rejected before network call
        val callsBefore = fake.calls.get()
        coordinator.inviteStaffMember("friend-1", RestaurantStaffRole.RESTAURANT_MANAGER)
        assertEquals(callsBefore, fake.calls.get())
        assertNotNull(coordinator.state.value.errorBanner)

        // Owner revoking active Waiter succeeds; Owner revoking self-session clears local workspace
        fake.customRoleForHearth = RestaurantStaffRole.RESTAURANT_OWNER
        coordinator.loadCenter()
        delay(40)
        coordinator.revokeStaffMember("sm-waiter")
        delay(40)
        assertEquals(
            "revoked",
            coordinator.state.value.staffMembers.first { it.staffMemberId == "sm-waiter" }.status,
        )

        // Account switch clears all state cleanly
        coordinator.onAccountChanged("uid-other-user")
        assertTrue(coordinator.state.value.ownedRestaurants.isEmpty())
        assertTrue(coordinator.state.value.orders.isEmpty())
        assertNull(coordinator.state.value.selectedRestaurantId)
        coordinator.onAccountChanged(null)
        assertNull(coordinator.state.value.activeAccountUid)
        assertTrue(coordinator.state.value.applications.isEmpty())
    }

    @Test
    fun `kitchen order board transitions enforce progression, require decline reason, and preserve unpaid indicator`() = runBlocking {
        val fake = FakeCenterDataSource()
        fake.customRoleForHearth = RestaurantStaffRole.KITCHEN_STAFF
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledCenterEnv() },
            accountUidProvider = { "chef-1" },
        )

        coordinator.loadCenter()
        delay(40)

        // Verify unpaid indicator present without payment processing controls
        val initialTicket = coordinator.state.value.orders.first { it.orderId == "o-hearth-1" }
        assertTrue(initialTicket.isUnpaid)
        assertEquals(PaymentStatusUi.UNPAID, initialTicket.paymentStatus)

        // Reject without reason blocked locally
        coordinator.transitionOrderStatus("o-hearth-1", RestaurantOrderStatus.REJECTED, "  ")
        assertNotNull(coordinator.state.value.errorBanner)

        // Valid progression by kitchen staff: SUBMITTED -> ACCEPTED -> PREPARING -> READY
        coordinator.transitionOrderStatus("o-hearth-1", RestaurantOrderStatus.ACCEPTED)
        delay(30)
        coordinator.transitionOrderStatus("o-hearth-1", RestaurantOrderStatus.PREPARING)
        delay(30)
        coordinator.transitionOrderStatus("o-hearth-1", RestaurantOrderStatus.READY)
        delay(30)
        assertEquals(
            RestaurantOrderStatus.READY,
            coordinator.state.value.orders.first { it.orderId == "o-hearth-1" }.status,
        )

        // Kitchen Staff CANNOT mark READY -> SERVED (only Waiter/Manager/Owner can!)
        coordinator.transitionOrderStatus("o-hearth-1", RestaurantOrderStatus.SERVED)
        assertNotNull(coordinator.state.value.errorBanner)
        assertEquals(
            RestaurantOrderStatus.READY,
            coordinator.state.value.orders.first { it.orderId == "o-hearth-1" }.status,
        )

        // Served & unpaid shows up in UNPAID_SERVED filter independently of payment processing
        fake.orders = fake.orders.map {
            if (it.orderId == "o-hearth-1") it.copy(status = RestaurantOrderStatus.SERVED, paymentStatus = PaymentStatusUi.UNPAID)
            else it
        }
        fake.customRoleForHearth = RestaurantStaffRole.RESTAURANT_OWNER
        coordinator.loadCenter()
        delay(40)
        coordinator.selectOrderFilter(OrderBoardFilter.UNPAID_SERVED)
        assertEquals(listOf("o-hearth-1"), coordinator.state.value.filteredOrders.map { it.orderId })
    }

    @Test
    fun `staff invites send invited_adischat_user_id by default, preserve phone & profile UUID, and block invalid or self invites`() = runBlocking {
        val fake = FakeCenterDataSource()
        fake.customRoleForHearth = RestaurantStaffRole.RESTAURANT_MANAGER
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledCenterEnv() },
            accountUidProvider = { "fb-manager-1" },
        )

        coordinator.loadCenter()
        delay(40)

        // 1. AdisChat User ID sends invitedAdischatUserId (no verified phone required)
        coordinator.inviteStaffMember("fb-email-waiter-1", RestaurantStaffRole.WAITER)
        delay(40)
        assertEquals("fb-email-waiter-1", fake.lastInviteRequest?.invitedAdischatUserId)
        assertEquals("fb-email-waiter-1", fake.lastInviteRequest?.invitedIdentifier)
        assertNull(fake.lastInviteRequest?.invitedPhone)
        assertNull(fake.lastInviteRequest?.invitedProfileId)

        // 2. E.164 phone compatibility preserved
        coordinator.inviteStaffMember("+251 911-223-344", RestaurantStaffRole.CASHIER)
        delay(40)
        assertEquals("+251911223344", fake.lastInviteRequest?.invitedPhone)
        assertEquals("+251911223344", fake.lastInviteRequest?.invitedIdentifier)
        assertNull(fake.lastInviteRequest?.invitedAdischatUserId)

        // 3. Profile UUID compatibility preserved
        val profileUuid = "33330000-0000-4000-8000-000000000003"
        coordinator.inviteStaffMember(profileUuid, RestaurantStaffRole.KITCHEN_STAFF)
        delay(40)
        assertEquals(profileUuid, fake.lastInviteRequest?.invitedProfileId)
        assertEquals(profileUuid, fake.lastInviteRequest?.invitedIdentifier)
        assertNull(fake.lastInviteRequest?.invitedAdischatUserId)

        // 4. Reject self-invite, emails, malformed/local phones, and whitespace without calling network
        val callsBefore = fake.calls.get()
        for (bad in listOf("fb-manager-1", "waiter@example.com", "0911223344", "+123", "fb bad id")) {
            coordinator.inviteStaffMember(bad, RestaurantStaffRole.WAITER)
            assertNotNull("Expected validation error for $bad", coordinator.state.value.errorBanner)
        }
        assertEquals(callsBefore, fake.calls.get())
    }

    @Test
    fun `submitDraftApplication uploads business license and transitions application to SUBMITTED`() = runTest {
        val fake = FakeCenterDataSource()
        val coordinator = RestaurantCenterCoordinator(
            dataSource = fake,
            environmentProvider = ::enabledCenterEnv,
            accountUidProvider = { "owner-1" },
            scope = this,
        )
        coordinator.loadCenter()
        delay(40)

        var submittedCallbackInvoked = false
        val licenseSource = RestaurantsBinaryUploadSource(
            fileName = "trade_license.pdf",
            mimeType = "application/pdf",
            byteSize = 100_000L,
            openStream = { java.io.ByteArrayInputStream(ByteArray(10)) },
        )
        val draftReq = SaveApplicationDraftRequest(
            applicationId = "app-draft",
            clientDraftKey = "draft-owner-1",
            legalName = "Hearth PLC",
            brandName = "Habesha Hearth",
            businessType = "restaurant",
            contactPhone = "+251900000001",
            contactEmail = "partner@example.invalid",
            tinNumber = "0041234",
            registrationNumber = "0041234",
            operatingHoursSummary = "08:00-22:00",
            isDineInAvailable = true,
        )

        coordinator.submitDraftApplication(
            draft = draftReq,
            pendingLicenseSource = licenseSource,
            onSuccess = { submittedCallbackInvoked = true },
        )
        delay(40)

        assertTrue(submittedCallbackInvoked)
        assertNull(coordinator.state.value.errorBanner)
        assertEquals("Application submitted for Admin review.", coordinator.state.value.actionFeedback)
        val app = coordinator.state.value.applications.first { it.id == "app-draft" }
        assertEquals(RestaurantApplicationStatus.SUBMITTED, app.status)
        assertTrue(app.documents.any { it.docType == "business_license" })
    }

    @Test
    fun `owner setup checklist differentiates activation readiness from live ordering readiness`() = runBlocking {
        val fake = FakeCenterDataSource()
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledCenterEnv() },
            accountUidProvider = { "owner-1" },
        )
        coordinator.loadCenter()
        delay(40)
        coordinator.selectRestaurant("r-pending")
        delay(40)

        val state = coordinator.state.value
        assertEquals(RestaurantOperationalStatus.PENDING_SETUP, state.selectedRestaurant?.status)

        // 1. Activation readiness: 0 menu items -> not ready
        val actReadiness = state.activationReadiness
        assertFalse(actReadiness.isReadyToActivate)
        assertTrue(actReadiness.pendingRequirements.contains("At least one menu item"))
        // Tables, QR codes, and staff are NOT listed in activation prerequisites
        assertFalse(actReadiness.pendingRequirements.any { it.contains("table", ignoreCase = true) })
        assertFalse(actReadiness.pendingRequirements.any { it.contains("QR", ignoreCase = true) })
        assertFalse(actReadiness.pendingRequirements.any { it.contains("staff", ignoreCase = true) })

        // 2. Live ordering readiness: requires ACTIVE status, dish, tables, QR
        val liveReadiness = state.liveOrderingReadiness
        assertFalse(liveReadiness.isReadyForLiveOrdering)
        assertTrue(liveReadiness.pendingRequirements.contains("Activate restaurant first"))
    }

    @Test
    fun `owner can activate restaurant once readiness prerequisites are satisfied`() = runBlocking {
        val fake = FakeCenterDataSource()
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledCenterEnv() },
            accountUidProvider = { "owner-1" },
        )
        coordinator.loadCenter()
        delay(40)
        coordinator.selectRestaurant("r-pending")
        delay(40)

        // Activate restaurant
        coordinator.activateRestaurant()
        delay(40)

        assertTrue(fake.activatedRestaurants.contains("r-pending"))
        assertEquals(RestaurantOperationalStatus.ACTIVE, coordinator.state.value.selectedRestaurant?.status)
        assertFalse(coordinator.state.value.selectedRestaurant?.isLiveOrderingEnabled == true)
        assertEquals("Restaurant activated successfully.", coordinator.state.value.actionFeedback)
    }

    @Test
    fun `owner can upsert categories, items, and atomic category plus item`() = runBlocking {
        val fake = FakeCenterDataSource()
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledCenterEnv() },
            accountUidProvider = { "owner-1" },
        )
        coordinator.loadCenter()
        delay(40)
        coordinator.selectRestaurant("r-hearth")
        delay(40)

        // Upsert category
        coordinator.upsertMenuCategory("Appetizers", 1)
        delay(40)
        assertNotNull(fake.lastUpsertCategoryRequest)
        assertEquals("Appetizers", fake.lastUpsertCategoryRequest?.name)
        assertTrue(coordinator.state.value.categories.any { it.name == "Appetizers" })

        // Upsert item
        val catId = coordinator.state.value.categories.first().id
        coordinator.upsertMenuItem(catId, "Sambusa", "Crispy lentil pastry", 4500L)
        delay(40)
        assertNotNull(fake.lastUpsertMenuItemRequest)
        assertEquals("Sambusa", fake.lastUpsertMenuItemRequest?.name)
        assertEquals(4500L, fake.lastUpsertMenuItemRequest?.basePriceMinor)

        // Atomic createCategoryAndItem
        coordinator.createCategoryAndItem("Desserts", "Baklava", "Sweet honey pastry", 8000L)
        delay(40)
        assertTrue(coordinator.state.value.categories.any { it.name == "Desserts" })
        assertEquals("Created category \"Desserts\" and dish \"Baklava\".", coordinator.state.value.actionFeedback)
    }

    @Test
    fun `owner can upsert dining tables, rotate QR, and revoke QR`() = runBlocking {
        val fake = FakeCenterDataSource()
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledCenterEnv() },
            accountUidProvider = { "owner-1" },
        )
        coordinator.loadCenter()
        delay(40)
        coordinator.selectRestaurant("r-hearth")
        delay(40)

        // Upsert Table
        coordinator.upsertTable("T-01", "Window Table 1", "Main Dining", 4)
        delay(40)
        assertNotNull(fake.lastUpsertTableRequest)
        assertEquals("T-01", fake.lastUpsertTableRequest?.tableCode)
        val createdTable = coordinator.state.value.tables.first { it.tableCode == "T-01" }

        // Rotate QR
        coordinator.rotateTableQr(createdTable.id)
        delay(40)
        val rotatedTable = coordinator.state.value.tables.first { it.id == createdTable.id }
        assertEquals(2, rotatedTable.activeQrVersion)
        assertEquals("active", rotatedTable.activeQrStatus)

        // Revoke QR
        coordinator.revokeTableQr(createdTable.id)
        delay(40)
        assertEquals(createdTable.id, fake.lastRevokedTableId)
        val revokedTable = coordinator.state.value.tables.first { it.id == createdTable.id }
        assertNull(revokedTable.activeQrVersion)
        assertEquals("revoked", revokedTable.activeQrStatus)
    }

    @Test
    fun `publish live ordering is rejected if restaurant is not active or caller is not owner`() = runBlocking {
        val fake = FakeCenterDataSource()
        val coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = fake,
            environmentProvider = { enabledCenterEnv() },
            accountUidProvider = { "owner-1" },
        )
        coordinator.loadCenter()
        delay(40)
        coordinator.selectRestaurant("r-pending")
        delay(40)

        // Attempting to publish live while PENDING_SETUP is blocked
        coordinator.togglePublishLive(true)
        delay(40)
        assertNotNull(coordinator.state.value.errorBanner)
        assertTrue(coordinator.state.value.errorBanner?.contains("Active") == true)
    }

    private fun enabledCenterEnv() = RestaurantsEnvironment(
        supabaseUrl = "https://test.supabase.co",
        supabasePublishableKey = "pk_test",
        identityBridgeUrl = "https://bridge.example",
        isIdentityBridgeEnabled = true,
        isRestaurantCenterEnabled = true,
    )
}

private class FakeCenterDataSource : RestaurantCenterDataSource {
    var workspaceFailure: Throwable? = null
    val calls = AtomicInteger(0)
    var customRoleForHearth = RestaurantStaffRole.RESTAURANT_OWNER
    var lastInviteRequest: StaffInviteRequest? = null

    var applications = listOf(
        CenterApplicationUi(
            id = "app-draft",
            applicantProfileId = "owner-1",
            legalName = "Hearth PLC",
            brandName = "Habesha Hearth",
            description = "Authentic platters",
            businessType = "restaurant",
            contactPhone = "+251900000001",
            contactEmail = "partner@example.invalid",
            tinNumber = "0041234",
            status = RestaurantApplicationStatus.DRAFT,
            branchDraftCount = 1,
        ),
    )

    val restPending = CenterRestaurantUi(
        id = "r-pending",
        slug = "pending-spot",
        name = "Pending Spot",
        description = "Not activated yet",
        status = RestaurantOperationalStatus.PENDING_SETUP,
        isLiveOrderingEnabled = false,
        currentUserRole = RestaurantStaffRole.RESTAURANT_OWNER,
    )

    var orders = listOf(
        CenterOrderTicketUi(
            orderId = "o-hearth-1",
            orderNumber = "101",
            restaurantId = "r-hearth",
            branchId = "b-hearth-1",
            tableLabel = "Table 4",
            itemCountLabel = "2x Shekla Tibs",
            status = RestaurantOrderStatus.ORDER_SUBMITTED,
            paymentStatus = PaymentStatusUi.UNPAID,
            totalMinor = 136000L,
        ),
        CenterOrderTicketUi(
            orderId = "o-second-1",
            orderNumber = "205",
            restaurantId = "r-second",
            branchId = "b-second-1",
            tableLabel = "Table 2",
            itemCountLabel = "1x Pour Over",
            status = RestaurantOrderStatus.ORDER_SUBMITTED,
            paymentStatus = PaymentStatusUi.UNPAID,
            totalMinor = 16000L,
        ),
    )

    override suspend fun loadMyApplications(): Result<List<CenterApplicationUi>> {
        calls.incrementAndGet()
        return Result.success(applications)
    }

    override suspend fun saveApplicationDraft(request: SaveApplicationDraftRequest): Result<CenterApplicationUi> {
        calls.incrementAndGet()
        return Result.success(applications.first())
    }

    override suspend fun attachApplicationDocument(request: ConfirmDocumentRequest): Result<CenterApplicationDocUi> {
        calls.incrementAndGet()
        return Result.success(CenterApplicationDocUi("d1", request.docType, request.fileName, request.mimeType, request.byteSize))
    }

    override suspend fun uploadAndConfirmApplicationDocument(
        applicationId: String,
        docType: String,
        source: RestaurantsBinaryUploadSource,
    ): Result<CenterApplicationDocUi> {
        calls.incrementAndGet()
        val doc = CenterApplicationDocUi("d-license", docType, source.fileName, source.mimeType, source.byteSize)
        applications = applications.map {
            if (it.id == applicationId) it.copy(documents = it.documents + doc) else it
        }
        return Result.success(doc)
    }

    override suspend fun submitApplication(
        applicationId: String,
        idempotencyKey: String?,
    ): Result<CenterApplicationUi> {
        calls.incrementAndGet()
        applications = applications.map {
            if (it.id == applicationId) it.copy(status = RestaurantApplicationStatus.SUBMITTED) else it
        }
        return Result.success(applications.first { it.id == applicationId })
    }

    override suspend fun loadAccessibleRestaurants(): Result<List<CenterRestaurantUi>> {
        calls.incrementAndGet()
        workspaceFailure?.let { return Result.failure(it) }
        return Result.success(
            listOf(
                CenterRestaurantUi(
                    id = "r-hearth",
                    slug = "habesha-hearth",
                    name = "Habesha Hearth",
                    description = "Grill",
                    status = RestaurantOperationalStatus.ACTIVE,
                    isLiveOrderingEnabled = false,
                    currentUserRole = customRoleForHearth,
                ),
                CenterRestaurantUi(
                    id = "r-second",
                    slug = "kaffa-bistro",
                    name = "Kaffa Bistro",
                    description = "Cafe",
                    status = RestaurantOperationalStatus.ACTIVE,
                    isLiveOrderingEnabled = true,
                    currentUserRole = RestaurantStaffRole.RESTAURANT_OWNER,
                ),
                restPending,
            ),
        )
    }

    override suspend fun loadBranches(restaurantId: String): Result<List<CenterBranchUi>> {
        calls.incrementAndGet()
        val id = if (restaurantId == "r-second") "b-second-1" else "b-hearth-1"
        return Result.success(
            listOf(CenterBranchUi(id, restaurantId, "MAIN", "Main Branch", "Addis", "Addis Ababa", true, false, true, "active")),
        )
    }

    override suspend fun createBranch(request: CreateBranchRequest) = error("unused")
    override suspend fun updateBranch(request: UpdateBranchRequest) = error("unused")
    override suspend fun updateRestaurantSettings(request: UpdateRestaurantSettingsRequest): Result<RestaurantSummaryDto> {
        calls.incrementAndGet()
        return Result.success(RestaurantSummaryDto(id = request.restaurantId, name = "Habesha Hearth", description = request.description.orEmpty(), status = "active"))
    }

    override suspend fun publishLive(restaurantId: String, enable: Boolean): Result<Boolean> {
        calls.incrementAndGet()
        return Result.success(enable)
    }

    var lastUpsertCategoryRequest: UpsertCategoryRequest? = null
    var lastUpsertMenuItemRequest: UpsertMenuItemRequest? = null
    var lastUpsertTableRequest: UpsertTableRequest? = null
    var lastRevokedTableId: String? = null
    var activatedRestaurants = mutableListOf<String>()

    override suspend fun activateRestaurant(restaurantId: String): Result<ActivateRestaurantResponse> {
        calls.incrementAndGet()
        activatedRestaurants.add(restaurantId)
        return Result.success(ActivateRestaurantResponse(ok = true, status = "active", isLiveOrderingEnabled = false))
    }

    override suspend fun loadMenuCategories(restaurantId: String): Result<List<MenuCategoryDto>> = Result.success(emptyList())
    override suspend fun loadMenuItemsForBranch(restaurantId: String, branchId: String): Result<List<CenterMenuItemUi>> = Result.success(emptyList())

    override suspend fun upsertMenuCategory(request: UpsertCategoryRequest): Result<MenuCategoryDto> {
        calls.incrementAndGet()
        lastUpsertCategoryRequest = request
        val id = request.id ?: "cat-${System.currentTimeMillis()}"
        return Result.success(MenuCategoryDto(id = id, restaurantId = request.restaurantId, name = request.name, sortOrder = request.sortOrder ?: 0))
    }

    override suspend fun upsertMenuItem(request: UpsertMenuItemRequest): Result<MenuItemDto> {
        calls.incrementAndGet()
        lastUpsertMenuItemRequest = request
        val id = request.id ?: "item-${System.currentTimeMillis()}"
        return Result.success(
            MenuItemDto(
                id = id,
                restaurantId = request.restaurantId,
                categoryId = request.categoryId,
                name = request.name,
                description = request.description.orEmpty(),
                basePriceMinor = request.basePriceMinor,
                isActive = request.isActive ?: true,
            )
        )
    }

    override suspend fun setBranchAvailability(request: SetBranchAvailabilityRequest) = Result.success(Unit)
    override suspend fun loadTables(restaurantId: String, branchId: String): Result<List<DiningTableWithStatusDto>> = Result.success(emptyList())

    override suspend fun upsertTable(request: UpsertTableRequest): Result<CenterTableUi> {
        calls.incrementAndGet()
        lastUpsertTableRequest = request
        val id = request.id ?: "table-${System.currentTimeMillis()}"
        return Result.success(
            CenterTableUi(
                id = id,
                restaurantId = request.restaurantId,
                branchId = request.branchId,
                tableCode = request.tableCode,
                label = request.label,
                zone = request.zone.orEmpty(),
                capacity = request.capacity ?: 4,
                isActive = request.isActive ?: true,
                activeQrVersion = null,
                activeQrStatus = null,
                openSessionId = null,
                openSessionExpiresAt = null,
                hasSessionCode = false,
            )
        )
    }

    override suspend fun rotateTableQr(restaurantId: String, branchId: String, tableId: String): Result<RotateQrResponse> {
        calls.incrementAndGet()
        return Result.success(RotateQrResponse(ok = true, qrTokenId = "qr-$tableId", qrVersion = 2, qrUrl = "https://adischat.com/d/qr-$tableId"))
    }

    override suspend fun revokeTableQr(restaurantId: String, branchId: String, tableId: String): Result<Unit> {
        calls.incrementAndGet()
        lastRevokedTableId = tableId
        return Result.success(Unit)
    }
    override suspend fun openDiningSession(restaurantId: String, branchId: String, tableId: String): Result<OpenSessionResponse> = error("unused")
    override suspend fun rotateSessionCode(restaurantId: String, branchId: String, diningSessionId: String): Result<RotateSessionCodeResponse> = error("unused")
    override suspend fun closeDiningSession(restaurantId: String, branchId: String, diningSessionId: String) = Result.success(Unit)

    override suspend fun loadStaffMembers(restaurantId: String, branchId: String?): Result<List<StaffMemberSummaryDto>> {
        calls.incrementAndGet()
        return Result.success(
            listOf(
                StaffMemberSummaryDto("sm-owner", restaurantId, null, "owner-1", "Owner", null, "RESTAURANT_OWNER", "active"),
                StaffMemberSummaryDto("sm-waiter", restaurantId, branchId, "waiter-1", "Dawit", null, "WAITER", "active"),
            ),
        )
    }

    override suspend fun loadStaffInvitations(restaurantId: String, branchId: String?): Result<List<StaffInvitationDto>> =
        Result.success(emptyList())

    override suspend fun inviteStaff(request: StaffInviteRequest): Result<StaffInviteResponse> {
        calls.incrementAndGet()
        lastInviteRequest = request
        return Result.success(StaffInviteResponse(ok = true, invitationId = "inv-1"))
    }

    override suspend fun revokeStaffMember(staffMemberId: String): Result<Unit> {
        calls.incrementAndGet()
        return Result.success(Unit)
    }

    override suspend fun revokeStaffInvitation(invitationId: String): Result<Unit> = Result.success(Unit)

    override suspend fun loadBranchOrders(restaurantId: String, branchId: String): Result<List<CenterOrderTicketUi>> {
        calls.incrementAndGet()
        return Result.success(orders.filter { it.restaurantId == restaurantId && it.branchId == branchId })
    }

    override suspend fun transitionOrderStatus(orderId: String, targetStatus: String, reason: String?): Result<TransitionOrderResultDto> {
        calls.incrementAndGet()
        val prev = orders.first { it.orderId == orderId }
        val next = RestaurantOrderStatus.fromWireValue(targetStatus)
        orders = orders.map {
            if (it.orderId == orderId) it.copy(status = next, terminalReason = reason) else it
        }
        return Result.success(TransitionOrderResultDto(orderId, prev.status.wireValue, next.wireValue))
    }
}
