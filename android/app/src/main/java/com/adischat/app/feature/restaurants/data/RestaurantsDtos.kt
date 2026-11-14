package com.adischat.app.feature.restaurants.data

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

// ============================================================================
// Common envelopes & responses
// ============================================================================

data class RestaurantsOkResponse(
    val ok: Boolean = false,
)

data class RestaurantsApiErrorEnvelope(
    val ok: Boolean = false,
    val error: RestaurantsApiErrorBody? = null,
)

data class RestaurantsApiErrorBody(
    val code: String? = null,
    val message: String? = null,
)

// ============================================================================
// Identity bridge & session exchange (`restaurants-exchange-session` + GoTrue)
// ============================================================================

data class RestaurantsAssertionRequest(
    @SerializedName("project_scope")
    val projectScope: String = "restaurants",
)

data class RestaurantsAssertionResponse(
    val ok: Boolean = false,
    val assertion: String? = null,
    @SerializedName("expires_at")
    val expiresAt: Long? = null,
)

data class RestaurantsExchangeSessionRequest(
    val assertion: String,
)

data class RestaurantsExchangeSessionResponse(
    val ok: Boolean = false,
    @SerializedName("profile_id")
    val profileId: String? = null,
    val session: RestaurantsAuthSessionDto? = null,
)

data class RestaurantsAuthSessionDto(
    @SerializedName("access_token")
    val accessToken: String = "",
    @SerializedName("refresh_token")
    val refreshToken: String = "",
    @SerializedName("expires_at")
    val expiresAt: Long? = null,
    @SerializedName("user_id")
    val userId: String? = null,
)

data class GoTrueRefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String,
)

data class GoTrueRefreshResponse(
    @SerializedName("access_token")
    val accessToken: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("expires_in")
    val expiresIn: Long? = null,
    @SerializedName("expires_at")
    val expiresAt: Long? = null,
    val user: GoTrueUserDto? = null,
)

data class GoTrueUserDto(
    val id: String? = null,
)

// ============================================================================
// PostgREST catalog & discovery models
// ============================================================================

data class RestaurantProfileDto(
    val id: String,
    @SerializedName("adischat_user_id")
    val adischatUserId: String = "",
    @SerializedName("display_name")
    val displayName: String? = null,
    val phone: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    val status: String = "active",
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
)

data class RestaurantSummaryDto(
    val id: String,
    @SerializedName("owner_profile_id")
    val ownerProfileId: String? = null,
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    @SerializedName("logo_path")
    val logoPath: String? = null,
    @SerializedName("cover_path")
    val coverPath: String? = null,
    val status: String = "pending_setup",
    @SerializedName("is_live_ordering_enabled")
    val isLiveOrderingEnabled: Boolean = false,
    @SerializedName("suspended_reason")
    val suspendedReason: String? = null,
    @SerializedName("suspended_at")
    val suspendedAt: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
)

data class RestaurantBranchDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    val code: String = "",
    val name: String = "",
    val address: String = "",
    val city: String = "Addis Ababa",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerializedName("opening_hours_json")
    val openingHoursJson: JsonElement? = null,
    @SerializedName("require_staff_session_activation")
    val requireStaffSessionActivation: Boolean = true,
    @SerializedName("require_session_code")
    val requireSessionCode: Boolean = false,
    @SerializedName("is_dine_in_active")
    val isDineInActive: Boolean = false,
    val status: String = "active",
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
) {
    val isActive: Boolean
        get() = status.equals("active", ignoreCase = true)
}

data class MenuCategoryDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    val name: String = "",
    @SerializedName("sort_order")
    val sortOrder: Int = 0,
    @SerializedName("is_active")
    val isActive: Boolean = true,
)

data class MenuItemDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("category_id")
    val categoryId: String,
    val name: String = "",
    val description: String = "",
    @SerializedName("base_price_minor")
    val basePriceMinor: Long = 0L,
    val currency: String = "ETB",
    @SerializedName("prep_time_mins")
    val prepTimeMins: Int = 15,
    @SerializedName("is_veg")
    val isVeg: Boolean = false,
    @SerializedName("is_fasting")
    val isFasting: Boolean = false,
    val allergens: List<String> = emptyList(),
    @SerializedName("is_active")
    val isActive: Boolean = true,
    @SerializedName("sort_order")
    val sortOrder: Int = 0,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
)

data class MenuItemImageDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("menu_item_id")
    val menuItemId: String,
    @SerializedName("storage_path")
    val storagePath: String? = null,
    @SerializedName("is_primary")
    val isPrimary: Boolean = false,
    @SerializedName("sort_order")
    val sortOrder: Int = 0,
)

data class MenuOptionGroupDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("menu_item_id")
    val menuItemId: String,
    val name: String = "",
    @SerializedName("is_required")
    val isRequired: Boolean = false,
    @SerializedName("min_select")
    val minSelect: Int = 0,
    @SerializedName("max_select")
    val maxSelect: Int = 1,
    @SerializedName("sort_order")
    val sortOrder: Int = 0,
)

data class MenuOptionDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("option_group_id")
    val optionGroupId: String,
    val name: String = "",
    @SerializedName("price_delta_minor")
    val priceDeltaMinor: Long = 0L,
    @SerializedName("is_available")
    val isAvailable: Boolean = true,
    @SerializedName("sort_order")
    val sortOrder: Int = 0,
)

data class BranchMenuItemOverrideDto(
    @SerializedName("branch_id")
    val branchId: String,
    @SerializedName("menu_item_id")
    val menuItemId: String,
    @SerializedName("is_available")
    val isAvailable: Boolean = true,
    @SerializedName("price_override_minor")
    val priceOverrideMinor: Long? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
)

// ============================================================================
// Restaurant applications (`restaurants-application`)
// ============================================================================

data class DraftBranchInputDto(
    val code: String,
    val name: String,
    val address: String,
    val city: String? = null,
    @SerializedName("opening_hours_json")
    val openingHoursJson: JsonElement? = null,
)

data class DraftMenuOptionInputDto(
    val name: String,
    @SerializedName("price_delta_minor")
    val priceDeltaMinor: Long = 0L,
    @SerializedName("is_available")
    val isAvailable: Boolean = true,
    @SerializedName("sort_order")
    val sortOrder: Int = 0,
)

data class DraftMenuOptionGroupInputDto(
    val name: String,
    @SerializedName("is_required")
    val isRequired: Boolean = false,
    @SerializedName("min_select")
    val minSelect: Int = 0,
    @SerializedName("max_select")
    val maxSelect: Int = 1,
    @SerializedName("sort_order")
    val sortOrder: Int = 0,
    val options: List<DraftMenuOptionInputDto> = emptyList(),
)

data class DraftMenuItemInputDto(
    @SerializedName("category_name")
    val categoryName: String,
    val name: String,
    val description: String? = null,
    @SerializedName("base_price_minor")
    val basePriceMinor: Long,
    @SerializedName("image_path")
    val imagePath: String? = null,
    @SerializedName("option_groups")
    val optionGroups: List<DraftMenuOptionGroupInputDto> = emptyList(),
)

data class DraftMenuCategoryInputDto(
    val name: String,
    @SerializedName("sort_order")
    val sortOrder: Int = 0,
)

data class DraftMenuInputDto(
    val categories: List<DraftMenuCategoryInputDto> = emptyList(),
    val items: List<DraftMenuItemInputDto> = emptyList(),
)

data class SaveApplicationDraftRequest(
    @SerializedName("application_id")
    val applicationId: String? = null,
    @SerializedName("legal_name")
    val legalName: String,
    @SerializedName("brand_name")
    val brandName: String,
    val description: String = "",
    @SerializedName("business_type")
    val businessType: String,
    @SerializedName("contact_phone")
    val contactPhone: String,
    @SerializedName("contact_email")
    val contactEmail: String,
    @SerializedName("tin_number")
    val tinNumber: String = "",
    @SerializedName("logo_path")
    val logoPath: String? = null,
    @SerializedName("cover_path")
    val coverPath: String? = null,
    val branches: List<DraftBranchInputDto>? = null,
    @SerializedName("draft_menu")
    val draftMenu: DraftMenuInputDto? = null,
)

data class RestaurantApplicationDto(
    val id: String,
    @SerializedName("applicant_profile_id")
    val applicantProfileId: String? = null,
    @SerializedName("legal_name")
    val legalName: String = "",
    @SerializedName("brand_name")
    val brandName: String = "",
    val description: String = "",
    @SerializedName("business_type")
    val businessType: String = "",
    @SerializedName("contact_phone")
    val contactPhone: String = "",
    @SerializedName("contact_email")
    val contactEmail: String = "",
    @SerializedName("tin_number")
    val tinNumber: String = "",
    @SerializedName("logo_path")
    val logoPath: String? = null,
    @SerializedName("cover_path")
    val coverPath: String? = null,
    @SerializedName("draft_branches_json")
    val draftBranchesJson: JsonElement? = null,
    @SerializedName("draft_menu_json")
    val draftMenuJson: JsonElement? = null,
    val status: String = "DRAFT",
    @SerializedName("review_notes")
    val reviewNotes: String? = null,
    @SerializedName("approved_restaurant_id")
    val approvedRestaurantId: String? = null,
    @SerializedName("submitted_at")
    val submittedAt: String? = null,
    @SerializedName("reviewed_at")
    val reviewedAt: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    @SerializedName("restaurant_application_documents")
    val documents: List<ApplicationDocumentDto> = emptyList(),
)

data class ApplicationDocumentDto(
    val id: String,
    @SerializedName("application_id")
    val applicationId: String = "",
    @SerializedName("doc_type")
    val docType: String = "",
    @SerializedName("storage_path")
    val storagePath: String? = null,
    @SerializedName("file_name")
    val fileName: String = "",
    @SerializedName("mime_type")
    val mimeType: String = "",
    @SerializedName("byte_size")
    val byteSize: Long = 0L,
    @SerializedName("created_at")
    val createdAt: String? = null,
)

data class RestaurantApplicationListResponse(
    val ok: Boolean = false,
    val applications: List<RestaurantApplicationDto> = emptyList(),
)

data class RestaurantApplicationResponse(
    val ok: Boolean = false,
    val application: RestaurantApplicationDto? = null,
)

data class DocumentUploadUrlRequest(
    @SerializedName("application_id")
    val applicationId: String,
    @SerializedName("doc_type")
    val docType: String,
    @SerializedName("file_name")
    val fileName: String,
    @SerializedName("mime_type")
    val mimeType: String,
    @SerializedName("byte_size")
    val byteSize: Long,
)

data class DocumentUploadUrlResponse(
    val ok: Boolean = false,
    val uploadUrl: String? = null,
    val storagePath: String? = null,
    @SerializedName("doc_type")
    val docType: String? = null,
)

data class ConfirmDocumentRequest(
    @SerializedName("application_id")
    val applicationId: String,
    @SerializedName("doc_type")
    val docType: String,
    @SerializedName("storage_path")
    val storagePath: String,
    @SerializedName("file_name")
    val fileName: String,
    @SerializedName("mime_type")
    val mimeType: String,
    @SerializedName("byte_size")
    val byteSize: Long,
)

data class ConfirmDocumentResponse(
    val ok: Boolean = false,
    val document: ApplicationDocumentDto? = null,
)

data class ApplicationMediaUploadUrlRequest(
    @SerializedName("application_id")
    val applicationId: String,
    @SerializedName("file_name")
    val fileName: String,
    @SerializedName("mime_type")
    val mimeType: String,
    @SerializedName("byte_size")
    val byteSize: Long,
)

data class MediaUploadUrlResponse(
    val ok: Boolean = false,
    val uploadUrl: String? = null,
    val storagePath: String? = null,
)

data class SubmitApplicationRequest(
    @SerializedName("application_id")
    val applicationId: String,
)

// ============================================================================
// Admin review (`restaurants-admin-review`)
// ============================================================================

data class AdminReviewActionRequest(
    @SerializedName("application_id")
    val applicationId: String,
    val slug: String? = null,
    val notes: String? = null,
)

data class AdminApproveResultDto(
    @SerializedName("application_id")
    val applicationId: String? = null,
    @SerializedName("restaurant_id")
    val restaurantId: String? = null,
    val status: String? = null,
    @SerializedName("hydrated_items")
    val hydratedItems: Int = 0,
)

data class AdminApproveResponse(
    val ok: Boolean = false,
    val result: AdminApproveResultDto? = null,
)

data class AdminApplicationStatusResponse(
    val ok: Boolean = false,
    @SerializedName("application_id")
    val applicationId: String? = null,
    val status: String? = null,
)

data class AmIAdminResponse(
    val ok: Boolean = false,
    @SerializedName("is_platform_admin")
    val isPlatformAdmin: Boolean = false,
    @SerializedName("profile_id")
    val profileId: String? = null,
)

data class AdminListApplicationsRequest(
    val statuses: List<String>? = null,
    val status: String? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)

data class AdminPaginationDto(
    val limit: Int = 25,
    val offset: Int = 0,
    val count: Int = 0,
)

data class AdminPendingApplicationDto(
    val id: String,
    @SerializedName("applicant_profile_id")
    val applicantProfileId: String = "",
    val status: String = "SUBMITTED",
    @SerializedName("legal_name")
    val legalName: String = "",
    @SerializedName("brand_name")
    val brandName: String = "",
    val description: String = "",
    @SerializedName("business_type")
    val businessType: String = "",
    @SerializedName("contact_phone")
    val contactPhone: String = "",
    @SerializedName("contact_email")
    val contactEmail: String = "",
    @SerializedName("tin_number")
    val tinNumber: String = "",
    @SerializedName("logo_path")
    val logoPath: String? = null,
    @SerializedName("cover_path")
    val coverPath: String? = null,
    @SerializedName("branch_count")
    val branchCount: Int = 0,
    @SerializedName("draft_menu_item_count")
    val draftMenuItemCount: Int = 0,
    @SerializedName("submitted_at")
    val submittedAt: String? = null,
    @SerializedName("reviewed_at")
    val reviewedAt: String? = null,
    @SerializedName("review_notes")
    val reviewNotes: String? = null,
    @SerializedName("approved_restaurant_id")
    val approvedRestaurantId: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    val documents: List<ApplicationDocumentDto> = emptyList(),
)

data class AdminListApplicationsResponse(
    val ok: Boolean = false,
    val applications: List<AdminPendingApplicationDto> = emptyList(),
    val pagination: AdminPaginationDto? = null,
)

data class AdminInspectDocumentRequest(
    @SerializedName("application_id")
    val applicationId: String,
    @SerializedName("document_id")
    val documentId: String,
)

data class AdminInspectDocumentResponse(
    val ok: Boolean = false,
    @SerializedName("signed_url")
    val signedUrl: String? = null,
    @SerializedName("expires_in_seconds")
    val expiresInSeconds: Long? = null,
    val document: ApplicationDocumentDto? = null,
)

data class AdminRestaurantStatusRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    val reason: String,
)

data class AdminRestaurantStatusResponse(
    val ok: Boolean = false,
    @SerializedName("restaurant_id")
    val restaurantId: String? = null,
    val status: String? = null,
)

data class AdminListReportsRequest(
    val status: String? = null,
    val limit: Int? = null,
)

data class RestaurantReportDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("menu_item_id")
    val menuItemId: String? = null,
    @SerializedName("reporter_profile_id")
    val reporterProfileId: String? = null,
    val reason: String = "",
    val status: String = "OPEN",
    @SerializedName("resolution_notes")
    val resolutionNotes: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("resolved_at")
    val resolvedAt: String? = null,
)

data class AdminListReportsResponse(
    val ok: Boolean = false,
    val reports: List<RestaurantReportDto> = emptyList(),
)

data class AdminResolveReportRequest(
    @SerializedName("report_id")
    val reportId: String,
    val status: String,
    @SerializedName("resolution_notes")
    val resolutionNotes: String? = null,
)

data class AdminResolveReportResponse(
    val ok: Boolean = false,
    val report: RestaurantReportDto? = null,
)

data class AdminListAuditLogsRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String? = null,
    @SerializedName("action_filter")
    val actionFilter: String? = null,
    val limit: Int? = null,
)

data class RestaurantAuditLogDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String? = null,
    @SerializedName("actor_profile_id")
    val actorProfileId: String? = null,
    val action: String = "",
    @SerializedName("target_type")
    val targetType: String? = null,
    @SerializedName("target_id")
    val targetId: String? = null,
    @SerializedName("metadata_json")
    val metadataJson: JsonElement? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
)

data class AdminListAuditLogsResponse(
    val ok: Boolean = false,
    val logs: List<RestaurantAuditLogDto> = emptyList(),
)

// ============================================================================
// Staff invitations & members (`restaurants-staff`)
// ============================================================================

data class StaffInviteRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String? = null,
    val role: String,
    @SerializedName("invited_identifier")
    val invitedIdentifier: String,
    @SerializedName("invited_profile_id")
    val invitedProfileId: String? = null,
    @SerializedName("invited_adischat_user_id")
    val invitedAdischatUserId: String? = null,
    @SerializedName("invited_phone")
    val invitedPhone: String? = null,
)

data class StaffInviteResponse(
    val ok: Boolean = false,
    @SerializedName("invitation_id")
    val invitationId: String? = null,
    @SerializedName("invite_token")
    val inviteToken: String? = null,
    @SerializedName("expires_at")
    val expiresAt: String? = null,
)

data class StaffAcceptRequest(
    val token: String,
)

data class StaffMembershipDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String? = null,
    @SerializedName("profile_id")
    val profileId: String,
    val role: String = "",
    val status: String = "ACTIVE",
    @SerializedName("created_at")
    val createdAt: String? = null,
)

data class StaffAcceptResponse(
    val ok: Boolean = false,
    val membership: StaffMembershipDto? = null,
)

data class StaffListMembersRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String? = null,
)

data class StaffMemberSummaryDto(
    @SerializedName("staff_member_id")
    val staffMemberId: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String? = null,
    @SerializedName("profile_id")
    val profileId: String,
    @SerializedName("display_name")
    val displayName: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    val role: String = "",
    val status: String = "",
    @SerializedName("created_at")
    val createdAt: String? = null,
)

data class StaffListMembersResponse(
    val ok: Boolean = false,
    val members: List<StaffMemberSummaryDto> = emptyList(),
)

data class StaffListInvitationsRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String? = null,
)

data class StaffInvitationDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String? = null,
    val role: String = "",
    @SerializedName("invited_identifier")
    val invitedIdentifier: String? = null,
    val status: String = "",
    @SerializedName("expires_at")
    val expiresAt: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
)

data class StaffListInvitationsResponse(
    val ok: Boolean = false,
    val invitations: List<StaffInvitationDto> = emptyList(),
)

data class StaffRevokeInvitationRequest(
    @SerializedName("invitation_id")
    val invitationId: String,
)

data class StaffRevokeInvitationResponse(
    val ok: Boolean = false,
    @SerializedName("invitation_id")
    val invitationId: String? = null,
    val status: String? = null,
)

data class StaffRevokeRequest(
    @SerializedName("staff_member_id")
    val staffMemberId: String,
)

data class StaffRevokeResponse(
    val ok: Boolean = false,
    @SerializedName("staff_member_id")
    val staffMemberId: String? = null,
    val status: String? = null,
)

// ============================================================================
// Menu, branch & restaurant settings (`restaurants-menu`)
// ============================================================================

data class SetBranchAvailabilityRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String,
    @SerializedName("menu_item_id")
    val menuItemId: String,
    @SerializedName("is_available")
    val isAvailable: Boolean,
    @SerializedName("price_override_minor")
    val priceOverrideMinor: Long? = null,
)

data class CreateBranchRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    val code: String,
    val name: String,
    val address: String,
    val city: String? = null,
    @SerializedName("opening_hours_json")
    val openingHoursJson: JsonElement? = null,
    @SerializedName("require_staff_session_activation")
    val requireStaffSessionActivation: Boolean = true,
    @SerializedName("require_session_code")
    val requireSessionCode: Boolean = false,
    @SerializedName("is_dine_in_active")
    val isDineInActive: Boolean = false,
)

data class UpdateBranchRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String,
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    @SerializedName("opening_hours_json")
    val openingHoursJson: JsonElement? = null,
    @SerializedName("require_staff_session_activation")
    val requireStaffSessionActivation: Boolean? = null,
    @SerializedName("require_session_code")
    val requireSessionCode: Boolean? = null,
    @SerializedName("is_dine_in_active")
    val isDineInActive: Boolean? = null,
)

data class BranchMutationResponse(
    val ok: Boolean = false,
    val branch: RestaurantBranchDto? = null,
)

data class UpdateRestaurantSettingsRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    val description: String? = null,
    @SerializedName("logo_path")
    val logoPath: String? = null,
    @SerializedName("cover_path")
    val coverPath: String? = null,
)

data class RestaurantSettingsResponse(
    val ok: Boolean = false,
    val restaurant: RestaurantSummaryDto? = null,
)

data class UpsertCategoryRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    val id: String? = null,
    val name: String,
    @SerializedName("sort_order")
    val sortOrder: Int? = null,
    @SerializedName("is_active")
    val isActive: Boolean? = null,
)

data class CategoryMutationResponse(
    val ok: Boolean = false,
    val category: MenuCategoryDto? = null,
)

data class UpsertMenuItemRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    val id: String? = null,
    @SerializedName("category_id")
    val categoryId: String,
    val name: String,
    val description: String? = null,
    @SerializedName("base_price_minor")
    val basePriceMinor: Long,
    @SerializedName("is_active")
    val isActive: Boolean? = null,
)

data class MenuItemMutationResponse(
    val ok: Boolean = false,
    val item: MenuItemDto? = null,
)

data class UpsertOptionGroupRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    val id: String? = null,
    @SerializedName("menu_item_id")
    val menuItemId: String,
    val name: String,
    @SerializedName("is_required")
    val isRequired: Boolean? = null,
    @SerializedName("min_select")
    val minSelect: Int? = null,
    @SerializedName("max_select")
    val maxSelect: Int? = null,
    @SerializedName("sort_order")
    val sortOrder: Int? = null,
)

data class OptionGroupMutationResponse(
    val ok: Boolean = false,
    @SerializedName("option_group")
    val optionGroup: MenuOptionGroupDto? = null,
)

data class UpsertOptionRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    val id: String? = null,
    @SerializedName("option_group_id")
    val optionGroupId: String,
    val name: String,
    @SerializedName("price_delta_minor")
    val priceDeltaMinor: Long? = null,
    @SerializedName("is_available")
    val isAvailable: Boolean? = null,
    @SerializedName("sort_order")
    val sortOrder: Int? = null,
)

data class OptionMutationResponse(
    val ok: Boolean = false,
    val option: MenuOptionDto? = null,
)

data class AttachMenuItemImageRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("menu_item_id")
    val menuItemId: String,
    @SerializedName("storage_path")
    val storagePath: String,
    @SerializedName("is_primary")
    val isPrimary: Boolean? = null,
    @SerializedName("sort_order")
    val sortOrder: Int? = null,
)

data class MenuItemImageMutationResponse(
    val ok: Boolean = false,
    val image: MenuItemImageDto? = null,
)

data class RemoveMenuItemImageRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("image_id")
    val imageId: String,
)

data class RemoveMenuItemImageResponse(
    val ok: Boolean = false,
    val removed: Boolean = false,
)

data class PhotoUploadUrlRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("file_name")
    val fileName: String,
    @SerializedName("mime_type")
    val mimeType: String? = null,
    @SerializedName("byte_size")
    val byteSize: Long? = null,
)

data class PublishLiveRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    val enable: Boolean,
)

data class PublishLiveResponse(
    val ok: Boolean = false,
    @SerializedName("is_live_ordering_enabled")
    val isLiveOrderingEnabled: Boolean = false,
)

// ============================================================================
// Tables, QR codes & dining sessions (`restaurants-tables-qr`)
// ============================================================================

data class ResolveQrRequest(
    val token: String,
    @SerializedName("session_code")
    val sessionCode: String? = null,
)

data class ResolvedEntityDto(
    val id: String,
    val name: String = "",
)

data class ResolvedTableDto(
    val id: String,
    val label: String = "",
)

data class ResolveQrResponse(
    val ok: Boolean = false,
    val restaurant: ResolvedEntityDto? = null,
    val branch: ResolvedEntityDto? = null,
    val table: ResolvedTableDto? = null,
    @SerializedName("dining_session_id")
    val diningSessionId: String? = null,
    @SerializedName("expires_at")
    val expiresAt: String? = null,
)

data class ListTablesRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String,
)

data class OpenDiningSessionSummaryDto(
    val id: String,
    @SerializedName("expires_at")
    val expiresAt: String? = null,
    @SerializedName("has_session_code")
    val hasSessionCode: Boolean = false,
)

data class DiningTableWithStatusDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String,
    @SerializedName("table_code")
    val tableCode: String = "",
    val label: String = "",
    val zone: String = "",
    val capacity: Int = 4,
    @SerializedName("is_active")
    val isActive: Boolean = true,
    @SerializedName("active_qr_version")
    val activeQrVersion: Int? = null,
    @SerializedName("active_qr_status")
    val activeQrStatus: String? = null,
    @SerializedName("open_session")
    val openSession: OpenDiningSessionSummaryDto? = null,
)

data class ListTablesResponse(
    val ok: Boolean = false,
    val tables: List<DiningTableWithStatusDto> = emptyList(),
)

data class UpsertTableRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String,
    val id: String? = null,
    @SerializedName("table_code")
    val tableCode: String,
    val label: String,
    val zone: String? = null,
    val capacity: Int? = null,
    @SerializedName("is_active")
    val isActive: Boolean? = null,
)

data class DiningTableDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String,
    @SerializedName("table_code")
    val tableCode: String = "",
    val label: String = "",
    val zone: String = "",
    val capacity: Int = 4,
    @SerializedName("is_active")
    val isActive: Boolean = true,
)

data class TableMutationResponse(
    val ok: Boolean = false,
    val table: DiningTableDto? = null,
)

data class TableScopedRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String,
    @SerializedName("table_id")
    val tableId: String,
)

data class RotateQrResponse(
    val ok: Boolean = false,
    @SerializedName("qr_token_id")
    val qrTokenId: String? = null,
    @SerializedName("qr_version")
    val qrVersion: Int? = null,
    @SerializedName("qr_url")
    val qrUrl: String? = null,
    @SerializedName("raw_token")
    val rawToken: String? = null,
)

data class OpenSessionResponse(
    val ok: Boolean = false,
    @SerializedName("dining_session_id")
    val diningSessionId: String? = null,
    @SerializedName("expires_at")
    val expiresAt: String? = null,
    @SerializedName("session_code")
    val sessionCode: String? = null,
)

data class DiningSessionScopedRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String,
    @SerializedName("dining_session_id")
    val diningSessionId: String,
)

data class RotateSessionCodeResponse(
    val ok: Boolean = false,
    @SerializedName("dining_session_id")
    val diningSessionId: String? = null,
    @SerializedName("session_code")
    val sessionCode: String? = null,
)

// ============================================================================
// Dine-in orders, status transitions, payment-at-restaurant & reporting
// ============================================================================

data class SubmitOrderItemRequest(
    @SerializedName("menu_item_id")
    val menuItemId: String,
    val quantity: Int,
    @SerializedName("option_ids")
    val optionIds: List<String>? = null,
    @SerializedName("special_instructions")
    val specialInstructions: String? = null,
)

data class SubmitDineInOrderRequest(
    @SerializedName("dining_session_id")
    val diningSessionId: String,
    @SerializedName("branch_id")
    val branchId: String? = null,
    @SerializedName("idempotency_key")
    val idempotencyKey: String,
    @SerializedName("order_type")
    val orderType: String = "DINE_IN",
    @SerializedName("payment_method")
    val paymentMethod: String = "PAY_AT_RESTAURANT",
    @SerializedName("customer_notes")
    val customerNotes: String? = null,
    val items: List<SubmitOrderItemRequest>,
)

data class SubmitOrderResultDto(
    @SerializedName("order_id")
    val orderId: String,
    @SerializedName("order_number")
    val orderNumber: String = "",
    val status: String = "",
    @SerializedName("payment_method")
    val paymentMethod: String = "PAY_AT_RESTAURANT",
    @SerializedName("payment_status")
    val paymentStatus: String = "UNPAID",
    @SerializedName("total_minor")
    val totalMinor: Long = 0L,
    @SerializedName("idempotent_replay")
    val idempotentReplay: Boolean = false,
)

data class SubmitOrderResponse(
    val ok: Boolean = false,
    val order: SubmitOrderResultDto? = null,
)

data class TransitionOrderRequest(
    @SerializedName("order_id")
    val orderId: String,
    @SerializedName("target_status")
    val targetStatus: String,
    val reason: String? = null,
)

data class TransitionOrderResultDto(
    @SerializedName("order_id")
    val orderId: String,
    @SerializedName("from_status")
    val fromStatus: String = "",
    @SerializedName("to_status")
    val toStatus: String = "",
)

data class TransitionOrderResponse(
    val ok: Boolean = false,
    val result: TransitionOrderResultDto? = null,
)

data class ConfirmPaymentRequest(
    @SerializedName("order_id")
    val orderId: String,
    @SerializedName("receipt_ref")
    val receiptRef: String? = null,
    val notes: String? = null,
)

data class ConfirmPaymentResultDto(
    @SerializedName("order_id")
    val orderId: String,
    @SerializedName("payment_status")
    val paymentStatus: String = "",
    @SerializedName("order_status")
    val orderStatus: String = "",
    @SerializedName("amount_minor")
    val amountMinor: Long = 0L,
)

data class ConfirmPaymentResponse(
    val ok: Boolean = false,
    val result: ConfirmPaymentResultDto? = null,
)

data class ReportContentRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("menu_item_id")
    val menuItemId: String? = null,
    val reason: String,
)

data class ReportContentResponse(
    val ok: Boolean = false,
    val report: RestaurantReportDto? = null,
)

// PostgREST order & tracking read records
data class OrderRecordDto(
    val id: String,
    @SerializedName("order_number")
    val orderNumber: String = "",
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String,
    @SerializedName("dining_session_id")
    val diningSessionId: String? = null,
    @SerializedName("table_id")
    val tableId: String? = null,
    @SerializedName("customer_profile_id")
    val customerProfileId: String,
    @SerializedName("order_type")
    val orderType: String = "DINE_IN",
    @SerializedName("payment_method")
    val paymentMethod: String = "PAY_AT_RESTAURANT",
    @SerializedName("payment_status")
    val paymentStatus: String = "UNPAID",
    val status: String = "",
    @SerializedName("subtotal_minor")
    val subtotalMinor: Long = 0L,
    @SerializedName("service_charge_minor")
    val serviceChargeMinor: Long = 0L,
    @SerializedName("tax_minor")
    val taxMinor: Long = 0L,
    @SerializedName("total_minor")
    val totalMinor: Long = 0L,
    @SerializedName("customer_notes")
    val customerNotes: String? = null,
    @SerializedName("rejected_reason")
    val rejectedReason: String? = null,
    @SerializedName("cancelled_reason")
    val cancelledReason: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
)

data class OrderItemRecordDto(
    val id: String,
    @SerializedName("order_id")
    val orderId: String,
    @SerializedName("menu_item_id")
    val menuItemId: String,
    @SerializedName("item_name_snapshot")
    val itemNameSnapshot: String = "",
    @SerializedName("unit_price_minor_snapshot")
    val unitPriceMinorSnapshot: Long = 0L,
    val quantity: Int = 1,
    @SerializedName("line_total_minor")
    val lineTotalMinor: Long = 0L,
    @SerializedName("options_delta_minor_snapshot")
    val optionsDeltaMinorSnapshot: Long = 0L,
    @SerializedName("selected_options_snapshot_json")
    val selectedOptionsSnapshotJson: JsonElement? = null,
    @SerializedName("special_instructions")
    val specialInstructions: String? = null,
)

data class OrderStatusEventDto(
    val id: String,
    @SerializedName("order_id")
    val orderId: String,
    @SerializedName("from_status")
    val fromStatus: String? = null,
    @SerializedName("to_status")
    val toStatus: String = "",
    val reason: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
)

data class OrderPaymentRecordDto(
    val id: String,
    @SerializedName("order_id")
    val orderId: String,
    @SerializedName("payment_method")
    val paymentMethod: String = "PAY_AT_RESTAURANT",
    val status: String = "UNPAID",
    @SerializedName("amount_minor")
    val amountMinor: Long = 0L,
    @SerializedName("receipt_ref")
    val receiptRef: String? = null,
    @SerializedName("confirmed_at")
    val confirmedAt: String? = null,
)

data class KitchenTicketDto(
    val id: String,
    @SerializedName("order_id")
    val orderId: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String,
    @SerializedName("kitchen_status")
    val kitchenStatus: String = "",
    @SerializedName("ticket_number")
    val ticketNumber: Long? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
)

data class RestaurantNotificationDto(
    val id: String,
    @SerializedName("recipient_profile_id")
    val recipientProfileId: String,
    val type: String = "",
    val title: String = "",
    val body: String = "",
    @SerializedName("order_id")
    val orderId: String? = null,
    @SerializedName("read_at")
    val readAt: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
)

// ============================================================================
// Phase R4A backend contract additions
// ============================================================================

data class AuthorizedWorkspaceResponse(
    val ok: Boolean = false,
    val memberships: List<StaffMembershipDto> = emptyList(),
    val restaurants: List<RestaurantSummaryDto> = emptyList(),
    val branches: List<RestaurantBranchDto> = emptyList(),
)

data class RestaurantScopedRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
)

data class ActivateRestaurantResponse(
    val ok: Boolean = false,
    @SerializedName("restaurant_id")
    val restaurantId: String? = null,
    val status: String? = null,
    @SerializedName("is_live_ordering_enabled")
    val isLiveOrderingEnabled: Boolean = false,
    @SerializedName("already_active")
    val alreadyActive: Boolean = false,
)

data class SyncBranchOrdersRequest(
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String,
    val since: String? = null,
    @SerializedName("after_id")
    val afterId: String? = null,
    val limit: Int? = null,
)

data class SyncBranchOrderDto(
    val id: String,
    @SerializedName("restaurant_id")
    val restaurantId: String,
    @SerializedName("branch_id")
    val branchId: String,
    @SerializedName("table_id")
    val tableId: String? = null,
    @SerializedName("dining_session_id")
    val diningSessionId: String? = null,
    @SerializedName("order_number")
    val orderNumber: String = "",
    @SerializedName("order_type")
    val orderType: String = "DINE_IN",
    val status: String = "",
    @SerializedName("payment_method")
    val paymentMethod: String = "PAY_AT_RESTAURANT",
    @SerializedName("payment_status")
    val paymentStatus: String = "UNPAID",
    @SerializedName("subtotal_minor")
    val subtotalMinor: Long = 0L,
    @SerializedName("service_charge_minor")
    val serviceChargeMinor: Long = 0L,
    @SerializedName("tax_minor")
    val taxMinor: Long = 0L,
    @SerializedName("total_minor")
    val totalMinor: Long = 0L,
    val currency: String = "ETB",
    @SerializedName("customer_notes")
    val customerNotes: String? = null,
    @SerializedName("rejected_reason")
    val rejectedReason: String? = null,
    @SerializedName("cancelled_reason")
    val cancelledReason: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    @SerializedName("order_items")
    val orderItems: List<OrderItemRecordDto> = emptyList(),
    @SerializedName("kitchen_tickets")
    val kitchenTickets: List<KitchenTicketDto> = emptyList(),
    @SerializedName("order_status_events")
    val orderStatusEvents: List<OrderStatusEventDto> = emptyList(),
)

data class SyncBranchOrdersResponse(
    val ok: Boolean = false,
    @SerializedName("branch_id")
    val branchId: String? = null,
    @SerializedName("sync_mode")
    val syncMode: String = "snapshot",
    val orders: List<SyncBranchOrderDto> = emptyList(),
    @SerializedName("next_cursor")
    val nextCursor: String? = null,
    @SerializedName("next_cursor_id")
    val nextCursorId: String? = null,
    @SerializedName("has_more")
    val hasMore: Boolean = false,
    @SerializedName("server_time")
    val serverTime: String? = null,
)

// ============================================================================
// Phase B4A Desktop QR Approval & WebView Handoff
// ============================================================================

data class AuthorizeQrChallengeRequest(
    @SerializedName("challenge_id")
    val challengeId: String,
    @SerializedName("challenge_nonce")
    val challengeNonce: String,
    val action: String? = null,
)

data class AuthorizeQrChallengeResponse(
    val ok: Boolean = false,
    val authorized: Boolean = false,
    val role: String? = null,
)

data class CreateHandoffTicketResponse(
    val ok: Boolean = false,
    @SerializedName("ticket_id")
    val ticketId: String? = null,
    val ticket: String? = null,
    @SerializedName("expires_at")
    val expiresAt: String? = null,
)

data class ConsumeHandoffTicketRequest(
    val ticket: String,
)

data class ConsumeHandoffTicketResponse(
    val ok: Boolean = false,
    @SerializedName("profile_id")
    val profileId: String? = null,
    val session: RestaurantsAuthSessionDto? = null,
)

data class RestaurantCenterEligibility(
    val isEligible: Boolean,
    val role: String? = null,
    val restaurantId: String? = null,
    val branchId: String? = null,
)

