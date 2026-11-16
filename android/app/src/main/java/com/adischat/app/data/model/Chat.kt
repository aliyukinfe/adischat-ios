package com.adischat.app.data.model

import com.google.gson.annotations.SerializedName

data class Conversation(
    val id:      String = "",
    val type:      String = "support",  // "support" | "direct"
    @SerializedName("otherUserId", alternate = ["other_user_id", "userId", "user_id", "uid"])
    val otherUserId:       String = "",
    @SerializedName("otherUserName", alternate = ["other_user_name", "userName", "user_name", "name"])
    val otherUserName:     String = "",
    @SerializedName("otherUserPhotoUrl", alternate = ["other_user_photo_url", "user_photo_url", "photoUrl", "photo_url", "avatarUrl", "avatar_url", "profilePicture", "profile_picture"])
    val otherUserPhotoUrl: String = "",
    val lastMessage:       String = "",
    val lastSenderRole:    String = "",
    val lastMessageAt:     Long   = 0L,
    val unreadCount:       Int    = 0,
    @SerializedName("other_user_verification_level", alternate = ["otherUserVerificationLevel", "verification_level", "verificationLevel"])
    val otherUserVerificationLevel: Int = 0,
    @SerializedName("other_user_verified", alternate = ["otherUserVerified", "verified", "is_verified", "isVerified", "blue_badge", "blueBadge"])
    val otherUserVerified: Boolean = false,
)

data class CommunityRoom(
    val id:      String = "",
    val type:      String = "channel",
    val name:      String = "",
    val description:   String = "",
    val ownerUid:      String = "",
    @SerializedName("photoUrl", alternate = ["photo_url", "avatarUrl", "avatar_url", "profilePicture", "profile_picture", "imageUrl", "image_url"])
    val photoUrl:      String = "",
    val memberCount:   Int    = 0,
    val joined:      Boolean = false,
    val lastMessage:   String = "",
    val lastMessageAt: Long   = 0L,
    val lastPostAt: Long = 0L,
    val lastPostPreview: String = "",
    val lastPostAuthor: String = "",
    val unreadCount: Int = 0,
    val verified:      Boolean = false,
    val createdAt:     Long   = 0L,
)

data class Message(
    val id:      String = "",
    val senderId:      String = "",
    val senderRole:    String = "user",
    @SerializedName("senderName", alternate = ["sender_name", "name"])
    val senderName:    String = "",
    @SerializedName("senderPhotoUrl", alternate = ["sender_photo_url", "photoUrl", "photo_url", "avatarUrl", "avatar_url", "profilePicture", "profile_picture"])
    val senderPhotoUrl: String = "",
    @SerializedName("sender_verification_level", alternate = ["senderVerificationLevel", "verification_level", "verificationLevel"])
    val senderVerificationLevel: Int = 0,
    @SerializedName("sender_verified", alternate = ["senderVerified", "verified", "is_verified", "isVerified", "blue_badge", "blueBadge"])
    val senderVerified: Boolean = false,
    val type:      String = "text",   // text | image | video | voice | call
    val text:      String = "",
    @SerializedName("mediaUrl", alternate = ["media_url", "url", "fileUrl", "file_url"])
    val mediaUrl:      String = "",
    @SerializedName("mediaMimeType", alternate = ["media_mime_type", "mimeType", "mime_type", "contentType", "content_type"])
    val mediaMimeType: String = "",
    val durationMs:    Long   = 0L,
    // Call records (type == "call") - Telegram-style in-timeline call events.
    @SerializedName("callType", alternate = ["call_type"])
    val callType:      String = "",      // voice | video
    @SerializedName("callStatus", alternate = ["call_status"])
    val callStatus:    String = "",      // completed | missed | rejected | cancelled | failed
    @SerializedName("callDuration", alternate = ["call_duration"])
    val callDuration:  Int    = 0,      // seconds
    @SerializedName("delivered_at", alternate = ["deliveredAt"])
    val deliveredAt:   Long   = 0L,
    @SerializedName("read_at", alternate = ["readAt", "seen_at", "seenAt"])
    val readAt:      Long   = 0L,
    @SerializedName("delivery_status", alternate = ["deliveryStatus", "status"])
    val deliveryStatus: String = "",
    @SerializedName("forwarded", alternate = ["isForwarded", "is_forwarded"])
    val forwarded:     Boolean = false,
    @SerializedName("edited", alternate = ["isEdited", "is_edited"])
    val edited:      Boolean = false,
        @SerializedName("edited_at", alternate = ["editedAt", "updated_at", "updatedAt"])
    val editedAt:      Long = 0L,
    @SerializedName("deleted_at", alternate = ["deletedAt"])
    val deletedAt:     Long = 0L,
    @SerializedName("reply_to_message_id", alternate = ["replyToMessageId", "reply_message_id", "replyMessageId"])
    val replyToMessageId: String = "",
    @SerializedName("reply_sender_name", alternate = ["replySenderName", "replyToSenderName"])
    val replySenderName: String = "",
    @SerializedName("reply_preview", alternate = ["replyPreview", "replyText", "replySnippet"])
    val replyPreview: String = "",
    @SerializedName("forwarded_from_message_id", alternate = ["forwardedFromMessageId"])
    val forwardedFromMessageId: String = "",
    @SerializedName("forwarded_from_sender_name", alternate = ["forwardedFromSenderName"])
    val forwardedFromSenderName: String = "",
    val reactions:     Map<String, String> = emptyMap(),
    val deleted:       Boolean = false,
    val timestamp:     Long   = 0L,
    val localStatus:   String = "", // sending | uploading | sent | delivered | seen | failed
    val localProgress: Int    = 100,
    val localError:    String = "",
)
