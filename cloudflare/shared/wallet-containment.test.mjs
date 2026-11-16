import assert from "node:assert/strict";
import test from "node:test";
import { walletContainmentResponse } from "./wallet-containment.mjs";

function request(method, path) {
  return new Request(`https://worker.invalid${path}`, { method });
}

test("blocks wallet funding, PIN, activation, and payment codes", () => {
  for (const [method, path] of [
    ["POST", "/api/wallet/deposit"],
    ["POST", "/api/wallet/change-pin"],
    ["POST", "/api/wallet/activate"],
    ["GET", "/api/wallet/me"],
    ["GET", "/api/wallet/pay-code"],
    ["GET", "/api/wallet/receive"],
    ["POST", "/api/admin/wallets/user/adjust"],
    ["GET", "/api/merchant/pay-code"],
  ]) {
    const result = walletContainmentResponse(request(method, path));
    assert.equal(result?.status, 403, `${method} ${path}`);
    assert.equal(result.headers.get("Cache-Control"), "no-store");
  }
});

test("blocks wallet-backed game mutations but preserves read-only paths", () => {
  assert.equal(walletContainmentResponse(request("POST", "/api/games/id/buy"))?.status, 403);
  assert.equal(walletContainmentResponse(request("POST", "/api/games/id/draw"))?.status, 403);
  assert.equal(walletContainmentResponse(request("GET", "/api/games/id/detail")), null);
  assert.equal(walletContainmentResponse(request("GET", "/api/wallet/transactions")), null);
  assert.equal(walletContainmentResponse(request("POST", "/api/messages/send")), null);
  assert.equal(walletContainmentResponse(request("GET", "/api/health")), null);
});
