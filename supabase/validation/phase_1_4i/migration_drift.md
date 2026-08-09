# Migration Drift

Four older remote versions still use production-assigned timestamps that differ from local equivalent filenames:

- Remote `20260728195549` corresponds by name to local `20260728113000_adishop_phase_1_4h_2_moderation_completion.sql`
- Remote `20260729082227` corresponds by name to local `20260729111856_fix_phase_1_4h_order_item_relationships.sql`
- Remote `20260729092419` corresponds by name to local `20260729122228_complete_buyer_receipt_projection.sql`
- Remote `20260729100500` corresponds by name to local `20260729130254_fix_seller_request_response_contract.sql`

No migration-history repair was performed. The versions predate Phase 1.4I and did not block applying `20260805182908_adishop_phase_1_4i_1_atomic_inventory`. Exact checksum equivalence remains unverified.
