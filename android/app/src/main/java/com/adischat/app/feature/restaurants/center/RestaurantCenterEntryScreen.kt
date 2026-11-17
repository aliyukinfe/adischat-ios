package com.adischat.app.feature.restaurants.center

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adischat.app.feature.restaurants.data.RestaurantsRepository
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadState
import com.google.firebase.auth.FirebaseAuth

@Composable
fun RestaurantCenterEntryScreen(accountUid: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dataSource = remember { RemoteRestaurantCenterDataSource(RestaurantsRepository()) }
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

    LaunchedEffect(coordinator) {
        coordinator.onAccountChanged(accountUid)
        coordinator.loadCenter()
    }
    DisposableEffect(coordinator) {
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
        val listener = FirebaseAuth.AuthStateListener { current ->
            if (current.currentUser?.uid != accountUid) {
                coordinator.onAccountChanged(null)
                dataSource.clearCachedWorkspace()
            }
        }
        auth?.addAuthStateListener(listener)
        onDispose {
            auth?.removeAuthStateListener(listener)
            coordinator.onAccountChanged(null)
            dataSource.clearCachedWorkspace()
        }
    }

    when {
        state.loadState is RestaurantsLoadState.Content && state.ownedRestaurants.isEmpty() ->
            RestaurantCenterPortalScreen(onBackToProfile = onBack)
        state.activeAccountUid == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = onBack) { Text("Restaurant access unavailable") }
        }
        state.loadState is RestaurantsLoadState.Empty || state.loadState is RestaurantsLoadState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
