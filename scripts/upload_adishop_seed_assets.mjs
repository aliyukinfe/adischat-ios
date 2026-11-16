import { createClient } from '@supabase/supabase-js';
import { readFile, stat, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const ROOT = resolve(new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const MAP_PATH = resolve(ROOT, 'scripts/adishop-seed-image-map.json');
const REPORT_PATH = resolve(ROOT, 'scripts/adishop-seed-upload-report.json');
const BUCKET = 'shop-product-images';
const MAX_BYTES = 300_000;

const url = process.env.ADISHOP_SUPABASE_URL;
const secretKey = process.env.ADISHOP_SUPABASE_SECRET_KEY;

if (!url || !/^https:\/\/[a-z0-9]+\.supabase\.co$/i.test(url)) {
  throw new Error('Set ADISHOP_SUPABASE_URL to the AdisShop Supabase project URL.');
}

if (!secretKey) {
  throw new Error('Set ADISHOP_SUPABASE_SECRET_KEY for this local seed upload. The key is not logged or written.');
}

const supabase = createClient(url, secretKey, {
  auth: { persistSession: false, autoRefreshToken: false },
});

const publicUrlFor = (path) => `${url}/storage/v1/object/public/${BUCKET}/${path}`;

async function verifyPublicObject(path) {
  const response = await fetch(publicUrlFor(path), { method: 'HEAD' });
  return {
    status: response.status,
    contentType: response.headers.get('content-type') || '',
    contentLength: Number(response.headers.get('content-length') || '0'),
    ok: response.ok,
  };
}

async function main() {
  const map = JSON.parse(await readFile(MAP_PATH, 'utf8'));
  if (!Array.isArray(map) || map.length !== 20) {
    throw new Error(`Expected 20 mapped product images, found ${Array.isArray(map) ? map.length : 'invalid JSON'}.`);
  }

  const report = [];

  for (const item of map) {
    if (!item.storage_path?.endsWith('.webp') || !item.local_source?.endsWith('.webp')) {
      throw new Error(`Invalid non-WebP mapping for ${item.product_title || item.product_id}.`);
    }
    if (item.storage_path.includes(BUCKET)) {
      throw new Error(`storage_path must not include bucket name: ${item.storage_path}`);
    }

    const sourcePath = resolve(ROOT, item.local_source);
    const info = await stat(sourcePath);
    if (info.size <= 0 || info.size > MAX_BYTES) {
      throw new Error(`${item.local_source} has invalid size ${info.size}; expected 1..${MAX_BYTES} bytes.`);
    }

    const existing = await verifyPublicObject(item.storage_path);
    if (existing.ok) {
      throw new Error(`Refusing to overwrite existing public object ${item.storage_path}.`);
    }

    const bytes = await readFile(sourcePath);
    const { error } = await supabase.storage.from(BUCKET).upload(item.storage_path, bytes, {
      contentType: 'image/webp',
      upsert: false,
      cacheControl: '31536000',
    });

    if (error) {
      throw new Error(`Upload failed for ${item.storage_path}: ${error.message}`);
    }

    const verify = await verifyPublicObject(item.storage_path);
    report.push({
      productTitle: item.product_title,
      storagePath: item.storage_path,
      localSource: item.local_source,
      bytes: info.size,
      httpStatus: verify.status,
      contentType: verify.contentType,
      contentLength: verify.contentLength,
      result: verify.ok && verify.contentType.includes('image/webp') && verify.contentLength > 0 ? 'PASS' : 'FAIL',
    });
  }

  await writeFile(REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`, 'utf8');

  const failures = report.filter((row) => row.result !== 'PASS');
  if (failures.length > 0) {
    throw new Error(`${failures.length} uploaded objects failed public verification. See scripts/adishop-seed-upload-report.json.`);
  }

  console.log(`Uploaded and verified ${report.length} product images. Report: scripts/adishop-seed-upload-report.json`);
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
