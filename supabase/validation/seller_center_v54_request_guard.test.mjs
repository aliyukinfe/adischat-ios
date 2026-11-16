import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const migration = readFileSync(new URL("../migrations/20261027184820_adisgebeya_v54_seller_request_terminal_guard.sql", import.meta.url), "utf8");
const edge = readFileSync(new URL("../functions/_shared/adishop.ts", import.meta.url), "utf8");

test("terminal request guard follows selected-store authorization and precedes response mutation", () => {
  const ownerCheck = migration.indexOf("adishop_profile_can_fulfill_store");
  const terminalCheck = migration.indexOf("request_status in ('rejected', 'cancelled', 'completed', 'closed')");
  const responseCall = migration.indexOf("return public.adishop_seller_respond_request(");
  assert.ok(ownerCheck >= 0 && ownerCheck < terminalCheck && terminalCheck < responseCall);
  assert.match(migration, /from public\.shop_refund_requests where id = p_request_id for update/);
  assert.match(migration, /from public\.shop_exchange_requests where id = p_request_id for update/);
  assert.match(migration, /revoke all on function public\.adishop_seller_respond_request_scoped\(.+\) from public, anon, authenticated/);
});

test("closed seller requests return a safe conflict from the existing Edge handler", () => {
  assert.match(edge, /detail\.includes\("seller-request-closed"\).*structuredError\("seller_request_closed".*409, false\)/);
});
