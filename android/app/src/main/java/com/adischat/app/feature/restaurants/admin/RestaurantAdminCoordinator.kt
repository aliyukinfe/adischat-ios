package com.adischat.app.feature.restaurants.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.feature.restaurants.center.RestaurantApplicationStatus
import com.adischat.app.feature.restaurants.center.RestaurantOperationalStatus
import com.adischat.app.feature.restaurants.data.RestaurantsConfig
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.RestaurantsErrorMapper
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Isolated offline coordinator for Platform Admin moderation & application review.
 * Enforces:
 * - [RestaurantsEnvironment.isAdminEnabled] hard disabled gate
 * - Active admin verification + account-switch state wipe
 * - Strict no-self-approval rule (applicant cannot approve own application)
 * - Approval distinct from operational activation (approved restaurants start pending setup)
 */
class RestaurantAdminCoordinator(
    private val scope: CoroutineScope,
    private val dataSource: RestaurantAdminDataSource,
    private val environmentProvider: () -> RestaurantsEnvironment = { RestaurantsConfig.currentEnvironment },
    private val accountUidProvider: () -> String? = {
        try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
    },
) {
    private val flowEpoch = AtomicLong(0L)
    private var inFlightJob: Job? = null

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<RestaurantAdminUiState> = _state.asStateFlow()

    private fun initialState(): RestaurantAdminUiState {
        val env = environmentProvider()
        return RestaurantAdminUiState(
            loadState = if (env.isAdminEnabled) RestaurantsLoadState.Empty else RestaurantsLoadState.Disabled,
            isAdminEnabled = env.isAdminEnabled,
            activeAccountUid = accountUidProvider()?.takeIf { it.isNotBlank() },
        )
    }

    fun onAccountChanged(newAdisChatUid: String?) {
        val cleanUid = newAdisChatUid?.takeIf { it.isNotBlank() }
        flowEpoch.incrementAndGet()
        inFlightJob?.cancel()
        val env = environmentProvider()
        _state.value = RestaurantAdminUiState(
            loadState = if (env.isAdminEnabled) RestaurantsLoadState.Empty else RestaurantsLoadState.Disabled,
            isAdminEnabled = env.isAdminEnabled,
            activeAccountUid = cleanUid,
            isAuthorizedAdmin = false,
            actionNotice = "Admin session cleared after account change.",
        )
    }

    fun selectTab(tab: RestaurantAdminTab) {
        _state.update { it.copy(selectedTab = tab, errorBanner = null, actionNotice = null) }
    }

    fun selectApplication(applicationId: String) {
        _state.update { it.copy(selectedApplicationId = applicationId, errorBanner = null) }
    }

    fun loadAdminWorkspace() {
        val env = environmentProvider()
        if (!env.isAdminEnabled) {
            _state.update {
                it.copy(loadState = RestaurantsLoadState.Disabled, isAdminEnabled = false, isAuthorizedAdmin = false)
            }
            return
        }
        val uid = requireUid() ?: return
        val startEpoch = flowEpoch.get()
        inFlightJob?.cancel()

        _state.update {
            it.copy(loadState = RestaurantsLoadState.Loading, isAdminEnabled = true, errorBanner = null)
        }

        inFlightJob = scope.launch {
            val adminRes = dataSource.verifyPlatformAdminAccess(uid)
            if (!isStillValid(startEpoch, uid)) return@launch

            val isAdmin = adminRes.getOrElse { err ->
                val f = RestaurantsErrorMapper.mapThrowable(err)
                _state.update { current ->
                    if (!isStillValid(startEpoch, uid)) return@update current
                    current.copy(
                        loadState = RestaurantsLoadState.Error(f.userMessage),
                        isAuthorizedAdmin = false,
                    )
                }
                return@launch
            }

            if (!isAdmin) {
                _state.update { current ->
                    if (!isStillValid(startEpoch, uid)) return@update current
                    current.copy(
                        loadState = RestaurantsLoadState.Error("Platform admin authorization required."),
                        isAuthorizedAdmin = false,
                    )
                }
                return@launch
            }

            val (appsRes, restsRes, logsRes) = coroutineScope {
                val a = async { dataSource.loadApplications() }
                val r = async { dataSource.loadRestaurantsAndBranches() }
                val l = async { dataSource.loadAuditLogs() }
                Triple(a.await(), r.await(), l.await())
            }
            if (!isStillValid(startEpoch, uid)) return@launch

            val apps = appsRes.getOrElse { emptyList() }
            val rests = restsRes.getOrElse { emptyList() }
            val logs = logsRes.getOrElse { emptyList() }.map { dto ->
                AdminAuditEntryUi(
                    id = dto.id,
                    restaurantId = dto.restaurantId,
                    actorProfileId = dto.actorProfileId,
                    action = dto.action,
                    targetType = dto.targetType,
                    targetId = dto.targetId,
                    createdAt = dto.createdAt,
                )
            }

            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    loadState = RestaurantsLoadState.Content,
                    isAuthorizedAdmin = true,
                    applications = apps,
                    selectedApplicationId = current.selectedApplicationId ?: apps.firstOrNull()?.id,
                    restaurants = rests,
                    auditLogs = logs,
                )
            }
        }
    }

    fun startReview(applicationId: String, notes: String? = null) {
        val uid = requireAuthorizedAdminUid() ?: return
        val startEpoch = flowEpoch.get()
        runAdminMutation(startEpoch, uid) {
            val res = dataSource.startReview(applicationId, notes).getOrThrow()
            val nextStatus = RestaurantApplicationStatus.fromWireValue(res.status)
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    applications = current.applications.map {
                        if (it.id == applicationId) it.copy(status = nextStatus) else it
                    },
                    actionNotice = "Application moved to ${nextStatus.label}.",
                )
            }
        }
    }

    fun inspectDocument(applicationId: String, documentId: String) {
        val uid = requireAuthorizedAdminUid() ?: return
        val startEpoch = flowEpoch.get()
        runAdminMutation(startEpoch, uid) {
            val signedUrl = dataSource.inspectDocument(applicationId, documentId).getOrThrow()
            val expiresAt = (System.currentTimeMillis() / 1000L) + 120L
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    applications = current.applications.map { app ->
                        if (app.id == applicationId) {
                            app.copy(
                                documents = app.documents.map { doc ->
                                    if (doc.id == documentId) {
                                        doc.copy(
                                            lastInspectedUrlPreview = signedUrl,
                                            signedUrlExpiresAtEpochSeconds = if (signedUrl != null) expiresAt else null,
                                            reviewState = AdminDocReviewState.VERIFIED,
                                        )
                                    } else {
                                        doc
                                    }
                                },
                            )
                        } else {
                            app
                        }
                    },
                    actionNotice = "Private document inspection authorized (valid 120s).",
                )
            }
        }
    }

    fun approveApplication(applicationId: String, slug: String, notes: String? = null) {
        if (_state.value.isWorking) return
        val uid = requireAuthorizedAdminUid() ?: return
        val app = _state.value.applications.firstOrNull { it.id == applicationId } ?: return

        if (app.applicantProfileId == uid) {
            _state.update {
                it.copy(errorBanner = "Self-approval is forbidden: applicants cannot approve their own restaurant.")
            }
            return
        }
        if (app.status != RestaurantApplicationStatus.SUBMITTED &&
            app.status != RestaurantApplicationStatus.UNDER_REVIEW
        ) {
            _state.update {
                it.copy(errorBanner = "Only Submitted or Under Review applications can be approved.")
            }
            return
        }

        val cleanSlug = slug.trim().lowercase()
        val slugErr = RestaurantSlugHelper.getSlugValidationError(cleanSlug)
        if (slugErr != null) {
            _state.update {
                it.copy(errorBanner = slugErr)
            }
            return
        }

        val startEpoch = flowEpoch.get()
        runAdminMutation(startEpoch, uid) {
            val res = dataSource.approveApplication(applicationId, cleanSlug, notes).getOrThrow()
            val result = res.result
            val refreshedApps = dataSource.loadApplications().getOrElse {
                _state.value.applications.map { existing ->
                    if (existing.id == applicationId) {
                        existing.copy(
                            status = RestaurantApplicationStatus.APPROVED,
                            approvedRestaurantId = result?.restaurantId,
                            reviewNotes = notes,
                        )
                    } else {
                        existing
                    }
                }
            }
            val refreshedRests = dataSource.loadRestaurantsAndBranches().getOrElse { _state.value.restaurants }
            val refreshedLogs = dataSource.loadAuditLogs().getOrElse { emptyList() }.map { dto ->
                AdminAuditEntryUi(dto.id, dto.restaurantId, dto.actorProfileId, dto.action, dto.targetType, dto.targetId, dto.createdAt)
            }
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                val restRef = result?.restaurantId ?: "New Restaurant"
                current.copy(
                    applications = refreshedApps,
                    restaurants = refreshedRests,
                    auditLogs = refreshedLogs,
                    actionNotice = "Restaurant Approved: $restRef. Status: Pending Setup.",
                )
            }
        }
    }

    fun requestChanges(applicationId: String, notes: String) {
        val uid = requireAuthorizedAdminUid() ?: return
        val cleanNotes = notes.trim()
        if (cleanNotes.isEmpty()) {
            _state.update { it.copy(errorBanner = "Feedback notes are required when requesting changes.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runAdminMutation(startEpoch, uid) {
            dataSource.requestChanges(applicationId, cleanNotes).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    applications = current.applications.map {
                        if (it.id == applicationId) {
                            it.copy(status = RestaurantApplicationStatus.CHANGES_REQUESTED, reviewNotes = cleanNotes)
                        } else {
                            it
                        }
                    },
                    actionNotice = "Changes requested from applicant.",
                )
            }
        }
    }

    fun rejectApplication(applicationId: String, notes: String) {
        val uid = requireAuthorizedAdminUid() ?: return
        val cleanNotes = notes.trim()
        if (cleanNotes.isEmpty()) {
            _state.update { it.copy(errorBanner = "A rejection reason is required.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runAdminMutation(startEpoch, uid) {
            dataSource.rejectApplication(applicationId, cleanNotes).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    applications = current.applications.map {
                        if (it.id == applicationId) {
                            it.copy(status = RestaurantApplicationStatus.REJECTED, reviewNotes = cleanNotes)
                        } else {
                            it
                        }
                    },
                    actionNotice = "Application rejected.",
                )
            }
        }
    }

    fun suspendRestaurant(restaurantId: String, reason: String) {
        val uid = requireAuthorizedAdminUid() ?: return
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) {
            _state.update { it.copy(errorBanner = "Suspension reason is required.") }
            return
        }
        val startEpoch = flowEpoch.get()
        runAdminMutation(startEpoch, uid) {
            dataSource.suspendRestaurant(restaurantId, cleanReason).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    restaurants = current.restaurants.map {
                        if (it.id == restaurantId) {
                            it.copy(
                                status = RestaurantOperationalStatus.SUSPENDED,
                                isLiveOrderingEnabled = false,
                                suspendedReason = cleanReason,
                            )
                        } else {
                            it
                        }
                    },
                    actionNotice = "Restaurant suspended and live ordering disabled.",
                )
            }
        }
    }

    fun reactivateRestaurant(restaurantId: String, reason: String) {
        val uid = requireAuthorizedAdminUid() ?: return
        val startEpoch = flowEpoch.get()
        runAdminMutation(startEpoch, uid) {
            dataSource.reactivateRestaurant(restaurantId, reason.trim()).getOrThrow()
            _state.update { current ->
                if (!isStillValid(startEpoch, uid)) return@update current
                current.copy(
                    restaurants = current.restaurants.map {
                        if (it.id == restaurantId) {
                            it.copy(status = RestaurantOperationalStatus.ACTIVE, suspendedReason = null)
                        } else {
                            it
                        }
                    },
                    actionNotice = "Restaurant reactivated.",
                )
            }
        }
    }

    private fun runAdminMutation(
        startEpoch: Long,
        uid: String,
        block: suspend () -> Unit,
    ) {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, errorBanner = null, actionNotice = null) }
        scope.launch {
            try {
                block()
            } catch (t: Throwable) {
                if (!isStillValid(startEpoch, uid)) return@launch
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

    private fun requireAuthorizedAdminUid(): String? {
        val env = environmentProvider()
        if (!env.isAdminEnabled) {
            _state.update {
                it.copy(loadState = RestaurantsLoadState.Disabled, isAdminEnabled = false, isAuthorizedAdmin = false)
            }
            return null
        }
        val uid = requireUid() ?: return null
        if (!_state.value.isAuthorizedAdmin) {
            _state.update { it.copy(errorBanner = "Platform admin authorization required.") }
            return null
        }
        return uid
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

    private fun isStillValid(expectedEpoch: Long, expectedUid: String): Boolean {
        val liveUid = accountUidProvider()?.takeIf { it.isNotBlank() }
        return flowEpoch.get() == expectedEpoch && liveUid == expectedUid
    }
}

class RestaurantAdminViewModel(
    dataSource: RestaurantAdminDataSource,
    environmentProvider: () -> RestaurantsEnvironment = { RestaurantsConfig.currentEnvironment },
    accountUidProvider: () -> String? = {
        try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
    },
) : ViewModel() {
    val coordinator = RestaurantAdminCoordinator(
        scope = viewModelScope,
        dataSource = dataSource,
        environmentProvider = environmentProvider,
        accountUidProvider = accountUidProvider,
    )

    val state: StateFlow<RestaurantAdminUiState> = coordinator.state
}
