package com.adischat.app.feature.restaurants.center

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.feature.restaurants.data.ConfirmDocumentRequest
import com.adischat.app.feature.restaurants.data.CreateBranchRequest
import com.adischat.app.feature.restaurants.data.RestaurantMediaSlot
import com.adischat.app.feature.restaurants.data.RestaurantSummaryDto
import com.adischat.app.feature.restaurants.data.RestaurantsBinaryUploadSource
import com.adischat.app.feature.restaurants.data.RestaurantsConfig
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RestaurantsErrorMapper
import com.adischat.app.feature.restaurants.data.RestaurantsFailure
import com.adischat.app.feature.restaurants.data.RestaurantsUploadValidator
import com.adischat.app.feature.restaurants.data.SaveApplicationDraftRequest
import com.adischat.app.feature.restaurants.data.SetBranchAvailabilityRequest
import com.adischat.app.feature.restaurants.data.StaffInvitationDto
import com.adischat.app.feature.restaurants.data.StaffInviteRequest
import com.adischat.app.feature.restaurants.data.StaffMemberSummaryDto
import com.adischat.app.feature.restaurants.data.UpdateBranchRequest
import com.adischat.app.feature.restaurants.data.UpdateRestaurantSettingsRequest
import com.adischat.app.feature.restaurants.data.UpsertCategoryRequest
import com.adischat.app.feature.restaurants.data.UpsertMenuItemRequest
import com.adischat.app.feature.restaurants.data.UpsertTableRequest
import com.adischat.app.feature.restaurants.presentation.RestaurantOrderStatus
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Isolated offline-first coordinator for Restaurant Center.
 * Strictly blocks all operations when [RestaurantsEnvironment.isRestaurantCenterEnabled] is false,
 * isolates tenant/branch datasets cleanly, and clears state on account switch or sign-out.
 */
class RestaurantCenterCoordinator(
    private val scope: CoroutineScope,
    private val dataSource: RestaurantCenterDataSource,
    private val environmentProvider: () -> RestaurantsEnvironment = { RestaurantsConfig.currentEnvironment },
    private val accountUidProvider: () -> String? = {
        try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
    },
) {
    private data class PendingRetryUpload(
        val uid: String,
        val epoch: Long,
        val target: RestaurantsPendingPickerTarget,
        val source: RestaurantsBinaryUploadSource,
    )

    private val flowEpoch = AtomicLong(0L)
    private var inFlightJob: Job? = null
    private var inFlightUploadJob: Job? = null
    private var pendingRetryUpload: PendingRetryUpload? = null

    @Volatile
    private var uploadCancelledByUser: Boolean = false

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<RestaurantCenterUiState> = _state.asStateFlow()

    private fun initialState(): RestaurantCenterUiState {
        val env = environmentProvider()
        return RestaurantCenterUiState(
            loadState = if (env.isRestaurantCenterEnabled) RestaurantsLoadState.Empty else RestaurantsLoadState.Disabled,
            isCenterEnabled = env.isRestaurantCenterEnabled,
            activeAccountUid = accountUidProvider()?.takeIf { it.isNotBlank() },
        )
    }

    fun onAccountChanged(newAdisChatUid: String?) {
        val cleanUid = newAdisChatUid?.takeIf { it.isNotBlank() }
        flowEpoch.incrementAndGet()
        uploadCancelledByUser = false
        inFlightJob?.cancel()
        inFlightUploadJob?.cancel()
        inFlightUploadJob = null
        pendingRetryUpload = null
        val env = environmentProvider()
        _state.value = RestaurantCenterUiState(
            loadState = if (env.isRestaurantCenterEnabled) RestaurantsLoadState.Empty else RestaurantsLoadState.Disabled,
            isCenterEnabled = env.isRestaurantCenterEnabled,
            activeAccountUid = cleanUid,
            actionFeedback = if (cleanUid == null) {
                "Signed out. Restaurant Center cleared."
            } else {
                "Account switched. Previous restaurant access cleared."
            },
        )
    }

    fun selectTab(tab: RestaurantCenterTab) {
        if (tab !in RestaurantRolePermissions.visibleTabs(_state.value.activeRole)) return
        _state.update { it.copy(selectedTab = tab, errorBanner = null, actionFeedback = null) }
    }

    fun selectOrderFilter(filter: OrderBoardFilter) {
        _state.update { it.copy(orderFilter = filter) }
    }

    fun loadCenter() {
        val env = environmentProvider()
        if (!env.isRestaurantCenterEnabled) {
            _state.update {
                it.copy(
                    loadState = RestaurantsLoadState.Disabled,
                    isCenterEnabled = false,
                )
            }
            return
        }
        val uid = requireUid() ?: return
        val startEpoch = flowEpoch.get()
        inFlightJob?.cancel()

        _state.update {
            it.copy(
                loadState = RestaurantsLoadState.Loading,
                isCenterEnabled = true,
                errorBanner = null,
            )
        }

        inFlightJob = scope.launch {
            val (appsRes, restsRes) = coroutineScope {
                val a = async { dataSource.loadMyApplications() }
                val r = async { dataSource.loadAccessibleRestaurants() }
                a.await() to r.await()
            }
            if (!isStillValid(startEpoch, uid)) return@launch

            val apps = appsRes.getOrElse { err ->
                val f = RestaurantsErrorMapper.mapThrowable(err)
                showLoadFailure(f, startEpoch, uid)
                return@launch
            }
            val rests = restsRes.getOrElse { err ->
                val f = RestaurantsErrorMapper.mapThrowable(err)
                showLoadFailure(f, startEpoch, uid)
                return@launch
            }

            val selectedRest = rests.firstOrNull { it.id == _state.value.selectedRestaurantId }
                ?: rests.firstOrNull()

            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                val switchedOrCleared = selectedRest == null || selectedRest.id != current.selectedRestaurantId
                current.copy(
                    loadState = RestaurantsLoadState.Content,
                    applications = apps,
                    ownedRestaurants = rests,
                    selectedRestaurantId = selectedRest?.id,
                    selectedTab = if (current.selectedTab in RestaurantRolePermissions.visibleTabs(selectedRest?.roleForBranch(if (switchedOrCleared) selectedRest?.assignedBranchId else current.selectedBranchId)))
                        current.selectedTab else RestaurantCenterTab.OVERVIEW,
                    selectedBranchId = if (switchedOrCleared) selectedRest?.assignedBranchId else current.selectedBranchId,
                    branches = if (switchedOrCleared) emptyList() else current.branches,
                    categories = if (switchedOrCleared) emptyList() else current.categories,
                    menuItems = if (switchedOrCleared) emptyList() else current.menuItems,
                    tables = if (switchedOrCleared) emptyList() else current.tables,
                    orders = if (switchedOrCleared) emptyList() else current.orders,
                    staffMembers = if (switchedOrCleared) emptyList() else current.staffMembers,
                    staffInvitations = if (switchedOrCleared) emptyList() else current.staffInvitations,
                    orderSyncCursor = if (switchedOrCleared) null else current.orderSyncCursor,
                    orderSyncCursorId = if (switchedOrCleared) null else current.orderSyncCursorId,
                    orderHasMore = if (switchedOrCleared) false else current.orderHasMore,
                )
            }

            if (selectedRest != null) {
                loadRestaurantScope(selectedRest.id, startEpoch, uid)
            }
        }
    }

    fun selectRestaurant(restaurantId: String) {
        val env = environmentProvider()
        if (!env.isRestaurantCenterEnabled) return
        val uid = requireUid() ?: return
        val target = _state.value.ownedRestaurants.firstOrNull { it.id == restaurantId } ?: return
        val startEpoch = flowEpoch.incrementAndGet()
        inFlightJob?.cancel()

        // Clear prior tenant's branch/menu/table/order/staff state immediately before switching.
        _state.update {
            it.copy(
                selectedRestaurantId = target.id,
                selectedTab = if (it.selectedTab in RestaurantRolePermissions.visibleTabs(target.roleForBranch(target.assignedBranchId)))
                    it.selectedTab else RestaurantCenterTab.OVERVIEW,
                selectedBranchId = target.assignedBranchId,
                branches = emptyList(),
                categories = emptyList(),
                menuItems = emptyList(),
                tables = emptyList(),
                orders = emptyList(),
                orderSyncCursor = null,
                orderSyncCursorId = null,
                orderHasMore = false,
                staffMembers = emptyList(),
                staffInvitations = emptyList(),
                isWorking = true,
                errorBanner = null,
            )
        }
        inFlightJob = scope.launch {
            loadRestaurantScope(target.id, startEpoch, uid)
        }
    }

    fun selectBranch(branchId: String) {
        val env = environmentProvider()
        if (!env.isRestaurantCenterEnabled) return
        val uid = requireUid() ?: return
        val currentRestId = _state.value.selectedRestaurantId ?: return
        val branchBelongs = _state.value.branches.any { it.id == branchId && it.restaurantId == currentRestId }
        if (!branchBelongs) {
            _state.update { it.copy(errorBanner = "Selected branch does not belong to current restaurant.") }
            return
        }

        val startEpoch = flowEpoch.incrementAndGet()
        inFlightJob?.cancel()
        _state.update {
            it.copy(
                selectedBranchId = branchId,
                selectedTab = if (it.selectedTab in RestaurantRolePermissions.visibleTabs(it.selectedRestaurant?.roleForBranch(branchId)))
                    it.selectedTab else RestaurantCenterTab.OVERVIEW,
                tables = emptyList(),
                orders = emptyList(),
                orderSyncCursor = null,
                orderSyncCursorId = null,
                orderHasMore = false,
                isWorking = true,
                errorBanner = null,
            )
        }
        inFlightJob = scope.launch {
            loadBranchScope(currentRestId, branchId, startEpoch, uid)
        }
    }

    // ========================================================================
    // Application draft & submission
    // ========================================================================

    fun saveApplicationDraft(
        request: SaveApplicationDraftRequest,
        pendingLicenseSource: RestaurantsBinaryUploadSource? = null,
        onSuccess: (CenterApplicationUi) -> Unit = {},
    ) {
        val uid = requireEnabledAndUid() ?: return
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val saved = dataSource.saveApplicationDraft(request).getOrThrow()
            var currentApp = saved
            if (pendingLicenseSource != null) {
                val doc = dataSource.uploadAndConfirmApplicationDocument(
                    applicationId = saved.id,
                    docType = "business_license",
                    source = pendingLicenseSource,
                ).getOrThrow()
                val updatedDocs = currentApp.documents.filterNot { it.id == doc.id } + doc
                currentApp = currentApp.copy(documents = updatedDocs)
            }
            val updatedApps = (_state.value.applications.filterNot { it.id == currentApp.id } + currentApp)
            _state.update {
                if (!isStillValid(startEpoch, uid)) return@update it
                it.copy(
                    applications = updatedApps,
                    actionFeedback = "Application draft saved (${currentApp.status.label}).",
                )
            }
            onSuccess(currentApp)
        }
    }

    fun submitDraftApplication(
        draft: SaveApplicationDraftRequest,
        pendingLicenseSource: RestaurantsBinaryUploadSource? = null,
        onSuccess: () -> Unit = {},
    ) {
        val uid = requireEnabledAndUid() ?: return
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val saved = dataSource.saveApplicationDraft(draft).getOrThrow()
            var currentApp = saved
            if (pendingLicenseSource != null) {
                val doc = dataSource.uploadAndConfirmApplicationDocument(
                    applicationId = saved.id,
                    docType = "business_license",
                    source = pendingLicenseSource,
                ).getOrThrow()
                val updatedDocs = currentApp.documents.filterNot { it.id == doc.id } + doc
                currentApp = currentApp.copy(documents = updatedDocs)
            }
            val hasLicense = currentApp.documents.any {
                it.docType.equals("business_license", ignoreCase = true)
            }
            if (!hasLicense) {
                throw RestaurantsFailure.Validation(
                    code = "missing_business_license",
                    message = "At least one business license document is required before submitting.",
                )
            }
            val idempotencyKey = "sub-${saved.id}-${UUID.randomUUID().toString().take(12)}"
            val submitted = dataSource.submitApplication(saved.id, idempotencyKey).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                val updatedApps = (current.applications.filterNot { it.id == submitted.id } + submitted)
                current.copy(
                    applications = updatedApps,
                    actionFeedback = "Application submitted for Admin review.",
                )
            }
            onSuccess()
        }
    }

    fun confirmApplicationDocument(request: ConfirmDocumentRequest) {
        val uid = requireEnabledAndUid() ?: return
        val app = _state.value.applications.firstOrNull { it.id == request.applicationId }
        if (app != null && !app.status.isEditableByApplicant) {
            _state.update { it.copy(errorBanner = "Documents can only be edited while in Draft or Changes Requested.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val doc = dataSource.attachApplicationDocument(request).getOrThrow()
            appendConfirmedApplicationDoc(startEpoch, uid, request.applicationId, doc)
        }
    }

    fun handlePickedUpload(
        target: RestaurantsPendingPickerTarget,
        source: RestaurantsBinaryUploadSource,
    ) {
        when (target) {
            is RestaurantsPendingPickerTarget.ApplicationDocument ->
                uploadApplicationDocument(target.applicationId, target.docType, source, target.label)
            is RestaurantsPendingPickerTarget.ApplicationMedia -> {
                val app = _state.value.applications.firstOrNull { it.id == target.application.id }
                    ?: target.application
                uploadApplicationDraftMedia(
                    draft = SaveApplicationDraftRequest(
                        applicationId = app.id,
                        legalName = app.legalName,
                        brandName = app.brandName,
                        description = app.description,
                        businessType = app.businessType,
                        contactPhone = app.contactPhone,
                        contactEmail = app.contactEmail,
                        tinNumber = app.tinNumber,
                        logoPath = app.logoPath,
                        coverPath = app.coverPath,
                    ),
                    applicationId = app.id,
                    slot = target.slot,
                    source = source,
                    target = target,
                )
            }
            is RestaurantsPendingPickerTarget.RestaurantMedia ->
                uploadRestaurantMedia(target.slot, source, target.description, target)
            is RestaurantsPendingPickerTarget.MenuItemPhoto ->
                uploadMenuItemPhoto(target.menuItemId, source, target = target)
        }
    }

    fun reportUploadValidationError(
        target: RestaurantsPendingPickerTarget,
        message: String,
    ) {
        pendingRetryUpload = null
        _state.update {
            it.copy(
                uploadState = CenterUploadUiState(
                    phase = CenterUploadPhase.FAILED,
                    targetKey = target.targetKey,
                    label = target.displayLabel,
                    errorMessage = message,
                    canRetry = false,
                ),
                errorBanner = message,
            )
        }
    }

    fun reportPickerCancelled() {
        // Picker cancellation requires no network call and preserves existing upload/screen state cleanly.
    }

    fun cancelActiveUpload() {
        val currentUpload = _state.value.uploadState
        if (!currentUpload.isInProgress && inFlightUploadJob?.isActive != true) return
        uploadCancelledByUser = true
        inFlightUploadJob?.cancel()
        inFlightUploadJob = null
        pendingRetryUpload = null
        _state.update {
            it.copy(
                uploadState = currentUpload.copy(
                    phase = CenterUploadPhase.CANCELLED,
                    statusMessage = "Upload cancelled.",
                    errorMessage = null,
                    canRetry = false,
                ),
                errorBanner = null,
            )
        }
    }

    fun retryFailedUpload() {
        val uid = requireEnabledAndUid() ?: return
        val retry = pendingRetryUpload ?: return
        if (retry.uid != uid || retry.epoch != flowEpoch.get()) {
            pendingRetryUpload = null
            return
        }
        pendingRetryUpload = null
        handlePickedUpload(retry.target, retry.source)
    }

    fun dismissUploadStatus() {
        if (_state.value.uploadState.isInProgress) return
        pendingRetryUpload = null
        _state.update { it.copy(uploadState = CenterUploadUiState()) }
    }

    fun uploadApplicationDocument(
        applicationId: String,
        docType: String,
        source: RestaurantsBinaryUploadSource,
        displayLabel: String = "Verification Document",
    ) {
        val target = RestaurantsPendingPickerTarget.ApplicationDocument(applicationId, docType, displayLabel)
        val uid = requireEnabledAndUid() ?: return
        val app = _state.value.applications.firstOrNull { it.id == applicationId }
        if (app != null && !app.status.isEditableByApplicant) {
            reportUploadValidationError(target, "Documents can only be edited while in Draft or Changes Requested.")
            return
        }
        launchTrackedUpload(uid = uid, target = target, source = source) { startEpoch ->
            val doc = dataSource.uploadAndConfirmApplicationDocument(applicationId, docType, source).getOrThrow()
            appendConfirmedApplicationDoc(startEpoch, uid, applicationId, doc)
            "Uploaded ${doc.fileName} safely."
        }
    }

    fun uploadApplicationDraftMedia(
        draft: SaveApplicationDraftRequest,
        applicationId: String,
        slot: RestaurantMediaSlot,
        source: RestaurantsBinaryUploadSource,
        target: RestaurantsPendingPickerTarget? = null,
    ) {
        val uid = requireEnabledAndUid() ?: return
        val app = _state.value.applications.firstOrNull { it.id == applicationId }
        val resolvedTarget = target ?: RestaurantsPendingPickerTarget.ApplicationMedia(
            application = app ?: CenterApplicationUi(
                id = applicationId,
                applicantProfileId = uid,
                legalName = draft.legalName,
                brandName = draft.brandName,
                description = draft.description,
                businessType = draft.businessType,
                contactPhone = draft.contactPhone,
                contactEmail = draft.contactEmail,
                tinNumber = draft.tinNumber,
                status = RestaurantApplicationStatus.DRAFT,
            ),
            slot = slot,
        )
        if (app != null && !app.status.isEditableByApplicant) {
            reportUploadValidationError(resolvedTarget, "Application media can only be edited while in Draft or Changes Requested.")
            return
        }
        launchTrackedUpload(uid = uid, target = resolvedTarget, source = source) { startEpoch ->
            val storagePath = dataSource.uploadApplicationMedia(applicationId, source).getOrThrow()
            val updatedRequest = draft.copy(
                applicationId = applicationId,
                logoPath = if (slot == RestaurantMediaSlot.LOGO) storagePath else draft.logoPath,
                coverPath = if (slot == RestaurantMediaSlot.COVER) storagePath else draft.coverPath,
            )
            val saved = dataSource.saveApplicationDraft(updatedRequest).getOrThrow()
            val feedback = "Uploaded and saved application ${slot.name.lowercase()} media."
            _state.update {
                if (!isStillValid(startEpoch, uid)) return@update it
                val updatedApps = (it.applications.filterNot { existing -> existing.id == saved.id } + saved)
                it.copy(
                    applications = updatedApps,
                    actionFeedback = feedback,
                )
            }
            feedback
        }
    }

    private fun appendConfirmedApplicationDoc(
        startEpoch: Long,
        uid: String,
        applicationId: String,
        doc: CenterApplicationDocUi,
    ) {
        _state.update { current ->
            if (!isStillValid(startEpoch, uid)) return@update current
            current.copy(
                applications = current.applications.map { existing ->
                    if (existing.id == applicationId) {
                        val dedupedDocs = existing.documents.filterNot { it.id == doc.id } + doc
                        existing.copy(documents = dedupedDocs)
                    } else {
                        existing
                    }
                },
                actionFeedback = "Attached ${doc.fileName} safely.",
            )
        }
    }

    fun submitApplication(applicationId: String) {
        val uid = requireEnabledAndUid() ?: return
        val app = _state.value.applications.firstOrNull { it.id == applicationId }
        if (app != null && !app.status.isEditableByApplicant) {
            _state.update { it.copy(errorBanner = "Only Draft or Changes Requested applications can be submitted.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val idempotencyKey = "sub-${applicationId}-${UUID.randomUUID().toString().take(12)}"
            val submitted = dataSource.submitApplication(applicationId, idempotencyKey).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    applications = current.applications.map {
                        if (it.id == applicationId) submitted else it
                    },
                    actionFeedback = "Application submitted for Admin review.",
                )
            }
        }
    }

    // ========================================================================
    // Operational settings & branches
    // ========================================================================

    fun activateRestaurant() {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        if (!RestaurantRolePermissions.canPublishLive(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Only the Restaurant Owner may activate the restaurant.") }
            return
        }
        if (rest.status == RestaurantOperationalStatus.SUSPENDED) {
            _state.update { it.copy(errorBanner = "Suspended restaurants require Platform Admin reactivation.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val res = dataSource.activateRestaurant(rest.id).getOrThrow()
            val nextOp = RestaurantOperationalStatus.fromWireValue(res.status)
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    ownedRestaurants = current.ownedRestaurants.map { r ->
                        if (r.id == rest.id) {
                            r.copy(status = nextOp, isLiveOrderingEnabled = res.isLiveOrderingEnabled)
                        } else {
                            r
                        }
                    },
                    actionFeedback = if (res.isLiveOrderingEnabled) {
                        "Restaurant is active and live."
                    } else {
                        "Restaurant activated (pending live toggle)."
                    },
                )
            }
        }
    }

    fun syncOrders(incremental: Boolean = true) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        val branchId = _state.value.selectedBranchId ?: return
        val startEpoch = flowEpoch.get()
        val since = if (incremental) _state.value.orderSyncCursor else null
        val afterId = if (incremental) _state.value.orderSyncCursorId else null

        runMutation(startEpoch, uid) {
            val (tickets, meta) = dataSource.syncBranchOrders(
                restaurantId = rest.id,
                branchId = branchId,
                since = since,
                afterId = afterId,
            ).getOrThrow()

            val scopedIncoming = RestaurantCenterReducers.filterOrdersForBranch(rest.id, branchId, tickets)
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                val merged = if (since != null) {
                    val byId = current.orders.associateBy { it.orderId }.toMutableMap()
                    scopedIncoming.forEach { t -> byId[t.orderId] = t }
                    byId.values.toList()
                } else {
                    scopedIncoming.distinctBy { it.orderId }
                }
                current.copy(
                    orders = merged,
                    orderSyncCursor = meta.nextCursor ?: current.orderSyncCursor,
                    orderSyncCursorId = meta.nextCursorId ?: current.orderSyncCursorId,
                    orderHasMore = meta.hasMore,
                )
            }
        }
    }

    fun togglePublishLive(enable: Boolean) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        if (!RestaurantRolePermissions.canPublishLive(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Only the Restaurant Owner can change live ordering status.") }
            return
        }
        if (rest.status != RestaurantOperationalStatus.ACTIVE) {
            _state.update { it.copy(errorBanner = "Restaurant status must be Active before publishing live ordering.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val live = dataSource.publishLive(rest.id, enable).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    ownedRestaurants = current.ownedRestaurants.map {
                        if (it.id == rest.id) it.copy(isLiveOrderingEnabled = live) else it
                    },
                    actionFeedback = if (live) "Live dine-in ordering enabled." else "Live dine-in ordering paused.",
                )
            }
        }
    }

    fun updateRestaurantSettings(description: String, logoPath: String? = null, coverPath: String? = null) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        if (!RestaurantRolePermissions.canEditSettings(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Owner role required to update settings.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val updated = dataSource.updateRestaurantSettings(
                UpdateRestaurantSettingsRequest(
                    restaurantId = rest.id,
                    description = description,
                    logoPath = logoPath,
                    coverPath = coverPath,
                ),
            ).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                applyUpdatedRestaurantSummary(current, rest.id, updated, "Restaurant settings updated.")
            }
        }
    }

    fun uploadRestaurantMedia(
        slot: RestaurantMediaSlot,
        source: RestaurantsBinaryUploadSource,
        description: String? = null,
        target: RestaurantsPendingPickerTarget? = null,
    ) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        val resolvedTarget = target ?: RestaurantsPendingPickerTarget.RestaurantMedia(rest.id, slot, description)
        if (!RestaurantRolePermissions.canEditSettings(_state.value.activeRole)) {
            reportUploadValidationError(resolvedTarget, "Owner role required to update settings.")
            return
        }
        launchTrackedUpload(uid = uid, target = resolvedTarget, source = source) { startEpoch ->
            val updated = dataSource.uploadAndUpdateRestaurantMedia(
                restaurantId = rest.id,
                slot = slot,
                source = source,
                description = description,
            ).getOrThrow()
            val feedback = "Updated restaurant ${slot.name.lowercase()} image."
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                applyUpdatedRestaurantSummary(current, rest.id, updated, feedback)
            }
            feedback
        }
    }

    private fun applyUpdatedRestaurantSummary(
        current: RestaurantCenterUiState,
        restaurantId: String,
        updated: RestaurantSummaryDto,
        feedback: String,
    ): RestaurantCenterUiState = current.copy(
        ownedRestaurants = current.ownedRestaurants.map {
            if (it.id == restaurantId) {
                it.copy(
                    description = updated.description,
                    logoPath = updated.logoPath ?: it.logoPath,
                    coverPath = updated.coverPath ?: it.coverPath,
                )
            } else {
                it
            }
        },
        actionFeedback = feedback,
    )

    fun updateBranchSettings(
        branchId: String,
        isDineInActive: Boolean,
        requireStaffActivation: Boolean,
        requireSessionCode: Boolean,
    ) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        if (_state.value.selectedBranchId != branchId || _state.value.branches.none { it.id == branchId && it.restaurantId == rest.id }) return
        if (!RestaurantRolePermissions.canEditBranchSettings(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Owner or assigned branch manager role required to update branch settings.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val updated = dataSource.updateBranch(
                UpdateBranchRequest(
                    restaurantId = rest.id,
                    branchId = branchId,
                    requireStaffSessionActivation = requireStaffActivation,
                    requireSessionCode = requireSessionCode,
                    isDineInActive = isDineInActive,
                ),
            ).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    branches = current.branches.map { if (it.id == branchId) updated else it },
                    actionFeedback = "Branch ${updated.name} updated.",
                )
            }
        }
    }

    // ========================================================================
    // Menu management
    // ========================================================================

    fun toggleMenuItemAvailability(menuItemId: String, available: Boolean) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        val branchId = _state.value.selectedBranchId ?: return
        if (!RestaurantRolePermissions.canToggleBranchStock(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Staff role lacks permission to toggle branch stock.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            dataSource.setBranchAvailability(
                SetBranchAvailabilityRequest(
                    restaurantId = rest.id,
                    branchId = branchId,
                    menuItemId = menuItemId,
                    isAvailable = available,
                ),
            ).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    menuItems = current.menuItems.map {
                        if (it.id == menuItemId) it.copy(isAvailableAtBranch = available) else it
                    },
                    actionFeedback = "Item availability updated for branch.",
                )
            }
        }
    }

    fun upsertMenuCategory(name: String, sortOrder: Int = 0, categoryId: String? = null) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        if (!RestaurantRolePermissions.canManageMenu(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Only the Owner can edit menu categories.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val cat = dataSource.upsertMenuCategory(
                UpsertCategoryRequest(
                    restaurantId = rest.id,
                    id = categoryId,
                    name = name,
                    sortOrder = sortOrder,
                ),
            ).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                val updated = current.categories.filterNot { it.id == cat.id } + cat
                current.copy(
                    categories = updated.sortedBy { it.sortOrder },
                    actionFeedback = "Saved category \"${cat.name}\".",
                )
            }
        }
    }

    fun upsertMenuItem(
        categoryId: String,
        name: String,
        description: String,
        priceMinor: Long,
        itemId: String? = null,
        isActive: Boolean = true,
    ) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        if (!RestaurantRolePermissions.canManageMenu(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Only the Owner can edit menu items.") }
            return
        }
        val branchId = _state.value.selectedBranchId ?: return
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            dataSource.upsertMenuItem(
                UpsertMenuItemRequest(
                    restaurantId = rest.id,
                    id = itemId,
                    categoryId = categoryId,
                    name = name,
                    description = description,
                    basePriceMinor = priceMinor,
                    isActive = isActive,
                ),
            ).getOrThrow()
            val refreshed = dataSource.loadMenuItemsForBranch(rest.id, branchId).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    menuItems = refreshed,
                    actionFeedback = "Saved menu item \"$name\".",
                )
            }
        }
    }

    fun createCategoryAndItem(
        categoryName: String,
        name: String,
        description: String,
        priceMinor: Long,
    ) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        if (!RestaurantRolePermissions.canManageMenu(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Only the Owner can edit menu items.") }
            return
        }
        val branchId = _state.value.selectedBranchId ?: return
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val cat = dataSource.upsertMenuCategory(
                UpsertCategoryRequest(restaurantId = rest.id, name = categoryName, sortOrder = 0),
            ).getOrThrow()
            dataSource.upsertMenuItem(
                UpsertMenuItemRequest(
                    restaurantId = rest.id,
                    categoryId = cat.id,
                    name = name,
                    description = description,
                    basePriceMinor = priceMinor,
                    isActive = true,
                ),
            ).getOrThrow()
            val refreshed = dataSource.loadMenuItemsForBranch(rest.id, branchId).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                val updatedCats = (current.categories.filterNot { it.id == cat.id } + cat).sortedBy { it.sortOrder }
                current.copy(
                    categories = updatedCats,
                    menuItems = refreshed,
                    actionFeedback = "Created category \"$categoryName\" and dish \"$name\".",
                )
            }
        }
    }

    fun uploadMenuItemPhoto(
        menuItemId: String,
        source: RestaurantsBinaryUploadSource,
        isPrimary: Boolean = true,
        sortOrder: Int = 0,
        target: RestaurantsPendingPickerTarget? = null,
    ) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        val itemName = _state.value.menuItems.firstOrNull { it.id == menuItemId }?.name ?: "Menu Item"
        val resolvedTarget = target ?: RestaurantsPendingPickerTarget.MenuItemPhoto(rest.id, menuItemId, itemName)
        if (!RestaurantRolePermissions.canManageMenu(_state.value.activeRole)) {
            reportUploadValidationError(resolvedTarget, "Only the Owner can edit menu items.")
            return
        }
        val branchId = _state.value.selectedBranchId
        launchTrackedUpload(uid = uid, target = resolvedTarget, source = source) { startEpoch ->
            val attached = dataSource.uploadAndAttachMenuItemImage(
                restaurantId = rest.id,
                menuItemId = menuItemId,
                source = source,
                isPrimary = isPrimary,
                sortOrder = sortOrder,
            ).getOrThrow()
            val refreshed = if (branchId != null) {
                dataSource.loadMenuItemsForBranch(rest.id, branchId).getOrNull()
            } else {
                null
            }
            val feedback = "Uploaded and attached photo for $itemName."
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                val nextItems = refreshed ?: current.menuItems.map { item ->
                    if (item.id == menuItemId) item.copy(photoPath = attached.storagePath) else item
                }
                current.copy(
                    menuItems = nextItems,
                    actionFeedback = feedback,
                )
            }
            feedback
        }
    }

    // ========================================================================
    // Tables, QR codes & Staff-activated Dining Sessions
    // ========================================================================

    fun upsertTable(
        tableCode: String,
        label: String,
        zone: String,
        capacity: Int,
        tableId: String? = null,
        isActive: Boolean = true,
    ) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        val branchId = _state.value.selectedBranchId ?: return
        if (!RestaurantRolePermissions.canManageTablesAndQr(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Only Owner or Manager can add or edit tables.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val saved = dataSource.upsertTable(
                UpsertTableRequest(
                    restaurantId = rest.id,
                    branchId = branchId,
                    id = tableId,
                    tableCode = tableCode,
                    label = label,
                    zone = zone,
                    capacity = capacity,
                    isActive = isActive,
                ),
            ).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                val existing = current.tables.firstOrNull { it.id == saved.id }
                val merged = if (existing != null) {
                    saved.copy(
                        activeQrVersion = existing.activeQrVersion,
                        activeQrStatus = existing.activeQrStatus,
                        openSessionId = existing.openSessionId,
                        openSessionExpiresAt = existing.openSessionExpiresAt,
                        hasSessionCode = existing.hasSessionCode,
                        latestGeneratedSessionCode = existing.latestGeneratedSessionCode,
                        latestQrUrl = existing.latestQrUrl,
                    )
                } else {
                    saved
                }
                current.copy(
                    tables = current.tables.filterNot { it.id == saved.id } + merged,
                    actionFeedback = "Saved table ${saved.label}.",
                )
            }
        }
    }

    fun rotateTableQr(tableId: String) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        val branchId = _state.value.selectedBranchId ?: return
        if (!RestaurantRolePermissions.canManageTablesAndQr(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Only Owner or Manager can rotate table QR codes.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val rotated = dataSource.rotateTableQr(rest.id, branchId, tableId).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    tables = current.tables.map { t ->
                        if (t.id == tableId) {
                            t.copy(
                                activeQrVersion = rotated.qrVersion,
                                activeQrStatus = "active",
                                latestQrUrl = rotated.qrUrl,
                            )
                        } else {
                            t
                        }
                    },
                    actionFeedback = "Rotated QR to v${rotated.qrVersion ?: 1}.",
                )
            }
        }
    }

    fun revokeTableQr(tableId: String) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        val branchId = _state.value.selectedBranchId ?: return
        if (!RestaurantRolePermissions.canManageTablesAndQr(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Only Owner or Manager can revoke table QR codes.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            dataSource.revokeTableQr(rest.id, branchId, tableId).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    tables = current.tables.map { t ->
                        if (t.id == tableId) {
                            t.copy(
                                activeQrVersion = null,
                                activeQrStatus = "revoked",
                                latestQrUrl = null,
                            )
                        } else {
                            t
                        }
                    },
                    actionFeedback = "Revoked table QR code.",
                )
            }
        }
    }

    fun openDiningSession(tableId: String) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        val branchId = _state.value.selectedBranchId ?: return
        if (!RestaurantRolePermissions.canActivateTableSession(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Your role cannot open dining sessions.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val opened = dataSource.openDiningSession(rest.id, branchId, tableId).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    tables = current.tables.map { t ->
                        if (t.id == tableId) {
                            t.copy(
                                openSessionId = opened.diningSessionId,
                                openSessionExpiresAt = opened.expiresAt,
                                latestGeneratedSessionCode = opened.sessionCode,
                                hasSessionCode = !opened.sessionCode.isNullOrBlank(),
                            )
                        } else {
                            t
                        }
                    },
                    actionFeedback = "Opened table session.",
                )
            }
        }
    }

    fun closeDiningSession(diningSessionId: String) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        val branchId = _state.value.selectedBranchId ?: return
        if (!RestaurantRolePermissions.canActivateTableSession(_state.value.activeRole)) {
            _state.update { it.copy(errorBanner = "Your role cannot close dining sessions.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            dataSource.closeDiningSession(rest.id, branchId, diningSessionId).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    tables = current.tables.map { t ->
                        if (t.openSessionId == diningSessionId) {
                            t.copy(
                                openSessionId = null,
                                openSessionExpiresAt = null,
                                latestGeneratedSessionCode = null,
                            )
                        } else {
                            t
                        }
                    },
                    actionFeedback = "Table dining session closed.",
                )
            }
        }
    }

    // ========================================================================
    // Staff invitations & revocation (Individual AdisChat accounts only)
    // ========================================================================

    fun inviteStaffMember(
        invitedIdentifier: String,
        targetRole: RestaurantStaffRole,
        branchId: String? = _state.value.selectedBranchId,
    ) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        if (!RestaurantRolePermissions.canInviteStaffRole(_state.value.activeRole, targetRole)) {
            _state.update {
                it.copy(errorBanner = "Your role (${_state.value.activeRole?.label}) cannot invite ${targetRole.label}.")
            }
            return
        }
        val cleanId = invitedIdentifier.trim()
        if (cleanId == uid) {
            _state.update { it.copy(errorBanner = "You cannot invite your own AdisChat account.") }
            return
        }
        val built = classifyStaffInviteRequest(
            restaurantId = rest.id,
            branchId = branchId,
            roleWireValue = targetRole.wireValue,
            rawIdentifier = cleanId,
        )
        if (built.errorMessage != null || built.request == null) {
            _state.update { it.copy(errorBanner = built.errorMessage ?: "Enter a valid AdisChat User ID or verified phone number.") }
            return
        }

        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            dataSource.inviteStaff(built.request).getOrThrow()
            val refreshedInvites = dataSource.loadStaffInvitations(rest.id, branchId).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    staffInvitations = refreshedInvites.mapNotNull(::mapInvitationDto),
                    actionFeedback = "Invitation sent to ${built.request.invitedIdentifier}.",
                )
            }
        }
    }

    fun revokeStaffMember(staffMemberId: String) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        val targetMember = _state.value.staffMembers.firstOrNull {
            it.staffMemberId == staffMemberId && it.restaurantId == rest.id
        } ?: return

        if (!RestaurantRolePermissions.canRevokeStaff(_state.value.activeRole, targetMember.role)) {
            _state.update { it.copy(errorBanner = "Your role cannot revoke ${targetMember.role.label}.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            dataSource.revokeStaffMember(staffMemberId).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                val updatedMembers = current.staffMembers.map { m ->
                    if (m.staffMemberId == staffMemberId) m.copy(status = "revoked") else m
                }
                val currentWasRevoked = targetMember.profileId == uid
                if (currentWasRevoked) {
                    current.copy(
                        ownedRestaurants = current.ownedRestaurants.filterNot { it.id == rest.id },
                        selectedRestaurantId = null,
                        branches = emptyList(),
                        tables = emptyList(),
                        orders = emptyList(),
                        staffMembers = emptyList(),
                        staffInvitations = emptyList(),
                        errorBanner = "Your staff access for ${rest.name} was revoked.",
                    )
                } else {
                    current.copy(
                        staffMembers = updatedMembers,
                        actionFeedback = "Revoked access for ${targetMember.displayName}.",
                    )
                }
            }
        }
    }

    // ========================================================================
    // Kitchen Order Board
    // ========================================================================

    fun transitionOrderStatus(
        orderId: String,
        targetStatus: RestaurantOrderStatus,
        reason: String? = null,
    ) {
        val uid = requireEnabledAndUid() ?: return
        val rest = requireActiveSelectedRestaurant() ?: return
        val branchId = _state.value.selectedBranchId ?: return
        val ticket = _state.value.orders.firstOrNull {
            it.orderId == orderId && it.restaurantId == rest.id && it.branchId == branchId
        } ?: return

        val allowed = RestaurantRolePermissions.allowedNextOrderStatuses(_state.value.activeRole, ticket.status)
        if (!allowed.contains(targetStatus)) {
            _state.update {
                it.copy(
                    errorBanner = "Transition ${ticket.status.label} -> ${targetStatus.label} is not allowed for ${_state.value.activeRole?.label ?: "current role"}.",
                )
            }
            return
        }
        if (targetStatus == RestaurantOrderStatus.REJECTED && reason.isNullOrBlank()) {
            _state.update { it.copy(errorBanner = "Please enter a brief reason before declining an order.") }
            return
        }

        val startEpoch = flowEpoch.get()
        runMutation(startEpoch, uid) {
            val res = dataSource.transitionOrderStatus(orderId, targetStatus.wireValue, reason?.trim()).getOrThrow()
            val refreshedOrders = dataSource.loadBranchOrders(rest.id, branchId).getOrThrow()
                .filter { it.restaurantId == rest.id && it.branchId == branchId }
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    orders = refreshedOrders,
                    actionFeedback = "Order #${ticket.orderNumber} updated to ${res.toStatus}.",
                )
            }
        }
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private suspend fun loadRestaurantScope(restaurantId: String, startEpoch: Long, uid: String) {
        val branchesRes = dataSource.loadBranches(restaurantId)
        if (!isStillValid(startEpoch, uid)) return
        if (clearOnAuthorizationFailure(branchesRes.exceptionOrNull(), startEpoch, uid)) return

        val branches = branchesRes.getOrElse { err ->
            val f = RestaurantsErrorMapper.mapThrowable(err)
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(isWorking = false, errorBanner = f.userMessage)
            }
            return
        }.filter { it.restaurantId == restaurantId }

        val assigned = _state.value.ownedRestaurants.firstOrNull { it.id == restaurantId }?.assignedBranchId
        val selectedBranchId = branches.firstOrNull { it.id == assigned }?.id
            ?: branches.firstOrNull()?.id

        val (catsRes, staffRes, invitesRes) = coroutineScope {
            val c = async { dataSource.loadMenuCategories(restaurantId) }
            val s = async { dataSource.loadStaffMembers(restaurantId, selectedBranchId) }
            val i = async { dataSource.loadStaffInvitations(restaurantId, selectedBranchId) }
            Triple(c.await(), s.await(), i.await())
        }
        if (!isStillValid(startEpoch, uid)) return
        if (listOf(catsRes, staffRes, invitesRes).any {
                clearOnAuthorizationFailure(it.exceptionOrNull(), startEpoch, uid)
            }) return

        _state.update { current ->
            if (!isStillValid(startEpoch, uid)) return@update current
            current.copy(
                branches = branches,
                selectedBranchId = selectedBranchId,
                selectedTab = if (current.selectedTab in RestaurantRolePermissions.visibleTabs(current.selectedRestaurant?.roleForBranch(selectedBranchId)))
                    current.selectedTab else RestaurantCenterTab.OVERVIEW,
                categories = catsRes.getOrElse { emptyList() }.filter { it.restaurantId == restaurantId },
                staffMembers = staffRes.getOrElse { emptyList() }
                    .filter { it.restaurantId == restaurantId }
                    .mapNotNull(::mapStaffMemberDto),
                staffInvitations = invitesRes.getOrElse { emptyList() }
                    .filter { it.restaurantId == restaurantId }
                    .mapNotNull(::mapInvitationDto),
                isWorking = false,
            )
        }

        if (selectedBranchId != null) {
            loadBranchScope(restaurantId, selectedBranchId, startEpoch, uid)
        }
    }

    private suspend fun loadBranchScope(
        restaurantId: String,
        branchId: String,
        startEpoch: Long,
        uid: String,
    ) {
        val (menuRes, tablesRes, ordersRes) = coroutineScope {
            val m = async { dataSource.loadMenuItemsForBranch(restaurantId, branchId) }
            val t = async { dataSource.loadTables(restaurantId, branchId) }
            val o = async { dataSource.syncBranchOrders(restaurantId, branchId) }
            Triple(m.await(), t.await(), o.await())
        }
        if (!isStillValid(startEpoch, uid)) return
        if (listOf(menuRes, tablesRes, ordersRes).any {
                clearOnAuthorizationFailure(it.exceptionOrNull(), startEpoch, uid)
            }) return

        val syncedPair = ordersRes.getOrNull()
        _state.update { current ->
            if (!isStillValid(startEpoch, uid)) return@update current
            current.copy(
                menuItems = menuRes.getOrElse { emptyList() }.filter { it.restaurantId == restaurantId },
                tables = tablesRes.getOrElse { emptyList() }
                    .filter { it.restaurantId == restaurantId && it.branchId == branchId }
                    .map { dto ->
                        CenterTableUi(
                            id = dto.id,
                            restaurantId = dto.restaurantId,
                            branchId = dto.branchId,
                            tableCode = dto.tableCode,
                            label = dto.label,
                            zone = dto.zone,
                            capacity = dto.capacity,
                            isActive = dto.isActive,
                            activeQrVersion = dto.activeQrVersion,
                            activeQrStatus = dto.activeQrStatus,
                            openSessionId = dto.openSession?.id,
                            openSessionExpiresAt = dto.openSession?.expiresAt,
                            hasSessionCode = dto.openSession?.hasSessionCode ?: false,
                        )
                    },
                orders = RestaurantCenterReducers.filterOrdersForBranch(
                    restaurantId = restaurantId,
                    branchId = branchId,
                    orders = syncedPair?.first.orEmpty(),
                ).distinctBy { it.orderId },
                orderSyncCursor = syncedPair?.second?.nextCursor,
                orderSyncCursorId = syncedPair?.second?.nextCursorId,
                orderHasMore = syncedPair?.second?.hasMore ?: false,
                isWorking = false,
            )
        }
    }

    private fun launchTrackedUpload(
        uid: String,
        target: RestaurantsPendingPickerTarget,
        source: RestaurantsBinaryUploadSource,
        block: suspend (startEpoch: Long) -> String,
    ) {
        if (_state.value.uploadState.isInProgress || inFlightUploadJob?.isActive == true) {
            _state.update {
                it.copy(errorBanner = "An upload is already in progress. Cancel or wait before starting another.")
            }
            return
        }
        try {
            RestaurantsUploadValidator.validateMetadata(target.uploadKind, source)
        } catch (t: Throwable) {
            val mapped = RestaurantsErrorMapper.mapThrowable(t)
            reportUploadValidationError(target, mapped.userMessage)
            return
        }

        val startEpoch = flowEpoch.get()
        uploadCancelledByUser = false
        pendingRetryUpload = null
        _state.update {
            it.copy(
                uploadState = CenterUploadUiState(
                    phase = CenterUploadPhase.UPLOADING,
                    targetKey = target.targetKey,
                    label = target.displayLabel,
                    fileName = source.fileName,
                    mimeType = source.mimeType,
                    byteSize = source.byteSize,
                    statusMessage = "Uploading ${source.fileName}...",
                    errorMessage = null,
                    canRetry = false,
                ),
                errorBanner = null,
                actionFeedback = null,
            )
        }

        inFlightUploadJob = scope.launch {
            try {
                val successMsg = block(startEpoch)
                if (!isStillValid(startEpoch, uid)) return@launch
                pendingRetryUpload = null
                _state.update { current ->
                    if (!isStillValid(startEpoch, uid)) return@update current
                    current.copy(
                        uploadState = CenterUploadUiState(
                            phase = CenterUploadPhase.SUCCEEDED,
                            targetKey = target.targetKey,
                            label = target.displayLabel,
                            fileName = source.fileName,
                            mimeType = source.mimeType,
                            byteSize = source.byteSize,
                            statusMessage = successMsg,
                            errorMessage = null,
                            canRetry = false,
                        ),
                    )
                }
            } catch (ce: CancellationException) {
                if (!isStillValid(startEpoch, uid)) throw ce
                if (uploadCancelledByUser) {
                    _state.update { current ->
                        if (!isStillValid(startEpoch, uid)) return@update current
                        current.copy(
                            uploadState = current.uploadState.copy(
                                phase = CenterUploadPhase.CANCELLED,
                                statusMessage = "Upload cancelled.",
                                errorMessage = null,
                                canRetry = false,
                            ),
                        )
                    }
                }
                throw ce
            } catch (t: Throwable) {
                if (!isStillValid(startEpoch, uid)) return@launch
                if (clearOnAuthorizationFailure(t, startEpoch, uid)) return@launch
                val mapped = RestaurantsErrorMapper.mapThrowable(t)
                val recoverable = isRecoverableUploadFailure(mapped)
                pendingRetryUpload = if (recoverable) {
                    PendingRetryUpload(uid = uid, epoch = startEpoch, target = target, source = source)
                } else {
                    null
                }
                _state.update { current ->
                    if (!isStillValid(startEpoch, uid)) return@update current
                    current.copy(
                        uploadState = CenterUploadUiState(
                            phase = CenterUploadPhase.FAILED,
                            targetKey = target.targetKey,
                            label = target.displayLabel,
                            fileName = source.fileName,
                            mimeType = source.mimeType,
                            byteSize = source.byteSize,
                            statusMessage = null,
                            errorMessage = mapped.userMessage,
                            canRetry = recoverable,
                        ),
                        errorBanner = mapped.userMessage,
                    )
                }
            }
        }
    }

    private fun isRecoverableUploadFailure(failure: RestaurantsFailure): Boolean = when (failure) {
        is RestaurantsFailure.Network,
        is RestaurantsFailure.Server,
        is RestaurantsFailure.RateLimited -> true
        is RestaurantsFailure.Forbidden -> failure.code == "signed_upload_rejected"
        else -> false
    }

    private fun runMutation(
        startEpoch: Long,
        uid: String,
        block: suspend () -> Unit,
    ) {
        _state.update { it.copy(isWorking = true, errorBanner = null, actionFeedback = null) }
        scope.launch {
            try {
                block()
            } catch (t: Throwable) {
                if (!isStillValid(startEpoch, uid)) return@launch
                if (clearOnAuthorizationFailure(t, startEpoch, uid)) return@launch
                val mapped = RestaurantsErrorMapper.mapThrowable(t)
                _state.update { current ->
                    if (!isStillValid(startEpoch, uid)) return@update current
                    current.copy(errorBanner = mapped.userMessage)
                }
            } finally {
                if (isStillValid(startEpoch, uid)) {
                    _state.update { it.copy(isWorking = false) }
                }
            }
        }
    }

    private fun requireEnabledAndUid(): String? {
        val env = environmentProvider()
        if (!env.isRestaurantCenterEnabled) {
            _state.update { it.copy(loadState = RestaurantsLoadState.Disabled, isCenterEnabled = false) }
            return null
        }
        return requireUid()
    }

    private fun clearOnAuthorizationFailure(error: Throwable?, epoch: Long, uid: String): Boolean {
        if (error == null) return false
        val failure = RestaurantsErrorMapper.mapThrowable(error)
        if (failure !is RestaurantsFailure.Unauthenticated && failure !is RestaurantsFailure.Forbidden) return false
        if (!isStillValid(epoch, uid)) return true
        pendingRetryUpload = null
        showLoadFailure(failure, epoch, uid)
        return true
    }

    private fun showLoadFailure(failure: RestaurantsFailure, epoch: Long, uid: String) {
        if (!isStillValid(epoch, uid)) return
        inFlightUploadJob?.cancel()
        pendingRetryUpload = null
        (dataSource as? RemoteRestaurantCenterDataSource)?.clearCachedWorkspace()
        _state.value = RestaurantCenterUiState(
            loadState = RestaurantsLoadState.Error(failure.userMessage),
            isCenterEnabled = true,
            activeAccountUid = uid,
            errorBanner = failure.userMessage,
        )
        flowEpoch.incrementAndGet()
    }

    private fun requireUid(): String? {
        val uid = accountUidProvider()?.takeIf { it.isNotBlank() }
        if (uid == null) {
            onAccountChanged(null)
            return null
        }
        if (uid != _state.value.activeAccountUid) {
            onAccountChanged(uid)
        }
        return uid
    }

    private fun requireActiveSelectedRestaurant(): CenterRestaurantUi? {
        val rest = _state.value.selectedRestaurant
        if (rest == null || !rest.hasActiveRole) {
            _state.update {
                it.copy(errorBanner = "Active restaurant staff role required for this operation.")
            }
            return null
        }
        return rest
    }

    private fun isStillValid(expectedEpoch: Long, expectedUid: String): Boolean {
        val liveUid = accountUidProvider()?.takeIf { it.isNotBlank() }
        return flowEpoch.get() == expectedEpoch && liveUid == expectedUid
    }

    private fun mapStaffMemberDto(dto: StaffMemberSummaryDto): CenterStaffMemberUi? {
        val role = RestaurantStaffRole.fromWireValue(dto.role) ?: return null
        return CenterStaffMemberUi(
            staffMemberId = dto.staffMemberId,
            restaurantId = dto.restaurantId,
            branchId = dto.branchId,
            profileId = dto.profileId,
            displayName = dto.displayName?.takeIf { it.isNotBlank() } ?: dto.profileId.take(10),
            role = role,
            status = dto.status,
        )
    }

    private fun mapInvitationDto(dto: StaffInvitationDto): CenterStaffInvitationUi? {
        val role = RestaurantStaffRole.fromWireValue(dto.role) ?: return null
        return CenterStaffInvitationUi(
            id = dto.id,
            restaurantId = dto.restaurantId,
            branchId = dto.branchId,
            role = role,
            invitedIdentifier = dto.invitedIdentifier.orEmpty(),
            status = dto.status,
            expiresAt = dto.expiresAt,
        )
    }
}

class RestaurantCenterViewModel(
    dataSource: RestaurantCenterDataSource,
    environmentProvider: () -> RestaurantsEnvironment = { RestaurantsConfig.currentEnvironment },
    accountUidProvider: () -> String? = {
        try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
    },
) : ViewModel() {
    val coordinator = RestaurantCenterCoordinator(
        scope = viewModelScope,
        dataSource = dataSource,
        environmentProvider = environmentProvider,
        accountUidProvider = accountUidProvider,
    )

    val state: StateFlow<RestaurantCenterUiState> = coordinator.state
}

internal data class ClassifiedStaffInviteResult(
    val request: StaffInviteRequest? = null,
    val errorMessage: String? = null,
)

private val INVITE_E164_PHONE_REGEX = Regex("^\\+[1-9]\\d{7,14}$")
private val INVITE_UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
private val INVITE_PHONE_ATTEMPT_REGEX = Regex("^\\d[\\d\\s()-]{5,}$")
private val INVITE_ADISCHAT_UID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{2,127}$")
private val INVITE_ASCII_LETTER_REGEX = Regex("[A-Za-z]")

internal fun classifyStaffInviteRequest(
    restaurantId: String,
    branchId: String?,
    roleWireValue: String,
    rawIdentifier: String,
): ClassifiedStaffInviteResult {
    val cleanId = rawIdentifier.trim()
    if (cleanId.isEmpty()) {
        return ClassifiedStaffInviteResult(errorMessage = "Enter the staff member's AdisChat User ID or verified phone number.")
    }
    if (cleanId.contains("@")) {
        return ClassifiedStaffInviteResult(errorMessage = "Email invitations are not supported. Use an AdisChat User ID or verified E.164 phone number.")
    }

    val normalizedPhone = cleanId.replace(Regex("[\\s()-]+"), "")
    if (INVITE_E164_PHONE_REGEX.matches(normalizedPhone)) {
        return ClassifiedStaffInviteResult(
            request = StaffInviteRequest(
                restaurantId = restaurantId,
                branchId = branchId,
                role = roleWireValue,
                invitedIdentifier = normalizedPhone,
                invitedPhone = normalizedPhone,
            ),
        )
    }

    if (INVITE_UUID_REGEX.matches(cleanId)) {
        return ClassifiedStaffInviteResult(
            request = StaffInviteRequest(
                restaurantId = restaurantId,
                branchId = branchId,
                role = roleWireValue,
                invitedIdentifier = cleanId,
                invitedProfileId = cleanId,
            ),
        )
    }

    if (cleanId.startsWith("+") || INVITE_PHONE_ATTEMPT_REGEX.matches(cleanId)) {
        return ClassifiedStaffInviteResult(
            errorMessage = "Enter a valid E.164 phone number (for example, +251911223344) or an AdisChat User ID.",
        )
    }

    if (!cleanId.any { it.isWhitespace() } &&
        INVITE_ADISCHAT_UID_REGEX.matches(cleanId) &&
        INVITE_ASCII_LETTER_REGEX.containsMatchIn(cleanId)
    ) {
        return ClassifiedStaffInviteResult(
            request = StaffInviteRequest(
                restaurantId = restaurantId,
                branchId = branchId,
                role = roleWireValue,
                invitedIdentifier = cleanId,
                invitedAdischatUserId = cleanId,
            ),
        )
    }

    return ClassifiedStaffInviteResult(
        errorMessage = "Enter a valid AdisChat User ID (3-128 safe characters, no spaces), verified E.164 phone, or Profile UUID.",
    )
}
