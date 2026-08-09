# Store Profile Validation

- Authenticated Hulu Store snapshot: PASS
- Owner-scoped load: PASS (production Edge Function 200)
- Supported fields exposed: logo, banner, name, description, business type, phone, email, address, city, region, country, hours, shipping, returns, exchanges, warranty, support, website, Instagram
- Verification status: read-only
- Unsaved-change detection: implemented
- Optimistic concurrency using `updated_at`: implemented; stale update returns 409
- Storage upload MIME/size checks: retained
- Controlled production mutation and database readback: PASS
- Live field changed: customer service policy to `AdisGebeya in-app support`
- Database readback: PASS; one `seller.store.update` audit event recorded for the logical save
