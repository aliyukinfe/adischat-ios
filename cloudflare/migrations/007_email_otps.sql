CREATE TABLE IF NOT EXISTS email_verification_otps (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  salt TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  used_at INTEGER NOT NULL DEFAULT 0,
  attempts INTEGER NOT NULL DEFAULT 0,
  resend_status TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_email_verification_otps_email_active ON email_verification_otps(email, used_at, expires_at, created_at);
