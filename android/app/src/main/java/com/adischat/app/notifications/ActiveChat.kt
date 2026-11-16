package com.adischat.app.notifications

/**
 * Tracks which conversation (if any) is currently open on screen so the FCM
 * service can suppress a push for a chat the user is already reading. A
 * foreground `notification` message only reaches onMessageReceived while the
 * app is visible, so this is enough to avoid double-notifying the active chat.
 */
object ActiveChat {
    @Volatile
    var conversationId: String? = null

    fun clear() {
        conversationId = null
    }
}
