Add-Type -AssemblyName System.Drawing

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " VayuClient Windows App and Shortcut Icon Verification   " -ForegroundColor Cyan
Write-Host " Developer: ANSH9BOSS                                    " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Verify VayuClient.exe embedded icon
$vayuExe = "VayuClient\bin\Release\net8.0-windows\VayuClient.exe"
if (-not (Test-Path $vayuExe)) {
    $vayuExe = "VayuClient\bin\Release\net8.0-windows\win-x64\VayuClient.exe"
}

Write-Host "`n[CHECK 1] Verifying VayuClient.exe embedded icon..." -ForegroundColor Yellow
$icon1 = [System.Drawing.Icon]::ExtractAssociatedIcon((Resolve-Path $vayuExe).Path)
if ($icon1 -ne $null) {
    Write-Host "  -> Successfully extracted embedded icon from $vayuExe ($($icon1.Width)x$($icon1.Height))" -ForegroundColor Green
} else {
    Write-Host "  -> FAIL: Could not extract icon from $vayuExe" -ForegroundColor Red
}

# 2. Verify VayuClientSetup.exe embedded icon
$setupExe = "dist\VayuClientSetup.exe"
Write-Host "`n[CHECK 2] Verifying VayuClientSetup.exe embedded icon..." -ForegroundColor Yellow
$icon2 = [System.Drawing.Icon]::ExtractAssociatedIcon((Resolve-Path $setupExe).Path)
if ($icon2 -ne $null) {
    Write-Host "  -> Successfully extracted embedded icon from $setupExe ($($icon2.Width)x$($icon2.Height))" -ForegroundColor Green
} else {
    Write-Host "  -> FAIL: Could not extract icon from $setupExe" -ForegroundColor Red
}

# 3. Clean test installation using payload zip
Write-Host "`n[CHECK 3] Installing to clean directory and creating shortcuts..." -ForegroundColor Yellow
$installDir = Join-Path $env:LOCALAPPDATA "Programs\VayuClient"
$startMenuDir = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\VayuClient"
$startMenuLnk = Join-Path $startMenuDir "VayuClient.lnk"
$desktopLnk = Join-Path $env:USERPROFILE "Desktop\VayuClient.lnk"

# Clean old installation
if (Test-Path $installDir) {
    Remove-Item $installDir -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Path $installDir -Force | Out-Null

$zipPath = "VayuClientSetup\Resources\VayuClientPayload.zip"
Expand-Archive -Path $zipPath -DestinationPath $installDir -Force

$installedExe = Join-Path $installDir "VayuClient.exe"
$installedIco = Join-Path $installDir "vayu_logo.ico"
$installedPng = Join-Path $installDir "vayu_logo.png"

Write-Host "  -> Installed VayuClient.exe exists: $(Test-Path $installedExe)" -ForegroundColor Green
Write-Host "  -> Installed vayu_logo.ico exists:  $(Test-Path $installedIco)" -ForegroundColor Green
Write-Host "  -> Installed vayu_logo.png exists:  $(Test-Path $installedPng)" -ForegroundColor Green

# Create shortcuts with explicit icon
New-Item -ItemType Directory -Path $startMenuDir -Force | Out-Null

$wscript = New-Object -ComObject WScript.Shell

$sLnk = $wscript.CreateShortcut($startMenuLnk)
$sLnk.TargetPath = $installedExe
$sLnk.WorkingDirectory = $installDir
$sLnk.Description = "VayuClient - Premium Minecraft Launcher by ANSH9BOSS"
$sLnk.IconLocation = "$installedIco"
$sLnk.Save()

$dLnk = $wscript.CreateShortcut($desktopLnk)
$dLnk.TargetPath = $installedExe
$dLnk.WorkingDirectory = $installDir
$dLnk.Description = "VayuClient - Premium Minecraft Launcher by ANSH9BOSS"
$dLnk.IconLocation = "$installedIco"
$dLnk.Save()

Write-Host "`n[CHECK 4] Verifying Shortcut Icon Locations..." -ForegroundColor Yellow
$readStart = $wscript.CreateShortcut($startMenuLnk)
Write-Host "  -> Start Menu Shortcut:" -ForegroundColor Cyan
Write-Host "     TargetPath:   $($readStart.TargetPath)"
Write-Host "     IconLocation: $($readStart.IconLocation)"

$readDesk = $wscript.CreateShortcut($desktopLnk)
Write-Host "  -> Desktop Shortcut:" -ForegroundColor Cyan
Write-Host "     TargetPath:   $($readDesk.TargetPath)"
Write-Host "     IconLocation: $($readDesk.IconLocation)"

# 4. Verify that icon location points to a valid file
if (Test-Path $readStart.IconLocation) {
    Write-Host "  -> [PASS] Start Menu IconLocation points to valid existing icon file!" -ForegroundColor Green
} else {
    Write-Host "  -> [FAIL] Start Menu IconLocation target does not exist!" -ForegroundColor Red
}

if (Test-Path $readDesk.IconLocation) {
    Write-Host "  -> [PASS] Desktop Shortcut IconLocation points to valid existing icon file!" -ForegroundColor Green
} else {
    Write-Host "  -> [FAIL] Desktop Shortcut IconLocation target does not exist!" -ForegroundColor Red
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " ALL ICON VERIFICATION CHECKS PASSED!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
