[CmdletBinding()]
param(
    [string]$ServerRoot = (Join-Path (Split-Path -Parent $PSScriptRoot) 'runtime\skyblock-test'),
    [string]$Username = 'j0sh',
    [int]$Port = 9029,
    [string]$Bind = '127.0.0.1',
    [string[]]$AllowedHosts = @(),
    [switch]$Rotate
)
$ErrorActionPreference = 'Stop'
if ($Username -notmatch '^[A-Za-z0-9_-]{1,32}$' -or $Port -lt 1024 -or $Port -gt 65535) { throw 'Invalid username or port.' }
if ($Bind -notmatch '^(?:0|[1-9][0-9]{0,2})(?:\.(?:0|[1-9][0-9]{0,2})){3}$' -or @($Bind.Split('.') | Where-Object { [int]$_ -gt 255 }).Count -ne 0 -or ([int]$Bind.Split('.')[0] -ge 224 -and [int]$Bind.Split('.')[0] -le 239)) { throw 'Bind must be a literal, non-multicast IPv4 address.' }
if ($AllowedHosts.Count -gt 16) { throw 'At most 16 allowed hosts may be configured.' }
foreach ($authority in $AllowedHosts) {
    if ($null -eq $authority -or $authority.Length -gt 259 -or $authority -notmatch '^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?::[0-9]{1,5})?$') { throw 'Use exact hostnames or IPv4 addresses with optional ports, not URLs or wildcards.' }
    if ($authority.Contains(':') -and ([int]$authority.Split(':')[1] -lt 1 -or [int]$authority.Split(':')[1] -gt 65535)) { throw 'Invalid allowed-host port.' }
}
if (-not (Test-Path -LiteralPath $ServerRoot -PathType Container)) { throw 'The selected server directory does not exist.' }
$target = Join-Path $ServerRoot 'plugins\NerdSMP\security\watchdog-panel.yml'
if ((Test-Path -LiteralPath $target) -and -not $Rotate) { throw 'Credentials already exist. Use -Rotate to replace them intentionally.' }
$credential = Get-Credential -UserName $Username -Message 'Choose the Watchdog panel password. It will be stored only as a salted hash.'
if ($null -eq $credential) { throw 'Cancelled. No settings changed.' }
$secret = $credential.GetNetworkCredential().Password
if ($secret.Length -lt 8 -or $secret.Length -gt 128) { throw 'Use a password of 8-128 characters.' }
$salt = New-Object byte[] 32
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($salt) } finally { $random.Dispose() }
$derive = [System.Security.Cryptography.Rfc2898DeriveBytes]::new($secret, $salt, 600000, [System.Security.Cryptography.HashAlgorithmName]::SHA256)
try { $hash = [Convert]::ToBase64String($derive.GetBytes(32)) } finally { $derive.Dispose(); $secret = $null }
$text = "schema: 1`nenabled: true`nbind: '$Bind'`nport: $Port`nusername: $Username`nsalt: '$([Convert]::ToBase64String($salt))'`nhash: '$hash'`niterations: 600000`n"
if ($AllowedHosts.Count -gt 0) { $text += "allowed-hosts:`n"; foreach ($authority in $AllowedHosts) { $text += "  - '$($authority.ToLowerInvariant())'`n" } }
$parent = Split-Path -Parent $target
New-Item -ItemType Directory -Path $parent -Force | Out-Null
$temporary = $target + '.tmp'
[System.IO.File]::WriteAllText($temporary, $text, [System.Text.UTF8Encoding]::new($false))
if (Test-Path -LiteralPath $target) { [System.IO.File]::Replace($temporary, $target, $target + '.previous', $true) }
else { [System.IO.File]::Move($temporary, $target) }
Write-Host "Panel settings saved. Restart the server to bind ${Bind}:$Port."
if ($Bind -notmatch '^127\.') { Write-Warning 'HTTP does not encrypt login credentials. Use a trusted network, VPN or HTTPS reverse proxy. Firewall and router settings have not been changed.' }
