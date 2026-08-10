# Phase 1.4I.3 Transport Acceptance

Date: 2026-08-10
Starting HEAD: `f082e0c`
Source commit: `f23973a`
Version: `1.0.121` (`121`)

## Source Audit

- Endpoint: `adishop-messages`, `verify_jwt=false` at the gateway with mandatory internal Shop-session validation.
- Seller identity: resolved from the verified Shop session; never accepted from Android.
- Seller stores: derived server-side from active store ownership/membership.
- Conversation access: buyer profile match or membership in the conversation store; foreign access returns safe 404.
- Messages: newest 50 returned in stable chronological order; the backend supports a `before` cursor for older pages.
- Unread: counted from server participant `last_read_at`; read updates skip an already-current latest message.
- Send: server acknowledgement precedes Android insertion; `client_message_id` makes retries idempotent.
- Attachments: conversation access is checked before upload or signed URL creation; allowed MIME types and 10 MB maximum are enforced; signed URLs expire after 300 seconds.
- Errors: Android sanitizes authentication, HTTP, JWT, policy, database and service details.

## Lifecycle and Reliability

- Active visible conversation: immediate refresh, then every 10 seconds.
- Background or non-visible conversation: coroutine cancelled by lifecycle/composition.
- Foreground return: immediate refresh before the next interval.
- Duplicate jobs: in-flight requests are keyed by Firebase user and conversation.
- Account switch: protected state resets on owner UID change; stale-account responses are discarded.
- Logout/session expiry: Shop memory is cleared; navigation disposal cancels refresh; one coordinated authorization retry is allowed and a second 401 terminates safely.
- Failed refresh: existing messages are retained because state is replaced only on success.

## Device and Live Evidence

- Device: `RZCT7061S7E`.
- Fresh debug APK installed; version remained `1.0.121` (`121`).
- Controlled seller opened real conversations for `Phase 1.4C Validation Store` and loaded an existing protected image attachment.
- One controlled seller message was acknowledged by the server, appeared immediately, and remained one database row after the next refresh.
- Background/foreground returned to the same conversation without crash, ANR or duplicate content.
- Controlled network interruption did not crash the app; existing attachment/history remained available and protected requests recovered after connectivity returned.
- Existing Hulu Store Seller A and Abel mobile Seller B device/server isolation evidence from Phase 1.4I.2 remains valid; this transport change adds stale-account result rejection and does not alter ownership rules.
- Missing session: HTTP 401. Malformed session: HTTP 401.

## Builds and Security

- `compileDebugKotlin`: PASS, 2m10s.
- Final `compileDebugKotlin`, `testDebugUnitTest` and `assembleDebug`: PASS, 3m24s.
- Final `compileReleaseKotlin`, `lintVitalRelease`, `minifyReleaseWithR8`, `assembleRelease`: PASS, 10m27s.
- Gradle heap: `-Xmx3072m`.
- Release APK: v2 signature verified.
- Focused Android source and release APK privileged-secret scans: zero matches.
- Phase 1.4I.2 exactly-once seller-response behavior remains unchanged.

Decision: authenticated 10-second message refresh is accepted for Phase 1.4I. Supabase Realtime is a future non-blocking enhancement.
