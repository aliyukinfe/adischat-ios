package com.adischat.app.data.model

data class AppUser(
    val uid:                       String  = "",
    val name:                      String  = "",
    val username:                  String  = "",
    val phone:                     String  = "",
    val email:                     String  = "",
    val dob:                       String  = "",
    val photoUrl:                  String  = "",
    val role:                      String  = "user",    // "user" | "merchant" | "admin"
    val balance:                   Double  = 0.0,
    val points:                    Int     = 0,
    val orders:                    Int     = 0,
    val emailVerified:             Boolean = false,
    val verificationLevel:         Int     = 0,
    // 0 = unverified  |  1 = email verified  |  2 = profile complete  |  3 = blue badge
    val verificationRequestStatus: String? = null,
    // null | "pending" | "approved" | "rejected"
)

/** Lightweight user card returned by /api/users/search and in conversations */
data class UserSummary(
    val uid:               String = "",
    val name:              String = "",
    val username:          String = "",
    val phone:             String = "",
    val photoUrl:          String = "",
    val role:              String = "user",
    val verificationLevel: Int    = 0,
)

data class ScanEntry(
    val data:      String = "",
    val type:      String = "url",  // url | text | phone | email | wifi
    val timestamp: Long   = 0L,
)

/** Returned by GET /api/verification/status */
data class VerificationStatus(
    val verificationLevel:         Int           = 0,
    val verificationRequestStatus: String?       = null,
    val request:                   VerificationRequest? = null,
)

data class VerificationRequest(
    val status:          String  = "pending",
    val submittedAt:     Long    = 0L,
    val reviewedAt:      Long?   = null,
    val rejectionReason: String? = null,
    val documentType:    String  = "",
)

/** Platform-wide statistics returned by GET /api/admin/stats */
data class AdminStats(
    val totalUsers:            Int = 0,
    val unverified:            Int = 0,
    val emailVerified:         Int = 0,
    val profileComplete:       Int = 0,
    val blueBadge:             Int = 0,
    val pendingVerifications:  Int = 0,
    val approvedVerifications: Int = 0,
    val rejectedVerifications: Int = 0,
)

/** Admin view of a single verification request */
data class AdminVerificationRequest(
    val uid:             String  = "",
    val fullName:        String  = "",
    val documentType:    String  = "",
    val documentUrl:     String  = "",
    val selfieUrl:       String  = "",
    val status:          String  = "pending",
    val submittedAt:     Long    = 0L,
    val reviewedAt:      Long?   = null,
    val rejectionReason: String? = null,
    val userName:        String  = "",
    val userEmail:       String  = "",
    val userPhone:       String  = "",
    val userPhoto:       String  = "",
)
