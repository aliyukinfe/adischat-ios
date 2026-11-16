import { currentSession } from "./adishop.ts";

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const moderationStatuses = new Set(["pending", "submitted", "under_review", "approved", "rejected", "changes_requested"]);
const productStatuses = new Set(["draft", "active", "archived", "suspended"]);
const stockStatuses = new Set(["in_stock", "low_stock", "out_of_stock"]);

function reply(body: Record<string, unknown>, status = 200): Response {
  return Response.json(body, { status, headers: { "Cache-Control": "no-store" } });
}

function boundedInt(value: string | null, fallback: number, min: number, max: number): number | null {
  if (value === null) return fallback;
  if (!/^\d+$/.test(value)) return null;
  const number = Number(value);
  return Number.isSafeInteger(number) ? Math.max(min, Math.min(max, number)) : null;
}

function safeSearch(value: string | null): string {
  return (value || "").trim().slice(0, 100).replace(/[^\p{L}\p{N} _-]/gu, "").trim();
}

export async function handleAdminAllProducts(request: Request): Promise<Response> {
  if (request.method !== "GET") return reply({ ok: false, error: "method_not_allowed" }, 405);
  try {
    const { client, profileId } = await currentSession(request);
    const session = await client.rpc("adishop_admin_session", { p_profile_id: profileId });
    const permissions = (session.data as { permissions?: string[] } | null)?.permissions || [];
    if (session.error || !permissions.includes("product.moderation.read")) {
      return reply({ ok: false, error: "admin_forbidden" }, 403);
    }

    const params = new URL(request.url).searchParams;
    const limit = boundedInt(params.get("limit"), 30, 1, 100);
    const offset = boundedInt(params.get("offset"), 0, 0, 1000000);
    if (limit === null || offset === null) return reply({ ok: false, error: "invalid_filter" }, 400);
    const status = params.get("status") || "all";
    const category = params.get("category") || "";
    const storeId = params.get("storeId") || "";
    const stock = params.get("stock") || "all";
    const sort = params.get("sort") || "newest";
    const rawSearch = (params.get("search") || "").trim();
    if (rawSearch.length > 100) return reply({ ok: false, error: "invalid_filter" }, 400);
    const search = safeSearch(rawSearch);
    if (status !== "all" && !moderationStatuses.has(status) && !productStatuses.has(status)) return reply({ ok: false, error: "invalid_filter" }, 400);
    if (stock !== "all" && !stockStatuses.has(stock)) return reply({ ok: false, error: "invalid_filter" }, 400);
    if (storeId && !uuidPattern.test(storeId)) return reply({ ok: false, error: "invalid_filter" }, 400);
    if (!new Set(["newest", "oldest", "updated"]).has(sort) || category.length > 120) return reply({ ok: false, error: "invalid_filter" }, 400);

    let query = client.from("shop_products")
      .select("id,store_id,title,status,moderation_status,price_minor,currency,stock_status,category_slug,created_at,updated_at,submitted_at,aggregate_available_quantity", { count: "exact" });
    query = query.is("deleted_at", null);
    if (status !== "all") query = query.eq(productStatuses.has(status) ? "status" : "moderation_status", status);
    if (category) query = query.eq("category_slug", category);
    if (storeId) query = query.eq("store_id", storeId);
    if (stock !== "all") query = query.eq("stock_status", stock);

    if (uuidPattern.test(rawSearch)) {
      query = query.eq("id", rawSearch);
    } else if (rawSearch && search !== rawSearch) {
      const [products, variants] = await Promise.all([
        client.from("shop_products").select("id").eq("seller_sku", rawSearch).limit(101),
        client.from("shop_product_variants").select("product_id").eq("sku", rawSearch).limit(101),
      ]);
      if (products.error || variants.error) return reply({ ok: false, error: "product_search_unavailable" }, 503);
      if ((products.data || []).length > 100 || (variants.data || []).length > 100) return reply({ ok: false, error: "search_too_broad" }, 422);
      const ids = [...new Set([
        ...(products.data || []).map((row) => String(row.id)),
        ...(variants.data || []).map((row) => String(row.product_id)),
      ].filter((id) => uuidPattern.test(id)))];
      if (!ids.length) return reply({ ok: true, products: [], total: 0, limit, offset });
      query = query.in("id", ids);
    } else if (search) {
      const [stores, variants] = await Promise.all([
        client.from("shop_stores").select("id").ilike("name", `%${search}%`).limit(500),
        client.from("shop_product_variants").select("product_id").ilike("sku", `%${search}%`).limit(500),
      ]);
      if (stores.error || variants.error) return reply({ ok: false, error: "product_search_unavailable" }, 503);
      if ((stores.data || []).length === 500 || (variants.data || []).length === 500) return reply({ ok: false, error: "search_too_broad" }, 422);
      const storeIds = [...new Set((stores.data || []).map((row) => String(row.id)).filter((id) => uuidPattern.test(id)))];
      const productIds = [...new Set((variants.data || []).map((row) => String(row.product_id)).filter((id) => uuidPattern.test(id)))];
      const alternatives = [`title.ilike.%${search}%`, `seller_sku.ilike.%${search}%`];
      if (storeIds.length) alternatives.push(`store_id.in.(${storeIds.join(",")})`);
      if (productIds.length) alternatives.push(`id.in.(${productIds.join(",")})`);
      if (storeIds.length + productIds.length > 100) return reply({ ok: false, error: "search_too_broad" }, 422);
      query = query.or(alternatives.join(","));
    }

    const orderColumn = sort === "updated" ? "updated_at" : "created_at";
    const page = await query.order(orderColumn, { ascending: sort === "oldest" }).order("id", { ascending: true }).range(offset, offset + limit - 1);
    if (page.error) return reply({ ok: false, error: "products_unavailable" }, 503);
    const rows = page.data || [];
    const productIds = rows.map((row) => String(row.id));
    const storeIds = [...new Set(rows.map((row) => String(row.store_id)))];
    const [stores, images] = await Promise.all([
      storeIds.length ? client.from("shop_stores").select("id,name").in("id", storeIds) : Promise.resolve({ data: [], error: null }),
      productIds.length ? client.from("shop_product_images").select("product_id,image_url,is_primary,display_order").in("product_id", productIds).is("deleted_at", null).order("is_primary", { ascending: false }).order("display_order", { ascending: true }) : Promise.resolve({ data: [], error: null }),
    ]);
    if (stores.error || images.error) return reply({ ok: false, error: "products_unavailable" }, 503);
    const storeNames = new Map((stores.data || []).map((row) => [String(row.id), String(row.name)]));
    const imageUrls = new Map<string, string>();
    for (const image of images.data || []) {
      const id = String(image.product_id);
      if (!imageUrls.has(id)) imageUrls.set(id, String(image.image_url || ""));
    }
    return reply({
      ok: true,
      products: rows.map((row) => ({ ...row, store_name: storeNames.get(String(row.store_id)) || null, image_url: imageUrls.get(String(row.id)) || null })),
      total: page.count ?? null,
      limit,
      offset,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message.toLowerCase() : "";
    const authFailure = ["missing-session", "invalid-session", "expired", "bad-token", "bad-signature", "bad-claims"].some((code) => message.includes(code));
    return authFailure
      ? reply({ ok: false, error: "auth_required" }, 401)
      : reply({ ok: false, error: "products_unavailable" }, 503);
  }
}
