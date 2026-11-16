package com.adischat.app.data.model

import android.net.Uri
import com.google.gson.annotations.SerializedName

enum class AdisLinkType {
    @SerializedName("user")
    USER,

    @SerializedName("channel")
    CHANNEL,

    @SerializedName("group")
    GROUP,
}

data class AdisLinkTarget(
    val slug: String = "",
    val type: AdisLinkType = AdisLinkType.USER,
    val user: UserSummary? = null,
    val community: CommunityRoom? = null,
) {
    val isCommunity: Boolean
        get() = type == AdisLinkType.CHANNEL || type == AdisLinkType.GROUP
}

object AdisLinks {
    const val HOST = "me.adischat.com"

    fun user(username: String): String =
        "https://$HOST/${username.cleanAdisSlug()}"

    fun channel(slugOrId: String): String =
        "https://$HOST/channel/${slugOrId.cleanAdisSlug()}"

    fun group(slugOrId: String): String =
        "https://$HOST/group/${slugOrId.cleanAdisSlug()}"

    fun appUser(username: String): String =
        "adischat://link/user/${username.cleanAdisSlug()}"

    fun appChannel(slugOrId: String): String =
        "adischat://link/channel/${slugOrId.cleanAdisSlug()}"

    fun appGroup(slugOrId: String): String =
        "adischat://link/group/${slugOrId.cleanAdisSlug()}"

    fun parse(rawUrl: String): AdisLinkTarget? {
        val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return null
        val isWebLink = uri.scheme in listOf("http", "https") && uri.host.equals(HOST, ignoreCase = true)
        val isAppLink = uri.scheme.equals("adischat", ignoreCase = true) &&
        uri.host?.lowercase() in listOf("link", "user", "channel", "group")
        if (!isWebLink && !isAppLink) return null

        val segments = uri.pathSegments.filter { it.isNotBlank() }
        val rawSlug = when {
        isAppLink && uri.host.equals("user", ignoreCase = true) -> uri.getQueryParameter("username") ?: segments.firstOrNull()
        isAppLink && uri.host.equals("channel", ignoreCase = true) -> uri.getQueryParameter("id") ?: segments.firstOrNull()
        isAppLink && uri.host.equals("group", ignoreCase = true) -> uri.getQueryParameter("id") ?: segments.firstOrNull()
        else -> segments.firstOrNull()
        }?.trim()?.removePrefix("@") ?: return null
        if (rawSlug.isBlank()) return null

        val explicitType = uri.getQueryParameter("type")?.lowercase()
        val firstSegment = if (isAppLink && uri.host.equals("link", ignoreCase = true)) {
        segments.firstOrNull()?.lowercase()
        } else {
        uri.host?.lowercase().takeIf { isAppLink && it in listOf("user", "channel", "group") }
        ?: segments.firstOrNull()?.lowercase()
        }
        val type = when {
        explicitType == "channel" || firstSegment in listOf("c", "channel") -> AdisLinkType.CHANNEL
        explicitType == "group" || firstSegment in listOf("g", "group") -> AdisLinkType.GROUP
        else -> AdisLinkType.USER
        }
        val slug = when {
        isWebLink && firstSegment in listOf("u", "user", "c", "channel", "g", "group") -> segments.getOrNull(1)
        isAppLink && uri.host.equals("link", ignoreCase = true) && firstSegment in listOf("u", "user", "c", "channel", "g", "group") -> segments.getOrNull(1)
        else -> rawSlug
        }?.removePrefix("@")?.trim().orEmpty().ifBlank { rawSlug }

        return if (slug.isBlank()) null else AdisLinkTarget(slug = slug, type = type)
    }
}

private fun String.cleanAdisSlug(): String =
    trim().removePrefix("@").trim('/')
