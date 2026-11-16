# Phase 9F.9 disposable HTTP fixture

This fixture is synthetic and loopback-only. It must never use production or staging credentials. Use a fresh PostgreSQL 17 cluster and database named `phase9f9`; do not reuse an unknown data directory. The tested ports were PostgreSQL `55441`, PostgREST `55484`, and the temporary Edge proxy `55485` (Deno `8000`).

Load in order: `phase9c3_order_fixture.sql`, `phase9c5_actual_rpc_overlay.sql`, run `phase9c5_actual_rpc_checks.mjs setup phase9f9`, then `phase9c7_integrated_order_release_candidate.sql`, `phase9f9_order_http_rls.sql`, and `phase9f9_shop_session_overlay.sql`. Start PostgREST with `phase8b3_postgrest.conf`, overriding its DB URI to the disposable database. Run `node supabase/validation/phase9f9_postgrest_http.test.mjs` and `node supabase/validation/phase9f9_order_edge_http.test.mjs`. Stop PostgREST and the cluster after testing. The Edge test starts and stops its own Deno handlers and loopback proxy.

The Shop JWTs and service-role JWT in the tests are local synthetic fixtures. The order RLS overlay represents the repository's deny-all client policy; this is not a production Auth, Firebase bridge, Supabase gateway, payment-provider, or full-schema parity test. The base 9F.9 tests do not assert delivery-code or concurrent HTTP behavior. A passing run is not a production deployment gate.

## Phase 9F.10 extension

For delivery-code and concurrent HTTP checks, load `phase9f10_delivery_fixture.sql` into the same disposable database. `phase9f10_order_http.test.mjs` installs the unchanged `adishop_reveal_delivery_code` function extracted from `20260724210238_adishop_delivery_code_idempotent_reveal.sql`, then calls the unchanged shared Edge handlers through `phase9f10_order_edge_server.ts`. After adding the RPC, notify PostgREST with `select pg_notify('pgrst','reload schema')` before testing. Start PostgreSQL with explicit `-o '-h 127.0.0.1 -p 55441'`; its unmodified config otherwise uses port 5432.

Run `node supabase/validation/phase9f10_order_http.test.mjs` after PostgREST is ready. The test closes its Deno server and HTTP proxy. Stop PostgREST and PostgreSQL separately, then verify no listener remains on ports `55441`, `55484`, `55485`, or `8000`.

The 9F.10 test uses signed local Shop-session fixtures rather than real Firebase/Cloudflare assertion issuance or Supabase Auth verification. It cannot establish token-rotation/replay, gateway, provider-callback, production-schema, or version-119 client parity. The delivery RPC is from a repository migration, not an independently fetched deployed definition.
