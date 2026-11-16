# SEC-03M.1 staging backup package (review only)

No script in this package has been approved for execution against staging. Target: `opkqyvttbyezjbqwkvho` (`adisgebeya-staging`, `eu-west-1`) through `aws-0-eu-west-1.pooler.supabase.com:5432`, database `postgres`, username `postgres.opkqyvttbyezjbqwkvho`, TLS `verify-full`. `Common.ps1` rejects other settings and never reads the generic `SUPABASE_DB_PASSWORD`. The CA file must match a SHA-256 fingerprint obtained independently from the **same staging project's Database Settings** in the Supabase Dashboard; a matching hash supplied from the same untrusted file is not independent verification. Libpq then verifies the certificate chain and pooler hostname on connection.

## Scope and limitations

`01-ExportDatabase.ps1` is a read-only PostgreSQL 17.11 custom-format dump of `public`, `auth`, `storage`, `supabase_migrations`, and `shop_qa_private` in one snapshot. `auth` is required because `shop_profiles.supabase_user_id` references `auth.users`. `shop_qa_private` contains five Shop helper functions used by public Shop RPCs and two `shop_product_images` triggers; omitting it would leave the local restore incomplete. The exporter requires those exact function signatures and both trigger entries in the archive. `storage` preserves bucket/object metadata, not object bytes. The archive retains data, sequences, functions, triggers, RLS policies and ACLs. It does not include extension-owned schemas, Realtime, platform configuration, global role definitions/passwords, Firebase/Cloudflare state or external secrets. `pg_dump` does not export global roles. The local restore creates no-login role placeholders locally, skips original ownership, and attempts grant restoration. Missing roles or managed-platform dependencies fail the restore. This is not a full Supabase disaster-recovery image.

The disposable role bootstrap includes a `NOLOGIN` placeholder for staging grants to `postgres`; it does not give that role superuser or login capability. `pg_restore --no-owner` leaves local objects owned by `sec03m_restore`, so original Supabase ownership and role memberships are not reproduced. Staging also has `supabase_vault` in the managed `vault` schema, which is not available in the local PostgreSQL 17 installation or included in this archive. The deployed `adishop_qa_reveal_delivery_code` and `adishop_qa_verify_delivery_code` functions reference `vault`; even if their definitions restore, their Vault-backed behavior cannot be verified locally. Do not create stub Vault functions or drop these functions to force a passing restore.

Before dumping, the exporter checks for unexpected non-system schemas and verifies that the archive contains key Shop, Auth, Storage and migration tables. A `--schema`-limited `pg_dump` does **not** automatically include dependencies outside those schemas; extensions and managed platform functions remain a restore-compatibility risk. Review any newly added schema before changing the allowlist.

Counts are taken after the dump, not in its snapshot. If staging changes during export, comparison can fail; do not waive the mismatch. Local verification checks every table count plus table, RLS, function, sequence, index, constraint, trigger, policy, grant and migration-row totals. It does not prove all definition-level or hosted Auth/Storage parity.

## Retention and credentials

Retained files belong under `%LOCALAPPDATA%\AdisGebeya\SEC-03M\backups`, outside Git, with current-user ACLs. Export and restore now **refuse to run** unless BitLocker reports that this volume is fully encrypted and protected; a failed or unavailable BitLocker status blocks execution. `Protect-Archive.ps1` encrypts the dump using Windows DPAPI CurrentUser, verifies a decrypt/hash round trip, and the exporter removes its private plaintext working file even on failure. File deletion alone cannot guarantee secure erasure. The manifest contains hashes and counts, not credentials or rows. DPAPI is tied to the Windows profile: off-machine recovery needs a separately approved key/profile recovery plan. This package verifies local recovery, not off-site disaster recovery.

Only process-scoped `ADISGEBEYA_STAGING_DB_HOST`, `ADISGEBEYA_STAGING_DB_PORT`, `ADISGEBEYA_STAGING_DB_NAME`, `ADISGEBEYA_STAGING_DB_USER`, `ADISGEBEYA_STAGING_DB_PASSWORD`, `ADISGEBEYA_STAGING_DB_SSLMODE`, `ADISGEBEYA_STAGING_DB_SSLROOTCERT`, and `ADISGEBEYA_STAGING_DB_CA_SHA256` are accepted. Run from the approved PowerShell session that owns them; Codex's separate process cannot inherit them. The password goes to PostgreSQL child processes through temporary `PGPASSWORD`, never arguments, source or chat. Remote `PGOPTIONS` requests read-only transactions. Do not enable transcript/debug logging in that session. Clear credentials after verification.

## Proposed commands (DO NOT RUN before separate approval)

From `C:\Users\adema\adischat-android` in Windows PowerShell 5.1:

```powershell
# Offline, no database connection or files created.
& .\supabase\validation\sec03m_staging_backup\00-DryRun.ps1
# Offline session and CA checks, from the credential-bearing PowerShell session.
& .\supabase\validation\sec03m_staging_backup\00-DryRun.ps1 -CheckSession
# Requires explicit approval: read-only staging dump, then immediate encryption.
& .\supabase\validation\sec03m_staging_backup\01-ExportDatabase.ps1
# Supply the retained private manifest path from the export; local-only restore.
& .\supabase\validation\sec03m_staging_backup\03-RestoreLocal.ps1 -ManifestPath '<private backup directory>\manifest.json'
# Only after separately approved Storage-byte export:
& .\supabase\validation\sec03m_staging_backup\04-VerifyStorageManifest.ps1 -ManifestPath '<private storage directory>\storage-manifest.json'
```

The restore uses a fresh SCRAM-protected PostgreSQL 17 cluster on `127.0.0.1:55445` with a random local-only password, then stops and removes it. It never uses the staging password locally. Restore errors, grant mismatches, missing tables and count differences fail closed. `LocalRoles.sql` is for disposable local PostgreSQL only; never run it on Supabase.

## Private Storage bytes: separate gate

Preflight inventory: two private buckets, `shop-product-images` (three objects) and `shop-store-assets` (zero). The SQL archive contains metadata only. To preserve bytes, obtain a separately approved staging-only Storage read credential via protected session management. The database password is not a Storage API credential. Prefer a constrained read-only authorization path; a broad staging service key needs separate privilege review. Never place it in URLs, arguments, source, or manifests.

After approval, pin the API to `https://opkqyvttbyezjbqwkvho.supabase.co`. Enumerate both buckets with the Storage object-list API, traversing prefixes and every pagination page. For each object retain bucket, exact path, object ID, version/ETag if supplied, MIME type, size and metadata. Download through an authenticated private-object GET only. Hash plaintext bytes, DPAPI-encrypt each file under a flat unique name in a private directory, verify decrypt/hash, then remove plaintext. Write `storage-manifest.json` with `projectRef`, plus `objects[]` entries containing `bucket`, `path`, `encryptedFile`, `sha256`, `encryptedSha256`, and `metadata`. Compare its per-bucket object counts against a fresh hosted inventory; changed objects require a new export or explicit gap report. `04-VerifyStorageManifest.ps1` validates retained encrypted files locally, not hosted parity. Never make buckets public or create permanent public URLs.
