import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";

const source = readFileSync(new URL("../functions/_shared/adishop-reviewed-media.ts", import.meta.url), "utf8");
const { projectBuyerProductMedia, projectBuyerOrderMedia, reviewedMediaUrl } =
  await import(`data:text/javascript,${encodeURIComponent(stripTypeScriptTypes(source))}`);
const origin = "https://example.supabase.co";
const productId = "20000000-0000-4000-8000-000000000001";
const imageId = "30000000-0000-4000-8000-000000000001";
const storeId = "10000000-0000-4000-8000-000000000001";

test("buyer product projection replaces public URLs without exposing paths", () => {
  const input = { id: productId, title: "Laptop",
    shop_product_images: [{ id: imageId, image_url: "https://old-public/photo", storage_path: "private/path", bucket: "private" }],
    shop_stores: { id: storeId, name: "Store", logo_url: "https://old-public/logo" } };
  const projected = projectBuyerProductMedia(input, origin);
  assert.equal(projected.shop_product_images[0].image_url, reviewedMediaUrl(origin, "image", imageId));
  assert.equal(projected.shop_product_images[0].storage_path, undefined);
  assert.equal(projected.shop_stores.logo_url, reviewedMediaUrl(origin, "store-logo", storeId));
  assert.equal(projected.title, "Laptop");
});

test("cart-style missing image IDs become safe placeholders", () => {
  const projected = projectBuyerProductMedia({ shop_product_images: [{ image_url: "https://old-public/photo" }] }, origin);
  assert.equal(projected.shop_product_images[0].image_url, null);
});

test("order snapshots use current reviewed product cover or a placeholder", () => {
  const order = { id: "order", shop_order_groups: [{ shop_order_items: [
    { product_id: productId, image_snapshot: "https://old-public/order-photo" },
    { image_snapshot: "https://old-public/unlinked" },
  ] }] };
  const projected = projectBuyerOrderMedia(order, origin);
  assert.equal(projected.shop_order_groups[0].shop_order_items[0].image_snapshot,
    reviewedMediaUrl(origin, "product-cover", productId));
  assert.equal(projected.shop_order_groups[0].shop_order_items[1].image_snapshot, null);
});
