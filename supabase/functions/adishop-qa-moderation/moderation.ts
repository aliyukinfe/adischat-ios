// Staging-only moderation transport. SQL remains the final authorization gate.
export type AuthenticatedActor = { profileId: string; jti: string };
export type RpcResult = { data: unknown; error: { code?: string; message?: string } | null };
export type ModerationDeps = {
  authenticate(request: Request): Promise<AuthenticatedActor>;
  isAdmin(actor: AuthenticatedActor): Promise<boolean>;
  rpc(name: string, args: Record<string, unknown>): Promise<RpcResult>;
  now(): number;
};

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const timestamp = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})$/;
const MAX_BODY_BYTES = 8192;
const WINDOW_MS = 60_000;
const MAX_PER_SESSION = 10;

function reply(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), { status, headers: {
    "content-type": "application/json", "cache-control": "private, no-store",
    "x-content-type-options": "nosniff",
  } });
}
function failure(status: number, code: string): Response {
  return reply(status, { ok: false, error: code });
}
async function boundedJson(request: Request): Promise<Record<string, unknown> | null> {
  if (Number(request.headers.get("content-length") || 0) > MAX_BODY_BYTES || !request.body) return null;
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let length = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      length += value.length;
      if (length > MAX_BODY_BYTES) { await reader.cancel(); return null; }
      chunks.push(value);
    }
    const body = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(
      Uint8Array.from(chunks.flatMap((chunk) => Array.from(chunk))),
    ));
    return body && typeof body === "object" && !Array.isArray(body) ? body : null;
  } catch { return null; }
}
function validText(value: unknown, min: number, max: number): boolean {
  return typeof value === "string" && value.trim().length >= min && value.length <= max;
}

export function createModerationHandler(deps: ModerationDeps) {
  const requests = new Map<string, number[]>();
  const seen = new Map<string, number>();
  let globalWindow = 0;
  let globalCount = 0;
  return async (request: Request): Promise<Response> => {
    if (request.method !== "POST" && request.method !== "GET") return failure(405, "method-not-allowed");
    const now = deps.now();
    if (now - globalWindow >= WINDOW_MS) { globalWindow = now; globalCount = 0; }
    if (++globalCount > 100) return failure(429, "rate-limited");
    if (request.method === "GET") {
      const url = new URL(request.url);
      const action = url.searchParams.get("action");
      const kind = url.searchParams.get("kind");
      if (action !== "queue" && action !== "preview" ||
          [...url.searchParams.keys()].some((key) => !["action", "kind", "id", "limit", "offset"].includes(key)) ||
          (kind !== null && !["store", "product", "image"].includes(kind))) return failure(400, "invalid-request");
      const limit = Number(url.searchParams.get("limit") ?? "20");
      const offset = Number(url.searchParams.get("offset") ?? "0");
      const id = url.searchParams.get("id");
      if (action === "queue" && (id !== null || !Number.isSafeInteger(limit) || limit < 1 || limit > 50 ||
          !Number.isSafeInteger(offset) || offset < 0) ||
          action === "preview" && (!kind || !id || !uuid.test(id) ||
          url.searchParams.has("limit") || url.searchParams.has("offset"))) return failure(400, "invalid-request");
      let actor: AuthenticatedActor;
      try { actor = await deps.authenticate(request); }
      catch { return failure(401, "invalid-session"); }
      if (!uuid.test(actor.profileId) || !actor.jti) return failure(401, "invalid-session");
      try { if (!await deps.isAdmin(actor)) return failure(403, "forbidden"); }
      catch { return failure(503, "unavailable"); }
      const recent = (requests.get(actor.jti) || []).filter((time) => now - time < WINDOW_MS);
      if (recent.length >= MAX_PER_SESSION) return failure(429, "rate-limited");
      recent.push(now); requests.set(actor.jti, recent);
      try {
        const result = action === "queue"
          ? await deps.rpc("adishop_qa_moderation_queue", { p_session_jti: actor.jti,
            p_kind: kind, p_limit: limit, p_offset: offset })
          : await deps.rpc("adishop_qa_moderation_preview", { p_session_jti: actor.jti,
            p_kind: kind, p_id: id });
        if (result.error) return failure(result.error.code === "28000" ? 401 :
          result.error.code === "42501" ? 403 : result.error.code === "22023" ? 404 : 503,
          result.error.code === "28000" ? "invalid-session" :
          result.error.code === "42501" ? "forbidden" :
          result.error.code === "22023" ? "not-found" : "unavailable");
        return reply(200, { ok: true, [action === "queue" ? "reviewQueue" : "reviewPreview"]: result.data });
      } catch { return failure(503, "unavailable"); }
    }
    const requestId = request.headers.get("idempotency-key") || "";
    if (!uuid.test(requestId)) return failure(400, "invalid-request");
    const body = await boundedJson(request);
    if (!body) return failure(400, "invalid-request");
    const { kind, id, decision, expectedRevision } = body;
    if (!["store", "product", "image"].includes(String(kind)) || !uuid.test(String(id)) ||
        !["approve", "reject", "revoke"].includes(String(decision)) ||
        (decision === "revoke" && kind !== "store") ||
        (kind === "image"
          ? !Number.isSafeInteger(expectedRevision) || Number(expectedRevision) < 1
          : typeof expectedRevision !== "string" || !timestamp.test(expectedRevision) ||
            !Number.isFinite(Date.parse(expectedRevision)))) return failure(400, "invalid-request");
    const allowed = kind === "store"
      ? ["kind", "id", "decision", "expectedRevision", "publicName", "publicDescription", "publicCity"]
      : kind === "product"
      ? ["kind", "id", "decision", "expectedRevision", "publicTitle", "publicShortDescription"]
      : ["kind", "id", "decision", "expectedRevision", "altText"];
    if (Object.keys(body).some((key) => !allowed.includes(key))) return failure(400, "invalid-request");
    if (decision === "approve" && (kind === "store"
      ? !validText(body.publicName, 2, 160) || !validText(body.publicCity, 1, 120) ||
        (body.publicDescription != null && !validText(body.publicDescription, 0, 500))
      : kind === "product"
      ? !validText(body.publicTitle, 2, 160) ||
        (body.publicShortDescription != null && !validText(body.publicShortDescription, 0, 1000))
      : body.altText != null && !validText(body.altText, 0, 240))) return failure(400, "invalid-request");

    let actor: AuthenticatedActor;
    try { actor = await deps.authenticate(request); }
    catch { return failure(401, "invalid-session"); }
    if (!uuid.test(actor.profileId) || !actor.jti) return failure(401, "invalid-session");
    try { if (!await deps.isAdmin(actor)) return failure(403, "forbidden"); }
    catch { return failure(503, "unavailable"); }
    if (kind === "image" && decision === "approve") return failure(403, "visual-preview-required");
    const recent = (requests.get(actor.jti) || []).filter((time) => now - time < WINDOW_MS);
    if (recent.length >= MAX_PER_SESSION) return failure(429, "rate-limited");
    recent.push(now); requests.set(actor.jti, recent);
    for (const [key, expiry] of seen) if (expiry <= now) seen.delete(key);
    if (seen.size > 1000) return failure(503, "unavailable");
    const dedupeKey = `${actor.jti}:${requestId}`;
    if (seen.has(dedupeKey)) return failure(409, "duplicate-request");
    seen.set(dedupeKey, now + 600_000);

    const name = kind === "store" ? "adishop_qa_review_store" :
      kind === "product" ? "adishop_qa_review_product" : "adishop_qa_review_image";
    const args: Record<string, unknown> = kind === "store"
      ? { p_session_jti: actor.jti, p_store_id: id, p_expected_updated_at: expectedRevision,
        p_decision: decision, p_public_name: body.publicName ?? null,
        p_public_description: body.publicDescription ?? null, p_public_city: body.publicCity ?? null }
      : kind === "product"
      ? { p_session_jti: actor.jti, p_product_id: id, p_expected_updated_at: expectedRevision,
        p_decision: decision, p_public_title: body.publicTitle ?? null,
        p_public_short_description: body.publicShortDescription ?? null }
      : { p_session_jti: actor.jti, p_image_id: id, p_expected_revision: expectedRevision,
        p_decision: decision, p_alt_text: body.altText ?? null };
    try {
      const result = await deps.rpc(name, args);
      if (result.error) {
        seen.delete(dedupeKey);
        const status = result.error.code === "28000" ? 401 : result.error.code === "42501" ? 403 :
          result.error.code === "40001" ? 409 : result.error.code === "22023" ? 400 : 503;
        return failure(status, status === 409 ?
          result.error.message === "duplicate-review" ? "duplicate-request" : "stale-revision" :
          status === 403 ? "forbidden" :
          status === 401 ? "invalid-session" : status === 400 ? "invalid-request" : "unavailable");
      }
      return reply(200, { ok: true, review: result.data });
    } catch {
      seen.delete(dedupeKey);
      return failure(503, "unavailable");
    }
  };
}
