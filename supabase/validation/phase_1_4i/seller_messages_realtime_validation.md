# Seller Messages Realtime Validation

Date: 2026-08-09

Status: BLOCKED BY AUTHENTICATION ARCHITECTURE

## Verified

- Message table: `shop_messages`.
- Conversation tables: `shop_conversations` and `shop_conversation_participant_state`.
- `shop_messages` is present in `supabase_realtime`; unrelated Shop-private tables are not published.
- RLS is enabled on all three message/conversation tables.
- Existing Android Seller Messages uses protected Shop-session Edge Functions and an authenticated 10-second refresh fallback.
- Attachments use the private `shop-chat-attachments` bucket and protected function access.

## Blocker

The production Firebase JWT issuer and audience match AdisChat, but the token does not contain the Supabase-compatible `role=authenticated` claim and direct PostgREST authentication was rejected with HTTP 401/PGRST301. Android cannot subscribe directly with the current Shop-session token. Service-role credentials must never be shipped to Android, and anon subscriptions would not preserve row-level authorization.

No RLS policy, publication, or Android auth boundary was weakened. Live Seller A/B Realtime tests were therefore not fabricated.

Safe current behavior: authenticated refresh every 10 seconds while the message screen is active.
