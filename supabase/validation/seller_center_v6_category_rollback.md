# V6 category recovery

The proposal is one PostgreSQL transaction. A failed guard or statement rolls the entire change back before commit. Do not repair drift by editing applied migration history.

After a committed rollout, first stop further category edits and capture current definition, mapping, product-attribute, and audit rows. Prepare a **new reviewed compensating migration** based on that snapshot. Restore only the six prior required flags and five reviewed mappings, and remove only the 464 newly added mappings that have acquired no product values. The eight product-owner-rejected pairs were never deployed and require no rollback. Keep any new definitions referenced by saved specifications; mark unused definitions inactive only after proving no product or variant depends on them. Do not delete products, inventory, customer data, or seller drafts.

Recheck the seller form and buyer product detail after recovery. Category-unit restrictions are not part of this proposal and need no rollback here. No recovery SQL is executed or pre-authorized by this document.
