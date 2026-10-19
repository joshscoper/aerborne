param([int]$Port = 5050)

$ErrorActionPreference = 'Stop'
$bundledPython = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$venvPython = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
$localPackages = Join-Path $PSScriptRoot '.venv\Lib\site-packages'

if (Test-Path -LiteralPath $bundledPython) {
    $developmentPython = $bundledPython
    if (Test-Path -LiteralPath $localPackages) {
        $env:PYTHONPATH = $localPackages + [IO.Path]::PathSeparator + $env:PYTHONPATH
    }
} elseif (Test-Path -LiteralPath $venvPython) {
    $developmentPython = $venvPython
} else {
    $developmentPython = (Get-Command python -ErrorAction Stop).Source
}

Push-Location -LiteralPath $PSScriptRoot
try {
    & $developmentPython -W ignore::DeprecationWarning (Join-Path $PSScriptRoot 'dev.py') --port $Port
} finally {
    Pop-Location
}
