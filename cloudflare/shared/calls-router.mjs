import { sendPushToUser } from "./push.mjs";
const now = () => Date.now();
const DELIVERY_ACK_TIMEOUT_MS = 12_000;
const MISSED_CALL_TIMEOUT_MS = 45_000;
const id = (prefix) => `${prefix}_${Date.now().toString(36)}_${crypto.randomUUID().replaceAll("-", "").slice(0, 12)}`;
const videoCallsEnabled = (env) => String(env.VIDEO_CALLS_ENABLED || "").toLowerCase() === "true";

async function bodyJson(request) {
  return request.json().catch(() => ({}));
}

function callRow(row = {}) {
  const callerPhotoUrl = (row.callerPhotoUrl || "").trim();
  const receiverPhotoUrl = (row.receiverPhotoUrl || "").trim();
  return {
    id: row.id || "",
    callerUid: row.callerUid || "",
    receiverUid: row.receiverUid || "",
    type: row.type || "voice",
    status: row.status || "ringing",
    startedAt: Number(row.startedAt || 0),
    ringingAt: Number(row.ringingAt || 0),
    acceptedAt: Number(row.acceptedAt || 0),
    connectedAt: Number(row.connectedAt || 0),
    endedAt: Number(row.endedAt || 0),
    durationSeconds: Number(row.durationSeconds || 0),
    endReason: row.endReason || "",
    createdAt: Number(row.createdAt || row.startedAt || 0),
    cloudflareSessionId: row.cloudflareSessionId || "",
    callerName: row.callerName || "",
    callerPhotoUrl: callerPhotoUrl && callerPhotoUrl !== receiverPhotoUrl ? callerPhotoUrl : "",
    receiverName: row.receiverName || "",
    receiverPhotoUrl,
  };
}

async function getCall(env, callId) {
  return env.DB.prepare(
    `SELECT c.*,
        COALESCE(caller.name, caller.username, caller.email, '') AS callerName,
        COALESCE(caller.photo_url, '') AS callerPhotoUrl,
        COALESCE(receiver.name, receiver.username, receiver.email, '') AS receiverName,
        COALESCE(receiver.photo_url, '') AS receiverPhotoUrl
       FROM calls c
       LEFT JOIN users caller ON caller.uid = c.callerUid
       LEFT JOIN users receiver ON receiver.uid = c.receiverUid
      WHERE c.id = ?`,
  ).bind(callId).first();
}

function canAccess(call, uid) {
  return call && (call.callerUid === uid || call.receiverUid === uid);
}

async function maybeSendIncomingCallNotification(env, call) {
  const caller = await env.DB.prepare("SELECT name, username, email, photo_url FROM users WHERE uid=?").bind(call.callerUid).first();
  const callerName = caller?.name || caller?.username || caller?.email || "AdisChat";
  const title = call.type === "video" ? "Incoming video call" : "Incoming voice call";
  return sendPushToUser(env, call.receiverUid, {
    type: "incoming_call",
    callId: call.id,
    callerId: call.callerUid,
    callerUid: call.callerUid,
    receiverId: call.receiverUid,
    callType: call.type,
    createdAt: call.startedAt || now(),
    callerName,
    title,
    body: callerName,
  }, {
    title,
    body: callerName,
  });
}

async function sendCallUpdate(env, call, status, actorUid) {
  const targetUid = call.callerUid === actorUid ? call.receiverUid : call.callerUid;
  if (!targetUid || targetUid === actorUid) return;
  await sendPushToUser(env, targetUid, {
    type: "call_update",
    callId: call.id,
    callStatus: status,
    callType: call.type,
    actorUid,
    title: "Call updated",
    body: status,
  });
}

function callEventText(type, status, durationSeconds = 0) {
  const label = type === "video" ? "Video call" : "Voice call";
  if (status === "completed" || status === "ended") return `${label} ended${durationSeconds ? ` - ${durationSeconds}s` : ""}`;
  if (status === "missed") return `Missed ${label.toLowerCase()}`;
  if (status === "unreachable") return `${label} not answered - user not reachable`;
  if (status === "cancelled") return `Cancelled ${label.toLowerCase()}`;
  if (status === "rejected") return `${label} rejected`;
  if (status === "failed") return `${label} failed`;
  return label;
}

async function ensureDirectConversation(env, call, ts) {
  const existing = await env.DB.prepare(
    `SELECT id FROM conversations
      WHERE type='direct' AND (
        (participant1_id=? AND participant2_id=?) OR
        (participant1_id=? AND participant2_id=?)
      )
      LIMIT 1`,
  ).bind(call.callerUid, call.receiverUid, call.receiverUid, call.callerUid).first();
  if (existing?.id) return existing.id;

  const receiver = await env.DB.prepare("SELECT name, photo_url FROM users WHERE uid=?").bind(call.receiverUid).first();
  const convId = id("conv");
  await env.DB.prepare(
    `INSERT INTO conversations(id, type, user_id, user_name, user_photo_url, participant1_id, participant2_id, created_at, updated_at)
     VALUES (?, 'direct', ?, ?, ?, ?, ?, ?, ?)`,
  ).bind(convId, call.receiverUid, receiver?.name || "", receiver?.photo_url || "", call.callerUid, call.receiverUid, ts, ts).run();
  return convId;
}

async function insertCallMessage(env, call, status, actorUid, ts, durationSeconds = 0) {
  const conversationId = await ensureDirectConversation(env, call, ts);
  const msgId = id("msg");
  const text = callEventText(call.type, status, durationSeconds);
  await env.DB.prepare(
    `INSERT INTO messages(id, conversation_id, sender_id, sender_role, text, timestamp, created_at, type,
       media_url, media_mime_type, duration_ms, forwarded, call_type, call_status, call_duration)
     VALUES (?, ?, ?, 'user', ?, ?, ?, 'call', '', '', 0, 0, ?, ?, ?)`,
  ).bind(msgId, conversationId, actorUid || call.callerUid, text, ts, ts, call.type, status, durationSeconds).run();
  await env.DB.prepare(
    `UPDATE conversations SET last_message=?, last_sender_role='user', last_message_at=?, updated_at=?,
       p1_unread = p1_unread + CASE WHEN participant1_id<>? THEN 1 ELSE 0 END,
       p2_unread = p2_unread + CASE WHEN participant2_id<>? THEN 1 ELSE 0 END
     WHERE id=?`,
  ).bind(text, ts, ts, actorUid || call.callerUid, actorUid || call.callerUid, conversationId).run();
}

async function markStaleOpenCalls(env, receiverUid = "") {
  const cutoff = now() - MISSED_CALL_TIMEOUT_MS;
  const query = receiverUid
    ? `SELECT c.*,
        COALESCE(caller.name, caller.username, caller.email, '') AS callerName,
        COALESCE(caller.photo_url, '') AS callerPhotoUrl,
        COALESCE(receiver.name, receiver.username, receiver.email, '') AS receiverName,
        COALESCE(receiver.photo_url, '') AS receiverPhotoUrl
       FROM calls c
       LEFT JOIN users caller ON caller.uid = c.callerUid
       LEFT JOIN users receiver ON receiver.uid = c.receiverUid
       WHERE c.receiverUid = ? AND c.status IN ('delivering','ringing') AND c.startedAt < ?
       ORDER BY c.startedAt ASC LIMIT 10`
    : `SELECT c.*,
        COALESCE(caller.name, caller.username, caller.email, '') AS callerName,
        COALESCE(caller.photo_url, '') AS callerPhotoUrl,
        COALESCE(receiver.name, receiver.username, receiver.email, '') AS receiverName,
        COALESCE(receiver.photo_url, '') AS receiverPhotoUrl
       FROM calls c
       LEFT JOIN users caller ON caller.uid = c.callerUid
       LEFT JOIN users receiver ON receiver.uid = c.receiverUid
       WHERE c.status IN ('delivering','ringing') AND c.startedAt < ?
       ORDER BY c.startedAt ASC LIMIT 10`;
  const stale = receiverUid
    ? await env.DB.prepare(query).bind(receiverUid, cutoff).all()
    : await env.DB.prepare(query).bind(cutoff).all();
  const ts = now();
  for (const call of stale.results || []) {
    const status = call.status === "ringing" ? "missed" : "unreachable";
    const reason = call.status === "ringing" ? "missed_timeout" : "no_receiver_ack";
    const updated = await env.DB.prepare("UPDATE calls SET status=?, endedAt=?, durationSeconds=0, endReason=?, endedByUid=? WHERE id=? AND status=?")
      .bind(status, ts, reason, call.callerUid, call.id, call.status).run();
    if (updated.meta?.changes) {
      if (status === "missed") await insertCallMessage(env, call, "missed", call.callerUid, ts, 0);
      await sendCallUpdate(env, call, status, call.callerUid);
      console.info("[calls] stale open call resolved", { callId: call.id, receiverUid: call.receiverUid, status, previousStatus: call.status });
    }
  }
}

async function markDeliveryTimedOut(env, call) {
  if (!call || call.status !== "delivering") return call;
  if (Number(call.startedAt || 0) >= now() - DELIVERY_ACK_TIMEOUT_MS) return call;
  const ts = now();
  const updated = await env.DB.prepare(
    "UPDATE calls SET status='unreachable', endedAt=?, durationSeconds=0, endReason='no_receiver_ack', endedByUid=? WHERE id=? AND status='delivering'",
  ).bind(ts, call.callerUid, call.id).run();
  if (!updated.meta?.changes) return getCall(env, call.id);
  const resolved = await getCall(env, call.id);
  await sendCallUpdate(env, resolved || call, "unreachable", call.receiverUid).catch(() => null);
  console.info("[calls] delivery ack timeout", {
    callId: call.id,
    callerUid: call.callerUid,
    receiverUid: call.receiverUid,
    waitedMs: ts - Number(call.startedAt || ts),
  });
  return resolved || { ...call, status: "unreachable", endedAt: ts, endReason: "no_receiver_ack" };
}

async function createRealtimeSession(env, call) {
  // TODO: Configure Cloudflare Realtime credentials:
  // env.CLOUDFLARE_ACCOUNT_ID, env.CLOUDFLARE_REALTIME_APP_ID, env.CLOUDFLARE_REALTIME_API_TOKEN.
  // Then create/reuse an SFU session and return its id. Until configured,
  // the call session still records in D1 and the app shows a friendly setup message.
  if (!env.CLOUDFLARE_ACCOUNT_ID || !env.CLOUDFLARE_REALTIME_APP_ID || !env.CLOUDFLARE_REALTIME_API_TOKEN) {
    return "";
  }
  const response = await fetch(
    `https://api.cloudflare.com/client/v4/accounts/${env.CLOUDFLARE_ACCOUNT_ID}/realtime/sfu/apps/${env.CLOUDFLARE_REALTIME_APP_ID}/sessions/new`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.CLOUDFLARE_REALTIME_API_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ sessionDescription: { type: "offer", sdp: "" }, callId: call.id }),
    },
  ).catch(() => null);
  if (!response?.ok) return "";
  const json = await response.json().catch(() => ({}));
  return json?.result?.sessionId || json?.result?.id || "";
}

async function createRealtimeToken(env, call, user) {
  const defaultIceServers = [
    { urls: ["stun:stun.cloudflare.com:3478"] },
    { urls: ["stun:stun.l.google.com:19302"] },
    { urls: ["stun:stun1.l.google.com:19302"] },
    { urls: ["stun:stun2.l.google.com:19302"] },
  ];
  const configuredIceServers = (() => {
    if (env.CLOUDFLARE_ICE_SERVERS_JSON) {
      try {
        const parsed = JSON.parse(env.CLOUDFLARE_ICE_SERVERS_JSON);
        if (Array.isArray(parsed) && parsed.length) return parsed;
      } catch {
        // Fall through to the simple TURN env format below.
      }
    }
    const turnUrls = (env.CLOUDFLARE_TURN_URLS || env.TURN_URLS || "").split(",").map((item) => item.trim()).filter(Boolean);
    const username = env.CLOUDFLARE_TURN_USERNAME || env.TURN_USERNAME || "";
    const credential = env.CLOUDFLARE_TURN_CREDENTIAL || env.TURN_CREDENTIAL || "";
    if (!turnUrls.length) return [];
    return [{ urls: turnUrls, username, credential }];
  })();
  const iceServers = [...configuredIceServers, ...defaultIceServers];
  const urls = iceServers.flatMap((server) => Array.isArray(server.urls) ? server.urls : [server.urls]).filter(Boolean);
  const iceSummary = {
    stun: urls.some((url) => String(url).toLowerCase().startsWith("stun:")),
    turn: urls.some((url) => String(url).toLowerCase().startsWith("turn:") || String(url).toLowerCase().startsWith("turns:")),
    count: iceServers.length,
  };
  console.info("[calls] ICE config prepared", {
    callId: call.id,
    hasStun: iceSummary.stun,
    hasTurn: iceSummary.turn,
    serverCount: iceSummary.count,
  });

  // TODO: Replace with Cloudflare Realtime token creation once account/app
  // credentials are bound. TURN/SFU credentials must be issued by backend only.
  if (!env.CLOUDFLARE_REALTIME_API_TOKEN || !call.cloudflareSessionId) {
    return {
      token: "",
      expiresAt: 0,
      sfuUrl: env.CLOUDFLARE_REALTIME_SFU_URL || "",
      turnUrls: (env.CLOUDFLARE_TURN_URLS || env.TURN_URLS || "").split(",").map((item) => item.trim()).filter(Boolean),
      iceServers,
      iceSummary,
      setupRequired: false,
      message: configuredIceServers.length
        ? "Using backend-provided TURN/ICE fallback while Cloudflare Realtime SFU is not configured."
        : "Using STUN fallback while Cloudflare Realtime TURN/SFU credentials are not configured.",
    };
  }
  return {
    token: "",
    expiresAt: 0,
    sfuUrl: env.CLOUDFLARE_REALTIME_SFU_URL || "",
    turnUrls: (env.CLOUDFLARE_TURN_URLS || env.TURN_URLS || "").split(",").map((item) => item.trim()).filter(Boolean),
    iceServers,
    iceSummary,
    setupRequired: true,
    message: "Cloudflare Realtime token endpoint is ready for credential wiring.",
  };
}

export async function handleCallsRequest(request, env, user) {
  const url = new URL(request.url);
  if (!url.pathname.startsWith("/api/calls")) return null;

  if (request.method === "POST" && url.pathname === "/api/calls/start") {
    const body = await bodyJson(request);
    const receiverUid = (body.receiverUid || "").toString().trim();
    const type = body.type === "video" ? "video" : "voice";
    if (type === "video" && !videoCallsEnabled(env)) {
      console.info("[calls] video call blocked: feature disabled", { callerUid: user.uid, receiverUid });
      return Response.json({ error: "Video calls are paused for this stability release" }, { status: 503 });
    }
    if (!receiverUid) return Response.json({ error: "receiverUid is required" }, { status: 400 });
    if (receiverUid === user.uid) return Response.json({ error: "Cannot call yourself" }, { status: 400 });
    const receiver = await env.DB.prepare("SELECT uid FROM users WHERE uid=?").bind(receiverUid).first();
    if (!receiver?.uid) return Response.json({ error: "Receiver not found" }, { status: 404 });

    const call = {
      id: id("call"),
      callerUid: user.uid,
      receiverUid,
      type,
      status: "delivering",
      startedAt: now(),
      cloudflareSessionId: "",
    };
    call.cloudflareSessionId = await createRealtimeSession(env, call);

    await env.DB.prepare(
      `INSERT INTO calls
        (id, callerUid, receiverUid, type, status, startedAt, acceptedAt, endedAt, durationSeconds, cloudflareSessionId)
       VALUES (?, ?, ?, ?, 'delivering', ?, 0, 0, 0, ?)`,
    ).bind(call.id, call.callerUid, call.receiverUid, call.type, call.startedAt, call.cloudflareSessionId).run();
    console.info("[calls] call record created", { callId: call.id, callerUid: call.callerUid, receiverUid: call.receiverUid, type });

    await env.DB.prepare(
      `INSERT INTO call_participants
        (id, callId, uid, role, joinedAt, leftAt, muted, cameraEnabled)
       VALUES (?, ?, ?, 'caller', ?, 0, 0, ?)`,
    ).bind(id("participant"), call.id, user.uid, call.startedAt, type === "video" ? 1 : 0).run();

    const pushResult = await maybeSendIncomingCallNotification(env, call);
    console.info("[calls] incoming push attempted", { callId: call.id, receiverUid: call.receiverUid, sent: pushResult?.sent || 0, ok: Boolean(pushResult?.ok), reason: pushResult?.reason || "" });
    if (!pushResult?.ok && (pushResult?.sent || 0) === 0) {
      console.warn("[calls] no immediate push transport; keeping call pending for polling fallback", {
        callId: call.id,
        callerUid: call.callerUid,
        receiverUid: call.receiverUid,
        reason: pushResult?.reason || "no_receiver_tokens",
        timeoutMs: DELIVERY_ACK_TIMEOUT_MS,
      });
    }
    const createdCall = await getCall(env, call.id);
    return Response.json({ call: callRow(createdCall || call), setupRequired: !call.cloudflareSessionId });
  }

  if (request.method === "POST" && ["/api/calls/accept", "/api/calls/reject", "/api/calls/end", "/api/calls/cancel", "/api/calls/connected", "/api/calls/token", "/api/calls/ringing_ack", "/api/calls/ringing-ack"].includes(url.pathname)) {
    const body = await bodyJson(request);
    const callId = (body.callId || "").toString();
    const call = await getCall(env, callId);
    if (!canAccess(call, user.uid)) return Response.json({ error: "Call not found" }, { status: 404 });

    if (url.pathname === "/api/calls/token") {
      return Response.json({ call: callRow(call), realtime: await createRealtimeToken(env, call, user) });
    }

    const ts = now();
    if (url.pathname === "/api/calls/ringing_ack" || url.pathname === "/api/calls/ringing-ack") {
      if (call.receiverUid !== user.uid) return Response.json({ error: "Only receiver can acknowledge ringing" }, { status: 403 });
      if (["cancelled", "rejected", "missed", "completed", "ended", "failed", "unreachable"].includes(call.status)) {
        return Response.json({ call: callRow(call) });
      }
      await env.DB.prepare("UPDATE calls SET status='ringing', ringingAt=? WHERE id=? AND status IN ('created','delivering','ringing')")
        .bind(ts, callId).run();
      await sendCallUpdate(env, call, "ringing", user.uid);
      console.info("[calls] ringing_ack received", { callId, receiverUid: user.uid });
      return Response.json({ call: callRow({ ...call, status: "ringing", ringingAt: ts }) });
    }

    if (url.pathname === "/api/calls/accept") {
      if (call.receiverUid !== user.uid) return Response.json({ error: "Only receiver can accept" }, { status: 403 });
      await env.DB.prepare("UPDATE calls SET status='accepted', acceptedAt=? WHERE id=?").bind(ts, callId).run();
      await env.DB.prepare(
        `INSERT INTO call_participants
        (id, callId, uid, role, joinedAt, leftAt, muted, cameraEnabled)
        VALUES (?, ?, ?, 'receiver', ?, 0, 0, ?)`,
      ).bind(id("participant"), callId, user.uid, ts, call.type === "video" ? 1 : 0).run();
      console.info("[calls] call answered", { callId, receiverUid: user.uid });
      return Response.json({ call: callRow({ ...call, status: "accepted", acceptedAt: ts }) });
    }

    if (url.pathname === "/api/calls/reject") {
      if (call.receiverUid !== user.uid) return Response.json({ error: "Only receiver can reject" }, { status: 403 });
      await env.DB.prepare("UPDATE calls SET status='rejected', rejectedAt=?, endedAt=?, endReason=?, endedByUid=? WHERE id=?")
        .bind(ts, ts, "rejected", user.uid, callId).run();
      await insertCallMessage(env, call, "rejected", user.uid, ts, 0);
      await sendCallUpdate(env, call, "rejected", user.uid);
      console.info("[calls] call declined", { callId, receiverUid: user.uid });
      return Response.json({ call: callRow({ ...call, status: "rejected", rejectedAt: ts, endedAt: ts, endReason: "rejected", endedByUid: user.uid }) });
    }

    if (url.pathname === "/api/calls/cancel") {
      if (call.callerUid !== user.uid) return Response.json({ error: "Only caller can cancel" }, { status: 403 });
      await env.DB.prepare("UPDATE calls SET status='cancelled', cancelledAt=?, endedAt=?, endReason=?, endedByUid=? WHERE id=?")
        .bind(ts, ts, "cancelled", user.uid, callId).run();
      await insertCallMessage(env, call, "cancelled", user.uid, ts, 0);
      await sendCallUpdate(env, call, "cancelled", user.uid);
      console.info("[calls] call ended", { callId, status: "cancelled", actorUid: user.uid });
      return Response.json({ call: callRow({ ...call, status: "cancelled", cancelledAt: ts, endedAt: ts, endReason: "cancelled", endedByUid: user.uid }) });
    }

    if (url.pathname === "/api/calls/connected") {
      await env.DB.prepare("UPDATE calls SET status='connected', connectedAt=? WHERE id=?").bind(ts, callId).run();
      return Response.json({ call: callRow({ ...call, status: "connected", connectedAt: ts }) });
    }

    const acceptedAt = call.acceptedAt || 0;
    const durationSeconds = acceptedAt > 0 ? Math.max(0, Math.round((ts - acceptedAt) / 1000)) : 0;
    const reason = (body.endReason || "").toString();
    const status = reason === "network_error" ? "failed" : acceptedAt > 0 ? "completed" : (call.ringingAt || call.status === "ringing") ? "missed" : "unreachable";
    await env.DB.prepare("UPDATE calls SET status=?, endedAt=?, durationSeconds=?, endReason=?, endedByUid=? WHERE id=?")
      .bind(status, ts, durationSeconds, (body.endReason || status).toString(), user.uid, callId).run();
    await env.DB.prepare("UPDATE call_participants SET leftAt=? WHERE callId=? AND uid=? AND leftAt=0")
      .bind(ts, callId, user.uid).run();
    await insertCallMessage(env, call, status, user.uid, ts, durationSeconds);
    await sendCallUpdate(env, call, status, user.uid);
    if (status === "missed") console.info("[calls] missed call created", { callId, receiverUid: call.receiverUid });
    if (status === "unreachable") console.info("[calls] call unreachable", { callId, receiverUid: call.receiverUid });
    console.info("[calls] call ended", { callId, status, actorUid: user.uid });
    return Response.json({ call: callRow({ ...call, status, endedAt: ts, durationSeconds }) });
  }

  if (request.method === "GET" && url.pathname === "/api/calls/active") {
    await markStaleOpenCalls(env, user.uid);
    const call = await env.DB.prepare(
      `SELECT c.*, caller.name AS callerName, caller.photo_url AS callerPhotoUrl, receiver.name AS receiverName, receiver.photo_url AS receiverPhotoUrl
       FROM calls c
       LEFT JOIN users caller ON caller.uid=c.callerUid
       LEFT JOIN users receiver ON receiver.uid=c.receiverUid
       WHERE c.receiverUid=? AND c.status IN ('delivering','ringing','accepted','connected')
       ORDER BY c.startedAt DESC LIMIT 1`,
    ).bind(user.uid).first();
    return Response.json({ call: call ? callRow(call) : null });
  }

  if (request.method === "GET" && url.pathname === "/api/calls/pending") {
    await markStaleOpenCalls(env, user.uid);
    const call = await env.DB.prepare(
      `SELECT c.*, caller.name AS callerName, caller.photo_url AS callerPhotoUrl, receiver.name AS receiverName, receiver.photo_url AS receiverPhotoUrl
       FROM calls c
       LEFT JOIN users caller ON caller.uid=c.callerUid
       LEFT JOIN users receiver ON receiver.uid=c.receiverUid
       WHERE c.receiverUid=? AND c.status IN ('delivering','ringing')
       ORDER BY c.startedAt DESC LIMIT 1`,
    ).bind(user.uid).first();
    console.info("[calls] pending poll result", { receiverUid: user.uid, hasCall: Boolean(call?.id), callId: call?.id || "" });
    return Response.json({ call: call ? callRow(call) : null });
  }

  const getMatch = url.pathname.match(/^\/api\/calls\/([^/]+)$/);
  if (request.method === "GET" && getMatch) {
    let call = await getCall(env, decodeURIComponent(getMatch[1]));
    if (!canAccess(call, user.uid)) return Response.json({ error: "Call not found" }, { status: 404 });
    if (call.callerUid === user.uid) call = await markDeliveryTimedOut(env, call);
    return Response.json({ call: callRow(call) });
  }

  const signalMatch = url.pathname.match(/^\/api\/calls\/([^/]+)\/signal$/);
  if (request.method === "POST" && signalMatch) {
    const callId = decodeURIComponent(signalMatch[1]);
    const call = await getCall(env, callId);
    if (!canAccess(call, user.uid)) return Response.json({ error: "Call not found" }, { status: 404 });
    const body = await bodyJson(request);
    await env.DB.prepare(
      "INSERT INTO call_signals(call_id, from_uid, kind, payload, created_at) VALUES (?, ?, ?, ?, ?)",
    ).bind(callId, user.uid, (body.kind || "").toString().slice(0, 32), (body.payload || "").toString(), now()).run();
    return Response.json({ ok: true });
  }

  const signalsMatch = url.pathname.match(/^\/api\/calls\/([^/]+)\/signals$/);
  if (request.method === "GET" && signalsMatch) {
    const callId = decodeURIComponent(signalsMatch[1]);
    const call = await getCall(env, callId);
    if (!canAccess(call, user.uid)) return Response.json({ error: "Call not found" }, { status: 404 });
    const after = Number(url.searchParams.get("after") || 0);
    const rows = await env.DB.prepare(
      "SELECT seq, from_uid AS fromUid, kind, payload FROM call_signals WHERE call_id=? AND seq>? AND from_uid<>? ORDER BY seq ASC LIMIT 100",
    ).bind(callId, after, user.uid).all();
    return Response.json({ status: call.status || "ringing", signals: rows.results || [] });
  }

  if (request.method === "GET" && url.pathname === "/api/calls/history") {
    const limit = Math.min(Number(url.searchParams.get("limit") || 50), 100);
    const rows = await env.DB.prepare(
      `SELECT c.*,
        COALESCE(caller.name, caller.username, caller.email, '') AS callerName,
        COALESCE(caller.photo_url, '') AS callerPhotoUrl,
        COALESCE(receiver.name, receiver.username, receiver.email, '') AS receiverName,
        COALESCE(receiver.photo_url, '') AS receiverPhotoUrl
        FROM calls c
        LEFT JOIN users caller ON caller.uid = c.callerUid
        LEFT JOIN users receiver ON receiver.uid = c.receiverUid
        WHERE c.callerUid = ? OR c.receiverUid = ?
        ORDER BY c.startedAt DESC
        LIMIT ?`,
    ).bind(user.uid, user.uid, limit).all();
    return Response.json({ calls: (rows.results || []).map(callRow) });
  }

  return Response.json({ error: "Not found" }, { status: 404 });
}
