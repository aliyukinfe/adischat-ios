import assert from "node:assert/strict";
import test from "node:test";
import { handleRestaurantsIdentityRequest } from "./restaurants-identity-assertion.mjs";

const TEST_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"; // 64 chars

function base64UrlToBytes(base64url) {
  const normalized = base64url.replace(/-/g, "+").replace(/_/g, "/");
  const pad = normalized.length % 4 === 0 ? "" : "=".repeat(4 - (normalized.length % 4));
  const binary = atob(normalized + pad);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function decodeJwt(jwt) {
  const parts = jwt.split(".");
  assert.equal(parts.length, 3, "JWT must have 3 parts");
  const decoder = new TextDecoder();
  const header = JSON.parse(decoder.decode(base64UrlToBytes(parts[0])));
  const payload = JSON.parse(decoder.decode(base64UrlToBytes(parts[1])));
  return { header, payload, signature: parts[2] };
}

async function verifyHmacSignature(jwt, secret) {
  const parts = jwt.split(".");
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["verify"],
  );
  return crypto.subtle.verify(
    "HMAC",
    key,
    base64UrlToBytes(parts[2]),
    encoder.encode(`${parts[0]}.${parts[1]}`),
  );
}

test("1. handleRestaurantsIdentityRequest ignores unrelated routes", async () => {
  const req = new Request("https://adischat-api.test/api/health", { method: "GET" });
  const res = await handleRestaurantsIdentityRequest(req, {}, { uid: "test-user" });
  assert.equal(res, null);
});

test("2. Rejects non-POST HTTP methods with 405", async () => {
  const req = new Request("https://adischat-api.test/api/restaurants/session-assertion", {
    method: "GET",
  });
  const res = await handleRestaurantsIdentityRequest(req, { RESTAURANTS_ASSERTION_SECRET: TEST_SECRET }, { uid: "test-user" });
  assert.equal(res.status, 405);
  const data = await res.json();
  assert.equal(data.error.code, "method_not_allowed");
});

test("3. Fails closed with 503 if RESTAURANTS_ASSERTION_SECRET is missing or too short", async () => {
  const req = new Request("https://adischat-api.test/api/restaurants/session-assertion", {
    method: "POST",
  });
  const resNoSecret = await handleRestaurantsIdentityRequest(req, {}, { uid: "test-user" });
  assert.equal(resNoSecret.status, 503);
  const dataNoSecret = await resNoSecret.json();
  assert.equal(dataNoSecret.error.code, "bridge_not_configured");

  const resShortSecret = await handleRestaurantsIdentityRequest(req, { RESTAURANTS_ASSERTION_SECRET: "too_short_secret" }, { uid: "test-user" });
  assert.equal(resShortSecret.status, 503);
  const dataShort = await resShortSecret.json();
  assert.equal(dataShort.error.code, "bridge_not_configured");
});

test("4. Rejects anonymous accounts with 403", async () => {
  const req = new Request("https://adischat-api.test/api/restaurants/session-assertion", {
    method: "POST",
  });
  const resAnon = await handleRestaurantsIdentityRequest(
    req,
    { RESTAURANTS_ASSERTION_SECRET: TEST_SECRET },
    { uid: "anon-123", signInProvider: "anonymous" },
  );
  assert.equal(resAnon.status, 403);
  const data = await resAnon.json();
  assert.equal(data.error.code, "forbidden_anonymous_account");
});

test("5. Rejects banned or suspended accounts with 403", async () => {
  const req = new Request("https://adischat-api.test/api/restaurants/session-assertion", {
    method: "POST",
  });
  for (const bannedRole of ["banned", "suspended", "revoked", "disabled"]) {
    const res = await handleRestaurantsIdentityRequest(
      req,
      { RESTAURANTS_ASSERTION_SECRET: TEST_SECRET },
      { uid: "suspended-user", role: bannedRole },
    );
    assert.equal(res.status, 403, `Role ${bannedRole} must be rejected`);
    const data = await res.json();
    assert.equal(data.error.code, "account_inactive");
  }
});

test("6. Rejects foreign project_scope with 403", async () => {
  const req = new Request("https://adischat-api.test/api/restaurants/session-assertion", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ project_scope: "shop" }),
  });
  const res = await handleRestaurantsIdentityRequest(
    req,
    { RESTAURANTS_ASSERTION_SECRET: TEST_SECRET },
    { uid: "test-user" },
  );
  assert.equal(res.status, 403);
  const data = await res.json();
  assert.equal(data.error.code, "invalid_project_scope");
});

test("7. Rejects identity substitution attempts with 403", async () => {
  const req = new Request("https://adischat-api.test/api/restaurants/session-assertion", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ uid: "other-user-uid" }),
  });
  const res = await handleRestaurantsIdentityRequest(
    req,
    { RESTAURANTS_ASSERTION_SECRET: TEST_SECRET },
    { uid: "real-user-uid" },
  );
  assert.equal(res.status, 403);
  const data = await res.json();
  assert.equal(data.error.code, "identity_substitution_forbidden");
});

test("8. Generates valid assertion matching production Restaurants Supabase contract", async () => {
  const now = Math.floor(Date.now() / 1000);
  const testUser = {
    uid: "eZA5erSpzpgW43EuKhi6FdVEUGv2",
    email: "aliyu98@gmail.com",
    name: "Aliyu Admin",
    photoUrl: "https://example.com/photo.jpg",
    authTime: now - 10,
    role: "user",
    claims: { auth_time: now - 10 },
  };

  const req = new Request("https://adischat-api.test/api/restaurants/session-assertion", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ project_scope: "restaurants" }),
  });

  const env = {
    RESTAURANTS_ASSERTION_SECRET: TEST_SECRET,
    RESTAURANTS_ASSERTION_ISSUER: "adischat-api-prod",
    RESTAURANTS_ASSERTION_AUDIENCE: "adischat-restaurants:npmniymdxabjvagierwq",
  };

  const res = await handleRestaurantsIdentityRequest(req, env, testUser);
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.ok, true);
  assert.ok(typeof data.assertion === "string");
  assert.ok(data.expires_at > Date.now());

  // Decode and verify assertion claims
  const { header, payload } = decodeJwt(data.assertion);
  assert.equal(header.alg, "HS256");
  assert.equal(header.typ, "JWT");
  assert.equal(payload.iss, "adischat-api-prod");
  assert.equal(payload.aud, "adischat-restaurants:npmniymdxabjvagierwq");
  assert.equal(payload.sub, "eZA5erSpzpgW43EuKhi6FdVEUGv2");
  assert.equal(payload.provider, "firebase");
  assert.equal(payload.project_scope, "restaurants");
  assert.equal(payload.adischat_user_id, "eZA5erSpzpgW43EuKhi6FdVEUGv2");
  assert.equal(payload.display_name, "Aliyu Admin");
  assert.equal(payload.email, "aliyu98@gmail.com");
  assert.equal(payload.avatar_url, "https://example.com/photo.jpg");
  assert.equal(payload.firebase_auth_time, now - 10);
  assert.ok(payload.jti && payload.jti.length >= 8);
  assert.equal(payload.exp - payload.iat, 120, "TTL must be exactly 120 seconds");
  assert.ok(payload.exp > now);
  assert.ok(payload.iat <= now);

  // Verify HMAC-SHA256 signature
  const signatureValid = await verifyHmacSignature(data.assertion, TEST_SECRET);
  assert.equal(signatureValid, true, "Signature must verify with TEST_SECRET");
});
