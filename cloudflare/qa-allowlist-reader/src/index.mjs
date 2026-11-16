import { allowlistHas, validUid } from "../../qa-shop-revoker/src/state.mjs";

const encoder = new TextEncoder();
const deny = (status) => Response.json({ error: status === 503 ? "Unavailable" : "Unauthorized" },
  { status, headers: { "Cache-Control": "no-store" } });

export function createHandler(now = () => Date.now()) {
  return async (request, env) => {
    if (new URL(request.url).pathname !== "/contains" || new URL(request.url).search) return deny(404);
    if (request.method !== "POST") return deny(405);
    if (!env?.QA_STATE_DB?.prepare || typeof env.QA_ALLOWLIST_READ_SECRET !== "string" ||
        encoder.encode(env.QA_ALLOWLIST_READ_SECRET).length < 48) return deny(503);
    const length = Number(request.headers.get("content-length") || 0);
    if (!Number.isSafeInteger(length) || length > 256) return deny(401);
    try {
      const raw = await request.text();
      if (encoder.encode(raw).length > 256) return deny(401);
      const body = JSON.parse(raw);
      if (!body || typeof body !== "object" || Array.isArray(body) ||
          Object.keys(body).length !== 1 || !validUid(body.uid)) return deny(401);
      const timestamp = request.headers.get("x-qa-time") || "";
      const signature = request.headers.get("x-qa-signature") || "";
      if (!/^[0-9]{10}$/.test(timestamp) || !/^[0-9a-f]{64}$/.test(signature) ||
          Math.abs(Math.floor(now() / 1000) - Number(timestamp)) > 30) return deny(401);
      const key = await crypto.subtle.importKey("raw", encoder.encode(env.QA_ALLOWLIST_READ_SECRET),
        { name: "HMAC", hash: "SHA-256" }, false, ["verify"]);
      const bytes = Uint8Array.from(signature.match(/../g), (part) => parseInt(part, 16));
      if (!await crypto.subtle.verify("HMAC", key, bytes, encoder.encode(`${timestamp}\n${body.uid}`))) return deny(401);
      return Response.json({ allowed: await allowlistHas(env, body.uid) },
        { headers: { "Cache-Control": "no-store" } });
    } catch { return deny(503); }
  };
}

export default { fetch: createHandler() };
