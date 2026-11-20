import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import worker from "./index.mjs";
import { sendPushToUser } from "../../shared/push.mjs";

const crypto = globalThis.crypto ?? webcrypto;
const baseUrl = "https://adischat-api.adischat.workers.dev";
const uid = "p7e-synthetic-user";
const authPair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
const authJwk = await crypto.subtle.exportKey("jwk", authPair.publicKey);

async function firebaseToken(overrides = {}) {
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT", kid: "p7e-kid" })).toString("base64url");
  const body = Buffer.from(JSON.stringify({
    aud: "adischat-f4a77", iss: "https://securetoken.google.com/adischat-f4a77",
    sub: uid, user_id: uid, iat: now - 60, auth_time: now - 60, exp: now + 300,
    ...overrides,
  })).toString("base64url");
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", authPair.privateKey, new TextEncoder().encode(`${header}.${body}`));
  return `${header}.${body}.${Buffer.from(signature).toString("base64url")}`;
}

function jwksResponse() {
  return Response.json({ keys: [{ ...authJwk, kid: "p7e-kid" }] }, { headers: { "Cache-Control": "max-age=300" } });
}

function database() {
  return {
    prepare(sql) {
      return {
        bind() {
          return {
            async first() {
              if (sql.includes("push_tokens")) return null;
              return { uid, role: "user", name: "Synthetic User", account_status: "active" };
            },
            async all() { return { results: [] }; },
            async run() { return { success: true }; },
          };
        },
      };
    },
  };
}

function env(extra = {}) {
  return {
    FIREBASE_PROJECT_ID: "adischat-f4a77",
    CORS_ORIGIN: "*",
    DB: database(),
    MEDIA: { async get(key) {
      if (key !== "synthetic-image") return null;
      return {
        body: new TextEncoder().encode("synthetic-media"), httpEtag: '"synthetic"',
        writeHttpMetadata(headers) { headers.set("Content-Type", "text/plain"); },
      };
    } },
    ...extra,
  };
}

test("P7E public health and media plus existing global preflight retain runtime behavior", async () => {
  const health = await worker.fetch(new Request(`${baseUrl}/api/health`), env());
  assert.equal(health.status, 200);
  assert.equal((await health.json()).service, "adischat-api");
  assert.equal(health.headers.get("Access-Control-Allow-Origin"), "*");

  const media = await worker.fetch(new Request(`${baseUrl}/api/media/synthetic-image`), env());
  assert.equal(media.status, 200);
  assert.equal(await media.text(), "synthetic-media");
  const missing = await worker.fetch(new Request(`${baseUrl}/api/media/missing`), env());
  assert.equal(missing.status, 404);

  const preflight = await worker.fetch(new Request(`${baseUrl}/api/conversations`, { method: "OPTIONS" }), env());
  assert.equal(preflight.status, 204);
  assert.equal(preflight.headers.get("Access-Control-Allow-Origin"), "*");
});

test("P7E existing protected route families reject missing Firebase tokens", async () => {
  for (const path of [
    "/api/users/me", "/api/conversations", "/api/calls/active", "/api/shop/session-assertion",
    "/api/linked-devices", "/api/channels/synthetic/verification", "/api/admin/users",
  ]) {
    const result = await worker.fetch(new Request(`${baseUrl}${path}`), env());
    assert.equal(result.status, 401, path);
    assert.equal(result.headers.get("Access-Control-Allow-Origin"), "*", path);
  }
  const push = await worker.fetch(new Request(`${baseUrl}/api/internal/shop/push`, { method: "POST" }), env());
  assert.equal(push.status, 401);
});

test("P7E Firebase-authenticated Shop, core chat, calls and desktop QR routes still dispatch", async () => {
  const authorization = `Bearer ${await firebaseToken()}`;
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url) => {
    assert.equal(url, "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
    return jwksResponse();
  };
  try {
    const config = env({ ADISHOP_ASSERTION_SECRET: "synthetic-shop-only-secret-1234567890" });
    const authenticated = (path, method = "GET") => worker.fetch(new Request(`${baseUrl}${path}`, {
      method, headers: { Authorization: authorization },
    }), config);
    const shop = await authenticated("/api/shop/session-assertion", "POST");
    assert.equal(shop.status, 200);
    assert.equal((await shop.json()).ok, true);
    const conversations = await authenticated("/api/conversations");
    assert.equal(conversations.status, 200);
    assert.deepEqual(await conversations.json(), []);
    const profile = await authenticated("/api/users/me");
    assert.equal(profile.status, 200);
    const calls = await authenticated("/api/calls/active");
    assert.equal(calls.status, 200);
    const linked = await authenticated("/api/linked-devices");
    assert.equal(linked.status, 200);
    const qr = await worker.fetch(new Request(`${baseUrl}/auth/desktop/qr/create`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: "{}",
    }), config);
    assert.equal(qr.status, 200);
    assert.equal((await qr.json()).status, "pending");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("P7E Restaurants route enforces production Firebase and live revocation through Worker entry", async () => {
  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const privateDer = await crypto.subtle.exportKey("pkcs8", pair.privateKey);
  const admin = JSON.stringify({
    project_id: "adischat-f4a77",
    client_email: "synthetic@adischat-f4a77.iam.gserviceaccount.com",
    private_key: `-----BEGIN PRIVATE KEY-----\n${Buffer.from(privateDer).toString("base64")}\n-----END PRIVATE KEY-----`,
  });
  const config = env({
    RESTAURANTS_ASSERTION_SECRET: "synthetic-production-restaurants-secret-123456",
    RESTAURANTS_FIREBASE_AUTH_SERVICE_ACCOUNT: admin,
    RESTAURANTS_BRIDGE_RATE_LIMITER: { limit: async () => ({ success: true }) },
  });
  const originalFetch = globalThis.fetch;
  let liveState = "valid";
  globalThis.fetch = async (url) => {
    if (String(url).includes("securetoken@system.gserviceaccount.com")) return jwksResponse();
    if (String(url).includes("oauth2.googleapis.com")) return Response.json({ access_token: "synthetic-access" });
    if (String(url).includes("identitytoolkit.googleapis.com")) {
      if (liveState === "unavailable") return Response.json({}, { status: 503 });
      return Response.json({ users: [{ localId: uid, disabled: liveState === "disabled", validSince: String(Math.floor(Date.now() / 1000) + (liveState === "revoked" ? 10 : -120)) }] });
    }
    throw new Error("Unexpected synthetic fetch");
  };
  const call = async (token, currentConfig = config) => worker.fetch(new Request(`${baseUrl}/api/restaurants/session-assertion`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, Origin: "https://restaurant.adischat.com", "Content-Type": "application/json" },
    body: JSON.stringify({ project_scope: "restaurants" }),
  }), currentConfig);
  try {
    const goodToken = await firebaseToken();
    const valid = await call(goodToken);
    assert.equal(valid.status, 200);
    assert.equal(valid.headers.get("Access-Control-Allow-Origin"), "https://restaurant.adischat.com");
    assert.equal(JSON.parse(Buffer.from((await valid.json()).assertion.split(".")[1], "base64url").toString("utf8")).aud, "adischat-restaurants:npmniymdxabjvagierwq");
    liveState = "disabled";
    assert.equal((await call(goodToken)).status, 403);
    liveState = "revoked";
    assert.equal((await call(goodToken)).status, 401);
    liveState = "unavailable";
    assert.equal((await call(goodToken)).status, 503);
    liveState = "valid";
    assert.equal((await call(await firebaseToken({ aud: "adischat-qa" }))).status, 401);
    assert.equal((await call(goodToken, env({ ...config, RESTAURANTS_BRIDGE_RATE_LIMITER: null }))).status, 503);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("P7E recovered push module retains no-token, legacy and FCM v1 paths", async () => {
  const noTokens = await sendPushToUser(env(), uid, { type: "message" });
  assert.deepEqual(noTokens, { ok: false, reason: "no_tokens", sent: 0 });

  const pushDb = {
    prepare() { return { bind() { return { async all() { return { results: [{ token: "synthetic-device-token" }] }; } }; } }; },
  };
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), auth: init?.headers?.Authorization || "", body: String(init?.body || "") });
    return Response.json({ success: 1 });
  };
  try {
    const legacy = await sendPushToUser(env({ DB: pushDb, FCM_SERVER_KEY: "synthetic-legacy-key" }), uid, { type: "shop_message" });
    assert.equal(legacy.ok, true);
    assert.equal(legacy.sent, 1);
    assert.equal(calls[0].url, "https://fcm.googleapis.com/fcm/send");
    assert.equal(JSON.parse(calls[0].body).registration_ids[0], "synthetic-device-token");
  } finally {
    globalThis.fetch = originalFetch;
  }

  const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const privateDer = await crypto.subtle.exportKey("pkcs8", pair.privateKey);
  const serviceAccount = JSON.stringify({
    project_id: "adischat-f4a77",
    client_email: "synthetic@adischat-f4a77.iam.gserviceaccount.com",
    private_key: `-----BEGIN PRIVATE KEY-----\n${Buffer.from(privateDer).toString("base64")}\n-----END PRIVATE KEY-----`,
  });
  const v1Calls = [];
  globalThis.fetch = async (url, init) => {
    v1Calls.push({ url: String(url), body: String(init?.body || "") });
    if (String(url).includes("oauth2.googleapis.com")) return Response.json({ access_token: "synthetic-access" });
    return Response.json({ name: "synthetic-message" });
  };
  try {
    const v1 = await sendPushToUser(env({ DB: pushDb, FCM_SERVICE_ACCOUNT: serviceAccount }), uid, { type: "message" });
    assert.equal(v1.ok, true);
    assert.equal(v1.sent, 1);
    assert.equal(v1Calls[1].url, "https://fcm.googleapis.com/v1/projects/adischat-f4a77/messages:send");
    assert.equal(JSON.parse(v1Calls[1].body).message.token, "synthetic-device-token");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("P7E internal Shop push uses its existing bridge secret and recovered transport", async () => {
  const config = env({ ADISHOP_PUSH_BRIDGE_SECRET: "synthetic-shop-bridge-secret" });
  const denied = await worker.fetch(new Request(`${baseUrl}/api/internal/shop/push`, { method: "POST" }), config);
  assert.equal(denied.status, 401);
  const allowed = await worker.fetch(new Request(`${baseUrl}/api/internal/shop/push`, {
    method: "POST",
    headers: { "x-adishop-push-secret": "synthetic-shop-bridge-secret", "Content-Type": "application/json" },
    body: JSON.stringify({ receiverUid: uid, type: "seller_confirmation_pending", notificationId: "synthetic-event", context: "seller", storeId: "synthetic-store" }),
  }), config);
  assert.equal(allowed.status, 200);
  assert.equal((await allowed.json()).reason, "no_tokens");
});
