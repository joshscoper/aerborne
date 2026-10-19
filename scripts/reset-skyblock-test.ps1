[CmdletBinding()]
param([switch]$Apply)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$runtime = [IO.Path]::GetFullPath((Join-Path $project 'runtime\skyblock-test'))
$backupRoot = [IO.Path]::GetFullPath((Join-Path $project 'runtime\review-backups'))
if (-not $runtime.StartsWith($project + '\', [StringComparison]::OrdinalIgnoreCase) -or
    -not $backupRoot.StartsWith($project + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Reset targets escaped the workspace.' }
if (-not (Test-Path -LiteralPath $runtime -PathType Container)) { throw 'The isolated Skyblock runtime does not exist.' }
foreach ($ancestor in @($project, (Join-Path $project 'runtime'), $backupRoot)) {
    if ((Test-Path -LiteralPath $ancestor) -and ((Get-Item -LiteralPath $ancestor).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'Refusing a reset or backup through a reparse-point ancestor.'
    }
}
$links = @(Get-Item -LiteralPath $runtime; Get-ChildItem -LiteralPath $runtime -Recurse -Force) |
    Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }
if ($links) { throw 'Refusing a reset through reparse points or symbolic links.' }
$running = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
    $_.CommandLine -and ($_.CommandLine -match 'paper-26\.2-121\.jar' -or $_.CommandLine.Contains($runtime))
})
if ($running.Count -gt 0) { throw 'Stop Paper cleanly before backing up or resetting the test server.' }
if (Get-NetTCPConnection -State Listen -LocalPort 25566 -ErrorAction SilentlyContinue) { throw 'Port 25566 is in use. Refusing to reset a running server.' }
$properties = Get-Content -LiteralPath (Join-Path $runtime 'server.properties') -Raw
if ($properties -notmatch '(?m)^level-name=world\s*$' -or $properties -notmatch '(?m)^server-ip=127\.0\.0\.1\s*$') {
    throw 'This script only supports the localhost Skyblock runtime with level-name=world.'
}
$data = Join-Path $runtime 'plugins\NerdSMP'
$config = Get-Content -LiteralPath (Join-Path $data 'config.yml') -Raw
if ($config -notmatch '(?m)^storage:\s*\r?\n\s+mode: memory\s*$') { throw 'Only the local YAML test database may be reset by this script.' }
$manifestFiles = @(Get-ChildItem -LiteralPath (Join-Path $data 'skyblock') -Filter '*-world.yml' -File)
if ($manifestFiles.Count -ne 1) { throw 'Expected exactly one isolated island-world manifest.' }
$manifestText = Get-Content -LiteralPath $manifestFiles[0].FullName -Raw
if ($manifestText -notmatch '(?m)^world: (nerdsmp_skyblock_[a-z0-9_-]{1,64})\s*$') { throw 'Invalid island-world manifest.' }
$islandWorld = Join-Path $runtime ('world\dimensions\minecraft\' + $Matches[1])
$targets = @($data, $islandWorld, (Join-Path $runtime 'world\players'),
    (Join-Path $runtime 'usercache.json'), (Join-Path $runtime 'banned-players.json'), (Join-Path $runtime 'banned-ips.json'))
foreach ($target in $targets) {
    $absolute = [IO.Path]::GetFullPath($target)
    if (-not $absolute.StartsWith($runtime + '\', [StringComparison]::OrdinalIgnoreCase) -or $absolute -eq $runtime) { throw "Unsafe reset target: $absolute" }
}
Write-Host 'Reset scope: island dimension, all plugin player/progress/market state, vanilla player saves/stats/advancements, bans and user cache.'
Write-Host 'Preserved: hub/Nether/End terrain, config/content, manual NPC anchors, travel destinations, ops/whitelist, Nexo and EULA acceptance.'
$targets | ForEach-Object { Write-Host $_ }
if (-not $Apply) { Write-Host 'Preview only. Pass -Apply to create a verified full backup and perform this reset.'; exit 0 }

$backup = Join-Path $backupRoot ('skyblock-full-reset-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
if (Test-Path -LiteralPath $backup) { throw 'Backup destination already exists.' }
New-Item -ItemType Directory -Path $backup | Out-Null
$snapshot = Join-Path $backup 'snapshot'
Copy-Item -LiteralPath $runtime -Destination $snapshot -Recurse
$files = @(Get-ChildItem -LiteralPath $runtime -Recurse -File -Force)
$copied = @(Get-ChildItem -LiteralPath $snapshot -Recurse -File -Force)
if ($files.Count -ne $copied.Count) { throw 'Backup file count mismatch. Nothing was reset.' }
foreach ($file in $files) {
    $relative = $file.FullName.Substring($runtime.Length + 1)
    $copy = Join-Path $snapshot $relative
    if (-not (Test-Path -LiteralPath $copy -PathType Leaf) -or (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash) {
        throw "Backup verification failed for $relative. Nothing was reset."
    }
}
Write-Host "Verified $($files.Count) files. Backup: $backup"
# Moving exact validated targets keeps the reset recoverable even after verification.
$removed = Join-Path $backup 'removed'
New-Item -ItemType Directory -Path $removed | Out-Null
for ($index = 0; $index -lt $targets.Count; $index++) {
    if (Test-Path -LiteralPath $targets[$index]) { Move-Item -LiteralPath $targets[$index] -Destination (Join-Path $removed ([string]$index)) }
}
New-Item -ItemType Directory -Path $data | Out-Null
foreach ($name in @('config.yml', 'content', 'npc-instances.yml', 'travel.yml')) {
    $source = Join-Path (Join-Path $removed '0') $name
    if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination (Join-Path $data $name) -Recurse }
}
foreach ($name in @('data', 'stats', 'advancements')) { New-Item -ItemType Directory -Path (Join-Path $runtime ('world\players\' + $name)) -Force | Out-Null }
Write-Host "Reset complete. The old data remains in $backup. No SMP files were changed."
Write-Host 'Start run-skyblock-test-server.cmd to create the fresh island world. Do not copy old island/player state into this reset.'
