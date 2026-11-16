import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { createHandler } from "./index.mjs";
import { createHandler as createReader } from "../../qa-allowlist-reader/src/index.mjs";

globalThis.crypto ||= webcrypto;
const now = 1_800_000_000_000;
const seconds = Math.floor(now / 1000);
const uid = "qa-buyer-uid";
const env = (members = [uid]) => ({
  FIREBASE_PROJECT_ID: "adischat-qa",
  ADISHOP_ASSERTION_ISSUER: "adischat-staging-opkqyvttbyezjbqwkvho",
  ADISHOP_ASSERTION_AUDIENCE: "adishop-staging-edge",
  ADISHOP_ASSERTION_SECRET: "a".repeat(64),
  QA_FIREBASE_WEB_API_KEY: "synthetic-test-key",
  QA_ALLOWLIST_READ_SECRET: "b".repeat(64),
  QA_ALLOWLIST_READER: { fetch: (url, init) => createReader(() => now)(new Request(url, init), {
    QA_ALLOWLIST_READ_SECRET: "b".repeat(64),
    QA_STATE_DB: { prepare: (sql) => {
      assert.match(sql, /^select uid from qa_allowlist where uid = \? and enabled = 1$/);
      return { bind: (candidate) => ({ first: async () =>
        members.includes(candidate) ? { uid: candidate } : null }) };
    } },
  }) },
  QA_RATE_LIMITER: { limit: async () => ({ success: true }) },
});
const b64 = (value) => Buffer.from(value).toString("base64url");
const decode = (part) => JSON.parse(Buffer.from(part, "base64url").toString());
const signing = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
const jwk = { ...await crypto.subtle.exportKey("jwk", signing.publicKey), kid: "test-key" };
async function token(overrides = {}) {
  const payload = { aud: "adischat-qa", iss: "https://securetoken.google.com/adischat-qa", sub: uid,
    user_id: uid, iat: seconds - 30, auth_time: seconds - 100, exp: seconds + 3500, ...overrides };
  const input = `${b64(JSON.stringify({ alg: "RS256", kid: "test-key" }))}.${b64(JSON.stringify(payload))}`;
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", signing.privateKey, new TextEncoder().encode(input));
  return `${input}.${b64(Buffer.from(signature))}`;
}
function handler(options = {}) {
  let lookupCalls = 0;
  const fetchImpl = async (url, init) => {
    if (url.includes("/jwk/")) return Response.json({ keys: [jwk] }, { headers: { "cache-control": "max-age=3600" } });
    lookupCalls++;
    assert.match(url, /identitytoolkit\.googleapis\.com/);
    assert.ok(init.body.includes("idToken"));
    if (options.lookupFailure) throw new Error("lookup unavailable");
    const user = { localId: options.localId || uid, disabled: !!options.disabled,
      validSince: Object.hasOwn(options, "validSince") ? options.validSince : seconds - 200 };
    if (options.missingValidSince) delete user.validSince;
    return Response.json({ users: [user] });
  };
  return { run: createHandler(fetchImpl, () => now), calls: () => lookupCalls };
}
const request = (jwt, body = { client: "android" }, method = "POST") => new Request("https://qa.invalid/api/shop/session-assertion", {
  method, headers: jwt ? { authorization: `Bearer ${jwt}`, "content-type": "application/json" } : {},
  body: method === "POST" ? JSON.stringify(body) : undefined,
});

test("valid QA token yields short, unique, UID-bound assertions", async () => {
  const { run, calls } = handler(); const jwt = await token();
  const first = await run(request(jwt), env()); const second = await run(request(jwt), env());
  assert.equal(first.status, 200); assert.equal(second.status, 200); assert.equal(calls(), 2);
  const a = (await first.json()).assertion.split("."); const b = (await second.json()).assertion.split(".");
  const claims = decode(a[1]);
  assert.equal(first.headers.get("cache-control"), "no-store");
  assert.equal(claims.sub, uid); assert.equal(claims.provider, "firebase");
  assert.equal(claims.iss, env().ADISHOP_ASSERTION_ISSUER); assert.equal(claims.aud, env().ADISHOP_ASSERTION_AUDIENCE);
  assert.equal(claims.exp - claims.iat, 120); assert.notEqual(claims.jti, decode(b[1]).jti);
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(env().ADISHOP_ASSERTION_SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["verify"]);
  assert.equal(await crypto.subtle.verify("HMAC", key, Buffer.from(a[2], "base64url"), new TextEncoder().encode(`${a[0]}.${a[1]}`)), true);
});

test("signature, audience, issuer, project, time and allowlist fail closed", async () => {
  const { run } = handler();
  const bad = [
    (await token()).slice(0, -2) + "zz", await token({ aud: "adischat-f4a77" }),
    await token({ iss: "https://securetoken.google.com/adischat-f4a77" }),
    await token({ exp: seconds - 1 }), await token({ iat: seconds + 60 }),
    await token({ sub: "other-uid", user_id: "other-uid" }),
    await token({ user_id: "client-spoofed" }),
  ];
  for (const jwt of bad) assert.equal((await run(request(jwt), env())).status, 401);
});

test("disabled and revoked Firebase users are denied", async () => {
  const jwt = await token();
  assert.equal((await handler({ disabled: true }).run(request(jwt), env())).status, 401);
  assert.equal((await handler({ validSince: seconds - 10 }).run(request(jwt), env())).status, 401);
  assert.equal((await handler({ validSince: seconds - 100 }).run(request(jwt), env())).status, 401);
  assert.equal((await handler({ localId: "other" }).run(request(jwt), env())).status, 401);
  assert.equal((await handler({ lookupFailure: true }).run(request(jwt), env())).status, 503);
});
test("Firebase revocation precision and malformed timestamp fail closed", async () => {
  const cutoff = seconds - 100;
  assert.equal((await handler({ validSince: cutoff }).run(request(await token({ auth_time: cutoff - 1 })), env())).status, 401);
  assert.equal((await handler({ validSince: cutoff }).run(request(await token({ auth_time: cutoff })), env())).status, 401);
  assert.equal((await handler({ validSince: cutoff }).run(request(await token({ auth_time: cutoff + 1 })), env())).status, 200);
  assert.equal((await handler({ missingValidSince: true }).run(request(await token()), env())).status, 401);
  assert.equal((await handler({ validSince: "bad" }).run(request(await token()), env())).status, 401);
  assert.equal((await handler({ validSince: null }).run(request(await token()), env())).status, 401);
});

test("rate-limit failures and absent or production-like config fail closed", async () => {
  const jwt = await token();
  const limited = env(); limited.QA_RATE_LIMITER.limit = async () => ({ success: false });
  assert.equal((await handler().run(request(jwt), limited)).status, 429);
  const broken = ["ADISHOP_ASSERTION_SECRET", "QA_FIREBASE_WEB_API_KEY", "QA_ALLOWLIST_READER", "QA_ALLOWLIST_READ_SECRET", "QA_RATE_LIMITER"];
  for (const key of broken) { const config = env(); delete config[key]; assert.equal((await handler().run(request(jwt), config)).status, 503); }
  const prod = env(); prod.FIREBASE_PROJECT_ID = "adischat-f4a77";
  assert.equal((await handler().run(request(jwt), prod)).status, 503);
  const wrong = env(); wrong.ADISHOP_ASSERTION_AUDIENCE = "adishop-prod-edge";
  assert.equal((await handler().run(request(jwt), wrong)).status, 503);
  const wrongIssuer = env(); wrongIssuer.ADISHOP_ASSERTION_ISSUER = "adischat-api";
  assert.equal((await handler().run(request(jwt), wrongIssuer)).status, 503);
  const shortSecret = env(); shortSecret.ADISHOP_ASSERTION_SECRET = "short";
  assert.equal((await handler().run(request(jwt), shortSecret)).status, 503);
  assert.equal((await handler().run(request(jwt), env([]))).status, 401);
});

test("method, missing auth, invalid payload and oversized body are denied", async () => {
  const { run } = handler(); const jwt = await token();
  assert.equal((await run(request(jwt, {}, "GET"), env())).status, 405);
  assert.equal((await run(request(null), env())).status, 401);
  assert.equal((await run(request("not.a.jwt"), env())).status, 401);
  assert.equal((await run(request(jwt, { uid: uid }), env())).status, 400);
  assert.equal((await run(request(jwt, { client: "android", padding: "x".repeat(1100) }), env())).status, 400);
  assert.equal((await run(new Request("https://qa.invalid/other", { method: "POST" }), env())).status, 404);
});

test("failure responses do not include credentials or assertion material", async () => {
  const jwt = await token(); const config = env();
  const response = await handler({ lookupFailure: true }).run(request(jwt), config);
  const body = await response.text();
  assert.equal(response.status, 503);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(body.includes(jwt), false);
  assert.equal(body.includes(config.ADISHOP_ASSERTION_SECRET), false);
  assert.equal(body.includes("assertion"), false);
});

test("pre-verification limit rejects before JWKS lookup", async () => {
  let keysFetched = 0;
  const run = createHandler(async () => { keysFetched++; throw new Error("unexpected fetch"); }, () => now);
  const config = env(); config.QA_RATE_LIMITER.limit = async ({ key }) => ({ success: !key.startsWith("pre:") });
  const result = await run(request(await token()), config);
  assert.equal(result.status, 429);
  assert.equal(keysFetched, 0);
});
