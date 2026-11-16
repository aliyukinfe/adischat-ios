import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const dir = dirname(fileURLToPath(import.meta.url));
const read = (name) => readFileSync(join(dir, name), 'utf8');
const core = read('01_trust_schema.draft.sql');
const delivery = read('02_delivery_pricing.draft.sql');
const guards = read('03_guards_projection.draft.sql');
const transitions = read('04_case_transitions.draft.sql');
const intake = read('05_claim_intake.draft.sql');
const storage = read('06_private_evidence_storage.draft.sql');
const brands = read('07_brand_administration.draft.sql');
const catalog = read('08_catalog_projection.draft.sql');
const access = read('09_evidence_access_audit.draft.sql');
const uploads = read('10_pending_evidence_uploads.draft.sql');
const contracts = read('api_contracts.md');

test('all SQL drafts are transaction wrapped and outside active migrations', () => {
  for (const sql of [core, delivery, guards, transitions, intake, storage, brands, catalog, access, uploads]) {
    assert.match(sql, /^-- REVIEW DRAFT ONLY/m);
    assert.match(sql, /^begin;/m);
    assert.match(sql, /^commit;/m);
    assert.doesNotMatch(sql, /\b(update|delete from)\s+public\.shop_products\b/i);
  }
});

test('claim intake is owner-scoped and transactionally creates its details', () => {
  assert.match(intake, /owner_profile_id=p_actor/);
  assert.match(intake, /pg_advisory_xact_lock/);
  assert.match(intake, /insert into public\.shop_benefit_cases/);
  assert.match(intake, /insert into public\.shop_product_benefit_claims/);
  assert.match(intake, /insert into public\.shop_store_brand_authorizations/);
  assert.match(intake, /revoke all on function public\.adishop_benefit_create_claim/);
  assert.match(transitions, /s\.suspended_at is null and s\.deleted_at is null\) then raise exception 'benefit-not-found'/);
});

test('private benefit records have RLS and no direct client access', () => {
  assert.match(core, /enable row level security/);
  assert.match(core, /revoke all on public\.%I from public, anon, authenticated/);
  assert.match(core, /revoke update, delete on public\.shop_benefit_cases from service_role/);
  assert.match(guards, /shop_benefit_events_immutable/);
  assert.match(guards, /adishop_benefit_record_creation\(\) returns trigger\s+language plpgsql security definer set search_path = public/);
  assert.match(guards, /revoke insert, update, delete on public\.shop_benefit_events from service_role/);
  assert.match(storage, /'shop-benefit-evidence',false,10485760/);
  assert.match(storage, /allowed_mime_types=array\['image\/jpeg','image\/png','application\/pdf'\]::text\[\]/);
  assert.match(storage, /as restrictive for all to anon,authenticated/);
  assert.match(core, /shop_benefit_evidence_content_once_idx/);
});

test('decisions are row locked, versioned, idempotent, and deny dual-role self review', () => {
  assert.match(transitions, /where id=p_case_id for update/g);
  assert.match(transitions, /c\.version <> p_expected_version/g);
  assert.match(transitions, /p_actor=c\.submitted_by_profile_id/);
  assert.match(transitions, /shop_store_members m where m\.store_id=c\.store_id/);
  assert.match(transitions, /request_key=p_request_key/g);
});

test('trust projection fails closed for inactive, expired, revoked, or undocumented cases', () => {
  for (const fragment of ["p.status='active'", "c.status='approved'", 'c.expires_at > now()',
    'c.revoked_at is null', 'e.deleted_at is null', 'p.brand_id=a.brand_id']) {
    assert.ok(guards.includes(fragment), fragment);
  }
});

test('delivery and price drafts do not enable unvalidated benefits', () => {
  assert.match(delivery, /funding_source text not null/);
  assert.match(delivery, /minimum_group_subtotal_minor/);
  assert.match(transitions, /benefit-quote-integration-pending/);
  assert.match(contracts, /priceQuote": null/);
  assert.match(contracts, /Insufficient 30-day price history|insufficient 30-day price history/i);
});
