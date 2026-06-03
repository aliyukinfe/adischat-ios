-- ============================================================
-- AdisChat – Channel Game Pot  |  D1 Migration
-- Database : adischa-db
-- Apply via: wrangler d1 execute adischa-db --file=gamepot-migration.sql
-- ============================================================

-- ── channel_games ─────────────────────────────────────────────────────────────
-- One row per game. Settings are locked once status transitions to 'active'.
CREATE TABLE IF NOT EXISTS channel_games (
  id                     TEXT    PRIMARY KEY,
  channel_id             TEXT    NOT NULL,
  creator_uid            TEXT    NOT NULL,
  title                  TEXT    NOT NULL,
  total_tickets          INTEGER NOT NULL,
  price_per_ticket       REAL    NOT NULL,
  admin_commission_pct   REAL    NOT NULL DEFAULT 5.0,
  max_tickets_per_user   INTEGER,                       -- NULL = unlimited
  duration_seconds       INTEGER NOT NULL,
  starts_at              INTEGER,                       -- unix epoch (ms)
  ends_at                INTEGER,                       -- unix epoch (ms)
  status                 TEXT    NOT NULL DEFAULT 'pending',
  -- status: pending | active | drawing | completed | cancelled
  jackpot_amount         REAL    NOT NULL DEFAULT 0.0,
  tickets_sold           INTEGER NOT NULL DEFAULT 0,
  winner_ticket_number   INTEGER,
  winner_uid             TEXT,
  winner_name            TEXT,
  winner_payout          REAL,
  admin_commission_amount REAL,
  draw_seed              TEXT,   -- crypto random seed stored at draw time (audit trail)
  created_at             INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  updated_at             INTEGER NOT NULL DEFAULT (unixepoch() * 1000)
);

-- ── game_tickets ──────────────────────────────────────────────────────────────
-- Pre-populated by the create-game handler (one row per ticket number).
-- UNIQUE constraint on (game_id, ticket_number) prevents double-sell at DB level.
CREATE TABLE IF NOT EXISTS game_tickets (
  id             TEXT    PRIMARY KEY,
  game_id        TEXT    NOT NULL,
  ticket_number  INTEGER NOT NULL,
  owner_uid      TEXT,
  owner_name     TEXT,
  purchased_at   INTEGER,
  status         TEXT    NOT NULL DEFAULT 'available',
  -- status: available | sold | won
  tx_reference   TEXT,
  UNIQUE (game_id, ticket_number),
  FOREIGN KEY (game_id) REFERENCES channel_games(id)
);

-- ── game_participants ─────────────────────────────────────────────────────────
-- Aggregate per-user stats within a game (for leaderboard / recent buyers UI).
CREATE TABLE IF NOT EXISTS game_participants (
  id             TEXT    PRIMARY KEY,
  game_id        TEXT    NOT NULL,
  uid            TEXT    NOT NULL,
  name           TEXT    NOT NULL,
  photo_url      TEXT    NOT NULL DEFAULT '',
  tickets_count  INTEGER NOT NULL DEFAULT 0,
  total_spent    REAL    NOT NULL DEFAULT 0.0,
  joined_at      INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  updated_at     INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  UNIQUE (game_id, uid),
  FOREIGN KEY (game_id) REFERENCES channel_games(id)
);

-- ── game_winners ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS game_winners (
  id                TEXT PRIMARY KEY,
  game_id           TEXT NOT NULL,
  channel_id        TEXT NOT NULL,
  winner_uid        TEXT NOT NULL,
  winner_name       TEXT NOT NULL,
  ticket_number     INTEGER NOT NULL,
  jackpot_amount    REAL NOT NULL,
  admin_commission  REAL NOT NULL,
  payout_amount     REAL NOT NULL,
  payout_reference  TEXT,
  drawn_at          INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  FOREIGN KEY (game_id) REFERENCES channel_games(id)
);

-- ── Indexes ───────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_games_channel    ON channel_games(channel_id, status);
CREATE INDEX IF NOT EXISTS idx_games_status     ON channel_games(status);
CREATE INDEX IF NOT EXISTS idx_tickets_game     ON game_tickets(game_id, status);
CREATE INDEX IF NOT EXISTS idx_tickets_owner    ON game_tickets(owner_uid, game_id);
CREATE INDEX IF NOT EXISTS idx_participants_game ON game_participants(game_id);
CREATE INDEX IF NOT EXISTS idx_winners_channel  ON game_winners(channel_id, drawn_at DESC);
