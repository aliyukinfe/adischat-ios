import { allowlistHas } from "../../qa-allowlist-reader/src/client.mjs";

const project = "adischat-qa";
const issuer = "adischat-staging-opkqyvttbyezjbqwkvho";
const audience = "adishop-staging-edge";
const jwksUrl = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const encoder = new TextEncoder();

function reply(status, body) {
  return Response.json(body, { status, headers: { "Cache-Control": "no-store" } });
}

function decode(segment) {
  if (!/^[A-Za-z0-9_-]+$/.test(segment)) throw new Error("invalid-token");
  const raw = atob(segment.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(segment.length / 4) * 4, "="));
  return JSON.parse(new TextDecoder().decode(Uint8Array.from(raw, (char) => char.charCodeAt(0))));
}

function bytes(segment) {
  if (!/^[A-Za-z0-9_-]+$/.test(segment)) throw new Error("invalid-token");
  const raw = atob(segment.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(segment.length / 4) * 4, "="));
  return Uint8Array.from(raw, (char) => char.charCodeAt(0));
}

function encode(value) {
  return btoa(String.fromCharCode(...value)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function configuration(env) {
  if (env.FIREBASE_PROJECT_ID !== project || env.ADISHOP_ASSERTION_ISSUER !== issuer ||
      env.ADISHOP_ASSERTION_AUDIENCE !== audience || !env.QA_RATE_LIMITER?.limit ||
      !env.QA_ALLOWLIST_READER?.fetch || !env.QA_ALLOWLIST_READ_SECRET ||
      encoder.encode(env.QA_ALLOWLIST_READ_SECRET).length < 48 ||
      !env.QA_FIREBASE_WEB_API_KEY || !env.ADISHOP_ASSERTION_SECRET ||
      encoder.encode(env.ADISHOP_ASSERTION_SECRET).length < 48) return null;
  return true;
}

async function bodyAllowed(request) {
  if (Number(request.headers.get("content-length") || 0) > 1024) return false;
  const reader = request.body?.getReader();
  if (!reader) return false;
  let length = 0;
  const chunks = [];
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    length += value.length;
    if (length > 1024) { await reader.cancel(); return false; }
    chunks.push(value);
  }
  const data = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) { data.set(chunk, offset); offset += chunk.length; }
  try {
    const parsed = JSON.parse(new TextDecoder().decode(data));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) &&
      Object.keys(parsed).every((key) => key === "client" && parsed.client === "android");
  } catch { return false; }
}

export function createHandler(fetchImpl = fetch, now = () => Date.now()) {
  let keys = null;
  let keysUntil = 0;
  async function firebaseKeys() {
    if (keys && keysUntil > now()) return keys;
    const response = await fetchImpl(jwksUrl);
    if (!response.ok) throw new Error("key-unavailable");
    const data = await response.json();
    if (!Array.isArray(data.keys)) throw new Error("key-unavailable");
    keys = data.keys;
    const maxAge = Number(response.headers.get("cache-control")?.match(/max-age=(\d+)/)?.[1] || 0);
    keysUntil = now() + Math.min(Math.max(maxAge - 30, 0), 3600) * 1000;
    return keys;
  }

  return async function handle(request, env) {
    const url = new URL(request.url);
    if (url.pathname !== "/api/shop/session-assertion" || url.search) return reply(404, { error: "Not found" });
    if (request.method !== "POST") return reply(405, { error: "Method not allowed" });
    const allowed = configuration(env);
    if (!allowed) return reply(503, { error: "Issuer unavailable" });
    const authorization = request.headers.get("authorization") || "";
    const token = authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
    if (!token || token.length > 8192 || !/^[A-Za-z0-9_.-]+$/.test(token)) return reply(401, { error: "Unauthorized" });
    if (!await bodyAllowed(request)) return reply(400, { error: "Invalid request" });

    try {
      const clientIp = request.headers.get("cf-connecting-ip") || "unknown";
      if (clientIp.length > 45 || !/^[0-9a-fA-F:.]+$/.test(clientIp) && clientIp !== "unknown") {
        return reply(401, { error: "Unauthorized" });
      }
      const preflight = await env.QA_RATE_LIMITER.limit({ key: `pre:${clientIp}` });
      if (preflight?.success !== true) return reply(429, { error: "Too many requests" });
      const parts = token.split(".");
      if (parts.length !== 3) return reply(401, { error: "Unauthorized" });
      let header, claims;
      try { header = decode(parts[0]); claims = decode(parts[1]); }
      catch { return reply(401, { error: "Unauthorized" }); }
      if (!header || typeof header !== "object" || Array.isArray(header) ||
          !claims || typeof claims !== "object" || Array.isArray(claims)) {
        return reply(401, { error: "Unauthorized" });
      }
      const seconds = Math.floor(now() / 1000);
      if (header.alg !== "RS256" || typeof header.kid !== "string" || !header.kid || header.kid.length > 256 ||
          claims.aud !== project || claims.iss !== `https://securetoken.google.com/${project}` ||
          typeof claims.sub !== "string" || !claims.sub || claims.sub.length > 128 ||
          (claims.user_id && claims.user_id !== claims.sub) ||
          !Number.isInteger(claims.iat) || claims.iat > seconds + 30 || claims.iat < seconds - 3700 ||
          !Number.isInteger(claims.auth_time) || claims.auth_time > claims.iat ||
          !Number.isInteger(claims.exp) || claims.exp <= seconds || claims.exp > claims.iat + 3660) {
        return reply(401, { error: "Unauthorized" });
      }
      const jwk = (await firebaseKeys()).find((key) => key.kid === header.kid && key.kty === "RSA" &&
        (!key.alg || key.alg === "RS256") && (!key.use || key.use === "sig"));
      if (!jwk) return reply(401, { error: "Unauthorized" });
      const publicKey = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
      const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, bytes(parts[2]), encoder.encode(`${parts[0]}.${parts[1]}`));
      if (!valid || !await allowlistHas(env, claims.sub, now())) return reply(401, { error: "Unauthorized" });

      const limited = await env.QA_RATE_LIMITER.limit({ key: `uid:${claims.sub}` });
      if (limited?.success !== true) return reply(429, { error: "Too many requests" });
      const lookup = await fetchImpl(`https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${encodeURIComponent(env.QA_FIREBASE_WEB_API_KEY)}`, {
        method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ idToken: token }),
      });
      if (!lookup.ok) return reply(401, { error: "Unauthorized" });
      const data = await lookup.json();
      const user = data.users?.length === 1 ? data.users[0] : null;
      const rawValidSince = user?.validSince;
      const validSince = typeof rawValidSince === "string" && /^[0-9]{1,12}$/.test(rawValidSince) ||
        typeof rawValidSince === "number" && Number.isSafeInteger(rawValidSince) && rawValidSince >= 0
        ? Number(rawValidSince) : null;
      if (!user || user.localId !== claims.sub || user.disabled === true ||
          !Number.isSafeInteger(validSince) || claims.auth_time <= validSince) {
        return reply(401, { error: "Unauthorized" });
      }

      const issuedAt = Math.floor(now() / 1000);
      const expiresAt = issuedAt + 120;
      const assertionHeader = encode(encoder.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
      const assertionPayload = encode(encoder.encode(JSON.stringify({
        iss: issuer, aud: audience, sub: claims.sub, provider: "firebase",
        adischat_user_id: claims.sub, iat: issuedAt, nbf: issuedAt - 5, exp: expiresAt,
        jti: `qa_${crypto.randomUUID()}`,
      })));
      const input = `${assertionHeader}.${assertionPayload}`;
      const signingKey = await crypto.subtle.importKey("raw", encoder.encode(env.ADISHOP_ASSERTION_SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
      const signature = await crypto.subtle.sign("HMAC", signingKey, encoder.encode(input));
      return reply(200, { ok: true, assertion: `${input}.${encode(new Uint8Array(signature))}`, expiresAt: expiresAt * 1000 });
    } catch {
      return reply(503, { error: "Issuer unavailable" });
    }
  };
}

export default { fetch: createHandler() };

