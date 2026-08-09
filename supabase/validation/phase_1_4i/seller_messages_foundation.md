# Phase 1.4I Seller Messages Validation

Date: 2026-08-01

## Production foundation

- Project: `mcewjfnkrtiiolzibsuh`
- Migrations applied:
  - `20260729205933_adishop_phase_1_4i_seller_messages.sql`
  - `20260729212038_adishop_phase_1_4i_message_advisor_fixes.sql`
- Edge Function: `adishop-messages`
- Function ID: `ea5c0a0a-7673-4571-9f1b-cb4bfc3256c1`
- Deployed version: `12`
- Gateway JWT verification: disabled; the function performs internal Shop-session authentication.
- Missing and malformed Shop sessions return HTTP 401.

## Database and authorization

- `shop_conversations`, `shop_conversation_participant_state`, and `shop_messages` have RLS enabled.
- Buyer access is restricted to the conversation customer profile.
- Seller access is restricted to stores owned by the current seller profile.
- Message writes use the authenticated Shop profile and ignore client-supplied sender identity.
- Message sends are idempotent by `(sender_profile_id, client_message_id)`.
- `shop_messages` is included in the Supabase Realtime publication.
- The `shop-chat-attachments` bucket is private with a 10 MB limit and a restricted MIME allowlist.

## Android and live device evidence

Connected device: Samsung `SM-M336BU` (`RZCT7061S7E`).

- Buyer `abebe kebede` opened a real product conversation for `Daily Skin Cream`.
- Buyer sent `Phase 1.4I buyer chat validation`; it persisted after refresh.
- Approved seller `hulu store` received the conversation and unread badge.
- Seller opened the protected thread and replied `great`; it persisted after refresh.
- Buyer switched back and received `great` in the same thread.
- Unrelated approved seller `merke` saw an empty inbox and could not see the hulu store conversation.
- No Android crash or raw HTTP error appeared during the successful buyer/seller validation.
- The protected Supabase-to-Cloudflare push bridge resolved the recipient from `shop_profiles.adischat_user_id`; no client-supplied recipient identity is trusted.
- A controlled production bridge probe returned `ok=true` and `sent=1` for the logged-in buyer.
- Android posted the received Shop message on the existing `messages` notification channel with the expected safe title and body.
- The native Shop notification route opens the protected conversation by conversation ID after refreshing the Shop session.
- Mute and unmute both persisted for the authenticated buyer and immediately changed the accessible action label.
- Archive persisted without deleting either participant's message history; the buyer returned to AdisGebeya Home instead of remaining on a stale loading screen.
- Seller Messages now exposes separate Inbox and Archived filters, and an archived thread can use the protected unarchive action.
- Delete now requires an explicit confirmation explaining that only the current participant's copy is removed. The destructive confirmation was not executed against the controlled production conversation.
- Buyer uploaded `phase_1_4i_attachment.png` through the native document picker.
- The image rendered in the protected conversation after upload.
- Production contains one matching `shop_messages` image row and one matching private Storage object.
- Edge Function logs record `POST ...action=upload` with HTTP 200 on deployed version 2.
- Repeated open-thread refreshes no longer rewrite read state when the latest message is already read; `updated_at` remained unchanged across a 15-second production probe.

Evidence files:

- `artifacts/phase_1_4i_buyer_chat_sent.xml`
- `artifacts/phase_1_4i_seller_now.xml`
- `artifacts/phase_1_4i_hulu_chat.xml`
- `artifacts/phase_1_4i_buyer_return.xml`
- `artifacts/phase_1_4i_merke_messages.xml`
- `artifacts/phase_1_4i_attachment_picker.xml`
- `artifacts/phase_1_4i_attachment_result.xml`
- `artifacts/phase_1_4i_typing_install.xml`
- `artifacts/phase_1_4i_shop_notification.xml`
- `artifacts/phase_1_4i_shop_notification_retry.xml`
- `artifacts/phase_1_4i_controls_installed.xml`
- `artifacts/phase_1_4i_muted.xml`
- `artifacts/phase_1_4i_unmuted.xml`
- `artifacts/phase_1_4i_delete_confirmation.xml`
- `artifacts/phase_1_4i_archive_returns_shop.xml`

## Build evidence

- `compileDebugKotlin`: PASS with normal 3 GB Gradle settings after buyer conversation composable isolation/refactor.
- Final compile duration after extracting typing activity from the message composer: 1 minute 21 seconds.
- `testDebugUnitTest`: PASS, 21 tests, 0 failures.
- `assembleDebug`: PASS.
- Debug APK installed successfully on the connected device.
- Android version remains `1.0.121`.
- Two Temurin 17 G1 native crashes were recorded during separate Gradle runs. The source compile passes with the normal checked-in 3 GB settings; a one-command SerialGC override was used only to complete test/APK packaging and was not saved to `gradle.properties`.
- A generated Gradle 9.4.1 instrumentation transform for `kotlin-tooling-core-2.2.10.jar` developed an invalid ZIP central-directory header after a successful compile. The single generated transform entry was quarantined, Gradle regenerated it, and `assembleDebug` then passed in 1 minute 30 seconds.

## Remaining Module 1 gates

- Image attachment selection, upload, private persistence, and in-chat rendering are device-validated. PDF/text download-open behavior and seller-side receipt still require explicit device validation.
- Android now emits bounded typing heartbeats and renders the server-derived peer-typing boolean. Two-account live display validation is still pending.
- Supabase Realtime delivery is not yet authenticated and device-validated; the open thread currently uses a 10-second protected polling fallback.
- Database notification creation and Android FCM delivery are production-validated while the buyer device is online. Background/locked receipt and tap routing still require an explicit device run.
- Mute/unmute, archive, archive navigation, and delete confirmation are device-validated. Seller-side Archived inbox/unarchive and the destructive participant-delete result still require a seller-account device run.

Module 1 must remain in progress until these gates pass.
