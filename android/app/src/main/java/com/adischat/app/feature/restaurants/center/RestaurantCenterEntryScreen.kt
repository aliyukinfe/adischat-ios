package com.adischat.app.feature.restaurants.center

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adischat.app.feature.restaurants.admin.RemoteRestaurantAdminDataSource
import com.adischat.app.feature.restaurants.admin.RestaurantAdminCoordinator
import com.adischat.app.feature.restaurants.admin.RestaurantAdminScreen
import com.adischat.app.feature.restaurants.data.DraftBranchInputDto
import com.adischat.app.feature.restaurants.data.RestaurantsRepository
import com.adischat.app.feature.restaurants.data.SaveApplicationDraftRequest
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadState
import com.google.firebase.auth.FirebaseAuth

@Composable
fun RestaurantCenterEntryScreen(accountUid: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val repository = remember { RestaurantsRepository() }
    val dataSource = remember(repository) { RemoteRestaurantCenterDataSource(repository) }
    val adminDataSource = remember(repository) { RemoteRestaurantAdminDataSource(repository) }

    // Platform Super Admin verification state:
    // null = checking am-i-admin endpoint, true = authorized Platform Super Admin, false = merchant/applicant
    var isPlatformAdmin by remember(accountUid) { mutableStateOf<Boolean?>(null) }

    val adminCoordinator = remember(adminDataSource, accountUid) {
        RestaurantAdminCoordinator(
            scope = scope,
            dataSource = adminDataSource,
            accountUidProvider = {
                accountUid.takeIf {
                    it.isNotBlank() && runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() == it
                }
            },
        )
    }
    val adminState by adminCoordinator.state.collectAsStateWithLifecycle()

    val coordinator = remember(dataSource, accountUid) {
        RestaurantCenterCoordinator(
            scope = scope,
            dataSource = dataSource,
            accountUidProvider = {
                accountUid.takeIf {
                    it.isNotBlank() && runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() == it
                }
            },
        )
    }
    val state by coordinator.state.collectAsStateWithLifecycle()

    LaunchedEffect(accountUid) {
        if (accountUid.isBlank()) {
            isPlatformAdmin = false
            return@LaunchedEffect
        }
        // Check Platform Super Admin status through authenticated am-i-admin endpoint
        repository.amIAdmin()
            .onSuccess { res ->
                if (res.ok && res.isPlatformAdmin) {
                    isPlatformAdmin = true
                    adminCoordinator.onAccountChanged(accountUid)
                    adminCoordinator.loadAdminWorkspace()
                } else {
                    isPlatformAdmin = false
                    coordinator.onAccountChanged(accountUid)
                    coordinator.loadCenter()
                }
            }
            .onFailure {
                // Non-admin accounts (or failed am-i-admin check) route to merchant/applicant flow
                isPlatformAdmin = false
                coordinator.onAccountChanged(accountUid)
                coordinator.loadCenter()
            }
    }

    DisposableEffect(coordinator, adminCoordinator, accountUid) {
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
        val listener = FirebaseAuth.AuthStateListener { current ->
            if (current.currentUser?.uid != accountUid) {
                isPlatformAdmin = null
                coordinator.onAccountChanged(null)
                adminCoordinator.onAccountChanged(null)
                dataSource.clearCachedWorkspace()
            }
        }
        auth?.addAuthStateListener(listener)
        onDispose {
            auth?.removeAuthStateListener(listener)
            coordinator.onAccountChanged(null)
            adminCoordinator.onAccountChanged(null)
            dataSource.clearCachedWorkspace()
        }
    }

    when {
        // While checking Platform Super Admin status
        isPlatformAdmin == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // Verified Platform Super Admin -> Route to native Restaurant Admin console
        isPlatformAdmin == true -> {
            RestaurantAdminScreen(
                state = adminState,
                onTabSelected = adminCoordinator::selectTab,
                onSelectApplication = adminCoordinator::selectApplication,
                onInspectDocument = adminCoordinator::inspectDocument,
                onStartReview = adminCoordinator::startReview,
                onApproveApplication = { appId, notes ->
                    adminCoordinator.approveApplication(appId, notes = notes)
                },
                onRequestChanges = adminCoordinator::requestChanges,
                onRejectApplication = adminCoordinator::rejectApplication,
                onSuspendRestaurant = adminCoordinator::suspendRestaurant,
                onReactivateRestaurant = adminCoordinator::reactivateRestaurant,
                onBackClick = onBack,
            )
        }

        // Ordinary authenticated user / Applicant / Owner / Staff
        else -> {
            when {
                state.activeAccountUid == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = onBack) { Text("Restaurant access unavailable") }
                }
                state.loadState is RestaurantsLoadState.Empty || state.loadState is RestaurantsLoadState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.loadState is RestaurantsLoadState.Content && state.ownedRestaurants.isEmpty() ->
                    RestaurantCenterPortalScreen(
                        onBackToProfile = onBack,
                        existingApplication = state.applications.firstOrNull(),
                        onSubmitApplication = { draft ->
                            val branches = if (draft.primaryBranchName.isNotBlank() || draft.streetAddress.isNotBlank()) {
                                listOf(
                                    DraftBranchInputDto(
                                        code = "MAIN",
                                        name = draft.primaryBranchName.ifBlank { draft.brandName.ifBlank { "Main Branch" } },
                                        address = draft.streetAddress.ifBlank { "Primary Location" },
                                        city = draft.cityAndArea.takeIf { it.isNotBlank() },
                                    )
                                )
                            } else null

                            val req = SaveApplicationDraftRequest(
                                applicationId = draft.draftId.takeIf { it.startsWith("app-") || it.length > 20 },
                                legalName = draft.legalName,
                                brandName = draft.brandName,
                                description = draft.description,
                                businessType = "RESTAURANT",
                                contactPhone = draft.contactPhone,
                                contactEmail = draft.contactEmail,
                                tinNumber = draft.registrationNumber,
                                branches = branches,
                            )
                            coordinator.submitDraftApplication(
                                draft = req,
                                onSuccess = {
                                    coordinator.loadCenter()
                                },
                            )
                        },
                    )
                else -> RestaurantCenterScreen(
                    state = state,
                    onTabSelected = coordinator::selectTab,
                    onRestaurantSelected = coordinator::selectRestaurant,
                    onBranchSelected = coordinator::selectBranch,
                    onOrderFilterSelected = coordinator::selectOrderFilter,
                    onTransitionOrder = coordinator::transitionOrderStatus,
                    onToggleItemStock = coordinator::toggleMenuItemAvailability,
                    onRotateTableQr = coordinator::rotateTableQr,
                    onOpenDiningSession = coordinator::openDiningSession,
                    onCloseDiningSession = coordinator::closeDiningSession,
                    onInviteStaff = { identifier, role -> coordinator.inviteStaffMember(identifier, role) },
                    onRevokeStaff = coordinator::revokeStaffMember,
                    onTogglePublishLive = coordinator::togglePublishLive,
                    onUpdateSettings = { coordinator.updateRestaurantSettings(it) },
                    onSubmitApplication = coordinator::submitApplication,
                    onBackClick = onBack,
                    onUploadPickedFile = coordinator::handlePickedUpload,
                    onUploadValidationError = coordinator::reportUploadValidationError,
                    onPickerCancelled = coordinator::reportPickerCancelled,
                    onCancelUpload = coordinator::cancelActiveUpload,
                    onRetryUpload = coordinator::retryFailedUpload,
                    onDismissUploadStatus = coordinator::dismissUploadStatus,
                )
            }
        }
    }
}
