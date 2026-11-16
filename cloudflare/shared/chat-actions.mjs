/*
 * AdisChat chat actions routes for Cloudflare Workers + D1.
 *
 * Install:
 *   import { handleChatActionsRequest } from './chat-actions-worker-routes.js';
 *   const handled = await handleChatActionsRequest(request, env, { uid });
 *   if (handled) return handled;
 *
 * Required bindings:
 *   env.DB - D1 database
 * Optional:
 *   env.AI - Cloudflare Workers AI, used by translate endpoint
 */

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const notFound = () => json({ error: "Not found" }, 404);

const now = () => Date.now();

const rid = (prefix) =>
  `${prefix}_${Date.now().toString(36)}_${crypto.randomUUID().replaceAll("-", "").slice(0, 12)}`;

async function bodyJson(request) {
  try {
    return await request.json();
  } catch {
    return {};
  }
}

function requireUid(ctx) {
  const uid = ctx?.uid || ctx?.user?.uid || ctx?.auth?.uid;
  if (!uid) throw new Error("AUTH_REQUIRED");
  return uid;
}

async function execFirst(db, statements) {
  let lastError;
  for (const { sql, params = [] } of statements) {
    try {
      return await db.prepare(sql).bind(...params).first();
    } catch (err) {
      lastError = err;
    }
  }
  throw lastError;
}

async function execRun(db, statements) {
  let lastError;
  for (const { sql, params = [] } of statements) {
    try {
      return await db.prepare(sql).bind(...params).run();
    } catch (err) {
      lastError = err;
    }
  }
  throw lastError;
}

async function execAll(db, statements) {
  let lastError;
  for (const { sql, params = [] } of statements) {
    try {
      return await db.prepare(sql).bind(...params).all();
    } catch (err) {
      lastError = err;
    }
  }
  throw lastError;
}

function directMessageSelect(threadId, messageId) {
  return [
    { sql: "SELECT * FROM messages WHERE conversation_id = ? AND id = ?", params: [threadId, messageId] },
    { sql: "SELECT * FROM conversation_messages WHERE conversation_id = ? AND id = ?", params: [threadId, messageId] },
    { sql: "SELECT * FROM direct_messages WHERE conversation_id = ? AND id = ?", params: [threadId, messageId] },
  ];
}

function communityMessageSelect(threadId, messageId) {
  return [
    { sql: "SELECT * FROM community_messages WHERE room_id = ? AND id = ?", params: [threadId, messageId] },
    { sql: "SELECT * FROM community_messages WHERE community_id = ? AND id = ?", params: [threadId, messageId] },
    { sql: "SELECT * FROM messages WHERE room_id = ? AND id = ?", params: [threadId, messageId] },
  ];
}

function messageUpdateText(threadType, threadId, messageId, text, editedAt) {
  if (threadType === "community") {
    return [
      { sql: "UPDATE community_messages SET text = ?, edited_at = ? WHERE room_id = ? AND id = ?", params: [text, editedAt, threadId, messageId] },
      { sql: "UPDATE community_messages SET text = ?, edited_at = ? WHERE community_id = ? AND id = ?", params: [text, editedAt, threadId, messageId] },
      { sql: "UPDATE messages SET text = ?, edited_at = ? WHERE room_id = ? AND id = ?", params: [text, editedAt, threadId, messageId] },
    ];
  }
  return [
    { sql: "UPDATE messages SET text = ?, edited_at = ? WHERE conversation_id = ? AND id = ?", params: [text, editedAt, threadId, messageId] },
    { sql: "UPDATE conversation_messages SET text = ?, edited_at = ? WHERE conversation_id = ? AND id = ?", params: [text, editedAt, threadId, messageId] },
    { sql: "UPDATE direct_messages SET text = ?, edited_at = ? WHERE conversation_id = ? AND id = ?", params: [text, editedAt, threadId, messageId] },
  ];
}

function messageSoftDelete(threadType, threadId, messageId, deletedAt) {
  if (threadType === "community") {
    return [
      { sql: "UPDATE community_messages SET deleted = 1, text = '', deleted_at = ? WHERE room_id = ? AND id = ?", params: [deletedAt, threadId, messageId] },
      { sql: "UPDATE community_messages SET deleted = 1, text = '', deleted_at = ? WHERE community_id = ? AND id = ?", params: [deletedAt, threadId, messageId] },
      { sql: "UPDATE messages SET deleted = 1, text = '', deleted_at = ? WHERE room_id = ? AND id = ?", params: [deletedAt, threadId, messageId] },
    ];
  }
  return [
    { sql: "UPDATE messages SET deleted = 1, text = '', deleted_at = ? WHERE conversation_id = ? AND id = ?", params: [deletedAt, threadId, messageId] },
    { sql: "UPDATE conversation_messages SET deleted = 1, text = '', deleted_at = ? WHERE conversation_id = ? AND id = ?", params: [deletedAt, threadId, messageId] },
    { sql: "UPDATE direct_messages SET deleted = 1, text = '', deleted_at = ? WHERE conversation_id = ? AND id = ?", params: [deletedAt, threadId, messageId] },
  ];
}

async function getMessage(db, threadType, threadId, messageId) {
  return execFirst(db, threadType === "community" ? communityMessageSelect(threadId, messageId) : directMessageSelect(threadId, messageId));
}

function messageOwner(message) {
  return message?.sender_id || message?.senderId || message?.uid || message?.author_uid || message?.authorUid || "";
}

async function hasThreadAccess(db, threadType, threadId, uid) {
  if (threadType === "community") {
    const room = await db.prepare("SELECT owner_uid FROM community_rooms WHERE id = ?").bind(threadId).first();
    if (room?.owner_uid === uid) return true;
    const member = await db.prepare("SELECT 1 AS ok FROM community_members WHERE room_id = ? AND uid = ?").bind(threadId, uid).first();
    return Boolean(member?.ok);
  }

  const conversation = await execFirst(db, [
    {
      sql: `SELECT id FROM conversations
        WHERE id = ?
        AND (participant1_id = ? OR participant2_id = ? OR user_id = ?)`,
      params: [threadId, uid, uid, uid],
    },
    {
      sql: `SELECT id FROM direct_conversations
        WHERE id = ?
        AND (participant1_id = ? OR participant2_id = ? OR user_id = ?)`,
      params: [threadId, uid, uid, uid],
    },
  ]).catch(() => null);
  return Boolean(conversation?.id);
}

async function upsertAction(db, uid, threadType, threadId, messageId, patch) {
  const t = now();
  await db.prepare(`
    INSERT INTO chat_message_actions(uid, thread_type, thread_id, message_id, hidden, starred, listened, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(uid, thread_type, thread_id, message_id) DO UPDATE SET
      hidden = COALESCE(excluded.hidden, hidden),
      starred = COALESCE(excluded.starred, starred),
      listened = COALESCE(excluded.listened, listened),
      updated_at = excluded.updated_at
  `).bind(
    uid,
    threadType,
    threadId,
    messageId,
    patch.hidden ?? null,
    patch.starred ?? null,
    patch.listened ?? null,
    t,
    t,
  ).run();
}

async function handleMessageAction(request, env, ctx, threadType, threadId, messageId, action) {
  const uid = requireUid(ctx);
  const body = await bodyJson(request);
  const db = env.DB;
  if (!(await hasThreadAccess(db, threadType, threadId, uid))) return notFound();
  const message = await getMessage(db, threadType, threadId, messageId);
  if (!message) return notFound();

  if (action === "edit" && request.method === "PATCH") {
    if (messageOwner(message) !== uid) return json({ error: "Only the sender can edit this message" }, 403);
    const text = String(body.text || "").trim();
    if (!text) return json({ error: "Message text is required" }, 400);
    const t = now();
    await db.prepare(`
      INSERT INTO chat_message_edits(id, uid, thread_type, thread_id, message_id, previous_text, new_text, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(rid("edit"), uid, threadType, threadId, messageId, message.text || "", text, t).run();
    await execRun(db, messageUpdateText(threadType, threadId, messageId, text, t));
    return json({ ok: true, edited: true, messageId, text, editedAt: t });
  }

  if (action === "delete" && request.method === "POST") {
    const mode = body.mode === "everyone" ? "everyone" : "me";
    if (mode === "everyone") {
      if (messageOwner(message) !== uid) return json({ error: "Only the sender can delete for everyone" }, 403);
      await execRun(db, messageSoftDelete(threadType, threadId, messageId, now()));
      return json({ ok: true, deletedForEveryone: true, messageId });
    }
    await upsertAction(db, uid, threadType, threadId, messageId, { hidden: 1 });
    return json({ ok: true, deletedForMe: true, messageId });
  }

  if (action === "pin" && request.method === "POST") {
    const pinned = body.pinned === false ? 0 : 1;
    const t = now();
    await db.prepare(`
      INSERT INTO chat_message_pins(uid, thread_type, thread_id, message_id, pinned, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(uid, thread_type, thread_id, message_id) DO UPDATE SET pinned = excluded.pinned, updated_at = excluded.updated_at
    `).bind(uid, threadType, threadId, messageId, pinned, t, t).run();
    return json({ ok: true, pinned: pinned === 1, messageId });
  }

  if (action === "star" && request.method === "POST") {
    const starred = body.starred === false ? 0 : 1;
    await upsertAction(db, uid, threadType, threadId, messageId, { starred });
    return json({ ok: true, starred: starred === 1, messageId });
  }

  if (action === "listen" && request.method === "POST") {
    await upsertAction(db, uid, threadType, threadId, messageId, { listened: 1 });
    return json({ ok: true, listened: true, messageId });
  }

  if (action === "reaction" && request.method === "POST") {
    const emoji = String(body.emoji || "").trim();
    if (!emoji) return json({ error: "emoji is required" }, 400);
    const existing = await db.prepare(`
      SELECT emoji FROM chat_message_reactions
      WHERE uid = ? AND thread_type = ? AND thread_id = ? AND message_id = ?
    `).bind(uid, threadType, threadId, messageId).first();
    if (existing?.emoji === emoji) {
      await db.prepare(`
        DELETE FROM chat_message_reactions
        WHERE uid = ? AND thread_type = ? AND thread_id = ? AND message_id = ?
      `).bind(uid, threadType, threadId, messageId).run();
      return json({ ok: true, reacted: false, messageId, emoji: "" });
    }
    const t = now();
    await db.prepare(`
      INSERT INTO chat_message_reactions(uid, thread_type, thread_id, message_id, emoji, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(uid, thread_type, thread_id, message_id) DO UPDATE SET emoji = excluded.emoji, updated_at = excluded.updated_at
    `).bind(uid, threadType, threadId, messageId, emoji, t, t).run();
    return json({ ok: true, reacted: true, messageId, emoji });
  }

  if (action === "translate" && request.method === "POST") {
    const targetLanguage = String(body.targetLanguage || "en");
    const sourceText = String(message.text || "");
    let translatedText = sourceText;
    if (env.AI && sourceText) {
      const aiResult = await env.AI.run("@cf/meta/m2m100-1.2b", {
        text: sourceText,
        source_lang: body.sourceLanguage || "auto",
        target_lang: targetLanguage,
      });
      translatedText = aiResult?.translated_text || aiResult?.translation || sourceText;
    }
    return json({ ok: true, messageId, sourceText, translatedText, targetLanguage });
  }

  return notFound();
}

async function handleThreadAction(request, env, ctx, threadType, threadId, action) {
  const uid = requireUid(ctx);
  const db = env.DB;
  const body = await bodyJson(request);
  if (!(await hasThreadAccess(db, threadType, threadId, uid))) return notFound();

  if (action === "settings") {
    if (request.method === "GET") {
      const row = await db.prepare("SELECT * FROM chat_thread_settings WHERE uid = ? AND thread_type = ? AND thread_id = ?")
        .bind(uid, threadType, threadId).first();
      return json(row || { muted: 0, wallpaper: "", disappearing_seconds: 0, screenshot_warning: 1 });
    }
    if (request.method === "PUT") {
      const t = now();
      await db.prepare(`
        INSERT INTO chat_thread_settings(uid, thread_type, thread_id, muted, wallpaper, disappearing_seconds, screenshot_warning, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(uid, thread_type, thread_id) DO UPDATE SET
        muted = excluded.muted,
        wallpaper = excluded.wallpaper,
        disappearing_seconds = excluded.disappearing_seconds,
        screenshot_warning = excluded.screenshot_warning,
        updated_at = excluded.updated_at
      `).bind(
        uid,
        threadType,
        threadId,
        body.muted ? 1 : 0,
        String(body.wallpaper || ""),
        Number(body.disappearingSeconds || 0),
        body.screenshotWarning === false ? 0 : 1,
        t,
      ).run();
      return json({ ok: true });
    }
  }

  if (action === "search" && request.method === "GET") {
    const q = new URL(request.url).searchParams.get("q") || "";
    if (!q.trim()) return json([]);
    const like = `%${q.trim()}%`;
    const rows = await execAll(db, threadType === "community" ? [
      { sql: "SELECT * FROM community_messages WHERE room_id = ? AND text LIKE ? ORDER BY timestamp DESC LIMIT 50", params: [threadId, like] },
      { sql: "SELECT * FROM community_messages WHERE community_id = ? AND text LIKE ? ORDER BY timestamp DESC LIMIT 50", params: [threadId, like] },
    ] : [
      { sql: "SELECT * FROM messages WHERE conversation_id = ? AND text LIKE ? ORDER BY timestamp DESC LIMIT 50", params: [threadId, like] },
      { sql: "SELECT * FROM conversation_messages WHERE conversation_id = ? AND text LIKE ? ORDER BY timestamp DESC LIMIT 50", params: [threadId, like] },
    ]);
    return json(rows.results || []);
  }

  if (action === "export" && request.method === "GET") {
    const rows = await execAll(db, threadType === "community" ? [
      { sql: "SELECT * FROM community_messages WHERE room_id = ? ORDER BY timestamp ASC LIMIT 5000", params: [threadId] },
      { sql: "SELECT * FROM community_messages WHERE community_id = ? ORDER BY timestamp ASC LIMIT 5000", params: [threadId] },
    ] : [
      { sql: "SELECT * FROM messages WHERE conversation_id = ? ORDER BY timestamp ASC LIMIT 5000", params: [threadId] },
      { sql: "SELECT * FROM conversation_messages WHERE conversation_id = ? ORDER BY timestamp ASC LIMIT 5000", params: [threadId] },
    ]);
    return json({ threadType, threadId, exportedAt: now(), messages: rows.results || [] });
  }

  if (action === "clear" && request.method === "POST") {
    const rows = await execAll(db, threadType === "community" ? [
      { sql: "SELECT id FROM community_messages WHERE room_id = ?", params: [threadId] },
      { sql: "SELECT id FROM community_messages WHERE community_id = ?", params: [threadId] },
    ] : [
      { sql: "SELECT id FROM messages WHERE conversation_id = ?", params: [threadId] },
      { sql: "SELECT id FROM conversation_messages WHERE conversation_id = ?", params: [threadId] },
    ]);
    const t = now();
    const batch = (rows.results || []).map((row) =>
      db.prepare(`
        INSERT INTO chat_message_actions(uid, thread_type, thread_id, message_id, hidden, created_at, updated_at)
        VALUES (?, ?, ?, ?, 1, ?, ?)
        ON CONFLICT(uid, thread_type, thread_id, message_id) DO UPDATE SET hidden = 1, updated_at = excluded.updated_at
      `).bind(uid, threadType, threadId, row.id, t, t),
    );
    if (batch.length) await db.batch(batch);
    return json({ ok: true, hiddenCount: batch.length });
  }

  if (action === "report" && request.method === "POST") {
    await db.prepare(`
      INSERT INTO chat_reports(id, reporter_uid, reported_uid, thread_type, thread_id, message_id, reason, details, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(rid("report"), uid, String(body.reportedUid || ""), threadType, threadId, String(body.messageId || ""), String(body.reason || "report"), String(body.details || ""), now()).run();
    return json({ ok: true, reported: true });
  }

  if (action === "block" && request.method === "POST") {
    const blockedUid = String(body.blockedUid || "");
    if (!blockedUid) return json({ error: "blockedUid is required" }, 400);
    await db.prepare(`
      INSERT INTO chat_blocks(blocker_uid, blocked_uid, reason, created_at)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(blocker_uid, blocked_uid) DO UPDATE SET reason = excluded.reason
    `).bind(uid, blockedUid, String(body.reason || ""), now()).run();
    return json({ ok: true, blocked: true, blockedUid });
  }

  return notFound();
}

export async function handleChatActionsRequest(request, env, ctx = {}) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "");
  let match = path.match(/^\/api\/conversations\/([^/]+)\/messages\/([^/]+)\/(edit|delete|pin|star|listen|translate|reaction)$/);
  if (match) return handleMessageAction(request, env, ctx, "direct", decodeURIComponent(match[1]), decodeURIComponent(match[2]), match[3]);

  match = path.match(/^\/api\/communities\/([^/]+)\/messages\/([^/]+)\/(edit|delete|pin|star|listen|translate|reaction)$/);
  if (match) return handleMessageAction(request, env, ctx, "community", decodeURIComponent(match[1]), decodeURIComponent(match[2]), match[3]);

  match = path.match(/^\/api\/conversations\/([^/]+)\/(settings|search|export|clear|report|block)$/);
  if (match) return handleThreadAction(request, env, ctx, "direct", decodeURIComponent(match[1]), match[2]);

  match = path.match(/^\/api\/communities\/([^/]+)\/(settings|search|export|clear|report|block)$/);
  if (match) return handleThreadAction(request, env, ctx, "community", decodeURIComponent(match[1]), match[2]);

  return null;
}
