export type ReviewedMediaKind = "image" | "product-cover" | "store-logo" | "store-cover";
export type ReviewedMediaLocator = {
  kind: ReviewedMediaKind;
  id: string;
  revision: number;
  bucket: string;
  productId?: string;
  storeId?: string;
  storagePath?: string;
  sourceUrl?: string;
};

export type ReviewedMediaDependencies = {
  enabled: boolean;
  supabaseOrigin: string;
  lookup: (kind: ReviewedMediaKind, id: string) => Promise<ReviewedMediaLocator | null>;
  download: (bucket: string, path: string) => Promise<Blob | null>;
};

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const allowedKinds = new Set<ReviewedMediaKind>(["image", "product-cover", "store-logo", "store-cover"]);
const allowedMime = new Set(["image/jpeg", "image/png", "image/webp"]);
const noStore = {
  "Cache-Control": "private, no-store, max-age=0",
  "Pragma": "no-cache",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
};

function hidden(status: number): Response {
  return new Response(null, { status, headers: noStore });
}

function safePath(value: string): string | null {
  if (!value || value.length > 1024 || value.startsWith("/") || value.includes("\\") || value.includes("%") ||
      value.split("/").some((part) => !part || part === "." || part === ".." || /[\x00-\x1f\x7f]/.test(part))) return null;
  return value;
}

function storageTarget(locator: ReviewedMediaLocator, origin: string): { bucket: string; path: string } | null {
  const expectedBucket = locator.kind === "image" || locator.kind === "product-cover"
    ? "shop-product-images" : "shop-store-assets";
  if (locator.bucket !== expectedBucket) return null;
  if (locator.kind === "image" || locator.kind === "product-cover") {
    const path = safePath(locator.storagePath || "");
    if (path?.startsWith("seller-products/")) {
      const parts = path.split("/");
      if (!locator.productId || !locator.storeId || parts.length < 5 ||
          parts[2].toLowerCase() !== locator.storeId.toLowerCase() ||
          parts[3].toLowerCase() !== locator.productId.toLowerCase()) return null;
    }
    return path ? { bucket: expectedBucket, path } : null;
  }
  try {
    const url = new URL(locator.sourceUrl || "");
    const expected = new URL(origin);
    const prefix = `/storage/v1/object/public/${expectedBucket}/`;
    if (url.origin !== expected.origin || !url.pathname.startsWith(prefix) || url.search || url.hash) return null;
    const path = safePath(decodeURIComponent(url.pathname.slice(prefix.length)));
    if (path && !path.split("/").some((part) => part.toLowerCase() === locator.id.toLowerCase())) return null;
    return path ? { bucket: expectedBucket, path } : null;
  } catch {
    return null;
  }
}

export async function serveReviewedMedia(request: Request, deps: ReviewedMediaDependencies): Promise<Response> {
  if (!deps.enabled) return hidden(404);
  if (request.method !== "GET") return hidden(405);
  const url = new URL(request.url);
  if ([...url.searchParams.keys()].some((key) => !["action", "kind", "id"].includes(key)) ||
      url.searchParams.get("action") !== "reviewed-media") return hidden(404);
  const kind = url.searchParams.get("kind") as ReviewedMediaKind | null;
  const id = url.searchParams.get("id") || "";
  if (url.searchParams.getAll("action").length !== 1 ||
      url.searchParams.getAll("kind").length !== 1 || url.searchParams.getAll("id").length !== 1 ||
      !kind || !allowedKinds.has(kind) || !uuid.test(id)) return hidden(404);
  try {
    const locator = await deps.lookup(kind, id);
    if (!locator || locator.kind !== kind || locator.id.toLowerCase() !== id.toLowerCase() ||
        !Number.isSafeInteger(locator.revision) || locator.revision < 1) return hidden(404);
    const target = storageTarget(locator, deps.supabaseOrigin);
    if (!target) return hidden(404);
    const blob = await deps.download(target.bucket, target.path);
    if (!blob) return hidden(404);
    if (blob.size < 1 || blob.size > 5 * 1024 * 1024 || !allowedMime.has(blob.type.toLowerCase())) return hidden(415);
    const current = await deps.lookup(kind, id);
    if (!current || current.kind !== kind || current.id.toLowerCase() !== id.toLowerCase() ||
        current.revision !== locator.revision ||
        storageTarget(current, deps.supabaseOrigin)?.path !== target.path) return hidden(404);
    return new Response(blob.stream(), {
      status: 200,
      headers: { ...noStore, "Content-Type": blob.type.toLowerCase(), "Content-Length": String(blob.size) },
    });
  } catch {
    return hidden(503);
  }
}

type Row = Record<string, unknown>;
const asRow = (value: unknown): Row | null =>
  value && typeof value === "object" && !Array.isArray(value) ? value as Row : null;

export function reviewedMediaUrl(origin: string, kind: ReviewedMediaKind, id: unknown): string | null {
  if (typeof id !== "string" || !uuid.test(id)) return null;
  try {
    const base = new URL(origin);
    return `${base.origin}/functions/v1/adishop-product-reviews?action=reviewed-media&kind=${kind}&id=${id}`;
  } catch {
    return null;
  }
}

export function projectBuyerProductMedia(value: unknown, origin: string): unknown {
  const product = asRow(value);
  if (!product) return value;
  const images = Array.isArray(product.shop_product_images)
    ? product.shop_product_images.map((value) => {
      const image = asRow(value);
      if (!image) return value;
      const { storage_path: _path, bucket: _bucket, ...publicImage } = image;
      return { ...publicImage, image_url: reviewedMediaUrl(origin, "image", image.id) };
    }) : product.shop_product_images;
  const store = asRow(product.shop_stores);
  return { ...product, shop_product_images: images,
    shop_stores: store ? { ...store,
      logo_url: reviewedMediaUrl(origin, "store-logo", store.id),
      cover_url: reviewedMediaUrl(origin, "store-cover", store.id) } : product.shop_stores };
}

export function projectBuyerOrderMedia(value: unknown, origin: string): unknown {
  const order = asRow(value);
  if (!order) return value;
  return { ...order, shop_order_groups: Array.isArray(order.shop_order_groups)
    ? order.shop_order_groups.map((value) => {
      const group = asRow(value);
      if (!group) return value;
      return { ...group, shop_order_items: Array.isArray(group.shop_order_items)
        ? group.shop_order_items.map((value) => {
          const item = asRow(value);
          if (!item) return value;
          return { ...item,
            image_snapshot: reviewedMediaUrl(origin, "product-cover", item.product_id),
            shop_products: projectBuyerProductMedia(item.shop_products, origin) };
        }) : group.shop_order_items };
    }) : order.shop_order_groups };
}
