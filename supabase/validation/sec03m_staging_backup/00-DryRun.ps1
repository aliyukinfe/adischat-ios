param([switch]$CheckSession)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
Assert-Tools
foreach ($name in @('Common.ps1','Protect-Archive.ps1','01-ExportDatabase.ps1','03-RestoreLocal.ps1','04-VerifyStorageManifest.ps1')) {
  $path = Join-Path $PSScriptRoot $name
  if (-not (Test-Path -LiteralPath $path)) { throw "Package file missing: $name" }
  $tokens = $null; $errors = $null
  [void][System.Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
  if (@($errors).Count -gt 0) { throw "PowerShell parse error: $name" }
}
if ((Get-PSDrive C).Free -lt 2GB) { throw 'At least 2 GB of local free space is required.' }
[void](Assert-PrivatePath (Join-Path $Script:Root 'backups'))
[void](Assert-PrivatePath (Join-Path $Script:Root 'restore'))
if ($CheckSession) {
  $config = Get-StagingConfig
  if ($config.Host -cne $Script:ExpectedHost) { throw 'Wrong target.' }
  Assert-EncryptedVolume
  Write-Output 'Dedicated session, CA fingerprint and encrypted-volume checks: PASS (no connection made).'
} else {
  Write-Output 'Static package, PostgreSQL tools, private paths and disk: PASS (no connection made).'
}
