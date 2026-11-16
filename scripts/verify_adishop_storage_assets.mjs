import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const ROOT = resolve(new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const MAP_PATH = resolve(ROOT, 'scripts/adishop-seed-image-map.json');
const REPORT_PATH = resolve(ROOT, 'scripts/adishop-storage-verification-report.json');
const BUCKET = 'shop-product-images';
const url = process.env.ADISHOP_SUPABASE_URL;

if (!url || !/^https:\/\/[a-z0-9]+\.supabase\.co$/i.test(url)) {
  throw new Error('Set ADISHOP_SUPABASE_URL to the AdisShop Supabase project URL.');
}

const publicUrlFor = (path) => `${url}/storage/v1/object/public/${BUCKET}/${path}`;

async function main() {
  const map = JSON.parse(await readFile(MAP_PATH, 'utf8'));
  const report = [];

  for (const item of map) {
    const response = await fetch(publicUrlFor(item.storage_path), { method: 'HEAD' });
    const contentType = response.headers.get('content-type') || '';
    const contentLength = Number(response.headers.get('content-length') || '0');
    report.push({
      productTitle: item.product_title,
      storagePath: item.storage_path,
      httpStatus: response.status,
      contentType,
      contentLength,
      result: response.ok && contentType.includes('image/webp') && contentLength > 0 ? 'PASS' : 'FAIL',
    });
  }

  await writeFile(REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  const failures = report.filter((row) => row.result !== 'PASS');
  console.log(`Verified ${report.length} product image URLs. Failures: ${failures.length}. Report: scripts/adishop-storage-verification-report.json`);
  if (failures.length > 0) {
    process.exit(1);
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
