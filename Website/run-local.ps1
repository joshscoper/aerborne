param([int]$Port = 5050)

$ErrorActionPreference = 'Stop'
$bundledPython = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$venvPython = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'

if (Test-Path -LiteralPath $venvPython) {
    # Native image extensions must run with the Python that owns this environment.
    $developmentPython = $venvPython
} elseif (Test-Path -LiteralPath $bundledPython) {
    $developmentPython = $bundledPython
} else {
    $developmentPython = (Get-Command python -ErrorAction Stop).Source
}

Push-Location -LiteralPath $PSScriptRoot
try {
    & $developmentPython -W ignore::DeprecationWarning (Join-Path $PSScriptRoot 'dev.py') --port $Port
} finally {
    Pop-Location
}
