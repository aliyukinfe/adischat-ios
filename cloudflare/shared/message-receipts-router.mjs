const now = () => Date.now();
const id = () => `receipt_${Date.now().toString(36)}_${crypto.randomUUID().replaceAll("-", "").slice(0, 12)}`;
const MAX_READ_RECEIPT_BATCH = 50;

async function upsertReceipt(env, { threadType, threadId, messageId, uid, deliveredAt = 0, readAt = 0 }) {
  const ts = now();
  await env.DB.prepare(
    `INSERT INTO message_receipts
      (id, threadType, threadId, messageId, uid, deliveredAt, readAt, updatedAt)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(threadType, threadId, messageId, uid) DO UPDATE SET
       deliveredAt = CASE WHEN message_receipts.deliveredAt > 0 THEN message_receipts.deliveredAt ELSE excluded.deliveredAt END,
       readAt = CASE WHEN message_receipts.readAt > 0 THEN message_receipts.readAt ELSE excluded.readAt END,
       updatedAt = CASE WHEN message_receipts.readAt > 0 THEN message_receipts.updatedAt ELSE excluded.updatedAt END`,
  ).bind(id(), threadType, threadId, messageId, uid, deliveredAt, readAt, ts).run();
}

async function getDirectThread(env, threadId, uid) {
  const conv = await env.DB.prepare(
    `SELECT participant1_id, participant2_id, user_id,
      COALESCE(p1_unread, 0) AS p1_unread,
      COALESCE(p2_unread, 0) AS p2_unread,
      COALESCE(p1_last_read_at, 0) AS p1_last_read_at,
      COALESCE(p2_last_read_at, 0) AS p2_last_read_at
     FROM conversations WHERE id=?`,
  ).bind(threadId).first();
  if (!conv || !(conv.participant1_id === uid || conv.participant2_id === uid || conv.user_id === uid)) return null;
  const isP1 = conv.participant1_id === uid;
  return {
    conv,
    isP1,
    unread: Number((isP1 ? conv.p1_unread : conv.p2_unread) || 0),
    lastReadAt: Number((isP1 ? conv.p1_last_read_at : conv.p2_last_read_at) || 0),
  };
}

async function clearThreadUnread(env, threadId, uid, directInfo, readAt) {
  if (!directInfo || directInfo.unread <= 0) return false;
  if (directInfo.isP1) {
    await env.DB.prepare("UPDATE conversations SET p1_unread=0, p1_last_read_at=?, unread_for_user=CASE WHEN user_id=? THEN 0 ELSE unread_for_user END WHERE id=?")
      .bind(readAt, uid, threadId).run();
  } else {
    await env.DB.prepare("UPDATE conversations SET p2_unread=0, p2_last_read_at=?, unread_for_user=CASE WHEN user_id=? THEN 0 ELSE unread_for_user END WHERE id=?")
      .bind(readAt, uid, threadId).run();
  }
  return true;
}

async function canReadThread(env, threadType, threadId, uid) {
  if (threadType === "community") {
    const room = await env.DB.prepare("SELECT owner_uid FROM community_rooms WHERE id=?").bind(threadId).first().catch(() => null);
    if (!room) return false;
    if (room.owner_uid === uid) return true;
    const member = await env.DB.prepare("SELECT 1 FROM community_members WHERE room_id=? AND uid=?").bind(threadId, uid).first().catch(() => null);
    return Boolean(member);
  }
  return Boolean(await getDirectThread(env, threadId, uid));
}

async function directUnreadMessages(env, threadId, uid, after) {
  const rows = await env.DB.prepare(
    `SELECT m.id, m.sender_id AS senderId
     FROM messages m
     WHERE m.conversation_id=?
       AND m.sender_id<>?
       AND COALESCE(m.timestamp, 0)>?
       AND NOT EXISTS (
         SELECT 1 FROM message_receipts r
         WHERE r.threadType='direct' AND r.threadId=? AND r.messageId=m.id AND r.uid=? AND COALESCE(r.readAt, 0)>0
       )
     ORDER BY m.timestamp ASC
     LIMIT ?`,
  ).bind(threadId, uid, after, threadId, uid, MAX_READ_RECEIPT_BATCH + 1).all();
  return rows.results || [];
}

async function communityReadCursor(env, threadId, uid) {
  const row = await env.DB.prepare(
    "SELECT COALESCE(MAX(readAt), 0) AS lastReadAt FROM message_receipts WHERE threadType='community' AND threadId=? AND uid=?",
  ).bind(threadId, uid).first().catch(() => null);
  return Number(row?.lastReadAt || 0);
}

async function communityUnreadMessages(env, threadId, uid, after) {
  const rows = await env.DB.prepare(
    `SELECT m.id, m.sender_uid AS senderId
     FROM community_messages m
     WHERE m.room_id=?
       AND m.sender_uid<>?
       AND COALESCE(m.created_at, 0)>?
       AND NOT EXISTS (
         SELECT 1 FROM message_receipts r
         WHERE r.threadType='community' AND r.threadId=? AND r.messageId=m.id AND r.uid=? AND COALESCE(r.readAt, 0)>0
       )
     ORDER BY m.created_at ASC
     LIMIT ?`,
  ).bind(threadId, uid, after, threadId, uid, MAX_READ_RECEIPT_BATCH + 1).all();
  return rows.results || [];
}

export async function handleMessageReceiptsRequest(request, env, user) {
  const url = new URL(request.url);
  const direct = url.pathname.match(/^\/api\/conversations\/([^/]+)\/read$/);
  const community = url.pathname.match(/^\/api\/communities\/([^/]+)\/read$/);
  if (request.method !== "PUT" || (!direct && !community)) return null;

  const threadType = community ? "community" : "direct";
  const threadId = decodeURIComponent((direct || community)[1]);
  if (!(await canReadThread(env, threadType, threadId, user.uid))) return Response.json({ error: "Thread not found" }, { status: 404 });

  const readAt = now();
  const directInfo = threadType === "direct" ? await getDirectThread(env, threadId, user.uid) : null;
  const after = threadType === "direct" ? Number(directInfo?.lastReadAt || 0) : await communityReadCursor(env, threadId, user.uid);
  const candidates = threadType === "direct"
    ? await directUnreadMessages(env, threadId, user.uid, after)
    : await communityUnreadMessages(env, threadId, user.uid, after);
  const messages = candidates.slice(0, MAX_READ_RECEIPT_BATCH);

  for (const message of messages) {
    await upsertReceipt(env, {
      threadType,
      threadId,
      messageId: message.id,
      uid: user.uid,
      deliveredAt: readAt,
      readAt,
    });
  }

  const clearedUnread = threadType === "direct" ? await clearThreadUnread(env, threadId, user.uid, directInfo, readAt) : false;
  if (candidates.length > MAX_READ_RECEIPT_BATCH) {
    console.warn("[receipts] read receipt batch capped", { threadType, threadId, uid: user.uid, count: candidates.length, max: MAX_READ_RECEIPT_BATCH });
  }
  console.info("[receipts] mark-read", { threadType, threadId, uid: user.uid, rowsWritten: messages.length, clearedUnread });

  return Response.json({ ok: true, readAt, count: messages.length, capped: candidates.length > MAX_READ_RECEIPT_BATCH });
}