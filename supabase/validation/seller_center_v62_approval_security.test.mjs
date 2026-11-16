import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const sql = readFileSync(new URL('./seller_center_v62_approval_security_proposal.sql', import.meta.url), 'utf8');
const edge = readFileSync(new URL('../functions/_shared/adishop.ts', import.meta.url), 'utf8');

test('review decisions require a matching server submission timestamp', () => {
  assert.match(sql, /p_expected_submitted_at is null or product_record\.submitted_at is distinct from p_expected_submitted_at/);
  assert.match(sql, /coalesce\(product_record\.submitted_version, 0\) < 1/);
  assert.match(sql, /product_record\.moderation_status not in \('submitted', 'under_review'\)/);
  assert.match(sql, /product_record\.status <> 'draft'/);
  assert.match(sql, /product_record\.deleted_at is not null/);
  assert.match(edge, /p_expected_submitted_at: body\.expectedSubmittedAt/);
});

test('legacy review calls fail closed while non-review actions retain a wrapper', () => {
  assert.match(sql, /if p_action in \('start_review', 'approve', 'reject', 'request_changes'\) then\s+raise exception 'stale-product-review'/);
  assert.match(sql, /null::timestamptz/);
});

test('approval counts only active same-store images and serializes deletion', () => {
  assert.match(sql, /i\.product_id = product_record\.id/);
  assert.match(sql, /i\.store_id = product_record\.store_id/);
  assert.match(sql, /i\.deleted_at is null/);
  assert.match(sql, /where id = old\.product_id\s+for update/);
  assert.match(sql, /tg_op = 'UPDATE' and old\.deleted_at is not null and new\.deleted_at is not null/);
  assert.match(sql, /before delete or update of deleted_at, product_id, store_id, image_url, bucket, storage_path/);
  assert.match(sql, /if not has_active_image then raise exception 'missing-images'/);
});

test('review requests are idempotent only for the same actor, product, and action', () => {
  assert.match(sql, /prior_audit\.actor_profile_id = p_actor_profile_id/);
  assert.match(sql, /prior_audit\.target_id = p_product_id/);
  assert.match(sql, /prior_audit\.action_type = 'product_moderation\.' \|\| p_action/);
  assert.match(sql, /raise exception 'idempotency-conflict'/);
  assert.match(sql, /self-product-review-blocked/);
});
