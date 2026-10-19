[CmdletBinding()]
param(
    [switch]$PrepareOnly,
    [switch]$RefreshConfig,
    [switch]$Skyblock,
    [switch]$LegacySmp,
    [switch]$Nexo,
    [ValidateRange(1, 64)]
    [int]$MinMemoryGb = 2,
    [ValidateRange(1, 64)]
    [int]$MaxMemoryGb = 4
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-Sha256Hex {
    param([Parameter(Mandatory)][string]$Path)

    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    $stream = [System.IO.File]::Open(
        $resolvedPath,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::Read
    )
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hashBytes = $sha256.ComputeHash($stream)
        return ([System.BitConverter]::ToString($hashBytes)).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
        $stream.Dispose()
    }
}

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ($Skyblock -and $LegacySmp) { throw 'Choose Skyblock or LegacySmp, not both.' }
if (-not $LegacySmp) { $Skyblock = $true }
$runtimeRoot = Join-Path $projectRoot $(if ($Skyblock) { 'runtime\skyblock-test' } else { 'runtime\paper-test' })
$pluginsRoot = Join-Path $runtimeRoot 'plugins'
$nerdSmpDataRoot = Join-Path $pluginsRoot 'NerdSMP'
$nexoDataRoot = Join-Path $pluginsRoot 'Nexo'
$localPluginsRoot = Join-Path $projectRoot 'local-plugins'
$paperJar = Join-Path $runtimeRoot 'paper-26.2-121.jar'
$paperUrl = 'https://fill-data.papermc.io/v1/objects/0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54/paper-26.2-121.jar'
$paperSha256 = '0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54'

if ($MaxMemoryGb -lt $MinMemoryGb) {
    throw 'MaxMemoryGb must be greater than or equal to MinMemoryGb.'
}

function Get-JavaMajorVersion {
    param([Parameter(Mandatory)][string]$Executable)

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Executable
    $startInfo.Arguments = '-version'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            return 0
        }
        $standardOutput = $process.StandardOutput.ReadToEnd()
        $standardError = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        $exitCode = $process.ExitCode
    } catch {
        return 0
    } finally {
        $process.Dispose()
    }

    $versionOutput = $standardOutput + $standardError
    if ($exitCode -ne 0 -or $versionOutput -notmatch 'version "(?<major>[0-9]+)') {
        return 0
    }
    return [int]$Matches.major
}

function Find-Java25 {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) {
        $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe'))
    }

    $bundledRoot = Join-Path $projectRoot '.toolchains\jdk25'
    if (Test-Path -LiteralPath $bundledRoot) {
        Get-ChildItem -LiteralPath $bundledRoot -Directory | ForEach-Object {
            $candidates.Add((Join-Path $_.FullName 'bin\java.exe'))
        }
    }

    $pathJava = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($pathJava) {
        $candidates.Add($pathJava.Source)
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if ((Test-Path -LiteralPath $candidate) -and (Get-JavaMajorVersion -Executable $candidate) -eq 25) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw 'JDK 25 was not found. Install JDK 25 or set JAVA_HOME, then run this script again.'
}

function Copy-InitialFile {
    param(
        [Parameter(Mandatory)][string]$Source,
        [Parameter(Mandatory)][string]$Destination
    )

    if ($RefreshConfig -or -not (Test-Path -LiteralPath $Destination)) {
        Copy-Item -LiteralPath $Source -Destination $Destination -Force
    }
}

function Copy-VersionedContentFile {
    param(
        [Parameter(Mandatory)][string]$Source,
        [Parameter(Mandatory)][string]$Destination
    )

    if ($RefreshConfig -or -not (Test-Path -LiteralPath $Destination)) {
        Copy-Item -LiteralPath $Source -Destination $Destination -Force
        return
    }

    $sourceHeader = Select-String -LiteralPath $Source -Pattern '^schema(?:-version)?:\s*(\d+)\s*$' |
        Select-Object -First 1
    $destinationHeader = Select-String -LiteralPath $Destination -Pattern '^schema(?:-version)?:\s*(\d+)\s*$' |
        Select-Object -First 1
    $sourceVersion = if ($null -eq $sourceHeader) { 0 } else { [int]$sourceHeader.Matches[0].Groups[1].Value }
    $destinationVersion = if ($null -eq $destinationHeader) { 0 } else { [int]$destinationHeader.Matches[0].Groups[1].Value }
    if ($sourceVersion -le $destinationVersion) {
        return
    }

    $backup = "$Destination.schema-$destinationVersion.bak"
    if (-not (Test-Path -LiteralPath $backup)) {
        Copy-Item -LiteralPath $Destination -Destination $backup
    }
    Copy-Item -LiteralPath $Source -Destination $Destination -Force
    Write-Host "Updated $(Split-Path -Leaf $Destination) from schema $destinationVersion to $sourceVersion."
}

function Assert-TestServerStopped {
    $expectedPaperJar = [System.IO.Path]::GetFullPath($paperJar)
    $paperJarName = [System.IO.Path]::GetFileName($paperJar)
    $running = @(
        Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
            Where-Object {
                $_.CommandLine -and (
                    $_.CommandLine.IndexOf($expectedPaperJar, [System.StringComparison]::OrdinalIgnoreCase) -ge 0 -or
                    $_.CommandLine.IndexOf($paperJarName, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
                )
            }
    )
    $testPort = if ($Skyblock) { 25566 } else { 25565 }
    $listeners = @(Get-NetTCPConnection -LocalPort $testPort -State Listen -ErrorAction SilentlyContinue)
    if ($running.Count -eq 0 -and $listeners.Count -eq 0) {
        return
    }
    $processIds = @(
        $running | ForEach-Object { $_.ProcessId }
        $listeners | ForEach-Object { $_.OwningProcess }
    ) | Sort-Object -Unique
    $processIds = $processIds -join ', '
    throw "The NerdSMP test server is already running (PID $processIds). Type stop in its console and wait for shutdown before building or staging a plugin jar. Never hot-replace or /reload NerdSMP."
}

Assert-TestServerStopped
$javaExecutable = Find-Java25
$javaHome = Split-Path -Parent (Split-Path -Parent $javaExecutable)
$env:JAVA_HOME = $javaHome
$env:PATH = (Join-Path $javaHome 'bin') + [System.IO.Path]::PathSeparator + $env:PATH

Write-Host 'Building NerdSMP with Java 25...'
$gradleWrapper = Join-Path $projectRoot 'gradlew.bat'
& $gradleWrapper --no-daemon :nerdsmp-paper:shadowJar
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE."
}

New-Item -ItemType Directory -Force -Path $runtimeRoot, $pluginsRoot, $nerdSmpDataRoot | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $runtimeRoot 'config'), (Join-Path $nerdSmpDataRoot 'content') | Out-Null

if (Test-Path -LiteralPath $paperJar) {
    $existingHash = Get-Sha256Hex -Path $paperJar
    if ($existingHash -ne $paperSha256) {
        Write-Warning 'The cached Paper jar failed checksum verification and will be downloaded again.'
        Remove-Item -LiteralPath $paperJar
    }
}

if (-not (Test-Path -LiteralPath $paperJar)) {
    $sharedPaperJar = Join-Path $projectRoot 'runtime\paper-test\paper-26.2-121.jar'
    if ($Skyblock -and (Test-Path -LiteralPath $sharedPaperJar) -and (Get-Sha256Hex -Path $sharedPaperJar) -eq $paperSha256) {
        Copy-Item -LiteralPath $sharedPaperJar -Destination $paperJar
    }
}
if (-not (Test-Path -LiteralPath $paperJar)) {
    Write-Host 'Downloading the pinned stable Paper 26.2 build 121...'
    Invoke-WebRequest -Uri $paperUrl -OutFile $paperJar
}

$downloadedHash = Get-Sha256Hex -Path $paperJar
if ($downloadedHash -ne $paperSha256) {
    Remove-Item -LiteralPath $paperJar
    throw 'Paper download failed SHA-256 verification.'
}

$pluginArtifact = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'nerdsmp-paper\build\libs') -File -Filter 'nerdsmp-paper-*.jar' |
    Where-Object { $_.Name -notlike '*-sources.jar' } |
    Select-Object -First 1
if ($null -eq $pluginArtifact) {
    throw 'The NerdSMP Paper artifact was not produced by the Gradle build.'
}
Assert-TestServerStopped
Copy-Item -LiteralPath $pluginArtifact.FullName -Destination (Join-Path $pluginsRoot 'NerdSMP.jar') -Force

$nexoInstalled = $false
if ($Nexo) {
$nexoSource = Get-ChildItem -LiteralPath $localPluginsRoot -File -Filter 'Nexo*.jar' -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -ne $nexoSource) {
    Get-ChildItem -LiteralPath $pluginsRoot -File -Filter 'Nexo*.jar' -ErrorAction SilentlyContinue |
        Remove-Item -Force
    Copy-Item -LiteralPath $nexoSource.FullName -Destination (Join-Path $pluginsRoot 'Nexo.jar') -Force
}
$nexoInstalled = $null -ne (Get-ChildItem -LiteralPath $pluginsRoot -File -Filter 'Nexo*.jar' -ErrorAction SilentlyContinue |
    Select-Object -First 1)

if ($nexoInstalled) {
    New-Item -ItemType Directory -Force -Path $nexoDataRoot | Out-Null
    Copy-InitialFile `
        -Source (Join-Path $projectRoot 'deploy\nexo\settings.local.fragment.yml') `
        -Destination (Join-Path $nexoDataRoot 'settings.yml')
}

$nexoExternalPackRoot = Join-Path $nexoDataRoot 'pack\external_packs'
New-Item -ItemType Directory -Force -Path $nexoExternalPackRoot | Out-Null
$nexoSourcePack = Join-Path $projectRoot 'assets\nexo-pack'
$nexoPackTarget = Join-Path $nexoExternalPackRoot 'NerdSMP'
$legacyPackArchive = Join-Path $nexoExternalPackRoot 'NerdSMP.zip'
if (Test-Path -LiteralPath $nexoPackTarget) {
    $resolvedPackTarget = (Resolve-Path -LiteralPath $nexoPackTarget).Path
    $expectedPackTarget = [IO.Path]::GetFullPath((Join-Path $runtimeRoot 'plugins\Nexo\pack\external_packs\NerdSMP'))
    if ($resolvedPackTarget -ne $expectedPackTarget) { throw 'Refusing to replace an unexpected Nexo pack directory.' }
    Remove-Item -LiteralPath $nexoPackTarget -Recurse -Force
}
if (Test-Path -LiteralPath $legacyPackArchive) {
    Remove-Item -LiteralPath $legacyPackArchive -Force
}
Copy-Item -LiteralPath $nexoSourcePack -Destination $nexoPackTarget -Recurse -Force
} else {
    # Preserve an existing optional plugin; a parked jar can be restored with -Nexo.
    $optionalArchive = Join-Path $runtimeRoot 'optional-plugins'
    $installedNexo = @(Get-ChildItem -LiteralPath $pluginsRoot -File -Filter 'Nexo*.jar' -ErrorAction SilentlyContinue)
    if ($installedNexo.Count -gt 0) {
        New-Item -ItemType Directory -Force -Path $optionalArchive | Out-Null
        foreach ($optionalJar in $installedNexo) {
            $archiveName = $optionalJar.Name + '.' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.disabled'
            Move-Item -LiteralPath $optionalJar.FullName -Destination (Join-Path $optionalArchive $archiveName)
        }
        Write-Host 'Nexo jars parked in optional-plugins; data and pack assets are preserved.'
    }
}

$propertiesSource = if ($Skyblock) { 'deploy\skyblock\server.properties' } else { 'deploy\test\server.properties' }
Copy-InitialFile -Source (Join-Path $projectRoot $propertiesSource) -Destination (Join-Path $runtimeRoot 'server.properties')
Copy-InitialFile -Source (Join-Path $projectRoot 'deploy\test\paper-global.yml') -Destination (Join-Path $runtimeRoot 'config\paper-global.yml')
Copy-InitialFile -Source (Join-Path $projectRoot 'deploy\test\config.yml') -Destination (Join-Path $nerdSmpDataRoot 'config.yml')
Copy-InitialFile -Source (Join-Path $projectRoot 'nerdsmp-paper\src\main\resources\content\relics.yml') -Destination (Join-Path $nerdSmpDataRoot 'content\relics.yml')
Copy-InitialFile -Source (Join-Path $projectRoot 'nerdsmp-paper\src\main\resources\content\mobs.yml') -Destination (Join-Path $nerdSmpDataRoot 'content\mobs.yml')
Copy-InitialFile -Source (Join-Path $projectRoot 'nerdsmp-paper\src\main\resources\content\orders.yml') -Destination (Join-Path $nerdSmpDataRoot 'content\orders.yml')
Copy-InitialFile -Source (Join-Path $projectRoot 'nerdsmp-paper\src\main\resources\content\recipes.yml') -Destination (Join-Path $nerdSmpDataRoot 'content\recipes.yml')
Copy-InitialFile -Source (Join-Path $projectRoot 'nerdsmp-paper\src\main\resources\content\daily-quests.yml') -Destination (Join-Path $nerdSmpDataRoot 'content\daily-quests.yml')
Copy-InitialFile -Source (Join-Path $projectRoot 'nerdsmp-paper\src\main\resources\content\skyblock-daily-quests.yml') -Destination (Join-Path $nerdSmpDataRoot 'content\skyblock-daily-quests.yml')
Copy-VersionedContentFile -Source (Join-Path $projectRoot 'nerdsmp-paper\src\main\resources\content\npcs.yml') -Destination (Join-Path $nerdSmpDataRoot 'content\npcs.yml')
Copy-InitialFile -Source (Join-Path $projectRoot 'nerdsmp-paper\src\main\resources\content\reputation-contracts.yml') -Destination (Join-Path $nerdSmpDataRoot 'content\reputation-contracts.yml')
Copy-VersionedContentFile -Source (Join-Path $projectRoot 'nerdsmp-paper\src\main\resources\content\drop-tables.yml') -Destination (Join-Path $nerdSmpDataRoot 'content\drop-tables.yml')
Copy-VersionedContentFile -Source (Join-Path $projectRoot 'nerdsmp-paper\src\main\resources\content\achievements.yml') -Destination (Join-Path $nerdSmpDataRoot 'content\achievements.yml')

Write-Host "Test server prepared at $runtimeRoot"
Write-Host $(if ($Skyblock) { 'Connect with a paid Java account at 127.0.0.1:25566. SMP data remains in runtime\paper-test.' } else { 'Connect with a paid Java account at 127.0.0.1:25565.' })
if ($nexoInstalled) {
    Write-Host 'Nexo detected; the NerdSMP source pack was installed into Nexo external_packs.' -ForegroundColor Green
} elseif ($Nexo) {
    Write-Warning 'Optional Nexo mode needs a licensed Nexo jar in local-plugins.'
} else {
    Write-Host 'Vanilla textures: no resource pack or client mods required.' -ForegroundColor Green
}

if ($PrepareOnly) {
    Write-Host 'Preparation complete; the server was not started.'
    exit 0
}
if ($Nexo -and -not $nexoInstalled) {
    throw 'Nexo is required for resource-pack management. Add its licensed jar to local-plugins and rerun.'
}

$eulaFile = Join-Path $runtimeRoot 'eula.txt'
$eulaAccepted = (Test-Path -LiteralPath $eulaFile) -and ((Get-Content -Raw -LiteralPath $eulaFile) -match '(?m)^eula=true\s*$')
if (-not $eulaAccepted) {
    Write-Host ''
    Write-Host 'Minecraft requires acceptance of its EULA before the server can start.' -ForegroundColor Yellow
    Write-Host 'Review: https://aka.ms/MinecraftEULA'
    $answer = Read-Host 'Type I AGREE to record your acceptance for this local test server'
    if ($answer -cne 'I AGREE') {
        throw 'EULA acceptance was not recorded. The server was not started.'
    }
    Set-Content -LiteralPath $eulaFile -Value 'eula=true' -Encoding ascii
}

Push-Location $runtimeRoot
try {
    Write-Host 'Starting NerdSMP Paper test server. Type stop in this window for a clean shutdown.' -ForegroundColor Green
    $gameplayMode = if ($Skyblock) { 'skyblock' } else { 'smp' }
    $vanillaTextures = if ($Nexo) { 'false' } else { 'true' }
    & $javaExecutable "-Xms${MinMemoryGb}G" "-Xmx${MaxMemoryGb}G" '-XX:+UseG1GC' "-Dnerdsmp.gameplay=$gameplayMode" "-Dnerdsmp.vanillaTextures=$vanillaTextures" '-jar' $paperJar '--nogui'
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
