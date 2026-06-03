package com.adischat.app.data.model

data class Conversation(
    val id:                String = "",
    val type:              String = "support",  // "support" | "direct"
    val otherUserId:       String = "",
    val otherUserName:     String = "",
    val otherUserPhotoUrl: String = "",
    val lastMessage:       String = "",
    val lastSenderRole:    String = "",
    val lastMessageAt:     Long   = 0L,
    val unreadCount:       Int    = 0,
)

data class CommunityRoom(
    val id:            String = "",
    val type:          String = "channel",
    val name:          String = "",
    val description:   String = "",
    val ownerUid:      String = "",
    val photoUrl:      String = "",
    val memberCount:   Int    = 0,
    val joined:        Boolean = false,
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
    val id:            String = "",
    val senderId:      String = "",
    val senderRole:    String = "user",
    val senderName:    String = "",
    val senderPhotoUrl: String = "",
    val type:          String = "text",   // text | image | video | voice
    val text:          String = "",
    val mediaUrl:      String = "",
    val mediaMimeType: String = "",
    val durationMs:    Long   = 0L,
    val reactions:     Map<String, String> = emptyMap(),
    val deleted:       Boolean = false,
    val timestamp:     Long   = 0L,
)
