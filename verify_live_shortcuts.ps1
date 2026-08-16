$w = New-Object -ComObject WScript.Shell
$startLnkPath = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\VayuClient\VayuClient.lnk'
$deskLnkPath = Join-Path $env:USERPROFILE 'Desktop\VayuClient.lnk'

if (Test-Path $startLnkPath) {
    $startLnk = $w.CreateShortcut($startLnkPath)
    Write-Host "Start Menu Target: $($startLnk.TargetPath)"
    Write-Host "Start Menu Icon:   $($startLnk.IconLocation)"
} else {
    Write-Host "Start Menu Shortcut NOT found!"
}

if (Test-Path $deskLnkPath) {
    $deskLnk = $w.CreateShortcut($deskLnkPath)
    Write-Host "Desktop Target:    $($deskLnk.TargetPath)"
    Write-Host "Desktop Icon:      $($deskLnk.IconLocation)"
} else {
    Write-Host "Desktop Shortcut NOT found!"
}
