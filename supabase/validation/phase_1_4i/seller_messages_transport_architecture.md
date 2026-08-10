# Seller Messages Transport Architecture

Date: 2026-08-10

## Current Production Path

Firebase identity -> short-lived Shop session -> protected Supabase Edge Function -> server-derived profile/store/conversation ownership -> authenticated message refresh.

The active conversation performs an immediate authenticated refresh when its lifecycle reaches `STARTED`, then refreshes at 10-second intervals. Moving the app to the background, leaving the conversation, logging out through the app navigation, or destroying the composition cancels the polling coroutine. Android never receives a service-role credential.

## Future Optional Path

Firebase identity -> trusted AdisChat server -> short-lived Supabase-compatible Realtime authorization -> RLS-protected Realtime subscription.

This requires a deliberately designed token bridge compatible with Supabase Realtime authorization. Anonymous subscriptions, broad message policies, client-trusted seller/store identifiers, and embedded privileged credentials are prohibited.

Status: `FUTURE ENHANCEMENT - NON-BLOCKING`.
