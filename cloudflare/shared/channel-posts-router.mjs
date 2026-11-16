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

function parseReactions(value) {
  try {
    return typeof value === "string" ? JSON.parse(value || "{}") : value || {};
  } catch {
    return {};
  }
}

function postJson(row) {
  const createdAt = Number(row.created_at || 0);
  const type = row.type || "text";
  return {
    id: row.id || "",
    channelId: row.room_id || "",
    authorUid: row.sender_uid || "",
    authorName: row.sender_name || "",
    authorPhotoUrl: row.sender_photo_url || "",
    title: "",
    body: row.deleted_at ? "" : row.text || "",
    postType: type,
    mediaUrl: row.deleted_at ? "" : row.media_url || "",
    mediaMimeType: row.media_mime_type || "",
    linkUrl: "",
    linkTitle: "",
    linkImage: "",
    pinned: false,
    views: 0,
    reactions: parseReactions(row.reactions),
    createdAt,
    updatedAt: createdAt,
    deleted: Boolean(row.deleted_at),
  };
}

async function currentUserProfile(env, user) {
  const uid = uidFrom(user);
  const row =
    (await first(env.DB, "SELECT uid, name, username, email, role, photo_url FROM users WHERE uid = ?", [uid]).catch(() => null)) ||
    (await first(env.DB, "SELECT id AS uid, username, email, role, avatar_url AS photo_url FROM users WHERE id = ?", [uid]).catch(() => null));
  return {
    uid,
    name: clean(row?.name || user.name || user.displayName || row?.username || user.email || "AdisChat", 120),
    photoUrl: clean(row?.photo_url || "", 1000),
    role: clean(row?.role || user.role || "user", 40),
  };
}

async function getChannel(db, channelId) {
  return first(db, "SELECT * FROM community_rooms WHERE id = ? AND type = 'channel'", [channelId]);
}

async function isChannelAdmin(env, uid, channel) {
  if (!channel) return false;
  if (channel.owner_uid === uid) return true;
  const user =
    (await first(env.DB, "SELECT role FROM users WHERE uid = ?", [uid]).catch(() => null)) ||
    (await first(env.DB, "SELECT role FROM users WHERE id = ?", [uid]).catch(() => null));
  return ["admin", "super_admin", "owner"].includes(String(user?.role || "").toLowerCase());
}

function previewFor(type, text) {
  if (text) return text.slice(0, 240);
  if (type === "image") return "Image";
  if (type === "video") return "Video";
  if (type === "voice") return "Voice message";
  if (type === "document" || type === "file") return "File";
  return "Attachment";
}

function bodyText(body) {
  const title = clean(body.title, 240);
  const text = clean(body.body ?? body.text ?? "", 8000);
  if (title && text) return `${title}\n\n${text}`.slice(0, 8000);
  return text || title;
}

async function listPosts(request, env, channelId) {
  const channel = await getChannel(env.DB, channelId);
  if (!channel) return json({ error: "Not found" }, 404);

  const url = new URL(request.url);
  const limit = Math.min(Math.max(Number(url.searchParams.get("limit") || 20), 1), 100);
  const before = Number(url.searchParams.get("before") || 0);
  const after = Number(url.searchParams.get("after") || 0);
  const params = [channelId];
  let where = "room_id = ? AND deleted_at IS NULL";
  if (after > 0) {
    where += " AND created_at > ?";
    params.push(after);
  } else if (before > 0) {
    where += " AND created_at < ?";
    params.push(before);
  }
  params.push(limit + 1);

  const order = after > 0 ? "ASC" : "DESC";
  const rows = await all(
    env.DB,
    `SELECT * FROM community_messages WHERE ${where} ORDER BY created_at ${order} LIMIT ?`,
    params,
  );
  const page = rows.slice(0, limit);
  return json({
    posts: page.map(postJson),
    hasMore: rows.length > limit,
    nextCursor: rows.length > limit && page.length ? String(page[page.length - 1].created_at || "") : null,
  });
}

async function createPost(request, env, user, channelId) {
  const uid = uidFrom(user);
  const channel = await getChannel(env.DB, channelId);
  if (!channel) return json({ error: "Not found" }, 404);
  if (!(await isChannelAdmin(env, uid, channel))) return json({ error: "Forbidden" }, 403);

  const body = await bodyJson(request);
  const profile = await currentUserProfile(env, user);
  const type = clean(body.postType || body.type || "text", 30) || "text";
  const text = bodyText(body);
  const mediaUrl = clean(body.mediaUrl || body.media_url, 2000);
  const mediaMimeType = clean(body.mediaMimeType || body.media_mime_type, 200);
  if (!text && !mediaUrl && type === "text") return json({ error: "Post is required" }, 400);

  const t = now();
  const postId = rid("cp");
  await run(
    env.DB,
    `INSERT INTO community_messages(id, room_id, sender_uid, sender_name, sender_photo_url, type, text, media_url, media_mime_type, duration_ms, created_at, reactions, deleted_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, '{}', NULL)`,
    [postId, channelId, uid, profile.name, profile.photoUrl, type, text, mediaUrl, mediaMimeType, t],
  );
  await run(
    env.DB,
    "UPDATE community_rooms SET last_post_preview = ?, last_post_author = ?, last_post_at = ?, updated_at = ? WHERE id = ?",
    [previewFor(type, text), profile.name, t, t, channelId],
  );
  const row = await first(env.DB, "SELECT * FROM community_messages WHERE id = ?", [postId]);
  return json(postJson(row), 201);
}

async function editPost(request, env, user, channelId, postId) {
  const uid = uidFrom(user);
  const channel = await getChannel(env.DB, channelId);
  if (!channel) return json({ error: "Not found" }, 404);
  if (!(await isChannelAdmin(env, uid, channel))) return json({ error: "Forbidden" }, 403);
  const post = await first(env.DB, "SELECT * FROM community_messages WHERE room_id = ? AND id = ?", [channelId, postId]);
  if (!post) return json({ error: "Not found" }, 404);

  const body = await bodyJson(request);
  const text = bodyText(body) || post.text || "";
  const mediaUrl = body.mediaUrl === undefined && body.media_url === undefined ? post.media_url : clean(body.mediaUrl || body.media_url, 2000);
  const mediaMimeType =
    body.mediaMimeType === undefined && body.media_mime_type === undefined
      ? post.media_mime_type
      : clean(body.mediaMimeType || body.media_mime_type, 200);
  await run(env.DB, "UPDATE community_messages SET text = ?, media_url = ?, media_mime_type = ? WHERE room_id = ? AND id = ?", [
    text,
    mediaUrl,
    mediaMimeType,
    channelId,
    postId,
  ]);
  const updated = await first(env.DB, "SELECT * FROM community_messages WHERE room_id = ? AND id = ?", [channelId, postId]);
  return json(postJson(updated));
}

async function deletePost(env, user, channelId, postId) {
  const uid = uidFrom(user);
  const channel = await getChannel(env.DB, channelId);
  if (!channel) return json({ error: "Not found" }, 404);
  const post = await first(env.DB, "SELECT * FROM community_messages WHERE room_id = ? AND id = ?", [channelId, postId]);
  if (!post) return json({ error: "Not found" }, 404);
  if (post.sender_uid !== uid && !(await isChannelAdmin(env, uid, channel))) return json({ error: "Forbidden" }, 403);
  await run(env.DB, "UPDATE community_messages SET deleted_at = ?, text = '', media_url = '' WHERE room_id = ? AND id = ?", [
    now(),
    channelId,
    postId,
  ]);
  return json({ ok: true });
}

async function reactToPost(request, env, user, channelId, postId) {
  const uid = uidFrom(user);
  const body = await bodyJson(request);
  const emoji = clean(body.emoji, 16);
  const post = await first(env.DB, "SELECT reactions FROM community_messages WHERE room_id = ? AND id = ?", [channelId, postId]);
  if (!post) return json({ error: "Not found" }, 404);
  const reactions = parseReactions(post.reactions);
  if (emoji) reactions[uid] = emoji;
  else delete reactions[uid];
  await run(env.DB, "UPDATE community_messages SET reactions = ? WHERE room_id = ? AND id = ?", [
    JSON.stringify(reactions),
    channelId,
    postId,
  ]);
  return json({ ok: true });
}

async function noOpPostAction(env, channelId, postId) {
  const post = await first(env.DB, "SELECT id FROM community_messages WHERE room_id = ? AND id = ? AND deleted_at IS NULL", [channelId, postId]);
  if (!post) return json({ error: "Not found" }, 404);
  return json({ ok: true });
}

export async function handleChannelPostsRequest(request, env, user) {
  const path = new URL(request.url).pathname;

  let match = path.match(/^\/api\/channels\/([^/]+)\/posts$/);
  if (match && request.method === "GET") return listPosts(request, env, decodeURIComponent(match[1]));
  if (match && request.method === "POST") return createPost(request, env, user, decodeURIComponent(match[1]));

  match = path.match(/^\/api\/channels\/([^/]+)\/posts\/([^/]+)$/);
  if (match && request.method === "PATCH") return editPost(request, env, user, decodeURIComponent(match[1]), decodeURIComponent(match[2]));
  if (match && request.method === "DELETE") return deletePost(env, user, decodeURIComponent(match[1]), decodeURIComponent(match[2]));

  match = path.match(/^\/api\/channels\/([^/]+)\/posts\/([^/]+)\/react$/);
  if (match && request.method === "POST") return reactToPost(request, env, user, decodeURIComponent(match[1]), decodeURIComponent(match[2]));

  match = path.match(/^\/api\/channels\/([^/]+)\/posts\/([^/]+)\/(?:pin|view)$/);
  if (match && request.method === "POST") return noOpPostAction(env, decodeURIComponent(match[1]), decodeURIComponent(match[2]));

  return null;
}
