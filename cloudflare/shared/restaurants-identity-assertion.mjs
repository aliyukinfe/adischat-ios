import { requireFirebaseUser } from "./firebase-auth.mjs";

const DEFAULT_PROJECT_ID = "adischat-f4a77";
const DEFAULT_ISSUER = "adischat-api-prod";
const DEFAULT_AUDIENCE = "adischat-restaurants:npmniymdxabjvagierwq";
const ASSERTION_TTL_SECONDS = 120;
const MIN_SECRET_LENGTH = 32;

const ALLOWED_ORIGINS = new Set([
  "https://restaurant.adischat.com",
  "https://adischat.com",
  "https://www.adischat.com",
]);

const encoder = new TextEncoder();

function base64Url(bytes) {
  const raw = btoa(String.fromCharCode(...new Uint8Array(bytes)));
  return raw.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function json(body, status = 200, extraHeaders = {}) {
  return Response.json(body, {
    status,
    headers: {
      "Cache-Control": "no-store",
      Pragma: "no-cache",
      ...extraHeaders,
    },
  });
}

function failure(code, message, status = 400) {
  return json({ ok: false, error: { code, message } }, status);
}

export function withRestaurantCors(response, origin) {
  const headers = new Headers(response.headers);
  headers.set("Vary", "Origin");
  if (origin && ALLOWED_ORIGINS.has(origin)) {
    headers.set("Access-Control-Allow-Origin", origin);
    headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
    headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
    headers.set("Access-Control-Max-Age", "600");
  }
  return new Response(response.body, { status: response.status, headers });
}

function nowSeconds() {
  return Math.floor(Date.now() / 1000);
}

async function signAssertion(payload, secret) {
  const header = { alg: "HS256", typ: "JWT" };
  const encodedHeader = base64Url(encoder.encode(JSON.stringify(header)));
  const encodedPayload = base64Url(encoder.encode(JSON.stringify(payload)));
  const signingInput = `${encodedHeader}.${encodedPayload}`;
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(signingInput));
  return `${signingInput}.${base64Url(signature)}`;
}

/**
 * Verifies live account status against Google Identity Toolkit if service account is configured.
 */
export async function verifyLiveFirebaseAccount(idToken, claims, serviceAccountJson, options = {}) {
  try {
    const serviceAccount = JSON.parse(serviceAccountJson || "");
    const projectId = serviceAccount.project_id || DEFAULT_PROJECT_ID;
    if (
      !serviceAccount.client_email ||
      !serviceAccount.private_key ||
      !claims?.sub ||
      !Number.isSafeInteger(claims.auth_time) ||
      claims.auth_time <= 0
    ) {
      return { ok: false, reason: "unavailable" };
    }
    const nowSec = options.nowSec ?? nowSeconds();
    const fetchImpl = options.fetchImpl ?? fetch;

    // Mint Google OAuth token
    const privateKeyDer = Uint8Array.from(
      atob(serviceAccount.private_key.replace(/-----[^-]+-----|\s+/g, "")),
      (char) => char.charCodeAt(0),
    );
    const privateKey = await crypto.subtle.importKey(
      "pkcs8",
      privateKeyDer,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["sign"],
    );
    const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
    const oauthClaims = base64Url(
      JSON.stringify({
        iss: serviceAccount.client_email,
        scope: "https://www.googleapis.com/auth/identitytoolkit",
        aud: "https://oauth2.googleapis.com/token",
        iat: nowSec,
        exp: nowSec + 300,
      }),
    );
    const signingInput = `${header}.${oauthClaims}`;
    const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", privateKey, encoder.encode(signingInput));
    const tokenRes = await fetchImpl("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion: `${signingInput}.${base64Url(new Uint8Array(sig))}`,
      }),
    });
    if (!tokenRes.ok) return { ok: false, reason: "unavailable" };
    const tokenBody = await tokenRes.json();
    const accessToken = tokenBody?.access_token;
    if (!accessToken) return { ok: false, reason: "unavailable" };

    const lookupRes = await fetchImpl(
      `https://identitytoolkit.googleapis.com/v1/projects/${projectId}/accounts:lookup`,
      {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
        body: JSON.stringify({ idToken }),
      },
    );
    if (!lookupRes.ok) return { ok: false, reason: "unavailable" };
    const lookupBody = await lookupRes.json();
    const user = Array.isArray(lookupBody?.users)
      ? lookupBody.users.find((entry) => entry?.localId === claims.sub)
      : null;
    if (!user) return { ok: false, reason: "unavailable" };
    if (user.disabled === true) return { ok: false, reason: "disabled" };
    const validSince = Number(user.validSince);
    if (Number.isSafeInteger(validSince) && validSince > 0 && claims.auth_time <= validSince) {
      return { ok: false, reason: "revoked" };
    }
    return { ok: true };
  } catch {
    return { ok: false, reason: "unavailable" };
  }
}

async function issueAssertion(request, env, options = {}) {
  const secret = (env.RESTAURANTS_ASSERTION_SECRET ?? "").trim();
  if (secret.length < MIN_SECRET_LENGTH) {
    return failure("bridge_not_configured", "Restaurants identity bridge is not configured.", 503);
  }

  // Cross-service isolation: refuse desktop QR tokens
  const authHeader = request.headers.get("Authorization") || "";
  const rawBearer = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
  if (rawBearer.startsWith("adis_desktop_")) {
    return failure("cross_service_auth_forbidden", "Desktop QR tokens cannot access Restaurants bridge.", 403);
  }

  // 1. Authenticate Firebase user
  let authResult;
  if (options.user || options.uid) {
    const u = options.user || options;
    authResult = { user: u, userRecord: u };
  } else {
    try {
      authResult = await (options.verifyFirebaseUser ?? requireFirebaseUser)(request, env);
    } catch {
      return failure("firebase_verification_unavailable", "Unable to verify Firebase token.", 503);
    }
  }

  if (authResult?.error) {
    return authResult.error;
  }

  const user = authResult?.user;
  const claims = authResult?.userRecord?.claims ?? user?.claims ?? {};
  const uid = typeof user?.uid === "string" ? user.uid.trim() : "";
  if (!uid || uid.length > 128) {
    return failure("invalid_firebase_token", "Authenticated Firebase identity required.", 401);
  }

  // 2. Reject anonymous accounts
  const signInProvider = user?.signInProvider || claims?.firebase?.sign_in_provider;
  if (signInProvider === "anonymous") {
    return failure("forbidden_anonymous_account", "Anonymous accounts cannot access Restaurants.", 403);
  }

  // 3. Reject suspended / banned accounts via user.role or D1
  const directRole = String(user?.role || "").toLowerCase();
  if (["banned", "suspended", "revoked", "disabled"].includes(directRole)) {
    return failure("account_inactive", "Account is suspended or inactive.", 403);
  }

  if (env.DB) {
    try {
      const dbUser = await env.DB.prepare("SELECT role FROM users WHERE uid = ?").bind(uid).first();
      const role = String(dbUser?.role || "").toLowerCase();
      if (["banned", "suspended", "revoked", "disabled"].includes(role)) {
        return failure("account_inactive", "Account is suspended or inactive.", 403);
      }
    } catch {
      return failure("account_status_unavailable", "Unable to verify account status in database.", 503);
    }
  }

  // 4. Rate limiting check (if binding present)
  if (env.RESTAURANTS_BRIDGE_RATE_LIMITER?.limit) {
    try {
      const outcome = await env.RESTAURANTS_BRIDGE_RATE_LIMITER.limit({ key: `restaurants:uid:${uid}` });
      if (!outcome || outcome.success !== true) {
        return failure("rate_limit_exceeded", "Too many session assertion requests.", 429);
      }
    } catch {
      return failure("rate_limiter_unavailable", "Rate limiter unavailable.", 503);
    }
  }

  // 5. Request body validation
  let body = {};
  const rawBody = await request.text();
  if (rawBody.trim() !== "") {
    try {
      body = JSON.parse(rawBody);
    } catch {
      return failure("invalid_request_body", "Valid JSON request body is required.", 400);
    }
    if (!body || typeof body !== "object" || Array.isArray(body)) {
      return failure("invalid_request_body", "Request body must be a JSON object.", 400);
    }
  }

  if (body.project_scope !== undefined && body.project_scope !== "restaurants") {
    return failure("invalid_project_scope", "Assertion endpoint is scoped strictly to AdisChat Restaurants.", 403);
  }

  for (const candidateKey of ["uid", "sub", "user_id", "adischat_user_id"]) {
    if (body[candidateKey] !== undefined && String(body[candidateKey]).trim() !== uid) {
      return failure("identity_substitution_forbidden", "Cannot substitute another user identity.", 403);
    }
  }

  // 6. Optional live revocation check if configured
  const revocationEnabled = (env.FIREBASE_REVOCATION_ENABLED ?? "").trim().toLowerCase() === "true";
  if ((options.checkFirebaseRevocation || revocationEnabled) && env.RESTAURANTS_FIREBASE_AUTH_SERVICE_ACCOUNT) {
    const live = await (options.verifyLiveAccount ?? verifyLiveFirebaseAccount)(
      rawBearer,
      claims,
      env.RESTAURANTS_FIREBASE_AUTH_SERVICE_ACCOUNT,
      options,
    );
    if (!live?.ok) {
      if (live?.reason === "disabled") return failure("firebase_user_disabled", "Firebase account is disabled.", 403);
      if (live?.reason === "revoked") return failure("firebase_token_revoked", "Firebase session has been revoked.", 401);
      return failure("firebase_verification_unavailable", "Live revocation check failed.", 503);
    }
  }

  // 7. Mint assertion payload
  const nowSec = options.nowSec ?? nowSeconds();
  const issuer = (env.RESTAURANTS_ASSERTION_ISSUER ?? "").trim() || DEFAULT_ISSUER;
  const audience = (env.RESTAURANTS_ASSERTION_AUDIENCE ?? "").trim() || DEFAULT_AUDIENCE;
  const exp = nowSec + ASSERTION_TTL_SECONDS;
  const jti = (options.generateJti ?? (() => crypto.randomUUID()))();

  let firebaseAuthTime = typeof user?.authTime === "number" && Number.isSafeInteger(user.authTime) && user.authTime > 0
    ? user.authTime
    : typeof claims?.auth_time === "number" && Number.isSafeInteger(claims.auth_time) && claims.auth_time > 0
    ? claims.auth_time
    : nowSec;

  if (firebaseAuthTime > nowSec + 30) {
    firebaseAuthTime = nowSec;
  }

  const displayName = (user?.name ?? "").trim().slice(0, 80);
  const email = (user?.email ?? "").trim();
  const photoUrl = (user?.photoUrl ?? "").trim();
  const avatarUrl = photoUrl.startsWith("https://") ? photoUrl : "";
  const phone = (user?.phoneNumber ?? "").trim();
  const phoneVerified = Boolean(phone);

  const payload = {
    iss: issuer,
    aud: audience,
    sub: uid,
    jti,
    iat: nowSec,
    nbf: nowSec,
    exp,
    provider: "firebase",
    project_scope: "restaurants",
    firebase_auth_time: firebaseAuthTime,
    adischat_user_id: uid,
    phone_verified: phoneVerified,
    ...(displayName ? { display_name: displayName } : {}),
    ...(avatarUrl ? { avatar_url: avatarUrl } : {}),
    ...(phone ? { phone } : {}),
    ...(email ? { email } : {}),
  };

  const assertion = await signAssertion(payload, secret);
  return json({
    ok: true,
    assertion,
    expires_at: exp * 1000,
  });
}

/**
 * Handles POST /api/restaurants/session-assertion
 */
export async function handleRestaurantsIdentityRequest(request, env, options = {}) {
  const url = new URL(request.url);
  if (url.pathname !== "/api/restaurants/session-assertion") {
    return null;
  }

  const origin = request.headers.get("Origin");
  if (origin && !ALLOWED_ORIGINS.has(origin)) {
    return withRestaurantCors(failure("origin_not_allowed", "Origin not allowed.", 403), null);
  }

  if (request.method === "OPTIONS") {
    return withRestaurantCors(new Response(null, { status: 204 }), origin);
  }

  if (request.method !== "POST") {
    return withRestaurantCors(failure("method_not_allowed", "Method not allowed.", 405), origin);
  }

  try {
    const res = await issueAssertion(request, env, options);
    return withRestaurantCors(res, origin);
  } catch (err) {
    const msg = err instanceof Error ? err.message : "Internal error";
    return withRestaurantCors(failure("bridge_error", msg, 500), origin);
  }
}
