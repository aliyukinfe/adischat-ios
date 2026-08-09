# Messages and Realtime Validation

- Protected seller inbox endpoint: production 200 on authenticated device
- Conversation, product/order context, attachments, typing expiry and unread state: backend-supported
- `shop_messages` is in the Supabase Realtime publication
- Message tables have participant RLS and private attachment storage
- Android Seller Messages currently uses authenticated endpoint refresh, not an authenticated Supabase Realtime channel
- Live Seller A inbox and conversation read: PASS; real product context, messages, image and file attachment rendered on device
- A new seller message was not sent because the visible conversation belongs to a real customer
- Realtime arrival/read-state device matrix: BLOCKED pending a Firebase-compatible Supabase Realtime client/session integration
- No typing, presence or read state is fabricated
