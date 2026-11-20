const keyCache = {
  expiresAt: 0,
  keys: null,
};

const encoder = new TextEncoder();

function json(body, status = 200) {
  return Response.json(body, { status });
}

function base64UrlToBytes(value) {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function base64UrlJson(value) {
  return JSON.parse(new TextDecoder().decode(base64UrlToBytes(value)));
}

async function getFirebaseKeys() {
  const now = Date.now();
  if (keyCache.keys && keyCache.expiresAt > now) return keyCache.keys;

  const response = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
  if (!response.ok) throw new Error("Unable to fetch Firebase public keys");

  const maxAgeMatch = response.headers.get("cache-control")?.match(/max-age=(\d+)/);
  const maxAgeMs = Number(maxAgeMatch?.[1] || 3600) * 1000;
  const body = await response.json();
  keyCache.keys = Object.fromEntries((body.keys || []).map((key) => [key.kid, key]));
  keyCache.expiresAt = now + Math.max(60000, maxAgeMs - 30000);
  return keyCache.keys;
}

async function verifySignature(header, signingInput, signature) {
  if (header.alg !== "RS256" || !header.kid) return false;
  const keys = await getFirebaseKeys();
  const jwk = keys[header.kid];
  if (!jwk) return false;

  const key = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );
  return crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, signature, encoder.encode(signingInput));
}

async function readUserRecord(env, uid) {
  if (!env.DB) return null;
  const queries = [
    "SELECT uid, role, name, photo_url FROM users WHERE uid = ?",
    "SELECT uid, role, display_name AS name, photo_url FROM users WHERE uid = ?",
    "SELECT id AS uid, role, name, avatar_url AS photo_url FROM users WHERE id = ?",
    "SELECT user_id AS uid, role, name, photo_url FROM profiles WHERE user_id = ?",
  ];

  for (const sql of queries) {
    try {
      const row = await env.DB.prepare(sql).bind(uid).first();
      if (row) return row;
    } catch {
      // Schemas differ between the app databases; try the next known shape.
    }
  }
  return null;
}

export async function requireFirebaseUser(request, env) {
  const projectId = env.FIREBASE_PROJECT_ID;
  if (!projectId) return { error: json({ error: "FIREBASE_PROJECT_ID is not configured" }, 500) };

  const authorization = request.headers.get("authorization") || "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
  if (!token) return { error: json({ error: "Missing Firebase ID token" }, 401) };

  const parts = token.split(".");
  if (parts.length !== 3) return { error: json({ error: "Invalid Firebase ID token" }, 401) };

  let header;
  let payload;
  try {
    header = base64UrlJson(parts[0]);
    payload = base64UrlJson(parts[1]);
  } catch {
    return { error: json({ error: "Invalid Firebase ID token" }, 401) };
  }

  const nowSeconds = Math.floor(Date.now() / 1000);
  const expectedIssuer = `https://securetoken.google.com/${projectId}`;
  if (payload.aud !== projectId || payload.iss !== expectedIssuer || !payload.sub || payload.exp <= nowSeconds) {
    return { error: json({ error: "Expired or rejected Firebase ID token" }, 401) };
  }

  const valid = await verifySignature(header, `${parts[0]}.${parts[1]}`, base64UrlToBytes(parts[2]));
  if (!valid) return { error: json({ error: "Firebase ID token signature failed" }, 401) };

  const uid = payload.user_id || payload.sub;
  const record = await readUserRecord(env, uid);
  const user = {
    uid,
    email: payload.email || "",
    role: record?.role || payload.role || "user",
    name: record?.name || payload.name || "",
    photoUrl: record?.photo_url || "",
    authTime: payload.auth_time || null,
    phoneNumber: payload.phone_number || null,
    signInProvider: payload.firebase?.sign_in_provider || null,
    claims: payload,
  };
  return { user, userRecord: record || user };
}
