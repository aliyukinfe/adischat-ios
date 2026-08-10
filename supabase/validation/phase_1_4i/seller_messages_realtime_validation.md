# Seller Messages Transport Validation

Date: 2026-08-10

Status: AUTHENTICATED REFRESH ACCEPTED; REALTIME NON-BLOCKING

## Verified

- Message table: `shop_messages`.
- Conversation tables: `shop_conversations` and `shop_conversation_participant_state`.
- `shop_messages` is present in `supabase_realtime`; unrelated Shop-private tables are not published.
- RLS is enabled on all three message/conversation tables.
- Android Seller Messages uses protected Shop-session Edge Functions and an authenticated 10-second refresh transport.
- Attachments use the private `shop-chat-attachments` bucket and protected function access.
- The active conversation refresh is lifecycle-aware: it starts immediately at `STARTED`, repeats every 10 seconds, and is cancelled below `STARTED` or when the conversation leaves composition.
- Overlapping conversation refreshes are suppressed and results from a stale Firebase account are rejected.
- A controlled seller send was acknowledged by the server, displayed immediately, and remained exactly one row after a subsequent refresh.
- Missing and malformed Shop sessions returned HTTP 401.

## Realtime Decision

The production Firebase JWT issuer and audience match AdisChat, but the token does not contain the Supabase-compatible `role=authenticated` claim and direct PostgREST authentication was rejected with HTTP 401/PGRST301. Android cannot subscribe directly with the current Shop-session token. Service-role credentials must never be shipped to Android, and anon subscriptions would not preserve row-level authorization.

No RLS policy, publication, or Android auth boundary was weakened. Supabase Realtime is a future transport enhancement and is not required for Seller Messages correctness.

Accepted current behavior: authenticated refresh every 10 seconds only while an active conversation is visible and the app is foregrounded.
