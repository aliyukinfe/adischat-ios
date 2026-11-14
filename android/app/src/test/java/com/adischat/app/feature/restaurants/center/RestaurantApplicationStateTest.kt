package com.adischat.app.feature.restaurants.center

import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantApplicationStateTest {

    private fun sampleLicenseDoc(): RestaurantApplicationAttachmentUi =
        RestaurantApplicationAttachmentUi(
            localId = "doc-license-1",
            docType = "business_license",
            label = "Business License",
            fileName = "trade-license.pdf",
            mimeType = "application/pdf",
            byteSize = 240_000L,
        )

    private fun completeValidDraft(): RestaurantApplicationDraftUiState =
        RestaurantApplicationDraftUiState(
            applicantProfileId = "firebase-uid-owner-1",
            legalName = "Habesha Hearth Hospitality PLC",
            brandName = "Habesha Hearth",
            registrationNumber = "ET-AA-TR-2026-0091",
            contactPhone = "+251 91 234 5678",
            contactEmail = "owner@habeshahearth.et",
            cityAndArea = "Addis Ababa, Bole Atlas",
            streetAddress = "Bole Atlas Main Rd, Cameroon St",
            latitudeText = "9.0054",
            longitudeText = "38.7636",
            primaryBranchName = "Bole Atlas Flagship",
            selectedCuisines = listOf("Ethiopian", "Coffee & Tea"),
            description = "Modern Ethiopian kitchen and specialty coffee bar in Bole Atlas.",
            operatingHoursSummary = "Mon-Sun 07:30-22:00",
            isDineInAvailable = true,
            businessLicenseDoc = sampleLicenseDoc(),
            isAccuracyConfirmed = true,
        )

    @Test
    fun `step 1 business validation rejects blank fields bad phone and invalid email`() {
        val result = RestaurantApplicationValidator.validateStep(
            step = RestaurantApplicationStep.BUSINESS_INFO,
            draft = RestaurantApplicationDraftUiState(
                legalName = "A",
                brandName = "",
                registrationNumber = "12",
                contactPhone = "1234",
                contactEmail = "not-an-email",
            ),
        )
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ApplicationFieldKey.LEGAL_NAME))
        assertTrue(result.fieldErrors.containsKey(ApplicationFieldKey.BRAND_NAME))
        assertTrue(result.fieldErrors.containsKey(ApplicationFieldKey.REGISTRATION_NUMBER))
        assertTrue(result.fieldErrors.containsKey(ApplicationFieldKey.CONTACT_PHONE))
        assertTrue(result.fieldErrors.containsKey(ApplicationFieldKey.CONTACT_EMAIL))
    }

    @Test
    fun `step 2 location accepts empty optional coordinates and rejects partial or out-of-range coordinates`() {
        val validWithoutCoords = RestaurantApplicationValidator.validateStep(
            step = RestaurantApplicationStep.LOCATION,
            draft = completeValidDraft().copy(latitudeText = "", longitudeText = ""),
        )
        assertTrue(validWithoutCoords.isValid)

        val partialCoords = RestaurantApplicationValidator.validateStep(
            step = RestaurantApplicationStep.LOCATION,
            draft = completeValidDraft().copy(latitudeText = "9.0", longitudeText = ""),
        )
        assertFalse(partialCoords.isValid)
        assertTrue(partialCoords.fieldErrors.containsKey(ApplicationFieldKey.MAP_COORDINATES))

        val outOfRange = RestaurantApplicationValidator.validateStep(
            step = RestaurantApplicationStep.LOCATION,
            draft = completeValidDraft().copy(latitudeText = "120.5", longitudeText = "38.7"),
        )
        assertFalse(outOfRange.isValid)
        assertTrue(outOfRange.fieldErrors.containsKey(ApplicationFieldKey.MAP_COORDINATES))
    }

    @Test
    fun `step 3 details requires cuisine description and hours and toggles custom cuisines cleanly`() {
        var draft = RestaurantApplicationDraftUiState()
        draft = RestaurantApplicationReducer.toggleCuisine(draft, "Ethiopian")
        draft = RestaurantApplicationReducer.addCustomCuisine(draft, "Fresh Pastry")
        draft = RestaurantApplicationReducer.addCustomCuisine(draft, "ethiopian") // duplicate ignored
        assertEquals(listOf("Ethiopian", "Fresh Pastry"), draft.selectedCuisines)

        val incomplete = RestaurantApplicationValidator.validateStep(
            step = RestaurantApplicationStep.RESTAURANT_DETAILS,
            draft = draft.copy(description = "Short", operatingHoursSummary = ""),
        )
        assertFalse(incomplete.isValid)
        assertTrue(incomplete.fieldErrors.containsKey(ApplicationFieldKey.DESCRIPTION))
        assertTrue(incomplete.fieldErrors.containsKey(ApplicationFieldKey.OPERATING_HOURS))
    }

    @Test
    fun `step 4 documents requires business license and omits sensitive id fields from draft model`() {
        val missingLicense = RestaurantApplicationValidator.validateStep(
            step = RestaurantApplicationStep.DOCUMENTS,
            draft = completeValidDraft().copy(businessLicenseDoc = null),
        )
        assertFalse(missingLicense.isValid)
        assertTrue(missingLicense.fieldErrors.containsKey(ApplicationFieldKey.BUSINESS_LICENSE))

        // Structural verification: draft state & DTO builder contain no national ID or passport fields
        val fieldNames = RestaurantApplicationDraftUiState::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fieldNames.any { it.contains("nationalid") || it.contains("passport") || it.contains("governmentid") })
        val dto = RestaurantApplicationValidator.toSaveApplicationDraftRequest(completeValidDraft())
        assertEquals("Habesha Hearth", dto.brandName)
        assertEquals(1, dto.branches?.size)
    }

    @Test
    fun `navigation across steps preserves form data and local save keeps editable draft status`() {
        var state = completeValidDraft()
        assertEquals(RestaurantApplicationStep.BUSINESS_INFO, state.currentStep)

        state = RestaurantApplicationReducer.nextStep(state)
        assertEquals(RestaurantApplicationStep.LOCATION, state.currentStep)
        state = RestaurantApplicationReducer.nextStep(state)
        assertEquals(RestaurantApplicationStep.RESTAURANT_DETAILS, state.currentStep)
        state = RestaurantApplicationReducer.previousStep(state)
        assertEquals(RestaurantApplicationStep.LOCATION, state.currentStep)
        assertEquals("Habesha Hearth Hospitality PLC", state.legalName)

        state = RestaurantApplicationReducer.saveLocalDraft(state, nowEpochMs = 1_760_000_000_000L)
        assertTrue(state.hasLocalDraftSave)
        assertEquals(1_760_000_000_000L, state.lastLocalSaveTimestampMs)
        assertEquals(RestaurantApplicationStatus.DRAFT, state.status)
        assertNotNull(state.statusBannerMessage)
    }

    @Test
    fun `submit blocks honestly when backend is disconnected and never fakes approval or submission`() {
        val disconnectedEnv = RestaurantsEnvironment(
            supabaseUrl = "",
            supabasePublishableKey = "",
            identityBridgeUrl = "",
            isIdentityBridgeEnabled = false,
            isRestaurantCenterEnabled = true,
            isOfflinePreview = false,
        )
        val initial = completeValidDraft().copy(currentStep = RestaurantApplicationStep.REVIEW)
        val result = RestaurantApplicationReducer.attemptSubmit(
            state = initial,
            authenticatedUid = "firebase-uid-owner-1",
            environment = disconnectedEnv,
            nowEpochMs = 1_760_000_123_000L,
        )

        assertEquals(RestaurantApplicationStatus.DRAFT, result.status)
        assertTrue(result.hasLocalDraftSave)
        assertFalse(result.isStatusBannerError)
        assertTrue(result.statusBannerMessage.orEmpty().contains("backend is not connected", ignoreCase = true))

        val eligibility = RestaurantApplicationGuards.evaluateSubmissionEligibility(
            draft = initial,
            authenticatedUid = "firebase-uid-owner-1",
            environment = disconnectedEnv,
        )
        assertTrue(eligibility is ApplicationSubmissionEligibility.Blocked)
        assertTrue((eligibility as ApplicationSubmissionEligibility.Blocked).isBackendUnavailable)
    }

    @Test
    fun `owner cannot self approve application and only platform admin may review`() {
        val applicantId = "firebase-uid-owner-1"
        assertFalse(
            RestaurantApplicationGuards.canActorApproveOrRejectApplication(
                actorProfileId = applicantId,
                applicantProfileId = applicantId,
                isAuthorizedPlatformAdmin = false,
            ),
        )
        assertFalse(
            RestaurantApplicationGuards.canActorApproveOrRejectApplication(
                actorProfileId = applicantId,
                applicantProfileId = applicantId,
                isAuthorizedPlatformAdmin = true,
            ),
        )
        assertFalse(
            RestaurantApplicationGuards.canActorApproveOrRejectApplication(
                actorProfileId = "firebase-uid-reviewer-2",
                applicantProfileId = applicantId,
                isAuthorizedPlatformAdmin = false,
            ),
        )
        assertTrue(
            RestaurantApplicationGuards.canActorApproveOrRejectApplication(
                actorProfileId = "firebase-uid-admin-99",
                applicantProfileId = applicantId,
                isAuthorizedPlatformAdmin = true,
            ),
        )
    }

    @Test
    fun `management placeholders display coming soon or pending approval based on status`() {
        val draftCards = RestaurantApplicationGuards.buildManagementPlaceholders(RestaurantApplicationStatus.DRAFT)
        assertEquals(5, draftCards.size)
        assertTrue(draftCards.all { it.badge == ManagementPlaceholderBadge.COMING_SOON })

        val submittedCards = RestaurantApplicationGuards.buildManagementPlaceholders(RestaurantApplicationStatus.UNDER_REVIEW)
        assertEquals(5, submittedCards.size)
        assertEquals(ManagementPlaceholderBadge.PENDING_APPROVAL, submittedCards[0].badge)
        assertEquals(ManagementPlaceholderBadge.PENDING_APPROVAL, submittedCards[1].badge)
        assertEquals(ManagementPlaceholderBadge.COMING_SOON, submittedCards[2].badge)

        assertEquals(
            setOf(
                RestaurantManagementFeature.RESTAURANT_PROFILE,
                RestaurantManagementFeature.BRANCHES,
                RestaurantManagementFeature.MENU,
                RestaurantManagementFeature.STAFF,
                RestaurantManagementFeature.ORDERS,
            ),
            draftCards.map { it.feature }.toSet(),
        )
    }
}
