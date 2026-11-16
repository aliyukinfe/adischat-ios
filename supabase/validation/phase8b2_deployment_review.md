# Phase 8B.2 scoped admin-products candidate

Status: BLOCKED for deployment. No production function, database, RLS, or Android flag was changed.

## Provenance

- Project: `mcewjfnkrtiiolzibsuh`; deployed `adishop-admin-products` version 10, `verify_jwt=false`.
- Retrieved with Supabase CLI 3.0.0 `functions download --remote=mcewjfnkrtiiolzibsuh --use-api` on 2026-11-05.
- Both downloaded files were compared byte-for-byte with the files returned by the Supabase deployed-function API; both matched exactly.
- Deployed bundle digest reported by the API: `8e997d4d4f47426bb0085ef3c48bd5fbd8f36793b10c1ed7344bd0fd63a09a2f`.

## Baseline and rollback artifact

Directory: `phase8b2_v10_rollback/supabase/functions/`

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `adishop-admin-products/index.ts` | 146 | `55324370C316DD80FD155927347CD04F3EBAD8256C463DB707666A3D4D6B9418` |
| `_shared/adishop.ts` | 52827 | `8FA84F8196A717F643EF9B736171B0A95152DCD69CA33CB0BFF93F32A20D0A0E` |

## Scoped candidate manifest

Directory: `phase8b2_scoped_bundle/supabase/functions/`

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `adishop-admin-products/index.ts` | 329 | `C974A5A4BCF756DE8E4B5FA019FED7D7815584D42C0EAFDF81E4966F797541ED` |
| `_shared/adishop.ts` | 52834 | `D79591980C603681A0613FC26929CC5BAB58614AFABD12A002BBF0EB6A59CAE0` |
| `_shared/adishop-admin-all-products.ts` | 7708 | `092F8A0513317D071E47E7B8594BA1A2AA894009C3ED56D5F96B00E436FD558F` |

Exact baseline-to-candidate delta: wrapper imports the advanced handler and branches only on `view=all-v2`; the deployed dispatcher changes only `async function currentSession` to `export async function currentSession`; one advanced-handler file is added. The candidate imports only the v10 `@supabase/supabase-js@2.110.7` dependency and its two local modules. It does not include the unrelated worktree dispatcher changes. No credential literal is added; the existing dispatcher still reads server environment variables.

## Request compatibility and authorization

| Request or identity | Candidate source behavior | Runtime evidence |
| --- | --- | --- |
| Existing moderation list with status/search/limit/offset | Delegates to exact v10 dispatcher | Wrapper-level synthetic HTTP comparison passed; real PostgREST comparison not run |
| Product detail | Separate unchanged Edge Function | Not exercised by candidate |
| `view=all-v2` GET | New advanced handler | Synthetic HTTP/query tests passed; isolated Auth/PostgREST not run |
| `view=all-v2` non-GET | 405 | Source review only |
| Missing/invalid/expired Shop session | `currentSession` rejects before privileged query | Missing-session synthetic test passed; real credential cases not run |
| Customer, Seller, Support Agent | No `product.moderation.read` in Phase 8B.1 read-only role metadata | Synthetic no-permission test passed; real role HTTP tests not run |
| Shop Admin, Shop Super Admin | Permission granted in Phase 8B.1 read-only role metadata | Synthetic permission test passed; real role HTTP tests not run |
| Forged client role/store | Handler derives profile from signed Shop session and uses store ID only as a filter | Source review; real forged-request tests not run |

The established v10 architecture intentionally has Supabase platform `verify_jwt=false`: it validates the bearer as an HMAC Shop-session JWT, checks the persisted session for expiry/revocation and active profile status, then uses the server-side permission RPC. The candidate preserves that boundary. This is not a claim of runtime authentication validation.

## Local evidence and unavailable gates

- `node --test supabase/validation/phase8b1_admin_products_local.test.mjs`: 11/11 pass. These tests inject synthetic session/database clients and compare baseline/candidate wrapper routing. They do not exercise Supabase Auth, PostgREST, RLS, or real HTTP authorization.
- Candidate has an exact, query-builder-encoded path for punctuated product and variant SKUs; text search and candidate lookup remain bounded. It requests an exact count on the final filtered product query and orders by date then ID.
- The deployed v10 route remains the only production route. The candidate has not been served against an isolated database or authenticated over HTTP. Customer, Seller, Support Agent, Shop Admin, Shop Super Admin, invalid/expired credentials, combined filters, exact totals, pagination under writes, and legacy RPC responses remain runtime NOT VERIFIED.
- No isolated AdisGebeya Supabase branch/project is available. This machine has no Deno, Docker, or psql executable. Supabase CLI download/deploy alone does not provide isolated Auth/PostgREST execution.
- Phase 8B.1 read-only `EXPLAIN` showed a sequential scan for `%Galaxy%` title search. No matching substring-search index exists in the inspected schema. One-character searches and large offsets remain performance risks; offset pagination can shift under concurrent product writes.
- Before rollout, evaluate a staged `pg_trgm` GIN or indexed full-text strategy for product/store/SKU search, cap or debounce very short terms at the client/server contract, and measure broad-search behavior with realistic volume. No index is proposed for direct production deployment in this package.

## Future deployment and recovery (not authorized)

After isolated HTTP/Auth/PostgREST tests, index/performance review, bundle-hash recheck, and separate approval, deploy only from this scoped directory with `npx supabase functions deploy adishop-admin-products --remote=mcewjfnkrtiiolzibsuh --use-api --no-verify-jwt --workdir <absolute-path-to-phase8b2_scoped_bundle>`. Do not use `--prune`.

To recover, deploy only `adishop-admin-products` from the untouched `phase8b2_v10_rollback` directory with the same CLI flags, then verify legacy moderation-list, filters, authorization failures, and error contracts. Redeployment increments the function version; it does not restore version number 10. Preserve the hashes above before either operation.
