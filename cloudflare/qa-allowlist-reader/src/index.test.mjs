import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { readFileSync } from "node:fs";
import { createHandler } from "./index.mjs";
import { allowlistHas } from "./client.mjs";

globalThis.crypto ||= webcrypto;
const secret = "c".repeat(64);
const now = 1_800_000_000_000;
const uid = "qa-synthetic-one";
let queryCount = 0;
const readerEnv = () => ({
  QA_ALLOWLIST_READ_SECRET: secret,
  QA_STATE_DB: { prepare(sql) {
    assert.equal(sql, "select uid from qa_allowlist where uid = ? and enabled = 1");
    queryCount++;
    return { bind: (candidate) => ({ first: async () => candidate === uid ? { uid } : null }) };
  } },
});
const reader = createHandler(() => now);
const issuerEnv = () => ({ QA_ALLOWLIST_READ_SECRET: secret,
  QA_ALLOWLIST_READER: { fetch: (url, init) => reader(new Request(url, init), readerEnv()) } });

test("private reader returns only allowlist membership through signed service call", async () => {
  queryCount = 0;
  assert.equal(await allowlistHas(issuerEnv(), uid, now), true);
  assert.equal(await allowlistHas(issuerEnv(), "qa-synthetic-two", now), false);
  assert.equal(queryCount, 2);
  assert.equal(Object.hasOwn(issuerEnv(), "QA_STATE_DB"), false);
});

test("unauthenticated and forged reader requests cannot access D1", async () => {
  queryCount = 0;
  for (const headers of [{}, { "x-qa-time": String(now / 1000), "x-qa-signature": "0".repeat(64) }]) {
    const result = await reader(new Request("https://qa-allowlist-reader.internal/contains", {
      method: "POST", headers, body: JSON.stringify({ uid }),
    }), readerEnv());
    assert.equal(result.status, 401);
  }
  assert.equal(queryCount, 0);
});

test("write methods, other paths, stale signatures and unavailable data fail closed", async () => {
  queryCount = 0;
  assert.equal((await reader(new Request("https://qa-allowlist-reader.internal/contains", { method: "PUT" }), readerEnv())).status, 405);
  assert.equal((await reader(new Request("https://qa-allowlist-reader.internal/update", { method: "POST" }), readerEnv())).status, 404);
  assert.equal((await reader(new Request("https://qa-allowlist-reader.internal/contains", {
    method: "POST", body: JSON.stringify({ uid, action: "write" }),
  }), readerEnv())).status, 401);
  assert.equal(queryCount, 0);
  const intercepted = issuerEnv();
  intercepted.QA_ALLOWLIST_READER.fetch = async (url, init) => {
    const stale = new Request(url, { ...init, headers: { ...init.headers, "x-qa-time": String(Math.floor(now / 1000) - 120) } });
    return reader(stale, readerEnv());
  };
  await assert.rejects(() => allowlistHas(intercepted, uid, now));
  assert.equal(queryCount, 0);
  const broken = issuerEnv();
  broken.QA_ALLOWLIST_READER.fetch = async () => new Response(null, { status: 503 });
  await assert.rejects(() => allowlistHas(broken, uid, now));
});

test("QA deployment templates enforce issuer and reader binding separation", () => {
  const issuer = readFileSync(new URL("../../qa-shop-issuer/wrangler.qa.example.toml", import.meta.url), "utf8");
  const readerConfig = readFileSync(new URL("../wrangler.qa.example.toml", import.meta.url), "utf8");
  const revoker = readFileSync(new URL("../../qa-shop-revoker/wrangler.qa.example.toml", import.meta.url), "utf8");
  assert.match(issuer, /binding = "QA_ALLOWLIST_READER"/);
  assert.doesNotMatch(issuer, /\[\[d1_databases\]\]/);
  assert.match(readerConfig, /workers_dev = false/);
  assert.match(readerConfig, /binding = "QA_STATE_DB"/);
  assert.match(revoker, /binding = "QA_STATE_DB"/);
  for (const config of [issuer, readerConfig, revoker]) {
    assert.doesNotMatch(config, /mcewjfnkrtiiolzibsuh|adischat-f4a77/);
  }
});
