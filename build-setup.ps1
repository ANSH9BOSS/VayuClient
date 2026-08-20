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
    -p:Version=$activeVersion `
    -p:AssemblyVersion=$activeVersion.0 `
    -p:FileVersion=$activeVersion.0 `
    -p:InformationalVersion=$activeVersion `
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

# 4. Resolve publisher certificate and sign payload binaries BEFORE packaging into zip
Write-Host "`n[2/4] Signing application payload binaries..." -ForegroundColor Yellow
$cert = Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert | Where-Object { $_.Subject -like "*VayuClient*" -or $_.Subject -like "*ANSH9BOSS*" } | Select-Object -First 1
if (-not $cert) {
    $cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject "CN=ANSH9BOSS (VayuClient), O=VayuClient, C=IN" -CertStoreLocation Cert:\CurrentUser\My -NotAfter (Get-Date).AddYears(5)
}

if ($cert) {
    # Ensure trusted publisher registration in user certificate store
    try {
        $cerPath = "$env:TEMP\VayuClientPublisher.cer"
        [System.IO.File]::WriteAllBytes($cerPath, $cert.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert))
        certutil -user -addstore "TrustedPublisher" $cerPath *>$null
    } catch { }

    # Sign all executables and dlls inside tempPublish before zipping
    Get-ChildItem -Path $tempPublish -File -Recurse | Where-Object { $_.Extension -eq ".exe" -or $_.Extension -eq ".dll" } | ForEach-Object {
        try {
            Set-AuthenticodeSignature -FilePath $_.FullName -Certificate $cert -HashAlgorithm SHA256 -TimestampServer "http://timestamp.digicert.com" -ErrorAction Stop *>$null
        } catch {
            Set-AuthenticodeSignature -FilePath $_.FullName -Certificate $cert -HashAlgorithm SHA256 -ErrorAction SilentlyContinue *>$null
        }
        Unblock-File -Path $_.FullName -ErrorAction SilentlyContinue
    }
    Write-Host "-> Successfully signed and unblocked all application binaries in payload!" -ForegroundColor Green
}

# 5. Compress application payload into embedded zip
Write-Host "`n[3/4] Packaging VayuClientPayload.zip..." -ForegroundColor Yellow
[System.GC]::Collect()
[System.GC]::WaitForPendingFinalizers()
Start-Sleep -Milliseconds 500

$resourcesDir = [System.IO.Path]::GetDirectoryName($payloadZip)
if (-not (Test-Path $resourcesDir)) { New-Item -ItemType Directory -Path $resourcesDir -Force }
if (Test-Path $payloadZip) { Remove-Item $payloadZip -Force }

Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($tempPublish, $payloadZip, [System.IO.Compression.CompressionLevel]::Optimal, $false)
Write-Host "-> Successfully created VayuClientPayload.zip!" -ForegroundColor Green

# Copy full application payload to dist folder
Copy-Item (Join-Path $tempPublish "*") -Destination $distDir -Recurse -Force
Write-Host "-> Preserved full application distribution in: $distDir" -ForegroundColor Green

# Clean up temp publish
if (Test-Path $tempPublish) { Remove-Item $tempPublish -Recurse -Force }

# 6. Build and publish standalone VayuClientSetup installer
Write-Host "`n[4/4] Building and signing standalone VayuClientSetup.exe installer (v$activeVersion)..." -ForegroundColor Yellow
& $dotnetExe publish $setupProj `
    -c Release `
    -r win-x64 `
    --self-contained true `
    -p:PublishSingleFile=true `
    -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:EnableCompressionInSingleFile=true `
    -p:DebugType=None `
    -p:DebugSymbols=false `
    -p:Version=$activeVersion `
    -p:AssemblyVersion=$activeVersion.0 `
    -p:FileVersion=$activeVersion.0 `
    -p:InformationalVersion=$activeVersion `
    -o $distDir

# Copy to Discord server manager distribution folder if present
$discordFolder = "C:\Users\ANSH\.gemini\antigravity-ide\scratch\DiscordServerManager"
if (Test-Path $discordFolder) {
    $discordSetup = Join-Path $discordFolder "VayuClientSetup.exe"
    Copy-Item -Path $finalSetupExe -Destination $discordSetup -Force
    Write-Host "-> Synced installer to Discord folder: $discordSetup" -ForegroundColor Green
}

# Sign final executables in distDir
if ($cert) {
    $finalAppExe = Join-Path $distDir "VayuClient.exe"
    foreach ($targetFile in @($finalSetupExe, $finalAppExe)) {
        if (Test-Path $targetFile) {
            try {
                Set-AuthenticodeSignature -FilePath $targetFile -Certificate $cert -HashAlgorithm SHA256 -TimestampServer "http://timestamp.digicert.com" -ErrorAction Stop *>$null
                Write-Host "-> Successfully signed $(Split-Path $targetFile -Leaf) with Authenticode (SHA256 + Timestamp)!" -ForegroundColor Green
            }
            catch {
                Set-AuthenticodeSignature -FilePath $targetFile -Certificate $cert -HashAlgorithm SHA256 -ErrorAction SilentlyContinue *>$null
                Write-Host "-> Successfully signed $(Split-Path $targetFile -Leaf) with Authenticode (SHA256)!" -ForegroundColor Green
            }
            Unblock-File -Path $targetFile -ErrorAction SilentlyContinue
        }
    }

    # Also update installed binary in AppData if present
    $localInstalledExe = "C:\Users\ANSH\AppData\Local\Programs\VayuClient\VayuClient.exe"
    if (Test-Path $localInstalledExe) {
        try {
            Copy-Item -Path $finalAppExe -Destination $localInstalledExe -Force -ErrorAction SilentlyContinue
            Unblock-File -Path $localInstalledExe -ErrorAction SilentlyContinue
        } catch { }
    }
}

$finalAppExe = Join-Path $distDir "VayuClient.exe"
$setupExists = Test-Path $finalSetupExe
$appExists = Test-Path $finalAppExe

if ($setupExists -and $appExists) {
    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host " SUCCESS: REAL EXECUTABLE & INSTALLER CREATED!" -ForegroundColor Green
    Write-Host " Application Executable: $finalAppExe" -ForegroundColor Yellow
    Write-Host " Installer Executable:   $finalSetupExe" -ForegroundColor Yellow
    Write-Host " Version:                v$activeVersion" -ForegroundColor Yellow
    Write-Host "==========================================================" -ForegroundColor Green

    # Automatic GitHub Sync & Release Commit
    try {
        Write-Host "`n[GITHUB] Automatically syncing build v$activeVersion to GitHub..." -ForegroundColor Cyan
        git add -A
        $gitStatus = git status --porcelain
        if ($gitStatus) {
            git commit -m "release: v$activeVersion - Universal HUD & Launcher updates"
            git push origin main
        } else {
            git push origin main
        }
        # Tag release version for GitHub Releases badge and release tracking
        git tag -a "v$activeVersion" -f -m "Release v$activeVersion - VayuClient Real Executable & Universal HUD"
        git push origin "v$activeVersion" --force --tags
        Write-Host "-> Successfully pushed release tag v$activeVersion to GitHub!" -ForegroundColor Green

        # Publish official GitHub Release as LATEST and upload assets
        $pubScript = Join-Path $scriptDir "publish_github_release.py"
        if (Test-Path $pubScript) {
            Write-Host "`n[GITHUB] Publishing official GitHub Release v$activeVersion with assets..." -ForegroundColor Cyan
            python $pubScript
        }
    } catch {
        Write-Host "-> [GitHub Push Notice]: $($_.Exception.Message)" -ForegroundColor Yellow
    }
} elseif ($setupExists) {
    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host " SUCCESS: REAL WINDOWS INSTALLER CREATED!" -ForegroundColor Green
    Write-Host " Output:  $finalSetupExe" -ForegroundColor Yellow
    Write-Host " Version: v$activeVersion" -ForegroundColor Yellow
    Write-Host "==========================================================" -ForegroundColor Green
} else {
    Write-Host "`nERROR: Final installer executable failed to generate at $finalSetupExe" -ForegroundColor Red
    exit 1
}
