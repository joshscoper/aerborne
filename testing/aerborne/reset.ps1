<#
.SYNOPSIS
Wipes the test world, player data and test website so the next start is a fresh install.
Keeps the Paper jar, the EULA answer, server.properties and the shared tokens and settings.
Stop the server and website first.
#>
[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param()

. (Join-Path $PSScriptRoot 'common.ps1')

if (@(Get-NetTCPConnection -LocalPort $MinecraftPort, $WebsitePort -State Listen -ErrorAction SilentlyContinue).Count -gt 0) {
    throw 'Stop the test server and website before resetting.'
}
$targets = @(
    (Join-Path $ServerRoot 'world'), (Join-Path $ServerRoot 'world_nether'), (Join-Path $ServerRoot 'world_the_end'),
    (Join-Path $ServerRoot 'logs')
) + @(Get-ChildItem -LiteralPath $NerdSmpData -Force -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -ne 'security' } | ForEach-Object { $_.FullName }) + @($WebsiteData)
foreach ($target in $targets) {
    if ((Test-Path -LiteralPath $target) -and $PSCmdlet.ShouldProcess($target, 'Delete')) {
        Remove-Item -LiteralPath $target -Recurse -Force
    }
}
Write-Host 'Reset done. Run start.cmd for a fresh test.' -ForegroundColor Green
