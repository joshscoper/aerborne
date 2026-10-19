[CmdletBinding()]
param([switch]$ReleaseProfile)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$javaRoot = Join-Path $project '.toolchains\jdk25\jdk-25.0.4.1+1\bin'
$sourceRuntime = Join-Path $project 'runtime\skyblock-test'
$fixture = Join-Path $project 'scripts\zone-smoke'
$pluginJar = Join-Path $project 'nerdsmp-paper\build\libs\nerdsmp-paper-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $pluginJar)) { throw 'Build and test NerdSMP first.' }
if ((Get-Content -LiteralPath (Join-Path $sourceRuntime 'eula.txt') -Raw) -notmatch '(?m)^eula=true\s*$') {
    throw 'The source test server must already have the owner-approved EULA acceptance.'
}
$runtime = Join-Path $project ('runtime\zone-smoke-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $runtime | Out-Null
$classes = Join-Path $runtime 'fixture-classes'
$plugins = Join-Path $runtime 'plugins'
$data = Join-Path $plugins 'NerdSMP'
New-Item -ItemType Directory -Path $classes,$data -Force | Out-Null
foreach ($name in @('paper-26.2-121.jar','eula.txt','libraries','cache','versions')) {
    Copy-Item -LiteralPath (Join-Path $sourceRuntime $name) -Destination $runtime -Recurse
}
Copy-Item -LiteralPath $pluginJar -Destination (Join-Path $plugins 'NerdSMP.jar')
$fixtureConfig = if ($ReleaseProfile) { 'deploy\release\config.yml' } else { 'deploy\test\config.yml' }
Copy-Item -LiteralPath (Join-Path $project $fixtureConfig) -Destination (Join-Path $data 'config.yml')
$apiSecurity = Join-Path $data 'security'
New-Item -ItemType Directory -Path $apiSecurity -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $fixture 'web-api.yml') -Destination $apiSecurity
Copy-Item -LiteralPath (Join-Path $fixture 'web-api-token.txt') -Destination $apiSecurity
Copy-Item -LiteralPath (Join-Path $fixture 'server.properties') -Destination $runtime
$api = Get-ChildItem -LiteralPath 'C:\Users\Josh\.gradle\caches\modules-2\files-2.1\io.papermc.paper\paper-api\26.2.build.121-stable' -Filter '*.jar' -Recurse | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
$classPath = @($pluginJar,$api.FullName) + @(Get-ChildItem -LiteralPath (Join-Path $runtime 'libraries') -Filter '*.jar' -Recurse | ForEach-Object FullName)
$fixtureSources = @(Get-ChildItem -LiteralPath $fixture -Filter '*.java' -File | ForEach-Object FullName)
& (Join-Path $javaRoot 'javac.exe') '-cp' ($classPath -join ';') '-d' $classes @fixtureSources
if ($LASTEXITCODE -ne 0) { throw 'Smoke helper compilation failed.' }
Copy-Item -LiteralPath (Join-Path $fixture 'plugin.yml') -Destination $classes
& (Join-Path $javaRoot 'jar.exe') '--create' '--file' (Join-Path $plugins 'ZoneSmoke.jar') '-C' $classes '.'
if ($LASTEXITCODE -ne 0) { throw 'Smoke helper packaging failed.' }
Write-Host "Disposable smoke server: $runtime"
Push-Location $runtime
try {
    & (Join-Path $javaRoot 'java.exe') '-Xms1G' '-Xmx3G' '-Dterminal.jline=false' '-Dterminal.ansi=false' '-Dnerdsmp.gameplay=skyblock' '-Dnerdsmp.vanillaTextures=true' '-Dnerdsmp.zoneSmoke=true' '-jar' 'paper-26.2-121.jar' '--nogui'
    if ($LASTEXITCODE -ne 0) { throw 'Smoke server exited unsuccessfully.' }
    $result = Get-Content -LiteralPath (Join-Path $runtime 'zone-smoke-result.txt') -Raw
    if ($result.Trim() -ne 'PASS') { throw $result }
    Write-Host 'Disposable Paper smoke passed.'
} finally { Pop-Location }
