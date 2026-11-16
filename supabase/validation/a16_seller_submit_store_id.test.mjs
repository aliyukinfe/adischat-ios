import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import test from "node:test";

const source = readFileSync(
  fileURLToPath(new URL("../functions/_shared/adishop.ts", import.meta.url)),
  "utf8",
);
const submitHandler = source.match(/async function submitSellerProduct\(request: Request\) \{([\s\S]*?)\n\}/)?.[1];
assert.ok(submitHandler, "seller submit handler must exist");
const uuidPattern = submitHandler.match(/if \(storeId && !(\/\^[^\n]+?\$\/i)\.test\(storeId\)\)/)?.[1];
assert.ok(uuidPattern, "seller submit must validate selected store IDs");
const acceptsStoreId = new RegExp(uuidPattern.slice(1, -2), "i");

test("seller submission accepts a complete UUID for the selected store", () => {
  assert.equal(acceptsStoreId.test("cfad4ae7-acb2-491d-accb-513935222a0c"), true);
  assert.equal(acceptsStoreId.test("00000000-0000-4000-8000-000000000201"), true);
});

test("seller submission rejects incomplete and malformed store IDs", () => {
  assert.equal(acceptsStoreId.test("cfad4ae7-acb2-491d-513935222a0c"), false);
  assert.equal(acceptsStoreId.test("not-a-store"), false);
});

test("seller submission passes the selected store and product to the scoped RPC", () => {
  assert.match(submitHandler, /adishop_seller_submit_product_for_store/);
  assert.match(submitHandler, /p_store_id: storeId/);
  assert.match(submitHandler, /p_product_id: String\(body\.productId \|\| ""\)/);
});
