import { audit, currentSession, jsonBody, Json, response, safeError } from "./adishop.ts";
import { sellerBenefitAction, sellerBenefitEvidence } from "./adishop-benefits.ts";

type Client = Awaited<ReturnType<typeof currentSession>>["client"];

const STORE_FIELDS = "id,owner_profile_id,name,slug,description,category,city,logo_url,cover_url,status,approval_status,address_line,region_state,country,contact_phone,contact_email,business_hours,social_links,refund_policy_text,exchange_policy_text,shipping_policy_text,warranty_policy_text,customer_service_policy_text,returns_accepted,exchanges_accepted,accepts_cash_on_delivery,created_at,updated_at";

function text(value: unknown, max = 4000) {
  return String(value ?? "").trim().slice(0, max);
}

function integer(value: unknown, min: number, max: number) {
  const parsed = Math.trunc(Number(value));
  if (!Number.isFinite(parsed)) throw new Error("invalid-number");
  return Math.max(min, Math.min(max, parsed));
}

function object(value: unknown): Json {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Json : {};
}

async function ownedStores(client: Client, profileId: string) {
  const [owned, memberships] = await Promise.all([
    client.from("shop_stores").select(STORE_FIELDS).eq("owner_profile_id", profileId).is("deleted_at", null),
    client.from("shop_store_members").select("store_id").eq("profile_id", profileId).eq("status", "active").in("role", ["owner", "admin"]),
  ]);
  if (owned.error || memberships.error) throw owned.error || memberships.error;
  const memberIds = [...new Set((memberships.data || []).map((row) => String(row.store_id)))];
  const memberStores = memberIds.length
    ? await client.from("shop_stores").select(STORE_FIELDS).in("id", memberIds).is("deleted_at", null)
    : { data: [], error: null };
  if (memberStores.error) throw memberStores.error;
  const byId = new Map<string, Json>();
  [...(owned.data || []), ...(memberStores.data || [])].forEach((store) => byId.set(String(store.id), store as Json));
  return [...byId.values()];
}

async function selectedStore(client: Client, profileId: string, requested?: unknown) {
  const stores = await ownedStores(client, profileId);
  const wanted = text(requested, 64);
  const store = (wanted ? stores.find((row) => row.id === wanted) : stores[0]) || null;
  if (!store) throw new Error("seller-store-not-found");
  return { store, stores };
}

function isoDate(value: unknown, endOfDay = false) {
  const raw = text(value, 40);
  if (!raw) return null;
  const date = new Date(/^\d{4}-\d{2}-\d{2}$/.test(raw) ? `${raw}T${endOfDay ? "23:59:59.999" : "00:00:00.000"}Z` : raw);
  if (!Number.isFinite(date.getTime())) throw new Error("invalid-date-range");
  return date.toISOString();
}

async function businessSnapshot(client: Client, profileId: string, requestedStoreId?: unknown, startValue?: unknown, endValue?: unknown) {
  const { store, stores } = await selectedStore(client, profileId, requestedStoreId);
  const storeId = String(store.id);
  const startAt = isoDate(startValue);
  const endAt = isoDate(endValue, true);
  if (startAt && endAt && startAt > endAt) throw new Error("invalid-date-range");
  let orderGroupsQuery = client.from("shop_order_groups").select("id,order_id,status,subtotal_minor,created_at,updated_at").eq("store_id", storeId);
  if (startAt) orderGroupsQuery = orderGroupsQuery.gte("created_at", startAt);
  if (endAt) orderGroupsQuery = orderGroupsQuery.lte("created_at", endAt);
  const [productsResult, orderGroupsResult, productReviewsResult, storeReviewsResult, refundsResult, exchangesResult, couponsResult, campaignsResult, settingsResult, historyResult] = await Promise.all([
    client.from("shop_products").select("id,store_id,title,seller_sku,status,moderation_status,stock_status,price_minor,currency,inventory:shop_inventory(id,product_id,variant_id,quantity_available,quantity_reserved,low_stock_threshold,updated_at),variants:shop_product_variants(id,sku,title,options,status,price_minor)").eq("store_id", storeId).is("deleted_at", null).order("updated_at", { ascending: false }),
    orderGroupsQuery.order("created_at", { ascending: false }),
    client.from("shop_reviews").select("id,profile_id,product_id,rating,title,body,status,verified_purchase,seller_response,seller_response_at,created_at,shop_products(title)").eq("store_id", storeId).order("created_at", { ascending: false }),
    client.from("shop_store_reviews").select("id,profile_id,rating,body,status,verified_purchase,seller_response,seller_response_at,created_at").eq("store_id", storeId).order("created_at", { ascending: false }),
    client.from("shop_refund_requests").select("id,order_id,order_group_id,reason_code,description,requested_amount,currency,status,seller_response,buyer_visible_update,created_at,updated_at").eq("store_id", storeId).order("created_at", { ascending: false }),
    client.from("shop_exchange_requests").select("id,order_id,order_group_id,reason_code,description,status,seller_response,buyer_visible_update,created_at,updated_at").eq("store_id", storeId).order("created_at", { ascending: false }),
    client.from("shop_seller_coupons").select("*").eq("store_id", storeId).order("updated_at", { ascending: false }),
    client.from("shop_campaigns").select("id,title,description,image_url,campaign_type,discount_percent,status,is_active,start_at,end_at,created_at,updated_at").eq("store_id", storeId).order("updated_at", { ascending: false }),
    client.from("shop_seller_settings").select("*").eq("store_id", storeId).maybeSingle(),
    client.from("shop_inventory_history").select("id,product_id,variant_id,previous_quantity,new_quantity,quantity_delta,reason,created_at").eq("store_id", storeId).order("created_at", { ascending: false }).limit(100),
  ]);
  const results = [productsResult, orderGroupsResult, productReviewsResult, storeReviewsResult, refundsResult, exchangesResult, couponsResult, campaignsResult, settingsResult, historyResult];
  const failed = results.find((item) => item.error);
  if (failed?.error) throw failed.error;

  const groups = orderGroupsResult.data || [];
  const orderIds = [...new Set(groups.map((row) => String(row.order_id)))];
  const groupIds = groups.map((row) => String(row.id));
  const [ordersResult, itemsResult, notesResult, paymentsResult] = await Promise.all([
    orderIds.length ? client.from("shop_orders").select("id,profile_id,order_number,status,created_at").in("id", orderIds) : Promise.resolve({ data: [], error: null }),
    groupIds.length ? client.from("shop_order_items").select("order_group_id,product_id,title_snapshot,quantity,line_total_minor").in("order_group_id", groupIds) : Promise.resolve({ data: [], error: null }),
    client.from("shop_seller_customer_notes").select("customer_profile_id,note,updated_at").eq("store_id", storeId),
    orderIds.length ? client.from("shop_payments").select("id,order_id,status,amount_minor,currency,method_code,created_at").in("order_id", orderIds).order("created_at", { ascending: false }) : Promise.resolve({ data: [], error: null }),
  ]);
  const secondFailed = [ordersResult, itemsResult, notesResult, paymentsResult].find((item) => item.error);
  if (secondFailed?.error) throw secondFailed.error;

  const orders = ordersResult.data || [];
  const profileIds = [...new Set(orders.map((row) => String(row.profile_id)))];
  const profilesResult = profileIds.length
    ? await client.from("shop_profiles").select("id,display_name").in("id", profileIds)
    : { data: [], error: null };
  if (profilesResult.error) throw profilesResult.error;
  const profiles = new Map((profilesResult.data || []).map((row) => [String(row.id), text(row.display_name, 100) || "Customer"]));
  const notes = new Map((notesResult.data || []).map((row) => [String(row.customer_profile_id), text(row.note, 1000)]));
  const groupByOrder = new Map(groups.map((row) => [String(row.order_id), row]));
  const customerMap = new Map<string, { displayName: string; orderCount: number; lifetimeSpendMinor: number; latestOrderAt: string; latestOrderGroupId: string; note: string; recentOrders: Json[] }>();
  orders.forEach((order) => {
    const profileIdValue = String(order.profile_id);
    const group = groupByOrder.get(String(order.id));
    if (!group) return;
    const current = customerMap.get(profileIdValue) || { displayName: profiles.get(profileIdValue) || "Customer", orderCount: 0, lifetimeSpendMinor: 0, latestOrderAt: "", latestOrderGroupId: "", note: notes.get(profileIdValue) || "", recentOrders: [] };
    current.orderCount += 1;
    current.lifetimeSpendMinor += Number(group.subtotal_minor || 0);
    current.recentOrders.push({ orderNumber: text(order.order_number, 60), status: text(group.status, 40), totalMinor: Number(group.subtotal_minor || 0), createdAt: String(order.created_at || "") });
    if (!current.latestOrderAt || String(order.created_at) > current.latestOrderAt) {
      current.latestOrderAt = String(order.created_at || "");
      current.latestOrderGroupId = String(group.id);
    }
    customerMap.set(profileIdValue, current);
  });

  const delivered = groups.filter((row) => String(row.status) === "delivered");
  const itemCount = (itemsResult.data || []).reduce((sum, row) => sum + Number(row.quantity || 0), 0);
  const refundCount = (refundsResult.data || []).length;
  const productTotals = new Map<string, { title: string; quantity: number; revenueMinor: number }>();
  (itemsResult.data || []).forEach((row) => {
    const id = String(row.product_id || "");
    const current = productTotals.get(id) || { title: text(row.title_snapshot, 160) || "Product", quantity: 0, revenueMinor: 0 };
    current.quantity += Number(row.quantity || 0);
    current.revenueMinor += Number(row.line_total_minor || 0);
    productTotals.set(id, current);
  });
  const revenueByDay = new Map<string, number>();
  delivered.forEach((row) => {
    const day = String(row.created_at || "").slice(0, 10);
    if (day) revenueByDay.set(day, (revenueByDay.get(day) || 0) + Number(row.subtotal_minor || 0));
  });
  const analytics = {
    revenueMinor: delivered.reduce((sum, row) => sum + Number(row.subtotal_minor || 0), 0),
    orderCount: groups.length,
    deliveredOrderCount: delivered.length,
    productsSold: itemCount,
    refundRate: delivered.length ? refundCount / delivered.length : 0,
    averageOrderValueMinor: groups.length ? Math.round(groups.reduce((sum, row) => sum + Number(row.subtotal_minor || 0), 0) / groups.length) : 0,
    topProducts: [...productTotals.values()].sort((a, b) => b.quantity - a.quantity).slice(0, 10),
    revenueTrend: [...revenueByDay.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([date, revenueMinor]) => ({ date, revenueMinor })),
    rangeStart: startAt,
    rangeEnd: endAt,
    visitors: null,
    conversionRate: null,
  };
  const capturedPayments = (paymentsResult.data || []).filter((row) => ["paid", "captured", "completed"].includes(String(row.status)));
  const finance = {
    payoutsAvailable: false,
    capturedSalesMinor: capturedPayments.reduce((sum, row) => sum + Number(row.amount_minor || 0), 0),
    pendingMinor: (paymentsResult.data || []).filter((row) => ["pending", "payment_pending", "unpaid"].includes(String(row.status))).reduce((sum, row) => sum + Number(row.amount_minor || 0), 0),
    withdrawableMinor: null,
    transactions: paymentsResult.data || [],
    payoutHistory: [],
  };
  return {
    stores,
    selectedStore: store,
    products: productsResult.data || [],
    inventoryHistory: historyResult.data || [],
    coupons: couponsResult.data || [],
    campaigns: campaignsResult.data || [],
    analytics,
    customers: [...customerMap.values()].map((customer) => ({ ...customer, recentOrders: customer.recentOrders.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))).slice(0, 20) })).sort((a, b) => b.latestOrderAt.localeCompare(a.latestOrderAt)),
    productReviews: productReviewsResult.data || [],
    storeReviews: storeReviewsResult.data || [],
    reviewSummary: {
      total: (productReviewsResult.data || []).length + (storeReviewsResult.data || []).length,
      replied: [...(productReviewsResult.data || []), ...(storeReviewsResult.data || [])].filter((row) => text(row.seller_response)).length,
      ratings: [1, 2, 3, 4, 5].map((rating) => ({ rating, count: [...(productReviewsResult.data || []), ...(storeReviewsResult.data || [])].filter((row) => Number(row.rating) === rating).length })),
    },
    refunds: refundsResult.data || [],
    exchanges: exchangesResult.data || [],
    finance,
    settings: settingsResult.data || { store_id: storeId, notifications: {}, shipping: {}, returns: {}, exchanges: {}, security: {}, language: "en", theme: "system" },
  };
}

async function updateStore(client: Client, profileId: string, body: Json) {
  const { store } = await selectedStore(client, profileId, body.storeId);
  const patch: Json = {
    name: text(body.name, 120), description: text(body.description), category: text(body.businessType, 100), address_line: text(body.addressLine, 300),
    city: text(body.city, 100), region_state: text(body.regionState, 100), country: text(body.country, 100) || "Ethiopia",
    contact_phone: text(body.contactPhone, 40), contact_email: text(body.contactEmail, 200), business_hours: object(body.businessHours),
    social_links: object(body.socialLinks), shipping_policy_text: text(body.shippingPolicy), refund_policy_text: text(body.returnPolicy),
    exchange_policy_text: text(body.exchangePolicy), warranty_policy_text: text(body.warrantyPolicy),
    customer_service_policy_text: text(body.supportContact, 1000), updated_at: new Date().toISOString(),
  };
  if (!patch.name) return safeError("Store name is required.", 422);
  const expectedUpdatedAt = text(body.expectedUpdatedAt, 50);
  let query = client.from("shop_stores").update(patch).eq("id", store.id);
  if (expectedUpdatedAt) query = query.eq("updated_at", expectedUpdatedAt);
  const result = await query.select(STORE_FIELDS).maybeSingle();
  if (result.error) throw result.error;
  if (!result.data) return safeError("Store profile changed on another device. Refresh and try again.", 409);
  await audit(client, profileId, "seller.store.update", "shop_store", String(store.id), { fields: Object.keys(patch).filter((key) => key !== "updated_at") });
  return response({ ok: true, selectedStore: result.data as unknown as Json });
}

async function updateInventory(client: Client, profileId: string, body: Json) {
  const { store } = await selectedStore(client, profileId, body.storeId);
  const requestId = text(body.requestId, 160);
  const updates = Array.isArray(body.items) ? body.items.slice(0, 100) as Json[] : [];
  if (!requestId || !updates.length) return safeError("Choose inventory items to update.", 422);
  const normalized = updates.map((row) => ({
    inventoryId: text(row.inventoryId, 64),
    quantityAvailable: integer(row.quantityAvailable, 0, 1000000),
    lowStockThreshold: integer(row.lowStockThreshold, 0, 1000000),
  }));
  if (new Set(normalized.map((row) => row.inventoryId)).size !== normalized.length || normalized.some((row) => !row.inventoryId)) return safeError("Inventory selection is invalid.", 422);
  const result = await client.rpc("shop_seller_bulk_inventory_update", {
    p_actor_profile_id: profileId, p_store_id: store.id, p_request_id: requestId,
    p_reason: text(body.reason, 120) || "manual_adjustment", p_items: normalized,
  });
  if (result.error) {
    if (String(result.error.message).includes("inventory-below-reserved")) return safeError("Available stock cannot be lower than reserved stock.", 409);
    if (String(result.error.message).includes("inventory-item-not-found")) return safeError("One or more inventory items are unavailable.", 404);
    throw result.error;
  }
  return response({ ok: true, inventoryResult: result.data as Json });
}

async function saveCoupon(client: Client, profileId: string, body: Json) {
  const { store } = await selectedStore(client, profileId, body.storeId);
  const code = text(body.code, 40).toUpperCase().replace(/[^A-Z0-9_-]/g, "");
  if (code.length < 3) return safeError("Coupon code must contain at least three letters or numbers.", 422);
  const startsAt = isoDate(body.startsAt);
  const endsAt = isoDate(body.endsAt, true);
  if (startsAt && endsAt && startsAt >= endsAt) return safeError("Coupon end date must be after its start date.", 422);
  const discountType = text(body.discountType, 20) || "percent";
  const discountValue = integer(body.discountValue, 1, 10000000);
  if (!['percent', 'fixed'].includes(discountType) || (discountType === 'percent' && discountValue > 90)) return safeError("Coupon discount is invalid.", 422);
  const payload = {
    store_id: store.id, code, description: text(body.description, 300), discount_type: text(body.discountType, 20) || "percent",
    discount_value: discountValue, minimum_order_minor: integer(body.minimumOrderMinor || 0, 0, 100000000),
    usage_limit: body.usageLimit == null ? null : integer(body.usageLimit, 1, 1000000), starts_at: startsAt, ends_at: endsAt,
    status: text(body.status, 20) || "draft", created_by_profile_id: profileId, updated_at: new Date().toISOString(),
  };
  const result = await client.from("shop_seller_coupons").upsert(payload, { onConflict: "store_id,code" });
  if (result.error) throw result.error;
  await audit(client, profileId, "seller.coupon.save", "shop_store", String(store.id), { code, status: payload.status });
  return response({ ok: true });
}

async function saveCampaign(client: Client, profileId: string, body: Json) {
  const { store } = await selectedStore(client, profileId, body.storeId);
  const title = text(body.title, 120);
  if (!title) return safeError("Campaign title is required.", 422);
  const id = text(body.id, 64);
  const startsAt = isoDate(body.startsAt);
  const endsAt = isoDate(body.endsAt, true);
  if (startsAt && endsAt && startsAt >= endsAt) return safeError("Campaign end date must be after its start date.", 422);
  const productIds = Array.isArray(body.productIds) ? [...new Set(body.productIds.map((value) => text(value, 64)).filter(Boolean))].slice(0, 100) : [];
  if (productIds.length) {
    const products = await client.from("shop_products").select("id").eq("store_id", store.id).in("id", productIds).is("deleted_at", null);
    if (products.error) throw products.error;
    if ((products.data || []).length !== productIds.length) return safeError("A campaign product is unavailable.", 404);
  }
  const payload = {
    store_id: store.id, title, slug: `${title.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "")}-${String(store.id).slice(0, 8)}`,
    description: text(body.description, 500), campaign_type: text(body.campaignType, 30) || "campaign",
    discount_percent: body.discountPercent == null ? null : integer(body.discountPercent, 1, 90), status: text(body.status, 20) || "draft",
    is_active: body.isActive === true, start_at: startsAt, end_at: endsAt, updated_at: new Date().toISOString(),
  };
  const query = id ? client.from("shop_campaigns").update(payload).eq("id", id).eq("store_id", store.id) : client.from("shop_campaigns").insert(payload);
  const result = await query;
  if (result.error) throw result.error;
  const campaignResult = id
    ? await client.from("shop_campaigns").select("id").eq("id", id).eq("store_id", store.id).single()
    : await client.from("shop_campaigns").select("id").eq("store_id", store.id).eq("slug", payload.slug).order("created_at", { ascending: false }).limit(1).single();
  if (campaignResult.error) throw campaignResult.error;
  if (productIds.length) {
    const cleared = await client.from("shop_campaign_products").delete().eq("campaign_id", campaignResult.data.id);
    if (cleared.error) throw cleared.error;
    const linked = await client.from("shop_campaign_products").insert(productIds.map((productId) => ({ campaign_id: campaignResult.data.id, product_id: productId })));
    if (linked.error) throw linked.error;
  }
  await audit(client, profileId, "seller.campaign.save", "shop_store", String(store.id), { campaignId: id || null, status: payload.status });
  return response({ ok: true });
}

async function replyReview(client: Client, profileId: string, body: Json) {
  const { store } = await selectedStore(client, profileId, body.storeId);
  const reviewId = text(body.reviewId, 64);
  const reply = text(body.reply, 1500);
  const type = text(body.reviewType, 20);
  if (!reviewId || !reply) return safeError("Write a reply before saving.", 422);
  const table = type === "store" ? "shop_store_reviews" : "shop_reviews";
  const existing = await client.from(table).select("id,seller_response").eq("id", reviewId).eq("store_id", store.id).maybeSingle();
  if (existing.error) throw existing.error;
  if (!existing.data) return safeError("Review not found.", 404);
  if (text(existing.data.seller_response, 1500) === reply) return response({ ok: true, idempotent: true });
  const result = await client.from(table).update({ seller_response: reply, seller_response_at: new Date().toISOString(), updated_at: new Date().toISOString() }).eq("id", reviewId).eq("store_id", store.id).select("id").maybeSingle();
  if (result.error) throw result.error;
  if (!result.data) return safeError("Review not found.", 404);
  await audit(client, profileId, "seller.review.reply", table, reviewId, { storeId: store.id });
  return response({ ok: true });
}

async function saveCustomerNote(client: Client, profileId: string, body: Json) {
  const { store } = await selectedStore(client, profileId, body.storeId);
  const groupId = text(body.orderGroupId, 64);
  const group = await client.from("shop_order_groups").select("id,order_id").eq("id", groupId).eq("store_id", store.id).maybeSingle();
  if (group.error) throw group.error;
  if (!group.data) return safeError("Customer order not found.", 404);
  const order = await client.from("shop_orders").select("profile_id").eq("id", group.data.order_id).single();
  if (order.error) throw order.error;
  const note = text(body.note, 1000);
  const existing = await client.from("shop_seller_customer_notes").select("note").eq("store_id", store.id).eq("customer_profile_id", order.data.profile_id).maybeSingle();
  if (existing.error) throw existing.error;
  if (text(existing.data?.note, 1000) === note) return response({ ok: true, idempotent: true });
  const result = await client.from("shop_seller_customer_notes").upsert({ store_id: store.id, customer_profile_id: order.data.profile_id, note, updated_by_profile_id: profileId, updated_at: new Date().toISOString() }, { onConflict: "store_id,customer_profile_id" });
  if (result.error) throw result.error;
  await audit(client, profileId, "seller.customer.note", "shop_store", String(store.id), { orderGroupId: groupId });
  return response({ ok: true });
}

async function saveSettings(client: Client, profileId: string, body: Json) {
  const { store } = await selectedStore(client, profileId, body.storeId);
  const payload = {
    store_id: store.id, notifications: object(body.notifications), shipping: object(body.shipping), returns: object(body.returns),
    exchanges: object(body.exchanges), security: object(body.security), language: text(body.language, 5) || "en",
    theme: text(body.theme, 10) || "system", updated_by_profile_id: profileId, updated_at: new Date().toISOString(),
  };
  const result = await client.from("shop_seller_settings").upsert(payload, { onConflict: "store_id" });
  if (result.error) throw result.error;
  await audit(client, profileId, "seller.settings.update", "shop_store", String(store.id), { language: payload.language, theme: payload.theme });
  return response({ ok: true });
}

async function uploadStoreAsset(request: Request, client: Client, profileId: string) {
  const form = await request.formData();
  const { store } = await selectedStore(client, profileId, form.get("storeId"));
  const asset = form.get("asset");
  const kind = text(form.get("kind"), 20);
  if (!(asset instanceof File) || !["logo", "cover"].includes(kind)) return safeError("Choose a store image.", 422);
  if (!["image/jpeg", "image/png", "image/webp"].includes(asset.type) || asset.size > 5 * 1024 * 1024) return safeError("Use a JPG, PNG or WebP image under 5 MB.", 422);
  const ext = asset.type === "image/png" ? "png" : asset.type === "image/webp" ? "webp" : "jpg";
  const path = `${store.id}/${kind}-${crypto.randomUUID()}.${ext}`;
  const uploaded = await client.storage.from("shop-store-assets").upload(path, asset, { contentType: asset.type, upsert: false });
  if (uploaded.error) throw uploaded.error;
  const url = client.storage.from("shop-store-assets").getPublicUrl(path).data.publicUrl;
  const column = kind === "logo" ? "logo_url" : "cover_url";
  const changed = await client.from("shop_stores").update({ [column]: url, updated_at: new Date().toISOString() }).eq("id", store.id);
  if (changed.error) {
    await client.storage.from("shop-store-assets").remove([path]);
    throw changed.error;
  }
  await audit(client, profileId, "seller.store.asset", "shop_store", String(store.id), { kind, path });
  return response({ ok: true, url });
}

export async function handleSellerBusiness(request: Request): Promise<Response> {
  try {
    const { client, profileId } = await currentSession(request);
    const url = new URL(request.url);
    const action = url.searchParams.get("action") || "dashboard";
    if (action === "benefit-evidence") return await sellerBenefitEvidence(request, client, profileId);
    if (action.startsWith("benefit-")) return await sellerBenefitAction(request, client, profileId);
    if (request.method === "GET" && action === "dashboard") {
      const sellerBusiness = await businessSnapshot(client, profileId, url.searchParams.get("storeId"), url.searchParams.get("start"), url.searchParams.get("end"));
      return response({ ok: true, sellerBusiness: sellerBusiness as unknown as Json });
    }
    if (request.method !== "POST") return safeError("Method not allowed.", 405);
    if (action === "store-asset") return await uploadStoreAsset(request, client, profileId);
    const body = await jsonBody(request);
    let result: Response;
    if (action === "store-update") result = await updateStore(client, profileId, body);
    else if (action === "inventory-update") result = await updateInventory(client, profileId, body);
    else if (action === "coupon-save") result = await saveCoupon(client, profileId, body);
    else if (action === "campaign-save") result = await saveCampaign(client, profileId, body);
    else if (action === "review-reply") result = await replyReview(client, profileId, body);
    else if (action === "customer-note") result = await saveCustomerNote(client, profileId, body);
    else if (action === "settings-save") result = await saveSettings(client, profileId, body);
    else return safeError("Seller action is not supported.", 404);
    return result;
  } catch (error) {
    const code = error instanceof Error ? error.message : "seller-business-failed";
    if (["missing-session", "invalid-session", "bad-token", "bad-alg", "bad-signature", "bad-claims", "expired"].includes(code)) {
      return safeError("Please sign in again.", 401);
    }
    if (code === "seller-store-not-found") return safeError("An approved seller store is required.", 403);
    if (code === "invalid-date-range") return safeError("Choose a valid date range.", 422);
    console.error("seller_business_failed", { code });
    return safeError("Seller tools are temporarily unavailable.", 500);
  }
}
