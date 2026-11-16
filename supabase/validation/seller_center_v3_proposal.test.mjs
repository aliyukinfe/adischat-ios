import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const dir = dirname(fileURLToPath(import.meta.url));
const report = JSON.parse(readFileSync(join(dir, 'seller_center_v3_category_proposal.json'), 'utf8'));
const metadataSql = readFileSync(join(dir, 'seller_center_v3_category_metadata_proposal.sql'), 'utf8');
const unitSql = readFileSync(join(dir, 'seller_center_v3_selling_unit_enforcement_proposal.sql'), 'utf8');

test('every seller-selectable leaf has a reviewed proposal', () => {
  assert.equal(report.categories.length, 185);
  assert.equal(new Set(report.categories.map(row => row.id)).size, 185);
  assert.equal(report.summary.proposedAfter.directlyMapped, 180);
  assert.equal(report.categories.filter(row => row.beforeClassification === 'INTENTIONALLY_MINIMAL').length, 5);
  assert.ok(report.categories.every(row => row.proposedAllowedSellingUnits.length > 0));
});

test('Office Equipment receives seven optional fields', () => {
  const office = report.categories.find(row => row.path === 'Office, School & Stationery > Office Equipment');
  assert.deepEqual(office.proposedOptionalAttributes, [
    'EQUIPMENT_TYPE', 'BRAND', 'MODEL', 'CAPACITY', 'DIMENSIONS', 'POWER_REQUIREMENTS', 'COMPATIBILITY',
  ]);
  assert.deepEqual(office.proposedAllowedSellingUnits, ['PIECE', 'SET']);
});

test('invalid leaves have narrowly scoped required-rule and mapping corrections', () => {
  const invalid = report.categories.filter(row => row.beforeClassification === 'INVALID');
  assert.deepEqual(invalid.map(row => row.path).sort(), [
    'Electronics & Technology > Chargers & Cables',
    'Electronics & Technology > Gaming',
    'Electronics & Technology > Phone Accessories',
  ]);
  assert.ok(invalid.every(row => row.proposedRequiredRuleChange.required === false));
  assert.ok(invalid.every(row => row.proposedRemoveMappings.includes('RAM')));
  assert.equal((metadataSql.match(/set required=false/g) || []).length, 3);
  assert.equal((metadataSql.match(/not exists \(select 1 from public\.shop_products/g) || []).length, 3);
});

test('unit proposal preserves observed kilogram listing and is review-only', () => {
  const rhinestones = report.categories.find(row => row.path.endsWith('Rhinestones & Decorative Stones'));
  assert.ok(rhinestones.proposedAllowedSellingUnits.includes('KILOGRAM'));
  assert.match(unitSql, /REVIEW ONLY/);
  assert.match(unitSql, /invalid-category-selling-unit/);
  assert.match(metadataSql, /REVIEW ONLY/);
});
