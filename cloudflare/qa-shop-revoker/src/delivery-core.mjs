import { allowlistHas, operationStore, validId, validUid } from "./state.mjs";

const enc = new TextEncoder();
const actions = new Set(["firebase_disabled", "firebase_tokens_revoked"]);
const shopUrl = "https://opkqyvttbyezjbqwkvho.supabase.co";

function reply(status, body) {
  return Response.json(body, { status, headers: { "Cache-Control": "no-store" } });
}
async function body(request) {
  if (!request.body || Number(request.headers.get("content-length") || 0) > 1024) throw new Error("bad-body");
  const reader = request.body.getReader();
  const pieces = []; let length = 0;
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    length += value.length;
    if (length > 1024) { await reader.cancel(); throw new Error("bad-body"); }
    pieces.push(value);
  }
  const data = new Uint8Array(length); let at = 0;
  for (const piece of pieces) { data.set(piece, at); at += piece.length; }
  const parsed = JSON.parse(new TextDecoder().decode(data));
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("bad-body");
  return parsed;
}
function eventPayload(event) {
  return JSON.stringify(["qa-revocation-v1", event.eventId, event.uid, event.action,
    event.baselineValidSince, event.appliedValidSince]);
}
async function eventSignature(event, secret) {
  const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, enc.encode(eventPayload(event)));
  return btoa(String.fromCharCode(...new Uint8Array(signature))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
async function signedEvent(op, secret) {
  const event = { eventId: op.id, uid: op.uid, action: op.action,
    baselineValidSince: op.baseline_valid_since, appliedValidSince: op.applied_valid_since };
  return { event, signature: await eventSignature(event, secret) };
}
async function eventValid(message, secret) {
  const { event, signature } = message || {};
  if (!event || Object.keys(event).sort().join() !==
      "action,appliedValidSince,baselineValidSince,eventId,uid" ||
      !validId(event.eventId) || !validUid(event.uid) || !actions.has(event.action) ||
      !Number.isSafeInteger(event.baselineValidSince) ||
      !Number.isSafeInteger(event.appliedValidSince) ||
      typeof signature !== "string" || !/^[A-Za-z0-9_-]{43}$/.test(signature)) return false;
  const expected = await eventSignature(event, secret);
  const a = Uint8Array.from(atob(expected.replace(/-/g, "+").replace(/_/g, "/").padEnd(44, "=")), (c) => c.charCodeAt(0));
  const b = Uint8Array.from(atob(signature.replace(/-/g, "+").replace(/_/g, "/").padEnd(44, "=")), (c) => c.charCodeAt(0));
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
  return difference === 0;
}
async function stableId(parts) {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", enc.encode(JSON.stringify(parts))));
  digest[6] = (digest[6] & 0x0f) | 0x50;
  digest[8] = (digest[8] & 0x3f) | 0x80;
  const hex = [...digest.slice(0, 16)].map((value) => value.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function createDelivery({ service, mutateFirebase, stateFor = operationStore,
  now = () => Date.now(), uuid = () => crypto.randomUUID() }) {
  function configured(env) {
    return env?.QA_FIREBASE_PROJECT_ID === "adischat-qa" && env?.QA_SHOP_URL === shopUrl &&
      service.configuration(env) && env.QA_STATE_DB?.prepare && env.QA_REVOCATION_QUEUE?.send &&
      env.QA_INVALID_QUEUE?.send && typeof env.QA_EVENT_HMAC_SECRET === "string" &&
      enc.encode(env.QA_EVENT_HMAC_SECRET).length >= 48;
  }
  async function queueOperation(op, env, state) {
    if ((op.enqueue_count || 0) >= 5) {
      await state.attention(op.id, "delivery-exhausted", now());
      throw new Error("qa-delivery-exhausted");
    }
    const payload = await signedEvent(op, env.QA_EVENT_HMAC_SECRET);
    await env.QA_REVOCATION_QUEUE.send(payload);
    await state.queued(op.id, now());
  }
  async function produce(request, env) {
    if (request.method !== "POST") return reply(405, { error: "Method not allowed" });
    if (!configured(env)) return reply(503, { error: "QA coordinator unavailable" });
    try {
      if (!await service.verifyAuthority(request, env)) return reply(401, { error: "Unauthorized" });
    } catch { return reply(503, { error: "Authority unavailable" }); }
    try {
      if (!env.QA_ADMIN_RATE_LIMITER?.limit) return reply(503, { error: "Rate limit unavailable" });
      const limited = await env.QA_ADMIN_RATE_LIMITER.limit({ key: "qa-admin-revocation" });
      if (limited?.success !== true) return reply(429, { error: "Too many requests" });
    } catch { return reply(503, { error: "Rate limit unavailable" }); }
    let input;
    try { input = await body(request); }
    catch { return reply(400, { error: "Invalid request" }); }
    const { operationId, uid, action } = input;
    if (Object.keys(input).sort().join() !== "action,operationId,uid" ||
        !validId(operationId) || !validUid(uid) || !actions.has(action)) {
      return reply(400, { error: "Invalid request" });
    }
    try {
      await service.shopProfile(env, uid);
      const config = service.configuration(env);
      const state = stateFor(env);
      const firebase = await service.firebaseUser(uid, config.account);
      const op = await state.begin(operationId, uid, action, firebase.validSince, now());
      if (!op || op.uid !== uid || op.action !== action) return reply(409, { error: "Operation conflict" });
      if (op.state === "confirmed") return reply(200, { ok: true, operationId, shopRevoked: true });
      if (op.state === "queued") return reply(202, { ok: true, operationId, shopRevoked: false });
      if (op.state === "intent" || op.state === "processing") {
        if (op.state === "intent" && action === "firebase_tokens_revoked" &&
            Math.floor(now() / 1000) <= op.baseline_valid_since) {
          return reply(503, { error: "Revocation pending review" });
        }
        const claimed = await state.claim(operationId, now());
        if (!claimed) return reply(202, { ok: true, operationId, shopRevoked: false });
        let applied = firebase;
        if (op.state === "processing") {
          if (action === "firebase_disabled" && !firebase.disabled ||
              action === "firebase_tokens_revoked" && firebase.validSince <= op.baseline_valid_since) {
            await state.attention(operationId, "ambiguous-retry", now());
            return reply(503, { error: "Revocation pending review" });
          }
        } else if (action === "firebase_disabled" && firebase.disabled) {
          // A Console-side disable is already authoritative; still revoke Shop.
        } else {
          await mutateFirebase(env, service, uid, action, now());
          applied = await service.firebaseUser(uid, config.account);
        }
        if (action === "firebase_disabled" && !applied.disabled ||
            action === "firebase_tokens_revoked" && applied.validSince <= op.baseline_valid_since) {
          await state.attention(operationId, "firebase-unconfirmed", now());
          return reply(503, { error: "Revocation pending review" });
        }
        await state.applied(operationId, applied.validSince, now());
      }
      const ready = await state.get(operationId);
      if (ready?.state !== "firebase_done") throw new Error("qa-state-conflict");
      await queueOperation(ready, env, state);
      return reply(202, { ok: true, operationId, shopRevoked: false });
    } catch { return reply(503, { error: "Revocation pending" }); }
  }
  async function consumeOne(message, env, state) {
    if (!await eventValid(message, env.QA_EVENT_HMAC_SECRET)) return "invalid";
    const { event } = message;
    const op = await state.get(event.eventId);
    if (!op || op.uid !== event.uid || op.action !== event.action ||
        op.baseline_valid_since !== event.baselineValidSince ||
        op.applied_valid_since !== event.appliedValidSince ||
        !["firebase_done", "queued", "confirmed"].includes(op.state)) return "invalid";
    if (op.state === "confirmed") return "confirmed";
    const current = await service.firebaseUser(event.uid, service.configuration(env).account);
    if (event.action === "firebase_disabled" && !current.disabled ||
        event.action === "firebase_tokens_revoked" && current.validSince < event.appliedValidSince) {
      await state.attention(event.eventId, "firebase-state-conflict", now());
      return "retry";
    }
    await service.shopProfile(env, event.uid);
    const result = await service.revoke(env, event.eventId, event.uid, event.action);
    if (result?.ok !== true || result.eventId !== event.eventId) return "retry";
    await state.confirmed(event.eventId, now());
    return "confirmed";
  }
  async function consume(batch, env) {
    if (!configured(env)) { for (const message of batch.messages) message.retry(); return; }
    const state = stateFor(env);
    for (const message of batch.messages) {
      try {
        const result = await consumeOne(message.body, env, state);
        if (result === "confirmed") message.ack();
        else if (result === "invalid") {
          await env.QA_INVALID_QUEUE.send({ eventId: validId(message.body?.event?.eventId) ? message.body.event.eventId : null,
            category: "invalid-revocation-event" });
          message.ack();
        } else message.retry();
      } catch { message.retry(); }
    }
  }
  async function reconcile(env, limit = 20) {
    if (!configured(env) || !Number.isInteger(limit) || limit < 1 || limit > 20) throw new Error("qa-reconcile-unavailable");
    const state = stateFor(env);
    const owner = uuid();
    if (!await state.claimScan(owner, now())) throw new Error("qa-reconcile-busy");
    try {
      const cursor = (await state.cursor())?.last_link_id || null;
      const filter = cursor ? `&id=gt.${encodeURIComponent(cursor)}` : "";
      const rows = await service.shopRequest(env, `/rest/v1/shop_user_links?provider=eq.firebase${filter}` +
        `&select=id,provider_user_id,shop_profile_id,account_status&order=id.asc&limit=${limit}`);
      if (!Array.isArray(rows) || rows.length > limit) throw new Error("qa-reconcile-incomplete");
      let previous = cursor; let revoked = 0; let ambiguous = 0;
      for (const row of rows) {
        if (!validId(row.id) || !validUid(row.provider_user_id) || !validId(row.shop_profile_id) ||
            previous && row.id <= previous) throw new Error("qa-reconcile-unstable-page");
        const uid = row.provider_user_id;
        const profiles = await service.shopRequest(env, `/rest/v1/shop_profiles?id=eq.${row.shop_profile_id}&select=status&limit=1`);
        if (!Array.isArray(profiles) || profiles.length !== 1) throw new Error("qa-reconcile-profile-missing");
        const oldest = await service.oldestActiveFamily(env, row.shop_profile_id);
        const user = await service.firebaseUser(uid, service.configuration(env).account);
        const listed = await allowlistHas(env, uid);
        if ((row.account_status !== "active" || profiles[0].status !== "active") && oldest !== null) {
          const result = await service.shopRequest(env, "/rest/v1/rpc/adishop_qa_revoke_inactive_sessions", {
            method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ p_verified_uid: uid }),
          });
          if (result?.ok !== true) throw new Error("qa-reconcile-inactive-failed");
          revoked++;
        } else {
          const cause = user.disabled ? "firebase_disabled" : !listed ? "allowlist_removed" :
            oldest !== null && user.validSince >= Math.floor(oldest) ? "firebase_tokens_revoked" : null;
          if (cause) {
            if (cause === "firebase_tokens_revoked" && user.validSince === Math.floor(oldest)) ambiguous++;
            const id = await stableId(["qa-reconcile-v1", uid, cause, user.validSince,
              cause === "firebase_tokens_revoked" ? oldest : null]);
            const result = await service.revoke(env, id, uid, cause);
            if (result?.ok !== true) throw new Error("qa-reconcile-revoke-failed");
            if (result.changed) revoked++;
          }
        }
        previous = row.id;
      }
      const next = rows.length === limit ? previous : null;
      await state.saveCursor(next, owner, now(), { scanned: rows.length, revoked, ambiguous });
      return { scanned: rows.length, revoked, ambiguous, nextCursor: next, complete: next === null };
    } catch (error) {
      const category = String(error?.message || "");
      await state.recordFailure(category.startsWith("qa-reconcile-") ? category : "dependency-unavailable", owner, now());
      throw error;
    } finally { await state.releaseScan(owner); }
  }
  async function resendPending(env, limit = 20) {
    if (!configured(env)) throw new Error("qa-outbox-unavailable");
    const state = stateFor(env);
    const pending = await state.pending(limit, now() - 60_000);
    if (!Array.isArray(pending) || pending.length > limit) throw new Error("qa-outbox-unavailable");
    for (const op of pending) await queueOperation(op, env, state);
    return pending.length;
  }
  return { produce, consume, reconcile, resendPending, signedEvent, eventValid };
}
