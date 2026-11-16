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
  return response({ ok: true, order: data });
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
  if (error) return safeError("This seller action is not allowed for the current order state.", 409);
  await audit(client, profileId, `seller_order.${action}`, "shop_order_group", orderGroupId);
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
  await audit(client, profileId, "seller_order.verify_delivery_code", "shop_order_group", orderGroupId);
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
  if (normalized.includes("store-not-active")) return { code: "store_not_active", message: "The store must be active before approval.", status: 409 };
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
  const body = await jsonBody(request);
  return adminRpc(request, "adishop_admin_review_product", {
    p_product_id: String(body.productId || ""),
    p_action: String(body.action || ""),
    p_reason_code: String(body.reasonCode || ""),
    p_public_reason: String(body.publicReason || body.reason || ""),
    p_private_notes: String(body.privateNotes || ""),
    p_request_id: String(body.requestId || request.headers.get("x-request-id") || ""),
    p_expected_submitted_at: body.expectedSubmittedAt ? String(body.expectedSubmittedAt) : null,
  }, "productDetail", "p_actor_profile_id");
}

async function adminOrders(request: Request) {
  const params = pageParams(request);
  return adminRpc(request, "adishop_admin_list_orders", { p_search: params.p_search, p_limit: params.p_limit, p_offset: params.p_offset }, "orders");
}

async function adminAddOrderNote(request: Request) {
  const body = await jsonBody(request);
  return adminRpc(request, "adishop_admin_add_order_note", {
    p_order_id: String(body.orderId || ""),
    p_note: String(body.note || ""),
    p_visibility: String(body.visibility || "private"),
  }, "orderNote");
}

async function adminAuditLog(request: Request) {
  const params = pageParams(request);
  return adminRpc(request, "adishop_admin_audit_log", { p_limit: params.p_limit, p_offset: params.p_offset }, "auditLog");
}

async function adminUsers(request: Request) {
  return adminRpc(request, "adishop_admin_users", {}, "adminUsers");
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
    if (name === "adishop-request-order-cancellation") return await cancelOrder(request);
    if (name === "adishop-reveal-delivery-code") return await revealDeliveryCode(request);
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
    if (name === "adishop-seller-products") return await listSellerProducts(request);
    if (name === "adishop-seller-product-detail") return await getSellerProduct(request);
    if (name === "adishop-seller-create-product") return await createSellerProduct(request);
    if (name === "adishop-seller-update-product") return await updateSellerProduct(request);
    if (name === "adishop-seller-submit-product") return await submitSellerProduct(request);
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
    if (name === "adishop-admin-add-order-note") return await adminAddOrderNote(request);
    if (name === "adishop-admin-audit-log") return await adminAuditLog(request);
    if (name === "adishop-admin-users") return await adminUsers(request);
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
