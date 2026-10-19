[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$probeProject = Split-Path -Parent $PSScriptRoot
$probeJava = Join-Path $probeProject '.toolchains\jdk25\jdk-25.0.4.1+1\bin'
$probeJar = Join-Path $probeProject 'nerdsmp-paper\build\libs\nerdsmp-paper-0.1.0-SNAPSHOT.jar'
$probeLibraries = Join-Path $probeProject 'runtime\skyblock-test\libraries'
if (-not (Test-Path -LiteralPath $probeJar)) { throw 'Build the plugin before running this diagnostic.' }
$probeApi = Get-ChildItem -LiteralPath 'C:\Users\Josh\.gradle\caches\modules-2\files-2.1\io.papermc.paper\paper-api\26.2.build.121-stable' -Filter '*.jar' -Recurse | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
$probeClasspath = @($probeJar, $probeApi.FullName) + @(Get-ChildItem -LiteralPath $probeLibraries -Filter '*.jar' -Recurse | ForEach-Object FullName)
$probeClasses = Join-Path $probeProject ('build\persistence-probe-classes-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $probeClasses | Out-Null
& (Join-Path $probeJava 'javac.exe') '-cp' ($probeClasspath -join ';') '-d' $probeClasses (Join-Path $PSScriptRoot 'verification\PersistenceFailureProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Persistence diagnostic compilation failed.' }
& (Join-Path $probeJava 'java.exe') '-cp' ((@($probeClasses) + $probeClasspath) -join ';') 'net.nerdsmp.verification.PersistenceFailureProbe' (Join-Path $probeProject 'build')
$probeResult = $LASTEXITCODE
Write-Host 'This diagnostic uses only new temporary profiles. Exit 1 means an invariant failed; inspect the output.'
exit $probeResult
