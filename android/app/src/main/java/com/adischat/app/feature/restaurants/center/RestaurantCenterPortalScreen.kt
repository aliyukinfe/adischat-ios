package com.adischat.app.feature.restaurants.center

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.data.RestaurantMediaSlot
import com.adischat.app.feature.restaurants.data.RestaurantsConfig
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.presentation.DineInNoticeBanner
import com.adischat.app.feature.restaurants.presentation.RestaurantTagPill
import com.adischat.app.feature.restaurants.presentation.RestaurantsAppIntegrationGuards
import com.adischat.app.feature.restaurants.presentation.RestaurantsDisabledNoticeCard
import com.adischat.app.feature.restaurants.presentation.RestaurantsLightTheme
import com.adischat.app.feature.restaurants.presentation.RestaurantsSystemBarAppearance
import com.adischat.app.feature.restaurants.presentation.RestaurantsTopBar
import com.adischat.app.feature.restaurants.presentation.RestaurantsUiDefaults
import com.google.firebase.auth.FirebaseAuth

/**
 * Native Compose Restaurant Center hub & 5-step Partner Application workflow (Phase R4A).
 *
 * Strictly scoped to application drafting & partner readiness overview:
 * - Application drafting preserves state across all 5 steps locally.
 * - Management sections (Profile, Branches, Menu, Staff, Orders) are read-only placeholders.
 * - Submission blocks honestly when the Restaurants backend is not connected and never fabricates approval.
 */
@Composable
fun RestaurantCenterPortalScreen(
    onBackToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    existingApplication: CenterApplicationUi? = null,
    onSubmitApplication: ((RestaurantApplicationDraftUiState) -> Unit)? = null,
    environment: RestaurantsEnvironment = remember { RestaurantsConfig.currentEnvironment },
) {
    RestaurantsSystemBarAppearance(useDarkIcons = true)
    RestaurantsLightTheme {
        val extColors = RestaurantsLightTheme.colors
        val enabled = FeatureFlags.RESTAURANT_CENTER_ENABLED &&
            RestaurantsAppIntegrationGuards.isRestaurantCenterEntryAllowed(environment)

        val authenticatedUid = remember { resolveAuthenticatedFirebaseUidOrNull() }
        val initialDraft = remember(existingApplication, authenticatedUid) {
            if (existingApplication != null) {
                RestaurantApplicationDraftUiState(
                    draftId = existingApplication.id,
                    applicantProfileId = existingApplication.applicantProfileId,
                    status = existingApplication.status,
                    legalName = existingApplication.legalName,
                    brandName = existingApplication.brandName,
                    description = existingApplication.description,
                    contactPhone = existingApplication.contactPhone,
                    contactEmail = existingApplication.contactEmail,
                    registrationNumber = existingApplication.tinNumber,
                    reviewNotes = existingApplication.reviewNotes,
                    hasLocalDraftSave = true,
                )
            } else {
                RestaurantApplicationDraftUiState(
                    applicantProfileId = authenticatedUid,
                )
            }
        }
        var draftState by remember(initialDraft) {
            mutableStateOf(initialDraft)
        }
        var isWizardOpen by remember { mutableStateOf(false) }

        val handleBack: () -> Unit = {
            if (isWizardOpen) {
                isWizardOpen = false
            } else {
                onBackToProfile()
            }
        }
        BackHandler(onBack = handleBack)

        val uploadPicker = rememberRestaurantsUploadPicker(
            onFileSelected = { target, source ->
                when (target) {
                    is RestaurantsPendingPickerTarget.ApplicationMedia -> {
                        val attachment = RestaurantApplicationAttachmentHelper.fromBinaryUploadSource(
                            localId = "media-${target.slot.name.lowercase()}",
                            docType = target.slot.name,
                            label = if (target.slot == RestaurantMediaSlot.LOGO) "Restaurant Logo" else "Cover Photo",
                            source = source,
                        )
                        draftState = RestaurantApplicationReducer.updateField(draftState) { current ->
                            when (target.slot) {
                                RestaurantMediaSlot.LOGO -> current.copy(logoAttachment = attachment)
                                RestaurantMediaSlot.COVER -> current.copy(coverAttachment = attachment)
                            }
                        }
                    }

                    is RestaurantsPendingPickerTarget.ApplicationDocument -> {
                        val attachment = RestaurantApplicationAttachmentHelper.fromBinaryUploadSource(
                            localId = "doc-${System.currentTimeMillis()}",
                            docType = target.docType,
                            label = target.label,
                            source = source,
                        )
                        draftState = if (target.docType == DOC_TYPE_BUSINESS_LICENSE) {
                            RestaurantApplicationReducer.updateField(
                                state = draftState,
                                clearKeys = setOf(ApplicationFieldKey.BUSINESS_LICENSE),
                            ) { it.copy(businessLicenseDoc = attachment) }
                        } else {
                            RestaurantApplicationReducer.updateField(draftState) { current ->
                                val filtered = current.supportingDocs.filterNot {
                                    it.fileName.equals(attachment.fileName, ignoreCase = true)
                                }
                                current.copy(supportingDocs = filtered + attachment)
                            }
                        }
                    }

                    else -> Unit
                }
            },
            onSelectionFailed = { _, message ->
                draftState = draftState.copy(
                    statusBannerMessage = message,
                    isStatusBannerError = true,
                )
            },
        )

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = extColors.mainBackground,
            topBar = {
                RestaurantsTopBar(
                    title = if (isWizardOpen) "Restaurant Application" else "Restaurant Center",
                    subtitle = when {
                        !enabled -> "Unavailable"
                        isWizardOpen -> "Step ${draftState.currentStep.stepNumber} of 5 · ${draftState.currentStep.fullTitle}"
                        else -> "Partner onboarding & readiness"
                    },
                    onBackClick = handleBack,
                    trailingContent = if (enabled) {
                        {
                            RestaurantTagPill(
                                label = draftState.status.label,
                                containerColor = applicationStatusContainerColor(draftState.status),
                                contentColor = applicationStatusContentColor(draftState.status),
                            )
                        }
                    } else {
                        null
                    },
                )
            },
            bottomBar = {
                if (enabled && isWizardOpen) {
                    ApplicationWizardBottomBar(
                        draft = draftState,
                        canSubmitAttempt = draftState.currentStep == RestaurantApplicationStep.REVIEW,
                        onPreviousClick = {
                            draftState = RestaurantApplicationReducer.previousStep(draftState)
                        },
                        onSaveDraftClick = {
                            draftState = RestaurantApplicationReducer.saveLocalDraft(
                                state = draftState,
                                nowEpochMs = System.currentTimeMillis(),
                            )
                        },
                        onNextClick = {
                            draftState = RestaurantApplicationReducer.nextStep(draftState)
                        },
                        onSubmitClick = {
                            draftState = RestaurantApplicationReducer.attemptSubmit(
                                state = draftState,
                                authenticatedUid = authenticatedUid,
                                environment = environment,
                                nowEpochMs = System.currentTimeMillis(),
                            )
                            if (!draftState.isStatusBannerError && onSubmitApplication != null) {
                                onSubmitApplication(draftState)
                                isWizardOpen = false
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (!enabled) {
                    RestaurantsDisabledNoticeCard(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                } else if (!isWizardOpen) {
                    RestaurantCenterHubOverview(
                        draft = draftState,
                        environment = environment,
                        authenticatedUid = authenticatedUid,
                        onOpenApplication = { startAtReview ->
                            if (startAtReview && draftState.hasLocalDraftSave) {
                                draftState = RestaurantApplicationReducer.selectStep(
                                    draftState,
                                    draftState.currentStep,
                                )
                            }
                            isWizardOpen = true
                        },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                } else {
                    RestaurantApplicationWizardContent(
                        draft = draftState,
                        environment = environment,
                        uploadPicker = uploadPicker,
                        onSelectStep = { step ->
                            draftState = RestaurantApplicationReducer.selectStep(draftState, step)
                        },
                        onDraftChanged = { updated -> draftState = updated },
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun RestaurantCenterHubOverview(
    draft: RestaurantApplicationDraftUiState,
    environment: RestaurantsEnvironment,
    authenticatedUid: String?,
    onOpenApplication: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extColors = RestaurantsLightTheme.colors
    val scrollState = rememberScrollState()
    val hasStartedDraft = draft.hasLocalDraftSave ||
        draft.legalName.isNotBlank() ||
        draft.brandName.isNotBlank() ||
        draft.cityAndArea.isNotBlank()

    val managementCards = remember(draft.status) {
        RestaurantApplicationGuards.buildManagementPlaceholders(draft.status)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = RestaurantsUiDefaults.ScreenHorizontalPadding, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackendConnectionNoticeCard(
            environment = environment,
            authenticatedUid = authenticatedUid,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
            color = extColors.cardSurface,
            border = BorderStroke(1.dp, extColors.cardBorder),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = if (draft.brandName.isNotBlank()) {
                                draft.brandName
                            } else {
                                "Apply to Join AdisChat Restaurants"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = extColors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (draft.status == RestaurantApplicationStatus.SUBMITTED || draft.status == RestaurantApplicationStatus.UNDER_REVIEW) {
                                "Your application has been submitted and is currently pending platform review."
                            } else if (hasStartedDraft) {
                                "Step ${draft.currentStep.stepNumber} of 5 · Application draft ready"
                            } else {
                                "Prepare your restaurant onboarding application across 5 guided steps."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = extColors.secondaryText,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    RestaurantTagPill(
                        label = draft.status.label,
                        containerColor = applicationStatusContainerColor(draft.status),
                        contentColor = applicationStatusContentColor(draft.status),
                    )
                }

                ApplicationStatusTimelineStrip(currentStatus = draft.status)

                if (!draft.reviewNotes.isNullOrBlank()) {
                    DineInNoticeBanner(
                        headline = "Platform Review Notes",
                        supportingText = draft.reviewNotes,
                        icon = Icons.Rounded.Info,
                    )
                }

                DineInNoticeBanner(
                    headline = "Independent Platform Approval",
                    supportingText = "Only authorized AdisChat platform admins can approve or reject applications. Restaurant owners cannot self-approve.",
                    icon = Icons.Rounded.VerifiedUser,
                )

                Button(
                    onClick = { onOpenApplication(hasStartedDraft) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extColors.brandGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = if (hasStartedDraft) Icons.Rounded.Edit else Icons.Rounded.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (draft.status) {
                            RestaurantApplicationStatus.SUBMITTED,
                            RestaurantApplicationStatus.UNDER_REVIEW -> "Application Pending Review"
                            RestaurantApplicationStatus.CHANGES_REQUESTED -> "Review Notes & Update"
                            RestaurantApplicationStatus.APPROVED -> "Application Approved"
                            RestaurantApplicationStatus.REJECTED -> "Application Rejected"
                            RestaurantApplicationStatus.DRAFT -> if (hasStartedDraft) "Continue Application Draft" else "Start Restaurant Application"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Partner Management Workspace",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = extColors.primaryText,
            )
            Text(
                text = "Application drafting is enabled now. Management modules unlock after backend connection and admin approval.",
                style = MaterialTheme.typography.bodySmall,
                color = extColors.secondaryText,
            )
            Spacer(modifier = Modifier.height(2.dp))
            managementCards.forEach { card ->
                ManagementPlaceholderCard(item = card)
            }
        }
    }
}

@Composable
private fun BackendConnectionNoticeCard(
    environment: RestaurantsEnvironment,
    authenticatedUid: String?,
) {
    val extColors = RestaurantsLightTheme.colors
    val isConnected = environment.isStagingConnected()
    val bannerContainer = if (isConnected) extColors.brandGreenSoft else extColors.qaNoticeBackground
    val bannerBorder = if (isConnected) extColors.brandGreenBorder else extColors.qaNoticeBorder
    val bannerContent = if (isConnected) extColors.brandGreenDeep else extColors.qaNoticeText

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = bannerContainer,
        border = BorderStroke(1.dp, bannerBorder),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Rounded.VerifiedUser else Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = bannerContent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (isConnected) {
                        "Restaurants Service Configured (Local Foundation Phase)"
                    } else {
                        "Backend Not Connected · Local Draft Mode"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = bannerContent,
                )
                Text(
                    text = if (authenticatedUid.isNullOrBlank()) {
                        "You can draft details locally now; sign in with an AdisChat account and connect the dedicated Restaurants backend before submitting."
                    } else {
                        "Signed in account ready for future bridge · Drafts save locally until live submission & document storage are enabled."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = extColors.primaryText,
                )
            }
        }
    }
}

@Composable
private fun ApplicationStatusTimelineStrip(
    currentStatus: RestaurantApplicationStatus,
) {
    val extColors = RestaurantsLightTheme.colors
    val stageList = RestaurantApplicationCatalog.lifecycleOrder
    val activeIndex = when (currentStatus) {
        RestaurantApplicationStatus.DRAFT -> 0
        RestaurantApplicationStatus.SUBMITTED -> 1
        RestaurantApplicationStatus.UNDER_REVIEW,
        RestaurantApplicationStatus.CHANGES_REQUESTED,
        -> 2
        RestaurantApplicationStatus.APPROVED,
        RestaurantApplicationStatus.REJECTED,
        -> 3
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            stageList.forEachIndexed { index, status ->
                val isCompletedOrCurrent = index <= activeIndex
                val isErrorTerminal = index == 3 && currentStatus == RestaurantApplicationStatus.REJECTED
                val pillBg = when {
                    isErrorTerminal -> extColors.errorSoft
                    isCompletedOrCurrent -> extColors.brandGreenSoft
                    else -> extColors.mutedSurface
                }
                val pillBorder = when {
                    isErrorTerminal -> extColors.error.copy(alpha = 0.45f)
                    isCompletedOrCurrent -> extColors.brandGreenBorder
                    else -> extColors.cardBorder
                }
                val textColor = when {
                    isErrorTerminal -> extColors.error
                    isCompletedOrCurrent -> extColors.brandGreenDeep
                    else -> extColors.secondaryText
                }
                val label = if (index == 2 && currentStatus == RestaurantApplicationStatus.CHANGES_REQUESTED) {
                    "Changes"
                } else if (index == 3 && currentStatus == RestaurantApplicationStatus.REJECTED) {
                    "Rejected"
                } else {
                    status.label
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = pillBg,
                    border = BorderStroke(1.dp, pillBorder),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagementPlaceholderCard(
    item: RestaurantManagementPlaceholderUi,
) {
    val extColors = RestaurantsLightTheme.colors
    val icon = when (item.feature) {
        RestaurantManagementFeature.RESTAURANT_PROFILE -> Icons.Rounded.Storefront
        RestaurantManagementFeature.BRANCHES -> Icons.Rounded.LocationOn
        RestaurantManagementFeature.MENU -> Icons.Rounded.RestaurantMenu
        RestaurantManagementFeature.STAFF -> Icons.Rounded.Group
        RestaurantManagementFeature.ORDERS -> Icons.Rounded.ReceiptLong
    }
    val badgeBg = if (item.badge == ManagementPlaceholderBadge.PENDING_APPROVAL) {
        extColors.qaNoticeBackground
    } else {
        extColors.mutedSurface
    }
    val badgeFg = if (item.badge == ManagementPlaceholderBadge.PENDING_APPROVAL) {
        extColors.qaNoticeText
    } else {
        extColors.secondaryText
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = extColors.cardSurface,
        border = BorderStroke(1.dp, extColors.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = extColors.sidebarBackground,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = extColors.secondaryText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = extColors.primaryText,
                    )
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = extColors.tertiaryText,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = extColors.secondaryText,
                )
            }
            RestaurantTagPill(
                label = item.badge.label,
                containerColor = badgeBg,
                contentColor = badgeFg,
            )
        }
    }
}

@Composable
private fun RestaurantApplicationWizardContent(
    draft: RestaurantApplicationDraftUiState,
    environment: RestaurantsEnvironment,
    uploadPicker: RestaurantsUploadPickerHandle,
    onSelectStep: (RestaurantApplicationStep) -> Unit,
    onDraftChanged: (RestaurantApplicationDraftUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extColors = RestaurantsLightTheme.colors
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = RestaurantsUiDefaults.ScreenHorizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ApplicationStepHeaderBar(
            draft = draft,
            onSelectStep = onSelectStep,
        )

        if (!draft.statusBannerMessage.isNullOrBlank()) {
            WizardFeedbackBanner(
                message = draft.statusBannerMessage,
                isError = draft.isStatusBannerError,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
            color = extColors.cardSurface,
            border = BorderStroke(1.dp, extColors.cardBorder),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${draft.currentStep.stepNumber}. ${draft.currentStep.fullTitle}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = extColors.primaryText,
                    )
                    Text(
                        text = draft.currentStep.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = extColors.secondaryText,
                    )
                }
                HorizontalDivider(color = extColors.divider)

                when (draft.currentStep) {
                    RestaurantApplicationStep.BUSINESS_INFO ->
                        Step1BusinessInfoForm(draft = draft, onDraftChanged = onDraftChanged)

                    RestaurantApplicationStep.LOCATION ->
                        Step2LocationForm(draft = draft, onDraftChanged = onDraftChanged)

                    RestaurantApplicationStep.RESTAURANT_DETAILS ->
                        Step3RestaurantDetailsForm(
                            draft = draft,
                            uploadPicker = uploadPicker,
                            onDraftChanged = onDraftChanged,
                        )

                    RestaurantApplicationStep.DOCUMENTS ->
                        Step4DocumentsForm(
                            draft = draft,
                            uploadPicker = uploadPicker,
                            onDraftChanged = onDraftChanged,
                        )

                    RestaurantApplicationStep.REVIEW ->
                        Step5ReviewSummaryForm(
                            draft = draft,
                            environment = environment,
                            onJumpToStep = onSelectStep,
                            onDraftChanged = onDraftChanged,
                        )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ApplicationStepHeaderBar(
    draft: RestaurantApplicationDraftUiState,
    onSelectStep: (RestaurantApplicationStep) -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RestaurantApplicationStep.entries.forEach { step ->
            val isActive = draft.currentStep == step
            val isComplete = RestaurantApplicationValidator.isStepCompleted(step, draft)
            val bg = when {
                isActive -> extColors.brandGreen
                isComplete -> extColors.brandGreenSoft
                else -> extColors.mutedSurface
            }
            val fg = when {
                isActive -> Color.White
                isComplete -> extColors.brandGreenDeep
                else -> extColors.secondaryText
            }
            val border = when {
                isActive -> extColors.brandGreen
                isComplete -> extColors.brandGreenBorder
                else -> extColors.cardBorder
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectStep(step) },
                shape = RoundedCornerShape(10.dp),
                color = bg,
                border = BorderStroke(1.dp, border),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = if (isComplete && !isActive) "${step.stepNumber} ✓" else "${step.stepNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                    )
                    Text(
                        text = step.shortTitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardFeedbackBanner(
    message: String,
    isError: Boolean,
) {
    val extColors = RestaurantsLightTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = if (isError) extColors.errorSoft else extColors.brandGreenSoft,
        border = BorderStroke(
            1.dp,
            if (isError) extColors.error.copy(alpha = 0.45f) else extColors.brandGreenBorder,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isError) Icons.Rounded.Info else Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (isError) extColors.error else extColors.brandGreenDeep,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) extColors.error else extColors.brandGreenDeep,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun Step1BusinessInfoForm(
    draft: RestaurantApplicationDraftUiState,
    onDraftChanged: (RestaurantApplicationDraftUiState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ApplicationTextField(
            value = draft.legalName,
            onValueChange = { value ->
                onDraftChanged(
                    RestaurantApplicationReducer.updateField(draft, setOf(ApplicationFieldKey.LEGAL_NAME)) {
                        it.copy(legalName = value)
                    },
                )
            },
            label = "Legal / business name *",
            placeholder = "e.g., Habesha Hearth Hospitality PLC",
            errorMessage = draft.stepErrors[ApplicationFieldKey.LEGAL_NAME],
        )
        ApplicationTextField(
            value = draft.brandName,
            onValueChange = { value ->
                onDraftChanged(
                    RestaurantApplicationReducer.updateField(draft, setOf(ApplicationFieldKey.BRAND_NAME)) {
                        it.copy(brandName = value)
                    },
                )
            },
            label = "Public display name *",
            placeholder = "e.g., Habesha Hearth",
            errorMessage = draft.stepErrors[ApplicationFieldKey.BRAND_NAME],
        )
        ApplicationTextField(
            value = draft.registrationNumber,
            onValueChange = { value ->
                onDraftChanged(
                    RestaurantApplicationReducer.updateField(draft, setOf(ApplicationFieldKey.REGISTRATION_NUMBER)) {
                        it.copy(registrationNumber = value)
                    },
                )
            },
            label = "Business registration / license number *",
            placeholder = "Trade registration or TIN / license reference",
            errorMessage = draft.stepErrors[ApplicationFieldKey.REGISTRATION_NUMBER],
        )
        ApplicationTextField(
            value = draft.contactPhone,
            onValueChange = { value ->
                onDraftChanged(
                    RestaurantApplicationReducer.updateField(draft, setOf(ApplicationFieldKey.CONTACT_PHONE)) {
                        it.copy(contactPhone = value)
                    },
                )
            },
            label = "Contact phone *",
            placeholder = "+251 91 234 5678",
            keyboardType = KeyboardType.Phone,
            errorMessage = draft.stepErrors[ApplicationFieldKey.CONTACT_PHONE],
        )
        ApplicationTextField(
            value = draft.contactEmail,
            onValueChange = { value ->
                onDraftChanged(
                    RestaurantApplicationReducer.updateField(draft, setOf(ApplicationFieldKey.CONTACT_EMAIL)) {
                        it.copy(contactEmail = value)
                    },
                )
            },
            label = "Contact email *",
            placeholder = "partner@restaurant.et",
            keyboardType = KeyboardType.Email,
            errorMessage = draft.stepErrors[ApplicationFieldKey.CONTACT_EMAIL],
        )
    }
}

@Composable
private fun Step2LocationForm(
    draft: RestaurantApplicationDraftUiState,
    onDraftChanged: (RestaurantApplicationDraftUiState) -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ApplicationTextField(
            value = draft.cityAndArea,
            onValueChange = { value ->
                onDraftChanged(
                    RestaurantApplicationReducer.updateField(draft, setOf(ApplicationFieldKey.CITY_AREA)) {
                        it.copy(cityAndArea = value)
                    },
                )
            },
            label = "City & area *",
            placeholder = "e.g., Addis Ababa, Bole Atlas",
            errorMessage = draft.stepErrors[ApplicationFieldKey.CITY_AREA],
        )
        ApplicationTextField(
            value = draft.streetAddress,
            onValueChange = { value ->
                onDraftChanged(
                    RestaurantApplicationReducer.updateField(draft, setOf(ApplicationFieldKey.STREET_ADDRESS)) {
                        it.copy(streetAddress = value)
                    },
                )
            },
            label = "Restaurant address *",
            placeholder = "Street, building name, floor or landmark",
            errorMessage = draft.stepErrors[ApplicationFieldKey.STREET_ADDRESS],
        )
        ApplicationTextField(
            value = draft.primaryBranchName,
            onValueChange = { value ->
                onDraftChanged(
                    RestaurantApplicationReducer.updateField(draft, setOf(ApplicationFieldKey.PRIMARY_BRANCH_NAME)) {
                        it.copy(primaryBranchName = value)
                    },
                )
            },
            label = "Primary branch name *",
            placeholder = "e.g., Bole Atlas Flagship",
            errorMessage = draft.stepErrors[ApplicationFieldKey.PRIMARY_BRANCH_NAME],
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Map coordinates (optional)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = extColors.primaryText,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.latitudeText,
                    onValueChange = { value ->
                        onDraftChanged(
                            RestaurantApplicationReducer.updateField(draft, setOf(ApplicationFieldKey.MAP_COORDINATES)) {
                                it.copy(latitudeText = value)
                            },
                        )
                    },
                    label = { Text("Latitude") },
                    placeholder = { Text("9.0054") },
                    singleLine = true,
                    isError = draft.stepErrors.containsKey(ApplicationFieldKey.MAP_COORDINATES),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.longitudeText,
                    onValueChange = { value ->
                        onDraftChanged(
                            RestaurantApplicationReducer.updateField(draft, setOf(ApplicationFieldKey.MAP_COORDINATES)) {
                                it.copy(longitudeText = value)
                            },
                        )
                    },
                    label = { Text("Longitude") },
                    placeholder = { Text("38.7636") },
                    singleLine = true,
                    isError = draft.stepErrors.containsKey(ApplicationFieldKey.MAP_COORDINATES),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            val coordError = draft.stepErrors[ApplicationFieldKey.MAP_COORDINATES]
            if (!coordError.isNullOrBlank()) {
                Text(
                    text = coordError,
                    style = MaterialTheme.typography.labelSmall,
                    color = extColors.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step3RestaurantDetailsForm(
    draft: RestaurantApplicationDraftUiState,
    uploadPicker: RestaurantsUploadPickerHandle,
    onDraftChanged: (RestaurantApplicationDraftUiState) -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    var customCuisineInput by remember { mutableStateOf("") }
    val transientApp = remember(draft.draftId, draft.legalName, draft.brandName) {
        draft.toTransientCenterApplicationUi()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Cuisine categories *",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = extColors.primaryText,
            )
            val allOptions = remember(draft.selectedCuisines) {
                (RestaurantApplicationCatalog.suggestedCuisines + draft.selectedCuisines).distinct()
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                allOptions.forEach { cuisine ->
                    val selected = draft.selectedCuisines.any { it.equals(cuisine, ignoreCase = true) }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onDraftChanged(RestaurantApplicationReducer.toggleCuisine(draft, cuisine))
                        },
                        label = {
                            Text(
                                text = cuisine,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = extColors.brandGreenSoft,
                            selectedLabelColor = extColors.brandGreenDeep,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = customCuisineInput,
                    onValueChange = { customCuisineInput = it },
                    label = { Text("Add another cuisine") },
                    placeholder = { Text("e.g., Fresh Bakery") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        onDraftChanged(
                            RestaurantApplicationReducer.addCustomCuisine(draft, customCuisineInput),
                        )
                        customCuisineInput = ""
                    },
                    enabled = customCuisineInput.isNotBlank(),
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
            val cuisineError = draft.stepErrors[ApplicationFieldKey.CUISINES]
            if (!cuisineError.isNullOrBlank()) {
                Text(
                    text = cuisineError,
                    style = MaterialTheme.typography.labelSmall,
                    color = extColors.error,
                )
            }
        }

        ApplicationTextField(
            value = draft.description,
            onValueChange = { value ->
                onDraftChanged(
                    RestaurantApplicationReducer.updateField(draft, setOf(ApplicationFieldKey.DESCRIPTION)) {
                        it.copy(description = value)
                    },
                )
            },
            label = "Restaurant description *",
            placeholder = "Tell customers about your kitchen, signature dishes, and atmosphere.",
            singleLine = false,
            minLines = 3,
            errorMessage = draft.stepErrors[ApplicationFieldKey.DESCRIPTION],
        )

        ApplicationTextField(
            value = draft.operatingHoursSummary,
            onValueChange = { value ->
                onDraftChanged(
                    RestaurantApplicationReducer.updateField(draft, setOf(ApplicationFieldKey.OPERATING_HOURS)) {
                        it.copy(operatingHoursSummary = value)
                    },
                )
            },
            label = "Operating hours *",
            placeholder = "e.g., Mon–Sun · 07:30–22:00",
            errorMessage = draft.stepErrors[ApplicationFieldKey.OPERATING_HOURS],
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
            color = extColors.sidebarBackground,
            border = BorderStroke(1.dp, extColors.cardBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Dine-in table ordering available",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = extColors.primaryText,
                    )
                    Text(
                        text = "Customers can scan branch table QR codes to browse and place dine-in orders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extColors.secondaryText,
                    )
                }
                Switch(
                    checked = draft.isDineInAvailable,
                    onCheckedChange = { checked ->
                        onDraftChanged(
                            RestaurantApplicationReducer.updateField(draft) {
                                it.copy(isDineInAvailable = checked)
                            },
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = extColors.brandGreen,
                    ),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Logo & cover photo (optional in draft)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = extColors.primaryText,
            )
            MediaSelectionRowCard(
                title = "Restaurant Logo",
                subtitle = "Square brand mark shown in header and search cards.",
                attachment = draft.logoAttachment,
                onPickClick = {
                    uploadPicker.pickApplicationMedia(transientApp, RestaurantMediaSlot.LOGO)
                },
                onRemoveClick = {
                    onDraftChanged(
                        RestaurantApplicationReducer.updateField(draft) { it.copy(logoAttachment = null) },
                    )
                },
            )
            MediaSelectionRowCard(
                title = "Cover Photo",
                subtitle = "Banner photo showcasing your dining space or signature dishes.",
                attachment = draft.coverAttachment,
                onPickClick = {
                    uploadPicker.pickApplicationMedia(transientApp, RestaurantMediaSlot.COVER)
                },
                onRemoveClick = {
                    onDraftChanged(
                        RestaurantApplicationReducer.updateField(draft) { it.copy(coverAttachment = null) },
                    )
                },
            )
        }
    }
}

@Composable
private fun Step4DocumentsForm(
    draft: RestaurantApplicationDraftUiState,
    uploadPicker: RestaurantsUploadPickerHandle,
    onDraftChanged: (RestaurantApplicationDraftUiState) -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Business / Trade License *",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = extColors.primaryText,
            )
            DocumentAttachmentRowCard(
                title = "Business License PDF or Photo",
                subtitle = "PDF, JPG, PNG, or WebP · Uploaded securely once live backend is connected.",
                attachment = draft.businessLicenseDoc,
                actionLabel = if (draft.businessLicenseDoc == null) "Select File" else "Replace",
                onPickClick = {
                    uploadPicker.pickApplicationDocument(
                        applicationId = draft.draftId,
                        docType = DOC_TYPE_BUSINESS_LICENSE,
                        label = "Business License",
                    )
                },
                onRemoveClick = {
                    onDraftChanged(
                        RestaurantApplicationReducer.updateField(draft) { it.copy(businessLicenseDoc = null) },
                    )
                },
            )
            val licenseError = draft.stepErrors[ApplicationFieldKey.BUSINESS_LICENSE]
            if (!licenseError.isNullOrBlank()) {
                Text(
                    text = licenseError,
                    style = MaterialTheme.typography.labelSmall,
                    color = extColors.error,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Supporting business documents (optional)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = extColors.primaryText,
                    )
                    Text(
                        text = "TIN certificate, health permit, or authorization letter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extColors.secondaryText,
                    )
                }
                OutlinedButton(
                    onClick = {
                        uploadPicker.pickApplicationDocument(
                            applicationId = draft.draftId,
                            docType = DOC_TYPE_SUPPORTING,
                            label = "Supporting Document",
                        )
                    },
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Doc")
                }
            }

            if (draft.supportingDocs.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                    color = extColors.sidebarBackground,
                    border = BorderStroke(1.dp, extColors.cardBorder),
                ) {
                    Text(
                        text = "No supporting documents selected yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extColors.secondaryText,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                draft.supportingDocs.forEach { doc ->
                    SelectedAttachmentItemCard(
                        attachment = doc,
                        onRemoveClick = {
                            onDraftChanged(
                                RestaurantApplicationReducer.updateField(draft) { current ->
                                    current.copy(
                                        supportingDocs = current.supportingDocs.filterNot {
                                            it.localId == doc.localId
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
            color = extColors.qaNoticeBackground,
            border = BorderStroke(1.dp, extColors.qaNoticeBorder),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Badge,
                    contentDescription = null,
                    tint = extColors.qaNoticeText,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Owner Identification Requirements Notice",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = extColors.qaNoticeText,
                    )
                    Text(
                        text = "Authorized platform reviewers will verify owner government ID or passport in private encrypted storage during live submission. Sensitive ID numbers and identity images are intentionally not collected or saved in local drafts or QA previews.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extColors.primaryText,
                    )
                }
            }
        }
    }
}

@Composable
private fun Step5ReviewSummaryForm(
    draft: RestaurantApplicationDraftUiState,
    environment: RestaurantsEnvironment,
    onJumpToStep: (RestaurantApplicationStep) -> Unit,
    onDraftChanged: (RestaurantApplicationDraftUiState) -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ReviewSectionCard(
            title = "1. Business Information",
            onEditClick = { onJumpToStep(RestaurantApplicationStep.BUSINESS_INFO) },
            rows = listOf(
                "Legal name" to draft.legalName.ifBlank { "Missing" },
                "Display name" to draft.brandName.ifBlank { "Missing" },
                "License / Reg No." to draft.registrationNumber.ifBlank { "Missing" },
                "Phone" to draft.contactPhone.ifBlank { "Missing" },
                "Email" to draft.contactEmail.ifBlank { "Missing" },
            ),
        )

        val coordsText = if (draft.latitudeText.isNotBlank() && draft.longitudeText.isNotBlank()) {
            "${draft.latitudeText.trim()}, ${draft.longitudeText.trim()}"
        } else {
            "Not provided (optional)"
        }
        ReviewSectionCard(
            title = "2. Location & Primary Branch",
            onEditClick = { onJumpToStep(RestaurantApplicationStep.LOCATION) },
            rows = listOf(
                "City & area" to draft.cityAndArea.ifBlank { "Missing" },
                "Address" to draft.streetAddress.ifBlank { "Missing" },
                "Primary branch" to draft.primaryBranchName.ifBlank { "Missing" },
                "Coordinates" to coordsText,
            ),
        )

        ReviewSectionCard(
            title = "3. Restaurant Details",
            onEditClick = { onJumpToStep(RestaurantApplicationStep.RESTAURANT_DETAILS) },
            rows = listOf(
                "Cuisines" to if (draft.selectedCuisines.isEmpty()) "None selected" else draft.selectedCuisines.joinToString(", "),
                "Description" to draft.description.ifBlank { "Missing" },
                "Hours" to draft.operatingHoursSummary.ifBlank { "Missing" },
                "Dine-in ordering" to if (draft.isDineInAvailable) "Enabled" else "Disabled",
                "Logo" to (draft.logoAttachment?.fileName ?: "Not selected"),
                "Cover photo" to (draft.coverAttachment?.fileName ?: "Not selected"),
            ),
        )

        ReviewSectionCard(
            title = "4. Documents",
            onEditClick = { onJumpToStep(RestaurantApplicationStep.DOCUMENTS) },
            rows = listOf(
                "Business license" to (draft.businessLicenseDoc?.fileName ?: "Missing"),
                "Supporting docs" to if (draft.supportingDocs.isEmpty()) {
                    "None"
                } else {
                    draft.supportingDocs.joinToString(", ") { it.fileName }
                },
                "Owner ID check" to "Verified later via live compliance review",
            ),
        )

        if (!environment.isStagingConnected()) {
            DineInNoticeBanner(
                headline = "Submission Requires Live Restaurants Backend",
                supportingText = "Your backend service is not connected yet. Clicking Save Draft stores progress locally; Submit Application will report the backend-unavailable status instead of faking submission or approval.",
                icon = Icons.Rounded.CloudOff,
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onDraftChanged(
                        RestaurantApplicationReducer.updateField(
                            state = draft,
                            clearKeys = setOf(ApplicationFieldKey.ACCURACY_CONFIRMATION),
                        ) { it.copy(isAccuracyConfirmed = !it.isAccuracyConfirmed) },
                    )
                },
            shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
            color = extColors.sidebarBackground,
            border = BorderStroke(
                1.dp,
                if (draft.stepErrors.containsKey(ApplicationFieldKey.ACCURACY_CONFIRMATION)) {
                    extColors.error
                } else {
                    extColors.cardBorder
                },
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = draft.isAccuracyConfirmed,
                    onCheckedChange = { checked ->
                        onDraftChanged(
                            RestaurantApplicationReducer.updateField(
                                state = draft,
                                clearKeys = setOf(ApplicationFieldKey.ACCURACY_CONFIRMATION),
                            ) { it.copy(isAccuracyConfirmed = checked) },
                        )
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = extColors.brandGreen,
                        checkmarkColor = Color.White,
                    ),
                )
                Text(
                    text = "I confirm all restaurant details and licensing information entered above are accurate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extColors.primaryText,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        val accuracyError = draft.stepErrors[ApplicationFieldKey.ACCURACY_CONFIRMATION]
        if (!accuracyError.isNullOrBlank()) {
            Text(
                text = accuracyError,
                style = MaterialTheme.typography.labelSmall,
                color = extColors.error,
            )
        }
    }
}

@Composable
private fun ReviewSectionCard(
    title: String,
    rows: List<Pair<String, String>>,
    onEditClick: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = extColors.sidebarBackground,
        border = BorderStroke(1.dp, extColors.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = extColors.primaryText,
                )
                TextButton(
                    onClick = onEditClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Edit",
                        color = extColors.brandGreenDeep,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = extColors.secondaryText,
                        modifier = Modifier.weight(0.38f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (value == "Missing") extColors.error else extColors.primaryText,
                        modifier = Modifier.weight(0.62f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplicationWizardBottomBar(
    draft: RestaurantApplicationDraftUiState,
    canSubmitAttempt: Boolean,
    onPreviousClick: () -> Unit,
    onSaveDraftClick: () -> Unit,
    onNextClick: () -> Unit,
    onSubmitClick: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    Surface(
        color = extColors.cardSurface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, extColors.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (draft.currentStep.previousOrNull() != null) {
                OutlinedButton(
                    onClick = onPreviousClick,
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                ) {
                    Text("Back")
                }
            }
            OutlinedButton(
                onClick = onSaveDraftClick,
                shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
            ) {
                Text("Save Draft")
            }
            Button(
                onClick = if (canSubmitAttempt) onSubmitClick else onNextClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = extColors.brandGreen,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = if (canSubmitAttempt) "Submit Application" else "Continue",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ApplicationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    errorMessage: String?,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = minLines,
        isError = !errorMessage.isNullOrBlank(),
        supportingText = if (!errorMessage.isNullOrBlank()) {
            { Text(errorMessage) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MediaSelectionRowCard(
    title: String,
    subtitle: String,
    attachment: RestaurantApplicationAttachmentUi?,
    onPickClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = extColors.sidebarBackground,
        border = BorderStroke(1.dp, extColors.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = extColors.brandGreenSoft,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        tint = extColors.brandGreenDeep,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = extColors.primaryText,
                )
                val desc = if (attachment != null) {
                    val size = RestaurantsPickerMetadataHelper.formatByteSize(attachment.byteSize)
                    if (size.isNotBlank()) "${attachment.fileName} · $size" else attachment.fileName
                } else {
                    subtitle
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (attachment != null) extColors.brandGreenDeep else extColors.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (attachment != null) {
                IconButton(onClick = onRemoveClick) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove $title",
                        tint = extColors.secondaryText,
                    )
                }
            }
            OutlinedButton(
                onClick = onPickClick,
                shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
            ) {
                Text(if (attachment == null) "Select" else "Change")
            }
        }
    }
}

@Composable
private fun DocumentAttachmentRowCard(
    title: String,
    subtitle: String,
    attachment: RestaurantApplicationAttachmentUi?,
    actionLabel: String,
    onPickClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = extColors.sidebarBackground,
        border = BorderStroke(1.dp, extColors.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = if (attachment != null) extColors.brandGreenDeep else extColors.secondaryText,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = extColors.primaryText,
                )
                val info = if (attachment != null) {
                    val size = RestaurantsPickerMetadataHelper.formatByteSize(attachment.byteSize)
                    if (size.isNotBlank()) "${attachment.fileName} · $size" else attachment.fileName
                } else {
                    subtitle
                }
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (attachment != null) extColors.brandGreenDeep else extColors.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (attachment != null) {
                IconButton(onClick = onRemoveClick) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove $title",
                        tint = extColors.secondaryText,
                    )
                }
            }
            OutlinedButton(
                onClick = onPickClick,
                shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SelectedAttachmentItemCard(
    attachment: RestaurantApplicationAttachmentUi,
    onRemoveClick: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    val size = RestaurantsPickerMetadataHelper.formatByteSize(attachment.byteSize)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = extColors.cardSurface,
        border = BorderStroke(1.dp, extColors.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = extColors.brandGreenDeep,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = extColors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (size.isNotBlank()) {
                    Text(
                        text = size,
                        style = MaterialTheme.typography.labelSmall,
                        color = extColors.secondaryText,
                    )
                }
            }
            IconButton(onClick = onRemoveClick) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove ${attachment.fileName}",
                    tint = extColors.secondaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun applicationStatusContainerColor(status: RestaurantApplicationStatus): Color {
    val extColors = RestaurantsLightTheme.colors
    return when (status) {
        RestaurantApplicationStatus.APPROVED -> extColors.brandGreenSoft
        RestaurantApplicationStatus.REJECTED -> extColors.errorSoft
        RestaurantApplicationStatus.SUBMITTED,
        RestaurantApplicationStatus.UNDER_REVIEW,
        RestaurantApplicationStatus.CHANGES_REQUESTED,
        -> extColors.qaNoticeBackground
        RestaurantApplicationStatus.DRAFT -> extColors.mutedSurface
    }
}

@Composable
private fun applicationStatusContentColor(status: RestaurantApplicationStatus): Color {
    val extColors = RestaurantsLightTheme.colors
    return when (status) {
        RestaurantApplicationStatus.APPROVED -> extColors.brandGreenDeep
        RestaurantApplicationStatus.REJECTED -> extColors.error
        RestaurantApplicationStatus.SUBMITTED,
        RestaurantApplicationStatus.UNDER_REVIEW,
        RestaurantApplicationStatus.CHANGES_REQUESTED,
        -> extColors.qaNoticeText
        RestaurantApplicationStatus.DRAFT -> extColors.secondaryText
    }
}

private fun RestaurantApplicationDraftUiState.toTransientCenterApplicationUi(): CenterApplicationUi =
    CenterApplicationUi(
        id = draftId,
        applicantProfileId = applicantProfileId.orEmpty(),
        legalName = legalName,
        brandName = brandName,
        description = description,
        businessType = selectedCuisines.joinToString(", "),
        contactPhone = contactPhone,
        contactEmail = contactEmail,
        tinNumber = registrationNumber,
        logoPath = logoAttachment?.fileName,
        coverPath = coverAttachment?.fileName,
        status = status,
        reviewNotes = reviewNotes,
    )

private fun resolveAuthenticatedFirebaseUidOrNull(): String? = runCatching {
    FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() }
}.getOrNull()

private const val DOC_TYPE_BUSINESS_LICENSE = "business_license"
private const val DOC_TYPE_SUPPORTING = "supporting_doc"
