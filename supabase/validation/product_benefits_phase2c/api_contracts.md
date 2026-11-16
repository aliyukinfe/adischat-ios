# Product Benefits Phase 2C: proposed contracts

Review artifact, not a deployed API. Existing Shop-session authentication, store
ownership, and service-role-only RPC invocation remain mandatory. All actor IDs
below are server-derived. Never accept `approved`, `reviewerId`, or final totals
from a client.

## Seller

| Existing-function extension (proposed) | Request | Response / checks |
| --- | --- | --- |
| `POST /seller/benefit-claims` | `storeId`, `productId?`, `brandId?`, `kind`, terms, `requestKey` | Draft ID/version. Resolve product and canonical brand within selected store; reject foreign IDs. Initial draft submission is owner-only in SQL draft; staff policy is pending. |
| `POST /seller/benefit-evidence` | multipart file, `caseId`, `storeId`, `requestKey` | Private evidence metadata only. Validate MIME by content and extension, size, checksum, path scope, ownership, and duplicate request. Reserve a pending database job before upload to private `shop-benefit-evidence`; finalize metadata transactionally. A failed finalize leaves the pending job for an idempotent retry or separately authorized cleanup. The cleanup route is disabled by default and is never scheduled. |
| `POST /seller/benefit-submit` | `caseId`, `storeId`, `expectedVersion`, `requestKey` | Atomic `submitted` transition. Safe 409 on stale version; no duplicate event. |
| `GET /seller/benefit-claims` | selected `storeId`, cursor | Own-store status, effective/expiry dates, public reason, version. No private Storage path or preview URL. |
| `POST /seller/delivery-rules` | zone, method, product scope, group threshold, fee, funding source, validity | Draft rule; seller cannot mark platform-funded without a separate platform authorization. |
| `POST /seller/price-offers` | product/variant, tier bounds or promotion, amount, period, campaign | Draft offer; canonical ETB minor units; no percentage badge until reference-price policy is resolved. |

All mutation routes require a valid Shop session, current selected-store scope,
server-side ownership, idempotency key and explicit version where applicable.
Never write several independent Edge/Storage operations as if they were one DB
transaction.

| Actor | Own-store draft/submit | Evidence | Review/approve/revoke | Buyer projection |
| --- | --- | --- | --- | --- |
| Seller owner | Yes | Own case only | No, including dual-role accounts | Public only |
| Other seller / foreign store | No | No | No | Public only |
| Independent Shop Admin with `benefit.review` and `benefit.evidence.read` | No seller claim submission | Authorized, audited byte proxy | Yes, except their own store | Public only |
| Super Admin with `benefit.brand.manage` and evidence permission | No seller claim submission | Authorized, audited byte proxy | Yes, except their own store | Public only |
| Buyer / anonymous | No | No | No | Effective, public-safe fields only |

## Admin

`GET /admin/benefit-queue`, `GET /admin/benefit-case/{id}`, and
`POST /admin/benefit-decision` use `benefit.review` or narrower permissions.
Decision body: `caseId`, `expectedVersion`, `action` (`approve|reject|revoke`),
`reason`, `effectiveAt`, `expiresAt`, `requestKey`. Only the server-sourced admin
profile may decide; an owner, member, or submitter of the subject store is denied
even if the account also has an admin role. Evidence metadata contains no Storage
path. A separate GET preview request revalidates the Shop session, evidence
permission and reviewer separation, records an append-only access event, then
streams bounded bytes with `Cache-Control: no-store`. It creates no reusable
signed URL. Logs and buyer payloads never contain paths or tokens.
Create/review canonical brands and authorization scope separately; brand aliases
must not be seller-controlled. Audit every decision and evidence access.

Phase 2I local transport mapping (disabled): existing `adishop-admin-product-detail`
serves `benefit-evidence` metadata and `benefit-preview` bytes; existing
`adishop-admin-products` serves `benefit-brands` and version-checked brand
management. The reviewer must inspect every evidence page before the local UI
enables approval. Retiring a brand suppresses its effective claims without
rewriting existing products.

## Buyer projection

`GET /catalog/products/{id}/benefits?zoneId=...&quantity=...&variantId=...`
returns only effective, approved claims for an active approved product/store.
No zone: omit address-dependent delivery claims and return
`deliveryContext: "unknown"`. Never infer Official Store from store approval or
Original Product from brand text. Expired/revoked claims are absent at read time.
Public cache keys include product, store, variant, zone, quantity band, effective
version and expiry; revocation and suspension invalidate immediately.

Phase 2I local trust projection uses existing `adishop-product-reviews` with
`verified-benefits` for Product Details and `verified-benefits-batch` for up to
100 Home product IDs. It is a separate optional enrichment request, so the
existing catalog payload is unchanged. The batch RPC delegates to the same
server-side active-product/store/brand/expiry checks. Home shows at most two
eligible badges; both buyer rendering flags remain disabled.

```json
{
  "productId": "<uuid>",
  "deliveryContext": "unknown",
  "verifiedBenefits": [
    {"type": "seller_warranty", "label": "Seller warranty", "summary": "<verified terms>", "effectiveUntil": "<timestamp>"}
  ],
  "priceQuote": null
}
```

Illustrative contract only: no claim here corresponds to a real product.

## Server-authoritative quote

`POST /checkout/quote` accepts cart or Buy Now source, address ID, selected
variant IDs, quantities and `requestKey`; it derives store, buyer, currency,
base prices, eligibility and available inventory on the server. Response:

```json
{
  "quoteId": "<uuid>", "currency": "ETB", "source": "CART",
  "itemsSubtotalMinor": "<integer>", "discountMinor": "<integer>",
  "shippingMinor": "<integer>", "totalMinor": "<integer>",
  "appliedOfferIds": [], "shippingMethodIds": [],
  "eligibilityVersion": "<opaque>", "expiresAt": "<timestamp>"
}
```

Unknown amounts are omitted or `null`, never zero placeholders. Resolve offers
per variant and seller group. Non-stacking default: choose the eligible offer
with the lowest final payable amount; deterministic tie-break by offer ID.
Do not combine a bulk tier with a campaign/coupon unless separately approved.
Checkout confirmation must revalidate quote version, effective periods, address,
inventory, funding and price in the existing secure order transaction. If stale,
return a safe conflict and require a refreshed quote. Current checkout remains
unchanged until this full path passes staging tests.

Home cards show at most two badges. Product Details may show all applicable
verified benefits with conditions and expiry. No badge is rendered from a draft,
missing location, insufficient price history, or unknown eligibility.
The proposed discount reference is an auditable 30-day prior-price window;
insufficient 30-day price history and new-product handling remain unresolved,
so percentage-off claims stay disabled until that policy is approved.
