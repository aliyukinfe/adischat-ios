const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });

const now = () => Date.now();
const rid = (prefix) => `${prefix}_${crypto.randomUUID()}`;

async function bodyJson(request) {
  try {
    return await request.json();
  } catch {
    return {};
  }
}

function clean(value, max = 4000) {
  return String(value ?? "").trim().slice(0, max);
}

function asBool(value) {
  return value === true || value === 1 || value === "1";
}

function uidFrom(user) {
  const uid = user?.uid || user?.id || "";
  if (!uid) throw new Error("AUTH_REQUIRED");
  return uid;
}

async function first(db, sql, params = []) {
  return db.prepare(sql).bind(...params).first();
}

async function run(db, sql, params = []) {
  return db.prepare(sql).bind(...params).run();
}

async function all(db, sql, params = []) {
  const result = await db.prepare(sql).bind(...params).all();
  return result?.results || [];
}

async function firstAvailable(db, statements) {
  for (const statement of statements) {
    try {
      const row = await db.prepare(statement.sql).bind(...(statement.params || [])).first();
      if (row) return row;
    } catch {
      // Some production databases still have older table/column names.
    }
  }
  return null;
}

async function runAvailable(db, statements) {
  let ran = false;
  for (const statement of statements) {
    try {
      await db.prepare(statement.sql).bind(...(statement.params || [])).run();
      ran = true;
    } catch {
      // Ignore missing legacy tables while migrations roll forward.
    }
  }
  return ran;
}

function roomJson(row, uid) {
  const lastPostAt = Number(row.last_post_at || row.lastPostAt || row.updated_at || row.created_at || 0);
  const preview = row.last_post_preview || row.lastPostPreview || "";
  const memberCount = Number(row.member_count || row.memberCount || 0);
  return {
    id: row.id || "",
    type: row.type || "channel",
    name: row.name || "",
    description: row.description || "",
    ownerUid: row.owner_uid || row.ownerUid || "",
    photoUrl: row.photo_url || row.photoUrl || "",
    memberCount,
    joined: asBool(row.joined) || row.owner_uid === uid || row.ownerUid === uid,
    lastMessage: preview,
    lastMessageAt: lastPostAt,
    lastPostAt,
    lastPostPreview: preview,
    lastPostAuthor: row.last_post_author || row.lastPostAuthor || "",
    unreadCount: 0,
    verified: asBool(row.verified || row.is_verified),
    createdAt: Number(row.created_at || row.createdAt || 0),
  };
}

function messageJson(row) {
  const timestamp = Number(row.created_at || row.timestamp || 0);
  const reactionsRaw = row.reactions || "{}";
  let reactions = {};
  try {
    reactions = typeof reactionsRaw === "string" ? JSON.parse(reactionsRaw || "{}") : reactionsRaw || {};
  } catch {
    reactions = {};
  }
  return {
    id: row.id || "",
    senderId: row.sender_uid || row.sender_id || row.senderId || "",
    senderRole: row.sender_role || row.senderRole || "user",
    senderName: row.sender_name || row.senderName || "",
    senderPhotoUrl: row.sender_photo_url || row.senderPhotoUrl || "",
    type: row.type || "text",
    text: row.deleted_at ? "" : row.text || "",
    mediaUrl: row.media_url || row.mediaUrl || "",
    mediaMimeType: row.media_mime_type || row.mediaMimeType || "",
    durationMs: Number(row.duration_ms || row.durationMs || 0),
    reactions,
    deleted: Boolean(row.deleted_at),
    deletedAt: Number(row.deleted_at || 0),
    timestamp,
    deliveredAt: timestamp,
    readAt: 0,
    deliveryStatus: "sent",
    localStatus: "sent",
    localProgress: 100,
  };
}

async function currentUserProfile(env, user) {
  const uid = uidFrom(user);
  const row = await first(env.DB, "SELECT uid, name, username, email, role, photo_url FROM users WHERE uid = ?", [uid])
    .catch(() => null)
    || await first(env.DB, "SELECT id AS uid, username, email, role, avatar_url AS photo_url FROM users WHERE id = ?", [uid]).catch(() => null);
  return {
    uid,
    name: clean(row?.name || user.name || user.displayName || row?.username || user.email || "AdisChat", 120),
    photoUrl: clean(row?.photo_url || "", 1000),
    role: clean(row?.role || user.role || "user", 40),
  };
}

async function getRoom(db, roomId) {
  const id = clean(roomId, 160);
  return first(db, "SELECT *, id AS resolved_id FROM community_rooms WHERE id = ?", [id]);
}

async function isRoomAdmin(env, uid, room) {
  if (!room) return false;
  if (room.owner_uid === uid) return true;
  const user = await first(env.DB, "SELECT role FROM users WHERE id = ?", [uid]).catch(() => null);
  return ["admin", "super_admin", "owner"].includes(String(user?.role || "").toLowerCase());
}

async function ensureMembership(db, roomId, uid, role = "member") {
  await run(
    db,
    "INSERT OR IGNORE INTO community_members(room_id, uid, role, joined_at) VALUES (?, ?, ?, ?)",
    [roomId, uid, role, now()],
  );
}

async function listRooms(request, env, user, typeOverride = null) {
  const uid = uidFrom(user);
  const url = new URL(request.url);
  const type = clean(typeOverride || url.searchParams.get("type") || "channel", 20).toLowerCase() === "group" ? "group" : "channel";
  const query = clean(url.searchParams.get("q") || "", 80).toLowerCase();
  const like = `%${query}%`;
  const rows = await all(
    env.DB,
    `SELECT r.*,
        CASE WHEN r.owner_uid = ? OR m.uid IS NOT NULL THEN 1 ELSE 0 END AS joined,
        (SELECT COUNT(*) FROM community_members cm WHERE cm.room_id = r.id) AS member_count
       FROM community_rooms r
       LEFT JOIN community_members m ON m.room_id = r.id AND m.uid = ?
      WHERE r.type = ?
        AND (? = '' OR lower(r.name) LIKE ? OR lower(r.description) LIKE ?)
      ORDER BY COALESCE(NULLIF(r.last_post_at, 0), r.updated_at, r.created_at) DESC`,
    [uid, uid, type, query, like, like],
  );
  return json(rows.map((row) => roomJson(row, uid)));
}

async function getRoomResponse(env, user, roomId, expectedType) {
  const uid = uidFrom(user);
  const room = await first(
    env.DB,
    `SELECT r.*,
        CASE WHEN r.owner_uid = ? OR m.uid IS NOT NULL THEN 1 ELSE 0 END AS joined,
        (SELECT COUNT(*) FROM community_members cm WHERE cm.room_id = r.id) AS member_count
       FROM community_rooms r
       LEFT JOIN community_members m ON m.room_id = r.id AND m.uid = ?
      WHERE r.id = ? AND r.type = ?
      LIMIT 1`,
    [uid, uid, roomId, expectedType],
  );
  if (!room) return json({ error: expectedType === "group" ? "Group not found" : "Channel not found" }, 404);
  return json(roomJson(room, uid));
}

async function createRoom(request, env, user) {
  const uid = uidFrom(user);
  const body = await bodyJson(request);
  const type = clean(body.type || "channel", 20).toLowerCase() === "group" ? "group" : "channel";
  const name = clean(body.name, 120);
  if (!name) return json({ error: "Name is required" }, 400);
  const roomId = rid(type);
  const t = now();
  await run(
    env.DB,
    `INSERT INTO community_rooms(id, type, name, description, owner_uid, photo_url, created_at, updated_at, last_post_preview, last_post_author, last_post_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, '', '', 0)`,
    [roomId, type, name, clean(body.description, 500), uid, clean(body.photoUrl || body.photo_url, 1000), t, t],
  );
  await ensureMembership(env.DB, roomId, uid, "owner");
  const room = await getRoom(env.DB, roomId);
  return json(roomJson({ ...room, joined: 1, member_count: 1 }, uid), 201);
}

async function updateRoom(request, env, user, roomId) {
  const uid = uidFrom(user);
  const room = await getRoom(env.DB, roomId);
  if (!room) return json({ error: "Not found" }, 404);
  if (!(await isRoomAdmin(env, uid, room))) return json({ error: "Forbidden" }, 403);
  const body = await bodyJson(request);
  const name = clean(body.name || room.name, 120);
  if (!name) return json({ error: "Name is required" }, 400);
  await run(
    env.DB,
    "UPDATE community_rooms SET name = ?, description = ?, photo_url = ?, updated_at = ? WHERE id = ?",
    [name, clean(body.description ?? room.description, 500), clean(body.photoUrl ?? body.photo_url ?? room.photo_url, 1000), now(), roomId],
  );
  const updated = await getRoom(env.DB, roomId);
  const member = await first(env.DB, "SELECT 1 AS joined FROM community_members WHERE room_id = ? AND uid = ?", [roomId, uid]);
  const count = await first(env.DB, "SELECT COUNT(*) AS member_count FROM community_members WHERE room_id = ?", [roomId]);
  return json(roomJson({ ...updated, joined: member ? 1 : 0, member_count: count?.member_count || 0 }, uid));
}

async function deleteRoom(env, user, roomId) {
  const uid = uidFrom(user);
  const room = await getRoom(env.DB, roomId);
  if (!room) return json({ error: "Not found" }, 404);
  if (!(await isRoomAdmin(env, uid, room))) return json({ error: "Forbidden" }, 403);
  await run(env.DB, "DELETE FROM community_messages WHERE room_id = ?", [roomId]);
  await run(env.DB, "DELETE FROM community_members WHERE room_id = ?", [roomId]);
  await run(env.DB, "DELETE FROM community_rooms WHERE id = ?", [roomId]);
  return json({ ok: true });
}

async function joinRoom(env, user, roomId) {
  const uid = uidFrom(user);
  const room = await getRoom(env.DB, roomId);
  if (!room) return json({ error: "Not found" }, 404);
  await ensureMembership(env.DB, roomId, uid);
  const count = await first(env.DB, "SELECT COUNT(*) AS member_count FROM community_members WHERE room_id = ?", [roomId]);
  return json(roomJson({ ...room, joined: 1, member_count: count?.member_count || 0 }, uid));
}

async function leaveRoom(env, user, roomId, expectedType = null) {
  const uid = uidFrom(user);
  const room = await getRoom(env.DB, roomId);
  if (!room || (expectedType && room.type !== expectedType)) {
    return json({ error: expectedType === "group" ? "Group not found" : expectedType === "channel" ? "Channel not found" : "Not found" }, 404);
  }
  if (room.owner_uid === uid || room.ownerUid === uid) {
    return json({ error: room.type === "channel" ? "Channel owners cannot unfollow their own channel" : "Group owners cannot leave their own group" }, 400);
  }
  const resolvedId = room.resolved_id || room.id || roomId;
  await run(env.DB, "DELETE FROM community_members WHERE room_id = ? AND uid = ?", [resolvedId, uid]);
  await run(env.DB, "DELETE FROM community_reads WHERE room_id = ? AND uid = ?", [resolvedId, uid]).catch(() => null);
  const count = await first(env.DB, "SELECT COUNT(*) AS member_count FROM community_members WHERE room_id = ?", [resolvedId]).catch(() => ({ member_count: 0 }));
  return json(roomJson({ ...room, joined: 0, member_count: count?.member_count || 0 }, uid));
}

async function hasMembership(env, roomId, uid, room) {
  if (room?.owner_uid === uid || room?.ownerUid === uid) return true;
  const member = await first(env.DB, "SELECT 1 AS member FROM community_members WHERE room_id = ? AND uid = ?", [roomId, uid]);
  return Boolean(member);
}

async function listMessages(request, env, roomId) {
  const url = new URL(request.url);
  const after = Number(url.searchParams.get("after") || 0);
  const where = after > 0 ? "room_id = ? AND created_at > ?" : "room_id = ?";
  const params = after > 0 ? [roomId, after] : [roomId];
  const rows = await all(
    env.DB,
    `SELECT * FROM community_messages WHERE ${where} ORDER BY created_at ASC LIMIT 500`,
    params,
  );
  return json(rows.map(messageJson));
}

async function sendMessage(request, env, user, roomId) {
  const uid = uidFrom(user);
  const room = await getRoom(env.DB, roomId);
  if (!room) return json({ error: "Not found" }, 404);
  if (!(await hasMembership(env, roomId, uid, room)) && !(await isRoomAdmin(env, uid, room))) {
    return json({ error: room.type === "channel" ? "Follow this channel before posting" : "Join this group before sending messages" }, 403);
  }

  const body = await bodyJson(request);
  const profile = await currentUserProfile(env, user);
  const type = clean(body.type || "text", 30) || "text";
  const text = clean(body.text, 8000);
  const mediaUrl = clean(body.mediaUrl || body.media_url, 2000);
  if (!text && !mediaUrl && type === "text") return json({ error: "Message is required" }, 400);

  const t = now();
  const messageId = rid("cm");
  await run(
    env.DB,
    `INSERT INTO community_messages(id, room_id, sender_uid, sender_name, sender_photo_url, type, text, media_url, media_mime_type, duration_ms, created_at, reactions, deleted_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '{}', NULL)`,
    [
      messageId,
      roomId,
      uid,
      profile.name,
      profile.photoUrl,
      type,
      text,
      mediaUrl,
      clean(body.mediaMimeType || body.media_mime_type, 200),
      Number(body.durationMs || body.duration_ms || 0),
      t,
    ],
  );

  const preview = text || (type === "image" ? "Image" : type === "video" ? "Video" : type === "voice" ? "Voice message" : "Attachment");
  await run(
    env.DB,
    "UPDATE community_rooms SET last_post_preview = ?, last_post_author = ?, last_post_at = ?, updated_at = ? WHERE id = ?",
    [preview.slice(0, 240), profile.name, t, t, roomId],
  );

  const message = await first(env.DB, "SELECT * FROM community_messages WHERE id = ?", [messageId]);
  return json(messageJson(message), 201);
}

async function deleteMessage(env, user, roomId, messageId) {
  const uid = uidFrom(user);
  const room = await getRoom(env.DB, roomId);
  if (!room) return json({ error: "Not found" }, 404);
  const message = await first(env.DB, "SELECT * FROM community_messages WHERE room_id = ? AND id = ?", [roomId, messageId]);
  if (!message) return json({ error: "Not found" }, 404);
  if (message.sender_uid !== uid && !(await isRoomAdmin(env, uid, room))) return json({ error: "Forbidden" }, 403);
  await run(env.DB, "UPDATE community_messages SET deleted_at = ?, text = '', media_url = '' WHERE room_id = ? AND id = ?", [now(), roomId, messageId]);
  return json({ ok: true });
}

async function reactToMessage(request, env, user, roomId, messageId) {
  const uid = uidFrom(user);
  const body = await bodyJson(request);
  const emoji = clean(body.emoji, 16);
  const message = await first(env.DB, "SELECT reactions FROM community_messages WHERE room_id = ? AND id = ?", [roomId, messageId]);
  if (!message) return json({ error: "Not found" }, 404);
  let reactions = {};
  try {
    reactions = JSON.parse(message.reactions || "{}");
  } catch {
    reactions = {};
  }
  if (emoji) reactions[uid] = emoji;
  else delete reactions[uid];
  await run(env.DB, "UPDATE community_messages SET reactions = ? WHERE room_id = ? AND id = ?", [JSON.stringify(reactions), roomId, messageId]);
  return json({ ok: true });
}

export async function handleCommunitiesRequest(request, env, user) {
  const url = new URL(request.url);
  const path = url.pathname;
  if (path === "/api/communities" && request.method === "GET") return listRooms(request, env, user);
  if (path === "/api/communities" && request.method === "POST") return createRoom(request, env, user);

  if ((path === "/api/groups" || path === "/groups") && request.method === "GET") {
    return listRooms(request, env, user, "group");
  }
  if ((path === "/api/channels" || path === "/channels") && request.method === "GET") {
    return listRooms(request, env, user, "channel");
  }

  let directMatch = path.match(/^\/api\/groups\/([^/]+)$/) || path.match(/^\/groups\/([^/]+)$/);
  if (directMatch && request.method === "GET") return getRoomResponse(env, user, decodeURIComponent(directMatch[1]), "group");

  directMatch = path.match(/^\/api\/groups\/([^/]+)\/leave$/) || path.match(/^\/groups\/([^/]+)\/leave$/);
  if (directMatch && ["POST", "DELETE"].includes(request.method)) return leaveRoom(env, user, decodeURIComponent(directMatch[1]), "group");

  directMatch = path.match(/^\/api\/channels\/([^/]+)$/) || path.match(/^\/channels\/([^/]+)$/);
  if (directMatch && request.method === "GET") return getRoomResponse(env, user, decodeURIComponent(directMatch[1]), "channel");

  directMatch = path.match(/^\/api\/channels\/([^/]+)\/unfollow$/) || path.match(/^\/channels\/([^/]+)\/unfollow$/);
  if (directMatch && ["POST", "DELETE"].includes(request.method)) return leaveRoom(env, user, decodeURIComponent(directMatch[1]), "channel");

  let match = path.match(/^\/api\/communities\/([^/]+)$/);
  if (match && request.method === "PUT") return updateRoom(request, env, user, decodeURIComponent(match[1]));
  if (match && request.method === "DELETE") return deleteRoom(env, user, decodeURIComponent(match[1]));

  match = path.match(/^\/api\/communities\/([^/]+)\/join$/);
  if (match && request.method === "POST") return joinRoom(env, user, decodeURIComponent(match[1]));
  if (match && request.method === "DELETE") return leaveRoom(env, user, decodeURIComponent(match[1]));

  match = path.match(/^\/api\/communities\/([^/]+)\/(?:leave|unfollow)$/);
  if (match && (request.method === "POST" || request.method === "DELETE")) return leaveRoom(env, user, decodeURIComponent(match[1]));

  match = path.match(/^\/api\/communities\/([^/]+)\/messages$/);
  if (match && request.method === "GET") return listMessages(request, env, decodeURIComponent(match[1]));
  if (match && request.method === "POST") return sendMessage(request, env, user, decodeURIComponent(match[1]));

  match = path.match(/^\/api\/communities\/([^/]+)\/messages\/([^/]+)$/);
  if (match && request.method === "DELETE") return deleteMessage(env, user, decodeURIComponent(match[1]), decodeURIComponent(match[2]));

  match = path.match(/^\/api\/communities\/([^/]+)\/messages\/([^/]+)\/reaction$/);
  if (match && request.method === "POST") return reactToMessage(request, env, user, decodeURIComponent(match[1]), decodeURIComponent(match[2]));

  return null;
}
