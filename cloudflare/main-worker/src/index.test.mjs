import assert from "node:assert/strict";
import test from "node:test";
import worker from "./index.mjs";

const TEST_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

test("Main Worker: GET /api/health returns health JSON", async () => {
  const req = new Request("https://adischat-api.test/api/health", { method: "GET" });
  const env = { CORS_ORIGIN: "*" };
  const res = await worker.fetch(req, env, {});
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.ok, true);
  assert.equal(data.service, "adischat-api");
  assert.equal(data.version, "2026.06.07");
});

test("Main Worker: OPTIONS preflight on Restaurants route returns 204 with restaurant CORS", async () => {
  const req = new Request("https://adischat-api.test/api/restaurants/session-assertion", {
    method: "OPTIONS",
    headers: { Origin: "https://restaurant.adischat.com" },
  });
  const env = { CORS_ORIGIN: "*", RESTAURANTS_ASSERTION_SECRET: TEST_SECRET };
  const res = await worker.fetch(req, env, {});
  assert.equal(res.status, 204);
  assert.equal(res.headers.get("Access-Control-Allow-Origin"), "https://restaurant.adischat.com");
});

test("Main Worker: Unauthenticated request to /api/restaurants/session-assertion returns 401 Missing Firebase token", async () => {
  const req = new Request("https://adischat-api.test/api/restaurants/session-assertion", {
    method: "POST",
  });
  const env = {
    FIREBASE_PROJECT_ID: "adischat-f4a77",
    CORS_ORIGIN: "*",
    RESTAURANTS_ASSERTION_SECRET: TEST_SECRET,
  };
  const res = await worker.fetch(req, env, {});
  assert.equal(res.status, 401);
  const data = await res.json();
  assert.equal(data.error, "Missing Firebase ID token");
});

test("Main Worker: Unauthenticated request to /api/shop/session-assertion returns 401 Missing Firebase token", async () => {
  const req = new Request("https://adischat-api.test/api/shop/session-assertion", {
    method: "POST",
  });
  const env = {
    FIREBASE_PROJECT_ID: "adischat-f4a77",
    CORS_ORIGIN: "*",
  };
  const res = await worker.fetch(req, env, {});
  assert.equal(res.status, 401);
  const data = await res.json();
  assert.equal(data.error, "Missing Firebase ID token");
});

test("Main Worker: Invalid bearer token returns 401 Invalid Firebase ID token", async () => {
  const req = new Request("https://adischat-api.test/api/restaurants/session-assertion", {
    method: "POST",
    headers: { Authorization: "Bearer totally_invalid_token" },
  });
  const env = {
    FIREBASE_PROJECT_ID: "adischat-f4a77",
    CORS_ORIGIN: "*",
    RESTAURANTS_ASSERTION_SECRET: TEST_SECRET,
  };
  const res = await worker.fetch(req, env, {});
  assert.equal(res.status, 401);
  const data = await res.json();
  assert.equal(data.error, "Invalid Firebase ID token");
});

test("Main Worker: Unknown route returns 404 Not Found after passing auth gate", async () => {
  const req = new Request("https://adischat-api.test/api/nonexistent-route", {
    method: "GET",
  });
  const env = {
    FIREBASE_PROJECT_ID: "adischat-f4a77",
    CORS_ORIGIN: "*",
  };
  const res = await worker.fetch(req, env, {});
  assert.equal(res.status, 401); // Auth gate intercepts unauthenticated requests
});
