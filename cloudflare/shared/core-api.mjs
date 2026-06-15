import { sendPushToUser } from "./push.mjs";
const JSON_HEADERS = { "Content-Type": "application/json; charset=utf-8" };

const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
const now = () => Date.now();
const id = (prefix) => `${prefix}_${Date.now().toString(36)}_${crypto.randomUUID().replaceAll("-", "").slice(0, 12)}`;
const OTP_TTL_MS = 10 * 60 * 1000;
const OTP_RESEND_COOLDOWN_MS = 60 * 1000;
const OTP_MAX_ATTEMPTS = 5;

async function bodyJson(request) {
  try { return await request.json(); } catch { return {}; }
}

function boolInt(value) { return value ? 1 : 0; }
function asBool(value) { return value === true || value === 1 || value === "1"; }
function cleanText(value, max = 4000) { return String(value ?? "").slice(0, max); }
function cleanEmail(value) { return cleanText(value, 160).trim().toLowerCase(); }
function normalizeUsername(value, fallback = "") {
  const raw = String(value || fallback || "").trim().toLowerCase().replace(/^@/, "");
  const cleaned = raw.replace(/[^a-z0-9_]/g, "_").replace(/_+/g, "_").replace(/^_+|_+$/g, "").slice(0, 24);
  return cleaned.length >= 3 ? cleaned : `user_${Math.floor(1000 + Math.random() * 9000)}`;
}

function hex(buffer) {
  return [...new Uint8Array(buffer)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function sha256(value) {
  return hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
}

function generateOtp() {
  const bytes = new Uint32Array(1);
  crypto.getRandomValues(bytes);
  return String(100000 + (bytes[0] % 900000));
}

function getResendFrom(env) {
  const fromEmail = String(env.RESEND_FROM_EMAIL || env.EMAIL_FROM || env.FROM_EMAIL || "").trim();
  const fromName = env.RESEND_FROM_NAME || "AdisChat";
  if (!fromEmail) return "";
  if (fromEmail.includes("<") && fromEmail.includes(">")) return fromEmail;
  return `${fromName} <${fromEmail}>`;
}

async function sendEmailOtpViaResend(env, email, otp) {
  if (!env.RESEND_API_KEY) {
    return { sent: false, reason: "missing_resend_api_key" };
  }
  const from = getResendFrom(env);
  if (!from) {
    return { sent: false, reason: "missing_from_email" };
  }

  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from,
      to: [email],
      subject: "Your AdisChat verification code",
      html: `<div style="font-family:Arial,sans-serif;background:#070A12;color:#fff;padding:28px;border-radius:18px">
        <h1 style="margin:0 0 12px;font-size:24px">AdisChat verification</h1>
        <p style="color:#B6C2D1;font-size:15px">Use this code to verify your email address.</p>
        <div style="font-size:34px;font-weight:800;letter-spacing:6px;margin:24px 0;color:#5CFF7A">${otp}</div>
        <p style="color:#8FA0B4;font-size:13px">This code expires in 10 minutes. If you did not request it, you can ignore this email.</p>
      </div>`,
      text: `Your AdisChat verification code is ${otp}. It expires in 10 minutes.`,
    }),
  });

  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    console.warn("Resend OTP email failed", { status: response.status, detail: detail.slice(0, 500) });
    return { sent: false, reason: `resend_${response.status}`, detail: detail.slice(0, 500) };
  }
  return { sent: true };
}

function userJson(row = {}) {
  return {
    uid: row.uid || "",
    name: row.name || "",
    username: row.username || "",
    phone: row.phone || "",
    email: row.email || "",
    dob: row.dob || "",
    photoUrl: row.photo_url || row.photoUrl || "",
    role: row.role || "user",
    balance: Number(row.balance || 0),
    points: Number(row.points || 0),
    orders: Number(row.orders || 0),
    emailVerified: asBool(row.email_verified ?? row.emailVerified),
    verification_level: Number(row.verification_level || 0),
    verification_request_status: row.verification_request_status || null,
    verified: Number(row.verification_level || 0) >= 3,
    lastSeenVisibility: row.last_seen_visibility || row.lastSeenVisibility || "public",
    accountStatus: row.account_status || row.accountStatus || "active",
    deletedAt: Number(row.deleted_at || row.deletedAt || 0),
  };
}

async function getUser(env, uid) {
  return env.DB.prepare("SELECT * FROM users WHERE uid=?").bind(uid).first();
}

async function availableUsername(env, preferred, uid) {
  const base = normalizeUsername(preferred, `user_${uid.slice(0, 8)}`);
  const existing = await env.DB.prepare("SELECT uid FROM users WHERE lower(username)=lower(?) AND uid<>? LIMIT 1")
    .bind(base, uid).first();
  if (!existing) return base;
  const suffix = uid.replace(/[^a-zA-Z0-9]/g, "").slice(0, 6).toLowerCase() || Math.floor(100000 + Math.random() * 900000).toString();
  return normalizeUsername(`${base}_${suffix}`, `user_${suffix}`);
}

async function ensureUserProfile(env, user, body = {}) {
  const existing = await getUser(env, user.uid);
  if (existing) return existing;

  const email = cleanText(body.email || user.email, 160).trim().toLowerCase();
  const fallbackName = email ? email.split("@")[0] : "AdisChat user";
  const name = cleanText(body.name || user.name || fallbackName, 120).trim() || "AdisChat user";
  const username = await availableUsername(env, body.username || email.split("@")[0] || name, user.uid);
  const t = now();
  await env.DB.prepare(`INSERT INTO users(uid, name, username, phone, email, dob, photo_url, role, email_verified, created_at, updated_at, last_seen)
    VALUES (?, ?, ?, ?, ?, ?, ?, 'user', ?, ?, ?, ?)`)
    .bind(
      user.uid,
      name,
      username,
      cleanText(body.phone || user.phone, 64),
      email,
      cleanText(body.dob, 32),
      "",
      boolInt(body.emailVerified || user.emailVerified),
      t,
      t,
      t,
    ).run();
  return getUser(env, user.uid);
}

async function requireAdmin(env, user) {
  const row = await getUser(env, user.uid);
  if ((row?.role || user.role || "").toLowerCase() !== "admin") return null;
  return row;
}

function messageJson(row = {}) {
  return {
    id: row.id || "",
    senderId: row.sender_id || "",
    senderRole: row.sender_role || "user",
    senderName: row.sender_name || row.name || "",
    senderPhotoUrl: row.sender_photo_url || row.photo_url || "",
    sender_verification_level: Number(row.sender_verification_level || row.verification_level || 0),
    sender_verified: Number(row.sender_verification_level || row.verification_level || 0) >= 3,
    type: row.type || "text",
    text: row.text || "",
    mediaUrl: row.media_url || "",
    mediaMimeType: row.media_mime_type || "",
    durationMs: Number(row.duration_ms || 0),
    forwarded: asBool(row.forwarded),
    edited: Number(row.edited_at || 0) > 0,
    edited_at: Number(row.edited_at || 0),
    deleted_at: Number(row.deleted_at || 0),
    callType: row.call_type || "",
    callStatus: row.call_status || "",
    callDuration: Number(row.call_duration || 0),
    deliveredAt: Number(row.delivered_at || 0),
    readAt: Number(row.read_at || 0),
    deliveryStatus: row.read_at ? "seen" : row.delivered_at ? "delivered" : "sent",
    reactions: row.reactions || {},
    deleted: asBool(row.deleted),
    timestamp: Number(row.timestamp || row.created_at || 0),
  };
}

async function sendMessagePush(env, conversation, sender, message) {
  const receiverUid = conversation.participant1_id === sender.uid ? conversation.participant2_id : conversation.participant1_id;
  if (!receiverUid || receiverUid === sender.uid) return;
  const senderRow = await getUser(env, sender.uid);
  const senderName = senderRow?.name || sender.name || "AdisChat";
  const body = message.text || (message.type === "image" ? "Photo" : message.type === "video" ? "Video" : message.type === "voice" ? "Voice message" : message.type || "New message");
  await sendPushToUser(env, receiverUid, {
    type: "message",
    conversationId: message.conversationId,
    messageId: message.id,
    senderId: sender.uid,
    senderName,
    title: senderName,
    body,
  });
}

function conversationJson(row, uid) {
  const isP1 = row.participant1_id === uid;
  const otherId = row.type === "direct" ? (isP1 ? row.participant2_id : row.participant1_id) : (row.user_id || "support");
  const unread = row.type === "direct" ? (isP1 ? row.p1_unread : row.p2_unread) : (row.user_id === uid ? row.unread_for_user : row.unread_for_admin);
  return {
    id: row.id,
    type: row.type || "support",
    otherUserId: otherId || "support",
    otherUserName: row.other_name || row.user_name || (row.type === "support" ? "AdisChat Support" : "AdisChat user"),
    otherUserPhotoUrl: row.other_photo_url || row.user_photo_url || "",
    lastMessage: row.last_message || "",
    lastSenderRole: row.last_sender_role || "user",
    lastMessageAt: Number(row.last_message_at || row.updated_at || 0),
    unreadCount: Number(unread || 0),
    other_user_verification_level: Number(row.other_verification_level || 0),
    other_user_verified: Number(row.other_verification_level || 0) >= 3,
  };
}

async function getConversationForUser(env, conversationId, uid) {
  const row = await env.DB.prepare("SELECT * FROM conversations WHERE id=?").bind(conversationId).first();
  if (!row) return null;
  if (row.participant1_id === uid || row.participant2_id === uid || row.user_id === uid) return row;
  return null;
}

async function handlePublicAuth(request, env) {
  const url = new URL(request.url);
  if (request.method === "POST" && url.pathname === "/api/auth/lookup") {
    const body = await bodyJson(request);
    const identifier = cleanText(body.identifier || body.email || body.phone || body.username, 128).trim();
    const username = identifier.replace(/^@/, "").trim();
    const email = identifier.toLowerCase();
    const digits = identifier.replace(/\D/g, "");
    const phoneVariants = new Set([identifier]);
    if (digits) {
      phoneVariants.add(digits);
      if (digits.startsWith("0")) phoneVariants.add(`+251${digits.slice(1)}`);
      if (digits.startsWith("251")) phoneVariants.add(`+${digits}`);
      if (digits.length === 9) phoneVariants.add(`+251${digits}`);
    }
    const phones = [...phoneVariants].slice(0, 6);
    while (phones.length < 6) phones.push("");
    const rows = await env.DB.prepare(`SELECT uid, email, username FROM users
      WHERE lower(username)=lower(?) OR lower(email)=lower(?)
        OR phone IN (?, ?, ?, ?, ?, ?)
      ORDER BY CASE
        WHEN lower(username)=lower(?) THEN 0
        WHEN lower(email)=lower(?) THEN 1
        ELSE 2
      END, updated_at DESC
      LIMIT 5`)
      .bind(username, email, ...phones, username, email).all();
    const matches = rows.results || [];
    if (!matches.length) return json({ error: "Account not found" }, 404);

    const exactUsername = matches.filter((row) => (row.username || "").toLowerCase() === username.toLowerCase());
    const exactEmail = matches.filter((row) => (row.email || "").toLowerCase() === email);
    const candidates = exactUsername.length ? exactUsername : exactEmail.length ? exactEmail : matches;
    const uniqueUids = new Set(candidates.map((row) => row.uid).filter(Boolean));
    if (uniqueUids.size > 1) return json({ error: "Account lookup is ambiguous. Please sign in with email." }, 409);
    const row = candidates[0];
    if (!row?.email || !row?.uid) return json({ error: "Account not found" }, 404);
    return json({ uid: row.uid, email: row.email, username: row.username || "" });
  }
  if (request.method === "POST" && url.pathname === "/api/auth/send-email-otp") {
    const body = await bodyJson(request);
    const email = cleanEmail(body.email);
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return json({ error: "Enter a valid email address" }, 400);

    const previous = await env.DB.prepare(
      "SELECT created_at FROM email_verification_otps WHERE email=? AND used_at=0 AND expires_at>? ORDER BY created_at DESC LIMIT 1"
    ).bind(email, now()).first();
    if (previous?.created_at && now() - Number(previous.created_at) < OTP_RESEND_COOLDOWN_MS) {
      const retryAfter = Math.ceil((OTP_RESEND_COOLDOWN_MS - (now() - Number(previous.created_at))) / 1000);
      return json({ error: "Please wait before requesting another code", retryAfterSeconds: retryAfter }, 429);
    }

    const otp = generateOtp();
    const salt = crypto.randomUUID();
    const otpId = id("email_otp");
    const createdAt = now();
    const expiresAt = createdAt + OTP_TTL_MS;
    const codeHash = await sha256(`${email}:${otp}:${salt}`);
    const delivery = await sendEmailOtpViaResend(env, email, otp);

    if (!delivery.sent && env.RESEND_API_KEY) {
      return json({ error: "Could not send verification email", reason: delivery.reason }, 502);
    }

    await env.DB.prepare(`INSERT INTO email_verification_otps(id, email, code_hash, salt, created_at, expires_at, used_at, attempts, resend_status)
      VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?)`)
      .bind(otpId, email, codeHash, salt, createdAt, expiresAt, delivery.sent ? "sent" : delivery.reason).run();

    // Local/dev fallback only: production Resend sends the code by email and devOtp stays null.
    return json({ ok: true, devOtp: delivery.sent ? null : otp, expiresAt });
  }
  if (request.method === "POST" && url.pathname === "/api/auth/verify-email-otp") {
    const body = await bodyJson(request);
    const email = cleanEmail(body.email);
    const otp = cleanText(body.otp || body.code, 12).replace(/\D/g, "");
    if (!email || otp.length !== 6) return json({ verified: false, error: "Invalid verification code" }, 400);

    const row = await env.DB.prepare(
      "SELECT * FROM email_verification_otps WHERE email=? AND used_at=0 AND expires_at>? ORDER BY created_at DESC LIMIT 1"
    ).bind(email, now()).first();
    if (!row) return json({ verified: false, error: "Verification code expired. Please request a new one." }, 400);
    if (Number(row.attempts || 0) >= OTP_MAX_ATTEMPTS) return json({ verified: false, error: "Too many attempts. Please request a new code." }, 429);

    const candidateHash = await sha256(`${email}:${otp}:${row.salt}`);
    if (candidateHash !== row.code_hash) {
      await env.DB.prepare("UPDATE email_verification_otps SET attempts=attempts+1 WHERE id=?").bind(row.id).run();
      return json({ verified: false, error: "Incorrect verification code" }, 400);
    }

    await env.DB.prepare("UPDATE email_verification_otps SET used_at=? WHERE id=?").bind(now(), row.id).run();
    return json({ verified: true });
  }
  return null;
}

async function handleUsers(request, env, user) {
  const url = new URL(request.url);
  if (request.method === "GET" && url.pathname === "/api/users/me") {
    const row = await ensureUserProfile(env, user);
    if (row.account_status === "deleted" || Number(row.deleted_at || 0) > 0) {
      return json({ error: "Account deleted" }, 410);
    }
    return json(userJson(row));
  }
  if (request.method === "POST" && url.pathname === "/api/users/sync") {
    const body = await bodyJson(request);
    const existing = await ensureUserProfile(env, user, body);
    if (existing.account_status === "deleted" || Number(existing.deleted_at || 0) > 0) {
      return json({ error: "Account deleted" }, 410);
    }
    const name = cleanText(body.name || user.name || existing?.name || "AdisChat user", 120);
    const username = normalizeUsername(body.username || existing?.username, name);
    await env.DB.prepare(`INSERT INTO users(uid, name, username, phone, email, dob, photo_url, role, email_verified, created_at, updated_at, last_seen)
      VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'user'), ?, ?, ?, ?)
      ON CONFLICT(uid) DO UPDATE SET
        name=excluded.name,
        username=COALESCE(NULLIF(users.username, ''), excluded.username),
        phone=COALESCE(NULLIF(excluded.phone, ''), users.phone),
        email=COALESCE(NULLIF(excluded.email, ''), users.email),
        dob=COALESCE(NULLIF(excluded.dob, ''), users.dob),
        photo_url=users.photo_url,
        email_verified=MAX(users.email_verified, excluded.email_verified),
        updated_at=excluded.updated_at,
        last_seen=excluded.last_seen,
        account_status=COALESCE(NULLIF(users.account_status, ''), 'active')`)
      .bind(user.uid, name, username, cleanText(body.phone, 64), cleanText(body.email || user.email, 160), cleanText(body.dob, 32), "", existing?.role || body.role || "user", boolInt(body.emailVerified), now(), now(), now()).run();
    return json(userJson(await getUser(env, user.uid)));
  }
  if (request.method === "PUT" && url.pathname === "/api/users/privacy") {
    const body = await bodyJson(request);
    const visibility = ["public", "private"].includes(body.lastSeenVisibility) ? body.lastSeenVisibility : "public";
    await env.DB.prepare("UPDATE users SET last_seen_visibility=?, updated_at=? WHERE uid=?")
      .bind(visibility, now(), user.uid).run();
    return json(userJson(await getUser(env, user.uid)));
  }
  if (request.method === "DELETE" && url.pathname === "/api/users/me") {
    const t = now();
    await env.DB.prepare(
      `UPDATE users SET
        name='Deleted Account',
        username='deleted_' || substr(uid, 1, 8),
        phone='',
        email='',
        dob='',
        photo_url='',
        last_seen_visibility='private',
        account_status='deleted',
        deleted_at=?,
        updated_at=?
       WHERE uid=?`
    ).bind(t, t, user.uid).run();
    await env.DB.prepare("DELETE FROM push_tokens WHERE uid=?").bind(user.uid).run().catch(() => null);
    await env.DB.prepare("UPDATE linked_devices SET revoked_at=? WHERE user_id=? AND revoked_at=0").bind(t, user.uid).run().catch(() => null);
    return json({ deleted: true, deletedAt: t });
  }
  const updateMatch = url.pathname.match(/^\/api\/users\/([^/]+)$/);
  if (request.method === "PUT" && updateMatch) {
    const targetUid = decodeURIComponent(updateMatch[1]);
    if (targetUid !== user.uid && (user.role || "") !== "admin") return json({ error: "Forbidden" }, 403);
    const body = await bodyJson(request);
    await env.DB.prepare("UPDATE users SET name=?, username=?, phone=?, email=?, dob=?, updated_at=? WHERE uid=?")
      .bind(cleanText(body.name, 120), normalizeUsername(body.username), cleanText(body.phone, 64), cleanText(body.email, 160), cleanText(body.dob, 32), now(), targetUid).run();
    return json(userJson(await getUser(env, targetUid)));
  }
  if (request.method === "GET" && url.pathname === "/api/users/search") {
    const q = cleanText(url.searchParams.get("q"), 80).trim();
    if (!q) return json([]);
    const like = `%${q.replace(/^@/, "")}%`;
    const rows = await env.DB.prepare(`SELECT uid, name, username, phone, photo_url, role, verification_level FROM users
      WHERE uid<>? AND COALESCE(account_status,'active')<>'deleted'
        AND (lower(name) LIKE lower(?) OR lower(username) LIKE lower(?) OR phone LIKE ? OR lower(email) LIKE lower(?))
      ORDER BY name COLLATE NOCASE LIMIT 50`).bind(user.uid, like, like, like, like).all();
    return json((rows.results || []).map((row) => ({
      uid: row.uid, name: row.name || "", username: row.username || "", phone: row.phone || "", photoUrl: row.photo_url || "", role: row.role || "user", verification_level: Number(row.verification_level || 0), verified: Number(row.verification_level || 0) >= 3,
    })));
  }
  if (request.method === "POST" && url.pathname === "/api/users/heartbeat") {
    await env.DB.prepare("UPDATE users SET last_seen=?, updated_at=? WHERE uid=?").bind(now(), now(), user.uid).run();
    return json({ ok: true, lastSeen: now() });
  }
  const presenceMatch = url.pathname.match(/^\/api\/users\/([^/]+)\/presence$/);
  if (request.method === "GET" && presenceMatch) {
    const row = await getUser(env, decodeURIComponent(presenceMatch[1]));
    if (!row || row.account_status === "deleted" || Number(row.deleted_at || 0) > 0) {
      return json({ uid: "", online: false, lastSeen: 0 });
    }
    if (row.uid !== user.uid && (row.last_seen_visibility || "public") === "private") {
      return json({ uid: row.uid || "", online: false, lastSeen: 0, hidden: true });
    }
    const lastSeen = Number(row?.last_seen || 0);
    return json({ uid: row?.uid || "", online: lastSeen > now() - 90_000, lastSeen });
  }
  return null;
}

async function handleConversations(request, env, user) {
  const url = new URL(request.url);
  if (request.method === "GET" && url.pathname === "/api/conversations") {
    const rows = await env.DB.prepare(`SELECT c.*, u.name AS other_name, u.photo_url AS other_photo_url, u.verification_level AS other_verification_level
      FROM conversations c
      LEFT JOIN users u ON u.uid = CASE WHEN c.participant1_id = ? THEN c.participant2_id ELSE c.participant1_id END
      WHERE c.participant1_id=? OR c.participant2_id=? OR c.user_id=?
      ORDER BY c.last_message_at DESC, c.updated_at DESC`).bind(user.uid, user.uid, user.uid, user.uid).all();
    return json((rows.results || []).map((row) => conversationJson(row, user.uid)));
  }
  const startMatch = url.pathname.match(/^\/api\/conversations\/start\/([^/]+)$/);
  if (request.method === "POST" && startMatch) {
    const targetUid = decodeURIComponent(startMatch[1]);
    if (!targetUid || targetUid === user.uid) return json({ error: "Invalid user" }, 400);
    const target = await getUser(env, targetUid);
    if (!target) return json({ error: "User not found" }, 404);
    const existing = await env.DB.prepare(`SELECT * FROM conversations WHERE type='direct' AND ((participant1_id=? AND participant2_id=?) OR (participant1_id=? AND participant2_id=?)) LIMIT 1`)
      .bind(user.uid, targetUid, targetUid, user.uid).first();
    const convId = existing?.id || id("conv");
    if (!existing) {
      await env.DB.prepare(`INSERT INTO conversations(id, type, user_id, user_name, user_photo_url, participant1_id, participant2_id, created_at, updated_at)
        VALUES (?, 'direct', ?, ?, ?, ?, ?, ?, ?)`).bind(convId, targetUid, target.name || "", target.photo_url || "", user.uid, targetUid, now(), now()).run();
    }
    return json({ conversationId: convId, conversation: conversationJson({ ...(existing || {}), id: convId, type: "direct", participant1_id: user.uid, participant2_id: targetUid, other_name: target.name, other_photo_url: target.photo_url, other_verification_level: target.verification_level }, user.uid) });
  }
  const messagesMatch = url.pathname.match(/^\/api\/conversations\/([^/]+)\/messages$/);
  if (messagesMatch) {
    const conversationId = decodeURIComponent(messagesMatch[1]);
    const conversation = await getConversationForUser(env, conversationId, user.uid);
    if (!conversation) return json({ error: "Conversation not found" }, 404);
    if (request.method === "GET") {
      const rows = await env.DB.prepare(`SELECT m.*, u.name AS sender_name, u.photo_url AS sender_photo_url, u.verification_level AS sender_verification_level,
        COALESCE(r.deliveredAt, 0) AS delivered_at, COALESCE(r.readAt, 0) AS read_at
        FROM messages m
        LEFT JOIN users u ON u.uid=m.sender_id
        LEFT JOIN message_receipts r ON r.threadType='direct' AND r.threadId=m.conversation_id AND r.messageId=m.id AND r.uid<>m.sender_id
        WHERE m.conversation_id=?
        ORDER BY m.timestamp ASC LIMIT 1000`).bind(conversationId).all();
      return json((rows.results || []).map(messageJson));
    }
    if (request.method === "POST") {
      const body = await bodyJson(request);
      const t = now();
      const msgId = id("msg");
      const text = cleanText(body.text, 4000);
      const type = cleanText(body.type || "text", 32);
      await env.DB.prepare(`INSERT INTO messages(id, conversation_id, sender_id, sender_role, text, timestamp, created_at, type, media_url, media_mime_type, duration_ms, forwarded, call_type, call_status, call_duration)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)
        .bind(msgId, conversationId, user.uid, user.role || "user", text, t, t, type, cleanText(body.mediaUrl, 1000), cleanText(body.mediaMimeType, 120), Number(body.durationMs || 0), boolInt(body.forwarded), cleanText(body.callType, 32), cleanText(body.callStatus, 32), Number(body.callDuration || 0)).run();
      const p1Unread = conversation.participant1_id === user.uid ? 0 : Number(conversation.p1_unread || 0) + 1;
      const p2Unread = conversation.participant2_id === user.uid ? 0 : Number(conversation.p2_unread || 0) + 1;
      await env.DB.prepare("UPDATE conversations SET last_message=?, last_sender_role=?, last_message_at=?, updated_at=?, p1_unread=?, p2_unread=? WHERE id=?")
        .bind(text || type, user.role || "user", t, t, p1Unread, p2Unread, conversationId).run();
      const confirmed = {
        id: msgId, conversation_id: conversationId, sender_id: user.uid, sender_role: user.role || "user",
        sender_name: user.name, sender_photo_url: user.photoUrl, text, timestamp: t, type,
        media_url: body.mediaUrl || "", media_mime_type: body.mediaMimeType || "",
        duration_ms: body.durationMs || 0, forwarded: body.forwarded ? 1 : 0,
      };
      await sendMessagePush(env, conversation, user, { id: msgId, conversationId, text, type }).catch(() => null);
      return json(messageJson(confirmed));
    }
  }
  return null;
}

function verificationRequestJson(row = {}) {
  return {
    status: row.status || "none",
    submittedAt: Number(row.submitted_at || 0),
    reviewedAt: row.reviewed_at ? Number(row.reviewed_at) : null,
    rejectionReason: row.rejection_reason || null,
    documentType: row.document_type || "",
  };
}

function adminVerificationJson(row = {}) {
  return {
    uid: row.uid || "",
    fullName: row.full_name || "",
    documentType: row.document_type || "",
    documentUrl: row.document_url || "",
    selfieUrl: row.selfie_url || "",
    status: row.status || "pending",
    submittedAt: Number(row.submitted_at || 0),
    reviewedAt: row.reviewed_at ? Number(row.reviewed_at) : null,
    rejectionReason: row.rejection_reason || null,
    userName: row.user_name || "",
    userEmail: row.user_email || "",
    userPhone: row.user_phone || "",
    userPhoto: row.user_photo || "",
  };
}

async function handleVerification(request, env, user) {
  const url = new URL(request.url);
  if (request.method === "GET" && url.pathname === "/api/verification/status") {
    const profile = await ensureUserProfile(env, user);
    const requestRow = await env.DB.prepare(
      "SELECT * FROM verification_requests WHERE uid=? ORDER BY submitted_at DESC LIMIT 1",
    ).bind(user.uid).first();
    return json({
      verification_level: Number(profile?.verification_level || 0),
      verification_request_status: profile?.verification_request_status || requestRow?.status || null,
      request: requestRow ? verificationRequestJson(requestRow) : null,
    });
  }

  if (request.method === "POST" && url.pathname === "/api/verification/submit") {
    const body = await bodyJson(request);
    const fullName = cleanText(body.fullName || body.full_name || "", 160).trim();
    const documentType = cleanText(body.documentType || body.document_type || "", 80).trim();
    const documentUrl = cleanText(body.documentUrl || body.document_url || "", 1000).trim();
    const selfieUrl = cleanText(body.selfieUrl || body.selfie_url || "", 1000).trim();
    if (!fullName || !documentType || !documentUrl || !selfieUrl) {
      return json({ error: "Complete verification details are required" }, 400);
    }
    const t = now();
    await ensureUserProfile(env, user, { name: fullName });
    await env.DB.prepare(
      `INSERT INTO verification_requests(uid, full_name, document_type, document_url, selfie_url, status, submitted_at, reviewed_at, reviewer_uid, rejection_reason)
       VALUES (?, ?, ?, ?, ?, 'pending', ?, NULL, NULL, NULL)`,
    ).bind(user.uid, fullName, documentType, documentUrl, selfieUrl, t).run();
    await env.DB.prepare("UPDATE users SET verification_request_status='pending', updated_at=? WHERE uid=?")
      .bind(t, user.uid).run();
    return json({ submitted: true, status: "pending" });
  }

  return null;
}

async function handleAdmin(request, env, user) {
  const url = new URL(request.url);
  if (!url.pathname.startsWith("/api/admin")) return null;
  const admin = await requireAdmin(env, user);
  if (!admin) return json({ error: "Admin only" }, 403);
  if (request.method === "GET" && url.pathname === "/api/admin/stats") {
    const row = await env.DB.prepare(`SELECT
      (SELECT COUNT(*) FROM users) AS totalUsers,
      (SELECT COUNT(*) FROM users WHERE COALESCE(verification_level,0)=0) AS unverified,
      (SELECT COUNT(*) FROM users WHERE COALESCE(email_verified,0)=1) AS emailVerified,
      (SELECT COUNT(*) FROM users WHERE COALESCE(verification_level,0)>=2) AS profileComplete,
      (SELECT COUNT(*) FROM users WHERE COALESCE(verification_level,0)>=3) AS blueBadge,
      (SELECT COUNT(*) FROM verification_requests WHERE status='pending') AS pendingVerifications,
      (SELECT COUNT(*) FROM verification_requests WHERE status='approved') AS approvedVerifications,
      (SELECT COUNT(*) FROM verification_requests WHERE status='rejected') AS rejectedVerifications`).first();
    return json(row || {});
  }
  if (request.method === "GET" && url.pathname === "/api/admin/users") {
    const q = cleanText(url.searchParams.get("q"), 80).trim();
    const page = Math.max(0, Number(url.searchParams.get("page") || 0));
    const like = `%${q}%`;
    const rows = await env.DB.prepare(`SELECT * FROM users WHERE ?='' OR lower(name) LIKE lower(?) OR lower(username) LIKE lower(?) OR lower(email) LIKE lower(?) OR phone LIKE ? ORDER BY created_at DESC LIMIT 50 OFFSET ?`)
      .bind(q, like, like, like, like, page * 50).all();
    return json((rows.results || []).map(userJson));
  }
  const roleMatch = url.pathname.match(/^\/api\/admin\/users\/([^/]+)\/role$/);
  if (request.method === "PUT" && roleMatch) {
    const body = await bodyJson(request);
    const role = ["user", "merchant", "admin"].includes(body.role) ? body.role : "user";
    await env.DB.prepare("UPDATE users SET role=?, updated_at=? WHERE uid=?").bind(role, now(), decodeURIComponent(roleMatch[1])).run();
    return json({ ok: true, role });
  }
  const statusMatch = url.pathname.match(/^\/api\/admin\/users\/([^/]+)\/status$/);
  if (request.method === "PUT" && statusMatch) {
    const body = await bodyJson(request);
    const targetUid = decodeURIComponent(statusMatch[1]);
    const status = ["active", "paused", "deleted"].includes(body.status) ? body.status : "active";
    const t = now();
    await env.DB.prepare("UPDATE users SET account_status=?, deleted_at=?, updated_at=? WHERE uid=?")
      .bind(status, status === "deleted" ? t : 0, t, targetUid).run();
    if (status !== "active") {
      await env.DB.prepare("DELETE FROM sessions WHERE uid=? OR user_id=?").bind(targetUid, targetUid).run().catch(() => null);
      await env.DB.prepare("DELETE FROM linked_devices WHERE user_id=? OR uid=?").bind(targetUid, targetUid).run().catch(() => null);
      await env.DB.prepare("DELETE FROM push_tokens WHERE uid=?").bind(targetUid).run().catch(() => null);
    }
    return json({ ok: true, status });
  }
  const verificationMatch = url.pathname.match(/^\/api\/admin\/users\/([^/]+)\/verification$/);
  if (request.method === "PUT" && verificationMatch) {
    const body = await bodyJson(request);
    const targetUid = decodeURIComponent(verificationMatch[1]);
    const level = Math.max(0, Math.min(3, Number(body.verificationLevel ?? body.verification_level ?? (body.verified ? 3 : 0))));
    const requestStatus = level >= 3 ? "approved" : "none";
    const t = now();
    await env.DB.prepare("UPDATE users SET verification_level=?, verification_request_status=?, updated_at=? WHERE uid=?")
      .bind(level, requestStatus, t, targetUid).run();
    if (level <= 0) {
      await env.DB.prepare("UPDATE verification_requests SET status='rejected', reviewed_at=?, reviewer_uid=?, rejection_reason='Blue badge removed by admin' WHERE uid=? AND status='approved'")
        .bind(t, user.uid, targetUid).run().catch(() => null);
    }
    return json({ ok: true, verified: level >= 3, verificationLevel: level });
  }
  const deleteMatch = url.pathname.match(/^\/api\/admin\/users\/([^/]+)$/);
  if (request.method === "DELETE" && deleteMatch) {
    const targetUid = decodeURIComponent(deleteMatch[1]);
    if (targetUid === user.uid) return json({ error: "You cannot delete your own admin account" }, 400);
    const t = now();
    await env.DB.prepare("UPDATE users SET account_status='deleted', deleted_at=?, updated_at=? WHERE uid=?")
      .bind(t, t, targetUid).run();
    await env.DB.prepare("DELETE FROM sessions WHERE uid=? OR user_id=?").bind(targetUid, targetUid).run().catch(() => null);
    await env.DB.prepare("DELETE FROM linked_devices WHERE user_id=? OR uid=?").bind(targetUid, targetUid).run().catch(() => null);
    await env.DB.prepare("DELETE FROM push_tokens WHERE uid=?").bind(targetUid).run().catch(() => null);
    return json({ ok: true, deleted: true });
  }
  if (request.method === "GET" && url.pathname === "/api/admin/verifications") {
    const status = cleanText(url.searchParams.get("status") || "pending", 40);
    const rows = await env.DB.prepare(`SELECT vr.*, u.name AS user_name, u.email AS user_email, u.phone AS user_phone, u.photo_url AS user_photo
      FROM verification_requests vr
      LEFT JOIN users u ON u.uid = vr.uid
      WHERE ? = 'all' OR vr.status = ?
      ORDER BY vr.submitted_at DESC LIMIT 100`).bind(status, status).all();
    return json((rows.results || []).map(adminVerificationJson));
  }
  const approveMatch = url.pathname.match(/^\/api\/admin\/verifications\/([^/]+)\/approve$/);
  if (request.method === "POST" && approveMatch) {
    const targetUid = decodeURIComponent(approveMatch[1]);
    const t = now();
    await env.DB.prepare("UPDATE verification_requests SET status='approved', reviewed_at=?, reviewer_uid=?, rejection_reason=NULL WHERE uid=?")
      .bind(t, user.uid, targetUid).run();
    await env.DB.prepare("UPDATE users SET verification_level=3, verification_request_status='approved', updated_at=? WHERE uid=?")
      .bind(t, targetUid).run();
    return json({ approved: true });
  }
  const rejectMatch = url.pathname.match(/^\/api\/admin\/verifications\/([^/]+)\/reject$/);
  if (request.method === "POST" && rejectMatch) {
    const targetUid = decodeURIComponent(rejectMatch[1]);
    const body = await bodyJson(request);
    const t = now();
    await env.DB.prepare("UPDATE verification_requests SET status='rejected', reviewed_at=?, reviewer_uid=?, rejection_reason=? WHERE uid=?")
      .bind(t, user.uid, cleanText(body.reason || body.rejectionReason || "Rejected", 500), targetUid).run();
    await env.DB.prepare("UPDATE users SET verification_request_status='rejected', updated_at=? WHERE uid=?")
      .bind(t, targetUid).run();
    return json({ rejected: true });
  }
  return null;
}

async function handleNotifications(request, env, user) {
  const url = new URL(request.url);
  if (request.method !== "POST" || !url.pathname.startsWith("/api/notifications/")) return null;
  const body = await bodyJson(request);
  const token = cleanText(body.token || body.fcmToken, 512);
  const deviceId = cleanText(body.deviceId || body.device_id || "", 128);
  const platform = cleanText(body.platform || "android", 32);
  if (url.pathname.endsWith("/unregister-token") && !token) {
    if (deviceId) {
      await env.DB.prepare("DELETE FROM push_tokens WHERE uid=? AND device_id=?").bind(user.uid, deviceId).run();
    } else {
      await env.DB.prepare("DELETE FROM push_tokens WHERE uid=?").bind(user.uid).run();
    }
    return json({ ok: true });
  }
  if (!token) return json({ ok: true });
  if (url.pathname.endsWith("/register-token")) {
    // A single device install must belong to exactly one authenticated account.
    // Move the token/device away from previous users before saving this session.
    await env.DB.prepare("DELETE FROM push_tokens WHERE token=? AND uid<>?").bind(token, user.uid).run();
    if (deviceId) {
      await env.DB.prepare("DELETE FROM push_tokens WHERE device_id=? AND uid<>?").bind(deviceId, user.uid).run();
    }
    await env.DB.prepare("INSERT OR REPLACE INTO push_tokens(token, uid, platform, device_id, updated_at) VALUES (?, ?, ?, ?, ?)")
      .bind(token, user.uid, platform, deviceId, now()).run();
    return json({ ok: true });
  }
  if (url.pathname.endsWith("/unregister-token")) {
    if (deviceId) {
      await env.DB.prepare("DELETE FROM push_tokens WHERE token=? AND uid=? AND device_id=?").bind(token, user.uid, deviceId).run();
    } else {
      await env.DB.prepare("DELETE FROM push_tokens WHERE token=? AND uid=?").bind(token, user.uid).run();
    }
    return json({ ok: true });
  }
  return null;
}

export async function handlePublicCoreRequest(request, env) {
  return handlePublicAuth(request, env);
}

export async function handleCoreApiRequest(request, env, user) {
  if (!env.DB) return json({ error: "D1 database is not configured" }, 500);
  return (await handleUsers(request, env, user))
    || (await handleVerification(request, env, user))
    || (await handleConversations(request, env, user))
    || (await handleAdmin(request, env, user))
    || (await handleNotifications(request, env, user))
    || null;
}
