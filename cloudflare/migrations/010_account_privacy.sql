ALTER TABLE users ADD COLUMN last_seen_visibility TEXT NOT NULL DEFAULT 'public';
ALTER TABLE users ADD COLUMN account_status TEXT NOT NULL DEFAULT 'active';
ALTER TABLE users ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_users_account_status ON users(account_status);
