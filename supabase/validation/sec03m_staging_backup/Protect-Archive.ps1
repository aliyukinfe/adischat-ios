. (Join-Path $PSScriptRoot 'Common.ps1')
Add-Type -AssemblyName System.Security

function Protect-LocalFile([string]$Source) {
  $sourcePath = Assert-PrivatePath $Source
  $target = Assert-PrivatePath ($sourcePath + '.dpapi')
  if (Test-Path -LiteralPath $target) { throw 'Encrypted target already exists.' }
  $plain = [IO.File]::ReadAllBytes($sourcePath)
  try {
    $hash = [Security.Cryptography.SHA256]::Create()
    $plainHash = ([BitConverter]::ToString($hash.ComputeHash($plain))).Replace('-', '')
    $cipher = [Security.Cryptography.ProtectedData]::Protect($plain, $null,
      [Security.Cryptography.DataProtectionScope]::CurrentUser)
    [IO.File]::WriteAllBytes($target, $cipher)
    $verified = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($target), $null,
      [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $verifiedHash = ([BitConverter]::ToString($hash.ComputeHash($verified))).Replace('-', '')
    [Array]::Clear($verified, 0, $verified.Length)
    if ($verifiedHash -cne $plainHash) { throw 'Encryption round-trip checksum mismatch.' }
    return @{ PlainSha256 = $plainHash; EncryptedSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash;
      EncryptedFile = [IO.Path]::GetFileName($target) }
  } catch {
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Force }
    throw
  } finally {
    [Array]::Clear($plain, 0, $plain.Length)
  }
}

function Unprotect-LocalFile([string]$Encrypted, [string]$Destination, [string]$ExpectedPlainSha256) {
  $encryptedPath = Assert-PrivatePath $Encrypted
  $destinationPath = Assert-PrivatePath $Destination
  if (Test-Path -LiteralPath $destinationPath) { throw 'Decrypted target already exists.' }
  $plain = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($encryptedPath), $null,
    [Security.Cryptography.DataProtectionScope]::CurrentUser)
  try {
    $hash = [Security.Cryptography.SHA256]::Create()
    $actual = ([BitConverter]::ToString($hash.ComputeHash($plain))).Replace('-', '')
    if ($actual -cne $ExpectedPlainSha256) { throw 'Plaintext checksum mismatch.' }
    [IO.File]::WriteAllBytes($destinationPath, $plain)
  } finally { [Array]::Clear($plain, 0, $plain.Length) }
}
