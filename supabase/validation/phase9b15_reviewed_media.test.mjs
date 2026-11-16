import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";

const source = readFileSync(new URL("../functions/_shared/adishop-reviewed-media.ts", import.meta.url), "utf8");
const { serveReviewedMedia } = await import(`data:text/javascript,${encodeURIComponent(stripTypeScriptTypes(source))}`);
const id = "30000000-0000-4000-8000-000000000001";
const storeId = "10000000-0000-4000-8000-000000000001";
const origin = "https://example.supabase.co";
const productId = "20000000-0000-4000-8000-000000000001";
const image = { kind: "image", id, revision: 2, bucket: "shop-product-images",
  productId, storeId, storagePath: `seller-products/owner/${storeId}/${productId}/photo.png` };
const png = new Blob([new Uint8Array([137, 80, 78, 71])], { type: "image/png" });
const url = (kind = "image", target = id) =>
  `https://example.supabase.co/functions/v1/adishop-product-reviews?action=reviewed-media&kind=${kind}&id=${target}`;
const deps = (lookup = async () => image, download = async () => png) =>
  ({ enabled: true, supabaseOrigin: origin, lookup, download });

test("disabled route makes no locator or Storage call", async () => {
  const result = await serveReviewedMedia(new Request(url()), {
    ...deps(() => { throw Error("unexpected lookup"); }), enabled: false,
  });
  assert.equal(result.status, 404);
  assert.match(result.headers.get("cache-control"), /no-store/);
});

test("approved image streams only bytes and never exposes private paths", async () => {
  let lookups = 0;
  const result = await serveReviewedMedia(new Request(url()), deps(async () => { lookups++; return image; }));
  assert.equal(result.status, 200);
  assert.equal(lookups, 2);
  assert.equal(result.headers.get("content-type"), "image/png");
  assert.match(result.headers.get("cache-control"), /no-store/);
  assert.equal(result.headers.get("x-content-type-options"), "nosniff");
  assert.deepEqual(new Uint8Array(await result.arrayBuffer()), new Uint8Array([137, 80, 78, 71]));
  assert.equal(result.headers.get("location"), null);
});

test("approved product cover uses product identity without an object path parameter", async () => {
  const cover = { ...image, kind: "product-cover", id: productId };
  const result = await serveReviewedMedia(new Request(url("product-cover", productId)), deps(async () => cover));
  assert.equal(result.status, 200);
});

test("unreviewed, rejected, and cross-store locator denial are indistinguishable", async () => {
  for (const status of ["unreviewed", "rejected", "cross-store"]) {
    const result = await serveReviewedMedia(new Request(url()), deps(async () => null));
    assert.equal(result.status, 404, status);
    assert.match(result.headers.get("cache-control"), /no-store/);
  }
});

test("forged authorization header cannot override a denied locator", async () => {
  const request = new Request(url(), { headers: { Authorization: "Bearer forged-admin" } });
  const result = await serveReviewedMedia(request, deps(async () => null));
  assert.equal(result.status, 404);
});

test("revision invalidation during download fails closed", async () => {
  let n = 0;
  const result = await serveReviewedMedia(new Request(url()), deps(async () => (++n === 1 ? image : { ...image, revision: 3 })));
  assert.equal(result.status, 404);
});

test("forged store/path parameters and malformed identifiers are rejected before lookup", async () => {
  for (const requestUrl of [url() + "&path=other/object", url() + "&storeId=" + storeId,
    url() + "&kind=store-logo",
    url("image", "../../private"), url("seller", id)]) {
    const result = await serveReviewedMedia(new Request(requestUrl), deps(() => { throw Error("unexpected lookup"); }));
    assert.equal(result.status, 404);
  }
});

test("wrong bucket, traversal, and mismatched locator cannot fetch objects", async () => {
  for (const bad of [{ ...image, bucket: "shop-seller-documents" },
    { ...image, storagePath: "seller-products/../private.png" },
    { ...image, storagePath: "seller-products/%2e%2e/private.png" },
    { ...image, storagePath: `seller-products/owner/${id}/${productId}/photo.png` },
    { ...image, id: storeId }]) {
    const result = await serveReviewedMedia(new Request(url()), deps(async () => bad,
      () => { throw Error("unexpected download"); }));
    assert.equal(result.status, 404);
  }
});

test("reviewed store image resolves only same-origin exact bucket URL", async () => {
  const store = { kind: "store-logo", id: storeId, revision: 1, bucket: "shop-store-assets",
    sourceUrl: `${origin}/storage/v1/object/public/shop-store-assets/stores/${storeId}/logo.png` };
  const result = await serveReviewedMedia(new Request(url("store-logo", storeId)), deps(async () => store));
  assert.equal(result.status, 200);
  for (const sourceUrl of ["https://evil.example/storage/v1/object/public/shop-store-assets/store/logo.png",
    `${origin}/storage/v1/object/public/shop-product-images/store/logo.png`,
    `${origin}/storage/v1/object/public/shop-store-assets/stores/${id}/logo.png`,
    `${origin}/storage/v1/object/public/shop-store-assets/store/%252e%252e/logo.png`]) {
    const denied = await serveReviewedMedia(new Request(url("store-logo", storeId)),
      deps(async () => ({ ...store, sourceUrl }), () => { throw Error("unexpected download"); }));
    assert.equal(denied.status, 404);
  }
});

test("unsafe MIME, oversized bytes, and Storage failure are not cached", async () => {
  for (const blob of [new Blob(["<svg/>"] , { type: "image/svg+xml" }),
    new Blob([new Uint8Array(5 * 1024 * 1024 + 1)], { type: "image/png" })]) {
    const result = await serveReviewedMedia(new Request(url()), deps(async () => image, async () => blob));
    assert.equal(result.status, 415);
    assert.match(result.headers.get("cache-control"), /no-store/);
  }
  const failed = await serveReviewedMedia(new Request(url()), deps(async () => image, async () => { throw Error("private-path"); }));
  assert.equal(failed.status, 503);
  assert.equal(await failed.text(), "");
  assert.match(failed.headers.get("cache-control"), /no-store/);
});
