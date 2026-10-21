param([string]$PythonPath = '')
$ErrorActionPreference = 'Stop'
$previewUrl = 'http://127.0.0.1:64140/'
try {
    $existing = Invoke-RestMethod ($previewUrl + 'preview-revision') -TimeoutSec 2
    if ($existing.preview -eq $true) {
        Write-Host "Staff Console preview is already running at $previewUrl"
        return
    }
} catch { }
if (-not $PythonPath) {
    $bundledPython = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
    if (Test-Path -LiteralPath $bundledPython) { $PythonPath = $bundledPython }
    else { $PythonPath = (Get-Command python -ErrorAction Stop).Source }
}
$previewScript = Join-Path $PSScriptRoot 'preview.py'
$previewProcess = Start-Process -FilePath $PythonPath -ArgumentList @('"' + $previewScript + '"') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $PSScriptRoot 'preview-out.log') -RedirectStandardError (Join-Path $PSScriptRoot 'preview-err.log') -PassThru
$previewProcess.Id | Set-Content (Join-Path $PSScriptRoot 'preview.pid')
Write-Host "Staff Console preview started at $previewUrl (PID $($previewProcess.Id))."
Write-Host 'Fictional records only. UI edits automatically reload; no Minecraft server is connected.'
