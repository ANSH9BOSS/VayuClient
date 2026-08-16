# ==========================================================
#  VayuClient Semantic Versioning Automation Tool
#  Developer: ANSH9BOSS
# ==========================================================

param(
    [Parameter(Position = 0)]
    [ValidateSet("patch", "minor", "major", "get", "")]
    [string]$Type = "",

    [Parameter()]
    [string]$Set = "",

    [Parameter()]
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
$rootDir = $PSScriptRoot
if ([string]::IsNullOrEmpty($rootDir)) {
    $rootDir = Get-Location
}

$versionJsonPath = Join-Path $rootDir "version.json"
$directoryBuildPropsPath = Join-Path $rootDir "Directory.Build.props"
$vayuCsprojPath = Join-Path $rootDir "VayuClient\VayuClient.csproj"
$setupCsprojPath = Join-Path $rootDir "VayuClientSetup\VayuClientSetup.csproj"

# 1. Read existing version
if (-not (Test-Path $versionJsonPath)) {
    $currentMajor = 1
    $currentMinor = 0
    $currentPatch = 1
} else {
    $json = Get-Content $versionJsonPath -Raw | ConvertFrom-Json
    $currentMajor = [int]$json.major
    $currentMinor = [int]$json.minor
    $currentPatch = [int]$json.patch
}

$oldVersion = "$currentMajor.$currentMinor.$currentPatch"

if ($Type -eq "get" -or ([string]::IsNullOrEmpty($Type) -and [string]::IsNullOrEmpty($Set))) {
    if (-not $Quiet) {
        Write-Host "Current VayuClient Version: " -NoNewline
        Write-Host $oldVersion -ForegroundColor Cyan
    }
    return $oldVersion
}

# 2. Compute new version
$newMajor = $currentMajor
$newMinor = $currentMinor
$newPatch = $currentPatch

if (![string]::IsNullOrEmpty($Set)) {
    $parts = $Set.Trim().TrimStart('v').Split('.')
    if ($parts.Length -lt 2) {
        Write-Error "Invalid version format '$Set'. Expected X.Y or X.Y.Z format (e.g., 1.0.2)."
        return
    }
    $newMajor = [int]$parts[0]
    $newMinor = [int]$parts[1]
    $newPatch = if ($parts.Length -ge 3) { [int]$parts[2] } else { 0 }
}
elseif ($Type -eq "patch") {
    $newPatch = $currentPatch + 1
}
elseif ($Type -eq "minor") {
    $newMinor = $currentMinor + 1
    $newPatch = 0
}
elseif ($Type -eq "major") {
    $newMajor = $currentMajor + 1
    $newMinor = 0
    $newPatch = 0
}

$newVersion = "$newMajor.$newMinor.$newPatch"
$today = (Get-Date).ToString("yyyy-MM-dd")

if (-not $Quiet) {
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host " VayuClient Semantic Version Bump                        " -ForegroundColor White
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host " Previous Version: $oldVersion" -ForegroundColor Yellow
    Write-Host " New Version:      $newVersion ($Type)" -ForegroundColor Green
    Write-Host " Developer:        ANSH9BOSS" -ForegroundColor Gray
    Write-Host "==========================================================" -ForegroundColor Cyan
}

# 3. Update version.json
$newJson = @{
    major = $newMajor
    minor = $newMinor
    patch = $newPatch
    version = $newVersion
    developer = "ANSH9BOSS"
    productName = "VayuClient"
    lastUpdated = $today
} | ConvertTo-Json -Depth 4

Set-Content -Path $versionJsonPath -Value $newJson -Encoding utf8

# 4. Update Directory.Build.props
$buildPropsContent = @"
<Project>
  <PropertyGroup>
    <Version>$newVersion</Version>
    <FileVersion>$newVersion.0</FileVersion>
    <AssemblyVersion>$newVersion.0</AssemblyVersion>
    <InformationalVersion>$newVersion</InformationalVersion>
    <Authors>ANSH9BOSS</Authors>
    <Company>ANSH9BOSS</Company>
    <Product>VayuClient</Product>
    <Copyright>Copyright © 2026 ANSH9BOSS</Copyright>
  </PropertyGroup>
</Project>
"@
Set-Content -Path $directoryBuildPropsPath -Value $buildPropsContent -Encoding utf8

# 5. Synchronize .csproj files if version tags exist
function Update-CsprojVersion($csprojPath, $ver) {
    if (Test-Path $csprojPath) {
        $content = Get-Content $csprojPath -Raw
        $content = [System.Text.RegularExpressions.Regex]::Replace($content, "<Version>[^<]*</Version>", "<Version>$ver</Version>")
        $content = [System.Text.RegularExpressions.Regex]::Replace($content, "<FileVersion>[^<]*</FileVersion>", "<FileVersion>$ver</FileVersion>")
        $content = [System.Text.RegularExpressions.Regex]::Replace($content, "<AssemblyVersion>[^<]*</AssemblyVersion>", "<AssemblyVersion>$ver</AssemblyVersion>")
        Set-Content -Path $csprojPath -Value $content -Encoding utf8
    }
}

Update-CsprojVersion $vayuCsprojPath $newVersion
Update-CsprojVersion $setupCsprojPath $newVersion

if (-not $Quiet) {
    Write-Host "-> [OK] version.json updated to $newVersion" -ForegroundColor Green
    Write-Host "-> [OK] Directory.Build.props updated to $newVersion" -ForegroundColor Green
    Write-Host "-> [OK] VayuClient.csproj synchronized" -ForegroundColor Green
    Write-Host "-> [OK] VayuClientSetup.csproj synchronized" -ForegroundColor Green
}

return $newVersion
