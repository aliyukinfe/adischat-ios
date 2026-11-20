const LEGACY_FCM_URL = "https://fcm.googleapis.com/fcm/send";
const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const TOKEN_URL = "https://oauth2.googleapis.com/token";

function cleanData(data = {}) {
  const out = {};
  for (const [key, value] of Object.entries(data)) {
    if (value === undefined || value === null) continue;
    out[key] = String(value);
  }
  return out;
}

function base64Url(input) {
  const bytes = input instanceof Uint8Array ? input : new TextEncoder().encode(String(input));
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function pemToArrayBuffer(pem) {
  const body = String(pem || "")
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

async function serviceAccountAccessToken(serviceAccount) {
  const nowSeconds = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64Url(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: FCM_SCOPE,
    aud: TOKEN_URL,
    iat: nowSeconds,
    exp: nowSeconds + 3600,
  }));
  const unsigned = `${header}.${claim}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const assertion = `${unsigned}.${base64Url(new Uint8Array(signature))}`;
  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) throw new Error("FCM access token request failed");
  const json = await response.json();
  return json.access_token || "";
}

async function userTokens(env, uid) {
  if (!env.DB || !uid) return [];
  const rows = await env.DB.prepare(
    "SELECT token FROM push_tokens WHERE uid=? AND token<>'' ORDER BY updated_at DESC LIMIT 20",
  ).bind(uid).all();
  return [...new Set((rows.results || []).map((row) => row.token).filter(Boolean))];
}

async function sendLegacy(env, tokens, data, notification) {
  if (!env.FCM_SERVER_KEY) return null;
  const payload = { registration_ids: tokens, priority: "high", data };
  if (notification) payload.notification = notification;
  const response = await fetch(LEGACY_FCM_URL, {
    method: "POST",
    headers: {
      Authorization: `key=${env.FCM_SERVER_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  }).catch(() => null);
  return { ok: !!response?.ok, status: response?.status || 0, sent: tokens.length };
}

async function sendV1(env, tokens, data, notification) {
  if (!env.FCM_SERVICE_ACCOUNT) return null;
  const serviceAccount = JSON.parse(env.FCM_SERVICE_ACCOUNT);
  const projectId = serviceAccount.project_id || env.FIREBASE_PROJECT_ID;
  const accessToken = await serviceAccountAccessToken(serviceAccount);
  let sent = 0;
  for (const token of tokens) {
    const channelId = data?.type === "incoming_call" ? "incoming_calls" : data?.type === "message" ? "messages" : undefined;
    const message = {
      token,
      data,
      android: {
        priority: "HIGH",
        ...(notification && channelId ? {
          notification: {
            channel_id: channelId,
            notification_priority: data?.type === "incoming_call" ? "PRIORITY_MAX" : "PRIORITY_HIGH",
          },
        } : {}),
      },
    };
    if (notification) message.notification = notification;
    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ message }),
    }).catch(() => null);
    if (response?.ok) sent += 1;
  }
  return { ok: sent > 0, sent };
}

export async function sendPushToUser(env, uid, data, notification = null) {
  const tokens = await userTokens(env, uid);
  console.info("[push] tokens selected", { uid, count: tokens.length, type: data?.type || "" });
  if (!tokens.length) return { ok: false, reason: "no_tokens", sent: 0 };
  const clean = cleanData(data);
  const note = notification ? { title: String(notification.title || "AdisChat"), body: String(notification.body || "") } : null;

  const legacy = await sendLegacy(env, tokens, clean, note);
  if (legacy) {
    console.info("[push] legacy result", { uid, type: clean.type || "", ok: Boolean(legacy.ok), sent: legacy.sent || 0, status: legacy.status || 0 });
    return legacy;
  }

  const v1 = await sendV1(env, tokens, clean, note).catch((error) => {
    console.error("[push] FCM v1 send failed", {
      uid,
      type: clean.type || "",
      error: String(error?.message || "FCM request failed").slice(0, 160),
    });
    return null;
  });
  if (v1) {
    console.info("[push] v1 result", { uid, type: clean.type || "", ok: Boolean(v1.ok), sent: v1.sent || 0 });
    return v1;
  }

  const reason = env.FCM_SERVICE_ACCOUNT || env.FCM_SERVER_KEY ? "fcm_send_failed" : "missing_fcm_credentials";
  console.warn("[push] notification not sent", { uid, type: clean.type || "", tokenCount: tokens.length, reason });
  return { ok: false, reason, sent: 0 };
}
