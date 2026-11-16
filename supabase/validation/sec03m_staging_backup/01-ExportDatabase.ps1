$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
. (Join-Path $PSScriptRoot 'Protect-Archive.ps1')
Assert-Tools
$config = Get-StagingConfig
Assert-EncryptedVolume
$id = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$planned = Join-Path $Script:Root (Join-Path 'backups' $id)
if (Test-Path -LiteralPath $planned) { throw 'Backup directory already exists; refusing to overwrite.' }
$directory = New-PrivateDirectory $planned
$dump = Assert-PrivatePath (Join-Path $directory 'staging.db.dump')
$manifestPath = Assert-PrivatePath (Join-Path $directory 'manifest.json')
$psql = Join-Path $Script:PgBin 'psql.exe'
$pgDump = Join-Path $Script:PgBin 'pg_dump.exe'
$pgRestore = Join-Path $Script:PgBin 'pg_restore.exe'
try {
  $encryptedHash = Invoke-WithStagingEnvironment $config {
    $identity = & $psql -X -q -A -t -w -v ON_ERROR_STOP=1 -c "select current_setting('server_version_num'), current_database()"
    if ($LASTEXITCODE -ne 0 -or ($identity | Select-Object -First 1) -ne '170011|postgres') {
      throw 'Remote database identity or PostgreSQL version mismatch.'
    }
    $unknownSchemas = @(& $psql -X -q -A -t -w -v ON_ERROR_STOP=1 -c @'
select nspname from pg_catalog.pg_namespace
where nspname !~ '^pg_' and nspname not in
('information_schema','public','auth','storage','supabase_migrations','shop_qa_private',
 'extensions','graphql','graphql_public','realtime','vault','net','cron',
 'pgmq','pgsodium','pgsodium_masks','pgbouncer') order by 1
'@)
    if ($LASTEXITCODE -ne 0 -or $unknownSchemas.Count -ne 0) {
      throw 'Unexpected non-system schema; review the dump scope before exporting.'
    }
    $args = @('-Fc', '-w', '--serializable-deferrable', '--lock-wait-timeout=10s')
    foreach ($schema in $Script:Schemas) { $args += "--schema=$schema" }
    $args += @('--file', $dump)
    & $pgDump @args
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $dump)) { throw 'Staging dump failed.' }
    $toc = (& $pgRestore --list $dump) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Staging dump archive is unreadable.' }
    foreach ($entry in @('TABLE public shop_profiles ', 'TABLE public shop_sessions ',
      'TABLE auth users ', 'TABLE storage buckets ', 'TABLE storage objects ',
      'TABLE supabase_migrations schema_migrations ',
      'FUNCTION shop_qa_private active_read_scope(text) ',
      'FUNCTION shop_qa_private adishop_qa_confirm_checkout(text, uuid, uuid, text, text) ',
      'FUNCTION shop_qa_private invalidate_product_for_image() ',
      'FUNCTION shop_qa_private locked_actor(text) ',
      'FUNCTION shop_qa_private touch_image_revision() ',
      'TRIGGER public shop_product_images shop_qa_image_invalidate ',
      'TRIGGER public shop_product_images shop_qa_image_revision ')) {
      if (-not $toc.Contains($entry)) { throw "Required archive object missing: $entry" }
    }
    $counts = Get-TableCounts $psql
    $metadata = Get-SchemaMetadata $psql
    $archive = Protect-LocalFile $dump
    $manifest = [ordered]@{
      projectRef = $Script:ProjectRef; sourceHost = $Script:ExpectedHost;
      sourcePort = 5432; database = 'postgres'; postgres = '17.11';
      capturedUtc = (Get-Date).ToUniversalTime().ToString('o'); id = $id;
      schemas = $Script:Schemas; archive = $archive; tableCounts = $counts;
      schemaMetadata = $metadata; storageBytesIncluded = $false;
      note = 'DPAPI CurrentUser encryption; local same-user restore only. Storage bytes excluded.'
    }
    [IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    $archive.EncryptedSha256
  }
} finally {
  if (Test-Path -LiteralPath $dump) { Remove-Item -LiteralPath $dump -Force }
}
Write-Output "Encrypted staging database archive prepared: $directory"
Write-Output "Encrypted SHA-256: $encryptedHash"
Write-Output 'Storage object bytes were not exported.'
