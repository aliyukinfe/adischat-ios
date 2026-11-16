CREATE TABLE IF NOT EXISTS message_receipts (
  id TEXT PRIMARY KEY,
  threadType TEXT NOT NULL,
  threadId TEXT NOT NULL,
  messageId TEXT NOT NULL,
  uid TEXT NOT NULL,
  deliveredAt INTEGER NOT NULL DEFAULT 0,
  readAt INTEGER NOT NULL DEFAULT 0,
  updatedAt INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_message_receipts_unique
  ON message_receipts(threadType, threadId, messageId, uid);

CREATE INDEX IF NOT EXISTS idx_message_receipts_thread_user
  ON message_receipts(threadType, threadId, uid, readAt DESC);

