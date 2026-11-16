package com.adischat.app.data.model

/**
 * A single post in a channel's broadcast feed.
 * postType: "text" | "image" | "video" | "link"
 */
data class ChannelPost(
    val id:      String  = "",
    val channelId:     String  = "",
    val authorUid:     String  = "",
    val authorName:    String  = "",
    val authorPhotoUrl: String = "",
    val title:      String  = "",
    val body:      String  = "",
    val postType:      String  = "text",
    val mediaUrl:      String  = "",
    val mediaMimeType: String  = "",
    val linkUrl:       String  = "",
    val linkTitle:     String  = "",
    val linkImage:     String  = "",
    val pinned:      Boolean = false,
    val views:      Int     = 0,
    val reactions:     Map<String, String> = emptyMap(),  // uid - emoji
    val createdAt:     Long    = 0L,
    val updatedAt:     Long    = 0L,
    val deleted:       Boolean = false,
    val localStatus:   String  = "", // publishing | published | failed
    val localProgress: Int     = 100,
    val localError:    String  = "",
) {
    /** Aggregated emoji counts: emoji - count */
    val reactionCounts: Map<String, Int>
        get() = reactions.values.groupingBy { it }.eachCount()

    /** True when this is a video (heuristic: mime or url contains video) */
    val isVideo: Boolean
        get() = postType == "video" ||
        mediaMimeType.startsWith("video/") ||
        mediaUrl.contains(".mp4", ignoreCase = true) ||
        mediaUrl.contains(".mov", ignoreCase = true)
}

data class ChannelPostsPage(
    val posts:      List<ChannelPost> = emptyList(),
    val hasMore:    Boolean      = false,
    val nextCursor: String?      = null,
)
