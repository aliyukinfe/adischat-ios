// Local QA transport only. No checkout, order mutation, or media delivery.
import { currentSession } from "../functions/_shared/adishop.ts";

type Row = Record<string, unknown>;
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const json = (value: Row, status = 200) => new Response(JSON.stringify(value), {
  status, headers: { "content-type": "application/json", "cache-control": "private, no-store" },
});
const denied = (status = 404) => json({ ok: false, error: "Unavailable." }, status);

function verifiedJti(request: Request): string | null {
  const token = request.headers.get("authorization")?.replace(/^Bearer /, "") || "";
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.jti === "string" && payload.jti.length < 160 ? payload.jti : null;
  } catch { return null; }
}

async function roles(client: ReturnType<typeof import("../functions/_shared/adishop.ts").adminClient>, profileId: string) {
  const { data, error } = await client.from("shop_user_roles")
    .select("shop_roles(code)").eq("profile_id", profileId).is("revoked_at", null);
  if (error) throw new Error("role-lookup-failed");
  return new Set((data || []).map((row: Row) => {
    const role = row.shop_roles as Row | Row[] | null;
    return String(Array.isArray(role) ? role[0]?.code || "" : role?.code || "");
  }));
}

async function sellerStore(client: ReturnType<typeof import("../functions/_shared/adishop.ts").adminClient>,
  profileId: string, storeId: string): Promise<boolean> {
  const { data: store, error } = await client.from("shop_stores")
    .select("id,owner_profile_id,status,approval_status").eq("id", storeId).maybeSingle();
  if (error || !store || store.status !== "active" || store.approval_status !== "approved") return false;
  if (store.owner_profile_id === profileId) return true;
  const member = await client.from("shop_store_members").select("id")
    .eq("store_id", storeId).eq("profile_id", profileId).eq("status", "active").maybeSingle();
  return !member.error && Boolean(member.data);
}

async function handle(request: Request): Promise<Response> {
  const backend = Deno.env.get("SUPABASE_URL") || "";
  if (Deno.env.get("ADISHOP_QA_READ_ENABLED") !== "true" ||
      !/^http:\/\/127\.0\.0\.1:\d+$/.test(backend)) return denied();
  if (request.method !== "GET" && request.method !== "POST") return denied(405);
  const url = new URL(request.url);
  if (["profileId", "userId", "role", "sellerId"].some((key) => url.searchParams.has(key))) return denied(400);

  let rawAction = url.pathname.replace(/^\/+/, "");
  if (rawAction.startsWith("functions/v1/adishop-")) {
    rawAction = rawAction.slice("functions/v1/adishop-".length);
  }

  // Strict guard: strictly reject checkout and order-mutation enablement in QA
  const blockedMutations = [
    "create-checkout", "confirm-checkout", "request-order-cancellation",
    "seller-accept-order-group", "seller-reject-order-group", "seller-mark-processing",
    "seller-mark-packed", "seller-hand-to-carrier", "reveal-delivery-code",
    "seller-verify-delivery-code"
  ];
  if (blockedMutations.includes(rawAction)) {
    return denied(405);
  }

  let action = rawAction;
  let simulatedAction: string | null = null;

  if (rawAction === "get-cart") {
    action = "cart";
  } else if (rawAction === "add-cart-item") {
    action = "cart";
    simulatedAction = "add";
  } else if (rawAction === "update-cart-item") {
    action = "cart";
    simulatedAction = "update";
  } else if (rawAction === "remove-cart-item") {
    action = "cart";
    simulatedAction = "remove";
  } else if (rawAction === "clear-cart") {
    action = "cart";
    simulatedAction = "clear";
  } else if (rawAction === "list-favorites") {
    action = "favorites";
  } else if (rawAction === "add-favorite") {
    action = "favorites";
    simulatedAction = "add";
  } else if (rawAction === "remove-favorite") {
    action = "favorites";
    simulatedAction = "remove";
  } else if (rawAction === "list-addresses") {
    action = "addresses";
  } else if (rawAction === "create-address") {
    action = "addresses";
    simulatedAction = "create";
  } else if (rawAction === "update-address") {
    action = "addresses";
    simulatedAction = "update";
  } else if (rawAction === "delete-address") {
    action = "addresses";
    simulatedAction = "delete";
  } else if (rawAction === "set-default-address") {
    action = "addresses";
    simulatedAction = "set_default";
  } else if (rawAction === "get-seller-application-status") {
    action = "seller-application";
  } else if (rawAction === "seller-product-detail") {
    action = "seller-product-detail";
  } else if (rawAction === "list-seller-order-groups") {
    action = "seller-orders";
  } else if (rawAction === "get-seller-order-group") {
    action = "seller-order";
  } else if (rawAction === "list-orders") {
    action = "buyer-orders";
  } else if (rawAction === "get-order") {
    action = "buyer-order";
  }

  const validActions = [
    "cart", "favorites", "addresses", "seller-application", "seller-products",
    "seller-product-detail", "buyer-orders", "buyer-order", "seller-orders", "seller-order",
    "review-queue", "review-decision", "image-review", "seller-product"
  ];
  if (!validActions.includes(action)) return denied();
  if (request.method === "POST" && !["cart", "favorites", "addresses", "review-decision", "image-review", "seller-product"].includes(action)) return denied(405);
  if (request.method === "GET" && ["review-decision", "image-review", "seller-product"].includes(action)) return denied(405);

  try {
    // currentSession verifies the signed Shop JWT before its JTI is read below.
    const { client, profileId } = await currentSession(request);
    const jti = verifiedJti(request);
    if (!jti) return denied(401);
    const scope = await client.rpc("adishop_qa_active_read_scope", { p_session_jti: jti });
    if (scope.error || (scope.data as Row | null)?.profileId !== profileId) return denied(401);

    if (request.method === "POST") {
      let body = await request.json().catch(() => ({}));
      if (!body || typeof body !== "object" || Array.isArray(body) ||
          ["profileId", "userId", "role", "sellerId", "sessionJti"].some((key) => key in body)) return denied(400);
      if (simulatedAction) {
        body = { ...body, action: simulatedAction };
      }
      if (action === "cart") {
        if (!["add", "update", "remove", "clear"].includes(body.action)) return denied(400);
        const result = await client.rpc("adishop_qa_cart_action", {
          p_session_jti: jti, p_action: body.action,
          p_product_id: body.productId || null, p_variant_id: body.variantId || null,
          p_item_id: body.itemId || null, p_quantity: body.quantity ?? null,
        });
        if (result.error) return denied(result.error.message.includes("invalid-session") ? 401 : 409);
        return json({ ok: true, cart: result.data as Row });
      }
      if (action === "favorites") {
        if (!["add", "remove"].includes(body.action) || typeof body.productId !== "string" ||
            !uuid.test(body.productId)) return denied(400);
        const result = await client.rpc("adishop_qa_favorite_action", {
          p_session_jti: jti, p_action: body.action, p_product_id: body.productId,
        });
        if (result.error) return denied(result.error.code === "28000" ? 401 : 400);
        return json({ ok: true, favorite: result.data as Row });
      }
      if (action === "addresses") {
        if (!["create", "update", "delete", "set_default"].includes(body.action) ||
            (body.action !== "create" && (typeof body.id !== "string" || !uuid.test(body.id)))) return denied(400);
        const result = await client.rpc("adishop_qa_address_action", {
          p_session_jti: jti, p_action: body.action, p_address_id: body.id || null,
          p_label: body.label ?? null, p_recipient_name: body.recipientName ?? null,
          p_phone: body.phone ?? null, p_city: body.city ?? null,
          p_address_line1: body.addressLine1 ?? null,
          p_address_line2: body.addressLine2 ?? null,
          p_is_default: body.isDefault === true,
        });
        if (result.error) return denied(result.error.code === "28000" ? 401 :
          result.error.message.includes("address-not-found") ? 404 : 400);
        return json({ ok: true, address: result.data as Row });
      }
      if (action === "seller-product") {
        const result = await client.rpc("adishop_qa_seller_product_edit", {
          p_session_jti: jti, p_store_id: body.storeId || null,
          p_product_id: body.productId || null,
          p_expected_updated_at: body.expectedUpdatedAt || null,
          p_action: body.action || null, p_title: body.title || null,
          p_price_minor: body.priceMinor ?? null, p_variant_id: body.variantId || null,
        });
        if (result.error) return denied(result.error.message.includes("stale-revision") ? 409 :
          result.error.code === "42501" ? 403 : 400);
        return json({ ok: true, product: result.data as Row });
      }
      const granted = await roles(client, profileId);
      if (!granted.has("SHOP_ADMIN") && !granted.has("SHOP_SUPER_ADMIN")) return denied(403);
      if (action === "image-review") {
        const result = await client.rpc("adishop_qa_review_image", {
          p_session_jti: jti, p_image_id: body.imageId || null,
          p_expected_revision: body.expectedRevision ?? null,
          p_decision: body.decision || null, p_alt_text: body.altText || null,
        });
        if (result.error) return denied(result.error.message.includes("stale-revision") ? 409 :
          result.error.code === "42501" ? 403 : 400);
        return json({ ok: true, review: result.data as Row });
      }
      const result = await client.rpc("adishop_qa_review_product", {
        p_session_jti: jti, p_product_id: body.productId || null,
        p_expected_updated_at: body.expectedUpdatedAt || null,
        p_decision: body.decision || null, p_public_title: body.publicTitle || null,
        p_public_short_description: body.publicShortDescription || null,
      });
      if (result.error) return denied(result.error.message.includes("stale-revision") ? 409 :
        result.error.code === "42501" ? 403 : 400);
      return json({ ok: true, review: result.data as Row });
    }

    if (action === "cart") {
      const result = await client.rpc("adishop_qa_cart_action", {
        p_session_jti: jti,
        p_action: "read",
      });
      if (result.error) return denied(503);
      return json({ ok: true, cart: (result.data as Row | null) || null });
    }
    if (action === "favorites") {
      const result = await client.from("shop_favorites")
        .select("id,product_id,created_at")
        .eq("profile_id", profileId).order("created_at", { ascending: false });
      if (result.error) return denied(503);
      const rows = (result.data || []) as Row[];
      const productIds = rows.map((r) => r.product_id as string).filter(Boolean);
      let catMap = new Map<string, Row>();
      if (productIds.length > 0) {
        const catRes = await client.from("shop_qa_public_catalog")
          .select("id,title,short_description,price_minor,currency,stock_status,store_id,store_name")
          .in("id", productIds);
        if (!catRes.error && catRes.data) {
          catMap = new Map((catRes.data as Row[]).map((p: Row) => [p.id as string, p]));
        }
      }
      const mapped = rows.map((r) => {
        const cat = catMap.get(r.product_id as string);
        return {
          id: r.id,
          product_id: r.product_id,
          created_at: r.created_at,
          shop_products: cat ? {
            id: cat.id,
            title: cat.title,
            short_description: cat.short_description,
            price_minor: cat.price_minor,
            currency: cat.currency,
            stock_status: cat.stock_status,
            shop_stores: cat.store_name ? { id: cat.store_id, name: cat.store_name } : null,
            shop_product_images: [],
          } : null,
        };
      });
      return json({ ok: true, favorites: mapped });
    }
    if (action === "addresses") {
      const result = await client.from("shop_addresses")
        .select("id,label,recipient_name,phone,city,address_line1,address_line2,is_default")
        .eq("profile_id", profileId).is("deleted_at", null).order("created_at");
      return result.error ? denied(503) : json({ ok: true, addresses: result.data || [] });
    }
    if (action === "seller-application") {
      const result = await client.from("shop_seller_applications")
        .select("id,proposed_store_name,business_type,status,submitted_at,rejection_reason")
        .eq("profile_id", profileId);
      if (result.error) return denied(503);
      const rows = (result.data || []) as Row[];
      const app = rows[0] || null;
      return json({
        ok: true,
        status: (app?.status as string) || "not_started",
        application: app,
        "seller-application": rows,
      });
    }
    if (action === "buyer-orders") {
      const result = await client.from("shop_orders")
        .select("id,order_number,status,fulfillment_status,delivery_status,payment_status,total_minor,created_at")
        .eq("profile_id", profileId).order("created_at", { ascending: false }).limit(50);
      return result.error ? denied(503) : json({ ok: true, orders: result.data || [] });
    }
    if (action === "buyer-order") {
      const orderId = url.searchParams.get("orderId") || "";
      if (!uuid.test(orderId)) return denied(400);
      const result = await client.from("shop_orders")
        .select("id,order_number,status,fulfillment_status,delivery_status,payment_status,total_minor,created_at")
        .eq("id", orderId).eq("profile_id", profileId).maybeSingle();
      if (result.error) return denied(503);
      if (!result.data) return denied(404);
      const groups = await client.from("shop_order_groups")
        .select("id,order_id,store_id,status,fulfillment_status,seller_response_status,created_at")
        .eq("order_id", orderId);
      const orderData = result.data as Row;
      return json({ ok: true, order: { ...orderData, shop_order_groups: groups.data || [] } });
    }
    if (action === "review-queue") {
      const granted = await roles(client, profileId);
      if (!granted.has("SHOP_ADMIN") && !granted.has("SHOP_SUPER_ADMIN")) return denied(403);
      const result = await client.from("shop_products")
        .select("id,store_id,title,status,moderation_status,updated_at")
        .order("updated_at", { ascending: false }).limit(50);
      return result.error ? denied(503) : json({ ok: true, products: result.data || [] });
    }

    // Seller-scoped routes
    const granted = await roles(client, profileId);
    if (!granted.has("SELLER") && !granted.has("SHOP_ADMIN") && !granted.has("SHOP_SUPER_ADMIN")) return denied(403);
    const storeId = url.searchParams.get("storeId") || "";
    if (!uuid.test(storeId) || !(await sellerStore(client, profileId, storeId))) return denied(404);

    if (action === "seller-products") {
      const result = await client.from("shop_products")
        .select("id,store_id,title,status,moderation_status,price_minor,updated_at")
        .eq("store_id", storeId).is("deleted_at", null).order("updated_at", { ascending: false }).limit(50);
      return result.error ? denied(503) : json({ ok: true, products: result.data || [], sellerProducts: result.data || [] });
    }
    if (action === "seller-product-detail") {
      const productId = url.searchParams.get("productId") || "";
      if (!uuid.test(productId)) return denied(400);
      const result = await client.from("shop_products")
        .select("id,store_id,title,status,moderation_status,price_minor,updated_at")
        .eq("store_id", storeId).eq("id", productId).is("deleted_at", null).maybeSingle();
      return result.error ? denied(503) : result.data ? json({ ok: true, product: result.data, sellerProduct: result.data }) : denied(404);
    }
    if (action === "seller-order") {
      const groupId = url.searchParams.get("orderGroupId") || "";
      if (!uuid.test(groupId)) return denied(400);
      const result = await client.from("shop_order_groups")
        .select("id,order_id,store_id,status,fulfillment_status,seller_response_status,created_at")
        .eq("id", groupId).eq("store_id", storeId).maybeSingle();
      return result.error ? denied(503) : result.data ? json({ ok: true, orderGroup: result.data, sellerOrderGroup: result.data }) : denied(404);
    }
    const groups = await client.from("shop_order_groups")
      .select("id,order_id,store_id,status,fulfillment_status,seller_response_status,created_at")
      .eq("store_id", storeId).order("created_at", { ascending: false }).limit(50);
    return groups.error ? denied(503) : json({ ok: true, orderGroups: groups.data || [], sellerOrderGroups: groups.data || [] });
  } catch {
    return denied(401);
  }
}

Deno.serve({ hostname: "127.0.0.1", port: 55453 }, handle);
