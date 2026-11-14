package com.adischat.app.feature.restaurants.center

import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.data.DraftBranchInputDto
import com.adischat.app.feature.restaurants.data.RestaurantsBinaryUploadSource
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import com.adischat.app.feature.restaurants.data.SaveApplicationDraftRequest
import java.util.Locale

/**
 * Steps of the 5-step native Restaurant Partner Application workflow (Phase R4A).
 */
enum class RestaurantApplicationStep(
    val stepNumber: Int,
    val shortTitle: String,
    val fullTitle: String,
    val subtitle: String,
) {
    BUSINESS_INFO(
        stepNumber = 1,
        shortTitle = "Business",
        fullTitle = "Business Information",
        subtitle = "Legal entity, public brand name, license reference, and primary contacts.",
    ),
    LOCATION(
        stepNumber = 2,
        shortTitle = "Location",
        fullTitle = "Location & Primary Branch",
        subtitle = "City, area, street address, optional map coordinates, and flagship branch name.",
    ),
    RESTAURANT_DETAILS(
        stepNumber = 3,
        shortTitle = "Details",
        fullTitle = "Restaurant Details",
        subtitle = "Cuisine categories, description, hours, dine-in availability, and branding.",
    ),
    DOCUMENTS(
        stepNumber = 4,
        shortTitle = "Documents",
        fullTitle = "Business Documents",
        subtitle = "Business license and supporting files. Sensitive IDs are collected only at live compliance review.",
    ),
    REVIEW(
        stepNumber = 5,
        shortTitle = "Review",
        fullTitle = "Review & Submit",
        subtitle = "Verify all entered details, save locally as draft, or submit once the backend is connected.",
    );

    fun previousOrNull(): RestaurantApplicationStep? =
        entries.getOrNull(ordinal - 1)

    fun nextOrNull(): RestaurantApplicationStep? =
        entries.getOrNull(ordinal + 1)
}

enum class ApplicationFieldKey {
    LEGAL_NAME,
    BRAND_NAME,
    REGISTRATION_NUMBER,
    CONTACT_PHONE,
    CONTACT_EMAIL,
    CITY_AREA,
    STREET_ADDRESS,
    MAP_COORDINATES,
    PRIMARY_BRANCH_NAME,
    CUISINES,
    DESCRIPTION,
    OPERATING_HOURS,
    BUSINESS_LICENSE,
    ACCURACY_CONFIRMATION,
}

enum class ManagementPlaceholderBadge(val label: String) {
    COMING_SOON("Coming Soon"),
    PENDING_APPROVAL("Pending Approval"),
}

enum class RestaurantManagementFeature {
    RESTAURANT_PROFILE,
    BRANCHES,
    MENU,
    STAFF,
    ORDERS,
}

data class RestaurantManagementPlaceholderUi(
    val feature: RestaurantManagementFeature,
    val title: String,
    val subtitle: String,
    val badge: ManagementPlaceholderBadge,
)

data class RestaurantApplicationAttachmentUi(
    val localId: String,
    val docType: String,
    val label: String,
    val fileName: String,
    val mimeType: String,
    val byteSize: Long?,
)

/**
 * Local application draft state for Phase R4A.
 *
 * Intentionally excludes sensitive owner national ID numbers or ID document binaries so
 * QA previews and local drafts never retain sensitive identity documents on-device.
 */
data class RestaurantApplicationDraftUiState(
    val draftId: String = "local-draft-1",
    val applicantProfileId: String? = null,
    val status: RestaurantApplicationStatus = RestaurantApplicationStatus.DRAFT,
    val currentStep: RestaurantApplicationStep = RestaurantApplicationStep.BUSINESS_INFO,

    // Step 1 — Business Information
    val legalName: String = "",
    val brandName: String = "",
    val registrationNumber: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",

    // Step 2 — Location
    val cityAndArea: String = "",
    val streetAddress: String = "",
    val latitudeText: String = "",
    val longitudeText: String = "",
    val primaryBranchName: String = "",

    // Step 3 — Restaurant Details
    val selectedCuisines: List<String> = emptyList(),
    val description: String = "",
    val operatingHoursSummary: String = "",
    val isDineInAvailable: Boolean = true,
    val logoAttachment: RestaurantApplicationAttachmentUi? = null,
    val coverAttachment: RestaurantApplicationAttachmentUi? = null,

    // Step 4 — Business Documents (no sensitive ID docs persisted in local state)
    val businessLicenseDoc: RestaurantApplicationAttachmentUi? = null,
    val supportingDocs: List<RestaurantApplicationAttachmentUi> = emptyList(),

    // Step 5 — Review & Local Save / Honest Backend Guard Feedback
    val isAccuracyConfirmed: Boolean = false,
    val reviewNotes: String? = null,
    val hasLocalDraftSave: Boolean = false,
    val lastLocalSaveTimestampMs: Long? = null,
    val stepErrors: Map<ApplicationFieldKey, String> = emptyMap(),
    val statusBannerMessage: String? = null,
    val isStatusBannerError: Boolean = false,
)

data class StepValidationResult(
    val step: RestaurantApplicationStep,
    val fieldErrors: Map<ApplicationFieldKey, String>,
) {
    val isValid: Boolean get() = fieldErrors.isEmpty()
}

sealed interface ApplicationSubmissionEligibility {
    data object ReadyForBackendSubmission : ApplicationSubmissionEligibility
    data class Blocked(val reason: String, val isBackendUnavailable: Boolean = false) : ApplicationSubmissionEligibility
}

object RestaurantApplicationCatalog {
    val suggestedCuisines: List<String> = listOf(
        "Ethiopian",
        "Coffee & Tea",
        "Fresh Juice & Shakes",
        "Breakfast & Bakery",
        "Grill & Tibs",
        "Fast Casual",
        "Vegetarian & Fasting",
        "International",
    )

    val lifecycleOrder: List<RestaurantApplicationStatus> = listOf(
        RestaurantApplicationStatus.DRAFT,
        RestaurantApplicationStatus.SUBMITTED,
        RestaurantApplicationStatus.UNDER_REVIEW,
        RestaurantApplicationStatus.APPROVED,
    )
}

object RestaurantApplicationValidator {

    private val simpleEmailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateStep(
        step: RestaurantApplicationStep,
        draft: RestaurantApplicationDraftUiState,
    ): StepValidationResult {
        val errors = linkedMapOf<ApplicationFieldKey, String>()
        when (step) {
            RestaurantApplicationStep.BUSINESS_INFO -> {
                if (draft.legalName.trim().length < 2) {
                    errors[ApplicationFieldKey.LEGAL_NAME] = "Enter the registered legal or business name."
                }
                if (draft.brandName.trim().length < 2) {
                    errors[ApplicationFieldKey.BRAND_NAME] = "Enter the public restaurant display name."
                }
                if (draft.registrationNumber.trim().length < 3) {
                    errors[ApplicationFieldKey.REGISTRATION_NUMBER] =
                        "Enter a business registration or trade license reference."
                }
                val phoneDigits = draft.contactPhone.count { it.isDigit() }
                if (phoneDigits < 7) {
                    errors[ApplicationFieldKey.CONTACT_PHONE] = "Enter a valid contact phone number (at least 7 digits)."
                }
                val trimmedEmail = draft.contactEmail.trim()
                if (!simpleEmailRegex.matches(trimmedEmail)) {
                    errors[ApplicationFieldKey.CONTACT_EMAIL] = "Enter a valid contact email address."
                }
            }

            RestaurantApplicationStep.LOCATION -> {
                if (draft.cityAndArea.trim().length < 2) {
                    errors[ApplicationFieldKey.CITY_AREA] = "Enter city and neighborhood or sub-city."
                }
                if (draft.streetAddress.trim().length < 4) {
                    errors[ApplicationFieldKey.STREET_ADDRESS] = "Enter the restaurant street address or landmark."
                }
                val latRaw = draft.latitudeText.trim()
                val lngRaw = draft.longitudeText.trim()
                if (latRaw.isNotEmpty() || lngRaw.isNotEmpty()) {
                    val lat = latRaw.toDoubleOrNull()
                    val lng = lngRaw.toDoubleOrNull()
                    val validCoords = lat != null && lng != null &&
                        lat in -90.0..90.0 && lng in -180.0..180.0
                    if (!validCoords) {
                        errors[ApplicationFieldKey.MAP_COORDINATES] =
                            "Provide both latitude (-90..90) and longitude (-180..180), or leave both blank."
                    }
                }
                if (draft.primaryBranchName.trim().length < 2) {
                    errors[ApplicationFieldKey.PRIMARY_BRANCH_NAME] = "Enter the primary branch name."
                }
            }

            RestaurantApplicationStep.RESTAURANT_DETAILS -> {
                if (draft.selectedCuisines.isEmpty()) {
                    errors[ApplicationFieldKey.CUISINES] = "Select at least one cuisine category."
                }
                if (draft.description.trim().length < 10) {
                    errors[ApplicationFieldKey.DESCRIPTION] =
                        "Describe your restaurant concept (at least 10 characters)."
                }
                if (draft.operatingHoursSummary.trim().length < 3) {
                    errors[ApplicationFieldKey.OPERATING_HOURS] = "Enter your typical operating hours."
                }
            }

            RestaurantApplicationStep.DOCUMENTS -> {
                if (draft.businessLicenseDoc == null) {
                    errors[ApplicationFieldKey.BUSINESS_LICENSE] =
                        "Attach a business or trade license PDF/photo before final submission."
                }
            }

            RestaurantApplicationStep.REVIEW -> {
                listOf(
                    RestaurantApplicationStep.BUSINESS_INFO,
                    RestaurantApplicationStep.LOCATION,
                    RestaurantApplicationStep.RESTAURANT_DETAILS,
                    RestaurantApplicationStep.DOCUMENTS,
                ).forEach { prerequisite ->
                    errors.putAll(validateStep(prerequisite, draft).fieldErrors)
                }
                if (!draft.isAccuracyConfirmed) {
                    errors[ApplicationFieldKey.ACCURACY_CONFIRMATION] =
                        "Confirm that the application details are accurate before submitting."
                }
            }
        }
        return StepValidationResult(step = step, fieldErrors = errors)
    }

    fun isStepCompleted(
        step: RestaurantApplicationStep,
        draft: RestaurantApplicationDraftUiState,
    ): Boolean = validateStep(step, draft).isValid

    fun toSaveApplicationDraftRequest(draft: RestaurantApplicationDraftUiState): SaveApplicationDraftRequest {
        val branchSummary = RestaurantApplicationAttachmentHelper.formatBranchSummary(draft)
        return SaveApplicationDraftRequest(
            applicationId = draft.draftId.takeIf { !it.startsWith("local-") },
            legalName = draft.legalName.trim(),
            brandName = draft.brandName.trim(),
            description = draft.description.trim(),
            businessType = draft.selectedCuisines.joinToString(", "),
            contactPhone = draft.contactPhone.trim(),
            contactEmail = draft.contactEmail.trim(),
            tinNumber = draft.registrationNumber.trim(),
            logoPath = draft.logoAttachment?.fileName,
            coverPath = draft.coverAttachment?.fileName,
            branches = listOf(branchSummary),
        )
    }
}

object RestaurantApplicationGuards {

    /**
     * Enforces platform governance: an applicant/owner can never approve or reject
     * their own application; an explicit platform-admin claim is required.
     */
    fun canActorApproveOrRejectApplication(
        actorProfileId: String?,
        applicantProfileId: String?,
        isAuthorizedPlatformAdmin: Boolean,
    ): Boolean {
        if (!isAuthorizedPlatformAdmin) return false
        val actor = actorProfileId?.trim().orEmpty()
        if (actor.isEmpty()) return false
        val applicant = applicantProfileId?.trim().orEmpty()
        if (applicant.isNotEmpty() && actor == applicant) return false
        return true
    }

    fun evaluateSubmissionEligibility(
        draft: RestaurantApplicationDraftUiState,
        authenticatedUid: String?,
        environment: RestaurantsEnvironment,
    ): ApplicationSubmissionEligibility {
        if (!environment.isRestaurantCenterEnabled) {
            return ApplicationSubmissionEligibility.Blocked(
                reason = "Restaurant Center is disabled in this release configuration.",
            )
        }
        if (authenticatedUid.isNullOrBlank()) {
            return ApplicationSubmissionEligibility.Blocked(
                reason = "Sign in with your AdisChat account before submitting a restaurant application.",
            )
        }
        if (!draft.status.isEditableByApplicant) {
            return ApplicationSubmissionEligibility.Blocked(
                reason = "This application is currently ${draft.status.label} and cannot be resubmitted.",
            )
        }
        val reviewValidation = RestaurantApplicationValidator.validateStep(
            step = RestaurantApplicationStep.REVIEW,
            draft = draft,
        )
        if (!reviewValidation.isValid) {
            return ApplicationSubmissionEligibility.Blocked(
                reason = reviewValidation.fieldErrors.values.firstOrNull()
                    ?: "Complete all required application fields before submitting.",
            )
        }
        if (!environment.isStagingConnected()) {
            return ApplicationSubmissionEligibility.Blocked(
                reason = "Restaurants backend is not connected yet. Your application has been kept as a local draft; live submission and admin review will unlock when the Restaurants service is enabled.",
                isBackendUnavailable = true,
            )
        }
        return ApplicationSubmissionEligibility.ReadyForBackendSubmission
    }

    fun buildManagementPlaceholders(
        status: RestaurantApplicationStatus,
    ): List<RestaurantManagementPlaceholderUi> {
        val awaitingReview = status == RestaurantApplicationStatus.SUBMITTED ||
            status == RestaurantApplicationStatus.UNDER_REVIEW ||
            status == RestaurantApplicationStatus.CHANGES_REQUESTED
        val primaryBadge = if (awaitingReview) {
            ManagementPlaceholderBadge.PENDING_APPROVAL
        } else {
            ManagementPlaceholderBadge.COMING_SOON
        }
        return listOf(
            RestaurantManagementPlaceholderUi(
                feature = RestaurantManagementFeature.RESTAURANT_PROFILE,
                title = "Restaurant Profile",
                subtitle = if (awaitingReview) {
                    "Profile publishing unlocks after admin approval."
                } else {
                    "Public branding, cuisine tags, and story management."
                },
                badge = primaryBadge,
            ),
            RestaurantManagementPlaceholderUi(
                feature = RestaurantManagementFeature.BRANCHES,
                title = "Branch Management",
                subtitle = if (awaitingReview) {
                    "Branch setup unlocks once your application is approved."
                } else {
                    "Addresses, opening hours, and table QR codes."
                },
                badge = primaryBadge,
            ),
            RestaurantManagementPlaceholderUi(
                feature = RestaurantManagementFeature.MENU,
                title = "Menu Management",
                subtitle = if (awaitingReview) {
                    "Menu publishing waits for application approval."
                } else {
                    "Categories, pricing in ETB, modifiers, and availability."
                },
                badge = ManagementPlaceholderBadge.COMING_SOON,
            ),
            RestaurantManagementPlaceholderUi(
                feature = RestaurantManagementFeature.STAFF,
                title = "Staff & Roles",
                subtitle = if (awaitingReview) {
                    "Staff invites activate after restaurant approval."
                } else {
                    "Invite managers, cashiers, kitchen, and waitstaff."
                },
                badge = ManagementPlaceholderBadge.COMING_SOON,
            ),
            RestaurantManagementPlaceholderUi(
                feature = RestaurantManagementFeature.ORDERS,
                title = "Orders Management",
                subtitle = if (awaitingReview) {
                    "Live table orders unlock when a branch is approved."
                } else {
                    "Real-time dine-in order queue and status transitions."
                },
                badge = ManagementPlaceholderBadge.COMING_SOON,
            ),
        )
    }
}

object RestaurantApplicationAttachmentHelper {

    fun fromBinaryUploadSource(
        localId: String,
        docType: String,
        label: String,
        source: RestaurantsBinaryUploadSource,
    ): RestaurantApplicationAttachmentUi = RestaurantApplicationAttachmentUi(
        localId = localId,
        docType = docType,
        label = label,
        fileName = source.fileName.trim().ifEmpty { "selected-file" },
        mimeType = source.mimeType.trim().ifEmpty { "application/octet-stream" },
        byteSize = source.byteSize,
    )

    fun formatBranchSummary(draft: RestaurantApplicationDraftUiState): DraftBranchInputDto {
        val branchCode = draft.primaryBranchName.trim()
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]+"), "-")
            .trim('-')
            .take(12)
            .ifEmpty { "MAIN" }
        val coordsNote = if (draft.latitudeText.isNotBlank() && draft.longitudeText.isNotBlank()) {
            " (${draft.latitudeText.trim()}, ${draft.longitudeText.trim()})"
        } else {
            ""
        }
        return DraftBranchInputDto(
            name = draft.primaryBranchName.trim(),
            code = branchCode,
            address = draft.streetAddress.trim() + coordsNote,
            city = draft.cityAndArea.trim(),
        )
    }
}

object RestaurantApplicationReducer {

    fun updateField(
        state: RestaurantApplicationDraftUiState,
        clearKeys: Set<ApplicationFieldKey> = emptySet(),
        transform: (RestaurantApplicationDraftUiState) -> RestaurantApplicationDraftUiState,
    ): RestaurantApplicationDraftUiState {
        val next = transform(state)
        return next.copy(
            stepErrors = if (clearKeys.isEmpty()) next.stepErrors else next.stepErrors - clearKeys,
            statusBannerMessage = null,
            isStatusBannerError = false,
        )
    }

    fun toggleCuisine(
        state: RestaurantApplicationDraftUiState,
        cuisine: String,
    ): RestaurantApplicationDraftUiState {
        val trimmed = cuisine.trim()
        if (trimmed.isEmpty()) return state
        val exists = state.selectedCuisines.any { it.equals(trimmed, ignoreCase = true) }
        val updated = if (exists) {
            state.selectedCuisines.filterNot { it.equals(trimmed, ignoreCase = true) }
        } else {
            state.selectedCuisines + trimmed
        }
        return state.copy(
            selectedCuisines = updated,
            stepErrors = state.stepErrors - ApplicationFieldKey.CUISINES,
            statusBannerMessage = null,
            isStatusBannerError = false,
        )
    }

    fun addCustomCuisine(
        state: RestaurantApplicationDraftUiState,
        rawCuisine: String,
    ): RestaurantApplicationDraftUiState {
        val trimmed = rawCuisine.trim()
        if (trimmed.isEmpty()) return state
        if (state.selectedCuisines.any { it.equals(trimmed, ignoreCase = true) }) {
            return state
        }
        return state.copy(
            selectedCuisines = state.selectedCuisines + trimmed,
            stepErrors = state.stepErrors - ApplicationFieldKey.CUISINES,
            statusBannerMessage = null,
            isStatusBannerError = false,
        )
    }

    fun selectStep(
        state: RestaurantApplicationDraftUiState,
        targetStep: RestaurantApplicationStep,
    ): RestaurantApplicationDraftUiState = state.copy(
        currentStep = targetStep,
        stepErrors = emptyMap(),
        statusBannerMessage = null,
        isStatusBannerError = false,
    )

    fun nextStep(state: RestaurantApplicationDraftUiState): RestaurantApplicationDraftUiState {
        val current = state.currentStep
        val validation = RestaurantApplicationValidator.validateStep(current, state)
        if (!validation.isValid) {
            return state.copy(
                stepErrors = validation.fieldErrors,
                statusBannerMessage = "Fix the highlighted fields in Step ${current.stepNumber} to continue.",
                isStatusBannerError = true,
            )
        }
        val next = current.nextOrNull() ?: return state
        return state.copy(
            currentStep = next,
            stepErrors = emptyMap(),
            statusBannerMessage = null,
            isStatusBannerError = false,
        )
    }

    fun previousStep(state: RestaurantApplicationDraftUiState): RestaurantApplicationDraftUiState {
        val prev = state.currentStep.previousOrNull() ?: return state
        return state.copy(
            currentStep = prev,
            stepErrors = emptyMap(),
            statusBannerMessage = null,
            isStatusBannerError = false,
        )
    }

    fun saveLocalDraft(
        state: RestaurantApplicationDraftUiState,
        nowEpochMs: Long,
    ): RestaurantApplicationDraftUiState = state.copy(
        status = if (state.status == RestaurantApplicationStatus.CHANGES_REQUESTED) {
            RestaurantApplicationStatus.CHANGES_REQUESTED
        } else {
            RestaurantApplicationStatus.DRAFT
        },
        hasLocalDraftSave = true,
        lastLocalSaveTimestampMs = nowEpochMs,
        statusBannerMessage = "Draft saved locally on this device. Your progress across all 5 steps is preserved.",
        isStatusBannerError = false,
    )

    fun attemptSubmit(
        state: RestaurantApplicationDraftUiState,
        authenticatedUid: String?,
        environment: RestaurantsEnvironment,
        nowEpochMs: Long,
    ): RestaurantApplicationDraftUiState {
        val validation = RestaurantApplicationValidator.validateStep(
            step = RestaurantApplicationStep.REVIEW,
            draft = state,
        )
        if (!validation.isValid) {
            val firstError = validation.fieldErrors.values.firstOrNull()
                ?: "Complete all required fields before submitting."
            return state.copy(
                stepErrors = validation.fieldErrors,
                statusBannerMessage = firstError,
                isStatusBannerError = true,
            )
        }
        return when (
            val eligibility = RestaurantApplicationGuards.evaluateSubmissionEligibility(
                draft = state,
                authenticatedUid = authenticatedUid,
                environment = environment,
            )
        ) {
            is ApplicationSubmissionEligibility.Blocked -> {
                // Keep draft safely saved locally when backend is unavailable; never fake SUBMITTED or APPROVED.
                state.copy(
                    hasLocalDraftSave = true,
                    lastLocalSaveTimestampMs = nowEpochMs,
                    stepErrors = emptyMap(),
                    statusBannerMessage = eligibility.reason,
                    isStatusBannerError = !eligibility.isBackendUnavailable,
                )
            }

            ApplicationSubmissionEligibility.ReadyForBackendSubmission -> {
                // Phase R4A is local foundation only; live network submission will be wired in R4B.
                state.copy(
                    hasLocalDraftSave = true,
                    lastLocalSaveTimestampMs = nowEpochMs,
                    stepErrors = emptyMap(),
                    statusBannerMessage = "Application verified and saved locally. Live submission endpoint will be connected in the next phase.",
                    isStatusBannerError = false,
                )
            }
        }
    }
}
