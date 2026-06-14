# AdisChat Desktop

AdisChat Desktop is a messaging-only client for Windows first, with macOS and Linux packaging ready through Tauri.

Desktop v1 intentionally includes only:

- Private chats
- Groups
- Channels
- Voice/video call foundation
- Messaging media upload/download
- Desktop notifications

It intentionally excludes wallet, payments, rides, shops, QR, services, game, and other super-app modules.

## Backend

No new backend is created. The client uses the same existing AdisChat services:

- Main Cloudflare Worker: `https://adischat-api.adischat.workers.dev`
- Community/channel Worker routes: `https://adischat-wallet-api.adischat.workers.dev`
- Firebase Authentication
- Existing media upload route: `/api/upload`
- Existing private chat routes: `/api/conversations`
- Existing group routes: `/api/communities`
- Existing channel routes: `/api/channels`
- Existing call routes: `/api/calls`

Realtime is implemented as a WebSocket-ready client with a polling fallback that matches the Android client’s current behavior. Set `NEXT_PUBLIC_ADISCHAT_WS_URL` when the production WebSocket endpoint is available.

## Setup

```powershell
npm run desktop:install
Copy-Item desktop\.env.example desktop\.env.local
npm run desktop:dev
```

Fill `desktop/.env.local` with the Firebase Web app id if one is created in Firebase Console. The checked-in defaults reuse the current AdisChat Firebase project and Cloudflare endpoints.

## Tauri

Install Rust before packaging:

```powershell
winget install Rustlang.Rustup
rustup default stable
```

Run desktop app:

```powershell
npm run desktop:tauri:dev
```

Build Windows installer:

```powershell
npm run desktop:tauri:build:windows
```

Expected outputs after a successful Tauri build:

- `desktop/src-tauri/target/release/bundle/nsis/`
- `desktop/src-tauri/target/release/bundle/msi/`

## Auto Update

The Tauri updater block is present but disabled until signing keys and update endpoints are ready. Enable it in `src-tauri/tauri.conf.json` after creating a release host for desktop bundles.
