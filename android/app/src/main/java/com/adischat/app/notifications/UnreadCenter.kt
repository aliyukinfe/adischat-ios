package com.adischat.app.notifications

import android.content.Context
import com.adischat.app.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Process-wide unread aggregator powering the notification badges across Home,
 * the Chat tab, and the Chats/Groups/Channels sections - WhatsApp/Telegram style.
 *
 * Direct (private) chats are server-backed: the API already tracks unread per
 * conversation and clears it on open. Groups & channels run on a separate backend
 * that has NO per-user read tracking and can't be changed here, so their unread is
 * computed on the client: we remember the last-read timestamp per room locally
 * (survives restarts) and count messages newer than it (excluding our own).
 *
 * Totals:  total = chatUnread + groupUnread + channelUnread
 */
object UnreadCenter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo = ChatRepository()
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile private var started = false
    private lateinit var prefs: android.content.SharedPreferences

    // Per-item unread maps (id -> count). Direct conversations come from the server;
    // group/channel rooms are computed locally.
    private val _perConversation = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _groupPerRoom     = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _channelPerRoom   = MutableStateFlow<Map<String, Int>>(emptyMap())
    val groupPerRoom:   StateFlow<Map<String, Int>> = _groupPerRoom.asStateFlow()
    val channelPerRoom: StateFlow<Map<String, Int>> = _channelPerRoom.asStateFlow()

    // Section + global totals.
    private val _chatUnread    = MutableStateFlow(0)
    private val _groupUnread   = MutableStateFlow(0)
    private val _channelUnread = MutableStateFlow(0)
    private val _total      = MutableStateFlow(0)
    val chatUnread:    StateFlow<Int> = _chatUnread.asStateFlow()
    val groupUnread:   StateFlow<Int> = _groupUnread.asStateFlow()
    val channelUnread: StateFlow<Int> = _channelUnread.asStateFlow()
    val total:      StateFlow<Int> = _total.asStateFlow()

    // Cache: roomId -> (lastMessageAt we counted for, unread count). Avoids re-fetching
    // a room's messages when nothing changed since the last poll.
    private val countCache = HashMap<String, Pair<Long, Int>>()

    private fun myUid(): String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private fun lastRead(roomId: String): Long = prefs.getLong("read_$roomId", 0L)
    private fun setLastRead(roomId: String, ts: Long) {
        prefs.edit().putLong("read_$roomId", ts).apply()
    }

    /** Start the background poller once. Safe to call repeatedly. */
    fun start(context: Context) {
        if (started) return
        started = true
        prefs = context.applicationContext.getSharedPreferences("adis_unread_reads", Context.MODE_PRIVATE)
        scope.launch {
        while (true) {
        runCatching { refreshOnce() }
        // Wake early if something asks for an immediate refresh (push / send / open).
        withTimeoutOrNull(120_000) { refreshSignal.first() }
        }
        }
    }

    /** Ask the poller to refresh as soon as possible (e.g. a push just arrived). */
    fun refreshSoon() { refreshSignal.tryEmit(Unit) }

    /** Clear process-wide unread state when the authenticated account changes. */
    fun resetForAccountSwitch() {
        countCache.clear()
        _perConversation.value = emptyMap()
        _groupPerRoom.value = emptyMap()
        _channelPerRoom.value = emptyMap()
        recomputeTotals()
    }

    /** Mark a direct conversation read locally (immediate badge feedback). */
    fun markConversationRead(conversationId: String) {
        if (conversationId.isBlank()) return
        _perConversation.value = _perConversation.value.toMutableMap().apply { put(conversationId, 0) }
        recomputeTotals()
        refreshSoon()
    }

    /** Mark a group/channel room read: persist the read mark and clear its count now. */
    fun markRoomRead(roomId: String) {
        if (roomId.isBlank() || !started) return
        setLastRead(roomId, System.currentTimeMillis())
        countCache[roomId] = (System.currentTimeMillis()) to 0
        _groupPerRoom.value   = _groupPerRoom.value.toMutableMap().apply { if (containsKey(roomId)) put(roomId, 0) }
        _channelPerRoom.value = _channelPerRoom.value.toMutableMap().apply { if (containsKey(roomId)) put(roomId, 0) }
        recomputeTotals()
        refreshSoon()
    }

    private suspend fun refreshOnce() {
        val uid = myUid()
        if (uid.isBlank()) return

        // - Direct (private) chats - server is the source of truth -
        runCatching { repo.getConversationsOnce() }.getOrNull()?.let { convos ->
        val map = HashMap<String, Int>(convos.size)
        convos.forEach { c -> if (c.unreadCount > 0) map[c.id] = c.unreadCount }
        // Don't count the chat that's open right now.
        ActiveChat.conversationId?.let { open -> map.remove(open) }
        _perConversation.value = map
        }

        // - Groups & channels - computed locally -
        val groups   = runCatching { repo.getCommunitiesOnce("group") }.getOrNull()
        val channels = runCatching { repo.getCommunitiesOnce("channel") }.getOrNull()
        if (groups != null)   _groupPerRoom.value   = computeRoomUnread(groups, uid)
        if (channels != null) _channelPerRoom.value = computeRoomUnread(channels, uid)

        recomputeTotals()
    }

    private suspend fun computeRoomUnread(
        rooms: List<com.adischat.app.data.model.CommunityRoom>, uid: String,
    ): Map<String, Int> {
        val result = HashMap<String, Int>()
        val activeRoom = ActiveChat.conversationId
        for (room in rooms) {
        if (!room.joined) continue
        val roomId = room.id
        val lastMsgAt = room.lastMessageAt

        // Currently open - always read.
        if (roomId == activeRoom) {
        setLastRead(roomId, System.currentTimeMillis())
        countCache[roomId] = lastMsgAt to 0
        result[roomId] = 0
        continue
        }

        val read = lastRead(roomId)
        // First time we ever see this room: baseline to its latest activity so we
        // don't flood the badge with historical messages. New messages will count.
        if (read == 0L) {
        setLastRead(roomId, if (lastMsgAt > 0L) lastMsgAt else System.currentTimeMillis())
        countCache[roomId] = lastMsgAt to 0
        result[roomId] = 0
        continue
        }

        // No new activity since last read - 0 without a network call.
        if (lastMsgAt <= read) {
        countCache[roomId] = lastMsgAt to 0
        result[roomId] = 0
        continue
        }

        // Cached for this exact lastMessageAt - reuse.
        val cached = countCache[roomId]
        if (cached != null && cached.first == lastMsgAt) {
        result[roomId] = cached.second
        continue
        }

        // Otherwise fetch and count messages newer than read, excluding our own.
        val msgs = runCatching { repo.getCommunityMessagesOnce(roomId) }.getOrNull()
        val count = msgs?.count { it.timestamp > read && it.senderId != uid && it.senderId.isNotBlank() } ?: 0
        countCache[roomId] = lastMsgAt to count
        result[roomId] = count
        }
        return result
    }

    private fun recomputeTotals() {
        val chat    = _perConversation.value.values.sum()
        val group   = _groupPerRoom.value.values.sum()
        val channel = _channelPerRoom.value.values.sum()
        _chatUnread.value    = chat
        _groupUnread.value   = group
        _channelUnread.value = channel
        _total.value      = chat + group + channel
    }
}
