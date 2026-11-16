package com.adischat.app.data.model

import com.google.gson.annotations.SerializedName

data class AppUser(
    val uid:      String  = "",
    val name:      String  = "",
    val username:      String  = "",
    val phone:      String  = "",
    val email:      String  = "",
    val dob:      String  = "",
    @SerializedName("photoUrl", alternate = ["photo_url", "avatarUrl", "avatar_url", "profilePicture", "profile_picture", "imageUrl", "image_url"])
    val photoUrl:      String  = "",
    val role:      String  = "user",    // "user" | "merchant" | "admin"
    val balance:      Double  = 0.0,
    val points:      Int     = 0,
    val orders:      Int     = 0,
    val emailVerified:      Boolean = false,
    @SerializedName("verification_level", alternate = ["verificationLevel"])
    val verificationLevel: Int = 0,
    @SerializedName("verified", alternate = ["is_verified", "isVerified", "blue_badge", "blueBadge"])
    val verified: Boolean = false,
    // 0 = unverified  |  1 = email verified  |  2 = profile complete  |  3 = blue badge
    @SerializedName("verification_request_status", alternate = ["verificationRequestStatus"])
    val verificationRequestStatus: String? = null,
    // null | "pending" | "approved" | "rejected"
    @SerializedName("lastSeenVisibility", alternate = ["last_seen_visibility"])
    val lastSeenVisibility: String = "public",
    @SerializedName("onlineStatusVisibility", alternate = ["online_status_visibility"])
    val onlineStatusVisibility: String = "same_as_last_seen",
    @SerializedName("profilePhotoVisibility", alternate = ["profile_photo_visibility"])
    val profilePhotoVisibility: String = "everyone",
    @SerializedName("phoneNumberVisibility", alternate = ["phone_number_visibility"])
    val phoneNumberVisibility: String = "nobody",
    @SerializedName("readReceiptsEnabled", alternate = ["read_receipts_enabled"])
    val readReceiptsEnabled: Boolean = true,
    @SerializedName("whoCanMessageMe", alternate = ["who_can_message_me"])
    val whoCanMessageMe: String = "everyone",
    @SerializedName("whoCanCallMe", alternate = ["who_can_call_me"])
    val whoCanCallMe: String = "everyone",
    @SerializedName("whoCanAddMeToGroups", alternate = ["who_can_add_me_to_groups"])
    val whoCanAddMeToGroups: String = "everyone",
    @SerializedName("accountStatus", alternate = ["account_status"])
    val accountStatus: String = "active",
    @SerializedName("deletedAt", alternate = ["deleted_at"])
    val deletedAt: Long = 0L,
)

/** Lightweight user card returned by /api/users/search and in conversations */
data class UserSummary(
    val uid:      String = "",
    val name:      String = "",
    val username:      String = "",
    val phone:      String = "",
    @SerializedName("photoUrl", alternate = ["photo_url", "avatarUrl", "avatar_url", "profilePicture", "profile_picture", "imageUrl", "image_url"])
    val photoUrl:      String = "",
    val role:      String = "user",
    @SerializedName("verification_level", alternate = ["verificationLevel"])
    val verificationLevel: Int = 0,
    @SerializedName("verified", alternate = ["is_verified", "isVerified", "blue_badge", "blueBadge"])
    val verified: Boolean = false,
)

data class ScanEntry(
    val data:      String = "",
    val type:      String = "url",  // url | text | phone | email | wifi
    val timestamp: Long   = 0L,
)

/** Returned by GET /api/verification/status */
data class VerificationStatus(
    @SerializedName("verification_level", alternate = ["verificationLevel"])
    val verificationLevel: Int = 0,
    @SerializedName("verification_request_status", alternate = ["verificationRequestStatus"])
    val verificationRequestStatus: String? = null,
    val request:      VerificationRequest? = null,
)

data class VerificationRequest(
    val status:      String  = "pending",
    val submittedAt:     Long    = 0L,
    val reviewedAt:      Long?   = null,
    val rejectionReason: String? = null,
    val documentType:    String  = "",
)

/** Platform-wide statistics returned by GET /api/admin/stats */
data class AdminStats(
    val totalUsers:      Int = 0,
    val unverified:      Int = 0,
    val emailVerified:      Int = 0,
    val profileComplete:       Int = 0,
    val blueBadge:      Int = 0,
    val pendingVerifications:  Int = 0,
    val approvedVerifications: Int = 0,
    val rejectedVerifications: Int = 0,
)

/** Admin view of a single verification request */
data class AdminVerificationRequest(
    val uid:      String  = "",
    val fullName:      String  = "",
    val documentType:    String  = "",
    val documentUrl:     String  = "",
    val selfieUrl:       String  = "",
    val status:      String  = "pending",
    val submittedAt:     Long    = 0L,
    val reviewedAt:      Long?   = null,
    val rejectionReason: String? = null,
    val userName:      String  = "",
    val userEmail:       String  = "",
    val userPhone:       String  = "",
    val userPhoto:       String  = "",
)
