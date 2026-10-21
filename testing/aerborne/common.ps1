# Shared settings for the Aerborne local test environment. Dot-source this from the other scripts.
# The test server and website use their own ports and data, so they never touch the live site or the
# other test servers under runtime/.

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$TestRoot = $PSScriptRoot
$ProjectRoot = (Resolve-Path (Join-Path $TestRoot '..\..')).Path
$ServerRoot = Join-Path $TestRoot 'server'
$WebsiteData = Join-Path $TestRoot 'website-data'
$WebsiteRoot = Join-Path $ProjectRoot 'Website'
$NerdSmpData = Join-Path $ServerRoot 'plugins\NerdSMP'
$SecurityRoot = Join-Path $NerdSmpData 'security'

$MinecraftPort = 25565
$ApiPort = 9030
$PanelPort = 9031
$WebsitePort = 5070  # Not 5060: browsers block that port (it belongs to internet phone calls).
$WebsiteUrl = "http://127.0.0.1:$WebsitePort"

$PaperJarName = 'paper-26.2-121.jar'
$PaperUrl = 'https://fill-data.papermc.io/v1/objects/0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54/paper-26.2-121.jar'
$PaperSha256 = '0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54'

function Get-Sha256Hex([string]$Path) {
    $stream = [System.IO.File]::OpenRead((Resolve-Path -LiteralPath $Path).Path)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return ([System.BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose(); $stream.Dispose() }
}

function New-HexToken {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([System.BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}

# The plugin and the website share these tokens. They're created once and reused, exactly as the plugin
# would create them on first start, so the website can be configured before the server ever runs.
function Get-SharedToken([string]$FileName) {
    New-Item -ItemType Directory -Force -Path $SecurityRoot | Out-Null
    $path = Join-Path $SecurityRoot $FileName
    if (-not (Test-Path -LiteralPath $path)) {
        [System.IO.File]::WriteAllText($path, (New-HexToken) + "`n", [System.Text.Encoding]::ASCII)
    }
    return ([System.IO.File]::ReadAllText($path)).Trim()
}

function Write-IfMissing([string]$Path, [string]$Content) {
    if (Test-Path -LiteralPath $Path) { return }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    [System.IO.File]::WriteAllText($Path, $Content.Replace("`r`n", "`n"), (New-Object System.Text.UTF8Encoding($false)))
}

function Find-Java25 {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe')) }
    $bundled = Join-Path $ProjectRoot '.toolchains\jdk25'
    if (Test-Path -LiteralPath $bundled) {
        Get-ChildItem -LiteralPath $bundled -Directory | ForEach-Object { $candidates.Add((Join-Path $_.FullName 'bin\java.exe')) }
    }
    foreach ($candidate in $candidates) {
        if (-not (Test-Path -LiteralPath $candidate)) { continue }
        # java -version writes to stderr; let cmd merge it so PowerShell doesn't treat it as an error.
        $version = (& cmd.exe /c "`"$candidate`" -version 2>&1") | Out-String
        if ($version -match 'version "25') { return (Resolve-Path -LiteralPath $candidate).Path }
    }
    throw 'JDK 25 was not found. Install JDK 25 or set JAVA_HOME, then run this script again.'
}
