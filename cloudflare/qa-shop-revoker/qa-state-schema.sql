-- Local D1 schema proposal only. Never apply to production or staging without approval.
create table qa_allowlist (
  uid text primary key,
  enabled integer not null check (enabled in (0,1)),
  updated_at integer not null
);
create table qa_revocation_ops (
  id text primary key,
  uid text not null,
  action text not null check (action in ('firebase_disabled','firebase_tokens_revoked')),
  baseline_valid_since integer not null,
  applied_valid_since integer,
  state text not null check (state in ('intent','processing','firebase_done','queued','confirmed')),
  enqueue_count integer not null default 0,
  lease_until integer,
  attention text,
  updated_at integer not null
);
create index qa_revocation_ops_pending on qa_revocation_ops(state, updated_at, id);
create table qa_reconciliation_cursor (
  id integer primary key check (id = 1),
  last_link_id text,
  lease_owner text,
  lease_until integer,
  last_error text,
  last_failed_at integer,
  last_scanned integer not null default 0,
  last_revoked integer not null default 0,
  last_ambiguous integer not null default 0,
  completed_scans integer not null default 0,
  updated_at integer not null
);
insert into qa_reconciliation_cursor(id, last_link_id, updated_at) values (1, null, 0);
