import { cartMinimumOrderQuantity } from "../functions/_shared/adishop.ts";

for (const [name, value, expected] of [
  ["valid quantity", { minimum_order_quantity: 3 }, 3],
  ["null quantity uses legacy default", { minimum_order_quantity: null }, 1],
  ["null relation", null, null],
  ["array relation", [{ minimum_order_quantity: 3 }], null],
  ["missing quantity", {}, null],
  ["nonnumeric quantity", { minimum_order_quantity: "invalid" }, null],
  ["negative quantity", { minimum_order_quantity: -2 }, null],
  ["fractional quantity", { minimum_order_quantity: 1.5 }, null],
] as const) {
  Deno.test(`cart relation ${name}`, () => {
    if (cartMinimumOrderQuantity(value) !== expected) throw new Error("incorrect minimum order quantity");
  });
}
