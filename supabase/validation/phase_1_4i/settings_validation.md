# Seller Settings Validation

- Notifications: orders, messages, reviews, returns/refunds, marketing, finance
- Store preferences: shipping, returns, exchanges
- Language: English/Amharic
- Theme: system/light/dark
- Persisted in server-only `shop_seller_settings`
- PIN, 2FA, staff permissions and payout settings: honestly unavailable and not simulated
- Controlled save/readback: PASS
- Marketing notifications enabled and shipping preference changed to `Standard delivery`
- Database readback matched the device state and one `seller.settings.update` audit event was recorded
