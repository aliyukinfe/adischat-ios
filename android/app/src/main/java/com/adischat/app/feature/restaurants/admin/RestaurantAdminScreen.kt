package com.adischat.app.feature.restaurants.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adischat.app.feature.restaurants.center.RestaurantApplicationStatus
import com.adischat.app.feature.restaurants.center.RestaurantOperationalStatus
import com.adischat.app.feature.restaurants.presentation.DineInNoticeBanner
import com.adischat.app.feature.restaurants.presentation.RestaurantTagPill
import com.adischat.app.feature.restaurants.presentation.RestaurantsDisabledNoticeCard
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadState
import com.adischat.app.feature.restaurants.presentation.RestaurantsLoadingSkeletonList
import com.adischat.app.feature.restaurants.presentation.RestaurantsStateMessageCard
import com.adischat.app.feature.restaurants.presentation.RestaurantsTopBar
import com.adischat.app.feature.restaurants.presentation.RestaurantsUiDefaults

/**
 * Isolated offline Platform Admin presentation screen (Phase R3G).
 * Mocked and inaccessible in production while [com.adischat.app.FeatureFlags.RESTAURANT_ADMIN_ENABLED] is false.
 */
@Composable
fun RestaurantAdminScreen(
    state: RestaurantAdminUiState,
    onTabSelected: (RestaurantAdminTab) -> Unit,
    onSelectApplication: (String) -> Unit,
    onInspectDocument: (applicationId: String, documentId: String) -> Unit,
    onStartReview: (applicationId: String) -> Unit,
    onApproveApplication: (applicationId: String, slug: String, notes: String?) -> Unit,
    onRequestChanges: (applicationId: String, notes: String) -> Unit,
    onRejectApplication: (applicationId: String, notes: String) -> Unit,
    onSuspendRestaurant: (restaurantId: String, reason: String) -> Unit,
    onReactivateRestaurant: (restaurantId: String, reason: String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RestaurantsTopBar(
                title = "Platform Restaurant Admin",
                subtitle = "Platform Moderation & Application Review",
                onBackClick = onBackClick,
                trailingContent = {
                    RestaurantTagPill(
                        label = "Super Admin",
                        containerColor = RestaurantsUiDefaults.WarmAmberSoft,
                        contentColor = RestaurantsUiDefaults.WarmAmberAccent,
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
            if (!state.isAdminEnabled || state.loadState is RestaurantsLoadState.Disabled) {
                RestaurantsDisabledNoticeCard(
                    modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                )
            } else if (state.loadState is RestaurantsLoadState.Loading) {
                RestaurantsLoadingSkeletonList(
                    cardCount = 3,
                    modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                )
            } else if (!state.isAuthorizedAdmin) {
                RestaurantsStateMessageCard(
                    icon = Icons.Rounded.AdminPanelSettings,
                    title = "Admin Authorization Required",
                    message = state.errorBanner ?: "Platform Super Admin permissions are required to review restaurant applications.",
                    isError = true,
                    modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                ) {
                    if (!state.errorBanner.isNullOrBlank() || !state.actionNotice.isNullOrBlank()) {
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
                                val errorTitle = when {
                                    state.errorBanner.contains("slug", ignoreCase = true) -> "Invalid Restaurant Slug"
                                    state.errorBanner.contains("license", ignoreCase = true) -> "Missing License Document"
                                    state.errorBanner.contains("self-approval", ignoreCase = true) -> "Self-Approval Forbidden"
                                    state.errorBanner.contains("session", ignoreCase = true) || state.errorBanner.contains("sign in", ignoreCase = true) -> "Session Expired"
                                    state.errorBanner.contains("permission", ignoreCase = true) || state.errorBanner.contains("admin", ignoreCase = true) -> "Unauthorized"
                                    else -> "Admin Action Failed"
                                }
                                RestaurantsStateMessageCard(
                                    icon = Icons.Rounded.ErrorOutline,
                                    title = errorTitle,
                                    message = state.errorBanner,
                                    isError = true,
                                    modifier = Modifier.padding(0.dp),
                                )
                            }

                            if (!state.actionNotice.isNullOrBlank()) {
                                DineInNoticeBanner(
                                    headline = "Admin Action Recorded",
                                    supportingText = state.actionNotice,
                                    icon = Icons.Rounded.Info,
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
                        item {
                            DineInNoticeBanner(
                                headline = "Platform Admin Review Console",
                                supportingText = "Authorized moderation of partner applications and audit logs. Self-approval is strictly forbidden.",
                                icon = Icons.Rounded.AdminPanelSettings,
                            )
                        }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(RestaurantAdminTab.entries, key = { it.name }) { tab ->
                                FilterChip(
                                    selected = state.selectedTab == tab,
                                    onClick = { onTabSelected(tab) },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    }

                    when (state.selectedTab) {
                        RestaurantAdminTab.APPLICATIONS -> {
                            items(state.applications, key = { it.id }) { app ->
                                AdminApplicationQueueCard(
                                    application = app,
                                    isSelected = app.id == state.selectedApplication?.id,
                                    onClick = { onSelectApplication(app.id) },
                                )
                            }
                            state.selectedApplication?.let { selected ->
                                item(key = "detail-${selected.id}") {
                                    AdminApplicationDetailCard(
                                        application = selected,
                                        actorUid = state.activeAccountUid,
                                        isWorking = state.isWorking,
                                        onInspectDocument = { docId -> onInspectDocument(selected.id, docId) },
                                        onStartReview = { onStartReview(selected.id) },
                                        onApprove = { slug, notes -> onApproveApplication(selected.id, slug, notes) },
                                        onRequestChanges = { notes -> onRequestChanges(selected.id, notes) },
                                        onReject = { notes -> onRejectApplication(selected.id, notes) },
                                    )
                                }
                            }
                        }

                        RestaurantAdminTab.RESTAURANTS -> {
                            if (state.restaurants.isEmpty()) {
                                item {
                                    RestaurantsStateMessageCard(
                                        icon = Icons.Rounded.Storefront,
                                        title = "No approved restaurants",
                                        message = "Approved restaurants and branches appear here.",
                                        modifier = Modifier.padding(0.dp),
                                    )
                                }
                            } else {
                                items(state.restaurants, key = { it.id }) { restaurant ->
                                    AdminRestaurantOverviewCard(
                                        restaurant = restaurant,
                                        onSuspend = { reason -> onSuspendRestaurant(restaurant.id, reason) },
                                        onReactivate = { reason -> onReactivateRestaurant(restaurant.id, reason) },
                                    )
                                }
                            }
                        }

                        RestaurantAdminTab.AUDIT_LOGS -> {
                            items(state.auditLogs, key = { it.id }) { entry ->
                                Surface(
                                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(entry.action, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = "Target: ${entry.targetType ?: "-"} ${entry.targetId ?: ""} • Actor: ${entry.actorProfileId ?: "-"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        entry.createdAt?.let {
                                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
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
private fun AdminApplicationQueueCard(
    application: AdminApplicationUi,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(application.brandName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${application.legalName} • TIN ${application.tinNumber.ifBlank { "N/A" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RestaurantTagPill(label = application.status.label)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminApplicationDetailCard(
    application: AdminApplicationUi,
    actorUid: String?,
    isWorking: Boolean,
    onInspectDocument: (documentId: String) -> Unit,
    onStartReview: () -> Unit,
    onApprove: (slug: String, notes: String?) -> Unit,
    onRequestChanges: (notes: String) -> Unit,
    onReject: (notes: String) -> Unit,
) {
    var notes by rememberSaveable(application.id) { mutableStateOf("") }
    var showApproveDialog by rememberSaveable(application.id) { mutableStateOf(false) }
    val isSelfApplicant = application.applicantProfileId == actorUid

    if (showApproveDialog) {
        AdminApproveConfirmationDialog(
            application = application,
            initialNotes = notes,
            isWorking = isWorking,
            onDismiss = { showApproveDialog = false },
            onConfirm = { finalSlug, finalNotes ->
                showApproveDialog = false
                onApprove(finalSlug, finalNotes)
            },
        )
    }

    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Application Inspection: ${application.brandName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Applicant: ${application.applicantProfileId} • Branches: ${application.branchCount} • Draft items: ${application.draftMenuItemsCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isSelfApplicant) {
                Text(
                    text = "Self-review restriction: You cannot approve an application tied to your own account.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Text("Documents (${application.documents.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            application.documents.forEach { doc ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${doc.docType}: ${doc.fileName}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text("${doc.reviewState.label} • ${doc.mimeType}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { onInspectDocument(doc.id) }) {
                        Text("Inspect")
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Review notes / feedback") },
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (application.status == RestaurantApplicationStatus.SUBMITTED) {
                    OutlinedButton(
                        enabled = !isWorking,
                        onClick = onStartReview,
                    ) { Text("Start Review") }
                }
                if (application.status == RestaurantApplicationStatus.SUBMITTED ||
                    application.status == RestaurantApplicationStatus.UNDER_REVIEW
                ) {
                    Button(
                        enabled = !isSelfApplicant && !isWorking,
                        onClick = { showApproveDialog = true },
                    ) {
                        Text("Approve")
                    }
                    OutlinedButton(
                        enabled = !isWorking,
                        onClick = { onRequestChanges(notes) },
                    ) { Text("Request Changes") }
                    OutlinedButton(
                        enabled = !isWorking,
                        onClick = { onReject(notes) },
                    ) { Text("Reject") }
                }
            }
        }
    }
}

@Composable
fun AdminApproveConfirmationDialog(
    application: AdminApplicationUi,
    initialNotes: String,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (slug: String, notes: String?) -> Unit,
) {
    var slug by rememberSaveable(application.id) {
        mutableStateOf(RestaurantSlugHelper.generateSlug(application.brandName, application.legalName))
    }
    var notes by rememberSaveable(application.id) { mutableStateOf(initialNotes) }
    val slugError = RestaurantSlugHelper.getSlugValidationError(slug)
    val isValidSlug = slugError == null

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = {
            Text(
                text = "Approve Restaurant Application",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = application.brandName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (application.legalName.isNotBlank() && application.legalName != application.brandName) {
                            Text(
                                text = "Legal: ${application.legalName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Applicant: ${application.applicantProfileId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val contact = application.contactEmail.ifBlank { application.contactPhone }
                        if (contact.isNotBlank()) {
                            Text(
                                text = "Contact: $contact",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Current Status: ${application.status.label}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                OutlinedTextField(
                    value = slug,
                    onValueChange = { slug = it.lowercase().trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Restaurant URL Slug *") },
                    placeholder = { Text("e.g. lala-habesha") },
                    singleLine = true,
                    isError = !isValidSlug,
                    supportingText = {
                        if (slugError != null) {
                            Text(slugError, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Lowercase letters, numbers, hyphens (3-50 chars)")
                        }
                    },
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Approval notes (optional)") },
                    placeholder = { Text("Notes for the audit log") },
                    maxLines = 3,
                )

                Text(
                    text = "Upon approval, the restaurant will be initialized in 'pending_setup' status with 1 owner membership.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(slug.trim(), notes.ifBlank { null }) },
                enabled = isValidSlug && !isWorking,
            ) {
                if (isWorking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Approving...")
                } else {
                    Text("Confirm Approval")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isWorking,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AdminRestaurantOverviewCard(
    restaurant: AdminRestaurantOverviewUi,
    onSuspend: (reason: String) -> Unit,
    onReactivate: (reason: String) -> Unit,
) {
    var reason by rememberSaveable(restaurant.id) { mutableStateOf("") }
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(restaurant.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${restaurant.branches.size} branch(es) • Live ordering: ${if (restaurant.isLiveOrderingEnabled) "ON" else "OFF"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RestaurantTagPill(label = restaurant.status.label)
            }
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Moderation reason") },
            )
            if (restaurant.status != RestaurantOperationalStatus.SUSPENDED) {
                OutlinedButton(
                    enabled = reason.isNotBlank(),
                    onClick = { onSuspend(reason) },
                ) { Text("Suspend Restaurant") }
            } else {
                Button(
                    enabled = reason.isNotBlank(),
                    onClick = { onReactivate(reason) },
                ) { Text("Reactivate Restaurant") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RestaurantAdminPreview() {
    RestaurantAdminScreen(
        state = RestaurantAdminUiState(
            loadState = RestaurantsLoadState.Content,
            isAdminEnabled = true,
            activeAccountUid = "admin-1",
            isAuthorizedAdmin = true,
            applications = listOf(
                AdminApplicationUi(
                    id = "app-1",
                    applicantProfileId = "owner-1",
                    brandName = "Habesha Hearth",
                    legalName = "Habesha Culinary PLC",
                    contactEmail = "partner@example.invalid",
                    contactPhone = "+251900000001",
                    tinNumber = "0041234567",
                    status = RestaurantApplicationStatus.UNDER_REVIEW,
                    submittedAt = "2026-11-10",
                    branchCount = 2,
                    draftMenuItemsCount = 12,
                    documents = listOf(
                        AdminApplicationDocUi("doc-1", "business_license", "license.pdf", "application/pdf", 240_000L),
                    ),
                ),
            ),
        ),
        onTabSelected = {},
        onSelectApplication = {},
        onInspectDocument = { _, _ -> },
        onStartReview = {},
        onApproveApplication = { _, _, _ -> },
        onRequestChanges = { _, _ -> },
        onRejectApplication = { _, _ -> },
        onSuspendRestaurant = { _, _ -> },
        onReactivateRestaurant = { _, _ -> },
        onBackClick = {},
    )
}
