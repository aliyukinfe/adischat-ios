param([Parameter(Mandatory = $true)][string]$ManifestPath)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
Add-Type -AssemblyName System.Security
$path = Assert-PrivatePath $ManifestPath
$manifest = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
if ($manifest.projectRef -cne $Script:ProjectRef -or $manifest.objects -isnot [array]) {
  throw 'Storage manifest is missing the approved project identity or object list.'
}
$buckets = @('shop-product-images', 'shop-store-assets')
$seen = [Collections.Generic.HashSet[string]]::new()
foreach ($item in $manifest.objects) {
  if ($buckets -cnotcontains $item.bucket -or [string]::IsNullOrWhiteSpace($item.path) -or
      $item.path.StartsWith('/') -or $item.path.Contains('\') -or
      @($item.path.Split('/')) -contains '..' -or
      $item.encryptedFile -notmatch '^[A-Za-z0-9_-]+\.dpapi$' -or
      -not $seen.Add("$($item.bucket)/$($item.path)")) { throw 'Invalid or duplicate Storage manifest entry.' }
  $encrypted = Assert-PrivatePath (Join-Path (Split-Path $path -Parent) $item.encryptedFile)
  if ((Get-FileHash -Algorithm SHA256 -LiteralPath $encrypted).Hash -cne $item.encryptedSha256) {
    throw 'Storage encrypted-object checksum mismatch.'
  }
  $plain = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($encrypted), $null,
    [Security.Cryptography.DataProtectionScope]::CurrentUser)
  try {
    $hash = [Security.Cryptography.SHA256]::Create()
    $actual = ([BitConverter]::ToString($hash.ComputeHash($plain))).Replace('-', '')
    if ($actual -cne $item.sha256) { throw 'Storage object plaintext checksum mismatch.' }
  } finally { [Array]::Clear($plain, 0, $plain.Length) }
}
if (@($manifest.objects).Count -ne 3) { throw 'Storage object count differs from the preflight inventory; refresh inventory before claiming coverage.' }
Write-Output "Storage manifest checksums verified for $(@($manifest.objects).Count) encrypted objects; hosted parity still requires comparison."
