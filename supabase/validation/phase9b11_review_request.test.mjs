import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";

const source = readFileSync(new URL("../functions/_shared/adishop-public-content-review.ts", import.meta.url), "utf8");
const { publicContentDecision, publicContentPage, publicContentTarget } =
  await import(`data:text/javascript,${encodeURIComponent(stripTypeScriptTypes(source))}`);

const id = "20000000-0000-4000-8000-000000000001";

test("valid target and reviewed revision", () => {
  assert.deepEqual(publicContentTarget({ kind: "product", id }), { kind: "product", id });
  assert.deepEqual(publicContentDecision({ kind: "image", id, expectedRevision: 4, decision: "approved" }),
    { kind: "image", id, revision: 4, decision: "approved" });
});

test("forged actor and store fields are not included in the RPC arguments", () => {
  assert.deepEqual(publicContentDecision({ kind: "store", id, expectedRevision: 1,
    decision: "rejected", actorProfileId: id, storeId: id }),
  { kind: "store", id, revision: 1, decision: "rejected" });
});

test("invalid identities, kinds, and decisions fail closed", () => {
  assert.equal(publicContentTarget({ kind: "product", id: "bad" }), null);
  assert.equal(publicContentTarget({ kind: "order", id }), null);
  assert.equal(publicContentTarget([]), null);
  assert.equal(publicContentDecision({ kind: "product", id, expectedRevision: 1, decision: "pending" }), null);
});

test("stale-shaped and unsafe revisions fail closed", () => {
  for (const revision of [0, -1, 1.5, "1", Number.MAX_SAFE_INTEGER + 1]) {
    assert.equal(publicContentDecision({ kind: "product", id, expectedRevision: revision,
      decision: "approved" }), null);
  }
});

test("queue pagination is bounded", () => {
  assert.deepEqual(publicContentPage(new URLSearchParams()), { kind: null, limit: 50, offset: 0 });
  assert.deepEqual(publicContentPage(new URLSearchParams("kind=variant&limit=100&offset=2")),
    { kind: "variant", limit: 100, offset: 2 });
  assert.equal(publicContentPage(new URLSearchParams("limit=101")), null);
  assert.equal(publicContentPage(new URLSearchParams("offset=-1")), null);
  assert.equal(publicContentPage(new URLSearchParams("kind=order")), null);
});
