import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const dir = dirname(fileURLToPath(import.meta.url));
const sourceBytes = readFileSync(join(dir, 'seller_center_v3_category_proposal.json'));
const source = JSON.parse(sourceBytes.toString('utf8'));
const review = JSON.parse(readFileSync(join(dir, 'seller_center_v4_reviewed_metadata_delta.json'), 'utf8'));
const android = readFileSync(join(dir, '../../android/app/src/main/java/com/adischat/app/feature/shop/presentation/SellerUnitSuggestions.kt'), 'utf8');

test('reviewed metadata diff is complete and pinned to its source', () => {
  assert.equal(createHash('sha256').update(sourceBytes).digest('hex'), review.sourceProposalSha256);
  assert.equal(review.newDefinitionCodes.length, 19);
  assert.equal(new Set(review.newDefinitionCodes).size, 19);
  assert.equal(source.categories.reduce((count, row) => count + row.proposedOptionalAttributes.length, 0), 472);
  assert.equal(review.mappingAdditions.count, 472);
  assert.equal(review.requiredToOptional.reduce((count, row) => count + row.codes.length, 0), 6);
  assert.equal(review.mappingRemovals.reduce((count, row) => count + row.codes.length, 0), 5);
  for (const removal of review.mappingRemovals) {
    const category = source.categories.find(row => row.id === removal.categoryId);
    assert.ok(category);
    assert.ok(removal.codes.every(code => category.proposedRemoveMappings.includes(code)));
  }
  assert.equal(review.expectedIfSeparatelyApproved.categoryAttributeMappings, 373 + 472 - 5);
  assert.equal(review.expectedIfSeparatelyApproved.selectableLeafMappings, 342 + 472 - 5);
  assert.equal(review.sourceSqlExecutableAsReviewed, false);
});

test('Android advisory suggestions match all 185 proposed leaf lists', () => {
  const rows = [...android.matchAll(/^\s*"([0-9a-f-]{36})" to setOf\(([^)]*)\),$/gm)];
  assert.equal(rows.length, 185);
  const byId = new Map(rows.map(([, id, values]) => [id, [...values.matchAll(/"([A-Z_]+)"/g)].map(match => match[1])]));
  for (const category of source.categories) {
    assert.deepEqual(byId.get(category.id), category.proposedAllowedSellingUnits, category.path);
  }
});
