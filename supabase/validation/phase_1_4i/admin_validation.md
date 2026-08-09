# Admin Validation

- Seller Business function does not accept or infer Admin authority
- Existing Admin Center remains a separate protected surface
- Current seller account receives 403 from Admin session endpoint, as expected
- Authorized Admin identity: PASS; connected device showed `Shop super admin`
- Protected Admin session endpoint: PASS (200)
- Dashboard and read-only moderation views: PASS; real product, store and refund records loaded
- Current production counts displayed: 0 pending seller applications, 7 products and 6 open orders
- Role separation: PASS; Admin access did not bypass seller ownership, and a foreign Hulu Store Seller Business probe returned 403 without a business payload
- Controlled moderation mutation: NOT RUN because no pending controlled seller application existed
- Probe-session cleanup: PASS; three temporary Admin validation sessions were revoked while the app session remained active
- Final Admin read/authorization gate: PASS
