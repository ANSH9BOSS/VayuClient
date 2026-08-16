$appData = "$env:APPDATA\VayuClient"
Write-Host "=== Launcher Logs ==="
$latestLauncherLog = Get-ChildItem -Path "$appData\logs" -Filter *.log -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($latestLauncherLog) {
    Write-Host "File: $($latestLauncherLog.FullName) ($($latestLauncherLog.LastWriteTime))"
    Get-Content $latestLauncherLog.FullName -Tail 50
} else {
    Write-Host "No launcher logs found."
}

Write-Host "`n=== Game latest.log ==="
$latestGameLog = Get-ChildItem -Path "$appData\Instances\*\game\logs\latest.log" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($latestGameLog) {
    Write-Host "File: $($latestGameLog.FullName) ($($latestGameLog.LastWriteTime))"
    Get-Content $latestGameLog.FullName -Tail 50
} else {
    Write-Host "No game latest.log found."
}

Write-Host "`n=== Crash Reports ==="
$latestCrash = Get-ChildItem -Path "$appData\Instances\*\game\crash-reports\*.txt" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($latestCrash) {
    Write-Host "File: $($latestCrash.FullName) ($($latestCrash.LastWriteTime))"
    Get-Content $latestCrash.FullName -Tail 50
} else {
    Write-Host "No crash reports found."
}
