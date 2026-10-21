<#
.SYNOPSIS
Builds NerdSMP and runs the Aerborne local test server on port 25565, connected to the local test website.

.PARAMETER SkipBuild
Use the plugin jar that's already built instead of building it again.

.PARAMETER PrepareOnly
Set everything up but don't start the server.
#>
[CmdletBinding()]
param([switch]$SkipBuild, [switch]$PrepareOnly, [int]$MaxMemoryGb = 4)

. (Join-Path $PSScriptRoot 'common.ps1')

$running = @(Get-NetTCPConnection -LocalPort $MinecraftPort -State Listen -ErrorAction SilentlyContinue)
if ($running.Count -gt 0) {
    throw "The test server is already running (port $MinecraftPort). Type stop in its window first; never /reload NerdSMP."
}

$java = Find-Java25
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java)

if (-not $SkipBuild) {
    Write-Host 'Building NerdSMP...' -ForegroundColor Cyan
    & (Join-Path $ProjectRoot 'gradlew.bat') --no-daemon --offline -q :nerdsmp-paper:shadowJar
    if ($LASTEXITCODE -ne 0) { throw "The plugin build failed (exit code $LASTEXITCODE)." }
}

New-Item -ItemType Directory -Force -Path $ServerRoot, (Join-Path $ServerRoot 'plugins'), $SecurityRoot | Out-Null

# Paper: reuse a verified copy from the other test servers when there is one, otherwise download it.
$paperJar = Join-Path $ServerRoot $PaperJarName
if ((Test-Path -LiteralPath $paperJar) -and (Get-Sha256Hex $paperJar) -ne $PaperSha256) { Remove-Item -LiteralPath $paperJar }
if (-not (Test-Path -LiteralPath $paperJar)) {
    foreach ($cached in @('runtime\skyblock-test', 'runtime\paper-test')) {
        $candidate = Join-Path (Join-Path $ProjectRoot $cached) $PaperJarName
        if ((Test-Path -LiteralPath $candidate) -and (Get-Sha256Hex $candidate) -eq $PaperSha256) {
            Copy-Item -LiteralPath $candidate -Destination $paperJar
            break
        }
    }
}
if (-not (Test-Path -LiteralPath $paperJar)) {
    Write-Host 'Downloading Paper 26.2 build 121...' -ForegroundColor Cyan
    Invoke-WebRequest -Uri $PaperUrl -OutFile $paperJar
    if ((Get-Sha256Hex $paperJar) -ne $PaperSha256) { Remove-Item -LiteralPath $paperJar; throw 'The Paper download failed its checksum.' }
}

$plugin = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'nerdsmp-paper\build\libs') -Filter 'nerdsmp-paper-*.jar' |
    Where-Object { $_.Name -notlike '*-sources.jar' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $plugin) { throw 'No NerdSMP jar was built. Run without -SkipBuild.' }
Copy-Item -LiteralPath $plugin.FullName -Destination (Join-Path $ServerRoot 'plugins\NerdSMP.jar') -Force

# Server settings. Existing files are kept, so your own changes survive restarts.
Write-IfMissing (Join-Path $ServerRoot 'server.properties') @"
server-port=$MinecraftPort
motd=Aerborne local test server
online-mode=true
spawn-protection=0
view-distance=10
simulation-distance=8
max-players=20
"@

# Connect the plugin to the local website: game API, Arcade payouts, friends and messages, staff panel.
foreach ($name in 'web-api-token.txt', 'website-rewards-token.txt', 'website-social-token.txt', 'watchdog-website-token.txt') {
    Get-SharedToken $name | Out-Null
}
Write-IfMissing (Join-Path $SecurityRoot 'web-api.yml') @"
# Local test copy. The website at $WebsiteUrl reads leaderboards, profiles and /weblink codes from here.
schema: 1
enabled: true
bind: '127.0.0.1'
port: $ApiPort
allowed-hosts: []
cors-origins: []
snapshot-seconds: 5
global-per-minute: 600
per-address-per-minute: 300
"@
Write-IfMissing (Join-Path $SecurityRoot 'website-rewards.yml') @"
# Local test copy: collects Arcade payouts from the test website every 15 seconds.
schema: 1
enabled: true
url: '$WebsiteUrl/admin/api/arcade-payouts'
interval-seconds: 15
max-marks-per-payout: 50
max-marks-per-hour: 5000
"@
Write-IfMissing (Join-Path $SecurityRoot 'website-social.yml') @"
# Local test copy: friends, blocks and messages sync with the test website every 5 seconds.
schema: 1
enabled: true
url: '$WebsiteUrl/admin/api/social-sync'
interval-seconds: 5
"@
$panelTemplate = Join-Path $ProjectRoot 'nerdsmp-paper\src\main\resources\security\watchdog-panel.yml'
$panelFile = Join-Path $SecurityRoot 'watchdog-panel.yml'
if (-not (Test-Path -LiteralPath $panelFile)) {
    # The staff panel signs in website admins, so it needs no password of its own here.
    $panel = [System.IO.File]::ReadAllText($panelTemplate)
    $panel = $panel -replace '(?m)^enabled: false', 'enabled: true' -replace '(?m)^port: \d+', "port: $PanelPort"
    $panel = $panel -replace "(?ms)(website-sync:\s*\r?\n\s*enabled:) false", '$1 true'
    $panel = $panel -replace "https://aerborne\.net/admin/api/panel-accounts", "$WebsiteUrl/admin/api/panel-accounts"
    [System.IO.File]::WriteAllText($panelFile, $panel, (New-Object System.Text.UTF8Encoding($false)))
}

if ($PrepareOnly) {
    Write-Host "Prepared $ServerRoot. The server was not started." -ForegroundColor Green
    exit 0
}

$eula = Join-Path $ServerRoot 'eula.txt'
if (-not ((Test-Path -LiteralPath $eula) -and ((Get-Content -Raw -LiteralPath $eula) -match '(?m)^eula=true\s*$'))) {
    Write-Host ''
    Write-Host 'Minecraft requires acceptance of its EULA before the server can start.' -ForegroundColor Yellow
    Write-Host 'Review: https://aka.ms/MinecraftEULA'
    $answer = Read-Host 'Type I AGREE to record your acceptance for this local test server'
    if ($answer -cne 'I AGREE') { throw 'EULA acceptance was not recorded. The server was not started.' }
    Set-Content -LiteralPath $eula -Value 'eula=true' -Encoding ascii
}

Push-Location $ServerRoot
try {
    Write-Host "Starting the Aerborne test server on localhost:$MinecraftPort. Type stop here for a clean shutdown." -ForegroundColor Green
    & $java '-Xms2G' "-Xmx${MaxMemoryGb}G" '-XX:+UseG1GC' '-Dnerdsmp.gameplay=skyblock' '-Dnerdsmp.vanillaTextures=true' '-jar' $paperJar '--nogui'
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
