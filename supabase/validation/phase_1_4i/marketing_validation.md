# Marketing Validation

- Real store coupons and campaigns: connected
- Coupon and campaign dates: validated server-side
- Percentage discount limit: 1-90
- Campaign product ownership: validated when product IDs are supplied
- Draft history: displayed from production data
- Flash sale and featured-product management: honestly unavailable because no approved seller policy/workflow was verified
- Controlled create test: PASS; draft coupon `PHASEI1` created once at 5 percent with one audit event
- Existing unrelated draft coupon remained unchanged
- Controlled update of the created coupon: NOT RUN; no unnecessary second production mutation was made
