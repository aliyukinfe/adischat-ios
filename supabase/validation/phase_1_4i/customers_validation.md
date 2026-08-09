# Customers Validation

- Privacy-safe customer list: server-derived from owned-store orders
- Search, order count, lifetime spend, repeat-buyer state, recent order history: implemented
- Private seller notes: idempotent and store-scoped
- Protected message route: reuses order conversation ownership
- Phone, email, address and profile identifiers: absent from Android customer DTO
- Protected customer list and recent-order read on device: PASS
- Controlled private note save/readback: PASS; `Phase 1.4I validation` persisted with one `seller.customer.note` audit event
