CREATE TABLE IF NOT EXISTS calls (
  id TEXT PRIMARY KEY,
  callerUid TEXT NOT NULL,
  receiverUid TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('voice', 'video')),
  status TEXT NOT NULL CHECK (status IN ('ringing', 'accepted', 'rejected', 'missed', 'ended')),
  startedAt INTEGER NOT NULL,
  acceptedAt INTEGER NOT NULL DEFAULT 0,
  endedAt INTEGER NOT NULL DEFAULT 0,
  durationSeconds INTEGER NOT NULL DEFAULT 0,
  cloudflareSessionId TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_calls_caller_started ON calls(callerUid, startedAt DESC);
CREATE INDEX IF NOT EXISTS idx_calls_receiver_started ON calls(receiverUid, startedAt DESC);
CREATE INDEX IF NOT EXISTS idx_calls_status_started ON calls(status, startedAt DESC);

CREATE TABLE IF NOT EXISTS call_participants (
  id TEXT PRIMARY KEY,
  callId TEXT NOT NULL,
  uid TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'participant',
  joinedAt INTEGER NOT NULL DEFAULT 0,
  leftAt INTEGER NOT NULL DEFAULT 0,
  muted INTEGER NOT NULL DEFAULT 0,
  cameraEnabled INTEGER NOT NULL DEFAULT 0,
  FOREIGN KEY (callId) REFERENCES calls(id)
);

CREATE INDEX IF NOT EXISTS idx_call_participants_call ON call_participants(callId);
CREATE INDEX IF NOT EXISTS idx_call_participants_uid ON call_participants(uid, joinedAt DESC);

