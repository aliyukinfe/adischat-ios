package com.adischat.app.feature.restaurants.center

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.adischat.app.feature.restaurants.data.MenuCategoryDto
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
    onActivateRestaurant: () -> Unit = {},
    onRevokeTableQr: (tableId: String) -> Unit = {},
    onUpsertCategory: (name: String, sortOrder: Int, categoryId: String?) -> Unit = { _, _, _ -> },
    onCreateCategoryAndItem: (categoryName: String, name: String, description: String, priceMinor: Long) -> Unit = { _, _, _, _ -> },
    onUpsertMenuItem: (categoryId: String, name: String, description: String, priceMinor: Long, itemId: String?, isActive: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onUpsertTable: (tableCode: String, label: String, zone: String, capacity: Int, tableId: String?, isActive: Boolean) -> Unit = { _, _, _, _, _, _ -> },
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
    var showAddCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<MenuCategoryDto?>(null) }

    var showAddDishDialog by rememberSaveable { mutableStateOf(false) }
    var editingDish by remember { mutableStateOf<CenterMenuItemUi?>(null) }

    var showAddTableDialog by rememberSaveable { mutableStateOf(false) }
    var editingTable by remember { mutableStateOf<CenterTableUi?>(null) }

    var viewingQrTable by remember { mutableStateOf<CenterTableUi?>(null) }
    var selectedMenuCategoryFilter by rememberSaveable { mutableStateOf<String?>(null) }

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
            } else if (state.loadState is RestaurantsLoadState.Error || state.activeRole == null) {
                RestaurantsStateMessageCard(
                    icon = Icons.Rounded.ErrorOutline,
                    title = "Access unavailable",
                    message = state.errorBanner ?: "Restaurant membership could not be verified.",
                    isError = true,
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
                            items(RestaurantRolePermissions.visibleTabs(state.activeRole), key = { it.name }) { tab ->
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
                                    activationReadiness = state.activationReadiness,
                                    liveOrderingReadiness = state.liveOrderingReadiness,
                                    menuItemsCount = state.menuItems.size,
                                    tablesCount = state.tables.size,
                                    staffCount = state.staffMembers.size,
                                    onTabSelected = onTabSelected,
                                    onAddDishClick = {
                                        editingDish = null
                                        showAddDishDialog = true
                                    },
                                    onAddTableClick = {
                                        editingTable = null
                                        showAddTableDialog = true
                                    },
                                    onActivateRestaurant = onActivateRestaurant,
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
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Menu (${state.menuItems.size} dishes)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (RestaurantRolePermissions.canManageMenu(state.activeRole)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = {
                                                editingCategory = null
                                                showAddCategoryDialog = true
                                            }) {
                                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Category")
                                            }
                                            Button(onClick = {
                                                editingDish = null
                                                showAddDishDialog = true
                                            }) {
                                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Add Dish")
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.categories.isNotEmpty()) {
                                item {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        item {
                                            FilterChip(
                                                selected = selectedMenuCategoryFilter == null,
                                                onClick = { selectedMenuCategoryFilter = null },
                                                label = { Text("All (${state.menuItems.size})") },
                                            )
                                        }
                                        items(state.categories, key = { it.id }) { cat ->
                                            FilterChip(
                                                selected = selectedMenuCategoryFilter == cat.id,
                                                onClick = {
                                                    selectedMenuCategoryFilter = if (selectedMenuCategoryFilter == cat.id) null else cat.id
                                                },
                                                label = { Text(cat.name) },
                                            )
                                        }
                                    }
                                }
                            }

                            val displayedItems = if (selectedMenuCategoryFilter != null) {
                                state.menuItems.filter { it.categoryId == selectedMenuCategoryFilter }
                            } else {
                                state.menuItems
                            }

                            if (displayedItems.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.RestaurantMenu,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(40.dp),
                                            )
                                            Text(
                                                text = if (state.menuItems.isEmpty()) "No dishes on your menu yet" else "No dishes in this category",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = if (state.menuItems.isEmpty()) {
                                                    "Add your categories and at least 1 menu item to satisfy activation requirements and prepare for live ordering."
                                                } else {
                                                    "Select another category or add dishes to this category."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                            )
                                            if (RestaurantRolePermissions.canManageMenu(state.activeRole)) {
                                                Button(
                                                    onClick = {
                                                        editingDish = null
                                                        showAddDishDialog = true
                                                    },
                                                ) {
                                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Add Your First Dish")
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(displayedItems, key = { it.id }) { item ->
                                    CenterMenuRowCard(
                                        item = item,
                                        canToggleStock = RestaurantRolePermissions.canToggleBranchStock(state.activeRole),
                                        canManageMenu = RestaurantRolePermissions.canManageMenu(state.activeRole),
                                        uploadInProgress = state.uploadState.isInProgress,
                                        onToggleStock = { onToggleItemStock(item.id, !item.isAvailableAtBranch) },
                                        onEditItem = {
                                            editingDish = item
                                            showAddDishDialog = true
                                        },
                                        onUploadPhoto = {
                                            val restId = state.selectedRestaurantId
                                            if (!restId.isNullOrBlank()) {
                                                uploadPicker.pickMenuItemPhoto(restId, item.id, item.name)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        RestaurantCenterTab.TABLES_QR -> {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(
                                            text = "Dining Tables (${state.tables.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = "${state.selectedBranch?.name ?: "Branch"} • ${state.tables.count { (it.activeQrVersion ?: 0) > 0 }} QR active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (RestaurantRolePermissions.canManageTablesAndQr(state.activeRole)) {
                                        Button(onClick = {
                                            editingTable = null
                                            showAddTableDialog = true
                                        }) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Add Table")
                                        }
                                    }
                                }
                            }

                            if (state.tables.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.QrCode2,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(40.dp),
                                            )
                                            Text(
                                                text = "No tables configured yet",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = "Add dining tables with capacity and labels, then generate printable QR codes for customer ordering. Tables and QR codes can be prepared in advance before activation.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                            )
                                            if (RestaurantRolePermissions.canManageTablesAndQr(state.activeRole)) {
                                                Button(
                                                    onClick = {
                                                        editingTable = null
                                                        showAddTableDialog = true
                                                    },
                                                ) {
                                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Add Your First Table")
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(state.tables, key = { it.id }) { table ->
                                    CenterTableCard(
                                        table = table,
                                        canManageQr = RestaurantRolePermissions.canManageTablesAndQr(state.activeRole),
                                        canOperateSession = RestaurantRolePermissions.canActivateTableSession(state.activeRole),
                                        onRotateQr = { onRotateTableQr(table.id) },
                                        onRevokeQr = { onRevokeTableQr(table.id) },
                                        onViewQr = { viewingQrTable = table },
                                        onEditTable = {
                                            editingTable = table
                                            showAddTableDialog = true
                                        },
                                        onOpenSession = { onOpenDiningSession(table.id) },
                                        onCloseSession = { sid -> onCloseDiningSession(sid) },
                                    )
                                }
                            }
                        }
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

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            initialCategory = editingCategory,
            onDismiss = {
                showAddCategoryDialog = false
                editingCategory = null
            },
            onSave = onUpsertCategory,
        )
    }

    if (showAddDishDialog) {
        AddEditDishDialog(
            initialItem = editingDish,
            categories = state.categories,
            onDismiss = {
                showAddDishDialog = false
                editingDish = null
            },
            onSave = { catId, name, desc, priceMinor, itemId, active ->
                if (catId.startsWith("new:")) {
                    onCreateCategoryAndItem(catId.removePrefix("new:"), name, desc, priceMinor)
                } else {
                    onUpsertMenuItem(catId, name, desc, priceMinor, itemId, active)
                }
            },
        )
    }

    if (showAddTableDialog) {
        AddEditTableDialog(
            initialTable = editingTable,
            onDismiss = {
                showAddTableDialog = false
                editingTable = null
            },
            onSave = onUpsertTable,
        )
    }

    val currentViewingTable = viewingQrTable
    if (currentViewingTable != null) {
        ViewTableQrDialog(
            table = currentViewingTable,
            onDismiss = { viewingQrTable = null },
            onRotateQr = { onRotateTableQr(currentViewingTable.id) },
            onRevokeQr = { onRevokeTableQr(currentViewingTable.id) },
        )
    }
}

@Composable
private fun CenterOverviewSection(
    restaurant: CenterRestaurantUi?,
    branch: CenterBranchUi?,
    activeRole: RestaurantStaffRole?,
    activeOrdersCount: Int,
    activationReadiness: ActivationReadiness,
    liveOrderingReadiness: LiveOrderingReadiness,
    menuItemsCount: Int,
    tablesCount: Int,
    staffCount: Int,
    onTabSelected: (RestaurantCenterTab) -> Unit,
    onAddDishClick: () -> Unit,
    onAddTableClick: () -> Unit,
    onActivateRestaurant: () -> Unit,
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

    if (restaurant.status == RestaurantOperationalStatus.PENDING_SETUP) {
        Surface(
            shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Restaurant Setup — ${restaurant.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Complete initial setup to activate your restaurant on AdisChat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RestaurantTagPill(
                        label = "Pending Setup",
                        containerColor = RestaurantsUiDefaults.WarmAmberSoft,
                        contentColor = RestaurantsUiDefaults.WarmAmberAccent,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Text(
                    text = "A. Restaurant Activation Requirements",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                SetupChecklistRow(
                    isComplete = true,
                    title = "Business Approved",
                    description = "Approved by Platform Super Admin",
                )

                SetupChecklistRow(
                    isComplete = activationReadiness.hasName && activationReadiness.hasDescription,
                    title = "Restaurant Profile",
                    description = "${restaurant.name} • ${if (restaurant.description.isNotBlank()) "Profile complete" else "Description required"}",
                )

                SetupChecklistRow(
                    isComplete = activationReadiness.hasActiveBranch,
                    title = "Main Branch",
                    description = branch?.let { "${it.name} (${it.address})" } ?: "Active branch created",
                )

                SetupChecklistRow(
                    isComplete = menuItemsCount > 0,
                    title = if (menuItemsCount > 0) "Menu Configured" else "Menu (Dishes)",
                    description = if (menuItemsCount > 0) {
                        "$menuItemsCount dish(es) on menu"
                    } else {
                        "Not configured — Add at least 1 dish"
                    },
                    actionLabel = if (menuItemsCount == 0 && RestaurantRolePermissions.canManageMenu(activeRole)) "Add Dish" else null,
                    onActionClick = onAddDishClick,
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onActivateRestaurant,
                            enabled = activationReadiness.isReadyToActivate && RestaurantRolePermissions.canPublishLive(activeRole),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Activate Restaurant")
                        }
                        if (!activationReadiness.isReadyToActivate) {
                            Text(
                                text = "Activation waiting for: ${activationReadiness.pendingRequirements.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(
                                text = "All activation requirements satisfied. Tap to activate.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Text(
                    text = "B. Live Dine-In Ordering Preparation",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SetupChecklistRow(
                    isComplete = tablesCount > 0,
                    title = "Dining Tables",
                    description = if (tablesCount > 0) "$tablesCount table(s) configured" else "Not configured — Add tables",
                    actionLabel = if (tablesCount == 0 && RestaurantRolePermissions.canManageTablesAndQr(activeRole)) "Add Table" else null,
                    onActionClick = onAddTableClick,
                )

                val qrCount = liveOrderingReadiness.hasActiveQr
                SetupChecklistRow(
                    isComplete = qrCount,
                    title = "Table QR Codes",
                    description = if (qrCount) "QR codes generated" else "Not generated — Configure under Tables tab",
                    actionLabel = "Tables & QR",
                    onActionClick = { onTabSelected(RestaurantCenterTab.TABLES_QR) },
                )

                SetupChecklistRow(
                    isComplete = staffCount > 1,
                    title = "Staff Members",
                    description = if (staffCount > 1) "$staffCount staff member(s) assigned" else "Only owner assigned — Invite team under Staff tab",
                    actionLabel = "Staff",
                    onActionClick = { onTabSelected(RestaurantCenterTab.STAFF) },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Live Dine-In Ordering", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Disabled — Requires restaurant activation and at least 1 table with active QR code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = false,
                        onCheckedChange = {},
                        enabled = false,
                    )
                }
            }
        }
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
                        text = if (restaurant.isLiveOrderingEnabled) {
                            "Customer live ordering is active for this restaurant."
                        } else if (!liveOrderingReadiness.isReadyForLiveOrdering) {
                            "Pending: ${liveOrderingReadiness.pendingRequirements.joinToString(", ")}"
                        } else {
                            "Ready to publish live dining."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = restaurant.isLiveOrderingEnabled,
                    onCheckedChange = onTogglePublishLive,
                    enabled = RestaurantRolePermissions.canPublishLive(activeRole) &&
                        restaurant.status == RestaurantOperationalStatus.ACTIVE &&
                        (restaurant.isLiveOrderingEnabled || liveOrderingReadiness.isReadyForLiveOrdering),
                )
            }
            Text(
                text = "Kitchen queue: $activeOrdersCount ticket(s) • Dishes: $menuItemsCount • Tables: $tablesCount • Role: ${activeRole?.label ?: "None"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SetupChecklistRow(
    isComplete: Boolean,
    title: String,
    description: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isComplete) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp).padding(top = 2.dp),
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            OutlinedButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelSmall)
            }
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
    onEditItem: () -> Unit = {},
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEditItem) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Dish")
                    }
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CenterTableCard(
    table: CenterTableUi,
    canManageQr: Boolean,
    canOperateSession: Boolean,
    onRotateQr: () -> Unit,
    onRevokeQr: () -> Unit = {},
    onViewQr: () -> Unit = {},
    onEditTable: () -> Unit = {},
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (canManageQr) {
                    if ((table.activeQrVersion ?: 0) > 0) {
                        Button(onClick = onViewQr) {
                            Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View QR")
                        }
                        OutlinedButton(onClick = onRotateQr) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rotate QR")
                        }
                    } else {
                        Button(onClick = onRotateQr) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Generate QR")
                        }
                    }
                    OutlinedButton(onClick = onEditTable) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
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

@Composable
private fun AddCategoryDialog(
    initialCategory: MenuCategoryDto? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, sortOrder: Int, categoryId: String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialCategory?.name ?: "") }
    var sortOrderText by rememberSaveable { mutableStateOf(initialCategory?.sortOrder?.toString() ?: "0") }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCategory == null) "Add Menu Category" else "Edit Menu Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError != null && it.isNotBlank()) nameError = null
                    },
                    label = { Text("Category Name (e.g. Traditional, Drinks)") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sortOrderText,
                    onValueChange = { sortOrderText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Display Order") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = "Category name is required"
                    } else {
                        val order = sortOrderText.toIntOrNull() ?: 0
                        onSave(name.trim(), order, initialCategory?.id)
                        onDismiss()
                    }
                },
            ) {
                Text(if (initialCategory == null) "Create Category" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddEditDishDialog(
    initialItem: CenterMenuItemUi? = null,
    categories: List<MenuCategoryDto>,
    onDismiss: () -> Unit,
    onSave: (categoryId: String, name: String, description: String, priceMinor: Long, itemId: String?, isActive: Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialItem?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(initialItem?.description ?: "") }
    var priceText by rememberSaveable {
        mutableStateOf(
            initialItem?.let { (it.basePriceMinor / 100.0).toString() } ?: ""
        )
    }
    var selectedCategoryId by rememberSaveable {
        mutableStateOf(
            initialItem?.categoryId ?: categories.firstOrNull()?.id ?: ""
        )
    }
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var isActive by rememberSaveable { mutableStateOf(initialItem?.isActive ?: true) }

    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var priceError by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryError by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialItem == null) "Add Menu Dish" else "Edit Menu Dish",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (categories.isNotEmpty()) {
                    Text("Select Category", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = {
                                    selectedCategoryId = cat.id
                                    categoryError = null
                                },
                                label = { Text(cat.name) },
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = {
                            newCategoryName = it
                            categoryError = null
                        },
                        label = { Text("Initial Category (e.g. Main Dishes)") },
                        singleLine = true,
                        isError = categoryError != null,
                        supportingText = categoryError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError != null && it.isNotBlank()) nameError = null
                    },
                    label = { Text("Dish Name (e.g. Doro Wat)") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = priceText,
                    onValueChange = {
                        priceText = it
                        if (priceError != null) priceError = null
                    },
                    label = { Text("Price in ETB (e.g. 350.00)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = priceError != null,
                    supportingText = priceError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Active on Menu", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val cleanPrice = priceText.toDoubleOrNull()
                    val catId = selectedCategoryId.ifBlank {
                        if (newCategoryName.isNotBlank()) "new:${newCategoryName.trim()}" else ""
                    }

                    var hasError = false
                    if (cleanName.isBlank()) {
                        nameError = "Dish name is required"
                        hasError = true
                    }
                    if (cleanPrice == null || cleanPrice <= 0.0) {
                        priceError = "Enter a valid price greater than 0"
                        hasError = true
                    }
                    if (catId.isBlank()) {
                        categoryError = "Select or enter a category"
                        hasError = true
                    }

                    if (!hasError && cleanPrice != null) {
                        val minorUnits = Math.round(cleanPrice * 100.0)
                        if (minorUnits <= 0L || minorUnits > 100_000_000L) {
                            priceError = "Price must be between 0.01 and 1,000,000 ETB"
                            return@Button
                        }
                        onSave(catId, cleanName, description.trim(), minorUnits, initialItem?.id, isActive)
                        onDismiss()
                    }
                },
            ) {
                Text(if (initialItem == null) "Create Dish" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AddEditTableDialog(
    initialTable: CenterTableUi? = null,
    onDismiss: () -> Unit,
    onSave: (tableCode: String, label: String, zone: String, capacity: Int, tableId: String?, isActive: Boolean) -> Unit,
) {
    var tableCode by rememberSaveable { mutableStateOf(initialTable?.tableCode ?: "") }
    var label by rememberSaveable { mutableStateOf(initialTable?.label ?: "") }
    var zone by rememberSaveable { mutableStateOf(initialTable?.zone ?: "Main Dining") }
    var capacityText by rememberSaveable { mutableStateOf(initialTable?.capacity?.toString() ?: "4") }
    var isActive by rememberSaveable { mutableStateOf(initialTable?.isActive ?: true) }

    var codeError by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialTable == null) "Add Dining Table" else "Edit Dining Table",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = tableCode,
                    onValueChange = {
                        tableCode = it
                        if (codeError != null && it.isNotBlank()) codeError = null
                    },
                    label = { Text("Table Code (e.g. T-01, 1)") },
                    isError = codeError != null,
                    supportingText = codeError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Table Label (e.g. Table 1, Window Booth)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = zone,
                    onValueChange = { zone = it },
                    label = { Text("Zone (e.g. Main Dining, Terrace)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = capacityText,
                    onValueChange = { capacityText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Seating Capacity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Active Table", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanCode = tableCode.trim()
                    val cleanLabel = label.trim().ifBlank { cleanCode }
                    val cap = capacityText.toIntOrNull() ?: 4

                    var hasError = false
                    if (cleanCode.isBlank()) {
                        codeError = "Table code is required"
                        hasError = true
                    }
                    if (!hasError) {
                        onSave(cleanCode, cleanLabel, zone.trim().ifBlank { "Main Dining" }, cap, initialTable?.id, isActive)
                        onDismiss()
                    }
                },
            ) {
                Text(if (initialTable == null) "Create Table" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ViewTableQrDialog(
    table: CenterTableUi,
    onDismiss: () -> Unit,
    onRotateQr: () -> Unit,
    onRevokeQr: () -> Unit,
) {
    val context = LocalContext.current
    val qrVersion = table.activeQrVersion ?: 1
    val qrUrl = table.latestQrUrl ?: "https://adischat.com/d/table-${table.id.take(8)}"
    val qrBitmap = remember(qrUrl) { createQrBitmap(qrUrl, 512) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("${table.label} — QR Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .size(190.dp)
                            .padding(4.dp),
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Scannable QR code for ${table.label}",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Active QR Version: v$qrVersion", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("Zone: ${table.zone} • Capacity: ${table.capacity} seats", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("QR Ordering URL:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(qrUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = "Preparing QR codes in advance allows you to print table standees before opening. Customers cannot open sessions or order until Live Dine-In Ordering is enabled by the owner.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Table ${table.label} Dine-In QR Link")
                            putExtra(Intent.EXTRA_TEXT, "AdisChat Dine-In Table ${table.label}\nZone: ${table.zone}\nSeats: ${table.capacity}\nOrdering URL: $qrUrl")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Table QR"))
                    },
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = {
                        onRevokeQr()
                        onDismiss()
                    },
                ) {
                    Text("Revoke", color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        onRotateQr()
                        onDismiss()
                    },
                ) {
                    Text("Regenerate")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun createQrBitmap(payload: String, size: Int = 512): Bitmap? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }
    }.getOrNull()
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
