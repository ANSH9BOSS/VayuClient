# ==========================================================
#  VayuClient Real Windows Setup Installer Build Script
#  Developer: ANSH9BOSS
#  Product: VayuClient
# ==========================================================

param(
    [Parameter()]
    [ValidateSet("none", "patch", "minor", "major")]
    [string]$Bump = "patch"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$dotnetExe = "C:\Users\ANSH\AppData\Local\dotnet\dotnet.exe"
if (-not (Test-Path $dotnetExe)) {
    $dotnetExe = "dotnet"
}

# 1. Handle automatic semantic version bump if requested
$bumpScript = Join-Path $scriptDir "bump-version.ps1"
if ($Bump -ne "none" -and (Test-Path $bumpScript)) {
    Write-Host "`n[SEMVER] Bumping semantic version ($Bump)..." -ForegroundColor Cyan
    & powershell -File $bumpScript -Type $Bump
}

# 2. Read active version from version.json or Directory.Build.props
$versionJson = Join-Path $scriptDir "version.json"
$activeVersion = "1.0.1"
if (Test-Path $versionJson) {
    $vData = Get-Content $versionJson -Raw | ConvertFrom-Json
    $activeVersion = $vData.version
}

$vayuProj = Join-Path $scriptDir "VayuClient\VayuClient.csproj"
$setupProj = Join-Path $scriptDir "VayuClientSetup\VayuClientSetup.csproj"
$tempPublish = Join-Path $scriptDir "temp-publish"
$payloadZip = Join-Path $scriptDir "VayuClientSetup\Resources\VayuClientPayload.zip"
$distDir = Join-Path $scriptDir "dist"
$finalSetupExe = Join-Path $distDir "VayuClientSetup.exe"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Building VayuClient Real Windows Installer (ANSH9BOSS) " -ForegroundColor Cyan
Write-Host " Target Version: v$activeVersion" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

# Terminate any running processes and clean build staging
Get-Process VayuClient,VayuClientSetup -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 500

if (Test-Path $tempPublish) { Remove-Item $tempPublish -Recurse -Force -ErrorAction SilentlyContinue }
if (Test-Path $payloadZip) { Remove-Item $payloadZip -Force -ErrorAction SilentlyContinue }
if (Test-Path $finalSetupExe) { Remove-Item $finalSetupExe -Force -ErrorAction SilentlyContinue }
if (-not (Test-Path $distDir)) { New-Item -ItemType Directory -Path $distDir -Force }

# 3. Publish VayuClient application payload
Write-Host "`n[1/3] Publishing VayuClient application payload (v$activeVersion)..." -ForegroundColor Yellow
& $dotnetExe publish $vayuProj `
    -c Release `
    -r win-x64 `
    --self-contained true `
    -p:PublishSingleFile=true `
    -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:EnableCompressionInSingleFile=true `
    -p:DebugType=None `
    -p:DebugSymbols=false `
    -o $tempPublish

# Explicitly ensure official branding assets exist in the payload
$vayuIco = Join-Path $scriptDir "VayuClient\Assets\Images\vayu_logo.ico"
$vayuPng = Join-Path $scriptDir "VayuClient\Assets\Images\vayu_logo.png"

if (Test-Path $vayuIco) {
    Copy-Item $vayuIco -Destination (Join-Path $tempPublish "vayu_logo.ico") -Force
}
if (Test-Path $vayuPng) {
    Copy-Item $vayuPng -Destination (Join-Path $tempPublish "vayu_logo.png") -Force
}
$stevePng = Join-Path $scriptDir "VayuClient\Assets\Images\steve_head.png"
if (Test-Path $stevePng) {
    Copy-Item $stevePng -Destination (Join-Path $tempPublish "steve_head.png") -Force
}

# 4. Compress application payload into embedded zip
Write-Host "`n[2/3] Packaging VayuClientPayload.zip..." -ForegroundColor Yellow
[System.GC]::Collect()
[System.GC]::WaitForPendingFinalizers()
Start-Sleep -Seconds 1

$resourcesDir = [System.IO.Path]::GetDirectoryName($payloadZip)
if (-not (Test-Path $resourcesDir)) { New-Item -ItemType Directory -Path $resourcesDir -Force }
if (Test-Path $payloadZip) { Remove-Item $payloadZip -Force }

# Retry loop for zipping in case Windows Defender / file scanner holds a temporary lock
$zipped = $false
for ($attempt = 1; $attempt -le 5; $attempt++) {
    try {
        Compress-Archive -Path "$tempPublish\*" -DestinationPath $payloadZip -Force
        $zipped = $true
        break
    }
    catch {
        Write-Host "Waiting for file handles to release (attempt $attempt)..." -ForegroundColor DarkGray
        Start-Sleep -Milliseconds 800
    }
}
if (-not $zipped) {
    Compress-Archive -Path "$tempPublish\*" -DestinationPath $payloadZip -Force
}

# 5. Build and publish standalone VayuClientSetup installer
Write-Host "`n[3/3] Building standalone VayuClientSetup.exe installer (v$activeVersion)..." -ForegroundColor Yellow
& $dotnetExe publish $setupProj `
    -c Release `
    -r win-x64 `
    --self-contained true `
    -p:PublishSingleFile=true `
    -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:EnableCompressionInSingleFile=true `
    -p:DebugType=None `
    -p:DebugSymbols=false `
    -o $distDir

# Clean up temp publish
if (Test-Path $tempPublish) { Remove-Item $tempPublish -Recurse -Force }

# Copy to Discord server manager distribution folder if present
$discordFolder = "C:\Users\ANSH\.gemini\antigravity-ide\scratch\DiscordServerManager"
if (Test-Path $discordFolder) {
    $discordSetup = Join-Path $discordFolder "VayuClientSetup.exe"
    Copy-Item -Path $finalSetupExe -Destination $discordSetup -Force
    Write-Host "-> Synced installer to Discord folder: $discordSetup" -ForegroundColor Green
}

if (Test-Path $finalSetupExe) {
    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host " SUCCESS: REAL WINDOWS INSTALLER CREATED!" -ForegroundColor Green
    Write-Host " Output:  $finalSetupExe" -ForegroundColor Yellow
    Write-Host " Version: v$activeVersion" -ForegroundColor Yellow
    Write-Host "==========================================================" -ForegroundColor Green
} else {
    Write-Host "`nERROR: Final installer executable failed to generate at $finalSetupExe" -ForegroundColor Red
    exit 1
}
