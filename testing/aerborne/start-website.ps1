<#
.SYNOPSIS
Runs the Aerborne website at http://127.0.0.1:5070, connected to the local test server.
It reloads itself when you edit the website's code, templates or styles.
#>
[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'common.ps1')

$python = Join-Path $WebsiteRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    throw "The website's virtual environment is missing. From $WebsiteRoot run: python -m venv .venv; .venv\Scripts\pip install -r requirements.txt"
}
New-Item -ItemType Directory -Force -Path $WebsiteData | Out-Null

# Everything the live site sets in cPanel, pointed at the local test server instead.
$env:AERBORNE_DATA_DIR = $WebsiteData
$env:AERBORNE_SECURE_COOKIES = '0'
$env:AERBORNE_SITE_URL = $WebsiteUrl
$env:AERBORNE_SERVER_ADDRESS = 'localhost'
$env:AERBORNE_SERVER_PORT = "$MinecraftPort"
$env:AERBORNE_STATUS_HOST = '127.0.0.1'
$env:AERBORNE_STATUS_PORT = "$MinecraftPort"
$env:AERBORNE_API_URL = "http://127.0.0.1:$ApiPort"
$env:AERBORNE_API_TOKEN = Get-SharedToken 'web-api-token.txt'
$env:AERBORNE_REWARDS_TOKEN = Get-SharedToken 'website-rewards-token.txt'
$env:AERBORNE_SOCIAL_TOKEN = Get-SharedToken 'website-social-token.txt'
$env:AERBORNE_PANEL_SYNC_TOKEN = Get-SharedToken 'watchdog-website-token.txt'
$env:AERBORNE_PANEL_URL = "http://127.0.0.1:$PanelPort"

Push-Location $WebsiteRoot
try {
    Write-Host "Aerborne test website: $WebsiteUrl  (admin: $WebsiteUrl/admin)" -ForegroundColor Green
    & $python dev.py --port $WebsitePort
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
