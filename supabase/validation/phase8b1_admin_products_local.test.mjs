import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { stripTypeScriptTypes } from "node:module";
import test from "node:test";

const source = await readFile(new URL("./phase8b2_scoped_bundle/supabase/functions/_shared/adishop-admin-all-products.ts", import.meta.url), "utf8");
const moduleSource = stripTypeScriptTypes(source.replace(
  'import { currentSession } from "./adishop.ts";',
  "const currentSession = (...args) => globalThis.__phase8b1Session(...args);",
));
const { handleAdminAllProducts } = await import(`data:text/javascript,${encodeURIComponent(moduleSource)}`);

function request(query = "", headers = {}) {
  return new Request(`https://example.test/functions/v1/adishop-admin-products?view=all-v2&${query}`, { headers });
}

async function result(query = "") {
  const response = await handleAdminAllProducts(request(query));
  return { status: response.status, body: await response.json() };
}

function fakeClient(permission, rows = [], lookups = {}) {
  const calls = [];
  const client = {
    rpc: async (name, args) => {
      calls.push(["rpc", name, args]);
      return { data: { permissions: permission ? [permission] : [] }, error: null };
    },
    from: (table) => {
      const query = {
        select: (...args) => { calls.push([table, "select", args]); return query; },
        is: (...args) => { calls.push([table, "is", args]); return query; },
        eq: (...args) => { calls.push([table, "eq", args]); return query; },
        ilike: (...args) => { calls.push([table, "ilike", args]); return query; },
        in: (...args) => { calls.push([table, "in", args]); return query; },
        or: (...args) => { calls.push([table, "or", args]); return query; },
        limit: (...args) => { calls.push([table, "limit", args]); return Promise.resolve({ data: lookups[table] || [], error: null }); },
        order: (...args) => { calls.push([table, "order", args]); return query; },
        range: (...args) => {
          calls.push([table, "range", args]);
          return Promise.resolve({ data: rows, count: rows.length, error: null });
        },
      };
      return query;
    },
  };
  return { client, calls };
}

test("missing session is denied before any data access", async () => {
  globalThis.__phase8b1Session = async () => { throw new Error("missing-session"); };
  assert.equal((await result()).status, 401);
});

test("missing moderation-read permission is denied before product access", async () => {
  const { client, calls } = fakeClient(null);
  globalThis.__phase8b1Session = async () => ({ client, profileId: "synthetic-buyer" });
  assert.equal((await result()).status, 403);
  assert.equal(calls.some(([name]) => name === "shop_products"), false);
});

test("authorized request applies filters, exact count, deterministic order and page bound", async () => {
  const { client, calls } = fakeClient("product.moderation.read");
  globalThis.__phase8b1Session = async () => ({ client, profileId: "synthetic-admin" });
  const response = await result("status=approved&category=phones&storeId=11111111-1111-4111-8111-111111111111&stock=in_stock&sort=updated&limit=200&offset=5");
  assert.equal(response.status, 200);
  assert.equal(response.body.limit, 100);
  assert.deepEqual(calls.find(([table, method]) => table === "shop_products" && method === "range")?.[2], [5, 104]);
  assert.deepEqual(calls.find(([table, method]) => table === "shop_products" && method === "select")?.[2][1], { count: "exact" });
  assert.deepEqual(calls.filter(([table, method]) => table === "shop_products" && method === "eq").map(([, , args]) => args), [
    ["moderation_status", "approved"], ["category_slug", "phones"],
    ["store_id", "11111111-1111-4111-8111-111111111111"], ["stock_status", "in_stock"],
  ]);
  assert.deepEqual(calls.filter(([table, method]) => table === "shop_products" && method === "order").map(([, , args]) => args), [
    ["updated_at", { ascending: false }], ["id", { ascending: true }],
  ]);
});

test("omitted pagination uses the documented 30-item first page", async () => {
  const { client, calls } = fakeClient("product.moderation.read");
  globalThis.__phase8b1Session = async () => ({ client, profileId: "synthetic-admin" });
  const response = await result();
  assert.equal(response.status, 200);
  assert.equal(response.body.limit, 30);
  assert.equal(response.body.offset, 0);
  assert.deepEqual(calls.find(([table, method]) => table === "shop_products" && method === "range")?.[2], [0, 29]);
});

test("invalid filters and identifiers fail before product access", async () => {
  const { client, calls } = fakeClient("product.moderation.read");
  globalThis.__phase8b1Session = async () => ({ client, profileId: "synthetic-admin" });
  for (const query of ["status=bogus", "stock=bogus", "storeId=forged", "sort=price_minor.desc", "limit=bogus", "offset=-5"]) {
    assert.equal((await result(query)).status, 400);
  }
  assert.equal(calls.some(([name]) => name === "shop_products"), false);
});

test("search queries only bounded preliminary tables and same final product dataset", async () => {
  const { client, calls } = fakeClient("product.moderation.read");
  globalThis.__phase8b1Session = async () => ({ client, profileId: "synthetic-admin" });
  assert.equal((await result("search=Galaxy&stock=in_stock")).status, 200);
  assert.equal(calls.filter(([, method]) => method === "limit").length, 2);
  assert.equal(calls.some(([table, method]) => table === "shop_products" && method === "or"), true);
});

test("product ID uses an exact indexed equality filter", async () => {
  const exactId = "11111111-1111-4111-8111-111111111111";
  const { client, calls } = fakeClient("product.moderation.read");
  globalThis.__phase8b1Session = async () => ({ client, profileId: "synthetic-admin" });
  const response = await result(`search=${exactId}`);
  assert.equal(response.status, 200);
  assert.equal(response.body.total, 0);
  assert.deepEqual(calls.find(([table, method, args]) => table === "shop_products" && method === "eq" && args[0] === "id")?.[2], ["id", exactId]);
  assert.equal(calls.some(([table]) => table === "shop_product_variants"), false);
});

test("textual variant SKU candidates join the final filtered dataset", async () => {
  const productId = "22222222-2222-4222-8222-222222222222";
  const { client, calls } = fakeClient("product.moderation.read", [], {
    shop_product_variants: [{ product_id: productId }],
  });
  globalThis.__phase8b1Session = async () => ({ client, profileId: "synthetic-admin" });
  assert.equal((await result("search=SKU-123&status=approved")).status, 200);
  const expression = calls.find(([table, method]) => table === "shop_products" && method === "or")?.[2][0];
  assert.ok(expression.includes(productId));
  assert.deepEqual(calls.find(([table, method, args]) => table === "shop_products" && method === "eq" && args[0] === "moderation_status")?.[2], ["moderation_status", "approved"]);
});

test("punctuated SKU is preserved for exact product and variant lookup", async () => {
  const productId = "33333333-3333-4333-8333-333333333333";
  const variantProductId = "44444444-4444-4444-8444-444444444444";
  const { client, calls } = fakeClient("product.moderation.read", [], {
    shop_products: [{ id: productId }],
    shop_product_variants: [{ product_id: variantProductId }],
  });
  globalThis.__phase8b1Session = async () => ({ client, profileId: "synthetic-admin" });
  const response = await result("search=SKU%2F128.5");
  assert.equal(response.status, 200);
  assert.deepEqual(calls.find(([table, method, args]) => table === "shop_products" && method === "eq" && args[0] === "seller_sku")?.[2], ["seller_sku", "SKU/128.5"]);
  assert.deepEqual(calls.find(([table, method, args]) => table === "shop_product_variants" && method === "eq" && args[0] === "sku")?.[2], ["sku", "SKU/128.5"]);
  assert.deepEqual(calls.find(([table, method, args]) => table === "shop_products" && method === "in" && args[0] === "id")?.[2], ["id", [productId, variantProductId]]);
});

test("overlong search is rejected before database reads", async () => {
  const { client, calls } = fakeClient("product.moderation.read");
  globalThis.__phase8b1Session = async () => ({ client, profileId: "synthetic-admin" });
  assert.equal((await result(`search=${"x".repeat(101)}`)).status, 400);
  assert.equal(calls.some(([name]) => name === "shop_products"), false);
});

async function wrapperHandler(path) {
  const wrapper = await readFile(new URL(path, import.meta.url), "utf8");
  const source = wrapper.replace(/^import .*;\r?\n/gm, "");
  let handler;
  globalThis.Deno = { serve: (value) => { handler = value; } };
  globalThis.__phase8b2Legacy = async (_name, request) => Response.json({ route: "legacy", path: new URL(request.url).search });
  globalThis.__phase8b2Advanced = async () => Response.json({ route: "advanced" });
  await import(`data:text/javascript,${encodeURIComponent(`
    const handleAdishopFunction = globalThis.__phase8b2Legacy;
    const handleAdminAllProducts = globalThis.__phase8b2Advanced;
    ${source}
  `)}`);
  return handler;
}

test("candidate preserves v10 wrapper dispatch for every non-v2 request", async () => {
  const baseline = await wrapperHandler("./phase8b2_v10_rollback/supabase/functions/adishop-admin-products/index.ts");
  const candidate = await wrapperHandler("./phase8b2_scoped_bundle/supabase/functions/adishop-admin-products/index.ts");
  for (const query of ["", "status=pending", "status=all&search=Galaxy", "action=benefit-queue", "view=unexpected"]) {
    const url = `https://example.test/functions/v1/adishop-admin-products?${query}`;
    assert.deepEqual(await (await candidate(new Request(url))).json(), await (await baseline(new Request(url))).json());
  }
  const advanced = await candidate(request());
  assert.equal((await advanced.json()).route, "advanced");
});
