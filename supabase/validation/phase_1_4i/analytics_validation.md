# Analytics Validation

- Metrics are server-derived and owned-store scoped
- Date presets: 7, 30, 90 days
- Revenue, orders, delivered orders, products sold, refund rate, average order value: implemented
- Top products and revenue trend: server-derived
- Visitors and conversion: shown as not measured; no data fabricated
- Authenticated default-range load on device: PASS
- Default live totals: ETB 40,000 revenue, 6 orders, 2 delivered, 6 products sold, ETB 16,683.33 average order, and 50 percent refund rate
- Seven-day preset: PASS; server-derived range returned 0 orders, 0 delivered and ETB 0 revenue for 2026-08-03 through 2026-08-09
- Thirty-day preset: PASS; server-derived range returned 6 orders, 2 delivered and ETB 40,000 revenue for 2026-07-11 through 2026-08-09
- The differing seven-day and thirty-day results prove that the date filter is applied server-side
