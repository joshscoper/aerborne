[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$javaBin = Join-Path $project '.toolchains\jdk25\jdk-25.0.4.1+1\bin'
$sourceRuntime = Join-Path $project 'runtime\skyblock-test'
$pluginJar = Join-Path $project 'nerdsmp-paper\build\libs\nerdsmp-paper-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $pluginJar)) { throw 'Build the plugin first.' }
if ((Get-Content -LiteralPath (Join-Path $sourceRuntime 'eula.txt') -Raw) -notmatch '(?m)^eula=true\s*$') {
    throw 'The source test server must already have the owner-approved EULA acceptance.'
}
$probeRuntime = Join-Path $project ('runtime\metallurgy-probe-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$classes = Join-Path $probeRuntime 'fixture-classes'
$plugins = Join-Path $probeRuntime 'plugins'
$data = Join-Path $plugins 'NerdSMP'
New-Item -ItemType Directory -Path $classes,$data -Force | Out-Null
foreach ($name in 'paper-26.2-121.jar','eula.txt','libraries','cache','versions') {
    Copy-Item -LiteralPath (Join-Path $sourceRuntime $name) -Destination $probeRuntime -Recurse
}
Copy-Item -LiteralPath $pluginJar -Destination (Join-Path $plugins 'NerdSMP.jar')
$probeConfig = Get-Content -LiteralPath (Join-Path $project 'deploy\test\config.yml') -Raw
if ([regex]::Matches($probeConfig, '(?m)^anti-cheat:').Count -ne 1) { throw 'Expected one anti-cheat section in the test profile.' }
$probeConfig.Replace('anti-cheat:', "anti-cheat:`n  geolocation:`n    enabled: false`n    auto-download: false") | Set-Content -LiteralPath (Join-Path $data 'config.yml')
$security = Join-Path $data 'security'
New-Item -ItemType Directory -Path $security -Force | Out-Null
@"
schema: 1
enabled: false
bind: '127.0.0.1'
port: 9048
allowed-hosts: []
cors-origins: []
snapshot-seconds: 5
global-per-minute: 600
per-address-per-minute: 120
"@ | Set-Content -LiteralPath (Join-Path $security 'web-api.yml')
$properties = Get-Content -LiteralPath (Join-Path $project 'scripts\zone-smoke\server.properties') -Raw
$properties.Replace('server-port=25579','server-port=25598') | Set-Content -LiteralPath (Join-Path $probeRuntime 'server.properties')
$api = Get-ChildItem -LiteralPath 'C:\Users\Josh\.gradle\caches\modules-2\files-2.1\io.papermc.paper\paper-api\26.2.build.121-stable' -Filter '*.jar' -Recurse | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
$classPath = @($pluginJar,$api.FullName) + @(Get-ChildItem -LiteralPath (Join-Path $probeRuntime 'libraries') -Filter '*.jar' -Recurse | ForEach-Object FullName)
& (Join-Path $javaBin 'javac.exe') '-cp' ($classPath -join ';') '-d' $classes (Join-Path $PSScriptRoot 'MetallurgyProbe.java') (Join-Path $PSScriptRoot 'StatsAndExperienceProbe.java') (Join-Path $PSScriptRoot 'DropStackingProbe.java') (Join-Path $PSScriptRoot 'HostileSpawnProbe.java') (Join-Path $PSScriptRoot 'EncounterBehaviorProbe.java') (Join-Path $PSScriptRoot 'MetricsProbe.java') (Join-Path $PSScriptRoot 'MobNameplateProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed.' }
@"
name: MetallurgyProbe
version: '1'
main: net.nerdsmp.probe.MetallurgyProbe
api-version: '26.2'
depend: [NerdSMP]
"@ | Set-Content -LiteralPath (Join-Path $classes 'plugin.yml')
& (Join-Path $javaBin 'jar.exe') '--create' '--file' (Join-Path $plugins 'MetallurgyProbe.jar') '-C' $classes '.'
if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed.' }
Write-Host "Disposable metallurgy probe: $probeRuntime"
Push-Location $probeRuntime
try {
    & (Join-Path $javaBin 'java.exe') '-Xms512M' '-Xmx2G' '-Dterminal.jline=false' '-Dterminal.ansi=false' '-Dnerdsmp.gameplay=skyblock' '-Dnerdsmp.vanillaTextures=true' '-Dnerdsmp.metallurgyProbe=true' '-jar' 'paper-26.2-121.jar' '--nogui'
    if ($LASTEXITCODE -ne 0) { throw 'Probe server failed.' }
    $result = Get-Content -LiteralPath (Join-Path $probeRuntime 'metallurgy-probe-result.txt') -Raw
    if ($result -notmatch '^PASS ') { throw $result }
    Write-Host $result
} finally { Pop-Location }
