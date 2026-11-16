package com.adischat.app.calls

import com.adischat.app.network.CallSession

fun CallSession.safeCallerName(fallback: String = ""): String =
    callerName.trim().ifBlank { fallback.trim() }.ifBlank { "Unknown caller" }

fun CallSession.safeCallerPhotoUrl(currentUid: String, currentUserPhotoUrl: String = ""): String {
    val caller = callerUid.trim()
    val receiver = receiverUid.trim()
    val photo = callerPhotoUrl.trim()
    val receiverPhoto = receiverPhotoUrl.trim()
    val currentPhoto = currentUserPhotoUrl.trim()

    if (caller.isBlank() || currentUid.isBlank()) return ""
    if (caller == currentUid) return ""
    if (receiver.isNotBlank() && receiver != currentUid) return ""
    if (photo.isBlank()) return ""

    // A caller without an avatar must never inherit the receiver/current user's
    // avatar. If the backend accidentally mirrors receiverPhotoUrl, force the UI
    // to use initials/default caller fallback instead.
    if (receiverPhoto.isNotBlank() && photo == receiverPhoto) return ""
    if (currentPhoto.isNotBlank() && photo == currentPhoto) return ""

    return photo
}
