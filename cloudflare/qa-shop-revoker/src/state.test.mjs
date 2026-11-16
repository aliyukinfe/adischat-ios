import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import { allowlistHas, operationStore } from "./state.mjs";

function fixture() {
  const db = new DatabaseSync(":memory:");
  db.exec(readFileSync(new URL("../qa-state-schema.sql", import.meta.url), "utf8"));
  const env = { QA_STATE_DB: { prepare(sql) {
    const statement = db.prepare(sql);
    return { first: async () => statement.get() ?? null, bind(...values) {
      return {
        first: async () => statement.get(...values) ?? null,
        all: async () => ({ results: statement.all(...values) }),
        run: async () => ({ meta: { changes: statement.run(...values).changes } }),
      };
    } };
  } } };
  return { db, env, store: operationStore(env) };
}
const id = "11111111-1111-4111-8111-111111111111";

test("shared QA allowlist reads only enabled entries and fails on missing binding", async () => {
  const f = fixture();
  f.db.prepare("insert into qa_allowlist(uid,enabled,updated_at) values(?,?,?)").run("qa-one", 1, 1);
  assert.equal(await allowlistHas(f.env, "qa-one"), true);
  assert.equal(await allowlistHas(f.env, "qa-two"), false);
  f.db.prepare("update qa_allowlist set enabled = 0 where uid = ?").run("qa-one");
  assert.equal(await allowlistHas(f.env, "qa-one"), false);
  await assert.rejects(() => allowlistHas({}, "qa-one"));
  f.db.close();
});
test("D1-style operation record survives retries, conflicts and queue exhaustion", async () => {
  const f = fixture();
  const first = await f.store.begin(id, "qa-one", "firebase_disabled", 100, 1000);
  const duplicate = await f.store.begin(id, "qa-other", "firebase_tokens_revoked", 200, 1001);
  assert.equal(first.uid, "qa-one"); assert.equal(duplicate.uid, "qa-one");
  assert.equal(await f.store.claim(id, 1000), true);
  assert.equal(await f.store.claim(id, 1000), false);
  await f.store.applied(id, 101, 1001);
  for (let index = 0; index < 5; index++) await f.store.queued(id, 2000 + index);
  assert.equal((await f.store.get(id)).enqueue_count, 5);
  assert.equal((await f.store.get(id)).attention, "delivery-exhausted");
  assert.deepEqual(await f.store.pending(20, 3000), []);
  await f.store.confirmed(id, 3000);
  assert.equal((await f.store.get(id)).state, "confirmed");
  f.db.close();
});
test("D1-style reconciliation cursor uses a lease and records failure without advancement", async () => {
  const f = fixture();
  assert.equal(await f.store.claimScan("owner-a", 1000), true);
  assert.equal(await f.store.claimScan("owner-b", 1000), false);
  await f.store.recordFailure("qa-reconcile-incomplete", "owner-a", 1001);
  assert.equal((await f.store.cursor()).last_link_id, null);
  assert.equal(f.db.prepare("select last_error from qa_reconciliation_cursor").get().last_error, "qa-reconcile-incomplete");
  await f.store.saveCursor("link-1001", "owner-a", 1002);
  await f.store.releaseScan("owner-a");
  assert.equal(await f.store.claimScan("owner-b", 1003), true);
  assert.equal((await f.store.cursor()).last_link_id, "link-1001");
  f.db.close();
});
