# Inventory Validation

- Authenticated Hulu Store load: PASS
- Device records shown: 2 real inventory rows
- Search, stock filters, sort, product SKU, variant SKU/title, reserved stock, thresholds, bulk selection: implemented
- Atomic bulk RPC migration: `20260805182908_adishop_phase_1_4i_1_atomic_inventory`
- Ownership validation: database-enforced
- Negative stock and stock below reserved: rejected
- Idempotency: request ID checked before mutation
- Grants: anon false, authenticated false, service role true
- Foreign-store no-write probe: PASS (`seller-store-not-found`)
- Controlled single mutation/readback: PASS; Hulu Store mobile threshold changed from 3 to 4 with one history row and one audit event
- Controlled two-row bulk mutation/readback: PASS; the test-product threshold changed from 3 to 4 while the unchanged mobile row produced no duplicate history
- Quantity and reservation values remained unchanged; total validation evidence is two history rows and two audit events
