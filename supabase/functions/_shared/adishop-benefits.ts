import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2.110.7";
import { benefitKinds, benefitMime, benefitUuid } from "./adishop-benefits-policy.ts";

type Client = SupabaseClient;
type Body = Record<string, unknown>;
const kinds = new Set<string>(benefitKinds);
const evidenceTypes = new Set(["authorization_letter", "manufacturer_terms", "invoice", "seller_terms", "other"]);

function json(body: Body, status = 200): Response {
  return Response.json(body, { status });
}

function fail(message: string, status: number): Response {
  return json({ ok: false, error: message }, status);
}

function uuid(value: unknown): string | null {
  return benefitUuid(value);
}

function text(value: unknown, limit: number): string {
  return String(value || "").trim().slice(0, limit);
}

async function body(request: Request): Promise<Body> {
  try {
    const value: unknown = await request.json();
    return value && typeof value === "object" && !Array.isArray(value) ? value as Body : {};
  } catch {
    return {};
  }
}

async function ownedStore(client: Client, profileId: string, storeId: string): Promise<boolean> {
  const { data, error } = await client.from("shop_stores")
    .select("id").eq("id", storeId).eq("owner_profile_id", profileId)
    .eq("status", "active").eq("approval_status", "approved")
    .is("suspended_at", null).is("deleted_at", null).maybeSingle();
  return !error && !!data;
}

async function adminPermission(client: Client, profileId: string, permission: string): Promise<boolean> {
  const { data, error } = await client.rpc("adishop_admin_session", { p_profile_id: profileId });
  const permissions = (data as Body | null)?.permissions;
  return !error && Array.isArray(permissions) && permissions.includes(permission);
}

async function reviewerStoreIds(client: Client, profileId: string): Promise<Set<string>> {
  const [owned, member] = await Promise.all([
    client.from("shop_stores").select("id").eq("owner_profile_id", profileId),
    client.from("shop_store_members").select("store_id").eq("profile_id", profileId).eq("status", "active"),
  ]);
  if (owned.error || member.error) throw new Error("benefit-review-scope-unavailable");
  return new Set([...(owned.data || []).map((row) => String(row.id)),
    ...(member.data || []).map((row) => String(row.store_id))]);
}

function dbFailure(message: string): Response {
  if (/benefit-merge-(version-conflict|request-conflict)/.test(message)) {
    return fail("Duplicate review changed. Refresh and try again.", 409);
  }
  if (/benefit-merge-not-found|benefit-brand-not-found/.test(message)) return fail("Record not found.", 404);
  if (/invalid-benefit-merge-(request|decision)/.test(message)) return fail("Check the duplicate review.", 400);
  if (/benefit-(version-conflict|request-conflict|invalid-transition)/.test(message)) {
    return fail("This claim changed. Refresh and try again.", 409);
  }
  if (/benefit-(not-found|separation-of-duties)/.test(message)) return fail("Claim not found.", 404);
  if (/benefit-(evidence-required|details-required|document-expiry-mismatch|validity-required|validity-too-long|brand-mismatch|brand-unavailable|store-unavailable)/.test(message)) {
    return fail("Evidence or validity dates need review.", 422);
  }
  if (/invalid-benefit-request|benefit-brand-not-found/.test(message)) return fail("Check the claim details.", 400);
  return fail("Benefit action is temporarily unavailable.", 503);
}

function claimWithEffectiveStatus(row: Body): Body {
  const expiry = String(row.expires_at || "");
  const expired = row.status === "approved" && Number.isFinite(Date.parse(expiry)) && Date.parse(expiry) <= Date.now();
  return { ...row, effectiveStatus: expired ? "expired" : row.status };
}

export async function sellerBenefitAction(request: Request, client: Client, profileId: string, actionOverride?: string): Promise<Response> {
  const url = new URL(request.url);
  const action = actionOverride || url.searchParams.get("action") || "";
  const input = request.method === "GET" ? {} : await body(request);
  const storeId = uuid(input.storeId || url.searchParams.get("storeId"));
  if (!storeId || !await ownedStore(client, profileId, storeId)) return fail("Store not found.", 404);

  if (action === "benefit-brands" && request.method === "GET") {
    const { data, error } = await client.from("shop_benefit_brands")
      .select("id,canonical_name").eq("status", "active")
      .order("canonical_name").limit(200);
    return error ? fail("Brands are temporarily unavailable.", 503) : json({ ok: true, benefitBrands: data || [] });
  }

  if (action === "benefit-list" && request.method === "GET") {
    const productId = url.searchParams.get("productId");
    if (productId && !uuid(productId)) return fail("Product not found.", 404);
    let query = client.from("shop_benefit_cases")
      .select("id,store_id,product_id,brand_id,kind,status,version,submitted_at,effective_at,expires_at,public_reason,created_at")
      .eq("store_id", storeId).eq("submitted_by_profile_id", profileId)
      .in("kind", [...kinds]).order("created_at", { ascending: false }).limit(100);
    if (productId) query = query.eq("product_id", productId);
    const { data, error } = await query;
    return error ? fail("Claims are temporarily unavailable.", 503) :
      json({ ok: true, benefitClaims: (data || []).map((row) => claimWithEffectiveStatus(row as Body)) });
  }

  if (action === "benefit-create" && request.method === "POST") {
    const kind = String(input.kind || "");
    const productId = uuid(input.productId);
    const brandId = uuid(input.brandId);
    if (!kinds.has(kind) || (kind === "official_store" ? !brandId || !!input.productId :
        !productId || (["original_product", "manufacturer_warranty"].includes(kind) && !brandId))) {
      return fail("Check the claim details.", 400);
    }
    const requestKey = text(input.requestKey, 160);
    if (requestKey.length < 8) return fail("Check the claim details.", 400);
    const coveredIds = Array.isArray(input.coveredProductIds) ? input.coveredProductIds.map(uuid) : [];
    if (coveredIds.some((id) => id === null)) return fail("Check the claim details.", 400);
    const { data, error } = await client.rpc("adishop_benefit_create_claim", {
      p_actor: profileId, p_store_id: storeId, p_product_id: productId,
      p_brand_id: brandId, p_kind: kind,
      p_terms_summary: text(input.termsSummary, 500),
      p_coverage_details: text(input.coverageDetails, 2000),
      p_duration_months: input.durationMonths == null ? null : Number(input.durationMonths),
      p_authorization_scope: kind === "official_store" ? String(input.authorizationScope || "") : null,
      p_covered_product_ids: coveredIds,
      p_request_key: requestKey,
    });
    return error ? dbFailure(error.message) : json({ ok: true, benefitClaim: data });
  }

  if (action === "benefit-submit" && request.method === "POST") {
    const caseId = uuid(input.caseId);
    const expectedVersion = Number(input.expectedVersion);
    const requestKey = text(input.requestKey, 160);
    if (!caseId || !Number.isSafeInteger(expectedVersion) || expectedVersion < 1 || requestKey.length < 8) {
      return fail("Check the claim details.", 400);
    }
    const { data, error } = await client.rpc("adishop_benefit_submit", {
      p_actor: profileId, p_store_id: storeId, p_case_id: caseId,
      p_expected_version: expectedVersion, p_request_key: requestKey,
    });
    return error ? dbFailure(error.message) : json({ ok: true, benefitClaim: data });
  }
  return fail("Benefit action unavailable.", 404);
}

export async function sellerBenefitEvidence(request: Request, client: Client, profileId: string): Promise<Response> {
  if (request.method !== "POST") return fail("Method not allowed.", 405);
  const form = await request.formData().catch(() => null);
  const storeId = uuid(form?.get("storeId"));
  const caseId = uuid(form?.get("caseId"));
  const evidenceType = text(form?.get("evidenceType"), 40);
  const file = form?.get("file");
  if (!storeId || !caseId || !evidenceTypes.has(evidenceType) || !(file instanceof File) ||
      file.size < 1 || file.size > 10 * 1024 * 1024) return fail("Check the evidence file.", 400);
  if (!await ownedStore(client, profileId, storeId)) return fail("Store not found.", 404);
  const { data: claim, error: claimError } = await client.from("shop_benefit_cases")
    .select("id").eq("id", caseId).eq("store_id", storeId)
    .eq("submitted_by_profile_id", profileId).eq("status", "draft").maybeSingle();
  if (claimError || !claim) return fail("Claim not found.", 404);
  const bytes = new Uint8Array(await file.arrayBuffer());
  const mime = benefitMime(bytes);
  if (!mime || file.type !== mime) return fail("Use a JPEG, PNG, or PDF file.", 400);
  const hash = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)))
    .map((value) => value.toString(16).padStart(2, "0")).join("");
  const expires = text(form?.get("documentExpiresAt"), 40);
  if (expires && !Number.isFinite(Date.parse(expires))) return fail("Check the document expiry date.", 400);
  const expiresAt = expires ? new Date(expires).toISOString() : null;
  const requestKey = text(form?.get("requestKey"), 160) || crypto.randomUUID();
  if (requestKey.length < 8) return fail("Check the evidence request.", 400);
  const reserved = await client.rpc("adishop_benefit_upload_reserve", {
    p_actor: profileId, p_store_id: storeId, p_case_id: caseId,
    p_evidence_type: evidenceType, p_mime_type: mime, p_size_bytes: bytes.length,
    p_sha256_hex: hash, p_document_expires_at: expiresAt, p_request_key: requestKey,
  });
  if (reserved.error) return dbFailure(reserved.error.message);
  const reservation = reserved.data as Body | null;
  if (reservation?.state === "complete") {
    const evidenceId = uuid(reservation.evidenceId);
    if (!evidenceId) return fail("Evidence upload is temporarily unavailable.", 503);
    const { data: existing, error } = await client.from("shop_benefit_evidence")
      .select("id,evidence_type,mime_type,document_expires_at,created_at")
      .eq("id", evidenceId).eq("case_id", caseId).is("deleted_at", null).maybeSingle();
    return error || !existing ? fail("Evidence upload is temporarily unavailable.", 503)
      : json({ ok: true, evidence: [{ id: existing.id, evidenceType: existing.evidence_type,
        mimeType: existing.mime_type, documentExpiresAt: existing.document_expires_at,
        createdAt: existing.created_at }] });
  }
  const jobId = uuid(reservation?.jobId);
  const path = String(reservation?.path || "");
  if (reservation?.state !== "pending" || !jobId ||
      !new RegExp(`^${storeId}/${caseId}/[0-9a-f-]{36}\\.(jpg|png|pdf)$`, "i").test(path)) {
    return fail("Evidence upload is temporarily unavailable.", 503);
  }
  const bucket = client.storage.from("shop-benefit-evidence");
  const uploaded = await bucket.upload(path, bytes, { contentType: mime, upsert: false });
  if (uploaded.error) {
    const existing = await bucket.download(path);
    if (existing.error || !existing.data) return fail("Evidence upload is temporarily unavailable.", 503);
    const oldBytes = new Uint8Array(await existing.data.arrayBuffer());
    if (oldBytes.length !== bytes.length || benefitMime(oldBytes) !== mime) {
      return fail("Evidence upload is temporarily unavailable.", 503);
    }
    const oldHash = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", oldBytes)))
      .map((value) => value.toString(16).padStart(2, "0")).join("");
    if (oldHash !== hash) return fail("Evidence upload is temporarily unavailable.", 503);
  }
  const finalized = await client.rpc("adishop_benefit_upload_finalize", {
    p_actor: profileId, p_job_id: jobId,
  });
  if (finalized.error || !finalized.data) {
    console.error("benefit_upload_finalize_pending", { jobId });
    return fail("Evidence upload is pending. Please retry later.", 503);
  }
  return json({ ok: true, evidence: [finalized.data] });
}

export async function adminBenefitUploadCleanup(request: Request, client: Client, profileId: string): Promise<Response> {
  if (request.method !== "POST") return fail("Method not allowed.", 405);
  if (Deno.env.get("ADISHOP_BENEFIT_CLEANUP_ENABLED") !== "true") return fail("Cleanup is unavailable.", 404);
  if (!await adminPermission(client, profileId, "benefit.evidence.cleanup")) return fail("Admin access required.", 403);
  const input = await body(request);
  const limit = Number(input.limit);
  if (input.execute !== true || !Number.isSafeInteger(limit) || limit < 1 || limit > 20) {
    return fail("Check the cleanup request.", 400);
  }
  const claimed = await client.rpc("adishop_benefit_upload_claim_cleanup", { p_actor: profileId, p_limit: limit });
  if (claimed.error || !Array.isArray(claimed.data)) return fail("Cleanup is temporarily unavailable.", 503);
  let cleaned = 0;
  let failed = 0;
  for (const raw of claimed.data) {
    const job = raw as Body;
    const jobId = uuid(job.jobId);
    const leaseId = uuid(job.leaseId);
    const path = String(job.path || "");
    if (!jobId || !leaseId || !/^[0-9a-f-]{36}\/[0-9a-f-]{36}\/[0-9a-f-]{36}\.(jpg|png|pdf)$/i.test(path)) {
      failed++;
      continue;
    }
    const removed = await client.storage.from("shop-benefit-evidence").remove([path]);
    const alreadyAbsent = String((removed.error as unknown as Body | null)?.code || "") === "NoSuchKey";
    const succeeded = !removed.error || alreadyAbsent;
    const finished = await client.rpc("adishop_benefit_upload_finish_cleanup", {
      p_actor: profileId, p_job_id: jobId, p_lease_id: leaseId,
      p_success: succeeded, p_error_code: succeeded ? null : "storage_error",
    });
    if (succeeded && !finished.error && finished.data === true) cleaned++;
    else failed++;
  }
  return json({ ok: failed === 0, claimed: claimed.data.length, cleaned, failed }, failed ? 503 : 200);
}

export async function adminBenefitRead(
  request: Request, client: Client, profileId: string, scope: "product" | "store" | "queue",
): Promise<Response> {
  if (!await adminPermission(client, profileId, "benefit.review")) return fail("Admin access required.", 403);
  const url = new URL(request.url);
  let query = client.from("shop_benefit_cases")
    .select("id,store_id,product_id,brand_id,kind,status,version,submitted_at,reviewed_at,effective_at,expires_at,public_reason,private_reason,created_at")
    .in("kind", [...kinds]).order("created_at", { ascending: false }).limit(100);
  if (scope === "product") {
    const productId = uuid(url.searchParams.get("productId"));
    if (!productId) return fail("Product not found.", 404);
    query = query.eq("product_id", productId);
  } else if (scope === "store") {
    const storeId = uuid(url.searchParams.get("storeId"));
    if (!storeId) return fail("Store not found.", 404);
    query = query.eq("store_id", storeId);
  } else {
    query = query.in("status", ["submitted", "under_review"]);
  }
  const { data, error } = await query;
  if (error) return fail("Claims are temporarily unavailable.", 503);
  try {
    const excluded = await reviewerStoreIds(client, profileId);
    return json({ ok: true, benefitClaims: (data || [])
      .filter((row) => !excluded.has(String(row.store_id)))
      .map((row) => claimWithEffectiveStatus(row as Body)) });
  } catch {
    return fail("Claims are temporarily unavailable.", 503);
  }
}

export async function adminBenefitEvidence(request: Request, client: Client, profileId: string): Promise<Response> {
  if (!await adminPermission(client, profileId, "benefit.evidence.read")) return fail("Admin access required.", 403);
  const caseId = uuid(new URL(request.url).searchParams.get("caseId"));
  if (!caseId) return fail("Claim not found.", 404);
  const { data: claim } = await client.from("shop_benefit_cases").select("id,store_id").eq("id", caseId).maybeSingle();
  if (!claim) return fail("Claim not found.", 404);
  try {
    if ((await reviewerStoreIds(client, profileId)).has(String(claim.store_id))) return fail("Claim not found.", 404);
  } catch {
    return fail("Evidence is temporarily unavailable.", 503);
  }
  const { data, error } = await client.from("shop_benefit_evidence")
    .select("id,evidence_type,mime_type,document_expires_at,created_at")
    .eq("case_id", caseId).is("deleted_at", null);
  if (error) return fail("Evidence is temporarily unavailable.", 503);
  const evidence = (data || []).map((row) => ({ id: row.id, evidenceType: row.evidence_type,
    mimeType: row.mime_type, documentExpiresAt: row.document_expires_at, createdAt: row.created_at }));
  return json({ ok: true, evidence });
}

export async function adminBenefitPreview(request: Request, client: Client, profileId: string): Promise<Response> {
  if (request.method !== "GET") return fail("Method not allowed.", 405);
  if (!await adminPermission(client, profileId, "benefit.evidence.read")) return fail("Admin access required.", 403);
  const evidenceId = uuid(new URL(request.url).searchParams.get("evidenceId"));
  if (!evidenceId) return fail("Evidence not found.", 404);
  const { data: evidence, error } = await client.from("shop_benefit_evidence")
    .select("id,case_id,storage_path,mime_type,size_bytes,sha256_hex")
    .eq("id", evidenceId).is("deleted_at", null).maybeSingle();
  if (error || !evidence || evidence.size_bytes < 1 || evidence.size_bytes > 10 * 1024 * 1024) {
    return fail("Evidence not found.", 404);
  }
  const { data: claim, error: claimError } = await client.from("shop_benefit_cases")
    .select("id,store_id,status").eq("id", evidence.case_id).maybeSingle();
  if (claimError || !claim || claim.status === "draft") return fail("Evidence not found.", 404);
  try {
    if ((await reviewerStoreIds(client, profileId)).has(String(claim.store_id))) return fail("Evidence not found.", 404);
  } catch {
    return fail("Evidence is temporarily unavailable.", 503);
  }
  const recorded = await client.rpc("adishop_benefit_record_evidence_preview", {
    p_actor: profileId, p_evidence_id: evidenceId, p_request_key: crypto.randomUUID(),
  });
  if (recorded.error || recorded.data !== true) return fail("Evidence is temporarily unavailable.", 503);
  const downloaded = await client.storage.from("shop-benefit-evidence").download(evidence.storage_path);
  if (downloaded.error || !downloaded.data) return fail("Evidence is temporarily unavailable.", 503);
  const bytes = new Uint8Array(await downloaded.data.arrayBuffer());
  if (bytes.length < 1 || bytes.length > 10 * 1024 * 1024 || bytes.length !== evidence.size_bytes ||
      benefitMime(bytes) !== evidence.mime_type) {
    return fail("Evidence is temporarily unavailable.", 503);
  }
  const hash = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)))
    .map((value) => value.toString(16).padStart(2, "0")).join("");
  if (hash !== evidence.sha256_hex) return fail("Evidence is temporarily unavailable.", 503);
  return new Response(bytes, { headers: {
    "Content-Type": evidence.mime_type,
    "Content-Disposition": "inline",
    "Cache-Control": "private, no-store, max-age=0",
    "Pragma": "no-cache",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
    "Content-Security-Policy": "sandbox",
  } });
}

export async function adminBenefitHistory(request: Request, client: Client, profileId: string): Promise<Response> {
  if (!await adminPermission(client, profileId, "benefit.review")) return fail("Admin access required.", 403);
  const caseId = uuid(new URL(request.url).searchParams.get("caseId"));
  if (!caseId) return fail("Claim not found.", 404);
  const { data: claim, error: claimError } = await client.from("shop_benefit_cases")
    .select("id,store_id").eq("id", caseId).maybeSingle();
  if (claimError || !claim) return fail("Claim not found.", 404);
  try {
    if ((await reviewerStoreIds(client, profileId)).has(String(claim.store_id))) return fail("Claim not found.", 404);
  } catch {
    return fail("History is temporarily unavailable.", 503);
  }
  const { data, error } = await client.from("shop_benefit_events")
    .select("id,action,previous_status,new_status,case_version,reason,created_at")
    .eq("case_id", caseId).order("created_at", { ascending: true }).limit(100);
  return error ? fail("History is temporarily unavailable.", 503) : json({ ok: true, benefitEvents: data || [] });
}

export async function adminBenefitDecision(request: Request, client: Client, profileId: string): Promise<Response> {
  if (request.method !== "POST") return fail("Method not allowed.", 405);
  if (!await adminPermission(client, profileId, "benefit.review")) return fail("Admin access required.", 403);
  const input = await body(request);
  const caseId = uuid(input.caseId);
  const expectedVersion = Number(input.expectedVersion);
  const action = String(input.benefitAction || "");
  const requestKey = text(input.requestKey, 160);
  if (!caseId || !["approve", "reject", "revoke"].includes(action) ||
      !Number.isSafeInteger(expectedVersion) || expectedVersion < 1 || requestKey.length < 8) {
    return fail("Check the review details.", 400);
  }
  const { data, error } = await client.rpc("adishop_benefit_decide", {
    p_actor: profileId, p_case_id: caseId, p_expected_version: expectedVersion,
    p_action: action, p_reason: text(input.reason, 1600),
    p_effective_at: input.effectiveAt || null, p_expires_at: input.expiresAt || null,
    p_request_key: requestKey,
  });
  return error ? dbFailure(error.message) : json({ ok: true, benefitClaim: data });
}

export async function adminBenefitBrands(request: Request, client: Client, profileId: string): Promise<Response> {
  if (!await adminPermission(client, profileId, "benefit.brand.manage")) return fail("Admin access required.", 403);
  if (request.method === "GET") {
    const [brands, aliases, events, reviews] = await Promise.all([
      client.from("shop_benefit_brands").select("id,canonical_name,status,version")
        .order("canonical_name").limit(300),
      client.from("shop_benefit_brand_aliases").select("brand_id,normalized_alias")
        .is("retired_at", null).limit(1000),
      client.from("shop_benefit_brand_events")
        .select("brand_id,action,normalized_name,reason,brand_version,created_at")
        .order("created_at", { ascending: false }).limit(1000),
      client.from("shop_benefit_brand_merge_reviews")
        .select("id,source_brand_id,target_brand_id,status,reason,version,reviewed_at,decision_reason,created_at")
        .order("created_at", { ascending: false }).limit(100),
    ]);
    if (brands.error || aliases.error || events.error || reviews.error) {
      return fail("Brands are temporarily unavailable.", 503);
    }
    const names = new Map<string, string[]>();
    for (const alias of aliases.data || []) {
      const id = String(alias.brand_id);
      names.set(id, [...(names.get(id) || []), String(alias.normalized_alias)]);
    }
    return json({ ok: true, benefitBrandEvents: events.data || [], benefitMergeReviews: reviews.data || [],
      benefitBrands: (brands.data || []).map((brand) => ({
      ...brand, aliases: names.get(String(brand.id)) || [],
    })) });
  }
  if (request.method !== "POST") return fail("Method not allowed.", 405);
  const input = await body(request);
  const action = String(input.brandAction || "");
  const brandId = input.brandId == null ? null : uuid(input.brandId);
  const version = input.expectedVersion == null ? null : Number(input.expectedVersion);
  const requestKey = text(input.requestKey, 160);
  if (action === "merge_request") {
    const source = uuid(input.sourceBrandId);
    const target = uuid(input.targetBrandId);
    if (!source || !target || source === target || requestKey.length < 8) return fail("Check the duplicate review.", 400);
    const { data, error } = await client.rpc("adishop_benefit_brand_merge_request", {
      p_actor: profileId, p_source: source, p_target: target,
      p_reason: text(input.reason, 500), p_request_key: requestKey,
    });
    return error ? (error.code === "23505" ? fail("A duplicate review is already pending.", 409) : dbFailure(error.message))
      : json({ ok: true, benefitMergeReview: data });
  }
  if (action === "merge_decide") {
    const reviewId = uuid(input.reviewId);
    const decision = String(input.decision || "");
    if (!reviewId || !["confirm_duplicate", "reject"].includes(decision) ||
        !Number.isSafeInteger(version) || version! < 1 || requestKey.length < 8) {
      return fail("Check the duplicate decision.", 400);
    }
    const { data, error } = await client.rpc("adishop_benefit_brand_merge_decide", {
      p_actor: profileId, p_review_id: reviewId, p_expected_version: version,
      p_decision: decision, p_reason: text(input.reason, 500), p_request_key: requestKey,
    });
    return error ? dbFailure(error.message) : json({ ok: true, benefitMergeReview: data });
  }
  if (!["create", "rename", "add_alias", "retire_alias", "retire"].includes(action) ||
      (action !== "create" && (!brandId || !Number.isSafeInteger(version) || version! < 1)) ||
      (action === "create" && (input.brandId != null || input.expectedVersion != null)) ||
      requestKey.length < 8) return fail("Check the brand details.", 400);
  const { data, error } = await client.rpc("adishop_benefit_brand_manage", {
    p_actor: profileId, p_action: action, p_brand_id: brandId,
    p_name: text(input.name, 120), p_expected_version: version,
    p_reason: text(input.reason, 500), p_request_key: requestKey,
  });
  if (error) {
    if (/benefit-brand-(name-conflict|request-conflict|version-conflict|alias-not-found)/.test(error.message)) {
      return fail("Brand changed or name is already in use. Refresh and try again.", 409);
    }
    if (/invalid-benefit-brand-request/.test(error.message)) return fail("Check the brand details.", 400);
    return fail("Brand action is temporarily unavailable.", 503);
  }
  return json({ ok: true, benefitBrand: data });
}

function buyerSafeBenefits(data: unknown): Body[] {
  const seen = new Set<string>();
  return (Array.isArray(data) ? data : []).flatMap((raw: unknown) => {
    if (!raw || typeof raw !== "object") return [];
    const row = raw as Body;
    const until = String(row.effectiveUntil || "");
    const label = text(row.label, 120);
    if (!kinds.has(String(row.type || "")) || !Number.isFinite(Date.parse(until)) || Date.parse(until) <= Date.now()) return [];
    if (!label) return [];
    const key = `${row.type}:${label}`;
    if (seen.has(key)) return [];
    seen.add(key);
    return [{ type: String(row.type), label,
      summary: row.summary == null ? null : text(row.summary, 500), effectiveUntil: until }];
  });
}

export async function publicProductBenefits(request: Request, client: Client): Promise<Response> {
  const productId = uuid(new URL(request.url).searchParams.get("productId"));
  if (!productId) return fail("Product not found.", 404);
  const { data, error } = await client.rpc("adishop_effective_product_trust", { p_product_id: productId });
  if (error) return fail("Benefits are temporarily unavailable.", 503);
  const verifiedBenefits = buyerSafeBenefits(data);
  return json({ ok: true, verifiedBenefits });
}

export async function publicCatalogBenefits(request: Request, client: Client): Promise<Response> {
  if (request.method !== "POST") return fail("Method not allowed.", 405);
  const input = await body(request);
  if (!Array.isArray(input.productIds) || input.productIds.length < 1 || input.productIds.length > 100 ||
      input.productIds.some((id) => !uuid(id))) return fail("Check the product list.", 400);
  const ids = [...new Set(input.productIds as string[])];
  const { data, error } = await client.rpc("adishop_effective_catalog_trust", { p_product_ids: ids });
  if (error || !data || typeof data !== "object" || Array.isArray(data)) {
    return fail("Benefits are temporarily unavailable.", 503);
  }
  const raw = data as Body;
  const benefitsByProductId = Object.fromEntries(ids.map((id) => [id, buyerSafeBenefits(raw[id])]));
  return json({ ok: true, benefitsByProductId });
}
