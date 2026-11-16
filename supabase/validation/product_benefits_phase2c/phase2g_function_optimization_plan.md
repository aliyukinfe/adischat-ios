# Phase 2G: Edge Function optimization plan

Read-only review, 2026-11-04. Project `mcewjfnkrtiiolzibsuh`. No
deployment, deletion, SQL write, production-data change, or billing change.

## Evidence and current architecture

- Supabase function inventory: 100 active functions; all report
  `verify_jwt=false`. The Free-plan limit is 100 functions. Phase 2F classified
  26 critical, 67 active, seven possibly unused, and zero proven retirement
  candidates.
- Most function entrypoints are thin URL-preserving wrappers around
  `_shared/adishop.ts` and its `handleAdishopFunction` dispatcher. The
  `adishop-seller-business` wrapper uses its own shared handler. Shared code
  does not mean shared deployment: each deployed function includes its own
  bundled copy, which can differ in version from neighboring routes and the
  local worktree.
- Android `ShopFunctionApi.kt` hardcodes function URLs. Product listing and
  detail use PostgREST and `adishop_product_catalog_payload`, not a new Edge
  Function. The notification-list route also hosts a secret-authenticated cron
  dispatch branch. Three active cron jobs cover push dispatch, seller-response
  expiry, and checkout-reservation release.
- Repository search found no current Android main-source, nearby web, or
  Cloudflare caller for the seven candidates. One Android device test calls
  `adishop-get-seller-order-group`. No candidate appeared in the available
  24-hour function-edge log aggregation. Neither observation proves historical
  or external non-use; older APK traffic and complete external integrations
  have not been inventoried.

## Consolidation decision

| Option | Immediate function slots | Compatibility and recommendation |
| --- | ---: | --- |
| Extend existing routes with versioned actions | 0 | Preferred for Product Benefits, subject to isolated HTTP/Auth/Storage tests and exact deployed-bundle diffs. Existing request/response contracts must remain unchanged. |
| Add a gateway while retaining legacy wrappers | 0 net if a new gateway is deployed | Useful only as a future migration pattern; it cannot solve the present 100-function cap. Route matching, authentication, rate limits, and response schemas become shared failure domains. |
| Replace old URLs with a gateway and retire wrappers | One slot per retired wrapper, less any new gateway | Unsafe for installed Android versions and unknown external callers until a measured deprecation window and rollback test. |
| Retire confirmed-unused routes | One slot per retired route | No current candidate meets the evidence bar. Do not delete any now. |

Shared implementation already removes much source duplication. Do not
consolidate URLs solely to reduce code lines. **Immediate safe savings: zero.**
The seven candidates yield at most seven theoretical slots after independent
non-use verification, not seven approved deletions.

## Seven possibly unused routes

| Route | Purpose and authorization | Removal risk / missing evidence |
| --- | --- | --- |
| `adishop-get-seller-order-group` | Seller order detail; Shop session, selected-store scope, store-filtered query. | High: Android device test references the URL; old fulfillment clients may call it. |
| `adishop-seller-mark-ready-for-pickup` | Fulfillment transition; Shop session, selected-store check, scoped transition RPC. | High: old seller versions may rely on this exact state transition URL. |
| `adishop-admin-seller-application-detail` | Application detail; Shop session and permission-checked DB RPC. | Medium/high: old Admin UI or external review tool may use it; absence in current Android is not proof. |
| `adishop-product-reviews` | Published product-review read; intentionally public. | Medium: anonymous or web callers may not be visible in Android source. |
| `adishop-store-reviews` | Published store-review read; intentionally public. | Medium: same public-client and URL compatibility risk. |
| `adishop-admin-refund-detail` | Admin refund detail; Shop session plus admin permission. | High: support workflow and old Admin UI compatibility. |
| `adishop-admin-exchange-detail` | Admin exchange detail; Shop session plus admin permission. | High: support workflow and old Admin UI compatibility. |

All seven have real deployed wrappers and handlers, not dead stubs. Before any
retirement, collect a longer function-access window than the available 24-hour
log slice; inspect released APKs, web/Cloudflare deployments, cron/webhooks,
server-to-server callers, and documented support tools. Then test replacement
routes on all supported client versions. Archive the exact deployed bundle,
version, config, and response contract. Recovery is to redeploy that archived
bundle under the original slug, smoke-test auth and response parity, and check
error/traffic metrics. This procedure itself needs a rehearsal before deletion.

## Product Benefits route plan

1. Seller claims/status: add explicitly named, versioned actions to the
   existing `adishop-seller-business` transport, but use dedicated claim RPCs,
   server-derived profile/store ownership, idempotency, and version checks.
   Private evidence upload needs a distinct action and private bucket; never
   reuse public product-media or store-asset storage policy.
2. Admin review: keep benefit decisions separate from product moderation.
   A namespaced action on an existing admin transport may save a function slot,
   but must invoke distinct `benefit.review` authorization and row-locked RPCs;
   dual-role reviewers cannot approve their own store. Do not reinterpret an
   existing product-approval action. Preserve its legacy payload and errors.
3. Buyer projection: extend the existing public catalog SQL/RPC projection with
   only currently effective, public-safe benefit fields. No evidence paths or
   seller assertions. Store/product/brand status, expiry, and revocation are
   evaluated server-side. Do not make Android infer verification from text.
4. Delivery/pricing: keep disabled. Later use server-side quote/revalidation
   in existing checkout paths, including zone, variant, quantity, eligibility,
   funding, and offer conflict logic. No zero/unknown price placeholders and
   no discount or shipping badge before listing/checkout parity is proven.

The Phase 2C SQL/API drafts remain outside active migrations. Phase 2D found
that no isolated Auth + Storage + HTTP staging stack exists. Reusing an Edge
route does not remove the need for private Storage, RLS/grant tests, concurrency
tests, or a separate production approval.

## Authentication boundary

`verify_jwt=false` removes gateway JWT enforcement; it is not an authorization
policy. In the reviewed deployed routes, seller order detail/transition use
`currentSession` and selected-store checks; admin refund/exchange details use
`requireAdminPermission`; seller application detail delegates permission to
`adishop_admin_get_seller_application`. The live DB grants for that RPC,
`adishop_admin_session`, and the scoped seller transition RPC deny `anon` and
`authenticated` execution and permit `service_role`. The two review-read
routes are intentionally public and return only `status=published` reviews,
but use a service-role client and should be reviewed for parent product/store
visibility, abuse limits, and public field minimization before reuse.

Session exchange/refresh are deliberately callable without an existing Shop
session; their assertion or refresh-token validation is the boundary. Push
dispatch requires its own secret when invoked through the notification route.
This is a targeted static/deployed-source review, **not** proof that all 100
deployed routes have correct internal authorization. A route-by-route deployed
bundle audit and isolated negative HTTP tests remain security gates. In
particular, never expose a new benefits action by falling through to a public
or merely session-authenticated branch.

## Sequence and gates

1. Preserve all 100 URLs. Inventory caller versions and longer-term traffic;
   establish route ownership and contract tests for each candidate.
2. Establish an isolated Supabase environment with Auth, PostgREST, private
   Storage, Edge HTTP, and synthetic identities. Resolve Phase 2C draft policy
   questions and migration-ledger drift separately.
3. Implement benefit RPCs/private evidence policy behind default-off flags;
   test store isolation, self-approval denial, evidence privacy, stale versions,
   expiry/revocation, and concurrent decisions over real HTTP.
4. Extend only carefully chosen existing routes with additive versioned
   actions. Compare each exact deployed bundle with the proposed bundle;
   prevent unrelated shared-file changes from riding along. Test old Android
   contracts and scheduled dispatch.
5. Add public-safe projection, then checkout quote parity. Enable verified
   badges last, and delivery/pricing only after their policy and accounting
   gates pass. Seek separate production authorization for every deployment.
6. Reconsider retirement only after sustained no-use evidence, supported-APK
   sunset, staged replacement tests, and rehearsed restoration.

No function is approved for deletion or production Product Benefits rollout by
this plan.
