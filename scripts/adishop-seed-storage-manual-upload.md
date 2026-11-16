# AdisShop Seed Storage Manual Upload

Use this only if `scripts/upload_adishop_seed_assets.mjs` cannot be run with a local server-only key.

1. Open Supabase Dashboard for project `mcewjfnkrtiiolzibsuh`.
2. Go to Storage.
3. Open bucket `shop-product-images`.
4. Create folder `seed` if it does not exist.
5. Upload every `.webp` file from `assets/shop-seed/` into that `seed` folder.
6. Do not rename the files.

The final object paths must match the live database `storage_path` values exactly:

```text
seed/bluetooth-speaker.webp
seed/business-notebook.webp
seed/canvas-tote-bag.webp
seed/ceramic-coffee-cups.webp
seed/classic-cotton-t-shirt.webp
seed/cotton-towel-set.webp
seed/daily-planner.webp
seed/daily-skin-cream.webp
seed/honey-jar.webp
seed/kids-puzzle-set.webp
seed/light-jacket.webp
seed/natural-soap-pack.webp
seed/phone-stand.webp
seed/premium-coffee-beans.webp
seed/smart-watch.webp
seed/spice-mix.webp
seed/table-organizer.webp
seed/training-water-bottle.webp
seed/usb-c-fast-charger.webp
seed/wireless-earbuds.webp
```

After upload, verify from the repo root:

```powershell
$env:ADISHOP_SUPABASE_URL='https://mcewjfnkrtiiolzibsuh.supabase.co'
node scripts\verify_adishop_storage_assets.mjs
```

If using the local upload script instead of the Dashboard, provide the server-only key through the environment only:

```powershell
$env:ADISHOP_SUPABASE_URL='https://mcewjfnkrtiiolzibsuh.supabase.co'
$env:ADISHOP_SUPABASE_SECRET_KEY='<server-side-secret>'
node scripts\upload_adishop_seed_assets.mjs
Remove-Item Env:ADISHOP_SUPABASE_SECRET_KEY -ErrorAction SilentlyContinue
```

The release gate is satisfied only when this verifier reports `Failures: 0`.
