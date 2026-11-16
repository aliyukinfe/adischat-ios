CREATE TABLE IF NOT EXISTS channel_verifications (
  channel_id TEXT PRIMARY KEY,
  channel_name TEXT NOT NULL DEFAULT '',
  owner_uid TEXT NOT NULL,
  note TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'pending',
  admin_uid TEXT NOT NULL DEFAULT '',
  admin_note TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
