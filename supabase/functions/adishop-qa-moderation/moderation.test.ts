// Transport mocks only; hosted Auth, gateway, and RPC behavior are separate gates.
import assert from "node:assert/strict";
import { createModerationHandler, type ModerationDeps } from "./moderation.ts";

const actor = { profileId: "90000000-0000-4000-8000-000000000003", jti: "qa-jti-3" };
const target = "92000000-0000-4000-8000-000000000001";
const revision = "2026-11-09T12:00:00Z";
const requestId = "99000000-0000-4000-8000-000000000001";
const store = { kind: "store", id: target, expectedRevision: revision,
  decision: "approve", publicName: "Reviewed QA Store", publicCity: "QA City" };
const product = { kind: "product", id: target, expectedRevision: revision,
  decision: "approve", publicTitle: "Reviewed QA Product" };
const image = { kind: "image", id: target, expectedRevision: 1,
  decision: "approve", altText: "QA image" };
function assertEquals(actual: unknown, expected: unknown, message?: string) {
  if (message) assert.deepEqual(actual, expected, message);
  else assert.deepEqual(actual, expected);
}
function assertStringIncludes(value: string, fragment: string) { assert.ok(value.includes(fragment)); }
function request(body: unknown, key = requestId): Request {
  return new Request("http://127.0.0.1/adishop-qa-moderation", {
    method: "POST", headers: { "content-type": "application/json", "idempotency-key": key },
    body: JSON.stringify(body),
  });
}
function read(action: string, params = ""): Request {
  return new Request(`http://127.0.0.1/adishop-qa-moderation?action=${action}${params}`);
}
function fixture(options: Partial<ModerationDeps> = {}) {
  const calls: Array<{ name: string; args: Record<string, unknown> }> = [];
  const deps: ModerationDeps = {
    authenticate: async () => actor,
    isAdmin: async () => true,
    rpc: async (name, args) => { calls.push({ name, args }); return { data: { decision: args.p_decision }, error: null }; },
    now: () => 1_000_000,
    ...options,
  };
  return { handler: createModerationHandler(deps), calls };
}

Deno.test("admin queue uses bounded pagination and session-scoped RPC", async () => {
  const f = fixture();
  const response = await f.handler(read("queue", "&kind=product&limit=20&offset=40"));
  assertEquals(response.status, 200);
  assertEquals(f.calls[0].name, "adishop_qa_moderation_queue");
  assertEquals(f.calls[0].args, { p_session_jti: actor.jti, p_kind: "product", p_limit: 20, p_offset: 40 });
  assertEquals((await response.json()).ok, true);
});

Deno.test("preview uses exact target and rejects unauthorized or missing targets", async () => {
  const f = fixture();
  const response = await f.handler(read("preview", `&kind=image&id=${target}`));
  assertEquals(response.status, 200);
  assertEquals(f.calls[0].name, "adishop_qa_moderation_preview");
  assertEquals(f.calls[0].args, { p_session_jti: actor.jti, p_kind: "image", p_id: target });
  assertEquals((await fixture({ isAdmin: async () => false }).handler(read("queue"))).status, 403);
  assertEquals((await fixture({ authenticate: async () => { throw Error("suspended"); } }).handler(read("queue"))).status, 401);
  assertEquals((await fixture({ rpc: async () => ({ data: null, error: { code: "22023" } }) })
    .handler(read("preview", `&kind=store&id=${target}`))).status, 404);
});

Deno.test("read input and backend errors fail safely", async () => {
  const f = fixture();
  for (const url of [read("queue", "&limit=51"), read("queue", "&offset=-1"),
    read("preview", `&kind=store&id=${target}&offset=0`), read("preview", "&kind=store&id=bad")]) {
    assertEquals((await f.handler(url)).status, 400);
  }
  assertEquals(f.calls.length, 0);
  const response = await fixture({ rpc: async () => ({ data: null, error: { code: "XX000", message: "private" } }) })
    .handler(read("queue"));
  assertEquals(response.status, 503);
  assertEquals((await response.text()).includes("private"), false);
});

Deno.test("store routes only the validated JTI to service RPC", async () => {
  const f = fixture();
  assertEquals((await f.handler(request(store))).status, 200);
  assertEquals(f.calls[0].name, "adishop_qa_review_store");
  assertEquals(f.calls[0].args.p_session_jti, actor.jti);
  assertEquals(f.calls[0].args.p_store_id, target);
});
Deno.test("product and image rejection route to exact review RPCs", async () => {
  const f = fixture();
  assertEquals((await f.handler(request(product))).status, 200);
  assertEquals((await f.handler(request({ ...image, decision: "reject" }, "99000000-0000-4000-8000-000000000002"))).status, 200);
  assertEquals(f.calls.map((call) => call.name), ["adishop_qa_review_product", "adishop_qa_review_image"]);
});
Deno.test("image approval blocked before RPC even for admin", async () => {
  const f = fixture();
  const response = await f.handler(request(image));
  assertEquals(response.status, 403);
  assertEquals((await response.json()).error, "visual-preview-required");
  assertEquals(f.calls.length, 0);
});
Deno.test("buyer and seller roles denied before RPC", async () => {
  for (const role of ["buyer", "seller"]) {
    const f = fixture({ isAdmin: async () => false });
    assertEquals((await f.handler(request(store))).status, 403, role);
    assertEquals(f.calls.length, 0);
  }
});
Deno.test("invalid, expired, suspended and revoked sessions denied", async () => {
  for (const reason of ["invalid", "expired", "suspended", "revoked"]) {
    const f = fixture({ authenticate: async () => { throw Error(reason); } });
    assertEquals((await f.handler(request(store))).status, 401, reason);
    assertEquals(f.calls.length, 0);
  }
});
Deno.test("client actor and role claims rejected", async () => {
  for (const key of ["profileId", "userId", "role", "sellerId", "sessionJti"]) {
    const f = fixture();
    assertEquals((await f.handler(request({ ...store, [key]: "SHOP_ADMIN" }))).status, 400);
    assertEquals(f.calls.length, 0);
  }
});
Deno.test("malformed IDs revisions decisions and idempotency denied", async () => {
  for (const body of [{ ...store, id: "bad" }, { ...store, expectedRevision: "tomorrow" },
    { ...store, decision: "activate" }, { ...image, expectedRevision: 0 }]) {
    assertEquals((await fixture().handler(request(body))).status, 400);
  }
  assertEquals((await fixture().handler(request(store, "bad"))).status, 400);
});
Deno.test("oversized body denied before authorization", async () => {
  let authCalls = 0;
  const f = fixture({ authenticate: async () => { authCalls++; return actor; } });
  assertEquals((await f.handler(request({ ...store, padding: "x".repeat(9000) }))).status, 400);
  assertEquals(authCalls, 0);
});
Deno.test("same-instance duplicate request denied", async () => {
  const f = fixture();
  assertEquals((await f.handler(request(store))).status, 200);
  assertEquals((await f.handler(request(store))).status, 409);
  assertEquals(f.calls.length, 1);
});
Deno.test("stale revision and owner denial map to safe errors", async () => {
  for (const [code, expected] of [["40001", 409], ["42501", 403]] as const) {
    const f = fixture({ rpc: async () => ({ data: null, error: { code, message: "private SQL detail" } }) });
    const response = await f.handler(request(store));
    assertEquals(response.status, expected);
    assertEquals((await response.text()).includes("private SQL detail"), false);
  }
});
Deno.test("database duplicate-review maps to safe 409", async () => {
  const f = fixture({ rpc: async () => ({ data: null,
    error: { code: "40001", message: "duplicate-review" } }) });
  const response = await f.handler(request(store));
  assertEquals(response.status, 409);
  assertEquals((await response.json()).error, "duplicate-request");
});
Deno.test("unexpected RPC failure returns 503 without SQL or credential leak", async () => {
  const f = fixture({ rpc: async () => { throw Error("private-token-and-SQL"); } });
  const response = await f.handler(request(store));
  assertEquals(response.status, 503);
  assertEquals((await response.text()).includes("private-token-and-SQL"), false);
});
Deno.test("per-session request rate is bounded", async () => {
  const f = fixture();
  for (let n = 1; n <= 10; n++) {
    const key = `99000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
    assertEquals((await f.handler(request(store, key))).status, 200);
  }
  assertEquals((await f.handler(request(store, "99000000-0000-4000-8000-000000000011"))).status, 429);
});
Deno.test("unsupported method denied", async () => {
  const f = fixture();
  const response = await f.handler(new Request("http://127.0.0.1/", { method: "PUT" }));
  assertEquals(response.status, 405);
  assertStringIncludes(await response.text(), "method-not-allowed");
});
