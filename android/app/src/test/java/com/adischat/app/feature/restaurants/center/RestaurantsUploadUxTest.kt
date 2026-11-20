package com.adischat.app.feature.restaurants.center

import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.data.ConfirmDocumentRequest
import com.adischat.app.feature.restaurants.data.CreateBranchRequest
import com.adischat.app.feature.restaurants.data.DiningTableWithStatusDto
import com.adischat.app.feature.restaurants.data.MenuCategoryDto
import com.adischat.app.feature.restaurants.data.MenuItemDto
import com.adischat.app.feature.restaurants.data.MenuItemImageDto
import com.adischat.app.feature.restaurants.data.OpenSessionResponse
import com.adischat.app.feature.restaurants.data.RestaurantMediaSlot
import com.adischat.app.feature.restaurants.data.RestaurantSummaryDto
import com.adischat.app.feature.restaurants.data.RestaurantsBinaryUploadSource
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RestaurantsFailure
import com.adischat.app.feature.restaurants.data.RestaurantsUploadKind
import com.adischat.app.feature.restaurants.data.RotateQrResponse
import com.adischat.app.feature.restaurants.data.RotateSessionCodeResponse
import com.adischat.app.feature.restaurants.data.SaveApplicationDraftRequest
import com.adischat.app.feature.restaurants.data.SetBranchAvailabilityRequest
import com.adischat.app.feature.restaurants.data.StaffInvitationDto
import com.adischat.app.feature.restaurants.data.StaffInviteRequest
import com.adischat.app.feature.restaurants.data.StaffInviteResponse
import com.adischat.app.feature.restaurants.data.StaffMemberSummaryDto
import com.adischat.app.feature.restaurants.data.TransitionOrderResultDto
import com.adischat.app.feature.restaurants.data.UpdateBranchRequest
import com.adischat.app.feature.restaurants.data.UpdateRestaurantSettingsRequest
import com.adischat.app.feature.restaurants.data.UpsertCategoryRequest
import com.adischat.app.feature.restaurants.data.UpsertMenuItemRequest
import com.adischat.app.feature.restaurants.data.UpsertTableRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

class RestaurantsUploadUxTest {

    @Test
    fun `valid file selection resolves canonical mime types sizes and extension fallbacks`() {
        assertFalse(FeatureFlags.RESTAURANT_CENTER_ENABLED)

        val pdfDoc = RestaurantsPickerMetadataHelper.prepareSelection(
            kind = RestaurantsUploadKind.APPLICATION_DOCUMENT,
            fileName = "license.pdf",
            reportedMime = "application/pdf",
            byteSize = 2048L,
            openStream = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
        )
        assertTrue(pdfDoc.isSuccess)
        assertEquals("application/pdf", pdfDoc.getOrNull()?.mimeType)
        assertEquals("license.pdf", pdfDoc.getOrNull()?.fileName)

        // Android media pickers often report image/jpg; normalized cleanly to image/jpeg.
        val jpgAlias = RestaurantsPickerMetadataHelper.prepareSelection(
            kind = RestaurantsUploadKind.PUBLIC_MEDIA,
            fileName = "dish_photo.jpg",
            reportedMime = "image/jpg",
            byteSize = 4096L,
            openStream = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
        )
        assertTrue(jpgAlias.isSuccess)
        assertEquals("image/jpeg", jpgAlias.getOrNull()?.mimeType)

        // SAF OpenDocument may report application/octet-stream; inferred from .webp extension cleanly.
        val inferredWebp = RestaurantsPickerMetadataHelper.prepareSelection(
            kind = RestaurantsUploadKind.APPLICATION_DOCUMENT,
            fileName = "permit.webp",
            reportedMime = "application/octet-stream",
            byteSize = 1024L,
            openStream = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
        )
        assertTrue(inferredWebp.isSuccess)
        assertEquals("image/webp", inferredWebp.getOrNull()?.mimeType)
    }

    @Test
    fun `unsupported file types and oversized files are rejected cleanly without retry or network calls`() = runBlocking {
        val svgRes = RestaurantsPickerMetadataHelper.prepareSelection(
            kind = RestaurantsUploadKind.APPLICATION_DOCUMENT,
            fileName = "vector.svg",
            reportedMime = "image/svg+xml",
            byteSize = 512L,
            openStream = { ByteArrayInputStream(ByteArray(0)) },
        )
        assertTrue(svgRes.exceptionOrNull() is RestaurantsFailure.Validation)

        val pdfForPhoto = RestaurantsPickerMetadataHelper.prepareSelection(
            kind = RestaurantsUploadKind.PUBLIC_MEDIA,
            fileName = "menu.pdf",
            reportedMime = "application/pdf",
            byteSize = 512L,
            openStream = { ByteArrayInputStream(ByteArray(0)) },
        )
        assertTrue(pdfForPhoto.exceptionOrNull() is RestaurantsFailure.Validation)

        val oversizedDoc = RestaurantsPickerMetadataHelper.prepareSelection(
            kind = RestaurantsUploadKind.APPLICATION_DOCUMENT,
            fileName = "huge.pdf",
            reportedMime = "application/pdf",
            byteSize = (10L * 1024L * 1024L) + 1L,
            openStream = { ByteArrayInputStream(ByteArray(0)) },
        )
        assertTrue(oversizedDoc.exceptionOrNull() is RestaurantsFailure.Validation)

        val oversizedImage = RestaurantsPickerMetadataHelper.prepareSelection(
            kind = RestaurantsUploadKind.PUBLIC_MEDIA,
            fileName = "huge.png",
            reportedMime = "image/png",
            byteSize = (5L * 1024L * 1024L) + 1L,
            openStream = { ByteArrayInputStream(ByteArray(0)) },
        )
        assertTrue(oversizedImage.exceptionOrNull() is RestaurantsFailure.Validation)

        val missingSize = RestaurantsPickerMetadataHelper.prepareSelection(
            kind = RestaurantsUploadKind.PUBLIC_MEDIA,
            fileName = "empty.jpg",
            reportedMime = "image/jpeg",
            byteSize = 0L,
            openStream = { ByteArrayInputStream(ByteArray(0)) },
        )
        assertTrue(missingSize.exceptionOrNull() is RestaurantsFailure.Validation)

        val ds = UploadTestCenterDataSource()
        val coordinator = createCoordinator(this, ds)
        coordinator.loadCenter()
        delay(50)

        val docTarget = RestaurantsPendingPickerTarget.ApplicationDocument("app-1", "business_license", "License Document")
        coordinator.reportUploadValidationError(docTarget, svgRes.exceptionOrNull()!!.message!!)
        assertEquals(CenterUploadPhase.FAILED, coordinator.state.value.uploadState.phase)
        assertFalse(coordinator.state.value.uploadState.canRetry)
        assertEquals(0, ds.uploadCalls.get())

        // Coordinator also blocks direct oversized binary source submission cleanly.
        coordinator.handlePickedUpload(
            docTarget,
            RestaurantsBinaryUploadSource("huge.pdf", "application/pdf", (10L * 1024L * 1024L) + 1L) {
                ByteArrayInputStream(ByteArray(0))
            },
        )
        delay(20)
        assertEquals(CenterUploadPhase.FAILED, coordinator.state.value.uploadState.phase)
        assertFalse(coordinator.state.value.uploadState.canRetry)
        assertEquals(0, ds.uploadCalls.get())
    }

    @Test
    fun `picker cancellation and active upload cancellation leave state clean without confirming`() = runBlocking {
        val ds = UploadTestCenterDataSource().apply { delayMs = 80L }
        val coordinator = createCoordinator(this, ds)
        coordinator.loadCenter()
        delay(50)

        // System picker cancelled by user before file selected -> no upload, no error.
        coordinator.reportPickerCancelled()
        assertEquals(CenterUploadPhase.IDLE, coordinator.state.value.uploadState.phase)
        assertNull(coordinator.state.value.errorBanner)
        assertEquals(0, ds.uploadCalls.get())

        // Active upload cancelled in flight -> transitions to CANCELLED cleanly without confirmation.
        val target = RestaurantsPendingPickerTarget.ApplicationDocument("app-1", "business_license", "License Document")
        coordinator.handlePickedUpload(
            target,
            RestaurantsBinaryUploadSource.fromBytes("lic.pdf", "application/pdf", byteArrayOf(1, 2, 3)),
        )
        delay(20)
        assertTrue(coordinator.state.value.uploadState.isInProgress)

        coordinator.cancelActiveUpload()
        delay(90)
        assertEquals(CenterUploadPhase.CANCELLED, coordinator.state.value.uploadState.phase)
        assertFalse(coordinator.state.value.uploadState.isInProgress)
        assertNull(coordinator.state.value.errorBanner)
        assertEquals(0, ds.confirmedCount.get())
        assertTrue(coordinator.state.value.applications.first().documents.isEmpty())
    }

    @Test
    fun `successful coordinator callbacks update document app branding restaurant media and menu photos`() = runBlocking {
        val ds = UploadTestCenterDataSource()
        val coordinator = createCoordinator(this, ds)
        coordinator.loadCenter()
        delay(60)

        val app = coordinator.state.value.applications.first()
        coordinator.handlePickedUpload(
            RestaurantsPendingPickerTarget.ApplicationDocument(app.id, "business_license", "License"),
            RestaurantsBinaryUploadSource.fromBytes("license.pdf", "application/pdf", byteArrayOf(1, 2, 3)),
        )
        delay(40)
        assertEquals(CenterUploadPhase.SUCCEEDED, coordinator.state.value.uploadState.phase)
        assertEquals(1, coordinator.state.value.applications.first().documents.size)
        assertEquals("license.pdf", coordinator.state.value.applications.first().documents.first().fileName)

        coordinator.handlePickedUpload(
            RestaurantsPendingPickerTarget.ApplicationMedia(coordinator.state.value.applications.first(), RestaurantMediaSlot.LOGO),
            RestaurantsBinaryUploadSource.fromBytes("app-logo.png", "image/png", byteArrayOf(4, 5, 6)),
        )
        delay(40)
        assertEquals("applications/app-1/media/app-logo.png", coordinator.state.value.applications.first().logoPath)

        coordinator.handlePickedUpload(
            RestaurantsPendingPickerTarget.RestaurantMedia("rest-1", RestaurantMediaSlot.COVER, "Updated desc"),
            RestaurantsBinaryUploadSource.fromBytes("cover.webp", "image/webp", byteArrayOf(7, 8, 9)),
        )
        delay(40)
        assertEquals("restaurants/rest-1/cover.webp", coordinator.state.value.selectedRestaurant?.coverPath)

        coordinator.handlePickedUpload(
            RestaurantsPendingPickerTarget.MenuItemPhoto("rest-1", "mi-1", "Shekla Tibs"),
            RestaurantsBinaryUploadSource.fromBytes("tibs.jpg", "image/jpeg", byteArrayOf(10, 11)),
        )
        delay(40)
        assertEquals("restaurants/rest-1/tibs.jpg", coordinator.state.value.menuItems.first { it.id == "mi-1" }.photoPath)
        assertEquals(CenterUploadPhase.SUCCEEDED, coordinator.state.value.uploadState.phase)
    }

    @Test
    fun `upload failure exposes retry and succeeds cleanly on retry without duplicate confirmation`() = runBlocking {
        val ds = UploadTestCenterDataSource().apply { failNextUpload = true }
        val coordinator = createCoordinator(this, ds)
        coordinator.loadCenter()
        delay(60)

        val target = RestaurantsPendingPickerTarget.ApplicationDocument("app-1", "owner_id", "Owner ID")
        coordinator.handlePickedUpload(
            target,
            RestaurantsBinaryUploadSource.fromBytes("id.png", "image/png", byteArrayOf(1, 2, 3)),
        )
        delay(40)

        assertEquals(CenterUploadPhase.FAILED, coordinator.state.value.uploadState.phase)
        assertTrue(coordinator.state.value.uploadState.canRetry)
        assertNotNull(coordinator.state.value.errorBanner)
        assertEquals(0, ds.confirmedCount.get())

        coordinator.retryFailedUpload()
        delay(40)

        assertEquals(CenterUploadPhase.SUCCEEDED, coordinator.state.value.uploadState.phase)
        assertFalse(coordinator.state.value.uploadState.canRetry)
        assertNull(coordinator.state.value.errorBanner)
        assertEquals(1, ds.confirmedCount.get())
        assertEquals(1, coordinator.state.value.applications.first().documents.size)
    }

    @Test
    fun `account switching during upload aborts upload clears retry and blocks cross-account leakage`() = runBlocking {
        var uid: String? = "owner-A"
        val ds = UploadTestCenterDataSource().apply { delayMs = 80L }
        val coordinator = createCoordinator(this, ds) { uid }
        coordinator.loadCenter()
        delay(60)

        val target = RestaurantsPendingPickerTarget.MenuItemPhoto("rest-1", "mi-1", "Shekla Tibs")
        coordinator.handlePickedUpload(
            target,
            RestaurantsBinaryUploadSource.fromBytes("dish.jpg", "image/jpeg", byteArrayOf(1, 2, 3)),
        )
        delay(20)
        assertTrue(coordinator.state.value.uploadState.isInProgress)

        uid = "owner-B"
        coordinator.onAccountChanged("owner-B")
        delay(100)

        assertEquals("owner-B", coordinator.state.value.activeAccountUid)
        assertEquals(CenterUploadPhase.IDLE, coordinator.state.value.uploadState.phase)
        assertTrue(coordinator.state.value.applications.isEmpty())
        assertTrue(coordinator.state.value.ownedRestaurants.isEmpty())
        assertNull(coordinator.state.value.errorBanner)

        // Retrying a prior failure after account switch must remain blocked cleanly.
        uid = "owner-A"
        ds.failNextUpload = true
        ds.delayMs = 0L
        coordinator.onAccountChanged("owner-A")
        coordinator.loadCenter()
        delay(60)
        coordinator.handlePickedUpload(
            target,
            RestaurantsBinaryUploadSource.fromBytes("dish.jpg", "image/jpeg", byteArrayOf(1, 2, 3)),
        )
        delay(40)
        assertTrue(coordinator.state.value.uploadState.canRetry)

        uid = "owner-B"
        coordinator.onAccountChanged("owner-B")
        val callsBeforeRetry = ds.uploadCalls.get()
        coordinator.retryFailedUpload()
        delay(30)
        assertEquals(callsBeforeRetry, ds.uploadCalls.get())
    }

    @Test
    fun `duplicate concurrent upload clicks do not trigger duplicate confirmations`() = runBlocking {
        val ds = UploadTestCenterDataSource().apply { delayMs = 70L }
        val coordinator = createCoordinator(this, ds)
        coordinator.loadCenter()
        delay(60)

        val target = RestaurantsPendingPickerTarget.ApplicationDocument("app-1", "tin_certificate", "TIN")
        val source = RestaurantsBinaryUploadSource.fromBytes("tin.pdf", "application/pdf", byteArrayOf(1, 2, 3))

        coordinator.handlePickedUpload(target, source)
        delay(15)
        coordinator.handlePickedUpload(target, source)
        delay(90)

        assertEquals(1, ds.uploadCalls.get())
        assertEquals(1, ds.confirmedCount.get())
        assertEquals(1, coordinator.state.value.applications.first().documents.size)
    }

    private fun createCoordinator(
        scope: kotlinx.coroutines.CoroutineScope,
        ds: RestaurantCenterDataSource,
        uidProvider: () -> String? = { "owner-A" },
    ): RestaurantCenterCoordinator = RestaurantCenterCoordinator(
        scope = scope,
        dataSource = ds,
        environmentProvider = {
            RestaurantsEnvironment(
                supabaseUrl = "https://restaurants-test.supabase.co",
                supabasePublishableKey = "pk_test",
                isRestaurantCenterEnabled = true,
            )
        },
        accountUidProvider = uidProvider,
    )

    private class UploadTestCenterDataSource : RestaurantCenterDataSource {
        val uploadCalls = AtomicInteger(0)
        val confirmedCount = AtomicInteger(0)
        var delayMs: Long = 0L
        var failNextUpload: Boolean = false

        private var app = CenterApplicationUi(
            id = "app-1",
            applicantProfileId = "owner-A",
            legalName = "Hearth PLC",
            brandName = "Habesha Hearth",
            description = "Grill",
            businessType = "restaurant",
            contactPhone = "+251911000000",
            contactEmail = "owner@hearth.et",
            tinNumber = "TIN-123",
            status = RestaurantApplicationStatus.DRAFT,
        )

        private var restaurant = CenterRestaurantUi(
            id = "rest-1",
            slug = "habesha-hearth",
            name = "Habesha Hearth",
            description = "Grill",
            status = RestaurantOperationalStatus.ACTIVE,
            isLiveOrderingEnabled = false,
            currentUserRole = RestaurantStaffRole.RESTAURANT_OWNER,
        )

        private var menuItems = listOf(
            CenterMenuItemUi(
                id = "mi-1",
                restaurantId = "rest-1",
                categoryId = "cat-1",
                categoryName = "Main",
                name = "Shekla Tibs",
                description = "Hot clay pot",
                basePriceMinor = 45000L,
            ),
        )

        override suspend fun loadMyApplications(): Result<List<CenterApplicationUi>> = Result.success(listOf(app))

        override suspend fun saveApplicationDraft(request: SaveApplicationDraftRequest): Result<CenterApplicationUi> {
            app = app.copy(
                logoPath = request.logoPath ?: app.logoPath,
                coverPath = request.coverPath ?: app.coverPath,
            )
            return Result.success(app)
        }

        override suspend fun attachApplicationDocument(request: ConfirmDocumentRequest): Result<CenterApplicationDocUi> =
            error("unused")

        private suspend fun beforeUpload(): Throwable? {
            uploadCalls.incrementAndGet()
            if (delayMs > 0L) delay(delayMs)
            if (failNextUpload) {
                failNextUpload = false
                return RestaurantsFailure.Network("Temporary upload failure")
            }
            return null
        }

        override suspend fun uploadAndConfirmApplicationDocument(
            applicationId: String,
            docType: String,
            source: RestaurantsBinaryUploadSource,
        ): Result<CenterApplicationDocUi> {
            beforeUpload()?.let { return Result.failure(it) }
            confirmedCount.incrementAndGet()
            val doc = CenterApplicationDocUi("doc-1", docType, source.fileName, source.mimeType, source.byteSize)
            app = app.copy(documents = app.documents.filterNot { it.id == doc.id } + doc)
            return Result.success(doc)
        }

        override suspend fun uploadApplicationMedia(
            applicationId: String,
            source: RestaurantsBinaryUploadSource,
        ): Result<String> {
            beforeUpload()?.let { return Result.failure(it) }
            return Result.success("applications/$applicationId/media/${source.fileName}")
        }

        override suspend fun uploadAndUpdateRestaurantMedia(
            restaurantId: String,
            slot: RestaurantMediaSlot,
            source: RestaurantsBinaryUploadSource,
            description: String?,
        ): Result<RestaurantSummaryDto> {
            beforeUpload()?.let { return Result.failure(it) }
            val path = "restaurants/$restaurantId/${source.fileName}"
            restaurant = restaurant.copy(
                description = description ?: restaurant.description,
                logoPath = if (slot == RestaurantMediaSlot.LOGO) path else restaurant.logoPath,
                coverPath = if (slot == RestaurantMediaSlot.COVER) path else restaurant.coverPath,
            )
            return Result.success(
                RestaurantSummaryDto(
                    id = restaurantId,
                    name = restaurant.name,
                    description = restaurant.description,
                    logoPath = restaurant.logoPath,
                    coverPath = restaurant.coverPath,
                    status = "active",
                ),
            )
        }

        override suspend fun uploadAndAttachMenuItemImage(
            restaurantId: String,
            menuItemId: String,
            source: RestaurantsBinaryUploadSource,
            isPrimary: Boolean?,
            sortOrder: Int?,
        ): Result<MenuItemImageDto> {
            beforeUpload()?.let { return Result.failure(it) }
            val path = "restaurants/$restaurantId/${source.fileName}"
            menuItems = menuItems.map { if (it.id == menuItemId) it.copy(photoPath = path) else it }
            confirmedCount.incrementAndGet()
            return Result.success(
                MenuItemImageDto(
                    id = "img-1",
                    restaurantId = restaurantId,
                    menuItemId = menuItemId,
                    storagePath = path,
                    isPrimary = isPrimary ?: true,
                    sortOrder = sortOrder ?: 0,
                ),
            )
        }

        override suspend fun submitApplication(
            applicationId: String,
            idempotencyKey: String?,
        ): Result<CenterApplicationUi> = error("unused")
        override suspend fun loadAccessibleRestaurants(): Result<List<CenterRestaurantUi>> = Result.success(listOf(restaurant))
        override suspend fun loadBranches(restaurantId: String): Result<List<CenterBranchUi>> = Result.success(
            listOf(CenterBranchUi("br-1", restaurantId, "MAIN", "Main", "Bole", "Addis Ababa", true, false, true, "active")),
        )
        override suspend fun createBranch(request: CreateBranchRequest) = error("unused")
        override suspend fun updateBranch(request: UpdateBranchRequest) = error("unused")
        override suspend fun updateRestaurantSettings(request: UpdateRestaurantSettingsRequest) = error("unused")
        override suspend fun publishLive(restaurantId: String, enable: Boolean) = error("unused")
        override suspend fun loadMenuCategories(restaurantId: String) = Result.success(emptyList<MenuCategoryDto>())
        override suspend fun loadMenuItemsForBranch(restaurantId: String, branchId: String) = Result.success(menuItems)
        override suspend fun upsertMenuCategory(request: UpsertCategoryRequest) = error("unused")
        override suspend fun upsertMenuItem(request: UpsertMenuItemRequest) = error("unused")
        override suspend fun setBranchAvailability(request: SetBranchAvailabilityRequest) = Result.success(Unit)
        override suspend fun loadTables(restaurantId: String, branchId: String) = Result.success(emptyList<DiningTableWithStatusDto>())
        override suspend fun upsertTable(request: UpsertTableRequest) = error("unused")
        override suspend fun rotateTableQr(restaurantId: String, branchId: String, tableId: String): Result<RotateQrResponse> = error("unused")
        override suspend fun revokeTableQr(restaurantId: String, branchId: String, tableId: String) = Result.success(Unit)
        override suspend fun openDiningSession(restaurantId: String, branchId: String, tableId: String): Result<OpenSessionResponse> = error("unused")
        override suspend fun rotateSessionCode(restaurantId: String, branchId: String, diningSessionId: String): Result<RotateSessionCodeResponse> = error("unused")
        override suspend fun closeDiningSession(restaurantId: String, branchId: String, diningSessionId: String) = Result.success(Unit)
        override suspend fun loadStaffMembers(restaurantId: String, branchId: String?) = Result.success(emptyList<StaffMemberSummaryDto>())
        override suspend fun loadStaffInvitations(restaurantId: String, branchId: String?) = Result.success(emptyList<StaffInvitationDto>())
        override suspend fun inviteStaff(request: StaffInviteRequest): Result<StaffInviteResponse> = error("unused")
        override suspend fun revokeStaffMember(staffMemberId: String) = Result.success(Unit)
        override suspend fun revokeStaffInvitation(invitationId: String) = Result.success(Unit)
        override suspend fun loadBranchOrders(restaurantId: String, branchId: String) = Result.success(emptyList<CenterOrderTicketUi>())
        override suspend fun transitionOrderStatus(orderId: String, targetStatus: String, reason: String?): Result<TransitionOrderResultDto> = error("unused")
    }
}
