package com.adischat.app.feature.restaurants.center

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adischat.app.feature.restaurants.data.RestaurantMediaSlot
import com.adischat.app.feature.restaurants.data.RestaurantsBinaryUploadSource
import com.adischat.app.feature.restaurants.presentation.DineInNoticeBanner
import com.adischat.app.feature.restaurants.presentation.PaymentStatusUi
import com.adischat.app.feature.restaurants.presentation.RestaurantOrderStatus
import com.adischat.app.feature.restaurants.presentation.RestaurantTagPill
import com.adischat.app.feature.restaurants.presentation.RestaurantsDisabledNoticeCard
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadState
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadingSkeletonList
import com.adischat.app.feature.restaurants.presentation.RestaurantsStateMessageCard
import com.adischat.app.feature.restaurants.presentation.RestaurantsTopBar
import com.adischat.app.feature.restaurants.presentation.RestaurantsUiDefaults
import com.adischat.app.feature.restaurants.presentation.RestaurantsUiReducers

/**
 * Isolated offline Restaurant Center UI (Phase R3G).
 * Inaccessible while [com.adischat.app.FeatureFlags.RESTAURANT_CENTER_ENABLED] is false.
 */
@Composable
fun RestaurantCenterScreen(
    state: RestaurantCenterUiState,
    onTabSelected: (RestaurantCenterTab) -> Unit,
    onRestaurantSelected: (String) -> Unit,
    onBranchSelected: (String) -> Unit,
    onOrderFilterSelected: (OrderBoardFilter) -> Unit,
    onTransitionOrder: (orderId: String, target: RestaurantOrderStatus, reason: String?) -> Unit,
    onToggleItemStock: (menuItemId: String, available: Boolean) -> Unit,
    onRotateTableQr: (tableId: String) -> Unit,
    onOpenDiningSession: (tableId: String) -> Unit,
    onCloseDiningSession: (sessionId: String) -> Unit,
    onInviteStaff: (identifier: String, role: RestaurantStaffRole) -> Unit,
    onRevokeStaff: (staffMemberId: String) -> Unit,
    onTogglePublishLive: (Boolean) -> Unit,
    onUpdateSettings: (description: String) -> Unit,
    onSubmitApplication: (applicationId: String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onUploadPickedFile: (RestaurantsPendingPickerTarget, RestaurantsBinaryUploadSource) -> Unit = { _, _ -> },
    onUploadValidationError: (RestaurantsPendingPickerTarget, String) -> Unit = { _, _ -> },
    onPickerCancelled: () -> Unit = {},
    onCancelUpload: () -> Unit = {},
    onRetryUpload: () -> Unit = {},
    onDismissUploadStatus: () -> Unit = {},
) {
    val uploadPicker = rememberRestaurantsUploadPicker(
        onFileSelected = onUploadPickedFile,
        onSelectionFailed = onUploadValidationError,
        onPickerCancelled = onPickerCancelled,
    )
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RestaurantsTopBar(
                title = state.selectedRestaurant?.name ?: "Restaurant Center",
                subtitle = state.activeRole?.label ?: "Partner Operations",
                onBackClick = onBackClick,
                trailingContent = {
                    RestaurantTagPill(
                        label = state.selectedRestaurant?.status?.label ?: "Offline Preview",
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!state.isCenterEnabled || state.loadState is RestaurantsLoadState.Disabled) {
                RestaurantsDisabledNoticeCard(
                    modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                )
            } else if (state.loadState is RestaurantsLoadState.Loading) {
                RestaurantsLoadingSkeletonList(
                    cardCount = 3,
                    modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                ) {
                    val hasPinnedFeedback = !state.errorBanner.isNullOrBlank() ||
                        !state.actionFeedback.isNullOrBlank() ||
                        state.uploadState.phase != CenterUploadPhase.IDLE
                    if (hasPinnedFeedback) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = RestaurantsUiDefaults.ScreenHorizontalPadding,
                                    end = RestaurantsUiDefaults.ScreenHorizontalPadding,
                                    top = 8.dp,
                                ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!state.errorBanner.isNullOrBlank()) {
                                RestaurantsStateMessageCard(
                                    icon = Icons.Rounded.ErrorOutline,
                                    title = "Action Restricted",
                                    message = state.errorBanner,
                                    isError = true,
                                    modifier = Modifier.padding(0.dp),
                                )
                            }

                            if (!state.actionFeedback.isNullOrBlank()) {
                                DineInNoticeBanner(
                                    headline = "Updated",
                                    supportingText = state.actionFeedback,
                                    icon = Icons.Rounded.Info,
                                )
                            }

                            if (state.uploadState.phase != CenterUploadPhase.IDLE) {
                                CenterUploadStatusCard(
                                    uploadState = state.uploadState,
                                    onCancel = onCancelUpload,
                                    onRetry = onRetryUpload,
                                    onDismiss = onDismissUploadStatus,
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = RestaurantsUiDefaults.ScreenHorizontalPadding,
                            end = RestaurantsUiDefaults.ScreenHorizontalPadding,
                            top = 10.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {

                    if (state.ownedRestaurants.size > 1) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.ownedRestaurants, key = { it.id }) { rest ->
                                    FilterChip(
                                        selected = rest.id == state.selectedRestaurantId,
                                        onClick = { onRestaurantSelected(rest.id) },
                                        label = { Text("${rest.name} (${rest.currentUserRole?.label ?: "Staff"})") },
                                    )
                                }
                            }
                        }
                    }

                    if (state.branches.size > 1) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.branches, key = { it.id }) { br ->
                                    FilterChip(
                                        selected = br.id == state.selectedBranchId,
                                        onClick = { onBranchSelected(br.id) },
                                        label = { Text(br.name) },
                                    )
                                }
                            }
                        }
                    }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(RestaurantCenterTab.entries, key = { it.name }) { tab ->
                                FilterChip(
                                    selected = state.selectedTab == tab,
                                    onClick = { onTabSelected(tab) },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    }

                    when (state.selectedTab) {
                        RestaurantCenterTab.OVERVIEW -> {
                            item {
                                CenterOverviewSection(
                                    restaurant = state.selectedRestaurant,
                                    branch = state.selectedBranch,
                                    activeRole = state.activeRole,
                                    activeOrdersCount = state.orders.count { !it.status.isTerminalNegative && it.status != RestaurantOrderStatus.COMPLETED },
                                    onTogglePublishLive = onTogglePublishLive,
                                )
                            }
                        }

                        RestaurantCenterTab.ORDERS -> {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(OrderBoardFilter.entries, key = { it.name }) { f ->
                                        FilterChip(
                                            selected = state.orderFilter == f,
                                            onClick = { onOrderFilterSelected(f) },
                                            label = { Text(f.label) },
                                        )
                                    }
                                }
                            }
                            if (state.filteredOrders.isEmpty()) {
                                item {
                                    RestaurantsStateMessageCard(
                                        icon = Icons.Rounded.Restaurant,
                                        title = "No orders in ${state.orderFilter.label}",
                                        message = "Incoming dine-in orders for this branch will appear here.",
                                        modifier = Modifier.padding(0.dp),
                                    )
                                }
                            } else {
                                items(state.filteredOrders, key = { it.orderId }) { ticket ->
                                    CenterOrderBoardCard(
                                        ticket = ticket,
                                        actorRole = state.activeRole,
                                        onTransition = onTransitionOrder,
                                    )
                                }
                            }
                        }

                        RestaurantCenterTab.MENU -> {
                            items(state.menuItems, key = { it.id }) { item ->
                                CenterMenuRowCard(
                                    item = item,
                                    canToggleStock = RestaurantRolePermissions.canToggleBranchStock(state.activeRole),
                                    canManageMenu = RestaurantRolePermissions.canManageMenu(state.activeRole),
                                    uploadInProgress = state.uploadState.isInProgress,
                                    onToggleStock = { onToggleItemStock(item.id, !item.isAvailableAtBranch) },
                                    onUploadPhoto = {
                                        val restId = state.selectedRestaurantId
                                        if (!restId.isNullOrBlank()) {
                                            uploadPicker.pickMenuItemPhoto(restId, item.id, item.name)
                                        }
                                    },
                                )
                            }
                        }

                        RestaurantCenterTab.TABLES_QR -> {
                            items(state.tables, key = { it.id }) { table ->
                                CenterTableCard(
                                    table = table,
                                    canManageQr = RestaurantRolePermissions.canManageTablesAndQr(state.activeRole),
                                    canOperateSession = RestaurantRolePermissions.canActivateTableSession(state.activeRole),
                                    onRotateQr = { onRotateTableQr(table.id) },
                                    onOpenSession = { onOpenDiningSession(table.id) },
                                    onCloseSession = { sid -> onCloseDiningSession(sid) },
                                )
                            }
                        }

                        RestaurantCenterTab.STAFF -> {
                            item {
                                StaffInviteCard(
                                    actorRole = state.activeRole,
                                    onInvite = onInviteStaff,
                                )
                            }
                            items(state.staffMembers, key = { it.staffMemberId }) { member ->
                                StaffMemberRowCard(
                                    member = member,
                                    canRevoke = RestaurantRolePermissions.canRevokeStaff(state.activeRole, member.role) &&
                                        member.status.equals("active", ignoreCase = true),
                                    onRevoke = { onRevokeStaff(member.staffMemberId) },
                                )
                            }
                        }

                        RestaurantCenterTab.APPLICATION -> {
                            items(state.applications, key = { it.id }) { app ->
                                CenterApplicationCard(
                                    application = app,
                                    uploadInProgress = state.uploadState.isInProgress,
                                    onUploadDocument = { docType, label ->
                                        uploadPicker.pickApplicationDocument(app.id, docType, label)
                                    },
                                    onUploadMedia = { slot ->
                                        uploadPicker.pickApplicationMedia(app, slot)
                                    },
                                    onSubmit = { onSubmitApplication(app.id) },
                                )
                            }
                        }

                        RestaurantCenterTab.SETTINGS -> {
                            item {
                                CenterSettingsCard(
                                    restaurant = state.selectedRestaurant,
                                    branch = state.selectedBranch,
                                    canEdit = RestaurantRolePermissions.canEditSettings(state.activeRole),
                                    uploadInProgress = state.uploadState.isInProgress,
                                    onUploadMedia = { slot, description ->
                                        val restId = state.selectedRestaurant?.id
                                        if (!restId.isNullOrBlank()) {
                                            uploadPicker.pickRestaurantMedia(restId, slot, description)
                                        }
                                    },
                                    onSaveDescription = onUpdateSettings,
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun CenterOverviewSection(
    restaurant: CenterRestaurantUi?,
    branch: CenterBranchUi?,
    activeRole: RestaurantStaffRole?,
    activeOrdersCount: Int,
    onTogglePublishLive: (Boolean) -> Unit,
) {
    if (restaurant == null) {
        RestaurantsStateMessageCard(
            icon = Icons.Rounded.Storefront,
            title = "No restaurant workspace yet",
            message = "Submit an application under the Application tab or accept an individual staff invitation.",
            modifier = Modifier.padding(0.dp),
        )
        return
    }
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(restaurant.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = branch?.let { "${it.name} • ${it.address}" } ?: "No active branch selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RestaurantTagPill(label = restaurant.status.label)
            }
            if (restaurant.status == RestaurantOperationalStatus.SUSPENDED && !restaurant.suspendedReason.isNullOrBlank()) {
                Text(
                    text = "Suspended: ${restaurant.suspendedReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live Dine-In Ordering", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Separate from Admin approval — requires Active status & Owner toggle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = restaurant.isLiveOrderingEnabled,
                    onCheckedChange = onTogglePublishLive,
                    enabled = RestaurantRolePermissions.canPublishLive(activeRole) &&
                        restaurant.status == RestaurantOperationalStatus.ACTIVE,
                )
            }
            Text(
                text = "Active kitchen queue: $activeOrdersCount dine-in ticket(s) • Your role: ${activeRole?.label ?: "None"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CenterOrderBoardCard(
    ticket: CenterOrderTicketUi,
    actorRole: RestaurantStaffRole?,
    onTransition: (orderId: String, target: RestaurantOrderStatus, reason: String?) -> Unit,
) {
    var declineReason by rememberSaveable(ticket.orderId) { mutableStateOf("") }
    var showDeclineField by rememberSaveable(ticket.orderId) { mutableStateOf(false) }
    val nextActions = RestaurantRolePermissions.allowedNextOrderStatuses(actorRole, ticket.status)

    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Order #${ticket.orderNumber} • ${ticket.tableLabel}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = ticket.itemCountLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RestaurantTagPill(label = ticket.status.label)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RestaurantTagPill(
                    label = if (ticket.isUnpaid) "UNPAID • Pay at Restaurant" else "PAID",
                    containerColor = if (ticket.isUnpaid) {
                        RestaurantsUiDefaults.WarmAmberSoft
                    } else {
                        RestaurantsUiDefaults.DineInGreenSoft
                    },
                    contentColor = if (ticket.isUnpaid) {
                        RestaurantsUiDefaults.WarmAmberAccent
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = RestaurantsUiReducers.formatEtbMinor(ticket.totalMinor),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (ticket.status == RestaurantOrderStatus.SERVED && ticket.isUnpaid) {
                Text(
                    text = "Served at table — awaiting counter settlement to complete.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!ticket.terminalReason.isNullOrBlank()) {
                Text(
                    text = "Reason: ${ticket.terminalReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (showDeclineField) {
                OutlinedTextField(
                    value = declineReason,
                    onValueChange = { declineReason = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Required decline reason") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDeclineField = false }) { Text("Back") }
                    Button(
                        enabled = declineReason.isNotBlank(),
                        onClick = {
                            onTransition(ticket.orderId, RestaurantOrderStatus.REJECTED, declineReason)
                            showDeclineField = false
                        },
                    ) { Text("Confirm Decline") }
                }
            } else if (nextActions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    nextActions.forEach { target ->
                        if (target == RestaurantOrderStatus.REJECTED) {
                            OutlinedButton(onClick = { showDeclineField = true }) {
                                Text("Decline")
                            }
                        } else if (target == RestaurantOrderStatus.CANCELLED) {
                            OutlinedButton(
                                onClick = { onTransition(ticket.orderId, target, "Cancelled by staff") },
                            ) {
                                Text("Cancel")
                            }
                        } else {
                            Button(onClick = { onTransition(ticket.orderId, target, null) }) {
                                Text(target.label)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMenuRowCard(
    item: CenterMenuItemUi,
    canToggleStock: Boolean,
    canManageMenu: Boolean,
    uploadInProgress: Boolean,
    onToggleStock: () -> Unit,
    onUploadPhoto: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${item.categoryName} • ${RestaurantsUiReducers.formatEtbMinor(item.branchPriceOverrideMinor ?: item.basePriceMinor)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (item.photoPath.isNullOrBlank()) "No item photo uploaded" else "Item photo attached",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.photoPath.isNullOrBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                Switch(
                    checked = item.isAvailableAtBranch,
                    onCheckedChange = { onToggleStock() },
                    enabled = canToggleStock,
                )
            }
            if (canManageMenu) {
                OutlinedButton(
                    onClick = onUploadPhoto,
                    enabled = !uploadInProgress,
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (item.photoPath.isNullOrBlank()) "Upload Photo" else "Replace Photo")
                }
            }
        }
    }
}

@Composable
private fun CenterTableCard(
    table: CenterTableUi,
    canManageQr: Boolean,
    canOperateSession: Boolean,
    onRotateQr: () -> Unit,
    onOpenSession: () -> Unit,
    onCloseSession: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${table.label} (${table.tableCode})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${table.zone} • Seats ${table.capacity} • QR v${table.activeQrVersion ?: 0}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RestaurantTagPill(label = if (table.openSessionId != null) "Session Open" else "Idle")
            }
            table.latestGeneratedSessionCode?.let { code ->
                Text("Staff Session Code: $code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canManageQr) {
                    OutlinedButton(onClick = onRotateQr) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rotate QR")
                    }
                }
                if (canOperateSession) {
                    val openId = table.openSessionId
                    if (openId == null) {
                        Button(onClick = onOpenSession) { Text("Activate Table") }
                    } else {
                        OutlinedButton(onClick = { onCloseSession(openId) }) { Text("Close Session") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffInviteCard(
    actorRole: RestaurantStaffRole?,
    onInvite: (identifier: String, role: RestaurantStaffRole) -> Unit,
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var selectedRole by rememberSaveable { mutableStateOf(RestaurantStaffRole.WAITER) }
    val invitableRoles = RestaurantStaffRole.entries.filter {
        RestaurantRolePermissions.canInviteStaffRole(actorRole, it)
    }

    if (invitableRoles.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Invite Staff (AdisChat Account)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Staff join with their AdisChat User ID (no phone verification required) or verified phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("AdisChat User ID (preferred) or verified phone") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                invitableRoles.forEach { role ->
                    FilterChip(
                        selected = selectedRole == role,
                        onClick = { selectedRole = role },
                        label = { Text(role.label) },
                    )
                }
            }
            Button(
                enabled = identifier.isNotBlank(),
                onClick = {
                    onInvite(identifier, selectedRole)
                    identifier = ""
                },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Send Individual Invite")
            }
        }
    }
}

@Composable
private fun StaffMemberRowCard(
    member: CenterStaffMemberUi,
    canRevoke: Boolean,
    onRevoke: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(member.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${member.role.label} • ${member.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canRevoke) {
                OutlinedButton(onClick = onRevoke) { Text("Revoke") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CenterApplicationCard(
    application: CenterApplicationUi,
    uploadInProgress: Boolean,
    onUploadDocument: (docType: String, label: String) -> Unit,
    onUploadMedia: (RestaurantMediaSlot) -> Unit,
    onSubmit: () -> Unit,
) {
    val docTypeOptions = listOf(
        "business_license" to "License",
        "tin_certificate" to "TIN",
        "owner_id" to "Owner ID",
        "health_permit" to "Health",
        "other" to "Other",
    )
    var selectedDocType by rememberSaveable(application.id) { mutableStateOf("business_license") }
    val selectedDocLabel = docTypeOptions.firstOrNull { it.first == selectedDocType }?.second ?: "Document"

    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    application.brandName.ifBlank { application.legalName },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                RestaurantTagPill(label = application.status.label)
            }
            Text(
                "Documents attached: ${application.documents.size} • Branches: ${application.branchDraftCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "App Logo: ${if (application.logoPath.isNullOrBlank()) "Not uploaded" else "Uploaded"} • " +
                    "App Cover: ${if (application.coverPath.isNullOrBlank()) "Not uploaded" else "Uploaded"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            application.documents.forEach { doc ->
                Text(
                    text = "• ${doc.docType}: ${doc.fileName} (${RestaurantsPickerMetadataHelper.formatByteSize(doc.byteSize)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!application.reviewNotes.isNullOrBlank()) {
                Text("Reviewer Notes: ${application.reviewNotes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (application.status.isEditableByApplicant) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Text("Business Verification Documents (PDF / JPEG / PNG / WEBP)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    docTypeOptions.forEach { (wireType, label) ->
                        FilterChip(
                            selected = selectedDocType == wireType,
                            onClick = { selectedDocType = wireType },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedButton(
                    onClick = { onUploadDocument(selectedDocType, "$selectedDocLabel Document") },
                    enabled = !uploadInProgress,
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload $selectedDocLabel")
                }
                Text("Application Branding Media (JPEG / PNG / WEBP)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onUploadMedia(RestaurantMediaSlot.LOGO) },
                        enabled = !uploadInProgress,
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (application.logoPath.isNullOrBlank()) "Upload App Logo" else "Replace App Logo")
                    }
                    OutlinedButton(
                        onClick = { onUploadMedia(RestaurantMediaSlot.COVER) },
                        enabled = !uploadInProgress,
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (application.coverPath.isNullOrBlank()) "Upload App Cover" else "Replace App Cover")
                    }
                }
                Button(onClick = onSubmit) { Text("Submit Application for Review") }
            } else {
                Text(
                    text = "Only platform administrators review submitted applications. Approval enables setup prior to going live.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CenterSettingsCard(
    restaurant: CenterRestaurantUi?,
    branch: CenterBranchUi?,
    canEdit: Boolean,
    uploadInProgress: Boolean,
    onUploadMedia: (slot: RestaurantMediaSlot, currentDescription: String) -> Unit,
    onSaveDescription: (String) -> Unit,
) {
    var desc by rememberSaveable(restaurant?.id) { mutableStateOf(restaurant?.description.orEmpty()) }
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Restaurant Profile, Media & Hours", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                enabled = canEdit,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Public description") },
            )
            Text(
                text = "Logo: ${if (restaurant?.logoPath.isNullOrBlank()) "Not uploaded" else "Uploaded"} • " +
                    "Cover: ${if (restaurant?.coverPath.isNullOrBlank()) "Not uploaded" else "Uploaded"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canEdit && restaurant != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onUploadMedia(RestaurantMediaSlot.LOGO, desc) },
                        enabled = !uploadInProgress,
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (restaurant.logoPath.isNullOrBlank()) "Upload Logo" else "Replace Logo")
                    }
                    OutlinedButton(
                        onClick = { onUploadMedia(RestaurantMediaSlot.COVER, desc) },
                        enabled = !uploadInProgress,
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (restaurant.coverPath.isNullOrBlank()) "Upload Cover" else "Replace Cover")
                    }
                }
            }
            if (branch != null && branch.openingHours.isNotEmpty()) {
                branch.openingHours.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.dayLabel, style = MaterialTheme.typography.bodySmall)
                        Text(row.hoursLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (canEdit) {
                Button(onClick = { onSaveDescription(desc) }) { Text("Save Settings") }
            }
        }
    }
}

@Composable
private fun CenterUploadStatusCard(
    uploadState: CenterUploadUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pillLabel = when (uploadState.phase) {
        CenterUploadPhase.IDLE -> ""
        CenterUploadPhase.PREPARING -> "Preparing"
        CenterUploadPhase.UPLOADING -> "Uploading"
        CenterUploadPhase.SUCCEEDED -> "Uploaded"
        CenterUploadPhase.FAILED -> "Failed"
        CenterUploadPhase.CANCELLED -> "Cancelled"
    }
    val sizeLabel = RestaurantsPickerMetadataHelper.formatByteSize(uploadState.byteSize)
    val metaLine = listOfNotNull(
        uploadState.fileName?.takeIf { it.isNotBlank() },
        sizeLabel.takeIf { it.isNotBlank() },
    ).joinToString(" • ")

    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (uploadState.phase == CenterUploadPhase.FAILED) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            },
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uploadState.label ?: "File Upload",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (metaLine.isNotEmpty()) {
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                RestaurantTagPill(label = pillLabel)
            }

            if (uploadState.isInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = uploadState.statusMessage ?: "Uploading file...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
            } else {
                val feedbackText = uploadState.errorMessage ?: uploadState.statusMessage.orEmpty()
                if (feedbackText.isNotEmpty()) {
                    Text(
                        text = feedbackText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uploadState.phase == CenterUploadPhase.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uploadState.phase == CenterUploadPhase.FAILED && uploadState.canRetry) {
                        Button(onClick = onRetry) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry Upload")
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RestaurantCenterPreview() {
    RestaurantCenterScreen(
        state = RestaurantCenterUiState(
            loadState = RestaurantsLoadState.Content,
            isCenterEnabled = true,
            activeAccountUid = "owner-1",
            selectedTab = RestaurantCenterTab.ORDERS,
            ownedRestaurants = listOf(
                CenterRestaurantUi(
                    id = "r1",
                    slug = "habesha-hearth",
                    name = "Habesha Hearth",
                    description = "Traditional grill & clay-pot specialties",
                    status = RestaurantOperationalStatus.ACTIVE,
                    isLiveOrderingEnabled = false,
                    currentUserRole = RestaurantStaffRole.RESTAURANT_OWNER,
                ),
            ),
            selectedRestaurantId = "r1",
            branches = listOf(
                CenterBranchUi("b1", "r1", "BOLE", "Bole Atlas", "Cape Verde St", "Addis Ababa", true, false, true, "active"),
            ),
            selectedBranchId = "b1",
            orders = listOf(
                CenterOrderTicketUi("o1", "104", "r1", "b1", "Table 4", "2x Shekla Tibs", RestaurantOrderStatus.ORDER_SUBMITTED, PaymentStatusUi.UNPAID, 136000L),
            ),
        ),
        onTabSelected = {},
        onRestaurantSelected = {},
        onBranchSelected = {},
        onOrderFilterSelected = {},
        onTransitionOrder = { _, _, _ -> },
        onToggleItemStock = { _, _ -> },
        onRotateTableQr = {},
        onOpenDiningSession = {},
        onCloseDiningSession = {},
        onInviteStaff = { _, _ -> },
        onRevokeStaff = {},
        onTogglePublishLive = {},
        onUpdateSettings = {},
        onSubmitApplication = {},
        onBackClick = {},
    )
}
