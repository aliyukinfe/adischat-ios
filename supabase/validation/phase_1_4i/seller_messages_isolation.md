# Seller Messages Isolation

Date: 2026-08-09

Status: STATIC SECURITY PASS; REALTIME LIVE MATRIX BLOCKED

- Conversation access is resolved server-side from the authenticated Shop profile.
- Seller ownership is derived from active store ownership/membership, not a client-supplied seller ID.
- Buyer access is restricted to the conversation customer profile.
- Message send, attachment upload, read state and conversation mutations call the protected message Edge Function.
- Account switch/logout clears protected Shop state in the existing session coordinator.
- Direct Realtime was not enabled because the production token cannot satisfy the current Supabase auth boundary.
- Prior Seller B cross-store HTTP probe remained denied with 403; no new foreign data was written during this task.

Pending: Seller A live incoming event and Seller B non-receipt after a secure Realtime-compatible identity configuration exists.
