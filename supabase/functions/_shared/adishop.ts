import { createClient } from "https://esm.sh/@supabase/supabase-js@2.110.7";

const encoder = new TextEncoder();
const decoder = new TextDecoder();

type Json = Record<string, unknown>;

function response(body: Json, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

function safeError(message = "We couldn't sync your Shop account.", status = 400) {
  return response({ ok: false, error: message }, status);
}

function correlationId() {
  return randomId("corr");
}

function structuredError(code: string, message: string, status = 400, retryable = false) {
  return response({
    ok: false,
    error: {
      code,
      message,
      retryable,
      correlation_id: correlationId(),
    },
  }, status);
}

function base64UrlToBytes(value: string) {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const raw = atob(padded);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i += 1) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

function base64Url(bytes: Uint8Array | ArrayBuffer) {
  const raw = btoa(String.fromCharCode(...new Uint8Array(bytes)));
  return raw.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlJson(value: string) {
  try {
    return JSON.parse(decoder.decode(base64UrlToBytes(value)));
  } catch {
    throw new Error("bad-token");
  }
}

async function hmacKey(secret: string, usage: KeyUsage[]) {
  return crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, usage);
}

async function verifyJwt(token: string, secret: string, audience: string, issuer: string) {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("bad-token");
  const header = base64UrlJson(parts[0]);
  const payload = base64UrlJson(parts[1]);
  if (header.alg !== "HS256") throw new Error("bad-alg");
  const valid = await crypto.subtle.verify(
    "HMAC",
    await hmacKey(secret, ["verify"]),
    base64UrlToBytes(parts[2]),
    encoder.encode(`${parts[0]}.${parts[1]}`),
  );
  if (!valid) throw new Error("bad-signature");
  const now = Math.floor(Date.now() / 1000);
  if (payload.aud !== audience || payload.iss !== issuer || !payload.sub) throw new Error("bad-claims");
  if (Number(payload.nbf || 0) > now + 30 || Number(payload.exp || 0) <= now) throw new Error("expired");
  return payload;
}

async function signJwt(payload: Json, secret: string) {
  const header = { alg: "HS256", typ: "JWT" };
  const encodedHeader = base64Url(encoder.encode(JSON.stringify(header)));
  const encodedPayload = base64Url(encoder.encode(JSON.stringify(payload)));
  const signingInput = `${encodedHeader}.${encodedPayload}`;
  const signature = await crypto.subtle.sign("HMAC", await hmacKey(secret, ["sign"]), encoder.encode(signingInput));
  return `${signingInput}.${base64Url(signature)}`;
}

function randomId(prefix: string) {
  const bytes = new Uint8Array(18);
  crypto.getRandomValues(bytes);
  return `${prefix}_${base64Url(bytes)}`;
}

async function sha256(value: string) {
  return base64Url(await crypto.subtle.digest("SHA-256", encoder.encode(value)));
}

function adminClient() {
  const url = Deno.env.get("SUPABASE_URL") || "";
  const key = Deno.env.get("ADISHOP_SUPABASE_SECRET_KEY") || "";
  if (!url || !key) throw new Error("missing-supabase-env");
  return createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } });
}

async function jsonBody(request: Request) {
  if (request.method === "GET") return {};
  return await request.json().catch(() => ({}));
}

async function currentSession(request: Request) {
  const authorization = request.headers.get("authorization") || "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
  if (!token) throw new Error("missing-session");
  const payload = await verifyJwt(
    token,
    Deno.env.get("ADISHOP_SHOP_SESSION_SECRET") || "",
    "adishop-native",
    "adishop-edge",
  );
  const client = adminClient();
  const { data: session, error } = await client
    .from("shop_sessions")
    .select("id,profile_id,expires_at,revoked_at,shop_profiles(id,status,display_name,email,phone,adischat_user_id)")
    .eq("session_jti", payload.jti)
    .is("revoked_at", null)
    .gt("expires_at", new Date().toISOString())
    .single();
  if (error || !session || (session.shop_profiles as Json)?.status !== "active") throw new Error("invalid-session");
  return { client, profileId: session.profile_id as string, profile: session.shop_profiles as Json };
}

async function audit(client: ReturnType<typeof adminClient>, profileId: string | null, action: string, entityType: string, entityId?: string, metadata: Json = {}) {
  await client.from("shop_audit_logs").insert({
    actor_profile_id: profileId,
    action,
    entity_type: entityType,
    entity_id: entityId || null,
    metadata,
  });
}

async function issueSession(client: ReturnType<typeof adminClient>, profileId: string) {
  const now = Math.floor(Date.now() / 1000);
  const sessionJti = randomId("session");
  const refreshToken = randomId("refresh");
  const refreshHash = await sha256(refreshToken);
  const accessToken = await signJwt({
    iss: "adishop-edge",
    aud: "adishop-native",
    sub: profileId,
    jti: sessionJti,
    iat: now,
    nbf: now - 15,
    exp: now + 15 * 60,
  }, Deno.env.get("ADISHOP_SHOP_SESSION_SECRET") || "");

  const { error } = await client.from("shop_sessions").insert({
    profile_id: profileId,
    session_jti: sessionJti,
    refresh_token_hash: refreshHash,
    expires_at: new Date((now + 15 * 60) * 1000).toISOString(),
    refresh_expires_at: new Date((now + 30 * 24 * 60 * 60) * 1000).toISOString(),
  });
  if (error) throw error;
  return { accessToken, refreshToken, expiresAt: (now + 15 * 60) * 1000 };
}

async function exchange(request: Request) {
  const body = await jsonBody(request);
  const assertion = String(body.assertion || "");
  const payload = await verifyJwt(
    assertion,
    Deno.env.get("ADISHOP_ASSERTION_SECRET") || "",
    Deno.env.get("ADISHOP_ASSERTION_AUDIENCE") || "adishop-edge",
    Deno.env.get("ADISHOP_ASSERTION_ISSUER") || "adischat-api",
  ).catch(() => null);
  if (!payload?.jti || !payload.sub) return safeError("We couldn't sync your Shop account.", 401);

  const client = adminClient();
  const replay = await client.from("shop_identity_assertion_replays").insert({
    jti: payload.jti,
    adischat_user_id: payload.sub,
    expires_at: new Date(Number(payload.exp) * 1000).toISOString(),
  });
  if (replay.error) return safeError("We couldn't sync your Shop account.", 401);

  const profileUpsert = await client
    .from("shop_profiles")
    .upsert({
      adischat_user_id: payload.sub,
      display_name: payload.display_name || null,
      email: payload.email || null,
      status: "active",
      updated_at: new Date().toISOString(),
    }, { onConflict: "adischat_user_id" })
    .select("id,display_name,email,phone,status,adischat_user_id")
    .single();
  if (profileUpsert.error || !profileUpsert.data) return safeError("We couldn't sync your Shop account.", 500);

  const profileId = profileUpsert.data.id;
  const link = await client.from("shop_user_links").upsert({
    shop_profile_id: profileId,
    provider: payload.provider || "firebase",
    provider_user_id: payload.sub,
    external_subject: payload.sub,
    account_status: "active",
    display_name: payload.display_name || null,
    avatar_url: payload.avatar_url || null,
    verified_email: payload.email || null,
    verified_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
    last_synced_at: new Date().toISOString(),
  }, { onConflict: "provider,provider_user_id" });
  if (link.error) return safeError("We couldn't sync your Shop account.", 500);

  await audit(client, profileId, "identity.exchange", "shop_profile", profileId, { provider: payload.provider || "firebase" });
  return response({ ok: true, profile: profileUpsert.data, session: await issueSession(client, profileId) });
}

async function refresh(request: Request) {
  const body = await jsonBody(request);
  const refreshToken = String(body.refreshToken || "");
  if (!refreshToken) return safeError("Please sign in again.", 401);
  const client = adminClient();
  const { data: session, error } = await client
    .from("shop_sessions")
    .select("id,profile_id,refresh_expires_at,revoked_at")
    .eq("refresh_token_hash", await sha256(refreshToken))
    .is("revoked_at", null)
    .gt("refresh_expires_at", new Date().toISOString())
    .single();
  if (error || !session) return safeError("Please sign in again.", 401);
  await client.from("shop_sessions").update({ revoked_at: new Date().toISOString() }).eq("id", session.id);
  return response({ ok: true, session: await issueSession(client, session.profile_id) });
}

async function getCurrentProfile(request: Request) {
  const { profile } = await currentSession(request);
  return response({ ok: true, profile });
}

async function getCart(request: Request) {
  const { client, profileId } = await currentSession(request);
  const cart = await ensureCart(client, profileId);
  const { data, error } = await client
    .from("shop_cart_items")
    .select("id,quantity,unit_price_minor,product_id,variant_id,shop_products(id,title,price_minor,currency,stock_status,shop_product_images(image_url,is_primary,display_order),shop_stores(name,status,approval_status)),shop_product_variants(id,title,price_minor,status)")
    .eq("cart_id", cart.id)
    .order("created_at");
  if (error) return safeError("Your cart is temporarily unavailable.", 500);
  return response({ ok: true, cart: { ...cart, items: data || [], totals: totals(data || []) } });
}

async function ensureCart(client: ReturnType<typeof adminClient>, profileId: string) {
  const existing = await client.from("shop_carts").select("*").eq("profile_id", profileId).eq("status", "active").eq("currency", "ETB").maybeSingle();
  if (existing.data) return existing.data;
  const created = await client.from("shop_carts").insert({ profile_id: profileId, currency: "ETB", status: "active" }).select("*").single();
  if (created.error) throw created.error;
  return created.data;
}

function totals(items: Json[]) {
  const subtotalMinor = items.reduce((sum, item) => sum + Number(item.unit_price_minor || 0) * Number(item.quantity || 0), 0);
  return { subtotalMinor, discountMinor: 0, deliveryEstimateMinor: 0, taxMinor: 0, totalMinor: subtotalMinor };
}

async function addCartItem(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const productId = String(body.productId || "");
  const variantId = body.variantId ? String(body.variantId) : null;
  const quantity = Math.max(1, Math.min(99, Number(body.quantity || 1)));
  if (!productId) return safeError("Check the product and try again.");

  const productResult = await client
    .from("shop_products")
    .select("id,price_minor,status,moderation_status,stock_status,shop_stores(status,approval_status),shop_inventory(quantity_available,quantity_reserved)")
    .eq("id", productId)
    .single();
  const product = productResult.data as Json | null;
  const store = product?.shop_stores as Json | undefined;
  const inventory = Array.isArray(product?.shop_inventory) ? product.shop_inventory[0] as Json : null;
  const available = Number(inventory?.quantity_available || 0) - Number(inventory?.quantity_reserved || 0);
  if (productResult.error || !product || product.status !== "active" || product.moderation_status !== "approved" || store?.status !== "active" || store?.approval_status !== "approved" || available < quantity) {
    return safeError("This product is temporarily unavailable.", 409);
  }
  const cart = await ensureCart(client, profileId);
  let existingQuery = client.from("shop_cart_items").select("id,quantity").eq("cart_id", cart.id).eq("product_id", productId);
  existingQuery = variantId ? existingQuery.eq("variant_id", variantId) : existingQuery.is("variant_id", null);
  const existing = await existingQuery.maybeSingle();
  if (existing.data) {
    const nextQuantity = Math.min(available, Number(existing.data.quantity || 0) + quantity);
    await client.from("shop_cart_items").update({ quantity: nextQuantity, unit_price_minor: product.price_minor, updated_at: new Date().toISOString() }).eq("id", existing.data.id);
  } else {
    await client.from("shop_cart_items").insert({ cart_id: cart.id, product_id: productId, variant_id: variantId, quantity, unit_price_minor: product.price_minor });
  }
  await audit(client, profileId, "cart.add_item", "shop_product", productId, { quantity });
  return getCart(request);
}

async function updateCartItem(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const itemId = String(body.itemId || "");
  const quantity = Math.max(1, Math.min(99, Number(body.quantity || 1)));
  const cart = await ensureCart(client, profileId);
  if (!itemId) return safeError("Check the cart item and try again.");
  const item = await client.from("shop_cart_items").select("id").eq("id", itemId).eq("cart_id", cart.id).maybeSingle();
  if (!item.data) return safeError("Cart item not found.", 404);
  await client.from("shop_cart_items").update({ quantity, updated_at: new Date().toISOString() }).eq("id", itemId).eq("cart_id", cart.id);
  await audit(client, profileId, "cart.update_item", "shop_cart_item", itemId, { quantity });
  return getCart(request);
}

async function removeCartItem(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const itemId = String(body.itemId || "");
  const cart = await ensureCart(client, profileId);
  if (!itemId) return safeError("Check the cart item and try again.");
  const item = await client.from("shop_cart_items").select("id").eq("id", itemId).eq("cart_id", cart.id).maybeSingle();
  if (!item.data) return safeError("Cart item not found.", 404);
  await client.from("shop_cart_items").delete().eq("id", itemId).eq("cart_id", cart.id);
  await audit(client, profileId, "cart.remove_item", "shop_cart_item", itemId);
  return getCart(request);
}

async function clearCart(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const cart = await ensureCart(client, profileId);
  if (body.cartId && String(body.cartId) !== String(cart.id)) return safeError("Cart not found.", 404);
  await client.from("shop_cart_items").delete().eq("cart_id", cart.id);
  await audit(client, profileId, "cart.clear", "shop_cart", cart.id);
  return getCart(request);
}

async function favorite(request: Request, add: boolean) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const productId = String(body.productId || "");
  const favoriteId = String(body.favoriteId || "");
  if (!productId && (add || !favoriteId)) return safeError("Check the product and try again.");
  let result: Json = { ok: true };
  if (add) {
    const favoriteResult = await client
      .from("shop_favorites")
      .upsert({ profile_id: profileId, product_id: productId }, { onConflict: "profile_id,product_id" })
      .select("id,product_id")
      .single();
    if (favoriteResult.error || !favoriteResult.data) return safeError("We couldn't update favorites.", 500);
    result = { ok: true, favorite: favoriteResult.data };
  } else if (favoriteId) {
    const favoriteRow = await client.from("shop_favorites").select("id").eq("id", favoriteId).eq("profile_id", profileId).maybeSingle();
    if (!favoriteRow.data) return safeError("Favorite not found.", 404);
    await client.from("shop_favorites").delete().eq("id", favoriteId).eq("profile_id", profileId);
  } else {
    await client.from("shop_favorites").delete().eq("profile_id", profileId).eq("product_id", productId);
  }
  await audit(client, profileId, add ? "favorite.add" : "favorite.remove", favoriteId ? "shop_favorite" : "shop_product", favoriteId || productId);
  return response(result);
}

async function listFavorites(request: Request) {
  const { client, profileId } = await currentSession(request);
  const { data, error } = await client
    .from("shop_favorites")
    .select("id,product_id,created_at,shop_products(id,title,slug,brand,category_slug,price_minor,compare_at_price_minor,discount_percent,rating_average,rating_count,completed_sales_count,stock_status,shop_product_images(image_url,is_primary,display_order,alt_text),shop_stores(name,slug,approval_status,status))")
    .eq("profile_id", profileId)
    .order("created_at", { ascending: false });
  if (error) return safeError("We couldn't load saved products.", 500);
  return response({ ok: true, favorites: data || [] });
}

async function recordView(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const productId = String(body.productId || "");
  if (!productId) return safeError("Check the product and try again.");
  await client.from("shop_recently_viewed").upsert({ profile_id: profileId, product_id: productId, viewed_at: new Date().toISOString() }, { onConflict: "profile_id,product_id" });
  return response({ ok: true });
}

async function listRecentlyViewed(request: Request) {
  const { client, profileId } = await currentSession(request);
  const { data, error } = await client
    .from("shop_recently_viewed")
    .select("id,product_id,viewed_at,shop_products(id,title,slug,brand,category_slug,price_minor,compare_at_price_minor,discount_percent,rating_average,rating_count,completed_sales_count,stock_status,shop_product_images(image_url,is_primary,display_order,alt_text),shop_stores(name,slug,approval_status,status))")
    .eq("profile_id", profileId)
    .order("viewed_at", { ascending: false })
    .limit(30);
  if (error) return safeError("We couldn't load recently viewed products.", 500);
  return response({ ok: true, recentlyViewed: data || [] });
}

async function listAddresses(request: Request) {
  const { client, profileId } = await currentSession(request);
  const { data, error } = await client.from("shop_addresses").select("*").eq("profile_id", profileId).is("deleted_at", null).order("is_default", { ascending: false });
  if (error) return safeError("We couldn't load your addresses.", 500);
  return response({ ok: true, addresses: data || [] });
}

async function upsertAddress(request: Request, mode: "create" | "update") {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const payload = {
    profile_id: profileId,
    label: String(body.label || "Home").slice(0, 60),
    recipient_name: String(body.fullName || body.recipientName || "").trim().slice(0, 120),
    phone: String(body.phone || "").trim().slice(0, 32),
    city: String(body.city || "").trim().slice(0, 80),
    address_line1: String(body.addressLine1 || body.street || "").trim().slice(0, 200),
    address_line2: String(body.addressLine2 || body.landmark || "").trim().slice(0, 200) || null,
    is_default: Boolean(body.isDefault),
    updated_at: new Date().toISOString(),
  };
  if (!payload.recipient_name || !payload.phone || !payload.city || !payload.address_line1) return safeError("Check the address details and try again.");
  if (payload.is_default) await client.from("shop_addresses").update({ is_default: false }).eq("profile_id", profileId).is("deleted_at", null);
  const result = mode === "create"
    ? await client.from("shop_addresses").insert(payload).select("*").single()
    : await client.from("shop_addresses").update(payload).eq("id", String(body.id || "")).eq("profile_id", profileId).select("*").single();
  if (result.error) return safeError(mode === "update" ? "Address not found." : "We couldn't save your address.", mode === "update" ? 404 : 500);
  await audit(client, profileId, `address.${mode}`, "shop_address", result.data.id);
  return response({ ok: true, address: result.data });
}

async function deleteAddress(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const id = String(body.id || "");
  const address = await client.from("shop_addresses").select("id").eq("id", id).eq("profile_id", profileId).is("deleted_at", null).maybeSingle();
  if (!address.data) return safeError("Address not found.", 404);
  await client.from("shop_addresses").update({ deleted_at: new Date().toISOString(), is_default: false }).eq("id", id).eq("profile_id", profileId);
  await audit(client, profileId, "address.delete", "shop_address", id);
  return response({ ok: true });
}

async function setDefaultAddress(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const id = String(body.id || "");
  const address = await client.from("shop_addresses").select("id").eq("id", id).eq("profile_id", profileId).is("deleted_at", null).maybeSingle();
  if (!address.data) return safeError("Address not found.", 404);
  await client.from("shop_addresses").update({ is_default: false }).eq("profile_id", profileId).is("deleted_at", null);
  await client.from("shop_addresses").update({ is_default: true }).eq("id", id).eq("profile_id", profileId).is("deleted_at", null);
  return listAddresses(request);
}

async function listOrders(request: Request) {
  const { client, profileId } = await currentSession(request);
  const { data, error } = await client.from("shop_orders").select("*,shop_order_groups(*,shop_order_items(*,shop_products(title,shop_product_images(image_url,is_primary))))").eq("profile_id", profileId).order("created_at", { ascending: false });
  if (error) return safeError("We couldn't load your orders.", 500);
  return response({ ok: true, orders: data || [] });
}

async function getOrder(request: Request) {
  const { client, profileId } = await currentSession(request);
  const id = String((await jsonBody(request)).orderId || new URL(request.url).searchParams.get("orderId") || "");
  const { data, error } = await client
    .from("shop_orders")
    .select("*,shop_order_groups(*,shop_order_items(*)),shop_order_addresses(*),shop_payments(method_code,status,amount_minor,currency),shop_order_status_history(*)")
    .eq("id", id)
    .eq("profile_id", profileId)
    .single();
  if (error) return safeError("We couldn't load your order.", 404);
  const issues = await client.rpc("adishop_buyer_order_issues", { p_profile_id: profileId, p_order_id: id });
  const postDeliveryActions = await client.rpc("adishop_post_delivery_actions", { p_profile_id: profileId, p_order_id: id });
  const reviews = await client.rpc("adishop_buyer_order_reviews", { p_profile_id: profileId, p_order_id: id });
  const requests = await client.rpc("adishop_buyer_request_payloads", { p_profile_id: profileId, p_order_id: id });
  return response({
    ok: true,
    order: {
      ...(data as Json),
      issues: issues.data || [],
      postDeliveryActions: postDeliveryActions.data || null,
      reviews: reviews.data || { productReviews: [], storeReviews: [] },
      refundRequests: ((requests.data as Json | null)?.refundRequests as unknown[]) || [],
      exchangeRequests: ((requests.data as Json | null)?.exchangeRequests as unknown[]) || [],
    },
  });
}

function issueError(raw = "") {
  const normalized = raw.toLowerCase();
  if (normalized.includes("order-not-found") || normalized.includes("order-group-not-found")) {
    return { code: "order_not_found", message: "Order not found.", status: 404, retryable: false };
  }
  if (normalized.includes("invalid-issue-reason")) {
    return { code: "invalid_issue_reason", message: "Choose an available issue reason.", status: 400, retryable: true };
  }
  if (normalized.includes("issue-description-required")) {
    return { code: "issue_description_required", message: "Add a short description for this issue.", status: 400, retryable: true };
  }
  if (normalized.includes("duplicate key") || normalized.includes("one_active")) {
    return { code: "issue_already_open", message: "This order already has an active issue for that reason.", status: 409, retryable: false };
  }
  return { code: "issue_unavailable", message: "Buyer Protection is temporarily unavailable.", status: 500, retryable: true };
}

async function buyerOrderIssueOptions(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const orderId = String(body.orderId || new URL(request.url).searchParams.get("orderId") || "");
  const { data, error } = await client.rpc("adishop_buyer_order_issue_options", {
    p_profile_id: profileId,
    p_order_id: orderId,
  });
  if (error) {
    const mapped = issueError(error.message);
    return structuredError(mapped.code, mapped.message, mapped.status, mapped.retryable);
  }
  return response({ ok: true, issueOptions: data as Json });
}

async function buyerOrderIssues(request: Request) {
  const { client, profileId } = await currentSession(request);
  const orderId = String(new URL(request.url).searchParams.get("orderId") || "");
  const { data, error } = await client.rpc("adishop_buyer_order_issues", {
    p_profile_id: profileId,
    p_order_id: orderId,
  });
  if (error) {
    const mapped = issueError(error.message);
    return structuredError(mapped.code, mapped.message, mapped.status, mapped.retryable);
  }
  return response({ ok: true, issues: data as Json });
}

async function buyerCreateOrderIssue(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const { data, error } = await client.rpc("adishop_buyer_create_order_issue", {
    p_profile_id: profileId,
    p_order_id: String(body.orderId || ""),
    p_order_group_id: body.orderGroupId ? String(body.orderGroupId) : null,
    p_reason_code: String(body.reasonCode || ""),
    p_description: String(body.description || ""),
    p_idempotency_key: String(body.idempotencyKey || request.headers.get("x-idempotency-key") || ""),
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || ""),
  });
  if (error) {
    const mapped = issueError(error.message);
    return structuredError(mapped.code, mapped.message, mapped.status, mapped.retryable);
  }
  return response({ ok: true, issue: (data as Json)?.issue || data });
}

function postDeliveryError(raw = "") {
  const normalized = raw.toLowerCase();
  if (normalized.includes("order-not-found") || normalized.includes("order-item-not-found") || normalized.includes("order-group-not-found")) {
    return { code: "order_not_found", message: "Order not found.", status: 404, retryable: false };
  }
  if (normalized.includes("invalid-rating")) {
    return { code: "invalid_rating", message: "Choose a rating from 1 to 5.", status: 400, retryable: true };
  }
  if (normalized.includes("invalid-quantity")) {
    return { code: "invalid_quantity", message: "Choose a valid quantity.", status: 400, retryable: true };
  }
  if (normalized.includes("description-required")) {
    return { code: "description_required", message: "Add a short description.", status: 400, retryable: true };
  }
  if (normalized.includes("invalid-variant")) {
    return { code: "invalid_variant", message: "Choose a valid replacement option.", status: 400, retryable: true };
  }
  if (normalized.includes("review-already-exists")) {
    return { code: "review_already_exists", message: "You already reviewed this item.", status: 409, retryable: false };
  }
  if (normalized.includes("refund-already-active")) {
    return { code: "refund_already_active", message: "A refund request is already open for this item.", status: 409, retryable: false };
  }
  if (normalized.includes("exchange-already-active")) {
    return { code: "exchange_already_active", message: "An exchange request is already open for this item.", status: 409, retryable: false };
  }
  if (normalized.includes("conflicting-refund-request") || normalized.includes("conflicting-exchange-request")) {
    return { code: "conflicting_request", message: "This item already has another active request.", status: 409, retryable: false };
  }
  if (normalized.includes("review-not-eligible")) {
    return { code: "review_not_eligible", message: "This order is not eligible for review.", status: 409, retryable: false };
  }
  if (normalized.includes("refund-not-eligible")) {
    return { code: "refund_not_eligible", message: "This order is not eligible for refund.", status: 409, retryable: false };
  }
  if (normalized.includes("exchange-not-eligible")) {
    return { code: "exchange_not_eligible", message: "This order is not eligible for exchange.", status: 409, retryable: false };
  }
  return { code: "post_delivery_unavailable", message: "This order action is temporarily unavailable.", status: 500, retryable: true };
}

async function buyerRpc(request: Request, rpcName: string, args: Json, responseKey: string) {
  const { client, profileId } = await currentSession(request);
  const { data, error } = await client.rpc(rpcName, { p_profile_id: profileId, ...args });
  if (error) {
    const mapped = postDeliveryError(error.message);
    return structuredError(mapped.code, mapped.message, mapped.status, mapped.retryable);
  }
  return response({ ok: true, [responseKey]: data as Json });
}

function idempotencyKey(request: Request, body: Json) {
  return String(body.idempotencyKey || body.requestId || request.headers.get("x-idempotency-key") || request.headers.get("x-request-id") || "").trim();
}

async function buyerPostDeliveryActions(request: Request) {
  const orderId = String(new URL(request.url).searchParams.get("orderId") || (await jsonBody(request)).orderId || "");
  return buyerRpc(request, "adishop_post_delivery_actions", { p_order_id: orderId }, "postDeliveryActions");
}

async function buyerReviewOptions(request: Request) {
  const orderId = String(new URL(request.url).searchParams.get("orderId") || (await jsonBody(request)).orderId || "");
  const { client, profileId } = await currentSession(request);
  const actions = await client.rpc("adishop_post_delivery_actions", { p_profile_id: profileId, p_order_id: orderId });
  const reviews = await client.rpc("adishop_buyer_order_reviews", { p_profile_id: profileId, p_order_id: orderId });
  if (actions.error || reviews.error) {
    const mapped = postDeliveryError(actions.error?.message || reviews.error?.message || "");
    return structuredError(mapped.code, mapped.message, mapped.status, mapped.retryable);
  }
  return response({ ok: true, reviewOptions: { actions: actions.data, reviews: reviews.data } });
}

async function buyerCreateProductReview(request: Request) {
  const body = await jsonBody(request);
  return buyerRpc(request, "adishop_buyer_create_product_review", {
    p_order_id: String(body.orderId || ""),
    p_order_item_id: String(body.orderItemId || ""),
    p_rating: Math.max(1, Math.min(5, Number(body.rating || 0))),
    p_title: String(body.title || ""),
    p_body: String(body.body || body.reviewBody || ""),
    p_idempotency_key: idempotencyKey(request, body),
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || idempotencyKey(request, body)),
  }, "productReview");
}

async function buyerCreateStoreReview(request: Request) {
  const body = await jsonBody(request);
  return buyerRpc(request, "adishop_buyer_create_store_review", {
    p_order_id: String(body.orderId || ""),
    p_order_group_id: String(body.orderGroupId || ""),
    p_rating: Math.max(1, Math.min(5, Number(body.rating || 0))),
    p_service_rating: Math.max(1, Math.min(5, Number(body.serviceRating || body.rating || 0))),
    p_communication_rating: Math.max(1, Math.min(5, Number(body.communicationRating || body.rating || 0))),
    p_delivery_rating: Math.max(1, Math.min(5, Number(body.deliveryRating || body.rating || 0))),
    p_body: String(body.body || body.reviewBody || ""),
    p_idempotency_key: idempotencyKey(request, body),
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || idempotencyKey(request, body)),
  }, "storeReview");
}

async function buyerOrderReviews(request: Request) {
  const orderId = String(new URL(request.url).searchParams.get("orderId") || (await jsonBody(request)).orderId || "");
  return buyerRpc(request, "adishop_buyer_order_reviews", { p_order_id: orderId }, "reviews");
}

async function publicProductReviews(request: Request) {
  const client = adminClient();
  const url = new URL(request.url);
  const productId = String(url.searchParams.get("productId") || "");
  if (!productId) return structuredError("product_not_found", "Product not found.", 404, false);
  const { data, error } = await client
    .from("shop_reviews")
    .select("id,product_id,store_id,rating,title,body,verified_purchase,created_at,shop_profiles(display_name)")
    .eq("product_id", productId)
    .eq("status", "published")
    .order("created_at", { ascending: false })
    .limit(Math.max(1, Math.min(100, Number(url.searchParams.get("limit") || 30))));
  if (error) return safeError("Reviews are temporarily unavailable.", 500);
  return response({ ok: true, reviews: data || [] });
}

async function publicStoreReviews(request: Request) {
  const client = adminClient();
  const url = new URL(request.url);
  const storeId = String(url.searchParams.get("storeId") || "");
  if (!storeId) return structuredError("store_not_found", "Store not found.", 404, false);
  const { data, error } = await client
    .from("shop_store_reviews")
    .select("id,store_id,rating,service_rating,communication_rating,delivery_rating,body,verified_purchase,created_at,shop_profiles(display_name)")
    .eq("store_id", storeId)
    .eq("status", "published")
    .order("created_at", { ascending: false })
    .limit(Math.max(1, Math.min(100, Number(url.searchParams.get("limit") || 30))));
  if (error) return safeError("Reviews are temporarily unavailable.", 500);
  return response({ ok: true, reviews: data || [] });
}

async function buyerCreateRefundRequest(request: Request) {
  const body = await jsonBody(request);
  return buyerRpc(request, "adishop_buyer_create_refund_request", {
    p_order_id: String(body.orderId || ""),
    p_order_item_id: String(body.orderItemId || ""),
    p_quantity: Math.max(1, Number(body.quantity || 1)),
    p_reason_code: String(body.reasonCode || "other"),
    p_description: String(body.description || ""),
    p_idempotency_key: idempotencyKey(request, body),
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || idempotencyKey(request, body)),
  }, "refundRequest");
}

async function buyerCreateExchangeRequest(request: Request) {
  const body = await jsonBody(request);
  return buyerRpc(request, "adishop_buyer_create_exchange_request", {
    p_order_id: String(body.orderId || ""),
    p_order_item_id: String(body.orderItemId || ""),
    p_requested_variant_id: body.requestedVariantId ? String(body.requestedVariantId) : null,
    p_quantity: Math.max(1, Number(body.quantity || 1)),
    p_reason_code: String(body.reasonCode || "other"),
    p_description: String(body.description || ""),
    p_idempotency_key: idempotencyKey(request, body),
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || idempotencyKey(request, body)),
  }, "exchangeRequest");
}

async function buyerRequests(request: Request, responseKey: "refundRequests" | "exchangeRequests" | "requests") {
  const orderId = new URL(request.url).searchParams.get("orderId");
  const result = await buyerRpc(request, "adishop_buyer_request_payloads", { p_order_id: orderId || null }, "requests");
  if (responseKey === "requests") return result;
  const body = await result.clone().json().catch(() => null) as Json | null;
  return response({ ok: true, [responseKey]: ((body?.requests as Json | undefined)?.[responseKey] as unknown[]) || [] });
}

async function buyerBuyAgain(request: Request) {
  const body = await jsonBody(request);
  return buyerRpc(request, "adishop_buyer_buy_again", {
    p_order_id: String(body.orderId || ""),
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || idempotencyKey(request, body) || randomId("buy_again")),
  }, "buyAgain");
}

async function buyerOrderReceipt(request: Request) {
  const orderId = String(new URL(request.url).searchParams.get("orderId") || (await jsonBody(request)).orderId || "");
  return buyerRpc(request, "adishop_buyer_order_receipt", { p_order_id: orderId }, "receipt");
}

async function sellerRequests(request: Request) {
  const { client, profileId } = await currentSession(request);
  const { data, error } = await client.rpc("adishop_seller_request_payloads", { p_profile_id: profileId });
  if (error) return structuredError("seller_requests_unavailable", "Seller requests are temporarily unavailable.", 500, true);
  return response({ ok: true, sellerRequests: data as Json });
}

async function sellerRespondToRequest(request: Request, kind: "refund" | "exchange") {
  try {
    const { client, profileId } = await currentSession(request);
    const body = await jsonBody(request);
    const id = String(body.requestId || body.refundRequestId || body.exchangeRequestId || "");
    const message = String(body.message || body.response || "").trim().slice(0, 1600);
    const requestId = String(body.idempotencyKey || request.headers.get("x-request-id") || "").trim().slice(0, 160);
    const expectedVersion = Number(body.expectedVersion);
    if (!id || message.length < 5 || requestId.length < 8 || !Number.isInteger(expectedVersion) || expectedVersion < 1) {
      return structuredError("seller_response_required", "Refresh the request and add a response for the buyer.", 400, false);
    }
    const result = await client.rpc("adishop_seller_respond_request", {
      p_profile_id: profileId,
      p_kind: kind,
      p_request_id: id,
      p_message: message,
      p_idempotency_key: requestId,
      p_expected_version: expectedVersion,
    });
    if (result.error) {
      const detail = result.error.message.toLowerCase();
      if (detail.includes("seller-request-not-found")) return structuredError("seller_request_not_found", "Request not found.", 404, false);
      if (detail.includes("stale-seller-request")) return structuredError("stale_request", "This request changed. Refresh it and try again.", 409, false);
      if (detail.includes("seller-response-required") || detail.includes("seller-idempotency-key-required")) {
        return structuredError("seller_response_required", "Add a response for the buyer.", 400, false);
      }
      return structuredError("seller_request_failed", "Request response could not be saved.", 500, true);
    }
    return sellerRequests(request);
  } catch (error) {
    const code = error instanceof Error ? error.message : "";
    if (["missing-session", "bad-token", "bad-alg", "bad-signature", "bad-claims", "expired", "invalid-session"].includes(code)) {
      return structuredError("auth_required", "Please sign in again.", 401, false);
    }
    return structuredError("seller_request_failed", "Request response could not be saved.", 500, true);
  }
}

async function cancelOrder(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const orderId = String(body.orderId || "");
  const reasonCode = String(body.reasonCode || "customer_request");
  const reasonText = String(body.reasonText || "");
  const { data, error } = await client.rpc("adishop_request_order_cancellation", {
    p_profile_id: profileId,
    p_order_id: orderId,
    p_reason_code: reasonCode,
    p_reason_text: reasonText,
  });
  if (error) return safeError("This order cannot be cancelled right now.", 409);
  return response({ ok: true, order: data as Json });
}

async function revealDeliveryCode(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const orderId = String(body.orderId || new URL(request.url).searchParams.get("orderId") || "");
  const { data, error } = await client.rpc("adishop_reveal_delivery_code", {
    p_profile_id: profileId,
    p_order_id: orderId,
  });
  if (error) return safeError("Delivery code is not available yet.", 409);
  return response({ ok: true, deliveryCode: data as Json });
}

async function listNotifications(request: Request) {
  const { client, profileId } = await currentSession(request);
  const { data, error } = await client
    .from("shop_notifications")
    .select("id,type,title,body,data,read_at,created_at")
    .eq("profile_id", profileId)
    .order("created_at", { ascending: false })
    .limit(50);
  if (error) return safeError("We couldn't load notifications.", 500);
  return response({ ok: true, notifications: data || [] });
}

async function notificationUnreadCount(request: Request) {
  const { client, profileId } = await currentSession(request);
  const { count, error } = await client
    .from("shop_notifications")
    .select("id", { count: "exact", head: true })
    .eq("profile_id", profileId)
    .is("read_at", null);
  if (error) return safeError("We couldn't load notifications.", 500);
  return response({ ok: true, unreadCount: count || 0 });
}

async function markNotificationRead(request: Request, all: boolean) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  let query = client.from("shop_notifications").update({ read_at: new Date().toISOString() }).eq("profile_id", profileId).is("read_at", null);
  if (!all) query = query.eq("id", String(body.notificationId || ""));
  const { error } = await query;
  if (error) return safeError("We couldn't update notifications.", 500);
  return notificationUnreadCount(request);
}

async function sellerStoreIds(client: ReturnType<typeof adminClient>, profileId: string) {
  const owned = await client
    .from("shop_stores")
    .select("id")
    .eq("owner_profile_id", profileId)
    .eq("status", "active")
    .eq("approval_status", "approved");
  const member = await client
    .from("shop_store_members")
    .select("store_id")
    .eq("profile_id", profileId)
    .eq("status", "active");
  const ids = new Set<string>();
  (owned.data || []).forEach((row: Json) => ids.add(String(row.id)));
  (member.data || []).forEach((row: Json) => ids.add(String(row.store_id)));
  return Array.from(ids);
}

async function enrichSellerOrderGroups(client: ReturnType<typeof adminClient>, groups: Json[]) {
  const issueReasonLabel = (reason: unknown) => {
    switch (String(reason || "").trim().toLowerCase()) {
      case "item_not_received": return "Item not received";
      case "wrong_item": return "Wrong item received";
      case "damaged_item": return "Item arrived damaged";
      case "incomplete_order": return "Incomplete order";
      case "not_as_described": return "Item not as described";
      case "seller_issue": return "Seller issue";
      case "delivery_issue": return "Delivery issue";
      case "other": return "Other issue";
      default: return "Order issue";
    }
  };
  const orderIds = Array.from(new Set(groups.map((group) => String((group.shop_orders as Json | undefined)?.id || "")).filter(Boolean)));
  const profileIds = Array.from(new Set(groups.map((group) => String((group.shop_orders as Json | undefined)?.profile_id || "")).filter(Boolean)));
  if (orderIds.length === 0) return groups;

  const addresses = await client
    .from("shop_order_addresses")
    .select("order_id,recipient_name,phone,city,address_line1,address_line2,latitude,longitude")
    .in("order_id", orderIds);
  const payments = await client
    .from("shop_payments")
    .select("order_id,method_code,status,amount_minor,currency")
    .in("order_id", orderIds)
    .order("created_at", { ascending: false });
  const history = await client
    .from("shop_order_status_history")
    .select("id,order_id,order_group_id,status,event_code,public_message,previous_status,next_status,actor_role,occurred_at,created_at")
    .in("order_id", orderIds)
    .order("occurred_at", { ascending: true });
  const deliveryCodes = await client
    .from("shop_delivery_confirmation_codes")
    .select("order_id,revealed_at,verified_at,failed_attempts,locked_until")
    .in("order_id", orderIds);
  const issues = await client
    .from("shop_order_issues")
    .select("id,order_id,order_group_id,reason_code,description,status,buyer_visible_resolution,created_at,updated_at")
    .in("order_id", orderIds)
    .order("created_at", { ascending: false });
  const issueIds = (issues.data || []).map((row: Json) => String(row.id || "")).filter(Boolean);
  const issueEvents = issueIds.length === 0
    ? { data: [] as Json[] }
    : await client
      .from("shop_order_issue_events")
      .select("issue_id,event_code,next_status,public_message,created_at")
      .in("issue_id", issueIds)
      .not("public_message", "is", null)
      .order("created_at", { ascending: true });
  const profiles = profileIds.length === 0
    ? { data: [] as Json[] }
    : await client.from("shop_profiles").select("id,display_name,phone").in("id", profileIds);

  const addressByOrder = new Map((addresses.data || []).map((row: Json) => [String(row.order_id), row]));
  const paymentByOrder = new Map<string, Json>();
  (payments.data || []).forEach((row: Json) => {
    const orderId = String(row.order_id || "");
    if (orderId && !paymentByOrder.has(orderId)) paymentByOrder.set(orderId, row);
  });
  const profileById = new Map((profiles.data || []).map((row: Json) => [String(row.id), row]));
  const codeByOrder = new Map((deliveryCodes.data || []).map((row: Json) => [String(row.order_id), row]));
  const eventsByIssue = new Map<string, Json[]>();
  (issueEvents.data || []).forEach((row: Json) => {
    const issueId = String(row.issue_id || "");
    if (!issueId) return;
    eventsByIssue.set(issueId, [...(eventsByIssue.get(issueId) || []), row]);
  });
  const issuesByOrder = new Map<string, Json[]>();
  (issues.data || []).forEach((row: Json) => {
    const orderId = String(row.order_id || "");
    if (!orderId) return;
    issuesByOrder.set(orderId, [...(issuesByOrder.get(orderId) || []), row]);
  });
  const historyByOrder = new Map<string, Json[]>();
  (history.data || []).forEach((row: Json) => {
    const orderId = String(row.order_id || "");
    if (!orderId) return;
    historyByOrder.set(orderId, [...(historyByOrder.get(orderId) || []), row]);
  });

  return groups.map((group) => {
    const order = (group.shop_orders || {}) as Json;
    const orderId = String(order.id || "");
    const profile = profileById.get(String(order.profile_id || "")) || {};
    const address = addressByOrder.get(orderId) || {};
    const payment = paymentByOrder.get(orderId) || null;
    const code = codeByOrder.get(orderId) || {};
    const groupId = String(group.id || "");
    const sellerVisibleIssues = (issuesByOrder.get(orderId) || [])
      .filter((row) => String(row.order_group_id || "") === groupId)
      .map((row) => {
        const issueId = String(row.id || "");
        return {
          reason_code: row.reason_code || null,
          reason_label: issueReasonLabel(row.reason_code),
          description: row.description || "",
          status: row.status || "open",
          buyer_visible_resolution: row.buyer_visible_resolution || null,
          created_at: row.created_at || null,
          updated_at: row.updated_at || null,
          events: (eventsByIssue.get(issueId) || []).map((event) => ({
            event_code: event.event_code || null,
            next_status: event.next_status || null,
            public_message: event.public_message || null,
            created_at: event.created_at || null,
          })),
        };
      });
    const { profile_id: _profileId, ...publicOrder } = order;
    return {
      ...group,
      shop_orders: publicOrder,
      customer: {
        display_name: String(address.recipient_name || profile.display_name || "Customer"),
        phone: String(address.phone || profile.phone || ""),
      },
      delivery_address: address ? {
        recipient_name: address.recipient_name || null,
        phone: address.phone || null,
        city: address.city || null,
        address_line1: address.address_line1 || null,
        address_line2: address.address_line2 || null,
        latitude: address.latitude || null,
        longitude: address.longitude || null,
      } : null,
      payment: payment ? {
        method_code: payment.method_code || null,
        status: payment.status || null,
        amount_minor: payment.amount_minor || null,
        currency: payment.currency || null,
      } : null,
      history: (historyByOrder.get(orderId) || []).filter((row) => {
        const orderGroupId = String(row.order_group_id || "");
        return !orderGroupId || orderGroupId === String(group.id || "");
      }),
      delivery_code_status: {
        revealed: Boolean(code.revealed_at),
        verified: Boolean(code.verified_at),
        failed_attempts: Number(code.failed_attempts || 0),
        locked: code.locked_until ? new Date(String(code.locked_until)).getTime() > Date.now() : false,
      },
      issues: sellerVisibleIssues,
    };
  });
}

async function listSellerOrderGroups(request: Request) {
  const { client, profileId } = await currentSession(request);
  const storeIds = await sellerStoreIds(client, profileId);
  if (storeIds.length === 0) return response({ ok: true, sellerOrderGroups: [] });
  const { data, error } = await client
    .from("shop_order_groups")
    .select("*,shop_orders(id,profile_id,order_number,status,payment_status,fulfillment_status,delivery_status,currency,total_minor,created_at),shop_order_items(*)")
    .in("store_id", storeIds)
    .order("created_at", { ascending: false })
    .limit(100);
  if (error) return safeError("We couldn't load seller orders.", 500);
  return response({ ok: true, sellerOrderGroups: await enrichSellerOrderGroups(client, (data || []) as Json[]) });
}

async function getSellerOrderGroup(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const groupId = String(body.orderGroupId || new URL(request.url).searchParams.get("orderGroupId") || "");
  const { data, error } = await client
    .from("shop_order_groups")
    .select("*,shop_orders(id,profile_id,order_number,status,payment_status,fulfillment_status,delivery_status,currency,total_minor,created_at),shop_order_items(*)")
    .eq("id", groupId)
    .single();
  if (error || !data || !await sellerStoreIds(client, profileId).then((ids) => ids.includes(String((data as Json).store_id)))) {
    return safeError("Seller order not found.", 404);
  }
  const enriched = await enrichSellerOrderGroups(client, [data as Json]);
  return response({ ok: true, sellerOrderGroup: enriched[0] || null });
}

async function transitionSellerOrderGroup(request: Request, action: string) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const orderGroupId = String(body.orderGroupId || "");
  const reason = String(body.reason || "");
  const metadata = {
    reasonCode: body.reasonCode || null,
    requestId: body.requestId || request.headers.get("x-request-id") || null,
    carrier: body.carrier || null,
    trackingNumber: body.trackingNumber || null,
  };
  const { data, error } = await client.rpc("adishop_transition_seller_order_group", {
    p_profile_id: profileId,
    p_order_group_id: orderGroupId,
    p_action: action,
    p_reason: reason,
    p_metadata: metadata,
  });
  if (error) {
    const normalized = error.message.toLowerCase();
    if (normalized.includes("rejection-reason-required")) {
      return structuredError("rejection_reason_required", "Choose a rejection reason.", 400, true);
    }
    if (normalized.includes("invalid-rejection-reason")) {
      return structuredError("invalid_rejection_reason", "Choose a valid rejection reason.", 400, true);
    }
    if (normalized.includes("rejection-explanation-required")) {
      return structuredError("rejection_explanation_required", "Add an explanation for Other.", 400, true);
    }
    return safeError("This seller action is not allowed for the current order state.", 409);
  }
  return response({ ok: true, sellerOrderGroup: data as Json });
}

async function verifySellerDeliveryCode(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const orderGroupId = String(body.orderGroupId || "");
  const deliveryCode = String(body.deliveryCode || "");
  const { data, error } = await client.rpc("adishop_verify_delivery_code", {
    p_profile_id: profileId,
    p_order_group_id: orderGroupId,
    p_code: deliveryCode,
  });
  if (error) {
    const mapped = deliveryCodeError(error.message);
    return structuredError(mapped.code, mapped.message, 409, mapped.retryable);
  }
  if ((data as Json)?.ok === false) {
    const mapped = deliveryCodeError(String((data as Json)?.code || (data as Json)?.error || ""));
    return structuredError(mapped.code, mapped.message, 409, mapped.retryable);
  }
  return response({ ok: true, sellerOrderGroup: data as Json });
}

function deliveryCodeError(raw = "") {
  const normalized = raw.toLowerCase();
  if (normalized.includes("invalid-delivery-code") || normalized.includes("delivery_code_invalid")) {
    return { code: "delivery_code_invalid", message: "The delivery code is incorrect.", retryable: true };
  }
  if (normalized.includes("delivery-code-locked") || normalized.includes("delivery_code_locked")) {
    return { code: "delivery_code_locked", message: "Too many incorrect delivery code attempts. Try again later.", retryable: true };
  }
  if (normalized.includes("delivery-code-not-revealed") || normalized.includes("delivery_code_not_revealed")) {
    return { code: "delivery_code_not_revealed", message: "Ask the customer to reveal the delivery code first.", retryable: true };
  }
  if (normalized.includes("seller-order-not-found") || normalized.includes("seller_order_not_found")) {
    return { code: "seller_order_not_found", message: "Seller order not found.", retryable: false };
  }
  return { code: "delivery_code_unavailable", message: "Delivery code could not be verified.", retryable: true };
}

function sellerProductError(raw = "") {
  const normalized = raw.toLowerCase();
  if (normalized.includes("seller-store-not-found")) {
    return { code: "seller_store_not_found", message: "Your approved seller store could not be found.", status: 403, retryable: false };
  }
  if (normalized.includes("seller-product-not-found")) {
    return { code: "seller_product_not_found", message: "Product not found for this seller.", status: 404, retryable: false };
  }
  if (normalized.includes("seller-product-locked")) {
    return { code: "seller_product_locked", message: "This product is locked while it is in review.", status: 409, retryable: true };
  }
  if (normalized.includes("category-not-found")) {
    return { code: "category_not_found", message: "Choose an active product category.", status: 409, retryable: true };
  }
  if (normalized.includes("product-validation-failed")) {
    return { code: "product_validation_failed", message: "Product is missing required details.", status: 409, retryable: true };
  }
  if (normalized.includes("seller-product-media-limit")) {
    return { code: "seller_product_media_limit", message: "A product can have up to 10 images.", status: 409, retryable: false };
  }
  if (normalized.includes("seller-product-media-not-found")) {
    return { code: "seller_product_media_not_found", message: "Product image not found for this seller.", status: 404, retryable: false };
  }
  return { code: "seller_product_unavailable", message: "Seller product action could not be completed.", status: 500, retryable: true };
}

async function sellerProductRpc(request: Request, rpcName: string, args: Json, responseKey: string) {
  const { client, profileId } = await currentSession(request);
  const { data, error } = await client.rpc(rpcName, { p_profile_id: profileId, ...args });
  if (error) {
    const mapped = sellerProductError(error.message);
    return structuredError(mapped.code, mapped.message, mapped.status, mapped.retryable);
  }
  return response({ ok: true, [responseKey]: data as Json });
}

async function listSellerProducts(request: Request) {
  const url = new URL(request.url);
  return sellerProductRpc(request, "adishop_seller_list_products", {
    p_status: url.searchParams.get("status") || "all",
    p_limit: Math.max(1, Math.min(100, Number(url.searchParams.get("limit") || 30))),
    p_offset: Math.max(0, Number(url.searchParams.get("offset") || 0)),
  }, "sellerProducts");
}

async function getSellerProduct(request: Request) {
  const url = new URL(request.url);
  return sellerProductRpc(request, "adishop_seller_get_product", {
    p_product_id: String(url.searchParams.get("productId") || ""),
  }, "sellerProduct");
}

async function createSellerProduct(request: Request) {
  const body = await jsonBody(request);
  return sellerProductRpc(request, "adishop_seller_create_product", {
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || ""),
  }, "sellerProduct");
}

async function updateSellerProduct(request: Request) {
  const body = await jsonBody(request);
  const productId = String(body.productId || "");
  const payload = (body.product || body.payload || {}) as Json;
  if (payload && typeof payload === "object" && !Array.isArray(payload)) {
    const mutablePayload = payload as Record<string, unknown>;
    if (mutablePayload.images == null || (Array.isArray(mutablePayload.images) && mutablePayload.images.length === 0)) delete mutablePayload.images;
    if (mutablePayload.variants == null || (Array.isArray(mutablePayload.variants) && mutablePayload.variants.length === 0)) delete mutablePayload.variants;
  }
  return sellerProductRpc(request, "adishop_seller_update_product", {
    p_product_id: productId,
    p_payload: payload,
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || ""),
  }, "sellerProduct");
}

async function submitSellerProduct(request: Request) {
  const body = await jsonBody(request);
  return sellerProductRpc(request, "adishop_seller_submit_product", {
    p_product_id: String(body.productId || ""),
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || ""),
  }, "sellerProduct");
}

function sanitizePathPart(value: string) {
  return value.replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 80);
}

async function sellerOwnedEditableProduct(client: ReturnType<typeof adminClient>, profileId: string, productId: string) {
  const { data, error } = await client
    .from("shop_products")
    .select("id,store_id,status,moderation_status,shop_stores!inner(id,owner_profile_id,status,approval_status,deleted_at)")
    .eq("id", productId)
    .is("deleted_at", null)
    .single();
  const product = data as Json | null;
  const store = (product?.shop_stores || {}) as Json;
  if (error || !product || String(store.owner_profile_id || "") !== profileId) throw new Error("seller-product-not-found");
  if (store.status !== "active" || store.approval_status !== "approved" || store.deleted_at) throw new Error("seller-store-not-found");
  if (["submitted", "under_review", "approved", "suspended", "archived"].includes(String(product.moderation_status || ""))) {
    throw new Error("seller-product-locked");
  }
  return product;
}

function mediaError(raw = "") {
  const mapped = sellerProductError(raw);
  if (mapped.code !== "seller_product_unavailable") return mapped;
  const normalized = raw.toLowerCase();
  if (normalized.includes("unsupported-media-type")) return { code: "unsupported_media_type", message: "Use a JPEG, PNG or WebP image.", status: 415, retryable: false };
  if (normalized.includes("media-too-large")) return { code: "media_too_large", message: "Image is too large. Choose a smaller photo.", status: 413, retryable: false };
  if (normalized.includes("bad-media")) return { code: "bad_media", message: "Image could not be uploaded.", status: 400, retryable: true };
  return { code: "seller_product_media_unavailable", message: "Product image action could not be completed.", status: 500, retryable: true };
}

async function uploadSellerProductMedia(request: Request) {
  try {
    const { client, profileId } = await currentSession(request);
    const form = await request.formData();
    const productId = String(form.get("productId") || "");
    const requestId = sanitizePathPart(String(form.get("requestId") || request.headers.get("x-request-id") || randomId("media")));
    const altText = String(form.get("altText") || "").slice(0, 160);
    const width = Math.max(0, Math.min(12000, Number(form.get("width") || 0)));
    const height = Math.max(0, Math.min(12000, Number(form.get("height") || 0)));
    const file = form.get("image");
    if (!productId || !(file instanceof File)) throw new Error("bad-media");
    const mime = file.type;
    const extension = mime === "image/png" ? "png" : mime === "image/webp" ? "webp" : mime === "image/jpeg" ? "jpg" : "";
    if (!extension) throw new Error("unsupported-media-type");
    if (file.size <= 0) throw new Error("bad-media");
    if (file.size > 5 * 1024 * 1024) throw new Error("media-too-large");

    const product = await sellerOwnedEditableProduct(client, profileId, productId);
    const storeId = String(product.store_id || "");
    const existingCount = await client
      .from("shop_product_images")
      .select("id", { count: "exact", head: true })
      .eq("product_id", productId)
      .is("deleted_at", null);
    if ((existingCount.count || 0) >= 10) throw new Error("seller-product-media-limit");

    const bucket = "shop-product-images";
    const storagePath = `seller-products/${profileId}/${storeId}/${productId}/${requestId}.${extension}`;
    const existing = await client
      .from("shop_product_images")
      .select("*")
      .eq("bucket", bucket)
      .eq("storage_path", storagePath)
      .maybeSingle();
    if (existing.data) return response({ ok: true, sellerProductImage: existing.data as Json });

    const bytes = new Uint8Array(await file.arrayBuffer());
    const upload = await client.storage.from(bucket).upload(storagePath, bytes, { contentType: mime, upsert: false });
    if (upload.error) throw new Error("bad-media");
    const publicUrl = client.storage.from(bucket).getPublicUrl(storagePath).data.publicUrl;
    const primary = (existingCount.count || 0) === 0;
    if (primary) {
      await client.from("shop_product_images").update({ is_primary: false }).eq("product_id", productId);
    }
    const inserted = await client
      .from("shop_product_images")
      .insert({
        product_id: productId,
        seller_profile_id: profileId,
        store_id: storeId,
        bucket,
        storage_path: storagePath,
        image_url: publicUrl,
        alt_text: altText || null,
        is_primary: primary,
        display_order: existingCount.count || 0,
        mime_type: mime,
        width: width || null,
        height: height || null,
        size_bytes: file.size,
        moderation_status: String(product.moderation_status || "draft"),
      })
      .select("*")
      .single();
    if (inserted.error || !inserted.data) throw new Error("bad-media");
    await audit(client, profileId, "seller_product_media.upload", "shop_product", productId, { imageId: String((inserted.data as Json).id || "") });
    return response({ ok: true, sellerProductImage: inserted.data as Json });
  } catch (error) {
    const mapped = mediaError(error instanceof Error ? error.message : "");
    return structuredError(mapped.code, mapped.message, mapped.status, mapped.retryable);
  }
}

async function deleteSellerProductMedia(request: Request) {
  try {
    const { client, profileId } = await currentSession(request);
    const body = await jsonBody(request);
    const productId = String(body.productId || "");
    const imageId = String(body.imageId || "");
    await sellerOwnedEditableProduct(client, profileId, productId);
    const found = await client
      .from("shop_product_images")
      .select("*")
      .eq("id", imageId)
      .eq("product_id", productId)
      .eq("seller_profile_id", profileId)
      .is("deleted_at", null)
      .single();
    if (found.error || !found.data) throw new Error("seller-product-media-not-found");
    const row = found.data as Json;
    await client.from("shop_product_images").update({ deleted_at: new Date().toISOString(), is_primary: false }).eq("id", imageId);
    if (row.storage_path) await client.storage.from(String(row.bucket || "shop-product-images")).remove([String(row.storage_path)]);
    const replacement = await client
      .from("shop_product_images")
      .select("id")
      .eq("product_id", productId)
      .is("deleted_at", null)
      .order("display_order", { ascending: true })
      .limit(1)
      .maybeSingle();
    if (replacement.data) await client.from("shop_product_images").update({ is_primary: true }).eq("id", String((replacement.data as Json).id || ""));
    await audit(client, profileId, "seller_product_media.delete", "shop_product", productId, { imageId });
    const product = await client.rpc("adishop_seller_get_product", { p_profile_id: profileId, p_product_id: productId });
    return response({ ok: true, sellerProduct: product.data as Json });
  } catch (error) {
    const mapped = mediaError(error instanceof Error ? error.message : "");
    return structuredError(mapped.code, mapped.message, mapped.status, mapped.retryable);
  }
}

async function reorderSellerProductMedia(request: Request) {
  try {
    const { client, profileId } = await currentSession(request);
    const body = await jsonBody(request);
    const productId = String(body.productId || "");
    const orderedIds = Array.isArray(body.imageIds) ? body.imageIds.map((id) => String(id)).filter(Boolean).slice(0, 10) : [];
    const primaryId = String(body.primaryImageId || orderedIds[0] || "");
    if (!orderedIds.length || !primaryId) throw new Error("bad-media");
    await sellerOwnedEditableProduct(client, profileId, productId);
    const rows = await client
      .from("shop_product_images")
      .select("id")
      .eq("product_id", productId)
      .eq("seller_profile_id", profileId)
      .is("deleted_at", null)
      .in("id", orderedIds);
    if (rows.error || (rows.data || []).length !== orderedIds.length) throw new Error("seller-product-media-not-found");
    await client.from("shop_product_images").update({ is_primary: false }).eq("product_id", productId);
    for (let index = 0; index < orderedIds.length; index += 1) {
      await client.from("shop_product_images").update({ display_order: index, is_primary: orderedIds[index] === primaryId }).eq("id", orderedIds[index]);
    }
    await audit(client, profileId, "seller_product_media.reorder", "shop_product", productId, { count: orderedIds.length });
    const product = await client.rpc("adishop_seller_get_product", { p_profile_id: profileId, p_product_id: productId });
    return response({ ok: true, sellerProduct: product.data as Json });
  } catch (error) {
    const mapped = mediaError(error instanceof Error ? error.message : "");
    return structuredError(mapped.code, mapped.message, mapped.status, mapped.retryable);
  }
}

function adminError(raw = "") {
  const normalized = raw.toLowerCase();
  if (normalized.includes("admin-forbidden")) return { code: "admin_forbidden", message: "You do not have access to AdisGebeya Admin.", status: 403 };
  if (normalized.includes("reason-required")) return { code: "reason_required", message: "A clear reason is required.", status: 400 };
  if (normalized.includes("seller-application-not-found")) return { code: "seller_application_not_found", message: "Seller application not found.", status: 404 };
  if (normalized.includes("product-not-found")) return { code: "product_not_found", message: "Product not found.", status: 404 };
  if (normalized.includes("order-not-found")) return { code: "order_not_found", message: "Order not found.", status: 404 };
  if (normalized.includes("invalid-seller-application-state")) return { code: "invalid_state", message: "This seller application has already moved to another state.", status: 409 };
  if (normalized.includes("invalid-product-state")) return { code: "invalid_state", message: "This product has already moved to another state.", status: 409 };
  if (normalized.includes("stale-product-review")) return { code: "stale_product_review", message: "This product changed. Refresh it before reviewing.", status: 409 };
  if (normalized.includes("idempotency-conflict")) return { code: "idempotency_conflict", message: "This review request was already used.", status: 409 };
  if (normalized.includes("self-product-review-blocked")) return { code: "self_review_blocked", message: "A seller cannot review their own product.", status: 403 };
  if (normalized.includes("product-validation-failed")) return { code: "product_validation_failed", message: "Product is missing required details.", status: 409 };
  if (normalized.includes("missing-images")) return { code: "missing_images", message: "At least one product image is required.", status: 409 };
  if (normalized.includes("missing-stock")) return { code: "missing_stock", message: "Product stock is required before approval.", status: 409 };
  if (normalized.includes("store-not-found")) return { code: "store_not_found", message: "Store not found.", status: 404 };
  if (normalized.includes("store-not-active")) return { code: "store_not_active", message: "The store must be active before approval.", status: 409 };
  if (normalized.includes("unsupported-action")) return { code: "unsupported_action", message: "Unsupported moderation action.", status: 400 };
  if (normalized.includes("last-super-admin-protected")) return { code: "last_super_admin_protected", message: "At least one Super Admin must remain active.", status: 409 };
  if (normalized.includes("self-escalation-blocked")) return { code: "self_escalation_blocked", message: "Admins cannot assign roles to themselves.", status: 409 };
  return { code: "admin_action_failed", message: "Admin action could not be completed.", status: 500 };
}

async function adminRpc(request: Request, rpcName: string, args: Json, responseKey: string, profileParam = "p_profile_id") {
  const { client, profileId } = await currentSession(request);
  const { data, error } = await client.rpc(rpcName, { [profileParam]: profileId, ...args });
  if (error) {
    const mapped = adminError(error.message);
    return structuredError(mapped.code, mapped.message, mapped.status, mapped.status >= 500);
  }
  return response({ ok: true, [responseKey]: data as Json });
}

function pageParams(request: Request) {
  const url = new URL(request.url);
  return {
    p_status: url.searchParams.get("status") || "submitted",
    p_search: url.searchParams.get("search") || "",
    p_limit: Math.max(1, Math.min(100, Number(url.searchParams.get("limit") || 30))),
    p_offset: Math.max(0, Number(url.searchParams.get("offset") || 0)),
  };
}

async function adminSession(request: Request) {
  return adminRpc(request, "adishop_admin_session", {}, "adminSession");
}

async function adminDashboard(request: Request) {
  return adminRpc(request, "adishop_admin_dashboard", {}, "dashboard");
}

async function adminSellerApplications(request: Request) {
  return adminRpc(request, "adishop_admin_list_seller_applications", pageParams(request), "sellerApplications");
}

async function adminSellerApplicationDetail(request: Request) {
  const url = new URL(request.url);
  const applicationId = String(url.searchParams.get("applicationId") || "");
  return adminRpc(request, "adishop_admin_get_seller_application", { p_application_id: applicationId }, "sellerApplicationDetail");
}

async function adminReviewSeller(request: Request) {
  const body = await jsonBody(request);
  return adminRpc(request, "adishop_admin_review_seller_application", {
    p_application_id: String(body.applicationId || ""),
    p_action: String(body.action || ""),
    p_reason: String(body.reason || ""),
    p_private_notes: String(body.privateNotes || ""),
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || ""),
  }, "sellerApplicationDetail", "p_actor_profile_id");
}

async function adminProducts(request: Request) {
  const params = pageParams(request);
  return adminRpc(request, "adishop_admin_list_products", { ...params, p_status: params.p_status === "submitted" ? "pending" : params.p_status }, "products");
}

async function adminProductDetail(request: Request) {
  const url = new URL(request.url);
  return adminRpc(request, "adishop_admin_get_product", { p_product_id: String(url.searchParams.get("productId") || "") }, "productDetail");
}

async function adminReviewProduct(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);

  // 1. Verify authorized Shop Admin role (strictly SHOP_SUPER_ADMIN or SHOP_ADMIN; SUPPORT_AGENT and SELLER prohibited)
  const { data: adminRoles, error: rolesError } = await client
    .rpc("current_shop_roles", { target_profile_id: profileId });
  const hasModerationRole = Array.isArray(adminRoles) && adminRoles.some((r: string) =>
    ["SHOP_SUPER_ADMIN", "SHOP_ADMIN"].includes(r)
  );
  if (rolesError || !hasModerationRole) {
    return structuredError("admin_forbidden", "You do not have access to AdisGebeya Admin.", 403);
  }

  // 2. Validate action and inputs
  const action = String(body.action || "").trim().toLowerCase();
  const allowedActions = ["start_review", "approve", "reject", "request_changes", "suspend", "reactivate", "archive"];
  if (!allowedActions.includes(action)) {
    return structuredError("unsupported_action", "Unsupported moderation action.", 400);
  }

  const productId = String(body.productId || "").trim();
  if (!productId) {
    return structuredError("product_id_required", "Product ID is required.", 400);
  }

  const reason = String(body.publicReason || body.reason || "").trim();
  if (["reject", "request_changes", "suspend"].includes(action) && reason.length < 3) {
    return structuredError("reason_required", "A clear reason is required.", 400);
  }

  // 3. Pre-check seller self-approval and cross-store integrity
  const { data: productRecord, error: prodError } = await client
    .from("shop_products")
    .select("id, store_id, moderation_status, status, shop_stores(id, owner_profile_id, status, approval_status)")
    .eq("id", productId)
    .single();

  if (prodError || !productRecord) {
    return structuredError("product_not_found", "Product not found.", 404);
  }

  const store = productRecord.shop_stores as { id?: string; owner_profile_id?: string; status?: string; approval_status?: string } | null;
  if (!store) {
    return structuredError("store_not_found", "Store not found.", 404);
  }

  // Reject seller self-approval server-side
  if (["approve", "reject", "request_changes", "suspend", "reactivate", "archive"].includes(action) &&
      store.owner_profile_id === profileId) {
    return structuredError("self_review_blocked", "A seller cannot review their own product.", 403);
  }

  // Validate allowed moderation status transitions
  const currentModStatus = String(productRecord.moderation_status || productRecord.status || "").toLowerCase();
  if (action === "start_review" && !["pending", "submitted", "under_review"].includes(currentModStatus)) {
    return structuredError("invalid_state", "This product has already moved to another state.", 409);
  }
  if (["approve", "reject", "request_changes"].includes(action) &&
      !["pending", "submitted", "under_review", "changes_requested", "approved", "rejected"].includes(currentModStatus)) {
    return structuredError("invalid_state", "This product has already moved to another state.", 409);
  }

  // 4. Invoke RPC with strictly enforced moderator identity (preventing client override)
  const { data, error } = await client.rpc("adishop_admin_review_product", {
    p_actor_profile_id: profileId,
    p_product_id: productId,
    p_action: action,
    p_reason_code: String(body.reasonCode || ""),
    p_public_reason: reason,
    p_private_notes: String(body.privateNotes || ""),
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || ""),
  });

  if (error) {
    const mapped = adminError(error.message);
    return structuredError(mapped.code, mapped.message, mapped.status, mapped.status >= 500);
  }

  return response({ ok: true, productDetail: data as Json });
}

async function adminOrders(request: Request) {
  const params = pageParams(request);
  return adminRpc(request, "adishop_admin_list_orders", { p_search: params.p_search, p_limit: params.p_limit, p_offset: params.p_offset }, "adminOrders");
}

async function adminOrderDetail(request: Request) {
  const url = new URL(request.url);
  return adminRpc(request, "adishop_admin_get_order", { p_order_id: String(url.searchParams.get("orderId") || "") }, "orderDetail");
}

async function adminAddOrderNote(request: Request) {
  const body = await jsonBody(request);
  return adminRpc(request, "adishop_admin_add_order_note", {
    p_order_id: String(body.orderId || ""),
    p_note: String(body.note || ""),
    p_visibility: String(body.visibility || "private"),
  }, "orderNote");
}

async function adminUpdateOrderIssue(request: Request) {
  const body = await jsonBody(request);
  return adminRpc(request, "adishop_admin_update_order_issue", {
    p_issue_id: String(body.issueId || ""),
    p_status: String(body.status || ""),
    p_buyer_visible_message: String(body.buyerVisibleMessage || ""),
    p_private_note: String(body.privateNote || ""),
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || ""),
  }, "issue", "p_actor_profile_id");
}

async function adminAuditLog(request: Request) {
  const params = pageParams(request);
  return adminRpc(request, "adishop_admin_audit_log", { p_limit: params.p_limit, p_offset: params.p_offset }, "auditLog");
}

async function adminUsers(request: Request) {
  return adminRpc(request, "adishop_admin_users", {}, "adminUsers");
}

async function adminStores(request: Request) {
  const params = pageParams(request);
  return adminRpc(request, "adishop_admin_list_stores", {
    p_status: params.p_status,
    p_search: params.p_search,
    p_limit: params.p_limit,
    p_offset: params.p_offset,
  }, "stores");
}

async function adminStoreDetail(request: Request) {
  const url = new URL(request.url);
  return adminRpc(request, "adishop_admin_get_store", { p_store_id: String(url.searchParams.get("storeId") || "") }, "storeDetail");
}

async function adminUpdateStoreStatus(request: Request) {
  const body = await jsonBody(request);
  return adminRpc(request, "adishop_admin_update_store_status", {
    p_store_id: String(body.storeId || ""),
    p_action: String(body.action || ""),
    p_reason_code: String(body.reasonCode || ""),
    p_seller_visible_message: String(body.sellerVisibleMessage || body.reason || ""),
    p_private_note: String(body.privateNote || ""),
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || ""),
  }, "storeDetail", "p_actor_profile_id");
}

async function adminRoleMutation(request: Request, mode: "assign" | "revoke") {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const rpcName = mode === "assign" ? "adishop_admin_assign_role" : "adishop_admin_revoke_role";
  const { data, error } = await client.rpc(rpcName, {
    p_actor_profile_id: profileId,
    p_target_profile_id: String(body.profileId || ""),
    p_role_code: String(body.roleCode || ""),
    p_reason: String(body.reason || ""),
  });
  if (error) {
    const mapped = adminError(error.message);
    return structuredError(mapped.code, mapped.message, mapped.status, mapped.status >= 500);
  }
  return response({ ok: true, result: data as Json });
}

async function sellerStatus(request: Request) {
  const { client, profileId } = await currentSession(request);
  const { data } = await client.from("shop_seller_applications").select("*").eq("profile_id", profileId).order("created_at", { ascending: false }).limit(1).maybeSingle();
  return response({ ok: true, status: data?.status || "not_started", application: data || null });
}

function checkoutError(raw = "") {
  if (raw.includes("empty-cart")) return { code: "empty_cart", message: "Your cart is empty.", retryable: false };
  if (raw.includes("insufficient-stock")) return { code: "insufficient_inventory", message: "This item is no longer available in the requested quantity.", retryable: true };
  if (raw.includes("unavailable-item")) return { code: "product_unavailable", message: "Some items are no longer available.", retryable: true };
  if (raw.includes("checkout-expired")) return { code: "checkout_expired", message: "Your checkout expired. Please review your cart and try again.", retryable: true };
  if (raw.includes("payment-method-unavailable")) return { code: "payment_method_unavailable", message: "No supported payment method is available yet.", retryable: false };
  if (raw.includes("address-not-found")) return { code: "address_not_found", message: "Choose a valid delivery address.", retryable: false };
  if (raw.includes("cod-not-eligible")) return { code: "cod_not_eligible", message: "Cash on delivery is not available for this order.", retryable: false };
  if (raw.includes("address-not-eligible")) return { code: "address_not_eligible", message: "Cash on delivery is not available for this delivery address.", retryable: false };
  return { code: "checkout_unavailable", message: "Checkout could not start. Please refresh your cart and try again.", retryable: true };
}

async function createCheckout(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const idempotencyKey = String(body.idempotencyKey || request.headers.get("x-idempotency-key") || "").trim();
  if (idempotencyKey.length < 8) return structuredError("invalid_checkout_request", "Please try checkout again.", 400, true);
  const source = String(body.source || "CART").toUpperCase();
  const rpcArgs = source === "BUY_NOW"
    ? {
      p_profile_id: profileId,
      p_idempotency_key: idempotencyKey,
      p_product_id: String(body.productId || ""),
      p_variant_id: body.variantId ? String(body.variantId) : null,
      p_quantity: Math.max(1, Math.min(99, Number(body.quantity || 1))),
    }
    : {
      p_profile_id: profileId,
      p_idempotency_key: idempotencyKey,
    };
  const { data, error } = source === "BUY_NOW"
    ? await client.rpc("adishop_create_buy_now_checkout_session", rpcArgs)
    : await client.rpc("adishop_create_checkout_session", rpcArgs);
  if (error) {
    const mapped = checkoutError(error.message);
    return structuredError(mapped.code, mapped.message, 409, mapped.retryable);
  }
  await audit(client, profileId, "checkout.intent", "shop_checkout_session", (data as Json)?.id as string | undefined);
  return response({ ok: true, checkout: data as Json });
}

async function confirmCheckout(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const checkoutSessionId = String(body.checkoutSessionId || "");
  const addressId = String(body.addressId || "");
  const paymentMethodCode = String(body.paymentMethodCode || "");
  const idempotencyKey = String(body.idempotencyKey || request.headers.get("x-idempotency-key") || "").trim();
  if (!checkoutSessionId || !addressId || !paymentMethodCode || idempotencyKey.length < 8) {
    return structuredError("invalid_checkout_request", "Check checkout details and try again.", 400, true);
  }
  const { data, error } = await client.rpc("adishop_confirm_checkout", {
    p_profile_id: profileId,
    p_checkout_session_id: checkoutSessionId,
    p_address_id: addressId,
    p_payment_method_code: paymentMethodCode,
    p_idempotency_key: idempotencyKey,
  });
  if (error) {
    const mapped = checkoutError(error.message);
    return structuredError(mapped.code, mapped.message, 409, mapped.retryable);
  }
  const result = data as Json;
  await audit(client, profileId, "checkout.confirm_request", "shop_order", result?.orderId as string | undefined);
  return response({
    ok: true,
    order: {
      id: result?.orderId || result?.id,
      order_number: result?.orderNumber || result?.order_number,
      status: result?.state || result?.status,
      payment_status: result?.paymentStatus || result?.payment_status,
      fulfillment_status: result?.fulfillmentStatus || result?.fulfillment_status,
      delivery_status: result?.delivery_status,
      total_minor: result?.total_minor,
      created_at: result?.created_at,
    },
  });
}

type ShopConversationAccess = {
  conversation: Json;
  role: "customer" | "seller";
};

async function conversationAccess(
  client: ReturnType<typeof adminClient>,
  profileId: string,
  conversationId: string,
): Promise<ShopConversationAccess | null> {
  if (!conversationId) return null;
  const { data, error } = await client
    .from("shop_conversations")
    .select("id,store_id,customer_profile_id,context_type,product_id,order_group_id,status,last_message_id,last_message_preview,last_message_at,created_at,updated_at")
    .eq("id", conversationId)
    .single();
  if (error || !data) return null;
  const conversation = data as Json;
  if (String(conversation.customer_profile_id || "") === profileId) {
    return { conversation, role: "customer" };
  }
  const stores = await sellerStoreIds(client, profileId);
  if (stores.includes(String(conversation.store_id || ""))) {
    return { conversation, role: "seller" };
  }
  return null;
}

async function conversationSummary(
  client: ReturnType<typeof adminClient>,
  conversation: Json,
  profileId: string,
): Promise<Json> {
  const storeId = String(conversation.store_id || "");
  const customerId = String(conversation.customer_profile_id || "");
  const { data: state } = await client
    .from("shop_conversation_participant_state")
    .select("last_read_at,archived_at,muted_until,deleted_at,typing_expires_at")
    .eq("conversation_id", conversation.id)
    .eq("profile_id", profileId)
    .maybeSingle();
  const [{ data: store }, { data: customer }, { count: unreadCount }, { count: typingPeerCount }] = await Promise.all([
    client.from("shop_stores").select("id,name,logo_url,status,approval_status").eq("id", storeId).maybeSingle(),
    client.from("shop_profiles").select("id,display_name,status").eq("id", customerId).maybeSingle(),
    client
      .from("shop_messages")
      .select("id", { count: "exact", head: true })
      .eq("conversation_id", conversation.id)
      .neq("sender_profile_id", profileId)
      .gt("created_at", String((state as Json | null)?.last_read_at || "1970-01-01T00:00:00.000Z"))
      .is("deleted_at", null),
    client
      .from("shop_conversation_participant_state")
      .select("profile_id", { count: "exact", head: true })
      .eq("conversation_id", conversation.id)
      .neq("profile_id", profileId)
      .gt("typing_expires_at", new Date().toISOString()),
  ]);
  return {
    ...conversation,
    store: store || null,
    customer: customer || null,
    participant_state: state || null,
    unread_count: unreadCount || 0,
    peer_typing: (typingPeerCount || 0) > 0,
  };
}

async function listShopConversations(request: Request, sellerOnly = false) {
  const { client, profileId } = await currentSession(request);
  const url = new URL(request.url);
  const search = String(url.searchParams.get("search") || "").trim().toLowerCase();
  const archived = url.searchParams.get("archived") === "true";
  let query = client
    .from("shop_conversations")
    .select("id,store_id,customer_profile_id,context_type,product_id,order_group_id,status,last_message_id,last_message_preview,last_message_at,created_at,updated_at")
    .order("last_message_at", { ascending: false, nullsFirst: false })
    .order("created_at", { ascending: false })
    .limit(100);

  if (sellerOnly) {
    const storeIds = await sellerStoreIds(client, profileId);
    if (storeIds.length === 0) return response({ ok: true, conversations: [], unreadCount: 0 });
    query = query.in("store_id", storeIds);
  } else {
    query = query.eq("customer_profile_id", profileId);
  }

  const { data, error } = await query;
  if (error) return safeError("Messages could not be loaded.", 500);
  const summaries: Json[] = await Promise.all(
    (data || []).map((row) => conversationSummary(client, row as Json, profileId)),
  );
  const visible = summaries.filter((row) => {
    const state = (row.participant_state || {}) as Json;
    if (Boolean(state.deleted_at)) return false;
    if (Boolean(state.archived_at) !== archived) return false;
    if (!search) return true;
    const store = (row.store || {}) as Json;
    const customer = (row.customer || {}) as Json;
    return [
      row.last_message_preview,
      store.name,
      customer.display_name,
    ].some((value) => String(value || "").toLowerCase().includes(search));
  });
  return response({
    ok: true,
    conversations: visible,
    unreadCount: visible.reduce((sum, row) => sum + Number(row.unread_count || 0), 0),
  });
}

async function openShopConversation(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const productId = String(body.productId || "");
  const orderGroupId = String(body.orderGroupId || "");
  let storeId = String(body.storeId || "");
  let contextType: "general" | "product" | "order" = "general";

  if (orderGroupId) {
    const { data } = await client
      .from("shop_order_groups")
      .select("id,store_id,shop_orders!inner(profile_id)")
      .eq("id", orderGroupId)
      .single();
    const order = ((data as Json | null)?.shop_orders || {}) as Json;
    if (!data || String(order.profile_id || "") !== profileId) {
      return safeError("Order conversation is unavailable.", 404);
    }
    storeId = String((data as Json).store_id || "");
    contextType = "order";
  } else if (productId) {
    const { data } = await client
      .from("shop_products")
      .select("id,store_id,status,moderation_status,deleted_at,shop_stores!inner(status,approval_status,deleted_at)")
      .eq("id", productId)
      .single();
    const store = ((data as Json | null)?.shop_stores || {}) as Json;
    if (
      !data ||
      data.status !== "active" ||
      data.moderation_status !== "approved" ||
      data.deleted_at ||
      store.status !== "active" ||
      store.approval_status !== "approved" ||
      store.deleted_at
    ) {
      return safeError("Product conversation is unavailable.", 404);
    }
    storeId = String((data as Json).store_id || "");
    contextType = "product";
  }

  if (!storeId) return safeError("Store conversation is unavailable.", 404);
  const { data: store } = await client
    .from("shop_stores")
    .select("id,status,approval_status,deleted_at")
    .eq("id", storeId)
    .single();
  if (!store || store.status !== "active" || store.approval_status !== "approved" || store.deleted_at) {
    return safeError("Store conversation is unavailable.", 404);
  }

  let existing = client
    .from("shop_conversations")
    .select("*")
    .eq("store_id", storeId)
    .eq("customer_profile_id", profileId)
    .eq("context_type", contextType);
  existing = contextType === "product"
    ? existing.eq("product_id", productId)
    : contextType === "order"
    ? existing.eq("order_group_id", orderGroupId)
    : existing.is("product_id", null).is("order_group_id", null);
  const current = await existing.maybeSingle();

  let conversation = current.data as Json | null;
  if (!conversation) {
    const created = await client
      .from("shop_conversations")
      .insert({
        store_id: storeId,
        customer_profile_id: profileId,
        context_type: contextType,
        product_id: productId || null,
        order_group_id: orderGroupId || null,
      })
      .select("*")
      .single();
    if (created.error || !created.data) return safeError("Conversation could not be started.", 500);
    conversation = created.data as Json;
  }

  await client.from("shop_conversation_participant_state").upsert({
    conversation_id: conversation.id,
    profile_id: profileId,
    deleted_at: null,
    archived_at: null,
    updated_at: new Date().toISOString(),
  }, { onConflict: "conversation_id,profile_id" });
  return response({ ok: true, conversation: await conversationSummary(client, conversation, profileId) });
}

async function listShopMessages(request: Request) {
  const { client, profileId } = await currentSession(request);
  const url = new URL(request.url);
  const conversationId = String(url.searchParams.get("conversationId") || "");
  const before = String(url.searchParams.get("before") || "");
  const access = await conversationAccess(client, profileId, conversationId);
  if (!access) return safeError("Conversation not found.", 404);
  let query = client
    .from("shop_messages")
    .select("id,conversation_id,sender_profile_id,message_type,body,attachment_path,attachment_name,attachment_mime_type,attachment_size_bytes,client_message_id,created_at,edited_at,deleted_at")
    .eq("conversation_id", conversationId)
    .is("deleted_at", null)
    .order("created_at", { ascending: false })
    .limit(50);
  if (before) query = query.lt("created_at", before);
  const { data, error } = await query;
  if (error) return safeError("Messages could not be loaded.", 500);
  const messages = await Promise.all((data || []).map(async (row) => {
    const message = row as Json;
    if (!message.attachment_path) return message;
    const signed = await client.storage
      .from("shop-chat-attachments")
      .createSignedUrl(String(message.attachment_path), 300);
    return { ...message, attachment_url: signed.data?.signedUrl || null };
  }));
  return response({
    ok: true,
    conversation: await conversationSummary(client, access.conversation, profileId),
    messages: messages.reverse(),
  });
}

async function persistShopMessage(
  client: ReturnType<typeof adminClient>,
  profileId: string,
  access: ShopConversationAccess,
  values: Json,
  clientMessageId: string,
  preview: string,
) {
  const conversationId = String(access.conversation.id || "");
  const existing = await client
    .from("shop_messages")
    .select("*")
    .eq("sender_profile_id", profileId)
    .eq("client_message_id", clientMessageId)
    .maybeSingle();
  if (existing.data) return response({ ok: true, message: existing.data });

  const inserted = await client
    .from("shop_messages")
    .insert({
      ...values,
      conversation_id: conversationId,
      sender_profile_id: profileId,
      client_message_id: clientMessageId,
    })
    .select("*")
    .single();
  if (inserted.error || !inserted.data) return safeError("Message could not be sent.", 500);
  const now = new Date().toISOString();
  await Promise.all([
    client.from("shop_conversations").update({
      last_message_id: inserted.data.id,
      last_message_preview: preview.slice(0, 160),
      last_message_at: inserted.data.created_at,
      updated_at: now,
    }).eq("id", conversationId),
    client.from("shop_conversation_participant_state").upsert({
      conversation_id: conversationId,
      profile_id: profileId,
      last_read_message_id: inserted.data.id,
      last_read_at: inserted.data.created_at,
      archived_at: null,
      deleted_at: null,
      typing_expires_at: null,
      updated_at: now,
    }, { onConflict: "conversation_id,profile_id" }),
  ]);

  const recipients = access.role === "seller"
    ? [String(access.conversation.customer_profile_id || "")]
    : [] as string[];
  if (access.role === "customer") {
    const storeId = String(access.conversation.store_id || "");
    const [{ data: store }, { data: members }] = await Promise.all([
      client.from("shop_stores").select("owner_profile_id").eq("id", storeId).single(),
      client.from("shop_store_members").select("profile_id").eq("store_id", storeId).eq("status", "active"),
    ]);
    const recipientSet = new Set<string>([
      String((store as Json | null)?.owner_profile_id || ""),
      ...(members || []).map((row) => String((row as Json).profile_id || "")),
    ]);
    recipients.push(...[...recipientSet].filter(Boolean));
  }
  const uniqueRecipients = [...new Set(recipients.filter(Boolean))];
  await Promise.all(uniqueRecipients.map((recipientId) =>
    client.rpc("adishop_create_notification", {
      p_profile_id: recipientId,
      p_type: "shop_message",
      p_title: access.role === "seller" ? "New seller message" : "New customer message",
      p_body: preview.slice(0, 120),
      p_data: { conversationId, storeId: access.conversation.store_id },
    })
  ));
  const bridgeUrl = String(Deno.env.get("ADISHOP_PUSH_BRIDGE_URL") || "").trim();
  const bridgeSecret = String(Deno.env.get("ADISHOP_PUSH_BRIDGE_SECRET") || "").trim();
  if (bridgeUrl && bridgeSecret && uniqueRecipients.length) {
    const { data: recipientProfiles } = await client
      .from("shop_profiles")
      .select("id,adischat_user_id")
      .in("id", uniqueRecipients);
    const title = access.role === "seller" ? "New seller message" : "New customer message";
    await Promise.all((recipientProfiles || []).map(async (profile) => {
      const receiverUid = String((profile as Json).adischat_user_id || "").trim();
      if (!receiverUid) return;
      await fetch(bridgeUrl, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-adishop-push-secret": bridgeSecret,
        },
        body: JSON.stringify({
          receiverUid,
          conversationId,
          messageId: inserted.data.id,
          storeId: access.conversation.store_id,
          title,
          body: preview.slice(0, 120),
        }),
      }).catch(() => null);
    }));
  }
  await audit(client, profileId, "message.send", "shop_conversation", conversationId, {
    messageType: String(values.message_type || "text"),
  });
  return response({ ok: true, message: inserted.data });
}

async function sendShopMessage(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const conversationId = String(body.conversationId || "");
  const messageBody = String(body.body || "").trim();
  const clientMessageId = String(body.clientMessageId || "").trim();
  const access = await conversationAccess(client, profileId, conversationId);
  if (!access) return safeError("Conversation not found.", 404);
  if (!messageBody || messageBody.length > 4000 || clientMessageId.length < 8 || clientMessageId.length > 160) {
    return safeError("Check the message and try again.", 400);
  }
  return persistShopMessage(
    client,
    profileId,
    access,
    { message_type: "text", body: messageBody },
    clientMessageId,
    messageBody,
  );
}

const SHOP_CHAT_ATTACHMENT_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
  "application/pdf",
  "text/plain",
]);

function safeAttachmentName(value: string) {
  const normalized = value.normalize("NFKC").replace(/[^a-zA-Z0-9._ -]/g, "_").trim();
  return normalized.slice(0, 120) || "attachment";
}

function attachmentExtension(mimeType: string) {
  if (mimeType === "image/jpeg") return "jpg";
  if (mimeType === "image/png") return "png";
  if (mimeType === "image/webp") return "webp";
  if (mimeType === "application/pdf") return "pdf";
  return "txt";
}

async function uploadShopMessageAttachment(request: Request) {
  const { client, profileId } = await currentSession(request);
  const form = await request.formData();
  const conversationId = String(form.get("conversationId") || "");
  const clientMessageId = String(form.get("clientMessageId") || "").trim();
  const caption = String(form.get("body") || "").trim().slice(0, 4000);
  const attachment = form.get("attachment");
  const access = await conversationAccess(client, profileId, conversationId);
  if (!access) return safeError("Conversation not found.", 404);
  if (!(attachment instanceof File) || attachment.size < 1 || attachment.size > 10 * 1024 * 1024) {
    return safeError("Choose a file smaller than 10 MB.", 400);
  }
  const mimeType = String(attachment.type || "").toLowerCase();
  if (!SHOP_CHAT_ATTACHMENT_TYPES.has(mimeType)) {
    return safeError("This file type is not supported.", 400);
  }
  if (clientMessageId.length < 8 || clientMessageId.length > 160) {
    return safeError("Attachment could not be sent.", 400);
  }
  const fileName = safeAttachmentName(attachment.name);
  const path = `${conversationId}/${profileId}/${crypto.randomUUID()}.${attachmentExtension(mimeType)}`;
  const uploaded = await client.storage.from("shop-chat-attachments").upload(path, attachment, {
    contentType: mimeType,
    upsert: false,
  });
  if (uploaded.error) return safeError("Attachment could not be uploaded.", 500);
  const messageType = mimeType.startsWith("image/") ? "image" : "file";
  const preview = caption || (messageType === "image" ? `Photo: ${fileName}` : `File: ${fileName}`);
  const result = await persistShopMessage(
    client,
    profileId,
    access,
    {
      message_type: messageType,
      body: caption || null,
      attachment_path: path,
      attachment_name: fileName,
      attachment_mime_type: mimeType,
      attachment_size_bytes: attachment.size,
    },
    clientMessageId,
    preview,
  );
  if (!result.ok) {
    await client.storage.from("shop-chat-attachments").remove([path]);
  }
  return result;
}

async function updateShopConversation(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const conversationId = String(body.conversationId || "");
  const action = String(body.action || "");
  const access = await conversationAccess(client, profileId, conversationId);
  if (!access) return safeError("Conversation not found.", 404);
  const now = new Date().toISOString();
  const values: Json = { conversation_id: conversationId, profile_id: profileId, updated_at: now };
  if (action === "read") {
    const [{ data: latest }, { data: currentState }] = await Promise.all([
      client
        .from("shop_messages")
        .select("id,created_at")
        .eq("conversation_id", conversationId)
        .is("deleted_at", null)
        .order("created_at", { ascending: false })
        .limit(1)
        .maybeSingle(),
      client
        .from("shop_conversation_participant_state")
        .select("last_read_message_id")
        .eq("conversation_id", conversationId)
        .eq("profile_id", profileId)
        .maybeSingle(),
    ]);
    if (!latest || String((currentState as Json | null)?.last_read_message_id || "") === String(latest.id)) {
      return response({ ok: true, conversation: await conversationSummary(client, access.conversation, profileId) });
    }
    values.last_read_message_id = latest?.id || null;
    values.last_read_at = latest.created_at;
  } else if (action === "archive") {
    values.archived_at = now;
  } else if (action === "unarchive") {
    values.archived_at = null;
  } else if (action === "mute") {
    const hours = Math.max(1, Math.min(24 * 30, Number(body.hours || 8)));
    values.muted_until = new Date(Date.now() + hours * 60 * 60 * 1000).toISOString();
  } else if (action === "unmute") {
    values.muted_until = null;
  } else if (action === "delete") {
    values.deleted_at = now;
  } else if (action === "typing") {
    values.typing_expires_at = new Date(Date.now() + 8_000).toISOString();
  } else if (action === "stopped_typing") {
    values.typing_expires_at = null;
  } else {
    return safeError("Conversation action is unavailable.", 400);
  }
  const { error } = await client
    .from("shop_conversation_participant_state")
    .upsert(values, { onConflict: "conversation_id,profile_id" });
  if (error) return safeError("Conversation could not be updated.", 500);
  return response({ ok: true, conversation: await conversationSummary(client, access.conversation, profileId) });
}

async function submitSellerApplication(request: Request) {
  const { client, profileId } = await currentSession(request);
  const body = await jsonBody(request);
  const storeName = String(body.storeName || body.proposedStoreName || "").trim().slice(0, 120);
  if (!storeName) return safeError("Enter your store name.");
  const payload = {
    profile_id: profileId,
    proposed_store_name: storeName,
    business_type: String(body.businessType || "individual").slice(0, 60),
    status: body.submit === false ? "draft" : "submitted",
    submitted_at: body.submit === false ? null : new Date().toISOString(),
    updated_at: new Date().toISOString(),
  };
  const result = await client.from("shop_seller_applications").insert(payload).select("*").single();
  if (result.error) return safeError("We couldn't save your seller application.", 500);
  await audit(client, profileId, payload.status === "draft" ? "seller_application.draft" : "seller_application.submit", "shop_seller_application", result.data.id);
  return response({ ok: true, application: result.data });
}

export async function handleAdishopFunction(name: string, request: Request) {
  try {
    if (name === "adishop-messages") {
      const action = new URL(request.url).searchParams.get("action") || "";
      if (action === "seller-list") return await listShopConversations(request, true);
      if (action === "buyer-list") return await listShopConversations(request, false);
      if (action === "open") return await openShopConversation(request);
      if (action === "messages") return await listShopMessages(request);
      if (action === "send") return await sendShopMessage(request);
      if (action === "upload") return await uploadShopMessageAttachment(request);
      if (action === "update") return await updateShopConversation(request);
      return safeError("Message action unavailable.", 404);
    }
    if (name === "adishop-exchange-adischat-session") return await exchange(request);
    if (name === "adishop-refresh-shop-session") return await refresh(request);
    if (name === "adishop-get-current-profile") return await getCurrentProfile(request);
    if (name === "adishop-get-cart") return await getCart(request);
    if (name === "adishop-add-cart-item") return await addCartItem(request);
    if (name === "adishop-update-cart-item") return await updateCartItem(request);
    if (name === "adishop-remove-cart-item") return await removeCartItem(request);
    if (name === "adishop-clear-cart") return await clearCart(request);
    if (name === "adishop-add-favorite") return await favorite(request, true);
    if (name === "adishop-remove-favorite") return await favorite(request, false);
    if (name === "adishop-list-favorites") return await listFavorites(request);
    if (name === "adishop-record-product-view") return await recordView(request);
    if (name === "adishop-list-recently-viewed") return await listRecentlyViewed(request);
    if (name === "adishop-list-addresses") return await listAddresses(request);
    if (name === "adishop-create-address") return await upsertAddress(request, "create");
    if (name === "adishop-update-address") return await upsertAddress(request, "update");
    if (name === "adishop-delete-address") return await deleteAddress(request);
    if (name === "adishop-set-default-address") return await setDefaultAddress(request);
    if (name === "adishop-list-orders") return await listOrders(request);
    if (name === "adishop-get-order") return await getOrder(request);
    if (name === "adishop-buyer-post-delivery-actions") return await buyerPostDeliveryActions(request);
    if (name === "adishop-buyer-review-options") return await buyerReviewOptions(request);
    if (name === "adishop-buyer-create-product-review") return await buyerCreateProductReview(request);
    if (name === "adishop-buyer-create-store-review") return await buyerCreateStoreReview(request);
    if (name === "adishop-buyer-order-reviews") return await buyerOrderReviews(request);
    if (name === "adishop-product-reviews") return await publicProductReviews(request);
    if (name === "adishop-store-reviews") return await publicStoreReviews(request);
    if (name === "adishop-buyer-create-refund-request") return await buyerCreateRefundRequest(request);
    if (name === "adishop-buyer-refund-requests") return await buyerRequests(request, "refundRequests");
    if (name === "adishop-buyer-create-exchange-request") return await buyerCreateExchangeRequest(request);
    if (name === "adishop-buyer-exchange-requests") return await buyerRequests(request, "exchangeRequests");
    if (name === "adishop-buyer-buy-again") return await buyerBuyAgain(request);
    if (name === "adishop-buyer-order-receipt") return await buyerOrderReceipt(request);
    if (name === "adishop-request-order-cancellation") return await cancelOrder(request);
    if (name === "adishop-reveal-delivery-code") return await revealDeliveryCode(request);
    if (name === "adishop-buyer-order-issue-options") return await buyerOrderIssueOptions(request);
    if (name === "adishop-buyer-create-order-issue") return await buyerCreateOrderIssue(request);
    if (name === "adishop-buyer-order-issues") return await buyerOrderIssues(request);
    if (name === "adishop-list-notifications") return await listNotifications(request);
    if (name === "adishop-notification-unread-count") return await notificationUnreadCount(request);
    if (name === "adishop-mark-notification-read") return await markNotificationRead(request, false);
    if (name === "adishop-mark-all-notifications-read") return await markNotificationRead(request, true);
    if (name === "adishop-list-seller-order-groups") return await listSellerOrderGroups(request);
    if (name === "adishop-get-seller-order-group") return await getSellerOrderGroup(request);
    if (name === "adishop-seller-accept-order-group") return await transitionSellerOrderGroup(request, "accept");
    if (name === "adishop-seller-reject-order-group") return await transitionSellerOrderGroup(request, "reject");
    if (name === "adishop-seller-mark-processing") return await transitionSellerOrderGroup(request, "processing");
    if (name === "adishop-seller-mark-packed") return await transitionSellerOrderGroup(request, "packed");
    if (name === "adishop-seller-mark-ready-for-pickup") return await transitionSellerOrderGroup(request, "ready_for_pickup");
    if (name === "adishop-seller-hand-to-carrier") return await transitionSellerOrderGroup(request, "handed_to_carrier");
    if (name === "adishop-seller-verify-delivery-code") return await verifySellerDeliveryCode(request);
    if (name === "adishop-seller-requests") return await sellerRequests(request);
    if (name === "adishop-seller-respond-refund-request") return await sellerRespondToRequest(request, "refund");
    if (name === "adishop-seller-respond-exchange-request") return await sellerRespondToRequest(request, "exchange");
    if (name === "adishop-seller-products") return await listSellerProducts(request);
    if (name === "adishop-seller-product-detail") return await getSellerProduct(request);
    if (name === "adishop-seller-create-product") return await createSellerProduct(request);
    if (name === "adishop-seller-update-product") return await updateSellerProduct(request);
    if (name === "adishop-seller-submit-product") return await submitSellerProduct(request);
    if (name === "adishop-seller-upload-product-media") return await uploadSellerProductMedia(request);
    if (name === "adishop-seller-delete-product-media") return await deleteSellerProductMedia(request);
    if (name === "adishop-seller-reorder-product-media") return await reorderSellerProductMedia(request);
    if (name === "adishop-create-checkout") return await createCheckout(request);
    if (name === "adishop-confirm-checkout") return await confirmCheckout(request);
    if (name === "adishop-submit-seller-application") return await submitSellerApplication(request);
    if (name === "adishop-get-seller-application-status") return await sellerStatus(request);
    if (name === "adishop-admin-session") return await adminSession(request);
    if (name === "adishop-admin-dashboard") return await adminDashboard(request);
    if (name === "adishop-admin-seller-applications") return await adminSellerApplications(request);
    if (name === "adishop-admin-seller-application-detail") return await adminSellerApplicationDetail(request);
    if (name === "adishop-admin-review-seller") return await adminReviewSeller(request);
    if (name === "adishop-admin-products") return await adminProducts(request);
    if (name === "adishop-admin-product-detail") return await adminProductDetail(request);
    if (name === "adishop-admin-review-product") return await adminReviewProduct(request);
    if (name === "adishop-admin-orders") return await adminOrders(request);
    if (name === "adishop-admin-order-detail") return await adminOrderDetail(request);
    if (name === "adishop-admin-update-order-issue") return await adminUpdateOrderIssue(request);
    if (name === "adishop-admin-add-order-note") return await adminAddOrderNote(request);
    if (name === "adishop-admin-audit-log") return await adminAuditLog(request);
    if (name === "adishop-admin-users") return await adminUsers(request);
    if (name === "adishop-admin-stores") return await adminStores(request);
    if (name === "adishop-admin-store-detail") return await adminStoreDetail(request);
    if (name === "adishop-admin-update-store-status") return await adminUpdateStoreStatus(request);
    if (name === "adishop-admin-assign-role") return await adminRoleMutation(request, "assign");
    if (name === "adishop-admin-revoke-role") return await adminRoleMutation(request, "revoke");
    return safeError("Shop action unavailable.", 404);
  } catch (error) {
    const code = error instanceof Error ? error.message : "";
    if (["missing-session", "bad-token", "bad-alg", "bad-signature", "bad-claims", "expired", "invalid-session"].includes(code)) {
      return safeError("Please sign in again.", 401);
    }
    return safeError("We couldn't sync your Shop account.", 500);
  }
}
