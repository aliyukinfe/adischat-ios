CREATE TABLE IF NOT EXISTS desktop_qr_sessions (
  id TEXT PRIMARY KEY,
  token_hash TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL DEFAULT 'pending',
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  scanned_at INTEGER NOT NULL DEFAULT 0,
  approved_at INTEGER NOT NULL DEFAULT 0,
  rejected_at INTEGER NOT NULL DEFAULT 0,
  user_id TEXT NOT NULL DEFAULT '',
  desktop_device_id TEXT NOT NULL DEFAULT '',
  desktop_device_name TEXT NOT NULL DEFAULT '',
  desktop_os TEXT NOT NULL DEFAULT '',
  desktop_ip TEXT NOT NULL DEFAULT '',
  desktop_location TEXT NOT NULL DEFAULT '',
  desktop_session_token TEXT NOT NULL DEFAULT '',
  used_at INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_desktop_qr_sessions_token_hash ON desktop_qr_sessions(token_hash);
CREATE INDEX IF NOT EXISTS idx_desktop_qr_sessions_status_expires ON desktop_qr_sessions(status, expires_at);

CREATE TABLE IF NOT EXISTS linked_devices (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  session_token_hash TEXT NOT NULL UNIQUE,
  device_type TEXT NOT NULL DEFAULT 'desktop',
  device_name TEXT NOT NULL DEFAULT '',
  os TEXT NOT NULL DEFAULT '',
  app_version TEXT NOT NULL DEFAULT '',
  ip TEXT NOT NULL DEFAULT '',
  location TEXT NOT NULL DEFAULT '',
  last_active_at INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL DEFAULT 0,
  revoked_at INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_linked_devices_user ON linked_devices(user_id, revoked_at, last_active_at);
CREATE INDEX IF NOT EXISTS idx_linked_devices_token_hash ON linked_devices(session_token_hash);