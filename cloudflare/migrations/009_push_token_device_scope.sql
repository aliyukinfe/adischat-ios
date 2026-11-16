ALTER TABLE push_tokens ADD COLUMN device_id TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_push_tokens_uid_device
  ON push_tokens(uid, device_id);

CREATE INDEX IF NOT EXISTS idx_push_tokens_device
  ON push_tokens(device_id);
