# Channel Blue-Badge Verification — Backend Spec

The app needs a **channel-scoped** verification flow, separate from the existing
personal KYC (`/api/verification/*`). Reusing the personal endpoint fails with
HTTP 400 because it validates the *requesting user* (document type, level
gating, "already verified"), and even on success it would badge the user, not
the channel.

Below is the minimal set of endpoints + storage to add to the Cloudflare Worker.

## 1. Storage

Add a table / KV namespace `channel_verifications`:

| field        | type    | notes                                        |
|--------------|---------|----------------------------------------------|
| channelId    | string  | primary key                                  |
| channelName  | string  | snapshot at request time                     |
| ownerUid     | string  | requesting admin/owner                       |
| note         | string  | optional reason from requester               |
| status       | string  | `none` \| `pending` \| `approved` \| `rejected` |
| adminUid     | string  | who reviewed                                 |
| adminNote    | string  | optional rejection reason                    |
| createdAt    | number  | epoch ms                                     |
| updatedAt    | number  | epoch ms                                     |

Also add `verified: boolean` (default false) to the channel/community record so
the client can show the badge only when granted.

## 2. Endpoints

### Request (channel owner)
```
POST /api/channels/{channelId}/verification
body: { "note": string }
-> 200 { channelId, status: "pending", createdAt }
```
Sets status=pending. Idempotent: re-requesting while pending returns the
existing record.

### Status (client reads to show badge / request state)
```
GET /api/channels/{channelId}/verification
-> 200 { channelId, status, note, adminNote, updatedAt }
```

### Admin list (feeds the Verifications panel)
```
GET /api/admin/channel-verifications?status=pending
-> 200 [ { channelId, channelName, ownerUid, note, status, createdAt } ]
```

### Admin approve / reject
```
POST /api/admin/channel-verifications/{channelId}/approve
-> sets status=approved AND channel.verified=true

POST /api/admin/channel-verifications/{channelId}/reject
body: { "reason": string }
-> sets status=rejected, adminNote=reason
```

## 3. Client wiring (after endpoints exist)

- `CloudflareApi.kt`: add the 5 calls above + response data classes
  (mirror the existing `GameApproval*` pattern, which already does exactly this
  for game pots).
- Channel "Request blue badge" dialog → `POST .../verification`.
- `ChannelFeedScreen` badge → show only when status/`verified` == approved.
- Admin panel **Verifications** tab → add a "Channels" filter that lists
  `GET /api/admin/channel-verifications` with approve/reject buttons
  (reuse the existing `VerificationsTab` card layout).

> Note: the **game-pot approval** feature already implements this exact
> request→admin-approve loop (`/api/games/{channelId}/approval` +
> `/api/games/admin/approvals/...`). Cloning that code path for channel
> verification is the fastest route.
