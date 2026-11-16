import { requireFirebaseUser } from "./firebase-auth.mjs";

const encoder = new TextEncoder();
const QR_TTL_MS = 90_000;
const SESSION_TTL_MS = 1000 * 60 * 60 * 24 * 60;
const DESKTOP_LAST_ACTIVE_WRITE_INTERVAL_MS = 3 * 60 * 1000;

function id(prefix) {
  return `${prefix}_${Date.now().toString(36)}_${crypto.randomUUID().replaceAll("-", "").slice(0, 18)}`;
}

function now() {
  return Date.now();
}

async function bodyJson(request) {
  return request.json().catch(() => ({}));
}

function base64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function randomToken(size = 32) {
  const bytes = new Uint8Array(size);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

async function sha256(value) {
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(value));
  return base64Url(new Uint8Array(digest));
}

function deviceFromBody(body, request) {
  const ua = request.headers.get("user-agent") || "Desktop";
  return {
    deviceId: (body.desktopDeviceId || body.deviceId || id("desktop_device")).toString().slice(0, 128),
    deviceName: (body.desktopDeviceName || body.deviceName || "AdisChat Desktop").toString().slice(0, 120),
    os: (body.desktopOs || body.os || (ua.includes("Windows") ? "Windows" : ua.includes("Mac") ? "macOS" : ua.includes("Linux") ? "Linux" : "Desktop")).toString().slice(0, 80),
    ip: request.headers.get("cf-connecting-ip") || "",
    location: [request.cf?.city, request.cf?.region, request.cf?.country].filter(Boolean).join(", "),
  };
}

function publicSession(row) {
  const ts = now();
  const expired = row.expires_at <= ts && !["approved", "rejected"].includes(row.status);
  return {
    qrSessionId: row.id,
    status: expired ? "expired" : row.status,
    expiresAt: row.expires_at,
    scannedAt: row.scanned_at || 0,
    approvedAt: row.approved_at || 0,
    rejectedAt: row.rejected_at || 0,
    device: {
      name: row.desktop_device_name || "AdisChat Desktop",
      os: row.desktop_os || "Desktop",
      location: row.desktop_location || "",
      ip: row.desktop_ip || "",
    },
  };
}

async function getSession(env, id) {
  return env.DB.prepare("SELECT * FROM desktop_qr_sessions WHERE id = ?").bind(id).first();
}

async function expireIfNeeded(env, row) {
  if (!row || row.expires_at > now() || ["approved", "rejected", "expired"].includes(row.status)) return row;
  await env.DB.prepare("UPDATE desktop_qr_sessions SET status='expired' WHERE id=? AND status IN ('pending','scanned')").bind(row.id).run();
  return { ...row, status: "expired" };
}

async function findByToken(env, token) {
  const tokenHash = await sha256(token);
  const row = await env.DB.prepare("SELECT * FROM desktop_qr_sessions WHERE token_hash = ?").bind(tokenHash).first();
  return expireIfNeeded(env, row);
}

function extractQr(body) {
  const raw = (body.qrPayload || body.payload || body.token || "").toString().trim();
  if (!raw) return { token: "", qrSessionId: (body.qrSessionId || "").toString() };
  try {
    const url = new URL(raw);
    return { token: url.searchParams.get("token") || raw, qrSessionId: url.searchParams.get("sid") || (body.qrSessionId || "").toString() };
  } catch {
    return { token: raw.replace(/^adischat-desktop-login:/, ""), qrSessionId: (body.qrSessionId || "").toString() };
  }
}

async function protectedUser(request, env) {
  const auth = await requireFirebaseUser(request, env);
  if (auth.error) return auth;
  return auth;
}

export async function handleDesktopQrAuthRequest(request, env) {
  const url = new URL(request.url);
  if (!url.pathname.startsWith("/auth/desktop/qr") && !url.pathname.startsWith("/api/linked-devices")) return null;

  if (!env.DB) return Response.json({ error: "D1 database is not configured" }, { status: 500 });

  if (request.method === "POST" && url.pathname === "/auth/desktop/qr/create") {
    const body = await bodyJson(request);
    const device = deviceFromBody(body, request);
    const token = randomToken(36);
    const qrSessionId = id("qrdesk");
    const expiresAt = now() + QR_TTL_MS;
    const qrPayload = `adischat://desktop-login?sid=${encodeURIComponent(qrSessionId)}&token=${encodeURIComponent(token)}`;
    await env.DB.prepare(
      `INSERT INTO desktop_qr_sessions
        (id, token_hash, status, created_at, expires_at, scanned_at, approved_at, rejected_at, user_id,
        desktop_device_id, desktop_device_name, desktop_os, desktop_ip, desktop_location, used_at)
       VALUES (?, ?, 'pending', ?, ?, 0, 0, 0, '', ?, ?, ?, ?, ?, 0)`,
    ).bind(qrSessionId, await sha256(token), now(), expiresAt, device.deviceId, device.deviceName, device.os, device.ip, device.location).run();
    return Response.json({ qrSessionId, qrPayload, expiresAt, status: "pending" });
  }

  if (request.method === "GET" && url.pathname.startsWith("/auth/desktop/qr/status/")) {
    const qrSessionId = decodeURIComponent(url.pathname.split("/").pop() || "");
    let row = await getSession(env, qrSessionId);
    row = await expireIfNeeded(env, row);
    if (!row) return Response.json({ error: "QR session not found" }, { status: 404 });
    const response = publicSession(row);
    if (row.status === "approved" && row.desktop_session_token) {
      response.desktopToken = row.desktop_session_token;
      response.userId = row.user_id;
      await env.DB.prepare("UPDATE desktop_qr_sessions SET desktop_session_token = '' WHERE id = ?").bind(row.id).run();
    }
    return Response.json(response);
  }

  if (request.method === "POST" && ["/auth/desktop/qr/scan", "/auth/desktop/qr/approve", "/auth/desktop/qr/reject"].includes(url.pathname)) {
    const auth = await protectedUser(request, env);
    if (auth.error) return auth.error;
    const body = await bodyJson(request);
    const { token, qrSessionId } = extractQr(body);
    let row = token ? await findByToken(env, token) : await getSession(env, qrSessionId);
    row = await expireIfNeeded(env, row);
    if (!row || row.status === "expired") return Response.json({ error: "QR code expired. Generate a new code." }, { status: 410 });
    if (row.status === "approved" || row.used_at) return Response.json({ error: "QR code already used" }, { status: 409 });
    if (row.status === "rejected") return Response.json({ error: "QR login was rejected" }, { status: 409 });

    const ts = now();
    if (url.pathname.endsWith("/scan")) {
      await env.DB.prepare("UPDATE desktop_qr_sessions SET status='scanned', scanned_at=?, user_id=? WHERE id=? AND status='pending'")
        .bind(ts, auth.user.uid, row.id).run();
      return Response.json({ ...publicSession({ ...row, status: "scanned", scanned_at: ts, user_id: auth.user.uid }), user: { uid: auth.user.uid, name: auth.user.name, photoUrl: auth.user.photoUrl } });
    }

    if (url.pathname.endsWith("/reject")) {
      await env.DB.prepare("UPDATE desktop_qr_sessions SET status='rejected', rejected_at=?, user_id=? WHERE id=? AND status IN ('pending','scanned')")
        .bind(ts, auth.user.uid, row.id).run();
      return Response.json({ ok: true, status: "rejected" });
    }

    const sessionToken = `adis_desktop_${randomToken(48)}`;
    const sessionId = id("session");
    const expiresAt = ts + SESSION_TTL_MS;
    await env.DB.prepare(
      `INSERT INTO linked_devices
        (id, user_id, session_token_hash, device_type, device_name, os, app_version, ip, location, last_active_at, created_at, expires_at, revoked_at)
       VALUES (?, ?, ?, 'desktop', ?, ?, ?, ?, ?, ?, ?, ?, 0)`,
    ).bind(sessionId, auth.user.uid, await sha256(sessionToken), row.desktop_device_name || "AdisChat Desktop", row.desktop_os || "Desktop", body.appVersion || "desktop", row.desktop_ip || "", row.desktop_location || "", ts, ts, expiresAt).run();
    await env.DB.prepare("UPDATE desktop_qr_sessions SET status='approved', approved_at=?, used_at=?, user_id=?, desktop_session_token=? WHERE id=? AND status IN ('pending','scanned')")
      .bind(ts, ts, auth.user.uid, sessionToken, row.id).run();
    return Response.json({ ok: true, status: "approved", sessionId, expiresAt });
  }

  if (url.pathname.startsWith("/api/linked-devices")) {
    const auth = await protectedUser(request, env);
    if (auth.error) return auth.error;
    if (request.method === "GET" && url.pathname === "/api/linked-devices") {
      const rows = await env.DB.prepare(
        `SELECT id, device_type, device_name, os, app_version, ip, location, last_active_at, created_at, expires_at, revoked_at
        FROM linked_devices WHERE user_id = ? ORDER BY last_active_at DESC`,
      ).bind(auth.user.uid).all();
      return Response.json({ devices: (rows.results || []).map((row) => ({
        id: row.id,
        deviceType: row.device_type,
        deviceName: row.device_name,
        os: row.os,
        appVersion: row.app_version,
        ip: row.ip,
        location: row.location,
        lastActiveAt: row.last_active_at,
        createdAt: row.created_at,
        expiresAt: row.expires_at,
        revokedAt: row.revoked_at,
      })) });
    }
    if (request.method === "POST" && url.pathname === "/api/linked-devices/logout-all") {
      await env.DB.prepare("UPDATE linked_devices SET revoked_at=? WHERE user_id=? AND device_type='desktop' AND revoked_at=0").bind(now(), auth.user.uid).run();
      return Response.json({ ok: true });
    }
    const match = url.pathname.match(/^\/api\/linked-devices\/([^/]+)\/logout$/);
    if (request.method === "POST" && match) {
      await env.DB.prepare("UPDATE linked_devices SET revoked_at=? WHERE id=? AND user_id=?").bind(now(), decodeURIComponent(match[1]), auth.user.uid).run();
      return Response.json({ ok: true });
    }
  }

  return Response.json({ error: "Not found" }, { status: 404 });
}

export async function authenticateDesktopSession(request, env) {
  const authorization = request.headers.get("authorization") || "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
  if (!token.startsWith("adis_desktop_")) return null;
  const tokenHash = await sha256(token);
  const row = await env.DB.prepare("SELECT * FROM linked_devices WHERE session_token_hash=? AND revoked_at=0 AND expires_at > ?")
    .bind(tokenHash, now()).first();
  if (!row) return null;
  const currentTs = now();
  const lastActiveAt = Number(row.last_active_at || 0);
  if (currentTs - lastActiveAt >= DESKTOP_LAST_ACTIVE_WRITE_INTERVAL_MS) {
    env.DB.prepare("UPDATE linked_devices SET last_active_at=? WHERE id=?")
      .bind(currentTs, row.id)
      .run()
      .then(() => console.info("[desktop-auth] last_active_at write", { sessionId: row.id, skipped: false }))
      .catch((error) => console.warn("[desktop-auth] last_active_at write failed", { sessionId: row.id, error: String(error) }));
  } else {
    console.debug("[desktop-auth] last_active_at write skipped", {
      sessionId: row.id,
      skipped: true,
      ageMs: currentTs - lastActiveAt,
    });
  }
  return {
    uid: row.user_id,
    role: "user",
    name: "",
    photoUrl: "",
    desktopSessionId: row.id,
  };
}