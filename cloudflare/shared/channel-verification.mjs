// ============================================================================
// Channel Blue-Badge Verification - Cloudflare Worker handlers (D1)
// ============================================================================
// Adapt the router registration, auth (requireUser / requireAdmin), and the
// D1 binding name (env.DB) to match your existing Worker. This mirrors the
// game-pot approval pattern already in your backend.
//      

// STEP 1 - One-time schema (run once via `wrangler d1 execute` or migrations):
/*
CREATE TABLE IF NOT EXISTS channel_verifications (
  channel_id    TEXT PRIMARY KEY,
  channel_name  TEXT NOT NULL DEFAULT '',
  owner_uid     TEXT NOT NULL,
  note      TEXT NOT NULL DEFAULT '',
  status      TEXT NOT NULL DEFAULT 'pending',   -- pending | approved | rejected
  admin_uid     TEXT NOT NULL DEFAULT '',
  admin_note    TEXT NOT NULL DEFAULT '',
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL
);

-- STEP 2 - add a verified flag to your channels/communities table:
ALTER TABLE communities ADD COLUMN verified INTEGER NOT NULL DEFAULT 0;
*/

const now = () => Date.now();
const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });

async function first(db, statements) {
  let lastError;
  for (const statement of statements) {
    try {
      return await db.prepare(statement.sql).bind(...(statement.params || [])).first();
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError;
}

async function run(db, statements) {
  let lastError;
  for (const statement of statements) {
    try {
      return await db.prepare(statement.sql).bind(...(statement.params || [])).run();
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError;
}

async function all(db, statements) {
  let lastError;
  for (const statement of statements) {
    try {
      return await db.prepare(statement.sql).bind(...(statement.params || [])).all();
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError;
}

// STEP 3 - Channel owner requests verification.
// POST /api/channels/:channelId/verification   body: { note }
export async function requestChannelVerification(request, env, channelId, user) {
  const body = await request.json().catch(() => ({}));
  const note = (body.note || "").toString().slice(0, 500);

  // Look up the channel to snapshot its name and confirm ownership.
  const channel = await first(env.DB, [
    { sql: "SELECT id, name, owner_uid FROM communities WHERE id = ?", params: [channelId] },
    { sql: "SELECT id, name, ownerUid AS owner_uid FROM communities WHERE id = ?", params: [channelId] },
    { sql: "SELECT id, name, owner_uid FROM channels WHERE id = ?", params: [channelId] },
    { sql: "SELECT id, name, ownerUid AS owner_uid FROM channels WHERE id = ?", params: [channelId] },
  ]);
  if (!channel) return json({ error: "Channel not found" }, 404);
  if (channel.owner_uid !== user.uid) return json({ error: "Not the channel owner" }, 403);

  // Idempotent: if a pending request exists, return it unchanged.
  const existing = await first(env.DB, [
    { sql: "SELECT status FROM channel_verifications WHERE channel_id = ?", params: [channelId] },
    { sql: "SELECT status FROM channel_verifications WHERE channelId = ?", params: [channelId] },
  ]);
  if (existing && existing.status === "pending") {
    return json({ channelId, status: "pending" });
  }

  const ts = now();
  await run(env.DB, [
    {
      sql: `INSERT INTO channel_verifications
        (channel_id, channel_name, owner_uid, note, status, admin_uid, admin_note, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'pending', '', '', ?, ?)
        ON CONFLICT(channel_id) DO UPDATE SET
        channel_name = excluded.channel_name,
        note      = excluded.note,
        status       = 'pending',
        admin_uid    = '',
        admin_note   = '',
        updated_at   = excluded.updated_at`,
      params: [channelId, channel.name || "", user.uid, note, ts, ts],
    },
    {
      sql: `INSERT INTO channel_verifications
        (channelId, channelName, ownerUid, note, status, adminUid, adminNote, createdAt, updatedAt)
        VALUES (?, ?, ?, ?, 'pending', '', '', ?, ?)
        ON CONFLICT(channelId) DO UPDATE SET
        channelName = excluded.channelName,
        note      = excluded.note,
        status      = 'pending',
        adminUid    = '',
        adminNote   = '',
        updatedAt   = excluded.updatedAt`,
      params: [channelId, channel.name || "", user.uid, note, ts, ts],
    },
  ]);

  return json({ channelId, status: "pending", createdAt: ts });
}

// STEP 4 - Client reads status (to show badge / request state).
// GET /api/channels/:channelId/verification
export async function getChannelVerification(request, env, channelId) {
  const row = await first(env.DB, [
    {
      sql: `SELECT channel_id AS channelId, status, note, admin_note AS adminNote, updated_at AS updatedAt
        FROM channel_verifications WHERE channel_id = ?`,
      params: [channelId],
    },
    {
      sql: `SELECT channelId, status, note, adminNote, updatedAt
        FROM channel_verifications WHERE channelId = ?`,
      params: [channelId],
    },
  ]);
  if (!row) return json({ channelId, status: "none" });
  return json({
    channelId: row.channelId,
    status: row.status,
    note: row.note,
    adminNote: row.adminNote,
    updatedAt: row.updatedAt,
  });
}

// STEP 5 - Admin list for the Verifications panel.
// GET /api/admin/channel-verifications?status=pending
export async function listChannelVerifications(request, env) {
  const url = new URL(request.url);
  const status = url.searchParams.get("status") || "pending";
  const { results } = await all(env.DB, [
    {
      sql: `SELECT channel_id AS channelId, channel_name AS channelName, owner_uid AS ownerUid, note, status, created_at AS createdAt
        FROM channel_verifications
        WHERE status = ?
        ORDER BY created_at DESC`,
      params: [status],
    },
    {
      sql: `SELECT channelId, channelName, ownerUid, note, status, createdAt
        FROM channel_verifications
        WHERE status = ?
        ORDER BY createdAt DESC`,
      params: [status],
    },
  ]);
  return json(
    (results || []).map((r) => ({
      channelId: r.channelId,
      channelName: r.channelName,
      ownerUid: r.ownerUid,
      note: r.note,
      status: r.status,
      createdAt: r.createdAt,
    }))
  );
}

// STEP 6 - Admin approves - status approved + channel.verified = true.
// POST /api/admin/channel-verifications/:channelId/approve
export async function approveChannelVerification(request, env, channelId, admin) {
  const ts = now();
  await run(env.DB, [
    {
      sql: `UPDATE channel_verifications
        SET status='approved', admin_uid=?, admin_note='', updated_at=?
        WHERE channel_id=?`,
      params: [admin.uid, ts, channelId],
    },
    {
      sql: `UPDATE channel_verifications
        SET status='approved', adminUid=?, adminNote='', updatedAt=?
        WHERE channelId=?`,
      params: [admin.uid, ts, channelId],
    },
  ]);

  const badgeUpdates = [
    "UPDATE communities SET verified=1 WHERE id=?",
    "UPDATE communities SET is_verified=1 WHERE id=?",
    "UPDATE channels SET verified=1 WHERE id=?",
    "UPDATE channels SET is_verified=1 WHERE id=?",
  ];
  for (const sql of badgeUpdates) {
    try {
      await env.DB.prepare(sql).bind(channelId).run();
      break;
    } catch {
      // Channel schemas can differ while migrations roll out; the request row above remains authoritative.
    }
  }
  return json({ channelId, status: "approved", approved: true });
}

// STEP 7 - Admin rejects.
// POST /api/admin/channel-verifications/:channelId/reject   body: { reason }
export async function rejectChannelVerification(request, env, channelId, admin) {
  const body = await request.json().catch(() => ({}));
  const reason = (body.reason || "").toString().slice(0, 500);
  const ts = now();
  await run(env.DB, [
    {
      sql: `UPDATE channel_verifications
        SET status='rejected', admin_uid=?, admin_note=?, updated_at=?
        WHERE channel_id=?`,
      params: [admin.uid, reason, ts, channelId],
    },
    {
      sql: `UPDATE channel_verifications
        SET status='rejected', adminUid=?, adminNote=?, updatedAt=?
        WHERE channelId=?`,
      params: [admin.uid, reason, ts, channelId],
    },
  ]);
  return json({ channelId, status: "rejected", rejected: true });
}

//      
// ROUTER REGISTRATION (adapt to your router - itty-router / Hono / manual switch)
//      
/*
// Example with itty-router style:
router.post("/api/channels/:channelId/verification", async (req, env) => {
  const user = await requireUser(req, env);      // your auth helper
  return requestChannelVerification(req, env, req.params.channelId, user);
});

router.get("/api/channels/:channelId/verification", (req, env) =>
  getChannelVerification(req, env, req.params.channelId));

router.get("/api/admin/channel-verifications", async (req, env) => {
  await requireAdmin(req, env);      // your admin guard
  return listChannelVerifications(req, env);
});

router.post("/api/admin/channel-verifications/:channelId/approve", async (req, env) => {
  const admin = await requireAdmin(req, env);
  return approveChannelVerification(req, env, req.params.channelId, admin);
});

router.post("/api/admin/channel-verifications/:channelId/reject", async (req, env) => {
  const admin = await requireAdmin(req, env);
  return rejectChannelVerification(req, env, req.params.channelId, admin);
});
*/

// IMPORTANT: also include `verified` in whatever endpoint returns channel/
// community details, so the app can show the badge only when verified === true.
