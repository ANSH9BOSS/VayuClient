Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " VayuClient Live Installation & Start Menu Registration  " -ForegroundColor Cyan
Write-Host " Developer: ANSH9BOSS                                    " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$targetDir = Join-Path $env:LOCALAPPDATA "Programs\VayuClient"
$startMenuDir = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\VayuClient"
$startMenuLnk = Join-Path $startMenuDir "VayuClient.lnk"
$desktopLnk = Join-Path $env:USERPROFILE "Desktop\VayuClient.lnk"
$zipPath = "VayuClientSetup\Resources\VayuClientPayload.zip"

if (-not (Test-Path $zipPath)) {
    Write-Host "Error: Payload zip not found" -ForegroundColor Red
    exit 1
}

# 1. Clean previous installation
Write-Host "[1/4] Preparing target directory..." -ForegroundColor Yellow
if (Test-Path $targetDir) {
    Remove-Item $targetDir -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Path $targetDir -Force | Out-Null

# 2. Extract payload
Write-Host "[2/4] Extracting VayuClient payload..." -ForegroundColor Yellow
Expand-Archive -Path $zipPath -DestinationPath $targetDir -Force

$installedExe = Join-Path $targetDir "VayuClient.exe"
$installedIco = Join-Path $targetDir "vayu_logo.ico"

# 3. Create Windows Shortcuts with official icon
Write-Host "[3/4] Creating Start Menu and Desktop shortcuts..." -ForegroundColor Yellow
if (-not (Test-Path $startMenuDir)) {
    New-Item -ItemType Directory -Path $startMenuDir -Force | Out-Null
}

$wscript = New-Object -ComObject WScript.Shell

$sLnk = $wscript.CreateShortcut($startMenuLnk)
$sLnk.TargetPath = $installedExe
$sLnk.WorkingDirectory = $targetDir
$sLnk.Description = "VayuClient - Premium Minecraft Launcher by ANSH9BOSS"
$sLnk.IconLocation = "$installedIco,0"
$sLnk.Save()

$dLnk = $wscript.CreateShortcut($desktopLnk)
$dLnk.TargetPath = $installedExe
$dLnk.WorkingDirectory = $targetDir
$dLnk.Description = "VayuClient - Premium Minecraft Launcher by ANSH9BOSS"
$dLnk.IconLocation = "$installedIco,0"
$dLnk.Save()

$versionJsonPath = "version.json"
$activeVersion = "1.0.1"
if (Test-Path $versionJsonPath) {
    $vData = Get-Content $versionJsonPath -Raw | ConvertFrom-Json
    $activeVersion = $vData.version
}

# 4. Register in Windows Uninstall registry
Write-Host "[4/4] Registering in Windows Add/Remove Programs (v$activeVersion)..." -ForegroundColor Yellow
$regPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\VayuClient"
New-Item -Path $regPath -Force | Out-Null
Set-ItemProperty -Path $regPath -Name "DisplayName" -Value "VayuClient"
Set-ItemProperty -Path $regPath -Name "Publisher" -Value "ANSH9BOSS"
Set-ItemProperty -Path $regPath -Name "DisplayVersion" -Value $activeVersion
Set-ItemProperty -Path $regPath -Name "DisplayIcon" -Value "$installedIco,0"
Set-ItemProperty -Path $regPath -Name "InstallLocation" -Value $targetDir
Set-ItemProperty -Path $regPath -Name "UninstallString" -Value "`"$targetDir\VayuClientSetup.exe`" /uninstall"

# Copy uninstaller payload
$setupExe = "dist\VayuClientSetup.exe"
if (Test-Path $setupExe) {
    Copy-Item $setupExe (Join-Path $targetDir "VayuClientSetup.exe") -Force
}

# Flush Windows Shell Icon Cache
try {
    $type = [Type]::GetType("System.Runtime.InteropServices.Marshal")
    # SHChangeNotify
} catch {}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " LIVE INSTALLATION COMPLETE & SHORTCUTS REGISTERED!" -ForegroundColor Green
Write-Host " Target Exe:     $installedExe"
Write-Host " Icon Asset:     $installedIco"
Write-Host " Start Menu Lnk: $startMenuLnk"
Write-Host " Desktop Lnk:    $desktopLnk"
Write-Host "==========================================================" -ForegroundColor Green
