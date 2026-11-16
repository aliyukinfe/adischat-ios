// Synthetic two-connection PostgreSQL check; no remote endpoints.
import assert from 'node:assert/strict';
import { execFileSync, spawn } from 'node:child_process';

const psql = 'C:/PostgreSQL/17/pgsql/bin/psql.exe';
const database = process.env.PHASE9G_DB || 'phase9g17b_local';
const args = ['-X', '-v', 'ON_ERROR_STOP=1', '-At', '-h', '127.0.0.1',
  '-p', '55451', '-U', 'postgres', '-d', database];
const store = '92000000-0000-4000-8000-000000000001';
function sql(statement) {
  return execFileSync(psql, [...args, '-c', statement], { encoding: 'utf8' }).trim();
}
function run(statement) {
  return new Promise((resolve) => {
    const child = spawn(psql, [...args, '-c', statement], { windowsHide: true });
    let stderr = '';
    child.stderr.on('data', (data) => { stderr += data.toString(); });
    child.on('close', (code) => resolve({ code, stderr }));
  });
}
async function held() {
  for (let n = 0; n < 80; n++) {
    const active = Number(sql(`select count(*) from pg_stat_activity
      where datname='${database}' and state='active' and pid<>pg_backend_pid()
        and query like '%pg_sleep(2) /* qa-race-hold */%'`));
    if (active) return;
    await new Promise((resolve) => setTimeout(resolve, 25));
  }
  throw Error('lock barrier not reached');
}

let passed = 0;
function check(label, condition) {
  assert.ok(condition, label); passed++; console.log(`PASS ${label}`);
}
const revision = () => sql(`select updated_at from public.shop_stores where id='${store}'`);
const review = (expected) => `select public.adishop_qa_review_store('qa-g11-3',
  '${store}','${expected}','approve','Reviewed QA Store','Synthetic only','QA City')`;
sql(`select public.adishop_qa_review_store('qa-g11-3','${store}','${revision()}','revoke')`);
const before = revision();
const auditBefore = Number(sql(`select count(*) from public.shop_audit_logs
  where entity_id='${store}' and action='qa.store.approve'`));

const editFirst = run(`begin; update public.shop_stores set name='Edited Synthetic QA Store'
  where id='${store}'; select pg_sleep(2) /* qa-race-hold */; commit`);
await held();
const staleReview = run(review(before));
const [editResult, staleResult] = await Promise.all([editFirst, staleReview]);
check('edit-first transaction commits', editResult.code === 0);
check('edit-first reviewer gets stale revision', staleResult.code !== 0 && staleResult.stderr.includes('stale-revision'));
check('stale reviewer writes no audit', Number(sql(`select count(*) from public.shop_audit_logs
  where entity_id='${store}' and action='qa.store.approve'`)) === auditBefore);
check('stale reviewer writes no projection', Number(sql(`select count(*) from public.shop_qa_reviewed_stores
  where store_id='${store}'`)) === 0);

const reviewFirst = run(`begin; ${review(revision())};
  select pg_sleep(2) /* qa-race-hold */; commit`);
await held();
const laterEdit = run(`update public.shop_stores set name='Later Synthetic Edit' where id='${store}'`);
const [reviewResult, laterResult] = await Promise.all([reviewFirst, laterEdit]);
check('review-first transaction commits', reviewResult.code === 0);
check('review-first later edit commits', laterResult.code === 0);
check('later edit hides stale reviewed store', Number(sql(`select count(*)
  from public.shop_qa_public_stores where id='${store}'`)) === 0);
const current = revision();
sql(review(current));
const repeated = await run(review(current));
check('cross-request repeat denied', repeated.code !== 0 && repeated.stderr.includes('duplicate-review'));
check('repeated approval keeps one exact-revision projection', Number(sql(`select count(*)
  from public.shop_qa_reviewed_stores where store_id='${store}' and store_updated_at='${current}'`)) === 1);
sql(`select public.adishop_qa_review_store('qa-g11-3','${store}','${current}','revoke')`);
check('revocation hides reviewed store', Number(sql(`select count(*)
  from public.shop_qa_public_stores where id='${store}'`)) === 0);
console.log(`TOTAL ${passed} PASS 0 FAIL`);
