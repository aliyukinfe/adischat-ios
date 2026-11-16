import { createService } from "./index.mjs";
import { createDelivery } from "./delivery-core.mjs";

const service = createService();
const safeResponse = (status, body) => Response.json(body, { status, headers: { "Cache-Control": "no-store" } });
export async function mutateFirebase(env, verifiedService, uid, action, time, fetchImpl = fetch) {
  const config = verifiedService.configuration(env);
  if (!config || env.QA_FIREBASE_PROJECT_ID !== "adischat-qa") throw new Error("qa-firebase-unavailable");
  const token = await verifiedService.adminToken(config.account);
  const change = action === "firebase_disabled" ? { disableUser: true } :
    action === "firebase_tokens_revoked" ? { validSince: String(Math.floor(time / 1000)) } : null;
  if (!change) throw new Error("qa-firebase-invalid-action");
  const result = await fetchImpl("https://identitytoolkit.googleapis.com/v1/projects/adischat-qa/accounts:update", {
    method: "POST", headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
    body: JSON.stringify({ localId: uid, ...change }),
  });
  if (!result.ok) throw new Error("qa-firebase-update-failed");
}
const delivery = createDelivery({ service, mutateFirebase });

export function createWorker(deliveryImpl, serviceImpl) { return {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.search) return safeResponse(404, { error: "Not found" });
    if (url.pathname === "/api/shop/identity/admin-change") return deliveryImpl.produce(request, env);
    if (url.pathname === "/api/shop/identity/reconcile") {
      if (request.method !== "POST") return safeResponse(405, { error: "Method not allowed" });
      if (!serviceImpl.configuration(env)) return safeResponse(503, { error: "Unavailable" });
      try {
        if (!await serviceImpl.verifyAuthority(request, env)) return safeResponse(401, { error: "Unauthorized" });
        return safeResponse(200, { ok: true, ...await deliveryImpl.reconcile(env, 8) });
      } catch { return safeResponse(503, { error: "Reconciliation incomplete" }); }
    }
    return safeResponse(404, { error: "Not found" });
  },
  async queue(batch, env) { await deliveryImpl.consume(batch, env); },
  async scheduled(_event, env) {
    const results = await Promise.allSettled([
      deliveryImpl.resendPending(env, 8), deliveryImpl.reconcile(env, 8),
    ]);
    if (results.some((result) => result.status === "rejected")) throw new Error("qa-maintenance-incomplete");
  },
}; }

export default createWorker(delivery, service);
