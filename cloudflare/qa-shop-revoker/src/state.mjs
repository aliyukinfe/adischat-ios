const uidPattern = /^[A-Za-z0-9:_-]{1,128}$/;
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function validUid(uid) { return typeof uid === "string" && uidPattern.test(uid); }
export function validId(id) { return typeof id === "string" && uuidPattern.test(id); }

function database(env) {
  if (!env?.QA_STATE_DB?.prepare) throw new Error("qa-state-unavailable");
  return env.QA_STATE_DB;
}

export async function allowlistHas(env, uid) {
  if (!validUid(uid)) return false;
  const row = await database(env).prepare("select uid from qa_allowlist where uid = ? and enabled = 1")
    .bind(uid).first();
  return row?.uid === uid;
}

export function operationStore(env) {
  const db = database(env);
  const get = (id) => db.prepare("select * from qa_revocation_ops where id = ?").bind(id).first();
  return {
    get,
    async begin(id, uid, action, baseline, now) {
      await db.prepare("insert or ignore into qa_revocation_ops(id, uid, action, baseline_valid_since, state, updated_at) values(?,?,?,?, 'intent', ?)")
        .bind(id, uid, action, baseline, now).run();
      return get(id);
    },
    async claim(id, now) {
      const result = await db.prepare("update qa_revocation_ops set state = 'processing', lease_until = ?, updated_at = ? where id = ? and (state = 'intent' or (state = 'processing' and lease_until < ?))")
        .bind(now + 30_000, now, id, now).run();
      return result.meta?.changes === 1;
    },
    async applied(id, validSince, now) {
      const result = await db.prepare("update qa_revocation_ops set state = 'firebase_done', applied_valid_since = ?, lease_until = null, updated_at = ? where id = ? and state = 'processing'")
        .bind(validSince, now, id).run();
      if (result.meta?.changes !== 1) throw new Error("qa-state-conflict");
    },
    async queued(id, now) {
      const result = await db.prepare("update qa_revocation_ops set state = 'queued', enqueue_count = enqueue_count + 1, attention = case when enqueue_count + 1 >= 5 then 'delivery-exhausted' else attention end, updated_at = ? where id = ? and state in ('firebase_done','queued')")
        .bind(now, id).run();
      if (result.meta?.changes !== 1) throw new Error("qa-state-conflict");
    },
    async confirmed(id, now) {
      const result = await db.prepare("update qa_revocation_ops set state = 'confirmed', updated_at = ? where id = ? and state in ('firebase_done','queued','confirmed')")
        .bind(now, id).run();
      if (result.meta?.changes !== 1) throw new Error("qa-state-conflict");
    },
    async attention(id, category, now) {
      await db.prepare("update qa_revocation_ops set attention = ?, updated_at = ? where id = ?")
        .bind(category, now, id).run();
    },
    async pending(limit, olderThan) {
      const result = await db.prepare("select * from qa_revocation_ops where state in ('firebase_done','queued') and enqueue_count < 5 and updated_at < ? order by updated_at asc, id asc limit ?")
        .bind(olderThan, limit).all();
      return result.results;
    },
    async cursor() {
      return db.prepare("select last_link_id from qa_reconciliation_cursor where id = 1").first();
    },
    async claimScan(owner, now) {
      const result = await db.prepare("update qa_reconciliation_cursor set lease_owner = ?, lease_until = ? where id = 1 and (lease_until is null or lease_until < ?)")
        .bind(owner, now + 60_000, now).run();
      return result.meta?.changes === 1;
    },
    async saveCursor(cursor, owner, now, stats = {}) {
      const result = await db.prepare("update qa_reconciliation_cursor set last_link_id = ?, last_scanned = ?, last_revoked = ?, last_ambiguous = ?, completed_scans = completed_scans + ?, last_error = null, updated_at = ? where id = 1 and lease_owner = ?")
        .bind(cursor, stats.scanned ?? 0, stats.revoked ?? 0, stats.ambiguous ?? 0,
          cursor === null ? 1 : 0, now, owner).run();
      if (result.meta?.changes !== 1) throw new Error("qa-scan-lease-lost");
    },
    async recordFailure(category, owner, now) {
      await db.prepare("update qa_reconciliation_cursor set last_error = ?, last_failed_at = ? where id = 1 and lease_owner = ?")
        .bind(category, now, owner).run();
    },
    async releaseScan(owner) {
      await db.prepare("update qa_reconciliation_cursor set lease_owner = null, lease_until = null where id = 1 and lease_owner = ?")
        .bind(owner).run();
    },
  };
}
