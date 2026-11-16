// Disposable loopback-only PostgreSQL fixture; synthetic identities only.
const { execFileSync, spawn } = require('node:child_process');
const bin = 'C:/PostgreSQL/17/pgsql/bin/psql.exe';
const port = process.env.SEC03G_PG_PORT || '55499';
const args = ['-h', '127.0.0.1', '-p', port, '-U', 'postgres', '-d', 'postgres',
  '-v', 'ON_ERROR_STOP=1', '-Atq'];
let passed = 0;
function sql(query) { return execFileSync(bin, [...args, '-c', query], { encoding: 'utf8' }).trim(); }
function run(query) {
  return new Promise((resolve) => {
    const child = spawn(bin, [...args, '-c', query], { windowsHide: true });
    let stderr = '';
    child.stderr.on('data', (chunk) => { stderr += chunk; });
    child.on('close', (code) => resolve({ code, stderr }));
  });
}
function check(label, actual, expected) {
  if (actual !== expected) throw new Error(`${label}: expected ${expected}, got ${actual}`);
  passed++;
  console.log(`PASS ${label}`);
}
async function barrier(marker) {
  for (let attempt = 0; attempt < 60; attempt++) {
    if (sql(`select count(*) from pg_stat_activity where pid<>pg_backend_pid()
      and state='active' and wait_event='PgSleep' and query like '%${marker}%'`) === '1') return;
    await new Promise((resolve) => setTimeout(resolve, 25));
  }
  throw new Error(`barrier not reached: ${marker}`);
}
function fixture(suffix) {
  const profile = `84000000-0000-4000-8000-00000000000${suffix}`;
  const family = `85000000-0000-4000-8000-00000000000${suffix}`;
  const uid = `qa-race-${suffix}`;
  sql(`insert into auth.users(id) values ('${profile}');
    insert into public.shop_profiles(id,supabase_user_id,adischat_user_id,status)
      values ('${profile}','${profile}','${uid}','active');
    insert into public.shop_user_links(shop_profile_id,provider,provider_user_id,account_status)
      values ('${profile}','firebase','${uid}','active');
    insert into public.shop_session_families(id,profile_id) values ('${family}','${profile}');
    insert into public.shop_sessions(profile_id,family_id,session_jti,refresh_token_hash,expires_at,refresh_expires_at)
      values ('${profile}','${family}','race-${suffix}-old','race-${suffix}-old-hash',now()+interval '10 minutes',now()+interval '1 day')`);
  return { profile, family, uid, suffix };
}
function refresh(f) {
  return `select * from public.adishop_rotate_shop_session('race-${f.suffix}-old-hash',
    'race-${f.suffix}-new','race-${f.suffix}-new-hash',
    clock_timestamp()+interval '10 minutes',clock_timestamp()+interval '1 day')`;
}
function final(f) {
  return sql(`select (select account_status from public.shop_user_links where provider_user_id='${f.uid}')||'|'
    ||(select count(*) from public.shop_session_families where profile_id='${f.profile}' and revoked_at is null)||'|'
    ||(select count(*) from public.shop_sessions where profile_id='${f.profile}' and revoked_at is null)||'|'
    ||(select count(*) from public.shop_audit_logs where entity_id='${f.profile}' and action='identity.qa_firebase_revoked')`);
}
async function main() {
  const first = fixture(3);
  const rotating = run(`begin; ${refresh(first)}; select pg_sleep(1) /* sec03g_refresh_first */; commit`);
  await barrier('sec03g_refresh_first');
  const revoking = run(`select public.adishop_qa_revoke_firebase_identity('${first.uid}','firebase_disabled')`);
  const [a, b] = await Promise.all([rotating, revoking]);
  check('refresh-first rotation commits', a.code, 0);
  check('refresh-first revocation commits', b.code, 0);
  check('refresh-first final state', final(first), 'disabled|0|0|1');

  const second = fixture(4);
  const revocation = run(`begin; select public.adishop_qa_revoke_firebase_identity('${second.uid}','allowlist_removed');
    select pg_sleep(1) /* sec03g_revoke_first */; commit`);
  await barrier('sec03g_revoke_first');
  const lateRefresh = run(refresh(second));
  const [c, d] = await Promise.all([revocation, lateRefresh]);
  check('revoke-first revocation commits', c.code, 0);
  check('revoke-first refresh rejected', d.code === 0 ? 0 : 1, 1);
  check('revoke-first final state', final(second), 'disabled|0|0|1');
  console.log(`SEC03G race checks: ${passed}/${passed} passed`);
}
main().catch((error) => { console.error(error.message); process.exitCode = 1; });
