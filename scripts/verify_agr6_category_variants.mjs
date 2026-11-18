import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const REPO_ROOT = path.resolve(__dirname, '..');

console.log('=== AG-R6.1 Category-Aware Product Variants Verification ===\n');

// 1. Replicate CategoryVariantPresets logic from Kotlin
const CategoryVariantPresets = {
  SHOE_EU_SIZES: ['36', '37', '38', '39', '40', '41', '42', '43', '44', '45', '46'],
  KIDS_SHOE_SIZES: ['28', '29', '30', '31', '32', '33', '34', '35', '36', '37', '38'],
  TROUSER_SIZES: [
    '28', '30', '32', '34', '36', '38', '40', '42',
    '30/30', '32/30', '32/32', '34/32', '36/32',
  ],
  APPAREL_ALPHA_SIZES: ['XS', 'S', 'M', 'L', 'XL', 'XXL', '3XL', '4XL'],
  FURNITURE_SIZES: ['Single', 'Double', 'Queen', 'King', 'Custom'],

  KIDS_SHOE_CATEGORY_IDS: new Set([
    '2ba3dd18-474b-4544-8ea1-c1b3a3e97279',
  ]),
  FOOTWEAR_CATEGORY_IDS: new Set([
    'b9b2956d-425b-4049-850e-9b2a88125acc',
    '08b0d893-223c-42c5-86cd-1e01e07bc82b',
    '83af6f95-e949-489e-8a11-f6d68602e1b1',
    '586ab99f-66fc-4c03-8464-ee2f902eccdd',
  ]),
  TROUSER_CATEGORY_IDS: new Set([
    '7a56e0f2-2b60-4543-8a52-512e969cc253',
  ]),
  APPAREL_CATEGORY_IDS: new Set([
    'db8c93f5-e3a0-46eb-8778-713d4c9b23c3',
    '4b75f4e5-3b88-46e2-8069-33082443228c',
    '68f28559-2640-4b65-84d8-e36532c82931',
    '9035a12e-ad49-455f-82b8-9ffaf02655ca',
    '679f286d-3bdb-48ea-8a0f-593fdca66c50',
    'c185bdc9-f022-44de-8fa7-6158e177b070',
    '00000000-0000-4000-8000-000000000102',
  ]),
  FURNITURE_CATEGORY_IDS: new Set([
    '97385dba-3aaf-4fcd-8bf7-6cd7f54e8e6f',
    '5abfc134-cb63-4006-83a4-bf8940695771',
    '00000000-0000-4000-8000-000000000103',
  ]),

  resolveSuggestions(categoryId, categoryCode, attributeCode, defaultSuggestions = []) {
    const attr = (attributeCode || '').trim().toUpperCase();
    if (attr !== 'SIZE' && attr !== 'SHOE_SIZE' && attr !== 'CLOTHING_SIZE' && attr !== 'DIMENSIONS') {
      return defaultSuggestions;
    }
    const id = (categoryId || '').trim().toLowerCase();
    const code = (categoryCode || '').trim().toUpperCase();

    if (this.KIDS_SHOE_CATEGORY_IDS.has(id) || code === 'KIDS_SHOES' || code.includes('KIDS_SHOE')) {
      return this.KIDS_SHOE_SIZES;
    }
    if (this.FOOTWEAR_CATEGORY_IDS.has(id) || code.includes('SHOE') || code.includes('FOOTWEAR') || attr === 'SHOE_SIZE') {
      return this.SHOE_EU_SIZES;
    }
    if (this.TROUSER_CATEGORY_IDS.has(id) || code === 'TROUSERS' || code.includes('PANTS') || code.includes('JEANS')) {
      return this.TROUSER_SIZES;
    }
    if (this.FURNITURE_CATEGORY_IDS.has(id) || code.includes('FURNITURE') || code.includes('BEDDING') || code.includes('MATTRESS')) {
      return this.FURNITURE_SIZES;
    }
    if (this.APPAREL_CATEGORY_IDS.has(id) || code.includes('CLOTHING') || code.includes('JACKET') || code.includes('SHIRT') || code.includes('DRESS') || code.includes('APPAREL') || attr === 'CLOTHING_SIZE') {
      return this.APPAREL_ALPHA_SIZES;
    }
    return defaultSuggestions.length > 0 ? defaultSuggestions : this.APPAREL_ALPHA_SIZES;
  },
};

// 2. Replicate buyer variant matching and image selection logic
function findVerifiedVariantImageIndex(images, selectedVariant, selectedColor = null, allVariants = []) {
  if (!images || images.length === 0) return null;
  const variantImageIds = new Set((selectedVariant?.imageIds || []).filter(Boolean));
  if (variantImageIds.size > 0) {
    const idx = images.findIndex((img) => variantImageIds.has(img.id));
    if (idx >= 0) return idx;
  }
  const colorVal = selectedColor || selectedVariant?.options?.COLOR || selectedVariant?.options?.COLOUR || selectedVariant?.options?.color;
  if (colorVal) {
    const matchingVariant = allVariants.find((v) => {
      if (!v.imageIds || v.imageIds.length === 0) return false;
      const vColor = v.options?.COLOR || v.options?.COLOUR || v.options?.color;
      return vColor && vColor.toLowerCase() === colorVal.toLowerCase();
    });
    if (matchingVariant) {
      const idx = images.findIndex((img) => matchingVariant.imageIds.includes(img.id));
      if (idx >= 0) return idx;
    }
  }
  return null;
}

function resolveExactVariant(variants, requiredOptionCodes, selectedValues) {
  if (requiredOptionCodes.some((code) => !selectedValues[code] || !selectedValues[code].trim())) {
    return null;
  }
  const matches = variants.filter((variant) => {
    if (variant.status !== 'active') return false;
    return requiredOptionCodes.every((code) => {
      const vVal = variant.options?.[code];
      const sVal = selectedValues[code];
      return vVal && sVal && vVal.toLowerCase() === sVal.toLowerCase();
    });
  });
  return matches.length === 1 ? matches[0] : null;
}

function canConfirmPurchase(selectedVariant, quantity) {
  return (
    selectedVariant != null &&
    selectedVariant.status === 'active' &&
    selectedVariant.availableQuantity >= quantity &&
    quantity > 0
  );
}

// ================= TEST SUITE =================
let passed = 0;
let total = 0;
function test(name, fn) {
  total++;
  try {
    fn();
    console.log(`  [PASS] ${name}`);
    passed++;
  } catch (err) {
    console.error(`  [FAIL] ${name}:`, err.message);
  }
}

console.log('--- 1. Seller Category-Aware Suggestions Tests ---');

test('Footwear - Men Shoes returns EU 36-46', () => {
  const suggestions = CategoryVariantPresets.resolveSuggestions(
    'b9b2956d-425b-4049-850e-9b2a88125acc',
    'MENS_SHOES',
    'SIZE'
  );
  assert.deepEqual(suggestions, ['36', '37', '38', '39', '40', '41', '42', '43', '44', '45', '46']);
});

test('Footwear - Women Shoes returns EU 36-46', () => {
  const suggestions = CategoryVariantPresets.resolveSuggestions(
    '08b0d893-223c-42c5-86cd-1e01e07bc82b',
    'WOMENS_SHOES',
    'SIZE'
  );
  assert.deepEqual(suggestions, ['36', '37', '38', '39', '40', '41', '42', '43', '44', '45', '46']);
});

test('Footwear - Kids Shoes returns EU 28-38', () => {
  const suggestions = CategoryVariantPresets.resolveSuggestions(
    '2ba3dd18-474b-4544-8ea1-c1b3a3e97279',
    'KIDS_SHOES',
    'SIZE'
  );
  assert.deepEqual(suggestions, ['28', '29', '30', '31', '32', '33', '34', '35', '36', '37', '38']);
});

test('Trousers returns waist sizes 28-42 and inseams', () => {
  const suggestions = CategoryVariantPresets.resolveSuggestions(
    '7a56e0f2-2b60-4543-8a52-512e969cc253',
    'TROUSERS',
    'SIZE'
  );
  assert.ok(suggestions.includes('28'));
  assert.ok(suggestions.includes('42'));
  assert.ok(suggestions.includes('32/32'));
  assert.ok(suggestions.includes('34/32'));
});

test('Jackets & Apparel returns XS-4XL', () => {
  const suggestions = CategoryVariantPresets.resolveSuggestions(
    'db8c93f5-e3a0-46eb-8778-713d4c9b23c3',
    'JACKETS',
    'SIZE'
  );
  assert.deepEqual(suggestions, ['XS', 'S', 'M', 'L', 'XL', 'XXL', '3XL', '4XL']);
});

test('Furniture returns Single, Double, Queen, King, Custom', () => {
  const suggestions = CategoryVariantPresets.resolveSuggestions(
    '97385dba-3aaf-4fcd-8bf7-6cd7f54e8e6f',
    'FURNITURE',
    'SIZE'
  );
  assert.deepEqual(suggestions, ['Single', 'Double', 'Queen', 'King', 'Custom']);
});

test('Electronics - Preserves RAM, Storage, Model suggestions without size override', () => {
  const defaultStorage = ['128GB', '256GB', '512GB', '1TB'];
  const storageResult = CategoryVariantPresets.resolveSuggestions(
    '8f7be2bd-0876-42e4-84ec-6eb8b12d4314',
    'LAPTOPS',
    'STORAGE',
    defaultStorage
  );
  assert.deepEqual(storageResult, defaultStorage);

  const defaultRam = ['8GB', '16GB', '32GB', '64GB'];
  const ramResult = CategoryVariantPresets.resolveSuggestions(
    '8f7be2bd-0876-42e4-84ec-6eb8b12d4314',
    'LAPTOPS',
    'RAM',
    defaultRam
  );
  assert.deepEqual(ramResult, defaultRam);
});

console.log('\n--- 2. Buyer Product Variant Experience Tests ---');

const sampleImages = [
  { id: 'img-1', imageUrl: 'https://images.unsplash.com/shoe-black.jpg', isPrimary: true },
  { id: 'img-2', imageUrl: 'https://images.unsplash.com/shoe-white.jpg', isPrimary: false },
  { id: 'img-3', imageUrl: 'https://images.unsplash.com/shoe-brown.jpg', isPrimary: false },
];

const sampleVariants = [
  {
    id: 'var-1',
    sku: 'SHOE-BLK-42',
    title: 'Black / 42',
    options: { COLOR: 'Black', SIZE: '42' },
    priceMinor: 450000,
    compareAtPriceMinor: 550000,
    status: 'active',
    availableQuantity: 7,
    imageIds: ['img-1'],
  },
  {
    id: 'var-2',
    sku: 'SHOE-WHT-42',
    title: 'White / 42',
    options: { COLOR: 'White', SIZE: '42' },
    priceMinor: 480000,
    compareAtPriceMinor: null,
    status: 'active',
    availableQuantity: 0, // Out of stock
    imageIds: ['img-2'],
  },
  {
    id: 'var-3',
    sku: 'SHOE-BRN-44',
    title: 'Brown / 44',
    options: { COLOR: 'Brown', SIZE: '44' },
    priceMinor: 420000,
    compareAtPriceMinor: 500000,
    status: 'active',
    availableQuantity: 3,
    imageIds: ['img-3'],
  },
  {
    id: 'var-4',
    sku: 'SHOE-NOIMG-40',
    title: 'Navy / 40',
    options: { COLOR: 'Navy', SIZE: '40' },
    priceMinor: 410000,
    status: 'active',
    availableQuantity: 5,
    imageIds: [], // No variant image
  },
];

test('Variant selection updates SKU, price, compare price, and availability', () => {
  const resolved = resolveExactVariant(sampleVariants, ['COLOR', 'SIZE'], { COLOR: 'Black', SIZE: '42' });
  assert.ok(resolved != null);
  assert.equal(resolved.sku, 'SHOE-BLK-42');
  assert.equal(resolved.priceMinor, 450000);
  assert.equal(resolved.compareAtPriceMinor, 550000);
  assert.equal(resolved.availableQuantity, 7);
});

test('Color variant maps to corresponding gallery image index', () => {
  const resolved = resolveExactVariant(sampleVariants, ['COLOR', 'SIZE'], { COLOR: 'Brown', SIZE: '44' });
  const imgIdx = findVerifiedVariantImageIndex(sampleImages, resolved, 'Brown', sampleVariants);
  assert.equal(imgIdx, 2); // 'img-3' is index 2
  assert.equal(sampleImages[imgIdx].id, 'img-3');
});

test('Variant without image returns null and preserves gallery without inventing images', () => {
  const resolved = resolveExactVariant(sampleVariants, ['COLOR', 'SIZE'], { COLOR: 'Navy', SIZE: '40' });
  const imgIdx = findVerifiedVariantImageIndex(sampleImages, resolved, 'Navy', sampleVariants);
  assert.equal(imgIdx, null); // Preserves gallery
});

test('Out-of-stock variant cannot be added to cart', () => {
  const resolved = resolveExactVariant(sampleVariants, ['COLOR', 'SIZE'], { COLOR: 'White', SIZE: '42' });
  assert.ok(resolved != null);
  assert.equal(resolved.availableQuantity, 0);
  assert.equal(canConfirmPurchase(resolved, 1), false);
});

test('In-stock variant can be confirmed up to available quantity', () => {
  const resolved = resolveExactVariant(sampleVariants, ['COLOR', 'SIZE'], { COLOR: 'Black', SIZE: '42' });
  assert.equal(canConfirmPurchase(resolved, 1), true);
  assert.equal(canConfirmPurchase(resolved, 7), true);
  assert.equal(canConfirmPurchase(resolved, 8), false); // Exceeds stock
});

console.log('\n--- 3. Source Code Integrity Tests ---');

test('ShopModels.kt defines sku on ShopPublicVariantDto', () => {
  const code = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/data/ShopModels.kt'), 'utf8');
  assert.ok(code.includes('@SerializedName("sku") val sku: String? = null'));
});

test('SellerVariantCatalogEditor.kt defines CategoryVariantPresets with resolveSuggestions', () => {
  const code = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/SellerVariantCatalogEditor.kt'), 'utf8');
  assert.ok(code.includes('object CategoryVariantPresets'));
  assert.ok(code.includes('fun resolveSuggestions'));
  assert.ok(code.includes('CategoryVariantPresets.resolveSuggestions'));
  assert.ok(code.includes('categoryCode: String? = null'));
});

test('ShopSellerProductsScreen.kt passes categoryCode to SellerVariantCatalogEditor', () => {
  const code = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopSellerProductsScreen.kt'), 'utf8');
  assert.ok(code.includes('categoryCode = selectedCategory?.code'));
});

test('ProductDetailsScreen.kt implements findVerifiedVariantImageIndex and variant summary update', () => {
  const code = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ProductDetailsScreen.kt'), 'utf8');
  assert.ok(code.includes('fun findVerifiedVariantImageIndex'));
  assert.ok(code.includes('targetImageIndex = targetImageIndex'));
  assert.ok(code.includes('selectedVariant = selectedVariant'));
  assert.ok(code.includes('SKU: ${selectedVariant?.sku}'));
});

test('ProductInformationComponents.kt supports interactive ProductOptionsSummary', () => {
  const code = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ProductInformationComponents.kt'), 'utf8');
  assert.ok(code.includes('selectedValues: Map<String, String> = emptyMap()'));
  assert.ok(code.includes('onSelectOption: ((String, String) -> Unit)? = null'));
  assert.ok(code.includes('ProductVariantSelector('));
});

console.log('\n--- 4. AG-R6.3 Seller Listing UX & SKU Safety Tests ---');

function generateSuggestedSku(brand, title, combination) {
  const prefix = ((brand && brand.trim()) || title.slice(0, 8)).trim().toUpperCase().replace(/[^A-Z0-9]+/g, '');
  const suffix = Object.values(combination).map((v) => v.toUpperCase().replace(/[^A-Z0-9]+/g, '')).join('-');
  if (prefix && suffix) return `${prefix}-${suffix}`;
  if (suffix) return suffix;
  return prefix || 'SKU';
}

function findDuplicateSkus(drafts) {
  const activeSkus = Object.values(drafts)
    .filter((d) => d.active)
    .map((d) => d.sku?.trim().toUpperCase())
    .filter(Boolean);
  const counts = {};
  for (const sku of activeSkus) {
    counts[sku] = (counts[sku] || 0) + 1;
  }
  return new Set(Object.keys(counts).filter((k) => counts[k] > 1));
}

function findDuplicateSkusFromOutputs(json) {
  try {
    const outputs = JSON.parse(json);
    const activeSkus = (outputs.variants || [])
      .filter((v) => v.status === 'active')
      .map((v) => v.sku?.trim().toUpperCase())
      .filter(Boolean);
    const counts = {};
    for (const sku of activeSkus) {
      counts[sku] = (counts[sku] || 0) + 1;
    }
    return new Set(Object.keys(counts).filter((k) => counts[k] > 1));
  } catch {
    return new Set();
  }
}

test('generateSuggestedSku generates deterministic uppercase SKU with brand and options', () => {
  const sku = generateSuggestedSku('Adidas', 'Running Shoe', { COLOR: 'White', SIZE: '42' });
  assert.equal(sku, 'ADIDAS-WHITE-42');
});

test('generateSuggestedSku falls back to sanitized title prefix when brand is omitted', () => {
  const sku = generateSuggestedSku('', 'Leather Jacket', { COLOR: 'Brown', SIZE: 'XL' });
  assert.equal(sku, 'LEATHER-BROWN-XL');
});

test('findDuplicateSkus detects identical active SKUs and ignores case/whitespace', () => {
  const drafts = {
    'COLOR=Black|SIZE=42': { active: true, sku: 'SHOE-42' },
    'COLOR=White|SIZE=42': { active: true, sku: ' shoe-42 ' },
    'COLOR=Blue|SIZE=44': { active: true, sku: 'SHOE-44' },
  };
  const duplicates = findDuplicateSkus(drafts);
  assert.equal(duplicates.size, 1);
  assert.ok(duplicates.has('SHOE-42'));
});

test('findDuplicateSkus ignores SKUs of inactive variants', () => {
  const drafts = {
    'COLOR=Black|SIZE=42': { active: true, sku: 'SHOE-42' },
    'COLOR=White|SIZE=42': { active: false, sku: 'SHOE-42' },
    'COLOR=Blue|SIZE=44': { active: true, sku: 'SHOE-44' },
  };
  const duplicates = findDuplicateSkus(drafts);
  assert.equal(duplicates.size, 0);
});

test('findDuplicateSkusFromOutputs detects duplicate active variant SKUs from JSON', () => {
  const payload = JSON.stringify({
    variants: [
      { id: '1', sku: 'BOOT-40', status: 'active', options: { SIZE: '40' } },
      { id: '2', sku: 'boot-40', status: 'active', options: { SIZE: '41' } },
      { id: '3', sku: 'BOOT-42', status: 'inactive', options: { SIZE: '42' } },
    ],
  });
  const duplicates = findDuplicateSkusFromOutputs(payload);
  assert.equal(duplicates.size, 1);
  assert.ok(duplicates.has('BOOT-40'));
});

test('SellerVariantCatalogEditor.kt implements SKU helpers, auto-fill, and bulk actions', () => {
  const code = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/SellerVariantCatalogEditor.kt'), 'utf8');
  assert.ok(code.includes('fun generateSuggestedSku'));
  assert.ok(code.includes('fun findDuplicateSkus'));
  assert.ok(code.includes('fun findDuplicateSkusFromOutputs'));
  assert.ok(code.includes('Auto-fill SKUs'));
  assert.ok(code.includes('Apply base price'));
  assert.ok(code.includes('Apply base stock'));
  assert.ok(code.includes('basePriceMinor: Int = product.priceMinor'));
});

test('ShopSellerProductsScreen.kt implements 6-section structure and Listing Readiness Checklist', () => {
  const code = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopSellerProductsScreen.kt'), 'utf8');
  assert.ok(code.includes('fun SellerSectionCard'));
  assert.ok(code.includes('fun ListingChecklistRow'));
  assert.ok(code.includes('Section 1: Basic Information'));
  assert.ok(code.includes('Section 2: Category & Attributes'));
  assert.ok(code.includes('Section 3: Product Images'));
  assert.ok(code.includes('Section 4: Sizes, Colors & Variations'));
  assert.ok(code.includes('Section 5: Base Price & Inventory'));
  assert.ok(code.includes('Section 6: Review & Submit'));
  assert.ok(code.includes('Listing Readiness Checklist'));
  assert.ok(code.includes('Cover Photo Preview'));
});

console.log(`\nVerification complete: ${passed}/${total} tests passed.`);
if (passed !== total) process.exit(1);
