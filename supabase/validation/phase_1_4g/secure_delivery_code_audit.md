# Phase 1.4G Secure Delivery Code Audit

Date: 2026-07-26

## Audit Summary

- Existing order architecture uses `shop_orders` with per-store `shop_order_groups`.
- Seller order actions are routed through Supabase Edge Functions and the `adishop_transition_seller_order_group` RPC.
- Buyer delivery code reveal is routed through `adishop_reveal_delivery_code`.
- Seller delivery confirmation is routed through `adishop_verify_delivery_code`.
- Protected order RPC grants are restricted to `service_role` on the live project.
- RLS is enabled on order, payment, notification, and delivery-code tables on the live project.

## Critical Finding

`shop_delivery_confirmation_codes.delivery_code` existed and previously allowed temporary plaintext code persistence for idempotent reveal.

Phase 1.4G requires that delivery codes are never stored in plaintext.

## Fix Applied

Migration:

`20260726114945_adishop_phase_1_4g_secure_delivery_codes.sql`

Changes:

- Cleared all non-null plaintext delivery-code rows.
- Updated `adishop_reveal_delivery_code` to return plaintext only on first reveal.
- Updated `adishop_reveal_delivery_code` to persist only salted SHA-256 hashes.
- Updated `adishop_verify_delivery_code` to compare salted SHA-256 hashes.
- Kept wrong-code attempt persistence and lockout.
- Kept delivered state, notification, timeline, and audit writes server-side.
- Reconfirmed `adishop_reveal_delivery_code` and `adishop_verify_delivery_code` grants are `service_role` only.

## Verification Evidence

- Live plaintext rows after fix: `0`.
- Live function definition no longer contains `delivery_code = code_plain`.
- Live reveal function contains SHA-256 digest hashing.
- Live verify function contains SHA-256 digest verification.
- Live grants for reveal and verify functions: `service_role` only.

## HTTP 409 Reveal Hotfix

Validation date: 2026-07-26

- Buyer delivery-code reveal returned HTTP 409 because the RPC used Supabase crypto helpers while the function runtime `search_path` was limited to `public`.
- Live probe showed `gen_random_bytes(integer)` exists in the `extensions` schema and failed inside `adishop_reveal_delivery_code`.
- Added migration `20260726122350_adishop_phase_1_4g_delivery_code_salt_fix.sql` to set `search_path = public, extensions` for reveal and verify RPCs.
- Repaired stuck validation order `ADS-20260726-95E3DA09` by clearing an unrevealed and unverified hash row only; no plaintext code was printed or stored.
- Connected Android buyer reveal then succeeded: `revealed=true`, `has_hash=true`, `has_plaintext=false`, `verified=false`, `failed_attempts=0`.

## Repeatable Reveal Hotfix

Validation date: 2026-07-26

- Buyer requirement changed so tapping Delivery code repeatedly must show the same code again, not a blank message and not a newly generated code.
- Added migration `20260726123207_adishop_phase_1_4g_repeatable_delivery_code.sql`.
- Delivery code is now derived deterministically from order id plus a per-order salt.
- The plaintext code is still not persisted; only `code_salt`, salted `code_hash`, and `revealed_at` are stored.
- Rollback-safe probe confirmed two reveal calls for the same order return equal nonblank 6-digit codes without printing the code.
- Connected Android repeat taps for `ADS-20260726-95E3DA09` kept server state valid: `revealed=true`, `has_hash=true`, `has_plaintext=false`, `verified=false`, `failed_attempts=0`.

## Migration Application Note

`supabase db push --linked --dry-run` and `supabase migration repair --linked` were blocked by direct Postgres pooler timeouts.

The migration SQL was applied with `supabase db query --linked --file ...`, then the verified migration version was inserted into `supabase_migrations.schema_migrations` through the linked query API.

No secrets or plaintext delivery codes are stored in this evidence file.
