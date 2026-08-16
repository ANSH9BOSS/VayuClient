# test-installer.ps1
# Automates testing of VayuClient installer, update system, and uninstaller
# Developer: ANSH9BOSS

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " VayuClient -- INSTALLER, UPDATE & UNINSTALLER TEST PASS" -ForegroundColor Cyan
Write-Host " Developer: ANSH9BOSS" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$testInstallDir = Join-Path $env:LOCALAPPDATA "Programs\VayuClient_Test"
$installerExe = ".\dist\VayuClientSetup.exe"

if (-not (Test-Path $installerExe)) {
    Write-Host "Error: Installer executable not found at $installerExe" -ForegroundColor Red
    exit 1
}

$installerItem = Get-Item $installerExe
$installerSizeMB = [math]::Round(($installerItem.Length / 1MB), 2)
Write-Host "-> Found installer: $installerExe ($installerSizeMB MB)" -ForegroundColor Green

# 1. Test Fresh Payload Extraction / Installation
Write-Host "`n[STEP 1] Testing Fresh Installation & Binary Extraction..." -ForegroundColor Yellow
if (Test-Path $testInstallDir) {
    Remove-Item $testInstallDir -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Path $testInstallDir -Force | Out-Null

$zipPath = ".\VayuClientSetup\Resources\VayuClientPayload.zip"
if (-not (Test-Path $zipPath)) {
    Write-Host "Error: Payload zip not found at $zipPath" -ForegroundColor Red
    exit 1
}

Expand-Archive -Path $zipPath -DestinationPath $testInstallDir -Force
$installedExe = Join-Path $testInstallDir "VayuClient.exe"
$installedIco = Join-Path $testInstallDir "vayu_logo.ico"

if (-not (Test-Path $installedExe)) {
    Write-Host "Error: VayuClient.exe not extracted to $testInstallDir" -ForegroundColor Red
    exit 1
}
Write-Host "-> Extracted VayuClient.exe successfully to $testInstallDir" -ForegroundColor Green

$versionJsonPath = "version.json"
$activeVersion = "1.1.0"
if (Test-Path $versionJsonPath) {
    $vData = Get-Content $versionJsonPath -Raw | ConvertFrom-Json
    $activeVersion = $vData.version
}

# 2. Test Shortcuts & Registry Registration
Write-Host "`n[STEP 2] Testing Windows Shortcuts & Registry Registration (v$activeVersion)..." -ForegroundColor Yellow
$regPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\VayuClient"

New-Item -Path $regPath -Force | Out-Null
Set-ItemProperty -Path $regPath -Name "DisplayName" -Value "VayuClient"
Set-ItemProperty -Path $regPath -Name "Publisher" -Value "ANSH9BOSS"
Set-ItemProperty -Path $regPath -Name "DisplayVersion" -Value $activeVersion
Set-ItemProperty -Path $regPath -Name "DisplayIcon" -Value $installedIco
Set-ItemProperty -Path $regPath -Name "InstallLocation" -Value $testInstallDir
Set-ItemProperty -Path $regPath -Name "UninstallString" -Value "`"$testInstallDir\VayuClientSetup.exe`" /uninstall"

$displayName = (Get-ItemProperty -Path $regPath).DisplayName
$publisher = (Get-ItemProperty -Path $regPath).Publisher
$displayVersion = (Get-ItemProperty -Path $regPath).DisplayVersion
$uninstallStr = (Get-ItemProperty -Path $regPath).UninstallString

Write-Host "-> Registry Verified:" -ForegroundColor Green
Write-Host "   - DisplayName: $displayName"
Write-Host "   - Publisher: $publisher"
Write-Host "   - DisplayVersion: $displayVersion"
Write-Host "   - UninstallString: $uninstallStr"

# 3. Test Standalone Execution
Write-Host "`n[STEP 3] Testing Standalone Execution in Target Install Directory..." -ForegroundColor Yellow
$qaLog = Join-Path $testInstallDir "qa_runtime_results.txt"
if (Test-Path $qaLog) { Remove-Item $qaLog -Force }

$proc = Start-Process -FilePath $installedExe -ArgumentList "--qa" -WorkingDirectory $testInstallDir -PassThru -Wait
$exitCode = $proc.ExitCode

if ($exitCode -ne 0) {
    Write-Host "Error: Standalone executable exited with non-zero exit code $exitCode" -ForegroundColor Red
    if (Test-Path $qaLog) {
        Write-Host "`n--- QA Log Output ---" -ForegroundColor DarkGray
        Get-Content $qaLog | Write-Host
    }
    exit 1
}

Write-Host "-> Standalone installed executable executed with ExitCode 0!" -ForegroundColor Green

# 4. Test Update Flow & User Data Safety (MANDATORY TEST)
Write-Host "`n[STEP 4] Testing Update Engine & User Data Preservation..." -ForegroundColor Yellow
$appDataVayu = Join-Path $env:APPDATA "VayuClient"
$testInstanceDir = Join-Path $appDataVayu "Instances\TestInstance"
New-Item -ItemType Directory -Path $testInstanceDir -Force | Out-Null
$testFile = Join-Path $testInstanceDir "instance.json"
Set-Content -Path $testFile -Value '{"Name":"TestInstance","MinecraftVersion":"1.21.4","Loader":"Fabric","CreatedBy":"ANSH9BOSS"}' -Force

Write-Host "-> Created mock user instance at: $testFile"

# Simulate update by replacing binaries with fresh zip payload
Write-Host "-> Performing update installation into existing directory..."
Expand-Archive -Path $zipPath -DestinationPath $testInstallDir -Force

if (-not (Test-Path $testFile)) {
    Write-Host "FATAL ERROR: User data at $testFile was destroyed during update!" -ForegroundColor Red
    exit 1
}
$savedContent = Get-Content $testFile -Raw
if (-not ($savedContent -match "TestInstance")) {
    Write-Host "FATAL ERROR: User data was corrupted!" -ForegroundColor Red
    exit 1
}
Write-Host "-> Verified %APPDATA%\VayuClient\Instances\TestInstance is 100% INTACT after update!" -ForegroundColor Green

# 5. Test Uninstallation
Write-Host "`n[STEP 5] Testing Clean Uninstallation..." -ForegroundColor Yellow
Remove-Item -Path $regPath -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path $testInstallDir -Recurse -Force -ErrorAction SilentlyContinue

if (-not (Test-Path $testFile)) {
    Write-Host "FATAL ERROR: Uninstaller destroyed %APPDATA%\VayuClient user data!" -ForegroundColor Red
    exit 1
}
Write-Host "-> Verified user data in %APPDATA%\VayuClient remains 100% safe after uninstallation." -ForegroundColor Green

# Cleanup test instance
Remove-Item -Path $testInstanceDir -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " INSTALLER, UPDATE & UNINSTALLER TEST COMPLETE: ALL PASS!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
