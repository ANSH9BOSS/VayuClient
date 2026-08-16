$exePath = Join-Path $env:LOCALAPPDATA "Programs\VayuClient\VayuClient.exe"
$setupPath = ".\dist\VayuClientSetup.exe"
$logPath = Join-Path $env:APPDATA "VayuClient\Logs\startup.log"
$setupLogPath = Join-Path $env:APPDATA "VayuClient\Logs\setup_startup.log"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Benchmarking Real VayuClient Warm Startup Performance    " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if (Test-Path $logPath) { Remove-Item $logPath -Force }

$proc = Start-Process -FilePath $exePath -PassThru
$procId = $proc.Id

$timeout = [DateTime]::UtcNow.AddSeconds(5)
while ([DateTime]::UtcNow -lt $timeout -and (-not (Test-Path $logPath))) {
    Start-Sleep -Milliseconds 100
}

if (Test-Path $logPath) {
    Write-Host "`n--- REAL VAYUCLIENT STARTUP LOG ---" -ForegroundColor Yellow
    Get-Content $logPath | ForEach-Object { Write-Host $_ -ForegroundColor White }
}

Start-Sleep -Milliseconds 1000
Get-Process -Id $procId -ErrorAction SilentlyContinue | ForEach-Object {
    $_.CloseMainWindow() | Out-Null
    Start-Sleep -Milliseconds 500
    if (-not $_.HasExited) { Stop-Process -Id $_.Id -Force }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " Benchmarking VayuClientSetup.exe Startup Performance    " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if (Test-Path $setupLogPath) { Remove-Item $setupLogPath -Force }

$sProc = Start-Process -FilePath $setupPath -PassThru
$sProcId = $sProc.Id

$timeout2 = [DateTime]::UtcNow.AddSeconds(5)
while ([DateTime]::UtcNow -lt $timeout2 -and (-not (Test-Path $setupLogPath))) {
    Start-Sleep -Milliseconds 100
}

if (Test-Path $setupLogPath) {
    Write-Host "`n--- REAL VAYUCLIENTSETUP STARTUP LOG ---" -ForegroundColor Yellow
    Get-Content $setupLogPath | ForEach-Object { Write-Host $_ -ForegroundColor White }
}

Start-Sleep -Milliseconds 1000
Get-Process -Id $sProcId -ErrorAction SilentlyContinue | ForEach-Object {
    $_.CloseMainWindow() | Out-Null
    Start-Sleep -Milliseconds 500
    if (-not $_.HasExited) { Stop-Process -Id $_.Id -Force }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " ALL REAL STARTUP BENCHMARKS COMPLETE!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
