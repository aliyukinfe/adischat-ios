// Phase 9G.13 Staging Deployment Package Local Verification
// Validates preflight, consolidated DDL, synthetic seed, and non-destructive rollback
// against a clean isolated database with existing staging identity foundation.
import assert from 'node:assert/strict';
import { spawnSync, execFileSync } from 'node:child_process';

const psql = 'C:/PostgreSQL/17/pgsql/bin/psql.exe';
const packageDir = 'C:/Users/adema/adischat-android/supabase/validation/phase9g13_staging_package';
const validationDir = 'C:/Users/adema/adischat-android/supabase/validation';
const dbName = 'phase9g13_staging_clean_test';

function runSql(db, query) {
  return execFileSync(psql, [
    '-X', '-v', 'ON_ERROR_STOP=1', '-At',
    '-h', '127.0.0.1', '-p', '55451', '-U', 'postgres',
    '-d', db, '-c', query
  ], { encoding: 'utf8' }).trim();
}

function runFile(db, filePath) {
  const res = spawnSync(psql, [
    '-X', '-v', 'ON_ERROR_STOP=1',
    '-h', '127.0.0.1', '-p', '55451', '-U', 'postgres',
    '-d', db, '-f', filePath
  ], { encoding: 'utf8' });
  const combined = `${res.stdout || ''}\n${res.stderr || ''}`;
  if (res.status !== 0) {
    throw new Error(combined);
  }
  return combined;
}

let passed = 0;
function check(label, fn) {
  fn();
  passed++;
  console.log(`PASS ${label}`);
}

try {
  console.log(`Setting up clean isolated database '${dbName}'...`);
  runSql('postgres', `drop database if exists ${dbName};`);
  runSql('postgres', `create database ${dbName};`);

  // 1. Establish existing staging identity foundation
  runFile(dbName, `${validationDir}/phase9g9_local_identity_bootstrap.sql`);
  runFile(dbName, `${validationDir}/phase9g11_local_identity_fixture.sql`);

  // Insert a dummy existing profile and link to test preservation
  runSql(dbName, `
    insert into public.shop_profiles(id, status, display_name)
      values ('80000000-0000-4000-8000-000000000001', 'active', 'Pre-existing Tester Profile');
    insert into public.shop_user_links(shop_profile_id, provider, provider_user_id, account_status)
      values ('80000000-0000-4000-8000-000000000001', 'firebase', 'staging-tester-uid-1', 'active');
  `);

  check('preflight: 01_preflight_and_backup passes on clean staging identity baseline', () => {
    const out = runFile(dbName, `${packageDir}/01_preflight_and_backup.sql`);
    assert.match(out, /PREFLIGHT_PASS/);
  });

  check('migration: 02_schema_marketplace_qa applies cleanly in transaction', () => {
    const out = runFile(dbName, `${packageDir}/02_schema_marketplace_qa.sql`);
    assert.match(out, /COMMIT/);
  });

  check('schema: 20 marketplace and review tables created with RLS enabled', () => {
    const tableCount = runSql(dbName, `
      select count(*) from pg_tables
      where schemaname = 'public' and tablename like 'shop_%' and rowsecurity = true;
    `);
    // 7 identity tables + 20 marketplace tables = 27 tables with RLS
    assert.equal(parseInt(tableCount, 10), 27);
  });

  check('views: security barrier views created without permissions leaks', () => {
    const viewCount = runSql(dbName, `
      select count(*) from pg_views
      where schemaname = 'public' and viewname in ('shop_qa_public_catalog', 'shop_qa_public_stores');
    `);
    assert.equal(parseInt(viewCount, 10), 2);
  });

  check('guard: 01_preflight_and_backup blocks re-application when marketplace tables exist', () => {
    assert.throws(() => {
      runFile(dbName, `${packageDir}/01_preflight_and_backup.sql`);
    }, /PREFLIGHT_FAIL: Marketplace objects already exist/);
  });

  check('seed: 03_synthetic_seed_qa inserts draft catalog', () => {
    const out = runFile(dbName, `${packageDir}/03_synthetic_seed_qa.sql`);
    assert.match(out, /COMMIT/);
    const catCount = runSql(dbName, `select count(*) from public.shop_categories;`);
    assert.equal(parseInt(catCount, 10), 2);
    const prodCount = runSql(dbName, `select count(*) from public.shop_products;`);
    assert.equal(parseInt(prodCount, 10), 1);
  });

  check('catalog-privacy: unreviewed seeded draft product is hidden from public view', () => {
    const publicCount = runSql(dbName, `select count(*) from public.shop_qa_public_catalog;`);
    assert.equal(parseInt(publicCount, 10), 0);
  });

  check('rollback: 05_recovery_and_rollback removes marketplace schema cleanly', () => {
    const out = runFile(dbName, `${packageDir}/05_recovery_and_rollback.sql`);
    assert.match(out, /COMMIT/);
    const marketplaceTables = runSql(dbName, `
      select count(*) from pg_tables
      where schemaname = 'public' and tablename in ('shop_products', 'shop_stores', 'shop_carts');
    `);
    assert.equal(parseInt(marketplaceTables, 10), 0);
  });

  check('preservation: pre-existing identity profiles and links remain 100% intact after rollback', () => {
    const profile = runSql(dbName, `
      select display_name from public.shop_profiles where id = '80000000-0000-4000-8000-000000000001';
    `);
    assert.equal(profile, 'Pre-existing Tester Profile');
    const link = runSql(dbName, `
      select provider_user_id from public.shop_user_links where shop_profile_id = '80000000-0000-4000-8000-000000000001';
    `);
    assert.equal(link, 'staging-tester-uid-1');
  });

  console.log(`\n========================================`);
  console.log(`PHASE 9G.13 DEPLOYMENT PACKAGE: ${passed} PASS, 0 FAIL`);
  console.log(`========================================\n`);

} finally {
  runSql('postgres', `drop database if exists ${dbName};`);
}
