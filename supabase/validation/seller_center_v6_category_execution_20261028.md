# V6 category SQL execution record

Executed on 2026-10-28 against Supabase project `mcewjfnkrtiiolzibsuh` with `supabase db query --linked --project-ref ... --file supabase/validation/seller_center_v6_category_migration.sql`. One execution was attempted; the CLI returned exit code 0, no error, and no result rows. No other SQL write was run.

Exact executed file SHA-256: `757FA041D2CBE1077AC7B7B6F9F9AAD04018042DC4C5E9DD411A6F96DF3EAE35`.

## Preflight

- 185 selectable leaves, 26 active definitions, 373 mappings, 342 selectable-leaf mappings.
- Six expected required flags and five expected live removal pairs present; zero products in the three corrected categories.
- 18 saved specification values; RLS enabled on all five checked tables.
- Zero collisions for the 19 new definition codes; zero collisions and zero missing category IDs across all 464 proposed mapping pairs.
- SQL SHA-256 matched the authorized value. Static checks found one transaction, the baseline and postcondition guards, 19 definition INSERTs, 464 optional mapping INSERTs, and no product, RLS, or selling-unit writes.

## Postconditions

- 185 selectable leaves, 45 active definitions, 832 mappings, 801 selectable-leaf mappings.
- Zero of the six expected required flags remain required; zero of the five intended removal pairs remain.
- All 464 new mapping pairs are present and optional; all eight product-owner-rejected proposed pairs remain absent.
- All 18 saved specification values remain. Product count and full-row fingerprint, saved-specification fingerprint, 25 selling-unit rows and fingerprint, and relevant RLS-policy fingerprint match the pre-execution snapshots. RLS remains enabled on all five checked tables.
- Existing `adishop_category_attribute_schema` REST RPC returned HTTP 200 for Office Equipment (seven fields, all optional), Chargers & Cables (ten, all optional), Gaming (thirteen, all optional), and Phone Accessories (nine, all optional).

`supabase db query --file` executes SQL directly and does not itself insert a Supabase migration-ledger entry. Do not create a duplicate pending migration or blindly rerun this SQL. Reconcile deployment history separately with a reviewed plan if required; this execution did not authorize extra SQL.

Seller Center stabilization remains blocked pending the separate product submission and admin approval gates. No Edge Functions, product publication, Android release, version change, or coupon work was performed.
