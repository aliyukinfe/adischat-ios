// Mechanical local bundle generation. No database access or deployment.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';

const files = [
  'phase9c3_seller_sla_candidate.sql',
  'phase9c2_buyer_cancellation_candidate.sql',
  'phase9c6_seller_rejection_inventory_candidate.sql',
  'phase9c5_seller_expiry_inventory_candidate.sql',
];
const parts = files.map((name) => {
  const source = readFileSync(`supabase/validation/${name}`, 'utf8').trim();
  assert.match(source, /^--[^\n]*\n(?:--[^\n]*\n)*begin;\s*set local lock_timeout = '5s';/i, name);
  assert.match(source, /\bcommit;\s*$/i, name);
  const body = source
    .replace(/^--[^\n]*\n(?:--[^\n]*\n)*begin;\s*set local lock_timeout = '5s';\s*/i, '')
    .replace(/\s*commit;\s*$/i, '');
  assert.ok(!/\b(?:begin|commit);/i.test(body), `${name} has nested transaction boundaries`);
  return `-- Source: ${name}\n${body}`;
});
const sql = `-- LOCAL REVIEW CANDIDATE ONLY. No cron activation or production approval.\n` +
  `-- Apply as one transaction only after separate production authorization.\n` +
  `begin;\nset local lock_timeout = '5s';\n\n${parts.join('\n\n')}\ncommit;\n`;
assert.ok(!/cron\.(schedule|unschedule)/i.test(sql), 'cron operation in bundle');
writeFileSync('supabase/validation/phase9c7_integrated_order_release_candidate.sql', sql, 'utf8');
process.stdout.write('Generated one-transaction local order release candidate\n');
