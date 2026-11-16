# Phase 9G.17f staging-only moderation recovery

Target: `opkqyvttbyezjbqwkvho`. Never use against production `mcewjfnkrtiiolzibsuh`.
This is a preparation runbook, not an authorization to run SQL or deploy Edge code.

## Before deployment

1. Obtain explicit approval to resume the inactive project. Confirm the project ref and ACTIVE status afterward.
2. Take and verify a staging backup/restore point. Capture the exact live definitions and ACLs of the three existing functions below with the read-only query. Save the output outside the repository as a protected deployment artifact. Compare signatures and hashes to the reviewed baseline. Stop on drift.
3. Record counts of audit rows and reviewed store/product/image projections. Keep Android Public Content disabled.

```sql
select p.oid::regprocedure::text as signature,
       pg_get_functiondef(p.oid) as definition,
       p.proacl as acl,
       p.prosecdef as security_definer,
       p.proconfig as settings
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('adishop_qa_review_product',
                   'adishop_qa_review_image',
                   'adishop_qa_seller_product_edit',
                   'adishop_qa_review_store')
order by signature;
```

## Recovery after a partial rollout

1. Disable the moderation Edge route or keep `ADISHOP_QA_MODERATION_ENABLED` unset/false. Do not change exchange or refresh.
2. If the SQL deployment transaction failed, verify that PostgreSQL rolled it back. Do not run a second repair blindly.
3. If the SQL committed, restore only the three pre-existing function bodies from the captured definitions, after replacing the known audit `entity_id` text casts with UUID-typed values. Preserve their original signatures, `security definer`, hardened `search_path`, and ACLs. Do not restore the known UUID/text bug. Have a reviewer approve the exact generated restore diff before execution.
4. For the new store-review RPC, revoke EXECUTE from all roles, then drop only `public.adishop_qa_review_store(text,uuid,timestamptz,text,text,text,text)` once no caller depends on it. Do not drop any table, projection, trigger, identity object, or audit row.
5. Verify function definitions and grants, active Shop authentication, and existing reviewed visibility. Do not use `phase9g13_staging_package/05_recovery_and_rollback.sql`: it removes marketplace data.

The exact restore SQL cannot be finalized while staging is inactive because the live function bodies and grants cannot be captured. Recovery is not certified until a captured-definition restore is rehearsed on a disposable copy.

## Edge deployment gate

- Deploy `adishop-qa-moderation` only to the confirmed staging ref. Configure gateway `verify_jwt=false` because the handler verifies the Shop-signed bearer session itself; test gateway routing and invalid-token denial before enabling moderation.
- Required environment variable names: `SUPABASE_URL`, `ADISHOP_QA_MODERATION_ENABLED`, `ADISHOP_SHOP_SESSION_SECRET`, `ADISHOP_SUPABASE_SECRET_KEY`. The enable flag defaults off when absent.
- Keep service-role EXECUTE limited to the four moderation RPCs; never expose that credential to Android. Confirm the handler's 8192-byte body limit and per-isolate rate limit. The latter is not a distributed abuse-control guarantee.
- Do not enable Android Public Content: its current decision endpoint and numeric revision shape differ from this route's store/product timestamp revision and idempotency-header contract.
