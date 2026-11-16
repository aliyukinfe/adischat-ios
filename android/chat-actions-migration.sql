-- AdisChat chat actions - D1 migration
-- Apply to both API workers that serve direct conversations and community chats.

CREATE TABLE IF NOT EXISTS chat_message_actions (
  uid TEXT NOT NULL,
  thread_type TEXT NOT NULL CHECK (thread_type IN ('direct', 'community')),
  thread_id TEXT NOT NULL,
  message_id TEXT NOT NULL,
  hidden INTEGER NOT NULL DEFAULT 0,
  starred INTEGER NOT NULL DEFAULT 0,
  listened INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (uid, thread_type, thread_id, message_id)
);

CREATE TABLE IF NOT EXISTS chat_message_pins (
  uid TEXT NOT NULL,
  thread_type TEXT NOT NULL CHECK (thread_type IN ('direct', 'community')),
  thread_id TEXT NOT NULL,
  message_id TEXT NOT NULL,
  pinned INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (uid, thread_type, thread_id, message_id)
);

CREATE TABLE IF NOT EXISTS chat_message_edits (
  id TEXT PRIMARY KEY,
  uid TEXT NOT NULL,
  thread_type TEXT NOT NULL CHECK (thread_type IN ('direct', 'community')),
  thread_id TEXT NOT NULL,
  message_id TEXT NOT NULL,
  previous_text TEXT NOT NULL DEFAULT '',
  new_text TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_reports (
  id TEXT PRIMARY KEY,
  reporter_uid TEXT NOT NULL,
  reported_uid TEXT NOT NULL DEFAULT '',
  thread_type TEXT NOT NULL CHECK (thread_type IN ('direct', 'community')),
  thread_id TEXT NOT NULL,
  message_id TEXT NOT NULL DEFAULT '',
  reason TEXT NOT NULL DEFAULT '',
  details TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'open',
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_message_reactions (
  uid TEXT NOT NULL,
  thread_type TEXT NOT NULL CHECK (thread_type IN ('direct', 'community')),
  thread_id TEXT NOT NULL,
  message_id TEXT NOT NULL,
  emoji TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (uid, thread_type, thread_id, message_id)
);

CREATE TABLE IF NOT EXISTS chat_thread_settings (
  uid TEXT NOT NULL,
  thread_type TEXT NOT NULL CHECK (thread_type IN ('direct', 'community')),
  thread_id TEXT NOT NULL,
  muted INTEGER NOT NULL DEFAULT 0,
  wallpaper TEXT NOT NULL DEFAULT '',
  disappearing_seconds INTEGER NOT NULL DEFAULT 0,
  screenshot_warning INTEGER NOT NULL DEFAULT 1,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (uid, thread_type, thread_id)
);

CREATE TABLE IF NOT EXISTS chat_blocks (
  blocker_uid TEXT NOT NULL,
  blocked_uid TEXT NOT NULL,
  reason TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  PRIMARY KEY (blocker_uid, blocked_uid)
);

CREATE INDEX IF NOT EXISTS idx_chat_actions_thread ON chat_message_actions(thread_type, thread_id, message_id);
CREATE INDEX IF NOT EXISTS idx_chat_pins_thread ON chat_message_pins(thread_type, thread_id, uid, pinned);
CREATE INDEX IF NOT EXISTS idx_chat_reports_status ON chat_reports(status, created_at);
CREATE INDEX IF NOT EXISTS idx_chat_reactions_message ON chat_message_reactions(thread_type, thread_id, message_id);
