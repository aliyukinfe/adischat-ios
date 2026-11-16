import { activeSessionProfile } from "./phase8b2_scoped_bundle/supabase/functions/_shared/adishop.ts";

const id = "10000000-0000-4000-8000-000000000004";
const valid = { id, status: "active", display_name: "Synthetic Admin" };

Deno.test("active session profile accepts the matching active object", () => {
  if (activeSessionProfile(valid, id) !== valid) throw new Error("valid profile rejected");
});

for (const [name, value, profileId] of [
  ["null", null, id],
  ["array", [valid], id],
  ["missing id", { status: "active" }, id],
  ["non-string id", { id: 4, status: "active" }, id],
  ["missing status", { id }, id],
  ["inactive", { id, status: "suspended" }, id],
  ["wrong profile", valid, "10000000-0000-4000-8000-000000000005"],
  ["malformed profile id", valid, null],
] as const) {
  Deno.test(`active session profile rejects ${name}`, () => {
    if (activeSessionProfile(value, profileId) !== null) throw new Error("malformed profile accepted");
  });
}
