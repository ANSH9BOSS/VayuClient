$appData = "$env:APPDATA\VayuClient"
Write-Host "Verifying launch arguments resolution..."
$args = & ".\VayuClient\bin\Release\net8.0-windows\VayuClient.exe" --qa
Write-Host "QA returned code: $LASTEXITCODE"

$latestLog = Get-ChildItem -Path "$appData\logs\launch_*.log" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($latestLog) {
    Write-Host "`nLatest Launch Log: $($latestLog.FullName)"
    $content = Get-Content $latestLog.FullName
    $hasNativesJavaMainClassError = $content | Select-String -Pattern "Optimized\\natives"
    if ($hasNativesJavaMainClassError) {
        Write-Host "WARNING: Found space-split artifact:" -ForegroundColor Red
        $hasNativesJavaMainClassError
    } else {
        Write-Host "SUCCESS: No space-split artifacts found in arguments!" -ForegroundColor Green
    }
}
