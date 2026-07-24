# AdisGebeya Phase 1.4D.1B Migration Reconciliation

Date: 2026-07-24
Project ref: mcewjfnkrtiiolzibsuh

## Root Cause

The Supabase CLI had been linked under a nested `supabase/supabase/.temp` directory during earlier commands. When the CLI was run from the authoritative `supabase` project directory, the real drift was visible:

- Early Shop migrations existed locally but were not recorded in remote migration history.
- Three delivery-code local migration files used timestamps that did not match the versions already recorded remotely.
- Two 2026-07-24 stabilization migrations had been applied directly with SQL but were not recorded in migration history.

## Evidence Checked

- Remote Shop tables exist: 45 `public.shop_%` tables.
- `shop_checkout_sessions` exists.
- `shop_inventory_reservations` exists.
- Delivery-code tables exist: `shop_delivery_code_attempts`, `shop_delivery_confirmation_codes`.
- Delivery-code columns exist: `code_hash`, `code_salt`, `revealed_at`, `verified_at`, `failed_attempts`, `locked_until`.
- RPCs exist:
  - `adishop_create_buy_now_checkout_session(uuid, text, uuid, uuid, integer)`
  - `adishop_request_order_cancellation(uuid, uuid, text, text)`
  - `adishop_reveal_delivery_code(uuid, uuid)`
  - `adishop_verify_delivery_code(uuid, uuid, text)`

## Actions

- Linked the correct Supabase project directory with `npx supabase link --project-ref mcewjfnkrtiiolzibsuh`.
- Renamed never-recorded local delivery-code files to match the remote migration versions:
  - `20260720153813...` -> `20260720154505...`
  - `20260720154535...` -> `20260720154629...`
  - `20260720154825...` -> `20260720154938...`
- Repaired migration history as applied only for versions with remote schema evidence:
  - `202607190001`
  - `202607190002`
  - `202607190003`
  - `20260720102937`
  - `20260724115938`
  - `20260724120500`

## Final Status

`npx supabase migration list --linked` now shows every local migration version matched by a remote migration version:

- 202607190001
- 202607190002
- 202607190003
- 20260720102937
- 20260720112511
- 20260720130820
- 20260720132543
- 20260720142310
- 20260720143853
- 20260720152519
- 20260720154505
- 20260720154629
- 20260720154938
- 20260724115938
- 20260724120500

No unexplained local-only or remote-only migration versions remained after reconciliation.
